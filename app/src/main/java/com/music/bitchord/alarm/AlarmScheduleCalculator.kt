package com.music.bitchord.alarm

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Pure wall-clock recurrence calculation. */
object AlarmScheduleCalculator {

    fun nextOccurrence(
        config: AlarmConfig,
        now: Instant,
        zoneId: ZoneId,
    ): Instant? {
        if (!config.isReadyToSchedule()) return null

        val nowAtZone = now.atZone(zoneId)
        val localTime = LocalTime.of(config.hour, config.minute)

        if (config.repeatDays.isEmpty()) {
            val today = occurrence(nowAtZone.toLocalDate(), localTime, zoneId)
            return if (today.toInstant().isAfter(now)) {
                today.toInstant()
            } else {
                occurrence(nowAtZone.toLocalDate().plusDays(1), localTime, zoneId).toInstant()
            }
        }

        for (offset in 0L..7L) {
            val date = nowAtZone.toLocalDate().plusDays(offset)
            if (date.dayOfWeek.value !in config.repeatDays) continue
            val candidate = occurrence(date, localTime, zoneId).toInstant()
            if (candidate.isAfter(now)) return candidate
        }
        return null
    }

    /**
     * `atZone` moves a nonexistent spring-forward local time into the first valid
     * offset and chooses the earlier offset when autumn repeats a local time.
     */
    private fun occurrence(date: LocalDate, time: LocalTime, zoneId: ZoneId): ZonedDateTime =
        date.atTime(time).atZone(zoneId)
}
