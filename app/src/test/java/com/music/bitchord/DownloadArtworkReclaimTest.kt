package com.music.bitchord

import com.music.bitchord.download.DownloadStore
import com.music.bitchord.download.Downloads
import com.music.bitchord.download.MediaTagger
import com.music.bitchord.download.SavedCollection
import com.music.bitchord.download.SavedSongMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadArtworkReclaimTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val now = 1_700_000_000_000L
    private val day = DownloadStore.ORPHAN_MIN_AGE_MS

    private fun sidecar(name: String, ageMs: Long): File {
        val file = File(temp.root, name)
        file.writeBytes(ByteArray(32))
        assertTrue(file.setLastModified(now - ageMs))
        return file
    }

    @Test
    fun `old unreferenced artwork is reclaimed`() {
        val orphan = sidecar("aaaaaaaa.jpg", 2 * day)
        val removed = MediaTagger.orphanedArtwork(temp.root, emptySet(), now)
        assertEquals(listOf(orphan), removed)
    }

    @Test
    fun `artwork a download record still names survives`() {
        sidecar("bbbbbbbb.jpg", 30 * day)
        assertTrue(MediaTagger.orphanedArtwork(temp.root, setOf("bbbbbbbb.jpg"), now).isEmpty())
    }

    @Test
    fun `fresh artwork is treated as an in-flight download`() {
        sidecar("cccccccc.jpg", 1_000L)
        assertTrue(MediaTagger.orphanedArtwork(temp.root, emptySet(), now).isEmpty())
    }

    @Test
    fun `track and collection records supply the reference set`() {
        val metadata = mapOf(
            "a" to SavedSongMetadata(
                videoId = "a", title = "A", artist = "x", uri = "file:///music/a.flac",
                thumbnailUrl = "file:///data/app/files/download-artwork/aaaa.jpg",
            ),
            "b" to SavedSongMetadata(
                videoId = "b", title = "B", artist = "x", uri = "file:///music/b.flac",
                thumbnailUrl = "https://i.ytimg.com/remote.jpg",
            ),
        )
        val collections = mapOf(
            "p" to SavedCollection(id = "p", title = "P", subtitle = "", thumbnailUrl = "file:///data/app/files/download-artwork/cccc.jpg", playlist = false),
        )
        assertEquals(setOf("aaaa.jpg", "cccc.jpg"), Downloads.referencedArtworkNames(metadata, collections))
    }

    @Test
    fun `non-sidecar files and partial writes are left alone`() {
        val note = File(temp.root, "note.txt").apply {
            writeText("keep")
            setLastModified(now - 30 * day)
        }
        val part = File(temp.root, ".dddddddd.jpg.part").apply {
            writeBytes(ByteArray(8))
            setLastModified(now - 30 * day)
        }
        assertTrue(MediaTagger.orphanedArtwork(temp.root, emptySet(), now).isEmpty())
        assertTrue(note.exists() && part.exists())
    }
}
