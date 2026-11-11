package com.music.bitchord

import com.music.bitchord.download.DownloadStore
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadFilenameTest {

    @Test
    fun `same title different recording gets a new filename`() {
        val taken = setOf("Artist - Intro.m4a", "Artist - Intro (2).m4a")
        assertEquals("Artist - Intro (3).m4a", DownloadStore.nextAvailableName("Artist - Intro.m4a") { it in taken })
    }

    @Test
    fun `first free suffix is used`() {
        assertEquals("Track (2).flac", DownloadStore.nextAvailableName("Track.flac") { false })
    }

    @Test
    fun `extension is preserved when there is none`() {
        assertEquals("Track (2)", DownloadStore.nextAvailableName("Track") { false })
    }
}
