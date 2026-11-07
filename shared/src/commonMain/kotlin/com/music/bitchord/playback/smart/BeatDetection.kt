// Chunking adapted from CPJKU/beat_this (MIT), Copyright (c) 2024 Institute
// of Computational Perception, JKU Linz. See native/analyzer/BEAT_THIS_LICENSE.
package com.music.bitchord.playback.smart

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Pure postprocessing for Beat This!, shared by Android and desktop. */
object BeatDetection {
    const val FRAME_RATE = 50.0
    const val CHUNK_FRAMES = 1500
    const val BORDER_FRAMES = 6
    const val MIN_BEATS = 8

    /** A model input; negative [startFrame] means zero padding before the spectrogram. */
    data class Chunk(val startFrame: Int, val frameCount: Int)

    /**
     * CPJKU's split_piece: pad the outer borders and shift the final chunk left
     * to avoid an inference over a tiny tail. Aggregation keeps the first prediction
     * in overlaps and discards six frames from BOTH ends of every chunk.
     */
    fun chunks(frames: Int): List<Chunk> {
        if (frames <= 0) return emptyList()
        val starts = mutableListOf<Int>()
        var start = -BORDER_FRAMES
        while (start < frames - BORDER_FRAMES) {
            starts += start
            start += CHUNK_FRAMES - 2 * BORDER_FRAMES
        }
        if (frames > CHUNK_FRAMES - 2 * BORDER_FRAMES) {
            starts[starts.lastIndex] = frames - (CHUNK_FRAMES - BORDER_FRAMES)
        }
        return starts.map { from ->
            val end = min(from + CHUNK_FRAMES, frames)
            val leftPad = max(0, -from)
            val rightPad = min(BORDER_FRAMES, max(0, from + CHUNK_FRAMES - frames))
            Chunk(from, end - max(0, from) + leftPad + rightPad)
        }
    }

    /** Finite, positive local maxima; a flat run is one beat, refined below the 20 ms hop. */
    fun pickPeaks(logits: FloatArray): List<Double> {
        val peaks = ArrayList<Int>()
        for (index in logits.indices) {
            val value = logits[index]
            if (!value.isFinite() || value <= 0f) continue
            if ((max(0, index - 3)..min(logits.lastIndex, index + 3)).none {
                    logits[it].isFinite() && logits[it] > value
                }) peaks += index
        }
        val result = ArrayList<Double>()
        var index = 0
        while (index < peaks.size) {
            val first = peaks[index]
            var last = first
            while (index + 1 < peaks.size && peaks[index + 1] == last + 1) {
                last = peaks[++index]
            }
            if (last > first) {
                result += (first + last) / 2.0
            } else {
                var position = first.toDouble()
                if (first > 0 && first < logits.lastIndex) {
                    val left = logits[first - 1].toDouble()
                    val centre = logits[first].toDouble()
                    val right = logits[first + 1].toDouble()
                    val curvature = left - 2 * centre + right
                    if (left.isFinite() && right.isFinite() && curvature < -1e-9) {
                        position += (0.5 * (left - right) / curvature).coerceIn(-0.5, 0.5)
                    }
                }
                result += position
            }
            index++
        }
        return result
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2 else sorted[middle]
    }

    private fun gaps(beats: List<Double>): List<Double>? {
        if (beats.size < MIN_BEATS || beats.any { !it.isFinite() }) return null
        return beats.zipWithNext { left, right -> right - left }
            .takeIf { gaps -> gaps.all { it.isFinite() && it > 0 } }
    }

    /** Median interval, robust to a few dropped beats. Invalid or unordered evidence has no tempo. */
    fun tempoFromBeats(beats: List<Double>): Double {
        val gaps = gaps(beats) ?: return 0.0
        val rough = median(gaps)
        val kept = gaps.filter { abs(it - rough) <= rough * 0.2 }
        val interval = median(if (kept.size >= 4) kept else gaps)
        val bpm = 60 / interval
        return bpm.takeIf { it in 40.0..220.0 } ?: 0.0
    }

    /**
     * Regularity alone is not confidence: a logit near zero is an undecided
     * model, even when its peaks are periodic. Score the evidence above that
     * decision boundary, with fewer than 16 beats reducing support. This is a
     * conservative mixing score, not a calibrated probability of correctness.
     */
    fun gridConfidence(beats: List<Double>, peakLogits: List<Double>): Double {
        val gaps = gaps(beats) ?: return 0.0
        if (peakLogits.size != beats.size || peakLogits.any { !it.isFinite() }) return 0.0
        if (tempoFromBeats(beats) <= 0) return 0.0
        val interval = median(gaps)
        val regular = gaps.count { abs(it - interval) <= interval * 0.1 }.toDouble() / gaps.size
        val strength = (2 / (1 + exp(-median(peakLogits))) - 1).coerceIn(0.0, 1.0)
        val support = min(1.0, beats.size / 16.0)
        return (0.95 * regular * strength * support).coerceIn(0.0, 0.95)
    }

    /** Never move a stray downbeat onto a distant beat, especially across a beatless break. */
    fun snapDownbeats(beats: List<Double>, downbeats: List<Double>): List<Double> {
        val bpm = tempoFromBeats(beats)
        if (bpm <= 0) return emptyList()
        val tolerance = min(2 / FRAME_RATE, 0.1 * 60 / bpm)
        return downbeats.filter { it.isFinite() }.mapNotNull { time ->
            beats.minByOrNull { abs(it - time) }?.takeIf { abs(it - time) <= tolerance + 1e-9 }
        }.distinct().sorted()
    }
}
