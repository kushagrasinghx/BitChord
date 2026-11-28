package com.music.bitchord.playback.offlinebackup

import com.music.bitchord.data.offlinebackup.OfflineBackupDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Background rolling LRU cache manager for Offline Backup audio files.
 *
 * - Stores files in the app's private internal directory ([cacheDir]).
 * - Enforces a strict storage cap ([maxBytes], default 500 MB) and track count cap
 *   ([maxTracks], default 50 tracks).
 * - Downloads/writes into a `.tmp` file first and atomically promotes it only after
 *   completion; interrupted or failed writes are deleted immediately so corrupted
 *   files are never registered or played.
 */
class OfflineBackupCacheManager(
    private val cacheDir: File,
    private val dao: OfflineBackupDao,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val maxTracks: Int = DEFAULT_MAX_TRACKS,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val localCopyProvider: ((songId: String, sink: OutputStream) -> Boolean)? = null,
    private val onCacheChanged: () -> Unit = {},
) {

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val fsLock = Any()

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        cleanupPartialFiles()
    }

    /**
     * Deletes any leftover `.tmp` files from interrupted downloads.
     */
    fun cleanupPartialFiles() {
        val files = cacheDir.listFiles() ?: return
        for (file in files) {
            if (file.isFile && file.name.endsWith(TEMP_SUFFIX)) {
                runCatching { file.delete() }
            }
        }
    }

    /**
     * Enqueues a background cache write for [songId] if not already cached or in flight.
     */
    fun enqueueCache(
        songId: String,
        remoteUrl: String?,
        headers: Map<String, String> = emptyMap(),
    ) {
        if (songId.isBlank()) return
        val existingPath = dao.getTrack(songId)?.localCachePath
        if (existingPath != null && File(existingPath).let { it.isFile && it.length() > 0L }) {
            return
        }
        if (activeJobs.containsKey(songId)) return

        val job = scope.launch {
            try {
                cacheNow(songId, remoteUrl, headers)
            } finally {
                activeJobs.remove(songId)
            }
        }
        val previous = activeJobs.putIfAbsent(songId, job)
        if (previous != null) {
            job.cancel()
        }
    }

    /**
     * Synchronously caches [songId] into [cacheDir], enforces LRU caps, and updates [dao].
     * Returns the final [File] if successful, or null if caching failed or was aborted.
     */
    fun cacheNow(
        songId: String,
        remoteUrl: String?,
        headers: Map<String, String> = emptyMap(),
        streamDownloader: ((url: String, headers: Map<String, String>, sink: OutputStream) -> Unit)? = null,
    ): File? {
        if (songId.isBlank()) return null
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val safeName = sanitizeFileName(songId)
        val finalFile = File(cacheDir, "$safeName$AUDIO_SUFFIX")
        val tempFile = File(cacheDir, "$safeName.${System.nanoTime()}$TEMP_SUFFIX")

        if (finalFile.isFile && finalFile.length() > 0L) {
            synchronized(fsLock) {
                dao.markCached(songId, finalFile.absolutePath, finalFile.length())
                evictIfNeeded()
            }
            onCacheChanged()
            return finalFile
        }

        var succeeded = false
        try {
            val copiedFromLocal = FileOutputStream(tempFile).use { out ->
                val copied = localCopyProvider?.invoke(songId, out) == true
                if (copied) {
                    out.flush()
                    true
                } else {
                    false
                }
            }

            if (!copiedFromLocal) {
                val url = remoteUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    ?: return null
                FileOutputStream(tempFile).use { out ->
                    if (streamDownloader != null) {
                        streamDownloader(url, headers, out)
                    } else {
                        downloadWithHttp(url, headers, out)
                    }
                    out.flush()
                }
            }

            val size = tempFile.length()
            if (size <= 0L) {
                return null
            }

            synchronized(fsLock) {
                if (finalFile.exists()) finalFile.delete()
                if (!tempFile.renameTo(finalFile)) {
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                }
                val finalSize = finalFile.length()
                if (finalSize <= 0L) {
                    finalFile.delete()
                    return null
                }
                dao.markCached(songId, finalFile.absolutePath, finalSize)
                evictIfNeeded()
                succeeded = true
            }
            onCacheChanged()
            return finalFile
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            return null
        } finally {
            if (!succeeded && tempFile.exists()) {
                runCatching { tempFile.delete() }
            }
        }
    }

    /**
     * Enforces [maxBytes] and [maxTracks] by deleting the oldest / least-recently-played
     * cached files and clearing their `local_cache_path` in [dao].
     *
     * Returns the list of evicted `song_id`s.
     */
    fun evictIfNeeded(): List<String> = synchronized(fsLock) {
        val lruTracks = dao.getCachedTracksLeastRecentlyUsed()
        val missingIds = mutableListOf<String>()
        val validEntries = mutableListOf<Pair<String, File>>()
        var totalBytes = 0L

        for (track in lruTracks) {
            val path = track.localCachePath
            val file = path?.let(::File)
            if (file == null || !file.isFile || file.length() <= 0L) {
                missingIds += track.songId
            } else {
                val len = file.length()
                validEntries += track.songId to file
                totalBytes += len
            }
        }

        val evictedIds = mutableListOf<String>()
        evictedIds += missingIds

        var remainingCount = validEntries.size
        var index = 0
        while (index < validEntries.size && (totalBytes > maxBytes || remainingCount > maxTracks)) {
            val (songId, file) = validEntries[index]
            val len = file.length()
            runCatching { file.delete() }
            totalBytes -= len
            remainingCount -= 1
            evictedIds += songId
            index += 1
        }

        if (evictedIds.isNotEmpty()) {
            dao.clearCachePaths(evictedIds)
        }
        evictedIds
    }

    private fun downloadWithHttp(url: String, headers: Map<String, String>, out: OutputStream) {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }
        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} downloading offline backup track")
            }
            val body = response.body ?: throw IOException("Empty response body")
            body.byteStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                }
            }
        }
    }

    private fun sanitizeFileName(songId: String): String =
        songId.replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        const val DEFAULT_MAX_BYTES: Long = 500L * 1024L * 1024L
        const val DEFAULT_MAX_TRACKS: Int = 50
        const val CACHE_DIR_NAME = "offline_backup_cache"
        private const val AUDIO_SUFFIX = ".audio"
        private const val TEMP_SUFFIX = ".tmp"

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
