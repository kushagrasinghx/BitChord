package com.music.bitchord

import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.discord.discordAudioQualityLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiscordAudioQualityTest {

    @Test
    fun `hi-res line contains measured specs separated by center dots`() {
        val stats = NerdStats.Snapshot(
            mimeType = "audio/flac",
            bitrateKbps = 4608,
            sampleRateHz = 96000,
            channels = 2,
            bitDepth = 24,
        )

        assertEquals(
            "Hi-Res Lossless · FLAC · 4608 kbps · 24-bit · 96 kHz · Stereo",
            discordAudioQualityLine(stats),
        )
    }

    @Test
    fun `cd-quality lossless line keeps known specs only`() {
        val stats = NerdStats.Snapshot(
            mimeType = "audio/alac",
            bitrateKbps = 1411,
            sampleRateHz = 44100,
            channels = null,
            bitDepth = 16,
        )

        assertEquals(
            "Lossless · ALAC · 1411 kbps · 16-bit · 44.1 kHz",
            discordAudioQualityLine(stats),
        )
    }

    @Test
    fun `dolby line includes immersive codec and channels`() {
        val stats = NerdStats.Snapshot(
            mimeType = "audio/eac3-joc",
            bitrateKbps = 768,
            sampleRateHz = 48000,
            channels = 6,
        )

        assertEquals(
            "◗◖ Dolby Atmos · E-AC-3 JOC · 768 kbps · 48 kHz · 6 ch",
            discordAudioQualityLine(stats),
        )
    }

    @Test
    fun `ordinary lossy audio has no quality line`() {
        val stats = NerdStats.Snapshot(
            mimeType = "audio/opus",
            bitrateKbps = 160,
            sampleRateHz = 48000,
            channels = 2,
        )

        assertNull(discordAudioQualityLine(stats))
        assertNull(discordAudioQualityLine(null))
    }
}
