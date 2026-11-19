package com.music.bitchord.data

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.StoredTrack
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Local listening history kept on this device.
 *
 * One bucket per listener: the account and profile that was signed in when the
 * track played, or [GUEST] when nobody was. Scoping matters on a shared phone —
 * signing out must not hand the next person what the last one listened to.
 *
 * Used when YouTube watch history is paused or empty, when signed out, and
 * offline.
 */
object LocalHistoryRepository {

    private const val KEY_HISTORY = "bitchord_local_history"
    private const val GUEST = "guest"

    private lateinit var prefs: SharedPreferences

    /** Which bucket [record] writes and [all] reads; supplied in [init]. */
    @Volatile
    private var listenerKey: () -> String? = { null }

    @Volatile
    private var songs: Map<String, List<Song>> = emptyMap()

    fun all(): List<Song> = songs[owner()].orEmpty()

    /** The bucket [all] currently reads; caches use it to avoid crossing listeners. */
    fun ownerKey(): String = owner()

    fun init(context: Context, listenerKey: () -> String? = { null }) {
        prefs = context.getSharedPreferences("bitchord_settings", Context.MODE_PRIVATE)
        this.listenerKey = listenerKey
        reload()
    }

    @Synchronized
    fun reload() {
        if (!this::prefs.isInitialized) return
        val raw = prefs.getString(KEY_HISTORY, null)
        songs = if (raw == null) emptyMap() else LocalHistoryCodec.decode(raw)
    }

    @Synchronized
    fun record(song: Song) {
        // Streams and placeholders that never resolved have no id. Recording
        // one would leave a blank row in History that cannot be played.
        if (song.videoId.isBlank()) return
        val next = recordInHistory(songs, owner(), song) ?: return
        songs = next
        persist()
    }

    @Synchronized
    fun clear() {
        songs = songs - owner()
        if (!this::prefs.isInitialized) return
        if (songs.isEmpty()) {
            prefs.edit().remove(KEY_HISTORY).apply()
        } else {
            persist()
        }
    }

    private fun owner(): String = listenerKey() ?: GUEST

    private fun persist() {
        if (!this::prefs.isInitialized) return
        prefs.edit().putString(KEY_HISTORY, LocalHistoryCodec.encode(songs)).apply()
    }
}

/** Deep enough to be useful, shallow enough that the file stays small. */
internal const val LOCAL_HISTORY_LIMIT = 100

/**
 * [history] with [song] moved to the top of [owner]'s bucket, or null when it
 * is already there. Only that bucket is touched; the others are left as-is.
 */
internal fun recordInHistory(
    history: Map<String, List<Song>>,
    owner: String,
    song: Song,
    limit: Int = LOCAL_HISTORY_LIMIT,
): Map<String, List<Song>>? {
    val current = history[owner].orEmpty()
    if (current.firstOrNull()?.videoId == song.videoId) return null
    val deduped = current.filterNot { it.videoId == song.videoId }
    return history + (owner to (listOf(song) + deduped).take(limit))
}

/**
 * The on-disk shape of the history: a bucket per listener, newest first.
 *
 * Kept apart from the repository so the format can be round-tripped in a JVM
 * test without Android's SharedPreferences.
 */
internal object LocalHistoryCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(history: Map<String, List<Song>>): String =
        json.encodeToString(history.mapValues { (_, songs) -> songs.map(StoredTrack::from) })

    fun decode(raw: String): Map<String, List<Song>> =
        runCatching {
            json.decodeFromString<Map<String, List<StoredTrack>>>(raw)
        }.getOrDefault(emptyMap())
            .mapValues { (_, tracks) -> tracks.map(StoredTrack::toSong) }
}
