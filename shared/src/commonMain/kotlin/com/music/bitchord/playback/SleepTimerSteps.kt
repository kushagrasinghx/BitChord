package com.music.bitchord.playback

import kotlin.math.roundToInt

/**
 * The sleep timer's arithmetic, apart from its clock: the durations offered and
 * the fade before the pause. Pure Kotlin, so the web player runs these same
 * rules (shared/src/jsMain/.../web/QueueWeb.kt); Android's [SleepTimer] hands
 * its members straight through to here.
 */
object SleepTimerSteps {

    /** Durations offered in the picker. */
    val PRESETS = listOf(15, 30, 45, 60)

    /** The custom picker moves in steps this size, from one step up to [MAX_MINUTES]. */
    const val STEP_MINUTES = 5

    /** Longest custom duration: twelve hours. */
    const val MAX_MINUTES = 720

    /** The custom duration a slider at [fraction] (0..1) of its travel stands for. */
    fun minutesAt(fraction: Float): Int {
        val raw = STEP_MINUTES + fraction.coerceIn(0f, 1f) * (MAX_MINUTES - STEP_MINUTES)
        return (raw / STEP_MINUTES).roundToInt() * STEP_MINUTES
    }

    /** Where [minutes] sits along the custom slider, the inverse of [minutesAt]. */
    fun fractionOf(minutes: Int): Float =
        (minutes - STEP_MINUTES).toFloat() / (MAX_MINUTES - STEP_MINUTES)

    /** Duration of the volume fade-out before playback stops (30 seconds). */
    const val FADE_OUT_DURATION_MS = 30_000L

    /**
     * Volume multiplier (0.0f..1.0f) for [remainingMs] milliseconds before deadline.
     * When fade is active and under [fadeDurationMs], volume decreases linearly to 0.
     */
    fun calculateFadeVolume(
        remainingMs: Long,
        fadeDurationMs: Long = FADE_OUT_DURATION_MS,
        fadeOutEnabled: Boolean = true,
    ): Float {
        if (!fadeOutEnabled || remainingMs >= fadeDurationMs) return 1f
        if (remainingMs <= 0L || fadeDurationMs <= 0L) return 0f
        return (remainingMs.toFloat() / fadeDurationMs.toFloat()).coerceIn(0f, 1f)
    }
}
