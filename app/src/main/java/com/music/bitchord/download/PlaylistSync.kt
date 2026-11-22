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
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.LikeStatus
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
 * [AppSettings.syncDownloadedPlaylists] is on, and the account's Liked Music
 * downloaded, while [AppSettings.downloadLikedSongs] is.
 *
 * Liked Music is filed as one more downloaded playlist, under
 * [YtMusicRepository.LIKED_MUSIC], so it reads back on the Downloads page like
 * any other. What sets it apart is that the setting stands in for the tap: the
 * first check fetches the whole of it without asking, and a like made in the
 * app brings a quick look at its newest page instead of waiting for the next
 * scheduled check.
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
    private const val LIKED_WORK = "liked_sync_soon"
    private const val PREFS = "bitchord_playlist_sync"
    private const val KEY_LAST_CHECK = "last_complete_check"
    private const val CHANNEL_ID = "playlist_updates"
    internal const val KEY_APPROVED = "approved"
    internal const val KEY_LIKED_ONLY = "liked_only"

    /** Android batches this with whatever else is waking the phone in the last [FLEX_HOURS]. */
    private const val PERIOD_HOURS = 12L
    private const val FLEX_HOURS = 4L

    /** How stale the last whole check may be when the app comes to the front. */
    private const val CATCH_UP_MILLIS = 6L * 60 * 60 * 1000

    /** The most one playlist may fetch without being asked; about 1.7 GB of lossless. */
    internal const val ASK_ABOVE = 50

    /**
     * How long after a like the quick look waits. Each like restarts it, so a
     * run of likes is one request rather than one each, and the rating has
     * reached YouTube by the time its list is read.
     */
    private const val LIKED_SETTLE_SECONDS = 20L

    /** Between requests for pages and for playlists: none of this is in a hurry. */
    private const val PAUSE_MILLIS = 400L

    /** One check at a time, however many of the triggers fire together. */
    private val running = Mutex()

    /** Whether either setting wants checks run at all. */
    private val enabled: Boolean
        get() = AppSettings.syncDownloadedPlaylists.value || AppSettings.downloadLikedSongs.value

    /**
     * Follows the two settings and the Wi-Fi rule their schedule depends on.
     *
     * Off, this never touches WorkManager, which is initialised on demand for
     * that reason: switches that default to off should cost a launch nothing.
     */
    fun init(app: Application) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            var was: Pair<Boolean, Boolean>? = null
            AppSettings.syncDownloadedPlaylists
                .combine(AppSettings.downloadLikedSongs) { playlists, liked -> playlists to liked }
                .combine(AppSettings.wifiOnlyDownloads) { settings, wifiOnly -> settings to wifiOnly }
                .distinctUntilChanged()
                .collect { (settings, wifiOnly) ->
                    val (playlists, liked) = settings
                    val before = was
                    if (playlists || liked) {
                        schedule(app, wifiOnly, replace = before != null)
                        // Only a switch just turned on, not every launch with
                        // one already on: the catch-up below handles those.
                        val turnedOn = before != null &&
                            ((playlists && !before.first) || (liked && !before.second))
                        if (turnedOn) checkNow(app)
                    } else if (before != null) {
                        cancel(app)
                    }
                    was = settings
                }
        }
        scope.launch {
            // Anything that turns a song liked, from any surface: the player,
            // a menu, the notification. A rating read off the library or a
            // menu counts too, which costs one page at most and is how a like
            // made on another device gets noticed while the app is open.
            var before = LikeState.overrides.value
            LikeState.overrides.collect { now ->
                val newlyLiked = now.any { (id, status) ->
                    status == LikeStatus.LIKE && before[id] != LikeStatus.LIKE
                }
                before = now
                if (newlyLiked && AppSettings.downloadLikedSongs.value) checkLikedSoon(app)
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (enabled && catchUpDue(app)) checkNow(app)
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

    /**
     * Queues a look at the newest page of Liked Music, where a new like lands,
     * [LIKED_SETTLE_SECONDS] from now. Replaces one still waiting, so the
     * wait restarts with every like.
     */
    private fun checkLikedSoon(context: Context) {
        val request = OneTimeWorkRequestBuilder<PlaylistSyncWorker>()
            .setConstraints(constraints(AppSettings.wifiOnlyDownloads.value))
            .setInitialDelay(LIKED_SETTLE_SECONDS, TimeUnit.SECONDS)
            .setInputData(workDataOf(KEY_LIKED_ONLY to true))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(LIKED_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    internal fun cancel(context: Context) {
        val work = WorkManager.getInstance(context)
        work.cancelUniqueWork(PERIODIC_WORK)
        work.cancelUniqueWork(ONE_OFF_WORK)
        work.cancelUniqueWork(LIKED_WORK)
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
     * Goes through every eligible playlist in turn, then Liked Music.
     *
     * Only a check that read every playlist to its end is stamped as done, so
     * one that failed partway is due again the next time the app is opened.
     *
     * @param likedOnly the quick look after a like: the newest page of Liked
     *   Music and nothing else. Never stamped, since it reads no whole list.
     */
    internal suspend fun check(context: Context, approved: Boolean, likedOnly: Boolean = false) {
        // An approval or a like waits its turn: the check already running may
        // be past the part that would have covered it. Anything else would only
        // repeat that check.
        if (approved || likedOnly) running.lock() else if (!running.tryLock()) return
        try {
            if (!storageWritable(context)) return
            var whole = true
            if (!likedOnly && AppSettings.syncDownloadedPlaylists.value) {
                for (id in Downloads.collections.value.keys) {
                    if (!AppSettings.syncDownloadedPlaylists.value || !AppSettings.downloadsAllowedNow) {
                        whole = false
                        break
                    }
                    val record = Downloads.collections.value[id] ?: continue
                    val browseId = syncableBrowseId(record) ?: continue
                    // Its own pass below while that setting is on.
                    if (browseId == YtMusicRepository.LIKED_MUSIC && AppSettings.downloadLikedSongs.value) continue
                    val listing = readWholePlaylist(
                        first = { YtMusicRepository.browseSongs(browseId) },
                        more = { YtMusicRepository.moreSongs(it) },
                    )
                    if (!listing.complete) whole = false
                    update(context, record, listing, approved)
                    delay(PAUSE_MILLIS)
                }
            }
            if (AppSettings.downloadLikedSongs.value) {
                if (!AppSettings.downloadsAllowedNow || !checkLiked(context, approved, likedOnly)) whole = false
            }
            if (whole && !likedOnly) prefs(context).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        } finally {
            running.unlock()
        }
    }

    /**
     * Brings the downloaded Liked Music up to date with the account's.
     *
     * With no record of it yet this is the first fill, which reads the whole
     * list and fetches it without asking: turning the setting on was the ask.
     * After that a [newestOnly] look reads one page, since a like always lands
     * at the top, and leaves anything deeper to the scheduled whole check.
     *
     * Only ever adds, like the playlists: un-liking a song keeps its file.
     *
     * @return whether the list was read to its end.
     */
    private suspend fun checkLiked(context: Context, approved: Boolean, newestOnly: Boolean): Boolean {
        val existing = Downloads.collections.value[YtMusicRepository.LIKED_MUSIC]
        val listing = if (newestOnly && existing != null) {
            val page = YtMusicRepository.browseSongs(YtMusicRepository.LIKED_MUSIC).getOrNull()
                ?: return false
            Listing(page.songs.distinctBy { it.videoId }, complete = true, cover = page.header?.thumbnailUrl)
        } else {
            readWholePlaylist(
                first = { YtMusicRepository.browseSongs(YtMusicRepository.LIKED_MUSIC) },
                more = { YtMusicRepository.moreSongs(it) },
            )
        }
        val record = existing ?: SavedCollection(
            id = YtMusicRepository.LIKED_MUSIC,
            title = context.getString(R.string.auto_liked),
            thumbnailUrl = listing.cover,
            playlist = true,
        )
        update(
            context,
            record,
            listing,
            approved = approved || existing == null,
            create = existing == null,
            // The download following each like is answer enough; a
            // notification for every heart would be noise.
            announce = !newestOnly,
        )
        return listing.complete
    }

    /**
     * @param create write the record even though it is not on file, for the
     *   first fill of Liked Music. Otherwise a record that has gone missing was
     *   deleted meanwhile and is left gone.
     * @param announce post a notification saying how many arrived.
     */
    private suspend fun update(
        context: Context,
        record: SavedCollection,
        listing: Listing,
        approved: Boolean,
        create: Boolean = false,
        announce: Boolean = true,
    ) {
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
        val current = Downloads.collections.value[record.id] ?: record.takeIf { create } ?: return
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
        if (announce && arrived > 0) post(context, notification, doneNotification(context, record.title, arrived))
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
        if (!AppSettings.syncDownloadedPlaylists.value && !AppSettings.downloadLikedSongs.value) {
            PlaylistSync.cancel(applicationContext)
            return Result.success()
        }
        PlaylistSync.check(
            applicationContext,
            approved = inputData.getBoolean(PlaylistSync.KEY_APPROVED, false),
            likedOnly = inputData.getBoolean(PlaylistSync.KEY_LIKED_ONLY, false),
        )
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
