package com.music.bitchord.playback.smart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AdvancedAutomixPlannerTest {

    /** A synthetic, fully analysed track on a steady grid. */
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

    private fun plan(current: TrackAnalysis, next: TrackAnalysis, advanced: Boolean) = planTransition(
        analysis = current,
        nextAnalysis = next,
        currentTrack = TransitionTrackInfo(current.trackId, (current.duration * 1000).toLong()),
        nextTrack = TransitionTrackInfo(next.trackId, (next.duration * 1000).toLong()),
        currentTime = 10.0,
        duration = current.duration,
        mode = CrossfadeMode.SMART,
        advanced = advanced,
    )

    @Test
    fun offLeavesEveryPlanAsItWas() {
        val current = track("a", 120.0, "C major", vocal = 0.9)
        val next = track("b", 97.0, "F♯ major", vocal = 0.9)
        assertEquals(
            planTransition(
                analysis = current,
                nextAnalysis = next,
                currentTrack = TransitionTrackInfo("a", 200_000),
                nextTrack = TransitionTrackInfo("b", 200_000),
                currentTime = 10.0,
                duration = 200.0,
                mode = CrossfadeMode.SMART,
            ),
            plan(current, next, advanced = false),
        )
    }

    @Test
    fun aPairThatCannotBeatMatchIsFilteredNotCut() {
        // Tempo out of reach, clashing keys, both sung: once a cut, which was
        // heard as the outgoing song glitching. Now always a filter ride.
        val current = track("a", 120.0, "C major", vocal = 0.9)
        val next = track("b", 97.0, "F♯ major", vocal = 0.9)
        val plan = plan(current, next, advanced = true)
        assertEquals(TransitionStyle.DJ_FILTER, plan.transitionStyle)
        assertEquals(1.0, plan.incomingPlaybackRate)
        assertEquals(1.0, plan.outgoingPlaybackRate)
        assertTrue(plan.fadeSeconds >= 4 * 0.5 * 2, "a filter ride, not a two-bar cut: ${plan.fadeSeconds}s")
        assertTrue(plan.fadeSeconds <= 8.0, "fallback should stay compact: ${plan.fadeSeconds}s")
    }

    @Test
    fun fallbackGestureIsStablePerPairButVariesAcrossPairs() {
        val current = track("stable-out", 120.0, "C major", vocal = 0.5)
        val variants = (0 until 24).map { index ->
            val next = track("incoming-$index", 97.0, "F♯ major", vocal = 0.5)
            val first = plan(current, next, advanced = true)
            val replay = plan(current, next, advanced = true)
            assertEquals(first.filterVariant, replay.filterVariant)
            assertTrue(first.fadeSeconds <= 8.0)
            first.filterVariant
        }.toSet()
        assertEquals(FilterTransitionVariant.entries.toSet(), variants)
    }

    @Test
    fun anInstrumentalFilterExitEchoesOnTheBeatWithRoomForTheTail() {
        // An instrumental exit ending well before the file does is safe to trail.
        val current = track("a", 120.0, "C major", vocal = 0.1).let {
            it.copy(contentEndTime = it.duration - 20, vocalActivityMask = it.energyCurve.map { 0.0 })
        }
        val plan = (0 until 100).asSequence().map { index ->
            val next = track("echo-$index", 97.0, "F♯ major", vocal = 0.1)
                .let { it.copy(vocalActivityMask = it.energyCurve.map { 0.0 }) }
            plan(current, next, advanced = true)
        }
            .first { it.filterVariant == FilterTransitionVariant.ECHO_RIDE }
        assertEquals(TransitionStyle.DJ_FILTER, plan.transitionStyle)
        assertEquals(0.5, plan.echoSeconds, 1e-6, "a one-beat echo")
        assertTrue(
            plan.transitionEnd + echoTailSeconds(plan.echoSeconds) <= current.duration,
            "the tail would outlast the outgoing file",
        )
    }

    @Test
    fun twoVocalTracksOverrideRandomFallbackWithVocalSafeSweep() {
        val current = track("vocal-out", 120.0, "C major", vocal = 0.9)
        val next = track("vocal-in", 97.0, "F♯ major", vocal = 0.9)
        val plan = plan(current, next, advanced = true)
        assertEquals(FilterTransitionVariant.SWEEP, plan.filterVariant)
        assertTrue(plan.vocalOverlap >= 0.5, "missing masks must be treated conservatively")
        assertEquals(0.0, plan.echoSeconds)
    }

    @Test
    fun aSlowerIncomingSongIsSpedUpToMatch() {
        val current = track("a", 120.0, "C major", vocal = 0.1)
        val next = track("b", 111.0, "C major", vocal = 0.1)
        val plan = plan(current, next, advanced = true)
        assertEquals(TransitionStyle.DJ_BLEND, plan.transitionStyle, "8% apart should now beat-match")
        assertEquals(120.0 / 111.0, plan.incomingPlaybackRate, 1e-3)
        assertEquals(1.0, plan.outgoingPlaybackRate)
    }

    @Test
    fun aFasterIncomingSongDoesNotRetempoTheAudibleOutgoingDeck() {
        val current = track("a", 120.0, "C major", vocal = 0.1)
        val next = track("b", 130.0, "C major", vocal = 0.1)
        val plan = plan(current, next, advanced = true)
        assertEquals(TransitionStyle.DJ_FILTER, plan.transitionStyle)
        assertEquals(1.0, plan.incomingPlaybackRate, "the incoming song is never slowed")
        assertEquals(1.0, plan.outgoingPlaybackRate, "an audible deck must never change speed")
        // Classic keeps its old answer: slow the incoming track down.
        assertTrue(plan(current, next, advanced = false).incomingPlaybackRate < 1.0)
    }

    @Test
    fun tempiMoreThanTenPercentApartAreNotBeatMatched() {
        val current = track("a", 120.0, "C major", vocal = 0.1)
        val next = track("b", 134.0, "C major", vocal = 0.1)
        val plan = plan(current, next, advanced = true)
        assertNotEquals(TransitionStyle.DJ_BLEND, plan.transitionStyle)
        assertEquals(1.0, plan.incomingPlaybackRate)
        assertEquals(1.0, plan.outgoingPlaybackRate)
    }

    @Test
    fun aBlendNeverEchoes() {
        val current = track("a", 120.0, "C major", vocal = 0.9)
        val next = track("b", 120.0, "C major", vocal = 0.9)
        assertEquals(0.0, plan(current, next, advanced = true).echoSeconds)
    }

    @Test
    fun neverSkipsMoreThanAQuarterOfTheIncomingSong() {
        val current = track("a", 120.0, "C major", vocal = 0.1)
        // A main drop three quarters of the way in outranks everything else.
        val next = track("b", 120.0, "C major", vocal = 0.1).let {
            it.copy(mixInCandidates = listOf(MixCandidate(it.downbeats[70], 1.0, "main_drop")), mixInTime = it.downbeats[70])
        }
        assertTrue(next.downbeats[70] > 0.25 * next.duration, "the fixture's drop must sit past the limit")
        for (advanced in listOf(false, true)) {
            val plan = plan(current, next, advanced)
            assertTrue(
                plan.incomingCueTime <= 0.25 * next.duration + 1e-6,
                "advanced=$advanced cued the incoming song at ${plan.incomingCueTime}s of ${next.duration}s",
            )
        }
    }

    @Test
    fun anAdvancedOverlapIsWholeBars() {
        val current = track("a", 120.0, "C major", vocal = 0.1)
        val next = track("b", 110.0, "G major", vocal = 0.1)
        val plan = plan(current, next, advanced = true)
        assertTrue(plan.beatSeconds > 0, "a trusted grid should be passed to the renderer")
        val bars = plan.fadeSeconds / (4 * plan.beatSeconds)
        assertEquals(bars.toInt().toDouble(), bars, 1e-6, "overlap of $bars bars")
        assertTrue(!plan.phaseLock, "phase correction must not pulse AudioTrack speed while audible")
    }
}
