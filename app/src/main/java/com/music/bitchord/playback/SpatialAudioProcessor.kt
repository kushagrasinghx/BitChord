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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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

/** What [SpatialAudioProcessor.activeEffect] reports while each effect is altering samples. */
object SpatialEffect {
    const val SPATIALIZE = "Stereo Spatialization"
    const val WIDEN = "Widen"
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
 * ## Latency and switching
 *
 * The spatializer delays the audio by [StereoSpatializer.latencyFrames] (~50 ms); the widener, as ever, adds no
 * delay. [latencyFrames] reports the current delay so the player's position follows what is actually heard.
 *
 * Every change of effect is a crossfade, never a cut: switching the widener on or off is a 60 ms crossfade between
 * time-aligned signals. Going to or from the spatializer changes the delay by those ~50 ms, so it is a short 20 ms
 * crossfade instead, under which ~50 ms of audio is skipped or heard twice; it cannot click. The spatializer is
 * started ahead of that crossfade and fed until its steering has settled, so it never fades in from silence.
 *
 * A new stream (after a seek, flush or format change) starts directly on the right path without a crossfade.
 * The next track of a gapless run in the same format continues on the running path, tail and all.
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

    /**
     * Which effect runs while [enabled]. Read on the audio thread at every block. The widener unless told
     * otherwise, as before there was a choice; PlaybackService sets it from the listener's setting.
     */
    @Volatile
    var mode: SpatialMode = SpatialMode.WIDEN

    private enum class Path { DRY, WIDEN, SPATIALIZE }

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

    /**
     * The spatializer for the current rate, built on first use. Null while the rate has no responses
     * (outside [SpeakerResponseStore.MIN_RATE]..[SpeakerResponseStore.MAX_RATE], or the store failed), in which
     * case [SpatialMode.SPATIALIZE] falls back to widening rather than going silent.
     */
    private var spatializer: StereoSpatializer? = null
    private var spatializerUnavailable = false

    /** Why [SpatialMode.SPATIALIZE] can't run at this rate, once known; see [bypassReason]. */
    private var unsupportedReason: String? = null

    /**
     * The effect actually altering samples right now ("Stereo Spatialization", "Widen"), or null while the
     * audio passes through untouched. Written on the audio thread per block; the audible sink publishes it so
     * the Audio Pipeline readout reports what is happening rather than what is switched on.
     */
    @Volatile
    var activeEffect: String? = null
        private set

    /** Why the effect is switched on but leaving the samples untouched (not stereo, no responses at this rate). */
    @Volatile
    var bypassReason: String? = null
        private set

    /** The spatializer's latency at this rate, 0 where it cannot run. */
    private var alignFrames = 0

    /** The path being heard, or faded out of while [target] differs. */
    private var heard = Path.DRY
    private var target = Path.DRY

    /** Frames [target] still needs before it can be faded in, then the crossfade's length and progress. */
    private var primeFrames = 0
    private var fadeFrames = 0
    private var fadePos = 0

    /** The next block starts a new stream: begin on the wanted path directly, without a crossfade. */
    private var fresh = true

    private var delayLeft = FloatArray(0)
    private var delayRight = FloatArray(0)
    private var delayIndex = 0
    private var lowpassLeft = 0f
    private var lowpassRight = 0f

    private var widenBuffer = FloatArray(0)
    private var spatialBuffer = FloatArray(0)

    /**
     * Configures the Float32 DSP engine for [sampleRate] and [channelCount].
     */
    fun configure(sampleRate: Int, channelCount: Int) {
        val sameFormat = sampleRate == this.sampleRate && channelCount == this.channelCount
        // The next track of a gapless run: keep the running path going, so the previous track's delayed audio and
        // room tail play out into this one instead of being cut. Seeks still reset it (flush sets [fresh]).
        if (sameFormat && !fresh && (heard != Path.DRY || target != Path.DRY)) return
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        if (!sameFormat) {
            spatializer = null
            spatializerUnavailable = false
            unsupportedReason = null
        }
        val stereo = channelCount == 2 && sampleRate > 0
        alignFrames = if (stereo && sampleRate in SpeakerResponseStore.MIN_RATE..SpeakerResponseStore.MAX_RATE) {
            StereoSpatializer.latencyFramesFor(sampleRate)
        } else {
            0
        }
        val delaySamples = if (stereo) (sampleRate * DELAY_MS / 1000f).roundToInt().coerceAtLeast(1) else 0
        if (delayLeft.size != delaySamples) {
            delayLeft = FloatArray(delaySamples)
            delayRight = FloatArray(delaySamples)
        }
        onFlush()
    }

    /**
     * Processes interleaved Float32 audio samples in [block] in-place.
     * Preserves dynamic headroom without clamping to [-1.0f, +1.0f].
     */
    fun process(block: AudioBlock) {
        val frames = block.frameCount
        if (frames == 0) return
        if (block.channelCount != 2 || channelCount != 2 || sampleRate <= 0) {
            // Mono voice notes and multichannel files pass through bit for bit; there is no stereo image to act on.
            activeEffect = null
            bypassReason = if (enabled) NOT_STEREO else null
            return
        }

        val want = wantedPath()
        if (fresh) {
            fresh = false
            heard = want
            target = want
            primeFrames = 0
            start(want)
        } else if (want != target) {
            switchTo(want)
        }
        activeEffect = when {
            heard == Path.SPATIALIZE || target == Path.SPATIALIZE -> SpatialEffect.SPATIALIZE
            heard == Path.WIDEN || target == Path.WIDEN -> SpatialEffect.WIDEN
            else -> null
        }
        bypassReason = if (activeEffect == null && enabled && mode == SpatialMode.SPATIALIZE) unsupportedReason else null
        // Off and settled: the block leaves untouched, not multiplied through by unity.
        if (heard == Path.DRY && target == Path.DRY) return

        val samples = block.samples
        val n = 2 * frames
        if (heard == Path.WIDEN || target == Path.WIDEN) {
            if (widenBuffer.size < n) widenBuffer = FloatArray(n)
            System.arraycopy(samples, 0, widenBuffer, 0, n)
            widen(widenBuffer, frames)
        }
        if (heard == Path.SPATIALIZE || target == Path.SPATIALIZE) {
            if (spatialBuffer.size < n) spatialBuffer = FloatArray(n)
            System.arraycopy(samples, 0, spatialBuffer, 0, n)
            spatializer!!.process(spatialBuffer, frames)
        }

        for (i in 0 until frames) {
            val l = 2 * i
            val r = l + 1
            if (heard == target || primeFrames > 0) {
                if (primeFrames > 0) primeFrames--
                samples[l] = sampleOf(heard, samples, l)
                samples[r] = sampleOf(heard, samples, r)
            } else {
                // equal-power crossfade from [heard] to [target]
                val t = (fadePos + 0.5) / fadeFrames * (PI / 2)
                val gOut = cos(t).toFloat()
                val gIn = sin(t).toFloat()
                samples[l] = sampleOf(heard, samples, l) * gOut + sampleOf(target, samples, l) * gIn
                samples[r] = sampleOf(heard, samples, r) * gOut + sampleOf(target, samples, r) * gIn
                if (++fadePos >= fadeFrames) heard = target
            }
        }
    }

    /**
     * Frames of output still owed after the last input frame: the running effect's delay plus, for the
     * spatializer, its room tail. The precision sink feeds this much silence through the chain at end of stream
     * so the end of a track is not cut off. Zero when the effect is off.
     */
    fun tailFrames(): Int = maxOf(tailOf(heard), tailOf(target))

    /**
     * The delay between input and output right now, in frames: the spatializer's latency while either effect is
     * on, 0 while off. During a switch that changes it, the value moves over at the middle of the crossfade.
     */
    fun latencyFrames(): Int {
        val switching = heard != target && primeFrames == 0 && 2 * fadePos >= fadeFrames
        return latencyOf(if (switching) target else heard)
    }

    /** [latencyFrames] in microseconds at the configured rate. */
    fun latencyUs(): Long = if (sampleRate > 0) latencyFrames().toLong() * 1_000_000L / sampleRate else 0L

    /**
     * The delay this processor will add once it is running with the current settings and format, in
     * microseconds. Unlike [latencyUs] it is already known right after a seek or flush, before the first block:
     * what a caller needs to line a freshly seeked player up with one that is already playing.
     */
    fun expectedLatencyUs(): Long {
        if (!enabled || channelCount != 2 || sampleRate <= 0) return 0L
        if (mode != SpatialMode.SPATIALIZE || spatializerUnavailable) return 0L
        return alignFrames.toLong() * 1_000_000L / sampleRate
    }

    private fun wantedPath(): Path = when {
        !enabled -> Path.DRY
        mode == SpatialMode.WIDEN -> Path.WIDEN
        activeSpatializer() != null -> Path.SPATIALIZE
        // No responses at this rate (outside what they cover, or they failed to load): leave the samples exactly
        // as they are rather than quietly running a different effect; [bypassReason] says why.
        else -> Path.DRY
    }

    private fun sampleOf(path: Path, dry: FloatArray, index: Int): Float = when (path) {
        Path.DRY -> dry[index]
        Path.WIDEN -> widenBuffer[index]
        Path.SPATIALIZE -> spatialBuffer[index]
    }

    private fun switchTo(want: Path) {
        // A new switch in the middle of a crossfade settles the old one at whichever end is nearer.
        if (heard != target && primeFrames == 0 && 2 * fadePos >= fadeFrames) heard = target
        target = want
        primeFrames = 0
        fadePos = 0
        if (want == heard) return
        start(want)
        primeFrames = when (want) {
            Path.DRY, Path.WIDEN -> 0
            Path.SPATIALIZE -> alignFrames + SPATIALIZE_WARMUP_HOPS * (spatializer?.hop ?: 0)
        }
        val fadeMs = if (latencyOf(heard) == latencyOf(want)) ALIGNED_FADE_MS else SHIFTING_FADE_MS
        fadeFrames = (sampleRate * fadeMs / 1000).coerceAtLeast(1)
    }

    /** Clears [path]'s state so it starts from silence. An idle path keeps stale state until then; it isn't read. */
    private fun start(path: Path) {
        when (path) {
            Path.DRY -> Unit
            Path.WIDEN -> clearWidener()
            Path.SPATIALIZE -> spatializer?.reset()
        }
    }

    private fun latencyOf(path: Path): Int = if (path == Path.SPATIALIZE) alignFrames else 0

    private fun tailOf(path: Path): Int = when (path) {
        Path.DRY -> 0
        Path.WIDEN -> 0
        Path.SPATIALIZE -> spatializer?.tailFrames ?: 0
    }

    private fun activeSpatializer(): StereoSpatializer? {
        spatializer?.let { return it }
        if (spatializerUnavailable) return null
        val responses = if (alignFrames == 0) null else SpeakerResponseStore.forRate(sampleRate)
        if (responses == null) {
            spatializerUnavailable = true
            unsupportedReason = if (alignFrames == 0) {
                "no speaker responses at %.1f kHz".format(java.util.Locale.ROOT, sampleRate / 1000.0)
            } else {
                "speaker responses unavailable"
            }
            return null
        }
        return StereoSpatializer(sampleRate, responses).also { spatializer = it }
    }

    /** Mid/side widening plus the cross-feed, exactly as before the spatializer existed. */
    private fun widen(buffer: FloatArray, frames: Int) {
        val delaySize = delayLeft.size
        if (delaySize > 0) {
            var dIdx = delayIndex
            var lpL = lowpassLeft
            var lpR = lowpassRight
            for (i in 0 until frames) {
                val left = buffer[2 * i]
                val right = buffer[2 * i + 1]

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
                buffer[2 * i] = widenedLeft * outputGain
                buffer[2 * i + 1] = widenedRight * outputGain
            }
            delayIndex = dIdx
            lowpassLeft = lpL
            lowpassRight = lpR
        }
    }

    private fun clearWidener() {
        delayLeft.fill(0f)
        delayRight.fill(0f)
        delayIndex = 0
        lowpassLeft = 0f
        lowpassRight = 0f
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
        fresh = true
        heard = Path.DRY
        target = Path.DRY
        primeFrames = 0
        fadePos = 0
        clearWidener()
    }

    override fun onReset() {
        spatializer = null
        spatializerUnavailable = false
        unsupportedReason = null
        activeEffect = null
        bypassReason = null
        fresh = true
        heard = Path.DRY
        target = Path.DRY
        primeFrames = 0
        fadePos = 0
        alignFrames = 0
        delayLeft = FloatArray(0)
        delayRight = FloatArray(0)
        delayIndex = 0
        lowpassLeft = 0f
        lowpassRight = 0f
        widenBuffer = FloatArray(0)
        spatialBuffer = FloatArray(0)
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
        private const val BYTES_PER_FRAME = 4 // stereo, 16-bit
        private const val DELAY_MS = 15

        /** Crossfade when the delay doesn't change (the widener on or off): long enough to be a smooth morph. */
        private const val ALIGNED_FADE_MS = 60

        /** Crossfade to or from the spatializer: short, as the two sides are ~50 ms apart in time. */
        private const val SHIFTING_FADE_MS = 20

        /** Hops the spatializer runs before it is faded in, so its steering has settled (~130 ms at 48 kHz). */
        private const val SPATIALIZE_WARMUP_HOPS = 6

        private const val NOT_STEREO = "not stereo"
    }
}
