/*
 * Copyright (C) 2026 BitChord Project
 *
 * Adapted from Mornye (https://github.com/mornyeapp/Mornye),
 * android/app/src/main/kotlin/com/zarz/mornye/UsbBitPerfectPlugin.kt (UsbPcmPlayback),
 * Copyright 2026 zarzet.
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.music.bitchord.playback.audio.bitperfect

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.media.AudioRouting
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer

/**
 * Android 14's bit-perfect mixer: the app asks AudioFlinger to open the USB
 * output at exactly the track's format and stop mixing, resampling and
 * applying volume, then plays one [AudioTrack] pinned to that device.
 *
 * Volume belongs to the DAC in this mode. The track is left at unity and
 * `STREAM_MUSIC` is never touched; phone volume keys may do nothing.
 *
 * The route is re-verified every [ROUTE_CHECK_MS] while audio is written:
 * the track must still resolve to this DAC and the preferred mixer
 * attributes must still be the ones set here. Another app or the system can
 * change either at any time, and a track that silently fell back to the
 * mixer would keep playing under a "bit-perfect" label.
 */
@RequiresApi(34)
internal class MixerTransport private constructor(
    private val manager: AudioManager,
    private val device: AudioDeviceInfo,
    private val mixer: AudioMixerAttributes,
    private val track: AudioTrack,
    override val sampleRate: Int,
    override val channelCount: Int,
    override val outputBits: Int,
    override val bufferSizeFrames: Long,
) : BitPerfectTransport {

    override val driver = BitPerfectDriver.ANDROID_MIXER
    override val deviceName: String = device.productName?.toString().orEmpty().ifBlank { "USB audio" }

    @Volatile private var failure: String? = null
    private var nextRouteCheck = 0L
    private var previousHead = 0L
    private var headWrap = 0L

    private val routing = AudioRouting.OnRoutingChangedListener {
        if (track.routedDevice?.id != device.id) failure = "USB route changed"
    }
    private val mixerListener = AudioManager.OnPreferredMixerAttributesChangedListener { attr, changed, value ->
        if (attr.usage == AudioAttributes.USAGE_MEDIA && changed.id == device.id && value != mixer) {
            failure = "Bit-perfect mixer attributes changed"
        }
    }

    init {
        track.addOnRoutingChangedListener(routing, Handler(Looper.getMainLooper()))
        manager.addOnPreferredMixerAttributesChangedListener(Runnable::run, mixerListener)
    }

    override fun write(buffer: ByteBuffer): Int {
        checkRoute()
        val written = track.write(buffer, buffer.remaining(), AudioTrack.WRITE_NON_BLOCKING)
        if (written < 0) throw BitPerfectTransportException("USB write failed: $written")
        return written
    }

    override fun play() {
        failure?.let { throw BitPerfectTransportException(it) }
        track.play()
        // No samples may be written until the route actually resolves to our DAC.
        if (!verifiedRoute()) {
            track.pause()
            failure = "USB route could not be verified"
            throw BitPerfectTransportException(failure!!)
        }
        nextRouteCheck = SystemClock.elapsedRealtime() + ROUTE_CHECK_MS
    }

    override fun pause() {
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
    }

    override fun flush() {
        pause()
        track.flush()
        previousHead = 0
        headWrap = 0
    }

    override fun stop() {
        if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop()
    }

    override fun playedFrames(): Long {
        val head = track.playbackHeadPosition.toLong() and 0xffffffffL
        if (head < previousHead) headWrap += 1L shl 32
        previousHead = head
        return headWrap + head
    }

    override fun release() {
        runCatching { track.removeOnRoutingChangedListener(routing) }
        runCatching { manager.removeOnPreferredMixerAttributesChangedListener(mixerListener) }
        runCatching { track.release() }
        runCatching { manager.clearPreferredMixerAttributes(ATTRIBUTES, device) }
    }

    private fun checkRoute() {
        failure?.let { throw BitPerfectTransportException(it) }
        val now = SystemClock.elapsedRealtime()
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING || now < nextRouteCheck) return
        if (!verifiedRoute()) {
            failure = "USB route changed"
            throw BitPerfectTransportException(failure!!)
        }
        nextRouteCheck = now + ROUTE_CHECK_MS
    }

    private fun verifiedRoute(): Boolean = track.routedDevice?.id == device.id &&
        manager.getPreferredMixerAttributes(ATTRIBUTES, device) == mixer

    companion object {
        private const val ROUTE_CHECK_MS = 250L

        val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        fun usbDevice(manager: AudioManager): AudioDeviceInfo? =
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }

        /**
         * The format this DAC would open at for the track: its own when offered.
         * Failing that, with [convert] the best it has, by [BitPerfectPcm.rankRates]
         * and [BitPerfectPcm.dacBits]; without it, null.
         */
        fun bestFormat(
            manager: AudioManager,
            device: AudioDeviceInfo,
            rate: Int,
            channels: Int,
            sourceBits: Int,
            convert: Boolean,
        ): AudioMixerAttributes? {
            val mask = channelMask(channels)
            val offered = runCatching { manager.getSupportedMixerAttributes(device) }.getOrDefault(emptyList()).filter {
                it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT &&
                    it.format.channelMask == mask && bits(it.format.encoding) > 0
            }
            val bestRate = if (convert) {
                BitPerfectPcm.rankRates(rate, offered.map { it.format.sampleRate }).firstOrNull() ?: return null
            } else {
                rate
            }
            val atRate = offered.filter { it.format.sampleRate == bestRate }
            val depths = atRate.map { bits(it.format.encoding) }
            val outputBits = (if (convert) BitPerfectPcm.dacBits(sourceBits, depths) else BitPerfectPcm.outputBits(sourceBits, depths))
                ?: return null
            return atRate.first { bits(it.format.encoding) == outputBits }
        }

        /** Opens the DAC at [bestFormat], or explains why not. */
        fun open(manager: AudioManager, rate: Int, channels: Int, sourceBits: Int, convert: Boolean): MixerTransport {
            val usb = usbDevice(manager) ?: throw BitPerfectTransportException("no_usb")
            val selected = bestFormat(manager, usb, rate, channels, sourceBits, convert)
                ?: throw BitPerfectTransportException("unsupported")
            val outputBits = bits(selected.format.encoding)
            val openedRate = selected.format.sampleRate
            if (!manager.setPreferredMixerAttributes(ATTRIBUTES, usb, selected)) {
                throw BitPerfectTransportException("unsupported")
            }
            var track: AudioTrack? = null
            try {
                val mask = channelMask(channels)
                val minimum = AudioTrack.getMinBufferSize(openedRate, mask, selected.format.encoding)
                if (minimum <= 0) throw BitPerfectTransportException("format")
                val frameBytes = channels * (outputBits / 8)
                val bufferBytes = maxOf(minimum * 2, openedRate / 5 * frameBytes)
                track = AudioTrack.Builder()
                    .setAudioAttributes(ATTRIBUTES)
                    .setAudioFormat(selected.format)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(bufferBytes)
                    .build()
                if (track.state != AudioTrack.STATE_INITIALIZED || !track.setPreferredDevice(usb) ||
                    track.sampleRate != openedRate || track.audioFormat != selected.format.encoding ||
                    track.channelCount != channels
                ) {
                    throw BitPerfectTransportException("format")
                }
                // Never change STREAM_MUSIC volume: the DAC owns volume in this mode.
                track.setVolume(1f)
                return MixerTransport(
                    manager = manager,
                    device = usb,
                    mixer = selected,
                    track = track,
                    sampleRate = openedRate,
                    channelCount = channels,
                    outputBits = outputBits,
                    bufferSizeFrames = (track.bufferSizeInFrames).toLong(),
                )
            } catch (error: Exception) {
                runCatching { track?.release() }
                runCatching { manager.clearPreferredMixerAttributes(ATTRIBUTES, usb) }
                throw error as? BitPerfectTransportException ?: BitPerfectTransportException("format", error)
            }
        }

        private fun channelMask(channels: Int): Int =
            if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO

        private fun bits(encoding: Int): Int = when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> 16
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
            AudioFormat.ENCODING_PCM_32BIT -> 32
            else -> 0
        }
    }
}
