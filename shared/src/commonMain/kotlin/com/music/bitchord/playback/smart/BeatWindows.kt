package com.music.bitchord.playback.smart

import kotlin.math.max
import kotlin.math.min

/** One successful model run, including the actual decoded window bounds. */
data class BeatWindow(val start: Double, val seconds: Double, val grid: BeatSide)

/**
 * Stitch overlapping windows at their midpoint, then score only the requested
 * part of the song. A strong outro must never lend confidence to a weak intro.
 */
fun combineBeatWindows(windows: List<BeatWindow>, from: Double, to: Double): BeatSide? {
    val ordered = windows.filter { it.start.isFinite() && it.seconds.isFinite() && it.seconds > 0 }.sortedBy { it.start }
    val beats = ArrayList<Double>()
    val downbeats = ArrayList<Double>()
    var confidenceSum = 0.0
    for ((index, window) in ordered.withIndex()) {
        val previous = ordered.getOrNull(index - 1)
        val next = ordered.getOrNull(index + 1)
        val begin = previous?.let { (it.start + it.seconds + window.start) / 2 }
            ?.takeIf { it > window.start } ?: window.start
        val end = next?.let { (window.start + window.seconds + it.start) / 2 }
            ?.takeIf { it < window.start + window.seconds } ?: window.start + window.seconds
        val kept = window.grid.beats.filter { it >= max(begin, from) && it < end && it <= to }
        beats += kept
        downbeats += window.grid.downbeats.filter { it in kept }
        val confidence = window.grid.confidence.takeIf { it.isFinite() }?.coerceIn(0.0, 0.95) ?: 0.0
        confidenceSum += confidence * kept.size
    }
    beats.sort()
    val bpm = BeatDetection.tempoFromBeats(beats)
    if (bpm <= 0) return null
    // A seam or tempo change can make individually good windows an unreliable combined grid.
    val timingLimit = BeatDetection.gridConfidence(beats, List(beats.size) { 100.0 })
    return BeatSide(beats, BeatDetection.snapDownbeats(beats, downbeats), bpm,
        min(confidenceSum / beats.size, timingLimit))
}
