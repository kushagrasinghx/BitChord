package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/** Where a track is between "not on this computer" and "on it". */
sealed interface DesktopDownloadState {

    /** Accepted, waiting for the one in front of it. */
    data object Queued : DesktopDownloadState

    /** [fraction] is 0f until the length is known, which is the first thing asked for. */
    data class Running(val fraction: Float) : DesktopDownloadState

    data class Failed(val reason: String) : DesktopDownloadState
}

/** One row of the downloads manager. */
data class DesktopDownloadItem(
    val song: Song,
    val state: DesktopDownloadState,
    /** The order it was asked for in, which is the order the queue reaches it in. */
    val sequence: Long,
)

/**
 * The download queue, and what came out of it.
 *
 * Android's `Downloads` split, kept: [active] is what is happening now and lives only in memory,
 * because a transfer interrupted by the process dying did not happen; what exists on disk is the
 * files themselves, which the library scan finds on its own.
 *
 * [WORKERS] run at once, each pulling from the same queue, so one slow track does not hold up the
 * rest and a release asked for as a whole arrives in the order it was asked for.
 */
object DesktopDownloadQueue {

    /** How many transfers run at once. Android's `DownloadService` uses the same number. */
    internal const val WORKERS = 4

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val sequence = AtomicLong(0)

    /** Asked for, not yet claimed by a worker. Insertion-ordered, which is queue order. */
    private val pending = LinkedHashMap<String, DesktopDownloadItem>()

    /** Claimed by a worker; the job is attached once it exists. */
    private val running = LinkedHashMap<String, Job?>()

    private var workers = 0

    private val _active = MutableStateFlow<Map<String, DesktopDownloadItem>>(emptyMap())

    /** Everything queued, running or just failed, keyed by videoId. */
    val active: StateFlow<Map<String, DesktopDownloadItem>> = _active

    private val _finished = MutableSharedFlow<Song>(extraBufferCapacity = 64)

    /** Tracks that reached disk, for the library to record. */
    val finished: SharedFlow<Song> = _finished

    /**
     * Adds [song] to the queue, unless it is already in it.
     *
     * A second tap on a track already queued or running is a no-op rather than a second transfer:
     * the row it would produce is the same row.
     */
    fun enqueue(song: Song) {
        val id = song.videoId
        if (id.isBlank()) return
        val item = synchronized(lock) {
            if (id in pending || id in running) return
            DesktopDownloadItem(song, DesktopDownloadState.Queued, sequence.incrementAndGet())
                .also { pending[id] = it }
        }
        _active.update { it + (id to item) }
        ensureWorkers()
    }

    fun enqueueAll(songs: List<Song>) = songs.forEach(::enqueue)

    /**
     * Drops [videoId] from the queue, or stops it if it is one of the ones running.
     *
     * Dropping it from [running] is what makes this safe in the gap between a track being dequeued
     * and its job existing: a cancel landing in that window finds no job to stop, but [onRunning]
     * then finds the id it was told to run is no longer wanted and stops it on arrival.
     */
    fun cancel(videoId: String) {
        val job = synchronized(lock) {
            pending.remove(videoId)
            if (videoId !in running) return@synchronized null
            running.remove(videoId)
        }
        job?.cancel()
        DesktopDownloadManager.cancel(videoId)
        // A download called off is not something anyone needs reminding to check on, so it leaves
        // the manager rather than sitting in it as a permanent "cancelled" row.
        _active.update { it - videoId }
    }

    fun cancelAll() = _active.value.keys.toList().forEach(::cancel)

    /** Drops the finished and failed rows, leaving whatever is still going. */
    fun clearFinished() {
        _active.update { current ->
            current.filterValues { it.state !is DesktopDownloadState.Failed }
        }
    }

    /** Puts a failed track back at the end of the queue. */
    fun retry(videoId: String) {
        val song = _active.value[videoId]?.song ?: return
        _active.update { it - videoId }
        enqueue(song)
    }

    private fun ensureWorkers() {
        val toStart = synchronized(lock) {
            val wanted = minOf(WORKERS, pending.size + running.size)
            val starting = (wanted - workers).coerceAtLeast(0)
            workers += starting
            starting
        }
        repeat(toStart) { scope.launch { work() } }
    }

    private suspend fun work() {
        try {
            while (true) {
                val item = takeNext() ?: return
                val id = item.song.videoId
                onRunning(id, currentJob())
                _active.update { it + (id to item.copy(state = DesktopDownloadState.Running(0f))) }
                val result = runCatching {
                    DesktopDownloadManager.download(item.song) { downloaded, total ->
                        val fraction = if (total != null && total > 0L) {
                            (downloaded.toDouble() / total).toFloat().coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        _active.update { current ->
                            // Only while it is still the running row: a cancel removes it, and
                            // putting it back here would resurrect a row nobody asked for.
                            if (id !in current) current
                            else current + (id to item.copy(state = DesktopDownloadState.Running(fraction)))
                        }
                    }
                }
                onIdle(id)
                result.fold(
                    onSuccess = { outcome ->
                        outcome.fold(
                            onSuccess = { saved ->
                                _active.update { it - id }
                                _finished.tryEmit(saved)
                            },
                            onFailure = { failed(id, item, it) },
                        )
                    },
                    onFailure = { failed(id, item, it) },
                )
            }
        } finally {
            synchronized(lock) { workers-- }
        }
    }

    private fun failed(id: String, item: DesktopDownloadItem, cause: Throwable) {
        // A cancelled transfer already left the manager; it is not a failure to report.
        if (cause is kotlinx.coroutines.CancellationException) return
        if (id !in _active.value) return
        val reason = cause.message?.takeIf(String::isNotBlank) ?: "The download did not finish"
        _active.update { it + (id to item.copy(state = DesktopDownloadState.Failed(reason))) }
        DesktopTrackLog.log("download failed for '${item.song.title}' — $reason")
    }

    /**
     * The next track to fetch, or null when the queue is empty.
     *
     * Claims it as running under the same lock that removed it, so there is no instant where a
     * track is in neither the queue nor the running slot and a [cancel] for it would quietly do
     * nothing.
     */
    private fun takeNext(): DesktopDownloadItem? = synchronized(lock) {
        val entry = pending.entries.firstOrNull() ?: return null
        pending.remove(entry.key)
        running[entry.key] = null
        entry.value
    }

    /** Attaches the job fetching [videoId], unless it has been cancelled meanwhile. */
    private fun onRunning(videoId: String, job: Job?) {
        val cancelled = synchronized(lock) {
            if (videoId !in running) return@synchronized true
            running[videoId] = job
            false
        }
        if (cancelled) job?.cancel()
    }

    private fun onIdle(videoId: String) {
        synchronized(lock) { running.remove(videoId) }
    }

    private suspend fun currentJob(): Job? = kotlin.coroutines.coroutineContext[Job]
}
