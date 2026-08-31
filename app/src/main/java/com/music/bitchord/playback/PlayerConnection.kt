package com.music.bitchord.playback

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Stable
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
    /**
     * The playhead. A field rather than a value: its identity never changes, so
     * carrying it here costs no invalidation — see [PlaybackPosition].
     */
    val position: PlaybackPosition = PlaybackPosition(),
    /**
     * Left here rather than moved alongside the position: it settles once per
     * track, and [mutableStateOf] compares structurally, so the poll writing it
     * back unchanged every tick invalidates nothing.
     */
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

/** Routes the player-screen AutoPlay button through the playback service. */
fun MediaController.toggleAutoplay() {
    sendCustomCommand(
        SessionCommand(ACTION_TOGGLE_AUTOPLAY, Bundle.EMPTY),
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
            // Synced here too, so seeking while paused or buffering still moves
            // the scrubber (the poll loop only runs on play).
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

    // Only while the app is on screen. The poll exists to move a scrubber, and
    // a scrubber behind a locked screen is not being read — but the loop is a
    // plain `delay`, so without this it went on making two binder round-trips a
    // second to the media session for the whole time the phone was in a pocket.
    // Nothing is lost by stopping: `sync` above runs on the controller's own
    // events, and the first thing that happens on the way back is a fresh read.
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
 *
 * It has to round-trip losslessly for everything [LastPlayed] stores, because
 * the queue it saves is read back out of the *player* — so a field dropped here
 * is a field that does not survive a restart, however carefully it is
 * persisted. That is what happened to [Song.durationText]: stored, restored,
 * and always null, because this function never carried it back off the item in
 * the first place.
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

/**
 * Marks a queue entry as AutoPlay's rather than the user's. Carried on the
 * MediaItem so it survives the trip through the session — the queue belongs to
 * the player, and the UI only ever sees it back through a MediaController.
 */
private const val EXTRA_FROM_AUTOPLAY = "bitchord.fromAutoplay"
private const val EXTRA_QUEUE_ORIGIN = "bitchord.queueOrigin"
private const val EXTRA_TRACK_NUMBER = "bitchord.trackNumber"

/**
 * The artist and album pages this track hangs under, when they are known.
 *
 * Carried so they survive the round trip through the session: the player's own
 * menu backfills them with a lookup when they are missing (see MainActivity's
 * `links`), but a queue restored after a restart, or a track read back by the
 * service, has only what the item carries.
 */
private const val EXTRA_ARTIST_ID = "bitchord.artistId"
private const val EXTRA_ALBUM_ID = "bitchord.albumId"

/** @see Song.localUri */
private const val EXTRA_LOCAL_URI = "bitchord.localUri"

/** @see Song.localPath */
private const val EXTRA_LOCAL_PATH = "bitchord.localPath"

/**
 * How long the track runs, as the row that queued it said.
 *
 * On the item rather than left to [MediaMetadata.durationMs] because that field
 * is the *player's* to state, and the player takes its own figure from the
 * decoder. This one is the claim a cross-source match is made on — see
 * [TrackMatcher] — and the two disagree often enough that overwriting either
 * with the other loses information. Carried so that [toSong] can give it back,
 * which is what [LastPlayed] saves and what puts `&d=` on a restored track's
 * playback URI.
 */
private const val EXTRA_DURATION = "bitchord.durationText"

/**
 * Where AutoPlay's section of the queue begins, and so where a track queued by
 * hand belongs — above the mix, below everything the user picked.
 *
 * Read as "the first of AutoPlay's tracks still to come", which is what keeps
 * it below the playing track even when the mix itself is what's playing: the
 * tracks of it already behind you count as played, and the section starts
 * again below the needle. Tracks put in by hand there — "Play next" while the
 * mix runs — stay above it too, for the same reason.
 *
 * The queue panel draws its AutoPlay heading at this same index.
 */
fun autoplaySectionStart(fromAutoplay: List<Boolean>, currentIndex: Int): Int {
    val after = (currentIndex + 1).coerceIn(0, fromAutoplay.size)
    return (after until fromAutoplay.size).firstOrNull { fromAutoplay[it] }
        ?: fromAutoplay.size
}

fun MediaController.autoplaySectionStart(): Int = autoplaySectionStart(
    fromAutoplay = (0 until mediaItemCount).map { getMediaItemAt(it).fromAutoplay },
    currentIndex = currentMediaItemIndex,
)

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
/**
 * MP4-family containers (m4a/aac/amr/wma/...) store their header or trailing
 * metadata in a way that needs backward seeking to parse, which the
 * content:// route (ContentDataSource) doesn't reliably support — the same
 * bytes read fine as a plain file. Formats like flac/mp3/ogg/webm already
 * seek correctly through content:// and are left alone.
 */
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

/**
 * The `&n=&a=&d=` tail every playback URI carries: what this track is, in the
 * terms [com.music.bitchord.data.sources.TrackMatcher] compares recordings on.
 *
 * The runtime is the one of the three that can rule a candidate *out* on its
 * own, and it is only ever a hint here — a row that never carried a duration
 * simply omits it and the match is made on title and artist alone, as it was
 * before.
 */
private fun Song.matchQuery(): String = buildString {
    append("&n=").append(Uri.encode(title))
    append("&a=").append(Uri.encode(artist))
    TrackMatcher.secondsOf(durationText)?.let { append("&d=").append(it) }
}

fun Song.toMediaItem(): MediaItem {
    val sourceTrack = SourceRegistry.parseTrackKey(videoId)
    // A row from search or a playlist carries no file of its own, but the track
    // may still be on disk from a download — see [Downloads.saved].
    //
    // Answered *here*, where the item is built, rather than in the player's
    // stream resolver, because everything downstream decides what to do by the
    // scheme the item arrives with: [AudioCache.playbackFactory] sends file and
    // content URIs past the disk cache instead of writing a second copy of
    // them, and DefaultDataSource picks ContentDataSource off the same scheme.
    // A local URI substituted further down lands inside the half of the chain
    // that only speaks HTTP, where OkHttp rejects it as a malformed URL — which
    // is what a downloaded track played from search used to do, four times over,
    // before giving up.
    //
    // Both halves are checked, not just the record: a claim about a folder this
    // app does not own — see [Downloads] — outlives the file it names whenever
    // one is deleted from a file manager, and trusting either unchecked sent the
    // player a `file://` uri to a path that had simply stopped existing.
    //
    // [localUri] needs it just as much as the lookup does, and for a reason that
    // is easy to miss: it is not only set from a folder read that just verified
    // the file. It also round-trips off the player's own item through
    // [MediaItem.toSong], and is persisted and restored by [LastPlayed] — so a
    // queue restored after a restart carries whatever was true whenever it was
    // last saved. Checking only the lookup leaves exactly that path unguarded,
    // which is the one a resumed queue takes.
    val offlineUri = localUri?.takeUnless(Downloads::isMissingLocalFile)
        ?: Downloads.verifiedSavedUri(videoId)
    val uriString = offlineUri ?: when {
        videoId.startsWith("content://") || videoId.startsWith("file://") -> videoId
        // Title, artist and runtime ride along in the URI because they are what
        // a cross-source match is made on, and the resolver runs on ExoPlayer's
        // loader thread with nothing but a DataSpec in hand — see
        // [SourceResolver.resolve]. Read-ahead resolves tracks that aren't the
        // current item, so reaching back for the session's metadata isn't an
        // option either.
        sourceTrack != null -> SourceRegistry.trackUri(sourceTrack.first, sourceTrack.second)
            .let { "$it${matchQuery()}" }
        // The same three fields, for the same reason, on the YouTube path: a
        // source ranked above YouTube gets offered this track before YouTube
        // resolves it — see [SourceResolver.substituteForYouTube] — and that
        // match is made on them, which the loader thread has no other way to
        // reach.
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
            // The release this track came off, when whoever queued it knew.
            //
            // A native field rather than an extra because Media3 bundles this
            // one across the session on its own, and because the lock screen and
            // Android Auto both draw it — a track queued from an album page had
            // the name in hand all along and was arriving at those surfaces
            // without it. It is also what the Replay's album chart is counted
            // on: read back off the player, a track with no album here is a
            // track that cannot be filed under one.
            .setAlbumTitle(albumName)
            // Sized here rather than left as stored: this is what the lock
            // screen, the notification and Android Auto draw, all of them
            // large, and none of them go back for a better copy later.
            .setArtworkUri(artworkAt(NOTIFICATION_ART_PX)?.toUri())
            // System media surfaces (One UI's Now Bar, Android Auto, Assistant)
            // classify a session by its media type; untyped sessions get treated
            // as generic audio and lose the music-specific card.
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            // What a queue entry has to carry about itself: which section of
            // the queue it belongs to, whether it is playing off the device,
            // and how long the row that queued it said it runs. The uri two
            // lines up answers the second question but does not survive the
            // trip back out — Media3 leaves a MediaItem's localConfiguration
            // out of the bundle it sends to a MediaController — so without this
            // a track playing from a file reaches the UI looking like any other
            // YouTube track, and the player's menu offers to rate, download and
            // share it.
            //
            // Set for every track rather than only the local and AutoPlay ones,
            // because the runtime applies to all of them: gated on those two, a
            // plain YouTube track carried no extras at all, so [toSong] read
            // back a null duration, [LastPlayed] stored a null, and the restored
            // queue lost the `&d=` its matching depends on.
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

/**
 * Which track a playback URI is for, as a media id — the inverse of the URI
 * [toMediaItem] builds, as far as the identity goes.
 *
 * Needed because most of what this app does to a track happens somewhere that
 * has only the URI: the resolver runs on ExoPlayer's loader thread with a
 * DataSpec in hand, and read-ahead means the track being fetched is usually not
 * the one playing. That is what makes it the answer to "whose log line is this"
 * — see [com.music.bitchord.data.TrackLog.about].
 *
 * Deliberately not the cache key, which looks similar and is not the same
 * thing: that one splits a track's renditions apart on purpose and spells a
 * source-backed track differently again, so filing lines under it would scatter
 * one song's story across several names.
 */
fun mediaIdIn(uri: Uri): String? = if (uri.authority == "source") {
    val configId = uri.getQueryParameter("s")
    val trackId = uri.getQueryParameter("t")
    if (configId != null && trackId != null) SourceRegistry.trackKey(configId, trackId) else null
} else {
    uri.getQueryParameter("v")
}

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
