package com.music.bitchord.desktop

import com.music.bitchord.data.settings.AutomixPerformanceMode
import com.music.bitchord.playback.smart.BeatTracker
import com.music.bitchord.playback.smart.MelSpectrogram
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/** The beat model on input the length the application actually feeds it. */
class DesktopBeatStressTest {

    @Test
    fun `a full-length track is tracked without taking the process down`() {
        DesktopAnalysisRuntime.ensureStarted()
        if (!DesktopAnalysisRuntime.available) {
            println("analysis library unavailable — skipping")
            return
        }

        val tracker = BeatTracker(
            modelPath = { DesktopAnalysisRuntime.modelPath(BeatTracker.MODEL_ASSET) },
            inferenceThreads = { AutomixPerformanceMode.BALANCED.inferenceThreads },
        )
        // 153 seconds: the length of the track the application crashed on.
        val pcm = clickTrack(120.0, 153.0, MelSpectrogram.sampleRate)

        // Returning null is a legitimate answer — a metronome is not music, and the model is
        // entitled to find no confident grid in one.
        tracker.track(pcm)
    }

    @Test
    fun `tracking twice in a row reuses the session without crashing`() {
        DesktopAnalysisRuntime.ensureStarted()
        if (!DesktopAnalysisRuntime.available) return

        val tracker = BeatTracker(
            modelPath = { DesktopAnalysisRuntime.modelPath(BeatTracker.MODEL_ASSET) },
            inferenceThreads = { AutomixPerformanceMode.BALANCED.inferenceThreads },
        )
        // Back-to-back, the way the queue analyses a track and then the next.
        repeat(2) { round ->
            val pcm = clickTrack(118.0 + round, 142.0, MelSpectrogram.sampleRate)
            tracker.track(pcm)
        }
        assertTrue(true, "both passes returned rather than crashing")
    }

    private fun clickTrack(bpm: Double, seconds: Double, rate: Double): FloatArray {
        val total = (seconds * rate).toInt()
        val samples = FloatArray(total)
        val period = (60.0 / bpm * rate).toInt()
        var start = 0
        while (start < total) {
            val length = minOf(period / 4, total - start)
            for (index in 0 until length) {
                val decay = exp(-index / (rate * 0.02))
                samples[start + index] = (sin(2.0 * PI * 1_000.0 * index / rate) * decay).toFloat()
            }
            start += period
        }
        return samples
    }
}
