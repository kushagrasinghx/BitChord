package com.music.bitchord.desktop

import com.music.bitchord.playback.ReverbSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The listener's reverb, kept where the rest of the desktop settings are and in
 * the phone's shape: a switch, and the room as one stored string.
 */
internal object DesktopReverbSettings {

    private val persistence = DesktopPersistence()

    private val _enabled = MutableStateFlow(persistence.boolean(KEY_ENABLED, false))
    private val _settings = MutableStateFlow(ReverbSettings.decode(persistence.string(KEY_SETTINGS, "")))

    val enabled: StateFlow<Boolean> = _enabled
    val settings: StateFlow<ReverbSettings> = _settings

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        persistence.saveBoolean(KEY_ENABLED, value)
    }

    fun setSettings(value: ReverbSettings) {
        val clamped = value.clamped()
        _settings.value = clamped
        persistence.saveString(KEY_SETTINGS, clamped.encode())
    }

    private const val KEY_ENABLED = "reverb_enabled"
    private const val KEY_SETTINGS = "reverb"
}
