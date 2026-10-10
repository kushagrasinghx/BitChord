package com.music.bitchord.ui.tv.screens

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.canvas.AppleArtistArt
import com.music.bitchord.data.canvas.AppleArtistArtRepository
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.tv.components.ProvideTvComfortScrolling
import com.music.bitchord.ui.tv.components.TvActivityIndicator
import com.music.bitchord.ui.tv.components.TvArtwork
import com.music.bitchord.ui.tv.components.TvButton
import com.music.bitchord.ui.tv.components.TvChromeScrollEffect
import com.music.bitchord.ui.tv.components.TvErrorState
import com.music.bitchord.ui.tv.components.TvLockup
import com.music.bitchord.ui.tv.components.TvRow
import com.music.bitchord.ui.tv.components.TvShelf
import com.music.bitchord.ui.tv.components.TvShelfTitle
import com.music.bitchord.ui.tv.components.TvSongRow
import com.music.bitchord.ui.tv.components.tvClick
import com.music.bitchord.ui.tv.components.tvInitialFocus
import com.music.bitchord.ui.tv.components.tvLift
import com.music.bitchord.ui.tv.components.tvPlatter
import com.music.bitchord.ui.tv.theme.TvDimensions
import com.music.bitchord.ui.tv.theme.TvGlass
import com.music.bitchord.ui.tv.theme.TvType
import kotlinx.coroutines.launch

/** Top Songs stops here, as on the phone: past twenty the list would bury the releases. */
private const val MaxTopSongs = 20

/** Top Songs pages sideways a column at a time; each column holds this many. */
private const val SongsPerColumn = 4

/**
 * An artist, laid out as the phone lays one out — hero photo with the name on
 * it and Play / Shuffle / Subscribe, the newest release, Top Songs in columns of
 * four, every shelf YouTube sends, then About — sized for a television.
 */
@Composable
internal fun TvArtistContent(
    page: DetailPage?,
    title: String,
    artworkUrl: String?,
    playingId: String?,
    isPlaying: Boolean,
    onPlay: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onSongMenu: (Song) -> Unit,
    onToggleSubscription: () -> Unit,
    onRetry: () -> Unit,
    onNavigateToDetail: (browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val songs = (page?.songs as? UiState.Success)?.data.orEmpty()
    val sections = page?.sections.orEmpty()
    val topRelease = remember(sections) { sections.topRelease() }

    // Apple Music's photo and title logo for this artist, found by name, as the
    // phone does. YouTube's own picture stands in until — and unless — it lands.
    var appleArt by remember(title) { mutableStateOf(AppleArtistArtRepository.cached(title)) }
    LaunchedEffect(title, page?.songs is UiState.Loading) {
        // A title that is still a whole credit ("A, B & C") finds nobody; wait
        // for the page to settle on the one name.
        if (page != null && page.songs !is UiState.Loading) {
            appleArt = AppleArtistArtRepository.artFor(title) ?: appleArt
        }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    TvChromeScrollEffect(listState)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val heroHeight = maxHeight * 0.68f
        ProvideTvComfortScrolling(top = 38.dp, bottom = 77.dp) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 45.dp),
                verticalArrangement = Arrangement.spacedBy(TvDimensions.ShelfSpacing - 11.dp),
            ) {
                item(key = "hero", contentType = "hero") {
                    TvArtistHero(
                        name = page?.title ?: title,
                        heroUrl = appleArt?.heroUrl ?: (page?.thumbnailUrl ?: artworkUrl).artworkAt(1280),
                        appleArt = appleArt,
                        stats = listOfNotNull(page?.monthlyListenerCount, page?.subscriberCountText).joinToString(" · "),
                        height = heroHeight,
                        canPlay = songs.isNotEmpty(),
                        subscribed = page?.subscription?.subscribed,
                        onPlay = { onPlay(songs, 0) },
                        onShuffle = { onShuffle(songs) },
                        onToggleSubscription = onToggleSubscription,
                        onActionsFocused = { scope.launch { listState.animateScrollToItem(0) } },
                    )
                }

                when (val state = page?.songs) {
                    null, UiState.Loading -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(22.dp), contentAlignment = Alignment.Center) {
                            TvActivityIndicator()
                        }
                    }
                    is UiState.Error -> item(key = "error") {
                        TvErrorState(message = state.message, onRetry = onRetry, modifier = Modifier.height(208.dp))
                    }
                    is UiState.Success -> Unit
                }

                if (topRelease != null) {
                    item(key = "latest", contentType = "latest") {
                        Column {
                            TvShelfTitle(title = "Latest Release")
                            TvLatestRelease(
                                item = topRelease,
                                onClick = { openShelfItem(topRelease, { onPlay(listOf(it), 0) }, onNavigateToDetail) },
                                modifier = Modifier.padding(
                                    start = TvDimensions.SafeMarginHorizontal,
                                    top = 11.dp,
                                    bottom = 6.dp,
                                ),
                            )
                        }
                    }
                }

                if (songs.isNotEmpty()) {
                    item(key = "topSongs", contentType = "topSongs") {
                        val top = remember(songs) { songs.take(MaxTopSongs) }
                        val columns = remember(top) { top.chunked(SongsPerColumn) }
                        Column {
                            TvShelfTitle(title = "Top Songs")
                            TvRow(
                                items = columns,
                                key = { it.first().videoId },
                                spacing = 19.dp,
                                top = 8.dp,
                                bottom = 8.dp,
                            ) { _, column ->
                                Column(modifier = Modifier.width(304.dp)) {
                                    column.forEach { song ->
                                        TvSongRow(
                                            song = song,
                                            showArtwork = true,
                                            isCurrent = song.videoId == playingId,
                                            isPlaying = isPlaying,
                                            onClick = { onPlay(top, top.indexOf(song)) },
                                            onLongClick = { onSongMenu(song) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                sections.forEachIndexed { index, shelf ->
                    if (shelf.items.isEmpty()) return@forEachIndexed
                    item(key = "section#$index", contentType = "section") {
                        val artists = shelf.items.count { it.browseId?.startsWith("UC") == true } * 2 > shelf.items.size
                        TvShelf(
                            title = shelf.title,
                            subtitle = shelf.subtitle.takeIf { it.isNotBlank() },
                            items = shelf.items,
                            key = { it.videoId ?: it.browseId ?: it.title },
                        ) { item ->
                            TvLockup(
                                title = item.title,
                                subtitle = if (artists) null else item.subtitle,
                                artworkUrl = item.thumbnailUrl,
                                circle = artists,
                                width = if (artists) 120.dp else 131.dp,
                                onClick = { openShelfItem(item, { onPlay(listOf(it), 0) }, onNavigateToDetail) },
                            )
                        }
                    }
                }

                val about = page?.description?.takeIf { it.isNotBlank() }
                val stats = listOfNotNull(page?.monthlyListenerCount, page?.subscriberCountText)
                if (about != null || stats.isNotEmpty()) {
                    item(key = "about", contentType = "about") {
                        Column {
                            TvShelfTitle(title = "About ${page?.title ?: title}")
                            TvAbout(
                                text = about,
                                stats = stats,
                                modifier = Modifier.padding(
                                    start = TvDimensions.SafeMarginHorizontal - 13.dp,
                                    end = TvDimensions.SafeMarginHorizontal,
                                    top = 8.dp,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The photo across the top, fading into the page at its foot, with the name —
 * Apple's title logo when there is one — counts and the actions laid over it.
 */
@Composable
private fun TvArtistHero(
    name: String,
    heroUrl: String?,
    appleArt: AppleArtistArt?,
    stats: String,
    height: androidx.compose.ui.unit.Dp,
    canPlay: Boolean,
    subscribed: Boolean?,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onToggleSubscription: () -> Unit,
    onActionsFocused: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        if (!heroUrl.isNullOrBlank()) {
            AsyncImage(
                model = heroUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                // Faded out to transparent rather than to a colour, so the photo
                // melts into whatever canvas is behind it with no seam. The
                // photo doesn't change, so the layer is drawn once and reused.
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(HeroSide)
                        drawRect(HeroFoot, blendMode = BlendMode.DstIn)
                    },
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = TvDimensions.SafeMarginHorizontal, end = TvDimensions.SafeMarginHorizontal, bottom = 8.dp),
        ) {
            val logo = appleArt?.logoUrl
            var logoShown by remember(logo) { mutableStateOf(false) }
            if (logo != null) {
                val aspect = appleArt.logoAspect.coerceIn(0.4f, 6f)
                AsyncImage(
                    model = logo,
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    onSuccess = { logoShown = true },
                    modifier = Modifier
                        .height(67.dp)
                        .widthIn(max = 368.dp)
                        .aspectRatio(aspect),
                )
            }
            if (!logoShown) {
                Text(
                    text = name,
                    style = TvType.LargeTitle.copy(fontSize = 39.sp, lineHeight = 44.sp, fontWeight = FontWeight.W800),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (stats.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(text = stats, style = TvType.Callout, color = Color.White.copy(alpha = 0.72f), maxLines = 1)
            }
            Spacer(Modifier.height(16.dp))
            TvActionRow(
                canPlay = canPlay,
                onPlay = onPlay,
                onShuffle = onShuffle,
                playColor = appleArt?.keyColor?.let { Color(it) },
                subscribed = subscribed,
                onToggleSubscription = onToggleSubscription,
                onFocused = onActionsFocused,
            )
        }
    }
}

private val HeroFoot = Brush.verticalGradient(
    0.45f to Color.Black,
    1f to Color.Transparent,
)

private val HeroSide = Brush.horizontalGradient(
    0f to Color.Black.copy(alpha = 0.45f),
    0.55f to Color.Transparent,
)

/** The newest release, as a wide card: its cover and what it is. */
@Composable
private fun TvLatestRelease(item: ShelfItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = modifier
            .width(432.dp)
            .tvLift(interaction, RoundedCornerShape(13.dp), focusedScale = 1.04f, elevation = 14.dp)
            .tvPlatter(interaction, resting = TvGlass.Fill)
            .tvClick(interaction, onClick = onClick)
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TvArtwork(url = item.thumbnailUrl, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(106.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = TvType.Headline,
                color = if (focused) TvGlass.OnPlatter else TvGlass.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = item.subtitle,
                style = TvType.Callout,
                color = if (focused) TvGlass.OnPlatterSecondary else TvGlass.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The blurb and the counts. Focusable, so the remote can reach the foot of the
 * page at all; select opens the whole text.
 */
@Composable
private fun TvAbout(text: String?, stats: List<String>, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .widthIn(max = 656.dp)
            .heightIn(min = 42.dp)
            .tvLift(interaction, RoundedCornerShape(13.dp), focusedScale = 1.02f, elevation = 11.dp)
            .tvPlatter(interaction, resting = Color.Transparent)
            .tvClick(interaction, onClick = { expanded = !expanded })
            .padding(13.dp),
    ) {
        if (text != null) {
            Text(
                text = text,
                style = TvType.Callout,
                color = if (focused) TvGlass.OnPlatter else TvGlass.TextSecondary,
                maxLines = if (expanded) Int.MAX_VALUE else 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (stats.isNotEmpty()) {
            if (text != null) Spacer(Modifier.height(10.dp))
            Text(
                text = stats.joinToString(" · "),
                style = TvType.Callout.copy(fontWeight = FontWeight.W600),
                color = if (focused) TvGlass.OnPlatter else TvGlass.TextPrimary,
            )
        }
    }
}

/**
 * The newest release on an artist's shelves — the phone's rule: only an `MPRE…`
 * id is a release, newest by the year in its subtitle, ties in YouTube's order.
 */
private fun List<HomeShelf>.topRelease(): ShelfItem? =
    asSequence()
        .flatMap { it.items.asSequence() }
        .filter { it.browseId?.startsWith("MPRE") == true }
        .withIndex()
        .maxWithOrNull(
            compareBy<IndexedValue<ShelfItem>> { it.value.releaseYear() ?: 0 }
                .thenByDescending { it.index },
        )?.value

private val ReleaseYear = Regex("""\b(19|20)\d{2}\b""")

private fun ShelfItem.releaseYear(): Int? =
    ReleaseYear.findAll(subtitle).lastOrNull()?.value?.toIntOrNull()
