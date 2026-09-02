package com.music.bitchord

import com.music.bitchord.playback.smart.AutomixAiRanker
import com.music.bitchord.playback.smart.TransitionPlan
import com.music.bitchord.playback.smart.TransitionStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AutomixAiRankerTest {
    @Test
    fun `AI uses the existing safe filter fallback for a rejected DJ blend`() {
        val fallback = AutomixAiRanker.conservative(
            TransitionPlan(transitionStyle = TransitionStyle.DJ_BLEND, bassSwap = true, filterSweep = 0.0),
        )
        assertEquals(TransitionStyle.DJ_FILTER, fallback.transitionStyle)
        assertFalse(fallback.bassSwap)
    }
}
