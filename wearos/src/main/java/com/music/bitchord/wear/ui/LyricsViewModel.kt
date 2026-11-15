package com.music.bitchord.wear.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.music.bitchord.wear.lyrics.SyncedLine
import com.music.bitchord.wear.lyrics.SyncedLyricsPayload
import com.music.bitchord.wear.lyrics.WearSyncPaths
import com.music.bitchord.wear.data.NowPlaying
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.abs

enum class LyricsStatus { IDLE, LOADING, READY, UNAVAILABLE }

/** Which text is shown for each line: the original, the phone's translation, or its Latin-script reading. */
enum class LyricsView { ORIGINAL, TRANSLATION, ROMANIZATION }

enum class ExtraStatus { IDLE, LOADING, READY, SAME, UNAVAILABLE }

/** One source that has synced lyrics for the current track. [kind] is "word" or "line" timing. */
data class LyricsSourceItem(val id: String, val label: String, val kind: String)

/** The phone's answer to "which sources have this song": it grows while the phone is still asking. */
data class SourcesUi(
    val trackId: String? = null,
    val items: List<LyricsSourceItem> = emptyList(),
    val currentId: String = "",
    val loading: Boolean = false,
)

/** How well the watch knows where the phone's playhead is: how many fixes, and the best round trip. */
data class SyncInfo(val fixCount: Int = 0, val bestRttMs: Long = -1L)

data class LyricsUiState(
    val trackId: String? = null,
    val title: String = "",
    val artist: String = "",
    val lines: List<SyncedLine> = emptyList(),
    val lyricsStatus: LyricsStatus = LyricsStatus.IDLE,
    val isPlaying: Boolean = false,
    val activeLineIndex: Int = -1,
    val viewMode: LyricsView = LyricsView.ORIGINAL,
    val extraStatus: ExtraStatus = ExtraStatus.IDLE,
    /** One entry per lyric line, aligned to [lines]; only meaningful when [extraStatus] is READY. */
    val extraTexts: List<String> = emptyList(),
)

sealed interface LyricsEvent {
    /** The clock naturally advanced to the next line. */
    data object LineChanged : LyricsEvent

    /** The clock naturally advanced into a chorus block. */
    data object ChorusEntered : LyricsEvent
}

/**
 * Lyrics for the watch.
 *
 * Playback state is NOT re-implemented here: the phone already publishes what is playing, with the
 * position and the instant it was true (see NowPlaying.positionAt), and PhoneLink already turns
 * that into the [NowPlaying] stream. This class takes that stream through [onNowPlaying] and adds:
 *
 *  - [state]      low-frequency UI state (track, lyrics, active line)
 *  - [positionMs] a local clock, ~60 Hz while playing and visible, 1 Hz in ambient
 *  - [events]     haptic triggers
 *
 * Clock: the phone stamps each reading with ITS wall clock, so the watch cannot simply compare that
 * stamp with its own: a few seconds of difference between the two clocks would put the lyrics a
 * line or two ahead while playing (and right again when paused, where nothing runs on). The offset
 * between the clocks is measured with a ping to the phone (shortest round trip wins, as ServerClock
 * does in :shared) and every reading is converted to watch time before use.
 *
 * Position = reading + time run on since its stamp + a decaying "blend" term. When a new NowPlaying
 * arrives and the visible position is off by more than [WearSyncPaths.DRIFT_SNAP_THRESHOLD_MS] it
 * snaps; otherwise the error is faded out linearly over [BLEND_WINDOW_MS], so lyrics never jump.
 *
 * Lyrics come from the phone as a Data Layer item (gzip asset). They are cached on the watch, so a
 * repeated track never needs another transfer; on a cache miss the phone is asked for them.
 */
class LyricsViewModel(application: Application) : AndroidViewModel(application),
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener {

    private val dataClient = Wearable.getDataClient(application)
    private val messageClient = Wearable.getMessageClient(application)
    private val nodeClient = Wearable.getNodeClient(application)
    private val cache = LyricsCache(application)
    private val prefs = application.getSharedPreferences("bitchord_lyrics", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(LyricsUiState(viewMode = restoredViewMode()))
    val state: StateFlow<LyricsUiState> = _state.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    /** Text size, kept across runs: 1.0 is the default. */
    private val _fontScale = MutableStateFlow(prefs.getFloat(KEY_FONT_SCALE, 1f))
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()

    private val _sources = MutableStateFlow(SourcesUi())
    val sources: StateFlow<SourcesUi> = _sources.asStateFlow()
    private var sourcesTimeoutJob: Job? = null

    private val _events = MutableSharedFlow<LyricsEvent>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<LyricsEvent> = _events.asSharedFlow()

    // --- Clock (main thread only) ---------------------------------------------------
    private var nowPlaying: NowPlaying? = null
    private var payload: SyncedLyricsPayload? = null
    private var durationMs = 0L
    private var playing = false
    private var blendFromMs = 0L
    private var blendStartElapsedMs = 0L
    private var hapticSuppressUntilElapsedMs = 0L

    /** The player's lyrics offset, mirrored from the phone: the position used is the playhead minus this. */
    private var lyricsOffsetMs = 0

    // --- Fixes on the phone's own playhead (by ping) -----------------------------------
    private class PhoneHead(val positionMs: Long, val advancing: Boolean, val speed: Float)

    private class Fix(val positionMs: Long, val atWatchWall: Long, val rttMs: Long, val speed: Float)

    private val fixes = ArrayList<Fix>()
    private val bestFixes = ArrayList<Fix>()
    private val fixScratch = LongArray(FIX_BEST_COUNT)
    private var burstLeft = 0
    private var nextPingAtElapsedMs = 0L

    private val _syncInfo = MutableStateFlow(SyncInfo())
    val syncInfo: StateFlow<SyncInfo> = _syncInfo.asStateFlow()

    // --- Phone <-> watch clock offset ------------------------------------------------
    private class ClockSample(val offsetMs: Long, val rttMs: Long, val atElapsedMs: Long)

    private val clockSamples = ArrayList<ClockSample>()

    /** phoneWall - watchWall. Zero until the first ping comes back. */
    private var clockOffsetMs = 0L
    private var clockJob: Job? = null

    /** The stamp of the NowPlaying last classified, and whether it was made on THIS watch's clock. */
    private var lastStamp = -1L
    private var stampIsLocal = false

    private var ambient = false
    private var uiVisible = false
    private var tickerJob: Job? = null
    private var lyricsRequestJob: Job? = null
    private var extraTimeoutJob: Job? = null

    init {
        dataClient.addListener(this)
        messageClient.addListener(this)
        viewModelScope.launch {
            restoreLatestPayload()
            restoreOffset()
        }
    }

    // ---------------------------------------------------------------------------------
    // Public API for the UI
    // ---------------------------------------------------------------------------------

    /** Feed every NowPlaying the app already collects. */
    fun onNowPlaying(np: NowPlaying?) {
        val old = nowPlaying
        if (np == null) {
            nowPlaying = null
            playing = false
            _state.update { it.copy(isPlaying = false) }
            tick()
            return
        }

        val shown = if (old != null) currentPositionMs() else 0L
        nowPlaying = np

        // PhoneLink stamps its own local edits (pause, seek) with this watch's clock, the phone's
        // readings carry the phone's. A stamp that is "now" can only be the former, and it is
        // judged when the reading first arrives, not on later looks at the same one.
        if (np.positionAt != lastStamp) {
            lastStamp = np.positionAt
            stampIsLocal = abs(System.currentTimeMillis() - np.positionAt) < LOCAL_STAMP_WINDOW_MS
        }

        val id: String? = np.mediaId
        val trackChanged = id != _state.value.trackId
        if (trackChanged) {
            startTrack(id, np.title, np.artist)
        } else if (_state.value.title != np.title || _state.value.artist != np.artist) {
            _state.update { it.copy(title = np.title, artist = np.artist) }
        }
        if (np.durationMs > 0) durationMs = np.durationMs

        // Fixes describe one unbroken stretch of playback: a new track, a pause, or a jump (a seek)
        // ends it, and a quick burst of pings measures the new one.
        if (trackChanged || !np.advancing) {
            clearFixes()
        } else if (fixes.isNotEmpty()) {
            val wall = System.currentTimeMillis()
            val fixed = fixPositionAt(wall)
            if (fixed != null && abs(fixed - positionOf(np, wall)) > FIX_CONSISTENCY_MS) clearFixes()
        }
        if (np.advancing && fixes.isEmpty()) requestBurst(if (trackChanged) PING_BURST_TRACK else PING_BURST_RESUME)

        val now = SystemClock.elapsedRealtime()
        val target = rawPositionMs()
        val drift = shown - target
        val smooth = old != null && !trackChanged && old.advancing && np.advancing &&
            abs(drift) <= WearSyncPaths.DRIFT_SNAP_THRESHOLD_MS
        blendFromMs = if (smooth) drift else 0L
        blendStartElapsedMs = now
        if (!smooth) hapticSuppressUntilElapsedMs = now + HAPTIC_SUPPRESS_AFTER_SNAP_MS

        playing = np.advancing
        _state.update { it.copy(isPlaying = np.advancing) }

        if (trackChanged && !id.isNullOrEmpty()) ensureLyrics(id)
        tick()
        updateTicker()
    }

    fun setUiVisible(visible: Boolean) {
        uiVisible = visible
        if (visible) {
            tick()
            requestBurst(PING_BURST_OPEN)
        } else {
            clockJob?.cancel()
        }
        updateTicker()
    }

    /** Ambient (AOD): the ticker drops to 1 Hz. */
    fun setAmbient(value: Boolean) {
        if (ambient == value) return
        ambient = value
        tick()
        updateTicker()
    }

    fun cycleFontScale() {
        val steps = FONT_SCALES
        val next = steps[(steps.indexOfFirst { it >= _fontScale.value - 0.01f }.let { if (it < 0) 0 else it } + 1) % steps.size]
        _fontScale.value = next
        prefs.edit().putFloat(KEY_FONT_SCALE, next).apply()
    }

    /** Where to seek the phone so that [line] starts: the same rule as tapping a line on the phone. */
    fun seekTargetMs(line: SyncedLine): Long = (line.startMs + lyricsOffsetMs).coerceAtLeast(0L)

    /** The watch opened its source list: the phone asks every enabled source and answers as they come. */
    fun openSources() {
        val id = _state.value.trackId
        if (id.isNullOrEmpty()) return
        _sources.update { if (it.trackId == id) it.copy(loading = true) else SourcesUi(trackId = id, loading = true) }
        sourcesTimeoutJob?.cancel()
        viewModelScope.launch {
            val nodes = nodeClient.connectedNodes.awaitOrNull().orEmpty()
            val body = id.toByteArray(Charsets.UTF_8)
            nodes.forEach { messageClient.sendMessage(it.id, WearSyncPaths.LYRICS_SOURCES_REQUEST, body).awaitOrNull() }
        }
        // 17 sources on a slow day: stop showing the spinner, whatever has arrived stays.
        sourcesTimeoutJob = viewModelScope.launch {
            delay(SOURCES_TIMEOUT_MS)
            _sources.update { it.copy(loading = false) }
        }
    }

    fun selectSource(sourceId: String) {
        val id = _state.value.trackId
        if (id.isNullOrEmpty()) return
        _sources.update { it.copy(currentId = sourceId) }
        viewModelScope.launch {
            val nodes = nodeClient.connectedNodes.awaitOrNull().orEmpty()
            val body = "$id\n$sourceId".toByteArray(Charsets.UTF_8)
            nodes.forEach { messageClient.sendMessage(it.id, WearSyncPaths.LYRICS_SELECT, body).awaitOrNull() }
        }
    }

    fun toggleTranslation() = setViewMode(LyricsView.TRANSLATION)

    fun toggleRomanization() = setViewMode(LyricsView.ROMANIZATION)

    /** Choosing the mode that is already on goes back to the original lyrics. */
    private fun setViewMode(mode: LyricsView) {
        val next = if (_state.value.viewMode == mode) LyricsView.ORIGINAL else mode
        prefs.edit().putString(KEY_VIEW_MODE, next.name).apply()
        _state.update {
            it.copy(
                viewMode = next,
                extraTexts = emptyList(),
                extraStatus = if (next == LyricsView.ORIGINAL) ExtraStatus.IDLE else ExtraStatus.LOADING,
            )
        }
        requestExtra(next)
    }

    /** Ask the phone to send the lyrics of the current track again. */
    fun requestLyrics() {
        val id = _state.value.trackId ?: return
        viewModelScope.launch { sendLyricsRequest(id) }
    }

    // ---------------------------------------------------------------------------------
    // Data Layer: lyrics payload
    // ---------------------------------------------------------------------------------

    override fun onDataChanged(events: DataEventBuffer) {
        // Extract synchronously: the buffer is released when this method returns.
        var payloadMap: DataMap? = null
        var extraMap: DataMap? = null
        var offsetMap: DataMap? = null
        var sourcesMap: DataMap? = null
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            when (event.dataItem.uri.path) {
                WearSyncPaths.LYRICS_PAYLOAD -> payloadMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                WearSyncPaths.LYRICS_EXTRA -> extraMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                WearSyncPaths.LYRICS_OFFSET -> offsetMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                WearSyncPaths.LYRICS_SOURCES -> sourcesMap = DataMapItem.fromDataItem(event.dataItem).dataMap
            }
        }
        val payload = payloadMap
        val extra = extraMap
        if (payload != null) viewModelScope.launch { handlePayloadDataMap(payload) }
        if (extra != null) viewModelScope.launch { handleExtraDataMap(extra) }
        val sourcesItem = sourcesMap
        if (sourcesItem != null) viewModelScope.launch { handleSourcesDataMap(sourcesItem) }
        val offset = offsetMap
        if (offset != null) viewModelScope.launch { applyOffset(offset.getInt(WearSyncPaths.KEY_OFFSET_MS, 0)) }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearSyncPaths.CLOCK_PONG || event.data.size < 16) return
        val received = System.currentTimeMillis()
        val buffer = ByteBuffer.wrap(event.data)
        val sentAt = buffer.long
        val phoneAt = buffer.long
        // Newer phones also say where their player is; older ones only send the two times.
        val head = if (event.data.size >= 29) {
            val position = buffer.long
            val advancing = buffer.get().toInt() != 0
            PhoneHead(position, advancing, buffer.float)
        } else {
            null
        }
        viewModelScope.launch { recordClockSample(sentAt, phoneAt, received, head) }
    }

    override fun onCleared() {
        dataClient.removeListener(this)
        messageClient.removeListener(this)
        super.onCleared()
    }

    private suspend fun restoreLatestPayload() {
        val map = try {
            val uri = Uri.Builder()
                .scheme(PutDataRequest.WEAR_URI_SCHEME)
                .path(WearSyncPaths.LYRICS_PAYLOAD)
                .build()
            val buffer = dataClient.getDataItems(uri).await()
            try {
                buffer.firstOrNull()?.let { DataMapItem.fromDataItem(it).dataMap }
            } finally {
                buffer.release()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return
        handlePayloadDataMap(map)
    }

    private fun handleSourcesDataMap(map: DataMap) {
        val trackId = map.getString(WearSyncPaths.KEY_TRACK_ID) ?: return
        if (trackId != _state.value.trackId) return
        val ids = map.getStringArrayList(WearSyncPaths.KEY_IDS).orEmpty()
        val labels = map.getStringArrayList(WearSyncPaths.KEY_LABELS).orEmpty()
        val kinds = map.getStringArrayList(WearSyncPaths.KEY_KINDS).orEmpty()
        val items = ids.indices.map { i ->
            LyricsSourceItem(ids[i], labels.getOrElse(i) { ids[i] }, kinds.getOrElse(i) { "line" })
        }
        val done = map.getBoolean(WearSyncPaths.KEY_DONE, false)
        if (done) sourcesTimeoutJob?.cancel()
        _sources.update {
            SourcesUi(
                trackId = trackId,
                items = items,
                currentId = map.getString(WearSyncPaths.KEY_CURRENT).orEmpty().ifEmpty { it.currentId },
                loading = !done,
            )
        }
    }

    private suspend fun restoreOffset() {
        val map = try {
            val uri = Uri.Builder()
                .scheme(PutDataRequest.WEAR_URI_SCHEME)
                .path(WearSyncPaths.LYRICS_OFFSET)
                .build()
            val buffer = dataClient.getDataItems(uri).await()
            try {
                buffer.firstOrNull()?.let { DataMapItem.fromDataItem(it).dataMap }
            } finally {
                buffer.release()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return
        applyOffset(map.getInt(WearSyncPaths.KEY_OFFSET_MS, 0))
    }

    private fun applyOffset(value: Int) {
        if (value == lyricsOffsetMs) return
        val displayed = if (nowPlaying != null) currentPositionMs() else 0L
        lyricsOffsetMs = value
        if (nowPlaying != null) {
            rebaseFrom(displayed)
            tick()
        }
    }

    private suspend fun handlePayloadDataMap(map: DataMap) {
        val trackId = map.getString(WearSyncPaths.KEY_TRACK_ID) ?: return
        val hash = map.getString(WearSyncPaths.KEY_HASH)
        val known = _state.value.trackId

        // The phone looked and found nothing for this track.
        if (hash == WearSyncPaths.NO_LYRICS_HASH) {
            if (known == trackId) {
                payload = null
                _state.update {
                    it.copy(lines = emptyList(), lyricsStatus = LyricsStatus.UNAVAILABLE, activeLineIndex = -1)
                }
            }
            return
        }

        val current = payload
        if (current != null && current.trackId == trackId && current.contentHash == hash) return

        val loaded = cache.get(trackId, hash) ?: run {
            val asset = map.getAsset(WearSyncPaths.KEY_ASSET) ?: return
            val fresh = readAsset(asset) ?: return
            cache.put(fresh)
            fresh
        }

        // An item for a track that is not the one playing is only cached, never shown.
        if (known == null || known == loaded.trackId) adoptPayload(loaded)
    }

    private suspend fun readAsset(asset: Asset): SyncedLyricsPayload? = try {
        val response = dataClient.getFdForAsset(asset).await()
        try {
            withContext(Dispatchers.IO) {
                response.inputStream.use { SyncedLyricsPayload.fromGzipStream(it) }
            }
        } finally {
            response.release()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Could not read lyrics asset", e)
        null
    }

    /** The last choice (original, translation, Latin script) is kept across runs. */
    private fun restoredViewMode(): LyricsView =
        runCatching { LyricsView.valueOf(prefs.getString(KEY_VIEW_MODE, null) ?: "ORIGINAL") }
            .getOrDefault(LyricsView.ORIGINAL)

    /** The language this watch is set to: that is the one a translation is wanted in. */
    private fun watchLanguage(): String {
        val locale = Locale.getDefault()
        return if (locale.language == "zh") locale.toLanguageTag() else locale.language
    }

    private fun kindOf(mode: LyricsView): String = when (mode) {
        LyricsView.TRANSLATION -> WearSyncPaths.KIND_TRANSLATION
        LyricsView.ROMANIZATION -> WearSyncPaths.KIND_ROMANIZATION
        LyricsView.ORIGINAL -> WearSyncPaths.KIND_ORIGINAL
    }

    /** Ask the phone for the translated or romanized text of the current track (or to stop sending it). */
    private fun requestExtra(mode: LyricsView) {
        extraTimeoutJob?.cancel()
        val id = _state.value.trackId
        if (id.isNullOrEmpty()) return
        viewModelScope.launch {
            val nodes = nodeClient.connectedNodes.awaitOrNull().orEmpty()
            val body = "$id\n${kindOf(mode)}\n${watchLanguage()}".toByteArray(Charsets.UTF_8)
            nodes.forEach { messageClient.sendMessage(it.id, WearSyncPaths.LYRICS_TRANSFORM, body).awaitOrNull() }
        }
        if (mode == LyricsView.ORIGINAL) return
        // The phone may be out of reach, or the lookup may fail: do not spin forever.
        extraTimeoutJob = viewModelScope.launch {
            delay(EXTRA_TIMEOUT_MS)
            if (_state.value.extraStatus == ExtraStatus.LOADING) {
                _state.update { it.copy(extraStatus = ExtraStatus.UNAVAILABLE) }
            }
        }
    }

    private fun handleExtraDataMap(map: DataMap) {
        val state = _state.value
        if (map.getString(WearSyncPaths.KEY_TRACK_ID) != state.trackId) return
        if (map.getString(WearSyncPaths.KEY_KIND) != kindOf(state.viewMode)) return
        extraTimeoutJob?.cancel()
        when (map.getString(WearSyncPaths.KEY_STATUS)) {
            WearSyncPaths.STATUS_OK -> {
                val texts = map.getStringArrayList(WearSyncPaths.KEY_TEXTS).orEmpty()
                _state.update { it.copy(extraTexts = texts, extraStatus = ExtraStatus.READY) }
            }
            WearSyncPaths.STATUS_SAME ->
                _state.update { it.copy(extraTexts = emptyList(), extraStatus = ExtraStatus.SAME) }
            else ->
                _state.update { it.copy(extraTexts = emptyList(), extraStatus = ExtraStatus.UNAVAILABLE) }
        }
    }

    private fun startTrack(id: String?, title: String, artist: String) {
        lyricsRequestJob?.cancel()
        _sources.value = SourcesUi()
        payload = null
        _state.update {
            it.copy(
                trackId = id,
                title = title,
                artist = artist,
                lines = emptyList(),
                lyricsStatus = if (id.isNullOrEmpty()) LyricsStatus.IDLE else LyricsStatus.LOADING,
                activeLineIndex = -1,
                extraTexts = emptyList(),
                extraStatus = if (it.viewMode == LyricsView.ORIGINAL) ExtraStatus.IDLE else ExtraStatus.LOADING,
            )
        }
    }

    /** Cache first. Otherwise wait a moment for the phone's own push, then ask for it. */
    private fun ensureLyrics(trackId: String) {
        lyricsRequestJob?.cancel()
        lyricsRequestJob = viewModelScope.launch {
            cache.get(trackId)?.let {
                adoptPayload(it)
                return@launch
            }
            delay(PAYLOAD_GRACE_MS)
            if (payload?.trackId != trackId && _state.value.lyricsStatus == LyricsStatus.LOADING) {
                sendLyricsRequest(trackId)
            }
        }
    }

    private suspend fun adoptPayload(p: SyncedLyricsPayload) {
        payload = p
        _state.update {
            it.copy(
                trackId = p.trackId,
                title = p.title.ifBlank { it.title },
                artist = p.artist.ifBlank { it.artist },
                lines = p.lines,
                lyricsStatus = LyricsStatus.READY,
                activeLineIndex = -1,
            )
        }
        if (p.durationMs > 0 && durationMs == 0L) durationMs = p.durationMs
        hapticSuppressUntilElapsedMs = SystemClock.elapsedRealtime() + HAPTIC_SUPPRESS_AFTER_SNAP_MS
        tick()
        cache.put(p) // refreshes LRU order, or stores a newer version
        // A translation was on for the last track (or these are different lyrics now): ask again.
        val mode = _state.value.viewMode
        if (mode != LyricsView.ORIGINAL) {
            _state.update { it.copy(extraStatus = ExtraStatus.LOADING, extraTexts = emptyList()) }
            requestExtra(mode)
        }
    }

    private suspend fun sendLyricsRequest(trackId: String) {
        val nodes = nodeClient.connectedNodes.awaitOrNull().orEmpty()
        val body = trackId.toByteArray(Charsets.UTF_8)
        nodes.forEach { messageClient.sendMessage(it.id, WearSyncPaths.LYRICS_REQUEST, body).awaitOrNull() }
    }

    private suspend fun <T> Task<T>.awaitOrNull(): T? = try {
        await()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    // ---------------------------------------------------------------------------------
    // Clock
    // ---------------------------------------------------------------------------------

    /** The watch's best reading of the phone's playhead right now, before any smoothing. */
    private fun rawPositionMs(): Long {
        val np = nowPlaying ?: return 0L
        val wall = System.currentTimeMillis()
        // A fix on the phone's playhead beats arithmetic on stamps from two different clocks.
        val base = if (np.advancing) fixPositionAt(wall) ?: positionOf(np, wall) else np.positionMs
        val max = if (np.durationMs > 0) np.durationMs else Long.MAX_VALUE
        return base.coerceAtMost(max) - lyricsOffsetMs
    }

    /** [np] run on from its stamp to [watchWall], with the stamp read on the watch's own clock. */
    private fun positionOf(np: NowPlaying, watchWall: Long): Long {
        if (!np.advancing || np.durationMs <= 0L) return np.positionMs
        val stampOnWatchClock = if (stampIsLocal) np.positionAt else np.positionAt - clockOffsetMs
        val ran = ((watchWall - stampOnWatchClock).coerceAtLeast(0L) * np.speed).toLong()
        return (np.positionMs + ran).coerceAtMost(np.durationMs)
    }

    private fun currentPositionMs(): Long {
        val base = rawPositionMs()
        val t = ((SystemClock.elapsedRealtime() - blendStartElapsedMs).toFloat() / BLEND_WINDOW_MS)
            .coerceIn(0f, 1f)
        val blended = base + (blendFromMs * (1f - t)).toLong()
        val max = if (durationMs > 0) durationMs else Long.MAX_VALUE
        return blended.coerceIn(0L, max)
    }

    /** Keep what is on screen continuous across a change in the clock estimate. */
    private fun rebaseFrom(displayed: Long) {
        val d = displayed - rawPositionMs()
        val smooth = abs(d) <= WearSyncPaths.DRIFT_SNAP_THRESHOLD_MS
        blendFromMs = if (smooth) d else 0L
        blendStartElapsedMs = SystemClock.elapsedRealtime()
        if (!smooth) hapticSuppressUntilElapsedMs = blendStartElapsedMs + HAPTIC_SUPPRESS_AFTER_SNAP_MS
    }

    /** [count] quick pings from now on (a longer burst already queued is kept). */
    private fun requestBurst(count: Int) {
        burstLeft = maxOf(burstLeft, count)
        nextPingAtElapsedMs = 0L
        startPinging()
    }

    /**
     * Pings the phone: a quick burst when the screen opens, the track changes or playback resumes,
     * then one every [PING_REFRESH_MS] while playing. Each answer is a fresh fix on the phone's playhead.
     */
    private fun startPinging() {
        if (clockJob?.isActive == true) return
        clockJob = viewModelScope.launch {
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                if (now >= nextPingAtElapsedMs) {
                    val burst = burstLeft > 0
                    if (burst || playing) sendPing()
                    if (burst) burstLeft--
                    nextPingAtElapsedMs = now + if (burstLeft > 0) PING_GAP_MS else PING_REFRESH_MS
                }
                delay(PING_POLL_MS)
            }
        }
    }

    private suspend fun sendPing() {
        val nodes = nodeClient.connectedNodes.awaitOrNull().orEmpty()
        if (nodes.isEmpty()) return
        val body = ByteBuffer.allocate(8).putLong(System.currentTimeMillis()).array()
        nodes.forEach { messageClient.sendMessage(it.id, WearSyncPaths.CLOCK_PING, body).awaitOrNull() }
    }

    private fun clearFixes() {
        fixes.clear()
        bestFixes.clear()
        _syncInfo.value = SyncInfo()
    }

    /** The few fixes with the shortest round trip are the ones to trust. */
    private fun refreshBestFixes() {
        bestFixes.clear()
        fixes.sortedBy { it.rttMs }.take(FIX_BEST_COUNT).forEach { bestFixes += it }
        _syncInfo.value = SyncInfo(fixes.size, bestFixes.firstOrNull()?.rttMs ?: -1L)
    }

    /** Each good fix run on to [watchWall]; their median, so one odd answer cannot move the lyrics. */
    private fun fixPositionAt(watchWall: Long): Long? {
        val n = bestFixes.size
        if (n == 0) return null
        for (i in 0 until n) {
            val f = bestFixes[i]
            fixScratch[i] = f.positionMs + ((watchWall - f.atWatchWall) * f.speed).toLong()
        }
        java.util.Arrays.sort(fixScratch, 0, n)
        return fixScratch[n / 2]
    }

    /**
     * [sentAt] and [receivedAt] are watch time, [phoneAt] phone time somewhere in between. The
     * phone's reading is taken to be at the midpoint, and the sample with the shortest round trip
     * is the one to trust: delay on the way out and delay on the way back are what spoil it.
     */
    private fun recordClockSample(sentAt: Long, phoneAt: Long, receivedAt: Long, head: PhoneHead?) {
        val rtt = receivedAt - sentAt
        if (rtt < 0 || rtt > MAX_CLOCK_RTT_MS) return
        val displayed = if (nowPlaying != null) currentPositionMs() else 0L
        var changed = false

        // 1. The offset between the two wall clocks: the fallback until a fix exists, and what
        //    reads the phone's stamps on the watch's clock.
        val now = SystemClock.elapsedRealtime()
        clockSamples.removeAll { now - it.atElapsedMs > CLOCK_SAMPLE_TTL_MS }
        clockSamples += ClockSample(offsetMs = phoneAt - (sentAt + rtt / 2), rttMs = rtt, atElapsedMs = now)
        val best = clockSamples.minBy { it.rttMs }
        if (best.offsetMs != clockOffsetMs) {
            clockOffsetMs = best.offsetMs
            Log.d(TAG, "clock offset ${best.offsetMs} ms (round trip ${best.rttMs} ms)")
            changed = true
        }

        // 2. A fix on the phone's playhead itself, taken at the middle of the round trip.
        val np = nowPlaying
        if (head != null && head.positionMs >= 0 && head.advancing && np != null && np.advancing) {
            val at = sentAt + rtt / 2
            // An answer that disagrees with what is held means a seek or a stall: start over from it.
            val held = fixPositionAt(at)
            if (held != null && abs(held - head.positionMs) > FIX_CONSISTENCY_MS) fixes.clear()
            fixes += Fix(head.positionMs, at, rtt, head.speed)
            val wall = System.currentTimeMillis()
            fixes.removeAll { wall - it.atWatchWall > FIX_TTL_MS }
            while (fixes.size > MAX_FIXES) fixes.removeAt(0)
            refreshBestFixes()
            changed = true
        }

        if (changed && np != null) {
            rebaseFrom(displayed)
            tick()
        }
    }

    private fun updateTicker() {
        if (!(playing && uiVisible)) return
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            while (isActive && playing && uiVisible) {
                tick()
                delay(if (ambient) AMBIENT_TICK_MS else FRAME_MS)
            }
        }
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val pos = currentPositionMs()
        _positionMs.value = pos

        val p = payload
        val idx = p?.lineIndexAt(pos) ?: -1
        val prevIdx = _state.value.activeLineIndex
        if (idx == prevIdx) return

        _state.update { it.copy(activeLineIndex = idx) }

        val natural = p != null && idx >= 0 && idx == prevIdx + 1 &&
            playing && !ambient && now >= hapticSuppressUntilElapsedMs
        if (natural) {
            val line = p!!.lines[idx]
            val prevLine = p.lines.getOrNull(prevIdx)
            if (!line.isGap) {
                _events.tryEmit(
                    if (line.isChorus && prevLine?.isChorus != true) LyricsEvent.ChorusEntered
                    else LyricsEvent.LineChanged,
                )
            }
        }
    }

    private companion object {
        const val TAG = "LyricsViewModel"
        const val FRAME_MS = 16L
        const val AMBIENT_TICK_MS = 1_000L
        const val BLEND_WINDOW_MS = 2_500L
        const val PAYLOAD_GRACE_MS = 1_500L
        const val EXTRA_TIMEOUT_MS = 25_000L
        const val HAPTIC_SUPPRESS_AFTER_SNAP_MS = 250L

        /** A stamp this close to "now" at arrival was written on this watch, not by the phone. */
        const val LOCAL_STAMP_WINDOW_MS = 150L
        const val PING_BURST_OPEN = 8
        const val PING_BURST_TRACK = 6
        const val PING_BURST_RESUME = 4
        const val PING_GAP_MS = 450L
        const val PING_REFRESH_MS = 15_000L
        const val PING_POLL_MS = 150L
        const val FIX_TTL_MS = 90_000L
        const val FIX_BEST_COUNT = 5
        const val MAX_FIXES = 16
        const val FIX_CONSISTENCY_MS = 800L
        const val KEY_VIEW_MODE = "view_mode"
        const val MAX_CLOCK_RTT_MS = 4_000L
        const val CLOCK_SAMPLE_TTL_MS = 300_000L

        const val KEY_FONT_SCALE = "font_scale"
        val FONT_SCALES = floatArrayOf(0.9f, 1f, 1.15f, 1.3f)
        const val SOURCES_TIMEOUT_MS = 45_000L
    }
}
