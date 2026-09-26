package com.music.bitchord.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.time.Duration
import java.util.Locale

/**
 * Where motion artwork comes from.
 *
 * Spotify's clips re-tint the backdrop as they loop; the rest settle on their first frame.
 */
enum class DesktopCanvasSource { SPOTIFY, OTHER }

// ── Matching, shared by every provider ──────────────────────────────────────

/**
 * Case, accents and punctuation all differ between YouTube Music, Apple and Tidal for the same
 * release. Fold all three away before comparing.
 */
internal fun String.normalizeForCanvasMatch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

internal fun splitCanvasArtists(raw: String): List<String> =
    raw.split(ARTIST_SEPARATORS)
        .map { it.normalizeForCanvasMatch() }
        .filter { it.isNotBlank() }

private val ARTIST_SEPARATORS = Regex(
    "(?:\\s*,\\s*|\\s*&\\s*|\\s+×\\s+|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)",
    RegexOption.IGNORE_CASE,
)

/** Exact on the name, and every credited artist we asked for present. */
internal fun canvasMatches(
    gotName: String,
    gotArtists: List<String>,
    wantName: String,
    wantArtist: String,
): Boolean {
    if (gotName.normalizeForCanvasMatch() != wantName.normalizeForCanvasMatch()) return false
    val wanted = splitCanvasArtists(wantArtist)
    val credited = gotArtists.map { it.normalizeForCanvasMatch() }.filter { it.isNotBlank() }
    if (wanted.isEmpty() || credited.isEmpty()) return false
    return wanted.all { want -> credited.any { it == want } }
}

// ── HTTP, shared by every provider ──────────────────────────────────────────

internal const val CANVAS_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

private val canvasClient: HttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

/**
 * A plain GET returning the body, or null for anything that isn't a 2xx or that throws.
 *
 * Canvas is decoration: no provider failure is allowed to reach the player.
 */
internal fun canvasGet(url: String, headers: Map<String, String> = emptyMap()): String? = runCatching {
    val builder = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(15))
        .header("User-Agent", CANVAS_UA)
    headers.forEach { (name, value) -> builder.header(name, value) }
    val response = canvasClient.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString())
    if (response.statusCode() in 200..299) response.body() else null
}.getOrNull()

private fun query(base: String, params: List<Pair<String, String>>): String =
    params.joinToString("&", prefix = "$base?") { (name, value) ->
        "$name=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
    }

private val canvasJson = Json { ignoreUnknownKeys = true; isLenient = true }

private fun parse(body: String?): JsonObject? =
    body?.let { runCatching { canvasJson.parseToJsonElement(it).jsonObject }.getOrNull() }

/** A string field, or null when the addon put something other than a string there. */
private fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull

// ── Apple Music ─────────────────────────────────────────────────────────────

/**
 * Apple's motion artwork, read through the catalog API the web player uses.
 *
 * The bearer token isn't published and rotates every few weeks, so it is read the way the browser
 * gets it: load the web player, find the JS bundle it pulls in, and pick the token out of it.
 */
internal object DesktopAppleMusicCanvas {

    private const val AMP = "https://amp-api.music.apple.com/v1/catalog"
    private const val WEB_PLAYER = "https://music.apple.com/us/browse"

    /** Motion artwork is per storefront; use this machine's if it looks sane. */
    private val storefront: String by lazy {
        Locale.getDefault().country.takeIf { it.length == 2 }?.lowercase(Locale.ROOT) ?: "us"
    }

    fun search(title: String, artist: String, album: String?): DesktopCanvasArtwork? {
        val bearer = token() ?: return null
        // The catalog search is term-based, so fold everything known into the term: an artist name
        // alone pulls in their whole discography for the scoring to then reject.
        val term = buildString {
            if (!title.contains(artist, ignoreCase = true)) append(artist).append(' ')
            append(title)
            if (!album.isNullOrBlank() && !title.contains(album, ignoreCase = true)) {
                append(' ').append(album)
            }
        }
        val url = query(
            "$AMP/$storefront/search",
            listOf(
                "term" to term,
                "types" to "songs",
                "limit" to "10",
                "extend" to "editorialVideo",
                "include" to "albums",
            ),
        )
        val hits = parse(get(url, bearer))
            ?.get("results")?.jsonObject
            ?.get("songs")?.jsonObject
            ?.get("data")?.jsonArray
            ?: return null

        val ranked = hits.mapNotNull { hit ->
            val song = hit as? JsonObject ?: return@mapNotNull null
            score(song, title, artist, album)?.let { it to song }
        }.sortedByDescending { it.first }

        for ((value, song) in ranked) {
            if (value < MIN_SCORE) break
            val attributes = song["attributes"]?.jsonObject ?: continue
            val songName = attributes.text("name")
            val songArtist = attributes.text("artistName")
            // Some searches carry the motion artwork inline, saving the album round trip.
            attributes["editorialVideo"]?.jsonObject?.let { video ->
                motionUrls(video)?.let { (primary, alternate) ->
                    return DesktopCanvasArtwork(
                        primary,
                        alternate,
                        songName,
                        songArtist,
                        attributes.text("albumName"),
                    )
                }
            }
            val albumId = albumId(song) ?: continue
            fetchAlbum(albumId, bearer, songName, songArtist)?.let { return it }
        }
        return null
    }

    /** Motion artwork for a release. Albums carry `editorialVideo` inline, so there is no second
     * lookup to resolve an id first. */
    fun searchAlbum(album: String, artist: String): DesktopCanvasArtwork? {
        val bearer = token() ?: return null
        val term = if (album.contains(artist, ignoreCase = true)) album else "$artist $album"
        val url = query(
            "$AMP/$storefront/search",
            listOf("term" to term, "types" to "albums", "limit" to "10", "extend" to "editorialVideo"),
        )
        val hits = parse(get(url, bearer))
            ?.get("results")?.jsonObject
            ?.get("albums")?.jsonObject
            ?.get("data")?.jsonArray
            ?: return null

        val ranked = hits.mapNotNull { hit ->
            val record = hit as? JsonObject ?: return@mapNotNull null
            score(record, album, artist, album, albumIsSelf = true)?.let { it to record }
        }.sortedByDescending { it.first }

        for ((value, record) in ranked) {
            if (value < MIN_SCORE) break
            val attributes = record["attributes"]?.jsonObject ?: continue
            val name = attributes.text("name")
            if (name != null && isCompilation(name)) continue
            val video = attributes["editorialVideo"]?.jsonObject ?: continue
            val (primary, alternate) = motionUrls(video) ?: continue
            return DesktopCanvasArtwork(primary, alternate, name, attributes.text("artistName"), name)
        }
        return null
    }

    private fun fetchAlbum(
        albumId: String,
        bearer: String,
        songTitle: String?,
        songArtist: String?,
    ): DesktopCanvasArtwork? {
        val url = query("$AMP/$storefront/albums/$albumId", listOf("extend" to "editorialVideo"))
        val album = parse(get(url, bearer))
            ?.get("data")?.jsonArray?.firstOrNull()?.jsonObject
            ?: return null
        val attributes = album["attributes"]?.jsonObject ?: return null
        val albumName = attributes.text("name").orEmpty()
        if (isCompilation(albumName)) return null
        val video = attributes["editorialVideo"]?.jsonObject ?: return null
        val (primary, alternate) = motionUrls(video) ?: return null
        return DesktopCanvasArtwork(
            url = primary,
            fallbackUrl = alternate,
            title = songTitle,
            artist = songArtist ?: attributes.text("artistName"),
            album = albumName,
        )
    }

    private fun albumId(song: JsonObject): String? {
        song["relationships"]?.jsonObject
            ?.get("albums")?.jsonObject
            ?.get("data")?.jsonArray?.firstOrNull()
            ?.jsonObject?.text("id")
            ?.let { return it.takeUnless { id -> id.startsWith("pl.") } }
        // Not every hit expands its relationships, but the web URL always ends in the album id.
        val url = song["attributes"]?.jsonObject?.text("url") ?: return null
        return url.substringAfter("/album/", "")
            .substringBefore("?")
            .substringAfterLast("/")
            .takeIf { it.isNotBlank() && it.all(Char::isDigit) }
    }

    /**
     * The square rendition first — it fills a square sleeve without cropping. The tall one is the
     * same clip framed for a phone and is only worth having as the retry.
     */
    private fun motionUrls(video: JsonObject): Pair<String, String?>? {
        fun link(key: String): String? = video[key]?.jsonObject?.let { asset ->
            asset.text("video") ?: asset.text("videoUrl") ?: asset.text("hlsUrl") ?: asset.text("url")
        }?.takeIf { it.isNotBlank() }
        val square = link("motionDetailSquare") ?: link("motionSquareVideo1x1")
        val raw = link("motionDetailRaw")
        val tall = link("motionDetailTall") ?: link("motionTallVideo3x4")
        val primary = square ?: raw ?: tall ?: return null
        val alternate = listOfNotNull(square, raw, tall).firstOrNull { it != primary }
        return primary to alternate
    }

    /**
     * The floor a hit has to clear. An exact artist and an exact title alone reach 25, so this only
     * admits a result that matched on both, or one that matched the artist plus a fuzzy title and
     * the right album.
     */
    private const val MIN_SCORE = 12

    /** How well a hit lines up, or null to reject it. Artist is a gate rather than a score: a clip
     * credited to someone else is never the right one, however well the title reads. */
    internal fun score(
        song: JsonObject,
        title: String,
        artist: String,
        album: String?,
        albumIsSelf: Boolean = false,
    ): Int? {
        val attributes = song["attributes"]?.jsonObject ?: return null
        val hitName = attributes.text("name").orEmpty()
        val hitArtist = attributes.text("artistName").orEmpty()
        val hitAlbum = if (albumIsSelf) hitName else attributes.text("albumName").orEmpty()
        if (isCompilation(hitName) || isCompilation(hitAlbum)) return null

        val wanted = splitCanvasArtists(artist)
        val credited = splitCanvasArtists(hitArtist)
        if (wanted.isEmpty() || credited.isEmpty()) return null
        if (!wanted.all { want -> credited.any { it == want } }) return null

        var score = 10
        val wantTitle = title.normalizeForCanvasMatch()
        val hitTitle = hitName.normalizeForCanvasMatch()
        score += when {
            hitTitle == wantTitle -> 15
            hitTitle.contains(wantTitle) || wantTitle.contains(hitTitle) -> 7
            // Same artist, different song — the mismatch that has to be kept out.
            else -> -10
        }
        if (!album.isNullOrBlank() && hitAlbum.isNotBlank()) {
            val wantAlbum = album.normalizeForCanvasMatch()
            val gotAlbum = hitAlbum.normalizeForCanvasMatch()
            score += when {
                gotAlbum == wantAlbum -> 20
                gotAlbum.contains(wantAlbum) || wantAlbum.contains(gotAlbum) -> 10
                else -> 0
            }
        }
        // A "(Deluxe)" on one side only is a different master, and often a different clip.
        for (word in EDITION_WORDS) {
            val inWanted = title.contains(word, ignoreCase = true)
            val inHit = hitName.contains(word, ignoreCase = true)
            if (inWanted && inHit) score += 5 else if (inHit) score -= 3
        }
        return score
    }

    private val EDITION_WORDS =
        listOf("deluxe", "expanded", "remastered", "remix", "version", "edit", "mix", "bonus")

    /** Editorial playlists have motion artwork of their own, and Apple returns them alongside
     * albums. Theirs belongs to the playlist, not the track. */
    internal fun isCompilation(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return COMPILATION_MARKERS.any { lower.contains(it) }
    }

    private val COMPILATION_MARKERS = listOf(
        "playlist", "set list", "essentials", "dj mix", "mixed",
        "apple music", "today's hits", "session",
    )

    // ── Token ────────────────────────────────────────────────────────────

    @Volatile private var cachedToken: String? = null
    @Volatile private var tokenExpiresAtMs = 0L
    @Volatile private var retryTokenAfterMs = 0L

    private fun get(url: String, bearer: String): String? = canvasGet(
        url,
        mapOf(
            "Authorization" to "Bearer $bearer",
            "Origin" to "https://music.apple.com",
            "Referer" to "https://music.apple.com/",
        ),
    )

    @Synchronized
    private fun token(): String? {
        val now = System.currentTimeMillis()
        cachedToken?.let { if (now < tokenExpiresAtMs - 60_000) return it }
        if (now < retryTokenAfterMs) return null

        val html = canvasGet(WEB_PLAYER)
        val scripts = html?.let {
            Regex("""/assets/index(?:-legacy)?[~-][A-Za-z0-9_-]+\.js""")
                .findAll(it).map(MatchResult::value).distinct().toList()
        }.orEmpty()

        for (path in scripts) {
            val script = canvasGet("https://music.apple.com$path") ?: continue
            val candidates = Regex("""ey[A-Za-z0-9_-]+\.ey[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
                .findAll(script)
                .map(MatchResult::value)
                .distinct()
                .mapNotNull { jwt -> expiry(jwt)?.let { jwt to it } }
                .filter { it.second > now }
                .toList()
            if (candidates.isEmpty()) continue
            val (jwt, expiresAt) = candidates.firstOrNull { isWebPlayerToken(it.first) } ?: candidates.first()
            cachedToken = jwt
            tokenExpiresAtMs = expiresAt
            return jwt
        }
        // A failed scrape backs off rather than retrying per track: if Apple has changed the page
        // shape, hammering it on every skip fixes nothing.
        retryTokenAfterMs = now + TOKEN_RETRY_MS
        DesktopTrackLog.log("canvas: could not read Apple Music's web player token")
        return null
    }

    private fun payload(jwt: String): JsonObject? = runCatching {
        val body = jwt.split('.').getOrNull(1) ?: return null
        val decoded = java.util.Base64.getUrlDecoder().decode(body.padEnd((body.length + 3) / 4 * 4, '='))
        canvasJson.parseToJsonElement(String(decoded, StandardCharsets.UTF_8)).jsonObject
    }.getOrNull()

    private fun expiry(jwt: String): Long? =
        payload(jwt)?.get("exp")?.jsonPrimitive?.contentOrNull?.toLongOrNull()?.times(1000)

    private fun isWebPlayerToken(jwt: String): Boolean =
        payload(jwt)?.text("root_https_origin")?.contains("music.apple.com") == true ||
            payload(jwt)?.get("root_https_origin")?.toString()?.contains("music.apple.com") == true

    private const val TOKEN_RETRY_MS = 30L * 60 * 1000
}

// ── Tidal ───────────────────────────────────────────────────────────────────

/**
 * Tidal's "video cover" — a square looping clip some albums ship instead of a still sleeve.
 *
 * Read through the public search endpoint Tidal's embeddable player uses, which needs no account.
 * Square and 1280px, so it drops into a sleeve without cropping.
 */
internal object DesktopTidalCanvas {

    private const val SEARCH = "https://api.tidal.com/v1/search"

    /** The token Tidal's public embed player ships; read-only, no account. */
    private const val EMBED_TOKEN = "vNVdglQOjFJJGG2U"

    /** Tidal's catalogue is regional, and so is which albums have a cover. */
    private val countryCode: String by lazy {
        Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase(Locale.ROOT) ?: "US"
    }

    fun search(title: String, artist: String, album: String?): DesktopCanvasArtwork? {
        val term = if (album.isNullOrBlank()) "$artist $title" else "$album $artist $title"
        val items = request(term, "TRACKS")?.get("tracks")?.jsonObject?.get("items")?.jsonArray
            ?: return null
        for (item in items) {
            val track = item as? JsonObject ?: continue
            val trackTitle = track.text("title") ?: continue
            // Tidal credits artists as separate objects, so this is the one service with no
            // separator to guess.
            val artists = track["artists"]?.jsonArray
                ?.mapNotNull { it.jsonObject.text("name") }
                .orEmpty()
            // Checked here rather than left to the caller: a search for one song returns ten, and
            // taking the first that has a cover would settle on the wrong track and stop looking.
            if (!canvasMatches(trackTitle, artists, title, artist)) continue
            val albumObj = track["album"]?.jsonObject
            val videoUrl = albumObj?.text("videoCover")?.let(::coverUrl) ?: continue
            return DesktopCanvasArtwork(
                url = videoUrl,
                title = trackTitle,
                artist = artists.joinToString(", ").ifBlank { null },
                album = albumObj.text("title"),
            )
        }
        return null
    }

    fun searchAlbum(album: String, artist: String): DesktopCanvasArtwork? {
        val items = request("$album $artist", "ALBUMS")?.get("albums")?.jsonObject?.get("items")?.jsonArray
            ?: return null
        for (item in items) {
            val record = item as? JsonObject ?: continue
            val recordTitle = record.text("title") ?: continue
            val artists = record["artists"]?.jsonArray
                ?.mapNotNull { it.jsonObject.text("name") }
                .orEmpty()
            // Without this a search for "SOS" settles on "Ctrl", which also has a cover.
            if (!canvasMatches(recordTitle, artists, album, artist)) continue
            val videoUrl = record.text("videoCover")?.let(::coverUrl) ?: continue
            return DesktopCanvasArtwork(
                url = videoUrl,
                title = recordTitle,
                artist = artists.joinToString(", ").ifBlank { null },
                album = recordTitle,
            )
        }
        return null
    }

    private fun request(term: String, types: String): JsonObject? = parse(
        canvasGet(
            query(
                SEARCH,
                listOf(
                    "query" to term,
                    "limit" to "10",
                    "types" to types,
                    "countryCode" to countryCode,
                ),
            ),
            mapOf("X-Tidal-Token" to EMBED_TOKEN),
        ),
    )

    /**
     * A cover id is five dash-separated segments spelling out its path on the CDN. Anything shaped
     * differently is a format we cannot address, so treat it as no cover rather than build a 404.
     */
    internal fun coverUrl(id: String): String? {
        val parts = id.split("-")
        if (parts.size != 5) return null
        return "https://resources.tidal.com/videos/${parts.joinToString("/")}/1280x1280.mp4"
    }
}

// ── Community ───────────────────────────────────────────────────────────────

/**
 * A community-curated `song + artist -> looping video` index, published as one JSON file.
 *
 * The catalogue services only have motion artwork where a label paid for some, which is a thin
 * slice of anything outside current chart releases. This is the only source that covers back
 * catalogue.
 */
internal object DesktopCommunityCanvas {

    private const val MANIFEST = "https://vivimusicanvas.mkmdevilmi.workers.dev/canvas.json"
    private const val TTL_MS = 30L * 60 * 1000

    private data class Entry(val song: String, val artist: String, val album: String, val url: String)

    @Volatile private var entries: List<Entry> = emptyList()
    @Volatile private var fetchedAtMs = 0L

    fun search(title: String, artist: String, album: String?): DesktopCanvasArtwork? {
        val index = manifest().ifEmpty { return null }
        val wantTitle = title.normalizeForCanvasMatch()
        val wantArtist = artist.normalizeForCanvasMatch()
        val wantAlbum = album?.normalizeForCanvasMatch()
        // Contributors write titles as they please, so this side matches on containment. The album
        // check is what keeps it honest when one is known.
        val hit = index.firstOrNull { entry ->
            val song = entry.song.normalizeForCanvasMatch()
            val credited = entry.artist.normalizeForCanvasMatch()
            val listed = entry.album.normalizeForCanvasMatch()
            song.isNotBlank() && (wantTitle.contains(song) || song.contains(wantTitle)) &&
                credited.isNotBlank() && (wantArtist.contains(credited) || credited.contains(wantArtist)) &&
                (listed.isBlank() || wantAlbum.isNullOrBlank() || listed == wantAlbum)
        } ?: return null
        return DesktopCanvasArtwork(hit.url, null, hit.song, hit.artist, hit.album.ifBlank { null })
    }

    /** The index is keyed by song, so this takes the first track of the album someone covered —
     * every clip on a release is usually the same loop anyway. */
    fun searchAlbum(album: String, artist: String): DesktopCanvasArtwork? {
        val index = manifest().ifEmpty { return null }
        val wantAlbum = album.normalizeForCanvasMatch()
        val wantArtist = artist.normalizeForCanvasMatch()
        if (wantAlbum.isBlank()) return null
        val hit = index.firstOrNull { entry ->
            val listed = entry.album.normalizeForCanvasMatch()
            val credited = entry.artist.normalizeForCanvasMatch()
            listed == wantAlbum && credited.isNotBlank() &&
                (wantArtist.contains(credited) || credited.contains(wantArtist))
        } ?: return null
        return DesktopCanvasArtwork(hit.url, null, hit.album, hit.artist, hit.album)
    }

    @Synchronized
    private fun manifest(): List<Entry> {
        val now = System.currentTimeMillis()
        if (entries.isNotEmpty() && now - fetchedAtMs < TTL_MS) return entries
        val body = canvasGet(MANIFEST)
        if (body == null) {
            // Serve what we already have rather than losing canvas entirely for half an hour.
            fetchedAtMs = now
            return entries
        }
        val parsed = runCatching {
            parse(body)?.get("items")?.jsonArray?.mapNotNull { item ->
                val value = item.jsonObject
                Entry(
                    song = value.text("song") ?: return@mapNotNull null,
                    artist = value.text("artist") ?: return@mapNotNull null,
                    album = value.text("album").orEmpty(),
                    url = value.text("url") ?: return@mapNotNull null,
                )
            }
        }.getOrNull().orEmpty()
        if (parsed.isNotEmpty()) entries = parsed
        fetchedAtMs = now
        return entries
    }
}
