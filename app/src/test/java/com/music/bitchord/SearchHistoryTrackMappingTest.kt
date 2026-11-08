package com.music.bitchord

import com.music.bitchord.data.model.EntityType
import com.music.bitchord.data.model.SearchHistoryEntity
import com.music.bitchord.data.model.toSong
import com.music.bitchord.data.model.withCorrectedType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SearchHistoryTrackMappingTest {

    @Test
    fun `track entity converts to Song with matching fields`() {
        val entity = SearchHistoryEntity(
            id = "vid_123",
            title = "Test Song",
            subtitle = "Test Artist",
            artworkUrl = "https://example.com/art.jpg",
            entityType = EntityType.TRACK,
        )

        val song = entity.toSong()

        assertNotNull(song)
        assertEquals("vid_123", song!!.videoId)
        assertEquals("Test Song", song.title)
        assertEquals("Test Artist", song.artist)
        assertEquals("https://example.com/art.jpg", song.thumbnailUrl)
    }

    @Test
    fun `non-track entities return null song`() {
        val album = SearchHistoryEntity(
            id = "album_123",
            title = "Test Album",
            subtitle = "Test Artist",
            artworkUrl = "https://example.com/album.jpg",
            entityType = EntityType.ALBUM,
        )
        val artist = album.copy(entityType = EntityType.ARTIST)
        val playlist = album.copy(entityType = EntityType.PLAYLIST)

        assertNull(album.toSong())
        assertNull(artist.toSong())
        assertNull(playlist.toSong())
    }

    @Test
    fun `raw query entries return null song`() {
        val query = SearchHistoryEntity(
            id = "q:some search",
            title = "some search",
            subtitle = "",
            artworkUrl = null,
            entityType = EntityType.TRACK,
        )

        assertNull(query.toSong())
    }

    @Test
    fun `podcast show saved as a track by an older build no longer plays`() {
        val show = SearchHistoryEntity(
            id = "MPSPPLE0Jo6NF_JYO5-phess8GKafKMtPv3tfZ",
            title = "Huberman Lab",
            subtitle = "Podcast",
            artworkUrl = null,
            entityType = EntityType.TRACK,
        ).withCorrectedType()

        assertEquals(EntityType.OTHER, show.entityType)
        assertNull(show.toSong())
    }

    @Test
    fun `correcting types leaves a real track alone`() {
        val track = SearchHistoryEntity(
            id = "vid_123",
            title = "Test Song",
            subtitle = "Test Artist",
            artworkUrl = null,
            entityType = EntityType.TRACK,
        )

        assertEquals(track, track.withCorrectedType())
    }
}
