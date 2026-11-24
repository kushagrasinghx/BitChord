package com.music.bitchord.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptionShadeTest {
    @Test
    fun darkOrUnknownCoverGetsNoExtraShade() {
        assertEquals(0f, captionShadeStrength(null), 0.0001f)
        assertEquals(0f, captionShadeStrength(0f), 0.0001f)
    }

    @Test
    fun shadeGrowsWithLuminanceAndClampsAtFull() {
        assertEquals(0.5f, captionShadeStrength(0.3f), 0.0001f)
        assertEquals(1f, captionShadeStrength(0.6f), 0.0001f)
        assertEquals(1f, captionShadeStrength(1f), 0.0001f)
    }
}
