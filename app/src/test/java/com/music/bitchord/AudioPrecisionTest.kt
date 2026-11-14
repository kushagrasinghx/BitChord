package com.music.bitchord

import com.music.bitchord.data.settings.OutputPcmMode
import com.music.bitchord.playback.audio.DirectAudioProbe
import com.music.bitchord.playback.audio.FallbackReason
import com.music.bitchord.playback.audio.OutputNegotiator
import com.music.bitchord.playback.audio.PcmBoundary
import com.music.bitchord.playback.audio.SourceDescriptor
import com.music.bitchord.playback.audio.dspNeedsLimiter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPrecisionTest {

    @Test
    fun `TPDF dither changes a sub-LSB signal but never adds noise to silence`() {
        assertEquals(0, PcmBoundary.clamp16FromFloat(0f, dither = true, noise = { 1f }).toInt())

        val positive = PcmBoundary.clamp16FromFloat(0.5f, dither = true, noise = { 1f })
        var call = 0
        val negative = PcmBoundary.clamp16FromFloat(
            0.5f,
            dither = true,
            noise = { if (call++ == 0) 0f else 1f },
        )
        assertNotEquals(positive, negative)
        assertEquals(16384.toShort(), positive)
        assertEquals(16383.toShort(), negative)
    }

    @Test
    fun `without dither quantisation is deterministic`() {
        assertEquals(16384.toShort(), PcmBoundary.clamp16FromFloat(0.5f))
    }

    @Test
    fun `a high-res source on a 16-bit request reports user preference`() {
        val result = OutputNegotiator.negotiate(
            source = SourceDescriptor(encoding = "PCM", sampleRateHz = 96_000, channelCount = 2, bitDepth = 24),
            decoderName = "test",
            sampleRateHz = 96_000,
            channelCount = 2,
            routeKind = com.music.bitchord.playback.AudioRouting.Kind.USB,
            deviceName = "USB DAC",
            advertisedEncodings = listOf(android.media.AudioFormat.ENCODING_PCM_16BIT),
            advertisedSampleRates = listOf(96_000),
            requestedMode = OutputPcmMode.PCM_16,
            directSupport = DirectAudioProbe.DirectSupport.NONE,
        )

        assertEquals(FallbackReason.USER_PREFERENCE, result.output.fallbackReason)
        assertTrue(result.output.fallbackDetail?.contains("16-bit") == true)
    }

    @Test
    fun `direct PCM16 on a high-res source still reports the user preference`() {
        val support = DirectAudioProbe.DirectSupport(
            isDirectSupported = true,
            isOffloadSupported = false,
            supportsFloat = false,
            supportsPcm24 = false,
            supportsPcm16 = true,
            description = "direct PCM16",
        )
        val result = OutputNegotiator.negotiate(
            source = SourceDescriptor("PCM", 96_000, 2, 24),
            decoderName = "test",
            sampleRateHz = 96_000,
            channelCount = 2,
            routeKind = com.music.bitchord.playback.AudioRouting.Kind.USB,
            deviceName = "USB DAC",
            advertisedEncodings = listOf(android.media.AudioFormat.ENCODING_PCM_16BIT),
            advertisedSampleRates = listOf(96_000),
            requestedMode = OutputPcmMode.PCM_16,
            directSupport = support,
        )
        assertEquals(com.music.bitchord.playback.audio.TransportType.AUDIO_TRACK_DIRECT, result.output.transport)
        assertEquals(FallbackReason.USER_PREFERENCE, result.output.fallbackReason)
    }

    @Test
    fun `the limiter engages for spatial and echo processing`() {
        assertTrue(dspNeedsLimiter(false, spatialEnabled = true, false, false))
        assertTrue(dspNeedsLimiter(false, false, false, echoEngaged = true))
        assertTrue(dspNeedsLimiter(false, false, transitionFiltering = true, false))
        assertEquals(false, dspNeedsLimiter(false, false, false, false))
    }
}
