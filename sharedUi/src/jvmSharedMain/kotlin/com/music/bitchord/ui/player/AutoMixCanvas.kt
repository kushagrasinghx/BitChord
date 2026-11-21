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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.IntSize
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

    fun start(
        state: AutoMixVisualState,
        entry: QueueEntryIdentity?,
        gate: AutoMixArtworkGate = AutoMixArtworkGate(),
    ): AutoMixCanvasTracks? {
        if (!gate.allows(state.generation) || seenGeneration == state.generation || entry != state.outgoing.identity) return null
        // A not-yet-bound UI selection must not consume the generation.
        seenGeneration = state.generation
        return AutoMixCanvasTracks(
            state.generation, state.outgoing.identity, state.incoming.identity,
            shownClip.takeIf { shownEntry == entry }?.takeUnless { it.retiresBeforeAutoMix },
        ).also { visual = it }
    }

    fun clear() {
        visual = null
    }

    fun beginMix(tracks: AutoMixCanvasTracks) {
        val shown = shownClip.takeIf { shownEntry == tracks.outgoing }
        // A Spotify Canvas is the whole phone screen, not a slot either picture can
        // share. It fades back to its cover before the reveal, which then runs on stills.
        tracks.retiringClip = shown?.takeIf { it.retiresBeforeAutoMix }
        tracks.pinnedClip = shown?.takeUnless { it.retiresBeforeAutoMix }
        tracks.outgoingReady = tracks.pinnedClip != null
        if (tracks.sharesClip) {
            tracks.incomingClip = tracks.pinnedClip
            tracks.incomingReady = true
        }
        tracks.started = true
    }

    fun finish(tracks: AutoMixCanvasTracks) {
        tracks.gestureOutgoing = null
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
    private val visualSample: () -> AutoMixVisualState? = { PlayerPlatform.host.autoMixVisualState?.value },
) {
    var pinnedClip by mutableStateOf(pinnedClip)
    /** An outgoing Spotify Canvas fading out to its cover; never part of the reveal. */
    var retiringClip by mutableStateOf<CanvasArtwork?>(null)
    /** Hold the outgoing clip on its current frame: the reveal animates that paused frame. */
    var freezeOutgoing by mutableStateOf(false)
    var started by mutableStateOf(false)
    var incomingClip by mutableStateOf<CanvasArtwork?>(null)
    var incomingReady by mutableStateOf(false)
    var incomingAspect by mutableStateOf(0f)
    private var incomingAccepted by mutableStateOf(false)
    private var choiceSealed by mutableStateOf(false)
    var outgoingReady by mutableStateOf(pinnedClip != null)
    var windowOpen by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
    var gestureOutgoing by mutableStateOf<CanvasStill?>(null)
    var gestureIncoming by mutableStateOf<CanvasStill?>(null)
    val liveIncoming get() = incomingAccepted && incomingReady
    val selectedStill get() = choiceSealed && !liveIncoming
    val liveOutgoing get() = started && outgoingReady && pinnedClip != null
    val sharesClip get() = pinnedClip?.url?.let { it == incomingClip?.url } == true

    fun beforeIncomingDeadline(state: AutoMixVisualState?): Boolean {
        if (state == null || state.generation != generation) return false
        if (state.phase == AutoMixVisualPhase.ARMED) return true
        if (!state.mixActive) return false
        val window = state.artworkWindow ?: return state.swapSeconds == null
        return state.audioSeconds < window.startSeconds - AutoMixArtworkTuning.CANVAS_READY_MARGIN_SECONDS
    }

    fun reportIncoming(ready: Boolean) {
        // A first-frame callback can run before the collector seals the choice,
        // even though the live audio sample has already passed the deadline.
        if (ready && (choiceSealed || incomingReady || !beforeIncomingDeadline(visualSample()))) return
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
        // Keep the snapshot read for draw/AndroidView invalidation. When drawing
        // is already scheduled, don't paint the collector's older audio sample.
        val observed = progress
        val sample = visualSample()
        if (sample?.generation != generation || sample.artworkWindow == null) return 0f
        return AutoMixArtworkTuning.revealProgress(
            AutoMixRevealClock.progress(sample, maxOf(observed, sample.artworkProgress)),
        )
    }

    fun standbyOpacity(): Float = if (windowOpen && liveIncoming) reveal() else 0f

    private val standbyProgress = ::standbyOpacity
    private val spatialStandby = CanvasOrganicReveal(generation, standbyProgress, allowSpatialMask = true)
    private val opacityStandby = CanvasOrganicReveal(generation, standbyProgress, allowSpatialMask = false)

    fun standbyReveal(allowSpatialMask: Boolean): CanvasOrganicReveal =
        if (allowSpatialMask) spatialStandby else opacityStandby

    fun outgoingStillBannerReveal(
        frames: AutoMixCoverFrames?,
        bounds: IntSize,
        bottomFade: Float,
        presentationAlpha: () -> Float,
    ): CanvasOrganicReveal? {
        if (!liveOutgoing || liveIncoming || frames?.generation != generation || frames.incoming == null ||
            bounds.width <= 0 || bounds.height <= 0
        ) return null
        return CanvasOrganicReveal(generation, ::reveal, true, CanvasStillRevealTarget(bounds, bottomFade, presentationAlpha))
    }

    /** Make the live/still choice once; a late frame cannot change it. */
    fun sealIncoming(): Boolean {
        if (!choiceSealed) {
            choiceSealed = true
            incomingAccepted = incomingReady && incomingClip != null
        }
        return incomingAccepted
    }

    fun contains(entry: QueueEntryIdentity?) = entry == outgoing || entry == incoming

    fun usesGestureSleeve(gesturing: Boolean): Boolean = gesturing && started &&
        (pinnedClip != null || incomingClip != null || gestureIncoming != null)
}

@Composable
internal fun rememberAutoMixCanvasPair(
    entry: QueueEntryIdentity?,
    queue: List<Song>,
    isPlaying: Boolean,
    landscape: Boolean,
    fullWindow: Boolean,
    fullBleed: Boolean,
    artworkGate: AutoMixArtworkGate,
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
    LaunchedEffect(feed, allowed, spotifyDisabled, spotifyFirst, landscape, fullWindow, fullBleed, artworkGate) {
        if (feed == null || !host.supportsAutoMixCanvas || !allowed || !artworkGate.enabled) return@LaunchedEffect
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
                    val tracks = pair.start(state, currentEntry, artworkGate) ?: return@collect
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
                            // An incoming Spotify Canvas reveals as its cover; the ordinary
                            // selected-track path brings the clip in once the mix has promoted.
                            ?.takeUnless { it.retiresBeforeAutoMix }
                            ?.nowPlayingRendition(fullWindow) ?: return@launch
                        if (sealed || active !== tracks || !tracks.beforeIncomingDeadline(feed.value)) return@launch
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
                val window = state.artworkWindow
                if (!tracks.freezeOutgoing && window != null &&
                    state.audioSeconds >= window.startSeconds - AutoMixArtworkTuning.CANVAS_READY_MARGIN_SECONDS
                ) tracks.freezeOutgoing = true
                if (!sealed && !tracks.beforeIncomingDeadline(state)) {
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

/** Spotify's full-screen Canvas never takes part in an AutoMix reveal; see [AutoMixCanvasPair.beginMix]. */
internal val CanvasArtwork.retiresBeforeAutoMix: Boolean get() = source == CanvasSource.SPOTIFY

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
