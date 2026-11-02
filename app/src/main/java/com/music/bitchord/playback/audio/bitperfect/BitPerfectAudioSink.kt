/*
 * Copyright (C) 2026 BitChord Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package com.music.bitchord.playback.audio.bitperfect

import android.media.AudioTrack
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.AudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Writes decoded PCM to a [BitPerfectTransport] untouched.
 *
 * Why not Media3's own sink with a custom `AudioOutputProvider`: that sink
 * converts every 24- and 32-bit stream to float before any output sees it,
 * and fades in the first 20 ms after every start and seek. The first loses
 * 32-bit sources outright; the second changes samples, which a DAC playing
 * DoP turns into a burst of noise. This sink does neither. It does the rest
 * of what a sink must — position from the device's own frame count, gapless
 * continuation while the format holds, a drain and reopen when it changes —
 * and nothing else.
 *
 * Volume, playback speed, silence skipping and audio effects are accepted and
 * ignored. Nothing upstream may scale a sample, and a speed other than 1x
 * would have to be resampled to be heard.
 *
 * The one exception, only with [com.music.bitchord.data.settings.AppSettings.convertForLimitedDac]
 * on, is a track the DAC cannot take at all. [DacFormatConverter] then fits it
 * to what the transport opened at, and the status says so.
 *
 * All calls arrive on the playback thread, as Media3 guarantees for a sink.
 */
@OptIn(UnstableApi::class)
internal class BitPerfectAudioSink(private val outputs: BitPerfectOutputs) : AudioSink {

    private data class Input(val format: Format) {
        val encoding = format.pcmEncoding
        val sampleRate = format.sampleRate
        val channelCount = format.channelCount
        val sourceBits = BitPerfectPcm.sourceBits(encoding) ?: 0
        val frameBytes = BitPerfectPcm.bytesPerSample(encoding) * channelCount

        fun sameStream(other: Input?) = other != null && encoding == other.encoding &&
            sampleRate == other.sampleRate && channelCount == other.channelCount
    }

    private var listener: AudioSink.Listener? = null
    private var audioAttributes = AudioAttributes.DEFAULT

    /** The most recent [configure]; opened lazily, at its first buffer. */
    private var configured: Input? = null

    /** What [transport] is carrying. */
    private var current: Input? = null
    private var transport: BitPerfectTransport? = null
    private var transportStarted = false

    /** Set only while [transport] could not be opened at the track's own format. */
    private var converter: DacFormatConverter? = null

    private var output: ByteBuffer = ByteBuffer.allocateDirect(0)
    private var outputFrameBytes = 0

    /** Output frames the transport has accepted since it was opened or flushed. */
    private var writtenFrames = 0L

    /** Input frames converted since it was opened or flushed, for timestamp checks. */
    private var submittedFrames = 0L
    private var startMediaTimeUs = C.TIME_UNSET
    private var needsResync = false
    private var currentInput: ByteBuffer? = null

    private var playing = false
    private var endOfStream = false
    private var stopped = false
    private var exact = true
    private var advancingReported = false

    /** A transport failure raised where Media3 allows no exception, rethrown at the next buffer. */
    private var pendingFailure: BitPerfectTransportException? = null

    /** Whether this exact stream could be opened now; asks nothing of the device beyond a query. */
    fun canAccept(format: Format): Boolean {
        if (format.sampleMimeType != MimeTypes.AUDIO_RAW) return false
        // Encoder priming means a lossy codec whose start and end need trimming:
        // not what bit-perfect output is for, and left to the ordinary path.
        if (format.encoderDelay > 0 || format.encoderPadding > 0) return false
        val bits = BitPerfectPcm.sourceBits(format.pcmEncoding) ?: return false
        if (format.channelCount !in 1..2 || format.sampleRate <= 0) return false
        return outputs.canOpen(format.sampleRate, format.channelCount, bits, this)
    }

    /** Whether normal output must wait for the DAC to come back to Android; see [BitPerfectOutput.normalOutputPending]. */
    fun holdsNormalOutput(): Boolean = outputs.normalOutputPending()

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
    }

    override fun supportsFormat(format: Format): Boolean =
        getFormatSupport(format) == AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY

    override fun getFormatSupport(format: Format): Int =
        if (canAccept(format)) AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY else AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) {
        val format = audioSinkConfig.format
        val mapping = audioSinkConfig.outputChannelMapping
        if (mapping != null && (0 until mapping.length()).any { mapping[it] != it }) {
            throw AudioSink.ConfigurationException("Channel remapping is not bit-perfect", format)
        }
        if (!canAccept(format)) {
            throw AudioSink.ConfigurationException("Bit-perfect output unavailable", format)
        }
        // Opening waits for the first buffer: whatever is being replaced may
        // still be playing out into this same device.
        configured = Input(format)
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean {
        pendingFailure?.let { pendingFailure = null; throw writeException(it) }
        val input = configured ?: return true
        if (transport != null && !input.sameStream(current)) {
            if (!drainForReconfiguration()) return false
            closeTransport()
        }
        if (transport == null) open(input)

        if (buffer !== currentInput) {
            currentInput = buffer
            syncTimestamp(input, presentationTimeUs)
        }
        try {
            if (output.hasRemaining()) {
                writeOutput()
                if (output.hasRemaining()) return false
            }
            while (buffer.remaining() >= input.frameBytes) {
                output.clear()
                val before = buffer.position()
                val converter = converter
                if (converter != null) {
                    converter.process(buffer, output)
                } else if (!BitPerfectPcm.convert(buffer, input.encoding, input.channelCount, requireNotNull(transport).outputBits, output)) {
                    markInexact()
                }
                submittedFrames += (buffer.position() - before) / input.frameBytes
                output.flip()
                writeOutput()
                if (output.hasRemaining()) return false
            }
        } catch (error: BitPerfectTransportException) {
            throw writeException(error)
        }
        // A trailing partial frame cannot be played; drop it rather than stall.
        buffer.position(buffer.limit())
        currentInput = null
        return true
    }

    override fun play() {
        playing = true
        startIfReady()
    }

    override fun pause() {
        playing = false
        transportStarted = false
        runCatching { transport?.pause() }
    }

    override fun handleDiscontinuity() {
        needsResync = true
    }

    override fun playToEndOfStream() {
        endOfStream = true
        val active = transport ?: return
        try {
            if (!flushOutput()) return
            if (!stopped) {
                active.stop()
                stopped = true
            }
        } catch (error: BitPerfectTransportException) {
            throw writeException(error)
        }
    }

    override fun isEnded(): Boolean = transport == null || (endOfStream && stopped && !hasPendingData())

    override fun hasPendingData(): Boolean {
        val active = transport ?: return false
        return output.hasRemaining() || active.playedFrames() < writtenFrames
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val active = transport ?: return AudioSink.CURRENT_POSITION_NOT_SET
        if (current == null || startMediaTimeUs == C.TIME_UNSET) return AudioSink.CURRENT_POSITION_NOT_SET
        val played = minOf(active.playedFrames(), writtenFrames)
        val playedUs = Util.sampleCountToDurationUs(played, active.sampleRate)
        if (!advancingReported && playing && played > 0) {
            advancingReported = true
            listener?.onPositionAdvancing(System.currentTimeMillis() - playedUs / 1000)
        }
        return startMediaTimeUs + playedUs
    }

    override fun flush() {
        runCatching { transport?.flush() }
        transportStarted = false
        resetCounters()
    }

    override fun reset() {
        flush()
        closeTransport()
    }

    override fun release() {
        reset()
    }

    override fun getAudioTrackBufferSizeUs(): Long {
        val active = transport ?: return C.TIME_UNSET
        return Util.sampleCountToDurationUs(active.bufferSizeFrames, active.sampleRate)
    }

    // Accepted and ignored: each would have to alter the samples to take effect.
    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) = Unit
    override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters.DEFAULT
    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) = Unit
    override fun getSkipSilenceEnabled(): Boolean = false
    override fun setVolume(volume: Float) = Unit
    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) = Unit
    override fun setAudioSessionId(audioSessionId: Int) = Unit

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        this.audioAttributes = audioAttributes
    }

    override fun getAudioAttributes(): AudioAttributes = audioAttributes

    override fun enableTunnelingV21() = Unit
    override fun disableTunneling() = Unit

    private fun open(input: Input) {
        val opened = try {
            outputs.open(input.sampleRate, input.channelCount, input.sourceBits, this)
        } catch (error: BitPerfectTransportException) {
            outputs.markFailed(
                input.sampleRate,
                input.channelCount,
                input.sourceBits,
                error.message.orEmpty(),
            )
            throw AudioSink.InitializationException(
                "Bit-perfect output unavailable: ${error.message}",
                AudioTrack.STATE_UNINITIALIZED,
                input.format,
                /* isRecoverable = */ true,
                error,
            )
        }
        transport = opened
        current = input
        converter = if (opened.sampleRate != input.sampleRate || opened.validBits < input.sourceBits) {
            DacFormatConverter(
                encoding = input.encoding,
                channels = input.channelCount,
                inputRate = input.sampleRate,
                outputRate = opened.sampleRate,
                validBits = opened.validBits,
                containerBits = opened.outputBits,
            )
        } else {
            null
        }
        outputFrameBytes = opened.channelCount * (opened.outputBits / 8)
        val capacity = OUTPUT_CHUNK_FRAMES * outputFrameBytes
        if (output.capacity() < capacity) {
            output = ByteBuffer.allocateDirect(capacity).order(ByteOrder.LITTLE_ENDIAN)
        }
        output.clear().limit(0)
        exact = true
        resetCounters()
        Log.i(
            TAG,
            "Opened ${opened.driver.label} on ${opened.deviceName}: ${opened.sampleRate} Hz, " +
                "${input.sourceBits}-bit source as ${opened.outputBits}-bit" +
                if (converter != null) " (DAC limit, source ${input.sampleRate} Hz)" else "",
        )
        publishStatus()
        startIfReady()
    }

    private fun closeTransport() {
        val active = transport ?: return
        runCatching { active.release() }
        outputs.release(this)
        outputs.publishIdle()
        transport = null
        current = null
        converter = null
        transportStarted = false
        output.clear().limit(0)
    }

    /**
     * Plays out the stream the transport was opened for, so a new format can
     * reopen it. Returns true once nothing of it is left to hear.
     */
    private fun drainForReconfiguration(): Boolean {
        val active = transport ?: return true
        try {
            if (!flushOutput()) return false
            if (!stopped) {
                active.stop()
                stopped = true
            }
        } catch (error: BitPerfectTransportException) {
            throw writeException(error)
        }
        return active.playedFrames() >= writtenFrames
    }

    /** Hands the transport everything still held on this side of it; true once that is all of it. */
    private fun flushOutput(): Boolean {
        while (true) {
            if (output.hasRemaining()) writeOutput()
            if (output.hasRemaining()) return false
            val converter = converter ?: return true
            output.clear()
            val done = converter.drain(output)
            output.flip()
            if (done && !output.hasRemaining()) return true
        }
    }

    private fun writeOutput() {
        val active = transport ?: return
        val taken = active.write(output)
        if (taken > 0) writtenFrames += taken / outputFrameBytes
        startIfReady()
    }

    /** Starts the device once it is both wanted and has something to play. */
    private fun startIfReady() {
        val active = transport ?: return
        if (!playing || transportStarted || writtenFrames == 0L) return
        try {
            active.play()
            transportStarted = true
        } catch (error: BitPerfectTransportException) {
            pendingFailure = error
        }
    }

    /**
     * Keeps [startMediaTimeUs] in step with the renderer's timestamps, the way
     * Media3's own sink does: set from the first buffer, and moved when a new
     * buffer arrives more than [MAX_TIMESTAMP_DRIFT_US] from where the frames
     * already written say it should.
     */
    private fun syncTimestamp(input: Input, presentationTimeUs: Long) {
        if (startMediaTimeUs == C.TIME_UNSET) {
            startMediaTimeUs = presentationTimeUs
            needsResync = false
            return
        }
        val expectedUs = startMediaTimeUs + Util.sampleCountToDurationUs(submittedFrames, input.sampleRate)
        if (needsResync || abs(expectedUs - presentationTimeUs) > MAX_TIMESTAMP_DRIFT_US) {
            if (expectedUs != presentationTimeUs) {
                startMediaTimeUs += presentationTimeUs - expectedUs
                listener?.onPositionDiscontinuity()
            }
            needsResync = false
        }
    }

    private fun markInexact() {
        if (!exact) return
        exact = false
        Log.i(TAG, "Source is not integer PCM; samples are rounded to the DAC's depth")
        publishStatus()
    }

    private fun publishStatus() {
        val active = transport ?: return
        outputs.publish(
            BitPerfectOutput.Status(
                active = true,
                driver = active.driver.label,
                deviceName = active.deviceName,
                sampleRateHz = active.sampleRate,
                outputBits = if (converter != null) active.validBits else active.outputBits,
                sourceBits = current?.sourceBits ?: 0,
                sourceRateHz = current?.sampleRate ?: 0,
                exact = exact && converter == null,
                dacLimited = converter != null,
            ),
        )
    }

    private fun resetCounters() {
        converter?.reset()
        output.clear().limit(0)
        writtenFrames = 0
        submittedFrames = 0
        startMediaTimeUs = C.TIME_UNSET
        needsResync = false
        currentInput = null
        endOfStream = false
        stopped = false
        advancingReported = false
    }

    private fun writeException(error: BitPerfectTransportException): AudioSink.WriteException {
        Log.w(TAG, "Bit-perfect output stopped: ${error.message}")
        return AudioSink.WriteException(
            AudioTrack.ERROR_DEAD_OBJECT,
            current?.format ?: configured?.format ?: Format.Builder().build(),
            /* isRecoverable = */ true,
        )
    }

    companion object {
        private const val TAG = "BitPerfectAudioSink"
        private const val OUTPUT_CHUNK_FRAMES = 4096
        private const val MAX_TIMESTAMP_DRIFT_US = 200_000L
    }
}
