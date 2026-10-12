package com.music.bitchord

import com.music.bitchord.download.AutoDownloads
import com.music.bitchord.download.DownloadTier
import com.music.bitchord.download.SavedSongMetadata
import com.music.bitchord.download.tier
import com.music.bitchord.data.stats.TrackEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoDownloadScoreTest {

    private fun entry(
        plays: Int,
        ageDays: Long,
        skips: Int = 0,
    ) = TrackEntry(
        id = "id",
        title = "t",
        artist = "a",
        ms = plays * 200_000L,
        plays = plays,
        last = System.currentTimeMillis() - ageDays * 24 * 60 * 60 * 1000,
        skips = skips,
    )

    @Test
    fun hitBeatsCasualListen() {
        val hit = AutoDownloads.score(entry(plays = 12, ageDays = 1), liked = true)
        val casual = AutoDownloads.score(entry(plays = 4, ageDays = 20), liked = false)
        assertTrue(hit > casual)
    }

    @Test
    fun skipsDragHeavilyPlayedDown() {
        val clean = AutoDownloads.score(entry(plays = 15, ageDays = 70), liked = false)
        val skipped = AutoDownloads.score(entry(plays = 15, ageDays = 70, skips = 6), liked = false)
        assertTrue(skipped < clean)
    }

    @Test
    fun likedWithNoHistoryStillScores() {
        assertEquals(1.0, AutoDownloads.score(null, liked = true), 0.0)
        assertEquals(0.0, AutoDownloads.score(null, liked = false), 0.0)
    }

    @Test
    fun cachedAddsBonusOnTop() {
        val plain = AutoDownloads.score(entry(plays = 5, ageDays = 10), liked = false)
        val cached = AutoDownloads.score(entry(plays = 5, ageDays = 10), liked = false, cached = true)
        assertEquals(AutoDownloads.CACHED_BONUS, cached - plain, 1e-9)
    }

    @Test
    fun cachedNullEntryBeatsUncached() {
        assertEquals(
            AutoDownloads.CACHED_BONUS,
            AutoDownloads.score(null, liked = false, cached = true),
            0.0,
        )
    }

    @Test
    fun forgottenLikedGetsABump() {
        val recent = AutoDownloads.score(entry(plays = 6, ageDays = 2), liked = true)
        val forgotten = AutoDownloads.score(entry(plays = 6, ageDays = 30), liked = true)
        // Older, but the forgotten-favorite bump outweighs the recency loss.
        assertTrue(forgotten > recent)
    }

    @Test
    fun recencyOrdersSamePlayCount() {
        val fresh = AutoDownloads.score(entry(plays = 5, ageDays = 2), liked = false)
        val stale = AutoDownloads.score(entry(plays = 5, ageDays = 60), liked = false)
        assertTrue(fresh > stale)
    }

    @Test
    fun tierPrefersDolbyThenFlac() {
        val dolby = SavedSongMetadata(
            videoId = "a", title = "t", artist = "a",
            uri = "file:///x", downloadFormat = "DOLBY", isDolby = true,
        ).tier()
        val flac = SavedSongMetadata(
            videoId = "b", title = "t", artist = "a",
            uri = "file:///y", downloadFormat = "FLAC", kbps = 900,
        ).tier()
        val high = SavedSongMetadata(
            videoId = "c", title = "t", artist = "a",
            uri = "file:///z", downloadFormat = "OPUS · 160 kbps", kbps = 160,
        ).tier()
        // 160 < 300 rung → standard bucket by kbps.
        assertEquals(DownloadTier.DOLBY, dolby)
        assertEquals(DownloadTier.LOSSLESS, flac)
        assertEquals(DownloadTier.STANDARD, high)
    }

    @Test
    fun tierReadsKbpsBackOffOldBadges() {
        // Files saved before kbps was recorded: the badge is all there is.
        val jiosaavn = SavedSongMetadata(
            videoId = "d", title = "t", artist = "a",
            uri = "file:///w", downloadFormat = "MP3 · 320 kbps",
        ).tier()
        val oldOpus = SavedSongMetadata(
            videoId = "e", title = "t", artist = "a",
            uri = "file:///v", downloadFormat = "OPUS · 160 kbps",
        ).tier()
        assertEquals(DownloadTier.HIGH, jiosaavn)
        assertEquals(DownloadTier.STANDARD, oldOpus)
    }
}
