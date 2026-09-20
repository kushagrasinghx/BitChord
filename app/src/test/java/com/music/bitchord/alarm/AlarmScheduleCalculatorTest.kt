package com.music.bitchord.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class AlarmScheduleCalculatorTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun `one-shot later today uses today`() {
        val next = next(alarm(hour = 9), "2026-09-20T08:00:00Z", utc)
        assertEquals(Instant.parse("2026-09-20T09:00:00Z"), next)
    }

    @Test
    fun `one-shot time already passed uses tomorrow`() {
        val next = next(alarm(hour = 7), "2026-09-20T08:00:00Z", utc)
        assertEquals(Instant.parse("2026-09-21T07:00:00Z"), next)
    }

    @Test
    fun `one-shot exact current minute never schedules in the past`() {
        val next = next(alarm(hour = 8), "2026-09-20T08:00:00Z", utc)
        assertEquals(Instant.parse("2026-09-21T08:00:00Z"), next)
    }

    @Test
    fun `recurring alarm today before time uses today`() {
        val next = next(alarm(hour = 9, days = setOf(7)), "2026-09-20T08:00:00Z", utc)
        assertEquals(Instant.parse("2026-09-20T09:00:00Z"), next)
    }

    @Test
    fun `recurring alarm today after time uses next selected weekday`() {
        val next = next(alarm(hour = 7, days = setOf(7, 2)), "2026-09-20T08:00:00Z", utc)
        assertEquals(Instant.parse("2026-09-22T07:00:00Z"), next)
    }

    @Test
    fun `single selected weekday wraps one week`() {
        val next = next(alarm(hour = 7, days = setOf(7)), "2026-09-20T08:00:00Z", utc)
        assertEquals(Instant.parse("2026-09-27T07:00:00Z"), next)
    }

    @Test
    fun `multiple weekdays choose the earliest future occurrence`() {
        val next = next(alarm(hour = 6, days = setOf(1, 3, 5)), "2026-09-22T18:00:00Z", utc)
        assertEquals(Instant.parse("2026-09-23T06:00:00Z"), next)
    }

    @Test
    fun `spring gap advances to the first valid local time`() {
        val paris = ZoneId.of("Europe/Paris")
        val next = next(alarm(hour = 2, minute = 30, days = setOf(7)), "2026-03-28T12:00:00Z", paris)
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), next)
    }

    @Test
    fun `autumn overlap chooses the earlier offset and one occurrence`() {
        val paris = ZoneId.of("Europe/Paris")
        val next = next(alarm(hour = 2, minute = 30, days = setOf(7)), "2026-10-24T12:00:00Z", paris)
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), next)
    }

    @Test
    fun `timezone recalculation preserves local wall clock`() {
        val config = alarm(hour = 7)
        val now = Instant.parse("2026-09-20T00:00:00Z")
        assertEquals(Instant.parse("2026-09-20T07:00:00Z"), AlarmScheduleCalculator.nextOccurrence(config, now, utc))
        assertEquals(
            Instant.parse("2026-09-20T05:00:00Z"),
            AlarmScheduleCalculator.nextOccurrence(config, now, ZoneId.of("Europe/Paris")),
        )
    }

    @Test
    fun `invalid configuration is not scheduled`() {
        assertNull(
            AlarmScheduleCalculator.nextOccurrence(
                alarm(hour = 25),
                Instant.parse("2026-09-20T00:00:00Z"),
                utc,
            ),
        )
    }

    @Test
    fun `disabled alarm is not scheduled`() {
        assertNull(
            AlarmScheduleCalculator.nextOccurrence(
                alarm(hour = 7).copy(enabled = false),
                Instant.parse("2026-09-20T00:00:00Z"),
                utc,
            ),
        )
    }

    private fun alarm(
        hour: Int,
        minute: Int = 0,
        days: Set<Int> = emptySet(),
    ) = AlarmConfig(
        enabled = true,
        hour = hour,
        minute = minute,
        repeatDays = days,
        playlistId = "PL123",
        playlistTitle = "Morning",
    )

    private fun next(config: AlarmConfig, now: String, zoneId: ZoneId): Instant? =
        AlarmScheduleCalculator.nextOccurrence(config, Instant.parse(now), zoneId)
}
