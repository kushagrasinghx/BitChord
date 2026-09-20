package com.music.bitchord.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.music.bitchord.MainActivity
import com.music.bitchord.R

/** Small, silent companion notification whose only extra action is Stop. */
object AlarmNotification {

    fun showActive(context: Context, request: AlarmScheduler.TriggerRequest) {
        createChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(context.getString(R.string.alarm_notification_title))
            .setContentText(context.getString(R.string.alarm_notification_text, request.playlistTitle))
            .setContentIntent(contentIntent(context))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setTimeoutAfter(AlarmStateMachine.ACTIVE_WINDOW_MILLIS)
            .addAction(
                0,
                context.getString(R.string.alarm_stop),
                AlarmReceiver.stopPendingIntent(context, request.token),
            )
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    fun showFailure(context: Context) {
        createChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(context.getString(R.string.alarm_failed_title))
            .setContentText(context.getString(R.string.alarm_failed_text))
            .setContentIntent(contentIntent(context))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setSilent(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.alarm_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.alarm_channel_description)
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun contentIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_CONTENT,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private const val CHANNEL_ID = "music_alarm"
    private const val NOTIFICATION_ID = 2201
    private const val REQUEST_CONTENT = 4103
}
