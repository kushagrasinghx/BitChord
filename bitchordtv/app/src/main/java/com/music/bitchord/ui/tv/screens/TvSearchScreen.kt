package com.music.bitchord.ui.tv.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nightlife
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchHistoryEntity
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.settings.SearchHistory
import com.music.bitchord.playback.playSongs
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.tv.components.TvBottomLoadingBar
import com.music.bitchord.ui.tv.components.TvEmptyState
import com.music.bitchord.ui.tv.components.TvErrorState
import com.music.bitchord.ui.tv.focus.tvButtonFocus
import com.music.bitchord.ui.tv.theme.AppleSpringPreset
import com.music.bitchord.ui.tv.theme.LocalTvFontFamily
import com.music.bitchord.ui.tv.theme.TvSFProDisplay
import com.music.bitchord.ui.tv.theme.TvThemeColors
import com.music.bitchord.ui.tv.theme.appleSpring
import kotlinx.coroutines.launch

/**
 * Curated Genre / Mood card for the Explore browse grid.
 */
private data class TvSearchGenreCard(
    val title: String,
    val subtitle: String,
    val query: String,
    val gradientColors: List<Color>,
    val icon: ImageVector,
)

private val kSearchGenres = listOf(
    TvSearchGenreCard(
        title = "Bollywood Fire",
        subtitle = "Latest Hindi hits & chartbusters",
        query = "Bollywood Hits",
        gradientColors = listOf(Color(0xFFE50914), Color(0xFF8E0E00)),
        icon = Icons.Default.LocalFireDepartment,
    ),
    TvSearchGenreCard(
        title = "Punjabi Heat",
        subtitle = "High energy bhangra & bass",
        query = "Punjabi Party Hits",
        gradientColors = listOf(Color(0xFFFF9900), Color(0xFFD35400)),
        icon = Icons.Default.ElectricBolt,
    ),
    TvSearchGenreCard(
        title = "Global Top 50",
        subtitle = "Most played worldwide tracks",
        query = "Top 50 Global Hits",
        gradientColors = listOf(Color(0xFF0052D4), Color(0xFF4364F7)),
        icon = Icons.Default.TrendingUp,
    ),
    TvSearchGenreCard(
        title = "Hip-Hop & Rap",
        subtitle = "Heavy beats & iconic lyricists",
        query = "Hip Hop Rap",
        gradientColors = listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
        icon = Icons.Default.MusicNote,
    ),
    TvSearchGenreCard(
        title = "Chill & Lo-Fi",
        subtitle = "Relaxing beats for focus & study",
        query = "Chill Lo-Fi Beats",
        gradientColors = listOf(Color(0xFF4A00E0), Color(0xFF8E2DE2)),
        icon = Icons.Default.Spa,
    ),
    TvSearchGenreCard(
        title = "Acoustic & Indie",
        subtitle = "Soulful voices & unplugged guitars",
        query = "Acoustic Indie",
        gradientColors = listOf(Color(0xFF00B4DB), Color(0xFF0083B0)),
        icon = Icons.Default.MusicNote,
    ),
    TvSearchGenreCard(
        title = "Romantic Melodies",
        subtitle = "Love anthems & timeless duets",
        query = "Romantic Bollywood",
        gradientColors = listOf(Color(0xFFFF416C), Color(0xFFFF4B2B)),
        icon = Icons.Default.Favorite,
    ),
    TvSearchGenreCard(
        title = "Retro 90s & 2000s",
        subtitle = "Golden nostalgia & classic hits",
        query = "90s Bollywood Classics",
        gradientColors = listOf(Color(0xFFFF512F), Color(0xFFDD2476)),
        icon = Icons.Default.Radio,
    ),
    TvSearchGenreCard(
        title = "EDM & Club Dance",
        subtitle = "Festival drops & electronic pulse",
        query = "EDM Dance Hits",
        gradientColors = listOf(Color(0xFF8A2387), Color(0xFFE94057)),
        icon = Icons.Default.Nightlife,
    ),
    TvSearchGenreCard(
        title = "Workout & Power",
        subtitle = "Pump-up beats for high motivation",
        query = "Workout Music",
        gradientColors = listOf(Color(0xFFF12711), Color(0xFFF5AF19)),
        icon = Icons.Default.FitnessCenter,
    ),
    TvSearchGenreCard(
        title = "Rock & Metal",
        subtitle = "Guitar riffs, hard rock & stadium anthems",
        query = "Rock Anthems",
        gradientColors = listOf(Color(0xFF4B1248), Color(0xFF2C3E50)),
        icon = Icons.Default.MusicNote,
    ),
    TvSearchGenreCard(
        title = "Devotional & Soul",
        subtitle = "Peaceful chants & spiritual melodies",
        query = "Devotional Songs",
        gradientColors = listOf(Color(0xFF1D2671), Color(0xFFC33764)),
        icon = Icons.Default.Spa,
    ),
)

/**
 * Deduplicates search results so no repeating tracks or browse items appear.
 */
private fun List<SearchResult>.deduplicated(): List<SearchResult> {
    val seenTrackIds = mutableSetOf<String>()
    val seenBrowseIds = mutableSetOf<String>()
    val seenTrackKeys = mutableSetOf<String>()

    return filter { item ->
        when (item) {
            is SearchResult.TopTrack -> {
                val song = item.song
                val key = "${song.title.lowercase().trim()}|${song.artist.lowercase().trim()}"
                if (song.videoId.isNotBlank() && !seenTrackIds.add(song.videoId)) return@filter false
                if (!seenTrackKeys.add(key)) return@filter false
                true
            }
            is SearchResult.Track -> {
                val song = item.song
                val key = "${song.title.lowercase().trim()}|${song.artist.lowercase().trim()}"
                if (song.videoId.isNotBlank() && !seenTrackIds.add(song.videoId)) return@filter false
                if (!seenTrackKeys.add(key)) return@filter false
                true
            }
            is SearchResult.Browse -> {
                val browse = item.item
                if (browse.browseId.isNotBlank() && !seenBrowseIds.add(browse.browseId)) return@filter false
                true
            }
        }
    }
}

/**
 * 1:1 Redesigned TV Search Screen built completely from scratch:
 * - Full-width modern layout (no cramped side-panels)
 * - Prominent frosted search input bar at the top with D-pad keyboard actions
 * - High-contrast filter tabs (All, Songs, Albums, Artists, Playlists)
 * - Deduplicated results with unmistakable TYPE BADGES ([SONG], [ALBUM], [ARTIST], [PLAYLIST])
 * - Top Result Hero card featuring cover art, badges, and quick play action
 * - Beautiful 4-column Spotify-style category grid in idle state
 * - Stable remote focus traversal that never gets lost
 */
@Composable
fun TvSearchScreen(
    viewModel: MainViewModel,
    mediaController: MediaController?,
    onNavigateToDetail: (browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val query by viewModel.query.collectAsState()
    val resultsState by viewModel.results.collectAsState()
    val activeFilter by viewModel.filter.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val font = LocalTvFontFamily.current

    val hasSubmittedResults = resultsState is UiState.Success ||
            resultsState is UiState.Loading ||
            resultsState is UiState.Error

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = 36.dp,
                end = 36.dp,
                top = 20.dp,
                bottom = 20.dp,
            ),
    ) {
        // ── TOP SEARCH BAR & FILTER ROW ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header Search Input Pill
            TvModernSearchBar(
                query = query,
                onQueryChange = { viewModel.onQueryChange(it) },
                onSearch = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    viewModel.submitSearch()
                },
                onClear = { viewModel.onQueryChange("") },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Filter Pills Row: All, Songs, Albums, Artists, Playlists (Videos removed)
        val visibleFilters = remember {
            listOf(
                SearchFilter.ALL,
                SearchFilter.SONGS,
                SearchFilter.ALBUMS,
                SearchFilter.ARTISTS,
                SearchFilter.PLAYLISTS,
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(visibleFilters) { filter ->
                TvSearchFilterChip(
                    label = filter.label,
                    isSelected = filter == activeFilter,
                    onClick = { viewModel.onFilterChange(filter) },
                )
            }
        }

        // Live Suggestions row while typing
        if (query.isNotBlank() && suggestions.isNotEmpty() && !hasSubmittedResults) {
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                items(suggestions) { suggestion ->
                    TvSuggestionChip(
                        text = suggestion,
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            viewModel.searchFor(suggestion)
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── MAIN BODY CONTENT AREA ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                // 1. Loading state
                resultsState is UiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TvBottomLoadingBar(visible = true)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Searching \"$query\"...",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = font,
                            color = Color.White.copy(alpha = 0.70f),
                        )
                    }
                }

                // 2. Error state
                resultsState is UiState.Error -> {
                    val msg = (resultsState as UiState.Error).message
                    TvErrorState(
                        message = msg,
                        onRetry = { viewModel.submitSearch() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // 3. Search results state
                resultsState is UiState.Success -> {
                    val rawResults = (resultsState as UiState.Success<List<SearchResult>>).data
                    val results = remember(rawResults) { rawResults.deduplicated() }

                    if (results.isEmpty()) {
                        TvEmptyState(
                            title = "No results found for \"$query\"",
                            message = "Try checking for typos or searching with different artist, song, or album keywords.",
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        TvSearchResultsList(
                            results = results,
                            onPlaySong = { song ->
                                coroutineScope.launch {
                                    mediaController?.playSongs(listOf(song), 0)
                                }
                                onNavigateToNowPlaying()
                            },
                            onNavigateToDetail = onNavigateToDetail,
                        )
                    }
                }

                // 4. Idle Explore State (when query is empty or no search submitted yet)
                else -> {
                    TvSearchExploreIdleContent(
                        searchHistory = searchHistory,
                        onSelectHistory = { entity ->
                            if (entity.entityType == com.music.bitchord.data.model.EntityType.TRACK) {
                                viewModel.searchFor(entity.title)
                            } else {
                                onNavigateToDetail(
                                    entity.id,
                                    entity.title,
                                    entity.subtitle,
                                    entity.artworkUrl,
                                    when (entity.entityType) {
                                        com.music.bitchord.data.model.EntityType.ALBUM -> BrowseType.ALBUM
                                        com.music.bitchord.data.model.EntityType.ARTIST -> BrowseType.ARTIST
                                        com.music.bitchord.data.model.EntityType.PLAYLIST -> BrowseType.PLAYLIST
                                        else -> BrowseType.OTHER
                                    },
                                )
                            }
                        },
                        onClearHistory = { viewModel.clearSearchHistory() },
                        onSelectGenre = { genre ->
                            viewModel.searchFor(genre.query)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Modern TV Search Bar with clear icon and high-contrast D-pad focus ring.
 */
@Composable
private fun TvModernSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val font = LocalTvFontFamily.current

    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(
                if (isFocused) Color.White.copy(alpha = 0.16f)
                else Color.White.copy(alpha = 0.08f)
            )
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(26.dp),
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = if (isFocused) Color.White else Color.White.copy(alpha = 0.65f),
            modifier = Modifier.size(22.dp),
        )

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text(
                    text = "Search songs, albums, artists, playlists...",
                    fontSize = 16.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.40f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 17.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                ),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                interactionSource = interactionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusable(interactionSource = interactionSource),
            )
        }

        if (query.isNotEmpty()) {
            val clearInteraction = remember { MutableInteractionSource() }
            val clearFocused by clearInteraction.collectIsFocusedAsState()

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (clearFocused) Color.White else Color.White.copy(alpha = 0.15f)
                    )
                    .focusable(interactionSource = clearInteraction)
                    .clickable(
                        interactionSource = clearInteraction,
                        indication = null,
                        onClick = onClear,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear search",
                    tint = if (clearFocused) Color.Black else Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * Filter Chip (All, Songs, Albums, Artists, Playlists).
 */
@Composable
private fun TvSearchFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val font = LocalTvFontFamily.current

    val bg = when {
        isFocused -> Color.White
        isSelected -> Color.White.copy(alpha = 0.26f)
        else -> Color.White.copy(alpha = 0.08f)
    }

    val textColor = when {
        isFocused -> Color(0xFF090A0F)
        isSelected -> Color.White
        else -> Color.White.copy(alpha = 0.70f)
    }

    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(
                width = if (isFocused) 2.dp else if (isSelected) 1.dp else 0.dp,
                color = if (isFocused) Color.White else if (isSelected) Color.White.copy(alpha = 0.35f) else Color.Transparent,
                shape = RoundedCornerShape(18.dp),
            )
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
            fontFamily = font,
            color = textColor,
        )
    }
}

/**
 * Typeahead autocomplete suggestion pill.
 */
@Composable
private fun TvSuggestionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val font = LocalTvFontFamily.current

    Row(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.10f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(16.dp),
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Default.TrendingUp,
            contentDescription = null,
            tint = if (isFocused) Color.Black else Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            fontFamily = font,
            color = if (isFocused) Color.Black else Color.White,
        )
    }
}

/**
 * Results list showing hero top result card followed by clean, perfectly-sized, labeled items.
 */
@Composable
private fun TvSearchResultsList(
    results: List<SearchResult>,
    onPlaySong: (Song) -> Unit,
    onNavigateToDetail: (browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val font = LocalTvFontFamily.current

    val topItem = results.firstOrNull()
    val remainingResults = if (results.size > 1) results.drop(1) else emptyList()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── TOP RESULT HERO CARD ──
        if (topItem != null) {
            item(key = "hero_top_result") {
                TvSearchTopResultHero(
                    result = topItem,
                    onPlaySong = onPlaySong,
                    onNavigateToDetail = onNavigateToDetail,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "ALL RESULTS",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W800,
                    letterSpacing = 1.sp,
                    fontFamily = font,
                    color = Color.White.copy(alpha = 0.50f),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }
        }

        // ── RESULTS ROWS ──
        itemsIndexed(
            items = remainingResults,
            key = { index, item ->
                when (item) {
                    is SearchResult.TopTrack -> "top_${item.song.videoId}_$index"
                    is SearchResult.Track -> "track_${item.song.videoId}_$index"
                    is SearchResult.Browse -> "browse_${item.item.browseId}_$index"
                }
            },
        ) { _, resultItem ->
            TvSearchResultRow(
                result = resultItem,
                onPlaySong = onPlaySong,
                onNavigateToDetail = onNavigateToDetail,
            )
        }

        item {
            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

/**
 * Prominent Spotify TV style Top Result Hero Card.
 */
@Composable
private fun TvSearchTopResultHero(
    result: SearchResult,
    onPlaySong: (Song) -> Unit,
    onNavigateToDetail: (browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val font = LocalTvFontFamily.current
    val title: String
    val subtitle: String
    val thumbUrl: String?
    val typeBadge: String
    val badgeColor: Color
    val isTrack: Boolean

    when (result) {
        is SearchResult.TopTrack -> {
            title = result.song.title
            val album = if (!result.song.albumName.isNullOrEmpty()) " • ${result.song.albumName}" else ""
            val dur = if (!result.song.durationText.isNullOrEmpty()) " • ${result.song.durationText}" else ""
            subtitle = "${result.song.artist}$album$dur"
            thumbUrl = result.song.thumbnailUrl
            typeBadge = "SONG"
            badgeColor = Color(0xFF1DB954)
            isTrack = true
        }
        is SearchResult.Track -> {
            title = result.song.title
            val album = if (!result.song.albumName.isNullOrEmpty()) " • ${result.song.albumName}" else ""
            val dur = if (!result.song.durationText.isNullOrEmpty()) " • ${result.song.durationText}" else ""
            subtitle = "${result.song.artist}$album$dur"
            thumbUrl = result.song.thumbnailUrl
            typeBadge = "SONG"
            badgeColor = Color(0xFF1DB954)
            isTrack = true
        }
        is SearchResult.Browse -> {
            title = result.item.title
            subtitle = result.item.subtitle
            thumbUrl = result.item.thumbnailUrl
            typeBadge = when (result.item.type) {
                BrowseType.ALBUM -> "ALBUM"
                BrowseType.ARTIST -> "ARTIST"
                BrowseType.PLAYLIST -> "PLAYLIST"
                BrowseType.OTHER -> "COLLECTION"
            }
            badgeColor = when (result.item.type) {
                BrowseType.ALBUM -> Color(0xFFBB86FC)
                BrowseType.ARTIST -> Color(0xFF03DAC6)
                BrowseType.PLAYLIST -> Color(0xFFFFB74D)
                BrowseType.OTHER -> Color(0xFF90CAF9)
            }
            isTrack = false
        }
    }

    val onClick: () -> Unit = {
        when (result) {
            is SearchResult.TopTrack -> onPlaySong(result.song)
            is SearchResult.Track -> onPlaySong(result.song)
            is SearchResult.Browse -> onNavigateToDetail(
                result.item.browseId,
                result.item.title,
                result.item.subtitle,
                result.item.thumbnailUrl,
                result.item.type,
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(136.dp)
            .tvButtonFocus(
                shape = RoundedCornerShape(20.dp),
                focusedScale = 1.02f,
                focusedBorderColor = Color.White,
                borderWidth = 2.5.dp,
                onClick = onClick,
            )
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.05f),
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Artwork
            AsyncImage(
                model = thumbUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(108.dp)
                    .clip(
                        if (typeBadge == "ARTIST") CircleShape else RoundedCornerShape(14.dp)
                    )
                    .background(Color.White.copy(alpha = 0.10f)),
            )

            // Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // TOP RESULT tag
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.20f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "TOP RESULT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.W800,
                            letterSpacing = 0.8.sp,
                            fontFamily = font,
                            color = Color.White,
                        )
                    }

                    // TYPE BADGE tag
                    TvItemTypeBadge(type = typeBadge, color = badgeColor)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = font,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    fontSize = 15.sp,
                    fontFamily = font,
                    color = Color.White.copy(alpha = 0.70f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Quick Play Button icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color(0xFF090A0F),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/**
 * Super clean individual search result row (70dp height) with artwork, title, subtitle, and explicit TYPE BADGE.
 */
@Composable
private fun TvSearchResultRow(
    result: SearchResult,
    onPlaySong: (Song) -> Unit,
    onNavigateToDetail: (browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val font = LocalTvFontFamily.current
    val title: String
    val subtitle: String
    val thumbUrl: String?
    val typeBadge: String
    val badgeColor: Color
    val isArtist: Boolean

    when (result) {
        is SearchResult.TopTrack -> {
            title = result.song.title
            val album = if (!result.song.albumName.isNullOrEmpty()) " • ${result.song.albumName}" else ""
            val dur = if (!result.song.durationText.isNullOrEmpty()) " • ${result.song.durationText}" else ""
            subtitle = "${result.song.artist}$album$dur"
            thumbUrl = result.song.thumbnailUrl
            typeBadge = "SONG"
            badgeColor = Color(0xFF1DB954)
            isArtist = false
        }
        is SearchResult.Track -> {
            title = result.song.title
            val album = if (!result.song.albumName.isNullOrEmpty()) " • ${result.song.albumName}" else ""
            val dur = if (!result.song.durationText.isNullOrEmpty()) " • ${result.song.durationText}" else ""
            subtitle = "${result.song.artist}$album$dur"
            thumbUrl = result.song.thumbnailUrl
            typeBadge = "SONG"
            badgeColor = Color(0xFF1DB954)
            isArtist = false
        }
        is SearchResult.Browse -> {
            title = result.item.title
            subtitle = result.item.subtitle
            thumbUrl = result.item.thumbnailUrl
            typeBadge = when (result.item.type) {
                BrowseType.ALBUM -> "ALBUM"
                BrowseType.ARTIST -> "ARTIST"
                BrowseType.PLAYLIST -> "PLAYLIST"
                BrowseType.OTHER -> "COLLECTION"
            }
            badgeColor = when (result.item.type) {
                BrowseType.ALBUM -> Color(0xFFBB86FC)
                BrowseType.ARTIST -> Color(0xFF03DAC6)
                BrowseType.PLAYLIST -> Color(0xFFFFB74D)
                BrowseType.OTHER -> Color(0xFF90CAF9)
            }
            isArtist = result.item.type == BrowseType.ARTIST
        }
    }

    val onClick: () -> Unit = {
        when (result) {
            is SearchResult.TopTrack -> onPlaySong(result.song)
            is SearchResult.Track -> onPlaySong(result.song)
            is SearchResult.Browse -> onNavigateToDetail(
                result.item.browseId,
                result.item.title,
                result.item.subtitle,
                result.item.thumbnailUrl,
                result.item.type,
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .tvButtonFocus(
                shape = RoundedCornerShape(14.dp),
                focusedScale = 1.02f,
                focusedBorderColor = Color.White,
                borderWidth = 2.dp,
                onClick = onClick,
            )
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Artwork
            AsyncImage(
                model = thumbUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(50.dp)
                    .clip(if (isArtist) CircleShape else RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.10f)),
            )

            // Titles + Type Badge
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = font,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    // Explicit TYPE BADGE
                    TvItemTypeBadge(type = typeBadge, color = badgeColor)
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    fontFamily = font,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Right Play Icon
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.50f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * High-contrast monospace micro-badge clearly distinguishing [SONG], [ALBUM], [ARTIST], and [PLAYLIST].
 */
@Composable
private fun TvItemTypeBadge(
    type: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.20f))
            .border(1.dp, color.copy(alpha = 0.60f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = type,
            fontSize = 9.sp,
            fontWeight = FontWeight.W800,
            letterSpacing = 0.8.sp,
            fontFamily = FontFamily.Monospace,
            color = color,
        )
    }
}

/**
 * Idle Explore Screen: Recent searches carousel and vibrant 4-column Spotify-style category cards grid.
 */
@Composable
private fun TvSearchExploreIdleContent(
    searchHistory: List<SearchHistoryEntity>,
    onSelectHistory: (SearchHistoryEntity) -> Unit,
    onClearHistory: () -> Unit,
    onSelectGenre: (TvSearchGenreCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val font = LocalTvFontFamily.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // ── RECENT SEARCHES ──
        if (searchHistory.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "RECENT SEARCHES",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W800,
                        letterSpacing = 1.sp,
                        fontFamily = font,
                        color = Color.White.copy(alpha = 0.50f),
                    )

                    val clearInteraction = remember { MutableInteractionSource() }
                    val clearFocused by clearInteraction.collectIsFocusedAsState()

                    Text(
                        text = "Clear History",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = font,
                        color = if (clearFocused) Color.White else Color.White.copy(alpha = 0.50f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .focusable(interactionSource = clearInteraction)
                            .clickable(
                                interactionSource = clearInteraction,
                                indication = null,
                                onClick = onClearHistory,
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(searchHistory) { entity ->
                        TvRecentSearchItem(
                            entity = entity,
                            onClick = { onSelectHistory(entity) },
                        )
                    }
                }
            }
        }

        // ── BROWSE GENRES & MOODS ──
        item {
            Text(
                text = "BROWSE ALL CATEGORIES",
                fontSize = 13.sp,
                fontWeight = FontWeight.W800,
                letterSpacing = 1.sp,
                fontFamily = font,
                color = Color.White.copy(alpha = 0.50f),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 4-column Category Tiles Grid
        items(kSearchGenres.chunked(4)) { rowGenres ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                rowGenres.forEach { genre ->
                    TvGenreCard(
                        genre = genre,
                        onClick = { onSelectGenre(genre) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Fill blank slots if last chunk has less than 4
                val remaining = 4 - rowGenres.size
                if (remaining > 0) {
                    repeat(remaining) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        item {
            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

/**
 * Individual Recent Search chip.
 */
@Composable
private fun TvRecentSearchItem(
    entity: SearchHistoryEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val font = LocalTvFontFamily.current

    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.08f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(22.dp),
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!entity.artworkUrl.isNullOrEmpty()) {
            AsyncImage(
                model = entity.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape),
            )
        } else {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = if (isFocused) Color.Black else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }

        Text(
            text = entity.title,
            fontSize = 14.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            fontFamily = font,
            color = if (isFocused) Color(0xFF090A0F) else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Vibrant 1:1 Spotify TV style genre & mood tile card.
 */
@Composable
private fun TvGenreCard(
    genre: TvSearchGenreCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val font = LocalTvFontFamily.current

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = when {
            isPressed -> 0.96f
            isFocused -> 1.05f
            else -> 1.0f
        },
        animationSpec = appleSpring(AppleSpringPreset.Snappy),
        label = "genreCardScale",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(98.dp)
            .shadow(
                elevation = if (isFocused) 16.dp else 4.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = genre.gradientColors.first().copy(alpha = 0.5f),
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = genre.gradientColors,
                )
            )
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.20f),
                shape = RoundedCornerShape(16.dp),
            )
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(14.dp),
    ) {
        // Decorative background angled icon
        Icon(
            imageVector = genre.icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.22f),
            modifier = Modifier
                .size(54.dp)
                .align(Alignment.BottomEnd)
                .rotate(-15f),
        )

        // Text labels
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = genre.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.W800,
                fontFamily = font,
                color = Color.White,
                letterSpacing = (-0.3).sp,
            )

            Text(
                text = genre.subtitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = font,
                color = Color.White.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}