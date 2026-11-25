package com.music.bitchord.wear.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.music.bitchord.wear.data.NowPlaying

/**
 * The watch's motor, with a short pattern for each playback event.
 *
 * These are deliberately briefer than the phone's: the watch sits against the
 * wrist, and a pattern long enough to read on a phone becomes a jolt there.
 */
class WearHaptics internal constructor(context: Context) {
    private val app = context.applicationContext

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val pauseEffect = VibrationEffect.createWaveform(
        longArrayOf(0L, 22L, 38L, 16L),
        intArrayOf(0, 200, 0, 120),
        -1,
    )
    private val resumeEffect = VibrationEffect.createWaveform(
        longArrayOf(0L, 14L, 22L, 20L),
        intArrayOf(0, 120, 0, 200),
        -1,
    )
    private val trackEffect = VibrationEffect.createWaveform(
        longArrayOf(0L, 16L, 42L, 16L),
        intArrayOf(0, 150, 0, 100),
        -1,
    )

    fun pause() = play(pauseEffect)

    fun resume() = play(resumeEffect)

    fun trackChange() = play(trackEffect)

    private fun play(effect: VibrationEffect) {
        val motor = vibrator ?: return
        if (!motor.hasVibrator()) return
        // The watch has the same system-wide haptic switch as the phone. Read
        // it per event rather than caching: it can be changed while the player
        // screen is open.
        val enabled = runCatching {
            Settings.System.getInt(
                app.contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED,
                1,
            ) != 0
        }.getOrDefault(true)
        if (!enabled) return
        motor.vibrate(effect)
    }
}

@Composable
fun rememberWearHaptics(): WearHaptics {
    val context = LocalContext.current
    return remember(context) { WearHaptics(context) }
}

/**
 * Buzzes once when the phone's playback pauses or resumes, and once when the
 * track changes. The first now-playing state only establishes a baseline: a
 * watch that has just connected has not seen a pause, and should not claim it
 * felt one.
 */
@Composable
fun PlaybackHapticsEffect(np: NowPlaying) {
    val haptics = rememberWearHaptics()
    val ambient = LocalAmbient.current
    var lastMediaId by remember { mutableStateOf<String?>(null) }
    var lastPlaying by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(np.mediaId, np.playing, ambient.active) {
        val previousMediaId = lastMediaId
        val previousPlaying = lastPlaying
        lastMediaId = np.mediaId
        lastPlaying = np.playing
        if (previousMediaId == null || previousPlaying == null || ambient.active) return@LaunchedEffect
        if (previousMediaId != np.mediaId) {
            haptics.trackChange()
        } else if (previousPlaying != np.playing) {
            if (np.playing) haptics.resume() else haptics.pause()
        }
    }
}
