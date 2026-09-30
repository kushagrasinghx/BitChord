package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.download.DownloadTarget
import com.music.bitchord.download.Downloads
import com.music.bitchord.download.SavedCollection
import com.music.bitchord.download.SavedSongMetadata
import com.music.bitchord.download.savedCollectionSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DownloadedPlaylistCacheTest {
    private fun song(id: String) = Song(id, id, "Artist", null)

    @Before
    fun reset() = clearCollections()

    @After
    fun tearDown() = clearCollections()

    private fun clearCollections() {
        Downloads.collections.value.keys.toList().forEach(Downloads::forgetCollection)
    }

    @Test
    fun `online refresh removes old tracks and saves the latest order`() {
        Downloads.rememberCollection(
            DownloadTarget("VLPL1", "Old name", "Owner", playlist = true),
            listOf(song("a"), song("b"), song("c")),
        )

        Downloads.updateCollectionMetadata(
            "VLPL1", "New name", "New owner", videoIds = listOf("c", "new", "a"),
        )

        val record = Downloads.collections.value.getValue("VLPL1")
        assertEquals("New name", record.title)
        assertEquals("New owner", record.subtitle)
        assertEquals(listOf("c", "new", "a"), record.videoIds)
        assertTrue(record.playlist)
    }

    @Test
    fun `rename preserves membership and the downloaded cover`() {
        Downloads.rememberCollection(
            DownloadTarget("VLPL1", "Old name", thumbnailUrl = "https://example/old.jpg"),
            listOf(song("a"), song("b")),
        )
        val localCover = "file:///private/download-artwork/cover.jpg"
        Downloads.rememberCollectionArtwork("VLPL1", localCover)

        Downloads.updateCollectionMetadata("VLPL1", "New name", "Owner", "https://example/new.jpg")

        val record = Downloads.collections.value.getValue("VLPL1")
        assertEquals(localCover, record.thumbnailUrl)
        assertEquals(listOf("a", "b"), record.videoIds)
    }

    @Test
    fun `remote cover can refresh and an empty latest playlist clears membership`() {
        Downloads.rememberCollection(
            DownloadTarget("VLPL1", "Name", thumbnailUrl = "https://example/old.jpg"),
            listOf(song("a")),
        )

        Downloads.updateCollectionMetadata(
            "VLPL1", "Name", "", "https://example/new.jpg", videoIds = emptyList(),
        )

        val record = Downloads.collections.value.getValue("VLPL1")
        assertEquals("https://example/new.jpg", record.thumbnailUrl)
        assertTrue(record.videoIds.isEmpty())
    }

    @Test
    fun `refresh does not create a downloaded collection for an online playlist`() {
        Downloads.updateCollectionMetadata("VLPL1", "Online only", "Owner", videoIds = listOf("a"))
        assertTrue(Downloads.collections.value.isEmpty())
        assertTrue(Downloads.collectionSnapshot("VLPL1").isEmpty())
    }

    @Test
    fun `instant snapshot follows playlist order and resolves aliases without file reads`() {
        val missingFile = "file:///does-not-exist/audio.flac"
        val metadata = mapOf(
            "a" to SavedSongMetadata("a", "First", "Artist", uri = "content://saved/a"),
            "catalogue" to SavedSongMetadata(
                "catalogue", "Alias track", "Artist", uri = missingFile,
                downloadFormat = "FLAC", dateAddedSeconds = 123,
            ),
        )
        val record = SavedCollection(
            "VLPL1", "Name", videoIds = listOf("video", "not-downloaded", "a", "catalogue"),
        )

        val songs = savedCollectionSnapshot(
            record,
            metadata,
            mapOf("video" to missingFile, "a" to "content://saved/a", "catalogue" to missingFile),
        )

        assertEquals(listOf("video", "a"), songs.map { it.videoId })
        assertEquals("Alias track", songs.first().title)
        assertEquals(missingFile, songs.first().localUri)
        assertEquals("FLAC", songs.first().downloadFormat)
        assertEquals(123L, songs.first().localDateAddedSeconds)
    }
}
