package com.music.bitchord.desktop

import com.music.bitchord.playback.EqLayout
import com.music.bitchord.playback.EqualizerPreset
import com.music.bitchord.playback.manualCurve
import com.music.bitchord.playback.toneCurve
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Which of the equaliser's two tabs is driving the sound.
 *
 * One at a time rather than both at once: they are two ways of describing the same curve, and
 * summing them would mean a tone pad sitting at dead centre still quietly altering whatever the
 * sliders said.
 */
internal enum class DesktopEqualizerMode { DYNAMIC, MANUAL }

/** The listener's equaliser, kept where the rest of the desktop settings are. */
internal object DesktopEqualizerSettings {

    private val persistence = DesktopPersistence()

    private val _enabled = MutableStateFlow(persistence.boolean(KEY_ENABLED, false))
    private val _mode = MutableStateFlow(
        runCatching { DesktopEqualizerMode.valueOf(persistence.string(KEY_MODE, "")) }
            .getOrDefault(DesktopEqualizerMode.DYNAMIC),
    )
    private val _toneX = MutableStateFlow(clampStep(persistence.string(KEY_TONE_X, "0").toIntOrNull() ?: 0))
    private val _toneY = MutableStateFlow(clampStep(persistence.string(KEY_TONE_Y, "0").toIntOrNull() ?: 0))
    private val _focused = MutableStateFlow(persistence.boolean(KEY_FOCUSED, false))
    private val _balance = MutableStateFlow(
        (persistence.string(KEY_BALANCE, "0").toFloatOrNull() ?: 0f).coerceIn(-1f, 1f),
    )
    private val _bands = MutableStateFlow(readBands())
    private val _preset = MutableStateFlow(EqualizerPreset.matching(_bands.value))

    val enabled: StateFlow<Boolean> = _enabled
    val mode: StateFlow<DesktopEqualizerMode> = _mode

    /** Tone pad, horizontal: warm at -5, bright at +5. */
    val toneX: StateFlow<Int> = _toneX

    /** Tone pad, vertical: scooped at -5, mid-forward at +5. */
    val toneY: StateFlow<Int> = _toneY

    /** Tone pad bandwidth: Broad when false, Focused when true. */
    val focused: StateFlow<Boolean> = _focused

    /** Left/right trim, -1 hard left to +1 hard right. Applies to both tabs. */
    val balance: StateFlow<Float> = _balance

    /** The manual tab's seven gains, in decibels, low to high. */
    val bands: StateFlow<List<Float>> = _bands

    /** Which preset the bands currently are, or [EqualizerPreset.CUSTOM]. */
    val preset: StateFlow<EqualizerPreset> = _preset

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        persistence.saveBoolean(KEY_ENABLED, value)
    }

    fun setMode(value: DesktopEqualizerMode) {
        _mode.value = value
        persistence.saveString(KEY_MODE, value.name)
    }

    fun setTone(x: Int, y: Int) {
        _toneX.value = clampStep(x)
        _toneY.value = clampStep(y)
        persistence.saveString(KEY_TONE_X, _toneX.value.toString())
        persistence.saveString(KEY_TONE_Y, _toneY.value.toString())
    }

    fun setFocused(value: Boolean) {
        _focused.value = value
        persistence.saveBoolean(KEY_FOCUSED, value)
    }

    fun setBalance(value: Float) {
        _balance.value = value.coerceIn(-1f, 1f)
        persistence.saveString(KEY_BALANCE, _balance.value.toString())
    }

    /**
     * Writes the manual tab's seven gains, and renames the preset row to suit.
     *
     * Stored as one string rather than seven keys so that it is one thing to keep in step.
     */
    fun setBands(values: List<Float>) {
        val clamped = List(EqLayout.MANUAL_COUNT) {
            values.getOrElse(it) { 0f }.coerceIn(-EqLayout.MANUAL_RANGE_DB, EqLayout.MANUAL_RANGE_DB)
        }
        _bands.value = clamped
        _preset.value = EqualizerPreset.matching(clamped)
        persistence.saveString(KEY_BANDS, clamped.joinToString(","))
    }

    /** Applies a preset's curve. [EqualizerPreset.CUSTOM] carries none, so it does nothing. */
    fun setPreset(preset: EqualizerPreset) {
        if (preset == EqualizerPreset.CUSTOM) return
        setBands(preset.bands)
    }

    /** The curve the current tab describes, ready for [DesktopEqualizer.setTuning]. */
    fun curve() = when (_mode.value) {
        DesktopEqualizerMode.DYNAMIC -> toneCurve(_toneX.value, _toneY.value, _focused.value)
        DesktopEqualizerMode.MANUAL -> manualCurve(_bands.value)
    }

    private fun readBands(): List<Float> {
        val stored = persistence.string(KEY_BANDS, "")
            .split(",")
            .mapNotNull { it.trim().toFloatOrNull() }
        // Padded rather than rejected: a file written by a build with a different band count should
        // restore the ones it does have.
        return List(EqLayout.MANUAL_COUNT) {
            stored.getOrElse(it) { 0f }.coerceIn(-EqLayout.MANUAL_RANGE_DB, EqLayout.MANUAL_RANGE_DB)
        }
    }

    private fun clampStep(value: Int) = value.coerceIn(-EqLayout.TONE_STEPS, EqLayout.TONE_STEPS)

    private const val KEY_ENABLED = "equalizer_enabled"
    private const val KEY_MODE = "equalizer_mode"
    private const val KEY_TONE_X = "equalizer_tone_x"
    private const val KEY_TONE_Y = "equalizer_tone_y"
    private const val KEY_FOCUSED = "equalizer_focused"
    private const val KEY_BALANCE = "equalizer_balance"
    private const val KEY_BANDS = "equalizer_bands"
}
