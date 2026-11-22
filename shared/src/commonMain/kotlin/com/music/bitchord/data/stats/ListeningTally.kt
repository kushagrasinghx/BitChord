package com.music.bitchord.data.stats

import kotlin.math.min

/**
 * What one tick of a player's progress sampler is worth: ListeningRecorder's
 * rule, without the player. The phone feeds it from PlaybackService's sampler,
 * the web player from its own; both get the same minutes and the same plays.
 *
 * ## Why it samples rather than measures
 *
 * Each tick contributes the *wall-clock* time since the last one, never the
 * track's position: seeking cannot inflate it because position is never
 * consulted, and pausing cannot because no tick happens. The gap either side of
 * a pause is capped at [MAX_STEP_MS], which is what stops an hour on the lock
 * screen from arriving as an hour of listening on the first tick after resume.
 * The few seconds before the first tick of each track are not counted; see
 * ListeningRecorder for why that is the right way to be wrong.
 *
 * Not thread-safe: the caller holds whatever lock it needs.
 */
class ListeningTally {

    /** What a tick added: minutes always, a play at most once per track, and whether to write it out now. */
    class Step(val playedMs: Long, val countsAsPlay: Boolean, val flush: Boolean)

    private var currentId: String? = null
    private var lastSampleAt: Long = 0L
    private var playedThisTrack: Long = 0L
    private var playCounted = false
    private var samplesSinceFlush = 0

    /**
     * One tick at [now] while [id] is audibly playing, [lengthMs] long (0 when
     * nothing knows; it then has to clear the thirty-second floor to count as a
     * play). Null when the tick adds nothing: the first tick of a track only
     * anchors the clock.
     */
    fun sample(id: String, now: Long, lengthMs: Long): Step? {
        if (id != currentId) {
            // A new track anchors the clock and contributes nothing yet — see
            // the class note on undercounting.
            currentId = id
            lastSampleAt = now
            playedThisTrack = 0L
            playCounted = false
            return null
        }
        val step = (now - lastSampleAt).coerceIn(0L, MAX_STEP_MS)
        lastSampleAt = now
        if (step <= 0L) return null
        playedThisTrack += step

        val threshold = if (lengthMs > 0) {
            min(lengthMs / 2, PLAY_CEILING_MS).coerceAtLeast(PLAY_FLOOR_MS)
        } else {
            PLAY_FLOOR_MS
        }
        val counts = !playCounted && playedThisTrack >= threshold
        if (counts) playCounted = true

        val flush = ++samplesSinceFlush >= FLUSH_EVERY
        if (flush) samplesSinceFlush = 0
        return Step(step, counts, flush)
    }

    /**
     * Playback stopped, paused, or moved on.
     *
     * Forgetting the current track is what makes the *next* tick anchor rather
     * than contribute: without it, a player paused for an afternoon would hand
     * [MAX_STEP_MS] of listening to whatever was on screen when it resumed.
     */
    fun stop() {
        currentId = null
        playedThisTrack = 0L
        playCounted = false
        samplesSinceFlush = 0
    }

    companion object {
        /**
         * The most a single tick may contribute.
         *
         * A shade over the sampler's interval, so an ordinary tick that arrived late
         * — a busy main thread, a device coming out of doze — is still counted in
         * full, while the unbounded gap across a pause is not.
         */
        const val MAX_STEP_MS = 8_000L

        /** Under half a minute is not a listen, however it ended. */
        const val PLAY_FLOOR_MS = 30_000L

        /** Past four minutes, half a track is more listening than anyone disputes. */
        const val PLAY_CEILING_MS = 4 * 60 * 1000L

        /** Six ticks — about half a minute of listening between disk writes. */
        const val FLUSH_EVERY = 6
    }
}
