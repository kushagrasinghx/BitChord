package com.music.bitchord.playback.applemusic

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Player
import com.apple.android.music.playback.controller.MediaPlayerController
import com.apple.android.music.playback.controller.MediaPlayerControllerFactory
import com.apple.android.music.playback.model.MediaItemType
import com.apple.android.music.playback.model.MediaPlayerException
import com.apple.android.music.playback.model.PlaybackState
import com.apple.android.music.playback.model.PlaybackRepeatMode
import com.apple.android.music.playback.model.PlaybackShuffleMode
import com.apple.android.music.playback.model.PlayerQueueItem
import com.apple.android.music.playback.player.MediaPlayerContextFactory
import com.apple.android.music.playback.queue.CatalogPlaybackQueueItemProvider
import com.apple.android.music.playback.queue.PlaybackModeContainer
import com.apple.android.music.playback.queue.PlaybackQueueItemsAdded
import com.apple.android.music.playback.queue.PlaybackQueueInsertionType
import com.apple.android.sdk.authentication.TokenProvider
import com.music.bitchord.BuildConfig
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Compiled only with Apple's official AAR pair; availability stays gated until device QA. */
internal fun createMusicKitPlayer(context: Context): AmPlayer? {
    if (!BuildConfig.APPLE_MUSIC_AVAILABLE ||
        Build.SUPPORTED_ABIS.none { it == "arm64-v8a" || it == "armeabi-v7a" }) return null
    try {
        context.packageManager.getPackageInfo("com.apple.android.music", 0)
    } catch (_: PackageManager.NameNotFoundException) {
        return null
    }
    // MusicKit requires explicit JNI loading before creating its controller.
    // LinkageError (including unsupported ABI/page alignment) is caught by AmPlayerFactory.
    System.loadLibrary("c++_shared")
    System.loadLibrary("appleMusicSDK")
    return MusicKitAmPlayer(context.applicationContext)
}

/**
 * One native controller per song isolates delayed callbacks and SDK-cached credentials.
 * MusicKit owns Android audio focus; releasing its controller relinquishes that focus.
 * No Apple DRM data or URL is exposed to Media3.
 */
private class MusicKitAmPlayer(private val context: Context) : AmPlayer {
    override val available = true
    override var listener: AmPlayer.Listener? = null
    override val positionMs: Long get() = controller?.currentPosition ?: C.TIME_UNSET
    private val main = Handler(Looper.getMainLooper())
    private var controller: MediaPlayerController? = null
    private var nativeListener: MediaPlayerController.Listener? = null
    private var tokens: PlaybackTokens? = null
    private var preparing: CancellableContinuation<Unit>? = null
    private var requestId = 0L
    private var desiredPlay = false
    private var buffering = true
    private var ended = false
    private var wasPlaying = false
    private var released = false

    override suspend fun load(request: AmPlayer.Request) = withContext(Dispatchers.Main.immediate) {
        check(!released)
        stop()
        requestId = request.requestId
        desiredPlay = false
        buffering = true
        ended = false
        wasPlaying = false
        check(!request.playbackStorefront.isNullOrBlank()) { "Reconnect Apple Music to obtain its playback storefront" }
        val credentials = PlaybackTokens(request.developerToken, request.userToken, request.playbackStorefront)
        tokens = credentials
        val native = MediaPlayerControllerFactory.createLocalController(context, main, credentials)
        controller = native
        native.playbackStoreConfiguration.notifyTokenChanged(context, credentials)
        // The phone implements repeat/shuffle; MusicKit owns exactly one song.
        native.repeatMode = PlaybackRepeatMode.REPEAT_MODE_OFF
        native.shuffleMode = PlaybackShuffleMode.SHUFFLE_MODE_OFF
        val events = events(native, request.requestId)
        nativeListener = events
        native.addListener(events)
        suspendCancellableCoroutine<Unit> { completion ->
            preparing = completion
            completion.invokeOnCancellation {
                main.post { if (controller === native) stop() }
            }
            try {
                val queue = CatalogPlaybackQueueItemProvider.Builder()
                    .items(MediaItemType.SONG, request.catalogId).build()
                // false is essential: prepare must never start audio before queue intent is checked.
                native.prepare(queue, PlaybackQueueInsertionType.INSERTION_TYPE_CLEAR_AND_REPLACE, false)
            } catch (_: Exception) {
                preparing = null
                if (completion.isActive) completion.resumeWithException(playbackFailure())
            } catch (_: LinkageError) {
                preparing = null
                if (completion.isActive) completion.resumeWithException(playbackFailure())
            }
        }
    }

    private fun events(native: MediaPlayerController, id: Long) = object : MediaPlayerController.Listener {
        private fun current() = !released && controller === native && requestId == id
        private fun changed() { if (current()) emit(native, id) }

        override fun onPlaybackQueueItemsAdded(player: MediaPlayerController, addedItems: PlaybackQueueItemsAdded) {
            if (!current() || addedItems.numOfItems <= 0) return
            buffering = false
            val completion = preparing
            preparing = null
            if (completion?.isActive == true) completion.resume(Unit)
            changed()
        }

        override fun onPlaybackStateChanged(player: MediaPlayerController, previousState: Int, newState: Int) {
            if (!current()) return
            // MusicKit pauses for audio-focus interruptions. Publish that pause to the session.
            if (newState == PlaybackState.PAUSED && wasPlaying && !buffering) desiredPlay = false
            wasPlaying = newState == PlaybackState.PLAYING
            changed()
        }

        override fun onBufferingStateChanged(player: MediaPlayerController, isBuffering: Boolean) {
            if (!current()) return
            buffering = isBuffering
            changed()
        }

        override fun onItemEnded(player: MediaPlayerController, item: PlayerQueueItem, endPosition: Long) {
            if (!current() || ended) return
            ended = true
            changed()
        }

        override fun onPlaybackError(player: MediaPlayerController, error: MediaPlayerException) {
            if (!current()) return
            val completion = preparing
            preparing = null
            if (completion?.isActive == true) completion.resumeWithException(playbackFailure())
            // Native exception text may contain request credentials; expose only the generation.
            listener?.onError(id)
        }

        override fun onPlaybackStateUpdated(player: MediaPlayerController) = changed()
        override fun onCurrentItemChanged(player: MediaPlayerController, previous: PlayerQueueItem?, current: PlayerQueueItem?) = changed()
        override fun onMetadataUpdated(player: MediaPlayerController, item: PlayerQueueItem) = changed()
        override fun onPlayerStateRestored(player: MediaPlayerController) = Unit
        override fun onPlaybackQueueChanged(player: MediaPlayerController, items: MutableList<PlayerQueueItem>) = Unit
        override fun onPlaybackModeChanged(player: MediaPlayerController, mode: PlaybackModeContainer) = Unit
        override fun onPlaybackRepeatModeChanged(player: MediaPlayerController, mode: Int) = Unit
        override fun onPlaybackShuffleModeChanged(player: MediaPlayerController, mode: Int) = Unit
    }

    private fun emit(native: MediaPlayerController, id: Long) {
        val playing = !ended && native.playbackState == PlaybackState.PLAYING
        listener?.onStateChanged(AmPlayer.Snapshot(
            requestId = id,
            playbackState = when { ended -> Player.STATE_ENDED; buffering -> Player.STATE_BUFFERING; else -> Player.STATE_READY },
            playWhenReady = desiredPlay,
            isPlaying = playing,
            positionMs = native.currentPosition.coerceAtLeast(0),
            durationMs = native.duration.takeIf { it > 0 } ?: C.TIME_UNSET,
        ))
    }

    override fun play() {
        desiredPlay = true
        controller?.play()
    }

    override fun pause() {
        desiredPlay = false
        controller?.pause()
    }

    override fun seekTo(positionMs: Long) { controller?.seekToPosition(positionMs.coerceAtLeast(0)) }

    override fun stop() {
        val native = controller
        controller = null // Reject reentrant/delayed native callbacks before touching the player.
        preparing?.cancel()
        preparing = null
        desiredPlay = false
        try {
            if (native != null) {
                nativeListener?.let(native::removeListener)
                native.pause()
                native.stop()
                tokens?.let {
                    it.clear()
                    native.playbackStoreConfiguration.notifyTokenChanged(context, it)
                }
                native.release()
            }
        } catch (_: Exception) {
            // Cleanup must also be safe when posted by a cancelled prepare callback.
        } catch (_: LinkageError) {
        } finally {
            nativeListener = null
            tokens?.clear()
            tokens = null
        }
    }

    override fun release() {
        released = true
        listener = null
        stop()
        try {
            MediaPlayerContextFactory.createPlayerContext(context).clearData()
        } catch (_: Exception) {
        } catch (_: LinkageError) {
        }
    }

    private fun playbackFailure() = IllegalStateException("MusicKit could not prepare this catalog song")

    private class PlaybackTokens(private var developer: String?, private var user: String?, private var storefront: String?) : TokenProvider {
        override fun getDeveloperToken(): String? = developer
        override fun getUserToken(): String? = user
        override fun getAppleMusicStoreFrontId(): String? = storefront
        fun clear() { developer = null; user = null; storefront = null }
    }
}
