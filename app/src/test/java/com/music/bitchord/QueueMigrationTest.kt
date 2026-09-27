package com.music.bitchord

import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueMigrationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    private data class StoredTrackForTest(
        val id: String,
        val title: String,
        val artist: String,
        val artwork: String? = null,
        val auto: Boolean = false,
        val tier: String? = null,
        val entryId: String? = null,
    ) {
        fun toSong(): Song {
            val resolvedTier = tier?.let { runCatching { QueueTier.valueOf(it) }.getOrNull() }
                ?: if (auto) QueueTier.AUTOPLAY else QueueTier.CONTEXT
            return Song(
                videoId = id,
                title = title,
                artist = artist,
                thumbnailUrl = artwork,
                queueTier = resolvedTier,
                queueEntryId = entryId,
            )
        }

        companion object {
            fun from(song: Song) = StoredTrackForTest(
                id = song.videoId,
                title = song.title,
                artist = song.artist,
                artwork = song.thumbnailUrl,
                auto = song.fromAutoplay,
                tier = song.queueTier.name,
                entryId = song.queueEntryId,
            )
        }
    }

    @Test
    fun `legacy snapshot with auto false deserializes to CONTEXT tier`() {
        val legacyJson = """
            {
                "id": "v1",
                "title": "Song 1",
                "artist": "Artist 1",
                "auto": false
            }
        """.trimIndent()

        val stored = json.decodeFromString<StoredTrackForTest>(legacyJson)
        val song = stored.toSong()

        assertEquals(QueueTier.CONTEXT, song.queueTier)
    }

    @Test
    fun `legacy snapshot with auto true deserializes to AUTOPLAY tier`() {
        val legacyJson = """
            {
                "id": "v2",
                "title": "Song 2",
                "artist": "Artist 2",
                "auto": true
            }
        """.trimIndent()

        val stored = json.decodeFromString<StoredTrackForTest>(legacyJson)
        val song = stored.toSong()

        assertEquals(QueueTier.AUTOPLAY, song.queueTier)
    }

    @Test
    fun `snapshot with tier USER_QUEUE and entryId round-trips cleanly`() {
        val song = Song(
            videoId = "v3",
            title = "Song 3",
            artist = "Artist 3",
            thumbnailUrl = null,
            queueTier = QueueTier.USER_QUEUE,
            queueEntryId = "uuid-entry-3",
        )

        val stored = StoredTrackForTest.from(song)
        val encoded = json.encodeToString(stored)
        val decoded = json.decodeFromString<StoredTrackForTest>(encoded).toSong()

        assertEquals(QueueTier.USER_QUEUE, decoded.queueTier)
        assertEquals("uuid-entry-3", decoded.queueEntryId)
    }
}
