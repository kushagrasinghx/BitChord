package com.music.pexpo.data.canvas

import com.music.pexpo.data.DebugLog as Log
import com.music.pexpo.data.Http
import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

/**
 * Spotify's own Canvas — the feature these other providers are named after.
 *
 * There is no public API for it. The web player fetches it from an internal
 * endpoint ([CANVAS_URL]) using protobuf over HTTP, authenticated with a
 * bearer token minted from the listener's own session — see [SpotifyToken].
 * That means this source is the one of the four that needs the listener to
 * hand something over first (Settings > their `sp_dc` session cookie); with
 * no cookie set [SpotifyToken.accessToken] returns null before any request is
 * made, so this is a free no-op until then.
 *
 * The wire format is hand-rolled rather than generated: the message shapes
 * are tiny — one string in, a handful of strings out — and pulling in protoc
 * codegen for two fields is not worth the build-time dependency. Field
 * numbers below are Spotify's own, not chosen by us; get them wrong and the
 * response silently parses to nothing rather than failing loudly, so they
 * are commented with what each one actually is.
 */
object SpotifyCanvas {

    private const val TAG = "SpotifyCanvas"
    private const val SEARCH_URL = "https://api.spotify.com/v1/search"
    private const val ALBUM_TRACKS_URL = "https://api.spotify.com/v1/albums"
    private const val CANVAS_URL = "https://spclient.wg.spotify.com/canvaz-cache/v0/canvases"
    private const val PATHFINDER_URL = "https://api-partner.spotify.com/pathfinder/v1/query"

    /** The persisted query id for the web player's own track search. */
    private const val PATHFINDER_SEARCH_HASH =
        "bc1ca2fcd0ba1013a0fc88e6cc4f190af501851e3dafd3e1ef85840297694428"

    /**
     * spclient gates this path to Spotify's own apps by user agent; the web
     * player's own UA is turned away, so this wears a mobile client's instead.
     */
    private const val SPOTIFY_APP_UA = "Spotify/9.0.34.593 iOS/18.4 (iPhone15,3)"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val CANVAS_URL_REGEX = Regex("""https://[^"'\s\x00-\x1F]+\.cnvs\.mp4""")

    private data class TrackHit(val uri: String, val title: String, val artist: String, val album: String?)

    suspend fun search(title: String, artist: String, album: String?): CanvasArtwork? {
        val token = SpotifyToken.accessToken()
        if (token == null) {
            Log.d(TAG, "no access token (cookie unset or mint failed); skipping")
            return null
        }

        // Pathfinder first: it's the search the web player's own search box
        // calls, and in practice the one this kind of token is reliably let
        // near — the plain REST endpoint below answers some requests and
        // 429s others in a pattern that hasn't been possible to pin down from
        // outside Spotify. Kept as the fallback rather than dropped, since a
        // client token failing to mint takes Pathfinder down with it but
        // leaves REST still reachable.
        val hit = searchViaPathfinder(title, artist, album, token)
            ?: searchViaRest(title, artist, album, token)
        if (hit == null) {
            Log.d(TAG, "no canvas for '$title' by '$artist' (no matching track found)")
            return null
        }

        val canvasUrl = fetchCanvasUrl(hit.uri, token)
        if (canvasUrl == null) {
            Log.d(TAG, "no canvas for '$title' by '$artist' (matched '${hit.title}', no clip)")
            return null
        }
        Log.d(TAG, "canvas for '${hit.title}' by '${hit.artist}'")
        return CanvasArtwork(
            url = canvasUrl,
            title = hit.title,
            artist = hit.artist,
            album = hit.album,
            source = CanvasSource.SPOTIFY,
        )
    }

    /**
     * Doesn't re-check the hit's title and artist the way [searchViaRest]
     * does: the fields a GraphQL response like this carries aren't published
     * anywhere, so pulling `name`/`artists` back out the way the REST
     * response's documented shape allows would be a guess. Spotify's own
     * relevance ranking on a "title artist album" query is trusted instead,
     * the same way [firstTrackUri] trusts an album's own track listing rather
     * than re-checking it.
     */
    private fun searchViaPathfinder(title: String, artist: String, album: String?, token: String): TrackHit? {
        val clientToken = SpotifyToken.clientToken()
        if (clientToken == null) {
            Log.d(TAG, "no client token; skipping pathfinder search")
            return null
        }
        val searchTerm = listOfNotNull(title, artist, album).joinToString(" ")
        val variables = buildJsonObject {
            put("searchTerm", searchTerm)
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

        val url = PATHFINDER_URL.toHttpUrl().newBuilder()
            .addQueryParameter("operationName", "searchTracks")
            .addQueryParameter("variables", variables)
            .addQueryParameter("extensions", extensions)
            .build()
            .toString()

        val headers = mapOf(
            "Authorization" to "Bearer $token",
            "Client-Token" to clientToken,
            "App-platform" to "WebPlayer",
            "Accept" to "application/json",
            "User-Agent" to CANVAS_UA,
        )
        val (code, body) = canvasGetWithStatus(url, headers)
        if (body == null) {
            Log.w(TAG, "pathfinder search failed, http $code")
            return null
        }
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        val firstItem = root?.get("data")?.jsonObject
            ?.get("searchV2")?.jsonObject
            ?.get("tracksV2")?.jsonObject
            ?.get("items")?.jsonArray
            ?.firstOrNull()
            ?.jsonObject?.get("item")?.jsonObject
            ?.get("data")?.jsonObject
        if (firstItem == null) {
            Log.w(TAG, "pathfinder response had no hit (http $code): ${body.take(200)}")
            return null
        }
        val uri = firstItem["uri"]?.jsonPrimitive?.contentOrNull
            ?: firstItem["id"]?.jsonPrimitive?.contentOrNull?.let { "spotify:track:$it" }
        if (uri == null) {
            Log.w(TAG, "pathfinder hit had neither uri nor id")
            return null
        }
        return TrackHit(uri, title, artist, album)
    }

    private fun searchViaRest(title: String, artist: String, album: String?, token: String): TrackHit? {
        val query = listOfNotNull(title, artist, album).joinToString(" ")
        val url = SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("type", "track")
            .addQueryParameter("limit", "10")
            .build()
            .toString()

        val (code, body) = canvasGetWithStatus(url, authHeaders(token))
        if (body == null) {
            Log.w(TAG, "search request failed, http $code")
            return null
        }
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        if (root == null) {
            Log.w(TAG, "search response wasn't JSON (http $code)")
            return null
        }
        val items = root["tracks"]?.jsonObject?.get("items")?.jsonArray
        if (items == null) {
            Log.w(TAG, "search response had no tracks.items (http $code): ${body.take(200)}")
            return null
        }

        for (item in items) {
            val track = item as? JsonObject ?: continue
            val trackTitle = track["name"]?.jsonPrimitive?.contentOrNull ?: continue
            val artists = track["artists"]?.jsonArray
                ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                .orEmpty()
            if (!isMatch(trackTitle, artists, title, artist)) continue

            val uri = track["uri"]?.jsonPrimitive?.contentOrNull ?: continue
            val albumName = track["album"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
            return TrackHit(uri, trackTitle, artists.joinToString(", ").ifBlank { artist }, albumName)
        }
        return null
    }

    /**
     * A release's canvas, read off its first track — Spotify hangs Canvas off
     * individual tracks, not the release itself, so there is no album-level
     * lookup to make directly.
     */
    suspend fun searchAlbum(album: String, artist: String): CanvasArtwork? {
        val token = SpotifyToken.accessToken() ?: return null
        val url = SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("q", "$album $artist")
            .addQueryParameter("type", "album")
            .addQueryParameter("limit", "10")
            .build()
            .toString()

        val body = canvasGet(url, authHeaders(token)) ?: return null
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val items = root["albums"]?.jsonObject?.get("items")?.jsonArray ?: return null

        for (item in items) {
            val record = item as? JsonObject ?: continue
            val recordTitle = record["name"]?.jsonPrimitive?.contentOrNull ?: continue
            val artists = record["artists"]?.jsonArray
                ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                .orEmpty()
            if (!isMatch(recordTitle, artists, album, artist)) continue

            val albumId = record["id"]?.jsonPrimitive?.contentOrNull ?: continue
            val trackUri = firstTrackUri(albumId, token) ?: continue
            val canvasUrl = fetchCanvasUrl(trackUri, token) ?: continue

            Log.d(TAG, "canvas for album '$recordTitle' by ${artists.joinToString()}")
            return CanvasArtwork(
                url = canvasUrl,
                title = recordTitle,
                artist = artists.joinToString(", ").ifBlank { null },
                album = recordTitle,
                source = CanvasSource.SPOTIFY,
            )
        }
        return null
    }

    private fun firstTrackUri(albumId: String, token: String): String? {
        val url = "$ALBUM_TRACKS_URL/$albumId/tracks".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "1")
            .build()
            .toString()
        val body = canvasGet(url, authHeaders(token)) ?: return null
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        return root["items"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull
    }

    /**
     * [SpotifyToken.clientToken] on top of the bearer — api.spotify.com and
     * spclient both turn away a request carrying only the bearer with a 429,
     * which reads exactly like rate limiting on the very first request of a
     * session until you notice that's what it always says. Sent whenever
     * minting one succeeds; omitted otherwise rather than failing the call,
     * since some of these endpoints still answer without it.
     */
    private fun authHeaders(token: String): Map<String, String> {
        val headers = mutableMapOf("Authorization" to "Bearer $token", "User-Agent" to CANVAS_UA)
        SpotifyToken.clientToken()?.let { headers["Client-Token"] = it }
        return headers
    }

    private fun isMatch(gotName: String, gotArtists: List<String>, wantName: String, wantArtist: String): Boolean {
        if (gotName.normalizeForMatch() != wantName.normalizeForMatch()) return false
        val wanted = splitArtists(wantArtist)
        val credited = gotArtists.map { it.normalizeForMatch() }.filter { it.isNotBlank() }
        if (wanted.isEmpty() || credited.isEmpty()) return false
        return wanted.all { want -> credited.any { it == want } }
    }

    // ---- canvaz-cache: protobuf request/response -----------------------

    private data class CanvasHit(val id: String?, val url: String, val trackUri: String?)

    private fun fetchCanvasUrl(trackUri: String, token: String): String? {
        val requestBody = encodeCanvasRequest(trackUri)
            .toRequestBody("application/protobuf".toMediaType())
        val request = Request.Builder()
            .url(CANVAS_URL)
            .post(requestBody)
            .apply { authHeaders(token).forEach { (name, value) -> header(name, value) } }
            .header("Accept", "application/protobuf")
            .header("Accept-Language", "en")
            .header("User-Agent", SPOTIFY_APP_UA)
            .build()

        val bytes = runCatching {
            Http.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "canvaz-cache http ${response.code} for $trackUri")
                }
                if (response.isSuccessful) response.body?.bytes() else null
            }
        }.onFailure { Log.w(TAG, "canvaz-cache request threw: ${it.message}") }.getOrNull() ?: return null

        // The structured parse first — it can tell this track's own clip apart
        // from another one Spotify bundled in the same response. The regex is
        // the fallback: it only needs a *.cnvs.mp4 URL to be sitting somewhere
        // in the bytes as a plain UTF-8 string, so it still finds a clip if a
        // field number above turns out to be stale even though the search
        // above already picked the right track.
        val hits = decodeCanvasResponse(bytes)
        val structured = hits.firstOrNull { it.trackUri == trackUri }?.url ?: hits.firstOrNull()?.url
        if (structured != null) return structured

        val fallback = CANVAS_URL_REGEX.find(String(bytes, Charsets.ISO_8859_1))?.value
        if (fallback == null) {
            Log.d(TAG, "canvaz-cache had nothing for $trackUri")
        } else {
            Log.d(TAG, "canvaz-cache url recovered by regex fallback for $trackUri")
        }
        return fallback
    }

    /** `CanvasRequest { repeated Track tracks = 1; Track { string track_uri = 1; } }` */
    private fun encodeCanvasRequest(trackUri: String): ByteArray {
        val track = ByteArrayOutputStream().let { buffer ->
            val out = CodedOutputStream.newInstance(buffer)
            out.writeString(1, trackUri)
            out.flush()
            buffer.toByteArray()
        }
        val request = ByteArrayOutputStream()
        val out = CodedOutputStream.newInstance(request)
        out.writeByteArray(1, track)
        out.flush()
        return request.toByteArray()
    }

    /**
     * `CanvasResponse { repeated Canvas canvases = 1; }`, `Canvas { id = 1;
     * canvas_url = 2; ...; track_uri = 5; ... }` — only the fields this needs
     * are read, everything else is skipped rather than modelled.
     */
    private fun decodeCanvasResponse(bytes: ByteArray): List<CanvasHit> = runCatching {
        val hits = mutableListOf<CanvasHit>()
        val input = CodedInputStream.newInstance(bytes)
        while (!input.isAtEnd) {
            val tag = input.readTag()
            if (tag == 0) break
            if (tag ushr 3 == 1) {
                decodeCanvas(input.readByteArray())?.let(hits::add)
            } else {
                input.skipField(tag)
            }
        }
        hits
    }.getOrElse { emptyList() }

    private fun decodeCanvas(bytes: ByteArray): CanvasHit? = runCatching {
        var id: String? = null
        var url: String? = null
        var trackUri: String? = null
        val input = CodedInputStream.newInstance(bytes)
        while (!input.isAtEnd) {
            val tag = input.readTag()
            if (tag == 0) break
            when (tag ushr 3) {
                1 -> id = input.readString()
                2 -> url = input.readString()
                5 -> trackUri = input.readString()
                else -> input.skipField(tag)
            }
        }
        url?.let { CanvasHit(id, it, trackUri) }
    }.getOrNull()
}
