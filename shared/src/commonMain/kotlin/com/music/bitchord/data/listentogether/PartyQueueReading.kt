package com.music.bitchord.data.listentogether

/**
 * Where [videoId] stands in the party's running order, read the same way on every device.
 *
 * A party queue keeps its history, so it can hold the playing track twice — once already played,
 * once now. Taking the first copy from the top, which both apps used to do, put the needle on the
 * old copy and showed everything played since as still to come. So the server's own index is
 * trusted first when it names this track: [PartyPlayback.queueIndex] is fresh on every state frame,
 * while [PartyQueue.index] only moves when the queue itself is resent. Failing that, the copy
 * nearest the index wins, ahead of it before behind it.
 */
fun partyQueueIndexOf(queue: PartyQueue, playback: PartyPlayback, videoId: String?): Int {
    if (videoId == null) return -1
    val items = queue.items
    val hint = if (playback.queueSeq == queue.seq) playback.queueIndex else queue.index
    if (hint in items.indices && items[hint].videoId == videoId) return hint
    var best = -1
    var bestDistance = Int.MAX_VALUE
    for (i in items.indices) {
        if (items[i].videoId != videoId) continue
        val distance = if (i >= hint) i - hint else (hint - i) * 2 + 1
        if (distance < bestDistance) {
            best = i
            bestDistance = distance
        }
    }
    return best
}

/** What the party plays after [videoId], or nothing when the queue does not hold it. */
fun partyUpcomingAfter(queue: PartyQueue, playback: PartyPlayback, videoId: String?): List<PartyTrack> {
    val at = partyQueueIndexOf(queue, playback, videoId)
    return if (at < 0) emptyList() else queue.items.subList(at + 1, queue.items.size)
}
