package com.music.bitchord.download

import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingDownloadTest {

    private fun song(id: String) = Song(
        videoId = id,
        title = "Title $id",
        artist = "Artist $id",
        thumbnailUrl = "https://example.test/$id.jpg",
        durationText = "3:21",
        albumName = "Album",
        sourceQuality = "LOSSLESS",
    )

    @Test
    fun codecPreservesOrderAndReconstructionFieldsAcrossRestart() {
        val jobs = listOf(
            PendingDownload.from(song("a"), "album", enqueuedAt = 1L),
            PendingDownload.from(song("b"), null, enqueuedAt = 2L),
        )
        val decoded = DownloadJobCodec.decode(DownloadJobCodec.encode(jobs))
        assertEquals(listOf("a", "b"), decoded.map { it.videoId })
        assertEquals("Title a", decoded[0].toSong().title)
        assertEquals("Artist b", decoded[1].toSong().artist)
        assertEquals("LOSSLESS", decoded[0].toSong().sourceQuality)
        assertEquals("album", decoded[0].from)
        assertEquals(2L, decoded[1].enqueuedAt)
    }

    @Test
    fun malformedJournalIsEmptyRatherThanPartiallyTrusted() {
        assertTrue(DownloadJobCodec.decode("not json").isEmpty())
        assertTrue(DownloadJobCodec.decode(null).isEmpty())
    }
}
