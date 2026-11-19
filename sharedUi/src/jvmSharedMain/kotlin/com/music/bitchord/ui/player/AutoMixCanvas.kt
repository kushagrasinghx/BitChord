package com.music.bitchord.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.canvas.CanvasSource
import com.music.bitchord.data.model.QueueEntryIdentity
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.AutoMixArtworkTuning
import com.music.bitchord.playback.AutoMixVisualPhase
import com.music.bitchord.playback.AutoMixVisualState
import com.music.bitchord.ui.graphics.smootherStep
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

    fun beginMix(tracks: AutoMixCanvasTracks) {
        tracks.pinnedClip = shownClip.takeIf { shownEntry == tracks.outgoing }
        tracks.outgoingReady = tracks.pinnedClip != null
        if (tracks.sharesClip) {
            tracks.incomingClip = tracks.pinnedClip
            tracks.incomingReady = true
        }
        tracks.started = true
    }

    fun finish(tracks: AutoMixCanvasTracks) {
        promoted = tracks.takeIf { it.liveIncoming }
        clear()
    }

    fun invalidatePromotion() { promoted = null }
}

internal class AutoMixCanvasTracks(
    val generation: Long,
    val outgoing: QueueEntryIdentity,
    val incoming: QueueEntryIdentity,
    pinnedClip: CanvasArtwork?,
) {
    var pinnedClip by mutableStateOf(pinnedClip)
    var started by mutableStateOf(false)
    var incomingClip by mutableStateOf<CanvasArtwork?>(null)
    var incomingReady by mutableStateOf(false)
    var incomingAspect by mutableStateOf(0f)
    private var incomingAccepted by mutableStateOf(false)
    private var choiceSealed by mutableStateOf(false)
    var outgoingReady by mutableStateOf(pinnedClip != null)
    var windowOpen by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
    val liveIncoming get() = incomingAccepted && incomingReady
    val selectedStill get() = choiceSealed && !liveIncoming
    val liveOutgoing get() = started && outgoingReady && pinnedClip != null
    val sharesClip get() = pinnedClip?.url?.let { it == incomingClip?.url } == true

    fun reportIncoming(ready: Boolean) {
        if (!ready && incomingAccepted && incomingReady) {
            // A lost surface or failed decoder may not rejoin this dissolve.
            incomingAccepted = false
            incomingClip = null
        }
        incomingReady = ready
    }

    fun reportOutgoing(ready: Boolean) {
        if (!ready) {
            outgoingReady = false
            if (sharesClip) reportIncoming(false)
        }
    }

    fun reveal(): Float {
        // Subscribe drawing and AndroidView updates even if the live feed has
        // already crossed a boundary that its collector has not applied yet.
        val observed = progress
        val sample = PlayerPlatform.host.autoMixVisualState?.value
        if (sample?.generation != generation || sample.artworkWindow == null) return 0f
        return AutoMixArtworkTuning.revealProgress(observed)
    }

    fun standbyOpacity(): Float = if (windowOpen && liveIncoming) reveal() else 0f

    private val standbyProgress = ::standbyOpacity
    private val spatialStandby = CanvasOrganicReveal(generation, standbyProgress, allowSpatialMask = true)
    private val opacityStandby = CanvasOrganicReveal(generation, standbyProgress, allowSpatialMask = false)

    fun standbyReveal(allowSpatialMask: Boolean): CanvasOrganicReveal =
        if (allowSpatialMask) spatialStandby else opacityStandby

    /** Make the live/still choice once; a late frame cannot change it. */
    fun sealIncoming(): Boolean {
        if (!choiceSealed) {
            choiceSealed = true
            incomingAccepted = incomingReady && incomingClip != null
        }
        return incomingAccepted
    }

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
            active?.sealIncoming()
            resolve?.cancel()
            timeout?.cancel()
        }
        fun clear() {
            stopPreparing()
            active = null
            pair.clear()
        }
        fun beforeDeadline(state: AutoMixVisualState?): Boolean {
            if (state == null || state.generation != active?.generation) return false
            if (state.phase == AutoMixVisualPhase.ARMED) return true
            if (!state.mixActive) return false
            val window = state.artworkWindow ?: return state.swapSeconds == null
            return state.audioSeconds < window.startSeconds - AutoMixArtworkTuning.CANVAS_READY_MARGIN_SECONDS
        }
        try {
            launch {
                snapshotFlow { currentEntry }.collect { selected ->
                    if (active?.contains(selected) == false) clear()
                }
            }
            feed.collect { state ->
                if (state == null || (!state.mixActive && state.phase != AutoMixVisualPhase.ARMED) ||
                    state.artworkProgress >= 1f ||
                    (state.swapSeconds != null && state.artworkWindow == null)
                ) {
                    val tracks = active
                    if (tracks != null && state?.generation == tracks.generation &&
                        state.artworkWindow != null &&
                        (state.artworkProgress >= 1f || state.phase == AutoMixVisualPhase.COMPLETED) &&
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
                        // The fullscreen slot can be prepared behind the square sleeve. The
                        // reverse would require putting a square view into a banner's parent.
                        if (!landscape && fullWindow && outgoingHero && !incomingHero) return@launch
                        tracks.incomingClip = tracks.pinnedClip?.takeIf { it.url == clip.url } ?: clip
                        if (clip.url == tracks.pinnedClip?.url) tracks.incomingReady = true
                    }
                }
                val tracks = active ?: return@collect
                if (state.phase == AutoMixVisualPhase.ARMED) return@collect
                if (!tracks.started) {
                    pair.beginMix(tracks)
                    timeout = launch {
                        delay(AutoMixArtworkTuning.CANVAS_PREPARE_TIMEOUT_MS)
                        if (active === tracks && !sealed && !tracks.incomingReady) stopPreparing()
                    }
                }
                tracks.progress = state.artworkProgress
                if (!sealed && !beforeDeadline(state)) {
                    if (!tracks.sealIncoming()) stopPreparing() else sealed = true
                }
                tracks.windowOpen = state.artworkWindow != null
            }
        } finally {
            clear()
        }
    }
    return pair
}

/** Keep the still/still shader out of any dissolve with a live layer. */
internal fun AutoMixCoverFrames?.baseUnderCanvas(pair: AutoMixCanvasTracks?): AutoMixCoverFrames? =
    if (this != null && pair != null && (pair.liveOutgoing || pair.liveIncoming)) AutoMixCoverFrames(base)
    else this

/** Retire the part of the outgoing picture not covered by the destination (bounds or bottom fade). */
internal fun outgoingArtworkTailAlpha(progress: Float): Float {
    val start = 0.8f
    return 1f - smootherStep(((progress - start) / (1f - start)).coerceIn(0f, 1f))
}

/** Local picture bounds after the same centered/top-aligned fit used by the live texture. */
fun canvasRevealFrame(width: Int, height: Int, aspect: Float, fitPortrait: Boolean, alignTop: Boolean): Rect {
    val w = width.toFloat()
    val h = height.toFloat()
    if (w <= 0f || h <= 0f || !fitPortrait || !aspect.isFinite() || aspect <= 0f || aspect >= 1f) {
        return Rect(0f, 0f, w, h)
    }
    val pictureWidth = minOf(w, h * aspect)
    val pictureHeight = minOf(h, w / aspect)
    val left = (w - pictureWidth) * 0.5f
    val top = if (alignTop) 0f else (h - pictureHeight) * 0.5f
    return Rect(left, top, left + pictureWidth, top + pictureHeight)
}

/** Full-window crops, fixed-height cropped banners, and identical contained fits match. */
internal fun sharesHeroRevealFootprint(
    outgoingFullscreen: Boolean,
    incomingFullscreen: Boolean,
    outgoingAspect: Float,
    incomingAspect: Float,
): Boolean = if (outgoingFullscreen || incomingFullscreen) {
    outgoingFullscreen && incomingFullscreen
} else {
    outgoingAspect.isFinite() && incomingAspect.isFinite() && outgoingAspect > 0f && incomingAspect > 0f &&
        ((outgoingAspect >= 1f && incomingAspect >= 1f) || outgoingAspect == incomingAspect)
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
