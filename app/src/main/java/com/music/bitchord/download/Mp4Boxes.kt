package com.music.bitchord.download

/**
 * The box walking [Mp4Sidx] and [ManifestFile] share: read-only, bounds-checked,
 * and quiet about anything malformed — a box that does not fit its parent ends
 * the list rather than throwing, so each caller decides what a short list means.
 */
internal object Mp4Boxes {

    class Box(val offset: Int, val headerLen: Int, val size: Int, val type: String) {
        val content get() = offset + headerLen
        val end get() = offset + size
    }

    fun boxes(bytes: ByteArray, start: Int = 0, end: Int = bytes.size): List<Box> {
        val out = mutableListOf<Box>()
        var pos = start
        while (pos + 8 <= end) {
            var size = readU32(bytes, pos)
            var header = 8
            if (size == 1L) {
                if (pos + 16 > end) break
                size = readU64(bytes, pos + 8)
                header = 16
            } else if (size == 0L) {
                size = (end - pos).toLong()
            }
            if (size < header || pos + size > end) break
            out += Box(pos, header, size.toInt(), String(bytes, pos + 4, 4, Charsets.ISO_8859_1))
            pos += size.toInt()
        }
        return out
    }

    fun child(bytes: ByteArray, parent: Box, type: String): Box? =
        boxes(bytes, parent.content, parent.end).firstOrNull { it.type == type }

    /** Follows [path] down from the top level, one child type per step. */
    fun find(bytes: ByteArray, vararg path: String): Box? {
        var box = boxes(bytes).firstOrNull { it.type == path.first() } ?: return null
        for (type in path.drop(1)) box = child(bytes, box, type) ?: return null
        return box
    }

    /** The 24-bit flags of a FullBox. */
    fun flags(b: ByteArray, box: Box): Int = readU32(b, box.content).toInt() and 0xFFFFFF

    fun readU32(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFF) shl 24) or ((b[off + 1].toLong() and 0xFF) shl 16) or
            ((b[off + 2].toLong() and 0xFF) shl 8) or (b[off + 3].toLong() and 0xFF)

    fun readU64(b: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (b[off + i].toLong() and 0xFF)
        return v
    }

    fun writeU32(b: ByteArray, off: Int, value: Long) {
        for (i in 0 until 4) b[off + i] = ((value shr (8 * (3 - i))) and 0xFF).toByte()
    }

    fun writeU64(b: ByteArray, off: Int, value: Long) {
        for (i in 0 until 8) b[off + i] = ((value shr (8 * (7 - i))) and 0xFF).toByte()
    }

    /**
     * One track's samples within a `moof`: where each sits in the segment and
     * how long it lasts. Only the track [trackId] is read; a `base_data_offset`
     * (an absolute file position, meaningless once the segment stands alone)
     * returns null rather than a guess.
     */
    class Sample(val offset: Int, val size: Int, val duration: Long)

    fun samples(bytes: ByteArray, moof: Box, trackId: Long, trexDuration: Long, trexSize: Long): List<Sample>? {
        val out = mutableListOf<Sample>()
        for (traf in boxes(bytes, moof.content, moof.end).filter { it.type == "traf" }) {
            val parts = boxes(bytes, traf.content, traf.end)
            val tfhd = parts.firstOrNull { it.type == "tfhd" } ?: continue
            if (readU32(bytes, tfhd.content + 4) != trackId) continue
            val tf = flags(bytes, tfhd)
            if (tf and 0x1 != 0) return null
            var p = tfhd.content + 8
            if (tf and 0x2 != 0) p += 4
            val defaultDuration = if (tf and 0x8 != 0) readU32(bytes, p).also { p += 4 } else trexDuration
            val defaultSize = if (tf and 0x10 != 0) readU32(bytes, p) else trexSize

            // Without a data_offset, samples follow on from the end of the
            // previous run — the first run's from the start of the next mdat.
            var cursor = boxes(bytes, moof.end, bytes.size).firstOrNull { it.type == "mdat" }?.content ?: return null
            for (trun in parts.filter { it.type == "trun" }) {
                val f = flags(bytes, trun)
                val count = readU32(bytes, trun.content + 4).toInt()
                var q = trun.content + 8
                if (f and 0x1 != 0) {
                    cursor = moof.offset + readU32(bytes, q).toInt()
                    q += 4
                }
                if (f and 0x4 != 0) q += 4
                repeat(count) {
                    val duration = if (f and 0x100 != 0) readU32(bytes, q).also { q += 4 } else defaultDuration
                    val size = if (f and 0x200 != 0) readU32(bytes, q).also { q += 4 } else defaultSize
                    if (f and 0x400 != 0) q += 4
                    if (f and 0x800 != 0) q += 4
                    if (size <= 0 || cursor + size > bytes.size) return null
                    out += Sample(cursor, size.toInt(), duration)
                    cursor += size.toInt()
                }
            }
        }
        return out
    }
}
