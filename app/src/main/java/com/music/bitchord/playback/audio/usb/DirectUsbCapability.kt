/*
 * Copyright (C) 2026 BitChord Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Architectural concepts for USB descriptor parsing adapted from:
 * decent-player (https://github.com/Ma145/decent-player)
 * Copyright (c) 2026 Ma145 (MIT License)
 */

package com.music.bitchord.playback.audio.usb

/**
 * Result of a dynamic runtime probe of USB audio devices connected to the system.
 */
data class DirectUsbProbeResult(
    /** Whether direct USB audio streaming is currently viable on the active hardware. */
    val isViable: Boolean,
    /** USB product name, or null if no USB device is present. */
    val productName: String?,
    /** USB Vendor ID (VID). */
    val vendorId: Int = 0,
    /** USB Product ID (PID). */
    val productId: Int = 0,
    /** USB Audio Class version: 1 for UAC1, 2 for UAC2, 0 if not audio class. */
    val uacVersion: Int = 0,
    /** Whether Android USB host permission has been granted for this device. */
    val hasPermission: Boolean = false,
    /** Address of the isochronous OUT endpoint for streaming audio. */
    val endpointOutAddress: Int = -1,
    /** Maximum packet size in bytes for the isochronous OUT endpoint. */
    val maxPacketSize: Int = 0,
    /** Number of alternate settings found on the AudioStreaming interface. */
    val altSettingCount: Int = 0,
    /** Whether the AudioStreaming interface was successfully claimed. */
    val isInterfaceClaimed: Boolean = false,
    /** Evaluated supported PCM bit depths based on endpoint bandwidth and descriptors. */
    val supportedBitDepths: List<Int> = emptyList(),
    /** Maximum sample rate supported by the endpoint packet budget. */
    val maxCalculatedSampleRate: Int = 0,
    /** Authoritative diagnosis explaining why direct USB is unavailable or fallback occurred. */
    val diagnosticReason: String,
) {
    companion object {
        fun notPresent(reason: String = "No USB audio device connected"): DirectUsbProbeResult =
            DirectUsbProbeResult(
                isViable = false,
                productName = null,
                diagnosticReason = reason,
            )
    }
}
