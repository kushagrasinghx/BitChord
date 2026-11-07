package com.music.bitchord

import com.music.bitchord.data.stats.ReplayPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ReplayMonthTest {

    private val today = LocalDate.of(2026, 11, 3)

    @Test
    fun `an earlier month is read as of its last day`() {
        val october = YearMonth.of(2026, 10)
        val asOf = ReplayPeriod.THIS_MONTH.asOf(october, today)
        assertEquals(LocalDate.of(2026, 10, 31), asOf)
        assertTrue(ReplayPeriod.THIS_MONTH.covers(october, asOf))
        assertFalse(ReplayPeriod.THIS_MONTH.covers(YearMonth.of(2026, 11), asOf))
        assertEquals(LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 10, 31), ReplayPeriod.THIS_MONTH.range(asOf))
        assertEquals("October 2026", ReplayPeriod.THIS_MONTH.label(asOf))
    }

    @Test
    fun `no month, the current month or a later one is read as of today`() {
        assertEquals(today, ReplayPeriod.THIS_MONTH.asOf(null, today))
        assertEquals(today, ReplayPeriod.THIS_MONTH.asOf(YearMonth.of(2026, 11), today))
        assertEquals(today, ReplayPeriod.THIS_MONTH.asOf(YearMonth.of(2026, 12), today))
    }

    @Test
    fun `other periods ignore the picked month`() {
        val october = YearMonth.of(2026, 10)
        listOf(ReplayPeriod.THIS_WEEK, ReplayPeriod.THIS_YEAR, ReplayPeriod.ALL_TIME).forEach {
            assertEquals(it.name, today, it.asOf(october, today))
        }
    }
}
