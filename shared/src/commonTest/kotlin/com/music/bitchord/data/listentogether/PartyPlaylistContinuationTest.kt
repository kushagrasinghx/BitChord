package com.music.bitchord.data.listentogether

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PartyPlaylistContinuationTest {
    private fun playlist(size: Int) = (0 until size).map { PartyTrack("id-${it % 3}", title = "$it") }

    @Test
    fun `starting another playlist replaces the old unqueued songs`() {
        val continuation = PartyPlaylistContinuation()
        continuation.start(playlist(70), 0, 0)
        val replacement = (0 until 40).map { PartyTrack("new-$it") }
        val queue = continuation.start(replacement, 0, 10).copy(seq = 11)
        assertEquals(listOf(replacement[26]), continuation.nextBatch(
            queue, PartyPlayback(queueSeq = 11, queueIndex = 1)))

        val single = continuation.start(listOf(PartyTrack("single")), 0, 11).copy(seq = 12)
        assertTrue(continuation.nextBatch(single, PartyPlayback(queueSeq = 12, queueIndex = 0)).isEmpty())
    }

    @Test
    fun `reconnect retries a lost batch but acknowledges an accepted one`() {
        val all = playlist(40)
        val continuation = PartyPlaylistContinuation()
        val queue = continuation.start(all, 0, 0).copy(seq = 1)
        val playback = PartyPlayback(queueSeq = 1, queueIndex = 1, queueLength = 26)
        val batch = continuation.nextBatch(queue, playback)
        continuation.onReconnect(queue.seq)
        assertEquals(batch, continuation.nextBatch(queue, playback))
        continuation.onReconnect(queue.seq + 1)
        assertTrue(continuation.nextBatch(queue.copy(seq = 2, items = queue.items + batch),
            playback.copy(queueSeq = 2, queueLength = 27)).isEmpty())
        assertEquals(listOf(all[27]), continuation.nextBatch(
            queue.copy(seq = 2, items = queue.items + batch),
            playback.copy(queueSeq = 2, queueLength = 27, queueIndex = 2)))
    }

    @Test
    fun `reselecting the existing queue does not need a new queue sequence`() {
        val all = playlist(40)
        val continuation = PartyPlaylistContinuation()
        val queue = continuation.start(all, 0, 5).copy(seq = 5)
        val playback = PartyPlayback(track = all[0], queueSeq = 5, queueIndex = 0, queueLength = 26)
        assertTrue(continuation.nextBatch(queue, playback).isEmpty())
        assertEquals(listOf(all[26]), continuation.nextBatch(queue, playback.copy(queueIndex = 1)))
    }

    @Test
    fun `early skip before initial echo can refill an unchanged queue sequence`() {
        val all = playlist(40)
        val continuation = PartyPlaylistContinuation()
        val queue = continuation.start(all, index = 0, baseQueueSeq = 5).copy(seq = 5)
        val playback = PartyPlayback(
            track = all[3],
            queueSeq = 5,
            queueLength = queue.items.size,
            queueIndex = 3,
        )

        assertEquals(all.subList(26, 29), continuation.nextBatch(queue, playback))
    }

    @Test
    fun `a competing addition that rejects a batch retries when playback opens room`() {
        val all = playlist(40)
        val continuation = PartyPlaylistContinuation()
        var queue = continuation.start(all, 0, 0).copy(seq = 1)
        var playback = PartyPlayback(queueSeq = 1, queueIndex = 1, queueLength = 26)
        assertEquals(listOf(all[26]), continuation.nextBatch(queue, playback))

        queue = queue.copy(seq = 2, items = queue.items + PartyTrack("manual"))
        playback = playback.copy(queueSeq = 2, queueLength = 27)
        assertTrue(continuation.nextBatch(queue, playback).isEmpty())
        assertTrue(continuation.nextBatch(queue, playback).isEmpty())

        playback = playback.copy(queueIndex = 2)
        assertEquals(listOf(all[26]), continuation.nextBatch(queue, playback))
        assertTrue(continuation.nextBatch(queue, playback).isEmpty())
    }

    @Test
    fun `rejected refill recovers when playback advances and history is trimmed`() {
        val all = playlist(70)
        val continuation = PartyPlaylistContinuation()
        val initial = continuation.start(all, 5, 0).copy(seq = 1)
        val playback = PartyPlayback(seq = 1, queueSeq = 1, queueIndex = 6)
        val batch = continuation.nextBatch(initial, playback)
        val competitor = initial.copy(seq = 2, items = initial.items + PartyTrack("manual"))
        assertTrue(continuation.nextBatch(competitor, playback.copy(queueSeq = 2)).isEmpty())
        val trimmed = competitor.copy(seq = 3, items = competitor.items.drop(1))
        assertEquals(batch, continuation.nextBatch(trimmed, playback.copy(seq = 2, queueSeq = 3)))
    }

    @Test
    fun `an accepted batch remains acknowledged when a later append hides its tail`() {
        val all = playlist(40)
        val continuation = PartyPlaylistContinuation()
        val initial = continuation.start(all, 0, 0).copy(seq = 1)
        val playback = PartyPlayback(queueSeq = 1, queueIndex = 1, queueLength = 26)
        val batch = continuation.nextBatch(initial, playback)
        assertEquals(listOf(all[26]), batch)

        // The client observes only the latest snapshot: B was accepted at seq 2,
        // then another member appended M at seq 3 before this frame was handled.
        val combined = initial.copy(
            seq = 3,
            items = initial.items + batch + PartyTrack("manual"),
        )
        val afterAppend = PartyPlayback(queueSeq = 3, queueIndex = 2, queueLength = 28)
        assertTrue(continuation.nextBatch(combined, afterAppend).isEmpty())
        assertEquals(listOf(all[27]), continuation.nextBatch(combined, afterAppend.copy(queueIndex = 3)))
    }

    @Test
    fun `playlist continues in bounded batches across more than sixty tracks`() {
        val all = playlist(70)
        val continuation = PartyPlaylistContinuation()
        val initial = continuation.start(all, index = 0, baseQueueSeq = 4)
        assertEquals(all.take(26), initial.items)

        var queue = initial.copy(seq = 5, items = initial.items.map { it.copy(title = "server metadata") })
        var playback = PartyPlayback(queueSeq = 5, queueLength = 26, queueIndex = 0)
        assertTrue(continuation.nextBatch(queue, playback).isEmpty())
        // Repeated state frames cannot resend the initial playlist or a batch.
        assertTrue(continuation.nextBatch(queue, playback).isEmpty())

        val received = initial.items.toMutableList()
        var seq = queue.seq
        var index = 0
        while (received.size < all.size) {
            index++ // Playing another track opens one slot under the 25-upcoming limit.
            playback = PartyPlayback(queueSeq = seq, queueLength = queue.items.size, queueIndex = index)
            val batch = continuation.nextBatch(queue, playback)
            assertEquals(1, batch.size)
            assertTrue(continuation.nextBatch(queue, playback).isEmpty())

            received += batch
            seq++
            queue = queue.copy(seq = seq, items = received.toList())
            playback = playback.copy(queueSeq = seq, queueLength = queue.items.size)
            continuation.nextBatch(queue, playback) // Acknowledge the appended batch.
        }
        assertEquals(all, received)
    }

    @Test
    fun `appended continuation page is supplied after the retained tail is exhausted`() {
        val firstPage = (0 until 28).map { PartyTrack("first-$it") }
        val continuation = PartyPlaylistContinuation()
        var queue = continuation.start(firstPage, index = 0, baseQueueSeq = 0).copy(seq = 1)
        var playback = PartyPlayback(queueSeq = 1, queueLength = 26, queueIndex = 1)

        val firstBatch = continuation.nextBatch(queue, playback)
        assertEquals(listOf(firstPage[26]), firstBatch)
        queue = queue.copy(seq = 2, items = queue.items + firstBatch)
        playback = playback.copy(queueSeq = 2, queueLength = queue.items.size)
        assertTrue(continuation.nextBatch(queue, playback).isEmpty())

        playback = playback.copy(queueIndex = 2)
        val finalBatch = continuation.nextBatch(queue, playback)
        assertEquals(listOf(firstPage[27]), finalBatch)
        queue = queue.copy(seq = 3, items = queue.items + finalBatch)
        playback = playback.copy(queueSeq = 3, queueLength = queue.items.size)
        assertTrue(continuation.nextBatch(queue, playback).isEmpty())

        val nextPage = listOf(PartyTrack("next-0"), PartyTrack("next-1"))
        continuation.append(nextPage)
        playback = playback.copy(queueIndex = 3)
        assertEquals(listOf(nextPage.first()), continuation.nextBatch(queue, playback))
    }

    @Test
    fun `many refill batches preserve repeated occurrences and stay within capacity`() {
        val all = playlist(100)
        val continuation = PartyPlaylistContinuation()
        var queue = continuation.start(all, index = 0, baseQueueSeq = 4).copy(seq = 5)
        var playback = PartyPlayback(queueSeq = 5, queueLength = queue.items.size, queueIndex = 0)
        assertTrue(continuation.nextBatch(queue, playback).isEmpty())

        val received = queue.items.toMutableList()
        var index = 0
        while (received.size < all.size) {
            index++
            playback = PartyPlayback(queueSeq = queue.seq, queueLength = queue.items.size, queueIndex = index)
            assertTrue(queue.items.size - playback.queueIndex - 1 <= 25)
            val batch = continuation.nextBatch(queue, playback)
            assertEquals(1, batch.size)
            assertTrue(continuation.nextBatch(queue, playback).isEmpty())

            received += batch
            queue = queue.copy(seq = queue.seq + 1, items = received.toList())
            playback = playback.copy(queueSeq = queue.seq, queueLength = queue.items.size)
            assertTrue(queue.items.size - playback.queueIndex - 1 <= 25)
            assertTrue(continuation.nextBatch(queue, playback).isEmpty())
        }

        assertEquals(all.map { it.videoId }, received.map { it.videoId })
        assertEquals(all.map { it.title }, received.map { it.title })
    }

    @Test
    fun `stale initial queue frames do not acknowledge publication`() {
        val all = playlist(40)
        val continuation = PartyPlaylistContinuation()
        val initial = continuation.start(all, index = 0, baseQueueSeq = 8)

        assertTrue(continuation.nextBatch(initial.copy(seq = 8), PartyPlayback(queueSeq = 8)).isEmpty())
        assertTrue(continuation.nextBatch(initial.copy(seq = 9), PartyPlayback(queueSeq = 8)).isEmpty())
        val acknowledged = initial.copy(seq = 9)
        assertTrue(
            continuation.nextBatch(
                acknowledged,
                PartyPlayback(queueSeq = 9, queueLength = acknowledged.items.size, queueIndex = 1),
            ).isNotEmpty(),
        )
    }

    @Test
    fun `partial batch acknowledgement keeps the unaccepted suffix`() {
        val all = playlist(60)
        val continuation = PartyPlaylistContinuation()
        var queue = continuation.start(all, index = 0, baseQueueSeq = 0).copy(seq = 1)
        var playback = PartyPlayback(queueSeq = 1, queueLength = 26, queueIndex = 3)
        val batch = continuation.nextBatch(queue, playback)
        assertEquals(all.subList(26, 29), batch)

        // Two concurrent manual additions leave room for only one of our tracks.
        queue = queue.copy(seq = 2, items = queue.items + listOf(PartyTrack("manual-1"), PartyTrack("manual-2")))
        playback = PartyPlayback(queueSeq = 2, queueLength = 28, queueIndex = 3)
        assertTrue(continuation.nextBatch(queue, playback).isEmpty())
        assertTrue(continuation.nextBatch(queue, playback).isEmpty())

        // The appended tail identifies the one accepted item; the other two remain pending.
        queue = queue.copy(seq = 3, items = queue.items + batch.take(1))
        playback = PartyPlayback(queueSeq = 3, queueLength = 29, queueIndex = 3)
        continuation.nextBatch(queue, playback)
        playback = playback.copy(queueIndex = 4)
        assertEquals(all[27], continuation.nextBatch(queue, playback).single())
    }

    @Test
    fun `history trimming preserves the playlist tail`() {
        val all = playlist(70)
        val continuation = PartyPlaylistContinuation()
        var queue = continuation.start(all, index = 6, baseQueueSeq = 0).copy(seq = 1)
        var playback = PartyPlayback(queueSeq = 1, queueLength = 32, queueIndex = 6)
        assertTrue(continuation.nextBatch(queue, playback).isEmpty())

        playback = playback.copy(queueIndex = 10)
        val firstBatch = continuation.nextBatch(queue, playback)
        assertEquals(all.subList(32, 36), firstBatch)
        queue = queue.copy(seq = 2, items = queue.items + firstBatch)
        playback = playback.copy(queueSeq = 2, queueLength = queue.items.size)
        continuation.nextBatch(queue, playback)

        queue = queue.copy(seq = 3, index = 2, items = queue.items.drop(8))
        playback = playback.copy(queueSeq = 3, queueLength = queue.items.size, queueIndex = 2)
        assertTrue(continuation.nextBatch(queue, playback).isEmpty())
        playback = playback.copy(queueIndex = 3)
        assertEquals(all[36], continuation.nextBatch(queue, playback).single())
    }

    @Test
    fun `clear or authoritative replacement stops the retained tail`() {
        val all = playlist(50)
        val continuation = PartyPlaylistContinuation()
        val initial = continuation.start(all, index = 0, baseQueueSeq = 2)
        val acknowledged = initial.copy(seq = 3)
        val playback = PartyPlayback(queueSeq = 3, queueLength = 26, queueIndex = 1)
        assertEquals(1, continuation.nextBatch(acknowledged, playback).size)

        val cleared = PartyQueue(seq = 4)
        assertTrue(continuation.nextBatch(cleared, PartyPlayback(queueSeq = 4)).isEmpty())
        assertTrue(continuation.nextBatch(acknowledged.copy(seq = 5), playback.copy(queueSeq = 5)).isEmpty())
        continuation.clear()
        assertTrue(continuation.nextBatch(acknowledged.copy(seq = 6), playback.copy(queueSeq = 6)).isEmpty())

        val replacement = PartyPlaylistContinuation()
        val original = replacement.start(all, index = 0, baseQueueSeq = 2).copy(seq = 3)
        replacement.nextBatch(original, playback.copy(queueSeq = 3))
        val oneTrackReplacement = PartyQueue(seq = 4, index = 0, items = listOf(original.items.last()))
        assertTrue(replacement.nextBatch(oneTrackReplacement, PartyPlayback(queueSeq = 4)).isEmpty())
        assertTrue(replacement.nextBatch(original.copy(seq = 5), playback.copy(queueSeq = 5)).isEmpty())
    }
}
