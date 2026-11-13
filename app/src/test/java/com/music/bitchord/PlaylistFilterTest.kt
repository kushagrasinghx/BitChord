package com.music.bitchord

import com.music.bitchord.ui.components.filterPlaylists
import org.junit.Assert.assertEquals
import org.junit.Test

/** Add to playlist's Find a playlist box. */
class PlaylistFilterTest {
    private val names = listOf("Nepali Rock", "Road Trip", "rock classics", "Beyoncé mix")
    private fun find(query: String) = filterPlaylists(names, query) { it }

    @Test
    fun everyPlaylistForABlankSearch() = assertEquals(names, find("  "))

    @Test
    fun everyWordInAnyOrderAndCase() {
        assertEquals(listOf("Nepali Rock", "rock classics"), find("ROCK"))
        assertEquals(listOf("Nepali Rock"), find("rock nep"))
        assertEquals(emptyList<String>(), find("jazz"))
    }

    @Test
    fun accentsDoNotMatter() = assertEquals(listOf("Beyoncé mix"), find("beyonce"))
}
