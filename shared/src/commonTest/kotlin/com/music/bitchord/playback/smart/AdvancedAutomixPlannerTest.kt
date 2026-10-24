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
        bass: Double? = null,
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
            lowEnergyCurve = if (bass == null) emptyList() else energy.map { EnergySample(it.time, bass) },
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
        val next = track("b", 94.0, "F♯ major", vocal = 0.9)
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
    fun anUnmixablePairIsCrossfadedAtTheEndIntoTheIntro() {
        // Tempo out of reach, clashing keys, both sung: every DJ gesture put two
        // loud unsynced songs on top of each other. The plain crossfade wins.
        val current = track("a", 120.0, "C major", vocal = 0.9)
        val next = track("b", 94.0, "F♯ major", vocal = 0.9)
        val plan = plan(current, next, advanced = true)
        assertEquals(TransitionStyle.EQUAL_POWER, plan.transitionStyle)
        assertEquals(current.contentEndTime, plan.transitionEnd, 1e-6)
        assertEquals(next.audibleStartTime!!, plan.incomingCueTime, 1e-6)
        assertEquals(1.0, plan.incomingPlaybackRate)
        assertEquals(0.0, plan.echoSeconds)
        assertTrue("unmixable-pair" in plan.policyReasons)
        assertEquals(TransitionStyle.EQUAL_POWER, plan(current, next, advanced = false).transitionStyle)
    }

    @Test
    fun aTempoOnlyMismatchIsStillFiltered() {
        val current = track("a", 120.0, "C major", vocal = 0.1)
        val next = track("b", 94.0, "C major", vocal = 0.1)
        val plan = plan(current, next, advanced = true)
        assertEquals(TransitionStyle.DJ_FILTER, plan.transitionStyle)
        assertTrue(plan.fadeSeconds <= 8.0, "fallback should stay compact: ${plan.fadeSeconds}s")
    }

    @Test
    fun theFallbackGestureFollowsTheLowEnd() {
        val kicks = plan(
            track("a", 120.0, "C major", vocal = 0.1, bass = 1.0),
            track("b", 94.0, "C major", vocal = 0.1, bass = 1.0),
            advanced = true,
        )
        assertEquals(FilterTransitionVariant.BASS_HANDOFF, kicks.filterVariant)
        val sparse = plan(
            track("a", 120.0, "C major", vocal = 0.1, bass = 1.0),
            track("b", 94.0, "C major", vocal = 0.1),
            advanced = true,
        )
        assertEquals(FilterTransitionVariant.SWEEP, sparse.filterVariant)
    }

    @Test
    fun anUnmatchedPairNeverEchoesAndEntersAtItsIntro() {
        val current = track("a", 120.0, "C major", vocal = 0.1).let {
            it.copy(contentEndTime = it.duration - 20, vocalActivityMask = it.energyCurve.map { 0.0 })
        }
        for (index in 0 until 24) {
            val next = track("echo-$index", 94.0, "C major", vocal = 0.1, bass = 1.0)
                .let { it.copy(vocalActivityMask = it.energyCurve.map { 0.0 }) }
            val plan = plan(current, next, advanced = true)
            assertEquals(TransitionStyle.DJ_FILTER, plan.transitionStyle)
            assertEquals(0.0, plan.echoSeconds)
            assertNotEquals(FilterTransitionVariant.ECHO_RIDE, plan.filterVariant)
            assertEquals(next.audibleStartTime!!, plan.incomingCueTime, 1e-6, "cued into the intro, not the drop")
        }
    }

    @Test
    fun twoVocalTracksOverrideTheGestureWithVocalSafeSweep() {
        // Within the stretch window, but the incoming grid is too weak to
        // beatmatch on, so this is a filter ride — with two singers on it.
        val current = track("vocal-out", 120.0, "C major", vocal = 0.9, bass = 1.0)
        val next = track("vocal-in", 130.0, "C major", vocal = 0.9, bass = 1.0).copy(beatConfidence = 0.4)
        val plan = plan(current, next, advanced = true)
        assertEquals(TransitionStyle.DJ_FILTER, plan.transitionStyle)
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
        // 135 against 120 would need the incoming song slowed by 11%: past the 10% window.
        val current = track("a", 120.0, "C major", vocal = 0.1)
        val next = track("b", 135.0, "C major", vocal = 0.1)
        val plan = plan(current, next, advanced = true)
        assertEquals(TransitionStyle.DJ_FILTER, plan.transitionStyle)
        assertEquals(1.0, plan.incomingPlaybackRate, "past the window the incoming song keeps its tempo")
        assertEquals(1.0, plan.outgoingPlaybackRate, "an audible deck must never change speed")
        // Inside the window (130: slowed by 7.7%) both modes slow the incoming song down.
        val near = track("c", 130.0, "C major", vocal = 0.1)
        assertTrue(plan(current, near, advanced = false).incomingPlaybackRate < 1.0)
        assertEquals(1.0, plan(current, near, advanced = true).outgoingPlaybackRate)
    }

    @Test
    fun tempiPastTheStretchWindowAreNotBeatMatched() {
        val current = track("a", 120.0, "C major", vocal = 0.1)
        val next = track("b", 136.0, "C major", vocal = 0.1)
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
