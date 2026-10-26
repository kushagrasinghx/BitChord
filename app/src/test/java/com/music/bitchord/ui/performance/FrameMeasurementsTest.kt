package com.music.bitchord.ui.performance

import org.junit.Assert.*
import org.junit.Test

class FrameMeasurementsTest {
    private fun frame(at: Long = 10, window: String = "main", total: Long = 12_000_000, deadline: Long = 8_333_333,
                      first: Boolean = false, lost: Int = 0, gpu: Long = 2_000_000) = FrameSample(
        window, at, total, deadline, 4_000_000, gpu, 0, 1_000_000, 2_000_000, 0, 0, 0, 0, first, lost,
    )

    @Test
    fun `classification follows each frame deadline instead of a fixed refresh rate`() {
        val frames = FrameMeasurements(0, 100)
        frames.add(frame(at = 1, deadline = 16_666_667))
        frames.add(frame(at = 2, deadline = 8_333_333))
        frames.add(frame(at = 3, deadline = -1))
        assertEquals(3, frames.summary().frames)
        assertEquals(2, frames.summary().knownDeadlines)
        assertEquals(1, frames.summary().lateFrames)
    }

    @Test
    fun `first draws remain in raw data but do not inflate steady-frame latency`() {
        val frames = FrameMeasurements(0, 100)
        frames.add(frame(at = 1, total = 400_000_000, first = true, lost = 2))
        frames.add(frame(at = 2, total = 4_000_000, gpu = -1))
        val summary = frames.summary()
        assertEquals(2, summary.frames)
        assertEquals(1, summary.firstDraws)
        assertEquals(0, summary.lateFrames)
        assertEquals(4_000_000L, summary.totalP95Ns)
        assertNull(summary.gpuP95Ns)
        assertEquals(2L, summary.lostReports)
    }

    @Test
    fun `warmup and out-of-window frames are excluded and storage is bounded`() {
        val frames = FrameMeasurements(10, 20, capacity = 2)
        frames.add(frame(at = 9))
        frames.add(frame(at = 10))
        frames.add(frame(at = 11))
        frames.add(frame(at = 12))
        frames.add(frame(at = 20))
        assertEquals(listOf(10L, 11L), frames.frames.map { it.intendedNs })
        assertEquals(1, frames.overflow)
    }

    @Test
    fun `window cadence excludes idle gaps and does not mix different windows`() {
        val frames = FrameMeasurements(0, 1_000_000_000)
        frames.add(frame(at = 1, total = 1_000_000, lost = 12))
        frames.add(frame(at = 16_666_668, total = 1_000_000))
        frames.add(frame(at = 500_000_000, total = 1_000_000))
        frames.add(frame(at = 2, window = "nowPlaying", total = 1_000_000))
        frames.add(frame(at = 8_333_335, window = "nowPlaying", total = 1_000_000))
        assertEquals(16_666_667L, frames.summary("main").vsyncIntervalP50Ns)
        assertEquals(8_333_333L, frames.summary("nowPlaying").vsyncIntervalP50Ns)
        assertEquals(12L, frames.summary().lostReports)
        assertEquals(0, frames.summary().lateFrames)
    }

    @Test
    fun `repeat timing reports do not count extra frames and conflicting deadlines are unknown`() {
        val frames = FrameMeasurements(0, 100)
        frames.add(frame(at = 1, total = 101_000_000, deadline = 11_600_000, first = true))
        frames.add(frame(at = 1, total = 101_000_000, deadline = 119_500_000, first = true))
        frames.add(frame(at = 2, total = 40_000_000, deadline = 11_600_000, lost = 1))
        frames.add(frame(at = 2, total = 40_000_000, deadline = 53_150_950))
        val summary = frames.summary()
        assertEquals(4, frames.frames.size) // All platform reports remain available for audit.
        assertEquals(2, summary.frames)
        assertEquals(4, summary.reportedFrames)
        assertEquals(2, summary.duplicateReports)
        assertEquals(2, summary.conflictingDeadlineFrames)
        assertEquals(1, summary.firstDraws)
        assertEquals(0, summary.knownDeadlines)
        assertFalse(summary.completeDeadlines)
        assertEquals(1L, summary.lostReports)
        assertEquals(40_000_000L, summary.totalP95Ns)
    }

    @Test
    fun `sentinel-like or multi-second budgets do not imply a healthy frame rate`() {
        val frames = FrameMeasurements(0, 100)
        frames.add(frame(at = 1, deadline = Long.MAX_VALUE - 1_821_000_000_000_000L))
        frames.add(frame(at = 2, deadline = 4_313_270_261))
        frames.add(frame(at = 3, deadline = 8_333_333))
        val summary = frames.summary()
        assertEquals(2, summary.invalidDeadlineFrames)
        assertEquals(1, summary.knownDeadlines)
        assertFalse(summary.completeDeadlines)
        assertNull(summary.deadlineP50Ns)
        assertEquals(1, summary.lateFrames)
    }

    @Test
    fun `distinct timing samples with the same intended timestamp are retained`() {
        val frames = FrameMeasurements(0, 100)
        frames.add(frame(at = 1, total = 12_000_000))
        frames.add(frame(at = 1, total = 14_000_000))
        assertEquals(2, frames.summary().frames)
        assertEquals(0, frames.summary().duplicateReports)
        assertTrue(frames.summary().completeDeadlines)
    }
}
