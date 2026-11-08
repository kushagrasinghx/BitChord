package com.music.bitchord.ui.festive

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiwaliGateTest {
    private val ist = TimeZone.getTimeZone("Asia/Kolkata")

    private fun ist(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Long =
        Calendar.getInstance(ist).apply {
            clear()
            set(year, month, day, hour, minute, second)
        }.timeInMillis

    @Test
    fun `is Diwali from midnight to midnight IST on 8 November 2026`() {
        assertTrue(DiwaliGate.isDiwaliDay(ist(2026, Calendar.NOVEMBER, 8, 0, 0)))
        assertTrue(DiwaliGate.isDiwaliDay(ist(2026, Calendar.NOVEMBER, 8, 12, 0)))
        assertTrue(DiwaliGate.isDiwaliDay(ist(2026, Calendar.NOVEMBER, 8, 23, 59, 59)))
    }

    @Test
    fun `is not Diwali either side of the day`() {
        assertFalse(DiwaliGate.isDiwaliDay(ist(2026, Calendar.NOVEMBER, 7, 23, 59, 59)))
        assertFalse(DiwaliGate.isDiwaliDay(ist(2026, Calendar.NOVEMBER, 9, 0, 0)))
        assertFalse(DiwaliGate.isDiwaliDay(ist(2026, Calendar.NOVEMBER, 6, 12, 0)))
        assertFalse(DiwaliGate.isDiwaliDay(ist(2027, Calendar.NOVEMBER, 8, 12, 0)))
    }

    @Test
    fun `the day is IST's whatever the device zone`() {
        // 7 Nov 20:00 UTC is already 8 Nov 01:30 in India.
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 7, 20, 0)
        }.timeInMillis
        assertTrue(DiwaliGate.isDiwaliDay(utc))
    }

    @Test
    fun `India is read from the timezone alone`() {
        assertTrue(DiwaliGate.isIndianTimezone(TimeZone.getTimeZone("Asia/Kolkata")))
        assertTrue(DiwaliGate.isIndianTimezone(TimeZone.getTimeZone("Asia/Calcutta")))
        assertFalse(DiwaliGate.isIndianTimezone(TimeZone.getTimeZone("Asia/Dubai")))
        assertFalse(DiwaliGate.isIndianTimezone(TimeZone.getTimeZone("America/New_York")))
        assertFalse(DiwaliGate.isIndianTimezone(TimeZone.getTimeZone("Asia/Kathmandu")))
    }
}
