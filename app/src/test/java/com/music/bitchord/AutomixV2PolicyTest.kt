package com.music.bitchord

import com.music.bitchord.playback.smart.AutomixCandidate
import com.music.bitchord.playback.smart.AutomixPreservation
import com.music.bitchord.playback.smart.MusicalSection
import com.music.bitchord.playback.smart.MusicalSectionType
import com.music.bitchord.playback.smart.QueueOrigin
import com.music.bitchord.playback.smart.TrackAnalysis
import com.music.bitchord.playback.smart.TransitionTier
import com.music.bitchord.playback.smart.assessTransitionTier
import com.music.bitchord.playback.smart.maximumStretch
import com.music.bitchord.playback.smart.measureLoudness
import com.music.bitchord.playback.smart.overlapHeadroomDb
import com.music.bitchord.playback.smart.preserveMixOut
import com.music.bitchord.playback.smart.scoreAutomixCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AutomixV2PolicyTest {
    private fun analysis(bpm: Double = 120.0, confidence: Double = .9) = TrackAnalysis(
        status = TrackAnalysis.STATUS_READY,
        bpm = bpm,
        beatInterval = 60 / bpm,
        beatConfidence = confidence,
        duration = 200.0,
        contentEndTime = 200.0,
        structuralConfidence = .85,
    )

    @Test fun `octave bpm pair can beatmatch`() {
        assertEquals(TransitionTier.BEATMATCHED, assessTransitionTier(analysis(63.0), analysis(126.0)).tier)
    }

    @Test fun `assisted tier never earns stretch`() {
        val verdict = assessTransitionTier(analysis(120.0, .4), analysis(123.0, .4))
        assertEquals(TransitionTier.DJ_ASSISTED, verdict.tier)
    }

    @Test fun `freedom rail is six percent and balanced is four`() {
        assertEquals(.04, maximumStretch(AutomixPreservation.BALANCED), 0.0)
        assertEquals(.06, maximumStretch(AutomixPreservation.DJ_FREEDOM), 0.0)
    }

    @Test fun `low structure confidence degrades cuts to full track`() {
        val weak = analysis().copy(structuralConfidence = .2)
        assertTrue(preserveMixOut(weak, 160.0, AutomixPreservation.DJ_FREEDOM) >= 198.0)
    }

    @Test fun `balanced never cuts through a protected chorus`() {
        val song = analysis().copy(
            sections = listOf(MusicalSection(186.0, 197.0, MusicalSectionType.CHORUS, .9, .9, .9)),
            phraseBoundaries = listOf(184.0, 192.0, 200.0),
        )
        assertTrue(preserveMixOut(song, 188.0, AutomixPreservation.BALANCED) >= 197.0)
    }

    @Test fun `play next outranks every dj candidate`() {
        val current = analysis()
        val playNext = AutomixCandidate("fixed", analysis(90.0), 4, QueueOrigin.PLAY_NEXT, false)
        val perfect = AutomixCandidate("perfect", analysis(120.0), 0, QueueOrigin.AUTOPLAY, true)
        assertTrue(scoreAutomixCandidate(current, playNext, .6) > scoreAutomixCandidate(current, perfect, .6))
    }

    @Test fun `loudness and true peak stay finite for synthetic pcm`() {
        val rate = 11_025.0
        val pcm = FloatArray(rate.toInt() * 4) { i -> (sin(2 * PI * 440 * i / rate) * .25).toFloat() }
        val result = requireNotNull(measureLoudness(pcm, rate))
        assertTrue(result.integratedLufs.isFinite())
        assertTrue(result.truePeakDbtp < 0.0)
    }

    @Test fun `overlap reserves true peak ceiling`() {
        assertTrue(overlapHeadroomDb(-.2, -.2, 0.0, 0.0) < -6.0)
    }
}
