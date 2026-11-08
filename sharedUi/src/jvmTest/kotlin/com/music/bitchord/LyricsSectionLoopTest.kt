package com.music.bitchord

import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.ui.player.deriveLyricLineEndMs
import com.music.bitchord.ui.player.mapLyricRangeToAudioRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsSectionLoopTest {

    private val sampleLines = listOf(
        // Line 0: word-synced with words
        LyricLine(
            timeMs = 10_000L,
            text = "Welcome to the show",
            words = listOf(
                LyricWord(10_000L, 10_500L, "Welcome"),
                LyricWord(10_500L, 11_000L, "to"),
                LyricWord(11_000L, 11_500L, "the"),
                LyricWord(11_500L, 13_200L, "show"),
            ),
        ),
        // Line 1: line-synced with explicit sungUntilMs
        LyricLine(
            timeMs = 15_000L,
            text = "Lights are turning low",
            sungUntilMs = 18_500L,
        ),
        // Line 2: standard line-synced without sungUntilMs
        LyricLine(
            timeMs = 20_000L,
            text = "Can you feel the rhythm go",
        ),
        // Line 3: gap line (instrumental break)
        LyricLine(
            timeMs = 26_000L,
            text = "",
        ),
        // Line 4: final line
        LyricLine(
            timeMs = 35_000L,
            text = "Until the end",
        ),
    )

    @Test
    fun `deriveLyricLineEndMs uses word end when available`() {
        val end = deriveLyricLineEndMs(lineIndex = 0, lines = sampleLines, trackDurationMs = 60_000L)
        assertEquals(13_200L, end)
    }

    @Test
    fun `deriveLyricLineEndMs uses sungUntilMs when available`() {
        val end = deriveLyricLineEndMs(lineIndex = 1, lines = sampleLines, trackDurationMs = 60_000L)
        assertEquals(18_500L, end)
    }

    @Test
    fun `deriveLyricLineEndMs uses next line timeMs when sungUntilMs is absent`() {
        // Line 2 has no sungUntilMs, next is line 3 (gap line at 26_000L)
        val end = deriveLyricLineEndMs(lineIndex = 2, lines = sampleLines, trackDurationMs = 60_000L)
        assertEquals(26_000L, end)
    }

    @Test
    fun `deriveLyricLineEndMs handles gap lines sensibly by using next sung line timeMs`() {
        // Line 3 is a gap line at 26_000L; its end is line 4 at 35_000L
        val end = deriveLyricLineEndMs(lineIndex = 3, lines = sampleLines, trackDurationMs = 60_000L)
        assertEquals(35_000L, end)
    }

    @Test
    fun `deriveLyricLineEndMs uses track duration for last line`() {
        val end = deriveLyricLineEndMs(lineIndex = 4, lines = sampleLines, trackDurationMs = 45_000L)
        assertEquals(45_000L, end)
    }

    @Test
    fun `lyrics offset pins conversion direction correctly`() {
        // Pin conversion direction: positive lyrics offset means lyrics are delayed relative to audio,
        // so audio position must be (lyricTime + offsetMs).
        val offset = 1_500
        val range = mapLyricRangeToAudioRange(
            firstLineIndex = 1,
            lastLineIndex = 1,
            lines = sampleLines,
            lyricsOffsetMs = offset,
            trackDurationMs = 60_000L,
        )
        assertNotNull(range)
        // Line 1: timeMs = 15_000, sungUntilMs = 18_500
        // With +1500ms offset:
        assertEquals(16_500L, range!!.first)
        assertEquals(20_000L, range.second)

        // Negative offset (-1000ms):
        val negRange = mapLyricRangeToAudioRange(
            firstLineIndex = 1,
            lastLineIndex = 1,
            lines = sampleLines,
            lyricsOffsetMs = -1_000,
            trackDurationMs = 60_000L,
        )
        assertNotNull(negRange)
        assertEquals(14_000L, negRange!!.first)
        assertEquals(17_500L, negRange.second)
    }

    @Test
    fun `mapLyricRangeToAudioRange supports multi-line selection`() {
        // Multi-line range from line 0 to line 2 (offset = 0)
        val range = mapLyricRangeToAudioRange(
            firstLineIndex = 0,
            lastLineIndex = 2,
            lines = sampleLines,
            lyricsOffsetMs = 0,
            trackDurationMs = 60_000L,
        )
        assertNotNull(range)
        // Start of line 0 is 10_000L; end of line 2 is next line start (26_000L)
        assertEquals(10_000L, range!!.first)
        assertEquals(26_000L, range.second)
    }

    @Test
    fun `mapLyricRangeToAudioRange normalizes inverted line indices`() {
        // Selection passed with last index first (2 to 0)
        val range = mapLyricRangeToAudioRange(
            firstLineIndex = 2,
            lastLineIndex = 0,
            lines = sampleLines,
            lyricsOffsetMs = 0,
            trackDurationMs = 60_000L,
        )
        assertNotNull(range)
        assertEquals(10_000L, range!!.first)
        assertEquals(26_000L, range.second)
    }

    @Test
    fun `mapLyricRangeToAudioRange clamps to track duration`() {
        val range = mapLyricRangeToAudioRange(
            firstLineIndex = 4,
            lastLineIndex = 4,
            lines = sampleLines,
            lyricsOffsetMs = 2_000,
            trackDurationMs = 38_000L,
        )
        assertNotNull(range)
        assertEquals(37_000L, range!!.first)
        assertEquals(38_000L, range.second)
    }

    @Test
    fun `mapLyricRangeToAudioRange rejects range smaller than minimum gap`() {
        val tinyLines = listOf(
            LyricLine(timeMs = 10_000L, text = "Too short", sungUntilMs = 10_200L), // 200ms gap < 500ms
        )
        val range = mapLyricRangeToAudioRange(
            firstLineIndex = 0,
            lastLineIndex = 0,
            lines = tinyLines,
            lyricsOffsetMs = 0,
            trackDurationMs = 60_000L,
        )
        assertNull(range)
    }
}
