package com.music.bitchord.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import kotlin.math.abs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.border

fun Modifier.thumbnailBorder(shape: Shape): Modifier = composed {
    this.border(
        width = 1.dp,
        color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f),
        shape = shape
    )
}

/**
 * The left and right inset every page's content sits at.
 *
 * It is the same inset the mini player and the tab bar float at, so the edge of
 * a track row, a card or a heading lines up with the edge of the bars stacked
 * below them rather than stepping in from them. One constant, shared by the
 * bars and the pages, is what keeps that true.
 */
val PAGE_GUTTER = 10.dp

/** Where a divider under a track row starts: clear of the 52dp of artwork. */
val ROW_DIVIDER_INSET = PAGE_GUTTER + 68.dp

/**
 * How wide the floating bars at the foot of the page — the tab bar and the mini
 * player above it — are ever allowed to get.
 *
 * Both are fixed rows of controls with a fixed amount to say, not content that
 * benefits from room, and a phone is the width they were spaced for. Run right
 * across a tablet the four tabs end up a hand apart with their labels marooned
 * in the middle of nothing, and the mini player puts its artwork and its buttons
 * at opposite ends of the screen with a lake of frosted glass between. Past this
 * they stop growing and centre themselves over the page instead.
 *
 * Set clear of the widest phone (448dp less the two [PAGE_GUTTER]s is 428dp), so
 * on a phone it does nothing and the bars still line up with the page's content.
 */
val FLOATING_BAR_MAX_WIDTH = 440.dp

/**
 * Width of a card in the compact carousels — home shelves, library shelves and
 * the artist page's releases alike.
 *
 * Sized so a phone-width row shows two cards whole with the edge of a third
 * showing: enough to say the row scrolls without a card being half a card.
 */
val SHELF_CARD_WIDTH = 150.dp

/** A song title with the catalogue-standard outlined E for explicit audio. */
@Composable
fun ExplicitSongTitle(
    song: Song,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (song.isExplicit == true) {
            Text(
                text = "E",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                modifier = Modifier
                    .border(1.dp, color.copy(alpha = 0.72f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 3.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = song.title,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Share of the row a lead-shelf card takes, so the next one peeks in past it. */
private const val HERO_CARD_FRACTION = 0.70f

/**
 * How wide a lead-shelf card is ever allowed to get.
 *
 * The fraction alone is a phone measurement wearing a percent sign: 70% of a
 * tablet is a card the better part of a foot across, and a hero card is a
 * caption over some artwork rather than a canvas — blown up that far it stops
 * being the top of a feed and becomes a poster with a shelf hiding under it.
 *
 * Set just clear of what the widest phone asks for (0.70 of 448dp is 314dp), so
 * every phone keeps the width the fraction gives it and only a screen wider than
 * any phone is held back to it.
 */
private val HERO_CARD_MAX_WIDTH = 320.dp

/** A lead-shelf card's proportions: a touch taller than it is wide. */
const val HERO_CARD_RATIO = 0.92f

/**
 * How wide a lead-shelf card should be in a row [available] wide — the shared
 * answer for the real shelf and for the skeleton that stands in for it, which
 * have to agree to the pixel or the feed jumps when the data lands.
 *
 * Given the row's own width rather than the window's, so it is still right in
 * the narrower column a tablet leaves once the player has taken its pane.
 * Height follows from [HERO_CARD_RATIO], so the card keeps its shape at any
 * width.
 */
fun heroCardWidth(available: Dp): Dp = minOf(available * HERO_CARD_FRACTION, HERO_CARD_MAX_WIDTH)

/** How many cards sit across a library grid row, and how wide each lands. */
data class LibraryGridSpec(val columns: Int, val cardWidth: Dp)

/** The narrowest a library grid card is let get before another column gives way. */
private val LIBRARY_GRID_MIN_CARD_WIDTH = 84.dp

/** Gap between cards in a library grid, in both directions. */
val LIBRARY_GRID_SPACING = 12.dp

private const val LIBRARY_GRID_MIN_COLUMNS = 2

/** Library shelves never grow past this many across, however wide the screen. */
private const val LIBRARY_GRID_MAX_COLUMNS = 5

/**
 * How a Library shelf's full "Show all" page lays out as a grid, in
 * [available] dp of row — see `LibraryGridPage`.
 *
 * Columns follow from [LIBRARY_GRID_MIN_CARD_WIDTH] — as many as fit — rather
 * than from a fixed count, so a phone settles on 3 or 4 and a tablet fills out
 * to the 5-column ceiling. Every width here is already in dp, which is what
 * makes this "based on device width and dpi" rather than a raw pixel count: a
 * dp reads the same physical size on a 420ppi phone as on a 160ppi tablet, so
 * the column count tracks how much room there actually is rather than how
 * many pixels the panel happens to report.
 *
 * The shelf's own preview row on the Library page itself is unrelated — it
 * keeps the fixed [SHELF_CARD_WIDTH] every other shelf uses and a flat
 * five-card cap rather than a width-derived one, so a card is the same size
 * whether the row it's in scrolls or not. See `LibraryGridShelf`.
 */
fun libraryGrid(available: Dp): LibraryGridSpec {
    val raw = ((available + LIBRARY_GRID_SPACING) / (LIBRARY_GRID_MIN_CARD_WIDTH + LIBRARY_GRID_SPACING))
        .toInt()
    val columns = raw.coerceIn(LIBRARY_GRID_MIN_COLUMNS, LIBRARY_GRID_MAX_COLUMNS)
    val cardWidth = (available - LIBRARY_GRID_SPACING * (columns - 1)) / columns
    return LibraryGridSpec(columns, cardWidth)
}

/**
 * One track row, used by search, library and detail pages.
 *
 * Swiping it either way queues the track or plays it next, per
 * [AppSettings.swipeToPlayNext] — the row springs back rather than
 * dismissing, since nothing is being removed. Long-press opens the actions
 * menu.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    onSwipeToQueue: (() -> Unit)? = null,
    /**
     * What the row paints over the swipe reveal as it slides back.
     *
     * It has to be the colour of the page the row is *on*, not the theme's
     * background — an album page tinted from its sleeve would otherwise drag a
     * black band across itself on every swipe.
     */
    rowBackground: Color = MaterialTheme.colorScheme.background,
    /**
     * Drawn in place of the artwork, for lists where every row would otherwise
     * repeat the same cover — an album's own track listing.
     */
    trackNumber: Int? = null,
    /**
     * The artist line, and the track number when there is one.
     *
     * A page tinted from its artwork wants this brighter than the flat feeds
     * do: the usual dim grey is pitched against black, and against a mid-toned
     * wash it stops being legible as a second line and starts disappearing.
     */
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    /**
     * What the badge on an already-downloaded row is tinted, or null to leave
     * those rows unmarked.
     *
     * Null is for the Downloads page itself, where every row qualifies and the
     * badge would say nothing. The colour is a parameter for the same reason
     * [subtitleColor] is: a page tinted from its artwork draws its accent from
     * the sleeve, and the theme's primary against that wash is exactly the
     * kind of thing that reads as pasted on.
     */
    downloadedTint: Color? = MaterialTheme.colorScheme.primary,
    /** Whether this row is the current item in the player's queue. */
    isCurrent: Boolean = false,
    /** Distinguishes active playback from the same current item while paused. */
    isPlaying: Boolean = false,
    /** Accent supplied by artwork-tinted pages. */
    activeTint: Color = MaterialTheme.colorScheme.primary,
    /** True while a Downloads row belongs to the current multi-selection. */
    selected: Boolean = false,
) {
    val haptics = rememberHaptics()
    val swipeStateHolder = remember { mutableStateOf<SwipeToDismissBoxState?>(null) }
    var boxWidth by remember { mutableFloatStateOf(0f) }

    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled && onSwipeToQueue != null) {
                val offset = try { swipeStateHolder.value?.requireOffset() ?: 0f } catch (e: Exception) { 0f }
                // Only queue if the physical drag reached half the box width, ignoring short accidental flings.
                if (abs(offset) >= boxWidth * 0.45f) {
                    haptics.play(Haptic.Select)
                    onSwipeToQueue()
                }
            }
            false // never actually dismiss; snap back
        },
        positionalThreshold = { distance -> distance * 0.5f },
    )
    swipeStateHolder.value = swipeState

    if (onSwipeToQueue == null) {
        SongRowContent(
            song = song,
            onClick = onClick,
            onLongPress = onLongPress,
            modifier = modifier,
            trackNumber = trackNumber,
            subtitleColor = subtitleColor,
            downloadedTint = downloadedTint,
            isCurrent = isCurrent,
            isPlaying = isPlaying,
            activeTint = activeTint,
            selected = selected,
        )
        return
    }

    // The row reveals "Queue" from the first pixel of the drag, but it only
    // *commits* past 45% of the width — so without this the label is a promise
    // the finger can't check. One light tick at the crossing is the whole point:
    // let go now and it queues.
    LaunchedEffect(swipeState, boxWidth) {
        if (boxWidth <= 0f) return@LaunchedEffect
        val armAt = boxWidth * 0.45f
        var armed = false
        snapshotFlow { try { swipeState.requireOffset() } catch (e: Exception) { 0f } }
            .collect { offset ->
                val travelled = abs(offset)
                when {
                    !armed && travelled >= armAt -> {
                        armed = true
                        haptics.play(Haptic.Tick)
                    }
                    // Silent, and with hysteresis: dragging back under the line
                    // re-arms, but so does the spring-back after a successful
                    // queue, and that must not buzz the same gesture twice.
                    armed && travelled < armAt * 0.8f -> armed = false
                }
            }
    }

    SwipeToDismissBox(
        state = swipeState,
        modifier = modifier.onSizeChanged { boxWidth = it.width.toFloat() },
        backgroundContent = { QueueSwipeBackground(swipeState) },
    ) {
        SongRowContent(
            song = song,
            onClick = onClick,
            onLongPress = onLongPress,
            modifier = Modifier.background(rowBackground),
            trackNumber = trackNumber,
            subtitleColor = subtitleColor,
            downloadedTint = downloadedTint,
            isCurrent = isCurrent,
            isPlaying = isPlaying,
            activeTint = activeTint,
            selected = selected,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSwipeBackground(swipeState: SwipeToDismissBoxState) {
    val playNext by AppSettings.swipeToPlayNext.collectAsStateWithLifecycle()
    Row(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                val offset = try { swipeState.requireOffset() } catch (e: Exception) { 0f }
                if (offset > 0f) {
                    clipRect(left = 0f, top = 0f, right = offset, bottom = size.height) {
                        this@drawWithContent.drawContent()
                    }
                } else if (offset < 0f) {
                    clipRect(left = size.width + offset, top = 0f, right = size.width, bottom = size.height) {
                        this@drawWithContent.drawContent()
                    }
                }
            }
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            .padding(horizontal = PAGE_GUTTER + 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        QueueSwipeLabel(playNext)
        QueueSwipeLabel(playNext)
    }
}

@Composable
private fun QueueSwipeLabel(playNext: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (playNext) Icons.Rounded.PlaylistPlay else Icons.Rounded.PlaylistAdd,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(if (playNext) R.string.play_next else R.string.queue),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRowContent(
    song: Song,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trackNumber: Int? = null,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    downloadedTint: Color? = MaterialTheme.colorScheme.primary,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    activeTint: Color = MaterialTheme.colorScheme.primary,
    selected: Boolean = false,
) {
    val titleColor by animateColorAsState(
        targetValue = if (isCurrent) activeTint else MaterialTheme.colorScheme.onBackground,
        label = "song row title",
    )
    val activeBackground by animateColorAsState(
        targetValue = if (isCurrent || selected) activeTint.copy(alpha = 0.14f) else Color.Transparent,
        label = "song row background",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(activeBackground)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = PAGE_GUTTER, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (trackNumber != null) {
            // Same 52dp the artwork would take, so a numbered list and an
            // illustrated one share a left edge and a divider inset.
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                if (isCurrent) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.now_playing),
                        tint = activeTint,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Text(
                        text = "$trackNumber",
                        style = MaterialTheme.typography.bodyLarge,
                        color = subtitleColor,
                    )
                }
            }
        } else {
            AsyncImage(
                model = song.artworkAt(ROW_ART_PX),
                contentDescription = null,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .thumbnailBorder(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            ExplicitSongTitle(
                song = song,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = listOfNotNull(
                    song.artist.takeIf { it.isNotBlank() },
                    song.downloadFormat,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (downloadedTint != null) {
            DownloadedBadge(song.videoId, downloadedTint)
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = stringResource(R.string.selected),
                tint = activeTint,
                modifier = Modifier.size(20.dp),
            )
        }
        if (isCurrent && trackNumber == null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.now_playing),
                tint = activeTint,
                modifier = Modifier.size(20.dp),
            )
        }
        song.durationText?.let {
            Spacer(Modifier.width(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = subtitleColor,
            )
        }
        // Same sheet the long-press opens, for anyone who doesn't think to hold.
        if (onLongPress != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onLongPress),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.more),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * The mark on a row whose track is already on disk.
 *
 * Sized under the row's "more" glyph on purpose: this is a statement about the
 * track, not something to press, and a status mark that matches an affordance
 * in weight invites a tap that does nothing.
 *
 * Reads [Downloads.saved] rather than touching the filesystem — a list cannot
 * afford a file check per row, and the map is kept honest by the disk check
 * every real read of a download goes through. The cost of that trade is a row
 * that can claim a file a file manager has since deleted, until something asks
 * for it and the record is pruned.
 */
@Composable
fun DownloadedBadge(videoId: String, tint: Color, modifier: Modifier = Modifier) {
    val saved by Downloads.saved.collectAsStateWithLifecycle()
    if (videoId !in saved) return
    Spacer(Modifier.width(8.dp))
    Icon(
        Icons.Rounded.DownloadDone,
        contentDescription = stringResource(R.string.downloaded),
        tint = tint,
        modifier = modifier.size(16.dp),
    )
}

/**
 * Pull-to-refresh for the tab feeds, with the usual circular puck suppressed.
 *
 * The feeds sit under a frosted bar that already occupies the top 96dp, so a
 * puck dropping into that space would be blurred out by the glass it lands
 * behind. The drag feedback is the loader line along the bottom edge of the
 * bar instead — which is why [state] is hoisted: the bar lives beside this
 * content, not inside it, and has to follow the same drag.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    state: PullToRefreshState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier.fillMaxSize(),
        indicator = {},
    ) {
        content()
    }
}

/** Slim dismissible-looking prompt shown atop Home while signed out. */
@Composable
fun SignInBanner(onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onSignIn)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.sign_in_youtube_music),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.personalized_recommendations),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Button(onClick = onSignIn) { Text(stringResource(R.string.sign_in)) }
    }
}

@Composable
fun MessageState(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER + 12.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
