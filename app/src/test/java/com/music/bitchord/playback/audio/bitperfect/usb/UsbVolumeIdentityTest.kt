package com.music.bitchord.playback.audio.bitperfect.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsbVolumeIdentityTest {
    @Test
    fun serialIdentityIsStableAndNamespaced() {
        assertEquals("serial:DAC-123", UsbVolumeIdentity.stableKey("DAC-123"))
        assertEquals("serial:DAC-123", UsbVolumeIdentity.stableKey(" DAC-123 "))
    }

    @Test
    fun missingSerialNeverFallsBackToModelIdentity() {
        assertNull(UsbVolumeIdentity.stableKey(null))
        assertNull(UsbVolumeIdentity.stableKey("  "))
    }
}
