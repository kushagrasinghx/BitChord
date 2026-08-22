package com.music.bitchord.playback

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.smart.CrossfadeMode
import com.music.bitchord.playback.smart.TrackAnalysis
import com.music.bitchord.playback.smart.TransitionStyle
import com.music.bitchord.playback.smart.TransitionTrackInfo
import com.music.bitchord.playback.smart.planTransition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * A real crossfade: two tracks audible at once, the outgoing one falling as the
 * incoming one rises, the way Spotify and Apple Music do it.
 *
 * ## Why there are two players
 *
 * One ExoPlayer renders one queue item at a time, so at a track boundary there
 * is exactly one source and the gain it can be given is either 1 (no fade) or 0
 * (silence). The previous version of this class was a single-player volume
 * ramp, and that is precisely why it never sounded like a crossfade: it dipped
 * to silence at the join and climbed back out, leaving a hole where the blend
 * should be. Overlap needs a second decoder. There is no way around it.
 *
 * ## Which player plays what
 *
 * The naive second player is a copy of the queue, and that is the design that
 * fell over before — two players both convinced they own the playlist, two
 * audio focus requests, and a MediaSession whose player keeps changing under
 * it. So the split here is deliberately lopsided:
 *
 *  - **[player]** — the one ExoPlayer that owns the queue, backs the
 *    MediaSession and holds audio focus, exactly as it did before this class
 *    existed. It is the only player the rest of the app ever sees.
 *  - **[ghost]** — a single-item, throwaway player that renders *the tail of
 *    the track being left behind* and nothing else. It has no queue, receives
 *    no user commands, is nobody's source of truth, and can be stopped dead at
 *    any instant without anything needing to be unwound.
 *
 * At the crossfade point the session player **jumps to the next track
 * immediately** and fades up, while the ghost carries the old track's last
 * seconds down to silence. That ordering is the point: the queue index, the
 * metadata, the notification and the UI all flip to the incoming song the
 * moment it becomes audible, instead of trailing the song that is on its way
 * out.
 *
 * ## The handoff
 *
 * The one seam in this design is the instant the outgoing track stops being
 * rendered by [player] and starts being rendered by [ghost]. Two ExoPlayers
 * cannot be started sample-accurately against each other, so the ghost is
 * armed early, left free-running *silently* alongside the session player, and
 * nudged with repeated seeks until the two agree on position to within
 * [SYNC_TOLERANCE_MS]. Those corrections are free: nobody can hear a muted
 * player being seeked. Only once they are aligned does the [Phase.LAPPING]
 * step hand the outgoing track over, equal-power, across [LAP_MS] — short
 * enough that whatever misalignment is left reads as texture rather than as a
 * click.
 *
 * ## Curve
 *
 * `sin`/`cos` rather than the old `sqrt`: `sin²+cos²=1` exactly, so two tracks
 * fading past each other hold constant *power* the whole way through and the
 * transition has no dip in the middle. That is the standard crossfade law, and
 * it is what makes a long crossfade sound like a blend instead of a dip.
 */
@UnstableApi
class CrossfadeController(
    private val scope: CoroutineScope,
    private val player: ExoPlayer,
    /** Builds the tail player. Called at most once; the instance is kept warm. */
    private val newGhost: () -> ExoPlayer,
    /**
     * Stored Smart Fade analysis for a media item, or an empty [TrackAnalysis]
     * when there is none yet. This is the seam Phase 1's DSP analyzer plugs
     * into: until analysis finishes, a track reads as "no evidence", which
     * [planTransition] answers with the same fixed-length crossfade this
     * class always ran before Smart Fade existed.
     */
    private val analysisFor: (MediaItem) -> TrackAnalysis = { TrackAnalysis() },
    /**
     * Queues background analysis for a media item that will soon need it.
     * Cheap to call on every tick: a track already analysed, already in
     * flight, or not yet fully cached is a no-op.
     */
    private val requestAnalysis: (MediaItem) -> Unit = {},
    /**
     * The low-pass and high-pass riding each side of a transition. This is what
     * makes a plan's
     * [com.music.bitchord.playback.smart.TransitionPlan.transitionStyle] audible
     * rather than advisory: see [rideFilters]. Defaults to
     * [TransitionFilters.None], which renders every style as the plain
     * equal-power blend this class ran before.
     */
    private val filters: TransitionFilters = TransitionFilters.None,
) {

    private enum class Phase {
        /** Nothing in flight; watching for the next transition. */
        IDLE,

        /** Ghost is spinning up on the outgoing track and syncing to the session player. */
        ARMING,

        /** Outgoing track being handed from the session player to the ghost. */
        LAPPING,

        /** Session player rising on the new track, ghost falling on the old one. */
        FADING,

        /** Something interrupted the fade; the ghost is being ramped out of the way. */
        BAILING,
    }

    private var phase = Phase.IDLE
    private var ghost: ExoPlayer? = null

    /** Length of the transition in flight, in ms. Fixed when it begins. */
    private var fadeMs = 0L

    /**
     * Where the fade window ends, in the session player's position ms.
     * Standard mode sets this to the track's own duration, which is what
     * [driveArming] always compared against before Smart Fade existed; a
     * Smart Fade plan can set it earlier, at an analyzed mix-out anchor, so
     * [driveArming] watches this field rather than re-deriving the fade point
     * from [ExoPlayer.getDuration] on every tick.
     */
    private var fadeEndMs = 0L

    /**
     * Which setting armed the fade in flight, so [driveFade] knows which one
     * being switched off mid-blend means "stop now" rather than misreading the
     * other mode's control as the fade having been turned off. Smart Fade
     * doesn't need [AppSettings.crossfadeSeconds] to be above zero at all —
     * see [considerSmartTransition] — so treating that as still-zero as a
     * reason to cut a Smart Fade short would end every one of them on its
     * first tick.
     */
    private var smartFadeActive = false

    /**
     * Where the incoming track is cued when the lap hands the queue over, in
     * its own timeline ms. Standard fades always leave this at 0 — a plain
     * track change starts from the top — and only a Smart Fade plan sets it
     * to an analyzed mix-in point instead.
     */
    private var incomingCueTimeMs: Long = 0L

    /**
     * The tempo-stretch ratio applied to the incoming track for the
     * transition, stacked on top of whatever [AppSettings.playbackSpeed] the
     * listener already has set — 1.0 is a no-op. This is what actually
     * beatmatches a BEATMATCHED-tier plan: without it, the two tracks blend
     * at their own unrelated tempi and the result is a crossfade with
     * smarter timing, not a beatmatch.
     */
    private var incomingPlaybackRate: Double = 1.0

    /**
     * The style-specific half of the plan in flight — everything [rideFilters]
     * needs and nothing else. Fixed when the transition begins, because a plan
     * is recomputed every tick and a bass swap that moved to a different beat
     * halfway through the blend would be heard as the low end flapping.
     */
    private var render = Render()

    /**
     * The style fields of a [com.music.bitchord.playback.smart.TransitionPlan],
     * separated out so the standard (non-Smart) path can pass defaults without
     * constructing a plan it never made.
     */
    private data class Render(
        val style: TransitionStyle = TransitionStyle.EQUAL_POWER,
        val bassSwap: Boolean = false,
        val bassSwapFraction: Double = 0.7,
        val filterSweep: Double = 0.0,
    )

    private var lapStartedAt = 0L
    private var bailStartedAt = 0L
    private var armDeadline = 0L

    /**
     * Gain the ghost was at when the fade was interrupted. The ramp out is
     * scaled by it, because a fade abandoned during [Phase.ARMING] is one where
     * the ghost is still silent — ramping "down from 1" there would put the
     * outgoing track on at full volume purely in order to fade it out again.
     */
    private var bailFromGain = 0f

    /** Last time the ghost was nudged, so corrections get a chance to settle. */
    private var lastSyncAt = 0L

    /** Dedupes the per-tick plan log down to one line per distinct verdict. */
    private var lastPlanVerdict = ""

    /**
     * How far ahead of the session player the ghost is seeked, to cover the time
     * a seek itself takes to come back. Learned rather than assumed — it varies
     * with the device and with whether the track is on disk yet.
     */
    private var seekLeadMs = 60L

    /**
     * How long our own `seekToNextMediaItem` gets to be recognised as ours, so
     * the lap isn't mistaken for the listener reaching for the scrubber.
     *
     * A window rather than a count of expected callbacks: Media3 reports the
     * queue moving as both a discontinuity and an item transition, a seek that
     * turns out to be a no-op reports neither, and a counter that guesses wrong
     * either swallows the listener's next seek or bails on our own. A window
     * clears itself however many callbacks turn up.
     */
    private var selfMoveUntil = 0L

    /**
     * Whether the transition now arriving is this class advancing the queue on
     * the listener's behalf — the crossfade equivalent of a track ending.
     * Consumed by [PlaybackService], which otherwise has no way to tell our
     * seek apart from a manual skip and would stop honouring "sleep after this
     * song".
     */
    private var autoAdvance = false

    fun consumeAutoAdvance(): Boolean = autoAdvance.also { autoAdvance = false }

    /**
     * True while a transition is armed or running on [player] and [ghost].
     *
     * For callers about to do something that would otherwise fight this
     * class for the session player mid-blend — [PlaybackService]'s quality
     * upgrade is the one that does, since `replaceMediaItem` tears the
     * current source down and rebuilds it. Interrupting the source
     * [driveArming] is syncing against, or the one [driveFade] is ramping,
     * breaks the blend rather than merely delaying it, so such a caller
     * should wait for this to clear rather than proceed anyway.
     */
    fun isTransitioning(): Boolean = phase != Phase.IDLE

    private val listener = object : Player.Listener {
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            // The listener moving the playhead is something no half-finished
            // crossfade should survive; the lap's own seek is not.
            if (reason == Player.DISCONTINUITY_REASON_SEEK && !ourOwnMove()) bail()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            when (reason) {
                // Something replaced the queue out from under the fade — a new
                // album, a new search result — so the tail still playing is a
                // leftover of a session that no longer exists. Note that this
                // does *not* fire when AutoPlay appends to the end, since the
                // playing item doesn't change: extending the queue mid-fade is
                // harmless and shouldn't cost the listener the blend.
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> bail()
                Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> if (!ourOwnMove()) bail()
            }
        }

        override fun onPlayerError(error: PlaybackException) = bail()
    }

    private fun ourOwnMove(): Boolean = SystemClock.elapsedRealtime() < selfMoveUntil

    fun start() {
        player.addListener(listener)
        scope.launch {
            while (isActive) {
                tick()
                delay(
                    when (phase) {
                        Phase.IDLE -> IDLE_STEP_MS
                        Phase.ARMING -> ARM_STEP_MS
                        Phase.LAPPING -> LAP_STEP_MS
                        Phase.FADING -> FADE_STEP_MS
                        Phase.BAILING -> BAIL_STEP_MS
                    },
                )
            }
        }
    }

    fun release() {
        player.removeListener(listener)
        player.volume = 1f
        filters.open()
        ghost?.release()
        ghost = null
    }

    // ---- Entry points -------------------------------------------------------

    /**
     * A skip the listener asked for: drop any blend in flight and get out of
     * the way.
     *
     * Crossfade is deliberately a property of tracks *running out*, not of
     * being changed. Blending a manual skip means the song just left behind
     * stays audible over the one that was asked for, which reads as the app
     * ignoring the button rather than as a transition — the point of pressing
     * next is usually to stop hearing the current track.
     *
     * Called before the skip is carried out, so the tail is already on its way
     * down as the new track starts. The listener would catch the resulting seek
     * anyway, but only outside [SELF_MOVE_WINDOW_MS]; a press landing inside
     * that window would be mistaken for the lap's own move and leave the ghost
     * running over the new track. Saying so explicitly closes that gap.
     */
    fun onSkipRequested() {
        if (phase != Phase.IDLE) bail()
    }

    // ---- Ticker -------------------------------------------------------------

    private fun tick() {
        // A pause has to take the tail with it, or the track being faded out
        // carries on alone over a stopped player. Mirrored every tick rather
        // than handled as an event, so audio focus loss, the sleep timer and
        // the pause button all get the same treatment for free.
        if (phase == Phase.ARMING || phase == Phase.LAPPING || phase == Phase.FADING) {
            ghost?.playWhenReady = player.playWhenReady
        }

        when (phase) {
            Phase.IDLE -> considerAutoTransition()
            Phase.ARMING -> driveArming()
            Phase.LAPPING -> driveLap()
            Phase.FADING -> driveFade()
            Phase.BAILING -> driveBail()
        }
    }

    /** Arms a crossfade as the playing track runs out. */
    private fun considerAutoTransition() {
        if (!player.isPlaying) return
        // Repeating one track would crossfade it into itself.
        if (player.repeatMode == Player.REPEAT_MODE_ONE) return
        if (!player.hasNextMediaItem()) return

        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return

        // Smart Fade is its own on/off, independent of the manual crossfade
        // length: it decides its own duration from each pair of tracks (beats,
        // tempo, structure), so requiring a nonzero [AppSettings.crossfadeSeconds]
        // first would tie an automatic feature to a manual one it doesn't use.
        if (AppSettings.smartFadeEnabled.value) {
            considerSmartTransition(duration)
            return
        }

        if (configuredFadeMs() <= 0L) return
        val fade = fadeFor(duration)
        if (fade <= 0L) return

        val remaining = duration - player.currentPosition
        // Arm early: the ghost needs time to spin up and settle into sync
        // before it is any use, and that work has to be finished by the time
        // the fade is due rather than started then.
        if (remaining > fade + ARM_LEAD_MS) return

        begin(fade, endMs = duration, smart = false)
    }

    /**
     * Arms a Smart Fade transition once its plan says the playhead is close
     * enough to start arming for it.
     *
     * Reads the plan's timing (where the fade starts and how long it runs),
     * where the incoming track should be cued
     * ([com.music.bitchord.playback.smart.TransitionPlan.incomingCueTime]),
     * and the tempo-stretch to align it with the outgoing track
     * ([com.music.bitchord.playback.smart.TransitionPlan.incomingPlaybackRate])
     * — see [driveLap], which applies both at the handoff — and the style the
     * blend is rendered in
     * ([com.music.bitchord.playback.smart.TransitionPlan.transitionStyle]),
     * which [rideFilters] turns into a filter ride or a bass swap over the same
     * equal-power gain curve.
     */
    private fun considerSmartTransition(duration: Long) {
        val currentItem = player.currentMediaItem ?: return
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return
        val nextItem = player.getMediaItemAt(nextIndex)

        // Cheap no-ops once a track is analysed or already in flight; called
        // every tick so a track that finishes caching mid-song is picked up
        // without a separate trigger.
        requestAnalysis(currentItem)
        requestAnalysis(nextItem)

        // Only used before analysis lands, or when the evidence is too weak
        // for more than a plain fade (see [TransitionTier.PLAIN_CROSSFADE]):
        // once real analysis is available, [planTransition] sizes the overlap
        // itself from tempo and structure and ignores this entirely. Honours
        // the manual slider if the listener also set one, so the two settings
        // don't fight; falls back to a fixed length when it's at "Off".
        val fallbackSeconds = configuredFadeMs().takeIf { it > 0L }
            ?.div(1000.0)
            ?: DEFAULT_SMART_FALLBACK_SECONDS

        val plan = planTransition(
            analysis = analysisFor(currentItem),
            nextAnalysis = analysisFor(nextItem),
            currentTrack = currentItem.toTransitionInfo(duration),
            nextTrack = nextItem.toTransitionInfo(nextItemDurationMs(nextIndex)),
            currentTime = player.currentPosition / 1000.0,
            duration = duration / 1000.0,
            fadeSeconds = fallbackSeconds,
            mode = CrossfadeMode.SMART,
        )
        // One line per distinct verdict rather than one per 250ms tick, so the
        // log says what the planner decided for this pair without burying it.
        val verdict = "${plan.reason}|${plan.transitionStyle}|fade=${plan.fadeMs}" +
            "|cue=${plan.incomingCueTime}|rate=${plan.incomingPlaybackRate}" +
            "|blocked=${plan.blocked}|policy=${plan.policyReasons.joinToString(",")}"
        if (verdict != lastPlanVerdict) {
            lastPlanVerdict = verdict
            Log.d(
                TAG,
                "plan ${currentItem.mediaId}->${nextItem.mediaId}: $verdict " +
                    "bpm=${analysisFor(currentItem).bpm}/${analysisFor(nextItem).bpm} " +
                    "conf=${analysisFor(currentItem).beatConfidence}/${analysisFor(nextItem).beatConfidence}",
            )
        }

        if (plan.blocked) return

        val fade = plan.fadeMs
        if (fade <= 0L) return

        val transitionStartMs = (plan.transitionStart * 1000).roundToLong()
        val remaining = transitionStartMs - player.currentPosition
        // Same arm-ahead margin as the standard path, just measured against
        // the plan's own start rather than a fixed offset from track end —
        // an analyzed mix-out anchor can place that start well before the
        // file actually ends.
        if (remaining > ARM_LEAD_MS) return

        begin(
            fade,
            endMs = (plan.transitionEnd * 1000).roundToLong(),
            smart = true,
            cueTimeMs = (plan.incomingCueTime * 1000).roundToLong(),
            playbackRate = plan.incomingPlaybackRate,
            renderStyle = Render(
                style = plan.transitionStyle,
                bassSwap = plan.bassSwap,
                bassSwapFraction = plan.bassSwapFraction,
                filterSweep = plan.filterSweep,
            ),
        )
    }

    /** The next queue item's own duration, or 0 when Media3 hasn't loaded that far ahead yet. */
    private fun nextItemDurationMs(nextIndex: Int): Long {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return 0L
        val durationMs = timeline.getWindow(nextIndex, Timeline.Window()).durationMs
        return durationMs.takeIf { it != C.TIME_UNSET } ?: 0L
    }

    /** BitChord doesn't carry album metadata on [MediaMetadata] yet, so [TransitionTrackInfo.album] stays blank. */
    private fun MediaItem.toTransitionInfo(durationMs: Long) = TransitionTrackInfo(
        id = mediaId,
        durationMs = durationMs,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
    )

    /**
     * Spins the ghost up on the outgoing track and walks it into sync with the
     * session player.
     */
    private fun begin(
        fade: Long,
        endMs: Long,
        smart: Boolean,
        cueTimeMs: Long = 0L,
        playbackRate: Double = 1.0,
        renderStyle: Render = Render(),
    ): Boolean {
        val outgoing = player.currentMediaItem ?: return false
        val ghost = warmGhost() ?: return false

        fadeMs = fade
        fadeEndMs = endMs
        smartFadeActive = smart
        incomingCueTimeMs = cueTimeMs.coerceAtLeast(0L)
        incomingPlaybackRate = playbackRate
        render = renderStyle
        armDeadline = SystemClock.elapsedRealtime() + ARM_TIMEOUT_MS
        lastSyncAt = 0L

        // Taken from the session player rather than from settings: these two
        // change how fast a position advances against the wall clock, and the
        // whole handoff rests on the pair agreeing about where they are.
        ghost.skipSilenceEnabled = player.skipSilenceEnabled
        ghost.playbackParameters = player.playbackParameters

        Log.d(
            TAG,
            "arm ${if (smart) "smart" else "standard"} fade=${fade}ms end=${endMs}ms " +
                "cue=${incomingCueTimeMs}ms rate=$incomingPlaybackRate at=${player.currentPosition}ms " +
                "style=${render.style} bassSwap=${render.bassSwap}@${render.bassSwapFraction} " +
                "sweep=${render.filterSweep}",
        )

        ghost.setMediaItem(outgoing)
        ghost.seekTo(player.currentPosition + seekLeadMs)
        ghost.volume = 0f
        ghost.playWhenReady = true
        ghost.prepare()

        phase = Phase.ARMING
        return true
    }

    private fun driveArming() {
        val ghost = ghost ?: return bail()
        if (!stillWorthFading()) return bail()

        val expired = SystemClock.elapsedRealtime() > armDeadline
        val running = ghost.isPlaying

        // A ghost that never got going has no tail to hand the track to, and
        // lapping onto silence would be the very hole this class exists to get
        // rid of. Give up instead and let the track change plainly.
        if (expired && !running) return bail()
        val drift = ghost.currentPosition - player.currentPosition
        val aligned = running && abs(drift) <= SYNC_TOLERANCE_MS

        if (running && !aligned) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastSyncAt >= SYNC_SETTLE_MS) {
                lastSyncAt = now
                // Whatever the last seek overshot or undershot by is exactly
                // what the next one should compensate for, so the lead tunes
                // itself to this device rather than to a guessed constant.
                seekLeadMs = (seekLeadMs - drift).coerceIn(0L, MAX_SEEK_LEAD_MS)
                ghost.seekTo(player.currentPosition + seekLeadMs)
            }
        }

        // Wait for the track to actually reach the fade point. [fadeEndMs] is
        // the track's own duration in standard mode, or a Smart Fade plan's
        // analyzed mix-out anchor when it ends before the file does.
        val atFadePoint = fadeEndMs <= 0L || fadeEndMs - player.currentPosition <= fadeMs

        if (!atFadePoint) return
        // Out of time to keep tidying up: a slightly ragged handoff still beats
        // no crossfade at all.
        if (aligned || expired) startLap()
    }

    private fun startLap() {
        if (ghost == null) return bail()
        lapStartedAt = SystemClock.elapsedRealtime()
        phase = Phase.LAPPING
    }

    /**
     * Hands the outgoing track from the session player to the ghost.
     *
     * Both are rendering the same audio at the same position here, so this is
     * kept as short as it can be while still being a ramp rather than a cut —
     * long enough to swallow any residual misalignment, too short for the two
     * copies to comb against each other audibly.
     */
    private fun driveLap() {
        val ghost = ghost ?: return bail()
        // Pausing in the ninety milliseconds it takes to hand the track over
        // means nothing has been handed over yet: the session player still has
        // the track, so give it back rather than advancing a queue the listener
        // has just stopped.
        if (!player.playWhenReady) return bail()

        val progress = (SystemClock.elapsedRealtime() - lapStartedAt).toFloat() / LAP_MS

        if (progress < 1f) {
            player.volume = fallGain(progress)
            ghost.volume = riseGain(progress)
            return
        }

        ghost.volume = 1f
        player.volume = 0f
        // The ghost has the old track. The session player is free to become the
        // new one — and everything hanging off it (queue index, metadata, the
        // notification, the UI) moves to the incoming song right here, while
        // its first note is still fading up.
        // Only a track running out ever gets this far, so the queue moving
        // here is always the crossfade standing in for a track ending.
        autoAdvance = true
        selfMoveUntil = SystemClock.elapsedRealtime() + SELF_MOVE_WINDOW_MS
        // A Smart Fade plan cues the incoming track to its own analyzed mix-in
        // point rather than 0 — landing on the beat grid, not the file's cold
        // open — so this seeks straight to that position in the same call
        // that moves the queue forward, instead of using
        // [Player.seekToNextMediaItem] (which always lands on 0) and then
        // correcting with a second seek that would itself be visible as a
        // discontinuity.
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex != C.INDEX_UNSET && incomingCueTimeMs > 0L) {
            player.seekTo(nextIndex, incomingCueTimeMs)
        } else {
            player.seekToNextMediaItem()
        }
        // Stacks on top of the listener's own speed control rather than
        // replacing it, so a beatmatched transition and "play everything at
        // 1.25x" don't fight each other. Restored in [finish].
        if (incomingPlaybackRate != 1.0) {
            player.setPlaybackSpeed((AppSettings.playbackSpeed.value * incomingPlaybackRate).toFloat())
        }
        phase = Phase.FADING
    }

    /**
     * The crossfade proper.
     *
     * Driven off the *incoming* track's position rather than off a clock, so a
     * pause parks the transition where it stands and resuming picks it back up
     * — no timer to reconcile, and no ghost left hanging at half volume while
     * the session player waits.
     */
    private fun driveFade() {
        val ghost = ghost ?: return bail()
        // The incoming track gets the same say over the length as the outgoing
        // one did, so a long crossfade into a short track tightens rather than
        // swallowing it. Its duration is often still unknown when the fade
        // starts — the stream is only being opened — so this is read every tick
        // and simply narrows the span once the answer arrives. Capped only by
        // the incoming track's own length, not by [configuredFadeMs] — a Smart
        // Fade plan already sized itself independently of that setting, and
        // may be running with it at zero.
        // Measured from where the incoming track was *cued*, not from zero. A
        // Smart Fade plan can drop it in mid-arrangement, and reading its raw
        // position as elapsed-fade would put a cue at 0:45 instantly past the
        // end of an 8-second fade — finishing the blend on its first tick and
        // landing as an abrupt cut, which is precisely the failure a cued
        // transition is supposed to avoid.
        val remainingIncoming = player.duration
            .takeIf { it != C.TIME_UNSET && it > 0L }
            ?.minus(incomingCueTimeMs)
            ?.coerceAtLeast(0L)
        val incomingCap = remainingIncoming?.div(3) ?: Long.MAX_VALUE
        val span = (minOf(fadeMs, incomingCap) - LAP_MS).coerceAtLeast(1L)
        val elapsed = (player.currentPosition - incomingCueTimeMs).coerceAtLeast(0L)
        val progress = (elapsed.toFloat() / span).coerceIn(0f, 1f)

        player.volume = riseGain(progress)
        ghost.volume = fallGain(progress)
        // Only here, never during ARMING or LAPPING: those two phases have both
        // players rendering the *same* audio at the same position, and filtering
        // one copy and not the other would comb them against each other. From
        // FADING onwards the session player is the incoming track and the ghost
        // is the outgoing one, which is exactly the split [filters] describes.
        rideFilters(progress)

        // Whichever comes first: the fade running its course, the old track
        // genuinely ending, the tail failing outright, or whichever setting
        // armed this fade being switched off mid-blend. Checked against the
        // setting that actually started it — a Smart Fade normally runs with
        // [configuredFadeMs] at zero, and reading that as "turned off" would
        // end every Smart Fade on its first tick.
        val settingSwitchedOff = if (smartFadeActive) {
            !AppSettings.smartFadeEnabled.value
        } else {
            configuredFadeMs() <= 0L
        }
        val done = progress >= 1f ||
            ghost.playbackState == Player.STATE_ENDED ||
            ghost.playbackState == Player.STATE_IDLE ||
            settingSwitchedOff
        if (done) finish()
    }

    /** Ramps the ghost out rather than cutting it, so an interruption has no click in it. */
    private fun driveBail() {
        val ghost = ghost
        if (ghost == null) {
            finish()
            return
        }
        val progress = (SystemClock.elapsedRealtime() - bailStartedAt).toFloat() / BAIL_MS
        if (progress < 1f) {
            ghost.volume = bailFromGain * fallGain(progress)
            return
        }
        finish()
    }

    // ---- Lifecycle of a transition -----------------------------------------

    /**
     * Abandons whatever is in flight. Safe to call from anywhere, at any phase:
     * the session player is always the one holding the queue, so there is never
     * a half-applied state to put back — only a ghost to fade out and a volume
     * to restore.
     */
    private fun bail() {
        if (phase == Phase.IDLE || phase == Phase.BAILING) return
        Log.d(TAG, "bail from $phase")
        player.volume = 1f
        // Glided open rather than snapped: the session player is still audible
        // here, and if the bail caught a bass swap mid-handover its low end is
        // currently lifted out. Dropping a 24 dB/octave filter in one buffer is
        // the click this ramp exists to avoid.
        filters.open()
        autoAdvance = false
        bailFromGain = ghost?.volume ?: 0f
        bailStartedAt = SystemClock.elapsedRealtime()
        phase = Phase.BAILING
    }

    private fun finish() {
        if (phase != Phase.IDLE) Log.d(TAG, "finish from $phase")
        player.volume = 1f
        // Unconditional and idempotent, like the speed reset below: correct
        // whether or not this transition ever filtered anything.
        filters.open()
        render = Render()
        // Undoes whatever [driveLap] stacked on for a beatmatched handoff —
        // unconditional and idempotent, so this is correct whether or not a
        // stretch was ever actually applied (a standard fade, or a Smart Fade
        // that never reached FADING, both leave the listener's own speed
        // control untouched anyway).
        player.setPlaybackSpeed(AppSettings.playbackSpeed.value)
        incomingPlaybackRate = 1.0
        ghost?.let {
            it.volume = 0f
            it.stop()
            it.clearMediaItems()
        }
        selfMoveUntil = 0L
        phase = Phase.IDLE
    }

    /** Still a next track, still playing, still switched on — by whichever setting armed this one. */
    private fun stillWorthFading(): Boolean {
        val stillOn = if (smartFadeActive) AppSettings.smartFadeEnabled.value else configuredFadeMs() > 0L
        return stillOn && player.hasNextMediaItem()
    }

    private fun warmGhost(): ExoPlayer? {
        ghost?.let { return it }
        return runCatching { newGhost() }.getOrNull()?.also { ghost = it }
    }

    // ---- Numbers ------------------------------------------------------------

    private fun configuredFadeMs(): Long = AppSettings.crossfadeSeconds.value * 1000L

    /**
     * The configured length, kept off tracks too short to spend it on. A fade
     * that swallows a third of a song stops being a transition and starts being
     * the arrangement.
     */
    private fun fadeFor(duration: Long): Long {
        val configured = configuredFadeMs()
        if (duration == C.TIME_UNSET || duration <= 0L) return configured
        return minOf(configured, duration / 3).coerceAtLeast(0L)
    }

    /**
     * Renders the plan's [TransitionStyle] as filtering across the blend.
     *
     * The gain curve is the same equal-power pair for every style — this is
     * what makes them sound different from each other, and it is the whole of
     * Phase 4. Driven off the same `progress` as the gains so the two stay
     * locked: a pause parks the filter exactly where it parks the fade.
     */
    private fun rideFilters(progress: Float) {
        when (render.style) {
            TransitionStyle.DJ_FILTER -> rideFilterSweep(progress)
            TransitionStyle.DJ_BLEND ->
                if (render.bassSwap) rideBassSwap(progress) else filters.open()
            // Both of these are defined as "don't touch the spectrum".
            // EQUAL_POWER is the bottom tier, reached because the evidence was
            // too weak to justify anything more opinionated; GAPLESS is an album
            // being played through, where any filtering would be an edit the
            // record didn't ask for.
            TransitionStyle.EQUAL_POWER, TransitionStyle.GAPLESS -> filters.open()
        }
    }

    /**
     * Pulls the outgoing track behind a closing low-pass, for a pair too far
     * apart in tempo to blend flat.
     *
     * Squared rather than linear because the fade and the filter are two things
     * happening to the same track at once: a cutoff falling linearly in octaves
     * is already dark by the halfway mark, and stacked on a gain that is also
     * falling it takes the outgoing track out well before the incoming one has
     * established itself. Holding the top end open through the first half and
     * spending the sweep in the second is the ride a DJ actually performs.
     */
    private fun rideFilterSweep(progress: Float) {
        val sweep = render.filterSweep.coerceIn(0.0, 1.0)
        if (sweep <= 0.0) {
            filters.open()
            return
        }
        // In octaves, because a sweep only sounds even if the cutoff halves at a
        // constant rate. Scaled by [filterSweep] so a partial sweep stops short
        // of the floor rather than crawling the same distance.
        val travel = FILTER_SWEEP_OCTAVES * sweep * progress * progress
        val cutoff = (TransitionFilterProcessor.OPEN_HZ * 2.0.pow(-travel))
            .coerceAtLeast(FILTER_FLOOR_HZ)
        filters.outgoing(cutoff.toFloat(), TransitionFilterProcessor.OFF_HZ)
        filters.incoming(TransitionFilterProcessor.OPEN_HZ, TransitionFilterProcessor.OFF_HZ)
    }

    /**
     * Hands the low end from one track to the other, once, at the beat the
     * planner chose.
     *
     * Both tracks keep their full spectrum except below [BASS_SWAP_HZ], where
     * exactly one of them is present at any instant: the incoming track arrives
     * with its low end lifted out, and takes it over as the outgoing track's is
     * lifted in turn. Ramped over [BASS_SWAP_WIDTH] of the fade rather than
     * switched, because a 24 dB/octave filter appearing in one buffer is a
     * transient of its own.
     */
    private fun rideBassSwap(progress: Float) {
        val swapAt = render.bassSwapFraction.coerceIn(0.05, 0.95)
        // 0 before the swap window, 1 after it: how much of the low end has
        // changed hands.
        val handover = ((progress - swapAt) / BASS_SWAP_WIDTH * 0.5 + 0.5).coerceIn(0.0, 1.0)
        filters.incoming(TransitionFilterProcessor.OPEN_HZ, bassCutoff(1.0 - handover))
        filters.outgoing(TransitionFilterProcessor.OPEN_HZ, bassCutoff(handover))
    }

    /** [amount] 0 leaves the low end alone; 1 lifts it out entirely. */
    private fun bassCutoff(amount: Double): Float {
        if (amount <= 0.0) return TransitionFilterProcessor.OFF_HZ
        val off = TransitionFilterProcessor.OFF_HZ.toDouble()
        return (off * (BASS_SWAP_HZ / off).pow(amount)).toFloat()
    }

    /** Equal-power pair: [riseGain]² + [fallGain]² = 1, so the blend never dips. */
    private fun riseGain(progress: Float): Float =
        sin(progress.coerceIn(0f, 1f) * PI.toFloat() / 2f)

    private fun fallGain(progress: Float): Float =
        cos(progress.coerceIn(0f, 1f) * PI.toFloat() / 2f)

    private companion object {
        const val TAG = "BitChordCrossfade"

        /**
         * Used only before a pair has been analysed, or when the evidence is
         * too weak for more than a plain fade — see [considerSmartTransition].
         * Once real analysis lands, the overlap is sized from tempo and
         * structure instead and this is never read.
         */
        const val DEFAULT_SMART_FALLBACK_SECONDS = 6.0

        /** Handoff of the outgoing track between the two players. */
        const val LAP_MS = 90L

        /** Ramp used when a fade is interrupted. */
        const val BAIL_MS = 120L

        /** Head start the ghost gets to spin up and settle into sync. */
        const val ARM_LEAD_MS = 2_000L

        /** How closely the two players must agree on position before the lap. */
        const val SYNC_TOLERANCE_MS = 20L

        /** Time a sync correction is given to land before it is judged. */
        const val SYNC_SETTLE_MS = 150L

        const val MAX_SEEK_LEAD_MS = 500L

        /** Longest the lap will wait on a ghost that won't sync. */
        const val ARM_TIMEOUT_MS = 4_000L

        /** How long the lap's own seek stays recognisable as ours. */
        const val SELF_MOVE_WINDOW_MS = 150L

        /**
         * How far a full filter ride travels, in octaves down from
         * [TransitionFilterProcessor.OPEN_HZ]. Lands on [FILTER_FLOOR_HZ].
         */
        const val FILTER_SWEEP_OCTAVES = 6.1

        /**
         * The bottom of a filter ride. Below a few hundred hertz a track stops
         * reading as "further away" and starts reading as "broken", which is not
         * the impression a transition should leave of the song being left.
         */
        const val FILTER_FLOOR_HZ = 300.0

        /**
         * Where the low end is considered to end. Around the fundamental of a
         * bass guitar's upper register, and the usual corner on a mixer's bass
         * kill — high enough to clear the kick and the sub, low enough to leave
         * the body of the vocal alone.
         */
        const val BASS_SWAP_HZ = 200.0

        /** How much of the fade the low end takes to change hands. */
        const val BASS_SWAP_WIDTH = 0.10

        const val IDLE_STEP_MS = 250L
        const val ARM_STEP_MS = 40L
        const val LAP_STEP_MS = 10L
        const val FADE_STEP_MS = 30L
        const val BAIL_STEP_MS = 15L
    }
}
