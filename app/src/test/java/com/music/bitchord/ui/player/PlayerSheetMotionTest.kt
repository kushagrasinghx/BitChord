package com.music.bitchord.ui.player

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerSheetMotionTest {
    private class Frames(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(16)
            return onFrame(scheduler.currentTime * 1_000_000)
        }
    }

    @Test fun directOpenWaitsForLayoutAndThenMovesWithoutRaisingAgain() = runTest {
        var raised = true
        var raises = 0
        val motion = PlayerSheetMotion(
            CoroutineScope(backgroundScope.coroutineContext + Frames(testScheduler)),
            PlayerDock().apply { sheetTravel = 1000f },
            shown = { raised },
            setShown = { raised = it; raises++ },
        )
        motion.onOverlayShown()
        runCurrent()
        advanceTimeBy(100)
        assertEquals(1000f, motion.position(), 0f)
        motion.laidOut = true
        Snapshot.sendApplyNotifications()
        runCurrent()
        advanceTimeBy(120)
        runCurrent()
        assertTrue(motion.position() in 0f..999f)
        advanceTimeBy(1500)
        runCurrent()
        assertFalse(motion.holding)
        assertTrue(raised)
        assertEquals(0, raises)
    }

    @Test fun attachingHostDoesNotRestartTapAnimationOrSettledPlayer() = runTest {
        var raised = false
        var raises = 0
        val motion = PlayerSheetMotion(
            CoroutineScope(backgroundScope.coroutineContext + Frames(testScheduler)),
            PlayerDock().apply { sheetTravel = 1000f },
            shown = { raised },
            setShown = { raised = it; raises++ },
        )
        motion.open()
        motion.laidOut = true
        Snapshot.sendApplyNotifications()
        runCurrent()
        advanceTimeBy(120)
        runCurrent()
        val before = motion.position()
        assertTrue(before < 1000f)
        motion.onOverlayShown()
        runCurrent()
        assertEquals(before, motion.position(), 0f)
        advanceTimeBy(1500)
        runCurrent()
        assertFalse(motion.holding)
        motion.onOverlayShown()
        runCurrent()
        assertFalse(motion.holding)
        assertEquals(1, raises)
    }

    @Test fun retainedHostRequiresFreshLayoutAfterClosing() = runTest {
        var raised = true
        val motion = PlayerSheetMotion(
            CoroutineScope(backgroundScope.coroutineContext + Frames(testScheduler)),
            PlayerDock().apply { sheetTravel = 1000f },
            shown = { raised },
            setShown = { raised = it },
        )
        motion.onOverlayShown()
        motion.laidOut = true
        Snapshot.sendApplyNotifications()
        runCurrent()
        advanceTimeBy(1600)
        runCurrent()
        motion.onSheetGone()
        assertFalse(motion.laidOut)
        motion.onOverlayShown()
        runCurrent()
        advanceTimeBy(100)
        assertEquals(1000f, motion.position(), 0f)
        motion.laidOut = true
        Snapshot.sendApplyNotifications()
        runCurrent()
        advanceTimeBy(120)
        runCurrent()
        assertTrue(motion.position() < 1000f)
    }

    @Test fun closingBeforeFirstLayoutCancelsPendingOpening() = runTest {
        var raised = true
        val motion = PlayerSheetMotion(
            CoroutineScope(backgroundScope.coroutineContext + Frames(testScheduler)),
            PlayerDock().apply { sheetTravel = 1000f },
            shown = { raised },
            setShown = { raised = it },
        )
        motion.onOverlayShown()
        runCurrent()
        motion.close()
        runCurrent()
        advanceTimeBy(1600)
        runCurrent()
        assertFalse(raised)
        motion.onSheetGone()
        advanceTimeBy(1600)
        runCurrent()
        assertFalse(raised)
        assertFalse(motion.holding)
    }
}
