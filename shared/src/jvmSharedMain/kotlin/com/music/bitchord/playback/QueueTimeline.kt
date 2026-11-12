package com.music.bitchord.playback

import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import java.util.UUID

/**
 * Information about where a queue or track was started from in the UI.
 */
data class QueueSource(
    val title: String,
    val type: PlaybackSourceType,
    val id: String? = null,
)

/**
 * Result of constructing a context queue, containing the reconstructed timeline
 * and the 0-based start index representing the user's tapped track.
 */
data class ContextQueueResult(
    val timeline: List<Song>,
    val startIndex: Int,
)

/**
 * Plan for executing a queue jump, indicating whether the operation is a pure seek,
 * the index permutation of original timeline items to keep/reorder, the new target index,
 * and whether the target track should be promoted to [QueueTier.CONTEXT].
 */
data class JumpOrderPlan(
    val isPureSeek: Boolean,
    val newOrderIndices: List<Int>,
    val newTargetIndex: Int,
    val promoteTargetToContext: Boolean = false,
)

/**
 * The three-tier queue's rules, as plain list edits.
 *
 * Shared because the phone and the desktop used to run two queues that only
 * looked alike: the desktop gave "Add to queue" no tier at all, so a track
 * queued by hand landed in the album's section, was invisible to a party, and
 * a jump or a clear treated it like any other row. Android's `QueueCoordinator`
 * applies these to an ExoPlayer; the desktop applies them to its own list. Same
 * input, same queue.
 */
object QueueTimeline {

    /**
     * Converts a [Song] into a queue entry belonging to [tier].
     *
     * Invariant: [Song.queueEntryId] is assigned ONCE when entering the queue and is strictly
     * immutable across all transformations, upgrades, and round-trips.
     */
    fun Song.asQueueEntry(tier: QueueTier): Song = copy(
        queueTier = tier,
        queueEntryId = queueEntryId ?: UUID.randomUUID().toString(),
    )

    private fun upcomingUserQueue(timeline: List<Song>, currentIndex: Int): List<Song> =
        if (currentIndex in timeline.indices) {
            timeline.subList(currentIndex + 1, timeline.size)
                .filter { it.queueTier == QueueTier.USER_QUEUE }
        } else {
            emptyList()
        }

    private fun Song.from(source: QueueSource): Song = copy(
        playbackSource = source.title,
        playbackSourceType = source.type,
        playbackSourceId = source.id,
    )

    /**
     * Constructs an interleaved queue for starting a Context (Album, Playlist, Artist):
     *
     * Invariant: [Preceding Context] + [Selected Track] + [Preserved USER_QUEUE] + [Following Context Tracks].
     * Playback starts at [ContextQueueResult.startIndex] (= precedingContext.size). Preceding tracks
     * remain in history for backward navigation and loop around under repeat-all.
     */
    fun buildContextQueue(
        currentTimeline: List<Song>,
        currentIndex: Int,
        newContextSongs: List<Song>,
        selectedIndex: Int,
        contextSource: QueueSource,
    ): ContextQueueResult {
        if (newContextSongs.isEmpty()) return ContextQueueResult(emptyList(), 0)

        val upcomingUserQueue = upcomingUserQueue(currentTimeline, currentIndex)
        val contextEntries = newContextSongs.map { it.from(contextSource).asQueueEntry(QueueTier.CONTEXT) }

        val safeIndex = selectedIndex.coerceIn(contextEntries.indices)
        val precedingContext = contextEntries.subList(0, safeIndex)
        val selected = contextEntries[safeIndex]
        val followingContext = contextEntries.subList(safeIndex + 1, contextEntries.size)

        return ContextQueueResult(
            timeline = precedingContext + listOf(selected) + upcomingUserQueue + followingContext,
            startIndex = precedingContext.size,
        )
    }

    /**
     * Constructs a queue for playing a one-off song (from Search, Home, Explore):
     *
     * Invariant: [Tapped Track] + [Preserved USER_QUEUE].
     */
    fun buildOneOffQueue(
        currentTimeline: List<Song>,
        currentIndex: Int,
        tappedSong: Song,
        source: QueueSource,
    ): List<Song> =
        listOf(tappedSong.from(source).asQueueEntry(QueueTier.CONTEXT)) +
            upcomingUserQueue(currentTimeline, currentIndex)

    /**
     * Constructs a single-song playback timeline for Listen Together mode:
     *
     * Invariant: [Tapped Track (CONTEXT)] + [Upcoming manual party tracks (USER_QUEUE)].
     * Preceding and following context tracks from the album/playlist are excluded from the shared
     * queue, and the previous track's AutoPlay suggestions are dropped. [upcomingPartySongs] is the
     * party's own queue after its current track, in order.
     */
    fun buildPartyPlaybackQueue(
        tappedSong: Song,
        source: QueueSource,
        upcomingPartySongs: List<Song>,
    ): List<Song> =
        listOf(tappedSong.from(source).asQueueEntry(QueueTier.CONTEXT)) +
            upcomingPartySongs
                .filterNot { it.fromAutoplay }
                .map { it.asQueueEntry(QueueTier.USER_QUEUE) }

    /**
     * Finds the insertion index for user-queued tracks.
     *
     * Invariant:
     * - "Play Next" (isNext = true) inserts at the head of USER_QUEUE (immediately after currentIndex).
     * - "Add to Queue" (isNext = false) inserts at the tail of USER_QUEUE (ahead of CONTEXT and AUTOPLAY).
     */
    fun findUserQueueInsertionIndex(
        timeline: List<Song>,
        currentIndex: Int,
        isNext: Boolean,
    ): Int {
        if (timeline.isEmpty()) return 0
        val start = (currentIndex + 1).coerceIn(0, timeline.size)
        if (isNext) return start
        for (i in start until timeline.size) {
            if (timeline[i].queueTier != QueueTier.USER_QUEUE) return i
        }
        return timeline.size
    }

    /**
     * Computes the jump plan and index permutation for tapping a track at [targetIndex].
     *
     * Invariants:
     * 1. Backward or same track ([targetIndex] <= [currentIndex]): pure seek.
     * 2. [QueueTier.CONTEXT]:
     *    - If no [QueueTier.USER_QUEUE] items were crossed: pure seek preserving timeline and all following items.
     *    - If [QueueTier.USER_QUEUE] items were crossed (Option B): all unplayed [QueueTier.USER_QUEUE] items
     *      are pinned contiguously immediately after target; preceding context items remain in history.
     * 3. [QueueTier.USER_QUEUE]: Bypassed manual queue items are pruned; subsequent user queue and context follow.
     * 4. [QueueTier.AUTOPLAY]: Target is promoted to [QueueTier.CONTEXT] (station pivot); future user queue is
     *    preserved behind it; old context and bypassed autoplay are dropped.
     */
    fun computeJumpOrder(
        tiers: List<QueueTier>,
        currentIndex: Int,
        targetIndex: Int,
    ): JumpOrderPlan? {
        if (currentIndex !in tiers.indices || targetIndex !in tiers.indices) return null
        if (targetIndex <= currentIndex) {
            return JumpOrderPlan(
                isPureSeek = true,
                newOrderIndices = tiers.indices.toList(),
                newTargetIndex = targetIndex,
            )
        }

        val targetTier = tiers[targetIndex]
        return when (targetTier) {
            QueueTier.CONTEXT -> {
                val crossedUserQueue = (currentIndex + 1 until targetIndex).filter { tiers[it] == QueueTier.USER_QUEUE }
                if (crossedUserQueue.isEmpty()) {
                    JumpOrderPlan(
                        isPureSeek = true,
                        newOrderIndices = tiers.indices.toList(),
                        newTargetIndex = targetIndex,
                    )
                } else {
                    val precedingHistory = (0..currentIndex).toList()
                    val bypassedContext = (currentIndex + 1 until targetIndex).filter { tiers[it] == QueueTier.CONTEXT }
                    val target = listOf(targetIndex)
                    val followingUserQueue = (targetIndex + 1 until tiers.size).filter { tiers[it] == QueueTier.USER_QUEUE }
                    val allUpcomingUserQueue = crossedUserQueue + followingUserQueue
                    val followingContext = (targetIndex + 1 until tiers.size).filter { tiers[it] == QueueTier.CONTEXT }
                    val followingAutoplay = (targetIndex + 1 until tiers.size).filter { tiers[it] == QueueTier.AUTOPLAY }

                    val order = precedingHistory + bypassedContext + target + allUpcomingUserQueue + followingContext + followingAutoplay
                    val newTarget = precedingHistory.size + bypassedContext.size
                    JumpOrderPlan(isPureSeek = false, newOrderIndices = order, newTargetIndex = newTarget)
                }
            }
            QueueTier.USER_QUEUE -> {
                val history = (0..currentIndex).toList()
                val target = listOf(targetIndex)
                val subsequentUserQueue = (targetIndex + 1 until tiers.size).filter { tiers[it] == QueueTier.USER_QUEUE }
                val upcomingContext = (currentIndex + 1 until tiers.size).filter { tiers[it] == QueueTier.CONTEXT }
                val upcomingAutoplay = (currentIndex + 1 until tiers.size).filter { tiers[it] == QueueTier.AUTOPLAY }

                val order = history + target + subsequentUserQueue + upcomingContext + upcomingAutoplay
                JumpOrderPlan(isPureSeek = false, newOrderIndices = order, newTargetIndex = history.size)
            }
            QueueTier.AUTOPLAY -> {
                val history = (0..currentIndex).toList()
                val target = listOf(targetIndex)
                val preservedUserQueue = (currentIndex + 1 until tiers.size).filter { tiers[it] == QueueTier.USER_QUEUE }
                val subsequentAutoplay = (targetIndex + 1 until tiers.size).filter { tiers[it] == QueueTier.AUTOPLAY }

                val order = history + target + preservedUserQueue + subsequentAutoplay
                JumpOrderPlan(isPureSeek = false, newOrderIndices = order, newTargetIndex = history.size, promoteTargetToContext = true)
            }
        }
    }

    /**
     * Reconstructs the upcoming queue when a listener taps an item in the queue.
     * Delegates to [computeJumpOrder] to maintain a single authoritative definition of jump ordering.
     */
    fun buildJumpQueue(
        currentTimeline: List<Song>,
        currentIndex: Int,
        targetIndex: Int,
    ): List<Song>? {
        val tiers = currentTimeline.map { it.queueTier }
        val plan = computeJumpOrder(tiers, currentIndex, targetIndex) ?: return null
        if (plan.isPureSeek && targetIndex <= currentIndex) return null

        val targetSong = currentTimeline[targetIndex]
        return plan.newOrderIndices.drop(plan.newTargetIndex).map { idx ->
            val song = currentTimeline[idx]
            if (idx == targetIndex && plan.promoteTargetToContext) {
                val sourceTitle = targetSong.playbackSource?.ifBlank { targetSong.title } ?: targetSong.title
                song.copy(
                    playbackSource = sourceTitle,
                    playbackSourceType = targetSong.playbackSourceType ?: PlaybackSourceType.QUEUE,
                ).asQueueEntry(QueueTier.CONTEXT)
            } else {
                song
            }
        }
    }

    /** Indices of the upcoming USER_QUEUE rows — what "Clear" removes. Context and AutoPlay stay. */
    fun upcomingUserQueueIndices(tiers: List<QueueTier>, currentIndex: Int): List<Int> =
        ((currentIndex + 1).coerceAtLeast(0) until tiers.size).filter { tiers[it] == QueueTier.USER_QUEUE }

    /**
     * Indices of played USER_QUEUE rows to prune once playback has moved into CONTEXT, so repeat-all
     * only cycles the context. Empty while a hand-queued track (or AutoPlay) is the one playing.
     */
    fun playedUserQueueIndices(tiers: List<QueueTier>, currentIndex: Int): List<Int> {
        if (currentIndex !in tiers.indices || currentIndex == 0) return emptyList()
        if (tiers[currentIndex] != QueueTier.CONTEXT) return emptyList()
        return (0 until currentIndex).filter { tiers[it] == QueueTier.USER_QUEUE }
    }

    /**
     * The order a queue goes in when it is started while shuffle is on: the picked track leads, the
     * listener's own queue stays pinned in order behind it, and the context and AutoPlay sections are
     * each shuffled among themselves.
     */
    fun shuffledStartingOrder(songs: List<Song>, startIndex: Int): List<Song> {
        if (songs.isEmpty()) return songs
        val at = startIndex.coerceIn(songs.indices)
        val rest = songs.filterIndexed { i, _ -> i != at }
        return listOf(songs[at]) +
            rest.filter { it.queueTier == QueueTier.USER_QUEUE } +
            rest.filter { it.queueTier == QueueTier.CONTEXT }.shuffled() +
            rest.filter { it.queueTier == QueueTier.AUTOPLAY }.shuffled()
    }

    /**
     * Shuffle applied to what is still to come: USER_QUEUE is never shuffled, CONTEXT and AUTOPLAY
     * are shuffled within their own sections. Returned as a permutation of indices into [upcoming].
     */
    fun shuffledUpcomingOrder(upcoming: List<QueueTier>): List<Int> {
        val user = upcoming.indices.filter { upcoming[it] == QueueTier.USER_QUEUE }
        val context = upcoming.indices.filter { upcoming[it] == QueueTier.CONTEXT }
        val autoplay = upcoming.indices.filter { upcoming[it] == QueueTier.AUTOPLAY }
        return user + avoidIdentityShuffle(context, context.shuffled()) +
            avoidIdentityShuffle(autoplay, autoplay.shuffled())
    }

    /**
     * A permutation that puts restored upcoming rows back in their sections — user queue, then
     * context, then AutoPlay — keeping the order [restored] gave them within each.
     */
    fun sectionedOrder(restored: List<Int>, upcoming: List<QueueTier>): List<Int> =
        restored.filter { upcoming[it] == QueueTier.USER_QUEUE } +
            restored.filter { upcoming[it] == QueueTier.CONTEXT } +
            restored.filter { upcoming[it] == QueueTier.AUTOPLAY }

    /**
     * Never returns a section's unchanged order when at least two tracks can move: an identity
     * shuffle is common in short queues and reads exactly like the tap was ignored.
     */
    fun avoidIdentityShuffle(original: List<Int>, shuffled: List<Int>): List<Int> {
        if (original.size <= 1 || shuffled != original) return shuffled
        return shuffled.drop(1) + shuffled.first()
    }
}
