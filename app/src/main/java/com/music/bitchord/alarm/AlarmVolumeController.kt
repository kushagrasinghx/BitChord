package com.music.bitchord.alarm

import android.content.Context
import android.media.AudioManager
import android.os.Build

object AlarmVolumeController {
    const val STREAM = AudioManager.STREAM_ALARM

    fun apply(context: Context, alarm: AlarmConfig, token: String): Int? {
        val manager = context.getSystemService(AudioManager::class.java) ?: return null
        val previous = runCatching { manager.getStreamVolume(STREAM) }.getOrNull() ?: return null
        val minimum = if (Build.VERSION.SDK_INT >= 28) manager.getStreamMinVolume(STREAM) else 0
        val maximum = manager.getStreamMaxVolume(STREAM)
        AlarmScheduler.captureVolume(context, alarm.id, token, previous)
        runCatching {
            manager.setStreamVolume(
                STREAM,
                AlarmVolumePolicy.streamIndex(alarm.targetVolumePercent, minimum, maximum),
                0,
            )
        }
        return previous
    }

    fun restore(context: Context, volume: Int?) {
        if (volume == null) return
        val manager = context.getSystemService(AudioManager::class.java) ?: return
        runCatching {
            manager.setStreamVolume(
                STREAM,
                volume.coerceIn(0, manager.getStreamMaxVolume(STREAM)),
                0,
            )
        }
    }
}
