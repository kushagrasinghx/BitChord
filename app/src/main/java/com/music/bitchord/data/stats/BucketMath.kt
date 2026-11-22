package com.music.bitchord.data.stats

import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.Locale

/**
 * Arithmetic on months of listening, for moving them between devices.
 *
 * Everything in a [StoredBucket] is a counter — milliseconds and plays per
 * track, artist, album, hour and day — so two months can be added and one can
 * be taken from another, entry by entry. That is what lets several phones'
 * listening be summed into one Replay without ever counting anything twice:
 * each phone's months are kept apart and added only when read (see
 * [ListeningStats.summary]), and the one place a phone's own months have to be
 * split — handing a device over to another install — is a subtraction.
 *
 * Nothing here mutates its arguments: every entry is copied before it is added
 * to, because [TrackEntry.absorb] and [NameEntry.absorb] add in place.
 */
object BucketMath {

    private val canonical = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** [a] and [b] added together. Both must be the same month. */
    fun plus(a: StoredBucket, b: StoredBucket): StoredBucket {
        require(a.month == b.month) { "Can't add ${a.month} to ${b.month}" }
        val tracks = LinkedHashMap<String, TrackEntry>()
        (a.tracks + b.tracks).forEach { entry ->
            tracks.merge(entry.id, entry.deepCopy()) { x, y -> x.also { it.absorb(y) } }
        }
        val artists = LinkedHashMap<String, NameEntry>()
        (a.artists + b.artists).forEach { entry ->
            artists.merge(artistKey(entry), entry.deepCopy()) { x, y -> x.also { it.absorb(y) } }
        }
        val albums = LinkedHashMap<String, NameEntry>()
        (a.albums + b.albums).forEach { entry ->
            albums.merge(albumKey(entry), entry.deepCopy()) { x, y -> x.also { it.absorb(y) } }
        }
        return StoredBucket(
            version = maxOf(a.version, b.version),
            month = a.month,
            tracks = tracks.values.toList(),
            artists = artists.values.toList(),
            albums = albums.values.toList(),
            hours = List(24) { a.hours.getOrElse(it) { 0L } + b.hours.getOrElse(it) { 0L } },
            days = addMaps(a.days, b.days) { x, y -> x + y },
            trackDays = addDayMaps(a.trackDays, b.trackDays),
            artistDays = addDayMaps(a.artistDays, b.artistDays),
            albumDays = addDayMaps(a.albumDays, b.albumDays),
        )
    }

    /**
     * What [a] has that [b] doesn't: every counter of [b] taken from [a], never
     * below zero, with entries left at nothing dropped.
     *
     * Clamped rather than allowed negative because the only use is "what was
     * listened to since [b] was taken", where [b] is an earlier copy of [a] — a
     * counter can only have grown. One that seems to have shrunk was pruned in
     * between, and the honest answer for it is "nothing new".
     */
    fun minus(a: StoredBucket, b: StoredBucket): StoredBucket {
        require(a.month == b.month) { "Can't subtract ${b.month} from ${a.month}" }
        val oldTracks = b.tracks.associateBy { it.id }
        val oldArtists = b.artists.associateBy(::artistKey)
        val oldAlbums = b.albums.associateBy(::albumKey)
        return StoredBucket(
            version = a.version,
            month = a.month,
            tracks = a.tracks.mapNotNull { entry ->
                val old = oldTracks[entry.id] ?: return@mapNotNull entry.deepCopy()
                entry.deepCopy().apply {
                    ms = (ms - old.ms).coerceAtLeast(0)
                    plays = (plays - old.plays).coerceAtLeast(0)
                    days = subtractDays(days, old.days)
                }.takeIf { it.ms > 0 || it.plays > 0 }
            },
            artists = a.artists.mapNotNull { it.minus(oldArtists[artistKey(it)]) },
            albums = a.albums.mapNotNull { it.minus(oldAlbums[albumKey(it)]) },
            hours = List(24) { (a.hours.getOrElse(it) { 0L } - b.hours.getOrElse(it) { 0L }).coerceAtLeast(0) },
            days = a.days.mapNotNull { (day, ms) ->
                val left = ms - (b.days[day] ?: 0L)
                if (left > 0) day to left else null
            }.toMap(),
            trackDays = a.trackDays?.let { subtractDays(it.toMutableMap(), b.trackDays.orEmpty()) },
            artistDays = a.artistDays?.let { subtractDays(it.toMutableMap(), b.artistDays.orEmpty()) },
            albumDays = a.albumDays?.let { subtractDays(it.toMutableMap(), b.albumDays.orEmpty()) },
        )
    }

    /** Whether [bucket] holds no listening at all. */
    fun isEmpty(bucket: StoredBucket): Boolean =
        bucket.tracks.isEmpty() && bucket.hours.all { it == 0L } && bucket.days.values.all { it == 0L }

    /**
     * The bytes a month is uploaded as, and hashed by. Compact, and in a fixed
     * field order, so the same month always hashes the same.
     */
    fun canonicalBytes(bucket: StoredBucket): ByteArray =
        canonical.encodeToString(StoredBucket.serializer(), bucket).toByteArray()

    fun decode(bytes: ByteArray): StoredBucket =
        canonical.decodeFromString(StoredBucket.serializer(), bytes.decodeToString())

    /** Hex SHA-256 of [bytes]: what a month is recognised by between syncs. */
    fun hash(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    // ---- Keys -------------------------------------------------------------

    /**
     * The same keys [ListeningStats] files entries under once loaded: the lead
     * artist, lower-cased; an album by its id, or by title and artist.
     */
    private fun artistKey(entry: NameEntry): String =
        (ListeningStats.primaryArtist(entry.name) ?: entry.name).lowercase(Locale.ROOT)

    private fun albumKey(entry: NameEntry): String =
        entry.id ?: (entry.name.lowercase(Locale.ROOT) + SEPARATOR + entry.sub.orEmpty().lowercase(Locale.ROOT))

    private val SEPARATOR = Char(31).toString()

    // ---- Plumbing ---------------------------------------------------------

    private fun TrackEntry.deepCopy() = copy(days = days.mapValuesTo(mutableMapOf()) { it.value.copy() })

    private fun NameEntry.deepCopy() = copy(days = days.mapValuesTo(mutableMapOf()) { it.value.copy() })

    private fun NameEntry.minus(old: NameEntry?): NameEntry? {
        if (old == null) return deepCopy()
        return deepCopy().apply {
            ms = (ms - old.ms).coerceAtLeast(0)
            plays = (plays - old.plays).coerceAtLeast(0)
            days = subtractDays(days, old.days)
        }.takeIf { it.ms > 0 || it.plays > 0 }
    }

    private fun subtractDays(
        from: MutableMap<String, DayStats>,
        take: Map<String, DayStats>,
    ): MutableMap<String, DayStats> {
        val out = mutableMapOf<String, DayStats>()
        from.forEach { (day, stats) ->
            val old = take[day]
            val left = DayStats(
                ms = (stats.ms - (old?.ms ?: 0L)).coerceAtLeast(0),
                plays = (stats.plays - (old?.plays ?: 0)).coerceAtLeast(0),
            )
            if (left.ms > 0 || left.plays > 0) out[day] = left
        }
        return out
    }

    private fun addDayMaps(a: Map<String, DayStats>?, b: Map<String, DayStats>?): Map<String, DayStats>? {
        if (a == null && b == null) return null
        // Copied going in: DayStats counts in place once loaded, and the result
        // must not share a counter with either argument.
        return addMaps(
            a.orEmpty().mapValues { it.value.copy() },
            b.orEmpty().mapValues { it.value.copy() },
        ) { x, y -> DayStats(x.ms + y.ms, x.plays + y.plays) }
    }

    private inline fun <K, V> addMaps(a: Map<K, V>, b: Map<K, V>, add: (V, V) -> V): Map<K, V> {
        val out = LinkedHashMap(a)
        b.forEach { (key, value) -> out[key] = out[key]?.let { add(it, value) } ?: value }
        return out
    }
}
