package com.music.bitchord.desktop

import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.QueueTimeline
import com.music.bitchord.playback.QueueTimeline.asQueueEntry

/**
 * The live queue: what is playing, what played before it, and what is next.
 *
 * The same three sections as the phone's — the listener's own queue, then the album or playlist it
 * was started from, then AutoPlay — edited by the same rules ([QueueTimeline]), so a tap on either
 * device leaves the two queues looking identical. That matters most in a party, where they are
 * meant to be one queue.
 */
internal data class DesktopQueue(
    val songs: List<Song> = emptyList(),
    val index: Int = 0,
) {
    val current: Song? get() = songs.getOrNull(index)

    val hasNext: Boolean get() = index < songs.lastIndex

    val hasPrevious: Boolean get() = index > 0

    /** What is still to come, which is what "Up Next" means. */
    val upcoming: List<Song> get() = if (index >= songs.lastIndex) emptyList() else songs.drop(index + 1)

    /**
     * Moves to [target]. Backwards is a plain seek through history; forwards is the phone's queue
     * jump, which keeps everything the listener queued by hand and drops only what was skipped.
     */
    fun jumpTo(target: Int): DesktopQueue {
        if (target !in songs.indices) return this
        if (target <= index) return copy(index = target).afterTransition()
        val rebuilt = QueueTimeline.buildJumpQueue(songs, index, target)
            ?: return copy(index = target).afterTransition()
        return copy(songs = songs.take(index + 1) + rebuilt, index = index + 1).afterTransition()
    }

    fun next(): DesktopQueue = if (hasNext) copy(index = index + 1).afterTransition() else this

    fun previous(): DesktopQueue = if (hasPrevious) copy(index = index - 1) else this

    /**
     * The track that follows [currentId] — what Next would play, and so what a crossfade has to
     * blend into. Null when nothing does, or when [currentId] is not in the queue at all.
     */
    fun followingFor(currentId: String, repeatAll: Boolean): Song? {
        val at = if (current?.videoId == currentId) index else songs.indexOfFirst { it.videoId == currentId }
        if (at < 0) return null
        return songs.getOrNull(at + 1) ?: if (repeatAll) songs.firstOrNull() else null
    }

    /**
     * The queue once a crossfade has handed playback to [songId]: one step on, as Next would have
     * taken it, wrapping only where repeat-all's crossfade did.
     */
    fun afterHandoffTo(songId: String): DesktopQueue = when {
        songs.getOrNull(index + 1)?.videoId == songId -> next()
        // Already there: Next or a queue pick got in ahead of the handoff's callback.
        current?.videoId == songId -> this
        !hasNext && songs.firstOrNull()?.videoId == songId -> copy(index = 0).afterTransition()
        else -> songs.indices
            .firstOrNull { it > index && songs[it].videoId == songId }
            ?.let { copy(index = it).afterTransition() }
            ?: this
    }

    /** Slots one track into the running order at [position]. */
    fun insert(position: Int, song: Song): DesktopQueue = copy(
        songs = songs.toMutableList().apply { add(position.coerceIn(0, size), song) },
    )

    /**
     * Queues [song] by hand, as the phone does: "Play next" heads the listener's own section,
     * "Add to queue" ends it — above the album and above AutoPlay either way.
     */
    fun enqueue(song: Song, playNext: Boolean): DesktopQueue = insert(
        QueueTimeline.findUserQueueInsertionIndex(songs, index, isNext = playNext),
        song.asQueueEntry(QueueTier.USER_QUEUE),
    )

    /** Adds tracks at the end — where AutoPlay's own additions go. */
    fun append(more: List<Song>): DesktopQueue =
        if (more.isEmpty()) this else copy(songs = songs + more)

    /** Removes one row, never the one playing. */
    fun removeAt(at: Int): DesktopQueue {
        if (at !in songs.indices || at == index) return this
        return copy(
            songs = songs.filterIndexed { i, _ -> i != at },
            index = if (at < index) index - 1 else index,
        )
    }

    /** Moves one row, keeping [index] on the song that is playing. */
    fun move(from: Int, to: Int): DesktopQueue {
        if (from !in songs.indices || to !in songs.indices || from == to) return this
        val moved = songs.toMutableList().apply { add(to, removeAt(from)) }
        val newIndex = when (index) {
            from -> to
            in (from + 1)..to -> index - 1
            in to until from -> index + 1
            else -> index
        }
        return copy(songs = moved, index = newIndex)
    }

    /**
     * "Clear": what the listener queued by hand, and only that. The album it was started from and
     * AutoPlay stay, exactly as on the phone.
     */
    fun withoutUserQueue(): DesktopQueue {
        val drop = QueueTimeline.upcomingUserQueueIndices(songs.map(Song::queueTier), index).toHashSet()
        if (drop.isEmpty()) return this
        return copy(songs = songs.filterIndexed { i, _ -> i !in drop })
    }

    /**
     * Everything AutoPlay put after the current track, removed.
     *
     * Switching AutoPlay off is the listener saying they do not want those tracks — Android's
     * `dropAutoplayTracksFromQueue`. What they queued by hand stays where it is.
     */
    fun withoutAutoplay(): DesktopQueue {
        val from = index + 1
        if (from >= songs.size) return this
        val kept = songs.take(from) + songs.drop(from).filterNot { it.fromAutoplay }
        return if (kept.size == songs.size) this else copy(songs = kept)
    }

    /**
     * The party's running order after the playing track, put in place of this one's.
     *
     * History and the playing track are this computer's and stay. Rows the queue already holds keep
     * their own metadata; only their section is taken from the party, which is what makes the
     * groups the same on every device. Returns this same object when nothing differs, so a
     * reconcile tick that changes nothing redraws nothing.
     */
    fun withPartyUpcoming(desired: List<Song>): DesktopQueue {
        if (index !in songs.indices) return this
        val local = upcoming
        if (local.size == desired.size &&
            local.indices.all { local[it].videoId == desired[it].videoId && local[it].queueTier == desired[it].queueTier }
        ) {
            return this
        }
        return copy(songs = songs.take(index + 1) + adopt(local, desired))
    }

    /**
     * Drops played hand-queued rows once playback is back in the album — the phone's
     * `consumePlayedUserQueue`, so repeat-all only cycles the album — and trims old history.
     */
    fun afterTransition(): DesktopQueue {
        val played = QueueTimeline.playedUserQueueIndices(songs.map(Song::queueTier), index)
        val pruned = if (played.isEmpty()) this else {
            val drop = played.toHashSet()
            copy(songs = songs.filterIndexed { i, _ -> i !in drop }, index = index - played.size)
        }
        return pruned.trimmed()
    }

    /**
     * Drops completed entries older than the history window, keeping [index] pointing at the same
     * song.
     */
    fun trimmed(): DesktopQueue {
        val trim = historyTrimCount(index)
        if (trim <= 0) return this
        return copy(songs = songs.drop(trim), index = index - trim)
    }

    /**
     * Shuffle as an edit to the queue rather than a playback mode. The listener's own queue is
     * never shuffled; the album and AutoPlay are each shuffled within their own section.
     */
    fun shuffledAhead(): DesktopQueue {
        val from = index + 1
        if (from >= songs.size) return this
        val upcoming = songs.drop(from)
        val order = QueueTimeline.shuffledUpcomingOrder(upcoming.map(Song::queueTier))
        return copy(songs = songs.take(from) + order.map(upcoming::get))
    }

    /**
     * Puts the upcoming tracks back in [originalOrder], by queue key.
     *
     * Worked out against a map of the positions each key holds rather than by searching the queue
     * once per entry: these queues are playlists, and a linear search per track is a million
     * comparisons over a thousand tracks — on the frame that handles the click.
     */
    fun inOrderOf(originalOrder: List<String>): DesktopQueue {
        val from = index + 1
        if (from >= songs.size) return this
        val upcoming = songs.drop(from)
        val restored = restoreOrder(upcoming.map(::orderKey), originalOrder)
        // Sections are preserved on the way back too.
        val order = QueueTimeline.sectionedOrder(restored, upcoming.map(Song::queueTier))
        return copy(songs = songs.take(from) + order.map(upcoming::get))
    }

    /** The keys [inOrderOf] puts the queue back by. */
    fun orderKeys(): List<String> = songs.map(::orderKey)

    companion object {
        /** Completed entries retained behind the current song. */
        const val MAX_HISTORY = 25

        /** A row's identity for shuffle's undo: its queue entry when it has one, as on the phone. */
        fun orderKey(song: Song): String = song.queueEntryId ?: song.videoId

        /** Keeps the queue at the row the listener picked. */
        fun startingAt(songs: List<Song>, startIndex: Int): DesktopQueue {
            if (songs.isEmpty()) return DesktopQueue()
            return DesktopQueue(songs, index = startIndex.coerceIn(songs.indices))
        }

        /**
         * The order a queue goes in when it is started while shuffle is on: the track the listener
         * picked leads, their own queue follows in order, and the rest follows at random.
         */
        fun shuffledStartingAt(songs: List<Song>, startIndex: Int): DesktopQueue {
            if (songs.isEmpty()) return DesktopQueue()
            return DesktopQueue(QueueTimeline.shuffledStartingOrder(songs, startIndex))
        }

        /**
         * [wanted], reusing the rows of [local] that are the same tracks — they carry metadata the
         * party's slimmer copy lacks — but in [wanted]'s sections. Each local row is used at most
         * once, so a track queued twice stays two rows with two identities.
         */
        fun adopt(local: List<Song>, wanted: List<Song>): List<Song> {
            val known = HashMap<String, ArrayDeque<Song>>()
            local.forEach { known.getOrPut(it.videoId) { ArrayDeque() }.addLast(it) }
            return wanted.map { w -> known[w.videoId]?.removeFirstOrNull()?.copy(queueTier = w.queueTier) ?: w }
        }

        /** One song played on its own is a queue of one, not an addition to the last one. */
        fun of(song: Song): DesktopQueue = DesktopQueue(listOf(song), index = 0)

        /** Restores a saved queue around the item that was current. */
        fun restored(songs: List<Song>, index: Int): DesktopQueue {
            if (songs.isEmpty()) return DesktopQueue()
            return DesktopQueue(songs, index.coerceIn(songs.indices)).trimmed()
        }

        /**
         * Where each of [upcoming] belongs once [original] is put back, as indices into [upcoming].
         *
         * Each track still queued goes back to where it stood in the old order. Whatever is left
         * over was queued after the shuffle and was never part of that order, so it keeps its place
         * at the end; a track named by [original] that has since been removed is skipped. A queue
         * holding the same track twice hands its copies out in the order they stand in, which is
         * what keeps both of them.
         */
        internal fun restoreOrder(upcoming: List<String>, original: List<String>): List<Int> {
            val positions = HashMap<String, ArrayDeque<Int>>(upcoming.size)
            upcoming.forEachIndexed { at, id -> positions.getOrPut(id) { ArrayDeque() }.addLast(at) }
            val placed = BooleanArray(upcoming.size)
            val out = ArrayList<Int>(upcoming.size)
            for (id in original) {
                val at = positions[id]?.removeFirstOrNull() ?: continue
                placed[at] = true
                out += at
            }
            for (at in upcoming.indices) if (!placed[at]) out += at
            return out
        }

        internal fun historyTrimCount(currentIndex: Int): Int =
            (currentIndex - MAX_HISTORY).coerceAtLeast(0)
    }
}
