package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.spotify.LocalPlaylist
import com.music.bitchord.data.spotify.LocalPlaylistStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPlaylistStoreTest {

    private fun sampleSong(id: String, title: String) = Song(
        videoId = id,
        title = title,
        artist = "Artist",
        thumbnailUrl = null,
    )

    @Test
    fun `getPlaylist resolves by raw id and browseId`() {
        val playlist = LocalPlaylist(
            id = "sp_local_123",
            title = "My Spotify Playlist",
            songs = listOf(sampleSong("v1", "Song 1")),
        )
        LocalPlaylistStore.setPlaylistsForTesting(listOf(playlist))

        assertEquals("My Spotify Playlist", LocalPlaylistStore.getPlaylist("sp_local_123")?.title)
        assertEquals("My Spotify Playlist", LocalPlaylistStore.getPlaylist("local:playlist:sp_local_123")?.title)
        assertNull(LocalPlaylistStore.getPlaylist("non_existent"))
    }

    @Test
    fun `deletePlaylist removes playlist by id and browseId`() {
        val p1 = LocalPlaylist(id = "sp_local_1", title = "P1", songs = emptyList())
        val p2 = LocalPlaylist(id = "sp_local_2", title = "P2", songs = emptyList())
        LocalPlaylistStore.setPlaylistsForTesting(listOf(p1, p2))

        LocalPlaylistStore.deletePlaylist("sp_local_1")
        assertNull(LocalPlaylistStore.getPlaylist("sp_local_1"))
        assertNotNull(LocalPlaylistStore.getPlaylist("sp_local_2"))

        LocalPlaylistStore.deletePlaylist("local:playlist:sp_local_2")
        assertNull(LocalPlaylistStore.getPlaylist("sp_local_2"))
        assertTrue(LocalPlaylistStore.playlists.value.isEmpty())
    }

    @Test
    fun `renamePlaylist updates playlist title`() {
        val playlist = LocalPlaylist(
            id = "sp_local_rename",
            title = "Old Name",
            songs = listOf(sampleSong("v1", "Track 1")),
        )
        LocalPlaylistStore.setPlaylistsForTesting(listOf(playlist))

        LocalPlaylistStore.renamePlaylist("sp_local_rename", "New Name")
        assertEquals("New Name", LocalPlaylistStore.getPlaylist("sp_local_rename")?.title)
        assertEquals(1, LocalPlaylistStore.getPlaylist("sp_local_rename")?.songs?.size)
    }

    @Test
    fun `removeSong removes track from playlist`() {
        val playlist = LocalPlaylist(
            id = "sp_local_songs",
            title = "Test List",
            songs = listOf(sampleSong("v1", "Track 1"), sampleSong("v2", "Track 2")),
        )
        LocalPlaylistStore.setPlaylistsForTesting(listOf(playlist))

        LocalPlaylistStore.removeSong("sp_local_songs", "v1")
        val updated = LocalPlaylistStore.getPlaylist("sp_local_songs")
        assertNotNull(updated)
        assertEquals(1, updated?.songs?.size)
        assertEquals("v2", updated?.songs?.first()?.videoId)
    }
}
