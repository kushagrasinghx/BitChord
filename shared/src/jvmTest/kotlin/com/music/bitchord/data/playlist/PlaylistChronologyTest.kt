package com.music.bitchord.data.playlist

import com.music.bitchord.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaylistChronologyTest {
    private fun song(id: String, entry: String = "entry-$id") =
        Song(videoId = id, title = id, artist = "Artist", thumbnailUrl = null, setVideoId = entry)

    @Test
    fun `first snapshot preserves server order as a chronology baseline`() {
        val first = preservePlaylistAddedOrder(null, listOf(song("new"), song("middle"), song("old")))
        assertEquals(listOf("new", "middle", "old"),
            first.sortedByDescending { it.playlistAddedOrdinal }.map { it.videoId })
        assertEquals(listOf("old", "middle", "new"),
            first.sortedBy { it.playlistAddedOrdinal }.map { it.videoId })
    }

    @Test
    fun `new additions sort above earlier songs even if the server places them elsewhere`() {
        val existing = preservePlaylistAddedOrder(null, listOf(song("bye-bye"), song("dragon-ball")))
        val refreshed = preservePlaylistAddedOrder(existing,
            listOf(song("lemonade"), song("bye-bye"), song("dragon-ball"), song("deserve")))
        assertEquals(listOf("lemonade", "deserve"), refreshed
            .sortedByDescending { it.playlistAddedOrdinal }.take(2).map { it.videoId })
        assertEquals("dragon-ball", refreshed.minBy { it.playlistAddedOrdinal!! }.videoId)
    }

    @Test
    fun `reordering and metadata refresh retain the original insertion ranks`() {
        val original = preservePlaylistAddedOrder(null, listOf(song("A"), song("B")))
        val reordered = preservePlaylistAddedOrder(original, listOf(song("B").copy(title = "new title"), song("A")))
        assertEquals(original.associate { it.videoId to it.playlistAddedOrdinal },
            reordered.associate { it.videoId to it.playlistAddedOrdinal })
    }

    @Test
    fun `duplicates by recording have independent insertion ranks`() {
        val old = preservePlaylistAddedOrder(null, listOf(song("same", "first")))
        val now = preservePlaylistAddedOrder(old, listOf(song("same", "second"), song("same", "first")))
        assertEquals(2, now.size)
        assertTrue(now[0].playlistAddedOrdinal!! > now[1].playlistAddedOrdinal!!)
    }

    @Test
    fun `old schema snapshots with missing sequence are upgraded without dropping tracks`() {
        val legacy = listOf(song("A"), song("B"), song("C"))
        val migrated = preservePlaylistAddedOrder(legacy, legacy)
        assertEquals(listOf(3L, 2L, 1L), migrated.map { it.playlistAddedOrdinal })
    }
}
