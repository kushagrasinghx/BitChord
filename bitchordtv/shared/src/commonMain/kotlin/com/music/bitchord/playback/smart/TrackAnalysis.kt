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
     * Per-sample vocal activity, indexed against [energyCurve] sample times.
     * Empty, or any length other than the energy curve's, means "no
     * evidence", which never blocks a transition.
     */
    val vocalActivityMask: List<Double> = emptyList(),
    /** Whole-track vocal likelihood, distinct from the per-sample [vocalActivityMask]. */
    val vocalProbability: Double = 0.0,
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
     */
    val isUsable: Boolean get() = status == STATUS_READY && bpm > 0

    companion object {
        const val STATUS_READY = "ready"
    }
}

/** One point on an energy curve. [energy] is in whatever scale the analyzer chose. */
data class EnergySample(val time: Double, val energy: Double)

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
)

/**
 * The degradation ladder. Ambition falls in explicit steps as certainty does,
 * rather than letting one engine quietly do beat math on junk data.
 */
enum class TransitionTier {
    /** Both grids trusted and the tempi sit within the transparent stretch window. */
    BEATMATCHED,

    /** Beat-quantized anchors and EQ handoffs are allowed; time-stretching is not. */
    DJ_ASSISTED,

    /** The evidence supports nothing beyond an equal-power fade at the analyzed anchor. */
    PLAIN_CROSSFADE,
}
