package com.music.bitchord

import com.music.bitchord.data.settings.withSongEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class SongLyricsEntryTest {

    @Test
    fun `setting a song makes it newest and the oldest falls off past the cap`() {
        val map = mapOf("a" to 1, "b" to 2, "c" to 3)

        assertEquals(listOf("b", "c", "a"), map.withSongEntry("a", 9, max = 3).keys.toList())
        assertEquals(9, map.withSongEntry("a", 9, max = 3)["a"])
        assertEquals(listOf("c", "d"), map.withSongEntry("d", 4, max = 2).keys.toList())
    }

    @Test
    fun `null removes the song`() {
        assertEquals(mapOf("b" to 2), mapOf("a" to 1, "b" to 2).withSongEntry("a", null))
    }
}
