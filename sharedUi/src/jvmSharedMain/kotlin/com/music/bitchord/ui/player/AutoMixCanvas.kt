package com.music.bitchord.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.music.bitchord.playback.AutoMixVisualPhase
import com.music.bitchord.playback.AutoMixVisualState
import com.music.bitchord.ui.rememberIsForeground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class AutoMixCanvasPair {
    var visual by mutableStateOf<AutoMixCanvasTracks?>(null)
        private set
    var promoted by mutableStateOf<AutoMixCanvasTracks?>(null)
        private set
    private var shownEntry: QueueEntryIdentity? = null
    private var shownClip: CanvasArtwork? = null
    private var seenGeneration: Long? = null

    fun displayed(entry: QueueEntryIdentity?, clip: CanvasArtwork?, rendered: Boolean) {
        if (promoted?.incoming != entry) promoted = null
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

    fun finish(tracks: AutoMixCanvasTracks) {
        if (tracks.incomingReady) promoted = tracks
        clear()
    }

    fun invalidatePromotion() { promoted = null }
}

internal class AutoMixCanvasTracks(
    val generation: Long,
    val outgoing: QueueEntryIdentity,
    val incoming: QueueEntryIdentity,
    val pinnedClip: CanvasArtwork?,
) {
    var incomingClip by mutableStateOf<CanvasArtwork?>(null)
    var incomingReady by mutableStateOf(false)
    var incomingAspect by mutableStateOf(0f)
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
    fullBleed: Boolean,
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
    val canvasAllowed = enabled && (metered != true || cellular)
    LaunchedEffect(canvasAllowed, spotifyDisabled, spotifyFirst, landscape, fullWindow, fullBleed) {
        pair.invalidatePromotion()
    }
    // Rotation and layout changes cancel the effect, but cannot prepare the same generation again.
    LaunchedEffect(feed, allowed, spotifyDisabled, spotifyFirst, landscape, fullWindow, fullBleed) {
        if (feed == null || !host.supportsAutoMixCanvas || !allowed) return@LaunchedEffect
        var active: AutoMixCanvasTracks? = null
        var resolve: Job? = null
        var timeout: Job? = null
        var sealed = false
        fun stopPreparing() {
            sealed = true
            active?.incomingReady = false
            active?.incomingClip = null
            resolve?.cancel()
            timeout?.cancel()
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
                    val tracks = active
                    if (tracks != null && state?.generation == tracks.generation &&
                        state.artworkProgress >= 1f && state.phase != AutoMixVisualPhase.CANCELLED &&
                        currentEntry == tracks.incoming
                    ) {
                        resolve?.cancel()
                        timeout?.cancel()
                        active = null
                        pair.finish(tracks)
                    } else if (tracks != null) clear()
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
                        val outgoingHero = fullBleed || tracks.pinnedClip?.source == CanvasSource.SPOTIFY
                        val incomingHero = fullBleed || clip.source == CanvasSource.SPOTIFY
                        if (!landscape && fullWindow && outgoingHero != incomingHero) return@launch
                        tracks.incomingClip = clip
                        if (clip.url == tracks.pinnedClip?.url) tracks.incomingReady = true
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

/** Both videos use one keyed call site within the same artwork layout. */
@Composable
internal fun CanvasPresentationHost(
    current: CanvasArtwork?,
    standby: CanvasArtwork? = null,
    render: @Composable (CanvasArtwork, Boolean) -> Unit,
) {
    val clips = listOfNotNull(current, standby?.takeUnless { it.url == current?.url })
    for (clip in clips) key(clip.url) { render(clip, clip.url != current?.url) }
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
