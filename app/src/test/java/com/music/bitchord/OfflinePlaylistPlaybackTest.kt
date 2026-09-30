package com.music.bitchord

import com.music.bitchord.data.playlistPlayback
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class OfflinePlaylistPlaybackTest {
    private fun song(id: String, localUri: String? = null, setVideoId: String? = null) = Song(
        videoId = id,
        title = "Track $id",
        artist = "Artist",
        thumbnailUrl = null,
        localUri = localUri,
        setVideoId = setVideoId,
    )

    @Test
    fun `online playback retains complete playlist and selected position`() {
        val songs = listOf(song("a"), song("b"), song("a", setVideoId = "second-a"))
        val result = playlistPlayback(songs, 2, online = true, saved = mapOf("a" to "file:///a"))!!
        assertSame(songs, result.songs)
        assertEquals(2, result.index)
        assertNull(result.songs[0].localUri)
    }

    @Test
    fun `offline skips unavailable rows in order and remaps tapped index`() {
        val songs = listOf(song("missing"), song("a"), song("missing-two"), song("b"))
        val result = playlistPlayback(
            songs, 3, online = false,
            saved = mapOf("a" to "file:///a", "b" to "content://downloads/b"),
        )!!
        assertEquals(listOf("a", "b"), result.songs.map { it.videoId })
        assertEquals(1, result.index)
        assertEquals("content://downloads/b", result.songs[result.index].localUri)
        assertNull(songs[3].localUri)
    }

    @Test
    fun `offline tap on an unavailable row does not play another track`() {
        val songs = listOf(song("saved"), song("missing"))
        assertNull(playlistPlayback(songs, 1, online = false, saved = mapOf("saved" to "file:///saved")))
    }

    @Test
    fun `offline duplicate occurrences keep the tapped playlist entry`() {
        val songs = listOf(
            song("missing"), song("a", setVideoId = "first-a"),
            song("missing-two"), song("a", setVideoId = "second-a"), song("b"),
        )
        val result = playlistPlayback(
            songs, 3, online = false, saved = mapOf("a" to "file:///a", "b" to "file:///b"),
        )!!
        assertEquals(listOf("a", "a", "b"), result.songs.map { it.videoId })
        assertEquals(1, result.index)
        assertEquals("second-a", result.songs[result.index].setVideoId)
    }

    @Test
    fun `local files and remote source rows keep their existing behavior`() {
        val local = song("local:7", localUri = "content://media/7")
        val remote = song("smb:track")
        val result = playlistPlayback(listOf(song("missing"), local, remote), 2, online = false, saved = emptyMap())!!
        assertEquals(listOf(local, remote), result.songs)
        assertSame(local, result.songs[0])
        assertSame(remote, result.songs[result.index])
    }

    @Test
    fun `empty or out of bounds selections have no playback`() {
        assertNull(playlistPlayback(emptyList(), 0, online = false, saved = emptyMap()))
        assertNull(playlistPlayback(listOf(song("a")), -1, online = true, saved = emptyMap()))
        assertNull(playlistPlayback(listOf(song("a")), 1, online = true, saved = emptyMap()))
        assertNull(playlistPlayback(listOf(song("a")), 0, online = false, saved = mapOf("a" to "")))
    }
}
