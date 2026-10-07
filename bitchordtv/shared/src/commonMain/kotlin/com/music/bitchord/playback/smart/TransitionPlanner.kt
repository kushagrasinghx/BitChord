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

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Turns stored analysis into a concrete transition plan for one pair of
 * tracks.
 *
 * Nothing here touches PCM; the planner decides *where* a transition happens
 * and *how* ambitious it is. [CrossfadeController] is what executes a plan: it
 * reads the timing fields ([TransitionPlan.transitionStart], [TransitionPlan.fadeSeconds]),
 * cues the incoming track to [TransitionPlan.incomingCueTime] instead of 0,
 * stretches it by [TransitionPlan.incomingPlaybackRate] to align tempo, and
 * renders [TransitionPlan.transitionStyle] as filtering across the blend —
 * a closing low-pass over the outgoing track for [TransitionStyle.DJ_FILTER],
 * a low-end handover at [TransitionPlan.bassSwapFraction] for
 * [TransitionStyle.DJ_BLEND]. The gain curve underneath is equal-power in every
 * case; see [com.music.bitchord.playback.TransitionFilterProcessor].
 */

/** Which crossfade behaviour the listener asked for. */
enum class CrossfadeMode { STANDARD, SMART }

/**
 * The minimal facts about a queue item the planner needs, independent of
 * Media3's `MediaItem` — kept separate so this file stays pure and testable
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
private const val AUTO_MIN_SECONDS = 4.0
private const val AUTO_FAST_TRACK_MIN_SECONDS = 6.0
private const val AUTO_TRANSITION_MAX_SECONDS = 12.0
private const val AUTO_FALLBACK_SECONDS = 8.0

/** Below this a track would spend too much of itself transitioning to be worth planning. */
private const val MIN_SMART_DURATION_SECONDS = 45.0

/** Leave enough incoming material after a calibrated handoff to avoid landing in its outro. */
private const val MIN_INCOMING_CLEARANCE_SECONDS = 5.0

private val KEY_INDEX = mapOf(
    "C" to 0, "C♯" to 1, "D♭" to 1, "D" to 2, "D♯" to 3, "E♭" to 3,
    "E" to 4, "F" to 5, "F♯" to 6, "G♭" to 6, "G" to 7, "G♯" to 8,
    "A♭" to 8, "A" to 9, "A♯" to 10, "B♭" to 10, "B" to 11,
)

/** Anything matching this is spoken or already a performance; mixing it is never wanted. */
private val BLOCKED_TEXT = Regex(
    """\b(podcast|episode|audiobook|live|concert|performance)\b""",
    RegexOption.IGNORE_CASE,
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
}

/**
 * The gesture used inside an unmatched-tempo [TransitionStyle.DJ_FILTER].
 *
 * This is deterministic per pair rather than random per planner tick: the
 * planner runs repeatedly while a song plays, and a genuinely random answer
 * would make the marker and the renderer change their minds before arming.
 */
enum class FilterTransitionVariant {
    /** Strong complementary high/low-pass sweep. */
    SWEEP,

    /** Lighter colour change with a conspicuous low-end and fader handoff. */
    BASS_HANDOFF,

    /** Filter ride whose outgoing side trails away through the beat-synced echo. */
    ECHO_RIDE,
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
    /**
     * Advanced Automix: the speed-up the *outgoing* track is brought up to
     * over its last beats before the blend, when it is the slower of the two.
     * Tempo only ever moves up, so at most one of this and
     * [incomingPlaybackRate] is above 1, and neither is ever below it.
     */
    val outgoingPlaybackRate: Double = 1.0,
    val handoffStartSeconds: Double = 0.0,
    val handoffDuration: Double = 0.0,
    val pickupSeconds: Double = 0.0,
    val transitionBeats: Int = 0,
    val bassSwap: Boolean = false,
    val handoffFraction: Double = HANDOFF_FRACTION,
    val bedPosition: Double = BED_POSITION,
    val bassSwapFraction: Double = 0.7,
    val filterSweep: Double = 0.0,
    /** The stable per-pair gesture for [TransitionStyle.DJ_FILTER]. */
    val filterVariant: FilterTransitionVariant = FilterTransitionVariant.SWEEP,
    /**
     * How strongly the two tracks are expected to be singing over each other
     * through this overlap, 0..1; see [vocalOverlapAmount].
     *
     * Separate from [filterSweep] because they answer to different things.
     * [filterSweep] is a property of the *style* — a filter ride is what an
     * unmatched pair gets instead of a beat-matched blend — and a blend
     * deliberately asks for none of it. This is a property of the *material*, and
     * it applies whatever the style: two tempo-matched vocals sitting on the same
     * grid is the case a blend handles worst, precisely because nothing about the
     * arrangement is going to separate them.
     *
     * Zero whenever either track lacks a vocal mask, which leaves every style
     * rendering exactly as it did before this existed.
     */
    val vocalOverlap: Double = 0.0,
    /**
     * The tempi the overlap is built on, which are **not** the analyses' raw
     * BPMs: the incoming one has been folded into the outgoing one's octave.
     * Zero when the plan is not beat-matched.
     */
    val outgoingBpm: Double = 0.0,
    val incomingBpm: Double = 0.0,
    /**
     * One beat of the outgoing grid in seconds, or 0 when that grid is not
     * trusted. Advanced Automix lands its gain and filter moves on these beats.
     */
    val beatSeconds: Double = 0.0,
    /**
     * Both grids are trusted and the tempi are matched, so the renderer may
     * nudge the incoming track's speed to hold the two beats in phase.
     */
    val phaseLock: Boolean = false,
    /**
     * Advanced Automix echo out: the delay time, in seconds, the outgoing
     * track's last beat repeats at as it leaves, or 0 for no echo. Planned only
     * where the outgoing file has at least [ECHO_REPEATS] of these left after
     * the transition ends, because the tail is generated from the outgoing
     * player's own (silenced) audio and dies with it.
     */
    val echoSeconds: Double = 0.0,
    /** Why the policy landed where it did, when it declined to be more ambitious. */
    val policyReasons: List<String> = emptyList(),
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

private fun trackDurationSeconds(track: TransitionTrackInfo?): Double =
    if (track == null || track.durationMs <= 0) 0.0 else track.durationMs / 1000.0

private fun itemText(track: TransitionTrackInfo?): String =
    if (track == null) "" else listOf(track.title, track.artist, track.album)
        .filter { it.isNotBlank() }
        .joinToString(" ")

/** A small stable hash so the same queue pair always receives the same gesture on every target. */
private fun filterVariantFor(current: TransitionTrackInfo?, next: TransitionTrackInfo?): FilterTransitionVariant {
    var hash = 0xCBF29CE484222325uL
    val key = "${current?.id.orEmpty()}\u0000${next?.id.orEmpty()}"
    for (character in key) {
        hash = (hash xor character.code.toULong()) * 0x100000001B3uL
    }
    return FilterTransitionVariant.entries[(hash % FilterTransitionVariant.entries.size.toULong()).toInt()]
}

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

/** Folds [nextBpm] into the same octave as [currentBpm] and returns the ratio between them. */
private fun normalizedTempoRatio(currentBpm: Double, nextBpm: Double): Double {
    if (currentBpm <= 0 || nextBpm <= 0) return 1.0
    var ratio = nextBpm / currentBpm
    while (ratio > 1.5) ratio /= 2
    while (ratio < 0.67) ratio *= 2
    return ratio
}

private fun splitKey(key: String): Pair<Int?, String?> {
    val parts = key.trim().split(' ')
    return KEY_INDEX[parts.firstOrNull()] to parts.getOrNull(1)
}

private fun keyDistance(left: String, right: String): Int? {
    val (leftIndex, leftMode) = splitKey(left)
    val (rightIndex, rightMode) = splitKey(right)
    if (leftIndex == null || rightIndex == null) return null
    val pitchDistance = min((leftIndex - rightIndex + 12) % 12, (rightIndex - leftIndex + 12) % 12)
    return pitchDistance + if (leftMode != null && rightMode != null && leftMode != rightMode) 1 else 0
}

private fun harmonicallyCompatible(left: String, right: String): Boolean {
    val (leftIndex, leftMode) = splitKey(left)
    val (rightIndex, rightMode) = splitKey(right)
    if (leftIndex == null || rightIndex == null) return false
    val distance = min((leftIndex - rightIndex + 12) % 12, (rightIndex - leftIndex + 12) % 12)
    if (leftMode != null && rightMode != null && leftMode != rightMode) return distance <= 1
    // A fifth is as close as a second here: it is the move every DJ makes.
    return distance <= 2 || distance == 5
}

/** A key the analyzer was not confident about is no key at all. */
private fun trustedKey(analysis: TrackAnalysis): String =
    if (analysis.key.isBlank() || analysis.keyConfidence < 0.25) "" else analysis.key

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
 * Snaps a transition start onto the outgoing track's grid: a phrase boundary
 * if one is near, a downbeat otherwise, and the raw target when neither is.
 */
private fun alignedTransitionStart(
    analysis: TrackAnalysis,
    target: Double,
    end: Double,
    preferEarlier: Boolean,
    minimum: Double,
): Double {
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

/**
 * However well a later drop scores, a transition never cues the incoming song
 * more than this far into itself. Without it a main drop two minutes in was a
 * perfectly good mix-in candidate, and the listener arrived at a song with
 * twenty seconds left.
 */
const val MAX_INCOMING_SKIP_FRACTION = 0.25

/** The deepest an incoming song of [length] seconds may be cued; unbounded when the length is unknown. */
fun latestIncomingCue(length: Double): Double =
    if (length.isFinite() && length > 0) length * MAX_INCOMING_SKIP_FRACTION else Double.POSITIVE_INFINITY

/**
 * Where the incoming track's arrangement arrives: the point the outgoing
 * track should be gone by. Never later than [latest]; a candidate past it is
 * passed over for the best one before it.
 */
 fun incomingCuePoint(analysis: TrackAnalysis, latest: Double = Double.POSITIVE_INFINITY): Double {
    rankMixInCandidates(analysis).firstOrNull { it.time <= latest }?.let { return it.time }

    val interval = analysis.beatInterval.orZero().takeIf { it > 0 }
        ?: if (analysis.bpm.orZero() > 0) 60 / analysis.bpm else 0.0
    val downbeats = analysis.downbeats
    fun within(time: Double): Double =
        if (time <= latest) time else nearestAtOrBefore(downbeats, latest) ?: latest

    val analyzedMixIn = analysis.mixInTime
    if (analyzedMixIn.isFinite() && analyzedMixIn > 0) {
        return within(nearestTimedValue(downbeats, analyzedMixIn, max(0.5, interval * 2)) ?: analyzedMixIn)
    }

    val pickup = max(
        0.0,
        analysis.introEndTime.orZero().takeIf { it != 0.0 }
            ?: (analysis.audibleStartTime ?: analysis.pickupTime).orZero().takeIf { it != 0.0 }
            ?: analysis.firstBeat.orZero(),
    )
    val duration = analysis.duration.orZero().takeIf { it != 0.0 } ?: 300.0
    if (pickup > 0 && pickup < duration - 10) {
        downbeats.firstOrNull { it >= pickup }?.let { return within(it) }
    }
    val phrases = analysis.phraseBoundaries
    if (phrases.size > 1 && phrases[1] > 4) return within(phrases[1])
    if (downbeats.size >= 8) return within(downbeats[min(8, downbeats.size - 1)].orZero())
    return within(pickup)
}

/**
 * [plan] with its incoming side pulled back to within [latestIncomingCue] of
 * [nextLength], by whole incoming bars so a beat-aligned entry stays aligned.
 * The backstop behind the capped candidate choice above, for every path —
 * an arrangement overlap or a stretch can still carry a cue past the line.
 */
private fun capIncomingSkip(plan: TransitionPlan, nextAnalysis: TrackAnalysis, nextLength: Double): TransitionPlan {
    val latest = latestIncomingCue(nextLength)
    if (plan.incomingCueTime <= latest) return plan
    val beat = nextAnalysis.beatInterval.orZero().takeIf { it > 0 }
        ?: if (nextAnalysis.bpm.orZero() > 0) 60 / nextAnalysis.bpm else 0.0
    val over = plan.incomingCueTime - latest
    val shift = if (beat > 0) ceil(over / (4 * beat)) * 4 * beat else over
    val cue = max(0.0, plan.incomingCueTime - shift)
    val moved = plan.incomingCueTime - cue
    return plan.copy(
        incomingCueTime = cue,
        incomingHandoffTime = max(cue, plan.incomingHandoffTime - moved),
    )
}

/** Where the incoming track first makes sound, so the fade is not cued into its lead-in silence. */
private fun incomingStartPoint(analysis: TrackAnalysis): Double =
    listOfNotNull(analysis.audibleStartTime, analysis.pickupTime, analysis.firstBeat)
        .firstOrNull { it.isFinite() && it >= 0 } ?: 0.0

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
 const val ARRANGEMENT_OVERLAP_BEATS = 8

/**
 * One continuous equal-power fade across the whole overlap. 0.5/0.5 is the
 * plain symmetric crossfade, which is exactly the sin/cos pair
 * [com.music.bitchord.playback.CrossfadeController] rides — so at these values
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

/**
 * How far the outgoing track's low-pass sweep travels by the end of the
 * overlap, as a fraction of a full ride. 1.0 is the whole way down to
 * [com.music.bitchord.playback.CrossfadeController.FILTER_FLOOR_HZ].
 */
const val FILTER_SWEEP = 1.0

/** The outgoing track must have this much audio before the overlap and the incoming this much after it. */
private const val MIN_CLEARANCE_SECONDS = 5.0

private fun averageEnergy(curve: List<EnergySample>, from: Double, until: Double): Double? {
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
    val before = averageEnergy(curve, at - windowSeconds, at) ?: return null
    val after = averageEnergy(curve, at, at + windowSeconds) ?: return null
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
): Double {
    if (overlapSeconds <= 0) return DEFAULT_BASS_SWAP_FRACTION

    val latestFraction = min(MAX_BASS_SWAP_FRACTION, BASS_SWAP_MAX_SECONDS / overlapSeconds)
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

/**
 * Where the incoming track takes over: the best-ranked mix-in candidate no
 * later than [latest], snapped to a downbeat.
 */
fun incomingMixInPoint(analysis: TrackAnalysis, latest: Double = Double.POSITIVE_INFINITY): Double? {
    val beatSeconds = analysis.beatInterval.orZero().takeIf { it > 0 }
        ?: if (analysis.bpm.orZero() > 0) 60 / analysis.bpm else 0.0
    val tolerance = max(0.5, beatSeconds * 2)
    val target = listOfNotNull(
        rankMixInCandidates(analysis).firstOrNull { it.time <= latest }?.time,
        analysis.mixInTime,
    )
        .firstOrNull { it.isFinite() && it > 0 && it <= latest }
        ?: return null
    return nearestValue(analysis.downbeats, target, tolerance)?.takeIf { it <= latest } ?: target
}

/** Where the incoming track first makes sound. */
fun incomingAudibleStart(analysis: TrackAnalysis): Double = audibleStartOf(analysis)

/** Plans one beat-matched transition between [analysis] and [nextAnalysis]. */
fun planWsolaTransition(
    analysis: TrackAnalysis,
    nextAnalysis: TrackAnalysis,
    duration: Double = 0.0,
    nextDuration: Double = 0.0,
    advanced: Boolean = false,
): WsolaPlanResult {
    val policy = assessTransitionTier(analysis, nextAnalysis, advanced)
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

    val incomingDropTime = incomingMixInPoint(nextAnalysis, latestIncomingCue(incomingLength))
    if (incomingDropTime == null || !incomingDropTime.isFinite() || incomingDropTime < 0) {
        return WsolaPlanResult.Refused("incoming-mix-in")
    }

    val contentEnd = analysis.contentEndTime.orZero().takeIf { it != 0.0 } ?: outgoingLength
    val mixOutAnchor = resolveMixOutAnchor(analysis, contentEnd = contentEnd, duration = outgoingLength)
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
    val cappedByOverlap = floor(floor(MAX_OVERLAP_SECONDS / incomingBeatSeconds) / 4).toInt() * 4
    if (cappedByOverlap < MIN_FADE_BEATS) return WsolaPlanResult.Refused("overlap-too-long")
    var fadeBeats = minOf(
        MAX_FADE_BEATS,
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
    val transitionStart = nearestAtOrBefore(analysis.downbeats, startTarget) ?: startTarget
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
    advanced: Boolean,
): TransitionPlan? {
    if (!harmonicallyCompatible(trustedKey(analysis), trustedKey(nextAnalysis))) return null

    val planned = planWsolaTransition(
        analysis = analysis,
        nextAnalysis = nextAnalysis,
        duration = length,
        nextDuration = nextLength,
        advanced = advanced,
    ) as? WsolaPlanResult.Planned ?: return null
    // Advanced mode never slows the incoming song or retimes the audible
    // outgoing song. A phrase switch that requires either degrades to the
    // vocal-aware filter planner below.
    if (advanced && planned.stretchRatio < 1.0) return null

    val overlap = planned.transitionEnd - planned.transitionStart
    val renderedIncomingRate = incomingRateFor(planned.stretchRatio, advanced)
    return TransitionPlan(
        markerVisible = true,
        transitionStart = planned.transitionStart,
        transitionEnd = planned.transitionEnd,
        fadeSeconds = overlap,
        handoffStartSeconds = 0.0,
        handoffDuration = overlap,
        incomingCueTime = planned.incomingCueTime,
        incomingHandoffTime = planned.incomingHandoffTime,
        incomingPlaybackRate = renderedIncomingRate,
        outgoingPlaybackRate = outgoingRateFor(planned.stretchRatio, advanced),
        pickupSeconds = incomingAudibleStart(nextAnalysis),
        transitionBeats = planned.beats,
        bassSwap = true,
        handoffFraction = planned.handoffFraction,
        bedPosition = planned.bedPosition,
        bassSwapFraction = planned.bassSwapFraction,
        // Deliberately not `planned.filterSweep`. A phrase switch is the one
        // case where both decks are genuinely on the same grid, and the move
        // there is to hand the low end over on a beat, not to hide the outgoing
        // track behind a filter — filtering a blend this well aligned would
        // throw away the reason it was worth aligning. The renderer reads a
        // nonzero sweep as "ride the filter instead", so this says zero.
        filterSweep = 0.0,
        // The separation this style *does* need, and the one it cannot get from
        // alignment. Two tracks on a shared grid are the worst case for
        // overlapping voices precisely because nothing about the arrangement
        // pulls them apart — they sit in the same bar, in the same range, for the
        // whole blend. The renderer uses this to deepen the entry high-pass and
        // the exit low-pass without turning the blend into a filter ride.
        vocalOverlap = plannedVocalOverlap(
            analysis = analysis,
            nextAnalysis = nextAnalysis,
            transitionStart = planned.transitionStart,
            transitionEnd = planned.transitionEnd,
            incomingCueTime = planned.incomingCueTime,
            incomingPlaybackRate = renderedIncomingRate,
        ),
        outgoingBpm = planned.outgoingBpm,
        incomingBpm = planned.incomingBpm,
        beatSeconds = 60 / planned.outgoingBpm,
        // Playback speed remains fixed once either deck is audible.
        phaseLock = false,
        transitionStyle = TransitionStyle.DJ_BLEND,
    )
}

private data class Overlap(
    val overlap: Double,
    val transitionBeats: Int,
    val incomingPlaybackRate: Double,
)

/** The tempo ratios [adaptiveOverlap] stretches the incoming track across; outside it the grids flam. */
private val STRETCH_WINDOW = 0.9..1.1

/**
 * How fast the incoming track plays for a blend whose incoming timeline must
 * run [mediaRatio] times the outgoing one's. Classically that is simply the
 * ratio, up or down. Advanced only speeds up the incoming track. Changing the
 * already-audible outgoing deck's AudioTrack rate causes discontinuities on
 * some Android routes, so a faster incoming track uses a filter transition.
 */
private fun incomingRateFor(mediaRatio: Double, advanced: Boolean): Double =
    roundRate(if (advanced && mediaRatio < 1) 1.0 else mediaRatio)

/** The audible outgoing deck is never tempo-shifted. */
private fun outgoingRateFor(mediaRatio: Double, advanced: Boolean): Double =
    1.0

private fun roundRate(rate: Double): Double = (rate * 10000).roundToInt() / 10000.0

/** Whole-track vocal likelihood above which both songs are taken to be sung through. */
private const val VOCAL_CONFLICT_PROBABILITY = 0.62

// ---------------------------------------------------------------------------
// Advanced Automix. Everything below is read only when the listener turned it
// on; with it off, [PairTraits] is never built and each branch that consults
// it falls through to the classic plan unchanged.
// ---------------------------------------------------------------------------

/** Longest overlap a calm-into-calm pair may stretch to, in beats and in seconds. */
private const val MAX_ADVANCED_BEATS = 32
private const val CALM_MAX_SECONDS = 16.0

/** How much of each side's edge is averaged to judge its energy. */
private const val ENERGY_WINDOW_BEATS = 8

/** A window below this fraction of its own track's mean energy is calm. */
private const val CALM_ENERGY = 0.7

/** A planned overlap singing over itself this much is worth trying another exit for. */
private const val VOCAL_REROUTE_OVERLAP = 0.3

/** At this overlap, musical variety yields to keeping only one lead vocal prominent. */
private const val VOCAL_SAFE_FALLBACK_THRESHOLD = 0.18

/** Conservative overlap used when both tracks look sung but a timed vocal mask is unavailable. */
private const val VOCAL_MASK_MISSING_PROTECTION = 0.55

/** ...and an alternative is taken only if it at least halves the collision. */
private const val VOCAL_REROUTE_GAIN = 0.5
private const val MAX_REROUTE_CANDIDATES = 3

/**
 * What an advanced plan needs to know about a pair beyond its tempo. Built
 * once per tick and shared by every exit [planTransition] tries, so the
 * whole-curve passes here are never repeated per candidate.
 */
private class PairTraits(analysis: TrackAnalysis, nextAnalysis: TrackAnalysis) {
    private val currentBpm = analysis.bpm.orZero()
    private val nextBpm = nextAnalysis.bpm.orZero()

    /** Too far apart for either to be sped up to the other, so both kicks would flam. */
    val tempoFar = currentBpm > 0 && nextBpm > 0 && speedUpBetween(currentBpm, nextBpm) - 1 > MAX_SPEED_UP
    val keyClash: Boolean = run {
        val left = trustedKey(analysis)
        val right = trustedKey(nextAnalysis)
        left.isNotEmpty() && right.isNotEmpty() && !harmonicallyCompatible(left, right)
    }
    val vocalConflict = analysis.vocalProbability >= VOCAL_CONFLICT_PROBABILITY &&
        nextAnalysis.vocalProbability >= VOCAL_CONFLICT_PROBABILITY

    /** Both grids trusted and the stretch will match the tempi, so the beats can be held in phase. */
    val phaseLockable = currentBpm > 0 && nextBpm > 0 && !tempoFar &&
        analysis.beatConfidence.orZero() >= MIN_BEATMATCH_CONFIDENCE &&
        nextAnalysis.beatConfidence.orZero() >= MIN_BEATMATCH_CONFIDENCE

    /** Whether the outgoing grid is trusted enough to place a transition's edges on. */
    val outgoingGrid = currentBpm > 0 && analysis.beatConfidence.orZero() >= MIN_DJ_CONFIDENCE
    val outgoingMeanEnergy = meanEnergy(analysis.energyCurve)
    val incomingMeanEnergy = meanEnergy(nextAnalysis.energyCurve)
}

private fun meanEnergy(curve: List<EnergySample>): Double {
    var sum = 0.0
    var count = 0
    for (point in curve) {
        if (point.energy.isFinite() && point.energy >= 0) {
            sum += point.energy
            count++
        }
    }
    return if (count > 0) sum / count else 0.0
}

/**
 * How the energy either side of the join scales the overlap: 2 for a quiet
 * outro into a quiet intro, which wants a long blend, and 1 otherwise or
 * without evidence. Only ever lengthens: halving two peaks meeting made
 * blends noticeably shorter than classic Automix, which read as a regression.
 */
private fun energyScale(
    analysis: TrackAnalysis,
    nextAnalysis: TrackAnalysis,
    traits: PairTraits,
    exitAt: Double,
    beatSeconds: Double,
): Double {
    if (beatSeconds <= 0 || traits.outgoingMeanEnergy <= 0 || traits.incomingMeanEnergy <= 0) return 1.0
    val window = ENERGY_WINDOW_BEATS * beatSeconds
    val entry = incomingStartPoint(nextAnalysis)
    val out = averageEnergy(analysis.energyCurve, exitAt - window, exitAt) ?: return 1.0
    val into = averageEnergy(nextAnalysis.energyCurve, entry, entry + window) ?: return 1.0
    val outLevel = out / traits.outgoingMeanEnergy
    val inLevel = into / traits.incomingMeanEnergy
    return if (outLevel < CALM_ENERGY && inLevel < CALM_ENERGY) 2.0 else 1.0
}

/**
 * [seconds] rounded to the nearest whole bar, or down to one when the nearest
 * would pass [maximum]; unchanged when not even one bar fits.
 */
private fun wholeBars(seconds: Double, beatSeconds: Double, maximum: Double): Double {
    if (beatSeconds <= 0) return seconds
    val bar = 4 * beatSeconds
    var bars = round(seconds / bar)
    if (bars * bar > maximum + 1e-6) bars = floor(maximum / bar + 1e-6)
    return if (bars >= 1) bars * bar else seconds
}

/** The outgoing downbeat at or up to [tolerance] before [time], so an exit lands on the one. */
private fun onDownbeatBefore(analysis: TrackAnalysis, time: Double, tolerance: Double): Double =
    timedValueNearOrBefore(analysis.downbeats, time, tolerance) ?: time

/**
 * How long a mix should run when the tracks are related but not phrase-switchable.
 *
 * With [traits] (Advanced Automix) the length follows how well the pair
 * agrees: a key or vocal clash is mixed in 8 beats rather than drawn out, any
 * other pair earns 16, a calm join doubles either, and the result is whole
 * bars. Without it this is the classic rule, which gave *clashing* keys the
 * longer overlap.
 */
private fun adaptiveOverlap(
    analysis: TrackAnalysis,
    nextAnalysis: TrackAnalysis,
    traits: PairTraits? = null,
    energy: Double = 1.0,
    filterVariant: FilterTransitionVariant = FilterTransitionVariant.SWEEP,
): Overlap {
    val currentBpm = analysis.bpm.orZero()
    val nextBpm = nextAnalysis.bpm.orZero()
    if (currentBpm <= 0 || nextBpm <= 0) {
        return Overlap(AUTO_FALLBACK_SECONDS, 0, 1.0)
    }

    val ratio = normalizedTempoRatio(currentBpm, nextBpm)
    val transitionBeats = if (traits == null) {
        val distance = keyDistance(trustedKey(analysis), trustedKey(nextAnalysis))
        val vocalConflict = analysis.vocalProbability >= VOCAL_CONFLICT_PROBABILITY &&
            nextAnalysis.vocalProbability >= VOCAL_CONFLICT_PROBABILITY
        if (!vocalConflict && (abs(1 - ratio) > 0.07 || (distance != null && distance > 4))) 16 else 8
    } else {
        // Tempo alone keeps the long ride: the one-kick bass handover is what
        // stops two unmatched grids flamming. Unmatched fallbacks deliberately
        // stay compact and vary by gesture; the old 10-12 second sweep made
        // every fallback sound like the same ordinary crossfade.
        val base = if (traits.tempoFar) {
            when (filterVariant) {
                FilterTransitionVariant.SWEEP -> 12
                FilterTransitionVariant.BASS_HANDOFF,
                FilterTransitionVariant.ECHO_RIDE -> 8
            }
        } else if (traits.keyClash || traits.vocalConflict) {
            8
        } else {
            16
        }
        (base * energy).roundToInt().coerceIn(MIN_FADE_BEATS, MAX_ADVANCED_BEATS)
    }
    val beatSeconds = 60 / currentBpm
    val minimumOverlap = if (currentBpm >= 140) AUTO_FAST_TRACK_MIN_SECONDS else AUTO_MIN_SECONDS
    val maximumOverlap = if (traits != null && energy > 1) CALM_MAX_SECONDS else AUTO_TRANSITION_MAX_SECONDS
    val overlap = clamp(transitionBeats * beatSeconds, minimumOverlap, maximumOverlap)

    return Overlap(
        overlap = if (traits != null) wholeBars(overlap, beatSeconds, maximumOverlap) else overlap,
        transitionBeats = transitionBeats,
        // The timeline ratio the blend runs at; [mixPlan] splits it between
        // the two players. Advanced stretches anything a speed-up can meet.
        incomingPlaybackRate = when {
            traits != null -> if (traits.tempoFar) 1.0 else roundRate(1 / ratio)
            ratio in STRETCH_WINDOW -> roundRate(clamp(1 / ratio, 0.9, 1.1))
            else -> 1.0
        },
    )
}

/**
 * How many times an echo repeats before it is inaudible. The renderer's
 * feedback is sized to this, and the planner reserves this many repeats of
 * outgoing audio after the exit, so the two cannot disagree about how long a
 * tail lasts.
 */
const val ECHO_REPEATS = 5

/**
 * Where in a filter ride the outgoing track echoes out, before the renderer
 * snaps it to a bar: the same point its low end hands over.
 */
const val FILTER_ECHO_AT = 0.5

/** The longest echo time; a slow track's beat is clamped to it. */
const val MAX_ECHO_SECONDS = 1.0

/** Slack past the tail, so the outgoing file never ends under the last repeat. */
private const val ECHO_ROOM_MARGIN_SECONDS = 0.5

/**
 * A one-beat echo. The dotted three-quarter beat that was here first
 * syncopates against the outgoing track, which is its appeal on a matched
 * pair — and over an incoming track at another tempo it read as the outgoing
 * song glitching. On the beat, the repeats sound like the song, trailing off.
 */
private fun echoSecondsFor(beatSeconds: Double): Double = min(beatSeconds, MAX_ECHO_SECONDS)

fun echoTailSeconds(echoSeconds: Double): Double = echoSeconds * ECHO_REPEATS

private fun standardTransition(
    length: Double,
    playbackTime: Double,
    fadeSeconds: Double,
    minFadeSeconds: Double,
    reason: String = "standard",
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
 * Plans the transition out of [currentTrack] and into [nextTrack].
 *
 * Called on every playback tick; the returned plan describes the transition
 * whether or not it has started yet.
 *
 * @param albumSequential true only when this is an album genuinely being
 *   played through in order, which is the sole case that earns a gapless
 *   handoff instead of a mix.
 * @param currentTime the outgoing track's playhead, in seconds.
 * @param advanced Advanced Automix: length from how well the pair agrees and
 *   how energetic the join is, edges on whole bars and downbeats, speed-ups
 *   of up to 10% to beat-match, an echo out where the outgoing track leaves
 *   singing, and another exit tried when the planned one sings over the
 *   incoming track. Off leaves every plan exactly as before.
 */
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
    advanced: Boolean = false,
): TransitionPlan {
    val length = max(duration.orZero(), trackDurationSeconds(currentTrack))
    val playbackTime = max(0.0, currentTime.orZero())
    if (length <= 0) return blocked("no-duration")

    val standardFade = clamp(fadeSeconds, minFadeSeconds, 12.0)
    if (mode != CrossfadeMode.SMART) {
        return standardTransition(length, playbackTime, standardFade, minFadeSeconds)
    }

    if (length < MIN_SMART_DURATION_SECONDS) {
        return blocked("short-duration-guard", transitionStart = length, transitionEnd = length)
    }

    val analyzedContentEnd = analysis.contentEndTime.orZero().takeIf { it != 0.0 } ?: length
    val finalMixAnchor = if (analyzedContentEnd > 0 && analyzedContentEnd <= length) {
        analyzedContentEnd
    } else {
        length
    }
    // Ranked once and kept: the best is the anchor (what [resolveMixOutAnchor]
    // answers), and the rest are where an advanced plan looks for a vocal-free exit.
    val rankedMixOuts = rankMixOutCandidates(analysis, finalMixAnchor, length)
    val mixOutAnchor = rankedMixOuts.firstOrNull()
        ?.let { MixOutAnchor(it.time, it.type, it.discardedMusicSeconds) }
        ?: MixOutAnchor(finalMixAnchor, "content_end", 0.0)
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
        )
    }

    val preferredMixAnchor = min(length, mixOutAnchor.time)
    val mixAnchor =
        if (playbackTime >= preferredMixAnchor - 0.05 && preferredMixAnchor < finalMixAnchor - 1) {
            finalMixAnchor
        } else {
            preferredMixAnchor
        }

    val policy = assessTransitionTier(analysis, nextAnalysis, advanced)
    if (policy.tier == TransitionTier.PLAIN_CROSSFADE) {
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

    val nextLength = max(nextAnalysis.duration.orZero(), trackDurationSeconds(nextTrack))

    phraseSwitch(analysis, nextAnalysis, length, nextLength, advanced)
        ?.takeIf { playbackTime < it.transitionEnd }
        ?.let { plan ->
            val started = playbackTime >= plan.transitionStart
            return capIncomingSkip(
                plan.copy(
                    shouldStart = started,
                    policyReasons = policy.reasons,
                    reason = if (started) "smart-phrase-switch" else "before-phrase-switch",
                ),
                nextAnalysis,
                nextLength,
            )
        }

    val traits = if (advanced) PairTraits(analysis, nextAnalysis) else null
    val filterVariant = if (traits != null) filterVariantFor(currentTrack, nextTrack) else FilterTransitionVariant.SWEEP
    val primary = mixPlan(
        analysis, nextAnalysis, mixAnchor, mixOutAnchor.type, length, nextLength, traits, filterVariant,
    )
    // An exit where the outgoing track is still singing over the incoming one
    // is worth trading for another the analysis ranked, if one halves the
    // collision. Only planned while it could still be taken: once the playhead
    // is past a start, that plan has already been armed or lost.
    val plan = if (traits != null &&
        primary.vocalOverlap > VOCAL_REROUTE_OVERLAP &&
        playbackTime < primary.transitionStart
    ) {
        rankedMixOuts.asSequence()
            .filter { abs(it.time - mixAnchor) > 1.0 && it.time <= length }
            .take(MAX_REROUTE_CANDIDATES)
            .map { mixPlan(analysis, nextAnalysis, it.time, it.type, length, nextLength, traits, filterVariant) }
            .filter { playbackTime < it.transitionStart }
            .minByOrNull { it.vocalOverlap }
            ?.takeIf { it.vocalOverlap <= primary.vocalOverlap * VOCAL_REROUTE_GAIN }
            ?: primary
    } else {
        primary
    }
    val started = playbackTime >= plan.transitionStart
    return capIncomingSkip(
        plan.copy(
            shouldStart = started,
            policyReasons = policy.reasons,
            reason = if (started) plan.reason else "before-${plan.reason}",
        ),
        nextAnalysis,
        nextLength,
    )
}

/**
 * The adaptive overlap ending at [mixAnchor]: a beat-matched blend when the
 * tempi agree, a filter ride when they don't. Timing only; the caller
 * decides whether it has started.
 */
private fun mixPlan(
    analysis: TrackAnalysis,
    nextAnalysis: TrackAnalysis,
    mixAnchor: Double,
    mixOutType: String,
    length: Double,
    nextLength: Double,
    traits: PairTraits?,
    filterVariant: FilterTransitionVariant,
): TransitionPlan {
    val currentBpm = analysis.bpm.orZero()
    val nextBpm = nextAnalysis.bpm.orZero()
    val gridBeatSeconds = if (traits?.outgoingGrid == true) 60 / currentBpm else 0.0
    val energy = if (traits != null) {
        energyScale(analysis, nextAnalysis, traits, mixAnchor, gridBeatSeconds)
    } else {
        1.0
    }
    val (overlap, transitionBeats, incomingPlaybackRate) = adaptiveOverlap(
        analysis, nextAnalysis, traits, energy, filterVariant,
    )
    val handoffBpm = if (currentBpm > 0) currentBpm else nextBpm
    // Advanced also blends any pair a speed-up matches on two trusted grids —
    // the 5-10% apart that used to be left to a filter ride.
    val sameBeatBlend = (traits == null || incomingPlaybackRate >= 1.0) && (
        currentBpm > 0 && nextBpm > 0 &&
            abs(1 - normalizedTempoRatio(currentBpm, nextBpm)) <= 0.05 &&
            (analysis.beatConfidence.orZero() >= 0.2 || nextAnalysis.beatConfidence.orZero() >= 0.2) ||
            traits?.phaseLockable == true
        )
    val outgoingArrangementOverlap =
        if (sameBeatBlend && mixOutType == "content_end") {
            min(ARRANGEMENT_OVERLAP_BEATS * 60 / currentBpm, MAX_DISCARDED_MUSIC_SECONDS)
        } else {
            0.0
        }
    val arrangementEnd = max(0.0, mixAnchor - outgoingArrangementOverlap)
    // Advanced: the exit pulled back onto a downbeat, so whatever the incoming
    // track lands on at the end of the overlap lands on the one.
    val mixEnd = if (gridBeatSeconds > 0) {
        onDownbeatBefore(analysis, arrangementEnd, 4 * gridBeatSeconds)
    } else {
        arrangementEnd
    }
    val maxBeats = if (traits != null) MAX_ADVANCED_BEATS.toDouble() else AUTO_TRANSITION_MAX_BEATS
    val fallbackMaxSeconds = when (filterVariant) {
        FilterTransitionVariant.SWEEP -> 8.0
        FilterTransitionVariant.BASS_HANDOFF -> 7.0
        FilterTransitionVariant.ECHO_RIDE -> 7.0
    }
    val maxSeconds = when {
        traits != null && !sameBeatBlend -> fallbackMaxSeconds
        traits != null && energy > 1 -> CALM_MAX_SECONDS
        else -> AUTO_TRANSITION_MAX_SECONDS
    }
    val maximumOverlap = minOf(
        if (handoffBpm > 0) (maxBeats * 60) / handoffBpm else maxSeconds,
        maxSeconds,
        mixEnd * 0.4,
        if (nextLength > 0) nextLength * 0.4 else maxSeconds,
    )
    val handoffBeats = if (sameBeatBlend) 8 else 4
    val beatSeconds = if (handoffBpm > 0) 60 / handoffBpm else 0.5
    val handoffSeconds = if (handoffBpm > 0) {
        clamp((handoffBeats * 60) / handoffBpm, 2.0, if (sameBeatBlend) 6.0 else 5.0)
    } else {
        4.0
    }
    val analyzedPickup = nextAnalysis.audibleStartTime ?: nextAnalysis.pickupTime
    val pickupSeconds = if (analyzedPickup != null && analyzedPickup.isFinite() && analyzedPickup >= 0) {
        analyzedPickup
    } else {
        0.0
    }
    val incomingDropTime = incomingCuePoint(nextAnalysis, latestIncomingCue(nextLength))
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
    val rawIncomingCueTime = incomingStartPoint(nextAnalysis)
    val analyzedIncomingHandoff = nextAnalysis.mixInTime
    val hasIncomingPreroll = analyzedIncomingHandoff.isFinite() &&
        analyzedIncomingHandoff > rawIncomingCueTime + 0.5
    val incomingCueTime = if (hasIncomingPreroll) rawIncomingCueTime else incomingHandoffTime
    val introPreroll = max(
        0.0,
        (if (hasIncomingPreroll) incomingHandoffTime - incomingCueTime else 0.0) /
            max(0.8, incomingPlaybackRate),
    )

    val finalIncomingCueTime: Double
    val transitionStart: Double

    if (sameBeatBlend && beatSeconds > 0) {
        val introDropTime = incomingHandoffTime / max(0.8, incomingPlaybackRate)
        val clamped = clamp(introDropTime, min(12.0, maximumOverlap), maximumOverlap)
        val totalOverlap = if (traits != null) wholeBars(clamped, beatSeconds, maximumOverlap) else clamped
        val targetStart = max(0.0, mixEnd - totalOverlap)
        val earliestTransitionStart = max(0.0, mixEnd - maximumOverlap)
        transitionStart = alignedTransitionStart(
            analysis,
            targetStart,
            mixEnd - 0.05,
            preferEarlier = true,
            minimum = earliestTransitionStart,
        )
        finalIncomingCueTime =
            max(0.0, incomingHandoffTime - (mixEnd - transitionStart) * incomingPlaybackRate)
    } else {
        val desiredOverlap = max(overlap, introPreroll + handoffSeconds * 0.42)
        val clamped = clamp(desiredOverlap, min(handoffSeconds, maximumOverlap), maximumOverlap)
        val actualOverlap = if (traits != null) wholeBars(clamped, beatSeconds, maximumOverlap) else clamped
        val targetStart = max(0.0, mixEnd - actualOverlap)
        val earliestTransitionStart = max(0.0, mixEnd - maximumOverlap)
        transitionStart = alignedTransitionStart(
            analysis,
            targetStart,
            mixEnd - 0.05,
            preferEarlier = desiredOverlap > overlap + 0.5,
            minimum = earliestTransitionStart,
        )
        finalIncomingCueTime = if (hasIncomingPreroll) {
            max(0.0, incomingHandoffTime - (mixEnd - transitionStart) * incomingPlaybackRate)
        } else {
            incomingCueTime
        }
    }

    val alignedOverlap = mixEnd - transitionStart
    val hasBassContent = analysis.lowEnergyCurve.isNotEmpty() || nextAnalysis.lowEnergyCurve.isNotEmpty()
    val measuredVocalOverlap = plannedVocalOverlap(
        analysis = analysis,
        nextAnalysis = nextAnalysis,
        transitionStart = transitionStart,
        transitionEnd = mixEnd,
        incomingCueTime = finalIncomingCueTime,
        incomingPlaybackRate = incomingPlaybackRate,
    )
    // A missing time mask must not mean "instrumental". Whole-track vocal
    // likelihood is less precise, but it is a safe fallback when either side
    // has not produced its mask yet.
    val vocalOverlap = max(
        measuredVocalOverlap,
        if (
            traits?.vocalConflict == true &&
            (analysis.vocalActivityMask.isEmpty() || nextAnalysis.vocalActivityMask.isEmpty())
        ) VOCAL_MASK_MISSING_PROTECTION else 0.0,
    )
    val vocalSafeFallback = !sameBeatBlend && vocalOverlap >= VOCAL_SAFE_FALLBACK_THRESHOLD
    // One stable fallback personality trails the outgoing track on a beat.
    // It is selected per track pair, not per playback attempt, so retrying a
    // transition never changes the plan underneath the renderer.
    val echoSeconds = if (
        gridBeatSeconds > 0 &&
        !sameBeatBlend &&
        !vocalSafeFallback &&
        filterVariant == FilterTransitionVariant.ECHO_RIDE
    ) {
        val echo = echoSecondsFor(gridBeatSeconds)
        val echoPoint = transitionStart + FILTER_ECHO_AT * alignedOverlap
        val room = echoPoint + 2 * gridBeatSeconds + echoTailSeconds(echo) + ECHO_ROOM_MARGIN_SECONDS <= length
        if (room) echo else 0.0
    } else {
        0.0
    }
    val resolvedFilterVariant = when {
        vocalSafeFallback -> FilterTransitionVariant.SWEEP
        filterVariant == FilterTransitionVariant.ECHO_RIDE && echoSeconds <= 0.0 ->
            FilterTransitionVariant.BASS_HANDOFF
        else -> filterVariant
    }
    return TransitionPlan(
        markerVisible = true,
        transitionStart = transitionStart,
        transitionEnd = mixEnd,
        fadeSeconds = alignedOverlap,
        handoffStartSeconds = 0.0,
        handoffDuration = alignedOverlap,
        incomingCueTime = finalIncomingCueTime,
        incomingHandoffTime = incomingHandoffTime,
        incomingPlaybackRate = incomingRateFor(incomingPlaybackRate, traits != null),
        outgoingPlaybackRate = outgoingRateFor(incomingPlaybackRate, traits != null),
        pickupSeconds = pickupSeconds,
        transitionBeats = transitionBeats,
        bassSwap = sameBeatBlend || hasBassContent,
        transitionStyle = if (sameBeatBlend) TransitionStyle.DJ_BLEND else TransitionStyle.DJ_FILTER,
        // The two styles are alternatives, not a scale: a matched pair hands the
        // low end over on a beat and otherwise stays open, while an unmatched
        // pair has no shared grid to hand anything over on and instead pulls the
        // outgoing track behind a closing low-pass. Left at zero on the blend
        // branch so the renderer doesn't do both at once.
        filterSweep = if (sameBeatBlend) 0.0 else FILTER_SWEEP,
        filterVariant = if (sameBeatBlend) FilterTransitionVariant.SWEEP else resolvedFilterVariant,
        vocalOverlap = vocalOverlap,
        beatSeconds = gridBeatSeconds,
        // Tempo is fixed while audible. Live phase-lock pulses changed
        // AudioTrack playback parameters mid-buffer and cracked on some phones.
        phaseLock = false,
        echoSeconds = echoSeconds,
        reason = "smart-duration",
    )
}
