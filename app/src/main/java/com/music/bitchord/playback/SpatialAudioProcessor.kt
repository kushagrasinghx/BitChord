package com.music.bitchord.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.music.bitchord.playback.audio.AudioBlock
import com.music.bitchord.playback.audio.FloatAudioProcessor
import com.music.bitchord.playback.audio.PcmBoundary
import com.music.bitchord.playback.spatializer.SpeakerResponseStore
import com.music.bitchord.playback.spatializer.StereoSpatializer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** How [SpatialAudioProcessor] spatializes a stereo track. */
enum class SpatialMode {
    /** Mid/side widening plus a short cross-feed: cheap, speaker-friendly, not true binaural. */
    WIDEN,

    /**
     * Stereo Spatialization ([StereoSpatializer]), for headphones: upmix to 5.1, then play the six channels as
     * virtual speakers in a room around the listener, rendered with measured head-related impulse responses.
     */
    SPATIALIZE,
}

/**
 * Spatial audio for stereo tracks, in one of two [SpatialMode]s.
 *
 * [SpatialMode.SPATIALIZE] hands the float path to [StereoSpatializer]. [SpatialMode.WIDEN] is the
 * cheap stand-in this class started as: widens the mid/side image and mixes in
 * a short, low-passed cross-feed between channels — the same trick most
 * consumer virtual-surround plugins use. O(1) per sample, no FFT or
 * convolution, so it costs nothing worth measuring on a phone CPU.
 *
 * Exists because the platform [android.media.audiofx.Virtualizer] produced no
 * audible difference on the reference device — likely swallowed by the OEM's
 * own audio effect chain — so this runs inside ExoPlayer's own audio
 * processor pipeline instead, where nothing else can intercept it.
 */
@UnstableApi
class SpatialAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false

    /** Which spatializer runs while [enabled]. Read on the audio thread at every block. */
    @Volatile
    var mode: SpatialMode = SpatialMode.SPATIALIZE

    /**
     * The spatializer for the current rate, built on first use. Null while the rate has no responses
     * (outside [SpeakerResponseStore.MIN_RATE]..[SpeakerResponseStore.MAX_RATE], or before the store is
     * initialised), in which case [SpatialMode.SPATIALIZE] falls back to widening rather than going silent.
     */
    private var spatializer: StereoSpatializer? = null

    /** Whether the previous block went through [spatializer]; a fresh start resets it so no stale audio replays. */
    private var spatializerRunning = false

    /** How much wider the stereo image gets. 1.0 = untouched. */
    private val widthGain = 2.5f

    /** Makeup attenuation after widening, so the wider side energy doesn't clip. */
    private val outputGain = 0.82f

    /** How much of the delayed, low-passed opposite channel gets mixed back in. */
    private val crossfeedGain = 0.2f

    /** One-pole lowpass factor applied to the cross-fed signal — dulls it, like a far ear would. */
    private val lowpassCoeff = 0.3f

    private var sampleRate: Int = 0
    private var channelCount: Int = 0

    private var delayLeft = FloatArray(0)
    private var delayRight = FloatArray(0)
    private var delayIndex = 0
    private var lowpassLeft = 0f
    private var lowpassRight = 0f

    /**
     * Configures the Float32 DSP engine for [sampleRate] and [channelCount].
     */
    fun configure(sampleRate: Int, channelCount: Int) {
        // A new stream in the same format (the next track of a gapless run) keeps the spatializer going:
        // resetting it would cut the previous track's tail and open a ~50 ms hole. Seeks still reset it (flush).
        val keepSpatializer = spatializerRunning && sampleRate == this.sampleRate && channelCount == this.channelCount
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        if (channelCount != 2 || sampleRate <= 0) {
            delayLeft = FloatArray(0)
            delayRight = FloatArray(0)
            delayIndex = 0
            lowpassLeft = 0f
            lowpassRight = 0f
            return
        }
        val delaySamples = (sampleRate * DELAY_MS / 1000f)
            .roundToInt()
            .coerceAtLeast(1)
        if (delayLeft.size != delaySamples) {
            delayLeft = FloatArray(delaySamples)
            delayRight = FloatArray(delaySamples)
        }
        onFlush()
        spatializerRunning = keepSpatializer
    }

    /**
     * Processes interleaved Float32 audio samples in [block] in-place.
     * Preserves dynamic headroom without clamping to [-1.0f, +1.0f].
     */
    fun process(block: AudioBlock) {
        if (!enabled || block.frameCount == 0) {
            if (!enabled) spatializerRunning = false
            return
        }
        if (block.channelCount != 2) return // Spatial widening only applies to stereo

        if (mode == SpatialMode.SPATIALIZE) {
            val active = activeSpatializer()
            if (active != null) {
                if (!spatializerRunning) {
                    active.reset()
                    spatializerRunning = true
                }
                active.process(block.samples, block.frameCount)
                return
            }
        }
        spatializerRunning = false

        val delaySize = delayLeft.size
        if (delaySize == 0) return

        val totalSamples = block.frameCount * 2
        var idx = 0
        var dIdx = delayIndex
        var lpL = lowpassLeft
        var lpR = lowpassRight

        while (idx < totalSamples) {
            val left = block.samples[idx]
            val right = block.samples[idx + 1]

            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f * widthGain
            var widenedLeft = mid + side
            var widenedRight = mid - side

            val delayedRight = delayRight[dIdx]
            val delayedLeft = delayLeft[dIdx]
            lpL += lowpassCoeff * (delayedRight - lpL)
            lpR += lowpassCoeff * (delayedLeft - lpR)
            widenedLeft += lpL * crossfeedGain
            widenedRight += lpR * crossfeedGain

            delayLeft[dIdx] = left
            delayRight[dIdx] = right
            dIdx = (dIdx + 1) % delaySize

            // Headroom is preserved: no clamping to [-1.0f, +1.0f]
            block.samples[idx] = widenedLeft * outputGain
            block.samples[idx + 1] = widenedRight * outputGain
            idx += 2
        }

        delayIndex = dIdx
        lowpassLeft = lpL
        lowpassRight = lpR
    }

    /**
     * Frames of output still owed after the last input frame: the spatializer's latency plus its room tail.
     * The precision sink feeds this much silence through the chain at end of stream so the end of a track is not
     * cut off. Zero when the spatializer is not running.
     */
    fun tailFrames(): Int =
        if (enabled && spatializerRunning) spatializer?.tailFrames ?: 0 else 0

    private fun activeSpatializer(): StereoSpatializer? {
        val current = spatializer
        if (current != null && current.sampleRate == sampleRate) return current
        if (channelCount != 2 || sampleRate <= 0) return null
        val responses = SpeakerResponseStore.forRate(sampleRate) ?: return null
        return StereoSpatializer(sampleRate, responses).also {
            spatializer = it
            spatializerRunning = false
        }
    }

    /**
     * Stereo 16-bit only: the widening is written in terms of a left and a
     * right sample, and there is no mid/side of a mono voice note or of a 5.1
     * mix to widen.
     *
     * Bowing out with [AudioProcessor.AudioFormat.NOT_SET] rather than an
     * [AudioProcessor.UnhandledAudioFormatException] is what keeps those
     * tracks playable at all.
     * [DefaultAudioSink][androidx.media3.exoplayer.audio.DefaultAudioSink]
     * configures every processor in its chain whether or not the effect is
     * switched on, and a throw from any
     * of them fails the whole sink — the renderer dies with
     * "MediaCodecAudioRenderer error" before a sample is written. NOT_SET
     * means "inactive for this format" and the chain routes around this
     * processor instead.
     *
     * Nothing from YouTube is anything but stereo, so this only ever showed
     * itself on files from the device: every mono or multichannel track in the
     * local library failed to play while downloads were fine.
     */
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        configure(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        return inputAudioFormat
    }

    override fun onFlush() {
        spatializerRunning = false
        delayLeft.fill(0f)
        delayRight.fill(0f)
        delayIndex = 0
        lowpassLeft = 0f
        lowpassRight = 0f
    }

    override fun onReset() {
        spatializer = null
        spatializerRunning = false
        delayLeft = FloatArray(0)
        delayRight = FloatArray(0)
        delayIndex = 0
        lowpassLeft = 0f
        lowpassRight = 0f
        channelCount = 0
        sampleRate = 0
    }

    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
        val frameCount = inputBuffer.remaining() / BYTES_PER_FRAME
        if (frameCount == 0) return
        val outputBuffer = replaceOutputBuffer(frameCount * BYTES_PER_FRAME)

        if (!enabled) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        val delaySize = delayLeft.size
        if (delaySize == 0) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        var dIdx = delayIndex
        var lpL = lowpassLeft
        var lpR = lowpassRight
        val invScale = 1.0f / 32768.0f

        repeat(frameCount) {
            val left = inputBuffer.short.toFloat() * invScale
            val right = inputBuffer.short.toFloat() * invScale

            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f * widthGain
            var widenedLeft = mid + side
            var widenedRight = mid - side

            val delayedRight = delayRight[dIdx]
            val delayedLeft = delayLeft[dIdx]
            lpL += lowpassCoeff * (delayedRight - lpL)
            lpR += lowpassCoeff * (delayedLeft - lpR)
            widenedLeft += lpL * crossfeedGain
            widenedRight += lpR * crossfeedGain

            delayLeft[dIdx] = left
            delayRight[dIdx] = right
            dIdx = (dIdx + 1) % delaySize

            outputBuffer.putShort(PcmBoundary.clamp16FromFloat(widenedLeft * outputGain))
            outputBuffer.putShort(PcmBoundary.clamp16FromFloat(widenedRight * outputGain))
        }
        delayIndex = dIdx
        lowpassLeft = lpL
        lowpassRight = lpR

        outputBuffer.flip()
    }

    private companion object {
        const val BYTES_PER_FRAME = 4 // stereo, 16-bit
        const val DELAY_MS = 15
    }
}
