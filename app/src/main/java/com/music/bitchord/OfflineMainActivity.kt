package com.music.bitchord

import android.Manifest
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.offline.DeviceMusicLibrary
import com.music.bitchord.playback.playOfflineSongs
import com.music.bitchord.playback.rememberOfflineMediaController
import com.music.bitchord.playback.rememberOfflinePlayerState
import com.music.bitchord.playback.skipOfflineNext
import com.music.bitchord.ui.OfflineLibraryScreen
import com.music.bitchord.ui.OfflineNowPlayingScreen
import com.music.bitchord.ui.theme.BitChordTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OfflineMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BitChordTheme { OfflineMusicRoot() } }
    }
}

@Composable
private fun OfflineMusicRoot() {
    val context = LocalContext.current
    val controller = rememberOfflineMediaController()
    val playerState = rememberOfflinePlayerState(controller)
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var scanning by remember { mutableStateOf(true) }
    var permissionRequested by remember { mutableStateOf(false) }
    var nowPlaying by remember { mutableStateOf(false) }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionRequested = true
        if (!granted) Toast.makeText(context, "Music access is required to show your library", Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(permissionRequested) {
        if (!DeviceMusicLibrary.hasPermission(context)) {
            if (!permissionRequested) launcher.launch(permission)
            scanning = false
            return@LaunchedEffect
        }
        scanning = true
        songs = withContext(Dispatchers.IO) { DeviceMusicLibrary.scan(context) }
        scanning = false
    }

    BackHandler(enabled = nowPlaying) { nowPlaying = false }
    if (nowPlaying && controller != null) {
        OfflineNowPlayingScreen(controller = controller, state = playerState, onBack = { nowPlaying = false })
        return
    }

    val currentSong = playerState.song
    val bottomPadding = if (currentSong == null) 24.dp else 92.dp
    Box(Modifier.fillMaxSize()) {
        when {
            scanning -> Text("Scanning your music...", modifier = Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleMedium)
            !DeviceMusicLibrary.hasPermission(context) -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.LibraryMusic, contentDescription = null)
                Text("Allow music access to use BitChord Offline", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                Text("Your files stay on this device. No account or internet connection is required.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            else -> OfflineLibraryScreen(
                songs = songs,
                currentSong = currentSong,
                isPlaying = playerState.isPlaying,
                onSongClick = { queue, index -> controller?.playOfflineSongs(queue, index) },
                onSongLongPress = { song -> Toast.makeText(context, "${song.title}\n${song.artist}", Toast.LENGTH_SHORT).show() },
                contentPadding = PaddingValues(bottom = bottomPadding),
            )
        }
        currentSong?.let { song ->
            OfflineMiniPlayer(
                song = song,
                isPlaying = playerState.isPlaying,
                onPlayPause = { if (playerState.isPlaying) controller?.pause() else controller?.play() },
                onNext = { controller?.skipOfflineNext() },
                onExpand = { nowPlaying = true },
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun OfflineMiniPlayer(song: Song, isPlaying: Boolean, onPlayPause: () -> Unit, onNext: () -> Unit, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onExpand).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(model = song.artworkAt(ROW_ART_PX), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onPlayPause) { Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (isPlaying) "Pause" else "Play") }
        IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, contentDescription = "Next") }
    }
}
