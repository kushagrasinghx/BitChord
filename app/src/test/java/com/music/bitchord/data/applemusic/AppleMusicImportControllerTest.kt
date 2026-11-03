package com.music.bitchord.data.applemusic

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.spotify.LocalPlaylist
import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppleMusicImportControllerTest {
    private class Destination : AppleMusicImportDestination {
        val saved = mutableListOf<LocalPlaylist>()
        override fun save(title: String, songs: List<Song>): LocalPlaylist =
            LocalPlaylist("local-${saved.size}", title, songs).also { saved += it }
    }

    @Test
    fun `only validated Apple HTTPS playlist links expose a storefront and real id`() {
        for (url in listOf(
            "https://music.apple.com/us/playlist/a-playlist/pl.real?ls=1&app=music",
            "https://music.apple.com/us/playlist/pl.real",
            "  https://MUSIC.APPLE.COM:443/US/playlist/a%20title/pl.real  ",
        )) assertEquals(AppleMusicPlaylistLink("us", "pl.real"), AppleMusicPlaylistLink.parse(url))
        assertEquals(
            AppleMusicPlaylistLink("gb", "pl.u-AbCdEf123"),
            AppleMusicPlaylistLink.parse("https://music.apple.com/gb/playlist/collection/pl.u-AbCdEf123"),
        )
        for (url in listOf(
            "", "pl.real", "http://music.apple.com/us/playlist/name/pl.real",
            "https://music.apple.com.evil.example/us/playlist/name/pl.real",
            "https://evil.example/us/playlist/name/pl.real",
            "https://music.apple.com@evil.example/us/playlist/name/pl.real",
            "https://user@music.apple.com/us/playlist/name/pl.real",
            "https://music.apple.com:444/us/playlist/name/pl.real",
            "https://music.apple.com/us/album/name/pl.real",
            "https://music.apple.com/us/song/name/123",
            "https://music.apple.com/playlist/name/pl.real",
            "https://music.apple.com/en-US/playlist/name/pl.real",
            "https://music.apple.com/us/playlist/name/p.library",
            "https://music.apple.com/us/playlist/name/pl.real/extra",
            "https://music.apple.com/us/playlist/name/pl.real/",
            "https://music.apple.com/us/playlist/name/pl.%2Fescape",
            "https://music.apple.com/us/playlist/name/pl.real#fragment",
            "https://music.apple.com/us//playlist/pl.real",
        )) assertNull(url, AppleMusicPlaylistLink.parse(url))
    }

    @Test
    fun `real library playlist imports YouTube matches and numeric catalog misses but skips missing uploads`() = runTest {
        val source = AppleMusicBrowseFake().apply {
            trackItems = listOf(
                libraryTrack("i.match", "123"),
                libraryTrack("i.apple", "456"),
                libraryTrack("123456", null),
                libraryTrack("i.match", "123"),
            )
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val matcher = AppleMusicMatcher()
        val mapper = AppleMusicTrackMapper(matcher, {
            if (it.id == "i.match") youtubeSong() else null
        }, dispatcher)
        val destination = Destination()
        val imports = AppleMusicImportController(AppleMusicFakeAccount().session, mapper, source, destination, dispatcher)
        val result = imports.import(AppleMusicImportSource.LibraryPlaylist(source.playlistItems.single()))!!

        assertEquals(listOf("playlist:p.real"), source.calls)
        assertEquals("Road Trip", result.playlist?.title)
        assertEquals(listOf("yt-song-001", "am-miss:456", "yt-song-001"), result.playlist?.songs?.map { it.videoId })
        assertEquals(4, result.totalTracks)
        assertEquals(2, result.matchedCount)
        assertEquals(2, result.unmatchedCount)
        assertEquals(1, result.appleOnlyCount)
        assertEquals(1, result.unavailableCount)
        assertEquals(3, result.savedCount)
        assertEquals(listOf("i.apple", "123456"), result.unmatchedTracks.map { it.id })
        assertEquals(1, destination.saved.size)
        assertEquals("123", matcher.cachedCatalogId("us", "yt-song-001"))
        assertEquals(AppleMusicImportPhase.COMPLETE, imports.state.value.phase)
        assertEquals(4, imports.state.value.resolvedCount)
    }

    @Test
    fun `library songs import uses authenticated songs endpoint and caller title`() = runTest {
        val source = AppleMusicBrowseFake()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val destination = Destination()
        val imports = AppleMusicImportController(
            AppleMusicFakeAccount().session, AppleMusicTrackMapper(AppleMusicMatcher(), { youtubeSong() }, dispatcher), source, destination, dispatcher,
        )
        val result = imports.import(AppleMusicImportSource.LibrarySongs, "  My Songs  ")!!
        assertEquals(listOf("songs"), source.calls)
        assertEquals("My Songs", result.playlist?.title)
        assertEquals(0, result.unmatchedCount)
        assertEquals(1, destination.saved.size)
    }

    @Test
    fun `public playlist import uses link storefront without a user session and keeps catalog origin`() = runTest {
        val account = AppleMusicFakeAccount().apply { key = null; storefront = null }
        val source = AppleMusicBrowseFake()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val matcher = AppleMusicMatcher()
        val imports = AppleMusicImportController(
            account.session, AppleMusicTrackMapper(matcher, { youtubeSong() }, dispatcher), source, Destination(), dispatcher,
        )
        val result = imports.import(AppleMusicImportSource.PlaylistUrl("https://music.apple.com/gb/playlist/shared/pl.real"))!!
        assertEquals(listOf("catalog:gb:pl.real", "catalog-tracks:gb:pl.real"), source.calls)
        assertEquals("Shared Playlist", result.playlist?.title)
        assertNull(result.tracks.single().libraryId)
        assertEquals("123", matcher.cachedCatalogId("gb", "yt-song-001"))
        assertNull(matcher.cached("us", "yt-song-001"))
    }

    @Test
    fun `invalid links signed out library and absent catalog playlist never save or fabricate ids`() = runTest {
        val account = AppleMusicFakeAccount().apply { key = null }
        val source = AppleMusicBrowseFake()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val destination = Destination()
        val imports = AppleMusicImportController(account.session, AppleMusicTrackMapper(AppleMusicMatcher(), { null }, dispatcher), source, destination, dispatcher)
        assertNull(imports.import(AppleMusicImportSource.LibrarySongs))
        assertEquals(AppleMusicImportError.NOT_CONNECTED, imports.state.value.error)
        assertNull(imports.import(AppleMusicImportSource.PlaylistUrl("https://evil.example/us/playlist/name/pl.real")))
        assertEquals(AppleMusicImportError.INVALID_LINK, imports.state.value.error)
        assertTrue(source.calls.isEmpty())
        source.catalogPlaylistItem = null
        assertNull(imports.import(AppleMusicImportSource.PlaylistUrl("https://music.apple.com/us/playlist/name/pl.real")))
        assertEquals(AppleMusicImportError.PLAYLIST_NOT_FOUND, imports.state.value.error)
        assertEquals(listOf("catalog:us:pl.real"), source.calls)
        assertTrue(destination.saved.isEmpty())
    }

    @Test
    fun `unavailable only or empty imports report counts without saving empty playlists`() = runTest {
        val source = AppleMusicBrowseFake().apply { trackItems = listOf(AppleMusicTrack("123456")) }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val destination = Destination()
        val imports = AppleMusicImportController(
            AppleMusicFakeAccount().session, AppleMusicTrackMapper(AppleMusicMatcher(), { null }, dispatcher), source, destination, dispatcher,
        )
        val result = imports.import(AppleMusicImportSource.LibrarySongs)!!
        assertNull(result.playlist)
        assertEquals(1, result.unmatchedCount)
        assertEquals(1, result.unavailableCount)
        assertEquals(0, result.savedCount)
        source.trackItems = emptyList()
        val empty = imports.import(AppleMusicImportSource.LibrarySongs)!!
        assertEquals(0, empty.totalTracks)
        assertNull(empty.playlist)
        assertTrue(destination.saved.isEmpty())
    }

    @Test
    fun `loading progress is emitted and account switch during matching cannot commit`() = runTest {
        val account = AppleMusicFakeAccount()
        val pageGate = CompletableDeferred<Unit>()
        val matchGate = CompletableDeferred<Unit>()
        val source = AppleMusicBrowseFake().apply {
            songsRequest = { page -> page(trackItems); pageGate.await(); trackItems }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val destination = Destination()
        val matcher = AppleMusicMatcher()
        val mapper = AppleMusicTrackMapper(matcher, {
            withContext(NonCancellable) { matchGate.await(); youtubeSong() }
        }, dispatcher)
        val imports = AppleMusicImportController(account.session, mapper, source, destination, dispatcher)
        val work = async { imports.import(AppleMusicImportSource.LibrarySongs) }
        runCurrent()
        assertEquals(1, imports.state.value.loadedCount)
        assertEquals(AppleMusicImportPhase.LOADING, imports.state.value.phase)
        pageGate.complete(Unit)
        runCurrent()
        assertEquals(AppleMusicImportPhase.MATCHING, imports.state.value.phase)
        account.key = "account-b"
        account.session.refresh()
        matchGate.complete(Unit)
        runCurrent()
        assertTrue(work.isCancelled)
        assertTrue(destination.saved.isEmpty())
        assertNull(imports.state.value.result)
        assertEquals(AppleMusicImportPhase.IDLE, imports.state.value.phase)
        assertNull(matcher.cached("us", "yt-song-001"))
    }

    @Test
    fun `explicit cancellation and save failures are distinct and never publish a result`() = runTest {
        val source = AppleMusicBrowseFake()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mapper = AppleMusicTrackMapper(AppleMusicMatcher(), { youtubeSong() }, dispatcher)
        val imports = AppleMusicImportController(AppleMusicFakeAccount().session, mapper, source, { _, _ -> error("secret") }, dispatcher)
        assertNull(imports.import(AppleMusicImportSource.LibrarySongs))
        assertEquals(AppleMusicImportError.SAVE_FAILED, imports.state.value.error)
        assertFalse(imports.state.value.isRunning)
        val gate = CompletableDeferred<Unit>()
        source.songsRequest = { page -> gate.await(); source.trackItems.also(page) }
        val work = async { imports.import(AppleMusicImportSource.LibrarySongs) }
        runCurrent()
        imports.cancel()
        runCurrent()
        assertTrue(work.isCancelled)
        assertEquals(AppleMusicImportPhase.IDLE, imports.state.value.phase)
        assertNull(imports.state.value.result)
    }

    @Test
    fun `metadata lookup matching and destination writes all run on the supplied IO dispatcher`() = runBlocking {
        val callingThread = Thread.currentThread()
        val touched = mutableListOf<Thread>()
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { io ->
            val source = AppleMusicBrowseFake().apply {
                songsRequest = { page -> touched += Thread.currentThread(); trackItems.also(page) }
            }
            val mapper = AppleMusicTrackMapper(AppleMusicMatcher(), { touched += Thread.currentThread(); youtubeSong() }, io)
            val imports = AppleMusicImportController(AppleMusicFakeAccount().session, mapper, source, { title, songs ->
                touched += Thread.currentThread()
                LocalPlaylist("saved", title, songs)
            }, io)
            assertNotNull(imports.import(AppleMusicImportSource.LibrarySongs))
        }
        assertEquals(3, touched.size)
        assertTrue(touched.none { it === callingThread })
    }
}
