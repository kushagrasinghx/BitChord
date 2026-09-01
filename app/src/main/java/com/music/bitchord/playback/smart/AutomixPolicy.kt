package com.music.bitchord.playback.smart

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Who decides which queued track follows the one playing. */
enum class AutomixQueueMode { RESPECT_QUEUE, DJ_CONTROL }

/** How much of a recording Automix is allowed to reshape. */
enum class AutomixPreservation { FULL_TRACK, BALANCED, DJ_FREEDOM }

/** Candidate-preview policy while Android reports a metered connection. */
enum class AutomixMeteredPreviews { CACHE_ONLY, THREE, WIFI_EQUIVALENT }

/** Why a media item occupies its position in the queue. */
enum class QueueOrigin { PLAY_NEXT, USER_QUEUE, AUTOPLAY }

/** Arrangement label inferred without adding another model to the APK. */
enum class MusicalSectionType { INTRO, BUILD, DROP, VERSE, CHORUS, BREAK, OUTRO, UNKNOWN }

data class MusicalSection(
    val start: Double,
    val end: Double,
    val type: MusicalSectionType,
    val confidence: Double,
    val energy: Double,
    val vocalActivity: Double,
)

data class PreservationLimits(
    val maximumAudibleTrimSeconds: Double,
    val maximumTrimBeats: Int,
    val minimumPlayedFraction: Double,
)

fun preservationLimits(policy: AutomixPreservation): PreservationLimits = when (policy) {
    AutomixPreservation.FULL_TRACK -> PreservationLimits(2.0, 0, 0.98)
    AutomixPreservation.BALANCED -> PreservationLimits(12.0, 16, 0.75)
    AutomixPreservation.DJ_FREEDOM -> PreservationLimits(30.0, 64, 0.60)
}

/** Low-confidence structure never authorizes an editorial cut. */
fun effectivePreservation(
    requested: AutomixPreservation,
    structuralConfidence: Double,
): AutomixPreservation = if (structuralConfidence >= 0.55) requested else AutomixPreservation.FULL_TRACK

/** Maximum transparent stretch for the selected preservation personality. */
fun maximumStretch(policy: AutomixPreservation): Double =
    if (policy == AutomixPreservation.DJ_FREEDOM) 0.06 else 0.04

/**
 * Applies the audible-tail, beat and minimum-content rails to a proposed exit.
 * Protected sections are never cut through; the next boundary at or after the
 * proposal wins, with content end as the safe fallback.
 */
fun preserveMixOut(
    analysis: TrackAnalysis,
    proposed: Double,
    requested: AutomixPreservation,
): Double {
    val end = analysis.contentEndTime.takeIf { it > 0 } ?: analysis.duration
    if (end <= 0) return proposed.coerceAtLeast(0.0)
    val policy = effectivePreservation(requested, analysis.structuralConfidence)
    val limits = preservationLimits(policy)
    val beat = analysis.beatInterval.takeIf { it > 0 }
        ?: analysis.bpm.takeIf { it > 0 }?.let { 60.0 / it }
    val beatLimit = beat?.let { limits.maximumTrimBeats * it }
        ?.takeIf { limits.maximumTrimBeats > 0 } ?: limits.maximumAudibleTrimSeconds
    val maxTrim = min(limits.maximumAudibleTrimSeconds, beatLimit)
    val earliestByTail = end - maxTrim
    val earliestByFraction = end * limits.minimumPlayedFraction
    var safe = max(proposed, max(earliestByTail, earliestByFraction)).coerceIn(0.0, end)

    val protected = analysis.sections.firstOrNull { section ->
        safe > section.start && safe < section.end &&
            (section.type == MusicalSectionType.CHORUS ||
                section.type == MusicalSectionType.DROP ||
                section.vocalActivity >= VOCAL_ACTIVE_THRESHOLD)
    }
    if (protected != null) safe = protected.end.coerceAtMost(end)

    // Creative policies still cut on a phrase or downbeat, never mid-beat.
    if (policy != AutomixPreservation.FULL_TRACK) {
        val phrase = analysis.phraseBoundaries
            .filter { it >= safe && it <= end }
            .minOrNull()
        val downbeat = analysis.downbeats
            .filter { it >= safe && it <= end }
            .minOrNull()
        safe = phrase ?: downbeat ?: safe
    }
    return safe
}

data class AutomixCandidate(
    val id: String,
    val analysis: TrackAnalysis,
    val queueDistance: Int,
    val origin: QueueOrigin,
    val cached: Boolean,
    val artist: String = "",
    val recentlyPlayed: Boolean = false,
    val manualDeferrals: Int = 0,
)

/** Progressive three-rise / peak / release arc, deliberately deterministic. */
class EnergyArc {
    private var step = 0
    private var lastActivityMs = 0L

    fun reset(nowMs: Long) {
        step = 0
        lastActivityMs = nowMs
    }

    fun target(nowMs: Long): Double {
        if (lastActivityMs == 0L || nowMs - lastActivityMs > 20 * 60_000L) reset(nowMs)
        return listOf(0.44, 0.55, 0.67, 0.80, 0.91, 0.86, 0.62)[step % 7]
    }

    fun advance(nowMs: Long) {
        if (lastActivityMs == 0L || nowMs - lastActivityMs > 20 * 60_000L) step = 0 else step++
        lastActivityMs = nowMs
    }
}

private fun harmonicScore(left: TrackAnalysis, right: TrackAnalysis): Double {
    if (left.keyConfidence < 0.4 || right.keyConfidence < 0.4) return 0.5
    val a = left.key.substringBefore(' ')
    val b = right.key.substringBefore(' ')
    return if (a == b) 1.0 else 0.55
}

private fun rhythmicScore(left: TrackAnalysis, right: TrackAnalysis): Double {
    if (left.bpm <= 0 || right.bpm <= 0) return 0.25
    val aligned = alignTempoOctave(left.bpm, right.bpm)
    val distance = abs(aligned / left.bpm - 1.0)
    val confidence = min(left.beatConfidence, right.beatConfidence).coerceIn(0.0, 1.0)
    return ((1.0 - distance / 0.12).coerceIn(0.0, 1.0) * 0.7 + confidence * 0.3)
}

private fun trackEnergy(analysis: TrackAnalysis): Double {
    // Candidate energy is the section that will actually take over, not the
    // last section of the file (normally a quiet outro).
    val entry = analysis.mixInCandidates
        .maxByOrNull { it.score }
        ?.time
        ?: analysis.mixInTime.takeIf { it > 0 }
        ?: analysis.audibleStartTime
        ?: 0.0
    val section = analysis.sections.firstOrNull { entry in it.start..it.end }?.energy
        ?: analysis.sections.firstOrNull { it.start >= entry }?.energy
    if (section != null) return section.coerceIn(0.0, 1.0)
    val values = analysis.energyCurve.map { it.energy }.filter { it.isFinite() }.sorted()
    if (values.isEmpty()) return 0.5
    val median = values[values.size / 2]
    val high = values[(values.lastIndex * 0.9).toInt()].coerceAtLeast(1e-9)
    return (median / high).coerceIn(0.0, 1.0)
}

/** Product weights are fixed: compatibility 45, energy 25, order 15, variety 10, cache 5. */
fun scoreAutomixCandidate(
    current: TrackAnalysis,
    candidate: AutomixCandidate,
    targetEnergy: Double,
): Double {
    if (candidate.origin == QueueOrigin.PLAY_NEXT) return 10_000.0
    if (candidate.origin == QueueOrigin.USER_QUEUE && candidate.manualDeferrals >= 3) return 9_000.0
    val compatibility = rhythmicScore(current, candidate.analysis) * 0.68 +
        harmonicScore(current, candidate.analysis) * 0.22 +
        min(current.structuralConfidence, candidate.analysis.structuralConfidence).coerceIn(0.0, 1.0) * 0.10
    val energy = 1.0 - abs(trackEnergy(candidate.analysis) - targetEnergy).coerceIn(0.0, 1.0)
    val relevance = (1.0 - candidate.queueDistance.coerceAtMost(8) / 8.0) *
        if (candidate.origin == QueueOrigin.USER_QUEUE) 1.0 else 0.72
    val variety = if (candidate.recentlyPlayed) 0.0 else 1.0
    return compatibility * 0.45 + energy * 0.25 + relevance * 0.15 + variety * 0.10 +
        (if (candidate.cached) 1.0 else 0.0) * 0.05
}

/** Per-session network preview guard; previews themselves never exceed 768 KiB. */
class PreviewBudget {
    var meteredBytes: Long = 0
        private set

    fun candidateLimit(metered: Boolean, setting: AutomixMeteredPreviews): Int = when {
        !metered -> 6
        setting == AutomixMeteredPreviews.CACHE_ONLY -> 0
        setting == AutomixMeteredPreviews.THREE -> 3
        else -> 6
    }

    fun reserve(metered: Boolean, bytes: Long): Boolean {
        val bounded = bytes.coerceAtMost(PREVIEW_BYTES)
        if (metered && meteredBytes + bounded > METERED_SESSION_BYTES) return false
        if (metered) meteredBytes += bounded
        return true
    }

    companion object {
        const val PREVIEW_BYTES = 768L * 1024L
        const val METERED_SESSION_BYTES = 12L * 1024L * 1024L
        const val MAX_CONCURRENT_DOWNLOADS = 2
    }
}
