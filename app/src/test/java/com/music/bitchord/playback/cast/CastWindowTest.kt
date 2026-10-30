package com.music.bitchord.playback.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which of the phone's queued tracks the receiver is given to hold beside the
 * one it is playing.
 *
 * The bug these cover was invisible across four releases: casting crashed the
 * app outright, and once that was fixed, the code below it had never once run. A
 * local track in the middle of the queue left the receiver with nothing queued
 * after the playing one, so it fell silent at the end of it.
 *
 * The window is worked out over plain lists and a predicate, so it can be asked
 * here without a Cast session, a network, or a player — which is the only way a
 * queue decision gets checked at all.
 */
class CastWindowTest {

    /** What a receiver could open: anything not marked as only-on-this-phone. */
    private val isUsable: (String) -> Boolean = { !it.startsWith("local:") }

    private fun queue(vararg ids: String) = ids.toList()

    // ---- ahead ---------------------------------------------------------------

    @Test
    fun `ahead is the next track`() {
        assertEquals(listOf("b"), windowAhead(queue("a", "b", "c"), 0, ahead = 1, isUsable))
    }

    @Test
    fun `ahead skips what only this phone can play`() {
        val items = queue("a", "local:b", "local:c", "d", "e")
        assertEquals(listOf("d"), windowAhead(items, 0, ahead = 1, isUsable))
    }

    /**
     * The one that used to end the session: a track the receiver cannot open in
     * the middle of the queue must not take the slot meant for the next one, or
     * the receiver is left with nothing to hand on to.
     */
    @Test
    fun `a track only this phone can play does not empty the window`() {
        val items = queue("a", "b", "local:c", "d", "e")
        assertEquals(listOf("d"), windowAhead(items, 1, ahead = 1, isUsable))
    }

    @Test
    fun `ahead takes as many usable ones as it is asked for`() {
        val items = queue("t1", "t2", "t3", "t4", "t5")
        assertEquals(listOf("t2", "t3"), windowAhead(items, 0, ahead = 2, isUsable))
    }

    @Test
    fun `ahead of nothing usable is empty rather than short`() {
        val items = queue("a", "local:b", "local:c")
        assertEquals(emptyList<String>(), windowAhead(items, 0, ahead = 1, isUsable))
    }

    @Test
    fun `ahead of the last track is empty`() {
        assertEquals(emptyList<String>(), windowAhead(queue("a", "b"), 1, ahead = 1, isUsable))
    }

    @Test
    fun `no room ahead asked for is none given`() {
        assertEquals(emptyList<String>(), windowAhead(queue("a", "b"), 0, ahead = 0, isUsable))
    }

    // ---- back ----------------------------------------------------------------

    @Test
    fun `back is the track before, when the receiver could open it`() {
        assertEquals("a", windowBefore(queue("a", "b"), 1, isUsable))
    }

    @Test
    fun `back at the top of the queue is nothing`() {
        assertNull(windowBefore(queue("a", "b"), 0, isUsable))
    }

    @Test
    fun `back with only unusable tracks before is nothing`() {
        assertNull(windowBefore(queue("local:a", "b"), 1, isUsable))
        assertNull(windowBefore(queue("local:a", "local:b", "c"), 2, isUsable))
    }

    /**
     * The case the name promises: the immediate track before belongs to this
     * phone, and the receiver has to look past it to find one it can play. A
     * back that returned null here would fall through to the phone, which would
     * move onto the local track, be refused, and skip forward again — a no-op
     * with a "can't be cast" message.
     */
    @Test
    fun `back steps over a track the receiver cannot open`() {
        assertEquals("a", windowBefore(queue("a", "local:b", "c"), 2, isUsable))
        assertEquals("a", windowBefore(queue("a", "local:b", "local:c", "d"), 3, isUsable))
    }

    @Test
    fun `back is the nearest usable track when several are between`() {
        assertEquals("b", windowBefore(queue("a", "b", "local:c", "local:d", "e"), 4, isUsable))
    }

    @Test
    fun `previous usable index is the same decision as the window`() {
        val items = queue("a", "local:b", "c")
        assertEquals(2, previousUsableIndex(items, 3, isUsable))
        assertEquals(0, previousUsableIndex(items, 2, isUsable))
        assertNull(previousUsableIndex(items, 0, isUsable))
    }

    // ---- the two together, as the receiver sees them -------------------------

    /**
     * The window as it ends up on a receiver: the track before, this one, and
     * the one after. Written out rather than handed to a function, because the
     * point is the shape of what is queued — three of them, and never a window
     * spent on tracks the receiver cannot open.
     */
    @Test
    fun `the queue holds the track before, this one, and the one after`() {
        val items = queue("a", "b", "c", "d", "e")
        val current = 2
        val window = buildList {
            windowBefore(items, current, isUsable)?.let(::add)
            add(items[current])
            addAll(windowAhead(items, current, ahead = 1, isUsable))
        }
        assertEquals(listOf("b", "c", "d"), window)
    }

    @Test
    fun `a local track in the middle does not cost the window a track`() {
        val items = queue("a", "b", "local:c", "d", "e")
        val current = 1
        val window = buildList {
            windowBefore(items, current, isUsable)?.let(::add)
            add(items[current])
            addAll(windowAhead(items, current, ahead = 1, isUsable))
        }
        assertEquals(listOf("a", "b", "d"), window)
    }

    @Test
    fun `at the top of the queue there is nothing before`() {
        val items = queue("a", "b")
        val current = 0
        val window = buildList {
            windowBefore(items, current, isUsable)?.let(::add)
            add(items[current])
            addAll(windowAhead(items, current, ahead = 1, isUsable))
        }
        assertEquals(listOf("a", "b"), window)
    }

    @Test
    fun `at the end of the queue there is nothing after`() {
        val items = queue("a", "b")
        val current = 1
        val window = buildList {
            windowBefore(items, current, isUsable)?.let(::add)
            add(items[current])
            addAll(windowAhead(items, current, ahead = 1, isUsable))
        }
        assertEquals(listOf("a", "b"), window)
    }

    /**
     * Reordering the phone's queue moves the whole "what comes next" list, so
     * every track past the first mismatch is dropped and put back — one Cast
     * round trip each. A narrow window is what keeps that to one.
     */
    @Test
    fun `a reorder touches one track, not a window of them`() {
        val before = queue("a", "b", "c", "d")
        val after = queue("a", "c", "b", "d")
        val current = 1
        val held = windowAhead(before, current, ahead = 1, isUsable)
        val wanted = windowAhead(after, current, ahead = 1, isUsable)
        assertEquals(1, held.count { it !in wanted })
    }

    // ---- what comes next, if anything ----------------------------------------

    @Test
    fun `the next usable track is the one after this`() {
        assertEquals(1, nextUsableIndex(queue("a", "b", "c"), 0, isUsable))
    }

    @Test
    fun `unusable tracks are stepped over, not stopped at`() {
        val items = queue("a", "local:b", "c")
        assertEquals(2, nextUsableIndex(items, 0, isUsable))
    }

    @Test
    fun `nothing usable after this is null`() {
        val items = queue("a", "local:b", "local:c")
        assertNull(nextUsableIndex(items, 0, isUsable))
    }

    @Test
    fun `at the end of the queue there is nothing next`() {
        assertNull(nextUsableIndex(queue("a", "b"), 1, isUsable))
    }

    /**
     * The refusal that C6 left playing: the track before is never the answer to
     * "what can the receiver play next", or an uncastable track would send the
     * session back to the track it came from instead of ending it.
     */
    @Test
    fun `the track before is never the next one`() {
        val items = queue("a", "local:b")
        assertNull(nextUsableIndex(items, 1, isUsable))
    }
}
