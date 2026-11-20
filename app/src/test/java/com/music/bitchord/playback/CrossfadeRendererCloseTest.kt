package com.music.bitchord.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CrossfadeRendererCloseTest {

    @Test
    fun `renderer closes after the owning service scope is cancelled`() = runTest {
        var closes = 0
        val serviceScope = TestScope(testScheduler)
        closeLoopRendererLater(serviceScope, 1_500L, AutoCloseable { closes++ })

        serviceScope.cancel()
        advanceTimeBy(2_000L)
        runCurrent()

        assertEquals(1, closes)
    }

    @Test
    fun `renderer is closed exactly once per scheduled close`() = runTest {
        var closes = 0
        closeLoopRendererLater(TestScope(testScheduler), 1_500L, AutoCloseable { closes++ })
        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(1, closes)
    }
}
