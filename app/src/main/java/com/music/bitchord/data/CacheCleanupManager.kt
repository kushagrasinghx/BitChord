package com.music.bitchord.data

import com.music.bitchord.data.settings.AppSettings
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

enum class AutoClearCacheInterval(val days: Int, val label: String) {
    OFF(0, "Off"),
    EVERY_3_DAYS(3, "Every 3 days"),
    WEEKLY(7, "Weekly"),
    MONTHLY(30, "Monthly");

    fun next(): AutoClearCacheInterval = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromDays(days: Int): AutoClearCacheInterval =
            entries.firstOrNull { it.days == days } ?: OFF
    }
}

enum class AutoClearCacheSizeLimit(val maxMb: Int, val label: String) {
    UNLIMITED(0, "Unlimited"),
    MB_250(250, "250 MB"),
    MB_500(500, "500 MB"),
    GB_1(1024, "1 GB");

    val maxBytes: Long get() = maxMb.toLong() * 1024L * 1024L

    fun next(): AutoClearCacheSizeLimit = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromMb(mb: Int): AutoClearCacheSizeLimit =
            entries.firstOrNull { it.maxMb == mb } ?: UNLIMITED
    }
}

/**
 * Periodically prunes temporary cache files inside the app's cache directory
 * (image cache, Canvas clips, remote artwork, and streamed audio cache)
 * while leaving offline downloaded songs in app/external storage untouched.
 */
object CacheCleanupManager {

    private val TARGET_SUBDIRS = listOf("image_cache", "canvas", "remote_art", "exoplayer", "audio_cache")

    fun scheduleStartupCheck(cacheDir: File) {
        val intervalDays = AppSettings.autoClearCacheIntervalDays.value
        val maxMb = AppSettings.autoClearCacheMaxMb.value
        if (intervalDays <= 0 && maxMb <= 0) return

        thread(name = "cache-auto-cleanup", isDaemon = true) {
            runCatching {
                runIfNeeded(
                    cacheDir = cacheDir,
                    intervalDays = intervalDays,
                    maxBytes = AutoClearCacheSizeLimit.fromMb(maxMb).maxBytes,
                    lastCleanupMs = AppSettings.lastAutoCacheCleanupMs.value,
                    nowMs = System.currentTimeMillis(),
                ) { cleanedAt ->
                    AppSettings.setLastAutoCacheCleanupMs(cleanedAt)
                }
            }
        }
    }

    internal fun runIfNeeded(
        cacheDir: File,
        intervalDays: Int,
        maxBytes: Long,
        lastCleanupMs: Long,
        nowMs: Long,
        onCleanupTimestamp: (Long) -> Unit,
    ): Int {
        if (!cacheDir.exists()) return 0
        val cacheFiles = TARGET_SUBDIRS
            .map { File(cacheDir, it) }
            .filter { it.exists() && it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile }.toList() }

        if (cacheFiles.isEmpty()) return 0

        val intervalDue = intervalDays > 0 &&
            (lastCleanupMs <= 0L || nowMs - lastCleanupMs >= TimeUnit.DAYS.toMillis(intervalDays.toLong()))

        val cutoffMs = if (intervalDue) {
            nowMs - TimeUnit.DAYS.toMillis(intervalDays.toLong())
        } else {
            0L
        }

        val toDelete = selectFilesToPrune(
            files = cacheFiles,
            maxBytes = maxBytes,
            ageCutoffMs = cutoffMs,
        )

        var deleted = 0
        for (file in toDelete) {
            if (file.delete()) deleted++
        }
        if (intervalDue) {
            onCleanupTimestamp(nowMs)
        }
        return deleted
    }

    internal fun selectFilesToPrune(
        files: List<File>,
        maxBytes: Long,
        ageCutoffMs: Long,
    ): List<File> {
        val selected = LinkedHashSet<File>()

        if (ageCutoffMs > 0L) {
            for (file in files) {
                if (file.lastModified() < ageCutoffMs) {
                    selected.add(file)
                }
            }
        }

        if (maxBytes > 0L) {
            val remaining = files
                .filter { it !in selected }
                .sortedBy { it.lastModified() }
            var totalBytes = remaining.sumOf { it.length() }
            for (file in remaining) {
                if (totalBytes <= maxBytes) break
                selected.add(file)
                totalBytes -= file.length()
            }
        }

        return selected.toList()
    }
}
