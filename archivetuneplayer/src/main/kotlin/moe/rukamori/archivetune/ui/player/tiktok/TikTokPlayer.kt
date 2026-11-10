/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player.tiktok

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import moe.rukamori.archivetune.ui.utils.resize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.LocalStableSystemBarsTopPadding
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.PlatformBackdrop
import moe.rukamori.archivetune.ui.component.rememberLiquidGlassEnabled
import moe.rukamori.archivetune.ui.component.layerBackdrop
import moe.rukamori.archivetune.ui.component.rememberBackdrop
import moe.rukamori.archivetune.ui.menu.AnchoredLyricsOverflowMenu
import moe.rukamori.archivetune.ui.player.AppleMusicQueueSheet
import moe.rukamori.archivetune.ui.player.LocalVideoArtworkState
import moe.rukamori.archivetune.ui.player.LocalVideoFullscreenState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal val TIKTOK_TOP_NAV_HEIGHT = 44.dp

private val TIKTOK_QUEUE_FEED_BLUR = 72.dp

@Composable
fun TikTokPlayerContent(
    mediaMetadata: MediaMetadata,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    menuState: MenuState,
    bottomSheetPageState: BottomSheetPageState,
    lyricsSyncOffset: Int = 0,
    onLyricsSyncOffsetChange: (Int) -> Unit = {},

    canvasPrimaryUrl: String? = null,
    canvasFallbackUrl: String? = null,
    onSeek: (Long) -> Unit = {},
    onSeekFinished: () -> Unit = {},
    externalBackRequests: androidx.compose.runtime.MutableIntState? = null,
    onStylePageOpenChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val player = playerConnection.player
    val haptics = LocalHapticFeedback.current

    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle()
    val queueTitle by playerConnection.queueTitle.collectAsStateWithLifecycle(initialValue = null)

    val pagerState =
        rememberPagerState(
            initialPage = currentWindowIndex.coerceAtLeast(0),
        ) { queueWindows.size }

    var pendingSeekTarget by remember { mutableStateOf<Int?>(null) }

    var autoAdvancing by remember { mutableStateOf(false) }

    val skipInvoker =
        remember(playerConnection, canSkipNext, canSkipPrevious) {
            { target: Int, from: Int ->
                when (target) {
                    from -> Unit
                    from + 1 -> if (canSkipNext) playerConnection.seekToNext()
                    from - 1 -> if (canSkipPrevious) playerConnection.seekToPrevious()
                    else -> if (target in 0 until queueWindows.size) player.seekTo(target, 0)
                }
                Unit
            }
        }

    val currentWindowIndexState = rememberUpdatedState(currentWindowIndex)
    val queueWindowsState = rememberUpdatedState(queueWindows)
    val skipInvokerState = rememberUpdatedState(skipInvoker)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settled ->
                val liveIndex = currentWindowIndexState.value
                val liveQueue = queueWindowsState.value
                if (liveQueue.isNotEmpty() &&
                    settled != liveIndex &&
                    settled in 0 until liveQueue.size
                ) {
                    pendingSeekTarget = settled
                    skipInvokerState.value(settled, liveIndex)
                }
            }
    }

    LaunchedEffect(currentWindowIndex, queueWindows.size) {
        if (queueWindows.isEmpty()) return@LaunchedEffect
        val pending = pendingSeekTarget
        if (pending != null) {
            if (currentWindowIndex == pending) pendingSeekTarget = null
            return@LaunchedEffect
        }
        if (currentWindowIndex !in 0 until queueWindows.size) return@LaunchedEffect
        if (pagerState.currentPage == currentWindowIndex) return@LaunchedEffect
        if (pagerState.isScrollInProgress) {
            snapshotFlow { pagerState.isScrollInProgress }.first { !it }
            if (pagerState.currentPage == currentWindowIndex) return@LaunchedEffect
        }
        val delta = currentWindowIndex - pagerState.currentPage
        if (delta == 1 || delta == -1) {
            autoAdvancing = true
            try {
                pagerState.animateScrollToPage(currentWindowIndex)
            } finally {
                autoAdvancing = false
            }
        } else {
            pagerState.scrollToPage(currentWindowIndex)
        }
    }

    val prefetchContext = LocalContext.current
    LaunchedEffect(currentWindowIndex, queueWindows) {
        val imageLoader = prefetchContext.imageLoader
        for (page in (currentWindowIndex + 1)..minOf(currentWindowIndex + 2, queueWindows.lastIndex)) {
            val metadata = queueWindows.getOrNull(page)?.mediaItem?.metadata ?: continue
            val url = metadata.thumbnailUrl ?: continue
            val sized = url.resize(
                width = TIKTOK_ART_PX,
                height = TIKTOK_ART_PX,
                maxresAllowed = true,
            )
            imageLoader.enqueue(
                ImageRequest
                    .Builder(prefetchContext)
                    .data(sized)
                    .memoryCacheKey(sized)
                    .diskCacheKey(sized)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build(),
            )
        }
    }

    LaunchedEffect(pendingSeekTarget) {
        val pending = pendingSeekTarget ?: return@LaunchedEffect
        delay(PENDING_SEEK_TIMEOUT_MS)
        if (pendingSeekTarget == pending) {
            pendingSeekTarget = null
            val idx = currentWindowIndex
            if (queueWindows.isNotEmpty() &&
                idx in 0 until queueWindows.size &&
                pagerState.currentPage != idx &&
                !pagerState.isScrollInProgress
            ) {
                pagerState.scrollToPage(idx)
            }
        }
    }

    var lastHapticIndex by remember { mutableStateOf(currentWindowIndex) }
    LaunchedEffect(currentWindowIndex) {
        if (currentWindowIndex != lastHapticIndex) {
            lastHapticIndex = currentWindowIndex
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    var lyricsOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(mediaMetadata.id) { lyricsOpen = false }

    var queueOpen by rememberSaveable { mutableStateOf(false) }

    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    var showLyricsMenu by remember { mutableStateOf(false) }
    LaunchedEffect(lyricsOpen) {
        if (!lyricsOpen) showLyricsMenu = false
    }

    val videoFullscreenHolder = LocalVideoFullscreenState.current
    val videoState = LocalVideoArtworkState.current
    val videoControlsShowing =
        videoState != null && !videoState.hasPlaybackFailed && !lyricsOpen
    var immersive by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = lyricsOpen) { lyricsOpen = false }
    BackHandler(enabled = immersive) { immersive = false }

    BackHandler(enabled = queueOpen) { queueOpen = false }

    LaunchedEffect(state.isExpandedOrExpanding) {
        if (!state.isExpandedOrExpanding) {
            if (immersive) immersive = false
            if (lyricsOpen) lyricsOpen = false
            if (queueOpen) queueOpen = false
        }
    }
    val tiktokStylePageOpen = lyricsOpen || queueOpen || immersive
    LaunchedEffect(tiktokStylePageOpen) { onStylePageOpenChange(tiktokStylePageOpen) }
    val externalBackEpoch = externalBackRequests?.intValue ?: 0
    LaunchedEffect(externalBackEpoch) {
        if (externalBackEpoch > 0) {
            if (lyricsOpen) lyricsOpen = false
            if (queueOpen) queueOpen = false
            if (immersive) immersive = false
        }
    }
    val onFullscreenAction =
        remember(videoState, videoFullscreenHolder) {
            {
                if (videoState != null) {
                    videoFullscreenHolder.isFullscreen = true
                } else {
                    immersive = !immersive
                    if (immersive) {
                        lyricsOpen = false
                        queueOpen = false
                    }
                }
            }
        }

    val stableTopInset = LocalStableSystemBarsTopPadding.current
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topChromeHeight = stableTopInset + TIKTOK_TOP_NAV_HEIGHT + 4.dp
    val bottomChromeHeight = TIKTOK_PROGRESS_ROW_HEIGHT + navigationBarInset

    var addToPlaylistSong by remember { mutableStateOf<MediaMetadata?>(null) }

    val displayPositionMs = sliderPosition ?: position

    val sliderPositionState = rememberUpdatedState(sliderPosition)
    val lyricsPosProvider = remember { { sliderPositionState.value } }

    val canBlurFeed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val feedBlur by animateDpAsState(
        targetValue = if (queueOpen && canBlurFeed) TIKTOK_QUEUE_FEED_BLUR else 0.dp,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "tiktokQueueFeedBlur",
    )

    val popupBackdrop: PlatformBackdrop? =
        if (rememberLiquidGlassEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rememberBackdrop(Color.Transparent)
        } else {
            null
        }

    var rootBounds by remember { mutableStateOf(Rect.Zero) }

    var lyricsOverflowAnchor by remember { mutableStateOf(Rect.Zero) }
    val lyricsPopupAnchor =
        remember(lyricsOverflowAnchor, rootBounds) {
            Rect(
                left = lyricsOverflowAnchor.left - rootBounds.left,
                top = lyricsOverflowAnchor.top - rootBounds.top,
                right = lyricsOverflowAnchor.right - rootBounds.left,
                bottom = lyricsOverflowAnchor.bottom - rootBounds.top,
            )
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onGloballyPositioned { rootBounds = it.boundsInRoot() },
    ) {
        VerticalPager(
            state = pagerState,

            modifier =
                Modifier
                    .fillMaxSize()
                    .let { base ->
                        if (feedBlur > 0.dp) base.blur(feedBlur) else base
                    }
                    .let { base ->
                        if (popupBackdrop != null && lyricsOpen && showLyricsMenu) {
                            base.layerBackdrop(popupBackdrop)
                        } else {
                            base
                        }
                    },

            beyondViewportPageCount = 1,
            pageSpacing = 0.dp,
        ) { page ->
            val window = queueWindows.getOrNull(page)
            val isCurrentPage = page == currentWindowIndex
            if (window == null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .tiktokScrim(),
                )
                return@VerticalPager
            }

            val pageMetadata =
                if (isCurrentPage) {
                    mediaMetadata
                } else {
                    window.mediaItem.metadata
                        ?: MediaMetadata(
                            id = window.mediaItem.mediaId,
                            title = "",
                            artists = emptyList(),
                            duration = -1,
                        )
                }
            TikTokSongPage(
                pageMetadata = pageMetadata,
                isCurrentPage = isCurrentPage,
                isPlaying = isPlaying,

                suppressPauseOverlay = isLoading || autoAdvancing,
                playerConnection = playerConnection,
                queueTitle = queueTitle,
                immersive = immersive,
                lyricsOpen = lyricsOpen,

                canvasPrimaryUrl = if (isCurrentPage) canvasPrimaryUrl else null,
                canvasFallbackUrl = if (isCurrentPage) canvasFallbackUrl else null,
                sliderPositionProvider = lyricsPosProvider,
                lyricsSyncOffset = lyricsSyncOffset,
                topChromeHeight = topChromeHeight,
                bottomChromeHeight = bottomChromeHeight,
                sheetState = state,
                onAddToPlaylist = { addToPlaylistSong = pageMetadata },
                onToggleLyrics = { lyricsOpen = !lyricsOpen },
                onTogglePlayPause = { player.togglePlayPause() },

                onQueueClick = {
                    if (lyricsOpen) lyricsOpen = false
                    queueOpen = true
                },
                onOpenLyricsMenu = { showLyricsMenu = true },
                onLyricsOverflowAnchorChange = { lyricsOverflowAnchor = it },
                navController = navController,
                menuState = menuState,
                bottomSheetPageState = bottomSheetPageState,
            )
        }

        AnimatedVisibility(
            visible = queueOpen,
            enter =
                slideInVertically(
                    animationSpec = tween(600, easing = FastOutSlowInEasing),
                ) { it / 4 } + fadeIn(tween(600)),
            exit =
                fadeOut(tween(400)) +
                    slideOutVertically(
                        animationSpec = tween(400, easing = FastOutSlowInEasing),
                    ) { it / 4 },
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()

                        .background(
                            Color.Black.copy(
                                alpha = if (canBlurFeed) 0.35f else 0.55f,
                            ),
                        )
                        .padding(top = topChromeHeight, bottom = bottomChromeHeight),
            ) {
                AppleMusicQueueSheet(
                    navController = navController,
                    playerBottomSheetState = state,
                    onClose = { queueOpen = false },
                )
            }
        }

        AnimatedVisibility(
            visible = !immersive,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
        ) {
            TikTokTopNavigation(
                navController = navController,
                state = state,
                isLoading = isLoading,
                onFullscreen = onFullscreenAction,
                showFullscreenButton = !videoControlsShowing,
            )
        }

        AnimatedVisibility(
            visible = !immersive,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.Black.copy(alpha = 0f),
                                    Color.Black.copy(alpha = 0.45f),
                                    Color.Black,
                                ),
                        ),
                    ),
        ) {
            TikTokBottomChrome(
                displayPositionMs = displayPositionMs,
                durationMs = duration,
                onSeek = onSeek,
                onSeekFinished = onSeekFinished,
            )
        }

        if (immersive) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = stableTopInset + 6.dp, end = 12.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .tiktokNoRippleClickable(onClick = { immersive = false }),
            ) {
                Icon(
                    painter = painterResource(R.drawable.solar_fullscreen_exit_linear),
                    contentDescription = stringResource(R.string.tiktok_feed_exit_fullscreen),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        if (lyricsOpen && showLyricsMenu) {
            AnchoredLyricsOverflowMenu(
                iconBoundsInRoot = lyricsPopupAnchor,
                lyricsProvider = { currentLyrics },
                mediaMetadataProvider = { mediaMetadata },
                lyricsSyncOffset = lyricsSyncOffset,
                onLyricsSyncOffsetChange = onLyricsSyncOffsetChange,
                onDismiss = { showLyricsMenu = false },
                backdrop = popupBackdrop,
            )
        }
    }

    addToPlaylistSong?.let { song ->
        TikTokAddToPlaylist(
            song = song,
            onDismiss = { addToPlaylistSong = null },
        )
    }
}

@Composable
private fun TikTokTopNavigation(
    navController: NavController,
    state: BottomSheetState,
    isLoading: Boolean,
    onFullscreen: () -> Unit,
    showFullscreenButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val stableTopInset = LocalStableSystemBarsTopPadding.current
    val tabs =
        remember(context) {
            listOf(
                TikTokFeedTab(
                    label = context.getString(R.string.home),
                    route = "home",
                ),
                TikTokFeedTab(
                    label = context.getString(R.string.filter_library),
                    route = "library",
                ),
            )
        }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val selectedRoutes =
        remember(navBackStackEntry) {
            navBackStackEntry?.destination?.hierarchy?.map { it.route }?.toSet()
                ?: emptySet<String>()
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = stableTopInset)
                .height(TIKTOK_TOP_NAV_HEIGHT)
                .padding(horizontal = 6.dp),
    ) {
        if (showFullscreenButton) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .tiktokNoRippleClickable(onClick = onFullscreen),
            ) {
                Icon(
                    painter = painterResource(R.drawable.solar_fullscreen_linear),
                    contentDescription = stringResource(R.string.tiktok_feed_fullscreen),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        } else {
            Spacer(Modifier.size(40.dp))
        }

        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { tab ->
                val isSelected = tab.route in selectedRoutes
                val onTabClick =
                    remember(navController, state, tab) {
                        {
                            state.collapseSoft()
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .tiktokNoRippleClickable(onClick = onTabClick)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = tab.label,
                        color = if (isSelected) Color.White else TIKTOK_INACTIVE_GRAY,
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier =
                            Modifier
                                .width(22.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(
                                    if (isSelected) Color.White else Color.Transparent,
                                ),
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .tiktokNoRippleClickable(
                        onClick =
                            remember(navController, state) {
                                {
                                    state.collapseSoft()
                                    navController.navigate("search") {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                    ),
        ) {
            AnimatedContent(
                targetState = isLoading,
                transitionSpec = {
                    fadeIn(tween(160)) togetherWith fadeOut(tween(160))
                },
                contentAlignment = Alignment.Center,
                label = "tiktokSearchLoadingSlot",
            ) { loading ->
                if (loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.solar_magnifer_linear),
                        contentDescription = stringResource(R.string.search),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

private data class TikTokFeedTab(
    val label: String,
    val route: String,
)

@Composable
internal fun Modifier.tiktokNoRippleClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.then(
        if (enabled) {
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
        } else {
            Modifier
        },
    )
}

private const val PENDING_SEEK_TIMEOUT_MS = 1_500L
