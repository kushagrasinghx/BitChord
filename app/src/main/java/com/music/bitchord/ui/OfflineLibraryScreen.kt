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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
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
    songs: List<Song>, currentSong: Song?, isPlaying: Boolean,
    onSongClick: (List<Song>, Int) -> Unit, onSongLongPress: (Song) -> Unit,
    onArtistClick: (String) -> Unit, onAlbumClick: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    var query by remember { mutableStateOf("") }
    var artistFilter by remember { mutableStateOf<String?>(null) }
    var albumFilter by remember { mutableStateOf<String?>(null) }
    val artists = remember(songs) { songs.map { it.artist }.filter { it.isNotBlank() }.distinct().sorted() }
    val albums = remember(songs) { songs.mapNotNull { it.albumName }.filter { it.isNotBlank() }.distinct().sorted() }
    val filtered = remember(songs, query, artistFilter, albumFilter) {
        val q = query.trim().lowercase()
        songs.filter { song ->
            (artistFilter == null || song.artist == artistFilter) &&
                (albumFilter == null || song.albumName == albumFilter) &&
                (q.isEmpty() || song.title.lowercase().contains(q) || song.artist.lowercase().contains(q) || song.albumName.orEmpty().lowercase().contains(q))
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text("Your Music", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(20.dp, 28.dp, 20.dp, 12.dp))
        OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("Search your music") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))

        Row(Modifier.fillMaxWidth().padding(20.dp, 16.dp, 20.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            OfflineStat(Icons.Rounded.MusicNote, "Songs", songs.size.toString())
            OfflineStat(Icons.Rounded.Person, "Artists", artists.size.toString())
            OfflineStat(Icons.Rounded.Album, "Albums", albums.size.toString())
        }
        if (artistFilter != null || albumFilter != null) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                artistFilter?.let { AssistChip(onClick = { artistFilter = null }, label = { Text("Artist: $it") }) }
                albumFilter?.let { AssistChip(onClick = { albumFilter = null }, label = { Text("Album: $it") }) }
            }
        }

        LazyRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Artists", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 4.dp).align(Alignment.CenterVertically)) }
            items(artists) { artist -> AssistChip(onClick = { artistFilter = artist; albumFilter = null; onArtistClick(artist) }, label = { Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis) }) }
        }
        LazyRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Albums", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 4.dp).align(Alignment.CenterVertically)) }
            items(albums) { album -> AssistChip(onClick = { albumFilter = album; artistFilter = null; onAlbumClick(album) }, label = { Text(album, maxLines = 1, overflow = TextOverflow.Ellipsis) }) }
        }

        if (filtered.isEmpty()) {
            Text("No local tracks match your search", modifier = Modifier.padding(32.dp), style = MaterialTheme.typography.titleMedium)
        } else {
            LazyColumn(contentPadding = contentPadding) {
                item { Text(if (query.isBlank() && artistFilter == null && albumFilter == null) "Songs" else "Results (${filtered.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(20.dp, 12.dp, 20.dp, 6.dp)) }
                itemsIndexed(filtered, key = { _, it -> "song:${it.localUri ?: it.videoId}" }) { index, song ->
                    Row(Modifier.fillMaxWidth().combinedClickable(onClick = { onSongClick(filtered, index) }, onLongClick = { onSongLongPress(song) }).padding(horizontal = 20.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = song.artworkAt(ROW_ART_PX), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(MaterialTheme.shapes.small))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            song.albumName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        if (currentSong?.localUri == song.localUri && isPlaying) Icon(Icons.Rounded.PlayArrow, "Playing", tint = MaterialTheme.colorScheme.primary)
                        else IconButton(onClick = { onSongClick(filtered, index) }) { Icon(Icons.Rounded.PlayArrow, "Play") }
                    }
                }
            }
        }
    }
}

@Composable private fun OfflineStat(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Icon(icon, null, Modifier.size(18.dp)); Column { Text(value, MaterialTheme.typography.titleMedium); Text(label, MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}
