package com.music.bitchord.ui.tv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import com.music.bitchord.ui.tv.components.tvNavbarBlur
import com.music.bitchord.ui.tv.components.tvMiniPlayerBlur
import com.music.bitchord.ui.tv.components.TvLeftNavRail
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.music.bitchord.R
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.TvSettings
import com.music.bitchord.playback.PlayerState
import com.music.bitchord.playback.playSongs
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.tv.dialogs.TvAboutDialog
import com.music.bitchord.ui.tv.dialogs.TvAccountDialog
import com.music.bitchord.ui.tv.dialogs.TvDiscordDialog
import com.music.bitchord.ui.tv.dialogs.TvNicknameDialog
import com.music.bitchord.ui.tv.dialogs.TvScrobbleDialog
import com.music.bitchord.ui.tv.dialogs.TvSourcesDialog
import com.music.bitchord.ui.tv.dialogs.TvThemeDialog
import com.music.bitchord.ui.tv.focus.tvButtonFocus
import com.music.bitchord.ui.tv.player.TvNowPlayingScreen
import com.music.bitchord.ui.tv.screens.TvDetailScreen
import com.music.bitchord.ui.tv.screens.TvHomeScreen
import com.music.bitchord.ui.tv.screens.TvLibraryScreen
import com.music.bitchord.ui.tv.screens.TvSearchScreen
import com.music.bitchord.ui.tv.screens.TvSettingsScreen
import com.music.bitchord.ui.tv.theme.AppleSpringPreset
import com.music.bitchord.ui.tv.theme.BitChordTvTheme
import com.music.bitchord.ui.tv.theme.LocalTvFontFamily
import com.music.bitchord.ui.tv.theme.TvDimensions
import com.music.bitchord.ui.tv.theme.TvSFProDisplay
import com.music.bitchord.ui.tv.theme.TvThemeColors
import com.music.bitchord.ui.tv.theme.appleSpring

enum class TvDestination(val label: String, val icon: ImageVector) {
    FOR_YOU("Home", Icons.Default.Home),
    LIBRARY("Library", Icons.Default.LibraryMusic),
    SEARCH("Search", Icons.Default.Search),
    SETTINGS("Settings", Icons.Default.Settings),
    BROWSE("Browse", Icons.Default.Explore),
    RADIO("Radio", Icons.Default.Radio),
}

private data class DetailDestination(
    val browseId: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val type: BrowseType,
)

@Composable
fun TvApp(
    viewModel: MainViewModel,
    mediaController: MediaController?,
    playerState: PlayerState,
    modifier: Modifier = Modifier,
) {
    BitChordTvTheme {
        var activeDestination by remember { mutableStateOf(TvDestination.FOR_YOU) }
        var activeDetail by remember { mutableStateOf<DetailDestination?>(null) }
        var isNowPlayingOpen by remember { mutableStateOf(false) }

        val setupVersionCompleted by com.music.bitchord.data.settings.TvSettings.tvSetupVersionCompleted.collectAsState()
        var isRunningSetup by remember(setupVersionCompleted) { mutableStateOf(setupVersionCompleted == 0) }
        val tvNavLayout by TvSettings.tvNavLayout.collectAsState()
        val isLeftRail = tvNavLayout == "left_rail"

        // Dialog States
        var showAccountDialog by remember { mutableStateOf(false) }
        var showDiscordDialog by remember { mutableStateOf(false) }
        var showScrobbleDialog by remember { mutableStateOf(false) }
        var showSourcesDialog by remember { mutableStateOf(false) }
        var showRefreshRateDialog by remember { mutableStateOf(false) }
        var showNicknameDialog by remember { mutableStateOf(false) }
        var showThemeDialog by remember { mutableStateOf(false) }
        var showAboutDialog by remember { mutableStateOf(false) }

        val palette = TvThemeColors.current



        // Initialize Spatial Audio Virtualizer
        val spatialAudio by TvSettings.spatialAudioEnabled.collectAsState()
        LaunchedEffect(spatialAudio) {
            com.music.bitchord.ui.tv.audio.TvSpatialAudioEngine.setEnabled(spatialAudio)
        }

        // App Startup Banner Animation
        var showStartupBanner by remember { mutableStateOf(true) }
        var bannerFading by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            delay(1200)
            bannerFading = true
            delay(750)
            showStartupBanner = false
        }

        val bannerAlpha by animateFloatAsState(
            targetValue = if (bannerFading) 0f else 1f,
            animationSpec = tween(700, easing = FastOutSlowInEasing),
            label = "bannerAlpha",
        )
        val bannerScale by animateFloatAsState(
            targetValue = if (bannerFading) 1.05f else 1f,
            animationSpec = tween(700, easing = FastOutSlowInEasing),
            label = "bannerScale",
        )

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(palette.background),
        ) {
            AnimatedContent(
                targetState = when {
                    isRunningSetup -> "setup"
                    isNowPlayingOpen -> "player"
                    else -> "main"
                },
                transitionSpec = {
                    if (targetState == "player") {
                        (scaleIn(initialScale = 0.85f, animationSpec = appleSpring(AppleSpringPreset.Gentle)) + fadeIn(tween(260)))
                            .togetherWith(scaleOut(targetScale = 0.94f, animationSpec = tween(200)) + fadeOut(tween(200)))
                    } else if (initialState == "player") {
                        (scaleIn(initialScale = 1.05f, animationSpec = tween(220)) + fadeIn(tween(220)))
                            .togetherWith(scaleOut(targetScale = 0.85f, animationSpec = appleSpring(AppleSpringPreset.Gentle)) + fadeOut(tween(220)))
                    } else {
                        fadeIn(tween(180)).togetherWith(fadeOut(tween(180)))
                    }
                },
                label = "tvAppViewTransition",
                modifier = Modifier.fillMaxSize(),
            ) { viewState ->
                when (viewState) {
                    "setup" -> {
                        com.music.bitchord.ui.tv.onboarding.TvSetupScreen(
                            onComplete = { isRunningSetup = false },
                        )
                    }
                    "player" -> {
                        TvNowPlayingScreen(
                            viewModel = viewModel,
                            mediaController = mediaController,
                            playerState = playerState,
                            onBack = { isNowPlayingOpen = false },
                        )
                    }
                    else -> {
                        if (isLeftRail) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                // Spotify TV Style Left Vertical Navigation Sidebar
                                TvLeftNavRail(
                                    activeDestination = activeDestination,
                                    hasNowPlaying = playerState.song != null,
                                    isPlaying = playerState.isPlaying,
                                    onDestinationSelected = { dest ->
                                        activeDetail = null
                                        activeDestination = dest
                                    },
                                    onOpenNowPlaying = { isNowPlayingOpen = true },
                                )

                                // Main Screen Content Area taking full remaining width and full height
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                ) {
                                    TvMainContentBody(
                                        activeDestination = activeDestination,
                                        activeDetail = activeDetail,
                                        viewModel = viewModel,
                                        mediaController = mediaController,
                                        onDestinationChange = { activeDestination = it },
                                        onDetailChange = { activeDetail = it },
                                        onOpenNowPlaying = { isNowPlayingOpen = true },
                                        onOpenAccountDialog = { showAccountDialog = true },
                                        onOpenDiscordDialog = { showDiscordDialog = true },
                                        onOpenScrobbleDialog = { showScrobbleDialog = true },
                                        onOpenSourcesDialog = { showSourcesDialog = true },
                                        onOpenRefreshRateDialog = { showRefreshRateDialog = true },
                                        onOpenNicknameDialog = { showNicknameDialog = true },
                                        onOpenThemeDialog = { showThemeDialog = true },
                                        onRunSetupAgain = { isRunningSetup = true },
                                        onOpenAboutDialog = { showAboutDialog = true },
                                    )

                                    // Floating Mini Playback Bar (bottom right) with frosted glass blur
                                    if (playerState.song != null && !isNowPlayingOpen) {
                                        TvGlobalMiniPlayer(
                                            playerState = playerState,
                                            mediaController = mediaController,
                                            onClick = { isNowPlayingOpen = true },
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(end = TvDimensions.SafeMarginHorizontal, bottom = 24.dp)
                                                .zIndex(15f),
                                        )
                                    }
                                }
                            }
                        } else {
                            // Classic Apple TV Top Pill Navigation Bar Layout
                            Box(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(top = 74.dp),
                                ) {
                                    TvMainContentBody(
                                        activeDestination = activeDestination,
                                        activeDetail = activeDetail,
                                        viewModel = viewModel,
                                        mediaController = mediaController,
                                        onDestinationChange = { activeDestination = it },
                                        onDetailChange = { activeDetail = it },
                                        onOpenNowPlaying = { isNowPlayingOpen = true },
                                        onOpenAccountDialog = { showAccountDialog = true },
                                        onOpenDiscordDialog = { showDiscordDialog = true },
                                        onOpenScrobbleDialog = { showScrobbleDialog = true },
                                        onOpenSourcesDialog = { showSourcesDialog = true },
                                        onOpenRefreshRateDialog = { showRefreshRateDialog = true },
                                        onOpenNicknameDialog = { showNicknameDialog = true },
                                        onOpenThemeDialog = { showThemeDialog = true },
                                        onRunSetupAgain = { isRunningSetup = true },
                                        onOpenAboutDialog = { showAboutDialog = true },
                                    )

                                    // Floating Mini Playback Bar (bottom right) with frosted glass blur
                                    if (playerState.song != null && !isNowPlayingOpen) {
                                        TvGlobalMiniPlayer(
                                            playerState = playerState,
                                            mediaController = mediaController,
                                            onClick = { isNowPlayingOpen = true },
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(end = TvDimensions.SafeMarginHorizontal, bottom = 24.dp)
                                                .zIndex(15f),
                                        )
                                    }
                                }

                                // Transparent Top Navigation Bar with Frosted Glass Blur
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth()
                                        .zIndex(20f),
                                ) {
                                    TvTopNavigationBar(
                                        activeDestination = activeDestination,
                                        hasNowPlaying = playerState.song != null,
                                        onDestinationSelected = { dest ->
                                            activeDetail = null
                                            activeDestination = dest
                                        },
                                        onOpenNowPlaying = { isNowPlayingOpen = true },
                                        onOpenSearch = {
                                            activeDetail = null
                                            activeDestination = TvDestination.SEARCH
                                        },
                                        onOpenSettings = {
                                            activeDetail = null
                                            activeDestination = TvDestination.SETTINGS
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Dialog Overlays
            if (showAccountDialog) {
                TvAccountDialog(viewModel = viewModel, onDismiss = { showAccountDialog = false })
            }
            if (showDiscordDialog) {
                TvDiscordDialog(onDismiss = { showDiscordDialog = false })
            }
            if (showScrobbleDialog) {
                TvScrobbleDialog(onDismiss = { showScrobbleDialog = false })
            }
            if (showSourcesDialog) {
                TvSourcesDialog(onDismiss = { showSourcesDialog = false })
            }
            if (showRefreshRateDialog) {
                com.music.bitchord.ui.tv.dialogs.TvRefreshRateDialog(onDismiss = { showRefreshRateDialog = false })
            }
            if (showNicknameDialog) {
                TvNicknameDialog(onDismiss = { showNicknameDialog = false })
            }
            if (showThemeDialog) {
                TvThemeDialog(onDismiss = { showThemeDialog = false })
            }
            if (showAboutDialog) {
                TvAboutDialog(onDismiss = { showAboutDialog = false })
            }

            // App Startup Banner Animation: FULLSCREEN edge-to-edge fit-to-screen
            if (showStartupBanner) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(100f)
                        .graphicsLayer {
                            alpha = bannerAlpha
                            scaleX = bannerScale
                            scaleY = bannerScale
                        }
                        .background(Color(0xFF101015)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.tv_banner),
                        contentDescription = "BitChord TV",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}

/**
 * Hosts the active main screen destination (Home, Search, Library, Settings, or Detail)
 * with snappy crossfade transitions.
 */
@Composable
private fun TvMainContentBody(
    activeDestination: TvDestination,
    activeDetail: DetailDestination?,
    viewModel: MainViewModel,
    mediaController: MediaController?,
    onDestinationChange: (TvDestination) -> Unit,
    onDetailChange: (DetailDestination?) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenAccountDialog: () -> Unit,
    onOpenDiscordDialog: () -> Unit,
    onOpenScrobbleDialog: () -> Unit,
    onOpenSourcesDialog: () -> Unit,
    onOpenRefreshRateDialog: () -> Unit,
    onOpenNicknameDialog: () -> Unit,
    onOpenThemeDialog: () -> Unit,
    onRunSetupAgain: () -> Unit,
    onOpenAboutDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (activeDetail != null) {
        val detail = activeDetail
        TvDetailScreen(
            browseId = detail.browseId,
            initialTitle = detail.title,
            initialSubtitle = detail.subtitle,
            initialThumbnailUrl = detail.thumbnailUrl,
            type = detail.type,
            viewModel = viewModel,
            mediaController = mediaController,
            onNavigateToNowPlaying = onOpenNowPlaying,
            onBack = { onDetailChange(null) },
            modifier = modifier,
        )
    } else {
        Crossfade(
            targetState = activeDestination,
            animationSpec = tween(durationMillis = 140),
            label = "navTabCrossfade",
            modifier = modifier,
        ) { destination ->
            when (destination) {
                TvDestination.FOR_YOU -> {
                    TvHomeScreen(
                        viewModel = viewModel,
                        mediaController = mediaController,
                        onNavigateToDetail = { browseId, title, subtitle, thumb, type ->
                            onDetailChange(DetailDestination(browseId, title, subtitle, thumb, type))
                        },
                        onNavigateToNowPlaying = onOpenNowPlaying,
                    )
                }
                TvDestination.BROWSE -> {
                    TvDetailScreen(
                        browseId = "FEmusic_explore",
                        initialTitle = "Browse & Explore",
                        initialSubtitle = "Explore charts, genres, and mood collections",
                        initialThumbnailUrl = null,
                        type = BrowseType.OTHER,
                        viewModel = viewModel,
                        mediaController = mediaController,
                        onNavigateToNowPlaying = onOpenNowPlaying,
                        onBack = { onDestinationChange(TvDestination.FOR_YOU) },
                    )
                }
                TvDestination.RADIO -> {
                    TvDetailScreen(
                        browseId = "FEmusic_radio",
                        initialTitle = "Radio Stations & Mixes",
                        initialSubtitle = "Continuous automated playback & custom stations",
                        initialThumbnailUrl = null,
                        type = BrowseType.OTHER,
                        viewModel = viewModel,
                        mediaController = mediaController,
                        onNavigateToNowPlaying = onOpenNowPlaying,
                        onBack = { onDestinationChange(TvDestination.FOR_YOU) },
                    )
                }
                TvDestination.LIBRARY -> {
                    TvLibraryScreen(
                        viewModel = viewModel,
                        onNavigateToDetail = { browseId, title, subtitle, thumb, type ->
                            onDetailChange(DetailDestination(browseId, title, subtitle, thumb, type))
                        },
                        onNavigateToLocalMusic = {
                            onDestinationChange(TvDestination.FOR_YOU)
                        },
                        onNavigateToDownloads = {},
                        onNavigateToHistory = {},
                        onNavigateToLiked = {},
                        onNavigateToSearch = {
                            onDestinationChange(TvDestination.SEARCH)
                        },
                        onNavigateToSettings = {
                            onDestinationChange(TvDestination.SETTINGS)
                        },
                    )
                }
                TvDestination.SEARCH -> {
                    TvSearchScreen(
                        viewModel = viewModel,
                        mediaController = mediaController,
                        onNavigateToDetail = { browseId, title, subtitle, thumb, type ->
                            onDetailChange(DetailDestination(browseId, title, subtitle, thumb, type))
                        },
                        onNavigateToNowPlaying = onOpenNowPlaying,
                    )
                }
                TvDestination.SETTINGS -> {
                    TvSettingsScreen(
                        viewModel = viewModel,
                        onBack = { onDestinationChange(TvDestination.FOR_YOU) },
                        onOpenAccountDialog = onOpenAccountDialog,
                        onOpenDiscordDialog = onOpenDiscordDialog,
                        onOpenScrobbleDialog = onOpenScrobbleDialog,
                        onOpenSourcesDialog = onOpenSourcesDialog,
                        onOpenRefreshRateDialog = onOpenRefreshRateDialog,
                        onOpenNicknameDialog = onOpenNicknameDialog,
                        onOpenThemeDialog = onOpenThemeDialog,
                        onRunSetupAgain = onRunSetupAgain,
                        onOpenAboutDialog = onOpenAboutDialog,
                    )
                }
            }
        }
    }
}

/**
 * 1:1 Apple Music-style Top Unified Horizontal Pill Navigation Bar with top-right search & settings buttons.
 * Uses ultra-optimized custom blur.
 */
@Composable
private fun TvTopNavigationBar(
    activeDestination: TvDestination,
    hasNowPlaying: Boolean,
    onDestinationSelected: (TvDestination) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = TvThemeColors.current

    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        // Frosted Glass Blur background layer (sits behind children)
        Box(
            modifier = Modifier
                .matchParentSize()
                .tvNavbarBlur(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = TvDimensions.SafeMarginHorizontal,
                    end = TvDimensions.SafeMarginHorizontal,
                    top = 18.dp,
                    bottom = 14.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
        // Left: Clean Frosted Monochrome BitChord Logo
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (palette.isDark) Color.White.copy(alpha = 0.14f)
                        else Color.Black.copy(alpha = 0.08f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = "BitChord Logo",
                    tint = palette.textPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Text(
                text = "BitChord TV",
                fontSize = 20.sp,
                fontWeight = FontWeight.W800,
                fontFamily = LocalTvFontFamily.current,
                color = palette.textPrimary,
                letterSpacing = (-0.4).sp,
            )
        }

        // Center: Unified Pill Navigation Bar (Home | Library | Now Playing)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(26.dp))
                .background(
                    if (palette.isDark) Color.White.copy(alpha = 0.10f)
                    else Color.Black.copy(alpha = 0.06f)
                )
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val pillTabs = listOf(
                TvDestination.FOR_YOU,
                TvDestination.LIBRARY,
            )

            pillTabs.forEach { destination ->
                val isSelected = activeDestination == destination
                TvNavPillItem(
                    label = destination.label,
                    isSelected = isSelected,
                    onClick = { onDestinationSelected(destination) },
                )
            }

            // Now Playing Quick Tab
            TvNavPillItem(
                label = "Now Playing",
                isSelected = false,
                onClick = onOpenNowPlaying,
            )
        }

        // Right Action Group: Search and Settings buttons side-by-side
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Frosted Circular Search Button
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (activeDestination == TvDestination.SEARCH) {
                            if (palette.isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.18f)
                        } else {
                            if (palette.isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f)
                        }
                    )
                    .tvButtonFocus(
                        shape = CircleShape,
                        focusedScale = 1.15f,
                        focusedBorderColor = if (palette.isDark) Color.White else palette.accentRed,
                        borderWidth = 3.dp,
                        onClick = onOpenSearch,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = palette.textPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }

            // Frosted Circular Settings Button (Beside Search)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (activeDestination == TvDestination.SETTINGS) {
                            if (palette.isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.18f)
                        } else {
                            if (palette.isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f)
                        }
                    )
                    .tvButtonFocus(
                        shape = CircleShape,
                        focusedScale = 1.15f,
                        focusedBorderColor = if (palette.isDark) Color.White else palette.accentRed,
                        borderWidth = 3.dp,
                        onClick = onOpenSettings,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = palette.textPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
}

@Composable
private fun TvNavPillItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = TvThemeColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> if (palette.isDark) Color.White else palette.accentRed
            isSelected -> if (palette.isDark) Color.White.copy(alpha = 0.22f) else palette.surfaceSelected
            else -> Color.Transparent
        },
        animationSpec = appleSpring(AppleSpringPreset.Snappy),
        label = "navPillBg",
    )

    val textColor by animateColorAsState(
        targetValue = when {
            isFocused -> if (palette.isDark) Color.Black else Color.White
            isSelected -> if (palette.isDark) Color.White else palette.accentRed
            else -> palette.textSecondary
        },
        animationSpec = appleSpring(AppleSpringPreset.Snappy),
        label = "navPillText",
    )

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = appleSpring(AppleSpringPreset.Snappy),
        label = "navPillScale",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
            fontFamily = LocalTvFontFamily.current,
            color = textColor,
        )
    }
}

@Composable
private fun TvNavPillSearchItem(
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isSelected -> Color.White.copy(alpha = 0.22f)
            else -> Color.Transparent
        },
        animationSpec = appleSpring(AppleSpringPreset.Snappy),
        label = "navPillSearchBg",
    )

    val tintColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.Black
            isSelected -> Color.White
            else -> Color.White.copy(alpha = 0.65f)
        },
        animationSpec = appleSpring(AppleSpringPreset.Snappy),
        label = "navPillSearchTint",
    )

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = appleSpring(AppleSpringPreset.Snappy),
        label = "navPillSearchScale",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = tintColor,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun TvNavIconButton(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isSelected -> Color.White.copy(alpha = 0.22f)
            else -> Color.White.copy(alpha = 0.08f)
        },
        animationSpec = appleSpring(AppleSpringPreset.Snappy),
        label = "navIconBg",
    )

    val tintColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.Black
            isSelected -> Color.White
            else -> Color.White.copy(alpha = 0.75f)
        },
        animationSpec = appleSpring(AppleSpringPreset.Snappy),
        label = "navIconTint",
    )

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = appleSpring(AppleSpringPreset.Snappy),
        label = "navIconScale",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(38.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tintColor,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun TvGlobalMiniPlayer(
    playerState: PlayerState,
    mediaController: MediaController?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = playerState.song ?: return
    val isPlaying = playerState.isPlaying
    val palette = TvThemeColors.current

    Box(
        modifier = modifier
            .tvButtonFocus(
                shape = RoundedCornerShape(20.dp),
                focusedScale = 1.05f,
                focusedBorderColor = Color.White,
                borderWidth = 3.dp,
                onClick = onClick,
            ),
    ) {
        // Frosted Glass Blur Backdrop Layer (Ultra-optimized, sits behind controls)
        Box(
            modifier = Modifier
                .matchParentSize()
                .tvMiniPlayerBlur(shape = RoundedCornerShape(20.dp)),
        )

        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!song.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(song.thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        Column(modifier = Modifier.width(180.dp)) {
            Text(
                text = song.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = TvSFProDisplay,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                fontSize = 12.sp,
                fontFamily = TvSFProDisplay,
                color = Color.White.copy(alpha = 0.70f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable {
                    if (isPlaying) mediaController?.pause() else mediaController?.play()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
}
