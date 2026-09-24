package com.music.bitchord.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ID) ?: return
        val token = intent.getStringExtra(TOKEN) ?: return
        when (intent.action) {
            TRIGGER, SNOOZE_TRIGGER -> {
                val epoch = intent.getLongExtra(EPOCH, Long.MIN_VALUE)
                if (epoch == Long.MIN_VALUE) return
                val request = AlarmScheduler.consumeTrigger(
                    context,
                    id,
                    token,
                    epoch,
                    intent.action == SNOOZE_TRIGGER,
                ) ?: return
                AlarmRingingService.start(context, request)
            }
            STOP -> {
                val end = AlarmScheduler.stop(context, id, token) ?: return
                AlarmRingingService.stop(context, end.previousAlarmVolume)
            }
            SNOOZE -> {
                val end = AlarmScheduler.snooze(context, id, token) ?: return
                AlarmRingingService.stop(context, end.previousAlarmVolume)
            }
            CANCEL_SNOOZE -> AlarmScheduler.cancelSnooze(context, id, token)
        }
    }

    companion object {
        private const val TRIGGER = "com.music.bitchord.alarm.TRIGGER"
        private const val SNOOZE_TRIGGER = "com.music.bitchord.alarm.SNOOZE_TRIGGER"
        private const val STOP = "com.music.bitchord.alarm.STOP"
        private const val SNOOZE = "com.music.bitchord.alarm.SNOOZE"
        private const val CANCEL_SNOOZE = "com.music.bitchord.alarm.CANCEL_SNOOZE"
        private const val ID = "alarm_id"
        private const val TOKEN = "alarm_token"
        private const val EPOCH = "alarm_epoch"

        fun triggerPendingIntent(
            context: Context,
            id: String,
            token: String,
            epoch: Long,
            snooze: Boolean,
        ) = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, AlarmReceiver::class.java)
                .setAction(if (snooze) SNOOZE_TRIGGER else TRIGGER)
                .setData(Uri.parse(alarmPendingIdentity(id, if (snooze) "snooze-trigger" else "trigger")))
                .putExtra(ID, id)
                .putExtra(TOKEN, token)
                .putExtra(EPOCH, epoch),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        fun stopPendingIntent(context: Context, id: String, token: String) =
            action(context, id, token, STOP, "stop")

        fun snoozePendingIntent(context: Context, id: String, token: String) =
            action(context, id, token, SNOOZE, "snooze")

        fun cancelSnoozePendingIntent(context: Context, id: String, token: String) =
            action(context, id, token, CANCEL_SNOOZE, "cancel-snooze")

        private fun action(
            context: Context,
            id: String,
            token: String,
            action: String,
            kind: String,
        ) = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, AlarmReceiver::class.java)
                .setAction(action)
                .setData(Uri.parse(alarmPendingIdentity(id, "$kind/$token")))
                .putExtra(ID, id)
                .putExtra(TOKEN, token),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
