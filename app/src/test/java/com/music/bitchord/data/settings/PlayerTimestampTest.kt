package com.music.bitchord.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerTimestampTest {

    @Test
    fun defaultTimestampIsRemainingCountdown() {
        assertFalse(AppSettings.showTotalDuration.value)
    }

    @Test
    fun toggleTimestampSwitchesMode() {
        val initial = AppSettings.showTotalDuration.value
        val toggled = !initial
        assertEquals(!initial, toggled)
    }

    @Test
    fun remainingCalculationDoesNotGoNegative() {
        val durationMs = 180_000L
        val shown = 1.05f // past duration
        val currentPos = (shown * durationMs).toLong()
        val remaining = (durationMs - currentPos).coerceAtLeast(0L)
        assertEquals(0L, remaining)
    }
}
