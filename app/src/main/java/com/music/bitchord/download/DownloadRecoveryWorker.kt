package com.music.bitchord.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.music.bitchord.R

/**
 * WorkManager wake-up for a download queue that outlived its process. It does
 * not transfer bytes itself: the existing foreground service owns progress,
 * cancellation and notification state, and starts only after this worker has
 * restored the persisted pending journal.
 *
 * On Android 12+ a background worker may not start another foreground service.
 * Promoting this worker first puts the process in the foreground-service state
 * the launch restriction exempts, then [DownloadService] takes over the same
 * data-sync slot.
 */
class DownloadRecoveryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!Downloads.hasPersistedJobs()) return Result.success()
        return runCatching {
            setForeground(foregroundInfo())
            ContextCompat.startForegroundService(
                applicationContext,
                Intent(applicationContext, DownloadService::class.java),
            )
            Result.success()
        }.getOrElse {
            android.util.Log.w("DownloadRecovery", "recovery start failed: ${it.message}", it)
            Result.retry()
        }
    }

    private fun foregroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Download recovery",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Resuming downloads interrupted by a restart"
                setShowBadge(false)
            },
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle("Resuming downloads")
            .setContentText("Checking the interrupted queue")
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        const val CHANNEL_ID = "download-recovery"
        const val NOTIFICATION_ID = 0x8176
    }
}
