package com.music.bitchord.ui.replay

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.music.bitchord.data.stats.ArtistFacts
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.data.stats.ReplaySummary
import com.music.bitchord.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Replay's state: which stretch of listening is being shown, and the
 * numbers for it.
 *
 * Held here rather than in [com.music.bitchord.ui.MainViewModel] because it is
 * only alive while the page is: the summary is a merge of a few files and is
 * cheap to make, and keeping a copy of every chart in a view model that outlives
 * the screen would hold artwork URLs and a few hundred rows for the rest of the
 * session in exchange for saving a hundred milliseconds nobody would notice.
 *
 * The *period* does survive, because it is a choice rather than a result.
 */
class ReplayState(
    val period: ReplayPeriod,
    val summary: ReplaySummary?,
    val loading: Boolean,
    /**
     * `MM/YY` of the first month this device recorded anything, for the card.
     *
     * Deliberately all-time rather than the open period's own first month: a
     * card that says "member since" has to mean since you started, and reading
     * it off the period would have it announce a new membership every time the
     * chips were switched to This month.
     */
    val memberSince: String?,
)

/**
 * @param active whether the Replay is open in any of its three forms — the
 *   page, the stories, the share sheet. The state is hoisted to the app so all
 *   three read one set of numbers, and this is what stops that hoisting from
 *   costing a file merge on every cold start for a page most launches never
 *   open. It also means reopening Replay re-reads: whatever has been played
 *   since is on it.
 */
@Composable
fun rememberReplayState(active: Boolean): Pair<ReplayState, (ReplayPeriod) -> Unit> {
    var period by rememberSaveable { mutableStateOf(ReplayPeriod.THIS_YEAR) }
    var summary by remember { mutableStateOf<ReplaySummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    var memberSince by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        // A directory listing, so off the composition's thread.
        memberSince = withContext(Dispatchers.IO) {
            ListeningStats.months().firstOrNull()?.let {
                "%02d/%02d".format(Locale.ROOT, it.monthValue, it.year % 100)
            }
        }
    }
    LaunchedEffect(period, active) {
        if (!active) return@LaunchedEffect
        // Only the first read shows a spinner. Switching period must not blank
        // the charts for the beat it takes to merge the files — that reads as
        // the page breaking rather than as it answering a different question.
        loading = summary == null
        summary = ListeningStats.summary(period)
        loading = false

        // Artist pictures and pages arrive after the page has been built — see
        // [ArtistFacts.revision] — so the charts are rebuilt when they do.
        //
        // Collected inside the effect rather than as composed state on purpose:
        // this function is called from the app's root, so a revision held as
        // state would recompose the whole tree every time a lookup landed, even
        // with the Replay closed. `collectLatest` gives the debounce for free —
        // a burst of lookups cancels each pending delay and only the last one
        // gets as far as a rebuild.
        ArtistFacts.revision.drop(1).collectLatest {
            delay(SETTLE_MILLIS)
            summary = ListeningStats.summary(period)
        }
    }
    return ReplayState(period, summary, loading, memberSince) to
        { next: ReplayPeriod -> period = next }
}

/** How long a burst of artist lookups is allowed to settle before a rebuild. */
private const val SETTLE_MILLIS = 1_200L

/**
 * One run of a card's headline, and whether it is the emphasised part.
 *
 * The sentence lives here rather than in the story that draws it because it is
 * drawn twice — once on screen and once into the picture the share button
 * produces — and a card that says something different in the version people
 * send is worse than no picture at all.
 */
data class HeadlineRun(val text: String, val bold: Boolean)

private fun runs(vararg parts: Pair<String, Boolean>): List<HeadlineRun> =
    parts.map { HeadlineRun(it.first, it.second) }

/** The sentence at the top of [page]. */
fun ReplaySummary.storyHeadline(context: Context, page: ReplayStoryPage): List<HeadlineRun> = when (page) {
    ReplayStoryPage.INTRO -> runs(
        context.getString(R.string.replay_intro_start) to false,
        "Replay" to true,
        context.getString(R.string.replay_intro_end) to false,
    )
    ReplayStoryPage.MINUTES -> runs(
        context.getString(R.string.replay_minutes_start) to false,
        context.getString(R.string.replay_minutes_value, formatMinutes(totalMs)) to true,
        context.getString(R.string.replay_minutes_end) to false,
    )
    ReplayStoryPage.SONGS -> runs(
        context.getString(R.string.replay_songs_start) to false,
        context.replayCount(totalPlays, R.plurals.replay_song_count) to true,
        context.getString(R.string.replay_songs_end) to false,
    )
    ReplayStoryPage.ARTISTS -> runs(
        context.getString(R.string.replay_artist_start) to false,
        context.getString(R.string.replay_artist_focus) to true,
        context.getString(R.string.replay_artist_end) to false,
    )
    ReplayStoryPage.ALBUMS -> runs(
        context.getString(R.string.replay_album_start) to false,
        context.getString(R.string.replay_album_focus) to true,
        context.getString(R.string.replay_album_end) to false,
    )
    ReplayStoryPage.GENRES -> runs(
        context.getString(R.string.replay_genre_start) to false,
        context.getString(R.string.replay_genre_focus) to true,
        context.getString(R.string.replay_genre_end) to false,
    )
    ReplayStoryPage.HABITS -> runs(
        context.getString(R.string.replay_habits_start) to false,
        context.replayCount(distinctSongs, R.plurals.replay_song_count) to true,
        context.getString(R.string.replay_habits_middle) to false,
        context.replayCount(distinctArtists, R.plurals.replay_artist_count) to true,
        "." to false,
    )
    ReplayStoryPage.SUMMARY -> runs(
        context.getString(R.string.replay_summary_start) to false,
        label to true,
        "." to false,
    )
}

fun ReplayPeriod.localizedChip(context: Context): String = when (this) {
    ReplayPeriod.THIS_MONTH -> context.getString(R.string.this_month)
    ReplayPeriod.THIS_YEAR -> context.getString(R.string.this_year)
    ReplayPeriod.ALL_TIME -> context.getString(R.string.all_time)
}

fun ReplaySummary.localizedLabel(context: Context): String = when (period) {
    ReplayPeriod.THIS_MONTH -> DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
        .format(LocalDate.now())
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    ReplayPeriod.THIS_YEAR -> LocalDate.now().year.toString()
    ReplayPeriod.ALL_TIME -> context.getString(R.string.all_time)
}

/**
 * Which cover a card is washed in.
 *
 * The three cards that are *about* something take that thing's artwork. The rest
 * walk a pool of everything on the Replay, so a run of eight cards is lit by
 * eight different records rather than by the top song eight times.
 */
fun ReplaySummary.storyArtwork(page: ReplayStoryPage): String? {
    val pool = (
        songs.map { it.song.thumbnailUrl } +
            artists.map { it.artworkUrl } +
            albums.map { it.artworkUrl }
        ).filterNotNull().distinct()
    val pinned = when (page) {
        ReplayStoryPage.SONGS -> songs.firstOrNull()?.song?.thumbnailUrl
        ReplayStoryPage.ARTISTS -> artists.firstOrNull()?.artworkUrl
        ReplayStoryPage.ALBUMS -> albums.firstOrNull()?.artworkUrl
        else -> null
    }
    if (pinned != null) return pinned
    if (pool.isEmpty()) return null
    return pool[page.ordinal % pool.size]
}

/**
 * How far a card's palette is turned around the colour wheel.
 *
 * The mesh is sampled from artwork, and a Replay is frequently four covers by
 * two artists with the same art direction — which is a run of eight cards in one
 * shade of blue. Rotating the hue per card is what makes the story *look* like a
 * story: every one arrives a different colour, and because only the hue moves,
 * the saturation and lightness the mesh was tuned for are untouched, so no card
 * comes out muddy or blown out.
 *
 * The steps are irregular rather than an even eighth of the wheel: an even walk
 * reads as a colour-picker demo, and the gaps here keep neighbours far enough
 * apart to be obviously different without the run looking mechanical.
 */
fun storyHue(page: ReplayStoryPage): Float = when (page) {
    ReplayStoryPage.INTRO -> 0f
    ReplayStoryPage.MINUTES -> 40f
    ReplayStoryPage.SONGS -> 95f
    ReplayStoryPage.ARTISTS -> 145f
    ReplayStoryPage.ALBUMS -> 195f
    ReplayStoryPage.GENRES -> 240f
    ReplayStoryPage.HABITS -> 285f
    ReplayStoryPage.SUMMARY -> 325f
}

/** Which page of the story view something opens onto. */
enum class ReplayStoryPage {
    INTRO, MINUTES, ARTISTS, SONGS, ALBUMS, GENRES, HABITS, SUMMARY;

    companion object {
        val ordered: List<ReplayStoryPage> = entries
    }
}

// ── Formatting ──────────────────────────────────────────────────────────────

/**
 * Listening time, in the largest unit that still says something.
 *
 * Minutes up to a day's worth, then hours: "1,284 minutes" is a number people
 * read as a number, and "21 hours" is one they read as an amount. Past a
 * thousand hours neither works and it becomes days.
 */
fun formatListening(context: Context, ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 60 -> context.getString(R.string.minutes_short, minutes)
        minutes < 1_440 -> context.getString(R.string.hours_minutes_short, minutes / 60, minutes % 60)
        else -> context.getString(R.string.minutes_grouped_short, grouped(minutes))
    }
}

/** The headline figure on the minutes card: always minutes, always grouped. */
fun formatMinutes(ms: Long): String = grouped(ms / 60_000)

fun grouped(value: Long): String = String.format(Locale.US, "%,d", value)

/** "3 pm", "midnight" — an hour of the day said the way anyone would say it. */
fun formatHour(context: Context, hour: Int): String = when (hour) {
    0 -> context.getString(R.string.midnight)
    12 -> context.getString(R.string.midday)
    in 1..11 -> context.getString(R.string.hour_am, hour)
    else -> context.getString(R.string.hour_pm, hour - 12)
}

/** `2026-08-14` as "14 August". */
fun formatDay(context: Context, iso: String): String = runCatching {
    LocalDate.parse(iso).format(
        DateTimeFormatter.ofPattern("d MMMM", context.resources.configuration.locales[0]),
    )
}.getOrDefault(iso)

fun Context.replayCount(count: Int, plural: Int): String =
    resources.getQuantityString(plural, count, grouped(count.toLong()))

// ── Rows and cards ──────────────────────────────────────────────────────────

/**
 * One line of one chart, with the four categories flattened onto a common
 * shape.
 *
 * Songs, artists, albums and genres are unlike enough in the data layer to be
 * kept apart there and alike enough on screen to be drawn once. [rank] is
 * carried on the row rather than derived from its index because the same row is
 * drawn in three places — the page, a story, the shared poster — and only one of
 * them has an index to hand.
 */
data class ReplayRow(
    val key: String,
    val rank: Int,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    val ms: Long,
    val plays: Int,
)

fun ReplaySummary.songRows(limit: Int): List<ReplayRow> =
    songs.take(limit).mapIndexed { index, entry ->
        ReplayRow(
            key = entry.song.videoId,
            rank = index + 1,
            title = entry.song.title,
            subtitle = entry.song.artist.takeIf { it.isNotBlank() },
            artworkUrl = entry.song.thumbnailUrl,
            ms = entry.ms,
            plays = entry.plays,
        )
    }

fun ReplaySummary.artistRows(limit: Int): List<ReplayRow> =
    artists.take(limit).mapIndexed { index, entry ->
        ReplayRow(
            key = entry.title,
            rank = index + 1,
            title = entry.title,
            subtitle = null,
            artworkUrl = entry.artworkUrl,
            ms = entry.ms,
            plays = entry.plays,
        )
    }

fun ReplaySummary.albumRows(limit: Int): List<ReplayRow> =
    albums.take(limit).mapIndexed { index, entry ->
        ReplayRow(
            key = entry.title + "|" + entry.subtitle.orEmpty(),
            rank = index + 1,
            title = entry.title,
            subtitle = entry.subtitle,
            artworkUrl = entry.artworkUrl,
            ms = entry.ms,
            plays = entry.plays,
        )
    }

/**
 * Genres, with the artwork deliberately dropped.
 *
 * The cover of whichever artist happened to lead the genre is not a picture of
 * the genre, and putting it there makes a row of five look like five artists
 * mislabelled. [com.music.bitchord.ui.replay.InitialTile] stands in instead.
 */
fun ReplaySummary.genreRows(limit: Int): List<ReplayRow> =
    genres.take(limit).mapIndexed { index, entry ->
        ReplayRow(
            key = entry.title,
            rank = index + 1,
            title = entry.title,
            subtitle = null,
            artworkUrl = null,
            ms = entry.ms,
            plays = entry.plays,
        )
    }

/** One of the cards along the top of the page. */
data class ReplayHeroCard(
    val label: String,
    val value: String,
    val detail: String?,
    val artworkUrl: String?,
    val page: ReplayStoryPage,
)

/**
 * The four headline facts, in the order they are dealt.
 *
 * Minutes leads because it is the one figure that needs no context to mean
 * something. A category with nothing in it is left out rather than shown empty:
 * a Replay of loose singles has no album chart, and a card reading "—" is worse
 * than three cards.
 */
fun ReplaySummary.cards(context: Context): List<ReplayHeroCard> = buildList {
    add(
        ReplayHeroCard(
            label = context.getString(R.string.minutes_listened),
            value = formatMinutes(totalMs),
            detail = "${context.replayCount(totalPlays, R.plurals.replay_play_count)} · " +
                localizedLabel(context),
            artworkUrl = songs.firstOrNull()?.song?.thumbnailUrl,
            page = ReplayStoryPage.MINUTES,
        ),
    )
    artists.firstOrNull()?.let {
        add(
            ReplayHeroCard(
                label = context.getString(R.string.top_artist),
                value = it.title,
                detail = "${formatListening(context, it.ms)} · " +
                    context.replayCount(it.plays, R.plurals.replay_play_count),
                artworkUrl = it.artworkUrl,
                page = ReplayStoryPage.ARTISTS,
            ),
        )
    }
    songs.firstOrNull()?.let {
        add(
            ReplayHeroCard(
                label = context.getString(R.string.top_song),
                value = it.song.title,
                detail = "${it.song.artist} · ${context.replayCount(it.plays, R.plurals.replay_play_count)}",
                artworkUrl = it.song.thumbnailUrl,
                page = ReplayStoryPage.SONGS,
            ),
        )
    }
    albums.firstOrNull()?.let {
        add(
            ReplayHeroCard(
                label = context.getString(R.string.top_album),
                value = it.title,
                detail = listOfNotNull(it.subtitle, formatListening(context, it.ms)).joinToString(" · "),
                artworkUrl = it.artworkUrl,
                page = ReplayStoryPage.ALBUMS,
            ),
        )
    }
}
