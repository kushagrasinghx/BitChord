package com.music.bitchord.playback.audio.bitperfect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BitPerfectVerificationTest {

    @Test
    fun `direct USB and AAudio exclusive are independently verified`() {
        assertTrue(driverIsIndependentlyVerified(BitPerfectDriver.USB_DIRECT))
        assertTrue(driverIsIndependentlyVerified(BitPerfectDriver.AAUDIO_EXCLUSIVE))
    }

    @Test
    fun `the Android mixer path is not independently verified`() {
        assertFalse(driverIsIndependentlyVerified(BitPerfectDriver.ANDROID_MIXER))
    }
}
