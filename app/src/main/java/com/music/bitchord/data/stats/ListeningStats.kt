package com.music.bitchord.data.stats

import android.content.Context
import android.util.Log
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/**
 * What this device has listened to, kept on this device.
 *
 * ## Aggregates, not an event log
 *
 * The obvious shape for listening history is a row per play, summed up when
 * something asks. It is also the shape that grows without bound on a phone
 * nobody is going to garbage-collect: a year of ordinary listening is tens of
 * thousands of rows, and every one of them is carried around so that a page
 * almost nobody opens can add them up once.
 *
 * So the addition happens on the way in. A track's total is a counter that goes
 * up while it plays, and reading the Replay is a merge of a handful of already
 * finished sums rather than a pass over history. The cost of that is what any
 * aggregate costs: the raw plays are gone, so a question nobody thought of in
 * advance cannot be answered later. Every question Replay asks is here.
 *
 * ## One file per month
 *
 * Buckets are calendar months in the device's own time zone, one JSON file
 * each. That is what makes "this year" and "all time" the same operation on
 * different numbers of files, and it means the only bucket held in memory is
 * the one being written to — the rest are read, merged and released.
 *
 * A month is also the granularity below which nothing on the Replay page asks a
 * question. Days are still counted, as a total per day inside the bucket, which
 * is enough for "your biggest day" without a file per day to get there.
 *
 * ## Nothing leaves the device
 *
 * There is no upload, no account and no id. [Backup] can write the whole thing
 * out as JSON because the user asked it to, and that is the only way any of this
 * goes anywhere.
 */
object ListeningStats {

    private lateinit var directory: File

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * The month being written to, held open between samples.
     *
     * Guarded by its own lock rather than made thread-safe piecewise: a sample
     * touches four maps and two arrays and has to leave them agreeing with each
     * other, and the writer is one coroutine ticking every few seconds, so
     * there is nothing here for finer locking to win.
     */
    private var open: OpenBucket? = null

    private val lock = Any()

    /** Set when [open] has changes not yet on disk. */
    private var dirty = false

    /**
     * Where every write to this folder happens.
     *
     * Its own scope rather than the caller's, because the caller is the
     * playback service and its scope is the main thread. [writeLock] serialises
     * them so a flush and a month rollover cannot be halfway into the same file
     * at once.
     */
    private val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    /**
     * Bumped by every completed write and every import, so [summary] can tell
     * whether the answer it already has is still the answer.
     */
    @Volatile
    private var version = 0L

    /** The last summary handed out, and what it was computed from. */
    private var cached: Cached? = null

    private class Cached(
        val period: ReplayPeriod,
        val version: Long,
        val facts: Int,
        val day: LocalDate,
        val summary: ReplaySummary,
    )

    fun init(context: Context) {
        directory = File(context.filesDir, DIRECTORY)
        // Other devices' months, added in by [summary]; see PeerListening.
        PeerListening.init(context)
        // Opening a month means reading and parsing it, and the first thing to
        // ask for one is the playback sampler — on the main thread, a few
        // seconds into the first track. Done here instead, it has happened long
        // before anything is playing.
        writer.launch { synchronized(lock) { bucketFor(YearMonth.now()) } }
    }

    private val ready: Boolean get() = this::directory.isInitialized

    // ── Writing ─────────────────────────────────────────────────────────────

    /**
     * Adds [playedMs] of [song] to the current month.
     *
     * [countsAsPlay] separates the two things a listener means by "played". The
     * minutes are what actually came out of the speaker and are added on every
     * sample; a *play* is a whole listen and is counted once, by whoever is
     * watching the track rather than here — see [ListeningRecorder], which holds
     * the same threshold the scrobbler uses.
     */
    fun record(song: Song, playedMs: Long, countsAsPlay: Boolean) {
        if (!ready) return
        if (playedMs <= 0 && !countsAsPlay) return
        if (song.videoId.isBlank()) return
        val now = System.currentTimeMillis()
        val at = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
        val dayKey = "${at.year}-${"%02d".format(at.monthValue)}-${"%02d".format(at.dayOfMonth)}"
        synchronized(lock) {
            // What a sample adds, and where, is OpenBucket's (shared with the
            // web player). Artists are looked up as they are noticed: see
            // ArtistFacts.
            bucketFor(YearMonth.from(at))
                .record(song, playedMs, countsAsPlay, now, dayKey, at.hour, at.dayOfMonth, ArtistFacts::noticed)
            dirty = true
        }
    }

    /**
     * Writes the open month out, if anything has changed.
     *
     * Called on a cadence rather than on every sample: the numbers are worth
     * losing a few seconds of, and a music player has better uses for its disk
     * than re-serialising a month every time a track ticks over.
     */
    fun flush() {
        pending()?.let { (key, snapshot) -> writer.launch { write(key, snapshot) } }
    }

    /** As [flush], for a caller that is about to read back what it wrote. */
    private suspend fun flushAndAwait() {
        pending()?.let { (key, snapshot) -> writer.launch { write(key, snapshot) }.join() }
    }

    /** The open month as bytes-to-be, or null when nothing has changed. */
    private fun pending(): Pair<String, StoredBucket>? {
        if (!ready) return null
        return synchronized(lock) {
            if (!dirty) return null
            val bucket = open ?: return null
            dirty = false
            bucket.key to bucket.snapshot()
        }
    }

    /**
     * Serialises a month and swaps it into place.
     *
     * Never on the caller's thread. The caller is the playback service's
     * sampler, which runs on the main thread — see its `reportProgress` — so
     * encoding a month of listening and writing it there was a JSON pass and a
     * file write on the UI thread every thirty seconds of playback, growing
     * with the size of the month.
     *
     * Written aside and renamed, so a kill mid-write leaves the previous
     * month rather than a truncated one.
     */
    private suspend fun write(key: String, snapshot: StoredBucket) = writeLock.withLock {
        runCatching {
            directory.mkdirs()
            val file = File(directory, "$key.json")
            val temporary = File(directory, "$key.json.tmp")
            temporary.writeText(json.encodeToString(StoredBucket.serializer(), snapshot))
            if (!temporary.renameTo(file)) {
                // renameTo will not replace on some filesystems; the delete is
                // the only thing standing between that and a .tmp per flush.
                if (file.delete() && temporary.renameTo(file)) Unit else temporary.delete()
            }
            version++
        }.onFailure { Log.w(TAG, "Could not write listening bucket $key", it) }
    }

    /** The bucket for [month], loading or creating it and retiring the last one. */
    private fun bucketFor(month: YearMonth): OpenBucket {
        val key = month.toString()
        open?.takeIf { it.key == key }?.let { return it }
        // The month rolled over mid-session. Whatever the last one still held
        // has to reach disk before it is dropped on the floor.
        open?.let { previous ->
            val stale = previous.snapshot()
            writer.launch { write(previous.key, stale) }
        }
        val loaded = read(key) ?: StoredBucket(month = key)
        return OpenBucket.of(key, loaded).also {
            open = it
            prune()
        }
    }

    // ── Reading ─────────────────────────────────────────────────────────────

    /**
     * The Replay for [period], merged off disk.
     *
     * Off the main thread and off the open bucket both: the months it needs are
     * files, and the one being written to is flushed first so that the page
     * agrees with what has just been playing.
     */
    suspend fun summary(period: ReplayPeriod): ReplaySummary = withContext(Dispatchers.IO) {
        flushAndAwait()
        val today = LocalDate.now()
        val facts = ArtistFacts.revision.value
        // Every input to the merge, so the cache cannot be stale: which months
        // are on disk and what is in them ([version]), what is known about the
        // artists in them, which period was asked for, and — because "this
        // month" and "this year" are relative — what day it is. Opening the
        // Library tab asks for this, so it is asked often and usually for an
        // answer that has not changed.
        cached?.takeIf {
            it.period == period && it.version == version && it.facts == facts && it.day == today
        }?.let { return@withContext it.summary }

        val merged = MergedListening()
        // This device's month first, then every other device's copy of it —
        // see [PeerListening]. Each is a separate sum of separate plays, so
        // adding them is the whole of the merge. Own first so that where two
        // entries fold into one row (a local file, say), the id that survives
        // is one this phone can open.
        fun monthBuckets(month: YearMonth): List<StoredBucket> =
            listOfNotNull(read(month.toString())) + PeerListening.buckets(month.toString())
        when {
            period.needsDayFilter() -> {
                // Week may span two months: read both, filter by day.
                val range = period.range(today)!!
                allMonths().filter { month ->
                    val start = month.atDay(1)
                    val end = month.atEndOfMonth()
                    !(end < range.first || start > range.second)
                }.forEach { month -> monthBuckets(month).forEach { bucket -> merged.add(bucket, range.asDayFilter()) } }
            }
            else -> {
                allMonths().filter { period.covers(it, today) }
                    .forEach { month -> monthBuckets(month).forEach(merged::add) }
            }
        }
        toSummary(merged, period, today).also {
            cached = Cached(period, version, facts, today, it)
        }
    }

    /**
     * One calendar [year] in full, for the Replay year story.
     *
     * Apart from [summary] because it needs a year the periods can't name: from
     * January the 1st to the 15th the story is still about the year that just
     * ended, and [ReplayPeriod.THIS_YEAR] has already moved on. Not cached:
     * it is read once each time the story's button appears, a dozen month files
     * at most, and caching it here would evict the Library's Lifetime summary
     * from [cached] every time Home was opened.
     *
     * Peers are merged in exactly as [summary] does, so the year is every
     * synced device's year.
     */
    suspend fun year(year: Int): ReplayYearData = withContext(Dispatchers.IO) {
        flushAndAwait()
        val today = LocalDate.now()
        val months = allMonths().filter { it.year == year }
        val merged = MergedListening()
        val dayBuckets = ArrayList<StoredBucket>()
        val monthTops = ArrayList<ReplayMonthTop>()
        months.forEach { month ->
            val buckets = bucketsOf(month)
            buckets.forEach(merged::add)
            dayBuckets += buckets
            // A month on its own, for its top song only. Ranked by the same
            // merge as the year, so a month's chart agrees with the Replay's.
            val single = MergedListening().apply { buckets.forEach(::add) }
            toSummary(single, ReplayPeriod.THIS_YEAR, today).songs.firstOrNull()
                ?.let { monthTops += ReplayMonthTop(month, it) }
        }
        val dayMs = YearListening.dayMs(dayBuckets)
        val summary = toSummary(merged, ReplayPeriod.THIS_YEAR, today).copy(label = year.toString())
        // The song that carried the busiest day (see YearListening). Months
        // recorded before tracks kept per-day entries have none, and the story
        // picks another song.
        val busiestDaySong = summary.busiestDay?.let { date ->
            val month = runCatching { YearMonth.parse(date.take(7)) }.getOrNull() ?: return@let null
            val best = YearListening.busiestDaySongId(bucketsOf(month), date) ?: return@let null
            summary.songs.firstOrNull { it.song.videoId == best }?.song
        }
        ReplayYearData(year, summary, dayMs, monthTops, busiestDaySong)
    }

    /** This device's copy of [month], then every other device's. See [summary]. */
    private fun bucketsOf(month: YearMonth): List<StoredBucket> =
        listOfNotNull(read(month.toString())) + PeerListening.buckets(month.toString())

    /** Every month with a file, oldest first. */
    fun months(): List<YearMonth> {
        if (!ready) return emptyList()
        val files = directory.listFiles() ?: return emptyList()
        return files.mapNotNull { file ->
            file.name.removeSuffix(".json").takeIf { it != file.name }
                ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        }.sorted()
    }

    /**
     * Every month with listening on this device or any other synced to it,
     * oldest first: what the Replay reads, where [months] is what this device
     * owns and uploads.
     */
    fun allMonths(): List<YearMonth> = (months() + PeerListening.months()).distinct().sorted()

    /**
     * Another device's months have been downloaded or dropped: the summary
     * held is no longer the answer. See [PeerListening].
     */
    fun peersChanged() {
        version++
        cached = null
        _peerRevision.value++
    }

    private val _peerRevision = MutableStateFlow(0)

    /** Bumped by [peersChanged], for a page that should redraw when it does. */
    val peerRevision: StateFlow<Int> = _peerRevision.asStateFlow()

    private fun read(key: String): StoredBucket? {
        val file = File(directory, "$key.json")
        if (!file.exists()) return null
        return runCatching { json.decodeFromString(StoredBucket.serializer(), file.readText()) }
            .onFailure { Log.w(TAG, "Discarding unreadable listening bucket $key", it) }
            .getOrNull()
    }

    // ── Housekeeping ────────────────────────────────────────────────────────

    /**
     * Keeps the folder to [KEEP_MONTHS] and each bucket to its entry caps.
     *
     * Both are the same bet: what survives is what was actually listened to, so
     * eviction is by time played rather than by recency. A track heard once in
     * passing is the first thing out of a full bucket, and it was never going to
     * appear on any page this data exists to draw.
     */
    private fun prune() {
        val existing = months()
        if (existing.size > ListeningLimits.KEEP_MONTHS) {
            existing.take(existing.size - ListeningLimits.KEEP_MONTHS).forEach {
                File(directory, "$it.json").delete()
            }
        }
        open?.trim()
    }

    // ── Backup ──────────────────────────────────────────────────────────────

    /** Every bucket, for [Backup]. */
    suspend fun exportAll(): List<StoredBucket> {
        if (!ready) return emptyList()
        flushAndAwait()
        return months().mapNotNull { read(it.toString()) }
    }

    /**
     * Replaces everything held with [buckets].
     *
     * A replace rather than a merge, and deliberately so: an import is a restore
     * onto a new device, and two copies of the same month added together would
     * silently double every number on the page with no way to tell afterwards.
     */
    fun importAll(buckets: List<StoredBucket>) {
        if (!ready) return
        synchronized(lock) {
            open = null
            dirty = false
            directory.listFiles()?.forEach { it.delete() }
            directory.mkdirs()
            version++
            cached = null
            buckets.forEach { bucket ->
                val key = runCatching { YearMonth.parse(bucket.month).toString() }.getOrNull()
                    ?: return@forEach
                runCatching {
                    File(directory, "$key.json")
                        .writeText(json.encodeToString(StoredBucket.serializer(), bucket))
                }.onFailure { Log.w(TAG, "Could not import bucket $key", it) }
            }
        }
    }

    // ── Shapes ──────────────────────────────────────────────────────────────

    /**
     * The charts [merged] makes for [period], drawn as the phone draws them.
     *
     * The ranking is [MergedListening]'s, shared with the web player. What is
     * added here is what only the phone knows: each artist's own picture and
     * page where [ArtistFacts] has found one, and the genres they vote for.
     */
    private fun toSummary(merged: MergedListening, period: ReplayPeriod, today: LocalDate): ReplaySummary {
        // For weekly mode, compute per-entity totals from day-level sub-buckets.
        val inRange = if (period.needsDayFilter()) period.range(today)?.asDayFilter() else null

        val rankedSongs = merged.songs(inRange)

        // The artist's own picture and page where one has been found, and
        // the track's sleeve where one hasn't -- see [ArtistFacts]. Resolved
        // here rather than stored in the bucket because it is a fact about
        // the artist, not about the evening they were played on: written
        // into every month it would freeze at whatever was known the first
        // time they came up, and be missing from every month before that.
        val rankedArtists = merged.artists(inRange).map {
            it.copy(
                artworkUrl = ArtistFacts.imageFor(it.title) ?: it.artworkUrl,
                browseId = it.browseId ?: ArtistFacts.browseIdFor(it.title),
            )
        }

        val rankedAlbums = merged.albums(inRange)

        // Genres are derived rather than stored — see [ArtistFacts]. An
        // artist with no tag yet simply doesn't vote, which is why this can
        // be an empty list on a page whose other charts are full.
        val genreMs = LinkedHashMap<String, Long>()
        val genrePlays = LinkedHashMap<String, Int>()
        val genreArt = LinkedHashMap<String, String?>()
        rankedArtists.forEach { artist ->
            ArtistFacts.genresFor(artist.title).forEach { genre ->
                genreMs[genre] = (genreMs[genre] ?: 0L) + artist.ms
                genrePlays[genre] = (genrePlays[genre] ?: 0) + artist.plays
                if (genreArt[genre] == null) genreArt[genre] = artist.artworkUrl
            }
        }
        // Anything still missing a picture is asked about now, so opening
        // the page is a second way for these to fill in — the first being
        // playing the music, which is no help to someone who has just
        // installed a build that started collecting them.
        rankedArtists.take(ARTISTS_TO_RESOLVE)
            .filter { it.artworkUrl == null || it.browseId == null }
            .forEach { ArtistFacts.noticed(it.title) }

        val rankedGenres = genreMs.entries
            .sortedByDescending { it.value }
            .map {
                RankedEntry(
                    title = it.key,
                    subtitle = null,
                    artworkUrl = genreArt[it.key],
                    browseId = null,
                    ms = it.value,
                    plays = genrePlays[it.key] ?: 0,
                )
            }

        val busiest = merged.busiestDay()
        return ReplaySummary(
            period = period,
            label = period.label(today),
            totalMs = rankedSongs.sumOf { it.ms },
            totalPlays = rankedSongs.sumOf { it.plays },
            songs = rankedSongs,
            artists = rankedArtists,
            albums = rankedAlbums,
            genres = rankedGenres,
            hourOfDay = merged.hours.toList(),
            busiestDay = busiest?.first,
            busiestDayMs = busiest?.second ?: 0L,
            distinctSongs = rankedSongs.size,
            distinctArtists = rankedArtists.size,
            distinctAlbums = rankedAlbums.size,
            since = merged.earliest,
        )
    }

    /**
     * Whether a `YYYY-MM-DD` key falls in this inclusive range, for the shared
     * merge, which has no calendar of its own. A key that won't parse isn't in it.
     */
    private fun Pair<LocalDate, LocalDate>.asDayFilter(): (String) -> Boolean = { key ->
        val date = runCatching { LocalDate.parse(key) }.getOrNull()
        date != null && date >= first && date <= second
    }

    /** @see ListeningKeys.mergeKey */
    internal fun mergeKey(entry: TrackEntry): String = ListeningKeys.mergeKey(entry)

    /** @see ListeningKeys.foldAlbumsByTitle */
    internal fun foldAlbumsByTitle(albums: Collection<NameEntry>): List<NameEntry> =
        ListeningKeys.foldAlbumsByTitle(albums)

    /** The lead artist on a track's credit: see [ListeningKeys.primaryArtist]. */
    fun primaryArtist(credit: String): String? = ListeningKeys.primaryArtist(credit)

    private const val TAG = "BitChordListening"
    private const val DIRECTORY = "listening"

    /** How far down the artist chart a page opening will send lookups for. */
    private const val ARTISTS_TO_RESOLVE = 15

}

/** A calendar year, for the Replay year story. See [ListeningStats.year]. */
data class ReplayYearData(
    val year: Int,
    val summary: ReplaySummary,
    /** Milliseconds listened per day, keyed `YYYY-MM-DD`. Days with nothing are absent. */
    val dayMs: Map<String, Long>,
    /** Each month's top song, oldest month first. Months with nothing are absent. */
    val monthTops: List<ReplayMonthTop>,
    /** The song played most on [ReplaySummary.busiestDay], when the months kept per-day counts. */
    val busiestDaySong: Song?,
)

/** One month's top song. */
data class ReplayMonthTop(val month: YearMonth, val song: RankedSong)

/** How far back a Replay reaches. */
enum class ReplayPeriod(val chip: String) {
    THIS_WEEK("This week"),
    THIS_MONTH("This month"),
    THIS_YEAR("This year"),
    ALL_TIME("All time"),
    ;

    /** The inclusive date range this period covers. Null for ALL_TIME. */
    fun range(today: LocalDate): Pair<LocalDate, LocalDate>? = when (this) {
        THIS_WEEK -> {
            val start = today.with(java.time.DayOfWeek.MONDAY)
            start to today
        }
        THIS_MONTH -> {
            val ym = YearMonth.from(today)
            ym.atDay(1) to ym.atEndOfMonth()
        }
        THIS_YEAR -> LocalDate.of(today.year, 1, 1) to LocalDate.of(today.year, 12, 31)
        ALL_TIME -> null
    }

    fun covers(month: YearMonth, today: LocalDate): Boolean = when (this) {
        THIS_WEEK -> {
            val range = month.atDay(1)..month.atEndOfMonth()
            val weekStart = today.with(java.time.DayOfWeek.MONDAY)
            val weekEnd = today
            !(weekEnd < range.start || weekStart > range.endInclusive)
        }
        THIS_MONTH -> month == YearMonth.from(today)
        THIS_YEAR -> month.year == today.year
        ALL_TIME -> true
    }

    /** Whether this period needs day-level filtering (week spans potentially multiple months). */
    fun needsDayFilter(): Boolean = this == THIS_WEEK

    fun label(today: LocalDate): String = when (this) {
        THIS_WEEK -> "This week"
        THIS_MONTH -> YearMonth.from(today).month.name.lowercase(Locale.ROOT)
            .replaceFirstChar { it.uppercase(Locale.ROOT) } + " ${today.year}"
        THIS_YEAR -> today.year.toString()
        ALL_TIME -> "All time"
    }
}

/**
 * Everything the Replay page and the stories draw, worked out once.
 *
 * Deliberately a plain value with the charts already sorted: the stories flip
 * between eight views of the same numbers, and recomputing a ranking per page
 * would put a sort on the swipe.
 */
data class ReplaySummary(
    val period: ReplayPeriod,
    val label: String,
    val totalMs: Long,
    val totalPlays: Int,
    val songs: List<RankedSong>,
    val artists: List<RankedEntry>,
    val albums: List<RankedEntry>,
    val genres: List<RankedEntry>,
    val hourOfDay: List<Long>,
    /** `YYYY-MM-DD` of the day with the most listening, or null. */
    val busiestDay: String?,
    val busiestDayMs: Long,
    val distinctSongs: Int,
    val distinctArtists: Int,
    val distinctAlbums: Int,
    /** The earliest month with anything in it, `YYYY-MM`. */
    val since: String?,
) {
    val minutes: Long get() = totalMs / 60_000
    val hours: Long get() = totalMs / 3_600_000

    /**
     * Whether there is anything at all to draw.
     *
     * One track is enough. There was a minimum here — five minutes, on the
     * reasoning that a chart of two songs is not a chart — and it was wrong in
     * the only way that matters: someone who has just played a couple of songs
     * and gone looking for the page they heard about is told they have not
     * listened to anything, which is both untrue and indistinguishable from the
     * feature being broken. A small Replay is a small Replay, and it grows.
     */
    val isEmpty: Boolean get() = songs.isEmpty()

    /** The hour of the day with the most listening, or null if nothing was. */
    val peakHour: Int? get() = hourOfDay.withIndex()
        .filter { it.value > 0 }
        .maxByOrNull { it.value }
        ?.index
}
