package com.music.bitchord.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossfadeHeadroomTest {

    @Test
    fun `a single audible side needs no trim`() {
        assertEquals(1f, crossfadeHeadroom(1f, 0f), 0.0001f)
        assertEquals(1f, crossfadeHeadroom(0f, 1f), 0.0001f)
        assertEquals(1f, crossfadeHeadroom(0f, 0f), 0.0001f)
    }

    @Test
    fun `two full faders cannot sum past full scale even when correlated`() {
        val trim = crossfadeHeadroom(1f, 1f)
        assertEquals(0.5f, trim, 0.0001f)
        assertTrue((1f + 1f) * trim <= 1.0001f)
    }

    @Test
    fun `every fader pair stays within full scale for coherent signals`() {
        for (incoming in 0..10) {
            for (outgoing in 0..10) {
                val gainIn = incoming / 10f
                val gainOut = outgoing / 10f
                val peak = (gainIn + gainOut) * crossfadeHeadroom(gainIn, gainOut)
                assertTrue("pair $gainIn/$gainOut peaked at $peak", peak <= 1.0001f)
            }
        }
    }
}
