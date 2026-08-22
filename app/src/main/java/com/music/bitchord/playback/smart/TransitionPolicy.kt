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
import kotlin.math.max
import kotlin.math.min

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

/** How far a tempo pairing may drift from unity and still be considered transparent to stretch. */
const val MAX_STRETCH_DEVIATION = 0.04

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
    "interior_mix_out" to 0.95,
    "outro_start" to 0.9,
    "content_end" to 0.75,
)

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
fun rankMixInCandidates(analysis: TrackAnalysis): List<RankedMixCandidate> {
    val candidates = analysis.mixInCandidates.filter { it.time.isFinite() && it.time >= 0 }
    if (candidates.isEmpty()) return emptyList()
    val beatSeconds = analysis.beatInterval.orZero()
        .takeIf { it > 0 }
        ?: if (analysis.bpm.orZero() > 0) 60 / analysis.bpm else 0.5
    val audibleStart = audibleStartOf(analysis)
    return candidates.map { candidate ->
        var rankScore = candidate.score.orZero() + (MIX_IN_TYPE_WEIGHT[candidate.type] ?: 0.0)
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
): List<RankedMixCandidate> {
    val end = resolveContentEnd(analysis, contentEnd, duration)
    if (end <= 0) return emptyList()
    return mixOutCandidatesOf(analysis, end)
        .map { candidate ->
            val measured = audibleSecondsBetween(analysis, candidate.time, end)
            // With no energy curve there is no way to tell skipped music from skipped silence, so
            // the raw gap is charged in full and the budget errs toward playing the track.
            RankedMixCandidate(
                time = candidate.time,
                score = candidate.score,
                type = candidate.type,
                rankScore = candidate.score + (MIX_OUT_TYPE_SCORE[candidate.type] ?: 0.0),
                discardedMusicSeconds = measured ?: max(0.0, end - candidate.time),
                measured = measured != null,
            )
        }
        .filter { it.discardedMusicSeconds <= MAX_DISCARDED_MUSIC_SECONDS }
        .sortedWith(compareByDescending<RankedMixCandidate> { it.rankScore }.thenByDescending { it.time })
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
): MixOutAnchor {
    val end = resolveContentEnd(analysis, contentEnd, duration)
    val best = rankMixOutCandidates(analysis, end, duration).firstOrNull()
    return MixOutAnchor(
        time = best?.time ?: end,
        type = best?.type ?: "content_end",
        discardedMusicSeconds = best?.discardedMusicSeconds ?: 0.0,
    )
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

    val stretchRatio = outgoingBpm / alignTempoOctave(outgoingBpm, incomingBpm)
    if (abs(stretchRatio - 1) > MAX_STRETCH_DEVIATION) reasons += "tempo-distance"
    if (outgoingConfidence < MIN_BEATMATCH_CONFIDENCE || incomingConfidence < MIN_BEATMATCH_CONFIDENCE) {
        reasons += "beat-confidence"
    }

    return TransitionPolicyVerdict(
        tier = if (reasons.isEmpty()) TransitionTier.BEATMATCHED else TransitionTier.DJ_ASSISTED,
        reasons = reasons,
        beatConfidence = floorConfidence,
    )
}
