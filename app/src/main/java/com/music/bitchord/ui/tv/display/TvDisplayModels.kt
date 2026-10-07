package com.music.bitchord.ui.tv.display

import androidx.compose.runtime.Immutable

enum class TvRefreshRatePreference(val label: String, val description: String) {
    SYSTEM_AUTO("Auto (Recommended)", "Allows the TV and Android system to manage the display refresh rate dynamically."),
    SMOOTH_60("Smooth 60 Hz", "Requests standard 60 Hz interface motion at native resolution."),
    ULTRA_120("Ultra 120 Hz", "Requests up to 120 fps for interface motion on compatible displays. Canvas video maintains native source frame rate.");

    companion object {
        fun fromString(value: String): TvRefreshRatePreference = when (value.uppercase()) {
            "SMOOTH_60", "60" -> SMOOTH_60
            "ULTRA_120", "120" -> ULTRA_120
            else -> SYSTEM_AUTO
        }
    }
}

@Immutable
data class TvDisplayMode(
    val id: Int,
    val physicalWidth: Int,
    val physicalHeight: Int,
    val refreshRate: Float,
    val alternativeRefreshRates: FloatArray = floatArrayOf(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TvDisplayMode
        return id == other.id &&
                physicalWidth == other.physicalWidth &&
                physicalHeight == other.physicalHeight &&
                refreshRate == other.refreshRate
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + physicalWidth
        result = 31 * result + physicalHeight
        result = 31 * result + refreshRate.hashCode()
        return result
    }
}

@Immutable
data class TvDisplayCapabilities(
    val displayId: Int = 0,
    val currentModeId: Int = 0,
    val currentPhysicalWidth: Int = 1920,
    val currentPhysicalHeight: Int = 1080,
    val actualRefreshRateHz: Float = 60.0f,
    val supportedModes: List<TvDisplayMode> = emptyList(),
    val compatible60ModeId: Int? = null,
    val compatible120ModeId: Int? = null,
    val canSwitchTo120Seamlessly: Boolean = false,
    val hasAdaptiveRefreshRateSupport: Boolean? = null,
) {
    val is120HzSupported: Boolean
        get() = compatible120ModeId != null ||
                actualRefreshRateHz >= 119.0f ||
                supportedModes.any { it.refreshRate >= 119.0f || it.alternativeRefreshRates.any { rate -> rate >= 119.0f } }

    val is60HzSupported: Boolean
        get() = compatible60ModeId != null || supportedModes.any { it.refreshRate in 58.0f..62.0f }
}
