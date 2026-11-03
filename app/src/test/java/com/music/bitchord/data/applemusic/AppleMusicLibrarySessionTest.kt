package com.music.bitchord.data.applemusic

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppleMusicLibrarySessionTest {
    @Test
    fun `identity storefront changes and disconnect advance opaque revision without exposing credentials`() {
        val account = AppleMusicFakeAccount()
        assertEquals(AppleMusicLibrarySession.State(true, "us", 0), account.session.state.value)
        assertEquals(0L, account.session.refresh().revision)
        account.key = "private-token-b"
        assertEquals(1L, account.session.refresh().revision)
        account.storefront = " GB "
        assertEquals(AppleMusicLibrarySession.State(true, "gb", 2), account.session.refresh())
        account.key = null
        account.storefront = null
        assertEquals(AppleMusicLibrarySession.State(false, null, 3), account.session.refresh())
        assertFalse(account.session.state.value.toString().contains("private-token"))
    }

    @Test
    fun `credential suppliers are never called with the presentation lock held`() = runTest {
        val executor = Executors.newSingleThreadExecutor()
        lateinit var session: AppleMusicLibrarySession
        var probe = false
        try {
            session = AppleMusicLibrarySession(
                accountKey = {
                    if (probe) executor.submit { session.locked {} }.get(2, TimeUnit.SECONDS)
                    "account-a"
                },
                userStorefront = { "us" },
            )
            probe = true
            session.refresh()
            val requests = AppleMusicLibraryRequests(session)
            requests.run({}, {}) { ticket -> ticket.publish {} }
            requests.close()
            session.close()
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `observing connection changes clears browse and import even while idle`() = runTest {
        var key: String? = "private-token-a"
        val connection = MutableStateFlow(true)
        val session = AppleMusicLibrarySession(backgroundScope, { key }, { if (key != null) "us" else null }, connection)
        val source = AppleMusicBrowseFake()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val mapper = AppleMusicTrackMapper(AppleMusicMatcher(), { youtubeSong() }, dispatcher)
        val browse = AppleMusicBrowseController(session, mapper, source, dispatcher = dispatcher)
        val imports = AppleMusicImportController(session, mapper, source, { title, songs ->
            com.music.bitchord.data.spotify.LocalPlaylist("saved", title, songs)
        }, dispatcher)
        browse.load(AppleMusicBrowseSelection.Songs)
        assertNotNull(imports.import(AppleMusicImportSource.LibrarySongs))
        key = null
        connection.value = false
        runCurrent()
        assertFalse(browse.state.value.account.connected)
        assertTrue(browse.state.value.tracks.isEmpty())
        assertNull(imports.state.value.result)
        assertEquals(AppleMusicImportPhase.IDLE, imports.state.value.phase)
    }

    @Test
    fun `session close cancels current work clears state and controller close is idempotent`() = runTest {
        val started = CompletableDeferred<Unit>()
        val source = AppleMusicBrowseFake().apply {
            songsRequest = { started.complete(Unit); awaitCancellation() }
        }
        val account = AppleMusicFakeAccount()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val browse = AppleMusicBrowseController(account.session, AppleMusicTrackMapper(AppleMusicMatcher(), { null }, dispatcher), source, dispatcher = dispatcher)
        val work = async { browse.load(AppleMusicBrowseSelection.Songs) }
        started.await()
        account.session.close()
        runCurrent()
        assertTrue(work.isCancelled)
        assertTrue(browse.state.value.tracks.isEmpty())
        assertFalse(browse.state.value.account.connected)
        browse.close()
        browse.close()
        account.session.close()
    }
}
