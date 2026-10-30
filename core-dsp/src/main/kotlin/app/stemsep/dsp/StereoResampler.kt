package app.stemsep.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Streaming stereo windowed-sinc resampler.
 *
 * The kernel is a Blackman-windowed sinc with 32 taps by default. Samples
 * outside the input are treated as zero at the start and end of the stream.
 * Standard audio rates have a small rational phase count, so the filter
 * weights are precomputed and the per-frame path contains no trigonometry.
 */
class StereoResampler(
    val inputRate: Int,
    val outputRate: Int,
    taps: Int = 32,
) {
    init {
        require(inputRate > 0) { "inputRate must be positive" }
        require(outputRate > 0) { "outputRate must be positive" }
        require(taps >= 8 && taps % 2 == 0) { "taps must be an even number >= 8" }
    }

    private val half = taps / 2
    private val cutoff = minOf(1.0, outputRate.toDouble() / inputRate.toDouble()) * 0.97
    private val passthrough = inputRate == outputRate
    private val rateGcd = gcd(inputRate, outputRate)
    private val phaseCount = outputRate / rateGcd
    private val phaseWeights: Array<DoubleArray>? =
        if (!passthrough && phaseCount <= 8192) {
            Array(phaseCount) { phase ->
                normalizedWeights(phase * rateGcd.toDouble() / outputRate)
            }
        } else {
            null
        }
    private val outputChunk = FloatArray(4096 * 2)

    private var buffer = FloatArray(4096 * 2)
    private var bufferStart = 0L
    private var bufferFrames = 0
    private var nextOutputFrame = 0L
    private var totalInputFrames = 0L
    private var expectedOutputFrames = 0L
    private var outputChunkFrames = 0
    private var finished = false

    fun process(input: FloatArray, inputFrames: Int, sink: (FloatArray, Int) -> Unit) {
        check(!finished) { "resampler already finished" }
        require(inputFrames >= 0 && input.size >= inputFrames * 2) { "invalid input" }
        if (passthrough) {
            totalInputFrames += inputFrames
            if (inputFrames > 0) sink(input, inputFrames)
            return
        }
        append(input, inputFrames)
        emit(sink, allowPastEnd = false)
    }

    fun finish(sink: (FloatArray, Int) -> Unit) {
        if (finished) return
        finished = true
        if (passthrough) {
            flushChunk(sink)
            return
        }
        expectedOutputFrames = totalInputFrames * outputRate / inputRate
        emit(sink, allowPastEnd = true)
        flushChunk(sink)
    }

    private fun append(input: FloatArray, inputFrames: Int) {
        if (inputFrames == 0) return
        ensureCapacity(bufferFrames + inputFrames)
        input.copyInto(
            destination = buffer,
            destinationOffset = bufferFrames * 2,
            startIndex = 0,
            endIndex = inputFrames * 2,
        )
        bufferFrames += inputFrames
        totalInputFrames += inputFrames
    }

    private fun ensureCapacity(frames: Int) {
        if (buffer.size >= frames * 2) return
        var size = maxOf(4096, buffer.size)
        while (size < frames * 2) size *= 2
        buffer = buffer.copyOf(size)
    }

    private fun emit(sink: (FloatArray, Int) -> Unit, allowPastEnd: Boolean) {
        while (true) {
            if (finished && nextOutputFrame >= expectedOutputFrames) break
            val numerator = nextOutputFrame * inputRate
            val center = numerator / outputRate
            val remainder = (numerator % outputRate).toInt()
            if (!allowPastEnd && center + half >= bufferStart + bufferFrames) break

            emitFrame(center, remainder)
            nextOutputFrame++
            if (outputChunkFrames == outputChunk.size / 2) flushChunk(sink)
            trim()
        }
    }

    private fun emitFrame(center: Long, remainder: Int) {
        val activeWeights = phaseWeights?.get(remainder / rateGcd)
            ?: normalizedWeights(remainder.toDouble() / outputRate)

        var left = 0.0
        var right = 0.0
        for (tap in activeWeights.indices) {
            val index = center + (tap - half + 1)
            val local = index - bufferStart
            if (local >= 0 && local < bufferFrames) {
                val weight = activeWeights[tap]
                left += buffer[local.toInt() * 2] * weight
                right += buffer[local.toInt() * 2 + 1] * weight
            }
        }
        outputChunk[outputChunkFrames * 2] = left.toFloat()
        outputChunk[outputChunkFrames * 2 + 1] = right.toFloat()
        outputChunkFrames++
    }

    private fun normalizedWeights(fraction: Double): DoubleArray {
        val result = DoubleArray(half * 2)
        var weightSum = 0.0
        for (tap in result.indices) {
            val offset = tap - half + 1
            val distance = offset - fraction
            val argument = cutoff * distance
            val sinc = if (abs(argument) < 1e-9) {
                1.0
            } else {
                sin(PI * argument) / (PI * argument)
            }
            val window = 0.42 +
                0.5 * cos(PI * distance / half) +
                0.08 * cos(2.0 * PI * distance / half)
            val weight = cutoff * sinc * window
            result[tap] = weight
            weightSum += weight
        }
        if (abs(weightSum) < 1e-12) return result
        for (tap in result.indices) result[tap] /= weightSum
        return result
    }

    private fun trim() {
        if (bufferFrames < 8192) return
        val center = nextOutputFrame * inputRate / outputRate
        val keepFrom = center - half + 1
        val drop = (keepFrom - bufferStart).coerceAtMost(bufferFrames.toLong()).toInt()
        if (drop < 4096) return
        System.arraycopy(buffer, drop * 2, buffer, 0, (bufferFrames - drop) * 2)
        bufferStart += drop
        bufferFrames -= drop
    }

    private fun flushChunk(sink: (FloatArray, Int) -> Unit) {
        if (outputChunkFrames > 0) {
            sink(outputChunk, outputChunkFrames)
            outputChunkFrames = 0
        }
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val remainder = x % y
            x = y
            y = remainder
        }
        return x
    }
}
