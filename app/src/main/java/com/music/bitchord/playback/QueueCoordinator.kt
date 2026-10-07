package com.music.bitchord.playback

import androidx.media3.common.Player
import com.music.bitchord.data.listentogether.PartyTrack
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song

/**
 * The tier of the queue row at [index]. The functions below take this as a
 * parameter so unit tests can supply tiers directly — on the JVM the metadata
 * bundle a MediaItem carries its tier in is a stub that stores nothing.
 */
private fun Player.queueTierAt(index: Int): QueueTier = getMediaItemAt(index).queueTier

/**
 * Headless orchestrator for two-tier Spotify-style queue operations.
 *
 * Owns timeline construction, tier assignment, deterministic queue identity assignment,
 * and user-queue pruning invariants. The list rules themselves live in [QueueTimeline],
 * which the desktop runs too, so both build the same queue from the same tap; this
 * applies them to a [Player].
 */
object QueueCoordinator {

    /** @see QueueTimeline.asQueueEntry */
    fun Song.asQueueEntry(tier: QueueTier): Song = with(QueueTimeline) { asQueueEntry(tier) }

    /** @see QueueTimeline.buildContextQueue */
    fun buildContextQueue(
        currentTimeline: List<Song>,
        currentIndex: Int,
        newContextSongs: List<Song>,
        selectedIndex: Int,
        contextSource: QueueSource,
    ): ContextQueueResult = QueueTimeline.buildContextQueue(
        currentTimeline, currentIndex, newContextSongs, selectedIndex, contextSource,
    )

    /** @see QueueTimeline.buildOneOffQueue */
    fun buildOneOffQueue(
        currentTimeline: List<Song>,
        currentIndex: Int,
        tappedSong: Song,
        source: QueueSource,
    ): List<Song> = QueueTimeline.buildOneOffQueue(currentTimeline, currentIndex, tappedSong, source)

    /** @see QueueTimeline.buildPartyPlaybackQueue */
    fun buildPartyPlaybackQueue(
        tappedSong: Song,
        source: QueueSource,
        upcomingPartyTracks: List<PartyTrack>,
    ): List<Song> = QueueTimeline.buildPartyPlaybackQueue(
        tappedSong,
        source,
        upcomingPartyTracks.map { it.toSong() },
    )

    /** @see QueueTimeline.findUserQueueInsertionIndex */
    fun findUserQueueInsertionIndex(
        timeline: List<Song>,
        currentIndex: Int,
        isNext: Boolean,
    ): Int = QueueTimeline.findUserQueueInsertionIndex(timeline, currentIndex, isNext)

    /**
     * Clears only the upcoming USER_QUEUE items from the player's timeline.
     *
     * Invariant: CONTEXT and AUTOPLAY tracks are completely untouched.
     */
    fun clearUserQueue(player: Player, tierAt: (Int) -> QueueTier = player::queueTierAt) {
        val currentIndex = player.currentMediaItemIndex
        val count = player.mediaItemCount
        if (count == 0) return

        val userQueueIndices = mutableListOf<Int>()
        for (i in (currentIndex + 1) until count) {
            if (tierAt(i) == QueueTier.USER_QUEUE) {
                userQueueIndices.add(i)
            }
        }

        // Remove in reverse order to preserve preceding indices during removal
        for (i in userQueueIndices.asReversed()) {
            player.removeMediaItem(i)
        }
    }

    /**
     * Prunes played USER_QUEUE items once playback has transitioned into CONTEXT.
     *
     * Invariant: Once an item leaves the USER_QUEUE block and enters CONTEXT, all preceding
     * consumed USER_QUEUE entries are safely removed so that Media3's native REPEAT_MODE_ALL
     * will only cycle through CONTEXT items.
     */
    fun consumePlayedUserQueue(player: Player, tierAt: (Int) -> QueueTier = player::queueTierAt) {
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex <= 0) return

        if (player.currentMediaItem == null) return
        if (tierAt(currentIndex) != QueueTier.CONTEXT) return

        val playedIndices = mutableListOf<Int>()
        for (i in 0 until currentIndex) {
            if (tierAt(i) == QueueTier.USER_QUEUE) {
                playedIndices.add(i)
            }
        }

        for (i in playedIndices.asReversed()) {
            player.removeMediaItem(i)
        }
    }

    /** @see QueueTimeline.buildJumpQueue */
    fun buildJumpQueue(
        currentTimeline: List<Song>,
        currentIndex: Int,
        targetIndex: Int,
    ): List<Song>? = QueueTimeline.buildJumpQueue(currentTimeline, currentIndex, targetIndex)

    /**
     * Executes a semantic queue jump on [player], preserving history up to [Player.getCurrentMediaItemIndex]
     * and avoiding unintended reshuffling.
     */
    fun jumpToQueueItem(
        player: Player,
        targetIndex: Int,
        cachedTimeline: List<Song>? = null,
    ) {
        val currentIndex = player.currentMediaItemIndex
        val count = player.mediaItemCount
        if (targetIndex !in 0 until count) return

        if (targetIndex <= currentIndex) {
            // Backward jump or same track: seek in history without modifying playlist
            player.seekTo(targetIndex, 0L)
            player.play()
            return
        }

        val currentTimeline = cachedTimeline?.takeIf { it.size == count }
            ?: (0 until count).map { player.getMediaItemAt(it).toSong() }
        val newUpcoming = buildJumpQueue(currentTimeline, currentIndex, targetIndex) ?: return

        // Retain played history up to and including currentIndex so backward navigation works
        val history = (0..currentIndex).map { player.getMediaItemAt(it) }
        val upcomingMediaItems = newUpcoming.map { it.toMediaItem() }

        val newPlaylist = history + upcomingMediaItems
        val newTargetIndex = history.size // First track of newUpcoming
        player.setMediaItems(newPlaylist, newTargetIndex, 0L)
        if (runCatching { player.playbackState }.getOrNull() == Player.STATE_IDLE) {
            player.prepare()
        }
        player.play()
    }
}
