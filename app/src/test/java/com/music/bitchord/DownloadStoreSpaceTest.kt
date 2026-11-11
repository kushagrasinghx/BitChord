package com.music.bitchord

import com.music.bitchord.download.DownloadStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DownloadStoreSpaceTest {

    @Test
    fun `insufficient storage raises a distinct error instead of a network error`() {
        val error = assertThrows(DownloadStore.InsufficientSpaceException::class.java) {
            DownloadStore.requireFreeSpace(availableBytes = 1024, minimumBytes = 64 * 1024 * 1024)
        }
        assertEquals(true, error.message?.contains("Not enough free storage"))
    }

    @Test
    fun `sufficient storage passes`() {
        DownloadStore.requireFreeSpace(availableBytes = 64 * 1024 * 1024, minimumBytes = 64 * 1024 * 1024)
    }
}
