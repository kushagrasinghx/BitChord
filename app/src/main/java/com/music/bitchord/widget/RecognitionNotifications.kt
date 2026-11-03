package com.music.bitchord.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.music.bitchord.R
import com.music.bitchord.data.model.NOTIFICATION_ART_PX
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.shazam.ShazamTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Notifications for matches found from the home-screen widget or QS tile. */
object RecognitionNotifications {

    private val artworkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var artworkJob: Job? = null
    @Volatile
    private var notificationGeneration = 0L

    fun cancel(context: Context) {
        notificationGeneration++
        artworkJob?.cancel()
        artworkJob = null
        runCatching {
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }
    }

    /** Posts the text immediately, then refreshes it with cover art if it loads. */
    fun post(context: Context, track: ShazamTrack) {
        val appContext = context.applicationContext
        val generation = ++notificationGeneration
        artworkJob?.cancel()
        publish(appContext, track, artwork = null)
        artworkJob = artworkScope.launch {
            loadArtwork(appContext, track.artworkUrl)?.let { artwork ->
                if (generation == notificationGeneration) {
                    publish(appContext, track, artwork)
                }
            }
        }
    }

    private fun publish(context: Context, track: ShazamTrack, artwork: Bitmap?) {
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.recognition_notification_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.recognition_notification_channel_description)
                },
            )
            manager.notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_logo)
                    .setContentTitle(track.title)
                    .setContentText(track.artist)
                    .setContentIntent(contentIntent(context, track.searchQuery))
                    .setCustomContentView(compactContentView(context, track, artwork))
                    .setCustomBigContentView(contentView(context, track, artwork))
                    .setCustomHeadsUpContentView(compactContentView(context, track, artwork))
                    .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_EVENT)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .build(),
            )
        }
    }

    private fun contentView(context: Context, track: ShazamTrack, artwork: Bitmap?): RemoteViews =
        RemoteViews(context.packageName, R.layout.notification_recognition).apply {
            setOnClickPendingIntent(
                R.id.notification_recognition_root,
                contentIntent(context, track.searchQuery),
            )
            setTextViewText(R.id.notification_recognition_title, track.title)
            setTextViewText(R.id.notification_recognition_artist, track.artist)
            if (artwork != null) {
                setImageViewBitmap(R.id.notification_recognition_art, artwork)
            } else {
                setImageViewResource(R.id.notification_recognition_art, R.drawable.ic_notification_logo)
            }
            setOnClickPendingIntent(
                R.id.notification_recognition_play,
                actionIntent(context, RecognitionNotificationActions.ACTION_PLAY_NOW, track.searchQuery, PLAY_REQUEST_CODE),
            )
            setOnClickPendingIntent(
                R.id.notification_recognition_playlist,
                actionIntent(
                    context,
                    RecognitionNotificationActions.ACTION_ADD_TO_PLAYLIST,
                    track.searchQuery,
                    PLAYLIST_REQUEST_CODE,
                ),
            )
        }

    private fun compactContentView(context: Context, track: ShazamTrack, artwork: Bitmap?): RemoteViews =
        RemoteViews(context.packageName, R.layout.notification_recognition_heads_up).apply {
            setOnClickPendingIntent(
                R.id.notification_recognition_root,
                contentIntent(context, track.searchQuery),
            )
            setTextViewText(R.id.notification_recognition_title, track.title)
            setTextViewText(R.id.notification_recognition_artist, track.artist)
            if (artwork != null) {
                setImageViewBitmap(R.id.notification_recognition_art, artwork)
            } else {
                setImageViewResource(R.id.notification_recognition_art, R.drawable.ic_notification_logo)
            }
            setOnClickPendingIntent(
                R.id.notification_recognition_play,
                actionIntent(context, RecognitionNotificationActions.ACTION_PLAY_NOW, track.searchQuery, PLAY_REQUEST_CODE),
            )
            setOnClickPendingIntent(
                R.id.notification_recognition_playlist,
                actionIntent(
                    context,
                    RecognitionNotificationActions.ACTION_ADD_TO_PLAYLIST,
                    track.searchQuery,
                    PLAYLIST_REQUEST_CODE,
                ),
            )
        }

    private fun actionIntent(context: Context, action: String, query: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            RecognitionNotificationActions.intent(context, action, query),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun contentIntent(context: Context, query: String): PendingIntent = PendingIntent.getActivity(
        context,
        NOTIFICATION_REQUEST_CODE,
        RecognitionNotificationActions.intent(
            context,
            RecognitionNotificationActions.ACTION_OPEN_SEARCH,
            query,
        ),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private suspend fun loadArtwork(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        val request = ImageRequest.Builder(context)
            .data(url.artworkAt(NOTIFICATION_ART_PX) ?: url)
            .size(NOTIFICATION_ART_PX)
            .allowHardware(false)
            .build()
        val result = runCatching { SingletonImageLoader.get(context).execute(request) }.getOrNull()
        return (result as? SuccessResult)?.image?.toBitmap()
    }

    private const val CHANNEL_ID = "recognition_matches"
    private const val NOTIFICATION_ID = 0x8177
    private const val NOTIFICATION_REQUEST_CODE = 0x8177
    private const val PLAY_REQUEST_CODE = 0x8178
    private const val PLAYLIST_REQUEST_CODE = 0x8179
}
