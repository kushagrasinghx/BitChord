package com.music.bitchord.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MixWavePrefetchTest {

    @Test
    fun `no waveform is decoded while no mix surface is open`() {
        assertFalse(shouldPrefetchMixWaves(view = null, preview = null))
    }

    @Test
    fun `open mix view requests waveforms`() {
        assertTrue(shouldPrefetchMixWaves(view = Any(), preview = null))
    }

    @Test
    fun `mix preview requests waveforms`() {
        assertTrue(shouldPrefetchMixWaves(view = null, preview = Any()))
    }
}
