package com.music.bitchord

import com.music.bitchord.data.LikeState
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.YtMusicRepository.SongPage
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The liked-music sync behind issue #219: a liked collection larger than one
 * page (or than the old ten-page cap) must still have every id recognised as
 * liked, without holding the library page open, and a track menu that says
 * nothing about a rating must never downgrade a LIKE already known.
 */
class LikedMusicSyncTest {

    private fun song(id: String) = Song(videoId = id, title = "t", artist = "a", thumbnailUrl = null)

    private fun songs(n: Int, offset: Int): List<Song> =
        List(n) { song("v${offset + it}") }

    @After
    fun tearDown() {
        LikeState.clear()
    }

    // ---- A. Pagination beyond the old 1000-song (10 page) boundary ---------

    @Test
    fun `liked sync follows continuations past the old ten page cap`() = runBlocking {
        LikeState.clear()
        val pageCount = 12
        val perPage = 100
        val pages = LinkedHashMap<String, SongPage>()
        for (p in 0 until pageCount) {
            val token = "page-$p"
            val next = if (p == pageCount - 1) null else "page-${p + 1}"
            pages[token] = SongPage(songs = songs(perPage, p * perPage), continuation = next)
        }

        YtMusicRepository.syncLikedMusic("page-0") { pages[it] }

        // Every id across every page — well past the old MAX_PAGES=10 — must
        // be recognised as liked, not just the first ten pages' worth.
        val liked = LikeState.overrides.value
        assertEquals(pageCount * perPage, liked.size)
        assertEquals(pageCount * perPage, liked.values.count { it == LikeStatus.LIKE })
        assertTrue(liked.containsKey("v${pageCount * perPage - 1}"))
    }

    @Test
    fun `liked sync stops when a continuation points back at a page already read`() = runBlocking {
        LikeState.clear()
        val pages = LinkedHashMap<String, SongPage>()
        pages["t0"] = SongPage(songs = songs(3, 0), continuation = "t1")
        pages["t1"] = SongPage(songs = songs(3, 3), continuation = "t0")
        var calls = 0

        // "t0" points to "t1", which points back to "t0" — a malformed or
        // buggy feed. Without the repeat guard this spins forever; with it,
        // the loop ends the moment a token it has already consumed comes
        // back around, instead of running until cancelled from outside.
        val job = launch {
            YtMusicRepository.syncLikedMusic("t0") { calls++; pages[it] }
        }
        withTimeout(1000) { job.join() }

        assertEquals(2, calls)
        assertEquals(setOf("v0", "v1", "v2", "v3", "v4", "v5"), LikeState.overrides.value.keys)
    }

    // ---- D. Continuation exhaustion -----------------------------------------

    @Test
    fun `liked sync stops when the continuation runs dry`() = runBlocking {
        LikeState.clear()
        val pages = LinkedHashMap<String, SongPage>()
        pages["t0"] = SongPage(songs = songs(3, 0), continuation = "t1")
        pages["t1"] = SongPage(songs = songs(3, 3), continuation = null)
        var calls = 0

        YtMusicRepository.syncLikedMusic("t0") {
            calls++
            pages[it]
        }

        // Two pages consumed, then the null continuation ended the loop — no
        // attempt to read a page the feed never promised.
        assertEquals(2, calls)
        assertEquals(setOf("v0", "v1", "v2", "v3", "v4", "v5"), LikeState.overrides.value.keys)
    }

    @Test
    fun `liked sync is a no-op with no continuation`() = runBlocking {
        LikeState.clear()
        var calls = 0
        YtMusicRepository.syncLikedMusic(null) { calls++; error("never called") }
        assertEquals(0, calls)
        assertTrue(LikeState.overrides.value.isEmpty())
    }

    // ---- Performance: first page is ready before the sync finishes ----------

    /**
     * The Library tab must show its first page without waiting for the whole
     * liked collection. In the app that ordering is: [fetchLibrary] builds the
     * page from Liked Music's first page, seeds it, then launches the
     * continuation sync in `viewModelScope` without awaiting it. Here the
     * background sync is held open on its next continuation while the first
     * page's ids are already visible — the sync finishing later is exactly what
     * lets the UI paint while the background catches up.
     */
    @Test
    fun `first page is seeded while background sync is still in flight`() = runBlocking {
        LikeState.clear()
        val gate = CompletableDeferred<SongPage?>()

        // Background: the continuation sync, blocked on its next page. It must
        // not be required to finish before the first page is usable.
        val background = launch { YtMusicRepository.syncLikedMusic("t0") { gate.await() } }
        try {
            // Foreground: what fetchLibrary does with the first page — seed it
            // and render it — completes while the background sync is suspended.
            yield()
            val firstPageIds = setOf("v0", "v1", "v2")
            LikeState.seedLiked(firstPageIds)
            assertEquals(firstPageIds, LikeState.overrides.value.keys)
        } finally {
            // Always release the background job, even if an assertion above
            // failed, so runBlocking can't hang on a leaked suspension.
            if (!gate.isCompleted) gate.complete(SongPage(songs = songs(3, 3), continuation = null))
        }
        background.join()
        assertEquals(setOf("v0", "v1", "v2", "v3", "v4", "v5"), LikeState.overrides.value.keys)
    }

    // ---- Failure & cancellation should not crash or stall --------------------

    @Test
    fun `a failed continuation stops the sync without losing earlier ids`() = runBlocking {
        LikeState.clear()
        val pages = LinkedHashMap<String, SongPage>()
        pages["t0"] = SongPage(songs = songs(3, 0), continuation = "t1")

        YtMusicRepository.syncLikedMusic("t0") {
            // Once the continuation to t1 is refused, the loop ends quietly —
            // like the default `moreSongs(it).getOrNull()` returning null on a
            // failed request. IDs already seeded stay put.
            if (it == "t1") null else pages[it]
        }

        assertEquals(setOf("v0", "v1", "v2"), LikeState.overrides.value.keys)
    }

    @Test
    fun `cancelling the sync stops further pagination`() = runBlocking {
        LikeState.clear()
        val pages = LinkedHashMap<String, SongPage>()
        pages["t0"] = SongPage(songs = songs(3, 0), continuation = "t1")
        val reachedSecond = Channel<Unit>(1)

        val job = launch {
            YtMusicRepository.syncLikedMusic("t0") {
                if (it == "t1") {
                    reachedSecond.trySend(Unit)
                    yield()
                    SongPage(songs = songs(3, 3), continuation = "t2")
                } else pages[it]
            }
        }
        reachedSecond.receive()
        job.cancel()
        job.join()

        // The second page's ids must not have been seeded after cancellation.
        assertEquals(setOf("v0", "v1", "v2"), LikeState.overrides.value.keys)
    }

    // ---- B. Missing like button means no stated rating ----------------------

    @Test
    fun `a row without a like button states no rating`() {
        val json = """
        {"contents":[{"playlistPanelVideoRenderer":{
          "videoId":"abc123",
          "menu":{"menuRenderer":{"items":[]}}
        }}]}
        """.trimIndent()

        val menu = InnertubeParser.parseSongMenu(Json.parseToJsonElement(json), "abc123")

        // Null, not INDIFFERENT: a missing button and a stated "no rating"
        // are different answers, and must not collapse into one.
        assertNull(menu?.likeStatus)
    }

    // ---- C. A stated LIKE is never overwritten by a silent menu -------------

    @Test
    fun `an existing like survives a menu that says nothing`() {
        LikeState.clear()
        LikeState.set("abc123", LikeStatus.LIKE)

        InnertubeParser.parseSongMenu(
            Json.parseToJsonElement(
                """{"contents":[{"playlistPanelVideoRenderer":{"videoId":"abc123"}}]}""",
            ),
            "abc123",
        )?.let { LikeState.rememberStated("abc123", it.likeStatus) }

        // The menu carried no rating (null), so the LIKE stays put.
        assertEquals(LikeStatus.LIKE, LikeState.overrides.value["abc123"])
    }

    @Test
    fun `a stated indifferent rating does not downgrade an existing like`() {
        LikeState.clear()
        LikeState.set("abc123", LikeStatus.LIKE)

        LikeState.rememberStated("abc123", LikeStatus.INDIFFERENT)

        assertEquals(LikeStatus.LIKE, LikeState.overrides.value["abc123"])
    }
}
