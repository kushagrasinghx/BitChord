package com.music.bitchord.data.stats

import com.music.bitchord.data.model.Song
import kotlinx.serialization.Serializable

/*
 * The shapes of a month of listening, as ListeningStats keeps it on the phone and
 * the web player keeps it in IndexedDB. Here rather than beside ListeningStats so
 * both write the same JSON: a month moves between them (Drive sync) as these bytes.
 */

/** Daily-level listening for a single track or artist. */
@Serializable
public data class DayStats(
    var ms: Long = 0L,
    var plays: Int = 0,
)

/** One track's totals inside a bucket. */
@Serializable
data class TrackEntry(
    val id: String,
    val title: String = "",
    val artist: String = "",
    var album: String? = null,
    var albumId: String? = null,
    var artistId: String? = null,
    var art: String? = null,
    var ms: Long = 0L,
    var plays: Int = 0,
    var last: Long = 0L,
    /** Per-day breakdown for weekly queries. Key: "YYYY-MM-DD". */
    var days: MutableMap<String, DayStats> = mutableMapOf(),
) {
    fun absorb(other: TrackEntry) {
        ms += other.ms
        plays += other.plays
        last = maxOf(last, other.last)
        if (album == null) album = other.album
        if (albumId == null) albumId = other.albumId
        if (artistId == null) artistId = other.artistId
        if (art == null) art = other.art
        // Merge per-day sub-buckets.
        if (other.days.isNotEmpty()) {
            other.days.forEach { (dateKey, stats) ->
                days.getOrPut(dateKey) { DayStats() }.also { existing ->
                    existing.ms += stats.ms
                    existing.plays += stats.plays
                }
            }
        }
    }

    /**
     * Sum ms/plays for days [inRange] says are in the week. Falls back to full totals if .days is empty.
     *
     * The week is a predicate on the `YYYY-MM-DD` key rather than two dates because
     * this file has no calendar: the phone answers it with java.time, the web with
     * its own dates, and an unreadable key is simply not in it.
     */
    fun weekRange(inRange: (String) -> Boolean): Pair<Long, Int> {
        var totalMs = 0L
        var totalPlays = 0
        if (days.isNotEmpty()) {
            for ((dateStr, stats) in days) {
                if (inRange(dateStr)) {
                    totalMs += stats.ms
                    totalPlays += stats.plays
                }
            }
        } else {
            // Legacy data: .days was never populated — use overall totals.
            return ms to plays
        }
        return totalMs to totalPlays
    }
}

/**
 * One artist's or album's totals.
 *
 * [key] is the map key it was stored under. Written for a reader's benefit and
 * for anything downstream of an export; it is deliberately *not* read back.
 *
 * Reading it was the original design, on the reasoning that a stored key cannot
 * be spelt differently by a later build. What that actually buys is the
 * opposite: an entry keyed by an older build keeps its old key forever, so the
 * same artist ends up in two rows the moment the spelling changes and there is
 * no way back. Recomputing on load means a change heals itself — see
 * [OpenBucket.of] and the merge it does on the way in.
 */
@Serializable
data class NameEntry(
    val name: String = "",
    val sub: String? = null,
    var art: String? = null,
    var id: String? = null,
    var ms: Long = 0L,
    var plays: Int = 0,
    val key: String? = null,
    /** Per-day breakdown for weekly queries. Key: "YYYY-MM-DD". */
    var days: MutableMap<String, DayStats> = mutableMapOf(),
) {
    fun absorb(other: NameEntry) {
        ms += other.ms
        plays += other.plays
        if (art == null) art = other.art
        if (id == null) id = other.id
        // Merge per-day sub-buckets.
        if (other.days.isNotEmpty()) {
            other.days.forEach { (dateKey, stats) ->
                days.getOrPut(dateKey) { DayStats() }.also { existing ->
                    existing.ms += stats.ms
                    existing.plays += stats.plays
                }
            }
        }
    }

    /** As [TrackEntry.weekRange]. */
    fun weekRange(inRange: (String) -> Boolean): Pair<Long, Int> {
        var totalMs = 0L
        var totalPlays = 0
        if (days.isNotEmpty()) {
            for ((dateStr, stats) in days) {
                if (inRange(dateStr)) {
                    totalMs += stats.ms
                    totalPlays += stats.plays
                }
            }
        } else {
            // Legacy data: .days was never populated — use overall totals.
            return ms to plays
        }
        return totalMs to totalPlays
    }
}

/** One calendar month on disk. */
@Serializable
data class StoredBucket(
    val version: Int = 1,
    val month: String,
    val tracks: List<TrackEntry> = emptyList(),
    val artists: List<NameEntry> = emptyList(),
    val albums: List<NameEntry> = emptyList(),
    /** Milliseconds played per hour of the day, 0..23. */
    val hours: List<Long> = List(24) { 0L },
    /** Milliseconds played per day of the month. */
    val days: Map<Int, Long> = emptyMap(),
    /** Per-day sub-buckets for tracks. Key: "YYYY-MM-DD". */
    val trackDays: Map<String, DayStats>? = null,
    /** Per-day sub-buckets for artists. Key: "YYYY-MM-DD". */
    val artistDays: Map<String, DayStats>? = null,
    /** Per-day sub-buckets for albums. Key: "YYYY-MM-DD". */
    val albumDays: Map<String, DayStats>? = null,
)

/** A row on one of the four charts. */
data class RankedEntry(
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    val browseId: String?,
    val ms: Long,
    val plays: Int,
)

/** A song row, which keeps the whole [Song] so tapping it can play it. */
data class RankedSong(val song: Song, val ms: Long, val plays: Int)
