package com.music.bitchord.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.canvas.CanvasSource
import com.music.bitchord.data.model.QueueEntryIdentity
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.AutoMixArtworkTuning
import com.music.bitchord.playback.AutoMixVisualState
import com.music.bitchord.ui.rememberIsForeground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class AutoMixCanvasPair {
    var visual by mutableStateOf<AutoMixCanvasTracks?>(null)
        private set
    private var shownEntry: QueueEntryIdentity? = null
    private var shownClip: CanvasArtwork? = null
    private var seenGeneration: Long? = null

    fun displayed(entry: QueueEntryIdentity?, clip: CanvasArtwork?, rendered: Boolean) {
        shownEntry = entry
        shownClip = clip.takeIf { rendered }
    }

    fun start(state: AutoMixVisualState, entry: QueueEntryIdentity?): AutoMixCanvasTracks? {
        if (seenGeneration == state.generation) return null
        seenGeneration = state.generation
        if (entry != state.outgoing.identity) return null
        return AutoMixCanvasTracks(
            state.generation, state.outgoing.identity, state.incoming.identity,
            shownClip.takeIf { shownEntry == entry },
        ).also { visual = it }
    }

    fun clear() {
        visual = null
    }
}

internal class AutoMixCanvasTracks(
    val generation: Long,
    val outgoing: QueueEntryIdentity,
    val incoming: QueueEntryIdentity,
    val pinnedClip: CanvasArtwork?,
) {
    var incomingReady by mutableStateOf(false)
    var showStatic by mutableStateOf(false)
    fun contains(entry: QueueEntryIdentity?) = entry == outgoing || entry == incoming
}

@Composable
internal fun rememberAutoMixCanvasPair(
    entry: QueueEntryIdentity?,
    queue: List<Song>,
    isPlaying: Boolean,
    landscape: Boolean,
    fullWindow: Boolean,
): AutoMixCanvasPair {
    val host = PlayerPlatform.host
    val feed = host.autoMixVisualState
    val enabled by PlayerSettings.animatedCanvas.collectAsStateWithLifecycle()
    val metered by PlayerSettings.meteredConnection.collectAsStateWithLifecycle()
    val cellular by PlayerSettings.canvasOverCellular.collectAsStateWithLifecycle()
    val spotifyDisabled by PlayerSettings.disableSpotifyCanvas.collectAsStateWithLifecycle()
    val spotifyFirst by PlayerSettings.prioritizeSpotifyCanvas.collectAsStateWithLifecycle()
    val foreground = rememberIsForeground()
    val allowed = enabled && (metered != true || cellular) && foreground && isPlaying
    val currentEntry by rememberUpdatedState(entry)
    val currentQueue by rememberUpdatedState(queue)
    val pair = remember(feed) { AutoMixCanvasPair() }
    // Rotation and layout changes cancel the effect, but cannot prepare the same generation again.
    LaunchedEffect(feed, allowed, spotifyDisabled, spotifyFirst, landscape, fullWindow) {
        if (feed == null || !host.supportsAutoMixCanvas || !allowed) return@LaunchedEffect
        var active: AutoMixCanvasTracks? = null
        var resolve: Job? = null
        var timeout: Job? = null
        var close: (() -> Unit)? = null
        var sealed = false
        fun stopPreparing() {
            sealed = true
            active?.incomingReady = false
            resolve?.cancel()
            timeout?.cancel()
            close?.invoke()
            close = null
        }
        fun clear() {
            stopPreparing()
            active = null
            pair.clear()
        }
        fun beforeDeadline(state: AutoMixVisualState?): Boolean {
            if (state == null || state.generation != active?.generation || !state.mixActive) return false
            val window = state.artworkWindow ?: return state.swapSeconds == null
            return state.audioSeconds < window.startSeconds - AutoMixArtworkTuning.CANVAS_READY_MARGIN_SECONDS
        }
        try {
            feed.collect { state ->
                if (state == null || !state.mixActive || state.artworkProgress >= 1f ||
                    (state.swapSeconds != null && state.artworkWindow == null)
                ) {
                    if (active != null) clear()
                    return@collect
                }
                if (active?.generation != state.generation) {
                    clear()
                    val tracks = pair.start(state, currentEntry) ?: return@collect
                    active = tracks
                    sealed = false
                    val index = state.incoming.armedIndex
                    val song = currentQueue.getOrNull(index)?.takeIf {
                        coverOccurrence(it, currentQueue, index) == tracks.incoming && !showsMusicVideo(it)
                    } ?: return@collect
                    timeout = launch {
                        delay(AutoMixArtworkTuning.CANVAS_PREPARE_TIMEOUT_MS)
                        if (!tracks.incomingReady) stopPreparing()
                    }
                    resolve = launch {
                        val clip = try {
                            host.cachedCanvas(song) ?: host.canvasFor(song)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        }?.takeUnless { spotifyDisabled && it.source == CanvasSource.SPOTIFY }
                            ?.nowPlayingRendition(fullWindow) ?: return@launch
                        if (sealed || !beforeDeadline(feed.value)) return@launch
                        close = host.prepareAutoMixCanvas(clip) { frame ->
                            if (active !== tracks) return@prepareAutoMixCanvas
                            if (!frame) tracks.incomingReady = false
                            else if (!sealed && beforeDeadline(feed.value)) tracks.incomingReady = true
                        }
                    }
                }
                val tracks = active ?: return@collect
                if (state.swapSeconds != null) tracks.showStatic = true
                if (!sealed && !beforeDeadline(state)) {
                    if (!tracks.incomingReady) stopPreparing() else sealed = true
                }
            }
        } finally {
            clear()
        }
    }
    return pair
}

internal fun CanvasArtwork.nowPlayingRendition(fullWindow: Boolean): CanvasArtwork {
    val tall = portraitUrl
    return if (tall != null && fullWindow) copy(url = tall, fallbackUrl = url, portraitUrl = null) else this
}

@Composable
internal fun RememberDisplayedAutoMixCanvas(
    pair: AutoMixCanvasPair,
    entry: QueueEntryIdentity?,
    canvas: CanvasArtwork?,
    rendered: Boolean,
) {
    SideEffect { pair.displayed(entry, canvas, rendered) }
}
