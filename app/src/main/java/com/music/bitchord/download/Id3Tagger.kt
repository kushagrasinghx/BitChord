package com.music.bitchord.download

import java.io.ByteArrayOutputStream

/**
 * Writes standard ID3v2.3 metadata frames into an MP3 file.
 *
 * Designed to mirror [Mp4Tagger], [FlacTagger], and [WebmTagger] with zero
 * external dependencies and no reliance on desktop Java classes (such as
 * `javax.imageio`) that cause runtime crashes on Android.
 *
 * Supported frames:
 *  - TIT2: Title
 *  - TPE1: Lead Artist / Performer
 *  - TALB: Album
 *  - TPE2: Album Artist
 *  - TRCK: Track Number (e.g. "3" or "3/12")
 *  - TPOS: Disc Number
 *  - TYER: Release Year / Date
 *  - USLT: Unsynchronised lyric/text transcription
 *  - COMM: Description / Comments
 *  - APIC: Attached Picture (Cover (front), exact original image bytes)
 */
object Id3Tagger {

    private val ID3_MAGIC = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte())

    fun tag(
        bytes: ByteArray,
        title: String,
        artist: String,
        album: String? = null,
        lyrics: String? = null,
        cover: ByteArray? = null,
        coverMime: String = "image/jpeg",
        albumArtist: String? = null,
        trackNumber: Int? = null,
        discNumber: Int? = null,
        year: String? = null,
        comment: String? = null,
    ): ByteArray = runCatching {
        val audioOffset = skipExistingId3(bytes)
        val id3HeaderAndFrames = buildId3Tag(
            title = title,
            artist = artist,
            album = album,
            lyrics = lyrics,
            cover = cover,
            coverMime = coverMime,
            albumArtist = albumArtist,
            trackNumber = trackNumber,
            discNumber = discNumber,
            year = year,
            comment = comment,
        )

        val out = ByteArrayOutputStream(id3HeaderAndFrames.size + (bytes.size - audioOffset))
        out.write(id3HeaderAndFrames)
        out.write(bytes, audioOffset, bytes.size - audioOffset)
        out.toByteArray()
    }.getOrDefault(bytes)

    /**
     * Finds where the existing ID3v2 tag ends (if any) and audio data begins.
     */
    internal fun skipExistingId3(bytes: ByteArray): Int {
        if (bytes.size < 10) return 0
        if (bytes[0] != ID3_MAGIC[0] || bytes[1] != ID3_MAGIC[1] || bytes[2] != ID3_MAGIC[2]) {
            return 0
        }
        val size = (bytes[6].toInt() and 0x7F shl 21) or
            (bytes[7].toInt() and 0x7F shl 14) or
            (bytes[8].toInt() and 0x7F shl 7) or
            (bytes[9].toInt() and 0x7F)
        val totalTagSize = 10 + size
        return if (totalTagSize <= bytes.size) totalTagSize else 0
    }

    private fun buildId3Tag(
        title: String,
        artist: String,
        album: String?,
        lyrics: String?,
        cover: ByteArray?,
        coverMime: String,
        albumArtist: String?,
        trackNumber: Int?,
        discNumber: Int?,
        year: String?,
        comment: String?,
    ): ByteArray {
        val framesOut = ByteArrayOutputStream()

        fun writeTextFrame(id: String, text: String?) {
            if (text.isNullOrBlank()) return
            framesOut.write(makeTextFrame(id, text.trim()))
        }

        writeTextFrame("TIT2", title)
        writeTextFrame("TPE1", artist)
        writeTextFrame("TALB", album)
        writeTextFrame("TPE2", albumArtist ?: artist)
        trackNumber?.let { writeTextFrame("TRCK", it.toString()) }
        discNumber?.let { writeTextFrame("TPOS", it.toString()) }
        writeTextFrame("TYER", year)
        writeTextFrame("COMM", comment)

        if (!lyrics.isNullOrBlank()) {
            framesOut.write(makeLyricsFrame(lyrics))
        }

        if (cover != null && cover.isNotEmpty()) {
            framesOut.write(makeApicFrame(cover, coverMime))
        }

        val frameBytes = framesOut.toByteArray()
        val header = ByteArray(10)
        header[0] = 'I'.code.toByte()
        header[1] = 'D'.code.toByte()
        header[2] = '3'.code.toByte()
        header[3] = 3 // ID3v2.3
        header[4] = 0 // Revision 0
        header[5] = 0 // Flags

        // Size as 4 syncsafe bytes (excluding 10-byte header)
        val tagSize = frameBytes.size
        header[6] = ((tagSize ushr 21) and 0x7F).toByte()
        header[7] = ((tagSize ushr 14) and 0x7F).toByte()
        header[8] = ((tagSize ushr 7) and 0x7F).toByte()
        header[9] = (tagSize and 0x7F).toByte()

        return header + frameBytes
    }

    private fun makeTextFrame(frameId: String, text: String): ByteArray {
        val (encodingByte, encodedText) = encodeText(text)
        val payload = byteArrayOf(encodingByte) + encodedText
        return makeFrame(frameId, payload)
    }

    private fun makeLyricsFrame(lyrics: String): ByteArray {
        val (encodingByte, encodedLyrics) = encodeText(lyrics)
        // USLT: encoding(1) + language(3: "eng") + content descriptor(null-term) + lyrics
        val lang = "eng".toByteArray(Charsets.ISO_8859_1)
        val term = if (encodingByte == 0x01.toByte()) byteArrayOf(0, 0) else byteArrayOf(0)
        val payload = byteArrayOf(encodingByte) + lang + term + encodedLyrics
        return makeFrame("USLT", payload)
    }

    private fun makeApicFrame(imageBytes: ByteArray, mimeType: String): ByteArray {
        val cleanMime = if (mimeType.isBlank()) "image/jpeg" else mimeType
        val mimeBytes = cleanMime.toByteArray(Charsets.ISO_8859_1)
        val payload = ByteArrayOutputStream()
        payload.write(0x00) // ISO-8859-1 for mime/description
        payload.write(mimeBytes)
        payload.write(0x00) // Null terminator for mime
        payload.write(0x03) // Picture type: Cover (front)
        payload.write(0x00) // Empty description + null terminator
        payload.write(imageBytes) // Raw picture bytes
        return makeFrame("APIC", payload.toByteArray())
    }

    private fun makeFrame(frameId: String, payload: ByteArray): ByteArray {
        val frame = ByteArray(10 + payload.size)
        val idBytes = frameId.toByteArray(Charsets.ISO_8859_1)
        System.arraycopy(idBytes, 0, frame, 0, 4)
        val size = payload.size
        // In ID3v2.3, frame size is a 32-bit big-endian integer
        frame[4] = ((size ushr 24) and 0xFF).toByte()
        frame[5] = ((size ushr 16) and 0xFF).toByte()
        frame[6] = ((size ushr 8) and 0xFF).toByte()
        frame[7] = (size and 0xFF).toByte()
        frame[8] = 0 // Flags
        frame[9] = 0 // Flags
        System.arraycopy(payload, 0, frame, 10, payload.size)
        return frame
    }

    /**
     * Determines whether [text] can be encoded in ISO-8859-1 or requires UTF-16LE with BOM.
     */
    private fun encodeText(text: String): Pair<Byte, ByteArray> {
        val isAsciiOrLatin1 = text.all { it.code in 0..255 }
        return if (isAsciiOrLatin1) {
            0x00.toByte() to text.toByteArray(Charsets.ISO_8859_1)
        } else {
            // UTF-16 with BOM (0xFF, 0xFE for LE)
            val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
            0x01.toByte() to (bom + text.toByteArray(Charsets.UTF_16LE))
        }
    }
}
