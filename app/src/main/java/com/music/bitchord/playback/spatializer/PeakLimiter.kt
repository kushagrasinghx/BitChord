package com.music.bitchord.playback.spatializer

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Stereo-linked look-ahead peak limiter for the spatializer output, tuned to stay out of the way: it only has work to
 * do on the peaks the spatialized mix still pushes past [ceilingDb] after [StereoSpatializer]'s headroom trim. It
 * turns the whole mix down, bass included, for as long as its release lasts, so it is tuned to act as little as
 * possible: measured on loud, dense masters it reduces by more than 0.1 dB about a tenth of the time, by at most
 * ~2 dB, and costs the bass ~0.1 dB there; on most music it never acts.
 *
 * Gain law: required gain per sample (ceiling / peak), its minimum over the look-ahead window, an instant-down /
 * slow-up one-pole ([releaseMs]), then a moving average over the look-ahead so the gain has reached its target by
 * the time the peak leaves the delay line — no clicks, no overshoot. Latency: [latencyFrames].
 */
class PeakLimiter(
    sampleRate: Int,
    ceilingDb: Float = -0.3f,
    lookaheadMs: Float = DEFAULT_LOOKAHEAD_MS,
    releaseMs: Float = 150f,
) {
    val latencyFrames: Int = latencyFramesFor(sampleRate, lookaheadMs)
    private val w = latencyFrames + 1
    private val ceiling = 10.0.pow(ceilingDb / 20.0).toFloat()
    private val release = exp(-1.0 / (sampleRate * releaseMs / 1000.0)).toFloat()

    private val delay = FloatArray(2 * w)
    private var delayPos = 0
    private val box = FloatArray(w)
    private var boxPos = 0
    private var boxSum = 0.0
    private var sinceResync = 0
    private val dqValue = FloatArray(w + 1)
    private val dqTime = LongArray(w + 1)
    private var dqHead = 0
    private var dqSize = 0
    private var time = 0L
    private var smoothed = 1f

    init { reset() }

    fun reset() {
        delay.fill(0f); delayPos = 0
        box.fill(1f); boxPos = 0; boxSum = w.toDouble(); sinceResync = 0
        dqHead = 0; dqSize = 0; time = 0L
        smoothed = 1f
    }

    /** Interleaved stereo, in place. */
    fun process(buf: FloatArray, frames: Int) {
        val cap = w + 1
        for (i in 0 until frames) {
            val l = buf[2 * i]; val r = buf[2 * i + 1]
            val peak = max(abs(l), abs(r))
            val req = if (peak > ceiling) ceiling / peak else 1f
            // sliding minimum of req over the last w samples (monotonic deque)
            while (dqSize > 0 && dqValue[(dqHead + dqSize - 1) % cap] >= req) dqSize--
            dqValue[(dqHead + dqSize) % cap] = req; dqTime[(dqHead + dqSize) % cap] = time; dqSize++
            while (dqTime[dqHead] <= time - w) { dqHead = (dqHead + 1) % cap; dqSize-- }
            val held = dqValue[dqHead]
            smoothed = if (held < smoothed) held else held + (smoothed - held) * release
            boxSum += smoothed - box[boxPos]
            box[boxPos] = smoothed
            boxPos = if (boxPos + 1 == w) 0 else boxPos + 1
            if (++sinceResync >= RESYNC) { boxSum = 0.0; for (v in box) boxSum += v; sinceResync = 0 }
            val gain = (boxSum / w).toFloat()
            // delay line of w - 1 samples
            val dl = delay[2 * delayPos]; val dr = delay[2 * delayPos + 1]
            delay[2 * delayPos] = l; delay[2 * delayPos + 1] = r
            delayPos = if (delayPos + 1 == latencyFrames.coerceAtLeast(1)) 0 else delayPos + 1
            val outL = if (latencyFrames == 0) l else dl
            val outR = if (latencyFrames == 0) r else dr
            buf[2 * i] = (outL * gain).coerceIn(-ceiling, ceiling)
            buf[2 * i + 1] = (outR * gain).coerceIn(-ceiling, ceiling)
            time++
        }
    }

    companion object {
        private const val RESYNC = 1 shl 16
        const val DEFAULT_LOOKAHEAD_MS = 5f

        /** The look-ahead delay in frames: the window is [lookaheadMs] long, the delay one frame shorter. */
        fun latencyFramesFor(sampleRate: Int, lookaheadMs: Float = DEFAULT_LOOKAHEAD_MS): Int =
            max(1, (sampleRate * lookaheadMs / 1000f).roundToInt()) - 1
    }
}
