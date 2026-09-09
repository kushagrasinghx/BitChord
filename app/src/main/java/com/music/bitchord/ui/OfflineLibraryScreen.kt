package com.music.bitchord.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.model.ROW_ART_PX

/** Local replacement for the online home/library surfaces. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OfflineLibraryScreen(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    contentPadding: PaddingValues,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(songs, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) songs else songs.filter {
            it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.albumName.orEmpty().lowercase().contains(q)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            text = "Your Music",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text("Search your music") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            OfflineStat(Icons.Rounded.MusicNote, "Songs", songs.size.toString())
            OfflineStat(Icons.Rounded.Person, "Artists", songs.map { it.artist }.distinct().size.toString())
            OfflineStat(Icons.Rounded.Album, "Albums", songs.mapNotNull { it.albumName }.distinct().size.toString())
        }

        if (filtered.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No local tracks match your search", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            LazyColumn(contentPadding = contentPadding) {
                items(filtered, key = { it.videoId }) { song ->
                    val index = filtered.indexOf(song)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onSongClick(filtered, index) },
                                onLongClick = { onSongLongPress(song) },
                            )
                            .padding(horizontal = 20.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = song.artworkAt(ROW_ART_PX),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(52.dp).clip(MaterialTheme.shapes.small),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            song.albumName?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (currentSong?.videoId == song.videoId && isPlaying) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Playing", tint = MaterialTheme.colorScheme.primary)
                        } else {
                            IconButton(onClick = { onSongClick(filtered, index) }) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineStat(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Column {
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
