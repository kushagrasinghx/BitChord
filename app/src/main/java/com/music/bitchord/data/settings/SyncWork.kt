package com.music.bitchord.data.settings

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.ktor.client.plugins.HttpRequestTimeoutException
import com.music.bitchord.sync.SyncApiException
import com.music.bitchord.sync.SyncConflictRetriesExhausted
import com.music.bitchord.sync.SyncProfileDisabled
import com.music.bitchord.sync.SyncProfileMissing
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** Schedules network-constrained sync only after the user opts in. */
internal class AndroidSyncSchedule(context: Context) : SyncSchedulePort {
    private val appContext = context.applicationContext

    override fun setEnabled(enabled: Boolean) {
        val workManager = WorkManager.getInstance(appContext)
        if (!enabled) {
            workManager.cancelUniqueWork(PERIODIC_WORK)
            workManager.cancelUniqueWork(IMMEDIATE_WORK)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val periodic = PeriodicWorkRequestBuilder<BitChordSyncWorker>(
            PERIOD_HOURS,
            TimeUnit.HOURS,
            FLEX_HOURS,
            TimeUnit.HOURS,
        ).setConstraints(constraints).build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, periodic)

        val immediate = OneTimeWorkRequestBuilder<BitChordSyncWorker>()
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.KEEP, immediate)
    }

    private companion object {
        const val PERIODIC_WORK = "bitchord_cross_device_sync_periodic"
        const val IMMEDIATE_WORK = "bitchord_cross_device_sync_now"
        const val PERIOD_HOURS = 6L
        const val FLEX_HOURS = 2L
    }
}

/** Runs one encrypted synchronization pass. The server receives ciphertext only. */
class BitChordSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val manager = AndroidSyncManager(applicationContext)
        val state = manager.localState()
        if (state == null || !state.enabled) {
            Result.success()
        } else {
            manager.syncNow()
            Result.success()
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SyncProfileMissing) {
        Result.success()
    } catch (_: SyncProfileDisabled) {
        Result.success()
    } catch (error: Exception) {
        if (SyncWorkRetryPolicy.shouldRetry(error)) Result.retry() else Result.failure()
    }
}

internal object SyncWorkRetryPolicy {
    fun shouldRetry(error: Throwable): Boolean = when (error) {
        is IOException,
        is HttpRequestTimeoutException,
        is SyncConflictRetriesExhausted,
        -> true
        is SyncApiException -> error.statusCode in RETRYABLE_HTTP_CODES || error.statusCode >= 500
        else -> false
    }

    private val RETRYABLE_HTTP_CODES = setOf(408, 425, 429)
}
