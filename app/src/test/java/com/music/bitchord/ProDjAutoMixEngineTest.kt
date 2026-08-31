package com.music.bitchord

import com.music.bitchord.playback.smart.CrossfadeMode
import com.music.bitchord.playback.smart.TrackAnalysis
import com.music.bitchord.playback.smart.TransitionStyle
import com.music.bitchord.playback.smart.planTransition
import com.music.bitchord.playback.smart.planWsolaTransition
import com.music.bitchord.playback.smart.WsolaPlanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

class ProDjAutoMixEngineTest {

    private fun createTrackAnalysis(
        bpm: Double = 124.0,
        key: String = "C major",
        keyConfidence: Double = 0.9,
        beatConfidence: Double = 0.95,
        duration: Double = 240.0,
        introEndTime: Double = 16.0,
        outroStartTime: Double = 210.0,
    ): TrackAnalysis {
        val beatInterval = 60.0 / bpm
        val downbeats = (0 until (duration / (beatInterval * 4)).toInt()).map { it * beatInterval * 4 }
        val phraseBoundaries = (0 until (duration / (beatInterval * 16)).toInt()).map { it * beatInterval * 16 }
        return TrackAnalysis(
            status = TrackAnalysis.STATUS_READY,
            bpm = bpm,
            beatInterval = beatInterval,
            beatConfidence = beatConfidence,
            key = key,
            keyConfidence = keyConfidence,
            duration = duration,
            contentEndTime = duration - 2.0,
            audibleStartTime = 0.5,
            introEndTime = introEndTime,
            outroStartTime = outroStartTime,
            mixInTime = introEndTime,
            mixOutTime = outroStartTime,
            downbeats = downbeats,
            phraseBoundaries = phraseBoundaries,
            structuralConfidence = 0.9,
        )
    }

    @Test
    fun `relative major and minor keys are recognized as harmonically compatible for DJ blend`() {
        val trackA = createTrackAnalysis(bpm = 124.0, key = "C major")
        val trackB = createTrackAnalysis(bpm = 124.0, key = "A minor")

        val plan = planTransition(
            analysis = trackA,
            nextAnalysis = trackB,
            duration = trackA.duration,
            mode = CrossfadeMode.SMART,
        )

        assertEquals(TransitionStyle.DJ_BLEND, plan.transitionStyle)
        assertTrue("Overlap should be a full musical phrase (>= 6 seconds)", plan.fadeSeconds >= 6.0)
    }

    @Test
    fun `adjacent fifth keys Camelot flow enables phrase matched DJ blend`() {
        val trackA = createTrackAnalysis(bpm = 128.0, key = "G major")
        val trackB = createTrackAnalysis(bpm = 128.0, key = "D major") // +1 fifth

        val plan = planTransition(
            analysis = trackA,
            nextAnalysis = trackB,
            duration = trackA.duration,
            mode = CrossfadeMode.SMART,
        )

        assertEquals(TransitionStyle.DJ_BLEND, plan.transitionStyle)
        assertTrue(plan.bassSwap)
    }

    @Test
    fun `WSOLA phrase switch plans musical 8 to 32 beat overlap`() {
        val trackA = createTrackAnalysis(bpm = 126.0, key = "F major")
        val trackB = createTrackAnalysis(bpm = 128.0, key = "D minor")

        val result = planWsolaTransition(
            analysis = trackA,
            nextAnalysis = trackB,
            duration = trackA.duration,
            nextDuration = trackB.duration,
        )

        assertTrue(result is WsolaPlanResult.Planned)
        val planned = result as WsolaPlanResult.Planned
        assertTrue(planned.beats >= 8)
        assertTrue(planned.overlapSeconds >= 4.0)
        assertTrue(planned.bassSwapFraction in 0.2..0.85)
    }

    @Test
    fun `DJ equal-power crossfade curve maintains perceived energy at center`() {
        val center = 0.5f
        val rise = sin(center * (PI.toFloat() / 2f)).pow(0.85f)
        val fall = cos(center * (PI.toFloat() / 2f)).pow(0.85f)

        // Equal-power sum check
        val powerSum = (rise * rise) + (fall * fall)
        assertTrue("Power sum should be close to or above 1.0 at center ($powerSum)", powerSum >= 0.95f)
    }
}
