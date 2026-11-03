package com.music.bitchord.data.applemusic

import com.music.bitchord.data.model.Song
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AppleMusicMatcherTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private class Clock(var now: Long = 1_000L) {
        fun millis() = now
    }

    private class Catalog : AppleMusicMatcher.Catalog {
        val searches = mutableListOf<Pair<String, String>>()
        val isrcRequests = mutableListOf<Pair<String, String>>()
        var search: suspend (String, String) -> List<AppleMusicTrack> = { _, _ -> emptyList() }
        var byIsrc: suspend (String, String) -> List<AppleMusicTrack> = { _, _ -> emptyList() }

        override suspend fun searchSongs(storefront: String, query: String): List<AppleMusicTrack> {
            searches += storefront to query
            return search(storefront, query)
        }

        override suspend fun songsByIsrc(storefront: String, isrc: String): List<AppleMusicTrack> {
            isrcRequests += storefront to isrc
            return byIsrc(storefront, isrc)
        }
    }

    private fun song(
        id: String = "yt-song",
        title: String = "Northern Lights",
        artist: String = "The Example",
        duration: String? = "3:00",
        explicit: Boolean? = null,
        video: Boolean = false,
    ) = Song(
        videoId = id,
        title = title,
        artist = artist,
        thumbnailUrl = null,
        durationText = duration,
        isExplicit = explicit,
        isVideo = video,
    )

    private fun track(
        id: String = "123",
        title: String = "Northern Lights",
        artist: String = "The Example",
        durationMs: Int = 180_000,
        explicit: Boolean? = null,
        video: Boolean = false,
        isrc: String? = ISRC,
    ) = AppleMusicTrack(
        id = id,
        catalogId = id,
        title = title,
        artist = artist,
        durationMs = durationMs,
        explicit = explicit,
        isVideo = video,
        isrc = isrc,
    )

    private fun file(): File = File(temporary.newFolder(), "matches.bin")

    @Test
    fun `ISRC hit is normalized cached and does not search`() = runTest {
        val catalog = Catalog().apply { byIsrc = { _, _ -> listOf(track()) } }
        val clock = Clock()
        val matcher = AppleMusicMatcher(catalog, clock = clock::millis)

        assertEquals("123", matcher.match(song(), " US ", "us-abc-12-00001"))
        assertEquals(listOf("us" to ISRC), catalog.isrcRequests)
        assertEquals("123", matcher.match(song(), "us"))
        assertEquals(ISRC, matcher.cachedIsrc("us", "yt-song"))
        assertEquals(AppleMusicMatcher.Match("123", ISRC), matcher.cached("us", "yt-song"))
        assertTrue(catalog.searches.isEmpty())
        assertEquals(1, catalog.isrcRequests.size)
    }

    @Test
    fun `ISRC miss falls back to TrackMatcher queries`() = runTest {
        val catalog = Catalog().apply {
            search = { _, _ -> listOf(track()) }
        }
        val matcher = AppleMusicMatcher(catalog)

        assertEquals("123", matcher.match(song(title = "Northern Lights (Official Audio)"), "us", ISRC))
        assertEquals(1, catalog.isrcRequests.size)
        assertEquals(listOf("us" to "northern lights the example"), catalog.searches)
    }

    @Test
    fun `an ISRC contradiction cannot win even with identical metadata`() = runTest {
        val catalog = Catalog().apply {
            byIsrc = { _, _ -> listOf(track(isrc = "USABC1200002")) }
            search = { _, _ -> listOf(track(isrc = "USABC1200002")) }
        }
        val matcher = AppleMusicMatcher(catalog)

        assertNull(matcher.match(song(), "us", ISRC))
        assertNull(matcher.cachedIsrc("us", "yt-song"))
        assertEquals(1, catalog.isrcRequests.size)
        assertTrue(catalog.searches.isNotEmpty())
    }

    @Test
    fun `same runtime never substitutes for artist identity`() = runTest {
        for (artist in listOf("A Different Singer", "")) {
            val catalog = Catalog().apply { search = { _, _ -> listOf(track(artist = artist)) } }
            assertNull(AppleMusicMatcher(catalog).match(song(), "us"))
        }
    }

    @Test
    fun `title and recording versions still use TrackMatcher`() = runTest {
        for (title in listOf("A Different Song", "Northern Lights (Live)", "Northern Lights (Remix)")) {
            val catalog = Catalog().apply { search = { _, _ -> listOf(track(title = title)) } }
            assertNull(AppleMusicMatcher(catalog).match(song(), "us"))
        }
    }

    @Test
    fun `duration slack accepts five seconds but not a millisecond more`() = runTest {
        for ((duration, expected) in listOf(175_000 to "123", 185_000 to "123", 174_999 to null, 185_001 to null, 0 to null)) {
            val catalog = Catalog().apply { search = { _, _ -> listOf(track(durationMs = duration)) } }
            assertEquals("duration $duration", expected, AppleMusicMatcher(catalog).match(song(), "us"))
        }
    }

    @Test
    fun `video origin cannot widen the duration window`() = runTest {
        val catalog = Catalog().apply { search = { _, _ -> listOf(track(durationMs = 210_000)) } }
        assertNull(AppleMusicMatcher(catalog).match(song(video = true), "us"))
    }

    @Test
    fun `missing source duration still permits title and artist evidence`() = runTest {
        val catalog = Catalog().apply { search = { _, _ -> listOf(track()) } }
        assertEquals("123", AppleMusicMatcher(catalog).match(song(duration = null), "us"))
    }

    @Test
    fun `matching explicit edition outranks unknown and rejects contradictory editions`() = runTest {
        val catalog = Catalog().apply {
            search = { _, _ ->
                listOf(
                    track(id = "1", explicit = false),
                    track(id = "2", explicit = null),
                    track(id = "3", explicit = true, durationMs = 184_000),
                )
            }
        }
        val matcher = AppleMusicMatcher(catalog)
        assertEquals("3", matcher.match(song(id = "explicit", explicit = true), "us"))
        assertEquals("1", matcher.match(song(id = "clean", explicit = false), "us"))

        catalog.search = { _, _ -> listOf(track(explicit = false)) }
        assertNull(matcher.match(song(id = "only-clean", explicit = true), "us"))
    }

    @Test
    fun `videos and library-only rows are rejected from both lookup routes`() = runTest {
        val invalid = listOf(
            track(video = true),
            track().copy(id = "i.library", catalogId = null),
            track().copy(id = "123", catalogId = null),
            track().copy(catalogId = "i.library"),
        )
        val catalog = Catalog().apply {
            byIsrc = { _, _ -> invalid }
            search = { _, _ -> invalid }
        }
        assertNull(AppleMusicMatcher(catalog).match(song(), "us", ISRC))
    }

    @Test
    fun `completed misses expire exactly at the negative TTL`() = runTest {
        val clock = Clock()
        val catalog = Catalog()
        val matcher = AppleMusicMatcher(catalog, negativeTtlMs = 100, clock = clock::millis)
        assertNull(matcher.match(song(), "us"))
        val requests = catalog.searches.size
        assertTrue(requests > 0)
        clock.now += 99
        catalog.search = { _, _ -> listOf(track()) }
        assertNull(matcher.match(song(), "us"))
        assertEquals(requests, catalog.searches.size)
        clock.now++
        assertEquals("123", matcher.match(song(), "us"))
        clock.now += 1_000_000
        assertEquals("123", matcher.match(song(), "us"))
        assertEquals(requests + 1, catalog.searches.size)
    }

    @Test
    fun `new ISRC evidence bypasses an earlier negative text search`() = runTest {
        val catalog = Catalog()
        val matcher = AppleMusicMatcher(catalog)
        assertNull(matcher.match(song(), "us"))
        catalog.byIsrc = { _, _ -> listOf(track()) }
        assertEquals("123", matcher.match(song(), "us", ISRC))
        assertEquals(1, catalog.isrcRequests.size)
    }

    @Test
    fun `new ISRC replaces a contradictory positive and survives restart`() = runTest {
        val file = file()
        val catalog = Catalog().apply { byIsrc = { _, _ -> listOf(track(id = "456", isrc = OTHER_ISRC)) } }
        val matcher = AppleMusicMatcher(catalog, cacheFile = file)
        assertTrue(matcher.store("us", "yt-song", "123", ISRC))
        assertTrue(matcher.store("gb", "yt-song", "789", ISRC))

        assertEquals("456", matcher.match(song(), " US ", "us-abc-12-00002"))
        assertEquals(AppleMusicMatcher.Match("456", OTHER_ISRC), matcher.cached("us", "yt-song"))
        assertEquals(listOf("us" to OTHER_ISRC), catalog.isrcRequests)
        assertTrue(catalog.searches.isEmpty())

        val restored = AppleMusicMatcher(catalog, cacheFile = file)
        assertEquals("456", restored.match(song(), "us", OTHER_ISRC))
        assertEquals("456", restored.match(song(), "us"))
        assertEquals(OTHER_ISRC, restored.cachedIsrc("us", "yt-song"))
        assertEquals("789", restored.cachedCatalogId("gb", "yt-song"))
        assertEquals(1, catalog.isrcRequests.size)
    }

    @Test
    fun `new ISRC miss replaces a contradictory positive with a persistent negative`() = runTest {
        val file = file()
        val clock = Clock()
        val catalog = Catalog()
        val matcher = AppleMusicMatcher(catalog, cacheFile = file, negativeTtlMs = 100, clock = clock::millis)
        assertTrue(matcher.store("us", "yt-song", "123", ISRC))
        assertNull(matcher.match(song(), "us", OTHER_ISRC))
        assertNull(matcher.cached("us", "yt-song"))
        assertTrue(catalog.searches.isNotEmpty())

        catalog.byIsrc = { _, _ -> listOf(track(id = "456", isrc = OTHER_ISRC)) }
        val restored = AppleMusicMatcher(catalog, cacheFile = file, negativeTtlMs = 100, clock = clock::millis)
        assertNull(restored.match(song(), "us", OTHER_ISRC))
        assertEquals(1, catalog.isrcRequests.size)
        clock.now += 100
        assertEquals("456", restored.match(song(), "us", OTHER_ISRC))
        assertEquals(2, catalog.isrcRequests.size)
    }

    @Test
    fun `failure and timeout invalidate only contradictory evidence across restart`() = runTest {
        for (timeout in listOf(false, true)) {
            val file = file()
            val catalog = Catalog().apply {
                byIsrc = { _, _ -> if (timeout) awaitCancellation() else throw IOException("offline") }
            }
            val matcher = AppleMusicMatcher(catalog, cacheFile = file)
            assertTrue(matcher.store("us", "yt-song", "123", ISRC))
            assertTrue(matcher.store("us", "safe", "789", ISRC))
            assertNull(matcher.match(song(), "us", OTHER_ISRC))
            assertNull(matcher.cached("us", "yt-song"))

            catalog.byIsrc = { _, _ -> listOf(track(id = "456", isrc = OTHER_ISRC)) }
            val restored = AppleMusicMatcher(catalog, cacheFile = file)
            assertNull(restored.cached("us", "yt-song"))
            assertEquals("789", restored.cachedCatalogId("us", "safe"))
            assertEquals("456", restored.match(song(), "us", OTHER_ISRC))
            assertEquals(2, catalog.isrcRequests.size)
            assertTrue(catalog.searches.isEmpty())
        }
    }

    @Test
    fun `positive cache with compatible or unknown ISRC still bypasses lookup`() = runTest {
        val catalog = Catalog().apply {
            byIsrc = { _, _ -> error("must not look up a compatible cache hit") }
            search = { _, _ -> error("must not search a compatible cache hit") }
        }
        for (isrc in listOf(null, ISRC)) {
            val matcher = AppleMusicMatcher(catalog)
            assertTrue(matcher.store("us", "yt-song", "123", isrc))
            assertEquals("123", matcher.match(song(), "us", ISRC))
            assertEquals("123", matcher.match(song(), "us"))
        }
        assertTrue(catalog.isrcRequests.isEmpty())
        assertTrue(catalog.searches.isEmpty())
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `positive and negative cache entries are isolated by storefront`() = runTest {
        val catalog = Catalog().apply {
            search = { storefront, _ -> if (storefront == "gb") emptyList() else listOf(track(id = "123")) }
        }
        val matcher = AppleMusicMatcher(catalog)
        assertNull(matcher.match(song(), "gb"))
        assertEquals("123", matcher.match(song(), "us"))
        assertTrue(matcher.store("ca", "yt-song", "456"))
        assertEquals("456", matcher.match(song(), "ca"))
        assertNull(matcher.match(song(), "gb"))
        assertEquals("123", matcher.cachedCatalogId("us", "yt-song"))
        assertEquals("456", matcher.cachedCatalogId("ca", "yt-song"))
        assertNull(matcher.cachedCatalogId("gb", "yt-song"))
    }

    @Test
    fun `positive and negative snapshots survive restart with their original TTL`() = runTest {
        val file = file()
        val clock = Clock()
        val first = AppleMusicMatcher(Catalog(), cacheFile = file, negativeTtlMs = 100, clock = clock::millis)
        assertTrue(first.seed("us", "known", track().copy(id = "i.library")))
        assertNull(first.match(song(id = "missing"), "us"))
        val catalog = Catalog().apply { search = { _, _ -> listOf(track(id = "456")) } }
        val restored = AppleMusicMatcher(catalog, cacheFile = file, negativeTtlMs = 100, clock = clock::millis)

        assertEquals("123", restored.match(song(id = "known"), "us"))
        assertEquals(ISRC, restored.cachedIsrc("us", "known"))
        assertNull(restored.match(song(id = "missing"), "us"))
        assertTrue(catalog.searches.isEmpty())
        clock.now += 100
        assertEquals("456", restored.match(song(id = "missing"), "us"))
        assertEquals(listOf("matches.bin"), file.parentFile!!.list()!!.toList())
    }

    @Test
    fun `LRU eviction is bounded and its access order is checkpointed`() {
        val file = file()
        val matcher = AppleMusicMatcher(Catalog(), cacheFile = file, maxCacheEntries = 2)
        matcher.store("us", "one", "1")
        matcher.store("us", "two", "2")
        assertEquals("1", matcher.cachedCatalogId("us", "one"))
        matcher.store("us", "three", "3")
        assertNull(matcher.cached("us", "two"))

        val restored = AppleMusicMatcher(Catalog(), cacheFile = file, maxCacheEntries = 2)
        assertEquals("1", restored.cachedCatalogId("us", "one"))
        assertEquals("3", restored.cachedCatalogId("us", "three"))
        assertNull(restored.cached("us", "two"))
        restored.store("gb", "one", "4")
        assertNull(restored.cached("us", "one"))
    }

    @Test
    fun `reducing capacity checkpoints eviction so old mappings cannot reappear`() {
        val file = file()
        val original = AppleMusicMatcher(Catalog(), cacheFile = file, maxCacheEntries = 3)
        original.store("us", "one", "1")
        original.store("us", "two", "2")
        original.store("us", "three", "3")
        val smaller = AppleMusicMatcher(Catalog(), cacheFile = file, maxCacheEntries = 1)
        assertNull(smaller.cached("us", "one"))
        assertNull(smaller.cached("us", "two"))
        assertEquals("3", smaller.cachedCatalogId("us", "three"))
        val restored = AppleMusicMatcher(Catalog(), cacheFile = file, maxCacheEntries = 3)
        assertNull(restored.cached("us", "one"))
        assertNull(restored.cached("us", "two"))
        assertEquals("3", restored.cachedCatalogId("us", "three"))
    }

    @Test
    fun `corrupt and truncated files are discarded and replaced`() = runTest {
        val file = file()
        file.writeBytes(byteArrayOf(1, 2, 3))
        val catalog = Catalog().apply { search = { _, _ -> listOf(track()) } }
        val matcher = AppleMusicMatcher(catalog, cacheFile = file)
        assertEquals("123", matcher.match(song(), "us"))
        val snapshot = file.readBytes()
        file.writeBytes(snapshot.copyOf(snapshot.size / 2))
        val restored = AppleMusicMatcher(catalog, cacheFile = file)
        assertNull(restored.cached("us", "yt-song"))
        assertTrue(restored.store("us", "new", "456"))
        assertEquals("456", AppleMusicMatcher(Catalog(), cacheFile = file).cachedCatalogId("us", "new"))
    }

    @Test
    fun `unwritable cache location leaves memory cache operational`() = runTest {
        val parent = temporary.newFile()
        val catalog = Catalog().apply { search = { _, _ -> listOf(track()) } }
        val matcher = AppleMusicMatcher(catalog, cacheFile = File(parent, "matches.bin"))
        assertEquals("123", matcher.match(song(), "us"))
        assertEquals("123", matcher.match(song(), "us"))
        assertEquals(1, catalog.searches.size)
    }

    @Test
    fun `wall clock rollback invalidates negative entries`() = runTest {
        val clock = Clock()
        val catalog = Catalog()
        val matcher = AppleMusicMatcher(catalog, clock = clock::millis)
        assertNull(matcher.match(song(), "us"))
        clock.now--
        catalog.search = { _, _ -> listOf(track()) }
        assertEquals("123", matcher.match(song(), "us"))
    }

    @Test
    fun `network and server failures remain immediately retryable`() = runTest {
        for (error in listOf(IOException("offline"), IllegalStateException("server unavailable"))) {
            val catalog = Catalog().apply { search = { _, _ -> throw error } }
            val matcher = AppleMusicMatcher(catalog)
            assertNull(matcher.match(song(), "us"))
            catalog.search = { _, _ -> listOf(track()) }
            assertEquals("123", matcher.match(song(), "us"))
            assertEquals(2, catalog.searches.size)
        }
    }

    @Test
    fun `ISRC transport error does not become a negative cache entry`() = runTest {
        val catalog = Catalog().apply { byIsrc = { _, _ -> throw IOException("offline") } }
        val matcher = AppleMusicMatcher(catalog)
        assertNull(matcher.match(song(), "us", ISRC))
        catalog.byIsrc = { _, _ -> listOf(track()) }
        assertEquals("123", matcher.match(song(), "us", ISRC))
        assertEquals(2, catalog.isrcRequests.size)
        assertTrue(catalog.searches.isEmpty())
    }

    @Test
    fun `ISRC and text search share one 2500ms budget and timeout can retry`() = runTest {
        val catalog = Catalog().apply {
            byIsrc = { _, _ -> delay(1_500); emptyList() }
            search = { _, _ -> delay(1_500); listOf(track()) }
        }
        val matcher = AppleMusicMatcher(catalog)
        assertNull(matcher.match(song(), "us", ISRC))
        assertEquals(2_500L, testScheduler.currentTime)
        catalog.byIsrc = { _, _ -> listOf(track()) }
        assertEquals("123", matcher.match(song(), "us", ISRC))
        assertEquals(2, catalog.isrcRequests.size)
    }

    @Test
    fun `prefetch mutex waiting shares foreground deadline while cache and direct hits stay fast`() = runTest {
        // These ids have identical hashes, so unrelated songs contend for the same stripe.
        val owner = song(id = "AaAa")
        val queued = song(id = "BBBB")
        val foreground = song(id = "AaBB")
        val cached = song(id = "BBAa")
        assertEquals(owner.videoId.hashCode(), queued.videoId.hashCode())
        assertEquals(owner.videoId.hashCode(), foreground.videoId.hashCode())
        assertEquals(owner.videoId.hashCode(), cached.videoId.hashCode())
        val catalog = Catalog().apply { search = { _, _ -> awaitCancellation() } }
        val matcher = AppleMusicMatcher(catalog, prefetchParallelism = 2)
        assertTrue(matcher.store("us", cached.videoId, "456", ISRC))
        val prefetch = async { matcher.prefetch(listOf(owner, queued), "us") }
        runCurrent()
        assertEquals(1, catalog.searches.size)

        assertEquals("456", matcher.match(cached, "us", ISRC))
        assertEquals("789", matcher.match(song(id = "am-miss:789"), "us"))
        assertEquals(0L, testScheduler.currentTime)
        delay(100)
        val startedAt = testScheduler.currentTime
        assertNull(matcher.match(foreground, "us"))
        assertEquals(2_500L, testScheduler.currentTime - startedAt)
        prefetch.await()

        catalog.search = { _, _ -> listOf(track()) }
        assertEquals("123", matcher.match(foreground, "us"))
        assertEquals("123", matcher.match(queued, "us"))
    }

    @Test
    fun `cancellation propagates without poisoning cache`() = runTest {
        val catalog = Catalog().apply { search = { _, _ -> throw CancellationException("cancelled") } }
        val matcher = AppleMusicMatcher(catalog)
        var cancelled = false
        try {
            matcher.match(song(), "us")
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        catalog.search = { _, _ -> listOf(track()) }
        assertEquals("123", matcher.match(song(), "us"))
    }

    @Test
    fun `cancelled owner releases its key for a waiting retry`() = runTest {
        val started = CompletableDeferred<Unit>()
        val catalog = Catalog().apply {
            search = { _, _ -> started.complete(Unit); awaitCancellation() }
        }
        val matcher = AppleMusicMatcher(catalog)
        val owner = async { matcher.match(song(), "us") }
        started.await()
        val waiter = async { matcher.match(song(), "us") }
        runCurrent()
        catalog.search = { _, _ -> listOf(track()) }
        owner.cancelAndJoin()
        assertEquals("123", waiter.await())
        assertEquals(2, catalog.searches.size)
    }

    @Test
    fun `same-key concurrent hits perform only one live lookup and waiter cancellation is isolated`() = runTest {
        val catalog = Catalog().apply { search = { _, _ -> delay(100); listOf(track()) } }
        val matcher = AppleMusicMatcher(catalog)
        val owner = async { matcher.match(song(), "us") }
        runCurrent()
        val cancelledWaiter = async { matcher.match(song(), "us") }
        val others = List(10) { async { matcher.match(song(), "us") } }
        runCurrent()
        cancelledWaiter.cancelAndJoin()
        assertEquals("123", owner.await())
        assertEquals(List(10) { "123" }, others.awaitAll())
        assertEquals(1, catalog.searches.size)
    }

    @Test
    fun `library seed during a live lookup wins over the lookup result`() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val catalog = Catalog().apply {
            search = { _, _ -> started.complete(Unit); release.await(); listOf(track()) }
        }
        val matcher = AppleMusicMatcher(catalog)
        val pending = async { matcher.match(song(), "us") }
        started.await()
        assertTrue(matcher.seed("us", "yt-song", track(id = "456")))
        release.complete(Unit)
        assertEquals("456", pending.await())
        assertEquals("456", matcher.cachedCatalogId("us", "yt-song"))
    }

    @Test
    fun `text-only result cannot replace a concurrent library seed with a different ISRC`() = runTest {
        val file = file()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val catalog = Catalog().apply {
            search = { _, _ -> started.complete(Unit); release.await(); listOf(track(isrc = ISRC)) }
        }
        val matcher = AppleMusicMatcher(catalog, cacheFile = file)
        val pending = async { matcher.match(song(), "us") }
        started.await()
        assertTrue(matcher.seed("us", "yt-song", track(id = "456", isrc = OTHER_ISRC)))
        release.complete(Unit)

        val expected = AppleMusicMatcher.Match("456", OTHER_ISRC)
        assertEquals("456", pending.await())
        assertEquals(expected, matcher.cached("us", "yt-song"))
        val restored = AppleMusicMatcher(catalog, cacheFile = file)
        assertEquals(expected, restored.cached("us", "yt-song"))
        assertEquals("456", restored.match(song(), "us"))
        assertEquals(1, catalog.searches.size)
        assertTrue(catalog.isrcRequests.isEmpty())
    }

    @Test
    fun `conflicting library seed cannot override a completed ISRC lookup or miss`() = runTest {
        for (found in listOf(false, true)) {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val catalog = Catalog().apply {
                byIsrc = { _, _ ->
                    started.complete(Unit)
                    release.await()
                    if (found) listOf(track(id = "456", isrc = OTHER_ISRC)) else emptyList()
                }
            }
            val matcher = AppleMusicMatcher(catalog)
            val pending = async { matcher.match(song(), "us", OTHER_ISRC) }
            started.await()
            assertTrue(matcher.seed("us", "yt-song", track(id = "789")))
            release.complete(Unit)

            val expected = if (found) AppleMusicMatcher.Match("456", OTHER_ISRC) else null
            assertEquals(expected?.catalogId, pending.await())
            assertEquals(expected, matcher.cached("us", "yt-song"))
            assertEquals(expected?.catalogId, matcher.match(song(), "us", OTHER_ISRC))
            assertEquals(1, catalog.isrcRequests.size)
        }
    }

    @Test
    fun `compatible library seed survives misses failures and timeouts`() = runTest {
        for (outcome in listOf("miss", "error", "timeout")) {
            for (isrc in listOf(null, ISRC)) {
                val started = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val catalog = Catalog().apply {
                    byIsrc = { _, _ ->
                        started.complete(Unit)
                        release.await()
                        when (outcome) {
                            "error" -> throw IOException("offline")
                            "timeout" -> awaitCancellation()
                            else -> emptyList()
                        }
                    }
                }
                val matcher = AppleMusicMatcher(catalog)
                val pending = async { matcher.match(song(), "us", ISRC) }
                started.await()
                assertTrue(matcher.seed("us", "yt-song", track(id = "456", isrc = isrc)))
                release.complete(Unit)

                assertEquals(if (outcome == "miss") "456" else null, pending.await())
                assertEquals(AppleMusicMatcher.Match("456", isrc), matcher.cached("us", "yt-song"))
                assertEquals("456", matcher.match(song(), "us", ISRC))
                assertEquals(1, catalog.isrcRequests.size)
            }
        }
    }

    @Test
    fun `direct missing-YT catalog identity never searches or accepts a library id`() = runTest {
        val catalog = Catalog().apply { search = { _, _ -> error("must not search") } }
        val matcher = AppleMusicMatcher(catalog)
        assertEquals("123", matcher.match(song(id = "am-miss:123"), "us"))
        assertEquals("123", matcher.cachedCatalogId("us", "am-miss:123"))
        for (id in listOf("am-miss:i.library", "am-miss:", "am-miss:123/extra")) {
            assertNull(matcher.match(song(id = id), "us"))
        }
        assertTrue(catalog.searches.isEmpty())
        assertTrue(catalog.isrcRequests.isEmpty())
        assertFalse(matcher.store("us", "am-miss:123", "456"))
    }

    @Test
    fun `seed requires a genuine song catalog mapping and replaces negatives`() = runTest {
        val matcher = AppleMusicMatcher(Catalog())
        assertNull(matcher.match(song(), "us"))
        assertFalse(matcher.seed("us", "yt-song", track().copy(id = "i.upload", catalogId = null)))
        assertFalse(matcher.seed("us", "yt-song", track(video = true)))
        assertFalse(matcher.store("us", "yt-song", "i.upload"))
        assertTrue(matcher.seed("us", "yt-song", track().copy(id = "i.library")))
        assertEquals("123", matcher.match(song(), "us"))
        assertEquals(ISRC, matcher.cachedIsrc("us", "yt-song"))
    }

    @Test
    fun `prefetch warms only next three with bounded parallelism and supplied ISRC`() = runTest {
        var active = 0
        var peak = 0
        val catalog = Catalog().apply {
            byIsrc = { _, _ ->
                active++
                peak = maxOf(peak, active)
                try {
                    delay(100)
                    listOf(track())
                } finally {
                    active--
                }
            }
        }
        val matcher = AppleMusicMatcher(catalog, prefetchParallelism = 2)
        matcher.prefetch((1..5).map { song(id = "yt-$it") }, "us") { ISRC }
        assertEquals(3, catalog.isrcRequests.size)
        assertEquals(2, peak)
        assertEquals(0, active)
        assertTrue(catalog.searches.isEmpty())
        for (id in 1..3) assertEquals("123", matcher.cachedCatalogId("us", "yt-$id"))
        for (id in 4..5) assertNull(matcher.cachedCatalogId("us", "yt-$id"))
    }

    private companion object {
        const val ISRC = "USABC1200001"
        const val OTHER_ISRC = "USABC1200002"
    }
}
