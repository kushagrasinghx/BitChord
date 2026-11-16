package com.music.bitchord.download

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * WorkManager wake-up for a download queue that outlived its process. It does
 * not transfer bytes itself: the existing foreground service owns progress,
 * cancellation and notification state, and starts only after this worker has
 * restored the persisted pending journal.
 */
class DownloadRecoveryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!Downloads.hasPersistedJobs()) return Result.success()
        return runCatching {
            ContextCompat.startForegroundService(
                applicationContext,
                Intent(applicationContext, DownloadService::class.java),
            )
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}
