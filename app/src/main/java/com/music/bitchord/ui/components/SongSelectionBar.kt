package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.SongSelection

/** What the selection bar can do with the picked songs; null actions aren't offered. */
class SongSelectionActions(
    val onToggle: (Song) -> Unit,
    val onSelectAll: (List<Song>) -> Unit,
    val onPlayNext: (List<Song>) -> Unit,
    val onAddToQueue: (List<Song>) -> Unit,
    /** Signed in only: playlists are the account's. */
    val onAddToPlaylist: ((List<Song>) -> Unit)?,
    /** On one of the user's own playlists only. */
    val onRemove: ((List<Song>) -> Unit)?,
    val onDone: () -> Unit,
)

/**
 * The picked songs' actions, floated under the top bar while picking, as the
 * Downloads folder's selection bar is: the count, Select all, then what to do
 * with them. Icons alone, labelled for TalkBack, so it fits a phone's width.
 */
@Composable
fun SongSelectionBar(
    selection: SongSelection,
    /** Every song on the page, in its order: what the selection is read against. */
    songs: List<Song>,
    actions: SongSelectionActions,
    modifier: Modifier = Modifier,
) {
    val picked = selection.of(songs)
    val count = picked.size
    val allPicked = songs.isNotEmpty() && count == songs.size
    val shape = RoundedCornerShape(14.dp)
    val ink = if (isSystemInDarkTheme()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .shadow(10.dp, shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton(Icons.Rounded.Close, stringResource(R.string.done), ink, enabled = true, onClick = actions.onDone)
        Text(
            text = "$count ${stringResource(R.string.selected)}",
            style = MaterialTheme.typography.titleSmall,
            color = ink,
            modifier = Modifier.weight(1f),
        )
        BarButton(Icons.Rounded.Check, stringResource(R.string.select_all), ink, enabled = !allPicked) {
            actions.onSelectAll(songs)
        }
        val accent = MaterialTheme.colorScheme.primary
        BarButton(Icons.AutoMirrored.Rounded.PlaylistPlay, stringResource(R.string.play_next), accent, count > 0) {
            actions.onPlayNext(picked)
        }
        BarButton(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.add_to_queue), accent, count > 0) {
            actions.onAddToQueue(picked)
        }
        actions.onAddToPlaylist?.let { add ->
            BarButton(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.add_to_playlist), accent, count > 0) {
                add(picked)
            }
        }
        actions.onRemove?.let { remove ->
            BarButton(Icons.Rounded.Delete, stringResource(R.string.remove_from_playlist), DESTRUCTIVE, count > 0) {
                remove(picked)
            }
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = label, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(22.dp))
    }
}

private val DESTRUCTIVE = Color(0xFFFF453A)
