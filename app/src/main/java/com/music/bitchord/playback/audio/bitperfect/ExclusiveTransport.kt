/*
 * Copyright (C) 2026 BitChord Project
 *
 * Adapted from Mornye (https://github.com/mornyeapp/Mornye),
 * android/app/src/main/kotlin/com/zarz/mornye/HiResPlayback.kt and NativeAudio.kt,
 * Copyright 2026 zarzet.
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.music.bitchord.playback.audio.bitperfect

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import java.nio.ByteBuffer

/** JNI over `exclusive_output.cpp`. Handles are opaque ids, never pointers. */
internal object ExclusiveNative {
    init {
        System.loadLibrary("bitchord_exclusive")
    }

    external fun open(rate: Int, channels: Int, bits: Int, device: Int): Long
    external fun bits(handle: Long): Int
    external fun device(handle: Long): Int
    external fun capacity(handle: Long): Int
    external fun write(handle: Long, data: ByteBuffer, offset: Int, count: Int): Int
    external fun start(handle: Long)
    external fun pause(handle: Long)
    external fun flush(handle: Long)
    external fun frames(handle: Long): Long
    external fun close(handle: Long)
}

/**
 * Exact-rate PCM through an AAudio exclusive stream, for a player whose DAC is
 * built in: a DAP's headphone output or line out. The native side opens the
 * stream at the track's own rate and channel count with conversion disabled,
 * and accepts it only if Android granted exclusive mode exactly as asked.
 *
 * The output is pinned to the wired headphone device when there is one, as
 * Mornye does, so a DAP whose default route is its speaker still plays
 * through its DAC.
 */
internal class ExclusiveTransport private constructor(
    private val manager: AudioManager,
    private var handle: Long,
    override val sampleRate: Int,
    override val channelCount: Int,
    override val outputBits: Int,
    override val deviceName: String,
    private val deviceId: Int,
) : BitPerfectTransport {

    override val driver = BitPerfectDriver.AAUDIO_EXCLUSIVE
    override val bufferSizeFrames: Long = ExclusiveNative.capacity(handle).toLong()

    @Volatile private var failure: String? = null

    /** AAudio reports no timestamp while paused; the clock must not run backwards meanwhile. */
    private var lastFrames = 0L

    private val route = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            if (removed.any { it.id == deviceId }) failure = "AAudio device disconnected"
        }
    }

    init {
        manager.registerAudioDeviceCallback(route, Handler(Looper.getMainLooper()))
    }

    override fun write(buffer: ByteBuffer): Int {
        val count = call { ExclusiveNative.write(handle, buffer, buffer.position(), minOf(buffer.remaining(), MAX_WRITE)) }
        buffer.position(buffer.position() + count)
        return count
    }

    override fun play() = call { ExclusiveNative.start(handle) }

    override fun pause() = call { ExclusiveNative.pause(handle) }

    override fun flush() {
        call { ExclusiveNative.flush(handle) }
        lastFrames = 0
    }

    /** The ring simply runs dry; the callback fills with silence that the clock does not count. */
    override fun stop() = Unit

    override fun playedFrames(): Long {
        if (handle == 0L || failure != null) return lastFrames
        val frames = runCatching { ExclusiveNative.frames(handle) }.getOrDefault(lastFrames)
        lastFrames = maxOf(lastFrames, frames)
        return lastFrames
    }

    override fun release() {
        runCatching { manager.unregisterAudioDeviceCallback(route) }
        if (handle != 0L) ExclusiveNative.close(handle)
        handle = 0
    }

    private inline fun <T> call(block: () -> T): T {
        failure?.let { throw BitPerfectTransportException(it) }
        return try {
            block()
        } catch (error: IllegalStateException) {
            failure = error.message ?: "AAudio output failed"
            throw BitPerfectTransportException(failure!!, error)
        }
    }

    companion object {
        /** The native side refuses larger single writes. */
        private const val MAX_WRITE = 262_144

        fun open(manager: AudioManager, rate: Int, channels: Int, sourceBits: Int): ExclusiveTransport {
            val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val wired = outputs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
            }
            val handle = try {
                ExclusiveNative.open(rate, channels, sourceBits, wired?.id ?: 0)
            } catch (error: IllegalStateException) {
                throw BitPerfectTransportException("exclusive_unavailable", error)
            } catch (error: UnsatisfiedLinkError) {
                throw BitPerfectTransportException("exclusive_unavailable", error)
            }
            try {
                val device = ExclusiveNative.device(handle)
                val name = outputs.firstOrNull { it.id == device }?.productName?.toString()
                    ?.takeIf { it.isNotBlank() } ?: "AAudio"
                return ExclusiveTransport(
                    manager = manager,
                    handle = handle,
                    sampleRate = rate,
                    channelCount = channels,
                    outputBits = ExclusiveNative.bits(handle),
                    deviceName = name,
                    deviceId = device,
                )
            } catch (error: Exception) {
                ExclusiveNative.close(handle)
                throw BitPerfectTransportException("exclusive_unavailable", error)
            }
        }
    }
}
