package com.music.bitchord.playback.audio

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import com.music.bitchord.BuildConfig
import com.music.bitchord.playback.AudioOutputStatus
import com.music.bitchord.playback.audio.usb.DirectAudioOutput
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Precision audio sink intercepting decoder PCM buffers before Media3's internal
 * processing pipeline can bypass BitChord's custom DSP chain.
 *
 * Architecture:
 * - In Precision Mode (for all supported linear PCM sources):
 *   decoder PCM -> PcmBoundary.decode -> AudioBlock (Float32) -> DspChain (Spatial -> EQ -> Transition)
 *   -> PcmBoundary.encode -> delegate [DefaultAudioSink].
 *   Internal DSP precision and AudioTrack output precision are independent:
 *   - Float-capable route ([enableFloatOutput] is true and supported by delegate):
 *     encodes to Float32 PCM -> delegate [DefaultAudioSink] (configured for Float32).
 *   - PCM16-only route ([enableFloatOutput] is false or unsupported by route/hardware):
 *     encodes to PCM16 -> delegate [DefaultAudioSink] (configured for PCM16).
 *
 *   Downstream, custom processors are excluded from [DefaultAudioSink]'s internal processor chain,
 *   guaranteeing ZERO duplicate processing, while SilenceSkipping and Sonic processors remain functional.
 *
 * - In Fallback/Legacy Mode (for non-linear PCM or unsupported channel counts/sample rates):
 *   decoder buffers pass straight through to [DefaultAudioSink].
 *
 * Threading & Buffer Contract:
 * - Steady-state execution avoids heap allocations by reusing an audio-thread-owned [AudioBlock]
 *   and a native-order [outputByteBuffer].
 * - Partial delegate consumption is fully supported: when [delegate] returns false, the exact same
 *   [outputByteBuffer] instance is retained and drained on subsequent ticks before accepting new input.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PrecisionAudioSink(
    private val delegate: AudioSink,
    val dspChain: DspChain,
    private val enableFloatOutput: Boolean,
    val directAudioOutput: DirectAudioOutput? = null,
    private val preferredOutputEncodingProvider: ((Format) -> PcmEncoding?)? = null,
) : ForwardingAudioSink(delegate) {

    /** Whether the precision Float32 DSP path is currently active for the configured format. */
    var isPrecisionActive: Boolean = false
        private set

    /** The PCM encoding format of the incoming decoder buffers when precision mode is active. */
    var inputPcmEncoding: PcmEncoding? = null
        private set

    /** The target PCM encoding format written to [delegate] when precision mode is active. */
    var targetOutputEncoding: PcmEncoding? = null
        private set

    /** The format passed to the most recent [configure] invocation. */
    var activeFormat: Format? = null
        private set

    private var processCounter: Long = 0L

    private var audioBlock: AudioBlock = AudioBlock(
        channelCount = AudioBlock.DEFAULT_CHANNELS,
        capacityFrames = DEFAULT_CAPACITY_FRAMES,
    )

    private var outputByteBuffer: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

    private var pendingPresentationTimeUs: Long = C.TIME_UNSET
    private var pendingAccessUnitCount: Int = 0

    override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) {
        val format = audioSinkConfig.format
        activeFormat = format

        if (shouldActivatePrecision(format)) {
            val encoding = mapToPcmEncoding(format.pcmEncoding)
            if (encoding != null) {
                val sampleRate = format.sampleRate
                val channelCount = format.channelCount

                // 1. Configure the Float32 DSP chain
                dspChain.configure(sampleRate, channelCount)

                // 2. Ensure internal buffers are sized for max frames and channel count
                ensureBuffers(channelCount)

                // 3. Determine target output encoding based on preferred encoding, float output setting & delegate capability
                val floatFormat = format.buildUpon()
                    .setPcmEncoding(C.ENCODING_PCM_FLOAT)
                    .build()
                val pcm24Format = format.buildUpon()
                    .setPcmEncoding(C.ENCODING_PCM_24BIT)
                    .build()
                val pcm16Format = format.buildUpon()
                    .setPcmEncoding(C.ENCODING_PCM_16BIT)
                    .build()

                val preferredEncoding = preferredOutputEncodingProvider?.invoke(format)
                val isHighResSource = encoding.bitDepth > 16

                val targetEncoding = when {
                    preferredEncoding == PcmEncoding.PCM_FLOAT && delegate.supportsFormat(floatFormat) -> PcmEncoding.PCM_FLOAT
                    preferredEncoding == PcmEncoding.PCM_24BIT_PACKED && delegate.supportsFormat(pcm24Format) -> PcmEncoding.PCM_24BIT_PACKED
                    preferredEncoding == PcmEncoding.PCM_16BIT -> PcmEncoding.PCM_16BIT
                    preferredEncoding == null && enableFloatOutput && delegate.supportsFormat(floatFormat) -> PcmEncoding.PCM_FLOAT
                    else -> PcmEncoding.PCM_16BIT
                }

                // 4. Configure delegate DefaultAudioSink
                val delegateFormat = format.buildUpon()
                    .setPcmEncoding(targetEncoding.toMedia3PcmEncoding())
                    .build()

                val delegateConfig = AudioSink.AudioSinkConfig.Builder(delegateFormat)
                    .setPreferredBufferSizeOverride(audioSinkConfig.preferredBufferSizeOverride)
                    .setOutputChannelMapping(audioSinkConfig.outputChannelMapping)
                    .setTimeline(audioSinkConfig.timeline)
                    .setMediaPeriodId(audioSinkConfig.mediaPeriodId)
                    .build()

                try {
                    delegate.configure(delegateConfig)
                    inputPcmEncoding = encoding
                    targetOutputEncoding = targetEncoding
                    isPrecisionActive = true
                    publishTelemetry(encoding, targetEncoding, isPrecisionActive = true)
                    logConfig(
                        precisionActive = true,
                        inputEncoding = encoding.name,
                        sampleRate = sampleRate,
                        channelCount = channelCount,
                        targetOutputEncoding = targetEncoding.name,
                        enableFloatOutput = enableFloatOutput,
                        delegateEncoding = delegateFormat.pcmEncoding,
                    )
                    return
                } catch (e: Exception) {
                    // If Float32 or PCM24 delegate config failed, try falling back to PCM16 before aborting precision
                    if (targetEncoding != PcmEncoding.PCM_16BIT) {
                        try {
                            val fallbackPcm16Config = AudioSink.AudioSinkConfig.Builder(pcm16Format)
                                .setPreferredBufferSizeOverride(audioSinkConfig.preferredBufferSizeOverride)
                                .setOutputChannelMapping(audioSinkConfig.outputChannelMapping)
                                .setTimeline(audioSinkConfig.timeline)
                                .setMediaPeriodId(audioSinkConfig.mediaPeriodId)
                                .build()
                            delegate.configure(fallbackPcm16Config)
                            inputPcmEncoding = encoding
                            targetOutputEncoding = PcmEncoding.PCM_16BIT
                            isPrecisionActive = true
                            publishTelemetry(encoding, PcmEncoding.PCM_16BIT, isPrecisionActive = true)
                            logConfig(
                                precisionActive = true,
                                inputEncoding = encoding.name,
                                sampleRate = sampleRate,
                                channelCount = channelCount,
                                targetOutputEncoding = PcmEncoding.PCM_16BIT.name,
                                enableFloatOutput = enableFloatOutput,
                                delegateEncoding = pcm16Format.pcmEncoding,
                            )
                            return
                        } catch (e2: Exception) {
                            // Delegate configuration failed completely; fall back safely
                        }
                    }
                    isPrecisionActive = false
                    inputPcmEncoding = null
                    targetOutputEncoding = null
                }
            }
        }

        // Fallback / legacy mode: forward configuration unchanged
        isPrecisionActive = false
        inputPcmEncoding = null
        targetOutputEncoding = null
        AudioOutputStatus.publishDsp(decoderOutputEncoding = null, dspFormat = "Legacy PCM")
        delegate.configure(audioSinkConfig)
        logConfig(
            precisionActive = false,
            inputEncoding = format.pcmEncoding.toString(),
            sampleRate = format.sampleRate,
            channelCount = format.channelCount,
            targetOutputEncoding = "NONE",
            enableFloatOutput = enableFloatOutput,
            delegateEncoding = format.pcmEncoding,
        )
    }

    override fun handleBuffer(
        inputBuffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean {
        if (!isPrecisionActive) {
            return delegate.handleBuffer(inputBuffer, presentationTimeUs, encodedAccessUnitCount)
        }

        val inEncoding = inputPcmEncoding ?: return delegate.handleBuffer(
            inputBuffer,
            presentationTimeUs,
            encodedAccessUnitCount,
        )
        val outEncoding = targetOutputEncoding ?: return delegate.handleBuffer(
            inputBuffer,
            presentationTimeUs,
            encodedAccessUnitCount,
        )

        val channelCount = audioBlock.channelCount
        val bytesPerFrame = inEncoding.bytesPerFrame(channelCount)
        if (bytesPerFrame <= 0) return true

        // 1. Drain pending output from previous cycle if delegate had backpressure
        if (outputByteBuffer.hasRemaining()) {
            val consumed = delegate.handleBuffer(
                outputByteBuffer,
                pendingPresentationTimeUs,
                pendingAccessUnitCount,
            )
            if (!consumed || outputByteBuffer.hasRemaining()) {
                // Delegate still busy; backpressure to renderer
                return false
            }
        }

        // 2. Output buffer is drained; process available input in blocks
        while (inputBuffer.remaining() >= bytesPerFrame) {
            val availableFrames = inputBuffer.remaining() / bytesPerFrame
            if (availableFrames <= 0) break

            val framesToRead = minOf(availableFrames, audioBlock.capacityFrames)
            val decodedFrames = PcmBoundary.decode(
                inputBuffer = inputBuffer,
                encoding = inEncoding,
                destinationBlock = audioBlock,
                maxFrames = framesToRead,
            )
            if (decodedFrames <= 0) break

            if (BuildConfig.DEBUG) {
                processCounter++
                if (processCounter == 1L || processCounter % 500L == 0L) {
                    try {
                        val count = processCounter
                        val active = isPrecisionActive
                        val inEnc = inEncoding.name
                        val outEnc = outEncoding.name
                        val frames = decodedFrames
                        Log.d(
                            TAG,
                            "handleBuffer() #$count precisionActive=$active inEnc=$inEnc outEnc=$outEnc frames=$frames",
                        )
                    } catch (_: Throwable) {
                    }
                }
            }

            // Process normalized Float32 samples through custom DSP chain
            dspChain.process(audioBlock)

            // Encode processed samples to target PCM output buffer (Float32 or PCM16)
            outputByteBuffer.clear()
            PcmBoundary.encode(
                sourceBlock = audioBlock,
                encoding = outEncoding,
                outputBuffer = outputByteBuffer,
            )
            outputByteBuffer.flip()

            pendingPresentationTimeUs = presentationTimeUs
            pendingAccessUnitCount = encodedAccessUnitCount

            val consumed = delegate.handleBuffer(
                outputByteBuffer,
                presentationTimeUs,
                encodedAccessUnitCount,
            )

            if (!consumed || outputByteBuffer.hasRemaining()) {
                // Delegate could not accept all output frames in this cycle
                return false
            }
        }

        // Drop any incomplete trailing frame bytes so we don't stall
        if (inputBuffer.remaining() in 1 until bytesPerFrame) {
            inputBuffer.position(inputBuffer.limit())
        }

        return !inputBuffer.hasRemaining() && !outputByteBuffer.hasRemaining()
    }

    override fun flush() {
        outputByteBuffer.clear()
        outputByteBuffer.flip()
        audioBlock.clear()
        pendingPresentationTimeUs = C.TIME_UNSET
        pendingAccessUnitCount = 0
        if (isPrecisionActive) {
            dspChain.flush()
        }
        delegate.flush()
    }

    override fun reset() {
        processCounter = 0L
        outputByteBuffer.clear()
        outputByteBuffer.flip()
        audioBlock.clear()
        pendingPresentationTimeUs = C.TIME_UNSET
        pendingAccessUnitCount = 0
        if (isPrecisionActive) {
            dspChain.reset()
        }
        isPrecisionActive = false
        inputPcmEncoding = null
        targetOutputEncoding = null
        activeFormat = null
        AudioOutputStatus.publishDsp(decoderOutputEncoding = null, dspFormat = "Float32")
        delegate.reset()
    }

    override fun handleDiscontinuity() {
        delegate.handleDiscontinuity()
    }

    override fun playToEndOfStream() {
        if (isPrecisionActive && outputByteBuffer.hasRemaining()) {
            delegate.handleBuffer(outputByteBuffer, pendingPresentationTimeUs, pendingAccessUnitCount)
        }
        delegate.playToEndOfStream()
    }

    override fun isEnded(): Boolean {
        if (isPrecisionActive && outputByteBuffer.hasRemaining()) {
            return false
        }
        return delegate.isEnded()
    }

    override fun hasPendingData(): Boolean {
        if (isPrecisionActive && outputByteBuffer.hasRemaining()) {
            return true
        }
        return delegate.hasPendingData()
    }

    override fun supportsFormat(format: Format): Boolean {
        if (shouldActivatePrecision(format)) {
            val pcm16Format = format.buildUpon()
                .setPcmEncoding(C.ENCODING_PCM_16BIT)
                .build()
            val floatFormat = format.buildUpon()
                .setPcmEncoding(C.ENCODING_PCM_FLOAT)
                .build()
            val delegateCanPlay = (enableFloatOutput && delegate.supportsFormat(floatFormat)) ||
                delegate.supportsFormat(pcm16Format)
            if (delegateCanPlay) {
                return true
            }
        }
        return delegate.supportsFormat(format)
    }

    override fun getFormatSupport(format: Format): Int {
        if (shouldActivatePrecision(format)) {
            val pcm16Format = format.buildUpon()
                .setPcmEncoding(C.ENCODING_PCM_16BIT)
                .build()
            val floatFormat = format.buildUpon()
                .setPcmEncoding(C.ENCODING_PCM_FLOAT)
                .build()
            val delegateCanPlay = (enableFloatOutput && delegate.getFormatSupport(floatFormat) != AudioSink.SINK_FORMAT_UNSUPPORTED) ||
                (delegate.getFormatSupport(pcm16Format) != AudioSink.SINK_FORMAT_UNSUPPORTED)

            if (delegateCanPlay) {
                // PrecisionAudioSink natively consumes all supported linear PCM formats (Float32, PCM16, PCM24, PCM32)
                // directly into its canonical Float32 AudioBlock without requiring Media3 to transcode.
                return AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
            }
        }
        return delegate.getFormatSupport(format)
    }

    private fun shouldActivatePrecision(format: Format): Boolean {
        val sampleMimeType = format.sampleMimeType
        if (sampleMimeType != null && sampleMimeType != MimeTypes.AUDIO_RAW) return false
        if (!Util.isEncodingLinearPcm(format.pcmEncoding)) return false
        if (mapToPcmEncoding(format.pcmEncoding) == null) return false
        if (format.channelCount !in 1..2) return false
        if (format.sampleRate <= 0) return false
        return true
    }

    private fun ensureBuffers(channelCount: Int) {
        if (audioBlock.channelCount != channelCount) {
            audioBlock = AudioBlock(channelCount = channelCount, capacityFrames = DEFAULT_CAPACITY_FRAMES)
        } else {
            audioBlock.reset(0)
        }

        val requiredCapacity = audioBlock.capacityFrames * channelCount * PcmEncoding.PCM_FLOAT.bytesPerSample
        if (outputByteBuffer.capacity() < requiredCapacity) {
            outputByteBuffer = ByteBuffer.allocateDirect(requiredCapacity).order(ByteOrder.nativeOrder())
        }
        outputByteBuffer.clear()
        outputByteBuffer.flip()
    }

    private fun publishTelemetry(
        inEncoding: PcmEncoding,
        outEncoding: PcmEncoding,
        isPrecisionActive: Boolean,
    ) {
        if (isPrecisionActive) {
            val label = when (inEncoding) {
                PcmEncoding.PCM_FLOAT -> "Float32"
                PcmEncoding.PCM_24BIT_PACKED -> "24-bit PCM"
                PcmEncoding.PCM_32BIT -> "32-bit PCM"
                PcmEncoding.PCM_16BIT -> "16-bit PCM"
            }
            AudioOutputStatus.publishDsp(decoderOutputEncoding = label, dspFormat = "Float32")
        } else {
            AudioOutputStatus.publishDsp(decoderOutputEncoding = null, dspFormat = "Legacy PCM")
        }
    }

    private fun logConfig(
        precisionActive: Boolean,
        inputEncoding: String,
        sampleRate: Int,
        channelCount: Int,
        targetOutputEncoding: String,
        enableFloatOutput: Boolean,
        delegateEncoding: Int,
    ) {
        if (BuildConfig.DEBUG) {
            try {
                Log.d(
                    TAG,
                    "configure() precisionActive=$precisionActive inputEncoding=$inputEncoding sr=$sampleRate ch=$channelCount targetOutputEncoding=$targetOutputEncoding enableFloatOutput=$enableFloatOutput delegateEncoding=$delegateEncoding",
                )
            } catch (_: Throwable) {
            }
        }
    }

    companion object {
        private const val TAG = "PrecisionAudioSink"
        const val DEFAULT_CAPACITY_FRAMES: Int = 4096

        fun mapToPcmEncoding(pcmEncoding: Int): PcmEncoding? = when (pcmEncoding) {
            C.ENCODING_PCM_16BIT -> PcmEncoding.PCM_16BIT
            C.ENCODING_PCM_24BIT -> PcmEncoding.PCM_24BIT_PACKED
            C.ENCODING_PCM_32BIT -> PcmEncoding.PCM_32BIT
            C.ENCODING_PCM_FLOAT -> PcmEncoding.PCM_FLOAT
            else -> null
        }

        fun mapFromPcmEncoding(pcmEncoding: PcmEncoding): Int = when (pcmEncoding) {
            PcmEncoding.PCM_16BIT -> C.ENCODING_PCM_16BIT
            PcmEncoding.PCM_24BIT_PACKED -> C.ENCODING_PCM_24BIT
            PcmEncoding.PCM_32BIT -> C.ENCODING_PCM_32BIT
            PcmEncoding.PCM_FLOAT -> C.ENCODING_PCM_FLOAT
        }

        fun PcmEncoding.toMedia3PcmEncoding(): Int = mapFromPcmEncoding(this)
    }
}
