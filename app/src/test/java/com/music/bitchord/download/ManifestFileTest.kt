package com.music.bitchord.download

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream

/**
 * An exported download of a manifest: the segments joined back into one file
 * instead of the track being handed to YouTube.
 */
class ManifestFileTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun u32(v: Long) = ByteArray(4) { (v shr (8 * (3 - it))).toByte() }
    private fun u16(v: Int) = byteArrayOf((v shr 8).toByte(), v.toByte())

    private fun box(type: String, vararg parts: ByteArray): ByteArray {
        val body = ByteArrayOutputStream().also { o -> parts.forEach(o::write) }.toByteArray()
        return u32(8L + body.size) + type.toByteArray(Charsets.ISO_8859_1) + body
    }

    /** STREAMINFO for 44.1kHz stereo 16-bit with no sample count, as a segmenter leaves it. */
    private val streamInfo = ByteArray(34).also {
        // 44100 = 0x0AC44 in 20 bits, then (channels-1)=1 in 3 bits, (bps-1)=15 in 5 bits.
        it[10] = 0x0A; it[11] = 0xC4.toByte(); it[12] = (0x40 or (1 shl 1)).toByte(); it[13] = (15 shl 4).toByte()
    }

    private fun init(entry: String): ByteArray {
        val dfla = box("dfLa", u32(0), byteArrayOf(0x80.toByte(), 0, 0, 34), streamInfo)
        val sampleEntry = box(entry, ByteArray(28), dfla)
        val stsd = box("stsd", u32(0), u32(1), sampleEntry)
        val mdhd = box("mdhd", u32(0), u32(0), u32(0), u32(44_100), u32(0), u32(0))
        val tkhd = box("tkhd", u32(0), u32(0), u32(0), u32(1), u32(0), u32(0))
        val trak = box("trak", tkhd, box("mdia", mdhd, box("minf", box("stbl", stsd))))
        val trex = box("trex", u32(0), u32(1), u32(1), u32(0), u32(0), u32(0))
        return box("ftyp", "iso8".toByteArray()) + box("moov", trak, box("mvex", trex))
    }

    /** One CMAF segment: styp + sidx (which must not survive), then moof+mdat. */
    private fun segment(frames: List<ByteArray>, frameTicks: Long, decodeTime: Long): ByteArray {
        val tfhd = box("tfhd", u32(0x20000), u32(1))
        val tfdt = box("tfdt", u32(0), u32(decodeTime))
        val entries = frames.flatMap { listOf(u32(frameTicks), u32(it.size.toLong())) }.toTypedArray()
        // data_offset (0x1) + duration (0x100) + size (0x200); offset filled once moof's size is known.
        fun moof(offset: Long) = box("moof", box("traf", tfhd, tfdt, box("trun", u32(0x301), u32(frames.size.toLong()), u32(offset), *entries)))
        val moofSize = moof(0).size
        val mdat = box("mdat", *frames.toTypedArray())
        return box("styp", "msdh".toByteArray()) + box("sidx", ByteArray(32)) + moof(moofSize + 8L) + mdat
    }

    private val frames = List(6) { n -> ByteArray(100 + n) { (n * 7 + it).toByte() } }
    private val segments get() = listOf(
        segment(frames.subList(0, 3), 4096, 0),
        segment(frames.subList(3, 6), 4096, 3 * 4096),
    )

    @Test
    fun `flac manifest becomes a native flac with a sample count`() {
        val out = ByteArrayOutputStream()
        ManifestFile.assemble(tmp.root, "flac", init("fLaC"), segments.asSequence(), out)
        val bytes = out.toByteArray()

        assertEquals("fLaC", String(bytes, 0, 4, Charsets.ISO_8859_1))
        assertEquals(0x80, bytes[4].toInt() and 0xFF) // STREAMINFO, flagged last
        val info = bytes.copyOfRange(8, 42)
        val total = ((info[13].toLong() and 0x0F) shl 32) or
            (0 until 4).fold(0L) { acc, i -> (acc shl 8) or (info[14 + i].toLong() and 0xFF) }
        assertEquals(6 * 4096L, total)
        assertEquals(15 shl 4, info[13].toInt() and 0xF0) // bits per sample untouched
        assertArrayEquals(frames.reduce { a, b -> a + b }, bytes.copyOfRange(42, bytes.size))
        assertTrue(tmp.root.listFiles().isNullOrEmpty()) // temporary frames file cleaned up
    }

    @Test(expected = IllegalStateException::class)
    fun `a non-flac stream is refused rather than written as flac`() {
        ManifestFile.assemble(tmp.root, "flac", init("mp4a"), segments.asSequence(), ByteArrayOutputStream())
    }

    @Test
    fun `m4a manifest is one fragmented mp4 that the tagger can index`() {
        val out = ByteArrayOutputStream()
        val init = init("ec-3")
        ManifestFile.assemble(tmp.root, "m4a", init, segments.asSequence(), out)
        val bytes = out.toByteArray()

        val top = Mp4Boxes.boxes(bytes).map { it.type }
        assertEquals(listOf("ftyp", "moov", "moof", "mdat", "moof", "mdat"), top)
        val indexed = Mp4Sidx.ensure(bytes)
        assertNotSame(bytes, indexed)
        assertEquals(listOf("ftyp", "moov", "sidx", "moof", "mdat", "moof", "mdat"), Mp4Boxes.boxes(indexed).map { it.type })
    }

    @Test
    fun `hls playlist lists its map and segments`() {
        val (init, media) = ManifestFile.hlsSegments(
            "#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:4.0,\nseg-1.m4s\n#EXTINF:4.0,\nseg-2.m4s\n#EXT-X-ENDLIST\n",
        )
        assertEquals("init.mp4", init)
        assertEquals(listOf("seg-1.m4s", "seg-2.m4s"), media)
    }
}
