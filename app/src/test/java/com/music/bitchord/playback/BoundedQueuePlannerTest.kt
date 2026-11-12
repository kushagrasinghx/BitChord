package com.music.bitchord.playback

import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedQueuePlannerTest {

    @Test
    fun `a list of the physical limit fits the window whatever the start`() {
        val context = songs(BoundedQueuePlanner.MAX_PHYSICAL_CONTEXT, "context-")
        for (selected in listOf(0, MAX_QUEUE_HISTORY, context.lastIndex)) {
            val plan = BoundedQueuePlanner.buildInitialPlan(
                snapshot(context),
                selectedIndex = selected,
                queueWindow = QueueWindow(context.size).startAt(selected),
            )
            assertTrue(plan.materializedSongs().size <= BoundedQueuePlanner.MAX_PHYSICAL_CONTEXT)
        }
    }

    @Test
    fun `initial plan bounds history and keeps queue tiers`() {
        val context = songs(80, "context-")
        val window = QueueWindow(context.size).startAt(30)
        val plan = BoundedQueuePlanner.buildInitialPlan(
            snapshot(context),
            selectedIndex = 30,
            queueWindow = window,
            preservedUserQueue = listOf(song("user")),
            existingAutoplay = listOf(song("auto")),
        )

        assertEquals((5 until 30).map { "context-$it" }, plan.history.map(Song::videoId))
        assertEquals("context-30", plan.current.videoId)
        assertEquals((31..55).map { "context-$it" }, plan.contextUpcoming.map { context[it.canonicalIndex].videoId })
        assertEquals(MAX_QUEUE_HISTORY, plan.history.size)
        assertEquals(WINDOW_AHEAD, plan.contextUpcoming.size)
        assertTrue(plan.history.all { it.queueTier == QueueTier.CONTEXT })
        assertEquals(QueueTier.CONTEXT, plan.current.queueTier)
        assertEquals(QueueTier.USER_QUEUE, plan.userQueue.single().queueTier)
        assertEquals(QueueTier.AUTOPLAY, plan.autoplay.single().queueTier)
        assertEquals(MAX_QUEUE_HISTORY + 1 + 1 + WINDOW_AHEAD + 1, plan.materializedSongs().size)
    }

    @Test
    fun `top up reaches target only below low water and is idempotent`() {
        val window = QueueWindow(80).startAt(0)
        val desired = window.upcoming(WINDOW_AHEAD, repeatAll = false)
        val materialized = desired.take(LOW_WATER - 1)

        val additions = BoundedQueuePlanner.topUpEntries(window, materialized, repeatAll = false)
        assertEquals(desired.drop(LOW_WATER - 1), additions)
        assertTrue(
            BoundedQueuePlanner.topUpEntries(
                window,
                materialized + additions,
                repeatAll = false,
            ).isEmpty(),
        )
        assertTrue(
            BoundedQueuePlanner.topUpEntries(
                window,
                desired.take(LOW_WATER),
                repeatAll = false,
            ).isEmpty(),
        )
    }

    @Test
    fun `top up rejects a stale prefix using canonical index and cycle`() {
        val window = QueueWindow(4).startAt(0)
        val desired = window.upcoming(WINDOW_AHEAD, repeatAll = true)
        val wrongCycle = desired.first().copy(cycleId = desired.first().cycleId + 1)
        val wrongIndex = desired.first().copy(canonicalIndex = (desired.first().canonicalIndex + 1) % 4)

        assertTrue(BoundedQueuePlanner.topUpEntries(window, listOf(wrongCycle), true).isEmpty())
        assertTrue(BoundedQueuePlanner.topUpEntries(window, listOf(wrongIndex), true).isEmpty())
    }

    @Test
    fun `same song can be materialized again in a later repeat cycle`() {
        val context = listOf(song("same"))
        val window = QueueWindow(1).startAt(0)
        val desired = BoundedQueuePlanner.physicalUpcoming(window, repeatAll = true)
        val existing = desired.take(desired.size.coerceAtLeast(1) - 1)

        val additions = BoundedQueuePlanner.topUpEntries(window, existing, repeatAll = true)

        assertEquals(listOf(1L), desired.map { it.cycleId })
        assertEquals(desired.drop(existing.size), additions)
        assertEquals(
            List(desired.size) { "same" },
            (existing + additions).map { context[it.canonicalIndex].videoId },
        )
    }

    @Test
    fun `physical repeat lookahead includes at most one future cycle`() {
        val window = QueueWindow(3).startAt(2)

        val desired = BoundedQueuePlanner.physicalUpcoming(window, repeatAll = true)

        assertEquals(listOf(1L, 1L, 1L), desired.map { it.cycleId })
        assertEquals(listOf(0, 1, 2), desired.map { it.canonicalIndex })

        requireNotNull(window.advanceAutomatically(repeatAll = true))
        val afterRollover = BoundedQueuePlanner.physicalUpcoming(window, repeatAll = true)
        assertEquals(listOf(1L, 1L), afterRollover.map { it.cycleId })
        assertEquals(listOf(1, 2), afterRollover.map { it.canonicalIndex })
    }

    @Test
    fun `initial repeat rollover is gated by continuation state`() {
        val context = listOf(song("only"))
        val window = QueueWindow(1).startAt(0)

        listOf(ContinuationState.AVAILABLE, ContinuationState.IN_FLIGHT).forEach { state ->
            val plan = BoundedQueuePlanner.buildInitialPlan(
                snapshot(context, state),
                selectedIndex = 0,
                queueWindow = window,
                repeatAll = true,
            )
            assertTrue(plan.contextUpcoming.isEmpty())
            assertFalse(plan.contextTrulyExhausted)
            assertTrue(plan.continuationPending)
        }

        val terminal = BoundedQueuePlanner.buildInitialPlan(
            snapshot(context, ContinuationState.EXHAUSTED),
            selectedIndex = 0,
            queueWindow = QueueWindow(1).startAt(0),
            repeatAll = true,
        )
        assertTrue(terminal.contextUpcoming.isEmpty())
        assertFalse(terminal.contextTrulyExhausted)
    }

    @Test
    fun `initial repeat-all plan does not expose the next cycle before active cycle ends`() {
        val context = songs(4, "track-")
        val plan = BoundedQueuePlanner.buildInitialPlan(
            snapshot(context, ContinuationState.EXHAUSTED),
            selectedIndex = 1,
            queueWindow = QueueWindow(4).startAt(1),
            repeatAll = true,
        )

        assertEquals(listOf(2, 3), plan.contextUpcoming.map { it.canonicalIndex })
        assertTrue(plan.contextUpcoming.all { it.cycleId == 0L })
    }

    @Test
    fun `repeat-all top-up materializes next cycle when terminal context tail is empty`() {
        val window = QueueWindow(4).startAt(0)
        val activeCycle = window.upcoming(WINDOW_AHEAD, repeatAll = false)

        val additions = BoundedQueuePlanner.topUpEntries(window, activeCycle.take(1), true)

        assertEquals(activeCycle.drop(1), additions)
        assertTrue(additions.all { it.cycleId == 0L })

        window.jumpTo(3)
        // The player still has bounded history, but no CONTEXT entries after
        // the current terminal track after AutoPlay is stashed.
        val physicalContextTail = emptyList<QueueWindowEntry>()
        val nextCycle = BoundedQueuePlanner.topUpEntries(window, physicalContextTail, true)
        assertEquals(listOf(0, 1, 2, 3), nextCycle.map { it.canonicalIndex })
        assertTrue(nextCycle.all { it.cycleId == 1L })

        // Once that tail is present, the same repeat-all top-up is idempotent.
        assertTrue(BoundedQueuePlanner.topUpEntries(window, nextCycle, true).isEmpty())
    }

    @Test
    fun `autoplay waits for terminal continuation and repeat all off`() {
        val window = QueueWindow(1).startAt(0)

        assertFalse(BoundedQueuePlanner.autoplayEligible(window, ContinuationState.AVAILABLE, false))
        assertFalse(BoundedQueuePlanner.autoplayEligible(window, ContinuationState.IN_FLIGHT, false))
        assertTrue(BoundedQueuePlanner.autoplayEligible(window, ContinuationState.EXHAUSTED, false))
        assertTrue(BoundedQueuePlanner.autoplayEligible(window, ContinuationState.BLOCKED, false))
        assertFalse(BoundedQueuePlanner.autoplayEligible(window, ContinuationState.EXHAUSTED, true))
    }

    @Test
    fun `repeat gate is terminal continuation only`() {
        assertFalse(BoundedQueuePlanner.canRepeat(ContinuationState.AVAILABLE))
        assertFalse(BoundedQueuePlanner.canRepeat(ContinuationState.IN_FLIGHT))
        assertTrue(BoundedQueuePlanner.canRepeat(ContinuationState.EXHAUSTED))
        assertTrue(BoundedQueuePlanner.canRepeat(ContinuationState.BLOCKED))
    }

    @Test
    fun `canonical and shuffled windows each traverse every item once`() {
        val canonical = traverse(QueueWindow(100).startAt(0))
        assertEquals((0 until 100).toList(), canonical.map { it.canonicalIndex })
        assertEquals(List(100) { 0L }, canonical.map { it.cycleId })

        val shuffled = traverse(QueueWindow(100, shuffleSeed = 7L).startAt(0, shuffle = true))
        assertEquals(100, shuffled.size)
        assertEquals((0 until 100).toSet(), shuffled.map { it.canonicalIndex }.toSet())
        assertEquals(100, shuffled.map { it.canonicalIndex }.distinct().size)
    }

    @Test
    fun `queue refills below low water without requiring a full window`() {
        listOf(100, 1_000, 10_000).forEach { size ->
            val window = QueueWindow(size).startAt(0)
            val queue = FakeQueue(window.upcoming(WINDOW_AHEAD, repeatAll = false))
            var sawPartialLowWater = false

            while (window.advanceAutomatically(repeatAll = false) != null) {
                queue.removeFirst()
                val desired = window.upcoming(WINDOW_AHEAD, repeatAll = false)
                val additions = BoundedQueuePlanner.topUpEntries(window, queue.entries, repeatAll = false)

                if (queue.entries.size == LOW_WATER && desired.size > LOW_WATER) {
                    sawPartialLowWater = true
                }
                if (queue.entries.size >= LOW_WATER) {
                    assertTrue(additions.isEmpty())
                } else {
                    assertEquals(desired.drop(queue.entries.size), additions)
                }
                if (additions.isNotEmpty()) queue.addAll(additions)

                // At exactly low water, leaving the queue short is intentional;
                // the next transition crosses the refill threshold.
                assertEquals(desired.take(queue.entries.size), queue.entries)
                assertTrue(queue.entries.size <= WINDOW_AHEAD)
            }

            assertTrue(sawPartialLowWater)
            assertTrue(queue.entries.isEmpty())
            assertEquals((size - 1 - WINDOW_AHEAD).coerceAtLeast(0), queue.addedEntries)
            assertEquals(
                (size - 1) + queue.addedEntries,
                queue.mutationBudget,
            )
            val expectedTopUps = if (queue.addedEntries == 0) {
                0
            } else {
                (queue.addedEntries + (WINDOW_AHEAD - LOW_WATER)) /
                    (WINDOW_AHEAD - LOW_WATER + 1)
            }
            assertEquals(expectedTopUps, queue.topUpCalls)
        }
    }

    private fun traverse(window: QueueWindow): List<QueueWindowEntry> = buildList {
        add(requireNotNull(window.current))
        while (true) add(window.advanceAutomatically(false) ?: break)
    }

    private fun snapshot(
        songs: List<Song>,
        state: ContinuationState = ContinuationState.EXHAUSTED,
    ): ContextSnapshot = ContextSnapshot(
        contextToken = "context",
        sourceKey = "source",
        browseId = null,
        songs = songs,
        version = 1L,
        continuationToken = if (state == ContinuationState.AVAILABLE || state == ContinuationState.IN_FLIGHT) {
            "next"
        } else {
            null
        },
        continuationState = state,
    )

    private fun songs(count: Int, prefix: String): List<Song> =
        (0 until count).map { song("$prefix$it") }

    private fun song(id: String): Song = Song(
        videoId = id,
        title = id,
        artist = "Artist",
        thumbnailUrl = null,
    )

    private class FakeQueue(initial: List<QueueWindowEntry>) {
        val entries = initial.toMutableList()
        var addedEntries = 0
            private set
        var topUpCalls = 0
            private set
        var mutationBudget = 0
            private set

        fun removeFirst() {
            if (entries.isNotEmpty()) entries.removeAt(0)
            mutationBudget++
        }

        fun addAll(additions: List<QueueWindowEntry>) {
            entries += additions
            addedEntries += additions.size
            topUpCalls++
            mutationBudget += additions.size
        }
    }
}
