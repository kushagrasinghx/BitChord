package com.music.bitchord.alarm

object AlarmStateMachine {
    const val ACTIVE_WINDOW_MILLIS = 4L * 60 * 60 * 1000
    data class TriggerTransition(val collection: AlarmCollection, val alarm: AlarmConfig, val token: String, val replaced: AlarmSession?)
    data class EndTransition(val collection: AlarmCollection, val previousAlarmVolume: Int?)
    data class SnoozeTransition(val collection: AlarmCollection, val previousAlarmVolume: Int?, val epochMillis: Long, val token: String)

    fun newAlarm(id: String, order: Long) = AlarmConfig(id = id, creationOrder = order)
    fun edit(entry: AlarmConfig, proposed: AlarmConfig) = proposed.copy(
        id = entry.id, creationOrder = entry.creationOrder,
        enabled = proposed.enabled && proposed.song?.isValid() == true,
        generation = next(entry.generation), scheduledEpochMillis = null, scheduledToken = null, scheduleMode = null,
        snoozeEpochMillis = null, snoozeToken = null,
        lastFailure = null, lastFailureEpochMillis = null,
    ).takeIf(AlarmConfig::isStructurallyValid) ?: entry
    fun invalidate(entry: AlarmConfig) = entry.copy(generation = next(entry.generation), scheduledEpochMillis = null, scheduledToken = null, scheduleMode = null)
    fun scheduled(entry: AlarmConfig, epoch: Long, mode: AlarmScheduleMode): AlarmConfig {
        val token = "${entry.id}:${entry.generation.toString(36)}:${epoch.toString(36)}"
        return entry.copy(scheduledEpochMillis = epoch, scheduledToken = token, scheduleMode = mode, lastFailure = null, lastFailureEpochMillis = null)
    }
    fun trigger(collection: AlarmCollection, id: String, token: String, epoch: Long, now: Long, snooze: Boolean): TriggerTransition? {
        val entry = collection.alarms.firstOrNull { it.id == id } ?: return null
        val valid = if (snooze) entry.snoozeToken == token && entry.snoozeEpochMillis == epoch else entry.isReadyToSchedule() && entry.scheduledToken == token && entry.scheduledEpochMillis == epoch
        if (!valid) return null
        val delivered = if (snooze) entry.copy(snoozeToken = null, snoozeEpochMillis = null) else entry.copy(enabled = entry.repeatDays.isNotEmpty(), scheduledToken = null, scheduledEpochMillis = null, scheduleMode = null)
        val updated = collection.alarms.map { if (it.id == id) delivered else it }
        val session = AlarmSession(id, token, now + ACTIVE_WINDOW_MILLIS)
        return TriggerTransition(collection.copy(alarms = updated, activeSession = session), delivered, token, collection.activeSession)
    }
    fun captureVolume(collection: AlarmCollection, id: String, token: String, volume: Int): AlarmCollection =
        if (collection.activeSession?.alarmId == id && collection.activeSession.token == token && collection.activeSession.previousAlarmVolume == null)
            collection.copy(activeSession = collection.activeSession.copy(previousAlarmVolume = volume)) else collection
    fun end(collection: AlarmCollection, id: String, token: String): EndTransition? {
        val session = collection.activeSession ?: return null
        if (session.alarmId != id || session.token != token) return null
        return EndTransition(collection.copy(activeSession = null), session.previousAlarmVolume)
    }
    fun snooze(collection: AlarmCollection, id: String, token: String, now: Long): SnoozeTransition? {
        val end = end(collection, id, token) ?: return null
        val entry = end.collection.alarms.firstOrNull { it.id == id } ?: return null
        val epoch = now + entry.snoozeMinutes * 60_000L
        val nextToken = "${entry.id}:s:${entry.generation.toString(36)}:${epoch.toString(36)}"
        val updated = entry.copy(snoozeEpochMillis = epoch, snoozeToken = nextToken)
        return SnoozeTransition(end.collection.copy(alarms = end.collection.alarms.map { if (it.id == id) updated else it }), end.previousAlarmVolume, epoch, nextToken)
    }
    fun cancelSnooze(collection: AlarmCollection, id: String, token: String): AlarmCollection? {
        val entry = collection.alarms.firstOrNull { it.id == id } ?: return null
        if (entry.snoozeToken != token || entry.snoozeEpochMillis == null) return null
        val updated = entry.copy(snoozeEpochMillis = null, snoozeToken = null)
        return collection.copy(alarms = collection.alarms.map { if (it.id == id) updated else it })
    }
    fun sorted(entries: List<AlarmConfig>) = entries.sortedWith(compareBy<AlarmConfig>({ it.hour }, { it.minute }, { it.creationOrder }))
    fun rescheduleCandidates(entries: List<AlarmConfig>) = entries.filter(AlarmConfig::isReadyToSchedule)
    fun remove(collection: AlarmCollection, id: String) = collection.copy(
        alarms = collection.alarms.filterNot { it.id == id },
        activeSession = collection.activeSession?.takeUnless { it.alarmId == id },
    )
    private fun next(value: Long) = if (value == Long.MAX_VALUE) 1L else value + 1
}
