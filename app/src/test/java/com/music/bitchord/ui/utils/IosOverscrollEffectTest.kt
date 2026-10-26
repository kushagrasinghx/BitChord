package com.music.bitchord.ui.utils

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IosOverscrollEffectTest {
    private class Frames(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(16)
            return onFrame(scheduler.currentTime * 1_000_000)
        }
    }

    @Test fun newDragStopsTheOldBounceInsteadOfFightingTheFinger() = runTest {
        val effect = IosOverscrollEffect()
        effect.applyToScroll(Offset(0f, 120f), NestedScrollSource.UserInput) { Offset.Zero }
        val bounce = launch(Frames(testScheduler)) {
            effect.applyToFling(Velocity.Zero) { Velocity.Zero }
        }
        runCurrent()
        advanceTimeBy(80)
        runCurrent()
        assertTrue(bounce.isActive)

        effect.applyToScroll(Offset(0f, 20f), NestedScrollSource.UserInput) { Offset.Zero }
        runCurrent()
        assertTrue("The previous spring must release control to the new drag", bounce.isCompleted)

        // Read the remaining pull by reversing beyond it. All movement left
        // after paying down the pull goes to the list, which consumes it here.
        var afterPull = Offset.Zero
        effect.applyToScroll(Offset(0f, -10_000f), NestedScrollSource.UserInput) {
            afterPull = it
            it
        }
        assertTrue(afterPull.y > -10_000f)
        assertFalse(effect.isInProgress)
        advanceTimeBy(1000)
        runCurrent()
        assertFalse("An old spring must not restore the cleared pull", effect.isInProgress)
    }

    @Test fun ordinaryPageScrollAndFlingPassThroughWithoutResistance() = runTest {
        val effect = IosOverscrollEffect()
        val drag = Offset(0f, -47.25f)
        var delivered = Offset.Zero
        val consumed = effect.applyToScroll(drag, NestedScrollSource.UserInput) {
            delivered = it
            it
        }
        assertEquals(drag, delivered)
        assertEquals(drag, consumed)
        var flingVelocity = Velocity.Zero
        effect.applyToFling(Velocity(0f, -5000f)) {
            flingVelocity = it
            it
        }
        assertEquals(Velocity(0f, -5000f), flingVelocity)
        assertFalse(effect.isInProgress)
    }

    @Test fun oldFlingCannotStartABounceAfterANewDragHasTakenOver() = runTest {
        val effect = IosOverscrollEffect()
        val finishFling = CompletableDeferred<Unit>()
        val oldFling = launch(Frames(testScheduler)) {
            effect.applyToFling(Velocity(0f, 3000f)) {
                finishFling.await()
                Velocity.Zero
            }
        }
        runCurrent()
        effect.applyToScroll(Offset(0f, -30f), NestedScrollSource.UserInput) { it }
        finishFling.complete(Unit)
        runCurrent()
        advanceTimeBy(80)
        runCurrent()
        assertTrue(oldFling.isCompleted)
        assertFalse(effect.isInProgress)
    }

    @Test fun edgeFlingReturnsToRest() = runTest {
        val effect = IosOverscrollEffect()
        val bounce = launch(Frames(testScheduler)) {
            effect.applyToFling(Velocity(0f, 3000f)) { Velocity.Zero }
        }
        runCurrent()
        advanceTimeBy(80)
        runCurrent()
        assertTrue(effect.isInProgress)
        advanceTimeBy(3000)
        runCurrent()
        assertTrue(bounce.isCompleted)
        assertFalse(effect.isInProgress)
    }
}
