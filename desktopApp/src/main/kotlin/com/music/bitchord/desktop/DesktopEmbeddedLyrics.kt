package com.music.bitchord.desktop

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * The lyrics already sitting inside a local file.
 *
 * A track that was downloaded had its lyrics fetched once and written into the file; asking four
 * servers for them again on every play is a network round trip to arrive at a string already on
 * disk, and it is why a downloaded song showed nothing at all with the connection off.
 *
 * Two fields are read, in this order:
 *
 *  - `BITCHORD_LYRICS`, this app's own, holding the enhanced A2 form with the word timings intact.
 *  - the container's standard lyrics field, holding plain `[mm:ss.xx]` LRC.
 *
 * The second is what every other player writes, so it is the fallback rather than the exception —
 * which also means a library tagged by something else is read correctly here.
 *
 * Never throws. A file that is not one of the three containers, or is one and has no lyrics in it,
 * is a null, and the caller falls back to the network.
 */
internal object DesktopEmbeddedLyrics {

    /**
     * Most bytes worth pulling to find a tag.
     *
     * A cap rather than a size: the metadata region is small in all three containers, but its
     * length is stated *by the file*, so a corrupt one could claim any number at all.
     */
    private const val MAX_TAG_BYTES = 8 * 1024 * 1024

    /** The raw LRC text inside [path], or null when it has none. */
    fun read(path: Path?): String? {
        if (path == null || !Files.isRegularFile(path)) return null
        sidecar(path)?.let { return it }
        val head = runCatching {
            Files.newInputStream(path).use { stream ->
                val buffer = ByteArray(MAX_TAG_BYTES)
                var total = 0
                while (total < buffer.size) {
                    val read = stream.read(buffer, total, buffer.size - total)
                    if (read <= 0) break
                    total += read
                }
                buffer.copyOf(total)
            }
        }.getOrNull() ?: return null
        return fromBytes(head)
    }

    /**
     * The `lyrics.lrc` written next to an offline package's playlist.
     *
     * Recognised by the playlist rather than the directory: a local `.m3u8` is only ever one of
     * these packages, since nothing else here saves a playlist to disk.
     */
    internal fun sidecar(path: Path): String? {
        if (!path.fileName.toString().endsWith(".m3u8", ignoreCase = true)) return null
        val file = path.parent?.resolve("lyrics.lrc") ?: return null
        if (!Files.isRegularFile(file)) return null
        val size = runCatching { Files.size(file) }.getOrDefault(0L)
        if (size !in 1..MAX_TAG_BYTES.toLong()) return null
        return runCatching { Files.readString(file) }.getOrNull()?.takeIf(String::isNotBlank)
    }

    /**
     * The raw LRC text in [head], whichever of the three containers it is.
     *
     * Split from [read] so the parsing can be checked against bytes a tagger just produced — the
     * round trip is the only thing that proves a reader and a writer agree.
     */
    internal fun fromBytes(head: ByteArray): String? {
        val found = when {
            head.startsWith(FLAC_MAGIC) -> flac(head)
            head.startsWith(MATROSKA_MAGIC) -> matroska(head)
            head.isMp4() -> mp4(head)
            else -> null
        }
        return found?.takeIf(String::isNotBlank)
    }

    // ── MP4 / M4A ────────────────────────────────────────────────────────

    private fun mp4(bytes: ByteArray): String? {
        val moov = topLevelBox(bytes, "moov") ?: return null
        // Exclusive, deliberately: the lyrics are the last item written into `ilst`, so their value
        // ends exactly on `moov`'s own end, and an inclusive bound rejects the one atom sought.
        val end = moov.last + 1
        return ilstText(bytes, moov.first, end, freeform = true)
            ?: ilstText(bytes, moov.first, end, freeform = false)
    }

    private fun topLevelBox(bytes: ByteArray, type: String): IntRange? {
        var pos = 0
        while (pos + 8 <= bytes.size) {
            val declared = readU32(bytes, pos)
            var headerLen = 8
            var size = declared
            if (declared == 1L) {
                if (pos + 16 > bytes.size) return null
                size = readU64(bytes, pos + 8)
                headerLen = 16
            } else if (declared == 0L) {
                size = (bytes.size - pos).toLong()
            }
            if (size < headerLen || size > Int.MAX_VALUE) return null
            val end = (pos + size).toInt().coerceAtMost(bytes.size)
            if (String(bytes, pos + 4, 4, StandardCharsets.ISO_8859_1) == type) return pos until end
            pos += size.toInt()
        }
        return null
    }

    private fun ilstText(bytes: ByteArray, from: Int, endExclusive: Int, freeform: Boolean): String? {
        val marker = if (freeform) WORD_LYRICS_FIELD.toByteArray(StandardCharsets.UTF_8) else LYR_ATOM
        var at = from
        while (true) {
            val found = bytes.indexOf(marker, at, endExclusive) ?: return null
            // The value is the first `data` box after the name in both layouts: a freeform item is
            // mean/name/data, a standard one is type/data.
            val data = bytes.indexOf(DATA_ATOM, found, endExclusive) ?: return null
            dataText(bytes, data, endExclusive)?.let { return it }
            at = found + marker.size
        }
    }

    private fun dataText(bytes: ByteArray, dataAt: Int, endExclusive: Int): String? {
        val start = dataAt - 4
        if (start < 0 || dataAt + 12 > endExclusive) return null
        val size = readU32(bytes, start).toInt()
        if (size <= 16 || start + size > endExclusive) return null
        // Type indicator 1 is UTF-8 text; a cover's 13/14 is the other thing a `data` box holds,
        // and decoding a JPEG as a string is not a lyric.
        if (readU32(bytes, dataAt + 4).toInt() != 1) return null
        return String(bytes, dataAt + 12, start + size - (dataAt + 12), StandardCharsets.UTF_8)
    }

    // ── FLAC ─────────────────────────────────────────────────────────────

    private fun flac(bytes: ByteArray): String? {
        var pos = FLAC_MAGIC.size
        while (pos + 4 <= bytes.size) {
            val flags = bytes[pos].toInt() and 0xFF
            val length = ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
                (bytes[pos + 3].toInt() and 0xFF)
            val start = pos + 4
            if (start + length > bytes.size) return null
            if (flags and 0x7F == FLAC_VORBIS_COMMENT) return vorbisComment(bytes, start, start + length)
            if (flags and 0x80 != 0) return null
            pos = start + length
        }
        return null
    }

    private fun vorbisComment(bytes: ByteArray, start: Int, end: Int): String? {
        var pos = start
        fun u32(): Int? {
            if (pos + 4 > end) return null
            val value = (bytes[pos].toInt() and 0xFF) or
                ((bytes[pos + 1].toInt() and 0xFF) shl 8) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 3].toInt() and 0xFF) shl 24)
            pos += 4
            return value
        }
        val vendor = u32() ?: return null
        pos += vendor
        val count = u32() ?: return null
        var plain: String? = null
        repeat(count.coerceAtMost(4_096)) {
            val length = u32() ?: return plain
            if (length < 0 || pos + length > end) return plain
            val entry = String(bytes, pos, length, StandardCharsets.UTF_8)
            pos += length
            val name = entry.substringBefore('=').uppercase()
            val value = entry.substringAfter('=', "")
            // This app's own field wins outright; the standard one is held in case it is the only
            // one there.
            if (name == WORD_LYRICS_FIELD && value.isNotBlank()) return value
            if (name == "LYRICS" && plain == null && value.isNotBlank()) plain = value
        }
        return plain
    }

    // ── Matroska / WebM ──────────────────────────────────────────────────

    private fun matroska(bytes: ByteArray): String? {
        var plain: String? = null
        for (name in listOf(WORD_LYRICS_FIELD, "LYRICS")) {
            val needle = name.toByteArray(StandardCharsets.US_ASCII)
            var from = 0
            while (true) {
                val at = bytes.indexOf(needle, from, bytes.size) ?: break
                from = at + needle.size
                // The name element's own header sits immediately in front of it: id(2) plus a
                // one-byte length for a name this short.
                if (at < 3 || bytes[at - 3] != ID_TAGNAME[0] || bytes[at - 2] != ID_TAGNAME[1]) continue
                if ((bytes[at - 1].toInt() and 0x7F) != needle.size) continue
                val string = bytes.indexOf(ID_TAGSTRING, from, bytes.size) ?: continue
                val size = readVint(bytes, string + 2) ?: continue
                val valueAt = string + 2 + size.width
                if (size.value <= 0 || valueAt + size.value > bytes.size) continue
                val value = String(bytes, valueAt, size.value.toInt(), StandardCharsets.UTF_8)
                if (value.isBlank()) continue
                if (name == WORD_LYRICS_FIELD) return value
                if (plain == null) plain = value
            }
        }
        return plain
    }

    private class Vint(val value: Long, val width: Int)

    private fun readVint(bytes: ByteArray, offset: Int): Vint? {
        if (offset >= bytes.size) return null
        val first = bytes[offset].toInt() and 0xFF
        if (first == 0) return null
        var width = 1
        var mask = 0x80
        while (first and mask == 0) {
            mask = mask shr 1
            width++
        }
        if (offset + width > bytes.size) return null
        var value = (first and mask.inv() and 0xFF).toLong()
        for (i in 1 until width) value = (value shl 8) or (bytes[offset + i].toLong() and 0xFF)
        return Vint(value, width)
    }

    // ── Bytes ────────────────────────────────────────────────────────────

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private fun ByteArray.isMp4(): Boolean =
        size > 12 && this[4] == 'f'.code.toByte() && this[5] == 't'.code.toByte() &&
            this[6] == 'y'.code.toByte() && this[7] == 'p'.code.toByte()

    private fun ByteArray.indexOf(needle: ByteArray, from: Int, until: Int): Int? {
        if (needle.isEmpty()) return null
        val last = minOf(until, size) - needle.size
        var i = from.coerceAtLeast(0)
        outer@ while (i <= last) {
            for (j in needle.indices) {
                if (this[i + j] != needle[j]) {
                    i++
                    continue@outer
                }
            }
            return i
        }
        return null
    }

    private fun readU32(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFF) shl 24) or ((b[off + 1].toLong() and 0xFF) shl 16) or
            ((b[off + 2].toLong() and 0xFF) shl 8) or (b[off + 3].toLong() and 0xFF)

    private fun readU64(b: ByteArray, off: Int): Long {
        var value = 0L
        for (i in 0 until 8) value = (value shl 8) or (b[off + i].toLong() and 0xFF)
        return value
    }

    /** This app's own field, holding the word-timed form other players have no place for. */
    internal const val WORD_LYRICS_FIELD = "BITCHORD_LYRICS"

    private const val FLAC_VORBIS_COMMENT = 4
    private val FLAC_MAGIC = "fLaC".toByteArray(StandardCharsets.US_ASCII)
    private val MATROSKA_MAGIC = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
    private val DATA_ATOM = "data".toByteArray(StandardCharsets.ISO_8859_1)
    private val LYR_ATOM = byteArrayOf(0xA9.toByte()) + "lyr".toByteArray(StandardCharsets.ISO_8859_1)
    private val ID_TAGNAME = byteArrayOf(0x45, 0xA3.toByte())
    private val ID_TAGSTRING = byteArrayOf(0x44, 0x87.toByte())
}
