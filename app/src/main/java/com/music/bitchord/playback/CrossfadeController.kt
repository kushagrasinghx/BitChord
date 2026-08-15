package com.music.bitchord.playback

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
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

    /** Only for auto transitions: hold the lap until the track is really this close to ending. */
    private var lapWhenRemainingMs = Long.MAX_VALUE

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
     * Whether a press of "next" is currently being held back waiting for the
     * ghost. [interceptSkipToNext] tells the session player the skip is in hand,
     * so if the blend then falls through it still has to happen — otherwise the
     * button quietly does nothing at all.
     */
    private var owedSkip = false

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
        ghost?.release()
        ghost = null
    }

    // ---- Entry points -------------------------------------------------------

    /**
     * Takes over a skip so it crossfades instead of cutting.
     *
     * Pressing "next" is how anyone actually tests this setting, and the old
     * implementation deliberately did nothing on a manual skip — which is most
     * of why it read as broken. Both Spotify and Apple Music blend a manual
     * skip, so this does too.
     *
     * @return true when the crossfade has the skip in hand and the caller
     *   should not also move the queue itself.
     */
    fun interceptSkipToNext(): Boolean {
        // A second press during a fade means "get on with it": drop the blend
        // and let the plain skip through.
        if (phase != Phase.IDLE) {
            bail()
            return false
        }
        if (configuredFadeMs() <= 0L) return false
        if (player.playbackState == Player.STATE_IDLE) return false
        // Skipping while paused should land on the next track, not start a
        // fade nobody is listening to.
        if (!player.playWhenReady) return false
        if (!player.hasNextMediaItem()) return false

        val duration = player.duration
        val remaining = if (duration == C.TIME_UNSET) Long.MAX_VALUE else duration - player.currentPosition
        // Skipping into the last moment of a track has nothing left to fade out.
        if (remaining < MIN_TAIL_MS) return false

        return begin(fade = fadeFor(duration), manual = true)
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
        if (configuredFadeMs() <= 0L) return
        if (!player.isPlaying) return
        // Repeating one track would crossfade it into itself.
        if (player.repeatMode == Player.REPEAT_MODE_ONE) return
        if (!player.hasNextMediaItem()) return

        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return

        val fade = fadeFor(duration)
        if (fade <= 0L) return

        val remaining = duration - player.currentPosition
        // Arm early: the ghost needs time to spin up and settle into sync
        // before it is any use, and that work has to be finished by the time
        // the fade is due rather than started then.
        if (remaining > fade + ARM_LEAD_MS) return

        lapWhenRemainingMs = fade
        begin(fade, manual = false)
    }

    /**
     * Spins the ghost up on the outgoing track and walks it into sync with the
     * session player.
     */
    private fun begin(fade: Long, manual: Boolean): Boolean {
        val outgoing = player.currentMediaItem ?: return false
        val ghost = warmGhost() ?: return false

        fadeMs = fade
        armDeadline = SystemClock.elapsedRealtime() +
            if (manual) MANUAL_ARM_TIMEOUT_MS else AUTO_ARM_TIMEOUT_MS
        lastSyncAt = 0L
        owedSkip = manual
        if (manual) lapWhenRemainingMs = Long.MAX_VALUE

        // Taken from the session player rather than from settings: these two
        // change how fast a position advances against the wall clock, and the
        // whole handoff rests on the pair agreeing about where they are.
        ghost.skipSilenceEnabled = player.skipSilenceEnabled
        ghost.playbackParameters = player.playbackParameters

        ghost.setMediaItem(outgoing)
        ghost.seekTo(player.currentPosition + seekLeadMs)
        ghost.volume = 0f
        ghost.playWhenReady = true
        ghost.prepare()

        phase = Phase.ARMING
        return true
    }

    private fun driveArming() {
        val ghost = ghost ?: return giveUp()
        if (!stillWorthFading()) return giveUp()

        val expired = SystemClock.elapsedRealtime() > armDeadline
        val running = ghost.isPlaying

        // A ghost that never got going has no tail to hand the track to, and
        // lapping onto silence would be the very hole this class exists to get
        // rid of. Give up instead and let the track change plainly.
        if (expired && !running) return giveUp()
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

        // Auto transitions wait for the track to actually reach the fade point;
        // a manual skip is due the moment the ghost can carry the tail.
        val duration = player.duration
        val atFadePoint = duration == C.TIME_UNSET ||
            duration - player.currentPosition <= lapWhenRemainingMs

        if (!atFadePoint) return
        // Out of time to keep tidying up: a slightly ragged handoff still beats
        // no crossfade at all.
        if (aligned || expired) startLap()
    }

    private fun startLap() {
        if (ghost == null) return giveUp()
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
        val ghost = ghost ?: return giveUp()
        // Pausing in the ninety milliseconds it takes to hand the track over
        // means nothing has been handed over yet: the session player still has
        // the track, so give it back rather than advancing a queue the listener
        // has just stopped.
        if (!player.playWhenReady) return giveUp()

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
        autoAdvance = lapWhenRemainingMs != Long.MAX_VALUE
        owedSkip = false
        selfMoveUntil = SystemClock.elapsedRealtime() + SELF_MOVE_WINDOW_MS
        player.seekToNextMediaItem()
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
        // and simply narrows the span once the answer arrives.
        val span = (minOf(fadeMs, fadeFor(player.duration)) - LAP_MS).coerceAtLeast(1L)
        val progress = (player.currentPosition.coerceAtLeast(0L).toFloat() / span).coerceIn(0f, 1f)

        player.volume = riseGain(progress)
        ghost.volume = fallGain(progress)

        // Whichever comes first: the fade running its course, the old track
        // genuinely ending, the tail failing outright, or the setting being
        // switched off mid-blend.
        val done = progress >= 1f ||
            ghost.playbackState == Player.STATE_ENDED ||
            ghost.playbackState == Player.STATE_IDLE ||
            configuredFadeMs() <= 0L
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
     * Abandons the blend but still does what the listener asked for.
     *
     * Only for the crossfade deciding against itself — a ghost that won't
     * start, the setting going off mid-arm. A fade cut short by the listener
     * seeking or queueing something else goes through [bail] instead, since
     * they have already moved on and a deferred skip would be one track too
     * many.
     */
    private fun giveUp() {
        val owed = owedSkip
        bail()
        if (!owed) return
        selfMoveUntil = SystemClock.elapsedRealtime() + SELF_MOVE_WINDOW_MS
        player.seekToNextMediaItem()
    }

    /**
     * Abandons whatever is in flight. Safe to call from anywhere, at any phase:
     * the session player is always the one holding the queue, so there is never
     * a half-applied state to put back — only a ghost to fade out and a volume
     * to restore.
     */
    private fun bail() {
        owedSkip = false
        if (phase == Phase.IDLE || phase == Phase.BAILING) return
        player.volume = 1f
        autoAdvance = false
        bailFromGain = ghost?.volume ?: 0f
        bailStartedAt = SystemClock.elapsedRealtime()
        phase = Phase.BAILING
    }

    private fun finish() {
        player.volume = 1f
        ghost?.let {
            it.volume = 0f
            it.stop()
            it.clearMediaItems()
        }
        lapWhenRemainingMs = Long.MAX_VALUE
        selfMoveUntil = 0L
        phase = Phase.IDLE
    }

    /** Still a next track, still playing, still switched on. */
    private fun stillWorthFading(): Boolean =
        configuredFadeMs() > 0L && player.hasNextMediaItem()

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

    /** Equal-power pair: [riseGain]² + [fallGain]² = 1, so the blend never dips. */
    private fun riseGain(progress: Float): Float =
        sin(progress.coerceIn(0f, 1f) * PI.toFloat() / 2f)

    private fun fallGain(progress: Float): Float =
        cos(progress.coerceIn(0f, 1f) * PI.toFloat() / 2f)

    private companion object {
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
        const val AUTO_ARM_TIMEOUT_MS = 4_000L

        /** Shorter for a skip: past this, the press stops feeling connected to anything. */
        const val MANUAL_ARM_TIMEOUT_MS = 450L

        /** Below this there is no tail worth handing to the ghost. */
        const val MIN_TAIL_MS = 750L

        /** How long the lap's own seek stays recognisable as ours. */
        const val SELF_MOVE_WINDOW_MS = 150L

        const val IDLE_STEP_MS = 250L
        const val ARM_STEP_MS = 40L
        const val LAP_STEP_MS = 10L
        const val FADE_STEP_MS = 30L
        const val BAIL_STEP_MS = 15L
    }
}
