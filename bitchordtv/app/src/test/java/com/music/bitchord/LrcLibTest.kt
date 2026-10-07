package com.music.bitchord

import com.music.bitchord.data.lyrics.LrcLib
import com.music.bitchord.data.lyrics.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcLibTest {

    /** Sung lines only — a long intro gets a synthesised gap in front. */
    private fun List<LyricLine>.words() = filterNot { it.isGap }.map { it.text }

    @Test
    fun `parses centisecond stamps`() {
        val lines = LrcLib.parseLrc(
            """
            [00:32.07] first line
            [01:05.50] second line
            """.trimIndent(),
        ).filterNot { it.isGap }
        assertEquals(2, lines.size)
        assertEquals(32_070L, lines[0].timeMs)
        assertEquals("first line", lines[0].text)
        assertEquals(65_500L, lines[1].timeMs)
    }

    @Test
    fun `parses millisecond stamps`() {
        val lines = LrcLib.parseLrc("[02:03.456] third line").filterNot { it.isGap }
        assertEquals(123_456L, lines.single().timeMs)
    }

    @Test
    fun `drops metadata tags and short gaps`() {
        val lines = LrcLib.parseLrc(
            """
            [ar:Arijit Singh]
            [ti:Zaalima]
            [00:10.00]
            [00:12.00] real words

            """.trimIndent(),
        )
        // The 2s gap between the two stamps is too short to be worth showing.
        assertEquals(listOf("real words"), lines.words())
        assertEquals(1, lines.count { it.isGap })
    }

    @Test
    fun `keeps long instrumental gaps`() {
        val lines = LrcLib.parseLrc(
            """
            [00:00.00] intro words
            [00:05.00]
            [00:30.00] verse
            """.trimIndent(),
        )
        assertEquals(3, lines.size)
        assertTrue(lines[1].isGap)
        assertEquals(5_000L, lines[1].timeMs)
    }

    @Test
    fun `keeps a trailing gap as the outro`() {
        val lines = LrcLib.parseLrc("[00:10.00] words\n[04:49.01] ")
        assertEquals(listOf("words"), lines.words())
        assertEquals(289_010L, lines.last().timeMs)
        assertTrue(lines.last().isGap)
    }

    @Test
    fun `adds a leading gap for a long intro`() {
        val lines = LrcLib.parseLrc("[00:32.07] first words")
        assertEquals(2, lines.size)
        assertTrue(lines[0].isGap)
        assertEquals(0L, lines[0].timeMs)
        assertEquals("first words", lines[1].text)
    }

    @Test
    fun `no leading gap when singing starts straight away`() {
        val lines = LrcLib.parseLrc("[00:01.00] straight in")
        assertEquals(1, lines.size)
        assertEquals("straight in", lines.single().text)
    }

    @Test
    fun `sorts out of order stamps`() {
        val lines = LrcLib.parseLrc("[00:30.00] later\n[00:10.00] earlier")
        assertEquals(listOf("earlier", "later"), lines.words())
        val sung = lines.filterNot { it.isGap }
        assertTrue(sung[0].timeMs < sung[1].timeMs)
    }
}
