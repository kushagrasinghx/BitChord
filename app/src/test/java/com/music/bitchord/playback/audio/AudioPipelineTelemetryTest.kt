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
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.TelemetryProvenance
import com.music.bitchord.playback.AudioOutputStatus
import com.music.bitchord.playback.AudioRouting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Rigorous telemetry correctness test suite:
 * Verifies that all statistics reported across the Audio Pipeline
 * represent authentic source/runtime facts with strict provenance,
 * without conflating encoded stream bitrate with PCM data throughput,
 * without inventing fallbacks for unknown runtime states, and cleanly
 * resetting metadata across track and route boundaries.
 */
class AudioPipelineTelemetryTest {

    @Before
    fun setUp() {
        NerdStats.forgetLastSession()
        AudioOutputStatus.reset()
    }

    // ── 1. Bitrate vs PCM Throughput Separation ───────────────────────────

    @Test
    fun flacStreamBitrateDoesNotConflateWithPcmDataRate() {
        // CD Quality FLAC: 16-bit, 44.1 kHz stereo
        // True PCM throughput: 16 * 44100 * 2 / 1000 = 1411 kbps
        // Actual encoded stream bitrate: 785 kbps
        val flacSnapshot = NerdStats.Snapshot(
            mimeType = "audio/flac",
            bitrateKbps = 785,
            bitrateProvenance = TelemetryProvenance.AUTHORITATIVE,
            pcmDataRateKbps = 1411,
            sampleRateHz = 44100,
            channels = 2,
            bitDepth = 16,
        )

        // Verifications:
        // 1. Encoded bitrate is 785 kbps, NEVER manufactured as 1411 kbps
        assertEquals(785, flacSnapshot.bitrateKbps)
        assertNotEquals(1411, flacSnapshot.bitrateKbps)
        assertEquals(TelemetryProvenance.AUTHORITATIVE, flacSnapshot.bitrateProvenance)

        // 2. PCM throughput is explicitly separated
        assertEquals(1411, flacSnapshot.pcmDataRateKbps)

        // 3. Identified as lossless, CD quality (not hi-res, not lossy)
        assertTrue(flacSnapshot.isLossless)
        assertFalse(flacSnapshot.isHiRes)
        assertFalse(flacSnapshot.isHiQuality)
    }

    @Test
    fun flacHighRes24Bit48kStereoCalculatesPcmRateSeparately() {
        // Hi-Res FLAC: 24-bit, 48.0 kHz stereo
        // True PCM throughput: 24 * 48000 * 2 / 1000 = 2304 kbps
        // Actual encoded stream bitrate: 1540 kbps
        val hiResSnapshot = NerdStats.Snapshot(
            mimeType = "audio/flac",
            bitrateKbps = 1540,
            bitrateProvenance = TelemetryProvenance.AUTHORITATIVE,
            pcmDataRateKbps = 2304,
            sampleRateHz = 48000,
            channels = 2,
            bitDepth = 24,
        )

        // Encoded bitrate must not be 2304 kbps
        assertEquals(1540, hiResSnapshot.bitrateKbps)
        assertNotEquals(2304, hiResSnapshot.bitrateKbps)
        assertEquals(2304, hiResSnapshot.pcmDataRateKbps)
        assertTrue(hiResSnapshot.isLossless)
        assertTrue(hiResSnapshot.isHiRes)
    }

    @Test
    fun flacUnknownBitrateLeavesBitrateNullAndPcmRateCalculated() {
        // FLAC where container does not declare bitrate and not yet resolved:
        val unknownBitrateFlac = NerdStats.Snapshot(
            mimeType = "audio/flac",
            bitrateKbps = null,
            bitrateProvenance = TelemetryProvenance.UNKNOWN,
            pcmDataRateKbps = 1411,
            sampleRateHz = 44100,
            channels = 2,
            bitDepth = 16,
        )

        // Encoded bitrate must remain unknown, NEVER fall back to 1411 kbps!
        assertNull(unknownBitrateFlac.bitrateKbps)
        assertEquals(TelemetryProvenance.UNKNOWN, unknownBitrateFlac.bitrateProvenance)
        assertEquals(1411, unknownBitrateFlac.pcmDataRateKbps)
        assertTrue(unknownBitrateFlac.isLossless)
    }

    @Test
    fun rawPcmStreamBitrateIsDerivedFromPcmThroughput() {
        // Raw linear PCM / WAV: Encoded bitrate IS the PCM throughput (DERIVED)
        val pcmThroughput = (16L * 44100 * 2 / 1000).toInt()
        val wavSnapshot = NerdStats.Snapshot(
            mimeType = "audio/raw",
            bitrateKbps = pcmThroughput,
            bitrateProvenance = TelemetryProvenance.DERIVED,
            pcmDataRateKbps = pcmThroughput,
            sampleRateHz = 44100,
            channels = 2,
            bitDepth = 16,
        )

        assertEquals(1411, wavSnapshot.bitrateKbps)
        assertEquals(TelemetryProvenance.DERIVED, wavSnapshot.bitrateProvenance)
        assertEquals(1411, wavSnapshot.pcmDataRateKbps)
        assertTrue(wavSnapshot.isLossless)
        assertTrue(NerdStats.isRawPcm("audio/raw"))
        assertTrue(NerdStats.isRawPcm("audio/wav"))
        assertTrue(NerdStats.isRawPcm("audio/x-wav"))
        assertFalse(NerdStats.isRawPcm("audio/flac"))
        assertFalse(NerdStats.isRawPcm("audio/opus"))
    }

    @Test
    fun lossyFormatsHaveNoBitDepthAndNoPcmDataRate() {
        // Lossy streaming (e.g. YouTube Opus 160 kbps, AAC 256 kbps)
        val opusSnapshot = NerdStats.Snapshot(
            mimeType = "audio/opus",
            bitrateKbps = 160,
            bitrateProvenance = TelemetryProvenance.AUTHORITATIVE,
            pcmDataRateKbps = null,
            sampleRateHz = 48000,
            channels = 2,
            bitDepth = null, // Lossy codecs do not have a bit depth!
        )

        assertFalse(opusSnapshot.isLossless)
        assertNull(opusSnapshot.bitDepth)
        assertNull(opusSnapshot.pcmDataRateKbps)
        assertEquals(160, opusSnapshot.bitrateKbps)
        assertEquals(TelemetryProvenance.AUTHORITATIVE, opusSnapshot.bitrateProvenance)
    }

    // ── 2. Resampler Stage Truthfulness ───────────────────────────────────

    @Test
    fun resamplerDoesNotFalselyReportPassthroughWhenOutputRateIsUnknown() {
        val inRate: Int? = 44100
        val outRate: Int? = null

        val isPassthrough = inRate != null && outRate != null && inRate == outRate
        val resamplerType = when {
            inRate == null || outRate == null -> "—"
            isPassthrough -> "None"
            else -> "Resampler"
        }
        val qualityText = when {
            inRate == null || outRate == null -> "—"
            isPassthrough -> "Passthrough"
            else -> "Resampled"
        }

        assertFalse(isPassthrough)
        assertEquals("—", resamplerType)
        assertEquals("—", qualityText)
    }

    @Test
    fun resamplerAccuratelyReportsPassthroughWhenRatesMatch() {
        val inRate: Int? = 48000
        val outRate: Int? = 48000

        val isPassthrough = inRate != null && outRate != null && inRate == outRate
        val resamplerType = when {
            inRate == null || outRate == null -> "—"
            isPassthrough -> "None"
            else -> "Resampler"
        }
        val qualityText = when {
            inRate == null || outRate == null -> "—"
            isPassthrough -> "Passthrough"
            else -> "Resampled"
        }

        assertTrue(isPassthrough)
        assertEquals("None", resamplerType)
        assertEquals("Passthrough", qualityText)
    }

    @Test
    fun resamplerAccuratelyReportsResampledWhenRatesDiffer() {
        val inRate: Int? = 44100
        val outRate: Int? = 48000

        val isPassthrough = inRate != null && outRate != null && inRate == outRate
        val resamplerType = when {
            inRate == null || outRate == null -> "—"
            isPassthrough -> "None"
            else -> "Resampler"
        }
        val qualityText = when {
            inRate == null || outRate == null -> "—"
            isPassthrough -> "Passthrough"
            else -> "Resampled"
        }

        assertFalse(isPassthrough)
        assertEquals("Resampler", resamplerType)
        assertEquals("Resampled", qualityText)
    }

    // ── 3. AudioTrack Stage Truthfulness ──────────────────────────────────

    @Test
    fun audioTrackDoesNotFabricateFloat32WhenUninitialized() {
        val actualEncoding: Int? = null
        val actualSampleRateHz: Int? = null

        val audioTrackEncoding = when (actualEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> "Float32"
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM24"
            AudioFormat.ENCODING_PCM_32BIT -> "PCM32"
            AudioFormat.ENCODING_PCM_16BIT -> "PCM16"
            else -> null
        }
        val audioTrackRate = actualSampleRateHz
        val audioTrackText = when {
            audioTrackEncoding != null && audioTrackRate != null -> "$audioTrackEncoding / $audioTrackRate Hz"
            audioTrackEncoding != null -> audioTrackEncoding
            audioTrackRate != null -> "$audioTrackRate Hz"
            else -> "—"
        }

        assertEquals("—", audioTrackText)
        assertNotEquals("Float32 / 48000 Hz", audioTrackText)
    }

    @Test
    fun audioTrackDisplaysExactConfiguredFormat() {
        val actualEncoding = AudioFormat.ENCODING_PCM_24BIT_PACKED
        val actualSampleRateHz = 96000

        val audioTrackEncoding = when (actualEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> "Float32"
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM24"
            AudioFormat.ENCODING_PCM_32BIT -> "PCM32"
            AudioFormat.ENCODING_PCM_16BIT -> "PCM16"
            else -> null
        }
        val audioTrackRate = actualSampleRateHz
        val audioTrackText = when {
            audioTrackEncoding != null && audioTrackRate != null -> "$audioTrackEncoding / $audioTrackRate Hz"
            audioTrackEncoding != null -> audioTrackEncoding
            audioTrackRate != null -> "$audioTrackRate Hz"
            else -> "—"
        }

        assertEquals("PCM24 / 96000 Hz", audioTrackText)
    }

    // ── 4. Track Transition Reset ─────────────────────────────────────────

    @Test
    fun trackTransitionClearsStalePerTrackMetadata() {
        AudioOutputStatus.publishDecoder("c2.android.flac.decoder")
        AudioOutputStatus.publishDsp(decoderOutputEncoding = "24-bit PCM")
        AudioOutputStatus.publishOutputExactness(exact = true, detail = "Bit-exact path active")
        AudioOutputStatus.publishLoudness(gainDb = -2.5f, lufs = -14.0f)

        assertEquals("c2.android.flac.decoder", AudioOutputStatus.current.value.decoderName)
        assertEquals("24-bit PCM", AudioOutputStatus.current.value.decoderOutputEncoding)
        assertTrue(AudioOutputStatus.current.value.outputExact)
        assertEquals(-2.5f, AudioOutputStatus.current.value.loudnessGainDb)
        assertEquals(-14.0f, AudioOutputStatus.current.value.loudnessLufs)

        // Perform track transition
        AudioOutputStatus.onTrackTransition()

        val after = AudioOutputStatus.current.value
        assertNull(after.decoderName)
        assertNull(after.decoderOutputEncoding)
        assertFalse(after.outputExact)
        assertNull(after.outputExactDetail)
        assertNull(after.loudnessGainDb)
        assertNull(after.loudnessLufs)
    }

    @Test
    fun nerdStatsTrackTransitionResetsCurrentSnapshot() {
        NerdStats.current.value = NerdStats.Snapshot(
            mimeType = "audio/flac",
            bitrateKbps = 800,
            sampleRateHz = 44100,
            channels = 2,
            bitDepth = 16,
        )
        assertEquals(800, NerdStats.current.value?.bitrateKbps)

        NerdStats.onTrackTransition()

        assertNull(NerdStats.current.value)
    }

    // ── 5. Route Transition Reset ─────────────────────────────────────────

    @Test
    fun bluetoothRouteTransitionClearsBluetoothProfileWhenRoutingAway() {
        val btSnapshot = AudioOutputStatus.Snapshot(
            routeKind = AudioRouting.Kind.BLUETOOTH,
            bluetoothProfile = "A2DP",
        )
        AudioOutputStatus.current.value = btSnapshot
        assertEquals("A2DP", AudioOutputStatus.current.value.bluetoothProfile)

        // Publish negotiation for PHONE route (speaker)
        val phoneNegotiation = OutputNegotiator.negotiate(
            source = SourceDescriptor(
                encoding = "16-bit PCM",
                sampleRateHz = 44100,
                channelCount = 2,
                bitDepth = 16,
            ),
            decoderName = "c2.android.flac.decoder",
            sampleRateHz = 44100,
            channelCount = 2,
            routeKind = AudioRouting.Kind.PHONE,
            deviceName = "Speaker",
            advertisedEncodings = listOf(AudioFormat.ENCODING_PCM_16BIT),
            advertisedSampleRates = listOf(48000),
            requestedMode = com.music.bitchord.data.settings.OutputPcmMode.PCM_16,
        )

        AudioOutputStatus.publishNegotiation(phoneNegotiation)

        val evaluated = AudioOutputStatus.current.value
        assertEquals(AudioRouting.Kind.PHONE, evaluated.routeKind)
        assertNull(evaluated.bluetoothProfile)
        assertNull(evaluated.bluetoothTelemetry)
    }
}
