package com.music.bitchord.desktop

import com.music.bitchord.data.listentogether.PartyTrack
import com.music.bitchord.data.listentogether.partyQueueIndexOf
import com.music.bitchord.data.listentogether.partyUpcomingAfter
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.QueueTier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Keeps this device's playback and the party's in step, ported from Android's `PartySync`.
 *
 * The two directions are deliberately separate, and never meet:
 *
 *  - **Outbound** is [onLocalIntent]: something the listener asked for here becomes a control the
 *    party is told about.
 *  - **Inbound** is [reconcile]: it writes to the engine directly, so nothing this class does to
 *    playback can come back round as an intent and be republished.
 *
 * Three details carry most of the weight, and each is a correction of something that reads as a
 * bug without it:
 *
 *  - **Resuming waits for the scheduled start.** The server anchors a resume slightly in the
 *    future so every device has one instant to aim at. Playing the moment the frame lands would
 *    start this device early by exactly that lead — and being *consistently* early is worse than
 *    occasionally late, because it sits under the drift limit and is never corrected.
 *  - **Drift is corrected by seeking, and only when it is real.** A seek is audible, so
 *    [DRIFT_LIMIT_MS] sits well above the jitter of two decoders running independently, and a
 *    correction needs [DRIFT_STRIKES] readings in a row before it fires.
 *  - **Position is only trusted once the clock is.** Before the first ping/pong there is no
 *    measured offset, so the track and play/pause are applied and the playhead is left alone
 *    rather than seeked to a guess.
 *
 * What Android has and this does not is audio focus: a desktop does not lose its audio to a phone
 * call, so there is no detach-and-rejoin here. If that changes the Android class is the reference.
 */
internal class DesktopPartySync(
    private val scope: CoroutineScope,
    private val engine: DesktopPlaybackEngine,
    /**
     * Puts the party's track on this device, resolving a stream for it: the party's running order
     * and the index of the track within it, as the phone's `PartySync.load` takes them.
     */
    private val playTrack: (queue: List<PartyTrack>, index: Int) -> Unit,
    /** The visible desktop queue, used to publish queue edits with playback intents. */
    private val localQueue: () -> Pair<List<Song>, Int> = { emptyList<Song>() to -1 },
    /**
     * Puts the party's upcoming tracks after the one playing here, without feeding them back
     * through [onLocalIntent] — the phone's `reconcileQueue`.
     */
    private val applyPartyUpcoming: (List<PartyTrack>) -> Unit = {},
    private val applyPartyAutoplay: (Boolean) -> Unit = {},
    private val onEnteredParty: () -> Unit = {},
    private val onLeftParty: () -> Unit = {},
) {

    private val jobs = mutableListOf<Job>()
    private var startJob: Job? = null
    private var publishJob: Job? = null

    private var loadingVideoId: String? = null
    private var alignedSeq = -1L
    private var driftStrikes = 0
    private var driftCooldownUntilMs = 0L
    /**
     * The seqs the party reaches once every control this device last sent has come back round.
     * Until then a reconcile would undo the very thing the listener just did; past them the quiet
     * window is over, however long it was meant to last.
     */
    private var awaitPlaybackSeq = Long.MAX_VALUE
    private var awaitQueueSeq = Long.MAX_VALUE
    private var locallyPaused = false
    private var lastPartyCode: String? = null
    private var appliedAutoplay: Boolean? = null
    /** A track selection whose decoder is still resolving. */
    private var pendingIntentVideoId: String? = null

    /**
     * Until when inbound reconciliation is held off.
     *
     * A control this device issued takes a moment to come back as a state frame; without a quiet
     * window the device that *issued* a seek would immediately fight its own echo.
     */
    private var quietUntilMs = 0L

    fun start() {
        stop()
        jobs += scope.launch {
            DesktopListenTogether.state
                .map { Triple(it.playback.seq, it.queue.seq, it.code) }
                .distinctUntilChanged()
                .collect { key ->
                    if (key.third != lastPartyCode) {
                        val wasInParty = lastPartyCode != null
                        val nowInParty = key.third != null
                        lastPartyCode = key.third
                        if (!wasInParty && nowInParty) onEnteredParty()
                        if (wasInParty && !nowInParty) onLeftParty()
                    }
                    // Every control sent has come back, so the party now describes what the
                    // listener did — or somebody else has moved it on past that, which is as good
                    // a reason to stop holding reconcile off.
                    if (pendingIntentVideoId == null &&
                        key.first >= awaitPlaybackSeq &&
                        key.second >= awaitQueueSeq
                    ) {
                        quietUntilMs = 0L
                    }
                    reconcile()
                }
        }
        // A steady tick as well as the frames: drift accumulates between controls, and nothing
        // arrives to announce it.
        jobs += scope.launch {
            while (true) {
                delay(RECONCILE_INTERVAL_MS)
                reconcile()
            }
        }
    }

    fun stop() {
        jobs.forEach(Job::cancel)
        jobs.clear()
        startJob?.cancel()
        startJob = null
        publishJob?.cancel()
        publishJob = null
        loadingVideoId = null
        alignedSeq = -1L
        driftStrikes = 0
        awaitPlaybackSeq = Long.MAX_VALUE
        awaitQueueSeq = Long.MAX_VALUE
        locallyPaused = false
        lastPartyCode = null
        appliedAutoplay = null
        pendingIntentVideoId = null
    }

    /** Something the listener asked for here, which the party should be told about. */
    fun onLocalIntent(expectedVideoId: String? = null) {
        val party = DesktopListenTogether.state.value
        if (!party.inParty || party.controlsLocked) return
        // Queue/transport gestures can arrive while a preceding song selection is still loading.
        // Keep waiting for that selection rather than letting the later gesture cancel its guard.
        val waitForVideoId = expectedVideoId
            ?: engine.state.value.takeIf { it.isLoading }?.song?.videoId
        pendingIntentVideoId = waitForVideoId
        // Nothing has gone out yet, so there is nothing to wait for and somebody else's control
        // must not end the window protecting this one.
        awaitPlaybackSeq = Long.MAX_VALUE
        awaitQueueSeq = Long.MAX_VALUE
        // A song selection first updates the visible state, then resolves a stream on a worker.
        // Keep reconciliation out of the way until that new track is actually playable; otherwise
        // the old party state can arrive during the resolve and pause/replace the new selection.
        quietUntilMs = if (waitForVideoId != null) Long.MAX_VALUE else nowMs() + INTENT_QUIET_MS
        publishJob?.cancel()
        publishJob = scope.launch {
            delay(PUBLISH_DEBOUNCE_MS)
            if (waitForVideoId != null) {
                repeat(PUBLISH_WAIT_ATTEMPTS) {
                    val playback = engine.state.value
                    if (playback.song?.videoId == waitForVideoId && !playback.isLoading) {
                        pendingIntentVideoId = null
                        quietUntilMs = nowMs() + INTENT_QUIET_MS
                        publish()
                        return@launch
                    }
                    delay(PUBLISH_WAIT_STEP_MS)
                }
                // The requested stream failed or was superseded. Do not publish the stale player
                // state; the regular party reconciliation will recover it.
                pendingIntentVideoId = null
                quietUntilMs = nowMs()
                return@launch
            }
            publish()
        }
    }

    /** In a host-locked party, play/pause affects only this computer. */
    fun handleLockedPlayPause(): Boolean {
        val party = DesktopListenTogether.state.value
        if (!party.controlsLocked) return false
        if (engine.state.value.isPlaying) {
            locallyPaused = true
            engine.pause()
        } else {
            locallyPaused = false
            DesktopListenTogether.partyPositionMs()?.let(engine::seekTo)
            engine.play()
        }
        return true
    }

    /** Puts this device where the party is, writing to the engine rather than through intents. */
    fun reconcile() {
        val party = DesktopListenTogether.state.value
        if (!party.inParty) {
            loadingVideoId = null
            locallyPaused = false
            appliedAutoplay = null
            return
        }
        if (!party.controlsLocked) locallyPaused = false
        if (nowMs() < quietUntilMs) return
        val target = party.playback
        if (target.autoplayEnabled != appliedAutoplay) {
            appliedAutoplay = target.autoplayEnabled
            applyPartyAutoplay(target.autoplayEnabled)
        }
        val track = target.track
        if (track == null) {
            // A newly created party is empty. Its host seeds it with the music already playing;
            // listeners never race the host for that first state.
            if (party.you?.isHost == true && party.connection == DesktopListenTogether.Connection.LIVE) publish()
            return
        }
        val playback = engine.state.value

        // A local file is this device's own business; the party has no copy of it to agree on.
        if (playback.song?.localPath != null || playback.song?.localUri != null) return

        // Before the first round trip the playhead is a guess, so the track and play/pause are
        // applied and the position is left alone.
        if (target.isPlaying && !party.clockSynced) return

        if (playback.song?.videoId != track.videoId) {
            if (loadingVideoId != track.videoId) {
                loadingVideoId = track.videoId
                // The queue is only usable if the track is actually in it. It may not be: the
                // queue and the track are two controls, and between them the party holds a new
                // running order with the old song still current. Falling back to the track alone
                // is always right; falling back to position zero never is.
                val at = partyQueueIndexOf(party.queue, target, track.videoId)
                if (at >= 0) playTrack(party.queue.items, at) else playTrack(listOf(track), 0)
            }
            return
        }
        loadingVideoId = null
        reconcileQueue(party)

        if (!target.isPlaying) {
            locallyPaused = false
            if (playback.isPlaying) engine.pause()
            if (abs(playback.positionMs - target.positionMs) > PAUSED_TOLERANCE_MS) {
                engine.seekTo(target.positionMs)
            }
            return
        }

        val want = DesktopListenTogether.partyPositionMs()
        if (locallyPaused) return
        if (!playback.isPlaying) {
            val wait = DesktopListenTogether.msUntilStart()
            if (wait > 0) {
                startJob?.cancel()
                startJob = scope.launch {
                    delay(wait)
                    reconcile()
                }
                return
            }
            if (want != null) engine.seekTo(want)
            startJob?.cancel()
            engine.play()
            return
        }

        if (want == null) return
        val drift = playback.positionMs - want
        val now = nowMs()
        val decision = decideSeek(
            drift = drift,
            controlSeq = target.seq,
            alignedSeq = alignedSeq,
            strikes = driftStrikes,
            nowMs = now,
            cooldownUntilMs = driftCooldownUntilMs,
        )
        alignedSeq = decision.alignedSeq
        driftStrikes = decision.strikes
        if (decision.cooldownUntilMs > 0) driftCooldownUntilMs = decision.cooldownUntilMs
        if (decision.seek) {
            DesktopTrackLog.log("party: ${decision.reason} ${drift}ms")
            engine.seekTo(want)
        }
    }

    /**
     * Brings what plays after this track into line with the party's — every tick, not just when
     * the queue's seq moves, so a local edit the party never took is put right rather than lived
     * with until the next track. Only while the track playing here is in the party's queue; a new
     * empty party keeps the host's queue, which [publish] is about to seed it with.
     */
    private fun reconcileQueue(party: DesktopListenTogether.State) {
        if (party.queue.items.isEmpty()) return
        val (songs, index) = localQueue()
        val currentId = songs.getOrNull(index)?.videoId ?: return
        if (partyQueueIndexOf(party.queue, party.playback, currentId) < 0) return
        applyPartyUpcoming(
            partyUpcomingAfter(party.queue, party.playback, currentId).take(MAX_PARTY_UPCOMING_QUEUE),
        )
    }

    /** Tells the party what this device just did. */
    private fun publish() {
        val party = DesktopListenTogether.state.value
        if (!party.inParty) return
        val playback = engine.state.value
        // Unlike ExoPlayer, the desktop state cannot represent "play when ready" while a stream
        // is still resolving: it temporarily reads as paused. Wait for the decoder so creating a
        // party during a load cannot seed a false pause or an incomplete current track.
        if (playback.isLoading) return
        val song = playback.song ?: return
        if (song.localPath != null || song.localUri != null) return
        val track = song.toPartyTrack(playback.durationMs)
        val position = playback.positionMs.coerceAtLeast(0L)
        val basePlayback = party.playback.seq
        val baseQueue = party.queue.seq
        var playbackControls = 0
        var queueControls = 0

        val (songs, index) = localQueue()
        val (shareable, shareIndex) = queueForPartyPublish(
            songs = songs,
            currentIndex = index,
            currentVideoId = song.videoId,
            currentDurationMs = playback.durationMs,
        )
        // Compared from the playing track on, by the tracks and their sections. Not by the party
        // queue's index, which only moves when the queue is resent and so read as different after
        // every ordinary track change; and not over history, which each device trims to its own
        // length, so two devices would otherwise resend their own pasts at each other on every
        // pause.
        val partyAt = partyQueueIndexOf(party.queue, party.playback, song.videoId)
        if (partyAt < 0 || shareIndex < 0 ||
            shareable.drop(shareIndex).map { it.videoId to it.fromAutoplay } !=
            party.queue.items.drop(partyAt).map { it.videoId to it.fromAutoplay }
        ) {
            DesktopListenTogether.setQueue(shareable, shareIndex)
            queueControls++
        }

        // After the queue, never before: a track change that arrives pointing into a running order
        // nobody has yet is the bug that played the wrong song.
        when {
            party.playback.track?.videoId != track.videoId -> {
                DesktopListenTogether.setTrack(track, position, playback.isPlaying)
                playbackControls++
            }
            playback.isPlaying && !party.playback.isPlaying -> {
                DesktopListenTogether.play(position)
                playbackControls++
            }
            !playback.isPlaying && party.playback.isPlaying -> {
                DesktopListenTogether.pause(position)
                playbackControls++
            }
            abs(position - (DesktopListenTogether.partyPositionMs() ?: position)) > SEEK_REPORT_FLOOR_MS -> {
                DesktopListenTogether.seek(position)
                playbackControls++
            }
        }
        awaitPlaybackSeq = basePlayback + playbackControls
        awaitQueueSeq = baseQueue + queueControls
        if (playbackControls == 0 && queueControls == 0) quietUntilMs = 0L
    }

    private fun nowMs(): Long = System.nanoTime() / 1_000_000L

    /** What [reconcile] decided about the playhead, and the counters it leaves behind. */
    internal data class SeekDecision(
        val seek: Boolean,
        val alignedSeq: Long,
        val strikes: Int,
        val cooldownUntilMs: Long = 0,
        val reason: String = "",
    )

    internal companion object {

        /**
         * Whether the playhead should be moved, given how far out it is.
         *
         * Pure so the rules can be stated rather than inferred: a new control aligns at once and
         * tightly, ordinary drift has to be both large and persistent, and a correction that has
         * just fired will not fire again until the cooldown is out.
         */
        fun decideSeek(
            drift: Long,
            controlSeq: Long,
            alignedSeq: Long,
            strikes: Int,
            nowMs: Long,
            cooldownUntilMs: Long,
        ): SeekDecision {
            // A deliberate jump by whoever holds the party: align to it now.
            if (controlSeq != alignedSeq) {
                return SeekDecision(
                    seek = abs(drift) > ALIGN_TOLERANCE_MS,
                    alignedSeq = controlSeq,
                    strikes = 0,
                    reason = "aligning onto control $controlSeq",
                )
            }
            if (abs(drift) <= DRIFT_LIMIT_MS) {
                return SeekDecision(seek = false, alignedSeq = alignedSeq, strikes = 0)
            }
            if (nowMs < cooldownUntilMs) {
                return SeekDecision(seek = false, alignedSeq = alignedSeq, strikes = strikes)
            }
            val next = strikes + 1
            if (next < DRIFT_STRIKES) {
                return SeekDecision(seek = false, alignedSeq = alignedSeq, strikes = next)
            }
            return SeekDecision(
                seek = true,
                alignedSeq = alignedSeq,
                strikes = 0,
                cooldownUntilMs = nowMs + DRIFT_COOLDOWN_MS,
                reason = "correcting",
            )
        }
        /** Well above two decoders' jitter, and at the level of an actual desync. */
        const val DRIFT_LIMIT_MS = 1_200L

        /** Readings in a row before a correction fires, so one stall does not cause a seek. */
        const val DRIFT_STRIKES = 2
        const val DRIFT_COOLDOWN_MS = 6_000L
        const val PAUSED_TOLERANCE_MS = 400L
        const val ALIGN_TOLERANCE_MS = 120L
        const val SEEK_REPORT_FLOOR_MS = 1_000L
        /**
         * The longest a local action is protected from being reconciled away. Normally it ends
         * sooner, when the controls it sent come back; this covers an echo that never does.
         */
        const val INTENT_QUIET_MS = 4_500L
        const val PUBLISH_DEBOUNCE_MS = 120L
        const val PUBLISH_WAIT_STEP_MS = 100L
        const val PUBLISH_WAIT_ATTEMPTS = 100
        const val RECONCILE_INTERVAL_MS = 700L
        const val MAX_PARTY_UPCOMING_QUEUE = 25

        /** The same queue projection Android publishes to a party. */
        internal fun queueForPartyPublish(
            songs: List<Song>,
            currentIndex: Int,
            currentVideoId: String,
            currentDurationMs: Long,
        ): Pair<List<PartyTrack>, Int> {
            val raw = songs.withIndex()
                .filter { (_, song) -> song.localPath == null && song.localUri == null }
            val rawCurrent = raw.indexOfFirst { (originalIndex, song) ->
                originalIndex == currentIndex && song.videoId == currentVideoId
            }.takeIf { it >= 0 } ?: raw.indexOfFirst { it.value.videoId == currentVideoId }

            val withoutContextTail = if (rawCurrent >= 0) {
                raw.take(rawCurrent + 1) + raw.drop(rawCurrent + 1)
                    .filter { it.value.queueTier != QueueTier.CONTEXT }
            } else {
                raw.filter { it.value.queueTier != QueueTier.CONTEXT }
            }
            val projectedCurrent = withoutContextTail.indexOfFirst { (originalIndex, song) ->
                originalIndex == currentIndex && song.videoId == currentVideoId
            }.takeIf { it >= 0 }
                ?: withoutContextTail.indexOfFirst { it.value.videoId == currentVideoId }
            val endExclusive = if (projectedCurrent >= 0) {
                (projectedCurrent + 1 + MAX_PARTY_UPCOMING_QUEUE)
                    .coerceAtMost(withoutContextTail.size)
            } else {
                (1 + MAX_PARTY_UPCOMING_QUEUE).coerceAtMost(withoutContextTail.size)
            }
            val tracks = withoutContextTail.take(endExclusive).map { (_, queued) ->
                queued.toPartyTrack(
                    if (queued.videoId == currentVideoId) currentDurationMs else 0L,
                )
            }
            return tracks to tracks.indexOfFirst { it.videoId == currentVideoId }
        }
    }
}

/** This device's row as the party knows it — identity and artwork, not how the audio was got. */
internal fun Song.toPartyTrack(durationMs: Long): PartyTrack = PartyTrack(
    videoId = videoId,
    title = title,
    artist = artist,
    thumbnailUrl = thumbnailUrl,
    durationMs = durationMs.takeIf { it > 0 },
    fromAutoplay = queueTier == QueueTier.AUTOPLAY,
)

/**
 * A shared queue item as a desktop-playable catalogue song, in the section the phone puts it in:
 * AutoPlay's, or the listener's own queue.
 */
internal fun PartyTrack.toDesktopSong(): Song = Song(
    videoId = videoId,
    title = title,
    artist = artist,
    thumbnailUrl = thumbnailUrl,
    // Not cosmetic: a cross-source match is made on it, as on the phone.
    durationText = durationMs?.let { ms ->
        val total = ms / 1000
        "%d:%02d".format(total / 60, total % 60)
    },
    queueTier = if (fromAutoplay) QueueTier.AUTOPLAY else QueueTier.USER_QUEUE,
)
