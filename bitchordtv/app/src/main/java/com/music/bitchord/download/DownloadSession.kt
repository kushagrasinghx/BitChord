package com.music.bitchord.download

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/** Where one track in the manager's list has got to. */
sealed interface DownloadProgress {

    data object Queued : DownloadProgress

    /** [fraction] is 0f until the length is known, which is one request in. */
    data class Running(val fraction: Float) : DownloadProgress

    data object Done : DownloadProgress

    data class Failed(val reason: String) : DownloadProgress

    /** Neither waiting nor coming back — nothing left for the user to watch. */
    val settled: Boolean get() = this is Done || this is Failed
}

/**
 * Everything downloaded since the app was opened, and whether the user has
 * looked at it yet.
 *
 * The gap this fills is between [Downloads.active] and [Downloads.saved].
 * `active` is only ever what is happening *right now* — a track leaves it the
 * instant it finishes, which is exactly the instant the user most wants to know
 * about it — and `saved` is a map of every file this app has ever written, with
 * nothing to distinguish the album from twenty minutes ago from one from last
 * March. Neither can answer "did the thing I asked for actually all arrive?",
 * and that is the only question a download manager exists to answer.
 *
 * Deliberately not persisted. This is a record of one sitting: the question it
 * answers is about a batch the user remembers starting, and after a restart
 * there is no such batch — what is on disk is then the whole truth and the
 * Downloads page is where to read it. Being a Kotlin object, the list dies with
 * the process, which is precisely the intended lifetime and needs no code.
 *
 * ### Why [State.visible] is not just "something is downloading"
 *
 * A download is the one thing in this app a user starts and walks away from, so
 * the moment it finishes is a moment nobody is watching. An indicator that
 * disappears the instant the last track lands is therefore an indicator that,
 * for the person who put their phone in their pocket, was never there at all —
 * they come back to a finished job with no trace of it and no way to tell a
 * completed album from one that failed on track nine.
 *
 * So it stays up until it has been *seen*, and "seen" is compared against when
 * the queue last went quiet rather than being a flag that is set once. That
 * comparison is what makes a visit during the download count for what it was:
 * checking in on it, not signing off on it. Look in halfway, close the sheet,
 * and the last track landing afterwards puts the indicator back — because the
 * thing being confirmed is the *outcome*, and the outcome hadn't happened yet.
 */
object DownloadSession {

    data class Item(
        /** The id the *tap* used, which is what everything else here is keyed by. */
        val videoId: String,
        val song: Song,
        val progress: DownloadProgress,
        /** What release this was part of, when it was part of one. */
        val from: String? = null,
        /** Ask order, so the list reads the way the queue drains. */
        val sequence: Long,
    )

    data class State(
        val items: List<Item> = emptyList(),
        /** When the user last had the manager open, on [tick]'s clock. */
        val seenAt: Long = 0L,
        /** When the last thing in the queue stopped moving, on [tick]'s clock. */
        val settledAt: Long = 0L,
    ) {
        val waiting: Int get() = items.count { !it.progress.settled }
        val finished: Int get() = items.count { it.progress is DownloadProgress.Done }
        val failed: Int get() = items.count { it.progress is DownloadProgress.Failed }

        /** Whether anything is still queued or running. */
        val busy: Boolean get() = waiting > 0

        /**
         * How far through the whole batch this is, counting a settled track as
         * a whole one whichever way it settled — a failure is not progress, but
         * it is finished, and a bar that can never fill because one track died
         * reads as a download that is still going.
         */
        val fraction: Float
            get() {
                if (items.isEmpty()) return 0f
                val total = items.sumOf { item ->
                    when (val progress = item.progress) {
                        is DownloadProgress.Running -> progress.fraction.toDouble()
                        DownloadProgress.Queued -> 0.0
                        else -> 1.0
                    }
                }
                return (total / items.size).toFloat().coerceIn(0f, 1f)
            }

        /** See the class comment: not "is downloading" but "is unaccounted for". */
        val visible: Boolean get() = items.isNotEmpty() && (busy || seenAt < settledAt)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * A logical clock, shared by the ask order and the two timestamps.
     *
     * Not a wall clock, and not [System.nanoTime] either. These values are only
     * ever compared with each other, and both real clocks can produce a
     * comparison that lies: the wall clock can be set backwards by the user or
     * by an NTP correction, and `nanoTime`'s origin is arbitrary and may be
     * negative — which would put a first completion *before* the zero that means
     * "never seen" and leave the indicator hidden for the one batch it most
     * needed to report. A counter starting at zero has neither problem.
     *
     * Atomic because there is no single-writer discipline left to lean on:
     * several downloads run at once now, so several threads are in [update] at
     * the same time and [update] retries its block on contention — which means
     * this can be called more than once for one logical event. Skipped values
     * cost nothing, since every one of these is only ever compared with another
     * for order.
     */
    private val clock = AtomicLong(0L)

    private fun tick(): Long = clock.incrementAndGet()

    /**
     * A track has been accepted into the queue — or refused before it got there,
     * which is still something the user asked for and is owed an answer about.
     *
     * Re-asking for a track already in the list resets it rather than adding a
     * second row: a failed download retried from the manager is the same errand,
     * and two rows for one song would leave the failure on screen next to its
     * own retry.
     */
    fun queued(song: Song, from: String? = null) {
        update { state ->
            val existing = state.items.indexOfFirst { it.videoId == song.videoId }
            val item = Item(
                videoId = song.videoId,
                song = song,
                progress = DownloadProgress.Queued,
                from = from ?: state.items.getOrNull(existing)?.from,
                sequence = if (existing >= 0) state.items[existing].sequence else tick(),
            )
            val items = if (existing >= 0) {
                state.items.toMutableList().also { it[existing] = item }
            } else {
                state.items + item
            }
            // Not settledAt: nothing has settled. But a new ask does mean the
            // batch the user last signed off on is no longer the batch in hand.
            state.copy(items = items)
        }
    }

    fun running(videoId: String, fraction: Float) =
        set(videoId, DownloadProgress.Running(fraction))

    fun done(videoId: String) = set(videoId, DownloadProgress.Done)

    fun failed(videoId: String, reason: String) = set(videoId, DownloadProgress.Failed(reason))

    /**
     * Swap in the track that is actually being fetched.
     *
     * A music-video row is replaced by the catalogue track behind it on the way
     * down (see [Downloads.run]), and that changes the title and the cover but
     * not the id this list is keyed by — so this is a correction to a row, not a
     * new one.
     */
    fun retitle(videoId: String, song: Song) {
        update { state ->
            val index = state.items.indexOfFirst { it.videoId == videoId }
            if (index < 0) return@update state
            state.copy(
                items = state.items.toMutableList().also {
                    it[index] = it[index].copy(song = song)
                },
            )
        }
    }

    /** Drop a row entirely — a download the user called off. */
    fun forget(videoId: String) {
        update { state ->
            val items = state.items.filterNot { it.videoId == videoId }
            if (items.size == state.items.size) return@update state
            // Removing the last unsettled row is the queue going quiet, and it
            // has to count as such or the indicator would linger over a list
            // that no longer has anything in it to report.
            state.copy(
                items = items,
                settledAt = if (items.none { !it.progress.settled }) tick() else state.settledAt,
            )
        }
    }

    /** The user has the manager open — see [State.visible]. */
    fun markSeen() {
        _state.update { it.copy(seenAt = tick()) }
    }

    /** Empty the list outright, at the user's request. */
    fun clear() {
        _state.value = State()
    }

    private fun set(videoId: String, progress: DownloadProgress) {
        update { state ->
            val index = state.items.indexOfFirst { it.videoId == videoId }
            if (index < 0) return@update state
            val items = state.items.toMutableList().also {
                it[index] = it[index].copy(progress = progress)
            }
            // Only the *last* one settling closes the batch off. Marking each
            // one would have a forty-track album go quiet thirty-nine times,
            // and the indicator is meant to report on the batch rather than on
            // whichever track happened to finish while nobody was looking.
            val quiet = items.none { !it.progress.settled }
            state.copy(
                items = items,
                settledAt = if (progress.settled && quiet) tick() else state.settledAt,
            )
        }
    }

    /**
     * Read-modify-write on [_state], atomically.
     *
     * A plain `_state.value = block(_state.value)` was safe while one download
     * ran at a time. With several in flight it is a lost-update race between
     * four workers each posting progress a few times a second, and what gets
     * lost is whole rows: two tracks reporting at once, and one of them stays
     * on screen at whatever it last managed to write. [MutableStateFlow.update]
     * is the compare-and-set version of the same line.
     */
    private inline fun update(block: (State) -> State) {
        _state.update(block)
    }
}
