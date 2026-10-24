package com.music.bitchord.wear.ui

import android.app.Application
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
import com.music.bitchord.shared.wear.SyncedLine
import com.music.bitchord.shared.wear.SyncedLyricsPayload
import com.music.bitchord.shared.wear.WearSyncPaths
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
import kotlin.math.abs

enum class LyricsStatus { IDLE, LOADING, READY, UNAVAILABLE }

data class LyricsUiState(
    val trackId: String? = null,
    val title: String = "",
    val artist: String = "",
    val lines: List<SyncedLine> = emptyList(),
    val lyricsStatus: LyricsStatus = LyricsStatus.IDLE,
    val isPlaying: Boolean = false,
    val activeLineIndex: Int = -1,
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

    private val _state = MutableStateFlow(LyricsUiState())
    val state: StateFlow<LyricsUiState> = _state.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

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

    init {
        dataClient.addListener(this)
        messageClient.addListener(this)
        viewModelScope.launch { restoreLatestPayload() }
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
            startClockSync()
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
        val maps = events
            .filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == WearSyncPaths.LYRICS_PAYLOAD }
            .map { DataMapItem.fromDataItem(it.dataItem).dataMap }
        val latest = maps.lastOrNull() ?: return
        viewModelScope.launch { handlePayloadDataMap(latest) }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearSyncPaths.CLOCK_PONG || event.data.size < 16) return
        val received = System.currentTimeMillis()
        val buffer = ByteBuffer.wrap(event.data)
        val sentAt = buffer.long
        val phoneAt = buffer.long
        viewModelScope.launch { recordClockSample(sentAt, phoneAt, received) }
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

    private fun startTrack(id: String?, title: String, artist: String) {
        lyricsRequestJob?.cancel()
        payload = null
        _state.update {
            it.copy(
                trackId = id,
                title = title,
                artist = artist,
                lines = emptyList(),
                lyricsStatus = if (id.isNullOrEmpty()) LyricsStatus.IDLE else LyricsStatus.LOADING,
                activeLineIndex = -1,
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
        return positionOf(np, System.currentTimeMillis()) + CLOCK_TRIM_MS
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

    /** A short burst of pings when the screen opens, then one a minute while it is on. */
    private fun startClockSync() {
        if (clockJob?.isActive == true) return
        clockJob = viewModelScope.launch {
            var sent = 0
            while (isActive) {
                val nodes = nodeClient.connectedNodes.awaitOrNull().orEmpty()
                val body = ByteBuffer.allocate(8).putLong(System.currentTimeMillis()).array()
                nodes.forEach { messageClient.sendMessage(it.id, WearSyncPaths.CLOCK_PING, body).awaitOrNull() }
                sent++
                delay(if (sent < CLOCK_BURST) CLOCK_BURST_GAP_MS else CLOCK_REFRESH_MS)
            }
        }
    }

    /**
     * [sentAt] and [receivedAt] are watch time, [phoneAt] phone time somewhere in between. The
     * phone's reading is taken to be at the midpoint, and the sample with the shortest round trip
     * is the one to trust: delay on the way out and delay on the way back are what spoil it.
     */
    private fun recordClockSample(sentAt: Long, phoneAt: Long, receivedAt: Long) {
        val rtt = receivedAt - sentAt
        if (rtt < 0 || rtt > MAX_CLOCK_RTT_MS) return
        val now = SystemClock.elapsedRealtime()
        clockSamples.removeAll { now - it.atElapsedMs > CLOCK_SAMPLE_TTL_MS }
        clockSamples += ClockSample(offsetMs = phoneAt - (sentAt + rtt / 2), rttMs = rtt, atElapsedMs = now)
        val best = clockSamples.minBy { it.rttMs }
        if (best.offsetMs == clockOffsetMs) return

        val displayed = if (nowPlaying != null) currentPositionMs() else 0L
        clockOffsetMs = best.offsetMs
        Log.d(TAG, "clock offset ${best.offsetMs} ms (round trip ${best.rttMs} ms)")
        if (nowPlaying != null) {
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
            _events.tryEmit(
                if (line.isChorus && prevLine?.isChorus != true) LyricsEvent.ChorusEntered
                else LyricsEvent.LineChanged,
            )
        }
    }

    private companion object {
        const val TAG = "LyricsViewModel"
        const val FRAME_MS = 16L
        const val AMBIENT_TICK_MS = 1_000L
        const val BLEND_WINDOW_MS = 2_500L
        const val PAYLOAD_GRACE_MS = 1_500L
        const val HAPTIC_SUPPRESS_AFTER_SNAP_MS = 250L

        /** A stamp this close to "now" at arrival was written on this watch, not by the phone. */
        const val LOCAL_STAMP_WINDOW_MS = 150L
        const val CLOCK_BURST = 6
        const val CLOCK_BURST_GAP_MS = 400L
        const val CLOCK_REFRESH_MS = 60_000L
        const val MAX_CLOCK_RTT_MS = 4_000L
        const val CLOCK_SAMPLE_TTL_MS = 300_000L

        /**
         * Fine adjustment on top of the measured clock offset, for a speaker or earbuds that are
         * consistently early or late (positive = lyrics appear earlier, negative = later).
         */
        const val CLOCK_TRIM_MS = 0L
    }
}
