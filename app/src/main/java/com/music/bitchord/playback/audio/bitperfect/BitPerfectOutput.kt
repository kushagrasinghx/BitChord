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

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.BitPerfectMode
import com.music.bitchord.playback.audio.bitperfect.usb.UsbDirectDevices
import com.music.bitchord.playback.audio.bitperfect.usb.UsbDirectTransport
import com.music.bitchord.playback.audio.bitperfect.usb.UsbVolume
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a [BitPerfectAudioSink] asks of [BitPerfectOutput], kept narrow so a test can stand in for it. */
internal interface BitPerfectOutputs {
    fun canOpen(sampleRate: Int, channels: Int, sourceBits: Int, requester: Any): Boolean

    fun open(sampleRate: Int, channels: Int, sourceBits: Int, requester: Any): BitPerfectTransport

    fun release(requester: Any)

    fun markFailed(sampleRate: Int, channels: Int, sourceBits: Int, reason: String)

    fun publish(status: BitPerfectOutput.Status)

    fun publishIdle()

    /** See [BitPerfectOutput.normalOutputPending]. */
    fun normalOutputPending(): Boolean
}

/**
 * Process-wide owner of the one exclusive output.
 *
 * Both crossfade players build a [BitPerfectAudioSink], but a DAC opened
 * bit-perfect has room for a single stream: Android's bit-perfect mixer
 * mutes everything else, and a claimed USB interface belongs to one handle.
 * Whichever sink opens first holds it until it releases; the other is told
 * no and stays on the ordinary path.
 *
 * A route that refused to open is remembered until the attached device, the
 * selected mode or [AppSettings.convertForLimitedDac] changes, so a track that
 * keeps failing is not retried on every buffer.
 */
internal object BitPerfectOutput {

    private const val TAG = "BitPerfectOutput"
    private const val NO_DEVICE = -1
    private const val DIRECT_HANDBACK_MS = 3000L

    data class Status(
        val active: Boolean = false,
        val driver: String? = null,
        val deviceName: String? = null,
        val sampleRateHz: Int = 0,
        val outputBits: Int = 0,
        val sourceBits: Int = 0,
        val sourceRateHz: Int = 0,
        /** False once any sample of the current track had to be rounded. */
        val exact: Boolean = true,
        /**
         * The DAC can't take this track as it is, so it is being converted to
         * the best format the DAC has. Never [exact]: check this one first.
         */
        val dacLimited: Boolean = false,
        /** Why the last attempt fell back to normal output, if it did. */
        val fallbackReason: String? = null,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _ready = MutableStateFlow(false)

    /**
     * Whether the selected mode has its device attached, so lossless tracks
     * will be going to the exclusive output: a USB DAC for either USB mode,
     * wired headphones for the built-in DAC. What settings fade and what
     * stops asking for Dolby Atmos follow this, not the bare mode — with the
     * mode on and nothing plugged in, everything plays as normal and should
     * look it.
     */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var watching = false

    /** Starts following the attached devices; [refreshReady] covers the mode and USB. */
    @Synchronized
    fun watch(context: Context) {
        if (watching) return
        watching = true
        val app = context.applicationContext
        app.getSystemService(AudioManager::class.java)?.registerAudioDeviceCallback(
            object : android.media.AudioDeviceCallback() {
                override fun onAudioDevicesAdded(added: Array<out android.media.AudioDeviceInfo>) = refreshReady(app)
                override fun onAudioDevicesRemoved(removed: Array<out android.media.AudioDeviceInfo>) = refreshReady(app)
            },
            android.os.Handler(android.os.Looper.getMainLooper()),
        )
        refreshReady(app)
    }

    fun refreshReady(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java)
        val attached = when (AppSettings.bitPerfectMode.value) {
            BitPerfectMode.OFF -> false
            BitPerfectMode.USB_ANDROID ->
                Build.VERSION.SDK_INT >= 34 && audio?.let(MixerTransport::usbDevice) != null
            // Android's own driver lets go of the DAC while this one streams,
            // so it is looked for as a USB device, not an audio output.
            BitPerfectMode.USB_DIRECT ->
                UsbDirectDevices.manager(context)?.let(UsbDirectDevices::audioDevice) != null
            BitPerfectMode.DAP_EXCLUSIVE -> Build.VERSION.SDK_INT >= 27 &&
                audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.any {
                    it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET
                } == true
        }
        this.attached = attached
        recomputeReady()
    }

    @Volatile private var attached = false

    /**
     * Re-derived whenever any of its inputs moves — the mode, the attached
     * device, or the output opening and closing — rather than only on device
     * events. Computed once at the moment the mode went off, it caught the
     * stream that was still closing and stayed true with the mode off.
     */
    private fun recomputeReady() {
        _ready.value = AppSettings.bitPerfectMode.value != BitPerfectMode.OFF &&
            (attached || _status.value.active)
    }

    fun forContext(context: Context): BitPerfectOutputs {
        val app = context.applicationContext
        return object : BitPerfectOutputs {
            override fun canOpen(sampleRate: Int, channels: Int, sourceBits: Int, requester: Any) =
                canOpen(app, sampleRate, channels, sourceBits, requester)

            override fun open(sampleRate: Int, channels: Int, sourceBits: Int, requester: Any) =
                open(app, sampleRate, channels, sourceBits, requester)

            override fun release(requester: Any) = this@BitPerfectOutput.release(requester)

            override fun markFailed(sampleRate: Int, channels: Int, sourceBits: Int, reason: String) =
                markFailed(app, sampleRate, channels, sourceBits, reason)

            override fun publish(status: Status) = this@BitPerfectOutput.publish(status)

            override fun publishIdle() = this@BitPerfectOutput.publishIdle()

            override fun normalOutputPending() = normalOutputPending(app)
        }
    }

    /** The DAC's Feature Unit volume while direct USB output is open; null otherwise. */
    private val _hardwareVolume = MutableStateFlow<UsbVolume.Snapshot?>(null)
    val hardwareVolume: StateFlow<UsbVolume.Snapshot?> = _hardwareVolume.asStateFlow()
    private val pendingVolume = AtomicReference<Double?>(null)
    private val volumeExecutor = Executors.newSingleThreadExecutor { Thread(it, "BitChord-USB-volume") }

    init {
        // A detached DAC stops its stream at once rather than at the next write.
        UsbDirectDevices.onDetached = { deviceId ->
            synchronized(this) { (active as? UsbDirectTransport)?.takeIf { it.usbDeviceId == deviceId } }
                ?.markDetached()
        }
    }

    private var owner: Any? = null
    private var active: BitPerfectTransport? = null

    @Volatile private var directReleasedAtMs = 0L

    /**
     * Whether a track meant for normal output should wait: the direct driver
     * has just handed the DAC back, and Android's own USB audio driver has
     * not re-attached to it yet. Until it does the DAC is not an audio output
     * at all, and a track started now would play from the phone's speaker —
     * a local Dolby or multichannel file following a lossless one, say.
     * Waits at most [DIRECT_HANDBACK_MS]; a DAC that never comes back (it was
     * unplugged) leaves normal output to Android.
     */
    fun normalOutputPending(context: Context): Boolean {
        val releasedAt = directReleasedAtMs
        if (releasedAt == 0L) return false
        if (android.os.SystemClock.elapsedRealtime() - releasedAt > DIRECT_HANDBACK_MS) {
            directReleasedAtMs = 0L
            return false
        }
        val usbOutput = context.getSystemService(AudioManager::class.java)
            ?.let(MixerTransport::usbDevice) != null
        if (usbOutput) directReleasedAtMs = 0L
        return !usbOutput
    }
    private val failed = HashSet<String>()
    private var failedFor: String? = null

    /** Whether a sink asking for this stream now could get the exclusive output. */
    @Synchronized
    fun canOpen(context: Context, sampleRate: Int, channels: Int, sourceBits: Int, requester: Any): Boolean {
        if (owner != null && owner !== requester) return false
        val mode = AppSettings.bitPerfectMode.value
        if (mode == BitPerfectMode.OFF) return false
        val manager = context.getSystemService(AudioManager::class.java) ?: return false
        return when (mode) {
            BitPerfectMode.OFF -> false
            BitPerfectMode.USB_ANDROID -> {
                if (Build.VERSION.SDK_INT < 34) return false
                val device = MixerTransport.usbDevice(manager) ?: return false
                if (key(mode, device.id, sampleRate, channels, sourceBits) in currentFailures(mode, device.id)) {
                    return false
                }
                MixerTransport.bestFormat(manager, device, sampleRate, channels, sourceBits, AppSettings.convertForLimitedDac.value) != null
            }
            // Nothing short of opening a stream says whether Android will grant
            // exclusive mode, so the first track of each format finds out.
            BitPerfectMode.DAP_EXCLUSIVE -> Build.VERSION.SDK_INT >= 27 &&
                key(mode, NO_DEVICE, sampleRate, channels, sourceBits) !in currentFailures(mode, NO_DEVICE)
            // Permission is asked for by the service when the mode is chosen or a
            // DAC is attached; until it is granted, tracks use normal output.
            BitPerfectMode.USB_DIRECT -> {
                val usb = UsbDirectDevices.manager(context) ?: return false
                val device = UsbDirectDevices.audioDevice(usb) ?: return false
                if (!usb.hasPermission(device)) return false
                if (key(mode, device.deviceId, sampleRate, channels, sourceBits) in currentFailures(mode, device.deviceId)) {
                    return false
                }
                val raw = UsbDirectDevices.rawDescriptors(usb, device) ?: return false
                UsbDirectTransport.plans(raw, sampleRate, channels, sourceBits, AppSettings.convertForLimitedDac.value).isNotEmpty()
            }
        }
    }

    /** Opens the exclusive output for [requester], or throws with the reason. */
    @Synchronized
    fun open(
        context: Context,
        sampleRate: Int,
        channels: Int,
        sourceBits: Int,
        requester: Any,
    ): BitPerfectTransport {
        if (owner != null && owner !== requester) throw BitPerfectTransportException("busy")
        val manager = context.getSystemService(AudioManager::class.java)
            ?: throw BitPerfectTransportException("no_audio_manager")
        val mode = AppSettings.bitPerfectMode.value
        val convert = AppSettings.convertForLimitedDac.value
        val transport = when (mode) {
            BitPerfectMode.OFF -> throw BitPerfectTransportException("off")
            BitPerfectMode.USB_ANDROID -> {
                if (Build.VERSION.SDK_INT < 34) throw BitPerfectTransportException("android_version")
                MixerTransport.open(manager, sampleRate, channels, sourceBits, convert)
            }
            BitPerfectMode.DAP_EXCLUSIVE -> {
                if (Build.VERSION.SDK_INT < 27) throw BitPerfectTransportException("android_version")
                ExclusiveTransport.open(manager, sampleRate, channels, sourceBits, convert)
            }
            BitPerfectMode.USB_DIRECT -> {
                val usb = UsbDirectDevices.manager(context) ?: throw BitPerfectTransportException("no_usb")
                val device = UsbDirectDevices.audioDevice(usb) ?: throw BitPerfectTransportException("no_usb")
                if (!usb.hasPermission(device)) throw BitPerfectTransportException("permission_denied")
                UsbDirectTransport.open(
                    manager = usb,
                    device = device,
                    rate = sampleRate,
                    channels = channels,
                    sourceBits = sourceBits,
                    convert = convert,
                    allowFixedVolume = AppSettings.usbAllowFixedVolume.value,
                    rememberedDb = UsbDirectDevices.rememberedVolume(device.deviceId),
                )
            }
        }
        owner = requester
        active = transport
        publishVolume((transport as? UsbDirectTransport)?.volume)
        return transport
    }

    /**
     * A newer request still queued keeps [DacVolume]'s level where it was
     * dragged to; only the last one's read-back is allowed to move it.
     */
    private fun publishVolume(snapshot: UsbVolume.Snapshot?) {
        _hardwareVolume.value = snapshot
        DacVolume.onSnapshot(snapshot)
    }

    @Synchronized
    fun release(requester: Any) {
        if (owner !== requester) return
        if (active is UsbDirectTransport) directReleasedAtMs = android.os.SystemClock.elapsedRealtime()
        owner = null
        active = null
        publishVolume(null)
    }

    /**
     * Sets the DAC's own volume while direct USB output is open. Requests
     * arrive as fast as a slider moves; only the latest pending one is sent.
     */
    fun setHardwareVolume(db: Double) {
        if (pendingVolume.getAndSet(db) != null) return
        volumeExecutor.execute {
            val target = pendingVolume.getAndSet(null) ?: return@execute
            val transport = synchronized(this) { active as? UsbDirectTransport } ?: return@execute
            runCatching { transport.setVolume(target) }
                .onSuccess { snapshot ->
                    UsbDirectDevices.rememberVolume(transport.usbDeviceId, snapshot.currentDb)
                    if (pendingVolume.get() == null) publishVolume(snapshot) else _hardwareVolume.value = snapshot
                }
                .onFailure { Log.w(TAG, "USB volume change failed", it) }
        }
    }

    /** Records that this stream could not be opened on the attached device. */
    @Synchronized
    fun markFailed(context: Context, sampleRate: Int, channels: Int, sourceBits: Int, reason: String) {
        val mode = AppSettings.bitPerfectMode.value
        val deviceId = when (mode) {
            BitPerfectMode.USB_ANDROID ->
                context.getSystemService(AudioManager::class.java)?.let(MixerTransport::usbDevice)?.id ?: NO_DEVICE
            BitPerfectMode.USB_DIRECT ->
                UsbDirectDevices.manager(context)?.let(UsbDirectDevices::audioDevice)?.deviceId ?: NO_DEVICE
            else -> NO_DEVICE
        }
        currentFailures(mode, deviceId).add(key(mode, deviceId, sampleRate, channels, sourceBits))
        Log.i(TAG, "Bit-perfect output unavailable ($reason) for $sampleRate Hz/$channels ch/$sourceBits-bit")
        _status.value = Status(fallbackReason = reason)
        recomputeReady()
    }

    fun publish(status: Status) {
        _status.value = status
        recomputeReady()
    }

    /** Clears an active status, leaving a fallback reason in place for the settings row. */
    fun publishIdle() {
        if (_status.value.active) _status.value = Status()
        recomputeReady()
    }

    private fun currentFailures(mode: BitPerfectMode, deviceId: Int): MutableSet<String> {
        val scope = "$mode/$deviceId/${AppSettings.convertForLimitedDac.value}"
        if (failedFor != scope) {
            failed.clear()
            failedFor = scope
        }
        return failed
    }

    private fun key(mode: BitPerfectMode, deviceId: Int, rate: Int, channels: Int, bits: Int) =
        "$mode/$deviceId/$rate/$channels/$bits"
}
