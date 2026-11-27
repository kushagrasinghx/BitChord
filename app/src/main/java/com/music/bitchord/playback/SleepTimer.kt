package com.music.bitchord.playback

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Sleep timer. Holds a deadline; [PlaybackService] watches it and pauses
 * playback once it passes.
 *
 * A deadline rather than a countdown: nothing has to tick for the timer to
 * stay accurate, so it survives the player UI being dismissed, and any
 * observer can work out how long is left for itself. elapsedRealtime, not
 * wall clock, so changing the system time can't cut a timer short.
 */
object SleepTimer {

    /** Deadline on [SystemClock.elapsedRealtime], or null when no timer is set. */
    val deadline = MutableStateFlow<Long?>(null)

    /** The preset that was chosen, so the picker can tick it. Null when off. */
    val minutes = MutableStateFlow<Int?>(null)

    /**
     * Pause when the current track ends instead of after a fixed wait.
     *
     * Deliberately not expressed as a deadline of "duration minus position":
     * seeking, crossfade and a queue that reorders itself would all leave that
     * number wrong, whereas the track ending is an event the player reports.
     */
    val afterTrack = MutableStateFlow(false)

    // The durations and the fade are plain arithmetic, in shared/commonMain
    // (SleepTimerSteps.kt) so the web player's timer follows the same rules.

    /** Durations offered in the picker. */
    val PRESETS = SleepTimerSteps.PRESETS

    /** The custom picker moves in steps this size, from one step up to [MAX_MINUTES]. */
    const val STEP_MINUTES = SleepTimerSteps.STEP_MINUTES

    /** Longest custom duration: twelve hours. */
    const val MAX_MINUTES = SleepTimerSteps.MAX_MINUTES

    /** The custom duration a slider at [fraction] (0..1) of its travel stands for. */
    fun minutesAt(fraction: Float): Int = SleepTimerSteps.minutesAt(fraction)

    /** Where [minutes] sits along the custom slider, the inverse of [minutesAt]. */
    fun fractionOf(minutes: Int): Float = SleepTimerSteps.fractionOf(minutes)

    /** Whether any kind of timer is currently armed. */
    val isRunning: Boolean get() = deadline.value != null || afterTrack.value

    fun start(minutes: Int) {
        afterTrack.value = false
        this.minutes.value = minutes
        deadline.value = SystemClock.elapsedRealtime() + minutes * 60_000L
    }

    /** Pause once the track playing right now finishes. */
    fun startAfterTrack() {
        minutes.value = null
        deadline.value = null
        afterTrack.value = true
    }

    fun cancel() {
        minutes.value = null
        deadline.value = null
        afterTrack.value = false
    }

    /** How long is left, or null when no timer is running. */
    fun remainingMs(): Long? =
        deadline.value?.let { (it - SystemClock.elapsedRealtime()).coerceAtLeast(0L) }

    /** Duration of the volume fade-out before playback stops (30 seconds). */
    const val FADE_OUT_DURATION_MS = SleepTimerSteps.FADE_OUT_DURATION_MS

    /**
     * Volume multiplier (0.0f..1.0f) for [remainingMs] milliseconds before deadline.
     * When fade is active and under [fadeDurationMs], volume decreases linearly to 0.
     */
    fun calculateFadeVolume(
        remainingMs: Long,
        fadeDurationMs: Long = FADE_OUT_DURATION_MS,
        fadeOutEnabled: Boolean = true,
    ): Float = SleepTimerSteps.calculateFadeVolume(remainingMs, fadeDurationMs, fadeOutEnabled)
}
