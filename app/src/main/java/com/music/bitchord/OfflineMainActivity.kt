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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.playSongs
import com.music.bitchord.playback.rememberMediaController
import com.music.bitchord.playback.rememberPlayerState
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.MiniPlayer
import com.music.bitchord.ui.screens.LocalMusicScreen
import com.music.bitchord.ui.theme.BitChordTheme
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Offline edition entry point.
 *
 * This activity intentionally has no account, catalogue, search-engine,
 * download, scrobbling or URL-routing concepts. The library is the device's
 * MediaStore and Media3 is the playback engine.
 */
class OfflineMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BitChordTheme {
                OfflineMusicRoot()
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun OfflineMusicRoot() {
    val context = LocalContext.current
    val controller = rememberMediaController()
    val playerState = rememberPlayerState(controller)
    val hazeState = remember { HazeState() }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            // Permission callbacks arrive before MediaStore has necessarily
            // refreshed its index. Load on IO and let the UI settle naturally.
            scanning = true
        } else {
            Toast.makeText(context, "Music access is required to show your library", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit, scanning) {
        if (!LocalMediaRepository.hasStoragePermission(context)) {
            launcher.launch(permission)
            return@LaunchedEffect
        }
        scanning = true
        songs = withContext(Dispatchers.IO) {
            LocalMediaRepository.getLocalMusic(context)
        }
        scanning = false
    }

    val currentSong = playerState.song
    val contentPadding = PaddingValues(bottom = if (currentSong != null) 92.dp else 24.dp)

    Box(Modifier.fillMaxSize()) {
        if (!scanning && songs.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(PAGE_GUTTER),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "No music found on this device",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    "Add MP3, FLAC, M4A, AAC, OGG, OPUS or WAV files to your device and refresh.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        } else {
            LocalMusicScreen(
                songs = songs,
                onSongClick = { queue, index -> controller?.playSongs(queue, index) },
                onSongLongPress = { song ->
                    Toast.makeText(context, "${song.title}\n${song.artist}", Toast.LENGTH_SHORT).show()
                },
                onSongSwipe = { song ->
                    // Swipe remains a UI interaction but has no remote side effect.
                    Toast.makeText(context, "Removed from queue: ${song.title}", Toast.LENGTH_SHORT).show()
                },
                onShuffle = { queue ->
                    if (queue.isNotEmpty()) controller?.playSongs(queue.shuffled(), 0)
                },
                contentPadding = contentPadding,
                emptyMessage = "No local music found",
                currentSong = currentSong,
                isPlaying = playerState.isPlaying,
            )
        }

        if (currentSong != null) {
            MiniPlayer(
                song = currentSong,
                isPlaying = playerState.isPlaying,
                isLoading = playerState.isLoading,
                hazeState = hazeState,
                onPlayPause = {
                    if (playerState.isPlaying) controller?.pause() else controller?.play()
                },
                onNext = { controller?.seekToNextMediaItem() },
                onExpand = {
                    // The existing full player remains available through the
                    // Android media session; keeping this action local avoids
                    // introducing another navigation dependency here.
                    Toast.makeText(context, "Now playing: ${currentSong.title}", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            )
        }
    }
}
