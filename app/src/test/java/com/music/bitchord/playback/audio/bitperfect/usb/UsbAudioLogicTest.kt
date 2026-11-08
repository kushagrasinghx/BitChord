/*
 * Copyright (C) 2026 BitChord Project
 *
 * Adapted from Mornye (https://github.com/mornyeapp/Mornye),
 * rust_backend/crates/mobile/src/usb_audio/{descriptors,volume}.rs tests,
 * Copyright 2026 zarzet.
 *
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package com.music.bitchord.playback.audio.bitperfect.usb

import com.music.bitchord.playback.audio.bitperfect.usb.UsbDescriptors.Alternate
import com.music.bitchord.playback.audio.bitperfect.usb.UsbDescriptors.Device
import com.music.bitchord.playback.audio.bitperfect.usb.UsbDescriptors.Encoding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UsbAudioLogicTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    // ---- Descriptors ----------------------------------------------------------------------

    @Test
    fun parsesUac1FixedRateHeadsetWithoutClockControls() {
        val raw = bytes(
            9, 4, 0, 0, 0, 1, 1, 0, 0, 9, 4, 1, 1, 1, 1, 2, 0, 0, 7, 0x24, 1, 1, 1, 1, 0, 11, 0x24,
            2, 1, 2, 2, 16, 1, 0x80, 0xbb, 0, 9, 5, 1, 0x0d, 192, 0, 1, 0, 0, 7, 0x25, 1, 0, 0, 0,
            0,
        )
        val device = UsbDescriptors.parse(raw)
        val matches = UsbDescriptors.formats(device, 48000, 2, 16, dsd = false, dop = false)
        assertEquals(1, matches.size)
        val a = matches[0].first
        assertEquals(1, a.interface_)
        assertFalse(a.endpoints[0].rateControl)
        assertEquals(1, a.endpoints[0].address)
        assertEquals(192, a.endpoints[0].maxPacket)
        assertEquals(1, a.endpoints[0].interval)
        assertEquals(0x0d, a.endpoints[0].attributes)
        assertEquals(0, a.endpoints[0].syncAddress)
        assertTrue(UsbDescriptors.formats(device, 44100, 2, 16, dsd = false, dop = false).isEmpty())
        // Fixed at 48 kHz with no rate control: a plan only for exactly that rate.
        assertNotNull(UsbDescriptors.plan(a, matches[0].second))
    }

    @Test
    fun resolvesUac2TerminalClockAndExplicitFeedback() {
        val raw = bytes(
            9, 4, 2, 0, 0, 1, 1, 0x20, 0, 17, 0x24, 2, 7, 1, 1, 0, 9, 2, 3, 0, 0, 0, 0, 0, 0, 0, 9,
            4, 3, 1, 2, 1, 2, 0x20, 0, 16, 0x24, 1, 7, 0, 1, 1, 0, 0, 0, 2, 3, 0, 0, 0, 0, 6, 0x24,
            2, 1, 4, 24, 7, 5, 1, 5, 0, 4, 1, 7, 5, 0x81, 0x11, 4, 0, 4,
        )
        val device = UsbDescriptors.parse(raw)
        val matches = UsbDescriptors.formats(device, 96000, 2, 24, dsd = false, dop = false)
        assertEquals(1, matches.size)
        assertEquals(2, matches[0].first.control)
        assertEquals(9, matches[0].first.clock)
        assertEquals(0x81, matches[0].first.endpoints[1].address)
        assertEquals(4, matches[0].second.subslot)
        assertTrue(UsbDescriptors.formats(device, 96000, 2, 32, dsd = false, dop = false).isEmpty())
        val plan = UsbDescriptors.plan(matches[0].first, matches[0].second)!!
        assertEquals(1, plan.data.address)
        assertEquals(0x81, plan.feedback!!.address)
    }

    @Test
    fun untrustedDescriptorsAreBounded() {
        for (data in listOf(bytes(0, 4), bytes(9, 4, 0), bytes(1), ByteArray(18) { -1 })) {
            try {
                UsbDescriptors.parse(data)
                fail("accepted malformed descriptor")
            } catch (_: UsbDescriptors.MalformedDescriptorException) {
            }
        }
        assertTrue(UsbDescriptors.parse(ByteArray(0)).alternates.isEmpty())
    }

    @Test
    fun unknownDsdDeviceRequiresExplicitDop() {
        val a = Alternate(uac2 = true, clock = 1, channels = 2, bits = 24, subslot = 3, pcm = true)
        val device = Device(alternates = listOf(a))
        assertTrue(UsbDescriptors.formats(device, 2822400, 2, 1, dsd = true, dop = false).isEmpty())
        val f = UsbDescriptors.formats(device, 2822400, 2, 1, dsd = true, dop = true)
        assertEquals(176400, f[0].second.sampleRate)
        assertEquals(Encoding.DOP, f[0].second.encoding)
        assertTrue(UsbDescriptors.formats(device, 96000, 2, 32, dsd = false, dop = false).isEmpty())
    }

    @Test
    fun nativeRequiresExactFirmwareAndAltsetting() {
        val a = Alternate(uac2 = true, alternate = 2, clock = 1, channels = 2, subslot = 4, bits = 32, pcm = true)
        val device = Device(vendor = 0x16d0, product = 0x071a, revision = 0x0199, alternates = listOf(a))
        assertEquals(Encoding.DSD_LE, UsbDescriptors.formats(device, 2822400, 2, 1, dsd = true, dop = false)[0].second.encoding)
        assertTrue(UsbDescriptors.formats(device.copy(revision = 0x100), 2822400, 2, 1, dsd = true, dop = false).isEmpty())
    }

    @Test
    fun implicitFeedbackIsNotPlanned() {
        val a = Alternate(
            uac2 = true,
            clock = 1,
            channels = 2,
            bits = 24,
            subslot = 4,
            pcm = true,
            // Asynchronous OUT endpoint (sync type 1) with no feedback endpoint.
            endpoints = listOf(UsbDescriptors.Endpoint(address = 1, attributes = 0x05, maxPacket = 512, interval = 1)),
        )
        val format = UsbDescriptors.formats(Device(alternates = listOf(a)), 96000, 2, 24, dsd = false, dop = false)
        assertNull(UsbDescriptors.plan(format[0].first, format[0].second))
    }

    // ---- Volume ---------------------------------------------------------------------------

    /** UAC2 control at one level, ranging -96..0 dB in 1 dB steps. */
    private class Fake(var db: Int, val mismatch: Boolean = false) : UsbControl {
        override fun transfer(input: Boolean, request: Int, value: Int, index: Int, data: ByteArray) {
            if (!input) {
                if (!mismatch) db = (data[0].toInt() and 255) or (data[1].toInt() shl 8)
            } else if (request == 2) {
                val range = bytes(1, 0, 0, 0xa0, 0, 0, 0, 1)
                range.copyInto(data, 0, 0, data.size)
            } else {
                data[0] = db.toByte()
                data[1] = (db shr 8).toByte()
            }
        }

        private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    }

    private val master = UsbVolume.Feature(unit = 2, interface_ = 0, uac2 = true, channels = listOf(0))

    @Test
    fun startupAttenuatesWithoutRaisingAndRoundsDown() {
        val io = Fake(0)
        val volume = UsbVolume.Volume.open(io, master)
        assertEquals(-40.0, volume.snapshot.currentDb, 0.0)
        assertEquals(-26.0, volume.set(io, -25.2).currentDb, 0.0)
        io.db = -70 * 256
        assertEquals(-70.0, UsbVolume.Volume.open(io, master).snapshot.currentDb, 0.0)
    }

    /** The attenuator lives in the DAC: closing the stream must hand it back as found. */
    @Test
    fun restoreReturnsEveryChannelToItsLevelBeforeOpen() {
        class Stereo(val levels: IntArray) : UsbControl {
            override fun transfer(input: Boolean, request: Int, value: Int, index: Int, data: ByteArray) {
                val channel = (value and 255) - 1
                if (!input) {
                    levels[channel] = (data[0].toInt() and 255) or (data[1].toInt() shl 8)
                } else if (request == 2) {
                    byteArrayOf(1, 0, 0, 0xa0.toByte(), 0, 0, 0, 1).copyInto(data, 0, 0, data.size)
                } else {
                    data[0] = levels[channel].toByte()
                    data[1] = (levels[channel] shr 8).toByte()
                }
            }
        }
        val io = Stereo(intArrayOf(0, Short.MIN_VALUE.toInt()))
        val volume = UsbVolume.Volume.open(io, UsbVolume.Feature(2, 0, true, listOf(1, 2)))
        volume.set(io, -12.0)
        assertEquals(-12 * 256, io.levels[0])
        volume.restore(io)
        assertEquals(0, io.levels[0])
        assertEquals(Short.MIN_VALUE.toInt(), io.levels[1])
    }

    @Test
    fun failedReadbackCannotReportSafeVolume() {
        try {
            UsbVolume.Volume.open(Fake(0, mismatch = true), master)
            fail("expected a read-back failure")
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun startupPreservesSilentChannels() {
        val io = Fake(Short.MIN_VALUE.toInt())
        val volume = UsbVolume.Volume.open(io, master)
        assertEquals(-128.0, volume.snapshot.currentDb, 0.0)
        assertEquals(Short.MIN_VALUE.toInt(), io.db)
    }

    @Test
    fun topologySelectsPlaybackNotCapture() {
        val raw = bytes(
            9, 4, 0, 0, 0, 1, 1, 0x20, 0, 18, 0x24, 6, 2, 1, 12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 12, 0x24, 3, 3, 1, 3, 0, 2, 1, 0, 0, 0,
        )
        assertEquals(2, UsbVolume.feature(raw, 0, 1, 2)!!.unit)
        assertNull(UsbVolume.feature(raw, 0, 9, 2))
        assertNull(UsbVolume.feature(raw, 1, 1, 2))
    }

    @Test
    fun uac1ControlUsesMinMaxResolutionAndVerifiedCurrent() {
        class Uac1(var db: Int) : UsbControl {
            override fun transfer(input: Boolean, request: Int, value: Int, index: Int, data: ByteArray) {
                assertEquals(0x200, value)
                assertEquals(0x200, index)
                if (!input) {
                    assertEquals(1, request)
                    db = (data[0].toInt() and 255) or (data[1].toInt() shl 8)
                } else {
                    val v = when (request) {
                        0x81 -> db
                        0x82 -> -80 * 256
                        0x83 -> 0
                        0x84 -> 128
                        else -> error("Unexpected UAC1 request")
                    }
                    data[0] = v.toByte()
                    data[1] = (v shr 8).toByte()
                }
            }
        }
        val raw = bytes(
            9, 4, 0, 0, 0, 1, 1, 0, 0, 10, 0x24, 6, 2, 1, 1, 3, 0, 0, 0, 9, 0x24, 3, 3, 1, 3, 0, 2,
            0,
        )
        val control = Uac1(-10 * 256)
        val volume = UsbVolume.Volume.open(control, UsbVolume.feature(raw, 0, 1, 2)!!)
        assertEquals(-40.0, volume.snapshot.currentDb, 0.0)
        assertEquals(-23.5, volume.set(control, -23.1).currentDb, 0.0)
        for (invalid in listOf(Double.NaN, 2.0)) {
            try {
                volume.set(control, invalid)
                fail("accepted $invalid dB")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Test
    fun startupKeepsIndependentChannelsAtOrBelowTheirOldLevels() {
        class Stereo(val levels: IntArray) : UsbControl {
            override fun transfer(input: Boolean, request: Int, value: Int, index: Int, data: ByteArray) {
                val channel = (value and 255) - 1
                if (!input) {
                    levels[channel] = (data[0].toInt() and 255) or (data[1].toInt() shl 8)
                } else if (request == 2) {
                    byteArrayOf(1, 0, 0, 0xa0.toByte(), 0, 0, 0, 1).copyInto(data, 0, 0, data.size)
                } else {
                    data[0] = levels[channel].toByte()
                    data[1] = (levels[channel] shr 8).toByte()
                }
            }
        }
        val io = Stereo(intArrayOf(-70 * 256, -10 * 256))
        val volume = UsbVolume.Volume.open(io, UsbVolume.Feature(2, 0, true, listOf(1, 2)))
        assertEquals(-70 * 256, io.levels[0])
        assertEquals(-40 * 256, io.levels[1])
        assertEquals(-70.0, volume.snapshot.restoreLimitDb, 0.0)
    }
}
