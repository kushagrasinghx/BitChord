package com.music.bitchord.desktop

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Reading the words back out of a file.
 *
 * Built from bytes rather than from a real download: the point is that the parser agrees with the
 * layout each container actually uses, which a hand-built header states exactly.
 */
class DesktopEmbeddedLyricsTest {

    private val scratch = Files.createTempDirectory("bitchord-embedded")

    @AfterTest
    fun cleanUp() {
        scratch.toFile().deleteRecursively()
    }

    // ── FLAC ──────────────────────────────────────────────────────────────

    private fun flac(vararg comments: Pair<String, String>): ByteArray {
        val body = ByteArrayOutputStream()
        fun u32le(value: Int) = body.write(
            byteArrayOf(
                (value and 0xFF).toByte(),
                ((value shr 8) and 0xFF).toByte(),
                ((value shr 16) and 0xFF).toByte(),
                ((value shr 24) and 0xFF).toByte(),
            ),
        )
        val vendor = "test".toByteArray()
        u32le(vendor.size)
        body.write(vendor)
        u32le(comments.size)
        comments.forEach { (name, value) ->
            val entry = "$name=$value".toByteArray(StandardCharsets.UTF_8)
            u32le(entry.size)
            body.write(entry)
        }
        val block = body.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("fLaC".toByteArray(StandardCharsets.US_ASCII))
        // Last-block bit set, type 4 (VORBIS_COMMENT), 24-bit big-endian length.
        out.write(byteArrayOf((0x80 or 4).toByte()))
        out.write(byteArrayOf(((block.size shr 16) and 0xFF).toByte(), ((block.size shr 8) and 0xFF).toByte(), (block.size and 0xFF).toByte()))
        out.write(block)
        return out.toByteArray()
    }

    @Test
    fun `a FLAC's standard lyrics field is read`() {
        val bytes = flac("TITLE" to "Shayar", "LYRICS" to "[00:12.00]a line")
        assertEquals("[00:12.00]a line", DesktopEmbeddedLyrics.fromBytes(bytes))
    }

    @Test
    fun `this app's own word-timed field wins over the standard one`() {
        val bytes = flac(
            "LYRICS" to "[00:12.00]plain",
            DesktopEmbeddedLyrics.WORD_LYRICS_FIELD to "[00:12.00]<00:12.00>word <00:12.50>timed",
        )
        assertEquals("[00:12.00]<00:12.00>word <00:12.50>timed", DesktopEmbeddedLyrics.fromBytes(bytes))
    }

    @Test
    fun `a FLAC with no lyrics comment yields nothing`() {
        assertNull(DesktopEmbeddedLyrics.fromBytes(flac("TITLE" to "Shayar", "ARTIST" to "Someone")))
    }

    @Test
    fun `a blank lyrics value is not lyrics`() {
        assertNull(DesktopEmbeddedLyrics.fromBytes(flac("LYRICS" to "")))
    }

    // ── Containers we do not recognise ────────────────────────────────────

    @Test
    fun `a file that is not one of the three containers yields nothing`() {
        assertNull(DesktopEmbeddedLyrics.fromBytes("just some bytes, not a container".toByteArray()))
        assertNull(DesktopEmbeddedLyrics.fromBytes(ByteArray(0)))
        assertNull(DesktopEmbeddedLyrics.fromBytes(ByteArray(4) { 0 }))
    }

    // ── Offline package sidecar ───────────────────────────────────────────

    @Test
    fun `an offline package's sidecar is read from beside its playlist`() {
        val dir = Files.createDirectory(scratch.resolve("package"))
        val playlist = Files.write(dir.resolve("stream.m3u8"), "#EXTM3U".toByteArray())
        Files.write(dir.resolve("lyrics.lrc"), "[00:01.00]from the sidecar".toByteArray())
        assertEquals("[00:01.00]from the sidecar", DesktopEmbeddedLyrics.sidecar(playlist))
    }

    @Test
    fun `a sidecar is only looked for beside a playlist`() {
        val dir = Files.createDirectory(scratch.resolve("plain"))
        val track = Files.write(dir.resolve("song.flac"), flac("LYRICS" to "x"))
        Files.write(dir.resolve("lyrics.lrc"), "[00:01.00]not this one".toByteArray())
        assertNull(DesktopEmbeddedLyrics.sidecar(track))
    }

    @Test
    fun `a playlist with no sidecar beside it yields nothing`() {
        val dir = Files.createDirectory(scratch.resolve("bare"))
        val playlist = Files.write(dir.resolve("stream.m3u8"), "#EXTM3U".toByteArray())
        assertNull(DesktopEmbeddedLyrics.sidecar(playlist))
    }

    // ── Reading a real file ───────────────────────────────────────────────

    @Test
    fun `the words come back out of a file on disk`() {
        val file = Files.write(scratch.resolve("track.flac"), flac("LYRICS" to "[00:05.00]on disk"))
        assertEquals("[00:05.00]on disk", DesktopEmbeddedLyrics.read(file))
    }

    @Test
    fun `a path that is not a file is not read`() {
        assertNull(DesktopEmbeddedLyrics.read(null))
        assertNull(DesktopEmbeddedLyrics.read(scratch.resolve("nothing-here.flac")))
    }
}
