package com.music.bitchord.data.canvas

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.Http
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.Base64
import java.util.Locale

/**
 * Apple Music's motion artwork — the animated sleeves the web player shows on
 * an album page, exposed on the catalog API as `editorialVideo`.
 *
 * The richest of the three sources and the fussiest to reach. Two problems to
 * solve: the endpoint needs a bearer token, and a free-text search will return
 * a plausible-looking wrong album for almost any query.
 *
 * The token is the same read-only one the web player mints for anonymous
 * visitors, so it is scraped rather than owned — see [token]. The search is
 * scored rather than trusted: results are ranked on how well the artist, track
 * and album line up, compilations and radio-style playlists are dropped
 * outright, and anything that doesn't clear the bar is skipped even if it is
 * the only hit. A wrong canvas is worse than none.
 *
 * The clips are HLS, which is why the app carries the Media3 HLS extension.
 */
object AppleMusicCanvas {

    private const val TAG = "AppleMusicCanvas"
    private const val AMP = "https://amp-api.music.apple.com/v1/catalog"
    private const val WEB_PLAYER = "https://music.apple.com/us/browse"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Motion artwork is per storefront; use the device's if it looks sane. */
    val storefront: String by lazy {
        Locale.getDefault().country.takeIf { it.length == 2 }?.lowercase(Locale.ROOT) ?: "us"
    }

    fun search(title: String, artist: String, album: String?): CanvasArtwork? {
        val bearer = token() ?: return null

        // The catalog search is term-based, so fold everything we know into
        // the term — an artist name alone pulls in their whole discography and
        // the scoring below then has to reject all of it.
        val term = buildString {
            if (!title.contains(artist, ignoreCase = true)) append(artist).append(' ')
            append(title)
            if (!album.isNullOrBlank() && !title.contains(album, ignoreCase = true)) {
                append(' ').append(album)
            }
        }

        val url = "$AMP/$storefront/search".toHttpUrl().newBuilder()
            .addQueryParameter("term", term)
            .addQueryParameter("types", "songs")
            .addQueryParameter("limit", "10")
            .addQueryParameter("extend", "editorialVideo")
            .addQueryParameter("include", "albums")
            .build()
            .toString()

        val body = get(url, bearer) ?: return null
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val hits = root["results"]?.jsonObject
            ?.get("songs")?.jsonObject
            ?.get("data")?.jsonArray
            ?: return null

        val ranked = hits.mapNotNull { hit ->
            val song = hit as? JsonObject ?: return@mapNotNull null
            val score = score(song, title, artist, album) ?: return@mapNotNull null
            score to song
        }.sortedByDescending { it.first }

        for ((score, song) in ranked) {
            if (score < MIN_SCORE) {
                Log.d(TAG, "no hit scored above $MIN_SCORE for '$title' (best was $score)")
                break
            }
            val attributes = song["attributes"]?.jsonObject ?: continue
            val songName = attributes["name"]?.jsonPrimitive?.contentOrNull
            val songArtist = attributes["artistName"]?.jsonPrimitive?.contentOrNull
            val albumName = attributes["albumName"]?.jsonPrimitive?.contentOrNull

            // Some searches already carry the motion artwork inline, which
            // saves the album round trip entirely.
            attributes["editorialVideo"]?.jsonObject?.let { video ->
                motionUrls(video)?.let { (primary, alternate) ->
                    Log.d(TAG, "inline motion artwork for '$songName'")
                    return CanvasArtwork(primary, alternate, songName, songArtist, albumName)
                }
            }

            val albumId = albumId(song) ?: continue
            fetchAlbum(albumId, bearer, songName, songArtist)?.let { return it }
        }
        return null
    }

    /**
     * Motion artwork for a release rather than a track, for the album page.
     *
     * Simpler than the song path: albums carry `editorialVideo` inline on the
     * search result, so there is no second lookup to resolve an id first.
     */
    fun searchAlbum(album: String, artist: String): CanvasArtwork? {
        val bearer = token() ?: return null
        val term = if (album.contains(artist, ignoreCase = true)) album else "$artist $album"

        val url = "$AMP/$storefront/search".toHttpUrl().newBuilder()
            .addQueryParameter("term", term)
            .addQueryParameter("types", "albums")
            .addQueryParameter("limit", "10")
            .addQueryParameter("extend", "editorialVideo")
            .build()
            .toString()

        val body = get(url, bearer) ?: return null
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val hits = root["results"]?.jsonObject
            ?.get("albums")?.jsonObject
            ?.get("data")?.jsonArray
            ?: return null

        val ranked = hits.mapNotNull { hit ->
            val record = hit as? JsonObject ?: return@mapNotNull null
            // An album is its own "album" as far as the scoring goes, which is
            // what keeps a deluxe edition from outranking the plain one.
            val score = score(record, album, artist, album, albumIsSelf = true)
                ?: return@mapNotNull null
            score to record
        }.sortedByDescending { it.first }

        for ((score, record) in ranked) {
            if (score < MIN_SCORE) break
            val attributes = record["attributes"]?.jsonObject ?: continue
            val name = attributes["name"]?.jsonPrimitive?.contentOrNull
            if (name != null && isCompilation(name)) continue
            val video = attributes["editorialVideo"]?.jsonObject ?: continue
            val (primary, alternate) = motionUrls(video) ?: continue

            Log.d(TAG, "motion artwork for album '$name'")
            return CanvasArtwork(
                url = primary,
                fallbackUrl = alternate,
                title = name,
                artist = attributes["artistName"]?.jsonPrimitive?.contentOrNull,
                album = name,
            )
        }
        return null
    }

    // ---- Search scoring ------------------------------------------------

    /**
     * The floor a hit has to clear. An exact artist and an exact title alone
     * reach 25, so this only ever admits a result that matched on both, or one
     * that matched the artist plus a fuzzy title and the right album.
     */
    private const val MIN_SCORE = 12

    /**
     * How well a search hit lines up with what's playing, or null to reject it
     * outright. Artist is a gate rather than a score: a clip credited to
     * someone else is never the right one, however well the title reads.
     */
    private fun score(
        song: JsonObject,
        title: String,
        artist: String,
        album: String?,
        // An album result has no `albumName` of its own — it *is* the album.
        albumIsSelf: Boolean = false,
    ): Int? {
        val attributes = song["attributes"]?.jsonObject ?: return null
        val hitName = attributes["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val hitArtist = attributes["artistName"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val hitAlbum = if (albumIsSelf) {
            hitName
        } else {
            attributes["albumName"]?.jsonPrimitive?.contentOrNull.orEmpty()
        }

        if (isCompilation(hitName) || isCompilation(hitAlbum)) return null

        val wanted = splitArtists(artist)
        val credited = splitArtists(hitArtist)
        if (wanted.isEmpty() || credited.isEmpty()) return null
        if (!wanted.all { want -> credited.any { it == want } }) return null

        var score = 10

        val wantTitle = title.normalizeForMatch()
        val hitTitle = hitName.normalizeForMatch()
        score += when {
            hitTitle == wantTitle -> 15
            hitTitle.contains(wantTitle) || wantTitle.contains(hitTitle) -> 7
            // Same artist, different song. Apple returns these freely and
            // they are exactly the mismatch that has to be kept out.
            else -> -10
        }

        if (!album.isNullOrBlank() && hitAlbum.isNotBlank()) {
            val wantAlbum = album.normalizeForMatch()
            val gotAlbum = hitAlbum.normalizeForMatch()
            score += when {
                gotAlbum == wantAlbum -> 20
                gotAlbum.contains(wantAlbum) || wantAlbum.contains(gotAlbum) -> 10
                else -> 0
            }
        }

        // A "(Deluxe)" or "(Remastered)" on one side only is a different
        // master of the same track, and often a different clip.
        for (word in EDITION_WORDS) {
            val inWanted = title.contains(word, ignoreCase = true)
            val inHit = hitName.contains(word, ignoreCase = true)
            if (inWanted && inHit) score += 5 else if (inHit) score -= 3
        }

        return score
    }

    private val EDITION_WORDS =
        listOf("deluxe", "expanded", "remastered", "remix", "version", "edit", "mix", "bonus")

    /**
     * Editorial playlists and radio mixes have motion artwork of their own,
     * and Apple returns them alongside albums. Theirs belongs to the playlist,
     * not to the track, so putting one behind a sleeve is always wrong.
     */
    private fun isCompilation(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return COMPILATION_MARKERS.any { lower.contains(it) }
    }

    private val COMPILATION_MARKERS = listOf(
        "playlist", "set list", "essentials", "dj mix", "mixed",
        "apple music", "today's hits", "session",
    )

    // ---- Album lookup --------------------------------------------------

    private fun albumId(song: JsonObject): String? {
        val fromRelationship = song["relationships"]?.jsonObject
            ?.get("albums")?.jsonObject
            ?.get("data")?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
        if (fromRelationship != null) return fromRelationship.takeUnless { it.startsWith("pl.") }

        // Not every hit expands its relationships, but the web URL always ends
        // in the album id: .../album/<slug>/<id>?i=<song id>
        val url = song["attributes"]?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
            ?: return null
        return url.substringAfter("/album/", "")
            .substringBefore("?")
            .substringAfterLast("/")
            .takeIf { it.isNotBlank() && it.all(Char::isDigit) }
    }

    private fun fetchAlbum(
        albumId: String,
        bearer: String,
        songTitle: String?,
        songArtist: String?,
    ): CanvasArtwork? {
        val url = "$AMP/$storefront/albums/$albumId".toHttpUrl().newBuilder()
            .addQueryParameter("extend", "editorialVideo")
            .build()
            .toString()

        val body = get(url, bearer) ?: return null
        val album = runCatching {
            json.parseToJsonElement(body).jsonObject["data"]?.jsonArray?.firstOrNull()?.jsonObject
        }.getOrNull() ?: return null

        val attributes = album["attributes"]?.jsonObject ?: return null
        val albumName = attributes["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (isCompilation(albumName)) return null

        val video = attributes["editorialVideo"]?.jsonObject ?: return null
        val (primary, alternate) = motionUrls(video) ?: return null

        Log.d(TAG, "motion artwork on album '$albumName' ($albumId)")
        return CanvasArtwork(
            url = primary,
            fallbackUrl = alternate,
            title = songTitle,
            artist = songArtist ?: attributes["artistName"]?.jsonPrimitive?.contentOrNull,
            album = albumName,
        )
    }

    /**
     * The square rendition first — it fills a square sleeve without cropping.
     * The tall one is the same clip framed for a phone-shaped surface and is
     * only worth having as the retry when the square won't play.
     */
    private fun motionUrls(video: JsonObject): Pair<String, String?>? {
        fun link(key: String): String? = video[key]?.jsonObject?.let { asset ->
            asset["video"]?.jsonPrimitive?.contentOrNull
                ?: asset["videoUrl"]?.jsonPrimitive?.contentOrNull
                ?: asset["hlsUrl"]?.jsonPrimitive?.contentOrNull
                ?: asset["url"]?.jsonPrimitive?.contentOrNull
        }?.takeIf { it.isNotBlank() }

        val square = link("motionDetailSquare") ?: link("motionSquareVideo1x1")
        val raw = link("motionDetailRaw")
        val tall = link("motionDetailTall") ?: link("motionTallVideo3x4")
        val primary = square ?: raw ?: tall ?: return null
        val alternate = listOfNotNull(square, raw, tall).firstOrNull { it != primary }
        return primary to alternate
    }

    // ---- Token ---------------------------------------------------------

    private var cachedToken: String? = null
    private var tokenExpiresAtMs = 0L
    private var retryTokenAfterMs = 0L

    /** Tokens the catalog API has already turned down. See [get]. */
    private val rejected = mutableSetOf<String>()

    /**
     * The anonymous bearer token the Apple Music web player uses for catalog
     * reads. It isn't published anywhere and rotates every few weeks, so it is
     * read the way the browser gets it: load the web player, find the JS
     * bundle it pulls in, and pick the token out of it. Held until shortly
     * before it expires.
     *
     * The bundle ships several unrelated JWTs and only the web player's own is
     * accepted here — the others come back 401 — so this picks by issuer
     * rather than taking the first one that parses.
     *
     * A failed scrape backs off for [TOKEN_RETRY_MS] rather than retrying per
     * track: if Apple has changed the page shape, hammering it on every skip
     * fixes nothing and makes every canvas lookup pay for the round trip.
     */
    @Synchronized
    private fun token(): String? {
        val now = System.currentTimeMillis()
        cachedToken?.let { if (now < tokenExpiresAtMs - 60_000) return it }
        if (now < retryTokenAfterMs) return null

        val html = canvasGet(WEB_PLAYER, mapOf("User-Agent" to CANVAS_UA))
        val scripts = html?.let {
            Regex("""/assets/index(?:-legacy)?[~-][A-Za-z0-9_-]+\.js""")
                .findAll(it).map(MatchResult::value).distinct().toList()
        }.orEmpty()

        for (path in scripts) {
            val script = canvasGet("https://music.apple.com$path", mapOf("User-Agent" to CANVAS_UA))
                ?: continue
            val candidates = Regex("""ey[A-Za-z0-9_-]+\.ey[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
                .findAll(script)
                .map(MatchResult::value)
                .distinct()
                .filter { it !in rejected }
                .mapNotNull { jwt -> expiry(jwt)?.let { jwt to it } }
                .filter { it.second > now }
                .toList()
            if (candidates.isEmpty()) continue

            // The web player's own token first; anything else is a guess kept
            // only so a change in how Apple labels it isn't fatal.
            val (jwt, expiresAt) = candidates.firstOrNull { isWebPlayerToken(it.first) }
                ?: candidates.first()
            cachedToken = jwt
            tokenExpiresAtMs = expiresAt
            Log.d(TAG, "web player token good until ${java.util.Date(expiresAt)}")
            return jwt
        }

        Log.w(TAG, "no usable web player token in ${scripts.size} bundle(s); backing off")
        retryTokenAfterMs = now + TOKEN_RETRY_MS
        return null
    }

    private const val TOKEN_RETRY_MS = 30L * 60 * 1000

    /**
     * An authenticated catalog read. A 401 means the token we picked out of
     * the bundle isn't the one this endpoint honours, so it is struck off and
     * the next lookup re-scrapes and picks a different one — the alternative
     * is being locked out until the app restarts.
     */
    private fun get(url: String, bearer: String): String? {
        val request = Request.Builder().url(url).apply {
            authHeaders(bearer).forEach { (name, value) -> header(name, value) }
        }.build()
        return runCatching {
            Http.client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> response.body?.string()
                    response.code == 401 -> {
                        Log.w(TAG, "token rejected by the catalog API; will re-scrape")
                        synchronized(this) {
                            rejected += bearer
                            if (cachedToken == bearer) {
                                cachedToken = null
                                tokenExpiresAtMs = 0L
                            }
                        }
                        null
                    }
                    else -> null
                }
            }
        }.getOrNull()
    }

    /** The web player's token names itself in the header `kid` and payload `iss`. */
    private fun isWebPlayerToken(jwt: String): Boolean = runCatching {
        val parts = jwt.split(".")
        val header = String(Base64.getUrlDecoder().decode(parts[0]), Charsets.UTF_8)
        val payload = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
        header.contains("WebPlayKid") || payload.contains("AMPWebPlay")
    }.getOrDefault(false)

    /** A JWT's `exp` in millis, or null if this isn't one we can read. */
    private fun expiry(jwt: String): Long? = runCatching {
        val payload = String(
            Base64.getUrlDecoder().decode(jwt.split(".")[1]),
            Charsets.UTF_8,
        )
        val seconds = Regex("\"exp\"\\s*:\\s*(\\d+)").find(payload)?.groupValues?.get(1)
        seconds?.toLong()?.times(1000)
    }.getOrDefault(null)

    private fun authHeaders(bearer: String) = mapOf(
        "Authorization" to "Bearer $bearer",
        "Origin" to "https://music.apple.com",
        "Referer" to "https://music.apple.com/",
        "User-Agent" to CANVAS_UA,
    )
}
