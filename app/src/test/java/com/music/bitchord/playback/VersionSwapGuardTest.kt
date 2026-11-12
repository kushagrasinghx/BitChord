package com.music.bitchord.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionSwapGuardTest {

    @Test
    fun `local playback may swap a version`() {
        assertFalse(shouldDeferVersionSwap(remoteSpeakerActive = false))
    }

    @Test
    fun `a Cast or watch speaker keeps its current cut`() {
        assertTrue(shouldDeferVersionSwap(remoteSpeakerActive = true))
    }
}
