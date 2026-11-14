package com.music.bitchord

import android.media.AudioDeviceInfo
import com.music.bitchord.playback.audio.LosslessOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LosslessOutputTest {

    @Test
    fun `a connected DAC does not make active A2DP lossless`() {
        val state = LosslessOutput.decide(
            outputTypes = setOf(
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            ),
            bluetoothCodec = "SBC",
            bluetoothSampleRateHz = 48_000,
            bluetoothActive = true,
        )
        assertFalse(state.capable)
        assertEquals("SBC", state.bluetoothCodec)
    }

    @Test
    fun `wired output is lossless when Bluetooth is not active`() {
        val state = LosslessOutput.decide(
            outputTypes = setOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES),
            bluetoothCodec = null,
            bluetoothActive = false,
        )
        assertTrue(state.capable)
        assertEquals(LosslessOutput.Kind.WIRED, state.via)
    }

    @Test
    fun `lossy Bluetooth codecs never open the lossless gate`() {
        assertFalse(LosslessOutput.isLosslessBluetoothCodec("LDAC", 44_100))
        assertFalse(LosslessOutput.isLosslessBluetoothCodec("LDAC", 96_000))
        assertFalse(LosslessOutput.isLosslessBluetoothCodec("LHDC V5", 96_000))
        assertFalse(LosslessOutput.isLosslessBluetoothCodec("SSC UHQ", 96_000))
        assertTrue(LosslessOutput.isLosslessBluetoothCodec("aptX Lossless", 48_000))
    }

    @Test
    fun `unknown active Bluetooth codec is reported, not guessed`() {
        val state = LosslessOutput.decide(
            outputTypes = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP),
            bluetoothCodec = null,
            bluetoothActive = true,
        )
        assertFalse(state.capable)
        assertTrue(state.bluetoothCodecUnknown)
    }
}
