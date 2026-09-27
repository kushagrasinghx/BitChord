/*
 * Copyright (C) 2026 BitChord Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package com.music.bitchord.playback.audio.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbDirectManagerTest {

    @Test
    fun calculateSupportedBitDepthsMatchesPacketBudgets() {
        // Under 176 bytes: cannot support even 16-bit 44.1kHz stereo
        assertEquals(emptyList<Int>(), UsbDirectManager.calculateSupportedBitDepths(100))

        // 176 - 287 bytes: supports 16-bit
        assertEquals(listOf(16), UsbDirectManager.calculateSupportedBitDepths(192))

        // 288 - 383 bytes: supports 16 and 24-bit
        assertEquals(listOf(16, 24), UsbDirectManager.calculateSupportedBitDepths(300))

        // >= 384 bytes (e.g. Portronics DAC with 384 bytes): supports 16, 24, 32
        assertEquals(listOf(16, 24, 32), UsbDirectManager.calculateSupportedBitDepths(384))
        assertEquals(listOf(16, 24, 32), UsbDirectManager.calculateSupportedBitDepths(1024))
    }

    @Test
    fun calculateMaxSampleRateFollowsEndpointPacketThresholds() {
        assertEquals(44100, UsbDirectManager.calculateMaxSampleRate(100))
        assertEquals(48000, UsbDirectManager.calculateMaxSampleRate(192))
        assertEquals(96000, UsbDirectManager.calculateMaxSampleRate(384)) // Portronics packet limit
        assertEquals(96000, UsbDirectManager.calculateMaxSampleRate(1024))
        assertEquals(192000, UsbDirectManager.calculateMaxSampleRate(1536))
    }

    @Test
    fun portronicsDacWithoutPermissionReportsAccurateAlsaFallback() {
        val (isViable, reason) = UsbDirectManager.evaluateViability(
            productName = "Portronics iKonnect C Pro",
            uacVersion = 1,
            maxPacketSize = 384,
            hasPermission = false,
        )

        assertFalse(isViable)
        assertTrue(reason.contains("requires USB host permission for 'Portronics iKonnect C Pro'"))
        assertTrue(reason.contains("managed by Android ALSA driver"))
    }

    @Test
    fun uac1DeviceWithPermissionReportsKernelDriverOwnership() {
        val (isViable, reason) = UsbDirectManager.evaluateViability(
            productName = "Portronics iKonnect C Pro",
            uacVersion = 1,
            maxPacketSize = 384,
            hasPermission = true,
        )

        assertFalse(isViable)
        assertTrue(reason.contains("UAC1 device (max packet 384 bytes) currently owned by kernel snd-usb-audio driver"))
    }

    @Test
    fun uac2DeviceWithPermissionIsViableForDirectUserspaceTransfer() {
        val (isViable, reason) = UsbDirectManager.evaluateViability(
            productName = "HiFi UAC2 DAC",
            uacVersion = 2,
            maxPacketSize = 1024,
            hasPermission = true,
        )

        assertTrue(isViable)
        assertTrue(reason.contains("UAC2 device ready for direct userspace transfer"))
    }

    @Test
    fun notPresentProbeResultProducesCleanDiagnostic() {
        val result = DirectUsbProbeResult.notPresent("No USB audio device connected")
        assertFalse(result.isViable)
        assertEquals("No USB audio device connected", result.diagnosticReason)
    }
}
