package com.music.bitchord.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopSharedCallsTest {

    @Test
    fun `explicit refresh drops completed answers`() = runBlocking {
        val calls = DesktopSharedCalls<Int>(60_000, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        var produced = 0

        assertEquals(1, calls.get("track") { Result.success(++produced) }.getOrThrow())
        assertEquals(1, calls.get("track") { Result.success(++produced) }.getOrThrow())
        calls.clearCompleted()
        assertEquals(2, calls.get("track") { Result.success(++produced) }.getOrThrow())
    }

    @Test
    fun `completed cache is bounded`() = runBlocking {
        val calls = DesktopSharedCalls<Int>(60_000, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        repeat(300) { key -> calls.get("$key") { Result.success(key) }.getOrThrow() }

        assertTrue(calls.size() <= 128, "cache held ${calls.size()} completed calls")
    }
}
