package com.music.bitchord.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-only regression coverage; deliberately not executed while builds are prohibited. */
class ExpandedPlayPauseMotionTest {
    @Test
    fun `resting symbol keeps its original scale opacity and sharpness`() {
        assertEquals(1f, expandedSymbolScale(1f), 0f)
        assertEquals(1f, expandedSymbolAlpha(1f), 0f)
        assertEquals(0f, expandedSymbolBlur(1f), 0f)
        assertEquals(0f, expandedSymbolAlpha(0f), 0f)
    }

    @Test
    fun `handoff retains a finite blurred symbol instead of a near-point morph`() {
        assertEquals(0.5f, expandedSymbolScale(0.5f), 0f)
        assertEquals(0.5f, expandedSymbolAlpha(0.5f), 0f)
        assertTrue(expandedSymbolBlur(0.5f) > 0f)
        assertTrue(expandedSymbolScale(0f) >= 0.35f)
    }

    @Test
    fun `both directions avoid blank frames and full-size double silhouettes`() {
        for (step in 0..1000) {
            val pause = step / 1000f
            val play = 1f - pause
            assertEquals(1f, expandedSymbolAlpha(play) + expandedSymbolAlpha(pause), 0.000001f)
            assertTrue(expandedSymbolScale(play) <= 0.5f || expandedSymbolScale(pause) <= 0.5f)
            assertTrue(expandedSymbolAlpha(play) in 0f..1f)
            assertTrue(expandedSymbolAlpha(pause) in 0f..1f)
        }
    }

    @Test
    fun `spring overshoot never resurrects the outgoing silhouette`() {
        assertEquals(0f, expandedSymbolAlpha(-0.05f), 0f)
        assertEquals(1f, expandedSymbolAlpha(1.05f), 0f)
        assertEquals(0f, expandedSymbolBlur(1.05f), 0f)
        assertEquals(1.05f, expandedSymbolScale(1.05f), 0f)
        assertEquals(1.08f, expandedSymbolScale(2f), 0f)
    }
}
