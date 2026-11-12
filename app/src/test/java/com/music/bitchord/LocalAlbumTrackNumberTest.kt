package com.music.bitchord

import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sortedForAlbum
import com.music.bitchord.data.useTrackTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAlbumTrackNumberTest {

    private fun song(id: String, title: String, album: String? = "Album", track: Int? = null) =
        Song(videoId = id, title = title, artist = "Artist", thumbnailUrl = null, albumName = album, trackNumber = track)

    @Test
    fun mediaStore_unpacksDiscPrefix() {
        assertEquals(3, LocalMediaRepository.trackNumberFromMediaStore(3))
        assertEquals(3, LocalMediaRepository.trackNumberFromMediaStore(1003))
        assertEquals(1, LocalMediaRepository.trackNumberFromMediaStore(2001))
        assertNull(LocalMediaRepository.trackNumberFromMediaStore(0))
        assertNull(LocalMediaRepository.trackNumberFromMediaStore(-1))
        assertNull(LocalMediaRepository.trackNumberFromMediaStore(null))
    }

    @Test
    fun tag_parsesHeadOfTotal() {
        assertEquals(3, LocalMediaRepository.trackNumberFromTag("3"))
        assertEquals(3, LocalMediaRepository.trackNumberFromTag("3/12"))
        assertEquals(7, LocalMediaRepository.trackNumberFromTag(" 7 / 10 "))
        assertNull(LocalMediaRepository.trackNumberFromTag(null))
        assertNull(LocalMediaRepository.trackNumberFromTag("abc"))
        assertNull(LocalMediaRepository.trackNumberFromTag("0"))
    }

    @Test
    fun album_sortsByTagNotTitle() {
        // Title-sorted incoming order, as the library leaves it.
        val group = listOf(
            song("c", "C Song", track = 3),
            song("a", "A Song", track = 1),
            song("b", "B Song", track = 2),
        )
        assertEquals(listOf("a", "b", "c"), group.sortedForAlbum().map { it.videoId })
    }

    @Test
    fun album_withoutTags_keepsIncomingOrder() {
        val group = listOf(
            song("b", "B Song"),
            song("a", "A Song"),
        )
        assertEquals(listOf("b", "a"), group.sortedForAlbum().map { it.videoId })
    }

    @Test
    fun album_untaggedRows_sortLast() {
        val group = listOf(
            song("u", "A Untagged"),
            song("t2", "B Song", track = 2),
            song("t1", "C Song", track = 1),
        )
        assertEquals(listOf("t1", "t2", "u"), group.sortedForAlbum().map { it.videoId })
    }

    @Test
    fun useTrackTags_onlyForSingleAlbumWithTags() {
        assertTrue(
            listOf(song("a", "A", album = "Album", track = 2), song("b", "B", album = "Album", track = 1)).useTrackTags(),
        )
        // Artist mix across albums keeps positional numbering.
        assertFalse(
            listOf(song("a", "A", album = "One", track = 1), song("b", "B", album = "Two", track = 1)).useTrackTags(),
        )
        assertFalse(listOf(song("a", "A", album = "Album"), song("b", "B", album = "Album")).useTrackTags())
        assertFalse(emptyList<Song>().useTrackTags())
    }
}
