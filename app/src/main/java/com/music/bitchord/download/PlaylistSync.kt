package com.music.bitchord.download

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.music.bitchord.R
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.TimeUnit

/**
 * Keeps the playlists downloaded whole in step with their online copies, while
 * [AppSettings.syncDownloadedPlaylists] is on.
 *
 * What is new is worked out fresh on every check and nothing about it is stored
 * until it has been dealt with. That is what makes a check safe to lose at any
 * point: Android stopping the job, the process dying or a page failing all leave
 * the remaining tracks new, and the next check simply finds them again.
 *
 * Only ever adds. A track the listener deleted is still named by its playlist's
 * record, so it does not come back; a track removed online keeps its file.
 */
object PlaylistSync {

    private const val TAG = "BitChord"
    private const val PERIODIC_WORK = "playlist_sync"
    private const val ONE_OFF_WORK = "playlist_sync_now"
    private const val PREFS = "bitchord_playlist_sync"
    private const val KEY_LAST_CHECK = "last_complete_check"
    private const val CHANNEL_ID = "playlist_updates"
    internal const val KEY_APPROVED = "approved"

    /** Android batches this with whatever else is waking the phone in the last [FLEX_HOURS]. */
    private const val PERIOD_HOURS = 12L
    private const val FLEX_HOURS = 4L

    /** How stale the last whole check may be when the app comes to the front. */
    private const val CATCH_UP_MILLIS = 6L * 60 * 60 * 1000

    /** The most one playlist may fetch without being asked; about 1.7 GB of lossless. */
    internal const val ASK_ABOVE = 50

    /** Between requests for pages and for playlists: none of this is in a hurry. */
    private const val PAUSE_MILLIS = 400L

    /** One check at a time, however many of the triggers fire together. */
    private val running = Mutex()

    /**
     * Follows the setting and the Wi-Fi rule its schedule depends on.
     *
     * Off, this never touches WorkManager, which is initialised on demand for
     * that reason: a switch that defaults to off should cost a launch nothing.
     */
    fun init(app: Application) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            var wasOn: Boolean? = null
            AppSettings.syncDownloadedPlaylists
                .combine(AppSettings.wifiOnlyDownloads) { on, wifiOnly -> on to wifiOnly }
                .distinctUntilChanged()
                .collect { (on, wifiOnly) ->
                    val firstLook = wasOn == null
                    if (on) {
                        schedule(app, wifiOnly, replace = !firstLook)
                        if (wasOn == false) checkNow(app)
                    } else if (!firstLook) {
                        cancel(app)
                    }
                    wasOn = on
                }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (AppSettings.syncDownloadedPlaylists.value && catchUpDue(app)) checkNow(app)
            }
        })
    }

    /**
     * Queues a check to run as soon as its conditions allow.
     *
     * @param approved also fetches the batches over [ASK_ABOVE], and replaces a
     *   check still waiting so that the answer is not lost to one that won't.
     */
    fun checkNow(context: Context, approved: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<PlaylistSyncWorker>()
            .setConstraints(constraints(AppSettings.wifiOnlyDownloads.value))
            .setInputData(workDataOf(KEY_APPROVED to approved))
            .build()
        val policy = if (approved) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_OFF_WORK, policy, request)
    }

    internal fun cancel(context: Context) {
        val work = WorkManager.getInstance(context)
        work.cancelUniqueWork(PERIODIC_WORK)
        work.cancelUniqueWork(ONE_OFF_WORK)
    }

    private fun schedule(context: Context, wifiOnly: Boolean, replace: Boolean) {
        val request = PeriodicWorkRequestBuilder<PlaylistSyncWorker>(
            PERIOD_HOURS,
            TimeUnit.HOURS,
            FLEX_HOURS,
            TimeUnit.HOURS,
        ).setConstraints(constraints(wifiOnly)).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun constraints(wifiOnly: Boolean): Constraints = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .setRequiresStorageNotLow(true)
        .build()

    private fun catchUpDue(context: Context): Boolean =
        System.currentTimeMillis() - prefs(context).getLong(KEY_LAST_CHECK, 0L) >= CATCH_UP_MILLIS

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- A check ------------------------------------------------------------

    /**
     * Goes through every eligible playlist in turn.
     *
     * Only a check that read every playlist to its end is stamped as done, so
     * one that failed partway is due again the next time the app is opened.
     */
    internal suspend fun check(context: Context, approved: Boolean) {
        // An approval waits its turn. Anything else would only repeat the check
        // already running.
        if (approved) running.lock() else if (!running.tryLock()) return
        try {
            if (!storageWritable(context)) return
            var whole = true
            Downloads.collections.value.keys.forEach { id ->
                if (!AppSettings.syncDownloadedPlaylists.value || !AppSettings.downloadsAllowedNow) return
                val record = Downloads.collections.value[id] ?: return@forEach
                val browseId = syncableBrowseId(record) ?: return@forEach
                val listing = readWholePlaylist(
                    first = { YtMusicRepository.browseSongs(browseId) },
                    more = { YtMusicRepository.moreSongs(it) },
                )
                if (!listing.complete) whole = false
                update(context, record, listing, approved)
                delay(PAUSE_MILLIS)
            }
            if (whole) prefs(context).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        } finally {
            running.unlock()
        }
    }

    private suspend fun update(context: Context, record: SavedCollection, listing: Listing, approved: Boolean) {
        val fresh = newTracks(listing.songs, record)
        val notification = record.id.hashCode()
        if (fresh.isEmpty()) {
            notifications(context)?.cancel(notification)
            return
        }
        if (fresh.size > ASK_ABOVE && !approved) {
            post(context, notification, askNotification(context, notification, record.title, fresh.size))
            return
        }
        // Already on the device for some other reason: joins the playlist
        // without being fetched again.
        val saved = Downloads.saved.value
        val (onDevice, missing) = fresh.partition { it.videoId in saved }
        val cover = listing.cover ?: record.thumbnailUrl?.takeIf { it.startsWith("http") }
        val fetch = missing.map { song ->
            if (song.thumbnailUrl.isNullOrBlank() && cover != null) song.copy(thumbnailUrl = cover) else song
        }
        val handled = onDevice.mapTo(HashSet()) { it.videoId } + Downloads.downloadNow(context, fetch, record.title)

        // Read again: the listener may have deleted this download meanwhile, and
        // writing the record back would bring it back from the dead.
        val current = Downloads.collections.value[record.id] ?: return
        val order = listingToRecord(listing.songs, current, handled)
        Downloads.rememberCollection(
            DownloadTarget(
                id = current.id,
                title = current.title,
                subtitle = current.subtitle,
                thumbnailUrl = current.thumbnailUrl,
                playlist = true,
            ),
            order,
        )
        val arrived = missing.count { it.videoId in handled && it.videoId in Downloads.saved.value }
        if (arrived > 0) post(context, notification, doneNotification(context, record.title, arrived))
    }

    /**
     * Below Android 10 an exported download needs a permission only a window can
     * ask for, and every track would fail for the want of it.
     */
    private fun storageWritable(context: Context): Boolean =
        !AppSettings.exportDownloads.value || !DownloadStore.needsLegacyPermission() ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    // ---- Reading a playlist -------------------------------------------------

    /**
     * @property complete whether this reached the end of the playlist. False
     *   when a request failed; [songs] is then whatever came before it, which is
     *   still worth acting on since a check only ever adds.
     */
    internal class Listing(val songs: List<Song>, val complete: Boolean, val cover: String? = null)

    /**
     * Every track in a playlist, however many pages it runs to.
     *
     * Its own walk rather than [YtMusicRepository.allSongs], which stops at a
     * page limit and turns a failed page into a shorter list without saying so.
     * Every page is read every time: a new track can be anywhere in a playlist,
     * and one added and one removed leave its count unchanged.
     *
     * Only the rows a page lists as its own, so the suggestions YouTube offers
     * to round a playlist out are never taken for part of it.
     */
    internal suspend fun readWholePlaylist(
        first: suspend () -> Result<YtMusicRepository.SongPage>,
        more: suspend (String) -> Result<YtMusicRepository.SongPage>,
    ): Listing {
        val opening = first().getOrNull() ?: return Listing(emptyList(), complete = false)
        val cover = opening.header?.thumbnailUrl
        val songs = LinkedHashMap<String, Song>()
        opening.songs.forEach { songs.putIfAbsent(it.videoId, it) }
        var next = opening.continuation
        while (next != null) {
            delay(PAUSE_MILLIS)
            val page = more(next).getOrNull() ?: return Listing(songs.values.toList(), complete = false, cover)
            val before = songs.size
            page.songs.forEach { songs.putIfAbsent(it.videoId, it) }
            // A page with nothing new is a feed that has looped back on itself
            // with a token still attached, not one with more to give.
            if (songs.size == before) break
            next = page.continuation
        }
        return Listing(songs.values.toList(), complete = true, cover)
    }

    /**
     * What a check looks at, under the id YouTube answers to: a playlist is
     * filed under whichever id it was downloaded from, with or without its `VL`.
     *
     * Null for an album, and for the two kinds of playlist with no YouTube copy
     * to compare against: Spotify's own and those imported to this device.
     */
    internal fun syncableBrowseId(record: SavedCollection): String? {
        val id = record.id
        return when {
            !record.playlist || ':' in id || id.startsWith("MP") -> null
            id.startsWith("VL") -> id
            else -> "VL$id"
        }
    }

    /** The tracks in [listing] that [record] has never named. */
    internal fun newTracks(listing: List<Song>, record: SavedCollection): List<Song> {
        val known = record.videoIds.toHashSet()
        return listing.filter { it.videoId !in known }
    }

    /**
     * [listing] without whatever is still to be dealt with, in the playlist's
     * own order. A track left out stays new, so the next check tries it again.
     */
    internal fun listingToRecord(listing: List<Song>, record: SavedCollection, handled: Set<String>): List<Song> {
        val known = record.videoIds.toHashSet()
        return listing.filter { it.videoId in known || it.videoId in handled }
    }

    // ---- Notifications ------------------------------------------------------

    private fun notifications(context: Context): NotificationManager? =
        context.getSystemService(NotificationManager::class.java)

    private fun post(context: Context, id: Int, notification: Notification) {
        val manager = notifications(context) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.playlist_updates_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) },
        )
        runCatching { manager.notify(id, notification) }
            .onFailure { Log.w(TAG, "playlist update not posted: ${it.message}") }
    }

    private fun askNotification(context: Context, id: Int, playlist: String, count: Int): Notification {
        val approve = PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, PlaylistSyncApproval::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return builder(context)
            .setContentTitle(context.getString(R.string.playlist_sync_ask_title, playlist, count))
            .setContentText(context.getString(R.string.playlist_sync_ask_text))
            .setContentIntent(approve)
            .build()
    }

    private fun doneNotification(context: Context, playlist: String, count: Int): Notification {
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        return builder(context)
            .setContentTitle(context.getString(R.string.playlist_sync_done_title, playlist, count))
            .setContentIntent(open)
            .build()
    }

    private fun builder(context: Context) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_logo)
        .setAutoCancel(true)
}

/** The scheduled half of [PlaylistSync]; every trigger ends up here. */
class PlaylistSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Turned off in a run of the app that died before it could cancel this.
        if (!AppSettings.syncDownloadedPlaylists.value) {
            PlaylistSync.cancel(applicationContext)
            return Result.success()
        }
        PlaylistSync.check(applicationContext, inputData.getBoolean(PlaylistSync.KEY_APPROVED, false))
        // Never a retry: an incomplete check is due again when the app next
        // opens, and a playlist that has been deleted online would otherwise
        // have WorkManager retrying it for as long as the setting stays on.
        return Result.success()
    }
}

/** The tap on a "new songs in this playlist" notification. */
class PlaylistSyncApproval : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PlaylistSync.checkNow(context, approved = true)
    }
}
