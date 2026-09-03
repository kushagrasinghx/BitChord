package com.music.bitchord.ui.screens

import com.music.bitchord.R

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.canvas.CanvasRepository
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.HEADER_ART_PX
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.ArtworkWash
import com.music.bitchord.ui.components.DownloadedBadge
import com.music.bitchord.ui.components.ExplicitSongTitle
import com.music.bitchord.ui.components.MessageState
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.ROW_DIVIDER_INSET
import com.music.bitchord.ui.components.SHELF_CARD_WIDTH
import com.music.bitchord.ui.components.SongRow
import com.music.bitchord.ui.components.thumbnailBorder
import com.music.bitchord.ui.components.detailSkeleton
import com.music.bitchord.ui.components.topBarContentPadding
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.player.CanvasArtworkPlayer
import com.music.bitchord.ui.theme.ArtworkPalette
import com.music.bitchord.ui.components.optimizedHazeEffect
import com.music.bitchord.ui.theme.rememberArtworkPalette
import kotlin.math.roundToInt
import java.util.Locale

/**
 * Ordering for the track list on an album or playlist page — the same idea as
 * the Downloads folder's sort, but without a date: a catalogue row carries
 * none, so [DEFAULT] (the release's own running order) is the only option
 * that isn't alphabetical.
 */
enum class SongSort {
    DEFAULT,
    TITLE_ASC,
    TITLE_DESC,
}

private const val MAX_ARTIST_SONGS = 20
private const val SONGS_PER_COLUMN = 4

/** The artist photo, very slightly taller than it is wide. */
private const val ARTIST_PHOTO_RATIO = 0.95f

/** A release's sleeve, given a little more height than the artist photo. */
private const val SLEEVE_RATIO = 0.92f

/** The sleeve on a release page, as a fraction of the page width. */
private const val SLEEVE_FRACTION = 0.80f

private val SLEEVE_SHAPE = RoundedCornerShape(12.dp)
private val PILL_SHAPE = RoundedCornerShape(12.dp)

/**
 * Where the search field sits once it is open — directly under the header,
 * which is item zero. The one place that has to know, so that opening the
 * search can carry the page up to it.
 */
private const val SEARCH_ITEM_INDEX = 1

/** The inset the header text and the action pills share. */
private val HEADER_GUTTER = PAGE_GUTTER + 14.dp

/** Extra breathing room for the editorial copy on album and artist pages. */
private val ABOUT_GUTTER = PAGE_GUTTER + 12.dp

/**
 * How far past the foot of the artwork the title block is allowed to hang.
 *
 * Sat flush to the bottom of the picture it lands wherever the picture happens
 * to be busy, and on a sleeve with anything going on down there the title reads
 * as part of the artwork rather than as a caption to it. Dropped clear, it sits
 * on the blurred colour instead, which has nothing in it to compete.
 */
private val HEADER_DROP = 44.dp

/**
 * Album / artist / playlist page. Rendered inside the main content area
 * rather than as a sheet, so the tab bar and mini player stay visible.
 *
 * The page paints itself in the artwork's own colours — a tint behind
 * everything, the artwork itself across the top of it, and an accent taken off
 * the sleeve for the credit line and the Play/Shuffle pair. See
 * [rememberArtworkPalette] for how those are derived and kept legible.
 *
 * It is built in three layers rather than the obvious one, and the order is the
 * whole trick:
 *
 *  1. [PageBackground] — the wash and the artwork, and nothing you can read.
 *  2. [MergeBand] — one pane of glass laid across the join, blurring layer 1.
 *  3. The list — titles, buttons and rows, drawn over the glass and so sharp.
 *
 * Blurring only the artwork leaves the artwork and the page as two surfaces
 * that have been made to *resemble* each other, and the eye finds that edge
 * every time. A single blur that samples across the join has no edge to find:
 * the picture, the colour under it and the colour under the song rows are all
 * one smear of the same glass. It is the same thing [TopFadeBlur] does to the
 * head of the screen, pointed at the middle of this one.
 */
@Composable
fun DetailScreen(
    page: DetailPage,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onSectionItemClick: (ShelfItem) -> Unit,
    onArtistClick: (String, String) -> Unit,
    onAddSuggested: (Song) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    /**
     * Holding one of the album cards on an artist page — the same menu the
     * shelves on every other tab open, so a release can be queued from
     * wherever it is seen rather than only from its own page.
     */
    onSectionItemLongPress: ((ShelfItem) -> Unit)? = null,
    /**
     * The header's overflow: everything this page can do to its whole track
     * list that isn't already one of the buttons beside it — downloading it
     * among them, see [com.music.bitchord.ui.components.BrowseActionsSheet].
     * Null on the pages with no list to act on.
     */
    onMore: ((List<Song>) -> Unit)? = null,
    /**
     * Saves this release to the account's library, or takes it out —
     * [DetailPage.library] says which way round. Null hides the control
     * entirely, which is the answer for a guest and for the pages YouTube never
     * offers to save; there is nothing to show a signed-out user here that
     * wouldn't just be refused.
     */
    onToggleLibrary: (() -> Unit)? = null,
    /**
     * How the track list is ordered — the release's own running order by
     * default, or alphabetical. Owned by the caller rather than this page
     * because the control for it lives in the top bar, alongside the account
     * photo, not in this header.
     */
    songSort: SongSort = SongSort.DEFAULT,
) {
    val rawSongs = (page.songs as? UiState.Success)?.data.orEmpty()
    val songs = remember(rawSongs, songSort) { rawSongs.sortedForDetail(songSort) }
    // What a numbered row shows regardless of [songSort] — an album's track
    // numbers are the sleeve's own and must not relabel themselves to match
    // wherever a sort put the row.
    val originalTrackNumbers = remember(rawSongs) {
        rawSongs.withIndex().associate { (i, s) -> s.videoId to i + 1 }
    }
    val isArtist = page.type == BrowseType.ARTIST
    val palette = rememberArtworkPalette(page.thumbnailUrl)

    // Narrowing the running order in place — the release equivalent of the
    // filter box on the Local Music tab, and the one thing a long track list
    // needs that scrolling can't give it. Off by default and reset with the
    // page: a filter left on an album that was closed and reopened would be a
    // page that appears to have lost most of its tracks.
    var searching by rememberSaveable(page.browseId) { mutableStateOf(false) }
    var query by rememberSaveable(page.browseId) { mutableStateOf("") }
    // Whether the field still owes the keyboard an appearance. Held here rather
    // than in the field, which is a row in a lazy list: scrolled out of sight it
    // is disposed, and a field that asks for focus every time it is composed
    // would throw the keyboard back up each time it scrolled into view.
    var focusSearch by remember(page.browseId) { mutableStateOf(false) }
    val closeSearch = {
        searching = false
        query = ""
    }
    // Back closes the search first — this handler is registered after the one
    // that pops the page, so it is the one that answers while it's enabled.
    BackHandler(enabled = searching) { closeSearch() }

    // Each surviving row still knows where it sat in the full running order, so
    // an album's track numbers stay the album's rather than becoming positions
    // in the filtered list.
    val matches = remember(songs, query) { songs.matching(query) }
    // What a tap plays: for playlists the full list (preserving context), so
    // searching and tapping still stays inside the playlist. For albums / other
    // browse types the filtered set is used — playing an entire album from a
    // single search hit would queue tracks the user never asked for.
    val queue = remember(songs, matches, page.type) {
        if (page.type == BrowseType.PLAYLIST) songs else matches.map { it.value }
    }
    val suggested = remember(page.suggestedSongs, query) {
        page.suggestedSongs.matching(query).map { it.value }
    }

    // What marks a row as already downloaded, tinted from the sleeve like the
    // rest of the page. Null on any page that is itself a reading of this
    // device — the Downloads folder, one downloaded playlist — where every row
    // qualifies and the badge would be decoration rather than information.
    val downloadedTint = palette.accent.takeUnless { page.browseId.startsWith("local:") }

    // Animated cover art on the header, the same feature the player has.
    // Albums only: a playlist's artwork is a collage and an artist page's is a
    // photograph, and neither is something a label publishes a canvas for.
    val canvasEnabled by AppSettings.animatedCanvas.collectAsStateWithLifecycle()
    // The credit line the header shows is the artist as far as the catalogue
    // services are concerned. A browse card's subtitle sometimes omits it, in
    // which case the tracks themselves know who it is.
    val credit = page.headerLines(songs.size).first.ifBlank { songs.firstOrNull()?.artist.orEmpty() }
    var canvas by remember(page.browseId) { mutableStateOf<CanvasArtwork?>(null) }
    LaunchedEffect(page.browseId, page.title, credit, canvasEnabled) {
        if (!canvasEnabled || page.type != BrowseType.ALBUM) {
            canvas = null
            return@LaunchedEffect
        }
        // As on the player: the credit fills in once the tracks load, so this
        // can run twice. Keep a clip that is already playing if the second
        // pass comes back empty.
        canvas = CanvasRepository.canvasForAlbum(page.title, credit) ?: canvas
    }

    val pageHaze = remember { HazeState() }

    // Opening the search carries the page up to it, so the field lands just
    // clear of the frosted bar with the tracks under it rather than at the foot
    // of a screen still filled with artwork. Done as an effect rather than in
    // the tap, so the row it scrolls to is already in the list by the time it
    // runs.
    val searchStop = with(LocalDensity.current) { topBarContentPadding().roundToPx() }
    LaunchedEffect(searching) {
        if (searching) listState.animateScrollToItem(SEARCH_ITEM_INDEX, -searchStop)
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        // The artwork is drawn behind the list rather than in it, so both need
        // to agree on its height without being able to ask each other. The
        // width is the page's, so the ratio decides it and both can work it out
        // alone.
        //
        // Measured rather than read off the window, because the two are not the
        // same number everywhere: on a tablet the page is the column left over
        // once the player has its pane, and a height derived from the whole
        // window there is a sleeve half again as tall as it is wide.
        val artHeight = maxWidth / if (isArtist) ARTIST_PHOTO_RATIO else SLEEVE_RATIO

        PageBackground(
            page = page,
            palette = palette,
            canvas = canvas,
            artHeight = artHeight,
            listState = listState,
            hazeState = pageHaze,
            modifier = Modifier.matchParentSize(),
        )

        MergeBand(
            palette = palette,
            artHeight = artHeight,
            listState = listState,
            hazeState = pageHaze,
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Both artist photos and release artwork run edge-to-edge up under
            // the glass bar — the image is the top of the page, not a card on it.
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
        ) {
            item(key = "header") {
                if (isArtist) {
                    ArtistHeader(page = page, palette = palette, artHeight = artHeight)
                } else {
                    ReleaseHeader(
                        page = page,
                        palette = palette,
                        artHeight = artHeight,
                        trackCount = songs.size,
                        songs = songs,
                        onPlay = { onSongClick(songs, 0) },
                        onShuffle = { onShuffle(songs) },
                        searching = searching,
                        onSearch = {
                            if (searching) {
                                closeSearch()
                            } else {
                                searching = true
                                focusSearch = true
                            }
                        },
                        onMore = onMore,
                        onArtistClick = onArtistClick,
                        onToggleLibrary = onToggleLibrary,
                    )
                }
            }

            if (isArtist && (page.subscriberCountText != null || page.monthlyListenerCount != null)) {
                item(key = "artist-stats") {
                    ArtistStatsRow(
                        subscriberCountText = page.subscriberCountText,
                        monthlyListenerCount = page.monthlyListenerCount,
                        palette = palette,
                    )
                }
            }

            if (searching) {
                item(key = "search") {
                    DetailSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        onClose = closeSearch,
                        autoFocus = focusSearch,
                        onFocused = { focusSearch = false },
                        palette = palette,
                        type = page.type,
                    )
                }
            }

            if (songs.isNotEmpty() && isArtist) {
                item(key = "actions") {
                    ActionRow(
                        palette = palette,
                        onPlay = { onSongClick(songs, 0) },
                        onShuffle = { onShuffle(songs) },
                        // Halved when an About section follows directly — see
                        // [AboutSection]'s own top inset, which makes up the
                        // rest of that shorter gap.
                        bottomSpace = if (page.description.isNullOrBlank()) 22.dp else 11.dp,
                    )
                }
            }

            // YouTube's own editorial blurb — an album or an artist only, per
            // [DetailPage.description]. A playlist never carries one, and the
            // section is skipped for it even on the rare response that does.
            if (!page.description.isNullOrBlank() &&
                (page.type == BrowseType.ALBUM || isArtist)
            ) {
                item(key = "about") {
                    AboutSection(
                        title = stringResource(
                            if (isArtist) R.string.about_artist else R.string.about_album,
                        ),
                        text = page.description,
                        palette = palette,
                    )
                }
            }

            when (val state = page.songs) {
                is UiState.Loading -> detailSkeleton(isArtist)
                is UiState.Error -> item { MessageState(state.message) }
                is UiState.Success -> if (isArtist) {
                    // An artist's full song list would bury the album shelves, so
                    // it pages sideways four at a time and stops at twenty.
                    item {
                        val top = state.data.take(MAX_ARTIST_SONGS)
                        SectionHeading(stringResource(R.string.top_songs), palette)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(top.chunked(SONGS_PER_COLUMN)) { column ->
                                Column(Modifier.fillParentMaxWidth(0.88f)) {
                                    column.forEach { song ->
                                        CompactSongRow(
                                            song = song,
                                            palette = palette,
                                            onClick = { onSongClick(top, top.indexOf(song)) },
                                            onLongPress = { onSongLongPress(song) },
                                            downloadedTint = downloadedTint,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Every row on an album carries the same sleeve, which is
                    // already the largest thing on the page — Apple Music
                    // numbers those rows instead, and so does this.
                    val numbered = page.type == BrowseType.ALBUM
                    if (matches.isEmpty() && state.data.isNotEmpty()) {
                        item(key = "no-matches") {
                            MessageState(stringResource(R.string.nothing_matches, query))
                        }
                    }
                    itemsIndexed(matches) { position, entry ->
                        val song = entry.value
                        val isCurrent = song.isSameQueueEntry(currentSong)
                        SongRow(
                            song = if (numbered) {
                                song
                            } else {
                                song.copy(thumbnailUrl = song.thumbnailUrl ?: page.thumbnailUrl)
                            },
                            onClick = {
                                val startIdx = if (page.type == BrowseType.PLAYLIST) entry.index else position
                                onSongClick(queue, startIdx)
                            },
                            onLongPress = { onSongLongPress(song) },
                            onSwipeToQueue = { onSongSwipe(song) },
                            rowBackground = Color.Transparent,
                            // The track's place on the release, not its place in
                            // what the filter — or a sort — left standing.
                            trackNumber = originalTrackNumbers[song.videoId].takeIf { numbered },
                            subtitleColor = palette.onBackgroundVariant,
                            downloadedTint = downloadedTint,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && isPlaying,
                            activeTint = palette.accent,
                        )
                        if (position < matches.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                                thickness = 0.5.dp,
                                color = palette.divider,
                            )
                        }
                    }
                }
            }

            // Tracks YouTube offers to round the playlist out, never folded
            // into the list above — see [DetailPage.suggestedSongs].
            if (suggested.isNotEmpty()) {
                item(key = "suggested-heading") {
                    SectionHeading(stringResource(R.string.suggested), palette)
                }
                itemsIndexed(
                    suggested,
                    key = { _, song -> "suggested-${song.videoId}" },
                ) { index, song ->
                    SuggestedSongRow(
                        song = song,
                        palette = palette,
                        onClick = { onSongClick(suggested, index) },
                        onLongPress = { onSongLongPress(song) },
                        onAdd = { onAddSuggested(song) },
                        downloadedTint = downloadedTint,
                    )
                    if (index < suggested.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                            thickness = 0.5.dp,
                            color = palette.divider,
                        )
                    }
                }
            }

            // Albums / Singles & EPs carousels (artist pages).
            items(page.sections) { shelf ->
                Column(Modifier.padding(top = 22.dp)) {
                    SectionHeading(shelf.title, palette)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(shelf.items) { item ->
                            SectionCard(
                                item = item,
                                palette = palette,
                                onClick = { onSectionItemClick(item) },
                                onLongPress = onSectionItemLongPress?.let { { it(item) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Playlist entries have their own identity because the same recording can be
 * added more than once. Fall back to media identity for albums and local rows,
 * which do not carry a set-video-id.
 */
private fun Song.isSameQueueEntry(current: Song?): Boolean {
    current ?: return false
    if (setVideoId != null && current.setVideoId != null) {
        return setVideoId == current.setVideoId
    }
    if (localUri != null && current.localUri != null) return localUri == current.localUri
    return videoId == current.videoId
}

/**
 * An album or playlist: the title, credit, meta and action buttons that sit
 * over the foot of the artwork.
 *
 * The artwork itself is not here — [PageBackground] draws it, so that
 * [MergeBand] can blur it without blurring any of this. What this item holds in
 * its place is a spacer of exactly the picture's height, which is what keeps
 * the two in step: the list reserves the room, the background fills it.
 */
@Composable
private fun ReleaseHeader(
    page: DetailPage,
    palette: ArtworkPalette,
    artHeight: Dp,
    trackCount: Int,
    songs: List<Song>,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    searching: Boolean,
    onSearch: () -> Unit,
    onMore: ((List<Song>) -> Unit)?,
    onArtistClick: (String, String) -> Unit,
    onToggleLibrary: (() -> Unit)?,
) {
    val (credit, meta) = page.headerLines(trackCount)
    // Every row on a release carries the same credit — see [pageCredit] — so
    // the first one speaks for the whole page, the same source the rows'
    // own long-press "Open artist" already reads from.
    val artist = songs.firstOrNull()

    // The outer Box just needs to be as tall as its content — we don't force
    // an aspect ratio here so the action buttons can extend below the artwork.
    Box(Modifier.fillMaxWidth()) {

        Spacer(Modifier.fillMaxWidth().height(artHeight + HEADER_DROP))

        // Text + action row stacked, pinned to the bottom of the Box.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineMedium,
                color = palette.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = HEADER_GUTTER),
            )
            // Artist / credit line
            if (credit.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = credit,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.accent,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = HEADER_GUTTER)
                        .let { m ->
                            val id = artist?.artistId
                            if (id == null) {
                                m
                            } else {
                                m.clip(RoundedCornerShape(6.dp))
                                    .clickable { onArtistClick(id, artist.artist) }
                            }
                        },
                )
            }
            // Metadata (kind • year • count)
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.7.sp),
                    color = palette.onBackgroundVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = HEADER_GUTTER),
                )
            }

            // Action buttons — live inside the header so there is zero gap
            // between the cover zone and the first song row.
            if (songs.isNotEmpty()) {
                // Only where YouTube said the release can be saved and the
                // caller is willing to take the write — see [onToggleLibrary].
                val library = page.library?.takeIf { onToggleLibrary != null }
                // Four circles and the pill is as much as this row can carry,
                // and on a 360dp screen it only carries it by giving something
                // up: the pill sheds padding first, being the widest thing here,
                // and the circles come down 4dp after that. The alternative is a
                // row that runs off the edge of the screen.
                val circles = listOfNotNull(library, onMore).size + 2 // + Shuffle, Search
                val full = circles >= 4
                val circleSize = if (full) 46.dp else 50.dp
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = HEADER_GUTTER),
                    horizontalArrangement = Arrangement.spacedBy(
                        if (full) 8.dp else 10.dp,
                        Alignment.CenterHorizontally,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (library != null) {
                        CircleIconButton(
                            // A tick, not a filled-in plus: the pair reads as
                            // "not yet / done", which is what the state is.
                            icon = if (library.saved) BitChordIcons.Check else BitChordIcons.Plus,
                            contentDescription = if (library.saved) {
                                stringResource(R.string.remove_from_library)
                            } else {
                                stringResource(R.string.add_to_library)
                            },
                            palette = palette,
                            onClick = { onToggleLibrary?.invoke() },
                            haptic = if (library.saved) Haptic.ToggleOff else Haptic.ToggleOn,
                            size = circleSize,
                        )
                    }
                    CircleIconButton(
                        icon = BitChordIcons.Shuffle,
                        contentDescription = stringResource(R.string.shuffle),
                        palette = palette,
                        onClick = onShuffle,
                        haptic = Haptic.Resume,
                        size = circleSize,
                    )
                    PlayPill(
                        palette = palette,
                        onClick = onPlay,
                        horizontalPadding = when (circles) {
                            1, 2 -> 32.dp
                            3 -> 24.dp
                            else -> 14.dp
                        },
                    )
                    // Where the download circle used to be. Downloading a
                    // release is a thing done once and then not thought about;
                    // finding a track on a long playlist is a thing done while
                    // reading the page, so it is the one that earns a button and
                    // the download moved to the overflow beside it.
                    CircleIconButton(
                        icon = if (searching) Icons.Rounded.Close else BitChordIcons.Search,
                        contentDescription = stringResource(
                            if (searching) R.string.close_search else R.string.search_this_list,
                        ),
                        palette = palette,
                        onClick = onSearch,
                        size = circleSize,
                    )
                    onMore?.let { more ->
                        CircleIconButton(
                            icon = Icons.Rounded.MoreHoriz,
                            contentDescription = stringResource(R.string.more),
                            palette = palette,
                            onClick = { more(songs) },
                            size = circleSize,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The filter box, shown under the header while the search circle is lit.
 *
 * Live rather than submit-on-enter, and for the same reason the Local Music
 * one is (see `LocalSearchField`): the list it narrows is already in memory, so
 * there is nothing for a submit action to wait for.
 *
 * Glass rather than a filled field — it is one of the header's controls that
 * happens to be typed into, and it sits close enough to the circles that a
 * Material text field beside them would read as a different app's furniture.
 */
@Composable
private fun DetailSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    autoFocus: Boolean,
    onFocused: () -> Unit,
    palette: ArtworkPalette,
    type: BrowseType,
) {
    // Opened by a tap on a button, which is as clear a statement of intent as
    // the keyboard is going to get — so it comes up with the field rather than
    // making the tap land twice. Once only: see [autoFocus]'s owner.
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focus.requestFocus()
            onFocused()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HEADER_GUTTER)
            .padding(bottom = 10.dp)
            .height(46.dp)
            .clip(PILL_SHAPE)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), PILL_SHAPE)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = BitChordIcons.Search,
            contentDescription = null,
            tint = palette.onBackgroundVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_this_list),
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.onBackgroundVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.onBackground),
                cursorBrush = SolidColor(palette.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                // Clearing and closing are the same gesture: an empty filter
                // showing an unfiltered list is a row of furniture with nothing
                // left to do.
                .clickable { if (query.isEmpty()) onClose() else onQueryChange("") },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(
                    if (query.isEmpty()) R.string.close_search else R.string.clear_search,
                ),
                tint = palette.onBackgroundVariant,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

private fun List<Song>.sortedForDetail(sort: SongSort): List<Song> = when (sort) {
    SongSort.DEFAULT -> this
    SongSort.TITLE_ASC -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    SongSort.TITLE_DESC -> sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
}

/**
 * The rows a typed query leaves standing, each still carrying its place in the
 * full list — see the track numbers on an album, which are the release's own
 * and not positions in whatever the filter left.
 */
private fun List<Song>.matching(query: String): List<IndexedValue<Song>> {
    val all = withIndex().toList()
    if (query.isBlank()) return all
    return all.filter { (_, song) ->
        song.title.contains(query, ignoreCase = true) ||
            song.artist.contains(query, ignoreCase = true) ||
            song.albumName?.contains(query, ignoreCase = true) == true
    }
}

/**
 * An artist: their name across the foot of the photo [PageBackground] is
 * drawing behind this. See [ReleaseHeader] for why the picture isn't here.
 */
@Composable
private fun ArtistHeader(page: DetailPage, palette: ArtworkPalette, artHeight: Dp) {
    Box(Modifier.fillMaxWidth()) {
        Spacer(Modifier.fillMaxWidth().height(artHeight + HEADER_DROP))
        Text(
            text = page.title,
            style = MaterialTheme.typography.displayLarge,
            color = palette.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // Bottom is half the top inset — the stats pills (or, absent
                // those, the action row) sit closer under the name than the
                // name sits under the artwork.
                .padding(start = HEADER_GUTTER, end = HEADER_GUTTER, top = 14.dp, bottom = 7.dp),
        )
    }
}

/**
 * Everything on a detail page that is colour rather than words: the page wash,
 * and the artwork sitting on top of it.
 *
 * This is the whole of what [MergeBand] blurs, and the reason it is a layer of
 * its own. The artwork used to live in the list's first item, which put it in
 * the same layer as the title and the buttons and the song rows — glass laid
 * over that would have smeared the text along with the picture. Split out, the
 * blur has the join to itself.
 *
 * It carries the artwork's scroll instead of being scrolled: the list owns the
 * gesture and reserves the room, and the picture is offset to follow whatever
 * the list did with item zero. Read in a layer block, so a scroll moves it
 * without recomposing anything.
 */
@Composable
private fun PageBackground(
    page: DetailPage,
    palette: ArtworkPalette,
    canvas: CanvasArtwork?,
    artHeight: Dp,
    listState: LazyListState,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clipToBounds()
            .hazeSource(hazeState),
    ) {
        ArtworkWash(palette = palette, modifier = Modifier.matchParentSize())

        Box(
            Modifier
                .fillMaxWidth()
                .height(artHeight)
                .offset { IntOffset(0, listState.headerTop(artHeight.toPx()).roundToInt()) },
        ) {
            AsyncImage(
                model = page.thumbnailUrl.artworkAt(HEADER_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .background(palette.elevated),
            )

            // Above the still art but below both gradients, so the scrim and
            // the wash that blend the header into the page still sit over it.
            // Always running: unlike the player's sleeve there is no transport
            // here to follow, and the page is only up while it's being read.
            canvas?.let { clip ->
                CanvasArtworkPlayer(
                    canvas = clip,
                    isPlaying = true,
                    modifier = Modifier.matchParentSize(),
                )
            }

            // Shade under the glass bar. Drawn in the page's own tint rather
            // than in black, so the back arrow — which is themed, not always
            // white — keeps its contrast in light mode as well as dark.
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.28f)
                    .background(
                        Brush.verticalGradient(
                            listOf(palette.background.copy(alpha = 0.55f), Color.Transparent),
                        ),
                    ),
            )

            // Settles the foot of the picture onto the colour the page is made
            // of, so the two sides of the join are already close before the
            // glass goes over them — a blur averages what it is given and
            // cannot invent agreement that isn't there. It matters most on a
            // monochrome sleeve, where the wash is the only thing with a hue.
            //
            // Inside this layer, deliberately: drawn above the glass it would
            // be a hard-edged rectangle of its own.
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1.00f to palette.wash.copy(alpha = 0.88f),
                        ),
                    ),
            )
        }
    }
}

/**
 * One pane of glass laid across the join, blurring [PageBackground] through it.
 *
 * Centred on the bottom edge of the artwork, so half of it is over the picture
 * and half over the page below — which is what makes it a merge rather than a
 * fade. A blur samples across its own footprint, so colour from the sleeve is
 * carried down past where the sleeve ends and the page's colour is carried up
 * into it, and the line that used to be there has nothing left to be a line
 * between.
 *
 * Its own two edges are the only ones left to hide, and the mask does that: the
 * band arrives from nothing and leaves to nothing over [MERGE_BAND]'s full
 * height, which is long enough that there is no moment where it starts.
 *
 * Sits between the background and the list, so the title, the buttons and the
 * song rows are drawn on top of it and stay sharp.
 */
@Composable
private fun MergeBand(
    palette: ArtworkPalette,
    artHeight: Dp,
    listState: LazyListState,
    hazeState: HazeState,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    // Asked for no dynamic blur, the page falls back to what the background
    // does on its own: the sleeve settling onto the wash it is drawn over.
    if (reduceDynamicBlur) return

    Box(
        Modifier
            .fillMaxWidth()
            .height(MERGE_BAND)
            // Placed rather than translated, which for this one matters a great
            // deal: haze records where it is when it is *placed*, and a
            // graphicsLayer moves content at draw time, long after. Translated,
            // the band went on believing it was at the top of the screen — so
            // it blurred the top of the screen and painted that down here,
            // which is a blur of the wrong thing and leaves the join intact.
            .offset {
                IntOffset(
                    x = 0,
                    y = (
                        listState.headerTop(artHeight.toPx()) +
                            artHeight.toPx() - MERGE_BAND.toPx() / 2f
                        ).roundToInt(),
                )
            }
            .optimizedHazeEffect(hazeState) {
                // Without this the band draws nothing at all.
                //
                // Haze defaults to only blurring sources *below* it, which it
                // decides with `area.zIndex < hazeZIndex` — where hazeZIndex
                // comes from the nearest enclosing source. This page sits
                // inside the app's own full-window source, so that value is
                // 0f; our source is nested inside the same one, so its zIndex
                // is 0f as well; and `0 < 0` is false. The page's own
                // background was being filtered out of its own effect, leaving
                // it with no areas to blur. The bottom fade behind the tab bar
                // escapes this only because it is drawn outside that source
                // and so has no zIndex to be compared against.
                //
                // [hazeState] is private to this page and holds exactly one
                // area, so there is nothing here to filter.
                canDrawArea = { true }
                blurRadius = MERGE_BLUR
                // Haze's film grain is uniform across the layer, so it would
                // show up at the ends as texture over content the mask has
                // otherwise left alone — exactly the edges it is hiding.
                noiseFactor = 0f
                // An empty list falls through to whatever style is in scope, so
                // "no tint" has to be said as a transparent one. The band is
                // here to move colour around, not to add any.
                tints = listOf(HazeTint(Color.Transparent))
                backgroundColor = palette.wash
                mask = Brush.verticalGradient(
                    0.00f to Color.Transparent,
                    0.50f to Color.Black,
                    1.00f to Color.Transparent,
                )
            },
    )
}

/**
 * Where the top of the artwork currently is.
 *
 * The list is the one being scrolled; the background only has to agree with it.
 * While the header is item zero and on screen, how far it has been scrolled off
 * the top is exactly the offset the picture behind it needs. Once it isn't,
 * there is nothing to agree with, and everything hanging off this parks two
 * artwork-heights up — far enough that no part of anything comes back down.
 */
private fun LazyListState.headerTop(artHeightPx: Float): Float =
    if (firstVisibleItemIndex == 0) -firstVisibleItemScrollOffset.toFloat() else -artHeightPx * 2f

/**
 * How tall the glass is — generous, because half of its run is spent arriving
 * and half leaving, and a band that reaches full strength quickly has an edge
 * again.
 */
private val MERGE_BAND = 320.dp

/**
 * Wide enough that nothing of the picture survives where the band is at full
 * strength — not softened detail, none. A blur that leaves shapes behind reads
 * as a blurred photograph, and a blurred photograph next to a flat colour is
 * still two surfaces.
 */
private val MERGE_BLUR = 100.dp

/** Shuffle • Play • Download — the Apple Music action row. */
@Composable
private fun ActionRow(
    palette: ArtworkPalette,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    bottomSpace: Dp = 22.dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HEADER_GUTTER),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Circular Shuffle button
        CircleIconButton(
            icon = BitChordIcons.Shuffle,
            contentDescription = stringResource(R.string.shuffle),
            palette = palette,
            onClick = onShuffle,
            haptic = Haptic.Resume,
        )

        PlayPill(
            palette = palette,
            onClick = onPlay,
        )
    }
    Spacer(Modifier.height(bottomSpace))
}

/**
 * The prominent, pill-shaped Play button that anchors the action row.
 * White-ish solid fill with the accent colour, like Apple Music's Play button.
 */
@Composable
private fun PlayPill(
    palette: ArtworkPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 32.dp,
) {
    // Resume rather than a flat tap: this button starts a queue, and the rising
    // pair says so.
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .height(50.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .clickable {
                haptics.play(Haptic.Resume)
                onClick()
            }
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = BitChordIcons.Play,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.play),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Small circular icon-only button — used for Shuffle and Download flanking the
 * Play pill. Translucent glassy fill, accent-coloured icon.
 */
@Composable
private fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    haptic: Haptic = Haptic.Tap,
    size: Dp = 50.dp,
) {
    val haptics = rememberHaptics()
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .clickable {
                haptics.play(haptic)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(size * 0.44f),
        )
    }
}

/** Track count and running time, the way a release page signs off. */
@Composable
private fun ReleaseFooter(songs: List<Song>, palette: ArtworkPalette) {
    Text(
        text = songs.playtimeSummary(),
        style = MaterialTheme.typography.labelMedium,
        color = palette.onBackgroundVariant,
        modifier = Modifier.padding(start = HEADER_GUTTER, end = HEADER_GUTTER, top = 18.dp),
    )
}

/** "1.2M subscribers" and "3.4M monthly listeners", off the artist header. */
@Composable
private fun ArtistStatsRow(
    subscriberCountText: String?,
    monthlyListenerCount: String?,
    palette: ArtworkPalette,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Top padding is left to the header's own bottom inset (7.dp).
            .padding(start = PAGE_GUTTER, end = PAGE_GUTTER, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        // YouTube's own count text already reads "1.2M subscribers" in full,
        // so only the number is kept and the label re-said in the app's own
        // words — the one way to fit both stats on one line on a narrow
        // screen without either wrapping into two.
        subscriberCountText?.let {
            StatChip(
                icon = Icons.Rounded.Person,
                text = stringResource(R.string.subscribers, it.substringBefore(' ')),
                palette = palette,
            )
        }
        monthlyListenerCount?.let {
            StatChip(
                icon = Icons.Rounded.GraphicEq,
                text = stringResource(R.string.monthly_listeners, it.substringBefore(' ')),
                palette = palette,
            )
        }
    }
}

@Composable
private fun StatChip(icon: ImageVector, text: String, palette: ArtworkPalette) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.onBackgroundVariant,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = palette.onBackgroundVariant,
        )
    }
}

/**
 * YouTube's own editorial note for a release or an artist, collapsed to a
 * few lines with a tap to read the rest — the same "About" block Apple
 * Music and YouTube Music itself show under the header.
 *
 * Whether there's anything to expand is only knowable once the text has
 * been laid out at the collapsed line count, so the "More" toggle is held
 * back until that measurement says the clipped text actually lost
 * something — otherwise a two-line bio would show a toggle with nothing
 * behind it to reveal.
 */
@Composable
private fun AboutSection(title: String, text: String, palette: ArtworkPalette) {
    var expanded by remember(text) { mutableStateOf(false) }
    var clipped by remember(text) { mutableStateOf(false) }
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = palette.onBackground,
            modifier = Modifier.padding(
                start = ABOUT_GUTTER, end = ABOUT_GUTTER, top = 2.dp, bottom = 6.dp,
            ),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = palette.onBackgroundVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result -> if (!expanded) clipped = result.hasVisualOverflow },
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = ABOUT_GUTTER)
                .let { m -> if (clipped || expanded) m.clickable { expanded = !expanded } else m },
        )
        if (clipped || expanded) {
            Text(
                text = stringResource(if (expanded) R.string.less else R.string.more),
                style = MaterialTheme.typography.labelLarge,
                color = palette.accent,
                modifier = Modifier
                    .padding(horizontal = ABOUT_GUTTER, vertical = 4.dp)
                    .clickable { expanded = !expanded },
            )
        }
    }
}

@Composable
private fun SectionHeading(title: String, palette: ArtworkPalette) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = palette.onBackground,
        modifier = Modifier.padding(
            start = PAGE_GUTTER, end = PAGE_GUTTER, top = 10.dp, bottom = 8.dp,
        ),
    )
}

/** Compact row used inside the artist song grid; no swipe, to keep the
 *  horizontal pager's gestures unambiguous. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactSongRow(
    song: Song,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    downloadedTint: Color? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.artworkAt(ROW_ART_PX),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(7.dp))
                .thumbnailBorder(RoundedCornerShape(7.dp))
                .background(palette.elevated),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            ExplicitSongTitle(
                song = song,
                style = MaterialTheme.typography.titleMedium,
                color = palette.onBackground,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onBackgroundVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (downloadedTint != null) {
            DownloadedBadge(song.videoId, downloadedTint)
        }
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
                tint = palette.onBackgroundVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * A row under "Suggested" — a track YouTube offers to round the playlist
 * out but that was never added. [onAdd] is the point of the row, so it gets
 * the trailing spot a track already on the playlist spends on "more"; the
 * long-press sheet is still one gesture away for anything else.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuggestedSongRow(
    song: Song,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onAdd: () -> Unit,
    downloadedTint: Color? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = PAGE_GUTTER, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.artworkAt(ROW_ART_PX),
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .thumbnailBorder(RoundedCornerShape(8.dp))
                .background(palette.elevated),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            ExplicitSongTitle(
                song = song,
                style = MaterialTheme.typography.titleMedium,
                color = palette.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onBackgroundVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (downloadedTint != null) {
            DownloadedBadge(song.videoId, downloadedTint)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(palette.accent.copy(alpha = 0.16f))
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = stringResource(R.string.add_to_playlist),
                tint = palette.accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SectionCard(
    item: ShelfItem,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .width(SHELF_CARD_WIDTH)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        AsyncImage(
            model = item.thumbnailUrl.artworkAt(CARD_ART_PX),
            contentDescription = null,
            modifier = Modifier
                .width(SHELF_CARD_WIDTH)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .thumbnailBorder(RoundedCornerShape(10.dp))
                .background(palette.elevated),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            color = palette.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.onBackgroundVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Splits the one subtitle a browse row hands over — "Album • Travis Scott •
 * 2023", or sometimes just "Travis Scott" — into the credit line and the
 * metadata line the header shows separately.
 *
 * Everything is optional, because every caller supplies a different amount of
 * it: the player knows an album's artist but not its year, search knows both,
 * and a home card frequently knows neither.
 */
@Composable
private fun DetailPage.headerLines(trackCount: Int): Pair<String, String> {
    val parts = subtitle.split("•", "·").map { it.trim() }.filter { it.isNotEmpty() }
    val year = parts.lastOrNull { it.length == 4 && it.all(Char::isDigit) }
    val kind = parts.firstOrNull { it.lowercase(Locale.ROOT) in KIND_WORDS }
    val credit = parts.filter { it != year && it != kind }.joinToString(", ")
    val meta = listOfNotNull(
        kind ?: type.localizedLabel(),
        year,
        trackCount.takeIf { it > 0 }?.let {
            pluralStringResource(R.plurals.track_count_plural, it, it)
        },
    ).joinToString(" • ").uppercase(Locale.getDefault())
    return credit to meta
}

/** Subtitle words that name what a page *is* rather than who made it. */
private val KIND_WORDS = setOf(
    "album", "single", "ep", "playlist", "artist", "podcast", "episode", "song", "video",
)

@Composable
private fun BrowseType.localizedLabel(): String? = when (this) {
        BrowseType.ALBUM -> stringResource(R.string.album)
        BrowseType.PLAYLIST -> stringResource(R.string.playlist)
        BrowseType.ARTIST -> stringResource(R.string.artist)
        BrowseType.OTHER -> null
    }

/** "12 songs, 41 minutes" — omitting the time when the rows carry no durations. */
@Composable
private fun List<Song>.playtimeSummary(): String {
    val count = pluralStringResource(R.plurals.song_count_plural, size, size)
    val minutes = sumOf { it.durationText.toSeconds() } / 60
    return when {
        minutes <= 0 -> count
        minutes < 60 -> stringResource(R.string.song_count_with_minutes, count, minutes)
        else -> {
            val hours = minutes / 60
            val rest = minutes % 60
            val hourLabel = pluralStringResource(R.plurals.hour_count, hours.toInt(), hours)
            if (rest == 0) {
                stringResource(R.string.song_count_with_duration, count, hourLabel)
            } else {
                stringResource(R.string.song_count_with_hours_minutes, count, hourLabel, rest)
            }
        }
    }
}

/** "3:45" or "1:02:33" as seconds; 0 for anything that isn't a duration. */
private fun String?.toSeconds(): Int {
    val parts = this?.split(":")?.map { it.trim().toIntOrNull() ?: return 0 } ?: return 0
    return when (parts.size) {
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> 0
    }
}
