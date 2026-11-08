/*
 * Copyright (C) 2026 BitChord Project
 *
 * Adapted in part from Mornye (https://github.com/mornyeapp/Mornye),
 * android/app/src/main/kotlin/com/zarz/mornye/UsbDirectPlayback.kt,
 * Copyright 2026 zarzet.
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.music.bitchord.playback.audio.bitperfect.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log

/**
 * The USB side of the direct driver that is not about streaming: which
 * attached device is a USB audio DAC, Android's permission to open it, its
 * descriptors, and the volume the listener last chose on it.
 *
 * Permission is asked for once, when the mode is on and a DAC is attached;
 * the answer arrives asynchronously, and [onChanged] tells the player to
 * re-decide its output. Descriptors are read once per attachment, because
 * the playback thread asks "could this track go direct?" for every format.
 */
internal object UsbDirectDevices {

    private const val TAG = "UsbDirectDevices"

    private var appContext: Context? = null
    private var permissionAction = ""
    private var requested: Int? = null
    private val descriptors = HashMap<Int, ByteArray>()
    private val volumes = HashMap<Int, Double>()

    /** A DAC was attached, detached or granted: whatever is playing should decide again. */
    @Volatile var onChanged: (() -> Unit)? = null

    /** Told when a device goes away, so an open stream on it stops at once. */
    @Volatile var onDetached: ((Int) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            @Suppress("DEPRECATION")
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE) ?: return
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    synchronized(this@UsbDirectDevices) {
                        descriptors.remove(device.deviceId)
                        volumes.remove(device.deviceId)
                        if (requested == device.deviceId) requested = null
                    }
                    onDetached?.invoke(device.deviceId)
                    onChanged?.invoke()
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> onChanged?.invoke()
                permissionAction -> {
                    synchronized(this@UsbDirectDevices) { requested = null }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    Log.i(TAG, "USB permission for ${device.productName}: $granted")
                    if (granted) onChanged?.invoke()
                }
            }
        }
    }

    @Synchronized
    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        permissionAction = "${app.packageName}.USB_AUDIO_PERMISSION"
        val filter = IntentFilter(permissionAction).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            app.registerReceiver(receiver, filter)
        }
    }

    fun manager(context: Context): UsbManager? = context.getSystemService(UsbManager::class.java)

    /** The first attached device with a USB Audio streaming interface, by device id. */
    fun audioDevice(manager: UsbManager): UsbDevice? =
        runCatching { manager.deviceList.values }.getOrDefault(emptyList()).sortedBy { it.deviceId }.firstOrNull { device ->
            (0 until device.interfaceCount).any {
                val iface = device.getInterface(it)
                iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO && iface.interfaceSubclass == 2
            }
        }

    /** Asks Android for permission to the attached DAC, once per attachment. */
    @Synchronized
    fun requestPermission(context: Context) {
        val manager = manager(context) ?: return
        val device = audioDevice(manager) ?: return
        if (manager.hasPermission(device) || requested == device.deviceId) return
        requested = device.deviceId
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val intent = PendingIntent.getBroadcast(
            context,
            device.deviceId,
            Intent(permissionAction).setPackage(context.packageName),
            flags,
        )
        runCatching { manager.requestPermission(device, intent) }
            .onFailure { requested = null; Log.w(TAG, "USB permission request failed", it) }
    }

    /** The DAC's descriptors, read once per attachment; null without permission. */
    @Synchronized
    fun rawDescriptors(manager: UsbManager, device: UsbDevice): ByteArray? {
        descriptors[device.deviceId]?.let { return it }
        if (!manager.hasPermission(device)) return null
        val connection = runCatching { manager.openDevice(device) }.getOrNull() ?: return null
        return try {
            connection.rawDescriptors?.also { descriptors[device.deviceId] = it }
        } finally {
            connection.close()
        }
    }

    @Synchronized
    fun rememberedVolume(deviceId: Int): Double? = volumes[deviceId]

    @Synchronized
    fun rememberVolume(deviceId: Int, db: Double) {
        volumes[deviceId] = db
    }
}
