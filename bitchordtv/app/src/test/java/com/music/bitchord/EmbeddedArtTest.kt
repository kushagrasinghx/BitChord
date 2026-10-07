package com.music.bitchord

import com.music.bitchord.data.remote.EmbeddedArt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedArtTest {

    private class MemReader(val bytes: ByteArray) : EmbeddedArt.Reader {
        override val size: Long get() = bytes.size.toLong()
        override fun read(offset: Long, length: Int): ByteArray {
            if (offset < 0 || offset >= bytes.size || length <= 0) return ByteArray(0)
            val end = minOf(offset + length, bytes.size.toLong()).toInt()
            return bytes.copyOfRange(offset.toInt(), end)
        }
    }

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)
    private val png = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 5, 6, 7, 8)

    private fun u32be(v: Int): ByteArray = byteArrayOf(
        (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte(),
    )

    private fun syncsafe(v: Int): ByteArray = byteArrayOf(
        ((v ushr 21) and 0x7F).toByte(), ((v ushr 14) and 0x7F).toByte(),
        ((v ushr 7) and 0x7F).toByte(), (v and 0x7F).toByte(),
    )

    private fun box(type: String, vararg payloads: ByteArray): ByteArray {
        val body = payloads.fold(byteArrayOf()) { acc, b -> acc + b }
        return u32be(8 + body.size) + type.toByteArray() + body
    }

    // ── ID3v2 ─────────────────────────────────────────────────────────────

    private fun id3File(version: Int, vararg frames: ByteArray, unsync: Boolean = false): ByteArray {
        val tag = frames.fold(byteArrayOf()) { acc, b -> acc + b }
        val flags = if (unsync) 0x80 else 0x00
        return "ID3".toByteArray() + byteArrayOf(version.toByte(), 0, flags.toByte()) +
            syncsafe(tag.size) + tag
    }

    private fun apicFrame(payload: ByteArray, v24: Boolean): ByteArray {
        val size = if (v24) syncsafe(payload.size) else u32be(payload.size)
        return "APIC".toByteArray() + size + byteArrayOf(0, 0) + payload
    }

    private fun apicPayload(mime: String, art: ByteArray): ByteArray =
        byteArrayOf(0) + "$mime\u0000".toByteArray() + byteArrayOf(3) + "d\u0000".toByteArray() + art

    @Test
    fun `id3v23 apic extracts`() {
        val file = id3File(3, apicFrame(apicPayload("image/jpeg", jpeg), v24 = false)) + ByteArray(100)
        val pic = EmbeddedArt.extract(MemReader(file))
        assertEquals("image/jpeg", pic?.mime)
        assertTrue(pic?.bytes?.contentEquals(jpeg) == true)
    }

    @Test
    fun `id3v24 apic extracts`() {
        val file = id3File(4, apicFrame(apicPayload("image/png", png), v24 = true)) + ByteArray(100)
        val pic = EmbeddedArt.extract(MemReader(file))
        assertEquals("image/png", pic?.mime)
        assertTrue(pic?.bytes?.contentEquals(png) == true)
    }

    @Test
    fun `id3v22 pic extracts`() {
        val payload = byteArrayOf(0) + "JPG".toByteArray() + byteArrayOf(0, 0) + jpeg
        val frame = "PIC".toByteArray() + byteArrayOf(0, 0, payload.size.toByte()) + payload
        val file = id3File(2, frame) + ByteArray(100)
        val pic = EmbeddedArt.extract(MemReader(file))
        assertEquals("image/jpeg", pic?.mime)
        assertTrue(pic?.bytes?.contentEquals(jpeg) == true)
    }

    @Test
    fun `id3 unsynchronised payload still extracts`() {
        // As stored (with the stuffing byte) and as decoded (without it).
        val stored = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00.toByte(), 0xE0.toByte(), 9)
        val decoded = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 9)
        val file = id3File(3, apicFrame(apicPayload("image/jpeg", stored), v24 = false), unsync = true) +
            ByteArray(100)
        val pic = EmbeddedArt.extract(MemReader(file))
        assertTrue(pic?.bytes?.contentEquals(decoded) == true)
    }

    @Test
    fun `id3 without picture returns null`() {
        val frame = "TIT2".toByteArray() + u32be(5) + byteArrayOf(0, 0) + byteArrayOf(0) + "hi\u0000".toByteArray()
        val file = id3File(3, frame) + ByteArray(100)
        assertNull(EmbeddedArt.extract(MemReader(file)))
    }

    // ── FLAC ──────────────────────────────────────────────────────────────

    private fun flacBlock(type: Int, payload: ByteArray, last: Boolean): ByteArray {
        val head = ((if (last) 0x80 else 0) or type).toByte()
        val len = payload.size
        return byteArrayOf(head, (len ushr 16).toByte(), (len ushr 8).toByte(), len.toByte()) + payload
    }

    private fun flacPicturePayload(mime: String, art: ByteArray): ByteArray =
        u32be(3) + u32be(mime.length) + mime.toByteArray() + u32be(0) +
            ByteArray(16) + u32be(art.size) + art

    @Test
    fun `flac picture extracts`() {
        val file = "fLaC".toByteArray() +
            flacBlock(0, ByteArray(34), last = false) +
            flacBlock(6, flacPicturePayload("image/png", png), last = true) +
            ByteArray(64)
        val pic = EmbeddedArt.extract(MemReader(file))
        assertEquals("image/png", pic?.mime)
        assertTrue(pic?.bytes?.contentEquals(png) == true)
    }

    @Test
    fun `flac without picture returns null`() {
        val file = "fLaC".toByteArray() + flacBlock(0, ByteArray(34), last = true) + ByteArray(64)
        assertNull(EmbeddedArt.extract(MemReader(file)))
    }

    @Test
    fun `flac absurd picture size returns null without allocating`() {
        val evil = u32be(3) + u32be(3) + "jpg".toByteArray() + u32be(0) + ByteArray(16) +
            u32be(Int.MAX_VALUE) + byteArrayOf(1, 2, 3)
        val file = "fLaC".toByteArray() + flacBlock(6, evil, last = true)
        assertNull(EmbeddedArt.extract(MemReader(file)))
    }

    // ── MP4 ───────────────────────────────────────────────────────────────

    @Test
    fun `mp4 covr extracts`() {
        val data = u32be(13) + u32be(0) + jpeg
        val covr = box("covr", box("data", data))
        val ilst = box("ilst", covr)
        val meta = box("meta", byteArrayOf(0, 0, 0, 0) + ilst)
        val moov = box("moov", box("udta", meta))
        val file = box("ftyp", "isom".toByteArray() + u32be(0) + "isom".toByteArray()) + moov +
            ByteArray(64)
        val pic = EmbeddedArt.extract(MemReader(file))
        assertEquals("image/jpeg", pic?.mime)
        assertTrue(pic?.bytes?.contentEquals(jpeg) == true)
    }

    @Test
    fun `mp4 moov at end extracts`() {
        val data = u32be(14) + u32be(0) + png
        val moov = box("moov", box("udta", box("meta", byteArrayOf(0, 0, 0, 0) + box("ilst", box("covr", box("data", data))))))
        val file = box("ftyp", "isom".toByteArray()) + box("mdat", ByteArray(5000)) + moov
        val pic = EmbeddedArt.extract(MemReader(file))
        assertEquals("image/png", pic?.mime)
        assertTrue(pic?.bytes?.contentEquals(png) == true)
    }

    @Test
    fun `mp4 without covr returns null`() {
        val file = box("ftyp", "isom".toByteArray()) + box("moov", box("mvhd", ByteArray(16)))
        assertNull(EmbeddedArt.extract(MemReader(file)))
    }

    // ── Ogg ───────────────────────────────────────────────────────────────

    private fun oggPage(body: ByteArray, serial: Int = 1, seq: Int = 0, continued: Boolean = false): ByteArray {
        val segs = ArrayList<Byte>()
        var rest = body.size
        while (rest >= 255) {
            segs.add(255.toByte())
            rest -= 255
        }
        segs.add(rest.toByte())
        val type = if (continued) 0x01 else 0x00
        var head = "OggS".toByteArray() + byteArrayOf(0, type.toByte()) +
            ByteArray(8) + u32be(serial) + u32be(seq) + u32be(0) + byteArrayOf(segs.size.toByte())
        head += segs.toByteArray()
        return head + body
    }

    private fun u32le(v: Int): ByteArray = byteArrayOf(
        v.toByte(), (v ushr 8).toByte(), (v ushr 16).toByte(), (v ushr 24).toByte(),
    )

    private fun vorbisCommentPacket(opus: Boolean, artB64: String): ByteArray {
        val field = "METADATA_BLOCK_PICTURE=$artB64".toByteArray()
        val vendor = "X".toByteArray()
        var packet = if (opus) {
            "OpusTags".toByteArray()
        } else {
            byteArrayOf(3) + "vorbis".toByteArray()
        }
        packet += u32le(vendor.size) + vendor + u32le(1) + u32le(field.size) + field
        return packet
    }

    private fun oggFile(opus: Boolean, artB64: String?): ByteArray {
        val ident = if (opus) {
            "OpusHead".toByteArray() + ByteArray(11)
        } else {
            byteArrayOf(1) + "vorbis".toByteArray() + ByteArray(23)
        }
        var out = oggPage(ident, seq = 0)
        if (artB64 != null) {
            out += oggPage(vorbisCommentPacket(opus, artB64), seq = 1)
        }
        return out + oggPage(ByteArray(100), seq = 2, continued = false)
    }

    private fun b64FlacPicture(mime: String, art: ByteArray): String =
        java.util.Base64.getEncoder().encodeToString(flacPicturePayload(mime, art))

    @Test
    fun `vorbis comment picture extracts`() {
        val file = oggFile(opus = false, artB64 = b64FlacPicture("image/jpeg", jpeg))
        val pic = EmbeddedArt.extract(MemReader(file))
        assertEquals("image/jpeg", pic?.mime)
        assertTrue(pic?.bytes?.contentEquals(jpeg) == true)
    }

    @Test
    fun `opus tags picture extracts`() {
        val file = oggFile(opus = true, artB64 = b64FlacPicture("image/png", png))
        val pic = EmbeddedArt.extract(MemReader(file))
        assertEquals("image/png", pic?.mime)
        assertTrue(pic?.bytes?.contentEquals(png) == true)
    }

    @Test
    fun `ogg without picture returns null`() {
        assertNull(EmbeddedArt.extract(MemReader(oggFile(opus = false, artB64 = null))))
    }

    // ── Negatives ─────────────────────────────────────────────────────────

    @Test
    fun `garbage and empties return null`() {
        assertNull(EmbeddedArt.extract(MemReader(ByteArray(0))))
        assertNull(EmbeddedArt.extract(MemReader(ByteArray(11))))
        assertNull(EmbeddedArt.extract(MemReader("hello world!".toByteArray())))
    }

    @Test
    fun `mime falls back to sniffed bytes`() {
        // APIC with a blank mime still resolves through the JPEG magic.
        val payload = byteArrayOf(0) + "\u0000".toByteArray() + byteArrayOf(3) + "d\u0000".toByteArray() + jpeg
        val file = id3File(3, apicFrame(payload, v24 = false)) + ByteArray(64)
        assertEquals("image/jpeg", EmbeddedArt.extract(MemReader(file))?.mime)
    }
}
