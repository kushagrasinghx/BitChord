package com.music.bitchord.data.webdav

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.music.bitchord.R
import com.music.bitchord.data.model.Song

/**
 * The shade presence for uploads, shaped like the download one: an ongoing
 * progress row while bytes are moving, replaced by the batch summary when it
 * lands.
 *
 * A plain notification rather than a foreground service — uploads run in the
 * UI scope and die with it, so there is no background work to keep alive.
 * Every post is caught: without the notification permission the shade simply
 * never shows, and that must not fail the batch.
 */
object WebDavUploadNotifications {

    fun postProgress(
        context: Context,
        current: Song?,
        runningCount: Int,
        queuedCount: Int,
        fraction: Float?,
    ) {
        val percent = fraction?.times(100)?.toInt() ?: 0
        val title = when {
            runningCount > 1 -> context.resources.getQuantityString(
                R.plurals.uploading_song_count, runningCount, runningCount,
            )
            else -> current?.title ?: context.getString(R.string.upload_notification_title)
        }
        val text = when {
            runningCount > 1 && queuedCount > 0 -> context.getString(
                R.string.upload_notification_queued, current?.title.orEmpty(), queuedCount,
            )
            runningCount > 1 -> current?.title.orEmpty()
            current == null -> context.getString(R.string.download_notification_starting)
            queuedCount > 0 -> context.getString(
                R.string.upload_notification_queued, current.artist, queuedCount,
            )
            else -> current.artist
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(title)
            .setContentText(text)
            // Indeterminate until something has a length to measure against.
            .setProgress(100, percent, fraction == null)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        post(context, notification)
    }

    fun postDone(context: Context, summary: WebDavUploads.Summary) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(context.getString(R.string.upload_channel_name))
            .setContentText(
                context.getString(
                    R.string.webdav_upload_summary,
                    summary.uploaded,
                    summary.skipped,
                    summary.failed,
                ),
            )
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()
        post(context, notification)
    }

    fun cancel(context: Context) {
        runCatching {
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }
    }

    private fun post(context: Context, notification: android.app.Notification) {
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.upload_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.upload_channel_description)
                    setShowBadge(false)
                },
            )
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private const val CHANNEL_ID = "webdav_uploads"

    /** Distinct from downloads', which [DownloadService][com.music.bitchord.download.DownloadService] owns. */
    private const val NOTIFICATION_ID = 0x8176
}
