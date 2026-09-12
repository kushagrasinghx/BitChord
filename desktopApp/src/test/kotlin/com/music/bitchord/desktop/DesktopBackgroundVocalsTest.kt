package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The answering vocal, pulled out of the bracket a provider wrote it into.
 *
 * Left inline it is dragged through the lead's own sweep and cut off mid-phrase, because the words
 * in it are sung *over* the line that follows rather than as part of this one.
 */
class DesktopBackgroundVocalsTest {

    private fun word(text: String, start: Long, end: Long) = DesktopLyricWord(start, end, text)

    @Test
    fun `a trailing bracket becomes the line's background`() {
        val line = DesktopLyricLine(timeMs = 12_420, text = "I get my weed from California (That's that shit)")
        val split = listOf(line).withBackgroundVocals().single()
        assertEquals("I get my weed from California", split.text)
        assertEquals("(That's that shit)", split.background?.text)
        // The punctuation is what the provider published and what a download has to write back.
        assertNotNull(split.background)
    }

    @Test
    fun `a word-synced line hands the bracket its own words and start`() {
        val words = listOf(
            word("Hold", 1_000, 1_500),
            word("on", 1_500, 2_000),
            word("(hold", 2_100, 2_400),
            word("on)", 2_400, 2_800),
        )
        val split = listOf(
            DesktopLyricLine(timeMs = 1_000, text = "Hold on (hold on)", words = words),
        ).withBackgroundVocals().single()

        assertEquals("Hold on", split.text)
        assertEquals(2, split.words.size)
        assertEquals("(hold on)", split.background?.text)
        assertEquals(2_100L, split.background?.timeMs)
        assertEquals(2, split.background?.words?.size)
    }

    @Test
    fun `a line that is entirely a bracket is already its own backing line`() {
        val line = DesktopLyricLine(timeMs = 5_000, text = "(Ooh ooh)")
        assertNull(listOf(line).withBackgroundVocals().single().background)
    }

    @Test
    fun `a bracket opening mid-word is not a word boundary and is left alone`() {
        val words = listOf(word("wait(ing)", 1_000, 1_800))
        val split = listOf(
            DesktopLyricLine(timeMs = 1_000, text = "wait(ing)", words = words),
        ).withBackgroundVocals().single()
        assertNull(split.background)
        assertEquals("wait(ing)", split.text)
    }

    @Test
    fun `a nested bracket splits at the outer pair`() {
        val split = listOf(
            DesktopLyricLine(timeMs = 0, text = "Lead line (answer (echo))"),
        ).withBackgroundVocals().single()
        assertEquals("Lead line", split.text)
        assertEquals("(answer (echo))", split.background?.text)
    }

    @Test
    fun `a source that already marked its backing vocal is not second-guessed`() {
        val line = DesktopLyricLine(
            timeMs = 0,
            text = "Lead (something)",
            background = DesktopLyricLine(timeMs = 0, text = "(already known)"),
        )
        assertEquals("(already known)", listOf(line).withBackgroundVocals().single().background?.text)
    }

    @Test
    fun `a gap and a bracket with no letters in it are left alone`() {
        assertNull(listOf(DesktopLyricLine(timeMs = 0, text = "")).withBackgroundVocals().single().background)
        assertNull(
            listOf(DesktopLyricLine(timeMs = 0, text = "Lead (...)")).withBackgroundVocals().single().background,
        )
    }
}
