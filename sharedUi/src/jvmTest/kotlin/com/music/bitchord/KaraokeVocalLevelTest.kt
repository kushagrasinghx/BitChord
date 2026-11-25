package com.music.bitchord

import com.music.bitchord.ui.player.coerceKaraokeVocalLevel
import com.music.bitchord.ui.player.karaokeVocalPercent
import org.junit.Assert.assertEquals
import org.junit.Test

class KaraokeVocalLevelTest {
    @Test
    fun `zero level reads as zero percent, the maximum reduction`() {
        assertEquals(0, karaokeVocalPercent(0f))
    }

    @Test
    fun `one level reads as one hundred percent, the original mix`() {
        assertEquals(100, karaokeVocalPercent(1f))
    }

    @Test
    fun `mid level reads as fifty percent`() {
        assertEquals(50, karaokeVocalPercent(0.5f))
    }

    @Test
    fun `percent rounds to the nearest whole value`() {
        assertEquals(13, karaokeVocalPercent(0.125f))
        assertEquals(25, karaokeVocalPercent(0.254f))
    }

    @Test
    fun `levels below zero clamp to maximum reduction`() {
        assertEquals(0f, coerceKaraokeVocalLevel(-0.5f))
        assertEquals(0, karaokeVocalPercent(-0.5f))
    }

    @Test
    fun `levels above one clamp to the original mix`() {
        assertEquals(1f, coerceKaraokeVocalLevel(1.5f))
        assertEquals(100, karaokeVocalPercent(1.5f))
    }

    @Test
    fun `an unreadable level reads as the default, not as itself`() {
        // A slider event can hand a NaN through; the setting must never
        // store one, and the default — maximum reduction — is the value
        // that makes the first activation audible.
        assertEquals(0f, coerceKaraokeVocalLevel(Float.NaN))
        assertEquals(0, karaokeVocalPercent(Float.NaN))
    }

    @Test
    fun `in-range levels pass through unchanged`() {
        assertEquals(0.25f, coerceKaraokeVocalLevel(0.25f))
        assertEquals(1f, coerceKaraokeVocalLevel(1f))
        assertEquals(0f, coerceKaraokeVocalLevel(0f))
    }

    @Test
    fun `percent rises with the level across the range`() {
        var previous = Int.MIN_VALUE
        for (step in 0..20) {
            val percent = karaokeVocalPercent(step / 20f)
            assert(percent >= previous) { "percent fell at level ${step / 20f}" }
            previous = percent
        }
    }
}
