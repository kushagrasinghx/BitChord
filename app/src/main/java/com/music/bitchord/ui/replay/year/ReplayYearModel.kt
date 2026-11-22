package com.music.bitchord.ui.replay.year

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.stats.ArtistFacts
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.RankedSong
import com.music.bitchord.data.stats.ReplayYearData
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.Month

/**
 * Everything the year story draws, worked out once when it opens.
 *
 * Which cards are told and each card's song are [YearPlan]'s, planned together
 * for the whole run (the number-one song kept back for its own reveal, no two
 * neighbouring cards scored by the same track); this adds what the phone knows
 * about artists, their genres.
 */
class ReplayYear(val data: ReplayYearData) {
    val year: Int get() = data.year
    private val summary get() = data.summary

    /** The run itself, planned by the shared YearPlan (the web player plans with it too). */
    private val plan = YearPlan(
        YearFacts(
            songs = summary.songs,
            artists = summary.artists,
            albums = summary.albums,
            genres = summary.genres,
            hourOfDay = summary.hourOfDay,
            busiestDay = summary.busiestDay,
            busiestDayMs = summary.busiestDayMs,
            monthTops = data.monthTops.map { it.song.song },
            busiestDaySong = data.busiestDaySong,
            genresFor = ArtistFacts::genresFor,
        ),
    )

    val personality: ListeningPersonality? = plan.personality

    val slides: List<YearSlide> = plan.slides

    /** Each card's song, or null for one with nothing fitting to play. */
    val clips: Map<YearSlide, Song?> = plan.clips

    companion object {
        /**
         * Which year's story the button opens on [today], or null when it isn't
         * up: only December, 1st to 31st, for the year that is ending.
         */
        fun yearShown(today: LocalDate): Int? =
            if (today.month == Month.DECEMBER) today.year else null

        /** @see YearPlan.topSongBy */
        fun topSongBy(songs: List<RankedSong>, artist: String, avoid: Set<String>): Song? =
            YearPlan.topSongBy(songs, artist, avoid)

        /** @see YearPlan.personalityOf */
        fun personalityOf(hours: List<Long>): ListeningPersonality? = YearPlan.personalityOf(hours)
    }
}

/**
 * The year the button on Home offers, or null while there is none.
 *
 * Read a moment after Home appears rather than with it: Home's first frame is
 * the app opening, and a dozen month files are not worth a dropped frame of it.
 * Re-read whenever [active] comes back, so a year that grew while the app was
 * in the background is the year that opens.
 */
@Composable
fun rememberReplayYear(active: Boolean): ReplayYear? {
    var year by remember { mutableStateOf<ReplayYear?>(null) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val shown = ReplayYear.yearShown(LocalDate.now()) ?: run { year = null; return@LaunchedEffect }
        delay(HOME_SETTLE_MS)
        val data = ListeningStats.year(shown)
        year = if (data.summary.isEmpty) null else ReplayYear(data)
    }
    return year
}

private const val HOME_SETTLE_MS = 900L
