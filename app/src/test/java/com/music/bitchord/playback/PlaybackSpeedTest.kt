package com.music.bitchord.playback

import com.music.bitchord.data.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.roundToInt

class PlaybackSpeedTest {

    @Test
    fun playbackSpeedBoundsAreValid() {
        assertEquals(0.5f, AppSettings.MIN_PLAYBACK_SPEED, 0.001f)
        assertEquals(2.0f, AppSettings.MAX_PLAYBACK_SPEED, 0.001f)
    }

    @Test
    fun playbackSpeedClampingRespectsRange() {
        val clamp = { v: Float -> v.coerceIn(AppSettings.MIN_PLAYBACK_SPEED, AppSettings.MAX_PLAYBACK_SPEED) }
        assertEquals(0.5f, clamp(0.2f), 0.001f)
        assertEquals(0.5f, clamp(-1.0f), 0.001f)
        assertEquals(2.0f, clamp(2.5f), 0.001f)
        assertEquals(2.0f, clamp(5.0f), 0.001f)
        assertEquals(1.25f, clamp(1.25f), 0.001f)
        assertEquals(1.0f, clamp(1.0f), 0.001f)
    }

    @Test
    fun playbackSpeedSteppingQuantization() {
        // Slider uses 0.05 step quantization
        val quantize = { v: Float -> (v * 20).roundToInt() / 20f }
        assertEquals(1.0f, quantize(1.01f), 0.001f)
        assertEquals(1.25f, quantize(1.24f), 0.001f)
        assertEquals(1.5f, quantize(1.52f), 0.001f)
        assertEquals(0.75f, quantize(0.76f), 0.001f)
    }

    @Test
    fun playbackSpeedPresetsCoverStandardListeningSpeeds() {
        val presets = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        presets.forEach { speed ->
            assert(speed in AppSettings.MIN_PLAYBACK_SPEED..AppSettings.MAX_PLAYBACK_SPEED)
        }
    }
}
