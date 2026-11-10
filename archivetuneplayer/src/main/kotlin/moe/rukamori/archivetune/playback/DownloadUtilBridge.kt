/*
 * BRIDGE (replaces ArchiveTune's playback/DownloadUtil.kt).
 *
 * The download manager surface the copied menus read: an inert manager over
 * real (empty) media3 caches, so the states the UI switches on all exist.
 * Downloads themselves are the host's business — the host bridge's download
 * hook decides what actually happens when a song is sent to it.
 *
 * media3 1.11 notes: DownloadManager takes the DatabaseProvider directly
 * (its download index lives inside), and DownloadService's abstract
 * surface is getDownloadManager / getScheduler / getForegroundNotification
 * with the notification channel described by string resources.
 */

package moe.rukamori.archivetune.playback

import android.app.Notification
import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.DownloadSource

class DownloadUtil(
    @Suppress("UNUSED_PARAMETER") context: Context,
) {
    /** Real but always-empty: this port's downloads are the host's own. A
     *  StateFlow, as the copied UI collects it without an initial value. */
    val downloads: StateFlow<Map<String, Download>> = MutableStateFlow(emptyMap())

    val downloadCache: Cache =
        SimpleCache(
            java.io.File(context.cacheDir, "at_download_bridge"),
            NoOpCacheEvictor(),
            StandaloneDatabaseProvider(context.applicationContext),
        )

    private val dataSourceFactory =
        DefaultDataSource.Factory(context.applicationContext)

    val downloadManager: DownloadManager =
        DownloadManager(
            context.applicationContext,
            StandaloneDatabaseProvider(context.applicationContext),
            downloadCache,
            dataSourceFactory,
        )

    fun getDownload(songId: String): kotlinx.coroutines.flow.Flow<Download?> = downloads.map { it[songId] }

    fun currentSourceDownloadTarget(mediaId: String): DownloadTarget =
        DownloadTarget(
            source = DownloadSource.YOUTUBE_MUSIC,
            key = mediaId,
            legacyIds = listOf(mediaId),
        )

    fun currentSourceDownloadIds(mediaId: String): List<String> = listOf(mediaId)

    fun clearCurrentTargetCacheSpans(mediaId: String) {}

    suspend fun prewarmSongForDownload(mediaId: String): String? = null

    data class DownloadTarget(
        val source: DownloadSource,
        val key: String,
        val legacyIds: List<String>,
    )
}

/**
 * BRIDGE — the service the menus address download commands at. Inert: its
 * manager is the bridge one above, so the commands land on empty state and
 * the host's own downloads are what a user actually gets.
 */
class ExoDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    0L,
    CHANNEL_ID,
    R.string.download_channel_name,
    R.string.download_channel_description,
) {
    override fun getDownloadManager(): DownloadManager = BridgeManagerHolder.get(this)

    override fun getScheduler(): androidx.media3.exoplayer.scheduler.Scheduler? = null

    override fun getForegroundNotification(
        downloads: List<Download>,
        notMetRequirements: Int,
    ): Notification =
        BridgeManagerHolder
            .notificationHelper(this)
            .buildProgressNotification(
                this,
                android.R.drawable.stat_sys_download,
                null,
                null,
                downloads,
                notMetRequirements,
            )

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 40_960
        private const val CHANNEL_ID = "archivetune_downloads"
    }
}

internal object BridgeManagerHolder {
    private var manager: DownloadManager? = null
    private var helper: androidx.media3.exoplayer.offline.DownloadNotificationHelper? = null

    fun get(context: Context): DownloadManager =
        manager ?: DownloadUtil(context).downloadManager.also { manager = it }

    fun notificationHelper(context: Context): androidx.media3.exoplayer.offline.DownloadNotificationHelper =
        helper ?: androidx.media3.exoplayer.offline.DownloadNotificationHelper(
            context.applicationContext,
            "archivetune_downloads",
        ).also { helper = it }
}
