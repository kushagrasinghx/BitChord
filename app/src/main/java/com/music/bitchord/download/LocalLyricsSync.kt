package com.music.bitchord.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.lyrics.LyricsMatch
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.LyricsSidecar
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.lyrics.toEnhancedLrc
import com.music.bitchord.data.lyrics.toLrc
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * Finds synchronized lyrics for the files already on this device and writes
 * them beside the audio as `.lrc`.
 *
 * This is the batch counterpart of the player's own lookup, not a second
 * lyrics stack: it asks [LyricsRepository] with the same settings and the same
 * providers, then applies the two gates a file needs that a screen does not —
 * [LyricsMatch] for whether the timing fits this recording, and
 * [LyricsSidecar] for never replacing something already there.
 *
 * Work runs through WorkManager under one unique name, so leaving the screen,
 * backgrounding the app, or asking again while a run is going cannot start a
 * second pass. Progress and per-track outcomes are kept in memory for the
 * sheet and in SharedPreferences for the next launch; a run that is stopped
 * simply leaves its unfinished tracks as they were, ready for Sync missing or
 * Retry failed.
 */
object LocalLyricsSync {

    private const val TAG = "BitChord"
    private const val PREFS = "bitchord_local_lyrics_sync"
    private const val KEY_STATUSES = "statuses"
    private const val WORK = "local_lyrics_sync"
    private const val KEY_MODE = "mode"

    /** One lookup may not hold the batch forever; same ceiling as a download's tag. */
    private const val LOOKUP_MS = 30_000L

    /** Four providers racing each is already a lot of requests; three at once is enough. */
    private const val PARALLEL = 3

    private const val MAX_LRC_CHARS = 64_000

    enum class Mode { ALL, MISSING, RETRY_FAILED }

    enum class State {
        NOT_SYNCED,
        SYNCING,
        SYNCED,
        ALREADY_PRESENT,
        NO_LYRICS,
        FAILED,
        ;

        val isDone: Boolean get() = this == SYNCED || this == ALREADY_PRESENT
    }

    data class TrackStatus(
        val uri: String,
        val title: String,
        val artist: String,
        val state: State,
        val source: String? = null,
        val message: String? = null,
        val updatedAt: Long = 0L,
    )

    data class Progress(
        val running: Boolean = false,
        val mode: Mode? = null,
        val total: Int = 0,
        val done: Int = 0,
        val currentTitle: String? = null,
        val finished: Boolean = false,
        val cancelled: Boolean = false,
        /** A reason the run could not begin, such as a missing permission. */
        val error: String? = null,
    ) {
        val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    }

    private lateinit var prefs: android.content.SharedPreferences
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _statuses = MutableStateFlow<Map<String, TrackStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, TrackStatus>> = _statuses.asStateFlow()

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    fun init(context: Context) {
        if (this::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _statuses.value = load()
        // One writer for the whole map: a batch updates it once per track, and
        // a JSON document per update would be a disk write per song.
        io.launch {
            _statuses.drop(1).conflate().collect { persist(it) }
        }
    }

    private fun ensurePrefs(context: Context) {
        if (!this::prefs.isInitialized) init(context)
    }

    /** Starts a batch; a run already going is replaced by this one. */
    fun start(context: Context, mode: Mode) {
        ensurePrefs(context)
        val enabled = sourcesFor(null)
        if (enabled.isEmpty()) {
            _progress.value = Progress(
                finished = true,
                error = "No lyrics sources are enabled",
            )
            return
        }
        _progress.value = Progress(running = true, mode = mode)
        val request = OneTimeWorkRequestBuilder<LocalLyricsSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setInputData(workDataOf(KEY_MODE to mode.name))
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
        }.onFailure {
            Log.w(TAG, "could not start lyrics sync: ${it.message}")
            _progress.value = Progress(finished = true, error = it.message ?: "Could not start")
        }
    }

    fun cancel(context: Context) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(WORK) }
        _progress.update { it.copy(running = false, cancelled = true) }
    }

    /**
     * Downloads and writes one track's lyrics, for the track menu.
     *
     * Unlike a batch this returns the outcome, because the caller is a tap that
     * wants to say what happened. It updates the same status map so a later
     * batch does not try the file again needlessly.
     */
    suspend fun syncOne(context: Context, song: Song): TrackStatus {
        ensurePrefs(context)
        return syncTrack(context, song, batch = false)
    }

    internal suspend fun run(context: Context, mode: Mode) {
        ensurePrefs(context)
        val songs = try {
            collectSongs(context)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Log.w(TAG, "could not list local music: ${failure.message}")
            _progress.value = Progress(finished = true, error = failure.message ?: "Could not read local music")
            return
        }
        val known = _statuses.value
        val candidates = when (mode) {
            Mode.ALL -> songs
            Mode.MISSING -> songs.filter { known[it.localUri]?.state?.isDone != true }
            Mode.RETRY_FAILED -> songs.filter { known[it.localUri]?.state == State.FAILED }
        }
        if (candidates.isEmpty()) {
            _progress.value = Progress(mode = mode, finished = true)
            return
        }
        val done = AtomicInteger(0)
        _progress.value = Progress(running = true, mode = mode, total = candidates.size)
        try {
            coroutineScope {
                val gate = Semaphore(PARALLEL)
                candidates.map { song ->
                    async(Dispatchers.IO) {
                        gate.withPermit {
                            if (!currentCoroutineContext().isActive) return@withPermit
                            syncTrack(context, song, batch = true)
                            val finished = done.incrementAndGet()
                            _progress.update {
                                it.copy(done = finished, currentTitle = song.title)
                            }
                        }
                    }
                }.awaitAll()
            }
            _progress.update { it.copy(running = false, finished = true, currentTitle = null) }
        } catch (cancelled: CancellationException) {
            _progress.update { it.copy(running = false, cancelled = true) }
            throw cancelled
        } finally {
            if (!currentCoroutineContext().isActive) {
                _progress.update { it.copy(running = false, cancelled = true) }
            }
        }
    }

    private suspend fun syncTrack(context: Context, song: Song, batch: Boolean): TrackStatus {
        val uri = song.localUri ?: return TrackStatus(
            uri = song.videoId,
            title = song.title,
            artist = song.artist,
            state = State.FAILED,
            message = "No file",
            updatedAt = System.currentTimeMillis(),
        )
        if (batch) {
            _progress.update { it.copy(currentTitle = song.title) }
        }
        updateStatus(song, uri, State.SYNCING)

        // Already beside the file: leave it exactly as it is. This is checked
        // before the network, so a second Sync all costs one local read per
        // track rather than a lookup.
        val existing = LyricsSidecar.read(context, uri)
        if (!existing.isNullOrBlank()) {
            return finish(song, uri, State.ALREADY_PRESENT, source = null, message = null)
        }

        val durationMs = song.durationMillis()
        val sources = sourcesFor(song)
        if (sources.isEmpty()) {
            return finish(song, uri, State.FAILED, message = "No lyrics sources are enabled")
        }
        val found = try {
            withTimeoutOrNull(LOOKUP_MS) {
                LyricsRepository.lyrics(
                    videoId = song.videoId,
                    title = song.title,
                    artist = song.artist,
                    durationMs = durationMs,
                    album = song.albumName,
                    sources = sources,
                    order = AppSettings.lyricsSourceOrder.value,
                    prioritizeSyllableSync = AppSettings.prioritizeSyllableSync.value,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Log.d(TAG, "lyrics lookup failed for ${song.title}: ${failure.message}")
            null
        } ?: return finish(song, uri, State.NO_LYRICS, message = "No synchronized lyrics found")

        if (found.lines.none { it.text.isNotBlank() }) {
            return finish(song, uri, State.NO_LYRICS, message = "No synchronized lyrics found")
        }
        when (LyricsMatch.check(found.lines, durationMs)) {
            LyricsMatch.Rejection.UNSYNCED ->
                return finish(song, uri, State.NO_LYRICS, message = "Only unsynchronized lyrics found")
            LyricsMatch.Rejection.RUNS_LONG, LyricsMatch.Rejection.RUNS_SHORT ->
                return finish(song, uri, State.NO_LYRICS, message = "Lyrics do not match this recording")
            LyricsMatch.Rejection.NONE -> Unit
        }

        val plain = found.lines.toLrc()
        val enhanced = found.lines.toEnhancedLrc()
        if (plain.isBlank() || plain.length > MAX_LRC_CHARS) {
            return finish(song, uri, State.FAILED, message = "Lyrics are empty or too large")
        }
        if (enhanced.length > MAX_LRC_CHARS * 2) {
            return finish(song, uri, State.FAILED, message = "Lyrics are too large")
        }
        val written = LyricsSidecar.write(context, uri, plain, enhanced)
        return when (written.state) {
            LyricsSidecar.State.WRITTEN -> finish(
                song, uri, State.SYNCED, source = found.source.label, message = null,
            )
            LyricsSidecar.State.ALREADY_PRESENT -> finish(song, uri, State.ALREADY_PRESENT)
            LyricsSidecar.State.NO_SYNCED_LYRICS ->
                finish(song, uri, State.NO_LYRICS, message = "Only unsynchronized lyrics found")
            LyricsSidecar.State.UNSUPPORTED ->
                finish(song, uri, State.FAILED, message = "This file's folder is not writable")
            LyricsSidecar.State.PERMISSION_DENIED ->
                finish(song, uri, State.FAILED, message = "Storage permission was refused")
            LyricsSidecar.State.FAILED ->
                finish(song, uri, State.FAILED, message = written.message ?: "Could not save lyrics")
        }
    }

    private fun finish(
        song: Song,
        uri: String,
        state: State,
        source: String? = null,
        message: String? = null,
    ): TrackStatus {
        updateStatus(song, uri, state, source, message)
        return _statuses.value[uri] ?: TrackStatus(
            uri = uri,
            title = song.title,
            artist = song.artist,
            state = state,
            source = source,
            message = message,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun updateStatus(
        song: Song,
        uri: String,
        state: State,
        source: String? = null,
        message: String? = null,
    ) {
        _statuses.update { current ->
            current + (uri to TrackStatus(
                uri = uri,
                title = song.title,
                artist = song.artist,
                state = state,
                source = source,
                message = message,
                updatedAt = System.currentTimeMillis(),
            ))
        }
    }

    /**
     * The local library, Downloads included.
     *
     * Downloads are read even without the media permission: they are this
     * app's own files and are exactly the tracks most likely to be offline.
     * The two lists overlap, so the file uri is the identity.
     */
    internal suspend fun collectSongs(context: Context): List<Song> {
        val local = runCatching { LocalMediaRepository.getLocalMusic(context) }.getOrDefault(emptyList())
        val downloads = runCatching { Downloads.getDownloadedSongs(context) }.getOrDefault(emptyList())
        return (local + downloads)
            .filter { it.localUri != null }
            .distinctBy { it.localUri }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    /**
     * The providers worth asking for a file.
     *
     * The three keyed on a YouTube video id cannot say anything useful about a
     * content or file uri, and asking them would only spend a request to get a
     * stranger's words back. Every metadata-based provider stays.
     */
    private fun sourcesFor(song: Song?): Set<LyricsSource> {
        val enabled = AppSettings.lyricsSources.value
        if (song == null || song.videoId.matches(YOUTUBE_ID)) return enabled
        return enabled - VIDEO_KEYED
    }

    private val YOUTUBE_ID = Regex("""^[A-Za-z0-9_-]{11}$""")
    private val VIDEO_KEYED = setOf(
        LyricsSource.SIMP_MUSIC,
        LyricsSource.YOUTUBE_TRANSCRIPT,
        LyricsSource.YOUTUBE_MUSIC,
    )

    // ---- Persistence --------------------------------------------------------

    private fun load(): Map<String, TrackStatus> {
        val raw = prefs.getString(KEY_STATUSES, null) ?: return emptyMap()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.getJSONObject(index)
                val uri = obj.optString("uri").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                uri to TrackStatus(
                    uri = uri,
                    title = obj.optString("title"),
                    artist = obj.optString("artist"),
                    state = runCatching { State.valueOf(obj.optString("state")) }.getOrDefault(State.NOT_SYNCED),
                    source = obj.optString("source").takeIf { it.isNotBlank() },
                    message = obj.optString("message").takeIf { it.isNotBlank() },
                    updatedAt = obj.optLong("updatedAt"),
                )
            }.toMap()
        }.getOrElse {
            Log.w(TAG, "local lyrics statuses unreadable: ${it.message}")
            emptyMap()
        }
    }

    private fun persist(statuses: Map<String, TrackStatus>) {
        runCatching {
            val array = JSONArray()
            statuses.values.forEach { status ->
                array.put(
                    JSONObject()
                        .put("uri", status.uri)
                        .put("title", status.title)
                        .put("artist", status.artist)
                        .put("state", status.state.name)
                        .put("source", status.source)
                        .put("message", status.message)
                        .put("updatedAt", status.updatedAt),
                )
            }
            prefs.edit().putString(KEY_STATUSES, array.toString()).apply()
        }.onFailure { Log.w(TAG, "local lyrics statuses not saved: ${it.message}") }
    }
}

/** The WorkManager entry point; all of the work is in [LocalLyricsSync.run]. */
class LocalLyricsSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val mode = runCatching {
            LocalLyricsSync.Mode.valueOf(inputData.getString("mode") ?: LocalLyricsSync.Mode.ALL.name)
        }.getOrDefault(LocalLyricsSync.Mode.ALL)
        LocalLyricsSync.run(applicationContext, mode)
        return Result.success()
    }
}
