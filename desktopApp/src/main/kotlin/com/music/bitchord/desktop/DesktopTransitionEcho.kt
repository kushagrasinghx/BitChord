package com.music.bitchord.desktop

/** Small allocation-free stereo delay used only during a planned Automix echo-out. */
internal class DesktopTransitionEcho(private val sampleRate: Int, channels: Int) {
    private val channelCount = channels.coerceAtLeast(1)
    private val ring = FloatArray(
        ((sampleRate * MAX_DELAY_SECONDS).toInt() + 1).times(channelCount).coerceAtLeast(channelCount * 2),
    )
    private var cursor = 0
    private var delaySamples = channelCount

    fun configure(seconds: Double) {
        delaySamples = (seconds * sampleRate * channelCount).toInt()
            .coerceIn(channelCount, ring.size - channelCount)
        java.util.Arrays.fill(ring, 0f)
        cursor = 0
    }

    fun process(sample: Float, send: Float, dry: Float): Float {
        val read = Math.floorMod(cursor - delaySamples, ring.size)
        val delayed = ring[read]
        ring[cursor] = sample * send + delayed * FEEDBACK
        cursor = (cursor + 1) % ring.size
        return sample * dry + delayed * WET
    }

    companion object {
        private const val MAX_DELAY_SECONDS = 1.0
        private const val WET = 0.5f
        private const val FEEDBACK = 0.4f
    }
}
