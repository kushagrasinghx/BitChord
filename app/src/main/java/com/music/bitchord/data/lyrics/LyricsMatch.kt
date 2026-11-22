package com.music.bitchord.data.lyrics

/**
 * Whether a set of lyrics can honestly be filed against a local recording.
 *
 * The providers are asked with the file's title, artist, album and length, but
 * a name is not a recording: a live cut, a remix and the album version can all
 * answer to the same three words. Two cheap checks catch the failures that
 * matter when the file is kept rather than shown once:
 *
 *  - **Plain text is not synchronized lyrics.** A result with no timestamps is
 *    a poem, not something a player can follow, and writing it as `.lrc` would
 *    claim a sync that does not exist.
 *  - **The words must fit inside the track.** A sheet that starts after the
 *    file ends, or runs minutes past it, belongs to another recording. The
 *    tolerance is deliberately generous — intros, outros and a stray final
 *    timestamp are normal — while still rejecting a different song's timing.
 *
 * Both are deliberately independent of which provider answered. The repository
 * already does the metadata matching and picks the best synced result; this is
 * the last gate before those lines are written beside a file that will outlive
 * the lookup.
 */
object LyricsMatch {

    /** How far past the file's end a final line may sit before it is another recording. */
    const val END_SLACK_MS = 20_000L

    /**
     * Below this share of the file's length, a sheet is more likely to be a
     * shorter version of the song than a real lyric sheet with a long outro.
     */
    private const val MIN_END_RATIO = 0.20

    enum class Rejection {
        NONE,

        /** No line carries a timestamp — plain lyrics, not synchronized ones. */
        UNSYNCED,

        /** The timing runs past the file: almost certainly a different cut. */
        RUNS_LONG,

        /** The timing ends far too early to be this recording. */
        RUNS_SHORT,
    }

    fun check(lines: List<LyricLine>, durationMs: Long): Rejection {
        val timed = lines.filter { it.timeMs > 0L }
        if (timed.isEmpty()) return Rejection.UNSYNCED
        if (durationMs <= 0L) return Rejection.NONE
        val first = timed.minOf { it.timeMs }
        val last = timed.maxOf { it.timeMs }
        if (first > durationMs + END_SLACK_MS || last > durationMs + END_SLACK_MS) {
            return Rejection.RUNS_LONG
        }
        if (durationMs >= 120_000L && last < (durationMs * MIN_END_RATIO).toLong()) {
            return Rejection.RUNS_SHORT
        }
        return Rejection.NONE
    }

    fun accepts(lines: List<LyricLine>, durationMs: Long): Boolean =
        check(lines, durationMs) == Rejection.NONE
}
