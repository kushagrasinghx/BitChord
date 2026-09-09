package com.music.bitchord.ui.theme

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

/** Local-only appearance preferences; backed by SharedPreferences, never network state. */
object LiquidGlassPreferences {
    private const val PREFS = "bitchord_appearance"
    private const val KEY_ENABLED = "liquid_glass_enabled"
    private const val KEY_STRENGTH = "liquid_glass_strength"

    private val enabledState = mutableStateOf(true)
    private val strengthState = mutableFloatStateOf(0.82f)

    fun enabled(context: Context): State<Boolean> = enabledState.apply {
        value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)
    }

    fun strength(context: Context): State<Float> = strengthState.apply {
        floatValue = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_STRENGTH, 0.82f)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        enabledState.value = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun setStrength(context: Context, strength: Float) {
        val clamped = strength.coerceIn(0f, 1f)
        strengthState.floatValue = clamped
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_STRENGTH, clamped).apply()
    }
}
