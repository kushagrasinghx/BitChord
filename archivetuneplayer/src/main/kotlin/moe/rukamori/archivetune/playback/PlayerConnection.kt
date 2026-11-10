/*
 * BRIDGE (replaces ArchiveTune's playback/PlayerConnection.kt).
 *
 * Same name, same package, same public surface — but where ArchiveTune's
 * class wrapped its MusicService binder and Room database, this one wraps
 * the hosting app's Media3 controller (a MediaController is a Player, which
 * is most of what the player UI ever asks for) plus the host bridge for
 * lyrics, likes and queue swaps. The copied player sources cannot tell the
 * difference.
 */

package moe.rukamori.archivetune.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.bridge.ArchiveTuneBridge
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.extensions.currentMetadata
import moe.rukamori.archivetune.extensions.getCurrentQueueIndex
import moe.rukamori.archivetune.extensions.getQueueWindows
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.ui.player.refetchCanvasArtworkForPlayback
import timber.log.Timber
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

internal data class CanvasArtworkUpdate(
    val mediaId: String,
    val artwork: CanvasArtwork,
)

internal enum class CanvasArtworkRefetchResult {
    Success,
    Failure,
    AlreadyRunning,
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerConnection(
    scope: CoroutineScope,
) : Player.Listener {
    private val host get() = ArchiveTuneBridge.get()
    private val connectionJob = SupervisorJob(scope.coroutineContext[Job])
    private val connectionScope = CoroutineScope(scope.coroutineContext + connectionJob)

    val service = MusicService(connectionScope)

    /** The host's controller — a MediaController is a Player. */
    val player: Player
        get() = host?.player() ?: PlaceholderPlayer

    /**
     * An ExoPlayer-typed view of the controller, for the queue sheets'
     * shuffle-order surgery while reordering under shuffle. Every Player
     * method forwards to the controller; the ExoPlayer-only members answer
     * with inert defaults (a controller cannot take a shuffle order, so the
     * drag keeps its optimistic local state and simply doesn't persist;
     * audioSessionId reads 0, which only gates the equalizer hook this port
     * stubs anyway). A proxy, so it stays whole whatever ExoPlayer grows.
     */
    val localPlayer: ExoPlayer by lazy { bridgeExoPlayer(player) }

    private var attachedPlayer: Player? = null

    private val playbackState = MutableStateFlow(player.playbackState)
    private val playWhenReady = MutableStateFlow(player.playWhenReady)
    val playbackParameters = MutableStateFlow(player.playbackParameters)

    val isPlaying =
        combine(playbackState, playWhenReady) { playbackState, playWhenReady ->
            playWhenReady && playbackState != STATE_ENDED
        }.stateIn(
            connectionScope,
            SharingStarted.Lazily,
            player.playWhenReady && player.playbackState != STATE_ENDED,
        )

    val mediaMetadata = MutableStateFlow<MediaMetadata?>(player.currentMetadata)
    val currentSong =
        mediaMetadata
            .flatMapLatest { metadata ->
                flowOf(metadata?.let { host?.song(it.id) })
            }.stateIn(connectionScope, SharingStarted.WhileSubscribed(5_000), null)

    val currentLyrics =
        mediaMetadata
            .flatMapLatest { metadata ->
                val hostFlow = metadata?.let { host?.currentLyrics() }
                if (hostFlow != null) hostFlow else flowOf(null)
            }.stateIn(connectionScope, SharingStarted.WhileSubscribed(5_000), null)

    val currentFormat =
        mediaMetadata
            .flatMapLatest { metadata ->
                val hostFlow = metadata?.let { host?.currentFormat() }
                if (hostFlow != null) hostFlow else flowOf(null)
            }.stateIn(connectionScope, SharingStarted.WhileSubscribed(5_000), null)

    val queueTitle = MutableStateFlow<String?>(null)
    val queueWindows = MutableStateFlow<List<Timeline.Window>>(emptyList())
    val currentMediaItemIndex = MutableStateFlow(-1)
    val currentWindowIndex = MutableStateFlow(-1)

    val shuffleModeEnabled = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(REPEAT_MODE_OFF)

    val canSkipPrevious = MutableStateFlow(true)
    val canSkipNext = MutableStateFlow(true)

    val aodModeEnabled = MutableStateFlow(false)

    val error = MutableStateFlow<PlaybackException?>(null)
    private var dismissedPlaybackError: PlaybackException? = null
    val waitingForNetworkConnection = MutableStateFlow(false)
    val queueRestoreCompleted = MutableStateFlow(true)

    private val _songEndedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val songEndedEvents = _songEndedEvents.asSharedFlow()

    val extractorAuthenticationEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val _canvasArtworkUpdates = MutableSharedFlow<CanvasArtworkUpdate>(extraBufferCapacity = 1)
    internal val canvasArtworkUpdates = _canvasArtworkUpdates.asSharedFlow()

    private val canvasRefetchRunning = AtomicBoolean(false)
    internal val isCanvasArtworkRefetching = MutableStateFlow(false).asStateFlow()

    init {
        attachToPlayer(player)
    }

    private fun attachToPlayer(newPlayer: Player) {
        attachedPlayer?.removeListener(this)
        attachedPlayer = newPlayer
        newPlayer.addListener(this)

        playbackState.value = newPlayer.playbackState
        playWhenReady.value = newPlayer.playWhenReady
        playbackParameters.value = newPlayer.playbackParameters
        queueWindows.value = newPlayer.getQueueWindows()
        currentWindowIndex.value = newPlayer.getCurrentQueueIndex()
        currentMediaItemIndex.value = newPlayer.currentMediaItemIndex
        shuffleModeEnabled.value = newPlayer.shuffleModeEnabled
        repeatMode.value = newPlayer.repeatMode
        mediaMetadata.value = newPlayer.currentMetadata
        updateCanSkipPreviousAndNext()
    }

    fun playQueue(queue: Queue) {
        connectionScope.launch {
            runCatching {
                val status = queue.getInitialStatus()
                host?.playItems(status.items, status.mediaItemIndex)
            }.onFailure {
                if (it is CancellationException) throw it
                Timber.tag("ArchiveTuneBridge").w(it, "playQueue failed")
            }
        }
    }

    fun startRadioSeamlessly() {
        mediaMetadata.value?.let { host?.startRadio(it) }
    }

    fun startRadio(seed: MediaMetadata) {
        host?.startRadio(seed)
    }

    fun playNext(item: MediaItem) = playNext(listOf(item))

    fun playNext(items: List<MediaItem>) {
        host?.playNext(items)
    }

    fun moveQueueItemToNext(mediaItemIndex: Int) {
        val current = currentWindowIndex.value
        if (mediaItemIndex < 0 || current < 0) return
        if (mediaItemIndex == current || mediaItemIndex == current + 1) return
        player.moveMediaItem(mediaItemIndex, current + 1)
    }

    fun addToQueue(item: MediaItem) = addToQueue(listOf(item))

    fun addToQueue(items: List<MediaItem>) {
        host?.addToQueue(items)
    }

    fun addAfterQueueIndex(index: Int, items: List<MediaItem>) {
        host?.addAfterQueueIndex(index, items)
    }

    fun playFromVoiceSearch(query: String) {
        Timber.tag("ArchiveTuneBridge").d("playFromVoiceSearch(%s) — not bridged", query)
    }

    fun toggleLike() {
        service.toggleLike()
    }

    internal suspend fun refetchCanvasArtwork(
        metadata: MediaMetadata,
        requireVertical: Boolean,
    ): CanvasArtworkRefetchResult {
        if (!canvasRefetchRunning.compareAndSet(false, true)) {
            return CanvasArtworkRefetchResult.AlreadyRunning
        }
        isCanvasArtworkRefetching.value = true
        return try {
            val country = Locale.getDefault().country
            val storefront = if (country.length == 2) country.lowercase(Locale.ROOT) else "us"
            val artwork =
                refetchCanvasArtworkForPlayback(
                    mediaId = metadata.id,
                    songTitleRaw = metadata.title,
                    artistNameRaw = metadata.artists.firstOrNull()?.name.orEmpty(),
                    storefront = storefront,
                    requireVertical = requireVertical,
                    albumTitle = metadata.album?.title,
                ) ?: return CanvasArtworkRefetchResult.Failure

            _canvasArtworkUpdates.emit(
                CanvasArtworkUpdate(
                    mediaId = metadata.id,
                    artwork = artwork,
                ),
            )
            CanvasArtworkRefetchResult.Success
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.tag("CanvasArtwork").w(error, "Canvas refetch failed for %s", metadata.id)
            CanvasArtworkRefetchResult.Failure
        } finally {
            canvasRefetchRunning.set(false)
            isCanvasArtworkRefetching.value = false
        }
    }

    internal fun publishCanvasArtworkUpdate(
        mediaId: String,
        artwork: CanvasArtwork,
    ) {
        connectionScope.launch {
            _canvasArtworkUpdates.emit(
                CanvasArtworkUpdate(
                    mediaId = mediaId,
                    artwork = artwork,
                ),
            )
        }
    }

    fun dismissPlaybackError() {
        dismissedPlaybackError = error.value ?: player.playerError
        error.value = null
    }

    fun updateExtractorBearerToken(token: String) {}

    fun seekToNext() {
        player.seekToNextMediaItem()
        player.prepare()
        player.playWhenReady = true
    }

    fun seekToPrevious() {
        player.seekToPreviousMediaItem()
        player.prepare()
        player.playWhenReady = true
    }

    override fun onPlaybackStateChanged(state: Int) {
        playbackState.value = state
        updatePlaybackError(player.playerError)
    }

    override fun onPlayWhenReadyChanged(
        newPlayWhenReady: Boolean,
        reason: Int,
    ) {
        playWhenReady.value = newPlayWhenReady
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        this.playbackParameters.value = playbackParameters
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        mediaMetadata.value = player.currentMetadata
        updateCanSkipPreviousAndNext()
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && mediaItem != null) {
            _songEndedEvents.tryEmit(Unit)
        }
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) {
        queueWindows.value = player.getQueueWindows()
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onShuffleModeEnabledChanged(enabled: Boolean) {
        shuffleModeEnabled.value = enabled
        queueWindows.value = player.getQueueWindows()
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onRepeatModeChanged(mode: Int) {
        repeatMode.value = mode
        updateCanSkipPreviousAndNext()
    }

    override fun onPlayerErrorChanged(playbackError: PlaybackException?) {
        updatePlaybackError(playbackError)
    }

    private fun updatePlaybackError(playbackError: PlaybackException?) {
        when {
            playbackError == null -> {
                dismissedPlaybackError = null
                error.value = null
            }

            playbackError !== dismissedPlaybackError -> {
                dismissedPlaybackError = null
                error.value = playbackError
            }
        }
    }

    private fun updateCanSkipPreviousAndNext() {
        if (!player.currentTimeline.isEmpty) {
            canSkipPrevious.value =
                player.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) ||
                    player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            canSkipNext.value = player.isCommandAvailable(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        } else {
            canSkipPrevious.value = false
            canSkipNext.value = false
        }
    }

    fun dispose() {
        connectionScope.cancel()
        attachedPlayer?.removeListener(this)
        attachedPlayer = null
    }
}

/** See [PlayerConnection.localPlayer]. */
private fun bridgeExoPlayer(controller: Player): ExoPlayer =
    Proxy.newProxyInstance(
        ExoPlayer::class.java.classLoader,
        arrayOf(ExoPlayer::class.java),
        InvocationHandler { proxy, method, args ->
            when (method.name) {
                "setShuffleOrder" -> {
                    Timber.tag("ArchiveTuneBridge").d("setShuffleOrder over a controller — ignored")
                    null
                }

                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "ArchiveTuneBridgeExoPlayer($controller)"
                else -> forwardOrDefault(controller, method, args)
            }
        },
    ) as ExoPlayer

/** Invoke [method] on [controller] when it declares a matching signature. */
private fun forwardOrDefault(controller: Player, method: Method, args: Array<Any?>?): Any? {
    val wanted = args ?: emptyArray()
    val match =
        controller.javaClass.methods.firstOrNull {
            it.name == method.name && it.parameterTypes.contentEquals(method.parameterTypes)
        } ?: return defaultValue(method)
    return try {
        match.invoke(controller, *wanted)
    } catch (e: InvocationTargetException) {
        throw e.cause ?: e
    } catch (e: IllegalArgumentException) {
        defaultValue(method)
    } catch (e: Exception) {
        Timber.tag("ArchiveTuneBridge").w(e, "ExoPlayer bridge call %s failed", method.name)
        defaultValue(method)
    }
}

private fun defaultValue(method: Method): Any? =
    when (method.returnType.name) {
        "boolean" -> false
        "int", "long", "short", "byte", "char" -> 0
        "float", "double" -> 0.0
        else -> null
    }
