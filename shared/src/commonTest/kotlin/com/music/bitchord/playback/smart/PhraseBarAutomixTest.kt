package com.music.bitchord.playback.smart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The fixed-length phrase mode: when the listener picks 8 or 16 bars, the overlap is exactly that
 * many bars of the outgoing grid with both edges on the beat, and picking nothing (0) leaves the
 * adaptive plan untouched.
 */
class PhraseBarAutomixTest {

    /** A synthetic, fully analysed track on a steady grid, matching [AdvancedAutomixPlannerTest]. */
    private fun track(
        id: String,
        bpm: Double,
        key: String,
        vocal: Double,
        duration: Double = 200.0,
    ): TrackAnalysis {
        val beat = 60.0 / bpm
        val downbeats = generateSequence(0.5) { it + 4 * beat }.takeWhile { it < duration - 1 }.toList()
        val energy = (0 until (duration * 2).toInt()).map { EnergySample(it / 2.0, 1.0) }
        return TrackAnalysis(
            status = TrackAnalysis.STATUS_READY,
            trackId = id,
            duration = duration,
            bpm = bpm,
            beatInterval = beat,
            beatConfidence = 0.9,
            downbeats = downbeats,
            phraseBoundaries = downbeats.filterIndexed { index, _ -> index % 8 == 0 },
            firstBeat = 0.5,
            key = key,
            keyConfidence = 0.9,
            audibleStartTime = 0.5,
            contentEndTime = duration - 2,
            mixInTime = downbeats[4],
            energyCurve = energy,
            vocalProbability = vocal,
        )
    }

    private fun plan(current: TrackAnalysis, next: TrackAnalysis, phraseBars: Int) = planTransition(
        analysis = current,
        nextAnalysis = next,
        currentTrack = TransitionTrackInfo(current.trackId, (current.duration * 1000).toLong()),
        nextTrack = TransitionTrackInfo(next.trackId, (next.duration * 1000).toLong()),
        currentTime = 10.0,
        duration = current.duration,
        mode = CrossfadeMode.SMART,
        advanced = true,
        phraseBars = phraseBars,
    )

    @Test
    fun eightBarsIsExactlyEightBarsOnTheGrid() {
        val current = track("a", 120.0, "C major", vocal = 0.1)
        val next = track("b", 120.0, "C major", vocal = 0.1)
        val plan = plan(current, next, PHRASE_BARS_SHORT)

        assertEquals(TransitionStyle.DJ_BLEND, plan.transitionStyle, "matched tempi should beat-match")
        assertEquals(32, plan.transitionBeats, "8 bars of 4/4 is 32 beats")
        val bars = plan.fadeSeconds / (4 * (60.0 / 120.0))
        assertEquals(8.0, bars, 1e-6, "overlap of $bars bars")
        assertEquals(plan.fadeSeconds, plan.transitionEnd - plan.transitionStart, 1e-6)
        assertTrue(
            current.downbeats.any { kotlin.math.abs(it - plan.transitionStart) < 1e-6 },
            "the mix should open on a downbeat: ${plan.transitionStart}",
        )
        assertTrue(plan.transitionEnd <= current.duration + 1e-6)
    }

    @Test
    fun sixteenBarsIsExactlySixteenBarsOnTheGrid() {
        val current = track("a", 120.0, "C major", vocal = 0.1)
        val next = track("b", 120.0, "C major", vocal = 0.1)
        val plan = plan(current, next, PHRASE_BARS_LONG)

        assertEquals(64, plan.transitionBeats, "16 bars of 4/4 is 64 beats")
        val bars = plan.fadeSeconds / (4 * (60.0 / 120.0))
        assertEquals(16.0, bars, 1e-6, "overlap of $bars bars")
        assertTrue(
            plan.fadeSeconds > MAX_OVERLAP_SECONDS_FOR_TEST,
            "a 16-bar mix at 120 BPM runs 32s, past the adaptive 16s ceiling",
        )
    }

    @Test
    fun aSlowerIncomingSongIsStillSpedUpToMatchUnderPhraseMode() {
        val current = track("a", 120.0, "C major", vocal = 0.1)
        val next = track("b", 111.0, "C major", vocal = 0.1)
        val plan = plan(current, next, PHRASE_BARS_SHORT)
        assertEquals(TransitionStyle.DJ_BLEND, plan.transitionStyle)
        assertEquals(120.0 / 111.0, plan.incomingPlaybackRate, 1e-3)
        assertEquals(1.0, plan.outgoingPlaybackRate, "an audible deck must never change speed")
    }

    @Test
    fun choosingNothingLeavesTheAdaptivePlanUntouched() {
        val current = track("a", 120.0, "C major", vocal = 0.1)
        val next = track("b", 110.0, "G major", vocal = 0.1)
        val adaptive = planTransition(
            analysis = current,
            nextAnalysis = next,
            currentTrack = TransitionTrackInfo("a", 200_000),
            nextTrack = TransitionTrackInfo("b", 200_000),
            currentTime = 10.0,
            duration = 200.0,
            mode = CrossfadeMode.SMART,
            advanced = true,
        )
        assertEquals(adaptive, plan(current, next, phraseBars = 0))
    }

    private companion object {
        /** The adaptive planner's ceiling; the phrase mode deliberately runs past it. */
        const val MAX_OVERLAP_SECONDS_FOR_TEST = 16.0
    }
}
