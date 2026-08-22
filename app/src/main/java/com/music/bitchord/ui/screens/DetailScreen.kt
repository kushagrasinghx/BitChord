package com.music.bitchord.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
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
import com.music.bitchord.ui.components.MessageState
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.ROW_DIVIDER_INSET
import com.music.bitchord.ui.components.SHELF_CARD_WIDTH
import com.music.bitchord.ui.components.SongRow
import com.music.bitchord.ui.components.thumbnailBorder
import com.music.bitchord.ui.components.detailSkeleton
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.player.CanvasArtworkPlayer
import com.music.bitchord.ui.theme.ArtworkPalette
import com.music.bitchord.ui.theme.rememberArtworkPalette
import kotlin.math.roundToInt

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

/** The inset the header text and the action pills share. */
private val HEADER_GUTTER = PAGE_GUTTER + 14.dp

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
 * one smear of the same glass. It is the same thing [BottomFadeBlur] does to
 * the foot of the screen, pointed at the middle of this one.
 */
@Composable
fun DetailScreen(
    page: DetailPage,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onSectionItemClick: (ShelfItem) -> Unit,
    onDownloadAll: (List<Song>) -> Unit,
    onArtistClick: (String, String) -> Unit,
    onAddSuggested: (Song) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val songs = (page.songs as? UiState.Success)?.data.orEmpty()
    val isArtist = page.type == BrowseType.ARTIST
    val palette = rememberArtworkPalette(page.thumbnailUrl)

    // Animated cover art on the header, the same feature the player has.
    // Albums only: a playlist's artwork is a collage and an artist page's is a
    // photograph, and neither is something a label publishes a canvas for.
    val canvasEnabled by AppSettings.animatedCanvas.collectAsStateWithLifecycle()
    // The credit line the header shows is the artist as far as the catalogue
    // services are concerned. A browse card's subtitle sometimes omits it, in
    // which case the tracks themselves know who it is.
    val credit = remember(page.subtitle, songs) {
        page.headerLines(songs.size).first.ifBlank { songs.firstOrNull()?.artist.orEmpty() }
    }
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
    // The artwork is drawn behind the list rather than in it, so both need to
    // agree on its height without being able to ask each other. The width is
    // the screen's, so the ratio decides it and both can work it out alone.
    val artHeight = LocalConfiguration.current.screenWidthDp.dp /
        if (isArtist) ARTIST_PHOTO_RATIO else SLEEVE_RATIO

    Box(modifier.fillMaxSize()) {
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
                        // A page of on-device tracks has nothing further away
                        // to fetch — everything on it is already local.
                        onDownload = onDownloadAll.takeUnless { page.browseId.startsWith("local:") },
                        onArtistClick = onArtistClick,
                    )
                }
            }

            if (songs.isNotEmpty() && isArtist) {
                item(key = "actions") {
                    ActionRow(
                        palette = palette,
                        onPlay = { onSongClick(songs, 0) },
                        onShuffle = { onShuffle(songs) },
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
                        SectionHeading("Top songs", palette)
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
                    itemsIndexed(state.data) { index, song ->
                        SongRow(
                            song = if (numbered) {
                                song
                            } else {
                                song.copy(thumbnailUrl = song.thumbnailUrl ?: page.thumbnailUrl)
                            },
                            onClick = { onSongClick(state.data, index) },
                            onLongPress = { onSongLongPress(song) },
                            onSwipeToQueue = { onSongSwipe(song) },
                            rowBackground = Color.Transparent,
                            trackNumber = (index + 1).takeIf { numbered },
                            subtitleColor = palette.onBackgroundVariant,
                        )
                        if (index < state.data.lastIndex) {
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
            if (page.suggestedSongs.isNotEmpty()) {
                item(key = "suggested-heading") {
                    SectionHeading("Suggested", palette)
                }
                itemsIndexed(
                    page.suggestedSongs,
                    key = { _, song -> "suggested-${song.videoId}" },
                ) { index, song ->
                    SuggestedSongRow(
                        song = song,
                        palette = palette,
                        onClick = { onSongClick(page.suggestedSongs, index) },
                        onLongPress = { onSongLongPress(song) },
                        onAdd = { onAddSuggested(song) },
                    )
                    if (index < page.suggestedSongs.lastIndex) {
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
                            )
                        }
                    }
                }
            }
        }
    }
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
    onDownload: ((List<Song>) -> Unit)?,
    onArtistClick: (String, String) -> Unit,
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
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = HEADER_GUTTER),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleIconButton(
                        icon = BitChordIcons.Shuffle,
                        contentDescription = "Shuffle",
                        palette = palette,
                        onClick = onShuffle,
                    )
                    PlayPill(
                        palette = palette,
                        onClick = onPlay,
                    )
                    onDownload?.let { download ->
                        CircleIconButton(
                            icon = BitChordIcons.Download,
                            contentDescription = "Download all",
                            palette = palette,
                            onClick = { download(songs) },
                        )
                    }
                }
            }
        }
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
                .padding(horizontal = HEADER_GUTTER, vertical = 14.dp),
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
            .hazeEffect(hazeState) {
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
private fun ActionRow(palette: ArtworkPalette, onPlay: () -> Unit, onShuffle: () -> Unit) {
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
            contentDescription = "Shuffle",
            palette = palette,
            onClick = onShuffle,
        )

        PlayPill(
            palette = palette,
            onClick = onPlay,
        )
    }
    Spacer(Modifier.height(22.dp))
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
) {
    Row(
        modifier = modifier
            .height(50.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 32.dp),
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
            text = "Play",
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
) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
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
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = palette.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onBackgroundVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
                contentDescription = "More",
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
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = palette.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                contentDescription = "Add to playlist",
                tint = palette.accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SectionCard(item: ShelfItem, palette: ArtworkPalette, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(SHELF_CARD_WIDTH)
            .clickable(onClick = onClick),
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
private fun DetailPage.headerLines(trackCount: Int): Pair<String, String> {
    val parts = subtitle.split("•", "·").map { it.trim() }.filter { it.isNotEmpty() }
    val year = parts.lastOrNull { it.length == 4 && it.all(Char::isDigit) }
    val kind = parts.firstOrNull { it.lowercase() in KIND_WORDS }
    val credit = parts.filter { it != year && it != kind }.joinToString(", ")
    val meta = listOfNotNull(
        kind ?: type.label,
        year,
        trackCount.takeIf { it > 0 }?.let { "$it ${if (it == 1) "song" else "songs"}" },
    ).joinToString(" • ").uppercase()
    return credit to meta
}

/** Subtitle words that name what a page *is* rather than who made it. */
private val KIND_WORDS = setOf(
    "album", "single", "ep", "playlist", "artist", "podcast", "episode", "song", "video",
)

private val BrowseType.label: String?
    get() = when (this) {
        BrowseType.ALBUM -> "Album"
        BrowseType.PLAYLIST -> "Playlist"
        BrowseType.ARTIST -> "Artist"
        BrowseType.OTHER -> null
    }

/** "12 songs, 41 minutes" — omitting the time when the rows carry no durations. */
private fun List<Song>.playtimeSummary(): String {
    val count = "$size ${if (size == 1) "song" else "songs"}"
    val minutes = sumOf { it.durationText.toSeconds() } / 60
    return when {
        minutes <= 0 -> count
        minutes < 60 -> "$count, $minutes minutes"
        else -> {
            val hours = minutes / 60
            val rest = minutes % 60
            val hourLabel = "$hours ${if (hours == 1) "hour" else "hours"}"
            if (rest == 0) "$count, $hourLabel" else "$count, $hourLabel $rest minutes"
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
