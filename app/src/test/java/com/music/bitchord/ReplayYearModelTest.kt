package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.stats.RankedEntry
import com.music.bitchord.data.stats.RankedSong
import com.music.bitchord.data.stats.ReplayMonthTop
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.data.stats.ReplaySummary
import com.music.bitchord.data.stats.ReplayYearData
import com.music.bitchord.ui.replay.year.ListeningPersonality
import com.music.bitchord.ui.replay.year.ReplayYear
import com.music.bitchord.ui.replay.year.YearSlide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Pins how the Replay year story plans its run of cards and their songs. */
class ReplayYearModelTest {

    private fun hours(vararg at: Pair<Int, Long>): List<Long> =
        List(24) { h -> at.firstOrNull { it.first == h }?.second ?: 0L }

    @Test
    fun lateListeningIsANightOwl() {
        assertEquals(
            ListeningPersonality.NIGHT_OWL,
            ReplayYear.personalityOf(hours(23 to 40L, 1 to 20L, 14 to 60L, 18 to 60L)),
        )
    }

    @Test
    fun morningListeningIsAnEarlyRiser() {
        assertEquals(ListeningPersonality.EARLY_BIRD, ReplayYear.personalityOf(hours(7 to 50L, 15 to 50L, 19 to 40L)))
    }

    @Test
    fun anEvenDayIsAroundTheClock() {
        assertEquals(ListeningPersonality.ALL_DAY, ReplayYear.personalityOf(List(24) { 10L }))
    }

    @Test
    fun nothingHeardHasNoPersonality() {
        assertNull(ReplayYear.personalityOf(List(24) { 0L }))
    }

    @Test
    fun theButtonIsUpOnlyThroughDecember() {
        assertEquals(2026, ReplayYear.yearShown(LocalDate.of(2026, 12, 1)))
        assertEquals(2026, ReplayYear.yearShown(LocalDate.of(2026, 12, 31)))
        assertNull(ReplayYear.yearShown(LocalDate.of(2026, 11, 30)))
        assertNull(ReplayYear.yearShown(LocalDate.of(2027, 1, 1)))
    }

    private fun song(id: String, artist: String, album: String? = null) =
        Song(videoId = id, title = "Song $id", artist = artist, thumbnailUrl = "https://art/$id", albumName = album)

    private fun summary(
        songs: List<RankedSong>,
        artists: List<String>,
        albums: Int = 0,
        genres: Int = 0,
        hourOfDay: List<Long> = List(24) { if (it == 21) 100L else 10L },
    ) = ReplaySummary(
        period = ReplayPeriod.THIS_YEAR,
        label = "2026",
        totalMs = songs.sumOf { it.ms },
        totalPlays = songs.sumOf { it.plays },
        songs = songs,
        artists = artists.mapIndexed { i, a -> RankedEntry(a, null, null, null, 1_000L * (10 - i), 10 - i) },
        albums = List(albums) { RankedEntry("Album $it", "x", null, null, 100L, 1) },
        genres = List(genres) { RankedEntry("genre $it", null, null, null, 100L, 1) },
        hourOfDay = hourOfDay,
        busiestDay = "2026-03-14",
        busiestDayMs = 3_600_000L,
        distinctSongs = songs.size,
        distinctArtists = artists.size,
        distinctAlbums = albums,
        since = "2026-01",
    )

    private val chart = listOf(
        RankedSong(song("aaaaaaaaaa1", "Lead"), 9_000_000L, 90),
        RankedSong(song("aaaaaaaaaa2", "Lead"), 8_000_000L, 80),
        RankedSong(song("aaaaaaaaaa3", "Second"), 7_000_000L, 70),
        RankedSong(song("aaaaaaaaaa4", "Third"), 6_000_000L, 60),
        RankedSong(song("aaaaaaaaaa5", "Lead"), 5_000_000L, 50),
        RankedSong(song("aaaaaaaaaa6", "Fourth"), 4_000_000L, 40),
    )

    private fun year(summary: ReplaySummary, months: Int = 3) = ReplayYear(
        ReplayYearData(
            year = 2026,
            summary = summary,
            dayMs = mapOf("2026-03-14" to 3_600_000L),
            monthTops = List(months) { ReplayMonthTop(YearMonth.of(2026, it + 1), chart[it % chart.size]) },
            busiestDaySong = null,
        ),
    )

    @Test
    fun cardsWithTooLittleToSayAreLeftOut() {
        val small = year(summary(chart.take(2), listOf("Lead")), months = 1)
        val slides = small.slides
        assertFalse(YearSlide.TOP_ARTISTS in slides)
        assertFalse(YearSlide.TOP_SONGS in slides)
        assertFalse(YearSlide.ALBUMS in slides)
        assertFalse(YearSlide.GENRES in slides)
        assertFalse(YearSlide.MONTHS in slides)
        assertTrue(YearSlide.SONG_REVEAL in slides)
        assertEquals(YearSlide.SUMMARY, slides.last())
    }

    @Test
    fun theNumberOneSongIsKeptForItsOwnReveal() {
        val full = year(summary(chart, listOf("Lead", "Second", "Third", "Fourth"), albums = 3, genres = 2))
        val top = chart.first().song.videoId
        full.clips.forEach { (slide, song) ->
            if (slide != YearSlide.SONG_REVEAL && slide != YearSlide.SUMMARY) {
                assertNotEquals("$slide played the number one", top, song?.videoId)
            }
        }
        assertEquals(top, full.clips[YearSlide.SONG_REVEAL]?.videoId)
        // The top artist's card plays their best song that isn't the number one.
        assertEquals("aaaaaaaaaa2", full.clips[YearSlide.ARTIST_REVEAL]?.videoId)
    }

    @Test
    fun theTopFiveOnlyPlayOnTheCardsAboutThem() {
        val long = List(14) { i ->
            val artist = listOf("Lead", "Second", "Third", "Fourth")[i % 4]
            RankedSong(song("bbbbbbbbb%02d".format(i), artist, album = if (i == 9) "Album 0" else null), 20_000_000L - i * 1_000_000L, 100 - i)
        }
        val full = year(summary(long, listOf("Lead", "Second", "Third", "Fourth"), albums = 3, genres = 2), months = 4)
        val top = long.take(5).map { it.song.videoId }.toSet()
        val aboutTheTop = setOf(
            YearSlide.SONG_REVEAL, YearSlide.SUMMARY, YearSlide.TOP_SONGS,
            YearSlide.ARTIST_REVEAL, YearSlide.TOP_ARTISTS,
        )
        full.clips.forEach { (slide, song) ->
            if (slide !in aboutTheTop) {
                assertFalse("$slide played a top-five song", song?.videoId in top)
                assertNotNull("$slide was left silent", song)
            }
        }
        // And each of those cards a different song.
        val others = full.clips.filterKeys { it !in aboutTheTop }.values.map { it?.videoId }
        assertEquals(others.size, others.toSet().size)
        // The top-songs card plays from the rest of the five.
        assertTrue(full.clips[YearSlide.TOP_SONGS]?.videoId in top)
    }

    @Test
    fun neighbouringCardsAreNotScoredByTheSameSong() {
        val full = year(summary(chart, listOf("Lead", "Second", "Third", "Fourth"), albums = 3, genres = 2))
        full.slides.zipWithNext().forEach { (a, b) ->
            val first = full.clips[a]?.videoId
            if (first != null) assertNotEquals("$a then $b", first, full.clips[b]?.videoId)
        }
    }
}
