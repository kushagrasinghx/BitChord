package com.music.bitchord.desktop

/**
 * A compact Schroeder reverb — four damped feedback comb filters summed into two all-pass
 * diffusers — used only to give the outgoing track a smooth tail as an Automix transition carries
 * it away. It is dual-mono: each channel keeps its own state, which is plenty for a decaying send
 * and costs half the memory of a true stereo network.
 *
 * Allocation-free after construction. [process] returns the *wet* signal only; the engine adds it
 * as a send and ramps its level, so the tail keeps ringing from the combs' own feedback after the
 * dry outgoing audio has faded to nothing.
 */
internal class DesktopTransitionReverb(sampleRate: Int, channels: Int) {
    private val channelCount = channels.coerceAtLeast(1)
    private val scale = sampleRate.toDouble() / REFERENCE_RATE

    private val combLen = IntArray(COMB_TUNINGS.size) { tuning(COMB_TUNINGS[it]) }
    private val combStart = offsets(combLen)
    private val combBuf = Array(channelCount) { FloatArray(combStart.last() + combLen.last()) }
    private val combPos = IntArray(channelCount * COMB_TUNINGS.size)
    private val combStore = FloatArray(channelCount * COMB_TUNINGS.size)

    private val allpassLen = IntArray(ALLPASS_TUNINGS.size) { tuning(ALLPASS_TUNINGS[it]) }
    private val allpassStart = offsets(allpassLen)
    private val allpassBuf = Array(channelCount) { FloatArray(allpassStart.last() + allpassLen.last()) }
    private val allpassPos = IntArray(channelCount * ALLPASS_TUNINGS.size)

    private fun tuning(base: Int): Int = (base * scale).toInt().coerceAtLeast(1)

    private fun offsets(lengths: IntArray): IntArray {
        val out = IntArray(lengths.size)
        var acc = 0
        for (i in lengths.indices) {
            out[i] = acc
            acc += lengths[i]
        }
        return out
    }

    fun reset() {
        for (b in combBuf) b.fill(0f)
        for (b in allpassBuf) b.fill(0f)
        combPos.fill(0)
        allpassPos.fill(0)
        combStore.fill(0f)
    }

    /** One sample of one channel; returns its wet contribution. */
    fun process(channel: Int, input: Float): Float {
        val ch = channel.coerceIn(0, channelCount - 1)
        var out = 0f
        for (c in COMB_TUNINGS.indices) {
            val state = ch * COMB_TUNINGS.size + c
            val idx = combPos[state]
            val pos = combStart[c] + idx
            val buffered = combBuf[ch][pos]
            // One-pole low-pass in the feedback loop: damps the tail so it darkens as it dies.
            val filtered = buffered * (1f - DAMP) + combStore[state] * DAMP
            combStore[state] = filtered
            combBuf[ch][pos] = input + filtered * FEEDBACK
            out += buffered
            combPos[state] = (idx + 1) % combLen[c]
        }
        out *= COMB_SCALE
        for (a in ALLPASS_TUNINGS.indices) {
            val state = ch * ALLPASS_TUNINGS.size + a
            val idx = allpassPos[state]
            val pos = allpassStart[a] + idx
            val buffered = allpassBuf[ch][pos]
            allpassBuf[ch][pos] = out + buffered * ALLPASS_FEEDBACK
            out = buffered - out
            allpassPos[state] = (idx + 1) % allpassLen[a]
        }
        return out * WET
    }

    companion object {
        private const val REFERENCE_RATE = 44_100.0
        // Freeverb's comb and all-pass tunings, in samples at 44.1 kHz, rescaled to the sink rate.
        private val COMB_TUNINGS = intArrayOf(1_557, 1_617, 1_491, 1_422)
        private val ALLPASS_TUNINGS = intArrayOf(556, 441)
        private const val FEEDBACK = 0.80f
        private const val DAMP = 0.35f
        private const val ALLPASS_FEEDBACK = 0.5f
        private const val COMB_SCALE = 0.18f
        private const val WET = 0.32f
    }
}
