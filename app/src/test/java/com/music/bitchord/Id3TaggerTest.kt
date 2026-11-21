package com.music.bitchord

import com.music.bitchord.download.Id3Tagger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class Id3TaggerTest {

    private fun ByteArray.indexOfBytes(needle: ByteArray, from: Int = 0): Int {
        outer@ for (i in from..size - needle.size) {
            for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    @Test
    fun `id3 tagging prepends valid ID3v2 header and frames`() {
        val dummyAudio = ByteArray(100) { (it + 1).toByte() }
        val cover = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)

        val tagged = Id3Tagger.tag(
            bytes = dummyAudio,
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera",
            lyrics = "Is this the real life?",
            cover = cover,
            coverMime = "image/jpeg",
            albumArtist = "Queen",
            trackNumber = 4,
            year = "1975",
        )

        assertNotSame(dummyAudio, tagged)
        assertTrue("Tagged file must be larger than original audio", tagged.size > dummyAudio.size)

        // Magic "ID3"
        assertEquals('I'.code.toByte(), tagged[0])
        assertEquals('D'.code.toByte(), tagged[1])
        assertEquals('3'.code.toByte(), tagged[2])
        assertEquals(3.toByte(), tagged[3]) // v2.3

        // Check frames are present
        assertTrue(tagged.indexOfBytes("TIT2".toByteArray()) >= 0)
        assertTrue(tagged.indexOfBytes("Bohemian Rhapsody".toByteArray()) >= 0)

        assertTrue(tagged.indexOfBytes("TPE1".toByteArray()) >= 0)
        assertTrue(tagged.indexOfBytes("Queen".toByteArray()) >= 0)

        assertTrue(tagged.indexOfBytes("TALB".toByteArray()) >= 0)
        assertTrue(tagged.indexOfBytes("A Night at the Opera".toByteArray()) >= 0)

        assertTrue(tagged.indexOfBytes("TRCK".toByteArray()) >= 0)
        assertTrue(tagged.indexOfBytes("4".toByteArray()) >= 0)

        assertTrue(tagged.indexOfBytes("USLT".toByteArray()) >= 0)
        assertTrue(tagged.indexOfBytes("Is this the real life?".toByteArray()) >= 0)

        assertTrue(tagged.indexOfBytes("APIC".toByteArray()) >= 0)
        assertTrue(tagged.indexOfBytes("image/jpeg".toByteArray()) >= 0)
        assertTrue(tagged.indexOfBytes(cover) >= 0)

        // Verify audio bytes at the end remain untouched
        val audioStart = tagged.size - dummyAudio.size
        assertArrayEquals(dummyAudio, tagged.copyOfRange(audioStart, tagged.size))
    }

    @Test
    fun `id3 tagging replaces existing ID3 header without duplicating`() {
        val dummyAudio = ByteArray(50) { 42 }
        val firstPass = Id3Tagger.tag(
            bytes = dummyAudio,
            title = "Old Title",
            artist = "Old Artist",
        )

        val secondPass = Id3Tagger.tag(
            bytes = firstPass,
            title = "New Title",
            artist = "New Artist",
        )

        // Old title must no longer be present
        assertEquals(-1, secondPass.indexOfBytes("Old Title".toByteArray()))
        assertTrue(secondPass.indexOfBytes("New Title".toByteArray()) >= 0)
        assertTrue(secondPass.indexOfBytes("New Artist".toByteArray()) >= 0)

        // Audio payload at the end must match
        val audioStart = secondPass.size - dummyAudio.size
        assertArrayEquals(dummyAudio, secondPass.copyOfRange(audioStart, secondPass.size))
    }

    @Test
    fun `id3 tagging handles unicode text with UTF-16`() {
        val dummyAudio = ByteArray(32) { 0 }
        val unicodeTitle = "初音ミクの消失" // Hatsune Miku
        val unicodeArtist = "cosMo@暴走P"

        val tagged = Id3Tagger.tag(
            bytes = dummyAudio,
            title = unicodeTitle,
            artist = unicodeArtist,
        )

        assertTrue(tagged.indexOfBytes("TIT2".toByteArray()) >= 0)
        assertTrue(tagged.indexOfBytes(unicodeTitle.toByteArray(Charsets.UTF_16LE)) >= 0)
        assertTrue(tagged.indexOfBytes(unicodeArtist.toByteArray(Charsets.UTF_16LE)) >= 0)
    }
}
