package com.music.bitchord.alarm

/** Pure state transitions shared by the scheduler, receiver, and JVM tests. */
object AlarmStateMachine {

    const val ACTIVE_WINDOW_MILLIS = 4L * 60L * 60L * 1_000L

    data class TriggerTransition(
        val config: AlarmConfig,
        val playlistId: String,
        val playlistTitle: String,
        val token: String,
        val recurring: Boolean,
    )

    fun edit(current: AlarmConfig, proposed: AlarmConfig): AlarmConfig {
        val next = proposed.copy(
            schemaVersion = AlarmConfig.CURRENT_SCHEMA,
            enabled = proposed.enabled && proposed.playlistId.isNotBlank(),
            generation = nextGeneration(current.generation),
            scheduledEpochMillis = null,
            scheduledToken = null,
            scheduleMode = null,
            activeToken = current.activeToken,
            activeUntilEpochMillis = current.activeUntilEpochMillis,
            lastFailure = null,
            lastFailureEpochMillis = null,
        )
        return if (next.isStructurallyValid()) next else current
    }

    fun invalidateSchedule(config: AlarmConfig): AlarmConfig = config.copy(
        generation = nextGeneration(config.generation),
        scheduledEpochMillis = null,
        scheduledToken = null,
        scheduleMode = null,
    )

    fun scheduled(
        config: AlarmConfig,
        epochMillis: Long,
        mode: AlarmScheduleMode,
    ): AlarmConfig {
        val token = token(config.generation, epochMillis)
        return config.copy(
            scheduledEpochMillis = epochMillis,
            scheduledToken = token,
            scheduleMode = mode,
            lastFailure = null,
            lastFailureEpochMillis = null,
        )
    }

    fun consumeTrigger(
        config: AlarmConfig,
        token: String,
        epochMillis: Long,
        nowEpochMillis: Long,
    ): TriggerTransition? {
        if (!config.isReadyToSchedule()) return null
        if (config.scheduledToken != token || config.scheduledEpochMillis != epochMillis) return null

        val recurring = config.repeatDays.isNotEmpty()
        val delivered = config.copy(
            enabled = recurring,
            scheduledEpochMillis = null,
            scheduledToken = null,
            scheduleMode = null,
            activeToken = token,
            activeUntilEpochMillis = nowEpochMillis + ACTIVE_WINDOW_MILLIS,
            lastFailure = null,
            lastFailureEpochMillis = null,
        )
        return TriggerTransition(
            config = delivered,
            playlistId = config.playlistId,
            playlistTitle = config.playlistTitle,
            token = token,
            recurring = recurring,
        )
    }

    fun canStop(config: AlarmConfig, token: String, nowEpochMillis: Long): Boolean =
        config.activeToken == token &&
            (config.activeUntilEpochMillis ?: Long.MIN_VALUE) >= nowEpochMillis

    fun stopped(config: AlarmConfig, token: String): AlarmConfig =
        if (config.activeToken == token) {
            config.copy(activeToken = null, activeUntilEpochMillis = null)
        } else {
            config
        }

    fun playbackFailed(config: AlarmConfig, token: String, nowEpochMillis: Long): AlarmConfig =
        if (config.activeToken == token) {
            config.copy(
                activeToken = null,
                activeUntilEpochMillis = null,
                lastFailure = AlarmFailure.PLAYBACK_UNAVAILABLE,
                lastFailureEpochMillis = nowEpochMillis,
            )
        } else {
            config
        }

    fun schedulingFailed(config: AlarmConfig, nowEpochMillis: Long): AlarmConfig = config.copy(
        scheduledEpochMillis = null,
        scheduledToken = null,
        scheduleMode = null,
        lastFailure = AlarmFailure.SCHEDULING_UNAVAILABLE,
        lastFailureEpochMillis = nowEpochMillis,
    )

    private fun token(generation: Long, epochMillis: Long): String =
        "${generation.toString(36)}:${epochMillis.toString(36)}"

    private fun nextGeneration(value: Long): Long =
        if (value == Long.MAX_VALUE) 1L else value + 1L
}
