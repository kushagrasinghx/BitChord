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
import java.util.Locale

/**
 * The cards of the Replay year story, in the order they are told.
 *
 * Built like a reveal rather than a report: the small numbers first, then the
 * artist, then the song, then the year laid out month by month, and the
 * summary last because it is the one people send.
 */
enum class YearSlide {
    OPENER, MINUTES, BUSIEST_DAY, CLOCK, GENRES, ARTIST_REVEAL, TOP_ARTISTS,
    SONG_REVEAL, TOP_SONGS, ALBUMS, MONTHS, DISCOVERY, SUMMARY;

    /**
     * How far into the card its big moment lands, in ms.
     *
     * The clip is started early by this much less the moment the chorus's
     * first word is sung, so the reveal and the hook arrive together. The
     * two countdown cards hold their reveal back for the suspense.
     */
    val revealAtMs: Long
        get() = when (this) {
            ARTIST_REVEAL -> 3_400L
            SONG_REVEAL -> 3_000L
            OPENER -> 1_200L
            else -> 1_600L
        }

    /** Where the card's palette is turned on the colour wheel, as the Replay story does. */
    val hue: Float
        get() = HUES[ordinal]

    private companion object {
        // Irregular steps for the reason in storyHue: an even walk looks like a demo.
        val HUES = floatArrayOf(0f, 40f, 75f, 120f, 160f, 205f, 235f, 270f, 300f, 330f, 20f, 100f, 180f)
    }
}

/** What the clock dial says about when someone listens. */
enum class ListeningPersonality { NIGHT_OWL, EARLY_BIRD, GOLDEN_HOUR, DAYTIME, ALL_DAY }

/**
 * Everything the year story draws, worked out once when it opens.
 *
 * Each card's song is chosen here rather than by the card so the whole run can
 * be planned together: the number-one song is kept back for its own reveal,
 * and the cards on either side of each other aren't scored by the same track.
 */
class ReplayYear(val data: ReplayYearData) {
    val year: Int get() = data.year
    private val summary get() = data.summary

    // Before [slides]: whether the clock card is told depends on it.
    val personality: ListeningPersonality? = personalityOf(summary.hourOfDay)

    val slides: List<YearSlide> = YearSlide.entries.filter(::eligible)

    /** Each card's song, or null for one with nothing fitting to play. */
    val clips: Map<YearSlide, Song?> = planClips()

    private fun eligible(slide: YearSlide): Boolean = when (slide) {
        YearSlide.BUSIEST_DAY -> summary.busiestDay != null && summary.busiestDayMs > 0
        YearSlide.CLOCK -> personality != null
        YearSlide.GENRES -> summary.genres.isNotEmpty()
        YearSlide.ARTIST_REVEAL -> summary.artists.isNotEmpty()
        YearSlide.TOP_ARTISTS -> summary.artists.size >= 3
        YearSlide.TOP_SONGS -> summary.songs.size >= 3
        YearSlide.ALBUMS -> summary.albums.size >= 3
        YearSlide.MONTHS -> data.monthTops.size >= 2
        else -> true
    }

    /**
     * The top five songs belong to the cards about them: the number one to its
     * own reveal and the closing summary, the two artist cards to those
     * artists' best songs, and the top-songs card to the rest of the five.
     * Every other card plays something from further down the year, each a
     * different song, so the run doesn't keep coming back to the same few.
     */
    private fun planClips(): Map<YearSlide, Song?> {
        val songs = summary.songs
        val first = songs.firstOrNull()?.song
        val top = songs.take(TOP_RESERVED).map { it.song.videoId }.toSet()
        val used = mutableSetOf<String>().apply { first?.let { add(it.videoId) } }
        fun claim(song: Song?): Song? = song?.also { used += it.videoId }
        // An artist whose only song is already taken still gets it, rather than silence.
        fun by(artist: String?): Song? = artist?.let { topSongBy(songs, it, used) ?: topSongBy(songs, it, emptySet()) }

        // The cards about the top songs and artists, which may use the top five.
        val artistReveal = claim(by(summary.artists.firstOrNull()?.title))
        val topArtists = claim(by(summary.artists.getOrNull(1)?.title))
        val topSongs = claim(songs.drop(1).take(TOP_RESERVED - 1).firstOrNull { it.song.videoId !in used }?.song)

        // Everyone else: below the top five, a different song each. A short
        // year with nothing down there falls back to the lower end of the five
        // rather than a silent card.
        val rest = songs.drop(TOP_RESERVED).map { it.song } + songs.drop(1).take(TOP_RESERVED - 1).reversed().map { it.song }
        fun free(song: Song?): Song? = song?.takeIf { it.videoId !in used && it.videoId !in top }
        fun next(): Song? = claim(rest.firstOrNull { it.videoId !in used })
        fun prefer(song: Song?): Song? = claim(free(song)) ?: next()

        val genreArtist = summary.genres.firstOrNull()?.title?.let { genre ->
            summary.artists.firstOrNull { genre in ArtistFacts.genresFor(it.title) }?.title
        }
        val topAlbum = summary.albums.firstOrNull()?.title
        fun byOutsideTop(artist: String?): Song? =
            artist?.let { topSongBy(songs, it, used + top) }

        val planned = HashMap<YearSlide, Song?>()
        planned[YearSlide.SONG_REVEAL] = first
        planned[YearSlide.SUMMARY] = first
        planned[YearSlide.ARTIST_REVEAL] = artistReveal
        planned[YearSlide.TOP_ARTISTS] = topArtists
        planned[YearSlide.TOP_SONGS] = topSongs
        // The cards with a song of their own to hand take it first, if it is
        // outside the top five; then the rest in the order they are told.
        if (YearSlide.BUSIEST_DAY in slides) planned[YearSlide.BUSIEST_DAY] = prefer(data.busiestDaySong)
        if (YearSlide.GENRES in slides) planned[YearSlide.GENRES] = prefer(byOutsideTop(genreArtist))
        if (YearSlide.ALBUMS in slides) {
            planned[YearSlide.ALBUMS] = prefer(songs.firstOrNull { it.song.albumName == topAlbum && free(it.song) != null }?.song)
        }
        if (YearSlide.MONTHS in slides) {
            planned[YearSlide.MONTHS] = prefer(data.monthTops.asReversed().firstOrNull { free(it.song.song) != null }?.song?.song)
        }
        slides.filter { it !in planned }.forEach { planned[it] = next() }

        return LinkedHashMap<YearSlide, Song?>().apply { slides.forEach { put(it, planned[it]) } }
    }

    companion object {
        /**
         * Which year's story the button opens on [today], or null when it isn't
         * up: only December, 1st to 31st, for the year that is ending.
         */
        fun yearShown(today: LocalDate): Int? =
            if (today.month == Month.DECEMBER) today.year else null

        /**
         * The song most played by [artist], other than any in [avoid].
         *
         * Matched on the lead name of each song's credit, the same way the
         * artist chart counts, so a feature counts for the lead and not for
         * the guest.
         */
        fun topSongBy(songs: List<RankedSong>, artist: String, avoid: Set<String>): Song? {
            val want = artist.trim().lowercase(Locale.ROOT)
            return songs.firstOrNull { ranked ->
                ranked.song.videoId !in avoid &&
                    (ListeningStats.primaryArtist(ranked.song.artist) ?: ranked.song.artist)
                        .trim().lowercase(Locale.ROOT) == want
            }?.song
        }

        /**
         * What a day's listening says about someone, from milliseconds per hour.
         *
         * A label only when a stretch of the day clearly leads: a third of the
         * listening after ten at night, or before ten in the morning, and so
         * on. Anything more even than that is all day. Null with nothing heard.
         */
        fun personalityOf(hours: List<Long>): ListeningPersonality? {
            val total = hours.sum().toDouble()
            if (total <= 0) return null
            fun share(range: IntRange) = range.sumOf { hours.getOrElse(it) { 0L } } / total
            val night = share(22..23) + share(0..3)
            val morning = share(5..9)
            val evening = share(17..21)
            val daytime = share(10..16)
            return when {
                night >= 0.30 -> ListeningPersonality.NIGHT_OWL
                morning >= 0.30 -> ListeningPersonality.EARLY_BIRD
                evening >= 0.40 -> ListeningPersonality.GOLDEN_HOUR
                daytime >= 0.50 -> ListeningPersonality.DAYTIME
                else -> ListeningPersonality.ALL_DAY
            }
        }
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

/** How far down the chart counts as a top song, kept for the cards about them. */
private const val TOP_RESERVED = 5
