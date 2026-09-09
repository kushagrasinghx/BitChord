package com.music.bitchord.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.*
import com.music.bitchord.offline.OfflineLocalStore

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OfflineLibraryScreen(
    songs: List<Song>, currentSong: Song?, isPlaying: Boolean,
    onSongClick: (List<Song>, Int) -> Unit, onSongLongPress: (Song) -> Unit,
    contentPadding: PaddingValues, store: OfflineLocalStore? = null,
) {
    val context = LocalContext.current
    val localStore = store ?: remember { OfflineLocalStore.get(context) }
    var query by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf<String?>(null) }
    var album by remember { mutableStateOf<String?>(null) }
    var collection by remember { mutableStateOf("all") }
    var version by remember { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var addSong by remember { mutableStateOf<Song?>(null) }
    var newPlaylist by remember { mutableStateOf(false) }
    if (showSettings) { OfflineSettingsScreen(localStore, { showSettings = false }, { version++ }); return }
    val artists = remember(songs) { songs.map { it.artist }.filter(String::isNotBlank).distinct().sorted() }
    val albums = remember(songs) { songs.mapNotNull { it.albumName }.filter(String::isNotBlank).distinct().sorted() }
    val playlists = remember(version) { localStore.playlistNames() }
    val filtered = remember(songs, query, artist, album, collection, version) {
        val base = when {
            collection == "favorites" -> localStore.favorites(songs)
            collection == "history" -> localStore.history(songs)
            collection.startsWith("playlist:") -> localStore.playlist(collection.removePrefix("playlist:"), songs)
            else -> songs
        }
        val q = query.trim().lowercase()
        base.filter { s -> (artist == null || s.artist == artist) && (album == null || s.albumName == album) && (q.isEmpty() || s.title.lowercase().contains(q) || s.artist.lowercase().contains(q) || s.albumName.orEmpty().lowercase().contains(q)) }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(20.dp, 28.dp, 20.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Your Music", style = MaterialTheme.typography.displaySmall, Modifier.weight(1f))
            IconButton({ showSettings = true }) { Icon(Icons.Rounded.Settings, "Settings") }
        }
        OutlinedTextField(query, { query = it }, singleLine = true, leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("Search your music") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(20.dp, 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip({ collection = "all" }, label = { Text("All") })
            AssistChip({ collection = "favorites" }, leadingIcon = { Icon(Icons.Rounded.Favorite, null) }, label = { Text("Favorites ${localStore.favorites(songs).size}") })
            AssistChip({ collection = "history" }, leadingIcon = { Icon(Icons.Rounded.History, null) }, label = { Text("History") })
            playlists.forEach { name -> AssistChip({ collection = "playlist:$name" }, label = { Text(name) }) }
            IconButton({ newPlaylist = true }) { Icon(Icons.Rounded.Add, "New playlist") }
        }
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("${songs.size} Songs", style = MaterialTheme.typography.labelLarge); Text("${artists.size} Artists", style = MaterialTheme.typography.labelLarge); Text("${albums.size} Albums", style = MaterialTheme.typography.labelLarge)
        }
        LazyRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { item { Text("Artists", Modifier.padding(end = 4.dp), style = MaterialTheme.typography.titleMedium) }; items(artists) { a -> AssistChip({ artist = a; album = null }, label = { Text(a, maxLines = 1, overflow = TextOverflow.Ellipsis) }) } }
        LazyRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { item { Text("Albums", Modifier.padding(end = 4.dp), style = MaterialTheme.typography.titleMedium) }; items(albums) { a -> AssistChip({ album = a; artist = null }, label = { Text(a, maxLines = 1, overflow = TextOverflow.Ellipsis) }) } }
        if (filtered.isEmpty()) Text("No local tracks match your selection", Modifier.padding(32.dp), style = MaterialTheme.typography.titleMedium)
        else LazyColumn(contentPadding = contentPadding) {
            item { Text(if (collection == "all") "Songs" else collection.removePrefix("playlist:"), Modifier.padding(20.dp, 12.dp, 20.dp, 6.dp), style = MaterialTheme.typography.titleMedium) }
            itemsIndexed(filtered, key = { _, s -> s.localUri ?: s.videoId }) { index, song ->
                val favorite = localStore.isFavorite(song)
                Row(Modifier.fillMaxWidth().combinedClickable({ localStore.recordHistory(song); onSongClick(filtered, index) }, { onSongLongPress(song) }).padding(horizontal = 20.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(song.artworkAt(ROW_ART_PX), null, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(MaterialTheme.shapes.small))
                    Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis); song.albumName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                    IconButton({ localStore.setFavorite(song, !favorite); version++ }) { Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorite") }
                    if (playlists.isNotEmpty()) IconButton({ addSong = song }) { Icon(Icons.Rounded.PlaylistAdd, "Add to playlist") }
                    IconButton({ localStore.recordHistory(song); onSongClick(filtered, index) }) { Icon(Icons.Rounded.PlayArrow, "Play") }
                }
            }
        }
    }
    addSong?.let { song -> AlertDialog(onDismissRequest = { addSong = null }, title = { Text("Add to playlist") }, text = { Column { playlists.forEach { name -> Text(name, Modifier.fillMaxWidth().clickable { localStore.addToPlaylist(name, song); addSong = null }.padding(10.dp)) } } }, confirmButton = {}) }
    if (newPlaylist) {
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { newPlaylist = false }, title = { Text("New playlist") }, text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Playlist name") }) }, confirmButton = { TextButton({ if (localStore.createPlaylist(name)) version++; newPlaylist = false }, enabled = name.isNotBlank()) { Text("Create") } }, dismissButton = { TextButton({ newPlaylist = false }) { Text("Cancel") } })
    }
}
