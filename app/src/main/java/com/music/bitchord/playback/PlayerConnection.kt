package com.music.bitchord.playback

import android.content.ComponentName
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.delay

/** Snapshot of playback state, driven by the MediaController. */
data class PlayerState(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
    /** True while ExoPlayer is buffering — including our own stream-URL resolution. */
    val isLoading: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Song> = emptyList(),
    val queueIndex: Int = 0,
    /**
     * Whether the queue has somewhere to go either side of the current track.
     * Taken from the player rather than [queueIndex], so the wrap-around of
     * repeat-all is already accounted for.
     */
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
)

/** Binds to [PlaybackService] for the lifetime of the composition. */
@Composable
fun rememberMediaController(): MediaController? {
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

/** Mirrors the controller into Compose state, polling position while playing. */
@Composable
fun rememberPlayerState(controller: MediaController?): PlayerState {
    var state by remember { mutableStateOf(PlayerState()) }

    DisposableEffect(controller) {
        val player = controller ?: return@DisposableEffect onDispose {}

        fun sync(error: String? = null) {
            val item = player.currentMediaItem
            state = state.copy(
                song = item?.toSong(),
                isPlaying = player.isPlaying,
                // Sync position here too, so seeking while paused or buffering
                // still moves the scrubber (the poll loop only runs on play).
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.coerceAtLeast(0L),
                error = error,
                isLoading = player.playbackState == Player.STATE_BUFFERING,
                repeatMode = player.repeatMode,
                queue = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toSong() },
                queueIndex = player.currentMediaItemIndex,
                hasPrevious = player.hasPreviousMediaItem(),
                hasNext = player.hasNextMediaItem(),
            )
        }

        val listener = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) = sync(state.error)
            override fun onPlayerErrorChanged(error: androidx.media3.common.PlaybackException?) {
                sync(error?.let { "Playback failed: ${it.errorCodeName}" })
            }
        }
        player.addListener(listener)
        sync()
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(controller, state.isPlaying) {
        while (controller != null && state.isPlaying) {
            state = state.copy(
                positionMs = controller.currentPosition.coerceAtLeast(0L),
                durationMs = controller.duration.coerceAtLeast(0L),
            )
            delay(500)
        }
    }
    return state
}

fun MediaItem.toSong() = Song(
    videoId = mediaId,
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.artist?.toString().orEmpty(),
    thumbnailUrl = mediaMetadata.artworkUri?.toString(),
    fromAutoplay = this.fromAutoplay,
)

/** @see Song.fromAutoplay */
val MediaItem.fromAutoplay: Boolean
    get() = mediaMetadata.extras?.getBoolean(EXTRA_FROM_AUTOPLAY) == true

/**
 * Marks a queue entry as AutoPlay's rather than the user's. Carried on the
 * MediaItem so it survives the trip through the session — the queue belongs to
 * the player, and the UI only ever sees it back through a MediaController.
 */
private const val EXTRA_FROM_AUTOPLAY = "bitchord.fromAutoplay"

/**
 * Where a track queued by hand belongs: after everything else the user picked,
 * but above AutoPlay's mix. Clamped past the playing track, so a song added
 * while the mix is already playing goes on next rather than into the past.
 */
fun MediaController.manualQueueEnd(): Int {
    val firstFromAutoplay = (0 until mediaItemCount)
        .firstOrNull { getMediaItemAt(it).fromAutoplay }
        ?: return mediaItemCount
    return maxOf(firstFromAutoplay, currentMediaItemIndex + 1).coerceAtMost(mediaItemCount)
}

/**
 * Custom scheme; PlaybackService resolves the real stream URL at play time.
 *
 * A video-tagged [Song] is expected to already have been swapped for its
 * catalogue audio release by [com.music.bitchord.data.YtMusicRepository.resolveAudio]
 * before this is called — the queue, history and the notification should
 * never see the video upload's id or title, only whatever the audio match
 * resolved to (or the video's own audio, as the deliberate fallback when no
 * match was found).
 */
fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(videoId)
    .setUri("bitchord://watch?v=$videoId")
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(thumbnailUrl?.toUri())
            // System media surfaces (One UI's Now Bar, Android Auto, Assistant)
            // classify a session by its media type; untyped sessions get treated
            // as generic audio and lose the music-specific card.
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            // Which section of the queue this belongs to, carried with it.
            .apply { if (fromAutoplay) setExtras(bundleOf(EXTRA_FROM_AUTOPLAY to true)) }
            .build(),
    )
    .build()

fun MediaController.playSongs(songs: List<Song>, startIndex: Int) {
    if (songs.isEmpty()) return
    // A queue started while shuffle is on goes in shuffled rather than being
    // played out of order — see [QueueShuffle]. The track the user picked still
    // leads, so it ends up at the top instead of at [startIndex].
    val shuffled = QueueShuffle.enabled.value
    val queue = if (shuffled) QueueShuffle.startingOrder(songs, startIndex) else songs
    setMediaItems(queue.map { it.toMediaItem() }, if (shuffled) 0 else startIndex, 0L)
    prepare()
    play()
}
