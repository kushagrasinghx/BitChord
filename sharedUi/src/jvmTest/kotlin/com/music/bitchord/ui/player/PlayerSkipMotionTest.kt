package com.music.bitchord.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-only regressions; not executed while builds are prohibited. */
class PlayerSkipMotionTest {
    @Test
    fun `survivor crosses the middle without stopping and restarting`() {
        val before = playerSkipPose(0.35f).survivorTravel
        val middle = playerSkipPose(0.36f).survivorTravel
        val after = playerSkipPose(0.37f).survivorTravel
        val incoming = middle - before
        val outgoing = after - middle

        assertTrue(incoming > 0.01f)
        assertTrue(outgoing > 0.01f)
        // A continuous deceleration must not launch a second move at the middle.
        assertTrue(outgoing <= incoming)
    }

    @Test
    fun `front retains visible size and opacity through early and middle contraction`() {
        // 36ms and 105ms of the 210ms FRONT interval in the 600ms cycle.
        val early = playerSkipPose(0.06f)
        assertTrue(early.frontScale > 0.85f)
        assertTrue(early.frontAlpha > 0.98f)
        assertTrue(early.frontScale * early.frontScale * early.frontAlpha > 0.75f)
        assertTrue(early.survivorTravel > 0f)
        val middle = playerSkipPose(0.175f)
        assertEquals(0.5f, middle.frontScale, 0.000001f)
        assertEquals(0.84375f, middle.frontAlpha, 0.000001f)
        assertTrue(middle.frontScale * middle.frontScale * middle.frontAlpha > 0.20f)
        assertEquals(0f, playerSkipPose(0.35f).frontScale, 0f)
        assertEquals(0f, playerSkipPose(0.35f).frontAlpha, 0f)
    }

    @Test
    fun `front contraction has gentler consecutive samples at supported refresh rates`() {
        // Scale-change limits distinguish the old 180ms contraction. These are
        // theoretical frame-clock samples, not measured display frame rates.
        for ((hz, firstLimit, stepLimit) in listOf(
            Triple(60, 0.020f, 0.120f),
            Triple(80, 0.011f, 0.090f),
            Triple(120, 0.005f, 0.060f),
        )) {
            var previous = playerSkipPose(0f)
            for (frame in 1..(600 * hz / 1000)) {
                val pose = playerSkipPose(frame * 1000f / hz / 600f)
                val scaleDrop = previous.frontScale - pose.frontScale
                assertTrue(scaleDrop >= 0f && scaleDrop < stepLimit)
                if (frame == 1) assertTrue(scaleDrop < firstLimit)
                assertTrue(pose.frontAlpha in 0f..previous.frontAlpha)
                assertTrue(pose.survivorTravel in previous.survivorTravel..1f)
                assertTrue(pose.newScale in previous.newScale..1f)
                assertTrue(pose.newAlpha in previous.newAlpha..1f)
                previous = pose
            }
            assertEquals(playerSkipPose(1f), previous)
        }
    }

    @Test
    fun `front finishes gently after the old cutoff without retiming the conveyor`() {
        val oldCutoff = playerSkipPose(180f / 600f)
        assertTrue(oldCutoff.frontScale > 0f)
        assertTrue(oldCutoff.frontAlpha > 0f)
        val beforeEnd = playerSkipPose(209f / 600f)
        assertTrue(beforeEnd.frontScale > 0f && beforeEnd.frontScale < 0.001f)
        assertTrue(beforeEnd.frontAlpha > 0f && beforeEnd.frontAlpha < 0.001f)
        assertEquals(0f, playerSkipPose(210f / 600f).frontScale, 0f)
        assertEquals(0f, playerSkipPose(210f / 600f).frontAlpha, 0f)
        assertEquals(0f, playerSkipPose(168f / 600f).newScale, 0f)
        assertEquals(1f, playerSkipPose(540f / 600f).survivorTravel, 0f)
        assertEquals(1f, playerSkipPose(540f / 600f).newScale, 0f)
        assertEquals(playerSkipPose(540f / 600f), playerSkipPose(600f / 600f))
    }

    @Test
    fun `replacement starts invisible and stays tiny during the single-arrow pose`() {
        val onset = playerSkipPose(0.28f)
        assertEquals(0f, onset.newScale, 0f)
        assertEquals(0f, onset.newAlpha, 0f)
        val single = playerSkipPose(0.36f)
        assertEquals(0f, single.frontScale, 0f)
        assertEquals(0f, single.frontAlpha, 0f)
        assertTrue(single.survivorTravel in 0.45f..0.55f)
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
        assertEquals(1f, start.frontAlpha, 0f)
        assertEquals(0f, start.survivorTravel, 0f)
        assertEquals(0f, start.newScale, 0f)
        assertEquals(0f, start.newAlpha, 0f)
        assertEquals(0f, end.frontScale, 0f)
        assertEquals(0f, end.frontAlpha, 0f)
        assertEquals(1f, end.survivorTravel, 0f)
        assertEquals(1f, end.newScale, 0f)
        assertEquals(1f, end.newAlpha, 0f)
        assertEquals(end, playerSkipPose(0.90f))
        assertEquals(start, playerSkipPose(-1f))
        assertEquals(end, playerSkipPose(2f))
    }

    @Test
    fun `all poses are bounded and monotonic with no spring overshoot`() {
        var previous = playerSkipPose(0f)
        for (step in 1..1000) {
            val pose = playerSkipPose(step / 1000f)
            assertTrue(pose.frontScale in 0f..previous.frontScale)
            assertTrue(pose.frontAlpha in 0f..previous.frontAlpha)
            assertTrue(pose.survivorTravel in previous.survivorTravel..1f)
            assertTrue(pose.newScale in previous.newScale..1f)
            assertTrue(pose.newAlpha in previous.newAlpha..1f)
            previous = pose
        }
    }

    @Test
    fun `survivor gives front space before continuing its uninterrupted journey`() {
        val early = playerSkipPose(0.15f).survivorTravel
        val middle = playerSkipPose(0.50f).survivorTravel
        assertTrue(early > 0f && early < 0.20f)
        assertTrue(middle > 0.65f && middle < 0.75f)
        assertEquals(1f, playerSkipPose(0.90f).survivorTravel, 0f)
    }

    @Test
    fun `rapid taps coalesce into at most the current and one pending cycle`() {
        assertEquals(1f, playerSkipTarget(cycles = 0f, target = 0f), 0f)
        var target = 1f
        for (tap in 1..1000) {
            target = playerSkipTarget(cycles = 0.4f, target = target)
            assertEquals(2f, target, 0f)
        }
        assertEquals(3f, playerSkipTarget(cycles = 1.2f, target = target), 0f)
        assertEquals(3f, playerSkipTarget(cycles = 2f, target = 2f), 0f)
    }
}
