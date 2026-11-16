/*
 * Copyright (C) 2026 BitChord Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package com.music.bitchord.playback.audio.bitperfect.usb

/**
 * A DAC's stable persisted-volume identity. Only a non-blank serial number is
 * accepted: model/vendor fallbacks could apply one DAC's level to another.
 */
internal object UsbVolumeIdentity {
    fun stableKey(serialNumber: String?): String? =
        serialNumber?.trim()?.takeIf { it.isNotEmpty() }?.let { "serial:$it" }
}
