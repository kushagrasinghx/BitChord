package com.music.bitchord.desktop

import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Locale
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

/**
 * Extracts album art from local audio files and caches it on disk.
 *
 * The scan in [DesktopLocalMusic] runs on every launch and on every watcher
 * notification, so the extraction must not re-read a 40 MB FLAC each time.
 * The result is written once to a per-file sidecar under the app's own cache
 * directory, and the `file://` URI of that sidecar becomes the song's
 * [thumbnailUrl][com.music.bitchord.data.model.Song.thumbnailUrl] — from
 * there, [DesktopArtworkCache] handles it identically to a remote URL.
 *
 * Extraction order:
 * 1. Embedded artwork: FLAC `PICTURE` block, ID3v2 `APIC` frame (MP3).
 * 2. Folder cover image: `cover.{jpg,jpeg,png}`, `folder.{jpg,jpeg,png}`,
 *    `front.{jpg,jpeg,png}`, or `album.{jpg,jpeg,png}` beside the file.
 *
 * Everything is best-effort and caught: a file whose artwork cannot be read
 * still appears in the library, just without a cover.
 */
internal object DesktopLocalArtwork {

    /** Where the files live, created on first use. */
    private val directory: Path by lazy {
        val base = System.getenv("XDG_CACHE_HOME")?.takeIf(String::isNotBlank)
            ?: "${System.getProperty("user.home")}/.cache"
        Path.of(base, "bitchord", "local-artwork").also {
            runCatching { Files.createDirectories(it) }
        }
    }

    /** Names, in priority order, that music folders use for a cover beside the tracks. */
    private val FOLDER_COVER_NAMES = listOf("cover", "folder", "front", "album", "artwork")
    private val IMAGE_EXTENSIONS = listOf("jpg", "jpeg", "png")

    /**
     * A `file://` URI of the cached artwork for [audioPath], or null when the
     * file carries no artwork and no folder cover could be found.
     *
     * The very first call for a given file extracts the image and writes the
     * sidecar; subsequent calls return the existing path without touching the
     * audio file at all.
     */
    fun artworkUri(audioPath: Path): String? = runCatching {
        val absolute = audioPath.toAbsolutePath().normalize()
        val key = digest(absolute.toString())
        val cached = directory.resolve("$key.jpg")
        if (Files.isRegularFile(cached) && Files.size(cached) > 0) {
            return cached.toUri().toString()
        }

        // A zero-byte marker means "looked, found nothing" — skip the extraction
        // on the next scan without opening the file again.
        if (Files.isRegularFile(cached)) return null

        val bytes = extractEmbedded(absolute) ?: folderCover(absolute)
        if (bytes != null) {
            Files.write(cached, bytes)
            return cached.toUri().toString()
        }

        // Write a zero-byte marker so the next scan knows this file was tried.
        Files.write(cached, ByteArray(0))
        null
    }.getOrNull()

    /**
     * Removes every cached sidecar. Called when the listener changes their
     * local-music folder — the old folder's covers are no longer relevant,
     * and a full re-extraction on the next scan picks up any that are.
     */
    fun clearCache() {
        runCatching {
            Files.list(directory).use { stream ->
                stream.forEach { runCatching { Files.deleteIfExists(it) } }
            }
        }
    }

    // ---- Embedded extraction ------------------------------------------------

    private fun extractEmbedded(path: Path): ByteArray? = when (path.extension.lowercase(Locale.ROOT)) {
        "flac" -> extractFlac(path)
        "mp3" -> extractMp3(path)
        else -> null
    }

    /**
     * Reads the FLAC metadata block chain and returns the first `PICTURE`
     * block's image bytes, or null.
     *
     * Only the metadata region is read — on a 40 MB file that is usually
     * under 64 kB — and parsing reuses the same block-header layout
     * [FlacTagger][com.music.bitchord.download.FlacTagger] writes.
     */
    private fun extractFlac(path: Path): ByteArray? = runCatching {
        RandomAccessFile(path.toFile(), "r").use { raf ->
            val magic = ByteArray(4)
            if (raf.read(magic) != 4) return@runCatching null
            if (magic.decodeToString() != "fLaC") return@runCatching null

            while (true) {
                val flagByte = raf.read()
                if (flagByte == -1) return@runCatching null
                val isLast = flagByte and 0x80 != 0
                val type = flagByte and 0x7F
                val lenBytes = ByteArray(3)
                if (raf.read(lenBytes) != 3) return@runCatching null
                val length = ((lenBytes[0].toInt() and 0xFF) shl 16) or
                    ((lenBytes[1].toInt() and 0xFF) shl 8) or
                    (lenBytes[2].toInt() and 0xFF)

                if (type == TYPE_PICTURE) {
                    return@runCatching readFlacPicture(raf)
                }

                // Skip this block's payload.
                val skipped = raf.skipBytes(length)
                if (skipped < length) {
                    // File is truncated.
                    return@runCatching null
                }
                if (isLast) break
            }
            null
        }
    }.getOrNull()

    /**
     * Reads a FLAC `PICTURE` block payload and returns the raw image bytes.
     *
     * Layout: picture-type (4B BE), MIME length (4B BE), MIME string,
     * description length (4B BE), description string, width (4B), height (4B),
     * colour depth (4B), colours (4B), data length (4B BE), data.
     */
    private fun readFlacPicture(raf: RandomAccessFile): ByteArray? {
        fun readBe(): Int {
            val b = ByteArray(4)
            if (raf.read(b) != 4) return -1
            return ((b[0].toInt() and 0xFF) shl 24) or
                ((b[1].toInt() and 0xFF) shl 16) or
                ((b[2].toInt() and 0xFF) shl 8) or
                (b[3].toInt() and 0xFF)
        }

        readBe() // picture type — don't care which, just take the first one
        val mimeLen = readBe(); if (mimeLen < 0 || mimeLen > 256) return null
        raf.skipBytes(mimeLen)
        val descLen = readBe(); if (descLen < 0 || descLen > 4096) return null
        raf.skipBytes(descLen)
        raf.skipBytes(16) // width, height, colour depth, colours
        val dataLen = readBe(); if (dataLen <= 0 || dataLen > 20 * 1024 * 1024) return null
        val data = ByteArray(dataLen)
        raf.readFully(data)
        return data
    }

    /**
     * Reads the first `APIC` frame from an ID3v2 tag at the front of an MP3.
     *
     * Only ID3v2.3 and 2.4 are handled — v2.2 uses a three-byte frame id
     * `PIC` that is virtually extinct. The tag is read in one pass up to
     * 4 MB, which is generous: a cover over 1 MB is unusual.
     */
    private fun extractMp3(path: Path): ByteArray? = runCatching {
        RandomAccessFile(path.toFile(), "r").use { raf ->
            val header = ByteArray(10)
            if (raf.read(header) != 10) return@runCatching null
            if (header.decodeToString(0, 3) != "ID3") return@runCatching null

            val major = header[3].toInt() and 0xFF
            if (major !in 3..4) return@runCatching null

            // Synchsafe size: 4 × 7-bit bytes.
            val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                ((header[7].toInt() and 0x7F) shl 14) or
                ((header[8].toInt() and 0x7F) shl 7) or
                (header[9].toInt() and 0x7F)
            if (tagSize <= 0 || tagSize > 4 * 1024 * 1024) return@runCatching null

            val tag = ByteArray(tagSize)
            if (raf.read(tag) != tagSize) return@runCatching null

            findApicFrame(tag, major)
        }
    }.getOrNull()

    /**
     * Locates and extracts an `APIC` frame from an ID3v2.3 or v2.4 tag body.
     */
    private fun findApicFrame(tag: ByteArray, major: Int): ByteArray? {
        var offset = 0
        while (offset + 10 <= tag.size) {
            val frameId = tag.decodeToString(offset, offset + 4)
            if (frameId[0] == '\u0000') break // padding
            val frameSize = if (major == 4) {
                // v2.4 uses synchsafe integers for frame size.
                ((tag[offset + 4].toInt() and 0x7F) shl 21) or
                    ((tag[offset + 5].toInt() and 0x7F) shl 14) or
                    ((tag[offset + 6].toInt() and 0x7F) shl 7) or
                    (tag[offset + 7].toInt() and 0x7F)
            } else {
                // v2.3 uses plain 4-byte big-endian.
                ((tag[offset + 4].toInt() and 0xFF) shl 24) or
                    ((tag[offset + 5].toInt() and 0xFF) shl 16) or
                    ((tag[offset + 6].toInt() and 0xFF) shl 8) or
                    (tag[offset + 7].toInt() and 0xFF)
            }
            if (frameSize <= 0 || offset + 10 + frameSize > tag.size) break

            val dataStart = offset + 10
            offset = dataStart + frameSize

            if (frameId != "APIC") continue
            return parseApicPayload(tag, dataStart, frameSize)
        }
        return null
    }

    /**
     * Parses an `APIC` frame payload: encoding byte, null-terminated MIME
     * string, picture-type byte, null-terminated description, then image data.
     */
    private fun parseApicPayload(tag: ByteArray, start: Int, size: Int): ByteArray? {
        if (size < 4) return null
        val end = start + size
        var pos = start + 1 // skip text-encoding byte

        // Skip MIME type (null-terminated).
        while (pos < end && tag[pos].toInt() != 0) pos++
        if (pos >= end) return null
        pos++ // past null

        // Skip picture type byte.
        if (pos >= end) return null
        pos++

        // Skip description (null-terminated; for UTF-16 this is double-null,
        // but the image data that follows is recognisable either way — a stray
        // null byte at the front of a JPEG or PNG is harmless to the decoder).
        val encoding = tag[start].toInt() and 0xFF
        if (encoding == 1 || encoding == 2) {
            // UTF-16: look for double-null terminator.
            while (pos + 1 < end) {
                if (tag[pos].toInt() == 0 && tag[pos + 1].toInt() == 0) { pos += 2; break }
                pos++
            }
        } else {
            while (pos < end && tag[pos].toInt() != 0) pos++
            if (pos < end) pos++ // past null
        }

        if (pos >= end) return null
        return tag.copyOfRange(pos, end)
    }

    // ---- Folder cover fallback ----------------------------------------------

    /**
     * Looks for a cover image beside the audio file — the convention every
     * music manager since the early 2000s has followed.
     */
    private fun folderCover(audioPath: Path): ByteArray? {
        val dir = audioPath.parent ?: return null
        for (name in FOLDER_COVER_NAMES) {
            for (ext in IMAGE_EXTENSIONS) {
                val candidate = dir.resolve("$name.$ext")
                if (candidate.isRegularFile()) {
                    return runCatching {
                        val bytes = Files.readAllBytes(candidate)
                        if (bytes.size > 20 * 1024 * 1024 || bytes.isEmpty()) null else bytes
                    }.getOrNull()
                }
            }
        }
        return null
    }

    // ---- Utilities ----------------------------------------------------------

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(32)

    /** FLAC metadata block type for a `PICTURE`. */
    private const val TYPE_PICTURE = 6
}
