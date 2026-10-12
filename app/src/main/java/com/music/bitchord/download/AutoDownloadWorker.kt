package com.music.bitchord.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AutoDownloadNetMode
import java.util.concurrent.TimeUnit

/**
 * Background half of automatic downloads, for `WIFI_CHARGING` mode only.
 *
 * The constraints below are held by the system: the app does not exist in
 * memory until both are true, so waiting costs no battery. Foreground modes
 * (`ALWAYS`, `WIFI_ONLY`) never schedule this — they sync while open.
 */
class AutoDownloadWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!AppSettings.autoDownloadEnabled.value) return Result.success()
        if (AppSettings.autoDownloadNetMode.value != AutoDownloadNetMode.WIFI_CHARGING) {
            return Result.success()
        }
        return when (val r = AutoDownloads.sync(applicationContext)) {
            is AutoDownloads.SyncResult.Done,
            is AutoDownloads.SyncResult.Capped,
            -> Result.success()
            is AutoDownloads.SyncResult.NotAllowedNow,
            is AutoDownloads.SyncResult.Disabled,
            -> Result.success()
            is AutoDownloads.SyncResult.Failed -> Result.retry()
        }
    }
}

/** One periodic slot, kept in sync with the settings by [ensure]. */
object AutoDownloadSchedule {
    private const val UNIQUE = "auto-downloads"

    fun ensure(context: Context) {
        val wm = WorkManager.getInstance(context.applicationContext)
        if (
            AppSettings.autoDownloadEnabled.value &&
            AppSettings.autoDownloadNetMode.value == AutoDownloadNetMode.WIFI_CHARGING
        ) {
            val req = PeriodicWorkRequestBuilder<AutoDownloadWorker>(24, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresCharging(true)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            wm.enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, req)
        } else {
            wm.cancelUniqueWork(UNIQUE)
        }
    }
}
