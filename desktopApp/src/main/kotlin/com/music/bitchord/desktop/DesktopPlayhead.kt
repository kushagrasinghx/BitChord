package com.music.bitchord.desktop

/**
 * Where in the playing track the listener is, read off the sink's played-frame count.
 *
 * Output frames and source time part ways three ways: the listener's speed, a beatmatched deck's
 * tempo as it eases back to 1x, and silence cut out by Skip silence. Each of those changes from the
 * frame being written *now*, which is heard only once the queue ahead of it has played out — so it
 * is recorded against that frame, as the start of a new segment, rather than applied at once.
 *
 * The clock this replaces kept a single anchor and multiplied everything since it by the current
 * speed: a speed change rescaled the whole song so far and threw the position (and the lyrics
 * with it) seconds out; an Automix ease was never counted; and the previous track's skipped
 * silence was carried into the next one.
 *
 * Audio thread only.
 */
internal class DesktopPlayhead {

    private class Segment(val frame: Long, val sourceUs: Double, val usPerFrame: Double)

    private val segments = ArrayDeque<Segment>()

    /** [frame] plays [sourceUs], and each output frame from it covers [usPerFrame] of source. */
    fun reset(frame: Long, sourceUs: Long, usPerFrame: Double) {
        segments.clear()
        segments.addLast(Segment(frame, sourceUs.toDouble(), usPerFrame))
    }

    /**
     * From [frame] — the first not yet written — each output frame covers [usPerFrame] of source,
     * after a jump of [skippedUs] that was never written at all.
     */
    fun change(frame: Long, usPerFrame: Double, skippedUs: Double = 0.0) {
        val last = segments.lastOrNull() ?: return reset(frame, skippedUs.toLong(), usPerFrame)
        if (skippedUs <= 0.0 && usPerFrame == last.usPerFrame) return
        val at = maxOf(frame, last.frame)
        val sourceUs = last.sourceUs + (at - last.frame) * last.usPerFrame + skippedUs.coerceAtLeast(0.0)
        if (at == last.frame) segments.removeLast()
        segments.addLast(Segment(at, sourceUs, usPerFrame))
        // Bounded by how many writes fit in the sink's queue; this is only a backstop.
        while (segments.size > MAX_SEGMENTS) segments.removeFirst()
    }

    /** The source position heard at [frame], the sink's played-frame count; forgets what is behind it. */
    fun at(frame: Long): Long {
        while (segments.size > 1 && segments[1].frame <= frame) segments.removeFirst()
        return peek(frame)
    }

    /** The source position [frame] will carry — a frame not yet heard, say — leaving everything in place. */
    fun peek(frame: Long): Long {
        val segment = segments.lastOrNull { it.frame <= frame } ?: segments.firstOrNull() ?: return 0L
        return (segment.sourceUs + (frame - segment.frame).coerceAtLeast(0L) * segment.usPerFrame).toLong()
    }

    private companion object {
        const val MAX_SEGMENTS = 1_024
    }
}
