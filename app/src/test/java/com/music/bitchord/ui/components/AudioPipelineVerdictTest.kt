package com.music.bitchord.ui.components

import com.music.bitchord.playback.audio.bitperfect.BitPerfectOutput
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioPipelineVerdictTest {

    @Test
    fun bluetoothIsNeverReportedAsBitExact() {
        val verdict = bitExactVerdict(
            dsd = false,
            bitPerfect = BitPerfectOutput.Status(),
            outputExact = true,
            outputExactDetail = "24-bit PCM to Float32",
            loudnessActive = false,
            eqActive = false,
            spatialActive = false,
            bluetooth = true,
            bluetoothCodec = "LDAC",
        )
        assertEquals("No — lossy Bluetooth (LDAC) re-encode", verdict)
    }

    @Test
    fun verifiedExclusiveOutputDoesNotClaimSourceLosslessness() {
        val verdict = bitExactVerdict(
            dsd = false,
            bitPerfect = BitPerfectOutput.Status(
                active = true,
                deviceName = "USB DAC",
                exact = true,
                verified = true,
            ),
            outputExact = false,
            outputExactDetail = null,
            loudnessActive = false,
            eqActive = false,
            spatialActive = false,
        )
        assertEquals("Yes — integer-exact to USB DAC", verdict)
    }

    @Test
    fun directAudioTrackIsNotReportedAsVerifiedActive() {
        val verdict = directStatusText(
            bitPerfectDriver = null,
            directPlaybackActual = true,
            directPlaybackRejected = false,
            directPlaybackSupported = true,
        )
        assertEquals("Requested (Direct AudioTrack; mixer bypass unverified)", verdict)
    }

    @Test
    fun wiredIntegerExactOutputKeepsThePositiveVerdict() {
        val verdict = bitExactVerdict(
            dsd = false,
            bitPerfect = BitPerfectOutput.Status(),
            outputExact = true,
            outputExactDetail = "16-bit PCM",
            loudnessActive = false,
            eqActive = false,
            spatialActive = false,
        )
        assertEquals("Yes (16-bit PCM)", verdict)
    }
}
