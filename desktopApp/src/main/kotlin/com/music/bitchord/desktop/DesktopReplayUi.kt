package com.music.bitchord.desktop

import com.music.bitchord.ui.replay.ReplayCreditCard
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.icons.BitChordIcons
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── The banner on the Library page ──────────────────────────────────────────

// ── The page ────────────────────────────────────────────────────────────────

@Composable
internal fun DesktopReplayPage(
    summary: DesktopReplaySummary,
    period: DesktopReplayPeriod,
    holder: String,
    onPeriodChange: (DesktopReplayPeriod) -> Unit,
    onPlaySong: (Song) -> Unit,
    onOpenArtist: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    // The mesh behind this page is painted by the frame, so the chrome is tinted by it too.
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item("heading") {
                // No arrow of its own: the top bar's back is the way out, as the gesture is on
                // Android.
                Spacer(Modifier.height(8.dp))
                Column {
                    Column(Modifier.padding(horizontal = GUTTER)) {
                        Spacer(Modifier.height(20.dp))
                        Text(
                            DesktopStrings["replay", "Replay"],
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.W800,
                            color = Color.White,
                        )
                        Text(
                            summary.label.ifBlank { period.chip },
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                        Spacer(Modifier.height(14.dp))
                        PeriodPicker(period, onPeriodChange)
                        Spacer(Modifier.height(18.dp))
                    }
                }
            }

            if (summary.isEmpty) {
                item("empty") { EmptyReplay(period) }
                return@LazyColumn
            }

            item("cards") {
                DesktopScrollableRow(gutter = GUTTER, spacing = 12.dp) {
                    items(summary.heroCards(), key = { it.label }) { card ->
                        ReplayCreditCard(
                            label = card.label,
                            value = card.value,
                            detail = card.detail,
                            artworkUrl = card.artworkUrl,
                            holder = holder,
                            memberSince = summary.memberSince(),
                            onClick = {},
                            modifier = Modifier.width(300.dp),
                        )
                    }
                }
            }

            chart(
                key = "songs",
                title = DesktopStrings["top_songs", "Top Songs"],
                rows = summary.songRows(CHART_LENGTH),
                onClick = { index -> summary.songs.getOrNull(index)?.let { onPlaySong(it.song) } },
            )
            chart(
                key = "artists",
                title = DesktopStrings["top_artists", "Top Artists"],
                rows = summary.artistRows(CHART_LENGTH),
                circular = true,
                onClick = { index -> summary.artists.getOrNull(index)?.let { onOpenArtist(it.title) } },
            )
            chart(
                key = "albums",
                title = DesktopStrings["top_albums", "Top Albums"],
                rows = summary.albumRows(CHART_LENGTH),
                onClick = {},
            )

            item("habits") { Habits(summary) }
            item("tail") { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun PeriodPicker(selected: DesktopReplayPeriod, onSelect: (DesktopReplayPeriod) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        DesktopReplayPeriod.entries.forEach { period ->
            val active = period == selected
            val background by animateColorAsState(
                if (active) Color.White.copy(alpha = 0.92f) else Color.Transparent,
                tween(160),
                label = "periodChip",
            )
            Text(
                period.chip,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.W700,
                color = if (active) Color.Black else Color.White.copy(alpha = 0.75f),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(background)
                    .clickable { onSelect(period) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

// ── The credit card ─────────────────────────────────────────────────────────

// ── Charts ──────────────────────────────────────────────────────────────────

private fun LazyListScope.chart(
    key: String,
    title: String,
    rows: List<DesktopReplayRow>,
    onClick: (Int) -> Unit,
    circular: Boolean = false,
) {
    if (rows.isEmpty()) return
    item("$key-title") {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.W800,
            color = Color.White,
            modifier = Modifier.padding(start = GUTTER, end = GUTTER, top = 8.dp, bottom = 10.dp),
        )
    }
    items(rows, key = { "$key-${it.key}" }) { row ->
        ChartRow(row, circular) { onClick(row.rank - 1) }
    }
    item("$key-gap") { Spacer(Modifier.height(20.dp)) }
}

@Composable
private fun ChartRow(row: DesktopReplayRow, circular: Boolean, onClick: () -> Unit) {
    val shape = if (circular) CircleShape else RoundedCornerShape(6.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = GUTTER, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankBadge(row.rank)
        if (row.artworkUrl != null) {
            DesktopArtwork(row.artworkUrl, Modifier.size(48.dp).clip(shape), px = ROW_ART_PX)
        } else {
            InitialTile(row.title, 48.dp, shape)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.W600,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(row.subtitle, formatListening(row.ms)).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (row.plays > 0) {
            Text(
                row.plays.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.45f),
            )
        }
    }
}

/** Number one is the accent; the rest are quiet, because a chart where every position shouts has
 * no first place. */
@Composable
private fun RankBadge(rank: Int) {
    Text(
        rank.toString(),
        style = if (rank == 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.W800,
        color = if (rank == 1) DesktopAccent else Color.White.copy(alpha = 0.45f),
        modifier = Modifier.width(28.dp),
    )
}

/** A stand-in cover for a row that has no artwork, coloured from the word itself. */
@Composable
private fun InitialTile(text: String, size: Dp, shape: Shape) {
    val hue = (text.hashCode().toFloat() % 360f + 360f) % 360f
    val color = Color.hsl(hue, 0.55f, 0.45f)
    Box(
        Modifier.size(size).clip(shape)
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.55f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.take(1).uppercase(Locale.ROOT),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.W800,
            color = Color.White,
        )
    }
}

// ── Tail ────────────────────────────────────────────────────────────────────

@Composable
private fun Habits(summary: DesktopReplaySummary) {
    Column(Modifier.padding(horizontal = GUTTER)) {
        Text(
            DesktopStrings["d_listening_shape", "Listening shape"],
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.W800,
            color = Color.White,
            modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("SONGS", summary.distinctSongs.toString(), Modifier.weight(1f))
            StatTile("ARTISTS", summary.distinctArtists.toString(), Modifier.weight(1f))
            StatTile("ALBUMS", summary.distinctAlbums.toString(), Modifier.weight(1f))
        }
        summary.peakHour?.let {
            Spacer(Modifier.height(10.dp))
            Note("You listen most around ${formatHour(it)}.")
        }
        summary.busiestDay?.let {
            Spacer(Modifier.height(6.dp))
            Note("Your biggest day was ${formatDay(it)} — ${formatListening(summary.busiestDayMs)}.")
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.W800,
            color = Color.White,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.55f),
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun Note(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.6f),
        modifier = modifier,
    )
}

@Composable
private fun EmptyReplay(period: DesktopReplayPeriod) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.Schedule,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            DesktopStrings["not_enough_listening", "Not enough listening yet"],
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when (period) {
                DesktopReplayPeriod.THIS_MONTH -> "Nothing this month. Play some music and it will fill up."
                else -> "Play some music and your Replay will build itself."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}

// ── The mesh behind all of it ───────────────────────────────────────────────

/** The artwork's own colours, averaged into a mesh and held still. */
@Composable
internal fun DesktopMesh(url: String?) {
    val mesh by produceState<ImageBitmap?>(initialValue = null, key1 = url) {
        value = withContext(Dispatchers.IO) {
            DesktopArtworkCache.load(url.artworkAt(MESH_SOURCE_PX) ?: url)
                ?.let { DesktopArtworkMesh.of(it, seed = url.hashCode()) }
        }
    }
    val texture = mesh
    if (texture == null) {
        Box(Modifier.fillMaxSize().background(DesktopBackground))
        return
    }
    Canvas(Modifier.fillMaxSize()) {
        drawImage(
            image = texture,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.Low,
        )
    }
}

// ── Rows and cards, derived from a summary ──────────────────────────────────

/** One row of a chart. */
data class DesktopReplayRow(
    val key: String,
    val rank: Int,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    val ms: Long,
    val plays: Int,
)

/** One of the cards along the top of the page. */
data class DesktopReplayHeroCard(
    val label: String,
    val value: String,
    val detail: String?,
    val artworkUrl: String?,
)

internal fun DesktopReplaySummary.songRows(limit: Int): List<DesktopReplayRow> =
    songs.take(limit).mapIndexed { index, entry ->
        DesktopReplayRow(
            key = entry.song.videoId,
            rank = index + 1,
            title = entry.song.title,
            subtitle = entry.song.artist.ifBlank { null },
            artworkUrl = entry.song.thumbnailUrl,
            ms = entry.listenedMs,
            plays = entry.plays,
        )
    }

internal fun DesktopReplaySummary.artistRows(limit: Int): List<DesktopReplayRow> =
    artists.take(limit).mapIndexed { index, entry -> entry.toRow(index) }

internal fun DesktopReplaySummary.albumRows(limit: Int): List<DesktopReplayRow> =
    albums.take(limit).mapIndexed { index, entry -> entry.toRow(index) }

private fun DesktopRankedEntry.toRow(index: Int) = DesktopReplayRow(
    key = title,
    rank = index + 1,
    title = title,
    subtitle = subtitle,
    artworkUrl = artworkUrl,
    ms = ms,
    plays = plays,
)

/**
 * The headline facts, in the order they are dealt.
 *
 * Minutes leads because it is the one figure that needs no context to mean something. A category
 * with nothing in it is left out rather than shown empty.
 */
internal fun DesktopReplaySummary.heroCards(): List<DesktopReplayHeroCard> = buildList {
    add(
        DesktopReplayHeroCard(
            label = DesktopStrings["minutes_listened", "Minutes listened"],
            value = grouped(minutes),
            detail = "${plays(totalPlays)} · ${label.ifBlank { period.chip }}",
            artworkUrl = songs.firstOrNull()?.song?.thumbnailUrl,
        ),
    )
    artists.firstOrNull()?.let {
        add(
            DesktopReplayHeroCard(
                label = DesktopStrings["top_artist", "Top artist"],
                value = it.title,
                detail = "${formatListening(it.ms)} · ${plays(it.plays)}",
                artworkUrl = it.artworkUrl,
            ),
        )
    }
    songs.firstOrNull()?.let {
        add(
            DesktopReplayHeroCard(
                label = DesktopStrings["top_song", "Top song"],
                value = it.song.title,
                detail = "${it.song.artist} · ${plays(it.plays)}",
                artworkUrl = it.song.thumbnailUrl,
            ),
        )
    }
    albums.firstOrNull()?.let {
        add(
            DesktopReplayHeroCard(
                label = DesktopStrings["top_album", "Top album"],
                value = it.title,
                detail = listOfNotNull(it.subtitle, formatListening(it.ms)).joinToString(" · "),
                artworkUrl = it.artworkUrl,
            ),
        )
    }
}

/** `MM/YY` of the first month with anything in it. */
internal fun DesktopReplaySummary.memberSince(): String? = since
    ?.let { runCatching { java.time.YearMonth.parse(it) }.getOrNull() }
    ?.let { "%02d/%02d".format(it.monthValue, it.year % 100) }

// ── Formatting ──────────────────────────────────────────────────────────────

internal fun grouped(value: Long): String = String.format(Locale.US, "%,d", value)

private fun plays(count: Int): String = if (count == 1) "1 play" else "${grouped(count.toLong())} plays"

internal fun formatListening(ms: Long): String {
    val minutes = ms / 60_000L
    if (minutes < 60) return if (minutes == 1L) "1 min" else "$minutes mins"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0L) "${grouped(hours)} hr" else "${grouped(hours)} hr $rest min"
}

private fun formatHour(hour: Int): String = when (hour) {
    0 -> "midnight"
    12 -> "midday"
    in 1..11 -> "$hour am"
    else -> "${hour - 12} pm"
}

private fun formatDay(iso: String): String = runCatching {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()))
}.getOrDefault(iso)

/** How far down each chart the page goes. A top five is a result; a top ten is a list. */
private const val CHART_LENGTH = 5

private const val ROW_ART_PX = 120
private const val MESH_SOURCE_PX = 120
/** The page margin, shared with every other page — see [DesktopPageGutter]. */
private val GUTTER = DesktopPageGutter
