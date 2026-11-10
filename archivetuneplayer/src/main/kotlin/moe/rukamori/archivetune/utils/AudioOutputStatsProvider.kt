/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

data class AudioOutputStats(
    val deviceLabel: String,
    val deviceTypeLabel: String,
    val deviceSampleRates: List<Int>,
    val mixSampleRate: Int,
) {
    val hasData: Boolean get() = mixSampleRate > 0 || deviceSampleRates.isNotEmpty()

    fun conversionDescription(sourceSampleRate: Int?): String? {
        val source = sourceSampleRate?.takeIf { it > 0 } ?: return null
        val output = when {
            deviceSampleRates.isNotEmpty() -> deviceSampleRates.maxOrNull() ?: mixSampleRate
            mixSampleRate > 0 -> mixSampleRate
            else -> return null
        }
        return if (source == output) {
            "No conversion — source matches the $output Hz output route"
        } else {
            "Android resamples $source Hz → $output Hz at the output route"
        }
    }
}

object AudioOutputStatsProvider {
    fun resolve(context: Context): AudioOutputStats {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return AudioOutputStats("Unknown", "Unknown", emptyList(), 0)

        val device = resolveActiveSink(audioManager)
        val rates = device?.sampleRates?.filter { it > 0 }?.distinct()?.sorted() ?: emptyList()

        val propertyRate =
            runCatching {
                audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 0
            }.getOrNull() ?: 0

        return AudioOutputStats(
            deviceLabel = device?.productName?.toString()?.takeUnless { it.isBlank() } ?: "Current output",
            deviceTypeLabel = deviceTypeName(device?.type),
            deviceSampleRates = rates,
            mixSampleRate = propertyRate,
        )
    }

    private fun resolveActiveSink(audioManager: AudioManager): AudioDeviceInfo? {
        val sinks = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.isSink }
        return sinks.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY }
            ?: sinks.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER }
            ?: sinks.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES }
            ?: sinks.firstOrNull { it.type == AudioDeviceInfo.TYPE_HDMI || it.type == AudioDeviceInfo.TYPE_HDMI_ARC || it.type == AudioDeviceInfo.TYPE_HDMI_EARC }
            ?: sinks.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE || it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
    }

    private fun deviceTypeName(type: Int?): String =
        when (type) {
            null -> "Unknown"
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
            AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE audio"
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired"
            AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> "Speaker"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
            else -> "Other"
        }
}
