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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.Song
import com.music.bitchord.sharedui.resources.*
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.components.MenuFrostDark
import com.music.bitchord.ui.components.liveMenuFrost
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
    reduceAnimation: Boolean,
    onAction: suspend (List<Song>, QueueBulkAction) -> Boolean,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ROW_SHAPE)
                .then(
                    if (reduceDynamicBlur) Modifier.background(MenuFrostDark)
                    else Modifier.liveMenuFrost(hazeState, MenuFrostDark),
                )
                .clickable(onClick = {})
                .navigationBarsPadding()
                .padding(vertical = 8.dp)
                .alpha(if (enabled) 1f else 0.38f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { perform(QueueBulkAction.PLAYLIST) }, enabled = enabled, modifier = Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(BitChordIcons.Plus, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text(stringResource(Res.string.queue_add_to_playlist), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            TextButton(onClick = { perform(QueueBulkAction.LIKE) }, enabled = enabled, modifier = Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(BitChordIcons.Heart, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text(stringResource(Res.string.like), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            TextButton(onClick = { perform(QueueBulkAction.DOWNLOAD) }, enabled = enabled, modifier = Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(BitChordIcons.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text(stringResource(Res.string.queue_download), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
