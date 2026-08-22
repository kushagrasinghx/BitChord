package com.music.bitchord.data.lyrics

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Where the player gets its lyrics.
 *
 * Four sources, in this order:
 *
 *  1. [BetterLyrics] — Apple Music TTML, per-syllable, keyed on title/artist.
 *  2. [LyricsPlus] — the YouLy+ backend; finest timing of the lot, flakiest hosting.
 *  3. [SimpMusicLyrics] — keyed on the video id, so it can't fetch the wrong edit.
 *  4. [LrcLib] — line-synced only, but it is the one that is always up.
 *
 * The first three are asked *at the same time* and their answers taken in that
 * order. Asked one after another, a miss on each of the first two would cost
 * its own round trip before the third was even tried, and a track with no
 * lyrics anywhere would spend the best part of twenty seconds finding that
 * out. Run together, a miss costs whatever the slowest one took.
 *
 * A word-timed answer wins outright. Failing that, a line-timed one is taken
 * from the highest-priority source that had it — better a whole line lighting
 * up in sync than the right animation on lyrics that don't exist.
 */
object LyricsRepository {

    /** Lyrics, and which of the four they turned out to come from. */
    data class Result(val source: LyricsSource, val lines: List<LyricLine>)

    /**
     * [sources] is the user's pick from Settings; anything not in it is not
     * contacted at all. An empty set means no lyrics, which is the same answer
     * as switching the feature off.
     */
    suspend fun lyrics(
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
        sources: Set<LyricsSource> = LyricsSource.entries.toSet(),
    ): Result? = coroutineScope {
        val racing: List<Pair<LyricsSource, Deferred<List<LyricLine>?>>> = buildList {
            if (LyricsSource.BETTER_LYRICS in sources) {
                add(
                    LyricsSource.BETTER_LYRICS to
                        async(Dispatchers.IO) { BetterLyrics.lyrics(title, artist, durationMs, album) },
                )
            }
            if (LyricsSource.LYRICS_PLUS in sources) {
                add(
                    LyricsSource.LYRICS_PLUS to
                        async(Dispatchers.IO) { LyricsPlus.lyrics(title, artist, durationMs, album) },
                )
            }
            if (LyricsSource.SIMP_MUSIC in sources) {
                add(
                    LyricsSource.SIMP_MUSIC to
                        async(Dispatchers.IO) { SimpMusicLyrics.lyrics(videoId, durationMs) },
                )
            }
        }

        try {
            var lineSynced: Result? = null
            for ((source, job) in racing) {
                val lines = runCatching { job.await() }.getOrNull() ?: continue
                if (lines.any { it.isWordSynced }) return@coroutineScope Result(source, lines)
                if (lineSynced == null) lineSynced = Result(source, lines)
            }
            lineSynced ?: if (LyricsSource.LRCLIB in sources) {
                LrcLib.lyrics(title, artist, durationMs)?.let { Result(LyricsSource.LRCLIB, it) }
            } else {
                null
            }
        } finally {
            // Whoever lost the race is no longer worth waiting on, and
            // coroutineScope will not return while they are still running.
            racing.forEach { it.second.cancel() }
        }
    }
}
