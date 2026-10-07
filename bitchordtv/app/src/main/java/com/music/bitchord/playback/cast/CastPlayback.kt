package com.music.bitchord.playback.cast

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueData
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.PendingResult
import com.google.android.gms.common.api.Result
import com.google.android.gms.common.images.WebImage
import com.music.bitchord.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

/** A stream the receiver can open by itself: a URL and what it holds. */
class CastStream(val url: String, val mimeType: String)

/** Told when the receiver changes something the session player reports as its own. */
interface CastSink {
    /** Play state, buffering, or the playhead moved. */
    fun onRemotePlaybackChanged()

    /** The music moved to a receiver, or back. */
    fun onRemoteRouteChanged()

    /** The receiver's volume changed, from here or from its own remote. */
    fun onRemoteVolumeChanged()
}

/**
 * What plays on the receiver while a Cast session is up.
 *
 * The phone's player stays the owner of the *queue* — every row, reorder,
 * autoplay refill, lyric and widget keeps reading it exactly as before — and is
 * held paused while the receiver is the speaker. The receiver is the owner of
 * the *clock*: [positionMs], [isPlaying] and the rest come from it, and
 * the session player reports them in place of the paused local ones, so the
 * app, the lock screen and the notification all show what the receiver is
 * doing. Two things keep the halves in step:
 *
 *  - The phone moving to another track (a skip, a tap in the queue) loads that
 *    track on the receiver.
 *  - The receiver moving to another track (it finished one, or somebody used
 *    its own remote) moves the phone's player to the same one.
 *
 * Which is which is told by comparing media ids rather than by a flag: the
 * player reports its own moves a beat after they happen, so a flag raised
 * around a seek would already be down by the time the report arrives, whereas
 * "the receiver is already on this track" stays true.
 *
 * A receiver cannot ask the service to resolve a `bitchord://` address, so the
 * stream URL is resolved here, on the phone, and the receiver is handed the
 * finished URL. It cannot send the headers a resolver may have wanted either,
 * and it cannot reach a file on this phone at all; those tracks are refused
 * with a message rather than loaded and left silent.
 *
 * The receiver's queue holds the playing track and a few after it, so that it
 * moves on by itself with no gap and with the phone asleep. Everything runs on
 * the main thread.
 */
class CastPlayback(
    private val scope: CoroutineScope,
    private val localPlayer: () -> ExoPlayer?,
    private val resolve: suspend (MediaItem) -> CastStream?,
    private val say: (Int) -> Unit,
) {
    var sink: CastSink? = null

    init {
        scope.launch {
            CastController.volume.drop(1).collect { if (active) sink?.onRemoteVolumeChanged() }
        }
    }

    /** Whether the receiver is the speaker. Everything else here is meaningful only while it is. */
    var active = false
        private set

    /** Set by [CastController.disconnect]: the listener chose this phone, so keep playing on it. */
    var resumeHereOnEnd = false

    private var session: CastSession? = null
    private var client: RemoteMediaClient? = null

    /** The listener wants sound; what [playWhenReady] reports. */
    private var wantsPlay = false

    /** A track is on its way to the receiver and nothing has come back yet. */
    private var loading = false

    /** The receiver ran out of queue. */
    private var finished = false

    /** Where a load or a seek asked the receiver to be, until it says where it is. */
    private var requestedPositionMs = 0L
    private var requestedAt = 0L

    private var loadGeneration = 0
    private var loadJob: Job? = null
    private var reconcileJob: Job? = null
    private var reconcileAgain = false

    private var lastItemId = MediaQueueItem.INVALID_ITEM_ID

    /** What the receiver was doing when its session started to end. */
    private var endPositionMs = 0L
    private var endMediaId: String? = null
    private var endWasPlaying = false

    val isPlaying: Boolean
        get() = active && !loading && client?.mediaStatus?.playerState == MediaStatus.PLAYER_STATE_PLAYING

    val playWhenReady: Boolean get() = wantsPlay

    /** In [Player]'s own terms, so the session player can answer with it directly. */
    val playbackState: Int
        get() = when {
            loading -> Player.STATE_BUFFERING
            finished -> Player.STATE_ENDED
            else -> when (client?.mediaStatus?.playerState) {
                MediaStatus.PLAYER_STATE_BUFFERING, MediaStatus.PLAYER_STATE_LOADING -> Player.STATE_BUFFERING
                else -> Player.STATE_READY
            }
        }

    val positionMs: Long
        get() {
            val live = client?.takeIf { !loading }?.approximateStreamPosition
            val asked = requestedPositionMs
            // A seek is asked of the receiver and answered a moment later; in
            // between, its own clock still reads the old place and the bar would
            // spring back to it.
            return if (live == null || SystemClock.elapsedRealtime() - requestedAt < SEEK_GRACE_MS) {
                asked
            } else {
                live.coerceAtLeast(0L)
            }
        }

    val durationMs: Long
        get() = client?.streamDuration?.takeIf { it > 0 } ?: C.TIME_UNSET

    /** The receiver's own volume as the whole number 0..100 a [Player] device expects. */
    val deviceVolume: Int get() = (CastController.volume.value * 100f).toInt()

    fun play() {
        if (!active) return
        wantsPlay = true
        finished = false
        client?.play()
        sink?.onRemotePlaybackChanged()
    }

    fun pause() {
        if (!active) return
        wantsPlay = false
        client?.pause()
        sink?.onRemotePlaybackChanged()
    }

    fun seekTo(positionMs: Long) {
        val client = client ?: return
        if (!active) return
        requestedPositionMs = positionMs.coerceAtLeast(0L)
        requestedAt = SystemClock.elapsedRealtime()
        client.seek(MediaSeekOptions.Builder().setPosition(requestedPositionMs).build())
        sink?.onRemotePlaybackChanged()
    }

    // ---- session lifecycle --------------------------------------------------

    private val callback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = onStatus()
    }

    fun onSessionBegan(newSession: CastSession, resumed: Boolean) {
        val remote = newSession.remoteMediaClient ?: return
        if (active && remote === client) return
        release()
        session = newSession
        client = remote
        remote.registerCallback(callback)
        active = true
        finished = false

        val player = localPlayer()
        if (player != null) {
            val wasPlaying = player.playWhenReady && player.playbackState != Player.STATE_ENDED
            val position = player.currentPosition.coerceAtLeast(0L)
            val currentId = player.currentMediaItem?.mediaId
            // The phone goes quiet first, whatever happens next, so the two
            // are never both audible.
            player.pause()
            val onReceiver = remote.mediaStatus?.queueItems
                ?.firstOrNull { it.itemId == remote.mediaStatus?.currentItemId }
                ?.mediaId()
            if (resumed && currentId != null && currentId == onReceiver) {
                // A session that survived the app: the receiver is already on
                // this track, so it keeps going instead of being restarted.
                lastItemId = remote.mediaStatus?.currentItemId ?: MediaQueueItem.INVALID_ITEM_ID
                wantsPlay = remote.mediaStatus?.playerState == MediaStatus.PLAYER_STATE_PLAYING
                scheduleReconcile()
            } else {
                load(player.currentMediaItem, position, play = wasPlaying)
            }
        }
        sink?.onRemoteRouteChanged()
        sink?.onRemotePlaybackChanged()
    }

    fun onSessionEnding(ending: CastSession) {
        val remote = ending.remoteMediaClient ?: client ?: return
        endPositionMs = remote.approximateStreamPosition.coerceAtLeast(0L)
        endMediaId = remote.mediaStatus?.let { status ->
            status.queueItems.firstOrNull { it.itemId == status.currentItemId }?.mediaId()
        }
        endWasPlaying = wantsPlay && remote.mediaStatus?.playerState == MediaStatus.PLAYER_STATE_PLAYING
    }

    /**
     * The receiver let go. Its place is carried back to the phone's player so
     * that the track, and the second in it, are still there. Whether sound
     * follows depends on who asked: a listener who picked "this phone" wants it
     * to go on playing here; a receiver that was switched off, or a Wi-Fi that
     * dropped, should not have the phone start playing out loud unprompted.
     */
    fun onSessionEnded(error: Int) {
        if (!active) return
        val resume = resumeHereOnEnd && endWasPlaying
        resumeHereOnEnd = false
        release()
        val player = localPlayer()
        if (player != null) {
            val index = indexOfMediaId(player, endMediaId)
            if (index != null) player.seekTo(index, endPositionMs) else player.seekTo(endPositionMs)
            if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) player.prepare()
            if (resume) player.play()
        }
        Log.i(TAG, "session ended: error=$error resumeHere=$resume")
        sink?.onRemoteRouteChanged()
        sink?.onRemotePlaybackChanged()
    }

    fun onConnectFailed() = say(R.string.cast_connect_failed)

    /** Lets go of the receiver without touching the phone's player. */
    fun release() {
        loadJob?.cancel()
        reconcileJob?.cancel()
        client?.unregisterCallback(callback)
        client = null
        session = null
        active = false
        loading = false
        wantsPlay = false
        finished = false
        lastItemId = MediaQueueItem.INVALID_ITEM_ID
    }

    // ---- the phone's player moved -------------------------------------------

    /** A new track became the phone's current one. */
    fun onLocalTransition(item: MediaItem?, reason: Int) {
        if (!active || item == null) return
        val remote = client ?: return
        if (item.mediaId == remoteMediaId()) {
            // Already there: the receiver moved first and this is the phone
            // catching up, or a queue edit that left the playing track alone.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) scheduleReconcile()
            return
        }
        val queued = remote.mediaStatus?.queueItems?.firstOrNull { it.mediaId() == item.mediaId }
        if (queued != null && !loading) {
            // A track the receiver already holds is a jump, not a reload, and
            // the jump is instant.
            wantsPlay = true
            finished = false
            requestedPositionMs = 0L
            requestedAt = SystemClock.elapsedRealtime()
            remote.queueJumpToItem(queued.itemId, null)
            sink?.onRemotePlaybackChanged()
        } else {
            load(item, 0L, play = true)
        }
    }

    /** The phone's queue changed under the playing track. */
    fun onLocalQueueChanged() = scheduleReconcile()

    fun onLocalRepeatChanged(repeatMode: Int) {
        if (!active) return
        client?.queueSetRepeatMode(receiverRepeat(repeatMode), null)
    }

    /**
     * Something other than the session player started the phone's own player
     * while the receiver is the speaker. The phone goes quiet again and the
     * request is made of the receiver instead.
     */
    fun onLocalStartedPlaying(player: Player) {
        if (!active) return
        player.pause()
        play()
    }

    // ---- the receiver moved -------------------------------------------------

    private fun onStatus() {
        val remote = client ?: return
        val status = remote.mediaStatus ?: return
        val state = status.playerState
        // Whatever the phone last asked for has been answered by now.
        requestedAt = 0L

        when (state) {
            MediaStatus.PLAYER_STATE_PLAYING -> {
                wantsPlay = true
                loading = false
                finished = false
            }
            MediaStatus.PLAYER_STATE_PAUSED -> {
                wantsPlay = false
                loading = false
            }
            MediaStatus.PLAYER_STATE_IDLE -> {
                if (!loading && status.idleReason == MediaStatus.IDLE_REASON_FINISHED) {
                    wantsPlay = false
                    onReceiverFinished()
                } else if (status.idleReason == MediaStatus.IDLE_REASON_ERROR) {
                    wantsPlay = false
                    loading = false
                    say(R.string.cast_track_unsupported)
                }
            }
        }

        val itemId = status.currentItemId
        if (itemId != MediaQueueItem.INVALID_ITEM_ID && itemId != lastItemId) {
            lastItemId = itemId
            val mediaId = status.getQueueItemById(itemId)?.mediaId()
            val player = localPlayer()
            if (mediaId != null && player != null && player.currentMediaItem?.mediaId != mediaId) {
                indexOfMediaId(player, mediaId)?.let { player.seekTo(it, 0L) }
            }
            scheduleReconcile()
        }
        sink?.onRemotePlaybackChanged()
    }

    /** The receiver played its last queued track to the end. */
    private fun onReceiverFinished() {
        val player = localPlayer() ?: return
        when {
            // The receiver holds only a few tracks ahead, so running out of
            // them is not the end of the phone's queue.
            player.hasNextMediaItem() -> player.seekToNextMediaItem()
            player.repeatMode == Player.REPEAT_MODE_ALL && player.mediaItemCount > 0 -> player.seekTo(0, 0L)
            else -> finished = true
        }
    }

    // ---- loading ------------------------------------------------------------

    /**
     * Puts [item] on the receiver, replacing its queue, and then lets
     * [reconcile] fill in what follows. Only the playing track is resolved
     * before the receiver is asked to start: the rest are resolved while it
     * plays, so a cast begins as quickly as a single stream resolves.
     */
    private fun load(item: MediaItem?, positionMs: Long, play: Boolean) {
        val remote = client ?: return
        loadJob?.cancel()
        val generation = ++loadGeneration
        wantsPlay = play
        loading = true
        finished = false
        requestedPositionMs = positionMs
        requestedAt = SystemClock.elapsedRealtime()
        sink?.onRemotePlaybackChanged()

        loadJob = scope.launch {
            val queueItem = item?.let { buildQueueItem(it, play) }
            if (generation != loadGeneration || !active) return@launch
            if (queueItem == null) {
                // Nothing to play, so nothing to wait for. The receiver is left
                // as it was; only the phone's idea of it is corrected.
                loading = false
                wantsPlay = false
                if (item != null) say(R.string.cast_track_unsupported)
                sink?.onRemotePlaybackChanged()
                return@launch
            }
            val repeat = receiverRepeat(localPlayer()?.repeatMode ?: Player.REPEAT_MODE_OFF)
            val request = MediaLoadRequestData.Builder()
                .setQueueData(
                    MediaQueueData.Builder()
                        .setItems(listOf(queueItem))
                        .setStartIndex(0)
                        .setRepeatMode(repeat)
                        .setStartTime(positionMs)
                        .build(),
                )
                .setAutoplay(play)
                .setCurrentTime(positionMs)
                .build()
            val result = remote.load(request).await()
            if (generation != loadGeneration || !active) return@launch
            if (!result.status.isSuccess) {
                Log.w(TAG, "load failed: ${result.status.statusCode}")
                loading = false
                wantsPlay = false
                say(R.string.cast_track_unsupported)
                sink?.onRemotePlaybackChanged()
                return@launch
            }
            lastItemId = remote.mediaStatus?.currentItemId ?: lastItemId
            loading = false
            sink?.onRemotePlaybackChanged()
            scheduleReconcile()
        }
    }

    private fun scheduleReconcile() {
        if (!active) return
        if (reconcileJob?.isActive == true) {
            reconcileAgain = true
            return
        }
        reconcileJob = scope.launch {
            do {
                reconcileAgain = false
                // Let the receiver's own status catch up with whatever just
                // changed it; reading its queue earlier reads the old one.
                delay(RECONCILE_DELAY_MS)
                if (active && !loading) reconcileOnce()
            } while (reconcileAgain && active)
        }
    }

    /**
     * Makes what the receiver has queued after the playing track match what the
     * phone's queue says comes next — [AHEAD] tracks of it. The matching
     * prefix is kept (it may already be buffering); the first track that
     * differs and everything after it is replaced.
     */
    private suspend fun reconcileOnce() {
        val remote = client ?: return
        val player = localPlayer() ?: return
        val status = remote.mediaStatus ?: return
        val queue = status.queueItems
        val here = queue.indexOfFirst { it.itemId == status.currentItemId }
        if (here < 0) return
        val remoteAhead = queue.drop(here + 1)

        val current = player.currentMediaItemIndex
        if (current == C.INDEX_UNSET) return
        val wanted = ((current + 1)..minOf(current + AHEAD, player.mediaItemCount - 1))
            .map { player.getMediaItemAt(it) }

        var kept = 0
        while (kept < remoteAhead.size && kept < wanted.size && remoteAhead[kept].mediaId() == wanted[kept].mediaId) {
            kept++
        }
        if (kept == remoteAhead.size && kept == wanted.size) return

        if (kept < remoteAhead.size) {
            remote.queueRemoveItems(remoteAhead.drop(kept).map { it.itemId }.toIntArray(), null).await()
        }
        for (next in wanted.drop(kept)) {
            val queueItem = buildQueueItem(next, autoplay = true) ?: break
            if (!active) return
            remote.queueInsertItems(arrayOf(queueItem), MediaQueueItem.INVALID_ITEM_ID, null).await()
        }
    }

    private suspend fun buildQueueItem(item: MediaItem, autoplay: Boolean): MediaQueueItem? {
        val stream = try {
            resolve(item)
        } catch (e: Exception) {
            Log.w(TAG, "could not resolve ${item.mediaId} for casting", e)
            null
        } ?: return null

        val metadata = item.mediaMetadata
        val music = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            metadata.title?.let { putString(MediaMetadata.KEY_TITLE, it.toString()) }
            metadata.artist?.let { putString(MediaMetadata.KEY_ARTIST, it.toString()) }
            metadata.albumTitle?.let { putString(MediaMetadata.KEY_ALBUM_TITLE, it.toString()) }
            metadata.artworkUri?.takeIf { it.scheme == "http" || it.scheme == "https" }
                ?.let { addImage(WebImage(it)) }
        }
        val info = MediaInfo.Builder(stream.url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(stream.mimeType)
            .setMetadata(music)
            .setCustomData(JSONObject().put(KEY_MEDIA_ID, item.mediaId))
            .apply { metadata.durationMs?.takeIf { it > 0 }?.let(::setStreamDuration) }
            .build()
        return MediaQueueItem.Builder(info).setAutoplay(autoplay).build()
    }

    // ---- small helpers ------------------------------------------------------

    private fun MediaQueueItem.mediaId(): String? = media?.customData?.optString(KEY_MEDIA_ID)?.ifEmpty { null }

    private fun remoteMediaId(): String? {
        val status = client?.mediaStatus ?: return null
        return status.getQueueItemById(status.currentItemId)?.mediaId()
    }

    /**
     * The row of [mediaId] nearest the one playing — a track queued twice is
     * the case where more than one answers, and the near one is the one meant.
     */
    private fun indexOfMediaId(player: Player, mediaId: String?): Int? {
        if (mediaId == null) return null
        val at = player.currentMediaItemIndex.coerceAtLeast(0)
        return (0 until player.mediaItemCount)
            .filter { player.getMediaItemAt(it).mediaId == mediaId }
            .minByOrNull { kotlin.math.abs(it - at) }
    }

    private fun receiverRepeat(repeatMode: Int): Int =
        if (repeatMode == Player.REPEAT_MODE_ONE) {
            MediaStatus.REPEAT_MODE_REPEAT_SINGLE
        } else {
            // Repeat-all is the phone's to do: the receiver holds a window of
            // the queue, and looping the window is not looping the queue.
            MediaStatus.REPEAT_MODE_REPEAT_OFF
        }

    private suspend fun <R : Result> PendingResult<R>.await(): R =
        suspendCancellableCoroutine { continuation ->
            setResultCallback { continuation.resume(it) }
            continuation.invokeOnCancellation { cancel() }
        }

    companion object {
        private const val TAG = "BitChordCast"
        private const val KEY_MEDIA_ID = "mediaId"

        /** Tracks kept queued on the receiver after the playing one. */
        private const val AHEAD = 3
        private const val RECONCILE_DELAY_MS = 300L
        private const val SEEK_GRACE_MS = 1_500L

        /**
         * What a stream is, for a receiver that is told rather than left to
         * guess. googlevideo URLs say so themselves; the rest are named by
         * their extension, and an unnamed one is taken to be MP4 audio, which
         * is what nearly every unnamed stream here is.
         */
        fun mimeTypeOf(uri: Uri): String {
            uri.getQueryParameter("mime")?.takeIf { it.isNotBlank() }?.let { return it }
            val path = uri.lastPathSegment?.lowercase().orEmpty()
            return when {
                path.endsWith(".flac") -> "audio/flac"
                path.endsWith(".mp3") -> "audio/mpeg"
                path.endsWith(".ogg") || path.endsWith(".opus") -> "audio/ogg"
                path.endsWith(".wav") -> "audio/wav"
                path.endsWith(".mpd") -> "application/dash+xml"
                path.endsWith(".m3u8") -> "application/x-mpegurl"
                else -> "audio/mp4"
            }
        }
    }
}
