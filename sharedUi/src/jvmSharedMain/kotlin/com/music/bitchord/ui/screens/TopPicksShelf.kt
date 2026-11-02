package com.music.bitchord.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.gestures.ScrollableState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.ui.AppUi
import com.music.bitchord.ui.player.CanvasArtworkPlayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import com.music.bitchord.ui.components.libraryGrid
import com.music.bitchord.ui.components.MOTION_FRAME_GAP_MS
import com.music.bitchord.ui.components.LIBRARY_GRID_SPACING
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.GridCells
import com.music.bitchord.ui.components.SHELF_CARD_WIDTH
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.PathOperation
import kotlin.math.exp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.HEADER_ART_PX
import com.music.bitchord.data.model.TopPick
import com.music.bitchord.data.model.TopPickKind
import com.music.bitchord.data.model.TopPickMood
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.sharedui.resources.*
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.rememberBlurredBackdrop
import com.music.bitchord.ui.components.ShelfRow
import com.music.bitchord.ui.components.SectionHeaderSkeleton
import com.music.bitchord.ui.components.ShimmerBox
import com.music.bitchord.ui.components.contextClick
import com.music.bitchord.ui.components.longPressMenuClickable
import com.music.bitchord.ui.components.thumbnailBorder
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * Home's "Top Picks for You": Apple Music's lead row of tall cards, each one
 * a station, a mood, an artist or a new release picked for this listener.
 *
 * The two "Made for You" stations are drawn here rather than shipped as video:
 * the same folded-paper chevrons and the fan of shards, as vector paths on a
 * Canvas whose phase is read only in the draw pass, so the animation repaints
 * the card without recomposing it, and stops when the row scrolls away and the
 * card leaves composition.
 */

/** Width over height: Apple's cards are a 3:4 portrait. */
private const val TOP_PICK_RATIO = 0.75f

/** Share of the row a card takes, so the next one shows past it. */
private const val TOP_PICK_FRACTION = 0.60f

/** Held back on a tablet, the same way [heroCardWidth] is. */
private val TOP_PICK_MAX_WIDTH = 280.dp

private val TopPickShape = RoundedCornerShape(12.dp)

private fun topPickWidth(available: Dp): Dp = minOf(available * TOP_PICK_FRACTION, TOP_PICK_MAX_WIDTH)

@Composable
internal fun TopPicksShelf(
    picks: List<TopPick>,
    /** The account's name, for "Kushagra's Station". Null shows "Your Station". */
    listenerName: String?,
    /** The card, and the title it was showing — the station's name for the queue. */
    onClick: (TopPick, String) -> Unit,
    onLongPress: ((TopPick) -> Unit)?,
) {
    val rowState = rememberLazyListState()
    // Which cards turned out to have a clip, by index, and which one plays.
    val hasClip = remember(picks) { mutableStateMapOf<Int, Boolean>() }
    var owner by remember(picks) { mutableIntStateOf(-1) }
    // Handed on only once a swipe has come to rest: passing it card to card
    // mid-swipe would start a decoder for every card the row slid past.
    LaunchedEffect(rowState, hasClip) {
        snapshotFlow { rowState.isScrollInProgress to clipOwner(rowState.layoutInfo, hasClip) }
            .collect { (scrolling, next) -> if (!scrolling) owner = next }
    }
    Column(Modifier.padding(bottom = 26.dp)) {
        SectionHeader(stringResource(Res.string.top_picks_for_you))
        BoxWithConstraints {
            val cardWidth = topPickWidth(maxWidth)
            PausedWhileScrolling(rowState) {
                ShelfRow(
                    contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    state = rowState,
                ) {
                    itemsIndexed(picks, key = { index, pick -> "$index:${pick.kind}:${pick.title}" }) { index, pick ->
                        CompositionLocalProvider(
                            LocalClipSlot provides ClipSlot(
                                mayPlay = index == owner,
                                onClipFound = { found -> hasClip[index] = found },
                            ),
                        ) {
                            TopPickCard(
                                pick = pick,
                                listenerName = listenerName,
                                onClick = onClick,
                                onLongPress = onLongPress?.takeIf { pick.kind == TopPickKind.LATEST_RELEASE },
                                modifier = Modifier.width(cardWidth),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Stands in for the row while it is being built, at the same size, so the feed doesn't jump. */
fun LazyListScope.topPicksSkeleton() {
    item(key = "skeleton:top-picks") {
        Column(Modifier.padding(bottom = 26.dp)) {
            SectionHeaderSkeleton()
            BoxWithConstraints {
                val cardWidth = topPickWidth(maxWidth)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    userScrollEnabled = false,
                ) {
                    items(2) {
                        ShimmerBox(
                            modifier = Modifier.width(cardWidth).aspectRatio(TOP_PICK_RATIO),
                            shape = TopPickShape,
                        )
                    }
                }
            }
        }
    }
}

/** What a card says: the small coloured line, the title, and an optional line under it. */
private class CardText(val eyebrow: String, val title: String, val subtitle: String)

@Composable
private fun cardText(pick: TopPick, listenerName: String?): CardText {
    val artist = pick.artistName.orEmpty()
    return when (pick.kind) {
        TopPickKind.LATEST_RELEASE -> CardText(
            eyebrow = stringResource(Res.string.top_pick_new_from, artist),
            title = pick.title,
            subtitle = localizeCardSubtitle(pick.subtitle),
        )
        TopPickKind.PERSONAL_STATION -> CardText(
            eyebrow = stringResource(Res.string.top_pick_made_for_you),
            title = listenerName?.substringBefore(' ')?.takeIf { it.isNotBlank() }
                ?.let { stringResource(Res.string.top_pick_named_station, it) }
                ?: stringResource(Res.string.top_pick_your_station),
            subtitle = "",
        )
        TopPickKind.MOOD -> {
            val mood = pick.mood ?: TopPickMood.FEEL_GOOD
            CardText(
                eyebrow = stringResource(Res.string.top_pick_mood_for_you),
                title = stringResource(mood.titleRes()),
                subtitle = stringResource(mood.lineRes()),
            )
        }
        TopPickKind.ARTIST_AND_SIMILAR -> CardText(
            eyebrow = stringResource(Res.string.top_pick_featuring, artist),
            title = stringResource(Res.string.top_pick_and_similar, artist),
            subtitle = "",
        )
        TopPickKind.FEATURED_ARTIST -> CardText(
            eyebrow = stringResource(Res.string.top_pick_featuring, artist),
            title = pick.title,
            subtitle = pick.subtitle,
        )
        TopPickKind.NEW_RELEASE_MIX -> CardText(
            eyebrow = stringResource(Res.string.top_pick_made_for_you),
            title = pick.title,
            subtitle = pick.subtitle,
        )
        TopPickKind.DISCOVERY_STATION -> CardText(
            eyebrow = stringResource(Res.string.top_pick_made_for_you),
            title = stringResource(Res.string.top_pick_discovery_station),
            subtitle = "",
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopPickCard(
    pick: TopPick,
    listenerName: String?,
    onClick: (TopPick, String) -> Unit,
    onLongPress: ((TopPick) -> Unit)?,
    modifier: Modifier,
) {
    val text = cardText(pick, listenerName)
    val longPress = onLongPress?.let { { it(pick) } }
    Box(
        modifier = modifier
            .aspectRatio(TOP_PICK_RATIO)
            .clip(TopPickShape)
            .thumbnailBorder(TopPickShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .longPressMenuClickable(onClick = { onClick(pick, text.title) }, onLongClick = longPress)
            .contextClick(longPress),
    ) {
        val eyebrowColor: Color
        val branded: Boolean
        when (pick.kind) {
            TopPickKind.PERSONAL_STATION -> {
                PersonalStationArt(Modifier.fillMaxSize())
                eyebrowColor = Color(0xFFFFD2DA)
                branded = true
            }
            TopPickKind.DISCOVERY_STATION -> {
                DiscoveryStationArt(Modifier.fillMaxSize())
                eyebrowColor = Color(0xFFCDBDFF)
                branded = true
            }
            TopPickKind.MOOD -> {
                val style = (pick.mood ?: TopPickMood.FEEL_GOOD).style()
                MoodArt(pick.mood ?: TopPickMood.FEEL_GOOD, style, Modifier.fillMaxSize())
                eyebrowColor = style.eyebrow
                branded = true
            }
            TopPickKind.ARTIST_AND_SIMILAR -> {
                val palette = collagePalette(pick.artistName.orEmpty())
                ArtistCollageArt(pick.images, palette, Modifier.fillMaxSize())
                eyebrowColor = palette.eyebrow
                branded = true
            }
            TopPickKind.LATEST_RELEASE, TopPickKind.NEW_RELEASE_MIX -> {
                ReleaseArt(
                    pick.images.firstOrNull(),
                    Modifier.fillMaxSize(),
                    // A New Release Mix is a playlist; only a real release has a sleeve that moves.
                    motion = pick.takeIf { it.kind == TopPickKind.LATEST_RELEASE }?.let { release ->
                        CardMotionSource(release.browseId ?: release.title) {
                            AppUi.host.releaseMotion(release.title, release.artistName.orEmpty())
                        }
                    },
                )
                eyebrowColor = PhotoEyebrow
                branded = false
            }
            TopPickKind.FEATURED_ARTIST -> {
                AsyncImage(
                    model = pick.images.firstOrNull().croppedAt(HEADER_ART_PX * 3 / 4, HEADER_ART_PX).whenArtworkReady(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                CardMotion(pick.artistMotion(), Modifier.fillMaxSize())
                // The caption sits on the picture's own darkened foot, as on
                // Apple's photo cards, rather than on a band of flat black.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.45f to Color.Transparent,
                                0.72f to Color.Black.copy(alpha = 0.45f),
                                1f to Color.Black.copy(alpha = 0.82f),
                            ),
                        ),
                )
                eyebrowColor = PhotoEyebrow
                branded = false
            }
        }
        if (branded) {
            Icon(
                painter = painterResource(Res.drawable.ic_logo),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
                    .height(15.dp)
                    .aspectRatio(730f / 484f),
            )
        }
        CardCaption(text, eyebrowColor, Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun CardCaption(text: CardText, eyebrowColor: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Text(
            text = text.eyebrow,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 16.sp),
            color = eyebrowColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = text.title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 17.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (text.subtitle.isNotBlank()) {
            Text(
                text = text.subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 16.sp),
                color = Color.White.copy(alpha = 0.86f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The line above the title on a photo card: white, let through, as on Apple Music's phone app. */
private val PhotoEyebrow = Color.White.copy(alpha = 0.62f)

/**
 * A release's card, as Apple Music draws one: the cover whole, as a square at
 * the top, and under it the cover's lower edge running on out of focus,
 * behind the caption.
 *
 * The blur is not a blur pass on screen: [rememberBlurredBackdrop] blurs a
 * small copy of the cover once, as it loads, and the image cache keeps the
 * result. A live blur — RenderEffect, Haze — would be re-run on every frame
 * the page draws, and this row's animated cards draw every frame.
 *
 * With a [motion] source, the release's motion cover plays over the square
 * once found, dissolving into the blur the same way the still does.
 */
@Composable
private fun ReleaseArt(url: String?, modifier: Modifier, motion: CardMotionSource? = null) {
    // How much of the still the clip has covered. Two faded layers stacked
    // would leave the still showing through the clip's own fade, so the still
    // steps out as the clip comes in.
    var covered by remember { mutableFloatStateOf(0f) }
    Box(modifier.background(Color.Black)) {
        AsyncImage(
            model = rememberBlurredBackdrop(url.croppedAt(BACKDROP_SOURCE_PX).whenArtworkReady()),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.42f),
        )
        // Toned down toward the caption, as Apple does, so the white text reads
        // on any cover.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.6f to Color.Black.copy(alpha = 0.08f),
                        1f to Color.Black.copy(alpha = 0.38f),
                    ),
                ),
        )
        AsyncImage(
            model = url.croppedAt(HEADER_ART_PX).whenArtworkReady(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                // Faded out over its last stretch, so the cover runs into the
                // blur under it instead of ending on a hard line. Offscreen so
                // the mask cuts the cover alone; the cover never animates, so
                // the layer is drawn once and reused while it scrolls.
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    alpha = 1f - covered
                }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(0.78f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                },
        )
        CardMotion(
            motion,
            Modifier.fillMaxWidth().aspectRatio(1f),
            bottomFade = 0.22f,
            onCover = { covered = it },
        )
    }
}

/**
 * Where a card's Apple Music clip comes from: [lookup], asked once per [key]
 * while the card is on screen.
 */
private class CardMotionSource(val key: Any, val lookup: suspend () -> CanvasArtwork?)

/** The artist's own looping clip, for the cards that show their face. */
private fun TopPick.artistMotion(): CardMotionSource? {
    val channel = artistBrowseId ?: return null
    val name = artistName ?: return null
    return CardMotionSource(channel) { AppUi.host.artistMotion(channel, name) }
}

/**
 * A card's clip over the still it sits on. The still stays underneath until
 * the clip's first frame has faded in, and for good where there is no clip —
 * which is most cards, and every card with animated artwork off.
 *
 * Held back with the pictures ([LocalArtworkImages]) so a cold start's feed
 * doesn't wait on it, paused through a scroll ([LocalArtworkScrolling]), and
 * paused and then let go of ([LocalArtworkMotion]) while Home isn't being looked
 * at. Only the card holding the row's [ClipSlot] plays; the others keep their
 * stills.
 */
@Composable
private fun CardMotion(
    source: CardMotionSource?,
    modifier: Modifier,
    bottomFade: Float = 0f,
    onCover: (Float) -> Unit = {},
) {
    if (source == null || !LocalArtworkImages.current) return
    // Looked up whether or not the clip may play yet, so the answer is to hand
    // when it may, and the row knows which cards have one to give the slot to.
    val clip by produceState<CanvasArtwork?>(null, source.key) { value = source.lookup() }
    val slot = LocalClipSlot.current
    val reportFound by rememberUpdatedState(slot?.onClipFound)
    val found = clip != null
    DisposableEffect(found) {
        reportFound?.invoke(found)
        onDispose { reportFound?.invoke(false) }
    }
    val canvas = clip ?: return
    val motion = LocalArtworkMotion.current
    val mayPlay = slot?.mayPlay != false
    val scrolling = LocalArtworkScrolling.current
    // Whether the clip's player exists at all. Building one blocks the main
    // thread and tearing one down takes its surface with it, so neither may
    // land on a frame anything is moving on: not mid-fling as the row scrolls
    // back into view, not on the first frame of Now Playing rising, and not on
    // the frame it lands back on the mini player. The still under it comes back
    // as it goes.
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(motion, mayPlay, scrolling) {
        when {
            // The row has handed the slot on, and it does that only at rest.
            !mayPlay -> mounted = false
            motion -> if (!mounted) {
                delay(CLIP_START_DELAY_MS)
                snapshotFlow { scrolling() }.first { !it }
                mounted = true
            }
            else -> {
                delay(CLIP_RELEASE_DELAY_MS)
                mounted = false
            }
        }
    }
    if (!mounted) return
    // Only paused for a scroll or for Now Playing passing over: neither is worth
    // fetching, decoding and fading the clip back in for. Read here, so it is
    // this card alone that recomposes as a scroll starts and ends.
    CanvasArtworkPlayer(
        canvas = canvas,
        isPlaying = true,
        modifier = modifier,
        bottomFade = bottomFade,
        onCoverChanged = onCover,
        pausedForTransition = !motion || scrolling(),
    )
}

/**
 * How long a card waits, once nothing stands in its clip's way, to build its
 * player: long enough for a player closing or a page settling to be finished.
 */
private const val CLIP_START_DELAY_MS = 400L

/**
 * How long a clip stays paused and mounted once the page stops being looked at
 * before its player and decoder are let go: past the whole of Now Playing's
 * slide, so the release lands under an opaque player, and so a quick look at
 * the player and back keeps the clip.
 */
private const val CLIP_RELEASE_DELAY_MS = 1_000L

/** The cover size the backdrop is cut from: it ends up 48px wide, so a small copy is plenty. */
private const val BACKDROP_SOURCE_PX = 160

// ---- Made for You stations ---------------------------------------------------

/**
 * Whether Top Picks' and Find Your Mood's artwork may move. Off while the
 * page isn't really being looked at — the app in the background, or Now
 * Playing pulled up over Home — when an endless animation would go on
 * redrawing a page nobody can see. The artwork then holds still where it is
 * and picks up again when the page is back.
 */
val LocalArtworkMotion = compositionLocalOf { true }

/**
 * Whether the page, or the row the artwork sits in, is being scrolled: every
 * loop is a redraw each frame, and a scroll is when the page can least spare
 * one. Handed down as something to ask rather than as a value, because a
 * value would recompose everything under it as each scroll starts and again
 * as it ends — on the very frames the scroll needs. Asked from the loops'
 * own coroutines instead ([loopPhase]), it recomposes nothing.
 */
val LocalArtworkScrolling = staticCompositionLocalOf<() -> Boolean> { { false } }

/** [content], with [state]'s scrolling added to what [LocalArtworkScrolling] answers. */
@Composable
internal fun PausedWhileScrolling(state: ScrollableState, content: @Composable () -> Unit) {
    val outer = LocalArtworkScrolling.current
    val scrolling = remember(outer, state) { { outer() || state.isScrollInProgress } }
    CompositionLocalProvider(LocalArtworkScrolling provides scrolling, content = content)
}

/**
 * One card's share of its row's clip: whether it [mayPlay], and where it says
 * whether it has one at all. A row of release and artist cards each decoding
 * their own video at once was a decoder per card; the row lets one play.
 */
private class ClipSlot(val mayPlay: Boolean, val onClipFound: (Boolean) -> Unit)

/** The card's [ClipSlot], or null outside a row that shares one out. */
private val LocalClipSlot = compositionLocalOf<ClipSlot?> { null }

/**
 * Which of the cards with a clip the row's slot goes to: the one most fully
 * in view, the earlier one on a tie. -1 when none in view has a clip.
 */
private fun clipOwner(layout: LazyListLayoutInfo, hasClip: Map<Int, Boolean>): Int {
    val start = layout.viewportStartOffset
    val end = layout.viewportEndOffset
    return layout.visibleItemsInfo
        .filter { hasClip[it.index] == true && it.size > 0 }
        .maxByOrNull { (minOf(it.offset + it.size, end) - maxOf(it.offset, start)).toFloat() / it.size }
        ?.index ?: -1
}

/**
 * Whether Top Picks may load its pictures yet. Off while Home is still
 * fetching its own feed: a cold start measured a third of a second slower to
 * Recents with the row's covers and photos decoding alongside it, so the
 * cards paint their colours and captions at once and their pictures follow
 * once the page they sit on has what it needs.
 */
val LocalArtworkImages = compositionLocalOf { true }

/** This image, or nothing yet while [LocalArtworkImages] is holding pictures back. */
@Composable
private fun String?.whenArtworkReady(): String? = if (LocalArtworkImages.current) this else null

/** One full breath of the stations' motion. Slow on purpose: it is a cover, not a spinner. */
private const val STATION_PERIOD_MS = 9_000

/**
 * A 0→1 loop of [periodMs], held wherever it is while [LocalArtworkMotion] is
 * off or [LocalArtworkScrolling] says a scroll is on, and carried on from
 * there after: a loop that went back to its first frame at every scroll would
 * jump. Read it only inside a draw lambda: then a frame of the loop costs a
 * redraw of that one canvas and never a recomposition.
 */
@Composable
private fun loopPhase(periodMs: Int): State<Float> {
    val phase = remember { mutableFloatStateOf(0f) }
    val running = LocalArtworkMotion.current
    val scrolling = LocalArtworkScrolling.current
    LaunchedEffect(running, periodMs, scrolling) {
        if (!running) return@LaunchedEffect
        var last = Long.MIN_VALUE
        while (true) {
            if (scrolling()) {
                // No frames asked for until it's over, so nothing is redrawn;
                // the time spent waiting isn't counted toward the loop.
                snapshotFlow { scrolling() }.first { !it }
                last = Long.MIN_VALUE
            }
            withFrameNanos { now ->
                if (last != Long.MIN_VALUE) {
                    phase.floatValue = (phase.floatValue + (now - last) / (periodMs * 1_000_000f)) % 1f
                }
                last = now
            }
            // A nine-second breath doesn't need every vsync, and each frame of
            // it redraws the page under the glass — see MOTION_FRAME_GAP_MS.
            delay(MOTION_FRAME_GAP_MS)
        }
    }
    return phase
}

/**
 * Keeps a card's drawn artwork as a finished picture between its own frames.
 *
 * With its loop held still for a scroll or for Now Playing passing over, a card
 * still has to be put back on screen on every frame of that movement, and once
 * more for each blur sampling the page. Drawn outright, that was every one of
 * the card's stacked gradient paths each time, for a picture that hadn't
 * changed. As a layer it is one texture, and a frame of the loop itself redraws
 * this canvas without re-recording the card around it.
 */
private val RetainedArt = Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }

private fun wave(phase: Float, offset: Float): Float = sin(2f * PI.toFloat() * (phase + offset))

/**
 * Whatever a canvas can work out once per size rather than once per frame —
 * brushes, fixed paths — rebuilt only when the card's size changes.
 */
private class SizeCache<T : Any>(private val build: (Size) -> T) {
    private var size = Size.Unspecified
    private var value: T? = null

    fun get(current: Size): T {
        val cached = value
        if (cached != null && current == size) return cached
        size = current
        return build(current).also { value = it }
    }
}

/** Outermost to innermost: where each arrow's tip rests, and its fold's two tones. */
private val PersonalLayers = listOf(
    Triple(1.00f, Color(0xFFFE5E83), Color(0xFFFA6196)),
    Triple(0.66f, Color(0xFFFE5065), Color(0xFFFC657C)),
    Triple(0.43f, Color(0xFFFB594C), Color(0xFFFC776D)),
    Triple(0.21f, Color(0xFFFB6729), Color(0xFFF98A48)),
)

private class PersonalBrushes(val background: Brush, val layers: List<Brush>)

/**
 * The personal station: arrows nested inside one another, all on the left
 * edge and pointing right, warm orange in the middle out to pink. Their tips
 * drift in and out of step, so the stack seems to fold and unfold.
 */
@Composable
private fun PersonalStationArt(modifier: Modifier) {
    val phase by loopPhase(STATION_PERIOD_MS)
    val path = remember { Path() }
    val brushes = remember {
        SizeCache { size ->
            PersonalBrushes(
                background = Brush.horizontalGradient(
                    0f to Color(0xFFF7334F),
                    0.38f to Color(0xFFFA6094),
                    0.75f to Color(0xFFF02A4A),
                    1f to Color(0xFFEE213D),
                ),
                // Each fold's shading runs to where its tip rests; the tip's
                // swing is small enough that the shading needn't chase it.
                layers = PersonalLayers.map { (rest, deep, light) ->
                    Brush.horizontalGradient(listOf(deep, light), startX = 0f, endX = size.width * rest)
                },
            )
        }
    }
    Canvas(modifier.then(RetainedArt)) {
        val w = size.width
        val h = size.height
        val cached = brushes.get(size)
        drawRect(cached.background)
        PersonalLayers.forEachIndexed { index, (rest, _, _) ->
            // The outer arrow is pinned to the card's edge; the inner ones swing.
            val tip = if (index == 0) rest else rest + 0.07f * wave(phase, index * 0.19f)
            path.reset()
            path.moveTo(0f, 0f)
            path.lineTo(w * tip, h / 2f)
            path.lineTo(0f, h)
            path.close()
            drawPath(path, cached.layers[index])
        }
    }
}

private val DiscoveryTopColors = listOf(
    Color(0xFF3A2496), Color(0xFF2F4FB0), Color(0xFF3B30A0),
    Color(0xFF2E7FB8), Color(0xFF27988A), Color(0xFF3C8DA8),
)
private val DiscoveryBottomColors = listOf(
    Color(0xFF3A2496), Color(0xFF333EA4), Color(0xFF2F62B6),
    Color(0xFF288B99), Color(0xFF3477BA), Color(0xFF42A296),
)

/** Where each shard meets the edge, as a share of the width; the inner ones swing. */
private val DiscoveryEdges = listOf(0f, 0.24f, 0.44f, 0.62f, 0.78f, 0.91f, 1.02f)

private class DiscoveryBrushes(val background: Brush, val top: List<Brush>, val bottom: List<Brush>, val arrow: Brush)

/**
 * The discovery station: a violet arrow across the card with a fan of blue,
 * indigo and teal shards opening around its tip, which sweep slowly to and fro.
 */
@Composable
private fun DiscoveryStationArt(modifier: Modifier) {
    val phase by loopPhase(STATION_PERIOD_MS)
    val path = remember { Path() }
    val brushes = remember {
        SizeCache { size ->
            val apex = Offset(size.width, size.height / 2f)
            fun shards(edgeY: Float, colors: List<Color>) = (0 until DiscoveryEdges.lastIndex).map { i ->
                val mid = size.width * (DiscoveryEdges[i] + DiscoveryEdges[i + 1]) / 2f
                val base = colors[i % colors.size]
                Brush.linearGradient(listOf(lerp(base, Color.White, 0.10f), base), start = apex, end = Offset(mid, edgeY))
            }
            DiscoveryBrushes(
                background = Brush.linearGradient(
                    listOf(Color(0xFF2A869F), Color(0xFF3477BA)),
                    Offset.Zero,
                    Offset(size.width, size.height),
                ),
                top = shards(0f, DiscoveryTopColors),
                bottom = shards(size.height, DiscoveryBottomColors),
                arrow = Brush.horizontalGradient(
                    0f to Color(0xFF502798),
                    0.55f to Color(0xFF421C88),
                    1f to Color(0xFF4A2292),
                ),
            )
        }
    }
    Canvas(modifier.then(RetainedArt)) {
        val w = size.width
        val h = size.height
        val cached = brushes.get(size)
        drawRect(cached.background)
        fun fan(edgeY: Float, shards: List<Brush>, offset: Float) {
            var previous = DiscoveryEdges[0]
            for (i in 1..DiscoveryEdges.lastIndex) {
                val rest = DiscoveryEdges[i]
                val edge = if (i == DiscoveryEdges.lastIndex) rest else rest + 0.06f * wave(phase, offset + i * 0.11f)
                path.reset()
                path.moveTo(w, h / 2f)
                path.lineTo(w * previous, edgeY)
                path.lineTo(w * edge, edgeY)
                path.close()
                drawPath(path, shards[i - 1])
                previous = edge
            }
        }
        fan(0f, cached.top, 0f)
        fan(h, cached.bottom, 0.5f)

        // The arrow itself, with a darker inner arrow that breathes.
        path.reset()
        path.moveTo(0f, 0f)
        path.lineTo(w, h / 2f)
        path.lineTo(0f, h)
        path.close()
        drawPath(path, cached.arrow)
        val inner = 0.08f + 0.04f * wave(phase, 0.25f)
        path.reset()
        path.moveTo(0f, h * inner)
        path.lineTo(w * (0.9f - inner), h / 2f)
        path.lineTo(0f, h * (1f - inner))
        path.close()
        drawPath(path, DiscoveryInnerArrow)
    }
}

private val DiscoveryInnerArrow = Color(0xFF3C1981).copy(alpha = 0.35f)

// ---- Mood for You -------------------------------------------------------------

/**
 * Apple Music's moods as they look now: a flat colour and one pale outline
 * emblem that moves. The seven Find Your Mood emblems are Apple's own — the
 * bolt strobes, the stones sway, the spiral unwinds, the star twists, the
 * rings ripple in, the dashed heart's dashes march and the heart beats, each
 * on the loop length of Apple's clip. Apple draws its other activities as
 * photographs, which this app can't use, so they get emblems in the same
 * hand instead.
 */
private class MoodStyle(
    val background: Color,
    val stroke: Color,
    val periodMs: Int,
    val eyebrow: Color = lerp(stroke, Color.White, 0.35f),
)

private fun TopPickMood.style(): MoodStyle = when (this) {
    TopPickMood.ENERGY -> MoodStyle(Color(0xFF1EBD38), Color(0xFFE2F6A2), 3_000, Color(0xFFE8FF9C))
    TopPickMood.RELAX -> MoodStyle(Color(0xFF31AFF3), Color(0xFFB4E9FF), 5_600, Color(0xFFD6F3FF))
    TopPickMood.FEELING_BLUE -> MoodStyle(Color(0xFF253AB2), Color(0xFF6CC4FF), 3_700, Color(0xFFA9DBFF))
    TopPickMood.FEEL_GOOD -> MoodStyle(Color(0xFFF2A900), Color(0xFFFFE8A0), 4_000, Color(0xFFFFF27A))
    TopPickMood.FOCUS -> MoodStyle(Color(0xFFF28400), Color(0xFFFFD6A4), 3_600, Color(0xFFFFE2B8))
    TopPickMood.HEARTBREAK -> MoodStyle(Color(0xFFEA3520), Color(0xFFFFA690), 2_700, Color(0xFFFFC9BC))
    TopPickMood.LOVE -> MoodStyle(Color(0xFFF34CA0), Color(0xFFFFB8DC), 1_700, Color(0xFFFFD0EA))
    TopPickMood.AFTER_HOURS -> MoodStyle(Color(0xFF3A1F78), Color(0xFFC9B6FF), 4_500)
    TopPickMood.ALONE_TIME -> MoodStyle(Color(0xFF2C6E8C), Color(0xFFA9E2F5), 4_000)
    TopPickMood.CHILL -> MoodStyle(Color(0xFF138A86), Color(0xFFA6F0E6), 5_000)
    TopPickMood.COMMUTING -> MoodStyle(Color(0xFF17855F), Color(0xFFA8EFD0), 2_400)
    TopPickMood.EATING_COOKING -> MoodStyle(Color(0xFFD9621A), Color(0xFFFFD3A8), 3_200)
    TopPickMood.EVENING -> MoodStyle(Color(0xFF2A3A8C), Color(0xFFB8C6FF), 6_000)
    TopPickMood.FITNESS -> MoodStyle(Color(0xFF8DB014), Color(0xFFEFFFA8), 2_000)
    TopPickMood.GAMING -> MoodStyle(Color(0xFF239A2A), Color(0xFFB8FFB0), 6_000)
    TopPickMood.HOME -> MoodStyle(Color(0xFF0E8A6C), Color(0xFFA8F2DB), 4_000)
    TopPickMood.MORNING -> MoodStyle(Color(0xFFF08C1A), Color(0xFFFFE2A6), 5_000)
    TopPickMood.MOTIVATION -> MoodStyle(Color(0xFFC01E4A), Color(0xFFFFB3C6), 1_800)
    TopPickMood.OUTDOORS -> MoodStyle(Color(0xFF4C8A1E), Color(0xFFD4F5A8), 6_000)
    TopPickMood.PARTY -> MoodStyle(Color(0xFFC2287A), Color(0xFFFFC0E0), 3_000)
    TopPickMood.SLEEP -> MoodStyle(Color(0xFF26266E), Color(0xFFC2C6FF), 4_500)
    TopPickMood.SOCIAL -> MoodStyle(Color(0xFFE0507A), Color(0xFFFFD0DE), 3_000)
    TopPickMood.SPORTS -> MoodStyle(Color(0xFF1C6CD0), Color(0xFFB8D8FF), 3_000)
    TopPickMood.SUMMERTIME -> MoodStyle(Color(0xFFFF8C00), Color(0xFFFFE6A8), 8_000)
    TopPickMood.HOLIDAY -> MoodStyle(Color(0xFFC62828), Color(0xFFFFC8C8), 8_000)
    TopPickMood.WEEKEND -> MoodStyle(Color(0xFF7A3CC2), Color(0xFFE2CCFF), 2_400)
    TopPickMood.WELLBEING -> MoodStyle(Color(0xFF36A98A), Color(0xFFC6F7E6), 5_000)
    TopPickMood.WORK -> MoodStyle(Color(0xFF3E63C9), Color(0xFFC4D2FF), 2_000)
}

internal fun TopPickMood.titleRes() = when (this) {
    TopPickMood.ENERGY -> Res.string.mood_energy
    TopPickMood.RELAX -> Res.string.mood_relax
    TopPickMood.FEELING_BLUE -> Res.string.mood_feeling_blue
    TopPickMood.FEEL_GOOD -> Res.string.mood_feel_good
    TopPickMood.FOCUS -> Res.string.mood_focus
    TopPickMood.HEARTBREAK -> Res.string.mood_heartbreak
    TopPickMood.LOVE -> Res.string.mood_love
    TopPickMood.AFTER_HOURS -> Res.string.mood_after_hours
    TopPickMood.ALONE_TIME -> Res.string.mood_alone_time
    TopPickMood.CHILL -> Res.string.mood_chill
    TopPickMood.COMMUTING -> Res.string.mood_commuting
    TopPickMood.EATING_COOKING -> Res.string.mood_eating_cooking
    TopPickMood.EVENING -> Res.string.mood_evening
    TopPickMood.FITNESS -> Res.string.mood_fitness
    TopPickMood.GAMING -> Res.string.mood_gaming
    TopPickMood.HOME -> Res.string.mood_home
    TopPickMood.MORNING -> Res.string.mood_morning
    TopPickMood.MOTIVATION -> Res.string.mood_motivation
    TopPickMood.OUTDOORS -> Res.string.mood_outdoors
    TopPickMood.PARTY -> Res.string.mood_party
    TopPickMood.SLEEP -> Res.string.mood_sleep
    TopPickMood.SOCIAL -> Res.string.mood_social
    TopPickMood.SPORTS -> Res.string.mood_sports
    TopPickMood.SUMMERTIME -> Res.string.mood_summertime
    TopPickMood.HOLIDAY -> Res.string.mood_holiday
    TopPickMood.WEEKEND -> Res.string.mood_weekend
    TopPickMood.WELLBEING -> Res.string.mood_wellbeing
    TopPickMood.WORK -> Res.string.mood_work
}

private fun TopPickMood.lineRes() = when (this) {
    TopPickMood.ENERGY -> Res.string.mood_energy_line
    TopPickMood.RELAX -> Res.string.mood_relax_line
    TopPickMood.FEELING_BLUE -> Res.string.mood_feeling_blue_line
    TopPickMood.FEEL_GOOD -> Res.string.mood_feel_good_line
    TopPickMood.FOCUS -> Res.string.mood_focus_line
    TopPickMood.HEARTBREAK -> Res.string.mood_heartbreak_line
    TopPickMood.LOVE -> Res.string.mood_love_line
    TopPickMood.AFTER_HOURS -> Res.string.mood_after_hours_line
    TopPickMood.ALONE_TIME -> Res.string.mood_alone_time_line
    TopPickMood.CHILL -> Res.string.mood_chill_line
    TopPickMood.COMMUTING -> Res.string.mood_commuting_line
    TopPickMood.EATING_COOKING -> Res.string.mood_eating_cooking_line
    TopPickMood.EVENING -> Res.string.mood_evening_line
    TopPickMood.FITNESS -> Res.string.mood_fitness_line
    TopPickMood.GAMING -> Res.string.mood_gaming_line
    TopPickMood.HOME -> Res.string.mood_home_line
    TopPickMood.MORNING -> Res.string.mood_morning_line
    TopPickMood.MOTIVATION -> Res.string.mood_motivation_line
    TopPickMood.OUTDOORS -> Res.string.mood_outdoors_line
    TopPickMood.PARTY -> Res.string.mood_party_line
    TopPickMood.SLEEP -> Res.string.mood_sleep_line
    TopPickMood.SOCIAL -> Res.string.mood_social_line
    TopPickMood.SPORTS -> Res.string.mood_sports_line
    TopPickMood.SUMMERTIME -> Res.string.mood_summertime_line
    TopPickMood.HOLIDAY -> Res.string.mood_holiday_line
    TopPickMood.WEEKEND -> Res.string.mood_weekend_line
    TopPickMood.WELLBEING -> Res.string.mood_wellbeing_line
    TopPickMood.WORK -> Res.string.mood_work_line
}

/** The emblem's square, in card pixels; [x] and [y] take shares of its side. */
private class Frame(val left: Float, val top: Float, val side: Float) {
    fun x(f: Float) = left + side * f
    fun y(f: Float) = top + side * f
    fun at(x: Float, y: Float) = Offset(x(x), y(y))
    val center get() = Offset(x(0.5f), y(0.5f))
}

/**
 * A mood's artwork. [square] is a Find Your Mood tile, where the emblem sits
 * in the middle; otherwise it's a tall Top Picks card and keeps to the part
 * above the caption.
 */
@Composable
private fun MoodArt(
    mood: TopPickMood,
    style: MoodStyle,
    modifier: Modifier,
    square: Boolean = false,
    /** Drawn at rest — see [stillPhase] — and never asks for a frame. */
    still: Boolean = false,
) {
    // Read only in the draw below, so a frame of the loop redraws this canvas
    // and recomposes nothing.
    val loop = if (still) null else loopPhase(style.periodMs)
    val cache = remember(mood) { EmblemCache() }
    Canvas(if (still) modifier else modifier.then(RetainedArt)) {
        drawRect(style.background)
        val w = size.width
        val h = size.height
        val f = if (square) {
            val side = w * 0.70f
            Frame(left = (w - side) / 2f, top = (h - side) / 2f, side = side)
        } else {
            // Most of the card's width, but never down into the caption: the
            // emblem keeps to the card's top two thirds whatever its proportions.
            val side = minOf(w * 0.84f, h * 0.62f)
            Frame(left = (w - side) / 2f, top = h * 0.37f - side / 2f, side = side)
        }
        val stroke = cache.stroke((if (square) 2.dp else 2.5.dp).toPx())
        drawEmblem(mood, f, loop?.value ?: mood.stillPhase(), style.stroke, stroke, cache)
    }
}

/**
 * Where in its loop a mood's emblem is drawn when it holds still: its start,
 * where nearly every one is whole and at rest, but for the two that are only
 * part-way drawn there.
 */
private fun TopPickMood.stillPhase(): Float = when (this) {
    // The pulse partway along the trace, rather than not yet set out.
    TopPickMood.FITNESS -> 0.55f
    // A line half typed, with the cursor showing.
    TopPickMood.WORK -> 0.6f
    else -> 0f
}

private val TAU = 2f * PI.toFloat()

/**
 * One mood card's drawing state, kept across frames: the emblem's fixed
 * outline is built once per card size instead of on every frame of its loop
 * (the spiral alone is 320 points and a path measure), along with the stroke.
 * [scratch] is for the parts that genuinely change frame to frame.
 */
private class EmblemCache {
    private var builtFor = Size.Unspecified
    private val outline = Path()
    val scratch = Path()
    val segment = Path()
    val measure = PathMeasure()
    var length = 0f
        private set
    private var strokeWidth = -1f
    private var strokeStyle = Stroke()

    /** The mood's fixed outline at [size], built by [build] the first time. */
    fun outline(size: Size, measured: Boolean = false, build: (Path) -> Unit): Path {
        if (size != builtFor) {
            outline.reset()
            build(outline)
            if (measured) {
                measure.setPath(outline, false)
                length = measure.length
            }
            builtFor = size
        }
        return outline
    }

    fun stroke(width: Float): Stroke {
        if (width != strokeWidth) {
            strokeWidth = width
            strokeStyle = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
        }
        return strokeStyle
    }
}

private fun DrawScope.drawEmblem(
    mood: TopPickMood,
    f: Frame,
    phase: Float,
    color: Color,
    stroke: Stroke,
    cache: EmblemCache,
) {
    val side = f.side
    val path = cache.scratch
    val segment = cache.segment
    when (mood) {
        TopPickMood.ENERGY -> {
            val path = cache.outline(size) { bolt(it, f) }
            // Every loop the bolt flickers: an echo jumps out to either side
            // of it and fades, like a strobe.
            val t = (phase - 0.28f) / 0.26f
            val flash = if (t in 0f..1f) sin(PI.toFloat() * t) else 0f
            if (flash > 0f) {
                translate(left = side * 0.07f * flash) { drawPath(path, color.copy(alpha = 0.55f * flash), style = stroke) }
                translate(left = -side * 0.05f * flash) { drawPath(path, color.copy(alpha = 0.35f * flash), style = stroke) }
            }
            drawPath(path, color, style = stroke)
        }
        TopPickMood.RELAX -> {
            // Three stones, balanced and swaying a little out of step.
            val stones = listOf(
                Triple(0.17f, 0.44f, 0.18f),
                Triple(0.38f, 0.68f, 0.28f),
                Triple(0.66f, 0.84f, 0.36f),
            )
            stones.forEachIndexed { index, (cy, width, height) ->
                val sway = wave(phase, index * 0.22f)
                val center = Offset(f.x(0.5f + 0.015f * sway), f.y(cy))
                rotate(degrees = 4f * sway, pivot = center) {
                    drawOval(
                        color = color,
                        topLeft = Offset(center.x - side * width / 2f, center.y - side * height / 2f),
                        size = Size(side * width, side * height),
                        style = stroke,
                    )
                }
            }
        }
        TopPickMood.FEELING_BLUE -> {
            cache.outline(size, measured = true) { spiral(it, f) }
            val length = cache.length
            // Whole, then unwinding from the top, then drawn back in.
            val from: Float
            val to: Float
            when {
                phase < 0.35f -> { from = 0f; to = length }
                phase < 0.8f -> { from = length * easeInOut((phase - 0.35f) / 0.45f); to = length }
                else -> { from = 0f; to = length * easeInOut((phase - 0.8f) / 0.2f) }
            }
            segment.reset()
            if (to > from) cache.measure.getSegment(from, to, segment, true)
            drawPath(segment, color, style = stroke)
        }
        TopPickMood.FEEL_GOOD -> {
            val path = cache.outline(size) { star(it, f) }
            val pulse = 0.9f + 0.1f * (0.5f + 0.5f * cos(TAU * phase))
            withTransform({
                rotate(8f * wave(phase, 0f), f.center)
                scale(pulse, pulse, f.center)
            }) { drawPath(path, color, style = stroke) }
        }
        TopPickMood.FOCUS -> {
            // Rings drawn in toward the centre: each one shrinks, the innermost
            // fades away and a new one fades in at the edge.
            for (i in 0 until 4) {
                val share = (i + 1 - phase) / 4
                drawCircle(color.copy(alpha = edgeFade(share)), radius = side * 0.5f * share, center = f.center, style = stroke)
            }
        }
        TopPickMood.HEARTBREAK -> {
            val path = cache.outline(size) { heart(it, f) }
            val dash = side * 0.075f
            val gap = side * 0.055f
            drawPath(
                path,
                color,
                style = Stroke(
                    width = stroke.width,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap), -phase * (dash + gap) * 2f),
                ),
            )
        }
        TopPickMood.LOVE -> {
            val path = cache.outline(size) { heart(it, f) }
            // Lub-dub: two quick swells, then rest.
            fun bump(at: Float): Float {
                val d = (phase - at) / 0.06f
                return exp(-d * d)
            }
            val beat = 1f + 0.07f * (bump(0.15f) + 0.7f * bump(0.38f))
            scale(beat, beat, f.center) { drawPath(path, color, style = stroke) }
        }
        TopPickMood.AFTER_HOURS -> {
            drawPath(cache.outline(size) { crescent(it, f, 0.42f, 0.52f, 0.34f) }, color, style = stroke)
            listOf(Triple(0.80f, 0.22f, 0.07f), Triple(0.86f, 0.58f, 0.05f), Triple(0.68f, 0.82f, 0.04f))
                .forEachIndexed { index, (x, y, r) ->
                    val twinkle = 0.25f + 0.75f * (0.5f + 0.5f * wave(phase, index / 3f))
                    sparkle(path, f.at(x, y), side * r)
                    drawPath(path, color.copy(alpha = twinkle), style = stroke)
                }
        }
        TopPickMood.ALONE_TIME -> {
            // A drop's ripples spreading out across still water.
            val center = f.at(0.5f, 0.55f)
            for (i in 0 until 4) {
                val share = (i + phase) / 4
                val rx = side * 0.5f * share
                drawOval(
                    color.copy(alpha = edgeFade(share)),
                    topLeft = Offset(center.x - rx, center.y - rx * 0.42f),
                    size = Size(rx * 2f, rx * 0.84f),
                    style = stroke,
                )
            }
        }
        TopPickMood.CHILL -> {
            // Three waves rolling slowly sideways.
            clipRect(f.left, f.top, f.x(1f), f.y(1f)) {
                listOf(0.32f, 0.5f, 0.68f).forEachIndexed { row, y ->
                    path.reset()
                    for (k in 0..80) {
                        val u = k / 80f
                        val py = y + 0.05f * sin(TAU * (u / 0.33f - phase - row * 0.15f))
                        if (k == 0) path.moveTo(f.x(u), f.y(py)) else path.lineTo(f.x(u), f.y(py))
                    }
                    drawPath(path, color, style = stroke)
                }
            }
        }
        TopPickMood.COMMUTING -> {
            // A road to the horizon, its centre line rushing toward you.
            drawLine(color, f.at(0.06f, 0.95f), f.at(0.44f, 0.14f), stroke.width, StrokeCap.Round)
            drawLine(color, f.at(0.94f, 0.95f), f.at(0.56f, 0.14f), stroke.width, StrokeCap.Round)
            drawLine(color, f.at(0.15f, 0.14f), f.at(0.85f, 0.14f), stroke.width, StrokeCap.Round)
            for (k in 0 until 5) {
                val u = (k / 5f + phase) % 1f
                val y0 = 0.18f + 0.77f * u * u
                val y1 = (y0 + 0.02f + 0.08f * u).coerceAtMost(0.95f)
                drawLine(color.copy(alpha = edgeFade(u)), f.at(0.5f, y0), f.at(0.5f, y1), stroke.width, StrokeCap.Round)
            }
        }
        TopPickMood.EATING_COOKING -> {
            // A bowl with steam curling up off it.
            path.reset()
            path.arcTo(Rect(f.x(0.12f), f.y(0.25f), f.x(0.88f), f.y(0.95f)), 0f, 180f, true)
            drawPath(path, color, style = stroke)
            drawLine(color, f.at(0.05f, 0.6f), f.at(0.95f, 0.6f), stroke.width, StrokeCap.Round)
            listOf(0.36f, 0.5f, 0.64f).forEachIndexed { index, x ->
                val rise = (phase + index / 3f) % 1f
                path.reset()
                for (k in 0..30) {
                    val u = k / 30f
                    val px = x + 0.035f * sin(TAU * (u * 1.2f + rise))
                    val py = 0.5f - 0.3f * u - 0.1f * rise
                    if (k == 0) path.moveTo(f.x(px), f.y(py)) else path.lineTo(f.x(px), f.y(py))
                }
                drawPath(path, color.copy(alpha = sin(PI.toFloat() * rise)), style = stroke)
            }
        }
        TopPickMood.EVENING -> {
            // The sun sinking behind the horizon and rising back.
            val horizon = 0.62f
            val sink = 0.1f * (0.5f - 0.5f * cos(TAU * phase))
            clipRect(f.left - side, f.top - side, f.x(2f), f.y(horizon)) {
                drawCircle(color, radius = side * 0.26f, center = f.at(0.5f, horizon - 0.02f + sink), style = stroke)
            }
            drawLine(color, f.at(0.04f, horizon), f.at(0.96f, horizon), stroke.width, StrokeCap.Round)
            drawLine(color, f.at(0.2f, horizon + 0.11f), f.at(0.8f, horizon + 0.11f), stroke.width, StrokeCap.Round)
            drawLine(color, f.at(0.36f, horizon + 0.22f), f.at(0.64f, horizon + 0.22f), stroke.width, StrokeCap.Round)
        }
        TopPickMood.FITNESS -> {
            // A heartbeat trace, a bright pulse running along it.
            val trace = cache.outline(size, measured = true) { out ->
                listOf(0f to 0.55f, 0.28f to 0.55f, 0.36f to 0.42f, 0.44f to 0.76f, 0.52f to 0.18f, 0.6f to 0.84f, 0.67f to 0.55f, 1f to 0.55f)
                    .forEachIndexed { index, (x, y) -> if (index == 0) out.moveTo(f.x(x), f.y(y)) else out.lineTo(f.x(x), f.y(y)) }
            }
            drawPath(trace, color.copy(alpha = 0.3f), style = stroke)
            val length = cache.length
            val head = phase * 1.4f * length
            segment.reset()
            cache.measure.getSegment((head - 0.45f * length).coerceAtLeast(0f), head.coerceAtMost(length), segment, true)
            drawPath(segment, color, style = stroke)
        }
        TopPickMood.GAMING -> {
            // Squares inside squares, turning against each other.
            listOf(0.9f, 0.62f, 0.34f).forEachIndexed { index, size ->
                val turn = (if (index % 2 == 0) 1f else -1f) * phase * 90f
                rotate(turn, f.center) {
                    drawRect(
                        color,
                        topLeft = f.at(0.5f - size / 2f, 0.5f - size / 2f),
                        size = Size(side * size, side * size),
                        style = stroke,
                    )
                }
            }
        }
        TopPickMood.HOME -> {
            val house = cache.outline(size) { out ->
                listOf(0.5f to 0.1f, 0.94f to 0.5f, 0.82f to 0.5f, 0.82f to 0.92f, 0.18f to 0.92f, 0.18f to 0.5f, 0.06f to 0.5f)
                    .forEachIndexed { index, (x, y) -> if (index == 0) out.moveTo(f.x(x), f.y(y)) else out.lineTo(f.x(x), f.y(y)) }
                out.close()
            }
            drawPath(house, color, style = stroke)
            drawRect(color, topLeft = f.at(0.41f, 0.66f), size = Size(side * 0.18f, side * 0.26f), style = stroke)
            // A lamp coming on and off in the window.
            val glow = 0.5f + 0.5f * wave(phase, 0f)
            drawRect(color.copy(alpha = 0.15f + 0.45f * glow), topLeft = f.at(0.27f, 0.56f), size = Size(side * 0.1f, side * 0.1f))
            drawRect(color, topLeft = f.at(0.27f, 0.56f), size = Size(side * 0.1f, side * 0.1f), style = stroke)
        }
        TopPickMood.MORNING -> {
            // The sun coming up, its rays turning.
            val horizon = 0.72f
            val sun = f.at(0.5f, horizon - 0.03f - 0.05f * (0.5f + 0.5f * wave(phase, 0f)))
            clipRect(f.left - side, f.top - side, f.x(2f), f.y(horizon)) {
                drawCircle(color, radius = side * 0.2f, center = sun, style = stroke)
                for (k in 0 until 9) {
                    val angle = PI.toFloat() + (k + 0.5f) * PI.toFloat() / 9f + phase * 0.35f
                    val a = Offset(sun.x + side * 0.28f * cos(angle), sun.y + side * 0.28f * sin(angle))
                    val b = Offset(sun.x + side * 0.38f * cos(angle), sun.y + side * 0.38f * sin(angle))
                    drawLine(color, a, b, stroke.width, StrokeCap.Round)
                }
            }
            drawLine(color, f.at(0.04f, horizon), f.at(0.96f, horizon), stroke.width, StrokeCap.Round)
        }
        TopPickMood.MOTIVATION -> {
            // Chevrons climbing up and out of the frame.
            for (k in 0 until 3) {
                val u = (k / 3f + phase) % 1f
                val y = 0.82f - 0.66f * u
                path.reset()
                path.moveTo(f.x(0.22f), f.y(y + 0.17f))
                path.lineTo(f.x(0.5f), f.y(y))
                path.lineTo(f.x(0.78f), f.y(y + 0.17f))
                drawPath(path, color.copy(alpha = sin(PI.toFloat() * u)), style = stroke)
            }
        }
        TopPickMood.OUTDOORS -> {
            val ridge = cache.outline(size) { out ->
                listOf(0.02f to 0.86f, 0.36f to 0.36f, 0.55f to 0.62f, 0.7f to 0.44f, 0.98f to 0.86f)
                    .forEachIndexed { index, (x, y) -> if (index == 0) out.moveTo(f.x(x), f.y(y)) else out.lineTo(f.x(x), f.y(y)) }
            }
            drawPath(ridge, color, style = stroke)
            drawLine(color, f.at(0.02f, 0.86f), f.at(0.98f, 0.86f), stroke.width, StrokeCap.Round)
            drawCircle(color, radius = side * 0.09f, center = f.at(0.78f + 0.02f * wave(phase, 0.25f), 0.2f + 0.02f * wave(phase, 0f)), style = stroke)
        }
        TopPickMood.PARTY -> {
            // Confetti stars, spinning and popping.
            listOf(
                Triple(0.24f, 0.24f, 0.14f), Triple(0.72f, 0.18f, 0.11f), Triple(0.52f, 0.5f, 0.17f),
                Triple(0.2f, 0.72f, 0.1f), Triple(0.8f, 0.66f, 0.13f), Triple(0.56f, 0.88f, 0.08f),
            ).forEachIndexed { index, (x, y, r) ->
                val pop = 0.85f + 0.15f * wave(phase, index / 6f)
                val spin = (if (index % 2 == 0) 1f else -1f) * phase * 360f
                fivePointStar(path, f.at(x, y), side * r)
                withTransform({
                    rotate(spin, f.at(x, y))
                    scale(pop, pop, f.at(x, y))
                }) { drawPath(path, color, style = stroke) }
            }
        }
        TopPickMood.SLEEP -> {
            // A closed eye, breathing.
            val breath = 1f + 0.04f * wave(phase, 0f)
            scale(breath, breath, f.center) {
                path.reset()
                path.arcTo(Rect(f.x(0.12f), f.y(0.2f), f.x(0.88f), f.y(0.66f)), 10f, 160f, true)
                drawPath(path, color, style = stroke)
                for (k in 0 until 5) {
                    val angle = (30f + k * 30f) * PI.toFloat() / 180f
                    val base = Offset(f.x(0.5f + 0.38f * cos(angle)), f.y(0.43f + 0.23f * sin(angle)))
                    val tip = Offset(base.x + side * 0.08f * cos(angle), base.y + side * 0.1f * sin(angle))
                    drawLine(color, base, tip, stroke.width, StrokeCap.Round)
                }
            }
        }
        TopPickMood.SOCIAL -> {
            // Two circles drifting together and apart.
            val gap = 0.12f + 0.07f * wave(phase, 0f)
            drawCircle(color, radius = side * 0.27f, center = f.at(0.5f - gap, 0.5f), style = stroke)
            drawCircle(color, radius = side * 0.27f, center = f.at(0.5f + gap, 0.5f), style = stroke)
        }
        TopPickMood.SPORTS -> {
            // A ball, spinning.
            val r = side * 0.44f
            drawCircle(color, radius = r, center = f.center, style = stroke)
            path.reset()
            path.addOval(Rect(f.center, r))
            clipPath(path) {
                rotate(phase * 360f, f.center) {
                    drawCircle(color, radius = r * 0.95f, center = Offset(f.center.x - r * 1.25f, f.center.y), style = stroke)
                    drawCircle(color, radius = r * 0.95f, center = Offset(f.center.x + r * 1.25f, f.center.y), style = stroke)
                }
            }
        }
        TopPickMood.SUMMERTIME -> {
            drawCircle(color, radius = side * 0.22f, center = f.center, style = stroke)
            rotate(phase * 30f, f.center) {
                for (k in 0 until 12) {
                    val angle = k * TAU / 12f
                    drawLine(
                        color,
                        Offset(f.center.x + side * 0.31f * cos(angle), f.center.y + side * 0.31f * sin(angle)),
                        Offset(f.center.x + side * 0.46f * cos(angle), f.center.y + side * 0.46f * sin(angle)),
                        stroke.width,
                        StrokeCap.Round,
                    )
                }
            }
        }
        TopPickMood.HOLIDAY -> {
            // A snowflake, turning.
            rotate(phase * 60f, f.center) {
                for (k in 0 until 6) {
                    val angle = k * TAU / 6f
                    val dir = Offset(cos(angle), sin(angle))
                    fun point(d: Float) = Offset(f.center.x + side * d * dir.x, f.center.y + side * d * dir.y)
                    drawLine(color, f.center, point(0.46f), stroke.width, StrokeCap.Round)
                    listOf(0.22f, 0.34f).forEach { d ->
                        val base = point(d)
                        listOf(-0.785f, 0.785f).forEach { turn ->
                            val a = angle + turn
                            drawLine(color, base, Offset(base.x + side * 0.1f * cos(a), base.y + side * 0.1f * sin(a)), stroke.width, StrokeCap.Round)
                        }
                    }
                }
            }
        }
        TopPickMood.WEEKEND -> {
            // A record spinning.
            drawCircle(color, radius = side * 0.46f, center = f.center, style = stroke)
            drawCircle(color, radius = side * 0.16f, center = f.center, style = stroke)
            drawCircle(color, radius = side * 0.025f, center = f.center)
            rotate(phase * 360f, f.center) {
                listOf(0.36f, 0.27f).forEach { r ->
                    val box = Rect(f.center, side * r)
                    drawArc(color, 200f, 70f, false, box.topLeft, box.size, style = stroke)
                    drawArc(color, 20f, 70f, false, box.topLeft, box.size, style = stroke)
                }
            }
        }
        TopPickMood.WELLBEING -> {
            // A lotus, its petals opening with each breath.
            val open = 0.85f + 0.15f * wave(phase, 0f)
            val base = f.at(0.5f, 0.84f)
            listOf(-62f, -31f, 0f, 31f, 62f).forEach { angle ->
                rotate(angle * open, base) {
                    path.reset()
                    path.moveTo(base.x, base.y)
                    path.quadraticTo(f.x(0.66f), f.y(0.52f), f.x(0.5f), f.y(0.14f + (1f - open) * 0.3f))
                    path.quadraticTo(f.x(0.34f), f.y(0.52f), base.x, base.y)
                    drawPath(path, color, style = stroke)
                }
            }
        }
        TopPickMood.WORK -> {
            // A laptop, a line of text being typed and the cursor blinking.
            drawRoundRect(
                color,
                topLeft = f.at(0.16f, 0.18f),
                size = Size(side * 0.68f, side * 0.48f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(side * 0.03f),
                style = stroke,
            )
            drawLine(color, f.at(0.04f, 0.78f), f.at(0.96f, 0.78f), stroke.width, StrokeCap.Round)
            drawLine(color, f.at(0.16f, 0.66f), f.at(0.04f, 0.78f), stroke.width, StrokeCap.Round)
            drawLine(color, f.at(0.84f, 0.66f), f.at(0.96f, 0.78f), stroke.width, StrokeCap.Round)
            val typed = 0.28f + 0.36f * (phase * 1.25f).coerceAtMost(1f)
            drawLine(color, f.at(0.28f, 0.36f), f.at(0.64f, 0.36f), stroke.width, StrokeCap.Round)
            drawLine(color, f.at(0.28f, 0.48f), f.at(typed, 0.48f), stroke.width, StrokeCap.Round)
            if ((phase * 4f).toInt() % 2 == 0) {
                drawLine(color, f.at(typed + 0.04f, 0.43f), f.at(typed + 0.04f, 0.53f), stroke.width, StrokeCap.Round)
            }
        }
    }
}

/** Fades a ring in at the outer edge and out at the centre. */
private fun edgeFade(share: Float): Float = when {
    share > 0.85f -> (1f - share) / 0.15f
    share < 0.15f -> share / 0.15f
    else -> 1f
}.coerceIn(0f, 1f)

private fun easeInOut(t: Float): Float = 0.5f - 0.5f * cos(PI.toFloat() * t.coerceIn(0f, 1f))

/** Apple's slanted bolt, corner by corner off their artwork. */
private fun bolt(path: Path, f: Frame) {
    val points = listOf(
        0.486f to 0.123f, 0.886f to 0.123f, 0.532f to 0.509f, 0.877f to 0.509f,
        0.114f to 0.895f, 0.409f to 0.614f, 0.105f to 0.614f,
    )
    path.reset()
    points.forEachIndexed { index, (x, y) ->
        if (index == 0) path.moveTo(f.x(x), f.y(y)) else path.lineTo(f.x(x), f.y(y))
    }
    path.close()
}

/** Four points joined by inward curves. */
private fun star(path: Path, f: Frame) {
    path.reset()
    path.moveTo(f.x(0.5f), f.y(0.06f))
    path.quadraticTo(f.x(0.56f), f.y(0.44f), f.x(0.94f), f.y(0.5f))
    path.quadraticTo(f.x(0.56f), f.y(0.56f), f.x(0.5f), f.y(0.94f))
    path.quadraticTo(f.x(0.44f), f.y(0.56f), f.x(0.06f), f.y(0.5f))
    path.quadraticTo(f.x(0.44f), f.y(0.44f), f.x(0.5f), f.y(0.06f))
    path.close()
}

/** The same four-pointed star, small, around [center]. */
private fun sparkle(path: Path, center: Offset, r: Float) {
    path.reset()
    path.moveTo(center.x, center.y - r)
    path.quadraticTo(center.x + r * 0.12f, center.y - r * 0.12f, center.x + r, center.y)
    path.quadraticTo(center.x + r * 0.12f, center.y + r * 0.12f, center.x, center.y + r)
    path.quadraticTo(center.x - r * 0.12f, center.y + r * 0.12f, center.x - r, center.y)
    path.quadraticTo(center.x - r * 0.12f, center.y - r * 0.12f, center.x, center.y - r)
    path.close()
}

private fun fivePointStar(path: Path, center: Offset, r: Float) {
    path.reset()
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) r else r * 0.45f
        val angle = i * PI.toFloat() / 5f - PI.toFloat() / 2f
        val x = center.x + radius * cos(angle)
        val y = center.y + radius * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
}

/** A crescent: a disc with a bite taken out of its upper right. */
private fun crescent(path: Path, f: Frame, cx: Float, cy: Float, r: Float) {
    val disc = Path().apply { addOval(Rect(f.at(cx, cy), f.side * r)) }
    val bite = Path().apply { addOval(Rect(f.at(cx + r * 0.5f, cy - r * 0.3f), f.side * r * 0.88f)) }
    path.reset()
    path.op(disc, bite, PathOperation.Difference)
}

/** Two round lobes and straight sides down to a point. */
private fun heart(path: Path, f: Frame) {
    val r = 0.24f
    path.reset()
    path.moveTo(f.x(0.5f), f.y(0.92f))
    path.lineTo(f.x(0.28f - r * 0.707f), f.y(0.30f + r * 0.707f))
    path.arcTo(Rect(f.x(0.28f - r), f.y(0.30f - r), f.x(0.28f + r), f.y(0.30f + r)), 135f, 195f, false)
    path.arcTo(Rect(f.x(0.72f - r), f.y(0.30f - r), f.x(0.72f + r), f.y(0.30f + r)), 210f, 195f, false)
    path.close()
}

/** A coil that narrows as it falls, like Apple's Feeling Blue twister. */
private fun spiral(path: Path, f: Frame) {
    val turns = 5.5f
    val steps = 320
    path.reset()
    for (k in 0..steps) {
        val t = k.toFloat() / steps
        val angle = PI.toFloat() + t * turns * TAU
        val rx = 0.38f * (1f - 0.72f * t)
        val ry = 0.075f * (1f - 0.55f * t)
        val x = f.x(0.5f + rx * cos(angle))
        val y = f.y(0.10f + 0.78f * t + ry * sin(angle))
        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
}

// ---- Find Your Mood -----------------------------------------------------------

/**
 * Apple Music's Find Your Mood shelf: its seven moods as square tiles, each
 * named under it, and Show all on the heading for the activities as well —
 * the grid [MoodGridPage] lays out. A tile plays that mood's station, the same
 * queue a Mood for You card starts.
 */
@Composable
internal fun FindYourMoodShelf(onClick: (TopPickMood, String) -> Unit, onShowAll: () -> Unit) {
    val rowState = rememberLazyListState()
    Column(Modifier.padding(bottom = 26.dp)) {
        SectionHeader(stringResource(Res.string.find_your_mood), onShowAll = onShowAll)
        ShelfRow(
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            state = rowState,
        ) {
            items(TopPickMood.entries.filter { it.isCoreMood }, key = { it.name }) { mood ->
                MoodTile(mood, onClick, Modifier.width(SHELF_CARD_WIDTH))
            }
        }
    }
}

/**
 * Every mood and activity, laid out as the Library's Show all grid is — the
 * same columns, card width and spacing — with Apple's seven moods first.
 */
@Composable
fun MoodGridPage(
    gridState: LazyGridState,
    onClick: (TopPickMood, String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = libraryGrid(maxWidth - PAGE_GUTTER * 2)
        LazyVerticalGrid(
            columns = GridCells.Fixed(grid.columns),
            state = gridState,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(LIBRARY_GRID_SPACING),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(horizontal = PAGE_GUTTER),
        ) {
            gridItems(TopPickMood.entries, key = { it.name }) { mood ->
                MoodTile(mood, onClick, Modifier.fillMaxWidth())
            }
        }
    }
}

/** A mood's square tile and its name under it, sized like a [ShelfCard]. */
@Composable
private fun MoodTile(mood: TopPickMood, onClick: (TopPickMood, String) -> Unit, modifier: Modifier) {
    val title = stringResource(mood.titleRes())
    Column(modifier.clickable { onClick(mood, title) }) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .thumbnailBorder(RoundedCornerShape(12.dp)),
        ) {
            // Still. A shelf of tiles each breathing on its own loop kept the
            // page under the glass bars redrawing — and the bars' blur with
            // it — for as long as the shelf was in view; the motion is Top
            // Picks' own, one card at a time.
            MoodArt(mood, mood.style(), Modifier.fillMaxSize(), square = true, still = true)
            Icon(
                painter = painterResource(Res.drawable.ic_logo),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .height(11.dp)
                    .aspectRatio(730f / 484f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---- Featuring … & Similar Artists ----------------------------------------------

private class CollagePalette(val top: Color, val bottom: Color, val eyebrow: Color)

/** Vivid backdrops, one per artist and the same one every day. */
private val CollagePalettes = listOf(
    CollagePalette(Color(0xFF8F3F00), Color(0xFFFF6A0F), Color(0xFFFFC48A)),
    CollagePalette(Color(0xFF2D8A20), Color(0xFF4FD22C), Color(0xFFCBFFA3)),
    CollagePalette(Color(0xFF173E9E), Color(0xFF2F7BFF), Color(0xFFC0D8FF)),
    CollagePalette(Color(0xFF4A1E99), Color(0xFF8A4BFF), Color(0xFFDCC8FF)),
    CollagePalette(Color(0xFF0B6E6E), Color(0xFF14B8A6), Color(0xFFB5FFF3)),
    CollagePalette(Color(0xFF8E1426), Color(0xFFF0364F), Color(0xFFFFC4CC)),
)

private fun collagePalette(name: String): CollagePalette = CollagePalettes[abs(name.hashCode()) % CollagePalettes.size]

/**
 * The artist in a large circle, with up to two related artists in smaller
 * ones beneath it, on a colour that brightens toward the caption.
 *
 * Still, unlike the single-artist photo card: an artist's clip is shot for a
 * full portrait frame, and cut down to a circle among other faces it reads as
 * a small moving tile rather than the artist.
 */
@Composable
private fun ArtistCollageArt(
    images: List<String>,
    palette: CollagePalette,
    modifier: Modifier,
) {
    BoxWithConstraints(
        modifier.background(Brush.verticalGradient(listOf(palette.top, palette.bottom))),
    ) {
        val w = maxWidth
        val h = maxHeight
        // Centre x, centre y (as shares of the card) and diameter (of its width).
        val slots = when (images.size) {
            0 -> emptyList()
            1 -> listOf(Triple(0.5f, 0.34f, 0.66f))
            2 -> listOf(Triple(0.40f, 0.28f, 0.60f), Triple(0.72f, 0.52f, 0.36f))
            else -> listOf(Triple(0.37f, 0.257f, 0.594f), Triple(0.76f, 0.48f, 0.333f), Triple(0.457f, 0.608f, 0.29f))
        }
        slots.zip(images).forEach { (slot, url) ->
            val (cx, cy, d) = slot
            val diameter = w * d
            val circle = Modifier
                .offset(x = w * cx - diameter / 2, y = h * cy - diameter / 2)
                .size(diameter)
                .clip(CircleShape)
            AsyncImage(
                model = url.croppedAt(if (d > 0.5f) HEADER_ART_PX else CARD_ART_PX).whenArtworkReady(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = circle.background(Color.Black.copy(alpha = 0.18f)),
            )
        }
    }
}

private val GOOGLE_SIZE_SUFFIX = Regex("""=w\d+-h\d+[^/?]*$""")

/**
 * Artwork cut to the card's own shape by Google's image server rather than by
 * the view. An artist's header picture is a wide banner; asked for a portrait
 * or a square with `-p`, the server crops it around the subject, which a
 * centre crop of the banner does not. Anything else is sized as usual.
 */
private fun String?.croppedAt(width: Int, height: Int = width): String? {
    if (this == null) return null
    // Apple Music's photos: the last path segment is the size. Their artist
    // portraits are square, and asking for a portrait crop gets the square
    // back anyway, so the square is asked for and the card crops it.
    if ("mzstatic.com/image/thumb/" in this) {
        val side = maxOf(width, height)
        return substringBeforeLast('/') + "/${side}x${side}cc.jpg"
    }
    return if (GOOGLE_SIZE_SUFFIX.containsMatchIn(this)) {
        replace(GOOGLE_SIZE_SUFFIX, "=w$width-h$height-p-l90-rj")
    } else {
        artworkAt(maxOf(width, height))
    }
}
