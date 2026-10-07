package com.music.bitchord.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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

    private val _changes = MutableStateFlow(0)

    /** Bumped whenever the system's set of outputs changes; lists keyed on it re-enumerate. */
    val changes: StateFlow<Int> = _changes

    internal fun systemChanged() = _changes.update { it + 1 }

    /**
     * Every output that can actually play audio, newest enumeration each call.
     *
     * On Windows these are WASAPI endpoints keyed by endpoint id — what the native sink opens —
     * rather than Java Sound mixers, whose names only loosely match an endpoint and include
     * entries such as "Primary Sound Driver" that no endpoint answers to.
     */
    fun available(): List<DesktopAudioDevice> {
        if (DesktopPlatform.isWindows) {
            DesktopWindowsAudio.devices()?.let { endpoints ->
                adoptLegacyChoice(endpoints)
                return endpoints
            }
        }
        return runCatching {
            AudioSystem.getMixerInfo()
                .filter { info ->
                    runCatching {
                        AudioSystem.getMixer(info).isLineSupported(DataLine.Info(SourceDataLine::class.java, null))
                    }.getOrDefault(false)
                }
                .map { DesktopAudioDevice(it.name, it.name, it.description.orEmpty()) }
                .distinctBy { it.id }
        }.getOrDefault(emptyList())
    }

    /** A choice stored by mixer name before endpoint ids, moved onto the endpoint of that name. */
    private fun adoptLegacyChoice(endpoints: List<DesktopAudioDevice>) {
        val stored = _selected.value
        if (stored == SYSTEM_DEFAULT || endpoints.any { it.id == stored }) return
        endpoints.firstOrNull { it.name.equals(stored, ignoreCase = true) }?.let { select(it.id) }
    }

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
    fun label(): String {
        val stored = _selected.value
        val default = DesktopStrings["d_system_default", "System default"]
        if (stored == SYSTEM_DEFAULT) return default
        // An unplugged choice plays on the default until it returns, so say so rather than
        // showing a raw endpoint id.
        return available().firstOrNull { it.id == stored }?.name ?: default
    }

    private const val KEY_DEVICE = "audio_output_device"
}
