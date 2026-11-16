package com.music.bitchord.desktop

import com.music.bitchord.data.model.QueueTier
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
        // The whole list stays, as on the phone: the rows above the picked one are what
        // previous steps back through.
        val queue = DesktopQueue.startingAt(songs("a", "b", "c", "d"), startIndex = 2)

        assertEquals(listOf("a", "b", "c", "d"), queue.songs.map { it.videoId })
        assertEquals("c", queue.current?.videoId)
        assertTrue(queue.hasPrevious)
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

    private fun queued(vararg ids: String) =
        ids.map { Song(it, it.uppercase(), "Artist", null, queueTier = QueueTier.USER_QUEUE) }

    private fun DesktopQueue.ids() = songs.map { it.videoId }

    @Test
    fun addToQueueEndsTheListenersOwnSectionAboveTheAlbumAndTheMix() {
        val queue = DesktopQueue(songs("cur", "ctx") + mix("m1"), index = 0)
            .enqueue(songs("x").single(), playNext = false)
            .enqueue(songs("y").single(), playNext = false)

        assertEquals(listOf("cur", "x", "y", "ctx", "m1"), queue.ids())
        assertEquals(QueueTier.USER_QUEUE, queue.songs[1].queueTier)
        assertTrue(queue.songs[1].queueEntryId != null)
    }

    @Test
    fun playNextHeadsTheListenersOwnSection() {
        val queue = DesktopQueue(songs("cur") + queued("q1") + songs("ctx"), index = 0)
            .enqueue(songs("x").single(), playNext = true)

        assertEquals(listOf("cur", "x", "q1", "ctx"), queue.ids())
    }

    @Test
    fun clearTakesOnlyWhatWasQueuedByHand() {
        val queue = DesktopQueue(songs("cur") + queued("q1", "q2") + songs("ctx") + mix("m1"), index = 0)
            .withoutUserQueue()

        assertEquals(listOf("cur", "ctx", "m1"), queue.ids())
    }

    @Test
    fun jumpingIntoTheAlbumKeepsTheTracksQueuedByHand() {
        val queue = DesktopQueue(songs("cur") + queued("q1") + songs("c1", "c2", "c3") + mix("m1"), index = 0)
            .jumpTo(3)

        // The phone's buildJumpQueue: the pick, the hand-queued track, the rest of the album, the mix.
        assertEquals(listOf("cur", "c2", "q1", "c3", "m1"), queue.ids())
        assertEquals("c2", queue.current?.videoId)
    }

    @Test
    fun jumpingIntoTheMixPromotesThePickAndKeepsTheRestOfTheMix() {
        val queue = DesktopQueue(songs("cur") + queued("q1") + mix("m1", "m2", "m3"), index = 0)
            .jumpTo(3)

        assertEquals(listOf("cur", "m2", "q1", "m3"), queue.ids())
        assertEquals(QueueTier.CONTEXT, queue.current?.queueTier)
    }

    @Test
    fun handQueuedTracksAreConsumedOncePlaybackIsBackInTheAlbum() {
        val queue = DesktopQueue(songs("a") + queued("q1") + songs("b"), index = 1).next()

        assertEquals(listOf("a", "b"), queue.ids())
        assertEquals("b", queue.current?.videoId)
    }

    @Test
    fun shuffleNeverMovesWhatTheListenerQueued() {
        val queue = DesktopQueue(songs("cur") + queued("q1", "q2") + songs("c1", "c2", "c3"), index = 0)
            .shuffledAhead()

        assertEquals(listOf("cur", "q1", "q2"), queue.ids().take(3))
        assertEquals(setOf("c1", "c2", "c3"), queue.ids().drop(3).toSet())
    }

    @Test
    fun aPartysUpcomingTracksReplaceOnlyWhatIsStillToCome() {
        val local = DesktopQueue(songs("old", "cur", "ctx1", "ctx2"), index = 1)

        val aligned = local.withPartyUpcoming(queued("p1") + mix("m1", "m2"))

        assertEquals(listOf("old", "cur", "p1", "m1", "m2"), aligned.ids())
        assertEquals(1, aligned.index)
        assertEquals(
            listOf(QueueTier.USER_QUEUE, QueueTier.AUTOPLAY, QueueTier.AUTOPLAY),
            aligned.upcoming.map { it.queueTier },
        )
        // Already in line: the same object back, so nothing redraws.
        assertTrue(aligned.withPartyUpcoming(queued("p1") + mix("m1", "m2")) === aligned)
    }

    @Test
    fun aTrackInThePartyQueueTwiceStaysTwoRows() {
        val local = DesktopQueue(songs("cur") + mix("m1"), index = 0)

        val aligned = local.withPartyUpcoming(mix("m1", "m1"))

        assertEquals(listOf("cur", "m1", "m1"), aligned.ids())
        assertTrue(aligned.songs[1] !== aligned.songs[2])
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
    @Test
    fun aCrossfadeHandoffMovesTheQueueOnSoNextIsTheTrackAfter() {
        val queue = DesktopQueue.startingAt(songs("a", "b", "c", "d"), startIndex = 0)

        val handed = queue.afterHandoffTo("b")

        assertEquals("b", handed.current?.videoId)
        assertEquals("c", handed.next().current?.videoId)
        // A callback arriving after Next already moved there leaves it alone.
        assertEquals(handed, handed.afterHandoffTo("b"))
    }

    @Test
    fun aRepeatAllHandoffWrapsToTheTop() {
        val queue = DesktopQueue.startingAt(songs("a", "b", "c"), startIndex = 2)

        assertEquals("a", queue.followingFor("c", repeatAll = true)?.videoId)
        assertEquals(null, queue.followingFor("c", repeatAll = false))
        assertEquals(0, queue.afterHandoffTo("a").index)
    }

    @Test
    fun theTrackPreparedToBlendIntoIsTheOneNextPlaysEvenWhenShuffled() {
        val queue = DesktopQueue.shuffledStartingAt(songs("a", "b", "c", "d", "e"), startIndex = 0)
            .next().next()

        val following = queue.followingFor(queue.current!!.videoId, repeatAll = false)

        assertEquals(queue.next().current?.videoId, following?.videoId)
    }
}
