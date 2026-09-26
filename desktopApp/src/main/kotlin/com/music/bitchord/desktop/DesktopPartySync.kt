package com.music.bitchord.desktop

import com.music.bitchord.data.listentogether.PartyTrack
import com.music.bitchord.data.model.Song
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
    /** Puts the party's track on this device, resolving a stream for it. */
    private val playTrack: (PartyTrack) -> Unit,
) {

    private val jobs = mutableListOf<Job>()
    private var startJob: Job? = null

    private var loadingVideoId: String? = null
    private var alignedSeq = -1L
    private var driftStrikes = 0
    private var driftCooldownUntilMs = 0L

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
                .map { it.playback.seq to it.playback.isPlaying }
                .distinctUntilChanged()
                .collect { reconcile() }
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
        loadingVideoId = null
        alignedSeq = -1L
        driftStrikes = 0
    }

    /** Something the listener asked for here, which the party should be told about. */
    fun onLocalIntent() {
        if (!DesktopListenTogether.state.value.inParty) return
        quietUntilMs = nowMs() + INTENT_QUIET_MS
        publish()
    }

    /** Puts this device where the party is, writing to the engine rather than through intents. */
    fun reconcile() {
        val party = DesktopListenTogether.state.value
        if (!party.inParty) {
            loadingVideoId = null
            return
        }
        if (nowMs() < quietUntilMs) return
        val target = party.playback
        val track = target.track ?: return
        val playback = engine.state.value

        // A local file is this device's own business; the party has no copy of it to agree on.
        if (playback.song?.localPath != null || playback.song?.localUri != null) return

        // Before the first round trip the playhead is a guess, so the track and play/pause are
        // applied and the position is left alone.
        if (target.isPlaying && !party.clockSynced) return

        if (playback.song?.videoId != track.videoId) {
            if (loadingVideoId != track.videoId) {
                loadingVideoId = track.videoId
                playTrack(track)
            }
            return
        }
        loadingVideoId = null

        if (!target.isPlaying) {
            if (playback.isPlaying) engine.pause()
            if (abs(playback.positionMs - target.positionMs) > PAUSED_TOLERANCE_MS) {
                engine.seekTo(target.positionMs)
            }
            return
        }

        val want = DesktopListenTogether.partyPositionMs()
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

    /** Tells the party what this device just did. */
    private fun publish() {
        val party = DesktopListenTogether.state.value
        if (!party.inParty) return
        val playback = engine.state.value
        val song = playback.song ?: return
        if (song.localPath != null || song.localUri != null) return
        val track = song.toPartyTrack(playback.durationMs)
        val position = playback.positionMs.coerceAtLeast(0L)

        when {
            party.playback.track?.videoId != track.videoId ->
                DesktopListenTogether.setTrack(track, position, playback.isPlaying)
            playback.isPlaying && !party.playback.isPlaying -> DesktopListenTogether.play(position)
            !playback.isPlaying && party.playback.isPlaying -> DesktopListenTogether.pause(position)
            abs(position - (DesktopListenTogether.partyPositionMs() ?: position)) > SEEK_REPORT_FLOOR_MS ->
                DesktopListenTogether.seek(position)
        }
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
        const val INTENT_QUIET_MS = 2_500L
        const val RECONCILE_INTERVAL_MS = 700L
    }
}

/** This device's row as the party knows it — identity and artwork, not how the audio was got. */
internal fun Song.toPartyTrack(durationMs: Long): PartyTrack = PartyTrack(
    videoId = videoId,
    title = title,
    artist = artist,
    thumbnailUrl = thumbnailUrl,
    durationMs = durationMs.takeIf { it > 0 },
)
