package com.music.bitchord.alarm

import kotlinx.serialization.Serializable

/** The single music alarm configured on this device. */
@Serializable
data class AlarmConfig(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val enabled: Boolean = false,
    val hour: Int = 7,
    val minute: Int = 0,
    /** ISO weekdays: Monday = 1 through Sunday = 7. Empty means one-shot. */
    val repeatDays: Set<Int> = emptySet(),
    val playlistId: String = "",
    val playlistTitle: String = "",
    val playlistArtworkUrl: String? = null,
    val generation: Long = 0L,
    val scheduledEpochMillis: Long? = null,
    val scheduledToken: String? = null,
    val scheduleMode: AlarmScheduleMode? = null,
    val activeToken: String? = null,
    val activeUntilEpochMillis: Long? = null,
    val lastFailure: AlarmFailure? = null,
    val lastFailureEpochMillis: Long? = null,
) {
    fun isStructurallyValid(): Boolean =
        schemaVersion == CURRENT_SCHEMA &&
            hour in 0..23 &&
            minute in 0..59 &&
            repeatDays.all { it in 1..7 }

    fun isReadyToSchedule(): Boolean =
        enabled && isStructurallyValid() && playlistId.isNotBlank()

    companion object {
        const val CURRENT_SCHEMA = 1
    }
}
@Serializable
enum class AlarmScheduleMode {
    EXACT,
    INEXACT,
}

@Serializable
enum class AlarmFailure {
    SCHEDULING_UNAVAILABLE,
    PLAYBACK_UNAVAILABLE,
}
