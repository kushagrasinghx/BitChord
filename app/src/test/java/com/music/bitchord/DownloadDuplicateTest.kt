package com.music.bitchord

import com.music.bitchord.download.alreadyDownloaded
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadDuplicateTest {

    private val saved = mapOf("song-1" to "file:///music/song-1.m4a")

    @Test
    fun `existing saved file suppresses duplicate enqueue`() {
        assertTrue(alreadyDownloaded(saved, "song-1") { true })
    }

    @Test
    fun `missing saved file allows re-download`() {
        assertFalse(alreadyDownloaded(saved, "song-1") { false })
    }

    @Test
    fun `unknown id is not a duplicate`() {
        assertFalse(alreadyDownloaded(saved, "other") { true })
    }
}
