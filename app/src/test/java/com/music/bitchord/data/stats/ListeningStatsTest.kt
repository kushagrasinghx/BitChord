package com.music.bitchord.data.stats

import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The weekly Replay reads day-level numbers off several buckets, and the day a
 * bucket is keyed by is recomputed on the way in rather than read back. A key a
 * parser no longer accepts vanishes silently, so the tests attach the merge to
 * a seeded folder and read out what survived.
 */
class ListeningStatsTest {

    /** The stored shape of one month, encoded the way the app writes it. */
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Points the singleton at [dir] the way [ListeningStats.init] would, and
     * invalidates any summary the previous test may have cached — the cache is
     * keyed by period, version and day, not by folder, so a re-pointed folder
     * alone would still answer with the last folder's numbers.
     */
    private fun pointAt(dir: File) {
        val field = ListeningStats::class.java.getDeclaredField("directory")
        field.isAccessible = true
        field.set(ListeningStats, dir)
        ListeningStats::class.java.getDeclaredField("cached").also {
            it.isAccessible = true
            it.set(ListeningStats, null)
        }
        ListeningStats::class.java.getDeclaredField("version").also {
            it.isAccessible = true
            it.setLong(ListeningStats, it.getLong(null) + 1)
        }
    }

    /** A bucket with listening on one track, artist and album, over some days. */
    private fun bucketFor(days: List<LocalDate>): StoredBucket {
        val track = TrackEntry(
            id = "video-1",
            title = "Track",
            artist = "Artist",
            album = "Album",
            albumId = "album-1",
            artistId = "artist-1",
            art = "green",
        )
        val artist = NameEntry(name = "Artist", art = "green", id = "artist-1")
        val album = NameEntry(name = "Album", sub = "Artist", art = "green", id = "album-1")
        val dayHours = mutableMapOf<Int, Long>()
        val dayTotals = mutableMapOf<String, DayStats>()
        days.forEach { day ->
            val dayKey = day.toString()
            val stats = DayStats(ms = 5_000, plays = 2)
            track.days[dayKey] = stats
            artist.days[dayKey] = stats
            album.days[dayKey] = stats
            dayHours[day.dayOfMonth] = 5_000L
            dayTotals[dayKey] = DayStats(ms = 5_000, plays = 2)
        }
        return StoredBucket(
            month = YearMonth.from(days.first()).toString(),
            tracks = listOf(track),
            artists = listOf(artist),
            albums = listOf(album),
            hours = List(24) { 0L },
            days = dayHours,
            trackDays = dayTotals,
            artistDays = dayTotals,
            albumDays = dayTotals,
        )
    }

    private fun seed(dir: File, bucket: StoredBucket) {
        dir.mkdirs()
        File(dir, "${bucket.month}.json").writeText(
            json.encodeToString(StoredBucket.serializer(), bucket),
        )
    }

    /**
     * The weekly merge once accepted `2026/11/14` for a key it builds as
     * `2026-11-14` and then parsed the two as though they agreed, so every
     * day-level figure fell out of the weekly numbers.
     */
    @Test
    fun `weekly summary keeps day-level listening from the current month`() = runTest {
        val dir = Files.createTempDirectory("listening").toFile()
        pointAt(dir)
        val today = LocalDate.now()
        seed(dir, bucketFor(listOf(today)))

        val summary = ListeningStats.summary(ReplayPeriod.THIS_WEEK)

        assertEquals(today.toString(), summary.busiestDay)
        assertEquals(5_000L, summary.busiestDayMs)
        assertEquals(1, summary.distinctSongs)
        assertEquals(1, summary.distinctArtists)
        assertEquals(1, summary.distinctAlbums)
        assertEquals(5_000L, summary.totalMs)
        assertEquals(2, summary.totalPlays)
    }

    /**
     * The week may span two months — [ListeningStats.summary] reads both, and
     * each of them needs its day keys built under its own month name. Built
     * around the range the period returns, so the test holds on any weekday.
     */
    /**
     * The week may span two months — [ListeningStats.summary] reads both, and
     * each of them must key its days under its own month's name. Built around
     * the range the period returns, so the test holds on any weekday.
     */
    @Test
    fun `weekly summary keeps day-level listening across a two-month week`() = runTest {
        val dir = Files.createTempDirectory("listening").toFile()
        pointAt(dir)
        val today = LocalDate.now()
        val (start, end) = ReplayPeriod.THIS_WEEK.range(today)!!
        val days = if (start == end) listOf(start) else listOf(start, end)
        // One file per month, the way real listening looks: both endpoints of
        // the week are inside the range by construction, so both must survive.
        days.groupBy { YearMonth.from(it) }.forEach { (_, monthDays) ->
            seed(dir, bucketFor(monthDays))
        }

        val summary = ListeningStats.summary(ReplayPeriod.THIS_WEEK)

        assertEquals(5_000L * days.size, summary.totalMs)
        assertEquals(2 * days.size, summary.totalPlays)
        assertEquals(5_000L, summary.busiestDayMs)
        assertTrue(days.map { it.toString() }.contains(summary.busiestDay))
    }

    /** A month that cannot be parsed is dropped by [ListeningStats.read], not by the page. */
    @Test
    fun `unreadable month is skipped rather than thrown`() = runTest {
        val dir = Files.createTempDirectory("listening").toFile()
        pointAt(dir)
        val today = LocalDate.now()
        val other = YearMonth.from(today).minusMonths(1)
        seed(dir, bucketFor(listOf(today)))
        // A bucket that failed on the way into a previous build, under a month
        // name the listing still accepts.
        val damaged = File(dir, "$other.json")
        damaged.parentFile.mkdirs()
        damaged.writeText("{corrupt")

        val summary = ListeningStats.summary(ReplayPeriod.THIS_WEEK)

        // The corrupt month is gone, the readable one is whole.
        assertEquals(today.toString(), summary.busiestDay)
        assertEquals(5_000L, summary.busiestDayMs)
        assertEquals(1, summary.distinctSongs)
    }
}
