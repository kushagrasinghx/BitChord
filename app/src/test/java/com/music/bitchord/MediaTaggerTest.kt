package com.music.bitchord

import com.music.bitchord.download.FlacTagger
import com.music.bitchord.download.Mp4Tagger
import com.music.bitchord.download.WebmTagger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * All three taggers touch raw bytes of a file a user will actually try to play,
 * so these check the two things that matter most: a container the taggers
 * don't recognise comes back byte-for-byte unchanged, and one they do comes
 * back with every existing byte preserved (just shifted, for MP4) plus the
 * new metadata recoverable at the position it should be.
 */
class MediaTaggerTest {

    private fun box(type: String, payload: ByteArray): ByteArray {
        val out = ByteArray(8 + payload.size)
        val size = out.size
        out[0] = (size ushr 24).toByte()
        out[1] = (size ushr 16).toByte()
        out[2] = (size ushr 8).toByte()
        out[3] = size.toByte()
        type.toByteArray(Charsets.ISO_8859_1).copyInto(out, 4)
        payload.copyInto(out, 8)
        return out
    }

    private fun u32(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    private fun readU32(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xFF) shl 24) or ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or (bytes[offset + 3].toLong() and 0xFF)

    private fun ByteArray.indexOfBytes(needle: ByteArray, from: Int = 0): Int {
        outer@ for (i in from..size - needle.size) {
            for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    /** `ftyp` + `moov(trak/mdia/minf/stbl/stco)` + `mdat`, with `stco`'s one entry pointing at `mdat`'s payload. */
    private fun buildFakeMp4(mdatPayload: ByteArray): Triple<ByteArray, Int, Int> {
        val ftyp = box("ftyp", ByteArray(8))

        fun moovWithStcoOffset(offset: Int): ByteArray {
            val stcoPayload = ByteArray(12)
            stcoPayload[7] = 1 // entry_count = 1
            u32(offset).copyInto(stcoPayload, 8)
            val stco = box("stco", stcoPayload)
            val stbl = box("stbl", stco)
            val minf = box("minf", stbl)
            val mdia = box("mdia", minf)
            val trak = box("trak", mdia)
            return box("moov", trak)
        }

        // moov's length doesn't depend on the offset value itself (both are
        // fixed 4-byte fields), so a placeholder pass is enough to learn where
        // mdat's payload will actually start.
        val moovPlaceholder = moovWithStcoOffset(0)
        val mdatPayloadOffset = ftyp.size + moovPlaceholder.size + 8
        val moov = moovWithStcoOffset(mdatPayloadOffset)
        check(moov.size == moovPlaceholder.size)

        val mdat = box("mdat", mdatPayload)
        return Triple(ftyp + moov + mdat, mdatPayloadOffset, ftyp.size)
    }

    @Test
    fun `mp4 tagging preserves mdat bytes and repoints stco at their new offset`() {
        val mdatPayload = ByteArray(24) { (it + 1).toByte() }
        val (original, mdatPayloadOffset, _) = buildFakeMp4(mdatPayload)
        val cover = byteArrayOf(9, 8, 7, 6, 5)

        val tagged = Mp4Tagger.tag(original, "My Title", "My Artist", "My Album", null, cover, coverIsPng = false)

        assertNotSame(original, tagged)
        val delta = tagged.size - original.size
        assertTrue("tagging should grow the file", delta > 0)

        val newOffset = mdatPayloadOffset + delta
        assertArrayEquals(mdatPayload, tagged.copyOfRange(newOffset, newOffset + mdatPayload.size))

        val stcoTypePos = tagged.indexOfBytes("stco".toByteArray(Charsets.US_ASCII))
        assertTrue(stcoTypePos >= 0)
        val entryOffsetPos = stcoTypePos + 4 + 4 + 4 // past type, version/flags, entry_count
        assertEquals(newOffset.toLong(), readU32(tagged, entryOffsetPos))

        assertTrue(tagged.indexOfBytes("My Title".toByteArray(Charsets.UTF_8)) >= 0)
        assertTrue(tagged.indexOfBytes("My Artist".toByteArray(Charsets.UTF_8)) >= 0)
        assertTrue(tagged.indexOfBytes("My Album".toByteArray(Charsets.UTF_8)) >= 0)
        assertTrue(tagged.indexOfBytes(cover) >= 0)
    }

    @Test
    fun `mp4 tagging is a no-op without a moov box`() {
        val bytes = box("ftyp", ByteArray(8)) + box("mdat", ByteArray(16))
        val tagged = Mp4Tagger.tag(bytes, "Title", "Artist", null, null, null, false)
        assertSame(bytes, tagged)
    }

    @Test
    fun `mp4 tagging is a no-op with nothing worth writing`() {
        val (original, _, _) = buildFakeMp4(ByteArray(4))
        val tagged = Mp4Tagger.tag(original, "", "", null, null, null, false)
        assertSame(original, tagged)
    }

    /**
     * Lyrics on their own have to be enough to trigger a rewrite. [MediaTagger]
     * decides whether to touch the file by comparing references, so a tagger
     * that treated lyrics as an afterthought — added to the atom list but not
     * counted when deciding whether there is anything to write — would return
     * the input for a track that has lyrics and nothing else, and the field
     * would silently never appear.
     */
    @Test
    fun `mp4 tagging writes lyrics into a lyr atom on their own`() {
        val (original, _, _) = buildFakeMp4(ByteArray(4))

        val tagged = Mp4Tagger.tag(original, "", "", null, LRC, null, false)

        assertNotSame(original, tagged)
        // ISO-8859-1, because the atom name leads with the 0xA9 byte that plain
        // ASCII can't encode — the same reason [Mp4Tagger.box] uses it.
        assertTrue(tagged.indexOfBytes("©lyr".toByteArray(Charsets.ISO_8859_1)) >= 0)
        assertTrue(tagged.indexOfBytes(LRC.toByteArray(Charsets.UTF_8)) >= 0)
    }

    private val ebmlHeaderId = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
    private val segmentId = byteArrayOf(0x18, 0x53.toByte(), 0x80.toByte(), 0x67)

    /** A one-byte-vint EBML header (4 bytes of dummy payload) followed by a `Segment` of [segmentSize]. */
    private fun buildFakeWebm(segmentBody: ByteArray, segmentSize: ByteArray): ByteArray {
        val header = ebmlHeaderId + byteArrayOf(0x84.toByte()) + ByteArray(4) // size vint = 4, one byte wide
        return header + segmentId + segmentSize + segmentBody
    }

    @Test
    fun `webm tagging appends after an unknown-size segment untouched`() {
        val unknownSize = byteArrayOf(0x01) + ByteArray(7) { 0xFF.toByte() }
        val segmentBody = ByteArray(10) { (it + 1).toByte() }
        val original = buildFakeWebm(segmentBody, unknownSize)

        val cover = byteArrayOf(3, 1, 4, 1, 5)
        val tagged = WebmTagger.tag(original, "T", "A", "Al", null, cover, "image/jpeg")

        assertNotSame(original, tagged)
        assertArrayEquals(original, tagged.copyOfRange(0, original.size))
        assertTrue(tagged.indexOfBytes("TITLE".toByteArray(Charsets.US_ASCII)) >= 0)
        assertTrue(tagged.indexOfBytes("ARTIST".toByteArray(Charsets.US_ASCII)) >= 0)
        assertTrue(tagged.indexOfBytes("ALBUM".toByteArray(Charsets.US_ASCII)) >= 0)
        assertTrue(tagged.indexOfBytes(cover) >= 0)
    }

    @Test
    fun `webm tagging widens a definite segment size in place`() {
        // A 2-byte vint (marker 0x40..) covering exactly the body that follows.
        val segmentBody = ByteArray(20) { it.toByte() }
        val declaredSize = segmentBody.size.toLong()
        val sizeField = byteArrayOf(
            (0x40 or ((declaredSize ushr 8).toInt() and 0x3F)).toByte(),
            (declaredSize and 0xFF).toByte(),
        )
        val original = buildFakeWebm(segmentBody, sizeField)

        val tagged = WebmTagger.tag(original, "T", "", null, null, null, "image/jpeg")

        assertNotSame(original, tagged)
        val sizeFieldOffset = ebmlHeaderId.size + 1 + 4 + segmentId.size
        // Everything except the 2-byte size field itself — which is expected
        // to change, that's the point of this test — is untouched.
        assertArrayEquals(original.copyOfRange(0, sizeFieldOffset), tagged.copyOfRange(0, sizeFieldOffset))
        assertArrayEquals(
            original.copyOfRange(sizeFieldOffset + 2, original.size),
            tagged.copyOfRange(sizeFieldOffset + 2, sizeFieldOffset + 2 + segmentBody.size),
        )

        val b0 = tagged[sizeFieldOffset].toInt() and 0xFF
        val b1 = tagged[sizeFieldOffset + 1].toInt() and 0xFF
        val decoded = ((b0 and 0x3F) shl 8) or b1
        val newBodyLength = tagged.size - (sizeFieldOffset + 2)
        assertEquals(newBodyLength.toLong(), decoded.toLong())
    }

    @Test
    fun `webm tagging is a no-op without a recognisable ebml header`() {
        val bytes = ByteArray(20) { it.toByte() }
        val tagged = WebmTagger.tag(bytes, "Title", "Artist", null, null, null, "image/jpeg")
        assertSame(bytes, tagged)
    }

    /** As for MP4: lyrics alone have to be reason enough to append a `Tags` element. */
    @Test
    fun `webm tagging writes lyrics into a LYRICS simpletag on their own`() {
        val unknownSize = byteArrayOf(0x01) + ByteArray(7) { 0xFF.toByte() }
        val original = buildFakeWebm(ByteArray(10) { (it + 1).toByte() }, unknownSize)

        val tagged = WebmTagger.tag(original, "", "", null, LRC, null, "image/jpeg")

        assertNotSame(original, tagged)
        assertArrayEquals(original, tagged.copyOfRange(0, original.size))
        assertTrue(tagged.indexOfBytes("LYRICS".toByteArray(Charsets.US_ASCII)) >= 0)
        assertTrue(tagged.indexOfBytes(LRC.toByteArray(Charsets.UTF_8)) >= 0)
    }

    // ---- FLAC ---------------------------------------------------------------

    private val flacMagic = "fLaC".toByteArray(Charsets.US_ASCII)

    private fun flacBlock(type: Int, payload: ByteArray, last: Boolean = false): ByteArray =
        byteArrayOf(
            (type or if (last) 0x80 else 0).toByte(),
            (payload.size ushr 16).toByte(),
            (payload.size ushr 8).toByte(),
            payload.size.toByte(),
        ) + payload

    /**
     * The metadata chain of [bytes] as (type, payload), and where the audio
     * frames begin.
     *
     * Walking to the last-block flag rather than to a known count is the point:
     * a flag left set on a block that is no longer last stops the walk early and
     * every assertion made off the result then fails, which is exactly the bug
     * worth catching.
     */
    private fun flacChain(bytes: ByteArray): Pair<List<Pair<Int, ByteArray>>, Int> {
        assertArrayEquals(flacMagic, bytes.copyOfRange(0, 4))
        val blocks = mutableListOf<Pair<Int, ByteArray>>()
        var offset = 4
        while (true) {
            val flags = bytes[offset].toInt() and 0xFF
            val length = ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or (bytes[offset + 3].toInt() and 0xFF)
            blocks += (flags and 0x7F) to bytes.copyOfRange(offset + 4, offset + 4 + length)
            offset += 4 + length
            if (flags and 0x80 != 0) return blocks to offset
        }
    }

    private fun le32(value: Int): ByteArray = byteArrayOf(
        value.toByte(),
        (value ushr 8).toByte(),
        (value ushr 16).toByte(),
        (value ushr 24).toByte(),
    )

    private fun readU32Le(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun vorbisComment(vendor: String, fields: List<String>): ByteArray {
        val vendorBytes = vendor.toByteArray(Charsets.UTF_8)
        var out = le32(vendorBytes.size) + vendorBytes + le32(fields.size)
        for (field in fields) {
            val encoded = field.toByteArray(Charsets.UTF_8)
            out += le32(encoded.size) + encoded
        }
        return out
    }

    @Test
    fun `flac tagging keeps the frames, carries other blocks and spends the padding`() {
        val streamInfo = ByteArray(34) { it.toByte() }
        val seekTable = ByteArray(18) { (it + 100).toByte() }
        val frames = ByteArray(64) { (it + 1).toByte() }
        val original = flacMagic +
            flacBlock(TYPE_STREAMINFO, streamInfo) +
            flacBlock(TYPE_SEEKTABLE, seekTable) +
            flacBlock(TYPE_PADDING, ByteArray(200), last = true) +
            frames
        val cover = byteArrayOf(9, 8, 7, 6, 5)

        val tagged = FlacTagger.tag(original, "My Title", "My Artist", "My Album", null, cover, "image/jpeg")

        assertNotSame(original, tagged)
        val (blocks, framesAt) = flacChain(tagged)
        // STREAMINFO first, SEEKTABLE carried across, PADDING gone, the two new
        // blocks appended — and the last-block flag on the last of them, which
        // is what let the walk get this far.
        assertEquals(
            listOf(TYPE_STREAMINFO, TYPE_SEEKTABLE, TYPE_VORBIS_COMMENT, TYPE_PICTURE),
            blocks.map { it.first },
        )
        assertArrayEquals(streamInfo, blocks[0].second)
        assertArrayEquals(seekTable, blocks[1].second)
        assertArrayEquals(frames, tagged.copyOfRange(framesAt, tagged.size))

        // Little-endian lengths, and no framing bit after the last field. Both
        // are inherited from Ogg Vorbis' comment layout except that the framing
        // bit isn't, and both are silent when written the other way.
        val comment = blocks[2].second
        val vendorLength = readU32Le(comment, 0)
        assertEquals("BitChord", String(comment, 4, vendorLength, Charsets.UTF_8))
        var at = 4 + vendorLength
        val count = readU32Le(comment, at)
        at += 4
        assertEquals(3L, count.toLong())
        val fields = buildList {
            repeat(count) {
                val length = readU32Le(comment, at)
                at += 4
                add(String(comment, at, length, Charsets.UTF_8))
                at += length
            }
        }
        assertEquals(listOf("TITLE=My Title", "ARTIST=My Artist", "ALBUM=My Album"), fields)
        assertEquals(comment.size.toLong(), at.toLong())

        // The picture block is big-endian, unlike the one above it.
        val picture = blocks[3].second
        assertEquals(3L, readU32(picture, 0)) // front cover
        val mimeLength = readU32(picture, 4).toInt()
        assertEquals("image/jpeg", String(picture, 8, mimeLength, Charsets.US_ASCII))
        var pat = 8 + mimeLength
        // Description length, width, height, colour depth, colours used.
        repeat(5) {
            assertEquals(0L, readU32(picture, pat))
            pat += 4
        }
        assertEquals(cover.size.toLong(), readU32(picture, pat))
        pat += 4
        assertArrayEquals(cover, picture.copyOfRange(pat, picture.size))
    }

    @Test
    fun `flac tagging replaces an existing comment rather than adding a second`() {
        val stale = vorbisComment("Somebody Else", listOf("TITLE=Old Title", "COMMENT=stale"))
        val frames = ByteArray(16) { 7 }
        val original = flacMagic +
            flacBlock(TYPE_STREAMINFO, ByteArray(34)) +
            flacBlock(TYPE_VORBIS_COMMENT, stale, last = true) +
            frames

        val tagged = FlacTagger.tag(original, "New Title", "New Artist", null, null, null, "image/jpeg")

        val (blocks, framesAt) = flacChain(tagged)
        assertEquals(1L, blocks.count { it.first == TYPE_VORBIS_COMMENT }.toLong())
        assertArrayEquals(frames, tagged.copyOfRange(framesAt, tagged.size))
        assertTrue(tagged.indexOfBytes("TITLE=New Title".toByteArray(Charsets.UTF_8)) >= 0)
        assertEquals(-1, tagged.indexOfBytes("Old Title".toByteArray(Charsets.UTF_8)))
        assertEquals(-1, tagged.indexOfBytes("stale".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `flac tagging is a no-op without the fLaC magic`() {
        val bytes = ByteArray(64) { it.toByte() }
        assertSame(bytes, FlacTagger.tag(bytes, "Title", "Artist", null, null, null, "image/jpeg"))
    }

    @Test
    fun `flac tagging is a no-op when a block claims more bytes than the file has`() {
        val original = flacMagic +
            byteArrayOf(TYPE_STREAMINFO.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()) +
            ByteArray(34)
        assertSame(original, FlacTagger.tag(original, "Title", "Artist", null, null, null, "image/jpeg"))
    }

    @Test
    fun `flac tagging is a no-op with nothing to write`() {
        val original = flacMagic + flacBlock(TYPE_STREAMINFO, ByteArray(34), last = true) + ByteArray(16)
        assertSame(original, FlacTagger.tag(original, "   ", "", null, null, null, "image/jpeg"))
    }

    /**
     * As for the other two: lyrics alone have to produce a `VORBIS_COMMENT`.
     *
     * Also checks the field survives its own newlines, which is the one thing
     * about a multi-line value worth asserting — a Vorbis field is length-
     * prefixed, so nothing needs escaping, and the failure mode of getting that
     * wrong is a comment block truncated at the first line break.
     */
    @Test
    fun `flac tagging writes multi-line lyrics into a LYRICS field on their own`() {
        val frames = ByteArray(16) { 7 }
        val original = flacMagic + flacBlock(TYPE_STREAMINFO, ByteArray(34), last = true) + frames

        val tagged = FlacTagger.tag(original, "", "", null, LRC, null, "image/jpeg")

        assertNotSame(original, tagged)
        val (blocks, framesAt) = flacChain(tagged)
        assertEquals(listOf(TYPE_STREAMINFO, TYPE_VORBIS_COMMENT), blocks.map { it.first })
        assertArrayEquals(frames, tagged.copyOfRange(framesAt, tagged.size))
        assertTrue(tagged.indexOfBytes("LYRICS=$LRC".toByteArray(Charsets.UTF_8)) >= 0)
    }

    private companion object {
        const val TYPE_STREAMINFO = 0
        const val TYPE_PADDING = 1
        const val TYPE_SEEKTABLE = 3
        const val TYPE_VORBIS_COMMENT = 4
        const val TYPE_PICTURE = 6

        /**
         * Stand-in LRC, in the shape `LrcWriter` emits — two stamped lines and
         * the newline between them, which is all these tests need to look for.
         */
        const val LRC = "[00:01.20]first line here\n[00:04.50]second line here"
    }
}
