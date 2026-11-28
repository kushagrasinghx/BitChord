package com.music.bitchord.data.offlinebackup

import android.content.Context
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.AudioCache
import com.music.bitchord.playback.offlinebackup.OfflineBackupCacheManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.OutputStream

/**
 * Repository coordinating listening history tracking, rolling LRU audio caching,
 * and the dynamic "Offline Backup" playlist.
 */
class OfflineBackupRepository(
    private val dao: OfflineBackupDao,
    private val cacheManager: OfflineBackupCacheManager?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _playlistVersion = MutableStateFlow(0L)
    val playlistVersion: StateFlow<Long> = _playlistVersion.asStateFlow()

    /**
     * Logs a play event for [song], increments `play_count`, updates `last_played_timestamp`,
     * and schedules background caching if [remoteUrl] is provided.
     */
    fun onTrackStreamedOnline(
        song: Song,
        remoteUrl: String?,
        headers: Map<String, String> = emptyMap(),
    ) {
        if (song.localUri != null || song.videoId.isBlank()) return
        dao.recordPlay(
            songId = song.videoId,
            title = song.title,
            artist = song.artist,
            remoteUrl = remoteUrl,
            timestampMs = clock(),
            thumbnailUrl = song.thumbnailUrl,
            durationText = song.durationText,
            albumName = song.albumName,
        )
        cacheManager?.enqueueCache(song.videoId, remoteUrl, headers)
        notifyChanged()
    }

    /**
     * Enqueues background caching for an already-logged [songId] once its stream URL is resolved.
     */
    fun ensureCachedFromStream(
        songId: String,
        remoteUrl: String?,
        headers: Map<String, String> = emptyMap(),
    ) {
        if (songId.isBlank()) return
        cacheManager?.enqueueCache(songId, remoteUrl, headers)
    }

    /**
     * Queries the database for tracks with valid, existing `local_cache_path` files,
     * ordered by `last_played_timestamp DESC, play_count DESC`.
     */
    fun getOfflineBackupPlaylist(): List<Song> {
        val candidates = dao.getOfflineBackupTracks()
        val missingIds = mutableListOf<String>()
        val result = mutableListOf<Song>()

        for (track in candidates) {
            val path = track.localCachePath
            val file = path?.let(::File)
            if (file != null && file.isFile && file.length() > 0L) {
                result += track.toSong().copy(localUri = file.toURI().toString())
            } else {
                missingIds += track.songId
            }
        }

        if (missingIds.isNotEmpty()) {
            dao.clearCachePaths(missingIds)
            notifyChanged()
        }
        return result
    }

    /**
     * Returns a valid, existing local cache file path for [songId], or null if not cached
     * or if the file is missing/empty on disk.
     */
    fun getValidLocalCachePath(songId: String): String? {
        if (songId.isBlank()) return null
        val track = dao.getTrack(songId) ?: return null
        val path = track.localCachePath ?: return null
        val file = File(path)
        if (file.isFile && file.length() > 0L) {
            return path
        }
        dao.clearCachePaths(listOf(songId))
        notifyChanged()
        return null
    }

    fun isTrackCached(songId: String): Boolean = getValidLocalCachePath(songId) != null

    fun getCachedSongIds(): Set<String> =
        getOfflineBackupPlaylist().mapTo(HashSet()) { it.videoId }

    fun notifyChanged() {
        _playlistVersion.value = _playlistVersion.value + 1L
    }

    companion object {
        const val BROWSE_ID = "local:offline_backup"

        @Volatile
        private var instance: OfflineBackupRepository? = null

        private val _globalVersion = MutableStateFlow(0L)
        val globalVersion: StateFlow<Long> = _globalVersion.asStateFlow()

        fun init(context: Context): OfflineBackupRepository {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                val appContext = context.applicationContext
                val db = OfflineBackupDatabase(appContext)
                val cacheDir = File(appContext.noBackupFilesDir, OfflineBackupCacheManager.CACHE_DIR_NAME)
                val repoHolder = arrayOfNulls<OfflineBackupRepository>(1)
                val cacheManager = OfflineBackupCacheManager(
                    cacheDir = cacheDir,
                    dao = db,
                    localCopyProvider = { songId, sink -> copyFromAudioCache(songId, sink) },
                    onCacheChanged = {
                        repoHolder[0]?.notifyChanged()
                        _globalVersion.value = _globalVersion.value + 1L
                    },
                )
                val repo = OfflineBackupRepository(db, cacheManager)
                repoHolder[0] = repo
                instance = repo
                return repo
            }
        }

        fun getOrNull(): OfflineBackupRepository? = instance

        private fun copyFromAudioCache(songId: String, sink: OutputStream): Boolean {
            val copy = AudioCache.completeCopyOf(songId) ?: return false
            return runCatching {
                kotlinx.coroutines.runBlocking {
                    AudioCache.copyOut(copy, sink) { _, _ -> }
                }
            }.isSuccess
        }
    }
}
