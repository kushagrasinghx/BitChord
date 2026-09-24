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
 *   - Float-capable route ([enableFloatOutput] is true and the delegate opens float natively):
 *     encodes to Float32 PCM -> delegate [DefaultAudioSink] (configured for Float32).
 *   - PCM16-only route ([enableFloatOutput] is false or unsupported by route/hardware):
 *     encodes to PCM16 -> delegate [DefaultAudioSink] (configured for PCM16).
 *
 *   Downstream, custom processors are excluded from [DefaultAudioSink]'s internal processor chain,
 *   guaranteeing ZERO duplicate processing, while SilenceSkipping and Sonic processors remain functional.
 *
 * - In Fallback/Legacy Mode (for non-linear PCM sources — a passthrough or offload
 *   bitstream): decoder buffers pass straight through to [DefaultAudioSink]. Nothing
 *   here can process a compressed stream, and nothing here pretends to.
 *
 * ## Output encoding
 *
 * [DefaultAudioSink] has exactly two linear-PCM output encodings: float, and 16-bit.
 * Its `configure` puts `ToInt16PcmAudioProcessor` in the chain for every input
 * encoding whenever float output is off, and `ToFloatPcmAudioProcessor` when it is
 * on. There is no 24-bit or 32-bit AudioTrack path through it, so asking for one
 * gets a silent downconvert rather than what was asked for — which is why
 * [resolveTargetEncoding] only ever answers float or 16-bit, however high-res the
 * route claims to be.
 *
 * Float is checked with [AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY] rather than
 * `supportsFormat`, which is too weak to be a gate: with float output disabled,
 * `getFormatSupport` rewrites a float format to 16-bit and still answers
 * SUPPORTED_WITH_TRANSCODING, so `supportsFormat` says "yes" about a track it is
 * going to downconvert.
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
    /**
     * Whether this sink is the one the listener can currently hear.
     *
     * A crossfade keeps two players alive, each with its own sink, both writing
     * into the process-wide [AudioOutputStatus]. Without this the idle one wins
     * the race regularly — its `reset` publishes "not exact" over the audible
     * player's verdict, and its `configure` publishes the *next* track's format
     * as though it were the playing one. Telemetry is therefore the audible
     * sink's to write; the spare stays quiet.
     */
    private val isAudible: () -> Boolean = { true },
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

    /** Sample rate of the configured format, for timestamping split sub-blocks. */
    private var configuredSampleRate: Int = 0

    private var audioBlock: AudioBlock = AudioBlock(
        channelCount = AudioBlock.DEFAULT_CHANNELS,
        capacityFrames = DEFAULT_CAPACITY_FRAMES,
    )

    private var outputByteBuffer: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())

    private var pendingPresentationTimeUs: Long = C.TIME_UNSET
    private var pendingAccessUnitCount: Int = 0

    /**
     * Frames already emitted from the decoder buffer currently being drained,
     * and the timestamp that identifies it.
     *
     * Kept across calls rather than per call because backpressure splits one
     * buffer over several: when the delegate refuses a sub-block, the renderer
     * hands the same partially-consumed buffer back with its *original*
     * presentation time, and a per-call counter would restart at zero and
     * re-issue a timestamp already used.
     */
    private var timestampedInputTimeUs: Long = C.TIME_UNSET
    private var framesEmittedForInput: Long = 0L

    /**
     * End-of-stream drain of the DSP chain's tail ([DspChain.tailFrames]: the stereo spatializer's latency plus its
     * room). -1 until [playToEndOfStream] first runs; then the frames of silence still to push through the chain.
     * The renderer calls [playToEndOfStream] on every loop until [isEnded], so the drain resumes where the
     * delegate's backpressure stopped it, and the delegate only hears about end of stream once it is done.
     */
    private var drainFramesRemaining: Int = -1

    /** Length of the drain in progress, and how many of its last frames fade out (only a reconfiguration's do). */
    private var drainTotalFrames: Int = 0
    private var drainFadeFrames: Int = 0

    /**
     * A configuration for a new sample rate or channel count, held back while the DSP chain still owes output for
     * the stream before it; see [configure].
     */
    private var pendingConfig: AudioSink.AudioSinkConfig? = null

    /** Presentation time just past the last frame written, where drained frames continue from. */
    private var emittedEndTimeUs: Long = C.TIME_UNSET

    /**
     * The span of real input since the last flush: its first presentation time and the end of its latest block.
     * [getCurrentPositionUs] stays inside it once the chain's latency is taken off, so the position neither dips
     * before a seek target while the delay line fills nor runs past the track's end while its tail drains.
     */
    private var inputStartTimeUs: Long = C.TIME_UNSET
    private var inputEndTimeUs: Long = C.TIME_UNSET

    override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) {
        // A new sample rate or channel count resets the DSP chain, and with spatial audio on the chain is still
        // holding the end of the previous stream (the effect's ~50 ms delay, then its room). Resetting now would
        // cut that off, so the configuration is held back, as DefaultAudioSink holds its own, and the next
        // handleBuffer plays the held audio out in the old format first. Nothing is held while no stage owes
        // output, so a bit-exact stream reconfigures at once, exactly as before.
        if (pendingConfig != null) {
            pendingConfig = audioSinkConfig
            return
        }
        val current = activeFormat
        val format = audioSinkConfig.format
        if (isPrecisionActive && current != null && dspChain.tailFrames() > 0 &&
            (format.sampleRate != current.sampleRate || format.channelCount != current.channelCount)
        ) {
            pendingConfig = audioSinkConfig
            return
        }
        applyConfiguration(audioSinkConfig)
    }

    private fun applyConfiguration(audioSinkConfig: AudioSink.AudioSinkConfig) {
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
                configuredSampleRate = sampleRate

                // 3. Determine target output encoding
                val floatFormat = format.buildUpon()
                    .setPcmEncoding(C.ENCODING_PCM_FLOAT)
                    .build()
                val pcm16Format = format.buildUpon()
                    .setPcmEncoding(C.ENCODING_PCM_16BIT)
                    .build()

                val targetEncoding = resolveTargetEncoding(
                    preferred = preferredOutputEncodingProvider?.invoke(format),
                    floatFormat = floatFormat,
                )

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
                    publishTelemetry(encoding, targetEncoding)
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
                    // If the Float32 delegate config failed, try PCM16 before aborting precision
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
                            publishTelemetry(encoding, PcmEncoding.PCM_16BIT)
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

        // Fallback / legacy mode: forward configuration unchanged. A passthrough
        // or offload bitstream reaches here, in which case nothing of ours is
        // altering samples — but nothing of ours can vouch for them either, and
        // no DSP stage can run on a stream that was never decoded to PCM.
        isPrecisionActive = false
        inputPcmEncoding = null
        targetOutputEncoding = null
        configuredSampleRate = 0
        if (isAudible()) {
            AudioOutputStatus.publishOutputExactness(
                exact = false,
                detail = "${encodingLabel(format.pcmEncoding)} is not linear PCM",
            )
            AudioOutputStatus.publishDsp(
                decoderOutputEncoding = null,
                dspFormat = "Legacy PCM",
                dspAvailable = false,
            )
        }
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
        pendingConfig?.let { config ->
            // This buffer is already in the new format: first finish the old stream, then switch.
            if (!finishStreamForReconfiguration()) return false
            pendingConfig = null
            applyConfiguration(config)
            if (!isPrecisionActive) {
                return delegate.handleBuffer(inputBuffer, presentationTimeUs, encodedAccessUnitCount)
            }
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

        // 2. Output buffer is drained; process available input in blocks.
        //    A decoder buffer larger than the block capacity is split, and each
        //    sub-block carries its own timestamp: handing the delegate the same
        //    presentation time for every piece makes its expected-position check
        //    drift by one block each time, and past 200ms of accumulated drift it
        //    raises UnexpectedDiscontinuityException and resyncs the clock.
        if (presentationTimeUs != timestampedInputTimeUs) {
            timestampedInputTimeUs = presentationTimeUs
            framesEmittedForInput = 0L
        }
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
            if (isAudible()) AudioOutputStatus.publishSpatial(dspChain.spatial.activeEffect, dspChain.spatial.bypassReason)

            // Encode processed samples to target PCM output buffer (Float32 or PCM16)
            outputByteBuffer.clear()
            PcmBoundary.encode(
                sourceBlock = audioBlock,
                encoding = outEncoding,
                outputBuffer = outputByteBuffer,
            )
            outputByteBuffer.flip()

            val blockTimeUs = advanceTimestamp(presentationTimeUs, framesEmittedForInput)
            // The access-unit count describes the whole decoder buffer, so it is
            // reported once, on the first piece of it.
            val blockAccessUnits = if (framesEmittedForInput == 0L) encodedAccessUnitCount else 0
            pendingPresentationTimeUs = blockTimeUs
            pendingAccessUnitCount = blockAccessUnits
            framesEmittedForInput += decodedFrames
            emittedEndTimeUs = advanceTimestamp(blockTimeUs, decodedFrames.toLong())
            if (inputStartTimeUs == C.TIME_UNSET) inputStartTimeUs = blockTimeUs
            inputEndTimeUs = emittedEndTimeUs
            drainFramesRemaining = -1

            val consumed = delegate.handleBuffer(
                outputByteBuffer,
                blockTimeUs,
                blockAccessUnits,
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

    /** [presentationTimeUs] moved on by [frames], or left alone when the rate is unknown. */
    private fun advanceTimestamp(presentationTimeUs: Long, frames: Long): Long {
        if (frames <= 0L || configuredSampleRate <= 0 || presentationTimeUs == C.TIME_UNSET) {
            return presentationTimeUs
        }
        return presentationTimeUs + Util.sampleCountToDurationUs(frames, configuredSampleRate)
    }

    override fun flush() {
        // A seek abandons whatever was being played out, but a held configuration still has to take effect.
        pendingConfig?.let {
            pendingConfig = null
            applyConfiguration(it)
        }
        drainFramesRemaining = -1
        emittedEndTimeUs = C.TIME_UNSET
        inputStartTimeUs = C.TIME_UNSET
        inputEndTimeUs = C.TIME_UNSET
        outputByteBuffer.clear()
        outputByteBuffer.flip()
        audioBlock.clear()
        pendingPresentationTimeUs = C.TIME_UNSET
        pendingAccessUnitCount = 0
        timestampedInputTimeUs = C.TIME_UNSET
        framesEmittedForInput = 0L
        if (isPrecisionActive) {
            dspChain.flush()
        }
        delegate.flush()
    }

    override fun reset() {
        pendingConfig = null
        drainFramesRemaining = -1
        emittedEndTimeUs = C.TIME_UNSET
        inputStartTimeUs = C.TIME_UNSET
        inputEndTimeUs = C.TIME_UNSET
        processCounter = 0L
        outputByteBuffer.clear()
        outputByteBuffer.flip()
        audioBlock.clear()
        pendingPresentationTimeUs = C.TIME_UNSET
        pendingAccessUnitCount = 0
        timestampedInputTimeUs = C.TIME_UNSET
        framesEmittedForInput = 0L
        if (isPrecisionActive) {
            dspChain.reset()
        }
        isPrecisionActive = false
        inputPcmEncoding = null
        targetOutputEncoding = null
        activeFormat = null
        configuredSampleRate = 0
        if (isAudible()) {
            AudioOutputStatus.publishOutputExactness(exact = false, detail = null)
            AudioOutputStatus.publishDsp(
                decoderOutputEncoding = null,
                dspFormat = "Float32",
                dspAvailable = true,
            )
            AudioOutputStatus.publishSpatial(effect = null, bypass = null)
        }
        delegate.reset()
    }

    override fun handleDiscontinuity() {
        delegate.handleDiscontinuity()
    }

    override fun playToEndOfStream() {
        if (isPrecisionActive) {
            if (outputByteBuffer.hasRemaining()) {
                val consumed = delegate.handleBuffer(outputByteBuffer, pendingPresentationTimeUs, pendingAccessUnitCount)
                if (!consumed || outputByteBuffer.hasRemaining()) return
            }
            if (!drainTail()) return
            pendingConfig?.let {
                pendingConfig = null
                applyConfiguration(it)
            }
        } else if (outputByteBuffer.hasRemaining()) {
            delegate.handleBuffer(outputByteBuffer, pendingPresentationTimeUs, pendingAccessUnitCount)
        }
        delegate.playToEndOfStream()
    }

    /**
     * Before a held configuration takes effect: plays out the chain's delay (the last ~50 ms of the old stream's
     * music) and [RECONFIGURE_FADE_MS] of its room, faded. Kept short on purpose: the new stream's first buffer
     * is stamped where the old one ended, and DefaultAudioSink treats a jump of 200 ms or more as a discontinuity.
     */
    private fun finishStreamForReconfiguration(): Boolean {
        if (outputByteBuffer.hasRemaining()) {
            val consumed = delegate.handleBuffer(outputByteBuffer, pendingPresentationTimeUs, pendingAccessUnitCount)
            if (!consumed || outputByteBuffer.hasRemaining()) return false
        }
        val fade = RECONFIGURE_FADE_MS * configuredSampleRate / 1000
        return drainTail(minOf(dspChain.tailFrames(), dspChain.latencyFrames() + fade), fade)
    }

    /**
     * Pushes the chain's tail (see [drainFramesRemaining]) through to the delegate as silence-in, audio-out,
     * [totalFrames] of it with the last [fadeFrames] faded out. Returns false while the delegate is still applying
     * backpressure; call again on the next render loop.
     */
    private fun drainTail(totalFrames: Int = dspChain.tailFrames(), fadeFrames: Int = 0): Boolean {
        val outEncoding = targetOutputEncoding ?: return true
        if (drainFramesRemaining < 0) {
            drainFramesRemaining = totalFrames
            drainTotalFrames = totalFrames
            drainFadeFrames = minOf(fadeFrames, totalFrames)
        }
        val channels = audioBlock.channelCount
        while (drainFramesRemaining > 0) {
            val frames = minOf(drainFramesRemaining, audioBlock.capacityFrames)
            audioBlock.reset(frames)
            java.util.Arrays.fill(audioBlock.samples, 0, frames * channels, 0f)
            dspChain.process(audioBlock)
            if (drainFadeFrames > 0) {
                val written = drainTotalFrames - drainFramesRemaining
                val fadeStart = drainTotalFrames - drainFadeFrames
                for (i in 0 until frames) {
                    val left = drainTotalFrames - (written + i)
                    if (written + i < fadeStart) continue
                    val gain = (0.5 - 0.5 * kotlin.math.cos(Math.PI * left / drainFadeFrames)).toFloat()
                    for (c in 0 until channels) audioBlock.samples[i * channels + c] *= gain
                }
            }
            outputByteBuffer.clear()
            PcmBoundary.encode(
                sourceBlock = audioBlock,
                encoding = outEncoding,
                outputBuffer = outputByteBuffer,
            )
            outputByteBuffer.flip()
            pendingPresentationTimeUs = emittedEndTimeUs
            pendingAccessUnitCount = 0
            emittedEndTimeUs = advanceTimestamp(emittedEndTimeUs, frames.toLong())
            drainFramesRemaining -= frames
            val consumed = delegate.handleBuffer(outputByteBuffer, pendingPresentationTimeUs, 0)
            if (!consumed || outputByteBuffer.hasRemaining()) return false
        }
        return true
    }

    /**
     * The delegate's position is that of the frames it has played, stamped with the presentation times of the
     * input they were made from. The DSP chain delays its output ([DspChain.latencyFrames]: ~50 ms while spatial
     * audio is on), so what is audible is that much older: synced lyrics, and anything else that follows the
     * position, would otherwise run early by the delay.
     */
    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val position = super.getCurrentPositionUs(sourceEnded)
        if (!isPrecisionActive || position == AudioSink.CURRENT_POSITION_NOT_SET || configuredSampleRate <= 0) {
            return position
        }
        val latencyFrames = dspChain.latencyFrames()
        if (latencyFrames <= 0) return position
        var heard = position - latencyFrames * C.MICROS_PER_SECOND / configuredSampleRate
        if (inputStartTimeUs != C.TIME_UNSET) heard = maxOf(heard, inputStartTimeUs)
        if (inputEndTimeUs != C.TIME_UNSET) heard = minOf(heard, inputEndTimeUs)
        return heard
    }

    override fun isEnded(): Boolean {
        if (isPrecisionActive && (outputByteBuffer.hasRemaining() || drainFramesRemaining > 0 || pendingConfig != null)) {
            return false
        }
        return delegate.isEnded()
    }

    override fun hasPendingData(): Boolean {
        if (isPrecisionActive && (outputByteBuffer.hasRemaining() || drainFramesRemaining > 0 || pendingConfig != null)) {
            return true
        }
        return delegate.hasPendingData()
    }

    override fun supportsFormat(format: Format): Boolean =
        getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

    /**
     * How well this sink can carry [format], which Media3 reads for more than a
     * yes/no: `MediaCodecAudioRenderer.getMediaFormat` asks about a PCM-float
     * format and requests float decoder output *only* when the answer is
     * [AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY].
     *
     * So answering SUPPORTED_DIRECTLY for float on a route that cannot open a
     * float track is not a harmless overstatement — it makes every decoder emit
     * float, including on the 16-bit speaker path. A 16-bit source then arrives
     * here as Float32 and is reported as converted-on-the-way-out, when in fact
     * the round trip through the float block recovers it exactly. The float
     * answer therefore tracks what [resolveTargetEncoding] will really do.
     */
    override fun getFormatSupport(format: Format): Int {
        if (shouldActivatePrecision(format)) {
            val pcm16Format = format.buildUpon()
                .setPcmEncoding(C.ENCODING_PCM_16BIT)
                .build()
            val floatFormat = format.buildUpon()
                .setPcmEncoding(C.ENCODING_PCM_FLOAT)
                .build()
            val pcm16Playable =
                delegate.getFormatSupport(pcm16Format) != AudioSink.SINK_FORMAT_UNSUPPORTED
            val floatIsNative = floatIsNative(floatFormat)

            if (format.pcmEncoding == C.ENCODING_PCM_FLOAT) {
                return when {
                    floatIsNative -> AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
                    pcm16Playable -> AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING
                    else -> AudioSink.SINK_FORMAT_UNSUPPORTED
                }
            }
            if (floatIsNative || pcm16Playable) {
                // PrecisionAudioSink natively consumes every supported linear PCM input
                // encoding (Float32, PCM16, PCM24, PCM32) straight into its canonical
                // Float32 AudioBlock, without requiring Media3 to transcode first.
                return AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
            }
        }
        return delegate.getFormatSupport(format)
    }

    /**
     * Whether the delegate will open a genuine float AudioTrack for [floatFormat].
     *
     * [enableFloatOutput] is checked first because `getFormatSupport` alone cannot
     * answer it: with float output off the delegate rewrites the format to 16-bit
     * and reports SUPPORTED_WITH_TRANSCODING, which is a different claim than the
     * one being made here.
     */
    private fun floatIsNative(floatFormat: Format): Boolean =
        enableFloatOutput &&
            delegate.getFormatSupport(floatFormat) == AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY

    /**
     * The encoding handed to the delegate, which is only ever float or 16-bit.
     *
     * [OutputNegotiator] can name PCM_24BIT_PACKED when a route advertises packed
     * 24-bit, and that used to be passed straight through. It cannot be honoured:
     * [DefaultAudioSink] has no 24-bit AudioTrack path, so it quietly converted the
     * result again — to 16-bit whenever float output was off — leaving a needless
     * extra quantization in the middle and a route summary claiming 24 bits that
     * were never written. A preference for more than 16 bits is therefore read as
     * what it is, a request for float, and honoured only where float is real.
     */
    private fun resolveTargetEncoding(preferred: PcmEncoding?, floatFormat: Format): PcmEncoding {
        val wantsAbove16Bit = when (preferred) {
            PcmEncoding.PCM_16BIT -> return PcmEncoding.PCM_16BIT
            PcmEncoding.PCM_FLOAT, PcmEncoding.PCM_24BIT_PACKED, PcmEncoding.PCM_32BIT -> true
            null -> enableFloatOutput
        }
        return if (wantsAbove16Bit && floatIsNative(floatFormat)) {
            PcmEncoding.PCM_FLOAT
        } else {
            PcmEncoding.PCM_16BIT
        }
    }

    private fun shouldActivatePrecision(format: Format): Boolean {
        val sampleMimeType = format.sampleMimeType
        if (sampleMimeType != null && sampleMimeType != MimeTypes.AUDIO_RAW) return false
        if (!Util.isEncodingLinearPcm(format.pcmEncoding)) return false
        if (mapToPcmEncoding(format.pcmEncoding) == null) return false
        // Any channel layout the decoder produces, not just mono and stereo. A
        // Dolby (E-AC-3 JOC) stream decodes to 5.1, and capping this at two
        // channels was what silently dropped the whole DSP chain — equaliser
        // included — for every immersive track. Each stage handles an arbitrary
        // channel count already; the ones that are inherently stereo, like
        // spatial widening, bow out on their own.
        if (format.channelCount !in 1..MAX_CHANNELS) return false
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

    /**
     * Reports whether the decoder's samples survive the trip to AudioTrack.
     *
     * This is the *encoding* half of bit-exactness — whether anything is being
     * lost between [PcmBoundary] and the track. Whether the listener also has a
     * DSP stage switched on is a separate question, and the readout combines the
     * two; see `AudioPipelineDialog.bitExactVerdict`.
     *
     * The combinations that are genuinely lossless:
     *
     * - 16-bit source to a 16-bit track. [PcmBoundary] scales by 32768, a power
     *   of two, so the float round trip recovers every original integer.
     * - 24-bit source to a float track. Scaled by 8388608, likewise exact, and
     *   float32 has the 24 bits of significand to hold the result.
     * - A float source to a float track, which is a copy.
     *
     * Everything else is inexact, and there are two of those worth naming. A
     * hi-res source on a route that will not open a float track gets 16-bit,
     * because Media3 offers nothing between. And **32-bit integer PCM cannot be
     * delivered exactly at all** — float32 carries 24 bits of significand, so
     * eight bits go regardless of route, and no setting in this app can change
     * that while [DefaultAudioSink] is doing the writing.
     */
    private fun publishOutputExactness(source: PcmEncoding, target: PcmEncoding) {
        val exact = when (source) {
            PcmEncoding.PCM_16BIT -> target == PcmEncoding.PCM_16BIT
            PcmEncoding.PCM_24BIT_PACKED -> target == PcmEncoding.PCM_FLOAT
            PcmEncoding.PCM_FLOAT -> target == PcmEncoding.PCM_FLOAT
            // 24 bits of significand cannot hold 32.
            PcmEncoding.PCM_32BIT -> false
        }
        val sourceLabel = encodingLabel(mapFromPcmEncoding(source))
        val detail = when {
            exact -> "$sourceLabel → ${encodingLabel(mapFromPcmEncoding(target))}"
            source == PcmEncoding.PCM_32BIT ->
                "32-bit PCM exceeds what AudioTrack can carry; 8 bits lost"
            else -> "$sourceLabel → 16-bit; this route cannot open a float track"
        }
        AudioOutputStatus.publishOutputExactness(exact = exact, detail = detail)
    }

    private fun publishTelemetry(inEncoding: PcmEncoding, outEncoding: PcmEncoding) {
        if (!isAudible()) return
        publishOutputExactness(inEncoding, outEncoding)
        AudioOutputStatus.publishDsp(
            decoderOutputEncoding = encodingLabel(mapFromPcmEncoding(inEncoding)),
            dspFormat = "Float32",
            dspAvailable = true,
        )
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

        /** How much of the room is played out, fading, before a held reconfiguration; see [finishStreamForReconfiguration]. */
        private const val RECONFIGURE_FADE_MS = 100

        /**
         * Widest channel layout the precision path will take on. Eight covers
         * 7.1, which is the most any decoder here produces; the cap exists so a
         * nonsense channel count cannot size a buffer without bound.
         */
        const val MAX_CHANNELS: Int = 8

        /** Human-readable name for a Media3 PCM encoding constant. */
        fun encodingLabel(pcmEncoding: Int): String = when (pcmEncoding) {
            C.ENCODING_PCM_16BIT -> "16-bit PCM"
            C.ENCODING_PCM_24BIT -> "24-bit PCM"
            C.ENCODING_PCM_32BIT -> "32-bit PCM"
            C.ENCODING_PCM_FLOAT -> "Float32"
            else -> "Non-PCM"
        }

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
