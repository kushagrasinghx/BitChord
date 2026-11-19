package com.music.bitchord.ui.player

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SONG_INFO_SLIDE_MS = 280
private const val SONG_INFO_CONFIRM_MS = 1_000L

/** Queue occurrences, not just recordings: the same recording can appear twice. */
private fun Song.infoKey(): Pair<String, String?> = videoId to queueEntryId

/** The session's shuffle order is the physical queue, including repeat-all wraps. */
internal fun songInfoNeighbour(queue: List<Song>, index: Int, next: Boolean, available: Boolean): Song? {
    if (!available || index !in queue.indices) return null
    val neighbour = if (next) {
        if (index == queue.lastIndex) 0 else index + 1
    } else {
        if (index == 0) queue.lastIndex else index - 1
    }
    return queue.getOrNull(neighbour)
}

internal class SongInfoLayer(val song: Song, x: Float) {
    // In viewport widths, so resizing does not strand the destination off-centre.
    var x by mutableFloatStateOf(x)
}

/** One owner for direct dragging, settlement, and the metadata handoff. */
internal class SongInfoTransition(private val scope: CoroutineScope) {
    private var job: Job? = null
    private var source by mutableStateOf<Song?>(null)
    private var target by mutableStateOf<Song?>(null)
    private var settled = false
    private var actionDispatched = false
    private var pendingSwipe = false
    private var next: Song? = null
    private var previous: Song? = null
    private var dragStarts = emptyList<Pair<SongInfoLayer, Float>>()
    private var drag = 0f
    var width = 0f
    var layers by mutableStateOf<List<SongInfoLayer>>(emptyList())
        private set

    fun visibleLayers(song: Song): List<SongInfoLayer> {
        val identity = song.infoKey()
        return if (source?.infoKey() == identity || (actionDispatched && target?.infoKey() == identity)) {
            layers
        } else {
            listOf(SongInfoLayer(song, 0f))
        }
    }

    /** An unrelated/automatic item change must render immediately, without a slide. */
    fun observe(song: Song) {
        val origin = source ?: return
        if (song.infoKey() != origin.infoKey() && (!actionDispatched || song.infoKey() != target?.infoKey())) {
            clear()
        } else if (settled && song.infoKey() == target?.infoKey()) {
            clear()
        }
    }

    fun beginDrag(song: Song, next: Song?, previous: Song?): Boolean {
        // A committed swipe owns its short completion. Another gesture must
        // not cancel the already accepted transport action before dispatch.
        if (pendingSwipe) return false
        clear()
        source = song
        this.next = next?.takeUnless { it.infoKey() == song.infoKey() }
        this.previous = previous?.takeUnless { it.infoKey() == song.infoKey() }
        layers = listOf(SongInfoLayer(song, 0f))
        dragStarts = layers.map { it to it.x }
        drag = 0f
        return true
    }

    fun dragBy(total: Float) {
        if (width <= 0f) return
        val neighbour = if (total < 0f) next else previous
        if (neighbour == null) {
            drag = 0f
            for ((layer, start) in dragStarts) layer.x = start
            layers = dragStarts.map { it.first }
            return
        }
        // Only compose the neighbour on the dragged side; the current marquee
        // remains keyed and mounted throughout reversals and rejected drags.
        val existing = layers.firstOrNull { it.song.infoKey() == neighbour.infoKey() }
        val incoming = existing ?: SongInfoLayer(neighbour, if (total < 0f) 1f else -1f)
        val start = if (total < 0f) 1f else -1f
        layers = dragStarts.map { it.first } + incoming
        drag = (total / width).coerceIn(-1f, 1f)
        for ((layer, initial) in dragStarts) layer.x = initial + drag
        incoming.x = start + drag
    }

    fun cancelDrag() {
        job?.cancel()
        val origin = source ?: return
        settle(origin, direction = if (drag < 0f) 1f else -1f) { clear() }
    }

    fun finishDrag(next: Boolean, currentSong: () -> Song?, canSlide: () -> Boolean, action: () -> Unit) {
        val origin = source ?: return
        if (currentSong()?.infoKey() != origin.infoKey()) {
            clear()
            return
        }
        val destination = if (next) this.next else previous
        if (destination == null || !canSlide()) {
            cancelDrag()
            action()
            return
        }
        target = destination
        pendingSwipe = true
        settle(destination, direction = if (next) -1f else 1f) {
            // Compare the live controller occurrence, not the last composed
            // Song: auto-advance may have happened before the UI observed it.
            if (currentSong()?.infoKey() != origin.infoKey()) {
                clear()
                return@settle
            }
            pendingSwipe = false
            // Playback can pass the restart point during the settle, too. Keep
            // the existing action's live decision, never commit a false preview.
            if (!canSlide()) {
                cancelDrag()
                action()
            } else {
                settled = true
                actionDispatched = true
                action()
                // A disconnected/refused controller must not leave phantom info.
                delay(SONG_INFO_CONFIRM_MS)
                cancelDrag()
            }
        }
    }

    private fun settle(destination: Song, direction: Float, finished: suspend () -> Unit) {
        job?.cancel()
        val starts = layers.map { it to it.x }
        job = scope.launch {
            animate(0f, 1f, animationSpec = tween(SONG_INFO_SLIDE_MS, easing = LinearOutSlowInEasing)) { value, _ ->
                for ((layer, start) in starts) {
                    val end = if (layer.song.infoKey() == destination.infoKey()) 0f else direction
                    layer.x = start + (end - start) * value
                }
            }
            finished()
        }
    }

    fun clear() {
        job?.cancel()
        job = null
        source = null
        target = null
        settled = false
        actionDispatched = false
        pendingSwipe = false
        next = null
        previous = null
        layers = emptyList()
        dragStarts = emptyList()
    }
}

/**
 * Only the info column occupies this viewport. Each block retains its own
 * leading layout: explicit blocks start at their badge, others at their title.
 * The fixed right edge is the marquee's end, before the stationary action row.
 */
@Composable
internal fun SlidingSongInfo(
    song: Song,
    transition: SongInfoTransition,
    modifier: Modifier = Modifier,
    content: @Composable (Song) -> Unit,
) {
    Box(modifier.onSizeChanged { transition.width = it.width.toFloat() }.clipToBounds()) {
        for (layer in transition.visibleLayers(song)) {
            key(layer.song.infoKey()) {
                Box(Modifier.fillMaxWidth().graphicsLayer { translationX = layer.x * size.width }) {
                    content(if (layer.song.infoKey() == song.infoKey()) song else layer.song)
                }
            }
        }
    }
}
