package com.music.bitchord

import com.music.bitchord.ui.player.edgeScrollSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How fast the queue list carries itself along under a row held at one of its
 * edges, so a track can be dragged across a queue longer than the screen.
 */
class QueueEdgeScrollTest {

    /** A 600px viewport, a 100px row, and a 50px zone at each end of it. */
    private fun speed(top: Float, viewportStart: Int = 0, viewportEnd: Int = 600) =
        edgeScrollSpeed(
            top = top,
            bottom = top + 100f,
            viewportStart = viewportStart,
            viewportEnd = viewportEnd,
            zone = 50f,
            speed = 1000f,
        )

    @Test
    fun `a row in the middle of the list scrolls it neither way`() {
        assertEquals(0f, speed(top = 250f), 0f)
    }

    @Test
    fun `a row clear of the zone by a single pixel still scrolls nothing`() {
        assertEquals(0f, speed(top = 51f), 0f)
        assertEquals(0f, speed(top = 449f), 0f)
    }

    @Test
    fun `reaching the top of the list scrolls back towards the start`() {
        assertTrue(speed(top = 40f) < 0f)
    }

    @Test
    fun `reaching the bottom scrolls on towards the end`() {
        assertTrue(speed(top = 460f) > 0f)
    }

    @Test
    fun `the speed at the very edge is the full speed asked for`() {
        // The row's top on the viewport's: the whole 50px zone is covered.
        assertEquals(-1000f, speed(top = 0f), 0.01f)
        // And its bottom on the viewport's, 500px down.
        assertEquals(1000f, speed(top = 500f), 0.01f)
    }

    @Test
    fun `held past the edge it goes no faster than at it`() {
        assertEquals(-1000f, speed(top = -400f), 0.01f)
        assertEquals(1000f, speed(top = 900f), 0.01f)
    }

    @Test
    fun `a row barely inside the zone already moves the list`() {
        // A fifth of the top speed at the near edge of the zone rather than
        // nothing: reaching it should read as a response, not as a stall.
        val barely = speed(top = 49.5f)
        assertTrue("$barely", barely <= -200f && barely > -250f)
    }

    @Test
    fun `the ramp is even across the zone`() {
        // Halfway in: the fifth it starts from, plus half of the rest.
        assertEquals(-600f, speed(top = 25f), 0.01f)
        assertEquals(600f, speed(top = 475f), 0.01f)
    }

    @Test
    fun `the zone is measured from the viewport, not from zero`() {
        // Content padding puts the viewport's start above the first row's own
        // offset. This row sits 150px clear of it, so it shouldn't scroll —
        // which measuring from 0 would have it doing at full speed.
        assertEquals(0f, speed(top = 0f, viewportStart = -200, viewportEnd = 400), 0f)
        // The same viewport, with the row now at the top edge of it.
        assertEquals(-1000f, speed(top = -200f, viewportStart = -200, viewportEnd = 400), 0.01f)
    }

    @Test
    fun `a viewport too short to clear both edges scrolls neither way`() {
        // A 100px row and 50px zones in 120px of viewport: it is in both zones
        // wherever it sits, and running away in whichever won the tie would be
        // worse than staying put.
        assertEquals(0f, speed(top = 10f, viewportStart = 0, viewportEnd = 120), 0f)
    }

    @Test
    fun `no zone is no auto-scroll`() {
        // Before the list is measured there is no density to size a zone from.
        assertEquals(
            0f,
            edgeScrollSpeed(
                top = 0f,
                bottom = 100f,
                viewportStart = 0,
                viewportEnd = 600,
                zone = 0f,
                speed = 1000f,
            ),
            0f,
        )
    }
}
