package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A download record is a claim about a folder this app does not own.
 *
 * The listener manages that folder with a file manager, so an entry can outlive the file it names.
 * Trusted rather than checked, such an entry showed the track as downloaded, refused to fetch it
 * again, and then handed the decoder a path that was not there — which surfaced only as
 * "could not open stream".
 */
class DesktopDownloadRecordTest {

    private val scratch = Files.createTempDirectory("bitchord-downloads")

    @AfterTest
    fun cleanUp() {
        scratch.toFile().deleteRecursively()
    }

    private fun song(id: String, path: String?) = Song(
        videoId = id,
        title = id,
        artist = "a",
        thumbnailUrl = "",
        localPath = path,
        localUri = path?.let { java.io.File(it).toURI().toString() },
    )

    private fun realFile(name: String): String {
        val file = scratch.resolve(name)
        Files.write(file, ByteArray(2048))
        return file.toAbsolutePath().toString()
    }

    @Test
    fun `a record whose file is there is honoured`() {
        val saved = song("a", realFile("a.m4a"))
        assertTrue(DesktopDownloadManager.isSaved(saved))
        assertEquals(saved.localPath, DesktopDownloadManager.savedFile(saved)?.toString())
    }

    @Test
    fun `a record whose file has gone is not`() {
        val gone = song("b", scratch.resolve("never-written.m4a").toString())
        assertFalse(DesktopDownloadManager.isSaved(gone))
        assertNull(DesktopDownloadManager.savedFile(gone))
    }

    @Test
    fun `an empty file is not a download`() {
        val empty = scratch.resolve("empty.m4a")
        Files.write(empty, ByteArray(0))
        assertFalse(DesktopDownloadManager.isSaved(song("c", empty.toString())))
    }

    @Test
    fun `a track that was never downloaded has no file`() {
        assertNull(DesktopDownloadManager.savedFile(song("d", null)))
        assertFalse(DesktopDownloadManager.isSaved(song("d", null)))
    }

    @Test
    fun `verifying a list keeps only what is on disk`() {
        val here = song("here", realFile("here.m4a"))
        val gone = song("gone", scratch.resolve("gone.m4a").toString())
        val never = song("never", null)
        assertEquals(listOf("here"), DesktopDownloadManager.verified(listOf(here, gone, never)).map { it.videoId })
    }

    @Test
    fun `deleting removes the file and says so`() {
        val saved = song("e", realFile("e.m4a"))
        assertTrue(DesktopDownloadManager.delete(saved))
        assertFalse(DesktopDownloadManager.isSaved(saved))
        // Deleting again is not an error, just nothing to do.
        assertFalse(DesktopDownloadManager.delete(saved))
    }

    @Test
    fun `a file uri with spaces and brackets is resolved`() {
        // The name the downloader actually writes: "Justin Bieber - Peaches (feat. …).m4a".
        val saved = song("f", realFile("Artist - Track (feat. Someone).m4a"))
        assertTrue(DesktopDownloadManager.isSaved(saved))
        // Even with only the uri to go on.
        val uriOnly = Song(
            videoId = "f",
            title = "f",
            artist = "a",
            thumbnailUrl = "",
            localUri = saved.localUri,
        )
        assertTrue(DesktopDownloadManager.isSaved(uriOnly))
    }

    @Test
    fun `what reaches the decoder is a path, not a percent-encoded uri`() {
        // FFmpeg does not percent-decode what it is handed. A file:// uri for a name with spaces
        // and brackets in it sent the decoder looking for "Justin%20Bieber%20-%20…" and it
        // answered "No such file or directory" for a file that was sitting right there.
        val saved = song("g", realFile("Justin Bieber - Peaches (feat. Daniel Caesar & Giveon).m4a"))
        val handed = DesktopDownloadManager.savedFile(saved)!!.toAbsolutePath().toString()
        assertFalse(handed.contains("%"), handed)
        assertFalse(handed.startsWith("file:"), handed)
        assertTrue(java.io.File(handed).isFile, handed)
        // The recorded uri is still a uri — it is the record, not what is opened.
        assertTrue(saved.localUri!!.startsWith("file:"))
        assertTrue(saved.localUri!!.contains("%20"))
    }
}
