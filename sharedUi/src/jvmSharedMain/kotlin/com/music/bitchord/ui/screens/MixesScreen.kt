package com.music.bitchord.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.HEADER_ART_PX
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.KeyedMix
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.ic_logo
import com.music.bitchord.sharedui.resources.mixes_none
import com.music.bitchord.sharedui.resources.mixes_yours
import com.music.bitchord.sharedui.resources.retry
import com.music.bitchord.ui.components.MessageState
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.PullToRefresh
import com.music.bitchord.ui.components.ShelfRow
import com.music.bitchord.ui.components.SignInBanner
import com.music.bitchord.ui.components.contextClick
import com.music.bitchord.ui.components.feedSkeleton
import com.music.bitchord.ui.components.longPressMenuClickable
import com.music.bitchord.ui.components.thumbnailBorder
import com.music.bitchord.ui.theme.rememberArtworkPalette
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * YouTube Music's "Mixed for you" cards, laid out the way Apple Music lays out
 * its stations rather than as the web player's one undivided grid.
 *
 * The page is a single flat list of mixes, and it sorts itself into three kinds
 * by name (see [groupMixes]): the handful of named mixes everyone gets, the
 * My Mixes made from this listener's taste, and the mood mixes. Each kind gets
 * the shape that suits how many there are and how personal they are — tiles,
 * a feature carousel, and a shelf per mood.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixesScreen(
    /** The large heading, drawn where Explore and Library draw theirs; null for none. */
    title: String?,
    state: UiState<List<KeyedMix>>,
    signedIn: Boolean,
    listState: LazyListState,
    onItemClick: (ShelfItem) -> Unit,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    pullState: PullToRefreshState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** Holding a card: the playlist menu, as on Home. */
    onItemLongPress: ((ShelfItem) -> Unit)? = null,
) {
    val sections = remember(state) { (state as? UiState.Success)?.data?.let(::groupMixes) }
    PullToRefresh(
        refreshing = refreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            if (title != null) {
                item(key = "title") {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        // Explore's exact spacing, so the heading stays put when
                        // switching between the tabs that carry one.
                        modifier = Modifier.padding(
                            start = PAGE_GUTTER,
                            end = PAGE_GUTTER,
                            top = 8.dp,
                            bottom = 14.dp,
                        ),
                    )
                }
            }
            // Every mix here is made from an account's listening; signed out
            // there is nothing to show but the way in.
            if (!signedIn) {
                item(key = "sign-in") { SignInBanner(onSignIn = onSignIn) }
                return@LazyColumn
            }
            when (state) {
                is UiState.Loading -> feedSkeleton()
                is UiState.Error -> item(key = "error") {
                    MessageState(state.message, actionLabel = stringResource(Res.string.retry), onAction = onRetry)
                }
                is UiState.Success -> {
                    if (sections == null || sections.isEmpty) {
                        item(key = "empty") { MessageState(stringResource(Res.string.mixes_none)) }
                    } else {
                        if (sections.stations.isNotEmpty()) {
                            item(key = "stations") {
                                StationGrid(sections.stations, onItemClick, onItemLongPress)
                            }
                        }
                        if (sections.yours.isNotEmpty()) {
                            item(key = "yours") {
                                FeatureCarousel(
                                    title = stringResource(Res.string.mixes_yours),
                                    items = sections.yours,
                                    onItemClick = onItemClick,
                                    onItemLongPress = onItemLongPress,
                                )
                            }
                        }
                        // A shelf per mood, its Supermix first — Home's own shelf,
                        // so these rows read exactly like the feed's.
                        sections.moods.forEach { mood ->
                            item(key = "mood:${mood.key}") {
                                Shelf(
                                    shelf = HomeShelf(title = mood.name, items = mood.items),
                                    onItemClick = onItemClick,
                                    onItemLongPress = onItemLongPress,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The named mixes as square tiles, three across (six on a wide screen), the
 * way Apple Music opens with its stations.
 */
@Composable
private fun StationGrid(
    items: List<ShelfItem>,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: ((ShelfItem) -> Unit)?,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER)
            .padding(bottom = 26.dp),
    ) {
        val columns = if (maxWidth >= WIDE_GRID_WIDTH) 6 else 3
        Column(verticalArrangement = Arrangement.spacedBy(TILE_SPACING)) {
            items.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(TILE_SPACING)) {
                    row.forEach { item ->
                        StationTile(
                            item = item,
                            onClick = { onItemClick(item) },
                            onLongPress = onItemLongPress?.let { { it(item) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Keeps a short last row's tiles the same size as the rest.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * One named mix, as a station: its artwork blurred down to colour alone, the
 * BitChord mark in the middle, and its name along the foot.
 *
 * The blur is had twice over. The artwork is loaded tiny, so stretching it to
 * the tile already loses every edge — which is the whole effect on Android
 * below 12, where [blur] draws nothing. On 12 and up the blur on top smooths
 * the stretched pixels into a wash. It is scaled past the tile so the blur's
 * soft rim falls outside the clip.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StationTile(
    item: ShelfItem,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(TILE_CORNER)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .thumbnailBorder(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
            .contextClick(onLongPress),
    ) {
        AsyncImage(
            model = item.thumbnailUrl.artworkAt(TILE_BLUR_ART_PX),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = TILE_BLUR_OVERSCAN
                    scaleY = TILE_BLUR_OVERSCAN
                }
                .blur(TILE_BLUR_RADIUS),
        )
        // A light veil, so a pale wash still carries the white mark and name.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = TILE_VEIL_ALPHA)),
        )
        // The mark is centred in the room the name leaves above it, not in the
        // whole tile, so it sits in the middle of what is actually empty.
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_logo),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .fillMaxWidth(TILE_LOGO_FRACTION)
                        .aspectRatio(LOGO_ASPECT),
                )
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 9.dp),
            )
        }
    }
}

/**
 * Cards a size up from Home's, each with its caption on a band tinted from its
 * own artwork — Apple Music's On Air Now, at a fixed width so a tablet shows
 * more of them rather than the same few blown up.
 */
@Composable
private fun FeatureCarousel(
    title: String,
    items: List<ShelfItem>,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: ((ShelfItem) -> Unit)?,
) {
    Column(Modifier.padding(bottom = 26.dp)) {
        SectionHeader(title)
        ShelfRow(
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(items, key = { it.browseId ?: it.title }) { item ->
                FeatureCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongPress = onItemLongPress?.let { { it(item) } },
                    modifier = Modifier.width(FEATURE_CARD_WIDTH),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeatureCard(
    item: ShelfItem,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val palette = rememberArtworkPalette(item.thumbnailUrl, artPx = HEADER_ART_PX)
    val shape = RoundedCornerShape(FEATURE_CORNER)
    Column(
        modifier = modifier
            .clip(shape)
            .thumbnailBorder(shape)
            .background(palette.wash)
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
            .contextClick(onLongPress),
    ) {
        AsyncImage(
            model = item.thumbnailUrl.artworkAt(HEADER_ART_PX),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = palette.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = localizeCardSubtitle(item.subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onBackgroundVariant,
                // Two lines held even when the credit is short, so every card
                // in the row stands the same height.
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val TILE_SPACING = 12.dp
private val TILE_CORNER = 12.dp
private val WIDE_GRID_WIDTH = 600.dp

/** Small enough that stretching it to a tile leaves only colour. */
private const val TILE_BLUR_ART_PX = 48
private val TILE_BLUR_RADIUS = 24.dp

/** How far past the tile the blurred art is drawn, so its soft rim is clipped off. */
private const val TILE_BLUR_OVERSCAN = 1.5f
private const val TILE_VEIL_ALPHA = 0.18f

/** The mark's width against the tile's. */
private const val TILE_LOGO_FRACTION = 0.42f

/** ic_logo's viewport, 730 x 484 — see HomeTitle. */
private const val LOGO_ASPECT = 730f / 484f

/** One fixed width on every screen; about one and a half Home cards. */
private val FEATURE_CARD_WIDTH = 240.dp
private val FEATURE_CORNER = 14.dp
