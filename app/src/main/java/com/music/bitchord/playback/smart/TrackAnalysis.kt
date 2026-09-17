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

/**
 * Stored offline analysis for one track, in that track's own timeline seconds.
 *
 * This is the contract between [TrackAnalyzer] and the transition policy.
 * Nothing here is PCM: the policy and planner read these fields and their
 * confidences, and never touch audio. Every field is optional, because the
 * ladder in [assessTransitionTier] is built to degrade on missing evidence
 * rather than to require it; an all-defaults instance is a legitimate input
 * that simply lands on the bottom rung.
 *
 * Phase 1 fills every field from DSP alone (see native/analyzer/), so
 * [beatConfidence] and [vocalActivityMask] are heuristics rather than a
 * trained model's output — the policy already treats them with the same
 * scrutiny it would a model that failed to load.
 */
data class TrackAnalysis(
    /**
     * Blank means "no status was reported", which counts as ready. Any other
     * value must be [STATUS_READY] for the planner to trust the rest of the
     * fields.
     */
    val status: String = "",
    /** Guards against a stale analysis being paired with the wrong track. */
    val trackId: String = "",
    val duration: Double = 0.0,

    val bpm: Double = 0.0,
    /**
     * Seconds per beat. Redundant with [bpm], but the analyzer measures it
     * directly and it survives tempo drift better, so it is preferred
     * wherever both are available.
     */
    val beatInterval: Double = 0.0,
    /**
     * How far the beat grid can be trusted, 0..1. A catalog tempo lookup
     * merges in at 0, so a metadata BPM alone can never authorize
     * beat-matching.
     */
    val beatConfidence: Double = 0.0,
    val downbeats: List<Double> = emptyList(),
    val phraseBoundaries: List<Double> = emptyList(),
    val firstBeat: Double = 0.0,

    val key: String = "",
    val keyConfidence: Double = 0.0,

    /** Where the file starts making sound, and where the first musical event lands. */
    val audibleStartTime: Double? = null,
    val pickupTime: Double? = null,
    val introEndTime: Double = 0.0,
    /** Where the content actually ends, excluding trailing silence. */
    val contentEndTime: Double = 0.0,
    val outroStartTime: Double = 0.0,

    val mixInTime: Double = 0.0,
    val mixOutTime: Double = 0.0,
    val mixInCandidates: List<MixCandidate> = emptyList(),
    val mixOutCandidates: List<MixCandidate> = emptyList(),

    val energyCurve: List<EnergySample> = emptyList(),
    /** Low-band energy, present only when the analyzer ran a band split. Drives the bass swap. */
    val lowEnergyCurve: List<EnergySample> = emptyList(),
    /**
     * Finetune §6.1: the transient fine energy curve, kept in memory only
     * (never persisted — see [AnalysisStore], which drops it on write). Lets
     * [buildupStart] re-derive the §4 gradient for analyses whose stored
     * buildup is absent. Empty for cached, head-only, or failed analyses,
     * where callers fall through to the next fallback.
     */
    val energyCurveFine: List<EnergySample> = emptyList(),
    /**
     * Per-sample vocal activity, indexed against [energyCurve] sample times.
     * Empty, or any length other than the energy curve's, means "no
     * evidence", which never blocks a transition.
     */
    val vocalActivityMask: List<Double> = emptyList(),
    /** Whole-track vocal likelihood, distinct from the per-sample [vocalActivityMask]. */
    val vocalProbability: Double = 0.0,
    // Full-plan P4: master descriptors, re-emitted by the JNI bridge for
    // wash gain-staging (hot crushed masters need less wash) and the
    // loudness normalizer. -70/0 = "unmeasured", which stages nothing.
    /** Integrated loudness in LUFS as computed natively (RMS-0.691, no gating). */
    val loudnessLufs: Double = -70.0,
    /** Peak level in dBFS. */
    val peakDbfs: Double = -70.0,
    /** P95-P20 dynamic range in dB. Small = crushed master. */
    val dynamicRangeDb: Double = 0.0,
    /**
     * Median fundamental over the head window's voiced frames, in Hz — the
     * only measurement that can contradict [key] before a pitch shift is
     * committed. 0 means "unmeasured", which vetoes nothing.
     *
     * A scalar rather than the full curve: the planner only ever asks "does
     * this track sing near its detected key", and a 10 ms curve would cost
     * kilobytes per stored entry for a question one number answers.
     */
    val vocalPitchMedianHz: Double = 0.0,
    /** Mean confidence over the same voiced frames, 0..1. Trusted at 0.5. */
    val pitchConfidence: Double = 0.0,
    /**
     * v2 §2b: structural section labels over the full track, ascending.
     * Computed once by [StructureDetector] from the transient fine curves and
     * persisted — the fine curves themselves are never stored. Empty until a
     * whole-track analysis has run; callers fall back to energy heuristics.
     */
    val structureMap: List<StructureLabel> = emptyList(),
    /** v2 §2b: first DROP label start, else null (see `firstDropSec`). */
    val structuredDropSec: Double? = null,
    /** v2 §2b: first BREAK label start, else null. */
    val structuredBreakSec: Double? = null,
    /** v2 §2b: first OUTRO label start, else null. */
    val structuredOutroSec: Double? = null,
    /** v2 §4: §4-gradient buildup foot, else null (see `buildupStart`). */
    val structuredBuildupSec: Double? = null,
    /** Phase A1: selectFirstDrop winner score (0..~1), else null. Persisted so
     * drop trust survives restart without the transient fine curve. */
    val dropConfidence: Double? = null,
    /** Phase A1: how the buildup foot was found — "gradient", "monotonic",
     * "build_label" or "stored". Null = unknown. */
    val buildupMethod: String? = null,
    /** Phase A1: raw buildup foot before phrase snap, else null. */
    val buildupFootSec: Double? = null,
    /** Phase A1: drop − foot in seconds, else null. */
    val buildupSpanSec: Double? = null,
    /** Phase A1: mean climb minus foot, normalized by track peak, else null. */
    val buildupRise: Double? = null,
    /**
     * Spec finetune §7: the track's own breathing room before the cut — the
     * start of the longest onset gap (>0.25 s) in the last 35% of the track,
     * plus one beat of lookahead. Null when the tail never breathes.
     * Persisted; computed once in [TrackAnalyzer.detectStructure].
     */
    val plainCutBreathSec: Double? = null,
    /**
     * Full-audit P0.2: true when this result came from the head-only pass —
     * the curve/mask below cover the opening window only, not the track.
     * The outgoing side needs tail evidence (mix-out, clash windows), so a
     * provisional result never satisfies the both-sides gate for it; the
     * incoming side only ever reads its entry window, so a provisional mask
     * is exactly the evidence it needs. Never persisted (see AnalysisStore).
     */
    val provisionalHead: Boolean = false,
) {
    /**
     * Whether this analysis actually describes a track, as opposed to standing
     * in for one that has not been analysed or could not be.
     *
     * Both no-analysis states have to be excluded, and they look different: a
     * track nothing has looked at yet has a blank [status], while one whose
     * decode failed is recorded [STATUS_READY] with every field at its default
     * so it is not retried forever. A zero [bpm] is what separates the second
     * from a real result — and it is also the threshold the policy uses, since
     * a tempo outside 40–220 drops a pairing to a plain crossfade anyway.
     *
     * Full-audit Phase 2: deliberately no confidence gate here — a shaky grid
     * still persists, and the policy degrades it downstream instead
     * (beatConfidence < 0.55 misses BEATMATCHED, < 0.28 reads AMBIENT).
     * Gating persist on confidence would silently convert SMART blends to
     * plain crossfades for exactly the tracks that need evidence most.
     */
    val isUsable: Boolean get() = status == STATUS_READY && bpm > 0

    companion object {
        const val STATUS_READY = "ready"
    }
}

/** One point on an energy curve. [energy] is in whatever scale the analyzer chose. */
data class EnergySample(val time: Double, val energy: Double)

/** v2 §2b: one structural section label. Times are track-timeline seconds. */
data class StructureLabel(val start: Double, val end: Double, val type: StructureSectionType)

/** v2 §2b: the eight section kinds the detector emits, first-match-wins. */
enum class StructureSectionType { DROP, BUILD, BREAK, VERSE, CHORUS, INTRO, OUTRO, AMBIENT }

/**
 * A candidate point for a transition to enter or leave on. [score] is the
 * analyzer's own confidence; [type] is what it recognized, and carries its
 * own weight during ranking.
 */
data class MixCandidate(val time: Double, val score: Double, val type: String)

/** A ranked [MixCandidate], carrying the score the policy actually ordered it by. */
data class RankedMixCandidate(
    val time: Double,
    val score: Double,
    val type: String,
    val rankScore: Double,
    /**
     * Seconds of audible music this candidate would skip by ending the
     * transition before the content does. Mix-out only; always 0 for mix-in.
     */
    val discardedMusicSeconds: Double = 0.0,
    /** False when there was no energy curve and [discardedMusicSeconds] is the raw gap instead. */
    val measured: Boolean = true,
)

/** Where a transition should end on the outgoing track, and what it costs to end there. */
data class MixOutAnchor(
    val time: Double,
    val type: String,
    val discardedMusicSeconds: Double,
)

/** The verdict on how ambitious a transition the stored evidence supports. */
data class TransitionPolicyVerdict(
    val tier: TransitionTier,
    /** Ordered most-disqualifying first, so `reasons.first()` is the routing verdict. */
    val reasons: List<String>,
    val beatConfidence: Double,
    /** v2 §5a: harmonic tempo ratio locking bpmA onto bpmB (1.0 = unison). 1.0 by default. */
    val matchedRatio: Double = 1.0,
    /** Multi-candidate §Q2: semitone shift of the incoming key chosen by best-fit
     * search (0 = none). In-memory only; render sites apply it behind their own gates. */
    val candidateShiftSemitones: Int = 0,
)

/**
 * The degradation ladder. Ambition falls in explicit steps as certainty does,
 * rather than letting one engine quietly do beat math on junk data.
 */
enum class TransitionTier {
    /** Both grids trusted and the tempi sit within the transparent stretch window. */
    BEATMATCHED,

    /** v2 §1: tempi lock through a harmonic ratio (3:2, 4:3, half/double...),
     * not unison — both decks stretch to a shared BPM. Sits between
     * BEATMATCHED and DJ_ASSISTED: beat math is allowed, unison math is not. */
    HALF_TIME,

    /** Beat-quantized anchors and EQ handoffs are allowed; time-stretching is not. */
    DJ_ASSISTED,

    /** The evidence supports nothing beyond an equal-power fade at the analyzed anchor. */
    PLAIN_CROSSFADE,
}
