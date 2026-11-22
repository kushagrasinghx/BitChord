package com.music.bitchord.data.telegram

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/** Resumable message-history scanner. Each successful page commits its cursor before the next. */
class TelegramIndexWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!TelegramClientManager.isReady) {
            TelegramClientManager.updateIndexingStatus("Waiting for Telegram connection; indexing will retry automatically.")
            return Result.retry()
        }
        val started = System.currentTimeMillis()
        val timeBudgetMs = 8 * 60 * 1000L
        return try {
            while (!isStopped && System.currentTimeMillis() - started < timeBudgetMs) {
                val selected = TelegramClientManager.selectedChatIds
                if (selected.isEmpty()) {
                    TelegramClientManager.updateIndexingStatus(null)
                    return Result.success()
                }
                var incomplete = false
                for (chatId in selected.sorted()) {
                    if (isStopped) {
                        TelegramClientManager.updateIndexingStatus("Indexing paused; it will resume automatically.")
                        return Result.retry()
                    }
                    val chatTitle = TelegramClientManager.chats.value.firstOrNull { it.id == chatId }?.displayTitle ?: "Telegram source"
                    TelegramClientManager.updateIndexingStatus("Indexing $chatTitle…")
                    val page = TelegramClientManager.indexPage(chatId)
                    incomplete = incomplete || !page.complete
                    setProgress(androidx.work.workDataOf("chat" to chatId, "messages" to page.messages))
                    delay(250)
                }
                if (!incomplete) {
                    TelegramClientManager.updateIndexingStatus(null)
                    return Result.success()
                }
            }
            TelegramClientManager.updateIndexingStatus("Indexing paused; it will resume automatically.")
            Result.retry()
        } catch (cancelled: CancellationException) {
            TelegramClientManager.updateIndexingStatus("Indexing paused; it will resume automatically.")
            throw cancelled
        } catch (_: Exception) {
            TelegramClientManager.updateIndexingStatus("Indexing paused after a Telegram error; it will retry automatically.")
            Result.retry()
        }
    }

    companion object {
        private const val ONE_TIME_NAME = "telegram-music-index"
        private const val PERIODIC_NAME = "telegram-music-sync"
        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueue(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                ONE_TIME_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<TelegramIndexWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build(),
            )
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<TelegramIndexWorker>(6, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build(),
            )
        }
    }
}
