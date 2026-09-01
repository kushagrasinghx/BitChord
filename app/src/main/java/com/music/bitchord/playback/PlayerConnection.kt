package com.music.bitchord.playback

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.music.bitchord.data.model.NOTIFICATION_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.data.sources.TrackMatcher
import com.music.bitchord.download.Downloads
import com.music.bitchord.playback.smart.QueueOrigin
import com.music.bitchord.ui.rememberIsForeground
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

/**
 * The playhead, deliberately kept out of [PlayerState].
 *
 * It moves twice a second; everything else on [PlayerState] moves on a track
 * change. Carried in the same object, the two are one snapshot read — and
 * [rememberPlayerState] returns a value, which makes it non-restartable, which
 * pushes that read up into its *caller's* scope. In this app the caller is the
 * root of the whole UI, so a ticking playhead invalidated the entire tree twice
 * a second: every tab, both floating bars, and the three real-time blurs
 * underneath them, whether or not anything on screen showed a position.
 *
 * Split out and held behind a stable object, the tick is a read of this alone.
 * Whoever draws a scrubber reads it and recomposes; nobody else hears about it.
 * Take care to keep it that way — reading [positionMs] high in the tree and
 * passing the `Long` down puts the invalidation straight back where it was.
 */
@Stable
class PlaybackPosition internal constructor() {
    var positionMs by mutableLongStateOf(0L)
        internal set
}

/** Snapshot of playback state, driven by the MediaController. */
data class PlayerState(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val position: PlaybackPosition = PlaybackPosition(),
    val durationMs: Long = 0L,
    val error: String? = null,
    val isLoading: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Song> = emptyList(),
    val queueIndex: Int = 0,
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

/** Routes the player-screen AutoPlay button through the playback service. */
fun MediaController.toggleAutoplay() {
    sendCustomCommand(
        SessionCommand(ACTION_TOGGLE_AUTOPLAY, Bundle.EMPTY),
        Bundle.EMPTY,
    )
}

/** Triggers an immediate DJ AutoMix transition into the next track on demand. */
fun MediaController.triggerSmartMixNow() {
    sendCustomCommand(
        SessionCommand(ACTION_TRIGGER_SMART_MIX_NOW, Bundle.EMPTY),
        Bundle.EMPTY,
    )
}

/** Mirrors the controller into Compose state, polling position while playing. */
@Composable
fun rememberPlayerState(controller: MediaController?): PlayerState {
    val position = remember { PlaybackPosition() }
    var state by remember { mutableStateOf(PlayerState(position = position)) }

    DisposableEffect(controller) {
        val player = controller ?: return@DisposableEffect onDispose {}

        fun sync(error: String? = null) {
            val item = player.currentMediaItem
            position.positionMs = player.currentPosition.coerceAtLeast(0L)
            state = state.copy(
                song = item?.toSong(),
                isPlaying = player.isPlaying,
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

    val foreground = rememberIsForeground()
    LaunchedEffect(controller, state.isPlaying, foreground) {
        while (controller != null && state.isPlaying && foreground) {
            position.positionMs = controller.currentPosition.coerceAtLeast(0L)
            val duration = controller.duration.coerceAtLeast(0L)
            if (duration != state.durationMs) state = state.copy(durationMs = duration)
            delay(500)
        }
    }
    return state
}

/**
 * The inverse of [toMediaItem], as far as a MediaItem can carry a [Song].
 */
fun MediaItem.toSong() = Song(
    videoId = mediaId,
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.artist?.toString().orEmpty(),
    thumbnailUrl = mediaMetadata.artworkUri?.toString(),
    durationText = mediaMetadata.extras?.getString(EXTRA_DURATION),
    artistId = mediaMetadata.extras?.getString(EXTRA_ARTIST_ID),
    albumId = mediaMetadata.extras?.getString(EXTRA_ALBUM_ID),
    albumName = mediaMetadata.albumTitle?.toString(),
    fromAutoplay = this.fromAutoplay,
    queueOrigin = queueOrigin,
    trackNumber = mediaMetadata.extras?.getInt(EXTRA_TRACK_NUMBER)?.takeIf { it > 0 },
    localUri = mediaMetadata.extras?.getString(EXTRA_LOCAL_URI),
    localPath = mediaMetadata.extras?.getString(EXTRA_LOCAL_PATH),
)

/** @see Song.fromAutoplay */
val MediaItem.fromAutoplay: Boolean
    get() = mediaMetadata.extras?.getBoolean(EXTRA_FROM_AUTOPLAY) == true

val MediaItem.queueOrigin: QueueOrigin
    get() = mediaMetadata.extras?.getString(EXTRA_QUEUE_ORIGIN)?.let {
        runCatching { QueueOrigin.valueOf(it) }.getOrNull()
    } ?: if (fromAutoplay) QueueOrigin.AUTOPLAY else QueueOrigin.USER_QUEUE

val MediaItem.trackNumber: Int?
    get() = mediaMetadata.extras?.getInt(EXTRA_TRACK_NUMBER)?.takeIf { it > 0 }

private const val EXTRA_FROM_AUTOPLAY = "bitchord.fromAutoplay"
private const val EXTRA_QUEUE_ORIGIN = "bitchord.queueOrigin"
private const val EXTRA_TRACK_NUMBER = "bitchord.trackNumber"
private const val EXTRA_ARTIST_ID = "bitchord.artistId"
private const val EXTRA_ALBUM_ID = "bitchord.albumId"
private const val EXTRA_LOCAL_URI = "bitchord.localUri"
private const val EXTRA_LOCAL_PATH = "bitchord.localPath"
private const val EXTRA_DURATION = "bitchord.durationText"

fun autoplaySectionStart(fromAutoplay: List<Boolean>, currentIndex: Int): Int {
    val after = (currentIndex + 1).coerceIn(0, fromAutoplay.size)
    return (after until fromAutoplay.size).firstOrNull { fromAutoplay[it] }
        ?: fromAutoplay.size
}

fun MediaController.autoplaySectionStart(): Int = autoplaySectionStart(
    fromAutoplay = (0 until mediaItemCount).map { getMediaItemAt(it).fromAutoplay },
    currentIndex = currentMediaItemIndex,
)

private val DIRECT_FILE_URI_EXTENSIONS = setOf(
    "m4a", "m4b", "m4p", "mp4", "aac", "3ga", "3gp", "3gpp",
    "alac", "amr", "awb", "wma", "aif", "aiff", "ac3", "dts",
)

private fun resolvePlaybackUri(uriString: String, localPath: String?): String {
    if (localPath.isNullOrBlank() || !uriString.startsWith("content://")) return uriString
    val ext = localPath.substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (ext !in DIRECT_FILE_URI_EXTENSIONS) return uriString
    val file = File(localPath)
    return if (file.exists() && file.canRead()) Uri.fromFile(file).toString() else uriString
}

private fun Song.matchQuery(): String = buildString {
    append("&n=").append(Uri.encode(title))
    append("&a=").append(Uri.encode(artist))
    TrackMatcher.secondsOf(durationText)?.let { append("&d=").append(it) }
}

fun Song.toMediaItem(): MediaItem {
    val sourceTrack = SourceRegistry.parseTrackKey(videoId)
    val offlineUri = localUri?.takeUnless(Downloads::isMissingLocalFile)
        ?: Downloads.verifiedSavedUri(videoId)
    val uriString = offlineUri ?: when {
        videoId.startsWith("content://") || videoId.startsWith("file://") -> videoId
        sourceTrack != null -> SourceRegistry.trackUri(sourceTrack.first, sourceTrack.second)
            .let { "$it${matchQuery()}" }
        else -> "bitchord://watch?v=$videoId${matchQuery()}"
    }
    val preferredUri = if (offlineUri == null && QualityUpgrade.shelvedFor(videoId) != null &&
        !uriString.contains("${QualityUpgrade.MARKER}=")
    ) {
        QualityUpgrade.upgradedUri(uriString)
    } else uriString
    return MediaItem.Builder()
        .setMediaId(videoId)
        .setUri(resolvePlaybackUri(preferredUri, localPath))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(albumName)
                .setArtworkUri(artworkAt(NOTIFICATION_ART_PX)?.toUri())
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .apply {
                    if (fromAutoplay || queueOrigin != QueueOrigin.USER_QUEUE || trackNumber != null ||
                        offlineUri != null || durationText != null || artistId != null || albumId != null
                    ) {
                        setExtras(
                            bundleOf(
                                EXTRA_FROM_AUTOPLAY to fromAutoplay,
                                EXTRA_QUEUE_ORIGIN to queueOrigin.name,
                                EXTRA_TRACK_NUMBER to trackNumber,
                                EXTRA_LOCAL_URI to offlineUri,
                                EXTRA_LOCAL_PATH to localPath,
                                EXTRA_DURATION to durationText,
                                EXTRA_ARTIST_ID to artistId,
                                EXTRA_ALBUM_ID to albumId,
                            ),
                        )
                    }
                }
                .build(),
        )
        .build()
}

fun mediaIdIn(uri: Uri): String? = if (uri.authority == "source") {
    val configId = uri.getQueryParameter("s")
    val trackId = uri.getQueryParameter("t")
    if (configId != null && trackId != null) SourceRegistry.trackKey(configId, trackId) else null
} else {
    uri.getQueryParameter("v")
}

fun MediaController.playSongs(songs: List<Song>, startIndex: Int) {
    if (songs.isEmpty()) return
    val shuffled = QueueShuffle.enabled.value
    val queue = if (shuffled) QueueShuffle.startingOrder(songs, startIndex) else songs
    setMediaItems(queue.map { it.toMediaItem() }, if (shuffled) 0 else startIndex, 0L)
    prepare()
    play()
}
