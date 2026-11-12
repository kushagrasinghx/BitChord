package com.music.bitchord.wear.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.music.bitchord.wear.WearActivity

/**
 * The watch as the phone's speaker: the player [Speaker] drives.
 *
 * A plain ExoPlayer whose every byte comes from the phone — see
 * [PhoneStreamDataSource] — inside a media session, so it keeps playing with
 * the screen off and a Bluetooth headset's buttons reach it.
 *
 * Buffers deeper than a phone player would: the link to the phone is the slow
 * part, and a minute in hand rides out a Bluetooth hiccup or a phone that is
 * slow to resolve the next track.
 */
@UnstableApi
class WatchPlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(PhoneStreamDataSource.Factory(this)))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    // Start as soon as ExoPlayer normally would; hold more than
                    // usual once going.
                    .setBufferDurationsMs(30_000, 90_000, 1_000, 2_000)
                    .build(),
            )
            .build()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, WearActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .build()
        Speaker.attach(this, player)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            (player as? ExoPlayer)?.let(Speaker::detach)
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
