package com.music.bitchord.playback.connect

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.music.bitchord.data.connect.ConnectClientFrame
import com.music.bitchord.data.connect.ConnectPlaybackState
import com.music.bitchord.data.connect.ConnectProtocol
import com.music.bitchord.data.connect.ConnectQueueState
import com.music.bitchord.data.connect.ConnectServerFrame
import com.music.bitchord.data.connect.ConnectTrack
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.cast.CastSink
import com.music.bitchord.playback.cast.RemoteSpeaker
import com.music.bitchord.playback.toMediaItem
import com.music.bitchord.playback.toSong
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * BitChord PC on the local network as a [RemoteSpeaker].
 *
 * Follows the exact same contract as [com.music.bitchord.playback.cast.CastPlayback] and
 * [com.music.bitchord.wear.WatchSpeaker]:
 * - The phone's local [ExoPlayer] stays paused/idle (never streams or decodes audio).
 * - The PC plays the audio and owns the clock.
 * - Playback controls, track picks, queue edits, repeat changes, and volume adjustments on the
 *   phone are forwarded over WebSocket to the PC.
 * - Track/state/queue/volume changes made directly on the PC flow back to the phone so the UI,
 *   notification, lock screen, and widgets stay synchronized in real time.
 */
class DesktopConnectPlayback(
    private val scope: CoroutineScope,
    private val localPlayer: () -> ExoPlayer?,
    private val onChanged: () -> Unit,
    private val say: (String) -> Unit,
) : RemoteSpeaker {

    override var sink: CastSink? = null

    @Volatile
    override var active: Boolean = false
        private set

    private val main = Handler(Looper.getMainLooper())
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val wsClient by lazy {
        HttpClient(OkHttp) {
            install(WebSockets)
            engine {
                config {
                    connectTimeout(6, TimeUnit.SECONDS)
                    readTimeout(0, TimeUnit.SECONDS)
                    writeTimeout(10, TimeUnit.SECONDS)
                    pingInterval(15, TimeUnit.SECONDS)
                    retryOnConnectionFailure(true)
                }
            }
        }
    }

    private val sendMutex = Mutex()
    @Volatile private var session: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null
    private var upcomingDebounceJob: Job? = null
    private var activeDevice: SavedDesktopDevice? = null

    private var remoteState: ConnectPlaybackState? = null
    private var remoteQueue: ConnectQueueState = ConnectQueueState()
    private var lastAppliedSeq: Long = -1L
    private var clockOffsetMs: Long = 0L

    private var wantsPlay = false
    private var loading = false
    private var requestedPositionMs = 0L
    private var requestedAt = 0L
    private var expectedVideoId: String? = null
    private var expectedAt = 0L
    private var applyingRemote = false

    override val playWhenReady: Boolean get() = wantsPlay

    override val isPlaying: Boolean
        get() = active && !loading && remoteState?.let { it.isPlaying && !it.isLoading && !it.buffering } == true

    override val playbackState: Int
        get() = when {
            loading -> Player.STATE_BUFFERING
            remoteState == null -> Player.STATE_BUFFERING
            remoteState?.isLoading == true || remoteState?.buffering == true -> Player.STATE_BUFFERING
            else -> Player.STATE_READY
        }

    override val positionMs: Long
        get() {
            val s = remoteState
            if (s == null || loading || SystemClock.elapsedRealtime() - requestedAt < SEEK_GRACE_MS) {
                return requestedPositionMs.coerceAtLeast(0L)
            }
            return s.positionAt(System.currentTimeMillis() + clockOffsetMs)
        }

    override val durationMs: Long
        get() = remoteState?.durationMs?.takeIf { it > 0L } ?: C.TIME_UNSET

    override val deviceVolume: Int
        get() = ((remoteState?.volume ?: DesktopConnectController.volume.value).coerceIn(0f, 1f) * 100f).roundToInt()

    // ---- Connection lifecycle ---------------------------------------------------

    fun connectTo(device: SavedDesktopDevice) {
        disconnectInternal(resumeHere = false, notifyController = false)
        activeDevice = device
        lastAppliedSeq = -1L
        clockOffsetMs = 0L
        remoteState = null
        remoteQueue = ConnectQueueState()
        expectedVideoId = null

        connectionJob = scope.launch(Dispatchers.IO) {
            var disconnectError: String? = null
            var unauthorizedId: String? = null
            try {
                wsClient.webSocket(
                    urlString = "ws://${device.host}:${device.port}/connect/ws",
                    request = {
                        header(HttpHeaders.Authorization, "Bearer ${device.token}")
                    },
                ) {
                    session = this
                    val pingJob = launch {
                        while (isActive) {
                            sendFrame(ConnectClientFrame.ping(System.currentTimeMillis()))
                            delay(PING_INTERVAL_MS)
                        }
                    }
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val serverFrame = runCatching {
                                json.decodeFromString<ConnectServerFrame>(frame.readText())
                            }.getOrNull() ?: continue
                            handleServerFrame(device, serverFrame)
                        }
                        val reason = closeReason.await()
                        if (reason?.code == CloseReason.Codes.VIOLATED_POLICY.code) {
                            unauthorizedId = device.deviceId
                            disconnectError = "Device was unpaired on PC. Pair again with the PIN."
                        }
                    } finally {
                        pingJob.cancel()
                        if (session === this) session = null
                    }
                }
            } catch (_: CancellationException) {
                return@launch
            } catch (e: Exception) {
                Log.w(TAG, "Connect WebSocket failed for ${device.host}:${device.port}", e)
                disconnectError = "Couldn't reach ${device.name} (${device.host}:${device.port})"
            } finally {
                if (activeDevice?.deviceId == device.deviceId) {
                    main.post {
                        if (activeDevice?.deviceId == device.deviceId) {
                            teardownLocal(resumeHere = false)
                            DesktopConnectController.onDisconnected(
                                errorMessage = disconnectError,
                                unauthorizedDeviceId = unauthorizedId,
                            )
                        }
                    }
                }
            }
        }
    }

    fun disconnect(resumeHere: Boolean) {
        disconnectInternal(resumeHere = resumeHere, notifyController = false)
    }

    fun release() {
        disconnectInternal(resumeHere = false, notifyController = false)
    }

    private fun disconnectInternal(resumeHere: Boolean, notifyController: Boolean) {
        val prevSession = session
        if (resumeHere && prevSession != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    sendRaw(prevSession, ConnectClientFrame.control(ConnectProtocol.ACTION_PAUSE))
                    prevSession.close(CloseReason(CloseReason.Codes.NORMAL, "handoff_to_phone"))
                }
            }
        } else if (prevSession != null) {
            scope.launch(Dispatchers.IO) {
                runCatching { prevSession.close(CloseReason(CloseReason.Codes.NORMAL, "disconnect")) }
            }
        }
        session = null
        connectionJob?.cancel()
        connectionJob = null
        upcomingDebounceJob?.cancel()
        upcomingDebounceJob = null
        activeDevice = null

        if (Looper.myLooper() == Looper.getMainLooper()) {
            teardownLocal(resumeHere)
            if (notifyController) DesktopConnectController.onDisconnected()
        } else {
            main.post {
                teardownLocal(resumeHere)
                if (notifyController) DesktopConnectController.onDisconnected()
            }
        }
    }

    private fun teardownLocal(resumeHere: Boolean) {
        if (!active) return
        val lastTrackId = remoteState?.track?.videoId
        val wasPlaying = wantsPlay || isPlaying
        val handoffPosition = positionMs
        active = false
        remoteState = null
        remoteQueue = ConnectQueueState()
        loading = false
        wantsPlay = false
        expectedVideoId = null
        localPlayer()?.let { player ->
            val idx = lastTrackId?.let { findIndex(player, it) }
            if (idx != null) player.seekTo(idx, handoffPosition) else player.seekTo(handoffPosition)
            if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) player.prepare()
            if (resumeHere && wasPlaying) player.play()
        }
        sink?.onRemoteRouteChanged()
        onChanged()
    }

    // ---- RemoteSpeaker transport ------------------------------------------------

    override fun play() {
        if (!active) return
        wantsPlay = true
        val player = localPlayer()
        if (remoteState?.track == null && player?.currentMediaItem != null) {
            sendQueueLoad(player, player.currentMediaItemIndex, 0L, play = true)
            return
        }
        sendControl(ConnectClientFrame.control(ConnectProtocol.ACTION_PLAY))
        sink?.onRemotePlaybackChanged()
        onChanged()
    }

    override fun pause() {
        if (!active) return
        wantsPlay = false
        sendControl(ConnectClientFrame.control(ConnectProtocol.ACTION_PAUSE))
        sink?.onRemotePlaybackChanged()
        onChanged()
    }

    override fun seekTo(positionMs: Long) {
        if (!active) return
        val clamped = positionMs.coerceAtLeast(0L)
        requestedPositionMs = clamped
        requestedAt = SystemClock.elapsedRealtime()
        sendControl(ConnectClientFrame.control(ConnectProtocol.ACTION_SEEK, positionMs = clamped))
        sink?.onRemotePlaybackChanged()
        onChanged()
    }

    override fun setVolume(fraction: Float) {
        if (!active) return
        val clamped = fraction.coerceIn(0f, 1f)
        remoteState = remoteState?.copy(volume = clamped)
        DesktopConnectController.onRemoteVolume(clamped)
        sendControl(ConnectClientFrame.control(ConnectProtocol.ACTION_SET_VOLUME, volume = clamped))
        sink?.onRemoteVolumeChanged()
    }

    override fun restoreQueueCurrent(item: MediaItem?, positionMs: Long, playWhenReady: Boolean): Boolean {
        if (!active) return false
        val player = localPlayer() ?: return false
        if (item == null) {
            wantsPlay = false
            loading = false
            sendControl(ConnectClientFrame.control(ConnectProtocol.ACTION_PAUSE))
            sink?.onRemotePlaybackChanged()
            onChanged()
            return true
        }
        val idx = player.currentMediaItemIndex.takeIf { it in 0 until player.mediaItemCount } ?: return false
        sendQueueLoad(player, idx, positionMs.coerceAtLeast(0L), play = playWhenReady)
        return true
    }

    override fun onLocalTransition(item: MediaItem?, reason: Int) {
        if (!active || applyingRemote || item == null) return
        if (item.mediaId == remoteState?.track?.videoId && !loading) {
            return
        }
        val player = localPlayer() ?: return
        val shouldPlay = wantsPlay || player.playWhenReady || reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        if (item.mediaId in remoteQueue.items.map { it.videoId } && !loading) {
            val remoteIdx = remoteQueue.items.indexOfFirst { it.videoId == item.mediaId }.takeIf { it >= 0 }
            wantsPlay = shouldPlay
            loading = true
            expectedVideoId = item.mediaId
            expectedAt = SystemClock.elapsedRealtime()
            requestedPositionMs = 0L
            requestedAt = expectedAt
            sendControl(
                ConnectClientFrame.control(
                    action = ConnectProtocol.ACTION_JUMP_TO,
                    videoId = item.mediaId,
                    index = remoteIdx,
                    positionMs = 0L,
                    play = shouldPlay,
                ),
            )
            sink?.onRemotePlaybackChanged()
            onChanged()
        } else {
            val idx = findIndex(player, item.mediaId) ?: player.currentMediaItemIndex
            sendQueueLoad(player, idx, 0L, play = shouldPlay)
        }
    }

    override fun onLocalQueueChanged() {
        if (!active || applyingRemote) return
        upcomingDebounceJob?.cancel()
        upcomingDebounceJob = scope.launch(Dispatchers.Main) {
            delay(200L)
            if (!active || applyingRemote) return@launch
            val player = localPlayer() ?: return@launch
            val currentIdx = player.currentMediaItemIndex
            if (currentIdx !in 0 until player.mediaItemCount) return@launch
            val upcoming = buildConnectTracks(
                player = player,
                fromIndex = currentIdx + 1,
                maxCount = ConnectProtocol.MAX_QUEUE_ITEMS - 1,
            )
            sendControl(ConnectClientFrame.control(ConnectProtocol.ACTION_UPDATE_UPCOMING, tracks = upcoming))
        }
    }

    override fun onLocalRepeatChanged(repeatMode: Int) {
        if (!active || applyingRemote) return
        sendControl(
            ConnectClientFrame.control(
                action = ConnectProtocol.ACTION_SET_REPEAT,
                repeatMode = toWireRepeat(repeatMode),
            ),
        )
    }

    override fun onLocalStartedPlaying(player: Player) {
        if (!active) return
        // Never allow audio out of the phone while PC is the active speaker.
        player.pause()
        player.stop()
        val current = player.currentMediaItem
        if (current != null && current.mediaId != expectedVideoId && current.mediaId != remoteState?.track?.videoId) {
            val idx = player.currentMediaItemIndex.coerceAtLeast(0)
            sendQueueLoad(player, idx, 0L, play = true)
        } else {
            play()
        }
    }

    // ---- Inbound PC state -------------------------------------------------------

    private fun handleServerFrame(device: SavedDesktopDevice, frame: ConnectServerFrame) {
        when (frame.type) {
            ConnectProtocol.FRAME_PONG -> {
                val sent = frame.clientMs ?: return
                val now = System.currentTimeMillis()
                val rtt = (now - sent).coerceAtLeast(0L)
                if (rtt < 1_000L) {
                    clockOffsetMs = frame.serverMs - (sent + rtt / 2L)
                }
            }
            ConnectProtocol.FRAME_WELCOME -> {
                val now = System.currentTimeMillis()
                if (frame.serverMs > 0L) clockOffsetMs = frame.serverMs - now
                main.post {
                    if (activeDevice?.deviceId != device.deviceId) return@post
                    onWelcomeMain(device, frame)
                }
            }
            ConnectProtocol.FRAME_QUEUE -> {
                val q = frame.queue ?: return
                main.post {
                    if (!active) return@post
                    remoteQueue = q
                    syncLocalQueueIfNeeded(q, remoteState?.track?.videoId)
                }
            }
            ConnectProtocol.FRAME_STATE -> {
                val s = frame.state ?: return
                main.post {
                    if (!active) return@post
                    applyRemoteState(s)
                }
            }
        }
    }

    private fun onWelcomeMain(device: SavedDesktopDevice, frame: ConnectServerFrame) {
        val player = localPlayer()
        val wasPlayingLocal = player != null && player.playWhenReady && player.playbackState != Player.STATE_ENDED &&
            player.currentMediaItem != null
        val localPos = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
        val localIdx = player?.currentMediaItemIndex ?: -1

        active = true
        frame.queue?.let { remoteQueue = it }
        frame.state?.let { s ->
            remoteState = s
            lastAppliedSeq = s.seq
            DesktopConnectController.onRemoteVolume(s.volume)
        }
        DesktopConnectController.onConnected(device, frame.deviceName)

        // Quiet the phone immediately so both devices never play together.
        if (player != null) {
            player.pause()
            player.stop()
        }

        val pcHasTrack = frame.state?.track != null
        when {
            wasPlayingLocal && player != null && localIdx in 0 until player.mediaItemCount -> {
                // Spotify-Connect handoff: phone was playing -> transfer playback & queue to PC.
                sendQueueLoad(player, localIdx, localPos, play = true)
            }
            pcHasTrack -> {
                // PC is already active (playing or paused) -> adopt PC's queue & mirror it on phone!
                val s = frame.state!!
                wantsPlay = s.isPlaying
                loading = s.isLoading
                val queueToAdopt = frame.queue?.takeIf { it.items.isNotEmpty() }
                    ?: ConnectQueueState(seq = s.queueSeq, index = 0, items = listOfNotNull(s.track))
                adoptRemoteQueueIntoPhone(queueToAdopt, s.track?.videoId, s.positionMs)
            }
            player != null && localIdx in 0 until player.mediaItemCount -> {
                // Phone was paused with a loaded track and PC was empty -> mirror it onto PC paused.
                sendQueueLoad(player, localIdx, localPos, play = false)
            }
        }

        sink?.onRemoteRouteChanged()
        sink?.onRemotePlaybackChanged()
        sink?.onRemoteVolumeChanged()
        onChanged()
    }

    private fun applyRemoteState(s: ConnectPlaybackState) {
        if (s.seq > 0L && s.seq < lastAppliedSeq) return
        if (s.seq > 0L) lastAppliedSeq = s.seq

        val now = SystemClock.elapsedRealtime()
        val expected = expectedVideoId
        if (expected != null) {
            if (s.track?.videoId == expected) {
                expectedVideoId = null
                loading = s.isLoading
            } else if (now - expectedAt < LOAD_GRACE_MS) {
                // Wait briefly for the PC to switch to the track the phone just asked for.
                return
            } else {
                expectedVideoId = null
                loading = s.isLoading
            }
        } else {
            loading = s.isLoading
        }

        val prevVol = remoteState?.volume
        remoteState = s
        if (now - requestedAt >= SEEK_GRACE_MS) {
            requestedAt = 0L
        }
        if (!loading) {
            wantsPlay = s.isPlaying
        }

        val volumeChanged = prevVol == null || kotlin.math.abs(prevVol - s.volume) > 0.005f
        if (volumeChanged) {
            DesktopConnectController.onRemoteVolume(s.volume)
        }

        // Keep phone's paused ExoPlayer aligned to whatever track PC is now playing.
        val pcVideoId = s.track?.videoId
        val player = localPlayer()
        if (player != null && !pcVideoId.isNullOrBlank() && player.currentMediaItem?.mediaId != pcVideoId) {
            val found = findIndex(player, pcVideoId)
            applyingRemote = true
            try {
                if (found != null) {
                    player.seekTo(found, 0L)
                } else {
                    val queueItems = remoteQueue.items.takeIf { list -> list.any { it.videoId == pcVideoId } }
                        ?: listOfNotNull(s.track)
                    adoptRemoteQueueIntoPhone(
                        ConnectQueueState(seq = s.queueSeq, index = s.queueIndex, items = queueItems),
                        pcVideoId,
                        s.positionMs,
                    )
                }
            } finally {
                applyingRemote = false
            }
        }

        sink?.onRemotePlaybackChanged()
        if (volumeChanged) sink?.onRemoteVolumeChanged()
        onChanged()
    }

    private fun syncLocalQueueIfNeeded(queue: ConnectQueueState, activeVideoId: String?) {
        if (queue.items.isEmpty()) return
        val player = localPlayer() ?: return
        val localIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        val remoteIds = queue.items.map { it.videoId }
        if (localIds == remoteIds) return
        // Only adopt remote queue if the phone is not in the middle of sending a new local selection.
        if (expectedVideoId != null && SystemClock.elapsedRealtime() - expectedAt < LOAD_GRACE_MS) return
        adoptRemoteQueueIntoPhone(queue, activeVideoId, remoteState?.positionMs ?: 0L)
    }

    private fun adoptRemoteQueueIntoPhone(queue: ConnectQueueState, activeVideoId: String?, positionMs: Long) {
        val player = localPlayer() ?: return
        if (queue.items.isEmpty()) return
        val items = queue.items.map { it.toAndroidSong().toMediaItem() }
        val targetIndex = activeVideoId?.let { id -> queue.items.indexOfFirst { it.videoId == id }.takeIf { it >= 0 } }
            ?: queue.index.coerceIn(items.indices)
        applyingRemote = true
        try {
            player.playWhenReady = false
            player.setMediaItems(items, targetIndex, positionMs.coerceAtLeast(0L))
            // Keep paused/stopped so the phone never opens a media stream while mirroring PC.
            player.stop()
        } finally {
            applyingRemote = false
        }
    }

    // ---- Outbound helpers -------------------------------------------------------

    private fun sendQueueLoad(player: Player, index: Int, positionMs: Long, play: Boolean) {
        if (index !in 0 until player.mediaItemCount) return
        val currentItem = player.getMediaItemAt(index)
        val currentSong = currentItem.toSong()
        if (isLocalOnlySong(currentSong)) {
            say("Local phone files cannot be played on PC")
            return
        }
        // Build a centered window around `index` (up to MAX_QUEUE_ITEMS), skipping local-only files.
        val windowStart = (index - 10).coerceAtLeast(0)
        val windowEnd = (windowStart + ConnectProtocol.MAX_QUEUE_ITEMS).coerceAtMost(player.mediaItemCount)
        val tracks = ArrayList<ConnectTrack>(windowEnd - windowStart)
        var mappedIndex = 0
        for (i in windowStart until windowEnd) {
            val item = player.getMediaItemAt(i)
            val song = item.toSong()
            if (isLocalOnlySong(song)) continue
            if (i == index) mappedIndex = tracks.size
            tracks += song.toConnectTrack(
                durationMs = if (i == index) player.duration.takeIf { it > 0L } else null,
            )
        }
        if (tracks.isEmpty()) return

        wantsPlay = play
        loading = true
        expectedVideoId = currentItem.mediaId
        expectedAt = SystemClock.elapsedRealtime()
        requestedPositionMs = positionMs.coerceAtLeast(0L)
        requestedAt = expectedAt
        remoteQueue = ConnectQueueState(index = mappedIndex, items = tracks)

        sendControl(
            ConnectClientFrame.control(
                action = ConnectProtocol.ACTION_LOAD_QUEUE,
                tracks = tracks,
                index = mappedIndex,
                positionMs = requestedPositionMs,
                play = play,
                repeatMode = toWireRepeat(player.repeatMode),
            ),
        )
        sink?.onRemotePlaybackChanged()
        onChanged()
    }

    private fun buildConnectTracks(player: Player, fromIndex: Int, maxCount: Int): List<ConnectTrack> {
        if (fromIndex !in 0 until player.mediaItemCount || maxCount <= 0) return emptyList()
        val end = (fromIndex + maxCount).coerceAtMost(player.mediaItemCount)
        val out = ArrayList<ConnectTrack>(end - fromIndex)
        for (i in fromIndex until end) {
            val song = player.getMediaItemAt(i).toSong()
            if (!isLocalOnlySong(song)) out += song.toConnectTrack()
        }
        return out
    }

    private fun sendControl(frame: ConnectClientFrame) {
        scope.launch(Dispatchers.IO) {
            sendFrame(frame)
        }
    }

    private suspend fun sendFrame(frame: ConnectClientFrame) {
        val current = session ?: return
        sendRaw(current, frame)
    }

    private suspend fun sendRaw(target: DefaultClientWebSocketSession, frame: ConnectClientFrame) {
        sendMutex.withLock {
            runCatching { target.send(Frame.Text(json.encodeToString(frame))) }
                .onFailure { Log.w(TAG, "Failed to send connect frame ${frame.type}/${frame.action}", it) }
        }
    }

    private fun findIndex(player: Player, videoId: String): Int? {
        val at = player.currentMediaItemIndex.coerceAtLeast(0)
        return (0 until player.mediaItemCount)
            .filter { player.getMediaItemAt(it).mediaId == videoId }
            .minByOrNull { kotlin.math.abs(it - at) }
    }

    companion object {
        private const val TAG = "BitChordConnect"
        private const val PING_INTERVAL_MS = 10_000L
        private const val SEEK_GRACE_MS = 1_500L
        private const val LOAD_GRACE_MS = 3_500L

        internal fun toWireRepeat(repeatMode: Int): String = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> ConnectProtocol.REPEAT_ONE
            Player.REPEAT_MODE_ALL -> ConnectProtocol.REPEAT_ALL
            else -> ConnectProtocol.REPEAT_OFF
        }

        internal fun isLocalOnlySong(song: Song): Boolean =
            (song.localUri != null || song.localPath != null) &&
                (song.videoId.startsWith("local:") || song.videoId.startsWith("content://") || song.videoId.startsWith("file://"))

        internal fun Song.toConnectTrack(durationMs: Long? = null): ConnectTrack = ConnectTrack(
            videoId = videoId,
            title = title,
            artist = artist,
            albumName = albumName,
            thumbnailUrl = thumbnailUrl,
            durationMs = durationMs?.takeIf { it > 0L } ?: parseDurationMs(durationText),
            isVideo = isVideo,
            fromAutoplay = queueTier == QueueTier.AUTOPLAY || fromAutoplay,
        )

        internal fun ConnectTrack.toAndroidSong(): Song = Song(
            videoId = videoId,
            title = title,
            artist = artist,
            albumName = albumName,
            thumbnailUrl = thumbnailUrl,
            durationText = durationMs?.takeIf { it > 0L }?.let { ms ->
                val total = ms / 1000L
                "%d:%02d".format(total / 60L, total % 60L)
            },
            isVideo = isVideo,
            isVideoOrigin = isVideo,
            queueTier = if (fromAutoplay) QueueTier.AUTOPLAY else QueueTier.USER_QUEUE,
        )

        private fun parseDurationMs(text: String?): Long? {
            val parts = text?.trim()?.split(':')?.mapNotNull(String::toLongOrNull) ?: return null
            return when (parts.size) {
                2 -> (parts[0] * 60L + parts[1]) * 1000L
                3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
                else -> null
            }?.takeIf { it > 0L }
        }
    }
}
