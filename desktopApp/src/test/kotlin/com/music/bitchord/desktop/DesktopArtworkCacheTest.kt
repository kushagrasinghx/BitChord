package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The artwork cache's budget, driven through the policy rather than through decoded images — Skia
 * cannot load in a test JVM, and the thing worth pinning is the eviction, not the decoding.
 *
 * What matters is that there is a budget at all: unbounded, a session's browsing stayed decoded for
 * the life of the process, which is where the memory people reported was going.
 */
class DesktopArtworkCacheTest {

    /** Sizes stand in for images: the value *is* its own byte count. */
    private fun lru(budget: Long) = DesktopByteBudgetLru<Long>(budget) { it }

    @Test
    fun theBudgetIsAFractionOfTheHeapRatherThanEverything() {
        val budget = DesktopArtworkCache.budgetBytes
        assertTrue(budget >= 32L * 1024 * 1024, "a small heap should still cache something: $budget")
        assertTrue(budget <= 128L * 1024 * 1024, "the cache should not grow without reason: $budget")
    }

    @Test
    fun holdingMoreThanTheBudgetDropsTheColdestEntries() {
        val cache = lru(10)
        repeat(20) { cache.put("art-$it", 3L) }

        assertTrue(cache.heldBytes <= 10, "held ${cache.heldBytes} against a budget of 10")
        // The newest is what a caller is about to draw, so it is the one eviction may not take.
        assertTrue(cache.contains("art-19"), "the newest entry was evicted")
        assertFalse(cache.contains("art-0"), "the coldest entry was kept")
    }

    @Test
    fun readingAnEntryKeepsItWarm() {
        val cache = lru(10)
        cache.put("a", 4L)
        cache.put("b", 4L)
        cache.get("a")
        cache.put("c", 4L)

        assertTrue(cache.contains("a"), "the entry that was just read should have survived")
        assertFalse(cache.contains("b"), "the colder entry should have gone instead")
    }

    @Test
    fun replacingAnEntryDoesNotCountItTwice() {
        val cache = lru(100)
        cache.put("a", 8L)
        cache.put("a", 2L)
        assertEquals(2L, cache.heldBytes)
    }

    @Test
    fun oneEntryLargerThanTheBudgetIsStillHanded() {
        val cache = lru(10)
        cache.put("huge", 40L)
        assertTrue(cache.contains("huge"), "the only entry was evicted, so nothing could be drawn")
    }

    @Test
    fun clearingGivesEverythingBack() {
        val cache = lru(100)
        repeat(5) { cache.put("x-$it", 10L) }
        cache.clear()
        assertEquals(0L, cache.heldBytes)
    }
}
