package com.music.bitchord.desktop

import kotlin.math.abs
import kotlin.math.roundToInt

/** The stereo widener, ported from Android's `SpatialAudioProcessor`. */
internal class DesktopSpatialAudio(private val channels: Int, sampleRate: Int) {

    @Volatile
    var enabled: Boolean = false

    /** Only stereo has a mid and a side to widen. */
    val applies: Boolean = channels == 2

    private val widthGain = 2.5f
    private val outputGain = 0.82f
    private val crossfeedGain = 0.2f
    private val lowpassCoeff = 0.3f

    private val delayFrames = (sampleRate * DELAY_MS / 1000f).roundToInt().coerceAtLeast(1)
    private var delayLeft = FloatArray(delayFrames)
    private var delayRight = FloatArray(delayFrames)
    private var delayIndex = 0
    private var lowpassLeft = 0f
    private var lowpassRight = 0f

    /** Widens [count] interleaved samples where they lie. */
    fun process(samples: FloatArray, count: Int) {
        if (!enabled || !applies) return
        var index = 0
        while (index + 1 < count) {
            val left = samples[index]
            val right = samples[index + 1]

            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f * widthGain
            var widenedLeft = mid + side
            var widenedRight = mid - side

            lowpassLeft += lowpassCoeff * (delayRight[delayIndex] - lowpassLeft)
            lowpassRight += lowpassCoeff * (delayLeft[delayIndex] - lowpassRight)
            widenedLeft += lowpassLeft * crossfeedGain
            widenedRight += lowpassRight * crossfeedGain

            delayLeft[delayIndex] = left
            delayRight[delayIndex] = right
            delayIndex = (delayIndex + 1) % delayFrames

            samples[index] = widenedLeft * outputGain
            samples[index + 1] = widenedRight * outputGain
            index += 2
        }
    }

    fun reset() {
        delayLeft.fill(0f)
        delayRight.fill(0f)
        delayIndex = 0
        lowpassLeft = 0f
        lowpassRight = 0f
    }

    private companion object {
        const val DELAY_MS = 15
    }
}

/** Skips long silences, after Media3's `SilenceSkippingAudioProcessor`. */
internal class DesktopSilenceSkipper(private val channels: Int, sampleRate: Int) {

    @Volatile
    var enabled: Boolean = false

    private val minSilenceFrames = (sampleRate * MIN_SILENCE_US / 1_000_000L).toInt()
    private val keptFrames = (sampleRate * KEPT_EACH_END_US / 1_000_000L).toInt().coerceAtLeast(1)

    /** Silence seen so far in a run that may yet turn out to be short. */
    private var pending = FloatArray(minSilenceFrames * channels)
    private var pendingFrames = 0

    /** The tail of a run being skipped, kept so its end can be heard. */
    private var tail = FloatArray(keptFrames * channels)
    private var tailFrames = 0
    private var tailWrite = 0

    private var skipping = false
    private var output = FloatArray(0)

    /** How much of the array [process] returned belongs to this call. */
    var outputCount: Int = 0
        private set

    /** How much has been dropped, in frames — the position clock needs it. */
    var skippedFrames: Long = 0L
        private set

    fun process(samples: FloatArray, count: Int): FloatArray {
        if (!enabled) {
            outputCount = count
            return samples
        }
        grow(count + (pendingFrames + keptFrames) * channels)
        var produced = 0
        var index = 0

        while (index + channels <= count) {
            if (isSilent(samples, index)) {
                if (skipping) {
                    // Only the last [keptFrames] of a skipped run matter.
                    val at = tailWrite * channels
                    System.arraycopy(samples, index, tail, at, channels)
                    tailWrite = (tailWrite + 1) % keptFrames
                    tailFrames = minOf(tailFrames + 1, keptFrames)
                    skippedFrames++
                } else {
                    System.arraycopy(samples, index, pending, pendingFrames * channels, channels)
                    pendingFrames++
                    if (pendingFrames >= minSilenceFrames) {
                        // Long enough to be a gap.
                        produced = emit(pending, keptFrames * channels, produced, attenuate = true)
                        skippedFrames += (pendingFrames - keptFrames).toLong()
                        pendingFrames = 0
                        tailFrames = 0
                        tailWrite = 0
                        skipping = true
                    }
                }
            } else {
                produced = when {
                    skipping -> emitTail(produced)
                    // A short run of quiet is part of the music; it goes out exactly as it came in.
                    pendingFrames > 0 -> emit(pending, pendingFrames * channels, produced, attenuate = false)
                    else -> produced
                }
                pendingFrames = 0
                skipping = false
                grow(produced + channels)
                System.arraycopy(samples, index, output, produced, channels)
                produced += channels
            }
            index += channels
        }

        outputCount = produced
        return output
    }

    private fun emitTail(produced: Int): Int {
        if (tailFrames == 0) return produced
        grow(produced + tailFrames * channels)
        var at = produced
        // The ring holds the most recent frames; oldest first on the way out.
        val start = if (tailFrames < keptFrames) 0 else tailWrite
        repeat(tailFrames) { step ->
            val frame = (start + step) % keptFrames
            System.arraycopy(tail, frame * channels, output, at, channels)
            at += channels
        }
        for (index in produced until at) output[index] *= KEPT_VOLUME
        tailFrames = 0
        tailWrite = 0
        return at
    }

    private fun emit(source: FloatArray, length: Int, produced: Int, attenuate: Boolean): Int {
        grow(produced + length)
        System.arraycopy(source, 0, output, produced, length)
        if (attenuate) for (index in produced until produced + length) output[index] *= KEPT_VOLUME
        return produced + length
    }

    private fun isSilent(samples: FloatArray, at: Int): Boolean {
        for (channel in 0 until channels) {
            if (abs(samples[at + channel]) > THRESHOLD) return false
        }
        return true
    }

    private fun grow(required: Int) {
        if (output.size < required) output = output.copyOf(maxOf(required, output.size * 2))
    }

    fun reset() {
        pendingFrames = 0
        tailFrames = 0
        tailWrite = 0
        skipping = false
        skippedFrames = 0
    }

    private companion object {
        /** Android's own minimum, an order of magnitude above Media3's default. */
        const val MIN_SILENCE_US = 1_000_000L

        /** Media3's retention ratio against that minimum, split across both ends. */
        const val KEPT_EACH_END_US = 100_000L

        /** `DEFAULT_SILENCE_THRESHOLD_LEVEL` of 1024, on a 16-bit scale. */
        const val THRESHOLD = 1024f / 32_768f

        /** `DEFAULT_MIN_VOLUME_TO_KEEP_PERCENTAGE` of 10. */
        const val KEPT_VOLUME = 0.1f
    }
}
