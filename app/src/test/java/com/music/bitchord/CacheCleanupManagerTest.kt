package com.music.bitchord

import com.music.bitchord.data.CacheCleanupManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CacheCleanupManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `runIfNeeded prunes old cache files and respects size cap without touching downloads`() {
        val cacheDir = tempFolder.newFolder("cache")
        val downloadsDir = tempFolder.newFolder("downloads")

        val nowMs = 1_000_000_000L
        val imageCache = File(cacheDir, "image_cache").apply { mkdirs() }
        val oldThumb = File(imageCache, "old.jpg").apply {
            writeBytes(ByteArray(100))
            setLastModified(1_000L)
        }
        val newerThumb = File(imageCache, "newer.jpg").apply {
            writeBytes(ByteArray(100))
            setLastModified(nowMs - 10_000L)
        }
        val newestThumb = File(imageCache, "newest.jpg").apply {
            writeBytes(ByteArray(100))
            setLastModified(nowMs - 5_000L)
        }
        val offlineSong = File(downloadsDir, "saved_song.flac").apply {
            writeBytes(ByteArray(500))
            setLastModified(500L)
        }

        var recordedTimestamp = 0L
        val deleted = CacheCleanupManager.runIfNeeded(
            cacheDir = cacheDir,
            intervalDays = 7,
            maxBytes = 120L,
            lastCleanupMs = 0L,
            nowMs = nowMs,
        ) { recordedTimestamp = it }

        assertEquals(2, deleted)
        assertFalse(oldThumb.exists())
        assertFalse(newerThumb.exists())
        assertTrue(newestThumb.exists())
        assertTrue(offlineSong.exists())
        assertEquals(1_000_000_000L, recordedTimestamp)
    }
}
