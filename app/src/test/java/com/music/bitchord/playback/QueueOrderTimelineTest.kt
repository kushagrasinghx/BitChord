package com.music.bitchord.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueOrderTimelineTest {

    /**
     * Three windows whose *shuffled* order is 2, 0, 1 — standing in for
     * ExoPlayer's unused random shuffle order while the app's own shuffle
     * (which reorders the queue itself) is reported as on.
     */
    private class ShuffledTimeline : Timeline() {
        private val shuffled = intArrayOf(2, 0, 1)

        override fun getWindowCount() = 3
        override fun getPeriodCount() = 3

        override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window =
            window.set(
                windowIndex, MediaItem.Builder().setMediaId("w$windowIndex").build(), null,
                C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET, false, false, null,
                0L, C.TIME_UNSET, windowIndex, windowIndex, 0L,
            )

        override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period =
            period.set(periodIndex, periodIndex, periodIndex, C.TIME_UNSET, 0L)

        override fun getIndexOfPeriod(uid: Any) = (uid as? Int) ?: C.INDEX_UNSET
        override fun getUidOfPeriod(periodIndex: Int): Any = periodIndex

        override fun getNextWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int {
            if (!shuffleModeEnabled) return super.getNextWindowIndex(windowIndex, repeatMode, false)
            val at = shuffled.indexOf(windowIndex)
            return if (at + 1 < shuffled.size) shuffled[at + 1] else C.INDEX_UNSET
        }

        override fun getPreviousWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int {
            if (!shuffleModeEnabled) return super.getPreviousWindowIndex(windowIndex, repeatMode, false)
            val at = shuffled.indexOf(windowIndex)
            return if (at > 0) shuffled[at - 1] else C.INDEX_UNSET
        }

        override fun getFirstWindowIndex(shuffleModeEnabled: Boolean) = if (shuffleModeEnabled) shuffled.first() else 0
        override fun getLastWindowIndex(shuffleModeEnabled: Boolean) = if (shuffleModeEnabled) shuffled.last() else 2
    }

    @Test
    fun `next and previous follow the queue order even with shuffle reported on`() {
        val raw = ShuffledTimeline()
        // The raw timeline would say window 2 has a next (0) and window 1 has none.
        assertEquals(0, raw.getNextWindowIndex(2, Player.REPEAT_MODE_OFF, true))
        assertEquals(C.INDEX_UNSET, raw.getNextWindowIndex(1, Player.REPEAT_MODE_OFF, true))

        val ordered = QueueOrderTimeline(raw)
        assertEquals(2, ordered.getNextWindowIndex(1, Player.REPEAT_MODE_OFF, true))
        assertEquals(C.INDEX_UNSET, ordered.getNextWindowIndex(2, Player.REPEAT_MODE_OFF, true))
        assertEquals(0, ordered.getPreviousWindowIndex(1, Player.REPEAT_MODE_OFF, true))
        assertEquals(0, ordered.getFirstWindowIndex(true))
        assertEquals(2, ordered.getLastWindowIndex(true))
        assertEquals(0, ordered.getNextWindowIndex(2, Player.REPEAT_MODE_ALL, true))
    }
}
