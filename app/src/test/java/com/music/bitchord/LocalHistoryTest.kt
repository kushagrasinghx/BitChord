package com.music.bitchord

import com.music.bitchord.data.LocalHistoryCodec
import com.music.bitchord.data.LocalHistoryRepository
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.StoredTrack
import com.music.bitchord.data.recordInHistory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalHistoryTest {

    private fun fakeSong(id: String) = Song(
        videoId = id,
        title = "Title $id",
        artist = "Artist $id",
        thumbnailUrl = null,
    )

    @Before
    fun setup() {
        LocalHistoryRepository.clear()
    }

    @Test
    fun `record prepends new track and dedupes existing track to top`() {
        LocalHistoryRepository.record(fakeSong("1"))
        LocalHistoryRepository.record(fakeSong("2"))
        LocalHistoryRepository.record(fakeSong("1"))

        val ids = LocalHistoryRepository.all().map { it.videoId }
        assertEquals(listOf("1", "2"), ids)
    }

    @Test
    fun `tracks without an id are not recorded`() {
        LocalHistoryRepository.record(fakeSong(""))
        LocalHistoryRepository.record(fakeSong("   "))

        assertTrue(LocalHistoryRepository.all().isEmpty())
    }

    @Test
    fun `history is capped at one hundred entries`() {
        for (i in 1..120) {
            LocalHistoryRepository.record(fakeSong("id_$i"))
        }

        val all = LocalHistoryRepository.all()
        assertEquals(100, all.size)
        assertEquals("id_120", all.first().videoId)
    }

    @Test
    fun `record ignores redundant call when same song is already at top`() {
        LocalHistoryRepository.record(fakeSong("1"))
        LocalHistoryRepository.record(fakeSong("2"))
        val before = LocalHistoryRepository.all()

        // Calling record with the currently playing song (index 0) is a no-op
        LocalHistoryRepository.record(fakeSong("2"))
        val after = LocalHistoryRepository.all()

        assertEquals(before, after)
    }

    @Test
    fun `stored track round-trips cleanly through json serialization`() {
        val original = Song(
            videoId = "vid_123",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            thumbnailUrl = "https://example.com/art.jpg",
            durationText = "5:55",
            artistId = "UCqueen",
            albumId = "MPREb_queen",
            albumName = "A Night at the Opera",
            isVideo = true,
            isExplicit = false,
            fromAutoplay = true,
            localUri = "content://media/1",
            localPath = "/sdcard/music/1.mp3",
        )

        val json = Json { ignoreUnknownKeys = true }
        val stored = StoredTrack.from(original)
        val serialized = json.encodeToString(listOf(stored))
        val deserialized = json.decodeFromString<List<StoredTrack>>(serialized).first().toSong()

        assertEquals(original.videoId, deserialized.videoId)
        assertEquals(original.title, deserialized.title)
        assertEquals(original.artist, deserialized.artist)
        assertEquals(original.thumbnailUrl, deserialized.thumbnailUrl)
        assertEquals(original.durationText, deserialized.durationText)
        assertEquals(original.artistId, deserialized.artistId)
        assertEquals(original.albumId, deserialized.albumId)
        assertEquals(original.albumName, deserialized.albumName)
        assertEquals(original.isVideo, deserialized.isVideo)
        assertEquals(original.isExplicit, deserialized.isExplicit)
        assertEquals(original.fromAutoplay, deserialized.fromAutoplay)
        assertEquals(original.localUri, deserialized.localUri)
        assertEquals(original.localPath, deserialized.localPath)
    }

    @Test
    fun `recording only touches the current listener's bucket`() {
        val first = recordInHistory(emptyMap(), "listener_a", fakeSong("a1"))!!
        val both = recordInHistory(first, "listener_b", fakeSong("b1"))!!

        assertEquals(listOf("a1"), both["listener_a"]?.map { it.videoId })
        assertEquals(listOf("b1"), both["listener_b"]?.map { it.videoId })
    }

    @Test
    fun `recording the same track at the top of a bucket is a no-op`() {
        val history = recordInHistory(emptyMap(), "listener_a", fakeSong("a1"))!!

        assertNull(recordInHistory(history, "listener_a", fakeSong("a1")))
    }

    @Test
    fun `the entry limit applies per listener bucket`() {
        var history = emptyMap<String, List<Song>>()
        repeat(3) { index ->
            history = recordInHistory(history, "listener_a", fakeSong("a$index"), limit = 2)!!
        }
        history = recordInHistory(history, "listener_b", fakeSong("b1"), limit = 2)!!

        assertEquals(listOf("a2", "a1"), history["listener_a"]?.map { it.videoId })
        assertEquals(listOf("b1"), history["listener_b"]?.map { it.videoId })
    }

    @Test
    fun `history codec keeps one bucket per listener`() {
        val history = mapOf(
            "guest" to listOf(fakeSong("guest_1"), fakeSong("guest_2")),
            "account:profile" to listOf(fakeSong("account_1")),
        )

        assertEquals(history, LocalHistoryCodec.decode(LocalHistoryCodec.encode(history)))
    }

    @Test
    fun `unreadable history decodes to empty`() {
        assertEquals(emptyMap<String, List<Song>>(), LocalHistoryCodec.decode("not json"))
    }
}
