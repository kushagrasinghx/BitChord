package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.SongSort
import com.music.bitchord.playback.contextEntryKey
import com.music.bitchord.ui.screens.matching
import com.music.bitchord.ui.screens.sortedForDetail
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a tapped detail list may hand playback the page snapshot instead (#1141). */
class InPageOrderTest {

    private val page = List(5) { i ->
        Song(videoId = "track-$i", title = "Song $i", artist = "Artist", albumName = null, thumbnailUrl = null)
    }
    private val pageKeys = page.map { it.contextEntryKey() }
    private fun List<Song>.keys() = map { it.contextEntryKey() }

    @Test
    fun `the page as it came, or searched down, keeps its order`() {
        assertTrue(inPageOrder(page.sortedForDetail(SongSort.DEFAULT).keys(), pageKeys))
        assertTrue(inPageOrder(page.matching("Song 1").map { it.value }.keys(), pageKeys))
        assertTrue(inPageOrder(listOf(page[0], page[2], page[4]).keys(), pageKeys))
    }

    @Test
    fun `a sorted list does not`() {
        assertFalse(inPageOrder(page.sortedForDetail(SongSort.DATE_ADDED_DESC).keys(), pageKeys))
        assertFalse(inPageOrder(page.sortedForDetail(SongSort.TITLE_DESC).keys(), pageKeys))
    }

    @Test
    fun `a song the page doesn't have does not`() {
        assertFalse(inPageOrder(listOf("elsewhere"), pageKeys))
    }
}
