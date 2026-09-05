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
import com.music.bitchord.ui.rememberIsForeground
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
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
    /** True while the current item is the replacement installed by a quality upgrade. */
    val isQualityUpgraded: Boolean = false,
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

/** Clears the previous queue's service and restart state before starting radio. */
suspend fun MediaController.beginRadioQueue() {
    sendCustomCommand(
        SessionCommand(ACTION_BEGIN_RADIO_QUEUE, Bundle.EMPTY),
        Bundle.EMPTY,
    ).await()
}

/**
 * Asks the service to go looking for a better copy of the playing track, now,
 * because the listener asked for it.
 *
 * A session command rather than a `replaceMediaItem` from here: the item has to
 * be rebuilt, the track's cached rendition has to be dropped and the automatic
 * path's verdicts about it have to be cleared, and all three live on the
 * service's side of the session — see [PlaybackService]'s `upgradeQualityNow`.
 */
fun MediaController.upgradeQuality() {
    sendCustomCommand(
        SessionCommand(ACTION_UPGRADE_QUALITY, Bundle.EMPTY),
        Bundle.EMPTY,
    )
}

/** Flushes the current radio queue to disk before reporting that it started. */
suspend fun MediaController.commitRadioQueue() {
    sendCustomCommand(
        SessionCommand(ACTION_COMMIT_RADIO_QUEUE, Bundle.EMPTY),
        Bundle.EMPTY,
    ).await()
}

/** Mirrors the controller into Compose state, polling position while playing. */
@Composable
fun rememberPlayerState(controller: MediaController?): PlayerState {
    val position = remember { PlaybackPosition() }
    var state by remember { mutableStateOf(PlayerState(position = position)) }

    DisposableEffect(controller) {
        val player = controller ?: return@DisposableEffect onDispose {}

        // A MediaController reads each item across the session boundary. Keep
        // the converted queue until its timeline actually changes; playback,
        // buffering, repeat and metadata events do not require another O(n)
        // walk over a large playlist.
        var queueSnapshot = emptyList<Song>()

        fun sync(
            error: String? = null,
            rebuildQueue: Boolean = false,
            refreshCurrentQueueItem: Boolean = false,
        ) {
            val item = player.currentMediaItem
            if (rebuildQueue) {
                queueSnapshot = (0 until player.mediaItemCount)
                    .map { player.getMediaItemAt(it).toSong() }
            } else if (refreshCurrentQueueItem && item != null) {
                val index = player.currentMediaItemIndex
                if (index in queueSnapshot.indices) {
                    queueSnapshot = queueSnapshot.toMutableList().also { it[index] = item.toSong() }
                }
            }
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
                queue = queueSnapshot,
                queueIndex = player.currentMediaItemIndex,
                hasPrevious = player.hasPreviousMediaItem(),
                hasNext = player.hasNextMediaItem(),
                isQualityUpgraded = item?.mediaMetadata?.extras
                    ?.getBoolean(EXTRA_QUALITY_UPGRADED) == true,
            )
        }

        val listener = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) = sync(
                error = state.error,
                rebuildQueue = events.contains(Player.EVENT_TIMELINE_CHANGED),
                refreshCurrentQueueItem = events.contains(Player.EVENT_MEDIA_METADATA_CHANGED),
            )
            override fun onPlayerErrorChanged(error: androidx.media3.common.PlaybackException?) {
                sync(error?.let { "Playback failed: ${it.errorCodeName}" })
            }
        }
        player.addListener(listener)
        sync(rebuildQueue = true)
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
 * It has to round-trip the fields used by the live queue UI and playback
 * service. A field dropped here disappears as soon as a Song passes through
 * Media3, because controllers and service helpers read it back through this.
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
    isExplicit = mediaMetadata.extras?.takeIf { it.containsKey(EXTRA_EXPLICIT) }
        ?.getBoolean(EXTRA_EXPLICIT),
    isVideo = mediaMetadata.extras?.getBoolean(EXTRA_IS_VIDEO) == true,
    isVideoOrigin = mediaMetadata.extras?.getBoolean(EXTRA_VIDEO_ORIGIN) == true ||
        mediaMetadata.extras?.getBoolean(EXTRA_IS_VIDEO) == true,
    setVideoId = mediaMetadata.extras?.getString(EXTRA_SET_VIDEO_ID),
    fromAutoplay = this.fromAutoplay,
    radioName = mediaMetadata.extras?.getString(EXTRA_RADIO_NAME),
    localUri = mediaMetadata.extras?.getString(EXTRA_LOCAL_URI),
    localPath = mediaMetadata.extras?.getString(EXTRA_LOCAL_PATH),
    localLyricsUri = mediaMetadata.extras?.getString(EXTRA_LOCAL_LYRICS_URI),
    localLyricsSource = mediaMetadata.extras?.getString(EXTRA_LOCAL_LYRICS_SOURCE),
    localLyricsFormat = mediaMetadata.extras?.getString(EXTRA_LOCAL_LYRICS_FORMAT),
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

/** @see Song.radioName */
private const val EXTRA_RADIO_NAME = "bitchord.radioName"

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

/** @see Song.setVideoId */
private const val EXTRA_SET_VIDEO_ID = "bitchord.setVideoId"

/** @see Song.localUri */
private const val EXTRA_LOCAL_URI = "bitchord.localUri"
private const val EXTRA_LOCAL_PATH = "bitchord.localPath"
private const val EXTRA_LOCAL_LYRICS_URI = "bitchord.localLyricsUri"
private const val EXTRA_LOCAL_LYRICS_SOURCE = "bitchord.localLyricsSource"
private const val EXTRA_LOCAL_LYRICS_FORMAT = "bitchord.localLyricsFormat"

/**
 * How long the track runs, as the row that queued it said.
 *
 * On the item rather than left to [MediaMetadata.durationMs] because that field
 * is the *player's* to state, and the player takes its own figure from the
 * decoder. This one is the claim a cross-source match is made on — see
 * [TrackMatcher] — and the two disagree often enough that overwriting either
 * with the other loses information. Carried so that [toSong] can give it back
 * to the live queue and matching code.
 */
private const val EXTRA_DURATION = "bitchord.durationText"
private const val EXTRA_EXPLICIT = "bitchord.explicit"
private const val EXTRA_IS_VIDEO = "bitchord.isVideo"
private const val EXTRA_VIDEO_ORIGIN = "bitchord.isVideoOrigin"

/** Set only on the replacement item produced by [QualityUpgrade]. */
const val EXTRA_QUALITY_UPGRADED = "bitchord.qualityUpgraded"

/** Whether this item was selected from a music-video row, even after conversion. */
val MediaItem.isVideoOrigin: Boolean
    get() = mediaMetadata.extras?.getBoolean(EXTRA_VIDEO_ORIGIN) == true ||
        mediaMetadata.extras?.getBoolean(EXTRA_IS_VIDEO) == true

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
 * A video-tagged [Song] plays using the video's own audio by default. The
 * player can explicitly replace just the current item with a catalogue match.
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
    albumName?.takeIf { it.isNotBlank() }?.let { append("&l=").append(Uri.encode(it)) }
    isExplicit?.let { append("&e=").append(if (it) "1" else "0") }
    // `m` controls source routing, not player policy. A converted catalogue
    // track keeps [isVideoOrigin] for AutoMix, but is intentionally audio here
    // so the normal source-quality upgrade path can improve it.
    if (isVideo) append("&m=1")
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
    // [MediaItem.toSong], so an item waiting in the live queue can carry a URI
    // that has become stale since it was created. Checking only the lookup
    // leaves that path unguarded.
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
        // A track the listener reverted by hand stays reverted — see
        // [OriginalVersion]. Applied here rather than at the one menu that
        // reverts, because "stays" has to mean the next queue entry built for
        // this song too: another play from a list, a restored queue, Android
        // Auto. Everything downstream reads the item's URI and nothing reads
        // the preference, so this is the only place it has to be said.
        OriginalVersion.isPinned(videoId) -> directYouTubeUri()
        // The same three fields, for the same reason, on the YouTube path: a
        // source ranked above YouTube gets offered this track before YouTube
        // resolves it — see [SourceResolver.substituteForYouTube] — and that
        // match is made on them, which the loader thread has no other way to
        // reach.
        else -> "bitchord://watch?v=$videoId${matchQuery()}"
    }
    return MediaItem.Builder()
        .setMediaId(videoId)
        .setUri(resolvePlaybackUri(uriString, localPath))
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
            // plain YouTube track carries no extras at all, so [toSong] reads
            // back a null duration and later matching loses the `&d=` it
            // depends on.
            .apply {
                val lyricsUri = localLyricsUri ?: Downloads.savedLyricsUri(videoId)
                val lyricsSource = localLyricsSource ?: Downloads.savedLyricsSource(videoId)
                val lyricsFormat = localLyricsFormat ?: Downloads.savedLyricsFormat(videoId)
                if (fromAutoplay || offlineUri != null || durationText != null ||
                    artistId != null || albumId != null || lyricsUri != null ||
                    setVideoId != null || isExplicit != null || isVideo || isVideoOrigin || radioName != null
                ) {
                    setExtras(
                        bundleOf(
                            EXTRA_FROM_AUTOPLAY to fromAutoplay,
                            EXTRA_RADIO_NAME to radioName,
                            EXTRA_LOCAL_URI to offlineUri,
                            EXTRA_LOCAL_PATH to localPath,
                            EXTRA_LOCAL_LYRICS_URI to lyricsUri,
                            EXTRA_LOCAL_LYRICS_SOURCE to lyricsSource,
                            EXTRA_LOCAL_LYRICS_FORMAT to lyricsFormat,
                            EXTRA_DURATION to durationText,
                            EXTRA_ARTIST_ID to artistId,
                            EXTRA_ALBUM_ID to albumId,
                            EXTRA_SET_VIDEO_ID to setVideoId,
                            EXTRA_EXPLICIT to isExplicit,
                            EXTRA_IS_VIDEO to isVideo,
                            EXTRA_VIDEO_ORIGIN to isVideoOrigin,
                        ),
                    )
                }
            }
            .build(),
    )
    .build()
}

/**
 * The current song reopened through YouTube alone, bypassing every substitute
 * and quality-upgrade path.  The separate rendition tag is essential: the
 * base cache key may currently contain JioSaavn or module bytes, and resuming
 * that entry as though it were a YouTube WebM corrupts the stream.
 */
fun Song.toDirectYouTubeMediaItem(): MediaItem =
    toMediaItem().buildUpon()
        .setUri(directYouTubeUri())
        .build()

private fun Song.directYouTubeUri(): String =
    "bitchord://watch?v=$videoId${matchQuery()}&$DIRECT_YOUTUBE_PARAMETER=1&q=original"

/**
 * Whether there is a YouTube upload behind this song to go back *to* — the
 * question "Revert to original" only means something for.
 *
 * Offered for any track that has one rather than only for a track a quality
 * upgrade has visibly swapped, because the swap is not the only way to end up
 * on a copy that is wrong. A source ranked above YouTube gets first refusal on
 * every track — see [SourceResolver.substituteForYouTube] — so a song can be
 * playing JioSaavn's or a module's idea of it from its very first second, with
 * nothing on screen having changed and nothing to undo. Those are precisely the
 * ones worth doubting: the match is made on title, artist and runtime, and a
 * live version, a remaster or a different mix agreeing on all three is a
 * catalogue's ordinary business. The listener is the only one who can hear that
 * it is the wrong recording, and until this was always available they had no
 * way to say so.
 *
 * The three exclusions are all "there is no such upload", not "reverting would
 * be unwise":
 *
 *  - a track queued from a module's own catalogue has no YouTube id at all,
 *    only a source-and-track key that would build a nonsense URI;
 *  - a file on the device is identified by its own `content://` or `file://`
 *    URI, for the same reason;
 *  - a music video *is* the YouTube upload, so there is nowhere for it to go.
 *    Its catalogue match is a separate control with its own way back — see
 *    `VideoAudioVersionButton`.
 */
fun Song.hasYouTubeOriginal(): Boolean =
    videoId.isNotBlank() &&
        !videoId.startsWith("content://") &&
        !videoId.startsWith("file://") &&
        !isVideo &&
        SourceRegistry.parseTrackKey(videoId) == null

/** A playback URI carrying this is explicitly requested YouTube, never a substitute. */
const val DIRECT_YOUTUBE_PARAMETER = "direct_youtube"

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
    val queue = if (shuffled) {
        QueueShuffle.startingOrder(songs, startIndex.coerceIn(songs.indices))
    } else {
        queueStartingAt(songs, startIndex)
    }
    setMediaItems(queue.map { it.toMediaItem() }, 0, 0L)
    prepare()
    play()
}
