package com.music.bitchord.wear.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.music.bitchord.wear.playback.Speaker
import com.music.bitchord.wear.R
import com.music.bitchord.wear.WearActivity
import com.music.bitchord.wear.WearProtocol as P

/**
 * Puts BitChord's own icon on the watch face while the phone plays.
 *
 * The icon Wear OS shows by itself for phone playback belongs to the system's
 * media controller and opens that, whatever app is playing. An Ongoing Activity
 * of our own is the one entry point the watch face offers an app, so this posts
 * one — tapping it opens this app's player — and takes it down again on pause.
 *
 * Woken only for [P.PATH_NOW_PLAYING], which the phone writes on track, play
 * and pause changes, not on a timer.
 */
class NowPlayingListener : WearableListenerService() {

    /** The phone driving this watch as its speaker — see [Speaker]. */
    override fun onMessageReceived(event: MessageEvent) {
        val path = event.path ?: return
        when {
            path == P.PATH_WATCH_INFO_REQUEST -> SelfUpdate.publishInfo(this)
            path.startsWith(P.PATH_SPEAKER + "/") -> Speaker.handle(this, path.substringAfterLast('/'), event.data)
        }
    }

    /** A newer build of this app, sent by the phone's "BitChord for Wear OS" settings. */
    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path == P.PATH_APK) SelfUpdate.receive(this, channel)
    }

    /** Play services has finished writing what [SelfUpdate.receive] asked it for. */
    override fun onInputClosed(channel: ChannelClient.Channel, closeReason: Int, appSpecificErrorCode: Int) {
        if (channel.path == P.PATH_APK) SelfUpdate.onReceived(this, channel, closeReason)
    }

    override fun onDataChanged(events: DataEventBuffer) {
        val latest = events.lastOrNull {
            it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == P.PATH_NOW_PLAYING
        } ?: return
        val map = DataMapItem.fromDataItem(latest.dataItem).dataMap
        // With this watch as the speaker its own media session carries the
        // ongoing icon, and it opens this app already.
        val playing = map.getBoolean(P.KEY_HAS_TRACK) && map.getBoolean(P.KEY_PLAYING) &&
            !map.getBoolean(P.KEY_WATCH_SPEAKER)
        if (playing) {
            show(this, map.getString(P.KEY_TITLE).orEmpty(), map.getString(P.KEY_ARTIST).orEmpty())
        } else {
            hide(this)
        }
    }

    companion object {
        private const val CHANNEL = "now_playing"
        private const val NOTIFICATION_ID = 1

        fun show(context: Context, title: String, artist: String) {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.playing_now), NotificationManager.IMPORTANCE_LOW),
            )
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, WearActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val builder = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(artist)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setContentIntent(open)
                .setOngoing(true)
                .setSilent(true)
            OngoingActivity.Builder(context, NOTIFICATION_ID, builder)
                .setStaticIcon(R.drawable.ic_notification)
                .setTouchIntent(open)
                .setTitle(title)
                .setStatus(Status.Builder().addTemplate(if (artist.isBlank()) title else "$title · $artist").build())
                .build()
                .apply(context)
            runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
        }

        fun hide(context: Context) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }
    }
}
