package com.music.bitchord.data.canvas

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.Http
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Result representing official studio artwork and optional motion canvas for a track.
 */
data class AppleArtwork(
    val artworkUrl: String,
    val motionCanvas: CanvasArtwork? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val source: String = "Apple",
)

/**
 * Resolves official studio album artwork and motion covers from Apple Music,
 * falling back to Spotify static studio artwork and active addons.
 *
 * Caches results in an LRU memory cache so re-visits on TV or queue lookups
 * resolve synchronously without re-querying the network.
 */
object AppleArtworkRepository {

    private const val TAG = "AppleArtworkRepository"
    private const val CACHE_SIZE = 256

    private class Entry(val artwork: AppleArtwork?)

    private val cache = object : LinkedHashMap<String, Entry>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) =
            size > CACHE_SIZE
    }

    private val lock = Mutex()

    /**
     * Synchronously returns cached studio artwork if already resolved, or null.
     */
    fun cached(song: Song?): AppleArtwork? {
        if (song == null) return null
        val key = cacheKey(song)
        return synchronized(cache) { cache[key]?.artwork }
    }

    /**
     * Resolves Studio Artwork (high-res official album cover and motion video if available).
     * Guaranteed never to throw.
     */
    suspend fun artworkFor(song: Song?): AppleArtwork? {
        if (song == null) return null
        if (song.videoId.isBlank() && song.title.isBlank()) return null

        val rawTitle = song.title.cleanedTrackTitle()
        val rawArtist = song.artist.cleanedTrackTitle()
        if (rawTitle.isBlank() && rawArtist.isBlank()) return null

        val key = cacheKey(song)
        synchronized(cache) {
            val entry = cache[key]
            if (entry != null) return entry.artwork
        }

        return lock.withLock {
            synchronized(cache) {
                val entry = cache[key]
                if (entry != null) return entry.artwork
            }

            val resolved = withContext(Dispatchers.IO) {
                runCatching { resolve(rawTitle, rawArtist, song.albumName) }
                    .onFailure { Log.d(TAG, "lookup failed for $rawTitle by $rawArtist: $it") }
                    .getOrNull()
            }

            synchronized(cache) {
                cache[key] = Entry(resolved)
            }
            resolved
        }
    }

    private fun cacheKey(song: Song): String =
        if (song.videoId.isNotBlank()) "song|${song.videoId}" else "song|${song.title}|${song.artist}"

    private suspend fun resolve(title: String, artist: String, album: String?): AppleArtwork? {
        val primary = primaryArtist(artist).ifBlank { artist }

        // 1. Priority 1 for Motion: Apple Music motion artwork
        val appleMotion = runCatching {
            AppleMusicCanvas.search(title, artist, album)
                ?: if (!primary.equals(artist, ignoreCase = true)) AppleMusicCanvas.search(title, primary, album) else null
        }.getOrNull()

        // 2. Priority 2 for Motion: Spotify Canvas looping video (if Apple motion not available)
        val bestMotion = appleMotion ?: runCatching {
            SpotifyCanvas.search(title, artist, album)
                ?: if (!primary.equals(artist, ignoreCase = true)) SpotifyCanvas.search(title, primary, album) else null
        }.getOrNull()

        // 3. Fetch official Apple Music studio album art from iTunes Search API with progressive queries
        val termsToTry = linkedSetOf<String>()
        val fullTerm = buildString {
            if (!title.contains(artist, ignoreCase = true)) append(artist).append(' ')
            append(title)
            if (!album.isNullOrBlank() && !title.contains(album, ignoreCase = true)) {
                append(' ').append(album)
            }
        }.trim()
        if (fullTerm.isNotBlank()) termsToTry.add(fullTerm)

        val artistTitleTerm = buildString {
            if (!title.contains(artist, ignoreCase = true)) append(artist).append(' ')
            append(title)
        }.trim()
        if (artistTitleTerm.isNotBlank()) termsToTry.add(artistTitleTerm)

        // Try censored profanity variant in search queries (e.g. "Lauv f*ck, i'm lonely")
        // because iTunes explicit indexing stores explicit tracks under censored spelling
        val censoredFull = fullTerm.censoredProfanityVariant()
        if (censoredFull != null && censoredFull.isNotBlank()) termsToTry.add(censoredFull)

        val censoredArtistTitle = artistTitleTerm.censoredProfanityVariant()
        if (censoredArtistTitle != null && censoredArtistTitle.isNotBlank()) termsToTry.add(censoredArtistTitle)

        // If artist contains multiple collaborators or producer tags (e.g. "DILJIT DOSANJH & thiarajxtt"),
        // try the primary artist as well so iTunes finds the official studio release
        if (!primary.equals(artist, ignoreCase = true)) {
            val primaryFull = buildString {
                if (!title.contains(primary, ignoreCase = true)) append(primary).append(' ')
                append(title)
                if (!album.isNullOrBlank() && !title.contains(album, ignoreCase = true)) {
                    append(' ').append(album)
                }
            }.trim()
            if (primaryFull.isNotBlank()) termsToTry.add(primaryFull)

            val primaryTitle = buildString {
                if (!title.contains(primary, ignoreCase = true)) append(primary).append(' ')
                append(title)
            }.trim()
            if (primaryTitle.isNotBlank()) termsToTry.add(primaryTitle)

            val primaryCensored = primaryTitle.censoredProfanityVariant()
            if (primaryCensored != null && primaryCensored.isNotBlank()) termsToTry.add(primaryCensored)
        }

        // Try title alone as fallback for unique track names
        if (title.isNotBlank()) {
            termsToTry.add(title.trim())
            title.censoredProfanityVariant()?.let { if (it.isNotBlank()) termsToTry.add(it) }
        }

        for (term in termsToTry) {
            val itunesHit = queryItunes(term, title, artist, album, bestMotion)
            if (itunesHit != null) {
                return itunesHit
            }
        }

        // 4. If motion exists even if iTunes search had no static artwork hit:
        if (bestMotion != null) {
            return AppleArtwork(
                artworkUrl = "",
                motionCanvas = bestMotion,
                title = bestMotion.title,
                artist = bestMotion.artist,
                album = bestMotion.album,
                source = if (bestMotion.source == CanvasSource.SPOTIFY) "Spotify" else "Apple",
            )
        }

        return null
    }

    private fun queryItunes(term: String, title: String, artist: String, album: String?, motion: CanvasArtwork?): AppleArtwork? {
        val url = "https://itunes.apple.com/search?term=${URLEncoder.encode(term, "UTF-8")}&entity=song&limit=5"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()

        val body = runCatching {
            Http.client.newCall(request).execute().use { it.body?.string() }
        }.getOrNull() ?: return null

        val root = runCatching { JSONObject(body) }.getOrNull()
        val results = root?.optJSONArray("results") ?: return null

        val wantedTitle = title.foldProfanity().normalizeForMatch()
        val wantedArtists = splitArtists(artist)
        val wantedAlbum = album?.normalizeForMatch()

        for (i in 0 until results.length()) {
            val r = results.optJSONObject(i) ?: continue
            val rawTrackName = r.optString("trackName")
            val trackName = rawTrackName.foldProfanity().normalizeForMatch()
            val collectionName = r.optString("collectionName").normalizeForMatch()

            // Match validation
            val cTrackName = rawTrackName.cleanedTrackTitle().foldProfanity().normalizeForMatch()
            val titleOk = trackName.contains(wantedTitle) || wantedTitle.contains(trackName) ||
                trackName.replace(" ", "") == wantedTitle.replace(" ", "") ||
                cTrackName == wantedTitle ||
                (wantedTitle.isNotBlank() && cTrackName.contains(wantedTitle))
            val artistOk = wantedArtists.isEmpty() || wantedArtists.any { want ->
                splitArtists(r.optString("artistName")).any { credited ->
                    credited.contains(want) || want.contains(credited)
                }
            }
            val albumOk = wantedAlbum.isNullOrBlank() || collectionName.isBlank() ||
                collectionName.contains(wantedAlbum) || wantedAlbum.contains(collectionName)

            if (titleOk && artistOk) {
                val raw100 = r.optString("artworkUrl100")
                if (raw100.isNotBlank()) {
                    val highRes = raw100
                        .replace("100x100bb", "1000x1000bb")
                        .replace("60x60bb", "1000x1000bb")
                    Log.d(TAG, "resolved Apple artwork for '$title' -> $highRes (motion: ${motion != null})")
                    return AppleArtwork(
                        artworkUrl = highRes,
                        motionCanvas = motion,
                        title = r.optString("trackName"),
                        artist = r.optString("artistName"),
                        album = r.optString("collectionName"),
                        source = "Apple",
                    )
                }
            }
        }
        return null
    }
}

private val PROFANITY_MAP = listOf(
    Regex("(?i)\\bfuck\\b") to "f*ck",
    Regex("(?i)\\bshit\\b") to "sh*t",
    Regex("(?i)\\bbitch\\b") to "b*tch",
)

private fun String.censoredProfanityVariant(): String? {
    var changed = false
    var res = this
    for ((regex, rep) in PROFANITY_MAP) {
        if (regex.containsMatchIn(res)) {
            res = res.replace(regex, rep)
            changed = true
        }
    }
    return if (changed) res else null
}

private val PROFANITY_FOLD = Regex("""\bf\s*[*k]+\s*ck\b|\bf\s*[*]+\s*k\b""", RegexOption.IGNORE_CASE)
private val SHIT_FOLD = Regex("""\bsh\s*[*]+\s*t\b""", RegexOption.IGNORE_CASE)
private val BITCH_FOLD = Regex("""\bb\s*[*]+\s*tch\b""", RegexOption.IGNORE_CASE)

fun String.foldProfanity(): String = this
    .replace(PROFANITY_FOLD, "fuck")
    .replace(SHIT_FOLD, "shit")
    .replace(BITCH_FOLD, "bitch")

private val TRACK_NOISE = Regex(
    """\s*[\(\[]\s*(?:from|feat\.?|ft\.?|featuring|with|stripped|acoustic|remix|live|radio edit|official|video|audio|version|deluxe|bonus|edit|prod\.?|produced by|lyrical|visualizer|mv|hd|4k|hq|full song)[^\)\]]*[\)\]]|""" +
        """\s*\b(?:official\s+(?:music\s+)?(?:video|audio)|lyrical(?:\s+video)?|full\s+song|4k\s+video|hd\s+video|music\s+video)\b""",
    RegexOption.IGNORE_CASE,
)

fun String.cleanedTrackTitle(): String = this
    .replace('’', '\'')
    .replace('‘', '\'')
    .replace(TRACK_NOISE, " ")
    .substringBefore(" | ")
    .replace(Regex("\\[[^\\]]*\\]"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()
    .ifBlank { this }

