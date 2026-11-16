package com.music.bitchord.desktop

import kotlin.math.PI
import kotlin.math.sin

/**
 * A flanger used only during a smart Automix transition. A short delay — half to a few
 * milliseconds — is swept up and down by a slow oscillator and fed back on itself; the modulated
 * copy is returned wet for the engine to mix under the outgoing track, so its tail shimmers and
 * widens as it hands over to the incoming one. The two channels sweep a quarter-cycle apart, so
 * the pair opens up rather than pumping as one. The single ring is interleaved by channel, exactly
 * as [DesktopTransitionEcho]'s is.
 */
internal class DesktopTransitionFlanger(private val sampleRate: Int, channels: Int) :
    DesktopTransitionTexture {
    private val channelCount = channels.coerceAtLeast(1)
    private val ring = FloatArray(((MAX_DELAY_SECONDS * sampleRate).toInt() + 1) * channelCount)
    private var cursor = 0
    private var phase = 0.0

    override fun reset() {
        java.util.Arrays.fill(ring, 0f)
        cursor = 0
        phase = 0.0
    }

    /** The wet, flanged copy of [sample] — the modulated delay, for a post-fader send. */
    override fun process(channel: Int, sample: Float): Float {
        val ch = cursor % channelCount
        val sweep = 0.5 * (1.0 + sin(phase + ch * (PI / 2.0)))
        val seconds = MIN_DELAY_SECONDS + sweep * (MAX_DELAY_SECONDS - MIN_DELAY_SECONDS)
        val delaySamples = (seconds * sampleRate * channelCount).toInt()
            .coerceIn(channelCount, ring.size - channelCount)
        val read = Math.floorMod(cursor - delaySamples, ring.size)
        val delayed = ring[read]
        ring[cursor] = sample + delayed * FEEDBACK
        cursor = (cursor + 1) % ring.size
        phase += 2.0 * PI * LFO_HZ / sampleRate
        if (phase > 2.0 * PI) phase -= 2.0 * PI
        return delayed * WET
    }

    companion object {
        private const val MIN_DELAY_SECONDS = 0.0005
        private const val MAX_DELAY_SECONDS = 0.005
        private const val LFO_HZ = 0.4
        private const val FEEDBACK = 0.6f
        private const val WET = 0.7f
    }
}
