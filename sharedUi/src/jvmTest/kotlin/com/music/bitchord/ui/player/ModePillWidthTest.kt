package com.music.bitchord.ui.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModePillWidthTest {
    /** The lyrics glyph, the three-segment capsule and the queue glyph, side by side. */
    private fun rowNeeds(rowWidth: Dp): Dp = 44.dp * 2 + modeSegmentWidth(rowWidth) * 3 + 2.dp

    @Test
    fun aRoomyRowKeepsFullSegments() {
        // A 411dp phone, less the player's 30dp gutters.
        assertEquals(64.dp, modeSegmentWidth(351.dp))
    }

    @Test
    fun aNarrowRowFitsBetweenTheGlyphs() {
        // A 720px phone at a larger display size: 320dp, less the gutters.
        val row = 260.dp
        assertTrue(rowNeeds(row) < row, "capsule overlaps the glyphs: needs ${rowNeeds(row)} of $row")
    }
}
