package com.music.bitchord.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-only regressions; not executed while builds are prohibited. */
class PlayerSkipMotionTest {
    @Test
    fun `survivor crosses the middle without stopping and restarting`() {
        val before = playerSkipPose(0.29f).survivorTravel
        val middle = playerSkipPose(0.30f).survivorTravel
        val after = playerSkipPose(0.31f).survivorTravel
        val incoming = middle - before
        val outgoing = after - middle

        assertTrue(incoming > 0.01f)
        assertTrue(outgoing > 0.01f)
        // A continuous deceleration must not launch a second move at the middle.
        assertTrue(outgoing <= incoming)
    }

    @Test
    fun `front visibly contracts before disappearing without shrinking the survivor`() {
        val early = playerSkipPose(0.10f)
        assertEquals(0.25f, early.frontScale, 0.000001f)
        assertTrue(early.survivorTravel > 0f)
        assertEquals(0f, playerSkipPose(0.20f).frontScale, 0f)
    }

    @Test
    fun `replacement starts invisible and stays tiny during the single-arrow pose`() {
        val onset = playerSkipPose(0.28f)
        assertEquals(0f, onset.newScale, 0f)
        assertEquals(0f, onset.newAlpha, 0f)
        val single = playerSkipPose(0.33f)
        assertEquals(0f, single.frontScale, 0f)
        assertTrue(single.survivorTravel in 0.5f..0.7f)
        assertTrue(single.newScale > 0f && single.newScale < 0.05f)
        assertTrue(single.newAlpha > 0f && single.newAlpha < 0.05f)
    }

    @Test
    fun `replacement grows while survivor continues advancing`() {
        val middle = playerSkipPose(0.50f)
        val late = playerSkipPose(0.67f)
        assertTrue(middle.newScale > 0f && middle.newScale < late.newScale)
        assertTrue(middle.newAlpha > 0f && middle.newAlpha < late.newAlpha)
        assertTrue(middle.survivorTravel < late.survivorTravel)
        assertTrue(late.survivorTravel < 1f)
    }

    @Test
    fun `cycle exchanges identities into exactly the original slots`() {
        val start = playerSkipPose(0f)
        val end = playerSkipPose(1f)
        assertEquals(1f, start.frontScale, 0f)
        assertEquals(0f, start.survivorTravel, 0f)
        assertEquals(0f, start.newScale, 0f)
        assertEquals(0f, start.newAlpha, 0f)
        assertEquals(0f, end.frontScale, 0f)
        assertEquals(1f, end.survivorTravel, 0f)
        assertEquals(1f, end.newScale, 0f)
        assertEquals(1f, end.newAlpha, 0f)
        assertEquals(end, playerSkipPose(0.85f))
        assertEquals(start, playerSkipPose(-1f))
        assertEquals(end, playerSkipPose(2f))
    }

    @Test
    fun `all poses are bounded and monotonic with no spring overshoot`() {
        var previous = playerSkipPose(0f)
        for (step in 1..1000) {
            val pose = playerSkipPose(step / 1000f)
            assertTrue(pose.frontScale in 0f..previous.frontScale)
            assertTrue(pose.survivorTravel in previous.survivorTravel..1f)
            assertTrue(pose.newScale in previous.newScale..1f)
            assertTrue(pose.newAlpha in previous.newAlpha..1f)
            previous = pose
        }
    }
}
