package com.music.bitchord

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.MonotonicFrameClock
import com.music.bitchord.ui.player.PlayerDock
import com.music.bitchord.ui.player.PlayerSheetMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
class PlayerSheetMotionTest {
    private class Player(scope: TestScope) {
        var shown = false
        var dismissals = 0
        val dock = PlayerDock().apply { sheetTravel = 1_000f }
        private val clock = object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                delay(16)
                return onFrame(scope.testScheduler.currentTime * 1_000_000)
            }
        }
        val motion = PlayerSheetMotion(
            scope = CoroutineScope(scope.coroutineContext + clock),
            dock = dock,
            shown = { shown },
            setShown = {
                shown = it
                if (!it) dismissals++
            },
        ).apply { laidOut = true }

        fun startClosing() {
            // Hold a fully raised player, as at the end of an upward pull.
            motion.drag(-dock.sheetTravel)
            motion.close()
        }
    }

    @Test
    fun `a fresh open still starts from the mini player`() = runTest {
        val player = Player(this)

        player.motion.open()

        assertTrue(player.shown)
        assertEquals(player.dock.sheetTravel, player.motion.position(), 0f)
        advanceUntilIdle()
        assertTrue(player.shown)
        assertFalse(player.motion.holding)
    }

    @Test
    fun `reopening reverses from the current position without dismissing`() = runTest {
        val player = Player(this)
        player.startClosing()
        advanceTimeBy(160)
        runCurrent()
        val closingAt = player.motion.position()
        assertTrue(closingAt > 0f && closingAt < player.dock.sheetTravel)

        player.motion.open()

        assertEquals(closingAt, player.motion.position(), 0f)
        assertFalse(player.motion.closing)
        advanceTimeBy(80)
        runCurrent()
        assertTrue(player.motion.position() < closingAt)
        advanceUntilIdle()
        assertTrue(player.shown)
        assertFalse(player.motion.holding)
        assertEquals(0, player.dismissals)
    }

    @Test
    fun `repeated open requests do not restart a reversal`() = runTest {
        val player = Player(this)
        player.startClosing()
        advanceTimeBy(160)
        player.motion.open()
        advanceTimeBy(64)
        runCurrent()
        val openingAt = player.motion.position()

        repeat(3) { player.motion.open() }

        assertEquals(openingAt, player.motion.position(), 0f)
        advanceUntilIdle()
        assertTrue(player.shown)
        assertEquals(0, player.dismissals)
    }

    @Test
    fun `a close can interrupt the reversal and still dismiss once`() = runTest {
        val player = Player(this)
        player.startClosing()
        advanceTimeBy(160)
        player.motion.open()
        advanceTimeBy(64)
        runCurrent()

        player.motion.close()
        player.motion.close()
        advanceUntilIdle()

        assertFalse(player.shown)
        assertEquals(1, player.dismissals)
    }

    @Test
    fun `a pull released toward the mini player can also be reopened`() = runTest {
        val player = Player(this)
        player.motion.drag(-100f)
        // Decide by distance, independently of the wall-clock gesture speed.
        player.motion.flingVelocity = Float.POSITIVE_INFINITY
        player.motion.release(0f)
        assertTrue(player.motion.closing)

        player.motion.open()

        assertEquals(900f, player.motion.position(), 0f)
        advanceUntilIdle()
        assertTrue(player.shown)
        assertFalse(player.motion.closing)
        assertEquals(0, player.dismissals)
    }

    @Test
    fun `an uninterrupted close dismisses normally`() = runTest {
        val player = Player(this)
        player.startClosing()
        advanceUntilIdle()

        assertFalse(player.shown)
        assertEquals(1, player.dismissals)
    }

    @Test
    fun `sheet disposal cancels a pending reversal`() = runTest {
        val player = Player(this)
        player.startClosing()
        advanceTimeBy(160)
        player.motion.open()

        player.motion.onSheetGone()
        advanceUntilIdle()

        assertFalse(player.motion.holding)
        assertFalse(player.motion.closing)
        assertFalse(player.motion.laidOut)
        assertEquals(0, player.dismissals)
    }
}
