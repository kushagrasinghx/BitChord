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
) {
    fun mixFor(targetReady: Boolean): AutoMixAmbientMix? = mix?.takeUnless {
        targetReady && settled?.first == it.generation
    }

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

private data class AmbientBoundary(val generation: Long, val phase: AutoMixVisualPhase, val windowEnded: Boolean)

private fun AutoMixVisualState.boundary() = AmbientBoundary(generation, phase, artworkProgress >= 1f)

/** Only lifecycle boundaries invalidate composition; the audio sample itself is read in drawing. */
@Composable
internal fun rememberAutoMixAmbient(entry: QueueEntryIdentity?, resolvedUrl: String?): AutoMixAmbient {
    val feed = PlayerPlatform.host.autoMixVisualState ?: return AutoMixAmbient()
    val history = remember(feed) { AmbientArtworkHistory() }
    val currentEntry = rememberUpdatedState(entry)
    val sample = remember(feed) { mutableStateOf(feed.value) }
    val boundary = remember(feed) { mutableStateOf(feed.value?.boundary()) }
    LaunchedEffect(feed) {
        feed.collect {
            sample.value = it
            boundary.value = it?.boundary()
        }
    }

    // Also sample synchronously when session metadata recomposes first. The boundary state
    // is only an invalidation signal; it does not own the ordering of the two feeds.
    val observedBoundary = boundary.value
    val visual = feed.value
    val stage = visual?.boundary().let { if (it == observedBoundary) observedBoundary else it }
    val prepared = preparedAutoMixAmbient?.takeIf { it.generation == stage?.generation }
    val holdsPair = stage?.phase == AutoMixVisualPhase.MIXING || stage?.phase == AutoMixVisualPhase.COMPLETED
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
                    else -> AutoMixArtworkTuning.ease(live.artworkProgress)
                }
            },
        )
    }
    val outgoing = visual != null && entry == visual.outgoing.identity
    val incoming = visual != null && entry == visual.incoming.identity
    val target = prepared?.takeIf { incoming && (resolvedUrl == null || resolvedUrl == it.resolvedUrl) }
    val cancelled = stage?.phase == AutoMixVisualPhase.CANCELLED && visual?.overlapStartSeconds != null &&
        (outgoing || incoming)
    val promoted = target != null && visual?.artworkWindow != null &&
        (stage?.windowEnded == true || stage?.phase == AutoMixVisualPhase.COMPLETED)
    val settled = if (stage != null && entry != null && (cancelled || promoted)) {
        AutoMixAmbient(settled = stage.generation to entry, incoming = target, cancelled = cancelled)
    } else history.settled?.takeIf { it.first == entry }?.second
    SideEffect {
        history.settled = if (entry != null && settled != null) entry to settled else null
        if (entry != null && resolvedUrl != null) history.note(entry, resolvedUrl)
    }
    val hold = ((outgoing || incoming) && stage?.phase == AutoMixVisualPhase.MIXING) ||
        (outgoing && stage?.phase == AutoMixVisualPhase.COMPLETED)
    return AutoMixAmbient(pair.takeIf { hold }, settled?.settled, settled?.incoming, settled?.cancelled == true)
}
