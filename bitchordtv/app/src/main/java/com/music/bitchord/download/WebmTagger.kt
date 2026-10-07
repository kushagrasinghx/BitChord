package com.music.bitchord.download

import com.music.bitchord.data.lyrics.WORD_LYRICS_FIELD

/**
 * Appends Matroska `Tags` and `Attachments` elements — title, artist, album,
 * lyrics, cover — to an already-downloaded WebM file, in place.
 *
 * The insertion is always a plain append at the end of the file, never a
 * splice in the middle, which is what makes this simpler than [Mp4Tagger]:
 * a downloaded track is one `EBML` header followed by one `Segment`, and
 * `Tags`/`Attachments` are ordinary children of that `Segment` with nothing
 * else addressing them by absolute offset (unlike MP4's `stco`/`co64`, an
 * optional `SeekHead` records where things are, but it is advisory — a
 * player without an entry for `Tags` in it still finds the element by
 * reading on, which is exactly what appending at the end relies on).
 *
 * The one field that can need touching is `Segment`'s own size, if it
 * declared one — a WebM served as a live remux typically declares it
 * "unknown" (an all-ones size, meaning "read to the end"), in which case
 * appending needs no further change at all. A declared size is only ever
 * widened in place, keeping its original byte width, because growing that
 * width would shift the size field itself and everything after it — the
 * same offset cascade [Mp4Tagger] exists to handle, which nothing here
 * reaches for. If the value doesn't fit the existing width, or the file
 * doesn't match the single-header/single-segment shape this assumes, the
 * input comes back unchanged rather than guessed at.
 */
object WebmTagger {

    private val EBML_HEADER_ID = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
    private val SEGMENT_ID = byteArrayOf(0x18, 0x53.toByte(), 0x80.toByte(), 0x67)

    private val ID_TAGS = byteArrayOf(0x12, 0x54, 0xC3.toByte(), 0x67)
    private val ID_TAG = byteArrayOf(0x73, 0x73)
    private val ID_TARGETS = byteArrayOf(0x63, 0xC0.toByte())
    private val ID_SIMPLETAG = byteArrayOf(0x67, 0xC8.toByte())
    private val ID_TAGNAME = byteArrayOf(0x45, 0xA3.toByte())
    private val ID_TAGSTRING = byteArrayOf(0x44, 0x87.toByte())
    private val ID_ATTACHMENTS = byteArrayOf(0x19, 0x41, 0xA4.toByte(), 0x69)
    private val ID_ATTACHEDFILE = byteArrayOf(0x61, 0xA7.toByte())
    private val ID_FILENAME = byteArrayOf(0x46, 0x6E)
    private val ID_FILEDESCRIPTION = byteArrayOf(0x46, 0x7E)
    private val ID_FILEMIMETYPE = byteArrayOf(0x46, 0x60)
    private val ID_FILEDATA = byteArrayOf(0x46, 0x5C)
    private val ID_FILEUID = byteArrayOf(0x46, 0xAE.toByte())

    fun tag(
        bytes: ByteArray,
        title: String,
        artist: String,
        album: String?,
        lyrics: String?,
        cover: ByteArray?,
        coverMime: String,
        /** The A2 form, under a name of this app's own — see [WORD_LYRICS_FIELD]. */
        wordLyrics: String? = null,
    ): ByteArray = runCatching {
        insert(bytes, buildTail(title, artist, album, lyrics, cover, coverMime, wordLyrics))
    }.getOrDefault(bytes)

    private fun insert(bytes: ByteArray, tail: ByteArray): ByteArray {
        if (tail.isEmpty()) return bytes
        if (bytes.size < 16 || !bytes.regionMatches(0, EBML_HEADER_ID)) return bytes

        val headerSize = readSize(bytes, EBML_HEADER_ID.size) ?: return bytes
        val segmentIdOffset = EBML_HEADER_ID.size + headerSize.width + headerSize.value.toInt()
        if (segmentIdOffset + 4 > bytes.size || !bytes.regionMatches(segmentIdOffset, SEGMENT_ID)) return bytes

        val segmentSize = readSize(bytes, segmentIdOffset + SEGMENT_ID.size) ?: return bytes
        val segmentContentStart = segmentIdOffset + SEGMENT_ID.size + segmentSize.width

        if (segmentSize.isUnknown) {
            val out = bytes.copyOf(bytes.size + tail.size)
            tail.copyInto(out, bytes.size)
            return out
        }

        // A declared size only matches this shape when it accounts for every
        // byte already in the file — anything else (trailing padding, more
        // top-level elements after Segment) isn't a layout worth guessing at.
        val declaredEnd = segmentContentStart + segmentSize.value
        if (declaredEnd != bytes.size.toLong()) return bytes

        val newSize = segmentSize.value + tail.size
        val maxForWidth = (1L shl (7 * segmentSize.width)) - 2
        if (newSize > maxForWidth) return bytes

        val out = bytes.copyOf(bytes.size + tail.size)
        tail.copyInto(out, bytes.size)
        writeVint(out, segmentIdOffset + SEGMENT_ID.size, newSize, segmentSize.width)
        return out
    }

    private fun buildTail(
        title: String,
        artist: String,
        album: String?,
        lyrics: String?,
        cover: ByteArray?,
        coverMime: String,
        wordLyrics: String?,
    ): ByteArray {
        var out = ByteArray(0)

        val simple = mutableListOf<ByteArray>()
        if (title.isNotBlank()) simple += simpleTag("TITLE", title)
        if (artist.isNotBlank()) simple += simpleTag("ARTIST", artist)
        if (!album.isNullOrBlank()) simple += simpleTag("ALBUM", album)
        // `LYRICS` is Matroska's own name for the field, and `TagString` is a
        // UTF-8 element with an explicit length — so the LRC's newlines need no
        // escaping and there is no ceiling worth worrying about here.
        if (!lyrics.isNullOrBlank()) simple += simpleTag("LYRICS", lyrics)
        // Beside `LYRICS`, never instead of it: a SimpleTag with a name a
        // player doesn't know is skipped, so the portable field is untouched.
        if (!wordLyrics.isNullOrBlank()) simple += simpleTag(WORD_LYRICS_FIELD, wordLyrics)
        if (simple.isNotEmpty()) {
            // An empty Targets applies the tag to the whole file — there is no
            // track/chapter to single out in a lone-audio-stream download.
            val targets = elem(ID_TARGETS, ByteArray(0))
            val tagPayload = simple.fold(targets) { acc, s -> acc + s }
            out += elem(ID_TAGS, elem(ID_TAG, tagPayload))
        }

        if (cover != null && cover.isNotEmpty()) {
            val fileName = elem(ID_FILENAME, "cover.jpg".toByteArray(Charsets.UTF_8))
            val description = elem(ID_FILEDESCRIPTION, "Cover (front)".toByteArray(Charsets.UTF_8))
            val fileMime = elem(ID_FILEMIMETYPE, coverMime.toByteArray(Charsets.US_ASCII))
            // A fixed id is fine: one attachment, and nothing here needs to
            // reference it back from a Tag.
            val fileUid = elem(ID_FILEUID, byteArrayOf(0, 0, 0, 0, 0, 0, 0, 1))
            val fileData = elem(ID_FILEDATA, cover)
            val attachedFile = elem(ID_ATTACHEDFILE, fileName + description + fileMime + fileUid + fileData)
            out += elem(ID_ATTACHMENTS, attachedFile)
        }

        return out
    }

    private fun simpleTag(name: String, value: String): ByteArray {
        val nameElem = elem(ID_TAGNAME, name.toByteArray(Charsets.US_ASCII))
        val stringElem = elem(ID_TAGSTRING, value.toByteArray(Charsets.UTF_8))
        return elem(ID_SIMPLETAG, nameElem + stringElem)
    }

    private fun elem(id: ByteArray, payload: ByteArray): ByteArray =
        id + encodeVint(payload.size.toLong()) + payload

    private class Size(val value: Long, val width: Int, val isUnknown: Boolean)

    /**
     * An EBML variable-length integer: the position of the highest set bit in
     * the first byte gives the width (1-8 bytes), and the remaining bits
     * across all of them are the value. A value using every one of those bits
     * (all ones) is the reserved "unknown length" marker.
     */
    private fun readSize(bytes: ByteArray, offset: Int): Size? {
        if (offset >= bytes.size) return null
        val first = bytes[offset].toInt() and 0xFF
        var width = 1
        var mask = 0x80
        while (mask != 0 && (first and mask) == 0) {
            mask = mask shr 1
            width++
        }
        if (mask == 0 || offset + width > bytes.size) return null
        var value = (first and (mask - 1)).toLong()
        for (i in 1 until width) value = (value shl 8) or (bytes[offset + i].toLong() and 0xFF)
        val maxVal = (1L shl (7 * width)) - 1
        return Size(value, width, value == maxVal)
    }

    /** The narrowest vint that fits [value], reserving the all-ones value as "unknown". */
    private fun encodeVint(value: Long): ByteArray {
        var width = 1
        while (width < 8 && value > (1L shl (7 * width)) - 2) width++
        return ByteArray(width).also { writeVint(it, 0, value, width) }
    }

    private fun writeVint(bytes: ByteArray, offset: Int, value: Long, width: Int) {
        var v = value
        for (i in width - 1 downTo 0) {
            bytes[offset + i] = (v and 0xFF).toByte()
            v = v shr 8
        }
        bytes[offset] = (bytes[offset].toInt() or (0x80 shr (width - 1))).toByte()
    }

    private fun ByteArray.regionMatches(offset: Int, other: ByteArray): Boolean {
        if (offset < 0 || offset + other.size > size) return false
        for (i in other.indices) if (this[offset + i] != other[i]) return false
        return true
    }
}
