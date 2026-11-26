@file:OptIn(ExperimentalJsExport::class)

package com.music.bitchord.web

import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.QueueBuilder
import com.music.bitchord.playback.QueueTimeline
import com.music.bitchord.playback.SleepTimerSteps
import com.music.bitchord.playback.autoplaySectionStart
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

// QueueTimeline.kt and QueueSections.kt take tiers; the web names them by its own strings.
private fun tiersOf(tiers: Array<String>): List<QueueTier>? = tiers.map {
    when (it) {
        "context" -> QueueTier.CONTEXT
        "user" -> QueueTier.USER_QUEUE
        "autoplay" -> QueueTier.AUTOPLAY
        else -> return null
    }
}

/** QueueTimeline.kt `JumpOrderPlan`: the new timeline as indices into the old one. */
@JsExport
class QueueJump(val isPureSeek: Boolean, val order: Array<Int>, val newIndex: Int, val promoteTarget: Boolean)

/** QueueTimeline.kt `computeJumpOrder`: what tapping the row at [targetIndex] does to the queue. */
@JsExport
fun queueJumpOrder(tiers: Array<String>, currentIndex: Int, targetIndex: Int): QueueJump? {
    val plan = QueueTimeline.computeJumpOrder(tiersOf(tiers) ?: return null, currentIndex, targetIndex) ?: return null
    return QueueJump(plan.isPureSeek, plan.newOrderIndices.toTypedArray(), plan.newTargetIndex, plan.promoteTargetToContext)
}

/** QueueTimeline.kt `upcomingUserQueueIndices`: the hand-queued rows "Clear" removes. */
@JsExport
fun queueUpcomingUserIndices(tiers: Array<String>, currentIndex: Int): Array<Int> =
    QueueTimeline.upcomingUserQueueIndices(tiersOf(tiers).orEmpty(), currentIndex).toTypedArray()

/** QueueTimeline.kt `playedUserQueueIndices`: played hand-queued rows to prune once the context plays again. */
@JsExport
fun queuePlayedUserIndices(tiers: Array<String>, currentIndex: Int): Array<Int> =
    QueueTimeline.playedUserQueueIndices(tiersOf(tiers).orEmpty(), currentIndex).toTypedArray()

/** QueueTimeline.kt `sectionedOrder`: restored upcoming rows put back in their sections. */
@JsExport
fun queueSectionedOrder(restored: Array<Int>, upcoming: Array<String>): Array<Int> =
    QueueTimeline.sectionedOrder(restored.toList(), tiersOf(upcoming).orEmpty()).toTypedArray()

/** QueueSections.kt `autoplaySectionStart`: the first AutoPlay row still to come. */
@JsExport
fun queueAutoplaySectionStart(tiers: Array<String>, currentIndex: Int): Int =
    autoplaySectionStart(tiers.map { it == "autoplay" }, currentIndex)

/** A track as QueueBuilder.kt reads one: its id, title, byline, and whether it's a music video. */
@JsExport
class StationTrack(val videoId: String, val title: String, val artist: String, val isVideo: Boolean)

private fun StationTrack.song() = Song(videoId, title, artist, thumbnailUrl = null, isVideo = isVideo)

/** QueueBuilder.kt `extend`: which of [candidates] to append after [existing], as indices into [candidates]. */
@JsExport
fun stationExtend(existing: Array<StationTrack>, candidates: Array<StationTrack>, limit: Int, preferVideos: Boolean): Array<Int> {
    val songs = candidates.map { it.song() }
    return QueueBuilder.extend(existing.map { it.song() }, songs, limit, preferVideos)
        .map { picked -> songs.indexOfFirst { it === picked } }
        .toTypedArray()
}

/** QueueBuilder.kt `isSameRecording`: the audio and video cuts of one track. */
@JsExport
fun stationSameRecording(a: StationTrack, b: StationTrack): Boolean = QueueBuilder.isSameRecording(a.song(), b.song())

/** QueueBuilder.kt `normalisedTitle`. */
@JsExport
fun stationTitle(raw: String): String = QueueBuilder.normalisedTitle(raw)

/** QueueBuilder.kt `artistSet`. */
@JsExport
fun stationArtists(raw: String): Array<String> = QueueBuilder.artistSet(raw).toTypedArray()
