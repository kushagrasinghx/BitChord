package com.music.bitchord.playback.smart

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The web player's engine/loopMix.test.ts, ported: seams, wraps, folds, vocal
 * rejection and the 1-bar fallback. The web's AudioBuffer loudness becomes the
 * analysis's level windows here.
 */
class LoopMixTest {

    /** Loud everywhere except where [quiet] says, at the analyzer's level rate. */
    private fun levels(duration: Double, quiet: List<Pair<Double, Double>> = emptyList()): List<CurveWindow> {
        val frames = (duration * LEVEL_FRAME_RATE).roundToInt()
        val values = FloatArray(frames) { index ->
            val t = index / LEVEL_FRAME_RATE
            if (quiet.any { (a, b) -> t >= a && t < b }) 0f else levelFromRms(0.3).toFloat()
        }
        return listOf(CurveWindow(0.0, LEVEL_FRAME_RATE, values))
    }

    private fun analysis(
        bpm: Double,
        duration: Double,
        vocals: List<CurveWindow> = emptyList(),
        quiet: List<Pair<Double, Double>> = emptyList(),
    ) = TrackAnalysis(
        status = TrackAnalysis.STATUS_READY,
        duration = duration,
        bpm = bpm,
        beatInterval = 60 / bpm,
        beatConfidence = 0.8,
        downbeats = listOf(0.5),
        firstBeat = 0.5,
        vocalWindows = vocals,
        levelWindows = levels(duration, quiet),
    )

    /** A vocal window that is [level] everywhere except [clean] spans. */
    private fun vocals(from: Double, to: Double, level: Float, clean: List<Pair<Double, Double>> = emptyList()): CurveWindow {
        val fps = 10.0
        val values = FloatArray(((to - from) * fps).roundToInt()) { index ->
            val t = from + index / fps
            if (clean.any { (a, b) -> t >= a && t < b }) 0.05f else level
        }
        return CurveWindow(from, fps, values)
    }

    /** Synced lyrics with nothing sung in the searched stretches: every loop is clear. */
    private val clear = emptyList<LoopMix.Span>()

    private fun plan(
        out: TrackAnalysis,
        inn: TrackAnalysis,
        sungOut: List<LoopMix.Span>? = clear,
        sungIn: List<LoopMix.Span>? = clear,
    ) = LoopMix.plan(out, out.duration, inn, inn.duration, 0.0, sungOut, sungIn)

    @Test
    fun loopsWholeBarsOutgoingNearItsEndIncomingAtItsStart() {
        val plan = assertNotNull(plan(analysis(124.0, 200.0), analysis(124.0, 180.0)))
        val bar = 60.0 / 124 * 4
        for (pick in listOf(plan.out, plan.inn)) {
            val bars = (pick.loopStart - 0.5) / bar
            assertTrue(abs(bars - bars.roundToInt()) < 1e-9)
            assertEquals(LoopMix.LOOP_BARS * bar, pick.loopEnd - pick.loopStart, 1e-9)
        }
        assertTrue(plan.out.loopEnd <= 199.5)
        assertTrue(plan.out.loopStart > 200 - 60)
        assertEquals(0.5, plan.inn.loopStart, 1e-9)
    }

    @Test
    fun prefersBarsTheVocalMaskCallsInstrumental() {
        // sung throughout except 150-170 s on the way out and 20-35 s on the way in
        val out = analysis(120.0, 200.0, listOf(vocals(130.0, 200.0, 0.8f, listOf(150.0 to 170.0))))
        val inn = analysis(120.0, 180.0, listOf(vocals(0.0, 45.0, 0.8f, listOf(20.0 to 35.0))))
        val plan = assertNotNull(plan(out, inn, null, null))
        assertTrue(plan.out.loopStart >= 150)
        assertTrue(plan.out.loopEnd <= 170 + 0.8)
        assertTrue(plan.out.vocal <= LoopMix.MAX_SUNG_SHARE)
        assertTrue(plan.inn.loopStart >= 20)
        assertTrue(plan.inn.loopEnd <= 35 + 0.8)
        assertEquals(LoopMix.Check.MASK, plan.out.checkedBy)
    }

    @Test
    fun skipsAQuietFadeOutForALoopThatGrooves() {
        val out = analysis(120.0, 200.0, quiet = listOf(185.0 to 200.0))
        val plan = assertNotNull(plan(out, analysis(120.0, 180.0)))
        assertTrue(plan.out.loopEnd <= 185)
    }

    @Test
    fun rampsAnyTempoGapFoldingToDoubleOrHalfTimeWhenCloser() {
        val near = assertNotNull(plan(analysis(120.0, 200.0), analysis(134.0, 180.0)))
        assertEquals(1.0, near.fold)
        assertEquals(ceil((134.0 - 120) / 2.5).toInt(), near.rampBars)
        // 111 -> 169 is 58 BPM up, but only 26.5 down to 169's half time: that's the ramp taken
        val wide = assertNotNull(plan(analysis(111.0, 200.0), analysis(169.0, 180.0)))
        assertEquals(2.0, wide.fold)
        assertEquals(ceil((111 - 169.0 / 2) / 2.5).toInt(), wide.rampBars)
        val slow = assertNotNull(plan(analysis(170.0, 200.0), analysis(80.0, 180.0)))
        assertEquals(0.5, slow.fold)
        // the incoming loop can only let go at its own boundary
        for (p in listOf(near, wide, slow)) {
            val inLoopOutBars = LoopMix.LOOP_BARS / p.fold
            assertEquals(0.0, p.totalBars % inLoopOutBars, 1e-9)
        }
    }

    @Test
    fun cutsEachLoopOnDetectedDownbeatsTempoDriftAndAll() {
        // a live-played song: the beat stretches 0.5% over the minute, so a constant grid would miss
        val beats = ArrayList<Double>()
        var t = 140.0
        var beat = 60.0 / 120
        while (t < 199.5) {
            beats += t
            t += beat
            beat *= 1.0002
        }
        val downbeats = beats.filterIndexed { index, _ -> index % 4 == 0 }
        val out = analysis(120.0, 200.0).copy(tailBeats = BeatSide(beats, downbeats, 120.0, 0.9))
        val plan = assertNotNull(plan(out, analysis(120.0, 180.0)))
        assertTrue(plan.out.loopStart in downbeats)
        assertTrue(plan.out.loopEnd in downbeats)
        assertEquals(LoopMix.LOOP_BARS, downbeats.indexOf(plan.out.loopEnd) - downbeats.indexOf(plan.out.loopStart))
        // the loop's tempo is its own length over 16 beats
        assertEquals((plan.out.loopEnd - plan.out.loopStart) / 16, plan.out.beatSec, 1e-12)
    }

    @Test
    fun neverCutsOnADownbeatTheModelPutOnTheWrongBeat() {
        val beat = 0.5
        // every bar line at 140 + 2k s, except the one at 190 s, which the model put two beats late
        val downbeats = List(30) { 140 + it * 4 * beat }.map { if (abs(it - 190) < 0.01) 191.0 else it }
        val out = analysis(120.0, 200.0).copy(tailBeats = BeatSide(downbeats, downbeats, 120.0, 0.9))
        val plan = assertNotNull(plan(out, analysis(120.0, 180.0)))
        assertTrue(plan.out.loopStart != 191.0 && plan.out.loopEnd != 191.0)
        assertEquals(LoopMix.LOOP_BARS * 4 * beat, plan.out.loopEnd - plan.out.loopStart, 1e-9)
    }

    @Test
    fun clockWrapsOnlyAtLoopEndsAndTheIncomingSongArrivesAtItsOwnSpeed() {
        val plan = assertNotNull(plan(analysis(111.0, 200.0), analysis(169.0, 180.0)))
        val clock = LoopMix.Clock(plan)
        // each beat's position follows from the last at the beat's own rate; a wrap only at a loop end
        for (side in LoopMix.Side.entries) {
            val pick = if (side == LoopMix.Side.OUT) plan.out else plan.inn
            var previous = LoopMix.positionAt(clock, side, 0.0)!!
            for (k in 1 until clock.beats) {
                val position = LoopMix.positionAt(clock, side, clock.beatTimes[k])!!
                val step = if (side == LoopMix.Side.OUT) pick.beatSec else plan.fold * pick.beatSec
                var expected = previous + step
                if (expected >= pick.loopEnd - 1e-9) expected -= pick.loopEnd - pick.loopStart
                assertEquals(expected, position, 1e-6)
                previous = position
            }
        }
        // the ramp ends at the incoming song's own tempo, so its last looped beat plays at rate 1
        val last = clock.beats - 1
        val rate = clock.tempo(last) * plan.fold * plan.inn.beatSec / 60
        assertEquals(1.0, rate, 1e-9)
        // and the unwrapped incoming position at release is a whole number of its loops
        val loops = (clock.inReleasePosition - plan.inn.loopStart) / (plan.inn.loopEnd - plan.inn.loopStart)
        assertEquals(loops.roundToInt().toDouble(), loops, 1e-9)
        assertEquals(plan.inn.loopEnd + (clock.elementAt - clock.releaseAt), clock.elementTrackTime, 1e-9)
        assertTrue(clock.handoffAt > 0 && clock.handoffAt < clock.releaseAt)
    }

    @Test
    fun maskStatsCountsFramesOverTheThresholdAndIsNullWhereTheMaskDoesNotReach() {
        val windows = listOf(vocals(0.0, 10.0, 0.8f, listOf(0.0 to 5.0)))
        assertEquals(0.5, LoopMix.maskStats(windows, 0.0, 10.0)!!.share, 1e-9)
        assertEquals(0.0, LoopMix.maskStats(windows, 0.0, 5.0)!!.share, 1e-9)
        assertNull(LoopMix.maskStats(windows, 8.0, 14.0))
    }

    @Test
    fun positionIsNothingBeforeTheMixAndTheLoopStartOnItsFirstBeat() {
        val plan = assertNotNull(plan(analysis(111.0, 200.0), analysis(169.0, 180.0)))
        val clock = LoopMix.Clock(plan)
        assertNull(LoopMix.positionAt(clock, LoopMix.Side.OUT, -0.1))
        assertEquals(plan.out.loopStart, LoopMix.positionAt(clock, LoopMix.Side.OUT, 0.0)!!, 1e-9)
        assertEquals(plan.inn.loopStart, LoopMix.positionAt(clock, LoopMix.Side.IN, 0.0)!!, 1e-9)
    }

    @Test
    fun positionStaysInsideEachLoopWrappingBackToItsStart() {
        val plan = assertNotNull(plan(analysis(111.0, 200.0), analysis(169.0, 180.0)))
        val clock = LoopMix.Clock(plan)
        var sawWrap = false
        var last = -1.0
        var t = 0.0
        while (t < clock.releaseAt) {
            val out = LoopMix.positionAt(clock, LoopMix.Side.OUT, t)!!
            assertTrue(out >= plan.out.loopStart - 1e-9 && out <= plan.out.loopEnd + 1e-9)
            if (out < last - 1) sawWrap = true
            last = out
            val inn = LoopMix.positionAt(clock, LoopMix.Side.IN, t)!!
            assertTrue(inn >= plan.inn.loopStart - 1e-9 && inn <= plan.inn.loopEnd + 1e-9)
            t += 0.05
        }
        assertTrue(sawWrap)
    }

    @Test
    fun theIncomingSongGoesOnFromItsLoopEndInRealTimeAndTheOutgoingOneStops() {
        val plan = assertNotNull(plan(analysis(111.0, 200.0), analysis(169.0, 180.0)))
        val clock = LoopMix.Clock(plan)
        assertNull(LoopMix.positionAt(clock, LoopMix.Side.OUT, clock.releaseAt + 0.1))
        assertEquals(plan.inn.loopEnd, LoopMix.positionAt(clock, LoopMix.Side.IN, clock.releaseAt)!!, 1e-9)
        assertEquals(clock.elementTrackTime, LoopMix.positionAt(clock, LoopMix.Side.IN, clock.elementAt)!!, 1e-9)
    }

    @Test
    fun beatPhaseIsZeroOnEveryScheduledBeat() {
        val plan = assertNotNull(plan(analysis(111.0, 200.0), analysis(169.0, 180.0)))
        val clock = LoopMix.Clock(plan)
        assertEquals(0.0, LoopMix.beatPhaseAt(clock, clock.beatTimes[5])!!, 1e-9)
        val mid = (clock.beatTimes[5] + clock.beatTimes[6]) / 2
        assertEquals(0.5, LoopMix.beatPhaseAt(clock, mid)!!, 1e-9)
        assertNull(LoopMix.beatPhaseAt(clock, -1.0))
    }

    @Test
    fun skipsALoopASyncedLineOverlapsAndOneALineIsStillEndingInto() {
        // out song: sung until 172.2 s, then a line from 189 s; the latest clean 4 bars end before 189
        val sung = listOf(LoopMix.Span(150.0, 172.2), LoopMix.Span(189.0, 199.0))
        val plan = assertNotNull(plan(analysis(120.0, 200.0), analysis(120.0, 180.0), sung, clear))
        for (span in sung) assertTrue(plan.out.loopStart - 0.3 >= span.end || plan.out.loopEnd <= span.start)
        assertEquals(LoopMix.Check.LYRICS, plan.out.checkedBy)
    }

    @Test
    fun callsASoftVerseSung() {
        // mask 0.3 everywhere but 176-186 s, as Afsos measured
        val out = analysis(120.0, 200.0, listOf(vocals(130.0, 200.0, 0.3f, listOf(176.0 to 186.0))))
        val plan = assertNotNull(plan(out, analysis(120.0, 180.0), null, clear))
        assertTrue(plan.out.loopStart >= 176.5)
        assertTrue(plan.out.loopEnd <= 186)
        assertEquals(LoopMix.Check.MASK, plan.out.checkedBy)
    }

    @Test
    fun fallsBackToA1BarLoopWhenOnlyOneCleanBarExists() {
        // sung everywhere in the search but 180.5-182.5 (one bar)
        val sung = listOf(LoopMix.Span(100.0, 180.2), LoopMix.Span(182.6, 199.0))
        val plan = assertNotNull(plan(analysis(120.0, 200.0), analysis(120.0, 180.0), sung, clear))
        assertEquals(1, plan.out.bars)
        assertEquals(4 * 0.5, plan.out.loopEnd - plan.out.loopStart, 1e-9)
    }

    @Test
    fun anOpenLinesGuessedTailIsLeftToTheMask() {
        // Line-synced lyrics: a line every 8 s from 140 s, none with an end, so each reads as
        // sung until the next. The mask says the singing stops 3 s into each line.
        val lines = (0 until 8).map { LoopMix.Line((140_000 + it * 8_000).toLong(), null, blank = false) }
        val spans = LoopMix.sungSpans(lines)
        val quiet = (0 until 8).map { (140.0 + it * 8 + 3) to (140.0 + it * 8 + 8) }
        val out = analysis(120.0, 200.0, listOf(vocals(130.0, 200.0, 0.8f, quiet)))
        val plan = assertNotNull(plan(out, analysis(120.0, 180.0), spans, clear))
        assertEquals(LoopMix.Check.LYRICS_AND_MASK, plan.out.checkedBy)
        // never over a line's first words
        for (span in spans) assertTrue(plan.out.loopStart - 0.3 >= span.certainUntil || plan.out.loopEnd <= span.start)
        // and without a mask, the guessed tails still block it
        assertNull(plan(analysis(120.0, 200.0, quiet = listOf(176.0 to 200.0)), analysis(120.0, 180.0), spans, clear))
    }

    @Test
    fun makesNoLoopMixWhenNoBarIsInstrumental() {
        assertNull(plan(analysis(120.0, 200.0), analysis(120.0, 180.0), listOf(LoopMix.Span(0.0, 200.0)), clear))
    }

    @Test
    fun turnsSyncedLinesIntoPaddedSpansALineWithoutAnEndSungUntilTheNext() {
        val spans = LoopMix.sungSpans(
            listOf(
                LoopMix.Line(10_000, 12_000, blank = false),
                LoopMix.Line(13_000, null, blank = false),
                LoopMix.Line(16_000, null, blank = true),
                LoopMix.Line(20_000, 21_000, blank = false),
            ),
        )
        assertEquals(9.85, spans[0].start, 1e-9)
        assertEquals(12.35, spans[0].end, 1e-9)
        assertEquals(16.35, spans[1].end, 1e-9)
        assertEquals(3, spans.size)
    }

    @Test
    fun musicVideosGetNoLoopMixOnEitherSide() {
        val out = analysis(120.0, 200.0)
        val inn = analysis(120.0, 180.0)
        assertNull(LoopMix.plan(out, 200.0, inn, 180.0, 0.0, clear, clear, outVideo = true))
        assertNull(LoopMix.plan(out, 200.0, inn, 180.0, 0.0, clear, clear, inVideo = true))
    }

    @Test
    fun noMaskAndNoLyricsMeansNoLoop() {
        assertNull(plan(analysis(120.0, 200.0), analysis(120.0, 180.0), null, null))
    }

    @Test
    fun prefersPhraseAlignedLoops() {
        // Track at 120 BPM: 1 bar = 2.0s, 4 bars = 8.0s.
        // Downbeats start at 0.5s: 0.5, 2.5, 4.5, 6.5, 8.5, 10.5, 12.5, 14.5, 16.5, 18.5...
        // Candidate 4-bar loops for incoming start at 0.5, 2.5, 4.5, 6.5, 8.5...
        // If musical phrase boundaries land at 8.5s and 16.5s, the loop starting at 8.5s should be chosen over 0.5s.
        val inn = analysis(120.0, 180.0).copy(
            phraseBoundaries = listOf(8.5, 16.5, 24.5),
        )
        val p = assertNotNull(plan(analysis(120.0, 200.0), inn))
        assertEquals(8.5, p.inn.loopStart, 1e-9)
    }

    @Test
    fun smoothSCurveRampEasesTempoTransitions() {
        val p = assertNotNull(plan(analysis(120.0, 200.0), analysis(140.0, 180.0)))
        val clock = LoopMix.Clock(p)
        // Before ramp: exactly 120.0 BPM
        assertEquals(120.0, clock.tempo(clock.rampFrom - 1), 1e-9)
        // Mid-ramp beats average to exactly 130.0 BPM (50% progress)
        val midBeatA = (clock.rampFrom + clock.rampTo) / 2 - 1
        val midBeatB = (clock.rampFrom + clock.rampTo) / 2
        assertEquals(130.0, (clock.tempo(midBeatA) + clock.tempo(midBeatB)) / 2, 1e-9)
        assertTrue(abs(clock.tempo(midBeatB) - 130.0) < 1.0)
        // End of ramp and beyond: exactly target BPM (140.0)
        assertEquals(140.0, clock.tempo(clock.rampTo), 1e-9)
        // S-curve derivative near start is smaller than linear slope
        val nearStartTempo = clock.tempo(clock.rampFrom)
        val linearDelta = (140.0 - 120.0) * (0.5 / (clock.rampTo - clock.rampFrom))
        assertTrue(nearStartTempo - 120.0 < linearDelta)
    }

    @Test
    fun keyClashShortensEnterBars() {
        // C Major (8B) and F# Major (2B) clash (tritone / opposite side of Camelot wheel)
        val out = analysis(120.0, 200.0).copy(key = "C major", keyConfidence = 0.9)
        val inn = analysis(120.0, 180.0).copy(key = "F♯ major", keyConfidence = 0.9)
        val p = assertNotNull(plan(out, inn))
        assertTrue(p.keyClash)
        assertEquals(2, p.enterBars)

        // Harmonically compatible: C major and G major (distance 1 on circle of fifths)
        val innCompatible = analysis(120.0, 180.0).copy(key = "G major", keyConfidence = 0.9)
        val pComp = assertNotNull(plan(out, innCompatible))
        assertTrue(!pComp.keyClash)
        assertEquals(LoopMix.ENTER_BARS, pComp.enterBars)
    }
}
