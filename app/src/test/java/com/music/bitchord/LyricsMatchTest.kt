package com.music.bitchord

import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The last gate before lyrics are written beside a file that will outlive the
 * lookup: a name is not a recording, so the timing has to be checked against
 * the file itself. These are the two ways a wrong answer shows up — no timing
 * at all, and timing that belongs to a different cut.
 *
 * Placeholder text only; no real lyric appears in this file.
 */
class LyricsMatchTest {

    @Test
    fun `plain lyrics are not synchronized lyrics`() {
        val plain = listOf(LyricLine(timeMs = 0L, text = "first line"))
        assertEquals(LyricsMatch.Rejection.UNSYNCED, LyricsMatch.check(plain, 180_000L))
        assertFalse(LyricsMatch.accepts(plain, 180_000L))
    }

    @Test
    fun `an empty result is not synchronized lyrics`() {
        assertEquals(LyricsMatch.Rejection.UNSYNCED, LyricsMatch.check(emptyList(), 180_000L))
    }

    @Test
    fun `timing that fits inside the track is accepted`() {
        val lines = listOf(
            LyricLine(timeMs = 8_000L, text = "first line"),
            LyricLine(timeMs = 90_000L, text = "second line"),
            LyricLine(timeMs = 172_000L, text = "last line"),
        )
        assertEquals(LyricsMatch.Rejection.NONE, LyricsMatch.check(lines, 180_000L))
        assertTrue(LyricsMatch.accepts(lines, 180_000L))
    }

    @Test
    fun `a final stamp inside the end slack is still this recording`() {
        val lines = listOf(
            LyricLine(timeMs = 5_000L, text = "first line"),
            LyricLine(timeMs = 195_000L, text = "last line"),
        )
        assertEquals(LyricsMatch.Rejection.NONE, LyricsMatch.check(lines, 180_000L))
    }

    @Test
    fun `timing that runs past the file belongs to another cut`() {
        val lines = listOf(
            LyricLine(timeMs = 5_000L, text = "first line"),
            LyricLine(timeMs = 260_000L, text = "last line"),
        )
        assertEquals(LyricsMatch.Rejection.RUNS_LONG, LyricsMatch.check(lines, 180_000L))
    }

    @Test
    fun `a sheet that starts after the file ends is rejected even when its last line is close`() {
        val lines = listOf(
            LyricLine(timeMs = 210_000L, text = "first line"),
            LyricLine(timeMs = 215_000L, text = "last line"),
        )
        assertEquals(LyricsMatch.Rejection.RUNS_LONG, LyricsMatch.check(lines, 180_000L))
    }

    @Test
    fun `a sheet that ends far too early belongs to a shorter version`() {
        val lines = listOf(
            LyricLine(timeMs = 5_000L, text = "first line"),
            LyricLine(timeMs = 25_000L, text = "last line"),
        )
        assertEquals(LyricsMatch.Rejection.RUNS_SHORT, LyricsMatch.check(lines, 180_000L))
    }

    @Test
    fun `a short track is not held to the long-file ratio`() {
        // A one-minute track can legitimately have a sheet that ends at 25s;
        // the ratio exists to catch album versions being filed under a live
        // cut, not to reject short songs.
        val lines = listOf(
            LyricLine(timeMs = 2_000L, text = "first line"),
            LyricLine(timeMs = 25_000L, text = "last line"),
        )
        assertEquals(LyricsMatch.Rejection.NONE, LyricsMatch.check(lines, 60_000L))
    }

    @Test
    fun `an unknown duration only rejects the absence of timing`() {
        val timed = listOf(LyricLine(timeMs = 90_000L, text = "a line"))
        assertEquals(LyricsMatch.Rejection.NONE, LyricsMatch.check(timed, 0L))
        val plain = listOf(LyricLine(timeMs = 0L, text = "a line"))
        assertEquals(LyricsMatch.Rejection.UNSYNCED, LyricsMatch.check(plain, 0L))
    }
}
