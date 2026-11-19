package com.music.bitchord.data.smartoffline

import com.music.bitchord.data.stats.DayStats
import com.music.bitchord.data.stats.StoredBucket
import com.music.bitchord.data.stats.TrackEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The window, checked on its own.
 *
 * The buckets here are the same shape the listening history writes to disk, so
 * these cases are about what the index does with a month of real numbers:
 * what falls inside the window, what falls outside it, and what happens to a
 * track whose days were never recorded.
 */
class ActivityIndexTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 3, 15)
    private val now: Instant = today.atStartOfDay(zone).plusSeconds(12 * 3600).toInstant()

    @Test
    fun `no buckets means no candidates`() {
        assertTrue(ActivityIndex.build(emptyList(), 28, now, zone).isEmpty())
    }

    @Test
    fun `a window of no days means no candidates`() {
        val buckets = listOf(bucket("2026-03", track("a", plays = 1, day = today)))
        assertTrue(ActivityIndex.build(buckets, 0, now, zone).isEmpty())
    }

    @Test
    fun `a track heard today is a candidate`() {
        val buckets = listOf(bucket("2026-03", track("a", plays = 1, day = today)))
        val index = ActivityIndex.build(buckets, 28, now, zone)
        assertEquals(setOf("a"), index.keys)
    }

    @Test
    fun `a track heard inside the window is a candidate`() {
        val buckets = listOf(bucket("2026-03", track("a", plays = 2, day = today.minusDays(20))))
        val index = ActivityIndex.build(buckets, 28, now, zone)
        assertTrue(index.containsKey("a"))
    }

    @Test
    fun `a track heard only before the window is not a candidate`() {
        val buckets = listOf(bucket("2026-01", track("a", plays = 9, day = today.minusDays(60))))
        val index = ActivityIndex.build(buckets, 28, now, zone)
        assertTrue(index.isEmpty())
    }

    @Test
    fun `a day outside the window is not counted`() {
        val buckets = listOf(bucket("2026-03", track("a", plays = 1, day = today.minusDays(40))))
        val index = ActivityIndex.build(buckets, 28, now, zone)
        assertTrue(index.isEmpty())
    }

    @Test
    fun `the window includes today`() {
        val buckets = listOf(bucket("2026-03", track("a", plays = 1, day = today)))
        val index = ActivityIndex.build(buckets, 1, now, zone)
        assertTrue(index.containsKey("a"))
    }

    @Test
    fun `plays inside the window are counted`() {
        val buckets = listOf(bucket("2026-03", track("a", plays = 3, day = today.minusDays(2))))
        val index = ActivityIndex.build(buckets, 28, now, zone)
        assertEquals(3, index.getValue("a").playsInWindow)
    }

    @Test
    fun `minutes inside the window are counted`() {
        val buckets = listOf(bucket("2026-03", track("a", plays = 3, day = today.minusDays(2))))
        val index = ActivityIndex.build(buckets, 28, now, zone)
        assertEquals(3 * 60_000L, index.getValue("a").msInWindow)
    }

    @Test
    fun `days from another month are merged in`() {
        val buckets = listOf(
            bucket("2026-02", track("a", plays = 1, day = today.minusDays(25))),
            bucket("2026-03", track("a", plays = 2, day = today.minusDays(1))),
        )
        val index = ActivityIndex.build(buckets, 28, now, zone)
        assertEquals(3, index.getValue("a").playsInWindow)
    }

    @Test
    fun `the days of a track are kept by date`() {
        val buckets = listOf(bucket("2026-03", track("a", plays = 1, day = today.minusDays(3))))
        val index = ActivityIndex.build(buckets, 28, now, zone)
        assertTrue(index.getValue("a").days.containsKey(today.minusDays(3).toString()))
    }

    @Test
    fun `the last play is the newest one across the buckets`() {
        val buckets = listOf(
            bucket("2026-02", track("a", plays = 1, day = today.minusDays(25))),
            bucket("2026-03", track("a", plays = 1, day = today.minusDays(1))),
        )
        val index = ActivityIndex.build(buckets, 28, now, zone)
        val expected = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expected, index.getValue("a").lastPlayedAt)
    }

    @Test
    fun `the newest bucket's metadata is the one kept`() {
        val older = bucket("2026-02", track("a", plays = 1, day = today.minusDays(25), title = "Old"))
        val newer = bucket("2026-03", track("a", plays = 1, day = today.minusDays(1), title = "New"))
        val index = ActivityIndex.build(listOf(older, newer), 28, now, zone)
        assertEquals("New", index.getValue("a").title)
    }

    @Test
    fun `a blank title never overwrites one that was filled in`() {
        val older = bucket("2026-02", track("a", plays = 1, day = today.minusDays(25), title = "Named"))
        val newer = bucket("2026-03", track("a", plays = 1, day = today.minusDays(1), title = ""))
        val index = ActivityIndex.build(listOf(older, newer), 28, now, zone)
        assertEquals("Named", index.getValue("a").title)
    }

    @Test
    fun `the totals count every month on disk`() {
        val buckets = listOf(
            bucket("2026-02", track("a", plays = 1, day = today.minusDays(25))),
            bucket("2026-03", track("a", plays = 2, day = today.minusDays(1))),
        )
        val index = ActivityIndex.build(buckets, 28, now, zone)
        assertEquals(3, index.getValue("a").totalPlays)
    }

    @Test
    fun `a month that has not happened yet is ignored`() {
        val buckets = listOf(bucket("2026-09", track("a", plays = 5, day = today.plusDays(200))))
        val index = ActivityIndex.build(buckets, 28, now, zone)
        assertTrue(index.isEmpty())
    }

    @Test
    fun `a month that ended before the window is ignored`() {
        val buckets = listOf(bucket("2025-11", track("a", plays = 5, day = today.minusDays(120))))
        val index = ActivityIndex.build(buckets, 28, now, zone)
        assertTrue(index.isEmpty())
    }

    @Test
    fun `a track with no days is admitted when its last play is inside the window`() {
        val entry = TrackEntry(id = "a", title = "Old format", ms = 120_000L, plays = 2, last = millis(today.minusDays(2)))
        val index = ActivityIndex.build(listOf(StoredBucket(month = "2026-03", tracks = listOf(entry))), 28, now, zone)
        assertTrue(index.containsKey("a"))
        assertEquals(2, index.getValue("a").playsInWindow)
    }

    @Test
    fun `a track with no days is left out when its last play is outside the window`() {
        val entry = TrackEntry(id = "a", title = "Old format", ms = 120_000L, plays = 2, last = millis(today.minusDays(60)))
        val index = ActivityIndex.build(listOf(StoredBucket(month = "2026-03", tracks = listOf(entry))), 28, now, zone)
        assertTrue(index.isEmpty())
    }

    @Test
    fun `a track with no days keeps its totals`() {
        val entry = TrackEntry(id = "a", title = "Old format", ms = 120_000L, plays = 2, last = millis(today.minusDays(2)))
        val index = ActivityIndex.build(listOf(StoredBucket(month = "2026-03", tracks = listOf(entry))), 28, now, zone)
        assertEquals(2, index.getValue("a").totalPlays)
        assertEquals(120_000L, index.getValue("a").totalMs)
    }

    @Test
    fun `a track with no days has no days to offer`() {
        val entry = TrackEntry(id = "a", title = "Old format", ms = 120_000L, plays = 2, last = millis(today.minusDays(2)))
        val index = ActivityIndex.build(listOf(StoredBucket(month = "2026-03", tracks = listOf(entry))), 28, now, zone)
        assertTrue(index.getValue("a").days.isEmpty())
    }

    @Test
    fun `a blank id is not a candidate`() {
        val entry = TrackEntry(id = "", title = "Nothing", ms = 60_000L, plays = 1, last = millis(today))
        val index = ActivityIndex.build(listOf(StoredBucket(month = "2026-03", tracks = listOf(entry))), 28, now, zone)
        assertTrue(index.isEmpty())
    }

    @Test
    fun `a month that cannot be read is skipped rather than fatal`() {
        val buckets = listOf(
            StoredBucket(month = "not-a-month", tracks = listOf(track("a", plays = 1, day = today))),
            bucket("2026-03", track("b", plays = 1, day = today)),
        )
        val index = ActivityIndex.build(buckets, 28, now, zone)
        assertEquals(setOf("b"), index.keys)
    }

    @Test
    fun `a wider window keeps more tracks`() {
        val buckets = listOf(
            bucket("2026-03", track("recent", plays = 1, day = today.minusDays(2))),
            bucket("2026-01", track("older", plays = 1, day = today.minusDays(50))),
        )
        val narrow = ActivityIndex.build(buckets, 28, now, zone)
        val wide = ActivityIndex.build(buckets, 90, now, zone)
        assertEquals(setOf("recent"), narrow.keys)
        assertEquals(setOf("recent", "older"), wide.keys)
    }

    /** A month of history holding one track, played on one day. */
    private fun bucket(month: String, entry: TrackEntry): StoredBucket =
        StoredBucket(month = month, tracks = listOf(entry))

    /** A track played [plays] times on [day]. */
    private fun track(
        id: String,
        plays: Int,
        day: LocalDate,
        title: String = "Track",
    ): TrackEntry = TrackEntry(
        id = id,
        title = title,
        artist = "Artist",
        ms = plays * 60_000L,
        plays = plays,
        last = millis(day),
        days = mutableMapOf(day.toString() to DayStats(ms = plays * 60_000L, plays = plays)),
    )

    /** The epoch millis of a day, in the zone the tests count days in. */
    private fun millis(day: LocalDate): Long =
        day.atStartOfDay(zone).toInstant().toEpochMilli()
}
