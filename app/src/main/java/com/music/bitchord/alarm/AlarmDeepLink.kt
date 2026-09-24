package com.music.bitchord.alarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.music.bitchord.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Relays a snooze-notification tap to the alarm list in the single-task main activity. */
object AlarmDeepLink {
    const val EXTRA_ALARM_ID = "bitchord.openAlarmId"

    private val request = MutableStateFlow<String?>(null)
    val pending = request.asStateFlow()

    fun consume(intent: Intent?): Boolean {
        val alarmId = intent?.getStringExtra(EXTRA_ALARM_ID)?.takeIf(String::isNotBlank) ?: return false
        intent.removeExtra(EXTRA_ALARM_ID)
        request.value = alarmId
        return true
    }

    fun handled() {
        request.value = null
    }

    fun contentIntent(context: Context, alarmId: String): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setData(Uri.parse(alarmPendingIdentity(alarmId, "open")))
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
