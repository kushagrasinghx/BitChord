package com.music.bitchord

import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.data.settings.permits
import com.music.bitchord.data.sources.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which sources each streaming ceiling pays for — the rule every playback
 * walk filters on, and the one a lowered ceiling has to take back from
 * whatever was already pinned or found under the old one.
 */
class AudioQualityCeilingTest {

    private val lossless = SourceKind.entries.filter { it.canServeLossless }

    @Test
    fun `lossless asks every source`() {
        SourceKind.entries.forEach { assertTrue(it.name, AudioQuality.LOSSLESS.permits(it)) }
    }

    @Test
    fun `high skips every source that can serve lossless and keeps JioSaavn`() {
        lossless.forEach { assertFalse(it.name, AudioQuality.HIGH.permits(it)) }
        assertTrue(AudioQuality.HIGH.permits(SourceKind.JIOSAAVN))
        assertTrue(AudioQuality.HIGH.permits(SourceKind.YOUTUBE))
    }

    @Test
    fun `medium and low leave YouTube alone`() {
        for (ceiling in listOf(AudioQuality.MEDIUM, AudioQuality.LOW)) {
            SourceKind.entries.forEach { kind ->
                assertEquals("$ceiling/$kind", kind == SourceKind.YOUTUBE, ceiling.permits(kind))
            }
        }
    }

    @Test
    fun `a lower rung never permits a source a higher one skips`() {
        val rungs = AudioQuality.entries
        for (i in rungs.indices) for (j in i + 1 until rungs.size) {
            SourceKind.entries.forEach { kind ->
                if (rungs[i].permits(kind)) assertTrue("${rungs[j]} / $kind", rungs[j].permits(kind))
            }
        }
    }

    @Test
    fun `the watch override caps a lossless ceiling`() {
        val wifi = AppSettings.audioQualityWifi.value
        val metered = AppSettings.meteredConnection.value
        val override = AppSettings.streamCeilingOverride.value
        try {
            AppSettings.meteredConnection.value = false
            AppSettings.audioQualityWifi.value = AudioQuality.LOSSLESS
            AppSettings.streamCeilingOverride.value = AudioQuality.MEDIUM
            assertEquals(AudioQuality.MEDIUM, AppSettings.effectiveAudioQuality)
            AppSettings.audioQualityWifi.value = AudioQuality.LOW
            assertEquals(AudioQuality.LOW, AppSettings.effectiveAudioQuality)
        } finally {
            AppSettings.audioQualityWifi.value = wifi
            AppSettings.meteredConnection.value = metered
            AppSettings.streamCeilingOverride.value = override
        }
    }
}
