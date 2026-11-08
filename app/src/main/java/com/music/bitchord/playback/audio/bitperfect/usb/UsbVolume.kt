/*
 * Copyright (C) 2026 BitChord Project
 *
 * Adapted from Mornye (https://github.com/mornyeapp/Mornye),
 * rust_backend/crates/mobile/src/usb_audio/volume.rs,
 * Copyright 2026 zarzet.
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.music.bitchord.playback.audio.bitperfect.usb

import kotlin.math.floor

/** A class-specific control request on the device's default pipe. */
internal fun interface UsbControl {
    /** Fills [data] for an IN request, sends it for an OUT one; throws when the device refuses. */
    fun transfer(input: Boolean, request: Int, value: Int, index: Int, data: ByteArray)
}

/**
 * USB Audio Feature Unit volume: the DAC's own attenuator, set in signed
 * 1/256 dB. This is how direct USB output gets a volume at all without
 * scaling a single sample.
 */
internal object UsbVolume {

    data class Feature(val unit: Int, val interface_: Int, val uac2: Boolean, val channels: List<Int>)

    data class Snapshot(
        val available: Boolean = false,
        val minDb: Double = 0.0,
        val maxDb: Double = 0.0,
        val currentDb: Double = 0.0,
        /**
         * The quietest channel before startup attenuation; restoring an earlier
         * setting must respect a DAC volume knob that was turned down since.
         */
        val restoreLimitDb: Double = 0.0,
    )

    /**
     * Follows only an unambiguous playback terminal chain. Never changes a
     * capture Feature Unit or guesses which input of a mixer or selector is
     * routed; any doubt means no hardware volume.
     */
    fun feature(raw: ByteArray, interface_: Int, terminal: Int, channels: Int): Feature? {
        var active = false
        var uac2 = false
        val sources = HashMap<Int, Int>()
        val features = HashMap<Int, Feature>()
        val outputs = mutableListOf<Int>()
        var offset = 0
        while (offset + 2 <= raw.size) {
            val len = raw[offset].toInt() and 255
            if (len < 2 || len > raw.size - offset) return null
            val d = raw.copyOfRange(offset, offset + len)
            offset += len
            fun u(i: Int) = d[i].toInt() and 255
            if (u(1) == 4 && len >= 9) {
                active = u(2) == interface_ && u(5) == 1 && u(6) == 1
                uac2 = u(7) == 0x20
            } else if (active && u(1) == 0x24 && len >= 4) {
                when (u(2)) {
                    3 -> if (len >= 9) {
                        sources[u(3)] = u(7)
                        outputs += u(3)
                    }
                    6 -> if (len >= 7) {
                        sources[u(3)] = u(4)
                        val size = if (uac2) 4 else u(5)
                        val start = if (uac2) 5 else 6
                        if (size == 0 || size > 4 || len < start + size + 1) continue
                        fun writable(channel: Int): Boolean {
                            val pos = start + channel * size
                            if (pos + size >= len) return false
                            return if (uac2) (u(pos) shr 2) and 3 == 3 else u(pos) and 2 != 0
                        }
                        val controlled = when {
                            writable(0) -> listOf(0)
                            (1..channels).all(::writable) -> (1..channels).toList()
                            else -> continue
                        }
                        features[u(3)] = Feature(u(3), interface_, uac2, controlled)
                    }
                }
            }
        }
        val matches = mutableListOf<Feature>()
        for (output in outputs) {
            var id = output
            val seen = mutableListOf<Int>()
            var candidate: Feature? = null
            while (id != terminal && id !in seen) {
                seen += id
                if (candidate == null) candidate = features[id]
                id = sources[id] ?: break
            }
            if (id == terminal && candidate != null) matches += candidate
        }
        val distinct = matches.distinct()
        return distinct.singleOrNull()
    }

    private data class Range(val min: Int, val max: Int, val step: Int) {
        fun floor(value: Int): Int {
            val clamped = value.coerceIn(min, max)
            val step = step.coerceAtLeast(1)
            return min + (clamped - min) / step * step
        }

        companion object {
            fun parse(bytes: ByteArray, at: Int = 0): Range {
                val r = Range(s16(bytes, at), s16(bytes, at + 2), s16(bytes, at + 4))
                if (r.min == Short.MIN_VALUE.toInt() || r.min > r.max || r.step < 0) {
                    throw IllegalStateException("Invalid USB volume range")
                }
                return r
            }
        }
    }

    private fun s16(bytes: ByteArray, at: Int): Int = ((bytes[at].toInt() and 255) or (bytes[at + 1].toInt() shl 8))

    private fun le16(value: Int) = byteArrayOf(value.toByte(), (value shr 8).toByte())

    class Volume private constructor(
        private val feature: Feature,
        private val ranges: List<List<Range>>,
        /** Each channel's level as the DAC had it before [open] attenuated it. */
        private val original: List<Int>,
        snapshot: Snapshot,
    ) {
        var snapshot: Snapshot = snapshot
            private set

        /** Sets every controlled channel to [db] (at most 0 dB), verified by read-back. */
        fun set(control: UsbControl, db: Double): Snapshot {
            require(db.isFinite() && db <= 0.0 && db >= -128.0) { "Invalid USB volume" }
            val target = floor(minOf(db, snapshot.maxDb) * 256.0).toInt()
            return setTargets(control, List(feature.channels.size) { target })
        }

        /**
         * Puts every channel back to the level it had before [open], so the
         * DAC is left as it was found. The attenuator lives in the DAC, not in
         * the stream, and would otherwise follow it into Android's own driver
         * and every other app.
         */
        fun restore(control: UsbControl): Snapshot = setTargets(control, original)

        internal fun setTargets(control: UsbControl, targets: List<Int>): Snapshot {
            val index = (feature.unit shl 8) or feature.interface_
            var loudest = Short.MIN_VALUE.toInt()
            for ((i, channel) in feature.channels.withIndex()) {
                val target = targets[i]
                // -32768 is USB's negative infinity. A channel already silent is
                // kept silent; startup must never raise a quieter channel.
                val value = if (target == Short.MIN_VALUE.toInt()) {
                    target
                } else {
                    ranges[i].filter { it.min <= target }.maxOfOrNull { it.floor(target) }
                        ?: throw IllegalStateException("USB volume below supported range")
                }
                val selector = 0x200 or channel
                control.transfer(false, SET_CUR, selector, index, le16(value))
                val readback = ByteArray(2)
                control.transfer(true, if (feature.uac2) UAC2_CUR else UAC1_GET_CUR, selector, index, readback)
                val actual = s16(readback, 0)
                if (actual != value) throw IllegalStateException("USB volume read-back mismatch")
                loudest = maxOf(loudest, actual)
            }
            snapshot = snapshot.copy(currentDb = loudest / 256.0)
            return snapshot
        }

        companion object {
            private const val SET_CUR = 1
            private const val UAC2_CUR = 1
            private const val UAC2_RANGE = 2
            private const val UAC1_GET_CUR = 0x81

            /** The quietest level the DAC is brought to before the first audio transfer. */
            private const val STARTUP_DB = -40

            /**
             * Reads every controlled channel's range and current level, then
             * lowers each to at most [STARTUP_DB] — never raising one — and
             * confirms by read-back before any audio may play.
             */
            fun open(control: UsbControl, feature: Feature): Volume {
                val index = (feature.unit shl 8) or feature.interface_
                val ranges = mutableListOf<List<Range>>()
                var current = Short.MIN_VALUE.toInt()
                var restoreLimit = Short.MAX_VALUE.toInt()
                val startup = mutableListOf<Int>()
                val original = mutableListOf<Int>()
                for (channel in feature.channels) {
                    val selector = 0x200 or channel
                    val data = ByteArray(2)
                    control.transfer(true, if (feature.uac2) UAC2_CUR else UAC1_GET_CUR, selector, index, data)
                    val level = s16(data, 0)
                    current = maxOf(current, level)
                    restoreLimit = minOf(restoreLimit, level)
                    original += level
                    startup += minOf(level, STARTUP_DB * 256)
                    ranges += if (feature.uac2) {
                        control.transfer(true, UAC2_RANGE, selector, index, data)
                        val count = (data[0].toInt() and 255) or ((data[1].toInt() and 255) shl 8)
                        if (count !in 1..16) throw IllegalStateException("Unsupported USB volume ranges")
                        val all = ByteArray(2 + 6 * count)
                        control.transfer(true, UAC2_RANGE, selector, index, all)
                        val echoed = (all[0].toInt() and 255) or ((all[1].toInt() and 255) shl 8)
                        if (echoed != count) throw IllegalStateException("USB volume range changed")
                        (0 until count).map { Range.parse(all, 2 + it * 6) }
                    } else {
                        val all = ByteArray(6)
                        for ((i, request) in (0x82..0x84).withIndex()) {
                            val part = ByteArray(2)
                            control.transfer(true, request, selector, index, part)
                            part.copyInto(all, i * 2)
                        }
                        listOf(Range.parse(all))
                    }
                }
                val min = ranges.maxOf { r -> r.minOf { it.min } }
                val max = minOf(ranges.minOf { r -> r.maxOf { it.max } }, 0)
                if (min >= max || min > STARTUP_DB * 256) {
                    throw IllegalStateException("USB volume cannot provide startup attenuation")
                }
                val volume = Volume(
                    feature,
                    ranges,
                    original,
                    Snapshot(
                        available = true,
                        minDb = min / 256.0,
                        maxDb = max / 256.0,
                        currentDb = current / 256.0,
                        restoreLimitDb = restoreLimit / 256.0,
                    ),
                )
                // Never raise the device's current volume on connection.
                volume.setTargets(control, startup)
                return volume
            }
        }
    }
}
