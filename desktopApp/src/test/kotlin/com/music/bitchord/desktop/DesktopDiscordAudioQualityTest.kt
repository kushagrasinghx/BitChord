package com.music.bitchord.desktop

import com.music.bitchord.data.NerdStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopDiscordAudioQualityTest {

    @Test
    fun flacMeasuresProduceFullLine() {
        val stats = NerdStats.Snapshot(
            mimeType = "audio/flac",
            bitrateKbps = 4608,
            sampleRateHz = 96000,
            channels = 2,
            bitDepth = 24,
        )
        assertEquals(
            "Hi-Res Lossless · FLAC · 4608 kbps · 24-bit · 96 kHz · Stereo",
            desktopAudioQualityLine(stats),
        )
    }

    @Test
    fun lossyHasNoLine() {
        val stats = NerdStats.Snapshot(
            mimeType = "audio/opus",
            bitrateKbps = 160,
            sampleRateHz = 48000,
            channels = 2,
        )
        assertNull(desktopAudioQualityLine(stats))
        assertNull(desktopAudioQualityLine(null))
    }
}
