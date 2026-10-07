package com.music.bitchord

import com.music.bitchord.playback.autoplayQueueNeedsRefresh
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoplayFallbackTest {
    @Test
    fun `enabled autoplay refreshes when current track has no queue after it`() {
        assertTrue(
            autoplayQueueNeedsRefresh(
                enabled = true,
                repeatAll = false,
                currentIndex = 2,
                itemCount = 3,
                loadInProgress = false,
            ),
        )
    }

    @Test
    fun `an existing queue or active load does not start a duplicate refresh`() {
        assertFalse(autoplayQueueNeedsRefresh(true, false, 1, 3, false))
        assertFalse(autoplayQueueNeedsRefresh(true, false, 2, 3, true))
    }

    @Test
    fun `disabled autoplay repeat all and a missing current track do not refresh`() {
        assertFalse(autoplayQueueNeedsRefresh(false, false, 0, 1, false))
        assertFalse(autoplayQueueNeedsRefresh(true, true, 0, 1, false))
        assertFalse(autoplayQueueNeedsRefresh(true, false, -1, 0, false))
    }
}
