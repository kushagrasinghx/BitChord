package com.music.bitchord.alarm

import kotlin.math.roundToInt

/** Pure conversion between the percentage exposed by the alarm UI and Android's alarm stream. */
object AlarmVolumePolicy {
    fun streamIndex(percent: Int, minimum: Int, maximum: Int): Int {
        if (maximum <= minimum) return minimum.coerceAtLeast(0)
        val safePercent = percent.coerceIn(10, 100)
        return (maximum * (safePercent / 100f))
            .roundToInt()
            .coerceIn(minimum, maximum)
    }

    fun percent(index: Int, minimum: Int, maximum: Int): Int {
        if (maximum <= minimum) return 0
        return ((index.coerceIn(minimum, maximum) * 100f) / maximum)
            .roundToInt()
            .coerceIn(0, 100)
    }
}
