package com.music.bitchord.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.session.MediaController
import com.music.bitchord.R
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.settings.TvSettings
import com.music.bitchord.playback.PlayerState
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.tv.components.LocalTvChrome
import com.music.bitchord.ui.tv.components.TvArtwork
import com.music.bitchord.ui.tv.components.TvChrome
import com.music.bitchord.ui.tv.components.TvTabIcon
import com.music.bitchord.ui.tv.components.TvTabPill
import com.music.bitchord.ui.tv.components.rememberDominantCardColor
import com.music.bitchord.ui.tv.components.tvClick
import com.music.bitchord.ui.tv.components.tvLift
import com.music.bitchord.ui.tv.dialogs.TvAccountDialog
import com.music.bitchord.ui.tv.player.TvNowPlayingScreen
import com.music.bitchord.ui.tv.screens.TvDetailScreen
import com.music.bitchord.ui.tv.screens.TvExploreScreen
import com.music.bitchord.ui.tv.screens.TvHomeScreen
import com.music.bitchord.ui.tv.screens.TvLibraryScreen
import com.music.bitchord.ui.tv.screens.TvSearchScreen
import com.music.bitchord.ui.tv.screens.TvSettingsScreen
import com.music.bitchord.ui.tv.theme.TvGlass
import com.music.bitchord.ui.tv.theme.TvThemeColors
import kotlinx.coroutines.delay

/** The tabs of the top bar. Now Playing is a tab too, but it opens the player rather than a page. */
enum class TvDestination(val label: String) {
    HOME("Home"),
    EXPLORE("Explore"),
    LIBRARY("Library"),
    SEARCH("Search"),
    SETTINGS("Settings"),
}

/** A pushed album, playlist or artist page. */
data class TvDetailRoute(
    val browseId: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val type: BrowseType,
)

typealias TvOpenDetail = (browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) -> Unit

@Composable
fun TvApp(
    viewModel: MainViewModel,
    mediaController: MediaController?,
    playerState: PlayerState,
    modifier: Modifier = Modifier,
) {
    val setupVersionCompleted by TvSettings.tvSetupVersionCompleted.collectAsState()
    var isRunningSetup by remember(setupVersionCompleted) { mutableStateOf(setupVersionCompleted == 0) }
    var isNowPlayingOpen by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }

    val spatialAudio by TvSettings.spatialAudioEnabled.collectAsState()
    LaunchedEffect(spatialAudio) {
        com.music.bitchord.ui.tv.audio.TvSpatialAudioEngine.setEnabled(spatialAudio)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .tvCanvas(playerState.song?.thumbnailUrl),
    ) {
        AnimatedContent(
            targetState = isRunningSetup,
            transitionSpec = { fadeIn(tween(220)).togetherWith(fadeOut(tween(160))) },
            label = "tvAppView",
            modifier = Modifier.fillMaxSize(),
        ) { setup ->
            if (setup) {
                com.music.bitchord.ui.tv.onboarding.TvSetupScreen(
                    viewModel = viewModel,
                    onComplete = { isRunningSetup = false },
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    // The pages stay alive under the player, so closing it comes
                    // back to the same tab, scroll position and focused card.
                    TvMainShell(
                        viewModel = viewModel,
                        mediaController = mediaController,
                        playerState = playerState,
                        covered = isNowPlayingOpen,
                        onOpenNowPlaying = { isNowPlayingOpen = true },
                        onOpenAccount = { showAccountDialog = true },
                        onRunSetupAgain = { isRunningSetup = true },
                    )
                    AnimatedVisibility(
                        visible = isNowPlayingOpen,
                        enter = scaleIn(initialScale = 0.94f, animationSpec = tween(260)) + fadeIn(tween(220)),
                        exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.97f, animationSpec = tween(180)),
                    ) {
                        // The player places its own focus (scrubber or stage).
                        Box(modifier = Modifier.fillMaxSize().focusGroup()) {
                            TvNowPlayingScreen(
                                viewModel = viewModel,
                                mediaController = mediaController,
                                playerState = playerState,
                                onBack = { isNowPlayingOpen = false },
                            )
                        }
                    }
                }
            }
        }

        if (showAccountDialog) {
            TvAccountDialog(viewModel = viewModel, onDismiss = { showAccountDialog = false })
        }
    }
}

/**
 * The screen-filling background: a soft top-to-bottom gradient with a wash of the
 * playing song's artwork colour across the top, as Apple Music tints its canvas.
 * Drawn behind everything in one pass; the tint animates in the draw phase.
 */
@Composable
private fun Modifier.tvCanvas(artworkUrl: String?): Modifier {
    val palette = TvThemeColors.current
    val tintSource = rememberDominantCardColor(
        artworkUrl = if (palette.tintsCanvas) artworkUrl else null,
        defaultColor = palette.canvasTop,
    )
    val tint = animateColorAsState(tintSource, tween(900), label = "canvasTint")
    val top = palette.canvasTop
    val bottom = palette.canvasBottom
    return drawBehind {
        drawRect(Brush.verticalGradient(listOf(top, bottom)))
        if (palette.tintsCanvas) {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(tint.value.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(size.width * 0.5f, -size.height * 0.15f),
                    radius = size.width * 0.75f,
                ),
            )
        }
    }
}

@Composable
private fun TvMainShell(
    viewModel: MainViewModel,
    mediaController: MediaController?,
    playerState: PlayerState,
    covered: Boolean,
    onOpenNowPlaying: () -> Unit,
    onOpenAccount: () -> Unit,
    onRunSetupAgain: () -> Unit,
) {
    val chrome = remember { TvChrome() }
    // What had focus when the player opened over this shell; it gets it back on close.
    var focusBeforePlayer by remember { mutableStateOf<FocusRequester?>(null) }
    val openNowPlaying: () -> Unit = {
        focusBeforePlayer = chrome.lastFocused
        onOpenNowPlaying()
    }
    val stateHolder = rememberSaveableStateHolder()
    var destination by remember { mutableStateOf(TvDestination.HOME) }
    val detailStack = remember { mutableStateListOf<TvDetailRoute>() }
    val tabFocus = remember { FocusRequester() }
    var barHasFocus by remember { mutableStateOf(false) }
    // The card each pushed page was opened from, so Back can hand focus back to it.
    val returnFocus = remember { mutableListOf<FocusRequester?>() }

    val select: (TvDestination) -> Unit = { next ->
        if (detailStack.isNotEmpty()) {
            detailStack.clear()
            returnFocus.clear()
            viewModel.clearDetail()
        }
        if (next != destination) {
            chrome.contentScrolled = false
            destination = next
        }
    }
    val openDetail: TvOpenDetail = { browseId, title, subtitle, thumb, type ->
        returnFocus.add(chrome.lastFocused)
        detailStack.add(TvDetailRoute(browseId, title, subtitle, thumb, type))
    }
    val activeDetail = detailStack.lastOrNull()
    val contentFocus = remember { FocusRequester() }
    val detailFocus = remember { FocusRequester() }
    var pendingReturn by remember { mutableStateOf<FocusRequester?>(null) }
    val popDetail: () -> Unit = {
        detailStack.removeAt(detailStack.lastIndex)
        pendingReturn = returnFocus.removeLastOrNull()
        chrome.epoch++
        viewModel.closeDetail()
    }
    // Leaving the last pushed page: put focus back on the card that opened it.
    var hadDetail by remember { mutableStateOf(false) }
    LaunchedEffect(activeDetail == null) {
        if (activeDetail != null) {
            hadDetail = true
            barHasFocus = false
        } else if (hadDetail) {
            hadDetail = false
            delay(16)
            val back = pendingReturn
            pendingReturn = null
            if (back == null || !runCatching { back.requestFocus() }.getOrDefault(false)) {
                runCatching { contentFocus.requestFocus() }
            }
        }
    }

    // tvOS Back: pop a pushed page; otherwise go back up to the tab bar; from
    // there, back to Home; from Home's tab, leave the app.
    BackHandler(enabled = !covered && (activeDetail != null || !barHasFocus || destination != TvDestination.HOME)) {
        when {
            activeDetail != null -> popDetail()
            !barHasFocus -> {
                chrome.scrollToTopRequests++
                runCatching { tabFocus.requestFocus() }
            }
            else -> select(TvDestination.HOME)
        }
    }

    LaunchedEffect(Unit) {
        delay(60)
        runCatching { tabFocus.requestFocus() }
    }
    // The tab bar only ever sits over a page at its top: taking focus sends the
    // page home, rather than leaving the bar floating over scrolled content.
    LaunchedEffect(barHasFocus) {
        if (barHasFocus) chrome.scrollToTopRequests++
    }
    var wasCovered by remember { mutableStateOf(false) }
    LaunchedEffect(covered) {
        if (covered) {
            wasCovered = true
        } else if (wasCovered) {
            wasCovered = false
            delay(40)
            val back = focusBeforePlayer
            focusBeforePlayer = null
            if (back == null || !runCatching { back.requestFocus() }.getOrDefault(false)) {
                runCatching { tabFocus.requestFocus() }
            }
        }
    }

    // The bar floats over pushed pages too, stepping aside as they scroll.
    val barVisible = !chrome.contentScrolled || barHasFocus
    val barProgress by animateFloatAsState(if (barVisible) 1f else 0f, tween(260), label = "barProgress")

    CompositionLocalProvider(LocalTvChrome provides chrome) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (covered) 0f else 1f }
                .focusProperties { onEnter = { if (covered) cancelFocusChange() } }
                .focusGroup(),
        ) {
            // The tab's page stays composed under a pushed page — not drawn, and
            // fenced off from focus — so Back lands on the card that pushed it.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (activeDetail == null) 1f else 0f }
                    .focusProperties { onEnter = { if (detailStack.isNotEmpty()) cancelFocusChange() } }
                    .focusGroup(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Down from the tab bar enters the page at its top, never at
                        // a row remembered from further down.
                        .focusRequester(contentFocus)
                        .focusGroup(),
                ) {
                    stateHolder.SaveableStateProvider(destination.name) {
                        when (destination) {
                            TvDestination.HOME -> TvHomeScreen(
                                viewModel = viewModel,
                                mediaController = mediaController,
                                onNavigateToDetail = openDetail,
                                onNavigateToNowPlaying = openNowPlaying,
                            )
                            TvDestination.EXPLORE -> TvExploreScreen(
                                viewModel = viewModel,
                                mediaController = mediaController,
                                onNavigateToDetail = openDetail,
                                onNavigateToNowPlaying = openNowPlaying,
                            )
                            TvDestination.LIBRARY -> TvLibraryScreen(
                                viewModel = viewModel,
                                mediaController = mediaController,
                                playerState = playerState,
                                onNavigateToDetail = openDetail,
                                onNavigateToNowPlaying = openNowPlaying,
                                onOpenAccount = onOpenAccount,
                            )
                            TvDestination.SEARCH -> TvSearchScreen(
                                viewModel = viewModel,
                                mediaController = mediaController,
                                onNavigateToDetail = openDetail,
                                onNavigateToNowPlaying = openNowPlaying,
                            )
                            TvDestination.SETTINGS -> TvSettingsScreen(
                                viewModel = viewModel,
                                onOpenAccount = onOpenAccount,
                                onRunSetupAgain = onRunSetupAgain,
                            )
                        }
                    }
                }
            }

            if (activeDetail != null) {
                androidx.compose.runtime.key(activeDetail) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(detailFocus)
                            .focusGroup(),
                    ) {
                    TvDetailScreen(
                        browseId = activeDetail.browseId,
                        initialTitle = activeDetail.title,
                        initialSubtitle = activeDetail.subtitle,
                        initialThumbnailUrl = activeDetail.thumbnailUrl,
                        type = activeDetail.type,
                        viewModel = viewModel,
                        mediaController = mediaController,
                        playerState = playerState,
                        onNavigateToDetail = openDetail,
                        onNavigateToNowPlaying = openNowPlaying,
                        onBack = popDetail,
                    )
                    }
                }
            }

            TvTopBar(
                destination = destination,
                viewModel = viewModel,
                tabFocus = tabFocus,
                // Down from the bar goes into whatever page is on top.
                contentFocus = if (activeDetail != null) detailFocus else contentFocus,
                onSelect = select,
                onOpenNowPlaying = openNowPlaying,
                onOpenAccount = onOpenAccount,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(10f)
                    .graphicsLayer {
                        alpha = barProgress
                        translationY = -(1f - barProgress) * 90.dp.toPx()
                    }
                    .onFocusChanged { barHasFocus = it.hasFocus },
            )
        }
    }
}

/**
 * The Apple Music tvOS tab bar: a translucent capsule centred at the top holding
 * the four tabs and, at its end, Search and Settings. Home, Explore and Library
 * switch as focus lands on them, the way tvOS tabs do; the rest act on select.
 */
@Composable
private fun TvTopBar(
    destination: TvDestination,
    viewModel: MainViewModel,
    tabFocus: FocusRequester,
    contentFocus: FocusRequester,
    onSelect: (TvDestination) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val account by viewModel.account.collectAsState()
    val signedIn by viewModel.signedIn.collectAsState()
    // Pages fill the screen from its top edge so they can scroll under this bar,
    // which puts them outside a plain "below" search from a tab. Point Down at them.
    val downToContent = Modifier.focusProperties { down = contentFocus }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 22.dp, start = 48.dp, end = 48.dp),
    ) {
        // The mark only, as the phone's Home heading shows it.
        Icon(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = "BitChord",
            tint = TvGlass.TextPrimary,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .height(26.dp)
                .aspectRatio(730f / 484f),
        )
        TvAvatarButton(
            imageUrl = if (signedIn) account?.thumbnailUrl else null,
            onClick = onOpenAccount,
            modifier = Modifier.align(Alignment.CenterEnd).then(downToContent),
        )
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(TvGlass.Bar)
                .padding(5.dp)
                .focusRestorer(tabFocus)
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(TvDestination.HOME, TvDestination.EXPLORE, TvDestination.LIBRARY).forEach { tab ->
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                LaunchedEffect(focused) {
                    if (focused && destination != tab) {
                        delay(140)
                        onSelect(tab)
                    }
                }
                TvTabPill(
                    label = tab.label,
                    selected = destination == tab,
                    interactionSource = interaction,
                    onClick = { onSelect(tab) },
                    modifier = downToContent.then(if (destination == tab) Modifier.focusRequester(tabFocus) else Modifier),
                )
            }
            TvTabPill(
                label = "Now Playing",
                selected = false,
                modifier = downToContent,
                onClick = onOpenNowPlaying,
            )
            TvTabIcon(
                icon = Icons.Rounded.Search,
                contentDescription = "Search",
                selected = destination == TvDestination.SEARCH,
                onClick = { onSelect(TvDestination.SEARCH) },
                modifier = downToContent.then(if (destination == TvDestination.SEARCH) Modifier.focusRequester(tabFocus) else Modifier),
            )
            TvTabIcon(
                icon = Icons.Rounded.Settings,
                contentDescription = "Settings",
                selected = destination == TvDestination.SETTINGS,
                onClick = { onSelect(TvDestination.SETTINGS) },
                modifier = downToContent.then(if (destination == TvDestination.SETTINGS) Modifier.focusRequester(tabFocus) else Modifier),
            )
        }
    }
}

/** The account's picture at the bar's leading edge; opens Account. */
@Composable
private fun TvAvatarButton(
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(40.dp)
            .tvLift(interaction, CircleShape, focusedScale = 1.15f, elevation = 10.dp)
            .tvClick(interaction, onClick = onClick)
            .background(TvGlass.Fill),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl.isNullOrBlank()) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = "Account",
                tint = TvGlass.TextSecondary,
                modifier = Modifier.size(22.dp),
            )
        } else {
            TvArtwork(url = imageUrl, px = 120, shape = CircleShape, modifier = Modifier.fillMaxSize())
        }
    }
}

/** The BitChord mark, for the few places that show the app itself. */
@Composable
fun TvAppMark(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 120.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(size * 0.225f))
            .background(
                Brush.linearGradient(listOf(Color(0xFFFF5468), Color(0xFFE3173A))),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = "BitChord",
            tint = Color.White,
            modifier = Modifier.size(size * 0.52f),
        )
    }
}
