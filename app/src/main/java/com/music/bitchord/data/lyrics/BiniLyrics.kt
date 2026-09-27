package com.music.bitchord.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Apple Music TTML again, from a third host — and the only one here that will
 * answer to a recording rather than to a name.
 *
 * The catalogue is the same one [BetterLyrics] serves; on a track both have,
 * the documents come back byte for byte identical. What it adds is a different
 * matcher over that catalogue, which is not nothing — it finds tracks
 * [BetterLyrics] misses, particularly outside the English-language releases —
 * and, more importantly, an index by ISRC.
 *
 * ### Why the ISRC matters
 *
 * Every other source here is asked for a title, an artist and a length, and
 * hopes. "Dracula" by Tame Impala exists as a single at 205 seconds and an
 * album cut at 206, with different words in places, and nothing in a fuzzy
 * match reliably tells those apart. An ISRC names one recording and only that
 * recording, so a lookup that has one cannot come back with the wrong edit.
 *
 * A search here also *reports* the ISRC of whatever it matched, which is where
 * [LyricsRepository] gets one to hand to the sources that can use it. That is
 * the whole arrangement: this asks by name once, and everything afterwards can
 * ask by recording.
 *
 * Two requests either way — the search returns a URL rather than the document,
 * so the TTML itself is a second fetch from the storage host.
 */
object BiniLyrics {

    private const val BASE = "https://lyrics-api.binimum.org/"

    /** Lyrics, and the ISRC of the recording they were matched to. */
    data class Match(val isrc: String?, val lines: List<LyricLine>)

    /**
     * Which recording this is, without fetching its words.
     *
     * The search is small — a couple of hundred bytes — and it is the only
     * request any source here makes whose answer is useful to the *other*
     * sources. [LyricsRepository] runs it on its own, before it asks anybody
     * for lyrics, so that everything downstream can name the recording rather
     * than describe it.
     */
    internal suspend fun identify(
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
        isrc: String? = null,
    ): Hit? = withContext(Dispatchers.IO) {
        val url = BASE.toHttpUrl().newBuilder()
            .apply {
                if (!isrc.isNullOrBlank()) {
                    // Nothing else is worth sending: the recording is named, and
                    // a title alongside it could only ever disagree with it.
                    addQueryParameter("isrc", isrc)
                } else {
                    addQueryParameter("track", title)
                    addQueryParameter("artist", artist)
                    if (!album.isNullOrBlank()) addQueryParameter("album", album)
                    val seconds = durationMs / 1000
                    if (seconds > 0) addQueryParameter("duration", seconds.toString())
                }
            }
            .build()

        // A miss is a 404 here rather than an empty result set, which
        // [lyricsGet] already turns into a null.
        val body = lyricsGet(url.toString()) ?: return@withContext null
        val response = runCatching { lyricsJson.decodeFromString<Response>(body) }.getOrNull()
            ?: return@withContext null
        response.results?.firstOrNull()
    }

    /** The document a search already found, fetched and parsed. */
    internal suspend fun lyricsFor(hit: Hit): Match? = withContext(Dispatchers.IO) {
        val document = hit.lyricsUrl?.takeIf { it.isNotBlank() } ?: return@withContext null
        val ttml = lyricsGet(document) ?: return@withContext null
        val lines = TtmlLyrics.parse(ttml).takeIf { it.isNotEmpty() } ?: return@withContext null
        Match(hit.isrc?.takeIf { it.isNotBlank() }, lines)
    }

    suspend fun lyrics(
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
        isrc: String? = null,
    ): Match? = identify(title, artist, durationMs, album, isrc)?.let { lyricsFor(it) }

    @Serializable
    internal data class Response(
        val total: Int? = null,
        /** How it matched — `HIT-EXACT` and friends. Logged, not acted on. */
        val source: String? = null,
        val results: List<Hit>? = null,
    )

    @Serializable
    internal data class Hit(
        @SerialName("track_name") val trackName: String? = null,
        @SerialName("artist_name") val artistName: String? = null,
        @SerialName("album_name") val albumName: String? = null,
        val duration: Int? = null,
        val isrc: String? = null,
        /** `word` or `line`; the document itself is the authority. */
        @SerialName("timing_type") val timingType: String? = null,
        val lyricsUrl: String? = null,
    )
}
