package com.music.bitchord.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.Mixer
import javax.sound.sampled.SourceDataLine

/** One place the samples can be sent. */
internal data class DesktopAudioDevice(val id: String, val name: String, val description: String)

/**
 * The output devices this machine offers, and which one playback should use.
 *
 * Android picks a route through `AudioManager`; a desktop picks a mixer. The choice is stored by
 * name rather than by index, because the order changes when a device is plugged in or removed.
 */
internal object DesktopAudioDevices {

    /** Follows whatever the system calls the default. */
    const val SYSTEM_DEFAULT = ""

    private val _selected = MutableStateFlow(DesktopPersistence().string(KEY_DEVICE))

    /** The stored choice: a device id, or blank for the system's own default. */
    val selected: StateFlow<String> = _selected

    fun select(id: String) {
        DesktopPersistence().saveString(KEY_DEVICE, id)
        _selected.value = id
    }

    /** Every mixer that can actually play audio out, newest enumeration each call. */
    fun available(): List<DesktopAudioDevice> = runCatching {
        AudioSystem.getMixerInfo()
            .filter { info ->
                runCatching {
                    AudioSystem.getMixer(info).isLineSupported(DataLine.Info(SourceDataLine::class.java, null))
                }.getOrDefault(false)
            }
            .map { DesktopAudioDevice(it.name, it.name, it.description.orEmpty()) }
            .distinctBy { it.id }
    }.getOrDefault(emptyList())

    /**
     * The mixer to open, or null for the system default.
     *
     * A stored device that is no longer present falls back to the default rather than failing —
     * headphones get unplugged, and that should not stop playback.
     */
    fun mixerFor(id: String): Mixer? {
        if (id == SYSTEM_DEFAULT) return null
        return runCatching {
            AudioSystem.getMixerInfo()
                .firstOrNull { it.name == id }
                ?.let(AudioSystem::getMixer)
        }.getOrNull()
    }

    /** What Settings shows for the stored choice. */
    fun label(): String = _selected.value.takeIf { it != SYSTEM_DEFAULT }
        ?: DesktopStrings["d_system_default", "System default"]

    private const val KEY_DEVICE = "audio_output_device"
}
