/*
 * BitChord: the Automix loop mix, a DJ transition on two beat-tracked songs.
 * Ported from the web player's playback/automix/engine/loopMix.ts (which
 * builds on AutoMix by David Finnegan, AGPL-3.0; see that folder's NOTICE.md).
 *
 * Copyright (C) 2026 Kushagra Singh
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version. Combined into BitChord (GPL-3.0-or-later) under
 * section 13 of the AGPL.
 */

package com.music.bitchord.playback.smart

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The loop mix: the outgoing song hands over to a deck at the start of an
 * instrumental loop near its end and loops it. The incoming song comes in on
 * its own instrumental loop, bass cut, at the outgoing tempo. One master tempo
 * then ramps from the outgoing song's BPM to the incoming one's, both decks
 * following it beat by beat, so they stay locked however far apart they
 * started. The bass swaps, the outgoing loop drains away through a rising
 * high-pass, the incoming loop lets go into the rest of its song at its own
 * tempo, and its player takes over two bars later.
 *
 * Everything is placed on one beat clock: beat k of the mix (in outgoing
 * beats) is at [Clock.beatTimes]`[k]` seconds after t0, and each deck's
 * position at beat k is worked out from the loop it is in, so nothing drifts.
 *
 * Pure: no audio, no players. [plan] picks the loops from stored analysis;
 * [Clock] lays the plan out in time; the renderer and the controller both read
 * the clock.
 */
object LoopMix {
    /** The longest loop, in each song's own bars; shorter ones are tried when no 4 clean bars exist. */
    const val LOOP_BARS = 4

    /** Loop lengths tried, longest first: a 1-bar instrumental loop beats a 4-bar one with a word in it. */
    private val LOOP_LENGTHS = intArrayOf(4, 2, 1)

    /** Bars the incoming loop takes to come in at the outgoing tempo. */
    const val ENTER_BARS = 4

    /** Bars the outgoing loop takes to drain away once the tempo has arrived. */
    const val EXIT_BARS = 2

    /** Bars after the incoming loop lets go before its own player takes over. */
    const val SETTLE_BARS = 2

    /** The fastest the master tempo moves: past this a ramp is heard as a lurch, not a mix. */
    const val MAX_BPM_PER_BAR = 2.5
    const val MIN_RAMP_BARS = 2
    const val MAX_RAMP_BARS = 24

    /** Share of sung mask frames a loop may still have (a breath), and the longest sung run it may hold. */
    const val MAX_SUNG_SHARE = 0.03
    const val MAX_SUNG_RUN_SEC = 0.2

    /** The vocal windows must cover this share of a loop (and the beat before it), or it is not vouched for. */
    const val MIN_MASK_COVERAGE = 0.8

    /** Clear time before a loop from lyrics: a line still ending as the loop starts would be cut and repeated. */
    const val LYRIC_GUARD_SEC = 0.3

    /** A synced line is padded this much at its start (lead-in breath) and its end (the voice's tail and reverb). */
    const val LYRIC_LEAD_SEC = 0.15
    const val LYRIC_TAIL_SEC = 0.35

    /** A line with no stated end is taken as sung until the next line, at most this long. */
    const val LYRIC_OPEN_LINE_SEC = 10.0

    /**
     * Of a line with no stated end, how much is surely sung: its opening words.
     * Past this the line may already be over (line-synced lyrics only time each
     * line's start), so a loop there is cleared by the vocal mask instead.
     */
    const val OPEN_LINE_CERTAIN_SEC = 2.0

    /** A loop quieter than this share of the stretch around it is a breakdown or a fade, not a groove. */
    const val MIN_LOUDNESS = 0.6

    /** An RMS this low is silence, whatever the stretch around it does. */
    const val SILENT_RMS = 0.01

    /** How far from the end the outgoing loop is looked for, and from the start the incoming one. */
    const val OUT_SEARCH_SEC = 60.0
    const val IN_SEARCH_SEC = 45.0

    /** Element <-> deck handovers: identical audio crossed this quickly is heard as one. */
    const val HANDOVER_SEC = 0.025
    const val BASS_CUT_DB = -18.0
    const val HIGH_PASS_FROM_HZ = 20.0
    const val HIGH_PASS_TO_HZ = 1500.0

    /** What said a loop is instrumental: the lyrics, the vocal mask, or the lyrics with the mask past an open line. */
    enum class Check { LYRICS, MASK, LYRICS_AND_MASK }

    /**
     * A sung stretch from the lyrics. [certainUntil] is how much of it the lyrics
     * actually vouch for: all of it for a line with a stated end, only its first
     * [OPEN_LINE_CERTAIN_SEC] for one without, whose end is a guess (the next
     * line). The guessed tail is left to the vocal mask.
     */
    data class Span(val start: Double, val end: Double, val certainUntil: Double = end)

    /** One synced lyric line, as [sungSpans] reads it. */
    data class Line(val timeMs: Long, val endMs: Long?, val blank: Boolean)

    data class Pick(
        /** Track seconds. */
        val loopStart: Double,
        val loopEnd: Double,
        /** The loop's length in this song's own bars: 4, or 2 or 1 when no 4 clean bars were found. */
        val bars: Int,
        /** Track seconds per beat on this loop: its length over its beats, bar line to bar line. */
        val beatSec: Double,
        val bpm: Double,
        /** Share of the loop that is sung (0..1); 0 when the lyrics vouch for it. */
        val vocal: Double,
        val checkedBy: Check,
    ) {
        val beats: Int get() = bars * 4
    }

    data class Plan(
        val out: Pick,
        val inn: Pick,
        /** Incoming beats per outgoing beat: 1, or 2 / 0.5 when double or half time is closer. */
        val fold: Double,
        val enterBars: Int,
        val rampBars: Int,
        val exitBars: Int,
        /** Outgoing bars from the outgoing loop's start to the incoming loop's release. */
        val totalBars: Int,
    ) {
        /** The tempo the ramp arrives at, in outgoing beats per minute. */
        val targetBpm: Double get() = inn.bpm / fold
    }

    // ---- Choosing the loops ----------------------------------------------------------------

    /**
     * The bars to loop on each side, or null when there is no loop mix to make:
     * either song has no usable grid there, or no instrumental bars at all. A
     * sung word is never looped; the pair then gets a blend or a filter mix.
     *
     * [earliestOutSec] is the earliest the outgoing loop may start (the
     * playhead plus setup time); [sungOut] / [sungIn] are each song's sung
     * stretches from synced lyrics, when it has them ([sungSpans]). A music
     * video ([outVideo] / [inVideo]) is never looped: its audio rarely matches
     * the album cut its lyrics are timed to.
     */
    fun plan(
        out: TrackAnalysis,
        outDuration: Double,
        inn: TrackAnalysis,
        inDuration: Double,
        earliestOutSec: Double,
        sungOut: List<Span>? = null,
        sungIn: List<Span>? = null,
        outVideo: Boolean = false,
        inVideo: Boolean = false,
        /** Filled with why there is no plan, when there isn't one. */
        why: StringBuilder? = null,
    ): Plan? {
        if (outVideo || inVideo) return null.also { why?.append("a music video") }
        val outGrid = constantGrid(out) ?: return null.also { why?.append("no grid on the outgoing song") }
        val inGrid = constantGrid(inn) ?: return null.also { why?.append("no grid on the incoming song") }
        val outCount = Rejections()
        val inCount = Rejections()

        // outgoing: the latest instrumental loop that ends before the song does
        val outEnd = outDuration - 0.5
        val outFirst = max(earliestOutSec, outDuration - OUT_SEARCH_SEC)
        val outPick = firstClean(
            candidates = { bars -> loopsBetween(out.tailBeats, outGrid, outFirst, outEnd, bars).asReversed() },
            analysis = out,
            sung = sungOut,
            beatSec = outGrid.beatSec,
            count = outCount,
        ) ?: return null.also { why?.append("outgoing: ")?.append(outCount) }

        // incoming: the earliest instrumental loop in its opening
        val inEnd = min(IN_SEARCH_SEC + LOOP_BARS * inGrid.barSec, inDuration - 20)
        val inPick = firstClean(
            candidates = { bars -> loopsBetween(inn.headBeats, inGrid, 0.0, inEnd, bars) },
            analysis = inn,
            sung = sungIn,
            beatSec = inGrid.beatSec,
            count = inCount,
        ) ?: return null.also { why?.append("incoming: ")?.append(inCount) }

        val outBeat = (outPick.end - outPick.start) / (outPick.bars * 4)
        val inBeat = (inPick.end - inPick.start) / (inPick.bars * 4)
        val outBpm = 60 / outBeat
        val inBpm = 60 / inBeat

        val fold = doubleArrayOf(1.0, 2.0, 0.5).minBy { abs(ln(inBpm / it / outBpm)) }
        val target = inBpm / fold
        val rampBars = ceil(abs(target - outBpm) / MAX_BPM_PER_BAR).toInt().coerceIn(MIN_RAMP_BARS, MAX_RAMP_BARS)
        // the incoming loop lets go only at its own loop boundary: round the mix up to whole incoming loops
        val inLoopOutBars = inPick.bars / fold
        val totalBars = (ceil((ENTER_BARS + rampBars + EXIT_BARS) / inLoopOutBars) * inLoopOutBars).roundToInt()

        fun pick(clean: Clean, beatSec: Double, bpm: Double) = Pick(
            loopStart = clean.start,
            loopEnd = clean.end,
            bars = clean.bars,
            beatSec = beatSec,
            bpm = bpm,
            vocal = clean.vocal,
            checkedBy = clean.checkedBy,
        )
        return Plan(
            out = pick(outPick, outBeat, outBpm),
            inn = pick(inPick, inBeat, inBpm),
            fold = fold,
            enterBars = ENTER_BARS,
            rampBars = rampBars,
            exitBars = totalBars - ENTER_BARS - rampBars,
            totalBars = totalBars,
        )
    }

    /** The fallback constant grid: a beat length, and a bar line to count from. */
    private class Grid(val beatSec: Double, val anchor: Double) {
        val barSec: Double get() = beatSec * 4
    }

    private fun constantGrid(analysis: TrackAnalysis): Grid? {
        val beat = analysis.beatInterval.takeIf { it.isFinite() && it > 0 }
            ?: analysis.bpm.takeIf { it.isFinite() && it > 0 }?.let { 60 / it }
            ?: return null
        val anchor = analysis.downbeats.firstOrNull { it.isFinite() } ?: analysis.firstBeat
        if (!anchor.isFinite()) return null
        return Grid(beat, anchor)
    }

    private open class Loop(val start: Double, val end: Double)

    private class Clean(start: Double, end: Double, val bars: Int, val vocal: Double, val checkedBy: Check) :
        Loop(start, end)

    /**
     * [bars]-bar loops lying in [from]..[to]. With the model's downbeats, each
     * loop runs from a detected beat 1 to the detected beat 1 [bars] bars on, so
     * its seam is the music's own bar line however the tempo wanders; one whose
     * end lands more than a quarter beat from [bars] bars on (a missed or
     * misplaced downbeat between) is skipped. Without them (or where the model
     * found too few), the constant grid.
     */
    private fun loopsBetween(side: BeatSide?, grid: Grid, from: Double, to: Double, bars: Int): List<Loop> {
        val loops = ArrayList<Loop>()
        if (side != null && side.bpm > 0 && side.downbeats.size >= bars + 1) {
            val beat = 60 / side.bpm
            val downs = side.downbeats
            for (start in downs) {
                if (start < from) continue
                val target = start + bars * 4 * beat
                val end = nearest(downs, target)
                if (abs(end - target) > beat / 4 || end > to) continue
                loops += Loop(start, end)
            }
            if (loops.isNotEmpty()) return loops
        }
        if (to < from) return loops
        val bar = grid.barSec
        val length = bars * bar
        var t = grid.anchor + ceil((from - grid.anchor) / bar) * bar
        while (t + length <= to) {
            loops += Loop(t, t + length)
            t += bar
        }
        return loops
    }

    private fun nearest(sorted: List<Double>, target: Double): Double {
        var low = 0
        var high = sorted.size - 1
        while (low < high) {
            val mid = (low + high) ushr 1
            if (sorted[mid] < target) low = mid + 1 else high = mid
        }
        val after = sorted[low]
        val before = sorted.getOrNull(low - 1) ?: return after
        return if (abs(before - target) <= abs(after - target)) before else after
    }

    /**
     * The first loop (in the order [candidates] gives) that is instrumental and
     * grooves, trying 4 bars, then 2, then 1. Null when there is none.
     *
     * Grooves: every bar at least [MIN_LOUDNESS] of the 60th-percentile loop in
     * the search. Instrumental: with synced lyrics, no sung line overlaps the
     * loop or the [LYRIC_GUARD_SEC] before it; without them, the vocal model's
     * windows cover the loop and the beat before it and find almost nothing
     * sung there.
     */
    /** What turned a side's candidate loops down, for the log and stats when none survives. */
    private class Rejections {
        var loops = 0
        var quiet = 0
        var lyrics = 0
        var unmasked = 0
        var sung = 0

        override fun toString() = if (loops == 0) {
            "no bar lines in the search"
        } else {
            "$loops loops: $quiet quiet, $lyrics sung (lyrics), $sung sung (mask), $unmasked unmeasured"
        }
    }

    private fun firstClean(
        candidates: (Int) -> List<Loop>,
        analysis: TrackAnalysis,
        sung: List<Span>?,
        beatSec: Double,
        count: Rejections,
    ): Clean? {
        for (bars in LOOP_LENGTHS) {
            val loops = candidates(bars)
            if (loops.isEmpty()) continue
            count.loops += loops.size
            val louds = DoubleArray(loops.size) { quietestBar(analysis.levelWindows, loops[it], bars) }
            val known = louds.filter { !it.isNaN() }.sorted()
            val typical = if (known.isEmpty()) Double.NaN else known[min(known.size - 1, floor(known.size * 0.6).toInt())]
            for ((index, loop) in loops.withIndex()) {
                val loud = louds[index]
                if (!loud.isNaN() && (loud < typical * MIN_LOUDNESS || loud <= SILENT_RMS)) {
                    count.quiet++
                    continue
                }
                if (sung != null) {
                    val from = loop.start - LYRIC_GUARD_SEC
                    val touching = sung.filter { it.start < loop.end && it.end > from }
                    if (touching.any { it.start < loop.end && it.certainUntil > from }) {
                        count.lyrics++
                        continue
                    }
                    if (touching.isEmpty()) return Clean(loop.start, loop.end, bars, 0.0, Check.LYRICS)
                    // Only the guessed tails of open lines reach it: the mask decides those.
                    val mask = maskStats(analysis.vocalWindows, loop.start - beatSec, loop.end)
                    if (mask == null) {
                        count.unmasked++
                        continue
                    }
                    if (mask.share > MAX_SUNG_SHARE || mask.longestRunSec > MAX_SUNG_RUN_SEC) {
                        count.sung++
                        continue
                    }
                    return Clean(loop.start, loop.end, bars, mask.share, Check.LYRICS_AND_MASK)
                }
                val mask = maskStats(analysis.vocalWindows, loop.start - beatSec, loop.end)
                if (mask == null) {
                    count.unmasked++
                    continue
                }
                if (mask.share > MAX_SUNG_SHARE || mask.longestRunSec > MAX_SUNG_RUN_SEC) {
                    count.sung++
                    continue
                }
                return Clean(loop.start, loop.end, bars, mask.share, Check.MASK)
            }
        }
        return null
    }

    /** The RMS of the quietest of a loop's bars, from the level windows; NaN where they don't reach. */
    private fun quietestBar(windows: List<CurveWindow>, loop: Loop, bars: Int): Double {
        if (windows.isEmpty()) return Double.NaN
        var quietest = Double.POSITIVE_INFINITY
        val bar = (loop.end - loop.start) / bars
        for (b in 0 until bars) {
            val rms = meanRms(windows, loop.start + b * bar, loop.start + (b + 1) * bar)
            if (rms.isNaN()) return Double.NaN
            quietest = min(quietest, rms)
        }
        return quietest
    }

    private fun meanRms(windows: List<CurveWindow>, from: Double, to: Double): Double {
        var sumSquares = 0.0
        var count = 0
        for (window in windows) {
            val a = max(0, ceil((from - window.startTime) * window.frameRate).toInt())
            val b = min(window.values.size, floor((to - window.startTime) * window.frameRate).toInt())
            for (i in a until b) {
                val rms = rmsFromLevel(window.values[i].toDouble())
                sumSquares += rms * rms
                count++
            }
        }
        return if (count == 0) Double.NaN else sqrt(sumSquares / count)
    }

    data class MaskStats(val share: Double, val longestRunSec: Double)

    /**
     * How much of [from]..[to] the vocal model calls sung, and its longest sung
     * run; null when its windows cover under [MIN_MASK_COVERAGE] of it.
     */
    fun maskStats(windows: List<CurveWindow>, from: Double, to: Double): MaskStats? {
        var sung = 0
        var n = 0
        var covered = 0.0
        var run = 0
        var longest = 0
        var fps = 1.0
        for (window in windows) {
            val a = max(0, ceil((from - window.startTime) * window.frameRate).toInt())
            val b = min(window.values.size, floor((to - window.startTime) * window.frameRate).toInt())
            for (i in a until b) {
                n++
                if (window.values[i] > VOCAL_ACTIVE_THRESHOLD) {
                    sung++
                    run++
                    longest = max(longest, run)
                } else {
                    run = 0
                }
            }
            if (b > a) {
                covered += (b - a) / window.frameRate
                fps = window.frameRate
            }
        }
        return if (n > 0 && covered >= (to - from) * MIN_MASK_COVERAGE) {
            MaskStats(sung.toDouble() / n, longest / fps)
        } else {
            null
        }
    }

    /**
     * A song's sung stretches from its synced lyric lines: each from just before
     * its start to just after its end. A line with no stated end is taken as sung
     * until the next line (at most [LYRIC_OPEN_LINE_SEC]), which can only make a
     * loop pickier, never let a word through.
     */
    fun sungSpans(lines: List<Line>): List<Span> {
        val sorted = lines.sortedBy { it.timeMs }
        val spans = ArrayList<Span>()
        for ((index, line) in sorted.withIndex()) {
            if (line.blank) continue
            val start = line.timeMs / 1000.0
            val next = sorted.subList(index + 1, sorted.size).firstOrNull { it.timeMs > line.timeMs }?.timeMs
            val stated = line.endMs != null && line.endMs > line.timeMs
            val end = if (stated) {
                line.endMs!! / 1000.0
            } else {
                min(start + LYRIC_OPEN_LINE_SEC, (next ?: (line.timeMs + 10_000)) / 1000.0)
            }
            val certain = if (stated) end else min(end, start + OPEN_LINE_CERTAIN_SEC)
            spans += Span(start - LYRIC_LEAD_SEC, end + LYRIC_TAIL_SEC, certain + LYRIC_TAIL_SEC)
        }
        return spans
    }

    // ---- Putting it on the clock -----------------------------------------------------------

    /**
     * A plan laid out in time, t0 = 0 being the moment the outgoing song
     * reaches its loop start. Every time here is seconds after t0.
     */
    class Clock(val plan: Plan) {
        /** Beats of the mix proper, in outgoing beats; then the incoming song's settle. */
        val beats: Int = plan.totalBars * 4
        val settleBeats: Int = (SETTLE_BARS * 4 / plan.fold).roundToInt()
        val rampFrom: Int = plan.enterBars * 4
        val rampTo: Int = (plan.enterBars + plan.rampBars) * 4

        /** Master tempo during outgoing beat [k]. */
        fun tempo(k: Int): Double = when {
            k < rampFrom -> plan.out.bpm
            k >= rampTo -> plan.targetBpm
            else -> plan.out.bpm + (plan.targetBpm - plan.out.bpm) * (k - rampFrom + 0.5) / (rampTo - rampFrom)
        }

        /** Beat k of the mix, for k in 0..beats + settleBeats. */
        val beatTimes: DoubleArray = DoubleArray(beats + settleBeats + 1).also { times ->
            for (k in 0 until beats + settleBeats) times[k + 1] = times[k] + 60 / tempo(k)
        }

        val enterEnd: Double get() = beatTimes[rampFrom]
        val rampEnd: Double get() = beatTimes[rampTo]

        /** Middle of the ramp: the app moves to the incoming song. */
        val handoffAt: Double get() = beatTimes[(rampFrom + rampTo) / 2]

        /** One bar at the tempo mid-ramp: how long the bass swap takes. */
        val swapBar: Double get() = 4 * 60 / tempo((rampFrom + rampTo) / 2)

        /** The incoming loop lets go; the outgoing one is silent. */
        val releaseAt: Double get() = beatTimes[beats]

        /** The incoming player takes over here, at this track position. */
        val elementAt: Double get() = beatTimes[beats + settleBeats]
        val elementTrackTime: Double get() = plan.inn.loopEnd + (elementAt - releaseAt)

        /** The unwrapped track position each deck plays at beat [k] (for k up to [beats]). */
        fun outPositionAt(k: Int): Double = plan.out.loopStart + k * plan.out.beatSec
        fun inPositionAt(k: Int): Double = plan.inn.loopStart + k * plan.fold * plan.inn.beatSec

        /** Unwrapped positions past which each deck no longer loops (the incoming one runs on). */
        val inReleasePosition: Double get() = inPositionAt(beats)

        /** The beat [t] falls in, and how far through it (0..1); k = -1 before t0. */
        fun beatAt(t: Double): Pair<Int, Double> {
            if (t < 0) return -1 to 0.0
            var low = 0
            var high = beatTimes.size - 1
            while (low < high) {
                val mid = (low + high + 1) ushr 1
                if (beatTimes[mid] <= t) low = mid else high = mid - 1
            }
            val k = low
            if (k + 1 >= beatTimes.size) return k to 0.0
            return k to min(1.0, (t - beatTimes[k]) / (beatTimes[k + 1] - beatTimes[k]))
        }
    }

    enum class Side { OUT, IN }

    /**
     * Where a song is in its own seconds [t] seconds after t0, from the same beat
     * clock the decks run on (so it loops and follows the tempo ramp exactly).
     * Null where that side isn't sounding from a deck: either side before t0,
     * and the outgoing one once its loop stops.
     */
    fun positionAt(clock: Clock, side: Side, t: Double): Double? {
        val plan = clock.plan
        if (t < 0) return null
        if (side == Side.IN && t >= clock.releaseAt) return plan.inn.loopEnd + (t - clock.releaseAt)
        if (side == Side.OUT && t >= clock.releaseAt) return null
        val (k, frac) = clock.beatAt(t)
        if (k >= clock.beats) return null
        return if (side == Side.OUT) {
            plan.out.loopStart + ((k % plan.out.beats) + frac) * plan.out.beatSec
        } else {
            plan.inn.loopStart + (((k * plan.fold) % plan.inn.beats) + frac * plan.fold) * plan.inn.beatSec
        }
    }

    /** Where in its beat the mix is at [t], 0 on the beat and rising to 1; null outside the mix. */
    fun beatPhaseAt(clock: Clock, t: Double): Double? {
        if (t < 0 || t >= clock.beatTimes.last()) return null
        return clock.beatAt(t).second
    }
}
