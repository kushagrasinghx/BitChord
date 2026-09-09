package com.music.bitchord

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.*
import com.music.bitchord.offline.*
import com.music.bitchord.playback.*
import com.music.bitchord.ui.*
import com.music.bitchord.ui.theme.BitChordTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OfflineMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LastPlayed.init(this)
        setContent { OfflineMusicApp() }
    }
}

@Composable
private fun OfflineMusicApp() {
    val context = LocalContext.current
    val store = remember { OfflineLocalStore.get(context) }
    var themeVersion by remember { mutableStateOf(0) }
    val theme = remember(themeVersion) { store.theme() }
    val dark = when (theme) {
        OfflineLocalStore.Theme.DARK -> true
        OfflineLocalStore.Theme.LIGHT -> false
        OfflineLocalStore.Theme.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    BitChordTheme(darkTheme = dark) { OfflineMusicRoot(store) { themeVersion++ } }
}

@Composable
private fun OfflineMusicRoot(store: OfflineLocalStore, onSettingsChanged: () -> Unit) {
    val context = LocalContext.current
    val controller = rememberOfflineMediaController()
    val state = rememberOfflinePlayerState(controller)
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var scanning by remember { mutableStateOf(true) }
    var permissionRequested by remember { mutableStateOf(false) }
    var notificationPermissionRequested by remember { mutableStateOf(false) }
    var nowPlaying by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }

    val audioPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionRequested = true
        if (!granted) Toast.makeText(context, "Music access is required to show your library", Toast.LENGTH_LONG).show()
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationPermissionRequested = true
        if (!granted) Toast.makeText(context, "Notifications are disabled. Playback still works, but media controls may be hidden.", Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(permissionRequested, refresh) {
        if (!DeviceMusicLibrary.hasPermission(context)) {
            if (!permissionRequested) audioLauncher.launch(audioPermission)
            scanning = false
            return@LaunchedEffect
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !notificationPermissionRequested
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        scanning = true
        songs = withContext(Dispatchers.IO) { DeviceMusicLibrary.scan(context) }
        scanning = false
    }

    BackHandler(enabled = nowPlaying || settings) { if (nowPlaying) nowPlaying = false else settings = false }
    if (settings) {
        OfflineLocalSettingsScreen(store, { settings = false }, { refresh++; onSettingsChanged() })
        return
    }
    if (nowPlaying && controller != null) {
        OfflineNowPlayingScreen(controller, state, { nowPlaying = false })
        return
    }

    val current = state.song
    Box(Modifier.fillMaxSize()) {
        when {
            scanning -> Text("Scanning your music...", Modifier.align(Alignment.Center))
            !DeviceMusicLibrary.hasPermission(context) -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.LibraryMusic, null)
                Text("Allow music access to use BitChord Offline", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                Text("Your files stay on this device. No account or internet connection is required.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            else -> OfflineLibraryScreen(
                songs,
                current,
                state.isPlaying,
                { queue, index -> queue.getOrNull(index)?.let(store::recordHistory); controller?.playOfflineSongs(queue, index) },
                { song -> store.recordHistory(song); Toast.makeText(context, "${song.title}\n${song.artist}", Toast.LENGTH_SHORT).show() },
                PaddingValues(bottom = if (current == null) 24.dp else 92.dp),
                store,
            )
        }
        current?.let { song ->
            OfflineMiniPlayer(
                song,
                state.isPlaying,
                { if (state.isPlaying) controller?.pause() else controller?.play() },
                { controller?.skipOfflineNext() },
                { nowPlaying = true },
                Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }
}

@Composable
private fun OfflineMiniPlayer(song: Song, isPlaying: Boolean, onPlayPause: () -> Unit, onNext: () -> Unit, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onExpand).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(song.artworkAt(ROW_ART_PX), null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onPlayPause) { Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) "Pause" else "Play") }
        IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, "Next") }
    }
}
