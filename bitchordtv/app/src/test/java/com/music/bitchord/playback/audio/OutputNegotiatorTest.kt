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
import com.music.bitchord.data.settings.OutputPcmMode
import com.music.bitchord.playback.AudioRouting
import com.music.bitchord.playback.audio.usb.DirectUsbProbeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputNegotiatorTest {

    private val flac96kHz24BitSource = SourceDescriptor(
        encoding = "FLAC",
        sampleRateHz = 96000,
        channelCount = 2,
        bitDepth = 24,
    )

    @Test
    fun internalDspIsAlwaysFloat32RegardlessOfRouteOrOutput() {
        val result = OutputNegotiator.negotiate(
            source = flac96kHz24BitSource,
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 96000,
            channelCount = 2,
            routeKind = AudioRouting.Kind.PHONE,
            deviceName = "Built-in speaker",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT),
            advertisedSampleRates = listOf(48000),
            requestedMode = OutputPcmMode.FLOAT_32,
        )

        assertEquals("Float32", result.dsp.format)
        assertEquals(96000, result.dsp.sampleRateHz)
        assertEquals(2, result.dsp.channelCount)
        assertEquals(PcmEncoding.PCM_16BIT, result.output.encoding)
    }

    @Test
    fun phoneSpeakerStrictlyCapsOutputAt16BitWithRouteLimitation() {
        val result = OutputNegotiator.negotiate(
            source = flac96kHz24BitSource,
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 96000,
            channelCount = 2,
            routeKind = AudioRouting.Kind.PHONE,
            deviceName = "Built-in speaker",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_FLOAT),
            advertisedSampleRates = listOf(48000),
            requestedMode = OutputPcmMode.FLOAT_32,
        )

        assertEquals(PcmEncoding.PCM_16BIT, result.output.encoding)
        assertEquals(FallbackReason.ROUTE_LIMITATION, result.output.fallbackReason)
        assertNotNull(result.output.fallbackDetail)
        assertTrue(result.output.fallbackDetail!!.contains("Speaker output capped at 16-bit"))
    }

    @Test
    fun bluetoothNegotiatesFloatOnlyWhenAdvertised() {
        val btWithFloat = OutputNegotiator.negotiate(
            source = flac96kHz24BitSource,
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 96000,
            channelCount = 2,
            routeKind = AudioRouting.Kind.BLUETOOTH,
            deviceName = "Sony WH-1000XM4 (LDAC)",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_FLOAT),
            advertisedSampleRates = listOf(44100, 48000, 96000),
            requestedMode = OutputPcmMode.FLOAT_32,
        )
        assertEquals(PcmEncoding.PCM_FLOAT, btWithFloat.output.encoding)
        assertEquals(FallbackReason.NONE, btWithFloat.output.fallbackReason)

        val btPcm16Only = OutputNegotiator.negotiate(
            source = flac96kHz24BitSource,
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 96000,
            channelCount = 2,
            routeKind = AudioRouting.Kind.BLUETOOTH,
            deviceName = "SBC Headset",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT),
            advertisedSampleRates = listOf(44100, 48000),
            requestedMode = OutputPcmMode.FLOAT_32,
        )
        assertEquals(PcmEncoding.PCM_16BIT, btPcm16Only.output.encoding)
        assertEquals(FallbackReason.ROUTE_LIMITATION, btPcm16Only.output.fallbackReason)
    }

    @Test
    fun usbPcm16DacProvidesAuthoritativeDiagnosticFallbackReason() {
        val portronicsProbe = DirectUsbProbeResult(
            isViable = false,
            productName = "Portronics iKonnect C Pro",
            vendorId = 0x001F,
            productId = 0x0B21,
            uacVersion = 1,
            hasPermission = false,
            endpointOutAddress = 3,
            maxPacketSize = 384,
            altSettingCount = 1,
            isInterfaceClaimed = false,
            supportedBitDepths = listOf(16, 24, 32),
            maxCalculatedSampleRate = 96000,
            diagnosticReason = "Direct USB requires USB host permission for 'Portronics iKonnect C Pro' (managed by Android ALSA driver)",
        )

        val result = OutputNegotiator.negotiate(
            source = flac96kHz24BitSource,
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 96000,
            channelCount = 2,
            routeKind = AudioRouting.Kind.USB,
            deviceName = "Portronics iKonnect C Pro",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT),
            advertisedSampleRates = listOf(8000, 48000),
            requestedMode = OutputPcmMode.FLOAT_32,
            directUsbProbe = portronicsProbe,
            knownSystemMixerRateHz = 48000,
        )

        assertEquals(TransportType.AUDIO_TRACK, result.output.transport)
        assertEquals(PcmEncoding.PCM_16BIT, result.output.encoding)
        assertEquals(FallbackReason.DIRECT_USB_UNAVAILABLE, result.output.fallbackReason)
        assertEquals(portronicsProbe.diagnosticReason, result.output.fallbackDetail)
        assertFalse(result.isSampleRatePreserved)
    }

    @Test
    fun viableDirectUsbUsesDirectUsbTransportAndFloatEncoding() {
        val uac2Probe = DirectUsbProbeResult(
            isViable = true,
            productName = "HiFi UAC2 DAC",
            vendorId = 0x1234,
            productId = 0x5678,
            uacVersion = 2,
            hasPermission = true,
            endpointOutAddress = 1,
            maxPacketSize = 1024,
            altSettingCount = 2,
            isInterfaceClaimed = true,
            supportedBitDepths = listOf(16, 24, 32),
            maxCalculatedSampleRate = 192000,
            diagnosticReason = "UAC2 device ready for direct userspace transfer",
        )

        val result = OutputNegotiator.negotiate(
            source = flac96kHz24BitSource,
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 96000,
            channelCount = 2,
            routeKind = AudioRouting.Kind.USB,
            deviceName = "HiFi UAC2 DAC",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT),
            advertisedSampleRates = listOf(48000),
            requestedMode = OutputPcmMode.FLOAT_32,
            directUsbProbe = uac2Probe,
        )

        assertEquals(TransportType.DIRECT_USB, result.output.transport)
        assertEquals(PcmEncoding.PCM_FLOAT, result.output.encoding)
        assertEquals(FallbackReason.NONE, result.output.fallbackReason)
    }

    @Test
    fun directFloat32SelectedWhenDirectAudioSupported() {
        val directSupport = DirectAudioProbe.DirectSupport(
            isDirectSupported = true,
            isOffloadSupported = false,
            supportsFloat = true,
            supportsPcm24 = true,
            supportsPcm16 = true,
            description = "Direct PCM supported: Float32/24-bit/16-bit @ 96000 Hz",
        )

        val result = OutputNegotiator.negotiate(
            source = flac96kHz24BitSource,
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 96000,
            channelCount = 2,
            routeKind = AudioRouting.Kind.USB,
            deviceName = "Direct AudioTrack DAC",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT),
            advertisedSampleRates = listOf(96000),
            requestedMode = OutputPcmMode.FLOAT_32,
            directSupport = directSupport,
        )

        assertEquals(TransportType.AUDIO_TRACK_DIRECT, result.output.transport)
        assertEquals(PcmEncoding.PCM_FLOAT, result.output.encoding)
        assertTrue(result.output.isDirect)
        assertEquals(FallbackReason.NONE, result.output.fallbackReason)
        assertTrue(result.isSampleRatePreserved)
    }

    @Test
    fun directPcm24SelectedWhenDirectAudioExposesPcm24Only() {
        val directSupport = DirectAudioProbe.DirectSupport(
            isDirectSupported = true,
            isOffloadSupported = false,
            supportsFloat = false,
            supportsPcm24 = true,
            supportsPcm16 = true,
            description = "Direct PCM supported: 24-bit/16-bit @ 96000 Hz",
        )

        val result = OutputNegotiator.negotiate(
            source = flac96kHz24BitSource,
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 96000,
            channelCount = 2,
            routeKind = AudioRouting.Kind.USB,
            deviceName = "Direct PCM24 DAC",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT),
            advertisedSampleRates = listOf(96000),
            requestedMode = OutputPcmMode.FLOAT_32,
            directSupport = directSupport,
        )

        assertEquals(TransportType.AUDIO_TRACK_DIRECT, result.output.transport)
        assertEquals(PcmEncoding.PCM_24BIT_PACKED, result.output.encoding)
        assertTrue(result.output.isDirect)
        assertEquals(FallbackReason.ROUTE_LIMITATION, result.output.fallbackReason)
        assertTrue(result.isSampleRatePreserved)
    }

    @Test
    fun externalRouteAdvertisedPcm24SelectedWhenSourceIsHighRes() {
        val result = OutputNegotiator.negotiate(
            source = flac96kHz24BitSource,
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 96000,
            channelCount = 2,
            routeKind = AudioRouting.Kind.WIRED,
            deviceName = "Hi-Res Wired Headphone Out",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_24BIT_PACKED),
            advertisedSampleRates = listOf(96000),
            requestedMode = OutputPcmMode.FLOAT_32,
        )

        assertEquals(TransportType.AUDIO_TRACK, result.output.transport)
        assertEquals(PcmEncoding.PCM_24BIT_PACKED, result.output.encoding)
        assertEquals(FallbackReason.ROUTE_LIMITATION, result.output.fallbackReason)
    }

    @Test
    fun pcm16RequestedOnHighResolutionSourceStrictlyHonorsPcm16Preference() {
        val result = OutputNegotiator.negotiate(
            source = flac96kHz24BitSource,
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 96000,
            channelCount = 2,
            routeKind = AudioRouting.Kind.WIRED,
            deviceName = "Hi-Res Wired Headphone Out",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_24BIT_PACKED),
            advertisedSampleRates = listOf(96000),
            requestedMode = OutputPcmMode.PCM_16,
        )

        // Internal DSP MUST remain canonical Float32
        assertEquals("Float32", result.dsp.format)
        assertEquals(96000, result.dsp.sampleRateHz)
        assertEquals(2, result.dsp.channelCount)

        // AudioTrack output encoding MUST strictly honor 16-bit PCM request
        assertEquals(TransportType.AUDIO_TRACK, result.output.transport)
        assertEquals(PcmEncoding.PCM_16BIT, result.output.encoding)
        assertEquals(FallbackReason.NONE, result.output.fallbackReason)
    }

    @Test
    fun float32SelectedOnSupportedRouteProducesFloat32AudioTrack() {
        val result = OutputNegotiator.negotiate(
            source = flac96kHz24BitSource,
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 96000,
            channelCount = 2,
            routeKind = AudioRouting.Kind.USB,
            deviceName = "High-End USB DAC",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_FLOAT),
            advertisedSampleRates = listOf(96000),
            requestedMode = OutputPcmMode.FLOAT_32,
            delegateSupportsFloat = true,
        )

        assertEquals("Float32", result.dsp.format)
        assertEquals(PcmEncoding.PCM_FLOAT, result.output.encoding)
        assertEquals(FallbackReason.NONE, result.output.fallbackReason)
    }

    @Test
    fun float32SelectedOnUnsupportedRouteFallsBackToPcm16() {
        val result = OutputNegotiator.negotiate(
            source = flac96kHz24BitSource,
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 96000,
            channelCount = 2,
            routeKind = AudioRouting.Kind.USB,
            deviceName = "Basic USB Dongle",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT),
            advertisedSampleRates = listOf(48000),
            requestedMode = OutputPcmMode.FLOAT_32,
            delegateSupportsFloat = false,
        )

        assertEquals("Float32", result.dsp.format)
        assertEquals(PcmEncoding.PCM_16BIT, result.output.encoding)
        assertEquals(FallbackReason.ROUTE_LIMITATION, result.output.fallbackReason)
    }

    @Test
    fun transitionsBetweenPcm16AndFloat32KeepDspAsFloat32() {
        // Mode 1: PCM16 requested
        val pcm16Result = OutputNegotiator.negotiate(
            source = flac96kHz24BitSource,
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 96000,
            channelCount = 2,
            routeKind = AudioRouting.Kind.USB,
            deviceName = "Capable USB DAC",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_FLOAT),
            advertisedSampleRates = listOf(96000),
            requestedMode = OutputPcmMode.PCM_16,
            delegateSupportsFloat = false,
        )
        assertEquals("Float32", pcm16Result.dsp.format)
        assertEquals(PcmEncoding.PCM_16BIT, pcm16Result.output.encoding)

        // Mode 2: Switch to Float32 requested
        val floatResult = OutputNegotiator.negotiate(
            source = flac96kHz24BitSource,
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 96000,
            channelCount = 2,
            routeKind = AudioRouting.Kind.USB,
            deviceName = "Capable USB DAC",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_FLOAT),
            advertisedSampleRates = listOf(96000),
            requestedMode = OutputPcmMode.FLOAT_32,
            delegateSupportsFloat = true,
        )
        assertEquals("Float32", floatResult.dsp.format)
        assertEquals(PcmEncoding.PCM_FLOAT, floatResult.output.encoding)
    }

    @Test
    fun bluetoothTelemetryFormattingAccuratelyReportsLdacBitrate() {
        val (bitrateLabel, mode) = com.music.bitchord.playback.audio.bluetooth.BluetoothAudioTracker.parseLdacBitrate(1000L)
        assertEquals("Not exposed by Android", bitrateLabel)
        assertEquals("High Quality (990 kbps nominal)", mode)

        val telemetry = com.music.bitchord.playback.audio.bluetooth.BluetoothTelemetry(
            isConnected = true,
            deviceName = "Sony WH-1000XM5",
            codecName = "LDAC",
            sampleRateHz = 96000,
            bitDepth = 24,
            bitrateLabel = bitrateLabel,
            mode = mode,
            isAuthoritative = true,
        )

        assertEquals("LDAC / 24-bit / 96000 Hz (High Quality (990 kbps nominal))", telemetry.formattedSummary())
        assertTrue(telemetry.isHighRes)
    }
}
