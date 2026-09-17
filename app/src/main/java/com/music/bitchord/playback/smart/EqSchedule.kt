package com.music.bitchord.playback.smart

import kotlin.math.max

/**
 * The per-transition-type 3-band EQ schedules from the DJ-EQ spec, as pure
 * functions of blend progress. No audio, no threads, no ExoPlayer — the
 * numbers below are the spec's schedule tables with linear interpolation
 * between rows, so a unit test can pin every row without rendering a sample.
 *
 * Conventions (spec §EQ schedules):
 * - progress 0.0 = start of overlap, 1.0 = end. Gain 1.0 = unity, 0.0 = kill.
 * - Each deck's gains are keyframes; [at] interpolates linearly between them.
 * - LOW on a swap type (SMOOTH, HARMONIC, FILTER_SWEEP, HALF_TIME_BLEND) is a
 *   placeholder: the controller's bass-swap state machine owns the LOW band
 *   there ([BASS_SWAP_PROGRESS], fired on a downbeat), because only it knows
 *   when the swap actually fired. Table-driven types (ECHO, LOOP, DISSOLVE)
 *   carry their real LOW keyframes below.
 * - `duckAMids` / `delayBMids` are the spec's ARM-time vocal flags. When false
 *   the `V?` / `V?B` rows are skipped and mids stay at unity until the close.
 */
object EqSchedule {

    data class EqGains(val low: Float, val mid: Float, val high: Float) {
        companion object {
            val UNITY = EqGains(1f, 1f, 1f)
            val SILENT = EqGains(0f, 0f, 0f)
        }
    }

    private data class Key(val progress: Float, val gains: EqGains)

    /**
     * Bass-swap arm progress per transition type (spec §Bass swap timing).
     * Null = no downbeat swap; the LOW band comes from the tables instead.
     * Satisfaction round: swap fires early so bass arrives BEFORE B is dominant.
     */
    val BASS_SWAP_PROGRESS: Map<TransitionType, Float> = mapOf(
        TransitionType.SMOOTH_CROSSFADE to 0.42f,
        TransitionType.HARMONIC_BLEND    to 0.40f,
        TransitionType.FILTER_SWEEP      to 0.30f,
        TransitionType.HALF_TIME_BLEND   to 0.20f,
        // Booth octave blend: the swap lands mid-window on the phrase "1",
        // like any beat-locked blend.
        TransitionType.OCTAVE_BLEND      to 0.50f,
    )

    /**
     * Swap duration in bars. 1 bar (4 beats at eqSwapBeatSec): the LOW
     * handover fires on the downbeat and completes one bar later. Actual
     * seconds = eqSwapBeatSec * SWAP_BARS * 4 / deckRate. The table-driven
     * types don't swap at all.
     */
    /** DJ hard swap default: the LOW handover completes in 1 bar on the fired
     *  downbeat. Value is bars; actual seconds = eqSwapBeatSec * bars * 4 /
     *  deckRate. The live render carries per-pair [eqSwapBars]: 1.0 when both
     *  sides are hot at the swap phrase, 2.0 gradual ride when sparse.
     */
    const val SWAP_BARS = 1.0

    fun outgoingGains(
        type: TransitionType,
        progress: Float,
        duckAMids: Boolean,
        shortBed: Boolean = false,
        // Real-DJ long blend: beds over LONG_BED_SECONDS voice the spread
        // long tables so voiceless pairs move mids/highs across the whole
        // 32-bar bed instead of holding unity for its first half.
        longBed: Boolean = false,
    ): EqGains = at(outgoingKeys(type, duckAMids, shortBed, longBed), progress)

    fun incomingGains(type: TransitionType, progress: Float, delayBMids: Boolean, longBed: Boolean = false): EqGains =
        at(incomingKeys(type, delayBMids, longBed), progress)

    private fun at(keys: List<Key>, progress: Float): EqGains {
        val p = progress.coerceIn(0f, 1f)
        if (p <= keys.first().progress) return keys.first().gains
        for (i in 0 until keys.size - 1) {
            val a = keys[i]
            val b = keys[i + 1]
            if (p <= b.progress) {
                val span = b.progress - a.progress
                val t = if (span <= 0f) 1f else ((p - a.progress) / span).coerceIn(0f, 1f)
                return EqGains(
                    low = lerp(a.gains.low, b.gains.low, t),
                    mid = lerp(a.gains.mid, b.gains.mid, t),
                    high = lerp(a.gains.high, b.gains.high, t),
                )
            }
        }
        return keys.last().gains
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    // ---- Outgoing (Track A) -------------------------------------------------

    /**
     * Full-audit P1 M1: beds under this length render the phrase-switch
     * short tables (HARMONIC only — the other types' keyframes already move
     * early). The long-bed HARMONIC taper's first mid move sits at 0.18 with
     * the last high move at 0.88; on a 2–7 s phrase-switch bed the mids never
     * leave unity before SILENT and the per-tick steps blow the glide budget
     * — the filter won and the EQ schedule was effectively bypassed.
     */
    const val SHORT_BED_SECONDS = 12.0

    /**
     * Real-DJ long blend: beds at/over this length voice the spread long-bed
     * tables (mids/highs trade across the full 16–32 bars). Shorter beds
     * keep the existing tables untouched.
     */
    const val LONG_BED_SECONDS = 20.0

    private fun outgoingKeys(
        type: TransitionType,
        duck: Boolean,
        shortBed: Boolean = false,
        longBed: Boolean = false,
    ): List<Key> = when (type) {
        TransitionType.SMOOTH_CROSSFADE -> {
            // Finetune-overlap: mids start receding at 0.15 (~3.4 s into a
            // 22.5 s bed), highs follow at 0.75 — the listener tracks each
            // band separately instead of hearing one fade.
            if (duck) {
                listOf(
                    Key(0f, EqGains.UNITY),
                    Key(0.15f, EqGains(1f, 0.82f, 1f)),
                    Key(0.30f, EqGains(1f, 0.45f, 1f)),
                    Key(0.45f, EqGains(1f, 0.32f, 1f)),
                    Key(0.60f, EqGains(1f, 0.20f, 1f)),
                    Key(0.75f, EqGains(1f, 0.12f, 0.88f)),
                    Key(0.90f, EqGains(1f, 0.10f, 0.62f)),
                    Key(1f, EqGains.SILENT),
                )
            } else if (longBed) {
                // Real-DJ long blend: spread the trade across the 32-bar bed
                // — first mid move at 0.22 (not 0.45), highs stepping down
                // through the back half so the bed keeps evolving.
                listOf(
                    Key(0f, EqGains.UNITY),
                    Key(0.22f, EqGains.UNITY),
                    Key(0.35f, EqGains(1f, 0.80f, 1f)),
                    Key(0.50f, EqGains(1f, 0.60f, 0.92f)),
                    Key(0.65f, EqGains(1f, 0.40f, 0.78f)),
                    Key(0.80f, EqGains(1f, 0.22f, 0.58f)),
                    Key(0.92f, EqGains(1f, 0.12f, 0.35f)),
                    Key(1f, EqGains.SILENT),
                )
            } else {
                listOf(
                    Key(0f, EqGains.UNITY),
                    Key(0.45f, EqGains.UNITY),
                    Key(0.62f, EqGains(1f, 0.78f, 1f)),
                    Key(0.78f, EqGains(1f, 0.50f, 0.85f)),
                    Key(0.92f, EqGains(1f, 0.25f, 0.60f)),
                    Key(1f, EqGains.SILENT),
                )
            }
        }
        TransitionType.HARMONIC_BLEND -> {
            // Finetune-overlap: perfect key match coexists long (unity to
            // 0.52 voiceless); ducked mids taper from 0.18 over a ~26 s bed.
            if (shortBed) {
                // P1 M1 short bed (phrase-switch 2–7 s, clash-shrunk beds):
                // same gestures, compressed — first mid move ≤0.08, mids
                // resolved by 0.60 so the voicing completes inside the bed
                // instead of arriving at SILENT still at unity.
                if (duck) {
                    listOf(
                        Key(0f, EqGains.UNITY),
                        Key(0.08f, EqGains(1f, 0.80f, 1f)),
                        Key(0.18f, EqGains(1f, 0.55f, 1f)),
                        Key(0.30f, EqGains(1f, 0.38f, 1f)),
                        Key(0.42f, EqGains(1f, 0.25f, 1f)),
                        Key(0.55f, EqGains(1f, 0.15f, 0.88f)),
                        Key(0.70f, EqGains(1f, 0.10f, 0.65f)),
                        Key(1f, EqGains.SILENT),
                    )
                } else {
                    listOf(
                        Key(0f, EqGains.UNITY),
                        Key(0.30f, EqGains.UNITY),
                        Key(0.45f, EqGains(1f, 0.80f, 1f)),
                        Key(0.60f, EqGains(1f, 0.52f, 0.88f)),
                        Key(1f, EqGains.SILENT),
                    )
                }
            } else if (duck) {
                listOf(
                    Key(0f, EqGains.UNITY),
                    Key(0.18f, EqGains(1f, 0.80f, 1f)),
                    Key(0.32f, EqGains(1f, 0.55f, 1f)),
                    Key(0.46f, EqGains(1f, 0.38f, 1f)),
                    Key(0.60f, EqGains(1f, 0.25f, 1f)),
                    Key(0.74f, EqGains(1f, 0.15f, 0.88f)),
                    Key(0.88f, EqGains(1f, 0.10f, 0.65f)),
                    Key(1f, EqGains.SILENT),
                )
            } else if (longBed) {
                // Real-DJ long blend: coexist to 0.30 (not 0.52), then trade
                // across the back two-thirds — the key match earns time, not
                // a 30-second freeze at unity.
                listOf(
                    Key(0f, EqGains.UNITY),
                    Key(0.30f, EqGains.UNITY),
                    Key(0.45f, EqGains(1f, 0.80f, 1f)),
                    Key(0.60f, EqGains(1f, 0.60f, 0.90f)),
                    Key(0.75f, EqGains(1f, 0.38f, 0.72f)),
                    Key(0.88f, EqGains(1f, 0.18f, 0.50f)),
                    Key(1f, EqGains.SILENT),
                )
            } else {
                listOf(
                    Key(0f, EqGains.UNITY),
                    Key(0.52f, EqGains.UNITY),
                    Key(0.68f, EqGains(1f, 0.80f, 1f)),
                    Key(0.82f, EqGains(1f, 0.52f, 0.88f)),
                    Key(1f, EqGains.SILENT),
                )
            }
        }
        TransitionType.FILTER_SWEEP -> {
            if (longBed) {
                // Parity long bed (Issue 3): same highs-first gesture, spread
                // across a 20 s+ bed instead of spent by 0.42.
                listOf(
                    Key(0f, EqGains.UNITY),
                    Key(0.22f, EqGains(1f, 1f, 0.90f)),
                    Key(0.35f, EqGains(1f, 1f, 0.70f)),
                    Key(0.50f, EqGains(1f, 0.80f, 0.45f)),
                    Key(0.65f, EqGains(1f, 0.55f, 0.25f)),
                    Key(0.80f, EqGains(1f, 0.30f, 0.12f)),
                    Key(0.92f, EqGains(1f, 0.12f, 0.05f)),
                    Key(1f, EqGains.SILENT),
                )
            } else {
                listOf(
                    // Finetune-overlap: A exits highs-first (0.18) then mids, so the
                    // clash is masked before B's mids emerge — never full spectrum
                    // on both decks at once.
                    Key(0f, EqGains.UNITY),
                    Key(0.18f, EqGains(1f, 1f, 0.82f)),
                    Key(0.30f, EqGains(1f, 1f, 0.58f)),
                    Key(0.42f, EqGains(1f, 0.72f, 0.32f)),
                    Key(0.56f, EqGains(1f, 0.42f, 0.14f)),
                    Key(0.70f, EqGains(1f, 0.18f, 0.10f)),
                    Key(0.84f, EqGains(1f, 0.05f, 0.03f)),
                    Key(1f, EqGains.SILENT),
                )
            }
        }
        TransitionType.ECHO_REVERB_OUT -> listOf(
            // Finetune-overlap: bass out at 0.30 (echo bass is muddy), then a
            // long mid taper so A dissolves into pure reverb tail by 0.88.
            // The mid dive starts at 0.70 on the schedule: B's entry band
            // clears before the echo tail crosses it, and the tail retains
            // the air while the mud leaves first.
            Key(0f, EqGains.UNITY),
            Key(0.22f, EqGains(1f, 1f, 1f)),
            Key(0.30f, EqGains(0f, 1f, 1f)),
            Key(0.46f, EqGains(0f, 0.72f, 1f)),
            Key(0.58f, EqGains(0f, 0.42f, 0.85f)),
            Key(0.70f, EqGains(0f, 0.18f, 0.62f)),
            Key(0.88f, EqGains(0f, 0.06f, 0.35f)),
            Key(1f, EqGains.SILENT),
        )
        TransitionType.LOOP_CUT_DROP,
        TransitionType.LOOP_ROLL,
        -> listOf(
            // Full energy to hold tension; all bands cut together before the drop.
            // The roll extend shares the shape: the release glide is voiced by
            // the renderer's 2-beat settle, not the table.
            Key(0f, EqGains.UNITY),
            Key(0.70f, EqGains(1f, 1f, 1f)),
            Key(0.85f, EqGains(1f, 0.70f, 1f)),
            Key(0.90f, EqGains.SILENT),
            Key(1f, EqGains.SILENT),
        )
        TransitionType.HALF_TIME_BLEND -> {
            // Finetune-overlap: mids touch at 0.14 (swap lands at 0.20) and
            // taper across the bed; voiceless pairs coexist to 0.42.
            if (duck) {
                listOf(
                    Key(0f, EqGains.UNITY),
                    Key(0.14f, EqGains(1f, 0.75f, 1f)),
                    Key(0.26f, EqGains(1f, 0.55f, 1f)),
                    Key(0.40f, EqGains(1f, 0.40f, 1f)),
                    Key(0.55f, EqGains(1f, 0.22f, 1f)),
                    Key(0.68f, EqGains(1f, 0.15f, 0.85f)),
                    Key(0.82f, EqGains(1f, 0.10f, 0.58f)),
                    Key(1f, EqGains.SILENT),
                )
            } else {
                listOf(
                    Key(0f, EqGains.UNITY),
                    Key(0.42f, EqGains.UNITY),
                    Key(0.60f, EqGains(1f, 0.86f, 0.80f)),
                    Key(0.78f, EqGains(1f, 0.58f, 0.55f)),
                    Key(1f, EqGains.SILENT),
                )
            }
        }
        TransitionType.PLAIN_DISSOLVE -> listOf(
            // Hollow-fix: A holds its bass to 0.35 (was 0.24) so it overlaps
            // B's low return — complementary handover, never both at 0.
            Key(0f, EqGains.UNITY),
            Key(0.14f, EqGains.UNITY),
            Key(0.35f, EqGains(1f, 1f, 1f)),
            Key(0.45f, EqGains(0f, 1f, 1f)),
            Key(0.72f, EqGains(0f, 0.60f, 0.90f)),
            Key(1f, EqGains.SILENT),
        )
        TransitionType.HARD_CUT -> listOf(Key(0f, EqGains.UNITY), Key(1f, EqGains.UNITY))
        // Booth octave blend: locked-grid blend at rate 1.0 — voice the
        // SMOOTH tables (same single-owner grammar, same swap machine).
        TransitionType.OCTAVE_BLEND ->
            outgoingKeys(TransitionType.SMOOTH_CROSSFADE, duck, shortBed, longBed)
    }

    // ---- Incoming (Track B) -------------------------------------------------

    private fun incomingKeys(type: TransitionType, delay: Boolean, longBed: Boolean = false): List<Key> = when (type) {
        TransitionType.SMOOTH_CROSSFADE -> {
            if (longBed) {
                // Long blend: B mids hand over across the back half so combined
                // mids stay >= ~0.85 through the 0.40-0.60 ownership window.
                val mid = if (delay) {
                    listOf(
                        Key(0f, EqGains(1f, 0f, 1f)),
                        Key(0.30f, EqGains(1f, 0f, 1f)),
                        Key(0.45f, EqGains(1f, 0.45f, 1f)),
                        Key(0.60f, EqGains.UNITY),
                    )
                } else {
                    listOf(
                        Key(0f, EqGains(1f, 0.70f, 1f)),
                        Key(0.32f, EqGains(1f, 0.88f, 1f)),
                        Key(0.60f, EqGains.UNITY),
                    )
                }
                mid + Key(1f, EqGains.UNITY)
            } else {
                val mid = if (delay) {
                    listOf(
                        Key(0f, EqGains(1f, 0f, 1f)),
                        Key(0.18f, EqGains(1f, 0f, 1f)),
                        Key(0.28f, EqGains(1f, 0.32f, 1f)),
                        Key(0.38f, EqGains(1f, 0.68f, 1f)),
                        Key(0.45f, EqGains.UNITY),
                    )
                } else {
                    listOf(Key(0f, EqGains(1f, 0.86f, 1f)), Key(0.12f, EqGains.UNITY))
                }
                mid + Key(1f, EqGains.UNITY)
            }
        }
        TransitionType.HARMONIC_BLEND ->
            if (longBed) {
                // Long blend: spread the highs-first entry across the whole bed,
                // never two full mids from second 0 on a 32-bar blend.
                if (delay) {
                    listOf(
                        Key(0f, EqGains(1f, 0f, 0.40f)),
                        Key(0.22f, EqGains(1f, 0f, 0.80f)),
                        Key(0.35f, EqGains(1f, 0f, 1f)),
                        Key(0.50f, EqGains(1f, 0.50f, 1f)),
                        Key(0.60f, EqGains.UNITY),
                        Key(1f, EqGains.UNITY),
                    )
                } else {
                    listOf(
                        Key(0f, EqGains(1f, 0f, 0.55f)),
                        Key(0.30f, EqGains(1f, 0.50f, 1f)),
                        Key(0.50f, EqGains.UNITY),
                        Key(1f, EqGains.UNITY),
                    )
                }
            } else if (delay) {
                listOf(
                    Key(0f, EqGains(1f, 0f, 0.40f)),
                    Key(0.12f, EqGains(1f, 0f, 0.80f)),
                    Key(0.22f, EqGains(1f, 0f, 1f)),
                    Key(0.32f, EqGains(1f, 0.50f, 1f)),
                    Key(0.45f, EqGains.UNITY),
                    Key(1f, EqGains.UNITY),
                )
            } else {
                listOf(
                    Key(0f, EqGains(1f, 0f, 0.60f)),
                    Key(0.12f, EqGains(1f, 0.40f, 1f)),
                    Key(0.22f, EqGains.UNITY),
                    Key(1f, EqGains.UNITY),
                )
            }
        TransitionType.FILTER_SWEEP -> if (longBed) {
            // Long blend: same highs-first gesture spread across 20s+ so mids
            // filling in from 0.45 (not 0.32) keeps the bed warm longer.
            listOf(
                Key(0f, EqGains(1f, 0f, 0.40f)),
                Key(0.22f, EqGains(1f, 0f, 0.80f)),
                Key(0.35f, EqGains(1f, 0f, 1f)),
                Key(0.45f, EqGains(1f, 0.50f, 1f)),
                Key(0.60f, EqGains(1f, 0.72f, 1f)),
                Key(0.80f, EqGains.UNITY),
                Key(1f, EqGains.UNITY),
            )
        } else listOf(
            Key(0f, EqGains(1f, 0f, 0.40f)),
            Key(0.12f, EqGains(1f, 0f, 0.80f)),
            Key(0.22f, EqGains(1f, 0f, 1f)),
            Key(0.32f, EqGains(1f, 0.50f, 1f)),
            Key(0.40f, EqGains(1f, 0.52f, 1f)),
            Key(0.60f, EqGains(1f, 0.78f, 1f)),
            Key(0.70f, EqGains.UNITY),
            Key(1f, EqGains.UNITY),
        )
        TransitionType.ECHO_REVERB_OUT -> listOf(
            // Hollow-fix: B low never hits 0 — it dips shallow (0.55) exactly
            // when A kills its bass at 0.30, then carries the low end while A
            // dissolves into tail. DJ rule: one deck always holds the bass.
            Key(0f, EqGains(1f, 0f, 0.80f)),
            Key(0.30f, EqGains(0.55f, 0f, 0.82f)),
            Key(0.45f, EqGains(0.75f, 0.45f, 1f)),
            Key(0.58f, EqGains(0.90f, 0.85f, 1f)),
            Key(0.68f, EqGains.UNITY),
            Key(1f, EqGains.UNITY),
        )
        TransitionType.LOOP_CUT_DROP,
        TransitionType.LOOP_ROLL,
        ->
            // B silent until the drop, then full mix — the payoff.
            listOf(Key(0f, EqGains.UNITY), Key(1f, EqGains.UNITY))
        TransitionType.HALF_TIME_BLEND -> listOf(
            // Bass killed; mids a touch softer on the approach, open by 0.10.
            Key(0f, EqGains(1f, 0.88f, 1f)),
            Key(0.10f, EqGains.UNITY),
            Key(1f, EqGains.UNITY),
        )
        TransitionType.PLAIN_DISSOLVE -> listOf(
            // Hollow-fix: B carries a reduced bass bed (0.45) from the start
            // and rises to meet A as A lets go at 0.35-0.45 — no dead zone.
            Key(0f, EqGains(0.45f, 1f, 1f)),
            Key(0.30f, EqGains(0.45f, 1f, 1f)),
            Key(0.45f, EqGains(0.85f, 1f, 1f)),
            Key(0.58f, EqGains.UNITY),
            Key(1f, EqGains.UNITY),
        )
        TransitionType.HARD_CUT -> listOf(Key(0f, EqGains.UNITY), Key(1f, EqGains.UNITY))
        // Booth octave blend: locked-grid blend — voice the SMOOTH tables.
        TransitionType.OCTAVE_BLEND ->
            incomingKeys(TransitionType.SMOOTH_CROSSFADE, delay, longBed)
    }

    /** Largest single-tick target step this schedule can produce, for the Rule 3 check. */
    fun maxTickStep(type: TransitionType, spanSec: Float, tickSec: Float = 0.030f): Float {
        var worst = 0f
        val steps = max(1, (spanSec / tickSec).toInt())
        for (lb in listOf(false, true)) {
            var prevOut = outgoingGains(type, 0f, true, longBed = lb)
            var prevIn = incomingGains(type, 0f, true, longBed = lb)
            for (i in 1..steps) {
                val p = i.toFloat() / steps
                val o = outgoingGains(type, p, true, longBed = lb)
                val n = incomingGains(type, p, true, longBed = lb)
                worst = max(worst, stepOf(prevOut, o))
                worst = max(worst, stepOf(prevIn, n))
                prevOut = o
                prevIn = n
            }
        }
        return worst
    }

    private fun stepOf(a: EqGains, b: EqGains): Float =
        max(max(kotlin.math.abs(a.low - b.low), kotlin.math.abs(a.mid - b.mid)), kotlin.math.abs(a.high - b.high))
}
