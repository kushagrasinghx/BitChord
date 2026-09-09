package com.music.bitchord.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.PLAYER_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.playback.OfflinePlayerState
import com.music.bitchord.playback.queueSongs
import com.music.bitchord.playback.seekOfflineTo
import com.music.bitchord.playback.skipOfflineNext
import com.music.bitchord.playback.skipOfflinePrevious
import androidx.media3.session.MediaController

@Composable
fun OfflineNowPlayingScreen(
    controller: MediaController,
    state: OfflinePlayerState,
    onBack: () -> Unit,
) {
    val song = state.song ?: run {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nothing is playing")
        }
        return
    }
    var dragging by remember(song.videoId) { mutableStateOf(false) }
    var sliderValue by remember(song.videoId) { mutableFloatStateOf(0f) }
    var showQueue by remember { mutableStateOf(false) }
    val queue = remember(state.queueVersion, controller.mediaItemCount, controller.currentMediaItemIndex) {
        controller.queueSongs()
    }

    LaunchedEffect(state.position.value, state.durationMs, dragging, song.videoId) {
        if (!dragging) {
            sliderValue = if (state.durationMs > 0L) {
                (state.position.value.toFloat() / state.durationMs).coerceIn(0f, 1f)
            } else 0f
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Now Playing",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            IconButton(onClick = { showQueue = !showQueue }) {
                Icon(Icons.Rounded.QueueMusic, contentDescription = "Queue")
            }
            IconButton(onClick = {}) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More")
            }
        }

        if (showQueue) {
            Text("Queue", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(queue) { index, item ->
                    QueueRow(
                        song = item,
                        active = index == controller.currentMediaItemIndex,
                        onClick = { controller.seekToDefaultPosition(index) },
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(0.35f))
            AsyncImage(
                model = song.artworkAt(PLAYER_ART_PX),
                contentDescription = "Album artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.weight(0.2f))
            Text(song.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(18.dp))

            Slider(
                value = sliderValue,
                onValueChange = { dragging = true; sliderValue = it },
                onValueChangeFinished = {
                    dragging = false
                    controller.seekOfflineTo((sliderValue * state.durationMs).toLong())
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(if (dragging) (sliderValue * state.durationMs).toLong() else state.position.value), style = MaterialTheme.typography.labelMedium)
                Text(formatTime(state.durationMs), style = MaterialTheme.typography.labelMedium)
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { controller.shuffleModeEnabled = !controller.shuffleModeEnabled }) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(26.dp))
                }
                IconButton(onClick = { controller.skipOfflinePrevious() }) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(38.dp))
                }
                IconButton(
                    onClick = { if (state.isPlaying) controller.pause() else controller.play() },
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(48.dp),
                    )
                }
                IconButton(onClick = { controller.skipOfflineNext() }) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(38.dp))
                }
                IconButton(onClick = { controller.repeatMode = if (controller.repeatMode == androidx.media3.common.Player.REPEAT_MODE_OFF) androidx.media3.common.Player.REPEAT_MODE_ALL else androidx.media3.common.Player.REPEAT_MODE_OFF }) {
                    Icon(Icons.Rounded.Repeat, contentDescription = "Repeat", modifier = Modifier.size(26.dp))
                }
            }
            Spacer(Modifier.weight(0.45f))
        }
    }
}

@Composable
private fun QueueRow(song: Song, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.artworkAt(160),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(MaterialTheme.shapes.small),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (active) Text("Playing", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
