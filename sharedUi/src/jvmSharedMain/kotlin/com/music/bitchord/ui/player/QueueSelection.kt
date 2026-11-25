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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.QueueEntryIdentity
import com.music.bitchord.data.model.Song
import com.music.bitchord.sharedui.resources.*
import com.music.bitchord.ui.icons.BitChordIcons
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Personal library actions; none edits the playback queue or enters Undo history. */
enum class QueueBulkAction { PLAYLIST, DOWNLOAD }

internal val QueueTracks.rows: List<QueueTrack>
    get() = history + user + context + autoplay

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
        // One action at a time; a selection closed or restarted meanwhile is left alone.
        fun run(block: suspend () -> Boolean) {
            if (!enabled || !selection.active) return
            val generation = selection.generation
            selection.busy = true
            scope.launch {
                try {
                    if (block() && selection.generation == generation) selection.clear()
                } finally {
                    if (selection.generation == generation) selection.busy = false
                }
            }
        }
        val songs = selected.map { it.song }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .playerBarSurface(RoundedCornerShape(16.dp), hazeState)
                .clickable(onClick = {})
                .navigationBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Capped so the three actions stay grouped on tablets and unfolded foldables.
            Row(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BulkActionButton(BitChordIcons.Plus, stringResource(Res.string.queue_add_to_playlist), enabled) {
                    run { onAction(songs, QueueBulkAction.PLAYLIST) }
                }
                BulkActionButton(Icons.Rounded.Delete, stringResource(Res.string.queue_selection_remove), enabled) {
                    val identities = selectedQueueEntryIdentities(selected, timeline)
                    run { onRemoveSelected(identities); true }
                }
                BulkActionButton(BitChordIcons.Download, stringResource(Res.string.queue_download), enabled) {
                    run { onAction(songs, QueueBulkAction.DOWNLOAD) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.BulkActionButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val labelStyle = MaterialTheme.typography.labelMedium
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        // One line on every width and font scale: shrinks a little before it ever ellipsizes.
        BasicText(
            text = label,
            style = labelStyle.copy(color = Color.White, textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = labelStyle.fontSize),
        )
    }
}
