package com.music.bitchord

import com.music.bitchord.data.lyrics.EmbeddedLyrics
import com.music.bitchord.data.lyrics.LrcLib
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.data.lyrics.toEnhancedLrc
import com.music.bitchord.data.lyrics.toLrc
import com.music.bitchord.download.FlacTagger
import com.music.bitchord.download.Mp4Tagger
import com.music.bitchord.download.WebmTagger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The reader and the three taggers have to agree, and nothing else checks that
 * they do: a download writes the file on a device and the player reads it back
 * on another run, so a disagreement between the two shows up as a downloaded
 * song that silently has no lyrics — which is exactly the bug this pair was
 * written for.
 *
 * Each test writes with the real tagger and reads with the real reader, so a
 * change to either side that breaks the pairing fails here rather than on a
 * phone. Placeholder text throughout; no real lyric appears in this file.
 */
class EmbeddedLyricsTest {

    private val lrc = "[00:01.00]first line\n[00:05.00]second line"

    @Test
    fun `an m4a written by the tagger reads back`() {
        val tagged = Mp4Tagger.tag(
            bytes = minimalMp4(),
            title = "t",
            artist = "a",
            album = null,
            lyrics = lrc,
            cover = null,
            coverIsPng = false,
        )
        assertEquals(lrc, EmbeddedLyrics.fromBytes(tagged))
    }

    @Test
    fun `a flac written by the tagger reads back`() {
        val tagged = FlacTagger.tag(
            bytes = minimalFlac(),
            title = "t",
            artist = "a",
            album = null,
            lyrics = lrc,
            cover = null,
            coverMime = "image/jpeg",
        )
        assertEquals(lrc, EmbeddedLyrics.fromBytes(tagged))
    }

    @Test
    fun `a webm written by the tagger reads back`() {
        val tagged = WebmTagger.tag(
            bytes = minimalWebm(),
            title = "t",
            artist = "a",
            album = null,
            lyrics = lrc,
            cover = null,
            coverMime = "image/jpeg",
        )
        assertEquals(lrc, EmbeddedLyrics.fromBytes(tagged))
    }

    /**
     * A cover is a `data` box too, and it sits in the same `ilst` as the lyrics.
     * Reading the first one that turns up rather than the lyrics' own would
     * hand a JPEG back as a string.
     */
    @Test
    fun `a cover alongside the lyrics is not mistaken for them`() {
        val tagged = Mp4Tagger.tag(
            bytes = minimalMp4(),
            title = "t",
            artist = "a",
            album = null,
            lyrics = lrc,
            cover = ByteArray(64) { 0x7F },
            coverIsPng = false,
        )
        assertEquals(lrc, EmbeddedLyrics.fromBytes(tagged))
    }

    /**
     * The whole point of the second field: a word-synced download has to come
     * back word-synced, or a downloaded song silently drops to whole-line
     * highlighting while a streamed one keeps its syllables.
     */
    @Test
    fun `word timings survive the write and the read, in all three containers`() {
        val words = listOf(
            LyricLine(
                timeMs = 1_000L,
                text = "two words",
                words = listOf(
                    LyricWord(startMs = 1_000L, endMs = 1_400L, text = "two"),
                    LyricWord(startMs = 1_400L, endMs = 2_000L, text = "words"),
                ),
            ),
        )
        val plain = words.toLrc()
        val enhanced = words.toEnhancedLrc()

        val written = listOf(
            Mp4Tagger.tag(minimalMp4(), "t", "a", null, plain, null, false, enhanced),
            FlacTagger.tag(minimalFlac(), "t", "a", null, plain, null, "image/jpeg", enhanced),
            WebmTagger.tag(minimalWebm(), "t", "a", null, plain, null, "image/jpeg", enhanced),
        )
        for (bytes in written) {
            // The word-timed field wins over the plain one sitting beside it.
            val read = LrcLib.parseLrc(requireNotNull(EmbeddedLyrics.fromBytes(bytes)))
            assertEquals(1, read.size)
            assertEquals("two words", read[0].text)
            assertEquals(listOf("two", "words"), read[0].words.map { it.text })
            assertEquals(listOf(1_000L, 1_400L), read[0].words.map { it.startMs })
            assertEquals(2_000L, read[0].endMs)
        }
    }

    /**
     * The standard field must stay plain whatever else is written beside it —
     * a reader without A2 shows `<00:01.00>` rather than skipping it, which is
     * the reason there are two fields at all.
     */
    @Test
    fun `the portable field never carries word stamps`() {
        val words = listOf(
            LyricLine(
                timeMs = 1_000L,
                text = "two words",
                words = listOf(
                    LyricWord(1_000L, 1_400L, "two"),
                    LyricWord(1_400L, 2_000L, "words"),
                ),
            ),
        )
        assertEquals("[00:01.00]two words", words.toLrc())
    }

    @Test
    fun `a file with no lyrics at all reads back as nothing`() {
        val tagged = Mp4Tagger.tag(
            bytes = minimalMp4(),
            title = "t",
            artist = "a",
            album = null,
            lyrics = null,
            cover = null,
            coverIsPng = false,
        )
        assertNull(EmbeddedLyrics.fromBytes(tagged))
    }

    @Test
    fun `something that is none of the three containers is not guessed at`() {
        assertNull(EmbeddedLyrics.fromBytes(ByteArray(512) { it.toByte() }))
    }

    // ---- Minimal containers, just enough shape for each tagger to accept ----

    /** `ftyp` then an empty `moov`, which is all [Mp4Tagger] looks for. */
    private fun minimalMp4(): ByteArray = box("ftyp", "isom".toByteArray()) + box("moov", ByteArray(0))

    private fun box(type: String, payload: ByteArray): ByteArray {
        val size = 8 + payload.size
        return byteArrayOf(
            (size ushr 24).toByte(), (size ushr 16).toByte(),
            (size ushr 8).toByte(), size.toByte(),
        ) + type.toByteArray(Charsets.ISO_8859_1) + payload
    }

    /** `fLaC`, a last-block STREAMINFO, then a byte standing in for the frames. */
    private fun minimalFlac(): ByteArray {
        val streamInfo = ByteArray(34)
        return "fLaC".toByteArray() +
            byteArrayOf(0x80.toByte(), 0, 0, streamInfo.size.toByte()) + streamInfo +
            byteArrayOf(0xFF.toByte())
    }

    /** An EBML header and a Segment whose declared size covers the rest of the file. */
    private fun minimalWebm(): ByteArray {
        val header = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()) +
            byteArrayOf(0x84.toByte()) + ByteArray(4)
        val body = ByteArray(8)
        val segment = byteArrayOf(0x18, 0x53, 0x80.toByte(), 0x67) +
            // An 8-byte length so there is room to grow it when tags are appended.
            byteArrayOf(0x01, 0, 0, 0, 0, 0, 0, body.size.toByte()) + body
        return header + segment
    }
}
