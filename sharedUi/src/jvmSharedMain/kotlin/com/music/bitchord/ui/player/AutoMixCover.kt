package com.music.bitchord.ui.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.BitmapImage
import coil3.Image as CoilImage
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.asPainter
import coil3.request.SuccessResult
import com.music.bitchord.data.model.QueueEntryIdentity
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.AutoMixArtworkTuning
import com.music.bitchord.playback.AutoMixVisualPhase
import com.music.bitchord.playback.AutoMixVisualState

/** Hold pixels without retaining a request's platform context. */
internal class DecodedAutoMixCover(
    val image: CoilImage,
    val painter: Painter? = null,
)

internal fun SuccessResult.decodedAutoMixCover(painter: Painter? = null): DecodedAutoMixCover? {
    val bitmap = image as? BitmapImage ?: return null
    return DecodedAutoMixCover(bitmap, painter)
}

/** The actual Phase 1 results, retained separately by surface size. Main only. */
internal object AutoMixPreparedCover {
    private var latest: Pair<Long, DecodedAutoMixCover>? = null
    private var latestMini: Pair<Long, DecodedAutoMixCover>? = null

    fun retain(generation: Long, result: SuccessResult) {
        latest = result.decodedAutoMixCover()?.let { generation to it }
    }

    fun forGeneration(generation: Long): DecodedAutoMixCover? =
        latest?.takeIf { it.first == generation }?.second

    fun retainMini(generation: Long, result: SuccessResult) {
        latestMini = result.decodedAutoMixCover()?.let { generation to it }
    }

    fun miniForGeneration(generation: Long): DecodedAutoMixCover? =
        latestMini?.takeIf { it.first == generation }?.second

    fun clear() {
        latest = null
        latestMini = null
    }
}

internal fun coverOccurrence(song: Song, queue: List<Song>, index: Int): QueueEntryIdentity? {
    val current = queue.getOrNull(index) ?: return null
    if (current.videoId != song.videoId || current.queueEntryId != song.queueEntryId) return null
    val ordinal = (0 until index).count {
        queue[it].videoId == song.videoId && queue[it].queueEntryId == song.queueEntryId
    }
    return QueueEntryIdentity(song.videoId, song.queueEntryId, ordinal)
}

internal class AutoMixCoverState(
    private val miniOutgoing: ((AutoMixVisualState) -> DecodedAutoMixCover?)? = null,
    private val preparedIncoming: (Long) -> DecodedAutoMixCover? = if (miniOutgoing == null) {
        AutoMixPreparedCover::forGeneration
    } else AutoMixPreparedCover::miniForGeneration,
) {
    private class PairFrames(
        val generation: Long,
        val outgoing: QueueEntryIdentity,
        val incoming: QueueEntryIdentity,
        val frames: AutoMixCoverFrames,
        val remoteArtworkScope: Long?,
    )

    private class Promoted(val entry: QueueEntryIdentity, val frames: AutoMixCoverFrames, val remoteArtworkScope: Long?)

    private var current: QueueEntryIdentity? = null
    private var currentArt: PlayerArtwork? = null
    private var armedArt: PlayerArtwork? = null
    private var armedCover: DecodedAutoMixCover? = null
    private var armedGeneration: Long? = null
    private var seenMix: Long? = null
    private var frozen: PairFrames? = null
    private var active by mutableStateOf<PairFrames?>(null)
    private var promoted by mutableStateOf<Promoted?>(null)
    private var progress by mutableFloatStateOf(0f)
    private var latest: AutoMixVisualState? = null

    fun bind(occurrence: QueueEntryIdentity?, art: PlayerArtwork?, visual: AutoMixVisualState?, gate: AutoMixArtworkGate = AutoMixArtworkGate()) {
        if (promoted?.let { !gate.matchesScope(it.remoteArtworkScope) } == true) promoted = null
        if (current != occurrence) {
            // Session metadata can move before the collector consumes the mix.
            // Retain this exact outgoing occurrence, not a general previous track.
            if (visual?.mixActive == true && visual.outgoing.identity == current && visual.incoming.identity == occurrence &&
                seenMix != visual.generation
            ) {
                armedGeneration = visual.generation
                armedArt = currentArt
                armedCover = promoted?.takeIf { it.entry == current }?.frames?.base
            }
            if (promoted?.entry == current) promoted = null
        }
        current = occurrence
        currentArt = art
        if (visual?.phase == AutoMixVisualPhase.ARMED && visual.outgoing.identity == current) {
            armedGeneration = visual.generation
            armedArt = art
            armedCover = promoted?.takeIf { it.entry == current }?.frames?.base
        }
    }

    fun update(visual: AutoMixVisualState?, gate: AutoMixArtworkGate = AutoMixArtworkGate()) {
        latest = visual
        if (promoted?.let { !gate.matchesScope(it.remoteArtworkScope) } == true) promoted = null
        if (visual == null || visual.phase == AutoMixVisualPhase.CANCELLED || !gate.allows(visual) ||
            !gate.matchesScope(visual.remoteArtworkScope)
        ) {
            if (visual != null) seenMix = visual.generation
            val pair = active
            // If the session already owns a decoded member of the pair, snap to it
            // without a Coil arrival fade. A new occurrence ignores this single frame.
            if (pair != null && visual?.generation == pair.generation && gate.matchesScope(pair.remoteArtworkScope)) {
                val selection = current
                if (selection != null) pair.selectedFrames(selection)?.let {
                    promoted = Promoted(selection, it, pair.remoteArtworkScope)
                }
            }
            active = null
            frozen = null
            armedArt = null
            armedCover = null
            armedGeneration = null
            return
        }
        when (visual.phase) {
            AutoMixVisualPhase.ARMED -> {
                active = null
                frozen = null
                armedGeneration = visual.generation
                armedArt = currentArt.takeIf { visual.outgoing.identity == current }
                armedCover = promoted?.takeIf { it.entry == visual.outgoing.identity }?.frames?.base
            }
            AutoMixVisualPhase.MIXING -> {
                if (seenMix != visual.generation) {
                    // Choose once from pixels prepared before the overlap. Do not change
                    // the layer stack if a decode finishes after this generation starts.
                    seenMix = visual.generation
                    val outgoing = promoted?.takeIf { it.entry == visual.outgoing.identity }?.frames?.base
                        ?: armedCover.takeIf { armedGeneration == visual.generation }
                        ?: miniOutgoing?.invoke(visual)
                        ?: (armedArt.takeIf { armedGeneration == visual.generation }
                            ?: currentArt.takeIf { visual.outgoing.identity == current })
                            ?.decodedCoverBefore(visual.generation)
                    val incoming = preparedIncoming(visual.generation)
                    frozen = if (outgoing != null && incoming != null && visual.artworkProgress < 1f) {
                        PairFrames(
                            visual.generation, visual.outgoing.identity, visual.incoming.identity,
                            AutoMixCoverFrames(outgoing, incoming, visual.generation) {
                                AutoMixRevealClock.progress(latest?.takeIf { it.generation == visual.generation }, progress)
                            },
                            visual.remoteArtworkScope,
                        )
                    } else null
                    armedArt = null
                    armedCover = null
                }
                if (visual.swapSeconds != null && visual.artworkWindow == null) frozen = null
                val pair = frozen
                progress = visual.artworkProgress
                if (pair != null && visual.artworkWindow != null && visual.artworkProgress >= 1f) {
                    promote(pair)
                } else {
                    // The session may publish its new item before the clock sample
                    // for the swap. Keep the outgoing pixels pinned at zero through it.
                    active = pair
                }
            }
            AutoMixVisualPhase.COMPLETED -> {
                seenMix = visual.generation
                // Completion may be conflated with the final progress tick.
                frozen?.takeIf { it.generation == visual.generation && visual.artworkWindow != null }
                    ?.let(::promote)
                active = null
                frozen = null
                armedArt = null
                armedCover = null
            }
            AutoMixVisualPhase.CANCELLED -> Unit // handled above
        }
    }

    private fun promote(pair: PairFrames) {
        val incoming = pair.frames.incoming ?: return
        promoted = Promoted(pair.incoming, AutoMixCoverFrames(incoming), pair.remoteArtworkScope)
        active = null
        frozen = null
    }

    private fun PairFrames.selectedFrames(occurrence: QueueEntryIdentity?): AutoMixCoverFrames? =
        when (occurrence) {
            outgoing -> AutoMixCoverFrames(frames.base)
            incoming -> frames.incoming?.let { AutoMixCoverFrames(it) }
            else -> null
        }

    fun framesFor(occurrence: QueueEntryIdentity?, gate: AutoMixArtworkGate = AutoMixArtworkGate()): AutoMixCoverFrames? {
        val pair = active
        if (pair != null && (occurrence == pair.outgoing || occurrence == pair.incoming)) {
            if (!gate.matchesScope(pair.remoteArtworkScope)) return null
            return if (gate.allows(pair.generation)) pair.frames else pair.selectedFrames(occurrence)
        }
        return promoted?.takeIf { it.entry == occurrence && gate.matchesScope(it.remoteArtworkScope) }?.frames
    }
}

@Composable
internal fun rememberAutoMixCover(
    occurrence: QueueEntryIdentity?,
    art: PlayerArtwork,
    gate: AutoMixArtworkGate,
): AutoMixCoverState {
    val feed = PlayerPlatform.host.autoMixVisualState
    val cover = remember(feed) { AutoMixCoverState() }
    SideEffect {
        cover.bind(occurrence, art, feed?.value?.takeIf { gate.allows(it) && gate.matchesScope(it.remoteArtworkScope) }, gate)
    }
    LaunchedEffect(feed, cover, gate) { feed?.collect { cover.update(it, gate) } }
    AutoMixRevealClockEffect(gate)
    return cover
}

private class DisplayedMiniCovers {
    private class Frame(
        val occurrence: QueueEntryIdentity,
        val cover: DecodedAutoMixCover,
        val decodedDuring: Long?,
        val remoteArtworkScope: Long?,
    )

    private var latest: Frame? = null
    private var previous: Frame? = null

    fun before(visual: AutoMixVisualState): DecodedAutoMixCover? =
        latest.eligibleFor(visual)
            // The new row may report a success before the collector sees the mix.
            ?: previous.eligibleFor(visual).takeIf { latest?.decodedDuring == visual.generation }

    private fun Frame?.eligibleFor(visual: AutoMixVisualState): DecodedAutoMixCover? =
        this?.takeIf {
            it.occurrence == visual.outgoing.identity && it.decodedDuring != visual.generation &&
                (it.remoteArtworkScope == null || it.remoteArtworkScope == visual.remoteArtworkScope)
        }?.cover

    fun rememberSuccess(
        occurrence: QueueEntryIdentity, state: AsyncImagePainter.State.Success, visual: AutoMixVisualState?, remoteArtworkScope: Long?,
    ) {
        // Coil's success painter is the decoded endpoint, not its arrival crossfade.
        val cover = state.result.decodedAutoMixCover(state.painter) ?: return
        val active = visual?.takeIf { it.mixActive }
        if (active != null && active.outgoing.identity == occurrence && before(active) != null) return
        if (latest?.occurrence != occurrence) previous = latest
        latest = Frame(occurrence, cover, active?.generation, remoteArtworkScope)
    }
}

/** Own once above the mini layouts; invoke the returned artwork slot inside their existing frames. */
@Composable
fun rememberMiniAutoMixArtwork(
    song: Song?,
    queue: List<Song>,
    index: Int,
): @Composable (String?, Modifier) -> Unit {
    val occurrence = remember(song?.videoId, song?.queueEntryId, queue, index) {
        song?.let { coverOccurrence(it, queue, index) }
    }
    val feed = PlayerPlatform.host.autoMixVisualState
    val displayed = remember(feed) { DisplayedMiniCovers() }
    val cover = remember(feed) { AutoMixCoverState(displayed::before) }
    val gate = autoMixArtworkGate(mini = true)
    val remoteScope = gate.remoteArtworkScope.takeIf {
        song?.videoId?.let { id -> id.startsWith("webdav:") || id.startsWith("smb:") } == true
    }
    SideEffect {
        cover.bind(occurrence, null, feed?.value?.takeIf { gate.allows(it) && gate.matchesScope(it.remoteArtworkScope) }, gate)
    }
    LaunchedEffect(feed, cover, gate) { feed?.collect { cover.update(it, gate) } }
    AutoMixRevealClockEffect(gate)

    return { model, modifier ->
        AutoMixCover(cover.framesFor(occurrence, gate), modifier, blur = false) { artworkModifier ->
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { state ->
                    if (occurrence != null && state.result.request.data == model) {
                        displayed.rememberSuccess(occurrence, state, feed?.value, remoteScope)
                    }
                },
                modifier = artworkModifier,
            )
        }
    }
}

internal class AutoMixCoverFrames(
    val base: DecodedAutoMixCover,
    val incoming: DecodedAutoMixCover? = null,
    val generation: Long = 0L,
    val progress: () -> Float = { 0f },
)

/** The frame, clip, shadow and any still/video masks belong to the caller. */
@Composable
internal fun AutoMixCover(
    frames: AutoMixCoverFrames?,
    modifier: Modifier,
    blur: Boolean,
    fallback: @Composable (Modifier) -> Unit,
) {
    if (frames == null) {
        fallback(modifier)
        return
    }
    val base = frames.base
    val incoming = frames.incoming
    Box(modifier) {
        if (incoming == null) {
            DecodedCover(base, Modifier.fillMaxSize())
        } else {
            val eased = { AutoMixArtworkTuning.revealProgress(frames.progress()) }
            DecodedCover(base, Modifier.fillMaxSize())
            AutoMixIncomingStill(incoming, eased, frames.generation, blur = blur)
        }
    }
}

/** Only the incoming still is isolated by the effect, never the outgoing Canvas. */
@Composable
internal fun AutoMixIncomingStill(
    result: DecodedAutoMixCover,
    eased: () -> Float,
    generation: Long,
    polish: Boolean = true,
    blur: Boolean = true,
) {
    DecodedCover(result, Modifier.fillMaxSize().then(autoMixIncomingMask(eased, generation, polish, blur)))
}

@Composable
private fun autoMixIncomingMask(eased: () -> Float, generation: Long, polish: Boolean, blur: Boolean): Modifier {
    val appReduced = PlayerSettings.reduceAnimation.collectAsState()
    val systemMotion = rememberCoroutineScope().coroutineContext[MotionDurationScale]
    val reducedMotion = { appReduced.value || systemMotion?.scaleFactor == 0f }
    return if (polish) PlayerPlatform.host.autoMixIncomingReveal(eased, reducedMotion, generation, blur)
    else Modifier.graphicsLayer { alpha = eased() }
}

/** The sleeve measures and clips both pictures, then feathers the composite once. */
@Composable
internal fun AutoMixGestureCover(
    art: PlayerArtwork,
    frames: AutoMixCoverFrames?,
    pair: AutoMixCanvasTracks,
    outgoing: () -> ImageBitmap?,
) {
    val matching = frames?.takeIf { it.generation == pair.generation }
    Box(Modifier.fillMaxSize()) {
        AutoMixPlayerCover(art, matching?.let { AutoMixCoverFrames(it.base) }, Modifier.fillMaxSize())
        outgoing()?.let {
            Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        val incoming = pair.gestureIncoming?.takeIf { pair.liveIncoming && !pair.sharesClip }?.image
        if (incoming != null) {
            val mask = autoMixIncomingMask(pair::reveal, pair.generation, polish = true, blur = true)
            Image(incoming, contentDescription = null, modifier = Modifier.fillMaxSize().then(mask), contentScale = ContentScale.Crop)
        } else if (!pair.sharesClip) {
            matching?.incoming?.let { AutoMixIncomingStill(it, pair::reveal, pair.generation) }
        }
    }
}

@Composable
internal fun DecodedCover(result: DecodedAutoMixCover, modifier: Modifier) {
    val context = LocalPlatformContext.current
    val painter = result.painter ?: remember(result.image, context) { result.image.asPainter(context) }
    Image(painter, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
}

@Composable
internal fun AutoMixPlayerCover(art: PlayerArtwork, frames: AutoMixCoverFrames?, modifier: Modifier) {
    AutoMixCover(frames, modifier, blur = true) { artworkModifier ->
        val prepared = art.prepared
        if (prepared != null) {
            DecodedCover(prepared, artworkModifier)
        } else key(art.url) {
            // A new source starts without an old painter, even before Coil calls onStart.
            AsyncImage(
                model = art.request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = art::onState,
                modifier = artworkModifier,
            )
        }
    }
}
