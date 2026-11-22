@file:OptIn(ExperimentalJsExport::class)

package com.music.bitchord.web

import com.music.bitchord.playback.SleepTimerSteps
import com.music.bitchord.playback.lastPlayedWindow as playbackLastPlayedWindow
import com.music.bitchord.playback.queueHistoryTrimCount as playbackQueueHistoryTrimCount

/** QueueHistoryWindow.kt `queueHistoryTrimCount`: how many entries before [currentIndex] fall out of the 25 kept. */
@JsExport
fun queueHistoryTrimCount(currentIndex: Int): Int = playbackQueueHistoryTrimCount(currentIndex)

/** QueueHistoryWindow.kt `lastPlayedWindow`, as [start, end): what a restart snapshot of a [size]-entry queue keeps. */
@JsExport
fun lastPlayedWindow(size: Int, index: Int): Array<Int> =
    playbackLastPlayedWindow(size, index).let { if (it.isEmpty()) arrayOf(0, 0) else arrayOf(it.first, it.last + 1) }

/** SleepTimerSteps.kt: the presets, the custom picker's step and ceiling, and the fade's length. */
@JsExport
class SleepTimerOptions(val presets: Array<Int>, val stepMinutes: Int, val maxMinutes: Int, val fadeOutMs: Double)

@JsExport
fun sleepTimerOptions(): SleepTimerOptions = SleepTimerOptions(
    SleepTimerSteps.PRESETS.toTypedArray(),
    SleepTimerSteps.STEP_MINUTES,
    SleepTimerSteps.MAX_MINUTES,
    SleepTimerSteps.FADE_OUT_DURATION_MS.toDouble(),
)

/** SleepTimerSteps.kt `calculateFadeVolume`: the volume multiplier [remainingMs] before the timer pauses. */
@JsExport
fun sleepFadeVolume(remainingMs: Double, fadeDurationMs: Double, fadeOutEnabled: Boolean): Double =
    SleepTimerSteps.calculateFadeVolume(remainingMs.toLong(), fadeDurationMs.toLong(), fadeOutEnabled).toDouble()
