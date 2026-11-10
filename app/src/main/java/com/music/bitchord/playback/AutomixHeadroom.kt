package com.music.bitchord.playback

/** Plain gain shared by the two decks before their audio reaches Android's mixer. */
internal object AutomixHeadroom {
    fun trimFor(incomingGain: Float, outgoingGain: Float): Float {
        val sum = incomingGain + outgoingGain
        // Android sums the two tracks after our per-deck limiters. Reserve
        // their full combined peak, using a plain gain rather than reshaping
        // each deck's peaks with a lower limiter ceiling.
        return if (sum <= 1f) 1f else 1f / sum
    }
}
