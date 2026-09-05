package com.music.bitchord.data.lyrics

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Where the player gets its lyrics.
 *
 * Eight sources, tried in [order] — the user's own priority list in Settings,
 * defaulting to [LyricsSource.entries]:
 *
 *  - [BetterLyrics] and [PaxSenix] — Apple Music TTML, per-syllable, from two
 *    independent hosts so one having a bad day doesn't cost the timing.
 *  - [LyricsPlus] — the YouLy+ backend; finest timing of the lot, flakiest hosting.
 *  - [SimpMusicLyrics] — keyed on the video id, so it can't fetch the wrong edit.
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
    ): LyricsArtifact? = lyrics(
        videoId = videoId,
        title = title,
        artist = artist,
        durationMs = durationMs,
        album = album,
        sources = sources,
        order = order,
        prioritizeSyllableSync = prioritizeSyllableSync,
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
    ): Result? = coroutineScope {
        LyricsLog.clear()
        LyricsLog.i("Repository", "Looking up lyrics for \"$title\" by \"$artist\" (${durationMs / 1000}s)")

        val sequence = order.filter { it in sources } +
            LyricsSource.entries.filter { it in sources && it !in order }

        LyricsLog.i("Repository", "Active sources order: ${sequence.joinToString { it.label }}")

        // Genius is a plain text web scraper. To preserve bandwidth and avoid rate-limiting,
        // it starts lazily and is only contacted if all higher-priority synced sources miss.
        val racing: List<Pair<LyricsSource, Deferred<LyricsArtifact?>>> = sequence.map { source ->
            val startMode = if (source == LyricsSource.GENIUS) kotlinx.coroutines.CoroutineStart.LAZY else kotlinx.coroutines.CoroutineStart.DEFAULT
            source to async(Dispatchers.IO, start = startMode) {
                fetchArtifact(source, videoId, title, artist, durationMs, album)
            }
        }

        try {
            var lineSynced: Result? = null
            for ((source, job) in racing) {
                // If we already found a line-synced or better result, skip Genius completely
                if (lineSynced != null && source == LyricsSource.GENIUS) {
                    LyricsLog.i("Repository", "Skipping Genius fallback because higher-priority source answered")
                    continue
                }

                if (source == LyricsSource.GENIUS && lineSynced == null) {
                    LyricsLog.w("Repository", "All synced providers missed. Running Genius fallback...")
                }

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
    ): LyricsArtifact? {
        LyricsLog.i(source.label, "Querying $source...")
        val artifact = when (source) {
            LyricsSource.BETTER_LYRICS -> BetterLyrics.artifact(title, artist, durationMs, album)
            LyricsSource.LYRICS_PLUS -> LyricsPlus.artifact(title, artist, durationMs, album)
            LyricsSource.SIMP_MUSIC -> SimpMusicLyrics.artifact(videoId, durationMs)
            LyricsSource.LRCLIB -> LrcLib.artifact(title, artist, durationMs)
            LyricsSource.MUSIXMATCH -> Musixmatch.lyrics(title, artist, durationMs)?.let {
                LyricsSerializer.fromLines(LyricsSource.MUSIXMATCH, it)
            }
            LyricsSource.PAXSENIX -> PaxSenix.lyrics(title, artist, durationMs, album)?.let {
                LyricsSerializer.fromLines(LyricsSource.PAXSENIX, it)
            }
            LyricsSource.KUGOU -> KuGou.lyrics(title, artist, durationMs, album)?.let {
                LyricsSerializer.fromLines(LyricsSource.KUGOU, it)
            }
            LyricsSource.GENIUS -> Genius.lyrics(title, artist)?.let {
                LyricsSerializer.fromLines(LyricsSource.GENIUS, it)
            }
        }
        if (artifact == null || artifact.lines.isEmpty()) {
            LyricsLog.w(source.label, "No lyrics returned")
        } else {
            val syncType = when {
                artifact.isWordSynced -> "word-synced"
                artifact.lines.any { it.timeMs > 0 } -> "line-synced"
                else -> "plain text"
            }
            LyricsLog.s(source.label, "Returned ${artifact.lines.size} lines ($syncType)")
        }
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
}
