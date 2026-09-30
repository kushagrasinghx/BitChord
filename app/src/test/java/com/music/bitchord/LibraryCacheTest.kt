package com.music.bitchord

import com.music.bitchord.data.Cached
import com.music.bitchord.data.LibraryCache
import com.music.bitchord.data.LibraryMetadataStore
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UserPlaylist
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LibraryCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private val scope = "account-a:personal"
    private val fetchedAt = 1_000_000L
    private val song = Song("song-1", "First", "Artist", "https://example.com/art.jpg", setVideoId = "entry-1")
    private val page = YtMusicRepository.SongPage(
        songs = listOf(song, song.copy(videoId = "song-2", title = "Second", setVideoId = "entry-2")),
        continuation = null,
        header = InnertubeParser.BrowseHeader("My playlist", "Two tracks", "https://example.com/playlist.jpg"),
        owned = true,
    )
    private fun store() = LibraryMetadataStore(temporary.root) { fetchedAt }

    @Test fun `download and regular routes share a playlist identity`() {
        assertEquals("VLPL123", LibraryCache.canonicalPlaylistId("local:playlist:VLPL123"))
        assertEquals("VLPL123", LibraryCache.canonicalPlaylistId("local:playlist:PL123"))
        assertEquals("VLPL123", LibraryCache.canonicalPlaylistId("PL123"))
        assertEquals("VLRD123", LibraryCache.canonicalPlaylistId("RD123"))
        assertEquals("local:all", LibraryCache.canonicalPlaylistId("local:all"))
        assertEquals("MPREb123", LibraryCache.canonicalPlaylistId("MPREb123"))
    }

    @Test fun `freshness expires but stale content remains available`() {
        val cached = Cached(page, fetchedAt)
        assertTrue(cached.isFresh(fetchedAt))
        assertTrue(cached.isFresh(fetchedAt + LibraryCache.REFRESH_INTERVAL_MS - 1))
        assertFalse(cached.isFresh(fetchedAt + LibraryCache.REFRESH_INTERVAL_MS))
        assertFalse(cached.isFresh(fetchedAt - 1))
        assertFalse(Cached(page, 0).isFresh(fetchedAt))
        assertEquals(page, cached.value)
    }

    @Test fun `metadata roundtrips without losing playlist order or entry IDs`() = runBlocking {
        val first = store()
        val card = ShelfItem("My playlist", "Two tracks", page.header?.thumbnailUrl, null, "VLPL123")
        val library = LibraryPage(listOf(song), listOf(song), listOf(HomeShelf("Playlists", listOf(card))))
        val playlists = listOf(UserPlaylist("PL123", "My playlist", "Two tracks", page.header?.thumbnailUrl))
        first.putLibrary(scope, library)
        first.putPlaylists(scope, playlists)
        first.putPlaylist(scope, "VLPL123", page)

        val reopened = store()
        assertNull(reopened.library(scope))
        reopened.load(scope)
        assertEquals(library, reopened.library(scope)?.value)
        assertEquals(playlists, reopened.playlists(scope)?.value)
        assertEquals(page, reopened.playlist(scope, "local:playlist:PL123")?.value)
        assertEquals(fetchedAt, reopened.playlist(scope, "VLPL123")?.fetchedAt)
    }

    @Test fun `accounts and profiles have isolated snapshots after restart`() = runBlocking {
        val first = store()
        first.putPlaylist(scope, "VLPL123", page)
        first.putPlaylist("account-a:brand", "VLPL123", page.copy(songs = listOf(song.copy(title = "Brand"))))
        val reopened = store()
        listOf(scope, "account-a:brand", "account-b:personal").forEach { reopened.load(it) }
        assertEquals("First", reopened.playlist(scope, "VLPL123")?.value?.songs?.first()?.title)
        assertEquals("Brand", reopened.playlist("account-a:brand", "VLPL123")?.value?.songs?.single()?.title)
        assertNull(reopened.playlist("account-b:personal", "VLPL123"))
    }

    @Test fun `partial network pages cannot replace a complete snapshot`() = runBlocking {
        val first = store()
        first.putPlaylist(scope, "VLPL123", page)
        val failure = runCatching {
            first.putPlaylist(scope, "VLPL123", page.copy(songs = listOf(song), continuation = "next-page"))
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals(page, first.playlist(scope, "VLPL123")?.value)
        val reopened = store()
        reopened.load(scope)
        assertEquals(page, reopened.playlist(scope, "VLPL123")?.value)
    }

    @Test fun `invalidating keeps content across restart while deletion removes it`() = runBlocking {
        val first = store()
        val library = LibraryPage(listOf(song), emptyList(), emptyList())
        val playlists = listOf(UserPlaylist("PL123", "My playlist", "", null))
        first.putLibrary(scope, library)
        first.putPlaylists(scope, playlists)
        first.putPlaylist(scope, "VLPL123", page)
        first.invalidateLibrary(scope)
        first.invalidatePlaylist(scope, "local:playlist:PL123")
        val reopened = store()
        reopened.load(scope)
        assertEquals(library, reopened.library(scope)?.value)
        assertEquals(playlists, reopened.playlists(scope)?.value)
        assertEquals(page, reopened.playlist(scope, "VLPL123")?.value)
        assertFalse(reopened.library(scope)!!.isFresh(fetchedAt))
        assertFalse(reopened.playlists(scope)!!.isFresh(fetchedAt))
        assertFalse(reopened.playlist(scope, "VLPL123")!!.isFresh(fetchedAt))
        reopened.removePlaylist(scope, "PL123")
        assertNull(reopened.playlist(scope, "VLPL123"))
        assertNotNull(reopened.library(scope))
    }

    @Test fun `local locations and transient continuation tokens are not cached`() = runBlocking {
        val localSong = song.copy(localUri = "file:///private/downloads/song.mp3", localPath = "/private/downloads/song.mp3",
            localDateAddedSeconds = 10, downloadFormat = "LOSSLESS", queueEntryId = "playing-entry")
        val first = store()
        first.putLibrary(scope, LibraryPage(listOf(localSong), emptyList(), emptyList(), likedContinuation = "expired-token"))
        first.putPlaylist(scope, "VLPL123", page.copy(songs = listOf(localSong)))
        val reopened = store()
        reopened.load(scope)
        assertNull(reopened.library(scope)?.value?.likedContinuation)
        val restored = reopened.playlist(scope, "VLPL123")!!.value.songs.single()
        assertEquals(song, restored)
        assertTrue(temporary.root.listFiles()!!.all { !it.readText().contains("private/downloads") })
    }

    @Test fun `corrupt cache is ignored and next successful snapshot repairs it`() = runBlocking {
        val first = store()
        first.putPlaylist(scope, "VLPL123", page)
        temporary.root.listFiles()!!.single().writeText("{broken")
        val reopened = store()
        reopened.load(scope)
        assertNull(reopened.playlist(scope, "VLPL123"))
        reopened.putPlaylist(scope, "VLPL123", page)
        val repaired = store()
        repaired.load(scope)
        assertEquals(page, repaired.playlist(scope, "VLPL123")?.value)
    }

    @Test fun `clearing one account preserves another`() = runBlocking {
        val first = store()
        first.putPlaylist(scope, "VLPL123", page)
        first.putPlaylist("account-b:personal", "VLPL123", page)
        first.clear(scope)
        val reopened = store()
        reopened.load(scope)
        reopened.load("account-b:personal")
        assertNull(reopened.playlist(scope, "VLPL123"))
        assertEquals(page, reopened.playlist("account-b:personal", "VLPL123")?.value)
    }

    @Test fun `disabling online cache preserves only downloaded playlist metadata`() = runBlocking {
        val first = store()
        first.putLibrary(scope, LibraryPage(listOf(song), emptyList(), emptyList()))
        first.putPlaylists(scope, listOf(UserPlaylist("PL123", "My playlist", "", null)))
        first.putPlaylist(scope, "VLPL123", page)
        first.putPlaylist(scope, "VLPL456", page)
        first.clearOnlineLibrary(scope, setOf("local:playlist:PL123"))
        val reopened = store()
        reopened.load(scope)
        assertNull(reopened.library(scope))
        assertNull(reopened.playlists(scope))
        assertEquals(page, reopened.playlist(scope, "VLPL123")?.value)
        assertNull(reopened.playlist(scope, "VLPL456"))
    }
}
