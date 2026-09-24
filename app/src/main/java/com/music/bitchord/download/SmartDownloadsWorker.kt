package com.music.bitchord.download

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * The pass that keeps a listener's library offline without being asked.
 *
 * It runs once a day (and once straight after the switch is turned on), on an
 * unmetered network, and does three things in order: work through what was
 * liked, work through library playlists if that's switched on, then drop the
 * oldest automatic downloads past the keep limit. Every step is bounded — a
 * per-day quota, a cap on how many playlists one run walks, and a page cap
 * inside each of them — because none of them has a user waiting on screen to
 * tell it when it has done enough.
 *
 * The whole feature is off unless [AppSettings.smartDownloads] says otherwise,
 * and the switch is the first thing checked: WorkManager keeps the request
 * registered while it's off (so it can be resumed cheaply), which means this
 * class is expected to be entered and left again having done nothing.
 */
internal class SmartDownloadsWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!AppSettings.smartDownloads.value) return Result.success()
        // The constraints already ask for a network this feature may spend, so
        // this only fires in the narrow gap where the metered flag changed
        // underneath a run that was already scheduled. Retrying is better than
        // queueing tracks the download path would then refuse one by one. It
        // asks *its own* switch rather than the one guarding a manual tap:
        // the whole point of having two is that they are allowed to disagree.
        if (!AppSettings.downloadsAllowedForBackground) {
            // The one silent way this pass can decline to happen, and the one
            // most likely to be mistaken for a bug: WorkManager's own network
            // constraint stops the worker from starting at all, so this is the
            // only place a refusal can say so out loud.
            Log.i(TAG, "waiting for a connection downloads are allowed on; will retry")
            return Result.retry()
        }
        return runCatching { harvest(applicationContext) }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    /**
     * One quota's worth of the library, then whatever the keep limit wants
     * dropped.
     *
     * Everything mutable lives in this method rather than in the class so the
     * ledger is written exactly once, at the end, with a single consistent
     * view of what the run decided. A crash mid-run costs the day's remaining
     * quota at worst — the tracks already handed to [Downloads.enqueue] are
     * there, and the ones not yet decided are simply picked up next time.
     */
    private suspend fun harvest(context: Context) {
        SmartDownloads.runLock.withLock {
            val ledger = SmartDownloads.read()
            val today = LocalDate.now().toString()

            var used = ledger.usedOn(today)
            var quota = ledger.remainingQuota(today, AppSettings.smartDownloadsDailyLimit.value)
            var cursor = ledger.playlistCursor

            // A copy, so the failure sweep below and every offer afterwards
            // mutate one map and the original ledger stays intact to diff
            // against if the run is interrupted.
            val decided = LinkedHashMap(ledger.decided)

            // A track that failed outright is put back on the list. Its
            // failure row stays visible in the Downloads sheet for the
            // listener to see and dismiss, but if nothing undid the decision
            // here it would never be tried again, and nothing else in the app
            // has any reason to ask for a liked track it never played.
            // An id that was deleted instead holds no failure any more — it
            // was dismissed, or removed after landing — so it stays decided
            // and is not fetched back.
            val active = Downloads.active.value
            val saved = Downloads.saved.value.keys
            val usedAtStart = used
            val entries = decided.entries.iterator()
            while (entries.hasNext()) {
                val entry = entries.next()
                if (entry.key !in saved && active[entry.key] is DownloadState.Failed) {
                    entries.remove()
                }
            }

            /**
             * Offers one candidate. Returns false once the day's quota is
             * spent, which is the only signal the scan loops need to stop.
             */
            fun offer(song: Song): Boolean {
                if (quota <= 0) return false
                if (!isNewCandidate(song.videoId, decided.keys, saved)) return quota > 0
                Downloads.enqueue(context, song, SmartDownloads.FROM)
                decided[song.videoId] = System.currentTimeMillis()
                quota--
                used++
                return quota > 0
            }

            // Liked Music first and in the order the library lists it, so the
            // tracks at the top of what the listener sees are the first ones
            // to go offline. Already having a track — downloaded by hand or by
            // an earlier run — is what advances the position here rather than
            // any remembered offset, which means a library that is already
            // fully offline resolves to "just the new ones" without this
            // feature needing to know what changed.
            if (quota > 0) {
                val liked = YtMusicRepository.allSongs(YtMusicRepository.LIKED_MUSIC)
                    .getOrNull()
                    .orEmpty()
                for (song in liked) {
                    if (!offer(song)) break
                }
            }

            if (quota > 0 && AppSettings.smartDownloadsPlaylists.value) {
                cursor = harvestPlaylists(cursor) { offer(it) }
            }

            SmartDownloads.write(
                ledger.copy(
                    decided = decided,
                    day = today,
                    used = used,
                    playlistCursor = cursor,
                ),
            )

            SmartDownloads.trim(context)

            // One line per run, and the only way anybody watching logcat can
            // tell the pass ran at all — WorkManager logs nothing about a
            // worker that returned success, and a run that found nothing new
            // is indistinguishable from a run that never happened without it.
            Log.i(
                TAG,
                "queued ${used - usedAtStart} track(s); " +
                    "${(AppSettings.smartDownloadsDailyLimit.value - used).coerceAtLeast(0)} " +
                    "of today's quota left; ${decided.size} ever asked for, " +
                    "${Downloads.saved.value.keys.count { it in decided }} of them on disk",
            )
        }
    }

    /**
     * Walks library playlists from where the last run stopped, taking only
     * what fits in what's left of the quota.
     *
     * The cursor is what keeps this affordable: a run with a full quota stops
     * at the first playlist that has something new and remembers the one it
     * stopped *before*, so the next run carries on there instead of paying for
     * the same pages again. A run that finds nothing anywhere comes all the
     * way round and lands back where it started, which is the correct answer
     * when the whole library is already offline.
     *
     * @return the cursor to persist.
     */
    private suspend fun harvestPlaylists(
        start: Int,
        offer: (Song) -> Boolean,
    ): Int {
        val playlists = YtMusicRepository.userPlaylists().getOrNull().orEmpty()
        if (playlists.isEmpty()) return 0

        var cursor = start.coerceIn(0, playlists.lastIndex)
        var visited = 0
        while (visited < minOf(SCAN_PLAYLISTS_PER_RUN, playlists.size)) {
            val playlist = playlists[cursor]
            cursor = (cursor + 1) % playlists.size
            visited++
            if (!scanPlaylist(playlist.browseId, offer)) return cursor
        }
        return cursor
    }

    /**
     * One playlist's tracks, following pagination only while something is
     * still wanted from it.
     *
     * @return false when the quota ran out mid-list.
     */
    private suspend fun scanPlaylist(
        browseId: String,
        offer: (Song) -> Boolean,
    ): Boolean {
        var page = YtMusicRepository.browseSongs(browseId).getOrNull() ?: return true
        for (attempt in 0 until MAX_SCAN_PAGES) {
            for (song in page.songs) {
                if (!offer(song)) return false
            }
            val token = page.continuation ?: return true
            page = YtMusicRepository.moreSongs(token).getOrNull() ?: return true
        }
        return true
    }

    private companion object {
        /** Same tag as everything else that talks in logcat. */
        const val TAG = "BitChord"

        /** Playlists one run walks before leaving the rest to a later run. */
        const val SCAN_PLAYLISTS_PER_RUN = 6

        /** Pages read from any single playlist in one run. */
        const val MAX_SCAN_PAGES = 3
    }
}
