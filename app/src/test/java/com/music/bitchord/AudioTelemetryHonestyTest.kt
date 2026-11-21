package com.music.bitchord

import android.media.AudioFormat
import com.music.bitchord.playback.AudioOutputStatus
import com.music.bitchord.playback.AudioRouting
import com.music.bitchord.playback.audio.DirectAudioProbe
import com.music.bitchord.playback.audio.TransportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTelemetryHonestyTest {

    @Test
    fun `unmeasured mixer rate and HAL format stay unknown`() {
        val snapshot = AudioOutputStatus.Snapshot(
            routeKind = AudioRouting.Kind.PHONE,
            actualSampleRateHz = 44_100,
            systemMixerRateHz = null,
        )

        val result = AudioOutputStatus.evaluateActualPath(snapshot)

        assertNull("44.1 kHz must not be reported as a fabricated 48 kHz mixer", result.systemMixerRateHz)
        assertNull("HAL format is not queryable and must stay unknown", result.halFormat)
    }

    @Test
    fun `direct Float32 is reported as accepted, not rejected`() {
        val support = DirectAudioProbe.DirectSupport(
            isDirectSupported = true,
            isOffloadSupported = false,
            supportsFloat = true,
            supportsPcm24 = true,
            supportsPcm16 = true,
            description = "test direct-float DAC",
        )
        val snapshot = AudioOutputStatus.Snapshot(
            routeKind = AudioRouting.Kind.USB,
            requestedTransportType = TransportType.AUDIO_TRACK_DIRECT,
            actualEncoding = AudioFormat.ENCODING_PCM_FLOAT,
            actualSampleRateHz = 96_000,
            sampleRatesHz = intArrayOf(96_000),
            encodings = intArrayOf(AudioFormat.ENCODING_PCM_FLOAT),
            directSupport = support,
            directPlaybackSelected = true,
        )

        val result = AudioOutputStatus.evaluateActualPath(snapshot)

        assertEquals(TransportType.AUDIO_TRACK_DIRECT, result.transportType)
        assertTrue(result.directPlaybackActual)
        assertFalse(result.directPlaybackRejected)
        assertNull(result.systemMixerRateHz)
    }

    @Test
    fun `unknown USB descriptors do not invent an endpoint format`() {
        val snapshot = AudioOutputStatus.Snapshot(
            routeKind = AudioRouting.Kind.USB,
            actualSampleRateHz = 48_000,
        )

        val result = AudioOutputStatus.evaluateActualPath(snapshot)

        assertNull(result.usbEndpointFormat)
        assertNull(result.halFormat)
    }
}
