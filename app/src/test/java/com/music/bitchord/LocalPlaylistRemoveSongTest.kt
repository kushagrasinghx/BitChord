package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.spotify.LocalPlaylistStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A playlist imported onto the device (signed out, or YouTube refused it) is
 * edited through [LocalPlaylistStore.removeSong]: one row out, the rest kept.
 */
class LocalPlaylistRemoveSongTest {

    private val made = mutableListOf<String>()

    @After
    fun tearDown() = made.forEach(LocalPlaylistStore::deletePlaylist)

    private fun song(id: String) = Song(videoId = id, title = id, artist = "A", thumbnailUrl = null)

    private fun save(vararg ids: String) =
        LocalPlaylistStore.savePlaylist("Imported", ids.map(::song)).also { made += it.browseId }

    private fun idsOf(browseId: String) =
        LocalPlaylistStore.getPlaylist(browseId)!!.songs.map { it.videoId }

    @Test
    fun `removes the picked song and keeps the rest in order`() {
        val playlist = save("a", "b", "c")
        LocalPlaylistStore.removeSong(playlist.browseId, song("b"))
        assertEquals(listOf("a", "c"), idsOf(playlist.browseId))
    }

    @Test
    fun `a song held twice loses one copy`() {
        val playlist = save("a", "b", "a")
        LocalPlaylistStore.removeSong(playlist.browseId, song("a"))
        assertEquals(listOf("b", "a"), idsOf(playlist.browseId))
    }

    @Test
    fun `a song not on the playlist changes nothing`() {
        val playlist = save("a", "b")
        LocalPlaylistStore.removeSong(playlist.browseId, song("z"))
        assertEquals(listOf("a", "b"), idsOf(playlist.browseId))
    }
}
