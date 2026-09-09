package com.music.bitchord.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.music.bitchord.OfflineMainActivity
import com.music.bitchord.R
import com.music.bitchord.data.model.Song

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
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
        session = MediaSession.Builder(this, player)
            .setId(SESSION_ID)
            .setSessionActivity(sessionActivity())
            .build()
        restoreQueue()
    }

    override fun onDestroy() {
        persistQueue()
        session?.release()
        session = null
        player.release()
        super.onDestroy()
    }

    private fun restoreQueue() {
        val snapshot = LastPlayed.load() ?: return
        val items = snapshot.songs.mapNotNull { it.toLocalMediaItem() }
        if (items.isEmpty()) return
        val index = snapshot.index.coerceIn(items.indices)
        player.setMediaItems(items, index, snapshot.positionMs)
        player.prepare()
    }

    private fun persistQueue() {
        if (!::player.isInitialized || player.mediaItemCount == 0) return
        val songs = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).toSongOrNull() }
        if (songs.isEmpty()) return
        LastPlayed.saveQueue(songs, player.currentMediaItemIndex)
        LastPlayed.savePlaybackState(player.currentMediaItemIndex, player.currentPosition)
    }

    private fun sessionActivity(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, OfflineMainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
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

private fun MediaItem.toSongOrNull(): Song? = localConfiguration?.uri?.toString()?.let { uri ->
    Song(
        videoId = mediaId,
        title = mediaMetadata.title?.toString().orEmpty().ifBlank { mediaId },
        artist = mediaMetadata.artist?.toString().orEmpty().ifBlank { "Unknown Artist" },
        thumbnailUrl = mediaMetadata.artworkUri?.toString(),
        albumName = mediaMetadata.albumTitle?.toString(),
        localUri = uri,
    )
}
