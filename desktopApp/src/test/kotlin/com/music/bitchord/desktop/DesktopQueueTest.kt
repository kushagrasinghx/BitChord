package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopQueueTest {

    private fun songs(vararg ids: String) = ids.map { Song(it, it.uppercase(), "Artist", null) }

    private fun mix(vararg ids: String) =
        ids.map { Song(it, it.uppercase(), "Artist", null, fromAutoplay = true) }

    @Test
    fun aQueueStartsWhereTheListenerStartedIt() {
        // Rows above the picked one have not played in this session, so putting them behind the
        // current item would present them as history.
        val queue = DesktopQueue.startingAt(songs("a", "b", "c", "d"), startIndex = 2)

        assertEquals(listOf("c", "d"), queue.songs.map { it.videoId })
        assertEquals("c", queue.current?.videoId)
        assertFalse(queue.hasPrevious)
    }

    @Test
    fun aSongPlayedOnItsOwnIsAQueueOfOne() {
        val queue = DesktopQueue.of(songs("a").single())

        assertEquals(listOf("a"), queue.songs.map { it.videoId })
        assertFalse(queue.hasNext)
    }

    @Test
    fun positionIsAnIndexRatherThanASearch() {
        // The same song twice: stepping forward from the first copy must land on the second, not
        // resolve back to the first.
        val queue = DesktopQueue(songs("a", "b", "a"), index = 0).next().next()

        assertEquals(2, queue.index)
        assertEquals("a", queue.current?.videoId)
        assertTrue(queue.hasPrevious)
        assertFalse(queue.hasNext)
    }

    @Test
    fun aForwardJumpDropsWhatItSkipped() {
        // Rows between the current track and the one picked have never played.
        val queue = DesktopQueue(songs("a", "b", "c", "d", "e"), index = 0).jumpTo(3)

        assertEquals(listOf("a", "d", "e"), queue.songs.map { it.videoId })
        assertEquals("d", queue.current?.videoId)
    }

    @Test
    fun aBackwardJumpRemovesNothing() {
        val queue = DesktopQueue(songs("a", "b", "c", "d"), index = 3).jumpTo(1)

        assertEquals(listOf("a", "b", "c", "d"), queue.songs.map { it.videoId })
        assertEquals("b", queue.current?.videoId)
    }

    @Test
    fun theNextTrackIsNotAJumpAndKeepsTheOneBehindIt() {
        val queue = DesktopQueue(songs("a", "b", "c"), index = 0).next()

        assertEquals(listOf("a", "b", "c"), queue.songs.map { it.videoId })
        assertEquals(1, queue.index)
    }

    @Test
    fun historyIsBounded() {
        val long = songs(*(1..40).map { "t$it" }.toTypedArray())

        val queue = DesktopQueue(long, index = 39).trimmed()

        // 25 completed entries retained behind the current song, and the index still points at the
        // same track.
        assertEquals(DesktopQueue.MAX_HISTORY, queue.index)
        assertEquals("t40", queue.current?.videoId)
        assertEquals(DesktopQueue.MAX_HISTORY + 1, queue.songs.size)
    }

    @Test
    fun aSavedQueueComesBackAroundTheItemThatWasCurrent() {
        val long = songs(*(1..60).map { "t$it" }.toTypedArray())

        val queue = DesktopQueue.restored(long, index = 50)

        assertEquals("t51", queue.current?.videoId)
        assertEquals(DesktopQueue.MAX_HISTORY, queue.index)
    }

    @Test
    fun anOutOfRangeSavedIndexIsBroughtBackInside() {
        val queue = DesktopQueue.restored(songs("a", "b"), index = 9)

        assertEquals("b", queue.current?.videoId)
    }

    @Test
    fun theMixStartsBelowEverythingTheListenerQueued() {
        val queue = DesktopQueue(songs("a", "b") + mix("m1", "m2"), index = 0)

        assertEquals(2, queue.autoplaySectionStart)
    }

    @Test
    fun theMixSectionStartsAgainBelowTheNeedleWhileItIsPlaying() {
        // Tracks of the mix already behind you count as played, so a track queued by hand belongs
        // above what is left of it.
        val queue = DesktopQueue(songs("a") + mix("m1", "m2", "m3"), index = 2)

        assertEquals(3, queue.autoplaySectionStart)
    }

    @Test
    fun withNoMixTheSectionStartsPastTheEnd() {
        val queue = DesktopQueue(songs("a", "b"), index = 0)

        assertEquals(2, queue.autoplaySectionStart)
    }

    @Test
    fun shufflingRearrangesOnlyWhatIsStillToCome() {
        val queue = DesktopQueue(songs("a", "b", "c", "d", "e"), index = 1).shuffledAhead()

        // What has played, and what is playing, stay exactly where they were.
        assertEquals(listOf("a", "b"), queue.songs.take(2).map { it.videoId })
        assertEquals(setOf("c", "d", "e"), queue.songs.drop(2).map { it.videoId }.toSet())
        assertEquals(1, queue.index)
    }

    @Test
    fun aShuffleKeepsTheMixBelowTheListenersOwnTracks() {
        val queue = DesktopQueue(songs("cur", "a", "b") + mix("m1", "m2"), index = 0).shuffledAhead()

        val ahead = queue.songs.drop(1)
        assertEquals(setOf("a", "b"), ahead.take(2).map { it.videoId }.toSet())
        assertTrue(ahead.drop(2).all { it.fromAutoplay })
    }

    @Test
    fun turningShuffleOffPutsTheOrderBack() {
        val original = songs("cur", "a", "b", "c")
        val shuffled = DesktopQueue(listOf(original[0], original[3], original[1], original[2]), index = 0)

        val restored = shuffled.inOrderOf(original.map { it.videoId })

        assertEquals(listOf("cur", "a", "b", "c"), restored.songs.map { it.videoId })
    }

    @Test
    fun anythingQueuedAfterTheShuffleKeepsItsPlaceAtTheEnd() {
        val original = songs("cur", "a", "b")
        val withLater = DesktopQueue(listOf(original[0], original[2], original[1]) + songs("later"), index = 0)

        val restored = withLater.inOrderOf(original.map { it.videoId })

        assertEquals(listOf("cur", "a", "b", "later"), restored.songs.map { it.videoId })
    }

    /** The upcoming stretch of the queue as turning shuffle off leaves it. */
    private fun restored(upcoming: List<String>, original: List<String>): List<String> =
        DesktopQueue.restoreOrder(upcoming, original).map { upcoming[it] }

    @Test
    fun aQueueHoldingTheSameTrackTwiceKeepsBothCopies() {
        assertEquals(
            listOf("b", "b", "c"),
            restored(upcoming = listOf("b", "c", "b"), original = listOf("a", "b", "b", "c")),
        )
    }

    @Test
    fun aTrackTheOldOrderNamesButTheQueueHasLostIsSkipped() {
        assertEquals(
            listOf("b", "d"),
            restored(upcoming = listOf("d", "b"), original = listOf("a", "b", "c", "d")),
        )
    }

    /**
     * The queues this runs on are playlists, and a per-track linear search over one is quadratic —
     * the shape that made shuffling a long queue hang. Ten thousand tracks is a fraction of a second
     * here and minutes if that ever comes back.
     */
    @Test
    fun aVeryLongQueueIsRestoredWithoutAPerTrackSearch() {
        val original = (0 until 10_000).map { it.toString() }
        assertEquals(original, restored(original.shuffled(), original))
    }

    @Test
    fun aQueueStartedUnderShuffleLeadsWithThePickedTrack() {
        val queue = DesktopQueue.shuffledStartingAt(songs("a", "b", "c", "d"), startIndex = 2)

        assertEquals("c", queue.songs.first().videoId)
        assertEquals(setOf("a", "b", "c", "d"), queue.songs.map { it.videoId }.toSet())
    }

    @Test
    fun upcomingIsWhatIsStillToCome() {
        val queue = DesktopQueue(songs("a", "b", "c"), index = 1)

        assertEquals(listOf("c"), queue.upcoming.map { it.videoId })
        assertEquals(emptyList(), DesktopQueue(songs("a"), index = 0).upcoming)
    }
}
