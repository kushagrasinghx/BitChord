package com.music.bitchord.data.innertube

import android.content.Context
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Registers plays against the signed-in account's YouTube Music history, so
 * recommendations on the home tab reflect what's actually been listened to.
 *
 * Three pings make one play, which is the sequence a real client performs:
 *  - `videostatsPlaybackUrl` the moment a track becomes audible. This creates the history entry.
 *  - `atrUrl` a few seconds in, which separates a play that started from a play that happened.
 *  - `videostatsWatchtimeUrl` as it plays on, and once more when it ends.
 *
 * Offline Support:
 * Plays heard while offline (downloads or local songs) are queued in persistent disk storage
 * (`offline_plays_queue.json`) and synced automatically to YouTube Music when network connectivity
 * returns, ensuring artist play counts and recommendation algorithms stay in sync.
 */
object PlaybackTracker {

    private const val TAG = "BitChord"
    private const val REPORT_INTERVAL_SECONDS = 30L
    private const val OPEN_ATTEMPTS = 3
    private const val OPEN_RETRY_DELAY_MS = 2_000L
    private const val QUEUE_FILE = "offline_plays_queue.json"

    val VIDEO_ID = Regex("""[A-Za-z0-9_-]{11}""")

    fun isEligibleVideoId(id: String): Boolean = VIDEO_ID.matches(id)

    data class OfflinePlayRecord(
        val videoId: String?,
        val title: String,
        val artist: String,
        val timestampMs: Long,
        val durationSeconds: Long = 60L,
    )

    private class Session(
        val videoId: String,
        val cpn: String,
        val tracking: Innertube.PlaybackTracking,
    ) {
        var reportedSeconds = 0L
        var flushingTo = 0L
        var atrSent = false
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _registeredPlays = MutableStateFlow(0)
    val registeredPlays: StateFlow<Int> = _registeredPlays.asStateFlow()

    private val lock = Mutex()
    private val queueFileLock = Mutex()

    @Volatile
    private var session: Session? = null

    @Volatile
    private var opening: String? = null

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var isSyncingOffline = false

    /**
     * Initialize tracker context and register network reconnection listener
     * to flush queued offline plays.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        scope.launch {
            AppSettings.meteredConnection.collect { connection ->
                if (connection != null && Innertube.cookie != null) {
                    syncOfflinePlays()
                }
            }
        }
    }

    /**
     * Call when a track becomes audible — both on play/resume and when the
     * queue moves on. If offline or if ping fails, the play is queued for sync upon reconnect.
     */
    fun onPlaying(
        videoId: String,
        title: String? = null,
        artist: String? = null,
        durationSeconds: Long = 60L,
    ) {
        val isVideo = VIDEO_ID.matches(videoId)
        if (!isVideo && (title.isNullOrBlank() || artist.isNullOrBlank())) return
        if (Innertube.cookie == null) return

        // If offline, queue play to sync when network returns
        if (AppSettings.meteredConnection.value == null) {
            queueOfflinePlay(
                videoId = if (isVideo) videoId else null,
                title = title.orEmpty(),
                artist = artist.orEmpty(),
                durationSeconds = durationSeconds,
            )
            return
        }

        if (!isVideo) {
            // Local track played while online — resolve matched videoId if needed
            scope.launch {
                val row = runCatching {
                    YtMusicRepository.search("$artist $title", SearchFilter.SONGS).getOrNull()?.firstOrNull()
                }.getOrNull()
                val matched = when (row) {
                    is SearchResult.TopTrack -> row.song.videoId
                    is SearchResult.Track -> row.song.videoId
                    else -> null
                }
                if (matched != null && isEligibleVideoId(matched)) {
                    onPlaying(matched, title, artist, durationSeconds)
                } else {
                    queueOfflinePlay(null, title.orEmpty(), artist.orEmpty(), durationSeconds)
                }
            }
            return
        }

        if (session?.videoId == videoId || opening == videoId) return
        opening = videoId
        scope.launch(TrackLog.about(videoId)) {
            try {
                val success = openWithRetries(videoId)
                if (!success) {
                    queueOfflinePlay(videoId, title.orEmpty(), artist.orEmpty(), durationSeconds)
                }
            } finally {
                if (opening == videoId) opening = null
            }
        }
    }

    private fun readQueueLocked(ctx: Context): MutableList<OfflinePlayRecord> {
        val file = File(ctx.filesDir, QUEUE_FILE)
        val list = mutableListOf<OfflinePlayRecord>()
        if (!file.exists()) return list
        val raw = file.readText(Charsets.UTF_8).trim()
        if (raw.isBlank() || raw == "[]") return list
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                OfflinePlayRecord(
                    videoId = obj.optString("v").takeIf { it.isNotBlank() },
                    title = obj.optString("t"),
                    artist = obj.optString("a"),
                    timestampMs = obj.optLong("ts"),
                    durationSeconds = obj.optLong("d", 60L),
                )
            )
        }
        return list
    }

    private fun writeQueueLocked(ctx: Context, list: List<OfflinePlayRecord>) {
        val file = File(ctx.filesDir, QUEUE_FILE)
        val newArr = JSONArray()
        for (rec in list) {
            val obj = JSONObject()
            rec.videoId?.let { obj.put("v", it) }
            obj.put("t", rec.title)
            obj.put("a", rec.artist)
            obj.put("ts", rec.timestampMs)
            obj.put("d", rec.durationSeconds)
            newArr.put(obj)
        }
        val tmp = File(ctx.filesDir, "$QUEUE_FILE.tmp")
        tmp.writeText(newArr.toString(), Charsets.UTF_8)
        tmp.renameTo(file)
    }

    /**
     * Queue a play that occurred while offline or failed due to network refusal.
     */
    fun queueOfflinePlay(
        videoId: String?,
        title: String,
        artist: String,
        durationSeconds: Long = 60L,
    ) {
        val ctx = appContext ?: return
        scope.launch(Dispatchers.IO) {
            queueFileLock.withLock {
                runCatching {
                    val list = readQueueLocked(ctx)
                    val now = System.currentTimeMillis()
                    val isRecentDuplicate = list.any {
                        (it.videoId != null && it.videoId == videoId || (it.title == title && it.artist == artist)) &&
                            now - it.timestampMs < 30_000L
                    }
                    if (!isRecentDuplicate && (videoId != null || (title.isNotBlank() && artist.isNotBlank()))) {
                        list.add(OfflinePlayRecord(videoId, title, artist, now, durationSeconds))
                    }
                    val trimmed = if (list.size > 100) list.takeLast(100) else list
                    writeQueueLocked(ctx, trimmed)
                    TrackLog.d(TAG, "Queued offline play for ${videoId ?: "$artist - $title"} (queue size: ${trimmed.size})")
                }
            }
        }
    }

    /**
     * Flush and sync all queued offline plays to YouTube Music once online.
     */
    suspend fun syncOfflinePlays() = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext
        if (Innertube.cookie == null || AppSettings.meteredConnection.value == null) return@withContext
        if (isSyncingOffline) return@withContext
        isSyncingOffline = true
        try {
            val pending = queueFileLock.withLock { readQueueLocked(ctx) }
            if (pending.isEmpty()) return@withContext

            var syncedCount = 0

            for (record in pending) {
                if (AppSettings.meteredConnection.value == null) break

                var vid = record.videoId
                if (vid == null || !isEligibleVideoId(vid)) {
                    if (record.title.isNotBlank() && record.artist.isNotBlank()) {
                        val row = runCatching {
                            YtMusicRepository.search("${record.artist} ${record.title}", SearchFilter.SONGS).getOrNull()?.firstOrNull()
                        }.getOrNull()
                        vid = when (row) {
                            is SearchResult.TopTrack -> row.song.videoId
                            is SearchResult.Track -> row.song.videoId
                            else -> null
                        }
                    }
                }

                if (vid != null && isEligibleVideoId(vid)) {
                    val success = runCatching {
                        val sig = StreamResolver.signatureTimestamp(vid) ?: return@runCatching false
                        val tracking = Innertube.playbackTracking(vid, sig) ?: return@runCatching false
                        val cpn = Innertube.newCpn()
                        val status = Innertube.pingPlayback(tracking.playbackUrl, cpn)
                        tracking.atrUrl?.let { Innertube.pingAtr(it, cpn) }
                        tracking.watchtimeUrl?.let {
                            Innertube.pingWatchtime(it, cpn, minOf(record.durationSeconds, 180L), final = true)
                        }
                        TrackLog.d(TAG, "Successfully synced offline play for $vid (HTTP $status)")
                        true
                    }.getOrDefault(false)

                    if (success) {
                        syncedCount++
                        queueFileLock.withLock {
                            val current = readQueueLocked(ctx)
                            current.removeAll {
                                it.timestampMs == record.timestampMs &&
                                    (it.videoId == record.videoId || (it.title == record.title && it.artist == record.artist))
                            }
                            writeQueueLocked(ctx, current)
                        }
                        delay(600)
                    }
                } else {
                    if (System.currentTimeMillis() - record.timestampMs >= 7 * 86_400_000L) {
                        queueFileLock.withLock {
                            val current = readQueueLocked(ctx)
                            current.removeAll {
                                it.timestampMs == record.timestampMs &&
                                    it.title == record.title &&
                                    it.artist == record.artist
                            }
                            writeQueueLocked(ctx, current)
                        }
                    }
                }
            }

            if (syncedCount > 0) {
                _registeredPlays.value += syncedCount
                TrackLog.d(TAG, "Synced $syncedCount offline plays to YouTube Music history")
            }
        } catch (e: Throwable) {
            TrackLog.w(TAG, "Failed syncing offline plays: ${e.message}")
        } finally {
            isSyncingOffline = false
        }
    }

    /**
     * Call when the queue moves to a different track. The previous session's
     * watched time is flushed before it is dropped.
     */
    fun onTrackChanged(positionSeconds: Long) {
        val closing = session ?: return
        session = null
        scope.launch(TrackLog.about(closing.videoId)) {
            runCatching { flush(closing, positionSeconds, final = true) }
                .onFailure {
                    TrackLog.w(TAG, "final watchtime ping failed for ${closing.videoId}: ${it.message}")
                }
        }
    }

    /**
     * Periodic progress report for the current track, in seconds played.
     */
    fun onProgress(videoId: String, positionSeconds: Long) {
        val current = session ?: return
        if (current.videoId != videoId) return
        if (!current.atrSent && positionSeconds >= current.tracking.atrAfterSeconds) {
            current.atrSent = true
            val atrUrl = current.tracking.atrUrl
            if (atrUrl != null) {
                scope.launch(TrackLog.about(videoId)) {
                    runCatching { Innertube.pingAtr(atrUrl, current.cpn) }
                        .onFailure { TrackLog.w(TAG, "atr ping failed for $videoId: ${it.message}") }
                }
            }
        }
        if (positionSeconds - maxOf(current.reportedSeconds, current.flushingTo) < REPORT_INTERVAL_SECONDS) return
        scope.launch(TrackLog.about(videoId)) {
            runCatching { flush(current, positionSeconds) }
                .onFailure { TrackLog.w(TAG, "watchtime ping failed for $videoId: ${it.message}") }
        }
    }

    /**
     * Close out the current play for good when queue finishes or service terminates.
     */
    fun onPlaybackFinished(positionSeconds: Long) {
        val closing = session ?: return
        session = null
        scope.launch(TrackLog.about(closing.videoId)) {
            withContext(NonCancellable) {
                runCatching { flush(closing, positionSeconds, final = true) }
                    .onFailure {
                        TrackLog.w(TAG, "closing watchtime ping failed for ${closing.videoId}: ${it.message}")
                    }
            }
        }
    }

    private suspend fun openWithRetries(videoId: String): Boolean {
        repeat(OPEN_ATTEMPTS) { attempt ->
            if (opening != videoId) return false
            val settled = try {
                open(videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                TrackLog.w(
                    TAG,
                    "history registration failed for $videoId " +
                        "(attempt ${attempt + 1}/$OPEN_ATTEMPTS): ${e.message}",
                )
                false
            }
            if (settled) return true
            if (attempt < OPEN_ATTEMPTS - 1) delay(OPEN_RETRY_DELAY_MS)
        }
        return false
    }

    private suspend fun open(videoId: String): Boolean = lock.withLock {
        val signatureTimestamp = StreamResolver.signatureTimestamp(videoId)
        if (signatureTimestamp == null) {
            TrackLog.w(TAG, "no signature timestamp yet; retrying history for $videoId")
            return@withLock false
        }
        val tracking = Innertube.playbackTracking(videoId, signatureTimestamp)
        if (tracking == null) {
            TrackLog.d(TAG, "no playback tracking for $videoId (guest, or the player declined)")
            return@withLock true
        }
        val fresh = Session(videoId, Innertube.newCpn(), tracking)
        val status = Innertube.pingPlayback(tracking.playbackUrl, fresh.cpn)
        session = fresh
        _registeredPlays.value++
        TrackLog.d(TAG, "history entry created for $videoId (HTTP $status)")
        true
    }

    private suspend fun flush(target: Session, positionSeconds: Long, final: Boolean = false) {
        val url = target.tracking.watchtimeUrl ?: return
        if (!final && positionSeconds <= target.reportedSeconds) return
        target.flushingTo = maxOf(target.flushingTo, positionSeconds)
        lock.withLock {
            val status = Innertube.pingWatchtime(url, target.cpn, positionSeconds, final)
            target.reportedSeconds = maxOf(target.reportedSeconds, positionSeconds)
            TrackLog.d(
                TAG,
                "watchtime ${positionSeconds}s reported for ${target.videoId}" +
                    "${if (final) " (final)" else ""} (HTTP $status)",
            )
        }
    }
}
