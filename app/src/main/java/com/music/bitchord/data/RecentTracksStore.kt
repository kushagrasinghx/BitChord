package com.music.bitchord.data

import android.content.Context
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.ShelfLayout
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * On-device persistent store for recently played tracks.
 *
 * Persists the user's latest played songs locally so that the Home screen
 * on both Mobile and Android TV can render a "Recents" shelf immediately on
 * cold start without waiting for any network round-trips.
 */
object RecentTracksStore {
    private const val FILE_NAME = "recent_tracks.json"
    private const val MAX_RECENT_TRACKS = 30
    private const val SHELF_TITLE = "Recents"

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var inMemoryCache: MutableList<Song>? = null
    internal var testDir: File? = null

    internal fun initForTesting(dir: File) {
        testDir = dir
        inMemoryCache = null
    }

    private fun getStorageFile(context: Context?): File {
        val parent = testDir ?: context?.filesDir ?: File(".")
        return File(parent, FILE_NAME)
    }

    /**
     * Records a played track into local storage, moving it to index 0.
     * Deduplicates by [Song.videoId].
     */
    fun recordPlay(context: Context?, song: Song) {
        if (song.videoId.isBlank()) return
        scope.launch {
            recordPlaySynchronous(context, song)
        }
    }

    internal suspend fun recordPlaySynchronous(context: Context?, song: Song) {
        if (song.videoId.isBlank()) return
        mutex.withLock {
            val current = loadInternal(context).toMutableList()
            current.removeAll { it.videoId == song.videoId }
            current.add(0, song)
            val trimmed = if (current.size > MAX_RECENT_TRACKS) {
                current.take(MAX_RECENT_TRACKS).toMutableList()
            } else {
                current
            }
            inMemoryCache = trimmed
            saveInternal(context, trimmed)
        }
    }

    /** Returns the list of cached recently played songs. */
    suspend fun getRecentSongs(context: Context?): List<Song> = withContext(Dispatchers.IO) {
        mutex.withLock { loadInternal(context) }
    }

    /**
     * Converts the local recent songs into a [HomeShelf] ready to display at the head of Home.
     * Returns null if no tracks have been played yet.
     */
    suspend fun asHomeShelf(context: Context?): HomeShelf? = withContext(Dispatchers.IO) {
        val songs = mutex.withLock { loadInternal(context) }
        if (songs.isEmpty()) return@withContext null
        val items = songs.map { song ->
            val subtitle = if (!song.durationText.isNullOrBlank()) {
                "${song.artist} • ${song.durationText}"
            } else {
                song.artist
            }
            ShelfItem(
                title = song.title,
                subtitle = subtitle,
                thumbnailUrl = song.thumbnailUrl,
                videoId = song.videoId,
                browseId = null,
                isVideo = song.isVideo,
            )
        }
        HomeShelf(
            title = SHELF_TITLE,
            items = items,
            subtitle = "Jump back in",
            layout = ShelfLayout.LIST,
        )
    }

    private fun loadInternal(context: Context?): List<Song> {
        inMemoryCache?.let { return it }
        val file = getStorageFile(context)
        if (!file.exists() || file.length() == 0L) return emptyList()
        val songs = runCatching {
            val text = file.readText()
            val array = JSONArray(text)
            val list = mutableListOf<Song>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val videoId = obj.optString("videoId").takeIf { it.isNotBlank() } ?: continue
                val title = obj.optString("title", "")
                val artist = obj.optString("artist", "")
                val thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() }
                val durationText = obj.optString("durationText").takeIf { it.isNotBlank() }
                val albumName = obj.optString("albumName").takeIf { it.isNotBlank() }
                val isVideo = obj.optBoolean("isVideo", false)
                list.add(
                    Song(
                        videoId = videoId,
                        title = title,
                        artist = artist,
                        thumbnailUrl = thumbnailUrl,
                        durationText = durationText,
                        albumName = albumName,
                        isVideo = isVideo,
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
        inMemoryCache = songs.toMutableList()
        return songs
    }

    private fun saveInternal(context: Context?, songs: List<Song>) {
        runCatching {
            val array = JSONArray()
            for (song in songs) {
                val obj = JSONObject()
                obj.put("videoId", song.videoId)
                obj.put("title", song.title)
                obj.put("artist", song.artist)
                song.thumbnailUrl?.let { obj.put("thumbnailUrl", it) }
                song.durationText?.let { obj.put("durationText", it) }
                song.albumName?.let { obj.put("albumName", it) }
                obj.put("isVideo", song.isVideo)
                array.put(obj)
            }
            val file = getStorageFile(context)
            file.writeText(array.toString())
        }
    }
}
