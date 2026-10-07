package com.music.bitchord

import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.data.lyrics.withBackgroundVocals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Only Apple's TTML marks the answering vocal outright. Every other provider
 * writes it into the line as a bracket, and left there it was sung over the
 * top of the *next* line's stamp — so the cursor moved on with the bracket
 * half-swept and the strip cut it off. These cover the split that pulls it out.
 *
 * The lines are the shape LyricsPlus serves "Heartbreak Anniversary" in, with
 * placeholder words.
 */
class BackgroundVocalTest {

    private fun words(vararg spans: Triple<Long, Long, String>) =
        spans.map { (start, end, text) -> LyricWord(start, end, text) }

    private fun wordSynced(vararg spans: Triple<Long, Long, String>): LyricLine {
        val list = words(*spans)
        return LyricLine(
            timeMs = list.first().startMs,
            text = list.joinToString(" ") { it.text },
            words = list,
        )
    }

    @Test
    fun `a trailing bracket becomes the answering line`() {
        val line = listOf(LyricLine(1_000L, "lead words (echoed words)")).withBackgroundVocals().single()
        assertEquals("lead words", line.text)
        assertEquals("(echoed words)", line.background?.text)
    }

    @Test
    fun `the answering line takes the words that were inside the bracket`() {
        val line = listOf(
            wordSynced(
                Triple(1_000L, 1_400L, "lead"),
                Triple(1_400L, 1_900L, "words"),
                Triple(2_100L, 2_500L, "(echoed"),
                Triple(2_500L, 3_200L, "words)"),
            ),
        ).withBackgroundVocals().single()

        assertEquals("lead words", line.text)
        assertEquals(listOf("lead", "words"), line.words.map { it.text })

        val backing = line.background!!
        assertEquals("(echoed words)", backing.text)
        assertEquals(listOf("(echoed", "words)"), backing.words.map { it.text })
        // Its own stamp, so it sweeps on its own clock rather than the lead's.
        assertEquals(2_100L, backing.timeMs)
        assertTrue(backing.isWordSynced)
    }

    /**
     * The bug the split exists for: the answer runs past the lead's last word,
     * and the line is not over until it stops.
     */
    @Test
    fun `the line ends when the answer does, not when the lead does`() {
        val line = listOf(
            wordSynced(
                Triple(1_000L, 1_400L, "lead"),
                Triple(2_100L, 3_200L, "(echo)"),
            ),
        ).withBackgroundVocals().single()
        assertEquals(3_200L, line.endMs)
    }

    @Test
    fun `a line that is entirely bracketed is left as it is`() {
        // It is already its own line; there is no lead to hang it under.
        val line = listOf(LyricLine(1_000L, "(ooh ooh)")).withBackgroundVocals().single()
        assertEquals("(ooh ooh)", line.text)
        assertNull(line.background)
    }

    @Test
    fun `a bracket in the middle of a line is left alone`() {
        val line = listOf(LyricLine(1_000L, "a (parenthetical) aside")).withBackgroundVocals().single()
        assertEquals("a (parenthetical) aside", line.text)
        assertNull(line.background)
    }

    @Test
    fun `a bracket opening mid-word is not a second voice`() {
        // "wait(ing)" is one word; there is no word boundary to split on and
        // nothing to give the answering line for timing.
        val line = listOf(
            wordSynced(
                Triple(1_000L, 1_400L, "still"),
                Triple(1_400L, 2_000L, "wait(ing)"),
            ),
        ).withBackgroundVocals().single()
        assertEquals("still wait(ing)", line.text)
        assertNull(line.background)
    }

    @Test
    fun `nesting splits at the outer bracket`() {
        val line = listOf(LyricLine(1_000L, "lead (echo (twice))")).withBackgroundVocals().single()
        assertEquals("lead", line.text)
        assertEquals("(echo (twice))", line.background?.text)
    }

    @Test
    fun `a bracket with no words in it is not a second voice`() {
        val line = listOf(LyricLine(1_000L, "lead words (!)")).withBackgroundVocals().single()
        assertEquals("lead words (!)", line.text)
        assertNull(line.background)
    }

    @Test
    fun `a line-synced answer shares the line's stamp and its stated end`() {
        val line = listOf(
            LyricLine(timeMs = 1_000L, text = "lead words (echo)", sungUntilMs = 4_000L),
        ).withBackgroundVocals().single()
        assertEquals("lead words", line.text)
        assertEquals(1_000L, line.background?.timeMs)
        assertEquals(4_000L, line.background?.sungUntilMs)
        assertEquals(4_000L, line.endMs)
    }

    @Test
    fun `a source that marked its own answer is not second-guessed`() {
        val marked = LyricLine(
            timeMs = 1_000L,
            text = "lead words (already split)",
            background = LyricLine(1_500L, "(the real answer)"),
        )
        val line = listOf(marked).withBackgroundVocals().single()
        assertEquals(marked, line)
    }

    @Test
    fun `instrumental breaks are left untouched`() {
        val line = listOf(LyricLine(1_000L, "")).withBackgroundVocals().single()
        assertTrue(line.isGap)
        assertNull(line.background)
    }
}
