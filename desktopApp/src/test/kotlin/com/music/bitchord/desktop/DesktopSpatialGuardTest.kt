package com.music.bitchord.desktop

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The one rule about spatial audio that is not about the widening itself: it must not run on a
 * Dolby Atmos stream.
 */
class DesktopSpatialGuardTest {

    @Test
    fun `a stream that declares Atmos is recognised, an ordinary one is not`() {
        assertTrue(
            DesktopStream(url = "x", isDolbyAtmos = true).isDolbyAtmos,
            "an Atmos rendition did not report itself",
        )
        assertTrue(
            !DesktopStream(url = "x", format = DesktopStreamFormat(codec = "mp4a", kbps = 320)).isDolbyAtmos,
            "an ordinary AAC stream claimed to be Atmos",
        )
    }

    @Test
    fun `the widener leaves a mono track alone and widens a stereo one`() {
        val rate = 44_100

        // Mono has no mid and side to widen, and Android declines it rather than mangling it — the
        // case that used to make every local mono file unplayable there.
        val mono = DesktopSpatialAudio(1, rate).apply { enabled = true }
        assertTrue(!mono.applies, "a mono track was accepted for widening")
        val monoSamples = FloatArray(2_000) { sin(it * 0.01).toFloat() }
        val untouched = monoSamples.copyOf()
        mono.process(monoSamples, monoSamples.size)
        assertTrue(monoSamples.contentEquals(untouched), "a mono track was widened anyway")

        // Stereo is widened: the difference between the channels grows.
        val stereo = DesktopSpatialAudio(2, rate).apply { enabled = true }
        val samples = FloatArray(8_000) { index ->
            val frame = index / 2
            val phase = if (index % 2 == 0) 0.0 else 0.6
            (sin(2.0 * PI * 440 * frame / rate + phase) * 0.5).toFloat()
        }
        val before = side(samples)
        stereo.process(samples, samples.size)
        assertTrue(side(samples) > before * 1.5, "stereo was not widened")
        // And nothing left the representable range in a way a sink would clip catastrophically —
        // the widener has makeup attenuation for this.
        assertTrue(samples.all { abs(it) < 2f }, "widening blew the signal up")
    }

    private fun side(samples: FloatArray): Double {
        var sum = 0.0
        var index = 0
        while (index + 1 < samples.size) {
            val value = (samples[index] - samples[index + 1]) * 0.5
            sum += value * value
            index += 2
        }
        return sum
    }
}
