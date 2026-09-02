package com.music.bitchord

import com.music.bitchord.playback.MAX_QUEUE_HISTORY
import com.music.bitchord.playback.LastPlayed
import com.music.bitchord.playback.queueHistoryTrimCount
import com.music.bitchord.playback.queueStartingAt
import com.music.bitchord.playback.skippedByQueueJump
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueHistoryTest {

    @Test
    fun `history retains at most twenty five songs`() {
        assertEquals(0, queueHistoryTrimCount(MAX_QUEUE_HISTORY))
        assertEquals(1, queueHistoryTrimCount(MAX_QUEUE_HISTORY + 1))
        assertEquals(75, queueHistoryTrimCount(100))
    }

    @Test
    fun `a direct forward choice removes only bypassed songs`() {
        assertEquals(3..6, skippedByQueueJump(currentIndex = 2, targetIndex = 7))
    }

    @Test
    fun `next and previous navigation preserve the queue`() {
        assertNull(skippedByQueueJump(currentIndex = 2, targetIndex = 3))
        assertNull(skippedByQueueJump(currentIndex = 7, targetIndex = 2))
    }

    @Test
    fun `starting in the middle does not turn earlier unplayed rows into history`() {
        assertEquals(listOf("c", "d"), queueStartingAt(listOf("a", "b", "c", "d"), 2))
    }

    @Test
    fun `persisted queue window is bounded independently of live queue size`() {
        val window = LastPlayed.window(size = 10_000, index = 5_000)

        assertEquals(4_975, window.first)
        assertEquals(76, window.count())
    }

    @Test
    fun `persisted queue never restores more than twenty five history rows`() {
        val window = LastPlayed.window(size = 10_000, index = 9_999)

        assertEquals(9_974, window.first)
        assertEquals(26, window.count())
    }

}
