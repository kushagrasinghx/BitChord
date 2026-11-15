package com.music.bitchord.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.QueueEntryIdentity
import com.music.bitchord.data.model.Song
import com.music.bitchord.sharedui.resources.*
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.components.queueActionSurface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Personal library actions; none edits the playback queue or enters Undo history. */
enum class QueueBulkAction { PLAYLIST, LIKE, DOWNLOAD }

internal val QueueTracks.rows: List<QueueTrack>
    get() = history + listOfNotNull(nowPlaying) + user + context + autoplay

private val QueueTrack.selectionKey: String
    get() = uniqueEntryId?.let { "entry:$it" } ?: key

private fun selectedQueueEntryIdentities(
    selected: List<QueueTrack>,
    timeline: List<Song>,
): List<QueueEntryIdentity> {
    val occurrenceByIndex = IntArray(timeline.size)
    val occurrences = HashMap<Pair<String, String?>, Int>()
    timeline.forEachIndexed { index, song ->
        val key = song.videoId to song.queueEntryId
        val occurrence = occurrences[key] ?: 0
        occurrenceByIndex[index] = occurrence
        occurrences[key] = occurrence + 1
    }
    return selected.map { track ->
        val song = track.song
        QueueEntryIdentity(
            mediaId = song.videoId,
            queueEntryId = song.queueEntryId,
            occurrence = occurrenceByIndex[track.timelineIndex],
        )
    }
}

/** Owned by the player, not lazy rows, so scrolling never disposes the selection. */
internal class QueueSelectionState {
    var active by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
    var generation by mutableIntStateOf(0)
        private set
    private var keys by mutableStateOf(emptySet<String>())

    fun start() {
        generation++
        active = true
        busy = false
        keys = emptySet()
    }

    fun isSelected(track: QueueTrack): Boolean = track.selectionKey in keys

    fun selected(rows: List<QueueTrack>): List<QueueTrack> = rows.filter(::isSelected)

    fun toggle(track: QueueTrack) {
        if (busy) return
        val key = track.selectionKey
        keys = if (key in keys) keys - key else keys + key
    }

    fun selectAll(rows: List<QueueTrack>) {
        if (!busy) keys = rows.mapTo(HashSet()) { it.selectionKey }
    }

    fun retain(rows: List<QueueTrack>) {
        keys = keys.intersect(rows.mapTo(HashSet()) { it.selectionKey })
    }

    fun clear() {
        generation++
        active = false
        busy = false
        keys = emptySet()
    }
}

@Composable
internal fun QueueSelectionBar(
    selection: QueueSelectionState,
    rows: List<QueueTrack>,
    timeline: List<Song>,
    reduceAnimation: Boolean,
    onAction: suspend (List<Song>, QueueBulkAction) -> Boolean,
    onRemoveSelected: suspend (List<QueueEntryIdentity>) -> Unit,
    hazeState: HazeState,
) {
    val scope = rememberCoroutineScope()
    val reduceDynamicBlur by PlayerSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    AnimatedVisibility(
        visible = selection.active,
        enter = if (reduceAnimation) EnterTransition.None else
            slideInVertically { it } + expandVertically() + fadeIn(),
        exit = if (reduceAnimation) ExitTransition.None else
            slideOutVertically { it } + shrinkVertically() + fadeOut(),
        label = "queue-selection-bar",
    ) {
        val selected = selection.selected(rows)
        val enabled = selected.isNotEmpty() && !selection.busy
        val perform: (QueueBulkAction) -> Unit = { action ->
            if (enabled && selection.active && !selection.busy) {
                val generation = selection.generation
                val songs = selected.map { it.song }
                selection.busy = true
                scope.launch {
                    try {
                        if (onAction(songs, action) && selection.generation == generation) selection.clear()
                    } finally {
                        if (selection.generation == generation) selection.busy = false
                    }
                }
            }
        }
        val performRemove: () -> Unit = {
            if (enabled && selection.active && !selection.busy) {
                val generation = selection.generation
                val identities = selectedQueueEntryIdentities(selected, timeline)
                selection.busy = true
                scope.launch {
                    try {
                        onRemoveSelected(identities)
                        if (selection.generation == generation) selection.clear()
                    } finally {
                        if (selection.generation == generation) selection.busy = false
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .queueActionSurface(hazeState, reduceDynamicBlur)
                .clickable(onClick = {})
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { perform(QueueBulkAction.PLAYLIST) }, enabled = enabled, modifier = Modifier.weight(1f)) {
                Column(Modifier.alpha(if (enabled) 1f else 0.38f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(BitChordIcons.Plus, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text(stringResource(Res.string.queue_add_to_playlist), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            TextButton(onClick = performRemove, enabled = enabled, modifier = Modifier.weight(1f)) {
                Column(Modifier.alpha(if (enabled) 1f else 0.38f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text(stringResource(Res.string.queue_selection_remove), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            TextButton(onClick = { perform(QueueBulkAction.DOWNLOAD) }, enabled = enabled, modifier = Modifier.weight(1f)) {
                Column(Modifier.alpha(if (enabled) 1f else 0.38f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(BitChordIcons.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text(stringResource(Res.string.queue_download), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
