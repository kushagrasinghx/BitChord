package com.music.bitchord.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * lrc.red — Apple Music TTML, per-syllable, filed by ISRC.
 *
 * Every document lives at `https://lrc.red/s/{ISRC}.ttml`, so a track whose
 * recording is already known costs one request and cannot come back as the
 * wrong edit. That is the usual case: [BiniLyrics] answers its searches out
 * of this same catalogue — its `lyricsUrl` points here — and
 * [LyricsRepository] hands the ISRC it finds to every source that can use one.
 *
 * Without one, lrc.red's own search (`/search.json?q=`) is asked with the
 * title and artist, and the hits are matched here rather than trusted in
 * order: the search is a free-text one and ranks "Bohemian Rhapsody
 * (Operatic Section / 2011 A Cappella Mix)" above the song itself. See [best].
 *
 * Line-synced entries are TTML too (`lrc:timing="Line"`), which [TtmlLyrics]
 * already reads, so one parser covers both. The document's own
 * `<audio lyricOffset>` is ignored, as it is for every Apple TTML here — the
 * stamps are written against the release audio, which is what is playing.
 */
object LrcRed {

    private const val BASE = "https://lrc.red/"

    /**
     * How far a hit's length may be from the playing track's. YouTube reports
     * whole seconds, and lrc.red lists the 2011 remaster of "Bohemian Rhapsody"
     * at 356.5 s against 352.0 s for the live cut — close enough that the
     * version words in [best] have to do most of the work, not this.
     */
    private const val DURATION_TOLERANCE_SECONDS = 3.0

    /** `CC-XXX-YY-NNNNN` without the dashes — and nothing else goes into a path. */
    private val ISRC = Regex("""[A-Z]{2}[A-Z0-9]{3}\d{7}""")

    fun documentUrl(isrc: String): String? =
        isrc.trim().uppercase(Locale.ROOT).takeIf { ISRC.matches(it) }?.let { "${BASE}s/$it.ttml" }

    /**
     * [get] is how the document is fetched; [LyricsRepository] passes one that
     * shares the download with [BiniLyrics], which wants the very same file.
     */
    suspend fun lyrics(
        title: String,
        artist: String,
        durationMs: Long,
        isrc: String? = null,
        get: suspend (String) -> String? = { lyricsGet(it) },
    ): List<LyricLine>? = withContext(Dispatchers.IO) {
        val known = isrc?.let(::documentUrl)
        if (known != null) document(known, get)?.let { return@withContext it }

        // A known recording lrc.red doesn't have — a local file's tag naming a
        // release it never indexed — still gets a search: the same song is
        // often catalogued under a sibling release's code.
        val hit = search(title, artist, durationMs) ?: return@withContext null
        val found = hit.isrc?.let(::documentUrl)?.takeIf { it != known } ?: return@withContext null
        document(found, get)
    }

    private suspend fun document(url: String, get: suspend (String) -> String?): List<LyricLine>? =
        get(url)?.let(TtmlLyrics::parse)?.takeIf { it.isNotEmpty() }

    private fun search(title: String, artist: String, durationMs: Long): Hit? {
        val url = "${BASE}search.json".toHttpUrl().newBuilder()
            .addQueryParameter("q", "$title $artist".trim())
            .build()
        val body = lyricsGet(url.toString()) ?: return null
        val hits = runCatching { lyricsJson.decodeFromString<Response>(body) }.getOrNull()?.hits
            ?: return null
        return best(hits, title, artist, durationMs)
    }

    /**
     * The hit that is this recording, or null if none of them is sure to be.
     *
     * A miss here is recoverable — the sources behind this one get their turn —
     * and the wrong words scrolling in time with the right song is not, so
     * every test is a requirement rather than a score:
     *
     *  - the title, outside its brackets and any " - " suffix, is the same;
     *  - the words in those brackets that name a different recording
     *    ([VERSION_WORDS]: live, remix, acoustic…) are the same on both
     *    sides — "Remastered" and "From 'Aashiqui 2'" don't count;
     *  - at least one credited artist is shared;
     *  - the length is within [DURATION_TOLERANCE_SECONDS], when both are known.
     *
     * Of the hits that pass, the closest in length wins, and the search's own
     * rank breaks a tie.
     */
    internal fun best(hits: List<Hit>, title: String, artist: String, durationMs: Long): Hit? {
        val wantedTitle = coreOf(title)
        val wantedVersion = versionOf(title)
        val wantedArtists = artistsOf(artist)
        val seconds = durationMs / 1000.0

        fun distance(hit: Hit): Double =
            if (durationMs > 0 && hit.duration != null) abs(hit.duration - seconds) else 0.0

        return hits
            .filter { hit ->
                val name = hit.title ?: return@filter false
                hit.isrc?.let(::documentUrl) != null &&
                    coreOf(name) == wantedTitle &&
                    versionOf(name) == wantedVersion &&
                    (wantedArtists.isEmpty() || artistsOf(hit.artist.orEmpty()).any { it in wantedArtists }) &&
                    distance(hit) <= DURATION_TOLERANCE_SECONDS
            }
            .minByOrNull(::distance)
    }

    /** "Song (Live) - 2011 Remaster" → "song". */
    private fun coreOf(title: String): String =
        normalized(title.replace(BRACKETED, " ").substringBefore(" - "))
            .ifEmpty { normalized(title) }

    /** The version words in the parts [coreOf] throws away. */
    private fun versionOf(title: String): Set<String> {
        val extras = BRACKETED.findAll(title).joinToString(" ") { it.value } +
            " " + title.substringAfter(" - ", "")
        return normalized(extras).split(' ').filter { it in VERSION_WORDS }.toSet()
    }

    private fun artistsOf(artist: String): Set<String> =
        artist.split(ARTIST_SEPARATORS).map(::normalized).filter { it.isNotEmpty() }.toSet()

    /** Lower case, accents off ("ROSÉ" is "rose"), and only letters and digits. */
    private fun normalized(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .replace(WHITESPACE, " ")
            .trim()

    private val BRACKETED = Regex("""[(\[][^)\]]*[)\]]""")
    private val COMBINING_MARKS = Regex("""\p{Mn}+""")
    private val WHITESPACE = Regex("""\s+""")
    private val ARTIST_SEPARATORS = Regex(
        """\s*(?:,|&|;|/|\s+and\s+|\s+x\s+|\s+with\s+|\s+feat\.?\s+|\s+ft\.?\s+)\s*""",
        RegexOption.IGNORE_CASE,
    )

    /** Bracket words that make it a different recording, not a different label. */
    private val VERSION_WORDS = setOf(
        "live", "remix", "remixed", "mix", "acoustic", "unplugged", "instrumental",
        "karaoke", "cappella", "acapella", "demo", "edit", "version", "cover",
        "sped", "slowed", "reverb", "nightcore", "lofi", "orchestral", "extended",
    )

    @Serializable
    data class Response(
        val hits: List<Hit>? = null,
        /** Paging cursor. The first page is all a match is looked for in. */
        val next: String? = null,
    )

    @Serializable
    data class Hit(
        val isrc: String? = null,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val year: Int? = null,
        /** Seconds, fractional. */
        val duration: Double? = null,
    )
}
