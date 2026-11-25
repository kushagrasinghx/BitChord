package com.music.bitchord.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer

/** Keep the position-driven AutoMix fade on fresh audio samples without changing the sink. */
@UnstableApi
internal class AutoMixAudioClockRenderer(
    renderer: Renderer,
    private val needsClock: () -> Boolean,
) : ForwardingRenderer(renderer) {
    override fun getDurationToProgressUs(positionUs: Long, elapsedRealtimeUs: Long): Long {
        val delayUs = super.getDurationToProgressUs(positionUs, elapsedRealtimeUs)
        // A full audio buffer can let Media3 sleep for up to a second. It only refreshes
        // the player position on wakeup, so the fade clock can exhaust its extrapolation
        // budget before the next sample. Keep the buffer and clock, just wake sooner.
        return if (delayUs > MAX_DELAY_US && state == Renderer.STATE_STARTED && needsClock()) {
            MAX_DELAY_US
        } else delayUs
    }

    private companion object {
        // Match the crossfade controller's 30 ms FADING cadence; never slow the renderer down.
        const val MAX_DELAY_US = 30_000L
    }
}
