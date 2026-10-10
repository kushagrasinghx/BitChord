package com.music.bitchord.ui.tv.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.screens.MOOD_CARD_ASPECT
import com.music.bitchord.ui.screens.MoodGenreSleeve
import com.music.bitchord.ui.tv.theme.TvDimensions
import com.music.bitchord.ui.tv.theme.TvGlass
import com.music.bitchord.ui.tv.theme.TvType
import kotlin.math.floor

// ─────────────────────────────────────────────────────────────────────────────
// Focus
// ─────────────────────────────────────────────────────────────────────────────

private val LiftSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 420f)
private val LiftDpSpring = spring<Dp>(dampingRatio = 1f, stiffness = 420f)

/**
 * tvOS focus: the focused element lifts towards the viewer — it grows and casts
 * a soft shadow — and is never outlined. Scale and shadow live on one render
 * layer and are read in the draw phase, so focus moves cost no recomposition of
 * anything beneath this modifier.
 */
@Composable
fun Modifier.tvLift(
    interactionSource: MutableInteractionSource,
    shape: Shape,
    focusedScale: Float = 1.08f,
    elevation: Dp = 14.dp,
): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val chrome = LocalTvChrome.current
    val requester = remember { FocusRequester() }
    LaunchedEffect(focused) { if (focused) chrome.lastFocused = requester }
    val scale = animateFloatAsState(
        targetValue = when {
            pressed -> 1f + (focusedScale - 1f) * 0.35f
            focused -> focusedScale
            else -> 1f
        },
        animationSpec = LiftSpring,
        label = "tvLiftScale",
    )
    val shadow = animateDpAsState(
        targetValue = if (focused && !pressed) elevation else 0.dp,
        animationSpec = LiftDpSpring,
        label = "tvLiftShadow",
    )
    return this
        .focusRequester(requester)
        .zIndex(if (focused) 1f else 0f)
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            shadowElevation = shadow.value.toPx()
            this.shape = shape
            clip = true
            ambientShadowColor = Color.Black
            spotShadowColor = Color.Black
        }
}

/** Click (and optional long-press) with no ripple; on TV this is also the focus target. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tvClick(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = combinedClickable(
    interactionSource = interactionSource,
    indication = null,
    enabled = enabled,
    onLongClick = onLongClick,
    onClick = onClick,
)

/**
 * Fills the layer behind the content with the resting colour, or the white platter
 * while focused. The colour animates in the draw phase only.
 */
@Composable
fun Modifier.tvPlatter(
    interactionSource: MutableInteractionSource,
    resting: Color,
    focused: Color = TvGlass.Platter,
): Modifier {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val color = animateColorAsState(
        targetValue = if (isFocused) focused else resting,
        animationSpec = tween(140),
        label = "tvPlatter",
    )
    return drawBehind { drawRect(color.value) }
}

/** Puts focus here once, when this first enters composition. */
@Composable
fun Modifier.tvInitialFocus(enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val requester = remember { FocusRequester() }
    LaunchedEffect(requester) { runCatching { requester.requestFocus() } }
    return focusRequester(requester)
}

// ─────────────────────────────────────────────────────────────────────────────
// Chrome: the floating tab bar hides once content scrolls under it
// ─────────────────────────────────────────────────────────────────────────────

@Stable
class TvChrome {
    /** The visible screen has scrolled away from its top; the tab bar steps aside. */
    var contentScrolled by mutableStateOf(false)

    /** Bumped by Back from inside content: the screen scrolls home before the bar takes focus. */
    var scrollToTopRequests by mutableIntStateOf(0)

    /**
     * Bumped whenever a page comes back into view (a pushed page popped), so
     * every live page re-reports its scroll state rather than leaving the last
     * report — the popped page's — standing.
     */
    var epoch by mutableIntStateOf(0)

    /** Whatever lifted element last had focus — read when a page is pushed, so Back can return to it. */
    var lastFocused: FocusRequester? = null
}

val LocalTvChrome = staticCompositionLocalOf { TvChrome() }

@Composable
fun TvChromeScrollEffect(state: LazyListState) {
    val chrome = LocalTvChrome.current
    val scrolled by remember(state) {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 32 }
    }
    LaunchedEffect(scrolled, chrome.epoch) { chrome.contentScrolled = scrolled }
    val seen = remember { chrome.scrollToTopRequests }
    val requests = chrome.scrollToTopRequests
    LaunchedEffect(requests) { if (requests != seen) state.animateScrollToItem(0) }
}

@Composable
fun TvChromeScrollEffect(state: LazyGridState) {
    val chrome = LocalTvChrome.current
    val scrolled by remember(state) {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 32 }
    }
    LaunchedEffect(scrolled, chrome.epoch) { chrome.contentScrolled = scrolled }
    val seen = remember { chrome.scrollToTopRequests }
    val requests = chrome.scrollToTopRequests
    LaunchedEffect(requests) { if (requests != seen) state.animateScrollToItem(0) }
}

/** For screens that never scroll under the bar (two-pane Library, Settings). */
@Composable
fun TvChromePinned() {
    val chrome = LocalTvChrome.current
    LaunchedEffect(chrome.epoch) { chrome.contentScrolled = false }
}

// ─────────────────────────────────────────────────────────────────────────────
// Scrolling: where a focused card is brought to
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Rows scroll themselves ([TvRow]); this keeps Compose's focus-driven scrolling
 * out of their horizontal axis. Requests still travel on to the feed above.
 */
@OptIn(ExperimentalFoundationApi::class)
private object TvRowNoScrollSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

/**
 * A feed's vertical scroll: the focused shelf is brought to one fixed line —
 * where the first shelf's cards rest — so the shelf in focus, captions and all,
 * is always wholly on screen, and every shelf stops in the same place.
 */
@OptIn(ExperimentalFoundationApi::class)
private class TvFeedScrollSpec(private val anchor: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        offset - anchor
}

/**
 * A horizontal row of cards that scrolls by whole cards. It keeps its own index
 * of the first card in view and moves it only when focus passes an edge, then
 * scrolls that card to the leading padding with [LazyListState.animateScrollToItem]
 * — so the first card in view always rests exactly on the margin, and a quick
 * run of presses retargets one animation instead of stranding several.
 *
 * Compose's own bring-into-view isn't used for this axis: driven by focus, it
 * was abandoned part-way when presses came quickly, leaving rows off the grid.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> TvRow(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    leading: Dp = TvDimensions.SafeMarginHorizontal,
    trailing: Dp = TvDimensions.SafeMarginHorizontal,
    spacing: Dp = TvDimensions.CardSpacing,
    top: Dp = 11.dp,
    bottom: Dp = 14.dp,
    contentType: (T) -> Any? = { null },
    itemContent: @Composable (index: Int, item: T) -> Unit,
) {
    val state = rememberLazyListState()
    var first by remember { mutableIntStateOf(state.firstVisibleItemIndex) }
    var focused by remember { mutableIntStateOf(-1) }
    // Entering the row from above or below lands on its first card in view —
    // the one on the margin — never on whichever card happened to be nearest.
    val entry = remember { FocusRequester() }
    val density = LocalDensity.current

    LaunchedEffect(focused) {
        if (focused < 0) return@LaunchedEffect
        val info = state.layoutInfo
        val itemSize = info.visibleItemsInfo.firstOrNull()?.size ?: return@LaunchedEffect
        val (lead, trail, gap) = with(density) { Triple(leading.toPx(), trailing.toPx(), spacing.toPx()) }
        val slots = ((info.viewportSize.width - lead - trail + gap) / (itemSize + gap)).toInt().coerceAtLeast(1)
        val next = when {
            focused < first -> focused
            focused > first + slots - 1 -> focused - slots + 1
            else -> first
        }
        if (next != first || state.firstVisibleItemIndex != next || state.firstVisibleItemScrollOffset != 0) {
            first = next
            state.animateScrollToItem(next)
        }
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides TvRowNoScrollSpec) {
        LazyRow(
            state = state,
            contentPadding = PaddingValues(start = leading, end = trailing, top = top, bottom = bottom),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            modifier = modifier
                .fillMaxWidth()
                .focusProperties { onEnter = { runCatching { entry.requestFocus() } } }
                .focusGroup(),
        ) {
            itemsIndexed(items = items, key = { _, item -> key(item) }, contentType = { _, item -> contentType(item) }) { index, item ->
                Box(
                    modifier = Modifier
                        .then(if (index == first) Modifier.focusRequester(entry) else Modifier)
                        .onFocusChanged { if (it.hasFocus) focused = index },
                ) {
                    itemContent(index, item)
                }
            }
        }
    }
}

/**
 * A page that isn't all shelves (an artist page: a hero, then sections): a
 * focused element is scrolled only far enough to sit inside [top] and [bottom]
 * margins — enough below it for a lockup's caption. Always the remaining
 * distance to that edge, so it gives the same answer every frame of the scroll.
 */
@OptIn(ExperimentalFoundationApi::class)
private class TvComfortScrollSpec(private val top: Float, private val bottom: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val end = containerSize - bottom
        return when {
            offset < top -> offset - top
            offset + size > end -> (offset + size - end).coerceAtMost(offset - top)
            else -> 0f
        }
    }
}

/** Scrolls a focused element in [content] just inside [top] / [bottom]; see [TvComfortScrollSpec]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProvideTvComfortScrolling(top: Dp, bottom: Dp, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val spec = remember(density, top, bottom) {
        with(density) { TvComfortScrollSpec(top.toPx(), bottom.toPx()) }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/** Brings a focused shelf in [content] to [anchor] from the top; see [TvFeedScrollSpec]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProvideTvFeedScrolling(anchor: Dp, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val spec = remember(density, anchor) { TvFeedScrollSpec(with(density) { anchor.toPx() }) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

// ─────────────────────────────────────────────────────────────────────────────
// Artwork & lockups
// ─────────────────────────────────────────────────────────────────────────────

/** Artwork at a size worth showing on a 10-foot screen, with a quiet glyph until it loads. */
@Composable
fun TvArtwork(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    px: Int = 544,
    glyph: ImageVector = Icons.Rounded.MusicNote,
) {
    val model = remember(url, px) { url.artworkAt(px) ?: url }
    Box(
        modifier = modifier
            .clip(shape)
            .background(TvGlass.Placeholder),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = glyph,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.22f),
            modifier = Modifier.fillMaxSize(0.34f),
        )
        if (!model.isNullOrBlank()) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The tvOS lockup: artwork that lifts on focus with its caption below. The
 * caption is never on a platter; it brightens and steps down out of the way
 * of the grown artwork instead.
 */
@Composable
fun TvLockup(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 128.dp,
    circle: Boolean = false,
    aspectRatio: Float = 1f,
    onLongClick: (() -> Unit)? = null,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = if (circle) CircleShape else RoundedCornerShape(10.dp)
    val captionDrop = animateDpAsState(if (focused) 7.dp else 0.dp, LiftDpSpring, label = "captionDrop")

    Column(
        // Dp.Unspecified fills the cell it's given — a grid column rather than a shelf slot.
        modifier = modifier.then(if (width != Dp.Unspecified) Modifier.width(width) else Modifier.fillMaxWidth()),
        horizontalAlignment = if (circle) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .tvLift(interaction, shape)
                .tvClick(interaction, onLongClick = onLongClick, onClick = onClick),
        ) {
            TvArtwork(
                url = artworkUrl,
                shape = shape,
                modifier = Modifier.fillMaxSize(),
            )
            overlay?.invoke(this)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = captionDrop.value.toPx() }
                .padding(top = 8.dp),
            horizontalAlignment = if (circle) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Text(
                text = title,
                style = TvType.CardTitle,
                color = if (focused) TvGlass.TextPrimary else TvGlass.TextPrimary.copy(alpha = 0.86f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (circle) TextAlign.Center else TextAlign.Start,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = TvType.Caption,
                    color = TvGlass.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (circle) TextAlign.Center else TextAlign.Start,
                )
            }
        }
    }
}

/** A shelf heading, aligned to the safe margin. */
@Composable
fun TvShelfTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TvDimensions.SafeMarginHorizontal),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TvType.Headline,
                color = TvGlass.TextPrimary.copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = TvType.Caption,
                    color = TvGlass.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke(this)
    }
}

/**
 * A titled horizontal shelf. Rows run edge to edge — the first card starts at the
 * safe margin and the rest bleed off the right edge, as on Apple TV — with room
 * above and below for a lifted card's growth and shadow.
 */
@Composable
fun <T> TvShelf(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    spacing: Dp = TvDimensions.CardSpacing,
    contentType: (T) -> Any? = { null },
    trailing: (@Composable RowScope.() -> Unit)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TvShelfTitle(title = title, subtitle = subtitle, trailing = trailing)
        TvRow(
            items = items,
            key = key,
            spacing = spacing,
            contentType = contentType,
        ) { _, item ->
            itemContent(item)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Controls
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One segment of the tab bar or a segmented control. Focus inverts it to the
 * white platter; the selected segment keeps a lighter capsule while focus is
 * elsewhere.
 */
@Composable
fun TvTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    leading: (@Composable () -> Unit)? = null,
) {
    val focused by interactionSource.collectIsFocusedAsState()
    Row(
        modifier = modifier
            // No growth on focus: a segment that scaled would pull its rounded
            // ends off-centre from the capsule around it.
            .height(29.dp)
            .tvLift(interactionSource, CircleShape, focusedScale = 1f, elevation = 0.dp)
            .tvPlatter(interactionSource, resting = if (selected) TvGlass.FillSelected else Color.Transparent)
            .tvClick(interactionSource, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading?.invoke()
        Text(
            text = label,
            style = TvType.Tab,
            color = when {
                focused -> TvGlass.OnPlatter
                selected -> TvGlass.TextPrimary
                else -> TvGlass.TextPrimary.copy(alpha = 0.66f)
            },
            maxLines = 1,
        )
    }
}

/** A round icon segment, for Search and Settings at the end of the tab bar. */
@Composable
fun TvTabIcon(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val focused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .size(29.dp)
            .tvLift(interactionSource, CircleShape, focusedScale = 1f, elevation = 0.dp)
            .tvPlatter(interactionSource, resting = if (selected) TvGlass.FillSelected else Color.Transparent)
            .tvClick(interactionSource, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                focused -> TvGlass.OnPlatter
                selected -> TvGlass.TextPrimary
                else -> TvGlass.TextPrimary.copy(alpha = 0.66f)
            },
            modifier = Modifier.size(15.dp),
        )
    }
}

/** A capsule of [TvTabPill]s, for filters and two-or-three-way choices. */
@Composable
fun TvSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(TvGlass.Bar)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            TvTabPill(
                label = label,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
            )
        }
    }
}

/**
 * The tvOS list row, as in Settings and in action menus: title on the left,
 * the current value or a glyph on the right, and a white platter on focus.
 */
@Composable
fun TvListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    resting: Color = Color.White.copy(alpha = 0.06f),
    onFocused: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    if (onFocused != null) {
        LaunchedEffect(focused) { if (focused) onFocused() }
    }
    val titleColor = when {
        !enabled -> TvGlass.TextTertiary
        focused && destructive -> TvGlass.Destructive
        focused -> TvGlass.OnPlatter
        destructive -> TvGlass.Destructive
        else -> TvGlass.TextPrimary
    }
    val secondaryColor = if (focused) TvGlass.OnPlatterSecondary else TvGlass.TextSecondary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp)
            .tvLift(interaction, RoundedCornerShape(11.dp), focusedScale = 1.03f, elevation = 11.dp)
            .tvPlatter(interaction, resting = resting)
            .tvClick(interaction, enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (focused) TvGlass.OnPlatter else titleColor,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TvType.Body,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = TvType.Caption,
                    color = secondaryColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!value.isNullOrBlank()) {
            Text(
                text = value,
                style = TvType.Callout,
                color = secondaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = secondaryColor,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Uppercase-free tvOS group header above a run of [TvListRow]s. */
@Composable
fun TvListHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = TvType.Caption.copy(fontSize = 12.sp),
        color = TvGlass.TextSecondary,
        modifier = modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
    )
}

/**
 * A track in an album, playlist or library list: number (or artwork), title and
 * artist, duration. Long-press opens the song's actions.
 */
@Composable
fun TvSongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    number: Int? = null,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    showArtwork: Boolean = number == null,
    onLongClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val primary = if (focused) TvGlass.OnPlatter else TvGlass.TextPrimary
    val secondary = if (focused) TvGlass.OnPlatterSecondary else TvGlass.TextSecondary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (showArtwork) 48.dp else 42.dp)
            .tvLift(interaction, RoundedCornerShape(10.dp), focusedScale = 1.025f, elevation = 10.dp)
            .tvPlatter(interaction, resting = Color.Transparent)
            .tvClick(interaction, onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        if (showArtwork) {
            TvArtwork(
                url = song.thumbnailUrl,
                px = 160,
                shape = RoundedCornerShape(5.dp),
                modifier = Modifier.size(34.dp),
            )
        } else {
            Box(modifier = Modifier.width(21.dp), contentAlignment = Alignment.CenterStart) {
                if (isCurrent) {
                    TvMiniEqualizer(
                        isPlaying = isPlaying,
                        barColor = if (focused) TvGlass.OnPlatter else TvGlass.AppleRed,
                    )
                } else if (number != null) {
                    Text(text = number.toString(), style = TvType.Callout, color = secondary)
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = TvType.Body.copy(fontSize = 13.sp),
                color = if (isCurrent && !focused) TvGlass.AppleRed else primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (song.artist.isNotBlank()) {
                Text(
                    text = song.artist,
                    style = TvType.Caption,
                    color = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val duration = song.durationText
        if (!duration.isNullOrBlank()) {
            Text(text = duration, style = TvType.Caption, color = secondary)
        }
    }
}

/**
 * A mood or genre card — the phone's own sleeve ([MoodGenreSleeve]: colour stripe,
 * duotone cover, title), lifted on focus.
 */
@Composable
fun TvMoodCard(
    item: MoodGenre,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    MoodGenreSleeve(
        item = item,
        titleStyle = TvType.Body.copy(fontSize = 14.sp),
        modifier = modifier
            .aspectRatio(MOOD_CARD_ASPECT)
            .tvLift(interaction, RoundedCornerShape(13.dp), focusedScale = 1.07f)
            .tvClick(interaction, onClick = onClick),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Status
// ─────────────────────────────────────────────────────────────────────────────

/** The tvOS activity indicator: eight spokes with a travelling bright head. */
@Composable
fun TvActivityIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = Color.White,
) {
    val transition = rememberInfiniteTransition(label = "tvSpinner")
    val step = transition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 840, easing = LinearEasing)),
        label = "tvSpinnerStep",
    )
    Canvas(modifier = modifier.size(size)) {
        val head = floor(step.value).toInt() % 8
        val radius = this.size.minDimension / 2f
        val stroke = radius * 0.2f
        for (i in 0 until 8) {
            val distance = (head - i + 8) % 8
            rotate(degrees = i * 45f) {
                drawLine(
                    color = color.copy(alpha = 1f - distance * 0.1f),
                    start = Offset(center.x, center.y - radius * 0.46f),
                    end = Offset(center.x, center.y - radius * 0.94f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** Resting placeholders for a shelf that hasn't loaded. Static, so they cost nothing to draw. */
@Composable
fun TvShelfPlaceholder(
    modifier: Modifier = Modifier,
    count: Int = 6,
    width: Dp = 128.dp,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(horizontal = TvDimensions.SafeMarginHorizontal)
                .size(width = 144.dp, height = 14.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = 0.06f)),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.padding(horizontal = TvDimensions.SafeMarginHorizontal),
            horizontalArrangement = Arrangement.spacedBy(TvDimensions.CardSpacing),
        ) {
            repeat(count) {
                Box(
                    modifier = Modifier
                        .width(width)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.06f)),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Text entry
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The tvOS text field: a translucent rounded field that turns into the white
 * platter, dark text on it, while focused. Typing goes through the system's TV
 * keyboard.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Done,
    capitalization: androidx.compose.ui.text.input.KeyboardCapitalization =
        androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
    onImeAction: () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val textColor = if (focused) TvGlass.OnPlatter else TvGlass.TextPrimary
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        interactionSource = interaction,
        textStyle = TvType.Body.copy(fontSize = 14.sp, color = textColor),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(textColor),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            capitalization = capitalization,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onAny = { onImeAction() }),
        modifier = modifier
            // One line, so Up and Down have nothing to do inside the field: they
            // always move focus. Compose only lets them out for key events from a
            // D-pad device; a keyboard's arrows (BlueStacks, a USB keyboard, some
            // phone remotes) were kept as cursor moves and left focus stuck here.
            .onPreviewKeyEvent { event ->
                if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Up); true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down); true
                    }
                    else -> false
                }
            }
            .fillMaxWidth()
            .height(43.dp)
            .tvLift(interaction, RoundedCornerShape(10.dp), focusedScale = 1.03f, elevation = 11.dp)
            .tvPlatter(interaction, resting = TvGlass.Fill),
        decorationBox = { inner ->
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = if (focused) TvGlass.OnPlatterSecondary else TvGlass.TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = TvType.Body.copy(fontSize = 14.sp),
                            color = if (focused) TvGlass.OnPlatterSecondary else TvGlass.TextTertiary,
                            maxLines = 1,
                        )
                    }
                    inner()
                }
            }
        },
    )
}
