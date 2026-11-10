package com.music.bitchord.data

import android.content.Context
import android.util.AtomicFile
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.playlist.PlaylistIndexBuilder
import com.music.bitchord.data.playlist.preservePlaylistAddedOrder
import com.music.bitchord.data.playlist.comparePlaylistSnapshots
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Durable playlist-only cache. Stores track metadata, never signed streaming URLs,
 * continuation tokens or cookies. The key includes the Google account and channel.
 *
 * Network pagination is detached from the detail-screen lifecycle: dismissing a
 * playlist or starting playback cannot invalidate a completed background scan.
 */
object PlaylistSnapshotCache {
    const val REVALIDATE_AFTER_MS = 6L * 60 * 60 * 1000
    private const val RECENT_DAYS = 7L
    private const val MAX_IDLE_MS = 30L * 24 * 60 * 60 * 1000
    private const val MAX_CACHE_BYTES = 96L * 1024 * 1024
    private const val MAX_SNAPSHOT_BYTES = 24L * 1024 * 1024
    private const val MAX_SCAN_PAGES = 200
    private const val INTER_PAGE_DELAY_MS = 150L
    private const val WORK_NAME = "youtube_playlist_snapshot_sync"
    private const val SCHEMA = 1

    data class Snapshot(
        val songs: List<Song>,
        val checkedAt: Long,
        val title: String? = null,
        val subtitle: String? = null,
        val thumbnailUrl: String? = null,
        val owned: Boolean? = null,
    )
    data class Update(
        val listener: String,
        val browseId: String,
        val songs: List<Song>,
        val continuation: String?,
        val complete: Boolean,
        val owned: Boolean?,
    )
    private data class Key(val listener: String, val browseId: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val memory = ConcurrentHashMap<Key, Snapshot>()
    private val locks = ConcurrentHashMap<Key, Any>()
    private val revisions = ConcurrentHashMap<Key, AtomicLong>()
    private val jobs = ConcurrentHashMap<Key, Job>()
    private val updatesFlow = MutableSharedFlow<Update>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val updates = updatesFlow.asSharedFlow()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    fun isPlaylist(browseId: String): Boolean = browseId.startsWith("VL")

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()
        val work = PeriodicWorkRequestBuilder<PlaylistSnapshotWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, work,
        )
    }

    suspend fun read(context: Context, listener: String, browseId: String): Snapshot? =
        withContext(Dispatchers.IO) {
            val key = Key(listener, browseId)
            synchronized(lock(key)) { readLocked(context, key) }
        }

    /**
     * Only one continuation walk per account/profile/playlist. Cached pages
     * remain visible until the entire replacement snapshot has succeeded.
     */
    fun requestSync(
        context: Context,
        listener: String,
        browseId: String,
        firstPage: YtMusicRepository.SongPage? = null,
        force: Boolean = false,
    ): Job {
        val key = Key(listener, browseId)
        synchronized(jobs) {
            jobs[key]?.takeIf { it.isActive }?.let { return it }
            val appContext = context.applicationContext
            val job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                sync(appContext, key, firstPage, force)
            }
            jobs[key] = job
            job.invokeOnCompletion { jobs.remove(key, job) }
            job.start()
            return job
        }
    }

    private suspend fun sync(
        context: Context,
        key: Key,
        firstPage: YtMusicRepository.SongPage?,
        force: Boolean,
    ) {
        if (activeListener(context) != key.listener) return
        val cached = read(context, key.listener, key.browseId)
        if (!force && firstPage == null && cached != null &&
            System.currentTimeMillis() - cached.checkedAt < REVALIDATE_AFTER_MS
        ) return
        val startedRevision = revision(key).get()
        val first = firstPage ?: YtMusicRepository.browseSongs(key.browseId).getOrNull() ?: return
        val builder = PlaylistIndexBuilder()
        if (!builder.addPage(first.songs, first.continuation)) return

        // Only first-time loads stream partial rows to the UI; an existing full
        // cache must never be replaced by a partial refresh or a failed scan.
        val progressive = cached == null
        if (progressive) updatesFlow.emit(Update(
            key.listener, key.browseId, builder.progress(), first.continuation,
            complete = false, owned = first.owned,
        ))
        var token = first.continuation
        var pages = 1
        while (token != null) {
            if (activeListener(context) != key.listener) return
            if (pages++ >= MAX_SCAN_PAGES) return
            delay(INTER_PAGE_DELAY_MS)
            val page = YtMusicRepository.moreSongs(token).getOrNull() ?: return
            if (!builder.addPage(page.songs, page.continuation)) return
            token = page.continuation
            if (progressive) updatesFlow.emit(Update(
                key.listener, key.browseId, builder.progress(), token,
                complete = false, owned = first.owned,
            ))
        }
        val complete = builder.completeSnapshot() ?: return
        val dated = preservePlaylistAddedOrder(cached?.songs, complete)
        val newSnapshot = Snapshot(
            songs = dated,
            checkedAt = System.currentTimeMillis(),
            title = first.header?.title ?: cached?.title,
            subtitle = first.header?.subtitle ?: cached?.subtitle,
            thumbnailUrl = first.header?.thumbnailUrl ?: cached?.thumbnailUrl,
            owned = first.owned ?: cached?.owned,
        )
        val changed = withContext(Dispatchers.IO) {
            synchronized(lock(key)) {
                if (startedRevision != revision(key).get() ||
                    activeListener(context) != key.listener) null
                else {
                    // Persist the successful check time, but don't ask the UI to
                    // rebuild the full playlist when nothing visible changed.
                    val previous = readLocked(context, key)
                    val visibleChange = previous == null ||
                        comparePlaylistSnapshots(previous.songs, dated).changed ||
                        previous.owned != newSnapshot.owned ||
                        previous.title != newSnapshot.title ||
                        previous.subtitle != newSnapshot.subtitle ||
                        previous.thumbnailUrl != newSnapshot.thumbnailUrl
                    writeLocked(context, key, newSnapshot)
                    visibleChange
                }
            }
        }
        if (changed == true) updatesFlow.emit(Update(
            key.listener, key.browseId, newSnapshot.songs, continuation = null,
            complete = true, owned = newSnapshot.owned,
        ))
    }

    /** Apply a confirmed local edit immediately and force the next full recheck. */
    fun mutate(
        context: Context,
        listener: String,
        browseId: String,
        transform: (List<Song>) -> List<Song>,
    ) {
        val key = Key(listener, browseId)
        revision(key).incrementAndGet()
        scope.launch {
            if (activeListener(context) != key.listener) return@launch
            val current: Snapshot
            val updated: Snapshot
            synchronized(lock(key)) {
                current = readLocked(context, key) ?: return@launch
                updated = current.copy(
                    songs = preservePlaylistAddedOrder(current.songs, transform(current.songs)),
                    checkedAt = 0L,
                )
                writeLocked(context, key, updated)
            }
            updatesFlow.emit(Update(listener, browseId, updated.songs, null, true, updated.owned))
        }
    }

    fun remove(context: Context, listener: String, browseId: String) {
        val key = Key(listener, browseId)
        revision(key).incrementAndGet()
        scope.launch {
            synchronized(lock(key)) {
                memory.remove(key)
                AtomicFile(fileFor(context, key)).delete()
            }
        }
    }

    suspend fun recentlyOpened(context: Context, listener: String): List<String> =
        withContext(Dispatchers.IO) {
            val dir = cacheDirectory(context)
            val now = System.currentTimeMillis()
            dir.listFiles().orEmpty()
                .filter { it.isFile && now - it.lastModified() <= RECENT_DAYS * 24 * 60 * 60 * 1000 }
                .sortedByDescending(File::lastModified)
                .mapNotNull { file ->
                    runCatching {
                        if (file.length() > MAX_SNAPSHOT_BYTES) return@runCatching null
                        val parsed = json.decodeFromString<StoredSnapshot>(
                            AtomicFile(file).openRead().bufferedReader().use { it.readText() },
                        )
                        parsed.browseId.takeIf { parsed.schema == SCHEMA && parsed.listener == listener }
                    }.getOrNull()
                }
                .distinct().take(2)
        }

    private fun activeListener(context: Context): String? {
        val auth = AuthStore(context.applicationContext)
        val account = auth.activeSession?.accountId ?: return "anonymous"
        val profile = auth.activeProfileId ?: return null
        return "$account:$profile"
    }

    private fun lock(key: Key): Any = locks.computeIfAbsent(key) { Any() }
    private fun revision(key: Key) = revisions.computeIfAbsent(key) { AtomicLong() }

    private fun readLocked(context: Context, key: Key): Snapshot? {
        memory[key]?.let { return it }
        val file = fileFor(context, key)
        if (!file.exists() || (file.length() <= 0L || file.length() > MAX_SNAPSHOT_BYTES)) return null
        return runCatching {
            val raw = AtomicFile(file).openRead().bufferedReader().use { it.readText() }
            val stored = json.decodeFromString<StoredSnapshot>(raw)
            if (stored.schema != SCHEMA || stored.listener != key.listener ||
                stored.browseId != key.browseId
            ) return null
            val decoded = stored.songs.map { it.toSong() }
            val ordered = if (decoded.any { it.playlistAddedOrdinal == null }) {
                preservePlaylistAddedOrder(decoded, decoded)
            } else decoded
            val snapshot = Snapshot(
                songs = ordered,
                checkedAt = stored.checkedAt,
                title = stored.title,
                subtitle = stored.subtitle,
                thumbnailUrl = stored.thumbnailUrl,
                owned = stored.owned,
            )
            memory[key] = snapshot
            file.setLastModified(System.currentTimeMillis())
            snapshot
        }.getOrNull()
    }

    private fun writeLocked(context: Context, key: Key, snapshot: Snapshot) {
        val raw = json.encodeToString(StoredSnapshot(
            listener = key.listener,
            browseId = key.browseId,
            checkedAt = snapshot.checkedAt,
            title = snapshot.title,
            subtitle = snapshot.subtitle,
            thumbnailUrl = snapshot.thumbnailUrl,
            owned = snapshot.owned,
            songs = snapshot.songs.map(StoredSong::from),
        ))
        val bytes = raw.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_SNAPSHOT_BYTES) return
        val atomic = AtomicFile(fileFor(context, key))
        val output = atomic.startWrite()
        try {
            output.write(bytes)
            atomic.finishWrite(output)
            memory[key] = snapshot
        } catch (e: Exception) {
            atomic.failWrite(output)
            throw e
        }
        prune(context)
    }

    private fun fileFor(context: Context, key: Key): File {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest((key.listener + "\u0000" + key.browseId).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(cacheDirectory(context), "$hash.json")
    }
    private fun cacheDirectory(context: Context) =
        File(context.filesDir, "playlist-snapshots").apply { mkdirs() }

    private fun prune(context: Context) {
        val files = cacheDirectory(context).listFiles().orEmpty()
            .filter { it.isFile }.sortedByDescending(File::lastModified)
        val now = System.currentTimeMillis()
        var size = 0L
        files.forEach { file ->
            size += file.length()
            if (now - file.lastModified() > MAX_IDLE_MS || size > MAX_CACHE_BYTES) {
                file.delete()
            }
        }
    }

    @Serializable
    private data class StoredSnapshot(
        val schema: Int = SCHEMA,
        val listener: String,
        val browseId: String,
        val checkedAt: Long,
        val title: String? = null,
        val subtitle: String? = null,
        val thumbnailUrl: String? = null,
        val owned: Boolean? = null,
        val songs: List<StoredSong>,
    )
    @Serializable
    private data class StoredArtist(val name: String, val browseId: String? = null)

    @Serializable
    private data class StoredSong(
        val videoId: String,
        val title: String,
        val artist: String,
        val thumbnailUrl: String? = null,
        val durationText: String? = null,
        val artistId: String? = null,
        val artists: List<StoredArtist> = emptyList(),
        val albumId: String? = null,
        val albumName: String? = null,
        val isVideo: Boolean = false,
        val isVideoOrigin: Boolean = false,
        val setVideoId: String? = null,
        val playlistAddedOrdinal: Long? = null,
        val trackNumber: Int? = null,
        val sourceQuality: String? = null,
        val sourceHiRes: Boolean = false,
        val isExplicit: Boolean? = null,
        val playbackSource: String? = null,
        val playbackSourceType: String? = null,
        val playbackSourceId: String? = null,
    ) {
        fun toSong(): Song = Song(
            videoId = videoId, title = title, artist = artist, thumbnailUrl = thumbnailUrl,
            durationText = durationText, artistId = artistId,
            artists = artists.map { ArtistRef(it.name, it.browseId) },
            albumId = albumId, albumName = albumName, isVideo = isVideo,
            isVideoOrigin = isVideoOrigin, setVideoId = setVideoId,
            playlistAddedOrdinal = playlistAddedOrdinal,
            trackNumber = trackNumber, sourceQuality = sourceQuality, sourceHiRes = sourceHiRes,
            isExplicit = isExplicit, playbackSource = playbackSource,
            playbackSourceType = playbackSourceType?.let {
                runCatching { PlaybackSourceType.valueOf(it) }.getOrNull()
            },
            playbackSourceId = playbackSourceId,
        )
        companion object {
            fun from(song: Song): StoredSong = StoredSong(
                videoId = song.videoId, title = song.title, artist = song.artist,
                thumbnailUrl = song.thumbnailUrl, durationText = song.durationText,
                artistId = song.artistId,
                artists = song.artists.map { StoredArtist(it.name, it.browseId) },
                albumId = song.albumId, albumName = song.albumName, isVideo = song.isVideo,
                isVideoOrigin = song.isVideoOrigin, setVideoId = song.setVideoId,
                playlistAddedOrdinal = song.playlistAddedOrdinal,
                trackNumber = song.trackNumber, sourceQuality = song.sourceQuality,
                sourceHiRes = song.sourceHiRes, isExplicit = song.isExplicit,
                playbackSource = song.playbackSource,
                playbackSourceType = song.playbackSourceType?.name,
                playbackSourceId = song.playbackSourceId,
            )
        }
    }
}

/** Limited, Wi-Fi-only revalidation of recently visited playlists. No audio downloads. */
class PlaylistSnapshotWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val auth = AuthStore(applicationContext)
        val account = auth.activeSession?.accountId ?: return Result.success()
        val profile = auth.activeProfileId ?: return Result.success()
        val listener = "$account:$profile"
        PlaylistSnapshotCache.recentlyOpened(applicationContext, listener).forEach { browseId ->
            PlaylistSnapshotCache.requestSync(applicationContext, listener, browseId).join()
        }
        return Result.success()
    }
}
