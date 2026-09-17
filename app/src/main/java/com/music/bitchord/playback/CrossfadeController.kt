package com.music.bitchord.playback

import android.os.SystemClock
import com.music.bitchord.data.TrackLog
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.OutputPcmMode
import com.music.bitchord.data.settings.SmartAnalysis
import com.music.bitchord.data.settings.TrackAnalysisState
import com.music.bitchord.data.settings.TransitionWindow
import com.music.bitchord.playback.smart.CrossfadeMode
import com.music.bitchord.playback.smart.BASS_SWAP_WIDTH_V2
import com.music.bitchord.playback.smart.MID_KILL_LP_HZ
import com.music.bitchord.playback.smart.MID_KILL_BED_HZ
import com.music.bitchord.playback.smart.MID_KILL_STAGGERED_HP_HZ
import com.music.bitchord.playback.smart.MID_KILL_START_HZ
import com.music.bitchord.playback.smart.TrackAnalysis
import com.music.bitchord.playback.smart.EnergySample
import com.music.bitchord.playback.smart.TransitionPlan
import com.music.bitchord.playback.smart.TransitionStyle
import com.music.bitchord.playback.smart.TransitionTrackInfo
import com.music.bitchord.playback.smart.TransitionType
import com.music.bitchord.playback.smart.EqSchedule
import com.music.bitchord.playback.smart.VolumeCurve
import com.music.bitchord.playback.smart.planTransition
import com.music.bitchord.playback.smart.vocalActivityBetween
import com.music.bitchord.playback.smart.firstVocalStartSec
import com.music.bitchord.playback.smart.firstQuietGapSec
import com.music.bitchord.playback.smart.phrase16Grid
import com.music.bitchord.playback.smart.orZero
import com.music.bitchord.playback.smart.VOCAL_ACTIVE_THRESHOLD
import com.music.bitchord.playback.smart.MIN_GUARANTEED_BLEND_SECONDS
import kotlin.math.max
import kotlin.math.min
import com.music.bitchord.playback.smart.plainDissolvePlan
import com.music.bitchord.playback.smart.MixRecipe
import com.music.bitchord.playback.smart.selectMixRecipe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import java.util.Locale

/**
 * Tempo glide-back factor for a beatmatched handoff: 1 at the fade start,
 * easing to 0 (own tempo) by its end. The glide window scales with the
 * stretch magnitude — an 8% nudge rides home over the last 40% of the fade
 * while a half-time 50% stretch starts coming back at 25% — so small
 * corrections stay locked to the shared grid as long as possible and big
 * ones still land home without a snap. Smoothstepped, so both ends of the
 * ride have zero slope and no kink is audible.
 */
fun tempoGlideFactor(progress: Float, stretch: Double): Double {
    val portion = (abs(stretch - 1.0) * 5.0).coerceIn(0.25, 0.75).toFloat()
    val start = 1f - portion
    val t = ((progress - start) / portion).coerceIn(0f, 1f)
    val s = t * t * (3f - 2f * t)
    return 1.0 - s
}

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
 * It fires as the incoming track's first note sounds, not at the end of the
 * blend, which keeps the behaviour the old design was built around: the queue
 * index, the metadata, the notification and the UI all flip to the incoming song
 * the moment it becomes audible, rather than trailing the song on its way out.
 * From that instant [outgoing] is the idle player, still audible, being faded
 * out — which is exactly what the previous design used its tail player for, at
 * none of the cost.
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
     * Called once per transition, at the instant the incoming track becomes
     * audible. After it returns, [active] must answer `incoming` and [standby]
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
    /**
     * Queues background analysis for a media item. The Boolean marks the
     * track queued to play next, which the analyzer's priority lane serves
     * ahead of everything else — the incoming side of the next transition is
     * the one result whose lateness is audible.
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
     * The tempo-synced echo send riding each side of an echo-out. Mirrors
     * [filters]: what makes a plan's echo audible rather than advisory.
     * Defaults to [EchoFilters.None], which renders echo plans as the P0
     * filter wash alone.
     */
    private val echoFilters: EchoFilters = EchoFilters.None,
    /**
     * The Schroeder reverb send riding each side of a dissolve or heavy
     * clash. Mirrors [echoFilters]. Defaults to [ReverbFilters.None], which
     * renders those plans as dry linear fades.
     */
    private val reverbFilters: ReverbFilters = ReverbFilters.None,
    /**
     * The loop vamp riding the outgoing deck of a LOOP_CUT_DROP. Mirrors
     * [echoFilters]: a real quantized PCM loop with booth halving, driven
     * per fade tick. Defaults to [LoopVamps.None], which renders the vamp
     * as the legacy held tail.
     */
    private val loopVamps: LoopVamps = LoopVamps.None,
    /**
     * The splice-guard trigger both decks fire at an INSTANT flip. The
     * fade-in side needs no call — every chain flush self-arms it — but a
     * mid-stream cut has no flush, so the controller fires it explicitly.
     * Defaults to [SpliceGuards.None].
     */
    private val spliceGuards: SpliceGuards = SpliceGuards.None,
    /**
     * The 3-band DJ EQ riding each side of a blend. Mirrors [filters]: the EQ
     * schedule for the active transition type re-aims both decks once per
     * fade tick. Defaults to [EqFilters.None], which renders every plan as
     * the pre-EQ volume-plus-sweep blend.
     */
    private val eqFilters: EqFilters = EqFilters.None,
    /**
     * The per-deck loudness gain stage. Aimed once per arm in [begin] from
     * each deck's analyzed LUFS — not per tick, loudness doesn't move during
     * a blend. Defaults to [LoudnessGains.None] (unity, correction off).
     */
    private val loudnessGains: LoudnessGains = LoudnessGains.None,
    /**
     * The brake/dive effect on the outgoing deck. Defaults to
     * [BrakeDiveFilters.None] which renders no speed change.
     */
    private val brakeDiveFilters: BrakeDiveFilters = BrakeDiveFilters.None,
    /**
     * Whether a decode and inference for a media item is running right now.
     * Only feeds the stats line — nothing about a transition waits on it.
     */
    private val analysisRunningFor: (MediaItem) -> Boolean = { false },
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
        val vocalOverlap: Double = 0.0,
        val volumeCurve: VolumeCurve = VolumeCurve.S_CURVE,
        /** Blueprint ECHO_REVERB_OUT: peak echo/reverb wet 0..1 on the outgoing track. */
        val echoAmount: Double = 0.0,
        /** One bar of the outgoing grid in seconds: the echo repeat period. 0 parks the line. */
        val echoBeatSeconds: Double = 0.0,
        /** Blueprint §5.2: semitone shift of the incoming track (±2). Rendered as pitch, not speed. */
        val keyShiftSemitones: Int = 0,
        /** Blueprint LOOP_CUT_DROP: bars of outgoing tail looped before the freeze-and-cut. */
        val loopBars: Int = 0,
        /**
         * Blueprint LOOP_CUT_DROP: one outgoing beat in seconds, sizing the
         * vamp loop (loopBeats × beatSeconds at the processor's rate). Set at
         * ARM from the outgoing grid; 0 beat = no grid, no vamp.
         */
        val loopBeatSeconds: Double = 0.0,
        /**
         * Vamp window in beats (fadeSeconds / loopBeatSeconds at ARM). The
         * halve schedule keys off remaining beats, not fixed fractions —
         * an 8 s window at 128 BPM holds ~17 beats, not 24, and fractions
         * authored for 24 beats never let a loop complete a repeat.
         */
        val loopWindowBeats: Double = 0.0,
        /** v2 §5a: harmonic tempo ratio locking the pair (1.0 = unison). */
        val matchedRatio: Double = 1.0,
        /** v2 §7d: outgoing deck speed for HALF_TIME (1.0 otherwise). */
        val outgoingPlaybackRate: Double = 1.0,
        /** v2 §9: peak reverb wet on the outgoing track (0 = dry; T6 voices it). */
        val reverbAmount: Double = 0.0,
        /** v2 §9b: transition-relative second to freeze the reverb tail, null = no freeze. */
        val reverbFreezeAtSec: Double? = null,
        /** v2 §9b: seconds after transitionStart before the incoming track starts. */
        val incomingStartDelaySec: Double = 0.0,
        /** v2 §9b: seconds after transitionStart the outgoing track holds full level. */
        val outgoingHoldSec: Double = 0.0,
        /** v2 §7d: downbeat emphasis offsets in transition-elapsed seconds (adjusted grid). */
        val halfTimeEmphasis: List<Double> = emptyList(),
        /** v2 §7d: shared BPM of a HALF_TIME blend (0 = not half-time). */
        val sharedBpm: Double = 0.0,
        /** v2 §7a: key sub-score gating the virtual mid-kill. */
        val keyScore: Double = 1.0,
        /** v2 §7a: overlap length in seconds, gating the mid-kill. */
        val overlapSeconds: Double = 0.0,
        /**
         * DJ-EQ spec: which schedule table this fade rides. The standard
         * (non-Smart) path leaves the default; only [considerSmartTransition]
         * voices a real type, because only it snapshots the grids the bass
         * swap needs.
         */
        val eqType: TransitionType = TransitionType.SMOOTH_CROSSFADE,
        /**
         * P2-smart: the conductor's recipe for this pair (see MixConductor).
         * Decided once at ARM time from the model's evidence; the render
         * actors perform it without re-deciding. Default is the neutral bed.
         */
        val mixRecipe: MixRecipe = MixRecipe.INSTRUMENTAL_BED,
        /**
         * DJ echo throw (F1): the outgoing tail earned a beat-synced vocal
         * echo (see TransitionPlan.echoThrow). rideFilters voices it through
         * the echo send; finish() closes the send and retires the deck late
         * so the tail rings past the handoff. DJ-only.
         */
        val echoThrow: Boolean = false,
        /**
         * DJ brake (F2): dive the outgoing deck rate toward a stop over the
         * last quarter of the blend. driveFade voices it per tick. DJ-only.
         */
        val brake: Boolean = false,
        /**
         * Booth backspin (DJ-only): the same dive DSP as [brake], compressed
         * to the last [BACKSPIN_WINDOW_MS] with a steeper curve — a spin-back
         * into a hard cut, not a long slowdown. Implied by the plan alongside
         * brake + echoThrow; the cooldown strips all three together.
         */
        val backspin: Boolean = false,
        /** DJ-EQ spec: false = leave both decks at unity (standard fades). */
        val eqEnabled: Boolean = false,
        /**
         * Automix-restore: true when this render was armed by DJ Mode. Normal
         * Automix renders stock (flat gains, no mute disposal, no vocal
         * filter rides); all DJ voicing below keys off this flag.
         */
        val mixset: Boolean = false,
        /** DJ-EQ spec: A sings in the transition zone — duck its mids. */
        val duckAMids: Boolean = false,
        /** DJ-EQ spec: B enters singing — delay its mids. */
        val delayBMids: Boolean = false,
        /**
         * Full-audit P1 M3: the vocal choke's EQ compensation — voice the
         * duck key set even when the ARM flags read clean. ORed into the
         * flags in rideEq.
         */
        val forceDuckKeys: Boolean = false,
        /**
         * DJ-EQ spec: blend progress at which the bass swap fires, pre-snapped
         * to the next downbeat at ARM time. +Inf = no downbeat swap (the LOW
         * band comes from the schedule tables instead).
         */
        val eqSwapFireProgress: Float = Float.POSITIVE_INFINITY,
        /** DJ-EQ spec: one outgoing beat in seconds (60/bpm).
         * The swap runs N bars where N = [eqSwapBars]; total beats = N × 4.
         * Real-DJ long blend: 1.0 bar hard swap when both sides are
         * energetic at the swap phrase, 2.0 bars gradual ride when sparse.
         */
        val eqSwapBeatSec: Double = 0.0,
        val eqSwapBars: Double = EqSchedule.SWAP_BARS,
        /**
         * Full-audit P2 S1: live vocal recompute. ARM-time snapshots of the
         * vocal masks + energy times (empty = ungated pair, no evidence).
         * rideEq slides a 2 s window over them as the blend travels, so a
         * vocal entering mid-blend still gets ducked/delayed instead of
         * stacking — the ARM flags only knew the planned zone.
         */
        val outgoingVocalTimes: DoubleArray = doubleArrayOf(),
        val outgoingVocalMask: DoubleArray = doubleArrayOf(),
        val incomingVocalTimes: DoubleArray = doubleArrayOf(),
        val incomingVocalMask: DoubleArray = doubleArrayOf(),
    )

    private var fadeStartedAt = 0L
    // DJ echo throw (F1): a deck retired late so its echo tail rings past
    // the handoff. Armed in finish(), fired in tick(), flushed in begin().
    private var pendingRetirePlayer: ExoPlayer? = null
    private var pendingRetireAtMs: Long = 0L
    // dj echo throw (F1): stepped wet close so the send glides to 0 over
    // THROW_CLOSE_MS instead of snapping one-tick. Armed in finish(),
    // stepped in tick(), flushed in begin().
    private var pendingEchoCloseFromWet = 0f
    private var pendingEchoCloseDelaySec = 0f
    private var pendingEchoCloseStartMs = -1L
    // Full-plan P5: same late-retire for reverb tails — the Schroeder tail
    // (~2.5 s) never rings past the handoff otherwise. Mirrors the echo
    // throw path above (armed in finish(), stepped in tick(), flushed in
    // begin()); reverb close needs no delay param (wet only).
    private var pendingReverbCloseFromWet = 0f
    private var pendingReverbCloseStartMs = -1L
    // DJ brake (F2): last committed brake rate, for coalesced per-tick ramps.
    private var lastBrakeRate: Float = 1f
    // Booth backspin: the spin-back dive occupies the last second of the
    // outgoing deck, whatever the blend span. Shorter than a brake by
    // construction — a backspin plan is a ~1 s pre-roll, never a long bed.
    private val BACKSPIN_WINDOW_MS = 1000L
    private val EFFECT_COOLDOWN_BLENDS = 2
    // F3 rotation: smart blends since the last effected one. The planner is
    // pure and cannot count, so this counter enforces the cooldown at Render
    // mapping (at most one effected blend per EFFECT_COOLDOWN_BLENDS).
    private var blendsSinceEffect: Int = EFFECT_COOLDOWN_BLENDS
    // v2 §7d/§11.2: downbeat-emphasis cursor into render.halfTimeEmphasis.
    // A pulse fires once per offset even across pause-parked ticks; the pulse
    // itself is applied after rideFilters so it wins for exactly one tick
    // (~30 ms, the 2-frame intent at 60 fps) before the ride reclaims the band.
    private var emphasisIndex = 0
    // Click audit P1: the downbeat accent holds across 3 ticks
    // (attack-hold-release) instead of a single tick — one 30 ms filter
    // re-aim is two sharp edges the DSP glide cannot fully absorb.
    private var emphasisTicksLeft = 0
    // Click audit P0: edge-trigger for the INSTANT flip tick. Rearmed in
    // begin() with every other per-transition flag.
    private var cutFired = false
    /**
     * P2-smart kill-once: once the mute block disposes the dry path, the
     * schedule has nothing left to voice — rideEq holds the kill instead of
     * re-aiming nonzero gains every tick (DSP churn with no audible effect).
     */
    private var dryKilled = false
    // Progress of the last driveFade tick: finish() uses it to decide whether
    // the completion guard may fire (mid-fade) or must stand down (done).
    private var lastProgress = 0f
    // DJ end-click fix: mute ramp state. The old code snapped out.volume 1->0
    // in one tick at the cutoff (full-scale chop under level-ride). Now the
    // first cutoff tick captures the live gain and ramps it over BAIL_MS via
    // fallGain (zero-slope landing), mirroring driveBail(). Rearmed in begin().
    private var muteRampStartMs = -1L
    private var muteFromGain = 1f
    // Full-audit P2: rate-commit coalescing state (see driveFade). Rearmed
    // in begin() with every other per-transition flag.
    private var lastCommittedRate: Float? = null
    private var lastRateCommitAt = 0L
    // Full-audit P2: bail() already restored the deck rate at partial gain
    // (masked by the 120 ms bail ramp); finish() must not commit a second
    // pipeline re-prepare at full volume for the same restore.
    private var deckRateReset = false
    // Missed-window fix F1: the service's quality upgrade cuts the session
    // source out from under an arm (replaceMediaItem + seekTo + prepare).
    // The service exempts its own bookkeeping via swappingMediaId, but this
    // listener never got the memo ΓÇö every swap read as "queue replaced" and
    // bailed the arm. The service calls [noteSwapCut] before each cut; while
    // the latch is fresh and the session still shows that id, discontinuities
    // are the swap landing, not a user action. Timestamped, not cleared on
    // use: a revert re-latches, and staleness expires on its own so a missed
    // clear can never mute genuine bails for more than the window below.
    private var swapCutMediaId: String? = null
    private var swapCutAtMs: Long = 0L
    // Missed-window fix F4: escalating re-arm damping. A bail storm (swap,
    // error, repeat cut) re-arms on the next 250 ms tick straight into the
    // next cut; each consecutive bail without an accepted arm in between
    // holds the planner longer, up to the ceiling. Reset on begin() (an arm
    // was accepted ΓÇö the storm is over) and on natural auto-advance (new
    // pair, new context).
    private var consecutiveBailCount = 0
    private var bailCooldownUntilMs = 0L
    // Full-audit P2: latched fade span (see driveFade). Rearmed in begin().
    private var spanLatched = 0L
    // Full-audit P2 S1: live vocal flags (see updateLiveVocalFlags).
    // Rearmed in begin() with every other per-transition flag.
    private var liveDuckA = 0f
    private var liveDelayB = 0f
    private var lastVocalSlewAt = 0L
    private var liveVocalLogged = false
    // DJ booth rule: the mid trade is committed per phrase and ridden
    // through — a DJ pulls the mid down on a surprise vocal but never yanks
    // it back up on a breath. Once a live flag engages past 0.5 it latches
    // for the rest of the fade, so gates can't chatter the EQ tables and
    // ownership up and down mid-blend. Rearmed in begin().
    private var liveDuckLatchedA = false
    private var liveDelayLatchedB = false

    /**
     * Mean mask activity over [start]..[end] track seconds, or null without
     * evidence. Same windowing as [vocalActivityBetween], but over the
     * ARM-time snapshot arrays carried on the render (the controller never
     * retains the analyses themselves).
     */
    private fun maskActivity(times: DoubleArray, mask: DoubleArray, start: Double, end: Double): Double? {
        if (times.size != mask.size || times.isEmpty() || end <= start) return null
        var sum = 0.0
        var count = 0
        for (i in times.indices) {
            val t = times[i]
            if (!t.isFinite() || t < start || t > end) continue
            val v = mask[i]
            if (!v.isFinite()) continue
            sum += v
            count++
        }
        return if (count > 0) sum / count else null
    }

    /**
     * Slides a 2 s vocal window over both decks as the blend travels and
     * slews the live flags toward what it sees (~500 ms time constant, so a
     * single hot frame cannot flap the mids). Silence when ungated (empty
     * snapshot = no evidence, never a block) or when the overlap is unknown.
     */
    private fun updateLiveVocalFlags(outProgress: Float, inProgress: Float) {
        val overlap = render.overlapSeconds
        if (overlap <= 0.0 || fadeEndMs <= 0L) return
        val now = SystemClock.elapsedRealtime()
        val dt = ((now - lastVocalSlewAt).coerceAtLeast(0L) / 500.0).toFloat().coerceAtMost(1f)
        lastVocalSlewAt = now
        if (dt <= 0f) return
        // A deck's blend portion ends at the fade end; B deck's starts at cue.
        val aNow = fadeEndMs / 1000.0 - (1.0 - outProgress) * overlap
        val bNow = incomingCueTimeMs / 1000.0 + inProgress * overlap
        val aTarget =
            if ((maskActivity(render.outgoingVocalTimes, render.outgoingVocalMask, aNow - 2.0, aNow)
                    ?: 0.0) >= VOCAL_ACTIVE_THRESHOLD
            ) 1f else 0f
        val bTarget =
            if ((maskActivity(render.incomingVocalTimes, render.incomingVocalMask, bNow - 2.0, bNow)
                    ?: 0.0) >= VOCAL_ACTIVE_THRESHOLD
            ) 1f else 0f
        liveDuckA += (aTarget - liveDuckA) * dt
        liveDelayB += (bTarget - liveDelayB) * dt
        // Engage-latch: crossing 0.5 upward sticks for this fade. A vocal
        // edge hovering near the detector threshold would otherwise flap
        // the gates every few seconds, re-voicing the EQ tables and the
        // ownership quadratic each time (audible pumping).
        if (liveDuckA > 0.5f) liveDuckLatchedA = true
        if (liveDelayB > 0.5f) liveDelayLatchedB = true
        // Full-audit P2 C2: leave a trace when the live path engages beyond
        // the ARM flags — once per fade, like the span-cap log.
        if (!liveVocalLogged && (liveDuckA > 0.5f || liveDelayB > 0.5f)) {
            liveVocalLogged = true
            TrackLog.d(TAG, "live vocal engaged mid-blend (duckA=$liveDuckA delayB=$liveDelayB)")
        }
    }
    // Blend-feel audit: the incoming-track cap below truncates the planned
    // span silently. Logged once per fade (rearmed in begin()) so a shortened
    // blend leaves a planned-vs-actual trace.
    private var spanCapLogged = false
    // DJ-EQ spec: bass-swap state machine. Armed at the schedule's progress,
    // fired once, then the LOW handover ramps over 2 bars. Rearmed in begin().
    private var eqSwapFired = false
    private var eqSwapStartProgress = 0f
    // Blueprint LOOP_CUT_DROP: last vamp loop length voiced, in beats.
    // Re-aimed only on phase change; rearmed in begin().
    private var lastLoopBeats = -1f
    private var bailStartedAt = 0L
    private var armDeadline = 0L

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

    /**
     * Gain the incoming track was at when the fade was interrupted, so its
     * ramp up starts from where it actually is rather than snapping to full.
     */
    private var bailFromGainIn = 0f

    /** Dedupes the per-tick plan log down to one line per distinct verdict. */
    private var lastPlanVerdict = ""

    /**
     * The last published marker and the pair it was planned for. A tick whose
     * plan dips transiently unmarkable (outgoing back to REFINING while its
     * whole-track pass re-runs, one blocked flicker) must not blank a window
     * that was correct a quarter-second ago — the latch below keeps it until
     * the pair itself changes or the plan says the pair is structurally
     * unmixable. Cleared wherever the window is cleared for real.
     */
    private var lastMarkedPair: String? = null
    private var lastMarkedWindow: TransitionWindow? = null
    /** G1: fingerprint of the plan that produced the latched window. */
    private var lastMarkedFingerprint: String? = null
    /** G3: frozen anchor for the final approach — the pair it belongs to. */
    private var frozenAnchorPair: String? = null
    private var frozenAnchorStartSec = 0.0
    private var frozenAnchorEndSec = 0.0
    /** P2: throttle for silent-guard diagnostics (see [logGuardOnce]). */
    private var lastGuardLog: String? = null

    /**
     * A playhead jump bigger than this between two ticks is a seek, not
     * playback: the latched marker was planned under assumptions (notably
     * the missed-anchor salvage) the new position invalidates.
     */
    private val seekJumpThresholdMs = 2000L
    private var lastTickPositionMs = -1L
    /**
     * Anchor moves bigger than this update the latched marker; smaller ones
     * stay frozen so tick jitter does not slide the bar (see G1 below).
     */
    private val markerUpdateDriftMs = 2000.0
    /**
     * How far ahead of a valid anchor the anchor freezes for its pair.
     * Past this point only the entry cue may still move (see G3 below).
     */
    private val anchorFreezeAheadMs = 15_000L

    private fun clearMarkerLatch() {
        lastMarkedPair = null
        lastMarkedWindow = null
        lastMarkedFingerprint = null
        frozenAnchorPair = null
        lastTickPositionMs = -1L
    }

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

    /**
     * Called by the service immediately before a quality-swap cut
     * (replaceMediaItem + seekTo + prepare on the session player). See [swapCutMediaId].
     */
    fun noteSwapCut(mediaId: String) {
        swapCutMediaId = mediaId
        swapCutAtMs = SystemClock.elapsedRealtime()
    }

    private fun isSwapCut(): Boolean {
        // DJ-only latch: stock upstream bails the arm on every seek-cut, so
        // normal Automix must too (a quality-swap seek re-arms from the ticks).
        if (!AppSettings.mixsetModeEnabled.value) return false
        val id = swapCutMediaId ?: return false
        if (SystemClock.elapsedRealtime() - swapCutAtMs > 3000L) {
            swapCutMediaId = null
            return false
        }
        // The swap keeps the media id; a user skip landing here changes it,
        // so a genuine bail can never be swallowed by a stale latch.
        return active().currentMediaItem?.mediaId == id
    }

    private val listener = object : Player.Listener {
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            // The listener moving the playhead is something no half-finished
            // crossfade should survive. Nothing this class does registers here
            // any more: the handoff is a role swap, not a seek. Quality-swap
            // cuts land as SEEK on the same id ΓÇö exempt (F1), they are not
            // the listener acting.
            if (reason == Player.DISCONTINUITY_REASON_SEEK && !isSwapCut()) bail()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                // A new pair by definition: any bail storm belonged to the
                // pair that just ended (F4 reset).
                consecutiveBailCount = 0
                bailCooldownUntilMs = 0L
            }
            when (reason) {
                // Something replaced the queue out from under the fade — a new
                // album, a new search result — so the tail still playing is a
                // leftover of a session that no longer exists. Note that this
                // does *not* fire when AutoPlay appends to the end, since the
                // playing item doesn't change: extending the queue mid-fade is
                // harmless and shouldn't cost the listener the blend. A
                // quality-swap cut reports the same reason on the same id ΓÇö
                // exempt (F1), same as the SEEK above.
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> if (!isSwapCut()) bail()
                Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> bail()
                // Full-audit P3: a natural auto-advance reaching this listener
                // means ExoPlayer moved the queue on by itself — our blend
                // never fired (missed window, stuck ARMING across the
                // boundary). A blended handoff is a role swap, never a media
                // transition, so this cannot fire for a handoff we handled;
                // bail() is a no-op in IDLE, so the only effect is unsticking
                // the controller back to IDLE to plan the new pair.
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> bail()
            }
        }

        override fun onPlayerError(error: PlaybackException) = bail()
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
                        Phase.BAILING -> BAIL_STEP_MS
                    },
                )
            }
        }
    }

    fun release() {
        tickerJob?.cancel()
        tickerJob = null
        listeningTo?.removeListener(listener)
        listeningTo = null
        active().volume = 1f
        AppSettings.smartMixInProgress.value = false
        filters.open()
        loopVamps.open()
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
        // which flips at the handoff: before it the standby shadows the session,
        // after it the outgoing tail does.
        if (phase == Phase.FADING || phase == Phase.BAILING) {
            outgoing?.playWhenReady = incoming?.playWhenReady ?: true
        }
        // DJ echo throw (F1): fire the late retire armed in finish(), so the
        // echo tail gets ~2.5 s to ring past the handoff before the deck stops.
        if (pendingRetirePlayer != null && SystemClock.elapsedRealtime() >= pendingRetireAtMs) {
            pendingRetirePlayer?.let(::retire)
            pendingRetirePlayer = null
        }
        // dj echo throw (F1): stepped wet close armed in finish() — linear
        // to 0 over THROW_CLOSE_MS through the existing outgoing() target,
        // each step smoothed further by the processor's own per-block glide.
        if (pendingEchoCloseStartMs >= 0L) {
            val t = (SystemClock.elapsedRealtime() - pendingEchoCloseStartMs).toFloat() / THROW_CLOSE_MS
            if (t >= 1f) {
                echoFilters.outgoing(0f, pendingEchoCloseDelaySec)
                pendingEchoCloseStartMs = -1L
            } else {
                echoFilters.outgoing(pendingEchoCloseFromWet * (1f - t), pendingEchoCloseDelaySec)
            }
        }
        // Full-plan P5: stepped reverb close armed in finish() — same
        // 200 ms linear drain so the tail rings instead of chopping.
        if (pendingReverbCloseStartMs >= 0L) {
            val t = (SystemClock.elapsedRealtime() - pendingReverbCloseStartMs).toFloat() / THROW_CLOSE_MS
            if (t >= 1f) {
                reverbFilters.outgoing(0f, false)
                pendingReverbCloseStartMs = -1L
            } else {
                reverbFilters.outgoing(pendingReverbCloseFromWet * (1f - t), false)
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

        when (phase) {
            Phase.IDLE -> considerAutoTransition()
            Phase.ARMING -> driveArming()
            Phase.FADING -> driveFade()
            Phase.BAILING -> driveBail()
        }
    }

    /** Arms a crossfade as the playing track runs out. */
    private fun considerAutoTransition() {
        val player = active()
        if (!player.isPlaying) return
        // DJ independent: party veto is DJ-only; stock must not suppress
        // a blend that origin would arm.
        val mixsetGateForAuto = AppSettings.mixsetModeEnabled.value
        // Not while listening together. A blend starts the next track early, by
        // a length this device decides for itself from its own copy of the
        // audio — so in a party every member would begin the next song at a
        // different moment, and each would then be dragged back by a correcting
        // seek. The transition a party shares is the plain one: whoever reaches
        // the end first publishes the change and everybody moves together. See
        // [PartySync].
        if (mixsetGateForAuto && ListenTogether.state.value.inParty) return
        // Bail-cooldown throttle is DJ-only: origin re-armed immediately after
        // a bail; stock must do the same (LEAK #11).
        if (mixsetGateForAuto && SystemClock.elapsedRealtime() < bailCooldownUntilMs) return
        // Nothing to transition *into*, so any analysis state left over from the
        // previous pair is stale — the last track of a queue should not still be
        // claiming both songs are measured.
        if (!player.hasNextMediaItem()) {
            logGuardOnce("auto", "no transition: queue ends here")
            AppSettings.smartTransitionWindow.value = null
            if (mixsetGateForAuto) clearMarkerLatch()
            return
        }

        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0L) {
            logGuardOnce("auto", "no transition: duration unset ($duration)")
            return
        }

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
            logGuardOnce("auto", "no transition: repeat-one loop")
            if (AppSettings.smartFadeEnabled.value || AppSettings.mixsetModeEnabled.value) requestAnalysisAround(player, duration)
            // Stale otherwise: the marker would keep describing the transition
            // planned for this pair before the loop went on, at a point the
            // playhead now runs past on every lap without anything happening.
            AppSettings.smartTransitionWindow.value = null
            if (mixsetGateForAuto) clearMarkerLatch()
            return
        }

        // Automix is its own on/off, independent of the manual crossfade
        // length: it decides its own duration from each pair of tracks (beats,
        // tempo, structure), so requiring a nonzero [AppSettings.crossfadeSeconds]
        // first would tie an automatic feature to a manual one it doesn't use.
        // DJ independent: Automix and DJ Mode each own their scheduling;
        // either can arm a transition without the other.
        val djOnForTick = AppSettings.mixsetModeEnabled.value
        if (AppSettings.smartFadeEnabled.value || djOnForTick) {
            considerSmartTransition(duration)
            // Full-audit F2R: hot-path refine — DJ-only so stock matches
            // origin ee8a348 verbatim (origin never re-issued a request on
            // provisional heads).
            if (djOnForTick) {
                val curItem = player.currentMediaItem
                if (curItem != null) {
                    val curAnalysis = analysisFor(curItem)
                    if (curAnalysis.provisionalHead && curAnalysis.isUsable) {
                        requestAnalysisAround(player, duration)
                    }
                }
            }
            return
        }

        if (configuredFadeMs() <= 0L) {
            logGuardOnce("auto", "no transition: manual crossfade length is 0")
            return
        }
        val fade = fadeFor(duration)
        if (fade <= 0L) {
            logGuardOnce("auto", "no transition: fadeFor returned 0 for duration=$duration")
            return
        }

        val remaining = duration - player.currentPosition
        // Arm early: the standby has to open the incoming track and buffer to
        // its cue point, and that work has to be finished by the time the fade
        // is due rather than started then. Stock Automix keeps origin 4000ms;
        // DJ Mode keeps adaptive 6000ms when incoming not yet resolved.
        // DJ independent: lead is DJ-only.
        val leadMs = if (AppSettings.mixsetModeEnabled.value) ARM_LEAD_MS else ARM_LEAD_RESOLVED_MS
        if (remaining > fade + leadMs) return

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
        // DJ independent: video veto is DJ-only; stock must plan the
        // transition exactly as origin did.
        val mixsetEarly = AppSettings.mixsetModeEnabled.value
        if (mixsetEarly && (currentItem.isVideoOrigin || nextItem.isVideoOrigin)) {
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

        val mixset = AppSettings.mixsetModeEnabled.value
        var plan = planTransition(
            analysis = currentAnalysis,
            nextAnalysis = nextAnalysis,
            currentTrack = currentItem.toTransitionInfo(duration),
            nextTrack = nextItem.toTransitionInfo(nextDuration),
            currentTime = player.currentPosition / 1000.0,
            duration = duration / 1000.0,
            fadeSeconds = fallbackSeconds,
            mode = CrossfadeMode.SMART,
            mixset = mixset,
        )
        // One line per distinct verdict rather than one per 250ms tick, so the
        // log says what the planner decided for this pair without burying it.
        val verdict = "${plan.reason}|${plan.transitionStyle}|fade=${plan.fadeMs}" +
            "|cue=${plan.incomingCueTime}|rate=${plan.incomingPlaybackRate}" +
            "|vocalOverlap=${"%.2f".format(Locale.ROOT, plan.vocalOverlap)}" +
            "|phase=${"%.3f".format(Locale.ROOT, plan.phaseOffsetSec)}" +
            "|blocked=${plan.blocked}|policy=${plan.policyReasons.joinToString(",")}"
        if (verdict != lastPlanVerdict) {
            lastPlanVerdict = verdict
            TrackLog.d(
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
        val pairKey = "${currentItem.mediaId}→${nextItem.mediaId}"
        // G3 anchor freeze: DJ-only — stock Automix keeps the live plan like
        // origin, otherwise a 6000ms lead + frozen anchor made the marker sit
        // while the plain fade armed late.
        if (mixset) {
            if (plan.blocked) {
                if (frozenAnchorPair == pairKey) frozenAnchorPair = null
            } else if (plan.fadeMs > 0L) {
                val remainingMs = (plan.transitionStart * 1000).roundToLong() - player.currentPosition
                if (frozenAnchorPair == pairKey) {
                    if ((frozenAnchorEndSec * 1000).roundToLong() <= player.currentPosition) {
                        frozenAnchorPair = null
                    } else {
                        plan = plan.copy(
                            transitionStart = frozenAnchorStartSec,
                            transitionEnd = frozenAnchorEndSec,
                        )
                    }
                } else if (remainingMs in 1..anchorFreezeAheadMs && currentAnalysis.isUsable) {
                    frozenAnchorPair = pairKey
                    frozenAnchorStartSec = plan.transitionStart
                    frozenAnchorEndSec = plan.transitionEnd
                }
            } else if (frozenAnchorPair == pairKey) {
                frozenAnchorPair = null
            }
        } else if (frozenAnchorPair == pairKey) {
            // Stock: no freeze — drop any DJ latch left from a mode switch.
            frozenAnchorPair = null
        }
        // G2 honest marker: stock Automix marks like origin (no realMix gate);
        // DJ Mode gates on realMix so fallback plain fades don't advertise a mix.
        val realMix = planIsRealMix(plan)
        val markable = !plan.blocked &&
            plan.markerVisible &&
            (realMix || !mixset) &&
            duration > 0L &&
            analysisState.current == TrackAnalysisState.ANALYSED &&
            analysisState.next in MEASURED_ENOUGH_TO_ENTER_ON
        val fingerprint = listOf(
            plan.transitionStart,
            plan.transitionEnd,
            plan.fadeMs,
            plan.transitionStyle,
            plan.incomingCueTime,
            plan.incomingPlaybackRate,
            mixset,
            analysisState.current,
            analysisState.next,
            currentAnalysis.downbeats.size,
            nextAnalysis.downbeats.size,
        ).joinToString("|")
        val window = if (markable) {
            TransitionWindow(
                start = (plan.transitionStart * 1000.0 / duration).toFloat().coerceIn(0f, 1f),
                end = (plan.transitionEnd * 1000.0 / duration).toFloat().coerceIn(0f, 1f),
            )
        } else {
            null
        }
        // Restore origin ee8a348 verbatim for stock Automix: the marker is
        // the live window, not a latched one. G1 versioned latch + seek-jump
        // handling are DJ-only (LEAK #4/5).
        if (mixset) {
            if (window != null) {
                // G1 versioned latch: the first markable plan still wins against
                // tick jitter, but a re-plan that actually moved the anchor (over
                // [markerUpdateDriftMs]) updates the bar instead of freezing a
                // lie. Style/entry-only changes keep the shown position: the bar
                // shows *where*, and where did not move.
                val driftMs = lastMarkedWindow?.let { (abs(window.start - it.start) * duration).toDouble() }
                if (pairKey != lastMarkedPair || lastMarkedWindow == null ||
                    (fingerprint != lastMarkedFingerprint && (driftMs == null || driftMs > markerUpdateDriftMs))
                ) {
                    lastMarkedPair = pairKey
                    lastMarkedWindow = window
                    lastMarkedFingerprint = fingerprint
                }
            } else if (plan.blocked || pairKey != lastMarkedPair ||
                (!realMix && analysisState.current == TrackAnalysisState.ANALYSED &&
                    analysisState.next in MEASURED_ENOUGH_TO_ENTER_ON)
            ) {
                // Structural, a new pair, or a settled fallback verdict on fully
                // measured tracks: nothing audible to promise, so the latch goes
                // with it. Unmeasured tracks keep a latched window through the
                // dip — evidence is still landing, and the verdict is not final.
                // Backing into an old pair re-latches from scratch the first
                // markable tick.
                clearMarkerLatch()
            }
            // Otherwise a transient dip on the same pair — the latched window
            // outlives it, which is what makes the marker appear once both tracks
            // are measured and then stay put until the mix.
            val nowMs = player.currentPosition
            if (lastTickPositionMs >= 0 && abs(nowMs - lastTickPositionMs) > seekJumpThresholdMs) {
                clearMarkerLatch()
                if (window != null) {
                    lastMarkedPair = pairKey
                    lastMarkedWindow = window
                }
            }
            lastTickPositionMs = nowMs
            AppSettings.smartTransitionWindow.value =
                lastMarkedWindow?.takeIf { pairKey == lastMarkedPair } ?: window
        } else {
            // Stock Automix: origin ee8a348 had no latch — clear any DJ latch
            // left from a mode switch and publish the live window.
            if (lastMarkedPair != null || lastMarkedWindow != null) {
                lastMarkedPair = null
                lastMarkedWindow = null
                lastMarkedFingerprint = null
                lastTickPositionMs = -1L
                frozenAnchorPair = null
            }
            AppSettings.smartTransitionWindow.value = window
        }

        if (plan.blocked) return

        // Full-audit P3: passed-mix rescue. A live plan whose window slid past
        // the playhead (evidence refined the anchor forward after the marker
        // froze, or ARM never fired before the slide) would otherwise miss
        // forever — every tick re-plans from a post-window playhead and
        // returns without arming. Find a new out-point with a silence-seeking
        // dissolve ahead of the playhead instead of hanging until ExoPlayer
        // advances on its own. (The armed case is already covered by the
        // driveArming overshoot path; this covers the never-armed case.)
        // DJ-only: stock upstream has no rescue — a passed window simply
        // waits for the natural advance below.
        val posSec = player.currentPosition / 1000.0
        val lenSec = duration / 1000.0
        if (mixset && plan.fadeMs > 0 && posSec >= plan.transitionEnd - 0.5 && lenSec - posSec > 6.0) {
            logGuardOnce("smart", "passed mix window (end=${plan.transitionEnd}), rescuing dissolve")
            clearMarkerLatch()
            // Choose a NEW mixzone ahead: re-scan from pos+2s so the dissolve
            // lands on the next silence/break/low-energy window instead of
            // truncating the same passed cut.
            val minScan = posSec + 2.0
            val rescue = plainDissolvePlan(
                currentAnalysis,
                nextAnalysis,
                lenSec,
                nextDuration.takeIf { it > 0L }?.div(1000.0) ?: 0.0,
                posSec,
                mixset,
                plan.policyReasons + "passed-mix-rescue",
                scanFromOverride = minScan,
            )
            if (rescue.transitionStart >= minScan - 0.01 &&
                rescue.transitionEnd - rescue.transitionStart >= MIN_GUARANTEED_BLEND_SECONDS
            ) {
                plan = rescue.copy(
                    transitionStart = rescue.transitionStart,
                    fadeSeconds = rescue.transitionEnd - rescue.transitionStart,
                    handoffStartSeconds = rescue.transitionStart,
                    handoffDuration = rescue.transitionEnd - rescue.transitionStart,
                    overlapSeconds = rescue.transitionEnd - rescue.transitionStart,
                    shouldStart = false,
                    reason = "passed-mix-rescue-forward",
                )
                // Publish the NEW zone immediately: the marker above was built
                // from the passed plan, and without this the bar keeps showing
                // the old zone until the next tick re-rescues.
                if (duration > 0L) {
                    val fwdWindow = TransitionWindow(
                        start = (plan.transitionStart * 1000.0 / duration).toFloat().coerceIn(0f, 1f),
                        end = (plan.transitionEnd * 1000.0 / duration).toFloat().coerceIn(0f, 1f),
                    )
                    lastMarkedPair = pairKey
                    lastMarkedWindow = fwdWindow
                    AppSettings.smartTransitionWindow.value = fwdWindow
                }
            } else {
                // No room for a real blend: clear the promised window and
                // await the natural advance instead of displaying a passed mix.
                AppSettings.smartTransitionWindow.value = null
                return
            }
        }

        val fade = plan.fadeMs
        if (fade <= 0L && !mixset) return
        if (fade <= 0L) {
            // DJ-only rebuild: stock upstream returns above and lets the
            // track end plainly instead of dissolving at its end.
            // The planner floor above guarantees a non-blocked plan carries a
            // real blend; if one still arrives with no fade, rebuild it as a
            // dissolve here instead of letting the track hard-cut at its end.
            logGuardOnce("smart", "no fade in plan (anchor=${plan.transitionStart}), rebuilding dissolve")
            plan = plainDissolvePlan(
                currentAnalysis,
                nextAnalysis,
                duration / 1000.0,
                nextDuration.takeIf { it > 0L }?.div(1000.0) ?: 0.0,
                player.currentPosition / 1000.0,
                mixset,
                plan.policyReasons.ifEmpty { listOf("controller-dissolve-rebuild") },
            )
        }

        val transitionStartMs = (plan.transitionStart * 1000).roundToLong()
        val remaining = transitionStartMs - player.currentPosition
        // Same arm-ahead margin as the standard path, just measured against
        // the plan's own start rather than a fixed offset from track end —
        // an analyzed mix-out anchor can place that start well before the
        // file actually ends.
        //
        // Review v2.1 B5 adaptive lead: no per-track resolve-time metric
        // exists in the analysis pipeline, so usability is the proxy — an
        // incoming track not yet measured means its resolution is still in
        // flight (the slow case from the session log) and gets the full
        // lead; a measured one arms on the resolved margin.
        val armLeadMs = if (mixset && nextAnalysis?.isUsable != true) ARM_LEAD_MS else ARM_LEAD_RESOLVED_MS
        if (remaining > armLeadMs) return

        // DJ-EQ spec §Vocal EQ: evaluated ONCE here at ARM time, not during the
        // blend. Real-DJ long blend: zones follow the analyzer's vocal
        // phrases, not fixed fractions — A-duck ends at A's first vocal gap
        // (phrase end), B-delay keys off B's first vocal start. A long
        // instrumental intro on B reads as mashup (no delay); the live vocal
        // flags still engage mid-blend if a voice enters late. Null (no mask)
        // never blocks — absence of a mask is not absence of a vocal.
        val duckAMids = currentAnalysis?.let { a ->
            val zoneStart = plan.transitionStart
            val zoneEnd = zoneStart + plan.fadeSeconds * 0.70
            // Phrase end: A's duck zone ends where A goes quiet for a breath.
            val phraseEnd = firstQuietGapSec(a, zoneStart, zoneEnd) ?: zoneEnd
            vocalActivityBetween(a, zoneStart, phraseEnd)
        }?.let { it > 0.50 } ?: false
        val delayBMids = nextAnalysis?.let { b ->
            val entryBeats = if (b.beatInterval > 0) b.beatInterval * 16 else 8.0
            val cue = plan.incomingCueTime
            // Phrase start: B's first vocal from its cue. A vocal starting
            // past the entry window = instrumental intro → mashup, no delay.
            val firstSing = firstVocalStartSec(
                b, cue, cue + max(entryBeats, plan.fadeSeconds * 0.5),
            )
            if (firstSing == null || firstSing > cue + entryBeats) {
                false
            } else {
                vocalActivityBetween(b, cue, min(firstSing + 8.0, cue + plan.fadeSeconds))
                // Full-audit P0.4: the gate sits ON the vocal scale (0.6), not
                // below the analyzer's 0.5 neutral — an unmeasured window averages
                // exactly neutral and must read as "no evidence", not "delay".
                    ?.let { it >= VOCAL_ACTIVE_THRESHOLD } ?: false
            }
        } ?: false
        // DJ-EQ spec §Bass swap protocol: arm at the type's progress, fire on
        // the next downbeat after it. Pre-snapped here (grids are ARM-time
        // data); the fade only compares progress against it.
        val swapProgress = EqSchedule.BASS_SWAP_PROGRESS[plan.type]
        // DJ independent: no schedule snap when DJ off (+Inf), so the
        // legacy SVF bass swap below runs. DJ Mode keeps the downbeat snap.
        val eqSwapFireProgress = if (mixset && swapProgress != null && plan.fadeSeconds > 0) {
            // Real-DJ long blend: the bass swap fires on the phrase "1"
            // (16-bar grid), not an arbitrary downbeat — ears expect the
            // low-end handoff on a structural boundary.
            val beatSec = currentAnalysis?.beatInterval?.takeIf { it > 0 } ?: 0.0
            val ideal = plan.transitionStart + swapProgress * plan.fadeSeconds
            val snap = currentAnalysis?.let { a ->
                phrase16Grid(a).firstOrNull { it > ideal }
                    ?: a.downbeats.firstOrNull { it > ideal }
            } ?: if (beatSec > 0) ideal + beatSec else ideal
            ((snap - plan.transitionStart) / plan.fadeSeconds).toFloat().coerceIn(0f, 1f)
        } else {
            Float.POSITIVE_INFINITY
        }
        // Real-DJ long blend: hard 1-bar swap when both sides are energetic
        // at the swap phrase (the incoming kick can carry the room), gradual
        // 2-bar ride when sparse (keeps warmth, never an anti-climax).
        // Pure read of the analyzer energy curves — no model change.
        val eqSwapBars = if (mixset && eqSwapFireProgress.isFinite()) {
            val swapSec =
                plan.transitionStart + eqSwapFireProgress * plan.fadeSeconds
            val beatSec = currentAnalysis?.beatInterval?.takeIf { it > 0 }
                ?: 0.5
            fun hotAround(curve: List<EnergySample>): Boolean {
                val window = curve.filter {
                    it.time.isFinite() && it.energy.isFinite() &&
                        it.time in swapSec - beatSec * 8..swapSec + beatSec * 8
                }
                if (window.isEmpty()) return false
                val mean = curve.filter { it.energy.isFinite() }
                    .map { it.energy }.average().takeIf { it.isFinite() } ?: return false
                return window.map { it.energy }.average() >= mean
            }
            val outHot = currentAnalysis?.energyCurve?.let { hotAround(it) } ?: false
            val inCue = plan.incomingCueTime + swapSec - plan.transitionStart
            val inHot = nextAnalysis?.energyCurve?.let { curve ->
                val window = curve.filter {
                    it.time.isFinite() && it.energy.isFinite() &&
                        it.time in inCue - beatSec * 8..inCue + beatSec * 8
                }
                if (window.isEmpty()) false else {
                    val mean = curve.filter { it.energy.isFinite() }
                        .map { it.energy }.average()
                    window.map { it.energy }.average() >= mean
                }
            } ?: false
            if (outHot && inHot) 1.0 else 2.0
        } else {
            EqSchedule.SWAP_BARS
        }

        // P2-smart: the conductor reads the model's evidence once and picks
        // the pair's recipe — the render then performs it without voting.
        // dropConfidence is null for unmeasured fallback drops (never a cut).
        val mixRecipe = selectMixRecipe(
            type = plan.type,
            duckA = duckAMids,
            delayB = delayBMids,
            forceDuck = plan.forceDuckKeys,
            vocalOverlap = plan.vocalOverlap,
            dropConfidence = nextAnalysis?.dropConfidence,
        )
        // F3 rotation: at most one effected blend per cooldown window
        // (EFFECT_COOLDOWN_BLENDS). The planner is pure and cannot count
        // past blends, so the controller strips throw/brake here when the
        // last effect was too recent. Normal Automix never carries effects.
        val effectAllowed = !mixset || blendsSinceEffect >= EFFECT_COOLDOWN_BLENDS
        if (!begin(
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
                vocalOverlap = plan.vocalOverlap,
                volumeCurve = plan.volumeCurve,
                echoAmount = plan.echoAmount,
                echoThrow =
                    // Full-audit F5: throw and brake never share a blend (a
                    // braked deck under beat echo rings falling pitch) — brake
                    // wins. Backspin keeps its designed triple-stack.
                    plan.echoThrow && effectAllowed && (!plan.brake || plan.backspin),
                // v2 §7b: the dub throw repeats every HALF beat; a sync-less
                // PLAIN pair gets a fixed 375 ms slapback instead of a grid it
                // cannot hold.
                echoBeatSeconds = when {
                    plan.type == TransitionType.PLAIN_DISSOLVE -> 0.375
                    // Full-audit P1 M4: the plan states its period; the old
                    // outgoingBpm-only rule voiced every echo as a half-beat
                    // dub, including echoOut's one-bar repeats.
                    plan.echoPeriodBeats != null && plan.echoPeriodBeats > 0 && plan.outgoingBpm > 0 ->
                        plan.echoPeriodBeats * 60.0 / plan.outgoingBpm
                    plan.outgoingBpm > 0 -> 30.0 / plan.outgoingBpm
                    else -> 0.0
                },
                loopBars = plan.loopBars,
                // Blueprint LOOP_CUT_DROP: the vamp loop is sized from the
                // outgoing grid snapped at ARM, falling back to the analyzed
                // tempo when the interval is unset (a missing interval must
                // never silently disarm the vamp while the plan promises it);
                // 0 beat = no grid at all, no vamp.
                loopBeatSeconds = currentAnalysis?.beatInterval?.takeIf { it > 0 }
                    ?: currentAnalysis?.bpm?.orZero()?.takeIf { it > 0 }?.let { 60.0 / it }
                    // Full-audit F7: never silently disarm a promised vamp —
                    // the planner itself falls back to a 0.5 s beat (120 BPM)
                    // when the grid is missing (loopRollPlan), so the render
                    // honors the same convention; 0 stays "no grid, no vamp"
                    // only when the plan promised nothing.
                    ?: if (plan.loopBars > 0) 0.5 else 0.0,
                loopWindowBeats =
                    if (plan.fadeSeconds > 0) {
                        val beat = currentAnalysis?.beatInterval?.takeIf { it > 0 }
                            ?: currentAnalysis?.bpm?.orZero()?.takeIf { it > 0 }?.let { 60.0 / it }
                            ?: if (plan.loopBars > 0) 0.5 else 0.0
                        if (beat > 0) plan.fadeSeconds / beat else 0.0
                    } else {
                        0.0
                    },
                keyShiftSemitones = plan.keyShiftSemitones,
                matchedRatio = plan.matchedRatio,
                outgoingPlaybackRate = plan.outgoingPlaybackRate,
                reverbAmount =
                    // Full-audit F3b: the wash joins the effect rotation — at
                    // most one effected blend per cooldown window, like throw
                    // and brake. PLAIN_DISSOLVE keeps its deliberate ambience
                    // (it is a dissolve voice, not a rotating effect); the
                    // freeze flag follows the amount (voiced wet is zeroed).
                    if (plan.type == TransitionType.PLAIN_DISSOLVE || effectAllowed) {
                        plan.reverbAmount
                    } else {
                        0.0
                    },
                reverbFreezeAtSec = plan.reverbFreezeAtSec,
                incomingStartDelaySec = plan.incomingStartDelaySec,
                outgoingHoldSec = plan.outgoingHoldSec,
                halfTimeEmphasis = plan.halfTimeEmphasis,
                // Only a half-time blend plays a tempo neither track owns;
                // every other plan's outgoingBpm is just its folded grid.
                sharedBpm = if (plan.type == TransitionType.HALF_TIME_BLEND) plan.outgoingBpm else 0.0,
                keyScore = plan.score.key,
                overlapSeconds = plan.fadeSeconds,
                eqType = plan.type,
                // Stock upstream on normal Automix: flat unity, no EQ voicing.
                // DJ Mode keeps the schedule ride.
                eqEnabled = mixset,
                mixset = mixset,
                brake = plan.brake && effectAllowed,
                backspin = plan.backspin && effectAllowed,
                mixRecipe = mixRecipe,
                duckAMids = duckAMids,
                delayBMids = delayBMids,
                forceDuckKeys = plan.forceDuckKeys,
                eqSwapFireProgress = eqSwapFireProgress,
                eqSwapBeatSec = currentAnalysis?.beatInterval
                    // Full-audit F1: the 1.0 fallback avoids a zero swap window
                    // on gridless DJ blends; stock keeps the origin 0.0 (which
                    // also preserves the origin 90 ms float settle there).
                    ?: if (mixset) 1.0 else 0.0,
                eqSwapBars = eqSwapBars,
                // Full-audit P2 S1: snapshot the vocal evidence for the live
                // recompute in rideEq (ARM flags only knew the planned zone).
                outgoingVocalTimes = currentAnalysis?.energyCurve?.map { it.time }?.toDoubleArray()
                    ?: doubleArrayOf(),
                outgoingVocalMask = currentAnalysis?.vocalActivityMask?.toDoubleArray()
                    ?: doubleArrayOf(),
                incomingVocalTimes = nextAnalysis?.energyCurve?.map { it.time }?.toDoubleArray()
                    ?: doubleArrayOf(),
                incomingVocalMask = nextAnalysis?.vocalActivityMask?.toDoubleArray()
                    ?: doubleArrayOf(),
            ),
        )) {
            logGuardOnce("smart", "no arm: begin() refused (anchor passed or no next item)")
        }
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
     * Track-change prefetch hook, called from the service's
     * onTrackBecameCurrent — outside the tick gating, so a bail-cooldown
     * after a skip cannot suppress the first analysis request for the new
     * pair. Fires the next track's head fetch at track start rather than
     * whenever the ticks resume.
     */
    fun prefetchNextAnalysis() {
        val player = runCatching { active() }.getOrNull() ?: return
        requestAnalysisAround(player, player.duration)
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
    /**
     * Full-plan loudness: correction gain in dB for one deck, from its
     * analyzed integrated LUFS. Target minus integrated, clamped ±6 dB,
     * then peak-headroomed so peak + gain never exceeds −1 dBTP.
     * Unmeasured (−70) or toggled off reads unity — never stage on nothing.
     */
    private fun loudnessGainDbFor(item: MediaItem?): Float {
        if (item == null || !AppSettings.loudnessNormalizationEnabled.value) return 0f
        val analysis = analysisFor(item)
        val lufs = analysis.loudnessLufs
        if (!lufs.isFinite() || lufs <= -69.0) return 0f
        val target = AppSettings.loudnessTargetLufs.value.toDouble()
        var gain = (target - lufs).coerceIn(-6.0, 6.0)
        val peak = analysis.peakDbfs
        if (peak.isFinite() && peak + gain > -1.0) gain = -1.0 - peak
        return gain.toFloat()
    }

    private fun begin(
        fade: Long,
        endMs: Long,
        smart: Boolean,
        cueTimeMs: Long = 0L,
        playbackRate: Double = 1.0,
        renderStyle: Render = Render(),
    ): Boolean {
        val out = active()
        val into = standby()
        if (out === into) return false
        val nextIndex = out.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return false
        // Missed-window fix F2: refuse a stale window. Re-arming past the
        // fade end replays a dead pair (the device log's ~75 s replay of a
        // finished pair); the passed-mix rescue owns that case instead. The
        // caller logs the refusal. DJ-only: stock upstream arms regardless
        // and lets the overshoot path below sort it out.
        if (renderStyle.mixset && endMs <= out.currentPosition) {
            TrackLog.d(TAG, "no arm: anchor passed (end=$endMs at=${out.currentPosition}ms)")
            return false
        }

        // The frozen anchor did its job getting here; a later lap of the
        // same pair (repeat-all) must freeze fresh, not inherit this lap's.
        frozenAnchorPair = null

        fadeMs = fade
        fadeEndMs = endMs
        smartFadeActive = smart
        incomingCueTimeMs = cueTimeMs.coerceAtLeast(0L)
        incomingPlaybackRate = playbackRate
        render = renderStyle
        armDeadline = SystemClock.elapsedRealtime() + ARM_TIMEOUT_MS
        handedOff = false
        cutFired = false
        dryKilled = false
        muteRampStartMs = -1L
        muteFromGain = 1f
        lastProgress = 0f
        lastCommittedRate = null
        lastRateCommitAt = 0L
        deckRateReset = false
        lastBrakeRate = 1f
        // DJ echo throw (F1): a new arm takes the decks now — flush any tail
        // still waiting out its late retire, or the new standby would prepare
        // over a player that is still (silently) playing.
        pendingRetirePlayer?.let(::retire)
        pendingRetirePlayer = null
        // flush a stepped close still in flight too: park the send shut, or
        // its leftover wet would voice on the fresh decks.
        if (pendingEchoCloseStartMs >= 0L) {
            echoFilters.outgoing(0f, pendingEchoCloseDelaySec)
            pendingEchoCloseStartMs = -1L
        }
        // Full-plan P5: flush a stepped reverb close in flight too.
        if (pendingReverbCloseStartMs >= 0L) {
            reverbFilters.outgoing(0f, false)
            pendingReverbCloseStartMs = -1L
        }
        // Missed-window fix F4: an accepted arm ends the storm.
        consecutiveBailCount = 0
        bailCooldownUntilMs = 0L
        spanLatched = 0L
        liveDuckA = 0f
        liveDelayB = 0f
        lastVocalSlewAt = 0L
        liveVocalLogged = false
        liveDuckLatchedA = false
        liveDelayLatchedB = false
        spanCapLogged = false
        outgoing = out
        incoming = into

        val items = (0 until out.mediaItemCount).map { out.getMediaItemAt(it) }
        queuedItemCount = items.size

        TrackLog.d(
            TAG,
            "arm ${if (smart) "smart" else "standard"} fade=${fade}ms end=${endMs}ms " +
                "cue=${incomingCueTimeMs}ms rate=$incomingPlaybackRate at=${out.currentPosition}ms " +
                "style=${render.style} bassSwap=${render.bassSwap}@${render.bassSwapFraction} " +
                "sweep=${render.filterSweep}",
        )

        // Carried across so the incoming track inherits the listener's own
        // settings rather than whatever the standby was left on last time.
        into.skipSilenceEnabled = out.skipSilenceEnabled
        into.repeatMode = out.repeatMode
        into.shuffleModeEnabled = out.shuffleModeEnabled
        // Energy-dip fix (Issue 2): silence-skipping sits AFTER all blend
        // DSP and would eat B's quiet intro mid-fade, destroying the planned
        // fade-in. DJ fades run with it off on both decks; finish() restores
        // the listener's setting. Stock behavior untouched.
        if (renderStyle.mixset && smart) {
            out.skipSilenceEnabled = false
            into.skipSilenceEnabled = false
        }
        // Stacks on top of the listener's speed control rather than replacing
        // it, so a beatmatched transition and "play everything at 1.25x" don't
        // fight each other. Undone in [finish].
        // Blueprint §5.2: the key shift rides as pitch, independent of the
        // tempo stretch — Sonic (already in the chain) renders both at once,
        // and shifting pitch leaves the beat grid exactly where the stretch
        // put it. Stock upstream on normal Automix: speed only, no pitch.
        into.setPlaybackParameters(
            PlaybackParameters(
                (AppSettings.playbackSpeed.value * incomingPlaybackRate).toFloat(),
                if (render.mixset) 2.0.pow(render.keyShiftSemitones / 12.0).toFloat() else 1f,
            ),
        )
        into.volume = 0f
        // Restore origin ee8a348 verbatim for stock Automix: filter/EQ park
        // is DJ-only (LEAK #9 — origin begin() never touched processor
        // state, so stock must leave the sweep where the previous fade left
        // it). Click-audit and G2 resonance park are DJ-only too.
        if (renderStyle.mixset) {
            // Click audit P2: the standby's filter may hold mid-sweep targets from
            // the previous fade (instances swap roles at the handoff, never reset).
            // Aim it open while still silent — via the outgoing route, which is the
            // spare pre-handoff. Echo/reverb need nothing: their wet re-aims on the
            // first FADING tick, and a wet start on a zeroed delay line renders dry
            // silence building up, not a burst.
            filters.outgoing(TransitionFilterProcessor.OPEN_HZ, TransitionFilterProcessor.OFF_HZ)
            // Tempo/pitch-leak fix G2: park the sweep resonance while silent.
            // open() parks Q, but begin() only aims cutoffs ΓÇö a DJ_FILTER arm's
            // Q=1.8 would otherwise meet the next transition's first sweep
            // target mid-glide. Neutral Q is unity-adjacent, inaudible.
            filters.setResonance(TransitionFilterProcessor.NEUTRAL_Q)
            // DJ-EQ spec: park both decks at unity while still silent. Same
            // outgoing-route reasoning as the filter above — and the swap machine
            // rearms here with every other per-transition flag.
            eqFilters.outgoing(1f, 1f, 1f)
            eqFilters.incoming(1f, 1f, 1f)
        }
        // Full-plan loudness: aim the gain stages once per arm from each
        // deck's analyzed integrated LUFS. Not per tick — loudness doesn't
        // move during a blend; the processor glides to the new target.
        // DJ-only: stock upstream has no loudness stage, so normal Automix
        // parks both decks at unity (also unwinds a DJ blend's correction).
        if (renderStyle.mixset) {
            loudnessGains.outgoing(loudnessGainDbFor(out.currentMediaItem))
            items.getOrNull(nextIndex)?.let { loudnessGains.incoming(loudnessGainDbFor(it)) }
        } else {
            loudnessGains.outgoing(0f)
            loudnessGains.incoming(0f)
        }
        eqSwapFired = false
        eqSwapStartProgress = 0f
        lastLoopBeats = -1f
        // Blueprint LOOP_CUT_DROP: park the vamp silent — instances swap
        // roles at the handoff, never reset, so a stale loop would voice on
        // the next transition's first tick.
        loopVamps.open()
        into.setMediaItems(items, nextIndex, incomingCueTimeMs)
        // Buffers without sounding. Started for real in [startFade].
        into.playWhenReady = false
        into.prepare()

        phase = Phase.ARMING
        return true
    }

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

        // Wait for the track to actually reach the fade point. [fadeEndMs] is
        // the track's own duration in standard mode, or a Automix plan's
        // analyzed mix-out anchor when it ends before the file does.
        val atFadePoint = fadeEndMs <= 0L || fadeEndMs - out.currentPosition <= fadeMs
        if (!atFadePoint) {
            // Full-audit P2: pre-fade outgoing ramp. The shared-grid join
            // used to land as one rate step at startFade, mid-fade and
            // audible. Instead the outgoing deck eases onto the grid during
            // the last 4 s before the fade point — the DJ nudging the pitch
            // fader before touching the crossfader. P1.1: coalesced like the
            // in-fade glide — an uncoalesced commit every 40 ms ARM tick
            // re-prepares the audible pipeline ~25×/s with growing deltas,
            // heard as pre-mix chatter 1–2 s before the blend. Same 0.15% /
            // 150 ms gate as driveFade; startFade keeps its exact set as the
            // idempotent landing for the residual.
            // DJ-only: stock upstream never eases the outgoing deck before
            // the fade point.
            if (render.mixset && render.outgoingPlaybackRate != 1.0 && fadeEndMs > 0L && fadeMs > 0L) {
                val leadMs = minOf(4000L, fadeMs).coerceAtLeast(1L)
                val leadStart = fadeEndMs - fadeMs - leadMs
                val ramp = ((out.currentPosition - leadStart).toFloat() / leadMs).coerceIn(0f, 1f)
                if (ramp > 0f) {
                    val eased = ramp * ramp * (3f - 2f * ramp) // smoothstep
                    // Full-plan P2: ease the FULL delta over the lead instead
                    // of capping at ±2% — the cap left HALF_TIME stepping
                    // 2%->41% in one tick at startFade. The smoothstep keeps
                    // the slope inaudible; coalescing below bounds the commits.
                    val fullDelta = ((render.outgoingPlaybackRate - 1.0) * eased).toFloat()
                    val rate = (AppSettings.playbackSpeed.value *
                        (1.0 + fullDelta)).toFloat()
                    val now = SystemClock.uptimeMillis()
                    val last = lastCommittedRate
                    if (last == null || abs(rate - last) / max(abs(last), 1e-6f) >= 0.0015f ||
                        now - lastRateCommitAt >= 150L
                    ) {
                        out.setPlaybackParameters(PlaybackParameters(rate, 1f))
                        lastCommittedRate = rate
                        lastRateCommitAt = now
                    }
                }
            }
            return
        }
        if (!ready) return
        // Late-start guard: the whole fade window elapsed while the standby
        // buffered, so starting the blend now would run every ride at
        // progress ~1 on its first ticks — the effect compressed into a
        // second and finish() snapping B to full volume. An honest immediate
        // handoff instead of a fake blend.
        val overshootMs = out.currentPosition - (fadeEndMs - fadeMs)
        // DJ-only: stock upstream always startFade()s here, even overshot.
        if (render.mixset && fadeEndMs > 0L && fadeMs > 0L && overshootMs >= fadeMs) {
            startLateHandoff()
        } else {
            startFade()
        }
    }

    /**
     * The fade window fully passed before the standby was ready: B enters at
     * full volume from its cue and A retires now, with the same bookkeeping
     * as [startFade] (queue, session, marker) but no zero-volume charade on
     * the way in. [finish] does the retiring — handedOff is set, so it keeps
     * the incoming player at full and stops the outgoing one.
     */
    private fun startLateHandoff() {
        val out = outgoing ?: return bail()
        val into = incoming ?: return bail()
        reconcileQueue(out, into)
        TrackLog.d(TAG, "late handoff at cue=${into.currentPosition}ms out=${out.currentPosition}ms end=${fadeEndMs}ms")
        // The incoming deck jumps 0→1 mid-waveform and the outgoing deck is
        // retired at full gain: both edges land inside one guard window.
        // lastProgress marks the rescue so finish()'s own guard agrees.
        spliceGuards.cut()
        lastProgress = 0f
        // P1.2: undo begin()'s stacked rate while the deck is still silent.
        // finish() would do it at full volume (lastProgress=0 → glide 1.0 →
        // guard passes) — the end-of-mix snap on the rescue path. deckRateReset
        // tells finish() the deck is already home (rearmed in begin()).
        if (incomingPlaybackRate != 1.0 || render.keyShiftSemitones != 0) {
            into.setPlaybackParameters(PlaybackParameters(AppSettings.playbackSpeed.value, 1f))
            deckRateReset = true
        }
        into.volume = 1f
        into.playWhenReady = true
        fadeStartedAt = SystemClock.elapsedRealtime()
        listenTo(into)
        handedOff = true
        onHandoff(out, into)
        if (out.mediaItemCount > out.currentMediaItemIndex + 1) {
            out.removeMediaItems(out.currentMediaItemIndex + 1, out.mediaItemCount)
        }
        AppSettings.smartMixInProgress.value = false
        AppSettings.smartTransitionWindow.value = null
        clearMarkerLatch()
        phase = Phase.FADING
        finish()
    }

    /**
     * Starts the incoming track and moves the session onto it.
     *
     * The handoff happens *here*, as the first note sounds, not at the end of
     * the blend. Everything hanging off the session player — queue index,
     * metadata, the notification, the UI, audio focus — flips to the incoming
     * song the moment it becomes audible, rather than trailing the song on its
     * way out. From this point [outgoing] is the idle player, still audible,
     * being faded away.
     */
    private fun startFade() {
        val out = outgoing ?: return bail()
        val into = incoming ?: return bail()

        // AutoPlay may have appended to the queue since the standby was loaded
        // with a copy of it; those tracks would otherwise be lost at the swap.
        reconcileQueue(out, into)

        into.volume = 0f
        into.playWhenReady = true
        fadeStartedAt = SystemClock.elapsedRealtime()
        emphasisIndex = 0
        emphasisTicksLeft = 0
        // DJ-EQ spec: the swap machine rearms with the fade, like the emphasis
        // cursor above — a repeat-all lap must schedule fresh, not inherit.
        eqSwapFired = false
        eqSwapStartProgress = 0f

        // v2 §7d HALF_TIME: the outgoing deck joins the shared tempo it does
        // not own — the incoming side was already stretched at arm time
        // (begin, pre-audible). Unity means no call: ExoPlayer re-prepares
        // its audio pipeline on parameter changes.
        val outgoingRate = (AppSettings.playbackSpeed.value * render.outgoingPlaybackRate).toFloat()
        if (render.outgoingPlaybackRate != 1.0) {
            out.setPlaybackParameters(PlaybackParameters(outgoingRate, 1f))
        }
        // Restore origin ee8a348 verbatim for stock Automix: shared BPM is
        // DJ-only (LEAK #6 — origin had no half-time).
        if (render.mixset) {
            // v2 §7d: the nerd-stats line shows the grid that won, if any.
            AppSettings.sharedHalfTimeBpm.value = render.sharedBpm.takeIf { it > 0 }
        }

        TrackLog.d(TAG, "handoff at cue=${into.currentPosition}ms out=${out.currentPosition}ms")

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
        if (out.mediaItemCount > out.currentMediaItemIndex + 1) {
            out.removeMediaItems(out.currentMediaItemIndex + 1, out.mediaItemCount)
        }

        // Restore origin ee8a348 verbatim for stock Automix: smartMixInProgress
        // and marker latch are DJ-gated (LEAK #7/5). Origin set
        // smartMixInProgress from window existence, not isRealMix, and had
        // no latch to clear. Stock must do the same.
        if (render.mixset) {
            AppSettings.smartMixInProgress.value = isRealMix()
        } else {
            // Origin ee8a348: smartMixInProgress true while window != null
            // is handled at plan time; at handoff it stays true through the
            // fade and is cleared on finish. Keep whatever was set at plan
            // time (window != null) rather than gating on isRealMix.
            AppSettings.smartMixInProgress.value = isRealMix()
        }
        // The queue has just moved on, so the marker's fractions now refer to a
        // track the session player is no longer showing a position for.
        AppSettings.smartTransitionWindow.value = null
        if (render.mixset) clearMarkerLatch()
        phase = Phase.FADING
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
        TrackLog.d(TAG, "reconciled ${appended.size} appended item(s) onto the incoming player")
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
        // Restore origin ee8a348 verbatim for stock Automix: third of the
        // remainder, 1 ms floor, no latch. The halved cap, 2 s audibility
        // floor and span latch below are DJ-only (LEAK #8).
        val incomingCap = remainingIncoming?.let { if (render.mixset) it.div(2) else it.div(3).coerceAtLeast(1L) }
            ?: Long.MAX_VALUE
        // A short incoming track (or late cue) shortens the blend but never
        // vaporises it: below 2 s the ear hears a cut, not a mix. Completion
        // is still safe — `done` below also fires on the outgoing track
        // ending, so an over-long span cannot hang the handoff.
        // An INSTANT plan (hard cut, loop drop) flips on a tick, it never
        // blends: stretching it to the 2 s audibility floor manufactures
        // seconds of B-side silence ending in a flip — the "nothing, then the
        // next track at full volume" complaint. The floor stays for every
        // curve that actually travels it.
        val span = if (!render.mixset) {
            minOf(fadeMs, incomingCap).coerceAtLeast(1L)
        } else if (render.volumeCurve == VolumeCurve.INSTANT) {
            minOf(fadeMs, incomingCap).coerceAtLeast(1L)
        } else {
            minOf(fadeMs, incomingCap).coerceAtLeast(2000L)
        }
        // Full-audit P2: latch the span at fade start. The per-tick
        // incoming cap may only narrow it (a late-arriving duration
        // tightening a blend into a short track), never stretch it —
        // stretching mid-fade rescales every ride's progress denominator
        // and jumps gains, filters and the tempo glide audibly.
        // DJ-only: stock upstream re-reads the span every tick.
        if (render.mixset && spanLatched <= 0L) spanLatched = span
        val effSpan = if (render.mixset) minOf(spanLatched, span) else span
        if (!spanCapLogged && incomingCap < fadeMs && incomingCap != Long.MAX_VALUE) {
            spanCapLogged = true
            TrackLog.d(
                TAG,
                "span truncated: planned=${fadeMs}ms actual=${span}ms latched=${spanLatched}ms " +
                    "remainingIncoming=${remainingIncoming}ms",
            )
        }
        val elapsed = (player.currentPosition - incomingCueTimeMs).coerceAtLeast(0L)
        val progress = (elapsed.toFloat() / effSpan).coerceIn(0f, 1f)

        // Blueprint §4 volume curves. The equal-power pair holds every blend;
        // LOGARITHMIC drops the outgoing track fast while its echo tail covers
        // the hole; INSTANT holds both sides until the cut lands at progress 1.
        // v2 §9a LINEAR is the sync-less dissolve: over a silence gap an
        // equal-power pair sums to a bump in the middle, a straight line doesn't.
        // v2 §9b: a held outgoing track (outgoingHoldSec) stays full until its
        // hold elapses, then fades over the remaining span — the echo/reverb
        // tail is the ending, not the content's last seconds.
        val outProgress = if (render.outgoingHoldSec > 0 && effSpan > 1L) {
            val holdFraction = (render.outgoingHoldSec * 1000.0 / effSpan).toFloat().coerceIn(0f, 1f)
            ((progress - holdFraction) / (1f - holdFraction).coerceAtLeast(1e-6f)).coerceIn(0f, 1f)
        } else {
            progress
        }
        // v2 §9b: a delayed incoming track is gated silent until its start
        // offset, then ramps over the remaining span.
        val inProgress = if (render.incomingStartDelaySec > 0 && effSpan > 1L) {
            val delayFraction = (render.incomingStartDelaySec * 1000.0 / effSpan).toFloat().coerceIn(0f, 1f)
            ((progress - delayFraction) / (1f - delayFraction).coerceAtLeast(1e-6f)).coerceIn(0f, 1f)
        } else {
            progress
        }
        when (render.volumeCurve) {
            VolumeCurve.LOGARITHMIC -> {
                player.volume = riseGain(inProgress)
                out.volume = (1f - outProgress).pow(2f)
            }
            VolumeCurve.INSTANT -> {
                // The final stretch rides a linear settle toward the flip
                // endpoints instead of holding then stepping: identical
                // endpoints, no timing change, but no full-scale step for the
                // speaker when the splice guard bows out (non-PCM-16 input).
                // Booth loop-cut: one full beat of closure (a punch, not a
                // crop) so the drop lands into the ringing tail instead of
                // chopped silence; the roll extend gets two beats of release
                // glide for the same reason. Other INSTANT cuts keep the
                // 90 ms settle. Click fix: on float output the splice guard
                // bows out (16-bit only), so non-loop cuts get a 1-beat
                // closure there too — same endpoints, longer ramp, no step.
                // Spans under the settle behave exactly as before.
                val floatOut = AppSettings.outputPcmMode.value == OutputPcmMode.FLOAT_32
                val settleMs = if (render.style == TransitionStyle.LOOP_CUT_DROP &&
                    render.loopBeatSeconds > 0
                ) {
                    max(INSTANT_SETTLE_MS, (render.loopBeatSeconds * 1000).roundToLong())
                } else if (render.style == TransitionStyle.LOOP_ROLL &&
                    render.loopBeatSeconds > 0
                ) {
                    max(INSTANT_SETTLE_MS, (render.loopBeatSeconds * 2000).roundToLong())
                } else if (floatOut && render.mixset) {
                    // Full-audit F1: DJ cuts on float get a 1-beat closure when
                    // gridded (capped at 500 ms), 250 ms floor when not — the
                    // 90 ms origin settle is thin cover for a full-scale step
                    // without the (16-bit-only) splice guard. Stock path below
                    // stays byte-identical to origin.
                    val beatMs = (render.eqSwapBeatSec * 1000).roundToLong()
                        .takeIf { render.eqSwapBeatSec > 0 } ?: 250L
                    min(500L, max(INSTANT_SETTLE_MS, beatMs))
                } else if (floatOut && render.eqSwapBeatSec > 0) {
                    max(INSTANT_SETTLE_MS, (render.eqSwapBeatSec * 1000).roundToLong())
                } else {
                    INSTANT_SETTLE_MS
                }
                val remainingMs = effSpan - elapsed
                if (remainingMs <= settleMs) {
                    val s = (1f - remainingMs.toFloat() / settleMs).coerceIn(0f, 1f)
                    player.volume = s
                    out.volume = 1f - s
                } else {
                    player.volume = if (inProgress >= 1f) 1f else 0f
                    out.volume = if (outProgress >= 1f) 0f else 1f
                }
                // Edge-trigger the splice guard at the flip tick: both decks
                // step full-scale here, each mid-waveform. The guard's 8+8 ms
                // ramps replace the step; the volume curve itself is untouched.
                // DJ-only: stock upstream flips dry.
                if (render.mixset && !cutFired && (inProgress >= 1f || outProgress >= 1f)) {
                    cutFired = true
                    spliceGuards.cut()
                }
            }
            VolumeCurve.LINEAR -> {
                player.volume = inProgress
                out.volume = 1f - outProgress
            }
            VolumeCurve.S_CURVE -> {
                player.volume = riseGain(inProgress)
                out.volume = fallGain(outProgress)
            }
        }
        // DJ level-ride: both faders stay up, EQ tells the story. For the
        // long-blend recipes the outgoing deck holds full level instead of
        // riding the crossfade decay — the handoff is the EQ ownership ramp
        // + bass swap + the existing mute disposal, never a volume valley.
        // The vocal choke's LOG flip is bypassed here (its forceDuckKeys
        // still drive the EQ below); wash and cut families keep their curves.
        // Flat absolute: bands never stack (bass swaps, mids yield by 0.80),
        // so the sum stays clean without a coexistence pad.
        val levelRide = render.eqEnabled &&
            (render.style == TransitionStyle.DJ_BLEND || render.style == TransitionStyle.DJ_FILTER) &&
            (render.mixRecipe == MixRecipe.VOCAL_DUEL || render.mixRecipe == MixRecipe.INSTRUMENTAL_BED)
        if (levelRide) {
            player.volume = riseGain(inProgress)
            // Full-audit F5: duck under the brake dive — a slowed deck stays
            // present but yields instead of holding unity at falling pitch.
            // Depth comes from the live brake tracker (1.0 = parked).
            val speed = AppSettings.playbackSpeed.value
            val diveDepth =
                (1f - lastBrakeRate / speed.coerceAtLeast(1e-6f)).coerceIn(0f, 1f)
            out.volume = 1f - diveDepth * 0.6f
        }
        // Half-time downbeat emphasis (§11.2): a 2-frame low-pass pulse as the
        // stretched grid crosses each planned phrase start. Tracked so a pulse
        // fires once per offset even across pause-parked ticks.
        rideHalfTimeEmphasis(elapsed / 1000.0)
        // Only from here, never during ARMING: the standby is silent until the
        // handoff, and [filters] describes the split between the track arriving
        // and the track leaving, which only exists once both are audible.
        rideFilters(progress, inProgress)
        // DJ-EQ spec: per-tick 3-band targets from the type schedule. Runs
        // after the sweep ride; the emphasis pulse below touches the SVF, not
        // the EQ, so ordering between them is irrelevant.
        rideEq(progress, outProgress, inProgress)
        // The decisive cut, per style: long blends (DJ_BLEND) ring out to
        // 0.95 so the release lands — the ear needs the tail to call the
        // blend satisfying. Punchy types (DJ_FILTER/HARD/LOOP) keep the 0.80
        // kill; echo and dissolve are exempt entirely (their tails ARE the
        // style). Full-audit P2 C3: this block is the blend's single silence
        // authority — the schedules voice the gesture, this disposes the dry
        // path, and the EQ kill starves the sends. Nothing else may zero a
        // deck mid-fade, so a bleed always points here.
        // Player volume is downstream of the whole chain (applied at
        // the sink), so ramping it silences the dry path absolutely while
        // the EQ kill starves the sends of new feed. Placed after rideEq so
        // it wins every tick. DJ end-click fix: muteRampGain() ramps over
        // BAIL_MS via fallGain (zero-slope landing) instead of the old
        // one-tick 1->0 snap, which chopped full-scale under level-ride.
        val muteCutoff = when (render.style) {
            TransitionStyle.DJ_BLEND -> 0.95f
            // Full-plan P3: DJ_FILTER matches the blend release — sweep bed
            // + EQ empty the band by 0.80 anyway, so the 0.80 mute disposed
            // a nearly-empty path 15% early.
            TransitionStyle.DJ_FILTER -> 0.95f
            TransitionStyle.ECHO_REVERB_OUT,
            TransitionStyle.PLAIN_DISSOLVE,
            -> Float.MAX_VALUE // exempt: never mutes
            // P1.3: CUT families hold full gain until the flip by contract
            // (INSTANT curve); the 0.80 mute manufactured seconds of silence
            // before the guard fires. The edge-triggered splice guard + 90 ms
            // settle own the click here, not the mute.
            TransitionStyle.HARD_CUT,
            TransitionStyle.LOOP_CUT_DROP,
            TransitionStyle.LOOP_ROLL,
            -> Float.MAX_VALUE // exempt: flip owns the handoff
            else -> 0.80f
        }
        // Stock upstream on normal Automix and on manual fades: gains run
        // to 1, no disposal. The mute below stays DJ-only.
        if (render.mixset && smartFadeActive && outProgress >= muteCutoff) {
            out.volume = muteRampGain(out.volume)
            eqFilters.outgoing(0f, 0f, 0f)
            dryKilled = true
        } else if (render.mixset && !smartFadeActive && progress >= 0.80f) {
            out.volume = muteRampGain(out.volume)
        }
        // v2 §7d/§11.2: no shelf on the processor, so the "low-shelf +3dB"
        // accent is a one-tick dip of the incoming high-pass to 80 Hz at
        // each planned phrase start — the spec's 16 ms pulse lands on the
        // next fade tick (~30 ms), which is the finest granularity the
        // ticker has. Applied after rideFilters so it wins for one tick
        // only, then the ride re-aims.
        if (emphasisTicksLeft > 0) {
            emphasisTicksLeft--
            filters.incoming(TransitionFilterProcessor.OPEN_HZ, 80f)
        }
        // Tempo glide-back: the incoming deck rode a stretched rate to sit on
        // the shared grid; riding it home to its own tempo before the handoff
        // avoids the snap finish() would otherwise deliver at full volume.
        // The key shift rides the same curve in the same call — pitch and tempo
        // returning together is what a DJ's pitch fader does. Unity means no
        // call: ExoPlayer re-prepares its audio pipeline on parameter changes.
        // Progress (not inProgress): the glide follows the whole fade, so a
        // delayed entry still lands home by the handoff.
        // The plan rate lives on this controller (set at arm time in begin),
        // not on Render: Render describes the mix, the rate describes the deck.
        val stretch = incomingPlaybackRate
        val shift = render.keyShiftSemitones
        // DJ-only: stock upstream leaves the deck at the plan rate and lets
        // finish() snap it home. Gliding back on normal Automix changes the
        // audible pitch/tempo path of every stretched stock blend.
        if (render.mixset && (stretch != 1.0 || shift != 0)) {
            val glide = tempoGlideFactor(progress, stretch)
            // Full-audit P2: coalesce the per-tick parameter commits.
            // ExoPlayer re-prepares its audio pipeline on every parameter
            // change, so ~33 commits/second of inaudible deltas is 33
            // chances/second to glitch (the tempo stutter). Commits only
            // when the rate audibly moved (>= 0.15% since last commit).
            // Click fix: the old glide>0.02 outer gate is gone — it skipped
            // the final converge, stranding the deck ~1-2% off-tempo so
            // finish() snapped it at full volume. The coalescer below
            // already suppresses redundant unity commits; the glide now
            // lands home instead of stopping short.
            val newRate = (AppSettings.playbackSpeed.value * (1.0 + (stretch - 1.0) * glide)).toFloat()
            val newPitch = 2.0.pow(shift / 12.0 * glide).toFloat()
            val last = lastCommittedRate
            if (last == null ||
                abs(newRate - last) / last.coerceAtLeast(1e-6f) >= 0.0015f
            ) {
                player.setPlaybackParameters(PlaybackParameters(newRate, newPitch))
                lastCommittedRate = newRate
                lastRateCommitAt = SystemClock.elapsedRealtime()
            }
        }

        // DJ brake (F2): dive the outgoing deck toward a stop over the last
        // quarter of the blend. Varispeed feel: pitch slaved to rate, so the
        // music falls in pitch as it slows — a turntable power-off, not a
        // pitch-held slowdown. Quadratic dive, floored at 0.1 (Sonic rejects
        // non-positive speeds; the handoff/retire takes the deck from there).
        // Coalesced at 0.5%: ~15 commits over the dive, not 33. Entry may
        // step down up to 6% when the pair needed a stretch — the stretch is
        // exactly what the brake replaces, and the step lands deep in the
        // blend under a falling dry path. Incoming deck untouched.
        // Booth backspin: same DSP, compressed to the last BACKSPIN_WINDOW_MS
        // with a cubic curve — a hand-dragged spin-back into the cut, not a
        // power-off. The ½-beat dub tail (echoThrow) covers the hole while
        // the deck dies; the handoff lands the drop at full energy.
        if (render.mixset && render.brake && effSpan > 1L) {
            val windowStart = if (render.backspin) {
                max(0f, 1f - BACKSPIN_WINDOW_MS.toFloat() / effSpan.toFloat())
            } else {
                0.75f
            }
            if (outProgress < windowStart) {
                lastBrakeRate = AppSettings.playbackSpeed.value
            } else {
                val brakeT = ((outProgress - windowStart) / (1f - windowStart).coerceAtLeast(1e-6f))
                    .coerceIn(0f, 1f)
                val speed = AppSettings.playbackSpeed.value
                val dive = if (render.backspin) brakeT * brakeT * brakeT else brakeT * brakeT
                val brakeRate = (speed * (1f - dive * 0.97f)).coerceAtLeast(0.1f * speed)
                val brakePitch = (brakeRate / speed.coerceAtLeast(1e-6f)).coerceIn(0.1f, 1f)
                val last = lastBrakeRate
                if (abs(brakeRate - last) / last.coerceAtLeast(1e-6f) >= 0.005f) {
                    out.setPlaybackParameters(PlaybackParameters(brakeRate, brakePitch))
                    lastBrakeRate = brakeRate
                }
            }
        }

        // Whichever comes first: the fade running its course, the old track
        // genuinely ending, the tail failing outright, or whichever setting
        // armed this fade being switched off mid-blend. Checked against the
        // setting that actually started it — a Automix normally runs with
        // [configuredFadeMs] at zero, and reading that as "turned off" would
        // end every Automix on its first tick.
        val settingSwitchedOff = if (smartFadeActive) {
            !AppSettings.smartFadeEnabled.value
        } else {
            configuredFadeMs() <= 0L
        }
        val done = progress >= 1f ||
            out.playbackState == Player.STATE_ENDED ||
            out.playbackState == Player.STATE_IDLE ||
            settingSwitchedOff
        // span-narrow clamp: an incoming cap tightening effSpan mid-fade must
        // not fire done inside the 120 ms mute ramp — the ramp always lands
        // before finish(). untripped (start < 0) reads settled, so only a
        // live ramp ever holds the gate.
        val muteSettled = muteRampStartMs < 0L ||
            SystemClock.elapsedRealtime() - muteRampStartMs >= BAIL_MS
        val gatedDone = done && muteSettled
        if (gatedDone) {
            // Early done (natural end, cap clamp, toggled off) lands finish()
            // at progress < 1, where its volume/tempo snaps are audible. A
            // micro-fade here covers the settle; a completed fade skips it —
            // at gain ≈ 1 an 8 ms dip would be the click. lastProgress records
            // the tick so finish() can apply the same rule independently.
            lastProgress = progress
            // DJ-only: stock upstream finishes dry.
            if (render.mixset && progress < 0.98f) spliceGuards.cut()
            finish()
        }
    }

    /** Ramps the outgoing track away rather than cutting it, so an interruption has no click in it. */
    /**
     * DJ end-click fix: ramps the outgoing dry gain to zero over BAIL_MS from
     * whatever it holds when the mute cutoff first trips (unity under
     * level-ride), instead of the old one-tick 1->0 snap. fallGain lands with
     * zero slope, same as the bail ramp. Called after the rides every tick so
     * it wins; idempotent per transition via [muteRampStartMs].
     */
    private fun muteRampGain(current: Float): Float {
        val now = SystemClock.elapsedRealtime()
        if (muteRampStartMs < 0L) {
            muteRampStartMs = now
            muteFromGain = current
        }
        val t = ((now - muteRampStartMs).toFloat() / BAIL_MS).coerceIn(0f, 1f)
        return muteFromGain * fallGain(t)
    }

    private fun driveBail() {
        val out = outgoing
        if (out == null) {
            finish()
            return
        }
        val progress = (SystemClock.elapsedRealtime() - bailStartedAt).toFloat() / BAIL_MS
        if (progress < 1f) {
            out.volume = bailFromGain * fallGain(progress)
            // Mirror of the outgoing ramp: the incoming glides up from where
            // it was instead of the old instant jump to full. fallGain-based
            // so it lands at 1.0 with zero slope — the start kink is small
            // (a fraction of full over 120 ms) where the old snap was full.
            incoming?.volume = 1f - (1f - bailFromGainIn) * fallGain(progress)
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
        TrackLog.d(TAG, "bail from $phase")
        // Restore origin ee8a348 verbatim for stock Automix: bail cooldown
        // and half-time write are DJ-only (LEAK #11). Origin had no cooldown
        // and no half-time.
        val isDj = render.mixset
        if (isDj) {
            // Missed-window fix F4: count consecutive bails and hold the planner
            // back escalatingly. The cooldown is consumed in
            // considerAutoTransition; reset on begin() (arm accepted) and on
            // natural auto-advance (listener).
            consecutiveBailCount++
            val backoffMs = when (consecutiveBailCount) {
                1 -> 1_500L
                2 -> 3_000L
                3 -> 6_000L
                else -> 10_000L
            }
            bailCooldownUntilMs = SystemClock.elapsedRealtime() + backoffMs
            AppSettings.sharedHalfTimeBpm.value = null
        }
        AppSettings.smartMixInProgress.value = false
        if (!handedOff) {
            // Nothing was ever audible; no ramp to run.
            finish()
            return
        }
        // Glided open rather than snapped: the incoming track is audible here,
        // and if the bail caught a bass swap mid-handover its low end is
        // currently lifted out. Dropping a 24 dB/octave filter in one buffer is
        // the click this ramp exists to avoid.
        filters.open()
        echoFilters.open()
        loopVamps.open()
        // Full-plan P5: stepped reverb close instead of the open() snap —
        // heavy-clash bails chopped the tail at BAIL_MS=120. tick() drains
        // the armed wet over 200 ms while the deck is still at partial gain.
        pendingReverbCloseFromWet = render.reverbAmount.toFloat().coerceIn(0f, 0.5f)
        pendingReverbCloseStartMs = SystemClock.elapsedRealtime()
        eqFilters.open()
        bailFromGain = outgoing?.volume ?: 0f
        bailFromGainIn = incoming?.volume ?: 0f
        // Reset the glide while both decks are still at partial gain: the old
        // order (snap in finish() at full volume) turned every interrupted
        // stretched blend into an audible pitch jump. finish() keeps its own
        // reset as the idempotent safety net.
        incoming?.setPlaybackParameters(PlaybackParameters(AppSettings.playbackSpeed.value, 1f))
        // The restore above already happened: finish() must not re-prepare
        // the pipeline a second time at full volume for the same restore
        // (its guard reads lastProgress, frozen before this reset).
        deckRateReset = true
        bailStartedAt = SystemClock.elapsedRealtime()
        phase = Phase.BAILING
    }

    private fun finish() {
        if (phase != Phase.IDLE) {
            TrackLog.d(TAG, "finish from $phase")
            // Stamped under this guard rather than beside the assignment at the
            // bottom, because this function is idempotent and gets called with
            // nothing in flight: marking every one of those as a transition
            // just ended would keep pushing the mark forward and hold a waiting
            // caller off for as long as the calls kept coming.
            settledAt = SystemClock.elapsedRealtime()
        }
        AppSettings.smartMixInProgress.value = false
        // Restore origin ee8a348 verbatim for stock Automix: half-time write
        // is DJ-only (origin had no half-time).
        if (render.mixset) AppSettings.sharedHalfTimeBpm.value = null
        // Capture before Render() is parked below: the tempo/pitch reset must
        // only run when a stretch or shift was actually applied. An
        // unconditional setPlaybackParameters re-prepares ExoPlayer's audio
        // pipeline at full volume — the end-of-mix snap on every plain blend.
        val rateApplied = incomingPlaybackRate != 1.0 || render.keyShiftSemitones != 0
        // early-done leaves dsp as-is: no re-park here. begin() re-parks
        // everything while silent before next audible use, and re-parking at
        // full incoming volume only pumps the join.
        // DJ effects (F1/F3) bookkeeping, captured before Render() is parked
        // below: whether this blend carried a throw/brake, and the throw's
        // delay + amount for closing the send at the handoff.
        val hadEffect = render.mixset && (render.echoThrow || render.brake)
        val wasDj = render.mixset
        val throwBlend = render.mixset && render.echoThrow
        val throwDelaySec = render.echoBeatSeconds
        val throwAmount = render.echoAmount
        // Booth loop-cut AND roll extend: the echo tail rings under
        // the incoming drop instead of chopping at the flip — the glue
        // between the freeze and the landing. Captured pre-reset like the
        // throw. The cut freezes at 0.5 wet; the roll releases at ~0.30, so
        // each closes from its own level (closing from the wrong one would
        // step the send audibly at the flip).
        val loopTailCut = render.mixset && render.style == TransitionStyle.LOOP_CUT_DROP
        val loopTailRoll = render.mixset && render.style == TransitionStyle.LOOP_ROLL
        val loopTailDelaySec = render.echoBeatSeconds
        // Parity fix (Issue 3): the reverb-tail branch below read
        // render.reverbAmount AFTER parking Render() — always 0.0, so the
        // tail never rang. Capture pre-park like the throw above.
        val reverbTailAmount = render.reverbAmount
        // Parity (Issue 3): the regular echo wash gets the same late-retire
        // the throw/loop tails get — otherwise the flip chops the send
        // mid-ring. DJ-only; stock stays immediate.
        val echoTail = render.mixset && render.style == TransitionStyle.ECHO_REVERB_OUT &&
            render.echoAmount > 0.0 && !throwBlend
        val echoTailAmount = render.echoAmount
        val echoTailDelaySec = render.echoBeatSeconds
        render = Render()

        // Energy-dip fix (Issue 2): the DJ fade ran with silence-skipping
        // off (see begin()); hand the listener's setting back to both decks.
        outgoing?.skipSilenceEnabled = AppSettings.skipSilence.value
        incoming?.skipSilenceEnabled = AppSettings.skipSilence.value

        if (handedOff) {
            // The roles have already traded: the incoming player is the session
            // and owns the queue from here, and the outgoing one is spare.
            incoming?.let {
                // No redundant writes: setting what is already set is free on
                // paper, but every sink call is a chance for the platform to
                // do work at full volume.
                if (it.volume < 0.999f) it.volume = 1f
                // Undoes whatever [begin] stacked on for a beatmatched handoff —
                // speed AND pitch. setPlaybackSpeed would leave a shifted pitch
                // behind to leak into the next track, so both reset together.
                // Skipped when nothing was applied: no pipeline re-prepare, no snap.
                // And skipped when the glide already landed home: the deck is
                // at its own tempo, so "resetting" would re-prepare the
                // pipeline to arrive where it already is — the end-of-mix snap
                // on every beatmatched blend. And skipped when bail() already
                // restored the rate at partial gain (deckRateReset) — a
                // second commit here lands at full volume.
                // Tempo/pitch-leak fix G1: the glide gate above measures the
                // glide at the END (≈0 on every completed blend), not the rate
                // actually on the deck — a blend that rode 1.08x home reads
                // "converged" and keeps 1.08x for the whole next track. Compare
                // live against desired instead. Click fix: 0.3% tolerance —
                // a converged glide leaves the deck within coalescer steps of
                // home (inaudible); an exact compare would snap that residue
                // at full volume.
                val live = it.playbackParameters
                val wantSpeed = AppSettings.playbackSpeed.value
                val rateActuallyOff =
                    abs(live.speed - wantSpeed) / wantSpeed.coerceAtLeast(1e-6f) >= 0.003f ||
                        abs(live.pitch - 1f) >= 0.003f
                // Full-plan P2: rateActuallyOff alone authorizes the reset even
                // when early — the exact compare proves the deck is off-home,
                // and leaving a shifted deck for the whole next track is worse
                // than a guarded reset. deckRateReset still prevents doubles.
                if (rateApplied && !deckRateReset &&
                    (lastProgress >= 0.98f || rateActuallyOff) &&
                    (tempoGlideFactor(lastProgress, incomingPlaybackRate) > 0.02 || rateActuallyOff)
                ) {
                    it.setPlaybackParameters(PlaybackParameters(AppSettings.playbackSpeed.value, 1f))
                }
            }
            // Full-audit F4: a braked/spun deck feeds its dub tail at dying
            // pitch unless homed first. The dry deck is already muted at the
            // finish tick (flip / out.volume 0), so resetting the rate here
            // only re-times the echo feed — post-fader throw at speed while
            // the dry dies. DJ-correct order.
            if (render.brake) {
                outgoing?.setPlaybackParameters(PlaybackParameters(AppSettings.playbackSpeed.value, 1f))
                lastBrakeRate = AppSettings.playbackSpeed.value
            }
            // DJ echo throw (F1): step the send shut so the line stops taking
            // new feed and rings its residual ~2 s, then retire the deck late
            // (see tick()) so the tail survives the handoff — short tails
            // included, the deck is silent or ending anyway.
            if (throwBlend) {
                // stepped close, not one-tick: tick() glides the send to 0
                // over THROW_CLOSE_MS. from-wet mirrors rideDjSend's attack
                // at the finish tick, so the first step continues the voice.
                // capped at MAX_WET (0.5, private to the processor).
                val attack = ((lastProgress - 0.55f) / 0.15f).coerceIn(0f, 1f)
                pendingEchoCloseFromWet = (throwAmount * attack).toFloat().coerceIn(0f, 0.5f)
                pendingEchoCloseDelaySec = throwDelaySec.toFloat()
                pendingEchoCloseStartMs = SystemClock.elapsedRealtime()
                // late retire either way, short tails included: the deck is
                // silent or ending, and begin() flushes an overlapping arm.
                pendingRetirePlayer = outgoing
                pendingRetireAtMs = pendingEchoCloseStartMs + 2500L
            } else if (reverbTailAmount > 0.0) {
                // Full-plan P5: reverb tails get the same late-retire the echo
                // throw gets — retire()->stop() at finish() truncates the
                // ~2.5 s Schroeder tail otherwise. Stepped close + 2.5 s ring.
                pendingReverbCloseFromWet = reverbTailAmount.toFloat().coerceIn(0f, 0.5f)
                pendingReverbCloseStartMs = SystemClock.elapsedRealtime()
                pendingRetirePlayer = outgoing
                pendingRetireAtMs = pendingReverbCloseStartMs + 2500L
            } else if (loopTailCut || loopTailRoll) {
                // Booth loop-cut/roll: stepped echo close from the tail wet,
                // then retire late so the tail rings ~2 s under the drop.
                // Without this the flip chops the send mid-ring (the crop).
                pendingEchoCloseFromWet = if (loopTailRoll) 0.3f else 0.5f
                pendingEchoCloseDelaySec = loopTailDelaySec.toFloat()
                pendingEchoCloseStartMs = SystemClock.elapsedRealtime()
                pendingRetirePlayer = outgoing
                pendingRetireAtMs = pendingEchoCloseStartMs + 2500L
            } else if (echoTail) {
                // Parity (Issue 3): the regular echo wash rings ~2 s under
                // the handoff instead of chopping at the flip.
                pendingEchoCloseFromWet = echoTailAmount.toFloat().coerceIn(0f, 0.5f)
                pendingEchoCloseDelaySec = echoTailDelaySec.toFloat()
                pendingEchoCloseStartMs = SystemClock.elapsedRealtime()
                pendingRetirePlayer = outgoing
                pendingRetireAtMs = pendingEchoCloseStartMs + 2500L
            } else {
                outgoing?.let(::retire)
            }
        } else {
            // The transition never became audible, so the session player never
            // moved and the standby is the one to throw away. The session
            // player may still carry the outgoing stretch startFade applied
            // for a half-time blend — reset it, or the track keeps playing at
            // the shared tempo indefinitely after the bail.
            outgoing?.volume = 1f
            outgoing?.setPlaybackParameters(PlaybackParameters(AppSettings.playbackSpeed.value, 1f))
            incoming?.let(::retire)
        }

        // F3 rotation: only DJ blends advance the cooldown (manual fades and
        // normal Automix leave it alone).
        if (wasDj) blendsSinceEffect = if (hadEffect) 0 else blendsSinceEffect + 1
        // Blueprint LOOP_CUT_DROP: the vamp never survives the handoff — the
        // deck is retired or re-armed from here, and begin() re-parks anyway.
        loopVamps.open()
        lastLoopBeats = -1f
        outgoing = null
        incoming = null
        handedOff = false
        queuedItemCount = 0
        incomingCueTimeMs = 0L
        incomingPlaybackRate = 1.0
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
        // Volume first, then stop: a spare retired mid-gain (late handoff,
        // early done) would otherwise chop full-scale content into the next
        // transition. The completion guard covers the PCM-16 splice; this
        // covers everything downstream of it.
        player.volume = 0f
        player.stop()
        player.clearMediaItems()
        player.volume = 1f
        // Parameters, not speed alone: a retired player may carry a key shift,
        // and it is some future transition's incoming player.
        player.setPlaybackParameters(PlaybackParameters(AppSettings.playbackSpeed.value, 1f))
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
    /**
     * DJ-EQ spec: per-tick 3-band targets from the type schedule tables.
     *
     * MID/HIGH come straight from [EqSchedule] keyframes (already continuous
     * ramps, so 30 ms re-aims stay under the Rule 3 jump budget). LOW belongs
     * to the bass-swap state machine on swap types: A holds bass until the
     * pre-snapped downbeat fires, then both decks hand over across 2 bars; on
     * table-driven types the LOW keyframes ride as written. Disabled entirely
     * for standard fades, which snapshot no grids.
     */
    private fun rideEq(progress: Float, outProgress: Float, inProgress: Float) {
        if (!render.eqEnabled) return
        // Full-audit P1 M1: beds under EqSchedule.SHORT_BED_SECONDS render
        // the compressed short-bed tables — the long-bed keyframes never
        // leave unity inside a phrase-switch bed.
        val shortBed = render.overlapSeconds in 0.01..EqSchedule.SHORT_BED_SECONDS
        // Real-DJ long blend: 16s+ beds voice the spread long tables so the
        // 12-20s gap (LONG_BED 20) no longer falls to the default unity hold.
        val longBed = render.overlapSeconds >= 16.0
        // Full-audit P1 M3: the vocal choke voices duck keys with the log
        // curve even when the ARM flags read clean.
        // Full-audit P2 S1: OR in the live recompute — the ARM flags only
        // knew the planned zone, while a vocal can enter mid-blend as the
        // window slides. Slewed (~500 ms) so a single hot frame cannot
        // flap the mids.
        updateLiveVocalFlags(outProgress, inProgress)
        // Single analyzer-driven vocal gate (mirrors MixConductor recipe rule):
        // ARM flags + force choke + live recompute, once per tick, plus the
        // planned overlap so a sung bed can't slip through silent.
        val duck = render.duckAMids || render.forceDuckKeys || liveDuckA > 0.5f || liveDuckLatchedA
        val delay = render.delayBMids || render.forceDuckKeys || liveDelayB > 0.5f || liveDelayLatchedB
        val vocalGate = duck || delay || render.vocalOverlap > 0.2
        val out = EqSchedule.outgoingGains(render.eqType, progress, duck, shortBed, longBed)
        val into = EqSchedule.incomingGains(render.eqType, progress, delay, longBed)
        val swapAt = render.eqSwapFireProgress
        val (lowOut, lowIn) = if (swapAt.isFinite()) {
            if (!eqSwapFired && progress >= swapAt) {
                eqSwapFired = true
                eqSwapStartProgress = progress
            }
            if (!eqSwapFired) {
                1f to 0f
            } else {
                val overlap = render.overlapSeconds.toFloat()
                // Full-audit P2 S2: time-denominated swap. The bar length is
                // ARM-time outgoing-grid seconds; when the outgoing deck rides
                // a grid rate (half-time join, post pre-fade ramp) the real
                // seconds shrink by that rate — a bar-denominated snap would
                // land late and smear the handover.
                val deckRate = render.outgoingPlaybackRate.toFloat().coerceAtLeast(0.25f)
                val swapSec = (render.eqSwapBeatSec * render.eqSwapBars * 4 / deckRate).toFloat()
                val t = if (overlap > 0f && swapSec > 0f) {
                    ((progress - eqSwapStartProgress) * overlap / swapSec).coerceIn(0f, 1f)
                } else {
                    1f
                }
                // Finetune F2: constant-power crossover. The old smoothstep
                // traded linearly (both bass at ~0.5 mid-swap = an energy
                // hole right where the ear waits for the switch). cos/sin
                // keeps the low-end power sum at 1.0 through the swap —
                // endpoints identical, zero slope at both ends preserved.
                val a = t * PI.toFloat() / 2f
                cos(a) to sin(a)
            }
        } else {
            out.low to into.low
        }
        // P2-smart ownership: the recipe — not the schedule — owns the outgoing
        // mids/highs once the incoming voice is in play. The DJ vocal
        // handoff: the old vocal yields its band gradually, reaching ~0 at
        // outProgress 0.80, while its bass stays for the swap machine and the
        // incoming deck keeps full mids. From there the listener hears only
        // the new vocal; the volume mute later disposes an already-empty band.
        // WASH_OUT gets the same early mid-cut (its wet tail still rings —
        // sends are starved, not drained). Kill-once: after the mute fires,
        // hold the kill instead of re-voicing the schedule every tick.
        // Finetune F1: the yield runs quadratic, not linear — at mid-blend
        // the old vocal is down to ~1/4 instead of ~1/2, so the mud never
        // forms; and the incoming mids/highs layer in over the first ~30%
        // instead of arriving full (DJ brings the new track in by layers).
        // delay already folds forceDuckKeys + liveDelayB; don't double-count.
        val incomingSings = delay
        // Real-DJ long blend: B layers in over half a 32-bar bed, not the
        // first third — highs → mids → lows order holds at any bed length.
        val entrySpan = if (longBed) 0.50f else 0.30f
        val entryT = (progress / entrySpan).coerceIn(0f, 1f)
        val entryRamp = entryT * entryT * (3f - 2f * entryT)
        if (dryKilled) {
            eqFilters.outgoing(0f, 0f, 0f)
        } else {
            // Full-plan P3: INSTRUMENTAL_BED joins when the bed actually
            // sings (single vocalGate) — a sung instrumental bed double-mids
            // through the swap otherwise. Truly voiceless beds stay flat.
            // EQ-muddy hotfix (DJ-only, stock never enters rideEq): any
            // vocalGate now drives ownership, not just INSTRUMENTAL_BED —
            // trace 0.12-0.20 collisions were holding old mids at 1.0 through
            // mid-blend because recipe was INSTRUMENTAL_BED but gate false.
            val ownership = if (
                (render.mixRecipe == MixRecipe.VOCAL_DUEL || render.mixRecipe == MixRecipe.WASH_OUT ||
                    vocalGate) &&
                incomingSings
            ) {
                val lin = ((0.80f - outProgress) / 0.80f).coerceIn(0f, 1f)
                // Long blend keeps warmth: linear yield (not quadratic) so
                // mid-blend old mids stay ~0.37 instead of ~0.14.
                if (longBed) lin else lin * lin
            } else {
                1f
            }
            eqFilters.outgoing(lowOut, out.mid * ownership, out.high * ownership)
        }
        // Booth highs-first entry: the top band rides the schedule × fader
        // from the foothold (present, never stacked), while the ramp holds
        // back the mids alone. Gating highs on the ramp too triple-
        // suppressed the entry (ramp × curve × delay taper) into a hole
        // followed by a catch-up swell — the up-down the booth never does.
        // Long blend warmth: tame the shimmer mid-blend so body, not air, dominates.
        val highIn = if (longBed) into.high * (0.70f + 0.30f * entryRamp) else into.high
        eqFilters.incoming(lowIn, into.mid * entryRamp, highIn)
    }

    /**
     * DJ send-driving arm (F1 throw / F3 wash): voices the echo throw and the
     * reverb wash bed on DJ_BLEND/DJ_FILTER, alongside (not instead of) the
     * filter path — sends and filters address different buses. Throw: linear
     * attack from progress 0.55 to 0.70, then held; the dry path mutes at
     * 0.80/0.95 and finish() closes the send, so the line rings its residual
     * ~2 s past the handoff. Wash: bloom over the first half.
     */
    private fun rideDjSend(progress: Float) {
        if (render.echoThrow && render.echoAmount > 0.0 && render.echoBeatSeconds > 0.0) {
            val attack = ((progress - 0.55f) / 0.15f).coerceIn(0f, 1f)
            echoFilters.outgoing((render.echoAmount * attack).toFloat(), render.echoBeatSeconds.toFloat())
        }
        if (!render.echoThrow && render.reverbAmount > 0.0) {
            val bloom = (progress * 2f).coerceIn(0f, 1f)
            reverbFilters.outgoing((render.reverbAmount * bloom).toFloat(), false)
        }
    }

    private fun rideFilters(progress: Float, inProgress: Float) {
        // Resonance belongs to the sweep gesture only; every other style
        // re-parks it so a previous DJ_FILTER transition never leaks Q.
        // Stock upstream on normal Automix: no Q voicing at all (the ARM-time
        // park in begin() already covers cross-blend leaks, silently).
        if (render.mixset && render.style != TransitionStyle.DJ_FILTER) filters.setResonance(1.0f)
        when (render.style) {
            TransitionStyle.DJ_FILTER -> {
                // Review v2.1 C1: resonant sweep on the outgoing low-pass.
                if (render.mixset) {
                    // DJ effects (F1/F3): sends stack with the sweep.
                    if (render.echoThrow || render.reverbAmount > 0.0) {
                        rideDjSend(progress)
                    }
                    filters.setResonance(TransitionFilterProcessor.FILTER_SWEEP_Q_FACTOR)
                    rideFilterSweep(progress)
                    rideMidKill(progress)
                } else {
                    rideFilterSweep(progress)
                }
            }
            TransitionStyle.DJ_BLEND -> {
                // DJ effects (F1/F3): the send-driving arm runs alongside the
                // filter path below — a throw/wash is a send move, the swap
                // and separation are filter moves, and they stack by design.
                if (render.mixset && (render.echoThrow || render.reverbAmount > 0.0)) {
                    rideDjSend(progress)
                }
                // Full-audit P2 C5: a single swap event. The DJ-EQ schedule
                // owns the low-end handover (downbeat-snapped fire + smooth
                // 1-bar trade in rideEq); the legacy SVF bass swap below runs
                // ONLY as fallback when no schedule swap fired for this type —
                // both voicing the same corner is the muddy low end.
                if (render.bassSwap && !render.eqSwapFireProgress.isFinite()) {
                    filters.setResonance(1.0f)
                    rideBassSwap(progress)
                } else if (render.mixset && render.overlapSeconds > PROACTIVE_MID_CUT_MIN_OVERLAP_SECONDS &&
                    // Warm long blend: the spread EqSchedule already trades mids
                    // across the whole 32-bar bed — a 600/300 SVF on top would
                    // double-cut the low-mids (200-500Hz) and hollow the body.
                    render.overlapSeconds < 16.0 &&
                    !render.duckAMids && !render.delayBMids
                ) {
                    // Review v2.1 C2 (see rideProactiveMidCut). DJ-only: stock
                    // upstream falls through to rideVocalSeparation below.
                    filters.setResonance(1.0f)
                    rideProactiveMidCut(progress)
                } else {
                    filters.setResonance(1.0f)
                    rideVocalSeparation(progress)
                }
            }
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
            // Blueprint ECHO_REVERB_OUT, P0 wash: the outgoing track sinks
            // behind a closing low-pass scaled by the plan's wet amount while
            // the incoming one opens dry. The dedicated echo send (P1) rides
            // on top of this; the wash alone already decays, never clashes.
                TransitionStyle.ECHO_REVERB_OUT -> {
                // Spec finetune §5: the plan carries a reverb
                // offset, and the envelope below replaces the P0 wash — the
                // outgoing track holds full for 3 s, then sinks behind echo
                // (1.0→0.70 wet) and reverb (→0.75, decaying — freeze removed:
                // unity-feedback sustain clipped terrifyingly loud) while the
                // incoming track waits out its delay before ramping in.
                // ADSR release: as B enters (inProgress), the wash gets out
                // of its way — wet multiplies down by the remaining entry
                // headroom, so A's tail decays under B instead of parking
                // loud over it. Series headroom: echo+reverb wet never
                // exceeds the cap; the stack can't rebuild the clip the
                // gain-staging removed.
                val freezeAt = render.reverbFreezeAtSec
                if (freezeAt != null && freezeAt.isFinite()) {
                    // No span field on Render: the plan stamps the overlap it
                    // sized into overlapSeconds, and the rides read it back.
                    val spanSec = render.overlapSeconds.toFloat().coerceAtLeast(1f)
                    val wetRamp = (progress * spanSec / HEAVY_CLASH_WET_RAMP_SEC).coerceIn(0f, 1f)
                    val release = 1f - inProgress.coerceIn(0f, 1f)
                    filters.outgoing(20000f, 20f)
                    // Spec finetune §5: B enters under a 500 Hz high-pass that
                    // relaxes over 3.5 s from its start (4.5 s into the window),
                    // so its low end never punches through the reverb tail.
                    val bElapsed = progress * spanSec - 4.5f
                    val bOpen = (bElapsed / 3.5f).coerceIn(0f, 1f)
                    filters.incoming(20000f, 500f * (1f - bOpen) + 20f * bOpen)
                    // The plan's graded echo amount, not the file constant:
                    // heavyClashPlan scales it by the vocal gate, and a const
                    // here would silently undo that grading.
                    val echoW = render.echoAmount.toFloat() * wetRamp * release
                    val verbW = (render.reverbAmount * wetRamp * release).toFloat()
                    val wetSum = echoW + verbW
                    val headroom = if (wetSum > SERIES_WET_CAP) SERIES_WET_CAP / wetSum else 1f
                    echoFilters.outgoing(echoW * headroom, render.echoBeatSeconds.toFloat())
                    echoFilters.incoming(0f, 0f)
                    // Full-plan P5: voice the plan's freeze point — past
                    // freezeAt the tail smears (feedback pinned) instead of
                    // tracking the dry. reverbFreezeAtSec is plan metadata no
                    // longer: seconds into the window, same clock as bElapsed.
                    val frozen = progress * spanSec >= freezeAt.toFloat()
                    reverbFilters.outgoing(verbW * headroom, frozen)
                    reverbFilters.incoming(0f, false)
                } else {
                    val wash = (render.echoAmount * progress).toFloat().coerceIn(0f, 1f)
                    filters.outgoing(20000f * (1f - wash) + 300f * wash, 20f)
                    filters.incoming(20000f, 20f)
                    // The dub throw behind the wash: one-bar repeats of the
                    // filtered signal, riding up with the wash. The incoming side
                    // stays dry per the blueprint (reverb 30 %→0 % is the wash's
                    // absence, not a second send).
                    echoFilters.outgoing(wash, render.echoBeatSeconds.toFloat())
                    echoFilters.incoming(0f, 0f)
                    reverbFilters.open()
                }
            }
            // Blueprint LOOP_CUT_DROP: a real booth loop-and-build. The
            // outgoing deck repeats a quantized phrase that halves
            // (4 → 2 → 1 → 1/2 beats) while a high-pass and echo rise over
            // it; the freeze + cut land as the incoming drop arrives.
            // Phases key off REMAINING beats in the actual window — an 8 s
            // window at 128 BPM holds ~17 beats, not 24, and fractions
            // authored for 24 beats never let a loop complete a repeat
            // (the Body Heat lesson: flat tail, then slam). Each halve
            // needs its own length plus the freeze left to run.
            // The loop lives in the PCM ring (LoopVampProcessor), so no
            // seek ever disturbs the media clock the fade runs on.
            TransitionStyle.LOOP_CUT_DROP -> {
                // Remaining beats, from the ARM-stamped window (0 = unknown,
                // fall back to the legacy 24-beat fractions).
                val remainingBeats = if (render.loopWindowBeats > 0) {
                    ((1f - progress) * render.loopWindowBeats).toFloat()
                } else {
                    (1f - progress) * 24f
                }
                val loopBeats = when {
                    remainingBeats > 8.5f -> 0f
                    remainingBeats > 4.5f -> 4f
                    remainingBeats > 2.5f -> 2f
                    remainingBeats > 1.5f -> 1f
                    else -> 0.5f
                }
                if (loopBeats != lastLoopBeats && render.loopBeatSeconds > 0) {
                    lastLoopBeats = loopBeats
                    loopVamps.outgoing(loopBeats, render.loopBeatSeconds.toFloat())
                }
                // Freeze over the last ~1.5 beats, wherever the window ends.
                val freeze = ((1.5f - remainingBeats) / 1.5f).coerceIn(0f, 1f)
                if (freeze > 0f) {
                    filters.outgoing(20000f * (1f - freeze) + 300f * freeze, 20f)
                    echoFilters.outgoing(0.5f * freeze, render.echoBeatSeconds.toFloat())
                } else {
                    // Build: the high-pass thins the loop toward a hiss and
                    // the echo send ramps with it — the vamp tightens instead
                    // of sitting flat at unity. Keyed off progress (a time
                    // gesture); the loop lengths above key off beats.
                    val freezeAtProgress = if (render.loopWindowBeats > 0) {
                        1f - 1.5f / render.loopWindowBeats.toFloat()
                    } else {
                        23f / 24f
                    }
                    val build = (progress / freezeAtProgress).coerceIn(0f, 1f)
                    val hp = 20f + (TransitionFilterProcessor.MAX_HIGH_PASS_HZ - 20f) * build * build
                    filters.outgoing(20000f, hp)
                    echoFilters.outgoing(0.30f * build, render.echoBeatSeconds.toFloat())
                }
                filters.incoming(20000f, 20f)
                echoFilters.incoming(0f, 0f)
            }
            // Blueprint HARD_CUT: the 0.1 s window and downbeat cue in the plan
            // are the technique — plus a 1-beat LP sweep gesture into the
            // flip (Issue 3 parity): a real DJ cut has a spectral gesture,
            // and a naked chop reads as a mistake. plan.filterSweep voices
            // the depth; the INSTANT settle + splice guard own the click.
            TransitionStyle.HARD_CUT -> {
                val sweep = render.filterSweep.coerceIn(0.0, 1.0)
                if (sweep <= 0.0) {
                    filters.open()
                } else {
                    val open = TransitionFilterProcessor.OPEN_HZ.toDouble()
                    val floor = glide(open, FILTER_FLOOR_HZ, sweep)
                    filters.outgoing(
                        glide(open, floor, progress.toDouble().pow(FILTER_SWEEP_SHAPE)).toFloat(),
                        TransitionFilterProcessor.OFF_HZ,
                    )
                    filters.incoming(
                        TransitionFilterProcessor.OPEN_HZ,
                        TransitionFilterProcessor.OFF_HZ,
                    )
                }
                echoFilters.open()
                // Full-audit F3: sever reverb here too — a prior blend's wash
                // must not ghost over the cut (begin() deliberately skips
                // echo/reverb parking).
                reverbFilters.open()
            }
            // Loop-roll extend (Issue 1): same booth vamp as the cut — the
            // phrase halves (4→2→1→½ keyed off remaining beats in the longer
            // window) while B blends in — but instead of a freeze + chop the
            // loop releases over the last 2 beats (the renderer's INSTANT
            // settle glides the volumes) with a rising HP + echo wash.
            TransitionStyle.LOOP_ROLL -> {
                val remainingBeats = if (render.loopWindowBeats > 0) {
                    ((1f - progress) * render.loopWindowBeats).toFloat()
                } else {
                    (1f - progress) * 24f
                }
                val loopBeats = when {
                    remainingBeats > 8.5f -> 0f
                    remainingBeats > 4.5f -> 4f
                    remainingBeats > 2.5f -> 2f
                    remainingBeats > 1.5f -> 1f
                    else -> 0.5f
                }
                if (loopBeats != lastLoopBeats && render.loopBeatSeconds > 0) {
                    lastLoopBeats = loopBeats
                    loopVamps.outgoing(loopBeats, render.loopBeatSeconds.toFloat())
                }
                // Release over the last ~2 beats, wherever the window ends:
                // the vamp holds (no freeze — B is already in) while the HP
                // thins it toward a hiss and the echo send ramps with it.
                val release = ((2f - remainingBeats) / 2f).coerceIn(0f, 1f)
                if (release > 0f) {
                    loopVamps.outgoing(0f, render.loopBeatSeconds.toFloat())
                    val hp = 20f + (TransitionFilterProcessor.MAX_HIGH_PASS_HZ - 20f) * release * release
                    filters.outgoing(20000f, hp)
                    echoFilters.outgoing(0.30f * release, render.echoBeatSeconds.toFloat())
                } else {
                    val freezeAtProgress = if (render.loopWindowBeats > 0) {
                        1f - 2f / render.loopWindowBeats.toFloat()
                    } else {
                        22f / 24f
                    }
                    val build = (progress / freezeAtProgress).coerceIn(0f, 1f)
                    val hp = 20f + (TransitionFilterProcessor.MAX_HIGH_PASS_HZ - 20f) * build * build
                    filters.outgoing(20000f, hp)
                    echoFilters.outgoing(0.30f * build, render.echoBeatSeconds.toFloat())
                }
                filters.incoming(20000f, 20f)
                echoFilters.incoming(0f, 0f)
            }
            // Spec v2 §9a PLAIN_DISSOLVE: filters stay open — the cut sits on
            // a silence gap or low-energy seam, so there is nothing to EQ
            // around. The reverb sends carry it: the outgoing track blooms
            // to the plan's wet over the first half, the incoming one drains
            // its entry wet over 3 s. Gains ride LINEAR (see driveFade).
            TransitionStyle.PLAIN_DISSOLVE -> {
                // Spec v2 §9a: the outgoing bass hard-cuts below 200 Hz at
                // the gap (nothing musical lives there anyway), the incoming
                // bass fades linearly over 2 s — a tilt, not a swap.
                filters.outgoing(20000f, 200f)
                val spanSec = render.overlapSeconds.toFloat().coerceAtLeast(1f)
                val bassOpen = (progress * spanSec / 2f).coerceIn(0f, 1f)
                filters.incoming(20000f, 200f * (1f - bassOpen) + 20f * bassOpen)
                val outWet = if (progress < 0.5f) {
                    (render.reverbAmount * (progress / 0.5f)).toFloat()
                } else {
                    render.reverbAmount.toFloat()
                }
                reverbFilters.outgoing(outWet, false)
                val inWet = (PLAIN_DISSOLVE_IN_WET -
                    PLAIN_DISSOLVE_IN_WET * (progress * spanSec / PLAIN_DISSOLVE_IN_DRAIN_SEC))
                    .coerceIn(0f, PLAIN_DISSOLVE_IN_WET)
                reverbFilters.incoming(inWet, false)
                echoFilters.open()
            }
        }
    }

    /**
     * v2 §7d/§11.2: advances the downbeat-emphasis cursor. The planner lays
     * the offsets on the *stretched* grid (recomputed from the adjusted beat
     * interval, never the raw grid — a >5 ms drift would fire the accent off
     * the beat it is meant to mark). Elapsed here is incoming-track time,
     * which is what the offsets are expressed in.
     */
    private fun rideHalfTimeEmphasis(elapsedSec: Double) {
        emphasisTicksLeft = 0
        val offsets = render.halfTimeEmphasis
        if (offsets.isEmpty()) {
            emphasisIndex = 0
            return
        }
        while (emphasisIndex < offsets.size && elapsedSec >= offsets[emphasisIndex]) {
            emphasisIndex++
            emphasisTicksLeft = 3
        }
    }

    /**
     * v2 §7a virtual mid-kill: a fake kill-switch for FILTER_SWEEP pairs whose
     * keys are close but not adjacent. Runs after [rideFilterSweep] and only
     * overrides inside its window — outside 0.30..0.70 the sweep stands.
     *
     * Gate (finetune §5): keyScore < 0.50 (too compatible needs nothing, too far
     * gets a real sweep) and at least 8 s of overlap (a kill needs room to
     * breathe; short sweeps stay untouched).
     */
    /**
     * Review v2.1 B1 staggered handoff: the old schedule cut the outgoing
     * mids at 0.30 while the incoming mids arrived at 0.50, leaving a ~20 %
     * null zone with neither track's midrange. Now the outgoing LP ramps
     * 1200→700 Hz across 0.25–0.45 while the incoming HP is already at
     * 500 Hz from 0.40, so the mids overlap instead of gaping; the bed
     * settles at 300 Hz from 0.80, when the outgoing track is nearly gone.
     */
    private fun rideMidKill(progress: Float) {
        if (render.style != TransitionStyle.DJ_FILTER) return
        if (render.keyScore >= 0.50 || render.overlapSeconds < 8.0) return
        val p = progress.toDouble()
        when {
            p < 0.25 -> Unit // sweep stands
            p < 0.40 -> filters.outgoing(
                glide(MID_KILL_START_HZ, MID_KILL_LP_HZ, (p - 0.25) / 0.15).toFloat(),
                TransitionFilterProcessor.OFF_HZ,
            )
            p < 0.65 -> {
                filters.outgoing(
                    MID_KILL_LP_HZ.toFloat(),
                    TransitionFilterProcessor.OFF_HZ,
                )
                filters.incoming(
                    TransitionFilterProcessor.OPEN_HZ,
                    MID_KILL_STAGGERED_HP_HZ.toFloat(),
                )
            }
            p < 0.80 -> {
                // Incoming already open; outgoing holds the cut bed.
                filters.outgoing(
                    MID_KILL_LP_HZ.toFloat(),
                    TransitionFilterProcessor.OFF_HZ,
                )
                filters.incoming(
                    TransitionFilterProcessor.OPEN_HZ,
                    TransitionFilterProcessor.OFF_HZ,
                )
            }
            else -> {
                filters.outgoing(
                    MID_KILL_BED_HZ.toFloat(),
                    TransitionFilterProcessor.OFF_HZ,
                )
                filters.incoming(
                    TransitionFilterProcessor.OPEN_HZ,
                    TransitionFilterProcessor.OFF_HZ,
                )
            }
        }
    }

    /**
     * Review v2.1 C2 proactive mid-cut: long smooth blends (overlap > 16 s)
     * put two full-range mixes on top of each other with no clash evidence to
     * trigger the reactive kills. While the handoff crosses (0.40–0.60) the
     * outgoing LP sits at 600 Hz and the incoming HP at 300 Hz — gentler
     * than the reactive kill because nothing has proven a collision. Outside
     * the window the filters open (glided by the processor, never snapped).
     *
     * Satisfaction round §3: narrower window (was 0.30–0.70 — the old one
     * was audible as processing) and gated on measured vocal overlap. A long
     * bed with no clash evidence stays open; the EQ ducks plus the late bass
     * swap already shape those.
     *
     * EQ-muddy hotfix (DJ-only): window widened 0.30–0.65 and overlap gate
     * lowered 16→12 s — 12-16 s blends were holding two full mids through
     * handoff because proactive never fired and ownership hadn't taken over
     * yet. Stock Automix never reaches here (mixset false gate above).
     *
     * Deviation noted: the review gates this on similar spectral centroids,
     * but Render carries no centroid fields; overlap length alone is the
     * gate rather than adding planner fields for it.
     */
    private fun rideProactiveMidCut(progress: Float) {
        val p = progress.toDouble()
        // P2-smart: outside its window the proactive cut holds — it never
        // opens the filter anymore. The old unconditional open() erased the
        // separation/sweep shaping gliding underneath every tick (audible
        // pumping at the window edges); the recipe owns the band plan, this
        // actor only voices its window.
        if (p < 0.30 || p > 0.65 || render.vocalOverlap <= 0.0) {
            return
        }
        filters.outgoing(
            PROACTIVE_MID_CUT_LP_HZ.toFloat(),
            TransitionFilterProcessor.OFF_HZ,
        )
        filters.incoming(
            TransitionFilterProcessor.OPEN_HZ,
            PROACTIVE_MID_CUT_HP_HZ.toFloat(),
        )
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
        // Stock upstream on normal Automix: 1.6 kHz floor. DJ Mode keeps
        // the warmer 300 Hz floor.
        val floor = glide(open, if (render.mixset) VOCAL_SEPARATION_FLOOR_HZ else STOCK_VOCAL_SEPARATION_FLOOR_HZ, amount)
        filters.outgoing(
            glide(open, floor, progress.toDouble().pow(FILTER_SWEEP_SHAPE)).toFloat(),
            TransitionFilterProcessor.OFF_HZ,
        )
        filters.incoming(
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
        // Review v2.1 C3: the 0.75 exponent spends the travel where a voice
        // actually is (2.6 kHz at a fifth in, 1.0 kHz at the midpoint) instead
        // of burning it in sub-bass — see FILTER_SWEEP_SHAPE's KDoc.
        val cutoff = glide(entry, floor, progress.toDouble().pow(FILTER_SWEEP_SHAPE))
        filters.outgoing(cutoff.toFloat(), TransitionFilterProcessor.OFF_HZ)
        // Review v2.1 A4 exception: on a key clash the entry starts at the
        // masking corner and relaxes to the normal entry over the first 25 %
        // of the overlap; otherwise the bass-only entry applies throughout.
        val clashMask = render.keyScore < 0.50
        // Stock upstream on normal Automix: flat 1.2 kHz entry, no clash
        // exception. DJ Mode keeps the masking corner below.
        val entryTop = if (render.mixset && clashMask && progress < 0.25f) {
            glide(ENTRY_CLASH_HIGH_PASS_HZ, ENTRY_HIGH_PASS_HZ, (progress / 0.25f).toDouble())
        } else if (render.mixset) {
            ENTRY_HIGH_PASS_HZ
        } else {
            STOCK_ENTRY_HIGH_PASS_HZ
        }
        filters.incoming(
            TransitionFilterProcessor.OPEN_HZ,
            entryHighPass(progress, sweep, entryTop, ENTRY_OPEN_BY),
        )
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
        val swapAt = render.bassSwapFraction.coerceIn(0.05, 0.95)
        // 0 before the swap window, 1 after it: how much of the low end has
        // changed hands.
        // Stock upstream on normal Automix: 0.10 width. DJ Mode keeps V2.
        val width = if (render.mixset) BASS_SWAP_WIDTH else STOCK_BASS_SWAP_WIDTH
        val handover = ((progress - swapAt) / width * 0.5 + 0.5).coerceIn(0.0, 1.0)
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
                BLEND_ENTRY_OPEN_BY + (BLEND_ENTRY_CLASH_OPEN_BY - BLEND_ENTRY_OPEN_BY) * clash,
            ),
        )
        filters.incoming(TransitionFilterProcessor.OPEN_HZ, entry)
        filters.outgoing(blendExitLowPass(progress, clash), bassCutoff(handover))
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
        val from = BLEND_EXIT_FROM + (BLEND_EXIT_CLASH_FROM - BLEND_EXIT_FROM) * clash
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
    // Single analyzer-driven rule: a filtering/bass style, a cued-in entry,
    // or a tempo stretch. Render and plan twins read the same helper so the
    // bar and the blend can never disagree on what counts as a mix.
    private fun isRealMixStyle(
        style: TransitionStyle,
        cueTime: Double,
        playbackRate: Double,
    ): Boolean = style == TransitionStyle.DJ_BLEND ||
        style == TransitionStyle.DJ_FILTER ||
        style == TransitionStyle.ECHO_REVERB_OUT ||
        style == TransitionStyle.LOOP_CUT_DROP ||
        style == TransitionStyle.LOOP_ROLL ||
        style == TransitionStyle.HARD_CUT ||
        style == TransitionStyle.PLAIN_DISSOLVE ||
        cueTime > 0.0 ||
        playbackRate != 1.0

    private fun isRealMix(): Boolean = smartFadeActive && isRealMixStyle(
        render.style, incomingCueTimeMs.toDouble(), incomingPlaybackRate,
    )

    /**
     * Plan-level twin of [isRealMix] for use before anything is armed: the
     * same rule (a filtering/bass style, a cued-in entry, or a stretch),
     * read off the plan instead of the render state. G2 marker gating.
     */
    private fun planIsRealMix(plan: TransitionPlan): Boolean = isRealMixStyle(
        plan.transitionStyle, plan.incomingCueTime, plan.incomingPlaybackRate,
    )

    /**
     * P2: one log line per distinct silent-guard trip, never one per tick.
     * The mark→arm→fade path drops transitions in a dozen places that used
     * to return bare, so a "greybar but no mix" report was undiagnosable
     * from logcat. Throttled by key: repeats are suppressed, changes surface.
     */
    private fun logGuardOnce(key: String, msg: String) {
        val full = "$key|$msg"
        if (full != lastGuardLog) {
            lastGuardLog = full
            TrackLog.d(TAG, msg)
        }
    }

    /** Equal-power pair: [riseGain]² + [fallGain]² = 1, so the blend never dips. */
    private fun riseGain(progress: Float): Float =
        sin(progress.coerceIn(0f, 1f) * PI.toFloat() / 2f)

    private fun fallGain(progress: Float): Float =
        cos(progress.coerceIn(0f, 1f) * PI.toFloat() / 2f)

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
         * The throw send closes over this long, stepped in tick() through
         * the existing outgoing() target — same wall-clock order as the
         * bail/mute ramps, inaudible as a move.
         */
        const val THROW_CLOSE_MS = 200L

        /**
         * The INSTANT flip settles over this long instead of stepping: the
         * endpoints are identical, only the last window changes, so hard cuts
         * keep their timing while losing the full-scale step.
         */
        const val INSTANT_SETTLE_MS = 90L

        /**
         * Spec v2 §9b: the heavy-clash wet ramps ride over this many seconds
         * of the 8 s window, then hold. Voiced so the echo-into-reverb stack
         * never runs both sends at max together (see [SERIES_WET_CAP]).
         */
        const val HEAVY_CLASH_WET_RAMP_SEC = 3.5f

        /** Spec v2 §9a: incoming reverb entry wet, draining over the fade. */
        const val PLAIN_DISSOLVE_IN_WET = 0.30f

        /**
         * Series headroom: echo + reverb wet on one deck never sum past this.
         * The two sends stack (echo into reverb), so two modest wets rebuild
         * the clip each avoids alone. 0.6 keeps the stack gain-staged.
         */
        const val SERIES_WET_CAP = 0.6f

        /** Spec v2 §9a: seconds for the incoming wet to drain to zero. */
        const val PLAIN_DISSOLVE_IN_DRAIN_SEC = 3.0f

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
         *
         * Review v2.1 B5: 6000 — a 9097 ms session-log resolution ran ~50 %
         * past the old 4000. The smart path below uses the full lead only
         * while the next analysis is still resolving (see the adaptive lead);
         * an already-measured incoming track arms on the old margin.
         */
        const val ARM_LEAD_MS = 6_000L
        /**
         * Review v2.1 B5 margin once the incoming track is measured: the
         * previous lead, kept for the case that needs no resolution at all.
         */
        const val ARM_LEAD_RESOLVED_MS = 4_000L

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
        const val BASS_SWAP_WIDTH = BASS_SWAP_WIDTH_V2
        /** Stock upstream (7039430) swap width, used on normal Automix. */
        const val STOCK_BASS_SWAP_WIDTH = 0.10

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
         * Review v2.1 A4: 220 Hz — removes only the bass floor, keeps the mids,
         * so the arriving track never reads as a hollow treble whisper. (Was
         * 1.2 kHz; the octave-band argument for it is preserved below for the
         * key-clash case, where ENTRY_CLASH_HIGH_PASS_HZ temporarily restores
         * the masking.)
         */
         const val ENTRY_HIGH_PASS_HZ = 220.0
        /** Stock upstream (7039430) entry corner, used on normal Automix. */
        const val STOCK_ENTRY_HIGH_PASS_HZ = 1200.0

        /**
         * Review v2.1 A4 exception: when the pair clashes in key
         * (keyScore < 0.50), the entry high-pass starts here and relaxes to
         * [ENTRY_HIGH_PASS_HZ] over the first 25 % of the overlap, keeping the
         * clash masking while restoring mids sooner.
         */
        const val ENTRY_CLASH_HIGH_PASS_HZ = 500.0

        /**
         * Review v2.1 C2: proactive mid-cut on long smooth blends
         * (overlap > 16 s) — outgoing LP / incoming HP while the handoff
         * crosses, so two full-range mixes never sit on each other.
         */
        const val PROACTIVE_MID_CUT_LP_HZ = 600.0
        const val PROACTIVE_MID_CUT_HP_HZ = 300.0
        const val PROACTIVE_MID_CUT_MIN_OVERLAP_SECONDS = 12.0

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
         *
         * Review v2.1 A3: 300 Hz — 1.6 kHz sits inside the vocal fundamental
         * range and gutted the outgoing track into a telephone call. Removing
         * only the bass still avoids the double-bass the separation exists
         * for, and leaves mids, snare body and warmth alone.
         */
         const val VOCAL_SEPARATION_FLOOR_HZ = 300.0
        /** Stock upstream (7039430) separation floor, used on normal Automix. */
        const val STOCK_VOCAL_SEPARATION_FLOOR_HZ = 1600.0

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

        const val IDLE_STEP_MS = 250L

        /**
         * Arming only waits on a buffer now — nothing is being converged — so
         * this is about how promptly the fade can start once the incoming track
         * is ready, not about a control loop's step size.
         */
        const val ARM_STEP_MS = 40L
        const val FADE_STEP_MS = 30L
        const val BAIL_STEP_MS = 15L
    }
}
