package com.music.bitchord.data.applemusic

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppleMusicTrackMappingTest {
    @Test
    fun `restored local playlist Songs use the same provider guard and reject malformed direct ids`() {
        assertTrue(youtubeSong().canPlayAppleMusicRow(false, false))
        val appleOnly = youtubeSong("am-miss:123")
        assertTrue(appleOnly.canPlayAppleMusicRow(true, true))
        assertFalse(appleOnly.canPlayAppleMusicRow(false, true))
        assertFalse(appleOnly.canPlayAppleMusicRow(true, false))
        for (id in listOf("am:i.pending", "am:unavailable:123", "am-miss:i.upload", "am-miss:0", "sp-miss:missing")) {
            assertFalse(id, youtubeSong(id).canPlayAppleMusicRow(true, true))
        }
    }

    @Test
    fun `matched rows preserve Apple identity and seed the same playback matcher`() = runTest {
        val matcher = AppleMusicMatcher()
        var received: AppleMusicTrack? = null
        val mapper = AppleMusicTrackMapper(matcher, { received = it; youtubeSong() }, StandardTestDispatcher(testScheduler))
        val track = libraryTrack()
        val pending = mapper.pending(track, AppleMusicTrackOrigin.LIBRARY)
        assertEquals("am:i.saved", pending.song.videoId)
        assertFalse(pending.isPlayable(true, true))

        val row = mapper.match(track, AppleMusicTrackOrigin.LIBRARY, "us")

        assertEquals(track, received)
        assertEquals("i.saved", row.libraryId)
        assertEquals("123", row.catalogId)
        assertEquals("yt-song-001", row.song.videoId)
        assertEquals(track.imageUrl, row.song.thumbnailUrl)
        assertEquals(AppleMusicTrackMatchStatus.YOUTUBE, row.status)
        assertTrue(row.isPlayable(false, false))
        assertEquals(AppleMusicMatcher.Match("123", "USABC1200001"), matcher.cached("us", row.song.videoId))
        assertNull(matcher.cached("gb", row.song.videoId))
    }

    @Test
    fun `unmatched catalog songs require a connected selected Apple provider`() = runTest {
        val mapper = AppleMusicTrackMapper(AppleMusicMatcher(), { null }, StandardTestDispatcher(testScheduler))
        val row = mapper.match(libraryTrack(), AppleMusicTrackOrigin.LIBRARY, "us")

        assertEquals("am-miss:123", row.song.videoId)
        assertEquals("3:00", row.song.durationText)
        assertEquals("An Album", row.song.albumName)
        assertEquals(true, row.song.isExplicit)
        assertEquals(AppleMusicTrackMatchStatus.APPLE_ONLY, row.status)
        assertTrue(row.isPlayable(true, true))
        assertFalse(row.isPlayable(false, true))
        assertFalse(row.isPlayable(true, false))
        assertFalse(row.isPlayable(false, false))
    }

    @Test
    fun `library numeric ids invalid mappings and videos never become fabricated Apple catalog songs`() = runTest {
        val mapper = AppleMusicTrackMapper(AppleMusicMatcher(), { null }, StandardTestDispatcher(testScheduler))
        val tracks = listOf(
            libraryTrack("123456", null),
            libraryTrack(catalogId = "i.upload"),
            libraryTrack(catalogId = "0"),
            libraryTrack(catalogId = "123").copy(isVideo = true),
            AppleMusicTrack("i.unavailable"),
        )
        for (track in tracks) {
            val row = mapper.match(track, AppleMusicTrackOrigin.LIBRARY, "us")
            assertEquals(AppleMusicTrackMatchStatus.UNAVAILABLE, row.status)
            assertTrue(row.song.videoId.startsWith("am:unavailable:"))
            assertFalse(row.song.videoId.startsWith("am-miss:"))
            assertFalse(row.isPlayable(true, true))
        }
    }

    @Test
    fun `a matched upload stays YouTube playable without seeding a catalog id`() = runTest {
        val matcher = AppleMusicMatcher()
        val mapper = AppleMusicTrackMapper(matcher, { youtubeSong() }, StandardTestDispatcher(testScheduler))
        val row = mapper.match(libraryTrack("87654", null), AppleMusicTrackOrigin.LIBRARY, "us")
        assertEquals("87654", row.libraryId)
        assertNull(row.catalogId)
        assertTrue(row.isPlayable(false, false))
        assertNull(matcher.cached("us", row.song.videoId))
    }

    @Test
    fun `catalog search ids are never presented as library ids`() = runTest {
        val mapper = AppleMusicTrackMapper(AppleMusicMatcher(), { null }, StandardTestDispatcher(testScheduler))
        val row = mapper.match(libraryTrack("123", "123"), AppleMusicTrackOrigin.CATALOG, "us")
        assertEquals("123", row.track.id)
        assertEquals("123", row.catalogId)
        assertNull(row.libraryId)
    }

    @Test
    fun `lookup failures retain Apple fallback but cancellation is not a miss`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val failed = AppleMusicTrackMapper(AppleMusicMatcher(), { throw IOException("secret") }, dispatcher)
            .match(libraryTrack(), AppleMusicTrackOrigin.LIBRARY, "us")
        assertTrue(failed.lookupFailed)
        assertEquals(AppleMusicTrackMatchStatus.APPLE_ONLY, failed.status)

        val mapper = AppleMusicTrackMapper(AppleMusicMatcher(), { throw CancellationException() }, dispatcher)
        try {
            mapper.match(libraryTrack(), AppleMusicTrackOrigin.LIBRARY, "us")
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            // Expected: cancellation must not create an Apple-only/negative row.
        }
    }

    @Test
    fun `external placeholder and video results are never accepted as YouTube matches`() = runTest {
        for (song in listOf(youtubeSong("sp-miss:123"), youtubeSong("am-miss:123"), youtubeSong().copy(isVideo = true))) {
            val mapper = AppleMusicTrackMapper(AppleMusicMatcher(), { song }, StandardTestDispatcher(testScheduler))
            val row = mapper.match(libraryTrack(), AppleMusicTrackOrigin.LIBRARY, "us")
            assertEquals(AppleMusicTrackMatchStatus.APPLE_ONLY, row.status)
        }
    }

    @Test
    fun `worker pool is bounded while preserving order and duplicate entries`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var active = 0
        var maximum = 0
        val mapper = AppleMusicTrackMapper(AppleMusicMatcher(), {
            active++
            maximum = maxOf(maximum, active)
            gate.await()
            active--
            youtubeSong("yt-${it.id}")
        }, StandardTestDispatcher(testScheduler))
        val tracks = (0..19).map { libraryTrack("i.$it") } + libraryTrack("i.0")
        val work = async { mapper.matchAll(tracks, AppleMusicTrackOrigin.LIBRARY, "us") }
        runCurrent()
        assertEquals(6, active)
        gate.complete(Unit)
        val rows = work.await()
        assertEquals(6, maximum)
        assertEquals(tracks, rows.map { it.track })
        assertEquals(rows.first(), rows.last())
    }
}
