@file:OptIn(ExperimentalJsExport::class)

package com.music.bitchord.web

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.stats.ListeningTally
import com.music.bitchord.data.stats.MergedListening
import com.music.bitchord.data.stats.OpenBucket
import com.music.bitchord.data.stats.RankedEntry
import com.music.bitchord.data.stats.RankedSong
import com.music.bitchord.data.stats.StoredBucket
import com.music.bitchord.data.stats.YearListening
import com.music.bitchord.ui.replay.year.YearFacts
import com.music.bitchord.ui.replay.year.YearPlan
import kotlinx.serialization.json.Json

/*
 * The Replay's rules for the web player (web/app/src/replay): how a tick of the
 * player counts, how it is filed into a month, and how months add up into the
 * charts and the year story. Months travel as the JSON ListeningStats writes on
 * the phone, so the web stores, and Drive exchanges, exactly the phone's bytes.
 */

/** ListeningStats' `json`: how a month is written to disk on the phone. */
private val bucketJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** A month as stored, or null when it can't be read (ListeningStats discards those too). */
private fun decode(json: String): StoredBucket? =
    runCatching { bucketJson.decodeFromString(StoredBucket.serializer(), json) }.getOrNull()

/** ListeningTally.kt `Step`: what one tick of the sampler added. */
@JsExport
class ReplayStep(val playedMs: Double, val countsAsPlay: Boolean, val flush: Boolean)

/** ListeningTally.kt, ListeningRecorder's rule: wall-clock steps capped at 8 s, a play at min(half, 4 min), never under 30 s. */
@JsExport
class ReplayTally {
    private val tally = ListeningTally()

    /** A tick at [now] (epoch ms) while [id] is audibly playing, [lengthMs] long (0 if unknown); null adds nothing. */
    fun sample(id: String, now: Double, lengthMs: Double): ReplayStep? =
        tally.sample(id, now.toLong(), lengthMs.toLong())?.let { ReplayStep(it.playedMs.toDouble(), it.countsAsPlay, it.flush) }

    /** Playback paused, stopped or moved on: the next tick anchors. */
    fun stop() = tally.stop()
}

/** A played track, as much of a Song as the record reads. */
@JsExport
class ReplayTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val albumId: String?,
    val artistId: String?,
    val art: String?,
)

/** OpenBucket.kt: the month being written to, read from and written back as the phone's JSON. */
@JsExport
class ReplayMonth(val month: String, json: String?) {
    // Trimmed on opening, as ListeningStats prunes when it opens a month.
    private val open = OpenBucket.of(month, json?.let(::decode) ?: StoredBucket(month = month)).also { it.trim() }

    /**
     * ListeningStats.record: [playedMs] of [track] at [now], in the browser's own
     * time zone's [hour] and [dayOfMonth] (this month's). False when it adds nothing.
     */
    fun record(track: ReplayTrack, playedMs: Double, countsAsPlay: Boolean, now: Double, hour: Int, dayOfMonth: Int): Boolean {
        if (playedMs <= 0 && !countsAsPlay) return false
        if (track.videoId.isBlank()) return false
        val song = Song(
            videoId = track.videoId,
            title = track.title,
            artist = track.artist,
            thumbnailUrl = track.art,
            artistId = track.artistId,
            albumId = track.albumId,
            albumName = track.album,
        )
        open.record(song, playedMs.toLong(), countsAsPlay, now.toLong(), MergedListening.dayKey(month, dayOfMonth), hour, dayOfMonth)
        return true
    }

    /** The month as the phone writes it. */
    fun json(): String = bucketJson.encodeToString(StoredBucket.serializer(), open.snapshot())
}

/** A song chart row (RankedSong). */
@JsExport
class ReplaySong(
    val videoId: String,
    val title: String,
    val artist: String,
    val art: String?,
    val album: String?,
    val albumId: String?,
    val artistId: String?,
    val ms: Double,
    val plays: Int,
)

/** An artist or album chart row (RankedEntry): an artist's [browseId] is its channel, an album's its browse id. */
@JsExport
class ReplayRow(val title: String, val subtitle: String?, val art: String?, val browseId: String?, val ms: Double, val plays: Int)

/** What ListeningStats.summary works out, less the genres (the web looks up no artist facts). */
@JsExport
class ReplayCharts(
    val songs: Array<ReplaySong>,
    val artists: Array<ReplayRow>,
    val albums: Array<ReplayRow>,
    /** Milliseconds per hour of the day, 0..23. */
    val hours: Array<Double>,
    val busiestDay: String?,
    val busiestDayMs: Double,
    val totalMs: Double,
    val totalPlays: Int,
    /** The earliest month added, `YYYY-MM`. */
    val since: String?,
)

private fun RankedSong.web() = ReplaySong(
    song.videoId, song.title, song.artist, song.thumbnailUrl, song.albumName, song.albumId, song.artistId, ms.toDouble(), plays,
)

private fun RankedEntry.web() = ReplayRow(title, subtitle, artworkUrl, browseId, ms.toDouble(), plays)

private fun MergedListening.charts(inRange: ((String) -> Boolean)?): Pair<ReplayCharts, List<RankedSong>> {
    val songs = songs(inRange)
    val busiest = busiestDay()
    return ReplayCharts(
        songs = songs.map { it.web() }.toTypedArray(),
        artists = artists(inRange).map { it.web() }.toTypedArray(),
        albums = albums(inRange).map { it.web() }.toTypedArray(),
        hours = hours.map { it.toDouble() }.toTypedArray(),
        busiestDay = busiest?.first,
        busiestDayMs = busiest?.second?.toDouble() ?: 0.0,
        totalMs = songs.sumOf { it.ms }.toDouble(),
        totalPlays = songs.sumOf { it.plays },
        since = earliest,
    ) to songs
}

/**
 * ListeningStats.summary over [buckets] (each month's, this browser's first, then
 * any other device's). For a week, [weekFrom]..[weekTo] (`YYYY-MM-DD`, inclusive)
 * limits it to those days, as the phone's This week does.
 */
@JsExport
fun replayCharts(buckets: Array<String>, weekFrom: String?, weekTo: String?): ReplayCharts {
    val merged = MergedListening()
    val inRange: ((String) -> Boolean)? =
        if (weekFrom != null && weekTo != null) { key -> key.length == 10 && key >= weekFrom && key <= weekTo } else null
    buckets.mapNotNull(::decode).forEach { if (inRange != null) merged.add(it, inRange) else merged.add(it) }
    return merged.charts(inRange).first
}

/** One month's top song, for the month-by-month card. */
@JsExport
class ReplayMonthTop(val month: String, val song: ReplaySong)

/** ListeningStats.year and the phone's ReplayYear: the year's numbers, and the cards told about them. */
@JsExport
class ReplayYearStory(
    val charts: ReplayCharts,
    /** Milliseconds per day: `YYYY-MM-DD` keys and their values, side by side. */
    val dayKeys: Array<String>,
    val dayMs: Array<Double>,
    val monthTops: Array<ReplayMonthTop>,
    val busiestDaySong: ReplaySong?,
    /** YearPlan's ListeningPersonality, or null with nothing heard. */
    val personality: String?,
    /** The cards told (YearSlide names), in order. */
    val slides: Array<String>,
    /** Each card's song, beside [slides]; null for a card with nothing to play. */
    val clips: Array<ReplaySong?>,
)

/**
 * The year story over [months] (one year's, oldest first, each month's buckets as
 * stored, this browser's first). Null when there is nothing in it.
 */
@JsExport
fun replayYear(months: Array<Array<String>>): ReplayYearStory? {
    val decoded = months.map { month -> month.mapNotNull(::decode) }
    val merged = MergedListening()
    decoded.forEach { buckets -> buckets.forEach(merged::add) }
    val (charts, songs) = merged.charts(null)
    if (songs.isEmpty()) return null

    val monthTops = decoded.mapNotNull { buckets ->
        // A month on its own, for its top song only: the same merge as the year.
        val month = buckets.firstOrNull()?.month ?: return@mapNotNull null
        MergedListening().apply { buckets.forEach(::add) }.songs().firstOrNull()?.let { month to it }
    }
    val busiestDaySong = charts.busiestDay?.let { date ->
        val buckets = decoded.firstOrNull { it.firstOrNull()?.month == date.take(7) } ?: return@let null
        val best = YearListening.busiestDaySongId(buckets, date) ?: return@let null
        songs.firstOrNull { it.song.videoId == best }
    }
    val plan = YearPlan(
        YearFacts(
            songs = songs,
            artists = merged.artists(),
            albums = merged.albums(),
            genres = emptyList(),
            hourOfDay = merged.hours.toList(),
            busiestDay = charts.busiestDay,
            busiestDayMs = charts.busiestDayMs.toLong(),
            monthTops = monthTops.map { it.second.song },
            busiestDaySong = busiestDaySong?.song,
            genresFor = { emptyList() },
        ),
    )
    val byId = songs.associateBy { it.song.videoId }
    val dayMs = YearListening.dayMs(decoded.flatten()).entries.sortedBy { it.key }
    return ReplayYearStory(
        charts = charts,
        dayKeys = dayMs.map { it.key }.toTypedArray(),
        dayMs = dayMs.map { it.value.toDouble() }.toTypedArray(),
        monthTops = monthTops.map { (month, top) -> ReplayMonthTop(month, top.web()) }.toTypedArray(),
        busiestDaySong = busiestDaySong?.web(),
        personality = plan.personality?.name,
        slides = plan.slides.map { it.name }.toTypedArray(),
        clips = plan.slides.map { slide -> plan.clips[slide]?.let { byId[it.videoId]?.web() } }.toTypedArray(),
    )
}
