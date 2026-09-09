package com.music.bitchord.offline

import android.content.Context
import com.music.bitchord.data.model.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Purely local persistence for user library state. Nothing here performs I/O over the network. */
class OfflineLocalStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun isFavorite(song: Song): Boolean = favoriteIds().contains(songKey(song))

    fun setFavorite(song: Song, favorite: Boolean) {
        val ids = favoriteIds().toMutableSet()
        if (favorite) ids.add(songKey(song)) else ids.remove(songKey(song))
        saveStringSet(KEY_FAVORITES, ids)
    }

    fun favorites(songs: List<Song>): List<Song> {
        val ids = favoriteIds()
        return songs.filter { songKey(it) in ids }
    }

    fun playlistNames(): List<String> = prefs.getStringSet(KEY_PLAYLIST_NAMES, emptySet()).orEmpty().toList().sorted()

    fun createPlaylist(name: String): Boolean {
        val clean = name.trim()
        if (clean.isBlank()) return false
        val names = playlistNames().toMutableSet()
        if (!names.add(clean)) return false
        saveStringSet(KEY_PLAYLIST_NAMES, names)
        savePlaylist(clean, emptyList())
        return true
    }

    fun addToPlaylist(name: String, song: Song) {
        val songs = loadPlaylist(name).toMutableList()
        val key = songKey(song)
        if (songs.none { it.key == key }) {
            songs.add(StoredSong.from(song))
            savePlaylist(name, songs)
        }
    }

    fun removeFromPlaylist(name: String, song: Song) {
        savePlaylist(name, loadPlaylist(name).filterNot { it.key == songKey(song) })
    }

    fun playlist(name: String, songs: List<Song>): List<Song> {
        val stored = loadPlaylist(name).associateBy { it.key }
        return songs.filter { stored.containsKey(songKey(it)) }
    }

    fun recordHistory(song: Song) {
        val updated = listOf(StoredSong.from(song)) + loadHistory().filterNot { it.key == songKey(song) }
        saveHistory(updated.take(MAX_HISTORY))
    }

    fun history(songs: List<Song>): List<Song> {
        val available = songs.associateBy(::songKey)
        return loadHistory().mapNotNull { available[it.key] }
    }

    fun clearHistory() = prefs.edit().remove(KEY_HISTORY).apply()

    fun theme(): Theme = runCatching { Theme.valueOf(prefs.getString(KEY_THEME, Theme.SYSTEM.name)!!) }.getOrDefault(Theme.SYSTEM)
    fun setTheme(theme: Theme) = prefs.edit().putString(KEY_THEME, theme.name).apply()

    fun minimumTrackSeconds(): Int = prefs.getInt(KEY_MIN_DURATION, 30)
    fun setMinimumTrackSeconds(seconds: Int) = prefs.edit().putInt(KEY_MIN_DURATION, seconds.coerceAtLeast(0)).apply()

    fun showUnknownArtists(): Boolean = prefs.getBoolean(KEY_UNKNOWN_ARTISTS, true)
    fun setShowUnknownArtists(enabled: Boolean) = prefs.edit().putBoolean(KEY_UNKNOWN_ARTISTS, enabled).apply()

    private fun favoriteIds(): Set<String> = prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty()
    private fun saveStringSet(key: String, values: Set<String>) = prefs.edit().putStringSet(key, values).apply()

    private fun loadPlaylist(name: String): List<StoredSong> = loadList(KEY_PLAYLIST_PREFIX + name)
    private fun savePlaylist(name: String, songs: List<StoredSong>) = saveList(KEY_PLAYLIST_PREFIX + name, songs)
    private fun loadHistory(): List<StoredSong> = loadList(KEY_HISTORY)
    private fun saveHistory(songs: List<StoredSong>) = saveList(KEY_HISTORY, songs)

    private fun loadList(key: String): List<StoredSong> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<StoredSong>>(raw) }.getOrDefault(emptyList())
    }

    private fun saveList(key: String, songs: List<StoredSong>) {
        prefs.edit().putString(key, json.encodeToString(songs)).apply()
    }

    private fun songKey(song: Song): String = song.localUri ?: song.videoId

    @Serializable
    private data class StoredSong(
        val key: String,
        val title: String,
        val artist: String,
        val artwork: String? = null,
        val album: String? = null,
        val duration: String? = null,
        val path: String? = null,
    ) {
        companion object {
            fun from(song: Song) = StoredSong(
                key = song.localUri ?: song.videoId,
                title = song.title,
                artist = song.artist,
                artwork = song.thumbnailUrl,
                album = song.albumName,
                duration = song.durationText,
                path = song.localPath,
            )
        }
    }

    enum class Theme { SYSTEM, LIGHT, DARK }

    companion object {
        private const val PREFS = "bitchord_offline_library"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_PLAYLIST_NAMES = "playlist_names"
        private const val KEY_PLAYLIST_PREFIX = "playlist:"
        private const val KEY_HISTORY = "history"
        private const val KEY_THEME = "theme"
        private const val KEY_MIN_DURATION = "minimum_track_seconds"
        private const val KEY_UNKNOWN_ARTISTS = "show_unknown_artists"
        private const val MAX_HISTORY = 100

        @Volatile private var instance: OfflineLocalStore? = null
        fun get(context: Context): OfflineLocalStore = instance ?: synchronized(this) {
            instance ?: OfflineLocalStore(context.applicationContext).also { instance = it }
        }
    }
}
