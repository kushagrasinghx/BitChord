package com.music.bitchord.download

import com.music.bitchord.data.lyrics.WORD_LYRICS_FIELD

import java.io.ByteArrayOutputStream

/**
 * Rewrites a downloaded FLAC's metadata blocks to carry title, artist, album,
 * lyrics and cover art.
 *
 * The simplest of the three taggers, and the reason is worth stating because it
 * is the opposite of what the other two are shaped by. A FLAC is `fLaC`, then a
 * chain of length-prefixed metadata blocks, then the audio frames — and nothing
 * in the format addresses anything by an absolute file offset. `SEEKTABLE`
 * *looks* like the exception, but its offsets are measured from the first byte
 * of the first frame header rather than from the start of the file, so growing
 * the metadata region moves the frames without invalidating a single number in
 * it. No `stco`/`co64` cascade to patch ([Mp4Tagger]), no `Segment` size to
 * widen in place without changing its byte width ([WebmTagger]): the whole job
 * here is to emit a new block chain and copy the frames through untouched.
 *
 * What comes out is the magic, `STREAMINFO`, every other block the file already
 * had, then a fresh `VORBIS_COMMENT` and `PICTURE`. The old copies of those two
 * are dropped rather than added to — a second `VORBIS_COMMENT` is illegal, and
 * two front covers is a coin toss over which one a player shows. `PADDING` is
 * dropped as well, which is what it is there for: it exists to be spent on
 * exactly this.
 *
 * Anything that doesn't fit the shape above — a file that doesn't open with
 * `fLaC`, a block that claims more bytes than the file has, a first block that
 * isn't `STREAMINFO` — comes back as the input, unchanged and by reference, so
 * [MediaTagger] leaves the downloaded file alone.
 */
object FlacTagger {

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
        val done = header(bytes, title, artist, album, lyrics, cover, coverMime, wordLyrics)
            ?: return@runCatching bytes
        val out = ByteArrayOutputStream(done.metadata.size + (bytes.size - done.audioStart))
        out.write(done.metadata)
        // The frames, verbatim.
        out.write(bytes, done.audioStart, bytes.size - done.audioStart)
        out.toByteArray()
    }.getOrDefault(bytes)

    /**
     * The finished metadata region to put in front of this file's frames, and
     * where those frames begin in the original.
     *
     * The whole reason this is separate from [tag]: everything a FLAC rewrite
     * has to *think* about lives in the first few hundred kilobytes of the file,
     * and everything after that is a byte-for-byte copy. Handing a caller those
     * two answers lets it stream the copy rather than hold three versions of a
     * 40MB track in memory to perform it — see [MediaTagger.tagFlacStreaming].
     */
    class Rewrite(val metadata: ByteArray, val audioStart: Int)

    /**
     * @param head the start of the file. It only has to be long enough to
     *   contain the whole metadata block chain; null comes back if it isn't, so
     *   a caller that guessed a prefix too short can tell that apart from a file
     *   this doesn't recognise only by trying again with more of it.
     * @return null when there is nothing to write, or nothing here recognises
     *   the shape — in both cases the file is to be left exactly as it is.
     */
    fun header(
        head: ByteArray,
        title: String,
        artist: String,
        album: String?,
        lyrics: String?,
        cover: ByteArray?,
        coverMime: String,
        wordLyrics: String? = null,
    ): Rewrite? {
        if (!head.regionMatches(0, MAGIC)) return null

        // The block chain. Each header is one byte of flags — bit 7 marks the
        // last block, bits 0-6 are the type — and three big-endian bytes of
        // payload length.
        val blocks = mutableListOf<Block>()
        var offset = MAGIC.size
        while (true) {
            if (offset + BLOCK_HEADER > head.size) return null
            val flags = head[offset].toInt() and 0xFF
            val length = ((head[offset + 1].toInt() and 0xFF) shl 16) or
                ((head[offset + 2].toInt() and 0xFF) shl 8) or
                (head[offset + 3].toInt() and 0xFF)
            val start = offset + BLOCK_HEADER
            if (start + length > head.size) return null
            blocks += Block(flags and 0x7F, start, length)
            offset = start + length
            if (flags and 0x80 != 0) break
        }
        // Required to be first by the format itself; a file that doesn't have it
        // there is not one to guess at.
        if (blocks.firstOrNull()?.type != TYPE_STREAMINFO) return null

        val additions = listOfNotNull(
            vorbisComment(title, artist, album, lyrics, wordLyrics)?.let { TYPE_VORBIS_COMMENT to it },
            cover?.takeIf { it.isNotEmpty() }?.let { TYPE_PICTURE to picture(it, coverMime) },
        )
            // A payload past what three bytes of length can describe costs that
            // one block and nothing else. Only a cover could realistically reach
            // 16MB — `LyricsTag` caps what it hands over at a small fraction of
            // it — and losing the cover is a better outcome than losing the tags.
            .filter { it.second.size <= MAX_BLOCK_BYTES }
        if (additions.isEmpty()) return null

        val chain = blocks.filterNot { it.type in REPLACED }
            .map { it.type to head.copyOfRange(it.start, it.start + it.length) } + additions

        val out = ByteArrayOutputStream(offset + additions.sumOf { it.second.size } + 64)
        out.write(MAGIC)
        chain.forEachIndexed { index, (type, payload) ->
            out.write(if (index == chain.lastIndex) type or 0x80 else type)
            out.write((payload.size ushr 16) and 0xFF)
            out.write((payload.size ushr 8) and 0xFF)
            out.write(payload.size and 0xFF)
            out.write(payload)
        }
        // [offset] is one past the last metadata block, which is where the
        // frames start.
        return Rewrite(out.toByteArray(), offset)
    }

    /**
     * A `VORBIS_COMMENT` payload, or null when there is nothing to say.
     *
     * Every length in here is **little-endian**, which is the one surprise in an
     * otherwise big-endian format — the block reuses Ogg Vorbis' comment layout
     * wholesale, and that layout is little-endian.
     *
     * It does *not* reuse the trailing framing bit. That byte belongs to the
     * Vorbis packet, not to the comment structure, and writing one here appends
     * a stray byte to the block that strict parsers reject. It is the classic
     * way this gets written wrong, so: no framing bit.
     *
     * [lyrics] goes in as `LYRICS`, which is where every reader that shows
     * lyrics for a FLAC looks. Nothing about the layout minds it being long or
     * containing newlines: a field is a length-prefixed run of UTF-8 bytes, and
     * only the *name* before the `=` is constrained.
     */
    private fun vorbisComment(
        title: String,
        artist: String,
        album: String?,
        lyrics: String?,
        wordLyrics: String?,
    ): ByteArray? {
        val fields = buildList {
            if (title.isNotBlank()) add("TITLE=$title")
            if (artist.isNotBlank()) add("ARTIST=$artist")
            if (!album.isNullOrBlank()) add("ALBUM=$album")
            if (!lyrics.isNullOrBlank()) add("LYRICS=$lyrics")
            // Beside `LYRICS`, never instead of it: an unknown name is skipped
            // by every reader, so the portable field stays exactly as it was.
            if (!wordLyrics.isNullOrBlank()) add("$WORD_LYRICS_FIELD=$wordLyrics")
        }
        if (fields.isEmpty()) return null

        val out = ByteArrayOutputStream()
        val vendor = VENDOR.toByteArray(Charsets.UTF_8)
        out.writeLe(vendor.size)
        out.write(vendor)
        out.writeLe(fields.size)
        for (field in fields) {
            val encoded = field.toByteArray(Charsets.UTF_8)
            out.writeLe(encoded.size)
            out.write(encoded)
        }
        return out.toByteArray()
    }

    /**
     * A `PICTURE` payload holding [cover] as the front cover.
     *
     * Big-endian throughout, unlike the comment block above. The dimensions and
     * colour fields are all written as zero, which the format defines as
     * "unstated" rather than as a claim about a 0x0 image — decoding the JPEG
     * here to fill them in would buy nothing, since every player that draws the
     * image has to decode it anyway.
     */
    private fun picture(cover: ByteArray, mime: String): ByteArray {
        val out = ByteArrayOutputStream(cover.size + 64)
        val mimeBytes = mime.toByteArray(Charsets.US_ASCII)
        out.writeBe(PICTURE_FRONT_COVER)
        out.writeBe(mimeBytes.size)
        out.write(mimeBytes)
        out.writeBe(0) // description length; the picture type already says it
        out.writeBe(0) // width
        out.writeBe(0) // height
        out.writeBe(0) // colour depth
        out.writeBe(0) // colours used — zero for anything that isn't paletted
        out.writeBe(cover.size)
        out.write(cover)
        return out.toByteArray()
    }

    private class Block(val type: Int, val start: Int, val length: Int)

    private fun ByteArrayOutputStream.writeLe(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeBe(value: Int) {
        write((value ushr 24) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun ByteArray.regionMatches(offset: Int, other: ByteArray): Boolean {
        if (offset < 0 || offset + other.size > size) return false
        for (i in other.indices) if (this[offset + i] != other[i]) return false
        return true
    }

    private val MAGIC = "fLaC".toByteArray(Charsets.US_ASCII)

    /** One byte of flags plus three of length, before every block's payload. */
    private const val BLOCK_HEADER = 4

    private const val TYPE_STREAMINFO = 0
    private const val TYPE_PADDING = 1
    private const val TYPE_VORBIS_COMMENT = 4
    private const val TYPE_PICTURE = 6

    /** Blocks this rewrites or spends, rather than carrying across. */
    private val REPLACED = setOf(TYPE_PADDING, TYPE_VORBIS_COMMENT, TYPE_PICTURE)

    /** The most a three-byte length field can describe. */
    private const val MAX_BLOCK_BYTES = (1 shl 24) - 1

    /** The `PICTURE` type for a front cover, which is the only one written here. */
    private const val PICTURE_FRONT_COVER = 3

    private const val VENDOR = "BitChord"
}
