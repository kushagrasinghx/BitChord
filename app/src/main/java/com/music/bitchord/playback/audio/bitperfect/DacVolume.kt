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

import com.music.bitchord.playback.audio.bitperfect.usb.UsbVolume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The DAC's hardware volume as one 0..1 level, for everything that is not a
 * settings slider in dB: the player's volume bar, the volume keys, the
 * system's remote-volume panel and the volume HUD.
 *
 * While direct USB output is open this *is* the app's volume: Android's
 * music stream no longer reaches the DAC, and nothing upstream may scale a
 * sample. [level] is null whenever there is no such DAC, and every caller
 * falls back to the system volume then.
 *
 * Mornye maps its slider linearly across the DAC's whole dB range. Many DACs
 * reach -127 dB, which leaves the bottom half of that travel inaudible, so
 * here the travel starts at [FLOOR_DB] and only its very bottom is the DAC's
 * minimum. dB is already logarithmic, so the rest stays linear in dB.
 *
 * The level moves the moment it is asked to, and is corrected to what the
 * DAC actually settled on (it rounds down to its own steps) once the control
 * transfer has been read back.
 */
internal object DacVolume {

    /** Key presses from silent to full, as on iOS. */
    const val STEPS = 16

    private const val FLOOR_DB = -80.0

    private val _level = MutableStateFlow<Float?>(null)
    val level: StateFlow<Float?> = _level.asStateFlow()

    private var snapshot: UsbVolume.Snapshot? = null

    /** Called by [BitPerfectOutput] whenever the DAC reports its volume, or stops having one. */
    @Synchronized
    internal fun onSnapshot(value: UsbVolume.Snapshot?) {
        snapshot = value
        _level.value = value?.takeIf { it.available }?.let(::fractionOf)
    }

    /** Sets the DAC to [fraction] of its travel. */
    @Synchronized
    fun set(fraction: Float) {
        val volume = snapshot?.takeIf { it.available } ?: return
        val target = fraction.coerceIn(0f, 1f)
        _level.value = target
        BitPerfectOutput.setHardwareVolume(dbOf(volume, target))
    }

    /**
     * One key press up (+1) or down (-1). Returns false when the level was
     * already at that end, which is what the HUD stretches against.
     */
    @Synchronized
    fun step(direction: Int): Boolean {
        val current = _level.value ?: return false
        val next = ((Math.round(current * STEPS) + direction).coerceIn(0, STEPS)).toFloat() / STEPS
        if (next == current) return false
        set(next)
        return true
    }

    internal fun fractionOf(volume: UsbVolume.Snapshot): Float {
        val floor = maxOf(volume.minDb, FLOOR_DB)
        if (volume.maxDb <= floor || volume.currentDb <= floor) return 0f
        return ((volume.currentDb - floor) / (volume.maxDb - floor)).toFloat().coerceIn(0f, 1f)
    }

    internal fun dbOf(volume: UsbVolume.Snapshot, fraction: Float): Double {
        if (fraction <= 0f) return volume.minDb
        val floor = maxOf(volume.minDb, FLOOR_DB)
        return floor + fraction * (volume.maxDb - floor)
    }
}
