package com.music.bitchord.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.music.bitchord.MainActivity
import java.time.Clock
import java.time.ZoneId

/** Thin Android AlarmManager adapter around the pure alarm state transitions. */
object AlarmScheduler {

    data class TriggerRequest(
        val playlistId: String,
        val playlistTitle: String,
        val token: String,
    )

    @Synchronized
    fun updateConfiguration(
        context: Context,
        edit: (AlarmConfig) -> AlarmConfig,
    ): AlarmConfig {
        val app = context.applicationContext
        cancelPlatformAlarm(app)
        val edited = AlarmStateMachine.edit(AlarmStore.current(app), edit(AlarmStore.current(app)))
        AlarmStore.save(app, edited)
        return if (edited.isReadyToSchedule()) {
            schedulePersisted(app, edited)
        } else {
            edited
        }
    }

    @Synchronized
    fun reschedule(context: Context): AlarmConfig {
        val app = context.applicationContext
        cancelPlatformAlarm(app)
        val invalidated = AlarmStateMachine.invalidateSchedule(AlarmStore.current(app))
        AlarmStore.save(app, invalidated)
        return if (invalidated.isReadyToSchedule()) {
            schedulePersisted(app, invalidated)
        } else {
            invalidated
        }
    }

    /** Reconciles permission changes or a missed/past persisted occurrence on app resume. */
    @Synchronized
    fun reconcile(context: Context): AlarmConfig {
        val app = context.applicationContext
        val current = AlarmStore.current(app)
        if (!current.isReadyToSchedule()) return current
        val desiredMode = mode(app)
        val occurrenceMissingOrPast =
            current.scheduledEpochMillis == null || current.scheduledEpochMillis <= Clock.systemUTC().millis()
        return if (occurrenceMissingOrPast || current.scheduleMode != desiredMode) {
            reschedule(app)
        } else {
            current
        }
    }

    @Synchronized
    fun consumeTrigger(
        context: Context,
        token: String,
        epochMillis: Long,
    ): TriggerRequest? {
        val app = context.applicationContext
        val transition = AlarmStateMachine.consumeTrigger(
            config = AlarmStore.current(app),
            token = token,
            epochMillis = epochMillis,
            nowEpochMillis = Clock.systemUTC().millis(),
        ) ?: return null

        AlarmStore.save(app, transition.config)
        if (transition.recurring) {
            val next = AlarmStateMachine.invalidateSchedule(transition.config)
            AlarmStore.save(app, next)
            schedulePersisted(app, next)
        }
        return TriggerRequest(transition.playlistId, transition.playlistTitle, transition.token)
    }

    @Synchronized
    fun consumeStop(context: Context, token: String): Boolean {
        val app = context.applicationContext
        val current = AlarmStore.current(app)
        if (!AlarmStateMachine.canStop(current, token, Clock.systemUTC().millis())) return false
        AlarmStore.save(app, AlarmStateMachine.stopped(current, token))
        return true
    }

    @Synchronized
    fun recordPlaybackFailure(context: Context, token: String) {
        val app = context.applicationContext
        val current = AlarmStore.current(app)
        AlarmStore.save(
            app,
            AlarmStateMachine.playbackFailed(current, token, Clock.systemUTC().millis()),
        )
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return alarmManager(context).canScheduleExactAlarms()
    }

    fun mode(context: Context): AlarmScheduleMode =
        chooseAlarmScheduleMode(canScheduleExact(context))

    private fun schedulePersisted(context: Context, config: AlarmConfig): AlarmConfig {
        val next = AlarmScheduleCalculator.nextOccurrence(
            config = config,
            now = Clock.systemUTC().instant(),
            zoneId = ZoneId.systemDefault(),
        ) ?: return AlarmStateMachine.schedulingFailed(config, Clock.systemUTC().millis()).also {
            AlarmStore.save(context, it)
        }

        val requestedMode = mode(context)
        var scheduled = AlarmStateMachine.scheduled(config, next.toEpochMilli(), requestedMode)
        AlarmStore.save(context, scheduled)

        return try {
            setPlatformAlarm(context, scheduled, requestedMode)
            scheduled
        } catch (denied: SecurityException) {
            if (requestedMode == AlarmScheduleMode.EXACT) {
                scheduled = scheduled.copy(scheduleMode = AlarmScheduleMode.INEXACT)
                AlarmStore.save(context, scheduled)
                runCatching { setPlatformAlarm(context, scheduled, AlarmScheduleMode.INEXACT) }
                    .fold(
                        onSuccess = { scheduled },
                        onFailure = { schedulingFailure(context, scheduled, it) },
                    )
            } else {
                schedulingFailure(context, scheduled, denied)
            }
        } catch (failure: RuntimeException) {
            schedulingFailure(context, scheduled, failure)
        }
    }

    private fun setPlatformAlarm(
        context: Context,
        config: AlarmConfig,
        mode: AlarmScheduleMode,
    ) {
        val epochMillis = requireNotNull(config.scheduledEpochMillis)
        val token = requireNotNull(config.scheduledToken)
        val trigger = AlarmReceiver.triggerPendingIntent(context, token, epochMillis)
        val manager = alarmManager(context)
        if (mode == AlarmScheduleMode.EXACT) {
            manager.setAlarmClock(
                AlarmManager.AlarmClockInfo(epochMillis, alarmScreenPendingIntent(context)),
                trigger,
            )
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMillis, trigger)
        }
    }

    private fun schedulingFailure(
        context: Context,
        config: AlarmConfig,
        failure: Throwable,
    ): AlarmConfig {
        Log.w(TAG, "Unable to schedule music alarm", failure)
        val failed = AlarmStateMachine.schedulingFailed(config, Clock.systemUTC().millis())
        AlarmStore.save(context, failed)
        return failed
    }

    private fun cancelPlatformAlarm(context: Context) {
        alarmManager(context).cancel(AlarmReceiver.triggerPendingIntent(context, "", 0L))
    }

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    private fun alarmScreenPendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_SHOW_ALARM,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private const val REQUEST_SHOW_ALARM = 4102
    private const val TAG = "BitChordAlarm"
}

internal fun chooseAlarmScheduleMode(canScheduleExact: Boolean): AlarmScheduleMode =
    if (canScheduleExact) AlarmScheduleMode.EXACT else AlarmScheduleMode.INEXACT
