/*
 * Copyright (C) 2026 BitChord Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package com.music.bitchord.playback.audio.usb

import com.music.bitchord.playback.audio.PcmEncoding
import java.nio.ByteBuffer

/**
 * Isolated abstraction for direct USB hardware audio streaming.
 *
 * Designed to bypass system audio mixers when an authorized, compatible USB Audio Class 2.0
 * DAC is active. When direct streaming is inactive, unavailable, or unpermitted, BitChord
 * cleanly falls back to [com.music.bitchord.playback.audio.PrecisionAudioSink] and AudioTrack.
 */
interface DirectAudioOutput {

    /** Whether direct hardware streaming is active and ready to accept audio frames. */
    val isActive: Boolean

    /** Current hardware probe diagnosis. */
    val probeResult: DirectUsbProbeResult

    /**
     * Attempts to configure the direct USB output stream for the given audio format.
     *
     * @return true if direct stream was successfully opened, false if fallback is required.
     */
    fun configure(
        sampleRate: Int,
        channelCount: Int,
        encoding: PcmEncoding,
    ): Boolean

    /**
     * Writes processed PCM samples directly to the USB endpoint.
     *
     * @param buffer Direct [ByteBuffer] containing audio frames.
     * @return Number of bytes accepted by the hardware endpoint, or -1 on error.
     */
    fun write(buffer: ByteBuffer): Int

    /** Flushes any buffered packets in the direct USB pipeline. */
    fun flush()

    /** Releases the USB interface and device connection. */
    fun release()
}

/**
 * Default safe direct audio output implementation that operates strictly as a probe
 * and fallback coordinator, ensuring zero destabilization of the AudioTrack path.
 */
class DefaultDirectAudioOutput(
    override val probeResult: DirectUsbProbeResult = DirectUsbProbeResult.notPresent(),
) : DirectAudioOutput {

    override val isActive: Boolean = false

    override fun configure(sampleRate: Int, channelCount: Int, encoding: PcmEncoding): Boolean = false

    override fun write(buffer: ByteBuffer): Int = 0

    override fun flush() {}

    override fun release() {}
}
