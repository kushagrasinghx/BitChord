package com.music.bitchord.data.stats

import android.content.Context
import android.util.Log
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
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
        synchronized(lock) {
            val bucket = bucketFor(YearMonth.from(at))
            val track = bucket.tracks.getOrPut(song.videoId) {
                TrackEntry(
                    id = song.videoId,
                    title = song.title,
                    artist = song.artist,
                    album = song.albumName,
                    albumId = song.albumId,
                    artistId = song.artistId,
                    art = song.thumbnailUrl,
                )
            }
            track.ms += playedMs
            track.last = now
            // Filled in as it becomes known: a track queued from search reaches
            // the player with no album, and the lookup that finds one lands
            // while it is already playing. The entry was created without it.
            if (track.album == null) track.album = song.albumName
            if (track.albumId == null) track.albumId = song.albumId
            if (track.artistId == null) track.artistId = song.artistId
            if (track.art == null) track.art = song.thumbnailUrl
            if (countsAsPlay) track.plays++

            // The lead artist, not the credit as a string. A track billed
            // "Cheema Y & Gur Sidhu" is not a third artist who happens to share
            // both their names, and filing it as one is how a chart lists the
            // same person twice — once solo and again in each collaboration —
            // with their listening split between the rows.
            //
            // The *lead* rather than everyone named, which is the reading the
            // rest of the app already makes: a row's artist id points at
            // whoever its page opens, and crediting a feature the same as a
            // headline act puts a guest verse on a chart of what someone
            // listens to.
            primaryArtist(song.artist)?.let { name ->
                // Asked here rather than in the recorder, because here is where
                // the credit has already been reduced to a person: asking about
                // "Cheema Y & Gur Sidhu" gets a picture of nobody and a genre of
                // nothing.
                ArtistFacts.noticed(name)
                val artist = bucket.artists.getOrPut(name.lowercase(Locale.ROOT)) {
                    NameEntry(name = name, art = song.thumbnailUrl)
                }
                artist.ms += playedMs
                if (countsAsPlay) artist.plays++
                if (artist.id == null) artist.id = song.artistId
            }

            song.albumName?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
                // Keyed on album *and* artist: "Greatest Hits" is not one
                // release, and merging every one of them into a single row is
                // how a compilation nobody owns tops the album chart.
                val album = bucket.albums.getOrPut(albumKey(name, song.artist)) {
                    NameEntry(name = name, sub = song.artist, art = song.thumbnailUrl)
                }
                album.ms += playedMs
                if (countsAsPlay) album.plays++
                if (album.id == null) album.id = song.albumId
            }

            bucket.hours[at.hour] += playedMs
            val day = at.dayOfMonth
            bucket.days[day] = (bucket.days[day] ?: 0L) + playedMs
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

        val merged = MergedBucket()
        months().filter { period.covers(it, today) }
            .forEach { month -> read(month.toString())?.let(merged::add) }
        merged.toSummary(period, today).also {
            cached = Cached(period, version, facts, today, it)
        }
    }

    /** Every month with a file, oldest first. */
    fun months(): List<YearMonth> {
        if (!ready) return emptyList()
        val files = directory.listFiles() ?: return emptyList()
        return files.mapNotNull { file ->
            file.name.removeSuffix(".json").takeIf { it != file.name }
                ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        }.sorted()
    }

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
        if (existing.size > KEEP_MONTHS) {
            existing.take(existing.size - KEEP_MONTHS).forEach {
                File(directory, "$it.json").delete()
            }
        }
        val bucket = open ?: return
        bucket.tracks.trimTo(MAX_TRACKS) { it.ms }
        bucket.artists.trimTo(MAX_NAMES) { it.ms }
        bucket.albums.trimTo(MAX_NAMES) { it.ms }
    }

    /**
     * [this] keyed by [key], with anything that lands on the same key added
     * together rather than overwriting.
     *
     * Needed because a key is *recomputed* on load rather than read back from
     * the file — see [NameEntry.key]. Two entries written under different keys
     * by an older build can therefore arrive at the same key now, and the
     * obvious `associateByTo` would silently drop one of their totals.
     */
    private inline fun <T : Any> List<T>.mergedBy(
        into: LinkedHashMap<String, T>,
        key: (T) -> String,
    ): LinkedHashMap<String, T> {
        forEach { entry ->
            val k = key(entry)
            val existing = into[k]
            if (existing == null) {
                into[k] = entry
            } else {
                @Suppress("UNCHECKED_CAST")
                (existing as NameEntry).absorb(entry as NameEntry)
            }
        }
        return into
    }

    private inline fun <K, V> MutableMap<K, V>.trimTo(limit: Int, crossinline weight: (V) -> Long) {
        if (size <= limit) return
        entries.sortedBy { weight(it.value) }
            .take(size - limit)
            .forEach { remove(it.key) }
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
     * The open month, in mutable form.
     *
     * Apart from [StoredBucket] because the stored shape is a schema and this
     * one is a working set: a counter that goes up thousands of times a session
     * wants to be a `var` in a map, and a file format wants to be immutable and
     * boring.
     */
    private class OpenBucket(
        val key: String,
        val tracks: MutableMap<String, TrackEntry>,
        val artists: MutableMap<String, NameEntry>,
        val albums: MutableMap<String, NameEntry>,
        val hours: LongArray,
        val days: MutableMap<Int, Long>,
    ) {
        fun snapshot() = StoredBucket(
            month = key,
            tracks = tracks.values.toList(),
            artists = artists.map { (key, entry) -> entry.copy(key = key) },
            albums = albums.map { (key, entry) -> entry.copy(key = key) },
            hours = hours.toList(),
            days = days.toMap(),
        )

        companion object {
            fun of(key: String, stored: StoredBucket) = OpenBucket(
                key = key,
                tracks = stored.tracks.associateByTo(LinkedHashMap()) { it.id },
                // Re-keyed on the lead artist, so entries a previous build
                // filed under a whole credit fold into the person on read
                // instead of sitting beside them forever.
                artists = stored.artists
                    .map { it.copy(name = primaryArtist(it.name) ?: it.name) }
                    .mergedBy(LinkedHashMap()) { it.name.lowercase(Locale.ROOT) },
                albums = stored.albums.mergedBy(LinkedHashMap()) { albumKey(it.name, it.sub.orEmpty()) },
                hours = LongArray(24) { stored.hours.getOrElse(it) { 0L } },
                days = stored.days.toMutableMap(),
            )
        }
    }

    /** Several months added together, on the way to a [ReplaySummary]. */
    private class MergedBucket {
        val tracks = HashMap<String, TrackEntry>()
        val artists = HashMap<String, NameEntry>()
        val albums = HashMap<String, NameEntry>()
        val hours = LongArray(24)
        val days = HashMap<String, Long>()
        var earliest: String? = null

        fun add(bucket: StoredBucket) {
            bucket.tracks.forEach { entry ->
                tracks.merge(entry.id, entry.copy()) { a, b -> a.also { it.absorb(b) } }
            }
            bucket.artists.forEach { entry ->
                val lead = entry.copy(name = primaryArtist(entry.name) ?: entry.name)
                artists.merge(lead.name.lowercase(Locale.ROOT), lead) { a, b -> a.also { it.absorb(b) } }
            }
            bucket.albums.forEach { entry ->
                val key = albumKey(entry.name, entry.sub.orEmpty())
                albums.merge(key, entry.copy()) { a, b -> a.also { it.absorb(b) } }
            }
            repeat(24) { hours[it] += bucket.hours.getOrElse(it) { 0L } }
            bucket.days.forEach { (day, ms) ->
                val date = "${bucket.month}-%02d".format(day)
                days[date] = (days[date] ?: 0L) + ms
            }
            if (earliest == null || bucket.month < earliest!!) earliest = bucket.month
        }

        fun toSummary(period: ReplayPeriod, today: LocalDate): ReplaySummary {
            val rankedSongs = tracks.values
                .sortedWith(compareByDescending<TrackEntry> { it.ms }.thenByDescending { it.plays })
                .map {
                    RankedSong(
                        song = Song(
                            videoId = it.id,
                            title = it.title,
                            artist = it.artist,
                            thumbnailUrl = it.art,
                            artistId = it.artistId,
                            albumId = it.albumId,
                            albumName = it.album,
                        ),
                        ms = it.ms,
                        plays = it.plays,
                    )
                }
            // The artist's own picture and page where one has been found, and
            // the track's sleeve where one hasn't -- see [ArtistFacts]. Resolved
            // here rather than stored in the bucket because it is a fact about
            // the artist, not about the evening they were played on: written
            // into every month it would freeze at whatever was known the first
            // time they came up, and be missing from every month before that.
            val rankedArtists = artists.values
                .sortedWith(compareByDescending<NameEntry> { it.ms }.thenByDescending { it.plays })
                .map {
                    RankedEntry(
                        title = it.name,
                        subtitle = it.sub,
                        artworkUrl = ArtistFacts.imageFor(it.name) ?: it.art,
                        browseId = it.id ?: ArtistFacts.browseIdFor(it.name),
                        ms = it.ms,
                        plays = it.plays,
                    )
                }
            val rankedAlbums = albums.values
                .sortedWith(compareByDescending<NameEntry> { it.ms }.thenByDescending { it.plays })
                .map { RankedEntry(it.name, it.sub, it.art, it.id, it.ms, it.plays) }

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

            val busiest = days.entries.maxByOrNull { it.value }
            return ReplaySummary(
                period = period,
                label = period.label(today),
                totalMs = tracks.values.sumOf { it.ms },
                totalPlays = tracks.values.sumOf { it.plays },
                songs = rankedSongs,
                artists = rankedArtists,
                albums = rankedAlbums,
                genres = rankedGenres,
                hourOfDay = hours.toList(),
                busiestDay = busiest?.key,
                busiestDayMs = busiest?.value ?: 0L,
                distinctSongs = tracks.size,
                distinctArtists = artists.size,
                distinctAlbums = albums.size,
                since = earliest,
            )
        }
    }

    /**
     * How an album is filed: its name and its artist, folded to lower case.
     *
     * Joined on a NUL rather than a space, because that is the one character
     * neither half can contain: with a space, "Greatest Hits" by "Vol 2" and
     * "Greatest Hits Vol" by "2" are the same key. Contrived, but the class of
     * bug is not, and a separator that cannot collide costs nothing.
     *
     * Concatenated rather than interpolated so the separator is visible in the
     * source — a NUL inside a template string is an invisible character in the
     * middle of a line, which is exactly how this file spent a while being
     * treated as a binary by every tool that looked at it.
     */
    private fun albumKey(name: String, artist: String): String =
        name.lowercase(Locale.ROOT) + ALBUM_KEY_SEPARATOR + artist.lowercase(Locale.ROOT)

    /** @see albumKey */
    private val ALBUM_KEY_SEPARATOR = Char(UNIT_SEPARATOR).toString()

    /**
     * ASCII 0x1F, which exists for exactly this and cannot occur in a title.
     *
     * Built from its code point rather than written as an escape: a control
     * character spelt out in a string literal is an invisible byte in the
     * middle of a line, and this file spent a while being treated as a binary
     * by every tool that looked at it because of one.
     */
    private const val UNIT_SEPARATOR = 31

    /**
     * The people named on a track's credit.
     *
     * Split conservatively, and on punctuation rather than on words: `,`, `&`,
     * `x` and the feature markers are how every catalogue this app reads joins
     * two artists, while " and " and " with " are as often part of a band's
     * actual name — "Florence and the Machine", "Nick Cave and the Bad Seeds" —
     * so they are left alone. A name is still split wrongly now and then
     * ("Simon & Garfunkel"), which costs one duplicated chart row; not splitting
     * at all costs a row for every collaboration anyone has ever recorded.
     */
    fun primaryArtist(credit: String): String? {
        lastCredit?.let { (raw, name) -> if (raw == credit) return name }
        val name = credit.split(CREDIT_SEPARATORS)
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        // Memoised on the last credit seen, because the caller is the playback
        // sampler: it asks about the same string a dozen times a minute for as
        // long as a track is playing, and a regex split per tick is work with a
        // known answer.
        lastCredit = credit to name
        return name
    }

    @Volatile
    private var lastCredit: Pair<String, String?>? = null

    private val CREDIT_SEPARATORS = Regex(
        """\s*,\s*|\s+&\s+|\s+x\s+|\s+feat\.?\s+|\s+ft\.?\s+|\s+featuring\s+""",
        RegexOption.IGNORE_CASE,
    )

    private const val TAG = "BitChordListening"
    private const val DIRECTORY = "listening"

    /** How far down the artist chart a page opening will send lookups for. */
    private const val ARTISTS_TO_RESOLVE = 15

    /** Three years, which is longer than "all time" means to most people. */
    private const val KEEP_MONTHS = 36

    /**
     * Per-month caps. Generous enough that nobody reaches them by listening,
     * tight enough that a bucket stays well under a hundred kilobytes.
     */
    private const val MAX_TRACKS = 600
    private const val MAX_NAMES = 400
}

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
) {
    fun absorb(other: TrackEntry) {
        ms += other.ms
        plays += other.plays
        last = maxOf(last, other.last)
        if (album == null) album = other.album
        if (albumId == null) albumId = other.albumId
        if (artistId == null) artistId = other.artistId
        if (art == null) art = other.art
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
) {
    fun absorb(other: NameEntry) {
        ms += other.ms
        plays += other.plays
        if (art == null) art = other.art
        if (id == null) id = other.id
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

/** How far back a Replay reaches. */
enum class ReplayPeriod(val chip: String) {
    THIS_MONTH("This month"),
    THIS_YEAR("This year"),
    ALL_TIME("All time"),
    ;

    fun covers(month: YearMonth, today: LocalDate): Boolean = when (this) {
        THIS_MONTH -> month == YearMonth.from(today)
        THIS_YEAR -> month.year == today.year
        ALL_TIME -> true
    }

    fun label(today: LocalDate): String = when (this) {
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
