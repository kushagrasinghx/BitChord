package com.music.bitchord.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Receives only this app's explicit trigger and Stop PendingIntents. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TRIGGER -> trigger(context, intent)
            ACTION_STOP -> stop(context, intent)
        }
    }

    private fun trigger(context: Context, intent: Intent) {
        val token = intent.getStringExtra(EXTRA_TOKEN) ?: return
        val epochMillis = intent.getLongExtra(EXTRA_EPOCH, Long.MIN_VALUE)
        if (epochMillis == Long.MIN_VALUE) return
        val request = AlarmScheduler.consumeTrigger(context, token, epochMillis) ?: return

        AlarmNotification.showActive(context, request)
        val pending = goAsync()
        AlarmPlaybackCoordinator.play(
            context = context,
            playlistId = request.playlistId,
            playlistTitle = request.playlistTitle,
        ) { started ->
            if (!started) {
                Log.w(TAG, "Music alarm playlist could not start")
                AlarmScheduler.recordPlaybackFailure(context, request.token)
                AlarmNotification.showFailure(context)
            }
            runCatching { pending.finish() }
        }
    }

    private fun stop(context: Context, intent: Intent) {
        val token = intent.getStringExtra(EXTRA_TOKEN) ?: return
        if (!AlarmScheduler.consumeStop(context, token)) return

        AlarmNotification.cancel(context)
        val pending = goAsync()
        AlarmPlaybackCoordinator.stop(context) { stopped ->
            if (!stopped) Log.w(TAG, "Unable to connect to playback session for alarm Stop")
            runCatching { pending.finish() }
        }
    }

    companion object {
        private const val ACTION_TRIGGER = "com.music.bitchord.alarm.TRIGGER"
        private const val ACTION_STOP = "com.music.bitchord.alarm.STOP"
        private const val EXTRA_TOKEN = "alarm_token"
        private const val EXTRA_EPOCH = "alarm_epoch"

        fun triggerPendingIntent(
            context: Context,
            token: String,
            epochMillis: Long,
        ): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_TRIGGER,
            Intent(context, AlarmReceiver::class.java)
                .setAction(ACTION_TRIGGER)
                .putExtra(EXTRA_TOKEN, token)
                .putExtra(EXTRA_EPOCH, epochMillis),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        fun stopPendingIntent(context: Context, token: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_STOP,
            Intent(context, AlarmReceiver::class.java)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_TOKEN, token),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private const val REQUEST_TRIGGER = 4100
        private const val REQUEST_STOP = 4101
        private const val TAG = "BitChordAlarm"
    }
}
