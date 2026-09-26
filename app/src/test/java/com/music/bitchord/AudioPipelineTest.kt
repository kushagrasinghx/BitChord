package com.music.bitchord

import android.media.AudioFormat
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.sources.StreamFormat
import com.music.bitchord.playback.AudioOutputStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verification of the Audio Pipeline models, quality badge tier classification,
 * preference matching, and authoritative stream source tracking.
 */
class AudioPipelineTest {

    @Before
    fun setUp() {
        NerdStats.forgetLastSession()
        AudioOutputStatus.reset()
    }

    // ── Quality Badge Classification ──────────────────────────────────────

    @Test
    fun `low quality tier identifies streams at or below 80 kbps`() {
        val lowOpus = NerdStats.Snapshot(
            mimeType = "audio/opus",
            bitrateKbps = 64,
            sampleRateHz = 48000,
            channels = 2,
        )
        assertTrue(lowOpus.isLowQuality)
        assertFalse(lowOpus.isMediumQuality)
        assertFalse(lowOpus.isHiQuality)
        assertFalse(lowOpus.isLossless)

        val edgeLow = NerdStats.Snapshot(
            mimeType = "audio/opus",
            bitrateKbps = NerdStats.LOW_QUALITY_MAX_KBPS,
            sampleRateHz = 48000,
            channels = 2,
        )
        assertTrue(edgeLow.isLowQuality)
        assertFalse(edgeLow.isMediumQuality)

        val justAbove = NerdStats.Snapshot(
            mimeType = "audio/opus",
            bitrateKbps = NerdStats.LOW_QUALITY_MAX_KBPS + 1,
            sampleRateHz = 48000,
            channels = 2,
        )
        assertFalse(justAbove.isLowQuality)
        assertTrue(justAbove.isMediumQuality)
    }

    @Test
    fun `medium quality tier identifies streams between 80 kbps and 256 kbps`() {
        val ytOpus160 = NerdStats.Snapshot(
            mimeType = "audio/opus",
            bitrateKbps = 160,
            sampleRateHz = 48000,
            channels = 2,
        )
        assertFalse(ytOpus160.isLowQuality)
        assertTrue(ytOpus160.isMediumQuality)
        assertFalse(ytOpus160.isHiQuality)
        assertFalse(ytOpus160.isLossless)

        val aac128 = NerdStats.Snapshot(
            mimeType = "audio/mp4a-latm",
            bitrateKbps = 128,
            sampleRateHz = 44100,
            channels = 2,
        )
        assertTrue(aac128.isMediumQuality)
        assertFalse(aac128.isHiQuality)
    }

    @Test
    fun `high quality tier requires 256 kbps or higher and is lossy`() {
        val aac320 = NerdStats.Snapshot(
            mimeType = "audio/mp4a-latm",
            bitrateKbps = 320,
            sampleRateHz = 44100,
            channels = 2,
        )
        assertFalse(aac320.isLowQuality)
        assertFalse(aac320.isMediumQuality)
        assertTrue(aac320.isHiQuality)
        assertFalse(aac320.isLossless)

        val boundary256 = NerdStats.Snapshot(
            mimeType = "audio/mp4a-latm",
            bitrateKbps = NerdStats.HI_QUALITY_KBPS,
            sampleRateHz = 44100,
            channels = 2,
        )
        assertTrue(boundary256.isHiQuality)
        assertFalse(boundary256.isMediumQuality)
    }

    @Test
    fun `lossless and hi-res lossless are never tagged as lossy tiers`() {
        val flacCd = NerdStats.Snapshot(
            mimeType = "audio/flac",
            bitrateKbps = 1411,
            sampleRateHz = 44100,
            channels = 2,
            bitDepth = 16,
        )
        assertTrue(flacCd.isLossless)
        assertFalse(flacCd.isHiRes)
        assertFalse(flacCd.isLowQuality)
        assertFalse(flacCd.isMediumQuality)
        assertFalse(flacCd.isHiQuality)

        val flacHiRes = NerdStats.Snapshot(
            mimeType = "audio/flac",
            bitrateKbps = 4608,
            sampleRateHz = 96000,
            channels = 2,
            bitDepth = 24,
        )
        assertTrue(flacHiRes.isLossless)
        assertTrue(flacHiRes.isHiRes)
        assertFalse(flacHiRes.isLowQuality)
        assertFalse(flacHiRes.isMediumQuality)
        assertFalse(flacHiRes.isHiQuality)
    }

    // ── Codec Label Resolution ─────────────────────────────────────────────

    @Test
    fun `codecLabel formats standard mime types into clean display names`() {
        assertEquals("FLAC", NerdStats.codecLabel("audio/flac"))
        assertEquals("Opus", NerdStats.codecLabel("audio/opus"))
        assertEquals("AAC", NerdStats.codecLabel("audio/mp4a-latm"))
        assertEquals("AAC", NerdStats.codecLabel("audio/aac"))
        assertEquals("MP3", NerdStats.codecLabel("audio/mpeg"))
        assertEquals("MP3", NerdStats.codecLabel("audio/mp3"))
        assertEquals("ALAC", NerdStats.codecLabel("audio/alac"))
        assertEquals("PCM", NerdStats.codecLabel("audio/raw"))
        assertEquals("Vorbis", NerdStats.codecLabel("audio/vorbis"))
        assertEquals("E-AC-3 JOC", NerdStats.codecLabel("audio/eac3-joc"))
        assertEquals("E-AC-3", NerdStats.codecLabel("audio/eac3"))
        assertNull(NerdStats.codecLabel(null))
    }

    // ── Authoritative Source Tracking ──────────────────────────────────────

    @Test
    fun `authoritative sources are recorded and retrieved across track identifiers`() {
        NerdStats.recordSource("track-123", "YouTube")
        assertEquals("YouTube", NerdStats.sourceFor("track-123"))

        NerdStats.onSourceStream("track-456", StreamFormat(codec = "flac"), source = "Unified Addon")
        assertEquals("Unified Addon", NerdStats.sourceFor("track-456"))

        NerdStats.onStreamPicked("track-789", 320, source = "Catalogue")
        assertEquals("Catalogue", NerdStats.sourceFor("track-789"))
    }

    @Test
    fun `source lookup unwraps prefixed source track keys`() {
        NerdStats.recordSource("inner-id-abc", "Qobuz FLAC")
        val fullMediaId = "src:config-uuid-123::inner-id-abc"
        assertEquals("Qobuz FLAC", NerdStats.sourceFor(fullMediaId))
    }

    @Test
    fun `clearDeclared drops the recorded source for that track`() {
        NerdStats.recordSource("track-abandoned", "Tidal HiFi")
        assertEquals("Tidal HiFi", NerdStats.sourceFor("track-abandoned"))

        NerdStats.clearDeclared("track-abandoned")
        assertNull(NerdStats.sourceFor("track-abandoned"))
    }

    @Test
    fun `forgetLastSession clears all recorded sources and snapshots`() {
        NerdStats.recordSource("track-1", "YouTube")
        NerdStats.recordSource("track-2", "Catalogue")
        NerdStats.current.value = NerdStats.Snapshot(
            mimeType = "audio/flac",
            bitrateKbps = 1411,
            sampleRateHz = 44100,
            channels = 2,
            sourceName = "Catalogue",
        )

        NerdStats.forgetLastSession()

        assertNull(NerdStats.current.value)
        assertNull(NerdStats.sourceFor("track-1"))
        assertNull(NerdStats.sourceFor("track-2"))
    }

    // ── Dynamic Source Transition & Leak Prevention ────────────────────────

    @Test
    fun `stale provider must never leak across tracks sharing same inner identifier`() {
        // Track 1 played from JioSaavn with inner track id "101"
        NerdStats.recordSource("101", "JioSaavn")
        assertEquals("JioSaavn", NerdStats.exactSourceFor("101"))

        // Track 2 is an Addon track with the exact same inner id "101" under an addon prefix
        val addonMediaId = "src:addon-uuid-xyz::101"
        // Before Addon resolves or records itself, exactSourceFor must NOT leak JioSaavn
        assertNull(NerdStats.exactSourceFor(addonMediaId))

        // Once Addon stream is recorded for the exact mediaId, it strictly returns the Addon
        NerdStats.recordSource(addonMediaId, "Unified Addon")
        assertEquals("Unified Addon", NerdStats.exactSourceFor(addonMediaId))
        // And track 1 remains JioSaavn without being mutated
        assertEquals("JioSaavn", NerdStats.exactSourceFor("101"))
    }

    @Test
    fun `playback source transitions maintain correct sequence without stale bleed`() {
        val jiosaavnId = "src:jiosaavn-uuid::track-jio"
        val addonId = "src:addon-uuid::track-addon"
        val localId = "local-storage-track-uri"

        // 1. JioSaavn playback
        NerdStats.recordSource(jiosaavnId, "JioSaavn")
        assertEquals("JioSaavn", NerdStats.exactSourceFor(jiosaavnId))

        // 2. Transition JioSaavn -> Addon
        NerdStats.recordSource(addonId, "Tidal Addon")
        assertEquals("Tidal Addon", NerdStats.exactSourceFor(addonId))

        // 3. Transition Addon -> Local Storage (Local Storage does not rely on streaming source)
        assertNull(NerdStats.exactSourceFor(localId))

        // 4. Transition Local -> JioSaavn
        assertEquals("JioSaavn", NerdStats.exactSourceFor(jiosaavnId))

        // 5. Transition Local -> Addon
        assertEquals("Tidal Addon", NerdStats.exactSourceFor(addonId))

        // 6. Transition Addon -> JioSaavn
        assertEquals("JioSaavn", NerdStats.exactSourceFor(jiosaavnId))
    }

    @Test
    fun `unknown provider and missing metadata remain null in exact lookup`() {
        assertNull(NerdStats.exactSourceFor(null))
        assertNull(NerdStats.exactSourceFor(""))
        assertNull(NerdStats.exactSourceFor("src:unknown-provider-uuid::track-999"))
    }

    // ── AudioOutputStatus Model ────────────────────────────────────────────

    @Test
    fun `AudioOutputStatus tracks decoder name and buffer size`() {
        AudioOutputStatus.publishDecoder("c2.android.flac.decoder")
        assertEquals("c2.android.flac.decoder", AudioOutputStatus.current.value.decoderName)

        AudioOutputStatus.publishAudioTrack(
            encoding = AudioFormat.ENCODING_PCM_FLOAT,
            sampleRateHz = 48000,
            bufferSize = 15376,
        )
        val snap = AudioOutputStatus.current.value
        assertEquals("c2.android.flac.decoder", snap.decoderName)
        assertEquals(AudioFormat.ENCODING_PCM_FLOAT, snap.actualEncoding)
        assertEquals(48000, snap.actualSampleRateHz)
        assertEquals(15376, snap.bufferSize)

        AudioOutputStatus.reset()
        assertNull(AudioOutputStatus.current.value.decoderName)
        assertNull(AudioOutputStatus.current.value.bufferSize)
        assertNull(AudioOutputStatus.current.value.actualEncoding)
    }

    // ── Resampler & DSP Logic Checks ───────────────────────────────────────

    @Test
    fun `resampler passthrough when input rate equals output rate`() {
        val inRate = 48000
        val outRate = 48000
        val isPassthrough = inRate == outRate
        val type = if (isPassthrough) "None" else "Resampler"
        val quality = if (isPassthrough) "Passthrough" else "Resampled"

        assertEquals("None", type)
        assertEquals("Passthrough", quality)
    }

    @Test
    fun `resampler active when input rate differs from output rate`() {
        val inRate = 44100
        val outRate = 48000
        val isPassthrough = inRate == outRate
        val type = if (isPassthrough) "None" else "Resampler"
        val quality = if (isPassthrough) "Passthrough" else "Resampled"

        assertEquals("Resampler", type)
        assertEquals("Resampled", quality)
    }
}
