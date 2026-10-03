package com.music.bitchord.desktop

import com.music.bitchord.playback.smart.MelSpectrogram
import com.music.bitchord.playback.smart.TrackFeatures
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The Automix analyser, running on the desktop through the same native code Android compiles. */
class DesktopAnalysisTest {

    @Test
    fun `the native analyser loads and reads a click track's tempo`() {
        DesktopAnalysisRuntime.ensureStarted()
        if (!DesktopAnalysisRuntime.available) {
            // A build made without CMake has no analyser and no Automix, which is a supported
            // configuration rather than a failure.
            println("analysis library unavailable — skipping")
            return
        }
        assertTrue(TrackFeatures.available, "the library loaded but TrackFeatures disagrees")

        val bpm = 120.0
        val seconds = 40.0
        val samples = clickTrack(bpm, seconds, TrackFeatures.sampleRate)

        val features = assertNotNull(
            TrackFeatures.analyze(samples, seconds),
            "the analyser declined a plain click track",
        )

        // Half or double time is a defensible reading of a bare click, so the check is on the beat
        // period modulo octaves rather than the raw figure.
        val reported = features.bpm
        assertTrue(reported > 0, "no tempo was reported")
        val ratio = generateSequence(reported) { it * 2 }.take(4)
            .plus(generateSequence(reported) { it / 2 }.take(4))
            .minOf { abs(it - bpm) }
        assertTrue(ratio < 6.0, "read $reported bpm from a $bpm bpm click track")
    }

    @Test
    fun `the mel front end reports the rate and hop the model was trained on`() {
        DesktopAnalysisRuntime.ensureStarted()
        if (!DesktopAnalysisRuntime.available) return

        // Dictated by the trained network, not chosen: 22,050 Hz with a hop of 441 is exactly fifty
        // frames a second, which is what beat times are derived from.
        assertTrue(abs(MelSpectrogram.sampleRate - 22_050.0) < 1.0, "rate ${MelSpectrogram.sampleRate}")
        assertTrue(MelSpectrogram.hop == 441, "hop ${MelSpectrogram.hop}")
        assertTrue(MelSpectrogram.mels == 128, "mels ${MelSpectrogram.mels}")

        val seconds = 4.0
        val spectrogram = assertNotNull(
            MelSpectrogram.compute(clickTrack(120.0, seconds, MelSpectrogram.sampleRate)),
            "the front end produced nothing for four seconds of audio",
        )
        val expectedFrames = (seconds * MelSpectrogram.frameRate).toInt()
        assertTrue(
            abs(spectrogram.frames - expectedFrames) < expectedFrames / 10,
            "got ${spectrogram.frames} frames, wanted about $expectedFrames",
        )
        assertTrue(spectrogram.values.size == spectrogram.frames * spectrogram.mels)
    }

    /** Short decaying tone bursts, one per beat — a metronome, in samples. */
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
