package com.music.bitchord.data.sources.module

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * One in-flight-or-recent answer per key, shared by everyone who asks for it.
 *
 * Nothing below a module's index was shared or kept before this, and the
 * index is the cheap half: it is one document that changes almost never, while
 * `searchTracks` and `getTrackStreamUrl` are live calls into somebody else's
 * backend. Measured on one track, in one ninety-second window, against one
 * module — 'What Did I Miss?' was searched twice for the identical query and
 * asked twice for the identical track's stream URL. None of that is a bug in
 * any one caller. A track simply passes this way several times over — the live
 * resolve, the second look under the music, the lossless pass after a lossy
 * swap, a re-resolve when the player reopens the source — and every one of
 * those started from nothing.
 *
 * Two things are shared here, and they are not the same thing:
 *
 *  - **In flight**, always, whatever [ttlMs] says. Two callers asking the same
 *    question at the same moment is the ordinary case in this layer, because
 *    a search and a stream lookup overlap across tracks.
 *  - **Completed**, for [ttlMs]. Zero means in-flight only, which is right for
 *    an answer that depends on state this cannot see.
 *
 * ### Why the work is not started in the caller's scope
 *
 * Because this layer's callers are *designed* to give up. [ModuleSource.search]
 * launches every module in an index at once and cancels the losers;
 * [SourceResolver.bestAcross][com.music.bitchord.data.sources.SourceResolver]
 * cancels every source still running as soon as one of them answers. Started in
 * the caller's scope, each of those cancellations tore down a request that had
 * already reached the module's server — so the server did the work, answered,
 * and found nobody listening. From the far end that is indistinguishable from a
 * client hammering it and hanging up, and it was a large part of what the
 * module's operator was seeing.
 *
 * Given a [scope] that outlives any one caller, a cancelled caller drops its
 * `await` and nothing else. The request finishes and lands here, where it is
 * usually wanted again moments later anyway.
 *
 * ### What is not kept
 *
 * A failure. A module that was briefly unreachable should be asked again on the
 * next track rather than written off for the window — the same rule
 * [ModuleManager.fetchIndex] applies, for the same reason. An *empty* answer is
 * not a failure: "this catalogue does not have that recording" is a real answer,
 * and re-asking for it is exactly the waste this exists to stop.
 */
internal class SharedCalls<T>(
    private val ttlMs: Long,
    private val scope: CoroutineScope,
    /** Where a reuse is reported, so a log still shows one line per question asked. */
    private val log: (String) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * The work behind one key, and when it was started.
     *
     * Held as the running [Deferred] rather than as its result, which is what
     * lets a caller arriving mid-flight wait on it instead of starting a
     * second copy.
     */
    private class Pending<T>(val work: Deferred<Result<T>>, val startedAtMs: Long)

    private val entries = ConcurrentHashMap<String, Pending<T>>()

    /**
     * Held across the lookup *and* the insert. Two coroutines missing together
     * would otherwise each start their own call, which is the case this is
     * most needed for — and it cannot be a bare `computeIfAbsent`, because the
     * value has to be built inside a suspending scope.
     */
    private val lock = Mutex()

    suspend fun get(key: String, describe: () -> String, produce: suspend () -> Result<T>): Result<T> {
        val entry = lock.withLock {
            val live = entries[key]?.takeIf { it.work.isActive || now() - it.startedAtMs < ttlMs }
            if (live != null) {
                log(describe() + if (live.work.isActive) " — ALREADY RUNNING" else " — CACHE HIT")
                live
            } else {
                if (entries.size >= MAX_ENTRIES) prune()
                Pending(scope.async { produce() }, now()).also { entries[key] = it }
            }
        }
        return entry.work.await().also { result ->
            // By identity, so a retry that has already replaced this entry is
            // not thrown away along with the failure it replaced.
            if (result.isFailure) entries.remove(key, entry)
        }
    }

    /** Everything held, dropped — the configuration it was all about is gone. */
    fun clear() = entries.clear()

    /** For tests and diagnostics: how many answers are being held. */
    internal fun size() = entries.size

    private fun prune() {
        val at = now()
        // Never an entry still running: something is waiting on it, and
        // dropping it here would not cancel it, only hide it from the next
        // caller — who would then start a second copy of work already in
        // flight, which is the one thing this class exists to prevent.
        entries.entries.removeIf { !it.value.work.isActive && at - it.value.startedAtMs >= ttlMs }
        if (entries.size >= MAX_ENTRIES) {
            entries.entries
                .filterNot { it.value.work.isActive }
                .minByOrNull { it.value.startedAtMs }
                ?.let { entries.remove(it.key, it.value) }
        }
    }

    private companion object {
        /**
         * How many answers to hold. A queue's worth of tracks at two queries
         * and a stream call each, several times over, with room to spare — the
         * point is a bound, not a budget.
         */
        const val MAX_ENTRIES = 128
    }
}
