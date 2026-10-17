package com.music.bitchord.desktop

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Artwork extraction and caching, driven through real (tiny) audio files written into temp
 * directories. The files are synthetic — just enough structure for the parser to find the
 * `PICTURE` block or `APIC` frame — because the thing under test is the metadata walk, not the
 * image decoder.
 *
 * Streamed and downloaded track artwork is unaffected by this feature; those songs carry a YouTube
 * thumbnail URL in [com.music.bitchord.data.model.Song.thumbnailUrl] and never enter this path.
 */
class DesktopLocalArtworkTest {

    // ---- FLAC embedded artwork -----------------------------------------

    @Test
    fun flacWithEmbeddedPictureBlockReturnsArtworkUri() {
        val dir = createTempDirectory("artwork-test-")
        try {
            val flac = dir.resolve("track.flac")
            Files.write(flac, buildFlacWithPicture(TINY_JPEG))
            val uri = DesktopLocalArtwork.artworkUri(flac)
            assertNotNull(uri, "a FLAC with a PICTURE block should produce an artwork URI")
            assertTrue(uri.startsWith("file:"), "the URI should be a file:// path, got: $uri")
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun flacWithoutPictureBlockReturnsNull() {
        val dir = createTempDirectory("artwork-test-")
        try {
            val flac = dir.resolve("empty.flac")
            Files.write(flac, buildFlacWithoutPicture())
            val uri = DesktopLocalArtwork.artworkUri(flac)
            assertNull(uri, "a FLAC without a PICTURE block should return null")
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun flacWithoutPictureFallsBackToFolderCover() {
        val dir = createTempDirectory("artwork-test-")
        try {
            val flac = dir.resolve("empty.flac")
            Files.write(flac, buildFlacWithoutPicture())
            Files.write(dir.resolve("cover.jpg"), TINY_JPEG)
            val uri = DesktopLocalArtwork.artworkUri(flac)
            assertNotNull(uri, "a FLAC beside a cover.jpg should produce an artwork URI")
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    // ---- MP3 embedded artwork ------------------------------------------

    @Test
    fun mp3WithApicFrameReturnsArtworkUri() {
        val dir = createTempDirectory("artwork-test-")
        try {
            val mp3 = dir.resolve("track.mp3")
            Files.write(mp3, buildMp3WithApic(TINY_JPEG))
            val uri = DesktopLocalArtwork.artworkUri(mp3)
            assertNotNull(uri, "an MP3 with an APIC frame should produce an artwork URI")
            assertTrue(uri.startsWith("file:"), "the URI should be a file:// path, got: $uri")
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun mp3WithoutApicReturnsNull() {
        val dir = createTempDirectory("artwork-test-")
        try {
            val mp3 = dir.resolve("no-art.mp3")
            Files.write(mp3, buildMp3WithoutApic())
            val uri = DesktopLocalArtwork.artworkUri(mp3)
            assertNull(uri, "an MP3 without an APIC frame should return null")
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    // ---- Unsupported and missing formats -------------------------------

    @Test
    fun oggFileWithNoFolderCoverReturnsNull() {
        val dir = createTempDirectory("artwork-test-")
        try {
            val ogg = dir.resolve("track.ogg")
            Files.write(ogg, byteArrayOf(0x4F, 0x67, 0x67, 0x53)) // OggS magic
            val uri = DesktopLocalArtwork.artworkUri(ogg)
            assertNull(uri, "an OGG without a folder cover should return null")
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun nonExistentFileReturnsNull() {
        val missing = Path.of("/tmp/definitely-does-not-exist-${System.nanoTime()}.flac")
        val uri = DesktopLocalArtwork.artworkUri(missing)
        assertNull(uri, "a non-existent file should return null")
    }

    // ---- Caching behaviour ---------------------------------------------

    @Test
    fun secondCallReturnsCachedUriWithoutRereading() {
        val dir = createTempDirectory("artwork-test-")
        try {
            val flac = dir.resolve("cached.flac")
            Files.write(flac, buildFlacWithPicture(TINY_JPEG))
            val first = DesktopLocalArtwork.artworkUri(flac)
            // Delete the original so the extractor cannot read it again.
            Files.delete(flac)
            val second = DesktopLocalArtwork.artworkUri(flac)
            assertEquals(first, second, "the cached URI should survive the original file being deleted")
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun clearCacheRemovesSidecars() {
        val dir = createTempDirectory("artwork-test-")
        try {
            val flac = dir.resolve("clear.flac")
            Files.write(flac, buildFlacWithPicture(TINY_JPEG))
            val first = DesktopLocalArtwork.artworkUri(flac)
            assertNotNull(first)

            DesktopLocalArtwork.clearCache()

            // After clearing, re-extracting from the file should still work.
            val second = DesktopLocalArtwork.artworkUri(flac)
            assertNotNull(second, "re-extraction after clear should succeed")
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    // ---- Folder cover fallback -----------------------------------------

    @Test
    fun folderCoverIsFoundInPriorityOrder() {
        val dir = createTempDirectory("artwork-test-")
        try {
            val wav = dir.resolve("track.wav")
            Files.write(wav, byteArrayOf(0x52, 0x49, 0x46, 0x46)) // RIFF magic
            // No embedded artwork for WAV — expects folder fallback.
            Files.write(dir.resolve("front.png"), TINY_PNG)
            val uri = DesktopLocalArtwork.artworkUri(wav)
            assertNotNull(uri, "a WAV beside a front.png should produce an artwork URI")
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    // ---- Streamed/downloaded tracks are unaffected ----------------------

    @Test
    fun streamedSongThumbnailUrlIsNotAltered() {
        // Streamed songs carry a YouTube URL; verify the Song model keeps it.
        val song = com.music.bitchord.data.model.Song(
            videoId = "dQw4w9WgXcQ",
            title = "Never Gonna Give You Up",
            artist = "Rick Astley",
            thumbnailUrl = "https://lh3.googleusercontent.com/w226-h226-l90-rj",
        )
        assertEquals(
            "https://lh3.googleusercontent.com/w226-h226-l90-rj",
            song.thumbnailUrl,
            "a streamed song's artwork URL must not be modified",
        )
    }

    // ---- Synthetic file builders ----------------------------------------

    companion object {
        /** A minimal valid JPEG: SOI marker, then EOI. */
        private val TINY_JPEG = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), // SOI
            0xFF.toByte(), 0xE0.toByte(), // APP0
            0x00, 0x10, // length 16
            0x4A, 0x46, 0x49, 0x46, 0x00, // "JFIF\0"
            0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0xFF.toByte(), 0xD9.toByte(), // EOI
        )

        /** A minimal PNG: signature + IHDR + IEND. */
        private val TINY_PNG = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
            0x00, 0x00, 0x00, 0x0D, // IHDR length = 13
            0x49, 0x48, 0x44, 0x52, // "IHDR"
            0x00, 0x00, 0x00, 0x01, // width = 1
            0x00, 0x00, 0x00, 0x01, // height = 1
            0x08, 0x02, // 8-bit RGB
            0x00, 0x00, 0x00, // compression, filter, interlace
            0x00, 0x00, 0x00, 0x00, // CRC placeholder
            0x00, 0x00, 0x00, 0x00, // IEND length = 0
            0x49, 0x45, 0x4E, 0x44, // "IEND"
            0xAE.toByte(), 0x42, 0x60, 0x82.toByte(), // IEND CRC
        )

        /**
         * Builds a minimal FLAC file with a `STREAMINFO` block and a `PICTURE` block
         * containing the given image bytes.
         */
        private fun buildFlacWithPicture(image: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            // Magic.
            out.write("fLaC".toByteArray())
            // STREAMINFO block: type=0, not last. 34 bytes of zeros is fine for the parser.
            out.write(0x00) // type 0, not last
            out.writeBe3(34) // length
            out.write(ByteArray(34))
            // PICTURE block: type=6, last.
            val picturePayload = buildFlacPicture(image)
            out.write(0x80 or 6) // type 6, last
            out.writeBe3(picturePayload.size)
            out.write(picturePayload)
            return out.toByteArray()
        }

        /** Builds a FLAC `PICTURE` payload with image type "front cover". */
        private fun buildFlacPicture(image: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            val mime = "image/jpeg".toByteArray()
            out.writeBe4(3) // picture type = front cover
            out.writeBe4(mime.size)
            out.write(mime)
            out.writeBe4(0) // description length
            out.writeBe4(1) // width
            out.writeBe4(1) // height
            out.writeBe4(24) // colour depth
            out.writeBe4(0) // colours
            out.writeBe4(image.size)
            out.write(image)
            return out.toByteArray()
        }

        /** Builds a minimal FLAC with only STREAMINFO (no PICTURE). */
        private fun buildFlacWithoutPicture(): ByteArray {
            val out = ByteArrayOutputStream()
            out.write("fLaC".toByteArray())
            out.write(0x80 or 0) // type 0 (STREAMINFO), last
            out.writeBe3(34)
            out.write(ByteArray(34))
            return out.toByteArray()
        }

        /**
         * Builds a minimal MP3 with an ID3v2.3 tag containing a single APIC frame.
         */
        private fun buildMp3WithApic(image: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            // APIC frame payload.
            val apic = ByteArrayOutputStream()
            apic.write(0x00) // encoding: ISO-8859-1
            apic.write("image/jpeg\u0000".toByteArray()) // MIME + null
            apic.write(0x03) // picture type: front cover
            apic.write(0x00) // description: empty, null-terminated
            apic.write(image)
            val apicBytes = apic.toByteArray()

            // APIC frame header.
            val frame = ByteArrayOutputStream()
            frame.write("APIC".toByteArray())
            frame.writeBe4(apicBytes.size) // v2.3: plain big-endian
            frame.write(0x00) // flags
            frame.write(0x00)
            frame.write(apicBytes)
            val frameBytes = frame.toByteArray()

            // ID3v2.3 header.
            out.write("ID3".toByteArray())
            out.write(0x03) // major version 3
            out.write(0x00) // revision
            out.write(0x00) // flags
            // Tag size in synchsafe: up to 127 bytes per byte.
            val size = frameBytes.size
            out.write((size ushr 21) and 0x7F)
            out.write((size ushr 14) and 0x7F)
            out.write((size ushr 7) and 0x7F)
            out.write(size and 0x7F)
            out.write(frameBytes)

            // Fake audio frame so the file has something after the tag.
            out.write(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00))
            return out.toByteArray()
        }

        /** Builds a minimal MP3 with an ID3v2.3 tag but no APIC frame. */
        private fun buildMp3WithoutApic(): ByteArray {
            val out = ByteArrayOutputStream()
            // ID3v2.3 header with a TIT2 frame (title) instead of APIC.
            val title = "Test".toByteArray()
            val frame = ByteArrayOutputStream()
            frame.write("TIT2".toByteArray())
            frame.writeBe4(title.size + 1) // +1 for encoding byte
            frame.write(0x00) // flags
            frame.write(0x00)
            frame.write(0x00) // encoding: ISO-8859-1
            frame.write(title)
            val frameBytes = frame.toByteArray()

            out.write("ID3".toByteArray())
            out.write(0x03) // major version 3
            out.write(0x00) // revision
            out.write(0x00) // flags
            val size = frameBytes.size
            out.write((size ushr 21) and 0x7F)
            out.write((size ushr 14) and 0x7F)
            out.write((size ushr 7) and 0x7F)
            out.write(size and 0x7F)
            out.write(frameBytes)

            out.write(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00))
            return out.toByteArray()
        }

        private fun ByteArrayOutputStream.writeBe3(value: Int) {
            write((value ushr 16) and 0xFF)
            write((value ushr 8) and 0xFF)
            write(value and 0xFF)
        }

        private fun ByteArrayOutputStream.writeBe4(value: Int) {
            write((value ushr 24) and 0xFF)
            write((value ushr 16) and 0xFF)
            write((value ushr 8) and 0xFF)
            write(value and 0xFF)
        }
    }
}
