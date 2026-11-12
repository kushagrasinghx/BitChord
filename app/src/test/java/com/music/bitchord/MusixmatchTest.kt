package com.music.bitchord

import com.music.bitchord.data.lyrics.Musixmatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusixmatchTest {

    @Test
    fun `rich sync keeps word timing and merges syllable fragments`() {
        val body = """
            [{
              "ts":48.502,
              "te":50.813,
              "x":"I save dick by giving it CPR",
              "l":[
                {"c":"I","o":0.000},{"c":" ","o":0.120},
                {"c":"save ","o":0.180},{"c":"dick ","o":0.520},
                {"c":"by ","o":0.870},{"c":"giv","o":1.080},
                {"c":"ing ","o":1.240},{"c":"it ","o":1.560},
                {"c":"CPR","o":1.820}
              ]
            }]
        """.trimIndent()

        val line = Musixmatch.parseRichSyncBody(body).single { !it.isGap }

        assertTrue(line.isWordSynced)
        assertEquals("I save dick by giving it CPR", line.text)
        assertEquals(
            listOf("I", "save", "dick", "by", "giving", "it", "CPR"),
            line.words.map { it.text },
        )
        assertEquals(48_502L, line.timeMs)
        assertEquals(49_582L, line.words[4].startMs)
        assertEquals(50_813L, line.words.last().endMs)
    }

    @Test
    fun `rich sync never emits backward word times`() {
        val body = """
            [{"ts":10.0,"te":12.0,"x":"one two three","l":[
              {"c":"one ","o":0.0},
              {"c":"two ","o":0.7},
              {"c":"three","o":0.65}
            ]}]
        """.trimIndent()

        val words = Musixmatch.parseRichSyncBody(body).single { !it.isGap }.words
        assertEquals(listOf(10_000L, 10_700L, 10_700L), words.map { it.startMs })
        assertTrue(words.zipWithNext().all { (left, right) -> left.startMs <= right.startMs })
        assertTrue(words.all { it.endMs >= it.startMs })
    }

    @Test
    fun `malformed rich sync is ignored`() {
        assertEquals(emptyList<Any>(), Musixmatch.parseRichSyncBody("not json"))
    }
}
