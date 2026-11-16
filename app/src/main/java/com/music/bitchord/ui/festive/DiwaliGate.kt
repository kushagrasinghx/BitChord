package com.music.bitchord.ui.festive

import java.util.Calendar
import java.util.TimeZone

/**
 * Who sees Diwali, and when — read off the device's own clock settings only.
 *
 * India is the device timezone alone: no SIM, carrier, ISP or IP lookup. India
 * has a single zone, and a phone set to it is a phone in India (or one whose
 * owner keeps it on home time, which is just as good an audience).
 */
object DiwaliGate {
    /** Both spellings: "Asia/Calcutta" is the legacy tz id older devices still report. */
    private val INDIA_ZONES = setOf("Asia/Kolkata", "Asia/Calcutta")

    private val IST: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    /** Lakshmi Puja, 2026. */
    private const val YEAR = 2026
    private const val MONTH = Calendar.NOVEMBER
    private const val DAY = 8

    fun isIndianTimezone(zone: TimeZone = TimeZone.getDefault()): Boolean = zone.id in INDIA_ZONES

    /** Whether it is Diwali in India right now, midnight to midnight IST. */
    fun isDiwaliDay(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val c = Calendar.getInstance(IST).apply { timeInMillis = nowMillis }
        return c.get(Calendar.YEAR) == YEAR &&
            c.get(Calendar.MONTH) == MONTH &&
            c.get(Calendar.DAY_OF_MONTH) == DAY
    }
}
