package com.music.bitchord

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.playOfflineSongs
import com.music.bitchord.playback.rememberOfflineMediaController
import com.music.bitchord.playback.rememberOfflinePlayerState
import com.music.bitchord.ui.OfflineLibraryScreen
import com.music.bitchord.ui.components.MiniPlayer
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.theme.BitChordTheme
import dev.chrisbanes.haze.HazeState
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
    val hazeState = remember { HazeState() }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var scanning by remember { mutableStateOf(true) }
    var permissionRequested by remember { mutableStateOf(false) }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else Manifest.permission.READ_EXTERNAL_STORAGE

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionRequested = true
        if (!granted) Toast.makeText(context, "Music access is required to show your library", Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(permissionRequested) {
        if (!LocalMediaRepository.hasStoragePermission(context)) {
            if (!permissionRequested) launcher.launch(permission)
            scanning = false
            return@LaunchedEffect
        }
        scanning = true
        songs = withContext(Dispatchers.IO) { LocalMediaRepository.getLocalMusic(context) }
        scanning = false
    }

    val currentSong = playerState.song
    val bottomPadding = if (currentSong == null) 24.dp else 92.dp

    Box(Modifier.fillMaxSize()) {
        when {
            scanning -> Text("Scanning your music...", modifier = Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleMedium)
            !LocalMediaRepository.hasStoragePermission(context) -> Column(Modifier.align(Alignment.Center).padding(PAGE_GUTTER), horizontalAlignment = Alignment.CenterHorizontally) {
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
            MiniPlayer(
                song = song,
                isPlaying = playerState.isPlaying,
                isLoading = playerState.isLoading,
                hazeState = hazeState,
                onPlayPause = { if (playerState.isPlaying) controller?.pause() else controller?.play() },
                onNext = { controller?.seekToNextMediaItem() },
                onExpand = { Toast.makeText(context, song.title, Toast.LENGTH_SHORT).show() },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            )
        }
    }
}
