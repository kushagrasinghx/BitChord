package com.music.bitchord.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusBarContrastTest {
    @Test
    fun darkTopBandUsesSubtleBaseScrim() {
        assertEquals(0.16f, topBandScrimAlpha(0f), 0.0001f)
        assertEquals(0.16f, topBandScrimAlpha(null), 0.0001f)
    }

    @Test
    fun lightTopBandUsesMaximumScrim() {
        assertEquals(0.52f, topBandScrimAlpha(1f), 0.0001f)
        assertEquals(0.52f, topBandScrimAlpha(2f), 0.0001f)
    }

    @Test
    fun mixedTopBandInterpolatesScrimInLinearLuminance() {
        val mixed = averageRelativeLuminance(
            intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
        )

        assertEquals(0.5f, mixed, 0.0001f)
        assertEquals(0.34f, topBandScrimAlpha(mixed), 0.0001f)
    }
}
