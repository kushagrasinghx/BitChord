package com.music.bitchord.playback

import com.music.bitchord.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContextRegistryTest {
    @Test
    fun registerDeduplicatesByVideoIdAndSnapshotIsImmutable() {
        val initial = mutableListOf(song("a"), song("a", title = "later"), song("b"))
        val registry = ContextRegistry()

        val registered = registry.register(
            contextToken = "context-a",
            sourceKey = "playlist:one",
            browseId = "VLone",
            songs = initial,
        )
        initial += song("c")

        assertEquals(listOf("a", "b"), registered.songs.map(Song::videoId))
        assertEquals(listOf("a", "b"), registry.snapshot()!!.songs.map(Song::videoId))
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (registered.songs as MutableList<Song>) += song("cannot-mutate")
        }
    }

    @Test
    fun continuationGrowthDeduplicatesPreservesOrderAndNotifiesOnce() {
        val growth = mutableListOf<ContextSnapshot>()
        val registry = ContextRegistry { growth += it }
        registry.register("context-a", "playlist:one", "VLone", listOf(song("a")), "page-1")

        val request = assertNotNull(registry.claimContinuation())
        val result = registry.complete(
            request,
            ContinuationPage(
                songs = listOf(song("a"), song("b"), song("b")),
                continuationToken = "page-2",
            ),
        )

        assertEquals(ContinuationResultStatus.COMPLETED, result.status)
        assertEquals(listOf("a", "b"), result.snapshot!!.songs.map(Song::videoId))
        assertEquals(ContinuationState.AVAILABLE, result.snapshot.continuationState)
        assertEquals(listOf("b"), result.addedSongs.map(Song::videoId))
        assertEquals(1, growth.size)
        assertEquals(2, growth.single().version)
        assertEquals("page-2", registry.claimContinuation()!!.continuationToken)
    }

    @Test
    fun staleCompletionAfterNewRegistrationCannotChangeNewContext() {
        val registry = ContextRegistry()
        registry.register("old", "playlist:old", "VLold", listOf(song("old")), "old-page")
        val oldRequest = assertNotNull(registry.claimContinuation())

        registry.register("new", "playlist:new", "VLnew", listOf(song("new")), null)
        val stale = registry.complete(
            oldRequest,
            ContinuationPage(listOf(song("must-not-append")), "next"),
        )

        assertEquals(ContinuationResultStatus.STALE_REQUEST, stale.status)
        assertEquals(listOf("new"), registry.snapshot()!!.songs.map(Song::videoId))
        assertEquals(ContinuationState.EXHAUSTED, registry.snapshot()!!.continuationState)
    }

    @Test
    fun duplicatePageIsBlockedWithDiagnosticAndCannotLoop() {
        val registry = ContextRegistry()
        registry.register("context-a", "playlist:one", "VLone", listOf(song("a")), "page-1")
        val request = assertNotNull(registry.claimContinuation())

        val rejected = registry.complete(
            request,
            ContinuationPage(listOf(song("a")), "page-2"),
        )

        assertEquals(ContinuationResultStatus.REJECTED_NO_PROGRESS, rejected.status)
        assertEquals(ContinuationDiagnostic.DUPLICATE_PAGE, rejected.diagnostic)
        assertEquals(ContinuationState.BLOCKED, rejected.snapshot!!.continuationState)
        assertEquals(ContinuationDiagnostic.DUPLICATE_PAGE, rejected.snapshot.diagnostic)
        assertNull(registry.claimContinuation())
        assertEquals(listOf("a"), registry.snapshot()!!.songs.map(Song::videoId))
    }

    @Test
    fun emptyPageIsBlockedAsNoProgress() {
        val registry = ContextRegistry()
        registry.register("context-a", "playlist:one", "VLone", listOf(song("a")), "page-1")
        val request = assertNotNull(registry.claimContinuation())

        val rejected = registry.complete(request, ContinuationPage(emptyList(), "page-2"))

        assertEquals(ContinuationResultStatus.REJECTED_NO_PROGRESS, rejected.status)
        assertEquals(ContinuationDiagnostic.NO_PROGRESS, rejected.diagnostic)
        assertEquals(ContinuationState.BLOCKED, registry.snapshot()!!.continuationState)
        assertNull(registry.claimContinuation())
    }

    @Test
    fun repeatedContinuationTokenIsRejectedEvenWhenPageHasNewSong() {
        val registry = ContextRegistry()
        registry.register("context-a", "playlist:one", "VLone", listOf(song("a")), "page-1")
        val request = assertNotNull(registry.claimContinuation())

        val rejected = registry.complete(
            request,
            ContinuationPage(listOf(song("b")), "page-1"),
        )

        assertEquals(ContinuationResultStatus.REJECTED_NO_PROGRESS, rejected.status)
        assertEquals(ContinuationDiagnostic.REPEATED_CONTINUATION, rejected.diagnostic)
        assertEquals(listOf("a"), registry.snapshot()!!.songs.map(Song::videoId))
        assertNull(registry.claimContinuation())
    }

    @Test
    fun onlyOneRequestCanBeInFlightAndFailureAllowsRetry() {
        val registry = ContextRegistry()
        registry.register("context-a", "playlist:one", "VLone", listOf(song("a")), "page-1")

        val first = assertNotNull(registry.claimContinuation())
        assertNull(registry.claimContinuation())

        val failed = registry.fail(first, IllegalStateException("network"))
        assertEquals(ContinuationResultStatus.FAILED, failed.status)
        assertEquals(ContinuationDiagnostic.REQUEST_FAILED, failed.diagnostic)

        val retry = assertNotNull(registry.claimContinuation())
        assertTrue(retry.requestId != first.requestId)
        val completed = registry.complete(retry, ContinuationPage(listOf(song("b")), null))
        assertEquals(ContinuationResultStatus.COMPLETED, completed.status)
        assertEquals(ContinuationState.EXHAUSTED, registry.snapshot()!!.continuationState)
    }

    @Test
    fun appendRejectsStaleContextAndGrowthCallbackCanBeAdded() {
        val growth = mutableListOf<ContextSnapshot>()
        val registry = ContextRegistry()
        val listenerId = registry.addGrowthListener { growth += it }
        registry.register("context-a", "playlist:one", "VLone", listOf(song("a")), null)

        val stale = registry.append("other-context", listOf(song("b")))
        assertEquals(AppendStatus.STALE_CONTEXT, stale.status)
        assertEquals(listOf("a"), registry.snapshot()!!.songs.map(Song::videoId))

        val appended = registry.append("context-a", listOf(song("b"), song("b")))
        assertEquals(AppendStatus.APPENDED, appended.status)
        assertEquals(listOf("a", "b"), registry.snapshot()!!.songs.map(Song::videoId))
        assertEquals(1, growth.size)
        assertTrue(registry.removeGrowthListener(listenerId))
        assertTrue(!registry.removeGrowthListener(listenerId))
    }

    @Test
    fun restoreRoundTripsCanonicalStateAndAssignsFreshVersionWithoutGrowth() {
        val original = ContextRegistry().apply {
            register(
                contextToken = "context-a",
                sourceKey = "playlist:one",
                browseId = "VLone",
                songs = listOf(song("a")),
                continuationToken = "page-1",
            )
        }
        val request = assertNotNull(original.claimContinuation())
        val completed = original.complete(
            request,
            ContinuationPage(listOf(song("b")), "page-2"),
        )
        val persisted = assertNotNull(completed.snapshot)
        assertEquals(listOf("page-1", "page-2"), persisted.seenContinuationTokens)

        val growth = mutableListOf<ContextSnapshot>()
        val restored = ContextRegistry { growth += it }.restore(persisted)

        assertEquals(persisted.contextToken, restored.contextToken)
        assertEquals(persisted.sourceKey, restored.sourceKey)
        assertEquals(persisted.browseId, restored.browseId)
        assertEquals(persisted.songs, restored.songs)
        assertEquals(persisted.continuationToken, restored.continuationToken)
        assertEquals(persisted.continuationState, restored.continuationState)
        assertEquals(persisted.diagnostic, restored.diagnostic)
        assertEquals(persisted.seenContinuationTokens, restored.seenContinuationTokens)
        assertTrue(restored.version != persisted.version)
        assertTrue(growth.isEmpty())
    }

    @Test
    fun restoreMakesInterruptedRequestAvailableWithFreshRequestIdAndVersion() {
        val registry = ContextRegistry()
        registry.register("context-a", "playlist:one", "VLone", listOf(song("a")), "page-1")
        val interrupted = assertNotNull(registry.claimContinuation())
        val persisted = assertNotNull(registry.snapshot())

        val restored = registry.restore(persisted)
        val retry = assertNotNull(registry.claimContinuation())

        assertEquals(ContinuationState.IN_FLIGHT, persisted.continuationState)
        assertEquals(ContinuationState.AVAILABLE, restored.continuationState)
        assertEquals(interrupted.continuationToken, retry.continuationToken)
        assertTrue(retry.requestId != interrupted.requestId)
        assertTrue(retry.version != interrupted.version)
        assertEquals(ContinuationResultStatus.STALE_REQUEST, registry.complete(
            interrupted,
            ContinuationPage(listOf(song("stale")), "page-2"),
        ).status)
        assertEquals(listOf("a"), registry.snapshot()!!.songs.map(Song::videoId))
    }

    @Test
    fun restoreRetainsTerminalStatesAndDiagnostics() {
        val source = ContextRegistry()
        source.register("context-a", "playlist:one", "VLone", listOf(song("a")), "page-1")
        val request = assertNotNull(source.claimContinuation())
        val blocked = assertNotNull(source.complete(
            request,
            ContinuationPage(listOf(song("a")), "page-2"),
        ).snapshot)
        val exhausted = blocked.copy(
            continuationState = ContinuationState.EXHAUSTED,
            continuationToken = "stale-page",
            diagnostic = ContinuationDiagnostic.REQUEST_FAILED,
        )

        val blockedRestored = ContextRegistry().restore(blocked)
        val exhaustedRestored = ContextRegistry().restore(exhausted)

        assertEquals(ContinuationState.BLOCKED, blockedRestored.continuationState)
        assertEquals(ContinuationDiagnostic.DUPLICATE_PAGE, blockedRestored.diagnostic)
        assertEquals(ContinuationState.EXHAUSTED, exhaustedRestored.continuationState)
        assertEquals(ContinuationDiagnostic.REQUEST_FAILED, exhaustedRestored.diagnostic)
        assertEquals("stale-page", exhaustedRestored.continuationToken)
        assertNull(ContextRegistry().apply {
            restore(exhausted)
        }.claimContinuation())
    }

    @Test
    fun restoreRejectsMalformedCanonicalSongsAndContinuationState() {
        val valid = ContextSnapshot(
            contextToken = "context-a",
            sourceKey = "playlist:one",
            browseId = "VLone",
            songs = listOf(song("a")),
            version = 42L,
            continuationToken = "page-1",
            continuationState = ContinuationState.AVAILABLE,
        )
        val registry = ContextRegistry()

        assertFailsWith<IllegalArgumentException> {
            registry.restore(valid.copy(songs = listOf(song("a"), song("a"))))
        }
        assertFailsWith<IllegalArgumentException> {
            registry.restore(valid.copy(songs = listOf(song(""))))
        }
        assertFailsWith<IllegalArgumentException> {
            registry.restore(valid.copy(continuationToken = null))
        }
        assertFailsWith<IllegalArgumentException> {
            registry.restore(valid.copy(continuationState = ContinuationState.IN_FLIGHT, continuationToken = " "))
        }
        assertFailsWith<IllegalArgumentException> {
            registry.restore(valid.copy(seenContinuationTokens = listOf("page-1", "page-1")))
        }
    }

    @Test
    fun restoredCanonicalSongsAreDefensiveAndImmutable() {
        val songs = mutableListOf(song("a"), song("b"))
        val restored = ContextRegistry().restore(
            ContextSnapshot(
                contextToken = "context-a",
                sourceKey = "playlist:one",
                browseId = null,
                songs = songs,
                version = 999L,
                continuationToken = null,
                continuationState = ContinuationState.EXHAUSTED,
            ),
        )
        songs += song("c")

        assertEquals(listOf("a", "b"), restored.songs.map(Song::videoId))
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (restored.songs as MutableList<Song>) += song("cannot-mutate")
        }
    }

    @Test
    fun repeatedContinuationTokenRemainsBlockedAfterRestore() {
        val firstRegistry = ContextRegistry()
        firstRegistry.register("context-a", "playlist:one", "VLone", listOf(song("a")), "page-1")
        val first = assertNotNull(firstRegistry.claimContinuation())
        val progressed = firstRegistry.complete(
            first,
            ContinuationPage(listOf(song("b")), "page-2"),
        )
        val persisted = assertNotNull(progressed.snapshot)

        val restoredRegistry = ContextRegistry()
        restoredRegistry.restore(persisted)
        val retry = assertNotNull(restoredRegistry.claimContinuation())
        val repeated = restoredRegistry.complete(
            retry,
            ContinuationPage(listOf(song("c")), "page-1"),
        )

        assertEquals(ContinuationResultStatus.REJECTED_NO_PROGRESS, repeated.status)
        assertEquals(ContinuationDiagnostic.REPEATED_CONTINUATION, repeated.diagnostic)
        assertEquals(ContinuationState.BLOCKED, repeated.snapshot!!.continuationState)
        assertEquals(listOf("a", "b"), repeated.snapshot.songs.map(Song::videoId))
    }

    private fun song(id: String, title: String = id) = Song(
        videoId = id,
        title = title,
        artist = "Artist",
        thumbnailUrl = null,
    )
}
