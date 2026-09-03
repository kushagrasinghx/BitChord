package com.music.bitchord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.R
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.LibrarySort
import com.music.bitchord.download.Downloads
import com.music.bitchord.download.SavedCollection
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.components.LIBRARY_GRID_SPACING
import com.music.bitchord.ui.components.MessageState
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.PullToRefresh
import com.music.bitchord.ui.components.SHELF_CARD_WIDTH
import com.music.bitchord.ui.components.libraryGrid
import com.music.bitchord.ui.components.librarySkeleton
import com.music.bitchord.ui.player.MeshGradientBackground
import com.music.bitchord.ui.player.rememberArtworkColors
import com.music.bitchord.ui.replay.ReplayHeroCard
import java.util.Locale

/**
 * The signed-in library: the saved collections, as shelves of cards.
 *
 * Deliberately only the collections. This page used to end with two runs of
 * track rows — "Liked Music" and "Songs" — which are two overlapping answers
 * to the same question and read as one list that couldn't make up its mind: a
 * track that stopped being liked didn't leave the page, it moved down it, into
 * a section most people had taken for more of the same. Liked Music is a
 * playlist, and it is reached the way every other playlist here is, by opening
 * its card.
 *
 * The liked list is still fetched — it is what the rest of the app reads a
 * track's rating off (see MainViewModel's `likeStatuses`); it just isn't a
 * second place to browse it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    signedIn: Boolean,
    state: UiState<LibraryPage>,
    listState: LazyListState,
    onShelfItemClick: (ShelfItem) -> Unit,
    onShelfItemLongPress: (ShelfItem) -> Unit,
    onNewPlaylist: () -> Unit,
    /**
     * A shelf's "Show all" — every shelf's row here stops at five cards (see
     * [LibraryGridShelf]), so this is the only way to reach whatever didn't
     * fit.
     */
    onShowAll: (HomeShelf) -> Unit,
    /**
     * The Replay's leading card — minutes listened — or null before anything has
     * been played.
     *
     * Not drawn as a card here. This page is a list of places to go, and a card
     * is an object to look at; one sitting at the top of it read as the Replay
     * page's opening reprinted on a page about playlists and downloads. What the
     * card is used for instead is its *numbers* and its *artwork*: the button
     * below says what is behind it, and is painted in the colours of the record
     * that year was mostly spent on.
     */
    replayCard: ReplayHeroCard?,
    onOpenReplay: () -> Unit,
    onSignIn: () -> Unit,
    onRetry: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    pullState: PullToRefreshState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    /**
     * The playlists downloaded whole, as cards behind the two device folders.
     *
     * They belong on that shelf because they are the same promise everything
     * else on it makes — here, now, without a network. Nothing is truncated:
     * the shelf is a row that scrolls, so "all of them" costs nothing.
     *
     * Downloaded *albums* are deliberately not here. An album stamps its name
     * onto each of its tracks, so the Downloads folder's Albums tab groups it
     * back up on its own and a card here would be a second door onto the same
     * list. A playlist has no tag anything can derive it from — its tracks are
     * off forty different releases — so this is the only place it can be reached
     * without going through that folder.
     */
    downloadedPlaylists: List<SavedCollection> = emptyList(),
) {
    val pinnedPlaylists by AppSettings.pinnedPlaylists.collectAsStateWithLifecycle()
    val onDevice = stringResource(R.string.on_device)
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
            item {
                Text(
                    text = stringResource(R.string.library),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
                )
            }
            // Drawn whether or not anything has been played: with nothing behind
            // it the page still has to say the feature exists, or the only way
            // to discover it is to have already used it.
            item(key = "replay") { ReplayBanner(replayCard, onOpenReplay) }
            item(key = "shelf:$onDevice") {
                val onDeviceShelf = HomeShelf(
                    title = onDevice,
                    items = listOf(
                        ShelfItem(
                            title = stringResource(R.string.downloads),
                            subtitle = stringResource(R.string.downloaded_songs),
                            thumbnailUrl = null,
                            videoId = null,
                            browseId = "local:downloads",
                        ),
                        ShelfItem(
                            title = stringResource(R.string.local_music),
                            subtitle = stringResource(R.string.audio_files_on_device),
                            thumbnailUrl = null,
                            videoId = null,
                            browseId = "local:all",
                        ),
                    ) + downloadedPlaylists.map { playlist ->
                        ShelfItem(
                            title = playlist.title,
                            // The credit the playlist was downloaded with,
                            // because this is also what the page it opens
                            // bills itself by — see `headerLines`, which
                            // reads the kind and the owner back out of it.
                            // Saying "Downloaded playlist" here instead would
                            // make that header read "Downloaded playlist" over
                            // "PLAYLIST • 12 SONGS", and the shelf this card
                            // is on already says where it lives.
                            subtitle = playlist.subtitle.ifBlank {
                                stringResource(R.string.downloaded_playlist)
                            },
                            thumbnailUrl = playlist.thumbnailUrl,
                            videoId = null,
                            browseId = Downloads.pageIdFor(playlist.id),
                        )
                    },
                )
                LibraryGridShelf(
                    shelf = onDeviceShelf,
                    onItemClick = onShelfItemClick,
                    onItemLongPress = onShelfItemLongPress,
                    onShowAll = { onShowAll(onDeviceShelf) },
                )
            }
            if (!signedIn) {
                item {
                    MessageState(
                        message = stringResource(R.string.library_sign_in_description),
                        actionLabel = stringResource(R.string.sign_in),
                        onAction = onSignIn,
                    )
                }
                return@LazyColumn
            }
            when (state) {
                is UiState.Loading -> librarySkeleton()
                is UiState.Error -> item {
                    MessageState(state.message, actionLabel = stringResource(R.string.retry), onAction = onRetry)
                }
                is UiState.Success -> {
                    // A fresh account has no Playlists shelf at all, and that
                    // is exactly the account most in need of the button that
                    // makes one — so the row is drawn either way, empty but
                    // for the tile that creates the first playlist.
                    val shelves = state.data.shelves
                    if (shelves.none { it.title == PLAYLISTS }) {
                        item(key = "shelf:$PLAYLISTS") {
                            val emptyPlaylists = HomeShelf(PLAYLISTS, emptyList())
                            PlaylistShelf(
                                shelf = emptyPlaylists,
                                onItemClick = onShelfItemClick,
                                onItemLongPress = onShelfItemLongPress,
                                onNewPlaylist = onNewPlaylist,
                                onShowAll = { onShowAll(emptyPlaylists) },
                            )
                        }
                    }
                    shelves.forEach { shelf ->
                        item(key = "shelf:${shelf.title}") {
                            if (shelf.title == PLAYLISTS) {
                                val pinnedFirst = shelf.pinnedFirst(pinnedPlaylists)
                                PlaylistShelf(
                                    shelf = pinnedFirst,
                                    onItemClick = onShelfItemClick,
                                    onItemLongPress = onShelfItemLongPress,
                                    onNewPlaylist = onNewPlaylist,
                                    onShowAll = { onShowAll(pinnedFirst) },
                                    pinnedPlaylists = pinnedPlaylists,
                                )
                            } else {
                                LibraryGridShelf(
                                    shelf = shelf,
                                    onItemClick = onShelfItemClick,
                                    onItemLongPress = onShelfItemLongPress,
                                    onShowAll = { onShowAll(shelf) },
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
 * The way in to Replay, at the top of the page.
 *
 * On the Library tab rather than a tab of its own because that is what Replay
 * is — a view of what is already yours, alongside the playlists and the
 * downloads. A fifth tab would give a page most people open a handful of times
 * a year the same standing as Search.
 *
 * ## Why it is painted the way the cards are
 *
 * The mesh is the same one the Replay cards and the player's backdrop run —
 * sampled from the artwork of the record the period was mostly spent on, and
 * drifting rather than settling (see [MeshGradientBackground]'s `continuous`).
 * A fixed brand gradient here looked like a promo banner, which is the one thing
 * this must not be: it advertises the user's own listening, so it should be lit
 * by the user's own listening, and it should not look like anything else on the
 * page. With nothing played yet the mesh falls back to its stock colours, which
 * is a perfectly good button and still not a red rectangle.
 *
 * A single wide strip rather than a shelf of cards: there is exactly one of it,
 * and a carousel with one item in it always reads as a carousel that failed to
 * load the rest.
 */
@Composable
private fun ReplayBanner(card: ReplayHeroCard?, onClick: () -> Unit) {
    val palette = rememberArtworkColors(card?.artworkUrl)
    Box(
        Modifier
            .padding(horizontal = PAGE_GUTTER, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
    ) {
        // Behind the row and sized to it rather than given a height of its own,
        // so the strip is as tall as its two lines of type and no taller.
        Box(Modifier.matchParentSize()) {
            MeshGradientBackground(
                palette = palette,
                trackKey = card?.artworkUrl ?: "replay",
                continuous = true,
                // A short wide strip: at the backdrop's own radius the four
                // colours blur into one wash before they reach its ends.
                blurRadius = 28.dp,
            )
        }
        // The mesh carries a vertical scrim of its own, pitched for a full
        // screen where it has hundreds of dp to fade across; over a strip this
        // short it lands as a flat darkening of the whole thing. So this one is
        // kept deliberately light and runs the other way — just enough under the
        // words on the left, and almost nothing over the colour on the right,
        // which is the half anyone actually sees as a gradient.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.34f),
                            Color.Black.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.your_replay),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                Text(
                    // The numbers when there are any, because "5,231 minutes" is
                    // a reason to tap and a description of the feature is not.
                    text = card?.let { "${it.value} ${it.label.lowercase(Locale.ROOT)} · ${it.detail}" }
                        ?: stringResource(R.string.replay_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = BitChordIcons.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * The one shelf on this page that can be written to: it leads with the tile
 * that creates a playlist, and holding a card gets rename and delete on top of
 * the queue actions every other shelf's menu offers.
 */
@Composable
private fun PlaylistShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: (ShelfItem) -> Unit,
    onNewPlaylist: () -> Unit,
    onShowAll: () -> Unit,
    pinnedPlaylists: List<String> = emptyList(),
) {
    LibraryGridShelf(
        shelf = shelf,
        onItemClick = onItemClick,
        onItemLongPress = onItemLongPress,
        onShowAll = onShowAll,
        pinnedPlaylists = pinnedPlaylists,
        leadingCard = {
            NewShelfCard(
                icon = BitChordIcons.Plus,
                label = stringResource(R.string.new_playlist),
                subtitle = stringResource(R.string.saved_to_youtube_music),
                onClick = onNewPlaylist,
            )
        },
    )
}

/** A Library shelf's preview row never swipes past this many cards. */
private const val LIBRARY_ROW_MAX_ITEMS = 5

/**
 * A Library shelf: a sideways-scrolling row of [SHELF_CARD_WIDTH] cards, the
 * same as every other shelf, but stopped at [LIBRARY_ROW_MAX_ITEMS] rather
 * than left to run the shelf's whole length — with a "Show all" beside the
 * title whenever there's more than that, opening the rest as a
 * vertically-scrolling grid instead. See [LibraryGridPage].
 *
 * [leadingCard], if given, occupies the first slot and counts against that
 * cap — see [PlaylistShelf].
 */
@Composable
internal fun LibraryGridShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: (ShelfItem) -> Unit,
    onShowAll: () -> Unit,
    leadingCard: (@Composable () -> Unit)? = null,
    pinnedPlaylists: List<String> = emptyList(),
) {
    val leadingCount = if (leadingCard != null) 1 else 0
    val visibleItems = shelf.items.take((LIBRARY_ROW_MAX_ITEMS - leadingCount).coerceAtLeast(0))
    Column(Modifier.padding(bottom = 26.dp)) {
        SectionHeader(
            title = shelf.title,
            subtitle = shelf.subtitle,
            onShowAll = onShowAll.takeIf { shelf.items.size + leadingCount > LIBRARY_ROW_MAX_ITEMS },
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(LIBRARY_GRID_SPACING),
        ) {
            leadingCard?.let { card -> item(key = "leading") { card() } }
            items(visibleItems) { item ->
                ShelfCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongPress = { onItemLongPress(item) },
                    isPinned = item.browseId != null && item.browseId in pinnedPlaylists,
                )
            }
        }
    }
}

/**
 * Everything a Library shelf's "Show all" opens onto — the same cards, at the
 * same [libraryGrid] width, run down the screen instead of stopping at one row.
 */
@Composable
fun LibraryGridPage(
    shelf: HomeShelf,
    gridState: LazyGridState,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: (ShelfItem) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onNewPlaylist: (() -> Unit)? = null,
) {
    // Re-read live rather than trusting [shelf] to already be sorted: this page
    // is opened from a snapshot (see `libraryShowAll` in MainActivity), and a
    // pin toggled from this page's own long-press menu must move the card
    // immediately rather than waiting for the row underneath to be revisited.
    val pinnedPlaylists by AppSettings.pinnedPlaylists.collectAsStateWithLifecycle()
    val librarySort by AppSettings.librarySort.collectAsStateWithLifecycle()
    // Pinning wins over the default order, but an explicit sort is a stronger,
    // more deliberate signal than a pin and is left to reorder the whole grid,
    // pinned cards included.
    val sortedShelf = shelf.pinnedFirst(pinnedPlaylists).sortedForLibrary(librarySort)
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
            if (onNewPlaylist != null) {
                item(key = "leading") {
                    NewShelfCard(
                        icon = BitChordIcons.Plus,
                        label = stringResource(R.string.new_playlist),
                        subtitle = stringResource(R.string.saved_to_youtube_music),
                        onClick = onNewPlaylist,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(sortedShelf.items, key = { it.browseId ?: it.title }) { item ->
                ShelfCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongPress = { onItemLongPress(item) },
                    modifier = Modifier.fillMaxWidth(),
                    isPinned = item.browseId != null && item.browseId in pinnedPlaylists,
                )
            }
        }
    }
}

/**
 * Moves whichever of this shelf's cards are in [pinned] to the front, in the
 * order they were pinned, leaving everything else in its existing order behind
 * them.
 *
 * A no-op on any shelf that isn't Playlists: [pinned] only ever holds playlist
 * browse ids, so an album or artist shelf never has a card that matches.
 */
private fun HomeShelf.pinnedFirst(pinned: List<String>): HomeShelf {
    if (pinned.isEmpty()) return this
    val byId = items.filter { it.browseId != null }.associateBy { it.browseId }
    val pinnedItems = pinned.mapNotNull { byId[it] }
    if (pinnedItems.isEmpty()) return this
    val pinnedSet = pinnedItems.toSet()
    return copy(items = pinnedItems + items.filter { it !in pinnedSet })
}

/**
 * A card's title is all a Library shelf carries, so [LibrarySort.DEFAULT] is
 * the only option that isn't alphabetical — everything else sorts on it.
 */
private fun HomeShelf.sortedForLibrary(sort: LibrarySort): HomeShelf = when (sort) {
    LibrarySort.DEFAULT -> this
    LibrarySort.TITLE_ASC -> copy(items = items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }))
    LibrarySort.TITLE_DESC -> copy(
        items = items.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title }),
    )
}

/** The library feed whose cards are the account's own — see [PlaylistShelf]. */
private const val PLAYLISTS = YtMusicRepository.PLAYLISTS_SHELF
