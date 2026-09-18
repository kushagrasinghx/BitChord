/*
 * Ported from Orchard (https://github.com/SFG5453/Orchard), merging its
 * TransitionPlanner.kt and WsolaPlanner.kt into one file.
 *
 * Copyright (C) 2026 SFG545 (original Orchard implementation)
 * Copyright (C) 2026 Kushagra Singh (BitChord adaptation)
 *
 * Orchard's original source is licensed under the GNU Affero General Public
 * License, version 3 or later. Per AGPLv3 section 13, this file is combined
 * here into BitChord -- a work licensed under the GNU General Public
 * License, version 3 or later -- and remains itself governed by the AGPLv3
 * as part of that combination.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.music.bitchord.playback.smart

import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.settings.AppSettings
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

private const val PLANNER_TAG = "BitChordTransitionPlanner"

/**
 * Turns stored analysis into a concrete transition plan for one pair of
 * tracks.
 *
 * Nothing here touches PCM; the planner decides *where* a transition happens
 * and *how* ambitious it is. [CrossfadeController] is what executes a plan: it
 * reads the timing fields ([TransitionPlan.transitionStart], [TransitionPlan.fadeSeconds]),
 * cues the incoming track to [TransitionPlan.incomingCueTime] instead of 0,
 * stretches it by [TransitionPlan.incomingPlaybackRate] to align tempo, and
 * renders [TransitionPlan.transitionStyle] as filtering across the blend â€”
 * a closing low-pass over the outgoing track for [TransitionStyle.DJ_FILTER],
 * a low-end handover at [TransitionPlan.bassSwapFraction] for
 * [TransitionStyle.DJ_BLEND]. The gain curve underneath is equal-power in every
 * case; see [com.music.bitchord.playback.TransitionFilterProcessor].
 */

/** Which crossfade behaviour the listener asked for. */
enum class CrossfadeMode { STANDARD, SMART }

/**
 * The minimal facts about a queue item the planner needs, independent of
 * Media3's `MediaItem` â€” kept separate so this file stays pure and testable
 * without constructing one.
 */
data class TransitionTrackInfo(
    val id: String,
    val durationMs: Long,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumId: String = "",
)

/**
 * Four bars. Overlaps are counted in beats because that is what the ear
 * hears; the seconds values are rails for tempi where four bars would be
 * absurd, not the primary control. Eight to sixteen beats is the range the
 * automatic-DJ literature reports for stable dance material, and less for
 * dense pop.
 */
private const val AUTO_TRANSITION_MAX_BEATS = 16.0
private const val AUTO_TRANSITION_MAX_SECONDS = 12.0
private const val AUTO_MIN_SECONDS = 4.0
private const val AUTO_FAST_TRACK_MIN_SECONDS = 6.0
private const val AUTO_FALLBACK_SECONDS = 8.0

/** Below this a track would spend too much of itself transitioning to be worth planning. */
private const val MIN_SMART_DURATION_SECONDS = 45.0

/** Below this fraction of its track mean, a blend window counts as a dip:
 * the energy-floor veto walks the anchor or fails over to the wash. */
internal const val MIX_MIN_ENERGY_FLOOR = 0.15

/**
 * Narrowest overlap the 80%-play floor may squeeze a transition down to. When
 * the anchor sits so close above the floor that even this does not fit, the
 * rail stays off and the anchor's own placement stands.
 */
private const val MIN_TRANSITION_OVERLAP_SECONDS = 4.0

/** Anything matching this is spoken or already a performance; mixing it is never wanted. */
private val BLOCKED_TEXT = Regex(
    """\b(podcast|episode|audiobook|live|concert|performance)\b""",
    RegexOption.IGNORE_CASE,
)

/**
 * Blueprint Â§5.7 per-archetype duration budgets, in beats. The old single
 * ceiling (16 beats / 12 s) would strangle the blueprint's long blends â€” a
 * 32-bar harmonic blend alone is 128 beats â€” so each archetype gets its own
 * budget and the rails below stay as the safety net.
 */
private fun maxBeatsFor(type: TransitionType): Double = when (type) {
    TransitionType.SMOOTH_CROSSFADE -> 128.0
    TransitionType.HARMONIC_BLEND -> 256.0
    TransitionType.FILTER_SWEEP -> 64.0
    TransitionType.ECHO_REVERB_OUT -> 48.0
    TransitionType.LOOP_CUT_DROP -> 24.0
    TransitionType.LOOP_ROLL -> 32.0
    TransitionType.HARD_CUT -> 1.0
    TransitionType.HALF_TIME_BLEND -> 64.0
    TransitionType.OCTAVE_BLEND -> 32.0
    TransitionType.PLAIN_DISSOLVE -> 16.0
}

/**
 * Finetune-overlap Â§Fix 1: DJ Mode per-type overlap ceilings in beats. The
 * old flat 16-beat cap (MIXSET_MAX_BEATS, deleted) strangled every blend to
 * ~7.5 s @128 BPM while the EQ tables are designed for 18â€“30 s beds.
 */
private fun djModeMaxBeats(type: TransitionType): Double = when (type) {
    TransitionType.SMOOTH_CROSSFADE -> 128.0
    TransitionType.HARMONIC_BLEND -> 128.0
    TransitionType.FILTER_SWEEP -> 64.0
    TransitionType.ECHO_REVERB_OUT -> 24.0
    TransitionType.LOOP_CUT_DROP -> 16.0
    TransitionType.LOOP_ROLL -> 24.0
    TransitionType.HARD_CUT -> 1.0
    TransitionType.HALF_TIME_BLEND -> 32.0
    TransitionType.OCTAVE_BLEND -> 32.0
    TransitionType.PLAIN_DISSOLVE -> 12.0
}

/** Hard safety net no transition may exceed, however generous its budget. */
private const val ABSOLUTE_MAX_TRANSITION_SECONDS = 90.0

/** Leave enough incoming material after a calibrated handoff to avoid landing in its outro. */
private const val MIN_INCOMING_CLEARANCE_SECONDS = 5.0

private val KEY_INDEX = mapOf(
    "C" to 0, "Câ™¯" to 1, "Dâ™­" to 1, "D" to 2, "Dâ™¯" to 3, "Eâ™­" to 3,
    "E" to 4, "F" to 5, "Fâ™¯" to 6, "Gâ™­" to 6, "G" to 7, "Gâ™¯" to 8,
    "Aâ™­" to 8, "A" to 9, "Aâ™¯" to 10, "Bâ™­" to 10, "B" to 11,
)

/** How the renderer should execute a planned transition. */
enum class TransitionStyle {
    /** A constant-power fade, unfiltered. The only style the bottom tier permits. */
    EQUAL_POWER,

    /** Album siblings played through: a near-instant handoff, not a mix. */
    GAPLESS,

    /** Beat-aligned blend with a bass swap, for matching or near-matching tempi. */
    DJ_BLEND,

    /** Filtered handoff for tempi too far apart to blend flat. */
    DJ_FILTER,

    /** Blueprint Â§5.7: the pair cannot sync, so the outgoing track decays
     * behind echo/reverb while the incoming one fades in dry. */
    ECHO_REVERB_OUT,

    /** Blueprint Â§5.7: both tracks at full energy â€” loop the outgoing tail,
     * freeze it, cut, and land the incoming track on its drop. */
    LOOP_CUT_DROP,

    /** Loop-roll extend (short-outro rescue): the outgoing tail is too short
     * to blend across, so a percussive phrase loops (4â†’2â†’1â†’Â½) while the
     * incoming track blends in, releasing on the downbeat as its drop lands.
     * Same vamp engine as LOOP_CUT_DROP, longer window, release glide. */
    LOOP_ROLL,

    /** Blueprint Â§5.7: no blend at all â€” a click-free cut exactly on a downbeat. */
    HARD_CUT,

    /** Spec v2 Â§9a: mismatch dissolve â€” a linear 2â€“4 s fade across a silence
     * gap or low-energy seam, carried by reverb rather than by beat sync.
     * Filters stay open; the gains and the reverb sends do the work. */
    PLAIN_DISSOLVE,
}

/**
 * Blueprint Â§4: the six DJ transition archetypes. The planner decides the
 * archetype ([TransitionType]); [TransitionStyle] is how the renderer voices
 * it. The two stay separate so one archetype can change rendering without
 * re-planning the pair.
 */
enum class TransitionType {
    SMOOTH_CROSSFADE,
    HARMONIC_BLEND,
    FILTER_SWEEP,
    ECHO_REVERB_OUT,
    LOOP_CUT_DROP,
    /** Loop-roll extend for short outros: loop + blend + release on the drop. */
    LOOP_ROLL,
    HARD_CUT,
    /** v2 Â§1: beat-synced blend across a harmonic tempo ratio, both decks on a shared BPM. */
    HALF_TIME_BLEND,
    /**
     * Booth octave blend: a harmonic-ratio pair (e.g. 87 â†” 174 BPM) played at
     * rate 1.0 on both decks â€” one slow bar against two fast bars, kicks
     * aligned every other beat, phrase-locked, deliberately short. The tempo
     * octave is the same grid an octave apart; stretching would destroy it.
     */
    OCTAVE_BLEND,
    /** v2 Â§9a: unsyncable pair cut at silence/a break point with a short linear dissolve. */
    PLAIN_DISSOLVE,
}

/** Blueprint Â§4 volume automation shapes. */
enum class VolumeCurve {
    /** The existing equal-power sin/cos ride. */
    S_CURVE,

    /** Fast early decay of the outgoing track; the echo tail covers the hole. */
    LOGARITHMIC,

    /** Volumes step at the cut point; the 0.1s window itself stays click-free. */
    INSTANT,

    /**
     * v2 Â§9a: straight-line dissolve for sync-less cuts. Equal-power over a
     * silence gap sums to a loudness bump in the middle; linear doesn't.
     */
    LINEAR,
}

/** Blueprint Â§4 EQ automation shapes. */
enum class EQCurve {
    EQ_SWAP,
    BASS_SWAP,
    TREBLE_SWAP,
    NONE,
}

/**
 * The planned transition for one pair of tracks, in outgoing-track timeline
 * seconds.
 *
 * A plan is produced on every tick; [shouldStart] is what says the playhead
 * has actually reached it. [markerVisible] is separate because a future UI
 * may want to draw the upcoming transition before it begins. When [blocked]
 * is true nothing should happen at all and [reason] says why.
 */
data class TransitionPlan(
    val shouldStart: Boolean = false,
    val markerVisible: Boolean = false,
    val blocked: Boolean = false,
    val reason: String = "",
    val transitionStart: Double = 0.0,
    val transitionEnd: Double = 0.0,
    val fadeSeconds: Double = 0.0,
    val transitionStyle: TransitionStyle = TransitionStyle.EQUAL_POWER,
    /** Where in the incoming track playback should be cued to when the transition opens. */
    val incomingCueTime: Double = 0.0,
    /** Where the incoming track's arrangement lands, on its own timeline. */
    val incomingHandoffTime: Double = 0.0,
    val incomingPlaybackRate: Double = 1.0,
    val handoffStartSeconds: Double = 0.0,
    val handoffDuration: Double = 0.0,
    val pickupSeconds: Double = 0.0,
    val transitionBeats: Int = 0,
    val bassSwap: Boolean = false,
    val handoffFraction: Double = HANDOFF_FRACTION,
    val bedPosition: Double = BED_POSITION,
    val bassSwapFraction: Double = 0.7,
    val filterSweep: Double = 0.0,
    /**
     * How strongly the two tracks are expected to be singing over each other
     * through this overlap, 0..1; see [vocalOverlapAmount].
     *
     * Separate from [filterSweep] because they answer to different things.
     * [filterSweep] is a property of the *style* â€” a filter ride is what an
     * unmatched pair gets instead of a beat-matched blend â€” and a blend
     * deliberately asks for none of it. This is a property of the *material*, and
     * it applies whatever the style: two tempo-matched vocals sitting on the same
     * grid is the case a blend handles worst, precisely because nothing about the
     * arrangement is going to separate them.
     *
     * Zero whenever either track lacks a vocal mask, which leaves every style
     * rendering exactly as it did before this existed.
     */
    val vocalOverlap: Double = 0.0,
    /** Blueprint Â§5.6: which archetype this plan implements. */
    val type: TransitionType = TransitionType.SMOOTH_CROSSFADE,
    /** Blueprint Â§6: the five sub-scores and weighted overall behind [type]. */
    val score: CompatibilityScore = CompatibilityScore(),
    /** Blueprint Â§5.7 ECHO_REVERB_OUT: peak echo/reverb wet 0..1 on the outgoing track. */
    val echoAmount: Double = 0.0,
    /**
     * DJ echo throw (F1): fire a beat-synced vocal echo on the outgoing tail
     * of a DJ_BLEND/DJ_FILTER instead of rendering it dry. The renderer voices
     * it through the echo send with [echoAmount] as peak wet. DJ-only, false
     * everywhere else (normal Automix renders stock dry).
     */
    val echoThrow: Boolean = false,
    /**
     * DJ brake (F2): dive the outgoing deck rate toward a stop over the last
     * quarter of the blend instead of gliding it home. DJ-only.
     */
    val brake: Boolean = false,
    /**
     * Booth backspin (DJ-only): the outgoing deck spins back over ~1 s into
     * a hard cut that lands the incoming track on its trusted drop at full
     * energy â€” the booth punctuation for a heavy lift across a proven key
     * clash. Implies [brake] + [echoThrow] (Â½-beat dub tail); the renderer
     * compresses the dive window to the spin. False everywhere else.
     */
    val backspin: Boolean = false,
    /** Blueprint Â§5.7 LOOP_CUT_DROP: how many bars of the outgoing tail loop before the freeze. */
    val loopBars: Int = 0,
    /** Blueprint Â§5.7 LOOP_CUT_DROP: where the incoming track lands, on its own timeline. */
    val dropCueTime: Double = 0.0,
    /** Blueprint Â§5.2: semitone shift of the incoming track (Â±[MAX_KEY_SHIFT_SEMITONES]), 0 = none. */
    val keyShiftSemitones: Int = 0,
    /** Blueprint Â§4 volume automation shape for this plan. */
    val volumeCurve: VolumeCurve = VolumeCurve.S_CURVE,
    /** Blueprint Â§4 EQ automation shape for this plan. */
    val eqCurve: EQCurve = EQCurve.NONE,
    /**
     * The tempi the overlap is built on, which are **not** the analyses' raw
     * BPMs: the incoming one has been folded into the outgoing one's octave.
     * Zero when the plan is not beat-matched.
     */
    val outgoingBpm: Double = 0.0,
    val incomingBpm: Double = 0.0,
    /** Why the policy landed where it did, when it declined to be more ambitious. */
    val policyReasons: List<String> = emptyList(),
    /** v2 Â§5a: harmonic tempo ratio locking the pair (1.0 = unison). */
    val matchedRatio: Double = 1.0,
    /** v2 Â§7d: outgoing deck speed for HALF_TIME (1.0 otherwise). */
    val outgoingPlaybackRate: Double = 1.0,
    /** v2 Â§9: peak reverb wet on the outgoing track (0 = dry). */
    val reverbAmount: Double = 0.0,
    /** v2 Â§9b: transition-relative second to freeze the reverb tail, null = no freeze. */
    val reverbFreezeAtSec: Double? = null,
    /** v2 Â§9b: seconds after transitionStart before the incoming track starts. */
    val incomingStartDelaySec: Double = 0.0,
    /** v2 Â§9b: seconds after transitionStart the outgoing track holds full level. */
    val outgoingHoldSec: Double = 0.0,
    /**
     * v2 Â§7d: downbeat emphasis offsets in transition-elapsed seconds, on the
     * ADJUSTED (stretched) grid â€” the executor pulses the low-pass there.
     * Empty when no grid survives the stretch.
     */
    val halfTimeEmphasis: List<Double> = emptyList(),
    /**
     * v2 Â§7a/T6: overlap length in seconds, as the plan sized it. The render
     * rides read it back (mid-kill gate, reverb envelope windows) because
     * Render carries no span of its own.
     */
    val overlapSeconds: Double = 0.0,
    /**
     * Spec finetune Â§7: the incoming deck's stretch in a HALF_TIME blend
     * (rateB from [halfTimeRates], 1.0 = unison). Carried for tests and logs:
     * the executor already voices it via incomingPlaybackRate.
     */
    val halfTimeStretch: Double = 1.0,
    /**
     * Spec finetune Â§4.1: true when this plan is a manual-fade fallback, not
     * a matrix decision â€” the per-type ceilings never apply to it. Tests use
     * it to exempt the standard path from the ceiling map.
     */
    val standardTransitionUsed: Boolean = false,
    /**
     * Full-audit P1 M3: set by the vocal choke ([applyMixsetFireFloor]) when
     * it flips S_CURVEâ†’LOGARITHMIC on a vocal-heavy blend. The EQ tables were
     * timed against the S-curve's slow middle; under LOG the gain drops
     * before the mid-duck arrives unless the duck-voiced key set is selected.
     * The renderer ORs this into duckAMids/delayBMids.
     */
    val forceDuckKeys: Boolean = false,
    /**
     * Full-audit P1 M4: echo repeat period in beats (0.5 = half-beat dub,
     * 1.0 = one-bar repeats), null = the renderer's default rule. heavyClash
     * and echoOut share a style but not a period; deriving it from
     * outgoingBpm alone rendered both as half-beat dubs.
     */
    val echoPeriodBeats: Double? = null,
    /**
     * Click-fix phase alignment: B cue offset from its nearest downbeat in
     * seconds (0 = on-grid). Stamped by the cut planners so the flip lands
     * phase-aligned; the 1-beat INSTANT closure absorbs residual drift on
     * Â±2% pairs. Carried for logs/tests; the renderer needs no DSP nudge
     * (a flip-time rate step would re-prepare the pipeline at full volume).
     */
    val phaseOffsetSec: Double = 0.0,
) {
    /** Convenience for the engine, which schedules in milliseconds. */
    val fadeMs: Long get() = (fadeSeconds * 1000).roundToLong()
}

private fun blocked(reason: String, transitionStart: Double = 0.0, transitionEnd: Double = 0.0) =
    TransitionPlan(
        blocked = true,
        reason = reason,
        transitionStart = transitionStart,
        transitionEnd = transitionEnd,
    )

/**
 * Blueprint Â§5.6 decision matrix, verbatim order: loop the double-high pair
 * with a drop ahead, echo out the unsyncable one, blend the harmonic one,
 * smooth the clean one, filter the clashing one, cut the rest.
 *
 * v2 Â§5c: HALF_TIME pairs arm first (they beat-sync by construction), and the
 * matrix runs for BEATMATCHED + HALF_TIME â€” PLAIN is dissolved upstream.
 * Spec finetune Â§7.1: DJ_ASSISTED runs a LIMITED matrix â€” no stretch may run,
 * so never SMOOTH or HARMONIC: echo out the unsyncable, filter-mask the
 * phase drift when the key agrees, cut the rest. [highEnergyB] is read at
 * the incoming buildup start, not at the proxy entry.
 */
fun selectTransitionType(
    score: CompatibilityScore,
    tier: TransitionTier,
    highEnergyA: Boolean,
    highEnergyB: Boolean,
    hasDropInB: Boolean,
    introQuality: Double = 0.0,
    outroQuality: Double = 0.0,
    trajectory: EnergyTrajectory = EnergyTrajectory.FLAT,
    mixset: Boolean = false,
): TransitionType {
    if (mixset && highEnergyA && highEnergyB && !hasDropInB) {
        return if (score.bpm < 0.70) TransitionType.ECHO_REVERB_OUT else TransitionType.HARD_CUT
    }
    fun washDowngrade(): TransitionType =
        if (introQuality < 0 || outroQuality < 0 ||
            trajectory == EnergyTrajectory.A_UP_B_UP
        ) {
            TransitionType.ECHO_REVERB_OUT
        } else {
            TransitionType.FILTER_SWEEP
        }
    if (tier == TransitionTier.DJ_ASSISTED) {
        return when {
            score.bpm < 0.60 -> TransitionType.ECHO_REVERB_OUT
            score.key >= 0.70 -> TransitionType.FILTER_SWEEP
            else -> TransitionType.HARD_CUT
        }
    }
    if (score.bpm >= 0.70 && score.key >= 0.85) return TransitionType.HARMONIC_BLEND
    if (score.bpm >= 0.70 && score.key >= 0.70) {
        return if (score.vocal >= 0.60) TransitionType.SMOOTH_CROSSFADE else washDowngrade()
    }
    return when {
        tier == TransitionTier.HALF_TIME -> TransitionType.FILTER_SWEEP
        highEnergyA && highEnergyB && hasDropInB -> TransitionType.LOOP_CUT_DROP
        score.bpm < 0.70 && score.key >= 0.70 -> washDowngrade()
        score.bpm < 0.70 -> TransitionType.ECHO_REVERB_OUT
        score.key < 0.70 -> TransitionType.FILTER_SWEEP
        else -> TransitionType.FILTER_SWEEP
    }
}

/**
 * v2 Â§7d: shared tempo and per-deck speeds for a HALF_TIME pair. The shared
 * grid is the GEOMETRIC mean (Review v2.1 B7) â€” both decks split the log
 * distance, each stretching by âˆšratio, instead of pegging at the slower
 * tempo and making one deck do all the work. [matchedRatio] is oriented
 * outgoingâ†’incoming (see [matchHarmonicRatio]), so
 * `bpmAÂ·âˆšratio == âˆš(bpmAÂ·bpmB)` up to stretch deviation; it defaults to 1.0
 * (no half-time relation) which reduces to the slower-side choice.
 * Returns (sharedBpm, rateA, rateB). Pure â€” tested directly.
 *
 * Deprecated: no live caller after HALF_TIME_BLEND planner deletion.
 * Kept for compat/tests; new code must not stretch decks.
 */
@Deprecated("Dead HALF path removed; harmonic pairs use filter at rate 1.0")
fun halfTimeRates(bpmA: Double, bpmB: Double, matchedRatio: Double = 1.0): Triple<Double, Double, Double> {
    if (bpmA <= 0 || bpmB <= 0) return Triple(0.0, 1.0, 1.0)
    val shared = bpmA * sqrt(matchedRatio.coerceAtLeast(1e-9))
    if (!(shared > 0)) return Triple(0.0, 1.0, 1.0)
    return Triple(shared, shared / bpmA, shared / bpmB)
}

/**
 * Spec v2 Â§9a: the silence-gap cutter, pure and unit-testable. Scans
 * [scanFrom]..[contentEnd] for (1) a silence gap (RMS below
 * [SILENCE_RMS_THRESHOLD] for at least [SILENCE_MIN_DURATION_SECONDS]) â€”
 * returns its start with a 2 s dissolve; (2) a structure-map BREAK label â€”
 * its start, 2 s; (3) the minimum-energy 4-bar window â€” its start, 4 s;
 * (4) otherwise contentEnd âˆ’ 8 s with a 4 s dissolve.
 *
 * No grid snapping: a dissolve lands where the music stops, not where the
 * grid says a bar should start.
 *
 * @return the cut point and the dissolve duration, both in seconds.
 */
fun findPlainCutPoint(analysis: TrackAnalysis, scanFrom: Double, contentEnd: Double): Pair<Double, Double> {
    val curve = analysis.energyCurve.filter { it.time.isFinite() && it.energy.isFinite() && it.energy >= 0 }
    if (curve.isEmpty() || contentEnd <= scanFrom) {
        return (contentEnd - 8.0) to 4.0
    }
    var runStart: Double? = null
    for (point in curve) {
        if (point.time < scanFrom || point.time > contentEnd) continue
        if (point.energy < SILENCE_RMS_THRESHOLD) {
            if (runStart == null) runStart = point.time
            if (point.time - runStart >= SILENCE_MIN_DURATION_SECONDS) {
                return runStart to 2.0
            }
        } else {
            runStart = null
        }
    }
    analysis.plainCutBreathSec
        ?.takeIf { it.isFinite() && it in scanFrom..contentEnd }
        ?.let { return it to 2.0 }
    analysis.structureMap
        .filter { it.type == StructureSectionType.BREAK && it.start.isFinite() }
        .map { it.start }
        .filter { it in scanFrom..contentEnd }
        .minOrNull()
        ?.let { return it to 2.0 }
    val beatSeconds = analysis.beatInterval.orZero()
        .takeIf { it > 0 }
        ?: if (analysis.bpm.orZero() > 0) 60 / analysis.bpm else 0.5
    val window = beatSeconds * 16
    var bestStart: Double? = null
    var bestMean = Double.POSITIVE_INFINITY
    var t = scanFrom
    while (t + window <= contentEnd) {
        val inWindow = curve.filter { it.time >= t && it.time < t + window }
        if (inWindow.isNotEmpty()) {
            val mean = inWindow.sumOf { it.energy } / inWindow.size
            if (mean < bestMean) {
                bestMean = mean
                bestStart = t
            }
        }
        t += window / 2
    }
    bestStart?.let { return it to 4.0 }
    return (contentEnd - 8.0) to 4.0
}

/**
 * Spec v2 Â§9a PLAIN_DISSOLVE (plan side; the LINEAR gains and reverb rides
 * voice in T5/C6). For pairs the evidence cannot sync: cut at the gap,
 * not on the grid.
 */
internal fun plainDissolvePlan(
    analysis: TrackAnalysis,
    nextAnalysis: TrackAnalysis,
    length: Double,
    nextLength: Double,
    playbackTime: Double,
    mixset: Boolean,
    reasons: List<String>,
    score: CompatibilityScore = CompatibilityScore(),
    candidateShift: Int? = null,
    scanFromOverride: Double? = null,
): TransitionPlan {
    val contentEnd = analysis.contentEndTime.orZero().takeIf { it > 0 } ?: length
    val audibleStart = audibleStartOf(analysis)
    val baseScanFrom = max(audibleStart + 0.5 * length, contentEnd - 45.0)
    val scanFrom = max(baseScanFrom, scanFromOverride ?: Double.NEGATIVE_INFINITY)
    var (cutSec, dissolveSec) = findPlainCutPoint(analysis, scanFrom, contentEnd)
    if (mixset && cutSec < playbackTime + MIN_GUARANTEED_BLEND_SECONDS) {
        val aheadScan = max(scanFrom, playbackTime + MIN_GUARANTEED_BLEND_SECONDS)
        val (fwdCut, fwdDissolve) = findPlainCutPoint(analysis, aheadScan, contentEnd)
        if (fwdCut - fwdDissolve < aheadScan - 0.01) {
            return blocked("dissolve-no-room", max(0.0, cutSec - dissolveSec), cutSec)
        }
        cutSec = fwdCut
        dissolveSec = fwdDissolve
    }
    val transitionStart = max(0.0, cutSec - dissolveSec)
    val entry = if (mixset) {
        refinedStartPoint(nextAnalysis, mixsetEntryPoint(nextAnalysis) ?: incomingAudibleStart(nextAnalysis))
    } else {
        incomingAudibleStart(nextAnalysis)
    }
    val keyShift = if (mixset && analysis.key.isNotBlank() && nextAnalysis.key.isNotBlank() &&
        analysis.keyConfidence.orZero() >= MIN_KEY_CONFIDENCE_FOR_SHIFT &&
        nextAnalysis.keyConfidence.orZero() >= MIN_KEY_CONFIDENCE_FOR_SHIFT &&
        !pitchVetoesShift(nextAnalysis.vocalPitchMedianHz, nextAnalysis.key)
    ) {
        candidateShift ?: semitonesToShift(analysis.key, nextAnalysis.key)
    } else {
        0
    }
    val started = playbackTime >= transitionStart
    return TransitionPlan(
        shouldStart = started,
        markerVisible = true,
        transitionStart = transitionStart,
        transitionEnd = cutSec,
        fadeSeconds = cutSec - transitionStart,
        transitionStyle = TransitionStyle.PLAIN_DISSOLVE,
        incomingCueTime = entry,
        incomingHandoffTime = entry,
        handoffStartSeconds = transitionStart,
        handoffDuration = dissolveSec,
        type = TransitionType.PLAIN_DISSOLVE,
        score = score,
        overlapSeconds = cutSec - transitionStart,
        reverbAmount = if (mixset) PLAIN_DISSOLVE_REVERB_WET else 0.0,
        keyShiftSemitones = keyShift,
        volumeCurve = if (mixset) VolumeCurve.LINEAR else VolumeCurve.S_CURVE,
        policyReasons = reasons,
        reason = if (started) "smart-plain-dissolve" else "before-plain-dissolve",
    )
}


/**
 * v2 Â§9b heavy-clash forced echo-out (plan side; the envelope voices in T5).
 * A holds full level 3 s, fades 3â€“6 s; B starts at +4 s and ramps 4 s; reverb
 * rises to 0.80 with a freeze at +3 s. 8 s window ending at the anchor: the
 * last 2 s of A's tail ring out as echo/reverb rather than content.
 */
private fun heavyClashPlan(
    analysis: TrackAnalysis,
    nextAnalysis: TrackAnalysis,
    length: Double,
    nextLength: Double,
    playbackTime: Double,
    mixAnchor: Double,
    entry: Double,
    score: CompatibilityScore,
    reasons: List<String>,
    mixset: Boolean,
): TransitionPlan {
    val fadeSec = 8.0
    val transitionStart = max(0.0, mixAnchor - fadeSec)
    val started = playbackTime >= transitionStart
    val clash = plannedVocalOverlap(analysis, nextAnalysis, transitionStart, mixAnchor, entry, 1.0)
        .coerceIn(0.0, 1.0)
    val outKnown = vocalActivityBetween(analysis, transitionStart, mixAnchor) != null
    val inKnown = vocalActivityBetween(nextAnalysis, entry, entry + fadeSec) != null
    val clashGate = if (!outKnown || !inKnown) 0.35 else (1.0 - clash).coerceIn(0.35, 1.0)
    return TransitionPlan(
        shouldStart = started,
        markerVisible = true,
        transitionStart = transitionStart,
        transitionEnd = mixAnchor,
        fadeSeconds = mixAnchor - transitionStart,
        transitionStyle = TransitionStyle.ECHO_REVERB_OUT,
        incomingCueTime = entry,
        incomingHandoffTime = entry,
        handoffStartSeconds = transitionStart + 4.5,
        handoffDuration = 4.0,
        type = TransitionType.ECHO_REVERB_OUT,
        score = score,
        overlapSeconds = fadeSec,
        echoAmount = if (mixset) HEAVY_CLASH_ECHO_AMOUNT * clashGate else 0.0,
        reverbAmount = if (mixset) HEAVY_CLASH_REVERB_WET * clashGate else 0.0,
        echoPeriodBeats = 0.5,
        reverbFreezeAtSec = HEAVY_CLASH_FREEZE_OFFSET_SEC,
        incomingStartDelaySec = 4.5,
        outgoingHoldSec = 3.0,
        volumeCurve = VolumeCurve.LOGARITHMIC,
        brake = brakeForSlowdown(mixset, analysis, nextAnalysis),
        policyReasons = reasons,
        reason = if (started) "smart-heavy-clash-echo" else "before-heavy-clash",
    )
}

/**
 * Unified CUT: one click-free INSTANT handoff on the downbeat.
 * loopBars=0 = honest HARD_CUT; loopBars=4 = LOOP_CUT_DROP vamp+freeze
 * (incoming arrives on its drop). Single renderer path keyed by loopBars.
 */
private fun cutPlan(
    analysis: TrackAnalysis,
    nextAnalysis: TrackAnalysis,
    length: Double,
    nextLength: Double,
    playbackTime: Double,
    mixAnchor: Double,
    score: CompatibilityScore,
    policyReasons: List<String>,
    mixset: Boolean = false,
    loopBars: Int = 0,
    dropTime: Double? = null,
): TransitionPlan {
    if (loopBars > 0 && dropTime != null) {
        return loopCutPlan(
            analysis, nextAnalysis, length, nextLength,
            playbackTime, mixAnchor, dropTime, score, policyReasons, mixset,
        )
    }
    return hardCutPlan(
        analysis, nextAnalysis, length, nextLength,
        playbackTime, mixAnchor, score, policyReasons, mixset,
    )
}

/**
 * Blueprint Â§5.7 HARD_CUT: no blend â€” a click-free 0.1 s handoff exactly on
 * the outgoing track's nearest downbeat. The renderer voices this with open
 * filters and a stepped volume curve.
 */
private fun hardCutPlan(
    analysis: TrackAnalysis,
    nextAnalysis: TrackAnalysis,
    length: Double,
    nextLength: Double,
    playbackTime: Double,
    mixAnchor: Double,
    score: CompatibilityScore,
    policyReasons: List<String>,
    mixset: Boolean = false,
): TransitionPlan {
    val beatSeconds = analysis.beatInterval.orZero().takeIf { it > 0 }
        ?: if (analysis.bpm.orZero() > 0) 60 / analysis.bpm else 0.5
    val playFloorSeconds = if (mixset) 0.0 else 0.8 * length
    val cutAt = (nearestTimedValue(analysis.downbeats, mixAnchor, tolerance = beatSeconds * 2)
        ?.coerceIn(0.0, length) ?: mixAnchor.coerceIn(0.0, length))
        .coerceAtLeast(min(playFloorSeconds, length))
    val cue = if (mixset) {
        mixsetEntryCue(nextAnalysis, nextLength)
    } else {
        vocalAwareCutCue(nextAnalysis, nextLength)
    }
    val started = playbackTime >= cutAt
    val cueDown = nearestTimedValue(nextAnalysis.downbeats, cue, tolerance = beatSeconds)
    return TransitionPlan(
        shouldStart = started,
        markerVisible = true,
        transitionStart = cutAt,
        transitionEnd = cutAt + 0.1,
        fadeSeconds = 0.1,
        transitionStyle = TransitionStyle.HARD_CUT,
        type = TransitionType.HARD_CUT,
        score = score,
        incomingCueTime = cue,
        incomingHandoffTime = cue,
        incomingPlaybackRate = 1.0,
        volumeCurve = VolumeCurve.INSTANT,
        eqCurve = EQCurve.NONE,
        filterSweep = 1.0,
        phaseOffsetSec = if (cueDown != null) cue - cueDown else 0.0,
        policyReasons = policyReasons,
        reason = if (started) "smart-hard-cut" else "before-hard-cut-window",
    )
}

/**
 * Booth backspin (DJ-only): the outgoing deck plays to the phrase end, spins
 * back over its last [BACKSPIN_SPIN_SECONDS], and hard-cuts onto the incoming
 * track's trusted drop at full energy. Typed LOOP_CUT_DROP with loopBars = 0:
 * an honest cut onto a drop with no vamp â€” the spin IS the transition â€” so
 * the guaranteed-blend floor (which exempts LOOP_CUT_DROP, not HARD_CUT)
 * lets the ~1 s pre-roll stand. Rendered with the HARD_CUT style (open
 * filters, stepped volume, 1-beat LP sweep into the flip) and no vamp (the
 * vamp keys off loopBars). The renderer compresses the brake dive to the
 * spin window, rings a Â½-beat dub tail on the outgoing channel while it
 * spins, and flips on the downbeat.
 */
private fun backspinPlan(
    analysis: TrackAnalysis,
    nextAnalysis: TrackAnalysis,
    length: Double,
    nextLength: Double,
    playbackTime: Double,
    mixAnchor: Double,
    dropTime: Double,
    score: CompatibilityScore,
    policyReasons: List<String>,
    mixset: Boolean = false,
): TransitionPlan {
    val beatSeconds = analysis.beatInterval.orZero().takeIf { it > 0 }
        ?: if (analysis.bpm.orZero() > 0) 60 / analysis.bpm else 0.5
    val cutAt = mixAnchor.coerceIn(0.0, length)
    val spinStart = (
        nearestTimedValue(analysis.downbeats, cutAt - BACKSPIN_SPIN_SECONDS, tolerance = beatSeconds * 2)
            ?: (cutAt - BACKSPIN_SPIN_SECONDS)
        ).coerceIn(0.0, cutAt)
    val maxCue = nextLength - MIN_INCOMING_CLEARANCE_SECONDS
    val cue = if (nextLength > 0 && maxCue >= 0) dropTime.coerceIn(0.0, maxCue) else dropTime
    val started = playbackTime >= spinStart
    val cueDown = nearestTimedValue(nextAnalysis.downbeats, cue, tolerance = beatSeconds)
    val fadeSeconds = (cutAt + 0.1 - spinStart).coerceAtLeast(0.1)
    return TransitionPlan(
        shouldStart = started,
        markerVisible = true,
        transitionStart = spinStart,
        transitionEnd = cutAt + 0.1,
        fadeSeconds = fadeSeconds,
        transitionStyle = TransitionStyle.HARD_CUT,
        type = TransitionType.LOOP_CUT_DROP,
        score = score,
        backspin = true,
        brake = true,
        echoThrow = true,
        echoAmount = ECHO_THROW_WET,
        echoPeriodBeats = BACKSPIN_ECHO_PERIOD_BEATS,
        loopBars = 0,
        dropCueTime = cue,
        incomingCueTime = cue,
        incomingHandoffTime = cue,
        incomingPlaybackRate = 1.0,
        transitionBeats = ((cutAt - spinStart) / beatSeconds).roundToInt().coerceAtLeast(1),
        volumeCurve = VolumeCurve.INSTANT,
        eqCurve = EQCurve.NONE,
        filterSweep = 1.0,
        overlapSeconds = fadeSeconds,
        phaseOffsetSec = if (cueDown != null) cue - cueDown else 0.0,
        keyShiftSemitones = 0,
        policyReasons = policyReasons,
        reason = if (started) "smart-backspin-drop" else "before-backspin-window",
    )
}

/**
 * Unified WASH: single entry for unsyncable pairs (merged ECHO long wash +
 * DISSOLVE short silence cut, per audit vote). longWash=true = decaying
 * echo wash on the grid; false = short silence-gap dissolve. Length is the
 * only axis; recipe stays WASH_OUT either way so the bar promises one thing.
 */
private fun washPlan(
    analysis: TrackAnalysis,
    nextAnalysis: TrackAnalysis,
    length: Double,
    nextLength: Double,
    playbackTime: Double,
    mixAnchor: Double,
    score: CompatibilityScore,
    policyReasons: List<String>,
    mixset: Boolean = false,
    longWash: Boolean = true,
): TransitionPlan {
    if (!longWash) {
        return plainDissolvePlan(
            analysis, nextAnalysis, length, nextLength,
            playbackTime, mixset, policyReasons,
        )
    }
    return echoOutPlan(
        analysis, nextAnalysis, length, nextLength,
        playbackTime, mixAnchor, score, policyReasons, mixset,
    )
}

/**
 * Blueprint Â§5.7 ECHO_REVERB_OUT: an 8â€“12 bar decay on the outgoing grid
 * while the incoming track fades in running natural â€” no time-stretch, the
 * tempi are too far apart to sync. Wet scales with the tempo distance.
 */
private fun echoOutPlan(
    analysis: TrackAnalysis,
    nextAnalysis: TrackAnalysis,
    length: Double,
    nextLength: Double,
    playbackTime: Double,
    mixAnchor: Double,
    score: CompatibilityScore,
    policyReasons: List<String>,
    mixset: Boolean = false,
): TransitionPlan {
    val bpmOut = analysis.bpm.orZero()
    val beatSeconds = if (bpmOut > 0) 60 / bpmOut else 0.5
    val typeCeiling = if (mixset) djModeCeilingFor(TransitionType.ECHO_REVERB_OUT)
        else ceilingFor(TransitionType.ECHO_REVERB_OUT)
    val fade = min(32.0 * beatSeconds, min(mixAnchor * 0.6, ABSOLUTE_MAX_TRANSITION_SECONDS))
        .coerceAtMost(typeCeiling)
        .coerceAtLeast(1.0)
    val targetStart = max(0.0, mixAnchor - fade)
    val playFloorSeconds = if (mixset) 0.0 else 0.8 * length
    val transitionStart = if (!mixset && playFloorSeconds < mixAnchor - 1.0) {
        max(
            alignedTransitionStart(
                analysis, targetStart, mixAnchor - 0.05,
                preferEarlier = true, minimum = targetStart,
            ),
            playFloorSeconds,
        )
    } else {
        alignedTransitionStart(
            analysis, targetStart, mixAnchor - 0.05,
            preferEarlier = true, minimum = targetStart,
            mixset = mixset,
        )
    }
    val cue = if (mixset) {
        mixsetEntryCue(nextAnalysis, nextLength)
    } else {
        vocalAwareCutCue(nextAnalysis, nextLength)
    }
    val maxHandoff = nextLength - MIN_INCOMING_CLEARANCE_SECONDS
    val handoff = if (nextLength > 0 && maxHandoff >= cue) min(cue, maxHandoff) else cue
    val started = playbackTime >= transitionStart
    val echoAmount = ((0.50 - score.bpm) / 0.50).coerceIn(0.3, 0.50)
    return TransitionPlan(
        shouldStart = started,
        markerVisible = true,
        transitionStart = transitionStart,
        transitionEnd = mixAnchor,
        fadeSeconds = (mixAnchor - transitionStart).coerceAtLeast(0.1),
        transitionStyle = TransitionStyle.ECHO_REVERB_OUT,
        type = TransitionType.ECHO_REVERB_OUT,
        score = score,
        echoAmount = echoAmount,
        reverbAmount = 0.0,
        echoPeriodBeats = 1.0,
        brake = brakeForSlowdown(mixset, analysis, nextAnalysis),
        incomingCueTime = cue,
        incomingHandoffTime = handoff,
        incomingPlaybackRate = 1.0,
        transitionBeats = 32,
        bassSwap = false,
        filterSweep = 0.0,
        volumeCurve = VolumeCurve.LOGARITHMIC,
        eqCurve = EQCurve.EQ_SWAP,
        vocalOverlap = plannedVocalOverlap(analysis, nextAnalysis, transitionStart, mixAnchor, cue, 1.0),
        outgoingBpm = bpmOut,
        incomingBpm = 0.0,
        policyReasons = policyReasons,
        reason = if (started) "smart-echo-out" else "before-echo-out-window",
    )
}

/**
 * FIX 3: HALF_TIME harmonic pairs route to FILTER_SWEEP instead of echo.
 * Rate stays 1.0 (no chipmunk); frequency handoff carries the transition.
 * Uses the existing FILTER_SWEEP EQ keyframe table from EqSchedule.kt.
 * [keyShiftSemitones] is pre-computed via [semitonesToShift] (clamped Â±2).
 */
private fun filterSweepPlan(
    analysis: TrackAnalysis,
    nextAnalysis: TrackAnalysis,
    length: Double,
    nextLength: Double,
    playbackTime: Double,
    mixAnchor: Double,
    proxyEntry: Double,
    score: CompatibilityScore,
    policyReasons: List<String>,
    mixset: Boolean = false,
    keyShiftSemitones: Int = 0,
    keyClash: Boolean = false,
): TransitionPlan {
    val beatSec = 60.0 / analysis.bpm.coerceAtLeast(1.0)
    val fade = min(32.0 * 60.0 / analysis.bpm.coerceAtLeast(1.0), mixAnchor * 0.6)
        .coerceAtMost(
            if (mixset) djModeCeilingFor(TransitionType.FILTER_SWEEP)
            else ceilingFor(TransitionType.FILTER_SWEEP)
        )
        .coerceAtMost(if (mixset && keyClash) 8.0 * beatSec else Double.POSITIVE_INFINITY)
        .coerceAtLeast(1.0)
    val targetStart = max(0.0, mixAnchor - fade)
    val transitionStart = alignedTransitionStart(
        analysis, targetStart, mixAnchor - 0.05,
        preferEarlier = true, minimum = targetStart,
        mixset = mixset,
    )
    val transitionBeats = ((mixAnchor - transitionStart) / (60.0 / analysis.bpm.coerceAtLeast(1.0)))
        .roundToInt().coerceIn(1, 32)
    val fadeSeconds = mixAnchor - transitionStart
    val started = playbackTime >= transitionStart
    return applyMixsetFireFloor(
        TransitionPlan(
            shouldStart = started,
            markerVisible = true,
            transitionStart = transitionStart,
            transitionEnd = mixAnchor,       // C3 FIX: was mixEnd (undefined in scope)
            fadeSeconds = fadeSeconds.coerceAtLeast(0.1),
            handoffStartSeconds = 0.0,
            handoffDuration = fadeSeconds,
            incomingCueTime = proxyEntry,
            incomingHandoffTime = proxyEntry,
            incomingPlaybackRate = 1.0,
            pickupSeconds = 0.0,
            transitionBeats = transitionBeats,
            bassSwap = true,
            transitionStyle = TransitionStyle.DJ_FILTER,
            type = TransitionType.FILTER_SWEEP,
            score = score,
            keyShiftSemitones = keyShiftSemitones,
            volumeCurve = VolumeCurve.LINEAR,
            eqCurve = EQCurve.EQ_SWAP,
            filterSweep = FILTER_SWEEP,  // Double const = 1.0; field is Double (M3 reverted)
            vocalOverlap = plannedVocalOverlap(
                analysis = analysis, nextAnalysis = nextAnalysis,
                transitionStart = transitionStart, transitionEnd = mixAnchor,
                incomingCueTime = proxyEntry, incomingPlaybackRate = 1.0,
            ),
            policyReasons = policyReasons,
            reason = if (started) "half-time-filter" else "before-half-time-filter",
            reverbAmount = 0.0, // Stock: dry. (DJ path was already dry here.)
        ),
        length, mixset,
    )
}

/**
 * Booth octave blend (DJ-only): a harmonic-ratio pair (e.g. 87 â†” 174 BPM)
 * beatmatched at rate 1.0 on both decks. The tempo octave is the same grid
 * an octave apart â€” one slow bar against two fast bars, kicks aligned every
 * other beat â€” so stretching would destroy the lock instead of creating it.
 * Phase is chosen, not assumed: the incoming cue is the downbeat minimizing
 * mean grid error over the window. Deliberately short (â‰¤8 slow bars): a
 * half-time switch wants a faster cut, and a Â±1% octave drift flams by the
 * window end. Null when the lock cannot be proven â€” the caller falls back
 * to the filter wash.
 */
private fun octaveBlendPlan(
    analysis: TrackAnalysis,
    nextAnalysis: TrackAnalysis,
    length: Double,
    nextLength: Double,
    playbackTime: Double,
    mixAnchor: Double,
    proxyEntry: Double,
    score: CompatibilityScore,
    policyReasons: List<String>,
    keyShiftSemitones: Int = 0,
    mixset: Boolean = false,
): TransitionPlan? {
    if (!mixset) return null
    val bpmA = analysis.bpm.orZero()
    val bpmB = nextAnalysis.bpm.orZero()
    if (bpmA <= 0 || bpmB <= 0) return null
    if (analysis.downbeats.isEmpty() || nextAnalysis.downbeats.isEmpty()) return null
    val ratio = listOf(2.0, 0.5).minByOrNull { abs(bpmA * it - bpmB) / bpmB } ?: return null
    if (abs(bpmA * ratio - bpmB) / bpmB > 0.01) return null
    if (keyScore(analysis.key, nextAnalysis.key) < 0.45 &&
        trustedKey(analysis).isNotBlank() && trustedKey(nextAnalysis).isNotBlank()
    ) return null
    val beatA = analysis.beatInterval.orZero().takeIf { it > 0 } ?: 60.0 / bpmA
    val beatB = nextAnalysis.beatInterval.orZero().takeIf { it > 0 } ?: 60.0 / bpmB
    val slowBeat = max(beatA, beatB)
    val fastBeat = min(beatA, beatB)
    val beatsPerSlow = (slowBeat / fastBeat).roundToInt().coerceAtLeast(1)
    val fade = minOf(16 * slowBeat, mixAnchor * 0.6, 12.0)
    if (fade < MIN_TRANSITION_OVERLAP_SECONDS) return null
    val targetStart = max(0.0, mixAnchor - fade)
    val startA = alignedTransitionStart(
        analysis, targetStart, mixAnchor - 0.05,
        preferEarlier = true, minimum = targetStart,
        mixset = true,
    )
    val slowInFast = beatsPerSlow * fastBeat
    val cueCandidates = nextAnalysis.downbeats.filter {
        it.isFinite() && it >= proxyEntry - 2 * slowBeat && it <= proxyEntry + 2 * slowBeat
    }
    if (cueCandidates.isEmpty()) return null
    val windowBeats = 16
    val cueB = cueCandidates.minByOrNull { c ->
        (0..windowBeats).sumOf { n -> abs((startA + n * slowBeat) - (c + n * slowInFast)) } / (windowBeats + 1)
    } ?: return null
    val fadeSeconds = (mixAnchor - startA).coerceAtLeast(0.1)
    val started = playbackTime >= startA
    return applyMixsetFireFloor(
        TransitionPlan(
            shouldStart = started,
            markerVisible = true,
            transitionStart = startA,
            transitionEnd = mixAnchor,
            fadeSeconds = fadeSeconds,
            handoffStartSeconds = 0.0,
            handoffDuration = fadeSeconds,
            incomingCueTime = cueB,
            incomingHandoffTime = cueB,
            incomingPlaybackRate = 1.0,
            outgoingPlaybackRate = 1.0,
            transitionBeats = (fadeSeconds / slowBeat).roundToInt(),
            bassSwap = true,
            transitionStyle = TransitionStyle.DJ_BLEND,
            type = TransitionType.OCTAVE_BLEND,
            score = score,
            keyShiftSemitones = keyShiftSemitones,
            volumeCurve = VolumeCurve.S_CURVE,
            eqCurve = EQCurve.BASS_SWAP,
            filterSweep = 0.0,
            vocalOverlap = plannedVocalOverlap(
                analysis = analysis, nextAnalysis = nextAnalysis,
                transitionStart = startA, transitionEnd = mixAnchor,
                incomingCueTime = cueB, incomingPlaybackRate = 1.0,
            ),
            matchedRatio = ratio,
            policyReasons = policyReasons,
            reason = if (started) "octave-blend" else "before-octave-blend",
        ),
        length, mixset,
    )
}

/**
 * Blueprint Â§5.7 LOOP_CUT_DROP: the outgoing track loops its last 4 bars,
 * freezes for 2, then cuts; the incoming track starts early enough to ARRIVE
 * at its drop exactly at the cut and takes over at full volume. Volumes stay
 * stepped ([VolumeCurve.INSTANT]) â€” the renderer holds the outgoing at full
 * and the incoming at zero until the cut lands.
 */
private fun loopCutPlan(
    analysis: TrackAnalysis,
    nextAnalysis: TrackAnalysis,
    length: Double,
    nextLength: Double,
    playbackTime: Double,
    mixAnchor: Double,
    dropTime: Double,
    score: CompatibilityScore,
    policyReasons: List<String>,
    mixset: Boolean = false,
): TransitionPlan {
    val bpmOut = analysis.bpm.orZero()
    val beatOut = if (bpmOut > 0) 60 / bpmOut else if (mixset) 0.0 else 0.5
    if (mixset && beatOut == 0.0) {
        return hardCutPlan(
            analysis, nextAnalysis, length, nextLength,
            playbackTime, mixAnchor, score, policyReasons, mixset,
        )
    }
    if (analysis.downbeats.isEmpty()) {
        return hardCutPlan(
            analysis, nextAnalysis, length, nextLength,
            playbackTime, mixAnchor, score, policyReasons, mixset,
        )
    }
    val loopCeiling = if (mixset) djModeCeilingFor(TransitionType.LOOP_CUT_DROP)
        else ceilingFor(TransitionType.LOOP_CUT_DROP)
    val rawWindow = min(6 * 4 * beatOut, min(mixAnchor * 0.6, ABSOLUTE_MAX_TRANSITION_SECONDS))
        .coerceAtMost(loopCeiling)
        .coerceAtLeast(1.0)
    val windowSec = if (mixset && beatOut > 0) (kotlin.math.round(rawWindow / (4 * beatOut)) * 4 * beatOut).coerceAtLeast(4 * beatOut) else rawWindow
    val playFloorSeconds = if (mixset) 0.0 else 0.8 * length
    val rawStart = max(0.0, mixAnchor - windowSec)
    val transitionStart = if (!mixset && playFloorSeconds < mixAnchor - 1.0) {
        max(
            alignedTransitionStart(
                analysis, rawStart, mixAnchor - 0.05,
                preferEarlier = true, minimum = rawStart,
            ),
            playFloorSeconds,
        )
    } else {
        alignedTransitionStart(
            analysis, rawStart, mixAnchor - 0.05,
            preferEarlier = true, minimum = rawStart,
            mixset = mixset,
        )
    }
    val bpmIn = nextAnalysis.bpm.orZero()
    val ratio = if (bpmOut > 0 && bpmIn > 0) normalizedTempoRatio(bpmOut, bpmIn) else 1.0
    val rate = if (ratio in 0.98..1.02) 1.0 / ratio else 1.0
    val beatIn = if (nextAnalysis.bpm.orZero() > 0) 60 / nextAnalysis.bpm.orZero() else beatOut
    val dropSnap = nearestTimedValue(nextAnalysis.downbeats, dropTime, tolerance = beatIn * 4)
        ?: dropTime
    val buildInSec = (mixAnchor - transitionStart) * rate
    val rawCue = max(0.0, dropSnap - buildInSec)
    val cue = if (mixset) {
        val downCue = nearestTimedValue(nextAnalysis.downbeats, rawCue, tolerance = beatIn * 2) ?: rawCue
        capIncomingEntry(downCue, nextAnalysis, nextLength, mixset)
    } else capIncomingEntry(
        snapToPhrase16(nextAnalysis, rawCue), nextAnalysis, nextLength, mixset,
    )
    val started = playbackTime >= transitionStart
    return TransitionPlan(
        shouldStart = started,
        markerVisible = true,
        transitionStart = transitionStart,
        transitionEnd = mixAnchor,
        fadeSeconds = (mixAnchor - transitionStart).coerceAtLeast(0.1),
        transitionStyle = TransitionStyle.LOOP_CUT_DROP,
        type = TransitionType.LOOP_CUT_DROP,
        score = score,
        loopBars = 4,
        dropCueTime = dropSnap,
        incomingCueTime = cue,
        incomingHandoffTime = dropSnap,
        incomingPlaybackRate = rate,
        transitionBeats = ((mixAnchor - transitionStart) / beatOut).roundToInt().coerceAtLeast(1),
        bassSwap = false,
        filterSweep = 0.0,
        volumeCurve = VolumeCurve.INSTANT,
        eqCurve = EQCurve.NONE,
        vocalOverlap = plannedVocalOverlap(analysis, nextAnalysis, transitionStart, mixAnchor, cue, rate),
        outgoingBpm = bpmOut,
        incomingBpm = if (rate != 1.0) bpmIn / rate else 0.0,
        policyReasons = policyReasons,
        reason = if (started) "smart-loop-cut" else "before-loop-cut-window",
    )
}

/**
 * Loop-roll extend (short-outro rescue): the outgoing tail is too short for
 * a loop-cut vamp (under 8 bars below the anchor), so instead of a flat tail
 * plus slam, a percussive phrase loops (4â†’2â†’1â†’Â½ via the same vamp engine)
 * while the incoming track blends in over the extended zone, releasing on
 * the downbeat as its drop lands.
 *
 * Window = max(16 beats, remaining outro, 8 s), capped by the LOOP_ROLL
 * ceiling. Volumes stay stepped (INSTANT) â€” the renderer voices the 2-beat
 * release glide at the flip instead of the 1-beat closure. Falls back to
 * the hard cut without a downbeat grid (nothing honest to loop).
 */
private fun loopRollPlan(
    analysis: TrackAnalysis,
    nextAnalysis: TrackAnalysis,
    length: Double,
    nextLength: Double,
    playbackTime: Double,
    mixAnchor: Double,
    dropTime: Double,
    score: CompatibilityScore,
    policyReasons: List<String>,
    mixset: Boolean = false,
): TransitionPlan {
    val bpmOut = analysis.bpm.orZero()
    val beatOut = if (bpmOut > 0) 60 / bpmOut else if (mixset) 0.0 else 0.5
    if (mixset && beatOut == 0.0) {
        return hardCutPlan(
            analysis, nextAnalysis, length, nextLength,
            playbackTime, mixAnchor, score, policyReasons, mixset,
        )
    }
    if (analysis.downbeats.isEmpty()) {
        return hardCutPlan(
            analysis, nextAnalysis, length, nextLength,
            playbackTime, mixAnchor, score, policyReasons, mixset,
        )
    }
    val rollCeiling = if (mixset) djModeCeilingFor(TransitionType.LOOP_ROLL)
    else ceilingFor(TransitionType.LOOP_ROLL)
    val outroRemaining = max(0.0, length - mixAnchor)
    val windowSec = max(max(16.0 * beatOut, outroRemaining), 8.0)
        .coerceAtMost(min(mixAnchor * 0.8, rollCeiling))
        .coerceAtLeast(1.0)
    val playFloorSeconds = if (mixset) 0.0 else 0.8 * length
    val rawStart = max(0.0, mixAnchor - windowSec)
    val transitionStart = if (!mixset && playFloorSeconds < mixAnchor - 1.0) {
        max(
            alignedTransitionStart(
                analysis, rawStart, mixAnchor - 0.05,
                preferEarlier = true, minimum = rawStart,
            ),
            playFloorSeconds,
        )
    } else {
        alignedTransitionStart(
            analysis, rawStart, mixAnchor - 0.05,
            preferEarlier = true, minimum = rawStart,
            mixset = mixset,
        )
    }
    val bpmIn = nextAnalysis.bpm.orZero()
    val ratio = if (bpmOut > 0 && bpmIn > 0) normalizedTempoRatio(bpmOut, bpmIn) else 1.0
    val rate = if (ratio in 0.98..1.02) 1.0 / ratio else 1.0
    val dropSnap = nearestTimedValue(nextAnalysis.downbeats, dropTime, tolerance = beatOut * 4)
        ?: dropTime
    val buildInSec = (mixAnchor - transitionStart) * rate
    val rawCue = max(0.0, dropSnap - buildInSec)
    val cue = capIncomingEntry(
        snapToPhrase16(nextAnalysis, rawCue), nextAnalysis, nextLength, mixset,
    )
    val cueDown = nearestTimedValue(nextAnalysis.downbeats, cue, tolerance = beatOut)
    val phaseOffset = if (cueDown != null) cue - cueDown else 0.0
    val started = playbackTime >= transitionStart
    return TransitionPlan(
        shouldStart = started,
        markerVisible = true,
        transitionStart = transitionStart,
        transitionEnd = mixAnchor,
        fadeSeconds = (mixAnchor - transitionStart).coerceAtLeast(0.1),
        transitionStyle = TransitionStyle.LOOP_ROLL,
        type = TransitionType.LOOP_ROLL,
        score = score,
        loopBars = 4,
        dropCueTime = dropSnap,
        incomingCueTime = cue,
        incomingHandoffTime = dropSnap,
        incomingPlaybackRate = rate,
        transitionBeats = ((mixAnchor - transitionStart) / beatOut).roundToInt().coerceAtLeast(1),
        bassSwap = false,
        filterSweep = 0.0,
        volumeCurve = VolumeCurve.INSTANT,
        eqCurve = EQCurve.NONE,
        vocalOverlap = plannedVocalOverlap(analysis, nextAnalysis, transitionStart, mixAnchor, cue, rate),
        outgoingBpm = bpmOut,
        incomingBpm = if (rate != 1.0) bpmIn / rate else 0.0,
        overlapSeconds = (mixAnchor - transitionStart).coerceAtLeast(0.1),
        phaseOffsetSec = phaseOffset,
        policyReasons = policyReasons,
        reason = if (started) "smart-loop-roll" else "before-loop-roll-window",
    )
}

private fun trackDurationSeconds(track: TransitionTrackInfo?): Double =
    if (track == null || track.durationMs <= 0) 0.0 else track.durationMs / 1000.0

/**
 * Gapless is for an album being played through, not for any two songs that
 * happen to share an album. A playlist, a manual queue or a shuffle that
 * lands two album siblings back to back is a mix, and gets mixed; the caller
 * decides which of those it is via `albumSequential` and says so explicitly.
 */
private fun sameAlbum(left: TransitionTrackInfo?, right: TransitionTrackInfo?): Boolean {
    if (left == null || right == null) return false
    if (left.albumId.isNotBlank() && left.albumId == right.albumId) return true
    return left.album.isNotBlank() && left.album == right.album && left.artist == right.artist
}

private fun itemText(track: TransitionTrackInfo?): String =
    if (track == null) "" else listOf(track.title, track.artist, track.album)
        .filter { it.isNotBlank() }
        .joinToString(" ")

/** Folds [nextBpm] into the same octave as [currentBpm] and returns the ratio between them. */
private fun normalizedTempoRatio(currentBpm: Double, nextBpm: Double): Double {
    if (currentBpm <= 0 || nextBpm <= 0) return 1.0
    var ratio = nextBpm / currentBpm
    while (ratio > 1.5) ratio /= 2
    while (ratio < 0.67) ratio *= 2
    return ratio
}

private fun splitKey(key: String, mixset: Boolean = false): Pair<Int?, String?> {
    val parts = key.trim().split(' ')
    val root = if (mixset) canonicalKeyRoot(parts.firstOrNull()) else parts.firstOrNull()
    return KEY_INDEX[root] to parts.getOrNull(1)
}

private fun keyDistance(left: String, right: String, mixset: Boolean = false): Int? {
    val (leftIndex, leftMode) = splitKey(left, mixset)
    val (rightIndex, rightMode) = splitKey(right, mixset)
    if (leftIndex == null || rightIndex == null) return null
    val pitchDistance = min((leftIndex - rightIndex + 12) % 12, (rightIndex - leftIndex + 12) % 12)
    return pitchDistance + if (leftMode != null && rightMode != null && leftMode != rightMode) 1 else 0
}

private fun harmonicallyCompatible(left: String, right: String, mixset: Boolean = false): Boolean {
    val (leftIndex, leftMode) = splitKey(left, mixset)
    val (rightIndex, rightMode) = splitKey(right, mixset)
    if (leftIndex == null || rightIndex == null) return false
    val distance = min((leftIndex - rightIndex + 12) % 12, (rightIndex - leftIndex + 12) % 12)
    if (leftMode != null && rightMode != null && leftMode != rightMode) return distance <= 1
    // A fifth is as close as a second here: it is the move every DJ makes.
    return distance <= 2 || distance == 5
}

/**
 * A key the analyzer was not confident about is no key at all.
 *
 * Full-audit Phase 2: rung 1 of the key-trust ladder â€” 0.25 (proven-clash
 * gate) < [MIN_KEY_CONFIDENCE_FOR_SCORING] 0.30 (neutral floor) <
 * [MIN_KEY_CONFIDENCE_FOR_SHIFT] 0.5 (retune). Three values on purpose:
 * proving a clash needs less certainty than acting on the key.
 */
private fun trustedKey(analysis: TrackAnalysis): String =
    if (analysis.key.isBlank() || analysis.keyConfidence < 0.25) "" else analysis.key

/**
 * Booth key-clash rule: a PROVEN clash (both keys trusted, raw score below
 * the near-miss band) shortens the blend. Unknown keys are no evidence, not
 * a clash â€” they keep full length. Uses the raw keyScore, not the
 * neutralized matrix score (which scores unknown as 0.50, same as a clash).
 */
private fun keyClashProven(analysis: TrackAnalysis, nextAnalysis: TrackAnalysis): Boolean {
    if (trustedKey(analysis).isBlank() || trustedKey(nextAnalysis).isBlank()) return false
    return keyScore(analysis.key, nextAnalysis.key) < 0.45
}

private fun nearestTimedValue(
    values: List<Double>,
    target: Double,
    tolerance: Double = Double.POSITIVE_INFINITY,
    minimum: Double = 0.0,
): Double? = values
    .filter { it.isFinite() && it >= minimum && abs(it - target) <= tolerance }
    .minByOrNull { abs(it - target) }

private fun timedValueNearOrBefore(
    values: List<Double>,
    target: Double,
    tolerance: Double = Double.POSITIVE_INFINITY,
    minimum: Double = 0.0,
): Double? = values
    .filter { it.isFinite() && it >= minimum && it <= target && target - it <= tolerance }
    .maxOrNull()

/**
 * Snaps a transition start onto the outgoing track's grid: a 16-bar grid
 * point first (Review v2.1 B4 â€” the spec phrase; the native 8-bar phrases
 * do not always resolve to stable 16-bar forms, so [phrase16Grid] is tried
 * before them), then an 8-bar phrase boundary, a downbeat otherwise, and
 * the raw target when neither is.
 */
private fun alignedTransitionStart(
    analysis: TrackAnalysis,
    target: Double,
    end: Double,
    preferEarlier: Boolean,
    minimum: Double,
    mixset: Boolean = false,
): Double {
    if (!mixset) {
        val interval = analysis.beatInterval.orZero().takeIf { it > 0 }
            ?: if (analysis.bpm.orZero() > 0) 60 / analysis.bpm else 0.0
        val phraseTolerance = max(1.0, interval * 4)
        val downbeatTolerance = max(0.75, interval * 2)
        val phrase = if (preferEarlier) {
            timedValueNearOrBefore(analysis.phraseBoundaries, target, phraseTolerance, minimum)
        } else {
            nearestTimedValue(analysis.phraseBoundaries, target, phraseTolerance, minimum)
        }
        val downbeat = if (preferEarlier) {
            timedValueNearOrBefore(analysis.downbeats, target, downbeatTolerance, minimum)
        } else {
            nearestTimedValue(analysis.downbeats, target, downbeatTolerance, minimum)
        }
        return clamp(phrase ?: downbeat ?: target, minimum, end)
    }
    val interval = analysis.beatInterval.orZero().takeIf { it > 0 }
        ?: if (analysis.bpm.orZero() > 0) 60 / analysis.bpm else 0.0
    val phrase16Tolerance = max(1.5, interval * 8)
    val phraseTolerance = max(1.0, interval * 4)
    val downbeatTolerance = max(0.75, interval * 2)
    val grid16 = if (mixset) phrase16Grid(analysis) else emptyList()
    val phrase16 = if (preferEarlier) {
        timedValueNearOrBefore(grid16, target, phrase16Tolerance, minimum)
    } else {
        nearestTimedValue(grid16, target, phrase16Tolerance, minimum)
    }
    val phrase = if (preferEarlier) {
        timedValueNearOrBefore(analysis.phraseBoundaries, target, phraseTolerance, minimum)
    } else {
        nearestTimedValue(analysis.phraseBoundaries, target, phraseTolerance, minimum)
    }
    val downbeat = if (preferEarlier) {
        timedValueNearOrBefore(analysis.downbeats, target, downbeatTolerance, minimum)
    } else {
        nearestTimedValue(analysis.downbeats, target, downbeatTolerance, minimum)
    }
    return clamp(phrase16 ?: phrase ?: downbeat ?: target, minimum, end)
}

/**
 * Where the incoming track's arrangement arrives: the point the outgoing
 * track should be gone by.
 */
internal fun incomingCuePoint(analysis: TrackAnalysis, introOnly: Boolean = false, mixset: Boolean = false): Double {
    val introEnd = introEndSeconds(analysis)
    if (introOnly) {
        rankMixInCandidates(analysis, mixset).firstOrNull { it.time <= introEnd }?.let { return it.time }
    } else {
        rankMixInCandidates(analysis, mixset).firstOrNull()?.let { return it.time }
    }

    val interval = analysis.beatInterval.orZero().takeIf { it > 0 }
        ?: if (analysis.bpm.orZero() > 0) 60 / analysis.bpm else 0.0
    val downbeats = analysis.downbeats

    val analyzedMixIn = analysis.mixInTime
    if (analyzedMixIn.isFinite() && analyzedMixIn > 0 &&
        (!introOnly || analyzedMixIn <= introEnd)
    ) {
        return nearestTimedValue(downbeats, analyzedMixIn, max(0.5, interval * 2)) ?: analyzedMixIn
    }

    val pickup = max(
        0.0,
        analysis.introEndTime.orZero().takeIf { it != 0.0 }
            ?: (analysis.audibleStartTime ?: analysis.pickupTime).orZero().takeIf { it != 0.0 }
            ?: analysis.firstBeat.orZero(),
    )
    val duration = analysis.duration.orZero().takeIf { it != 0.0 } ?: 300.0
    if (pickup > 0 && pickup < duration - 10) {
        downbeats.firstOrNull { it >= pickup }?.let {
            return if (introOnly) min(it, introEnd) else it
        }
    }
    val phrases = analysis.phraseBoundaries
    if (phrases.size > 1 && phrases[1] > 4) {
        val phrase = phrases[1]
        return if (introOnly) min(phrase, introEnd) else phrase
    }
    if (downbeats.size >= 8) {
        val eighth = downbeats[min(8, downbeats.size - 1)].orZero()
        return if (introOnly) min(eighth, introEnd) else eighth
    }
    return if (introOnly) min(pickup, introEnd) else pickup
}

/** Where the incoming track first makes sound, so the fade is not cued into its lead-in silence. */
private fun incomingStartPoint(analysis: TrackAnalysis, mixset: Boolean = false): Double {
    val claimed = listOfNotNull(analysis.audibleStartTime, analysis.pickupTime, analysis.firstBeat)
        .firstOrNull { it.isFinite() && it >= 0 } ?: 0.0
    if (!mixset) return claimed
    return refinedStartPoint(analysis, claimed)
}

/**
 * Full-audit P0.4: vocal-aware cue for the cut paths (ECHO/HARD). Those cued
 * at audible start â€” the first sound even when it is a vocal onset â€” and the
 * echo tail / instant chop smeared the two voices together. Route through
 * the ranked mix-in list (vocal-penalised) like the beat-matched path; when
 * the cue window still sings (or has no mask coverage while the scalar says
 * vocal-heavy), offset past the opening phrase. Bounded by the entry cap.
 */
private fun vocalAwareCutCue(nextAnalysis: TrackAnalysis, nextLength: Double): Double {
    val cue = incomingCuePoint(nextAnalysis, introOnly = true, mixset = false)
    val beat = nextAnalysis.beatInterval.takeIf { it > 0 } ?: 0.5
    val windowVocal = vocalActivityBetween(nextAnalysis, cue, cue + beat * 16)
    val sings = windowVocal?.let { it >= VOCAL_ACTIVE_THRESHOLD }
        ?: (nextAnalysis.vocalProbability >= 0.62)
    val offset = if (sings) beat * 16 else 0.0
    val snapped = snapToPhrase16(nextAnalysis, cue + offset)
    return capIncomingEntry(snapped, nextAnalysis, nextLength, mixsetActive = false)
}

/**
 * Incoming entries stay in the opening stretch: 30% of the incoming track
 * normally, 50% in Mixset Mode â€” with a floor at the audible start so a long
 * intro is never cued into silence. Applied where musical targets are chosen
 * (before overlap math derives consistency from them), never to an already
 * derived cue/handoff pair.
 */
private fun capIncomingEntry(
    cue: Double,
    nextAnalysis: TrackAnalysis,
    nextLength: Double,
    mixsetActive: Boolean,
): Double {
    if (!cue.isFinite() || nextLength <= 0) return cue
    if (!mixsetActive) return cue
    val audible = listOfNotNull(nextAnalysis.audibleStartTime, nextAnalysis.pickupTime)
        .firstOrNull { it.isFinite() && it >= 0 } ?: 0.0
    if (mixsetActive) return max(cue, audible + 2.0)
    val introEnd = introEndSeconds(nextAnalysis)
    return min(cue, max(introEnd, audible + 2.0)).coerceAtLeast(0.0)
}

/**
 * DJ Mode entry: the foot of the buildup, the drop, or the peak â€” wherever
 * the analysis justifies, mid-track included. Only the audible-start floor
 * applies. The handoff aims here, not at the peak: the peak arrives on its
 * own time after the takeover, which is what lets a long buildup breathe.
 */
private fun mixsetEntryCue(nextAnalysis: TrackAnalysis, nextLength: Double): Double {
    val best = mixsetEntryPoint(nextAnalysis) ?: incomingStartPoint(nextAnalysis, mixset = true)
    val span = spokenInterludeSpan(nextAnalysis)
    val routed = if (span != null && best in span) span.endInclusive else best
    return capIncomingEntry(routed, nextAnalysis, nextLength, mixsetActive = true)
}

// ---------------------------------------------------------------------------
// WSOLA-style beat-matched phrase-switch plan (ported from WsolaPlanner.kt)
// ---------------------------------------------------------------------------

// The fade is bounded in beats because overlap length is musical: bounding it
// in seconds makes a faster track get a longer mix, which is backwards. Four
// bars is the ceiling and one bar the floor, the latter for tracks whose
// intro cannot cover more.
private const val MIN_FADE_BEATS = 4
private const val MAX_FADE_BEATS = 16

// A ceiling on the whole overlap regardless of how long the incoming intro is.
private const val MAX_OVERLAP_SECONDS = 16.0

/**
 * Moving both decks by the same musical amount preserves the beat grid and
 * overlap length while putting the incoming arrangement inside the blend
 * instead of making it the finish line. Applied only to a content-end exit on
 * the outgoing side; a real structural/energy exit has already supplied the
 * earlier anchor.
 */
internal const val ARRANGEMENT_OVERLAP_BEATS = 8

/**
 * One continuous equal-power fade across the whole overlap. 0.5/0.5 is the
 * plain symmetric crossfade, which is exactly the sin/cos pair
 * [com.music.bitchord.playback.CrossfadeController] rides â€” so at these values
 * the renderer already honours them, and anything else would need a two-segment
 * gain curve it does not have.
 */
const val HANDOFF_FRACTION = 0.5
const val BED_POSITION = 0.5

/** The prior for where the low end hands over, on a pairing with no useful structural change. */
private const val DEFAULT_BASS_SWAP_FRACTION = 0.7

/** Analysis may move the swap later than the prior, but never so late the outgoing low end survives almost to silence. */
private const val MAX_BASS_SWAP_FRACTION = 0.85

/** A normalized low-band step smaller than this is too weak to move the swap away from its prior. */
private const val MIN_BASS_STRUCTURE_SCORE = 0.25

/** Capped in absolute seconds too, so a long overlap does not scale the hold up with it. */
private const val BASS_SWAP_MAX_SECONDS = 6.0

/** DJ-only tighter low-end handoff rails (normal Automix keeps the stock values above). */
private const val DJ_MAX_BASS_SWAP_FRACTION = 0.80
private const val DJ_BASS_SWAP_MAX_SECONDS = 5.5

/**
 * How far the outgoing track's low-pass sweep travels by the end of the
 * overlap, as a fraction of a full ride. 1.0 is the whole way down to
 * [com.music.bitchord.playback.CrossfadeController.FILTER_FLOOR_HZ].
 */
const val FILTER_SWEEP = 1.0

/** The outgoing track must have this much audio before the overlap and the incoming this much after it. */
private const val MIN_CLEARANCE_SECONDS = 5.0

private fun averageLowEnergy(curve: List<EnergySample>, from: Double, until: Double): Double? {
    if (until <= from) return null
    var index = curve.binarySearchBy(from) { it.time }.let { if (it >= 0) it else -it - 1 }
    var sum = 0.0
    var count = 0
    while (index < curve.size && curve[index].time < until) {
        val point = curve[index++]
        if (point.time.isFinite() && point.energy.isFinite() && point.energy >= 0) {
            sum += point.energy
            count++
        }
    }
    return if (count > 0) sum / count else null
}

private fun lowEnergyReference(curve: List<EnergySample>): Double? {
    val energies = curve.map { it.energy }.filter { it.isFinite() && it >= 0 }.sorted()
    if (energies.isEmpty()) return null
    val upperDecile = energies[(energies.lastIndex * 0.9).toInt()]
    val reference = max(upperDecile, (energies.lastOrNull() ?: 0.0) * 0.25)
    return reference.takeIf { it > 1e-9 }
}

private fun lowEnergyResolution(curve: List<EnergySample>): Double {
    val gaps = curve.zipWithNext { left, right -> right.time - left.time }
        .filter { it.isFinite() && it > 0 }
        .sorted()
    return gaps.getOrNull(gaps.size / 2) ?: 0.0
}

/** Change in low-band energy across one beat either side of [at], normalized per track. */
private fun lowEnergyChange(
    curve: List<EnergySample>,
    reference: Double?,
    at: Double,
    windowSeconds: Double,
): Double? {
    if (curve.isEmpty() || reference == null || windowSeconds <= 0) return null
    val before = averageLowEnergy(curve, at - windowSeconds, at) ?: return null
    val after = averageLowEnergy(curve, at, at + windowSeconds) ?: return null
    return (after / reference).coerceIn(0.0, 1.5) -
        (before / reference).coerceIn(0.0, 1.5)
}

/** Chooses one shared-grid beat for the low-end handoff. */
private fun bassSwapFractionFor(
    analysis: TrackAnalysis,
    nextAnalysis: TrackAnalysis,
    transitionStart: Double,
    incomingCueTime: Double,
    outgoingBeatSeconds: Double,
    incomingBeatSeconds: Double,
    overlapSeconds: Double,
    overlapBeats: Int,
    mixset: Boolean = false,
): Double {
    if (overlapSeconds <= 0) return DEFAULT_BASS_SWAP_FRACTION

    val maxFraction = if (mixset) DJ_MAX_BASS_SWAP_FRACTION else MAX_BASS_SWAP_FRACTION
    val maxSeconds = if (mixset) DJ_BASS_SWAP_MAX_SECONDS else BASS_SWAP_MAX_SECONDS
    val latestFraction = min(maxFraction, maxSeconds / overlapSeconds)
        .coerceIn(0.0, 1.0)
    val prior = min(DEFAULT_BASS_SWAP_FRACTION, latestFraction)
    if (overlapBeats < 2) return prior

    val earliestFraction = min(HANDOFF_FRACTION, latestFraction)
    val earliestBeat = ceil(earliestFraction * overlapBeats - 1e-9).toInt()
        .coerceIn(1, overlapBeats - 1)
    val latestBeat = floor(latestFraction * overlapBeats + 1e-9).toInt()
        .coerceIn(earliestBeat, overlapBeats - 1)
    val candidates = (earliestBeat..latestBeat).toList()
    val fallbackBeat = candidates.minWithOrNull(
        compareBy<Int> { abs(it.toDouble() / overlapBeats - prior) }
            .thenBy { if (it % 4 == 0) 0 else 1 },
    ) ?: return prior

    data class BassCandidate(val beat: Int, val score: Double)

    val outgoingReference = lowEnergyReference(analysis.lowEnergyCurve)
    val incomingReference = lowEnergyReference(nextAnalysis.lowEnergyCurve)
    val outgoingWindow = max(
        outgoingBeatSeconds,
        lowEnergyResolution(analysis.lowEnergyCurve) * 1.1,
    )
    val incomingWindow = max(
        incomingBeatSeconds,
        lowEnergyResolution(nextAnalysis.lowEnergyCurve) * 1.1,
    )
    val strongest = candidates.mapNotNull { beat ->
        val outgoingAt = transitionStart + beat * outgoingBeatSeconds
        val incomingAt = incomingCueTime + beat * incomingBeatSeconds
        val incomingChange = lowEnergyChange(
            nextAnalysis.lowEnergyCurve,
            incomingReference,
            incomingAt,
            incomingWindow,
        )
        val outgoingChange = lowEnergyChange(
            analysis.lowEnergyCurve,
            outgoingReference,
            outgoingAt,
            outgoingWindow,
        )
        if (incomingChange == null && outgoingChange == null) return@mapNotNull null
        BassCandidate(beat, (incomingChange ?: 0.0) - (outgoingChange ?: 0.0))
    }.maxWithOrNull(
        compareBy<BassCandidate> { it.score }
            .thenBy { if (it.beat % 4 == 0) 1 else 0 }
            .thenBy { -abs(it.beat.toDouble() / overlapBeats - prior) },
    )

    val chosenBeat = strongest?.takeIf { it.score >= MIN_BASS_STRUCTURE_SCORE }?.beat
        ?: fallbackBeat
    return chosenBeat.toDouble() / overlapBeats
}

/**
 * How vocal the planned overlap is on both sides at once, measured over the
 * windows the plan actually blends.
 *
 * The two windows are not the same length in wall-clock terms whenever the
 * incoming track is being stretched: [incomingPlaybackRate] above 1 means it
 * covers proportionally more of its own timeline in the same number of seconds,
 * so the incoming window is scaled by it rather than copied from the outgoing
 * one. Getting that wrong would measure a window the listener never hears.
 *
 * Answers zero for a degenerate span and for any track without a mask, so every
 * caller can set this unconditionally.
 */
/** Peak echo-send wet for a DJ vocal throw (F1): a send, not an instrument. */
private const val ECHO_THROW_WET = 0.45

/**
 * DJ send-effect selector (F1 throw / F3 wash, DJ-only): which, if any, send
 * effect voices the outgoing tail of a DJ_BLEND/DJ_FILTER. Returns
 * echoThrow + wash reverb wet. Evidence-gated: unknown masks answer none,
 * keeping blends that cannot prove a vocal tail exactly as dry as before.
 */
private fun djSendEffectFor(
    analysis: TrackAnalysis,
    nextAnalysis: TrackAnalysis,
    transitionStart: Double,
    transitionEnd: Double,
    incomingCueTime: Double,
    incomingPlaybackRate: Double,
    mixset: Boolean,
): Pair<Boolean, Double> {
    if (!mixset) return false to 0.0
    val overlap = transitionEnd - transitionStart
    if (!overlap.isFinite() || overlap < 6.0) return false to 0.0
    val tailStart = transitionEnd - minOf(8.0, overlap * 0.4)
    val tailMid = (tailStart + transitionEnd) / 2.0
    val early = vocalActivityBetween(analysis, tailStart, tailMid)
    val late = vocalActivityBetween(analysis, tailMid, transitionEnd)
    if (early != null && late != null && early >= 0.42 && late <= 0.35) {
        val inRate = incomingPlaybackRate.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        val inBeat = nextAnalysis.beatInterval.takeIf { it.isFinite() && it > 0.0 } ?: 0.5
        val entry = vocalActivityBetween(nextAnalysis, incomingCueTime, incomingCueTime + 4.0 * inBeat / inRate)
        if (entry != null && entry <= 0.45) return true to 0.0
    }
    var tailSum = 0.0
    var tailCount = 0
    var trackSum = 0.0
    var trackCount = 0
    for (sample in analysis.energyCurve) {
        if (!sample.time.isFinite() || !sample.energy.isFinite()) continue
        trackSum += sample.energy
        trackCount++
        if (sample.time >= tailStart && sample.time <= transitionEnd) {
            tailSum += sample.energy
            tailCount++
        }
    }
    if (trackCount > 0 && tailCount > 0 && tailSum / tailCount < 0.5 * trackSum / trackCount) {
        val crushed = analysis.dynamicRangeDb in 0.01..5.0 && analysis.peakDbfs > -3.0
        val crushScale = if (crushed) 0.6 else 1.0
        return false to BLEND_REVERB_WET * crushScale
    }
    return false to 0.0
}

/**
 * DJ brake selector (F2, DJ-only): a pair that would need a >6% tempo ride
 * gets punctuation instead â€” brake the outgoing out, drop the incoming on
 * the one. Literature reserves the brake for large gaps, not blends a pitch
 * ride could hold.
 */
private fun brakeFor(mixset: Boolean, incomingPlaybackRate: Double, overlapSeconds: Double): Boolean {
    if (!mixset || !overlapSeconds.isFinite() || overlapSeconds < 8.0) return false
    val rate = incomingPlaybackRate.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
    return rate > 1.06 || rate < 0.94
}

/**
 * Booth vinyl brake (DJ-only): the incoming tempo falls far below the
 * outgoing one (raw, unfolded ratio â€” an octave-adjacent 174â†’128 still
 * brakes), so pitch-dip the outgoing into the echo instead of blending
 * across a gap no ride can hold. The renderer voices it keylock-off
 * (pitch falls with the rate), exactly the booth gesture. Speedups ride
 * the echo/cut dry â€” a brake reads as a slowdown, never a lift.
 */
private fun brakeForSlowdown(
    mixset: Boolean,
    analysis: TrackAnalysis,
    nextAnalysis: TrackAnalysis,
): Boolean {
    if (!mixset) return false
    val bpmOut = analysis.bpm.orZero()
    val bpmIn = nextAnalysis.bpm.orZero()
    if (bpmOut <= 0 || bpmIn <= 0) return false
    return bpmIn / bpmOut < 0.85
}

/** Heavy-lift floor for a backspin: the raw speedup ratio past keylock comfort. */
private const val BACKSPIN_MIN_LIFT_RATIO = 1.04
/** Backspin pre-roll: the outgoing deck spins back over its last second. */
private const val BACKSPIN_SPIN_SECONDS = 1.0
/** Backspin dub tail on the outgoing channel while it spins (Â½ beat). */
private const val BACKSPIN_ECHO_PERIOD_BEATS = 0.5

/**
 * Booth backspin selector (DJ-only): a heavy LIFT across a proven key clash
 * gets a spin-back into the incoming drop instead of a blend no ride can
 * hold. Five gates, all evidence-gated â€” unknown on any axis answers false:
 *
 * 1. DJ Mode ([mixset]).
 * 2. Track2 is faster ([bpmIn] > [bpmOut]) AND the gap is heavy
 *    ([BACKSPIN_MIN_LIFT_RATIO]): a spin reads as a lift, never a slowdown â€”
 *    slowdowns keep [brakeForSlowdown].
 * 3. Both keys trusted (non-blank) and NOT [harmonicallyCompatible]: the pair
 *    is provably unblendable, not merely unmeasured.
 * 4. Peak energy ([isPeakEnergyAt]) at the outgoing exit AND the incoming
 *    drop: spins out of/into anything less break the emotional arc.
 * 5. The caller passes a trusted drop ([dropInB] with [isDropTrusted]) â€” the
 *    incoming track lands on its peak, never on a guess.
 */
private fun backspinFor(
    mixset: Boolean,
    analysis: TrackAnalysis,
    nextAnalysis: TrackAnalysis,
    mixAnchor: Double,
    dropInB: Double,
): Boolean {
    if (!mixset) return false
    val bpmOut = analysis.bpm.orZero()
    val bpmIn = nextAnalysis.bpm.orZero()
    if (bpmOut <= 0 || bpmIn <= 0) return false
    if (bpmIn <= bpmOut) return false
    if (bpmIn / bpmOut < BACKSPIN_MIN_LIFT_RATIO) return false
    val keyOut = trustedKey(analysis)
    val keyIn = trustedKey(nextAnalysis)
    if (keyOut.isBlank() || keyIn.isBlank()) return false
    if (harmonicallyCompatible(keyOut, keyIn, true)) return false
    if (!isPeakEnergyAt(analysis, mixAnchor)) return false
    if (!isPeakEnergyAt(nextAnalysis, dropInB)) return false
    return true
}

private fun plannedVocalOverlap(
    analysis: TrackAnalysis,
    nextAnalysis: TrackAnalysis,
    transitionStart: Double,
    transitionEnd: Double,
    incomingCueTime: Double,
    incomingPlaybackRate: Double,
): Double {
    val outgoingSpan = transitionEnd - transitionStart
    if (outgoingSpan <= 0.0 || !outgoingSpan.isFinite()) return 0.0
    val rate = incomingPlaybackRate.takeIf { it.isFinite() && it > 0 } ?: 1.0
    return simultaneousVocalFraction(
        outgoing = analysis,
        incoming = nextAnalysis,
        outStart = transitionStart,
        outEnd = transitionEnd,
        inStart = incomingCueTime,
        rate = rate,
    ) ?: 0.0
}

private fun nearestAtOrBefore(values: List<Double>, target: Double): Double? =
    values.filter { it.isFinite() && it >= 0 && it <= target }.maxOrNull()

/**
 * The outcome of planning one beat-matched transition.
 *
 * [Refused] is a routing decision, not an error: the caller falls back to the
 * adaptive overlap below, which degrades further on its own.
 */
sealed interface WsolaPlanResult {
    data class Refused(val reason: String) : WsolaPlanResult

    /** All times are seconds on each track's own media timeline. */
    data class Planned(
        val tier: TransitionTier,
        val beatConfidence: Double,
        val mixOutType: String,
        val vocalClash: Boolean,
        val transitionStart: Double,
        val transitionEnd: Double,
        val overlapSeconds: Double,
        val beats: Int,
        val fadeBeats: Int,
        val handoffFraction: Double,
        val bedPosition: Double,
        val bassSwapFraction: Double,
        val filterSweep: Double,
        val outgoingBpm: Double,
        val incomingBpm: Double,
        val stretchRatio: Double,
        val incomingCueTime: Double,
        val incomingDropTime: Double,
        val incomingHandoffTime: Double,
        val incomingResumeTime: Double,
    ) : WsolaPlanResult
}

/** Where the incoming track takes over: the best-ranked mix-in candidate, snapped to a downbeat. */
fun incomingMixInPoint(analysis: TrackAnalysis, introOnly: Boolean = false, mixset: Boolean = false): Double? {
    val beatSeconds = analysis.beatInterval.orZero().takeIf { it > 0 }
        ?: if (analysis.bpm.orZero() > 0) 60 / analysis.bpm else 0.0
    val tolerance = max(0.5, beatSeconds * 2)
    val introEnd = introEndSeconds(analysis)
    val ranked = if (introOnly) {
        rankMixInCandidates(analysis, mixset).firstOrNull { it.time <= introEnd }?.time
    } else {
        rankMixInCandidates(analysis, mixset).firstOrNull()?.time
    }
    val analyzed = analysis.mixInTime.takeIf { it.isFinite() && it > 0 && (!introOnly || it <= introEnd) }
    val target = listOfNotNull(ranked, analyzed)
        .firstOrNull { it.isFinite() && it > 0 }
        ?: return null
    return nearestValue(analysis.downbeats, target, tolerance) ?: target
}

/** Where the incoming track first makes sound. */
fun incomingAudibleStart(analysis: TrackAnalysis): Double = audibleStartOf(analysis)

/** Plans one beat-matched transition between [analysis] and [nextAnalysis]. */
fun planWsolaTransition(
    analysis: TrackAnalysis,
    nextAnalysis: TrackAnalysis,
    duration: Double = 0.0,
    nextDuration: Double = 0.0,
    mixset: Boolean = false,
    mixAnchorOverride: Double? = null,
): WsolaPlanResult {
    val policy = assessTransitionTier(analysis, nextAnalysis, mixset)
    if (policy.tier != TransitionTier.BEATMATCHED) {
        return WsolaPlanResult.Refused(policy.reasons.firstOrNull() ?: "policy")
    }

    val outgoingBpm = analysis.bpm.orZero()
    val incomingBpm = alignTempoOctave(outgoingBpm, nextAnalysis.bpm.orZero())
    val stretchRatio = outgoingBpm / incomingBpm

    val outgoingLength = max(duration.orZero(), analysis.duration.orZero())
    val incomingLength = max(nextDuration.orZero(), nextAnalysis.duration.orZero())
    if (outgoingLength <= 0 || incomingLength <= 0) return WsolaPlanResult.Refused("missing-duration")

    val incomingBeatSeconds = 60 / incomingBpm
    val outgoingBeatSeconds = 60 / outgoingBpm

    val rawDropTime = if (mixset) {
        mixsetEntryPoint(nextAnalysis) ?: incomingMixInPoint(nextAnalysis, mixset = true)
    } else {
        incomingMixInPoint(nextAnalysis)
    }
    val incomingDropTime = rawDropTime
        ?.takeIf { it.isFinite() && it >= 0 }
        ?.let { capIncomingEntry(it, nextAnalysis, incomingLength, mixset) }
    if (incomingDropTime == null || !incomingDropTime.isFinite() || incomingDropTime < 0) {
        return WsolaPlanResult.Refused("incoming-mix-in")
    }

    val contentEnd = analysis.contentEndTime.orZero().takeIf { it != 0.0 } ?: outgoingLength
    val resolvedAnchor = resolveMixOutAnchor(
        analysis, contentEnd = contentEnd, duration = outgoingLength,
    )
    val mixOutAnchor = if (mixset && mixAnchorOverride != null && mixAnchorOverride.isFinite()) {
        val coerced = mixAnchorOverride.coerceIn(0.0, outgoingLength)
        if (resolvedAnchor.time < coerced) resolvedAnchor else MixOutAnchor(
            time = coerced,
            type = "mixset_peak",
            discardedMusicSeconds = max(0.0, outgoingLength - coerced),
        )
    } else {
        resolvedAnchor
    }
    val unshiftedOverlapEnd = min(outgoingLength, mixOutAnchor.time)
    val outgoingArrangementOverlap =
        if (mixOutAnchor.type == "content_end") {
            min(ARRANGEMENT_OVERLAP_BEATS * outgoingBeatSeconds, MAX_DISCARDED_MUSIC_SECONDS)
        } else {
            0.0
        }
    val overlapEndTarget = max(MIN_CLEARANCE_SECONDS, unshiftedOverlapEnd - outgoingArrangementOverlap)

    val audibleStart = incomingAudibleStart(nextAnalysis)
    val availableFadeBeats = max(0.0, incomingDropTime - audibleStart) / incomingBeatSeconds
    val overlapCeilingSeconds = if (mixset) AppSettings.mixsetOverlapCeilingSeconds.value.toDouble() else MAX_OVERLAP_SECONDS
    val maxFadeBeats = if (mixset) 128 else MAX_FADE_BEATS
    val cappedByOverlap = floor(floor(overlapCeilingSeconds / incomingBeatSeconds) / 4).toInt() * 4
    if (cappedByOverlap < MIN_FADE_BEATS) return WsolaPlanResult.Refused("overlap-too-long")
    var fadeBeats = minOf(
        maxFadeBeats,
        cappedByOverlap,
        floor(availableFadeBeats / 4).toInt() * 4,
    )
    if (fadeBeats < MIN_FADE_BEATS) fadeBeats = MIN_FADE_BEATS

    fun clashOver(beats: Int): Boolean {
        val outStart = overlapEndTarget - beats * outgoingBeatSeconds
        val inStart = max(audibleStart, incomingDropTime - beats * incomingBeatSeconds)
        val outVocal = vocalActivityBetween(analysis, outStart, overlapEndTarget)
        val inVocal = vocalActivityBetween(nextAnalysis, inStart, incomingDropTime)

        // Instant-by-instant first, because it is the question actually being
        // asked. The mean-based test below only fires when *both* windows average
        // vocal across their whole length, which a real clash routinely does not:
        // an incoming track that starts singing a few seconds into the overlap
        // averages clear and still puts its opening line under the outgoing
        // vocal. This catches that, and it is what shrinks the overlap until the
        // two voices stop landing together.
        val simultaneous = simultaneousVocalFraction(
            outgoing = analysis,
            incoming = nextAnalysis,
            outStart = outStart,
            outEnd = overlapEndTarget,
            inStart = inStart,
            rate = if (outgoingBeatSeconds > 0) incomingBeatSeconds / outgoingBeatSeconds else 1.0,
        )
        if (simultaneous != null && simultaneous > VOCAL_CLASH_TOLERANCE) return true

        if (isVocalClash(outVocal, inVocal)) return true

        if (beats > 8 && outVocal != null && outVocal >= VOCAL_ACTIVE_THRESHOLD) {
            val deepVocal = vocalActivityBetween(analysis, outStart, overlapEndTarget - 8 * outgoingBeatSeconds)
            if (deepVocal != null && deepVocal >= VOCAL_ACTIVE_THRESHOLD) {
                return true
            }
        }
        return false
    }
    var fadeVocalClash = clashOver(fadeBeats)
    while (fadeVocalClash && fadeBeats > MIN_FADE_BEATS) {
        fadeBeats -= 4
        fadeVocalClash = clashOver(fadeBeats)
    }

    val coverableBeats = floor(max(0.0, incomingDropTime - audibleStart) / incomingBeatSeconds).toInt()
    val overlapBeats = min(fadeBeats, coverableBeats)
    if (overlapBeats < 1) return WsolaPlanResult.Refused("incoming-no-intro")

    val outgoingOverlapSeconds = overlapBeats * outgoingBeatSeconds
    val overlapSeconds = overlapBeats * incomingBeatSeconds

    val requestedIncomingHandoff =
        incomingDropTime + ARRANGEMENT_OVERLAP_BEATS * incomingBeatSeconds
    val maxIncomingHandoff = incomingLength - MIN_CLEARANCE_SECONDS
    if (maxIncomingHandoff < incomingDropTime) return WsolaPlanResult.Refused("incoming-too-short")
    val incomingHandoffTime = min(requestedIncomingHandoff, maxIncomingHandoff)
    val incomingCueTime = incomingHandoffTime - overlapSeconds
    if (incomingCueTime < audibleStart - 0.05) return WsolaPlanResult.Refused("incoming-no-runway")

    val startTarget = overlapEndTarget - outgoingOverlapSeconds
    val transitionStart = alignedTransitionStart(
        analysis, startTarget, overlapEndTarget,
        preferEarlier = true, minimum = MIN_CLEARANCE_SECONDS, mixset = mixset,
    )
    if (transitionStart < MIN_CLEARANCE_SECONDS) return WsolaPlanResult.Refused("outgoing-too-short")
    val transitionEnd = transitionStart + outgoingOverlapSeconds
    if (transitionEnd > outgoingLength + 0.05) return WsolaPlanResult.Refused("outgoing-overlap-overruns")

    val incomingResumeTime = incomingCueTime + overlapSeconds
    if (incomingResumeTime + MIN_CLEARANCE_SECONDS > incomingLength) {
        return WsolaPlanResult.Refused("incoming-too-short")
    }

    return WsolaPlanResult.Planned(
        tier = policy.tier,
        beatConfidence = policy.beatConfidence,
        mixOutType = mixOutAnchor.type,
        vocalClash = fadeVocalClash,
        transitionStart = transitionStart,
        transitionEnd = transitionEnd,
        overlapSeconds = overlapSeconds,
        beats = overlapBeats,
        fadeBeats = overlapBeats,
        handoffFraction = HANDOFF_FRACTION,
        bedPosition = BED_POSITION,
        bassSwapFraction = bassSwapFractionFor(
            analysis = analysis,
            nextAnalysis = nextAnalysis,
            transitionStart = transitionStart,
            incomingCueTime = incomingCueTime,
            outgoingBeatSeconds = outgoingBeatSeconds,
            incomingBeatSeconds = incomingBeatSeconds,
            overlapSeconds = overlapSeconds,
            overlapBeats = overlapBeats,
            mixset = mixset,
        ),
        filterSweep = FILTER_SWEEP,
        outgoingBpm = outgoingBpm,
        incomingBpm = incomingBpm,
        stretchRatio = stretchRatio,
        incomingCueTime = incomingCueTime,
        incomingDropTime = incomingDropTime,
        incomingHandoffTime = incomingHandoffTime,
        incomingResumeTime = incomingResumeTime,
    )
}

/**
 * The most ambitious move available: run the incoming track's instrumental
 * intro underneath the outgoing one and close on its drop. A refusal is a
 * routing decision, not an error: the caller falls back to the adaptive
 * overlap below, which degrades further on its own.
 */
private fun phraseSwitch(
    analysis: TrackAnalysis,
    nextAnalysis: TrackAnalysis,
    length: Double,
    nextLength: Double,
    mixset: Boolean = false,
    mixAnchor: Double = 0.0,
): TransitionPlan? {
    if (!harmonicallyCompatible(trustedKey(analysis), trustedKey(nextAnalysis), mixset)) return null

    val planned = planWsolaTransition(
        analysis = analysis,
        nextAnalysis = nextAnalysis,
        duration = length,
        nextDuration = nextLength,
        mixset = mixset,
        mixAnchorOverride = mixAnchor.takeIf { mixset },
    ) as? WsolaPlanResult.Planned ?: return null

    val overlap = planned.transitionEnd - planned.transitionStart
    val (throwFire, washWet) = djSendEffectFor(
        analysis = analysis,
        nextAnalysis = nextAnalysis,
        transitionStart = planned.transitionStart,
        transitionEnd = planned.transitionEnd,
        incomingCueTime = planned.incomingCueTime,
        incomingPlaybackRate = planned.stretchRatio,
        mixset = mixset,
    )
    return TransitionPlan(
        markerVisible = true,
        transitionStart = planned.transitionStart,
        transitionEnd = planned.transitionEnd,
        fadeSeconds = overlap,
        handoffStartSeconds = 0.0,
        handoffDuration = overlap,
        incomingCueTime = planned.incomingCueTime,
        incomingHandoffTime = planned.incomingHandoffTime,
        incomingPlaybackRate = (planned.stretchRatio * 10000).roundToInt() / 10000.0,
        pickupSeconds = incomingAudibleStart(nextAnalysis),
        transitionBeats = planned.beats,
        bassSwap = true,
        handoffFraction = planned.handoffFraction,
        bedPosition = planned.bedPosition,
        bassSwapFraction = planned.bassSwapFraction,
        // Deliberately not `planned.filterSweep`. A phrase switch is the one
        // case where both decks are genuinely on the same grid, and the move
        // there is to hand the low end over on a beat, not to hide the outgoing
        // throw away the reason it was worth aligning. The renderer reads a
        // nonzero sweep as "ride the filter instead", so this says zero.
        filterSweep = 0.0,
        // The separation this style *does* need, and the one it cannot get from
        // alignment. Two tracks on a shared grid are the worst case for
        // overlapping voices precisely because nothing about the arrangement
        // whole blend. The renderer uses this to deepen the entry high-pass and
        // the exit low-pass without turning the blend into a filter ride.
        vocalOverlap = plannedVocalOverlap(
            analysis = analysis,
            nextAnalysis = nextAnalysis,
            transitionStart = planned.transitionStart,
            transitionEnd = planned.transitionEnd,
            incomingCueTime = planned.incomingCueTime,
            incomingPlaybackRate = planned.stretchRatio,
        ),
        outgoingBpm = planned.outgoingBpm,
        incomingBpm = planned.incomingBpm,
        transitionStyle = TransitionStyle.DJ_BLEND,
        echoAmount = if (throwFire && mixset) ECHO_THROW_WET else 0.0,
        echoThrow = throwFire && mixset,
        echoPeriodBeats = if (throwFire && mixset) 1.0 else null,
        brake = brakeFor(
            mixset = mixset,
            incomingPlaybackRate = planned.stretchRatio,
            overlapSeconds = overlap,
        ),
        reverbAmount = if (mixset) washWet else 0.0,
    )
}

private data class Overlap(
    val overlap: Double,
    val transitionBeats: Int,
    val incomingPlaybackRate: Double,
)

/** How long a mix should run when the tracks are related but not phrase-switchable. */
/**
 * Finetune v1 Â§4.1 P1: per-type overlap ceilings. 12 s is a radio crossfade,
 * not a DJ blend â€” smooth/harmonic pairs get real blend room while surgical
 * types (filter/loop/dissolve) stay decisive.
 */
fun ceilingFor(type: TransitionType): Double = when (type) {
    TransitionType.SMOOTH_CROSSFADE -> 22.0
    TransitionType.HARMONIC_BLEND -> 28.0
    TransitionType.FILTER_SWEEP -> 9.0
    TransitionType.ECHO_REVERB_OUT -> 11.0
    TransitionType.LOOP_CUT_DROP -> 8.0
    TransitionType.LOOP_ROLL -> 12.0
    TransitionType.HARD_CUT -> 0.3
    TransitionType.HALF_TIME_BLEND -> 9.0
    TransitionType.OCTAVE_BLEND -> 16.0
    TransitionType.PLAIN_DISSOLVE -> 4.0
}

/**
 * Finetune-overlap: DJ Mode per-type ceilings in seconds. The normal-mode
 * [ceilingFor] would clip the long DJ beds from the inside (a 30 s harmonic
 * blend against a 28 s ceiling, a 15 s sweep against 9 s), so DJ Mode
 * carries its own â€” each just fits its beat target at 128 BPM, still under
 * the 90 s absolute net.
 */
private fun djModeCeilingFor(type: TransitionType): Double = when (type) {
    TransitionType.SMOOTH_CROSSFADE -> 60.0
    TransitionType.HARMONIC_BLEND -> 60.0
    TransitionType.FILTER_SWEEP -> 16.0
    TransitionType.ECHO_REVERB_OUT -> 12.5
    TransitionType.LOOP_CUT_DROP -> 8.0
    TransitionType.LOOP_ROLL -> 12.0
    TransitionType.HARD_CUT -> 0.3
    TransitionType.HALF_TIME_BLEND -> 16.0
    TransitionType.OCTAVE_BLEND -> 16.0
    TransitionType.PLAIN_DISSOLVE -> 6.0
}

/** Rail fallback: DJ Mode reads its own seconds ceiling, normal mode the classic one. */
private fun djRailCeiling(type: TransitionType, mixset: Boolean): Double =
    if (mixset) djModeCeilingFor(type) else ceilingFor(type)

/**
 * Finetune-overlap Â§Fix 4: when the EQ is already managing a vocal clash,
 * the overlap is safe to run longer â€” the mid-duck, not silence, separates
 * the voices. Capped by [djModeMaxBeats] at the call site.
 */
private fun eqOverlapBonusBeats(duckAMids: Boolean, delayBMids: Boolean, type: TransitionType): Int {
    if (type == TransitionType.LOOP_CUT_DROP ||
        type == TransitionType.HARD_CUT ||
        type == TransitionType.PLAIN_DISSOLVE
    ) return 0
    return when {
        duckAMids && delayBMids -> 8
        duckAMids || delayBMids -> 4
        else -> 0
    }
}

private fun adaptiveOverlap(
    analysis: TrackAnalysis,
    nextAnalysis: TrackAnalysis,
    transitionPoint: Double,
    entryPoint: Double,
    type: TransitionType = TransitionType.SMOOTH_CROSSFADE,
    mixset: Boolean = false,
): Overlap {
    val currentBpm = analysis.bpm.orZero()
    val nextBpm = nextAnalysis.bpm.orZero()
    if (currentBpm <= 0 || nextBpm <= 0) {
        return Overlap(AUTO_FALLBACK_SECONDS, 0, 1.0)
    }

    val ratio = normalizedTempoRatio(currentBpm, nextBpm)
    val distance = keyDistance(trustedKey(analysis), trustedKey(nextAnalysis), mixset)
    val vocalConflict = analysis.vocalProbability >= 0.62 && nextAnalysis.vocalProbability >= 0.62
    val baseBeats = if (mixset) {
        val tempoDeviation = abs(1 - ratio)
        when {
            tempoDeviation < 0.02 && (distance == null || distance <= 1) -> 128
            tempoDeviation < 0.04 && (distance == null || distance <= 3) -> 96
            tempoDeviation < 0.06 -> 64
            else -> 32
        }
    } else {
        val mismatch = !vocalConflict &&
            (abs(1 - ratio) > 0.07 || (distance != null && distance > 4))
        val stockBeats = if (mismatch) 16 else 8
        return Overlap(
            overlap = clamp(
                stockBeats * (60 / currentBpm),
                if (currentBpm >= 140) AUTO_FAST_TRACK_MIN_SECONDS else AUTO_MIN_SECONDS,
                AUTO_TRANSITION_MAX_SECONDS,
            ),
            transitionBeats = stockBeats,
            incomingPlaybackRate = if (ratio in 0.9..1.1) {
                (clamp(1 / ratio, 0.9, 1.1) * 10000).roundToInt() / 10000.0
            } else {
                1.0
            },
        )
    }
    val energyFactor = overlapEnergyFactor(analysis, nextAnalysis, transitionPoint, entryPoint)
    val beatSeconds = 60 / currentBpm
    val djBeatCeiling = djModeMaxBeats(type).toInt()
    val bonusBeats = if (mixset) {
        val estOverlap = (baseBeats * energyFactor).roundToInt() * beatSeconds
        val duckA = vocalActivityBetween(
            analysis, transitionPoint - estOverlap, transitionPoint - estOverlap * 0.30,
        )?.let { it > 0.50 } ?: false
        val entryWindow = nextAnalysis.beatInterval.takeIf { it > 0 }?.times(16) ?: 8.0
        val delayB = vocalActivityBetween(
            nextAnalysis, entryPoint, entryPoint + entryWindow,
        )?.let { it >= VOCAL_ACTIVE_THRESHOLD } ?: false
        eqOverlapBonusBeats(duckAMids = duckA, delayBMids = delayB, type = type)
    } else {
        0
    }
    val transitionBeats = ((baseBeats * energyFactor).roundToInt() + bonusBeats)
        .coerceIn(if (mixset) 16 else 4, if (mixset) max(16, djBeatCeiling) else 32)
        .let { if (mixset && vocalConflict) min(it, 32) else it }
    val minimumOverlap = if (currentBpm >= 140) 7.0 else 5.0

    return Overlap(
        overlap = clamp(
            transitionBeats * beatSeconds,
            minimumOverlap,
            if (mixset) djModeCeilingFor(type) else ceilingFor(type),
        ),
        transitionBeats = transitionBeats,
        incomingPlaybackRate = if (mixset) {
            if (ratio in 0.98..1.02) {
                (clamp(1 / ratio, 0.98, 1.02) * 10000).roundToInt() / 10000.0
            } else {
                1.0
            }
        } else {
            if (ratio in 0.9..1.1) {
                (clamp(1 / ratio, 0.9, 1.1) * 10000).roundToInt() / 10000.0
            } else {
                1.0
            }
        },
    )
}

/** v2 Â§6 energy-direction factor. Pure â€” shared with tests via [overlapEnergyFactorFor]. */
private const val OVERLAP_SLOPE_EPSILON = 0.002

private fun windowSlope(curve: List<EnergySample>, from: Double, to: Double): Double {
    if (to <= from) return 0.0
    val xs = mutableListOf<Double>()
    val ys = mutableListOf<Double>()
    for (point in curve) {
        if (!point.time.isFinite() || !point.energy.isFinite()) continue
        if (point.time < from || point.time > to) continue
        xs += point.time
        ys += point.energy
    }
    return StructureDetector.linearSlope(xs, ys)
}

private fun overlapEnergyFactor(
    analysis: TrackAnalysis,
    nextAnalysis: TrackAnalysis,
    transitionPoint: Double,
    entryPoint: Double,
): Double {
    val intervalA = analysis.beatInterval.orZero()
        .takeIf { it > 0 } ?: if (analysis.bpm.orZero() > 0) 60 / analysis.bpm else 0.5
    val intervalB = nextAnalysis.beatInterval.orZero()
        .takeIf { it > 0 } ?: if (nextAnalysis.bpm.orZero() > 0) 60 / nextAnalysis.bpm else 0.5
    val slopeA = windowSlope(analysis.energyCurve, transitionPoint - 64 * intervalA, transitionPoint)
    val slopeB = windowSlope(nextAnalysis.energyCurve, entryPoint, entryPoint + 64 * intervalB)
    return overlapEnergyFactorFor(slopeA, slopeB)
}

/** v2 Â§6 table: Aâ†“Bâ†‘ stretches, bothâ†‘ tightens, everything else holds. */
fun overlapEnergyFactorFor(slopeA: Double, slopeB: Double): Double =
    when (energyTrajectoryFor(slopeA, slopeB)) {
        EnergyTrajectory.A_DOWN_B_UP -> OVERLAP_ENERGY_STRETCH_FACTOR
        EnergyTrajectory.A_UP_B_UP -> OVERLAP_ENERGY_TIGHTEN_FACTOR
        else -> 1.0
    }

/**
 * Phase B4: energy direction of the pair, classified from the same two
 * slopes and dead-band as the sizing factor â€” zero new measurement. The
 * selector consumes it so fighting risers wash out while comedownâ†’buildup
 * arcs earn the long blend.
 */
enum class EnergyTrajectory {
    A_DOWN_B_UP, A_UP_B_UP, A_UP_B_DOWN, A_DOWN_B_DOWN,
    A_FLAT_B_UP, A_FLAT_B_DOWN, A_UP_B_FLAT, A_DOWN_B_FLAT, FLAT,
}

fun energyTrajectoryFor(slopeA: Double, slopeB: Double): EnergyTrajectory {
    val a = if (slopeA < -OVERLAP_SLOPE_EPSILON) -1 else if (slopeA > OVERLAP_SLOPE_EPSILON) 1 else 0
    val b = if (slopeB < -OVERLAP_SLOPE_EPSILON) -1 else if (slopeB > OVERLAP_SLOPE_EPSILON) 1 else 0
    return when {
        a < 0 && b > 0 -> EnergyTrajectory.A_DOWN_B_UP
        a > 0 && b > 0 -> EnergyTrajectory.A_UP_B_UP
        a > 0 && b < 0 -> EnergyTrajectory.A_UP_B_DOWN
        a < 0 && b < 0 -> EnergyTrajectory.A_DOWN_B_DOWN
        a == 0 && b > 0 -> EnergyTrajectory.A_FLAT_B_UP
        a == 0 && b < 0 -> EnergyTrajectory.A_FLAT_B_DOWN
        a > 0 && b == 0 -> EnergyTrajectory.A_UP_B_FLAT
        a < 0 && b == 0 -> EnergyTrajectory.A_DOWN_B_FLAT
        else -> EnergyTrajectory.FLAT
    }
}

private fun standardTransition(
    length: Double,
    playbackTime: Double,
    fadeSeconds: Double,
    minFadeSeconds: Double,
    reason: String = "standard",
    mixset: Boolean = false,
): TransitionPlan {
    val fade = clamp(fadeSeconds, minFadeSeconds, 12.0)
    val transitionStart = max(0.0, length - fade)
    val started = playbackTime >= transitionStart
    return TransitionPlan(
        shouldStart = started,
        markerVisible = true,
        transitionStart = transitionStart,
        transitionEnd = length,
        fadeSeconds = fade,
        transitionStyle = TransitionStyle.EQUAL_POWER,
        standardTransitionUsed = mixset,
        reason = if (started) reason else "before-$reason-window",
    )
}

/** A stale analysis paired with the wrong track is worse than no analysis at all. */
private fun analysisReadyForTrack(analysis: TrackAnalysis, track: TransitionTrackInfo?): Boolean {
    if (analysis.status.isBlank()) return true
    if (analysis.status != TrackAnalysis.STATUS_READY) return false
    return analysis.trackId.isBlank() || track?.id.isNullOrBlank() || analysis.trackId == track.id
}

/**
 * Mixset fire floor, retired: DJ Mode is freeform, so no blend may-start
 * floor applies â€” the exit anchor already carries the sole duration rule
 * (entry + one phrase anti-flap). Kept as a no-op (not deleted) because
 * MixsetTest pins its arithmetic directly; every call site still routes
 * through it.
 */
internal fun applyMixsetFireFloor(plan: TransitionPlan, length: Double, mixset: Boolean): TransitionPlan {
    if (mixset && plan.vocalOverlap > 0.12 && plan.volumeCurve == VolumeCurve.S_CURVE) {
        return plan.copy(
            volumeCurve = VolumeCurve.LOGARITHMIC,
            forceDuckKeys = true,
            policyReasons = plan.policyReasons + "vocal-choke-log",
        )
    }
    return plan
}

/**
 * Plans the transition out of [currentTrack] and into [nextTrack].
 *
 * Called on every playback tick; the returned plan describes the transition
 * whether or not it has started yet.
 *
 * @param albumSequential true only when this is an album genuinely being
 *   played through in order, which is the sole case that earns a gapless
 *   handoff instead of a mix.
 * @param currentTime the outgoing track's playhead, in seconds.
 * @param mixset Mixset Mode: the outgoing window becomes ~90 s past the
 *   track's best part and the 80%-play floor below does not apply.
 */
/**
 * Minimum audible blend every non-blocked plan must carry. Plans shorter
 * than this (HARD_CUT 0.1 s, misflagged GAPLESS, LOOP INSTANT-hold, span
 * collapses) are substituted with a silence-seeking dissolve so no track
 * ever hard-cuts like a manual Next press.
 */
const val MIN_GUARANTEED_BLEND_SECONDS = 4.0

fun planTransition(
    analysis: TrackAnalysis = TrackAnalysis(),
    nextAnalysis: TrackAnalysis = TrackAnalysis(),
    currentTrack: TransitionTrackInfo? = null,
    nextTrack: TransitionTrackInfo? = null,
    currentTime: Double = 0.0,
    duration: Double = 0.0,
    fadeSeconds: Double = 6.0,
    minFadeSeconds: Double = 1.0,
    mode: CrossfadeMode = CrossfadeMode.STANDARD,
    albumSequential: Boolean = false,
    mixset: Boolean = false,
): TransitionPlan {
    val plan = planTransitionInner(
        analysis, nextAnalysis, currentTrack, nextTrack, currentTime,
        duration, fadeSeconds, minFadeSeconds, mode, albumSequential, mixset,
    )
    if (plan.blocked) return plan
    if (!mixset) return plan
    if (plan.fadeSeconds >= MIN_GUARANTEED_BLEND_SECONDS) return plan
    if (plan.type == TransitionType.PLAIN_DISSOLVE) return plan
    if (plan.type == TransitionType.LOOP_CUT_DROP) return plan
    if (plan.type == TransitionType.LOOP_ROLL) return plan
    if (plan.type == TransitionType.HARD_CUT) return plan
    if (currentTrack != null && nextTrack != null && currentTrack.id == nextTrack.id) return plan
    if (plan.transitionStyle == TransitionStyle.GAPLESS) return plan
    val len = max(duration.orZero(), trackDurationSeconds(currentTrack))
    val nextLen = max(0.0, trackDurationSeconds(nextTrack))
    val playbackTime = max(0.0, currentTime.orZero())
    val sub = plainDissolvePlan(
        analysis, nextAnalysis, len, nextLen, playbackTime, mixset,
        plan.policyReasons.ifEmpty { listOf("instant-to-dissolve-floor") },
    )
    if (sub.fadeSeconds >= MIN_GUARANTEED_BLEND_SECONDS) return sub
    val extendedStart = max(0.0, sub.transitionEnd - MIN_GUARANTEED_BLEND_SECONDS)
    val extendedFade = sub.transitionEnd - extendedStart
    return sub.copy(
        shouldStart = playbackTime >= extendedStart,
        transitionStart = extendedStart,
        fadeSeconds = extendedFade,
        handoffStartSeconds = extendedStart,
        handoffDuration = extendedFade,
        overlapSeconds = extendedFade,
        reason = "instant-to-dissolve-floor",
    )
}

private fun planTransitionInner(
    analysis: TrackAnalysis = TrackAnalysis(),
    nextAnalysis: TrackAnalysis = TrackAnalysis(),
    currentTrack: TransitionTrackInfo? = null,
    nextTrack: TransitionTrackInfo? = null,
    currentTime: Double = 0.0,
    duration: Double = 0.0,
    fadeSeconds: Double = 6.0,
    minFadeSeconds: Double = 1.0,
    mode: CrossfadeMode = CrossfadeMode.STANDARD,
    albumSequential: Boolean = false,
    mixset: Boolean = false,
): TransitionPlan {
    val length = max(duration.orZero(), trackDurationSeconds(currentTrack))
    val playbackTime = max(0.0, currentTime.orZero())
    if (length <= 0) return blocked("no-duration")

    val standardFade = clamp(fadeSeconds, minFadeSeconds, 12.0)
    if (mode != CrossfadeMode.SMART) {
        return standardTransition(length, playbackTime, standardFade, minFadeSeconds, mixset = mixset)
    }

    if (length < MIN_SMART_DURATION_SECONDS) {
        return blocked("short-duration-guard", transitionStart = length, transitionEnd = length)
    }

    val nextLenShort = max(0.0, trackDurationSeconds(nextTrack))
    if (mixset && length < 90.0) {
        return applyMixsetFireFloor(
            plainDissolvePlan(analysis, nextAnalysis, length, nextLenShort, playbackTime, mixset, emptyList()),
            length, mixset,
        )
    }

    val analyzedContentEnd = analysis.contentEndTime.orZero().takeIf { it != 0.0 } ?: length
    val finalMixAnchor = if (analyzedContentEnd > 0 && analyzedContentEnd <= length) {
        analyzedContentEnd
    } else {
        length
    }
    val playFloorSeconds = if (mixset) 0.0 else length * 0.8
    val candidateWindow = if (mixset) {
        null
    } else {
        (max(0.0, playFloorSeconds)..(length - 15.0))
            .takeIf { it.start < it.endInclusive }
    }
    val mixOutAnchor = if (mixset) {
        mixsetMixOutAnchor(analysis, length, playbackTime)
    } else {
        resolveMixOutAnchor(
            analysis,
            contentEnd = finalMixAnchor,
            duration = length,
        )
    }
    val hasInteriorMixOut = mixOutAnchor.time < finalMixAnchor - 1

    if (albumSequential && sameAlbum(currentTrack, nextTrack) && !hasInteriorMixOut) {
        val transitionStart = max(0.0, length - 0.45)
        val started = playbackTime >= transitionStart
        return TransitionPlan(
            shouldStart = started,
            markerVisible = true,
            transitionStart = transitionStart,
            transitionEnd = length,
            fadeSeconds = 0.12,
            transitionStyle = TransitionStyle.GAPLESS,
            reason = if (started) "same-album-gapless" else "before-gapless-window",
        )
    }

    if (BLOCKED_TEXT.containsMatchIn("${itemText(currentTrack)} ${itemText(nextTrack)}")) {
        return blocked("blocked-speech-or-live")
    }

    if (!analysisReadyForTrack(analysis, currentTrack) ||
        !analysisReadyForTrack(nextAnalysis, nextTrack)
    ) {
        return standardTransition(
            length,
            playbackTime,
            standardFade,
            minFadeSeconds,
            "smart-analysis-fallback",
            mixset = mixset,
        )
    }

    val sameFileRepeat = currentTrack != null && nextTrack != null &&
        currentTrack.id.isNotBlank() && currentTrack.id == nextTrack.id
    val outgoingMasked = analysis.vocalActivityMask.isNotEmpty() && !analysis.provisionalHead
    val incomingMasked = nextAnalysis.vocalActivityMask.isNotEmpty()
    if (mixset && !sameFileRepeat && (!outgoingMasked || !incomingMasked)) {
        return applyMixsetFireFloor(
            plainDissolvePlan(
                analysis, nextAnalysis, length,
                max(nextAnalysis.duration.orZero(), trackDurationSeconds(nextTrack)),
                playbackTime, mixset,
                listOf("vocal-mask-gate"),
            ),
            length, mixset,
        )
    }

    val preferredMixAnchor = min(length, mixOutAnchor.time)
    val rawMixAnchor =
        if (!mixset && playbackTime >= preferredMixAnchor - 0.05 && preferredMixAnchor < finalMixAnchor - 1) {
            finalMixAnchor
        } else {
            preferredMixAnchor
        }
    var mixAnchor = alignMixsetExitToIncomingDrop(analysis, nextAnalysis, rawMixAnchor, mixset)

    val nextLength = max(nextAnalysis.duration.orZero(), trackDurationSeconds(nextTrack))

    val policy = assessTransitionTier(analysis, nextAnalysis, mixset)
    if (policy.tier == TransitionTier.PLAIN_CROSSFADE) {
        if (!mixset) {
            val transitionStart = max(0.0, mixAnchor - standardFade)
            val started = playbackTime >= transitionStart
            return TransitionPlan(
                shouldStart = started,
                markerVisible = true,
                transitionStart = transitionStart,
                transitionEnd = mixAnchor,
                fadeSeconds = mixAnchor - transitionStart,
                transitionStyle = TransitionStyle.EQUAL_POWER,
                incomingCueTime = incomingStartPoint(nextAnalysis),
                policyReasons = policy.reasons,
                reason = if (started) "smart-plain-crossfade" else "before-plain-crossfade-window",
            )
        }
        return applyMixsetFireFloor(
            plainDissolvePlan(
                analysis, nextAnalysis, length, nextLength,
                playbackTime, mixset, policy.reasons,
                candidateShift = policy.candidateShiftSemitones,
            ),
            length, mixset,
        )
    }

    var proxyEntry = capIncomingEntry(
        if (mixset) incomingCuePoint(nextAnalysis, introOnly = true, mixset = true)
        else incomingCuePoint(nextAnalysis),
        nextAnalysis, nextLength, mixset,
    )
    var energyFloorFailed = false
    if (mixset) {
        val beatA = analysis.beatInterval.orZero().takeIf { it > 0 }
            ?: if (analysis.bpm.orZero() > 0) 60 / analysis.bpm else 0.5
        val beatB = nextAnalysis.beatInterval.orZero().takeIf { it > 0 }
            ?: if (nextAnalysis.bpm.orZero() > 0) 60 / nextAnalysis.bpm else 0.5
        fun windowMean(a: TrackAnalysis, from: Double, to: Double): Double? {
            if (to <= from) return null
            var sum = 0.0
            var n = 0
            var t = from
            while (t <= to) {
                val e = energyAt(a, t) ?: return null
                sum += e
                n++
                t += 0.5
            }
            return if (n > 0) sum / n else null
        }
        fun trackMean(a: TrackAnalysis, len: Double): Double? {
            if (len <= 0) return null
            var sum = 0.0
            var n = 0
            var t = 0.0
            while (t <= len) {
                val e = energyAt(a, t) ?: return null
                sum += e
                n++
                t += len / 24.0
            }
            return if (n > 0) sum / n else null
        }
        val meanA = trackMean(analysis, length)
        val meanB = trackMean(nextAnalysis, nextLength)
        if (meanA != null && meanA > 0) {
            var shifts = 0
            while (shifts < 4) {
                val w = windowMean(analysis, mixAnchor - 8.0, mixAnchor) ?: break
                if (w >= MIX_MIN_ENERGY_FLOOR * meanA) break
                val earlier = mixAnchor - beatA
                val discardOk = if (mixset) {
                    length - earlier <= BLUEPRINT_WINDOW_DISCARD_BUDGET
                } else {
                    earlier >= length * 0.8 && length - earlier <= MAX_DISCARDED_MUSIC_SECONDS
                }
                if (!discardOk) break
                mixAnchor = earlier
                shifts++
            }
            val w = windowMean(analysis, mixAnchor - 8.0, mixAnchor)
            if (w == null || w < MIX_MIN_ENERGY_FLOOR * meanA) energyFloorFailed = true
        }
        if (!energyFloorFailed && meanB != null && meanB > 0) {
            var shifts = 0
            while (shifts < 4) {
                val w = windowMean(nextAnalysis, proxyEntry, proxyEntry + 4.0) ?: break
                if (w >= MIX_MIN_ENERGY_FLOOR * meanB) break
                proxyEntry += beatB
                shifts++
            }
            val w = windowMean(nextAnalysis, proxyEntry, proxyEntry + 4.0)
            if (w == null || w < MIX_MIN_ENERGY_FLOOR * meanB) {
                energyFloorFailed = true
            }
        }
        if (meanA == null || meanB == null) energyFloorFailed = false
    }
    val proxyScore = scoreCompatibility(analysis, nextAnalysis, mixAnchor, proxyEntry)
    if (mixset && proxyScore.overall < SCORE_ACCEPTABLE) {
        TrackLog.d(
            PLANNER_TAG,
            "Low score ${"%.2f".format(proxyScore.overall)} " +
                "genres=${genreClass(analysis)}/${genreClass(nextAnalysis)} " +
                "tier=${policy.tier} ratio=${policy.matchedRatio}",
        )
        if (policy.tier == TransitionTier.HALF_TIME) {
            val keyShiftSemitones = if (proxyScore.key in 0.45..0.75 &&
                    analysis.key.isNotBlank() && nextAnalysis.key.isNotBlank())
                semitonesToShift(analysis.key, nextAnalysis.key)
            else 0
            return octaveBlendPlan(
                analysis, nextAnalysis, length, nextLength,
                playbackTime, mixAnchor, proxyEntry, proxyScore, policy.reasons,
                keyShiftSemitones, mixset,
            ) ?: filterSweepPlan(
                analysis, nextAnalysis, length, nextLength,
                playbackTime, mixAnchor, proxyEntry, proxyScore, policy.reasons,
                mixset, keyShiftSemitones,
                keyClash = keyClashProven(analysis, nextAnalysis),
            )
        }
        return applyMixsetFireFloor(
            heavyClashPlan(
                analysis, nextAnalysis, length, nextLength,
                playbackTime, mixAnchor, proxyEntry, proxyScore, policy.reasons, mixset,
            ),
            length, mixset,
        )
    }
    val dropInB = firstDropSec(nextAnalysis)
    val highEnergyA = isHighEnergyAt(analysis, mixAnchor)
    val buildupB = buildupStart(nextAnalysis, dropInB ?: proxyEntry) ?: proxyEntry
    val highEnergyB = isHighEnergyAt(nextAnalysis, buildupB)
    val beatOrHalf = policy.tier == TransitionTier.BEATMATCHED || policy.tier == TransitionTier.HALF_TIME
    val realDropInB = dropInB != null && dropInB.isFinite() && isDropTrusted(nextAnalysis)
    val vocalWallToWall = mixset && run {
        val ivA = analysis.beatInterval.orZero().takeIf { it > 0 }
            ?: if (analysis.bpm.orZero() > 0) 60 / analysis.bpm else 0.5
        val ivB = nextAnalysis.beatInterval.orZero().takeIf { it > 0 }
            ?: if (nextAnalysis.bpm.orZero() > 0) 60 / nextAnalysis.bpm else 0.5
        val outStart = max(0.0, mixAnchor - 32 * ivA)
        val inEnd = proxyEntry + 32 * ivB
        val sim = simultaneousVocalFraction(
            outgoing = analysis, incoming = nextAnalysis,
            outStart = outStart, outEnd = mixAnchor, inStart = proxyEntry, rate = 1.0,
        )
        sim != null && sim > 0.2 &&
            firstQuietGapSec(analysis, outStart, mixAnchor) == null &&
            firstVocalStartSec(nextAnalysis, proxyEntry, inEnd) != null
    }
    val dropB = dropInB
    if (mixset && realDropInB && dropB != null && dropB.isFinite() &&
        backspinFor(mixset, analysis, nextAnalysis, mixAnchor, dropB)
    ) {
        return applyMixsetFireFloor(
            backspinPlan(
                analysis, nextAnalysis, length, nextLength,
                playbackTime, mixAnchor, dropB, proxyScore, policy.reasons, mixset,
            ),
            length, mixset,
        )
    }
    if (vocalWallToWall) {
        val beatOutA = analysis.beatInterval.orZero().takeIf { it > 0 }
            ?: if (analysis.bpm.orZero() > 0) 60 / analysis.bpm else 0.5
        if (realDropInB && dropInB != null && mixAnchor >= 32 * beatOutA) {
            return applyMixsetFireFloor(
                cutPlan(
                    analysis, nextAnalysis, length, nextLength,
                    playbackTime, mixAnchor, proxyScore, policy.reasons, mixset,
                    loopBars = 4, dropTime = dropInB,
                ),
                length, mixset,
            )
        }
        return applyMixsetFireFloor(
            if (proxyScore.bpm >= 0.60) {
                cutPlan(
                    analysis, nextAnalysis, length, nextLength,
                    playbackTime, mixAnchor, proxyScore, policy.reasons, mixset,
                    loopBars = 0,
                )
            } else {
                washPlan(
                    analysis, nextAnalysis, length, nextLength,
                    playbackTime, mixAnchor, proxyScore, policy.reasons, mixset,
                    longWash = true,
                )
            },
            length, mixset,
        )
    }
    val bestIntroRank = rankMixInCandidates(nextAnalysis, mixset).firstOrNull()?.rankScore
        ?: Double.NEGATIVE_INFINITY
    val bestOutroRank = rankMixOutCandidates(analysis, finalMixAnchor, length, candidateWindow, outroOnly = mixset)
        .firstOrNull()?.rankScore ?: Double.NEGATIVE_INFINITY
    val intervalA = analysis.beatInterval.orZero()
        .takeIf { it > 0 } ?: if (analysis.bpm.orZero() > 0) 60 / analysis.bpm else 0.5
    val intervalB = nextAnalysis.beatInterval.orZero()
        .takeIf { it > 0 } ?: if (nextAnalysis.bpm.orZero() > 0) 60 / nextAnalysis.bpm else 0.5
    val trajectory = energyTrajectoryFor(
        windowSlope(analysis.energyCurve, mixAnchor - 64 * intervalA, mixAnchor),
        windowSlope(nextAnalysis.energyCurve, proxyEntry, proxyEntry + 64 * intervalB),
    )
    val selectedType = if (beatOrHalf || policy.tier == TransitionTier.DJ_ASSISTED) {
        selectTransitionType(
            proxyScore, policy.tier, highEnergyA, highEnergyB, realDropInB,
            introQuality = bestIntroRank, outroQuality = bestOutroRank, trajectory = trajectory,
            mixset = mixset,
        )
    } else {
        TransitionType.SMOOTH_CROSSFADE
    }
    if (mixset && energyFloorFailed &&
        (selectedType == TransitionType.SMOOTH_CROSSFADE ||
            selectedType == TransitionType.HARMONIC_BLEND ||
            selectedType == TransitionType.FILTER_SWEEP)
    ) {
        return applyMixsetFireFloor(
            washPlan(
                analysis, nextAnalysis, length, nextLength,
                playbackTime, mixAnchor, proxyScore, policy.reasons + "energy-floor-failover", mixset,
                longWash = true,
            ),
            length, mixset,
        )
    }
    if (mixset && currentTrack != null && nextTrack != null && currentTrack.id == nextTrack.id) {
        return applyMixsetFireFloor(
            cutPlan(
                analysis, nextAnalysis, length, nextLength,
                playbackTime, mixAnchor, proxyScore, policy.reasons, mixset,
                loopBars = 0,
            ),
            length, mixset,
        )
    }
    if (mixset && selectedType == TransitionType.ECHO_REVERB_OUT) {
        return applyMixsetFireFloor(
            washPlan(
                analysis, nextAnalysis, length, nextLength,
                playbackTime, mixAnchor, proxyScore, policy.reasons, mixset,
                longWash = true,
            ),
            length, mixset,
        )
    }
    if (mixset && selectedType == TransitionType.LOOP_CUT_DROP && dropInB != null) {
        val beatOutA = analysis.beatInterval.orZero().takeIf { it > 0 }
            ?: if (analysis.bpm.orZero() > 0) 60 / analysis.bpm else 0.5
        if (mixAnchor >= 32 * beatOutA) {
            return applyMixsetFireFloor(
                cutPlan(
                    analysis, nextAnalysis, length, nextLength,
                    playbackTime, mixAnchor, proxyScore, policy.reasons, mixset,
                    loopBars = 4, dropTime = dropInB,
                ),
                length, mixset,
            )
        }
        val bpmOutR = analysis.bpm.orZero()
        val bpmInR = nextAnalysis.bpm.orZero()
        val tempoOk = bpmOutR > 0 && bpmInR > 0 &&
            abs(1 - normalizedTempoRatio(bpmOutR, bpmInR)) <= 0.04
        if (realDropInB && tempoOk && analysis.downbeats.isNotEmpty()) {
            return applyMixsetFireFloor(
                loopRollPlan(
                    analysis, nextAnalysis, length, nextLength,
                    playbackTime, mixAnchor, dropInB, proxyScore, policy.reasons, mixset,
                ),
                length, mixset,
            )
        }
        return applyMixsetFireFloor(
            cutPlan(
                analysis, nextAnalysis, length, nextLength,
                playbackTime, mixAnchor, proxyScore, policy.reasons, mixset,
                loopBars = 0,
            ),
            length, mixset,
        )
    }
    if (mixset && selectedType == TransitionType.HARD_CUT) {
        return applyMixsetFireFloor(
            cutPlan(
                analysis, nextAnalysis, length, nextLength,
                playbackTime, mixAnchor, proxyScore, policy.reasons, mixset,
            ),
            length, mixset,
        )
    }
    if (mixset && policy.tier == TransitionTier.HALF_TIME) {
        val keyShiftSemitones = if (proxyScore.key in 0.45..0.75 &&
                analysis.key.isNotBlank() && nextAnalysis.key.isNotBlank())
            semitonesToShift(analysis.key, nextAnalysis.key)
        else 0
        return octaveBlendPlan(
            analysis, nextAnalysis, length, nextLength,
            playbackTime, mixAnchor, proxyEntry, proxyScore, policy.reasons,
            keyShiftSemitones, mixset,
        ) ?: filterSweepPlan(
            analysis, nextAnalysis, length, nextLength,
            playbackTime, mixAnchor, proxyEntry, proxyScore, policy.reasons,
            mixset, keyShiftSemitones,
            keyClash = keyClashProven(analysis, nextAnalysis),
        )
    }

    phraseSwitch(analysis, nextAnalysis, length, nextLength, mixset, mixAnchor)
        ?.takeIf { playbackTime < it.transitionEnd }
        ?.let { plan ->
            val started = playbackTime >= plan.transitionStart
            return applyMixsetFireFloor(
                plan.copy(
                    shouldStart = started,
                    type = if (mixset) TransitionType.HARMONIC_BLEND else plan.type,
                    score = if (mixset) scoreCompatibility(analysis, nextAnalysis, plan.transitionStart, plan.incomingCueTime).let { re ->
                        if (re.overall < plan.score.overall) re else plan.score
                    } else plan.score,
                    eqCurve = if (mixset) EQCurve.BASS_SWAP else plan.eqCurve,
                    policyReasons = policy.reasons,
                    reason = if (started) "smart-phrase-switch" else "before-phrase-switch",
                ),
                length, mixset,
            )
        }

    val (overlap, transitionBeats, adaptiveRate) =
        adaptiveOverlap(analysis, nextAnalysis, mixAnchor, proxyEntry, selectedType, mixset)
    val incomingPlaybackRate =
        if (mixset && policy.tier == TransitionTier.DJ_ASSISTED) 1.0 else adaptiveRate
    val currentBpm = analysis.bpm.orZero()
    val nextBpm = nextAnalysis.bpm.orZero()
    val handoffBpm = if (currentBpm > 0) currentBpm else nextBpm
    val sameBeatBlend = currentBpm > 0 && nextBpm > 0 &&
        abs(1 - normalizedTempoRatio(currentBpm, nextBpm)) <= 0.05 &&
        (analysis.beatConfidence.orZero() >= 0.2 || nextAnalysis.beatConfidence.orZero() >= 0.2)
    val outgoingArrangementOverlap =
        if (sameBeatBlend && mixOutAnchor.type == "content_end") {
            min(ARRANGEMENT_OVERLAP_BEATS * 60 / currentBpm, MAX_DISCARDED_MUSIC_SECONDS)
        } else {
            0.0
        }
    var mixEnd = max(0.0, mixAnchor - outgoingArrangementOverlap)
    val typeBeats = min(maxBeatsFor(selectedType), if (mixset) djModeMaxBeats(selectedType) else Double.POSITIVE_INFINITY)
    val floorRail = mixEnd - playFloorSeconds
    val earlyIncomingCue = incomingStartPoint(nextAnalysis, mixset = true).coerceAtLeast(0.0)
    var maximumOverlap = if (!mixset) {
        minOf(
            if (handoffBpm > 0) (AUTO_TRANSITION_MAX_BEATS * 60) / handoffBpm else AUTO_TRANSITION_MAX_SECONDS,
            AUTO_TRANSITION_MAX_SECONDS,
            mixEnd * 0.4,
            if (nextLength > 0) nextLength * 0.4 else AUTO_TRANSITION_MAX_SECONDS,
        )
    } else minOf(
        if (handoffBpm > 0) (typeBeats * 60) / handoffBpm else djRailCeiling(selectedType, mixset),
        ABSOLUTE_MAX_TRANSITION_SECONDS,
        mixEnd * 0.6,
        if (nextLength > 0) max(0.0, nextLength - earlyIncomingCue) * 0.60 else djRailCeiling(selectedType, mixset),
        if (!mixset && floorRail >= MIN_TRANSITION_OVERLAP_SECONDS) floorRail else Double.POSITIVE_INFINITY,
    )
    val handoffBeats = if (sameBeatBlend) 8 else 4
    val beatSeconds = if (handoffBpm > 0) 60 / handoffBpm else 0.5
    val clashOverlapCap =
        if (mixset && selectedType == TransitionType.FILTER_SWEEP &&
            keyClashProven(analysis, nextAnalysis)
        ) {
            8.0 * beatSeconds
        } else {
            Double.POSITIVE_INFINITY
        }
    val handoffSeconds = if (handoffBpm > 0) {
        clamp((handoffBeats * 60) / handoffBpm, 2.0, if (sameBeatBlend) 6.0 else 5.0)
    } else {
        4.0
    }
    maximumOverlap = min(maximumOverlap, clashOverlapCap)
    val analyzedPickup = nextAnalysis.audibleStartTime ?: nextAnalysis.pickupTime
    val pickupSeconds = if (analyzedPickup != null && analyzedPickup.isFinite() && analyzedPickup >= 0) {
        analyzedPickup
    } else {
        0.0
    }
    val incomingDropTime = if (mixset) {
        capIncomingEntry(
            mixsetEntryPoint(nextAnalysis) ?: incomingCuePoint(nextAnalysis, mixset = true),
            nextAnalysis, nextLength, mixsetActive = true,
        )
    } else {
        capIncomingEntry(incomingCuePoint(nextAnalysis), nextAnalysis, nextLength, mixsetActive = false)
    }
    val alignedIncomingBpm = alignTempoOctave(currentBpm, nextBpm)
    val requestedIncomingHandoff =
        if (sameBeatBlend && alignedIncomingBpm > 0) {
            incomingDropTime + ARRANGEMENT_OVERLAP_BEATS * 60 / alignedIncomingBpm
        } else {
            incomingDropTime
        }
    val maxIncomingHandoff = nextLength - MIN_INCOMING_CLEARANCE_SECONDS
    val incomingHandoffTime =
        if (maxIncomingHandoff >= incomingDropTime) {
            min(requestedIncomingHandoff, maxIncomingHandoff)
        } else {
            incomingDropTime
        }
    val rawIncomingCueTime = incomingStartPoint(nextAnalysis, mixset = mixset)
    val analyzedIncomingHandoff = nextAnalysis.mixInTime
    val hasIncomingPreroll = analyzedIncomingHandoff.isFinite() &&
        analyzedIncomingHandoff > rawIncomingCueTime + 0.5
    val incomingCueTime = if (hasIncomingPreroll) rawIncomingCueTime else incomingHandoffTime
    val introPreroll = max(
        0.0,
        (if (hasIncomingPreroll) incomingHandoffTime - incomingCueTime else 0.0) /
            max(0.8, incomingPlaybackRate),
    )

    var finalIncomingCueTime: Double
    var transitionStart: Double

    if (sameBeatBlend && beatSeconds > 0) {
        val introDropTime = incomingHandoffTime / max(0.8, incomingPlaybackRate)
        val totalOverlap = clamp(introDropTime, min(12.0, maximumOverlap), maximumOverlap)
        val targetStart = max(0.0, mixEnd - totalOverlap)
        val earliestTransitionStart = max(0.0, mixEnd - maximumOverlap)
        transitionStart = alignedTransitionStart(
            analysis,
            targetStart,
            mixEnd - 0.05,
            preferEarlier = true,
            minimum = earliestTransitionStart,
            mixset = mixset,
        )
        finalIncomingCueTime =
            max(0.0, incomingHandoffTime - (mixEnd - transitionStart) * incomingPlaybackRate)
    } else {
        val desiredOverlap = max(overlap, introPreroll + handoffSeconds * 0.42)
        val actualOverlap = clamp(desiredOverlap, min(handoffSeconds, maximumOverlap), maximumOverlap)
        val targetStart = max(0.0, mixEnd - actualOverlap)
        val earliestTransitionStart = max(0.0, mixEnd - maximumOverlap)
        transitionStart = alignedTransitionStart(
            analysis,
            targetStart,
            mixEnd - 0.05,
            preferEarlier = desiredOverlap > overlap + 0.5,
            minimum = earliestTransitionStart,
            mixset = mixset,
        )
        finalIncomingCueTime = if (hasIncomingPreroll) {
            max(0.0, incomingHandoffTime - (mixEnd - transitionStart) * incomingPlaybackRate)
        } else {
            incomingCueTime
        }
    }

    var alignedOverlap = mixEnd - transitionStart
    if (sameBeatBlend && beatSeconds > 0 && alignedOverlap >= 8.0 && mixset) {
        val window = analysis.energyCurve.filter {
            it.time.isFinite() && it.energy.isFinite() &&
                it.time >= transitionStart && it.time <= mixEnd
        }
        if (window.size >= 8) {
            val mean = window.sumOf { it.energy } / window.size
            val lastQuarter = transitionStart + alignedOverlap * 0.75
            val peak = window.filter { it.time >= lastQuarter }.maxByOrNull { it.energy }
            val entryWindow = nextAnalysis.beatInterval.takeIf { it > 0 }?.times(16) ?: 8.0
            val singsIn = vocalActivityBetween(
                nextAnalysis, finalIncomingCueTime, finalIncomingCueTime + entryWindow,
            )?.let { it >= VOCAL_ACTIVE_THRESHOLD } ?: true
            if (peak != null && mean > 0 && peak.energy / mean >= 1.5 && !singsIn) {
                val beat = analysis.beatInterval.orZero().takeIf { it > 0 }
                    ?: if (currentBpm > 0) 60 / currentBpm else 0.5
                val valley = window.filter { it.time < peak.time - beat * 0.5 }
                    .minByOrNull { it.energy }?.time
                if (valley != null) {
                    val snapped = timedValueNearOrBefore(
                        phrase16Grid(analysis), valley, max(1.5, beat * 8), transitionStart,
                    ) ?: timedValueNearOrBefore(
                        analysis.downbeats, valley, max(0.75, beat * 2), transitionStart,
                    ) ?: valley
                    if (snapped > transitionStart + MIN_TRANSITION_OVERLAP_SECONDS &&
                        mixEnd - snapped <= MAX_DISCARDED_MUSIC_SECONDS
                    ) {
                        val delta = mixEnd - snapped
                        mixEnd = snapped
                        transitionStart = alignedTransitionStart(
                            analysis,
                            max(0.0, transitionStart - delta),
                            mixEnd - 0.05,
                            preferEarlier = true,
                            minimum = max(0.0, mixEnd - maximumOverlap),
                            mixset = mixset,
                        )
                        finalIncomingCueTime = max(
                            0.0,
                            incomingHandoffTime - (mixEnd - transitionStart) * incomingPlaybackRate,
                        )
                        alignedOverlap = mixEnd - transitionStart
                    }
                }
            }
        }
    }
    val hasBassContent = analysis.lowEnergyCurve.isNotEmpty() || nextAnalysis.lowEnergyCurve.isNotEmpty()
    val finalScore = scoreCompatibility(analysis, nextAnalysis, transitionStart, finalIncomingCueTime)
    val keyShift = if (mixset && !sameBeatBlend &&
        analysis.key.isNotBlank() && nextAnalysis.key.isNotBlank() &&
        keyScore(analysis.key, nextAnalysis.key) in 0.45..0.75 &&
        !(nextAnalysis.pitchConfidence >= TRUSTED_PITCH_CONFIDENCE &&
            pitchVetoesShift(nextAnalysis.vocalPitchMedianHz, nextAnalysis.key))
    ) {
        policy.candidateShiftSemitones
    } else {
        0
    }
    val started = playbackTime >= transitionStart
    val (throwFireAdaptive, washWetAdaptive) = djSendEffectFor(
        analysis = analysis,
        nextAnalysis = nextAnalysis,
        transitionStart = transitionStart,
        transitionEnd = mixEnd,
        incomingCueTime = finalIncomingCueTime,
        incomingPlaybackRate = incomingPlaybackRate,
        mixset = mixset,
    )
    return applyMixsetFireFloor(
        TransitionPlan(
            shouldStart = started,
            markerVisible = true,
            transitionStart = transitionStart,
            transitionEnd = mixEnd,
        fadeSeconds = alignedOverlap,
        handoffStartSeconds = 0.0,
        handoffDuration = alignedOverlap,
        incomingCueTime = finalIncomingCueTime,
        incomingHandoffTime = incomingHandoffTime,
        incomingPlaybackRate = incomingPlaybackRate,
        pickupSeconds = pickupSeconds,
        transitionBeats = transitionBeats,
        bassSwap = sameBeatBlend || hasBassContent,
        transitionStyle = if (sameBeatBlend) TransitionStyle.DJ_BLEND else TransitionStyle.DJ_FILTER,
        type = if (mixset) selectedType else TransitionType.SMOOTH_CROSSFADE,
        score = if (mixset) finalScore else CompatibilityScore(),
        keyShiftSemitones = keyShift,
        volumeCurve = VolumeCurve.S_CURVE,
        eqCurve = if (mixset) {
            if (sameBeatBlend) EQCurve.BASS_SWAP else EQCurve.EQ_SWAP
        } else {
            EQCurve.NONE
        },
        // The two styles are alternatives, not a scale: a matched pair hands the
        // low end over on a beat and otherwise stays open, while an unmatched
        // pair has no shared grid to hand anything over on and instead pulls the
        // outgoing track behind a closing low-pass. Left at zero on the blend
        // branch so the renderer doesn't do both at once.
        filterSweep = if (sameBeatBlend) 0.0 else FILTER_SWEEP,
        vocalOverlap = plannedVocalOverlap(
            analysis = analysis,
            nextAnalysis = nextAnalysis,
            transitionStart = transitionStart,
            transitionEnd = mixEnd,
            incomingCueTime = finalIncomingCueTime,
            incomingPlaybackRate = incomingPlaybackRate,
        ),
        policyReasons = policy.reasons,
        reason = if (started) "smart-duration" else "before-smart-duration",
        echoAmount = if (throwFireAdaptive && mixset) ECHO_THROW_WET else 0.0,
        echoThrow = throwFireAdaptive && mixset,
        echoPeriodBeats = if (throwFireAdaptive && mixset) 1.0 else null,
        brake = brakeFor(
            mixset = mixset,
            incomingPlaybackRate = incomingPlaybackRate,
            overlapSeconds = alignedOverlap,
        ),
        reverbAmount = if (mixset) washWetAdaptive else 0.0,
        ),
        length, mixset,
    )
}
