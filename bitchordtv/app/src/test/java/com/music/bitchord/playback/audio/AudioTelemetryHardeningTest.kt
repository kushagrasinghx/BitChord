/*
 * Copyright (C) 2026 BitChord Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package com.music.bitchord.playback.audio

import android.media.AudioFormat
import com.music.bitchord.playback.AudioOutputStatus
import com.music.bitchord.playback.AudioRouting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTelemetryHardeningTest {

    @Test
    fun usbEndpointTelemetryDistinguishesPortronicsUac1FromAudioTrackClientFormat() {
        // Developer hardware: OnePlus 11 + Portronics iKonnect C Pro UAC1 DAC
        val portronicsSnapshot = AudioOutputStatus.Snapshot(
            deviceName = "Portronics iKonnect C Pro",
            routeKind = AudioRouting.Kind.USB,
            encodings = intArrayOf(AudioFormat.ENCODING_PCM_16BIT),
            sampleRatesHz = intArrayOf(8000, 48000),
            requestedTransportType = TransportType.AUDIO_TRACK_DIRECT,
            directSupport = DirectAudioProbe.DirectSupport.NONE,
            actualEncoding = AudioFormat.ENCODING_PCM_FLOAT,
            actualSampleRateHz = 176400,
        )

        val evaluated = AudioOutputStatus.evaluateActualPath(portronicsSnapshot)

        // Verifications:
        // 1. USB Endpoint truthfully represents physical DAC: PCM16 / 48000 Hz
        assertEquals("PCM16 / 48000 Hz", evaluated.usbEndpointFormat)
        // 2. Direct playback is rejected
        assertTrue(evaluated.directPlaybackRejected)
        assertFalse(evaluated.directPlaybackActual)
        // 3. AudioFlinger Mixer is in the path at 48000 Hz with HAL format PCM24 packed
        assertEquals(48000, evaluated.systemMixerRateHz)
        assertEquals("PCM24 packed", evaluated.halFormat)
        // 4. AudioTrack requested is Float32 / 176400 Hz (not physical Float32)
        assertEquals(AudioFormat.ENCODING_PCM_FLOAT, evaluated.actualEncoding)
        assertEquals(176400, evaluated.actualSampleRateHz)
        // 5. Fallback detail accurately explains route limitation
        assertEquals("Direct playback unavailable for active USB device", evaluated.fallbackDetail)
    }

    @Test
    fun usbEndpointTelemetryDistinguishesExternal24BitDacFromAudioTrackClientFormat() {
        // Kushagra hardware: USB device advertising PCM24 / 48000 Hz
        val dac24Snapshot = AudioOutputStatus.Snapshot(
            deviceName = "External 24-bit DAC",
            routeKind = AudioRouting.Kind.USB,
            encodings = intArrayOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_24BIT_PACKED),
            sampleRatesHz = intArrayOf(44100, 48000),
            requestedTransportType = TransportType.AUDIO_TRACK_DIRECT,
            directSupport = DirectAudioProbe.DirectSupport.NONE,
            actualEncoding = AudioFormat.ENCODING_PCM_FLOAT,
            actualSampleRateHz = 176400,
        )

        val evaluated = AudioOutputStatus.evaluateActualPath(dac24Snapshot)

        // Verifications:
        // 1. USB Endpoint truthfully represents advertised 24-bit DAC: PCM24 / 48000 Hz
        assertEquals("PCM24 / 48000 Hz", evaluated.usbEndpointFormat)
        // 2. Direct playback is rejected (AudioFlinger is still in the path)
        assertTrue(evaluated.directPlaybackRejected)
        assertFalse(evaluated.directPlaybackActual)
        // 3. AudioFlinger mixer and HAL format
        assertEquals(48000, evaluated.systemMixerRateHz)
        assertEquals("PCM24 packed", evaluated.halFormat)
        // 4. Client format remains Float32
        assertEquals(AudioFormat.ENCODING_PCM_FLOAT, evaluated.actualEncoding)
        assertEquals(176400, evaluated.actualSampleRateHz)
    }

    @Test
    fun genuineDirectPlaybackReportsBitMatchedAndNullMixer() {
        val directCapableSnapshot = AudioOutputStatus.Snapshot(
            deviceName = "Hi-Res Direct DAC",
            routeKind = AudioRouting.Kind.USB,
            encodings = intArrayOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_24BIT_PACKED),
            sampleRatesHz = intArrayOf(44100, 48000, 96000, 192000),
            requestedTransportType = TransportType.AUDIO_TRACK_DIRECT,
            directSupport = DirectAudioProbe.DirectSupport(
                isDirectSupported = true,
                isOffloadSupported = false,
                supportsFloat = false,
                supportsPcm24 = true,
                supportsPcm16 = true,
                description = "Direct 24-bit PCM supported",
            ),
            actualEncoding = AudioFormat.ENCODING_PCM_24BIT_PACKED,
            actualSampleRateHz = 192000,
        )

        val evaluated = AudioOutputStatus.evaluateActualPath(directCapableSnapshot)

        assertTrue(evaluated.directPlaybackActual)
        assertFalse(evaluated.directPlaybackRejected)
        assertEquals(TransportType.AUDIO_TRACK_DIRECT, evaluated.transportType)
        assertNull(evaluated.systemMixerRateHz)
        assertNull(evaluated.halFormat)
        assertEquals("PCM24 / 192000 Hz", evaluated.usbEndpointFormat)
    }

    @Test
    fun phoneSpeakerAlwaysSetsHalFormatAndNullUsbEndpoint() {
        val speakerSnapshot = AudioOutputStatus.Snapshot(
            deviceName = "Built-in Speaker",
            routeKind = AudioRouting.Kind.PHONE,
            encodings = intArrayOf(AudioFormat.ENCODING_PCM_16BIT),
            sampleRatesHz = intArrayOf(48000),
            actualEncoding = AudioFormat.ENCODING_PCM_16BIT,
            actualSampleRateHz = 48000,
        )

        val evaluated = AudioOutputStatus.evaluateActualPath(speakerSnapshot)

        assertFalse(evaluated.directPlaybackActual)
        assertFalse(evaluated.directPlaybackRejected)
        assertEquals(48000, evaluated.systemMixerRateHz)
        assertEquals("PCM24 packed", evaluated.halFormat)
        assertNull(evaluated.usbEndpointFormat)
    }

    @Test
    fun staticUsbCapabilityAndActiveAudioTrackRemainSeparate() {
        // Moondrop CHU II DSP: Static USB descriptors advertise PCM24 / 96000 Hz max,
        // but Android runtime direct support confirms PCM16 @ 176.4 kHz direct AudioTrack.
        val chu2Snapshot = AudioOutputStatus.Snapshot(
            deviceName = "Moondrop CHU II DSP",
            routeKind = AudioRouting.Kind.USB,
            encodings = intArrayOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_24BIT_PACKED),
            sampleRatesHz = intArrayOf(44100, 48000, 88200, 96000), // static USB capability
            requestedTransportType = TransportType.AUDIO_TRACK_DIRECT,
            directPlaybackSelected = true,
            directSupport = DirectAudioProbe.DirectSupport(
                isDirectSupported = true,
                isOffloadSupported = false,
                supportsFloat = false,
                supportsPcm24 = false,
                supportsPcm16 = true,
                description = "Direct PCM supported: 16-bit @ 176400 Hz",
            ),
            actualEncoding = AudioFormat.ENCODING_PCM_16BIT,
            actualSampleRateHz = 176400,
        )

        val evaluated = AudioOutputStatus.evaluateActualPath(chu2Snapshot)

        // 1. Static capability truthfully reflects static descriptor: PCM24 / 96000 Hz
        assertEquals("PCM24 / 96000 Hz", evaluated.usbEndpointFormat)
        // 2. Active AudioTrack is 176.4 kHz PCM16
        assertEquals(176400, evaluated.actualSampleRateHz)
        assertEquals(AudioFormat.ENCODING_PCM_16BIT, evaluated.actualEncoding)
        // 3. Direct playback is ACTIVE and bypassing mixer (not capped at 96 kHz)
        assertTrue("Direct playback must be active at 176.4 kHz", evaluated.directPlaybackActual)
        assertFalse("Direct playback must not be rejected", evaluated.directPlaybackRejected)
        assertNull("System mixer must be bypassed", evaluated.systemMixerRateHz)
    }

    @Test
    fun unknownSampleRateDoesNotRejectDirectPlaybackOnWiredRoute() {
        // Between a route change and the first AudioTrack publish there is no
        // measured rate yet. "Not measured" must not read as "unsupported".
        val pending = AudioOutputStatus.Snapshot(
            deviceName = "Hi-Res Wired DAC",
            routeKind = AudioRouting.Kind.WIRED,
            sampleRatesHz = intArrayOf(44100, 48000, 96000),
            requestedTransportType = TransportType.AUDIO_TRACK_DIRECT,
            directSupport = DirectAudioProbe.DirectSupport(
                isDirectSupported = true,
                isOffloadSupported = false,
                supportsFloat = false,
                supportsPcm24 = true,
                supportsPcm16 = true,
                description = "Direct PCM supported: 24-bit @ 96000 Hz",
            ),
            actualSampleRateHz = null,
        )

        val evaluated = AudioOutputStatus.evaluateActualPath(pending)

        assertTrue("Direct playback must survive an unmeasured rate", evaluated.directPlaybackActual)
        assertFalse("Direct playback must not be rejected", evaluated.directPlaybackRejected)
    }

    @Test
    fun usbDeviceCapabilityFormattingIsHumanReadable() {
        assertEquals(
            "PCM 24-bit / 96 kHz",
            com.music.bitchord.ui.components.formatUsbCapability("PCM24 / 96000 Hz"),
        )
        assertEquals(
            "PCM 16-bit / 48 kHz",
            com.music.bitchord.ui.components.formatUsbCapability("PCM16 / 48000 Hz"),
        )
        assertEquals(
            "PCM 24-bit / 192 kHz",
            com.music.bitchord.ui.components.formatUsbCapability("PCM24 / 192000 Hz"),
        )
        assertEquals(
            "Float 32-bit / 176.4 kHz",
            com.music.bitchord.ui.components.formatUsbCapability("Float32 / 176400 Hz"),
        )
        assertEquals(
            "PCM 24-bit / 44.1 kHz",
            com.music.bitchord.ui.components.formatUsbCapability("PCM24 / 44100 Hz"),
        )
    }
}
