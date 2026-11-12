package com.music.bitchord.data.remote

/**
 * Cover pictures embedded in audio files, read through bounded ranged
 * access instead of downloading whole tracks.
 *
 * Only the tag regions are ever touched: an ID3v2 tag, a FLAC metadata
 * chain, an MP4 box walk or the first Ogg pages. Anything unparseable —
 * exotic flags, encryption, a picture bigger than [MAX_PICTURE_BYTES] —
 * reads as absent rather than as an error, because a missing cover must
 * never fail whatever asked for one. WAV is deliberately unsupported: its
 * metadata lives in RIFF chunks a good number of players ignore outright.
 */
object EmbeddedArt {

    data class Picture(val bytes: ByteArray, val mime: String)

    /**
     * Random access into the file. Backends serve HTTP ranges or share
     * offsets; both are just (offset, length) reads here.
     */
    interface Reader {
        val size: Long
        fun read(offset: Long, length: Int): ByteArray
        fun close() {}
    }

    /** Largest single picture kept — phone screens don't need more. */
    const val MAX_PICTURE_BYTES = 8 * 1024 * 1024

    /** Largest tag region walked before giving up on a file. */
    private const val MAX_WALK_BYTES = 16 * 1024 * 1024

    fun extract(reader: Reader): Picture? = runCatching {
        val magic = reader.read(0, 12)
        if (magic.size < 12) return null
        when {
            magic[0] == 'I'.code.toByte() && magic[1] == 'D'.code.toByte() && magic[2] == '3'.code.toByte() ->
                id3(reader, magic)
            magic[0] == 'f'.code.toByte() && magic[1] == 'L'.code.toByte() &&
                magic[2] == 'a'.code.toByte() && magic[3] == 'C'.code.toByte() -> flac(reader)
            magic[4] == 'f'.code.toByte() && magic[5] == 't'.code.toByte() &&
                magic[6] == 'y'.code.toByte() && magic[7] == 'p'.code.toByte() -> mp4(reader)
            magic[0] == 'O'.code.toByte() && magic[1] == 'g'.code.toByte() &&
                magic[2] == 'g'.code.toByte() && magic[3] == 'S'.code.toByte() -> ogg(reader)
            else -> null
        }
    }.getOrNull()

    // ── Buffered cursor ───────────────────────────────────────────────────

    private class Cursor(private val reader: Reader) {
        private var windowStart = 0L
        private var window = ByteArray(0)
        var position = 0L
            private set

        fun seek(at: Long) {
            position = at
        }

        fun bytes(length: Int): ByteArray {
            require(length >= 0)
            if (position < windowStart || position + length > windowStart + window.size) {
                val fetch = maxOf(length.toLong(), 64L * 1024)
                    .coerceAtMost((reader.size - position).coerceAtLeast(0L))
                windowStart = position
                window = if (fetch > 0) reader.read(position, fetch.toInt()) else ByteArray(0)
            }
            val from = (position - windowStart).toInt()
            val to = (from + length).coerceAtMost(window.size)
            position += (to - from)
            return window.copyOfRange(from, to)
        }

        fun skip(length: Long) {
            position += length
        }
    }

    // ── ID3v2 (MP3) ───────────────────────────────────────────────────────

    private fun id3(reader: Reader, magic: ByteArray): Picture? {
        val version = magic[3].toInt() and 0xFF
        if (version !in 2..4) return null
        val unsync = magic[5].toInt() and 0x80 != 0
        var tagSize = syncsafe(magic, 6)
        if (tagSize <= 0 || tagSize > MAX_WALK_BYTES) return null
        val tag = reader.read(10, tagSize)
        if (tag.size < tagSize) return null
        var pos = 0
        // Extended header: the declared size counts itself on v2.3 and not on
        // v2.4, and both conventions ship in the wild — try each, plus the two
        // fixed sizes, and take the first offset that parses as frames.
        if (magic[5].toInt() and 0x40 != 0 && tag.size >= 4) {
            val declared = if (version == 4) syncsafe(tag, 0) else u32(tag, 0)
            pos = listOf(declared, declared - 4, 6, 10)
                .firstOrNull { it in 0..tag.size && framesStart(tag, it, version) } ?: return null
        }
        while (pos + 10 <= tag.size) {
            val idLen = if (version == 2) 3 else 4
            if (pos + idLen + 3 > tag.size) break
            val id = tag.decodeToString(pos, pos + idLen)
            if (id.all { it == '\u0000' }) break
            val isPicture = (version == 2 && id == "PIC") || (version != 2 && id == "APIC")
            val size = if (version == 2) {
                u24(tag, pos + 3)
            } else if (version == 4) {
                syncsafe(tag, pos + 4)
            } else {
                u32(tag, pos + 4)
            }
            val headLen = if (version == 2) 6 else 10
            if (size <= 0 || pos + headLen + size > tag.size + 1 || !id.all { it.isLetterOrDigit() }) {
                // Padding, or a frame this walk can't trust — either way the
                // tag has nothing more to say from here.
                if (!isPicture) break
            }
            if (isPicture && size > 0 && pos + headLen + size <= tag.size) {
                val raw = tag.copyOfRange(pos + headLen, pos + headLen + size)
                // Sizes count stored bytes, so frame boundaries stay exact and
                // only the payload is de-unsynchronised — the other order
                // drifts every frame after the first stuffed byte.
                val payload = if (unsync) deunsync(raw) else raw
                pictureFromApic(payload, version)?.let { return it }
                return null
            }
            pos += headLen + size
        }
        return null
    }

    private fun framesStart(tag: ByteArray, at: Int, version: Int): Boolean {
        if (at + 4 > tag.size) return false
        if (tag.copyOfRange(at, minOf(at + 4, tag.size)).all { it == 0.toByte() }) return true
        val idLen = if (version == 2) 3 else 4
        if (at + idLen > tag.size) return false
        return tag.decodeToString(at, at + idLen).all { it.isLetterOrDigit() }
    }

    private fun deunsync(tag: ByteArray): ByteArray {
        val out = ByteArray(tag.size)
        var w = 0
        var r = 0
        while (r < tag.size) {
            out[w++] = tag[r]
            if (tag[r] == 0xFF.toByte() && r + 1 < tag.size && tag[r + 1] == 0.toByte()) r++
            r++
        }
        return out.copyOf(w)
    }

    private fun pictureFromApic(payload: ByteArray, version: Int): Picture? {
        if (payload.isEmpty()) return null
        return if (version == 2) {
            // encoding(1) + format(3) + type(1) + description + data
            if (payload.size < 6) return null
            val format = payload.decodeToString(1, 4).uppercase()
            val mime = if (format == "PNG") "image/png" else "image/jpeg"
            var at = 5
            at = skipNull(payload, at, 1) ?: return null
            picture(payload.copyOfRange(at, payload.size), mime)
        } else {
            var at = 1
            var mimeEnd = -1
            for (k in at until payload.size) {
                if (payload[k] == 0.toByte()) {
                    mimeEnd = k
                    break
                }
            }
            if (mimeEnd < 0) return null
            val mime = payload.decodeToString(at, mimeEnd).ifBlank { null }
            at = mimeEnd + 1
            if (at >= payload.size) return null
            at++ // picture type
            val encoding = payload[0].toInt() and 0xFF
            at = skipNull(payload, at, if (encoding == 1 || encoding == 2) 2 else 1) ?: return null
            picture(payload.copyOfRange(at, payload.size), mime)
        }
    }

    private fun skipNull(payload: ByteArray, at: Int, width: Int): Int? {
        var i = at
        while (i + width <= payload.size) {
            var zero = true
            for (k in 0 until width) if (payload[i + k] != 0.toByte()) { zero = false; break }
            if (zero) return i + width
            i += if (width == 2 && payload[i] != 0.toByte()) 2 else 1
        }
        return null
    }

    // ── FLAC ──────────────────────────────────────────────────────────────

    private fun flac(reader: Reader): Picture? {
        val cursor = Cursor(reader)
        cursor.seek(4)
        var walked = 0L
        while (true) {
            val head = cursor.bytes(4)
            if (head.size < 4) return null
            val last = head[0].toInt() and 0x80 != 0
            val type = head[0].toInt() and 0x7F
            val length = u24(head, 1).toLong()
            if (length < 0 || length > MAX_PICTURE_BYTES + 1024L) return null
            walked += 4
            if (walked > MAX_WALK_BYTES) return null
            if (type == 6) {
                return flacPicture(reader, cursor.position, length.toInt())
            }
            cursor.skip(length)
            if (last) return null
        }
    }

    /** A PICTURE block body: metadata, then the image at a known extent. */
    internal fun flacPicture(reader: Reader, at: Long, length: Int): Picture? {
        val head = reader.read(at, minOf(length, 64)).takeIf { it.size >= 8 } ?: return null
        var pos = 0
        fun u32at(): Int {
            if (pos + 4 > head.size) return -1
            return u32(head, pos).also { pos += 4 }
        }
        u32at() // picture type
        val mimeLen = u32at().toInt()
        if (mimeLen < 0 || pos + mimeLen > head.size) return null
        val mime = head.decodeToString(pos, pos + mimeLen).ifBlank { null }
        pos += mimeLen
        val descLen = u32at().toInt()
        if (descLen < 0 || pos + descLen + 20 > head.size) return null
        pos += descLen + 16 // description, width, height, depth, colors
        val dataLen = u32at()
        if (dataLen <= 0 || dataLen > MAX_PICTURE_BYTES) return null
        val dataAt = at + pos
        val data = reader.read(dataAt, dataLen.toInt())
        if (data.size != dataLen.toInt()) return null
        return picture(data, mime)
    }

    // ── MP4/M4A ───────────────────────────────────────────────────────────

    private fun mp4(reader: Reader): Picture? {
        val cursor = Cursor(reader)
        return findBox(cursor, 0, reader.size, setOf("moov"), 0)?.let { moov ->
            findBox(cursor, moov.start, moov.end, setOf("udta"), 1)?.let { udta ->
                findBox(cursor, udta.start, udta.end, setOf("meta"), 2)?.let { meta ->
                    // meta is a full box: 4 version/flags bytes before its children.
                    findBox(cursor, meta.start + 4, meta.end, setOf("ilst"), 3)?.let { ilst ->
                        covrData(cursor, ilst.start, ilst.end)
                    }
                }
            }
        }
    }

    private data class Box(val start: Long, val end: Long)

    private fun boxHeader(cursor: Cursor, at: Long): Triple<String, Long, Long>? {
        cursor.seek(at)
        val head = cursor.bytes(8)
        if (head.size < 8) return null
        var size = u32(head, 0).toLong()
        val type = head.decodeToString(4, 8)
        var headerLen = 8L
        if (size == 1L) {
            val ext = cursor.bytes(8)
            if (ext.size < 8) return null
            size = u64(ext, 0)
            headerLen = 16L
            if (size < headerLen) return null
        } else if (size == 0L) {
            // To the end of the file — bounded like any other walk.
            size = MAX_WALK_BYTES.toLong()
        }
        if (size < headerLen || size > MAX_WALK_BYTES * 4) return null
        return Triple(type, at + headerLen, at + size)
    }

    private fun findBox(cursor: Cursor, from: Long, until: Long, want: Set<String>, depth: Int): Box? {
        if (depth > 8 || from >= until) return null
        var at = from
        var walked = 0L
        while (at + 8 <= until && walked < MAX_WALK_BYTES) {
            val (type, payloadStart, payloadEnd) = boxHeader(cursor, at) ?: return null
            if (type in want) return Box(payloadStart, payloadEnd)
            walked += payloadEnd - at
            at = payloadEnd
        }
        return null
    }

    private fun covrData(cursor: Cursor, from: Long, until: Long): Picture? {
        findBox(cursor, from, until, setOf("covr"), 4)?.let { covr ->
            findBox(cursor, covr.start, covr.end, setOf("data"), 5)?.let { data ->
                cursor.seek(data.start)
                val head = cursor.bytes(8)
                if (head.size < 8) return null
                val kind = u32(head, 0)
                val mime = when (kind) {
                    13 -> "image/jpeg"
                    14 -> "image/png"
                    27 -> "image/bmp"
                    else -> null
                }
                val length = (data.end - data.start - 8).toInt()
                if (length <= 0 || length > MAX_PICTURE_BYTES) return null
                val bytes = cursor.bytes(length)
                if (bytes.size != length) return null
                return picture(bytes, mime)
            }
        }
        return null
    }

    // ── Ogg Vorbis / Opus ─────────────────────────────────────────────────

    private fun ogg(reader: Reader): Picture? {
        val cursor = Cursor(reader)
        var pages = 0
        var packet = ByteArray(0)
        var at = 0L
        while (pages++ < 64) {
            cursor.seek(at)
            val head = cursor.bytes(27)
            if (head.size < 27) return null
            if (!(head[0] == 'O'.code.toByte() && head[1] == 'g'.code.toByte() &&
                    head[2] == 'g'.code.toByte() && head[3] == 'S'.code.toByte())
            ) {
                return null
            }
            val continued = head[5].toInt() and 0x01 != 0
            val segCount = head[26].toInt() and 0xFF
            val table = cursor.bytes(segCount)
            if (table.size < segCount) return null
            var bodyLen = 0
            for (b in table) bodyLen += b.toInt() and 0xFF
            val body = cursor.bytes(bodyLen)
            if (body.size < bodyLen) return null
            packet = if (continued) packet + body else body
            at = cursor.position
            if (table.isNotEmpty() && table.last().toInt() and 0xFF < 255) {
                // Packet ends here: comment packets name themselves up front.
                if (packet.size > 7 && packet[0] == 0x03.toByte() &&
                    packet[1] == 'v'.code.toByte() && packet[2] == 'o'.code.toByte()
                ) {
                    vorbisPicture(packet)?.let { return it }
                    return null
                }
                if (packet.size > 8 && packet.copyOfRange(0, 8).decodeToString() == "OpusTags") {
                    vorbisPicture(packet, opus = true)?.let { return it }
                    return null
                }
                packet = ByteArray(0)
                if (pages > 4) return null
            }
            if (cursor.position - 0 > MAX_WALK_BYTES) return null
        }
        return null
    }

    private fun vorbisPicture(packet: ByteArray, opus: Boolean = false): Picture? {
        // [type/vorbis-magic][vendor len + vendor][count][len + field]...
        var pos = if (opus) 8 else 7
        fun u32le(): Long {
            if (pos + 4 > packet.size) return -1
            val v = (packet[pos].toLong() and 0xFF) or
                ((packet[pos + 1].toLong() and 0xFF) shl 8) or
                ((packet[pos + 2].toLong() and 0xFF) shl 16) or
                ((packet[pos + 3].toLong() and 0xFF) shl 24)
            pos += 4
            return v
        }
        val vendorLen = u32le()
        if (vendorLen < 0 || pos + vendorLen > packet.size) return null
        pos += vendorLen.toInt()
        val count = u32le()
        if (count < 0 || count > 10_000) return null
        repeat(count.toInt()) {
            val len = u32le()
            if (len < 0 || pos + len > packet.size) return null
            val field = packet.decodeToString(pos, (pos + len).toInt())
            pos += len.toInt()
            if (field.startsWith("METADATA_BLOCK_PICTURE=", ignoreCase = true)) {
                val payload = runCatching {
                    java.util.Base64.getMimeDecoder()
                        .decode(field.substringAfter('='))
                }.getOrNull() ?: return@repeat
                // A FLAC PICTURE struct without the 4-byte block header.
                return flacPictureBytes(payload)
            }
        }
        return null
    }

    private fun flacPictureBytes(payload: ByteArray): Picture? {
        if (payload.size < 32) return null
        var pos = 0
        fun u32be(): Int {
            if (pos + 4 > payload.size) return -1
            return u32(payload, pos).also { pos += 4 }
        }
        u32be()
        val mimeLen = u32be().toInt()
        if (mimeLen < 0 || pos + mimeLen > payload.size) return null
        val mime = payload.decodeToString(pos, pos + mimeLen).ifBlank { null }
        pos += mimeLen
        val descLen = u32be().toInt()
        if (descLen < 0 || pos + descLen + 20 > payload.size) return null
        pos += descLen + 16
        val dataLen = u32be()
        if (dataLen <= 0 || dataLen > MAX_PICTURE_BYTES || pos + dataLen > payload.size) return null
        return picture(payload.copyOfRange(pos, (pos + dataLen).toInt()), mime)
    }

    // ── Small byte readers ────────────────────────────────────────────────

    private fun syncsafe(bytes: ByteArray, at: Int): Int {
        if (at + 4 > bytes.size) return -1
        return ((bytes[at].toInt() and 0x7F) shl 21) or
            ((bytes[at + 1].toInt() and 0x7F) shl 14) or
            ((bytes[at + 2].toInt() and 0x7F) shl 7) or
            (bytes[at + 3].toInt() and 0x7F)
    }

    /**
     * Unsigned 32-bit big-endian, saturated: every caller needs an array
     * index or a length out of it, and a value past 2GB fails every range
     * check below rather than overflowing into one that passes.
     */
    private fun u32(bytes: ByteArray, at: Int): Int {
        if (at + 4 > bytes.size) return -1
        val v = ((bytes[at].toLong() and 0xFF) shl 24) or
            ((bytes[at + 1].toLong() and 0xFF) shl 16) or
            ((bytes[at + 2].toLong() and 0xFF) shl 8) or
            (bytes[at + 3].toLong() and 0xFF)
        return v.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun u24(bytes: ByteArray, at: Int): Int {
        if (at + 3 > bytes.size) return -1
        return ((bytes[at].toInt() and 0xFF) shl 16) or
            ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            (bytes[at + 2].toInt() and 0xFF)
    }

    private fun u64(bytes: ByteArray, at: Int): Long {
        if (at + 8 > bytes.size) return -1
        var v = 0L
        for (k in 0 until 8) v = (v shl 8) or (bytes[at + k].toLong() and 0xFF)
        return v
    }

    private fun picture(bytes: ByteArray, mime: String?): Picture? {
        if (bytes.isEmpty() || bytes.size > MAX_PICTURE_BYTES) return null
        return Picture(bytes, mime?.takeIf { it.isNotBlank() } ?: sniffMime(bytes) ?: "image/jpeg")
    }

    private fun sniffMime(bytes: ByteArray): String? {
        if (bytes.size < 4) return null
        return when {
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "image/png"
            bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() -> "image/gif"
            bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte() -> "image/bmp"
            bytes.size >= 12 && bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() -> "image/webp"
            else -> null
        }
    }
}
