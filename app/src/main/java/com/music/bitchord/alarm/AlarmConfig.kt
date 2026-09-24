package com.music.bitchord.alarm

import kotlinx.serialization.Serializable

@Serializable
data class AlarmCollection(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val alarms: List<AlarmConfig> = emptyList(),
    val activeSession: AlarmSession? = null,
) {
    fun isValid() =
        schemaVersion == CURRENT_SCHEMA &&
            alarms.map(AlarmConfig::id).distinct().size == alarms.size &&
            alarms.all(AlarmConfig::isStructurallyValid)

    companion object {
        const val CURRENT_SCHEMA = 3
    }
}

@Serializable
data class AlarmSession(
    val alarmId: String,
    val token: String,
    val activeUntilEpochMillis: Long,
    val previousAlarmVolume: Int? = null,
)

@Serializable
data class AlarmConfig(
    val id: String = "test-alarm",
    val creationOrder: Long = 0L,
    val label: String = "",
    val enabled: Boolean = false,
    val hour: Int = 7,
    val minute: Int = 0,
    val repeatDays: Set<Int> = emptySet(),
    val song: AlarmSong? = null,
    val targetVolumePercent: Int = 80,
    val snoozeMinutes: Int = 10,
    val volumeButtonAction: AlarmVolumeButtonAction = AlarmVolumeButtonAction.SNOOZE,
    val generation: Long = 0L,
    val scheduledEpochMillis: Long? = null,
    val scheduledToken: String? = null,
    val scheduleMode: AlarmScheduleMode? = null,
    val snoozeEpochMillis: Long? = null,
    val snoozeToken: String? = null,
    val lastFailure: AlarmFailure? = null,
    val lastFailureEpochMillis: Long? = null,
) {
    fun isStructurallyValid() =
        id.isNotBlank() &&
            label.length <= 60 &&
            hour in 0..23 &&
            minute in 0..59 &&
            repeatDays.all { it in 1..7 } &&
            targetVolumePercent in 10..100 &&
            snoozeMinutes in 5..30 &&
            song?.isValid() != false &&
            (!enabled || song != null)

    fun isReadyToSchedule() = enabled && isStructurallyValid() && song?.isValid() == true
}

@Serializable
data class AlarmSong(
    val videoId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val durationText: String? = null,
) {
    fun isValid() = videoId.isNotBlank() && title.isNotBlank()
}

@Serializable
enum class AlarmScheduleMode { EXACT, INEXACT }

@Serializable
enum class AlarmFailure { SCHEDULING_UNAVAILABLE, PLAYBACK_UNAVAILABLE }

@Serializable
enum class AlarmVolumeButtonAction { SNOOZE, STOP, ADJUST_VOLUME }
