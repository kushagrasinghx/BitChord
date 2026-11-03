package com.music.bitchord.playback.applemusic

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.music.bitchord.data.model.APPLE_MISSING_PREFIX
import com.music.bitchord.data.model.APPLE_PENDING_PREFIX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.appleMusicCatalogId
import com.music.bitchord.playback.cast.CastSink
import com.music.bitchord.playback.cast.RemoteSpeaker
import com.music.bitchord.playback.toSong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Main-thread queue/transport coordinator. No SDK types, URLs, or second queue. */
class AppleMusicSpeaker(
    private val scope: CoroutineScope,
    private val localPlayer: () -> Player?,
    private val amPlayer: AmPlayer?,
    private val settings: () -> Settings,
    private val higherPriorityActive: () -> Boolean,
    private val developerToken: suspend () -> String,
    private val match: suspend (Song, String) -> String?,
    private val prefetch: suspend (List<Song>, String) -> Unit,
    private val onChanged: () -> Unit = {},
    private val onFailure: (fallback: Boolean) -> Unit = {},
    private val onTrackEnded: () -> Unit = {},
    private val matchTimeoutMs: Long = 2_500,
    private val loadTimeoutMs: Long = 15_000,
    private val songOf: (MediaItem) -> Song = { it.toSong() },
) : RemoteSpeaker {
    data class Settings(
        val selected: Boolean,
        val userToken: String?,
        val storefront: String?,
        val fallback: Boolean = true,
        val playbackStorefront: String? = null,
    ) {
        val authorized: Boolean get() = !userToken.isNullOrBlank() &&
            storefront?.matches(Regex("[a-z]{2}")) == true

        override fun toString() = "AppleMusicSettings(selected=$selected, authorized=$authorized, fallback=$fallback)"
    }

    override var sink: CastSink? = null
    override val isLocalOutput = true
    override var active = false
        private set
    override var playWhenReady = false
        private set
    override var playbackState = Player.STATE_IDLE
        private set
    private var reportedPositionMs = 0L
    override val positionMs: Long get() =
        if (loaded) amPlayer?.positionMs?.takeIf { it >= 0 } ?: reportedPositionMs else reportedPositionMs
    override var durationMs = C.TIME_UNSET
        private set
    override var playerError: PlaybackException? = null
        private set
    override val isPlaying: Boolean get() = active && playing && playWhenReady
    // Local STREAM_MUSIC volume belongs to Android, not the MusicKit controller.
    override val deviceVolume = 0
    override fun setVolume(fraction: Float) = Unit

    private var playing = false
    private var loaded = false
    private var ended = false
    @Volatile private var released = false
    private var generation = 0L
    private var itemId: String? = null
    private var itemIndex = C.INDEX_UNSET
    private var loadJob: Job? = null
    private var prefetchJob: Job? = null
    @Volatile private var fallbackId: String? = null
    private var lastSettings = settings()
    @Volatile private var preempted = higherPriorityActive()

    init {
        require(matchTimeoutMs > 0 && loadTimeoutMs > 0)
        amPlayer?.listener = object : AmPlayer.Listener {
            override fun onStateChanged(snapshot: AmPlayer.Snapshot) {
                if (!accepts(snapshot.requestId)) return
                // Preparation callbacks describe the SDK's old/pre-seek clock, not
                // a seek the listener may have requested while load was suspended.
                if (loaded) {
                    reportedPositionMs = snapshot.positionMs.coerceAtLeast(0)
                    playbackState = snapshot.playbackState
                }
                durationMs = snapshot.durationMs.takeIf { it > 0 } ?: C.TIME_UNSET
                playing = loaded && snapshot.isPlaying
                // During load, keep the user's intent rather than the SDK's paused preparation.
                if (loaded && snapshot.playbackState != Player.STATE_ENDED) {
                    playWhenReady = snapshot.playWhenReady
                }
                if (snapshot.playbackState == Player.STATE_ENDED && loaded && !ended) {
                    ended = true
                    finishTrack()
                } else {
                    changed()
                }
            }

            override fun onError(requestId: Long) {
                if (accepts(requestId)) fail(requestId)
            }
        }
    }

    private fun eligible(config: Settings) = !released && amPlayer?.available == true &&
        config.selected && config.authorized && !higherPriorityActive()

    /** Safe from a loader thread; also holds next-item preparation before a transition fires. */
    fun holdsLocalAudio(mediaId: String): Boolean {
        if (released || amPlayer?.available != true || preempted) return false
        val config = settings()
        return config.selected && config.authorized && mediaId != fallbackId
    }

    data class Handoff(val positionMs: Long, val playWhenReady: Boolean)

    /** Called before Cast/watch read the paused queue owner's clock and play intent. */
    fun handoffToRemote(): Handoff? {
        if (!active || released) return null
        val handoff = Handoff(positionMs, playWhenReady)
        // The watch asks for the local player just before marking itself active.
        // Do not announce a route here and let refresh reclaim Apple in that gap.
        preempted = true
        invalidate()
        active = false
        playWhenReady = false
        parkLocalClock(handoff.positionMs)
        onChanged()
        return handoff
    }

    /** Reconcile provider/account/fallback changes and Cast/watch connect/disconnect. */
    fun refresh() {
        if (released) return
        val config = settings()
        val priority = higherPriorityActive()
        if (config == lastSettings && priority == preempted) return
        val wasActive = active
        val resume = if (wasActive) playWhenReady else localPlayer()?.playWhenReady == true
        val position = if (wasActive) positionMs else localPlayer()?.currentPosition ?: 0
        lastSettings = config
        preempted = priority
        fallbackId = null
        invalidate()
        active = false
        if (wasActive) parkLocalClock(position)
        notifyRoute()
        if (eligible(config)) {
            start(localPlayer()?.currentMediaItem, resume, position)
        } else if (wasActive && !priority) {
            resumeLocal(resume)
        }
    }

    override fun onLocalTransition(item: MediaItem?, reason: Int) {
        if (released) return
        refresh()
        if (item?.mediaId == itemId && localPlayer()?.currentMediaItemIndex == itemIndex &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
            prefetchNext()
            return
        }
        val resume = if (active) playWhenReady else localPlayer()?.playWhenReady == true
        val position = localPlayer()?.currentPosition ?: 0
        invalidate()
        fallbackId = null
        itemId = item?.mediaId
        active = false
        playerError = null
        if (item != null && eligible(settings())) start(item, resume, position) else notifyRoute()
    }

    private fun start(item: MediaItem?, play: Boolean, position: Long) {
        val song = item?.let(songOf) ?: return
        itemId = item.mediaId
        itemIndex = localPlayer()?.currentMediaItemIndex ?: C.INDEX_UNSET
        if (song.localUri != null || item.localConfiguration?.uri?.scheme in listOf("file", "content")) {
            notifyRoute()
            return
        }
        val config = settings()
        val requestId = ++generation
        active = true // Claim transport BEFORE waiting for catalog/network: no YouTube blip.
        playWhenReady = play
        reportedPositionMs = position.coerceAtLeast(0)
        durationMs = C.TIME_UNSET
        playbackState = Player.STATE_BUFFERING
        playerError = null
        playing = false
        loaded = false
        ended = false
        localPlayer()?.pause()
        notifyRoute()
        // Pending library rows are never a YouTube id, nor a fabricated catalog id.
        if (song.videoId.startsWith(APPLE_PENDING_PREFIX)) {
            fail(requestId)
            return
        }
        // Lazy assignment also works with immediate dispatchers and synchronous fake callbacks.
        loadJob = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val catalogId = withTimeoutOrNull(matchTimeoutMs) {
                    if (song.videoId.startsWith(APPLE_MISSING_PREFIX)) song.appleMusicCatalogId
                    else match(song, requireNotNull(config.storefront))
                }
                if (!accepts(requestId, config)) return@launch
                if (catalogId == null || !CATALOG_ID.matches(catalogId)) {
                    fail(requestId)
                    return@launch
                }
                val ready = withTimeoutOrNull(loadTimeoutMs) {
                    val token = developerToken()
                    currentCoroutineContext().ensureActive()
                    if (!accepts(requestId, config)) return@withTimeoutOrNull false
                    amPlayer?.load(AmPlayer.Request(
                        requestId, catalogId, token, requireNotNull(config.userToken),
                        requireNotNull(config.storefront), positionMs,
                        config.playbackStorefront,
                    ))
                    true
                } == true
                currentCoroutineContext().ensureActive()
                if (!accepts(requestId, config)) return@launch
                if (!ready) {
                    fail(requestId)
                    return@launch
                }
                amPlayer?.seekTo(positionMs) // Includes seeks received during preparation.
                if (!accepts(requestId, config)) return@launch
                loaded = true
                playbackState = Player.STATE_READY
                if (playWhenReady) amPlayer?.play()
                changed()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (accepts(requestId, config)) fail(requestId)
            } catch (_: LinkageError) {
                if (accepts(requestId, config)) fail(requestId)
            }
        }.also { it.start() }
        prefetchNext()
    }

    private fun accepts(requestId: Long, config: Settings = lastSettings): Boolean =
        active && !released && requestId == generation && eligible(config) &&
            settings() == config && localPlayer()?.currentMediaItem?.mediaId == itemId

    private fun fail(requestId: Long) {
        if (requestId != generation || !active) return
        val resume = playWhenReady
        val position = positionMs
        val fallback = settings().fallback && !isAppleRow(itemId)
        // Invalidate native callbacks before stopping or letting ExoPlayer resume.
        invalidate()
        playing = false
        loaded = false
        if (fallback) {
            fallbackId = itemId
            active = false
            playerError = null
            parkLocalClock(position)
            notifyRoute()
            resumeLocal(resume)
        } else {
            playWhenReady = false
            playbackState = Player.STATE_IDLE
            playerError = PlaybackException("Apple Music playback unavailable for this track", null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR)
            localPlayer()?.pause()
            changed()
        }
        onFailure(fallback)
        prefetchNext()
    }

    override fun play() {
        if (!active) return
        if (playerError != null || ended) {
            val position = if (ended) 0 else positionMs
            invalidate()
            start(localPlayer()?.currentMediaItem, true, position)
            return
        }
        playWhenReady = true
        transport { if (loaded) amPlayer?.play() }
        changed()
    }

    override fun pause() {
        if (!active) return
        playWhenReady = false
        transport { if (loaded) amPlayer?.pause() }
        playing = false
        changed()
    }

    override fun seekTo(positionMs: Long) {
        if (!active) return
        reportedPositionMs = positionMs.coerceAtLeast(0).let {
            if (durationMs > 0) it.coerceAtMost(durationMs) else it
        }
        transport { if (loaded) amPlayer?.seekTo(reportedPositionMs) }
        changed()
    }

    private inline fun transport(action: () -> Unit) {
        val requestId = generation
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            fail(requestId)
        } catch (_: LinkageError) {
            fail(requestId)
        }
    }

    private fun finishTrack() {
        val local = localPlayer() ?: return
        playing = false
        onTrackEnded()
        // A pause/end race must not start another song behind the listener's back.
        val resume = playWhenReady
        when {
            local.repeatMode == Player.REPEAT_MODE_ONE ||
                (local.repeatMode == Player.REPEAT_MODE_ALL && local.mediaItemCount == 1) -> {
                invalidate()
                start(local.currentMediaItem, resume, 0)
            }
            local.hasNextMediaItem() -> {
                val completedRequest = generation
                local.seekToNextMediaItem()
                // Real Player listeners deliver this later; fakes may deliver it inline.
                if (generation == completedRequest) {
                    onLocalTransition(local.currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
                }
            }
            local.repeatMode == Player.REPEAT_MODE_ALL && local.mediaItemCount > 0 -> {
                local.seekTo(0, 0)
                invalidate()
                start(local.currentMediaItem, resume, 0)
            }
            else -> {
                playWhenReady = false
                playbackState = Player.STATE_ENDED
                changed()
            }
        }
    }

    override fun onLocalQueueChanged() {
        refresh()
        if (localPlayer()?.currentMediaItem?.mediaId != itemId) {
            onLocalTransition(localPlayer()?.currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        } else prefetchNext()
    }

    override fun onLocalRepeatChanged(repeatMode: Int) = prefetchNext()

    override fun onLocalStartedPlaying(player: Player) {
        refresh()
        if (!active && eligible(settings()) && player.currentMediaItem?.mediaId != fallbackId) {
            start(player.currentMediaItem, true, player.currentPosition)
        }
        if (active) {
            player.pause()
            play()
        }
    }

    private fun prefetchNext() {
        prefetchJob?.cancel()
        if (!eligible(settings())) return
        val local = localPlayer() ?: return
        val config = settings()
        val songs = mutableListOf<Song>()
        val timeline = local.currentTimeline
        var index = local.currentMediaItemIndex
        val seen = mutableSetOf(index)
        for (offset in 0 until 3) {
            index = if (timeline.isEmpty) {
                (index + 1).takeIf { it < local.mediaItemCount } ?: C.INDEX_UNSET
            } else {
                timeline.getNextWindowIndex(index,
                    if (local.repeatMode == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF,
                    local.shuffleModeEnabled)
            }
            if (index == C.INDEX_UNSET || !seen.add(index)) break
            songOf(local.getMediaItemAt(index)).takeUnless {
                it.localUri != null || it.videoId.startsWith(APPLE_PENDING_PREFIX)
            }?.let(songs::add)
        }
        prefetchJob = scope.launch {
            try {
                prefetch(songs, requireNotNull(config.storefront))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Prefetch is optional; live matching still has its own deadline.
            }
        }
    }

    private fun parkLocalClock(position: Long) {
        val local = localPlayer() ?: return
        if (local.currentMediaItem?.mediaId == itemId) local.seekTo(position.coerceAtLeast(0))
    }

    private fun resumeLocal(play: Boolean) {
        val local = localPlayer() ?: return
        if (isAppleRow(local.currentMediaItem?.mediaId)) {
            local.pause()
            return
        }
        if (local.playbackState == Player.STATE_IDLE && local.mediaItemCount > 0) local.prepare()
        local.playWhenReady = play
    }

    private fun invalidate() {
        ++generation
        loadJob?.cancel()
        loadJob = null
        prefetchJob?.cancel()
        prefetchJob = null
        loaded = false
        playing = false
        try {
            amPlayer?.stop()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Cleanup must not strand the route on a failed native controller.
        } catch (_: LinkageError) {
            // A controller may have lost its native runtime.
        }
    }

    private fun notifyRoute() {
        sink?.onRemoteRouteChanged()
        changed()
    }

    private fun changed() {
        sink?.onRemotePlaybackChanged()
        onChanged()
    }

    fun release() {
        if (released) return
        if (active) {
            reportedPositionMs = positionMs
            parkLocalClock(reportedPositionMs)
        }
        released = true
        active = false
        playWhenReady = false
        playbackState = Player.STATE_IDLE
        invalidate()
        amPlayer?.listener = null
        try {
            amPlayer?.release()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Best-effort lifecycle cleanup.
        } catch (_: LinkageError) {
            // Best-effort lifecycle cleanup.
        }
        sink = null
    }

    companion object {
        private val CATALOG_ID = Regex("[1-9][0-9]{0,31}")
        fun isAppleRow(mediaId: String?) = mediaId?.let {
            it.startsWith(APPLE_PENDING_PREFIX) || it.startsWith(APPLE_MISSING_PREFIX)
        } == true
    }
}
