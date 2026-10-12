package com.music.bitchord.download

import android.content.Context
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Foreground triggers for automatic downloads.
 *
 * One debounced observer over the switches and the connections: enabling the
 * feature, flipping its network mode, joining Wi-Fi or plugging in all funnel
 * into the same delayed [AutoDownloads.sync]. The worker slot for
 * `WIFI_CHARGING` is refreshed on the same beats.
 *
 * Delayed past startup on purpose: the first sync must never race library
 * load, stream resolution, or the queue restore.
 */
object AutoDownloadTriggers {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var installed = false

    fun install(context: Context) {
        synchronized(this) {
            if (installed) return
            installed = true
        }
        val app = context.applicationContext
        AutoDownloadSchedule.ensure(app)
        scope.launch {
            delay(STARTUP_DELAY_MS)
            if (AppSettings.autoDownloadsAllowedNow) {
                AutoDownloads.sync(app)
            }
            combine(
                AppSettings.autoDownloadEnabled,
                AppSettings.autoDownloadNetMode,
                AppSettings.meteredConnection,
                AppSettings.isCharging,
                AppSettings.batteryPct,
            ) { v1, v2, v3, v4, v5 ->
                // Key on everything the gate reads: keying on `enabled`
                // alone would swallow network/battery flips while the
                // switch stays on.
                listOf(v1, v2, v3, v4, v5)
            }
                // A real debounce: a burst of connectivity flips settles
                // into one sync instead of N sequential crawls.
                .debounce(TRIGGER_DEBOUNCE_MS)
                // The initial emission replays the startup sync's own
                // conditions — without this it syncs twice on every launch.
                .drop(1)
                .collect {
                    AutoDownloadSchedule.ensure(app)
                    if (AppSettings.autoDownloadsAllowedNow) {
                        AutoDownloads.sync(app)
                    } else {
                        AutoDownloads.enforceQuota(app)
                    }
                }
        }
    }

    private const val STARTUP_DELAY_MS = 15_000L
    private const val TRIGGER_DEBOUNCE_MS = 5_000L
}
