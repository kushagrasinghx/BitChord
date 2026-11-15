package com.music.bitchord.playback.smart

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.io.File

/**
 * Manages stem cache storage, charging constraints, and cleanup for AutoMix.
 */
object StemManager {

    private const val STEM_DIR = "automix_stems"

    fun stemDirectory(context: Context): File =
        File(context.filesDir, STEM_DIR).apply { if (!exists()) mkdirs() }

    fun getStemStorageBytes(context: Context): Long {
        val dir = stemDirectory(context)
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun clearStems(context: Context): Boolean {
        val dir = stemDirectory(context)
        val files = dir.listFiles() ?: return true
        var allDeleted = true
        for (file in files) {
            if (!file.deleteRecursively()) allDeleted = false
        }
        return allDeleted
    }

    fun isDeviceCharging(context: Context): Boolean {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, filter)
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun shouldProcessStems(context: Context, chargingOnly: Boolean): Boolean {
        if (!chargingOnly) return true
        return isDeviceCharging(context)
    }

    fun formatStorageSize(bytes: Long): String = when {
        bytes <= 0L -> "0 B"
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
