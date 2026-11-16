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
import android.os.SystemClock
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
 * A pause longer than [IDLE_RELEASE_MS] hands a direct USB or AAudio exclusive
 * device back (see [suspend]), so the DAC can power down while nothing plays.
 * What it had been given but not yet played is kept, and play resumes from
 * that exact frame on a reopened device.
 *
 * All calls arrive on the playback thread, as Media3 guarantees for a sink.
 */
@OptIn(UnstableApi::class)
internal class BitPerfectAudioSink(
    private val outputs: BitPerfectOutputs,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : AudioSink {

    private data class Input(val format: Format) {
        val encoding = format.pcmEncoding
        val sampleRate = format.sampleRate
        val channelCount = format.channelCount
        val sourceBits = BitPerfectPcm.sourceBits(encoding) ?: 0
        val frameBytes = BitPerfectPcm.bytesPerSample(encoding) * channelCount

        fun sameStream(other: Input?) = other != null && encoding == other.encoding &&
            sampleRate == other.sampleRate && channelCount == other.channelCount
    }

    /** What a reopened transport must match for audio kept from the old one to play on it unchanged. */
    private data class Wire(
        val driver: BitPerfectDriver,
        val sampleRate: Int,
        val channelCount: Int,
        val outputBits: Int,
        val validBits: Int,
    ) {
        constructor(transport: BitPerfectTransport) : this(
            transport.driver,
            transport.sampleRate,
            transport.channelCount,
            transport.outputBits,
            transport.validBits,
        )
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

    /**
     * Output frames of the stream handed to a transport since the last open or
     * flush. Audio kept across a [suspend] and handed over again is not counted twice.
     */
    private var writtenFrames = 0L

    /** Frames [transport] itself has accepted, a resumed tail included; its [BitPerfectTransport.playedFrames] counts within these. */
    private var transportFrames = 0L

    /** Stream frames played before [transport] was opened, on the one a [suspend] released. */
    private var framesBase = 0L

    /** The last bytes [transport] accepted, for [suspend]. Null for a driver that is never released idle. */
    private var recent: RecentBytes? = null

    /** When playing last stopped, or [NOT_PAUSED]. */
    private var pausedAtMs = NOT_PAUSED

    /** The device was handed back during a long pause; [resume] reopens it. */
    private var suspended = false
    private var suspendedWire: Wire? = null

    /** What the released device had accepted but not played, to go to the reopened one first. */
    private var resumeTail: ByteBuffer? = null

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
        releaseIfIdle()
        val input = configured ?: return true
        if (suspended) {
            // Media3 keeps offering buffers while paused; the device stays released until play.
            if (!playing) return false
            try {
                resume()
            } catch (error: BitPerfectTransportException) {
                throw AudioSink.InitializationException(
                    "Bit-perfect output could not resume: ${error.message}",
                    AudioTrack.STATE_UNINITIALIZED,
                    input.format,
                    /* isRecoverable = */ true,
                    error,
                )
            }
        }
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
            if (!writeResumeTail()) return false
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
        pausedAtMs = NOT_PAUSED
        startIfReady()
    }

    override fun pause() {
        playing = false
        transportStarted = false
        pausedAtMs = clock()
        runCatching { transport?.pause() }
    }

    override fun handleDiscontinuity() {
        needsResync = true
    }

    override fun playToEndOfStream() {
        endOfStream = true
        releaseIfIdle()
        if (suspended) {
            if (!playing) return
            try {
                resume()
            } catch (error: BitPerfectTransportException) {
                throw writeException(error)
            }
        }
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

    override fun isEnded(): Boolean =
        (transport == null && !suspended) || (endOfStream && stopped && !hasPendingData())

    override fun hasPendingData(): Boolean {
        val held = output.hasRemaining() || resumeTail != null
        if (suspended) return held
        val active = transport ?: return false
        return held || playedFrames(active) < writtenFrames
    }

    // While suspended there is no device clock; Media3 then holds the position it last had.
    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val active = transport ?: return AudioSink.CURRENT_POSITION_NOT_SET
        if (current == null || startMediaTimeUs == C.TIME_UNSET) return AudioSink.CURRENT_POSITION_NOT_SET
        val played = minOf(playedFrames(active), writtenFrames)
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
        recent = if (opened.driver.releasesWhenIdle) {
            // Twice what the device buffers: [suspend] needs everything it has not played yet.
            val bytes = (opened.bufferSizeFrames * 2).coerceAtMost(Int.MAX_VALUE.toLong() / outputFrameBytes).toInt() * outputFrameBytes
            recent?.takeIf { it.capacity == bytes } ?: RecentBytes(bytes)
        } else {
            null
        }
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
        suspended = false
        suspendedWire = null
        resumeTail = null
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

    /** Releases the device once playing has been stopped for [IDLE_RELEASE_MS]. */
    private fun releaseIfIdle() {
        val active = transport ?: return
        if (playing || !active.driver.releasesWhenIdle) return
        // Opened without ever playing (a queue restored paused, say): count from now.
        if (pausedAtMs == NOT_PAUSED) pausedAtMs = clock()
        if (clock() - pausedAtMs >= IDLE_RELEASE_MS) suspend()
    }

    /**
     * Hands the device back during a long pause: a USB DAC returns to
     * Android's driver and can power down, the native worker stops waking,
     * and an exclusive AAudio stream is closed. [current], the converter and
     * the clock stay as they are. What the device had accepted but not
     * played is copied out of [recent], so [resume] can give it to the
     * reopened device before anything new.
     */
    private fun suspend() {
        val active = transport ?: return
        val history = recent ?: return
        val played = runCatching { active.playedFrames() }.getOrDefault(0L).coerceIn(0L, transportFrames)
        val keptBytes = minOf((transportFrames - played) * outputFrameBytes, history.size.toLong()).toInt()
        // A tail not yet fully handed over to this device comes after what it holds.
        val unwritten = resumeTail
        val tail = ByteBuffer.allocateDirect(keptBytes + (unwritten?.remaining() ?: 0)).order(ByteOrder.LITTLE_ENDIAN)
        history.copyLast(keptBytes, tail)
        unwritten?.let(tail::put)
        tail.flip()
        // Normally exactly what was played; later if the history could not hold everything unplayed.
        framesBase = writtenFrames - tail.remaining() / outputFrameBytes
        resumeTail = tail.takeIf { it.hasRemaining() }
        suspendedWire = Wire(active)
        suspended = true
        Log.i(TAG, "Released ${active.driver.label} after a long pause, keeping ${tail.remaining() / outputFrameBytes} frames")
        runCatching { active.release() }
        outputs.release(this)
        outputs.publishIdle()
        transport = null
        transportStarted = false
    }

    /**
     * Reopens what [suspend] released. With nothing kept (it had all played,
     * or a seek since dropped it), the next buffer opens as any new stream does.
     */
    private fun resume() {
        val wire = suspendedWire
        suspended = false
        suspendedWire = null
        if (resumeTail == null && !output.hasRemaining()) {
            current = null
            converter = null
            return
        }
        val previous = requireNotNull(current)
        val opened = outputs.open(previous.sampleRate, previous.channelCount, previous.sourceBits, this)
        if (Wire(opened) != wire) {
            // A different DAC, or one now offering another format: the kept audio cannot go to it as it is.
            runCatching { opened.release() }
            outputs.release(this)
            current = null
            converter = null
            throw BitPerfectTransportException("device_changed")
        }
        transport = opened
        transportFrames = 0
        recent?.clear()
        // stop() went to the device just released; a stream already ended must be told again.
        stopped = false
        Log.i(TAG, "Reopened ${opened.driver.label} on ${opened.deviceName} after a long pause")
        publishStatus()
    }

    /** Gives the reopened device what [suspend] kept; true once all of it is in. */
    private fun writeResumeTail(): Boolean {
        val tail = resumeTail ?: return true
        val active = transport ?: return false
        handOver(active, tail)
        startIfReady()
        if (tail.hasRemaining()) return false
        resumeTail = null
        return true
    }

    /** Stream frames played so far, on this device and the ones before it. */
    private fun playedFrames(active: BitPerfectTransport): Long = framesBase + active.playedFrames()

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
        return playedFrames(active) >= writtenFrames
    }

    /** Hands the transport everything still held on this side of it; true once that is all of it. */
    private fun flushOutput(): Boolean {
        if (!writeResumeTail()) return false
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
        val taken = handOver(active, output)
        if (taken > 0) writtenFrames += taken / outputFrameBytes
        startIfReady()
    }

    /** Writes what [active] takes of [buffer], keeping a copy in [recent] for a later [suspend]. */
    private fun handOver(active: BitPerfectTransport, buffer: ByteBuffer): Int {
        val start = buffer.position()
        val taken = active.write(buffer)
        if (taken > 0) {
            recent?.record(buffer, start, taken)
            transportFrames += taken / outputFrameBytes
        }
        return taken
    }

    /** Starts the device once it is both wanted and has something to play. */
    private fun startIfReady() {
        val active = transport ?: return
        if (!playing || transportStarted || transportFrames == 0L) return
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
        transportFrames = 0
        framesBase = 0
        recent?.clear()
        // A flush while suspended drops what was kept but leaves the device
        // released: a seek during a long pause should not reopen it before play.
        resumeTail = null
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

        /** A pause this long releases a direct USB or AAudio exclusive device. */
        const val IDLE_RELEASE_MS = 30_000L
        private const val NOT_PAUSED = -1L
    }
}

/** The last [capacity] bytes recorded, the oldest dropped first. */
internal class RecentBytes(val capacity: Int) {
    private val ring = ByteBuffer.allocateDirect(capacity)
    private var end = 0

    var size = 0
        private set

    fun clear() {
        end = 0
        size = 0
    }

    /** Records [count] bytes of [source] from index [from], leaving its position as it is. */
    fun record(source: ByteBuffer, from: Int, count: Int) {
        if (capacity == 0) return
        var left = minOf(count, capacity)
        var at = from + count - left
        size = minOf(capacity, size + left)
        val view = source.duplicate()
        while (left > 0) {
            val n = minOf(left, capacity - end)
            view.limit(at + n)
            view.position(at)
            ring.position(end)
            ring.put(view)
            end = (end + n) % capacity
            at += n
            left -= n
        }
    }

    /** Appends the newest [count] recorded bytes to [target], oldest first. */
    fun copyLast(count: Int, target: ByteBuffer) {
        require(count in 0..size) { "Only $size bytes recorded" }
        if (count == 0) return
        var at = (end - count + capacity) % capacity
        var left = count
        val view = ring.duplicate()
        while (left > 0) {
            val n = minOf(left, capacity - at)
            view.limit(at + n)
            view.position(at)
            target.put(view)
            at = (at + n) % capacity
            left -= n
        }
    }
}
