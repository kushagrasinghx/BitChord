package com.music.bitchord.ui.performance

import kotlin.math.ceil

/** Raw platform metrics. A lost callback report is not a dropped display frame. */
internal data class FrameSample(
    val window: String,
    val intendedNs: Long,
    val totalNs: Long,
    val deadlineNs: Long,
    val uiNs: Long,
    val gpuNs: Long,
    val unknownNs: Long,
    val layoutNs: Long,
    val drawNs: Long,
    val syncNs: Long,
    val commandNs: Long,
    val swapNs: Long,
    val vsyncDelayNs: Long,
    val firstDraw: Boolean,
    val reportsLost: Int,
)

internal data class FrameSummary(
    val frames: Int,
    val reportedFrames: Int,
    val duplicateReports: Int,
    val conflictingDeadlineFrames: Int,
    val invalidDeadlineFrames: Int,
    val firstDraws: Int,
    val knownDeadlines: Int,
    val lateFrames: Int,
    val lostReports: Long,
    val deadlineP50Ns: Long?,
    val totalP50Ns: Long?,
    val totalP95Ns: Long?,
    val totalP99Ns: Long?,
    val uiP95Ns: Long?,
    val gpuP95Ns: Long?,
    val vsyncIntervalP50Ns: Long?,
) {
    val completeDeadlines: Boolean get() = frames > firstDraws && knownDeadlines == frames - firstDraws
}

/** Bounded and worker-confined; collection performs no sorting or disk I/O. */
internal class FrameMeasurements(
    val startNs: Long,
    val endNs: Long,
    private val capacity: Int = 30_000,
) {
    val frames = ArrayList<FrameSample>()
    var overflow = 0
        private set

    fun add(sample: FrameSample) {
        if (sample.intendedNs !in startNs until endNs) return
        if (frames.size >= capacity) overflow++ else frames.add(sample)
    }

    fun summary(window: String? = null): FrameSummary {
        val reported = frames.filter { window == null || it.window == window }
        // The physical Android 16 recording contained repeat reports with all
        // timings equal except for a changing deadline. Keep raw data intact;
        // count an identical timing sample once and reject conflicting budgets.
        val groups = reported.groupBy { it.copy(deadlineNs = 0, reportsLost = 0) }.values
        var conflicts = 0
        var invalid = 0
        val all = groups.map { group ->
            val budgets = group.map { it.deadlineNs }.distinct()
            val conflict = budgets.size > 1
            // A >1s deadline is suspect for this interactive capture. It is not
            // clamped to a guessed 60/120Hz budget or used in a late percentage.
            val bad = budgets.any { it > MAX_PLAUSIBLE_DEADLINE_NS }
            if (conflict) conflicts++
            if (bad) invalid++
            group.first().copy(
                deadlineNs = if (conflict || bad) -1 else budgets.single(),
                reportsLost = group.sumOf { it.reportsLost },
            )
        }
        val steady = all.filterNot { it.firstDraw }
        val deadlines = steady.filter { it.deadlineNs > 0 && it.totalNs >= 0 }
        // Only adjacent frames in the same window; idle gaps are not refresh periods.
        val intervals = all.groupBy { it.window }.values.flatMap { samples ->
            samples.zipWithNext { a, b -> b.intendedNs - a.intendedNs }
                .filter { it in 1..100_000_000L }
        }
        return FrameSummary(
            frames = all.size,
            reportedFrames = reported.size,
            duplicateReports = reported.size - all.size,
            conflictingDeadlineFrames = conflicts,
            invalidDeadlineFrames = invalid,
            firstDraws = all.count { it.firstDraw },
            knownDeadlines = deadlines.size,
            lateFrames = deadlines.count { it.totalNs > it.deadlineNs },
            lostReports = all.sumOf { it.reportsLost.toLong() },
            deadlineP50Ns = if (deadlines.size == steady.size && steady.isNotEmpty())
                percentile(deadlines.map { it.deadlineNs }, 0.50) else null,
            totalP50Ns = percentile(steady.map { it.totalNs }, 0.50),
            totalP95Ns = percentile(steady.map { it.totalNs }, 0.95),
            totalP99Ns = percentile(steady.map { it.totalNs }, 0.99),
            uiP95Ns = percentile(steady.map { it.uiNs }, 0.95),
            gpuP95Ns = percentile(steady.map { it.gpuNs }, 0.95),
            vsyncIntervalP50Ns = percentile(intervals, 0.50),
        )
    }
}

private const val MAX_PLAUSIBLE_DEADLINE_NS = 1_000_000_000L

private fun percentile(values: List<Long>, fraction: Double): Long? {
    val sorted = values.filter { it >= 0 }.sorted()
    return sorted.getOrNull((ceil(sorted.size * fraction).toInt() - 1).coerceAtLeast(0))
}
