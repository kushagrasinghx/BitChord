package com.music.bitchord.playback.smart

import kotlin.math.abs

/**
 * v2 §2b structural section detector. Pure Kotlin, no PCM, no Android — every
 * function here is unit-testable on synthetic curves.
 *
 * The native side supplies three transient inputs (see TrackFeatures):
 * per-onset event times, per-frame spectral centroid in Hz, and a
 * full-resolution (250 ms) normalized energy curve. This object folds them
 * into section labels plus the scalars the planner reads; only those outputs
 * are persisted (see AnalysisStore schema 3). The fine curves themselves are
 * never stored — kilobytes per entry for data the planner never re-reads.
 *
 * Rules below follow the spec verbatim; deviations forced by real data carry
 * a DEVIATION note explaining why.
 */
object StructureDetector {

    /** Least-squares slope of ys over xs. 0 when degenerate. Shared with §6/§8b. */
    fun linearSlope(xs: List<Double>, ys: List<Double>): Double {
        if (xs.size != ys.size || xs.size < 2) return 0.0
        val n = xs.size
        val meanX = xs.sum() / n
        val meanY = ys.sum() / n
        var num = 0.0
        var den = 0.0
        for (i in xs.indices) {
            val dx = xs[i] - meanX
            num += dx * (ys[i] - meanY)
            den += dx * dx
        }
        return if (den > 1e-12) num / den else 0.0
    }

    private data class WindowStats(
        val start: Double,
        val end: Double,
        val rmsMean: Double,
        val onsetPerSec: Double,
        val centroidMean: Double,
    )

    /**
     * Classify 4-bar windows from downbeat stride 4. Windows with no fine
     * samples are skipped; an empty map means "no evidence" and callers keep
     * the v1 energy heuristics (spec §2b fallback).
     *
     * First match wins per window, in spec order:
     * DROP, BUILD, BREAK, OUTRO, INTRO, CHORUS/VERSE.
     *
     * @param meanRms track mean over the fine curve (caller computes once).
     * @param meanOnset track mean onset rate per second.
     */
    fun detect(
        fine: List<EnergySample>,
        centroid: List<EnergySample>,
        onsets: List<Double>,
        downbeats: List<Double>,
        duration: Double,
        meanRms: Double,
        meanOnset: Double,
        beatInterval: Double,
    ): List<StructureLabel> {
        if (fine.size < 8 || meanRms <= 0 || duration <= 0) return emptyList()
        val barSeconds = if (beatInterval.isFinite() && beatInterval > 0) beatInterval * 4 else 2.0
        // Spec slopes are per bar; measured slopes are per second over window
        // starts, so divide by the bar length. Exact — no approximation.
        val buildRmsSlope = BUILD_RMS_SLOPE_PER_BAR / barSeconds
        val buildCentroidSlope = BUILD_CENTROID_SLOPE_PER_BAR / barSeconds
        // Finetune v1 §1.4: spectral gate needs the track centroid mean.
        val finiteCents = centroid.filter { it.time.isFinite() && it.energy.isFinite() }.map { it.energy }
        val trackCentroidMean = if (finiteCents.isEmpty()) 0.0 else finiteCents.average()
        val bars = downbeats.filter { it.isFinite() }.sorted()
        if (bars.size < 8) return emptyList()
        // 4-bar windows, stepping one bar for boundary resolution.
        // Bound is strict: bar+4 == size would read bars[size] past the end
        // (crashed every track with >=8 downbeats on-device, 2026-09-05 log).
        val windows = buildWindowStats(fine, centroid, onsets, bars)
        if (windows.isEmpty()) return emptyList()
        val trackMean = windows.map { it.rmsMean }.average()
        val trackPeak = windows.maxOf { it.rmsMean }

        fun slopeBack(index: Int, windowsBack: Int, pick: (WindowStats) -> Double): Double {
            val from = (index - windowsBack).coerceAtLeast(0)
            val xs = (from..index).map { windows[it].start }
            val ys = (from..index).map { pick(windows[it]) }
            return linearSlope(xs, ys)
        }

        // Preceding-8-bar rising check for DROP: mean slope of rms over the
        // two windows before, positive.
        return windows.mapIndexed { index, window ->
            val pos = window.start / duration
            // Finetune v1 §1.1: 4-bar look-back, any 2 of 4 positive — gradual
            // build-ups need a wider scan than 2 bars (~3.7 s @128 BPM).
            val slopeWindow = positiveSlopeBars(windows, index)
            val type = when {
                (window.rmsMean > DROP_RMS_MULTIPLIER * meanRms &&
                    window.onsetPerSec > DROP_ONSET_MULTIPLIER * meanOnset &&
                    window.centroidMean > DROP_CENTROID_HZ &&
                    slopeWindow >= DROP_SLOPE_MIN_POSITIVE_BARS) ||
                    // Cold-open drop: starts at full energy, no build to slope
                    // back on. A drop in the first 12% with hot rms+onset IS
                    // the drop.
                    (pos < DROP_COLD_OPEN_POSITION_FRACTION &&
                        window.rmsMean > DROP_RMS_MULTIPLIER * meanRms &&
                        window.onsetPerSec > DROP_ONSET_MULTIPLIER * meanOnset) ->
                    StructureSectionType.DROP
                slopeBack(index, 2) { it.rmsMean } > buildRmsSlope &&
                    slopeBack(index, 2) { it.centroidMean } > buildCentroidSlope &&
                    risingBars(windows, index) >= 8 -> StructureSectionType.BUILD
                window.rmsMean < BREAK_RMS_FRACTION * meanRms &&
                    window.onsetPerSec < BREAK_ONSET_FRACTION * meanOnset &&
                    lowRunBars(windows, index, trackMean) >= BREAK_MIN_BARS &&
                    precededByHighEnergy(windows, index, trackMean, BREAK_PRIOR_HIGH_BARS) ->
                    StructureSectionType.BREAK
                pos > OUTRO_POSITION_FRACTION &&
                    outroFalling(windows, index) &&
                    window.rmsMean < OUTRO_RMS_PEAK_FRACTION * trackPeak &&
                    (trackCentroidMean <= 0 || window.centroidMean < OUTRO_SPECTRAL_RATIO * trackCentroidMean) ->
                    StructureSectionType.OUTRO
                pos < INTRO_POSITION_FRACTION &&
                    window.rmsMean < INTRO_RMS_FRACTION * meanRms &&
                    window.centroidMean < INTRO_CENTROID_HZ -> StructureSectionType.INTRO
                window.rmsMean > 1.05 * meanRms -> StructureSectionType.CHORUS
                else -> StructureSectionType.VERSE
            }
            StructureLabel(window.start, window.end, type)
        }.mergeAdjacent()
    }

    private fun buildWindowStats(
        fine: List<EnergySample>,
        centroid: List<EnergySample>,
        onsets: List<Double>,
        bars: List<Double>,
    ): List<WindowStats> {
        val windows = mutableListOf<WindowStats>()
        var bar = 0
        while (bar + 4 < bars.size) {
            val start = bars[bar]
            val end = bars[bar + 4]
            if (end <= start || start < 0) {
                bar++
                continue
            }
            val energies = fine.filter { it.time.isFinite() && it.energy.isFinite() && it.time in start..end }
                .map { it.energy }
            if (energies.isEmpty()) {
                bar++
                continue
            }
            val cents = centroid.filter { it.time.isFinite() && it.energy.isFinite() && it.time in start..end }
                .map { it.energy }
            val onsetCount = onsets.count { it.isFinite() && it in start..end }
            windows += WindowStats(
                start = start,
                end = end,
                rmsMean = energies.average(),
                onsetPerSec = onsetCount / (end - start),
                centroidMean = if (cents.isEmpty()) 0.0 else cents.average(),
            )
            bar++
        }
        return windows
    }

    /**
     * Exit-entry spec Fix 1 (+ Fix 6 genre branch): scored best-candidate drop
     * selection. Replaces first-match: every 4-bar window is scored on
     * rms/onset/centroid (each 0..1), BUILD-precedence (highest single weight:
     * an energetic intro with no BUILD before it can barely pass), sustain,
     * plus a position bonus for the expected drop zone [0.20L, 0.65L].
     * Winners need >= DROP_SCORE_BEST; fallback is max-RMS in-zone but only
     * when it genuinely peaks (guards the flat-bed phantom-drop test).
     * AMBIENT (untrusted grid) returns null — max-energy fallback downstream.
     */
    fun selectFirstDrop(
        fine: List<EnergySample>,
        centroid: List<EnergySample>,
        onsets: List<Double>,
        downbeats: List<Double>,
        duration: Double,
        meanRms: Double,
        meanOnset: Double,
        beatInterval: Double,
        structureMap: List<StructureLabel>,
        bpm: Double,
        beatConfidence: Double,
    ): DropCandidate? {
        if (fine.size < 8 || meanRms <= 0 || duration <= 0) return null
        if (beatConfidence < AMBIENT_BEAT_CONF) return null
        val bars = downbeats.filter { it.isFinite() }.sorted()
        if (bars.size < 8) return null
        val windows = buildWindowStats(fine, centroid, onsets, bars)
        if (windows.isEmpty()) return null
        val presence = bars.size / duration
        val isTechno = bpm >= DROP_TECHNO_BPM && bpm <= 150.0 && presence > 0.2
        val centroidNorm = when {
            isTechno -> DROP_TECHNO_CENTROID_HZ
            bpm in 70.0..105.0 && presence > 0.2 -> DROP_OTHER_CENTROID_HZ
            bpm in 90.0..135.0 -> DROP_OTHER_CENTROID_HZ
            else -> DROP_HOUSE_CENTROID_HZ
        }
        val rmsBoost = if (isTechno) DROP_TECHNO_RMS_BOOST else 1.0
        val sustainSteps = if (isTechno) DROP_TECHNO_SUSTAIN_STEPS else DROP_SUSTAIN_STEPS
        val phraseSec = if (beatInterval.isFinite() && beatInterval > 0) beatInterval * 64 else 30.0
        val barSec = if (beatInterval.isFinite() && beatInterval > 0) beatInterval * 4 else 2.0
        var best: DropCandidate? = null
        for (i in windows.indices) {
            val w = windows[i]
            if (!w.start.isFinite()) continue
            val rmsRatio = ((w.rmsMean / meanRms) * rmsBoost).coerceIn(0.0, 2.5) / 2.5
            val onsetRatio = if (meanOnset > 0) {
                (w.onsetPerSec / meanOnset).coerceIn(0.0, 2.5) / 2.5
            } else {
                0.0
            }
            val centroidScore = (w.centroidMean / centroidNorm).coerceIn(0.0, 1.0)
            val hasBuildBefore = structureMap.any { label ->
                label.type == StructureSectionType.BUILD &&
                    label.end.isFinite() && w.start.isFinite() &&
                    label.end > w.start - phraseSec * 2 &&
                    label.end <= w.start + barSec
            }
            // Compressed-EDM fallback: centroid rising over the 2 phrases
            // before the candidate counts as partial build (rms ceiling hides
            // the slope). Fraction of rising adjacent pairs in range.
            var risingPairs = 0
            var totalPairs = 0
            for (j in 1..windows.lastIndex) {
                val a = windows[j - 1]
                val b = windows[j]
                if (b.start < w.start - phraseSec * 2 || b.start > w.start) continue
                totalPairs++
                if (b.centroidMean > a.centroidMean) risingPairs++
            }
            val buildScore = when {
                hasBuildBefore -> 1.0
                totalPairs > 0 && risingPairs.toDouble() / totalPairs > DROP_CENTROID_BUILD_FRACTION -> 0.6
                else -> 0.0
            }
            var sustained = 0
            for (offset in 1..sustainSteps) {
                val next = windows.getOrNull(i + offset)
                if (next != null && next.rmsMean > meanRms) sustained++
            }
            val sustainScore = sustained.toDouble() / sustainSteps
            val positionRatio = w.start / duration
            val positionBonus = when {
                positionRatio in 0.20..0.65 -> DROP_SCORE_POSITION_BONUS
                positionRatio in 0.10..0.75 -> DROP_SCORE_POSITION_OK
                else -> DROP_SCORE_POSITION_PENALTY
            }
            val score = rmsRatio * 0.30 + onsetRatio * 0.15 + centroidScore * 0.15 +
                buildScore * 0.25 + sustainScore * 0.15 + positionBonus
            if (score > DROP_SCORE_ACCEPT) {
                if (best == null || score > (best.score ?: 0.0)) best = DropCandidate(w.start, score)
            }
        }
        val winner = best?.takeIf { (it.score ?: 0.0) >= DROP_SCORE_BEST }
        if (winner != null) return winner
        // Fallback: max-RMS in-zone, guarded — a flat bed must not report a
        // phantom drop (MixsetTest strict-neighbor contract). No score: the
        // fallback carries no measured confidence.
        return windows
            .filter { it.start / duration in 0.20..0.65 && it.rmsMean > 1.05 * meanRms }
            .maxByOrNull { it.rmsMean }
            ?.let { DropCandidate(it.start, null) }
    }

    data class DropCandidate(val startSec: Double, val score: Double?)

    /**
     * Spec slopes are per bar but windows step one bar while spanning four —
     * a per-second slope over window starts equals per-bar slope / barSeconds.
     * Rather than threading tempo through, compare against the spec constant
     * scaled by a nominal 3 s bar (≈128 BPM 4/4: bar = 1.875 s; using 3 s is
     * the conservative direction — fewer false BUILDs on slow tracks).
     * DEVIATION: documented approximation, errs toward fewer labels.
     */
    private fun risingBars(windows: List<WindowStats>, index: Int): Int {
        var count = 0
        var i = index
        while (i > 0 && windows[i].rmsMean >= windows[i - 1].rmsMean) {
            count++
            i--
        }
        return count
    }

    /** Sustained-low run length ending here, in window steps (~bars). */
    private fun lowRunBars(windows: List<WindowStats>, index: Int, trackMean: Double): Int {
        var count = 0
        var i = index
        while (i >= 0 && windows[i].rmsMean < BREAK_RMS_FRACTION * trackMean) {
            count++
            i--
        }
        return count
    }

    /**
     * Single-pass proxy for "preceded by DROP/CHORUS within N bars": labels
     * are assigned in this same pass, so a loud predecessor reads as
     * above-mean rms in the windows back. Finetune v1 §1.3: 4→8 bars — at
     * 70 BPM 4 bars = 13.7 s, too narrow to link breaks after a long drop.
     */
    private fun precededByHighEnergy(
        windows: List<WindowStats>,
        index: Int,
        trackMean: Double,
        windowBars: Int = BREAK_PRIOR_HIGH_BARS,
    ): Boolean {
        for (i in (index - windowBars).coerceAtLeast(0) until index) {
            if (windows[i].rmsMean > 1.05 * trackMean) return true
        }
        return false
    }

    /**
     * Finetune v1 §1.1: bars with positive rms slope in the look-back —
     * gradual build-ups need "any 2 of 4 positive", not a single 2-bar slope.
     */
    private fun positiveSlopeBars(windows: List<WindowStats>, index: Int): Int {
        var count = 0
        val from = (index - DROP_SLOPE_LOOKBACK_BARS).coerceAtLeast(0)
        for (i in (from + 1)..index) {
            if (windows[i].rmsMean > windows[i - 1].rmsMean) count++
        }
        return count
    }

    /**
     * Finetune v1 §1.4: the AND of slope<0 and last<first fails on real data
     * (noted as bug) — OR instead: a measurable downward slope, or the last
     * segment clearly quieter (12% drop) than the first.
     */
    private fun outroFalling(windows: List<WindowStats>, index: Int): Boolean {
        val from = (index - 15).coerceAtLeast(0)
        val xs = (from..index).map { windows[it].start }
        val ys = (from..index).map { windows[it].rmsMean }
        if (linearSlope(xs, ys) < OUTRO_FALLING_SLOPE) return true
        val first = ys.take(8).average()
        val last = ys.takeLast(8).average()
        return last < first * OUTRO_QUIET_RATIO
    }

    private fun List<StructureLabel>.mergeAdjacent(): List<StructureLabel> {
        if (isEmpty()) return this
        val out = mutableListOf(first())
        for (label in drop(1)) {
            val last = out.last()
            if (label.type == last.type && label.start <= last.end + 0.01) {
                out[out.lastIndex] = last.copy(end = last.end.coerceAtLeast(label.end))
            } else {
                out += label
            }
        }
        return out
    }

    /**
     * v2 §4 energy-gradient buildup foot, on the 250 ms fine curve.
     * Steps: (1) peak energy at [dropSec]; (2) per-sample gradient;
     * (3) scan backward from drop−8 s for the nearest ≤0→>0 flip;
     * (4) validate 8 s ≤ drop−foot ≤ 96 s and rise ≥ 0.25×peak;
     * (5) caller snaps to phrase16. Null when anything fails — the caller
     * falls back to drop−phrase (spec §4 fallback).
     */
    fun gradientBuildup(
        fine: List<EnergySample>,
        dropSec: Double,
        peakEnergy: Double,
    ): Double? {
        val flip = gradientInflection(fine, dropSec) ?: return null
        val span = dropSec - flip
        if (span < MIXSET_BUILDUP_MIN_SECONDS || span > MIXSET_BUILDUP_MAX_SECONDS) return null
        if (!climbValid(fine, flip, dropSec, peakEnergy)) return null
        return flip
    }

    /**
     * Spec finetune §6.1 step 2: the raw inflection — nearest ≤0→>0 gradient
     * flip scanning back from drop−8 s — with NO span/rise validation. The
     * caller decides whether the climb earns it (validated) or merely leans
     * up (monotonic). Null when the curve never turns upward before the drop.
     */
    fun gradientInflection(fine: List<EnergySample>, dropSec: Double): Double? {
        if (!dropSec.isFinite() || fine.size < 8) return null
        val pts = fine.filter { it.time.isFinite() && it.energy.isFinite() }.sortedBy { it.time }
        if (pts.size < 8) return null
        val dt = (pts.last().time - pts.first().time) / (pts.size - 1)
        if (!(dt > 0)) return null
        fun energyAt(t: Double): Double {
            val i = pts.binarySearchBy(t) { it.time }.let { if (it < 0) -(it + 1) else it }
                .coerceIn(0, pts.size - 1)
            return pts[i].energy
        }
        var t = dropSec - MIXSET_BUILDUP_MIN_SECONDS
        val scanEnd = dropSec - MIXSET_BUILDUP_MAX_SECONDS
        while (t > scanEnd) {
            val g0 = energyAt(t) - energyAt(t - dt)
            val g1 = energyAt(t + dt) - energyAt(t)
            if (g0 <= 0 && g1 > 0) return t
            t -= dt
        }
        return null
    }

    /**
     * Spec finetune §6.1 step 2 gate: more than half the fine windows from
     * foot to drop slope upward. A monotonic lean earns the inflection even
     * when the climb is too shallow to pass the rise threshold.
     */
    fun climbMonotonic(fine: List<EnergySample>, footSec: Double, dropSec: Double): Boolean {
        val pts = fine.filter {
            it.time.isFinite() && it.energy.isFinite() && it.time >= footSec && it.time <= dropSec
        }.sortedBy { it.time }
        if (pts.size < 4) return false
        var up = 0
        var total = 0
        for (i in 1 until pts.size) {
            total++
            if (pts[i].energy > pts[i - 1].energy) up++
        }
        return total > 0 && up.toDouble() / total > 0.5
    }

    private fun climbValid(
        fine: List<EnergySample>,
        footSec: Double,
        dropSec: Double,
        peakEnergy: Double,
    ): Boolean {
        val pts = fine.filter { it.time.isFinite() && it.energy.isFinite() }.sortedBy { it.time }
        if (pts.size < 8) return false
        val dt = (pts.last().time - pts.first().time) / (pts.size - 1)
        if (!(dt > 0)) return false
        fun energyAt(t: Double): Double {
            val i = pts.binarySearchBy(t) { it.time }.let { if (it < 0) -(it + 1) else it }
                .coerceIn(0, pts.size - 1)
            return pts[i].energy
        }
        val span = dropSec - footSec
        if (span < MIXSET_BUILDUP_MIN_SECONDS || span > MIXSET_BUILDUP_MAX_SECONDS) return false
        // Rise over the climb: mean energy from foot to drop vs foot.
        var sum = 0.0
        var n = 0
        var tt = footSec
        while (tt <= dropSec) {
            sum += energyAt(tt)
            n++
            tt += dt
        }
        val foot = energyAt(footSec)
        return n >= 2 && sum / n >= foot + MIXSET_BUILDUP_RISE_MARGIN * peakEnergy
    }
}
