package com.music.bitchord.playback

import android.media.AudioFormat
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.music.bitchord.data.settings.OutputPcmMode
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Live facts about the Android output route, kept separate from source-format
 * statistics. The framework owns the final mixer/DAC decision, so these fields
 * deliberately describe the selected device's advertised capabilities rather
 * than pretending that an app can guarantee bit-perfect delivery.
 */
object AudioOutputStatus {
    data class Snapshot(
        val sink: String = "AudioTrack",
        val requestedPcmMode: OutputPcmMode = OutputPcmMode.PCM_16,
        val deviceName: String = "System default",
        val sampleRatesHz: IntArray = IntArray(0),
        val encodings: IntArray = IntArray(0),
        val isUsb: Boolean = false,
        val actualEncoding: Int? = null,
        val actualSampleRateHz: Int? = null,
        val floatFallback: Boolean = false,
    )

    val current = MutableStateFlow(Snapshot())

    fun publish(
        manager: AudioManager,
        requestedPcmMode: OutputPcmMode,
        preferred: AudioDeviceInfo?,
        floatEnabled: Boolean,
    ) {
        val device = preferred ?: manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.isSink }
        current.value = Snapshot(
            requestedPcmMode = requestedPcmMode,
            deviceName = device?.productName?.toString()?.ifBlank { null } ?: "System default",
            sampleRatesHz = device?.sampleRates ?: IntArray(0),
            encodings = device?.encodings ?: IntArray(0),
            isUsb = device?.type in setOf(
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_ACCESSORY,
            ),
            // Cleared on a route change. onAudioTrackInitialized publishes the
            // format Android actually accepted for the new AudioTrack.
            floatFallback = requestedPcmMode == OutputPcmMode.FLOAT_32 && !floatEnabled,
        )
    }

    fun publishAudioTrack(encoding: Int, sampleRateHz: Int) {
        current.value = current.value.copy(
            actualEncoding = encoding,
            actualSampleRateHz = sampleRateHz,
            floatFallback = current.value.requestedPcmMode == OutputPcmMode.FLOAT_32 &&
                encoding != AudioFormat.ENCODING_PCM_FLOAT,
        )
    }

    fun encodingLabel(snapshot: Snapshot): String = when (snapshot.actualEncoding) {
        AudioFormat.ENCODING_PCM_FLOAT -> "32-bit float"
        AudioFormat.ENCODING_PCM_16BIT -> if (snapshot.floatFallback) "16-bit fallback" else "16-bit PCM"
        null -> if (snapshot.floatFallback) "16-bit fallback" else snapshot.requestedPcmMode.label
        else -> "PCM (${snapshot.actualEncoding})"
    }
}
