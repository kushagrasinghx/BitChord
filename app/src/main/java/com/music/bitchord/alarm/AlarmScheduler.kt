package com.music.bitchord.alarm

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import java.time.Clock
import java.time.ZoneId
import java.util.UUID

object AlarmScheduler {
    data class TriggerRequest(val alarm: AlarmConfig, val token: String, val replaced: AlarmSession?)
    data class EndRequest(val previousAlarmVolume: Int?)
    data class SnoozeRequest(val previousAlarmVolume: Int?, val epochMillis: Long)

    @Synchronized
    fun create(context: Context): AlarmConfig {
        val app = context.applicationContext
        val state = AlarmStore.current(app)
        val now = Clock.systemUTC().millis()
        val entry = AlarmStateMachine.newAlarm(
            UUID.randomUUID().toString(),
            (state.alarms.maxOfOrNull { it.creationOrder } ?: now) + 1,
        )
        AlarmStore.save(app, state.copy(alarms = state.alarms + entry))
        return entry
    }

    @Synchronized
    fun update(context: Context, id: String, edit: (AlarmConfig) -> AlarmConfig): AlarmConfig? {
        val app = context.applicationContext
        val state = AlarmStore.current(app)
        val old = state.alarms.firstOrNull { it.id == id } ?: return null
        cancel(app, id, snooze = false)
        cancel(app, id, snooze = true)
        AlarmNotification.cancelSnooze(app, id)
        val changed = AlarmStateMachine.edit(old, edit(old))
        var next = state.copy(alarms = state.alarms.map { if (it.id == id) changed else it })
        AlarmStore.save(app, next)
        if (!changed.isReadyToSchedule()) return changed

        val scheduled = schedule(app, changed)
        next = next.copy(alarms = next.alarms.map { if (it.id == id) scheduled else it })
        AlarmStore.save(app, next)
        return scheduled
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean) =
        update(context, id) { it.copy(enabled = enabled) }

    /** Restores a temporary alarm-stream override left behind by abrupt process death. */
    @Synchronized
    fun recoverInterruptedSession(context: Context) {
        val app = context.applicationContext
        val state = AlarmStore.current(app)
        val interrupted = state.activeSession ?: return
        AlarmVolumeController.restore(app, interrupted.previousAlarmVolume)
        AlarmNotification.cancel(app)
        AlarmStore.save(app, state.copy(activeSession = null))
    }

    /** Recreates quiet evidence for every still-valid snooze after process recreation. */
    fun restorePendingSnoozeNotifications(context: Context) {
        val app = context.applicationContext
        val now = Clock.systemUTC().millis()
        AlarmStore.current(app).alarms.forEach { alarm ->
            val epoch = alarm.snoozeEpochMillis
            val token = alarm.snoozeToken
            if (epoch != null && token != null && epoch > now) {
                AlarmNotification.showSnoozePending(app, alarm, epoch, token)
            }
        }
    }

    @Synchronized
    fun delete(context: Context, id: String) {
        val app = context.applicationContext
        cancel(app, id, snooze = false)
        cancel(app, id, snooze = true)
        AlarmNotification.cancelSnooze(app, id)
        val state = AlarmStore.current(app)
        val active = state.activeSession?.takeIf { it.alarmId == id }
        AlarmStore.save(app, AlarmStateMachine.remove(state, id))
        active?.let {
            AlarmRingingService.stop(app, it.previousAlarmVolume)
        }
    }

    @Synchronized
    fun rescheduleAll(context: Context, clearActiveSession: Boolean = false) {
        val app = context.applicationContext
        var state = AlarmStore.current(app)
        if (clearActiveSession) {
            AlarmVolumeController.restore(app, state.activeSession?.previousAlarmVolume)
            state = state.copy(activeSession = null)
        }
        state.alarms.forEach {
            cancel(app, it.id, snooze = false)
            cancel(app, it.id, snooze = true)
            AlarmNotification.cancelSnooze(app, it.id)
        }
        val rescheduled = state.alarms.map { entry ->
            var updated = AlarmStateMachine.invalidate(entry)
            if (updated.isReadyToSchedule()) updated = schedule(app, updated)
            updated.snoozeEpochMillis?.let { epoch ->
                updated.snoozeToken?.let { token ->
                    set(app, updated.id, token, epoch, snooze = true)
                    AlarmNotification.showSnoozePending(app, updated, epoch, token)
                }
            }
            updated
        }
        AlarmStore.save(app, state.copy(alarms = rescheduled))
    }

    @Synchronized
    fun consumeTrigger(
        context: Context,
        id: String,
        token: String,
        epoch: Long,
        snooze: Boolean,
    ): TriggerRequest? {
        val app = context.applicationContext
        val transition = AlarmStateMachine.trigger(
            AlarmStore.current(app),
            id,
            token,
            epoch,
            Clock.systemUTC().millis(),
            snooze,
        ) ?: return null
        var state = transition.collection
        if (!snooze && transition.alarm.repeatDays.isNotEmpty()) {
            val entry = schedule(app, AlarmStateMachine.invalidate(transition.alarm))
            state = state.copy(alarms = state.alarms.map { if (it.id == id) entry else it })
        }
        AlarmStore.save(app, state)
        if (snooze) AlarmNotification.cancelSnooze(app, id)
        return TriggerRequest(transition.alarm, transition.token, transition.replaced)
    }

    @Synchronized
    fun captureVolume(context: Context, id: String, token: String, volume: Int) {
        val app = context.applicationContext
        AlarmStore.save(
            app,
            AlarmStateMachine.captureVolume(AlarmStore.current(app), id, token, volume),
        )
    }

    @Synchronized
    fun stop(context: Context, id: String, token: String): EndRequest? {
        val app = context.applicationContext
        val transition = AlarmStateMachine.end(AlarmStore.current(app), id, token) ?: return null
        AlarmStore.save(app, transition.collection)
        return EndRequest(transition.previousAlarmVolume)
    }

    @Synchronized
    fun snooze(context: Context, id: String, token: String): SnoozeRequest? {
        val app = context.applicationContext
        val transition = AlarmStateMachine.snooze(
            AlarmStore.current(app),
            id,
            token,
            Clock.systemUTC().millis(),
        ) ?: return null
        AlarmStore.save(app, transition.collection)
        set(app, id, transition.token, transition.epochMillis, snooze = true)
        transition.collection.alarms.firstOrNull { it.id == id }?.let { alarm ->
            AlarmNotification.showSnoozePending(app, alarm, transition.epochMillis, transition.token)
        }
        return SnoozeRequest(transition.previousAlarmVolume, transition.epochMillis)
    }

    @Synchronized
    fun cancelSnooze(context: Context, id: String, token: String): Boolean {
        val app = context.applicationContext
        val updated = AlarmStateMachine.cancelSnooze(AlarmStore.current(app), id, token) ?: return false
        cancel(app, id, snooze = true)
        AlarmStore.save(app, updated)
        AlarmNotification.cancelSnooze(app, id)
        return true
    }

    fun canScheduleExact(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager(context).canScheduleExactAlarms()

    fun mode(context: Context) =
        if (canScheduleExact(context)) AlarmScheduleMode.EXACT else AlarmScheduleMode.INEXACT

    private fun schedule(context: Context, entry: AlarmConfig): AlarmConfig {
        val epoch = AlarmScheduleCalculator.nextOccurrence(
            entry,
            Clock.systemUTC().instant(),
            ZoneId.systemDefault(),
        )?.toEpochMilli() ?: return entry.copy(lastFailure = AlarmFailure.SCHEDULING_UNAVAILABLE)
        var scheduled = AlarmStateMachine.scheduled(entry, epoch, mode(context))
        runCatching {
            set(context, scheduled.id, requireNotNull(scheduled.scheduledToken), epoch, snooze = false)
        }.onFailure {
            scheduled = scheduled.copy(lastFailure = AlarmFailure.SCHEDULING_UNAVAILABLE)
        }
        return scheduled
    }

    private fun set(context: Context, id: String, token: String, epoch: Long, snooze: Boolean) {
        val pendingIntent = AlarmReceiver.triggerPendingIntent(context, id, token, epoch, snooze)
        if (canScheduleExact(context)) {
            manager(context).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epoch, pendingIntent)
        } else {
            manager(context).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epoch, pendingIntent)
        }
    }

    private fun cancel(context: Context, id: String, snooze: Boolean) =
        manager(context).cancel(AlarmReceiver.triggerPendingIntent(context, id, "", 0, snooze))

    private fun manager(context: Context) = context.getSystemService(AlarmManager::class.java)
}

internal fun alarmPendingIdentity(alarmId: String, kind: String) = "bitchord://alarm/$alarmId/$kind"
