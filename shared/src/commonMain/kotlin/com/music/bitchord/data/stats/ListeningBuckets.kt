package com.music.bitchord.data.stats

import com.music.bitchord.data.model.Song
import kotlin.concurrent.Volatile

/*
 * The arithmetic of ListeningStats with the files, clocks and lookups taken out:
 * how a sample is added to a month, how months are added up into charts, and how
 * entries are keyed. The phone's ListeningStats and the web player's Replay both
 * run this, so a month means the same on either.
 */

/** How entries are filed: the keys every month is read and merged under. */
object ListeningKeys {

    /**
     * How an album is filed: uses [albumId] as the primary key when available
     * (stable across different artist-credit strings), falling back to name+artist
     * for local files that have no ID.
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
    fun albumKey(name: String, artist: String): String =
        name.lowercase() + ALBUM_KEY_SEPARATOR + artist.lowercase()

    /** @see albumKey(albumId, name, artist) — overload for keyed lookups. */
    fun albumKey(albumId: String?, name: String, artist: String): String =
        albumId?.let { it } ?: albumKey(name, artist)

    /**
     * Which row of the song chart [entry] is added into.
     *
     * A track's id, except for a file on the device: its id is a `content://`
     * or `file://` address that only means something on the phone that played
     * it, so the same song played from two phones' storage would otherwise be
     * two rows. Those fold by title and artist instead.
     */
    fun mergeKey(entry: TrackEntry): String =
        if (entry.id.startsWith("content://") || entry.id.startsWith("file://")) {
            "local" + ALBUM_KEY_SEPARATOR + albumKey(entry.title, entry.artist)
        } else {
            entry.id
        }

    /**
     * [albums] with every entry that would be drawn as the same row folded into one.
     *
     * Filing by [albumKey] (id first) keeps one release apart from another, but
     * it also keeps one album apart from *itself*: a month written before the
     * track carried an album id files it by name, a later month files it by id,
     * and YouTube hands out more than one id for the same record. Over a year
     * those land as two entries with the same title and artist, which splits the
     * album's minutes across two rows and gives the Replay list two items with
     * the same key, which Compose answers by crashing.
     *
     * Entries with an id go first so the one that survives can still open the
     * album page; among those, the most played one's picture and id win.
     * Copies are folded, never the entries passed in.
     */
    fun foldAlbumsByTitle(albums: Collection<NameEntry>): List<NameEntry> {
        val folded = LinkedHashMap<String, NameEntry>()
        albums
            .sortedWith(compareByDescending<NameEntry> { it.id != null }.thenByDescending { it.ms })
            .forEach { entry ->
                val key = albumKey(entry.name, entry.sub.orEmpty())
                val existing = folded[key]
                if (existing == null) {
                    folded[key] = entry.copy(days = entry.days.mapValuesTo(mutableMapOf()) { it.value.copy() })
                } else {
                    existing.absorb(entry)
                }
            }
        return folded.values.toList()
    }

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
}

/** How much listening is kept: the folder's months and each month's entries. */
object ListeningLimits {
    /** Three years, which is longer than "all time" means to most people. */
    const val KEEP_MONTHS = 36

    /**
     * Per-month caps. Generous enough that nobody reaches them by listening,
     * tight enough that a bucket stays well under a hundred kilobytes.
     */
    const val MAX_TRACKS = 600
    const val MAX_NAMES = 400
}

/**
 * The open month, in mutable form.
 *
 * Apart from [StoredBucket] because the stored shape is a schema and this
 * one is a working set: a counter that goes up thousands of times a session
 * wants to be a `var` in a map, and a file format wants to be immutable and
 * boring.
 */
class OpenBucket(
    val key: String,
    val tracks: MutableMap<String, TrackEntry>,
    val artists: MutableMap<String, NameEntry>,
    val albums: MutableMap<String, NameEntry>,
    val hours: LongArray,
    val days: MutableMap<Int, Long>,
    /** Per-day sub-buckets for track/artist/album. Key: "YYYY-MM-DD". */
    val trackDays: MutableMap<String, DayStats> = mutableMapOf(),
    val artistDays: MutableMap<String, DayStats> = mutableMapOf(),
    val albumDays: MutableMap<String, DayStats> = mutableMapOf(),
) {
    fun snapshot() = StoredBucket(
        month = key,
        tracks = tracks.values.toList(),
        artists = artists.map { (key, entry) -> entry.copy(key = key) },
        albums = albums.map { (key, entry) -> entry.copy(key = key) },
        hours = hours.toList(),
        days = days.toMap(),
        trackDays = trackDays.mapValues { it.value },
        artistDays = artistDays.mapValues { it.value },
        albumDays = albumDays.mapValues { it.value },
    )

    /**
     * Adds [playedMs] of [song] to this month, at [now] — which the caller has
     * already turned into its own time zone's [dayKey] (`YYYY-MM-DD`), [hour] and
     * [dayOfMonth], since this file has no calendar.
     *
     * [countsAsPlay] separates the two things a listener means by "played". The
     * minutes are what actually came out of the speaker and are added on every
     * sample; a *play* is a whole listen and is counted once, by whoever is
     * watching the track rather than here — see [ListeningTally]. [noticed] hears
     * the lead artist's name, for anything that wants to look them up.
     */
    fun record(
        song: Song,
        playedMs: Long,
        countsAsPlay: Boolean,
        now: Long,
        dayKey: String,
        hour: Int,
        dayOfMonth: Int,
        noticed: (String) -> Unit = {},
    ) {
        val track = tracks.getOrPut(song.videoId) {
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

        // Day-level sub-bucket for the track.
        val trackDay = trackDays.getOrPut(dayKey) { DayStats() }
        trackDay.ms += playedMs
        if (countsAsPlay) trackDay.plays++

        // Also populate per-entity days map so weekRange() can read it.
        track.days.getOrPut(dayKey) { DayStats() }.also { d ->
            d.ms += playedMs
            if (countsAsPlay) d.plays++
        }

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
        ListeningKeys.primaryArtist(song.artist)?.let { name ->
            // Told here rather than by the recorder, because here is where
            // the credit has already been reduced to a person: asking about
            // "Cheema Y & Gur Sidhu" gets a picture of nobody and a genre of
            // nothing.
            noticed(name)
            val artist = artists.getOrPut(name.lowercase()) {
                NameEntry(name = name, art = song.thumbnailUrl)
            }
            artist.ms += playedMs
            if (countsAsPlay) artist.plays++
            if (artist.id == null) artist.id = song.artistId

            // Day-level sub-bucket for the artist.
            val artistDay = artistDays.getOrPut(dayKey) { DayStats() }
            artistDay.ms += playedMs
            if (countsAsPlay) artistDay.plays++

            // Also populate per-entity days map so weekRange() can read it.
            artist.days.getOrPut(dayKey) { DayStats() }.also { d ->
                d.ms += playedMs
                if (countsAsPlay) d.plays++
            }
        }

        song.albumName?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
            // Keyed on albumId when available (stable across artist-credit
            // variations), falling back to name+artist for local files.
            val key = ListeningKeys.albumKey(song.albumId, name, song.artist)
            val album = albums.getOrPut(key) {
                NameEntry(name = name, sub = song.artist, art = song.thumbnailUrl)
            }
            album.ms += playedMs
            if (countsAsPlay) album.plays++
            if (album.id == null) album.id = song.albumId

            // Day-level sub-bucket for the album.
            val albumDay = albumDays.getOrPut(dayKey) { DayStats() }
            albumDay.ms += playedMs
            if (countsAsPlay) albumDay.plays++

            // Also populate per-entity days map so weekRange() can read it.
            album.days.getOrPut(dayKey) { DayStats() }.also { d ->
                d.ms += playedMs
                if (countsAsPlay) d.plays++
            }
        }

        hours[hour] += playedMs
        days[dayOfMonth] = (days[dayOfMonth] ?: 0L) + playedMs
    }

    /**
     * Keeps this month to its entry caps.
     *
     * Eviction is by time played rather than by recency: what survives is what
     * was actually listened to. A track heard once in passing is the first thing
     * out of a full bucket, and it was never going to appear on any page this
     * data exists to draw.
     */
    fun trim() {
        tracks.trimTo(ListeningLimits.MAX_TRACKS) { it.ms }
        artists.trimTo(ListeningLimits.MAX_NAMES) { it.ms }
        albums.trimTo(ListeningLimits.MAX_NAMES) { it.ms }
    }

    private inline fun <K, V> MutableMap<K, V>.trimTo(limit: Int, crossinline weight: (V) -> Long) {
        if (size <= limit) return
        entries.sortedBy { weight(it.value) }
            .take(size - limit)
            .forEach { remove(it.key) }
    }

    companion object {
        fun of(key: String, stored: StoredBucket) = OpenBucket(
            key = key,
            tracks = stored.tracks.associateByTo(LinkedHashMap()) { it.id },
            // Re-keyed on the lead artist, so entries a previous build
            // filed under a whole credit fold into the person on read
            // instead of sitting beside them forever.
            artists = stored.artists
                .map { it.copy(name = ListeningKeys.primaryArtist(it.name) ?: it.name) }
                .mergedBy(LinkedHashMap()) { it.name.lowercase() },
            albums = stored.albums.mergedBy(LinkedHashMap()) {
                ListeningKeys.albumKey(it.id, it.name, it.sub.orEmpty())
            },
            hours = LongArray(24) { stored.hours.getOrElse(it) { 0L } },
            days = stored.days.toMutableMap(),
            trackDays = stored.trackDays?.toMutableMap() ?: mutableMapOf(),
            artistDays = stored.artistDays?.toMutableMap() ?: mutableMapOf(),
            albumDays = stored.albumDays?.toMutableMap() ?: mutableMapOf(),
        )

        /**
         * [this] keyed by [key], with anything that lands on the same key added
         * together rather than overwriting.
         *
         * Needed because a key is *recomputed* on load rather than read back from
         * the file — see [NameEntry.key]. Two entries written under different keys
         * by an older build can therefore arrive at the same key now, and the
         * obvious `associateByTo` would silently drop one of their totals.
         */
        private inline fun List<NameEntry>.mergedBy(
            into: LinkedHashMap<String, NameEntry>,
            key: (NameEntry) -> String,
        ): LinkedHashMap<String, NameEntry> {
            forEach { entry ->
                val k = key(entry)
                val existing = into[k]
                if (existing == null) {
                    into[k] = entry
                } else {
                    existing.absorb(entry)
                }
            }
            return into
        }
    }
}

/**
 * Several months added together, on the way to a Replay.
 *
 * The charts come out already ranked, over everything added or, for a week, over
 * the days [inRange] accepts. What a chart row *looks* like beyond that (an
 * artist's picture, a genre) is the caller's: those are facts about the artist
 * looked up on the phone, not things the months record.
 */
class MergedListening {
    val tracks = HashMap<String, TrackEntry>()
    val artists = HashMap<String, NameEntry>()
    val albums = HashMap<String, NameEntry>()
    val hours = LongArray(24)
    val days = HashMap<String, Long>()
    /** Per-day sub-buckets for tracks. Key: "YYYY-MM-DD". */
    val trackDays = HashMap<String, DayStats>()
    /** Per-day sub-buckets for artists. Key: "YYYY-MM-DD". */
    val artistDays = HashMap<String, DayStats>()
    /** Per-day sub-buckets for albums. Key: "YYYY-MM-DD". */
    val albumDays = HashMap<String, DayStats>()
    var earliest: String? = null

    fun add(bucket: StoredBucket) {
        addEntries(bucket)
        repeat(24) { hours[it] += bucket.hours.getOrElse(it) { 0L } }
        bucket.days.forEach { (day, ms) ->
            val date = dayKey(bucket.month, day)
            days[date] = (days[date] ?: 0L) + ms
        }
        if (earliest == null || bucket.month < earliest!!) earliest = bucket.month
    }

    /** Add only the days [inRange] accepts, keyed as "YYYY-MM-DD". */
    fun add(bucket: StoredBucket, inRange: (String) -> Boolean) {
        // Merge entity-level data (tracks/artists/albums) so weekRange() can read them.
        addEntries(bucket)
        // Merge day-level sub-buckets for the flat days map and cross-month totals.
        addDaySubs(bucket)
        bucket.days.forEach { (day, ms) ->
            val candidate = dayKey(bucket.month, day)
            if (inRange(candidate)) {
                days[candidate] = (days[candidate] ?: 0L) + ms
            }
        }
    }

    private fun addEntries(bucket: StoredBucket) {
        bucket.tracks.forEach { entry -> tracks.mergeEntry(ListeningKeys.mergeKey(entry), entry.copy(), TrackEntry::absorb) }
        bucket.artists.forEach { entry ->
            val lead = entry.copy(name = ListeningKeys.primaryArtist(entry.name) ?: entry.name)
            artists.mergeEntry(lead.name.lowercase(), lead, NameEntry::absorb)
        }
        bucket.albums.forEach { entry ->
            val key = ListeningKeys.albumKey(entry.id, entry.name, entry.sub.orEmpty())
            albums.mergeEntry(key, entry.copy(), NameEntry::absorb)
        }
    }

    /** Add all day-level sub-buckets for weekly queries. */
    private fun addDaySubs(bucket: StoredBucket) {
        bucket.trackDays?.forEach { (dateStr, stats) -> trackDays.addDay(dateStr, stats) }
        bucket.artistDays?.forEach { (dateStr, stats) -> artistDays.addDay(dateStr, stats) }
        bucket.albumDays?.forEach { (dateStr, stats) -> albumDays.addDay(dateStr, stats) }
    }

    /** The song chart: most listened first, within [inRange] when it is given (a week). */
    fun songs(inRange: ((String) -> Boolean)? = null): List<RankedSong> =
        ranked(tracks.values, inRange, TrackEntry::weekRange) { it.ms to it.plays }.map { (entry, ms, plays) ->
            RankedSong(
                song = Song(
                    videoId = entry.id,
                    title = entry.title,
                    artist = entry.artist,
                    thumbnailUrl = entry.art,
                    artistId = entry.artistId,
                    albumId = entry.albumId,
                    albumName = entry.album,
                ),
                ms = ms,
                plays = plays,
            )
        }

    /** The artist chart, with the track's sleeve as each picture; see the class note. */
    fun artists(inRange: ((String) -> Boolean)? = null): List<RankedEntry> =
        ranked(artists.values, inRange, NameEntry::weekRange) { it.ms to it.plays }.map { (entry, ms, plays) ->
            RankedEntry(entry.name, entry.sub, entry.art, entry.id, ms, plays)
        }

    /** The album chart, with one release drawn as one row: see [ListeningKeys.foldAlbumsByTitle]. */
    fun albums(inRange: ((String) -> Boolean)? = null): List<RankedEntry> =
        ranked(ListeningKeys.foldAlbumsByTitle(albums.values), inRange, NameEntry::weekRange) { it.ms to it.plays }
            .map { (entry, ms, plays) -> RankedEntry(entry.name, entry.sub, entry.art, entry.id, ms, plays) }

    /** The day with the most listening, `YYYY-MM-DD`, and how much; null with none. */
    fun busiestDay(): Pair<String, Long>? = days.entries.maxByOrNull { it.value }?.let { it.key to it.value }

    /**
     * [entries] by time, then plays. Over a week, an entry's own days within it,
     * and an entry with nothing in the week isn't on the chart at all.
     */
    private inline fun <T> ranked(
        entries: Collection<T>,
        noinline inRange: ((String) -> Boolean)?,
        week: T.((String) -> Boolean) -> Pair<Long, Int>,
        total: (T) -> Pair<Long, Int>,
    ): List<Triple<T, Long, Int>> {
        val counted = if (inRange != null) {
            entries.mapNotNull { entry ->
                val (ms, plays) = entry.week(inRange)
                if (ms > 0) Triple(entry, ms, plays) else null
            }
        } else {
            entries.map { entry -> total(entry).let { (ms, plays) -> Triple(entry, ms, plays) } }
        }
        return counted.sortedWith(compareByDescending<Triple<T, Long, Int>> { it.second }.thenByDescending { it.third })
    }

    private inline fun <T> HashMap<String, T>.mergeEntry(key: String, entry: T, absorb: T.(T) -> Unit) {
        val existing = this[key]
        if (existing == null) this[key] = entry else existing.absorb(entry)
    }

    private fun HashMap<String, DayStats>.addDay(dateStr: String, stats: DayStats) {
        val existing = this[dateStr]
        this[dateStr] = if (existing == null) stats else DayStats(existing.ms + stats.ms, existing.plays + stats.plays)
    }

    companion object {
        /** `2026-11` and 3 as `2026-11-03`. */
        fun dayKey(month: String, day: Int): String = month + "-" + day.toString().padStart(2, '0')
    }
}

/** The year story's arithmetic over one year's months; see ListeningStats.year. */
object YearListening {

    /** Milliseconds listened per day, keyed `YYYY-MM-DD`, over every bucket given. */
    fun dayMs(buckets: List<StoredBucket>): Map<String, Long> {
        val dayMs = HashMap<String, Long>()
        buckets.forEach { bucket ->
            bucket.days.forEach { (day, ms) ->
                val date = MergedListening.dayKey(bucket.month, day)
                dayMs[date] = (dayMs[date] ?: 0L) + ms
            }
        }
        return dayMs
    }

    /**
     * The song that carried [date]: the track whose day entry for it is biggest,
     * across [monthBuckets] (that month's, every device's). Months recorded before
     * tracks kept per-day entries have none, and the answer is null.
     */
    fun busiestDaySongId(monthBuckets: List<StoredBucket>, date: String): String? =
        monthBuckets.flatMap { it.tracks }
            .groupBy { it.id }
            .mapValues { (_, entries) -> entries.sumOf { it.days[date]?.ms ?: 0L } }
            .filterValues { it > 0 }
            .maxByOrNull { it.value }
            ?.key
}
