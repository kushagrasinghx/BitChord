package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.SongSort
import com.music.bitchord.ui.matchOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class SpotifyMatchOrderTest {
    private fun row(title: String) = Song(videoId = "spotify-pending:$title", title = title, artist = "A", thumbnailUrl = null)

    private val rows = listOf(row("Old"), row("Middle"), row("New"))

    @Test
    fun recentlyAddedMatchesTheNewestRowsFirst() {
        assertEquals(listOf(2, 1, 0), matchOrder(rows, SongSort.DATE_ADDED_DESC))
    }

    @Test
    fun theDefaultOrderIsThePlaylistsOwn() {
        assertEquals(listOf(0, 1, 2), matchOrder(rows, SongSort.DEFAULT))
    }

    @Test
    fun twoCopiesOfATrackKeepTheirOwnPlaces() {
        val twice = listOf(row("Same"), row("Other"), row("Same"))
        assertEquals(listOf(2, 1, 0), matchOrder(twice, SongSort.DATE_ADDED_DESC))
    }
}
