package com.music.bitchord.playback

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.model.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object LastPlayed {
    class Snapshot(val songs: List<Song>, val index: Int, val positionMs: Long)
    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }
    fun init(context: Context) { prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    internal fun window(size: Int, index: Int): IntRange {
        if (size <= 0) return IntRange.EMPTY
        val safe = index.coerceIn(0, size - 1); val start = (safe - MAX_QUEUE_HISTORY).coerceAtLeast(0)
        return start until (safe + 1 + KEEP_AHEAD).coerceAtMost(size)
    }
    fun saveQueue(songs: List<Song>, index: Int) {
        if (songs.isEmpty()) { clear(); return }
        val safe = index.coerceIn(songs.indices); val bounds = window(songs.size, safe)
        val encoded = runCatching { json.encodeToString(StoredQueue.serializer(), StoredQueue(songs.subList(bounds.first, bounds.last + 1).map(StoredTrack::from))) }.getOrNull() ?: return
        prefs.edit().putString(KEY_QUEUE, encoded).putInt(KEY_START, bounds.first).putInt(KEY_INDEX, safe - bounds.first).apply()
    }
    fun saveQueueImmediately(songs: List<Song>, index: Int, positionMs: Long) {
        if (songs.isEmpty()) { clearImmediately(); return }
        val safe = index.coerceIn(songs.indices); val bounds = window(songs.size, safe)
        val encoded = runCatching { json.encodeToString(StoredQueue.serializer(), StoredQueue(songs.subList(bounds.first, bounds.last + 1).map(StoredTrack::from))) }.getOrNull() ?: return
        prefs.edit().putString(KEY_QUEUE, encoded).putInt(KEY_START, bounds.first).putInt(KEY_INDEX, safe - bounds.first).putLong(KEY_POSITION, positionMs.coerceAtLeast(0L)).commit()
    }
    fun savePlaybackState(index: Int, positionMs: Long) {
        if (!prefs.contains(KEY_QUEUE)) return
        val start = prefs.getInt(KEY_START, 0)
        prefs.edit().putInt(KEY_INDEX, (index - start).coerceAtLeast(0)).putLong(KEY_POSITION, positionMs.coerceAtLeast(0L)).apply()
    }
    fun load(): Snapshot? {
        val raw = prefs.getString(KEY_QUEUE, null) ?: return null
        val stored = runCatching { json.decodeFromString<StoredQueue>(raw) }.getOrNull() ?: return null
        if (stored.tracks.isEmpty()) return null
        val songs = stored.tracks.map(StoredTrack::toSong)
        val index = prefs.getInt(KEY_INDEX, stored.legacyIndex ?: 0)
        return Snapshot(songs, index.coerceIn(songs.indices), prefs.getLong(KEY_POSITION, stored.legacyPositionMs ?: 0L).coerceAtLeast(0L))
    }
    fun clear() { prefs.edit().clear().apply() }
    fun clearImmediately() { prefs.edit().clear().commit() }
    @Serializable private data class StoredQueue(val tracks: List<StoredTrack>, val index: Int? = null, val positionMs: Long? = null) { val legacyIndex get() = index; val legacyPositionMs get() = positionMs }
    @Serializable private data class StoredTrack(val id: String, val title: String, val artist: String, val artwork: String? = null, val auto: Boolean = false, val local: String? = null, val path: String? = null, val duration: String? = null, val album: String? = null, val explicit: Boolean? = null, val video: Boolean = false) {
        fun toSong() = Song(videoId = id, title = title, artist = artist, thumbnailUrl = artwork, durationText = duration, albumName = album, isExplicit = explicit, isVideo = video, fromAutoplay = auto, localUri = local, localPath = path)
        companion object { fun from(s: Song) = StoredTrack(s.videoId, s.title, s.artist, s.thumbnailUrl, s.fromAutoplay, s.localUri, s.localPath, s.durationText, s.albumName, s.isExplicit, s.isVideo) }
    }
    private const val KEEP_AHEAD = 50; private const val MAX_QUEUE_HISTORY = 25
    private const val PREFS_NAME = "bitchord_last_played"; private const val KEY_QUEUE = "queue"; private const val KEY_START = "queue_start"; private const val KEY_INDEX = "index"; private const val KEY_POSITION = "position"
}
