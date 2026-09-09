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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OfflineLibraryScreen(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
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
    val artists = remember(songs) { songs.map { it.artist }.filter { it.isNotBlank() }.distinct().sorted() }
    val albums = remember(songs) { songs.mapNotNull { it.albumName }.filter { it.isNotBlank() }.distinct().sorted() }

    Column(Modifier.fillMaxSize()) {
        Text("Your Music", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text("Search your music") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            OfflineStat(Icons.Rounded.MusicNote, "Songs", songs.size.toString())
            OfflineStat(Icons.Rounded.Person, "Artists", artists.size.toString())
            OfflineStat(Icons.Rounded.Album, "Albums", albums.size.toString())
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Artists", style = MaterialTheme.typography.titleMedium)
            Text("${artists.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(contentPadding = contentPadding) {
            items(artists, key = { "artist:$it" }) { artist ->
                Row(Modifier.fillMaxWidth().combinedClickable(onClick = { onArtistClick(artist) }, onLongClick = {}).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(38.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(artist, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, top = 18.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Albums", style = MaterialTheme.typography.titleMedium)
                    Text("${albums.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(albums, key = { "album:$it" }) { album ->
                val albumSong = songs.firstOrNull { it.albumName == album }
                Row(Modifier.fillMaxWidth().combinedClickable(onClick = { onAlbumClick(album) }, onLongClick = {}).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = albumSong?.artworkAt(ROW_ART_PX), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(MaterialTheme.shapes.small))
                    Spacer(Modifier.width(14.dp))
                    Text(album, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, top = 18.dp, bottom = 8.dp)) {
                    Text(if (query.isBlank()) "Songs" else "Search results", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (filtered.isEmpty()) {
                item { Text("No local tracks match your search", modifier = Modifier.padding(32.dp), style = MaterialTheme.typography.titleMedium) }
            } else {
                items(filtered, key = { "song:${it.localUri ?: it.videoId}" }) { song ->
                    val index = filtered.indexOf(song)
                    Row(
                        Modifier.fillMaxWidth().combinedClickable(onClick = { onSongClick(filtered, index) }, onLongClick = { onSongLongPress(song) }).padding(horizontal = 20.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(model = song.artworkAt(ROW_ART_PX), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(MaterialTheme.shapes.small))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            song.albumName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        if (currentSong?.localUri == song.localUri && isPlaying) Icon(Icons.Rounded.PlayArrow, contentDescription = "Playing", tint = MaterialTheme.colorScheme.primary)
                        else IconButton(onClick = { onSongClick(filtered, index) }) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Play") }
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
        Column { Text(value, style = MaterialTheme.typography.titleMedium); Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
