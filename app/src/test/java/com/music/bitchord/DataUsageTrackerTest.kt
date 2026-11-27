package com.music.bitchord

import com.music.bitchord.data.DataUsageTracker
import com.music.bitchord.data.UsageBreakdown
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DataUsageTrackerTest {

    @Test
    fun `aggregateSummary computes daily, 7-day, and 30-day totals accurately`() {
        val today = LocalDate.of(2026, 11, 27)
        val buckets = mapOf(
            "2026-11-27" to UsageBreakdown(streamingWifiBytes = 100, downloadMobileBytes = 50),
            "2026-11-22" to UsageBreakdown(streamingMobileBytes = 200),
            "2026-11-10" to UsageBreakdown(downloadWifiBytes = 400),
            "2026-10-01" to UsageBreakdown(streamingWifiBytes = 999),
        )

        val summary = DataUsageTracker.aggregateSummary(buckets, today)
        assertEquals(150L, summary.today.totalBytes)
        assertEquals(350L, summary.last7Days.totalBytes)
        assertEquals(750L, summary.last30Days.totalBytes)
        assertEquals(100L, summary.last30Days.streamingWifiBytes)
        assertEquals(200L, summary.last30Days.streamingMobileBytes)
        assertEquals(400L, summary.last30Days.downloadWifiBytes)
        assertEquals(50L, summary.last30Days.downloadMobileBytes)
    }

    @Test
    fun `formatBytes formats B, KB, MB, and GB cleanly`() {
        assertEquals("512 B", DataUsageTracker.formatBytes(512L))
        assertEquals("10 KB", DataUsageTracker.formatBytes(10L * 1024L))
        assertEquals("2.5 MB", DataUsageTracker.formatBytes((2.5 * 1024 * 1024).toLong()))
    }
}
