package com.music.bitchord.playback

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.model.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The queue as it stood when the app was last used, so a cold start opens on
 * the track you left off on instead of an empty player.
 *
 * Persisted as its own small record rather than by serialising [Song]: the
 * fields below are all a MediaItem carries, so they are all that survives a
 * round trip through the player anyway, and a stored format is better off not
 * moving every time the domain model does.
 */
object LastPlayed {

    /** A restored queue: the tracks, which one was current, and how far in. */
    class Snapshot(val songs: List<Song>, val index: Int, val positionMs: Long)

    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("bitchord_last_played", Context.MODE_PRIVATE)
    }

    fun save(songs: List<Song>, index: Int, positionMs: Long) {
        if (songs.isEmpty()) return
        // AutoPlay keeps extending the queue, so it can run to hundreds of
        // tracks by the end of an evening. Store a window around where we are
        // instead of the lot — the current track has to be inside it, and what
        // follows is what resuming actually plays.
        val start = (index - KEEP_BEHIND).coerceIn(0, maxOf(0, songs.size - MAX_TRACKS))
        val window = songs.subList(start, minOf(songs.size, start + MAX_TRACKS))
        val stored = StoredQueue(
            tracks = window.map {
                StoredTrack(
                    it.videoId,
                    it.title,
                    it.artist,
                    it.thumbnailUrl,
                    it.fromAutoplay,
                    it.localUri,
                    it.localPath,
                    it.durationText,
                )
            },
            index = (index - start).coerceIn(0, window.lastIndex),
            positionMs = positionMs.coerceAtLeast(0L),
        )
        prefs.edit()
            .putString(
                KEY_QUEUE,
                runCatching { json.encodeToString(StoredQueue.serializer(), stored) }.getOrNull(),
            )
            .apply()
    }

    fun load(): Snapshot? {
        val raw = prefs.getString(KEY_QUEUE, null) ?: return null
        val stored = runCatching { json.decodeFromString<StoredQueue>(raw) }.getOrNull() ?: return null
        if (stored.tracks.isEmpty()) return null
        return Snapshot(
            songs = stored.tracks.map {
                Song(
                    it.id,
                    it.title,
                    it.artist,
                    it.artwork,
                    durationText = it.duration,
                    fromAutoplay = it.auto,
                    localUri = it.local,
                    localPath = it.path,
                )
            },
            index = stored.index.coerceIn(0, stored.tracks.lastIndex),
            positionMs = stored.positionMs.coerceAtLeast(0L),
        )
    }

    @Serializable
    private data class StoredTrack(
        val id: String,
        val title: String,
        val artist: String,
        val artwork: String? = null,
        /** Whether AutoPlay queued it — the queue's sections outlive a restart. */
        val auto: Boolean = false,
        /**
         * Where it plays from on disk, when that is anywhere. Not a detail the
         * player needs to resume — [id] alone finds the file again either way —
         * but it is what the UI reads to tell a track off the device from one
         * off YouTube, and a restored queue that dropped it had the player's
         * menu offering to rate, download and share a local file.
         */
        val local: String? = null,
        val path: String? = null,
        /**
         * How long the track runs, as the row that queued it said.
         *
         * Carried across a restart because it is what a cross-source match is
         * made on — see [TrackMatcher][com.music.bitchord.data.sources.TrackMatcher].
         * Dropping it did not look like it cost anything: nothing on screen
         * reads a queue row's duration, since the player takes its own from
         * the decoder. But every duration-based rule in the matcher degrades
         * silently to nothing without it, so a track resumed after a restart
         * was matched on title and artist alone while the same track queued
         * from a search was matched properly. That is the worst shape a bug
         * can have — the same song behaving differently depending on how long
         * ago the app was opened.
         */
        val duration: String? = null,
    )

    @Serializable
    private data class StoredQueue(
        val tracks: List<StoredTrack>,
        val index: Int,
        val positionMs: Long,
    )

    /** How much of what's already been played is worth keeping for "previous". */
    private const val KEEP_BEHIND = 10

    private const val MAX_TRACKS = 60

    private const val KEY_QUEUE = "queue"
}
