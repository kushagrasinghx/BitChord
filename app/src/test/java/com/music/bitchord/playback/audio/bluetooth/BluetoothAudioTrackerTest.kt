/*
 * Copyright (C) 2026 BitChord Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package com.music.bitchord.playback.audio.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothAudioTrackerTest {

    @Test
    fun mapCodecTypeHandlesAllStandardAndVendorTypes() {
        assertEquals("SBC", BluetoothAudioTracker.mapCodecType(BluetoothAudioTracker.SOURCE_CODEC_TYPE_SBC))
        assertEquals("AAC", BluetoothAudioTracker.mapCodecType(BluetoothAudioTracker.SOURCE_CODEC_TYPE_AAC))
        assertEquals("aptX", BluetoothAudioTracker.mapCodecType(BluetoothAudioTracker.SOURCE_CODEC_TYPE_APTX))
        assertEquals("aptX HD", BluetoothAudioTracker.mapCodecType(BluetoothAudioTracker.SOURCE_CODEC_TYPE_APTX_HD))
        assertEquals("LDAC", BluetoothAudioTracker.mapCodecType(BluetoothAudioTracker.SOURCE_CODEC_TYPE_LDAC))
        assertEquals("LC3", BluetoothAudioTracker.mapCodecType(BluetoothAudioTracker.SOURCE_CODEC_TYPE_LC3))
        assertEquals("Opus", BluetoothAudioTracker.mapCodecType(BluetoothAudioTracker.SOURCE_CODEC_TYPE_OPUS))
        assertEquals("Vendor Codec (7)", BluetoothAudioTracker.mapCodecType(7))
        assertEquals("Vendor Codec (8)", BluetoothAudioTracker.mapCodecType(8))
        assertEquals("Unknown", BluetoothAudioTracker.mapCodecType(-1))
    }

    @Test
    fun mapSampleRateParsesBitmasksAndDirectRates() {
        assertEquals(44100, BluetoothAudioTracker.mapSampleRate(1 shl 0))
        assertEquals(48000, BluetoothAudioTracker.mapSampleRate(1 shl 1))
        assertEquals(88200, BluetoothAudioTracker.mapSampleRate(1 shl 2))
        assertEquals(96000, BluetoothAudioTracker.mapSampleRate(1 shl 3))
        assertEquals(176400, BluetoothAudioTracker.mapSampleRate(1 shl 4))
        assertEquals(192000, BluetoothAudioTracker.mapSampleRate(1 shl 5))

        assertEquals(44100, BluetoothAudioTracker.mapSampleRate(44100))
        assertEquals(48000, BluetoothAudioTracker.mapSampleRate(48000))
        assertEquals(96000, BluetoothAudioTracker.mapSampleRate(96000))
        assertEquals(192000, BluetoothAudioTracker.mapSampleRate(192000))

        assertNull(BluetoothAudioTracker.mapSampleRate(0))
        assertNull(BluetoothAudioTracker.mapSampleRate(12345))
    }

    @Test
    fun mapBitDepthParsesBitmasksAndDirectDepths() {
        assertEquals(16, BluetoothAudioTracker.mapBitDepth(1 shl 0))
        assertEquals(24, BluetoothAudioTracker.mapBitDepth(1 shl 1))
        assertEquals(32, BluetoothAudioTracker.mapBitDepth(1 shl 2))

        assertEquals(16, BluetoothAudioTracker.mapBitDepth(16))
        assertEquals(24, BluetoothAudioTracker.mapBitDepth(24))
        assertEquals(32, BluetoothAudioTracker.mapBitDepth(32))

        assertNull(BluetoothAudioTracker.mapBitDepth(0))
        assertNull(BluetoothAudioTracker.mapBitDepth(8))
    }

    @Test
    fun ldacQualityModeIsTruthfullySeparatedFromRealtimeBitrate() {
        val (bitrate1000, mode1000) = BluetoothAudioTracker.parseLdacBitrate(1000L)
        assertEquals("Not exposed by Android", bitrate1000)
        assertEquals("High Quality (990 kbps nominal)", mode1000)

        val (bitrate1001, mode1001) = BluetoothAudioTracker.parseLdacBitrate(1001L)
        assertEquals("Not exposed by Android", bitrate1001)
        assertEquals("Standard (660 kbps nominal)", mode1001)

        val (bitrate1002, mode1002) = BluetoothAudioTracker.parseLdacBitrate(1002L)
        assertEquals("Not exposed by Android", bitrate1002)
        assertEquals("Connection Priority (330 kbps nominal)", mode1002)

        val (bitrate1003, mode1003) = BluetoothAudioTracker.parseLdacBitrate(1003L)
        assertEquals("Not exposed by Android", bitrate1003)
        assertEquals("Adaptive Bitrate (ABR)", mode1003)

        val (bitrateUnknown, modeUnknown) = BluetoothAudioTracker.parseLdacBitrate(0L)
        assertEquals("Not exposed by Android", bitrateUnknown)
        assertNull(modeUnknown)
    }

    @Test
    fun realmeBudsAir5ProLdacTelemetryVerification() {
        val sampleRateHz = BluetoothAudioTracker.mapSampleRate(8) // 96000 Hz
        val bitDepth = BluetoothAudioTracker.mapBitDepth(4) // 32-bit
        val mode = BluetoothAudioTracker.parseLdacQualityMode(1000L)

        val telemetry = BluetoothTelemetry(
            isConnected = true,
            deviceName = "realme Buds Air 5 Pro",
            codecName = "LDAC",
            sampleRateHz = sampleRateHz,
            bitDepth = bitDepth,
            bitrateLabel = "Not exposed by Android",
            mode = mode,
            isAuthoritative = true,
        )

        assertTrue(telemetry.isConnected)
        assertEquals("realme Buds Air 5 Pro", telemetry.deviceName)
        assertEquals("LDAC", telemetry.codecName)
        assertEquals(96000, telemetry.sampleRateHz)
        assertEquals(32, telemetry.bitDepth)
        assertEquals("Not exposed by Android", telemetry.bitrateLabel)
        assertEquals("High Quality (990 kbps nominal)", telemetry.mode)
        assertTrue(telemetry.isHighRes)
        assertTrue(telemetry.isAuthoritative)
    }

    @Test
    fun standardAacConfigReportsAccurateTelemetry() {
        val telemetry = BluetoothTelemetry(
            isConnected = true,
            deviceName = "AirPods Pro",
            codecName = BluetoothAudioTracker.mapCodecType(BluetoothAudioTracker.SOURCE_CODEC_TYPE_AAC),
            sampleRateHz = BluetoothAudioTracker.mapSampleRate(2), // 48000 Hz
            bitDepth = BluetoothAudioTracker.mapBitDepth(1), // 16-bit
            bitrateLabel = "Not exposed by Android",
            mode = null,
            isAuthoritative = true,
        )

        assertTrue(telemetry.isConnected)
        assertEquals("AAC", telemetry.codecName)
        assertEquals(48000, telemetry.sampleRateHz)
        assertEquals(16, telemetry.bitDepth)
        assertEquals("Not exposed by Android", telemetry.bitrateLabel)
        assertNull(telemetry.mode)
        assertFalse(telemetry.isHighRes)
    }

    @Test
    fun defaultTelemetryRepresentsDisconnectedState() {
        val defaultTelemetry = BluetoothTelemetry()
        assertFalse(defaultTelemetry.isConnected)
        assertNull(defaultTelemetry.deviceName)
        assertEquals("Unknown", defaultTelemetry.codecName)
        assertNull(defaultTelemetry.sampleRateHz)
        assertNull(defaultTelemetry.bitDepth)
        assertEquals("Not exposed by Android", defaultTelemetry.bitrateLabel)
        assertNull(defaultTelemetry.mode)
        assertFalse(defaultTelemetry.isAuthoritative)
        assertEquals("Disconnected", defaultTelemetry.formattedSummary())
    }
}
