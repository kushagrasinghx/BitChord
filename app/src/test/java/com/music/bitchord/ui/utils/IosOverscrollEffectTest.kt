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

    @Test fun reverseFlingUnwindsAnEdgePullWithoutSpendingItsWholeTravelOnTheBounce() {
        val effect = IosOverscrollEffect()
        effect.applyToScroll(Offset(0f, 120f), NestedScrollSource.UserInput) { Offset.Zero }
        var delivered = Offset.Zero
        val consumed = effect.applyToScroll(Offset(0f, -80f), NestedScrollSource.SideEffect) {
            delivered = it
            it
        }
        // A fling spends 30px unwinding a 120px pull, then the remaining
        // 50px scrolls the list in this same frame. Touch keeps 1:1 behavior.
        assertEquals(Offset(0f, -50f), delivered)
        assertEquals(Offset(0f, -80f), consumed)
        assertFalse(effect.isInProgress)
    }

    @Test fun reversingATouchPullStillUnwindsOneToOne() {
        val effect = IosOverscrollEffect()
        effect.applyToScroll(Offset(0f, 120f), NestedScrollSource.UserInput) { Offset.Zero }
        var delivered = Offset.Zero
        val first = effect.applyToScroll(Offset(0f, -80f), NestedScrollSource.UserInput) {
            delivered = it
            it
        }
        assertEquals(Offset.Zero, delivered)
        assertEquals(Offset(0f, -80f), first)
        assertTrue(effect.isInProgress)
        val second = effect.applyToScroll(Offset(0f, -80f), NestedScrollSource.UserInput) {
            delivered = it
            it
        }
        assertEquals(Offset(0f, -40f), delivered)
        assertEquals(Offset(0f, -80f), second)
        assertFalse(effect.isInProgress)
    }

    @Test fun subpixelListRemaindersDoNotAccumulatePhantomEdgeTension() {
        val effect = IosOverscrollEffect()
        repeat(8) {
            effect.applyToScroll(Offset(0f, -10f), NestedScrollSource.UserInput) {
                Offset(0f, -9.875f)
            }
        }
        assertFalse("Rounding residue must not turn interior scrolling into an edge pull", effect.isInProgress)
        var delivered = Offset.Zero
        effect.applyToScroll(Offset(0f, 20f), NestedScrollSource.UserInput) {
            delivered = it
            it
        }
        assertEquals(Offset(0f, 20f), delivered)
    }

    @Test fun smallDeltasStillMoveAnEstablishedTouchPullContinuously() {
        val effect = IosOverscrollEffect()
        effect.applyToScroll(Offset(0f, 1f), NestedScrollSource.UserInput) { Offset.Zero }
        repeat(8) {
            effect.applyToScroll(Offset(0f, .125f), NestedScrollSource.UserInput) { Offset.Zero }
        }
        var delivered = Offset.Zero
        effect.applyToScroll(Offset(0f, -2f), NestedScrollSource.UserInput) {
            delivered = it
            it
        }
        assertEquals(Offset.Zero, delivered)
        assertFalse(effect.isInProgress)
    }

    @Test fun flingUnwindingAccountsForEachAxisAndPreservesUnconsumedListMovement() {
        val effect = IosOverscrollEffect()
        effect.applyToScroll(Offset(-120f, -80f), NestedScrollSource.UserInput) { Offset.Zero }
        var delivered = Offset.Zero
        val first = effect.applyToScroll(Offset(20f, 40f), NestedScrollSource.SideEffect) {
            delivered = it
            // The list can consume only half its available vertical movement.
            Offset(0f, 10f)
        }
        assertEquals(Offset(0f, 20f), delivered)
        assertEquals(Offset(20f, 30f), first)
        assertTrue(effect.isInProgress) // 40px of horizontal pull remains.
        val second = effect.applyToScroll(Offset(20f, 0f), NestedScrollSource.SideEffect) {
            delivered = it
            it
        }
        assertEquals(Offset(10f, 0f), delivered)
        assertEquals(Offset(20f, 0f), second)
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
