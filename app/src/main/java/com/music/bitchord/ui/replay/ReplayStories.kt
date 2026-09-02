package com.music.bitchord.ui.replay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.HEADER_ART_PX
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.stats.ReplaySummary
import com.music.bitchord.ui.player.MeshGradientBackground
import com.music.bitchord.ui.player.MeshPalette
import com.music.bitchord.ui.player.rememberArtworkColors
import com.music.bitchord.ui.theme.AccentRed
import kotlinx.coroutines.launch

/**
 * The Replay as a run of full-screen cards you tap through.
 *
 * ## Why a story rather than a scroll
 *
 * The page below this already has every number on it, ranked and scrollable, and
 * that is the right shape for looking something up. It is the wrong shape for
 * being *told* something: a scroll gives every fact the same weight and lets the
 * eye run past the one that matters. A story gives each fact the whole screen
 * and a few seconds of nobody else's attention.
 *
 * ## The layout
 *
 * Every card is the same three bands, which is what makes eight of them read as
 * one piece rather than eight designs: a sentence at the top with the number in
 * it set bold, the thing that sentence is about in the middle, and the share
 * button at the bottom. The sentence carries the meaning, so a card that has
 * lost its artwork to a slow connection still says something.
 *
 * ## The mechanics people already know
 *
 * Tap the right of the screen to move on, the left to go back, hold anywhere to
 * stop the clock, swipe to move by hand, back or the cross to leave. None of it
 * is explained on screen: it is the same everywhere, and explaining it would be
 * the first thing on a page whose whole job is to be effortless.
 *
 * The auto-advance stops at the last card rather than closing — closing on a
 * timer takes the share button away from someone reaching for it.
 */
@Composable
fun ReplayStories(
    summary: ReplaySummary,
    start: ReplayStoryPage,
    onClose: () -> Unit,
    /** Shares the card that was on screen when the button was pressed. */
    onShare: (ReplayStoryPage) -> Unit,
    /**
     * Holds the clock while something is up in front of the story.
     *
     * The share sheet is the case: it covers the lower half of the card, takes a
     * second to draw its picture, and behind it the story was still counting
     * down — so by the time anyone had chosen an app, the card they were sending
     * was two cards further on than the one on screen.
     */
    paused: Boolean = false,
) {
    // A category with nothing in it has no card: an empty page in the middle of
    // a story reads as the app having lost something.
    val pages = remember(summary) {
        ReplayStoryPage.ordered.filter { page ->
            when (page) {
                ReplayStoryPage.ALBUMS -> summary.albums.isNotEmpty()
                ReplayStoryPage.GENRES -> summary.genres.isNotEmpty()
                else -> true
            }
        }
    }
    val pagerState = rememberPagerState(
        initialPage = pages.indexOf(start).coerceAtLeast(0),
        pageCount = { pages.size },
    )
    val scope = rememberCoroutineScope()
    var held by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val current by remember { derivedStateOf { pagerState.currentPage } }

    /**
     * Where a tap sends the story.
     *
     * Two things here are the fix for two separate bugs, and both come from a
     * tap and the auto-advance being able to move the pager at the same time.
     *
     *  - **The step is taken from [PagerState.settledPage], not `currentPage`.**
     *    `currentPage` flips to the destination halfway through a scroll, so a
     *    tap landing during the auto-advance was computing "next" from the page
     *    the story was already on its way to — and skipping one. `settledPage`
     *    is the last page that actually came to rest, so the worst a mistimed
     *    tap can do is ask for the transition already in flight.
     *  - **It snaps rather than animating.** An `animateScrollToPage` that gets
     *    cancelled mid-flight — which is exactly what a second scroll request
     *    does to it — leaves the pager wherever it had got to, which is the
     *    story sitting between two cards with neither readable. A snap has no
     *    in-between state to be interrupted in. It is also what every story
     *    player does: tapping is meant to feel like turning a page, not like
     *    starting a transition.
     */
    fun goTo(target: Int, animate: Boolean) {
        val next = target.coerceIn(0, pages.lastIndex)
        scope.launch {
            progress.snapTo(0f)
            if (animate) pagerState.animateScrollToPage(next) else pagerState.scrollToPage(next)
        }
    }

    fun step(forward: Boolean) =
        goTo(pagerState.settledPage + if (forward) 1 else -1, animate = false)

    LaunchedEffect(current) { progress.snapTo(0f) }
    LaunchedEffect(current, held, paused) {
        if (held || paused) return@LaunchedEffect
        if (current >= pages.lastIndex) return@LaunchedEffect
        // Resumed from where the hold left it rather than restarted, so letting
        // go doesn't hand back a card that was nearly finished.
        val remaining = ((1f - progress.value) * PAGE_MILLIS).toInt().coerceAtLeast(0)
        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
        // Handed to [goTo] rather than scrolled from here, and that is the whole
        // fix for a card that stopped three-quarters of the way across on its
        // own but slid cleanly when tapped. This effect is keyed on the current
        // page; `animateScrollToPage` flips that key at the halfway mark, which
        // cancels the effect — and with it the very animation that flipped it.
        // The pager was left wherever the cancellation caught it. [goTo] runs on
        // the composition's scope, which the page change has no bearing on.
        goTo(current + 1, animate = true)
    }


    val page = pages.getOrElse(current) { ReplayStoryPage.INTRO }
    val artwork = summary.storyArtwork(page)
    // Rotated per card, so the backdrop is visibly a different colour on every
    // one — see [storyHue].
    val palette = rememberArtworkColors(artwork).rotated(storyHue(page))

    // The system bars are kept outside the frame rather than padded for inside
    // it: the card is a fixed canvas and its own margins are part of the
    // design, so an inset applied within would move the headline on one phone
    // and not another. Fitting the whole card into the safe area instead leaves
    // it identical everywhere and puts the letterboxing where the bars are.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        StoryFrame {
            Stage(
                pages = pages,
                pagerState = pagerState,
                summary = summary,
                current = current,
                progress = progress,
                onHold = { held = it },
                onStep = ::step,
                onClose = onClose,
                onShare = onShare,
                palette = palette,
                artwork = artwork,
                page = page,
            )
        }
    }
}

/**
 * Holds a story to 9:16 whatever shape the screen is.
 *
 * A story is a fixed canvas, not a responsive layout: the type sizes, the
 * collage offsets and the room the headline is allowed are all set against one
 * set of proportions, and a 20:9 phone stretches that into a column with a hole
 * in the middle while a tablet flattens it. It is also the shape the shared
 * image comes out as, so a card the user sends looks like the card they were
 * looking at when they tapped share.
 *
 * Fitted rather than filled — whichever of width and height runs out first is
 * the one that sets the size, and the rest is black. Letterboxing is the honest
 * failure here: cropping would take the headline or the share button off the
 * edge of the screen on the exact devices most likely to be running this.
 */
@Composable
private fun StoryFrame(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val fitsByWidth = maxWidth / maxHeight < STORY_ASPECT
        Box(
            Modifier
                .then(if (fitsByWidth) Modifier.fillMaxWidth() else Modifier.fillMaxHeight())
                .aspectRatio(STORY_ASPECT),
        ) {
            content()
        }
    }
}

/** The story card itself, inside whatever box [StoryFrame] gave it. */
@Composable
private fun Stage(
    pages: List<ReplayStoryPage>,
    pagerState: PagerState,
    summary: ReplaySummary,
    current: Int,
    progress: Animatable<Float, *>,
    onHold: (Boolean) -> Unit,
    onStep: (Boolean) -> Unit,
    onClose: () -> Unit,
    onShare: (ReplayStoryPage) -> Unit,
    palette: MeshPalette,
    artwork: String?,
    page: ReplayStoryPage,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF17171A))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onHold(true)
                        tryAwaitRelease()
                        onHold(false)
                    },
                    // Supplied, and deliberately empty. Without an
                    // `onLongPress`, `detectTapGestures` has no notion of a long
                    // press at all and reports every press-and-release as a tap
                    // — so holding to pause the story turned the page the
                    // instant the finger came off it, which is the opposite of
                    // what holding is for. Handing it a callback is what makes
                    // it draw the line at the long-press timeout.
                    onLongPress = {},
                    // Every tap is a step; where it lands is worked out in
                    // [ReplayStories.step], which owns the pager.
                    onTap = { offset -> onStep(offset.x >= size.width * BACK_ZONE) },
                )
            },
    ) {
        // The soft colour behind everything, drawn from whatever the card is
        // about and rotated per card — so no two backdrops in the run are the
        // same colour. Keyed on the page as well as the artwork, or a card
        // sharing a cover with the one before it would not crossfade at all.
        MeshGradientBackground(palette = palette, trackKey = page.name, animated = false)
        // Enough ink for white type, weighted to the top where the headline sits
        // and to the foot where the controls do.
        //
        // Lighter than it looks like it should be, because the mesh carries a
        // vertical scrim of its own and the two stack: at the weights this
        // started on, the lower half of every card came out near black and the
        // backdrop read as a glow at the top rather than as a colour the card
        // was painted in.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.45f),
                        0.35f to Color.Black.copy(alpha = 0.10f),
                        1.0f to Color.Black.copy(alpha = 0.34f),
                    ),
                ),
        )

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
            StoryPage(page = pages[index], summary = summary, onShare = onShare)
        }

        StoryChrome(
            label = summary.localizedLabel(LocalContext.current),
            count = pages.size,
            current = current,
            progress = progress.value,
            onClose = onClose,
        )
    }
}

/**
 * The furniture that doesn't move between cards: the cross, the run of progress
 * segments, and the two words saying what this is.
 *
 * Drawn over the pager rather than inside each page so it stays put while the
 * cards slide under it — a header that swipes with its page reads as eight
 * headers rather than one.
 */
@Composable
private fun StoryChrome(
    label: String,
    count: Int,
    current: Int,
    progress: Float,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.close_replay),
                tint = Color.White,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose)
                    .padding(4.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(count) { index ->
                Segment(
                    fraction = when {
                        index < current -> 1f
                        index > current -> 0f
                        else -> progress
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                // "Replay'26" for a year, which is the shape everyone knows this
                // by. A month or "All time" has no two-digit form and is spelt
                // out rather than truncated into nonsense.
                text = if (label.length == 4 && label.all { it.isDigit() }) {
                    "Replay'${label.takeLast(2)}"
                } else {
                    "Replay · $label"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W700,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            // The mark and the word together, the way the card carries it.
            Icon(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(width = 26.dp, height = 17.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = "BitChord",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W700,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun Segment(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(2.5.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.28f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(2.5.dp)
                .background(Color.White),
        )
    }
}

// ── One card ────────────────────────────────────────────────────────────────

@Composable
private fun StoryPage(
    page: ReplayStoryPage,
    summary: ReplaySummary,
    onShare: (ReplayStoryPage) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = CHROME_HEIGHT, bottom = 18.dp),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize()) {
                val headline = summary.storyHeadline(LocalContext.current, page)
                when (page) {
                    ReplayStoryPage.INTRO -> Intro(summary, headline)
                    ReplayStoryPage.MINUTES -> Minutes(summary, headline)
                    ReplayStoryPage.SONGS ->
                        Leaderboard(headline, summary.songRows(STORY_ROWS), circular = false)
                    ReplayStoryPage.ARTISTS ->
                        Leaderboard(headline, summary.artistRows(STORY_ROWS), circular = true)
                    ReplayStoryPage.ALBUMS ->
                        Leaderboard(headline, summary.albumRows(STORY_ROWS), circular = false)
                    ReplayStoryPage.GENRES -> Genres(summary, headline)
                    ReplayStoryPage.HABITS -> Habits(summary, headline)
                    ReplayStoryPage.SUMMARY -> Recap(summary, headline)
                }
            }
        }
        // The share button is on every card, not only the last one: the card
        // somebody wants to send is whichever one surprised them, and making
        // them sit through the rest to reach a button is how a share doesn't
        // happen. It sends *this* card — the one being looked at — because that
        // is the one that prompted the tap; the whole-Replay picture is what the
        // button at the foot of the Replay page produces.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable { onShare(page) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.IosShare,
                    contentDescription = stringResource(R.string.share_my_replay),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * The sentence at the top of every card.
 *
 * Two weights in one line rather than a heading and a subheading, which is what
 * lets the number be the loud part of an ordinary sentence instead of a figure
 * with a caption under it. [parts] pairs each run of text with whether it is the
 * emphasised one.
 */
@Composable
private fun Headline(parts: List<HeadlineRun>) {
    Text(
        text = buildAnnotatedString {
            parts.forEach { run ->
                withStyle(
                    SpanStyle(
                        fontWeight = if (run.bold) FontWeight.W800 else FontWeight.W600,
                        color = if (run.bold) Color.White else Color.White.copy(alpha = 0.62f),
                    ),
                ) { append(run.text) }
            }
        },
        style = MaterialTheme.typography.displayLarge,
        fontSize = 30.sp,
        lineHeight = 37.sp,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ── The cards ───────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.Intro(summary: ReplaySummary, headline: List<HeadlineRun>) {
    Headline(headline)
    Spacer(Modifier.weight(1f))
    ArtworkCollage(summary)
    Spacer(Modifier.weight(1f))
    Text(
        text = stringResource(R.string.replay_private_counting),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.5f),
    )
}

@Composable
private fun ColumnScope.Minutes(summary: ReplaySummary, headline: List<HeadlineRun>) {
    val context = LocalContext.current
    Headline(headline)
    Spacer(Modifier.weight(1f))
    ArtworkCollage(summary)
    Spacer(Modifier.weight(1f))
    Text(
        // "That's 0 hours" is what an unconditional hours line says for the
        // first afternoon of listening, and it reads as the page failing to
        // count rather than as a small number. Under an hour, the plays are the
        // only figure worth restating.
        text = buildString {
            if (summary.hours >= 1) {
                append(context.getString(R.string.replay_hours_across, grouped(summary.hours)))
            } else {
                append(context.getString(R.string.replay_across))
            }
            append(context.resources.getQuantityString(
                R.plurals.replay_play_count,
                summary.totalPlays,
                grouped(summary.totalPlays.toLong()),
            ))
            append(".")
            summary.peakHour?.let {
                append(" ")
                append(context.getString(R.string.replay_mostly_around, formatHour(context, it)))
            }
        },
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White.copy(alpha = 0.62f),
    )
}

/**
 * A number one with its runners-up.
 *
 * The same card for songs, artists and albums, because the shape of the fact is
 * identical in all three and only the noun changes. The ranked four underneath
 * are the part Apple's version leaves out and the reason this is worth opening
 * twice: the hero is the answer, the list is the evidence.
 */
@Composable
private fun ColumnScope.Leaderboard(
    headline: List<HeadlineRun>,
    rows: List<ReplayRow>,
    circular: Boolean,
) {
    val context = LocalContext.current
    val lead = rows.firstOrNull() ?: return
    val shape = if (circular) CircleShape else RoundedCornerShape(10.dp)
    Headline(headline)
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Cover(lead.artworkUrl, lead.title, 116.dp, shape, HEADER_ART_PX, elevated = true)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = lead.title,
                style = MaterialTheme.typography.headlineMedium,
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.W800,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            lead.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${formatListening(context, lead.ms)} · " + context.resources.getQuantityString(
                    R.plurals.replay_play_count,
                    lead.plays,
                    grouped(lead.plays.toLong()),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
    }
    Spacer(Modifier.weight(1f))
    rows.drop(1).forEach { row ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RankBadge(row.rank, AccentRed)
            Cover(row.artworkUrl, row.title, 36.dp, shape, ROW_ART_PX)
            Spacer(Modifier.width(12.dp))
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.W600,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatListening(context, row.ms),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun ColumnScope.Genres(summary: ReplaySummary, headline: List<HeadlineRun>) {
    val context = LocalContext.current
    val rows = summary.genreRows(STORY_ROWS)
    val lead = rows.firstOrNull() ?: return
    Headline(headline)
    Spacer(Modifier.weight(1f))
    Text(
        text = lead.title,
        style = MaterialTheme.typography.displayLarge,
        fontSize = 60.sp,
        lineHeight = 62.sp,
        fontWeight = FontWeight.W800,
        color = Color.White,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        text = formatListening(context, lead.ms),
        style = MaterialTheme.typography.titleMedium,
        color = Color.White.copy(alpha = 0.6f),
    )
    Spacer(Modifier.height(22.dp))
    rows.drop(1).forEach { row ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RankBadge(row.rank, AccentRed)
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W600,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatListening(context, row.ms),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.45f),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ColumnScope.Habits(summary: ReplaySummary, headline: List<HeadlineRun>) {
    val context = LocalContext.current
    Headline(headline)
    Spacer(Modifier.weight(1f))
    if (summary.distinctAlbums > 0) {
        BigStat(grouped(summary.distinctAlbums.toLong()), stringResource(R.string.different_albums))
    }
    summary.busiestDay?.let {
        BigStat(
            formatDay(context, it),
            stringResource(R.string.your_biggest_day, formatListening(context, summary.busiestDayMs)),
        )
    }
    summary.peakHour?.let {
        BigStat(formatHour(context, it), stringResource(R.string.when_you_listen_most))
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun BigStat(value: String, label: String) {
    Column(Modifier.padding(bottom = 22.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.W800,
            color = Color.White,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.58f),
        )
    }
}

@Composable
private fun ColumnScope.Recap(summary: ReplaySummary, headline: List<HeadlineRun>) {
    Headline(headline)
    Spacer(Modifier.height(20.dp))
    RecapLine(stringResource(R.string.minutes), formatMinutes(summary.totalMs))
    summary.songs.firstOrNull()?.let { RecapLine(stringResource(R.string.top_song), it.song.title) }
    summary.artists.firstOrNull()?.let { RecapLine(stringResource(R.string.top_artist), it.title) }
    summary.albums.firstOrNull()?.let { RecapLine(stringResource(R.string.top_album), it.title) }
    summary.genres.firstOrNull()?.let { RecapLine(stringResource(R.string.top_genre), it.title) }
    Spacer(Modifier.weight(1f))
    Text(
        text = stringResource(R.string.tap_share_for_picture),
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White.copy(alpha = 0.55f),
    )
}

@Composable
private fun RecapLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.W700,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── Pieces ──────────────────────────────────────────────────────────────────

@Composable
private fun Cover(
    url: String?,
    fallbackText: String,
    size: Dp,
    shape: Shape,
    px: Int,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
) {
    val base = modifier
        .size(size)
        .let { if (elevated) it.shadow(18.dp, shape, clip = false) else it }
        .clip(shape)
    when {
        url != null -> AsyncImage(
            model = url.artworkAt(px),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = base,
        )
        fallbackText.isNotBlank() -> Box(base) { InitialTile(fallbackText, size, shape) }
        else -> Box(base.background(Color.White.copy(alpha = 0.10f)))
    }
}

/**
 * The scatter of covers and faces the opening cards are built around.
 *
 * Deliberately hand-placed rather than laid out. A grid of the top six looks
 * like a search result; the point of this is to look like a pile of records
 * someone has been through, which needs overlap, rotation and inconsistent
 * sizes — none of which any layout the framework offers will produce, and all of
 * which are stable here because the offsets are fractions of the box rather than
 * pixel positions.
 *
 * Artists come back as circles and releases as squares, which is the same
 * distinction every music app makes and the only label these need.
 */
@Composable
private fun ArtworkCollage(summary: ReplaySummary, modifier: Modifier = Modifier) {
    val covers = remember(summary) {
        summary.songs.mapNotNull { it.song.thumbnailUrl }.distinct().take(3)
    }
    val faces = remember(summary) {
        summary.artists.mapNotNull { it.artworkUrl }.distinct()
            .filterNot { it in covers }
            .take(3)
    }
    if (covers.isEmpty() && faces.isEmpty()) return

    BoxWithConstraints(modifier.fillMaxWidth().height(300.dp)) {
        val w = maxWidth
        val h = maxHeight
        // Top-left corner as a fraction of the box, then the size and the tilt.
        // Every anchor is chosen so `fraction * width + size` lands inside the
        // box on the narrowest frame this is drawn in — a piece cropped by the
        // edge reads as a layout that overflowed, not as a pile.
        val squares = listOf(
            Triple(0.26f to 0.34f, 168.dp, -3f),
            Triple(0.05f to 0.10f, 88.dp, -9f),
            Triple(0.62f to 0.04f, 72.dp, 7f),
        )
        val circles = listOf(
            Triple(0.02f to 0.62f, 62.dp, 0f),
            Triple(0.70f to 0.34f, 76.dp, 0f),
            Triple(0.44f to 0.78f, 66.dp, 0f),
        )
        covers.forEachIndexed { index, url ->
            val (position, size, angle) = squares[index]
            Cover(
                url = url,
                fallbackText = "",
                size = size,
                shape = RoundedCornerShape(4.dp),
                px = CARD_ART_PX,
                modifier = Modifier
                    .offset(x = w * position.first, y = h * position.second)
                    .rotate(angle),
                elevated = true,
            )
        }
        faces.forEachIndexed { index, url ->
            val (position, size, _) = circles[index]
            Cover(
                url = url,
                fallbackText = "",
                size = size,
                shape = CircleShape,
                px = CARD_ART_PX,
                modifier = Modifier.offset(x = w * position.first, y = h * position.second),
                elevated = true,
            )
        }
    }
}

/** [this] with every colour turned [degrees] around the wheel, tone untouched. */
private fun MeshPalette.rotated(degrees: Float): MeshPalette {
    if (degrees == 0f) return this
    return MeshPalette(
        colors.map { color ->
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(color.toArgb(), hsl)
            hsl[0] = (hsl[0] + degrees) % 360f
            Color(ColorUtils.HSLToColor(hsl))
        },
    )
}

/**
 * How much room [StoryChrome] takes above a card's content — the cross, the
 * segments and the two words, measured rather than guessed at because the
 * chrome is drawn over the pager and cannot push anything down.
 */
private val CHROME_HEIGHT = 96.dp

/** How far down a chart a card goes. */
private const val STORY_ROWS = 5

/**
 * The shape of a story card, everywhere. See [StoryFrame] for why it is fixed
 * rather than following the screen.
 */
private const val STORY_ASPECT = 9f / 16f

/** How long a card holds before moving on, unless a finger is on the screen. */
private const val PAGE_MILLIS = 6_000f

/** The share of the width that means "back" — the left edge, as everywhere else. */
private const val BACK_ZONE = 0.32f
