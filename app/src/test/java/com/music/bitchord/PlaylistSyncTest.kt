package com.music.bitchord

import com.music.bitchord.data.YtMusicRepository.SongPage
import com.music.bitchord.data.model.Song
import com.music.bitchord.download.Downloads
import com.music.bitchord.download.PlaylistSync
import com.music.bitchord.download.SavedCollection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What decides which tracks a playlist check fetches. All of it fails quietly: a
 * listing cut short is a song that never arrives, and a wrong answer to "is this
 * new" brings back a song the listener deleted.
 */
class PlaylistSyncTest {

    private fun song(id: String) = Song(videoId = id, title = id, artist = "Artist", thumbnailUrl = null)

    private fun songs(vararg ids: String) = ids.map(::song)

    private fun page(vararg ids: String, next: String? = null, suggested: List<Song> = emptyList()) =
        Result.success(SongPage(songs(*ids), next, suggested))

    private fun record(id: String = "VLPL1", vararg ids: String, playlist: Boolean = true) =
        SavedCollection(id = id, title = id, playlist = playlist, videoIds = ids.toList())

    private fun ids(list: List<Song>) = list.map { it.videoId }

    @Test
    fun `a listing follows every page to the end`() = runTest {
        val pages = mapOf("t2" to page("c", "d", next = "t3"), "t3" to page("e"))
        val listing = PlaylistSync.readWholePlaylist(
            first = { page("a", "b", next = "t2") },
            more = { pages.getValue(it) },
        )
        assertEquals(listOf("a", "b", "c", "d", "e"), ids(listing.songs))
        assertTrue(listing.complete)
    }

    @Test
    fun `a page that fails keeps what came before and says it is incomplete`() = runTest {
        val listing = PlaylistSync.readWholePlaylist(
            first = { page("a", next = "t2") },
            more = { Result.failure(IllegalStateException("offline")) },
        )
        assertEquals(listOf("a"), ids(listing.songs))
        assertFalse(listing.complete)
    }

    @Test
    fun `a failed first page is incomplete, not an empty playlist`() = runTest {
        val listing = PlaylistSync.readWholePlaylist(
            first = { Result.failure(IllegalStateException("offline")) },
            more = { error("nothing to continue") },
        )
        assertTrue(listing.songs.isEmpty())
        assertFalse(listing.complete)
    }

    @Test
    fun `a feed that loops back on itself ends instead of running forever`() = runTest {
        var requests = 0
        val listing = PlaylistSync.readWholePlaylist(
            first = { page("a", next = "t") },
            more = {
                requests++
                page("a", next = "t")
            },
        )
        assertEquals(1, requests)
        assertEquals(listOf("a"), ids(listing.songs))
        assertTrue(listing.complete)
    }

    @Test
    fun `suggested tracks are not part of the playlist`() = runTest {
        val listing = PlaylistSync.readWholePlaylist(
            first = { page("a", next = "t2", suggested = songs("x")) },
            more = { page("b", suggested = songs("y")) },
        )
        assertEquals(listOf("a", "b"), ids(listing.songs))
    }

    @Test
    fun `only tracks the record has never named are new`() {
        // "b" was downloaded and later deleted, so it is named but not on disk.
        val fresh = PlaylistSync.newTracks(songs("a", "b", "c"), record("VLPL1", "a", "b"))
        assertEquals(listOf("c"), ids(fresh))
    }

    @Test
    fun `a track that failed is left out of the record so it is tried again`() {
        val listing = songs("a", "b", "c")
        val order = PlaylistSync.listingToRecord(listing, record("VLPL1", "a"), handled = setOf("c"))
        assertEquals(listOf("a", "c"), ids(order))
    }

    @Test
    fun `only a YouTube playlist is checked, under the id YouTube answers to`() {
        assertEquals("VLPL1", PlaylistSync.syncableBrowseId(record("VLPL1")))
        assertEquals("VLPL1", PlaylistSync.syncableBrowseId(record("PL1")))
        assertNull(PlaylistSync.syncableBrowseId(record("MPREb_1", playlist = false)))
        assertNull(PlaylistSync.syncableBrowseId(record("VLOLAK1", playlist = false)))
        assertNull(PlaylistSync.syncableBrowseId(record("local:playlist:sp_local_1")))
        assertNull(PlaylistSync.syncableBrowseId(record("spotify:playlist:1")))
    }

    @Test
    fun `deleting a release keeps the tracks another release lists`() {
        val collections = mapOf(
            "one" to record("one", "a", "b", "c"),
            "two" to record("two", "b"),
        )
        val saved = mapOf("a" to "file:a", "b" to "file:b", "c" to "file:c")
        assertEquals(listOf("a", "c"), Downloads.tracksOnlyIn("one", collections, saved))
    }

    @Test
    fun `one file under two ids counts as shared`() {
        // "video" was swapped for "track" on the way down: both name one file.
        val collections = mapOf(
            "one" to record("one", "video", "a"),
            "two" to record("two", "track"),
        )
        val saved = mapOf("video" to "file:x", "track" to "file:x", "a" to "file:a")
        assertEquals(listOf("a"), Downloads.tracksOnlyIn("one", collections, saved))
    }
}
