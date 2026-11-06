package com.music.bitchord.data.listentogether

/** Keeps the part of a selected playlist that does not fit in the party queue. */
// shortcut: the initiating client holds the tail in memory; persist or transfer it for offline handover.
class PartyPlaylistContinuation {
    private var baseQueueSeq = 0L
    private var initialItems: List<PartyTrack> = emptyList()
    private var pending: List<PartyTrack> = emptyList()
    private var knownItems: List<PartyTrack> = emptyList()
    private var initialAcknowledged = false
    private var inFlight: InFlight? = null
    var active = false
        private set

    private data class InFlight(
        val items: List<PartyTrack>,
        val queueSeq: Long,
        val queueItems: List<PartyTrack>,
        val checkedSeq: Long,
        val checkedQueueIndex: Int,
        val checkedPlaybackSeq: Long,
    )

    /** A welcome snapshot proves a command at this sequence did not reach the server. */
    fun onReconnect(queueSeq: Long) {
        if (inFlight?.queueSeq == queueSeq) {
            inFlight = null
        }
    }

    /** Returns the bounded queue to publish, retaining history before [index]. */
    fun start(queue: List<PartyTrack>, index: Int, baseQueueSeq: Long): PartyQueue {
        require(index in queue.indices) { "Playlist index must identify a current track" }
        clear()
        active = true
        this.baseQueueSeq = baseQueueSeq
        initialQueueIndex = index
        val end = (index + MAX_UPCOMING + 1).coerceAtMost(queue.size)
        initialItems = queue.take(end)
        pending = queue.drop(end)
        return PartyQueue(index = index, items = initialItems)
    }

    /** Returns one queueAdd batch when the server has room and the prior batch is settled. */
    fun nextBatch(queue: PartyQueue, playback: PartyPlayback): List<PartyTrack> {
        if (!active) return emptyList()
        if (!initialAcknowledged) {
            if (queue.seq < baseQueueSeq || playback.queueSeq != queue.seq) {
                return emptyList()
            }
            val retained = suffixPrefixOverlap(initialItems, queue.items)
            if (queue.seq == baseQueueSeq &&
                (retained != initialItems.size || playback.queueIndex !in initialItems.indices ||
                    playback.track?.videoId != initialItems[playback.queueIndex].videoId)
            ) return emptyList()
            if (retained < initialItems.size - initialQueueIndex) {
                clear()
                return emptyList()
            }
            initialAcknowledged = true
            knownItems = queue.items
        }

        if (queue.seq < playback.queueSeq || playback.queueSeq != queue.seq) return emptyList()
        val retained = suffixPrefixOverlap(knownItems, queue.items)
        if (retained < minimumOverlap(knownItems)) {
            clear()
            return emptyList()
        }

        val outstanding = inFlight
        if (outstanding != null) {
            if (queue.seq == outstanding.queueSeq) return emptyList()
            if (queue.seq > outstanding.queueSeq) {
                val outstandingRetained = suffixPrefixOverlap(outstanding.queueItems, queue.items)
                if (outstandingRetained < minimumOverlap(outstanding.queueItems)) {
                    clear()
                    return emptyList()
                }
                val appended = queue.items.drop(outstandingRetained)
                val accepted = outstanding.items.acceptedPrefixAnywhere(appended)
                if (accepted > 0) {
                    pending = pending.drop(accepted)
                    knownItems = queue.items
                    inFlight = null
                } else if (outstanding.checkedSeq != outstanding.queueSeq &&
                    (playback.queueIndex > outstanding.checkedQueueIndex || playback.seq > outstanding.checkedPlaybackSeq)
                ) {
                    knownItems = queue.items
                    inFlight = null
                } else {
                    // An unrelated append may have raced ahead of this command.
                    knownItems = queue.items
                    if (outstanding.checkedSeq == outstanding.queueSeq) {
                        inFlight = outstanding.copy(checkedSeq = queue.seq, checkedQueueIndex = playback.queueIndex,
                            checkedPlaybackSeq = playback.seq)
                    }
                    return emptyList()
                }
            }
        } else if (queue.items.size > knownItems.size) {
            // Another party member appended tracks. They consume queue capacity but do
            // not consume this device's private playlist tail.
            knownItems = queue.items
        } else if (retained < knownItems.size) {
            // The server may trim old played history while preserving the queue tail.
            knownItems = queue.items
        }

        if (pending.isEmpty()) return emptyList()
        if (playback.queueIndex !in queue.items.indices) return emptyList()
        val upcoming = queue.items.size - playback.queueIndex - 1
        val room = MAX_UPCOMING - upcoming
        if (room <= 0) return emptyList()

        val batch = pending.take(room)
        if (batch.isEmpty()) return emptyList()
        inFlight = InFlight(batch, queue.seq, queue.items, queue.seq, playback.queueIndex, playback.seq)
        return batch
    }

    /** Extend the selected playlist when another source page arrives. */
    fun append(tracks: List<PartyTrack>) {
        if (active) pending = pending + tracks
    }

    fun clear() {
        baseQueueSeq = 0L
        initialItems = emptyList()
        pending = emptyList()
        knownItems = emptyList()
        initialAcknowledged = false
        inFlight = null
        active = false
        initialQueueIndex = -1
    }

    private var initialQueueIndex = -1

    private fun suffixPrefixOverlap(previous: List<PartyTrack>, current: List<PartyTrack>): Int {
        for (size in minOf(previous.size, current.size) downTo 1) {
            if (previous.takeLast(size).sameWireTracks(current.take(size))) return size
        }
        return 0
    }

    private fun minimumOverlap(previous: List<PartyTrack>) = minOf(MAX_UPCOMING + 1, previous.size)

    private fun List<PartyTrack>.sameWireTracks(other: List<PartyTrack>) =
        size == other.size && indices.all { this[it].sameWireTrack(other[it]) }

    private fun PartyTrack.sameWireTrack(other: PartyTrack) =
        videoId == other.videoId && fromAutoplay == other.fromAutoplay

    // shortcut: identical concurrent manual additions are ambiguous; add occurrence IDs when the wire protocol supports them.
    private fun List<PartyTrack>.acceptedPrefixAnywhere(appended: List<PartyTrack>): Int {
        for (count in minOf(size, appended.size) downTo 1) {
            for (start in 0..appended.size - count) {
                if (take(count).sameWireTracks(appended.subList(start, start + count))) return count
            }
        }
        return 0
    }

    private companion object {
        const val MAX_UPCOMING = 25
    }
}
