package com.music.bitchord.desktop

import kotlin.math.pow

/**
 * A beat-synced slice repeater — the "beat roll" — used only during a planned Automix transition.
 *
 * Before [latch] it records the outgoing stream into a ring exactly one slice long (a 1/4 or 1/8 of
 * a beat, sized by the caller). [latch] freezes that ring and replays it, each pass quieter than the
 * last, so the outgoing track rolls into the handoff instead of simply fading. The per-sample
 * contract mirrors [DesktopTransitionEcho]: [wet] carries the frozen repeats, [dry] the live
 * signal, and the engine ramps one down as it ramps the other up.
 */
internal class DesktopTransitionBeatRepeat(private val sampleRate: Int, channels: Int) {
    private val channelCount = channels.coerceAtLeast(1)
    private var sliceSamples = channelCount
    private var ring = FloatArray(0)
    private var writeCursor = 0
    private var readCursor = 0
    private var latched = false
    private var sinceLatch = 0

    /** Sizes the slice. [sliceSeconds] is one repeat interval (a beat, or half of one). */
    fun configure(sliceSeconds: Double) {
        val wanted = (sliceSeconds * sampleRate * channelCount).toInt()
        val largest = (MAX_SLICE_SECONDS * sampleRate * channelCount).toInt()
        sliceSamples = wanted.coerceIn(channelCount, largest)
        ring = FloatArray(sliceSamples)
        writeCursor = 0
        readCursor = 0
        latched = false
        sinceLatch = 0
    }

    /** Freezes the last recorded slice and starts looping it from its oldest sample. */
    fun latch() {
        if (latched || ring.isEmpty()) return
        latched = true
        // The ring is one slice long and was written continuously, so [writeCursor] now points at
        // its oldest sample: replaying from here loops the slice that was just heard.
        readCursor = writeCursor
        sinceLatch = 0
    }

    val engaged: Boolean get() = latched

    fun process(sample: Float, wet: Float, dry: Float): Float {
        if (ring.isEmpty()) return sample * dry
        if (!latched) {
            ring[writeCursor] = sample
            writeCursor = (writeCursor + 1) % sliceSamples
            return sample * dry
        }
        val out = ring[readCursor]
        readCursor = (readCursor + 1) % sliceSamples
        sinceLatch++
        val loops = sinceLatch / sliceSamples
        val decay = if (loops <= 0) 1f else DECAY.pow(loops.toDouble()).toFloat()
        return out * decay * wet + sample * dry
    }

    companion object {
        private const val MAX_SLICE_SECONDS = 1.0
        private const val DECAY = 0.55
    }
}
