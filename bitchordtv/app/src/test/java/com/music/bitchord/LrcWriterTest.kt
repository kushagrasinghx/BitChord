package com.music.bitchord

import com.music.bitchord.data.lyrics.LrcLib
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.data.lyrics.toLrc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The LRC writer is what the download path embeds into a saved file's metadata,
 * so what it emits is read back by third-party players this project will never
 * see. That makes the stamp format the whole test surface: a reader that
 * doesn't recognise a stamp doesn't skip the line, it shows the markup, and a
 * stamp that parses but is *wrong* shows the right words at the wrong moment.
 *
 * Everything below uses placeholder text rather than any real lyric.
 */
class LrcWriterTest {

    @Test
    fun `each line is stamped as mm colon ss dot centiseconds`() {
        val lines = listOf(
            LyricLine(timeMs = 0L, text = "first line"),
            LyricLine(timeMs = 61_230L, text = "second line"),
        )
        assertEquals("[00:00.00]first line\n[01:01.23]second line", lines.toLrc())
    }

    @Test
    fun `milliseconds are truncated to centiseconds rather than rounded up past a second`() {
        // 59.999s must not become [01:00.00]: rounding here would push a line
        // past a minute boundary and reorder it against the one that follows.
        assertEquals("[00:59.99]x", listOf(LyricLine(59_999L, "x")).toLrc())
    }

    @Test
    fun `stamps are ascii digits regardless of the default locale`() {
        // `String.format("%02d")` would emit Arabic-Indic digits under this
        // locale, which no LRC parser — including this project's own — matches.
        val original = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"))
            val written = listOf(LyricLine(75_400L, "x")).toLrc()
            assertEquals("[01:15.40]x", written)
        } finally {
            java.util.Locale.setDefault(original)
        }
    }

    @Test
    fun `a stamp past ninety-nine minutes overflows to three digits rather than wrapping`() {
        // Truncating to two digits would silently move the line an hour earlier.
        assertEquals("[100:00.00]x", listOf(LyricLine(6_000_000L, "x")).toLrc())
    }

    @Test
    fun `an instrumental gap is written as a bare stamp`() {
        val lines = listOf(
            LyricLine(timeMs = 1_000L, text = "first line"),
            LyricLine(timeMs = 8_000L, text = ""),
            LyricLine(timeMs = 20_000L, text = "second line"),
        )
        assertEquals("[00:01.00]first line\n[00:08.00]\n[00:20.00]second line", lines.toLrc())
    }

    @Test
    fun `lines are sorted by their stamp, whatever order they arrive in`() {
        val lines = listOf(
            LyricLine(timeMs = 5_000L, text = "later"),
            LyricLine(timeMs = 1_000L, text = "earlier"),
        )
        assertEquals("[00:01.00]earlier\n[00:05.00]later", lines.toLrc())
    }

    @Test
    fun `word timings are dropped and the line's own text is kept whole`() {
        // The A2 word-stamp extension is deliberately not written: a reader
        // without it renders `<00:01.00>` as text rather than ignoring it.
        val lines = listOf(
            LyricLine(
                timeMs = 1_000L,
                text = "two words",
                words = listOf(
                    LyricWord(startMs = 1_000L, endMs = 1_400L, text = "two"),
                    LyricWord(startMs = 1_400L, endMs = 2_000L, text = "words"),
                ),
            ),
        )
        val written = lines.toLrc()
        assertEquals("[00:01.00]two words", written)
        assertTrue("no word stamps", '<' !in written)
    }

    /**
     * The backing-vocal split is a display decision; the file gets the line
     * the way every provider that didn't mark it structurally published it.
     * Written as two stamps it would be two lines of the song where there is
     * one, and the second would be timed over the top of the next one.
     */
    @Test
    fun `an answering vocal is written back onto the end of its lead`() {
        val lines = listOf(
            LyricLine(
                timeMs = 1_000L,
                text = "the lead line",
                background = LyricLine(timeMs = 1_600L, text = "(the answer)"),
            ),
        )
        assertEquals("[00:01.00]the lead line (the answer)", lines.toLrc())
    }

    @Test
    fun `no lines is empty text rather than a blank stamp`() {
        assertEquals("", emptyList<LyricLine>().toLrc())
    }

    /**
     * The one property that matters end to end: what this writes,
     * [LrcLib.parseLrc] reads back to the same stamps and the same words. The
     * gaps here are spaced past `MIN_GAP_MS` so the parser's own short-gap
     * filtering doesn't drop them and make this a test of two behaviours.
     */
    @Test
    fun `what it writes, the parser reads back unchanged`() {
        val lines = listOf(
            LyricLine(timeMs = 0L, text = ""),
            LyricLine(timeMs = 6_120L, text = "first line"),
            LyricLine(timeMs = 12_340L, text = "second line"),
            LyricLine(timeMs = 18_000L, text = ""),
            LyricLine(timeMs = 25_500L, text = "third line"),
        )

        val reparsed = LrcLib.parseLrc(lines.toLrc())

        assertEquals(lines.map { it.timeMs }, reparsed.map { it.timeMs })
        assertEquals(lines.map { it.text }, reparsed.map { it.text })
    }
}
