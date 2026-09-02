package com.music.bitchord.ui.replay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.stats.ArtistFacts
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.data.stats.ReplaySummary
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.player.MeshGradientBackground
import com.music.bitchord.ui.player.rememberArtworkColors
import com.music.bitchord.ui.theme.AccentRed

/** Whether the Replay page is being shown in dark mode (vs light). */
@Composable
private fun isDarkMode(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * The Replay page: four cards, four charts and a way to share the lot.
 *
 * ## Two ways in to the same numbers
 *
 * The cards along the top are the *story* — one fact each, tappable, and what
 * anyone who opened this page out of curiosity actually wants. Everything under
 * them is the *table* — the same four categories ranked out to ten, for the
 * person who wants to know what came fourth. Wrapped-style apps usually ship
 * only the first and leave the second to a support article; the ranked lists
 * cost a scroll and answer every follow-up question the cards provoke.
 *
 * The Library page carries the *first* of those cards — the minutes — on its
 * own, as the way in. One card there is an invitation; four is a second copy of
 * this page's opening on a page that is about something else.
 *
 * ## Why the page is washed in the top song's colours
 *
 * The mesh backdrop is the player's, and that is the point: this page is about
 * one particular year of listening, and running it in the colours of the record
 * that defined that year ties the two together without a line of copy saying so.
 * It also means two people's Replays do not look alike, which a fixed brand
 * gradient could never manage.
 */
@Composable
fun ReplayScreen(
    state: ReplayState,
    /**
     * The name on the cards — the signed-in account's, or blank for a guest,
     * where [DEFAULT_HOLDER] stands in.
     */
    holder: String,
    onPeriodChange: (ReplayPeriod) -> Unit,
    onOpenStory: (ReplayStoryPage) -> Unit,
    onPlaySong: (Song) -> Unit,
    /**
     * Opens the artist's page. The browse id is null for most rows — an artist
     * is counted by name, and the id only rides along when the track that
     * credited them carried one — so the caller has to be able to find the page
     * from the name alone. Same for the album below.
     */
    onOpenArtist: (String?, String) -> Unit,
    onOpenAlbum: (String?, String, String?, String?) -> Unit,
    onShare: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val context = LocalContext.current
    val summary = state.summary
    val leadArtwork = summary?.songs?.firstOrNull()?.song?.thumbnailUrl
    val palette = rememberArtworkColors(leadArtwork)
    val topSongs = stringResource(R.string.top_songs)
    val topArtists = stringResource(R.string.top_artists)
    val topAlbums = stringResource(R.string.top_albums)
    val topGenres = stringResource(R.string.top_genres)

    Box(modifier.fillMaxSize()) {
        MeshGradientBackground(palette = palette, trackKey = leadArtwork, animated = false)
        // The mesh is built to sit behind a player, where the only thing over it
        // is a handful of large controls. A page of ranked lists needs a good
        // deal more separation than that, so most of it is put back under ink.
        val dark = isDarkMode()
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        if (dark) listOf(
                            Color.Black.copy(alpha = 0.30f),
                            Color.Black.copy(alpha = 0.72f),
                            Color.Black.copy(alpha = 0.88f),
                        ) else listOf(
                            Color.White.copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0.65f),
                            Color.White.copy(alpha = 0.82f),
                        ),
                    ),
                ),
        )

        val textColor = if (dark) Color.White else Color.Black
        val textVariant = if (dark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.55f)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item("heading") { Heading(state, onPeriodChange, dark) }

            when {
                state.loading && summary == null -> item("loading") {
                    Box(Modifier.fillMaxWidth().padding(64.dp), Alignment.Center) {
                        CircularProgressIndicator(color = textColor.copy(alpha = 0.6f))
                    }
                }
                summary == null || summary.isEmpty -> item("empty") { EmptyReplay(state.period, dark) }
                else -> {
                    item("cards") {
                        ReplayCardRow(
                            cards = summary.cards(context),
                            holder = holder,
                            memberSince = state.memberSince,
                            onOpenStory = onOpenStory,
                        )
                    }
                    item("open") {
                        ReplayActionRow(BitChordIcons.Play, stringResource(R.string.play_your_replay), dark) {
                            onOpenStory(ReplayStoryPage.INTRO)
                        }
                    }

                    chart(
                        key = "songs",
                        title = topSongs,
                        rows = summary.songRows(CHART_LENGTH),
                        dark = dark,
                        onClick = { index ->
                            summary.songs.getOrNull(index)?.let { onPlaySong(it.song) }
                        },
                    )
                    chart(
                        key = "artists",
                        title = topArtists,
                        rows = summary.artistRows(CHART_LENGTH),
                        dark = dark,
                        circular = true,
                        onClick = { index ->
                            val artist = summary.artists.getOrNull(index) ?: return@chart
                            onOpenArtist(artist.browseId, artist.title)
                        },
                    )
                    chart(
                        key = "albums",
                        title = topAlbums,
                        rows = summary.albumRows(CHART_LENGTH),
                        dark = dark,
                        onClick = { index ->
                            val album = summary.albums.getOrNull(index) ?: return@chart
                            onOpenAlbum(album.browseId, album.title, album.subtitle, album.artworkUrl)
                        },
                    )
                    if (summary.genres.isNotEmpty()) {
                        chart(
                            key = "genres",
                            title = topGenres,
                            rows = summary.genreRows(CHART_LENGTH),
                            dark = dark,
                            onClick = {},
                        )
                    } else if (ArtistFacts.genresAvailable) {
                        item("genres-pending") {
                            Note(
                                text = stringResource(R.string.genres_pending),
                                dark = dark,
                                modifier = Modifier.padding(horizontal = PAGE_GUTTER + 10.dp),
                            )
                        }
                    }

                    item("habits") { Habits(summary, dark) }
                    item("share") {
                        ReplayActionRow(
                            Icons.Rounded.IosShare,
                            stringResource(R.string.share_my_replay),
                            dark,
                            onShare,
                        )
                    }
                }
            }
        }
    }
}

// ── Heading ─────────────────────────────────────────────────────────────────

@Composable
private fun Heading(state: ReplayState, onPeriodChange: (ReplayPeriod) -> Unit, dark: Boolean) {
    val context = LocalContext.current
    val textColor = if (dark) Color.White else Color.Black
    Column(Modifier.padding(horizontal = PAGE_GUTTER + 10.dp)) {
        Spacer(Modifier.height(48.dp))
        Text(
            text = stringResource(R.string.replay),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.W800,
            color = textColor,
        )
        Text(
            text = state.summary?.localizedLabel(context) ?: state.period.localizedChip(context),
            style = MaterialTheme.typography.titleMedium,
            color = textColor.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(14.dp))
        PeriodPicker(state.period, onPeriodChange, dark)
        Spacer(Modifier.height(18.dp))
    }
}

/**
 * The three stretches a Replay can cover.
 *
 * Months and years rather than a date range, because that is the granularity the
 * listening is actually kept at — see [com.music.bitchord.data.stats.ListeningStats].
 * A "last 30 days" chip would have to be answered from monthly totals, which
 * would make it a lie for the first thirty days of every month.
 */
@Composable
private fun PeriodPicker(selected: ReplayPeriod, onSelect: (ReplayPeriod) -> Unit, dark: Boolean) {
    val context = LocalContext.current
    val textColor = if (dark) Color.White else Color.Black
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(textColor.copy(alpha = 0.10f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ReplayPeriod.entries.forEach { period ->
            val active = period == selected
            val background by animateColorAsState(
                if (active) textColor.copy(alpha = 0.92f) else Color.Transparent,
                tween(160),
                label = "periodChip",
            )
            Text(
                text = period.localizedChip(context),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.W700,
                color = if (active) if (dark) Color.Black else Color.White else textColor.copy(alpha = 0.75f),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(background)
                    .clickable { onSelect(period) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

// ── The cards ───────────────────────────────────────────────────────────────

/**
 * The wallet of cards, wherever it is drawn.
 *
 * It lives on the Library page rather than here — see [ReplayScreen]'s note on
 * where each half of Replay belongs — but it is defined alongside the page it
 * summarises, because the two have to keep saying the same thing.
 */
@Composable
private fun ReplayCardRow(
    cards: List<ReplayHeroCard>,
    holder: String,
    memberSince: String?,
    onOpenStory: (ReplayStoryPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = PAGE_GUTTER + 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(cards, key = { it.label }) { card ->
            ReplayCreditCard(
                label = card.label,
                value = card.value,
                detail = card.detail,
                artworkUrl = card.artworkUrl,
                holder = holder,
                memberSince = memberSince,
                onClick = { onOpenStory(card.page) },
                modifier = Modifier.width(300.dp),
            )
        }
    }
}

/**
 * The two things this page can do, drawn the same way.
 *
 * They sit at either end of it and are the same kind of act — open the Replay,
 * send the Replay — so they have no business looking unalike. The share button
 * used to be a filled red slab, which on a page with no destructive control on
 * it was the loudest thing there and read like one.
 */
@Composable
private fun ReplayActionRow(icon: ImageVector, label: String, dark: Boolean, onClick: () -> Unit) {
    val textColor = if (dark) Color.White else Color.Black
    Row(
        Modifier
            .padding(horizontal = PAGE_GUTTER + 10.dp, vertical = 18.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(textColor.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = BitChordIcons.ChevronRight,
            contentDescription = null,
            tint = textColor.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp),
        )
    }
}

// ── Charts ──────────────────────────────────────────────────────────────────

private fun LazyListScope.chart(
    key: String,
    title: String,
    rows: List<ReplayRow>,
    dark: Boolean,
    onClick: (Int) -> Unit,
    circular: Boolean = false,
) {
    if (rows.isEmpty()) return
    item("$key-title") { SectionTitle(title, dark) }
    items(rows, key = { "$key-${it.key}" }) { row ->
        ReplayChartRow(row, circular, dark) { onClick(row.rank - 1) }
    }
    item("$key-gap") { Spacer(Modifier.height(20.dp)) }
}

@Composable
private fun SectionTitle(text: String, dark: Boolean) {
    val textColor = if (dark) Color.White else Color.Black
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.W800,
        color = textColor,
        modifier = Modifier.padding(
            start = PAGE_GUTTER + 10.dp,
            end = PAGE_GUTTER + 10.dp,
            top = 8.dp,
            bottom = 10.dp,
        ),
    )
}

@Composable
private fun ReplayChartRow(row: ReplayRow, circular: Boolean, dark: Boolean, onClick: () -> Unit) {
    val textColor = if (dark) Color.White else Color.Black
    val textVariant = if (dark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.55f)
    val shape = if (circular) CircleShape else RoundedCornerShape(6.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = PAGE_GUTTER + 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankBadge(row.rank, AccentRed)
        if (row.artworkUrl != null) {
            AsyncImage(
                model = row.artworkUrl.artworkAt(ROW_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(shape),
            )
        } else {
            InitialTile(row.title, 48.dp, shape)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.W600,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(row.subtitle, formatListening(LocalContext.current, row.ms))
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = textVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (row.plays > 0) {
            Text(
                text = row.plays.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = textColor.copy(alpha = 0.45f),
            )
        }
    }
}

// ── Tail ────────────────────────────────────────────────────────────────────

@Composable
private fun Habits(summary: ReplaySummary, dark: Boolean) {
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = PAGE_GUTTER + 10.dp)) {
        SectionTitleInline(stringResource(R.string.listening_shape), dark)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(stringResource(R.string.songs), summary.distinctSongs.toString(), dark, Modifier.weight(1f))
            StatTile(stringResource(R.string.artists), summary.distinctArtists.toString(), dark, Modifier.weight(1f))
            StatTile(stringResource(R.string.albums), summary.distinctAlbums.toString(), dark, Modifier.weight(1f))
        }
        summary.peakHour?.let {
            Spacer(Modifier.height(10.dp))
            Note(stringResource(R.string.listen_most_around, formatHour(context, it)), dark)
        }
        summary.busiestDay?.let {
            Spacer(Modifier.height(6.dp))
            Note(
                stringResource(
                    R.string.biggest_day_note,
                    formatDay(context, it),
                    formatListening(context, summary.busiestDayMs),
                ),
                dark,
            )
        }
    }
}

@Composable
private fun SectionTitleInline(text: String, dark: Boolean) {
    val textColor = if (dark) Color.White else Color.Black
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.W800,
        color = textColor,
        modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
    )
}

@Composable
private fun StatTile(label: String, value: String, dark: Boolean, modifier: Modifier = Modifier) {
    val textColor = if (dark) Color.White else Color.Black
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(textColor.copy(alpha = 0.09f))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.W800,
            color = textColor,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor.copy(alpha = 0.55f),
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun Note(text: String, dark: Boolean, modifier: Modifier = Modifier) {
    val textColor = if (dark) Color.White else Color.Black
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = textColor.copy(alpha = 0.6f),
        modifier = modifier,
    )
}

@Composable
private fun EmptyReplay(period: ReplayPeriod, dark: Boolean) {
    val emptyColor = if (dark) Color.White else Color.Black
    val emptyVariant = if (dark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.4f)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = BitChordIcons.Clock,
            contentDescription = null,
            tint = emptyVariant,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.not_enough_listening),
            style = MaterialTheme.typography.titleLarge,
            color = emptyColor,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when (period) {
                ReplayPeriod.THIS_MONTH -> stringResource(R.string.not_enough_this_month)
                else -> stringResource(R.string.replay_empty_description)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = emptyColor.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * How far down each chart the page goes.
 *
 * Five. Ten was the first guess and it made the page a scroll of forty rows
 * whose second half nobody has an opinion about — a top five is a result, and a
 * top ten is a list.
 */
private const val CHART_LENGTH = 5
