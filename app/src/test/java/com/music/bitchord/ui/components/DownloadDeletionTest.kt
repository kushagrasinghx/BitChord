package com.music.bitchord.ui.components

import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a pending deletion promises to remove.
 *
 * The dialog can only be as safe as the records handed to it: if this model
 * picked up the whole page, or two ids for one track, confirming a single
 * row's deletion would take something else with it. These tests pin the exact
 * targets and the wording the multi-track warning is built from.
 */
class DownloadDeletionTest {

    private fun song(id: String, title: String = id, artist: String = "Artist") =
        Song(videoId = id, title = title, artist = artist, thumbnailUrl = null)

    @Test
    fun `a single track names exactly its own record`() {
        val deletion = DownloadDeletion(listOf(song("a", "Track A", "Artist A")))

        assertEquals(listOf("a"), deletion.videoIds)
        assertFalse(deletion.isCollection)
        assertFalse(deletion.isBulk)
    }

    @Test
    fun `a selection deletes each selected record once, in order`() {
        val deletion = DownloadDeletion(listOf(song("a"), song("b"), song("a")))

        assertEquals(listOf("a", "b"), deletion.videoIds)
        assertTrue(deletion.isBulk)
    }

    @Test
    fun `a repeated id is still one file and gets the single-track warning`() {
        val deletion = DownloadDeletion(listOf(song("a"), song("a")))

        assertEquals(listOf("a"), deletion.videoIds)
        assertFalse(deletion.isBulk)
    }

    @Test
    fun `a release carries its collection id and its own wording`() {
        val deletion = DownloadDeletion(
            songs = emptyList(),
            collectionId = "album-1",
            collectionTitle = "An Album",
            collectionSubtitle = "An Artist",
        )

        assertTrue(deletion.isCollection)
        assertTrue(deletion.isBulk)
        assertTrue(deletion.videoIds.isEmpty())
    }

    @Test
    fun `a multi-track warning lists three names and then an ellipsis`() {
        val lines = downloadDeletionLines(
            listOf(
                song("a", "A", "Artist A"),
                song("b", "B", "Artist B"),
                song("c", "C", "Artist C"),
                song("d", "D", "Artist D"),
            ),
        )

        assertEquals("A — Artist A\nB — Artist B\nC — Artist C\n…", lines)
    }

    @Test
    fun `a short selection has no ellipsis and repeats no track`() {
        val lines = downloadDeletionLines(
            listOf(
                song("a", "A", "Artist A"),
                song("a", "A", "Artist A"),
                song("b", "B", "Artist B"),
            ),
        )

        assertEquals("A — Artist A\nB — Artist B", lines)
    }

    @Test
    fun `blank fields are left out rather than printed empty`() {
        assertEquals("A", downloadDeletionLines(listOf(song("a", "A", ""))))
        assertEquals("Artist A", downloadDeletionLines(listOf(song("a", "", "Artist A"))))
    }
}
