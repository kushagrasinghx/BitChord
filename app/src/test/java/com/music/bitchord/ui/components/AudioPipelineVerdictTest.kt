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
