package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The quality badge and the stats line are both read off [DesktopStreamFormat], and every label
 * they draw has to mean the same thing it means on Android.
 */
class DesktopStreamFormatTest {

    @Test
    fun `ffmpeg codec names are recognised as lossless`() {
        assertTrue(DesktopStreamFormat(codec = "flac").isLossless)
        assertTrue(DesktopStreamFormat(codec = "alac").isLossless)
        assertTrue(DesktopStreamFormat(codec = "wavpack").isLossless)
        // FFmpeg names every uncompressed flavour pcm_<layout>.
        assertTrue(DesktopStreamFormat(codec = "pcm_s16le").isLossless)
        assertFalse(DesktopStreamFormat(codec = "aac").isLossless)
        assertFalse(DesktopStreamFormat(codec = "opus").isLossless)
    }

    @Test
    fun `hi-res is past CD, not merely lossless`() {
        val cd = DesktopStreamFormat("flac", sampleRateHz = 44_100, bitDepth = 16, channels = 2)
        val hiRes = DesktopStreamFormat("flac", sampleRateHz = 96_000, bitDepth = 24, channels = 2)
        assertTrue(cd.isLossless)
        assertFalse(cd.isHiRes)
        assertTrue(hiRes.isHiRes)
    }

    @Test
    fun `high quality is decided on the rate, not the source`() {
        // YouTube's Opus tops out around 160 and does not qualify; a 320kbps AAC does, wherever it
        // came from.
        assertFalse(DesktopStreamFormat("opus", kbps = 160).isHiQuality)
        assertTrue(DesktopStreamFormat("aac", kbps = 320).isHiQuality)
        assertTrue(DesktopStreamFormat("mp3", kbps = 256).isHiQuality)
        // Lossless is never *also* called high quality: the badge picks one.
        assertFalse(DesktopStreamFormat("flac", kbps = 1_411).isHiQuality)
    }

    @Test
    fun `dolby atmos is distinct from lossless`() {
        val atmos = DesktopStreamFormat(codec = "eac3", kbps = 768)
        assertTrue(atmos.isDolbyAtmos)
        assertFalse(atmos.isLossless)
    }

    @Test
    fun `a lossless stream states what its samples decode to`() {
        // 16-bit 44.1kHz stereo is 1411kbps, the figure Tidal and Apple Music print — not whatever
        // the FLAC container happened to compress to.
        assertEquals(
            "FLAC · 16-bit · 44.1 kHz · 1411 kbps · Stereo",
            DesktopStreamFormat("flac", kbps = 900, sampleRateHz = 44_100, bitDepth = 16, channels = 2).summary,
        )
    }

    @Test
    fun `a lossy stream states its own rate under its usual codec name`() {
        assertEquals(
            "AAC · 44.1 kHz · 302 kbps · Stereo",
            DesktopStreamFormat("aac", kbps = 302, sampleRateHz = 44_100, channels = 2).summary,
        )
        // YouTube describes every audio rendition as audio/mp4 whatever is inside it, which is why
        // the claimed format is never what the player shows.
        assertEquals("AAC · 320 kbps", DesktopStreamFormat("audio/mp4; codecs=\"mp4a.40.2\"", kbps = 320).summary)
    }

    @Test
    fun `nothing known is stated rather than guessed at`() {
        assertEquals("Unknown format", DesktopStreamFormat().summary)
        assertEquals("Opus · 160 kbps", DesktopStreamFormat("opus", kbps = 160).summary)
    }
}
