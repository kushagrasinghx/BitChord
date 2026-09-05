package com.music.bitchord.playback

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.model.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A bounded queue snapshot for restoring playback after process death.
 *
 * Queue contents are serialized only when Media3 reports that the playlist
 * changed. The frequently changing index and position are stored separately as
 * primitives, so progress sampling never walks or serializes the song list.
 */
object LastPlayed {

    class Snapshot(val songs: List<Song>, val index: Int, val positionMs: Long)

    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    internal fun window(size: Int, index: Int): IntRange {
        if (size <= 0) return IntRange.EMPTY
        val safeIndex = index.coerceIn(0, size - 1)
        val start = (safeIndex - MAX_QUEUE_HISTORY).coerceAtLeast(0)
        return start until (safeIndex + 1 + KEEP_AHEAD).coerceAtMost(size)
    }

    /** Persist at most 25 previous entries, the current one, and 50 upcoming entries. */
    fun saveQueue(songs: List<Song>, index: Int) {
        if (songs.isEmpty()) {
            clear()
            return
        }
        val safeIndex = index.coerceIn(songs.indices)
        val bounds = window(songs.size, safeIndex)
        val stored = StoredQueue(
            tracks = songs.subList(bounds.first, bounds.last + 1).map { StoredTrack.from(it) },
        )
        val encoded = runCatching {
            json.encodeToString(StoredQueue.serializer(), stored)
        }.getOrNull() ?: return
        prefs.edit()
            .putString(KEY_QUEUE, encoded)
            .putInt(KEY_INDEX, safeIndex - bounds.first)
            .apply()
    }

    /**
     * Atomically replaces the restart snapshot and waits until it reaches disk.
     * Used at an explicit queue boundary, where restoring the queue from before
     * that boundary would be worse than the small cost of a synchronous write.
     */
    fun saveQueueImmediately(songs: List<Song>, index: Int, positionMs: Long) {
        if (songs.isEmpty()) {
            clearImmediately()
            return
        }
        val safeIndex = index.coerceIn(songs.indices)
        val bounds = window(songs.size, safeIndex)
        val stored = StoredQueue(
            tracks = songs.subList(bounds.first, bounds.last + 1).map { StoredTrack.from(it) },
        )
        val encoded = runCatching {
            json.encodeToString(StoredQueue.serializer(), stored)
        }.getOrNull() ?: return
        prefs.edit()
            .putString(KEY_QUEUE, encoded)
            .putInt(KEY_INDEX, safeIndex - bounds.first)
            .putLong(KEY_POSITION, positionMs.coerceAtLeast(0L))
            .commit()
    }

    /** Update only cheap scalar state; this performs no queue conversion or JSON work. */
    fun savePlaybackState(index: Int, positionMs: Long) {
        if (!prefs.contains(KEY_QUEUE)) return
        prefs.edit()
            .putInt(KEY_INDEX, index.coerceAtLeast(0))
            .putLong(KEY_POSITION, positionMs.coerceAtLeast(0L))
            .apply()
    }

    fun load(): Snapshot? {
        val raw = prefs.getString(KEY_QUEUE, null) ?: return null
        val stored = runCatching { json.decodeFromString<StoredQueue>(raw) }.getOrNull()
            ?: return null
        if (stored.tracks.isEmpty()) return null
        val songs = stored.tracks.map(StoredTrack::toSong)
        val index = if (prefs.contains(KEY_INDEX)) {
            prefs.getInt(KEY_INDEX, 0)
        } else {
            stored.legacyIndex ?: 0
        }
        val positionMs = if (prefs.contains(KEY_POSITION)) {
            prefs.getLong(KEY_POSITION, 0L)
        } else {
            stored.legacyPositionMs ?: 0L
        }
        return Snapshot(
            songs = songs,
            index = index.coerceIn(songs.indices),
            positionMs = positionMs.coerceAtLeast(0L),
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    /** Remove a stale queue before another one is installed. */
    fun clearImmediately() {
        prefs.edit().clear().commit()
    }

    @Serializable
    private data class StoredQueue(
        val tracks: List<StoredTrack>,
        /** Compatibility with queue snapshots written before scalar state was separated. */
        val index: Int? = null,
        /** Compatibility with queue snapshots written before scalar state was separated. */
        val positionMs: Long? = null,
    ) {
        val legacyIndex: Int? get() = index
        val legacyPositionMs: Long? get() = positionMs
    }

    @Serializable
    private data class StoredTrack(
        val id: String,
        val title: String,
        val artist: String,
        val artwork: String? = null,
        val auto: Boolean = false,
        val local: String? = null,
        val path: String? = null,
        val lyricsUri: String? = null,
        val lyricsSource: String? = null,
        val lyricsFormat: String? = null,
        val duration: String? = null,
        val album: String? = null,
        val explicit: Boolean? = null,
        val video: Boolean = false,
    ) {
        fun toSong() = Song(
            videoId = id,
            title = title,
            artist = artist,
            thumbnailUrl = artwork,
            durationText = duration,
            albumName = album,
            isExplicit = explicit,
            isVideo = video,
            fromAutoplay = auto,
            localUri = local,
            localPath = path,
            localLyricsUri = lyricsUri,
            localLyricsSource = lyricsSource,
            localLyricsFormat = lyricsFormat,
        )

        companion object {
            fun from(song: Song) = StoredTrack(
                id = song.videoId,
                title = song.title,
                artist = song.artist,
                artwork = song.thumbnailUrl,
                auto = song.fromAutoplay,
                local = song.localUri,
                path = song.localPath,
                lyricsUri = song.localLyricsUri,
                lyricsSource = song.localLyricsSource,
                lyricsFormat = song.localLyricsFormat,
                duration = song.durationText,
                album = song.albumName,
                explicit = song.isExplicit,
                video = song.isVideo,
            )
        }
    }

    private const val KEEP_AHEAD = 50
    private const val PREFS_NAME = "bitchord_last_played"
    private const val KEY_QUEUE = "queue"
    private const val KEY_INDEX = "index"
    private const val KEY_POSITION = "position"
}
