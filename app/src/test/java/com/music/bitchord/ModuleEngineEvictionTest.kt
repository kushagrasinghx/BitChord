package com.music.bitchord

import com.music.bitchord.data.sources.module.QuickJsExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which module engines the cache is allowed to close.
 *
 * Engines live in an LRU-capped pool, and closing one is closing a QuickJS
 * interpreter: if it is the interpreter a call is currently running on, the
 * result was a native crash or, worse, a different track's stream URL. The
 * victim rule is therefore "oldest idle first, busy never", and these are the
 * cases that rule exists for.
 */
class ModuleEngineEvictionTest {
    private fun victims(
        order: List<String>,
        busy: Map<String, Int>,
        excess: Int,
    ) = QuickJsExecutor.evictionVictims(
        lruOrder = order,
        inUse = { busy[it] ?: 0 },
        excess = excess,
    )

    @Test
    fun `the least recently used idle module goes first`() {
        assertEquals(listOf("a"), victims(listOf("a", "b", "c"), emptyMap(), 1))
    }

    @Test
    fun `a module serving a call is never closed`() {
        assertEquals(listOf("b"), victims(listOf("a", "b", "c"), mapOf("a" to 1), 1))
    }

    @Test
    fun `a fully busy cache closes nothing rather than the oldest thing it has`() {
        val order = listOf("a", "b", "c")

        assertEquals(emptyList<String>(), victims(order, mapOf("a" to 1, "b" to 3, "c" to 1), 1))
    }

    @Test
    fun `two slots are filled from two idle modules in LRU order`() {
        assertEquals(
            listOf("b", "c"),
            victims(listOf("a", "b", "c", "d"), mapOf("a" to 1), 2),
        )
    }

    @Test
    fun `only as many victims are returned as are idle`() {
        assertEquals(listOf("c"), victims(listOf("a", "b", "c"), mapOf("a" to 1, "b" to 2), 3))
    }

    @Test
    fun `asking for no room closes nothing`() {
        assertTrue(victims(listOf("a"), emptyMap(), 0).isEmpty())
    }
}
