package com.music.bitchord.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import com.music.bitchord.data.model.QueueEntryIdentity
import com.music.bitchord.playback.AutoMixArtworkTuning
import com.music.bitchord.playback.AutoMixVisualPhase
import com.music.bitchord.playback.AutoMixVisualState

/** Main-only: retain the actual prepared results, independently of Coil and the URL caches. */
internal class PreparedAutoMixAmbient(val generation: Long, val resolvedUrl: String) {
    var mesh: ArtworkMesh? = null
    var blur: ImageBitmap? = null
    var palette: MeshPalette? = null
}

internal var preparedAutoMixAmbient: PreparedAutoMixAmbient? = null

/** Drop transition-only assets without evicting ordinary URL caches. */
fun discardBlockedAutoMixAmbient(allows: (Long) -> Boolean) {
    if (preparedAutoMixAmbient?.let { !allows(it.generation) } == true) preparedAutoMixAmbient = null
}

internal class AutoMixAmbientMix(
    val generation: Long,
    val outgoing: QueueEntryIdentity,
    val outgoingUrl: String?,
    val prepared: PreparedAutoMixAmbient?,
    val ease: () -> Float,
)

internal class AutoMixAmbient(
    val mix: AutoMixAmbientMix? = null,
    val settled: Pair<Long, QueueEntryIdentity>? = null,
    val incoming: PreparedAutoMixAmbient? = null,
    val cancelled: Boolean = false,
    val direct: Pair<Long, QueueEntryIdentity>? = null,
) {
    fun mixFor(targetReady: Boolean): AutoMixAmbientMix? = mix?.takeUnless {
        targetReady && settled?.first == it.generation
    }

    // Only the enabled transition's promotion has a composition reset epoch.
    fun snapKey(targetReady: Boolean): Pair<Long, QueueEntryIdentity>? = settled.takeIf { cancelled || targetReady }
}

/** Keep the reset epoch after leaving a promoted track, so the next ordinary skip still tweens. */
@Composable
internal fun <T : Any> rememberAmbientSnapKey(requested: T?): T? {
    val previous = remember { mutableStateOf<T?>(null) }
    SideEffect { if (requested != null) previous.value = requested }
    return requested ?: previous.value
}

private class AmbientArtworkHistory {
    var shown: Pair<QueueEntryIdentity, String>? = null
    var previous: Pair<QueueEntryIdentity, String>? = null
    var settled: Pair<QueueEntryIdentity, AutoMixAmbient>? = null
    var direct: Pair<Long, QueueEntryIdentity>? = null
    var lastEntry: QueueEntryIdentity? = null
    var mixingGeneration: Long? = null
    var closedGeneration: Long? = null

    fun note(entry: QueueEntryIdentity, url: String) {
        if (shown?.first != entry) previous = shown
        shown = entry to url
    }

    fun urlFor(entry: QueueEntryIdentity): String? = when (entry) {
        shown?.first -> shown?.second
        previous?.first -> previous?.second
        else -> null
    }
}

private data class AmbientBoundary(
    val generation: Long,
    val phase: AutoMixVisualPhase,
    val windowEnded: Boolean,
    val noDissolve: Boolean,
)

private fun AutoMixVisualState.boundary() = AmbientBoundary(
    generation, phase, artworkProgress >= 1f, swapSeconds != null && artworkWindow == null,
)

/** Keep a direct handoff tied to its selection, so late pixels cannot restart a fade. */
internal fun directAutoMixAmbientSelection(
    visual: AutoMixVisualState?, entry: QueueEntryIdentity?, gate: AutoMixArtworkGate,
    previous: Pair<Long, QueueEntryIdentity>?,
    lastEntry: QueueEntryIdentity? = null,
): Pair<Long, QueueEntryIdentity>? {
    if (entry == null) return null
    if (visual != null && (entry == visual.outgoing.identity || entry == visual.incoming.identity)) {
        if (visual.mixActive) return if (gate.allows(visual.generation)) null else visual.generation to entry
        // A terminal sample can overtake the selection update. Don't treat a
        // terminal feed left behind during later navigation as a new handoff.
        if (visual.swapSeconds != null && !gate.allows(visual.generation) && entry == visual.incoming.identity &&
            (previous?.first == visual.generation || lastEntry == visual.outgoing.identity)
        ) return visual.generation to entry
    }
    return previous?.takeIf { it.second == entry }
}

/** Only lifecycle boundaries invalidate composition; the audio sample itself is read in drawing. */
@Composable
internal fun rememberAutoMixAmbient(entry: QueueEntryIdentity?, resolvedUrl: String?, gate: AutoMixArtworkGate): AutoMixAmbient {
    val feed = PlayerPlatform.host.autoMixVisualState ?: return AutoMixAmbient()
    val history = remember(feed) { AmbientArtworkHistory() }
    val currentEntry = rememberUpdatedState(entry)
    val sample = remember(feed) { mutableStateOf(feed.value) }
    val boundary = remember(feed) { mutableStateOf(feed.value?.boundary()) }
    LaunchedEffect(feed, gate) {
        feed.collect {
            sample.value = it?.takeIf { state -> gate.allows(state.generation) }
            boundary.value = it?.boundary()
        }
    }

    // Also sample synchronously when session metadata recomposes first. The boundary state
    // is only an invalidation signal; it does not own the ordering of the two feeds.
    val observedBoundary = boundary.value
    val visual = feed.value
    val direct = directAutoMixAmbientSelection(
        visual, entry, gate, history.direct ?: history.settled?.second?.settled?.takeUnless { gate.allows(it.first) },
        history.lastEntry.takeUnless { visual?.generation == history.closedGeneration },
    )
    val stage = visual?.takeIf { gate.allows(it.generation) }?.boundary()
        .let { if (it == observedBoundary) observedBoundary else it }
    val prepared = preparedAutoMixAmbient?.takeIf { it.generation == stage?.generation }
    val holdsPair = stage?.phase == AutoMixVisualPhase.MIXING && stage.generation != history.closedGeneration
    val pair = remember(stage?.generation, holdsPair) {
        if (!holdsPair || visual == null) null else AutoMixAmbientMix(
            generation = visual.generation,
            outgoing = visual.outgoing.identity,
            outgoingUrl = history.urlFor(visual.outgoing.identity)
                ?: resolvedUrl.takeIf { entry == visual.outgoing.identity }
                ?: visual.outgoing.artworkUrl,
            prepared = prepared,
            ease = {
                // Observe the clock for redraw, but let cancellation win even before collection.
                val observed = sample.value
                val live = feed.value
                when {
                    live == null || live.generation != visual.generation -> 0f
                    live.phase == AutoMixVisualPhase.CANCELLED ->
                        if (currentEntry.value == visual.incoming.identity) 1f else 0f
                    live.artworkWindow == null || observed?.generation != live.generation -> 0f
                    live.phase == AutoMixVisualPhase.COMPLETED -> 1f
                    else -> AutoMixArtworkTuning.revealProgress(AutoMixRevealClock.progress(live, live.artworkProgress))
                }
            },
        )
    }
    val outgoing = visual != null && entry == visual.outgoing.identity
    val incoming = visual != null && entry == visual.incoming.identity
    val target = prepared?.takeIf { incoming && (resolvedUrl == null || resolvedUrl == it.resolvedUrl) }
    val ownsSettlement = stage != null && stage.generation != history.closedGeneration &&
        (holdsPair || stage.generation == history.mixingGeneration)
    val cancelled = ownsSettlement && stage.phase == AutoMixVisualPhase.CANCELLED && (outgoing || incoming)
    val promoted = ownsSettlement && target != null && visual?.artworkWindow != null &&
        (stage.windowEnded || stage.phase == AutoMixVisualPhase.COMPLETED)
    val retained = history.settled?.takeIf { (owner, ambient) ->
        val preparedUrl = ambient.incoming?.resolvedUrl
        direct == null && ambient.settled?.let { gate.allows(it.first) } == true &&
            owner == entry && (preparedUrl == null || resolvedUrl == null || preparedUrl == resolvedUrl)
    }?.second
    val settled = if (stage != null && entry != null && (cancelled || promoted)) {
        AutoMixAmbient(settled = stage.generation to entry, incoming = target, cancelled = cancelled)
    } else retained
    SideEffect {
        history.direct = direct
        history.lastEntry = entry
        if (holdsPair && (outgoing || incoming)) history.mixingGeneration = stage.generation
        // The feed can retain a terminal sample across ordinary queue navigation. Consume it
        // once, rather than treating a later visit to either occurrence as part of that mix.
        if (promoted || stage?.phase == AutoMixVisualPhase.COMPLETED ||
            stage?.phase == AutoMixVisualPhase.CANCELLED ||
            (holdsPair && entry != null && !outgoing && !incoming)
        ) history.closedGeneration = stage.generation
        if (visual != null && stage == null && !visual.mixActive && visual.phase != AutoMixVisualPhase.ARMED &&
            (visual.swapSeconds == null || (entry != null && !outgoing))
        ) history.closedGeneration = visual.generation
        history.settled = if (entry != null && settled != null) entry to settled else null
        if (entry != null && resolvedUrl != null) history.note(entry, resolvedUrl)
    }
    val hold = (outgoing || incoming) && holdsPair && !promoted && !stage.noDissolve
    // [direct] stays internal bookkeeping. Handed to the backdrops it made them drop the
    // held previous backdrop and snap to the new track's assets — black until those were
    // decoded — where a song change with art transition off should look like any other.
    return AutoMixAmbient(pair.takeIf { hold }, settled?.settled, settled?.incoming, settled?.cancelled == true, null)
}
