package com.music.bitchord.desktop

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The texture sends an Automix transition can be given, and the identity [chooseTexture] resolves a
 * plan to. [NONE] and the [FLANGER] fallthrough exist so the engine can decline to texture a
 * transition or default to the flanger when nothing more specific fits.
 */
internal enum class TransitionTexture { NONE, FLANGER, CRUSH, PHASER, VIBRATO, STEREO }

/**
 * A send effect the outgoing deck can be handed for a smart Automix transition, so its tail is
 * textured on the way out rather than simply faded. Every one is a *send*: [process] returns the
 * wet copy the engine mixes under the dry outgoing, and the engine ramps how much of it is heard.
 * The engine keeps exactly one of these live per transition and picks which kind the plan calls
 * for, so they never stack on each other.
 *
 * [process] is fed the interleaved stream one sample at a time, [channel] telling which of the two
 * channels that sample belongs to; the effects that treat the pair differently (stereo width) use
 * it, the others keep their own interleaved memory and mostly ignore it.
 */
internal interface DesktopTransitionTexture {
    fun reset()
    fun process(channel: Int, sample: Float): Float
}

/**
 * Bit depth reduction: the outgoing is quantised to a handful of levels and its sample-and-hold
 * clock dropped, so the tail crunches and fizzles as it leaves — a deliberate, obvious digital
 * decay rather than a clean fade. Reads best on fast, percussive material with little vocal in it,
 * where the grit lands as rhythm rather than as a smeared formant.
 */
internal class DesktopTransitionCrush(private val sampleRate: Int, channels: Int) :
    DesktopTransitionTexture {
    private val channelCount = channels.coerceAtLeast(1)
    private val held = FloatArray(channelCount)
    private var holdCounter = 0
    private var heldFor = 0

    override fun reset() {
        java.util.Arrays.fill(held, 0f)
        holdCounter = 0
        heldFor = 0
    }

    override fun process(channel: Int, sample: Float): Float {
        val ch = ((channel % channelCount) + channelCount) % channelCount
        // Drop the sample rate in coarse steps so the held value smears into a lo-fi staircase.
        if (heldFor >= HOLD_SAMPLES) {
            held[ch] = sample
            heldFor = 0
        } else {
            heldFor++
        }
        val half = LEVELS / 2f
        val stepped = (Math.round(held[ch] * half) / half).coerceIn(-1f, 1f)
        holdCounter++
        return stepped * WET
    }

    companion object {
        private const val LEVELS = 8f
        private const val HOLD_SAMPLES = 6
        private const val WET = 0.7f
    }
}

/**
 * A single swept all-pass, so the outgoing tail gains a moving comb of peaks and dips — the classic
 * phase-shift whoosh. Where a flanger doubles with a modulated delay, this rides the signal's own
 * phase, which separates two tracks whose tempi are a little apart without needing either to repeat.
 */
internal class DesktopTransitionPhaser(private val sampleRate: Int, channels: Int) :
    DesktopTransitionTexture {
    private val channelCount = channels.coerceAtLeast(1)
    private val xMem = FloatArray(channelCount)
    private val yMem = FloatArray(channelCount)
    private var phase = 0.0

    override fun reset() {
        java.util.Arrays.fill(xMem, 0f)
        java.util.Arrays.fill(yMem, 0f)
        phase = 0.0
    }

    override fun process(channel: Int, sample: Float): Float {
        val ch = ((channel % channelCount) + channelCount) % channelCount
        // A coefficient close to 1 keeps the all-pass; sweeping it moves the notches through the band.
        val a = 0.5 + 0.5 * sin(phase + ch * (PI / channelCount))
        val clamped = a.coerceIn(0.02, 0.98)
        val y = -clamped * sample + xMem[ch] + clamped * yMem[ch]
        xMem[ch] = sample
        yMem[ch] = y.toFloat()
        phase += 2.0 * PI * LFO_HZ / sampleRate
        if (phase > 2.0 * PI) phase -= 2.0 * PI
        // Dry-minus-all-pass is the swept comb; scaled down so it sits under the outgoing as a send.
        return ((sample - y) * 0.5 * WET).toFloat()
    }

    companion object {
        private const val LFO_HZ = 0.6
        private const val WET = 0.8f
    }
}

/**
 * A short modulated delay read as a pitch wobble: the tail bends up and down a few cents as it
 * leaves. Suited to slow, sustained, vocal-forward material, where a gentle pitch sway reads as
 * expression rather than as an artefact.
 */
internal class DesktopTransitionVibrato(private val sampleRate: Int, channels: Int) :
    DesktopTransitionTexture {
    private val channelCount = channels.coerceAtLeast(1)
    private val ring = FloatArray(((BASE_SECONDS + DEPTH_SECONDS) * sampleRate).toInt() * channelCount + channelCount)
    private var cursor = 0
    private var phase = 0.0

    override fun reset() {
        java.util.Arrays.fill(ring, 0f)
        cursor = 0
        phase = 0.0
    }

    override fun process(channel: Int, sample: Float): Float {
        val ch = ((channel % channelCount) + channelCount) % channelCount
        ring[cursor] = sample
        val wobble = sin(phase + ch * (PI / 2.0))
        val seconds = BASE_SECONDS + wobble * DEPTH_SECONDS
        val delaySamples = (seconds * sampleRate * channelCount).toInt()
            .coerceIn(channelCount, ring.size - channelCount)
        val read = Math.floorMod(cursor - delaySamples, ring.size)
        cursor = (cursor + 1) % ring.size
        phase += 2.0 * PI * LFO_HZ / sampleRate
        if (phase > 2.0 * PI) phase -= 2.0 * PI
        return ring[read] * WET
    }

    companion object {
        private const val BASE_SECONDS = 0.003
        private const val DEPTH_SECONDS = 0.0015
        private const val LFO_HZ = 5.5
        private const val WET = 0.7f
    }
}

/**
 * Spatial width plus a slow auto-pan: the two channels are decorrelated by a short delay and swung
 * against each other, so overlapping vocals spread across the image instead of stacking in the
 * centre. Chosen when the plan reports both tracks singing over each other.
 */
internal class DesktopTransitionStereo(private val sampleRate: Int, channels: Int) :
    DesktopTransitionTexture {
    private val channelCount = channels.coerceAtLeast(1)
    private val ring = FloatArray((HAAS_SECONDS * sampleRate).toInt() * channelCount + channelCount)
    private var cursor = 0
    private var pan = 0.0

    override fun reset() {
        java.util.Arrays.fill(ring, 0f)
        cursor = 0
        pan = 0.0
    }

    override fun process(channel: Int, sample: Float): Float {
        val ch = ((channel % channelCount) + channelCount) % channelCount
        ring[cursor] = sample
        val delaySamples = (HAAS_SECONDS * sampleRate * channelCount).toInt()
            .coerceIn(channelCount, ring.size - channelCount)
        val read = Math.floorMod(cursor - delaySamples, ring.size)
        val haas = ring[read]
        cursor = (cursor + 1) % ring.size
        val swing = sin(pan)
        pan += 2.0 * PI * PAN_HZ / sampleRate
        if (pan > 2.0 * PI) pan -= 2.0 * PI
        // Right channel gets the delayed, opposite-swung copy so the pair separates and moves.
        val offset = if (ch % 2 == 0) -swing else swing
        return ((sample * 0.35f + haas * 0.35f) * (1f + 0.5f * abs(offset).toFloat())).toFloat() * WET
    }

    companion object {
        private const val HAAS_SECONDS = 0.012
        private const val PAN_HZ = 0.35
        private const val WET = 0.6f
    }
}
