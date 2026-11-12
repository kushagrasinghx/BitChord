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

import java.nio.ByteBuffer

/**
 * One exclusive, mixer-free output opened at a track's own rate and channel
 * count. [BitPerfectAudioSink] owns exactly one at a time and drives it from
 * the playback thread; nothing here resamples, mixes or applies gain.
 *
 * Every call except [release] may throw [BitPerfectTransportException] once
 * the route is lost. The sink turns that into a recoverable Media3 error, so
 * the player rebuilds its output rather than carrying on into a device that
 * is no longer the one this stream was verified against.
 */
internal interface BitPerfectTransport {
    /** Which of the three output paths this is, for status and logs. */
    val driver: BitPerfectDriver
    val deviceName: String
    val sampleRate: Int
    val channelCount: Int

    /** Integer precision on the wire: 16, 24 or 32. */
    val outputBits: Int

    /** How much audio the transport itself buffers, in frames. */
    val bufferSizeFrames: Long

    /** Copies as much of [buffer] as fits without blocking; returns the byte count taken. */
    fun write(buffer: ByteBuffer): Int

    fun play()

    fun pause()

    /** Drops buffered audio and resets [playedFrames] to zero. */
    fun flush()

    /** No more audio follows: play out what is buffered. */
    fun stop()

    /** Frames the device has actually played since the last [flush], excluding inserted silence. */
    fun playedFrames(): Long

    fun release()
}

internal enum class BitPerfectDriver(val label: String) {
    ANDROID_MIXER("Android bit-perfect mixer"),
    USB_DIRECT("Direct USB"),
    AAUDIO_EXCLUSIVE("AAudio exclusive"),
}

internal class BitPerfectTransportException(message: String, cause: Throwable? = null) : Exception(message, cause)
