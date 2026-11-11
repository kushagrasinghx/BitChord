package com.music.bitchord.playback.dlna

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.music.bitchord.R
import com.music.bitchord.playback.cast.CastController
import com.music.bitchord.playback.cast.CastSink
import com.music.bitchord.playback.cast.CastStream
import com.music.bitchord.playback.cast.RemoteSpeaker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * A DLNA / UPnP AV renderer on the Wi-Fi as the speaker — [CastPlayback][com.music.bitchord.playback.cast.CastPlayback]'s
 * model with a MediaRenderer as the receiver.
 *
 * The phone's player keeps the queue and is held idle; the renderer plays the
 * current track and owns the clock, which the session player reports. The
 * stream URL is resolved here, exactly as for Cast, and handed over finished.
 * The next track is queued with `SetNextAVTransportURI` so the renderer moves on
 * gaplessly by itself; renderers without it are moved on when they stop at the
 * end of a track. UPnP has no push here (no event subscription, which would
 * need a server on the phone), so the renderer is polled about once a second
 * while it is the speaker. Main thread throughout; SOAP runs on IO.
 */
internal class DlnaSpeaker(
    private val scope: CoroutineScope,
    private val localPlayer: () -> ExoPlayer?,
    private val resolve: suspend (MediaItem) -> CastStream?,
    private val say: (Int) -> Unit,
    private val onChanged: () -> Unit,
) : RemoteSpeaker {

    override var sink: CastSink? = null

    init {
        // A Cast session starting takes the music: one speaker at a time.
        scope.launch {
            CastController.state.map { it.casting }.distinctUntilChanged().collect { casting ->
                if (casting && active) stop(resumeHere = false)
            }
        }
    }

    override var active = false
        private set

    /** The renderer, while it is the speaker. */
    var device: DlnaDevice? = null
        private set

    // The renderer's last word.
    private var transportState = ""
    private var statusPositionMs = 0L
    private var statusDurationMs = 0L
    private var statusAt = 0L
    private var volume = 0

    // What the phone asked for.
    private var wantsPlay = false

    /** The current load's SetAVTransportURI has gone out (a play() after this must send Play itself). */
    private var loadSent = false
    private var loading = false
    private var loadStartedAt = 0L
    private var finished = false
    private var requestedPositionMs = 0L
    private var requestedAt = 0L
    private var pendingSeekMs = 0L

    // What the renderer holds.
    private var currentId: String? = null
    private var currentUrl: String? = null
    private var currentDurationMs = 0L
    private var nextId: String? = null
    private var nextUrl: String? = null
    private var playedPositionMs = 0L
    private var sawPlaying = false

    private var generation = 0
    private var loadJob: Job? = null
    private var nextJob: Job? = null
    private var pollJob: Job? = null
    private var failures = 0

    override val playWhenReady: Boolean get() = wantsPlay
    override val isPlaying: Boolean get() = active && !loading && transportState == PLAYING

    override val playbackState: Int
        get() = when {
            loading -> Player.STATE_BUFFERING
            finished -> Player.STATE_ENDED
            transportState == TRANSITIONING -> Player.STATE_BUFFERING
            else -> Player.STATE_READY
        }

    override val positionMs: Long
        get() {
            if (loading || SystemClock.elapsedRealtime() - requestedAt < SEEK_GRACE_MS) return requestedPositionMs
            if (transportState != PLAYING) return statusPositionMs
            val ran = SystemClock.elapsedRealtime() - statusAt
            val duration = durationMs
            return (statusPositionMs + ran).let { if (duration > 0) it.coerceAtMost(duration) else it }
        }

    override val durationMs: Long
        get() = statusDurationMs.takeIf { it > 0 } ?: currentDurationMs.takeIf { it > 0 } ?: C.TIME_UNSET

    override val deviceVolume: Int get() = volume

    // ---- start / stop -------------------------------------------------------

    /** Make [target] the speaker, picking the queue up where the phone's player is. */
    fun start(target: DlnaDevice) {
        if (active && device?.id == target.id) return
        if (active) stop(resumeHere = false) // another renderer: hand back first, then over
        val player = localPlayer() ?: return
        if (player.mediaItemCount == 0) return
        device = target
        active = true
        failures = 0
        val wasPlaying = player.playWhenReady && player.playbackState != Player.STATE_ENDED
        val position = player.currentPosition.coerceAtLeast(0L)
        // Quiet and idle, not just paused: a paused player keeps buffering
        // whatever track the renderer moves it to.
        player.pause()
        player.stop()
        load(player.currentMediaItem, position, play = wasPlaying)
        startPolling()
        refreshVolume()
        sink?.onRemoteRouteChanged()
        changed()
    }

    /** Hands the music back to the phone at the renderer's place. [resumeHere]: the listener picked a phone output. */
    fun stop(resumeHere: Boolean) {
        if (!active) return
        val target = device
        val wasPlaying = wantsPlay
        val position = positionMs
        val id = currentId
        generation++
        loadJob?.cancel()
        nextJob?.cancel()
        pollJob?.cancel()
        active = false
        if (target != null) DlnaOutput.background { Upnp.soap(target.avTransportUrl, Upnp.AV_TRANSPORT, "Stop", INSTANCE) }
        resetRendererState()
        localPlayer()?.let { player ->
            val index = id?.let { indexOf(player, it) }
            if (index != null) player.seekTo(index, position) else player.seekTo(position)
            if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) player.prepare()
            if (resumeHere && wasPlaying) player.play()
        }
        device = null
        wantsPlay = false
        sink?.onRemoteRouteChanged()
        sink?.onRemotePlaybackChanged()
        changed()
    }

    private fun resetRendererState() {
        transportState = ""
        statusPositionMs = 0L
        statusDurationMs = 0L
        loading = false
        finished = false
        pendingSeekMs = 0L
        currentId = null
        currentUrl = null
        currentDurationMs = 0L
        nextId = null
        nextUrl = null
        sawPlaying = false
    }

    // ---- transport ------------------------------------------------------------

    override fun play() {
        if (!active) return
        wantsPlay = true
        // Picking another track calls play() before the transition reaches onLocalTransition: the phone's
        // player already has the new item while the renderer still holds the old one, paused. A bare Play
        // here would resume the old song for as long as the new stream takes to resolve. Load instead.
        val current = localPlayer()?.currentMediaItem
        if (finished || (current != null && current.mediaId != currentId)) {
            finished = false
            current?.let { load(it, 0L, play = true) }
            return
        }
        // A track is on its way and not on the renderer yet: its load sends Play itself.
        if (loading && !loadSent) {
            sink?.onRemotePlaybackChanged()
            changed()
            return
        }
        if (transportState == PAUSED) transportState = PLAYING
        send("Play", listOf("Speed" to "1"))
        // tell the session now (as CastPlayback does): otherwise the controller's masked state is
        // replaced by the session's stale one when the command is acknowledged — the icon flickers
        sink?.onRemotePlaybackChanged()
        changed()
    }

    override fun pause() {
        if (!active) return
        wantsPlay = false
        if (transportState == PLAYING) transportState = PAUSED
        send("Pause")
        sink?.onRemotePlaybackChanged()
        changed()
    }

    override fun seekTo(positionMs: Long) {
        if (!active) return
        requestedPositionMs = positionMs.coerceAtLeast(0L)
        requestedAt = SystemClock.elapsedRealtime()
        send("Seek", listOf("Unit" to "REL_TIME", "Target" to Upnp.formatTime(requestedPositionMs)))
        sink?.onRemotePlaybackChanged()
        changed()
    }

    override fun setVolume(fraction: Float) {
        val control = device?.renderingControlUrl ?: return
        val level = (fraction.coerceIn(0f, 1f) * 100).roundToInt()
        volume = level
        sink?.onRemoteVolumeChanged()
        changed()
        DlnaOutput.background {
            Upnp.soap(control, Upnp.RENDERING_CONTROL, "SetVolume", INSTANCE + listOf("Channel" to "Master", "DesiredVolume" to "$level"))
        }
    }

    private fun send(action: String, args: List<Pair<String, String>> = emptyList()) {
        val target = device ?: return
        commandsInFlight.incrementAndGet()
        DlnaOutput.background {
            try {
                Upnp.soap(target.avTransportUrl, Upnp.AV_TRANSPORT, action, INSTANCE + args)
            } finally {
                lastCommandDoneAt = SystemClock.elapsedRealtime()
                commandsInFlight.decrementAndGet()
            }
        }
    }

    /** Transport commands on their way, and when the last one finished: see [onStatus]. */
    private val commandsInFlight = java.util.concurrent.atomic.AtomicInteger()
    @Volatile private var lastCommandDoneAt = 0L

    // ---- the phone's player moved ---------------------------------------------

    override fun onLocalTransition(item: MediaItem?, reason: Int) {
        if (!active || item == null) return
        if (item.mediaId == currentId) {
            // Already there (the renderer moved first and this is the phone catching up), or
            // already being loaded (play() saw the new item first): nothing to send again.
            if (!loading) queueNext()
            return
        }
        load(item, 0L, play = wantsPlay || reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
    }

    override fun onLocalQueueChanged() {
        if (active && !loading) queueNext()
    }

    override fun onLocalRepeatChanged(repeatMode: Int) {
        if (active && !loading) queueNext()
    }

    override fun onLocalStartedPlaying(player: Player) {
        if (!active) return
        player.pause()
        play()
    }

    // ---- loading ------------------------------------------------------------------

    private fun load(item: MediaItem?, positionMs: Long, play: Boolean) {
        val target = device ?: return
        item ?: return
        loadJob?.cancel()
        nextJob?.cancel()
        val gen = ++generation
        wantsPlay = play
        loading = true
        loadSent = false
        loadStartedAt = SystemClock.elapsedRealtime()
        finished = false
        sawPlaying = false
        requestedPositionMs = positionMs
        requestedAt = SystemClock.elapsedRealtime()
        pendingSeekMs = if (positionMs > 1_500) positionMs else 0L
        currentId = item.mediaId
        currentUrl = null
        currentDurationMs = item.mediaMetadata.durationMs ?: 0L
        nextId = null
        nextUrl = null
        statusPositionMs = positionMs
        statusDurationMs = 0L
        sink?.onRemotePlaybackChanged()
        changed()

        loadJob = scope.launch {
            val stream = try {
                resolve(item)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "could not resolve ${item.mediaId}", t)
                null
            }
            if (gen != generation || !active) return@launch
            if (stream == null) {
                loading = false
                wantsPlay = false
                say(R.string.cast_track_unsupported)
                sink?.onRemotePlaybackChanged()
                changed()
                return@launch
            }
            currentUrl = stream.url
            // Whatever the listener wants by now: play() or pause() may have come in while resolving.
            val playNow = wantsPlay
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    Upnp.soap(
                        target.avTransportUrl, Upnp.AV_TRANSPORT, "SetAVTransportURI",
                        INSTANCE + listOf("CurrentURI" to stream.url, "CurrentURIMetaData" to didl(item, stream)),
                    )
                    if (playNow) Upnp.soap(target.avTransportUrl, Upnp.AV_TRANSPORT, "Play", INSTANCE + listOf("Speed" to "1"))
                }
            }
            if (gen != generation || !active) return@launch
            result.onFailure {
                Log.w(TAG, "load failed on ${target.name}", it)
                loading = false
                wantsPlay = false
                say(R.string.cast_track_unsupported)
                sink?.onRemotePlaybackChanged()
                changed()
                return@launch
            }
            loadSent = true
            // play() arrived while the URI was being sent: it left the Play to us
            if (!playNow && wantsPlay) send("Play", listOf("Speed" to "1"))
            // [loading] clears when the renderer reports this track (see onStatus).
            queueNext()
        }
    }

    /** Tells the renderer what follows, so it moves on by itself with no gap. */
    private fun queueNext() {
        val target = device ?: return
        val player = localPlayer() ?: return
        val index = player.nextMediaItemIndex
        val next = if (index != C.INDEX_UNSET) player.getMediaItemAt(index) else null
        if (next == null) {
            nextId = null
            nextUrl = null
            return
        }
        if (next.mediaId == nextId && nextUrl != null) return
        nextJob?.cancel()
        val gen = generation
        nextJob = scope.launch {
            val stream = try {
                resolve(next)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            } ?: return@launch
            if (gen != generation || !active) return@launch
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    Upnp.soap(
                        target.avTransportUrl, Upnp.AV_TRANSPORT, "SetNextAVTransportURI",
                        INSTANCE + listOf("NextURI" to stream.url, "NextURIMetaData" to didl(next, stream)),
                    )
                }
            }
            if (gen != generation || !active) return@launch
            // A renderer without gapless answers 401; it is then moved on when it stops.
            if (result.isSuccess) {
                nextId = next.mediaId
                nextUrl = stream.url
            }
        }
    }

    // ---- the renderer moved -----------------------------------------------------

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            var tick = 0
            while (isActive && active) {
                val target = device ?: break
                val polledAt = SystemClock.elapsedRealtime()
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val transport = Upnp.soap(target.avTransportUrl, Upnp.AV_TRANSPORT, "GetTransportInfo", INSTANCE)
                        val position = Upnp.soap(target.avTransportUrl, Upnp.AV_TRANSPORT, "GetPositionInfo", INSTANCE)
                        transport to position
                    }
                }
                if (!active || device !== target) break
                result.onSuccess { (transport, position) ->
                    failures = 0
                    onStatus(transport["CurrentTransportState"].orEmpty(), position, polledAt)
                }.onFailure {
                    // The renderer went away (switched off, Wi-Fi dropped): after
                    // ~10 s of silence the music comes back to the phone, paused.
                    if (++failures >= LOST_AFTER_FAILURES) {
                        Log.w(TAG, "lost ${target.name}", it)
                        say(R.string.cast_connect_failed)
                        stop(resumeHere = false)
                    }
                }
                if (tick++ % VOLUME_EVERY == 0) refreshVolume()
                delay(if (transportState == PLAYING && !loading) POLL_MS else POLL_FAST_MS)
            }
        }
    }

    private fun onStatus(state: String, position: Map<String, String>, polledAt: Long) {
        if (!active) return
        // Asked before our last Play/Pause reached the renderer: its state is from before that.
        // Taking it would flip the button back for a moment (pause → play icon → pause icon → play icon).
        if (commandsInFlight.get() > 0 || polledAt < lastCommandDoneAt) return
        val uri = position["TrackURI"].orEmpty()
        transportState = state
        statusPositionMs = Upnp.parseTime(position["RelTime"])
        statusDurationMs = Upnp.parseTime(position["TrackDuration"])
        statusAt = SystemClock.elapsedRealtime()
        requestedAt = if (loading) requestedAt else 0L

        // The renderer moved on to the track queued after this one, by itself.
        if (nextUrl != null && uri == nextUrl && uri != currentUrl) {
            currentId = nextId
            currentUrl = nextUrl
            currentDurationMs = 0L
            nextId = null
            nextUrl = null
            loading = false
            sawPlaying = false
            val player = localPlayer()
            val id = currentId
            if (player != null && id != null && player.currentMediaItem?.mediaId != id) {
                // The phone follows; its transition queues the one after.
                indexOf(player, id)?.let { player.seekTo(it, 0L) }
            }
        }

        val ours = uri.isEmpty() || currentUrl == null || uri == currentUrl
        if (loading) {
            // Until our SetAVTransportURI has gone out, the renderer is still reporting the previous
            // track (often paused): that must not settle the load nor overwrite what the listener wants.
            val mine = loadSent && (uri.isEmpty() || uri == currentUrl)
            val settled = mine && (state == PLAYING || state == PAUSED || (state == STOPPED && !wantsPlay))
            if (settled || SystemClock.elapsedRealtime() - loadStartedAt > LOAD_TIMEOUT_MS) loading = false
        }

        if (!loading) {
            if (!ours && state != NO_MEDIA) {
                // Something else took the renderer over — another app cast to it.
                Log.i(TAG, "${device?.name} is playing something else; letting go")
                stop(resumeHere = false)
                return
            }
            when (state) {
                PLAYING -> {
                    wantsPlay = true
                    finished = false
                    sawPlaying = true
                    playedPositionMs = statusPositionMs
                    if (pendingSeekMs > 0) {
                        val target = pendingSeekMs
                        pendingSeekMs = 0L
                        seekTo(target)
                    }
                }
                PAUSED -> wantsPlay = false
                STOPPED, NO_MEDIA -> if (sawPlaying) {
                    sawPlaying = false
                    val duration = durationMs
                    val nearEnd = duration > 0 && playedPositionMs >= duration - END_SLACK_MS
                    if (state == STOPPED && nearEnd) onRendererFinished() else wantsPlay = false
                }
            }
        }
        sink?.onRemotePlaybackChanged()
        changed()
    }

    /** The renderer played to the end of what it held; the phone's queue goes on. */
    private fun onRendererFinished() {
        val player = localPlayer() ?: return
        val index = player.nextMediaItemIndex
        if (index != C.INDEX_UNSET) {
            wantsPlay = true
            player.seekTo(index, 0L) // its transition loads the track here
        } else {
            wantsPlay = false
            finished = true
        }
    }

    private fun refreshVolume() {
        val control = device?.renderingControlUrl ?: return
        scope.launch {
            val level = withContext(Dispatchers.IO) {
                runCatching {
                    Upnp.soap(control, Upnp.RENDERING_CONTROL, "GetVolume", INSTANCE + listOf("Channel" to "Master"))["CurrentVolume"]
                        ?.trim()?.toIntOrNull()
                }.getOrNull()
            } ?: return@launch
            if (active && level != volume) {
                volume = level.coerceIn(0, 100)
                sink?.onRemoteVolumeChanged()
                changed()
            }
        }
    }

    // ---- helpers ------------------------------------------------------------------

    private fun changed() {
        DlnaOutput.onSpeakerChanged(this)
        onChanged()
    }

    /** DIDL-Lite for [item]: what the renderer shows (title, artist, album, artwork) and what the stream is. */
    private fun didl(item: MediaItem, stream: CastStream): String {
        val meta = item.mediaMetadata
        val e = Upnp::escape
        return buildString {
            append("<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" ")
            append("xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">")
            append("<item id=\"${e(item.mediaId)}\" parentID=\"0\" restricted=\"1\">")
            append("<dc:title>${e(meta.title?.toString() ?: "BitChord")}</dc:title>")
            meta.artist?.toString()?.let { append("<upnp:artist>${e(it)}</upnp:artist><dc:creator>${e(it)}</dc:creator>") }
            meta.albumTitle?.toString()?.let { append("<upnp:album>${e(it)}</upnp:album>") }
            meta.artworkUri?.takeIf { it.scheme == "http" || it.scheme == "https" }
                ?.let { append("<upnp:albumArtURI>${e(it.toString())}</upnp:albumArtURI>") }
            append("<upnp:class>object.item.audioItem.musicTrack</upnp:class>")
            val duration = meta.durationMs?.takeIf { it > 0 }?.let { " duration=\"${Upnp.formatTime(it)}.000\"" }.orEmpty()
            append("<res protocolInfo=\"http-get:*:${e(stream.mimeType)}:*\"$duration>${e(stream.url)}</res>")
            append("</item></DIDL-Lite>")
        }
    }

    /** The row of [mediaId] nearest the one playing. */
    private fun indexOf(player: Player, mediaId: String): Int? {
        val at = player.currentMediaItemIndex.coerceAtLeast(0)
        return (0 until player.mediaItemCount)
            .filter { player.getMediaItemAt(it).mediaId == mediaId }
            .minByOrNull { kotlin.math.abs(it - at) }
    }

    companion object {
        private const val TAG = "BitChordDlna"
        private val INSTANCE = listOf("InstanceID" to "0")

        private const val PLAYING = "PLAYING"
        private const val PAUSED = "PAUSED_PLAYBACK"
        private const val STOPPED = "STOPPED"
        private const val TRANSITIONING = "TRANSITIONING"
        private const val NO_MEDIA = "NO_MEDIA_PRESENT"

        private const val POLL_MS = 1_000L
        private const val POLL_FAST_MS = 600L
        private const val VOLUME_EVERY = 5
        private const val LOST_AFTER_FAILURES = 10
        private const val LOAD_TIMEOUT_MS = 20_000L
        private const val SEEK_GRACE_MS = 1_500L
        private const val END_SLACK_MS = 4_000L
    }
}
