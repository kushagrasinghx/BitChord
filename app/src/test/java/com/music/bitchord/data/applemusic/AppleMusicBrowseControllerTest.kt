package com.music.bitchord.data.applemusic

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppleMusicBrowseControllerTest {
    @Test
    fun `library collections playlist album tracks and catalog search use real endpoints`() = runTest {
        val account = AppleMusicFakeAccount()
        val source = AppleMusicBrowseFake()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mapper = AppleMusicTrackMapper(AppleMusicMatcher(), { youtubeSong() }, dispatcher)
        val browse = AppleMusicBrowseController(account.session, mapper, source, dispatcher = dispatcher)

        browse.load()
        assertEquals(source.playlistItems, browse.state.value.items)
        browse.load(AppleMusicBrowseSelection.Albums)
        assertEquals(source.albumItems, browse.state.value.items)
        browse.load(AppleMusicBrowseSelection.Artists)
        assertEquals(source.artistItems, browse.state.value.items)
        browse.load(AppleMusicBrowseSelection.ArtistAlbums(source.artistItems.single()))
        assertEquals(source.albumItems, browse.state.value.items)
        browse.load(AppleMusicBrowseSelection.RecentlyAdded)
        assertEquals(source.recentItems, browse.state.value.items)
        browse.load(AppleMusicBrowseSelection.Songs)
        assertEquals(source.trackItems, browse.state.value.tracks.map { it.track })
        browse.select(source.playlistItems.single())
        browse.select(source.albumItems.single())
        browse.search("  Northern Lights  ")
        assertNull(browse.state.value.tracks.single().libraryId)
        assertEquals(
            listOf("playlists", "albums", "artists", "artist:r.artist", "recent", "songs", "playlist:p.real", "album:l.album", "search:us:Northern Lights"),
            source.calls,
        )
        assertFalse(browse.state.value.isLoading)
        assertFalse(browse.state.value.isMatching)
    }

    @Test
    fun `accumulated pages are visible before the last page arrives`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val source = AppleMusicBrowseFake().apply {
            playlistsRequest = { page ->
                page(playlistItems)
                gate.await()
                (playlistItems + AppleMusicPlaylist("p.next", "Next Page")).also(page)
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val browse = AppleMusicBrowseController(
            AppleMusicFakeAccount().session, AppleMusicTrackMapper(AppleMusicMatcher(), { null }, dispatcher), source, dispatcher = dispatcher,
        )
        val work = async { browse.load() }
        runCurrent()
        assertEquals(listOf("p.real"), browse.state.value.items.map { it.id })
        assertTrue(browse.state.value.isLoading)
        gate.complete(Unit)
        work.await()
        assertEquals(listOf("p.real", "p.next"), browse.state.value.items.map { it.id })
        assertFalse(browse.state.value.isLoading)
    }

    @Test
    fun `pending pages and out of order matches progressively replace rows in place`() = runTest {
        val pageGate = CompletableDeferred<Unit>()
        val firstMatch = CompletableDeferred<Unit>()
        val secondMatch = CompletableDeferred<Unit>()
        val source = AppleMusicBrowseFake().apply {
            trackItems = listOf(libraryTrack("i.first"), libraryTrack("i.second", "456"))
            songsRequest = { page -> page(trackItems.take(1)); pageGate.await(); trackItems.also(page) }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mapper = AppleMusicTrackMapper(AppleMusicMatcher(), {
            if (it.id == "i.first") firstMatch.await() else secondMatch.await()
            youtubeSong("yt-${it.id}")
        }, dispatcher)
        val browse = AppleMusicBrowseController(AppleMusicFakeAccount().session, mapper, source, dispatcher = dispatcher)
        val work = async { browse.load(AppleMusicBrowseSelection.Songs) }
        runCurrent()
        assertEquals(1, browse.state.value.tracks.size)
        assertEquals(AppleMusicTrackMatchStatus.PENDING, browse.state.value.tracks.single().status)
        pageGate.complete(Unit)
        runCurrent()
        assertTrue(browse.state.value.isMatching)
        assertFalse(browse.state.value.isLoading)
        secondMatch.complete(Unit)
        runCurrent()
        assertEquals(listOf("am:i.first", "yt-i.second"), browse.state.value.tracks.map { it.song.videoId })
        firstMatch.complete(Unit)
        work.await()
        assertEquals(listOf("yt-i.first", "yt-i.second"), browse.state.value.tracks.map { it.song.videoId })
        assertEquals(2, browse.state.value.matchedCount)
        assertFalse(browse.state.value.isMatching)
    }

    @Test
    fun `account switch cancels and clears state and rejects noncooperative late pages`() = runTest {
        val account = AppleMusicFakeAccount()
        val gate = CompletableDeferred<Unit>()
        val source = AppleMusicBrowseFake().apply {
            songsRequest = { page ->
                withContext(NonCancellable) { gate.await(); trackItems.also(page) }
            }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val matcher = AppleMusicMatcher()
        val browse = AppleMusicBrowseController(account.session, AppleMusicTrackMapper(matcher, { youtubeSong() }, dispatcher), source, dispatcher = dispatcher)
        val work = async { browse.load(AppleMusicBrowseSelection.Songs) }
        runCurrent()
        account.key = "account-b"
        account.session.refresh()
        assertTrue(browse.state.value.tracks.isEmpty())
        assertEquals(1L, browse.state.value.account.revision)
        gate.complete(Unit)
        runCurrent()
        assertTrue(work.isCancelled)
        assertTrue(browse.state.value.tracks.isEmpty())
        assertNull(matcher.cached("us", "yt-song-001"))
    }

    @Test
    fun `credential identity is checked even if caller forgot the account change notification`() = runTest {
        val account = AppleMusicFakeAccount()
        val gate = CompletableDeferred<Unit>()
        val source = AppleMusicBrowseFake().apply { songsRequest = { page -> gate.await(); trackItems.also(page) } }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val browse = AppleMusicBrowseController(account.session, AppleMusicTrackMapper(AppleMusicMatcher(), { null }, dispatcher), source, dispatcher = dispatcher)
        val work = async { browse.load(AppleMusicBrowseSelection.Songs) }
        runCurrent()
        account.key = "account-b"
        gate.complete(Unit)
        runCurrent()
        assertTrue(work.isCancelled)
        assertTrue(browse.state.value.tracks.isEmpty())
        assertEquals(1L, browse.state.value.account.revision)
    }

    @Test
    fun `a new selection cancels old responses without changing the new page`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val source = AppleMusicBrowseFake().apply {
            songsRequest = { page -> withContext(NonCancellable) { gate.await(); trackItems.also(page) } }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val browse = AppleMusicBrowseController(AppleMusicFakeAccount().session, AppleMusicTrackMapper(AppleMusicMatcher(), { null }, dispatcher), source, dispatcher = dispatcher)
        val old = async { browse.load(AppleMusicBrowseSelection.Songs) }
        runCurrent()
        browse.load(AppleMusicBrowseSelection.Albums)
        gate.complete(Unit)
        runCurrent()
        assertTrue(old.isCancelled)
        assertEquals(AppleMusicBrowseSelection.Albums, browse.state.value.selection)
        assertEquals(source.albumItems, browse.state.value.items)
        assertTrue(browse.state.value.tracks.isEmpty())
    }

    @Test
    fun `Favorite Songs never guesses a name favorite flag personal mix or id`() = runTest {
        val source = AppleMusicBrowseFake().apply {
            playlistItems = listOf(AppleMusicPlaylist("p.favorite", "Favorite Songs", isFavorite = true, playlistType = "personal-mix"))
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val browse = AppleMusicBrowseController(AppleMusicFakeAccount().session, AppleMusicTrackMapper(AppleMusicMatcher(), { null }, dispatcher), source, dispatcher = dispatcher)
        browse.load(AppleMusicBrowseSelection.FavoriteSongs)
        assertEquals(AppleMusicBrowseError.FAVORITES_UNAVAILABLE, browse.state.value.error)
        assertNull(browse.state.value.favoriteSongsPlaylist)
        assertEquals(source.playlistItems, browse.state.value.items)
        assertEquals(listOf("playlists"), source.calls)
    }

    @Test
    fun `Favorite Songs requires verification and actual membership in this accounts library`() = runTest {
        val source = AppleMusicBrowseFake()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mapper = AppleMusicTrackMapper(AppleMusicMatcher(), { null }, dispatcher)
        val browse = AppleMusicBrowseController(
            AppleMusicFakeAccount().session, mapper, source, AppleMusicFavoriteSongsVerifier { "p.real" }, dispatcher,
        )
        browse.load(AppleMusicBrowseSelection.FavoriteSongs)
        assertEquals("p.real", browse.state.value.favoriteSongsPlaylist?.id)
        assertEquals(listOf("playlists", "playlist:p.real"), source.calls)
        assertEquals(1, browse.state.value.unmatchedCount)

        val unverified = AppleMusicBrowseController(
            AppleMusicFakeAccount().session, mapper, source, AppleMusicFavoriteSongsVerifier { "p.not-in-library" }, dispatcher,
        )
        unverified.load(AppleMusicBrowseSelection.FavoriteSongs)
        assertEquals(AppleMusicBrowseError.FAVORITES_UNAVAILABLE, unverified.state.value.error)
        assertEquals(1, source.calls.count { it.startsWith("playlist:") })
    }

    @Test
    fun `signed out library does not call API but explicit storefront catalog search is allowed`() = runTest {
        val account = AppleMusicFakeAccount().apply { key = null; storefront = null }
        val source = AppleMusicBrowseFake()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val browse = AppleMusicBrowseController(account.session, AppleMusicTrackMapper(AppleMusicMatcher(), { null }, dispatcher), source, dispatcher = dispatcher)
        browse.load(AppleMusicBrowseSelection.Songs)
        assertEquals(AppleMusicBrowseError.NOT_CONNECTED, browse.state.value.error)
        assertTrue(source.calls.isEmpty())
        browse.search("Song")
        assertEquals(AppleMusicBrowseError.STOREFRONT_REQUIRED, browse.state.value.error)
        assertTrue(source.calls.isEmpty())
        browse.search("Song", "gb")
        assertEquals(listOf("search:gb:Song"), source.calls)
        assertNull(browse.state.value.error)
    }

    @Test
    fun `partial data survives a redacted load failure and retry replaces it`() = runTest {
        val source = AppleMusicBrowseFake().apply {
            playlistsRequest = { page -> page(playlistItems); error("token-private") }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val browse = AppleMusicBrowseController(AppleMusicFakeAccount().session, AppleMusicTrackMapper(AppleMusicMatcher(), { null }, dispatcher), source, dispatcher = dispatcher)
        browse.load()
        assertEquals(AppleMusicBrowseError.LOAD_FAILED, browse.state.value.error)
        assertEquals(source.playlistItems, browse.state.value.items)
        assertFalse(browse.state.value.isLoading)
        source.playlistsRequest = { source.playlistItems.also(it) }
        browse.load()
        assertNull(browse.state.value.error)
    }
}
