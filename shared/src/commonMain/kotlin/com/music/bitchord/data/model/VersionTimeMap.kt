package com.music.bitchord.data.model

/**
 * YouTube Music's own word on how a music video and the song it is a video of
 * line up — the `segmentMap` its Song/Video switch reads. Each segment is a
 * stretch the two cuts share: where it starts in the one playing, where it
 * starts in the other, and how long it runs.
 *
 * Nothing is measured to get this. It arrives with the pair itself, so a
 * switch that has one lands on the same moment without fetching or decoding
 * either stream.
 */
class VersionTimeMap(segments: List<Segment>) {

    /** A stretch both cuts share, [durationMs] long in each. */
    data class Segment(val fromStartMs: Long, val toStartMs: Long, val durationMs: Long)

    val segments: List<Segment> = segments.sortedBy { it.fromStartMs }

    init {
        require(this.segments.isNotEmpty()) { "a time map needs at least one segment" }
    }

    /**
     * Where [positionMs] in the playing cut falls in the other one.
     *
     * Inside a shared stretch it is exact. Outside one — a video's spoken
     * intro, a skit between verses — there is no matching moment, so it lands
     * where the music picks up again; past the last stretch, at its end.
     */
    fun map(positionMs: Long): Long {
        for (segment in segments) {
            if (positionMs < segment.fromStartMs) return segment.toStartMs
            val into = positionMs - segment.fromStartMs
            if (into < segment.durationMs) return segment.toStartMs + into
        }
        val last = segments.last()
        return last.toStartMs + last.durationMs
    }

    /** The same pairing read from the other side. */
    fun inverted(): VersionTimeMap =
        VersionTimeMap(segments.map { Segment(it.toStartMs, it.fromStartMs, it.durationMs) })
}

/** The other version of a track, as YouTube Music pairs it, and how the two line up. */
data class VersionPair(val song: Song, val timeMap: VersionTimeMap?)
