package com.music.bitchord.desktop

import com.music.bitchord.data.canvas.SpotifyCanvasQuery
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64
import java.util.UUID
import java.net.CookieHandler
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.scene.web.WebEngine
import netscape.javascript.JSObject

/**
 * Spotify's Canvas: the short looping clip shown behind a track in their own app.
 *
 * Needs the listener's `sp_dc` cookie, which is the one thing here that identifies an account —
 * see [DesktopSpotifyToken] for how a bearer is minted from it, and why that is the fragile part
 * on a desktop with no embedded browser.
 */
internal object DesktopSpotifyCanvas {

    private const val SEARCH_URL = "https://api.spotify.com/v1/search"
    private const val ALBUM_TRACKS_URL = "https://api.spotify.com/v1/albums"
    private const val CANVAS_URL = "https://spclient.wg.spotify.com/canvaz-cache/v0/canvases"
    private const val PATHFINDER_URL = "https://api-partner.spotify.com/pathfinder/v1/query"

    /**
     * Names the web player's `searchTracks` query to Pathfinder.
     *
     * Pathfinder only runs persisted queries: the client sends the SHA-256 of a query text Spotify
     * registered at build time, never the query itself. The text is not published, so the hash
     * cannot be computed here; it is copied from the web player — the `extensions` parameter of a
     * `searchTracks` request in DevTools, or the bundle's `"searchTracks","query","<hash>"`
     * declaration. Same value as Android's `SpotifyCanvas`.
     *
     * It changes whenever Spotify edits the query. Pathfinder then rejects it, search falls back
     * to REST `/v1/search` (which 429s these tokens), and canvases stop. Refresh it from either
     * source above, or look it up at runtime the way [SpotifyCanvasQuery.findQueryHash] already
     * does for the `canvas` query.
     */
    private const val PATHFINDER_SEARCH_HASH =
        "bc1ca2fcd0ba1013a0fc88e6cc4f190af501851e3dafd3e1ef85840297694428"

    /** spclient gates this path to Spotify's own apps by user agent; the web player's is turned
     * away, so this wears a mobile client's instead. */
    private const val SPOTIFY_APP_UA = "Spotify/9.0.34.593 iOS/18.4 (iPhone15,3)"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val CANVAS_URL_REGEX = Regex("""https://[^"'\s\x00-\x1F]+\.cnvs\.mp4""")

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private data class TrackHit(val uri: String, val title: String, val artist: String, val album: String?)

    fun search(title: String, artist: String, album: String?): DesktopCanvasArtwork? {
        val token = DesktopSpotifyToken.accessToken() ?: return null
        val hit = searchTrack(title, artist, album, token) ?: run {
            DesktopTrackLog.log("canvas: Spotify search found no match for '$title' by '$artist'")
            return null
        }
        val canvasUrl = fetchCanvasUrl(hit.uri, token) ?: run {
            DesktopTrackLog.log("canvas: Spotify has no canvas for ${hit.uri} ('${hit.title}')")
            return null
        }
        DesktopTrackLog.log("canvas: Spotify canvas found for '${hit.title}'")
        return DesktopCanvasArtwork(
            url = canvasUrl,
            title = hit.title,
            artist = hit.artist,
            album = hit.album,
            source = DesktopCanvasSource.SPOTIFY,
        )
    }

    /** A release's canvas, read off its first track — Spotify hangs Canvas off tracks, not
     * releases, so there is no album-level lookup to make directly. */
    fun searchAlbum(album: String, artist: String): DesktopCanvasArtwork? {
        val token = DesktopSpotifyToken.accessToken() ?: return null
        val items = get(
            url("$SEARCH_URL", listOf("q" to "$album $artist", "type" to "album", "limit" to "10")),
            token,
        )?.get("albums")?.jsonObject?.get("items")?.jsonArray ?: return null

        for (item in items) {
            val record = item as? JsonObject ?: continue
            val recordTitle = record.text("name") ?: continue
            val artists = record["artists"]?.jsonArray
                ?.mapNotNull { it.jsonObject.text("name") }
                .orEmpty()
            if (!canvasMatches(recordTitle, artists, album, artist)) continue
            val albumId = record.text("id") ?: continue
            val trackUri = firstTrackUri(albumId, token) ?: continue
            val canvasUrl = fetchCanvasUrl(trackUri, token) ?: continue
            return DesktopCanvasArtwork(
                url = canvasUrl,
                title = recordTitle,
                artist = artists.joinToString(", ").ifBlank { null },
                album = recordTitle,
                source = DesktopCanvasSource.SPOTIFY,
            )
        }
        return null
    }

    /** Pathfinder first, as on Android: REST `/v1/search` answers this kind of token with 429s. */
    private fun searchTrack(title: String, artist: String, album: String?, token: String): TrackHit? =
        searchViaPathfinder(title, artist, album, token) ?: searchViaRest(title, artist, album, token)

    /**
     * The web player's own search box. Spotify's ranking is trusted for the hit, as on Android: the
     * GraphQL response's fields are not documented well enough to re-check title and artist.
     */
    private fun searchViaPathfinder(title: String, artist: String, album: String?, token: String): TrackHit? {
        val clientToken = DesktopSpotifyToken.clientToken() ?: run {
            DesktopTrackLog.log("canvas: no Spotify client token; skipping Pathfinder search")
            return null
        }
        val variables = buildJsonObject {
            put("searchTerm", listOfNotNull(title, artist, album).joinToString(" "))
            put("offset", 0)
            put("limit", 10)
            put("numberOfTopResults", 5)
            put("includeAudiobooks", false)
            put("includePreReleases", false)
        }.toString()
        val extensions = buildJsonObject {
            putJsonObject("persistedQuery") {
                put("version", 1)
                put("sha256Hash", PATHFINDER_SEARCH_HASH)
            }
        }.toString()
        val response = runCatching {
            http.send(
                HttpRequest.newBuilder(
                    URI.create(
                        url(
                            PATHFINDER_URL,
                            listOf("operationName" to "searchTracks", "variables" to variables, "extensions" to extensions),
                        ),
                    ),
                )
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer $token")
                    .header("Client-Token", clientToken)
                    .header("App-platform", "WebPlayer")
                    .header("Accept", "application/json")
                    .header("User-Agent", CANVAS_UA)
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        }.getOrElse {
            DesktopTrackLog.log("canvas: Pathfinder search threw: ${it.message}")
            return null
        }
        if (response.statusCode() !in 200..299) {
            DesktopTrackLog.log("canvas: Pathfinder search answered ${response.statusCode()}: ${response.body().take(200)}")
            return null
        }
        val hit = runCatching {
            json.parseToJsonElement(response.body()).jsonObject["data"]?.jsonObject
                ?.get("searchV2")?.jsonObject
                ?.get("tracksV2")?.jsonObject
                ?.get("items")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("item")?.jsonObject
                ?.get("data")?.jsonObject
        }.getOrNull() ?: run {
            DesktopTrackLog.log("canvas: Pathfinder search had no hit: ${response.body().take(200)}")
            return null
        }
        val uri = hit.text("uri") ?: hit.text("id")?.let { "spotify:track:$it" } ?: return null
        return TrackHit(uri, title, artist, album)
    }

    private fun searchViaRest(title: String, artist: String, album: String?, token: String): TrackHit? {
        val query = listOfNotNull(title, artist, album).joinToString(" ")
        val items = get(url(SEARCH_URL, listOf("q" to query, "type" to "track", "limit" to "10")), token)
            ?.get("tracks")?.jsonObject?.get("items")?.jsonArray
            ?: return null
        for (item in items) {
            val track = item as? JsonObject ?: continue
            val trackTitle = track.text("name") ?: continue
            val artists = track["artists"]?.jsonArray
                ?.mapNotNull { it.jsonObject.text("name") }
                .orEmpty()
            if (!canvasMatches(trackTitle, artists, title, artist)) continue
            val uri = track.text("uri") ?: continue
            return TrackHit(
                uri = uri,
                title = trackTitle,
                artist = artists.joinToString(", ").ifBlank { artist },
                album = track["album"]?.jsonObject?.text("name"),
            )
        }
        return null
    }

    private fun firstTrackUri(albumId: String, token: String): String? =
        get(url("$ALBUM_TRACKS_URL/$albumId/tracks", listOf("limit" to "1")), token)
            ?.get("items")?.jsonArray?.firstOrNull()?.jsonObject?.text("uri")

    // ── Pathfinder: the `canvas` GraphQL query ───────────────────────────

    /** The live `canvas` query hash, read off the web player's own scripts. */
    private val queryHashes = SpotifyCanvasQuery.QueryHashes(fetch = { url -> canvasGet(url) })

    /** The track's canvas, asked for the way the current web player does; `canvaz-cache` is the
     * fallback when the query fails or answers with nothing playable. */
    private fun fetchCanvasUrl(trackUri: String, token: String): String? =
        when (val answer = fetchCanvasViaPathfinder(trackUri, token)) {
            is SpotifyCanvasQuery.Answer.Found -> answer.url
            is SpotifyCanvasQuery.Answer.NoCanvas -> {
                DesktopTrackLog.log("canvas: Pathfinder has no playable canvas (${answer.detail}); trying canvaz-cache")
                fetchCanvasViaCanvaz(trackUri, token)
            }
            is SpotifyCanvasQuery.Answer.Failed -> {
                DesktopTrackLog.log("canvas: Spotify's canvas query failed (${answer.reason}); trying canvaz-cache")
                fetchCanvasViaCanvaz(trackUri, token)
            }
        }

    private fun fetchCanvasViaPathfinder(trackUri: String, token: String, isRetry: Boolean = false): SpotifyCanvasQuery.Answer {
        val hash = queryHashes.canvasHash(forceRefresh = isRetry)
        val response = runCatching {
            val builder = HttpRequest.newBuilder(URI.create(SpotifyCanvasQuery.ENDPOINT))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Accept-Language", "en")
                .header("App-platform", "WebPlayer")
                .header("User-Agent", CANVAS_UA)
            authHeaders(token).forEach { (name, value) -> builder.header(name, value) }
            http.send(
                builder.POST(HttpRequest.BodyPublishers.ofString(SpotifyCanvasQuery.requestBody(trackUri, hash))).build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        }.getOrElse { return SpotifyCanvasQuery.Answer.Failed("request threw: ${it.message}") }
        if (response.statusCode() !in 200..299) return SpotifyCanvasQuery.Answer.Failed("http ${response.statusCode()}")

        val answer = SpotifyCanvasQuery.parse(response.body())
        // A rebuilt web player retires the old hash: look it up again once.
        if (answer is SpotifyCanvasQuery.Answer.Failed && answer.staleHash && !isRetry) {
            return fetchCanvasViaPathfinder(trackUri, token, isRetry = true)
        }
        return answer
    }

    // ── canvaz-cache: protobuf in, protobuf out (fallback) ───────────────

    internal data class CanvasHit(val url: String, val trackUri: String?)

    private fun fetchCanvasViaCanvaz(trackUri: String, token: String): String? {
        val bytes = runCatching {
            val builder = HttpRequest.newBuilder(URI.create(CANVAS_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/protobuf")
                .header("Content-Type", "application/protobuf")
                .header("Accept-Language", "en")
                .header("User-Agent", SPOTIFY_APP_UA)
            authHeaders(token).forEach { (name, value) -> builder.header(name, value) }
            val response = http.send(
                builder.POST(HttpRequest.BodyPublishers.ofByteArray(encodeCanvasRequest(trackUri))).build(),
                HttpResponse.BodyHandlers.ofByteArray(),
            )
            if (response.statusCode() in 200..299) response.body() else null
        }.getOrNull() ?: return null

        // The structured parse first — it can tell this track's own clip apart from another one
        // bundled into the same response. The regex is the fallback, needing only a *.cnvs.mp4 URL
        // to be sitting in the bytes as plain text.
        val hits = decodeCanvasResponse(bytes)
        hits.firstOrNull { it.trackUri == trackUri }?.url?.let { return it }
        hits.firstOrNull()?.url?.let { return it }
        return CANVAS_URL_REGEX.find(String(bytes, Charsets.ISO_8859_1))?.value
    }

    /** `CanvasRequest { repeated Track tracks = 1; Track { string track_uri = 1; } }` */
    internal fun encodeCanvasRequest(trackUri: String): ByteArray {
        val track = ByteArrayOutputStream().apply { writeLengthDelimited(1, trackUri.toByteArray()) }
        return ByteArrayOutputStream().apply { writeLengthDelimited(1, track.toByteArray()) }.toByteArray()
    }

    /**
     * `CanvasResponse { repeated Canvas canvases = 1; }`, `Canvas { id = 1; canvas_url = 2;
     * track_uri = 5; }` — only the fields this needs are read.
     */
    internal fun decodeCanvasResponse(bytes: ByteArray): List<CanvasHit> = runCatching {
        val hits = mutableListOf<CanvasHit>()
        val reader = ProtoReader(bytes)
        while (reader.hasMore()) {
            val tag = reader.readTag() ?: break
            if (tag shr 3 == 1 && tag and 7 == 2) {
                decodeCanvas(reader.readBytes())?.let(hits::add)
            } else {
                reader.skip(tag)
            }
        }
        hits
    }.getOrElse { emptyList() }

    private fun decodeCanvas(bytes: ByteArray): CanvasHit? = runCatching {
        var url: String? = null
        var trackUri: String? = null
        val reader = ProtoReader(bytes)
        while (reader.hasMore()) {
            val tag = reader.readTag() ?: break
            when {
                tag shr 3 == 2 && tag and 7 == 2 -> url = String(reader.readBytes(), StandardCharsets.UTF_8)
                tag shr 3 == 5 && tag and 7 == 2 -> trackUri = String(reader.readBytes(), StandardCharsets.UTF_8)
                else -> reader.skip(tag)
            }
        }
        url?.let { CanvasHit(it, trackUri) }
    }.getOrNull()

    // ── Plumbing ─────────────────────────────────────────────────────────

    /** The client token on top of the bearer: both endpoints turn away a bearer-only request with
     * a 429 that reads exactly like rate limiting. Omitted rather than fatal when minting fails. */
    private fun authHeaders(token: String): Map<String, String> = buildMap {
        put("Authorization", "Bearer $token")
        DesktopSpotifyToken.clientToken()?.let { put("Client-Token", it) }
    }

    private fun get(url: String, token: String): JsonObject? {
        val response = runCatching {
            val builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", CANVAS_UA)
            authHeaders(token).forEach { (name, value) -> builder.header(name, value) }
            http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString())
        }.getOrElse {
            DesktopTrackLog.log("canvas: Spotify ${url.substringBefore('?')} threw: ${it.message}")
            return null
        }
        if (response.statusCode() !in 200..299) {
            DesktopTrackLog.log(
                "canvas: Spotify ${url.substringBefore('?')} answered ${response.statusCode()}: ${response.body().take(200)}",
            )
            return null
        }
        return runCatching { json.parseToJsonElement(response.body()).jsonObject }.getOrNull()
    }

    private fun url(base: String, params: List<Pair<String, String>>): String =
        params.joinToString("&", prefix = "$base?") { (name, value) ->
            "$name=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
        }

    /** A string field, or null when Spotify put something other than a string there. */
    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
}

// ── A protobuf reader, small enough not to be worth a dependency ────────────

private class ProtoReader(private val bytes: ByteArray) {
    private var at = 0

    fun hasMore(): Boolean = at < bytes.size

    fun readTag(): Int? = if (hasMore()) readVarint().toInt() else null

    fun readBytes(): ByteArray {
        val length = readVarint().toInt()
        val end = (at + length).coerceAtMost(bytes.size)
        val slice = bytes.copyOfRange(at, end)
        at = end
        return slice
    }

    fun skip(tag: Int) {
        when (tag and 7) {
            0 -> readVarint()
            1 -> at += 8
            2 -> readBytes()
            5 -> at += 4
            else -> at = bytes.size
        }
    }

    private fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (at < bytes.size && shift < 64) {
            val byte = bytes[at++].toInt()
            result = result or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) break
            shift += 7
        }
        return result
    }
}

private fun ByteArrayOutputStream.writeLengthDelimited(field: Int, payload: ByteArray) {
    writeVarint((field shl 3 or 2).toLong())
    writeVarint(payload.size.toLong())
    write(payload)
}

private fun ByteArrayOutputStream.writeVarint(value: Long) {
    var remaining = value
    while (true) {
        if (remaining and 0x7FL.inv() == 0L) {
            write(remaining.toInt())
            return
        }
        write((remaining and 0x7F or 0x80).toInt())
        remaining = remaining ushr 7
    }
}

/**
 * The bearer Spotify's own web player mints for itself.
 *
 * Same as Android's `SpotifyToken`: the real player is loaded offscreen (JavaFX's WebEngine here)
 * with the listener's cookie, and the token it mints is read off its own `/api/token` call. Signing
 * that request ourselves with the bundle's TOTP secret breaks whenever Spotify rotates the secret,
 * and even a correctly signed token is refused downstream with a 429.
 */
internal object DesktopSpotifyToken {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile private var cachedAccessToken: String? = null
    @Volatile private var accessTokenExpiresAtMs = 0L
    @Volatile private var cachedClientId: String? = null
    @Volatile private var cachedClientToken: String? = null
    @Volatile private var clientTokenExpiresAtMs = 0L
    @Volatile private var retryAfterMs = 0L
    @Volatile private var session: Pair<String, String>? = null

    /** The listener's `sp_dc` cookie. Nothing here works without it. */
    fun cookie(): String = DesktopPersistence().string(KEY_SPDC).trim()

    fun setCookie(value: String) {
        DesktopPersistence().saveString(KEY_SPDC, value.trim())
        cachedAccessToken = null
        accessTokenExpiresAtMs = 0L
        retryAfterMs = 0L
    }

    @Synchronized
    fun accessToken(): String? {
        val cookie = cookie().ifBlank { return null }
        val now = System.currentTimeMillis()
        cachedAccessToken?.let { if (now < accessTokenExpiresAtMs - 30_000) return it }
        if (now < retryAfterMs) return null

        val root = harvest(cookie)
        val token = root?.get("accessToken")?.jsonPrimitive?.contentOrNull
        if (token.isNullOrBlank()) {
            // Backed off rather than retried per track: a harvest loads the whole web player.
            retryAfterMs = now + RETRY_MS
            DesktopTrackLog.log("canvas: Spotify's web player did not hand over an access token")
            return null
        }
        cachedAccessToken = token
        accessTokenExpiresAtMs = root["accessTokenExpirationTimestampMs"]
            ?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?.takeIf { it > now } ?: (now + 3_600_000)
        cachedClientId = root["clientId"]?.jsonPrimitive?.contentOrNull
        root["clientToken"]?.jsonPrimitive?.contentOrNull?.let {
            cachedClientToken = it
            clientTokenExpiresAtMs = accessTokenExpiresAtMs
        }
        return token
    }

    /**
     * Loads open.spotify.com offscreen with the cookie applied and returns the body of the first
     * logged-in `/api/token` response the page receives. Blocks the caller; never call on the FX
     * thread.
     */
    private fun harvest(cookie: String): JsonObject? {
        if (Platform.isFxApplicationThread() || !DesktopPoTokenWebView.available) return null
        val result = CompletableFuture<JsonObject?>()
        val bridge = TokenBridge(result)
        // A fresh profile per harvest: the player skips minting when a live token sits in storage.
        val profile = Files.createTempDirectory("bitchord-spotify").toFile()
        var engine: WebEngine? = null

        Platform.runLater {
            runCatching {
                val web = WebEngine().also { engine = it }
                web.userDataDirectory = profile
                web.userAgent = CANVAS_UA
                // The first WebEngine installs its cookie store as the JVM default.
                CookieHandler.getDefault()?.put(
                    URI.create("https://open.spotify.com/"),
                    mapOf("Set-Cookie" to listOf("sp_dc=$cookie; Domain=.spotify.com; Path=/; Secure")),
                )
                val hook = {
                    runCatching {
                        (web.executeScript("window") as JSObject).setMember(BRIDGE_NAME, bridge)
                        web.executeScript(HOOK_SCRIPT)
                        web.executeScript(HEADER_SCRIPT)
                    }.onFailure { DesktopTrackLog.log("canvas: token hook not installed: ${it.message}") }
                }
                web.loadWorker.stateProperty().addListener { _, _, state ->
                    when (state) {
                        // JavaFX has no hook point before the page's scripts run; see onHeaders.
                        Worker.State.SUCCEEDED -> hook()
                        Worker.State.FAILED -> {
                            DesktopTrackLog.log("canvas: web player failed to load: ${web.loadWorker.exception?.message}")
                            result.complete(null)
                        }
                        else -> Unit
                    }
                }
                web.load("https://open.spotify.com/")
            }.onFailure { result.complete(null) }
        }

        return try {
            result.get(HARVEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            DesktopTrackLog.log("canvas: no logged-in token within ${HARVEST_TIMEOUT_MS / 1000}s; page: ${describePage(engine)}")
            null
        } finally {
            java.lang.ref.Reference.reachabilityFence(bridge) // JavaFX holds bridges weakly.
            Platform.runLater {
                runCatching { engine?.load("about:blank") }
                profile.deleteRecursively()
            }
        }
    }

    /** Where the page got to, for the log when a harvest times out. Waits briefly on the FX thread. */
    private fun describePage(engine: WebEngine?): String {
        val web = engine ?: return "never created"
        val described = CompletableFuture<String>()
        Platform.runLater {
            described.complete(
                runCatching {
                    web.executeScript(
                        "location.href + ' | title=' + document.title + ' | hook=' + !!window.__bitchordTokenHook + " +
                            "' | text=' + (document.body ? document.body.innerText : '').replace(/\\s+/g, ' ').slice(0, 200)",
                    ).toString()
                }.getOrElse { "unreadable: ${it.message}" },
            )
        }
        return runCatching { described.get(3, TimeUnit.SECONDS) }.getOrDefault("FX thread busy")
    }

    /** What the hooked page calls with each raw `/api/token` body. Public for LiveConnect. */
    class TokenBridge internal constructor(private val result: CompletableFuture<JsonObject?>) {
        fun onTokenPayload(payload: String?) {
            if (payload.isNullOrBlank() || result.isDone) return
            val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull()
            if (root == null) {
                DesktopTrackLog.log("canvas: /api/token answered with something else: ${payload.take(160)}")
                return
            }
            // The player mints an anonymous token before the cookie counts; that one reads no canvases.
            if (root["isAnonymous"]?.jsonPrimitive?.contentOrNull == "true") {
                DesktopTrackLog.log("canvas: /api/token gave an anonymous token; waiting for the logged-in one")
                return
            }
            if (root["accessToken"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) {
                DesktopTrackLog.log("canvas: /api/token gave no token; keys=${root.keys}")
                return
            }
            result.complete(root)
        }

        /**
         * The headers off one of the page's own authenticated requests. JavaFX gives no hook point
         * before the page's scripts run, so the `/api/token` call itself is usually missed, but
         * everything the player sends to spclient afterwards carries the bearer it got.
         */
        fun onHeaders(authorization: String?, clientToken: String?) {
            if (result.isDone) return
            val bearer = authorization?.removePrefix("Bearer ")?.trim()
            if (bearer.isNullOrBlank()) return
            result.complete(
                buildJsonObject {
                    put("accessToken", bearer)
                    // The real expiry came with the missed `/api/token` answer; tokens live an
                    // hour and this one was minted moments ago.
                    put("accessTokenExpirationTimestampMs", System.currentTimeMillis() + SNIFFED_TOKEN_LIFETIME_MS)
                    if (!clientToken.isNullOrBlank()) put("clientToken", clientToken)
                },
            )
        }

    }

    /**
     * The second header these endpoints demand alongside the bearer. Best-effort: a caller with no
     * client token sends the bearer alone and takes whatever the endpoint does with that.
     */
    @Synchronized
    fun clientToken(): String? {
        val now = System.currentTimeMillis()
        cachedClientToken?.let { if (now < clientTokenExpiresAtMs - 30_000) return it }
        val clientId = cachedClientId ?: return null
        val (clientVersion, deviceId) = session() ?: return null

        val payload = buildJsonObject {
            putJsonObject("client_data") {
                put("client_version", clientVersion)
                put("client_id", clientId)
                putJsonObject("js_sdk_data") {
                    put("device_brand", "unknown")
                    put("device_model", "unknown")
                    put("os", "linux")
                    put("os_version", System.getProperty("os.version").orEmpty())
                    put("device_id", deviceId)
                    put("device_type", "computer")
                }
            }
        }
        val body = runCatching {
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
            val response = client.send(
                HttpRequest.newBuilder(URI.create("https://clienttoken.spotify.com/v1/clienttoken"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    // The bare header, deliberately: clienttoken 400s on a charset suffix.
                    .header("Content-Type", "application/json")
                    .header("User-Agent", CANVAS_UA)
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            if (response.statusCode() in 200..299) response.body() else null
        }.getOrNull() ?: return null

        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        if (root["response_type"]?.jsonPrimitive?.contentOrNull != "RESPONSE_GRANTED_TOKEN_RESPONSE") return null
        val granted = root["granted_token"]?.jsonObject ?: return null
        val token = granted["token"]?.jsonPrimitive?.contentOrNull ?: return null
        val ttl = granted["expires_after_seconds"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3600L
        cachedClientToken = token
        clientTokenExpiresAtMs = now + ttl * 1000
        return token
    }

    /** The web player's build version, read off its own page, plus a device id to call ourselves. */
    private fun session(): Pair<String, String>? {
        session?.let { return it }
        val html = canvasGet("https://open.spotify.com") ?: return null
        val configB64 = Regex("""<script id="appServerConfig" type="text/plain">([^<]+)</script>""")
            .find(html)?.groupValues?.get(1) ?: return null
        val clientVersion = runCatching {
            val decoded = String(Base64.getDecoder().decode(configB64), StandardCharsets.UTF_8)
            json.parseToJsonElement(decoded).jsonObject["clientVersion"]?.jsonPrimitive?.contentOrNull
        }.getOrNull() ?: return null
        return (clientVersion to UUID.randomUUID().toString()).also { session = it }
    }

    private const val BRIDGE_NAME = "BitChordSpotifyTokenBridge"
    private const val HARVEST_TIMEOUT_MS = 25_000L

    private const val SNIFFED_TOKEN_LIFETIME_MS = 45L * 60 * 1000

    /** Reports the bearer and client token off the page's own requests to Spotify's backends. */
    private val HEADER_SCRIPT = """
        (function () {
          if (window.__bitchordHeaderHook) return;
          window.__bitchordHeaderHook = true;
          var isSpotify = function (u) {
            try { return /^https:\/\/[^\/]*(spclient|api)\.spotify\.com\//.test(String(u)); } catch (e) { return false; }
          };
          var pick = function (h, name) {
            try {
              if (!h) return null;
              if (typeof h.get === 'function') return h.get(name);
              if (Array.isArray(h)) {
                for (var i = 0; i < h.length; i++) if (String(h[i][0]).toLowerCase() === name) return h[i][1];
                return null;
              }
              for (var k in h) if (k.toLowerCase() === name) return h[k];
            } catch (e) {}
            return null;
          };
          var report = function (auth, client) {
            try { if (auth) $BRIDGE_NAME.onHeaders(String(auth), client ? String(client) : null); } catch (e) {}
          };
          var origFetch = window.fetch;
          if (origFetch) {
            window.fetch = function (input, init) {
              var url = (input && input.url) ? input.url : input;
              if (isSpotify(url)) {
                var h = (init && init.headers) || (input && input.headers);
                report(pick(h, 'authorization'), pick(h, 'client-token'));
              }
              return origFetch.apply(this, arguments);
            };
          }
          var origOpen = XMLHttpRequest.prototype.open;
          XMLHttpRequest.prototype.open = function (method, url) {
            this.__bitchordSpotify = isSpotify(url);
            this.__bitchordHeaders = {};
            return origOpen.apply(this, arguments);
          };
          var origSet = XMLHttpRequest.prototype.setRequestHeader;
          XMLHttpRequest.prototype.setRequestHeader = function (name, value) {
            try { if (this.__bitchordHeaders) this.__bitchordHeaders[String(name).toLowerCase()] = value; } catch (e) {}
            return origSet.apply(this, arguments);
          };
          var origSend = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.send = function () {
            if (this.__bitchordSpotify && this.__bitchordHeaders) {
              report(this.__bitchordHeaders['authorization'], this.__bitchordHeaders['client-token']);
            }
            return origSend.apply(this, arguments);
          };
        })();
    """.trimIndent()

    /** Android's hook, unchanged: reports every `/api/token` response, fetch or XHR. */
    private val HOOK_SCRIPT = """
        (function () {
          if (window.__bitchordTokenHook) return;
          window.__bitchordTokenHook = true;
          var report = function (body) {
            try { $BRIDGE_NAME.onTokenPayload(body); } catch (e) {}
          };
          var isToken = function (u) {
            try { return String(u).indexOf('/api/token') !== -1; } catch (e) { return false; }
          };
          var origFetch = window.fetch;
          if (origFetch) {
            window.fetch = function (input, init) {
              var url = (input && input.url) ? input.url : input;
              var result = origFetch.apply(this, arguments);
              if (isToken(url)) {
                try {
                  result.then(function (res) {
                    res.clone().text().then(report).catch(function () {});
                  }).catch(function () {});
                } catch (e) {}
              }
              return result;
            };
          }
          var origOpen = XMLHttpRequest.prototype.open;
          XMLHttpRequest.prototype.open = function (method, url) {
            this.__bitchordUrl = url;
            return origOpen.apply(this, arguments);
          };
          var origSend = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.send = function () {
            var xhr = this;
            try {
              xhr.addEventListener('load', function () {
                if (isToken(xhr.__bitchordUrl)) {
                  try { report(xhr.responseText); } catch (e) {}
                }
              });
            } catch (e) {}
            return origSend.apply(this, arguments);
          };
        })();
    """.trimIndent()

    private const val RETRY_MS = 30L * 60 * 1000

    internal const val KEY_SPDC = "spotify_spdc_token"
}
