package com.music.bitchord.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.music.bitchord.R

object AlarmNotification {
    const val ID = 2201
    private const val CHANNEL = "music_alarm_ringing_v2"

    fun active(context: Context, alarm: AlarmConfig, token: String): Notification {
        ensureChannel(context)
        val fullScreen = AlarmRingingActivity.intent(context, alarm.id, token)
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(alarm.label.ifBlank { context.getString(R.string.alarm_notification_title) })
            .setContentText(
                context.getString(R.string.alarm_notification_text, requireNotNull(alarm.song).title),
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .addAction(
                0,
                context.getString(R.string.alarm_snooze),
                AlarmReceiver.snoozePendingIntent(context, alarm.id, token),
            )
            .addAction(
                0,
                context.getString(R.string.alarm_stop),
                AlarmReceiver.stopPendingIntent(context, alarm.id, token),
            )
            .build()
    }

    fun canUseFullScreen(context: Context) =
        Build.VERSION.SDK_INT < 34 ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun showFailure(context: Context) {
        ensureChannel(context)
        NotificationManagerCompat.from(context).notify(
            ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle(context.getString(R.string.alarm_failed_title))
                .setContentText(context.getString(R.string.alarm_failed_text))
                .setAutoCancel(true)
                .build(),
        )
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(ID)

    private fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.alarm_channel_description)
                setSound(null, null)
                enableVibration(false)
            },
        )
    }
}
