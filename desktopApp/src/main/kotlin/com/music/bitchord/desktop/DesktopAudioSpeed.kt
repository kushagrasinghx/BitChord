package com.music.bitchord.desktop

import kotlin.math.abs
import kotlin.math.roundToInt

/** Playback speed without a change of pitch. */
internal class DesktopAudioSpeed(
    private val channels: Int,
    sampleRate: Int,
) {

    /** 1.0 is untouched, and is short-circuited entirely. */
    @Volatile
    var speed: Float = 1f

    /** Half a window. */
    private val hop = (sampleRate * 0.028f).roundToInt().coerceAtLeast(64)

    /** How far either side the reader may slide to find a better join. */
    private val search = (sampleRate * 0.007f).roundToInt().coerceAtLeast(16)

    private var pending = FloatArray(0)
    private var pendingFrames = 0
    private var tail = FloatArray(hop * channels)
    private var hasTail = false
    private var output = FloatArray(0)

    /** How much of the array [process] returned belongs to this call. */
    var outputCount: Int = 0
        private set

    /** Stretches [count] interleaved samples. */
    fun process(input: FloatArray, count: Int): FloatArray {
        val rate = speed
        if (abs(rate - 1f) < 0.001f) {
            // Nothing to do, and nothing to be gained by pretending otherwise: a window pass at 1.0
            // would still smear transients slightly.
            reset()
            outputCount = count
            return input
        }

        append(input, count)
        val advance = (hop * rate).roundToInt().coerceAtLeast(1)
        // Every window needs its own length, the next hop, and room to slide.
        val needed = 2 * hop + search
        var produced = 0
        var read = 0

        while (pendingFrames - read >= needed + search) {
            val at = if (hasTail) read + bestOffset(read) else read
            grow(produced + hop * channels)
            for (frame in 0 until hop) {
                val weight = frame.toFloat() / hop
                for (channel in 0 until channels) {
                    val incoming = pending[(at + frame) * channels + channel]
                    val outgoing = if (hasTail) tail[frame * channels + channel] else incoming
                    output[produced + frame * channels + channel] =
                        outgoing * (1f - weight) + incoming * weight
                }
            }
            produced += hop * channels
            System.arraycopy(pending, (at + hop) * channels, tail, 0, hop * channels)
            hasTail = true
            read += advance
        }

        consume(read)
        outputCount = produced
        return output
    }

    /** The offset within the search window whose shape best matches [tail]. */
    private fun bestOffset(from: Int): Int {
        var bestOffset = 0
        var best = Float.NEGATIVE_INFINITY
        var offset = -search
        while (offset <= search) {
            val start = from + offset
            if (start >= 0 && start + hop <= pendingFrames) {
                var sum = 0f
                var frame = 0
                // Every fourth frame: the correlation surface is smooth at audio rates, and the
                // peak does not move for the sampling.
                while (frame < hop) {
                    sum += tail[frame * channels] * pending[(start + frame) * channels]
                    frame += 4
                }
                if (sum > best) {
                    best = sum
                    bestOffset = offset
                }
            }
            offset++
        }
        return bestOffset
    }

    private fun append(input: FloatArray, count: Int) {
        val frames = count / channels
        val required = (pendingFrames + frames) * channels
        if (pending.size < required) pending = pending.copyOf(maxOf(required, pending.size * 2))
        System.arraycopy(input, 0, pending, pendingFrames * channels, frames * channels)
        pendingFrames += frames
    }

    private fun consume(frames: Int) {
        if (frames <= 0) return
        val remaining = pendingFrames - frames
        System.arraycopy(pending, frames * channels, pending, 0, remaining * channels)
        pendingFrames = remaining
    }

    private fun grow(required: Int) {
        if (output.size < required) output = output.copyOf(maxOf(required, output.size * 2))
    }

    /** Forgets the window in flight, for a seek or a change of track. */
    fun reset() {
        pendingFrames = 0
        hasTail = false
        outputCount = 0
    }
}
