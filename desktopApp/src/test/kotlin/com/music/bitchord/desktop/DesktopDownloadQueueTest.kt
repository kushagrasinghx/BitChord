package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The download queue's bookkeeping.
 *
 * The transfer itself is not exercised — that reaches the network and writes to the listener's own
 * Music folder — so these pin the part that decides what is queued, in what order, and what a
 * cancel does to it.
 */
class DesktopDownloadQueueTest {

    private fun song(id: String) = Song(videoId = id, title = "Track $id", artist = "Artist", thumbnailUrl = "")

    @AfterTest
    fun tearDown() {
        DesktopDownloadQueue.cancelAll()
    }

    @Test
    fun `a track is queued once however many times it is asked for`() {
        DesktopDownloadQueue.enqueue(song("a"))
        DesktopDownloadQueue.enqueue(song("a"))
        assertEquals(1, DesktopDownloadQueue.active.value.count { it.key == "a" })
    }

    @Test
    fun `the queue keeps the order it was asked in`() {
        listOf("a", "b", "c").forEach { DesktopDownloadQueue.enqueue(song(it)) }
        val order = DesktopDownloadQueue.active.value.values
            .sortedBy(DesktopDownloadItem::sequence)
            .map { it.song.videoId }
        assertEquals(listOf("a", "b", "c"), order.take(3))
    }

    @Test
    fun `a cancelled track leaves the manager rather than sitting in it`() {
        DesktopDownloadQueue.enqueue(song("gone"))
        assertTrue("gone" in DesktopDownloadQueue.active.value)
        DesktopDownloadQueue.cancel("gone")
        assertFalse("gone" in DesktopDownloadQueue.active.value)
    }

    @Test
    fun `cancel all empties the queue`() {
        listOf("a", "b", "c").forEach { DesktopDownloadQueue.enqueue(song(it)) }
        DesktopDownloadQueue.cancelAll()
        assertTrue(DesktopDownloadQueue.active.value.isEmpty())
    }

    @Test
    fun `a blank id is not something that can be downloaded`() {
        DesktopDownloadQueue.enqueue(song(""))
        assertFalse("" in DesktopDownloadQueue.active.value)
    }

    @Test
    fun `the summary line says what the queue is doing`() {
        assertEquals("Nothing downloading", summary(total = 0, failed = 0, busy = false))
        assertEquals("1 track", summary(total = 1, failed = 0, busy = true))
        assertEquals("4 tracks", summary(total = 4, failed = 0, busy = true))
        assertEquals("1 download failed", summary(total = 1, failed = 1, busy = false))
        assertEquals("3 downloads failed", summary(total = 3, failed = 3, busy = false))
        // Still going, so the count is what matters rather than the failures behind it.
        assertEquals("5 tracks", summary(total = 5, failed = 1, busy = true))
    }

    @Test
    fun `four transfers run at once, matching Android`() {
        assertEquals(4, DesktopDownloadQueue.WORKERS)
    }
}
