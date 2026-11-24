package com.music.bitchord.ui.utils

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Regression tests for [IosOverscrollEffect], covering #1952 (first tap after
 * scrolling to the edge of a menu/list being swallowed while the bounce settles).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IosOverscrollTest {

    private class SteppingFrameClock(
        private val frameDurationMs: Long = 16L,
    ) : MonotonicFrameClock {
        private var timeNanos = 0L

        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            delay(frameDurationMs)
            timeNanos += frameDurationMs * 1_000_000L
            return onFrame(timeNanos)
        }
    }

    @Test
    fun `isInProgress stays false during pull and bounce so first tap is never swallowed`() = runTest {
        val effect = IosOverscrollEffect()
        val clock = SteppingFrameClock()

        // Dragging past the edge stretches rawPullY, yet must not arm immediate
        // touch interception on the next pointer down.
        effect.applyToScroll(
            delta = Offset(0f, -120f),
            source = NestedScrollSource.UserInput,
            performScroll = { Offset.Zero },
        )
        assertEquals(-120f, effect.currentPullY, 0.01f)
        assertFalse(effect.isInProgress)

        // Start a fling bounce and check mid-flight that isInProgress stays false.
        val job = launch(clock) {
            effect.applyToFling(Velocity(0f, -4_000f)) { Velocity.Zero }
        }
        delay(80L)
        assertTrue("bounce should still be displaced mid-animation", abs(effect.currentPullY) > 1f)
        assertFalse("isInProgress must remain false while settling", effect.isInProgress)

        job.join()
        assertEquals(0f, effect.currentPullY, 0.01f)
        assertFalse(effect.isInProgress)
    }

    @Test
    fun `applyToFling with zero velocity still settles an existing overscroll pull`() = runTest {
        val effect = IosOverscrollEffect()
        val clock = SteppingFrameClock()

        effect.applyToScroll(
            delta = Offset(0f, -150f),
            source = NestedScrollSource.UserInput,
            performScroll = { Offset.Zero },
        )
        assertEquals(-150f, effect.currentPullY, 0.01f)

        withContext(clock) {
            effect.applyToFling(Velocity.Zero) { Velocity.Zero }
        }
        assertEquals(0f, effect.currentPullY, 0.01f)
    }

    @Test
    fun `user drag mid-bounce cancels settle and pays down remaining pull`() = runTest {
        val effect = IosOverscrollEffect()
        val clock = SteppingFrameClock()

        val bounceJob = launch(clock) {
            effect.applyToFling(Velocity(0f, -4_000f)) { Velocity.Zero }
        }
        delay(64L)
        val midBouncePull = effect.currentPullY
        assertTrue("expected negative pull during upward fling bounce", midBouncePull < -10f)

        // User drags back down while the bounce is still settling: stopSettling()
        // cancels the spring and payDown() consumes the stretch first.
        var forwardedScroll = Offset.Zero
        val consumed = effect.applyToScroll(
            delta = Offset(0f, 5f),
            source = NestedScrollSource.UserInput,
            performScroll = { remaining ->
                forwardedScroll = remaining
                remaining
            },
        )
        assertEquals(Offset(0f, 5f), consumed)
        assertEquals(Offset.Zero, forwardedScroll)
        assertEquals(midBouncePull + 5f, effect.currentPullY, 0.01f)

        bounceJob.join()
        // Cancelled settle must not overwrite the caught pull.
        assertEquals(midBouncePull + 5f, effect.currentPullY, 0.01f)
    }

    @Test
    fun `fling bounce finishes cleanly to zero without lingering sub-pixel tail`() = runTest {
        val effect = IosOverscrollEffect()
        val clock = SteppingFrameClock()

        val startMs = testScheduler.currentTime
        withContext(clock) {
            effect.applyToFling(Velocity(0f, -2_500f)) { Velocity.Zero }
        }
        val elapsedMs = testScheduler.currentTime - startMs

        assertEquals(0f, effect.currentPullY, 0.0f)
        assertTrue("expected bounce to settle promptly, took ${elapsedMs}ms", elapsedMs < 600L)
    }
}
