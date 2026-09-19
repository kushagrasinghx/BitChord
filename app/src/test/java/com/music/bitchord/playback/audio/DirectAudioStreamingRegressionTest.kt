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
import com.music.bitchord.playback.AudioOutputStatus
import com.music.bitchord.playback.AudioRouting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for Direct AudioTrack negotiation across sample rates and sources.
 *
 * Verifies that the decision to use Direct AudioTrack vs fallback to AudioFlinger mixer
 * is driven strictly by route capabilities, Android runtime support, and user preference,
 * never by whether the source is local or streaming.
 */
class DirectAudioStreamingRegressionTest {

    private fun flacSource(sampleRate: Int, bitDepth: Int = 24) = SourceDescriptor(
        encoding = "FLAC",
        sampleRateHz = sampleRate,
        channelCount = 2,
        bitDepth = bitDepth,
    )

    private fun streamingSource(sampleRate: Int, bitDepth: Int = 24) = SourceDescriptor(
        encoding = "audio/flac",
        sampleRateHz = sampleRate,
        channelCount = 2,
        bitDepth = bitDepth,
    )

    // 1. Streaming 88.2 kHz direct-capable route
    @Test
    fun streaming88200HzDirectCapableRoute() {
        val directSupport = DirectAudioProbe.DirectSupport(
            isDirectSupported = true,
            isOffloadSupported = false,
            supportsFloat = false,
            supportsPcm24 = true,
            supportsPcm16 = true,
            description = "Direct PCM supported: 24-bit/16-bit @ 88200 Hz",
        )

        val result = OutputNegotiator.negotiate(
            source = streamingSource(88200),
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 88200,
            channelCount = 2,
            routeKind = AudioRouting.Kind.USB,
            deviceName = "Chu2 DSP",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_24BIT_PACKED),
            advertisedSampleRates = listOf(44100, 48000, 88200, 96000),
            requestedMode = OutputPcmMode.FLOAT_32,
            directSupport = directSupport,
        )

        assertEquals(TransportType.AUDIO_TRACK_DIRECT, result.output.transport)
        assertTrue(result.output.isDirect)
        assertEquals(PcmEncoding.PCM_24BIT_PACKED, result.output.encoding)
        assertNull(result.output.systemMixerRateHz)
        assertEquals(FallbackReason.ROUTE_LIMITATION, result.output.fallbackReason)
        assertEquals("Float32", result.dsp.format)
    }

    // 2. Streaming 176.4 kHz direct-capable route (route exposes Direct PCM16)
    @Test
    fun streaming176400HzDirectCapableRouteFallsToDirectPcm16WhenPcm24Unavailable() {
        val directSupportPcm16Only = DirectAudioProbe.DirectSupport(
            isDirectSupported = true,
            isOffloadSupported = false,
            supportsFloat = false,
            supportsPcm24 = false,
            supportsPcm16 = true,
            description = "Direct PCM supported: 16-bit @ 176400 Hz",
        )

        val result = OutputNegotiator.negotiate(
            source = streamingSource(176400, bitDepth = 24),
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 176400,
            channelCount = 2,
            routeKind = AudioRouting.Kind.USB,
            deviceName = "Chu2 DSP",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_24BIT_PACKED),
            advertisedSampleRates = listOf(44100, 48000, 88200, 96000),
            requestedMode = OutputPcmMode.FLOAT_32,
            directSupport = directSupportPcm16Only,
        )

        // Must maintain direct output bypass instead of falling to AudioFlinger mixer
        assertEquals(TransportType.AUDIO_TRACK_DIRECT, result.output.transport)
        assertTrue(result.output.isDirect)
        assertEquals(PcmEncoding.PCM_16BIT, result.output.encoding)
        assertNull(result.output.systemMixerRateHz)
        assertEquals(FallbackReason.ROUTE_LIMITATION, result.output.fallbackReason)
        assertEquals("Route does not expose direct Float32 or 24-bit PCM (using direct 16-bit PCM)", result.output.fallbackDetail)
        assertEquals("Float32", result.dsp.format)
    }

    // 3. Streaming 192 kHz direct-capable route
    @Test
    fun streaming192000HzDirectCapableRoute() {
        val directSupport192 = DirectAudioProbe.DirectSupport(
            isDirectSupported = true,
            isOffloadSupported = false,
            supportsFloat = false,
            supportsPcm24 = true,
            supportsPcm16 = true,
            description = "Direct PCM supported: 24-bit/16-bit @ 192000 Hz",
        )

        val result = OutputNegotiator.negotiate(
            source = streamingSource(192000, bitDepth = 24),
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 192000,
            channelCount = 2,
            routeKind = AudioRouting.Kind.USB,
            deviceName = "Chu2 DSP",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_24BIT_PACKED),
            advertisedSampleRates = listOf(44100, 48000, 88200, 96000, 192000),
            requestedMode = OutputPcmMode.FLOAT_32,
            directSupport = directSupport192,
        )

        assertEquals(TransportType.AUDIO_TRACK_DIRECT, result.output.transport)
        assertTrue(result.output.isDirect)
        assertEquals(PcmEncoding.PCM_24BIT_PACKED, result.output.encoding)
        assertNull(result.output.systemMixerRateHz)
        assertEquals(FallbackReason.ROUTE_LIMITATION, result.output.fallbackReason)
        assertEquals("Float32", result.dsp.format)
    }

    // 4. Local 176.4 kHz direct-capable route
    @Test
    fun local176400HzDirectCapableRoute() {
        val directSupport = DirectAudioProbe.DirectSupport(
            isDirectSupported = true,
            isOffloadSupported = false,
            supportsFloat = false,
            supportsPcm24 = false,
            supportsPcm16 = true,
            description = "Direct PCM supported: 16-bit @ 176400 Hz",
        )

        val result = OutputNegotiator.negotiate(
            source = flacSource(176400, bitDepth = 24),
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 176400,
            channelCount = 2,
            routeKind = AudioRouting.Kind.USB,
            deviceName = "Chu2 DSP",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_24BIT_PACKED),
            advertisedSampleRates = listOf(44100, 48000, 88200, 96000),
            requestedMode = OutputPcmMode.FLOAT_32,
            directSupport = directSupport,
        )

        assertEquals(TransportType.AUDIO_TRACK_DIRECT, result.output.transport)
        assertTrue(result.output.isDirect)
        assertEquals(PcmEncoding.PCM_16BIT, result.output.encoding)
        assertNull(result.output.systemMixerRateHz)
        assertEquals(FallbackReason.ROUTE_LIMITATION, result.output.fallbackReason)
    }

    // 5. Local 192 kHz direct-capable route
    @Test
    fun local192000HzDirectCapableRoute() {
        val directSupport = DirectAudioProbe.DirectSupport(
            isDirectSupported = true,
            isOffloadSupported = false,
            supportsFloat = false,
            supportsPcm24 = true,
            supportsPcm16 = true,
            description = "Direct PCM supported: 24-bit/16-bit @ 192000 Hz",
        )

        val result = OutputNegotiator.negotiate(
            source = flacSource(192000, bitDepth = 24),
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 192000,
            channelCount = 2,
            routeKind = AudioRouting.Kind.USB,
            deviceName = "Chu2 DSP",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_24BIT_PACKED),
            advertisedSampleRates = listOf(44100, 48000, 88200, 96000, 192000),
            requestedMode = OutputPcmMode.FLOAT_32,
            directSupport = directSupport,
        )

        assertEquals(TransportType.AUDIO_TRACK_DIRECT, result.output.transport)
        assertTrue(result.output.isDirect)
        assertEquals(PcmEncoding.PCM_24BIT_PACKED, result.output.encoding)
        assertNull(result.output.systemMixerRateHz)
        assertEquals(FallbackReason.ROUTE_LIMITATION, result.output.fallbackReason)
    }

    // 6. Streaming and local must produce the exact same direct decision under identical route & format
    @Test
    fun streamingAndLocalProduceIdenticalDirectDecision() {
        val directSupport = DirectAudioProbe.DirectSupport(
            isDirectSupported = true,
            isOffloadSupported = false,
            supportsFloat = false,
            supportsPcm24 = false,
            supportsPcm16 = true,
            description = "Direct PCM supported: 16-bit @ 176400 Hz",
        )

        val streamingResult = OutputNegotiator.negotiate(
            source = streamingSource(176400, bitDepth = 24),
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 176400,
            channelCount = 2,
            routeKind = AudioRouting.Kind.USB,
            deviceName = "Chu2 DSP",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT),
            advertisedSampleRates = listOf(44100, 48000, 88200, 96000),
            requestedMode = OutputPcmMode.FLOAT_32,
            directSupport = directSupport,
        )

        val localResult = OutputNegotiator.negotiate(
            source = flacSource(176400, bitDepth = 24),
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 176400,
            channelCount = 2,
            routeKind = AudioRouting.Kind.USB,
            deviceName = "Chu2 DSP",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT),
            advertisedSampleRates = listOf(44100, 48000, 88200, 96000),
            requestedMode = OutputPcmMode.FLOAT_32,
            directSupport = directSupport,
        )

        assertEquals(streamingResult.output.transport, localResult.output.transport)
        assertEquals(streamingResult.output.encoding, localResult.output.encoding)
        assertEquals(streamingResult.output.isDirect, localResult.output.isDirect)
        assertEquals(streamingResult.output.systemMixerRateHz, localResult.output.systemMixerRateHz)
        assertEquals(streamingResult.output.fallbackReason, localResult.output.fallbackReason)
    }

    // 7. Source type alone must not disable direct output
    @Test
    fun sourceTypeAloneDoesNotDisableDirectOutput() {
        val directSupport = DirectAudioProbe.DirectSupport(
            isDirectSupported = true,
            isOffloadSupported = false,
            supportsFloat = false,
            supportsPcm24 = true,
            supportsPcm16 = true,
            description = "Direct PCM supported",
        )

        listOf("local", "Unified", "JioSaavn", "Addon").forEach { providerName ->
            val source = SourceDescriptor(
                encoding = "audio/flac",
                sampleRateHz = 96000,
                channelCount = 2,
                bitDepth = 24,
            )
            val result = OutputNegotiator.negotiate(
                source = source,
                decoderName = "c2.android.flac.decoder",
                sampleRateHz = 96000,
                channelCount = 2,
                routeKind = AudioRouting.Kind.USB,
                deviceName = "USB DAC ($providerName)",
                advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_24BIT_PACKED),
                advertisedSampleRates = listOf(96000),
                requestedMode = OutputPcmMode.FLOAT_32,
                directSupport = directSupport,
            )
            assertTrue("Provider $providerName must achieve direct output", result.output.isDirect)
            assertEquals(TransportType.AUDIO_TRACK_DIRECT, result.output.transport)
        }
    }

    // 8. Route capability determines direct availability
    @Test
    fun routeCapabilityDeterminesDirectAvailability() {
        val capableRoute = DirectAudioProbe.DirectSupport(
            isDirectSupported = true,
            isOffloadSupported = false,
            supportsFloat = false,
            supportsPcm24 = true,
            supportsPcm16 = true,
            description = "Direct supported",
        )
        val incapableRoute = DirectAudioProbe.DirectSupport.NONE

        val directResult = OutputNegotiator.negotiate(
            source = streamingSource(96000),
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 96000,
            channelCount = 2,
            routeKind = AudioRouting.Kind.USB,
            deviceName = "Capable DAC",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_24BIT_PACKED),
            advertisedSampleRates = listOf(96000),
            requestedMode = OutputPcmMode.FLOAT_32,
            directSupport = capableRoute,
        )
        assertTrue(directResult.output.isDirect)

        val fallbackResult = OutputNegotiator.negotiate(
            source = streamingSource(96000),
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 96000,
            channelCount = 2,
            routeKind = AudioRouting.Kind.USB,
            deviceName = "Incapable DAC",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT),
            advertisedSampleRates = listOf(48000),
            requestedMode = OutputPcmMode.FLOAT_32,
            directSupport = incapableRoute,
            knownSystemMixerRateHz = 48000,
        )
        assertFalse(fallbackResult.output.isDirect)
        assertEquals(TransportType.AUDIO_TRACK, fallbackResult.output.transport)
        assertEquals(48000, fallbackResult.output.systemMixerRateHz)
    }

    // 9. Exact sample-rate / encoding combination must be checked
    @Test
    fun exactSampleRateEncodingCombinationChecked() {
        val rates = listOf(44100, 48000, 88200, 96000, 176400, 192000)
        rates.forEach { rate ->
            val directSupport = DirectAudioProbe.DirectSupport(
                isDirectSupported = true,
                isOffloadSupported = false,
                supportsFloat = false,
                supportsPcm24 = rate != 176400, // 176.4 has no PCM24 direct
                supportsPcm16 = true,
                description = "Direct support for $rate",
            )
            val result = OutputNegotiator.negotiate(
                source = streamingSource(rate),
                decoderName = "c2.android.flac.decoder",
                sampleRateHz = rate,
                channelCount = 2,
                routeKind = AudioRouting.Kind.USB,
                deviceName = "Test DAC",
                advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_24BIT_PACKED),
                advertisedSampleRates = listOf(rate),
                requestedMode = OutputPcmMode.FLOAT_32,
                directSupport = directSupport,
            )
            assertTrue("Rate $rate must be direct", result.output.isDirect)
            if (rate == 176400) {
                assertEquals(PcmEncoding.PCM_16BIT, result.output.encoding)
                assertEquals(FallbackReason.ROUTE_LIMITATION, result.output.fallbackReason)
            } else {
                assertEquals(PcmEncoding.PCM_24BIT_PACKED, result.output.encoding)
                assertEquals(FallbackReason.ROUTE_LIMITATION, result.output.fallbackReason)
            }
        }
    }

    // 10. Unsupported combinations must fall back truthfully
    @Test
    fun unsupportedCombinationsFallBackTruthfully() {
        val directSupport = DirectAudioProbe.DirectSupport.NONE

        val result = OutputNegotiator.negotiate(
            source = streamingSource(384000),
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 384000,
            channelCount = 2,
            routeKind = AudioRouting.Kind.USB,
            deviceName = "Basic USB Dongle",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT),
            advertisedSampleRates = listOf(48000),
            requestedMode = OutputPcmMode.FLOAT_32,
            directSupport = directSupport,
            knownSystemMixerRateHz = 48000,
        )

        assertFalse(result.output.isDirect)
        assertEquals(TransportType.AUDIO_TRACK, result.output.transport)
        assertEquals(PcmEncoding.PCM_16BIT, result.output.encoding)
        assertEquals(48000, result.output.systemMixerRateHz)
        assertEquals(FallbackReason.ROUTE_LIMITATION, result.output.fallbackReason)
    }

    // 11. PCM16 output preference must remain strictly respected across all rates
    @Test
    fun pcm16OutputPreferenceStrictlyHonored() {
        val rates = listOf(44100, 48000, 88200, 96000, 176400, 192000)
        rates.forEach { rate ->
            val directSupport = DirectAudioProbe.DirectSupport(
                isDirectSupported = true,
                isOffloadSupported = false,
                supportsFloat = true,
                supportsPcm24 = true,
                supportsPcm16 = true,
                description = "All formats direct at $rate",
            )
            val result = OutputNegotiator.negotiate(
                source = streamingSource(rate, bitDepth = 24),
                decoderName = "c2.android.flac.decoder",
                sampleRateHz = rate,
                channelCount = 2,
                routeKind = AudioRouting.Kind.USB,
                deviceName = "High-End DAC",
                advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_24BIT_PACKED, AudioFormat.ENCODING_PCM_FLOAT),
                advertisedSampleRates = listOf(rate),
                requestedMode = OutputPcmMode.PCM_16,
                directSupport = directSupport,
            )
            assertEquals("DSP must be Float32", "Float32", result.dsp.format)
            assertEquals("Output must strictly be PCM16 at $rate", PcmEncoding.PCM_16BIT, result.output.encoding)
            assertTrue("Direct must be active at $rate", result.output.isDirect)
            assertEquals(FallbackReason.NONE, result.output.fallbackReason)
        }
    }

    // 12. Float32 DSP must remain active regardless of direct transport
    @Test
    fun float32DspRemainsActiveAcrossAllDirectAndFallbackTransports() {
        // Case A: Direct PCM24
        val directPcm24 = OutputNegotiator.negotiate(
            source = streamingSource(96000),
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 96000,
            channelCount = 2,
            routeKind = AudioRouting.Kind.USB,
            deviceName = "DAC",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_24BIT_PACKED),
            advertisedSampleRates = listOf(96000),
            requestedMode = OutputPcmMode.FLOAT_32,
            directSupport = DirectAudioProbe.DirectSupport(true, false, false, true, true, emptyList(), ""),
        )
        assertEquals("Float32", directPcm24.dsp.format)

        // Case B: Direct PCM16
        val directPcm16 = OutputNegotiator.negotiate(
            source = streamingSource(176400),
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 176400,
            channelCount = 2,
            routeKind = AudioRouting.Kind.USB,
            deviceName = "DAC",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT),
            advertisedSampleRates = listOf(176400),
            requestedMode = OutputPcmMode.FLOAT_32,
            directSupport = DirectAudioProbe.DirectSupport(true, false, false, false, true, emptyList(), ""),
        )
        assertEquals("Float32", directPcm16.dsp.format)

        // Case C: Mixer fallback PCM16
        val mixerFallback = OutputNegotiator.negotiate(
            source = streamingSource(176400),
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 176400,
            channelCount = 2,
            routeKind = AudioRouting.Kind.USB,
            deviceName = "DAC",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT),
            advertisedSampleRates = listOf(48000),
            requestedMode = OutputPcmMode.FLOAT_32,
            directSupport = DirectAudioProbe.DirectSupport.NONE,
        )
        assertEquals("Float32", mixerFallback.dsp.format)
    }

    // 13. Telemetry path evaluation truthfully reports Direct Active when directSupport confirms runtime support
    @Test
    fun telemetryEvaluationTruthfullyReportsDirectActiveWhenRuntimeDirectSupported() {
        // Chu2 DSP with 176.4 kHz streaming track where Android runtime directSupport is true
        val snapshot = AudioOutputStatus.Snapshot(
            deviceName = "Chu2 DSP",
            routeKind = AudioRouting.Kind.USB,
            encodings = intArrayOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_24BIT_PACKED),
            sampleRatesHz = intArrayOf(44100, 48000, 88200, 96000), // static USB descriptors capped at 96k
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

        val evaluated = AudioOutputStatus.evaluateActualPath(snapshot)

        assertTrue("Direct playback must be actual", evaluated.directPlaybackActual)
        assertFalse("Direct playback must not be rejected", evaluated.directPlaybackRejected)
        assertEquals(TransportType.AUDIO_TRACK_DIRECT, evaluated.transportType)
        assertNull("System mixer must be bypassed", evaluated.systemMixerRateHz)
        assertNull("HAL format must be null on direct bypass", evaluated.halFormat)
        assertEquals("PCM24 / 96000 Hz", evaluated.usbEndpointFormat)
    }
}
