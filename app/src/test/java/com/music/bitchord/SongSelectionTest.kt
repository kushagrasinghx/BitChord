package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.SongSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Picking songs on a page to act on together. */
class SongSelectionTest {
    private fun song(id: String) = Song(videoId = id, title = "Song $id", artist = "Artist", thumbnailUrl = null)
    private val page = listOf(song("a"), song("b"), song("c"), song("d"))

    @Test
    fun aTapPutsASongInAndTakesItOut() {
        var selection = SongSelection("VLx").toggled(page[1])
        assertTrue(page[1] in selection)
        selection = selection.toggled(page[1])
        assertFalse(page[1] in selection)
        assertEquals(0, selection.count)
    }

    @Test
    fun pickedSongsComeBackInThePagesOrder() {
        val selection = SongSelection("VLx").toggled(page[3]).toggled(page[0]).toggled(page[2])
        assertEquals(listOf("a", "c", "d"), selection.of(page).map { it.videoId })
    }

    @Test
    fun selectAllPicksEverySong() {
        assertEquals(4, SongSelection("VLx").withAll(page).count)
    }
}
