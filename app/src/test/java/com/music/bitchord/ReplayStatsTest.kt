package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.stats.DayStats
import com.music.bitchord.data.stats.RankedSong
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.data.stats.ReplaySummary
import com.music.bitchord.data.stats.TrackEntry
import com.music.bitchord.ui.replay.ReplayStoryPage
import com.music.bitchord.ui.replay.albumRows
import com.music.bitchord.ui.replay.artistRows
import com.music.bitchord.ui.replay.formatListening
import com.music.bitchord.ui.replay.formatMinutes
import com.music.bitchord.ui.replay.songRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReplayStatsTest {

    @Test
    fun weeklyRangeFiltersDaysCorrectly() {
        val entry = TrackEntry(
            id = "test_song",
            title = "Close To Me",
            artist = "Ellie Goulding",
            ms = 60_000L,
            plays = 0,
            days = mutableMapOf(
                "2026-11-02" to DayStats(ms = 30_000L, plays = 0),
                "2026-11-04" to DayStats(ms = 30_000L, plays = 0),
                "2026-10-25" to DayStats(ms = 50_000L, plays = 1), // outside range
            ),
        )
        val range = LocalDate.of(2026, 11, 2) to LocalDate.of(2026, 11, 8)
        val (ms, plays) = entry.weekRange(range)

        assertEquals(60_000L, ms)
        assertEquals(0, plays)
    }

    @Test
    fun replaySummaryHandlesZeroPlaysAndFewMinutesDefensively() {
        val summary = ReplaySummary(
            period = ReplayPeriod.THIS_YEAR,
            label = "2026",
            totalMs = 65_000L,
            totalPlays = 0,
            songs = listOf(
                RankedSong(
                    song = Song(
                        videoId = "close_to_me",
                        title = "Close To Me",
                        artist = "Ellie Goulding",
                    ),
                    ms = 65_000L,
                    plays = 0,
                ),
            ),
            artists = emptyList(),
            albums = emptyList(),
            genres = emptyList(),
            hourOfDay = List(24) { 0L },
            busiestDay = "2026-11-04",
            busiestDayMs = 65_000L,
            distinctSongs = 1,
            distinctArtists = 0,
            distinctAlbums = 0,
            since = "2026-11",
        )

        assertFalse(summary.isEmpty)
        assertEquals(1L, summary.minutes)
        assertEquals("1", formatMinutes(summary.totalMs))

        val songs = summary.songRows(5)
        assertEquals(1, songs.size)
        assertEquals("close_to_me", songs[0].key)
        assertEquals(0, songs[0].plays)

        val artists = summary.artistRows(5)
        assertTrue(artists.isEmpty())

        val albums = summary.albumRows(5)
        assertTrue(albums.isEmpty())
    }

    @Test
    fun candidateDateParsingMatchesIsoFormatWithoutSlashReplacement() {
        val month = "2026-11"
        val day = 4
        val candidate = "$month-%02d".format(day)
        assertEquals("2026-11-04", candidate)

        val parsed = runCatching { LocalDate.parse(candidate) }.getOrNull()
        assertNotNull(parsed)
        assertEquals(LocalDate.of(2026, 11, 4), parsed)

        // Verifying that replace("-", "/") caused parsing failure
        val brokenParse = runCatching { LocalDate.parse(candidate.replace("-", "/")) }.getOrNull()
        assertEquals(null, brokenParse)
    }
}
