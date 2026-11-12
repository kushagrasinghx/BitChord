/*
 * Copyright (C) 2026 BitChord Project
 *
 * Adapted from Mornye (https://github.com/mornyeapp/Mornye),
 * android/app/src/main/kotlin/com/zarz/mornye/UsbDirectPlayback.kt,
 * Copyright 2026 zarzet.
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.music.bitchord.playback.audio.bitperfect.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.music.bitchord.playback.audio.bitperfect.BitPerfectDriver
import com.music.bitchord.playback.audio.bitperfect.BitPerfectTransport
import com.music.bitchord.playback.audio.bitperfect.BitPerfectTransportException
import java.nio.ByteBuffer

/** JNI over `usb_transport.cpp`. Handles are opaque ids, never pointers. */
internal object UsbDirectNative {
    init {
        System.loadLibrary("bitchord_usb")
    }

    /** [candidates] holds [CANDIDATE_INTS] values per candidate, in preference order. */
    external fun open(fd: Int, candidates: IntArray): Long
    external fun chosen(handle: Long): Int
    external fun capacity(handle: Long): Int
    external fun write(handle: Long, data: ByteBuffer, offset: Int, count: Int): Int
    external fun setPlaying(handle: Long, playing: Boolean)
    external fun flush(handle: Long)
    external fun frames(handle: Long): Long
    external fun control(handle: Long, input: Boolean, request: Int, value: Int, index: Int, data: ByteArray): ByteArray
    external fun close(handle: Long)

    const val CANDIDATE_INTS = 17

    fun encode(plans: List<UsbDescriptors.Plan>): IntArray = plans.flatMap { plan ->
        val a = plan.alternate
        val f = plan.format
        listOf(
            a.control, a.interface_, a.alternate, if (a.uac2) 1 else 0, a.clock,
            plan.data.address, plan.data.attributes, plan.data.maxPacket, plan.data.interval,
            if (plan.data.rateControl) 1 else 0,
            plan.feedback?.address ?: -1, plan.feedback?.maxPacket ?: 0,
            f.sampleRate, f.channels, f.subslot, f.bits, f.encoding.code,
        )
    }.toIntArray()
}

/**
 * The direct USB driver: Android grants permission and opens the device, and
 * a native worker drives its isochronous endpoint with libusb, detaching
 * Android's own USB audio driver for as long as the stream is open.
 *
 * Volume is the DAC's Feature Unit, never the samples. Before the first
 * transfer every controlled channel is brought down to at most -40 dB, and a
 * level the listener chose earlier on the same DAC is put back only as far as
 * the DAC's own knob now allows. On release the DAC is returned to the level
 * it had before the stream opened. A DAC with no usable volume control plays
 * only if the listener has explicitly allowed fixed-volume output.
 *
 * A failure never redirects to the phone speaker from here: the transport
 * stops, and the sink's owner decides what plays next.
 */
internal class UsbDirectTransport private constructor(
    private val connection: UsbDeviceConnection,
    private var handle: Long,
    val usbDeviceId: Int,
    override val deviceName: String,
    private val format: UsbDescriptors.OutputFormat,
    private val volumeControl: UsbVolume.Volume?,
) : BitPerfectTransport {

    override val driver = BitPerfectDriver.USB_DIRECT
    override val sampleRate = format.sampleRate
    override val channelCount = format.channels

    /** Samples are written left-aligned in the endpoint's container. */
    override val outputBits = format.subslot * 8
    override val bufferSizeFrames: Long = UsbDirectNative.capacity(handle).toLong()

    @Volatile private var failure: String? = null
    private var lastFrames = 0L

    private val control = UsbControl { input, request, value, index, data ->
        val reply = UsbDirectNative.control(handle, input, request, value, index, data)
        if (input) reply.copyInto(data)
    }

    val volume: UsbVolume.Snapshot? get() = volumeControl?.snapshot

    /** Called off the playback thread; the native worker serializes it with streaming. */
    fun setVolume(db: Double): UsbVolume.Snapshot {
        val volume = volumeControl ?: throw IllegalStateException("USB hardware volume unavailable")
        return synchronized(volume) { volume.set(control, db) }
    }

    fun markDetached() {
        failure = "USB disconnected"
    }

    override fun write(buffer: ByteBuffer): Int {
        val count = call { UsbDirectNative.write(handle, buffer, buffer.position(), minOf(buffer.remaining(), MAX_WRITE)) }
        buffer.position(buffer.position() + count)
        return count
    }

    override fun play() = call { UsbDirectNative.setPlaying(handle, true) }

    override fun pause() = call { UsbDirectNative.setPlaying(handle, false) }

    override fun flush() {
        call { UsbDirectNative.flush(handle) }
        lastFrames = 0
    }

    /** The queue runs dry and the worker pads with silence that is not counted as played. */
    override fun stop() = Unit

    override fun playedFrames(): Long {
        if (handle == 0L || failure != null) return lastFrames
        lastFrames = maxOf(lastFrames, runCatching { UsbDirectNative.frames(handle) }.getOrDefault(lastFrames))
        return lastFrames
    }

    override fun release() {
        // Leave the DAC at the volume it had before this stream lowered it;
        // the next owner, Android's driver or another app, expects that level.
        // Pointless once it is unplugged, and a closed handle cannot be asked.
        if (handle != 0L && failure == null) {
            volumeControl?.let { volume -> runCatching { synchronized(volume) { volume.restore(control) } } }
        }
        // The worker joins (cancelling and acknowledging every transfer, then
        // releasing the interfaces) before Android closes the descriptor.
        if (handle != 0L) UsbDirectNative.close(handle)
        handle = 0
        runCatching { connection.close() }
    }

    private inline fun <T> call(block: () -> T): T {
        failure?.let { throw BitPerfectTransportException(it) }
        return try {
            block()
        } catch (error: IllegalStateException) {
            failure = error.message ?: "USB output failed"
            throw BitPerfectTransportException(failure!!, error)
        }
    }

    companion object {
        private const val MAX_WRITE = 256 * 1024

        fun open(
            manager: UsbManager,
            device: UsbDevice,
            rate: Int,
            channels: Int,
            sourceBits: Int,
            allowFixedVolume: Boolean,
            rememberedDb: Double?,
        ): UsbDirectTransport {
            val connection = manager.openDevice(device) ?: throw BitPerfectTransportException("cannot_open_usb")
            var handle = 0L
            try {
                val raw = connection.rawDescriptors ?: throw BitPerfectTransportException("no_descriptors")
                val plans = plans(raw, rate, channels, sourceBits)
                if (plans.isEmpty()) throw BitPerfectTransportException("format")
                handle = try {
                    UsbDirectNative.open(connection.fileDescriptor, UsbDirectNative.encode(plans))
                } catch (error: IllegalStateException) {
                    throw BitPerfectTransportException("unsupported", error)
                }
                val plan = plans[UsbDirectNative.chosen(handle)]
                val opened = handle
                val control = UsbControl { input, request, value, index, data ->
                    val reply = UsbDirectNative.control(opened, input, request, value, index, data)
                    if (input) reply.copyInto(data)
                }
                // Startup attenuation happens inside Volume.open, before any audio.
                val volume = UsbVolume.feature(raw, plan.alternate.control, plan.alternate.terminal, channels)
                    ?.let { feature -> runCatching { UsbVolume.Volume.open(control, feature) }.getOrNull() }
                if (volume == null && !allowFixedVolume) throw BitPerfectTransportException("volume_unavailable")
                if (volume != null && rememberedDb != null) {
                    runCatching { volume.set(control, minOf(rememberedDb, volume.snapshot.restoreLimitDb)) }
                }
                return UsbDirectTransport(
                    connection = connection,
                    handle = handle,
                    usbDeviceId = device.deviceId,
                    deviceName = device.productName?.takeIf { it.isNotBlank() } ?: "USB audio",
                    format = plan.format,
                    volumeControl = volume,
                )
            } catch (error: Exception) {
                if (handle != 0L) UsbDirectNative.close(handle)
                runCatching { connection.close() }
                throw error as? BitPerfectTransportException ?: BitPerfectTransportException("unsupported", error)
            }
        }

        /** Every way the attached DAC could carry this stream unchanged, best first. */
        fun plans(raw: ByteArray, rate: Int, channels: Int, sourceBits: Int): List<UsbDescriptors.Plan> {
            val device = try {
                UsbDescriptors.parse(raw)
            } catch (_: UsbDescriptors.MalformedDescriptorException) {
                return emptyList()
            }
            return UsbDescriptors.formats(device, rate, channels, sourceBits, dsd = false, dop = false)
                .mapNotNull { (alternate, format) -> UsbDescriptors.plan(alternate, format) }
                .take(32)
        }
    }
}
