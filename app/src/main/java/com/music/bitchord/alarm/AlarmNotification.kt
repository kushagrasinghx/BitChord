package com.music.bitchord.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.music.bitchord.R

object AlarmNotification {
    const val ID = 2201
    private const val CHANNEL = "music_alarm_ringing_v2"
    private const val SNOOZE_ID = 2202
    private const val SNOOZE_CHANNEL = "music_alarm_snooze_pending_v1"

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

    fun showSnoozePending(context: Context, alarm: AlarmConfig, epochMillis: Long, token: String) {
        runCatching {
            ensureSnoozeChannel(context)
            val wakeTime = formatSnoozeWakeTime(epochMillis, DateFormat.getTimeFormat(context))
            NotificationManagerCompat.from(context).notify(
                snoozeNotificationTag(alarm.id),
                SNOOZE_ID,
                NotificationCompat.Builder(context, SNOOZE_CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification_logo)
                    .setContentTitle(alarm.label.ifBlank { context.getString(R.string.alarm_notification_title) })
                    .setContentText(context.getString(R.string.alarm_snoozed_until, wakeTime))
                    .setSubText(alarm.song?.title)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setSilent(true)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setContentIntent(AlarmDeepLink.contentIntent(context, alarm.id))
                    .addAction(
                        0,
                        context.getString(R.string.alarm_cancel_snooze),
                        AlarmReceiver.cancelSnoozePendingIntent(context, alarm.id, token),
                    )
                    .build(),
            )
        }
    }

    fun cancelSnooze(context: Context, alarmId: String) =
        runCatching {
            NotificationManagerCompat.from(context).cancel(snoozeNotificationTag(alarmId), SNOOZE_ID)
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

    private fun ensureSnoozeChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                SNOOZE_CHANNEL,
                context.getString(R.string.alarm_snooze_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.alarm_snooze_channel_description)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
    }
}

internal fun snoozeNotificationTag(alarmId: String) = "alarm-snooze:$alarmId"

internal fun formatSnoozeWakeTime(epochMillis: Long, formatter: java.text.DateFormat): String =
    formatter.format(java.util.Date(epochMillis))
