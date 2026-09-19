package com.music.bitchord.data.lyrics

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections

/**
 * Where the player gets its lyrics.
 *
 * Sixteen sources, tried in [order] — the user's own priority list in Settings,
 * defaulting to [LyricsSource.entries]:
 *
 *  - [BetterLyrics], [PaxSenix] and [BiniLyrics] — Apple Music TTML,
 *    per-syllable, from three independent hosts so one having a bad day
 *    doesn't cost the timing.
 *  - [LyricsPlus] — the YouLy+ backend; finest timing of the lot, flakiest hosting.
 *  - [SimpMusicLyrics] — keyed on the video id, so it can't fetch the wrong edit.
 *  - [Unison] — contributed rather than licensed; thin, and occasionally the
 *    only place a track exists.
 *  - [LrcLib], [Musixmatch], [KuGou] — line-synced only, but between them
 *    almost always up, and [KuGou] carries a lot that the others don't.
 *
 * Every enabled source is asked *at the same time*, but their answers are
 * taken in [order]: the loop awaits them one at a time in that sequence, so a
 * lower-priority source finishing first never preempts one still pending
 * ahead of it. Asked one after another instead, a miss on each source would
 * cost its own round trip before the next was even tried, and a track with no
 * lyrics anywhere would spend the best part of a minute finding that out with
 * eight of them. Run together, a miss costs whatever the slowest one needed
 * to still be waited on took.
 *
 * A word-timed answer wins outright. Failing that, a line-timed one is taken
 * from the highest-priority source that had it — better a whole line lighting
 * up in sync than the right animation on lyrics that don't exist.
 *
 * ### Matching on the recording
 *
 * Everything above matches a *name*, and a name is ambiguous in a way that
 * matters: a single and its album cut share a title, an artist and very nearly
 * a length, and routinely differ in the words. An ISRC names one recording and
 * settles it. Two sources accept one — [BiniLyrics] and [LyricsPlus] — and
 * [BiniLyrics] is also the one that hands them out, reporting the ISRC of
 * whatever its own search matched.
 *
 * So the recording is settled *before* anybody is asked for words: one small
 * search against [BiniLyrics], whose answer is a name every other source can
 * use. It costs a round trip at the head of the lookup, which is why it is
 * capped at [IDENTIFY_TIMEOUT_MS] and why its result is kept against the video
 * id — a track asked about twice pays for this once. A downloaded or local file
 * can skip it entirely: its own tags name the recording, which is what the
 * [isrc] parameter is for.
 *
 * Only run when the user has [LyricsSource.BINI_LYRICS] enabled. It is a
 * request to a third party like any other, and a source somebody has turned off
 * is a source this app does not contact — not even for something it would only
 * use to help the sources they left on.
 */
object LyricsRepository {

    /** Parses a persisted sidecar back into the same result the player uses. */
    fun offline(
        content: String,
        format: LyricsArtifactFormat,
        source: LyricsSource = LyricsSource.LRCLIB,
    ): Result? {
        val lines = when (format) {
            LyricsArtifactFormat.TTML -> TtmlLyrics.parse(content)
            LyricsArtifactFormat.ENHANCED_LRC -> EnhancedLrc.parse(content)
            LyricsArtifactFormat.LRC -> LrcLib.parseLrc(content)
        }.takeIf { it.isNotEmpty() } ?: return null
        return result(
            source = source,
            lines = lines,
            artifact = LyricsArtifact(source, format, content, lines),
        )
    }

    /** Lyrics, their source, and the representation that can be persisted. */
    data class Result(
        val source: LyricsSource,
        val lines: List<LyricLine>,
        val artifact: LyricsArtifact? = null,
    )

    /** Direct artifact lookup for download and caching flows. */
    suspend fun artifact(
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
        sources: Set<LyricsSource> = LyricsSource.entries.toSet(),
        order: List<LyricsSource> = LyricsSource.entries,
        prioritizeSyllableSync: Boolean = false,
        isrc: String? = null,
    ): LyricsArtifact? = lyrics(
        videoId = videoId,
        title = title,
        artist = artist,
        durationMs = durationMs,
        album = album,
        sources = sources,
        order = order,
        prioritizeSyllableSync = prioritizeSyllableSync,
        isrc = isrc,
    )?.artifact

    /**
     * [sources] is the user's pick from Settings; anything not in it is not
     * contacted at all. An empty set means no lyrics, which is the same answer
     * as switching the feature off. [order] is tried first-to-last; a source
     * missing from it (an upgrade that added one after the order was last
     * saved) falls in after everything named, in [LyricsSource]'s own order.
     *
     * [prioritizeSyllableSync] decides what happens once *something* has come
     * back: off, the highest-priority source's own answer is taken as-is,
     * word-synced or not — priority is priority, and second-guessing it with
     * more network calls after it has already answered is not what "first"
     * was supposed to mean. On, a merely line-synced answer is kept only as a
     * fallback, and the search keeps going through the rest of [order] for a
     * word-synced one, taking the top-priority source that has one.
     */
    suspend fun lyrics(
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
        sources: Set<LyricsSource> = LyricsSource.entries.toSet(),
        order: List<LyricsSource> = LyricsSource.entries,
        prioritizeSyllableSync: Boolean = false,
        isrc: String? = null,
    ): Result? = coroutineScope {
        val sequence = order.filter { it in sources } +
            LyricsSource.entries.filter { it in sources && it !in order }

        // Every source but [SimpMusicLyrics] is asked for a name, and
        // YouTube's is not the name anyone catalogued. Cleaned once, here,
        // rather than by whichever source thought to do it for itself.
        val searchTitle = title.forLyricsSearch()
        val searchArtist = artist.artistForLyricsSearch()

        // Settled before anyone is asked for words, so every source that can
        // name the recording does. What the caller knows beats what we worked
        // out last time, and both beat asking again.
        val known = isrc?.takeIf { it.isNotBlank() } ?: isrcs[videoId]
        val hit = if (known == null) {
            identify(videoId, searchTitle, searchArtist, durationMs, album, sequence)
        } else {
            null
        }
        val recording = known ?: hit?.isrc?.takeIf { it.isNotBlank() }

        // Genius is a plain text web scraper. To preserve bandwidth and avoid rate-limiting,
        // it starts lazily and is only contacted if all higher-priority synced sources miss.
        val racing: List<Pair<LyricsSource, Deferred<LyricsArtifact?>>> = sequence.map { source ->
            val startMode = if (source == LyricsSource.GENIUS) kotlinx.coroutines.CoroutineStart.LAZY else kotlinx.coroutines.CoroutineStart.DEFAULT
            source to async(Dispatchers.IO, start = startMode) {
                fetchArtifact(source, videoId, searchTitle, searchArtist, durationMs, album, recording, hit)
            }
        }

        try {
            var lineSynced: Result? = null
            for ((source, job) in racing) {
                // If we already found a line-synced or better result, skip Genius completely
                if (lineSynced != null && source == LyricsSource.GENIUS) continue

                val artifact = runCatching { job.await() }.getOrNull() ?: continue
                val lines = artifact.lines
                if (lines.any { it.isWordSynced }) {
                    LyricsLog.s("Repository", "Word-synced match from ${source.label}")
                    return@coroutineScope result(source, lines, artifact)
                }
                if (!prioritizeSyllableSync && lines.any { it.timeMs > 0 }) {
                    LyricsLog.s("Repository", "Line-synced match from ${source.label}")
                    return@coroutineScope result(source, lines, artifact)
                }
                if (lineSynced == null) lineSynced = result(source, lines, artifact)
            }
            lineSynced
        } finally {
            // Whoever lost the race is no longer worth waiting on, and
            // coroutineScope will not return while they are still running.
            racing.forEach { it.second.cancel() }
        }
    }

    private suspend fun fetchArtifact(
        source: LyricsSource,
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
        isrc: String?,
        /** What [identify] already found, where it ran; saves a second search. */
        hit: BiniLyrics.Hit?,
    ): LyricsArtifact? {
        LyricsLog.i(source.label, "Querying $source...")
        val artifact = when (source) {
            LyricsSource.BETTER_LYRICS -> BetterLyrics.artifact(title, artist, durationMs, album)
            LyricsSource.BETTER_LYRICS_PORTATO -> BetterLyrics.portato(title, artist, durationMs, album)?.let { LyricsSerializer.fromLines(source, it) }
            LyricsSource.LYRICS_PLUS -> LyricsPlus.artifact(title, artist, durationMs, album, isrc)
            LyricsSource.BINI_LYRICS -> (hit?.let { BiniLyrics.lyricsFor(it) } ?: BiniLyrics.lyrics(title, artist, durationMs, album, isrc))
                ?.also { remember(videoId, it.isrc) }?.let { LyricsSerializer.fromLines(source, it.lines) }
            LyricsSource.UNISON -> Unison.lyrics(title, artist, durationMs, album)?.let { LyricsSerializer.fromLines(source, it) }
            LyricsSource.SIMP_MUSIC -> SimpMusicLyrics.artifact(videoId, durationMs)
            LyricsSource.YOUTUBE_TRANSCRIPT -> YouTubeTranscriptLyrics.lyrics(videoId)?.let { LyricsSerializer.fromLines(source, it) }
            LyricsSource.YOUTUBE_MUSIC -> YouTubeMusicLyrics.lyrics(videoId)?.let { LyricsSerializer.fromLines(source, it) }
            LyricsSource.LRCLIB -> LrcLib.artifact(title, artist, durationMs)
            LyricsSource.MUSIXMATCH -> Musixmatch.lyrics(title, artist, durationMs)?.let { LyricsSerializer.fromLines(source, it) }
            LyricsSource.PAXSENIX -> PaxSenix.lyrics(title, artist, durationMs, album)?.let { LyricsSerializer.fromLines(source, it) }
            LyricsSource.PAXSENIX_SPOTIFY -> PaxSenix.spotifyLyrics(title, artist, durationMs)?.let { LyricsSerializer.fromLines(source, it) }
            LyricsSource.PAXSENIX_MUSIXMATCH -> PaxSenix.musixmatchLyrics(title, artist, durationMs)?.let { LyricsSerializer.fromLines(source, it) }
            LyricsSource.KUGOU -> KuGou.lyrics(title, artist, durationMs, album)?.let { LyricsSerializer.fromLines(source, it) }
            LyricsSource.MEGALOBIZ -> Megalobiz.lyrics(title, artist)?.let { LyricsSerializer.fromLines(source, it) }
            LyricsSource.GENIUS -> Genius.lyrics(title, artist)?.let { LyricsSerializer.fromLines(source, it) }
        }
        if (artifact == null || artifact.lines.isEmpty()) LyricsLog.w(source.label, "No lyrics returned")
        else LyricsLog.s(source.label, "Returned ${artifact.lines.size} lines")
        return artifact
    }

    /**
     * Whichever source won, its lines get the same last pass: the answering
     * vocal split off the lead so it can be drawn under it. Done here rather
     * than in each parser because most of them write it as a bracket and only
     * [TtmlLyrics] knows it structurally — [withBackgroundVocals] leaves that
     * one's own split alone.
     */
    private fun result(
        source: LyricsSource,
        lines: List<LyricLine>,
        artifact: LyricsArtifact? = null,
    ): Result {
        val processed = lines.withBackgroundVocals()
        val finalArtifact = artifact ?: LyricsSerializer.fromLines(source, processed)
        return Result(source, processed, finalArtifact)
    }

    /**
     * Longest the lookup will wait to find out which recording this is.
     *
     * Short on purpose. Knowing the recording makes every match better, but not
     * knowing it only puts things back where they were a release ago, and a
     * lyrics panel sitting empty because one host is having a slow morning is a
     * worse failure than a fuzzy match.
     */
    private const val IDENTIFY_TIMEOUT_MS = 2_500L

    /**
     * The one request made before the race: which recording is this?
     *
     * Skipped entirely when the source that answers it is switched off, and
     * given up on rather than waited out — see [IDENTIFY_TIMEOUT_MS]. What it
     * finds is remembered, so a track pays for this once rather than once per
     * lookup, and the hit is handed back so [BiniLyrics] need not search twice.
     */
    private suspend fun identify(
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
        sequence: List<LyricsSource>,
    ): BiniLyrics.Hit? {
        if (LyricsSource.BINI_LYRICS !in sequence) return null
        val hit = withTimeoutOrNull(IDENTIFY_TIMEOUT_MS) {
            runCatching { BiniLyrics.identify(title, artist, durationMs, album) }.getOrNull()
        }
        if (hit == null) return null
        remember(videoId, hit.isrc)
        return hit
    }

    /** How many recordings to keep in hand; see [isrcs]. */
    private const val REMEMBERED = 100

    /**
     * The recording behind a video id, once something has worked it out.
     *
     * Bounded, least-recently-used, and in memory only. This is a shortcut,
     * not a store: losing it costs one fuzzy match, which is what every lookup
     * did before any of this, and keeping it on disk would mean keeping a
     * wrong answer on disk too.
     */
    private val isrcs: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(REMEMBERED, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, String>) = size > REMEMBERED
        },
    )

    private fun remember(videoId: String, isrc: String?) {
        if (isrc.isNullOrBlank() || videoId.isEmpty()) return
        isrcs.put(videoId, isrc)
    }
}
