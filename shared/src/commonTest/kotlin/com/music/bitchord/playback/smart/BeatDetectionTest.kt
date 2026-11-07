package com.music.bitchord.playback.smart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BeatDetectionTest {
    private val beats = List(48) { it * 0.5 }

    @Test fun weakPeriodicPeaksCannotAuthorizeBeatMatching() {
        assertTrue(BeatDetection.gridConfidence(beats, List(beats.size) { 0.01 }) < 0.1)
        assertTrue(BeatDetection.gridConfidence(beats, List(beats.size) { 3.0 }) > 0.55)
        val short = beats.take(8)
        assertTrue(BeatDetection.gridConfidence(short, List(short.size) { 3.0 }) < 0.55)
    }

    @Test fun aBreakDoesNotDragTheTempo() {
        val withBreak = beats.filterIndexed { index, _ -> index !in 12..17 }
        assertEquals(120.0, BeatDetection.tempoFromBeats(withBreak), 1e-9)
        assertTrue(BeatDetection.gridConfidence(withBreak, List(withBreak.size) { 3.0 }) > 0.55)
    }

    @Test fun aTempoChangeLowersConfidence() {
        val changing = List(40) { if (it < 20) it * 0.5 else 10 + (it - 20) * 0.4 }
        assertTrue(BeatDetection.gridConfidence(changing, List(changing.size) { 4.0 }) < 0.55)
    }

    @Test fun malformedEvidenceIsNeverTrusted() {
        for (bad in listOf(beats.reversed(), beats.toMutableList().apply { this[9] = this[8] },
            beats.toMutableList().apply { this[9] = Double.NaN })) {
            assertEquals(0.0, BeatDetection.tempoFromBeats(bad))
            assertEquals(0.0, BeatDetection.gridConfidence(bad, List(bad.size) { 3.0 }))
        }
        assertEquals(0.0, BeatDetection.gridConfidence(beats, listOf(3.0)))
        assertEquals(0.0, BeatDetection.gridConfidence(beats, List(beats.size) { Double.NaN }))
    }

    @Test fun aFlatPeakIsOneBeatAndNonFiniteLogitsAreIgnored() {
        val logits = FloatArray(20) { -2f }
        for (frame in 5..8) logits[frame] = 2f
        logits[15] = Float.NaN
        logits[19] = Float.POSITIVE_INFINITY
        assertEquals(listOf(6.5), BeatDetection.pickPeaks(logits))
    }

    @Test fun peaksRetainSubFrameTiming() {
        val logits = FloatArray(10) { -2f }.apply { this[3] = 0.5f; this[4] = 2f; this[5] = 1.5f }
        assertEquals(4.25, BeatDetection.pickPeaks(logits).single(), 1e-9)
    }

    @Test fun strayDownbeatsCannotJumpAcrossABreak() {
        assertEquals(listOf(0.0, 2.0, 6.0), BeatDetection.snapDownbeats(beats, listOf(0.02, 2.03, 2.04, 6.0, 8.25, Double.NaN)))
    }

    @Test fun chunkBordersCoverEveryRealFrame() {
        for (frames in listOf(1, 12, 1488, 1489, 1500, 2951, 2976, 3010, 4510)) {
            val chunks = BeatDetection.chunks(frames)
            assertEquals(-6, chunks.first().startFrame)
            if (frames > 1488) assertEquals(1500, chunks.last().frameCount)
            val covered = chunks.flatMap { chunk ->
                (chunk.startFrame + 6 until chunk.startFrame + chunk.frameCount - 6).toList()
            }.toSet()
            assertEquals((0 until frames).toSet(), covered)
            assertTrue(chunks.all { it.frameCount <= 1500 })
        }
        assertTrue(BeatDetection.chunks(0).isEmpty())
    }

    @Test fun openingAndEndingConfidenceStayIndependent() {
        fun window(start: Double, confidence: Double) = BeatWindow(start, 24.0,
            BeatSide(beats.map { it + start }, beats.filterIndexed { i, _ -> i % 4 == 0 }.map { it + start }, 120.0, confidence))
        val windows = listOf(window(0.0, 0.1), window(100.0, 0.9))
        assertEquals(0.1, assertNotNull(combineBeatWindows(windows, 0.0, 24.0)).confidence, 1e-9)
        assertEquals(0.9, assertNotNull(combineBeatWindows(windows, 100.0, 124.0)).confidence, 1e-9)
    }

    @Test fun overlappingWindowsDoNotDuplicateSeamBeats() {
        fun window(start: Double) = BeatWindow(start, 22.0,
            BeatSide(List(44) { start + it * 0.5 }, List(11) { start + it * 2.0 }, 120.0, 0.9))
        val side = assertNotNull(combineBeatWindows(listOf(window(0.0), window(20.0)), 0.0, 42.0))
        assertEquals(List(84) { it * 0.5 }, side.beats)
        assertEquals(120.0, side.bpm, 1e-9)
        assertTrue(side.downbeats.all { it in side.beats })
    }
}
