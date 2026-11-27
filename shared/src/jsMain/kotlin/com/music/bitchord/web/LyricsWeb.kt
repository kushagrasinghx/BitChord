@file:OptIn(ExperimentalJsExport::class)

package com.music.bitchord.web

import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.ui.player.LyricClockReading
import com.music.bitchord.ui.player.LyricsShareLine
import com.music.bitchord.ui.player.ShareTimeline
import com.music.bitchord.ui.player.fitsOnCard
import com.music.bitchord.ui.player.pickedShareLines
import com.music.bitchord.ui.player.LyricClock as PlayerLyricClock
import com.music.bitchord.ui.player.shareTimeline as playerShareTimeline

/**
 * LyricClock.kt, the Android app's lyric playhead: steered by the player's
 * readings rather than set by them, and holding the sweep where it was through
 * a pause and its resume. Times are milliseconds on one clock (`performance.now()`).
 */
@JsExport
class LyricClock(startMs: Double) {
    private val clock = PlayerLyricClock(startMs.toLong())

    /** Where the lyrics are drawn, in song milliseconds. */
    val displayedMs: Double get() = clock.displayedMs

    /** Frames stopped arriving (paused, or the tab hidden): the next [frame] starts a new run. */
    fun restart() = clock.restart()

    /**
     * LyricClock.observe: a reading outside the frame loop. Stopped, it is held, keeping the
     * sweep where it was unless the playhead jumped ([discontinuity] changed); returns where to
     * draw, or null for a playing reading, which the frame loop draws.
     */
    fun observe(nowMs: Double, positionMs: Double, sampledAtMs: Double, discontinuity: Double, isPlaying: Boolean): Double? =
        clock.observe(reading(positionMs, sampledAtMs, discontinuity, isPlaying), nowMs)?.toDouble()

    /** LyricClock.frame: advance to the frame at [nowMs] and return where the lyrics should be drawn. */
    fun frame(nowMs: Double, positionMs: Double, sampledAtMs: Double, discontinuity: Double, isPlaying: Boolean): Double =
        clock.frame(nowMs, reading(positionMs, sampledAtMs, discontinuity, isPlaying)).toDouble()

    private fun reading(positionMs: Double, sampledAtMs: Double, discontinuity: Double, isPlaying: Boolean) =
        LyricClockReading(positionMs.toLong(), sampledAtMs, discontinuity.toLong(), isPlaying)
}

/** LyricsShareLines.kt `fitsOnCard`: whether a line of [text] still fits a card holding [taken] characters. */
@JsExport
fun fitsOnShareCard(taken: Int, text: String?): Boolean = fitsOnCard(taken, text)

/** A lyric line, as much of one as the share reads: its stamp, its text and its stated end. */
@JsExport
class ShareSourceLine(val timeMs: Double, val text: String, val sungUntilMs: Double?)

/**
 * LyricsShareLines.kt `LyricsShareLine`: a line on the card, or the "(...)" of a skip.
 * [source] is the picked line's index in the lyrics, for its word timings; -1 for a skip
 * and for unsynced lyrics.
 */
@JsExport
class ShareLine(val text: String, val isGap: Boolean, val startMs: Double?, val endMs: Double?, val source: Int)

/** LyricsShareLines.kt `pickedShareLines`: what picking [picks] out of [lines] puts on the card. */
@JsExport
fun pickShareLines(lines: Array<ShareSourceLine>, picks: Array<Int>): Array<ShareLine> {
    val source = lines.map { LyricLine(it.timeMs.toLong(), it.text, sungUntilMs = it.sungUntilMs?.toLong()) }
    return pickedShareLines(source, null, picks.toSet()).map { line ->
        ShareLine(
            line.text,
            line.isGap,
            line.startMs?.toDouble(),
            line.endMs?.toDouble(),
            source.indexOfFirst { it === line.timing },
        )
    }.toTypedArray()
}

/** One stretch of the track the share video plays, in track milliseconds. */
@JsExport
class ShareSegment(val startMs: Double, val endMs: Double)

/** LyricsShareLines.kt `ShareTimeline`: the stretches the video plays back to back. */
@JsExport
class ShareVideoTimeline internal constructor(private val timeline: ShareTimeline) {
    val durationMs: Double get() = timeline.durationMs.toDouble()
    val segments: Array<ShareSegment>
        get() = timeline.segments.map { (start, end) -> ShareSegment(start.toDouble(), end.toDouble()) }.toTypedArray()

    /** Where in the track the video is [outputMs] in. */
    fun trackMsAt(outputMs: Double): Double = timeline.trackMsAt((outputMs * 1000).toLong()).toDouble()
}

/** LyricsShareLines.kt `shareTimeline`: the video's timeline for [lines], or null when they carry no timing. */
@JsExport
fun shareTimeline(lines: Array<ShareLine>): ShareVideoTimeline? =
    playerShareTimeline(
        lines.map { LyricsShareLine(it.text, null, it.isGap, it.startMs?.toLong(), it.endMs?.toLong()) },
    )?.let(::ShareVideoTimeline)
