package com.music.bitchord

import com.music.bitchord.download.DownloadStore
import com.music.bitchord.download.isOwnedDownload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `sequential allocations never reuse a reserved name`() {
        val reserved = mutableSetOf<String>()
        fun allocate(): String =
            DownloadStore.nextAvailableName("Artist - Title.flac") { it in reserved }.also { reserved += it }
        val first = allocate()
        val second = allocate()
        assertNotEquals(first, second)
        assertEquals(setOf("Artist - Title (2).flac", "Artist - Title (3).flac"), reserved)
    }

    @Test
    fun `a file is adopted only when its record belongs to this id`() {
        val own = "file:///Music/BitChord/Artist - Title.flac"
        val saved = mapOf("song-a" to own)
        assertTrue(isOwnedDownload("song-a", own, saved))
        assertFalse(isOwnedDownload("song-b", own, saved))
        assertFalse(isOwnedDownload("song-a", "file:///Music/BitChord/Other.flac", saved))
    }
}
