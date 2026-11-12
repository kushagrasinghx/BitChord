package com.music.bitchord.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueueWindowTest {

    @Test
    fun canonicalStartPinsCurrentAndKeepsCanonicalOrder() {
        val window = QueueWindow(6).startAt(2, shuffle = false)

        assertEquals(listOf(0, 1, 2, 3, 4, 5), window.order)
        assertEquals(2, window.cursor)
        assertEquals(2, window.currentCanonicalIndex)
        assertEquals(listOf(3, 4, 5), window.upcoming().map { it.canonicalIndex })
        assertEquals(listOf(3, 4, 5), window.slice(window.cursor + 1, 3).map { it.canonicalIndex })
    }

    @Test
    fun shuffleStartPinsCurrentAndIsDeterministic() {
        val first = QueueWindow(20, shuffleSeed = 1234L).startAt(7, shuffle = true)
        val second = QueueWindow(20, shuffleSeed = 1234L).startAt(7, shuffle = true)

        assertEquals(7, first.order.first())
        assertEquals(first.order, second.order)
        assertEquals((0 until 20).toSet(), first.order.toSet())
        assertEquals((0 until 20).toList(), first.order.mapIndexed { index, canonical -> first.posOf[canonical] })
    }

    @Test
    fun automaticShuffleTraversalConsumesEachTrackOnceThenStartsFreshCycle() {
        val window = QueueWindow(100, shuffleSeed = 9L).startAt(37, shuffle = true)
        val firstCycle = mutableListOf(window.currentCanonicalIndex!!)

        while (true) {
            val next = window.advanceAutomatically(repeatAll = false) ?: break
            firstCycle += next.canonicalIndex
        }

        assertEquals(100, firstCycle.size)
        assertEquals(100, firstCycle.toSet().size)
        assertEquals(0L, window.cycleId)
        assertNull(window.advanceAutomatically(repeatAll = false))

        val final = firstCycle.last()
        val nextCycle = window.advanceAutomatically(repeatAll = true)!!
        assertEquals(1L, nextCycle.cycleId)
        assertNotEquals(final, nextCycle.canonicalIndex)
        assertEquals(nextCycle, window.current)
    }

    @Test
    fun plannedBoundarySliceDoesNotAdvanceUntilCommitted() {
        val window = QueueWindow(4, shuffleSeed = 5L).startAt(0, shuffle = true)
        repeat(3) { assertTrue(window.advanceAutomatically(repeatAll = false) != null) }
        val before = window.snapshot()

        val lookAhead = window.slice(window.cursor + 1, 3, repeatAll = true)
        assertTrue(lookAhead.any { it.cycleId == 1L })
        val afterPlanning = window.snapshot()
        assertEquals(before.cursor, afterPlanning.cursor)
        assertEquals(before.currentCanonicalIndex, afterPlanning.currentCanonicalIndex)
        assertEquals(before.cycleId, afterPlanning.cycleId)
        assertEquals(before.consumedThisCycle, afterPlanning.consumedThisCycle)

        val boundary = lookAhead.first { it.cycleId == 1L }
        assertTrue(window.commit(boundary))
        assertEquals(1L, window.cycleId)
        assertEquals(boundary.canonicalIndex, window.currentCanonicalIndex)
    }

    @Test
    fun plannedBoundaryIsDeterministicAndGrowsWithTheCanonicalContext() {
        fun plannedWindow(): QueueWindow {
            val window = QueueWindow(5, shuffleSeed = 91L).startAt(0, shuffle = true)
            repeat(4) { window.advanceAutomatically() }
            window.upcoming(5, repeatAll = true)
            return window
        }

        val first = plannedWindow()
        val second = plannedWindow()
        assertEquals(first.snapshot().plannedCycles, second.snapshot().plannedCycles)
        assertEquals(first.upcoming(5, repeatAll = true), second.upcoming(5, repeatAll = true))

        first.extendTo(8)
        val plannedOrder = first.snapshot().plannedCycles.single().order
        assertEquals((0 until 8).toSet(), plannedOrder.toSet())
        assertTrue(first.upcoming(8, repeatAll = true).map { it.canonicalIndex }.containsAll((5 until 8).toList()))
    }

    @Test
    fun repeatAllOffCanonicalOrderWrapsWithoutEnablingShuffle() {
        val window = QueueWindow(3).startAt(1, shuffle = false)
        assertEquals(2, window.advanceAutomatically()!!.canonicalIndex)
        assertNull(window.advanceAutomatically(repeatAll = false))

        val wrapped = window.advanceAutomatically(repeatAll = true)!!
        assertEquals(0, wrapped.canonicalIndex)
        assertEquals(1L, wrapped.cycleId)
        assertFalse(window.isShuffleEnabled)
        assertEquals(listOf(0, 1, 2), window.order)
    }

    @Test
    fun extensionAppendsOnlyFreshCanonicalIndicesAndLeavesStableOrder() {
        val window = QueueWindow(8, shuffleSeed = 2L).startAt(3, shuffle = true)
        val oldOrder = window.order
        val oldCursor = window.cursor
        window.extendTo(13)

        assertEquals(oldOrder, window.order.take(8))
        assertEquals(oldCursor, window.cursor)
        assertEquals((0 until 13).toSet(), window.order.toSet())
        assertEquals(5, window.order.drop(8).toSet().size)
        assertTrue(window.upcoming(25).map { it.canonicalIndex }.containsAll((8 until 13).toList()))
    }

    @Test
    fun explicitNavigationMayReplayButDoesNotClearConsumedShuffleState() {
        val window = QueueWindow(6, shuffleSeed = 1L).startAt(0, shuffle = true)
        val first = window.advanceAutomatically()!!
        val second = window.advanceAutomatically()!!
        window.previous()
        assertEquals(first.canonicalIndex, window.currentCanonicalIndex)

        val upcoming = window.upcoming(25).map { it.canonicalIndex }
        assertFalse(upcoming.contains(first.canonicalIndex))
        assertFalse(upcoming.contains(second.canonicalIndex))

        // Explicit Next is deterministic and may replay the consumed item.
        assertEquals(second.canonicalIndex, window.next()!!.canonicalIndex)
    }

    @Test
    fun togglingShufflePinsCurrentAndOffRestoresCanonicalFollowingOrder() {
        val window = QueueWindow(10, shuffleSeed = 42L).startAt(4, shuffle = false)
        window.jumpTo(7)
        window.setShuffle(true)
        assertEquals(7, window.currentCanonicalIndex)
        assertEquals(7, window.order[window.cursor])

        window.setShuffle(false)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), window.order)
        assertEquals(listOf(8, 9), window.upcoming().map { it.canonicalIndex })
    }

    @Test
    fun snapshotRestoresLogicalState() {
        val original = QueueWindow(30, shuffleSeed = 17L).startAt(11, shuffle = true)
        original.advanceAutomatically()
        original.jumpTo(20)

        val restored = QueueWindow.restore(original.snapshot())
        assertEquals(original.snapshot(), restored.snapshot())
        assertEquals(original.upcoming(25), restored.upcoming(25))
        assertEquals(original.advanceAutomatically(), restored.advanceAutomatically())
    }

    @Test
    fun upcomingAndSlicesRemainBoundedForLargeContexts() {
        for (size in listOf(100, 1_000, 10_000)) {
            val window = QueueWindow(size, shuffleSeed = size.toLong()).startAt(size / 2, shuffle = true)
            assertEquals(WINDOW_AHEAD, window.upcoming(10_000).size)
            assertEquals(size - window.cursor - 1, window.slice(window.cursor + 1, 10_000).size)
            assertTrue(window.upcoming(10_000).all { it.cycleId == 0L })
        }
    }
}
