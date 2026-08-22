package com.music.bitchord.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the process alive while the download queue drains, and says so.
 *
 * A download is the one thing this app does that a user starts and then leaves:
 * they tap it and put the phone in a pocket. A coroutine on a ViewModel scope
 * would be killed the moment the activity goes, and a plain background service
 * on a modern Android is killed almost as fast — so this is a foreground
 * service, which is also the only honest arrangement, since a notification is
 * exactly what the user should get for work happening out of sight.
 *
 * It owns no state. The queue and everything known about it live in
 * [Downloads]; this drives that queue and reflects it into a notification, and
 * stops itself the moment there is nothing left to do.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private var drain: Job? = null
    private var notifier: Job? = null

    /** What the notification is currently about. */
    @Volatile
    private var current: Song? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must happen within a few seconds of the start request whatever the
        // intent turns out to be, the cancel below included — a service started
        // with startForegroundService and never promoted takes the app down
        // with it.
        promote()

        if (intent?.action == ACTION_CANCEL_ALL) {
            Downloads.active.value.keys.toList().forEach(Downloads::cancel)
            shutdown(stopWork = true)
            return START_NOT_STICKY
        }

        if (drain == null) {
            drain = scope.launch {
                drainQueue()
                // Not shutdown(stopWork = true): this is the drain coroutine,
                // and cancelling its own job here would be cancelling itself.
                shutdown(stopWork = false)
            }
            notifier = scope.launch { reflectProgress() }
        }
        // Not sticky: a queue is a list of things asked for in a session, and
        // reviving the service without one would put up a notification about
        // nothing.
        return START_NOT_STICKY
    }

    /**
     * One track at a time.
     *
     * Sequential because these are ranged fetches already served at line rate —
     * two at once would finish neither sooner — and because a single running
     * item is what makes the notification a sentence rather than a tally.
     */
    private suspend fun drainQueue() {
        while (true) {
            val song = Downloads.takeNext() ?: break
            current = song
            postNotification()

            // Its own job, so one track can be cancelled out from under the
            // loop without taking the rest of the queue with it.
            val job = scope.launch { Downloads.run(this@DownloadService, song) }
            Downloads.onRunning(song.videoId, job)
            job.join()
            Downloads.onIdle()
        }
        current = null
    }

    /** Repost as the running track advances, slowly enough not to thrash the shade. */
    private suspend fun reflectProgress() {
        Downloads.active.collect {
            postNotification()
            delay(PROGRESS_REFRESH_MS)
        }
    }

    private fun shutdown(stopWork: Boolean) {
        if (stopWork) drain?.cancel()
        notifier?.cancel()
        drain = null
        notifier = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        Downloads.onIdle()
        super.onDestroy()
    }

    // ---- Notification -------------------------------------------------------

    private fun promote() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun postNotification() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.notify(NOTIFICATION_ID, buildNotification()) }
    }

    private fun buildNotification(): Notification {
        val song = current
        val state = song?.let { Downloads.active.value[it.videoId] }
        val percent = ((state as? DownloadState.Running)?.fraction ?: 0f).times(100).toInt()
        val waiting = Downloads.active.value.count { it.value is DownloadState.Queued }

        val cancel = PendingIntent.getService(
            this,
            0,
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(song?.title ?: "Downloading")
            .setContentText(
                when {
                    song == null -> "Starting"
                    waiting > 0 -> "${song.artist} · $waiting more queued"
                    else -> song.artist
                },
            )
            // Indeterminate until the length is known, which is one request in.
            .setProgress(100, percent, state !is DownloadState.Running)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, "Cancel", cancel)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                // Progress, not news. It belongs in the shade without a sound
                // or a heads-up every time a track finishes.
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Songs being saved to your Music folder"
                setShowBadge(false)
            },
        )
    }

    private companion object {
        const val CHANNEL_ID = "downloads"

        /** Distinct from playback's, which Media3 owns. */
        const val NOTIFICATION_ID = 0x8175

        const val ACTION_CANCEL_ALL = "com.music.bitchord.download.CANCEL_ALL"

        /** Four updates a second is smooth; the shade coalesces anything faster anyway. */
        const val PROGRESS_REFRESH_MS = 250L
    }
}
