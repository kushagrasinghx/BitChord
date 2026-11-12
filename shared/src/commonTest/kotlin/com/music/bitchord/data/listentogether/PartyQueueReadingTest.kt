package com.music.bitchord.data.listentogether

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where the playing track stands in a party queue that keeps its history — and so can hold that
 * track twice, once played and once now.
 */
class PartyQueueReadingTest {

    private fun queue(vararg ids: String, seq: Long = 4, index: Int = -1) =
        PartyQueue(seq = seq, index = index, items = ids.map { PartyTrack(it) })

    @Test
    fun theStateFramesIndexWinsWhenItNamesTheTrack() {
        // [x, a, x, b]: x has played before and is playing again at 2.
        val q = queue("x", "a", "x", "b", index = 0)
        val playback = PartyPlayback(queueSeq = 4, queueIndex = 2)

        assertEquals(2, partyQueueIndexOf(q, playback, "x"))
        assertEquals(listOf("b"), partyUpcomingAfter(q, playback, "x").map { it.videoId })
    }

    @Test
    fun aStaleQueueIndexIsNotTakenFromAnOlderQueue() {
        // The frame describes a newer queue than the one held, so its index is not about this list.
        val q = queue("x", "a", "x", "b", seq = 3, index = 2)
        val playback = PartyPlayback(queueSeq = 4, queueIndex = 0)

        assertEquals(2, partyQueueIndexOf(q, playback, "x"))
    }

    @Test
    fun theCopyAheadOfTheNeedleBeatsTheOneBehindIt() {
        // The party moved on to b without resending the queue; b is ahead of the last index.
        val q = queue("b", "a", "x", "b", index = 2)
        val playback = PartyPlayback(queueSeq = 3, queueIndex = 2)

        assertEquals(3, partyQueueIndexOf(q, playback, "b"))
    }

    @Test
    fun aTrackTheQueueDoesNotHoldHasNothingAfterIt() {
        val q = queue("a", "b")

        assertEquals(-1, partyQueueIndexOf(q, PartyPlayback(), "z"))
        assertEquals(emptyList(), partyUpcomingAfter(q, PartyPlayback(), "z"))
        assertEquals(-1, partyQueueIndexOf(q, PartyPlayback(), null))
    }
}
