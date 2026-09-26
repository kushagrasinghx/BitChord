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
        assertEquals(0.65f, topBandScrimAlpha(1f), 0.0001f)
        // Out of range is clamped, not extrapolated: a luminance above 1 is a
        // rounding artefact, not a reason to darken past the ceiling.
        assertEquals(0.65f, topBandScrimAlpha(2f), 0.0001f)
    }

    @Test
    fun mixedTopBandInterpolatesScrimInLinearLuminance() {
        val mixed = averageRelativeLuminance(
            intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
        )

        assertEquals(0.5f, mixed, 0.0001f)
        // The midpoint of the 0.16…0.65 band, read off the linear luminance
        // rather than off the sRGB values — half black and half white averages
        // to a mid grey here, not to the much darker sRGB midpoint.
        assertEquals(0.405f, topBandScrimAlpha(mixed), 0.0001f)
    }
}
