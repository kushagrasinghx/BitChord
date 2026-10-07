package com.music.bitchord.download

import com.music.bitchord.download.Mp4Boxes.boxes
import com.music.bitchord.download.Mp4Boxes.child
import com.music.bitchord.download.Mp4Boxes.readU32
import com.music.bitchord.download.Mp4Boxes.readU64
import com.music.bitchord.download.Mp4Boxes.writeU32
import com.music.bitchord.download.Mp4Boxes.writeU64

/**
 * Gives a fragmented MP4 the segment index its player needs in order to seek.
 *
 * A fragmented MP4 — `moov` with no samples in it, then a run of `moof`+`mdat`
 * pairs — says nothing about where in time each fragment sits. The only thing
 * that does is a `sidx` box, and a stream that was cut up for adaptive delivery
 * (which is where a Dolby Atmos or ALAC download comes from) usually carries it
 * on the manifest side instead. Saved as a file, the index is simply gone.
 * Media3's `FragmentedMp4Extractor` then reports the file as unseekable: it
 * plays from the start perfectly, and every seek — the scrubber, a tap on a
 * lyric line — is ignored and playback carries on where it was.
 *
 * Rebuilding the index is cheap because the fragments describe themselves. Each
 * `moof` states its sample durations and the first one its decode time, so the
 * `sidx` is a sum over boxes already in the file. It is inserted directly after
 * `moov`; nothing in a fragmented file addresses itself by absolute offset
 * (`trun` data offsets are relative to their own `moof`), so nothing shifts.
 *
 * Anything unfamiliar — not fragmented, already indexed, more than one track,
 * a box that doesn't fit — returns the input array itself, the same "nothing
 * safe to do" signal [Mp4Tagger] uses.
 */
object Mp4Sidx {

    private class Fragment(val offset: Int, var end: Int, val ticks: Long, val decodeTime: Long?)

    fun ensure(bytes: ByteArray): ByteArray = runCatching { index(bytes) }.getOrNull() ?: bytes

    private fun index(bytes: ByteArray): ByteArray? {
        val top = boxes(bytes)
        if (top.any { it.type == "sidx" }) return null
        val moov = top.firstOrNull { it.type == "moov" } ?: return null
        val moofs = top.filter { it.type == "moof" }
        if (moofs.isEmpty() || moofs.first().offset < moov.end) return null

        val moovChildren = boxes(bytes, moov.content, moov.end)
        val mvex = moovChildren.firstOrNull { it.type == "mvex" } ?: return null
        val traks = moovChildren.filter { it.type == "trak" }
        if (traks.size != 1) return null
        val mdhd = child(bytes, child(bytes, traks.first(), "mdia") ?: return null, "mdhd") ?: return null
        val timescale = readU32(bytes, mdhd.content + if (bytes[mdhd.content].toInt() == 1) 20 else 12)
        if (timescale <= 0) return null
        val tkhd = child(bytes, traks.first(), "tkhd") ?: return null
        val trackId = readU32(bytes, tkhd.content + if (bytes[tkhd.content].toInt() == 1) 20 else 12)
        val trex = boxes(bytes, mvex.content, mvex.end)
            .firstOrNull { it.type == "trex" && readU32(bytes, it.content + 4) == trackId }
        val trexDuration = trex?.let { readU32(bytes, it.content + 12) } ?: 0L
        val trexSize = trex?.let { readU32(bytes, it.content + 16) } ?: 0L

        val fragments = moofs.map { moof ->
            val samples = Mp4Boxes.samples(bytes, moof, trackId, trexDuration, trexSize) ?: return null
            Fragment(moof.offset, moof.end, samples.sumOf { it.duration }, decodeTime(bytes, moof))
        }
        // A fragment runs until the next one starts, so whatever sits between a
        // moof and the following one — its mdat, a `free` — is counted into it,
        // and the last one runs to the end of the file.
        fragments.forEachIndexed { i, f ->
            f.end = fragments.getOrNull(i + 1)?.offset ?: bytes.size
        }
        if (fragments.any { it.ticks <= 0 || it.end - it.offset <= 0 }) return null

        val sidxSize = 40 + 12 * fragments.size
        // Measured from the end of the sidx, which sits where moov ended: the
        // first moof moves by the sidx's own length and so does that anchor.
        val firstOffset = (fragments.first().offset - moov.end).toLong()
        val out = ByteArray(sidxSize)
        writeU32(out, 0, sidxSize.toLong())
        "sidx".forEachIndexed { i, c -> out[4 + i] = c.code.toByte() }
        out[8] = 1 // version 1: 64-bit times and offset
        writeU32(out, 12, trackId)
        writeU32(out, 16, timescale)
        writeU64(out, 20, fragments.first().decodeTime ?: 0L)
        writeU64(out, 28, firstOffset)
        out[38] = (fragments.size shr 8).toByte()
        out[39] = fragments.size.toByte()
        fragments.forEachIndexed { i, f ->
            val at = 40 + 12 * i
            writeU32(out, at, (f.end - f.offset).toLong())
            writeU32(out, at + 4, f.ticks)
            // starts_with_SAP, SAP type 1: every audio frame is a sync sample.
            writeU32(out, at + 8, 0x90000000L)
        }

        return bytes.copyOf(moov.end) + out + bytes.copyOfRange(moov.end, bytes.size)
    }

    private fun decodeTime(bytes: ByteArray, moof: Mp4Boxes.Box): Long? {
        val traf = child(bytes, moof, "traf") ?: return null
        val tfdt = child(bytes, traf, "tfdt") ?: return null
        return if (bytes[tfdt.content].toInt() == 1) readU64(bytes, tfdt.content + 4) else readU32(bytes, tfdt.content + 4)
    }
}
