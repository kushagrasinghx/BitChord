package com.music.bitchord

import com.music.bitchord.data.LocalMediaRepository
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The cover inside a file opened from a file manager, kept where the player can load it (#1352). */
class EmbeddedCoverCacheTest {

    private val dir = Files.createTempDirectory("embedded_art").toFile()
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)

    @Test
    fun `a picture is written once and named by its file`() {
        val first = LocalMediaRepository.cachedEmbeddedArt(dir, "content://fm/song.mp3", jpeg)!!
        assertTrue(first.startsWith("file://"))
        val file = File(first.removePrefix("file://"))
        assertArrayEquals(jpeg, file.readBytes())
        assertEquals(first, LocalMediaRepository.cachedEmbeddedArt(dir, "content://fm/song.mp3", jpeg))
        assertEquals(1, dir.listFiles()!!.size)
    }

    @Test
    fun `a file with no picture has no cover`() {
        assertNull(LocalMediaRepository.cachedEmbeddedArt(dir, "content://fm/song.mp3", null))
        assertNull(LocalMediaRepository.cachedEmbeddedArt(dir, "content://fm/song.mp3", ByteArray(0)))
    }
}
