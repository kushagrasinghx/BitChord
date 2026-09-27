package com.music.bitchord

import com.music.bitchord.playback.smart.VersionAudioAligner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * [VersionAudioAligner.crossCorrelate] now runs the per-lag sums through an
 * FFT instead of the double loop it replaced. These tests pin the two
 * implementations together: same searchable range, same winning lag, same
 * confidence — a silent sign flip or wraparound here would turn every switch
 * into a misaligned one.
 */
class VersionAudioAlignerCorrelationTest {

    @Test
    fun `fft correlation matches brute force over many random envelopes`() {
        val random = Random(42)
        repeat(40) { case ->
            val sourceSize = 320 + random.nextInt(600)
            val targetSize = 320 + random.nextInt(600)
            val source = FloatArray(sourceSize) { random.nextFloat() }
            // Shift the target by a known lag and add noise, so the peak lands
            // somewhere plausible but not trivially at zero.
            val trueLag = random.nextInt(-150, 151)
            val target = FloatArray(targetSize) { index ->
                val sourceIndex = index - trueLag
                val base = if (sourceIndex in source.indices) source[sourceIndex] else random.nextFloat()
                (base + random.nextFloat() * 0.2f).coerceIn(0f, 1f)
            }

            val fft = VersionAudioAligner.crossCorrelate(source, target)
            val brute = bruteForce(source, target)

            assertEquals("floor differs in case $case", brute.floor, fft.floor)
            assertEquals("ceiling differs in case $case", brute.ceiling, fft.ceiling)
            assertEquals("best lag differs in case $case", brute.lagFrames, fft.lagFrames)
            assertTrue(
                "confidence differs in case $case: ${brute.confidence} vs ${fft.confidence}",
                (brute.confidence - fft.confidence).absoluteValue < 1e-6,
            )
        }
    }

    @Test
    fun `correlation finds a planted lag with high confidence`() {
        val random = Random(7)
        val source = FloatArray(900) { random.nextFloat() }
        val trueLag = 286 // 28.6 s at 100 fps — the field case's shape
        val target = FloatArray(900) { index ->
            val sourceIndex = index - trueLag
            if (sourceIndex in source.indices) source[sourceIndex] else random.nextFloat() * 0.3f
        }

        val result = VersionAudioAligner.crossCorrelate(source, target)

        assertEquals(trueLag, result.lagFrames)
        assertTrue("expected a confident peak, got ${result.confidence}", result.confidence > 0.9)
        assertTrue("peak must not be pinned to the searchable edge", result.lagFrames != result.floor)
        assertTrue("peak must not be pinned to the searchable edge", result.lagFrames != result.ceiling)
    }

    @Test
    fun `empty envelopes yield no measurable correlation`() {
        val result = VersionAudioAligner.crossCorrelate(FloatArray(0), FloatArray(500))
        assertEquals(0, result.lagFrames)
        assertEquals(0.0, result.confidence, 0.0)
    }

    /** The pre-FFT implementation, verbatim, as the reference oracle. */
    private fun bruteForce(
        sourceEnv: FloatArray,
        targetEnv: FloatArray,
        hopMs: Double = 10.0,
        maxLagSec: Double = 40.0,
    ): VersionAudioAligner.Correlation {
        if (sourceEnv.isEmpty() || targetEnv.isEmpty()) {
            return VersionAudioAligner.Correlation(0, 0.0, 0, 0)
        }

        val maxLagFrames = (maxLagSec * 1000.0 / hopMs).toInt()
        val minOverlapFrames = (3.0 * 1000.0 / hopMs).toInt()

        var bestLag = 0
        var bestCorrelation = -1.0

        val minLag = max(-maxLagFrames, -(sourceEnv.size - minOverlapFrames))
        val maxLag = min(maxLagFrames, targetEnv.size - minOverlapFrames)

        for (lag in minLag..maxLag) {
            val srcStart = max(0, -lag)
            val tgtStart = max(0, lag)
            val overlapLen = min(sourceEnv.size - srcStart, targetEnv.size - tgtStart)
            if (overlapLen < minOverlapFrames) continue

            var sumSrc = 0.0
            var sumTgt = 0.0
            for (k in 0 until overlapLen) {
                sumSrc += sourceEnv[srcStart + k]
                sumTgt += targetEnv[tgtStart + k]
            }
            val meanSrc = sumSrc / overlapLen
            val meanTgt = sumTgt / overlapLen

            var num = 0.0
            var denSrc = 0.0
            var denTgt = 0.0
            for (k in 0 until overlapLen) {
                val diffSrc = sourceEnv[srcStart + k] - meanSrc
                val diffTgt = targetEnv[tgtStart + k] - meanTgt
                num += diffSrc * diffTgt
                denSrc += diffSrc * diffSrc
                denTgt += diffTgt * diffTgt
            }

            val denom = sqrt(denSrc * denTgt)
            if (denom > 1e-6) {
                val r = num / denom
                if (r > bestCorrelation) {
                    bestCorrelation = r
                    bestLag = lag
                }
            }
        }

        return VersionAudioAligner.Correlation(bestLag, bestCorrelation, minLag, maxLag)
    }

    private val Double.absoluteValue: Double
        get() = if (this < 0) -this else this
}
