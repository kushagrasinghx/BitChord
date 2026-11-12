package com.music.bitchord

import com.music.bitchord.playback.cachedContainerOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What a download made straight from the song cache is filed as. It is read
 * off the file's own first bytes, so the one thing that must never happen is a
 * file being saved under the wrong extension — a manifest or an unknown
 * container is refused, and the download falls back to the network.
 */
class CachedContainerTest {

    private fun head(vararg prefix: Int): ByteArray =
        ByteArray(12).also { bytes -> prefix.forEachIndexed { i, b -> bytes[i] = b.toByte() } }

    private fun head(text: String, at: Int = 0): ByteArray =
        ByteArray(12).also { bytes -> text.toByteArray(Charsets.US_ASCII).copyInto(bytes, at) }

    @Test
    fun `an EBML header is a WebM`() {
        assertEquals("webm" to "audio/webm", cachedContainerOf(head(0x1A, 0x45, 0xDF, 0xA3)))
    }

    @Test
    fun `an ftyp box is an MP4 filed as m4a`() {
        assertEquals("m4a" to "audio/mp4", cachedContainerOf(head("ftypdash", at = 4)))
    }

    @Test
    fun `a FLAC stream marker is a FLAC`() {
        assertEquals("flac" to "audio/flac", cachedContainerOf(head("fLaC")))
    }

    @Test
    fun `a manifest is refused rather than misnamed`() {
        assertNull(cachedContainerOf("<?xml versio".toByteArray(Charsets.US_ASCII)))
        assertNull(cachedContainerOf("#EXTM3U\n#EXT".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun `too few bytes is no answer`() {
        assertNull(cachedContainerOf(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())))
    }
}
