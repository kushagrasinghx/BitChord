package com.music.bitchord.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.music.bitchord.MainActivity
import com.music.bitchord.R
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.model.Song

/** Local-only playback service: every playable item must be a local URI. */
class PlaybackService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.playback_channel_name)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification_logo) },
        )

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaLibrarySession.Builder(
            this,
            player,
            LocalLibraryCallback(),
        )
            .setId(SESSION_ID)
            .setSessionActivity(sessionActivity())
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = persistQueue()
            override fun onPlaybackStateChanged(playbackState: Int) = persistQueue()
            override fun onIsPlayingChanged(isPlaying: Boolean) = persistQueue()
        })
    }

    override fun onDestroy() {
        persistQueue()
        mediaSession?.release()
        mediaSession = null
        player.release()
        super.onDestroy()
    }

    private fun persistQueue() {
        if (!::player.isInitialized || player.mediaItemCount == 0) return
        val songs = (0 until player.mediaItemCount).mapNotNull { index ->
            player.getMediaItemAt(index).toSongOrNull()
        }
        if (songs.isEmpty()) return
        LastPlayed.saveQueue(songs, player.currentMediaItemIndex)
        LastPlayed.savePlaybackState(player.currentMediaItemIndex, player.currentPosition)
    }

    private fun sessionActivity(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private inner class LocalLibraryCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(
                LibraryResult.ofItem(
                    MediaItem.Builder()
                        .setMediaId(ROOT_ID)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(getString(R.string.local_music))
                                .build(),
                        )
                        .build(),
                    params,
                ),
            )

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (parentId != ROOT_ID) {
                return Futures.immediateFuture(LibraryResult.ofItemList(emptyList(), params))
            }
            val songs = LocalMediaRepository.getLocalMusic(this@PlaybackService)
            return Futures.immediateFuture(
                LibraryResult.ofItemList(songs.mapNotNull { it.toLocalMediaItem() }, params),
            )
        }
    }

    companion object {
        private const val ROOT_ID = "offline:root"
        private const val SESSION_ID = "bitchord-offline"
        private const val CHANNEL_ID = "bitchord_playback"
    }
}

private fun Song.toLocalMediaItem(): MediaItem? {
    val uri = localUri ?: return null
    return runCatching {
        OfflineDataSource.requireLocal(
            androidx.media3.datasource.DataSpec(android.net.Uri.parse(uri)),
        )
        MediaItem.Builder()
            .setMediaId(videoId)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(albumName)
                    .setArtworkUri(thumbnailUrl?.let(android.net.Uri::parse))
                    .build(),
            )
            .build()
    }.getOrNull()
}

private fun MediaItem.toSongOrNull(): Song? =
    localConfiguration?.uri?.toString()?.let { uri ->
        Song(
            videoId = mediaId,
            title = mediaMetadata.title?.toString().orEmpty().ifBlank { mediaId },
            artist = mediaMetadata.artist?.toString().orEmpty().ifBlank { "Unknown Artist" },
            thumbnailUrl = mediaMetadata.artworkUri?.toString(),
            albumName = mediaMetadata.albumTitle?.toString(),
            localUri = uri,
        )
    }
