package com.music.bitchord

import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LocalMetadataUpdateTest {

    @Test
    fun `song metadata copy updates title artist and album name`() {
        val original = Song(
            videoId = "local-123",
            title = "Old Title",
            artist = "Old Artist",
            albumName = "Old Album",
            localUri = "content://media/external/audio/media/123",
            localPath = "/storage/emulated/0/Music/song.mp3",
        )

        val updated = original.copy(
            title = "New Title",
            artist = "New Artist",
            albumName = "New Album",
        )

        assertEquals("New Title", updated.title)
        assertEquals("New Artist", updated.artist)
        assertEquals("New Album", updated.albumName)
        assertEquals(original.localUri, updated.localUri)
        assertEquals(original.localPath, updated.localPath)
        assertEquals(original.videoId, updated.videoId)
    }

    @Test
    fun `metadata update handles blank album as null`() {
        val song = Song(
            videoId = "local-456",
            title = "Sample Song",
            artist = "Sample Artist",
            albumName = "Sample Album",
            localUri = "file:///storage/emulated/0/Music/track.flac",
        )

        val albumInput = "   "
        val resolvedAlbum = albumInput.trim().takeIf { it.isNotEmpty() }
        val updated = song.copy(albumName = resolvedAlbum)

        assertEquals(null, updated.albumName)
        assertEquals("Sample Song", updated.title)
    }
}
