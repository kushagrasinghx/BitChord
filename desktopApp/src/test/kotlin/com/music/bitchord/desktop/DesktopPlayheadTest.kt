package com.music.bitchord.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopPlayheadTest {
    private val rate = 48_000
    private val usPerFrame = 1_000_000.0 / rate

    @Test
    fun `plays forward from the anchor at the listener's speed`() {
        val playhead = DesktopPlayhead().apply { reset(frame = 1_000, sourceUs = 30_000_000, usPerFrame = usPerFrame) }
        assertEquals(30_000_000, playhead.at(1_000))
        assertEquals(31_000_000, playhead.at(1_000 + rate.toLong()))
    }

    @Test
    fun `a speed change does not rescale what was already heard`() {
        val playhead = DesktopPlayhead().apply { reset(0, 0, usPerFrame) }
        // Two minutes at 1x, then 1.5x from the next frame written.
        val twoMinutes = 120L * rate
        playhead.change(twoMinutes, usPerFrame * 1.5)
        assertEquals(120_000_000, playhead.at(twoMinutes))
        assertEquals(121_500_000, playhead.at(twoMinutes + rate))
    }

    @Test
    fun `a change waits for the queued audio ahead of it to be heard`() {
        val playhead = DesktopPlayhead().apply { reset(0, 0, usPerFrame) }
        // Written at frame 9,600 while only 0 have played: the 200ms queued before it is still 1x.
        playhead.change(9_600, usPerFrame * 2)
        assertEquals(100_000, playhead.at(4_800))
        assertEquals(200_000, playhead.at(9_600))
        assertEquals(300_000, playhead.at(9_600 + 2_400))
    }

    @Test
    fun `skipped silence moves the position on where it was cut`() {
        val playhead = DesktopPlayhead().apply { reset(0, 0, usPerFrame) }
        playhead.change(rate.toLong(), usPerFrame, skippedUs = 2_000_000.0)
        assertEquals(999_979, playhead.at(rate - 1L))
        assertEquals(3_000_000, playhead.at(rate.toLong()))
    }

    @Test
    fun `a reset forgets every segment before it`() {
        val playhead = DesktopPlayhead().apply { reset(0, 0, usPerFrame) }
        playhead.change(10_000, usPerFrame * 2, skippedUs = 5_000_000.0)
        playhead.reset(20_000, 60_000_000, usPerFrame)
        assertEquals(60_000_000, playhead.at(20_000))
        assertEquals(61_000_000, playhead.at(20_000 + rate.toLong()))
    }
}
