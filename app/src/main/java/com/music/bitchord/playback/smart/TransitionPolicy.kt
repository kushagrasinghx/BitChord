/*
 * Ported from Orchard (https://github.com/SFG5453/Orchard).
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

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.settings.AppSettings

/**
 * The confidence-aware transition policy.
 *
 * Analysis happens ahead of playback (see [TrackAnalyzer]), and the runtime
 * only decides how ambitious a transition the stored evidence can support.
 * Ambition degrades in explicit tiers as certainty falls (see
 * [TransitionTier]) rather than letting one engine quietly do beat math on
 * junk data.
 *
 * Every judgement here is made from stored analysis fields and their
 * confidences; nothing in this file touches PCM.
 */

private const val POLICY_TAG = "BitChordPolicy"

/**
 * Last mixset anchor verdict logged; the planner calls
 * [mixsetMixOutAnchor] every tick, so only distinct decisions are logged.
 * The per-tick fields (t=, pos=) are stripped from the dedupe key â€” otherwise
 * every 250 ms tick formats a novel string and the "once" never fires
 * (field log 2026-09-05: 60+ rescue lines in 3 minutes).
 */
private var lastMixsetAnchorLog: String? = null

private val tickField = Regex(" (t|pos)=[0-9.]+")

private fun logMixsetAnchorOnce(trackId: String, msg: String) {
    val key = "$trackId|${tickField.replace(msg, "")}"
    if (key != lastMixsetAnchorLog) {
        lastMixsetAnchorLog = key
        TrackLog.d(POLICY_TAG, msg)
    }
}

/**
 * Below this the analyzer's beat grid is treated as a guess, and no renderer
 * may stretch or phase-align against it. Catalog tempo lookups merge in with
 * `beatConfidence` 0, so a metadata BPM alone can never authorize
 * beat-matching.
 */
const val MIN_BEATMATCH_CONFIDENCE = 0.55

/**
 * Below this on both tracks, even the DJ-assisted crossfade (beat-quantized
 * anchors, EQ handoff) is off the table and the mix degrades to a plain fade.
 */
const val MIN_DJ_CONFIDENCE = 0.2

/** One octave either side of a typical dance tempo; outside this the analysis is noise. */
const val MIN_BPM = 40.0
const val MAX_BPM = 220.0

/** How far a tempo pairing may drift from unity and still be considered transparent to stretch.
 * Tempo-transparency fix: Â±2 % (â‰ˆ1/3 semitone) stays under the audibility band;
 * anything wider falls back to a wash at rate 1.0 instead of a stretched beatmatch. */
const val MAX_STRETCH_DEVIATION = 0.02
const val STOCK_MAX_STRETCH_DEVIATION = 0.04

/**
 * A vocal-activity mask value at or above this counts as singing. A fallback
 * analyzer that emits a flat 0.5 mask never trips vocal logic; only a real
 * mask can.
 */
const val VOCAL_ACTIVE_THRESHOLD = 0.6

/**
 * How much of the outgoing track's remaining *music* a transition may skip by
 * ending before its content does. A transition is allowed to leave a short
 * tail unplayed; it is not allowed to cut the song short.
 */
const val MAX_DISCARDED_MUSIC_SECONDS = 12.0

/**
 * Fraction of a track's own loud-end reference below which a sample counts as
 * silence rather than music, so a genuine gap costs nothing against the budget.
 */
private const val AUDIBLE_ENERGY_FRACTION = 0.1

/**
 * How much each candidate type is trusted as an entry point before scoring.
 * Drops are where an arrangement arrives, so they dominate; a pickup is just
 * "the file starts making sound" and a phrase boundary is only a grid line.
 */
private val MIX_IN_TYPE_WEIGHT = mapOf(
    "main_drop" to 0.40,
    "intro_drop" to 0.50,
    "pickup" to 0.22,
    "phrase" to 0.12,
)
private val STOCK_MIX_IN_TYPE_WEIGHT = mapOf(
    "main_drop" to 0.5,
    "intro_drop" to 0.4,
    "pickup" to 0.15,
    "phrase" to 0.1,
)

/**
 * Mirrors the analyzer's own scoring of mix-out candidates, used when an
 * analysis carries the scalar fields but not the candidate list.
 */
private val MIX_OUT_TYPE_SCORE = mapOf(
    "energy_cliff" to 0.95,
    "interior_mix_out" to 0.90,
    "outro_start" to 0.95,
    "content_end" to 0.72,
    "vocal_exit" to 0.65,
    "low_energy" to 0.55,
    "blueprint_fallback" to 0.35,
)
private val STOCK_MIX_OUT_TYPE_SCORE = mapOf(
    "energy_cliff" to 0.95,
    "interior_mix_out" to 0.95,
    "outro_start" to 0.9,
    "content_end" to 0.75,
)

/** Finetune v1 Â§2.3: low-energy candidate inside a BREAK scores near the top. */
const val LOW_ENERGY_BREAK_BOOST_SCORE = 0.80

/**
 * When blueprint Â§7 candidates are active, the mix-out may end far earlier
 * than the old 12 s tail budget allows â€” the length-60 s fallback alone can
 * skip a minute of music. The windowed budget covers exactly that.
 */
const val BLUEPRINT_WINDOW_DISCARD_BUDGET = 75.0


/** v2 Â§10: 0.65 was too aggressive on vocal-heavy genres. */
const val VOCAL_DISCARD_THRESHOLD = 0.72
/** v2 Â§10: soft vocal penalty starts slightly earlier than the old implied 0.60. */
const val VOCAL_SOFT_PENALTY = 0.55
/** Finetune v1 Â§2.1 P1: strong zone above 0.65 gets Ã—3.0; soft zone Ã—1.8. */
const val VOCAL_STRONG_ZONE = 0.65
const val VOCAL_STRONG_MULTIPLIER = 3.0
const val VOCAL_SOFT_MULTIPLIER = 1.8
/** Finetune v1 Â§2.1: timing buffer around each exit candidate. */
const val VOCAL_EXIT_TIMING_WINDOW = 1.5
/** v2 Â§10: key confidence floor for scoring; below it the key reads neutral. */
const val MIN_KEY_CONFIDENCE_FOR_SCORING = 0.30
/** v2 Â§10: an unconfident key must not veto the whole plan. */
const val NEUTRAL_KEY_SCORE_BELOW_CONF = 0.50

/** v2 Â§6: overlap stretch when A falls while B rises (ideal blend). */
const val OVERLAP_ENERGY_STRETCH_FACTOR = 1.50
/** v2 Â§6: overlap tighten when both tracks rise (avoid mud). */
const val OVERLAP_ENERGY_TIGHTEN_FACTOR = 0.75

/** v2 Â§10: wider bass-swap ramp, smoother handover. */
const val BASS_SWAP_WIDTH_V2 = 0.20
/** v2 Â§7a: virtual mid-kill upper LP (FILTER_SWEEP only, keyScore < 0.50). */
const val MID_KILL_LP_HZ = 700.0
/** v2 Â§7a: virtual mid-kill lower HP entry. */
const val MID_KILL_HP_HZ = 350.0
/** Review v2.1 B1: outgoing LP ramp start for the staggered mid-kill. */
const val MID_KILL_START_HZ = 1200.0
/** Review v2.1 B1: incoming HP during the staggered overlap window. */
const val MID_KILL_STAGGERED_HP_HZ = 500.0
/** Review v2.1 B1: outgoing LP bed after the handoff completes. */
const val MID_KILL_BED_HZ = 300.0

/** Finetune v1 Â§1: structural detector thresholds. */
const val DROP_RMS_MULTIPLIER = 1.25
const val DROP_ONSET_MULTIPLIER = 1.25
const val DROP_CENTROID_HZ = 2500.0
const val DROP_SLOPE_LOOKBACK_BARS = 4
const val DROP_SLOPE_MIN_POSITIVE_BARS = 2
const val DROP_COLD_OPEN_POSITION_FRACTION = 0.12
const val BUILD_RMS_SLOPE_PER_BAR = 0.010
const val BUILD_CENTROID_SLOPE_PER_BAR = 90.0
const val BREAK_RMS_FRACTION = 0.68
const val BREAK_ONSET_FRACTION = 0.72
const val BREAK_MIN_BARS = 6
const val BREAK_PRIOR_HIGH_BARS = 8
const val OUTRO_POSITION_FRACTION = 0.65
const val OUTRO_RMS_PEAK_FRACTION = 0.75
const val OUTRO_FALLING_SLOPE = -0.0008
const val OUTRO_QUIET_RATIO = 0.88
const val OUTRO_SPECTRAL_RATIO = 0.88
const val INTRO_POSITION_FRACTION = 0.22
const val INTRO_RMS_FRACTION = 0.78
const val INTRO_CENTROID_HZ = 3200.0
const val COLD_OPEN_AUDIBLE_SECONDS = 1.5
const val COLD_OPEN_RMS_FRACTION = 0.85
const val AMBIENT_BEAT_CONF = 0.28
const val AMBIENT_ONSET_DENSITY = 1.8
const val AMBIENT_RMS_VARIANCE = 0.04
/** Exit-entry spec Fix 1: scored drop selection â€” acceptance and winner gates. */
const val DROP_SCORE_ACCEPT = 0.45
const val DROP_SCORE_BEST = 0.55
/** Exit-entry spec Fix 1: drop-zone position bonus/penalty. */
const val DROP_SCORE_POSITION_BONUS = 0.15
const val DROP_SCORE_POSITION_OK = 0.05
const val DROP_SCORE_POSITION_PENALTY = -0.20
/** Exit-entry spec Fix 6: techno branch (ELECTRONIC + bpm>=130). */
const val DROP_TECHNO_BPM = 130.0
const val DROP_TECHNO_CENTROID_HZ = 2000.0
const val DROP_TECHNO_RMS_BOOST = 1.10
const val DROP_TECHNO_SUSTAIN_STEPS = 8
const val DROP_HOUSE_CENTROID_HZ = 2500.0
const val DROP_OTHER_CENTROID_HZ = 2300.0
const val DROP_SUSTAIN_STEPS = 2
const val DROP_CENTROID_BUILD_FRACTION = 0.60
/** Exit-entry spec Fix 2: drop play-through before a post-drop exit. */
const val MIN_DROP_PLAY_THROUGH_PHRASES = 2.0
/** Exit-entry spec Fix 3: buildupâ†”drop distance validation. */
const val MIN_BUILDUP_TO_DROP_SECONDS = 8.0
const val MAX_BUILDUP_TO_DROP_SECONDS = 90.0
/** Exit-entry spec Fix 4: vocal check on the mixset entry. */
const val MIXSET_ENTRY_VOCAL_CHECK_BARS = 4
const val MIXSET_ENTRY_VOCAL_ADVANCE_BARS = 8
const val MIXSET_ENTRY_VOCAL_THRESHOLD = 0.45
const val MIXSET_ENTRY_VOCAL_ACCEPT = 0.40
/** Exit-entry spec Fix 7: late-drop window push band. */
const val LATE_DROP_WINDOW_LOW = 0.72
const val LATE_DROP_WINDOW_HIGH = 0.82

/** v2 Â§8: replaces the unbounded buildup walk; 90 s = ~4 phrases. */
const val MIXSET_BUILDUP_MAX_SECONDS = 90.0
/** v2 Â§8b: cooldown window must be falling, not flat or rising. */
const val MIXSET_COOLDOWN_SLOPE_THRESHOLD = -0.002

/** v2 Â§9a: silence-gap cutter thresholds. */
const val SILENCE_RMS_THRESHOLD = 0.08
const val SILENCE_MIN_DURATION_SECONDS = 0.6
/** v2 Â§9a: PLAIN_DISSOLVE reverb wet on the outgoing track. Voiced under the DSP cap. */
const val PLAIN_DISSOLVE_REVERB_WET = 0.30
/** v2 Â§9b: heavy-clash forced echo/reverb amounts (voiced under the DSP caps). */
const val HEAVY_CLASH_REVERB_WET = 0.34
const val HEAVY_CLASH_ECHO_AMOUNT = 0.50
/** v2 Â§9b: reverb freeze point after transition start. */
const val HEAVY_CLASH_FREEZE_OFFSET_SEC = 3.5
/** Automix reverb: echo-out wash voices the DSP max (ReverbProcessor MAX_WET = 0.34). */
const val ECHO_OUT_REVERB_WET = 0.34
/** Automix reverb: beat-matched blends carry a bed of reverb under the EQ swap. */
const val BLEND_REVERB_WET = 0.25

/** Non-finite guards, matching the desktop planner's coercion of `NaN`/`Infinity` to zero. */
internal fun Double.orZero(): Double = if (isFinite()) this else 0.0

internal fun Double?.orZero(): Double = if (this != null && isFinite()) this else 0.0

internal fun clamp(value: Double, min: Double, max: Double): Double =
    if (value.isFinite()) max(min, min(max, value)) else min

/**
 * Halves or doubles [incomingBpm] until it is as close as possible to
 * [outgoingBpm], the way a DJ counts a 63 BPM track against a 126 BPM one.
 */
fun alignTempoOctave(outgoingBpm: Double, incomingBpm: Double): Double {
    if (outgoingBpm <= 0 || incomingBpm <= 0) return incomingBpm
    var aligned = incomingBpm
    while (aligned / outgoingBpm > 1.5) aligned /= 2
    while (aligned / outgoingBpm < 0.67) aligned *= 2
    return aligned
}

/**
 * Mean vocal activity over [start]..[end] on a track's own timeline, or null
 * when the analysis carries no usable mask there. The mask is indexed against
 * [TrackAnalysis.energyCurve] times.
 */
fun vocalActivityBetween(analysis: TrackAnalysis, start: Double, end: Double): Double? {
    val mask = analysis.vocalActivityMask
    val curve = analysis.energyCurve
    if (mask.isEmpty() || mask.size != curve.size || end <= start) return null
    var sum = 0.0
    var count = 0
    for (index in mask.indices) {
        val time = curve[index].time
        if (!time.isFinite() || time < start || time > end) continue
        val value = mask[index]
        if (!value.isFinite()) continue
        sum += value
        count += 1
    }
    return if (count > 0) sum / count else null
}

/**
 * First instant at/after [fromSec] where the vocal mask stays active for
 * [minActiveSec] (0.5s default â€” one hot frame never trips it), or null when
 * no vocal starts before [untilSec] or the mask is unusable. Pure scan of the
 * analyzer mask â€” lets ARM time B's mid-delay to B's actual vocal phrase
 * instead of a fixed 16-beat window.
 */
fun firstVocalStartSec(
    analysis: TrackAnalysis,
    fromSec: Double,
    untilSec: Double,
    minActiveSec: Double = 0.5,
): Double? {
    val mask = analysis.vocalActivityMask
    val curve = analysis.energyCurve
    if (mask.isEmpty() || mask.size != curve.size || untilSec <= fromSec) return null
    var runStart: Double? = null
    var runEnd = 0.0
    for (index in mask.indices) {
        val time = curve[index].time
        if (!time.isFinite() || time < fromSec || time > untilSec) continue
        val active = mask[index].isFinite() && mask[index] >= VOCAL_ACTIVE_THRESHOLD
        if (active) {
            if (runStart == null) runStart = time
            runEnd = time
            if (runEnd - runStart >= minActiveSec) return runStart
        } else {
            runStart = null
        }
    }
    return null
}

/**
 * First instant at/after [fromSec] where the vocal mask stays quiet for
 * [quietSec] (2s default â€” a breath between phrases), or null when the track
 * sings wall-to-wall before [untilSec] or the mask is unusable. Pure scan â€”
 * lets ARM end A's duck zone at A's actual vocal phrase end.
 */
fun firstQuietGapSec(
    analysis: TrackAnalysis,
    fromSec: Double,
    untilSec: Double,
    quietSec: Double = 2.0,
): Double? {
    val mask = analysis.vocalActivityMask
    val curve = analysis.energyCurve
    if (mask.isEmpty() || mask.size != curve.size || untilSec <= fromSec) return null
    var runStart: Double? = null
    var runEnd = 0.0
    for (index in mask.indices) {
        val time = curve[index].time
        if (!time.isFinite() || time < fromSec || time > untilSec) continue
        val quiet = !mask[index].isFinite() || mask[index] < VOCAL_ACTIVE_THRESHOLD
        if (quiet) {
            if (runStart == null) runStart = time
            runEnd = time
            if (runEnd - runStart >= quietSec) return runStart
        } else {
            runStart = null
        }
    }
    return null
}

/**
 * Both windows measurably singing at once. Null means "no evidence", which
 * never blocks; absence of a mask is not absence of a vocal, but acting on it
 * would punish every track a fallback analyzer handled.
 */
fun isVocalClash(outgoingActivity: Double?, incomingActivity: Double?): Boolean =
    outgoingActivity != null &&
        incomingActivity != null &&
        outgoingActivity >= VOCAL_ACTIVE_THRESHOLD &&
        incomingActivity >= VOCAL_ACTIVE_THRESHOLD

/**
 * How strongly two windows sing over each other: 0 for nothing worth acting on,
 * 1 for two fully vocal passages landing on one another.
 *
 * [isVocalClash]'s graded counterpart, and the reason for having both. A boolean
 * is the right shape for a routing decision â€” shorten the overlap or don't â€” but
 * it is the wrong shape for the renderer, which has to decide *how hard* to pull
 * the two voices apart. A pair scraping over the threshold and two choruses
 * colliding are the same `true` and want visibly different treatment.
 *
 * Governed by the quieter of the two, because a clash needs both sides: an
 * instrumental passage under a vocal is not a clash however loud the vocal is,
 * and taking a mean would let one strong side manufacture one.
 *
 * Null on either side is no evidence and answers zero, which leaves whatever the
 * caller would have done anyway. Absence of a mask is not absence of a vocal â€”
 * but acting on it would filter every track a fallback analyzer handled.
 */
fun vocalOverlapAmount(outgoingActivity: Double?, incomingActivity: Double?): Double {
    if (outgoingActivity == null || incomingActivity == null) return 0.0
    val both = min(outgoingActivity, incomingActivity)
    if (both <= VOCAL_ACTIVE_THRESHOLD) return 0.0
    return ((both - VOCAL_ACTIVE_THRESHOLD) / (1.0 - VOCAL_ACTIVE_THRESHOLD)).coerceIn(0.0, 1.0)
}

/**
 * The fraction of a planned overlap where **both** tracks are singing at the
 * same instant, or null when either side has no mask.
 *
 * Why this exists alongside [vocalActivityBetween]: that one answers with a
 * *mean* over the window, and a mean is the wrong statistic for a clash. Twelve
 * seconds holding three seconds of vocal and nine of instrumental averages well
 * under [VOCAL_ACTIVE_THRESHOLD] and reads as clear â€” while the listener plainly
 * hears two voices for those three seconds. Every clash short of about half the
 * overlap was being averaged into silence, which is why a transition could be
 * planned as clean and still land two vocals on top of each other.
 *
 * Instant by instant instead. The outgoing track's own energy-curve samples are
 * the clock; each is mapped onto the incoming timeline through [rate], because a
 * stretched incoming track covers proportionally more of its own timeline in the
 * same wall-clock second. Unmeasured regions sit at the analyzer's neutral 0.5,
 * below the threshold, so they count as "not singing" rather than as evidence.
 *
 * Both curves are time-ascending, so the incoming index only ever moves forward:
 * this is one pass over each, not a search per sample.
 */
fun simultaneousVocalFraction(
    outgoing: TrackAnalysis,
    incoming: TrackAnalysis,
    outStart: Double,
    outEnd: Double,
    inStart: Double,
    rate: Double,
): Double? {
    val outMask = outgoing.vocalActivityMask
    val outCurve = outgoing.energyCurve
    val inMask = incoming.vocalActivityMask
    val inCurve = incoming.energyCurve
    if (outMask.isEmpty() || outMask.size != outCurve.size) return null
    if (inMask.isEmpty() || inMask.size != inCurve.size) return null
    if (outEnd <= outStart) return null
    val step = if (rate.isFinite() && rate > 0) rate else 1.0

    var inIndex = 0
    var both = 0
    var total = 0
    for (index in outMask.indices) {
        val time = outCurve[index].time
        if (!time.isFinite() || time < outStart) continue
        if (time > outEnd) break
        total += 1
        if (outMask[index] < VOCAL_ACTIVE_THRESHOLD) continue
        val target = inStart + (time - outStart) * step
        while (inIndex + 1 < inCurve.size && inCurve[inIndex + 1].time <= target) inIndex += 1
        if (inMask[inIndex] >= VOCAL_ACTIVE_THRESHOLD) both += 1
    }
    return if (total > 0) both.toDouble() / total else null
}

/**
 * How much simultaneous vocal a transition may carry before it counts as a
 * clash worth reshaping the overlap for.
 *
 * Not zero. A mask is a model's estimate sampled on a coarse grid, and both
 * edges of a vocal phrase are soft, so demanding literal zero would refuse
 * overlaps that sound clean and spend the fade budget chasing a rounding error.
 * A twentieth of the window is roughly one energy-curve sample either side of a
 * boundary.
 */
const val VOCAL_CLASH_TOLERANCE = 0.05

/**
 * Seconds of audible music in [start]..[end] on a track's own timeline,
 * judged against the track's own loud-end reference so the measure is
 * independent of how the analyzer scales energy. Returns null when there is
 * no usable curve.
 */
fun audibleSecondsBetween(analysis: TrackAnalysis, start: Double, end: Double): Double? {
    val curve = analysis.energyCurve
    if (curve.size < 2 || end <= start) return null
    val energies = curve.map { it.energy }.filter { it.isFinite() && it >= 0 }.sorted()
    if (energies.isEmpty()) return null
    val reference = energies[floor((energies.size - 1) * 0.85).toInt()].orZero()
    if (reference <= 0) return 0.0
    val threshold = reference * AUDIBLE_ENERGY_FRACTION
    val first = curve.first().time
    val last = curve.last().time
    if (!first.isFinite() || !last.isFinite() || last <= first) return null
    val sampleSeconds = (last - first) / (curve.size - 1)
    var audible = 0.0
    for (point in curve) {
        if (!point.time.isFinite() || point.time < start || point.time > end) continue
        if (point.energy >= threshold) audible += sampleSeconds
    }
    return audible
}

/**
 * The earliest point the analysis claims the track makes sound.
 *
 * [TrackAnalysis.firstBeat] is not nullable the way the other two are, and
 * the analyzer uses 0.0 as its "nothing was measured" fallback. Counting that
 * zero as a real audible start pins this to 0 for any track without a beat
 * grid, which silently overrides a measured `audibleStartTime` and tells the
 * planner the whole head of the track is intro it can fade across.
 */
internal fun audibleStartOf(analysis: TrackAnalysis): Double {
    val firstBeat = analysis.firstBeat.takeIf { it.isFinite() && it > 0 }
    val candidates = listOfNotNull(analysis.audibleStartTime, analysis.pickupTime, firstBeat)
        .filter { it.isFinite() && it >= 0 }
    return candidates.minOrNull() ?: 0.0
}

/**
 * Phase A2: first-sound search. The native sustain gate (4/6 active windows +
 * 1.45Ã— peak) misses a quiet cold breath before the beat, so `audible_start`
 * can land seconds late and the overlap renders a silent bed. Scan the coarse
 * persisted curve forward from 0 while under `claimed`: the first point at or
 * above the audible threshold, sustained for 2 consecutive samples and at or
 * above half the track mean, wins. Clamped to [0, claimed] â€” only ever moves
 * earlier-or-equal, never past the claimed onset into music.
 */
internal fun refinedStartPoint(analysis: TrackAnalysis, claimed: Double): Double {
    if (!claimed.isFinite() || claimed <= 0) return claimed.coerceAtLeast(0.0)
    val curve = analysis.energyCurve
    if (curve.size < 2) return claimed
    val energies = curve.map { it.energy }.filter { it.isFinite() && it >= 0 }.sorted()
    if (energies.isEmpty()) return claimed
    val reference = energies[floor((energies.size - 1) * 0.85).toInt()].orZero()
    if (reference <= 0) return claimed
    val threshold = reference * AUDIBLE_ENERGY_FRACTION
    val mean = energies.average().takeIf { it.isFinite() && it > 0 } ?: return claimed
    val gate = max(threshold, 0.5 * mean)
    var run = 0
    for (point in curve) {
        if (!point.time.isFinite() || point.time < 0 || point.time >= claimed) break
        if (point.energy.isFinite() && point.energy >= gate) {
            run++
            if (run >= 2) {
                val idx = curve.indexOf(point)
                val first = (curve.getOrNull(idx - 1)?.time ?: point.time)
                return first.coerceIn(0.0, claimed)
            }
        } else {
            run = 0
        }
    }
    return claimed
}

/** The value in [values] closest to [target] within [tolerance], or null when none qualifies. */
internal fun nearestValue(values: List<Double>, target: Double, tolerance: Double): Double? =
    values.filter { it.isFinite() && abs(it - target) <= tolerance }
        .minByOrNull { abs(it - target) }

/**
 * Ranks a track's analyzed mix-in candidates as entry points for a
 * transition, best first.
 *
 * Selection is a scoring problem, not a type lookup: the analyzer's own
 * score, the candidate type, downbeat alignment, whether there is any intro
 * before the point to bed under the outgoing track, and how vocal that intro
 * is all move a candidate up or down.
 */
fun rankMixInCandidates(analysis: TrackAnalysis, mixset: Boolean = false): List<RankedMixCandidate> {
    val candidates = analysis.mixInCandidates.filter { it.time.isFinite() && it.time >= 0 }
    if (candidates.isEmpty()) return emptyList()
    val beatSeconds = analysis.beatInterval.orZero()
        .takeIf { it > 0 }
        ?: if (analysis.bpm.orZero() > 0) 60 / analysis.bpm else 0.5
    val audibleStart = audibleStartOf(analysis)
    if (!mixset) {
        return candidates.map { candidate ->
            var rankScore = candidate.score.orZero() + (STOCK_MIX_IN_TYPE_WEIGHT[candidate.type] ?: 0.0)
            if (nearestValue(analysis.downbeats, candidate.time, beatSeconds / 2) != null) rankScore += 0.1
            // A cold open: nothing before the point to play underneath the outgoing track, so entering
            // here means starting the blend on the arrangement.
            if (candidate.time - audibleStart < beatSeconds * 4) rankScore -= 0.2
            // Prefer entries whose run-up is instrumental; an intro that already sings will sing over
            // the outgoing track for the whole pre-roll.
            val vocal = vocalActivityBetween(
                analysis,
                max(audibleStart, candidate.time - beatSeconds * 16),
                candidate.time,
            )
            if (vocal != null) rankScore += (0.5 - vocal) * 0.4
            RankedMixCandidate(
                time = candidate.time,
                score = candidate.score.orZero(),
                type = candidate.type,
                rankScore = rankScore,
            )
        }.sortedByDescending { it.rankScore }
    }
    val coldOpen = isColdOpen(analysis, audibleStart)
    return candidates.map { candidate ->
        var rankScore = candidate.score.orZero() + (MIX_IN_TYPE_WEIGHT[candidate.type] ?: 0.0)
        if (nearestValue(analysis.downbeats, candidate.time, beatSeconds / 3) != null) rankScore += 0.25
        if (coldOpen) {
            if (candidate.time - audibleStart < beatSeconds * 2) rankScore += 0.1
        } else {
            // A cold open: nothing before the point to play underneath the outgoing track, so entering
            // here means starting the blend on the arrangement.
            if (candidate.time - audibleStart < beatSeconds * 4) rankScore -= 0.2
        }
        // Prefer entries whose run-up is instrumental; an intro that already sings will sing over
        // the outgoing track for the whole pre-roll.
        val vocal = vocalActivityBetween(
            analysis,
            max(audibleStart, candidate.time - beatSeconds * 16),
            candidate.time,
        )
        if (vocal != null) {
            rankScore += (VOCAL_SOFT_PENALTY - vocal) * 0.4
            if (vocal > VOCAL_DISCARD_THRESHOLD) rankScore -= 1.0
        }
        RankedMixCandidate(
            time = candidate.time,
            score = candidate.score.orZero(),
            type = candidate.type,
            rankScore = rankScore,
        )
    }.sortedByDescending { it.rankScore }
}

/** Falls back to the scalar mix-out fields when the analysis carries no candidate list. */
private fun mixOutCandidatesOf(analysis: TrackAnalysis, contentEnd: Double): List<MixCandidate> {
    val supplied = analysis.mixOutCandidates.filter { it.time.isFinite() && it.time > 0 }
    val candidates = supplied.map {
        MixCandidate(time = it.time, score = it.score.orZero(), type = it.type)
    }.toMutableList()
    if (supplied.isEmpty()) {
        val mixOut = analysis.mixOutTime.orZero()
        val outroStart = analysis.outroStartTime.orZero()
        if (mixOut > 0 && mixOut < contentEnd - 1) {
            candidates += MixCandidate(mixOut, 0.95, "energy_cliff")
        }
        if (outroStart > 0 && outroStart < contentEnd - 1) {
            candidates += MixCandidate(outroStart, 0.9, "outro_start")
        }
    }
    // Vocals describe how the overlap should be shaped, not where the outgoing song stops. The
    // incoming instrumental runway can begin under an outgoing vocal; promoting vocal boundaries
    // to exit anchors waits for the easy gap (or skips the vocal tail entirely) instead of asking
    // the filter ride and gain curves to blend it. Only structural and energy candidates choose
    // the exit. The transition always has somewhere to end: where the content does.
    if (candidates.none { abs(it.time - contentEnd) < 0.05 }) {
        candidates += MixCandidate(contentEnd, 0.75, "content_end")
    }
    return candidates
}

/** Resolves the content end from the analysis and the caller's overrides, in priority order. */
private fun resolveContentEnd(analysis: TrackAnalysis, contentEnd: Double, duration: Double): Double =
    contentEnd.orZero().takeIf { it != 0.0 }
        ?: analysis.contentEndTime.orZero().takeIf { it != 0.0 }
        ?: duration.orZero().takeIf { it != 0.0 }
        ?: analysis.duration.orZero()

/**
 * Ranks a track's analyzed mix-out candidates as places for a transition to
 * end, best first.
 *
 * Candidates that would skip more than [MAX_DISCARDED_MUSIC_SECONDS] of
 * remaining music are dropped outright: how confidently the analyzer marked a
 * boundary is no argument for cutting a song short, and both an outro marker
 * and a mid-track silence gap will happily do exactly that. Silence is free,
 * so a genuine interior gap still wins the anchor it deserves.
 */
fun rankMixOutCandidates(
    analysis: TrackAnalysis,
    contentEnd: Double = 0.0,
    duration: Double = 0.0,
    allowedWindow: ClosedRange<Double>? = null,
    outroOnly: Boolean = false,
): List<RankedMixCandidate> {
    val end = resolveContentEnd(analysis, contentEnd, duration)
    if (end <= 0) return emptyList()
    if (!outroOnly && allowedWindow == null) {
        return mixOutCandidatesOf(analysis, end)
            .map { candidate ->
                val measured = audibleSecondsBetween(analysis, candidate.time, end)
                RankedMixCandidate(
                    time = candidate.time,
                    score = candidate.score,
                    type = candidate.type,
                    rankScore = candidate.score + (STOCK_MIX_OUT_TYPE_SCORE[candidate.type] ?: 0.0),
                    discardedMusicSeconds = measured ?: max(0.0, end - candidate.time),
                    measured = measured != null,
                )
            }
            .filter { it.discardedMusicSeconds <= MAX_DISCARDED_MUSIC_SECONDS }
            .sortedWith(compareByDescending<RankedMixCandidate> { it.rankScore }.thenByDescending { it.time })
    }
    val beatSeconds = analysis.beatInterval.orZero()
        .takeIf { it > 0 }
        ?: if (analysis.bpm.orZero() > 0) 60 / analysis.bpm else 0.5
    val base = mixOutCandidatesOf(analysis, end)
    val outroStart = analysis.outroStartTime.orZero()
    val scoped = if (outroOnly) {
        base.filter {
            it.type == "content_end" || it.type == "outro_start" ||
                (it.type == "energy_cliff" && outroStart > 0 && it.time >= outroStart)
        }
    } else {
        base
    }
    val augmented = if (allowedWindow != null && !outroOnly) {
        scoped + augmentMixOutCandidates(analysis, scoped, allowedWindow, beatSeconds)
    } else {
        scoped
    }
    val budget = if (outroOnly) MAX_DISCARDED_MUSIC_SECONDS
    else if (allowedWindow != null) BLUEPRINT_WINDOW_DISCARD_BUDGET else MAX_DISCARDED_MUSIC_SECONDS
    return augmented
        .filter { allowedWindow == null || it.time in allowedWindow }
        .map { candidate ->
            val measured = audibleSecondsBetween(analysis, candidate.time, end)
            // With no energy curve there is no way to tell skipped music from skipped silence, so
            // the raw gap is charged in full and the budget errs toward playing the track.
            val exitVocal = vocalActivityBetween(analysis, candidate.time - 1.0, candidate.time + 1.0)
            val windowVocal = vocalActivityBetween(
                analysis,
                candidate.time - VOCAL_EXIT_TIMING_WINDOW,
                candidate.time + VOCAL_EXIT_TIMING_WINDOW,
            )
            val effectiveVocal = maxOf(exitVocal ?: 0.0, windowVocal ?: 0.0)
            val hasMeasurement = exitVocal != null || windowVocal != null
            val vocalPenalty = when {
                !hasMeasurement -> 0.0
                effectiveVocal > VOCAL_DISCARD_THRESHOLD -> -1.0
                effectiveVocal > VOCAL_STRONG_ZONE ->
                    -(effectiveVocal - VOCAL_SOFT_PENALTY) * VOCAL_STRONG_MULTIPLIER
                effectiveVocal > VOCAL_SOFT_PENALTY ->
                    -(effectiveVocal - VOCAL_SOFT_PENALTY) * VOCAL_SOFT_MULTIPLIER
                else -> 0.0
            }
            val dropSec = firstDropSec(analysis)
            val phrase16 = phrase16Seconds(analysis)
            val playThrough =
                if (phrase16 != null && phrase16.isFinite() && phrase16 > 0) {
                    phrase16 * MIN_DROP_PLAY_THROUGH_PHRASES
                } else {
                    0.0
                }
            val inDrop = analysis.structureMap.any { label ->
                label.type == StructureSectionType.DROP &&
                    label.start.isFinite() &&
                    candidate.time >= label.start &&
                    candidate.time < label.start + analysis.beatInterval * 8
            }
            val breakBoost =
                if (inDrop) {
                    0.0
                } else if (candidate.type == "low_energy" && isInsideBreak(analysis, candidate.time) &&
                    (dropSec == null || !dropSec.isFinite() || candidate.time > dropSec + playThrough)
                ) {
                    LOW_ENERGY_BREAK_BOOST_SCORE - (MIX_OUT_TYPE_SCORE["low_energy"] ?: 0.0)
                } else {
                    0.0
                }
            RankedMixCandidate(
                time = candidate.time,
                score = candidate.score,
                type = candidate.type,
                rankScore = if (inDrop) {
                    -1.0
                } else {
                    candidate.score + (MIX_OUT_TYPE_SCORE[candidate.type] ?: 0.0) + vocalPenalty + breakBoost
                },
                discardedMusicSeconds = measured ?: max(0.0, end - candidate.time),
                measured = measured != null,
            )
        }
        .filter { it.discardedMusicSeconds <= budget }
        .sortedWith(compareByDescending<RankedMixCandidate> { it.rankScore }.thenByDescending { it.time })
}

/**
 * Blueprint Â§7 steps 3â€“4 and 6: the five lowest-energy points in [window]
 * plus one point just after every vocal phrase ends, each dropped when it
 * sits within 8 bars of an analyzer candidate or an earlier extra.
 */
private fun augmentMixOutCandidates(
    analysis: TrackAnalysis,
    existing: List<MixCandidate>,
    window: ClosedRange<Double>,
    beatSeconds: Double,
): List<MixCandidate> {
    val bar8 = (if (beatSeconds > 0) beatSeconds else 0.5) * 32
    val extras = mutableListOf<MixCandidate>()
    fun tooClose(time: Double): Boolean =
        existing.any { abs(it.time - time) < bar8 } || extras.any { abs(it.time - time) < bar8 }
    analysis.energyCurve
        .filter { it.time.isFinite() && it.energy.isFinite() && it.time in window }
        .sortedBy { it.energy }
        .forEach { point ->
            if (extras.size >= 5) return@forEach
            if (!tooClose(point.time)) extras += MixCandidate(point.time, 0.5, "low_energy")
        }
    val mask = analysis.vocalActivityMask
    val curve = analysis.energyCurve
    if (mask.size == curve.size && mask.isNotEmpty()) {
        for (i in 1 until mask.size) {
            val wasActive = mask[i - 1].isFinite() && mask[i - 1] >= VOCAL_ACTIVE_THRESHOLD
            val nowQuiet = !mask[i].isFinite() || mask[i] < VOCAL_ACTIVE_THRESHOLD
            if (wasActive && nowQuiet) {
                val exit = curve[i].time + VOCAL_EXIT_BUFFER_SECONDS
                if (exit.isFinite() && exit in window && !tooClose(exit)) {
                    extras += MixCandidate(exit, 0.6, "vocal_exit")
                }
            }
        }
    }
    return extras
}

/**
 * Where the outgoing track's transition ends: the best-ranked mix-out
 * candidate that stays inside the discarded-music budget, or the end of
 * content when none does.
 */
fun resolveMixOutAnchor(
    analysis: TrackAnalysis,
    contentEnd: Double = 0.0,
    duration: Double = 0.0,
    allowedWindow: ClosedRange<Double>? = null,
    fallbackTime: Double? = null,
    outroOnly: Boolean = false,
): MixOutAnchor {
    val end = resolveContentEnd(analysis, contentEnd, duration)
    val best = rankMixOutCandidates(analysis, end, duration, allowedWindow, outroOnly).firstOrNull()
    if (best != null) {
        return MixOutAnchor(
            time = best.time,
            type = best.type,
            discardedMusicSeconds = best.discardedMusicSeconds,
        )
    }
    val fallback = fallbackTime?.takeIf { it.isFinite() && it > 0 && it < end }
    if (fallback != null) {
        val discarded = audibleSecondsBetween(analysis, fallback, end) ?: max(0.0, end - fallback)
        return MixOutAnchor(time = fallback, type = "blueprint_fallback", discardedMusicSeconds = discarded)
    }
    return MixOutAnchor(time = end, type = "content_end", discardedMusicSeconds = 0.0)
}

/**
 * Blueprint Â§5.6 energy gate. The blueprint assumes a 0..1 profile with a
 * 0.70 high-energy line; this curve is raw RMS, so above-average reads as
 * high instead. Same question, honest scale.
 */
fun isHighEnergyAt(analysis: TrackAnalysis, time: Double): Boolean {
    val energy = energyAt(analysis, time) ?: return false
    val mean = meanEnergy(analysis) ?: return false
    if (mean <= 0) return false
    return energy >= mean * 1.1
}

/**
 * Booth backspin gate: peak energy, not merely high energy. Both decks must
 * be at their summit (â‰¥1.4Ã— mean AND â‰¥80% of the track max) for a backspin
 * to fire â€” a spin out of (or into) a merely above-average passage reads as
 * a gimmick and breaks the emotional arc. Null/empty curves are no evidence
 * and answer false, never true.
 */
fun isPeakEnergyAt(analysis: TrackAnalysis, time: Double): Boolean {
    val energy = energyAt(analysis, time) ?: return false
    val mean = meanEnergy(analysis) ?: return false
    if (mean <= 0) return false
    val max = maxEnergy(analysis) ?: return false
    if (max <= 0) return false
    return energy >= mean * 1.4 && energy >= max * 0.8
}

/**
 * Decides how ambitious a transition the stored analysis supports.
 *
 * Reasons are ordered most-disqualifying first so callers can surface
 * `reasons.first()` as the routing verdict.
 */
fun assessTransitionTier(
    analysis: TrackAnalysis,
    nextAnalysis: TrackAnalysis,
    mixset: Boolean = false,
): TransitionPolicyVerdict {
    val outgoingBpm = analysis.bpm.orZero()
    val incomingBpm = nextAnalysis.bpm.orZero()
    val outgoingConfidence = analysis.beatConfidence.orZero()
    val incomingConfidence = nextAnalysis.beatConfidence.orZero()
    val floorConfidence = min(outgoingConfidence, incomingConfidence)
    val reasons = mutableListOf<String>()

    if (outgoingBpm < MIN_BPM || outgoingBpm > MAX_BPM) reasons += "outgoing-tempo"
    if (incomingBpm < MIN_BPM || incomingBpm > MAX_BPM) reasons += "incoming-tempo"
    if (reasons.isNotEmpty()) {
        return TransitionPolicyVerdict(TransitionTier.PLAIN_CROSSFADE, reasons, floorConfidence)
    }

    if (outgoingConfidence < MIN_DJ_CONFIDENCE && incomingConfidence < MIN_DJ_CONFIDENCE) {
        return TransitionPolicyVerdict(
            TransitionTier.PLAIN_CROSSFADE,
            listOf("beat-confidence"),
            floorConfidence,
        )
    }

    if (!mixset) {
        val stretchRatio = outgoingBpm / alignTempoOctave(outgoingBpm, incomingBpm)
        if (abs(stretchRatio - 1) > STOCK_MAX_STRETCH_DEVIATION) reasons += "tempo-distance"
        if (outgoingConfidence < MIN_BEATMATCH_CONFIDENCE || incomingConfidence < MIN_BEATMATCH_CONFIDENCE) {
            reasons += "beat-confidence"
        }
        return TransitionPolicyVerdict(
            tier = if (reasons.isEmpty()) TransitionTier.BEATMATCHED else TransitionTier.DJ_ASSISTED,
            reasons = reasons,
            beatConfidence = floorConfidence,
        )
    }

    val candidate = findBestCandidate(analysis, nextAnalysis)
    val matchedRatio = candidate?.ratio
    if (matchedRatio == null) reasons += "tempo-distance"
    if (outgoingConfidence < MIN_BEATMATCH_CONFIDENCE || incomingConfidence < MIN_BEATMATCH_CONFIDENCE) {
        reasons += "beat-confidence"
    }
    if (matchedRatio != null && matchedRatio != 1.0) reasons += "harmonic-ratio"

    val halfLocked = mixset && AppSettings.automixHalfTempoLock.value
    val tier = when {
        reasons.isEmpty() -> TransitionTier.BEATMATCHED
        reasons.size == 1 && reasons[0] == "harmonic-ratio" && !halfLocked -> TransitionTier.HALF_TIME
        else -> TransitionTier.DJ_ASSISTED
    }
    return TransitionPolicyVerdict(
        tier = tier,
        reasons = reasons,
        beatConfidence = floorConfidence,
        matchedRatio = matchedRatio ?: 1.0,
        candidateShiftSemitones = candidate?.keyShiftSemitones ?: 0,
    )
}


/** Blueprint Â§6 weights: tempo matters most, vocals least. */
const val WEIGHT_BPM_SCORE = 0.30
const val WEIGHT_KEY_SCORE = 0.25
const val WEIGHT_ENERGY_SCORE = 0.20
const val WEIGHT_STRUCTURE_SCORE = 0.15
const val WEIGHT_VOCAL_SCORE = 0.10

/** Blueprint Â§6 decision thresholds on [CompatibilityScore.overall]. */
const val SCORE_EXCELLENT = 0.80
const val SCORE_GOOD = 0.60
const val SCORE_ACCEPTABLE = 0.40

/**
 * Measured-F0 confidence below this means the pitch tracker abstains and a
 * key shift stands on the detected key alone. Mirrors the gate the tracker
 * itself decodes with.
 */
const val TRUSTED_PITCH_CONFIDENCE = 0.5
/**
 * Full-audit Phase 2: key-shift bar. Same 0.5 value as
 * [TRUSTED_PITCH_CONFIDENCE] but key-named â€” the old reuse read as "pitch
 * gates key" and hid the three-rung ladder: trustedKey 0.25 (proven-clash
 * gate) < scoring 0.30 (neutral floor) < shift 0.5 (retune). Values
 * unchanged, names honest.
 */
const val MIN_KEY_CONFIDENCE_FOR_SHIFT = 0.5

/**
 * Blueprint Â§5.5: a transition may not start inside a vocal phrase, nor
 * within this long after one ends.
 */
const val VOCAL_EXIT_BUFFER_SECONDS = 0.5

/** v2 Â§5a: accepted harmonic tempo ratios. 1:1, 2:1, 1:2,
 * 3:2, 2:3, 4:3, 3:4 â€” v1 handled only the octave pair, so a 90 BPM hip-hop
 * track against 128 BPM house fell to PLAIN despite a clean 3:2 match.
 * Multi-candidate Â§Q1: 5:4 / 4:5 rescue genre-edge pairs (80â†”100, 96â†”120...).
 * Named consts declared ONCE and shared by the array and the cap map below â€”
 * Double map lookup is bit-equality, so re-expressed literals would miss. */
private const val RATIO_1_1 = 1.0
private const val RATIO_2_1 = 2.0
private const val RATIO_1_2 = 0.5
private const val RATIO_3_2 = 1.5
private const val RATIO_2_3 = 2.0 / 3.0
private const val RATIO_4_3 = 4.0 / 3.0
private const val RATIO_3_4 = 0.75
private const val RATIO_5_4 = 1.25
private const val RATIO_4_5 = 0.8

val SUPPORTED_BPM_RATIOS = doubleArrayOf(
    RATIO_1_1, RATIO_2_1, RATIO_1_2,
    RATIO_3_2, RATIO_2_3,
    RATIO_4_3, RATIO_3_4,
    RATIO_5_4, RATIO_4_5,
)

/** Multi-candidate Â§Q1: per-ratio deviation caps. Tempo-transparency fix: all
 * ratios share the Â±2 % inaudibility band â€” wider pairings fall back to a
 * wash at rate 1.0 instead of a stretched beatmatch. Ratios absent from
 * this map fall back to [MAX_STRETCH_DEVIATION]. */
val RATIO_DEVIATION_CAP: Map<Double, Double> = mapOf(
    RATIO_1_1 to 0.02,
    RATIO_2_1 to 0.02,
    RATIO_1_2 to 0.02,
    RATIO_3_2 to 0.02,
    RATIO_2_3 to 0.02,
    RATIO_4_3 to 0.02,
    RATIO_3_4 to 0.02,
    RATIO_5_4 to 0.02,
    RATIO_4_5 to 0.02,
)

/**
 * v2 Â§5a: first supported ratio bringing bpmA onto bpmB within
 * [MAX_STRETCH_DEVIATION], or null when no clean ratio match exists.
 */
fun matchHarmonicRatio(outgoingBpm: Double, incomingBpm: Double): Double? {
    if (outgoingBpm <= 0 || incomingBpm <= 0) return null
    for (ratio in SUPPORTED_BPM_RATIOS) {
        val deviation = abs(outgoingBpm * ratio - incomingBpm) / incomingBpm
        if (deviation <= MAX_STRETCH_DEVIATION) return ratio
    }
    return null
}

/** Blueprint Â§5.1 Rule 1. Scores only ratios inside their per-ratio cap; the best wins.
 * Formula shape kept ((1-diff*10)) so all downstream thresholds (matrix 0.50/0.60/0.70,
 * echoAmount, overall weight) behave exactly as before inside 0.03; only the 0.03-0.05
 * dead band changes (0 â†’ 0.5-0.7), rescuing pairs the tier path already accepts. */
fun bpmScore(outgoingBpm: Double, incomingBpm: Double): Double {
    if (outgoingBpm <= 0 || incomingBpm <= 0) return 0.0
    var best = 0.0
    for (ratio in SUPPORTED_BPM_RATIOS) {
        val cap = RATIO_DEVIATION_CAP[ratio] ?: MAX_STRETCH_DEVIATION
        val diff = abs(outgoingBpm * ratio - incomingBpm) / incomingBpm
        if (diff <= cap) {
            best = max(best, (1.0 - diff * 10.0).coerceIn(0.0, 1.0))
        }
    }
    return best
}

private val PITCH_CLASS_INDEX = mapOf(
    "C" to 0, "Câ™¯" to 1, "Dâ™­" to 1, "D" to 2, "Dâ™¯" to 3, "Eâ™­" to 3,
    "E" to 4, "F" to 5, "Fâ™¯" to 6, "Gâ™­" to 6, "G" to 7, "Gâ™¯" to 8,
    "Aâ™­" to 8, "A" to 9, "Aâ™¯" to 10, "Bâ™­" to 10, "B" to 11,
)

/**
 * The native detector emits ASCII accidentals ("C# minor", "Bb major") while
 * every Kotlin table reads Unicode ("Câ™¯", "Bâ™­") â€” and JNI documents its
 * strings as ASCII-only literals, so Unicode can never arrive. Canonicalize
 * at the single lookup point all tables share: without it every sharp/flat
 * key in production parses to null and silently disables key scoring,
 * shifting and the pitch veto.
 */
fun canonicalKeyRoot(raw: String?): String? {
    if (raw.isNullOrEmpty()) return null
    if (raw.endsWith("#")) return raw.dropLast(1) + "â™¯"
    if (raw.length == 2 && raw[0] in 'A'..'G' && raw[1] == 'b') return "${raw[0]}â™­"
    return raw
}

/**
 * Blueprint Â§5.2 mapping, derived arithmetically rather than tabulated: walk
 * the circle of fifths from C (= 8B) in semitone steps of a fifth. Minor takes
 * its relative major's number (A minor -> 8A via C major).
 *
 * @return number 1..12 plus minor flag, or null when the key is unparseable.
 */
fun camelotOf(key: String): Pair<Int, Boolean>? {
    val parts = key.trim().split(' ')
    val index = PITCH_CLASS_INDEX[canonicalKeyRoot(parts.firstOrNull())] ?: return null
    val minor = when (parts.getOrNull(1)?.lowercase()) {
        "minor", "m" -> true
        "major", "maj", "" -> false
        null -> false
        else -> return null
    }
    val majorIndex = if (minor) (index + 3) % 12 else index
    val fifthSteps = (7 * majorIndex) % 12
    return (((7 + fifthSteps) % 12) + 1) to minor
}

private fun keyScoreOf(
    leftNumber: Int,
    leftMinor: Boolean,
    rightNumber: Int,
    rightMinor: Boolean,
): Double {
    if (leftNumber == rightNumber && leftMinor == rightMinor) return 1.0
    if (leftNumber == rightNumber) return 0.85
    val step = min(abs(leftNumber - rightNumber), 12 - abs(leftNumber - rightNumber))
    if (step == 1 && leftMinor == rightMinor) return 0.75
    if (step == 1) return 0.45
    return 0.0
}

/**
 * Blueprint Â§5.2 compatibility table. An unparseable key is no key at all
 * (0.0); a key without a parseable mode keeps its number but forfeits the
 * relative-major credit.
 */
fun keyScore(leftKey: String, rightKey: String): Double {
    val left = camelotOf(leftKey)
    val right = camelotOf(rightKey)
    if (left == null || right == null) return 0.0
    return keyScoreOf(left.first, left.second, right.first, right.second)
}

/**
 * Blueprint Â§5.2 pitch-shift rule: the signed semitone shift of the incoming
 * track's key (positive = up) that first reaches Adjacent (0.75) or better,
 * capped at Â±[MAX_KEY_SHIFT_SEMITONES]. Zero when already there, or when no
 * shift inside the cap gets there â€” the caller must mask with a filter sweep
 * instead of shifting further.
 */
const val MAX_KEY_SHIFT_SEMITONES = 2

fun semitonesToShift(fromKey: String, toKey: String): Int {
    val from = camelotOf(fromKey) ?: return 0
    val to = camelotOf(toKey) ?: return 0
    if (keyScoreOf(from.first, from.second, to.first, to.second) >= 0.75) return 0
    val toIndex = PITCH_CLASS_INDEX[canonicalKeyRoot(toKey.trim().split(' ').firstOrNull())] ?: return 0
    for (magnitude in 1..MAX_KEY_SHIFT_SEMITONES) {
        for (shift in listOf(magnitude, -magnitude)) {
            val shifted = (toIndex + shift + 12) % 12
            if (keyScoreOf(from.first, from.second, camelotMajorNumber(shifted, to.second), to.second) >= 0.75) {
                return shift
            }
        }
    }
    return 0
}

private fun camelotMajorNumber(pitchIndex: Int, minor: Boolean): Int {
    val majorIndex = if (minor) (pitchIndex + 3) % 12 else pitchIndex
    return (((7 + (7 * majorIndex) % 12) % 12) + 1)
}

/** Pitch-class index (C = 0) of a detected [key], or null when unparseable. */
fun keyRootIndex(key: String): Int? =
    PITCH_CLASS_INDEX[canonicalKeyRoot(key.trim().split(' ').firstOrNull())]

/**
 * Whether a trusted measured median F0 contradicts the incoming track's own
 * detected key badly enough to cancel its pitch shift: more than three
 * semitones from the key root means the key detector, not the singer, is
 * probably wrong, and shifting on a wrong key lands in a worse one.
 *
 * Returns false whenever there is nothing to contradict with â€” unmeasured
 * median, unparseable key â€” so a missing pitch track changes nothing.
 */
fun pitchVetoesShift(medianHz: Double, detectedKey: String): Boolean {
    if (medianHz <= 0 || !medianHz.isFinite()) return false
    val root = keyRootIndex(detectedKey) ?: return false
    val midi = (69 + 12 * log2(medianHz / 440.0)).roundToInt()
    val pitchClass = ((midi % 12) + 12) % 12
    val distance = min((pitchClass - root + 12) % 12, (root - pitchClass + 12) % 12)
    return distance > 3
}


/** Candidate weights: tempo dominates, key is secondary, stretch is a mild tie-break. */
const val CANDIDATE_WEIGHT_BPM = 0.60
const val CANDIDATE_WEIGHT_KEY = 0.30
const val CANDIDATE_WEIGHT_STRETCH = 0.10
const val CANDIDATE_STRETCH_PENALTY_RATE = 0.12

/** One tempoÃ—shift hypothesis for a pair. Plan-time only; never stored. */
data class PairCandidate(
    val ratio: Double,
    val diff: Double,
    val deviationCap: Double,
    val keyShiftSemitones: Int,
    val keyFitScore: Double,
    val candidateScore: Double,
)

/** Canonical sharp-spelled root per pitch-class, parallel to PITCH_CLASS_INDEX
 * (spelling only feeds re-parse by camelotOf, which accepts both). */
private val CANONICAL_ROOTS = arrayOf(
    "C", "Câ™¯", "D", "Dâ™¯", "E", "F", "Fâ™¯", "G", "Gâ™¯", "A", "Aâ™¯", "B",
)

/**
 * Key string resulting from pitch-shifting [key] by [semitones] half-steps.
 * Mode is preserved, only the root moves. Null when blank/unparseable â€” the
 * caller then falls back to neutral key fit. Canonicalizes ASCII accidentals
 * ("C#") through [canonicalKeyRoot] first: raw-lowercase lookup would miss.
 */
fun keyAtSemitones(key: String, semitones: Int): String? {
    if (key.isBlank()) return null
    val parts = key.trim().split(' ')
    val root = parts.firstOrNull() ?: return null
    val mode = parts.getOrNull(1)?.lowercase()?.let {
        when (it) {
            "minor", "m" -> "minor"
            else -> "major"
        }
    } ?: "major"
    val pc = PITCH_CLASS_INDEX[canonicalKeyRoot(root)] ?: return null
    val shiftedRoot = CANONICAL_ROOTS[((pc + semitones) % 12 + 12) % 12]
    return "$shiftedRoot $mode"
}

fun scorePairCandidate(
    ratio: Double,
    diff: Double,
    cap: Double,
    keyFit: Double,
): Double {
    val bpmFit = (1.0 - diff / cap).coerceIn(0.0, 1.0)
    val stretchPenalty = (abs(1.0 - ratio) * CANDIDATE_STRETCH_PENALTY_RATE).coerceIn(0.0, 0.15)
    return bpmFit * CANDIDATE_WEIGHT_BPM + keyFit * CANDIDATE_WEIGHT_KEY +
        (1.0 - stretchPenalty) * CANDIDATE_WEIGHT_STRETCH
}

/**
 * Best (ratio Ã— shift) hypothesis for a pair, or null when no ratio fits any
 * per-ratio cap. The incoming key is shifted (never the outgoing); each shift
 * is veto-checked against the incoming vocal median. Shift 0 is always
 * eligible with the raw key score. Raw key scoring is untouched â€” the
 * candidate score only picks the ratio and the applied shift.
 */
fun findBestCandidate(
    analysisA: TrackAnalysis,
    analysisB: TrackAnalysis,
): PairCandidate? {
    val bpmA = analysisA.bpm.orZero()
    val bpmB = analysisB.bpm.orZero()
    if (bpmA <= 0 || bpmB <= 0) return null
    var best: PairCandidate? = null
    for (ratio in SUPPORTED_BPM_RATIOS) {
        val cap = RATIO_DEVIATION_CAP[ratio] ?: MAX_STRETCH_DEVIATION
        val diff = abs(bpmA * ratio - bpmB) / bpmB
        if (diff > cap) continue
        for (shift in -MAX_KEY_SHIFT_SEMITONES..MAX_KEY_SHIFT_SEMITONES) {
            val shiftedKeyB = if (shift == 0) analysisB.key.ifBlank { null }
                else keyAtSemitones(analysisB.key, shift)
            val keysConfident = analysisA.keyConfidence.orZero() >= MIN_KEY_CONFIDENCE_FOR_SCORING &&
                analysisB.keyConfidence.orZero() >= MIN_KEY_CONFIDENCE_FOR_SCORING
            val rawKeyFit: Double = when {
                analysisA.key.isBlank() || shiftedKeyB == null || !keysConfident -> NEUTRAL_KEY_SCORE_BELOW_CONF
                else -> keyScore(analysisA.key, shiftedKeyB)
            }
            if (shift != 0 && shiftedKeyB != null &&
                analysisB.pitchConfidence >= TRUSTED_PITCH_CONFIDENCE &&
                pitchVetoesShift(analysisB.vocalPitchMedianHz, shiftedKeyB)
            ) continue
            val score = scorePairCandidate(ratio, diff, cap, rawKeyFit)
            if (best == null || score > best.candidateScore) {
                best = PairCandidate(ratio, diff, cap, shift, rawKeyFit, score)
            }
        }
    }
    return best
}

/** Nearest energy-curve sample to [time], or null with no usable curve. */
fun energyAt(analysis: TrackAnalysis, time: Double): Double? {
    val curve = analysis.energyCurve
    if (curve.isEmpty() || !time.isFinite()) return null
    var best: Double? = null
    var bestDist = Double.POSITIVE_INFINITY
    for (point in curve) {
        if (!point.time.isFinite() || !point.energy.isFinite() || point.energy < 0) continue
        val dist = abs(point.time - time)
        if (dist < bestDist) {
            bestDist = dist
            best = point.energy
        }
    }
    return best
}

private fun meanEnergy(analysis: TrackAnalysis): Double? {
    val energies = analysis.energyCurve.map { it.energy }.filter { it.isFinite() && it >= 0 }
    if (energies.isEmpty()) return null
    return energies.sum() / energies.size
}

/** Track-maximum energy over the curve; null with no usable samples. */
private fun maxEnergy(analysis: TrackAnalysis): Double? =
    analysis.energyCurve.map { it.energy }
        .filter { it.isFinite() && it >= 0 }
        .maxOrNull()

/** Blueprint Â§5.3 Rule 1. Null on either side is no evidence and answers neutrally. */
fun energyScore(outgoingEnergy: Double?, incomingEnergy: Double?): Double {
    if (outgoingEnergy == null || incomingEnergy == null) return 0.5
    val peak = max(outgoingEnergy, incomingEnergy)
    if (peak <= 0) return 0.5
    return (1.0 - abs(outgoingEnergy - incomingEnergy) / peak).coerceIn(0.0, 1.0)
}

private enum class StructureRole { INTRO, OUTRO, BREAK, OTHER }

/**
 * Blueprint Â§5.4 section roles, derived from measured anchors: OUTRO past the
 * outro/content markers, INTRO inside the opening, BREAK on a low-energy
 * passage (below half the track mean), everything else OTHER (verse/chorus).
 */
private fun structureRoleOf(analysis: TrackAnalysis, time: Double, isOutgoing: Boolean): StructureRole {
    val mean = meanEnergy(analysis)
    val energy = energyAt(analysis, time)
    if (mean != null && energy != null && energy < mean * 0.5) return StructureRole.BREAK
    if (isOutgoing) {
        val outro = analysis.outroStartTime.orZero()
        if (outro > 0 && time >= outro) return StructureRole.OUTRO
        val contentEnd = analysis.contentEndTime.orZero()
        if (contentEnd > 0 && time >= contentEnd - 1.0) return StructureRole.OUTRO
    } else {
        val introEnd = analysis.introEndTime.orZero()
        if (introEnd > 0 && time <= introEnd) return StructureRole.INTRO
        if (introEnd <= 0 && time <= 32.0) return StructureRole.INTRO
    }
    return StructureRole.OTHER
}

/**
 * Automix-intro mode: where the incoming track's intro ends. Native
 * introEndTime when present, else the same 32 s opening the section-role
 * table treats as intro. Every normal-mode entry clamps to this.
 */
fun introEndSeconds(analysis: TrackAnalysis): Double {
    val introEnd = analysis.introEndTime.orZero()
    return if (introEnd > 0) introEnd else 32.0
}

/** Blueprint Â§5.4 section-pair table. */
fun structureScore(analysis: TrackAnalysis, next: TrackAnalysis, transitionTime: Double, entryTime: Double): Double {
    val out = structureRoleOf(analysis, transitionTime, isOutgoing = true)
    val incoming = structureRoleOf(next, entryTime, isOutgoing = false)
    return when {
        out == StructureRole.OUTRO && incoming == StructureRole.INTRO -> 1.0
        out == StructureRole.BREAK && incoming == StructureRole.INTRO -> 0.85
        out == StructureRole.BREAK -> 0.70
        out == StructureRole.OUTRO -> 0.65
        out == StructureRole.OTHER && incoming == StructureRole.INTRO -> 0.45
        else -> 0.25
    }
}

/**
 * Blueprint Â§5.5: 0.2 when [time] sits inside a vocal phrase or within
 * [VOCAL_EXIT_BUFFER_SECONDS] after one ends, 1.0 otherwise. No mask is no
 * evidence and answers 1.0 â€” nothing may punish a fallback analysis.
 */
fun vocalScoreAt(analysis: TrackAnalysis, time: Double): Double {
    val mask = analysis.vocalActivityMask
    val curve = analysis.energyCurve
    if (mask.isEmpty() || mask.size != curve.size || !time.isFinite()) return 1.0
    for (index in mask.indices) {
        val sample = curve[index].time
        if (!sample.isFinite() || sample > time || sample < time - VOCAL_EXIT_BUFFER_SECONDS) continue
        if (mask[index].isFinite() && mask[index] >= VOCAL_ACTIVE_THRESHOLD) return 0.2
    }
    return 1.0
}

/** Blueprint Â§4 compatibility model. */
data class CompatibilityScore(
    val bpm: Double = 0.0,
    val key: Double = 0.0,
    val energy: Double = 0.0,
    val structure: Double = 0.0,
    val vocal: Double = 0.0,
    val overall: Double = 0.0,
)

/** Blueprint Â§6 weighted overall for one pair at one candidate point. */
fun scoreCompatibility(
    analysis: TrackAnalysis,
    nextAnalysis: TrackAnalysis,
    transitionTime: Double,
    entryTime: Double,
): CompatibilityScore {
    val bpm = bpmScore(analysis.bpm.orZero(), nextAnalysis.bpm.orZero())
    val key = if (analysis.key.isBlank() || nextAnalysis.key.isBlank()) {
        NEUTRAL_KEY_SCORE_BELOW_CONF
    } else if (analysis.keyConfidence.orZero() < MIN_KEY_CONFIDENCE_FOR_SCORING ||
        nextAnalysis.keyConfidence.orZero() < MIN_KEY_CONFIDENCE_FOR_SCORING
    ) {
        NEUTRAL_KEY_SCORE_BELOW_CONF
    } else {
        keyScore(analysis.key, nextAnalysis.key)
    }
    val energy = energyScore(energyAt(analysis, transitionTime), energyAt(nextAnalysis, entryTime))
    val structure = structureScore(analysis, nextAnalysis, transitionTime, entryTime)
    val vocal = min(vocalScoreAt(analysis, transitionTime), vocalScoreAt(nextAnalysis, entryTime))
    val overall = bpm * WEIGHT_BPM_SCORE +
        key * WEIGHT_KEY_SCORE +
        energy * WEIGHT_ENERGY_SCORE +
        structure * WEIGHT_STRUCTURE_SCORE +
        vocal * WEIGHT_VOCAL_SCORE
    return CompatibilityScore(bpm, key, energy, structure, vocal, overall)
}

/**
 * Mixset Mode play window past the track's best part, in seconds: never cut
 * before the minimum, aim for the target, and wait past it â€” up to the
 * maximum â€” for a phrase boundary with a calm vocal. A peak gets room to
 * finish instead of being cut mid-chorus at a fixed offset.
 */
const val MIXSET_MIN_PLAY_SECONDS = 60.0
/**
 * No-evidence rescue floor: when [mixsetMixOutAnchor] has no best-part cue
 * at all it used to anchor `playbackTime + 15`, so a freshly tapped track
 * mixed ~20 s in. A mixset slot promises ~a minute of music, so the rescue
 * never lands before this â€” real evidence, once it arrives, re-plans to the
 * true anchor (the marker latch tracks anchor moves over 2 s).
 */
const val MIXSET_RESCUE_FLOOR_SECONDS = 60.0
/**
 * No mixset blend may *start* before this. The rescue floor above holds the
 * anchor, but every branch subtracts its own overlap/fade â€” sameBeat
 * 0.6Â·anchor, dissolve scanFrom cuts, the â‰¤20 s drop-align pull â€” so the
 * fire point could still land at ~16â€“28 s. The planner shifts the whole
 * window instead (see applyMixsetFireFloor).
 */
const val MIXSET_MIN_FIRE_SECONDS = 30.0
const val MIXSET_TARGET_PLAY_SECONDS = 90.0
const val MIXSET_MAX_PLAY_SECONDS = 130.0
/**
 * How much longer (in score-seconds) the anchor may play past the target for
 * a post-peak landing instead of taking a better-scoring point before it.
 */
const val MIXSET_WAIT_TOLERANCE_SECONDS = 8.0
/**
 * Landing on a singing voice costs more than any distance inside the window
 * (at most 30 s each way): when any calm grid point exists, the anchor never
 * lands on a vocal. Unknown stays cheap â€” absence of a mask is not evidence.
 */
const val MIXSET_SINGING_PENALTY = 20.0
/**
 * Energy-arc constants. A cooldown is an 8 s stretch running below 70% of the
 * track mean with its max-min spread under half the mean â€” low AND settled,
 * so a cliff edge or a single quiet bar does not qualify. A buildup foot is
 * the nearest point at least 4 s before the peak sitting at or under 40% of
 * the peak's own energy with the stretch after it rising at least 0.5 above
 * the foot â€” a genuine rise, not a flat line that happens to sit low.
 */
const val MIXSET_COOLDOWN_WINDOW_SECONDS = 8.0
const val MIXSET_LOW_MEAN_FRACTION = 0.7
const val MIXSET_STABLE_SPREAD_FRACTION = 0.5
const val MIXSET_BUILDUP_FOOT_FRACTION = 0.4
/** v2 Â§10: scan starts 8 s before the drop (was 4.0). */
const val MIXSET_BUILDUP_MIN_SECONDS = 8.0
/** v2 Â§10: relaxed for compressed tracks (was 0.5). */
const val MIXSET_BUILDUP_RISE_MARGIN = 0.25
/**
 * Spec active-playtime ceiling: from one phrase before Drop 1 to the exit
 * the listener should hear at most 3 minutes of a track. Past that the exit
 * is pulled back to the nearest 16-bar start inside the budget.
 */
const val MIXSET_MAX_ACTIVE_PLAY_SECONDS = 180.0
/**
 * DJ Mode anti-flap floor: the only duration rule left. The exit may land
 * anywhere the analysis justifies, but never before one 16-bar phrase past
 * the entry â€” without it a noisy curve could hop tracks seconds apart.
 * [mixsetAntiFlapSeconds] computes it from the grid; this is the fallback
 * when the track carries no tempo.
 */
const val MIXSET_ANTI_FLAP_FALLBACK_SECONDS = 30.0
/**
 * Spec phrase = 16 bars. The native analyzer emits 8-bar phrase boundaries,
 * so the 16-bar grid is derived in Kotlin: every 16th downbeat (downbeats
 * are bar starts), else every 2nd phrase boundary, else synthesized from
 * firstBeat + k * 16 bars. Zero native/JNI cost.
 */
const val MIXSET_PHRASE_BARS = 16

/**
 * The spec's 16-bar grid for [analysis], ascending. Empty only when the
 * analysis carries no timing at all (no downbeats, no phrases, no beat
 * interval) â€” callers then fall back to the raw time.
 */
fun phrase16Grid(analysis: TrackAnalysis): List<Double> {
    val downs = analysis.downbeats.filter { it.isFinite() }.sorted()
    if (downs.size >= MIXSET_PHRASE_BARS * 2) {
        return downs.filterIndexed { index, _ -> index % MIXSET_PHRASE_BARS == 0 }
    }
    val phrases = analysis.phraseBoundaries.filter { it.isFinite() }.sorted()
    if (phrases.size >= 4) {
        return phrases.filterIndexed { index, _ -> index % 2 == 0 }
    }
    val interval = analysis.beatInterval.orZero().takeIf { it > 0 }
        ?: if (analysis.bpm.orZero() > 0) 60 / analysis.bpm else 0.0
    val first = analysis.firstBeat.orZero()
    val duration = analysis.duration.orZero()
    if (interval <= 0 || duration <= 0) return emptyList()
    val step = interval * 4 * MIXSET_PHRASE_BARS
    val grid = mutableListOf<Double>()
    var t = first
    while (t <= duration) {
        grid.add(t)
        t += step
    }
    return grid
}

/**
 * Spec snap: the nearest 16-bar boundary at or before [time] when one is
 * within [tolerance] (default one phrase), else [time]. Cuts land on phrase
 * starts, never mid-phrase.
 */
fun snapToPhrase16(
    analysis: TrackAnalysis,
    time: Double,
    tolerance: Double = Double.POSITIVE_INFINITY,
    preferEarlier: Boolean = true,
): Double {
    if (!time.isFinite()) return time
    val grid = phrase16Grid(analysis)
    if (grid.isEmpty()) return time
    val tol = if (tolerance.isFinite()) tolerance else {
        val interval = analysis.beatInterval.orZero().takeIf { it > 0 }
            ?: if (analysis.bpm.orZero() > 0) 60 / analysis.bpm else 0.5
        interval * 4 * MIXSET_PHRASE_BARS
    }
    if (preferEarlier) {
        val snapped = grid.filter { it <= time + 1e-6 }.maxOrNull()
        if (snapped != null && time - snapped <= tol) return max(0.0, snapped)
    } else {
        val nearest = grid.minByOrNull { abs(it - time) }
        if (nearest != null && abs(nearest - time) <= tol) return max(0.0, nearest)
    }
    val nearest = grid.minByOrNull { abs(it - time) } ?: return time
    return if (abs(nearest - time) <= tol) max(0.0, nearest) else time
}

/**
 * Phase A3: 32-bar grid = every second 16-bar start. A use-site policy, not a
 * primitive: MIXSET_PHRASE_BARS stays 16, the 32-bar span is only justified
 * where two choruses have already played (see chorusCountBefore).
 */
fun phrase32Grid(analysis: TrackAnalysis): List<Double> =
    phrase16Grid(analysis).filterIndexed { index, _ -> index % 2 == 0 }

/** Phase A3: nearest 32-bar boundary to [time] within [tolerance], else [time]. */
fun snapToPhrase32(
    analysis: TrackAnalysis,
    time: Double,
    tolerance: Double = Double.POSITIVE_INFINITY,
    preferEarlier: Boolean = true,
): Double {
    if (!time.isFinite()) return time
    val grid = phrase32Grid(analysis)
    if (grid.isEmpty()) return time
    val tol = if (tolerance.isFinite()) tolerance else {
        (phrase16Seconds(analysis) ?: MIXSET_WAIT_TOLERANCE_SECONDS) * 2
    }
    if (preferEarlier) {
        val snapped = grid.filter { it <= time + 1e-6 }.maxOrNull()
        if (snapped != null && time - snapped <= tol) return max(0.0, snapped)
    } else {
        val nearest = grid.minByOrNull { abs(it - time) }
        if (nearest != null && abs(nearest - time) <= tol) return max(0.0, nearest)
    }
    val nearest = grid.minByOrNull { abs(it - time) } ?: return time
    return if (abs(nearest - time) <= tol) max(0.0, nearest) else time
}

/**
 * Phase A3: how many CHORUS sections end at or before [anchor]. mergeAdjacent
 * joins same-type neighbours, so one entry â‰ˆ one sustained loud passage and
 * counting ends (not starts) never double-counts the chorus being cut.
 * Empty map = no evidence = 0; the caller keeps 16-bar behaviour.
 */
fun chorusCountBefore(analysis: TrackAnalysis, anchor: Double): Int {
    if (!anchor.isFinite()) return 0
    return analysis.structureMap.count {
        it.type == StructureSectionType.CHORUS &&
            it.start.isFinite() && it.end.isFinite() &&
            it.end <= anchor + 1e-6
    }
}

/**
 * The track's best part: its drop, else its loudest moment past the intro,
 * else the analyzed mix-in. One deterministic function both sides share, so
 * the outgoing cut point and the incoming cue agree without the controller
 * having to remember where the current track started.
 */
fun bestPartCue(analysis: TrackAnalysis): Double? {
    firstDropSec(analysis)?.let { return it }
    maxEnergyTimeAfterIntro(analysis)?.let { return it }
    return analysis.mixInTime.takeIf { it.isFinite() && it > 0 }
}

private fun maxEnergyTimeAfterIntro(analysis: TrackAnalysis): Double? {
    val curve = analysis.energyCurve
    if (curve.isEmpty()) return null
    val introEnd = analysis.introEndTime.orZero()
    var best: Double? = null
    var bestEnergy = Double.NEGATIVE_INFINITY
    for (point in curve) {
        if (!point.time.isFinite() || !point.energy.isFinite()) continue
        if (point.time < introEnd) continue
        if (point.energy > bestEnergy) {
            bestEnergy = point.energy
            best = point.time
        }
    }
    return best
}

/**
 * Where the incoming track joins in Mixset Mode: the foot of its buildup â€”
 * the rise into the peak â€” so the track plays its own build and the peak
 * lands on its own time after the handoff, instead of being cued in the
 * face. Null when the curve shows no genuine rise (flat lines sit low
 * everywhere); the caller then falls back to the peak itself.
 */
/**
 * v2 Â§8: buildup foot = Â§4 gradient inflection computed once at analysis
 * time ([TrackAnalysis.structuredBuildupSec]), snapped to the 16-bar grid
 * here. Fallbacks, in order: dropâˆ’phrase when a drop exists but the gradient
 * failed (spec Â§4 fallback); the legacy foot walk when there is no drop at
 * all (peak-anchored tracks); null when nothing supports the claim.
 */
/**
 * Exit-entry spec Fix 3: distance validation for every buildup result. A
 * buildup closer than 8 s to the drop gives no approach; farther than 90 s
 * plays too much non-peak content. Either way back off to drop âˆ’ 1 phrase.
 * Null when even that is non-positive, so callers fall back to the peak.
 */
fun validateBuildupStart(rawBuildup: Double?, drop: Double, phrase16: Double?): Double? {
    if (rawBuildup == null || !rawBuildup.isFinite() || !drop.isFinite()) return rawBuildup
    val gap = drop - rawBuildup
    if (gap < MIN_BUILDUP_TO_DROP_SECONDS || gap > MAX_BUILDUP_TO_DROP_SECONDS) {
        if (phrase16 == null || !phrase16.isFinite() || phrase16 <= 0) return rawBuildup
        val backed = drop - phrase16
        return if (backed > 0) backed else null
    }
    return rawBuildup
}

fun buildupStart(analysis: TrackAnalysis, peakTime: Double): Double? {
    val raw = buildupStartRaw(analysis, peakTime) ?: return null
    val drop = firstDropSec(analysis) ?: return raw
    if (!drop.isFinite()) return raw
    return validateBuildupStart(raw, drop, phrase16Seconds(analysis))
}

/**
 * Full-audit P0.3: the measured-foot half of the buildup chain â€” stored map
 * buildup, validated gradient inflection, monotonic-lean inflection, or a
 * structural BUILD ending near the drop. Null when the drop has no findable
 * foot (cold open, step entry, flat bed, short intro): arithmetic fallbacks
 * (dropâˆ’phrase, dropâˆ’8 s) are numbers, not feet, and must never unlock
 * drop-gated routing. [buildupStartRaw] below falls back to them for
 * rendering continuity; trust decisions use this.
 */
fun trustedBuildupStart(analysis: TrackAnalysis): Double? {
    analysis.structuredBuildupSec?.takeIf { it.isFinite() }?.let { stored ->
        return snapToPhrase16(analysis, stored) ?: stored
    }
    val drop = firstDropSec(analysis)
    if (drop != null && drop.isFinite()) {
        val peakEnergy = analysis.energyCurve
            .filter { it.time.isFinite() && it.energy.isFinite() }
            .minByOrNull { abs(it.time - drop) }?.energy
            ?.takeIf { it > 0 }
        if (peakEnergy != null) {
            val gradient = StructureDetector.gradientBuildup(analysis.energyCurveFine, drop, peakEnergy)
            if (gradient != null && gradient.isFinite() && gradient > 0) {
                return snapToPhrase16(analysis, gradient) ?: gradient
            }
            val lean = StructureDetector.gradientInflection(analysis.energyCurveFine, drop)
            if (lean != null && lean.isFinite() && lean > 0 &&
                StructureDetector.climbMonotonic(analysis.energyCurveFine, lean, drop)
            ) {
                return snapToPhrase16(analysis, lean) ?: lean
            }
        }
        val phrase16 = phrase16Seconds(analysis)
        val buildNearDrop = analysis.structureMap
            .filter {
                it.type == StructureSectionType.BUILD && it.start.isFinite() && it.end.isFinite() &&
                    it.end <= drop && phrase16 != null && drop - it.end <= phrase16 * 4
            }
            .maxByOrNull { it.end }?.start
        if (buildNearDrop != null && buildNearDrop > 0) {
            return snapToPhrase16(analysis, buildNearDrop) ?: buildNearDrop
        }
    }
    return null
}

/**
 * Full-audit P0.3: a drop is trusted only when a measured foot stands before
 * it. A lone in-zone spike (cold-open hit, single loud chorus, flat-master
 * slope) becomes a "drop" through the max-RMS / 1.5Ã—-mean fallbacks with no
 * BUILD before it â€” and every downstream consumer treats it as measured.
 */
fun isDropTrusted(analysis: TrackAnalysis): Boolean {
    val drop = firstDropSec(analysis) ?: return false
    if (!drop.isFinite()) return false
    return trustedBuildupStart(analysis) != null
}

/**
 * Phase A4: skit/interlude proxy from persisted evidence only (centroid and
 * onset curves do not survive a restart â€” only RMS-adjacent energy and the
 * vocal mask do). A spoken bed is a long (>=30 s, ~4Ã— the cooldown window)
 * low span (< BREAK_RMS_FRACTION of the mean) whose vocal activity stays
 * below 0.40 (speech scores low on the band-ratio gate, singing does not),
 * on a track with no trusted drop (guards a genuine quiet intro) or low
 * whole-track vocal probability. Returns the longest qualifying span, else
 * null. Callers veto mixset entry/exit inside the span and force the PLAIN
 * path â€” a talking head is never a mix point.
 */
fun spokenInterludeSpan(analysis: TrackAnalysis): ClosedRange<Double>? {
    val curve = analysis.energyCurve
    if (curve.size < 4) return null
    val energies = curve.map { it.energy }.filter { it.isFinite() && it >= 0 }
    if (energies.isEmpty()) return null
    val mean = energies.average().takeIf { it.isFinite() && it > 0 } ?: return null
    if (!isDropTrusted(analysis) || analysis.vocalProbability < 0.40) {
        var best: ClosedRange<Double>? = null
        var runStart: Double? = null
        var runEnd = 0.0
        fun closeRun() {
            val s = runStart
            if (s != null && runEnd - s >= 30.0) {
                val v = vocalActivityBetween(analysis, s, runEnd)
                if (v != null && v < 0.40) {
                    if (best == null || runEnd - s > best!!.endInclusive - best!!.start) {
                        best = s..runEnd
                    }
                }
            }
            runStart = null
        }
        for (point in curve) {
            if (!point.time.isFinite() || !point.energy.isFinite()) continue
            if (point.energy < BREAK_RMS_FRACTION * mean) {
                if (runStart == null) runStart = point.time
                runEnd = point.time
            } else {
                closeRun()
            }
        }
        closeRun()
        return best
    }
    return null
}

private fun buildupStartRaw(analysis: TrackAnalysis, peakTime: Double): Double? {
    trustedBuildupStart(analysis)?.let { return it }
    val drop = firstDropSec(analysis)
    if (drop != null && drop.isFinite()) {
        val phrase16 = phrase16Seconds(analysis)
        if (phrase16 != null) {
            val fallback = drop - phrase16
            if (fallback > 0) {
                val footEnergy = energyAt(analysis, fallback)
                val dropEnergy = energyAt(analysis, drop)
                if (footEnergy != null && dropEnergy != null && dropEnergy > 0 &&
                    footEnergy > 0.85 * dropEnergy
                ) {
                    val approach = drop - MIXSET_BUILDUP_MIN_SECONDS
                    if (approach > 0) return snapToPhrase16(analysis, approach) ?: approach
                } else {
                    return snapToPhrase16(analysis, fallback) ?: fallback
                }
            }
            val floor = drop - MIXSET_BUILDUP_MIN_SECONDS
            if (floor > 0) return snapToPhrase16(analysis, floor) ?: floor
            return null
        }
        return null
    }
    return legacyBuildupFoot(analysis, peakTime)
}

/** Pre-v2 foot walk, kept for dropless tracks only (see [buildupStart]). */
private fun legacyBuildupFoot(analysis: TrackAnalysis, peakTime: Double): Double? {
    val curve = analysis.energyCurve
    if (curve.size < 3 || !peakTime.isFinite()) return null
    val peakEnergy = curve.minByOrNull { abs(it.time - peakTime) }
        ?.energy?.takeIf { it.isFinite() && it > 0 } ?: return null
    val footCeiling = MIXSET_BUILDUP_FOOT_FRACTION * peakEnergy
    for (i in curve.indices.reversed()) {
        val point = curve[i]
        if (!point.time.isFinite() || !point.energy.isFinite()) continue
        if (point.time > peakTime - MIXSET_BUILDUP_MIN_SECONDS) continue
        if (point.time >= peakTime) continue
        if (point.energy > footCeiling) continue
        val after = curve.subList(i, curve.size)
            .filter { it.time.isFinite() && it.energy.isFinite() && it.time <= peakTime }
        if (after.size < 2 || after.last().time - point.time < MIXSET_COOLDOWN_WINDOW_SECONDS) continue
        if (after.sumOf { it.energy } / after.size >= point.energy + MIXSET_BUILDUP_RISE_MARGIN) {
            return nearestValue(analysis.downbeats, point.time, MIXSET_BUILDUP_MIN_SECONDS)
                ?: point.time
        }
    }
    return null
}

/**
 * Exit-entry spec Fix 4: the mixset entry bypasses mix-in ranking, so a
 * buildup under vocals would enter mid-voice. Check the first 4 bars at
 * 250 ms resolution; when vocal-heavy, advance beat by beat (after an
 * initial 1-bar jump) up to 8 bars forward, never past 2 bars before the
 * drop. No clean bar â†’ keep the original (never overshoot into the drop).
 */
fun adjustedMixsetEntry(buildupStartSec: Double, analysis: TrackAnalysis, drop: Double): Double {
    val beat = analysis.beatInterval.takeIf { it.isFinite() && it > 0 } ?: return buildupStartSec
    if (buildupStartSec > drop - 2.0) return max(0.0, drop - 2.0)
    val mask = analysis.vocalActivityMask
    fun vocalAt(t: Double): Double {
        val idx = (t / 0.25).toInt()
        return mask.getOrNull(idx)?.takeIf { it.isFinite() } ?: 0.0
    }
    val startIdx = (buildupStartSec / 0.25).toInt()
    val checkSamples = (beat * 4 * MIXSET_ENTRY_VOCAL_CHECK_BARS / 0.25).toInt().coerceAtLeast(1)
    var sum = 0.0
    var n = 0
    for (i in 0 until checkSamples) {
        val v = mask.getOrNull(startIdx + i)?.takeIf { it.isFinite() } ?: continue
        sum += v
        n++
    }
    if (n == 0 || sum / n <= MIXSET_ENTRY_VOCAL_THRESHOLD) return buildupStartSec
    val maxAdvance = min(
        buildupStartSec + beat * MIXSET_ENTRY_VOCAL_ADVANCE_BARS,
        drop - 2.0,
    )
    var cursor = buildupStartSec + beat * 4
    while (cursor < maxAdvance) {
        if (vocalAt(cursor) < MIXSET_ENTRY_VOCAL_ACCEPT) return snapToPhrase16(analysis, cursor)
        cursor += beat * 4
    }
    return buildupStartSec
}

/**
 * DJ Mode entry, freeform: the buildup foot when the curve shows one, the
 * drop itself when it does not, else the peak â€” so the cue may land
 * mid-track rather than strictly at the head. One function so the hard/echo/
 * plain cues, the WSOLA drop and the adaptive handoff all agree on where
 * the incoming track begins.
 */
fun mixsetEntryPoint(analysis: TrackAnalysis): Double? {
    val peak = bestPartCue(analysis) ?: return null
    val drop = firstDropSec(analysis)?.takeIf { it.isFinite() }
    val entry = if (drop == null || isDropTrusted(analysis)) {
        buildupStart(analysis, peak)
    } else {
        null
    }
    val cue = entry
        ?: drop?.let { (it - MIXSET_BUILDUP_MIN_SECONDS).takeIf { r -> r > 0 } }
        ?: peak
    val snapped = snapToPhrase16(analysis, cue) ?: cue
    if (drop == null) return snapped
    return adjustedMixsetEntry(snapped, analysis, drop)
}

/**
 * Spec drop alignment (mixset only): the incoming track's drop must land
 * after the outgoing track is gone. When B's drop fires while A is still
 * up, pull A's exit back to the 16-bar start at/before the drop â€” but only
 * a nudge, never a jump: a gap beyond the wait tolerance means B's
 * structure doesn't fit the short overlap, and A's comedown wins over
 * forcing the alignment.
 */
fun alignMixsetExitToIncomingDrop(
    outgoing: TrackAnalysis,
    incoming: TrackAnalysis,
    anchor: Double,
    mixset: Boolean,
): Double {
    if (!mixset || !anchor.isFinite()) return anchor
    val dropB = firstDropSec(incoming) ?: return anchor
    if (!dropB.isFinite() || dropB >= anchor) return anchor
    if (!isDropTrusted(incoming)) return anchor
    val tolerance = minOf(phrase16Seconds(outgoing) ?: MIXSET_WAIT_TOLERANCE_SECONDS, 20.0)
    val pulled = snapToPhrase16(outgoing, dropB)
    if (pulled >= anchor || anchor - pulled > tolerance) return anchor
    return max(0.0, pulled)
}

/**
 * Spec 16-bar phrase length in seconds, from the beat interval. Null when the
 * analysis carries no tempo â€” tier-2 exit then cannot be computed.
 */
fun phrase16Seconds(analysis: TrackAnalysis): Double? {
    val interval = analysis.beatInterval.orZero().takeIf { it > 0 }
        ?: if (analysis.bpm.orZero() > 0) 60 / analysis.bpm else 0.0
    return if (interval > 0) interval * 4 * MIXSET_PHRASE_BARS else null
}

/** v2 Â§9c: coarse genre bucket. Routing never keys off it â€” it only tunes the
 *  mixset play target and rides along in low-score logs. First match wins. */
enum class GenreClass { ELECTRONIC, HIP_HOP, AMBIENT, POP, OTHER }

/**
 * v2 Â§9c classifier from stored analysis only. Matches the spec exactly:
 * AMBIENT on beat confidence alone (onset rate would need transient curves
 * the store deliberately never keeps); the structural detector's AMBIENT
 * label is the richer signal and lives on the analysis for planners.
 */
fun genreClass(analysis: TrackAnalysis): GenreClass {
    val bpm = analysis.bpm.orZero()
    val conf = analysis.beatConfidence.orZero()
    if (conf < 0.30) return GenreClass.AMBIENT
    val duration = analysis.duration.orZero().takeIf { it > 0 } ?: return GenreClass.OTHER
    val downs = analysis.downbeats.count { it.isFinite() }
    val presence = downs / duration
    if (bpm in 70.0..105.0 && presence > 0.2) return GenreClass.HIP_HOP
    if (bpm in 120.0..150.0 && presence > 0.2) return GenreClass.ELECTRONIC
    if (bpm in 90.0..135.0) return GenreClass.POP
    return GenreClass.OTHER
}

/**
 * v2 Â§9c: mixset play target by genre â€” hip-hop verses breathe shorter,
 * ambient beds get room. Â±seconds off the base target, clamped sane by callers.
 */
fun mixsetTargetFor(genre: GenreClass): Double = when (genre) {
    GenreClass.HIP_HOP -> MIXSET_TARGET_PLAY_SECONDS - 15.0
    GenreClass.AMBIENT -> MIXSET_TARGET_PLAY_SECONDS + 30.0
    else -> MIXSET_TARGET_PLAY_SECONDS
}

/**
 * v2 Â§2c/Â§3 outro exception: when the detector found an OUTRO section that
 * starts before the 80% floor and is followed by â‰¥30 s of low-energy tail,
 * the floor moves up to the outro start â€” the comedown has already begun, so
 * holding the track to 80% only burns low tail. Strict on the 30 s: a shorter
 * tail keeps the default floor.
 */
fun effectivePlayFloor(analysis: TrackAnalysis, length: Double): Double {
    val default = 0.8 * length
    val drop = firstDropSec(analysis)
    if (drop != null && drop.isFinite() && length > 0 &&
        drop / length in LATE_DROP_WINDOW_LOW..LATE_DROP_WINDOW_HIGH
    ) {
        val phrase16 = phrase16Seconds(analysis)
        if (phrase16 != null && phrase16.isFinite() && phrase16 > 0) {
            return maxOf(default, drop + MIN_DROP_PLAY_THROUGH_PHRASES * phrase16)
        }
        return maxOf(default, drop)
    }
    val outro = analysis.structuredOutroSec?.takeIf { it.isFinite() && it > 0 } ?: return default
    if (outro >= default) return default
    val tailEnd = minOf(outro + 20.0, length)
    if (tailEnd - outro < 20.0) return default
    val mean = meanEnergy(analysis) ?: return default
    if (mean <= 0) return default
    val tail = analysis.energyCurve.filter {
        it.time.isFinite() && it.energy.isFinite() && it.time in outro..tailEnd
    }.map { it.energy }
    if (tail.size < 2) return default
    return if (tail.average() < 0.72 * mean) maxOf(0.0, outro) else default
}

/**
 * Finetune v1 Â§2.3: BREAK is the DJ-canonical exit â€” a low-energy candidate
 * inside a detected BREAK section earns the boost.
 */
fun isInsideBreak(analysis: TrackAnalysis, time: Double): Boolean =
    analysis.structureMap.any { section ->
        section.type == StructureSectionType.BREAK &&
            time >= section.start && time <= section.end
    }

/** Finetune v1 Â§1.5/Â§3.3: derived, not stored â€” computable from stored fields. */
fun isColdOpen(analysis: TrackAnalysis, audibleStart: Double = audibleStartOf(analysis)): Boolean {
    if (!audibleStart.isFinite() || audibleStart >= COLD_OPEN_AUDIBLE_SECONDS) return false
    val mean = meanEnergy(analysis) ?: return false
    if (mean <= 0) return false
    val barSeconds = if (analysis.beatInterval.orZero() > 0) analysis.beatInterval * 4 else 2.0
    val head = analysis.energyCurve.filter {
        it.time.isFinite() && it.energy.isFinite() && it.time in audibleStart..audibleStart + barSeconds
    }.map { it.energy }
    if (head.size < 2) return false
    return head.average() > COLD_OPEN_RMS_FRACTION * mean
}

/**
 * One 16-bar phrase in seconds from the grid, else the fallback. The DJ Mode
 * anti-flap floor is entry + this â€” the sole duration rule in DJ Mode.
 */
fun mixsetAntiFlapSeconds(analysis: TrackAnalysis): Double =
    phrase16Seconds(analysis)?.takeIf { it.isFinite() && it > 0 } ?: MIXSET_ANTI_FLAP_FALLBACK_SECONDS

/**
 * DJ Mode outgoing anchor, freeform rules: the exit may land anywhere the
 * analysis justifies â€” no 60 s floor, no 130 s cap, no 180 s active cap, no
 * drop play-through floor. Tiers: (1) first cooldown at/after Drop 1 snapped
 * to the 16-bar grid; (2) Drop 1 + 2 phrases; (3) calm-vocal fallback with
 * the 40%-of-length escape hatch. The search runs from entry + one phrase
 * (anti-flap) to the track end. A mid-DROP landing is still pushed to the
 * next 16-bar start â€” cutting inside the drop sounds like a power outage,
 * and that is musicality, not a duration rule. A rescue anchor just ahead of
 * the playhead when it is already past the window â€” a manually started track
 * plays from 0, not from its best cue, so the computed window can already be
 * behind.
 */
fun mixsetMixOutAnchor(analysis: TrackAnalysis, length: Double, playbackTime: Double): MixOutAnchor {
    val rescue = MixOutAnchor(
        time = min(length, max(playbackTime + 15.0, MIXSET_RESCUE_FLOOR_SECONDS)).coerceAtLeast(0.0),
        type = "mixset_rescue",
        discardedMusicSeconds = 0.0,
    )
    val entry = bestPartCue(analysis)
    if (entry == null) {
        logMixsetAnchorOnce(analysis.trackId, "mixset anchor track=${analysis.trackId} type=mixset_rescue(no-entry) t=${"%.1f".format(rescue.time)} len=${"%.1f".format(length)} pos=${"%.1f".format(playbackTime)}")
        return rescue
    }
    val floor = entry + mixsetAntiFlapSeconds(analysis)
    val base = entry + mixsetTargetFor(genreClass(analysis))
    if (playbackTime >= length - 12.0) {
        logMixsetAnchorOnce(analysis.trackId, "mixset anchor track=${analysis.trackId} type=mixset_rescue(past-window) t=${"%.1f".format(rescue.time)} len=${"%.1f".format(length)} pos=${"%.1f".format(playbackTime)}")
        return rescue
    }
    val from = max(0.0, floor)
    val to = max(0.0, length - 2.0)
    if (to <= from) {
        logMixsetAnchorOnce(analysis.trackId, "mixset anchor track=${analysis.trackId} type=mixset_rescue(window-collapse) t=${"%.1f".format(rescue.time)} len=${"%.1f".format(length)} pos=${"%.1f".format(playbackTime)} from=${"%.1f".format(from)} to=${"%.1f".format(to)}")
        return rescue
    }
    val drop = firstDropSec(analysis)
    val tier1From = if (drop != null && drop.isFinite()) max(from, drop) else from
    var exit = cooldownLanding(analysis, tier1From, to)?.let { snapToPhrase16(analysis, it) }
    val spoken = spokenInterludeSpan(analysis)
    if (exit != null && spoken != null && exit in spoken) exit = null
    if (exit == null && drop != null && drop.isFinite()) {
        val phrase16 = phrase16Seconds(analysis)
        if (phrase16 != null) {
            val tier2raw = drop + 2 * phrase16
            val twoChoruses = chorusCountBefore(analysis, tier2raw) >= 2
            val tier2 = if (twoChoruses) {
                snapToPhrase32(analysis, drop + 4 * phrase16, preferEarlier = false)
            } else {
                snapToPhrase16(analysis, tier2raw, preferEarlier = false)
            }
            if (tier2 in from..to) exit = tier2
        }
    }
    var time = (exit ?: fallbackMixsetAnchor(analysis, from, to, base))
        .coerceIn(0.0, max(0.0, length - 2.0))
    if (exit == null) {
        val anchorVocal = vocalActivityBetween(analysis, time - 2.0, time + 2.0)
        if (anchorVocal != null && anchorVocal > VOCAL_DISCARD_THRESHOLD) {
            val tier3 = snapToPhrase16(analysis, 0.40 * length, preferEarlier = false)
            if (tier3 in from..to) {
                time = tier3.coerceIn(0.0, max(0.0, length - 2.0))
            }
        }
    }
    time = pushPastDrop(analysis, time, drop, length)
    logMixsetAnchorOnce(analysis.trackId, "mixset anchor track=${analysis.trackId} type=mixset_peak t=${"%.1f".format(time)} len=${"%.1f".format(length)} entry=${"%.1f".format(entry)} floor=${"%.1f".format(floor)}")
    return MixOutAnchor(time = time, type = "mixset_peak", discardedMusicSeconds = max(0.0, length - time))
}

/**
 * Spec active playtime: what the listener hears runs from one phrase before
 * the drop to the exit, at most 3 minutes. Past the budget the exit comes
 * back to the nearest 16-bar start inside it â€” unless that would break the
 * 60 s play floor, in which case the floor wins.
 */
private fun capActivePlaytime(analysis: TrackAnalysis, entry: Double, time: Double, length: Double): Double {
    val drop = firstDropSec(analysis) ?: entry.takeIf { it.isFinite() } ?: return time
    if (!drop.isFinite() || !time.isFinite()) return time
    val phrase16 = phrase16Seconds(analysis) ?: return time
    val listenStart = max(0.0, drop - phrase16)
    if (time - listenStart <= MIXSET_MAX_ACTIVE_PLAY_SECONDS) return time
    val capped = phrase16Grid(analysis).filter { it <= listenStart + MIXSET_MAX_ACTIVE_PLAY_SECONDS }.maxOrNull()
        ?: return time
    if (capped < entry + MIXSET_MIN_PLAY_SECONDS) return time
    return capped.coerceIn(0.0, max(0.0, length - 2.0)).coerceAtMost(time)
}

/**
 * Never cut mid-DROP: when the anchor sits inside drop-level energy at or
 * past the drop, move it to the next 16-bar start. A single push â€” a track
 * that never comes down still has to end somewhere.
 */
private fun pushPastDrop(analysis: TrackAnalysis, time: Double, drop: Double?, length: Double): Double {
    if (drop == null || !drop.isFinite() || !time.isFinite()) return time
    if (!isDropTrusted(analysis)) return time
    if (time < drop - 1.0) return time
    val dropEnergy = energyAt(analysis, drop) ?: return time
    if (dropEnergy <= 0) return time
    val here = energyAt(analysis, time) ?: return time
    if (here < dropEnergy * 0.8) return time
    val next = phrase16Grid(analysis).firstOrNull { it > time + 1.0 } ?: return time
    return next.coerceIn(0.0, max(0.0, length - 2.0))
}

/**
 * The first cooldown inside [from]..[to]: the earliest phrase boundary or
 * downbeat opening an 8 s stretch that runs low and settled. A cliff edge
 * fails the spread check and a lone quiet bar fails the mean check, so what
 * qualifies is a comedown that actually lasts. Null when the track never
 * comes down inside the window.
 */
fun cooldownLanding(analysis: TrackAnalysis, from: Double, to: Double): Double? {
    val curve = analysis.energyCurve
    if (curve.size < 3 || to <= from) return null
    val mean = meanEnergy(analysis) ?: return null
    if (mean <= 0) return null
    val lowCeiling = MIXSET_LOW_MEAN_FRACTION * mean
    val spreadCeiling = MIXSET_STABLE_SPREAD_FRACTION * mean
    val grid = (analysis.phraseBoundaries + analysis.downbeats)
        .filter { it.isFinite() && it in from..to }
        .distinct()
        .sorted()
    for (point in grid) {
        val window = curve.filter {
            it.time.isFinite() && it.energy.isFinite() &&
                it.time >= point && it.time <= point + MIXSET_COOLDOWN_WINDOW_SECONDS
        }
        if (window.size < 2) continue
        val energies = window.map { it.energy }
        if (energies.average() >= lowCeiling) continue
        if ((energies.max() - energies.min()) >= spreadCeiling) continue
        if (StructureDetector.linearSlope(window.map { it.time }, energies) < MIXSET_COOLDOWN_SLOPE_THRESHOLD) continue
        return point
    }
    return null
}

fun fallbackMixsetAnchor(analysis: TrackAnalysis, from: Double, to: Double, base: Double): Double {
    fun scored(time: Double): Double {
        val vocal = vocalActivityBetween(analysis, time - 2.0, time + 2.0)
        val penalty = when {
            vocal == null -> 2.0
            vocal < 0.4 -> 0.0
            else -> MIXSET_SINGING_PENALTY
        }
        return abs(time - base) + penalty
    }
    val grid = (analysis.phraseBoundaries + analysis.downbeats)
        .filter { it.isFinite() && it in from..to }
        .distinct()
    val early = grid.filter { it <= base }.minByOrNull(::scored)
    val late = grid.filter { it > base }.minByOrNull(::scored)
    val picked = when {
        late == null -> early
        early == null -> late
        scored(late) <= scored(early) + MIXSET_WAIT_TOLERANCE_SECONDS -> late
        else -> early
    } ?: base.coerceIn(from, to)
    return snapToPhrase16(analysis, picked).coerceIn(from, to)
}

/**
 * Blueprint Â§5.4 drop detection: the first local energy maximum past the
 * intro that clears 1.5x the track mean â€” where LOOP_CUT_DROP enters the
 * incoming track. Null when the curve cannot support the claim.
 *
 * v2 Â§2b: the detector's DROP label wins when present; the heuristic below
 * only runs for analyses that predate schema 3.
 */
fun firstDropSec(analysis: TrackAnalysis): Double? {
    val stored = analysis.structuredDropSec?.takeIf { it.isFinite() && it >= 0 }
    if (stored != null && analysis.dropConfidence != null) return stored
    val curve = analysis.energyCurve
    if (curve.size < 3) return null
    val mean = meanEnergy(analysis) ?: return null
    if (mean <= 0) return null
    val threshold = mean * 1.5
    val introEnd = analysis.introEndTime.orZero()
    for (i in 1 until curve.size - 1) {
        val point = curve[i]
        if (!point.time.isFinite() || point.time < introEnd) continue
        if (point.energy >= threshold &&
            point.energy > curve[i - 1].energy &&
            point.energy > curve[i + 1].energy
        ) {
            return point.time
        }
    }
    return null
}
