package com.music.bitchord.data.smartoffline

import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.StoredBucket
import com.music.bitchord.data.stats.TrackEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * What this device listened to in a recent window, read out of [ListeningStats].
 *
 * ## Read-only, and nothing more
 *
 * [ListeningStats] keeps its numbers as monthly buckets — one file per calendar
 * month, each holding a total per track and a per-day breakdown. This reads
 * them and answers the one question the ranking asks: *what did the user
 * listen to in the last N days*. It adds no second store and changes nothing
 * about how the numbers are kept.
 *
 * ## Why an index at all
 *
 * [ListeningStats.summary] answers "the top songs of a period" for the Replay
 * page, and it answers it by merging buckets. The ranking wants those same
 * numbers per track but with the **days left in**, because recency and
 * frequency are both measured against days. Merging months into a total throws
 * the days away, and the days are the part that says *when*.
 *
 * ## What it is not
 *
 * There is no play-event log anywhere in the app — [ListeningStats] says so
 * itself — so the finest thing this can say about *when* a track was heard is
 * the day it was heard on, and the one exact timestamp it keeps is the last
 * play. Everything here is built on that, and nothing here pretends to more.
 */
data class TrackActivity(
    /** The resolved id the listening history is keyed by — a source track id. */
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    /** Plays counted inside the window. */
    val playsInWindow: Int,
    /** Milliseconds actually listened to inside the window. */
    val msInWindow: Long,
    /** The days inside the window, keyed "YYYY-MM-DD". */
    val days: Map<String, DailyActivity>,
    /** Epoch millis of the last play, whatever window it fell in. 0 when never. */
    val lastPlayedAt: Long,
    /** Plays across every month on disk — context, never the ranking's driver. */
    val totalPlays: Int,
    /** Milliseconds across every month on disk. */
    val totalMs: Long,
)

/** One day of listening. Immutable, so an index can be handed around safely. */
data class DailyActivity(val ms: Long, val plays: Int)

/**
 * Reads the listening history into a windowed, per-track view.
 *
 * Two entry points, on purpose:
 *
 * - [load] is the one the app calls. It reads the buckets off disk and does
 *   the merging on the IO dispatcher.
 * - [build] is the one the tests call. It takes buckets already in hand and
 *   does nothing but arithmetic, so the window logic can be checked without a
 *   device, a context, or a file.
 */
object ActivityIndex {

    /**
     * The listening history of the last [windowDays] days, keyed by track id.
     *
     * @param windowDays how many days back to look, counting today as one.
     */
    suspend fun load(
        windowDays: Int,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<String, TrackActivity> = withContext(Dispatchers.IO) {
        build(ListeningStats.exportAll(), windowDays, now, zone)
    }

    /**
     * The same view, from buckets already read.
     *
     * A track is a candidate when it was heard inside the window — a play
     * counted, or minutes listened. A track that has not been heard in the
     * window is not a candidate at all, however much it was played before it:
     * the window is the question, and everything outside it is context.
     */
    fun build(
        buckets: List<StoredBucket>,
        windowDays: Int,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<String, TrackActivity> {
        if (windowDays <= 0 || buckets.isEmpty()) return emptyMap()

        val today = now.atZone(zone).toLocalDate()
        val windowStart = today.minusDays((windowDays - 1).toLong())
        val windowStartMillis = windowStart.atStartOfDay(zone).toInstant().toEpochMilli()

        val accumulated = LinkedHashMap<String, Accumulator>()
        for (bucket in buckets) {
            val month = runCatching { YearMonth.parse(bucket.month) }.getOrNull() ?: continue
            // A month that ended before the window opened, or that has not
            // started yet, holds nothing the window can see.
            if (month.atEndOfMonth().isBefore(windowStart)) continue
            if (month.atDay(1).isAfter(today)) continue
            for (entry in bucket.tracks) {
                if (entry.id.isBlank()) continue
                accumulated.getOrPut(entry.id) { Accumulator() }
                    .absorb(entry, windowStart, today, windowStartMillis)
            }
        }

        return accumulated
            .filterValues { it.playsInWindow > 0 || it.msInWindow > 0 }
            .mapValues { (id, acc) -> acc.toActivity(id) }
    }

    /**
     * The running totals for one track, as buckets are read.
     *
     * Buckets arrive oldest first, so a later bucket's metadata is the newer
     * one and wins; blank fields never overwrite a field that was filled in.
     */
    private class Accumulator {
        var title = ""
        var artist = ""
        var album: String? = null
        var artworkUrl: String? = null
        var playsInWindow = 0
        var msInWindow = 0L
        var lastPlayedAt = 0L
        var totalPlays = 0
        var totalMs = 0L
        val days = LinkedHashMap<String, DailyActivity>()

        fun absorb(
            entry: com.music.bitchord.data.stats.TrackEntry,
            windowStart: LocalDate,
            today: LocalDate,
            windowStartMillis: Long,
        ) {
            if (entry.title.isNotBlank()) title = entry.title
            if (entry.artist.isNotBlank()) artist = entry.artist
            if (!entry.album.isNullOrBlank()) album = entry.album
            if (!entry.art.isNullOrBlank()) artworkUrl = entry.art

            totalPlays += entry.plays
            totalMs += entry.ms
            if (entry.last > lastPlayedAt) lastPlayedAt = entry.last

            if (entry.days.isEmpty()) {
                // Rows written before the per-day breakdown existed carry only
                // the month's totals. The one thing that can still say whether
                // any of it fell inside the window is the last play, so that is
                // what decides it — and the whole month is counted, which
                // over-counts a boundary month rather than inventing precision.
                if (entry.last >= windowStartMillis) {
                    playsInWindow += entry.plays
                    msInWindow += entry.ms
                }
                return
            }

            for ((key, day) in entry.days) {
                val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: continue
                if (date.isBefore(windowStart) || date.isAfter(today)) continue
                playsInWindow += day.plays
                msInWindow += day.ms
                val existing = days[key]
                days[key] = DailyActivity(
                    ms = (existing?.ms ?: 0L) + day.ms,
                    plays = (existing?.plays ?: 0) + day.plays,
                )
            }
        }

        fun toActivity(id: String) = TrackActivity(
            id = id,
            title = title.ifBlank { id },
            artist = artist,
            album = album,
            artworkUrl = artworkUrl,
            playsInWindow = playsInWindow,
            msInWindow = msInWindow,
            days = days.toSortedMap(),
            lastPlayedAt = lastPlayedAt,
            totalPlays = totalPlays,
            totalMs = totalMs,
        )
    }
}
