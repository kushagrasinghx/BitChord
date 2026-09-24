package com.music.bitchord.alarm

enum class AlarmVolumeButtonCommand {
    SNOOZE,
    STOP,
    ADJUST_ALARM_VOLUME,
    CONSUME,
}

/** Maps a ringing-only volume-key event without depending on Activity or playback state. */
fun volumeButtonCommand(
    preference: AlarmVolumeButtonAction,
    isInitialKeyDown: Boolean,
): AlarmVolumeButtonCommand = when (preference) {
    AlarmVolumeButtonAction.ADJUST_VOLUME -> AlarmVolumeButtonCommand.ADJUST_ALARM_VOLUME
    AlarmVolumeButtonAction.SNOOZE ->
        if (isInitialKeyDown) AlarmVolumeButtonCommand.SNOOZE else AlarmVolumeButtonCommand.CONSUME
    AlarmVolumeButtonAction.STOP ->
        if (isInitialKeyDown) AlarmVolumeButtonCommand.STOP else AlarmVolumeButtonCommand.CONSUME
}
