package com.music.bitchord.playback

import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.listentogether.PartyPlayback
import com.music.bitchord.data.listentogether.PartyPlaylistContinuation
import com.music.bitchord.data.listentogether.PartyQueue
import com.music.bitchord.data.listentogether.PartyTrack
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
    fun `complete playlist is retained beyond the shared queue window`() {
        val songs = List(40) { song("playlist-$it") }
        val manual = List(5) { song("manual-$it") }

        val queue = QueueTimeline.buildPartyPlaybackQueue(songs, 0, source, manual)

        assertEquals(45, queue.size)
        assertEquals(manual.map { it.videoId } + (1..39).map { "playlist-$it" }, queue.drop(1).map { it.videoId })
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

    @Test
    fun `party queue jumps preserve the continuation tail and subsequent refill order`() {
        val songs = List(70) { index -> song("playlist-${index % 4}").copy(title = "Track $index") }
        val timeline = QueueTimeline.buildPartyPlaybackQueue(songs, 0, source, emptyList())
        val tracks = timeline.map { PartyTrack(it.videoId, title = it.title) }
        val defaultJump = QueueTimeline.computeJumpOrder(
            tiers = timeline.map { it.queueTier },
            currentIndex = 0,
            targetIndex = 10,
        )!!
        assertEquals(listOf(0) + (10 until timeline.size).toList(), defaultJump.newOrderIndices)

        val jump = QueueTimeline.computeJumpOrder(
            tiers = timeline.map { it.queueTier },
            currentIndex = 0,
            targetIndex = 10,
            preserveQueue = true,
        )!!
        assertEquals(timeline.indices.toList(), jump.newOrderIndices)
        assertEquals(10, jump.newTargetIndex)
        val partyOrder = jump.newOrderIndices.map(tracks::get)

        val continuation = PartyPlaylistContinuation()
        var queue = continuation.start(partyOrder, index = 0, baseQueueSeq = 5).copy(seq = 5)
        var playback = PartyPlayback(track = partyOrder[0], queueSeq = 5, queueLength = queue.items.size, queueIndex = 0)
        assertTrue(continuation.nextBatch(queue, playback).isEmpty())

        // Seeking ahead to playlist item 10 leaves the same server queue and opens ten slots.
        playback = playback.copy(track = partyOrder[10], queueIndex = 10)
        val batch = continuation.nextBatch(queue, playback)
        assertEquals((26..35).map { partyOrder[it] }, batch)
        assertEquals((26..35).map { partyOrder[it].videoId }, batch.map { it.videoId })

        queue = queue.copy(seq = 6, items = queue.items + batch)
        playback = playback.copy(queueSeq = 6, queueLength = queue.items.size)
        assertTrue(continuation.nextBatch(queue, playback).isEmpty())
        assertTrue(queue.items.size - playback.queueIndex - 1 <= 25)
        assertTrue(continuation.active)

        // A backward seek followed by another forward tap also leaves the running order intact.
        val backward = QueueTimeline.computeJumpOrder(
            timeline.map { it.queueTier }, currentIndex = 10, targetIndex = 4, preserveQueue = true,
        )!!
        val forward = QueueTimeline.computeJumpOrder(
            timeline.map { it.queueTier }, currentIndex = 4, targetIndex = 10, preserveQueue = true,
        )!!
        assertEquals(timeline.indices.toList(), backward.newOrderIndices)
        assertEquals(timeline.indices.toList(), forward.newOrderIndices)

        playback = playback.copy(track = partyOrder[11], queueIndex = 11)
        assertEquals(listOf(partyOrder[36]), continuation.nextBatch(queue, playback))
    }
}
