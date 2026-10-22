package com.music.bitchord.playback

import com.music.bitchord.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExplicitFilterTest {
    private fun song(id: String, explicit: Boolean?) =
        Song(videoId = id, title = id, artist = "a", thumbnailUrl = null, isExplicit = explicit)

    @Test
    fun blocksOnlyMarkedExplicitSongsWhileOn() {
        assertTrue(ExplicitFilter.blocks(song("e", true), enabled = true, allowed = emptySet()))
        assertFalse(ExplicitFilter.blocks(song("e", true), enabled = false, allowed = emptySet()))
        assertFalse(ExplicitFilter.blocks(song("c", false), enabled = true, allowed = emptySet()))
        assertFalse(ExplicitFilter.blocks(song("u", null), enabled = true, allowed = emptySet()))
    }

    @Test
    fun allowedSongsPlay() {
        assertFalse(ExplicitFilter.blocks(song("e", true), enabled = true, allowed = setOf("e")))
    }

    @Test
    fun filterKeepsOrderAndDropsBlocked() {
        val songs = listOf(song("a", false), song("b", true), song("c", true), song("d", null))
        assertEquals(listOf("a", "c", "d"), ExplicitFilter.filter(songs, true, setOf("c")).map { it.videoId })
        assertEquals(songs, ExplicitFilter.filter(songs, false, emptySet()))
    }
}
