package com.music.bitchord.data.listentogether

import android.os.SystemClock

/**
 * This device's offset from the party server's clock.
 *
 * Everything the party shares is expressed on the server's timeline — a
 * position and the server time it was true at — so a device that cannot
 * translate that timeline into its own cannot be in sync, however fast its
 * connection is. This is the translation.
 *
 * The method is NTP's, cut down to the part that matters over one WebSocket.
 * Each sample is a ping stamped on the way out and a pong carrying the server's
 * own reading:
 *
 * ```
 * offset     = serverMs - (t0 + t1) / 2
 * roundTrip  = t1 - t0
 * ```
 *
 * and the error on that offset is at most half the round trip, because the only
 * thing being assumed is that the two legs took the same time. Which is why the
 * sample with the *smallest* round trip wins rather than the newest or the
 * average: on mobile data a single request delayed behind a radio wake-up is
 * wrong by hundreds of milliseconds, and averaging folds that error in instead
 * of discarding it. A short burst of pings on connect therefore converges
 * faster than a long series would.
 *
 * The local half of every sample is [SystemClock.elapsedRealtime], not
 * `System.currentTimeMillis`. The wall clock is stepped — by the network
 * operator, by the user setting the time, by a DST change — and any step lands
 * directly in the offset and moves this device's playhead relative to everyone
 * else's. `elapsedRealtime` is monotonic since boot and counts through deep
 * sleep, which is exactly the timeline a playhead should be measured against.
 */
class ServerClock {

    private data class Sample(val offsetMs: Long, val roundTripMs: Long, val takenAtMs: Long)

    private val samples = ArrayDeque<Sample>()

    @Volatile
    var offsetMs: Long? = null
        private set

    @Volatile
    var roundTripMs: Long = 0
        private set

    /** True once at least one round trip has completed. */
    val synced: Boolean get() = offsetMs != null

    @Synchronized
    fun record(sentAtLocalMs: Long, serverMs: Long, receivedAtLocalMs: Long) {
        val roundTrip = (receivedAtLocalMs - sentAtLocalMs).coerceAtLeast(0)
        val midpoint = sentAtLocalMs + roundTrip / 2
        samples.addLast(Sample(serverMs - midpoint, roundTrip, receivedAtLocalMs))
        while (samples.size > WINDOW) samples.removeFirst()

        // Stale samples are dropped before choosing, or one lucky round trip
        // early in a session would pin the offset for the whole of it — and
        // clocks do drift, on a phone by milliseconds per minute.
        val cutoff = receivedAtLocalMs - SAMPLE_TTL_MS
        val usable = samples.filter { it.takenAtMs >= cutoff }.ifEmpty { samples.toList() }
        val best = usable.minBy { it.roundTripMs }
        offsetMs = best.offsetMs
        roundTripMs = best.roundTripMs
    }

    /** The server's clock, read from here. Null until the first pong lands. */
    fun serverNowMs(): Long? = offsetMs?.let { SystemClock.elapsedRealtime() + it }

    @Synchronized
    fun reset() {
        samples.clear()
        offsetMs = null
        roundTripMs = 0
    }

    companion object {
        fun localNowMs(): Long = SystemClock.elapsedRealtime()

        private const val WINDOW = 12
        private const val SAMPLE_TTL_MS = 120_000L
    }
}
