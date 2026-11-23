package com.music.bitchord.ui.replay.year

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.music.bitchord.R
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.HEADER_ART_PX
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.ui.player.MeshPalette
import com.music.bitchord.ui.replay.CHROME_HEIGHT
import com.music.bitchord.ui.replay.Cover
import com.music.bitchord.ui.replay.HeadlineRun
import com.music.bitchord.ui.replay.ReplayRow
import com.music.bitchord.ui.replay.albumRows
import com.music.bitchord.ui.replay.artistRows
import com.music.bitchord.ui.replay.formatDay
import com.music.bitchord.ui.replay.formatHour
import com.music.bitchord.ui.replay.grouped
import com.music.bitchord.ui.replay.songRows
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * One card of the year story, drawn over the backdrop the shell has already
 * laid down. Every card follows the same three beats: the line that sets it up,
 * the reveal at [YearSlide.revealAtMs] (which is when the clip's chorus lands),
 * and the detail after it.
 */
@Composable
fun YearSlideContent(slide: YearSlide, year: ReplayYear, palette: MeshPalette) {
    val accent = remember(palette) { accentOf(palette) }
    val confetti = remember(palette) { confettiOf(palette) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp)
            .padding(top = CHROME_HEIGHT + 6.dp, bottom = 72.dp),
    ) {
        when (slide) {
            YearSlide.OPENER -> Opener(year, accent, confetti)
            YearSlide.MINUTES -> Minutes(year, accent, confetti)
            YearSlide.BUSIEST_DAY -> BusiestDay(year, accent)
            YearSlide.CLOCK -> Clock(year, accent)
            YearSlide.GENRES -> Genres(year, accent)
            YearSlide.ARTIST_REVEAL -> ArtistReveal(year, accent, confetti)
            YearSlide.TOP_ARTISTS -> RankList(
                stringResource(R.string.replay_year_top_artists),
                year.data.summary.artistRows(5),
                circular = true,
                accent = accent,
            )
            YearSlide.SONG_REVEAL -> SongReveal(year, accent, confetti)
            YearSlide.TOP_SONGS -> RankList(
                stringResource(R.string.replay_year_top_songs),
                year.data.summary.songRows(5).drop(1),
                circular = false,
                accent = accent,
            )
            YearSlide.ALBUMS -> RankList(
                stringResource(R.string.replay_year_albums),
                year.data.summary.albumRows(5),
                circular = false,
                accent = accent,
            )
            YearSlide.MONTHS -> Months(year, accent)
            YearSlide.DISCOVERY -> Discovery(year, accent)
            YearSlide.SUMMARY -> Summary(year, accent)
        }
    }
}

// ── Type ────────────────────────────────────────────────────────────────────

private val Setup = TextStyle(fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp)
private val Huge = TextStyle(fontWeight = FontWeight.W900, letterSpacing = (-3).sp, color = Color.White)
private val Detail = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.W600, color = Color.White)

private fun huge(size: TextUnit) = Huge.copy(fontSize = size, lineHeight = size * 0.95f)

/** A line that sets a card up, arriving word by word from the top of the card. */
@Composable
private fun SetupLine(text: String, start: Long = 120) {
    KineticText(listOf(HeadlineRun(text, bold = true)), start = start, style = Setup)
}

// ── Cards ───────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.Opener(year: ReplayYear, accent: Color, confetti: List<Color>) {
    val clock = LocalSlideClock.current
    val reveal = YearSlide.OPENER.revealAtMs
    // The title first, set and timed like every other card's opening line.
    SetupLine(
        stringResource(R.string.replay_year_opener_start) + " " + year.year + " " +
            stringResource(R.string.replay_year_opener_end),
    )
    Spacer(Modifier.height(12.dp))
    // The year, slammed down a digit at a time onto a wall of the year's
    // covers drifting past behind it, in whatever height the words above leave.
    Box(
        Modifier.fillMaxWidth().weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        CoverWall(year, start = 60L, modifier = Modifier.matchParentSize())
        // A pool of shade under the digits, so they read over any artwork.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        0f to Color.Black.copy(alpha = 0.68f),
                        0.7f to Color.Black.copy(alpha = 0.3f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Burst(at = reveal, colors = confetti, modifier = Modifier.matchParentSize())
        Row(Modifier.shake(clock, reveal)) {
            year.year.toString().forEachIndexed { index, digit ->
                Text(
                    text = digit.toString(),
                    style = huge(112.sp).copy(
                        fontFamily = YearDigits,
                        shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 6f), blurRadius = 28f),
                    ),
                    color = Color.White,
                    modifier = Modifier.slam(clock, start = 380L + index * 190L, from = 3.2f),
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    // Underneath the year, once it has landed.
    Text(
        stringResource(R.string.replay_year_opener_detail),
        style = Detail.copy(fontWeight = FontWeight.W500),
        color = Color.White.copy(alpha = 0.8f),
        modifier = Modifier.enter(clock, reveal + 300),
    )
    Spacer(Modifier.height(8.dp))
}

/**
 * The year's covers as a tilted wall: three rows drifting in turn left and
 * right, wider than the card so it runs off both edges, and fading out at the
 * top and bottom. The rows sweep in from their own sides at [start] while the
 * whole wall settles back from a little too close.
 *
 * Each row is the same few covers over and over, so it loops without a seam:
 * it moves by less than one run of covers, then starts again where the next
 * run is identical. All the movement is read in layers off the slide's clock,
 * so nothing here recomposes as it drifts.
 */
@Composable
private fun CoverWall(year: ReplayYear, start: Long, modifier: Modifier) {
    val clock = LocalSlideClock.current
    val summary = year.data.summary
    val rows = remember(summary) {
        val art = (summary.songs.mapNotNull { it.song.thumbnailUrl } + summary.albums.mapNotNull { it.artworkUrl })
            .distinct()
            .take(WALL_ROWS * 7)
        if (art.isEmpty()) {
            emptyList()
        } else {
            List(WALL_ROWS) { r ->
                val own = art.filterIndexed { i, _ -> i % WALL_ROWS == r }
                own.ifEmpty { art.drop(r % art.size) + art.take(r % art.size) }
            }
        }
    }
    if (rows.isEmpty()) return
    Box(
        modifier
            // Wider than the card by a margin each side, so the tilt never shows a
            // corner. Outside the layer below, which would otherwise clip it back
            // to the column's own width.
            .layout { measurable, constraints ->
                val bleed = WALL_BLEED.roundToPx()
                val width = constraints.maxWidth + bleed * 2
                val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed, 0) }
            }
            // Faded out top and bottom: drawn offscreen, then cut by a gradient.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.2f to Color.Black,
                        0.8f to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(WALL_GAP, Alignment.CenterVertically),
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    rotationZ = -9f
                    if (clock.reduceMotion) {
                        alpha = 0.6f
                        return@graphicsLayer
                    }
                    val p = easeOutCubic(window(clock.elapsed(), start, 1_500))
                    val s = 1.18f - 0.18f * p
                    scaleX = s
                    scaleY = s
                    alpha = 0.6f * easeOutCubic((p * 1.8f).coerceAtMost(1f))
                },
        ) {
            rows.forEachIndexed { r, covers ->
                // Most of the sweep is done a third of the way in, on its ease-out.
                clock.beat(start + r * 120L + 360L, Haptic.Tick)
                val leftward = r % 2 == 0
                // Enough copies to cover the card twice over plus one run to loop through.
                val shown = remember(covers) { List(covers.size + 10) { covers[it % covers.size] } }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(WALL_GAP),
                    modifier = Modifier
                        .wrapContentWidth(Alignment.Start, unbounded = true)
                        .graphicsLayer {
                            val step = (WALL_COVER + WALL_GAP).toPx()
                            val run = covers.size * step
                            val t = clock.elapsed()
                            val drift = if (clock.reduceMotion) 0f else (t * WALL_SPEED_DP * density / 1000f) % run
                            // Rows enter from their own side, then keep drifting the same way.
                            val sweep = if (clock.reduceMotion) {
                                0f
                            } else {
                                (1f - easeOutCubic(window(t, start + r * 120L, 1_100))) * 340.dp.toPx()
                            }
                            translationX = if (leftward) sweep - drift - step else drift - run - sweep
                        },
                ) {
                    shown.forEach { url ->
                        Cover(
                            url = url,
                            fallbackText = "",
                            size = WALL_COVER,
                            shape = RoundedCornerShape(10.dp),
                            px = CARD_ART_PX,
                        )
                    }
                }
            }
        }
    }
}

/**
 * SF Pro's Heavy cut, for the year alone. The app's own family stops at Bold
 * (its W900 falls back to that), which is too light for the one word the
 * story opens on.
 */
private val YearDigits = FontFamily(Font(R.font.sf_pro_display_heavy, FontWeight.W900))

private val WALL_COVER = 96.dp
private val WALL_GAP = 10.dp
private val WALL_BLEED = 90.dp
private const val WALL_ROWS = 3
private const val WALL_SPEED_DP = 16f

@Composable
private fun ColumnScope.Minutes(year: ReplayYear, accent: Color, confetti: List<Color>) {
    val clock = LocalSlideClock.current
    val summary = year.data.summary
    val reveal = YearSlide.MINUTES.revealAtMs
    val countFor = 1_900L
    val landed = reveal + countFor
    clock.beat(landed, Haptic.Slam)
    SetupLine(stringResource(R.string.replay_year_minutes_start))
    Spacer(Modifier.weight(0.7f))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        OrbitRings(start = reveal, color = accent, modifier = Modifier.matchParentSize().aspectRatio(1f))
        Burst(at = landed, colors = confetti, modifier = Modifier.matchParentSize())
        Column(Modifier.shake(clock, landed)) {
            val digits = grouped(summary.minutes).length
            CountUp(
                target = summary.minutes,
                start = reveal,
                duration = countFor,
                style = huge(if (digits > 6) 84.sp else 104.sp),
                modifier = Modifier
                    .enter(clock, reveal, duration = 320, fromY = 0.dp, fromScale = 0.6f)
                    .graphicsLayer {
                        // A punch as the figure lands.
                        val p = window(clock.elapsed(), landed, 360)
                        val s = 1f + 0.14f * sin(p * PI.toFloat())
                        scaleX = s
                        scaleY = s
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    },
                format = ::grouped,
            )
            Text(
                pluralStringResource(R.plurals.replay_year_minutes_word, summary.minutes.toInt().coerceAtLeast(0)),
                style = huge(40.sp),
                color = accent,
                modifier = Modifier.enter(clock, reveal + 250),
            )
        }
    }
    Spacer(Modifier.weight(1f))
    val days = summary.minutes / 1_440.0
    val amount = if (days >= 1) {
        stringResource(R.string.replay_year_minutes_days_value, String.format(java.util.Locale.getDefault(), "%.1f", days))
    } else {
        stringResource(R.string.replay_year_minutes_hours_value, grouped(summary.hours))
    }
    KineticText(
        listOf(
            HeadlineRun(stringResource(R.string.replay_year_minutes_days_start) + " ", bold = false),
            HeadlineRun(amount, bold = true),
            HeadlineRun(" " + stringResource(R.string.replay_year_minutes_days_end), bold = false),
        ),
        start = landed + 450,
        style = Setup.copy(fontSize = 26.sp, lineHeight = 30.sp),
    )
}

@Composable
private fun ColumnScope.BusiestDay(year: ReplayYear, accent: Color) {
    val clock = LocalSlideClock.current
    val context = LocalContext.current
    val summary = year.data.summary
    val reveal = YearSlide.BUSIEST_DAY.revealAtMs
    val busiest = summary.busiestDay ?: return
    SetupLine(stringResource(R.string.replay_year_busiest_start))
    Spacer(Modifier.height(10.dp))
    Text(
        formatDay(context, busiest),
        style = huge(58.sp),
        color = accent,
        modifier = Modifier.slam(clock, reveal, from = 2.2f),
    )
    Spacer(Modifier.height(18.dp))
    YearGrid(year, busiest, accent, reveal, Modifier.fillMaxWidth().weight(1f))
    Spacer(Modifier.height(14.dp))
    Text(
        stringResource(R.string.replay_year_busiest_minutes, grouped(summary.busiestDayMs / 60_000)),
        style = Detail,
        modifier = Modifier.enter(clock, reveal + 500),
    )
    year.data.busiestDaySong?.let { song ->
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.enter(clock, reveal + 850)) {
            Cover(song.thumbnailUrl, song.title, 44.dp, RoundedCornerShape(5.dp), ROW_ART_PX)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.replay_year_busiest_song, song.title),
                style = Detail.copy(fontWeight = FontWeight.W500),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The whole year as twelve rows of days, each lit by how much was played on
 * it, drawing in a month at a time; then the busiest day flares.
 */
@Composable
private fun YearGrid(year: ReplayYear, busiest: String, accent: Color, reveal: Long, modifier: Modifier) {
    val clock = LocalSlideClock.current
    val cells = remember(year) {
        val max = year.data.dayMs.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
        Array(12) { m ->
            val month = java.time.YearMonth.of(year.year, m + 1)
            FloatArray(31) { d ->
                if (d >= month.lengthOfMonth()) -1f
                else (year.data.dayMs["%04d-%02d-%02d".format(java.util.Locale.ROOT, year.year, m + 1, d + 1)] ?: 0L)
                    .toFloat() / max
            }
        }
    }
    val target = remember(busiest) {
        runCatching { LocalDate.parse(busiest) }.getOrNull()?.let { it.monthValue - 1 to it.dayOfMonth - 1 }
    }
    val monthLabels = remember {
        java.time.Month.entries.map { it.getDisplayName(DateTextStyle.NARROW, java.util.Locale.getDefault()) }
    }
    Row(modifier) {
        Column(Modifier.padding(end = 6.dp), verticalArrangement = Arrangement.SpaceEvenly) {
            monthLabels.forEachIndexed { m, label ->
                Text(
                    label,
                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.W700),
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f).enter(clock, 200L + m * 70L, fromY = 0.dp, fromX = (-12).dp),
                )
            }
        }
        Spacer(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .drawBehind {
                    val t = clock.elapsed()
                    val rowH = size.height / 12f
                    val cell = minOf(size.width / 31f, rowH)
                    val gap = cell * 0.18f
                    val box = cell - gap
                    for (m in 0 until 12) {
                        val rowIn = easeOutCubic(window(t, 200L + m * 70L, 420))
                        if (rowIn <= 0f) continue
                        val y = m * rowH + (rowH - box) / 2f
                        for (d in 0 until 31) {
                            val v = cells[m][d]
                            if (v < 0f) continue
                            val x = d * cell
                            val lit = if (v > 0f) 0.25f + 0.75f * v else 0.08f
                            drawRoundRect(
                                color = (if (v > 0f) accent else Color.White).copy(alpha = lit * rowIn),
                                topLeft = Offset(x, y),
                                size = Size(box, box),
                                cornerRadius = CornerRadius(box * 0.28f),
                            )
                        }
                    }
                    target?.let { (m, d) ->
                        val p = window(t, reveal, 900)
                        if (p <= 0f) return@let
                        val cx = d * cell + box / 2f
                        val cy = m * rowH + rowH / 2f
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(cx - box / 2f, cy - box / 2f),
                            size = Size(box, box),
                            cornerRadius = CornerRadius(box * 0.28f),
                        )
                        val ring = if (clock.reduceMotion) 0f else easeOutCubic(p)
                        if (ring in 0.01f..0.99f) {
                            drawCircle(
                                color = Color.White.copy(alpha = 1f - ring),
                                radius = box * (1f + ring * 6f),
                                center = Offset(cx, cy),
                                style = Stroke(width = 2.dp.toPx()),
                            )
                        }
                    }
                },
        )
    }
}

@Composable
private fun ColumnScope.Clock(year: ReplayYear, accent: Color) {
    val clock = LocalSlideClock.current
    val context = LocalContext.current
    val summary = year.data.summary
    val reveal = YearSlide.CLOCK.revealAtMs
    val peak = summary.peakHour ?: return
    val personality = year.personality ?: return
    SetupLine(stringResource(R.string.replay_year_clock_start))
    Spacer(Modifier.weight(0.4f))
    Box(Modifier.fillMaxWidth().aspectRatio(1.15f), contentAlignment = Alignment.Center) {
        for (h in 0 until 24) clock.beat(BAR_START + h * BAR_STAGGER + BAR_GROW * 45 / 100, Haptic.Tick)
        val hours = remember(summary) {
            val max = summary.hourOfDay.maxOrNull()?.coerceAtLeast(1L) ?: 1L
            FloatArray(24) { summary.hourOfDay.getOrElse(it) { 0L }.toFloat() / max }
        }
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                val t = clock.elapsed()
                val outer = size.minDimension / 2f
                val inner = outer * 0.42f
                for (h in 0 until 24) {
                    val grow = easeOutBack(window(t, BAR_START + h * BAR_STAGGER, BAR_GROW))
                    if (grow <= 0f) continue
                    val angle = (h / 24f) * 2f * PI.toFloat() - PI.toFloat() / 2f
                    val length = (outer - inner) * (0.12f + 0.88f * hours[h]) * grow
                    val isPeak = h == peak
                    val pulse = if (isPeak && !clock.reduceMotion && t > reveal) {
                        1f + 0.08f * sin((t - reveal) / 160f)
                    } else {
                        1f
                    }
                    val start = Offset(center.x + cos(angle) * inner, center.y + sin(angle) * inner)
                    val end = Offset(
                        center.x + cos(angle) * (inner + length * pulse),
                        center.y + sin(angle) * (inner + length * pulse),
                    )
                    drawLine(
                        color = if (isPeak && t > reveal) accent else Color.White.copy(alpha = 0.55f),
                        start = start,
                        end = end,
                        strokeWidth = (if (isPeak) 9.dp else 5.dp).toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            },
        )
        Text(
            formatHour(context, peak),
            style = huge(34.sp).copy(letterSpacing = (-1).sp),
            modifier = Modifier.slam(clock, reveal, from = 2f),
        )
    }
    Spacer(Modifier.weight(0.5f))
    val (title, detail) = when (personality) {
        ListeningPersonality.NIGHT_OWL -> R.string.replay_year_night_owl to R.string.replay_year_night_owl_detail
        ListeningPersonality.EARLY_BIRD -> R.string.replay_year_early_bird to R.string.replay_year_early_bird_detail
        ListeningPersonality.GOLDEN_HOUR -> R.string.replay_year_golden_hour to R.string.replay_year_golden_hour_detail
        ListeningPersonality.DAYTIME -> R.string.replay_year_daytime to R.string.replay_year_daytime_detail
        ListeningPersonality.ALL_DAY -> R.string.replay_year_all_day to R.string.replay_year_all_day_detail
    }
    Text(
        stringResource(title),
        style = huge(50.sp).copy(letterSpacing = (-1.5).sp),
        color = accent,
        modifier = Modifier.enter(clock, reveal + 650, fromScale = 0.5f, fromY = 0.dp, haptic = Haptic.Tap),
    )
    Spacer(Modifier.height(8.dp))
    Text(stringResource(detail), style = Detail, modifier = Modifier.enter(clock, reveal + 1_000))
}

@Composable
private fun ColumnScope.Genres(year: ReplayYear, accent: Color) {
    val clock = LocalSlideClock.current
    val reveal = YearSlide.GENRES.revealAtMs
    val genres = year.data.summary.genres.take(5)
    SetupLine(stringResource(R.string.replay_year_genres_start))
    Spacer(Modifier.weight(0.5f))
    Column(Modifier.fillMaxWidth().clipToBounds()) {
        genres.forEachIndexed { index, genre ->
            // The top genre stands out; the rest share one size.
            val size = if (index == 0) 70.sp else 60.sp
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .enter(
                        clock,
                        start = reveal + index * 240L,
                        duration = 620,
                        fromX = if (index % 2 == 0) (-300).dp else 300.dp,
                        fromY = 0.dp,
                        fromRotation = if (index % 2 == 0) -8f else 8f,
                        haptic = Haptic.Tap,
                    )
                    .then(if (index == 0) Modifier.breathe(clock, reveal + 700) else Modifier),
            ) {
                Text(
                    "${index + 1}",
                    style = huge(size * 0.42f),
                    color = accent,
                    modifier = Modifier.padding(end = 8.dp, bottom = 6.dp),
                )
                // A long name ("Bollywood", "Singer-Songwriter") shrinks to the
                // width of the card rather than running off its edge.
                BasicText(
                    genre.title.replaceFirstChar { it.titlecase() },
                    style = huge(size).copy(lineHeight = 0.95.em, color = if (index == 0) accent else Color.White),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = size * 0.45f, maxFontSize = size, stepSize = 1.sp),
                )
            }
        }
    }
    Spacer(Modifier.weight(1f))
}

@Composable
private fun ColumnScope.ArtistReveal(year: ReplayYear, accent: Color, confetti: List<Color>) {
    val clock = LocalSlideClock.current
    val summary = year.data.summary
    val reveal = YearSlide.ARTIST_REVEAL.revealAtMs
    val top = summary.artists.firstOrNull() ?: return
    SetupLine(stringResource(R.string.replay_year_artist_start))
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        // The countdown: the rest of the top five flicker past before the one.
        val runners = summary.artists.drop(1).take(4).reversed()
        val step = if (runners.isEmpty()) 0L else ((reveal - 1_500L) / runners.size).coerceAtMost(420L)
        runners.forEachIndexed { index, artist ->
            val rank = runners.size - index + 1
            val from = 1_500L + index * step
            clock.beat(from + step / 2, Haptic.Tap)
            Text(
                "#$rank  ${artist.title}",
                style = huge(34.sp).copy(letterSpacing = (-1).sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.graphicsLayer {
                    val t = clock.elapsed()
                    val p = window(t, from, step.coerceAtLeast(1))
                    alpha = if (p <= 0f || p >= 1f) 0f else sin(p * PI.toFloat())
                    if (!clock.reduceMotion) {
                        val s = 0.7f + 0.5f * p
                        scaleX = s
                        scaleY = s
                    }
                },
            )
        }
        OrbitRings(start = reveal, color = accent, modifier = Modifier.size(300.dp))
        Burst(at = reveal + 380, colors = confetti, modifier = Modifier.size(340.dp))
        Cover(
            url = top.artworkUrl,
            fallbackText = top.title,
            size = 210.dp,
            shape = CircleShape,
            px = HEADER_ART_PX,
            elevated = true,
            modifier = Modifier
                .breathe(clock, reveal + 900)
                .slam(clock, reveal, from = 2.4f),
        )
    }
    KineticText(listOf(HeadlineRun(top.title, bold = true)), start = reveal + 420, style = huge(46.sp).copy(letterSpacing = (-1.5).sp))
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.replay_year_minutes_together, grouped(top.ms / 60_000)) +
            "  ·  " + pluralStringResource(R.plurals.replay_play_count, top.plays, grouped(top.plays.toLong())),
        style = Detail,
        modifier = Modifier.enter(clock, reveal + 900),
    )
}

@Composable
private fun ColumnScope.SongReveal(year: ReplayYear, accent: Color, confetti: List<Color>) {
    val clock = LocalSlideClock.current
    val reveal = YearSlide.SONG_REVEAL.revealAtMs
    val top = year.data.summary.songs.firstOrNull() ?: return
    val song = top.song
    SetupLine(stringResource(R.string.replay_year_song_start))
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        // The wait: a blank sleeve, breathing, until the record turns over.
        Box(
            Modifier
                .size(230.dp)
                .graphicsLayer {
                    val t = clock.elapsed()
                    alpha = 1f - window(t, reveal - 120, 160)
                    if (!clock.reduceMotion) {
                        val s = 0.92f + 0.05f * sin(t / 180f)
                        scaleX = s
                        scaleY = s
                        rotationZ = sin(t / 260f) * 3f
                    }
                }
                .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("?", style = huge(110.sp), color = Color.White.copy(alpha = 0.75f))
        }
        OrbitRings(start = reveal, color = accent, modifier = Modifier.size(320.dp))
        Burst(at = reveal + 300, colors = confetti, modifier = Modifier.size(360.dp), seed = 11)
        Cover(
            url = song.thumbnailUrl,
            fallbackText = song.title,
            size = 236.dp,
            shape = RoundedCornerShape(10.dp),
            px = HEADER_ART_PX,
            elevated = true,
            modifier = Modifier
                .breathe(clock, reveal + 900)
                .graphicsLayer {
                    if (clock.reduceMotion) return@graphicsLayer
                    // The sleeve turning over as it lands.
                    rotationY = 90f * (1f - easeOutCubic(window(clock.elapsed(), reveal, 420)))
                    cameraDistance = 14f * density
                }
                .slam(clock, reveal, from = 1.8f),
        )
    }
    // The title typed out a letter at a time, each one felt as it appears. Only
    // as far as two lines can show; the rest is cut off anyway.
    for (i in 1..song.title.length.coerceAtMost(TYPED_FELT)) clock.beat(reveal + TYPE_DELAY + i * TYPE_MS, Haptic.Tick)
    val typed by remember(song.title, clock) {
        derivedStateOf {
            if (clock.reduceMotion) return@derivedStateOf song.title.length
            ((clock.elapsed() - reveal - TYPE_DELAY) / TYPE_MS).toInt().coerceIn(0, song.title.length)
        }
    }
    Text(
        song.title.take(typed),
        style = huge(42.sp).copy(letterSpacing = (-1.2).sp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        song.artist,
        style = Detail.copy(fontSize = 21.sp),
        color = accent,
        modifier = Modifier.enter(clock, reveal + 600),
    )
    Spacer(Modifier.height(6.dp))
    Text(
        pluralStringResource(R.plurals.replay_year_played_times, top.plays, grouped(top.plays.toLong())),
        style = Detail,
        modifier = Modifier.enter(clock, reveal + 900),
    )
}

/** A chart of up to five, cascading in a row at a time, the first one larger. */
@Composable
private fun ColumnScope.RankList(title: String, rows: List<ReplayRow>, circular: Boolean, accent: Color) {
    val clock = LocalSlideClock.current
    val reveal = 900L
    SetupLine(title)
    Spacer(Modifier.weight(0.4f))
    rows.forEachIndexed { index, row ->
        val lead = index == 0
        val art = if (lead) 96.dp else 62.dp
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 7.dp)
                .enter(clock, reveal + index * 170L, duration = 600, fromX = 140.dp, fromY = 0.dp, fromRotation = 3f, haptic = Haptic.Tap),
        ) {
            Text(
                "${row.rank}",
                style = huge(if (lead) 46.sp else 32.sp).copy(letterSpacing = (-1).sp),
                color = if (lead) accent else Color.White.copy(alpha = 0.85f),
                modifier = Modifier.width(44.dp),
            )
            Cover(
                url = row.artworkUrl,
                fallbackText = row.title,
                size = art,
                shape = if (circular) CircleShape else RoundedCornerShape(6.dp),
                px = if (lead) CARD_ART_PX else ROW_ART_PX,
                elevated = lead,
                modifier = if (lead) Modifier.breathe(clock, reveal + 700) else Modifier,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    row.title,
                    style = TextStyle(fontSize = if (lead) 24.sp else 19.sp, fontWeight = FontWeight.W800),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                row.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W500),
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    Spacer(Modifier.weight(1f))
}

/** Each month's top song on a ribbon that runs through the year. */
@Composable
private fun ColumnScope.Months(year: ReplayYear, accent: Color) {
    val clock = LocalSlideClock.current
    val tops = year.data.monthTops
    SetupLine(stringResource(R.string.replay_year_months))
    Spacer(Modifier.weight(0.5f))
    val card = 156.dp
    val gap = 14.dp
    BoxWithConstraints(Modifier.fillMaxWidth().height(card + 70.dp).clipToBounds()) {
        val travel = ((card + gap) * tops.size - gap - maxWidth).coerceAtLeast(0.dp)
        val passes = (travel / (card + gap)).toInt()
        for (k in 1..passes) {
            // When the eased scroll has carried k cards' widths past, found on its own curve.
            val p = inverseOf(::easeInOutCubic, (card + gap).value * k / travel.value)
            clock.beat(RIBBON_START + (RIBBON_MS * p).toLong(), Haptic.Tick)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .graphicsLayer {
                    val p = if (clock.reduceMotion) 0f else easeInOutCubic(window(clock.elapsed(), RIBBON_START, RIBBON_MS))
                    translationX = -travel.toPx() * p
                },
        ) {
            tops.forEachIndexed { index, top ->
                Column(
                    Modifier
                        .width(card)
                        .enter(clock, 500L + index * 90L, fromY = 60.dp, fromRotation = if (index % 2 == 0) -6f else 6f)
                        .graphicsLayer { rotationZ = if (index % 2 == 0) -2f else 2f },
                ) {
                    Text(
                        top.month.month.getDisplayName(DateTextStyle.SHORT, java.util.Locale.getDefault()).uppercase(),
                        style = huge(28.sp).copy(letterSpacing = 0.sp),
                        color = accent,
                    )
                    Spacer(Modifier.height(6.dp))
                    Cover(top.song.song.thumbnailUrl, top.song.song.title, card, RoundedCornerShape(8.dp), CARD_ART_PX, elevated = true)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        top.song.song.title,
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.W700),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    Spacer(Modifier.weight(1f))
}

@Composable
private fun ColumnScope.Discovery(year: ReplayYear, accent: Color) {
    val clock = LocalSlideClock.current
    val summary = year.data.summary
    val reveal = YearSlide.DISCOVERY.revealAtMs
    SetupLine(stringResource(R.string.replay_year_discovery_start))
    Spacer(Modifier.weight(0.5f))
    val stats = listOf(
        summary.distinctSongs to R.plurals.replay_year_songs_word,
        summary.distinctArtists to R.plurals.replay_year_artists_word,
        summary.distinctAlbums to R.plurals.replay_year_albums_word,
    ).filter { it.first > 0 }
    stats.forEachIndexed { index, (count, word) ->
        val start = reveal + index * 550L
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.enter(clock, start, fromX = (-160).dp, fromY = 0.dp),
        ) {
            CountUp(
                target = count.toLong(),
                start = start,
                duration = 1_300,
                style = huge(80.sp),
                color = if (index == 0) accent else Color.White,
                format = ::grouped,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                pluralStringResource(word, count),
                style = huge(28.sp).copy(letterSpacing = (-0.5).sp),
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
    }
    Spacer(Modifier.weight(1f))
}

@Composable
private fun ColumnScope.Summary(year: ReplayYear, accent: Color) {
    val clock = LocalSlideClock.current
    val summary = year.data.summary
    val artist = summary.artists.firstOrNull()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Cover(
            url = artist?.artworkUrl ?: summary.songs.firstOrNull()?.song?.thumbnailUrl,
            fallbackText = artist?.title.orEmpty(),
            size = 112.dp,
            shape = if (artist != null) CircleShape else RoundedCornerShape(8.dp),
            px = CARD_ART_PX,
            elevated = true,
            modifier = Modifier.slam(clock, 200, from = 1.8f),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            stringResource(R.string.replay_year_summary_title, year.year.toString()),
            style = huge(44.sp).copy(letterSpacing = (-1.5).sp),
            modifier = Modifier.enter(clock, 450),
        )
    }
    Spacer(Modifier.height(26.dp))
    Row(Modifier.fillMaxWidth()) {
        SummaryColumn(
            stringResource(R.string.replay_year_summary_artists),
            summary.artists.take(5).map { it.title },
            start = 700,
            accent = accent,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(14.dp))
        SummaryColumn(
            stringResource(R.string.replay_year_summary_songs),
            summary.songs.take(5).map { it.song.title },
            start = 950,
            accent = accent,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.weight(1f))
    Row(Modifier.fillMaxWidth()) {
        SummaryStat(stringResource(R.string.replay_year_summary_minutes), grouped(summary.minutes), 1_900, Modifier.weight(1f))
        summary.genres.firstOrNull()?.let {
            Spacer(Modifier.width(14.dp))
            SummaryStat(stringResource(R.string.top_genre), it.title.replaceFirstChar { c -> c.titlecase() }, 2_100, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryColumn(title: String, names: List<String>, start: Long, accent: Color, modifier: Modifier) {
    val clock = LocalSlideClock.current
    Column(modifier) {
        Text(
            title,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W700),
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.enter(clock, start),
        )
        Spacer(Modifier.height(6.dp))
        names.forEachIndexed { index, name ->
            Row(Modifier.padding(vertical = 3.dp).enter(clock, start + 120L + index * 110L, fromY = 18.dp)) {
                Text(
                    "${index + 1}",
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.W900),
                    color = accent,
                    modifier = Modifier.width(20.dp),
                )
                Text(
                    name,
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.W700),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, start: Long, modifier: Modifier) {
    val clock = LocalSlideClock.current
    Column(modifier.enter(clock, start, fromScale = 0.7f)) {
        Text(label, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W700), color = Color.White.copy(alpha = 0.7f))
        Text(value, style = huge(30.sp).copy(letterSpacing = (-0.8).sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── Colour ──────────────────────────────────────────────────────────────────

/**
 * The card's highlight: the most colourful of its four mesh colours, lifted
 * until it reads against the darkened backdrop. Sampled from the card's own
 * palette so every card's accent belongs to its backdrop.
 */
internal fun accentOf(palette: MeshPalette): Color {
    val hsl = FloatArray(3)
    val best = palette.colors.maxByOrNull { color ->
        ColorUtils.colorToHSL(color.toArgb(), hsl)
        hsl[1] * (1f - kotlin.math.abs(hsl[2] - 0.5f))
    } ?: return Color.White
    ColorUtils.colorToHSL(best.toArgb(), hsl)
    hsl[1] = hsl[1].coerceAtLeast(0.55f)
    hsl[2] = 0.72f
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun confettiOf(palette: MeshPalette): List<Color> {
    val hsl = FloatArray(3)
    return palette.colors.map { color ->
        ColorUtils.colorToHSL(color.toArgb(), hsl)
        hsl[1] = hsl[1].coerceAtLeast(0.6f)
        hsl[2] = 0.68f
        Color(ColorUtils.HSLToColor(hsl))
    } + Color.White
}

// ── Timings shared by an animation and its haptics ──────────────────────────

private const val BAR_START = 150L
private const val BAR_STAGGER = 45L
private const val BAR_GROW = 520L
private const val TYPE_DELAY = 250L
private const val TYPE_MS = 38L
private const val TYPED_FELT = 40
private const val RIBBON_START = 1_500L
private const val RIBBON_MS = 9_000L

/** Where a rising [curve] over 0..1 reaches [y], by halving. */
private fun inverseOf(curve: (Float) -> Float, y: Float): Float {
    var lo = 0f
    var hi = 1f
    repeat(24) {
        val mid = (lo + hi) / 2f
        if (curve(mid) < y) lo = mid else hi = mid
    }
    return (lo + hi) / 2f
}
