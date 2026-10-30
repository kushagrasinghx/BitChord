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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
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

/**
 * What came of preparing one track for a receiver.
 *
 * Refused, Stalled and Failed all look the same from outside the receiver —
 * nothing new is playing — and want different things done about them. A refused
 * track cannot be cast at all, so it is skipped and the listener is told. A
 * stalled or failed one probably can; it was slow, or the resolver came back
 * empty, so it is neither skipped nor called uncastable. The session is ended
 * honestly instead: the receiver is stopped, the phone is told it is not
 * playing, and the next play takes the track again. Saying "this track can't be
 * cast" about a slow network is a lie the listener will believe.
 */
private sealed interface Prepared {
    data class Ready(val item: MediaQueueItem) : Prepared
    data object Refused : Prepared
    data object Stalled : Prepared
    data object Failed : Prepared
}

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
) : RemoteSpeaker {
    override var sink: CastSink? = null

    init {
        scope.launch {
            CastController.volume.drop(1).collect { if (active) sink?.onRemoteVolumeChanged() }
        }
    }

    /** Whether the receiver is the speaker. Everything else here is meaningful only while it is. */
    override var active = false
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

    /**
     * Where the receiver was at the last status, and when we heard it. Its
     * status arrives about every three seconds, so reporting that reading as
     * the position makes the progress bar step in three-second jumps; carrying
     * it forward from our own clock keeps it moving between updates.
     */
    private var anchorPositionMs = 0L
    private var anchorAt = 0L

    private var loadGeneration = 0
    private var loadJob: Job? = null
    private var reconcileJob: Job? = null
    private var reconcileAgain = false

    private var lastItemId = MediaQueueItem.INVALID_ITEM_ID

    /**
     * The receiver item the phone had *before* handing it a new track, for as
     * long as it has not yet moved off it.
     *
     * In that window every position the receiver reports belongs to the track it
     * was playing before, and reporting them is what made the bar leap to a
     * previous track's place the moment a receiver was picked. The change of item
     * is what confirms the handover, and it has to be the item that was there
     * before, since the new one cannot be known until the receiver says so.
     *
     * Null when no handover is outstanding. [MediaQueueItem.INVALID_ITEM_ID] is a
     * value here, not the absence of one: on a first connect there is no previous
     * track, and every reading is of something that is about to be replaced — but
     * "nothing to move off" and "nothing to wait for" are not the same thing, and
     * conflating them left the first connection of a session unguarded, which is
     * exactly when the bar jumped.
     */
    private var awaitingSwitchFrom: Int? = null

    /** What the receiver was doing when its session started to end. */
    private var endPositionMs = 0L
    private var endMediaId: String? = null
    private var endWasPlaying = false

    override val isPlaying: Boolean
        get() = active && !loading && !finished &&
            client?.mediaStatus?.playerState == MediaStatus.PLAYER_STATE_PLAYING

    override val playWhenReady: Boolean get() = wantsPlay

    /** In [Player]'s own terms, so the session player can answer with it directly. */
    override val playbackState: Int
        get() = when {
            loading -> Player.STATE_BUFFERING
            finished -> Player.STATE_ENDED
            else -> when (client?.mediaStatus?.playerState) {
                MediaStatus.PLAYER_STATE_BUFFERING, MediaStatus.PLAYER_STATE_LOADING -> Player.STATE_BUFFERING
                else -> Player.STATE_READY
            }
        }

    override val positionMs: Long
        get() {
            val value = computePosition()
            if (TRACE_CAST) {
                if (positionReads++ % 4L == 0L) {
                    Log.i(
                        TAG,
                        "[pos] $value | asked=$requestedPositionMs " +
                            "live=${client?.takeIf { !loading }?.approximateStreamPosition} " +
                            "anchor=$anchorPositionMs@$anchorAt | " +
                            "play=${isPlaying} ready=$playWhenReady loading=$loading switch=$awaitingSwitchFrom",
                    )
                }
            }
            return value
        }

    private var positionReads = 0L

    private fun computePosition(): Long {
        val now = SystemClock.elapsedRealtime()
        return castPositionMs(
            CastPositionInputs(
                liveMs = client?.takeIf { !loading }?.approximateStreamPosition,
                askedMs = requestedPositionMs,
                requestedAtMs = requestedAt,
                nowMs = now,
                handoverOutstanding = awaitingSwitchFrom != null,
                switchTimeoutMs = SWITCH_TIMEOUT_MS,
                seekGraceMs = SEEK_GRACE_MS,
                playing = isPlaying,
                anchorPositionMs = anchorPositionMs,
                anchorAtMs = anchorAt,
                durationMs = durationMs,
            ),
        )
    }

    override val durationMs: Long
        get() = client?.streamDuration?.takeIf { it > 0 } ?: C.TIME_UNSET

    /** The receiver's own volume as the whole number 0..100 a [Player] device expects. */
    override val deviceVolume: Int get() = (CastController.volume.value * 100f).toInt()

    override fun setVolume(fraction: Float) = CastController.setVolume(fraction)

    override fun play() {
        if (!active) return
        wantsPlay = true
        finished = false
        val player = localPlayer()
        val item = player?.currentMediaItem
        val status = client?.mediaStatus
        val receiverId = status?.getQueueItemById(status.currentItemId)?.mediaId()
        // A receiver that was stopped is idle, and a play command to an idle
        // receiver goes nowhere (it keeps its title and does nothing). So does
        // one that is on a track the phone has left behind. Both are the way
        // back from "nothing left that it could play" — see [skipToCastable] —
        // and both want the track handed over again rather than a command that
        // is silently dropped.
        if (player != null && item != null && status != null &&
            (status.playerState == MediaStatus.PLAYER_STATE_IDLE || receiverId != item.mediaId)
        ) {
            load(item, player.currentPosition.coerceAtLeast(0L), play = true)
        } else {
            client?.play()
        }
        sink?.onRemotePlaybackChanged()
    }

    override fun pause() {
        if (!active) return
        wantsPlay = false
        client?.pause()
        sink?.onRemotePlaybackChanged()
    }

    /**
     * A seek is the receiver's to carry out while it is playing. After the
     * session has been ended — nothing castable left, or a load failed — the
     * receiver is idle, and a seek command to an idle receiver goes nowhere.
     * The requested position is still recorded, so the bar shows where the
     * listener put it, but only [play] starts the receiver again: it hands the
     * track over once more, and that load starts from the phone's own position.
     * Seeking alone does not resume a stopped session.
     */
    override fun seekTo(positionMs: Long) {
        val client = client ?: return
        if (!active) return
        requestedPositionMs = positionMs.coerceAtLeast(0L)
        requestedAt = SystemClock.elapsedRealtime()
        client.seek(MediaSeekOptions.Builder().setPosition(requestedPositionMs).build())
        sink?.onRemotePlaybackChanged()
    }

    /**
     * Back, on a receiver that still holds the track before this one: a jump
     * rather than a reload, and so without the gap a reload costs. False when
     * the receiver has not got it queued — at the top of the queue, or before
     * the window has caught up — and the caller should move the phone instead.
     */
    override fun jumpToPrevious(): Boolean {
        if (!active) return false
        val remote = client ?: return false
        val status = remote.mediaStatus ?: return false
        val here = status.queueItems.indexOfFirst { it.itemId == status.currentItemId }
        if (here <= 0) {
            trace("back", "no previous item on the receiver's queue (index=$here); let the phone move instead")
            return false
        }
        val behind = status.queueItems[here - 1]
        trace("back", "jumping straight to ${behind.mediaId()} (itemId=${behind.itemId}), without reloading")
        wantsPlay = true
        finished = false
        requestedPositionMs = 0L
        requestedAt = SystemClock.elapsedRealtime()
        remote.queueJumpToItem(behind.itemId, null)
        sink?.onRemotePlaybackChanged()
        return true
    }

    // ---- session lifecycle --------------------------------------------------

    private val callback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = onStatus()
    }

    fun onSessionBegan(newSession: CastSession, resumed: Boolean) {
        try {
            beginSession(newSession, resumed)
        } catch (t: Throwable) {
            // This runs inside the framework's callback; an exception here ends
            // the app. A receiver that could not be set up is a failed cast.
            Log.e(TAG, "onSessionBegan threw", t)
            release()
            say(R.string.cast_connect_failed)
        }
    }

    private fun beginSession(newSession: CastSession, resumed: Boolean) {
        val remote = newSession.remoteMediaClient ?: return
        if (active && remote === client) return
        release()
        session = newSession
        client = remote
        remote.registerCallback(callback)
        active = true
        finished = false
        // Take the clock from where the receiver says it is, before anything can
        // ask. Without this the bar has no anchor until the first status arrives,
        // and reporting a position from no anchor at all is worse than reporting
        // the receiver's own.
        anchorTo(remote)

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
        anchorPositionMs = 0L
        anchorAt = 0L
        awaitingSwitchFrom = null
        lastItemId = MediaQueueItem.INVALID_ITEM_ID
    }

    // ---- the phone's player moved -------------------------------------------

    /** A new track became the phone's current one. */
    override fun onLocalTransition(item: MediaItem?, reason: Int) {
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
    override fun onLocalQueueChanged() = scheduleReconcile()

    override fun onLocalRepeatChanged(repeatMode: Int) {
        if (!active) return
        client?.queueSetRepeatMode(receiverRepeat(repeatMode), null)
    }

    /**
     * Something other than the session player started the phone's own player
     * while the receiver is the speaker. The phone goes quiet again and the
     * request is made of the receiver instead.
     */
    override fun onLocalStartedPlaying(player: Player) {
        if (!active) return
        player.pause()
        play()
    }

    // ---- the receiver moved -------------------------------------------------

    /**
     * Takes the receiver's clock as the anchor the position is carried forward
     * from. Zero is left to mean "never anchored", which [positionMs] refuses to
     * extrapolate from — measuring from zero would be measuring from the phone's
     * boot.
     */
    private fun anchorTo(remote: RemoteMediaClient) {
        anchorPositionMs = remote.approximateStreamPosition.coerceAtLeast(0L)
        anchorAt = SystemClock.elapsedRealtime()
    }

    private fun onStatus() {
        val remote = client ?: return
        val status = remote.mediaStatus ?: return
        val state = status.playerState
        // The receiver has moved off the track it was on before: that is the
        // handover. Until it does, a status is news about the old one, and
        // clearing the grace on it let the bar report a place on a track that was
        // no longer playing.
        val handover = awaitingSwitchFrom
        if (handover != null &&
            status.currentItemId != MediaQueueItem.INVALID_ITEM_ID &&
            status.currentItemId != handover
        ) {
            awaitingSwitchFrom = null
        }
        // Whatever the phone last asked for has been answered by now — but not
        // while the receiver is still on the track it was on before this load,
        // because that status is news about the old one, not about where we asked
        // it to begin.
        if (awaitingSwitchFrom == null) requestedAt = 0L
        // Re-anchor the clock: this is the freshest word on where the receiver is.
        anchorTo(remote)
        trace(
            "status",
            "queue=${status.queueItems.size} " +
                status.queueItems.mapIndexed { i, q ->
                    val mark = if (q.itemId == status.currentItemId) ">>" else "  "
                    "$mark$i:${q.mediaId() ?: "?"}"
                }.joinToString(" | ") + "  state=$state",
        )

        when (state) {
            MediaStatus.PLAYER_STATE_PLAYING -> {
                loading = false
                // "Playing" is only news about this session while it is about
                // the track the phone is on. Told to stop — because nothing it
                // could play was left, or because a load failed — a receiver
                // keeps reporting PLAYING for the track it was already on, and
                // taking that as a resume put the bar back on a track the phone
                // had left behind: the app said the uncastable track was playing
                // while the speaker was still on the previous one.
                val phoneId = localPlayer()?.currentMediaItem?.mediaId
                val receiverId = status.getQueueItemById(status.currentItemId)?.mediaId()
                if (adoptsReceiverPlaying(finished, phoneId, receiverId)) {
                    wantsPlay = true
                    finished = false
                }
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
            // The receiver has moved to a different track. Its position for the
            // new one is reported before it has counted anywhere into it, so
            // anchoring to that reading puts the bar back at the start of a track
            // that was asked to begin further in. Where we asked it to begin is
            // the better anchor, and it is this same reading that is stale.
            if (status.playerState == MediaStatus.PLAYER_STATE_PLAYING &&
                anchorPositionMs == 0L && requestedPositionMs > 0L
            ) {
                anchorPositionMs = requestedPositionMs
            }
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

        trace("load", "${item?.mediaId} at ${positionMs}ms (play=$play)")
        loadJob = scope.launch {
            try {
                loadNow(item, positionMs, play, generation, remote)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "load threw", t)
                // A load that threw leaves the receiver's state unknown. Only
                // the load that still owns the generation may correct it; a
                // newer one has already taken over.
                if (generation == loadGeneration && active) {
                    endSession(R.string.cast_connect_failed)
                }
            }
        }
    }

    private suspend fun loadNow(item: MediaItem?, positionMs: Long, play: Boolean, generation: Int, remote: RemoteMediaClient) {
        run {
            // Resolving a stream can involve the network, and a receiver that
            // does not answer can go quiet indefinitely. Without a ceiling here
            // the session sat in BUFFERING for minutes with nothing said to
            // anyone: no progress, no error, no way back short of leaving.
            val prepared = item?.let { timedBuild(it, play) }
            if (generation != loadGeneration || !active) return@run
            if (prepared !is Prepared.Ready) {
                // Refused is the only outcome whose cause is known without the
                // network: the scheme says a receiver cannot open this track.
                // A stall or an empty resolver answer might play on the next
                // try, and is not called uncastable — but the session still has
                // to stop claiming the receiver, so it ends honestly instead.
                if (prepared is Prepared.Refused) {
                    loading = false
                    trace("load", "${item.mediaId} refused by the receiver")
                    say(R.string.cast_track_unsupported)
                    // Carrying on — rather than going quiet and waiting for the
                    // listener to notice — is the whole difference between a
                    // track the receiver cannot take and a session that stops
                    // there.
                    //
                    // Started here but not awaited: moving the phone's player
                    // re-enters this function, which cancels the coroutine doing
                    // the asking. Run from inside it, this cancelled the caller
                    // halfway and the receiver was left playing the track before.
                    sink?.onRemotePlaybackChanged()
                    scope.launch { skipToCastable(generation) }
                } else {
                    // Slow, or the resolver came back empty. Neither says the
                    // track cannot be cast, so nothing is claimed: the session
                    // ends, the receiver stops, and pressing play takes the
                    // track again.
                    trace("load", "${item?.mediaId} could not be prepared; ending the session")
                    endSession()
                }
                return@run
            }
            val queueItem = prepared.item
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
            val result = remote.load(request).awaitResultOrNull("load")
            if (generation != loadGeneration || !active) return@run
            if (result == null) {
                // The receiver never answered. It may still be on the track
                // before, so it is stopped rather than left for its next
                // PLAYING status to be read as this one playing.
                Log.w(TAG, "load did not answer; stopping the receiver")
                endSession(R.string.cast_connect_failed)
                return@run
            }
            if (!result.status.isSuccess) {
                Log.w(TAG, "load failed: ${result.status.statusCode}")
                // A rejected load is a real refusal of this track by the
                // receiver. It is not skipped, because the next track may be
                // fine but the listener did ask for this one; they are told and
                // the session stops in a state that is true.
                endSession(R.string.cast_track_unsupported)
                return@run
            }
            trace("load", "receiver accepted ${item.mediaId}")
            // Read before the line below overwrites it. What the receiver was on
            // before this load is the one to wait to see it move off; taking it
            // afterwards captures the track it is already being told about, and
            // the handover can then never be confirmed.
            awaitingSwitchFrom = lastItemId
            lastItemId = remote.mediaStatus?.currentItemId ?: lastItemId
            // The grace starts now, not when the load was asked for. Resolving a
            // stream and getting the receiver to take it takes seconds, and by
            // then the window in which we answer with the position we asked for
            // instead of the receiver's has long closed — so the bar snapped to
            // whatever the receiver was on before this load, which is the track
            // it was playing when the phone turned up.
            requestedAt = SystemClock.elapsedRealtime()
            loading = false
            sink?.onRemotePlaybackChanged()
            scheduleReconcile()
        }
    }

    /**
     * Moves the phone's player past whatever the receiver cannot take, so a
     * track it has no use for does not end the session. Walks forward to the
     * first track a receiver could play; the phone's queue is the authority on
     * what comes next, and this is it honouring that for the receiver's sake.
     *
     * When there is no such track the receiver is told to stop, not merely
     * marked finished here: left alone it goes on playing the track before this
     * one, and its own PLAYING status then says a track the phone has already
     * left behind is playing. [generation] is the load this skip belongs to, so
     * a listener who picks another track while this runs is not overruled by a
     * stop meant for the track they left.
     */
    private fun skipToCastable(generation: Int) {
        val player = localPlayer() ?: return
        if (generation != loadGeneration || !active) return
        val next = nextUsableIndex(
            queueItemsOf(player),
            player.currentMediaItemIndex.coerceAtLeast(0),
            ::castable,
        )
        if (next != null) {
            player.seekTo(next, 0L)
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            return
        }
        // Nothing further is castable: the receiver is not going to play anything
        // more, and saying so is better than reporting it as playing.
        if (generation != loadGeneration || !active) return
        endSession()
    }

    /**
     * Ends this session in a state that tells the truth about the receiver.
     *
     * Every failure after a load was asked for used to leave `loading` false
     * with the receiver still on the track before: its next PLAYING status was
     * then read as this session playing the new track, which is the ghost C6
     * fixed for a refusal, reappearing through a stall, a timeout or a rejected
     * load. Stopping the receiver makes the state known — it goes idle, keeps
     * the connection — and `finished` keeps `isPlaying` and the MediaSession
     * from claiming otherwise until the listener starts something again.
     *
     * [message] is only for a failure the listener should hear about; a skip
     * that ran out of castable tracks has already said its piece.
     */
    private fun endSession(message: Int? = null) {
        if (!active) return
        finished = true
        wantsPlay = false
        loading = false
        try {
            client?.stop()
        } catch (t: Throwable) {
            // A receiver that has gone away is already stopped.
            Log.w(TAG, "could not stop the receiver", t)
        }
        if (message != null) say(message)
        sink?.onRemotePlaybackChanged()
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
                if (active && !loading) {
                    try {
                        reconcileOnce()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        Log.e(TAG, "reconcile threw", t)
                    }
                }
            } while (reconcileAgain && active)
        }
    }

    /**
     * [buildQueueItem] under a ceiling, saying which way it came back empty.
     * Resolving reaches the network and can stall; a track a receiver cannot
     * open is refused outright and costs nothing. Logging the difference is what
     * tells the two apart — an uncastable track and a stalled one look identical
     * from the outside, and the first is fixed by skipping the track while the
     * second must not be called uncastable.
     */
    private suspend fun timedBuild(item: MediaItem, autoplay: Boolean): Prepared {
        if (!castable(item)) {
            Log.i(TAG, "not castable: ${item.mediaId} (${item.localConfiguration?.uri?.scheme})")
            return Prepared.Refused
        }
        val started = SystemClock.elapsedRealtime()
        // withTimeoutOrNull answers null rather than throwing, so no catch for
        // TimeoutCancellationException is reachable here and none is needed. It
        // does cancel whatever it was waiting on, which is what is wanted here:
        // a resolver still in flight when the listener has moved on has no
        // business arriving later.
        val built = withTimeoutOrNull(LOAD_TIMEOUT_MS) { buildQueueItem(item, autoplay) }
        val took = SystemClock.elapsedRealtime() - started
        return when {
            built != null -> Prepared.Ready(built)
            took >= LOAD_TIMEOUT_MS -> {
                Log.w(TAG, "resolve stalled for ${item.mediaId} after ${took}ms")
                Prepared.Stalled
            }
            else -> {
                Log.w(TAG, "resolve gave nothing for ${item.mediaId} after ${took}ms")
                Prepared.Failed
            }
        }
    }

    /**
     * Makes what the receiver has queued match the phone's queue around the
     * playing track — the track before it and [AHEAD] of it after. The matching
     * prefix is kept (it may already be buffering); the first track that
     * differs and everything after it is replaced.
     *
     * The window is kept this small on purpose. Reordering the phone's queue
     * moves the whole "what comes next" list, so every track past the first
     * mismatch is dropped and put back, one Cast round trip each — a wider
     * window means more to re-add, and every re-add is a chance to fail. One
     * ahead is all that gapless handoff needs: the next track is on the
     * receiver before it is needed, and the phone has that track's whole
     * running time to queue the one after it.
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
        val wanted = windowAhead(queueItemsOf(player), current, AHEAD, ::castable)

        var kept = 0
        while (kept < remoteAhead.size && kept < wanted.size && remoteAhead[kept].mediaId() == wanted[kept].mediaId) {
            kept++
        }
        trace(
            "reconcile",
            "index=$current window=${queue.size} (was=$here) " +
                "want=${wanted.map { it.mediaId }} hold=${remoteAhead.map { it.mediaId() }} " +
                "prefix=$kept remove=${remoteAhead.drop(kept).map { it.mediaId() }}",
        )
        // The track before is settled whatever happened up there: a queue edited behind
        // the playing one leaves the tracks after it untouched, so the early-out
        // below would skip this on exactly the edit it exists for.
        if (kept != remoteAhead.size || kept != wanted.size) {
            if (kept < remoteAhead.size) {
                remote.queueRemoveItems(remoteAhead.drop(kept).map { it.itemId }.toIntArray(), null)
                    .awaitResultOrNull("remove") ?: return
            }
            for (next in wanted.drop(kept)) {
                // Not castable, or this one would not resolve in time: skip it
                // and carry on. Breaking here would leave the receiver with
                // whatever was queued before the bad track, which on a queue
                // edited anywhere behind it means no next track at all —
                // silence at the end of this one. The same ceiling as the load
                // path applies here, so a slow resolver cannot hold the queue
                // open indefinitely.
                val prepared = timedBuild(next, autoplay = true)
                if (prepared !is Prepared.Ready) continue
                if (!active) return
                remote.queueInsertItems(arrayOf(prepared.item), MediaQueueItem.INVALID_ITEM_ID, null)
                    .awaitResultOrNull("insert") ?: return
            }
        }

        reconcileBehind(remote, player, queue, here)
    }

    /**
     * Keeps the one track before the playing one in step, so that back on a
     * receiver is a jump rather than a reload. The window is read as "what comes
     * after the current item", which leaves this one to be matched on its own:
     * editing the queue further back does not move the current item, and without
     * this the receiver would go on offering a track the phone no longer has
     * before it.
     *
     * The playing item is never touched — removing it from a receiver's queue
     * stops playback — so the one behind it is replaced in place, before it.
     */
    private suspend fun reconcileBehind(
        remote: RemoteMediaClient,
        player: Player,
        queue: List<MediaQueueItem>,
        here: Int,
    ) {
        val current = player.currentMediaItemIndex
        if (current == C.INDEX_UNSET) return
        // Read out of the snapshot while it is still the truth. Everything past
        // this point awaits — a round trip to the receiver, then a URL to
        // resolve — and the receiver moves its queue while we wait, so the
        // snapshot is stale from here on and indexing into it ends the process.
        // An id is safe to keep: it names an item, not a place.
        val currentItemId = queue.getOrNull(here)?.itemId ?: return
        val wantedBehind = windowBefore(queueItemsOf(player), current, ::castable)
        val heldBehind = queue.getOrNull(here - 1)
        trace(
            "behind",
            "index=$current want=${wantedBehind?.mediaId} hold=${heldBehind?.mediaId()} " +
                if (heldBehind?.mediaId() == wantedBehind?.mediaId) "already there" else "changes",
        )
        if (heldBehind?.mediaId() == wantedBehind?.mediaId) return

        heldBehind?.let { remote.queueRemoveItems(intArrayOf(it.itemId), null).awaitResultOrNull("remove-behind") }
        if (!active) return
        if (wantedBehind == null) return
        val prepared = timedBuild(wantedBehind, autoplay = false)
        if (prepared !is Prepared.Ready) {
            trace("behind", "the previous track could not be prepared; leaving it as it is")
            return
        }
        if (!active) return
        // Asked to insert before an item that may have moved on while we were
        // resolving: the receiver is asked what it holds now, not what it held
        // when this began.
        val stillThere = remote.mediaStatus?.queueItems.orEmpty().any { it.itemId == currentItemId }
        if (!stillThere) {
            trace("behind", "the receiver no longer holds that item before it; left for the next pass")
            return
        }
        remote.queueInsertItems(arrayOf(prepared.item), currentItemId, null).awaitResultOrNull("insert-behind")
    }

    /**
     * Says what the receiver's queue is being asked to hold, and what it was
     * asked to do.
     *
     * The Cast framework logs nothing about queue operations at any verbosity a
     * release build keeps, which left the whole of this file's decision-making
     * unverifiable from outside — a window of the wrong shape, or a back that
     * reloads instead of jumping, look exactly like one that works until
     * somebody stands in front of a speaker and notices. One line at each
     * decision is enough to read the queue off the log afterwards.
     */
    private fun trace(op: String, detail: String) {
        if (TRACE_CAST) Log.i(TAG, "[$op] $detail")
    }

/**
 * Whether this track can reach a receiver at all.
 *
 * A receiver opens a URL by itself: it cannot resolve one of ours, cannot be
 * sent the headers a resolver might have wanted, and cannot reach a file on this
 * phone. So the question is asked of the scheme — of the one thing known without
 * touching the network.
 *
 * The local ones are named, and everything else is left to the resolver to judge:
 * a `bitchord://` address is how nearly every track here is spelled, and it is
 * exactly the one a receiver cannot make sense of on its own — but the resolver
 * turns it into a stream first, so it does reach one. Asking instead "is this
 * http(s)?" refuses every track the app owns.
 * A downloaded track is the case that needs both: its URI is a file's, but the
 * track behind it is still addressable, so it is cast through the address it
 * would have had — see [castableTrack]. The window and the load path ask this
 * same question, so a track skipped here is skipped there too, rather than the
 * queue offering the receiver something the load would refuse.
 */
private fun castable(item: MediaItem): Boolean {
    val uri = item.localConfiguration?.uri ?: return false
    return castableTrack(uri.scheme, item.mediaId)
}

/**
 * The phone's queue as a list, so the window can be worked out with the plain
 * functions a test can call. The player is the authority on the queue; this is
 * only its contents.
 */
private fun queueItemsOf(player: Player): List<MediaItem> =
    (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }

    private suspend fun buildQueueItem(item: MediaItem, autoplay: Boolean): MediaQueueItem? {
        val stream = try {
            resolve(item)
        } catch (e: CancellationException) {
            // A cancelled resolve says this track was overtaken, not that a
            // receiver cannot open it. Catching it as a failure used to tell
            // the listener "this can't be cast" and then carry on — which left
            // the receiver playing the track before, because nothing ever told
            // it to move.
            throw e
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

    /**
     * Not named `await`: [PendingResult] has a blocking member of that name,
     * and a member always beats an extension — so the blocking one ran, on the
     * main thread, and threw.
     */
    private suspend fun <R : Result> PendingResult<R>.awaitResult(): R =
        suspendCancellableCoroutine { continuation ->
            setResultCallback { continuation.resume(it) }
            continuation.invokeOnCancellation { cancel() }
        }

    /**
     * [awaitResult] under a ceiling, for the calls that hold the receiver's queue
     * open or the app in a loading state.
     *
     * Nothing on a Cast round trip is guaranteed to come back: a receiver that
     * loses the network mid-request simply never answers, and a plain await
     * waits on it for good. That is worse than slow here — the load leaves the
     * session in BUFFERING, and a queue call holds the reconcile open so nothing
     * after it runs either. So a call that does not answer in time is given up
     * on and reported.
     *
     * Giving up cancels the request, which is the point: a receiver left with an
     * answer arriving after the listener has moved on is worse than one that
     * never answers. The next pass asks again.
     */
    private suspend fun <R : Result> PendingResult<R>.awaitResultOrNull(op: String): R? {
        val result = withTimeoutOrNull(RPC_TIMEOUT_MS) { awaitResult() }
        if (result == null) {
            Log.w(TAG, "$op did not answer within ${RPC_TIMEOUT_MS}ms; the request was cancelled")
            trace(op, "no answer; the request was cancelled and left for the next pass")
        }
        return result
    }

    companion object {
        private const val TAG = "BitChordCast"

        /**
         * Whether every decision about the receiver's queue is said out loud.
         *
         * Off in a build that ships: it is one line per status the receiver
         * sends, which is about every three seconds for as long as a session
         * lasts, and none of it is about anything the listener asked. Turn it on
         * when the queue is being worked on — the framework logs nothing about
         * queue operations otherwise, and without this a window of the wrong
         * shape looks exactly like a correct one right up until somebody is
         * standing in front of a speaker.
         */
        private const val TRACE_CAST = false
        private const val KEY_MEDIA_ID = "mediaId"

        /** Tracks kept queued on the receiver after the playing one. */
        private const val AHEAD = 1
        private const val RECONCILE_DELAY_MS = 300L
        private const val SEEK_GRACE_MS = 1_500L

        /** Ceiling on resolving and loading one track, so a stall is an error and not a wait. */
        private const val LOAD_TIMEOUT_MS = 15_000L

        /** Ceiling on one queue round trip, so one that never answers cannot hold the queue open. */
        private const val RPC_TIMEOUT_MS = 10_000L

        /** How long a handover may go unconfirmed before the receiver's own reading is believed. */
        private const val SWITCH_TIMEOUT_MS = 6_000L

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
