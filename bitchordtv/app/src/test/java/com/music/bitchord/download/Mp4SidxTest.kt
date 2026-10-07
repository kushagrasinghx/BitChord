package com.music.bitchord.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.ByteArrayOutputStream

/** A fragmented MP4 saved without a `sidx` cannot seek; [Mp4Sidx] adds one. */
class Mp4SidxTest {

    private fun u32(v: Long) = ByteArray(4) { (v shr (8 * (3 - it))).toByte() }

    private fun box(type: String, vararg parts: ByteArray): ByteArray {
        val body = ByteArrayOutputStream().also { o -> parts.forEach(o::write) }.toByteArray()
        return u32(8L + body.size) + type.toByteArray(Charsets.ISO_8859_1) + body
    }

    private fun fragmentedFile(fragments: Int, samplesEach: Int, sampleTicks: Long): ByteArray {
        val mdhd = box("mdhd", u32(0), u32(0), u32(0), u32(48_000), u32(0), u32(0))
        val tkhd = box("tkhd", u32(0), u32(0), u32(0), u32(1), u32(0), u32(0))
        val trak = box("trak", tkhd, box("mdia", mdhd))
        val trex = box("trex", u32(0), u32(1), u32(1), u32(0), u32(0), u32(0))
        val moov = box("moov", trak, box("mvex", trex))
        val out = ByteArrayOutputStream()
        out.write(box("ftyp", "iso6".toByteArray(), u32(0)))
        out.write(moov)
        repeat(fragments) { n ->
            val tfhd = box("tfhd", u32(0x20000), u32(1))
            val tfdt = box("tfdt", u32(0), u32(n * samplesEach * sampleTicks))
            // sample_duration present (0x100) + sample_size present (0x200)
            val samples = ByteArrayOutputStream().also { o ->
                repeat(samplesEach) { o.write(u32(sampleTicks)); o.write(u32(10)) }
            }.toByteArray()
            val trun = box("trun", u32(0x300), u32(samplesEach.toLong()), samples)
            out.write(box("moof", box("traf", tfhd, tfdt, trun)))
            out.write(box("mdat", ByteArray(samplesEach * 10)))
        }
        return out.toByteArray()
    }

    @Test
    fun `adds a sidx whose references cover every fragment`() {
        val input = fragmentedFile(fragments = 3, samplesEach = 4, sampleTicks = 1536)
        val output = Mp4Sidx.ensure(input)
        assertNotSame(input, output)

        val sidxAt = indexOf(output, "sidx") - 4
        val size = u32At(output, sidxAt).toInt()
        assertEquals(40 + 12 * 3, size)
        assertEquals(1L, u32At(output, sidxAt + 12)) // reference_ID = track
        assertEquals(48_000L, u32At(output, sidxAt + 16))
        assertEquals(3, ((output[sidxAt + 38].toInt() and 0xFF) shl 8) or (output[sidxAt + 39].toInt() and 0xFF))
        // The first referenced byte is the first moof, found via first_offset.
        val firstOffset = u32At(output, sidxAt + 32)
        assertEquals("moof", String(output, (sidxAt + size + firstOffset).toInt() + 4, 4))
        var total = 0L
        for (i in 0 until 3) {
            val ref = sidxAt + 40 + 12 * i
            total += u32At(output, ref)
            assertEquals(4 * 1536L, u32At(output, ref + 4))
        }
        assertEquals(output.size - (sidxAt + size + firstOffset), total)
    }

    @Test
    fun `leaves an already indexed file alone`() {
        val once = Mp4Sidx.ensure(fragmentedFile(2, 4, 1536))
        assertSame(once, Mp4Sidx.ensure(once))
    }

    @Test
    fun `leaves a progressive file alone`() {
        val progressive = box("ftyp", "iso6".toByteArray(), u32(0)) + box("moov", box("trak"))
        assertSame(progressive, Mp4Sidx.ensure(progressive))
    }

    private fun u32At(b: ByteArray, at: Int): Long =
        (0 until 4).fold(0L) { acc, i -> (acc shl 8) or (b[at + i].toLong() and 0xFF) }

    private fun indexOf(b: ByteArray, type: String): Int {
        val t = type.toByteArray(Charsets.ISO_8859_1)
        for (i in 0..b.size - 4) if (t.indices.all { b[i + it] == t[it] }) return i
        error("no $type")
    }
}
