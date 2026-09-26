package com.music.bitchord.desktop

import com.music.bitchord.playback.TransitionFilter
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
            TransitionStyle.DJ_FILTER -> filterSweep(plan, progress, outgoing, incoming)
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
    }

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
        outgoing.setCutoffs(cutoff.toFloat(), TransitionFilter.OFF_HZ)
        incoming.setCutoffs(
            TransitionFilter.OPEN_HZ,
            entryHighPass(progress, sweep, ENTRY_HIGH_PASS_HZ, ENTRY_OPEN_BY),
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
        val swapAt = plan.bassSwapFraction.coerceIn(0.05, 0.95)
        // 0 before the swap window, 1 after it: how much of the low end has changed hands.
        val handover = ((progress - swapAt) / BASS_SWAP_WIDTH * 0.5 + 0.5).coerceIn(0.0, 1.0)
        val clash = plan.vocalOverlap.coerceIn(0.0, 1.0)
        // The incoming track's own low end is already held out by the swap, so whichever corner
        // sits higher is the one doing the work.
        val entry = maxOf(
            bassCutoff(1.0 - handover),
            entryHighPass(
                progress,
                1.0,
                glide(BLEND_ENTRY_HIGH_PASS_HZ, BLEND_ENTRY_CLASH_HIGH_PASS_HZ, clash),
                BLEND_ENTRY_OPEN_BY + (BLEND_ENTRY_CLASH_OPEN_BY - BLEND_ENTRY_OPEN_BY) * clash,
            ),
        )
        incoming.setCutoffs(TransitionFilter.OPEN_HZ, entry)
        outgoing.setCutoffs(blendExitLowPass(progress, clash), bassCutoff(handover))
    }

    /**
     * The outgoing track's low-pass through a beat-matched blend: open until [BLEND_EXIT_FROM],
     * then closing by the end.
     */
    private fun blendExitLowPass(progress: Float, clash: Double): Float {
        val from = BLEND_EXIT_FROM + (BLEND_EXIT_CLASH_FROM - BLEND_EXIT_FROM) * clash
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
}
