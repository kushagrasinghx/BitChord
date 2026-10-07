package com.music.bitchord.download

import com.music.bitchord.data.lyrics.WORD_LYRICS_FIELD

/**
 * Writes iTunes-style metadata atoms — title, artist, album, lyrics, cover —
 * into an already-downloaded M4A/MP4 file, in place.
 *
 * There is no public Android API for this: [android.media.MediaMuxer] can
 * copy tracks into a fresh MP4 but has no way to declare a title or embed
 * artwork, and every general-purpose Java tagging library drags in either
 * native code or `javax.imageio` (absent on Android, and the exact crash
 * several other music apps hit shipping the unmodified desktop jaudiotagger).
 * So this reads and rewrites the handful of boxes involved directly.
 *
 * The whole thing is a single insertion: a fresh `udta/meta/ilst` atom is
 * appended as the last child of `moov`. Growing `moov` shifts every byte
 * after it, which is only a problem because `stco`/`co64` (the sample tables
 * under `moov/trak/mdia/minf/stbl`) record *absolute* file offsets into
 * `mdat` — so every entry at or past the insertion point is bumped by the
 * inserted length. Nothing else in the file addresses itself by absolute
 * offset, so that one adjustment is sufficient regardless of whether `mdat`
 * sits before or after `moov`.
 *
 * Any layout this doesn't recognise — no `moov`, a box that doesn't fit its
 * parent — falls through to returning the input unchanged rather than
 * guessing: a download that plays untagged is a smaller loss than one a
 * bad rewrite has corrupted.
 */
object Mp4Tagger {

    private data class BoxRef(
        val offset: Int,
        val headerLen: Int,
        val size: Int,
        /** The raw 32-bit size field, before size==0/1 are resolved — 0 means "to end of parent", which must be left alone rather than replaced with a real number. */
        val rawSize32: Long,
        val type: String,
    ) {
        val contentOffset get() = offset + headerLen
        val end get() = offset + size
    }

    /** Box types whose payload is itself a run of child boxes, on the path down to `stco`/`co64`. */
    private val CONTAINERS = setOf("moov", "trak", "mdia", "minf", "stbl")

    fun tag(
        bytes: ByteArray,
        title: String,
        artist: String,
        album: String?,
        lyrics: String?,
        cover: ByteArray?,
        coverIsPng: Boolean,
        /** The A2 form, kept beside [lyrics] rather than instead of it — see [freeformItem]. */
        wordLyrics: String? = null,
    ): ByteArray {
        val items = mutableListOf<ByteArray>()
        // © is iTunes's own "copyright" prefix for the four text atoms
        // below — not a copyright mark here, just the byte their readers key on.
        if (title.isNotBlank()) items += textItem("©nam", title)
        if (artist.isNotBlank()) items += textItem("©ART", artist)
        if (!album.isNullOrBlank()) items += textItem("©alb", album)
        // `©lyr` is a UTF-8 text atom like the three above, with no length limit
        // and no objection to newlines, so LRC goes in as-is. There is a
        // separate `Sync Lyrics`/`sylt`-style representation in some tools;
        // nothing writes it, because `©lyr` holding LRC is what the players
        // that show synced lyrics for an M4A actually read.
        if (!lyrics.isNullOrBlank()) items += textItem("©lyr", lyrics)
        if (!wordLyrics.isNullOrBlank()) items += freeformItem(WORD_LYRICS_FIELD, wordLyrics)
        if (cover != null && cover.isNotEmpty()) items += coverItem(cover, coverIsPng)
        if (items.isEmpty()) return bytes

        val moov = runCatching {
            parseBoxes(bytes, 0, bytes.size).firstOrNull { it.type == "moov" }
        }.getOrNull() ?: return bytes

        return runCatching {
            insert(bytes, moov, udtaAtom(metaAtom(ilstAtom(items))))
        }.getOrDefault(bytes)
    }

    private fun insert(bytes: ByteArray, moov: BoxRef, udta: ByteArray): ByteArray {
        val insertAt = moov.end
        val delta = udta.size

        val prefix = bytes.copyOf(insertAt)
        // rawSize32 == 0 means "this box runs to the end of its parent" — still
        // true after the insertion, since nothing follows moov but this new
        // atom, so the field is left as-is rather than given a concrete value.
        if (moov.rawSize32 != 0L) {
            if (moov.headerLen == 16) {
                writeU64(prefix, moov.offset + 8, moov.size.toLong() + delta)
            } else {
                writeU32(prefix, moov.offset, moov.size.toLong() + delta)
            }
        }

        val offsetBoxes = mutableListOf<BoxRef>()
        collectOffsetBoxes(bytes, moov, offsetBoxes)
        offsetBoxes.forEach { box ->
            when (box.type) {
                "stco" -> patchStco(prefix, box, insertAt, delta)
                "co64" -> patchCo64(prefix, box, insertAt, delta)
            }
        }

        val suffix = bytes.copyOfRange(insertAt, bytes.size)
        return prefix + udta + suffix
    }

    private fun collectOffsetBoxes(bytes: ByteArray, box: BoxRef, out: MutableList<BoxRef>) {
        if (box.type == "stco" || box.type == "co64") {
            out += box
            return
        }
        if (box.type in CONTAINERS) {
            parseBoxes(bytes, box.contentOffset, box.end).forEach { collectOffsetBoxes(bytes, it, out) }
        }
    }

    /** `stco`: FullBox header, an entry count, then that many 32-bit offsets. */
    private fun patchStco(bytes: ByteArray, box: BoxRef, insertAt: Int, delta: Int) {
        val base = box.contentOffset + 4
        val count = readU32(bytes, base).toInt()
        var p = base + 4
        repeat(count) {
            val off = readU32(bytes, p)
            if (off >= insertAt) writeU32(bytes, p, off + delta)
            p += 4
        }
    }

    /** `co64`: the same shape as [patchStco], with 64-bit offsets. */
    private fun patchCo64(bytes: ByteArray, box: BoxRef, insertAt: Int, delta: Int) {
        val base = box.contentOffset + 4
        val count = readU32(bytes, base).toInt()
        var p = base + 4
        repeat(count) {
            val off = readU64(bytes, p)
            if (off >= insertAt) writeU64(bytes, p, off + delta)
            p += 8
        }
    }

    private fun parseBoxes(bytes: ByteArray, start: Int, end: Int): List<BoxRef> {
        val out = mutableListOf<BoxRef>()
        var pos = start
        while (pos + 8 <= end) {
            val size32 = readU32(bytes, pos)
            val type = String(bytes, pos + 4, 4, Charsets.ISO_8859_1)
            var headerLen = 8
            var size = size32
            if (size32 == 1L) {
                if (pos + 16 > end) break
                size = readU64(bytes, pos + 8)
                headerLen = 16
            } else if (size32 == 0L) {
                size = (end - pos).toLong()
            }
            if (size < headerLen || pos + size > end || size > Int.MAX_VALUE) break
            out += BoxRef(pos, headerLen, size.toInt(), size32, type)
            pos += size.toInt()
        }
        return out
    }

    private fun readU32(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFF) shl 24) or ((b[off + 1].toLong() and 0xFF) shl 16) or
            ((b[off + 2].toLong() and 0xFF) shl 8) or (b[off + 3].toLong() and 0xFF)

    private fun readU64(b: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (b[off + i].toLong() and 0xFF)
        return v
    }

    private fun writeU32(b: ByteArray, off: Int, value: Long) {
        b[off] = ((value shr 24) and 0xFF).toByte()
        b[off + 1] = ((value shr 16) and 0xFF).toByte()
        b[off + 2] = ((value shr 8) and 0xFF).toByte()
        b[off + 3] = (value and 0xFF).toByte()
    }

    private fun writeU64(b: ByteArray, off: Int, value: Long) {
        for (i in 0 until 8) b[off + i] = ((value shr (8 * (7 - i))) and 0xFF).toByte()
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        val out = ByteArray(8 + payload.size)
        writeU32(out, 0, (8 + payload.size).toLong())
        // ISO-8859-1, not ASCII: the iTunes item names below carry the 0xA9
        // "copyright" byte, which plain ASCII can't encode and would replace
        // with '?' — corrupting the very atom type a player looks up by.
        type.toByteArray(Charsets.ISO_8859_1).copyInto(out, 4)
        payload.copyInto(out, 8)
        return out
    }

    /** iTunes's `data` atom: version(0) + a type indicator packed into 3 flag bytes, then the value. */
    private fun dataAtom(typeIndicator: Int, payload: ByteArray): ByteArray {
        val body = ByteArray(8 + payload.size)
        writeU32(body, 0, typeIndicator.toLong())
        payload.copyInto(body, 8)
        return box("data", body)
    }

    /** Type indicator 1 = UTF-8 text. */
    private fun textItem(fourCc: String, text: String): ByteArray =
        box(fourCc, dataAtom(1, text.toByteArray(Charsets.UTF_8)))

    /** Type indicator 13 = JPEG, 14 = PNG. */
    private fun coverItem(image: ByteArray, isPng: Boolean): ByteArray =
        box("covr", dataAtom(if (isPng) 14 else 13, image))

    /**
     * A `----` item: iTunes' escape hatch for a field it has no four-byte code
     * for, named by a `mean`/`name` pair in front of the value.
     *
     * This is where the word timings go, and the reason they go somewhere of
     * their own is the one [LrcWriter] documents: `©lyr` is read by every other
     * player, and a reader without the A2 extension shows `<00:01.00>` rather
     * than skipping it. A player that doesn't know this item ignores it whole,
     * so the standard field stays clean and the timings still survive in the
     * file — see [EmbeddedLyrics][com.music.bitchord.data.lyrics.EmbeddedLyrics],
     * which reads them back.
     */
    private fun freeformItem(name: String, text: String): ByteArray {
        // Both are FullBoxes: four bytes of version/flags, then the string.
        val mean = box("mean", ByteArray(4) + MEAN.toByteArray(Charsets.UTF_8))
        val label = box("name", ByteArray(4) + name.toByteArray(Charsets.UTF_8))
        return box("----", mean + label + dataAtom(1, text.toByteArray(Charsets.UTF_8)))
    }

    /** The reverse-DNS owner of [freeformItem], which is what keeps the name ours. */
    private const val MEAN = "com.music.bitchord"


    /** A minimal handler box declaring this `meta` as iTunes-style metadata. */
    private fun hdlrAtom(): ByteArray {
        // version/flags(4) + pre_defined(4) + handler_type(4) + reserved(12) + name(1, empty cstring)
        val body = ByteArray(25)
        "mdir".toByteArray(Charsets.ISO_8859_1).copyInto(body, 8)
        return box("hdlr", body)
    }

    private fun ilstAtom(items: List<ByteArray>): ByteArray {
        val payload = ByteArray(items.sumOf { it.size })
        var p = 0
        items.forEach { it.copyInto(payload, p); p += it.size }
        return box("ilst", payload)
    }

    private fun metaAtom(ilst: ByteArray): ByteArray {
        val hdlr = hdlrAtom()
        val payload = ByteArray(4 + hdlr.size + ilst.size)
        hdlr.copyInto(payload, 4)
        ilst.copyInto(payload, 4 + hdlr.size)
        return box("meta", payload)
    }

    private fun udtaAtom(meta: ByteArray): ByteArray = box("udta", meta)
}
