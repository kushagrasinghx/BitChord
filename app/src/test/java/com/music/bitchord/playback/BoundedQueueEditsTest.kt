package com.music.bitchord.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedQueueEditsTest {

    @Test
    fun `latest shuffle wins while refresh and top up coalesce`() {
        var state = BoundedQueueEditState()
        state = BoundedQueueEdits.requestShuffle(state, desiredShuffle = true)
        state = BoundedQueueEdits.requestRefresh(state)
        state = BoundedQueueEdits.requestTopUp(state)
        state = BoundedQueueEdits.requestShuffle(state, desiredShuffle = false)

        val settlement = BoundedQueueEdits.settle(state, settledGeneration = 0L)

        assertEquals(
            BoundedQueueEdit(
                contextGeneration = 0L,
                desiredShuffle = false,
                refresh = true,
                topUp = true,
            ),
            settlement.edit,
        )
        assertFalse(settlement.state.hasPendingEdits)
        assertTrue(settlement.drained)
    }

    @Test
    fun `unsafe transition never drains pending edits`() {
        var state = BoundedQueueEditState()
        state = BoundedQueueEdits.requestShuffle(state, desiredShuffle = true)
        state = BoundedQueueEdits.requestTopUp(state)

        val deferred = BoundedQueueEdits.defer(state)

        assertNull(deferred.edit)
        assertEquals(state, deferred.state)
        assertTrue(deferred.state.hasPendingEdits)
    }

    @Test
    fun `context invalidation clears old work and stale settlement cannot drain new work`() {
        var state = BoundedQueueEditState()
        state = BoundedQueueEdits.requestRefresh(state)
        state = BoundedQueueEdits.invalidateContext(state)
        state = BoundedQueueEdits.requestTopUp(state, contextGeneration = 1L)

        // The old callback belongs to generation zero and must not consume the
        // top-up requested by generation one.
        val stale = BoundedQueueEdits.settle(state, settledGeneration = 0L)
        assertNull(stale.edit)
        assertTrue(stale.state.hasPendingEdits)
        assertEquals(1L, stale.state.contextGeneration)

        val current = BoundedQueueEdits.settle(stale.state, settledGeneration = 1L)
        assertEquals(1L, current.edit?.contextGeneration)
        assertEquals(false, current.edit?.refresh)
        assertEquals(true, current.edit?.topUp)
        assertFalse(current.state.hasPendingEdits)
    }
}
