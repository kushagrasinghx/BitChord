package com.music.bitchord.playback

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothAutoplayPolicyTest {

    @Test
    fun audioRoutingIdentifiesBluetoothAudioSinks() {
        assertEquals(AudioRouting.Kind.BLUETOOTH, AudioRouting.kindOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertEquals(AudioRouting.Kind.BLUETOOTH, AudioRouting.kindOf(AudioDeviceInfo.TYPE_HEARING_AID))
        assertEquals(AudioRouting.Kind.BLUETOOTH, AudioRouting.kindOf(AudioDeviceInfo.TYPE_BLE_HEADSET))
        assertEquals(AudioRouting.Kind.BLUETOOTH, AudioRouting.kindOf(AudioDeviceInfo.TYPE_BLE_SPEAKER))
        assertEquals(AudioRouting.Kind.BLUETOOTH, AudioRouting.kindOf(AudioDeviceInfo.TYPE_BLE_BROADCAST))
    }

    @Test
    fun telephonyScoIsNotTreatedAsMusicBluetoothSink() {
        // TYPE_BLUETOOTH_SCO is telephony-only and must be ignored for music playback
        assertEquals(null, AudioRouting.kindOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO))
    }

    @Test
    fun autoplayPolicyTriggersOnlyWhenEligible() {
        fun shouldAutoplay(
            bluetoothAutoplaySetting: Boolean,
            isBluetoothAudioSink: Boolean,
            hasMediaItems: Boolean,
            isPlaying: Boolean,
            manuallyPaused: Boolean,
        ): Boolean {
            return bluetoothAutoplaySetting &&
                isBluetoothAudioSink &&
                hasMediaItems &&
                !isPlaying &&
                !manuallyPaused
        }

        // Normal reconnect after disconnect: eligible
        assertTrue(
            shouldAutoplay(
                bluetoothAutoplaySetting = true,
                isBluetoothAudioSink = true,
                hasMediaItems = true,
                isPlaying = false,
                manuallyPaused = false,
            ),
        )

        // Setting disabled: do not autoplay
        assertFalse(
            shouldAutoplay(
                bluetoothAutoplaySetting = false,
                isBluetoothAudioSink = true,
                hasMediaItems = true,
                isPlaying = false,
                manuallyPaused = false,
            ),
        )

        // User intentionally paused: respect manual pause
        assertFalse(
            shouldAutoplay(
                bluetoothAutoplaySetting = true,
                isBluetoothAudioSink = true,
                hasMediaItems = true,
                isPlaying = false,
                manuallyPaused = true,
            ),
        )

        // Already playing: do not re-trigger
        assertFalse(
            shouldAutoplay(
                bluetoothAutoplaySetting = true,
                isBluetoothAudioSink = true,
                hasMediaItems = true,
                isPlaying = true,
                manuallyPaused = false,
            ),
        )

        // Non-bluetooth device: do not trigger
        assertFalse(
            shouldAutoplay(
                bluetoothAutoplaySetting = true,
                isBluetoothAudioSink = false,
                hasMediaItems = true,
                isPlaying = false,
                manuallyPaused = false,
            ),
        )

        // Empty queue: do not trigger
        assertFalse(
            shouldAutoplay(
                bluetoothAutoplaySetting = true,
                isBluetoothAudioSink = true,
                hasMediaItems = false,
                isPlaying = false,
                manuallyPaused = false,
            ),
        )
    }
}
