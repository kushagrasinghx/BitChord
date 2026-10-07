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
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.MixBlend
import com.music.bitchord.data.settings.SmartAnalysis
import com.music.bitchord.data.settings.TrackAnalysisState
import com.music.bitchord.data.settings.TransitionWindow
import com.music.bitchord.playback.smart.CrossfadeMode
import com.music.bitchord.playback.smart.FILTER_ECHO_AT
import com.music.bitchord.playback.smart.FilterTransitionVariant
import com.music.bitchord.playback.smart.TrackAnalysis
import com.music.bitchord.playback.smart.TransitionStyle
import com.music.bitchord.playback.smart.TransitionTrackInfo
import com.music.bitchord.playback.smart.echoTailSeconds
import com.music.bitchord.playback.smart.planTransition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import java.util.Locale

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
 * Two peers, not a player and a helper. Both are full ExoPlayers built the same
 * way and both can own the queue; at any instant one of them *is* the session
 * (it backs the MediaSession, holds audio focus and carries the notification)
 * and the other is idle. They swap roles at every transition.
 *
 *  - **[active]** — whichever player the session currently points at. The rest
 *    of the app only ever sees this one.
 *  - **[standby]** — the idle player. Between transitions it holds nothing. To
 *    arm a transition it is loaded with *the queue, positioned on the incoming
 *    track* at the plan's cue point, and started silently.
 *
 * The crucial word is **incoming**. An earlier version of this class put the
 * *outgoing* track on the second player: the session player jumped ahead to the
 * next song and the second player carried the old song's tail. That works, but
 * it forces a moment where both players render *the same audio*, and two
 * ExoPlayers cannot be started sample-accurately against each other. Whatever
 * they were misaligned by — measured on real transitions at 9 to 41ms — was
 * heard as the last instant of the outgoing track playing twice, at the head of
 * every single crossfade. No amount of tuning removes that; the duplication is
 * structural.
 *
 * Loading the *incoming* track on the standby removes it outright. The two
 * players never hold the same audio, so there is nothing to align, nothing to
 * hand over, and no seam to hide. The incoming track is simply already playing,
 * from exactly the right position, when its fader starts to move.
 *
 * ## The handoff
 *
 * Because both players own the queue, finishing a transition is a **role swap**
 * rather than a seek: nothing is re-buffered, nothing is re-sought, and no audio
 * is rendered twice. [onHandoff] is what performs it — the service moves the
 * MediaSession, audio focus, its listeners and its bookkeeping onto the incoming
 * player.
 *
 * It fires halfway through the blend — [HANDOFF_AT] — where the equal-power
 * curve makes the two tracks equally loud and the incoming one takes over. The
 * queue index, the metadata, the notification and the UI flip to the incoming
 * song there: not at its first, barely audible note, and not trailing the song
 * on its way out. Before it the incoming player is a silent-then-rising
 * shadow of the session; after it [outgoing] is the idle player, still audible,
 * being faded out.
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
    /** The player backing the session right now. Moves at every [onHandoff]. */
    private val active: () -> ExoPlayer,
    /** The idle player, which the next transition will load the incoming track onto. */
    private val standby: () -> ExoPlayer,
    /**
     * Moves the session onto the player that has just started the incoming
     * track: the MediaSession's player, audio focus, the service's listeners and
     * everything it books against a track change.
     *
     * Called once per transition, halfway through the blend. After it returns, [active] must answer `incoming` and [standby]
     * must answer `outgoing` — this class re-reads neither during a transition,
     * but everything else in the service does.
     */
    private val onHandoff: (outgoing: ExoPlayer, incoming: ExoPlayer) -> Unit,
    /**
     * Stored Automix analysis for a media item, or an empty [TrackAnalysis]
     * when there is none yet. This is the seam Phase 1's DSP analyzer plugs
     * into: until analysis finishes, a track reads as "no evidence", which
     * [planTransition] answers with the same fixed-length crossfade this
     * class always ran before Automix existed.
     */
    private val analysisFor: (MediaItem) -> TrackAnalysis = { TrackAnalysis() },
    /**
     * Queues background analysis for a media item that will soon need it.
     * Cheap to call on every tick: a track already analysed, already in
     * flight, or not yet fully cached is a no-op.
     *
     * Takes the item's duration in milliseconds, or 0 when Media3 hasn't loaded
     * that far ahead yet. The analyzer needs it to tell one rendition of a
     * recording from a differently-cut one before reusing an analysis across
     * them, and this class is the only place that already knows it.
     */
    private val requestAnalysis: (MediaItem, Long) -> Unit = { _, _ -> },
    /**
     * The low-pass and high-pass riding each side of a transition. This is what
     * makes a plan's
     * [com.music.bitchord.playback.smart.TransitionPlan.transitionStyle] audible
     * rather than advisory: see [rideFilters]. Defaults to
     * [TransitionFilters.None], which renders every style as the plain
     * equal-power blend this class ran before.
     */
    private val filters: TransitionFilters = TransitionFilters.None,
    /**
     * Whether a decode and inference for a media item is running right now.
     * Only feeds the stats line — nothing about a transition waits on it.
     */
    private val analysisRunningFor: (MediaItem) -> Boolean = { false },
    /**
     * The track about to be loaded onto the standby player, announced at the
     * moment [begin] arms rather than at the handoff.
     *
     * The timing is the entire point. Anything keyed to the *incoming* track
     * that lives on the standby player's audio path has to be set before that
     * player renders a frame, and the handoff happens after the incoming track
     * is already audible. Loudness normalization is the case that motivated
     * it: the standby's processor has to be told which song it is about to
     * level, or it spends the first half of the blend applying the outgoing
     * track's gain to the incoming one.
     *
     * Also given the media id of whatever follows the incoming track on the
     * standby's queue, so that player's per-track state can follow it across
     * a later gapless boundary without waiting on the service.
     */
    private val onArmIncoming: (MediaItem, String?) -> Unit = { _, _ -> },
    /**
     * A smooth headroom trim for both players while two tracks overlap:
     * `1 / sqrt(incomingGain + outgoingGain)`, 1 otherwise.
     *
     * Equal-power gains sum to more than 1 — up to 1.41 at the midpoint — so
     * two loud masters peaking together pass full scale in the platform mixer
     * and are hard-clipped into a crackle. The trim holds that to 1.19 at a
     * cost of at most 1.5 dB mid-blend. A gain rather than a limiter on
     * purpose — see [LoudnessProcessor].
     */
    private val onBlendHeadroom: (Float) -> Unit = {},
    /**
     * True while the service is mid-swap between two versions/cuts of the
     * current track. That swap fades across the same active/standby pair
     * this controller does, so the two must never run at once — arming a
     * transition here would tear the standby player away from the version
     * swap that already owns it, and the other direction is guarded
     * symmetrically where the swap starts. Checked once at the top of
     * [considerAutoTransition] rather than the pieces inside it, since
     * [Phase.IDLE] is the only phase that can still be preempted for free.
     */
    private val versionSwapActive: () -> Boolean = { false },
) {

    private enum class Phase {
        /** Nothing in flight; watching for the next transition. */
        IDLE,

        /**
         * The standby player is loading the incoming track and buffering to its
         * cue point. Silent, and nothing has been committed: abandoning here
         * costs only the standby's decoder.
         */
        ARMING,

        /** Incoming track rising on one player, outgoing falling on the other. */
        FADING,

        /**
         * The sleep timer is due at this track's end: the outgoing track is
         * being faded to silence on its own, over the same span an ordinary
         * transition would have used, but nothing is being loaded to follow
         * it. See [beginSleepFadeOut].
         */
        SLEEP_FADE,

        /** Something interrupted the fade; the outgoing track is being ramped away. */
        BAILING,
    }

    private var phase = Phase.IDLE

    /**
     * The player the session was on when this transition began — the one whose
     * track is being left. Held explicitly rather than re-read through
     * [standby], because [onHandoff] moves it out from under that name halfway
     * through the fade and the ramp has to keep driving the same two players it
     * started with.
     */
    private var outgoing: ExoPlayer? = null

    /** The player carrying the track arriving. Becomes the session at [onHandoff]. */
    private var incoming: ExoPlayer? = null

    /**
     * Whether [onHandoff] has run for the transition in flight, which is what
     * decides who owns what if it has to be unwound: before it, [outgoing] is
     * the session and [incoming] is a silent scratch player; after it, they have
     * traded places.
     */
    private var handedOff = false

    /**
     * How many items the queue held when the standby was loaded with a copy of
     * it. AutoPlay appending mid-transition is explicitly allowed, so the
     * difference is reconciled onto the standby before the swap rather than
     * being allowed to lose the appended tracks — see [reconcileQueue].
     */
    private var queuedItemCount = 0

    /**
     * The outgoing player, while it is held at the end of its own track — see
     * [holdAtEnd]. Null when nothing is held.
     */
    private var heldAtEnd: ExoPlayer? = null

    /** Which player this class's own listener is currently attached to. */
    private var listeningTo: ExoPlayer? = null
    private var tickerJob: Job? = null

    /** Length of the transition in flight, in ms. Fixed when it begins. */
    private var fadeMs = 0L

    /**
     * Where the fade window ends, in the session player's position ms.
     * Standard mode sets this to the track's own duration, which is what
     * [driveArming] always compared against before Automix existed; a
     * Automix plan can set it earlier, at an analyzed mix-out anchor, so
     * [driveArming] watches this field rather than re-deriving the fade point
     * from [ExoPlayer.getDuration] on every tick.
     */
    private var fadeEndMs = 0L

    /**
     * Which setting armed the fade in flight, so [driveFade] knows which one
     * being switched off mid-blend means "stop now" rather than misreading the
     * other mode's control as the fade having been turned off. Automix
     * doesn't need [AppSettings.crossfadeSeconds] to be above zero at all —
     * see [considerSmartTransition] — so treating that as still-zero as a
     * reason to cut a Automix short would end every one of them on its
     * first tick.
     */
    private var smartFadeActive = false

    /**
     * Where the incoming track is cued when the lap hands the queue over, in
     * its own timeline ms. Standard fades always leave this at 0 — a plain
     * track change starts from the top — and only a Automix plan sets it
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
     * Advanced Automix: the speed-up the outgoing track is brought up to before
     * the blend, when it is the slower of the pair — see [rampOutgoing]. 1.0
     * otherwise, and always 1.0 classically.
     */
    private var outgoingPlaybackRate: Double = 1.0

    /** How much faster the incoming timeline runs than the outgoing one through the blend. */
    private val mediaRatio: Double get() = incomingPlaybackRate / outgoingPlaybackRate

    /** The outgoing speed-up ramp: steps in all, one beat of outgoing media each, from [rampStartMs]. */
    private var rampSteps = 0
    private var rampStepMs = 0L
    private var rampStartMs = 0L
    private var rampStep = 0

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
     *
     * Every fraction the ride reads is resolved here, once per transition,
     * rather than on each 30ms fade tick. With [advanced] off they come out as
     * the classic constants; with it on and a trusted grid, each is moved onto
     * the nearest beat or bar of the overlap, so the bass swap, the filters
     * opening and the echo all land where a DJ would put them.
     */
    private class Render(
        val style: TransitionStyle = TransitionStyle.EQUAL_POWER,
        val bassSwap: Boolean = false,
        bassSwapFraction: Double = 0.7,
        val filterSweep: Double = 0.0,
        val filterVariant: FilterTransitionVariant = FilterTransitionVariant.SWEEP,
        val vocalOverlap: Double = 0.0,
        /** Advanced Automix: DJ fader curves, moves on the beat, phase lock, tempo ease-back. */
        val advanced: Boolean = false,
        /** The overlap's length in outgoing beats; 0 when the grid is not trusted. */
        beats: Double = 0.0,
        lockPhase: Boolean = false,
        /** The plan's echo time; 0 for no echo out. */
        echoSeconds: Double = 0.0,
    ) {
        private val grid = if (advanced && beats >= 2.0) beats else 0.0
        val clash = vocalOverlap.coerceIn(0.0, 1.0)

        /** Echo the outgoing track out rather than fading or filtering it away. Needs the grid it repeats on. */
        val echo = grid > 0 && echoSeconds > 0.0
        val echoSecondsF = echoSeconds.toFloat()

        /** How long the repeats ring after the dry signal goes, in ms. */
        val echoTailMs = (echoTailSeconds(echoSeconds) * 1000).toFloat()

        /** Hold the two beats in phase by nudging the incoming track's speed. */
        val phaseLock = advanced && lockPhase

        val swapAt = onBeat(bassSwapFraction.coerceIn(0.05, 0.95))

        /** The low end changes hands over half a beat that ends on [swapAt]; classically, centred on it. */
        val swapWidth = if (grid > 0) SWAP_BEATS / grid else 2 * BASS_SWAP_WIDTH
        val swapFrom = if (grid > 0) swapAt - swapWidth else swapAt - BASS_SWAP_WIDTH
        val entryOpenBy = onBar(
            when (filterVariant) {
                FilterTransitionVariant.SWEEP -> ENTRY_OPEN_BY
                FilterTransitionVariant.BASS_HANDOFF -> 0.38
                FilterTransitionVariant.ECHO_RIDE -> 0.52
            },
        )
        val vocalEntryOpenBy = onBar(entryOpenBy + (VOCAL_SAFE_ENTRY_OPEN_BY - entryOpenBy) * clash)
        val blendEntryOpenBy = onBar(BLEND_ENTRY_OPEN_BY + (BLEND_ENTRY_CLASH_OPEN_BY - BLEND_ENTRY_OPEN_BY) * clash)
        val blendExitFrom = onBar(BLEND_EXIT_FROM + (BLEND_EXIT_CLASH_FROM - BLEND_EXIT_FROM) * clash)
        val filterSwapAt = onBar(
            when (filterVariant) {
                FilterTransitionVariant.SWEEP -> FILTER_BASS_SWAP_AT
                FilterTransitionVariant.BASS_HANDOFF -> 0.43
                FilterTransitionVariant.ECHO_RIDE -> 0.58
            },
        )
        val filterSwapAtF = filterSwapAt.toFloat()


        /**
         * Where the dry outgoing signal is killed and only its echo goes on:
         * the filter ride's bass handover. The send opens
         * across the beat before it, so what repeats is that one beat — the
         * DJ move of hitting echo on the last beat and pulling the fader.
         */
        val echoAt = onBar(FILTER_ECHO_AT)
        val echoFrom = if (grid > 0) echoAt - 1.0 / grid else echoAt
        val echoKill = if (grid > 0) ECHO_KILL_BEATS / grid else 0.0

        /**
         * How much of the DJ fader curve a blend keeps: all of it for two tracks
         * that don't sing over each other, none — plain equal power — for a
         * head-on vocal collision, where both faders up would only be louder.
         */
        val holdFaders = (1.0 - clash).toFloat()
        val swapAtF = swapAt.toFloat()

        /**
         * Where the session moves to the incoming track. Advanced: when it
         * actually takes over — the bass swap in a blend.
         */
        val handoffAt = when {
            !advanced -> HANDOFF_AT
            style == TransitionStyle.DJ_BLEND && bassSwap -> swapAtF
            style == TransitionStyle.DJ_FILTER && filterVariant == FilterTransitionVariant.BASS_HANDOFF -> filterSwapAtF
            else -> HANDOFF_AT
        }

        private fun onBeat(fraction: Double): Double =
            if (grid > 0) (round(fraction * grid) / grid).coerceIn(1.0 / grid, 1.0 - 1.0 / grid) else fraction

        private fun onBar(fraction: Double): Double =
            if (grid >= 8.0) {
                (round(fraction * grid / 4.0) * 4.0 / grid).coerceIn(4.0 / grid, 1.0 - 4.0 / grid)
            } else {
                onBeat(fraction)
            }
    }

    private var fadeStartedAt = 0L

    /**
     * Both tracks' analyses as the blend began, for the beat the scrubber
     * glows on — see [publishBlend]. Fixed at the start, like [render], so a
     * refining pass landing mid-blend cannot move the grid under the glow.
     */
    private var outgoingAnalysis = TrackAnalysis()
    private var incomingAnalysis = TrackAnalysis()
    private var bailStartedAt = 0L
    private var armDeadline = 0L

    /** Length of the fade-out driven by [Phase.SLEEP_FADE], in ms. Fixed when it begins. */
    private var sleepFadeMs = 0L

    /**
     * Where the outgoing track was when [beginSleepFadeOut] started, in its
     * own position ms. Progress is measured from here rather than off a
     * clock, for the same reason [driveFade] measures off the incoming
     * track's position: a pause should park the fade-out where it stands,
     * not keep counting down underneath a silent, stopped player.
     */
    private var sleepFadeStartPositionMs = 0L

    /**
     * The outgoing track's own volume when [beginSleepFadeOut] started, so a
     * fade-out that begins mid-ramp (rare, but a plan can be replanned right up
     * to the moment it arms) scales from where the volume actually is rather
     * than assuming it started at full.
     */
    private var sleepFadeStartGain = 1f

    /**
     * When the last transition finished, from [SystemClock.elapsedRealtime], or
     * zero while none has this session.
     *
     * Read through [msSinceTransition] by callers that have to stay off the
     * session player for a moment *after* a blend as well as during one.
     */
    private var settledAt = 0L

    /**
     * Gain the outgoing track was at when the fade was interrupted, so the ramp
     * out starts from where it actually is rather than from full volume.
     */
    private var bailFromGain = 0f

    /** Dedupes the per-tick plan log down to one line per distinct verdict. */
    private var lastPlanVerdict = ""

    // ---- Phase lock (Advanced Automix) --------------------------------------

    /** When the speed nudge in flight ends, from [SystemClock.elapsedRealtime]; 0 when none is. */
    private var nudgeUntil = 0L

    /** No phase is measured before this: the players are settling after a start or a nudge. */
    private var lockQuietUntil = 0L

    /** Phase errors, in incoming seconds, gathered until there are enough to take a median of. */
    private val lockErrors = DoubleArray(LOCK_SAMPLES)
    private var lockCount = 0

    /** [beatAt]'s answer — seconds past the last beat, and that beat's length — without allocating. */
    private val beatScratch = DoubleArray(2)

    /** Where each side of the blend really is between the player's own reports — see [PositionClock]. */
    private val outClock = PositionClock()
    private val inClock = PositionClock()

    /**
     * A player's position between the moments it actually reports one.
     *
     * Media3 1.11 schedules its playback loop dynamically: while the output
     * buffer is full it sleeps, and [ExoPlayer.getCurrentPosition] only moves
     * when it wakes — measured here at about every 280ms, a step at a time.
     * Everything a blend times off a position was reading those steps: the
     * faders and filters moved in lurches, the scrubber's beat anchor jumped by
     * up to a beat, and the phase lock compared two players whose last reports
     * were a quarter of a second apart in age, on a 320ms beat — noise it then
     * dutifully "corrected".
     *
     * So each new report is taken as having landed halfway through the tick it
     * was seen in, and moved on from there at the player's own speed until the
     * next one. One subtraction and one multiply per read, and no need to turn
     * the scheduling — and the battery it saves — off.
     */
    private class PositionClock {
        private var player: ExoPlayer? = null
        private var reported = 0L
        private var seenAt = 0L

        fun reset() {
            player = null
        }

        /** [p]'s position now, in ms. */
        fun read(p: ExoPlayer): Double {
            val now = System.nanoTime()
            val position = p.currentPosition
            if (p !== player || position != reported) {
                seenAt = if (p === player) now - HALF_TICK_NANOS else now
                player = p
                reported = position
            }
            if (!p.isPlaying) return position.toDouble()
            val since = (now - seenAt).coerceIn(0L, MAX_EXTRAPOLATION_NANOS)
            return position + since / 1e6 * p.playbackParameters.speed
        }
    }

    /** [player]'s position through whichever clock follows its side of the blend. */
    private fun positionMs(player: ExoPlayer): Double =
        if (player === incoming) inClock.read(player) else outClock.read(player)

    // ---- Tempo ease-back (Advanced Automix) --------------------------------

    /** The player still coming off a beatmatch stretch after its blend finished, or null. */
    private var easing: ExoPlayer? = null
    private var easeRate = 1.0
    private var easeStep = 0.0
    private var easeStepMs = 0L
    private var easeNextAtMs = 0L
    private var easeItemIndex = 0
    private var softSpeedJob: Job? = null

    /**
     * True while a transition is armed or running.
     *
     * For callers about to do something that would otherwise fight this class
     * for the session player mid-blend — [PlaybackService]'s quality upgrade is
     * the one that does, since `replaceMediaItem` tears the current source down
     * and rebuilds it. Doing that to either player mid-transition breaks the
     * blend rather than merely delaying it, so such a caller should wait for
     * this to clear rather than proceed anyway.
     */
    fun isTransitioning(): Boolean = phase != Phase.IDLE

    /** Applies the listener's speed without exposing AudioTrack's parameter-change click. */
    fun applyPlaybackSpeed(speed: Float) {
        if (isTransitioning()) return
        // User-controlled speed must track the slider immediately. A previous
        // gain-notch wrapper restarted for every slider emission and sounded
        // like repeated stalls while dragging.
        for (player in listOf(active(), standby()).distinct()) {
            player.setPlaybackSpeed(speed)
        }
    }

    /**
     * How long since the last transition finished, or null while none has.
     *
     * For the same caller as [isTransitioning], which needs a little more than
     * that flag can give it. The flag clears on the tick the blend completes,
     * so a source torn down and rebuilt the moment it clears puts its break in
     * the audio a few hundred milliseconds after the incoming track finally
     * stood alone — not a broken blend, but heard as one. A caller that wants
     * the transition to have been *over* for a while, rather than merely to
     * have ended, waits this out too.
     *
     * Says nothing about a transition still in flight — it reports whatever the
     * one before it left behind — so [isTransitioning] stays the first question
     * to ask.
     */
    fun msSinceTransition(): Long? =
        settledAt.takeIf { it != 0L }?.let { SystemClock.elapsedRealtime() - it }

    private val listener = object : Player.Listener {
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            // The listener moving the playhead is something no half-finished
            // crossfade should survive. Nothing this class does registers here
            // any more: the handoff is a role swap, not a seek.
            if (reason == Player.DISCONTINUITY_REASON_SEEK) bail()
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
                Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> bail()
            }
        }

        override fun onPlayerError(error: PlaybackException) = bail()

        // The session reached the end of a track [holdAtEnd] is holding, before
        // the handoff took it off that track. Only ever a late handoff or a
        // transition that never started; either way the hold must not leave
        // the listener paused at the end of a song.
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady || reason != Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) return
            val held = heldAtEnd ?: return
            if (held !== listeningTo) return
            val into = incoming
            if (phase == Phase.FADING && !handedOff && into != null) {
                // The blend is running and the incoming track is already
                // playing under it: this is just the handoff, a little late.
                handOff(held, into)
                return
            }
            // No blend to hand to. Let the queue move on as it would have.
            releaseHold()
            if (phase == Phase.ARMING) bail()
            held.play()
        }
    }

    /**
     * Keeps [listener] on whichever player is the session.
     *
     * It has to move rather than sit on both: arming loads a whole queue onto
     * the standby, which Media3 reports as the playlist changing, and a listener
     * attached there would read that as the queue being replaced out from under
     * the very transition it is setting up.
     */
    private fun listenTo(target: ExoPlayer) {
        if (listeningTo === target) return
        listeningTo?.removeListener(listener)
        target.addListener(listener)
        listeningTo = target
    }

    fun start() {
        listenTo(active())
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                tick()
                delay(
                    when (phase) {
                        Phase.IDLE -> IDLE_STEP_MS
                        Phase.ARMING -> ARM_STEP_MS
                        Phase.FADING -> FADE_STEP_MS
                        Phase.SLEEP_FADE -> FADE_STEP_MS
                        Phase.BAILING -> BAIL_STEP_MS
                    },
                )
            }
        }
    }

    fun release() {
        tickerJob?.cancel()
        tickerJob = null
        endEase(clickless = false)
        releaseHold()
        listeningTo?.removeListener(listener)
        listeningTo = null
        active().volume = 1f
        AppSettings.smartMixInProgress.value = false
        AppSettings.smartMixBlend.value = null
        filters.open()
        filters.parkEchoes()
        onBlendHeadroom(1f)
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
     * Called before the skip is carried out, so the outgoing track is already on
     * its way down as the new one starts, and the listener's own seek lands on a
     * player this class has finished with.
     */
    fun onSkipRequested() {
        if (phase != Phase.IDLE) bail()
    }

    // ---- Ticker -------------------------------------------------------------

    private fun tick() {
        // A pause has to take the other player with it, or one half of the blend
        // carries on alone over a stopped one. Mirrored every tick rather than
        // handled as an event, so audio focus loss, the sleep timer and the
        // pause button all get the same treatment for free. Which player follows
        // which flips at the handoff, halfway through the blend: before it the
        // incoming track shadows the session, after it the outgoing tail does.
        if (phase == Phase.FADING || phase == Phase.BAILING) {
            if (handedOff) {
                outgoing?.playWhenReady = incoming?.playWhenReady ?: true
            } else {
                outgoing?.playWhenReady?.let { incoming?.playWhenReady = it }
            }
        }

        // Every tick, not only when a transition can be planned. This used to
        // live inside [considerSmartTransition], which needs an idle phase, a
        // playing player and a known duration — none of which hold during a
        // transition or during the re-buffer after a quality upgrade. The line
        // simply froze on the previous pair, so a track that had not been
        // analysed kept showing the *departing* track's "analysed" until
        // ticking resumed.
        publishAnalysisState()

        if (easing != null && phase == Phase.IDLE) driveEase()

        when (phase) {
            Phase.IDLE -> considerAutoTransition()
            Phase.ARMING -> driveArming()
            Phase.FADING -> driveFade()
            Phase.SLEEP_FADE -> driveSleepFade()
            Phase.BAILING -> driveBail()
        }
    }

    /** Arms a crossfade as the playing track runs out. */
    private fun considerAutoTransition() {
        val player = active()
        if (!player.isPlaying) return
        // Not while a version swap owns the standby player — see
        // [versionSwapActive]. Nothing to clean up on the way out unlike the
        // party case below: a version swap is a between-tracks affair on the
        // same item, so the transition window and mix flag it would have
        // armed still describe the next track correctly once the swap lets go.
        if (versionSwapActive()) return
        // Not while listening together. A blend starts the next track early, by
        // a length this device decides for itself from its own copy of the
        // audio — so in a party every member would begin the next song at a
        // different moment, and each would then be dragged back by a correcting
        // seek. The transition a party shares is the plain one: whoever reaches
        // the end first publishes the change and everybody moves together. See
        // [PartySync].
        //
        // The analysis behind it stops too, including a pass already running —
        // see [com.music.bitchord.playback.smart.TrackAnalyzer], which reads
        // the party for itself. Nothing here asks for one while this returns,
        // but a request made a tick before the party started would otherwise
        // run to completion: a whole-track decode and two model passes, spent
        // on a transition that cannot happen.
        if (ListenTogether.state.value.inParty) {
            // Left behind by the last pair planned before the party started.
            // The marker describes a transition that is no longer going to
            // happen, and the flag a mix that is no longer running; both would
            // otherwise sit on screen for as long as the party lasts.
            AppSettings.smartTransitionWindow.value = null
            AppSettings.smartMixInProgress.value = false
            return
        }
        // Nothing to transition *into*, so any analysis state left over from the
        // previous pair is stale — the last track of a queue should not still be
        // claiming both songs are measured.
        if (!player.hasNextMediaItem()) {
            AppSettings.smartTransitionWindow.value = null
            return
        }

        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return

        // Repeating one track would crossfade it into itself, so nothing is
        // armed and no window is marked — but the queue behind the loop has not
        // moved, and what sits after it is still the track that plays next the
        // moment repeat-one comes off.
        //
        // Returning here outright is what made turning repeat off look like it
        // lost an analysis. Analysis is only ever asked for on the way to
        // planning a transition, so for as long as the loop ran nothing asked
        // for the following track at all, and the request that finally arrived
        // when repeat came off was the *first* one — a whole-track decode
        // starting from nothing on a song that was by then seconds away, where
        // an unlooped queue would have had it measured minutes earlier. The
        // measurement is the same either way, so it may as well be made during
        // the loop rather than after it.
        if (player.repeatMode == Player.REPEAT_MODE_ONE) {
            if (AppSettings.smartFadeEnabled.value) requestAnalysisAround(player, duration)
            // Stale otherwise: the marker would keep describing the transition
            // planned for this pair before the loop went on, at a point the
            // playhead now runs past on every lap without anything happening.
            AppSettings.smartTransitionWindow.value = null
            return
        }

        // Automix is its own on/off, independent of the manual crossfade
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
        if (SleepTimer.afterTrack.value) {
            // The sleep timer is due when this track ends. There is no next
            // track to arm a crossfade into, so run the same fade-out this
            // pair would otherwise have used against silence instead — see
            // [beginSleepFadeOut]. No arm-lead margin: nothing is buffering.
            if (remaining > fade) return
            beginSleepFadeOut(fade)
            return
        }
        // Arm early: the standby has to open the incoming track and buffer to
        // its cue point, and that work has to be finished by the time the fade
        // is due rather than started then. And before this player starts
        // reading the next track, or the end can no longer be held — see
        // [holdAtEnd].
        if (remaining > fade + ARM_LEAD_MS && remaining > armBeforeReadAheadMs()) return

        begin(fade, endMs = duration, smart = false)
    }

    /**
     * Arms a Automix transition once its plan says the playhead is close
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
        val player = active()
        val currentItem = player.currentMediaItem ?: return
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return
        val nextItem = player.getMediaItemAt(nextIndex)
        // Even a manual catalogue match is still video-origin. AutoMix's
        // analysis and cueing are deliberately never applied to either side
        // of a transition involving a video row.
        if (currentItem.isVideoOrigin || nextItem.isVideoOrigin) {
            AppSettings.smartTransitionWindow.value = null
            AppSettings.smartMixInProgress.value = false
            return
        }
        val nextDuration = nextItemDurationMs(nextIndex, nextItem)

        requestAnalysisAround(player, duration)

        // Only used before analysis lands, or when the evidence is too weak
        // for more than a plain fade (see [TransitionTier.PLAIN_CROSSFADE]):
        // once real analysis is available, [planTransition] sizes the overlap
        // itself from tempo and structure and ignores this entirely. Honours
        // the manual slider if the listener also set one, so the two settings
        // don't fight; falls back to a fixed length when it's at "Off".
        val fallbackSeconds = configuredFadeMs().takeIf { it > 0L }
            ?.div(1000.0)
            ?: DEFAULT_SMART_FALLBACK_SECONDS

        // Resolved once and reused: [analysisFor] was being called five separate
        // times per tick below, and the answer cannot change mid-tick.
        val currentAnalysis = analysisFor(currentItem)
        val nextAnalysis = analysisFor(nextItem)
        val analysisState = AppSettings.smartAnalysis.value
        // The DJ planner is Automix now; there is no separate mode switch.
        val advanced = true

        val plan = planTransition(
            analysis = currentAnalysis,
            nextAnalysis = nextAnalysis,
            currentTrack = currentItem.toTransitionInfo(duration),
            nextTrack = nextItem.toTransitionInfo(nextDuration),
            currentTime = player.currentPosition / 1000.0,
            duration = duration / 1000.0,
            fadeSeconds = fallbackSeconds,
            mode = CrossfadeMode.SMART,
            advanced = advanced,
        )
        // One line per distinct verdict rather than one per 250ms tick, so the
        // log says what the planner decided for this pair without burying it.
        val verdict = "${plan.reason}|${plan.transitionStyle}|fade=${plan.fadeMs}" +
            "|cue=${plan.incomingCueTime}|rate=${plan.incomingPlaybackRate}" +
            "|vocalOverlap=${"%.2f".format(Locale.ROOT, plan.vocalOverlap)}" +
            "|advanced=$advanced|lock=${plan.phaseLock}|variant=${plan.filterVariant}|echo=${plan.echoSeconds}" +
            "|blocked=${plan.blocked}|policy=${plan.policyReasons.joinToString(",")}"
        if (verdict != lastPlanVerdict) {
            lastPlanVerdict = verdict
            Log.d(
                TAG,
                "plan ${currentItem.mediaId}->${nextItem.mediaId}: $verdict " +
                    "bpm=${currentAnalysis.bpm}/${nextAnalysis.bpm} " +
                    "conf=${currentAnalysis.beatConfidence}/${nextAnalysis.beatConfidence}",
            )
        }

        // Gated on *both* tracks being measured, not on the plan alone. Until
        // then the planner is still sizing the overlap from a fallback that
        // moves as evidence lands, and a marker that slides along the bar while
        // you watch it is worse than none. Cleared during the transition itself
        // by [driveLap], because from that moment these fractions describe a
        // track the session player has already left.
        //
        // Asymmetric on purpose, because the two sides are read for different
        // things and a head-only result covers one of them completely.
        //
        // Where the window *sits* comes almost entirely from the outgoing track:
        // its content end, its outro, its mix-out anchors. A provisional result
        // has none of those — [analyzeHead] drops them deliberately rather than
        // answering confidently about a track it has only seen the opening of —
        // so the plan falls back to a plain end-of-track window, and the marker
        // would sit there and then jump backwards when the whole-track pass
        // lands. That is the sliding marker this guard exists for, so the
        // outgoing side still has to be finished.
        //
        // The incoming side is the opposite case. All the planner asks of it is
        // tempo, confidence and where it is safe to cue in — which are exactly
        // the fields a head pass measures, and it measures them over the same
        // opening window the whole-track pass would. Refining will sharpen those
        // numbers but not move them, so holding the marker back for it hid a
        // window that was already correct. Since the incoming track is now
        // routinely analysed from its opening long before it plays, that was
        // most of the time the marker was missing.
        val markable = !plan.blocked &&
            plan.markerVisible &&
            duration > 0L &&
            analysisState.current == TrackAnalysisState.ANALYSED &&
            analysisState.next in MEASURED_ENOUGH_TO_ENTER_ON
        AppSettings.smartTransitionWindow.value = if (markable) {
            TransitionWindow(
                start = (plan.transitionStart * 1000.0 / duration).toFloat().coerceIn(0f, 1f),
                end = (plan.transitionEnd * 1000.0 / duration).toFloat().coerceIn(0f, 1f),
            )
        } else {
            null
        }

        if (plan.blocked) return

        val fade = plan.fadeMs
        if (fade <= 0L) return

        val transitionStartMs = (plan.transitionStart * 1000).roundToLong()
        val remaining = transitionStartMs - player.currentPosition
        if (SleepTimer.afterTrack.value) {
            // Automix would start blending into the next track here; the sleep
            // timer means there is no next track, so run the same fade-out
            // against silence instead — see [beginSleepFadeOut].
            if (remaining > 0L) return
            beginSleepFadeOut(fade)
            return
        }
        // Same arm-ahead margin as the standard path, just measured against
        // the plan's own start rather than a fixed offset from track end —
        // an analyzed mix-out anchor can place that start well before the
        // file actually ends.
        //
        // Plus, when the outgoing track is the one sped up, the beats that
        // ramp takes: it has to be at the incoming tempo by the fade, so it
        // starts that much earlier, from arming.
        //
        // And never later than the point this player starts reading the next
        // track, whatever the plan says: a short blend ending on the file's
        // last sample would otherwise arm too late to hold it — see
        // [holdAtEnd].
        val beatMs = (plan.beatSeconds * 1000).roundToLong()
        val rampMs = if (beatMs > 0L) rampStepsFor(plan.outgoingPlaybackRate) * beatMs else 0L
        val fileRemaining = duration - player.currentPosition
        if (remaining > ARM_LEAD_MS + rampMs && fileRemaining > armBeforeReadAheadMs()) return

        begin(
            fade,
            endMs = (plan.transitionEnd * 1000).roundToLong(),
            smart = true,
            cueTimeMs = (plan.incomingCueTime * 1000).roundToLong(),
            playbackRate = plan.incomingPlaybackRate,
            outgoingRate = plan.outgoingPlaybackRate,
            beatMs = beatMs,
            renderStyle = Render(
                style = plan.transitionStyle,
                bassSwap = plan.bassSwap,
                bassSwapFraction = plan.bassSwapFraction,
                filterSweep = plan.filterSweep,
                filterVariant = plan.filterVariant,
                vocalOverlap = plan.vocalOverlap,
                advanced = advanced,
                beats = if (plan.beatSeconds > 0) plan.fadeSeconds / plan.beatSeconds else 0.0,
                lockPhase = plan.phaseLock,
                echoSeconds = plan.echoSeconds,
            ),
        )
    }

    /**
     * Queues the playing track and the one queued after it for analysis.
     *
     * Cheap no-ops once a track is analysed or already in flight; called every
     * tick so a track that finishes caching mid-song is picked up without a
     * separate trigger.
     *
     * Not folded into [considerSmartTransition], because the pair still needs
     * measuring in the one case that never plans a transition at all: a track
     * on repeat-one, which will hand over to this same next track as soon as
     * the loop is switched off.
     */
    private fun requestAnalysisAround(player: ExoPlayer, duration: Long) {
        val currentItem = player.currentMediaItem ?: return
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return
        val nextItem = player.getMediaItemAt(nextIndex)
        if (currentItem.isVideoOrigin || nextItem.isVideoOrigin) return
        requestAnalysis(currentItem, duration)
        requestAnalysis(nextItem, nextItemDurationMs(nextIndex, nextItem))
    }

    /**
     * Keeps the stats line describing the pair that is actually playing.
     *
     * Cheap enough to run unconditionally — two concurrent-map lookups and a
     * set membership test — and running it unconditionally is the point: any
     * gating reintroduces the staleness this exists to remove.
     */
    private fun publishAnalysisState() {
        val player = active()
        val currentItem = player.currentMediaItem
        val nextIndex = player.nextMediaItemIndex
        val nextItem = if (nextIndex == C.INDEX_UNSET) null else player.getMediaItemAt(nextIndex)
        AppSettings.smartAnalysis.value = SmartAnalysis(
            current = currentItem?.let { stateOf(it, analysisFor(it)) } ?: TrackAnalysisState.WAITING,
            next = nextItem?.let { stateOf(it, analysisFor(it)) } ?: TrackAnalysisState.WAITING,
        )
    }

    /**
     * Where one track stands, for the stats line. "Analysing" is asked for
     * first because a track can be in flight while a superseded provisional
     * result is already on record, and the work in progress is the more useful
     * thing to say about it.
     */
    private fun stateOf(item: MediaItem, analysis: TrackAnalysis): TrackAnalysisState = when {
        // Usable first, and a pass in flight *second*. The other order was
        // right up to the point a head-only result started arriving before the
        // whole-track one: a track measured off its opening reads as analysed,
        // then finishes caching, then has the full pass run over it to replace
        // the provisional numbers — and reported "analysing" again throughout.
        // Going backwards from analysed reads as something having broken, when
        // what is happening is a better answer being computed. Confidence on one
        // such track went 0.39 to 0.94 and its cue moved from 0.1s to 9.5s.
        analysis.isUsable ->
            if (analysisRunningFor(item)) TrackAnalysisState.REFINING else TrackAnalysisState.ANALYSED
        analysisRunningFor(item) -> TrackAnalysisState.ANALYSING
        // A recorded-but-unusable result is the analyzer's way of saying it
        // tried and got nothing, and that it will not try again — it writes a
        // ready-but-empty entry precisely so the track stops being retried. A
        // track nothing has looked at yet has no status at all, which is the
        // only case that is still merely waiting.
        analysis.status == TrackAnalysis.STATUS_READY -> TrackAnalysisState.FAILED
        else -> TrackAnalysisState.WAITING
    }

    /**
     * The next queue item's own duration.
     *
     * Media3 fills a timeline window's duration in when the item is *prepared*,
     * which for the track after this one happens a few seconds before it starts
     * playing. So for almost the whole of the current track this answered zero —
     * and zero is not a harmless "don't know" downstream. It reaches
     * [com.music.bitchord.playback.smart.TrackAnalyzer.request] as the next
     * track's duration, and with no duration to check a sibling copy against the
     * analyzer will only read the rendition the cache key resolves to *right
     * now*, which with source substitution on is the `#alt` entry — while the
     * copy actually on disk is the plain one its own head fetch just pulled
     * down. Nothing matches, the pass returns silently, and it does that on every
     * tick for the rest of the track. Measured: a fully cached next track sat
     * unread for three minutes and was analysed eight seconds before the fade it
     * was meant to inform, having been analysable the whole time.
     *
     * The runtime is on the item already — queued from a row that knew it, and
     * carried on the playback URI as `d=` because a cross-source match is made on
     * it (see `Song.matchQuery`). Reading it here costs nothing and is available
     * from the moment the queue is set.
     */
    private fun nextItemDurationMs(nextIndex: Int, item: MediaItem): Long {
        val timeline = active().currentTimeline
        if (!timeline.isEmpty) {
            timeline.getWindow(nextIndex, Timeline.Window()).durationMs
                .takeIf { it != C.TIME_UNSET && it > 0 }
                ?.let { return it }
        }
        return queuedDurationMs(item)
    }

    /**
     * The runtime the queue row carried, in milliseconds, or 0 when the item
     * doesn't state one — a local file, or a track queued without a duration.
     *
     * Deliberately forgiving: [Uri.getQueryParameter] throws on an opaque URI,
     * and a missing or unparsable value is simply an absent duration rather than
     * anything worth failing a tick over.
     */
    private fun queuedDurationMs(item: MediaItem): Long {
        val uri = item.localConfiguration?.uri ?: return 0L
        val seconds = runCatching { uri.getQueryParameter("d") }.getOrNull()?.toLongOrNull() ?: return 0L
        return if (seconds > 0) seconds * 1000L else 0L
    }

    /** BitChord doesn't carry album metadata on [MediaMetadata] yet, so [TransitionTrackInfo.album] stays blank. */
    private fun MediaItem.toTransitionInfo(durationMs: Long) = TransitionTrackInfo(
        id = mediaId,
        durationMs = durationMs,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
    )

    /**
     * Loads the standby player with the queue, positioned on the incoming track
     * at the plan's cue point, and leaves it buffering there silently.
     *
     * Nothing is committed here. The standby is a scratch player until
     * [startFade] runs, so a queue edit, a skip or a pause arriving during
     * arming costs nothing but the decoder it was holding.
     *
     * The cue point is reached by *starting there* rather than by seeking:
     * `setMediaItems` takes the position the item is to begin at, so the
     * incoming track opens at its analyzed mix-in point with no seek, no
     * discontinuity and no frame-rounding. Same for the beatmatch stretch, which
     * is applied before a note has been rendered rather than being switched on
     * underneath one already playing.
     */
    private fun begin(
        fade: Long,
        endMs: Long,
        smart: Boolean,
        cueTimeMs: Long = 0L,
        playbackRate: Double = 1.0,
        outgoingRate: Double = 1.0,
        beatMs: Long = 0L,
        renderStyle: Render = Render(),
    ): Boolean {
        val out = active()
        val into = standby()
        if (out === into) return false
        val nextIndex = out.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return false
        // The last blend's stretch, if it is somehow still easing off: this
        // player is about to be the outgoing side of a new one.
        endEase(clickless = false)

        fadeMs = fade
        fadeEndMs = endMs
        smartFadeActive = smart
        incomingCueTimeMs = cueTimeMs.coerceAtLeast(0L)
        incomingPlaybackRate = playbackRate
        outgoingPlaybackRate = outgoingRate
        rampSteps = if (beatMs > 0L) rampStepsFor(outgoingRate) else 0
        rampStepMs = beatMs
        rampStartMs = endMs - fade - rampSteps * beatMs
        rampStep = 0
        render = renderStyle
        armDeadline = SystemClock.elapsedRealtime() + ARM_TIMEOUT_MS + rampSteps * beatMs
        handedOff = false
        outgoing = out
        incoming = into

        val items = (0 until out.mediaItemCount).map { out.getMediaItemAt(it) }
        queuedItemCount = items.size

        Log.d(
            TAG,
            "arm ${if (smart) "smart" else "standard"} fade=${fade}ms end=${endMs}ms " +
                "cue=${incomingCueTimeMs}ms rate=$incomingPlaybackRate at=${out.currentPosition}ms " +
                "style=${render.style} bassSwap=${render.bassSwap}@${render.swapAt} " +
                "advanced=${render.advanced} lock=${render.phaseLock} handoff=${render.handoffAt} " +
                "echo=${if (render.echo) "${render.echoSecondsF}s@${render.echoAt}" else "off"} " +
                "sweep=${render.filterSweep}",
        )

        holdAtEnd(out)

        // Carried across so the incoming track inherits the listener's own
        // settings rather than whatever the standby was left on last time.
        into.skipSilenceEnabled = out.skipSilenceEnabled
        into.repeatMode = out.repeatMode
        into.shuffleModeEnabled = out.shuffleModeEnabled
        // Stacks on top of the listener's speed control rather than replacing
        // it, so a beatmatched transition and "play everything at 1.25x" don't
        // fight each other. Undone in [finish].
        into.setPlaybackSpeed((AppSettings.playbackSpeed.value * incomingPlaybackRate).toFloat())
        into.volume = 0f
        into.setMediaItems(items, nextIndex, incomingCueTimeMs)
        // Before `prepare`, so the standby's per-player audio state is right
        // for the incoming track from its very first decoded frame rather than
        // from the handoff, which is half a blend too late.
        items.getOrNull(nextIndex)?.let { item ->
            val after = into.nextMediaItemIndex
            val afterId = if (after == C.INDEX_UNSET) null else items.getOrNull(after)?.mediaId
            onArmIncoming(item, afterId)
        }
        // Buffers without sounding. Started for real in [startFade].
        into.playWhenReady = false
        into.prepare()

        phase = Phase.ARMING
        return true
    }

    /**
     * Stops [out] reading on into the next track, so the handoff can cut its
     * queue without a break in its sound.
     *
     * A player reads well ahead of what it plays: everything its sink holds,
     * seconds of it on a float route, plus what the decoder has in flight. So
     * the outgoing player starts reading the *next* item — the song fading
     * in — that far before its own track ends, and its audio is already
     * queued behind the last of this one. [handOff] then removes that item,
     * and Media3, finding the period its renderers are reading gone, seeks to
     * where it already is: the sink is flushed and refilled, and the track
     * still sounding through the second half of the blend drops out for a
     * moment. Back when the handoff came at the start of the blend that
     * never happened — the track had a whole fade left to run — which is why
     * the break arrived with the midpoint handoff.
     *
     * Pausing at the end of the item keeps the renderers on this track: they
     * are told it is final, play it out to its last sample and stop. Removing
     * the items behind it then touches nothing being read. It has to be set
     * before the read-ahead reaches the next track, though — setting it once
     * that has happened makes Media3 flush to undo it, the same break — so
     * this is skipped when it is already too late, and both arm paths arm
     * early enough that it rarely is.
     *
     * Released in [finish], and by [listener] if the track runs out while
     * still the session's.
     */
    private fun holdAtEnd(out: ExoPlayer) {
        val duration = out.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return
        if (duration - out.currentPosition <= readAheadMs()) {
            Log.d(TAG, "end not held: ${duration - out.currentPosition}ms left, read-ahead ${readAheadMs()}ms")
            return
        }
        out.pauseAtEndOfMediaItems = true
        heldAtEnd = out
    }

    /** Undoes [holdAtEnd]. Idempotent; never flushes. */
    private fun releaseHold() {
        heldAtEnd?.pauseAtEndOfMediaItems = false
        heldAtEnd = null
    }

    /**
     * How far before its track's end the session player starts reading the
     * next one, with room to spare: its sink's lead over the speaker, plus
     * the decoder and a tick or two of this class running late.
     */
    private fun readAheadMs(): Long {
        val lead = filters.incomingLeadMs().takeIf { it > 0L } ?: FALLBACK_SINK_LEAD_MS
        return lead + READ_AHEAD_MARGIN_MS
    }

    /**
     * The latest the arm paths may arm and still leave [holdAtEnd] room: two
     * idle ticks ahead of [readAheadMs], so the tick that notices the end
     * coming is never already past it.
     */
    private fun armBeforeReadAheadMs(): Long = readAheadMs() + 2 * IDLE_STEP_MS

    /**
     * Waits for the standby to have the incoming track ready at its cue point,
     * and for the outgoing track to reach the fade.
     *
     * There is nothing to align here — the two players hold different songs — so
     * this is only ever waiting on a buffer.
     */
    private fun driveArming() {
        val out = outgoing ?: return bail()
        val into = incoming ?: return bail()
        if (!stillWorthFading()) return bail()
        // Paused while armed: the transition is no longer imminent, and holding
        // a prepared decoder open against a stopped player is worse than arming
        // again when playback resumes.
        if (!out.playWhenReady) return bail()

        val expired = SystemClock.elapsedRealtime() > armDeadline
        val ready = into.playbackState == Player.STATE_READY

        // A standby that never got the incoming track ready has nothing to fade
        // up. Give up and let the queue move on plainly rather than fading into
        // silence.
        if (expired && !ready) return bail()

        // Both sinks write seconds ahead of the speaker. The standby fills its
        // whole buffer while it waits here, so its filter has to be at the
        // blend's opening position before that audio is written, not once the
        // fade starts — otherwise the incoming track's first seconds are heard
        // unfiltered. The outgoing sink crosses into the blend its lead before
        // the listener does, and its filter has to start moving then.
        val outgoingAt = if (fadeMs > 0L && fadeEndMs > 0L) {
            (out.currentPosition + tracks.outgoingLeadMs() - (fadeEndMs - fadeMs)).toFloat() / fadeMs
        } else {
            0f
        }
        rideFilters(0f, outgoingAt.takeIf { it > 0f }?.coerceAtMost(1f))
        if (rampSteps > 0) rampOutgoing(out)

        // Wait for the track to actually reach the fade point. [fadeEndMs] is
        // the track's own duration in standard mode, or a Automix plan's
        // analyzed mix-out anchor when it ends before the file does.
        val atFadePoint = fadeEndMs <= 0L || fadeEndMs - out.currentPosition <= fadeMs
        if (!atFadePoint) return
        if (ready) startFade()
    }

    /**
     * Starts the incoming track under the outgoing one.
     *
     * The session stays where it is: the player, the notification and the
     * queue keep showing the outgoing song through the first half of the
     * blend, while its bar fills to the end, and move to the incoming song at
     * the midpoint — see [handOff]. Switching the moment the incoming track
     * first sounded put a song on screen that could barely be heard yet, under
     * one that was still plainly playing.
     */
    private fun startFade() {
        val out = outgoing ?: return bail()
        val into = incoming ?: return bail()

        into.volume = 0f
        into.playWhenReady = true
        fadeStartedAt = SystemClock.elapsedRealtime()
        nudgeUntil = 0L
        lockCount = 0
        outClock.reset()
        inClock.reset()
        // The incoming player's clock is still settling off its start.
        lockQuietUntil = fadeStartedAt + LOCK_START_MS
        // However the ramp went — a late arm, a slow buffer — the blend starts
        // on the incoming tempo.
        if (rampSteps > 0 && rampStep < rampSteps) {
            rampStep = rampSteps
            out.setPlaybackSpeed((AppSettings.playbackSpeed.value * outgoingPlaybackRate).toFloat())
        }
        outgoingAnalysis = out.currentMediaItem?.let(analysisFor) ?: TrackAnalysis()
        incomingAnalysis = into.currentMediaItem?.let(analysisFor) ?: TrackAnalysis()

        Log.d(TAG, "blend start at cue=${into.currentPosition}ms out=${out.currentPosition}ms")

        // Published before the mix flag, in the same tick: the scrubber shows
        // the old sheen for a mix flag with no blend beside it, and would flash
        // it for a frame otherwise.
        publishBlend(out, into, force = true)
        AppSettings.smartMixInProgress.value = isRealMix()
        // The bar fills to the end through the first half of the blend, which
        // would run straight over the marker for the transition now under way.
        AppSettings.smartTransitionWindow.value = null
        phase = Phase.FADING
    }

    /**
     * Moves the session onto the incoming player, halfway through the blend.
     *
     * Everything hanging off the session player — queue index, metadata, the
     * notification, the UI, audio focus — flips to the incoming song at the
     * point it becomes the louder of the two. From here [outgoing] is the idle
     * player, still audible, being faded away.
     */
    private fun handOff(out: ExoPlayer, into: ExoPlayer) {
        if (handedOff) return
        // AutoPlay may have appended to the queue since the standby was loaded
        // with a copy of it — during arming or the first half of the blend,
        // both of which the outgoing player owned — and those tracks would
        // otherwise be lost at the swap.
        reconcileQueue(out, into)

        Log.d(TAG, "handoff at in=${into.currentPosition}ms out=${out.currentPosition}ms")

        // Before the swap, so the listener follows the session rather than
        // firing on a player this class is about to demote.
        listenTo(into)
        handedOff = true
        onHandoff(out, into)

        // The outgoing player holds the whole queue too, and a standard
        // crossfade runs right up to its track's natural end — at which point
        // ExoPlayer would do what it always does and advance to the next item,
        // starting the incoming song a second time, on top of itself, out of the
        // player that is supposed to be going quiet. Truncating the queue at the
        // playing item turns that into STATE_ENDED, which [driveFade] already
        // reads as the tail being spent. Safe to discard: [into] is the
        // authoritative queue from here, and this player is retired seconds
        // later anyway.
        //
        // Only seamless because [holdAtEnd] kept this player from reading into
        // the items being removed. Without the hold, a handoff this close to
        // the track's end removes the period its renderers are reading, and
        // Media3 flushes the sink to recover — a break in the track still
        // playing.
        if (out.mediaItemCount > out.currentMediaItemIndex + 1) {
            out.removeMediaItems(out.currentMediaItemIndex + 1, out.mediaItemCount)
        }
    }

    /**
     * Copies onto the standby anything appended to the queue while it was
     * arming.
     *
     * AutoPlay extending the queue mid-transition is explicitly allowed — it
     * doesn't change the playing item, so it has never been a reason to drop a
     * blend. Under the old design that was free, because only one player ever
     * held the queue. Now the standby is carrying a copy taken at arm time, and
     * that copy is what survives the swap, so the difference has to be carried
     * across or the appended tracks simply vanish when the roles change.
     *
     * Only a pure append is reconciled. Anything else — a queue replaced, an
     * item removed or moved — changes what the incoming track *is*, and
     * [listener] has already bailed the transition for it.
     */
    private fun reconcileQueue(out: ExoPlayer, into: ExoPlayer) {
        val appended = (queuedItemCount until out.mediaItemCount).map { out.getMediaItemAt(it) }
        if (appended.isEmpty()) return
        into.addMediaItems(appended)
        queuedItemCount = out.mediaItemCount
        Log.d(TAG, "reconciled ${appended.size} appended item(s) onto the incoming player")
    }

    /**
     * The crossfade proper.
     *
     * Driven off the *incoming* track's position rather than off a clock, so a
     * pause parks the transition where it stands and resuming picks it back up
     * — no timer to reconcile, and neither player left hanging at half volume
     * while the other waits.
     */
    private fun driveFade() {
        val out = outgoing ?: return bail()
        val player = incoming ?: return bail()
        // The incoming track gets the same say over the length as the outgoing
        // one did, so a long crossfade into a short track tightens rather than
        // swallowing it. Its duration is often still unknown when the fade
        // starts — the stream is only being opened — so this is read every tick
        // and simply narrows the span once the answer arrives. Capped only by
        // the incoming track's own length, not by [configuredFadeMs] — a Smart
        // Fade plan already sized itself independently of that setting, and
        // may be running with it at zero.
        // Measured from where the incoming track was *cued*, not from zero. A
        // Automix plan can drop it in mid-arrangement, and reading its raw
        // position as elapsed-fade would put a cue at 0:45 instantly past the
        // end of an 8-second fade — finishing the blend on its first tick and
        // landing as an abrupt cut, which is precisely the failure a cued
        // transition is supposed to avoid.
        val remainingIncoming = player.duration
            .takeIf { it != C.TIME_UNSET && it > 0L }
            ?.minus(incomingCueTimeMs)
            ?.coerceAtLeast(0L)
        val incomingCap = remainingIncoming?.div(3) ?: Long.MAX_VALUE
        val span = minOf(fadeMs, incomingCap).coerceAtLeast(1L)
        val elapsed = (inClock.read(player) - incomingCueTimeMs).coerceAtLeast(0.0).toFloat()
        // Advanced: [fadeMs] is outgoing time, and a stretched incoming track
        // covers `rate` times as much of its own timeline in it — unscaled, a
        // 4% stretch finished the blend 4% early and left the incoming drop
        // landing after it rather than on its end.
        val wallSpan = if (render.advanced) span * mediaRatio.toFloat() else span.toFloat()
        val incomingProgress = (elapsed / wallSpan).coerceIn(0f, 1f)
        // Duration/decoder timing can put the real file end ahead of the
        // analysed anchor. In that case the outgoing deck must still reach
        // zero before its final sample instead of remaining loud and appearing
        // to stop. Normally this is zero and incoming timing remains authority.
        val outRemaining = out.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            ?.minus(out.currentPosition)
        val endDrivenProgress = outRemaining?.let {
            ((span - it).toFloat() / span).coerceIn(0f, 1f)
        } ?: 0f
        val progress = maxOf(incomingProgress, endDrivenProgress)

        // Before the handoff the session is still the outgoing player, so a
        // failure on the incoming one would otherwise go unheard: its position
        // stops, the blend parks at whatever mix it had reached, and the
        // outgoing track runs out under it.
        if (!handedOff && player.playbackState == Player.STATE_IDLE) return bail()

        // Faders act at the speaker; everything in the DSP chain — the filters
        // and the headroom trim — acts where each sink is writing, its lead
        // ahead of that. So those are aimed at the blend as it will be when the
        // audio they touch is played, each side by its own lead.
        val incomingAt = ((elapsed + tracks.incomingLeadMs()) / wallSpan).coerceIn(0f, 1f)
        val outgoingAt = (progress + tracks.outgoingLeadMs() / span.toFloat()).coerceIn(0f, 1f)

        val rise = mixRise(progress)
        val fall = mixFall(progress)
        player.volume = rise
        out.volume = fall
        onBlendHeadroom(
            headroomFor(mixRise(outgoingAt), outgoingLevel(elapsed / wallSpan + tracks.outgoingLeadMs() / span.toFloat(), wallSpan)),
        )
        if (render.phaseLock) holdPhase(out, player, progress)

        // The midpoint, or sooner if the outgoing track is about to run out
        // from under it: until the handoff its queue still runs on past the
        // track, and reaching the end would start the next item — the very
        // song fading in — a second time on the player that is leaving.
        if (!handedOff && (progress >= render.handoffAt || (outRemaining != null && outRemaining <= HANDOFF_GUARD_MS))) {
            handOff(out, player)
        }
        publishBlend(out, player)
        rideFilters(incomingAt, outgoingAt)

        // Finish when the fade runs its course or its setting is switched off.
        // The outgoing decoder ending is not completion: its final sample may
        // arrive early, and the incoming fader still has to finish rising.
        // Checked against the
        // setting that actually started it — a Automix normally runs with
        // [configuredFadeMs] at zero, and reading that as "turned off" would
        // end every Automix on its first tick.
        val settingSwitchedOff = if (smartFadeActive) {
            !AppSettings.smartFadeEnabled.value
        } else {
            configuredFadeMs() <= 0L
        }
        // An echo out rings past the end of the blend, carried by the outgoing
        // player's own silenced audio, so that player is kept until its tail
        // has been heard out.
        val tailDone = !render.echo || elapsed / wallSpan >= render.echoAt + render.echoTailMs / wallSpan
        val done = (progress >= 1f && tailDone) || settingSwitchedOff
        if (done) {
            // A blend cut short before its midpoint still ends on the incoming
            // track — it is the one playing on, and what [finish] keeps.
            handOff(out, player)
            finish()
        }
    }

    /**
     * Tells the scrubber where the blend's beats fall, and whether it is moving.
     *
     * The grid comes from whichever side the listener is hearing more of —
     * the outgoing track until the handoff, the incoming one after — at the
     * rate it is actually playing: the listener's speed, and for the incoming
     * side the beatmatch stretch on top. On a beatmatched pair both grids
     * agree anyway; on one that isn't, this follows the dominant track.
     *
     * Computed every fade tick but *published* only when it has moved: the
     * scrubber runs its own beat clock and only leans on this anchor, so
     * re-sending the same grid thirty times a second just wakes every
     * collector for nothing. A new value goes out when the tempo changes, the
     * anchor drifts past [ANCHOR_TOLERANCE_NANOS] — a pause, a stall, the
     * grid moving to the other song — or playback starts or stops.
     */
    private fun publishBlend(out: ExoPlayer, into: ExoPlayer, force: Boolean = false) {
        if (!smartFadeActive) return
        val session = if (handedOff) into else out
        val playing = session.isPlaying
        val last = AppSettings.smartMixBlend.value
        // A paused blend's grid is frozen along with its players; re-deriving
        // it against a clock that keeps running would only slide the anchor.
        if (!force && last != null && !playing && !last.playing) return

        // Placed with [beatAt], the grid the phase lock itself steers by: beats
        // counted within the analyzer's downbeat bars, not off one interval from
        // the first beat. That constant grid drifts — a 0.1% tempo error is
        // 180ms three minutes in, a third of a beat, right where a blend runs —
        // so the pulse sat visibly off audio the lock had put exactly on it.
        // The rate is each player's real speed, which includes any phase-lock
        // nudge in flight rather than the stretch it was armed with.
        val sides = if (handedOff) {
            listOf(incomingAnalysis to into, outgoingAnalysis to out)
        } else {
            listOf(outgoingAnalysis to out, incomingAnalysis to into)
        }
        var beatMs = 0f
        var anchor = 0L
        for ((analysis, player) in sides) {
            if (!beatAt(analysis, positionMs(player) / 1000.0)) continue
            val rate = player.playbackParameters.speed.toDouble().takeIf { it > 0.0 } ?: 1.0
            val sinceBeat = beatScratch[0]
            beatMs = (beatScratch[1] * 1000.0 / rate).toFloat()
            anchor = System.nanoTime() - (sinceBeat / rate * 1e9).toLong()
            break
        }
        if (!force && last != null && last.playing == playing && sameGrid(last, beatMs, anchor)) return
        AppSettings.smartMixBlend.value = MixBlend(
            beatMs = beatMs,
            beatAnchorNanos = anchor,
            playing = playing,
        )
    }

    /** Whether [beatMs] and [anchor] describe the grid [last] already published, to within a tick's jitter. */
    private fun sameGrid(last: MixBlend, beatMs: Float, anchor: Long): Boolean {
        if (beatMs <= 0f || last.beatMs <= 0f) return beatMs <= 0f && last.beatMs <= 0f
        if (abs(beatMs - last.beatMs) > beatMs * TEMPO_TOLERANCE) return false
        val beatNanos = (beatMs * 1_000_000.0).toLong().coerceAtLeast(1L)
        // Distance between the two anchors, the short way round one beat.
        val offset = Math.floorMod(anchor - last.beatAnchorNanos, beatNanos)
        return minOf(offset, beatNanos - offset) <= ANCHOR_TOLERANCE_NANOS
    }

    /**
     * Starts [Phase.SLEEP_FADE]: the track playing right now is faded to
     * silence on its own, over `fade` ms — the same span a real transition
     * into the next track would have spent on this side of the blend — and
     * then paused.
     *
     * Deliberately not [begin] with a no-op standby. [begin] loads the queue
     * onto the idle player and starts it silently so it is ready to take over
     * at the handoff; none of that should happen here; there is no handoff
     * coming, and preparing a second decoder just to throw it away the moment
     * the fade ends wastes exactly the work this exists to skip.
     */
    private fun beginSleepFadeOut(fade: Long) {
        val out = active()
        sleepFadeMs = fade.coerceAtLeast(1L)
        sleepFadeStartPositionMs = out.currentPosition
        sleepFadeStartGain = out.volume
        outgoing = out
        incoming = null
        handedOff = false
        Log.d(TAG, "sleep timer due: fading out over ${fade}ms instead of transitioning")
        phase = Phase.SLEEP_FADE
    }

    /**
     * The fade-out proper. Mirrors [driveFade]'s curve on the outgoing side —
     * same [fallGain], same position-driven progress — but there is no
     * incoming track to weigh it against, so the span is simply [sleepFadeMs].
     */
    private fun driveSleepFade() {
        val out = outgoing ?: return finishSleepFade()
        // The listener turned the timer off mid-fade — nothing left to do but
        // hand the track back at full volume and let ordinary transition
        // logic resume next tick.
        if (!SleepTimer.afterTrack.value) {
            out.volume = 1f
            outgoing = null
            phase = Phase.IDLE
            return
        }
        val elapsed = (out.currentPosition - sleepFadeStartPositionMs).coerceAtLeast(0L)
        val progress = (elapsed.toFloat() / sleepFadeMs).coerceIn(0f, 1f)
        out.volume = sleepFadeStartGain * fallGain(progress)
        val done = progress >= 1f ||
            out.playbackState == Player.STATE_ENDED ||
            out.playbackState == Player.STATE_IDLE
        if (done) finishSleepFade()
    }

    /** Pauses the track the fade-out was run on and retires the timer that asked for it. */
    private fun finishSleepFade() {
        outgoing?.pause()
        outgoing?.volume = 1f
        SleepTimer.cancel()
        outgoing = null
        phase = Phase.IDLE
    }

    /** Ramps the outgoing track away rather than cutting it, so an interruption has no click in it. */
    private fun driveBail() {
        val leaving = leavingOnBail()
        if (leaving == null) {
            finish()
            return
        }
        val progress = (SystemClock.elapsedRealtime() - bailStartedAt).toFloat() / BAIL_MS
        if (progress < 1f) {
            val fall = bailFromGain * fallGain(progress)
            leaving.volume = fall
            onBlendHeadroom(headroomFor(1f, fall))
            return
        }
        finish()
    }

    // ---- Lifecycle of a transition -----------------------------------------

    /**
     * Abandons whatever is in flight.
     *
     * What has to be put back depends entirely on whether [startFade] got as far
     * as swapping the roles. Before the handoff the session player is untouched
     * and the standby is a silent scratch player, so there is nothing to unwind
     * at all — [finish] just retires it. After the handoff the session has
     * already moved and cannot be moved back (the incoming track is playing and
     * has been announced), so the only thing left is to take the outgoing track
     * away gracefully.
     */
    private fun bail() {
        if (phase == Phase.IDLE || phase == Phase.BAILING) return
        Log.d(TAG, "bail from $phase")
        AppSettings.smartMixInProgress.value = false
        AppSettings.smartMixBlend.value = null
        if (!handedOff && phase != Phase.FADING) {
            // Nothing was ever audible; no ramp to run.
            finish()
            return
        }
        // Glided open rather than snapped: both tracks are audible here, and
        // if the bail caught a bass swap mid-handover a low end is currently
        // lifted out. Dropping a 24 dB/octave filter in one buffer is the click
        // this ramp exists to avoid.
        filters.open()
        // Whichever track is *not* the session's is the one ramped away: the
        // outgoing tail after the handoff, the incoming track before it.
        if (handedOff) incoming?.volume = 1f else outgoing?.volume = 1f
        bailFromGain = leavingOnBail()?.volume ?: 0f
        bailStartedAt = SystemClock.elapsedRealtime()
        phase = Phase.BAILING
    }

    private fun leavingOnBail(): ExoPlayer? = if (handedOff) outgoing else incoming

    private fun finish() {
        if (phase != Phase.IDLE) {
            Log.d(TAG, "finish from $phase")
            // Stamped under this guard rather than beside the assignment at the
            // bottom, because this function is idempotent and gets called with
            // nothing in flight: marking every one of those as a transition
            // just ended would keep pushing the mark forward and hold a waiting
            // caller off for as long as the calls kept coming.
            settledAt = SystemClock.elapsedRealtime()
        }
        AppSettings.smartMixInProgress.value = false
        AppSettings.smartMixBlend.value = null
        // Before anything else: a transition that never handed off leaves the
        // outgoing player as the session, and it must not pause at the end of
        // its track for a blend that is not coming.
        releaseHold()
        // Unconditional and idempotent, like the speed reset below: correct
        // whether or not this transition ever filtered anything.
        // The echo's dry gain is what silenced the outgoing track, and parking
        // it restores full level: silence the player first so the audio its
        // sink writes before the stop below can never be heard.
        if (handedOff && render.echo) outgoing?.volume = 0f
        filters.open()
        filters.parkEchoes()
        onBlendHeadroom(1f)
        render = Render()
        nudgeUntil = 0L

        if (handedOff) {
            // The roles have already traded: the incoming player is the session
            // and owns the queue from here, and the outgoing one is spare.
            incoming?.let {
                it.volume = 1f
                // Undoes whatever [begin] stacked on for a beatmatched handoff.
                // Unconditional and idempotent, so this is correct whether or
                // not a stretch was ever actually applied. Advanced Automix
                // walks it back over a few bars instead of in one jump, which
                // is the tempo lurch a DJ never lets anyone hear.
                // Repeated beat-by-beat ease steps caused a notch on every beat.
                // One short protected reset is less audible and cannot pump.
                setPlaybackSpeedClickless(it, AppSettings.playbackSpeed.value)
            }
            outgoing?.let(::retire)
        } else {
            // The transition never became audible, so the session player never
            // moved and the standby is the one to throw away.
            outgoing?.let { out ->
                out.volume = 1f
                // Sped up for a blend that never came: walk it back like any stretch.
                if (outgoingPlaybackRate != 1.0) {
                    beginEase(out, outgoingPlaybackRate, out.currentMediaItem?.let(analysisFor) ?: TrackAnalysis())
                }
            }
            incoming?.let(::retire)
        }

        outgoing = null
        incoming = null
        handedOff = false
        queuedItemCount = 0
        incomingCueTimeMs = 0L
        incomingPlaybackRate = 1.0
        outgoingPlaybackRate = 1.0
        rampSteps = 0
        outgoingAnalysis = TrackAnalysis()
        incomingAnalysis = TrackAnalysis()
        phase = Phase.IDLE
    }

    /** Still a next track, still playing, still switched on — by whichever setting armed this one. */
    private fun stillWorthFading(): Boolean {
        val stillOn = if (smartFadeActive) AppSettings.smartFadeEnabled.value else configuredFadeMs() > 0L
        return stillOn && (outgoing ?: active()).hasNextMediaItem()
    }

    /**
     * Puts a player back in the drawer: emptied, silent no longer, and on the
     * listener's own playback rate again.
     *
     * The volume matters as much as the emptying. A player left at the gain it
     * faded out on is the next transition's *incoming* player, and it would
     * arrive already turned down — so the reset is part of retiring it, not part
     * of preparing it.
     */
    private fun retire(player: ExoPlayer) {
        player.stop()
        player.clearMediaItems()
        // This is the next transition's incoming player, and from its handoff
        // the session: a hold left on it would pause every track at its end.
        player.pauseAtEndOfMediaItems = false
        player.volume = 1f
        player.setPlaybackSpeed(AppSettings.playbackSpeed.value)
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
     * [filters] addressed by track rather than by session role.
     *
     * The service wires [TransitionFilters.incoming] to the session player's
     * sink and [TransitionFilters.outgoing] to the spare's, which describes the
     * two tracks only once the handoff has moved the session. The handoff is
     * halfway through the blend now, so for the first half the roles are the
     * other way round, and each ride would otherwise be filtering the wrong
     * song.
     */
    private val tracks = object : TransitionFilters {
        override fun incoming(lowPassHz: Float, highPassHz: Float) {
            if (!aimIncoming) return
            if (handedOff) filters.incoming(lowPassHz, highPassHz) else filters.outgoing(lowPassHz, highPassHz)
        }

        override fun outgoing(lowPassHz: Float, highPassHz: Float) {
            if (!aimOutgoing) return
            if (handedOff) filters.outgoing(lowPassHz, highPassHz) else filters.incoming(lowPassHz, highPassHz)
        }

        override fun incomingLeadMs(): Long =
            if (handedOff) filters.incomingLeadMs() else filters.outgoingLeadMs()

        override fun outgoingLeadMs(): Long =
            if (handedOff) filters.outgoingLeadMs() else filters.incomingLeadMs()

        override fun outgoingEcho(delaySeconds: Float, send: Float, dry: Float) {
            if (!aimOutgoing) return
            if (handedOff) {
                filters.outgoingEcho(delaySeconds, send, dry)
            } else {
                filters.incomingEcho(delaySeconds, send, dry)
            }
        }
    }

    /**
     * Which side a ride pass may aim. Both, unless [rideFilters] is evaluating
     * the style at two different points in the blend — one per side, because
     * each side's filter runs its own distance ahead of the speaker.
     */
    private var aimIncoming = true
    private var aimOutgoing = true

    /**
     * Aims the incoming filter at [incomingAt] and the outgoing one at
     * [outgoingAt]: each the point in the blend its sink is processing *now*,
     * which is seconds past the point being heard — see
     * [TransitionFilterProcessor.leadUs]. Driving both off the heard progress,
     * as this used to, left every filter move seconds late: the incoming track
     * played its first seconds unfiltered, and the entry filter was still
     * lifting after the faders had finished.
     *
     * One style pass when the two agree, two masked passes when they don't —
     * a handful of `pow`s either way. [outgoingAt] is null for the arming
     * case, where the outgoing sink has not reached the blend yet and must be
     * left open.
     */
    private fun rideFilters(incomingAt: Float, outgoingAt: Float?) {
        if (outgoingAt == incomingAt) {
            rideStyle(incomingAt)
            return
        }
        // Incoming first: the few progress-independent branches that open both
        // filters agree with themselves, and the outgoing pass after this one
        // then has the last word on its own side.
        aimOutgoing = false
        rideStyle(incomingAt)
        aimOutgoing = true
        if (outgoingAt == null) return
        aimIncoming = false
        rideStyle(outgoingAt)
        aimIncoming = true
    }

    /**
     * Renders the plan's [TransitionStyle] as filtering across the blend.
     *
     * The gain curve is the same equal-power pair for every style — this is
     * what makes them sound different from each other, and it is the whole of
     * Phase 4. Driven off the same `progress` as the gains so the two stay
     * locked: a pause parks the filter exactly where it parks the fade.
     */
    private fun rideStyle(progress: Float) {
        when (render.style) {
            TransitionStyle.DJ_FILTER -> when (render.filterVariant) {
                FilterTransitionVariant.BASS_HANDOFF -> rideFallbackBassHandoff(progress)
                FilterTransitionVariant.SWEEP,
                FilterTransitionVariant.ECHO_RIDE,
                -> rideFilterSweep(progress)
            }
            TransitionStyle.DJ_BLEND ->
                if (render.bassSwap) rideBassSwap(progress) else rideVocalSeparation(progress)
            // GAPLESS is an album being played through, where any filtering would
            // be an edit the record didn't ask for — so it stays open whatever
            // the material does.
            TransitionStyle.GAPLESS -> filters.open()
            // EQUAL_POWER used to be defined the same way: the bottom tier,
            // reached because the evidence was too weak to justify anything more
            // opinionated, therefore don't touch the spectrum.
            //
            // That conflated two different kinds of evidence. The tier is decided
            // by tempo and beat confidence; whether both tracks are singing is
            // measured by a separate model that doesn't depend on either. A pair
            // can have useless tempo evidence — dropping it to this tier — and a
            // perfectly good vocal mask on both sides saying they collide. Every
            // one of those transitions was rendered as a plain crossfade with two
            // full vocals over each other, because the weak half of the evidence
            // was silencing the strong half.
            TransitionStyle.EQUAL_POWER -> rideVocalSeparation(progress)
        }
        if (render.echo) rideEchoOut(progress)
    }

    /**
     * Echo out, over whatever the style did to the outgoing side: that track
     * stays open — an echo of a muffled signal is only mud — with its low end
     * handed off on the echo beat, the send opening across the beat before it,
     * and the dry signal killed on it. After that only the repeats are left,
     * ringing over the incoming track.
     *
     * Left alone before the send opens, so the echo stage is not even engaged
     * — not a sample touched — until the beat that feeds it.
     */
    private fun rideEchoOut(progress: Float) {
        val p = progress.toDouble()
        val handover = ((p - (render.echoAt - render.swapWidth)) / render.swapWidth).coerceIn(0.0, 1.0)
        tracks.outgoing(TransitionFilterProcessor.OPEN_HZ, bassCutoff(handover))
        if (p < render.echoFrom) return
        val send = if (p >= render.echoAt) 0.0 else (p - render.echoFrom) / (render.echoAt - render.echoFrom)
        tracks.outgoingEcho(render.echoSecondsF, send.toFloat(), echoDry(progress))
    }

    /** The outgoing dry level in an echo out: whole until the echo beat, gone a fraction of a beat after. */
    private fun echoDry(progress: Float): Float =
        (1.0 - ((progress - render.echoAt) / render.echoKill).coerceIn(0.0, 1.0)).toFloat()

    /**
     * How loud the outgoing track actually is at [progress] — uncapped, since
     * an echo tail runs past the end of the blend — for the headroom trim: its
     * fader, or with an echo out, where the fader stays up for the repeats and
     * the chain does the fading, the louder of its dry signal and its repeats.
     * The repeats sum with a full-level incoming track in the platform mixer
     * just as a dry signal would.
     */
    private fun outgoingLevel(progress: Float, wallSpan: Float): Float {
        if (!render.echo) return mixFall(progress)
        val sinceEcho = (progress - render.echoAt.toFloat()) * wallSpan
        val repeats = if (sinceEcho > 0f) sinceEcho / (render.echoSecondsF * 1000f) else 0f
        val wet = if (sinceEcho > 0f) ECHO_LEVEL * ECHO_DECAY.pow(repeats) else 0f
        return maxOf(echoDry(progress), wet)
    }

    /**
     * The minimum intervention: pull two colliding vocals apart, and otherwise
     * leave the spectrum alone.
     *
     * Not a filter ride. [rideFilterSweep] is a *style* — a gesture chosen for a
     * pair that cannot be blended flat, driving to [FILTER_FLOOR_HZ] and taking
     * the outgoing track somewhere distant. This is damage control on a pair that
     * was going to be crossfaded plainly, and it has to stay subtle enough that a
     * listener notices the absence of the clash rather than the presence of a
     * filter. So it works the same way — complementary bands, outgoing losing its
     * top while the incoming enters with its body lifted — over a much shorter
     * distance, and only as far as the measured collision justifies.
     *
     * Zero overlap leaves both sides open, which is exactly what these styles did
     * before, so nothing changes for a pair that doesn't collide or for either
     * track lacking a vocal mask.
     */
    private fun rideVocalSeparation(progress: Float) {
        val amount = render.vocalOverlap.coerceIn(0.0, 1.0)
        if (amount <= 0.0) {
            filters.open()
            return
        }
        val open = TransitionFilterProcessor.OPEN_HZ.toDouble()
        // Both endpoints scaled by the collision, so a marginal clash is nudged
        // and a full one is properly separated, rather than everything getting
        // the same treatment at different speeds.
        val floor = glide(open, VOCAL_SEPARATION_FLOOR_HZ, amount)
        tracks.outgoing(
            glide(open, floor, progress.toDouble().pow(FILTER_SWEEP_SHAPE)).toFloat(),
            TransitionFilterProcessor.OFF_HZ,
        )
        tracks.incoming(
            TransitionFilterProcessor.OPEN_HZ,
            entryHighPass(progress, amount, VOCAL_SEPARATION_HIGH_PASS_HZ, ENTRY_OPEN_BY),
        )
    }

    /**
     * Pulls the outgoing track behind a closing low-pass while the incoming one
     * arrives with its body lifted out, for a pair too far apart in tempo to
     * blend flat.
     *
     * ## Why both sides are filtered
     *
     * The first version filtered only the outgoing track, and squared the
     * progress so that the sweep was spent almost entirely in the second half.
     * Both halves of that were wrong for the same reason: at the midpoint the
     * outgoing cutoff was still at 6.9kHz — wide open across the whole vocal
     * range — and the incoming track was explicitly set to no filtering at all.
     * So for the entire first half of every transition, two complete vocals
     * played over each other at comparable level, and the only thing
     * distinguishing them was gain. That is what a plain crossfade sounds like,
     * which is the one thing this is meant not to be.
     *
     * What a DJ does instead is hand the midrange over rather than double it:
     * the outgoing track starts losing its top the moment the blend begins, and
     * the incoming one enters high-passed — hats and presence only, no vocal
     * body — opening out as the outgoing track darkens. The two occupy
     * complementary bands through the middle of the blend and never compete for
     * the range a voice lives in.
     *
     * [FILTER_SWEEP_SHAPE] is what replaces the squaring: front-loaded now, so
     * the outgoing track's top is gone within the first tenth of the blend
     * rather than somewhere past the midpoint. What keeps that from gutting the
     * track being left is [FILTER_FLOOR_HZ] — the ride settles onto a 300Hz bed
     * and stays there — not restraint in the early travel, which is the part the
     * listener reads as the transition happening at all.
     */
    private fun rideFilterSweep(progress: Float) {
        val sweep = render.filterSweep.coerceIn(0.0, 1.0)
        if (sweep <= 0.0) {
            filters.open()
            return
        }
        // Both ends scaled by [filterSweep], so a partial sweep engages less
        // sharply *and* stops short of the floor rather than crawling the same
        // distance more slowly.
        val open = TransitionFilterProcessor.OPEN_HZ.toDouble()
        val entry = glide(open, FILTER_ENTRY_HZ, sweep)
        val floor = glide(open, FILTER_FLOOR_HZ, sweep)
        val cutoff = glide(entry, floor, progress.toDouble().pow(FILTER_SWEEP_SHAPE))
        val entryCorner = entryHighPass(
            progress,
            sweep,
            glide(ENTRY_HIGH_PASS_HZ, VOCAL_SAFE_ENTRY_HIGH_PASS_HZ, render.clash),
            render.vocalEntryOpenBy,
        )
        if (!render.advanced) {
            tracks.outgoing(cutoff.toFloat(), TransitionFilterProcessor.OFF_HZ)
            tracks.incoming(TransitionFilterProcessor.OPEN_HZ, entryCorner)
            return
        }
        // Two tempi that don't match are two kicks that flam, and the 300Hz bed
        // keeps the outgoing kick whole. So the low end still changes hands
        // once, on a beat — only one kick is ever playing.
        val handover = ((progress - (render.filterSwapAt - render.swapWidth)) / render.swapWidth).coerceIn(0.0, 1.0)
        tracks.outgoing(cutoff.toFloat(), bassCutoff(handover))
        tracks.incoming(TransitionFilterProcessor.OPEN_HZ, maxOf(entryCorner, bassCutoff(1.0 - handover)))
    }

    /**
     * A compact fallback move for unmatched grids: keep the mids recognisable,
     * remove the two kicks from each other's way, then darken the outgoing deck
     * as the incoming low end takes over. This is deliberately a different
     * gesture from the long filter sweep and uses the same two filter stages.
     */
    private fun rideFallbackBassHandoff(progress: Float) {
        val p = progress.toDouble()
        val handover = ((p - (render.filterSwapAt - render.swapWidth)) / render.swapWidth).coerceIn(0.0, 1.0)
        val exitAmount = ((p - render.filterSwapAt) / (1.0 - render.filterSwapAt)).coerceIn(0.0, 1.0)
        val handoffTone = glide(
            TransitionFilterProcessor.OPEN_HZ.toDouble(),
            FALLBACK_HANDOFF_FLOOR_HZ,
            exitAmount.pow(FALLBACK_HANDOFF_SHAPE),
        )
        // If timed masks found two singers, start clearing the outgoing vocal
        // range immediately instead of waiting for the bass exchange.
        val vocalTone = glide(
            TransitionFilterProcessor.OPEN_HZ.toDouble(),
            VOCAL_SEPARATION_FLOOR_HZ,
            render.clash * p.pow(VOCAL_SAFE_EXIT_SHAPE),
        )
        val outgoingTone = minOf(handoffTone, vocalTone).toFloat()
        val incomingEntry = entryHighPass(
            progress,
            1.0,
            glide(FALLBACK_HANDOFF_ENTRY_HZ, VOCAL_SAFE_ENTRY_HIGH_PASS_HZ, render.clash),
            render.vocalEntryOpenBy,
        )
        tracks.outgoing(outgoingTone, bassCutoff(handover))
        tracks.incoming(TransitionFilterProcessor.OPEN_HZ, maxOf(incomingEntry, bassCutoff(1.0 - handover)))
    }

    /**
     * Where the incoming track's high-pass sits at [progress].
     *
     * Rides from [topHz] down to nothing by [openBy] of the fade, so the track
     * is whole well before it is alone — the filter is there to keep it out of
     * the outgoing vocal's way during the overlap, not to colour the track the
     * listener is left with. [amount] scales the whole gesture, so a partial
     * sweep lifts proportionally less out.
     *
     * [ENTRY_SHAPE] is why the descent isn't linear. A geometric glide runs from
     * [TransitionFilterProcessor.OFF_HZ] to [topHz], and the bottom half of that
     * range is sub-bass nobody hears a filter in: measured, a plain ride was
     * down to 123Hz by a third of the way through, which is to say doing nothing
     * at all for two thirds of the overlap. The exponent spends the travel where
     * a voice actually is — 772Hz at a sixth of the way in, 436Hz at a third —
     * and still arrives at fully open on time.
     */
    private fun entryHighPass(progress: Float, amount: Double, topHz: Double, openBy: Double): Float {
        val remaining = (1.0 - progress / openBy).coerceIn(0.0, 1.0)
        return glide(TransitionFilterProcessor.OFF_HZ.toDouble(), topHz, amount * remaining.pow(ENTRY_SHAPE))
            .toFloat()
    }

    /**
     * Geometric interpolation between two cutoffs: [amount] 0 gives [from], 1
     * gives [to].
     *
     * Geometric rather than linear because pitch is logarithmic — a cutoff
     * moving in equal Hz steps sounds like it lurches through the bottom of its
     * range and crawls through the top.
     */
    private fun glide(from: Double, to: Double, amount: Double): Double =
        from * (to / from).pow(amount.coerceIn(0.0, 1.0))

    /**
     * Hands the low end from one track to the other, once, at the beat the
     * planner chose.
     *
     * Below [BASS_SWAP_HZ] exactly one track is present at any instant: the
     * incoming track arrives with its low end lifted out, and takes it over as
     * the outgoing track's is lifted in turn. Ramped over [BASS_SWAP_WIDTH] of
     * the fade rather than switched, because a 24 dB/octave filter appearing in
     * one buffer is a transient of its own.
     *
     * The midrange is handled far more lightly than in [rideFilterSweep] but is
     * no longer left alone, which it was. This style is chosen for pairs that
     * are beat-matched and close in tempo, so the two tracks are *meant* to
     * sound simultaneous — but "simultaneous" and "two lead vocals at once" are
     * not the same thing, and only the bass was ever being separated. So the
     * incoming track still enters with its body lifted, over a shorter window
     * and from a lower corner, and the outgoing track loses its top in the last
     * half, where it is already quiet enough that the change reads as it
     * receding rather than as an effect.
     */
    private fun rideBassSwap(progress: Float) {
        // 0 before the swap window, 1 after it: how much of the low end has
        // changed hands. Classically a window centred on the swap; advanced,
        // half a beat that ends on it, so the new kick arrives on the one.
        val handover = ((progress - render.swapFrom) / render.swapWidth).coerceIn(0.0, 1.0)
        // The incoming track's own low end is already being held out by the
        // swap, so whichever corner sits higher is the one doing the work.
        // Scaled up by however much the two are actually singing over each other.
        // A blend is chosen for pairs on a shared grid, which is the case where
        // nothing about the arrangement separates two lead vocals — they sit in
        // the same bar and the same range for the whole overlap — so the fixed
        // corner that was here handled a marginal collision and a head-on one
        // identically. At full collision the entry corner reaches
        // [BLEND_ENTRY_CLASH_HIGH_PASS_HZ] and holds longer.
        val clash = render.vocalOverlap.coerceIn(0.0, 1.0)
        val entry = maxOf(
            bassCutoff(1.0 - handover),
            entryHighPass(
                progress,
                1.0,
                glide(BLEND_ENTRY_HIGH_PASS_HZ, BLEND_ENTRY_CLASH_HIGH_PASS_HZ, clash),
                render.blendEntryOpenBy,
            ),
        )
        tracks.incoming(TransitionFilterProcessor.OPEN_HZ, entry)
        tracks.outgoing(blendExitLowPass(progress, clash), bassCutoff(handover))
    }

    /**
     * The outgoing track's low-pass through a beat-matched blend: open until
     * [BLEND_EXIT_FROM], then closing to [BLEND_EXIT_LOW_PASS_HZ] by the end.
     *
     * Deliberately shallow. Enough to take the air and the sibilance off a voice
     * that is on its way out, so it stops competing with the one arriving;
     * nowhere near the [FILTER_FLOOR_HZ] that [rideFilterSweep] drives to, which
     * would contradict the reason this style was chosen.
     *
     * [clash] both starts it earlier and takes it further, because "shallow" is
     * the right default and the wrong answer for two choruses landing together.
     */
    private fun blendExitLowPass(progress: Float, clash: Double): Float {
        val from = render.blendExitFrom
        val amount = ((progress - from) / (1.0 - from)).coerceIn(0.0, 1.0)
        val floor = glide(BLEND_EXIT_LOW_PASS_HZ, BLEND_EXIT_CLASH_LOW_PASS_HZ, clash)
        return glide(TransitionFilterProcessor.OPEN_HZ.toDouble(), floor, amount).toFloat()
    }

    /** [amount] 0 leaves the low end alone; 1 lifts it out entirely. */
    private fun bassCutoff(amount: Double): Float =
        glide(TransitionFilterProcessor.OFF_HZ.toDouble(), BASS_SWAP_HZ, amount).toFloat()

    /**
     * Whether the transition in flight is doing something a plain crossfade
     * could not — which is what [AppSettings.smartMixInProgress] promises the
     * listener when it lights the scrubber up.
     *
     * Any one of three things qualifies, because they are the three things
     * analysis buys: a style that filters or swaps bass, an incoming track cued
     * into its arrangement instead of its first frame, or a tempo stretch. The
     * case this exists to exclude is the fallback — an unanalysed pair, cued at
     * 0:00, fading equal-power — which is indistinguishable from what the app
     * did before Automix existed and would be a lie to advertise.
     */
    private fun isRealMix(): Boolean = smartFadeActive && (
        render.style == TransitionStyle.DJ_BLEND ||
            render.style == TransitionStyle.DJ_FILTER ||
            incomingCueTimeMs > 0L ||
            incomingPlaybackRate != 1.0 ||
            outgoingPlaybackRate != 1.0
        )

    /** Equal-power pair: [riseGain]² + [fallGain]² = 1, so the blend never dips. */
    private fun riseGain(progress: Float): Float =
        sin(progress.coerceIn(0f, 1f) * PI.toFloat() / 2f)

    private fun fallGain(progress: Float): Float =
        cos(progress.coerceIn(0f, 1f) * PI.toFloat() / 2f)

    /**
     * The incoming fader for the blend in flight. Classic Automix — and every
     * non-DJ style — is the equal-power [riseGain]. Advanced, each style gets
     * the curve a DJ would ride it on, with the filters doing the separating:
     *  - a bass-swap blend has the incoming fader fully up by the swap
     *    ("both faders up"), backed off toward equal power as far as the two
     *    vocals collide;
     *  - a filter ride brings it up a little early, behind its high-pass.
     *
     * [headroomFor] keeps two faders at full from summing past full scale.
     */
    private fun mixRise(progress: Float): Float {
        if (!render.advanced) return riseGain(progress)
        val styled = when (render.style) {
            TransitionStyle.DJ_BLEND ->
                if (render.bassSwap) held(riseGain(progress), riseGain(progress / render.swapAtF)) else riseGain(progress)
            TransitionStyle.DJ_FILTER -> when (render.filterVariant) {
                FilterTransitionVariant.BASS_HANDOFF -> riseGain(progress / render.filterSwapAtF)
                FilterTransitionVariant.ECHO_RIDE -> riseGain(progress.pow(ECHO_RIDE_RISE_SHAPE))
                FilterTransitionVariant.SWEEP -> riseGain(progress.pow(FILTER_RISE_SHAPE))
            }
            else -> riseGain(progress)
        }
        val vocalSafe = riseGain(progress.pow(VOCAL_SAFE_RISE_SHAPE))
        return styled + (vocalSafe - styled) * render.clash.toFloat()
    }

    /**
     * The outgoing fader, [mixRise]'s partner: held at full until the bass
     * swap in a blend and pulled after it, held longer in a filter ride where
     * the closing low-pass is already taking it away.
     */
    private fun mixFall(progress: Float): Float {
        if (!render.advanced) return fallGain(progress)
        // The fader carries the repeats too, so an echo out leaves it up and
        // fades the dry signal in the chain instead — see [rideEchoOut].
        if (render.echo) return 1f
        val styled = when (render.style) {
            TransitionStyle.DJ_BLEND -> {
                if (!render.bassSwap) {
                    fallGain(progress)
                } else {
                    val swap = render.swapAtF
                    held(fallGain(progress), if (progress <= swap) 1f else fallGain((progress - swap) / (1f - swap)))
                }
            }
            TransitionStyle.DJ_FILTER -> when (render.filterVariant) {
                FilterTransitionVariant.BASS_HANDOFF -> {
                    val swap = render.filterSwapAtF
                    if (progress <= swap) 1f else fallGain((progress - swap) / (1f - swap))
                }
                FilterTransitionVariant.ECHO_RIDE -> fallGain(progress.pow(ECHO_RIDE_FALL_SHAPE))
                FilterTransitionVariant.SWEEP -> fallGain(progress.pow(FILTER_FALL_SHAPE))
            }
            else -> fallGain(progress)
        }
        val vocalSafe = fallGain(progress.pow(VOCAL_SAFE_FALL_SHAPE))
        return styled + (vocalSafe - styled) * render.clash.toFloat()
    }

    /** [dj] as far as [Render.holdFaders] allows, the rest of the way back toward [plain]. */
    private fun held(plain: Float, dj: Float): Float = plain + (dj - plain) * render.holdFaders

    // ---- Phase lock ---------------------------------------------------------

    /**
     * Keeps the incoming track's beats on the outgoing track's, the way a DJ
     * rides the jog wheel: measure how far apart the two beats are, then run the
     * incoming track a few percent fast or slow for just long enough to close
     * the gap.
     *
     * The incoming track starts on a 40ms arming tick plus however long its
     * player takes to start, so it lands anything up to tens of milliseconds
     * off the beat — the "galloping" of a bad mix. Nothing measured or
     * corrected that before.
     *
     * Pulsed rather than continuous on purpose. A speed change is only heard
     * once the audio already buffered ahead has played out, so a controller
     * that reacted to every reading would keep correcting an error it had
     * already fixed and oscillate. A pulse of known size and length moves the
     * incoming track by exactly the measured error however late it lands, and
     * the lock then waits [LOCK_SETTLE_MS] before measuring again. Readings are
     * taken [LOCK_SAMPLES] at a time and the median used, because a player's
     * reported position jitters by about as much as the error being fixed.
     *
     * No new processing: this is the same speed control the beatmatch stretch
     * already runs through, moved briefly.
     */
    private fun holdPhase(out: ExoPlayer, into: ExoPlayer, progress: Float) {
        val now = SystemClock.elapsedRealtime()
        if (nudgeUntil != 0L) {
            // A pause mid-pulse ends it early; the next reading picks up the rest.
            if (now < nudgeUntil && into.isPlaying) return
            nudgeUntil = 0L
            into.setPlaybackSpeed(incomingBaseSpeed())
            lockQuietUntil = now + LOCK_SETTLE_MS
            lockCount = 0
            return
        }
        // Once the outgoing track is on its way out its beat stops mattering.
        if (progress >= LOCK_UNTIL || now < lockQuietUntil) return
        if (!out.isPlaying || !into.isPlaying) {
            lockCount = 0
            return
        }
        val error = phaseError(out, into)
        if (error.isNaN()) return
        lockErrors[lockCount++] = error
        if (lockCount < LOCK_SAMPLES) return
        lockCount = 0
        lockErrors.sort()
        val median = lockErrors[LOCK_SAMPLES / 2]
        if (abs(median) < LOCK_DEADBAND_SECONDS) {
            lockQuietUntil = now + LOCK_IN_PHASE_MS
            return
        }
        val base = incomingBaseSpeed()
        // Ahead slows down, behind speeds up; the pulse lasts as long as it
        // takes that offset in speed to cover the error.
        // Speeding up is capped so a nudge stacked on a large stretch never
        // takes the incoming track past [MAX_TOTAL_SPEED_UP].
        val factor = if (median > 0) {
            1f - NUDGE
        } else {
            minOf(1f + NUDGE, (MAX_TOTAL_SPEED_UP / incomingPlaybackRate).toFloat())
        }
        if (factor <= 1f && median < 0) return
        into.setPlaybackSpeed(base * factor)
        nudgeUntil = now + (abs(median) / (base * abs(factor - 1f)) * 1000).roundToLong().coerceIn(1L, MAX_NUDGE_MS)
    }

    private fun incomingBaseSpeed(): Float = (AppSettings.playbackSpeed.value * incomingPlaybackRate).toFloat()

    /**
     * How far the incoming beat sits ahead (positive) or behind the outgoing
     * one, in incoming-timeline seconds, or NaN when either has no grid or
     * the two are too far apart for this to be a phase error rather than a
     * grid disagreement.
     *
     * Read in outgoing seconds so the two grids compare directly: the incoming
     * one is divided by the stretch. Wrapped to the shorter of the two beats,
     * so a track counted at half or double time against the other still
     * locks on the shared pulse.
     */
    private fun phaseError(out: ExoPlayer, into: ExoPlayer): Double {
        if (!beatAt(outgoingAnalysis, positionMs(out) / 1000.0)) return Double.NaN
        val outSince = beatScratch[0]
        val outBeat = beatScratch[1]
        if (!beatAt(incomingAnalysis, positionMs(into) / 1000.0)) return Double.NaN
        val rate = mediaRatio
        val period = minOf(outBeat, beatScratch[1] / rate)
        var diff = (beatScratch[0] / rate - outSince) % period
        if (diff > period / 2) diff -= period else if (diff < -period / 2) diff += period
        if (abs(diff) > period * LOCK_MAX_BEATS) return Double.NaN
        return diff * rate
    }

    /**
     * Where [positionSec] falls in [analysis]'s beat: seconds past the last
     * beat into [beatScratch]`[0]`, that beat's length into `[1]`. False when
     * the track has no grid at all.
     *
     * Beats are counted within the bar the analyzer's downbeats bound, not off
     * one constant interval from the first beat, so a track that drifts still
     * reads its own local beat rather than one that slid minutes ago.
     */
    private fun beatAt(analysis: TrackAnalysis, positionSec: Double): Boolean {
        // The tempo is the authority. A stored beat interval that disagrees with
        // it by more than a quarter is not a beat — a head-only analysis was seen
        // carrying one of 24.7s on a 127 BPM track, which stopped the scrubber's
        // pulse dead for the rest of the blend.
        val fromTempo = if (analysis.bpm > 0.0) 60.0 / analysis.bpm else 0.0
        val interval = analysis.beatInterval
        val nominal = when {
            interval > 0.0 && (fromTempo <= 0.0 || abs(interval / fromTempo - 1.0) < 0.25) -> interval
            fromTempo > 0.0 -> fromTempo
            else -> return false
        }
        val downbeats = analysis.downbeats
        val next = downbeats.binarySearch(positionSec).let { if (it >= 0) it + 1 else -it - 1 }
        val origin: Double
        var beat = nominal
        if (next in 1 until downbeats.size) {
            origin = downbeats[next - 1]
            val bar = downbeats[next] - origin
            // A bar of one to eight beats is a bar, counted from the analyzer's own
            // downbeats. Anything longer is a stretch the analyzer found no
            // downbeat in, and dividing it up would invent a tempo.
            val beatsInBar = (bar / nominal).roundToInt()
            if (beatsInBar in 1..8) beat = bar / beatsInBar
        } else {
            origin = if (next >= 1) downbeats[next - 1] else analysis.firstBeat
        }
        if (beat <= 0.0 || !beat.isFinite()) return false
        beatScratch[0] = ((positionSec - origin) % beat + beat) % beat
        beatScratch[1] = beat
        return true
    }

    // ---- Tempo ease-back ----------------------------------------------------

    /**
     * Starts walking [player] off a beatmatch stretch of [rate], one small
     * step per beat of its own track, instead of snapping it back to the
     * listener's speed the moment the blend ends.
     *
     * Each step is at most [TEMPO_STEP_PER_BEAT] — under what anyone hears as
     * the tempo moving — so the length follows the stretch: a 10% speed-up
     * comes off over 14 beats, a 3% one over 4. One speed change per beat, not
     * per tick: each is a real change to the audio path.
     */
    private fun beginEase(player: ExoPlayer, rate: Double, analysis: TrackAnalysis) {
        val stretch = rate - 1.0
        if (abs(stretch) < EASE_DONE) {
            player.setPlaybackSpeed(AppSettings.playbackSpeed.value)
            return
        }
        val beat = when {
            analysis.beatInterval > 0.0 -> analysis.beatInterval
            analysis.bpm > 0.0 -> 60.0 / analysis.bpm
            else -> 0.0
        }
        val steps = rampStepsFor(rate)
        easeStep = stretch / steps
        easeRate = rate
        easeStepMs = if (beat > 0.0) (beat * 1000).roundToLong() else EASE_FALLBACK_STEP_MS
        easeItemIndex = player.currentMediaItemIndex
        easeNextAtMs = player.currentPosition + easeStepMs
        easing = player
        // Drops any phase-lock nudge still in flight back onto the plain stretch.
        setPlaybackSpeedClickless(player, (AppSettings.playbackSpeed.value * rate).toFloat())
    }

    /**
     * One step of the ease, when the playhead has crossed the next bar.
     * Measured on the track's own position so a pause holds it. Anything that
     * moves the listener off the stretched track — a skip, the next song, a
     * seek backwards, a party starting, the player no longer being the
     * session's — ends it on the listener's speed at once.
     */
    private fun driveEase() {
        val player = easing ?: return
        val position = player.currentPosition
        if (player !== active() ||
            player.currentMediaItemIndex != easeItemIndex ||
            position < easeNextAtMs - 2 * easeStepMs ||
            ListenTogether.state.value.inParty
        ) {
            endEase()
            return
        }
        if (position < easeNextAtMs) return
        easeRate -= easeStep
        if (abs(easeRate - 1.0) < EASE_DONE) {
            endEase()
            return
        }
        setPlaybackSpeedClickless(player, (AppSettings.playbackSpeed.value * easeRate).toFloat())
        easeNextAtMs += easeStepMs
    }

    /**
     * Brings the outgoing track up to [outgoingPlaybackRate] over its last
     * [rampSteps] beats before the fade, one [TEMPO_STEP_PER_BEAT] step per
     * beat, so it arrives at the incoming track's tempo instead of the incoming
     * track being slowed to its own. Stepped off the track's position, so a
     * tick landing late only delays a step, never skips the tempo it reaches.
     */
    private fun rampOutgoing(out: ExoPlayer) {
        val due = ((out.currentPosition - rampStartMs) / rampStepMs + 1).toInt().coerceIn(0, rampSteps)
        if (due <= rampStep) return
        rampStep = due
        val rate = 1.0 + (outgoingPlaybackRate - 1.0) * due / rampSteps
        out.setPlaybackSpeed((AppSettings.playbackSpeed.value * rate).toFloat())
    }

    /** Beats a tempo change of [rate] takes at [TEMPO_STEP_PER_BEAT]; 0 for none. */
    private fun rampStepsFor(rate: Double): Int {
        val stretch = abs(rate - 1.0)
        return if (stretch < EASE_DONE) 0 else ceil(stretch / TEMPO_STEP_PER_BEAT).toInt()
    }

    /** Puts the easing player on the listener's own speed and forgets it. Idempotent. */
    private fun endEase(clickless: Boolean = true) {
        val player = easing ?: return
        easing = null
        if (clickless) {
            setPlaybackSpeedClickless(player, AppSettings.playbackSpeed.value)
        } else {
            softSpeedJob?.cancel()
            softSpeedJob = null
            player.volume = 1f
            player.setPlaybackSpeed(AppSettings.playbackSpeed.value)
        }
    }

    /**
     * Changes speed behind a very short gain notch. Android's AudioTrack speed
     * path can expose a discontinuity when PlaybackParams changes with a
     * non-zero sample under the cursor; the notch puts that boundary at silence.
     * Used only between transitions, so the fade's own gain automation never
     * competes with it. The incoming deck is configured while silent instead.
     */
    private fun setPlaybackSpeedClickless(player: ExoPlayer, target: Float) {
        if (abs(player.playbackParameters.speed - target) < SPEED_CHANGE_EPSILON) return
        if (!player.isPlaying || player !== active() || player.volume <= 0f) {
            player.setPlaybackSpeed(target)
            return
        }
        softSpeedJob?.cancel()
        softSpeedJob = scope.launch {
            val level = player.volume
            try {
                for (step in 1..SPEED_NOTCH_STEPS) {
                    player.volume = level * (1f - step.toFloat() / SPEED_NOTCH_STEPS)
                    delay(SPEED_NOTCH_STEP_MS)
                }
                player.setPlaybackSpeed(target)
                delay(SPEED_NOTCH_HOLD_MS)
                for (step in 1..SPEED_NOTCH_STEPS) {
                    player.volume = level * step.toFloat() / SPEED_NOTCH_STEPS
                    delay(SPEED_NOTCH_STEP_MS)
                }
            } finally {
                if (player === active() && phase == Phase.IDLE) player.volume = level
            }
        }
    }

    /** See [onBlendHeadroom]. */
    private fun headroomFor(incomingGain: Float, outgoingGain: Float): Float {
        val sum = incomingGain + outgoingGain
        return if (sum <= 1f) 1f else 1f / kotlin.math.sqrt(sum)
    }

    // There is deliberately no second, equal-gain pair here any more. It existed
    // for the handoff of a track from one player to the other, where the two
    // signals were the same signal and so summed in amplitude rather than in
    // power. Nothing in this class renders the same audio twice now, so every
    // gain it applies is a gain against a genuinely different track, and
    // equal-power is right everywhere.

    private companion object {
        const val TAG = "BitChordCrossfade"

        /**
         * Used only before a pair has been analysed, or when the evidence is
         * too weak for more than a plain fade — see [considerSmartTransition].
         * Once real analysis lands, the overlap is sized from tempo and
         * structure instead and this is never read.
         */
        const val DEFAULT_SMART_FALLBACK_SECONDS = 6.0

        /** Ramp used when a fade is interrupted. */
        const val BAIL_MS = 120L

        /**
         * Head start the standby gets to open the incoming track and buffer to
         * its cue point.
         *
         * Sized for a *stream being opened*, which is the only thing arming
         * waits on now — there is no alignment to converge. Usually instant, as
         * the next track has normally been read ahead onto disk by the time it
         * matters, but a cold one has to be resolved and fetched, and a
         * transition that arrives before its incoming track is ready is one that
         * gets dropped.
         */
        const val ARM_LEAD_MS = 4_000L

        /**
         * What a player reads beyond its sink's lead before it reaches the next
         * track: the decoder's buffers in flight, and an idle tick or two of this
         * class getting round to arming. See [holdAtEnd].
         */
        const val READ_AHEAD_MARGIN_MS = 1_500L

        /**
         * The sink's lead when it has not reported one. A float route's runs to
         * about four seconds (see [TransitionFilterProcessor.leadUs]); this
         * errs long, since guessing short is the costly mistake.
         */
        const val FALLBACK_SINK_LEAD_MS = 6_000L

        /**
         * States in which a track is measured well enough to be *entered* on.
         *
         * [TrackAnalysisState.REFINING] belongs here because the entry fields —
         * tempo, beat confidence, the cue point — are all measured over the
         * track's opening, which is precisely what a head-only pass reads. The
         * whole-track pass it is waiting on adds the *exit* half: content end,
         * outro, mix-out anchors, the energy curve. Those matter when this track
         * is later the one being left, and not at all for the transition into it.
         */
        val MEASURED_ENOUGH_TO_ENTER_ON = setOf(
            TrackAnalysisState.ANALYSED,
            TrackAnalysisState.REFINING,
        )

        /**
         * Longest a transition will wait on an incoming track that will not
         * become ready. Past this the queue is left to move on plainly, which is
         * a missed crossfade rather than a broken one.
         */
        const val ARM_TIMEOUT_MS = 12_000L

        /**
         * Where the outgoing low-pass sits the instant a filter ride begins.
         *
         * The ride used to start from [TransitionFilterProcessor.OPEN_HZ] and
         * travel down, which meant the first stretch of every transition was
         * spent crossing a range nobody can hear a filter in: a tenth of the way
         * through the fade the cutoff was still at 17.5kHz, indistinguishable
         * from no filter at all, and the ride only became audible around the
         * midpoint. Engaging here instead — above the fundamentals of everything
         * but cymbals, so what goes first is air and shimmer — is what makes the
         * gesture read as a hand landing on the filter the moment the blend
         * starts, rather than something remembered late.
         *
         * 9kHz was the first attempt at that and still read as late by ear: it
         * is above everything but cymbals, so engaging there takes the air off
         * and nothing else, and the outgoing vocal — the thing actually clashing
         * — was untouched until the sweep had travelled most of the way down.
         * 7kHz is inside the presence range, so the gesture is audible on the
         * voice itself from the first instant.
         */
        const val FILTER_ENTRY_HZ = 7_000.0

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

        // ---- Advanced Automix ----------------------------------------------

        /**
         * Advanced: the low end changes hands over this many beats, ending on
         * the swap beat. Half a beat is a hand on the EQ, not a kill switch —
         * long enough that the 24 dB/octave corner moving is no click, short
         * enough that the two kicks never play together.
         */
        const val SWAP_BEATS = 0.5

        /** Where a filter ride hands its low end over, before bar-snapping. */
        const val FILTER_BASS_SWAP_AT = 0.5

        /**
         * Filter-ride fader shapes, as exponents on progress. Above 1 holds the
         * outgoing fader up longer, since the closing low-pass is already
         * taking it away; below 1 brings the incoming one up a little early
         * behind its high-pass.
         */
        const val FILTER_FALL_SHAPE = 1.6f
        const val FILTER_RISE_SHAPE = 0.8f

        /** Alternate fallback: a quick bass exchange followed by a shallow exit filter. */
        const val FALLBACK_HANDOFF_ENTRY_HZ = 900.0
        const val FALLBACK_HANDOFF_FLOOR_HZ = 1_600.0
        const val FALLBACK_HANDOFF_SHAPE = 0.8

        /** Vocal-aware fallback: keep the arriving lead out until the outgoing lead has cleared. */
        const val VOCAL_SAFE_ENTRY_HIGH_PASS_HZ = 2_400.0
        const val VOCAL_SAFE_ENTRY_OPEN_BY = 0.78
        const val VOCAL_SAFE_EXIT_SHAPE = 0.55
        const val VOCAL_SAFE_RISE_SHAPE = 1.65f
        const val VOCAL_SAFE_FALL_SHAPE = 0.65f

        /** Echo personality keeps both decks recognisable until its decisive fader pull. */
        const val ECHO_RIDE_RISE_SHAPE = 0.7f
        const val ECHO_RIDE_FALL_SHAPE = 1.35f

        /**
         * An echo out's dry signal is killed over this much of a beat: short
         * enough to read as the fader being pulled, long enough not to click.
         */
        const val ECHO_KILL_BEATS = 0.25

        /**
         * The echo's first repeat against the dry level, and what each repeat
         * keeps of the one before — [TransitionFilterProcessor]'s wet and
         * feedback, mirrored here for the headroom trim.
         */
        const val ECHO_LEVEL = 0.5f
        const val ECHO_DECAY = 0.4f

        /** Grace after the incoming track starts before its position is trusted for phase. */
        const val LOCK_START_MS = 300L

        /** Phase readings per decision; the median of these is what gets corrected. */
        const val LOCK_SAMPLES = 5

        /** Under this the two beats are together as far as anyone can hear. */
        const val LOCK_DEADBAND_SECONDS = 0.008

        /** After a nudge: long enough for the audio already buffered to play out at the new speed. */
        const val LOCK_SETTLE_MS = 400L

        /** After a reading in phase, how long before looking again. */
        const val LOCK_IN_PHASE_MS = 500L

        /**
         * How far a nudge moves the incoming speed. Inside the stretch the policy
         * already calls transparent, and over a pulse this short nobody hears
         * the tempo move — just the beats pulling together.
         */
        const val NUDGE = 0.03f

        /** Longest single nudge; anything left over is corrected on the next reading. */
        const val MAX_NUDGE_MS = 1_500L

        /** Past this much of the blend the outgoing track is too far gone for its beat to matter. */
        const val LOCK_UNTIL = 0.9f

        /**
         * More than this far apart — in beats of the shared pulse — is two grids
         * disagreeing about where the beat is, not a start that landed late, and
         * nudging toward it would only make it worse.
         */
        const val LOCK_MAX_BEATS = 0.3

        /**
         * The most the tempo moves in one beat, ramping up or easing back.
         * Gradual drift below about 1% a beat reads as the groove, not as the
         * track changing speed.
         */
        const val TEMPO_STEP_PER_BEAT = 0.0075

        /** The ceiling on a phase-lock nudge stacked on a speed-up. */
        const val MAX_TOTAL_SPEED_UP = 1.12

        /** Close enough to the listener's own speed to call the ease done. */
        const val EASE_DONE = 1e-4

        /** A step every this long when the track has no beat to step on. */
        const val EASE_FALLBACK_STEP_MS = 500L

        /** Short silence around an audible AudioTrack PlaybackParams update. */
        const val SPEED_NOTCH_STEPS = 2
        const val SPEED_NOTCH_STEP_MS = 2L
        const val SPEED_NOTCH_HOLD_MS = 2L
        const val SPEED_CHANGE_EPSILON = 0.0001f

        /**
         * Shape of the outgoing low-pass against fade progress, between
         * [FILTER_ENTRY_HZ] and [FILTER_FLOOR_HZ].
         *
         * Was 2.0 — squared — which left the cutoff at 6.9kHz at the midpoint,
         * so the outgoing vocal went untouched through the whole first half of
         * every transition. Then 1.3, which was still back-loaded: the exponent
         * held the cutoff near its entry point through the opening of the fade,
         * which is precisely where the two vocals overlap at comparable level.
         *
         * Below 1 now, so the ride is front-loaded — steepest at the start,
         * flattening as it approaches the floor. That is the shape of the gesture
         * being imitated: a hand moves a filter knob fast and then eases it in,
         * not the reverse. The old worry that a fast cutoff takes the outgoing
         * track out prematurely is answered by [FILTER_FLOOR_HZ] rather than by
         * the exponent — the ride bottoms out at 300Hz, which is still a present
         * bed under the incoming track, not silence.
         *
         * Crosses 5kHz — about where a low-pass becomes plainly audible on a
         * full-range mix — a twentieth of the way into the fade, against a
         * quarter of the way at 1.3. Lands at 3.8kHz a tenth of the way in,
         * 2.6kHz at a fifth, 1.0kHz at the midpoint.
         */
        const val FILTER_SWEEP_SHAPE = 0.75

        /**
         * Where the incoming track's high-pass starts on a filter ride.
         *
         * Above the fundamental range of most voices and the body of a snare, so
         * what arrives first is presence and percussion — enough to hear a track
         * coming and lock onto its groove, not enough for a second lead vocal.
         *
         * 700Hz was that corner while the outgoing sweep was gentler. It no longer
         * is: the sweep engages at [FILTER_ENTRY_HZ] and is down to 4kHz a tenth
         * of the way in, so a 700Hz entry left the two tracks sharing very nearly
         * three octaves — and sharing them from 529Hz up, which is exactly where a
         * lead vocal's fundamentals sit. 1.2kHz takes about an octave off the
         * bottom of that shared band, and it is the octave the collision actually
         * happens in. What is left of the outgoing track then sits *under* the
         * arriving one rather than inside it, which is what makes the incoming
         * track read as a layer landing on top of a darkening one instead of a
         * second voice in the same space.
         */
        const val ENTRY_HIGH_PASS_HZ = 1_200.0

        /**
         * How far into the fade the incoming track is fully open again.
         *
         * Comfortably before the end: past this point the outgoing track is deep
         * into its own sweep and quiet with it, so there is nothing left to keep
         * out of the way of, and anything still filtered here would just be the
         * new track arriving wrong.
         */
        const val ENTRY_OPEN_BY = 0.6

        /**
         * Shape of the incoming high-pass's descent; see [entryHighPass].
         *
         * Below 1 so the corner lingers in the range a voice occupies instead of
         * dropping straight through it into sub-bass, where a high-pass is
         * inaudible and the clash this exists to prevent is already back.
         *
         * 0.35 rather than 0.45 for more of the same, and the effect compounds
         * across the overlap rather than being a flat offset: on a filter ride the
         * corner sits a fourteenth higher a tenth of the way in, a quarter higher
         * at three tenths, a third higher at four. So the hold is back-loaded into
         * the middle of the blend — where both tracks are near equal gain and the
         * collision is at its worst — and what gets given up in exchange is the
         * bottom of the descent, which is a few hundred hertz of sub-bass nobody
         * hears a high-pass leave. The release into the last of [ENTRY_OPEN_BY] is
         * correspondingly more of an event, which is the point: the arriving track
         * opening out is the moment the listener is meant to notice.
         */
        const val ENTRY_SHAPE = 0.35

        /**
         * How far [rideVocalSeparation] closes the outgoing track's top at a
         * full collision.
         *
         * Well above [FILTER_FLOOR_HZ]'s 300Hz, because this fires on pairs that
         * were going to be crossfaded plainly and the intent is to stop two
         * voices competing, not to send one of them into another room. 1.6kHz is
         * below the presence and sibilance a lead vocal is picked out by, and
         * above enough of its body that the track still reads as itself.
         */
        const val VOCAL_SEPARATION_FLOOR_HZ = 1_600.0

        /**
         * Where the incoming track's high-pass starts in [rideVocalSeparation].
         *
         * Lower than [ENTRY_HIGH_PASS_HZ]'s 1.2kHz, for the same reason the floor
         * is higher: on a plain crossfade the arriving track has no filter
         * gesture to explain itself with, so it has to sound like it fades in
         * normally. 700Hz clears the body of a voice while leaving its lower
         * harmonics, which is enough to stop it fighting the outgoing lead.
         *
         * Was 450Hz, which fit that description on paper and was mostly inaudible
         * in practice: a fifth of the way in it was already down to 268Hz, doing
         * nothing about a collision the vocal model had reported at full strength.
         * 700Hz is the corner a filter ride itself used to open at, so it is a
         * known-restrained one rather than a new guess — and keeping this style a
         * clear step below that one leaves the two ranked the way their tiers are.
         */
        const val VOCAL_SEPARATION_HIGH_PASS_HZ = 700.0

        /**
         * [ENTRY_HIGH_PASS_HZ]'s counterpart for a beat-matched blend: lower, and
         * briefer.
         *
         * Was 320Hz, which the bass swap almost entirely swallowed. The incoming
         * track is already high-passed at [BASS_SWAP_HZ] until the low end changes
         * hands and [rideBassSwap] takes whichever corner is higher, so a 320Hz
         * entry was only above that floor for the first sixth of the blend, and
         * only ever by a little. 520Hz gives the arriving track an entry gesture
         * that outlives the bass kill — clear of it until nearly three tenths in —
         * rather than one hiding inside it.
         */
        const val BLEND_ENTRY_HIGH_PASS_HZ = 520.0

        /** [ENTRY_OPEN_BY]'s counterpart for a beat-matched blend. */
        const val BLEND_ENTRY_OPEN_BY = 0.45

        /**
         * Where [BLEND_ENTRY_HIGH_PASS_HZ] and [BLEND_ENTRY_OPEN_BY] go at a full
         * vocal collision: a corner high enough to hold the arriving voice's body
         * out, held for most of the blend rather than a third of it.
         *
         * Still short of [ENTRY_HIGH_PASS_HZ]'s filter-ride treatment. The two
         * tracks are on a shared grid and meant to sound simultaneous; the aim is
         * to stop the two leads occupying one band, not to hide either of them.
         *
         * Tracks [BLEND_ENTRY_HIGH_PASS_HZ] upward — 620Hz to 950Hz — so how hard
         * the two are singing over each other stays the thing that separates a
         * marginal collision from a head-on one, rather than both converging on
         * whatever the bass kill was already doing.
         */
        const val BLEND_ENTRY_CLASH_HIGH_PASS_HZ = 950.0
        const val BLEND_ENTRY_CLASH_OPEN_BY = 0.7

        /** Where [BLEND_EXIT_FROM] and [BLEND_EXIT_LOW_PASS_HZ] go at a full collision. */
        const val BLEND_EXIT_CLASH_FROM = 0.12
        const val BLEND_EXIT_CLASH_LOW_PASS_HZ = 1_100.0

        /**
         * Where the outgoing track starts losing its top on a beat-matched
         * blend.
         *
         * Was 0.5, which left the outgoing track completely unfiltered for the
         * whole first half — the same "remembered late" complaint that
         * [FILTER_ENTRY_HZ] answers on a filter ride, in the one style where
         * both tracks are at their most similar and so most likely to clash.
         * Brought forward rather than to zero: a beat-matched blend is chosen
         * because the two tracks are meant to sound simultaneous, and opening
         * with the outgoing one already darkened would defeat that.
         */
        const val BLEND_EXIT_FROM = 0.3

        /**
         * Where that low-pass lands by the end of the blend. High enough that the
         * track is still plainly itself — this style is chosen for pairs meant to
         * sound simultaneous — and low enough to take the sibilance off a voice
         * that is leaving.
         */
        const val BLEND_EXIT_LOW_PASS_HZ = 2_200.0

        /**
         * Where in the blend the session moves to the incoming track: halfway,
         * which with an equal-power curve is the instant the two are equally
         * loud and the incoming one takes over.
         */
        const val HANDOFF_AT = 0.5f

        /**
         * Hands off early when the outgoing track is this close to its end,
         * before its still-intact queue can advance it. Several fade ticks, so
         * one late tick cannot miss it.
         */
        const val HANDOFF_GUARD_MS = 400L

        /**
         * How far a freshly derived beat anchor may sit from the published one
         * before it is worth sending. Above a tick's worth of position jitter,
         * well under what the eye reads as a pulse off the beat.
         */
        const val ANCHOR_TOLERANCE_NANOS = 25_000_000L

        /** Relative tempo change worth re-publishing; beatmatch rates are fixed per blend. */
        const val TEMPO_TOLERANCE = 0.005f

        const val IDLE_STEP_MS = 250L

        /**
         * Arming only waits on a buffer now — nothing is being converged — so
         * this is about how promptly the fade can start once the incoming track
         * is ready, not about a control loop's step size.
         */
        const val ARM_STEP_MS = 40L
        const val FADE_STEP_MS = 30L

        /** A new position report landed somewhere in the last fade tick; half of one is the best guess. */
        const val HALF_TICK_NANOS = FADE_STEP_MS * 500_000L

        /** Never run a stalled player's position on further than its loop could plausibly sleep. */
        const val MAX_EXTRAPOLATION_NANOS = 600_000_000L
        const val BAIL_STEP_MS = 15L
    }
}
