package com.music.bitchord.playback

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shuffle as an edit to the queue, not a playback mode.
 *
 * ExoPlayer's own `shuffleModeEnabled` leaves the queue exactly as it is and
 * draws the next track from a hidden random order, so the queue panel shows
 * one running order while the player follows another — the user sees the album
 * listed in order and hears it jumping about. Toggling shuffle here rearranges
 * the queue itself and leaves playback strictly sequential: what the queue
 * shows is what plays, in that order.
 *
 * The order the queue was in beforehand is kept so the toggle can be undone.
 * The player's own shuffle mode is deliberately never enabled — it would
 * randomise on top of the order set here.
 *
 * User-queued tracks (USER_QUEUE) are NEVER shuffled: they reflect deliberate user
 * intent and remain pinned at the front. Context tracks (CONTEXT) and AutoPlay tracks
 * (AUTOPLAY) are shuffled among themselves.
 *
 * The rearranging itself is worked out as a permutation and applied to the
 * queue in one edit — see [applyOrder], which is where the size of the queue
 * stops mattering.
 */
object QueueShuffle {

    private val _enabled = MutableStateFlow(false)

    /** Whether the queue is currently held in shuffled order. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
    }
    /** Entry IDs in their pre-shuffle order. Empty while shuffle is off. */
    private var original: List<String> = emptyList()

    fun toggle(player: Player) {
        if (_enabled.value) restore(player) else shuffle(player)
        // Persist the new state so it survives app restarts.
        AppSettings.setShuffleEnabled(_enabled.value)
    }

    /**
     * Turns shuffle on without touching the current queue — for the Shuffle
     * button on an album or playlist page, where the queue it applies to is the
     * one about to replace this one. [playSongs] builds that one shuffled.
     */
    fun enableForNextQueue() {
        original = emptyList()
        _enabled.value = true
        AppSettings.setShuffleEnabled(true)
    }

    /**
     * The order a queue should go in when it is started while shuffle is on:
     * the track the user picked leads, the rest follow at random. The order it
     * arrived in is remembered, so turning shuffle off restores it.
     */
    fun startingOrder(songs: List<Song>, startIndex: Int): List<Song> {
        original = songs.map { it.queueEntryId ?: it.videoId }
        val rest = songs.filterIndexed { i, _ -> i != startIndex }
        val userQueue = rest.filter { it.queueTier == QueueTier.USER_QUEUE }
        val context = rest.filter { it.queueTier == QueueTier.CONTEXT }.shuffled()
        val autoplay = rest.filter { it.queueTier == QueueTier.AUTOPLAY }.shuffled()
        return listOf(songs[startIndex]) + userQueue + context + autoplay
    }

    /**
     * Rearranges everything after the playing track. That track keeps playing,
     * and whatever sits above it stays there — those have had their turn.
     *
     * Invariant: USER_QUEUE tracks are never shuffled. CONTEXT and AUTOPLAY are shuffled
     * within their respective sections.
     */
    private fun shuffle(player: Player) {
        val items = player.queueItems()
        original = items.map { it.queueEntryId ?: it.mediaId }
        val from = player.currentMediaItemIndex + 1
        val upcoming = items.drop(from)
        if (upcoming.isEmpty()) {
            _enabled.value = true
            return
        }

        val userQueueIndices = upcoming.indices.filter { upcoming[it].queueTier == QueueTier.USER_QUEUE }
        val contextIndices = upcoming.indices.filter { upcoming[it].queueTier == QueueTier.CONTEXT }
        val autoplayIndices = upcoming.indices.filter { upcoming[it].queueTier == QueueTier.AUTOPLAY }

        applyOrder(
            player,
            from,
            userQueueIndices + shuffledSection(contextIndices) + shuffledSection(autoplayIndices),
        )
        _enabled.value = true
    }

    /**
     * Randomises one queue section but never returns its unchanged order when
     * at least two tracks can move. A mathematically valid identity shuffle is
     * surprisingly common in short queues (one chance in two for two tracks),
     * and reads exactly like the first tap was ignored.
     */
    private fun shuffledSection(indices: List<Int>): List<Int> =
        avoidIdentityShuffle(indices, indices.shuffled())

    internal fun avoidIdentityShuffle(original: List<Int>, shuffled: List<Int>): List<Int> {
        if (original.size <= 1 || shuffled != original) return shuffled
        return shuffled.drop(1) + shuffled.first()
    }

    /** Puts the tracks still to come back into the order they were queued in. */
    private fun restore(player: Player) {
        val items = player.queueItems()
        val from = player.currentMediaItemIndex + 1
        val upcoming = items.drop(from)
        if (upcoming.isEmpty()) {
            original = emptyList()
            _enabled.value = false
            return
        }
        val restored = restoreOrder(upcoming.map { it.queueEntryId ?: it.mediaId }, original)
        applyOrder(player, from, sections(restored, upcoming))
        original = emptyList()
        _enabled.value = false
    }

    /**
     * Where each of [upcoming] belongs once [original] is put back, as indices
     * into [upcoming].
     *
     * Each track still queued goes back to where it stood in the old order.
     * Whatever is left over was queued after the shuffle and was never part of
     * that order, so it keeps its place at the end. A track named by [original]
     * that has since been removed is simply skipped.
     *
     * Written against a map of the positions each id holds rather than by
     * searching [upcoming] once per entry, because the queues this runs on are
     * playlists: a linear search per track is a million comparisons over a
     * thousand-track queue, and it happens on the frame that handles the tap.
     * A queue holding the same track twice hands its copies out in the order
     * they stand in, which is what keeps both of them.
     */
    internal fun restoreOrder(upcoming: List<String>, original: List<String>): List<Int> {
        val positions = HashMap<String, ArrayDeque<Int>>(upcoming.size)
        upcoming.forEachIndexed { index, id ->
            positions.getOrPut(id) { ArrayDeque() }.addLast(index)
        }
        val placed = BooleanArray(upcoming.size)
        val out = ArrayList<Int>(upcoming.size)
        for (id in original) {
            val index = positions[id]?.removeFirstOrNull() ?: continue
            placed[index] = true
            out += index
        }
        for (index in upcoming.indices) if (!placed[index]) out += index
        return out
    }

    /** [order] with sections strictly maintained: USER_QUEUE -> CONTEXT -> AUTOPLAY. */
    private fun sections(order: List<Int>, upcoming: List<MediaItem>): List<Int> =
        order.filter { upcoming[it].queueTier == QueueTier.USER_QUEUE } +
            order.filter { upcoming[it].queueTier == QueueTier.CONTEXT } +
            order.filter { upcoming[it].queueTier == QueueTier.AUTOPLAY }

    /**
     * Rearranges the live queue from [from] onwards, [order] naming where each
     * slot's new occupant is standing now.
     *
     * One edit, not a run of [Player.moveMediaItem], which is what this used to
     * do and what made shuffling a long playlist hang the app. Every move is a
     * separate trip across the session boundary and each one lands as a playlist
     * change: the service reserialises its queue snapshot to disk, the
     * notification's custom layout is rebuilt, and the whole timeline is
     * broadcast back for the UI to convert to songs and recompose from. One move
     * costs that once. A thousand-track shuffle is a thousand moves, so it cost
     * all of it a thousand times over, on the main thread, with nothing drawing
     * in between — which is an ANR, not a shuffle.
     *
     * A permutation rather than the rearranged items themselves because of what
     * a controller can see. Media3 strips a [MediaItem]'s `localConfiguration`
     * on the way out to a controller, so the items read back out of one have no
     * playback URI left on them; handing those to `replaceMediaItems` would send
     * the queue back with every upcoming track's URI missing. Indices survive
     * the trip intact, and the session applies them to the items it holds, which
     * never lost anything.
     */
    private fun applyOrder(player: Player, from: Int, order: List<Int>) {
        if (order.isEmpty()) return
        if (player is MediaController) {
            player.sendCustomCommand(
                SessionCommand(ACTION_REORDER_QUEUE, Bundle.EMPTY),
                bundleOf(
                    EXTRA_REORDER_FROM to from,
                    EXTRA_REORDER_ORDER to order.toIntArray(),
                ),
            )
        } else {
            reorder(player, from, order.toIntArray())
        }
    }

    /** [applyOrder] as it arrives at the session — see [ACTION_REORDER_QUEUE]. */
    fun reorderFromCommand(player: Player, args: Bundle) {
        val from = args.getInt(EXTRA_REORDER_FROM, -1)
        val order = args.getIntArray(EXTRA_REORDER_ORDER) ?: return
        if (from >= 0) reorder(player, from, order)
    }

    /**
     * Refused outright, rather than applied as far as it goes, when the queue no
     * longer has room for it: the order was worked out against the queue as it
     * stood a moment ago, and a queue that has since lost tracks — AutoPlay
     * switched off, say — would have to be rearranged into fewer slots than the
     * permutation names. Dropping the tail of it would drop those tracks from
     * the queue, which is not what shuffling asked for.
     */
    private fun reorder(player: Player, from: Int, order: IntArray) {
        if (order.isEmpty() || from + order.size > player.mediaItemCount) return
        val target = List(order.size) { player.getMediaItemAt(from + order[it]) }
        player.replaceMediaItems(from, from + order.size, target)
    }

    private fun Player.queueItems(): List<MediaItem> =
        List(mediaItemCount) { getMediaItemAt(it) }
}
