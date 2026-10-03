package com.music.bitchord.desktop

import com.music.bitchord.playback.TransitionFilter
import com.music.bitchord.playback.smart.FilterTransitionVariant
import com.music.bitchord.playback.smart.TransitionPlan
import com.music.bitchord.playback.smart.TransitionStyle
import kotlin.math.pow

/** Aims the two transition filters as a mix runs, ported from Android's `CrossfadeController`. */
internal object DesktopTransitionRide {

    /** Points [outgoing] and [incoming] where [plan] wants them at [progress]. */
    fun aim(
        plan: TransitionPlan,
        progress: Float,
        outgoing: TransitionFilter,
        incoming: TransitionFilter,
    ) {
        when (plan.transitionStyle) {
            TransitionStyle.DJ_FILTER -> when (plan.filterVariant) {
                FilterTransitionVariant.BASS_HANDOFF -> fallbackBassHandoff(plan, progress, outgoing, incoming)
                FilterTransitionVariant.SWEEP,
                FilterTransitionVariant.ECHO_RIDE,
                -> filterSweep(plan, progress, outgoing, incoming)
            }
            TransitionStyle.DJ_BLEND ->
                if (plan.bassSwap) {
                    bassSwap(plan, progress, outgoing, incoming)
                } else {
                    vocalSeparation(plan, progress, outgoing, incoming)
                }
            // GAPLESS is an album played through, where any filtering would be an edit the record
            // did not ask for.
            TransitionStyle.GAPLESS -> {
                outgoing.open()
                incoming.open()
            }
            // EQUAL_POWER is reached when the *tempo* evidence was too weak for anything more
            // opinionated.
            else -> vocalSeparation(plan, progress, outgoing, incoming)
        }
        if (plan.echoSeconds > 0.0) {
            val at = echoAt(plan).toDouble()
            val width = swapWidth(plan)
            val handover = ((progress - (at - width)) / width).coerceIn(0.0, 1.0)
            outgoing.setCutoffs(TransitionFilter.OPEN_HZ, bassCutoff(handover))
        }
    }

    fun echoAt(plan: TransitionPlan): Float = onBar(plan, FILTER_ECHO_AT).toFloat()

    fun handoffAt(plan: TransitionPlan): Float =
        if (plan.transitionStyle == TransitionStyle.DJ_BLEND && plan.bassSwap) {
            onBeat(plan, plan.bassSwapFraction).toFloat()
        } else {
            0.5f
        }

    fun echoFrom(plan: TransitionPlan): Float =
        (echoAt(plan) - if (plan.transitionBeats > 0) 1f / plan.transitionBeats else 0f).coerceAtLeast(0f)

    fun echoKill(plan: TransitionPlan): Float =
        (if (plan.transitionBeats > 0) ECHO_KILL_BEATS / plan.transitionBeats else 0.01).toFloat()

    /** Approximate loudness of the dry outgoing deck or its repeats, for mix headroom. */
    fun echoLevel(plan: TransitionPlan, progress: Float): Float {
        if (plan.echoSeconds <= 0.0) return 0f
        val at = echoAt(plan)
        val dry = (1f - ((progress - at) / echoKill(plan)).coerceIn(0f, 1f))
        val elapsedSeconds = (progress - at) * plan.fadeSeconds.toFloat()
        val repeats = if (elapsedSeconds > 0f) elapsedSeconds / plan.echoSeconds.toFloat() else 0f
        val wet = if (elapsedSeconds > 0f) ECHO_LEVEL * ECHO_DECAY.pow(repeats) else 0f
        return maxOf(dry, wet)
    }

    /** The fader shapes paired with [aim], matching the phone's advanced Automix renderer. */
    fun gains(plan: TransitionPlan, progress: Float, result: FloatArray) {
        require(result.size >= 2)
        val p = progress.coerceIn(0f, 1f)
        val plainIn = rise(p)
        val plainOut = fall(p)
        var styledOut = plainOut
        var styledIn = plainIn
        when (plan.transitionStyle) {
            TransitionStyle.DJ_BLEND -> if (plan.bassSwap) {
                val swap = onBeat(plan, plan.bassSwapFraction).toFloat()
                styledIn = maxOf(plainIn, rise(p / swap))
                styledOut = if (p <= swap) 1f else fall((p - swap) / (1f - swap))
            }
            TransitionStyle.DJ_FILTER -> when (plan.filterVariant) {
                FilterTransitionVariant.BASS_HANDOFF -> {
                    val swap = filterSwapAt(plan).toFloat()
                    styledOut = if (p <= swap) 1f else fall((p - swap) / (1f - swap))
                    styledIn = rise(p / swap)
                }
                // The delay owns the outgoing fade: the deck fader must carry its repeats.
                FilterTransitionVariant.ECHO_RIDE -> {
                    styledOut = if (plan.echoSeconds > 0.0) 1f else fall(p.pow(ECHO_RIDE_FALL_SHAPE))
                    styledIn = rise(p.pow(ECHO_RIDE_RISE_SHAPE))
                }
                FilterTransitionVariant.SWEEP -> {
                    styledOut = fall(p.pow(FILTER_FALL_SHAPE))
                    styledIn = rise(p.pow(FILTER_RISE_SHAPE))
                }
            }
            else -> Unit
        }
        val clash = plan.vocalOverlap.toFloat().coerceIn(0f, 1f)
        val safeOut = fall(p.pow(VOCAL_SAFE_FALL_SHAPE))
        val safeIn = rise(p.pow(VOCAL_SAFE_RISE_SHAPE))
        result[0] = styledOut + (safeOut - styledOut) * clash
        result[1] = styledIn + (safeIn - styledIn) * clash
    }

    private fun fallbackBassHandoff(
        plan: TransitionPlan,
        progress: Float,
        outgoing: TransitionFilter,
        incoming: TransitionFilter,
    ) {
        val clash = plan.vocalOverlap.coerceIn(0.0, 1.0)
        val swap = filterSwapAt(plan)
        val width = swapWidth(plan)
        val handover = ((progress - (swap - width)) / width).coerceIn(0.0, 1.0)
        val exitAmount = ((progress - swap) / (1.0 - swap)).coerceIn(0.0, 1.0)
        val handoffTone = glide(
            TransitionFilter.OPEN_HZ.toDouble(),
            FALLBACK_HANDOFF_FLOOR_HZ,
            exitAmount.pow(FALLBACK_HANDOFF_SHAPE),
        )
        val vocalTone = glide(
            TransitionFilter.OPEN_HZ.toDouble(),
            VOCAL_SEPARATION_FLOOR_HZ,
            clash * progress.toDouble().pow(VOCAL_SAFE_EXIT_SHAPE),
        )
        outgoing.setCutoffs(minOf(handoffTone, vocalTone).toFloat(), bassCutoff(handover))
        incoming.setCutoffs(
            TransitionFilter.OPEN_HZ,
            maxOf(
                bassCutoff(1.0 - handover),
                entryHighPass(
                    progress,
                    1.0,
                    glide(FALLBACK_HANDOFF_ENTRY_HZ, VOCAL_SAFE_ENTRY_HIGH_PASS_HZ, clash),
                    vocalEntryOpenBy(plan, ENTRY_OPEN_BY, clash),
                ),
            ),
        )
    }

    private fun rise(progress: Float) = kotlin.math.sin(progress.coerceIn(0f, 1f) * Math.PI.toFloat() / 2f)
    private fun fall(progress: Float) = kotlin.math.cos(progress.coerceIn(0f, 1f) * Math.PI.toFloat() / 2f)

    /**
     * The minimum intervention: pull two colliding vocals apart, and otherwise leave the spectrum
     * alone.
     */
    private fun vocalSeparation(
        plan: TransitionPlan,
        progress: Float,
        outgoing: TransitionFilter,
        incoming: TransitionFilter,
    ) {
        val amount = plan.vocalOverlap.coerceIn(0.0, 1.0)
        if (amount <= 0.0) {
            outgoing.open()
            incoming.open()
            return
        }
        val open = TransitionFilter.OPEN_HZ.toDouble()
        // Both endpoints scaled by the collision.
        val floor = glide(open, VOCAL_SEPARATION_FLOOR_HZ, amount)
        outgoing.setCutoffs(
            glide(open, floor, progress.toDouble().pow(FILTER_SWEEP_SHAPE)).toFloat(),
            TransitionFilter.OFF_HZ,
        )
        incoming.setCutoffs(
            TransitionFilter.OPEN_HZ,
            entryHighPass(progress, amount, VOCAL_SEPARATION_HIGH_PASS_HZ, ENTRY_OPEN_BY),
        )
    }

    /**
     * Pulls the outgoing track behind a closing low-pass while the incoming one arrives with its
     * body lifted out.
     */
    private fun filterSweep(
        plan: TransitionPlan,
        progress: Float,
        outgoing: TransitionFilter,
        incoming: TransitionFilter,
    ) {
        val sweep = plan.filterSweep.coerceIn(0.0, 1.0)
        if (sweep <= 0.0) {
            outgoing.open()
            incoming.open()
            return
        }
        val open = TransitionFilter.OPEN_HZ.toDouble()
        // Both ends scaled by the sweep, so a partial one engages less sharply *and* stops short of
        // the floor rather than crawling the same distance more slowly.
        val entry = glide(open, FILTER_ENTRY_HZ, sweep)
        val floor = glide(open, FILTER_FLOOR_HZ, sweep)
        val cutoff = glide(entry, floor, progress.toDouble().pow(FILTER_SWEEP_SHAPE))
        val clash = plan.vocalOverlap.coerceIn(0.0, 1.0)
        val swap = filterSwapAt(plan)
        val width = swapWidth(plan)
        val handover = ((progress - (swap - width)) / width).coerceIn(0.0, 1.0)
        outgoing.setCutoffs(cutoff.toFloat(), bassCutoff(handover))
        incoming.setCutoffs(
            TransitionFilter.OPEN_HZ,
            maxOf(
                bassCutoff(1.0 - handover),
                entryHighPass(
                    progress,
                    sweep,
                    glide(ENTRY_HIGH_PASS_HZ, VOCAL_SAFE_ENTRY_HIGH_PASS_HZ, clash),
                    vocalEntryOpenBy(plan, ENTRY_OPEN_BY, clash),
                ),
            ),
        )
    }

    /**
     * Hands the low end from one track to the other, once, at the point in the overlap the planner
     * chose.
     */
    private fun bassSwap(
        plan: TransitionPlan,
        progress: Float,
        outgoing: TransitionFilter,
        incoming: TransitionFilter,
    ) {
        val swapAt = onBeat(plan, plan.bassSwapFraction)
        val width = swapWidth(plan)
        // 0 before the swap window, 1 after it: how much of the low end has changed hands.
        val handover = ((progress - (swapAt - width)) / width).coerceIn(0.0, 1.0)
        val clash = plan.vocalOverlap.coerceIn(0.0, 1.0)
        // The incoming track's own low end is already held out by the swap, so whichever corner
        // sits higher is the one doing the work.
        val entry = maxOf(
            bassCutoff(1.0 - handover),
            entryHighPass(
                progress,
                1.0,
                glide(BLEND_ENTRY_HIGH_PASS_HZ, BLEND_ENTRY_CLASH_HIGH_PASS_HZ, clash),
                onBar(plan, BLEND_ENTRY_OPEN_BY + (BLEND_ENTRY_CLASH_OPEN_BY - BLEND_ENTRY_OPEN_BY) * clash),
            ),
        )
        incoming.setCutoffs(TransitionFilter.OPEN_HZ, entry)
        outgoing.setCutoffs(blendExitLowPass(plan, progress, clash), bassCutoff(handover))
    }

    /**
     * The outgoing track's low-pass through a beat-matched blend: open until [BLEND_EXIT_FROM],
     * then closing by the end.
     */
    private fun blendExitLowPass(plan: TransitionPlan, progress: Float, clash: Double): Float {
        val from = onBar(plan, BLEND_EXIT_FROM + (BLEND_EXIT_CLASH_FROM - BLEND_EXIT_FROM) * clash)
        val amount = ((progress - from) / (1.0 - from)).coerceIn(0.0, 1.0)
        val floor = glide(BLEND_EXIT_LOW_PASS_HZ, BLEND_EXIT_CLASH_LOW_PASS_HZ, clash)
        return glide(TransitionFilter.OPEN_HZ.toDouble(), floor, amount).toFloat()
    }

    /** [amount] 0 leaves the low end alone; 1 lifts it out entirely. */
    private fun bassCutoff(amount: Double): Float =
        glide(TransitionFilter.OFF_HZ.toDouble(), BASS_SWAP_HZ, amount).toFloat()

    /** Where the incoming track's high-pass sits at [progress]; open by [openBy]. */
    private fun entryHighPass(progress: Float, amount: Double, topHz: Double, openBy: Double): Float {
        val remaining = (1.0 - progress / openBy).coerceIn(0.0, 1.0)
        return glide(TransitionFilter.OFF_HZ.toDouble(), topHz, amount * remaining.pow(ENTRY_SHAPE)).toFloat()
    }

    /** Geometric interpolation between two cutoffs: [amount] 0 gives [from], 1 gives [to]. */
    private fun glide(from: Double, to: Double, amount: Double): Double =
        from * (to / from).pow(amount.coerceIn(0.0, 1.0))

    private fun filterSwapAt(plan: TransitionPlan): Double = onBar(
        plan,
        when (plan.filterVariant) {
            FilterTransitionVariant.SWEEP -> FILTER_BASS_SWAP_AT
            FilterTransitionVariant.BASS_HANDOFF -> 0.43
            FilterTransitionVariant.ECHO_RIDE -> 0.58
        },
    )

    private fun swapWidth(plan: TransitionPlan): Double =
        if (plan.transitionBeats >= 2) SWAP_BEATS / plan.transitionBeats else 2 * BASS_SWAP_WIDTH

    private fun vocalEntryOpenBy(plan: TransitionPlan, base: Double, clash: Double): Double =
        onBar(plan, base + (VOCAL_SAFE_ENTRY_OPEN_BY - base) * clash)

    private fun onBeat(plan: TransitionPlan, fraction: Double): Double {
        val beats = plan.transitionBeats.toDouble()
        if (beats < 2.0) return fraction.coerceIn(0.05, 0.95)
        return (kotlin.math.round(fraction * beats) / beats).coerceIn(1.0 / beats, 1.0 - 1.0 / beats)
    }

    private fun onBar(plan: TransitionPlan, fraction: Double): Double {
        val beats = plan.transitionBeats.toDouble()
        return if (beats >= 8.0) {
            (kotlin.math.round(fraction * beats / 4.0) * 4.0 / beats)
                .coerceIn(4.0 / beats, 1.0 - 4.0 / beats)
        } else {
            onBeat(plan, fraction)
        }
    }

    // Android's numbers, not new ones: these decide how a transition sounds.
    private const val FILTER_ENTRY_HZ = 7_000.0
    private const val FILTER_FLOOR_HZ = 300.0
    private const val FILTER_SWEEP_SHAPE = 0.75
    private const val ENTRY_HIGH_PASS_HZ = 1_200.0
    private const val ENTRY_OPEN_BY = 0.6
    private const val ENTRY_SHAPE = 0.35
    private const val VOCAL_SEPARATION_FLOOR_HZ = 1_600.0
    private const val VOCAL_SEPARATION_HIGH_PASS_HZ = 700.0
    private const val BASS_SWAP_HZ = 200.0
    private const val BASS_SWAP_WIDTH = 0.10
    private const val BLEND_ENTRY_HIGH_PASS_HZ = 520.0
    private const val BLEND_ENTRY_OPEN_BY = 0.45
    private const val BLEND_ENTRY_CLASH_HIGH_PASS_HZ = 950.0
    private const val BLEND_ENTRY_CLASH_OPEN_BY = 0.7
    private const val BLEND_EXIT_FROM = 0.3
    private const val BLEND_EXIT_CLASH_FROM = 0.12
    private const val BLEND_EXIT_LOW_PASS_HZ = 2_200.0
    private const val BLEND_EXIT_CLASH_LOW_PASS_HZ = 1_100.0
    private const val FILTER_BASS_SWAP_AT = 0.5
    private const val FILTER_RISE_SHAPE = 0.8f
    private const val FILTER_FALL_SHAPE = 1.6f
    private const val FALLBACK_HANDOFF_ENTRY_HZ = 900.0
    private const val FALLBACK_HANDOFF_FLOOR_HZ = 1_600.0
    private const val FALLBACK_HANDOFF_SHAPE = 0.8
    private const val VOCAL_SAFE_ENTRY_HIGH_PASS_HZ = 2_400.0
    private const val VOCAL_SAFE_ENTRY_OPEN_BY = 0.78
    private const val VOCAL_SAFE_EXIT_SHAPE = 0.55
    private const val SWAP_BEATS = 0.5
    private const val FILTER_ECHO_AT = 0.5
    private const val ECHO_KILL_BEATS = 0.25
    private const val ECHO_LEVEL = 0.5f
    private const val ECHO_DECAY = 0.4f
    private const val ECHO_RIDE_RISE_SHAPE = 0.7f
    private const val ECHO_RIDE_FALL_SHAPE = 1.35f
    private const val VOCAL_SAFE_RISE_SHAPE = 1.65f
    private const val VOCAL_SAFE_FALL_SHAPE = 0.65f
}
