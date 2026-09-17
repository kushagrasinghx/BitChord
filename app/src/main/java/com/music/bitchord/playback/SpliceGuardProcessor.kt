package com.music.bitchord.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.music.bitchord.data.settings.AppSettings
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos

/**
 * Micro-fade guard at every splice point. Beat-snapped cue starts and
 * INSTANT volume cuts land mid-waveform on non-zero samples — a full-scale
 * step the speaker renders as a click. This processor sits in each player's
 * chain and softens exactly those edges with 8–10 ms equal-power ramps:
 *
 * - Every [onFlush] (cue load, seek, fresh source) arms a 10 ms fade-in, so
 *   a spare player opening mid-waveform never starts with a step.
 * - [triggerCut] (driven by the controller at the INSTANT flip tick) runs an
 *   8 ms fade-out glued to an 8 ms fade-in on both decks.
 *
 * Steady-state audio passes through untouched: once the counters drain the
 * per-sample path is a single int comparison. 16-bit only, like the echo and
 * reverb sends — other encodings bow out with [AudioProcessor.AudioFormat.NOT_SET].
 */
@UnstableApi
class SpliceGuardProcessor : BaseAudioProcessor() {

    @Volatile private var fadeInRemaining = 0
    @Volatile private var cutOutRemaining = 0
    @Volatile private var cutInRemaining = 0

    private var fadeInFrames = 0
    private var cutOutFrames = 0
    private var cutInFrames = 0
    // Sample-denominated totals: queueInput advances one counter per sample,
    // and a stereo frame is two samples. Counted separately so the ramp
    // lengths stay exactly 10/8/8 ms regardless of channel count.
    private var fadeInSamples = 0
    private var cutOutSamples = 0
    private var cutInSamples = 0

    /** Fires the out+in micro-cut. Safe to call when idle: it just runs. */
    fun triggerCut() {
        cutOutRemaining = cutOutSamples
        cutInRemaining = 0
        fadeInRemaining = 0
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        val sr = inputAudioFormat.sampleRate
        fadeInFrames = ((sr * FADE_IN_MS) / 1000).toInt().coerceAtLeast(1)
        cutOutFrames = ((sr * CUT_OUT_MS) / 1000).toInt().coerceAtLeast(1)
        cutInFrames = ((sr * CUT_IN_MS) / 1000).toInt().coerceAtLeast(1)
        fadeInSamples = fadeInFrames * inputAudioFormat.channelCount
        cutOutSamples = cutOutFrames * inputAudioFormat.channelCount
        cutInSamples = cutInFrames * inputAudioFormat.channelCount
        return inputAudioFormat
    }

    override fun onFlush() {
        // A flush means a seek or a fresh source: the next buffer opens
        // mid-waveform, so arm the fade-in before it arrives.
        // DJ-only: stock upstream opens every source dry, so normal Automix
        // never arms the fade-in (every track start/seek would soften).
        if (!AppSettings.mixsetModeEnabled.value) {
            fadeInRemaining = 0
            cutOutRemaining = 0
            cutInRemaining = 0
            return
        }
        fadeInRemaining = fadeInSamples
        cutOutRemaining = 0
        cutInRemaining = 0
    }

    override fun onReset() {
        fadeInRemaining = 0
        cutOutRemaining = 0
        cutInRemaining = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        var position = inputBuffer.position()
        val limit = inputBuffer.limit()
        if (position >= limit) return
        val out = replaceOutputBuffer(limit - position)
        while (position < limit - 1) {
            val sample = inputBuffer.getShort(position).toInt()
            val gain = gainForNextSample()
            val guarded = if (gain >= 1f) {
                sample
            } else {
                (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            }
            out.putShort(guarded.toShort())
            position += 2
        }
        inputBuffer.position(limit)
        out.flip()
    }

    /**
     * Equal-power (sin²/cos²) gains with zero slope at both endpoints, so the
     * ramp itself introduces no kink. Cut-out runs first; the cut-in starts
     * the exact sample the cut-out reaches zero — no silent gap in between.
     */
    private fun gainForNextSample(): Float {
        if (cutOutRemaining > 0) {
            cutOutRemaining--
            val progress = 1f - cutOutRemaining.toFloat() / cutOutSamples.toFloat()
            if (cutOutRemaining == 0) {
                cutInRemaining = cutInSamples
            }
            val c = cos(progress * PI / 2.0).toFloat()
            return c * c
        }
        if (cutInRemaining > 0) {
            cutInRemaining--
            val progress = 1f - cutInRemaining.toFloat() / cutInSamples.toFloat()
            val s = kotlin.math.sin(progress * PI / 2.0).toFloat()
            return s * s
        }
        if (fadeInRemaining > 0) {
            fadeInRemaining--
            val progress = 1f - fadeInRemaining.toFloat() / fadeInSamples.toFloat()
            val s = kotlin.math.sin(progress * PI / 2.0).toFloat()
            return s * s
        }
        return 1f
    }

    companion object {
        const val FADE_IN_MS = 10L
        const val CUT_OUT_MS = 8L
        const val CUT_IN_MS = 8L
    }
}

/**
 * Routes the controller's cut trigger to the two splice guards. Both decks
 * fire: at an INSTANT flip the outgoing is cut mid-waveform and the incoming
 * opens mid-waveform, so each side needs its own ramp.
 */
interface SpliceGuards {
    fun cut()

    object None : SpliceGuards {
        override fun cut() {}
    }
}
