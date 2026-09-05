package com.music.bitchord.download

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.data.lyrics.LyricsArtifact
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.LyricsSerializer
import com.music.bitchord.data.lyrics.toEnhancedLrc
import com.music.bitchord.data.lyrics.toLrc
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.DownloadQuality
import com.music.bitchord.data.sources.SourceResolver
import com.music.bitchord.data.sources.SourceStream
import com.music.bitchord.data.sources.StreamFormat
import com.music.bitchord.data.sources.TrackMatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.io.File
import java.util.Locale

/** Where a track is between "not on this device" and "on it". */
sealed interface DownloadState {

    /** Accepted, waiting for the one in front of it. */
    data object Queued : DownloadState

    /** [fraction] is 0f until the length is known, which is the first thing asked for. */
    data class Running(val fraction: Float) : DownloadState

    data class Failed(val reason: String) : DownloadState
}

/** The lifecycle state of a track's lyrics file. */
enum class LyricsDownloadState {
    NOT_REQUESTED,
    DOWNLOADING,
    SAVED,
    FAILED,
    UNAVAILABLE,
}

/** A queued download item: either a full audio track or lyrics enrichment only. */
data class PendingDownload(
    val song: Song,
    val lyricsOnly: Boolean = false,
    val from: String? = null,
)

/**
 * The download queue, and the record of what came out of it.
 *
 * Downloads both the audio stream and the synchronized lyrics. The lyric lookup
 * is initiated concurrently with the audio transfer, eliminating idle latency
 * while ensuring both the audio file and its matching sidecar are ready together.
 */
object Downloads {

    private const val TAG = "BitChord"
    private const val KEY_SAVED = "downloaded_tracks"
    private const val KEY_SAVED_METADATA = "downloaded_tracks_metadata"
    private const val KEY_SAVED_COLLECTIONS = "downloaded_collections"

    private const val LOSSLESS_LOOKUP_MS = 20_000L
    private const val LYRICS_LOOKUP_MS = 15_000L

    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), String.serializer())
    private val metadataSerializer = MapSerializer(String.serializer(), SavedSongMetadata.serializer())
    private val collectionSerializer = MapSerializer(String.serializer(), SavedCollection.serializer())

    private val _active = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val active: StateFlow<Map<String, DownloadState>> = _active.asStateFlow()

    /**
     * Ids asked for as part of a release's own download tap, by the browseId
     * that was tapped.
     *
     * [active] is one flat map for the whole app — a track queued from one
     * release is still the same row if it happens to sit in another release
     * too, and correctly so. But that means a release page can't tell "one of
     * my tracks is queued" apart from "one of my tracks is queued *because I
     * was asked for*" just by scanning [active] for its own ids: two releases
     * that happen to share a track would both read as downloading the moment
     * either one is. This is what lets a release's header ask the narrower
     * question instead — never pruned explicitly, since a stale id here is
     * harmless once it drops out of [active].
     */
    private val _requested = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val requested: StateFlow<Map<String, Set<String>>> = _requested.asStateFlow()

    /** Record that [videoIds] were asked for as [browseId]'s own release. */
    fun markRequested(browseId: String, videoIds: Collection<String>) {
        if (videoIds.isEmpty()) return
        _requested.update { it + (browseId to (it[browseId].orEmpty() + videoIds)) }
    }

    private val _saved = MutableStateFlow<Map<String, String>>(emptyMap())
    val saved: StateFlow<Map<String, String>> = _saved.asStateFlow()

    private val _savedMetadata = MutableStateFlow<Map<String, SavedSongMetadata>>(emptyMap())
    val savedMetadata: StateFlow<Map<String, SavedSongMetadata>> = _savedMetadata.asStateFlow()

    private val _lyricsActive = MutableStateFlow<Set<String>>(emptySet())
    val lyricsActive: StateFlow<Set<String>> = _lyricsActive.asStateFlow()

    private val _collections = MutableStateFlow<Map<String, SavedCollection>>(emptyMap())
    val collections: StateFlow<Map<String, SavedCollection>> = _collections.asStateFlow()

    /** Waiting, in the order asked for. Guarded by [lock]. */
    private val pending = LinkedHashMap<String, PendingDownload>()

    private val lock = Any()

    /**
     * The tracks taken off the queue and not yet finished, to the job fetching
     * each — null in the gap between a worker claiming a track and its job
     * existing.
     *
     * A map rather than the single slot this used to be, because several
     * downloads run at once now. That plurality is the only reason it is here:
     * [cancel] has to find *this* track's job among several, and a worker
     * claiming the next track must not be able to step on another worker's.
     * Guarded by [lock].
     */
    private val running = LinkedHashMap<String, Job?>()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("bitchord_settings", Context.MODE_PRIVATE)
        _saved.value = runCatching {
            json.decodeFromString(serializer, prefs.getString(KEY_SAVED, null) ?: "{}")
        }.getOrDefault(emptyMap())
        _savedMetadata.value = runCatching {
            json.decodeFromString(metadataSerializer, prefs.getString(KEY_SAVED_METADATA, null) ?: "{}")
        }.getOrDefault(emptyMap())
        _collections.value = runCatching {
            json.decodeFromString(collectionSerializer, prefs.getString(KEY_SAVED_COLLECTIONS, null) ?: "{}")
        }.getOrDefault(emptyMap())
    }

    // ---- Asking -------------------------------------------------------------

    fun enqueue(context: Context, song: Song, from: String? = null) {
        val id = song.videoId
        if (!AppSettings.downloadsAllowedNow) {
            val inFlight = _active.value[id]
            if (inFlight !is DownloadState.Queued && inFlight !is DownloadState.Running) {
                DownloadSession.queued(song, from)
                fail(id, WIFI_ONLY_REFUSAL)
            }
            return
        }
        synchronized(lock) {
            if (id in pending || id in running) return
            pending[id] = PendingDownload(song, lyricsOnly = false, from = from)
        }
        _active.update { it + (id to DownloadState.Queued) }
        DownloadSession.queued(song, from)
        startService(context, id)
    }

    fun enqueueLyrics(context: Context, song: Song) {
        val id = song.videoId
        if (!AppSettings.downloadsAllowedNow) {
            fail(id, WIFI_ONLY_REFUSAL)
            return
        }
        synchronized(lock) {
            if (id in pending || id in running) return
            pending[id] = PendingDownload(song, lyricsOnly = true)
        }
        _lyricsActive.update { it + id }
        startService(context, id)
    }

    private fun startService(context: Context, id: String) {
        val app = context.applicationContext
        runCatching {
            ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
        }.onFailure {
            Log.w(TAG, "could not start the download service: ${it.message}")
            synchronized(lock) { pending.remove(id) }
            _lyricsActive.value = _lyricsActive.value - id
            fail(id, "Downloads can't start right now")
        }
    }

    /**
     * Drop [videoId] from the queue, or stop it if it is one of the ones
     * running.
     *
     * Dropping it from [running] is what makes this safe in the gap between a
     * track being dequeued and its job existing: a cancel landing in that
     * window finds no job to stop, but [onRunning] then finds the id it was
     * told to run is no longer wanted, and stops it on arrival.
     */
    fun cancel(videoId: String) {
        val job = synchronized(lock) {
            pending.remove(videoId)
            if (videoId !in running) return@synchronized null
            running.remove(videoId)
        }
        job?.cancel()
        clear(videoId)
        _lyricsActive.update { it - videoId }
        DownloadSession.forget(videoId)
    }

    // ---- The record ---------------------------------------------------------

    suspend fun savedUri(context: Context, videoId: String): Uri? = withContext(Dispatchers.IO) {
        val recorded = _saved.value[videoId] ?: return@withContext null
        val uri = recorded.toUri()
        if (DownloadStore.exists(context, uri)) return@withContext uri
        Log.d(TAG, "$videoId was downloaded but the file is gone; forgetting it")
        forget(videoId)
        null
    }

    /**
     * True when [uriString] names a `file://` path that is not there.
     *
     * Deliberately answers only for `file://`, and deliberately cheaply: this is
     * called from [Song.toMediaItem], which runs on the main thread once per
     * item for a whole queue. A `stat` is a few microseconds and safe at that
     * rate; the `openFileDescriptor` a `content://` uri would need is a binder
     * round trip, and three hundred of those while building a queue is a frame
     * budget gone. Stale `content://` records are left to
     * [PlaybackService.recoverFrom], which catches every scheme at the moment a
     * read actually fails and costs nothing until then.
     *
     * False for anything unparseable, which keeps "I could not tell" out of the
     * "the file is missing" answer — the caller drops a uri on a true here.
     */
    fun isMissingLocalFile(uriString: String): Boolean {
        if (!uriString.startsWith("file://")) return false
        val path = runCatching { uriString.toUri().path }.getOrNull() ?: return false
        return !File(path).exists()
    }

    /**
     * As [savedUri], but synchronous and without a [Context] parameter — for
     * [Song.toMediaItem], which builds a [MediaItem] on whatever thread that
     * happens to run on and has neither a suspend context nor a [Context] in
     * hand to reach [DownloadStore.exists] with.
     *
     * Without this, a record surviving the file it names — deleted by a file
     * manager, or a folder wiped out from under the app — sent the player a
     * `file://` uri to a path that is simply not there. Nothing downstream
     * checks that either: [AudioCache.playbackFactory] hands `file://` and
     * `content://` uris straight to [androidx.media3.datasource.FileDataSource],
     * which fails with `ERROR_CODE_IO_FILE_NOT_FOUND` — retried a handful of
     * times and then given up on, so the track just refuses to play, with
     * nothing to say why.
     *
     * Prunes the record on the way past, the same as [savedUri]: a claim that
     * has just been shown to be false is not worth keeping to be shown false
     * again on the next play.
     */
    fun verifiedSavedUri(videoId: String): String? {
        val recorded = _saved.value[videoId] ?: return null
        if (!isMissingLocalFile(recorded)) return recorded
        Log.d(TAG, "$videoId was downloaded but the file is gone; forgetting it")
        forget(videoId)
        return null
    }

    /** Delete the file saved for [videoId] and forget it. */
    suspend fun delete(context: Context, videoId: String): Boolean = withContext(Dispatchers.IO) {
        val uri = _saved.value[videoId]?.toUri() ?: return@withContext false
        val deleted = DownloadStore.delete(context, uri)
        _savedMetadata.value[videoId]?.lyricsUri?.toUri()?.let { sidecarUri ->
            LyricsSidecarStore.delete(context, sidecarUri)
        }
        forget(videoId)
        deleted
    }

    private fun forget(videoId: String) {
        record(saved = { it - videoId }, meta = { it - videoId })
    }

    /**
     * Drop the record for [videoId] because a read of the file it names has
     * just failed.
     *
     * The public counterpart to [forget], for [PlaybackService.recoverFrom] —
     * the one caller that does not need to check anything first, because the
     * player has already done better than a check: it opened the file and got
     * `ENOENT`. That covers the `content://` records [isMissingLocalFile]
     * deliberately declines to answer for, which is the whole reason this is
     * reachable from outside.
     *
     * Named for what it asserts rather than what it does, so a caller that has
     * *not* established the file is missing has no business calling it.
     */
    fun forgetMissing(videoId: String) {
        if (videoId !in _saved.value) return
        Log.d(TAG, "$videoId could not be opened; forgetting the download")
        forget(videoId)
    }

    // ---- Releases -----------------------------------------------------------

    fun rememberCollection(target: DownloadTarget, songs: List<Song>) {
        if (songs.isEmpty()) return
        val existing = _collections.value[target.id]
        val ids = songs.map { it.videoId }.distinct()
        val record = SavedCollection(
            id = target.id,
            title = target.title,
            subtitle = target.subtitle,
            thumbnailUrl = target.thumbnailUrl ?: existing?.thumbnailUrl,
            playlist = target.playlist,
            videoIds = (ids + (existing?.videoIds ?: emptyList())).distinct(),
        )
        recordCollections(_collections.value + (target.id to record))
    }

    fun forgetCollection(id: String) {
        if (id !in _collections.value) return
        recordCollections(_collections.value - id)
    }

    /**
     * Delete every file downloaded for release [id] and drop the record of it.
     *
     * The counterpart to [forgetCollection]: that one is for a record whose
     * files are already gone, this one is what actually takes them off the
     * device — the "delete download" a whole album or playlist card offers,
     * where a single track only ever offers [delete].
     */
    suspend fun deleteCollection(context: Context, id: String): Boolean {
        val record = _collections.value[id] ?: return false
        var any = false
        record.videoIds.forEach { videoId -> if (delete(context, videoId)) any = true }
        forgetCollection(id)
        return any
    }

    const val PLAYLIST_PREFIX = "local:playlist:"

    fun pageIdFor(id: String): String = PLAYLIST_PREFIX + id

    fun recordIdOf(browseId: String): String? =
        browseId.removePrefix(PLAYLIST_PREFIX).takeIf { it != browseId && it.isNotEmpty() }

    fun savedPlaylists(onDisk: Map<String, String> = _saved.value): List<SavedCollection> {
        if (_collections.value.isEmpty()) return emptyList()
        return _collections.value.values
            .filter { record -> record.playlist && record.videoIds.any { it in onDisk } }
            .sortedBy { it.title.lowercase(Locale.ROOT) }
    }

    fun collectionsAmong(songs: List<Song>): List<DownloadedCollection> {
        if (songs.isEmpty() || _collections.value.isEmpty()) return emptyList()
        val byId = songs.associateBy { it.videoId }
        val byUri = songs.mapNotNull { song -> song.localUri?.let { it to song } }.toMap()
        val uris = _saved.value
        return _collections.value.values
            .mapNotNull { record ->
                val tracks = record.videoIds
                    .mapNotNull { id -> byId[id] ?: uris[id]?.let(byUri::get) }
                    .distinctBy { it.localUri ?: it.videoId }
                if (tracks.isEmpty()) {
                    null
                } else {
                    DownloadedCollection(
                        id = record.id,
                        title = record.title,
                        subtitle = record.subtitle,
                        thumbnailUrl = record.thumbnailUrl ?: tracks.firstNotNullOfOrNull { it.thumbnailUrl },
                        playlist = record.playlist,
                        songs = tracks,
                    )
                }
            }
            .sortedBy { it.title.lowercase(Locale.ROOT) }
    }

    private fun recordCollections(map: Map<String, SavedCollection>) {
        _collections.value = map
        if (::prefs.isInitialized) {
            prefs.edit()
                .putString(KEY_SAVED_COLLECTIONS, json.encodeToString(collectionSerializer, map))
                .apply()
        }
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
    private fun remember(
        asked: Song,
        fetched: Song,
        uri: Uri,
        downloadFormat: String? = null,
        lyrics: LyricsResult? = null,
    ) {
        val ids = setOf(asked.videoId, fetched.videoId)
        // HLS packages cannot carry MP4 tags. Point the app's own metadata at
        // the cover saved beside their playlist so Downloads remains fully
        // offline even though another player cannot open that package.
        val savedArtwork = uri.takeIf { it.scheme == "file" && it.lastPathSegment == "playlist.m3u8" }
            ?.path?.let(::File)?.parentFile
            ?.listFiles()?.firstOrNull { it.nameWithoutExtension == "cover" }
            ?.let(Uri::fromFile)?.toString()
        // Either row may be the one that knew the release: a music video is
        // swapped for the catalogue track before this, and it is the catalogue
        // row that usually carries the album — but a search hit tapped directly
        // is both, and an album page's rows are neither.
        val album = fetched.albumName?.takeIf { it.isNotBlank() }
            ?: asked.albumName?.takeIf { it.isNotBlank() }
        val prevAsked = _savedMetadata.value[asked.videoId]
        val prevFetched = _savedMetadata.value[fetched.videoId]

        val metaAsked = SavedSongMetadata(
            videoId = asked.videoId,
            title = asked.title,
            artist = asked.artist,
            thumbnailUrl = savedArtwork ?: asked.thumbnailUrl,
            durationText = asked.durationText,
            albumName = album,
            uri = uri.toString(),
            downloadFormat = downloadFormat ?: prevAsked?.downloadFormat,
            lyricsUri = lyrics?.uri ?: prevAsked?.lyricsUri,
            lyricsSource = lyrics?.source ?: prevAsked?.lyricsSource,
            lyricsFormat = lyrics?.format ?: prevAsked?.lyricsFormat,
            lyricsState = lyrics?.state ?: prevAsked?.lyricsState ?: LyricsDownloadState.NOT_REQUESTED,
        )
        val metaFetched = SavedSongMetadata(
            videoId = fetched.videoId,
            title = fetched.title,
            artist = fetched.artist,
            thumbnailUrl = savedArtwork ?: fetched.thumbnailUrl,
            durationText = fetched.durationText,
            albumName = album,
            uri = uri.toString(),
            downloadFormat = downloadFormat ?: prevFetched?.downloadFormat,
            lyricsUri = lyrics?.uri ?: prevFetched?.lyricsUri,
            lyricsSource = lyrics?.source ?: prevFetched?.lyricsSource,
            lyricsFormat = lyrics?.format ?: prevFetched?.lyricsFormat,
            lyricsState = lyrics?.state ?: prevFetched?.lyricsState ?: LyricsDownloadState.NOT_REQUESTED,
        )
        record(
            saved = { it + ids.associateWith { id -> uri.toString() } },
            meta = {
                it + mapOf(asked.videoId to metaAsked, fetched.videoId to metaFetched)
            },
        )
    }

    private fun updateLyricsMetadata(videoId: String, lyrics: LyricsResult) {
        record(
            saved = { it },
            meta = { map ->
                val existing = map[videoId] ?: return@record map
                map + (
                    videoId to existing.copy(
                        lyricsUri = lyrics.uri,
                        lyricsSource = lyrics.source,
                        lyricsFormat = lyrics.format,
                        lyricsState = lyrics.state,
                    )
                )
            },
        )
    }

    private fun updateLyricsState(videoId: String, state: LyricsDownloadState) {
        record(
            saved = { it },
            meta = { map ->
                val existing = map[videoId] ?: return@record map
                map + (videoId to existing.copy(lyricsState = state))
            },
        )
    }

    /**
     * Apply [saved] and [meta] to the two records and write the result down.
     *
     * Takes transforms rather than finished maps because several downloads
     * finish at once now, and "read the map, add my track, store it back" run
     * from two threads loses one of the two tracks — silently, and permanently,
     * since this is the only record that a file was written. Both flows are
     * updated compare-and-set, and the persist is serialised so the copy that
     * reaches disk is never older than one already written.
     */
    private fun record(
        saved: (Map<String, String>) -> Map<String, String>,
        meta: (Map<String, SavedSongMetadata>) -> Map<String, SavedSongMetadata>,
    ) {
        val savedMap = _saved.updateAndGet(saved)
        val metaMap = _savedMetadata.updateAndGet(meta)
        if (!::prefs.isInitialized) return
        synchronized(recordLock) {
            prefs.edit()
                .putString(KEY_SAVED, json.encodeToString(serializer, savedMap))
                .putString(KEY_SAVED_METADATA, json.encodeToString(metadataSerializer, metaMap))
                .apply()
        }
    }

    private val recordLock = Any()

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
                            albumName = meta.albumName,
                            localUri = meta.uri,
                            downloadFormat = meta.downloadFormat,
                            localLyricsUri = meta.lyricsUri,
                            localLyricsSource = meta.lyricsSource,
                            localLyricsFormat = meta.lyricsFormat,
                        )
                    )
                }
            } else {
                forget(videoId)
            }
        }
        result
    }

    fun savedLyricsUri(videoId: String): String? = _savedMetadata.value[videoId]?.lyricsUri

    fun savedLyricsSource(videoId: String): String? = _savedMetadata.value[videoId]?.lyricsSource

    fun savedLyricsFormat(videoId: String): String? = _savedMetadata.value[videoId]?.lyricsFormat

    fun savedLyricsState(videoId: String): LyricsDownloadState =
        _savedMetadata.value[videoId]?.lyricsState ?: LyricsDownloadState.NOT_REQUESTED

    fun hasLyrics(videoId: String): Boolean = savedLyricsUri(videoId) != null

    // ---- Driven by DownloadService -----------------------------------------

    internal fun takeNext(): PendingDownload? = synchronized(lock) {
        val entry = pending.entries.firstOrNull() ?: return null
        pending.remove(entry.key)
        running[entry.key] = null
        entry.value
    }

    internal fun onRunning(videoId: String, job: Job) {
        val cancelled = synchronized(lock) {
            if (videoId !in running) return@synchronized true
            running[videoId] = job
            false
        }
        if (cancelled) job.cancel()
    }

    /** [videoId] is finished, one way or another, and no longer holds a worker. */
    internal fun onIdle(videoId: String) {
        synchronized(lock) { running.remove(videoId) }
    }

    /** Whether anything is still queued or in flight — see [DownloadService]'s workers. */
    internal fun busy(): Boolean = synchronized(lock) { pending.isNotEmpty() || running.isNotEmpty() }

    /**
     * The service is gone, so nothing is running any more.
     *
     * Distinct from [onIdle], which is one worker reporting one finished track.
     * This is the whole drain going away at once — every claim in [running] is
     * void, and leaving one behind would have [enqueue] refuse that track
     * forever as already in flight.
     */
    internal fun onStopped() {
        synchronized(lock) { running.clear() }
    }

    /**
     * Fetch one track or its lyrics, start to finish.
     */
    internal suspend fun run(context: Context, task: PendingDownload) = withContext(Dispatchers.IO) {
        val (song, lyricsOnly) = task
        if (lyricsOnly) {
            runLyricsOnly(context, song)
            return@withContext
        }
        val id = song.videoId
        // Set before the lookup, not after it. Resolving where a lossless track
        // comes from is the long part of a download, and leaving the row on
        // "Queued" for all of it reads as a queue that has stopped rather than
        // one that is working.
        _active.update { it + (id to DownloadState.Running(0f)) }
        DownloadSession.running(id, 0f)

        try {
            val plan = prepare(context, song)
            // The manager is showing the row that was tapped, which for a music
            // video is the wrong title and the wrong cover for the file actually
            // being written. Corrected here rather than left to disagree with
            // the notification and with the Downloads page afterwards.
            DownloadSession.retitle(id, plan.track)
            transfer(context, song, plan)
        } catch (e: CancellationException) {
            clear(id)
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "download failed for $id: ${e.message}", e)
            fail(id, e.friendly())
        }
    }

    internal suspend fun run(context: Context, song: Song) = run(context, PendingDownload(song))

    /**
     * Everything that has to be known before a byte can be asked for, and
     * nothing that touches the destination.
     *
     * Split out of [run] so it *can* be done ahead of time. Nothing does yet —
     * [DownloadService] still calls it inline, and the overlap that used to be
     * the point of the split is bought by running four workers instead. Kept
     * apart anyway, because the split is what makes the expensive half
     * separately measurable and separately cancellable.
     *
     * On a lossless queue this is the expensive half by a wide margin: a module
     * search fans out across a whole index and then a stream endpoint is
     * opened, tens of seconds against the few the transfer itself takes on a
     * fast connection. Run between transfers, as it used to be, that time was
     * simply the connection standing idle once per track, which is what a long
     * queue spent most of its life doing.
     *
     * Nothing here writes to [_active] or to [DownloadSession]. It may be
     * running for a track that is still queued — or for one that gets cancelled
     * before its turn — and a preparation is not a download.
     */
    internal suspend fun prepare(context: Context, song: Song): Prepared = withContext(Dispatchers.IO) {
        // Downloads preserve the exact item the listener picked. Catalogue
        // matching is a manual playback action and must not silently change a
        // download or its filename.
        val track = song
        // Read once, here, for the whole of this track. Both routes below
        // and the re-resolve inside [Downloader.fetch] have to agree on
        // which rung they are fetching, and re-reading the setting per call
        // would let a change made mid-download splice two renditions into
        // one file.
        val quality = AppSettings.downloadQuality.value

        // Asked before the lookup rather than after it, unlike the check on the
        // route's own filename below. A file already sitting in Music under a
        // lossless extension is the answer to the whole question, and spending
        // a twenty-second module search to arrive at a name we could have
        // guessed is the difference between re-running a 300-track queue in
        // seconds and re-running it in hours. Only the extensions that can only
        // be lossless are worth guessing at: an `.m4a` may be this app's ALAC
        // or its AAC, and adopting the wrong one would quietly answer a request
        // for lossless with a transcode.
        if (quality.keepsLossless) {
            LOSSLESS_EXTENSIONS.firstNotNullOfOrNull { extension ->
                DownloadStore.existing(context, DownloadStore.fileNameFor(track, extension))
            }?.let { uri ->
                return@withContext Prepared(song.videoId, track, route = null, alreadyAt = uri)
            }
        }

        val route = routeFor(track, quality)
        Log.d(TAG, "downloading ${song.videoId} as .${route.extension} (${route.describe}, ${quality.label})")
        Prepared(song.videoId, track, route = route, alreadyAt = null)
    }

    /**
     * What [prepare] worked out, ready for a transfer to be run against it.
     *
     * [videoId] rides along so a look-ahead can be checked against the track
     * actually taken off the queue: the two diverge whenever something is
     * cancelled while its route is being resolved, and a plan applied to the
     * wrong track would write one song's bytes under another's name.
     */
    internal class Prepared(
        val videoId: String,
        /** The catalogue track behind the row, which may not be the row. */
        val track: Song,
        /** Null when [alreadyAt] answered the question instead. */
        val route: Route?,
        /** A file already in Music that is this download, if there is one. */
        val alreadyAt: Uri?,
    )

    /**
     * Fetch the bytes [plan] points at and publish them.
     *
     * Owns the destination from end to end: every exit out of here either
     * commits or aborts, so a caller is free to call it a second time with a
     * freshly resolved plan without the first attempt leaving anything behind.
     */
    private suspend fun transfer(context: Context, song: Song, plan: Prepared) {
        val id = song.videoId
        val track = plan.track

        // Already there from a previous run the record lost track of — adopt it
        // rather than writing a second copy beside it.
        plan.alreadyAt?.let { uri ->
            remember(song, track, uri)
            DownloadSession.done(id)
            clear(id)
            return
        }
        val route = plan.route ?: error("Nothing to download")

        var pending: DownloadStore.Pending? = null
        var lyrics: Deferred<LyricsTag.Embeddable?>? = null
        var lyricsArtifact: Deferred<LyricsArtifact?>? = null
        var artwork: Deferred<MediaTagger.Artwork?>? = null
        try {
            coroutineScope {
                // Started before the transfer rather than after it, so four lyric
                // services are being raced while the bytes are already moving. Done
                // after the commit instead, every download would pay the slowest of
                // them in dead time — and it is a *suspending* wait, so it would sit
                // in the one stretch of this function that has no way back: past the
                // commit, [pending] is null and a cancellation there would abandon a
                // finished file that nothing has recorded yet. Awaited below while
                // there is still a pending destination to abort.
                //
                // [LyricsTag.forTrack] is contracted not to throw for anything but
                // cancellation, and that contract is load-bearing here: this is a
                // plain child of the scope, so a failure inside it would cancel the
                // download it was only meant to decorate.
                // HLS has no tag container, but its private offline package has
                // sidecars for exactly the same lyrics and full-resolution cover.
                if (route.taggable && (route.offlineHls != null || MediaTagger.carriesTags(route.extension))) {
                    lyrics = async { LyricsTag.forTrack(track) }
                    lyricsArtifact = async { fetchLyrics(song, track) }
                    artwork = async { MediaTagger.artworkFor(track) }
                }

                val name = DownloadStore.fileNameFor(track, route.extension)
                val alreadyThere = DownloadStore.existing(context, name)
                if (alreadyThere != null) {
                    Log.d(TAG, "$name is already in Music; adopting it")
                    val artifact = lyricsArtifact?.await()
                    val lyricsResult = saveLyricsSidecar(context, name, artifact)
                    remember(song, track, alreadyThere, route.downloadFormat, lyricsResult)
                    DownloadSession.done(id)
                    clear(id)
                    return@coroutineScope
                }

                if (route.offlineHls != null) {
                    val savedUri = OfflineHls.save(
                        context = context,
                        id = id,
                        url = route.offlineHls.url,
                        headers = route.offlineHls.headers,
                        onProgress = { written, total ->
                            val fraction = written.toFloat() / total
                            _active.update { it + (id to DownloadState.Running(fraction)) }
                            DownloadSession.running(id, fraction)
                        },
                        lyrics = lyrics?.await(),
                        artwork = artwork?.await(),
                    )
                    val artifact = lyricsArtifact?.await()
                    val lyricsResult = saveLyricsSidecar(context, name, artifact)
                    remember(song, track, savedUri, route.downloadFormat, lyricsResult)
                val manifest = route.offlineHls
                if (manifest != null) {
                    val onSegment: (Long, Long) -> Unit = { written, total ->
                        val fraction = written.toFloat() / total
                        _active.update { it + (id to DownloadState.Running(fraction)) }
                        DownloadSession.running(id, fraction)
                    }
                    val savedUri = if (manifest.dash) {
                        OfflineDash.save(
                            context = context,
                            id = id,
                            url = manifest.url,
                            headers = manifest.headers,
                            onProgress = onSegment,
                            lyrics = lyrics?.await(),
                            artwork = artwork?.await(),
                        )
                    } else {
                        OfflineHls.save(
                            context = context,
                            id = id,
                            url = manifest.url,
                            headers = manifest.headers,
                            onProgress = onSegment,
                            lyrics = lyrics?.await(),
                            artwork = artwork?.await(),
                        )
                    }
                    remember(song, track, savedUri, route.downloadFormat)
                    DownloadSession.done(id)
                    clear(id)
                    Log.d(TAG, "saved offline ${if (manifest.dash) "DASH" else "HLS"} package for $name")
                    return@coroutineScope
                }

                val destination = DownloadStore.begin(context, name, route.mimeType)
                pending = destination
                destination.openStream().use { sink ->
                    route.write(sink) { written, total ->
                        val fraction = written.toFloat() / total
                        _active.update { it + (id to DownloadState.Running(fraction)) }
                        DownloadSession.running(id, fraction)
                    }
                }
                val words = lyrics?.await()
                val cover = artwork?.await()
                val artifact = lyricsArtifact?.await()
                val lyricsResult = saveLyricsSidecar(context, name, artifact)

                // Publish only after metadata is part of the file. This keeps
                // concurrent album workers from exposing untagged tracks.
                MediaTagger.embed(context, destination.tagUri, track, route.extension, words, cover)
                val savedUri = destination.commit()
                pending = null
                remember(song, track, savedUri, route.downloadFormat, lyricsResult)
                DownloadSession.done(id)
                clear(id)
                Log.d(TAG, "saved $name")
            }
        } catch (e: Throwable) {
            pending?.abort()
            throw e
        } finally {
            lyrics?.cancel()
            lyricsArtifact?.cancel()
            artwork?.cancel()
        }
    }

    private suspend fun runLyricsOnly(context: Context, song: Song) = withContext(Dispatchers.IO) {
        val id = song.videoId
        _lyricsActive.update { it + id }
        try {
            val audioUri = savedUri(context, id)
            if (audioUri == null) {
                updateLyricsState(id, LyricsDownloadState.FAILED)
                return@withContext
            }

            val track = runCatching { YtMusicRepository.resolveAudio(song) }.getOrDefault(song)
            val artifact = fetchLyrics(song, track)

            if (artifact == null) {
                updateLyricsState(id, LyricsDownloadState.UNAVAILABLE)
                return@withContext
            }

            val displayName = DownloadStore.displayName(context, audioUri)
                ?: DownloadStore.fileNameFor(song, "m4a")
            val extension = displayName.substringAfterLast('.', "m4a")

            val lyricsResult = saveLyricsSidecar(context, displayName, artifact)
            runCatching {
                val embeddable = LyricsTag.Embeddable(
                    plain = artifact.lines.toLrc(),
                    enhanced = artifact.lines.toEnhancedLrc().takeIf { it.isNotBlank() },
                )
                MediaTagger.embed(context, audioUri, track, extension, embeddable)
            }.onFailure {
                Log.w(TAG, "could not embed lyrics tag in $displayName: ${it.message}")
            }
            if (lyricsResult?.state == LyricsDownloadState.SAVED) {
                updateLyricsMetadata(id, lyricsResult)
            } else {
                updateLyricsState(id, lyricsResult?.state ?: LyricsDownloadState.FAILED)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "lyrics-only download failed for $id: ${e.message}", e)
            updateLyricsState(id, LyricsDownloadState.FAILED)
        } finally {
            _lyricsActive.update { it - id }
        }
    }

    private suspend fun fetchLyrics(asked: Song, track: Song): LyricsArtifact? {
        val sources = if (AppSettings.syncedLyrics.value) {
            AppSettings.lyricsSources.value
        } else {
            emptySet()
        }
        if (sources.isEmpty()) return null

        val durationMs = track.durationMillis().takeIf { it > 0L }
            ?: TrackMatcher.secondsOf(track.durationText ?: asked.durationText)?.times(1000L)
            ?: 0L
        if (durationMs <= 0L) {
            Log.d(TAG, "no duration for ${track.videoId}; skipping lyrics")
            return null
        }

        val result = withTimeoutOrNull(LYRICS_LOOKUP_MS) {
            try {
                LyricsRepository.lyrics(
                    videoId = asked.videoId,
                    title = track.title,
                    artist = track.artist,
                    durationMs = durationMs,
                    album = track.albumName,
                    sources = sources,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "lyrics lookup failed for ${track.videoId}: ${e.message}")
                null
            }
        } ?: return null

        val artifact = result.artifact ?: return null
        if (artifact.lines.none { it.text.isNotBlank() }) return null
        if (artifact.content.length > LyricsSerializer.MAX_LYRICS_CHARS) {
            Log.w(TAG, "lyrics for ${track.videoId} exceed ${LyricsSerializer.MAX_LYRICS_CHARS} chars; skipping")
            return null
        }
        return artifact
    }

    private fun saveLyricsSidecar(
        context: Context,
        audioName: String,
        artifact: LyricsArtifact?,
    ): LyricsResult? {
        if (artifact == null) return null
        val sidecarName = LyricsSidecarStore.fileNameFor(audioName, artifact)
        val sidecarUri = runCatching {
            LyricsSidecarStore.write(context, sidecarName, artifact)
        }.onFailure {
            Log.w(TAG, "could not write lyrics sidecar $sidecarName: ${it.message}")
        }.getOrNull() ?: return LyricsResult(LyricsDownloadState.FAILED)

        return LyricsResult(
            state = LyricsDownloadState.SAVED,
            uri = sidecarUri.toString(),
            source = artifact.source.name,
            format = artifact.format.name,
        )
    }

    private class LyricsResult(
        val state: LyricsDownloadState,
        val uri: String? = null,
        val source: String? = null,
        val format: String? = null,
    )

    // ---- Routing ------------------------------------------------------------

    /**
     * One resolved download: what to call the file, what to tell the store it
     * is, and how to fill it.
     *
     * Exists so [run] has one linear body rather than two nearly-identical
     * ones. Everything after the bytes are chosen — the duplicate check, the
     * pending row, the commit, the tagging, the abort on failure — is the same
     * work whichever server the audio came from, and the two routes differ only
     * in these four answers.
     */
    internal class Route(
        val extension: String,
        val mimeType: String,
        val describe: String,
        /** Short premium-rendition badge shown only in BitChord's Downloads list. */
        val downloadFormat: String? = null,
        val taggable: Boolean = true,
        val offlineHls: Manifest? = null,
        val write: suspend (OutputStream, (written: Long, total: Long) -> Unit) -> Unit,
    )

    /**
     * A stream that arrived as an index rather than as audio, and which of the
     * two index formats it is. Both are saved into the same offline package —
     * see [OfflineDash] — so [dash] only decides who does the parsing.
     */
    internal class Manifest(
        val url: String,
        val headers: Map<String, String>,
        val dash: Boolean = false,
    )

    /**
     * Where this download's bytes are coming from.
     *
     * A configured source gets asked first, and YouTube is what happens when
     * none of them can serve it — see [SourceResolver.forDownload] for what
     * "can" means, which is narrower here than it is for playback.
     *
     * @param quality read once by the caller and passed down, so that a setting
     *   changed while this track is in the queue applies to the next one rather
     *   than to the middle of this one. [Downloader.fetch] resolves again after
     *   a mid-download refusal and has to ask for the same rung it started on.
     */
    private suspend fun routeFor(track: Song, quality: DownloadQuality): Route {
        fromSources(track, quality)?.let { (stream, storable) ->
            // A manifest is an index, not audio. Whichever kind it is, fetching
            // it as a file writes the index into something named `.flac` —
            // which is exactly what a `.mpd` did until [OfflineDash] existed:
            // a 2.7 KB download that reported success and could never play.
            // Read off the URL rather than off `stream.format`, which describes
            // the audio inside and says nothing about the envelope.
            val dash = OfflineDash.handles(stream.url)
            val hls = stream.url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
            val packaged = hls || dash
            // A package is only useful inside BitChord. When the user
            // explicitly exports files for another player, decline it here and
            // let the ordinary portable-file fallback resolve instead.
            if (packaged && AppSettings.exportDownloads.value) return@let
            return Route(
                // DASH is saved *as* HLS — see [OfflineDash] for why — so both
                // kinds land as the same package and are named for what was
                // written rather than for what was fetched.
                extension = if (packaged) "m3u8" else storable.extension,
                mimeType = if (packaged) "application/vnd.apple.mpegurl" else storable.mimeType,
                describe = stream.format.summary,
                downloadFormat = stream.format.downloadBadge(),
                taggable = true,
                offlineHls = Manifest(stream.url, stream.headers, dash = dash).takeIf { packaged },
                write = { sink, onProgress ->
                    Downloader.fetchDirect(stream.url, stream.headers, sink, onProgress)
                },
            )
        }
        val stream = StreamResolver.resolveForDownload(track.videoId, quality.maxKbps)
        return Route(
            extension = stream.downloadExtension,
            mimeType = stream.downloadMimeType,
            describe = "${stream.kbps}kbps ${stream.mimeType}",
            write = { sink, onProgress ->
                Downloader.fetch(track.videoId, stream, quality.maxKbps, sink, onProgress)
            },
        )
    }

    /**
     * The stream to keep for [track] from a configured source, with how to file
     * it — or null, which is not a failure, just YouTube's turn.
     *
     * Usually a bit-exact one; not always. [SourceResolver.forDownload] falls
     * back to the best lossy copy any enabled source holds when nothing has the
     * recording losslessly, and only gives up on the sources entirely when what
     * they offer would not beat YouTube's own AAC. Which of those happened is
     * the resolver's business — from here it is a URL and a codec either way.
     *
     * Bounded, because a module search waits on every backend it has (see
     * `ModuleSource.SEARCH_PATIENT_MS`) and does that once per query the matcher
     * offers. For a track no module holds, that is the whole queue stopped for
     * the better part of a minute on the way to a download that was always
     * going to be YouTube's. `PlaybackService.SUBSTITUTE_TIMEOUT_MS` bounds the
     * same search for the same reason.
     *
     * The [DownloadStore.storable] check belongs here rather than inside the
     * resolver: the resolver's job is finding the best audio, and whether this
     * device will keep a file of that codec is a question about Android.
     *
     * @param quality pinned by [run] for the whole of this track. Passed on to
     *   the resolver rather than left to it, so that the twenty seconds this may
     *   spend searching cannot be a window in which the setting changes and the
     *   two halves of one decision disagree.
     */
    private suspend fun fromSources(
        track: Song,
        quality: DownloadQuality,
    ): Pair<SourceStream, DownloadStore.Storable>? {
        // Wrapped so the two ways this can come back empty stay apart in the
        // log. `withTimeoutOrNull` collapses them into one null, and the
        // difference is the whole diagnosis: "no enabled source holds this
        // recording" is a fact about the track, and "the clock ran out" is a
        // fact about [SOURCE_LOOKUP_MS] — which is what silently sent a whole
        // Lossless queue to YouTube's AAC while the log said nothing at all.
        var timedOut = true
        val stream = withTimeoutOrNull(SOURCE_LOOKUP_MS) {
            try {
                SourceResolver.forDownload(
                    TrackMatcher.targetOf(track),
                    SourceResolver.requestForDownload(quality),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "source lookup failed for ${track.videoId}: ${e.message}")
                null
            }.also { timedOut = false }
        }
        if (stream == null) {
            if (timedOut) {
                Log.w(
                    TAG,
                    "source lookup for ${track.videoId} ran past ${SOURCE_LOOKUP_MS}ms; " +
                        "downloading it from YouTube instead of ${quality.label}",
                )
            }
            return null
        }

        val storable = DownloadStore.storable(stream.format.codec)
        if (storable == null) {
            Log.d(TAG, "nothing to file a '${stream.format.codec}' as; taking YouTube for ${track.videoId}")
            return null
        }
        return stream to storable
    }

    private fun clear(videoId: String) {
        _active.update { it - videoId }
    }

    private fun fail(videoId: String, reason: String) {
        _active.update { it + (videoId to DownloadState.Failed(reason)) }
        DownloadSession.failed(videoId, reason)
    }

    private fun Exception.friendly(): String = when {
        (this is IllegalStateException || this is IllegalArgumentException) &&
            !message.isNullOrBlank() -> message!!
        else -> "Download failed — check your connection"
    }

    /**
     * How long the source lookup may hold a download up before it goes to
     * YouTube regardless.
     *
     * **This has to clear `ModuleSource.SEARCH_PATIENT_MS`, and for a long time
     * it didn't.** It was 20s against a patient window of 25s, which made the
     * arithmetic decide the outcome: [SourceResolver.forDownload] asks every
     * source with `waitForAll = true`, that flag buys each module the full 25s
     * patient window, and this timeout fired five seconds before the window it
     * was waiting on could even close. Every track whose modules were not
     * unusually quick came back null — not "no source has it", but "nobody was
     * asked for long enough" — and null here means YouTube. So a Lossless
     * setting reliably produced YouTube's AAC, and it looked like a bulk-only
     * fault because it *was* one: a single download runs with the engines to
     * itself and lands inside 20s, while four workers sharing three interpreter
     * engines per module (`QuickJsExecutor.ENGINES_PER_MODULE`) do not.
     *
     * Sized off what it actually bounds, therefore, rather than off the playback
     * timeout it used to be matched to. One patient search is 25s, the matcher
     * offers up to two queries (`TrackMatcher.queries`), and [streamBest] may
     * then open up to `STREAM_ATTEMPTS` stream endpoints on the winner. Sixty
     * seconds covers a slow-but-working index; it is still finite, because the
     * alternative is the queue stalled per track on modules that simply do not
     * have the recording.
     *
     * The comparison with playback is no longer the right one and is worth
     * stating plainly: `PlaybackService.SUBSTITUTE_TIMEOUT_MS` is short because
     * a listener is staring at a paused player. Nothing is waiting here, the
     * answer becomes a permanent file, and re-fetching it later costs the whole
     * download again.
     *
     * It bounds the lossy half of that lookup too, which is why
     * [SourceResolver.forDownload] runs both halves at once rather than in
     * turn: a fast source queued behind a slow one would spend this budget
     * waiting for a module and never be asked.
     */
    private const val SOURCE_LOOKUP_MS = 60_000L

    /**
     * The extensions a file in Music can carry that say, on their own, that a
     * lossless request has already been answered.
     *
     * `m4a` is deliberately absent even though [DownloadStore.storable] files
     * ALAC as one: an `.m4a` in this folder is just as likely to be the AAC a
     * download at the High rung wrote, and there is nothing in the name to
     * separate them. Guessing wrong there would answer a request for lossless
     * with a transcode and never fetch the real thing.
     */
    private val LOSSLESS_EXTENSIONS = listOf("flac", "wav")

    /**
     * Why a download didn't start, when the reason is a setting rather than a
     * fault.
     *
     * Names the switch, because a refusal that only says no leaves the user
     * looking for a network problem that isn't there. Shared with the callers
     * that show it as a toast so the two cannot drift apart.
     */
    internal const val WIFI_ONLY_REFUSAL = "Downloads are set to Wi-Fi only"

    /** Dropped when the sheet is reopened; a failure is worth showing once. */
    fun dismissFailure(videoId: String) {
        if (_active.value[videoId] is DownloadState.Failed) clear(videoId)
    }
}

@kotlinx.serialization.Serializable
data class SavedSongMetadata(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
    val durationText: String? = null,
    val albumName: String? = null,
    val uri: String,
    val downloadFormat: String? = null,
    val lyricsUri: String? = null,
    val lyricsSource: String? = null,
    val lyricsFormat: String? = null,
    val lyricsState: LyricsDownloadState = LyricsDownloadState.NOT_REQUESTED,
)

/** Labels intentionally only distinguish premium formats, not ordinary AAC/Opus downloads. */
private fun StreamFormat.downloadBadge(): String? = when {
    isDolbyAtmos -> "DOLBY"
    codec.equals("flac", ignoreCase = true) || codec.equals("x-flac", ignoreCase = true) -> "FLAC"
    else -> null
}

/**
 * What a batch download was asked for as a whole.
 *
 * Built by whichever surface the tap came from — a release page's own download
 * button, a shelf card's menu — because that surface is the only thing that
 * knows the answer, and by the time the tracks reach the queue they are forty
 * unrelated rows. Null everywhere a single track is downloaded on its own,
 * which is the honest answer there: one song off an album is not the album.
 */
data class DownloadTarget(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val thumbnailUrl: String? = null,
    val playlist: Boolean = false,
)

@kotlinx.serialization.Serializable
data class SavedCollection(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val thumbnailUrl: String? = null,
    val playlist: Boolean = false,
    val videoIds: List<String> = emptyList(),
)

data class DownloadedCollection(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val playlist: Boolean,
    val songs: List<Song>,
)
