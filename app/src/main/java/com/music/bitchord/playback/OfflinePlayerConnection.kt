package com.music.bitchord.playback

import android.content.ComponentName
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.delay

class OfflinePlaybackPosition {
    var value by mutableLongStateOf(0L)
}

data class OfflinePlayerState(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val position: OfflinePlaybackPosition = OfflinePlaybackPosition(),
    val durationMs: Long = 0L,
    val queueVersion: Long = 0L,
)

@Composable
fun rememberOfflineMediaController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { controller = runCatching { future.get() }.getOrNull() },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            MediaController.releaseFuture(future)
            controller = null
        }
    }
    return controller
}

@Composable
fun rememberOfflinePlayerState(controller: MediaController?): OfflinePlayerState {
    val position = remember { OfflinePlaybackPosition() }
    var state by remember { mutableStateOf(OfflinePlayerState(position = position)) }

    DisposableEffect(controller) {
        val player = controller ?: return@DisposableEffect onDispose {}
        fun sync() {
            state = state.copy(
                song = player.currentMediaItem?.toSong(),
                isPlaying = player.isPlaying,
                isLoading = player.playbackState == Player.STATE_BUFFERING,
                durationMs = player.duration.coerceAtLeast(0L),
                queueVersion = state.queueVersion + 1L,
            )
            position.value = player.currentPosition.coerceAtLeast(0L)
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = sync()
        }
        player.addListener(listener)
        sync()
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(controller, state.isPlaying) {
        while (controller != null && state.isPlaying) {
            position.value = controller.currentPosition.coerceAtLeast(0L)
            delay(250)
        }
    }
    return state
}

fun Song.toOfflineMediaItem(): MediaItem? {
    val uri = localUri ?: return null
    return runCatching {
        val parsed = android.net.Uri.parse(uri)
        OfflineDataSource.requireLocal(androidx.media3.datasource.DataSpec(parsed))
        val artwork = thumbnailUrl?.let(android.net.Uri::parse)?.takeIf { artworkUri ->
            artworkUri.scheme.equals("content", true) ||
                artworkUri.scheme.equals("file", true) ||
                artworkUri.scheme.equals("android.resource", true)
        }
        MediaItem.Builder()
            .setMediaId(uri)
            .setUri(parsed)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(albumName)
                    .setArtworkUri(artwork)
                    .build(),
            )
            .build()
    }.getOrNull()
}

fun MediaController.playOfflineSongs(songs: List<Song>, startIndex: Int) {
    val valid = songs.mapNotNull { it.toOfflineMediaItem() }
    if (valid.isEmpty()) return
    val selectedUri = songs.getOrNull(startIndex)?.localUri
    val selectedIndex = valid.indexOfFirst { it.localConfiguration?.uri?.toString() == selectedUri }
        .takeIf { it >= 0 } ?: 0
    setMediaItems(valid, selectedIndex, 0L)
    prepare()
    play()
}

fun MediaController.seekOfflineTo(positionMs: Long) {
    seekTo(positionMs.coerceAtLeast(0L))
}

fun MediaController.skipOfflineNext() {
    if (hasNextMediaItem()) seekToNextMediaItem() else stop()
}

fun MediaController.skipOfflinePrevious() {
    if (currentPosition > 3_000L) seekTo(0L)
    else if (hasPreviousMediaItem()) seekToPreviousMediaItem()
    else seekTo(0L)
}

fun MediaController.queueSongs(): List<Song> =
    (0 until mediaItemCount).mapNotNull { getMediaItemAt(it).toSongOrNull() }

private fun MediaItem.toSong(): Song = Song(
    videoId = mediaId,
    title = mediaMetadata.title?.toString().orEmpty().ifBlank { mediaId },
    artist = mediaMetadata.artist?.toString().orEmpty().ifBlank { "Unknown Artist" },
    thumbnailUrl = mediaMetadata.artworkUri?.toString(),
    albumName = mediaMetadata.albumTitle?.toString(),
    localUri = localConfiguration?.uri?.toString(),
)

private fun MediaItem.toSongOrNull(): Song? =
    localConfiguration?.uri?.toString()?.let { toSong() }
