package com.music.bitchord.playback

import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PartyPlaybackQueueTest {
    private val source = QueueSource("Playlist", PlaybackSourceType.BROWSE, "playlist-id")

    private fun song(id: String, autoplay: Boolean = false) = Song(
        videoId = id,
        title = "Title $id",
        artist = "Artist $id",
        thumbnailUrl = null,
        queueTier = if (autoplay) QueueTier.AUTOPLAY else QueueTier.CONTEXT,
    )

    @Test
    fun `playlist playback shares selected track and following tracks without autoplay`() {
        val songs = listOf(song("a"), song("b"), song("c"))

        val queue = QueueTimeline.buildPartyPlaybackQueue(songs, 0, source, emptyList())

        assertEquals(listOf("a", "b", "c"), queue.map { it.videoId })
        assertEquals(QueueTier.CONTEXT, queue.first().queueTier)
        assertTrue(queue.drop(1).all { it.queueTier == QueueTier.USER_QUEUE })
        assertTrue(queue.all { !it.fromAutoplay })
        queue.forEach {
            assertEquals(source.title, it.playbackSource)
            assertEquals(source.id, it.playbackSourceId)
            assertNotNull(it.queueEntryId)
        }
    }

    @Test
    fun `mid-playlist selection excludes history and preserves upcoming order after manual queue`() {
        val songs = listOf(song("past"), song("selected"), song("next-1"), song("next-2"))
        val manual = listOf(
            song("manual-1").copy(queueEntryId = "existing-entry", playbackSource = "Old playlist"),
            song("stale-auto", autoplay = true),
            song("manual-2"),
        )

        val queue = QueueTimeline.buildPartyPlaybackQueue(songs, 1, source, manual)

        assertEquals(listOf("selected", "manual-1", "manual-2", "next-1", "next-2"), queue.map { it.videoId })
        assertEquals(QueueTier.CONTEXT, queue[0].queueTier)
        assertTrue(queue.drop(1).all { it.queueTier == QueueTier.USER_QUEUE })
        assertEquals("existing-entry", queue[1].queueEntryId)
        assertEquals("Old playlist", queue[1].playbackSource)
        assertTrue(queue.all { it.queueEntryId != null })
        assertEquals("Playlist", queue[0].playbackSource)
        assertEquals("Playlist", queue[3].playbackSource)
    }

    @Test
    fun `upcoming tracks are capped at twenty five including preserved manual tracks`() {
        val songs = List(40) { song("playlist-$it") }
        val manual = List(5) { song("manual-$it") }

        val queue = QueueTimeline.buildPartyPlaybackQueue(songs, 0, source, manual)

        assertEquals(26, queue.size)
        assertEquals(manual.map { it.videoId } + (1..20).map { "playlist-$it" }, queue.drop(1).map { it.videoId })
    }

    @Test
    fun `single selection has no following tracks and empty songs stay empty`() {
        val queue = QueueTimeline.buildPartyPlaybackQueue(listOf(song("only")), 0, source, emptyList())

        assertEquals(listOf("only"), queue.map { it.videoId })
        assertTrue(QueueTimeline.buildPartyPlaybackQueue(emptyList(), 0, source, emptyList()).isEmpty())
    }

    @Test
    fun `selection index clamps and source metadata and queue identities are assigned`() {
        val songs = listOf(song("first"), song("last"))

        val queue = QueueTimeline.buildPartyPlaybackQueue(songs, 99, source, emptyList())

        assertEquals(listOf("last"), queue.map { it.videoId })
        assertEquals("Playlist", queue[0].playbackSource)
        assertEquals(PlaybackSourceType.BROWSE, queue[0].playbackSourceType)
        assertEquals("playlist-id", queue[0].playbackSourceId)
        assertNotNull(queue[0].queueEntryId)
    }
}
