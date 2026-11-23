package com.music.bitchord.data.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals

class LrcVoiceTagTest {
    private fun sung(lrc: String) = LrcLib.parseLrc(lrc).filterNot { it.isGap }

    @Test
    fun aDuetsSingerTagIsNotReadAsWords() {
        val lines = sung(
            """
            [00:01.00]v1: <00:01.00>Hello <00:01.50>there
            [00:03.00]V2: General Kenobi
            """.trimIndent(),
        )
        assertEquals(listOf("Hello there", "General Kenobi"), lines.map { it.text })
        assertEquals("Hello", lines[0].words.first().text.trim())
    }

    @Test
    fun theSingersTakeTurnsAcrossThePanel() {
        val lines = sung(
            """
            [00:01.00]v1:One
            [00:02.00]v2:Two
            [00:03.00]v1:Three
            """.trimIndent(),
        )
        assertEquals(
            listOf(LyricAlignment.Start, LyricAlignment.End, LyricAlignment.Start),
            lines.map { it.alignment },
        )
    }

    @Test
    fun aLineWithoutATagIsLeftAlone() {
        assertEquals(listOf("v1 is my favourite"), sung("[00:01.00]v1 is my favourite").map { it.text })
    }
}
