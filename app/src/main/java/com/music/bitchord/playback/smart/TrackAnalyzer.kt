/*
 * Modeled on Orchard's own TrackAnalyzer (https://github.com/SFG5453/Orchard).
 * Phase 1 was the DSP-only pass (native/analyzer/audio_analysis.cpp); Phase 2
 * adds the Beat This! ONNX model (see [BeatTracker]) and Phase 3 the
 * open-unmix vocal mask (see [VocalTracker]), both over the head and tail of
 * the track, which is the only part a transition ever reads.
 *
 * Copyright (C) 2026 Kushagra Singh
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.music.bitchord.playback.smart

import android.content.Context
import android.media.MediaDataSource
import android.net.Uri
import com.music.bitchord.data.TrackLog
import android.os.Process
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AutomixPerformanceMode
import com.music.bitchord.data.sources.SourceResolver
import com.music.bitchord.data.sources.TrackMatcher
import com.music.bitchord.playback.AudioCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import java.util.Locale

/**
 * Produces [TrackAnalysis] for tracks that are about to be mixed, and hands it
 * to [TransitionPlanner].
 *
 * [analysisFor] is called from the crossfade watcher every tick, so it never
 * blocks or computes: it returns what is already known, and an unanalysed
 * track simply reads as no evidence, which the policy ladder answers with a
 * plain fade.
 */
@UnstableApi
class TrackAnalyzer(private val context: Context, private val cache: AudioCache) {

    /**
     * Dual pinned lanes. The high lane serves the track queued to play next,
     * the low lane everything else — and refine head passes ride whichever
     * lane their track's priority selects, so track1 and track2 (heads
     * included) genuinely overlap.
     *
     * Each lane owns its own model sessions. The ORT sessions assume
     * single-thread confinement, so two workers sharing one session would
     * race inference against release; per-lane triples remove that by
     * construction, with no lock ordering and no close-while-inferring
     * window. The global [running] set stays the cross-lane same-track
     * mutex: a track never runs on both lanes at once, so a head pass can
     * never overwrite a whole-track result.
     */
    private inner class Lane(
        val laneName: String,
        val tracker: BeatTracker,
        val vocals: VocalTracker,
        val pitch: PitchTracker,
    ) {
        val inFlight = AtomicInteger(0)
        fun releaseModels() {
            tracker.release()
            vocals.release()
            pitch.release()
        }
    }

    private val highLane = Lane("high", BeatTracker(context), VocalTracker(context), PitchTracker(context))
    private val lowLane = Lane("low", BeatTracker(context), VocalTracker(context), PitchTracker(context))

    /** Lane executing the current job; set by [AnalysisJob.run]. */
    private val currentLane = ThreadLocal<Lane>()

    /**
     * Resolved on first use, not at construction, for the reason [AnalysisStore]
     * documents about its own directory: this class is a field initializer on
     * the playback service, which runs before the service has a base context
     * attached, and asking a context for anything there returns null.
     */
    private val resolver by lazy { context.contentResolver }

    private val results = ConcurrentHashMap<String, TrackAnalysis>()
    private val running = ConcurrentHashMap.newKeySet<String>()
    private val sourceAnalysisUris = ConcurrentHashMap<String, Uri>()
    private val sourceResolutions = ConcurrentHashMap.newKeySet<String>()
    private val sourceResolutionAttempted = ConcurrentHashMap.newKeySet<String>()
    private val sourceResolutionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Tracks whose result came from [analyzeHead] and is waiting to be superseded. */
    private val provisional = ConcurrentHashMap.newKeySet<String>()

    /**
     * Cached prefix size, in bytes, at the last head attempt on each
     * *rendition*. See [headWorthTrying].
     *
     * Keyed by rendition rather than by track because the growth guard is
     * asking "have I already decoded roughly this much of this copy", and a
     * track can move between copies: a first attempt on a half-cached lossless
     * rendition recorded six megabytes, and a much lighter Opus head arriving
     * afterwards — the one that would actually have produced a result — was then
     * refused for being smaller than a number belonging to a different file.
     */
    private val headAttempts = ConcurrentHashMap<String, Long>()

    /**
     * Track-and-rendition pairs that have already reported waiting for a head,
     * so the tick doesn't spam. Not keyed by track alone: a track that gains a
     * second copy of itself is in a genuinely new situation, and the first
     * version of this hid exactly that.
     */
    private val headSkipLogged = ConcurrentHashMap.newKeySet<String>()

    /**
     * How many times each track's whole-track pass has been refused for
     * decoding short. Counted rather than flagged so a rendition still filling
     * in its holes gets a few more chances, while one that is genuinely
     * truncated stops being re-decoded on every tick.
     */
    private val shortDecodes = ConcurrentHashMap<String, Int>()

    /**
     * P0 terminal-analysis guarantee: bounded transient-failure counters so a
     * track that trips on a still-filling stream, a revoked content URI, or a
     * codec hiccup is retried a few times instead of written off on the first
     * stumble — while a track that genuinely cannot be read still terminates
     * instead of opening a data source on every tick forever.
     */
    private val throwStrikes = ConcurrentHashMap<String, Int>()
    private val openFailStrikes = ConcurrentHashMap<String, Int>()
    private val noDurationStrikes = ConcurrentHashMap<String, Int>()

    /** Local URIs already granted their one fresh-open retry (see [analyze]). */
    private val localReopenTried = ConcurrentHashMap.newKeySet<String>()

    /** When the in-flight job for each track was queued; see the stuck watchdog in [request]. */
    private val jobStartMs = ConcurrentHashMap<String, Long>()

    /** Tracks already reported stuck, so the watchdog logs once not every tick. */
    private val stuckLogged = ConcurrentHashMap.newKeySet<String>()

    /** Tracks already looked for on disk this session; see [restoreOnce]. */
    private val restoreAttempted = ConcurrentHashMap.newKeySet<String>()

    /** Results that survive the process, so a track is measured once and stays measured. */
    private val store = AnalysisStore(context)

    /**
     * Cache keys of renditions that decoded short despite the cache calling them
     * complete. Keyed by rendition rather than by track, because the point is to
     * send the next attempt at the *same* track to a different copy of it.
     */
    private val badRenditions = ConcurrentHashMap.newKeySet<String>()

    /**
     * How many times each rendition has been refused for a duration skew, so a
     * legitimately skewed copy is accepted provisionally instead of starving
     * the track forever. Keyed by rendition like [badRenditions], because the
     * point is per-copy patience, not per-track. Cleared on a clean duration
     * pass and on discard-refetch (different bytes start level); kept while
     * its copy stays suspect, so re-analysis of the same skewed bytes stays
     * flagged instead of flapping.
     */
    private val renditionRejects = ConcurrentHashMap<String, Int>()

    /**
     * Cache keys already put through the whole-track pass, whatever came of it.
     *
     * What reopens a track that was written off: a copy of it nobody has read
     * yet. Without this the write-off is final for the session, and a rendition
     * that arrives seconds later — a quality upgrade, or the head fetch the
     * analyzer itself asked for — is never looked at. Measured, a track was
     * given up on at 22:09:24 and its `#hifi` copy finished downloading at
     * 22:09:41.
     */
    private val triedRenditions = ConcurrentHashMap.newKeySet<String>()

    /**
     * How many times each rendition has been thrown off disk for being
     * undecodable, so a clean slate stays a one-off.
     *
     * The refetch that follows a discard is not guaranteed to be any better —
     * a source can serve the same broken file twice — and without a bound the
     * two halves feed each other: refuse, delete, refetch, refuse, delete, on a
     * 250ms tick, spending the listener's data in a loop. One clean-slate retry
     * is enough for the case this exists for, which is an entry spliced from two
     * different encodings and unrecoverable only because nothing would ever
     * overwrite it.
     */
    private val discarded = ConcurrentHashMap<String, Int>()

    private fun discardsOf(key: String): Int = discarded[key] ?: 0

    /**
     * Two lanes, two priorities. Each lane is still a single worker thread —
     * jobs on the same lane never run concurrently — but the next track's
     * job no longer waits behind the current track's. Priority selects the
     * lane (see [submit]), and FIFO is per lane.
     *
     * The queue holds [AnalysisJob]s only; anything else submitted here is a
     * bug, and failing fast on it is better than a stuck comparator.
     */
    private val jobSeq = AtomicLong(0)

    private inner class AnalysisJob(
        val lane: Lane,
        val priority: Int,
        val track: String?,
        val block: () -> Unit,
    ) : Runnable, Comparable<AnalysisJob> {
        val seq = jobSeq.getAndIncrement()
        override fun run() {
            currentLane.set(lane)
            lane.inFlight.incrementAndGet()
            try {
                block()
            } finally {
                currentLane.remove()
                // Lane-local idle release: the old global `running.isEmpty()`
                // close could land while the other lane was mid-inference.
                // Zero here means nothing submitted or running on this lane,
                // so its sessions are safe to drop; a queued backlog on the
                // lane keeps them warm instead of churning reloads. (A session
                // holds its arena and parsed graph in native heap while open,
                // which a backgrounded player cannot justify between
                // transitions; reloading costs under a second against an
                // analysis that already takes several.)
                if (lane.inFlight.decrementAndGet() == 0) lane.releaseModels()
            }
        }
        override fun compareTo(other: AnalysisJob): Int =
            compareValuesBy(this, other, { -it.priority }, { it.seq })
    }

    private fun laneThreadFactory(name: String): ThreadFactory = ThreadFactory { runnable ->
        Thread(runnable, name).apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY
        }
    }

    private val highExecutor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        PriorityBlockingQueue<Runnable>(),
        laneThreadFactory("bitchord-smart-high"),
    )

    private val lowExecutor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        PriorityBlockingQueue<Runnable>(),
        laneThreadFactory("bitchord-smart-low"),
    )

    private fun submit(trackId: String? = null, block: () -> Unit) {
        // Priority is the lane selector now, not just queue order: the next
        // track's analysis must never queue behind a backlog of normals.
        // RejectedExecutionException can only come from shutdown (see
        // [release]), and at that point the process is going away anyway.
        // The track id files the job's log lines against their track.
        // Dual lanes run only under PERFORMANCE mode: EFFICIENT and BALANCED
        // collapse onto the high lane so analysis stays single-threaded and
        // the priority queue alone keeps the next track ahead of the backlog.
        // The mode is read per submit, so toggling it mid-flight needs no
        // drain: the in-flight job finishes on its lane (per-lane tracker
        // instances plus the global [running] guard keep the brief overlap
        // safe) while every new job takes the newly selected lane.
        // Priority threading was removed for stock parity (every job is
        // normal): under PERFORMANCE every job takes the low lane, otherwise
        // the high lane — single-lane FIFO either way, like upstream.
        val dualLane = AppSettings.automixPerformanceMode.value == AutomixPerformanceMode.PERFORMANCE
        val low = dualLane
        val lane = if (low) lowLane else highLane
        (if (low) lowExecutor else highExecutor).execute(AnalysisJob(lane, PRIORITY_NORMAL, trackId, block))
    }

    /**
     * What is known about [trackId] right now: never a computation, never a
     * block. Returns an empty analysis for anything not yet finished, which
     * [assessTransitionTier] reads as no evidence rather than as a failure.
     */
    fun analysisFor(trackId: String): TrackAnalysis = results[trackId] ?: TrackAnalysis(trackId = trackId)

    /**
     * Looks [trackId] up on disk, once, off the playback thread.
     *
     * Deliberately not folded into [analysisFor], which is called several times
     * per tick from the playback thread and must never touch the filesystem.
     * The result lands in [results] a tick or two later, which is immaterial
     * against the seconds a real analysis takes — and against the alternative,
     * which is not having it at all.
     */
    private fun restoreOnce(trackId: String) {
        if (results.containsKey(trackId)) return
        // The set doubles as the once-guard: add() is true only for the first
        // caller, so a track with nothing stored is looked for once per session
        // rather than on every tick.
        if (!restoreAttempted.add(trackId)) return
        submit(trackId) {
            val stored = store.load(trackId) ?: return@submit
            TrackLog.d(TAG, "Restored analysis for $trackId: bpm=${stored.bpm} conf=${stored.beatConfidence}")
            results.putIfAbsent(trackId, stored)
        }
    }

    /** True once [trackId] has a result, including a failure. Nothing more will arrive. */
    fun isAnalysed(trackId: String): Boolean = results.containsKey(trackId)

    /**
     * True while a decode and inference for [trackId] is actually in flight.
     *
     * Distinct from "not analysed": a track waiting on bytes and a track being
     * worked on right now are the same absence of a result, and the difference
     * is the difference between something being wrong and something simply
     * taking the several seconds it takes.
     */
    fun isAnalysing(trackId: String): Boolean = trackId in running

    /**
     * Queues [trackId] (playing at [uri]) for analysis if it is not already
     * done or in flight. Cheap to call repeatedly; callers re-request as
     * caching progresses.
     *
     * Runs in up to two passes, because waiting for a full cache is what kept
     * the *incoming* track of every transition unanalysed. A track only
     * finishes downloading once it is already playing, so the whole-track pass
     * lands in time to describe a track's own mix-out and never in time to
     * describe its entry — which is the half the listener hears at the moment
     * of the blend.
     *
     * So a track with enough of a head on disk gets [analyzeHead] first: beat
     * grid only, over the opening window, which is all the incoming side is
     * read for. That result is provisional and is replaced by the whole-track
     * [analyze] as soon as the remaining bytes arrive.
     *
     * None of that applies to a track playing off the device, which goes
     * straight to [analyze] on the first tick that reaches it — there is nothing
     * to wait for and nothing to escalate through. See [LocalAudioSource] for
     * why that needed saying at all.
     */
    fun request(trackId: String, uri: Uri, durationSeconds: Double) {
        if (trackId.isBlank()) return
        if (trackId in running) return
        // Long-track skip is DJ-only: a 10+ minute track is recorded
        // ready-but-empty so the DJ planner renders it plainly. Stock upstream
        // analyses long tracks normally, so normal Automix falls through.
        if (AppSettings.mixsetModeEnabled.value &&
            durationSeconds.isFinite() && durationSeconds > MAX_ANALYSIS_DURATION_SECONDS) {
            if (results[trackId] == null) {
                TrackLog.d(TAG, "Skipping analysis of $trackId: ${durationSeconds}s exceeds ${MAX_ANALYSIS_DURATION_SECONDS}s")
                results[trackId] = empty(trackId, durationSeconds)
            }
            return
        }
        val analysisUri = analysisUriFor(trackId, uri) ?: return

        // Only the canonical YouTube rendition of this recording will do, not
        // the source substitute the player happens to be on: see
        // [chooseRendition]. Waiting on the live URI is what made analysis
        // arrive after the transition that needed it.
        // Queued before the cache is even consulted, so a track measured in an
        // earlier session short-circuits the whole path rather than being
        // re-earned from audio the cache may since have evicted.
        restoreOnce(trackId)

        // A track playing off the device is not a download in progress. Every
        // byte of it is already there, and none of those bytes are in the cache:
        // local URIs are routed past it rather than written into it a second
        // time. So everything below answers "nothing on disk" for a track that
        // is entirely on disk, and the head fetch that answer falls back to is a
        // no-op for anything without a YouTube id — which is why a local file
        // was never queued for analysis at all. See [LocalAudioSource].
        val local = LocalAudioSource.isLocal(analysisUri)

        // Complete *and* not already written off. A copy that decoded to
        // nothing is not a copy worth routing to: counting it as "fully cached"
        // sent this down the whole-track path on every tick with nothing left
        // for that path to read, and each of those empty passes was then
        // counted as a failed decode.
        //
        // Not asked of a local file, whose bytes the cache has never seen and
        // whose completeness is not a question: it is whole from the first tick.
        val complete = if (local) {
            emptyList()
        } else {
            cache.renditionsOf(analysisUri)
                .filter { AutomixAnalysisSource.isCanonicalYouTubeRendition(analysisUri.getQueryParameter("v"), it.key) }
                .filter { it.isComplete && it.key !in badRenditions }
        }
        val usableComplete = local || complete.isNotEmpty()

        val recorded = results[trackId]
        // Two things are worth superseding, and nothing else is. A provisional
        // head result, because replacing it with the whole-track pass is the
        // entire point of it — and a recorded failure, but only once a copy of
        // the track nobody has read yet turns up. Re-deciding a failure against
        // the same renditions that produced it would just spend the decode
        // again for the same answer.
        //
        // A local file is its own single copy, keyed by URI — see [copyToRead] —
        // so a failure there used to be read once and stay read. That froze
        // tracks on transient opens (a file still being written, a content
        // provider hiccup), so a local copy now gets one fresh-open retry
        // before the write-off below applies (see [analyze]).
        val untried = if (local) {
            uri.toString() !in triedRenditions
        } else {
            complete.any { it.key !in triedRenditions }
        }
        val supersedable = when {
            recorded == null -> false
            trackId in provisional -> usableComplete
            else -> !recorded.isUsable && untried
        }
        if (recorded != null && !supersedable) {
            // One exception to returning empty-handed. A provisional result is a
            // placeholder, not an answer — it says the opening decoded, not that
            // the track is measured — and the thing that supersedes it is bytes.
            // Without this nudge the byte escalation stops at whatever the first
            // successful head happened to cost, nothing else ever asks for the
            // rest, and a queued track reaches its own transition carrying an
            // entry-only estimate: no content end, no mix-out anchor, no vocal
            // mask, which is most of what the outgoing half of a blend reads.
            if (trackId in provisional && !usableComplete) cache.requestAnalysisHead(analysisUri)
            return
        }
        // The strike count belongs to the attempt that was given up on, not to
        // the track for the rest of the session; a reopened track starts level.
        if (recorded != null && !recorded.isUsable) shortDecodes.remove(trackId)
        // One head attempt per track: a partial container that will not parse
        // now is unlikely to parse ten ticks later, and retrying a decode every
        // 250ms would cost more than the analysis it is trying to bring
        // forward. Skipped entirely for a local file, which is `usableComplete`
        // from the first tick: the head pass exists to get ahead of a download,
        // and there is no download to get ahead of.
        val headRendition = if (usableComplete) null else headWorthTrying(trackId, analysisUri, durationSeconds)
        if (!usableComplete && headRendition == null) {
            // Nothing on disk worth decoding, so ask for something. Every other
            // writer either fetches this track's opening too late to matter or
            // never fetches it at all — see [AudioCache.requestAnalysisHead],
            // which is a no-op after the first call and for anything that isn't
            // a YouTube-backed track.
            cache.requestAnalysisHead(analysisUri)
            return
        }
        // Stuck watchdog (P0/F5): a job that has occupied its lane past
        // STUCK_JOB_TIMEOUT_MS blocks that lane's queue, so the watchdog is
        // per track but the blockage is per lane — the other lane keeps
        // serving its own priority. Reported once, never preempted — see
        // the constant for why killing it is worse.
        val started = jobStartMs[trackId]
        if (started != null && trackId in running &&
            System.currentTimeMillis() - started > STUCK_JOB_TIMEOUT_MS &&
            stuckLogged.add(trackId)
        ) {
            TrackLog.e(TAG, "Analysis job for $trackId stuck over ${STUCK_JOB_TIMEOUT_MS}ms on its lane")
        }
        if (!running.add(trackId)) return
        jobStartMs[trackId] = System.currentTimeMillis()

        submit(trackId) {
            try {
                // Efficient mode yields to decoding and playback rather than
                // competing for a core. Thread count remains the speed knob.
                // Applies on both lanes: each analysis job runs on a lane
                // thread and reads the mode dynamically.
                Process.setThreadPriority(
                    if (AppSettings.automixPerformanceMode.value == AutomixPerformanceMode.EFFICIENT) {
                        Process.THREAD_PRIORITY_BACKGROUND
                    } else {
                        Process.THREAD_PRIORITY_DEFAULT
                    },
                )
                // [restoreOnce] queues onto the lane executors, so a stored result for this track has landed by now if there
                // was one — but the decision to get here was taken a tick
                // earlier, when it had not. Without this check a track measured
                // in an earlier session is restored and then immediately spends
                // seven seconds recomputing the identical numbers. A provisional
                // result is exempt: superseding one is the whole point of it.
                val landed = results[trackId]
                if (landed != null && landed.isUsable && trackId !in provisional) return@submit
                if (usableComplete) {
                    val outcome = analyze(trackId, analysisUri, durationSeconds)
                    val whole = outcome.analysis
                    if (whole != null) {
                        results[trackId] = whole
                        provisional.remove(trackId)
                        shortDecodes.remove(trackId)
                        throwStrikes.remove(trackId)
                        openFailStrikes.remove(trackId)
                        noDurationStrikes.remove(trackId)
                        // Only the whole-track pass is persisted. A head result
                        // is missing everything past its window — the outro, the
                        // mix-out anchor, the energy curve — and storing one
                        // would freeze a deliberately partial answer in place of
                        // the complete one that supersedes it minutes later.
                        store.save(trackId, whole)
                        restoreAttempted.add(trackId)
                    } else if (outcome.decodedShort &&
                        shortDecodes.merge(trackId, 1, Int::plus)!! >= MAX_SHORT_DECODE_ATTEMPTS
                    ) {
                        // Bounded, so a container that is genuinely truncated
                        // isn't re-decoded on every tick for the rest of the
                        // session. Any provisional head result already published
                        // stays: a partial analysis beats an empty one.
                        TrackLog.w(TAG, "Giving up on $trackId after $MAX_SHORT_DECODE_ATTEMPTS short decodes")
                        if (trackId !in provisional) {
                            results[trackId] = TrackAnalysis(
                                status = TrackAnalysis.STATUS_READY,
                                trackId = trackId,
                                duration = durationSeconds,
                            )
                        }
                    }
                } else {
                    // Marked before it is published, so a reader on the playback
                    // thread can never see a provisional result that is not
                    // flagged as one.
                    analyzeHead(trackId, analysisUri, durationSeconds, headRendition!!)?.let { head ->
                        provisional.add(trackId)
                        results[trackId] = head
                    }
                }
            } catch (error: Throwable) {
                // Throwable, not Exception: decode leans on MediaCodec, and an
                // OOM or a codec-level Error uncaught on a pool thread that is
                // nobody's parent takes the whole app down for work whose
                // entire failure mode is meant to be "this track goes
                // unanalysed".
                TrackLog.w(TAG, "Analysis of $trackId failed", error)
                // A failed head pass records nothing: the whole-track pass reads
                // a different, complete file and deserves its own attempt.
                // [headWorthTrying] has already made sure the head is not tried
                // twice, so this cannot spin.
                if (usableComplete) {
                    // Recorded as ready-but-empty only after bounded strikes, so
                    // a track that trips on a transient (codec hiccup, half-
                    // flushed span, OOM under load) is retried instead of
                    // written off on the first stumble. The finally below
                    // releases the model sessions, so the next attempt starts
                    // from a released state rather than a dying one.
                    val strikes = throwStrikes.merge(trackId, 1, Int::plus)!!
                    if (strikes >= MAX_THROW_ATTEMPTS) {
                        TrackLog.w(TAG, "Giving up on $trackId after $strikes thrown attempts", error)
                        throwStrikes.remove(trackId)
                        results[trackId] = TrackAnalysis(
                            status = TrackAnalysis.STATUS_READY,
                            trackId = trackId,
                            duration = durationSeconds,
                        )
                        provisional.remove(trackId)
                    } else {
                        TrackLog.d(TAG, "Deferring $trackId after throw (attempt $strikes)", error)
                    }
                }
            } finally {
                running.remove(trackId)
                jobStartMs.remove(trackId)
                stuckLogged.remove(trackId)
                // Model sessions are released lane-locally in
                // [AnalysisJob.run] once nothing is submitted or running on
                // that lane — the old global `running.isEmpty()` close could
                // land while the other lane was mid-inference.
            }
        }
    }

    /**
     * Whether enough of [uri]'s head is on disk to be worth a decode, claiming
     * the attempt if so.
     *
     * The byte threshold is derived from the rendition's own average bitrate
     * where the duration is known, because "30 seconds of audio" is a wildly
     * different number of bytes at 96 kbps and at lossless.
     * [HEAD_BYTES_MARGIN] covers the container header and the fact that a
     * track's opening is rarely at its own average bitrate.
     *
     * Where the duration isn't known — which is the common case, since callers
     * request analysis before anything has read the container — the estimate is
     * unavailable and [MIN_HEAD_BYTES] stands in. That is about 30 s of a
     * typical stream but only a few seconds of lossless, so a single attempt
     * gated on it would be spent on too little audio for exactly the tracks
     * that carry the most bytes per second.
     *
     * Hence retrying on growth rather than attempting once: an attempt is
     * allowed again only when the cached prefix has [HEAD_RETRY_GROWTH]-fold
     * grown since the last one. A track therefore gets a handful of tries
     * spread across its download instead of either one try or one per tick.
     */
    private fun headWorthTrying(
        trackId: String,
        uri: Uri,
        durationSeconds: Double,
    ): AudioCache.Rendition? {
        // Across every rendition of the recording, not just the one the player
        // happens to be on. The same track can be part-downloaded under a
        // sibling cache key, and the live URI's own copy is frequently the one
        // holding nothing — a track that reported six megabytes cached twenty
        // minutes earlier reported zero here, because the question was being
        // asked of the wrong copy of it.
        val candidate = cache.renditionsOf(uri)
            .filter { AutomixAnalysisSource.isCanonicalYouTubeRendition(uri.getQueryParameter("v"), it.key) }
            .filter { it.cachedPrefix > 0L && it.key !in badRenditions }
            // The growth guard, applied as a filter rather than to the winner.
            // Applied afterwards it did not skip a copy, it ended the search: the
            // single best candidate was chosen, refused for not having grown, and
            // the second-best — frequently the one that would have worked — was
            // never reached. A track therefore got exactly one head attempt ever,
            // against whichever copy of it happened to rank highest at the time.
            .filter { rendition ->
                val previous = headAttempts[rendition.key] ?: return@filter true
                rendition.cachedPrefix >= previous * HEAD_RETRY_GROWTH
            }
            // Most *audio*, not most bytes: renditions differ in bitrate, so the
            // largest prefix is not necessarily the longest playable head.
            .maxByOrNull { headSecondsOf(it, durationSeconds) }
            ?: return null

        val prefix = candidate.cachedPrefix
        val total = candidate.contentLength
        val needed = if (durationSeconds.isFinite() && durationSeconds > MIN_HEAD_SECONDS && total > 0) {
            val bytesPerSecond = total / durationSeconds
            // Sized to [MIN_HEAD_SECONDS] — the shortest decode [analyzeHead]
            // will accept — not to the model's full window. Gating on the full
            // window meant demanding two and a half times the input the analysis
            // would actually settle for: a lossless rendition needs nine
            // megabytes on disk for thirty seconds of audio, and a track sitting
            // at six was refused outright despite holding twice what was needed
            // to produce a result. Whatever *is* cached still gets decoded — the
            // read simply runs out — so a larger prefix is used when there is
            // one, and [HEAD_RETRY_GROWTH] comes back for a better look as the
            // rest arrives.
            (MIN_HEAD_SECONDS * bytesPerSecond * HEAD_BYTES_MARGIN).toLong()
                .coerceAtLeast(MIN_HEAD_BYTES)
                .coerceAtMost(total)
        } else {
            MIN_HEAD_BYTES
        }
        if (prefix < needed) {
            // Once per track, not per tick: a head pass that never fires is
            // invisible otherwise, which is exactly how the first version of
            // this shipped doing nothing at all.
            if (headSkipLogged.add("$trackId@${candidate.key}")) {
                TrackLog.d(
                    TAG,
                    "Head pass for $trackId waiting: ${prefix / 1024}kB cached of " +
                        "${needed / 1024}kB needed (rendition ${candidate.key})",
                )
            }
            return null
        }

        headAttempts[candidate.key] = prefix
        return candidate
    }

    /**
     * Roughly how many seconds of audio a rendition's cached prefix holds.
     *
     * The ranking this feeds used to be `cachedPrefix / contentLength`, which
     * answers zero whenever the length is unknown — and the length is unknown
     * for precisely the entry that matters most, the head
     * [AudioCache.requestAnalysisHead] just fetched, because a bounded request
     * gets a bounded answer. A megabyte of freshly downloaded opening therefore
     * scored below a sibling holding eight unusable kilobytes, and the analyzer
     * spent its one attempt on the wrong copy.
     *
     * [ASSUMED_BYTES_PER_SECOND] stands in where the length still isn't known.
     * It only has to be the right order of magnitude: this decides which copy to
     * read first, not whether the result is trusted.
     */
    private fun headSecondsOf(rendition: AudioCache.Rendition, durationSeconds: Double): Double =
        if (rendition.contentLength > 0 && durationSeconds.isFinite() && durationSeconds > 0) {
            rendition.cachedPrefix * durationSeconds / rendition.contentLength
        } else {
            rendition.cachedPrefix / ASSUMED_BYTES_PER_SECOND
        }

    /**
     * The opening window only: a beat grid, and nothing that would need the rest
     * of the file.
     *
     * Runs [TrackFeatures] over the head, but copies across only the fields
     * that describe an *entry*: where the file starts making sound, the pickup,
     * the end of the intro, and the mix-in candidates. Those are all measured
     * within the opening seconds, so a head-only pass measures them exactly as
     * a whole-track pass would.
     *
     * Everything that describes the rest of the track is dropped on the floor —
     * content end, outro, mix-out anchors, the energy curve. Over a 30 s head
     * that pass does not fail, it answers confidently about a track that is
     * mostly missing, and the planner has no way to tell the difference. Left at
     * their defaults they read as "no evidence": [contentEndTime] falls back to
     * the real duration and the mix-out list ranks as empty.
     *
     * The energy curve is dropped for the same reason even though it is
     * genuinely measured here: the policy indexes the vocal mask against it and
     * counts audible seconds from it, and a curve that stops at 30 s would have
     * this track's *outgoing* half scored against a window it does not cover.
     * A vocal mask therefore cannot come from this pass either, and waits for
     * the whole-track one.
     */
    private fun analyzeHead(
        trackId: String,
        uri: Uri,
        durationSeconds: Double,
        rendition: AudioCache.Rendition,
    ): TrackAnalysis? {
        fun openSource(): MediaDataSource? = cache.renditionDataSource(uri, rendition)

        // Same guard the whole-track pass applies, for the same reason: a
        // sibling rendition can be a different cut, and a beat grid borrowed
        // across that would put every anchor seconds out. Skipped for the
        // player's own copy, which is the track by definition. A header that
        // will not parse yet is not held against the rendition — more bytes may
        // well fix it — but a length that genuinely disagrees is.
        val expected = durationSeconds.takeIf { it.isFinite() && it > 0 }
        // Set when the duration gate below gives up rejecting and lets the
        // copy through marked: ORed into [TrackAnalysis.provisionalHead] at
        // construction so the planner treats the result as suspect, never
        // as a confident full read.
        var provisionalRendition = false
        if (expected != null && rendition.key != cache.cacheKeyOf(uri)) {
            val length = openSource()?.use(AudioDecoder::containerDurationSeconds)
            if (length == null || length <= 0) {
                // Logged rather than returned quietly. This is the likeliest way
                // for a head pass to do nothing — a partial container the
                // extractor will not read a duration out of — and while it was
                // silent the whole path looked like it had never run.
                TrackLog.d(TAG, "Head rendition ${rendition.key} for $trackId has no readable duration yet")
                return null
            }
            if (abs(length - expected) > RENDITION_DURATION_TOLERANCE) {
                // Stock upstream writes a genuinely disagreeing length off:
                // a sibling rendition that long apart is a different cut.
                // Counting rejects toward a provisional accept is DJ-only.
                if (!AppSettings.mixsetModeEnabled.value) {
                    TrackLog.d(
                        TAG,
                        "Head rendition ${rendition.key} rejected for $trackId: " +
                            "${"%.1f".format(Locale.ROOT, length)}s against ${"%.1f".format(Locale.ROOT, expected)}s expected",
                    )
                    badRenditions.add(rendition.key)
                    return null
                }
                val rejects = (renditionRejects[rendition.key] ?: 0) + 1
                renditionRejects[rendition.key] = rejects
                if (rejects < PROVISIONAL_ACCEPT_ROUNDS) {
                    TrackLog.d(
                        TAG,
                        "Head rendition ${rendition.key} rejected for $trackId: " +
                            "${"%.1f".format(Locale.ROOT, length)}s against ${"%.1f".format(Locale.ROOT, expected)}s expected",
                    )
                    return null
                }
                // Same copy refused on every tick and nothing else to try: the
                // skew is the container's, not a wrong cut. Let it through
                // flagged — notably WITHOUT [badRenditions], which would hide
                // it from every later tick and re-create the starvation.
                TrackLog.d(
                    TAG,
                    "Head rendition ${rendition.key} for $trackId accepted provisionally " +
                        "after $rejects rejects " +
                        "(${"%.1f".format(Locale.ROOT, length)}s against ${"%.1f".format(Locale.ROOT, expected)}s expected)",
                )
                provisionalRendition = true
            } else {
                renditionRejects.remove(rendition.key)
            }
        }

        val window = BeatTracker.WINDOW_SECONDS
        val head = region(::openSource, 0.0, window, features = null, deriveFeatures = true)
            ?: run {
                TrackLog.d(TAG, "Head pass for $trackId could not decode rendition ${rendition.key}")
                return null
            }
        // What was decoded, not what was asked for: the source stops where the
        // cache does. A tempo read off a few seconds is not a weaker measurement
        // than one read off thirty, it is a different and much more credulous
        // one, and the planner cannot see the difference — so it is refused here
        // and the next attempt gets more of the file.
        if (head.seconds < MIN_HEAD_SECONDS) {
            TrackLog.d(TAG, "Head pass for $trackId decoded only ${"%.1f".format(Locale.ROOT, head.seconds)}s; too short")
            return null
        }
        val grid = head.grid
        val entry = head.features
        if (grid == null && entry == null) {
            TrackLog.d(TAG, "Head pass for $trackId produced nothing usable")
            return null
        }

        TrackLog.d(
            TAG,
            "Analysed head of $trackId: bpm=${grid?.bpm ?: entry?.bpm} " +
                "conf=${grid?.beatConfidence ?: entry?.beatConfidence} " +
                "audibleStart=${entry?.audibleStartTime} pickup=${entry?.pickupTime} " +
                "introEnd=${entry?.introEndTime} mixInCandidates=${entry?.mixInCandidates?.size ?: 0} " +
                "over ${"%.1f".format(Locale.ROOT, head.seconds)}s",
        )

        // Full-audit P0.2: the head window's vocal mask IS shipped now, indexed
        // against the head window's own energy curve and flagged provisional.
        // The incoming side of a transition only ever reads its entry window —
        // exactly what this pass measured — so a provisional mask is the
        // evidence it needs. The outgoing side needs tail evidence, which this
        // pass cannot have; the planner's both-sides gate (P0.1) therefore only
        // honours a provisional mask for the incoming side. Curve and mask are
        // attached together or not at all: the policy requires equal lengths,
        // and a bare curve would mislead every energy consumer with no vocal
        // benefit in return.
        val headMask = head.vocalMask?.toList().orEmpty()
        val headCurve = entry?.energyCurve.orEmpty()
        // Shipping head evidence (curve + mask) is DJ-only: stock upstream's
        // head pass returns no energy curve and no vocal mask, so normal
        // Automix plans off the beat grid and entry cues alone.
        val shipHeadEvidence = AppSettings.mixsetModeEnabled.value &&
            headMask.isNotEmpty() && headMask.size == headCurve.size
        if (shipHeadEvidence) renditionRejects.remove(rendition.key)
        return TrackAnalysis(
            status = TrackAnalysis.STATUS_READY,
            trackId = trackId,
            duration = durationSeconds,
            bpm = grid?.bpm ?: entry?.bpm ?: 0.0,
            beatInterval = grid?.beatInterval ?: entry?.beatInterval ?: 0.0,
            beatConfidence = grid?.beatConfidence ?: entry?.beatConfidence ?: 0.0,
            downbeats = grid?.downbeats ?: entry?.downbeats.orEmpty(),
            firstBeat = grid?.firstBeat ?: entry?.firstBeat ?: 0.0,
            key = entry?.key.orEmpty(),
            keyConfidence = entry?.keyConfidence ?: 0.0,
            audibleStartTime = entry?.audibleStartTime,
            pickupTime = entry?.pickupTime,
            introEndTime = entry?.introEndTime ?: 0.0,
            mixInTime = entry?.mixInTime ?: 0.0,
            mixInCandidates = entry?.mixInCandidates.orEmpty(),
            energyCurve = if (shipHeadEvidence) headCurve else emptyList(),
            vocalActivityMask = if (shipHeadEvidence) headMask else emptyList(),
            provisionalHead = AppSettings.mixsetModeEnabled.value &&
                (shipHeadEvidence || provisionalRendition),
        )
    }

    /**
     * Picks which rendition of [uri]'s recording to analyse: the lightest one
     * that is both complete and the same cut as the track being played.
     *
     * A recording can be on disk two or three times over — the Opus stream
     * YouTube served, a substituted source's copy, a later quality upgrade — and
     * they hold the same music, so an analysis of any of them describes all of
     * them. Analysing the smallest is not merely cheaper: it is the one that
     * finished downloading first, and a lossless upgrade can take most of a
     * track's play time to arrive. Waiting for it is why analysis was landing
     * seconds *after* the transition it was meant to inform.
     *
     * The duration check is what makes the sharing safe. A `#alt` rendition
     * comes from an entirely different source and may be a different cut —
     * a radio edit, a version with a longer intro — and a beat grid borrowed
     * across that difference would put every downbeat and both mix anchors
     * seconds out. Comparing container durations catches exactly that, and
     * costs a header parse per candidate.
     */
    private fun chooseRendition(
        trackId: String,
        uri: Uri,
        durationSeconds: Double,
    ): AudioCache.Rendition? {
        val complete = cache.renditionsOf(uri)
            .filter { AutomixAnalysisSource.isCanonicalYouTubeRendition(uri.getQueryParameter("v"), it.key) }
            .filter { it.isComplete && it.key !in badRenditions }
        if (complete.isEmpty()) return null

        val expected = durationSeconds.takeIf { it.isFinite() && it > 0 }
        // Without a length to check a sibling against, sharing would be a guess,
        // so only the rendition actually being played can be trusted.
        if (expected == null) {
            val own = cache.cacheKeyOf(uri)
            complete.firstOrNull { it.key == own }?.let { return it }
            // Nothing to cross-check against — but one copy is not ambiguous
            // either, and refusing it outright is a dead end rather than a
            // safeguard. [cacheKeyOf] answers with whichever rendition the key
            // factory resolves to *now*, which with substitution on is the `#alt`
            // entry; the copy actually on disk is routinely the plain one, so
            // this asked for a rendition that did not exist and returned null on
            // every tick, silently, for as long as the track stayed queued.
            //
            // The risk the duration check exists to catch is borrowing a beat
            // grid across two different cuts of a song. That needs two copies to
            // choose wrongly between. With exactly one there is no choice being
            // made, and the worst case degrades from "never analysed" to "a grid
            // measured off the only audio we have".
            return complete.singleOrNull()?.also {
                TrackLog.d(
                    TAG,
                    "Analysing $trackId from its only cached rendition ${it.key}; " +
                        "no duration to check it against",
                )
            }
        }

        for (candidate in complete) {
            val length = cache.renditionDataSource(uri, candidate)
                .use(AudioDecoder::containerDurationSeconds) ?: continue
            if (length <= 0) continue
            if (abs(length - expected) > RENDITION_DURATION_TOLERANCE) {
                val rejects = (renditionRejects[candidate.key] ?: 0) + 1
                renditionRejects[candidate.key] = rejects
                if (rejects < PROVISIONAL_ACCEPT_ROUNDS) {
                    TrackLog.d(
                        TAG,
                        "Rendition ${candidate.key} rejected for $trackId: " +
                            "${"%.1f".format(Locale.ROOT, length)}s against ${"%.1f".format(Locale.ROOT, expected)}s expected",
                    )
                    continue
                }
                // Refused on every tick and still the only copy in play: same
                // escape hatch as the head gate — let it through flagged, so
                // the whole-track result it produces carries low trust.
                TrackLog.d(
                    TAG,
                    "Rendition ${candidate.key} for $trackId accepted provisionally " +
                        "after $rejects rejects " +
                        "(${"%.1f".format(Locale.ROOT, length)}s against ${"%.1f".format(Locale.ROOT, expected)}s expected)",
                )
                return candidate
            }
            renditionRejects.remove(candidate.key)
            if (candidate.key != cache.cacheKeyOf(uri)) {
                TrackLog.d(
                    TAG,
                    "Analysing $trackId from lighter rendition ${candidate.key} " +
                        "(${candidate.contentLength / 1024}kB)",
                )
            }
            return candidate
        }
        return null
    }

    /**
     * Where a whole-track pass reads its audio from, and the identity the retry
     * bookkeeping keys off.
     *
     * Two kinds of copy exist and they are not interchangeable. A **cached
     * rendition** is one of several copies of a streamed recording: chosen
     * between by [chooseRendition], cross-checked for being the same cut, and —
     * when it turns out undecodable — thrown off disk so a clean one can replace
     * it. A **local file** is the track itself: exactly one of it, complete from
     * the moment it exists, nothing to choose between, and not ours to delete.
     *
     * [rendition] is what tells the two apart. Null means the audio is a file on
     * the device, which is why the blame-and-discard half of [structure] is
     * conditional on it rather than on a flag.
     */
    private class Copy(
        val key: String,
        val rendition: AudioCache.Rendition?,
        val open: () -> MediaDataSource?,
    )

    /**
     * Which copy of [trackId]'s audio to read, or null when there isn't a usable
     * one yet.
     *
     * A local URI short-circuits the entire rendition search: none of what that
     * search decides between applies to a file the user already has, and asking
     * the cache about one only ever produced the empty answer that kept Automix
     * from analysing local tracks at all. The URI stands in as the cache key,
     * which is what [request]'s `untried` check reads so a file that will not
     * decode is not re-decoded on every tick for the rest of the session.
     */
    private fun copyToRead(trackId: String, uri: Uri, durationSeconds: Double): Copy? {
        if (LocalAudioSource.isLocal(uri)) {
            return Copy(key = uri.toString(), rendition = null) { LocalAudioSource.open(resolver, uri) }
        }
        val rendition = chooseRendition(trackId, uri, durationSeconds) ?: return null
        return Copy(key = rendition.key, rendition = rendition) { cache.renditionDataSource(uri, rendition) }
    }

    /**
     * Source-backed playback URIs (JioSaavn, module/lossless, etc.) carry the
     * same title/artist/runtime matching hints as a YouTube item. Resolve that
     * identity once, then keep playback on its original URI while analysis
     * fills and reads the separately pinned YouTube Opus entry.
     */
    private fun analysisUriFor(trackId: String, playbackUri: Uri): Uri? {
        if (playbackUri.getQueryParameter("v") != null || playbackUri.authority != "source") return playbackUri
        sourceAnalysisUris[trackId]?.let { return it }
        if (trackId in sourceResolutionAttempted) return null
        if (!sourceResolutions.add(trackId)) return null
        val target = SourceResolver.targetIn(playbackUri)
        sourceResolutionScope.launch {
            try {
                val match = findYouTubeMatch(target)
                if (match != null) {
                    sourceAnalysisUris[trackId] = Uri.parse(AutomixAnalysisSource.opusUri(match.videoId))
                    Log.d(TAG, "Automix analysis for $trackId will use YouTube Opus ${match.videoId}")
                } else {
                    Log.d(TAG, "No YouTube match for Automix analysis of $trackId")
                }
            } catch (error: Throwable) {
                Log.d(TAG, "YouTube match failed for Automix analysis of $trackId: ${error.message}")
            } finally {
                sourceResolutionAttempted.add(trackId)
                sourceResolutions.remove(trackId)
            }
        }
        return null
    }

    private suspend fun findYouTubeMatch(target: TrackMatcher.Target): com.music.bitchord.data.model.Song? {
        if (target.title.isBlank()) return null
        for (query in TrackMatcher.queries(target)) {
            val candidates = YtMusicRepository.search(query, SearchFilter.SONGS)
                .getOrNull()
                ?.filterIsInstance<SearchResult.Track>()
                ?.map { it.song }
                .orEmpty()
            TrackMatcher.best(candidates, target)?.let { return it }
        }
        return null
    }

    /**
     * What a whole-track pass came back with.
     *
     * [decodedShort] is the difference between "this copy is broken, strike it"
     * and "there was no copy to read", which the caller counts very differently:
     * three strikes writes a track off for the session. Conflating the two spent
     * all three in 922ms on a track whose only complete copy had just been
     * rejected — the following two attempts decoded nothing because there was
     * nothing left to decode, and were counted as though they had tried.
     */
    private class WholeTrack(val analysis: TrackAnalysis?, val decodedShort: Boolean = false)

    /**
     * What Pass 1 came back with.
     *
     * [decodedShort] has to survive the return rather than collapsing into a null [features]: it is
     * the same distinction [WholeTrack.decodedShort] draws, between a broken copy and no copy, and
     * only one of the two is a strike.
     */
    private class Structural(
        val features: TrackFeatures.Features?,
        val decodedShort: Boolean = false,
    )

    /**
     * Decodes the whole track and reduces it to DSP features.
     *
     * A method rather than a block in [analyze] for a reason that is about memory, not tidiness —
     * see the call site. Everything it decodes is dead by the time it returns, and returning is
     * what makes that true of the heap as well as of the program.
     */
    private fun structure(
        trackId: String,
        uri: Uri,
        copy: Copy,
        effectiveDuration: Double,
    ): Structural {
        val structRate = TrackFeatures.sampleRate
        val decoded = copy.open()?.use { AudioDecoder.decodeRegion(it, 0.0, effectiveDuration) }
            ?: return Structural(null)
        val (pcm, _) = decoded

        // A decode that stops early is indistinguishable, downstream, from a
        // track that simply goes quiet: [TrackFeatures] is handed the
        // container's full duration alongside a short buffer, reads the
        // difference as trailing silence, and puts the mix-out anchor where the
        // bytes ran out. Nothing about the result looks wrong — it is a complete
        // analysis with a plausible contentEnd — and the audible symptom is the
        // track being faded out minutes early. Refused outright rather than
        // published, because a missing analysis degrades to a plain crossfade
        // while a confidently wrong one does not degrade at all.
        val decodedSeconds = if (pcm.sampleRate > 0) pcm.samples.size / pcm.sampleRate else 0.0
        if (decodedSeconds < effectiveDuration * MIN_DECODED_FRACTION) {
            TrackLog.w(
                TAG,
                "Analysis of $trackId refused: ${copy.key} decoded " +
                    "${"%.1f".format(Locale.ROOT, decodedSeconds)}s of a " +
                    "${"%.1f".format(Locale.ROOT, effectiveDuration)}s " +
                    // The two causes are different enough to be worth naming. A
                    // cached rendition stops at the first hole read-ahead left in
                    // it; a file on the device has no holes, so a short decode
                    // there means the container itself is truncated or damaged.
                    if (copy.rendition != null) "container — cached with holes?" else "container — truncated file?",
            )
            // Both halves of this are about *renditions*, so both are conditional
            // on there being one. A local file has no sibling copy to be routed to
            // instead and is not ours to delete either way; the three-strike count
            // in [request] is the whole bound there, and it is enough, because a
            // file on disk that decodes short will decode short again.
            copy.rendition?.let { rendition ->
                // Remembered, so the retry reaches for a *different* rendition. This
                // is the whole reason the lightest one is only a preference: a
                // rendition the cache index calls complete can still decode short if
                // it was written badly, and without this the retries would pick the
                // same broken copy three times over and give up on a track whose
                // heavier rendition would have analysed perfectly well.
                badRenditions.add(rendition.key)
                // And thrown off disk, not merely remembered. A file the cache calls
                // complete and the decoder gives up on partway is not going to
                // improve: nothing else will ever write to it, because as far as the
                // cache is concerned it is finished. Remembering it only helps for as
                // long as this process lives — a restart clears the set, the same
                // bytes are read again, and the same seconds are spent reaching the
                // same conclusion. Deleting it is what lets a clean copy be fetched.
                // Skipped for whatever the player is reading from; see
                // [AudioCache.discardBadRendition].
                if (rendition.isComplete && discardsOf(rendition.key) < MAX_RENDITION_DISCARDS &&
                    cache.discardBadRendition(uri, rendition.key)
                ) {
                    // Every memory of that copy goes with the bytes. Deleting the
                    // file and then still refusing its key is the worst of both: the
                    // clean copy [AudioCache.requestAnalysisHead] fetches in its place
                    // is filtered straight back out by [badRenditions], the track is
                    // written off for the session anyway, and the download was spent
                    // on nothing. The strike count goes too — the next attempt reads
                    // genuinely different bytes, so it starts level.
                    discarded.merge(rendition.key, 1, Int::plus)
                    badRenditions.remove(rendition.key)
                    triedRenditions.remove(rendition.key)
                    renditionRejects.remove(rendition.key)
                    shortDecodes.remove(trackId)
                }
            }
            return Structural(null, decodedShort = true)
        }

        val samples = if (abs(pcm.sampleRate - structRate) > 1.0) {
            TrackFeatures.resample(pcm.samples, pcm.sampleRate, structRate) ?: return Structural(null)
        } else {
            pcm.samples
        }

        return Structural(TrackFeatures.analyze(samples, effectiveDuration))
    }

    /**
     * The whole-track pass. A null [WholeTrack.analysis] means "not now, try
     * again"; see the short-decode guard below, which is the one condition that
     * produces a confident-looking analysis that is wrong by minutes rather than
     * merely absent, and the only one that counts as a strike.
     */
    private fun analyze(trackId: String, uri: Uri, durationSeconds: Double): WholeTrack {
        val copy = copyToRead(trackId, uri, durationSeconds) ?: return WholeTrack(null)
        // Stock upstream records the copy up front, before the outcome is
        // known. The outcome-decided recording below is DJ-only patience.
        if (!AppSettings.mixsetModeEnabled.value) triedRenditions.add(copy.key)
        // Recorded only once the outcome is decided (see the three return
        // sites below), never on the way in: a throw between here and there —
        // pitch resample NPE, model OOM — used to burn the copy before any
        // result existed, freezing the track as session-sticky FAILED with no
        // copy left to reopen it. A failure that decided nothing deserves
        // another attempt, not a write-off.
        val openSource = copy.open

        var effectiveDuration = durationSeconds
        if (!effectiveDuration.isFinite() || effectiveDuration <= 0) {
            effectiveDuration = openSource()?.use(AudioDecoder::containerDurationSeconds) ?: 0.0
        }
        if (effectiveDuration <= 0) {
            // Stock upstream writes the track off on the first unreadable
            // duration. The strike-bound deferral below is DJ-only patience.
            if (!AppSettings.mixsetModeEnabled.value) {
                TrackLog.d(TAG, "Skipping $trackId: ${copy.key} has no readable duration")
                return WholeTrack(empty(trackId, 0.0))
            }
            // No readable duration yet is "not now", not a verdict: stream
            // metadata routinely arrives seconds after playback starts.
            // Bounded so a track that genuinely has no duration doesn't open a
            // data source on every tick for the rest of the session; each
            // attempt costs one container-duration query, not a decode.
            val strikes = noDurationStrikes.merge(trackId, 1, Int::plus)!!
            if (strikes >= MAX_NO_DURATION_ATTEMPTS) {
                TrackLog.w(TAG, "Giving up on $trackId: no readable duration after $strikes attempts")
                noDurationStrikes.remove(trackId)
                return WholeTrack(empty(trackId, 0.0))
            }
            TrackLog.d(TAG, "Deferring $trackId: ${copy.key} has no readable duration yet (attempt $strikes)")
            return WholeTrack(null)
        }

        // Pass 1 (Phase 1, DSP-only): the analyzer needs the whole track — the energy curve,
        // phrase structure and mix-out anchor all read the tail, not just a window of it — at its
        // own low sample rate, so this is a much smaller decode than a full-rate pass would be.
        //
        // In a frame of its own, and handing back only the features, because of what it allocates
        // to get them: the whole track decoded to mono at the container's rate (35 MB for a
        // 3.5-minute song) plus the resampled copy the DSP reads (8 MB). Neither is touched again
        // after this line, but a local holding either stays reachable to the end of the method, and
        // the rest of the method is Pass 2 — the most allocation-heavy part of the analysis.
        // Measured on the API 28 emulator: those two buffers were 43 MB of an 82 MB live set, still
        // held while the models ran, in a process that was reaching a 256 MB heap limit and had
        // died on it. Returning is what releases them — a `val` cannot be nulled, and a narrower
        // scope alone does not make ART treat one as dead.
        val structural = structure(trackId, uri, copy, effectiveDuration)
        if (structural.decodedShort) {
            triedRenditions.add(copy.key)
            return WholeTrack(null, decodedShort = true)
        }
        val features = structural.features
        if (features == null) {
            // Stock upstream writes the copy off on the first null
            // open/decode. The local-reopen retry and the strike-bound
            // deferral below are DJ-only patience.
            if (!AppSettings.mixsetModeEnabled.value) {
                return WholeTrack(empty(trackId, effectiveDuration))
            }
            // A null open or decode on a copy the cache called complete is
            // usually transient — an eviction race, a half-flushed span, a
            // content provider hiccup on a local file — so strike it instead
            // of burning it. Local copies additionally get one fresh-open
            // retry first: there is no second copy of a file, and a single
            // reopen is what separates "still being written" from "unreadable".
            if (copy.rendition == null && localReopenTried.add(copy.key)) {
                TrackLog.d(TAG, "Retrying local $trackId once with a fresh open")
                return WholeTrack(null)
            }
            val strikes = openFailStrikes.merge(trackId, 1, Int::plus)!!
            if (strikes < MAX_OPEN_FAIL_ATTEMPTS) {
                TrackLog.d(TAG, "Deferring $trackId: null open/decode on ${copy.key} (attempt $strikes)")
                return WholeTrack(null)
            }
            openFailStrikes.remove(trackId)
            triedRenditions.add(copy.key)
            return WholeTrack(empty(trackId, effectiveDuration))
        }

        // Pass 2 (Phases 2 and 3, models): the Beat This! grid and the open-unmix vocal mask, over
        // the head and tail only. A transition only ever reads the tail of the outgoing track and
        // the head of the incoming one, and a track is both of those at different moments, so the
        // middle is never decoded for this. Both models read the same decoded region, so the
        // stereo buffer is paid for once.
        val window = BeatTracker.WINDOW_SECONDS
        val tailStart = max(0.0, effectiveDuration - window)
        val head = region(openSource, 0.0, minOf(window, effectiveDuration), features, trackPitch = true)
        val tail = if (tailStart > window / 2) region(openSource, tailStart, effectiveDuration, features) else null

        val headGrid = head?.grid
        val tailGrid = tail?.grid

        // The tail governs where the outgoing track is mixed out, so it takes precedence; the
        // head is what a track uses when it is the *incoming* side of a different transition.
        val leading = tailGrid ?: headGrid

        TrackLog.d(
            TAG,
            "Analysed $trackId: bpm=${leading?.bpm ?: features.bpm} " +
                "conf=${leading?.beatConfidence ?: features.beatConfidence} " +
                "key=${features.key} contentEnd=${features.contentEndTime} " +
                "mixOutCandidates=${features.mixOutCandidates.size} " +
                "vocalMask=${if (head?.vocalMask != null || tail?.vocalMask != null) "model" else "dsp"}",
        )

        val mergedDownbeats = (headGrid?.downbeats.orEmpty() + tailGrid?.downbeats.orEmpty())
            .ifEmpty { features.downbeats }
            .sorted()
        // v2 §2b: fold the transient fine curves into labels + scalars once,
        // here, while they are still in memory. The store keeps only the
        // outputs; the curves are released with `features` below.
        val structure = detectStructure(features, mergedDownbeats, effectiveDuration)

        triedRenditions.add(copy.key)
        // Null-safe: local files have no rendition copy. The count is kept
        // (not reset) while its copy stays suspect, so re-analysis of the
        // same skewed bytes stays flagged instead of flapping.
        val suspectKey = copy.rendition?.key
        // Accepting a duration-skewed copy provisionally is DJ-only: stock
        // upstream never flags (it has no provisional flag at all), so normal
        // Automix reads the analysis straight.
        val provisionalCopy = AppSettings.mixsetModeEnabled.value && suspectKey != null &&
            (renditionRejects[suspectKey] ?: 0) >= PROVISIONAL_ACCEPT_ROUNDS
        if (!provisionalCopy && suspectKey != null) renditionRejects.remove(suspectKey)
        return WholeTrack(
                TrackAnalysis(
                    status = TrackAnalysis.STATUS_READY,
                    trackId = trackId,
                    duration = effectiveDuration,
                    contentEndTime = features.contentEndTime.takeIf { it > 0 } ?: effectiveDuration,
                    bpm = leading?.bpm ?: features.bpm,
                beatInterval = leading?.beatInterval ?: features.beatInterval,
                beatConfidence = leading?.beatConfidence ?: features.beatConfidence,
                downbeats = mergedDownbeats,
                firstBeat = headGrid?.firstBeat ?: features.firstBeat,
                phraseBoundaries = features.phraseBoundaries,
                key = features.key,
                keyConfidence = features.keyConfidence,
                audibleStartTime = features.audibleStartTime,
                pickupTime = features.pickupTime,
                introEndTime = features.introEndTime,
                outroStartTime = features.outroStartTime,
                mixInTime = features.mixInTime,
                mixOutTime = features.mixOutTime,
                mixInCandidates = features.mixInCandidates,
                mixOutCandidates = features.mixOutCandidates,
                energyCurve = features.energyCurve,
                lowEnergyCurve = features.lowEnergyCurve,
                // Finetune §6.1: hand the transient fine curve to the in-memory
                // analysis (dropped on persist — see AnalysisStore).
                energyCurveFine = features.energyCurveFine,
                // The model's mask where it ran, the DSP heuristic's where it didn't. Falling back to
                // the heuristic rather than to nothing matters because the policy reads an
                // absent mask and a neutral one identically — as "no evidence" — so a failed model
                // pass would otherwise silently discard the estimate Phase 1 already had.
                vocalActivityMask = mergeMasks(features.energyCurve.size, head?.vocalMask, tail?.vocalMask)
                    ?: features.vocalActivityMask,
                vocalProbability = features.vocalProbability,
                // Full-plan P4: master descriptors ride the whole-track pass.
                loudnessLufs = features.loudnessLufs,
                peakDbfs = features.peakDbfs,
                dynamicRangeDb = features.dynamicRangeDb,
                vocalPitchMedianHz = head?.pitch?.voicedMedianHz() ?: 0.0,
                pitchConfidence = head?.pitch?.voicedMeanConfidence() ?: 0.0,
                structureMap = structure.map,
                structuredDropSec = structure.dropSec,
                structuredBreakSec = structure.breakSec,
                structuredOutroSec = structure.outroSec,
                structuredBuildupSec = structure.buildupSec,
                plainCutBreathSec = structure.breathSec,
                dropConfidence = structure.dropConfidence,
                buildupMethod = structure.buildupMethod,
                buildupFootSec = structure.buildupFootSec,
                buildupSpanSec = structure.buildupSpanSec,
                buildupRise = structure.buildupRise,
                // Duration-skewed copy accepted on patience: the evidence is
                // real but the cut is unverified — downstream gates (vocal
                // mask, both-sides) already treat provisional as suspect.
                provisionalHead = provisionalCopy,
            ),
        )
    }

    /** v2 §2b/§4 detector output: labels plus the scalars the planner reads. */
    private data class DetectedStructure(
        val map: List<StructureLabel> = emptyList(),
        val dropSec: Double? = null,
        val breakSec: Double? = null,
        val outroSec: Double? = null,
        val buildupSec: Double? = null,
        /** Spec finetune §7: breathing room before the cut (see TrackAnalysis). */
        val breathSec: Double? = null,
        val dropConfidence: Double? = null,
        val buildupMethod: String? = null,
        val buildupFootSec: Double? = null,
        val buildupSpanSec: Double? = null,
        val buildupRise: Double? = null,
    )

    /**
     * Runs [StructureDetector] on the whole-track pass. Empty in, empty out:
     * any missing input degrades to no labels and the planner keeps the v1
     * heuristics (spec §2b fallback). Single O(n) pass over the fine curve.
     */
    private fun detectStructure(
        features: TrackFeatures.Features,
        downbeats: List<Double>,
        duration: Double,
    ): DetectedStructure {
        val fine = features.energyCurveFine
        if (fine.size < 8 || duration <= 0) return DetectedStructure()
        val energies = fine.mapNotNull { it.energy.takeIf { e -> e.isFinite() && e >= 0 } }
        if (energies.isEmpty()) return DetectedStructure()
        val meanRms = energies.average()
        if (meanRms <= 0) return DetectedStructure()
        val onsets = features.onsetTimes.filter { it.isFinite() }
        val meanOnset = if (duration > 0) onsets.size / duration else 0.0
        // v2 §2b AMBIENT: no trusted grid and almost no attacks — the whole
        // track is one ambient bed. Short-circuits the window classifier.
        // Finetune v1 §1.6: tighter gates (chill-beat 0.28–0.32 is NOT ambient)
        // plus flat-dynamics check — a quiet-but-varied track is not ambient.
        val variance = if (energies.size >= 2) {
            val mean = energies.average()
            energies.sumOf { (it - mean) * (it - mean) } / energies.size
        } else {
            Double.MAX_VALUE
        }
        if (features.beatConfidence < AMBIENT_BEAT_CONF && meanOnset < AMBIENT_ONSET_DENSITY &&
            variance < AMBIENT_RMS_VARIANCE && duration > 0
        ) {
            return DetectedStructure(
                map = listOf(StructureLabel(0.0, duration, StructureSectionType.AMBIENT)),
            )
        }
        val interval = features.beatInterval.takeIf { it.isFinite() && it > 0 }
            ?: if (features.bpm > 0) 60.0 / features.bpm else 0.0
        // Defense in depth (field crash 2026-09-05: an off-by-one in the
        // classifier FAILED every whole-track analysis): section labels must
        // never take down beat/vocal/pitch evidence already computed. Any
        // detector throw degrades to an empty map (v1 energy heuristics).
        val map = runCatching {
            StructureDetector.detect(
                fine = fine,
                centroid = features.spectralCentroidCurve,
                onsets = onsets,
                downbeats = downbeats,
                duration = duration,
                meanRms = meanRms,
                meanOnset = meanOnset,
                beatInterval = interval,
            )
        }.onFailure {
            TrackLog.w(TAG, "StructureDetector failed; degrading to v1 heuristics", it)
        }.getOrDefault(emptyList())
        // Exit-entry spec Fix 1: scored best-candidate drop selection replaces
        // first-match. Wrapped: any scorer throw degrades to the old first
        // DROP label (which firstDropSec's stored-wins contract still honors).
        val dropPick = runCatching {
            StructureDetector.selectFirstDrop(
                fine = fine,
                centroid = features.spectralCentroidCurve,
                onsets = onsets,
                downbeats = downbeats,
                duration = duration,
                meanRms = meanRms,
                meanOnset = meanOnset,
                beatInterval = interval,
                structureMap = map,
                bpm = features.bpm,
                beatConfidence = features.beatConfidence,
            )
        }.onFailure {
            TrackLog.w(TAG, "selectFirstDrop failed; falling back to first DROP label", it)
        }.getOrNull()
            ?: map.firstOrNull { it.type == StructureSectionType.DROP }?.start
                ?.let { StructureDetector.DropCandidate(it, null) }
        val dropSec = dropPick?.startSec
        // §4 gradient anchors on the detector's DROP; without one there is no
        // peak to walk back from, and buildupStart falls back downstream.
        val buildupSec = if (dropSec != null && dropSec.isFinite()) {
            val peak = fine.filter { it.time.isFinite() && abs(it.time - dropSec) <= 2.0 }
                .mapNotNull { it.energy.takeIf { e -> e.isFinite() && e > 0 } }
                .maxOrNull()
            if (peak != null) StructureDetector.gradientBuildup(fine, dropSec, peak) else null
        } else {
            null
        }
        // Phase A1: persist how the foot was found so drop trust survives a
        // restart without the transient fine curve. Only the analyzer-side
        // gradient path is measurable here; plan-time fallbacks (monotonic /
        // build-label / stored) re-derive live in trustedBuildupStart.
        val buildupFoot = buildupSec?.takeIf { it.isFinite() && dropSec != null }
        val buildupRise = if (buildupFoot != null && dropSec != null) {
            val peak = fine.filter { it.time.isFinite() && abs(it.time - dropSec) <= 2.0 }
                .mapNotNull { it.energy.takeIf { e -> e.isFinite() && e > 0 } }
                .maxOrNull()
            val climb = fine.filter { it.time.isFinite() && it.time >= buildupFoot && it.time <= dropSec }
                .mapNotNull { it.energy.takeIf { e -> e.isFinite() } }
            if (peak != null && peak > 0 && climb.isNotEmpty()) {
                (climb.average() - (climb.firstOrNull() ?: 0.0)) / peak
            } else null
        } else null
        return DetectedStructure(
            map = map,
            dropSec = dropSec?.takeIf { it.isFinite() },
            breakSec = map.firstOrNull { it.type == StructureSectionType.BREAK }?.start
                ?.takeIf { it.isFinite() },
            outroSec = map.firstOrNull { it.type == StructureSectionType.OUTRO }?.start
                ?.takeIf { it.isFinite() },
            buildupSec = buildupSec?.takeIf { it.isFinite() },
            breathSec = longestTailBreath(onsets, duration, interval),
            dropConfidence = dropPick?.score?.takeIf { it.isFinite() },
            buildupMethod = if (buildupFoot != null) "gradient" else null,
            buildupFootSec = buildupFoot,
            buildupSpanSec = if (buildupFoot != null && dropSec != null) dropSec - buildupFoot else null,
            buildupRise = buildupRise?.takeIf { it.isFinite() },
        )
    }

    /**
     * Spec finetune §7: the longest onset gap (>0.25 s) inside the last 35% of
     * the track, plus one beat of lookahead so the cut lands breathing room
     * rather than on the silence edge itself. Null when the tail never
     * breathes. Pure — shared with tests.
     */
    private fun longestTailBreath(
        onsets: List<Double>,
        duration: Double,
        beatInterval: Double,
    ): Double? {
        if (duration <= 0) return null
        val tailFrom = duration * 0.65
        val tail = (onsets.filter { it.isFinite() && it >= tailFrom } + duration).sorted()
        if (tail.size < 2) return null
        var bestStart = -1.0
        var bestGap = 0.25
        for (i in 0 until tail.size - 1) {
            val gap = tail[i + 1] - tail[i]
            if (gap > bestGap) {
                bestGap = gap
                bestStart = tail[i]
            }
        }
        if (bestStart < 0) return null
        val lookahead = if (beatInterval.isFinite() && beatInterval > 0) beatInterval else 0.5
        return (bestStart + lookahead).takeIf { it.isFinite() && it < duration }
    }

    /**
     * Everything a decoded region contributes, once its audio has been let go
     * of. [seconds] is what was actually decoded, which for a partially cached
     * file is not what was asked for.
     */
    private class Region(
        val grid: BeatTracker.Grid?,
        val vocalMask: DoubleArray?,
        val seconds: Double,
        /** Only populated when the caller asked for it; see [region]'s `deriveFeatures`. */
        val features: TrackFeatures.Features? = null,
        /** Only populated when the caller asked for it; see [region]'s `trackPitch`. */
        val pitch: PitchTracker.PitchCurve? = null,
    )

    /**
     * Decodes one stereo region and runs both models over it, returning only their results.
     *
     * The point of the function boundary is the audio: the stereo buffer, its mono mix and the
     * resampled copies are all local, so they become collectible the moment this returns rather
     * than staying live until the whole analysis finishes. A 30 s stereo region is several
     * megabytes before either model's own working set is counted.
     *
     * Null, or a null field, means "no model evidence for this window" — a codec that will not
     * configure, a region too short, a missing model — which [analyze] already falls back on.
     *
     * The extractor seeks to a sync sample at or before what was asked for, so the region's real
     * start (not [startSeconds]) is what its beat times must be stated against.
     */
    private fun region(
        openSource: () -> MediaDataSource?,
        startSeconds: Double,
        endSeconds: Double,
        features: TrackFeatures.Features?,
        deriveFeatures: Boolean = false,
        trackPitch: Boolean = false,
    ): Region? {
        // Lane-local sessions: region() always runs inside an [AnalysisJob],
        // so the executing lane is set; the low-lane fallback is paranoia for
        // direct unit-test calls, never production.
        val lane = currentLane.get() ?: lowLane
        val decoded = openSource()?.use { AudioDecoder.decodeRegionStereo(it, startSeconds, endSeconds) }
            ?: run {
                // The two ways this comes back empty mean opposite things and
                // were reported identically, which cost a round of guessing:
                // this one is the extractor refusing the container outright, so
                // the bytes are wrong or not enough of them are there to parse.
                TrackLog.d(TAG, "Region [$startSeconds, $endSeconds) would not open")
                return null
            }
        val (stereo, actualStart) = decoded
        if (stereo.left.size < stereo.sampleRate) {
            // And this one is a container that parsed fine and yielded under a
            // second of audio — a decode that started and ran out, not one that
            // never started.
            TrackLog.d(TAG, "Region [$startSeconds, $endSeconds) decoded ${stereo.left.size} frames; too few")
            return null
        }

        val seconds = stereo.left.size / stereo.sampleRate
        // In a frame of its own so the full-rate mono downmix is released before either model runs.
        // It is 23 MB for this window at 48 kHz — the same size as each of the two channels it
        // averages — and it is read exactly twice, to make the resampled model input and the DSP
        // one. As a local it would nonetheless stay reachable through `tracker.track` and
        // `vocalMask` below, which is where the analysis allocates most heavily and where the
        // process was dying. Same reasoning [derived] already had, one level further out.
        val inputs = regionInputs(stereo, seconds, deriveFeatures)

        // Pitch reads the head only, resampled off the beat model's own input
        // rather than a second downmix: the planner verifies the *incoming*
        // track's key against it, and nothing downstream of the head ever asks.
        // Garnish-grade: wrapped so a resample NPE or a model OOM degrades to
        // no pitch (the key gate closes itself below its confidence floor)
        // instead of failing the whole analysis — the way beat and vocal
        // already behave. A pitch pass must never decide a track's fate.
        val pitchCurve = if (trackPitch) {
            runCatching {
                inputs.forModel
                    ?.let { MelSpectrogram.resample(it, MelSpectrogram.sampleRate, PitchTracker.SAMPLE_RATE.toDouble()) }
                    ?.let { lane.pitch.track(it, offsetSeconds = actualStart) }
            }.getOrElse { error ->
                TrackLog.w(TAG, "Pitch pass degraded to none", error)
                null
            }
        } else {
            null
        }

        return Region(
            grid = inputs.forModel?.let { lane.tracker.track(it, offsetSeconds = actualStart) },
            vocalMask = features?.let { vocalMask(stereo, it, actualStart) },
            seconds = seconds,
            features = inputs.derived,
            pitch = pitchCurve,
        )
    }

    /** A region's model input, and its DSP features when the caller asked for them. */
    private class RegionInputs(val forModel: FloatArray?, val derived: TrackFeatures.Features?)

    /**
     * Reduces a decoded region to the buffers the models and the DSP actually read.
     *
     * Both come off one full-rate mono downmix, which is why they are made together rather than on
     * demand: that downmix is the largest single allocation in the analysis, and returning is the
     * only way to be rid of it before the models run.
     */
    private fun regionInputs(
        stereo: AudioDecoder.StereoPcm,
        seconds: Double,
        deriveFeatures: Boolean,
    ): RegionInputs {
        val mono = FloatArray(stereo.left.size) { index -> (stereo.left[index] + stereo.right[index]) * 0.5f }
        val forModel = if (abs(stereo.sampleRate - MelSpectrogram.sampleRate) > 1.0) {
            MelSpectrogram.resample(mono, stereo.sampleRate, MelSpectrogram.sampleRate)
        } else {
            mono
        }

        // Derived here rather than by the caller so the mono buffer is still
        // live: handing it back would keep several megabytes reachable for the
        // rest of the analysis, which is the one thing this function exists to
        // avoid.
        val derived = if (deriveFeatures) {
            val forFeatures = if (abs(stereo.sampleRate - TrackFeatures.sampleRate) > 1.0) {
                TrackFeatures.resample(mono, stereo.sampleRate, TrackFeatures.sampleRate)
            } else {
                mono
            }
            forFeatures?.let { TrackFeatures.analyze(it, seconds) }
        } else {
            null
        }

        return RegionInputs(forModel, derived)
    }

    /**
     * A vocal-presence value for every point on the energy curve, filled only where the model
     * actually ran.
     *
     * The policy indexes the mask against energy-curve sample times and requires the two to be the
     * same length, but the model's window is fixed at about 22 seconds, far less than a track. So
     * the mask is built at full length and filled only over this region.
     *
     * Everywhere else stays at [NEUTRAL_VOCAL]. That is not a guess dressed up as data: it sits
     * below the policy's own VOCAL_ACTIVE_THRESHOLD, so unmeasured material can never trip vocal
     * logic in either direction.
     */
    private fun vocalMask(
        stereo: AudioDecoder.StereoPcm,
        features: TrackFeatures.Features,
        actualStart: Double,
    ): DoubleArray? {
        val lane = currentLane.get() ?: lowLane
        val curve = features.energyCurve
        if (curve.isEmpty() || !VocalSpectrogram.available) return null

        // The beat model's window is longer than the vocal model's fixed input, so the region is
        // trimmed rather than handed over whole — [VocalTracker.track] refuses anything wider than
        // its graph, and refusing is how the tail of every region would otherwise go unmeasured.
        // Two frames of margin absorb the ±1 sample a rate conversion can land on.
        val maxSeconds = (VocalTracker.FIXED_FRAMES - 2) * VocalSpectrogram.hop / VocalSpectrogram.sampleRate
        val maxSamples = (maxSeconds * stereo.sampleRate).toInt().coerceAtMost(stereo.left.size)
        if (maxSamples <= 0) return null
        val left = if (maxSamples < stereo.left.size) stereo.left.copyOf(maxSamples) else stereo.left
        val right = if (maxSamples < stereo.right.size) stereo.right.copyOf(maxSamples) else stereo.right

        val values = lane.vocals.track(left, right, stereo.sampleRate) ?: return null

        val mask = DoubleArray(curve.size) { NEUTRAL_VOCAL }
        for (index in curve.indices) {
            val frame = ((curve[index].time - actualStart) * VocalSpectrogram.frameRate).toInt()
            if (frame in values.indices) mask[index] = values[frame].toDouble()
        }
        return mask
    }

    /**
     * Overlays the head and tail masks onto one full-length curve, or null when neither ran —
     * which the caller answers by keeping the DSP heuristic rather than reporting a mask of
     * nothing but [NEUTRAL_VOCAL].
     */
    private fun mergeMasks(size: Int, head: DoubleArray?, tail: DoubleArray?): List<Double>? {
        if (size <= 0 || (head == null && tail == null)) return null
        val merged = DoubleArray(size) { NEUTRAL_VOCAL }
        for (source in listOfNotNull(head, tail)) {
            for (index in merged.indices) {
                if (index < source.size && source[index] != NEUTRAL_VOCAL) merged[index] = source[index]
            }
        }
        return merged.toList()
    }

    /** Recorded ready-but-empty so a track that cannot be decoded is not retried every tick. */
    private fun empty(trackId: String, durationSeconds: Double) = TrackAnalysis(
        status = TrackAnalysis.STATUS_READY,
        trackId = trackId,
        duration = durationSeconds,
    )

    fun release() {
        highExecutor.shutdownNow()
        lowExecutor.shutdownNow()
        sourceResolutionScope.cancel()
        sourceAnalysisUris.clear()
        sourceResolutions.clear()
        sourceResolutionAttempted.clear()
        headAttempts.clear()
        headSkipLogged.clear()
        provisional.clear()
        restoreAttempted.clear()
        shortDecodes.clear()
        badRenditions.clear()
        triedRenditions.clear()
        discarded.clear()
        highLane.releaseModels()
        lowLane.releaseModels()
    }

    companion object {
        const val TAG = "BitChordTrackAnalyzer"

        /** Lane for the track queued to play next. Everything else is 0. */
        const val PRIORITY_NEXT = 1
        const val PRIORITY_NORMAL = 0

        /**
         * What an unmeasured instant reads as. Below the policy's VOCAL_ACTIVE_THRESHOLD by
         * design, so absence of measurement is never mistaken for absence of a vocal, or for the
         * presence of one.
         */
        const val NEUTRAL_VOCAL = 0.5

        /**
         * How much more than the average-bitrate estimate of the opening window
         * to insist on before decoding it. Covers the container header and the
         * fact that a track's opening is rarely at its own average bitrate.
         */
        const val HEAD_BYTES_MARGIN = 1.35

        /**
         * Floor under the computed threshold, and the whole requirement when the
         * duration is unknown. Roughly fifteen seconds at 128 kbps — a little
         * over [MIN_HEAD_SECONDS], so it guarantees a parsable container and a
         * usable decode without quietly reinstating the thirty-second demand the
         * bitrate estimate was just lowered away from.
         */
        const val MIN_HEAD_BYTES = 256L * 1024L

        /**
         * How much of the container's stated duration must actually decode
         * before the whole-track pass is trusted. Not 1.0: a decoder legitimately
         * comes up a frame or two short of the container's rounding, and
         * refusing over that would refuse everything.
         */
        const val MIN_DECODED_FRACTION = 0.95

        /** Refusals before a track is written off as truncated rather than still filling in. */
        const val MAX_SHORT_DECODE_ATTEMPTS = 3

        /** Uncaught throws before a track is written off rather than retried. */
        const val MAX_THROW_ATTEMPTS = 3

        /**
         * Tracks longer than this are never analyzed: a full decode plus
         * inference pass over 10+ minutes costs heat and battery for
         * transitions the track will rarely need, and the planner already
         * knows how to render an unmeasured track (plain dissolve). Recorded
         * as ready-but-empty at the [request] gate, so it is not retried.
         */
        const val MAX_ANALYSIS_DURATION_SECONDS = 600.0

        /** Null open/decode passes before the copy is burnt rather than deferred. */
        const val MAX_OPEN_FAIL_ATTEMPTS = 3

        /**
         * Ticks with no readable duration before a track is recorded empty.
         * Generous: stream metadata routinely arrives seconds after playback
         * starts, and each attempt costs one container-duration query, not a
         * decode.
         */
        const val MAX_NO_DURATION_ATTEMPTS = 10

        /**
         * How long a job may occupy its lane before it is reported
         * stuck. Detection only, never preemption: MediaCodec calls are not
         * reliably interruptible, and killing the thread would trade a slow
         * analysis for a leaked codec and a corrupted session.
         */
        const val STUCK_JOB_TIMEOUT_MS = 90_000L

        /**
         * Clean-slate retries per rendition. One: a discard is worth doing when
         * the bytes on disk are unrecoverable and nothing would otherwise
         * overwrite them, and worth doing exactly once, because a second identical
         * answer means the source is serving that file rather than the cache
         * having mangled it.
         */
        const val MAX_RENDITION_DISCARDS = 1

        /**
         * How far two renditions' container durations may differ and still count
         * as the same cut. Generous enough for codec padding and the player's own
         * rounding, tight enough that a different edit of the same song — where a
         * borrowed beat grid would be useless — is rejected.
         */
        const val RENDITION_DURATION_TOLERANCE = 1.0

        /**
         * Duration-skew rejects before a rendition is accepted provisionally.
         * Three counter hits: the head gate and the chooser share the count,
         * so the first tick still prefers a different copy everywhere and the
         * trip lands a tick or two later — concluding the skew is the
         * container's, not a wrong cut. An extraction-fallback stream
         * reporting ~1s off, rejected forever, is the starvation this bounds.
         * Provisional results carry the low-trust flag, so downstream gates
         * stay cautious with them.
         */
        const val PROVISIONAL_ACCEPT_ROUNDS = 3

        /**
         * Stand-in bitrate for a rendition whose real length isn't recorded yet,
         * used only to rank copies against each other in [headSecondsOf]. About
         * 160kbps, the middle of what the streams in play here run at.
         */
        const val ASSUMED_BYTES_PER_SECOND = 20_000.0

        /**
         * The least decoded audio a head-only tempo estimate is allowed to rest
         * on. Twelve seconds is around 24 beats at 120 bpm — enough for the
         * grid's own confidence measure to mean something.
         */
        const val MIN_HEAD_SECONDS = 12.0

        /**
         * How much more of the file has to be cached before the head is worth
         * decoding again. Doubling bounds the attempts to a handful over a whole
         * download while still catching up quickly on a high-bitrate rendition
         * whose first attempt covered only a few seconds.
         */
        const val HEAD_RETRY_GROWTH = 2
    }
}
