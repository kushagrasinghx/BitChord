package com.music.bitchord.playback

import kotlin.random.Random

/** Number of CONTEXT tracks to expose ahead of the currently playing track. */
const val WINDOW_AHEAD = 25

/** Refill the materialized CONTEXT window when fewer than this many are ahead. */
const val WINDOW_LOW_WATER = 12

/** Short name for the upcoming-window refill threshold. */
const val LOW_WATER = WINDOW_LOW_WATER

/**
 * A logical queue item.  [cycleId] is deliberately part of the item: the same canonical track in
 * two repeat-all cycles is two different items as far as a player-window materialization is
 * concerned.
 */
data class QueueWindowEntry(
    val canonicalIndex: Int,
    val cycleId: Long,
)

/** A cycle planned for look-ahead but not yet selected for playback. */
data class QueueWindowCycleSnapshot(
    val cycleId: Long,
    val order: List<Int>,
)

/**
 * Serializable-in-spirit state for a [QueueWindow].  It contains indices rather than Songs so the
 * logical queue stays independent of the player and of any media model.
 */
data class QueueWindowSnapshot(
    val canonicalSize: Int,
    val order: List<Int>,
    val cursor: Int,
    val currentCanonicalIndex: Int?,
    val shuffleEnabled: Boolean,
    val shuffleSeed: Long,
    val cycleId: Long,
    val consumedThisCycle: List<Int>,
    val lastCycleFinalCanonicalIndex: Int?,
    val plannedCycles: List<QueueWindowCycleSnapshot> = emptyList(),
)

/**
 * Pure logical state for the bounded CONTEXT part of a playback queue.
 *
 * [order] is a permutation of canonical indices for the active cycle.  The player only needs a
 * small [slice] of it; this class intentionally does not know about Media3, Songs, or queue tiers.
 * The cursor points at the current item in [order].  For shuffle-off playback [order] remains
 * canonical and the cursor is allowed to point into the middle, which makes `startAt(2)` mean
 * `2, 3, ..., N - 1, 0, ...` without rotating the canonical order when continuation grows.
 *
 * A [slice] can look across a repeat-all boundary when requested.  Such entries carry the planned
 * cycle id, but reading them never changes the cursor; [commit] or one of the traversal methods is
 * the explicit state transition.
 *
 * Look-ahead plans are retained so continuation growth only appends fresh indices, including to
 * already planned cycles. Explicit order changes can invalidate plans; the owner should refresh
 * its materialized upcoming window after manual navigation or a shuffle toggle. Calls must be
 * serialized by the owner, just like the player's timeline mutations.
 */
class QueueWindow(
    canonicalSize: Int,
    shuffleSeed: Long = DEFAULT_SHUFFLE_SEED,
) {

    init {
        require(canonicalSize >= 0) { "canonicalSize must be non-negative" }
    }

    private var canonicalCount = canonicalSize
    private var seed = shuffleSeed
    private var logicalOrder: IntArray = IntArray(canonicalSize) { it }
    private var inversePositions: IntArray = IntArray(canonicalSize) { it }
    private var logicalCursor: Int = -1
    private var currentIndex: Int? = null
    private var shuffled: Boolean = false
    private var activeCycleId: Long = 0L
    private val consumed = LinkedHashSet<Int>()
    private var lastCycleFinal: Int? = null
    private val plannedCycles = LinkedHashMap<Long, IntArray>()

    /** Number of canonical tracks currently registered. */
    val canonicalSize: Int
        get() = canonicalCount

    /** Alias that reads naturally in code dealing with the canonical registry. */
    val canonicalN: Int
        get() = canonicalCount

    /** Compact alias for the canonical registry size used in queue-window notes. */
    val n: Int
        get() = canonicalCount

    /** The active logical permutation, copied so callers cannot mutate queue state. */
    val order: List<Int>
        get() = logicalOrder.toList()

    /** Inverse permutation: canonical index -> position in [order], or -1 if absent. */
    val posOf: List<Int>
        get() = inversePositions.toList()

    /** Current logical position, or -1 before [startAt] has been called. */
    val cursor: Int
        get() = logicalCursor

    /** Canonical index currently selected, or null before a context has started. */
    val currentCanonicalIndex: Int?
        get() = currentIndex

    /** Active cycle identity.  It advances only when a repeat-all cycle is committed. */
    val cycleId: Long
        get() = activeCycleId

    /** Root seed used to derive deterministic shuffled cycle permutations. */
    val shuffleSeed: Long
        get() = seed

    val isShuffleEnabled: Boolean
        get() = shuffled

    /** Alias useful at call sites that use the setting name rather than the predicate form. */
    val shuffle: Boolean
        get() = shuffled

    /** Tracks consumed in the active cycle, exposed as a stable read-only copy. */
    val consumedThisCycle: Set<Int>
        get() = consumed.toSet()

    val current: QueueWindowEntry?
        get() = currentIndex?.let { QueueWindowEntry(it, activeCycleId) }

    /**
     * Start (or restart) the logical context at a canonical item.
     *
     * Shuffle OFF keeps canonical [order] and positions the cursor at [canonicalIndex].  Shuffle
     * ON pins that item at position zero and deterministically permutes every other item.
     */
    fun startAt(canonicalIndex: Int, shuffle: Boolean = shuffled): QueueWindow {
        require(canonicalIndex in 0 until canonicalCount) {
            "canonicalIndex $canonicalIndex is outside 0 until $canonicalCount"
        }

        shuffled = shuffle
        activeCycleId = 0L
        currentIndex = canonicalIndex
        consumed.clear()
        lastCycleFinal = null
        plannedCycles.clear()
        logicalOrder = if (shuffle) {
            shuffledOrderAnchoredAt(canonicalIndex, activeCycleId)
        } else {
            IntArray(canonicalCount) { it }
        }
        rebuildInverse()
        logicalCursor = inversePositions[canonicalIndex]
        consumed += canonicalIndex
        return this
    }

    /** Convenience factory for callers that want a fully started window in one expression. */
    companion object {
        const val WINDOW_AHEAD: Int = com.music.bitchord.playback.WINDOW_AHEAD
        const val WINDOW_LOW_WATER: Int = com.music.bitchord.playback.WINDOW_LOW_WATER
        const val LOW_WATER: Int = com.music.bitchord.playback.LOW_WATER

        fun startAt(
            canonicalSize: Int,
            canonicalIndex: Int,
            shuffle: Boolean = false,
            shuffleSeed: Long = DEFAULT_SHUFFLE_SEED,
        ): QueueWindow = QueueWindow(canonicalSize, shuffleSeed).startAt(canonicalIndex, shuffle)

        fun restore(snapshot: QueueWindowSnapshot): QueueWindow {
            require(snapshot.canonicalSize >= 0) { "canonicalSize must be non-negative" }
            require(snapshot.order.size == snapshot.canonicalSize) {
                "snapshot order must contain every canonical index exactly once"
            }
            require(snapshot.cycleId >= 0L) { "cycleId must be non-negative" }
            require(snapshot.cursor in -1 until snapshot.canonicalSize) {
                "snapshot cursor is outside the canonical range"
            }

            val restored = QueueWindow(snapshot.canonicalSize, snapshot.shuffleSeed)
            restored.logicalOrder = snapshot.order.toIntArray()
            restored.validatePermutation(restored.logicalOrder)
            restored.rebuildInverse()
            restored.logicalCursor = snapshot.cursor
            restored.currentIndex = snapshot.currentCanonicalIndex
            restored.shuffled = snapshot.shuffleEnabled
            restored.activeCycleId = snapshot.cycleId
            restored.lastCycleFinal = snapshot.lastCycleFinalCanonicalIndex
            restored.consumed += snapshot.consumedThisCycle

            require((restored.logicalCursor == -1) == (restored.currentIndex == null)) {
                "snapshot cursor and current item disagree"
            }
            if (restored.currentIndex != null) {
                val current = restored.currentIndex!!
                require(restored.logicalCursor in restored.logicalOrder.indices) {
                    "started snapshots need a valid cursor"
                }
                require(restored.logicalOrder[restored.logicalCursor] == current) {
                    "snapshot cursor does not point at currentCanonicalIndex"
                }
            }
            require(restored.consumed.all { it in 0 until snapshot.canonicalSize }) {
                "snapshot consumed state contains an invalid canonical index"
            }
            require(restored.currentIndex == null || restored.currentIndex in restored.consumed) {
                "the current item must be consumed in its active cycle"
            }
            require(restored.lastCycleFinal == null || restored.lastCycleFinal in 0 until snapshot.canonicalSize) {
                "snapshot last-cycle item is outside the canonical range"
            }
            require(snapshot.consumedThisCycle.size == restored.consumed.size) {
                "snapshot consumed state contains duplicate indices"
            }
            require(restored.currentIndex != null || restored.consumed.isEmpty()) {
                "an unstarted snapshot cannot have consumed tracks"
            }
            require(restored.shuffled || restored.logicalOrder.indices.all {
                restored.logicalOrder[it] == it
            }) { "shuffle-off snapshots must use canonical order" }
            if (restored.shuffled) {
                require((0 until restored.logicalCursor).all {
                    restored.logicalOrder[it] in restored.consumed
                }) { "shuffled snapshots cannot leave unconsumed tracks behind the cursor" }
            }
            var expectedCycle = snapshot.cycleId
            snapshot.plannedCycles.forEach { planned ->
                require(expectedCycle < Long.MAX_VALUE && planned.cycleId == expectedCycle + 1L) {
                    "planned cycles must be consecutive and follow the active cycle"
                }
                val permutation = planned.order.toIntArray()
                restored.validatePermutation(permutation)
                require(restored.shuffled || permutation.indices.all { permutation[it] == it }) {
                    "shuffle-off planned cycles must use canonical order"
                }
                restored.plannedCycles[planned.cycleId] = permutation
                expectedCycle = planned.cycleId
            }
            return restored
        }

        fun fromSnapshot(snapshot: QueueWindowSnapshot): QueueWindow = restore(snapshot)
    }

    /** Add canonical indices [canonicalN, newCanonicalSize) without rebuilding old order. */
    fun extendTo(newCanonicalSize: Int): QueueWindow {
        require(newCanonicalSize >= canonicalCount) {
            "cannot shrink a QueueWindow from $canonicalCount to $newCanonicalSize"
        }
        if (newCanonicalSize == canonicalCount) return this

        val firstNew = canonicalCount
        canonicalCount = newCanonicalSize
        logicalOrder += freshOrder(firstNew, activeCycleId)
        plannedCycles.entries.forEach { planned ->
            planned.setValue(planned.value + freshOrder(firstNew, planned.key))
        }
        rebuildInverse()
        return this
    }

    /**
     * Return logical entries beginning at an order position.
     *
     * This is a raw logical slice: unlike [upcoming], it does not remove entries already consumed
     * after a manual jump.  Set [repeatAll] to include planned entries from later cycles.  The
     * active cursor and cycle state are never changed.
     */
    fun slice(
        fromPos: Int,
        count: Int,
        repeatAll: Boolean = false,
    ): List<QueueWindowEntry> {
        require(fromPos in 0..logicalOrder.size) {
            "fromPos $fromPos is outside 0..${logicalOrder.size}"
        }
        if (count <= 0 || logicalOrder.isEmpty()) return emptyList()

        val result = ArrayList<QueueWindowEntry>(count)
        var cycle = activeCycleId
        var orderForCycle = logicalOrder
        var position = fromPos

        while (result.size < count && orderForCycle.isNotEmpty()) {
            if (position == orderForCycle.size) {
                if (!repeatAll) break
                cycle = followingCycleId(cycle)
                orderForCycle = plannedOrder(cycle)
                position = 0
                continue
            }
            result += QueueWindowEntry(orderForCycle[position], cycle)
            position += 1
        }
        return result
    }

    /**
     * Upcoming logical entries, excluding the current item and bounded to [WINDOW_AHEAD].
     *
     * In shuffle mode consumed entries are skipped even if a manual jump placed the cursor before
     * them.  This is what keeps an explicit Previous/Next from silently re-inserting old shuffled
     * tracks into automatic playback.  In canonical mode the order is intentionally unfiltered.
     */
    fun upcoming(
        count: Int = WINDOW_AHEAD,
        repeatAll: Boolean = false,
    ): List<QueueWindowEntry> {
        val boundedCount = count.coerceAtMost(WINDOW_AHEAD)
        if (boundedCount <= 0 || currentIndex == null || logicalOrder.isEmpty()) return emptyList()

        val result = ArrayList<QueueWindowEntry>(boundedCount)
        var cycle = activeCycleId
        var orderForCycle = logicalOrder
        var position = logicalCursor + 1

        while (result.size < boundedCount && orderForCycle.isNotEmpty()) {
            while (position < orderForCycle.size && result.size < boundedCount) {
                val canonical = orderForCycle[position]
                if (cycle != activeCycleId || !shuffled || canonical !in consumed) {
                    result += QueueWindowEntry(canonical, cycle)
                }
                position += 1
            }

            if (result.size >= boundedCount || !repeatAll) break

            cycle = followingCycleId(cycle)
            orderForCycle = plannedOrder(cycle)
            position = 0
        }
        return result
    }

    /** Alias that makes the current-track exclusion explicit at call sites. */
    fun upcomingContext(
        count: Int = WINDOW_AHEAD,
        repeatAll: Boolean = false,
    ): List<QueueWindowEntry> = upcoming(count, repeatAll)

    /** Number of entries currently available to [upcoming] without changing state. */
    fun upcomingCount(repeatAll: Boolean = false): Int = upcoming(WINDOW_AHEAD, repeatAll).size

    /**
     * Automatic forward traversal.  Shuffle mode scans for the next unconsumed item; explicit
     * navigation may have moved the cursor backwards, so this is deliberately not just cursor + 1.
     * At a repeat-all boundary a fresh cycle is committed and returned immediately.
     */
    fun advanceAutomatically(repeatAll: Boolean = false): QueueWindowEntry? {
        currentIndex ?: return null
        if (logicalOrder.isEmpty()) return null

        if (shuffled) {
            for (position in (logicalCursor + 1) until logicalOrder.size) {
                val candidate = logicalOrder[position]
                if (candidate !in consumed) {
                    return moveWithinCycle(position)
                }
            }
        } else if (logicalCursor + 1 < logicalOrder.size) {
            return moveWithinCycle(logicalCursor + 1)
        }

        if (!repeatAll) return null
        return transitionToNextCycle()
    }

    /** Short alias for automatic player-driven advancement. */
    fun advance(repeatAll: Boolean = false): QueueWindowEntry? = advanceAutomatically(repeatAll)

    /** Alias used by integrations that name the operation after the player event. */
    fun nextAutomatic(repeatAll: Boolean = false): QueueWindowEntry? = advanceAutomatically(repeatAll)

    /**
     * Explicit Next.  It follows the stable logical order even when the target was consumed; an
     * explicit request is allowed to replay.  No consumed state is cleared or rebuilt.
     */
    fun next(repeatAll: Boolean = false): QueueWindowEntry? {
        if (currentIndex == null || logicalOrder.isEmpty()) return null
        val nextPosition = logicalCursor + 1
        if (nextPosition < logicalOrder.size) return moveWithinCycle(nextPosition)
        if (!repeatAll) return null
        return transitionToNextCycle()
    }

    /**
     * Explicit Previous.  History before the current cursor remains deterministic.  A bounded
     * window does not synthesize a previous cycle, so Previous at the first logical item returns
     * null rather than mutating cycle state behind the caller's back.
     */
    fun previous(): QueueWindowEntry? {
        if (currentIndex == null || logicalCursor <= 0) return null
        return moveWithinCycle(logicalCursor - 1)
    }

    /** Explicitly select a canonical track without rebuilding the active permutation. */
    fun jumpTo(canonicalIndex: Int): QueueWindowEntry {
        require(canonicalIndex in 0 until canonicalCount) {
            "canonicalIndex $canonicalIndex is outside 0 until $canonicalCount"
        }
        check(currentIndex != null) { "startAt must be called before jumpTo" }
        val position = inversePositions[canonicalIndex]
        check(position >= 0) { "canonical index $canonicalIndex is absent from logical order" }
        return moveWithinCycle(position)
    }

    /** Alias for integrations that call direct selection a seek. */
    fun seekTo(canonicalIndex: Int): QueueWindowEntry = jumpTo(canonicalIndex)

    /**
     * Rebuild the upcoming logical order while pinning the current item.
     *
     * Turning shuffle off restores canonical order and leaves the cursor at the current canonical
     * index.  Turning it on keeps cycle-consumed state, so tracks already consumed in this cycle do
     * not reappear in automatic traversal merely because the setting changed.
     *
     * [shuffleSeed] replaces the root seed when shuffle turns on. Without a new seed, turning
     * shuffle off and on again rebuilds exactly the order it had before.
     */
    fun setShuffle(enabled: Boolean, shuffleSeed: Long = seed): QueueWindow {
        if (enabled == shuffled) return this
        shuffled = enabled
        if (enabled) seed = shuffleSeed
        plannedCycles.clear()
        val current = currentIndex ?: run {
            logicalOrder = IntArray(canonicalCount) { it }
            rebuildInverse()
            return this
        }

        logicalOrder = if (enabled) {
            shuffledOrderAnchoredAt(current, activeCycleId)
        } else {
            IntArray(canonicalCount) { it }
        }
        rebuildInverse()
        logicalCursor = inversePositions[current]
        consumed += current
        return this
    }

    /** Toggle shuffle and return this window for fluent state transitions. */
    fun toggleShuffle(enabled: Boolean = !shuffled): QueueWindow = setShuffle(enabled)

    /**
     * Commit a previously planned entry.  Same-cycle commits are explicit seeks.  A next-cycle
     * entry commits exactly one planned repeat-all transition; reading [slice] or [upcoming] alone
     * never performs this transition.
     */
    fun commit(entry: QueueWindowEntry): Boolean {
        if (currentIndex == null || entry.canonicalIndex !in 0 until canonicalCount) return false

        if (entry.cycleId == activeCycleId) {
            val position = inversePositions[entry.canonicalIndex]
            if (position < 0) return false
            moveWithinCycle(position)
            return true
        }

        if (activeCycleId == Long.MAX_VALUE || entry.cycleId != activeCycleId + 1L) return false
        val nextOrder = plannedOrder(entry.cycleId)
        val position = nextOrder.indexOf(entry.canonicalIndex)
        if (position < 0) return false
        selectPlannedCycle(entry.cycleId, nextOrder, position)
        return true
    }

    /** Snapshot state for a later persistence layer without introducing player dependencies. */
    fun snapshot(): QueueWindowSnapshot = QueueWindowSnapshot(
        canonicalSize = canonicalCount,
        order = logicalOrder.toList(),
        cursor = logicalCursor,
        currentCanonicalIndex = currentIndex,
        shuffleEnabled = shuffled,
        shuffleSeed = seed,
        cycleId = activeCycleId,
        consumedThisCycle = consumed.toList(),
        lastCycleFinalCanonicalIndex = lastCycleFinal,
        plannedCycles = plannedCycles.map { (cycle, permutation) ->
            QueueWindowCycleSnapshot(cycle, permutation.toList())
        },
    )

    private fun moveWithinCycle(position: Int): QueueWindowEntry {
        check(position in logicalOrder.indices) { "logical position $position is outside the active order" }
        val previousBoundaryFinal = boundaryFinal()
        if (shuffled && position > logicalCursor) {
            // Forward explicit selections intentionally skip the crossed entries. Marking those
            // entries consumed keeps the permutation's prefix coherent after Previous/jump.
            for (skipped in (logicalCursor + 1)..position) consumed += logicalOrder[skipped]
        }
        logicalCursor = position
        currentIndex = logicalOrder[position]
        consumed += logicalOrder[position]
        if (boundaryFinal() != previousBoundaryFinal) plannedCycles.clear()
        return QueueWindowEntry(logicalOrder[position], activeCycleId)
    }

    private fun transitionToNextCycle(): QueueWindowEntry {
        val nextCycle = followingCycleId(activeCycleId)
        val nextOrder = plannedOrder(nextCycle)
        selectPlannedCycle(nextCycle, nextOrder, 0)
        return current!!
    }

    private fun selectPlannedCycle(cycle: Long, permutation: IntArray, position: Int) {
        lastCycleFinal = currentIndex
        activeCycleId = cycle
        logicalOrder = permutation
        plannedCycles.remove(cycle)
        rebuildInverse()
        logicalCursor = position
        currentIndex = logicalOrder[position]
        consumed.clear()
        for (consumedPosition in 0..position) consumed += logicalOrder[consumedPosition]
    }

    private fun plannedOrder(cycle: Long): IntArray {
        plannedCycles[cycle]?.let { return it }
        val previousCycle = cycle - 1L
        val previousFinal = if (previousCycle == activeCycleId) {
            boundaryFinal()
        } else {
            plannedOrder(previousCycle).lastOrNull()
        }
        return orderForCycle(cycle, previousFinal).also { plannedCycles[cycle] = it }
    }

    private fun boundaryFinal(): Int? {
        for (position in logicalOrder.lastIndex downTo (logicalCursor + 1)) {
            val canonical = logicalOrder[position]
            if (!shuffled || canonical !in consumed) return canonical
        }
        return currentIndex
    }

    private fun followingCycleId(cycle: Long): Long {
        check(cycle < Long.MAX_VALUE) { "cycleId exhausted" }
        return cycle + 1L
    }

    private fun freshOrder(firstNew: Int, cycle: Long): IntArray {
        val fresh = (firstNew until canonicalCount).toList()
        return if (shuffled && currentIndex != null) {
            fresh.shuffled(Random(seedForExtension(cycle, firstNew))).toIntArray()
        } else {
            fresh.toIntArray()
        }
    }

    private fun shuffledOrderAnchoredAt(anchor: Int, cycle: Long): IntArray {
        val rest = (0 until canonicalCount).filter { it != anchor }
            .shuffled(Random(seedForCycle(cycle)))
        return (listOf(anchor) + rest).toIntArray()
    }

    private fun orderForCycle(cycle: Long, previousFinal: Int?): IntArray {
        if (!shuffled) return IntArray(canonicalCount) { it }

        val result = (0 until canonicalCount).toMutableList()
        result.shuffle(Random(seedForCycle(cycle)))
        if (canonicalCount >= 2 && previousFinal != null && result.firstOrNull() == previousFinal) {
            val swapWith = result.indexOfFirst { it != previousFinal }
            if (swapWith >= 0) {
                val first = result[0]
                result[0] = result[swapWith]
                result[swapWith] = first
            }
        }
        return result.toIntArray()
    }

    private fun seedForCycle(cycle: Long): Long =
        seed xor (cycle * -7046029254386353131L)

    private fun seedForExtension(cycle: Long, firstNew: Int): Long =
        seed xor (cycle * -3335678366873096957L) xor canonicalCount.toLong() xor
            (firstNew.toLong() shl 32)

    private fun rebuildInverse() {
        inversePositions = IntArray(canonicalCount) { -1 }
        validatePermutation(logicalOrder)
        logicalOrder.forEachIndexed { position, canonical -> inversePositions[canonical] = position }
    }

    private fun validatePermutation(permutation: IntArray) {
        require(permutation.size == canonicalCount) {
            "logical order size ${permutation.size} does not match canonical size $canonicalCount"
        }
        val seen = BooleanArray(canonicalCount)
        permutation.forEach { canonical ->
            require(canonical in 0 until canonicalCount) { "invalid canonical index $canonical" }
            require(!seen[canonical]) { "logical order contains duplicate canonical index $canonical" }
            seen[canonical] = true
        }
    }
}

private const val DEFAULT_SHUFFLE_SEED: Long = 0x4D59535155455545L
