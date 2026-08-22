package com.music.bitchord.download

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import com.music.bitchord.data.DebugLog as Log
import androidx.core.content.ContextCompat
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Where a track is between "not on this device" and "on it". */
sealed interface DownloadState {

    /** Accepted, waiting for the one in front of it. */
    data object Queued : DownloadState

    /** [fraction] is 0f until the length is known, which is the first thing asked for. */
    data class Running(val fraction: Float) : DownloadState

    data class Failed(val reason: String) : DownloadState
}

/**
 * The download queue, and the record of what came out of it.
 *
 * Split deliberately into two pieces of state that look similar and behave
 * nothing alike:
 *
 *  - [active] is what is happening now — queued, running, just failed. It lives
 *    in memory, is driven by [DownloadService], and is empty on a cold start
 *    because a download interrupted by the process dying did not happen.
 *  - [saved] is what exists on disk, keyed by videoId and remembered across
 *    launches. It is the only way the app can answer "do I already have this?"
 *    without a media-store query per row, and the only way it knows *which*
 *    file a track corresponds to when asked to delete it.
 *
 * [saved] is a claim about a folder this app does not own. The user is expected
 * to manage Downloads with a file manager, so an entry here can outlive the
 * file it names — which is why every read of it goes through [savedUri], and
 * why that verifies before it answers.
 */
object Downloads {

    private const val TAG = "BitChord"
    private const val KEY_SAVED_METADATA = "downloaded_tracks_metadata"

    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), String.serializer())
    private val metadataSerializer = MapSerializer(String.serializer(), SavedSongMetadata.serializer())

    private val _active = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val active: StateFlow<Map<String, DownloadState>> = _active.asStateFlow()

    private val _saved = MutableStateFlow<Map<String, String>>(emptyMap())

    /** videoId to the uri of the file saved for it. */
    val saved: StateFlow<Map<String, String>> = _saved.asStateFlow()

    private val _savedMetadata = MutableStateFlow<Map<String, SavedSongMetadata>>(emptyMap())

    /** Waiting, in the order asked for. Guarded by [lock]. */
    private val pending = LinkedHashMap<String, Song>()

    private val lock = Any()

    @Volatile
    private var runningId: String? = null

    @Volatile
    private var runningJob: Job? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("bitchord_settings", Context.MODE_PRIVATE)
        _saved.value = runCatching {
            json.decodeFromString(serializer, prefs.getString(KEY_SAVED, null) ?: "{}")
        }.getOrDefault(emptyMap())
        _savedMetadata.value = runCatching {
            json.decodeFromString(metadataSerializer, prefs.getString(KEY_SAVED_METADATA, null) ?: "{}")
        }.getOrDefault(emptyMap())
    }

    // ---- Asking -------------------------------------------------------------

    /**
     * Queue [song], and make sure something is draining the queue.
     *
     * A track already saved, queued or running is left alone rather than
     * doubled — the menu row shows which of those it is, but a second tap
     * before the sheet updates should still be a no-op.
     */
    fun enqueue(context: Context, song: Song) {
        val id = song.videoId
        synchronized(lock) {
            if (id in pending || id == runningId) return
            pending[id] = song
        }
        _active.value = _active.value + (id to DownloadState.Queued)

        val app = context.applicationContext
        runCatching {
            ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
        }.onFailure {
            // Refused only when the app has no window and no exemption, which
            // means the queue has nothing to drain it and would sit there
            // looking accepted forever.
            Log.w(TAG, "could not start the download service: ${it.message}")
            synchronized(lock) { pending.remove(id) }
            fail(id, "Downloads can't start right now")
        }
    }

    /**
     * Drop [videoId] from the queue, or stop it if it is the one running.
     *
     * Clearing [runningId] is what makes this safe in the gap between a track
     * being dequeued and its job existing: a cancel landing in that window
     * finds no job to stop, but [onRunning] then finds the id it was told to
     * run is no longer the one wanted, and stops it on arrival.
     */
    fun cancel(videoId: String) {
        val job = synchronized(lock) {
            pending.remove(videoId)
            if (videoId != runningId) return@synchronized null
            runningId = null
            runningJob.also { runningJob = null }
        }
        job?.cancel()
        clear(videoId)
    }

    // ---- The record ---------------------------------------------------------

    /**
     * The file saved for [videoId], or null — pruning the record if the file
     * has been deleted from under it.
     *
     * Touches the filesystem, so call it off the main thread.
     */
    suspend fun savedUri(context: Context, videoId: String): Uri? = withContext(Dispatchers.IO) {
        val recorded = _saved.value[videoId] ?: return@withContext null
        val uri = recorded.toUri()
        if (DownloadStore.exists(context, uri)) return@withContext uri
        Log.d(TAG, "$videoId was downloaded but the file is gone; forgetting it")
        forget(videoId)
        null
    }

    /** Delete the file saved for [videoId] and forget it. */
    suspend fun delete(context: Context, videoId: String): Boolean = withContext(Dispatchers.IO) {
        val uri = _saved.value[videoId]?.toUri() ?: return@withContext false
        val deleted = DownloadStore.delete(context, uri)
        forget(videoId)
        deleted
    }

    private fun forget(videoId: String) {
        record(_saved.value - videoId, _savedMetadata.value - videoId)
    }

    /**
     * Record one file under every id it could be asked about.
     *
     * [asked] is the row the user tapped and [fetched] is what was actually
     * downloaded, and for a music video those are two different tracks. Filing
     * it under both is what lets the same song, found later through search,
     * still know it is already on the device. A stale id costs nothing: the
     * verification in [savedUri] prunes whichever one stops resolving.
     */
    private fun remember(asked: Song, fetched: Song, uri: Uri) {
        val ids = setOf(asked.videoId, fetched.videoId)
        val newSaved = _saved.value + ids.associateWith { uri.toString() }
        val metaAsked = SavedSongMetadata(
            videoId = asked.videoId,
            title = asked.title,
            artist = asked.artist,
            thumbnailUrl = asked.thumbnailUrl,
            durationText = asked.durationText,
            uri = uri.toString(),
        )
        val metaFetched = SavedSongMetadata(
            videoId = fetched.videoId,
            title = fetched.title,
            artist = fetched.artist,
            thumbnailUrl = fetched.thumbnailUrl,
            durationText = fetched.durationText,
            uri = uri.toString(),
        )
        val newMeta = _savedMetadata.value + mapOf(
            asked.videoId to metaAsked,
            fetched.videoId to metaFetched,
        )
        record(newSaved, newMeta)
    }

    private fun record(savedMap: Map<String, String>, metaMap: Map<String, SavedSongMetadata>) {
        _saved.value = savedMap
        _savedMetadata.value = metaMap
        if (::prefs.isInitialized) {
            prefs.edit()
                .putString(KEY_SAVED, json.encodeToString(serializer, savedMap))
                .putString(KEY_SAVED_METADATA, json.encodeToString(metadataSerializer, metaMap))
                .apply()
        }
    }

    /** Returns all downloaded songs whose files still exist on disk. */
    suspend fun getDownloadedSongs(context: Context): List<Song> = withContext(Dispatchers.IO) {
        val metaMap = _savedMetadata.value
        val result = mutableListOf<Song>()
        val seenUris = mutableSetOf<String>()

        for ((videoId, meta) in metaMap) {
            val uri = meta.uri.toUri()
            if (DownloadStore.exists(context, uri)) {
                if (seenUris.add(meta.uri)) {
                    result.add(
                        Song(
                            videoId = meta.videoId,
                            title = meta.title,
                            artist = meta.artist,
                            thumbnailUrl = meta.thumbnailUrl,
                            durationText = meta.durationText,
                            localUri = meta.uri,
                        )
                    )
                }
            } else {
                forget(videoId)
            }
        }
        result
    }

    private fun String.toUri(): Uri = Uri.parse(this)

    // ---- Driven by DownloadService -----------------------------------------

    /**
     * The next track to fetch, or null when the queue is empty.
     *
     * Claims it as running under the same lock that removed it, so there is no
     * instant where a track is in neither the queue nor the running slot and a
     * [cancel] for it would quietly do nothing.
     */
    internal fun takeNext(): Song? = synchronized(lock) {
        val entry = pending.entries.firstOrNull() ?: return null
        pending.remove(entry.key)
        runningId = entry.key
        entry.value
    }

    /** Attach the job fetching [videoId], unless it has been cancelled meanwhile. */
    internal fun onRunning(videoId: String, job: Job) {
        val cancelled = synchronized(lock) {
            if (runningId != videoId) return@synchronized true
            runningJob = job
            false
        }
        if (cancelled) job.cancel()
    }

    internal fun onIdle() {
        synchronized(lock) {
            runningId = null
            runningJob = null
        }
    }

    /**
     * Fetch one track, start to finish.
     *
     * Everything that can go wrong past the point of reserving a destination
     * has to unreserve it — a cancelled or failed download must not leave a
     * partial file behind pretending to be a whole one, which is what
     * [DownloadStore.Pending] exists to make hard to get wrong.
     *
     * Pinned to [Dispatchers.IO] here rather than trusted to arrive on it.
     * Resolving a stream blocks on HTTP and runs YouTube's player JavaScript
     * through Rhino, and [DownloadService] drives this from a main-thread scope
     * so its notification work stays where it belongs — inheriting that would
     * put every network call in the resolve on the main thread, where they
     * don't fail loudly so much as fail *uniformly*: `NetworkOnMainThreadException`
     * is caught by the same per-client `runCatching` that exists to tolerate a
     * client being turned away, so every client appears to be refused and the
     * whole thing reads as a network outage.
     */
    internal suspend fun run(context: Context, song: Song) = withContext(Dispatchers.IO) {
        val id = song.videoId
        _active.value = _active.value + (id to DownloadState.Running(0f))

        var pending: DownloadStore.Pending? = null
        try {
            // A music-video entry is swapped for the catalogue track behind it,
            // the same way queueing one is. It matters more here: the video's
            // title is where "(Official Video)" lives, and that would be baked
            // into a filename this app never gets to correct.
            val track = runCatching { YtMusicRepository.resolveAudio(song) }.getOrDefault(song)
            val stream = StreamResolver.resolveForDownload(track.videoId)
            Log.d(TAG, "downloading $id at ${stream.kbps}kbps (${stream.mimeType})")

            val name = DownloadStore.fileNameFor(track, stream.downloadExtension)
            // Already there from a previous run the record lost track of —
            // adopt it rather than writing a second copy beside it.
            val alreadyThere = DownloadStore.existing(context, name)
            if (alreadyThere != null) {
                Log.d(TAG, "$name is already in Music; adopting it")
                remember(song, track, alreadyThere)
                clear(id)
                return@withContext
            }

            val destination = DownloadStore.begin(context, name, stream.downloadMimeType)
            pending = destination
            destination.openStream().use { sink ->
                Downloader.fetch(track.videoId, stream, sink) { written, total ->
                    _active.value = _active.value +
                        (id to DownloadState.Running(written.toFloat() / total))
                }
            }
            val savedUri = destination.commit()
            pending = null
            MediaTagger.embed(context, savedUri, track, stream.downloadExtension)
            remember(song, track, savedUri)
            clear(id)
            Log.d(TAG, "saved $name")
        } catch (e: CancellationException) {
            pending?.abort()
            clear(id)
            throw e
        } catch (e: Exception) {
            pending?.abort()
            Log.w(TAG, "download failed for $id: ${e.message}", e)
            fail(id, e.friendly())
        }
    }

    /** Back to "not downloaded" — used for success, where [saved] takes over, and for cancellation. */
    private fun clear(videoId: String) {
        _active.value = _active.value - videoId
    }

    private fun fail(videoId: String, reason: String) {
        _active.value = _active.value + (videoId to DownloadState.Failed(reason))
    }

    /**
     * A failure a user can read. The message on an [error] raised in this
     * package is already written for them; anything else is a network fault
     * with a class name for a message.
     */
    private fun Exception.friendly(): String = when {
        this is IllegalStateException && !message.isNullOrBlank() -> message!!
        else -> "Download failed — check your connection"
    }

    /** Dropped when the sheet is reopened; a failure is worth showing once. */
    fun dismissFailure(videoId: String) {
        if (_active.value[videoId] is DownloadState.Failed) clear(videoId)
    }

    private const val KEY_SAVED = "downloaded_tracks"
}

@kotlinx.serialization.Serializable
internal data class SavedSongMetadata(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
    val durationText: String? = null,
    val uri: String,
)
