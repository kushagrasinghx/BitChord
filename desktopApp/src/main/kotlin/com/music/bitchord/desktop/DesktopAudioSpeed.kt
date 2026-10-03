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
    /** Where in [pending] the next window's nominal read sits. */
    private var readFrom = 0
    /** Where the last window joined, in [pending] and relative to its nominal read position. */
    private var lastJoin = 0
    private var lastOffset = 0
    private var output = FloatArray(0)
    /** Once a track has been stretched, stay in the windowed path at 1x until it is reset. */
    private var engaged = false

    /** How much of the array [process] returned belongs to this call. */
    var outputCount: Int = 0
        private set

    /** Stretches [count] interleaved samples. */
    fun process(input: FloatArray, count: Int): FloatArray {
        val rate = speed
        if (abs(rate - 1f) < 0.001f && !engaged) {
            // Nothing to do, and nothing to be gained by pretending otherwise: a window pass at 1.0
            // would still smear transients slightly.
            reset()
            outputCount = count
            return input
        }
        engaged = true

        append(input, count)
        val advance = (hop * rate).roundToInt().coerceAtLeast(1)
        // Every window needs its own length, the next hop, and room to slide.
        val needed = 2 * hop + search
        var produced = 0
        var read = readFrom

        while (pendingFrames - read >= needed + search) {
            produced = window(read, produced)
            read += advance
        }

        // Kept: the [search] frames before the next nominal read. Consuming right up to it, as
        // this used to, left the first join of every call unable to slide back onto the true
        // continuation — a forced bad join once a block.
        // Nor past where the source carries on from the last window, which [drain] copies from:
        // sped up, the next read runs ahead of it, and dropping up to the read left [drain]
        // reaching before the start of what was held.
        val drop = minOf(read - search, if (hasTail) lastJoin + hop else read).coerceAtLeast(0)
        consume(drop)
        readFrom = read - drop
        lastJoin -= drop
        outputCount = produced
        return output
    }

    /**
     * Everything still held, as a straight copy of what follows the last window: the audio after
     * it can be read from the decoder directly, with no gap and no repeat. Leaves it reset.
     */
    fun drain(): FloatArray {
        // The last window ended on the source at [lastJoin] + hop, so the source carries on from
        // there — the same audio [tail] holds, and then the rest of what is pending.
        val from = if (hasTail) lastJoin + hop else readFrom
        val rest = (pendingFrames - from).coerceAtLeast(0)
        grow(rest * channels)
        System.arraycopy(pending, from * channels, output, 0, rest * channels)
        reset()
        outputCount = rest * channels
        return output
    }

    /** One hop of output, faded from [tail] into the best join near [read]; returns the new end. */
    private fun window(read: Int, produced: Int): Int {
        val at = if (hasTail) read + bestOffset(read) else read
        lastJoin = at
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
        System.arraycopy(pending, (at + hop) * channels, tail, 0, hop * channels)
        hasTail = true
        lastOffset = at - read
        return produced + hop * channels
    }

    /**
     * The offset within the search window whose shape best matches [tail].
     *
     * Scored by normalised correlation over every channel. The raw dot product it replaced favoured
     * whichever candidate was loudest rather than whichever matched, so even at 1x it kept joining
     * a few milliseconds off the true continuation — a warble that lasted as long as the stretcher
     * stayed engaged. The continuation of the previous join is scored first and only a clearly
     * better match displaces it, which makes 1x an exact copy and keeps steady tones from hopping
     * between equally good periods.
     */
    private fun bestOffset(from: Int): Int {
        var bestOffset = lastOffset.coerceIn(-search, search)
        var best = score(from + bestOffset)
        var offset = -search
        while (offset <= search) {
            if (offset != bestOffset) {
                val candidate = score(from + offset)
                if (candidate > best + JOIN_MARGIN * kotlin.math.abs(best)) {
                    best = candidate
                    bestOffset = offset
                }
            }
            offset++
        }
        return bestOffset
    }

    private fun score(start: Int): Float {
        if (start < 0 || start + hop > pendingFrames) return Float.NEGATIVE_INFINITY
        var dot = 0f
        var energy = 0f
        var frame = 0
        // Every fourth frame: the correlation surface is smooth at audio rates, and the peak does
        // not move for the sampling.
        while (frame < hop) {
            var t = 0f
            var p = 0f
            for (channel in 0 until channels) {
                t += tail[frame * channels + channel]
                p += pending[(start + frame) * channels + channel]
            }
            dot += t * p
            energy += p * p
            frame += 4
        }
        return dot / kotlin.math.sqrt(energy + 1e-9f)
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
        engaged = false
        readFrom = 0
        lastJoin = 0
        lastOffset = 0
        outputCount = 0
    }

    private companion object {
        /** How much better than the running continuation another join has to score to win. */
        const val JOIN_MARGIN = 0.001f
    }
}
