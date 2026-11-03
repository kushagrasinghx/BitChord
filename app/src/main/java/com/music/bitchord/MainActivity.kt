package com.music.bitchord

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Upgrade
import com.music.bitchord.data.listentogether.ServerConnectionState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.music.bitchord.auth.DiscordLoginScreen
import com.music.bitchord.auth.SpotifyLoginScreen
import com.music.bitchord.auth.WebSessionMode
import com.music.bitchord.auth.YtMusicLoginScreen
import com.music.bitchord.data.AppUpdateChecker
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.listentogether.JamInviteLink
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.listentogether.partyQueueIndexOf
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.isUnresolvedSpotify
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.data.model.EntityType
import com.music.bitchord.data.model.SearchHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.BitPerfectMode
import com.music.bitchord.ui.AddonBlockedAlert
import com.music.bitchord.data.settings.LibrarySort
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.components.AccountProfileSelector
import com.music.bitchord.ui.components.DacVolumeHud
import com.music.bitchord.ui.components.DacVolumeKeys
import com.music.bitchord.ui.components.LocalPlayingBarsMotion
import com.music.bitchord.ui.components.handleDacVolumeKey
import com.music.bitchord.ui.components.VolumeHud
import com.music.bitchord.ui.navigation.AppDestination
import com.music.bitchord.ui.navigation.AppRoute
import com.music.bitchord.ui.screens.EqualizerPresetPage
import com.music.bitchord.ui.screens.SettingsChoicePage
import com.music.bitchord.ui.components.SpotifyImportAlert
import com.music.bitchord.ui.screens.WearOsScreen
import com.music.bitchord.ui.screens.AccountAndScrobblingScreen
import com.music.bitchord.ui.screens.DiscordDialog
import com.music.bitchord.ui.screens.DiscordDialogHost
import com.music.bitchord.ui.screens.DiscordScreen
import com.music.bitchord.ui.screens.EqualizerScreen
import com.music.bitchord.ui.screens.HistoryScreen
import com.music.bitchord.ui.screens.LibraryReplayEntry
import com.music.bitchord.ui.screens.libraryDeviceItems
import com.music.bitchord.ui.screens.SPOTIFY_BROWSE_ID
import com.music.bitchord.ui.screens.libraryLinks
import com.music.bitchord.ui.screens.CACHE_FOLDER_BROWSE_ID
import com.music.bitchord.ui.screens.ListenTogetherScreen
import com.music.bitchord.ui.screens.PartyServerEditor
import com.music.bitchord.ui.screens.SignatureApiEditor
import com.music.bitchord.ui.screens.SettingsScreen
import com.music.bitchord.ui.screens.SourceEditorAlert
import com.music.bitchord.ui.screens.SourcesScreen
import com.music.bitchord.data.sources.TrackMatcher
import com.music.bitchord.ui.screens.SpotifyCanvasAuthScreen
import com.music.bitchord.ui.screens.SpotifyLibraryScreen
import com.music.bitchord.data.spotify.SPOTIFY_PAGE_PREFIX
import com.music.bitchord.playback.AudioCache
import com.music.bitchord.playback.LinkRequest
import com.music.bitchord.playback.MusicLink
import com.music.bitchord.playback.OriginalVersion
import com.music.bitchord.playback.PlayerDeepLink
import com.music.bitchord.playback.QueueBuilder
import com.music.bitchord.playback.QueueCoordinator
import com.music.bitchord.playback.QueueCoordinator.asQueueEntry
import com.music.bitchord.playback.QueueShuffle
import com.music.bitchord.playback.QueueSource
import com.music.bitchord.playback.cast.CastController
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.playback.autoplayEnabledFor
import com.music.bitchord.playback.autoplaySectionStart
import com.music.bitchord.playback.beginRadioQueue
import com.music.bitchord.playback.commitRadioQueue
import com.music.bitchord.playback.fromAutoplay
import com.music.bitchord.playback.hasYouTubeOriginal
import com.music.bitchord.playback.loadAutoplayTracks
import com.music.bitchord.playback.playSongs
import com.music.bitchord.playback.BoundedQueuePlanner
import com.music.bitchord.playback.playBoundedContext
import com.music.bitchord.playback.contextEntryKey
import com.music.bitchord.playback.toMediaItem
import com.music.bitchord.playback.toSong
import com.music.bitchord.playback.toDirectYouTubeMediaItem
import com.music.bitchord.playback.toggleAutoplay
import com.music.bitchord.playback.toggleShuffle
import com.music.bitchord.playback.upgradeQuality
import com.music.bitchord.playback.revertToOriginal
import com.music.bitchord.playback.swapToVersion
import com.music.bitchord.playback.smart.VersionAudioAligner
import com.music.bitchord.download.DownloadSession
import com.music.bitchord.download.DownloadStore
import com.music.bitchord.download.MediaTagger
import com.music.bitchord.download.DownloadTarget
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.components.BrowseActionsMenu
import com.music.bitchord.ui.components.BrowseTarget
import com.music.bitchord.ui.components.ConfirmationAlert
import com.music.bitchord.ui.components.InfoAlert
import com.music.bitchord.ui.components.DownloadManagerPage
import com.music.bitchord.ui.components.PlaylistPickerSheet
import com.music.bitchord.ui.components.ReorderPlaylistPage
import com.music.bitchord.ui.components.LongPressOrigin
import com.music.bitchord.ui.components.MenuAnchor
import com.music.bitchord.ui.components.menuAnchor
import com.music.bitchord.ui.components.IosMenuPopover
import com.music.bitchord.ui.components.SongActionsMenu
import com.music.bitchord.ui.components.HeldContextMenu
import com.music.bitchord.ui.components.HeldItem
import androidx.media3.session.MediaController
import com.music.bitchord.playback.QualityUpgrade
import com.music.bitchord.playback.rememberMediaController
import com.music.bitchord.playback.rememberPlayerState
import com.music.bitchord.playback.setQueueDragActive
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.SearchSource
import com.music.bitchord.ui.components.BottomFadeScrim
import com.music.bitchord.ui.components.TopFadeBlur
import com.music.bitchord.ui.components.FloatingBarsTapGuard
import com.music.bitchord.ui.components.BottomTab
import com.music.bitchord.ui.components.FLOATING_BAR_MAX_WIDTH
import com.music.bitchord.ui.components.FloatingBottomBar
import com.music.bitchord.ui.components.GlassNavBar
import com.music.bitchord.ui.components.floatingtabbar.rememberFloatingTabBarScrollConnection
import com.music.bitchord.ui.components.FrostedTopBar
import com.music.bitchord.ui.components.LastfmLoginAlert
import com.music.bitchord.ui.components.LocalAppBackdrop
import com.music.bitchord.ui.components.LocalLiquidGlassEnabled
import com.music.bitchord.ui.components.backdrop.backdrops.LayerBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.layerBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.rememberLayerBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.rememberSnapshotBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.snapshotBackdropSource
import com.music.bitchord.ui.components.isGlassSupported
import com.music.bitchord.data.sources.SourceConfig
import com.music.bitchord.data.sources.SourceKind
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.ui.components.ListenBrainzTokenAlert
import com.music.bitchord.ui.components.MiniPlayer
import com.music.bitchord.ui.components.QueueActionNotice
import com.music.bitchord.ui.components.QueueActionNoticeHost
import com.music.bitchord.ui.components.TopBarAccountButton
import com.music.bitchord.ui.screens.SegmentedControl
import com.music.bitchord.ui.components.SearchField
import com.music.bitchord.sharedui.resources.Res as SharedRes
import com.music.bitchord.sharedui.resources.search_hint
import com.music.bitchord.sharedui.resources.search_addons_hint
import com.music.bitchord.sharedui.resources.search_library_hint
import com.music.bitchord.ui.components.TopBarDownloadButton
import com.music.bitchord.ui.components.optimizedHazeEffect
import com.music.bitchord.ui.components.topBarContentPadding
import com.music.bitchord.ui.components.topBarHeight
import com.music.bitchord.ui.components.AppLanguageDialog
import com.music.bitchord.ui.components.TranslationLanguageDialog
import com.music.bitchord.ui.components.LyricsSourcesDialog
import com.music.bitchord.ui.components.ServerEditorHost
import com.music.bitchord.ui.components.UpdateAvailableDialog
import com.music.bitchord.ui.components.WebDavConflictAlert
import com.music.bitchord.ui.components.FieldConfig
import com.music.bitchord.ui.icons.BitChordIcons
import androidx.media3.common.Player
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.TopPicks
import com.music.bitchord.data.model.TopPick
import com.music.bitchord.data.model.TopPickKind
import com.music.bitchord.data.model.TopPickMood
import com.music.bitchord.ui.screens.MoodGridPage
import com.music.bitchord.ui.player.NowPlayingScreen
import com.music.bitchord.ui.player.MusicVideoEngine
import com.music.bitchord.ui.player.PlayerSheetMotion
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import com.music.bitchord.ui.components.MiniPlayerPull
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.layout.onSizeChanged
import com.music.bitchord.ui.player.LocalPlayerDock
import com.music.bitchord.ui.player.PlayerDock
import com.music.bitchord.ui.screens.DetailScreen
import com.music.bitchord.ui.screens.sortedForDetail
import com.music.bitchord.ui.screens.ExploreScreen
import com.music.bitchord.ui.screens.LocalFolder
import com.music.bitchord.ui.screens.LocalFolderState
import com.music.bitchord.ui.screens.LocalFolderTab
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.vector.ImageVector
import com.music.bitchord.ui.screens.localizedLabel
import com.music.bitchord.data.LocalGroups
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.data.toGroup
import com.music.bitchord.data.settings.LibraryViewType
import com.music.bitchord.data.settings.LocalMusicSort
import com.music.bitchord.ui.screens.HomeScreen
import com.music.bitchord.ui.screens.LibraryGridPage
import com.music.bitchord.ui.screens.LibraryScreen
import com.music.bitchord.ui.screens.MoodGenrePlaylistsScreen
import com.music.bitchord.ui.screens.SearchScreen
import com.music.bitchord.data.settings.SongSort
import com.music.bitchord.ui.onboarding.OnboardingScreen
import com.music.bitchord.ui.replay.ReplayScreen
import com.music.bitchord.ui.replay.cards
import com.music.bitchord.ui.replay.ReplayShareSheet
import com.music.bitchord.ui.replay.ReplayStories
import com.music.bitchord.ui.replay.ReplayStoryPage
import com.music.bitchord.ui.replay.rememberReplayState
import com.music.bitchord.ui.theme.BitChordTheme
import com.music.bitchord.ui.theme.rememberArtworkPalette
import com.music.bitchord.data.canvas.AppleArtistArtRepository
import com.music.bitchord.data.canvas.keyColors
import com.music.bitchord.ui.theme.SystemBarIcons
import com.music.bitchord.ui.utils.guardSheetFromContentTouches
import com.music.bitchord.ui.utils.rememberIosOverscrollFactory
import com.music.bitchord.ui.performance.resolvePerformanceRefreshRate
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.Locale

/** A full first screen of a native YouTube Music radio before AutoPlay tops it up. */
/** How much of an artist page's header scrolls away before the top fade begins. */
private const val TOP_FADE_START = 0.55f

/** How much of the header's height the fade takes to reach full strength. */
private const val TOP_FADE_RAMP = 0.3f

private const val INITIAL_RADIO_TRACKS = 24

internal fun shouldSkipAfterDislike(
    previousStatus: LikeStatus,
    targetVideoId: String,
    currentVideoId: String?,
): Boolean = previousStatus != LikeStatus.DISLIKE && targetVideoId == currentVideoId


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Before the composition, so a cold launch from a widget's artwork has
        // the request already standing by the time BitChordApp first reads it.
        PlayerDeepLink.consume(intent)
        JamInviteLink.consume(intent)
        // Likewise for a link tapped or shared from another app — see [MusicLink].
        MusicLink.consume(intent)
        setContent {
            val theme by AppSettings.themeMode.collectAsStateWithLifecycle()
            val appFont by AppSettings.appFont.collectAsStateWithLifecycle()
            val highPerformance by AppSettings.highPerformanceMode.collectAsStateWithLifecycle()
            val liquidGlassEnabled by AppSettings.liquidGlass.collectAsStateWithLifecycle()
            val iosOverscrollFactory = rememberIosOverscrollFactory()
            val performanceRefreshRate by AppSettings.performanceRefreshRate.collectAsStateWithLifecycle()
            val composeView = LocalView.current
            LaunchedEffect(highPerformance, performanceRefreshRate, composeView) {
                applyPerformanceMode(highPerformance, performanceRefreshRate, composeView)
            }
            val darkTheme = when (theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            BitChordTheme(darkTheme = darkTheme, font = appFont) {
                // The glass surfaces sample this layer, and a layer records only
                // what is drawn into it — which, for BitChord, is a page that
                // paints no background of its own. Everywhere a page is not
                // showing artwork the recording is transparent, so the glass had
                // nothing to blur there and you saw straight through it to the
                // sharp page underneath: album art came through the bar blurred
                // and text came through it untouched. The window's background is
                // the floor the pages have always been drawn against, so it is
                // laid down here too and the recording is opaque like the screen.
                val windowBackground = MaterialTheme.colorScheme.background
                val paintBackdrop: ContentDrawScope.() -> Unit = remember(windowBackground) {
                    {
                        drawRect(windowBackground)
                        drawContent()
                    }
                }
                val appBackdrop = rememberLayerBackdrop(onDraw = paintBackdrop)
                CompositionLocalProvider(
                    LocalOverscrollFactory provides iosOverscrollFactory,
                    LocalLiquidGlassEnabled provides liquidGlassEnabled,
                    LocalAppBackdrop provides appBackdrop,
                ) {
                // The window's width, measured rather than asked for.
                //
                // `Configuration.screenWidthDp` is the wrong question here: in a
                // freeform or desktop window it can report the display rather
                // than the window it is actually in, and it lands a beat late
                // when that window is dragged. The layout downstream splits in
                // two on the strength of this number and sizes both halves from
                // it, so a stale one is a player pane sized for a window that no
                // longer exists and a page squeezed to a sliver to pay for it.
                // A measured constraint cannot be stale — it is the very width
                // the split is about to be laid out in.
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    BitChordApp(
                        darkTheme = darkTheme,
                        windowWidth = maxWidth,
                        windowHeight = maxHeight,
                        appBackdrop = appBackdrop,
                    )
                }
                }
            }
        }
    }

    /**
     * The volume keys move the DAC's own volume while direct USB output owns
     * it: Android's music stream no longer reaches that DAC, so the system
     * volume would change nothing anyone hears. Handled here rather than left
     * to the media session so the app's own HUD answers instead of Android's
     * remote-volume panel; in the background the session takes them. A sheet
     * in a window of its own asks the same through [DacVolumeKeys].
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        handleDacVolumeKey(event) || super.dispatchKeyEvent(event)

    /**
     * Requests a window refresh rate without forcing a display mode or
     * resolution. Android may still lower it for temperature, battery state or
     * hardware limits, which is why Settings describes this as a preference.
     */
    private fun applyPerformanceMode(enabled: Boolean, refreshRate: Int, composeView: View) {
        val supportedRefreshRate = composeView.display.resolvePerformanceRefreshRate(refreshRate)
        window.attributes = window.attributes.apply {
            preferredRefreshRate = if (enabled) supportedRefreshRate.toFloat() else 0f
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.setFrameRatePowerSavingsBalanced(!enabled)
            composeView.requestedFrameRate = if (enabled) {
                supportedRefreshRate.toFloat()
            } else {
                View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
            }
        }
    }

    /**
     * The other half of the relay. This activity is `singleTask`, so once it is
     * running a second tap on the widget does not rebuild anything — it arrives
     * here, and [onCreate] never runs again.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Replaces what getIntent() returns, so the extra this consumes is the
        // one that just arrived and not the one the task was started with.
        setIntent(intent)
        PlayerDeepLink.consume(intent)
        JamInviteLink.consume(intent)
        MusicLink.consume(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BitChordApp(
    darkTheme: Boolean,
    /** The width of the window this is laid out in — see the call site. */
    windowWidth: Dp,
    /**
     * The window's height, measured the same way and for the same reason as
     * [windowWidth] — and needed alongside it for exactly one thing: telling
     * a portrait window apart from a landscape one. Width alone can't; a
     * big tablet's portrait width comfortably clears a phone's landscape
     * width, so the two-column player (see [landscapePlayerAvailable])
     * would fire in portrait too if it only ever asked about width.
     */
    windowHeight: Dp,
    appBackdrop: LayerBackdrop,
    viewModel: MainViewModel = viewModel(),
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val hazeState = remember { HazeState() }
    // Recording the backdrop layer costs a draw pass, so it only runs when a
    // liquid-glass surface (the nav bar or artwork-page back button) can sample it.
    val glassActive = LocalLiquidGlassEnabled.current && isGlassSupported()
    // "Reduce dynamic blur" keeps the glass bar's *shape* — the folding
    // now-playing-and-tabs component is a layout, not an effect, and dropping
    // back to the two stacked bars would be answering a question about material
    // with a different screen. What it drops is the sampling: the surfaces fill
    // solid (see [Modifier.liquidGlass]) and the whole-page layer recording
    // below goes with them, which is the part that costs a draw pass.
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val progressiveBlur by AppSettings.progressiveBlur.collectAsStateWithLifecycle()
    val glassSamplesBackdrop = glassActive && !reduceDynamicBlur
    // What folds [GlassNavBar] between its expanded and inline shapes. Held here
    // rather than inside the bar because the page's scroll is what drives it,
    // and the page is a sibling of the bar rather than a child.
    val navBarScroll = rememberFloatingTabBarScrollConnection()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    // Explore loads the first time it's shown, not at launch beside Home.
    LaunchedEffect(selectedTab) {
        if (selectedTab == TAB_EXPLORE) viewModel.onExploreShown()
    }
    /**
     * Whether the player's sheet is up. The player is always a full-screen
     * take-over raised over the page, on every window size — the library
     * stays full-screen behind a mini player rather than losing a lane to a
     * permanent pane.
     */
    var showNowPlaying by remember { mutableStateOf(false) }
    // The DAC's own volume while direct USB output owns it; the full player
    // has its own bar for that, so the HUD stays out of its way.
    androidx.compose.runtime.SideEffect { VolumeHud.suppressed = showNowPlaying }
    // The Top Picks and mood artwork loop forever; nobody sees it with the
    // app in the background or Now Playing pulled up over the page.
    val appForeground = com.music.bitchord.ui.rememberIsForeground()
    val artworkMotion = appForeground && !showNowPlaying
    // Top Picks turns over each hour; coming back to the app after a
    // turn-over is an open too.
    LaunchedEffect(appForeground) {
        if (appForeground) viewModel.refreshTopPicksIfStale()
    }
    // Where the two ends of opening and closing the player meet: the mini
    // player's cover reports itself here, the sheet its offset, and the player
    // flies its artwork between them — see [PlayerDock].
    val playerDock = remember { PlayerDock() }
    // Who moves the player sheet when the app does rather than M3: a tap's
    // open, the pull up from the mini player, and every close — see
    // [PlayerSheetMotion].
    val playerSheetScope = rememberCoroutineScope()
    val playerSheetMotion = remember {
        PlayerSheetMotion(
            scope = playerSheetScope,
            dock = playerDock,
            shown = { showNowPlaying },
            setShown = { showNowPlaying = it },
        )
    }
    playerSheetMotion.windowInfo = LocalWindowInfo.current
    // Whether a portrait player fully up has the page covered. Derived, so the
    // page's draw below hears when that flips rather than on every frame of the
    // sheet's travel: read straight off the fraction, each of those frames
    // redrew the page's whole window behind the player, glass and blurs and all.
    val coveredByPlayer by remember {
        derivedStateOf { showNowPlaying && playerDock.attached && playerDock.openFraction() >= 1f }
    }
    // "Reduce animation" keeps the player as it always was: M3's own slide up
    // and down, its own scrim and window animations, no pull from the mini
    // player and no artwork flying between the two. Everything below that
    // takes part in the docking asks for [activeDock] / this, never for the
    // dock or the motion directly.
    val reducePlayerMotion by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val activeDock = playerDock.takeIf { !reducePlayerMotion }
    playerSheetMotion.flingVelocity = with(LocalDensity.current) { PLAYER_PULL_FLING_VELOCITY.toPx() }
    // How the app takes the player down, whatever for: a back press, or a page
    // opened from inside it. Set straight to false, the sheet left in one frame
    // on its window's own exit animation — the old fast drop, with a copy of the
    // artwork slipping down out of the mini player's cover after it.
    val dismissPlayer: () -> Unit = {
        if (reducePlayerMotion) showNowPlaying = false else playerSheetMotion.close()
    }
    // The far end of the relay from a widget's artwork. Cleared here rather than
    // where it was set, so the request is spent by being served — see
    // [PlayerDeepLink.handled]. The sheet itself is gated on there being a track,
    // so on a cold launch this simply arms it and it opens as the controller
    // connects.
    val openPlayerRequested by PlayerDeepLink.pending.collectAsStateWithLifecycle()
    LaunchedEffect(openPlayerRequested) {
        if (openPlayerRequested) {
            showNowPlaying = true
            PlayerDeepLink.handled()
        }
    }
    /**
     * What the in-app browser is open for, or null while it is closed —
     * signing in, or picking a channel in YouTube Music's own Accounts list.
     */
    var webSession by remember { mutableStateOf<WebSessionMode?>(null) }
    // Replay: the page (a route on the back stack — see [AppNavigator]), the
    // stories over it, and the share sheet over those. Closing one has to
    // reveal what it was opened from.
    // Library cards use the same Replay page as every other entry point, but
    // category cards ask it to start at their matching ranked section.
    var replayLandingPage by rememberSaveable { mutableStateOf(ReplayStoryPage.INTRO) }
    var replayStory by rememberSaveable { mutableStateOf<ReplayStoryPage?>(null) }
    var showReplayShare by rememberSaveable { mutableStateOf(false) }
    /** Which story card the share sheet is for, or null for the whole Replay. */
    var replaySharePage by rememberSaveable { mutableStateOf<ReplayStoryPage?>(null) }
    var showSpotifyCanvasAuth by remember { mutableStateOf(false) }

    // Hosted here rather than inside SourcesScreen so its frosted card has
    // something to blur: that screen is drawn inside the `hazeSource` subtree,
    // and a haze effect sampling the layer it is itself part of renders with no
    // background at all. Hosting it here also puts the scrim over the tab bar
    // and the mini player, like every other alert in the app.
    var editingSource by remember { mutableStateOf<SourceConfig?>(null) }
    var confirmJioSaavn by remember { mutableStateOf(false) }
    var confirmBitPerfect by remember { mutableStateOf<BitPerfectMode?>(null) }
    var confirmFixedVolume by remember { mutableStateOf(false) }
    /** An addon search row tapped while the quality setting skips its addon; see [AddonBlockedAlert]. */
    var blockedAddonSong by remember { mutableStateOf<com.music.bitchord.data.model.Song?>(null) }
    /** An addon a download just went ahead without, and why; see [SourceRegistry.addonsLeftOutOfDownloads]. */
    var addonLeftOutOfDownload by remember {
        mutableStateOf<Pair<String, SourceRegistry.DownloadExclusion>?>(null)
    }
    var editingPartyServer by remember { mutableStateOf(false) }
    var editingSignatureApi by remember { mutableStateOf(false) }
    var librarySortMenuOpen by remember { mutableStateOf(false) }
    var showLyricsSources by remember { mutableStateOf(false) }
    var showAppLanguage by remember { mutableStateOf(false) }
    var showTranslationLanguage by remember { mutableStateOf(false) }
    var showAccountSelector by remember { mutableStateOf(false) }
    var showListenBrainzLogin by remember { mutableStateOf(false) }
    var showLastfmLogin by remember { mutableStateOf(false) }
    var showWebDavEditor by remember { mutableStateOf(false) }
    var showSmbEditor by remember { mutableStateOf(false) }
    // Discord Rich Presence: its own page under Account & integrations (a
    // route), its own full-screen sign-in, and one slot for whichever of its
    // alerts is open. The alerts live out here rather than on the page because
    // their scrim has to cover the tab bar and mini player, which are drawn
    // after it.
    var showDiscordLogin by remember { mutableStateOf(false) }
    var showSpotifyLogin by remember { mutableStateOf(false) }
    var discordDialog by remember { mutableStateOf<DiscordDialog?>(null) }
    var songActions by remember { mutableStateOf<Song?>(null) }
    var showLyricsOffset by remember { mutableStateOf(false) }
    /**
     * Whether the track menu that is up was opened from the player.
     *
     * Its copy of the menu carries rows nothing else offers — a sleep timer,
     * the track log, share — and until now "opened from the player" and "the
     * player is on screen" were the same sentence, because the player was a
     * sheet and nothing else could be up behind it. On a tablet the player is
     * never *the* thing on screen: it is always beside whatever is, so the
     * question has to be answered by whoever opened the menu.
     */
    var menuFromPlayer by remember { mutableStateOf(false) }
    /** Whether the track menu was opened from the History screen. */
    var menuFromHistory by remember { mutableStateOf(false) }
    /**
     * The row or card that was held to open the track menu, or null when it
     * was opened any other way. Non-null lifts that item into
     * [HeldContextMenu]; null hangs the menu off [songMenuAnchor] instead —
     * which is what the ⋮ on the very same row opens.
     */
    var songMenuOrigin by remember { mutableStateOf<HeldItem?>(null) }
    /** The ⋮ or ⋯ the track menu was opened from, for [IosMenuPopover] to hang off. */
    var songMenuAnchor by remember { mutableStateOf<Rect?>(null) }
    /**
     * A still of the player for its ⋯ menu to blur — see the player's sheet
     * below. Up here so the tap that opens the menu can ask for it: asked any
     * later, the picture is read back on a frame of the menu's spring.
     */
    val playerBackdrop = rememberSnapshotBackdrop()
    /**
     * The player live instead, while it is a full-screen Spotify Canvas: that
     * keeps playing under the ⋯ menu, and a still of it would freeze under the
     * panel. Recorded only while that is the case — see the player's sheet.
     */
    val livePlayerBackdrop = rememberLayerBackdrop()
    var playerLiveCanvas by remember { mutableStateOf(false) }
    /**
     * The same for a ⋮ or ⋯ menu over a page: a still of everything under it —
     * the page, its bars, the mini player — blurred under the panel. The
     * page's live haze, under a panel growing on a spring, was re-cut and
     * rescaled with it on every frame: the blur swam against the panel's
     * growth, and the whole page was rendered again for it each time.
     */
    val pageBackdrop = rememberSnapshotBackdrop()
    /** Holding a row anywhere but the player — the menu without the player's rows. */
    val openSongMenu: (Song) -> Unit = { song ->
        menuFromPlayer = false
        songMenuOrigin = LongPressOrigin.consume()
        songMenuAnchor = MenuAnchor.consume()
        songActions = song
    }
    // Whether the player's album/artist lookup (below, for the current track)
    // is still in flight — read by the long-press sheet so it can show a
    // loading row instead of the two just being absent while it waits.
    var linksLoading by remember { mutableStateOf(false) }
    // Which track the playlist picker is adding, or null when it's closed.
    // Separate from [songActions] so the menu can close behind it — the picker
    // is the next step, not a second sheet stacked on the first.
    var playlistTarget by remember { mutableStateOf<Song?>(null) }
    // The picker opened from the Library tab, where there is no track and
    // creating the playlist is the whole errand.
    var creatingPlaylist by remember { mutableStateOf(false) }
    // The same two, over the device's own playlists — for a track that is a
    // file on it, and for "New playlist" on Local Music's Playlists tab.
    var offlinePlaylistTarget by remember { mutableStateOf<Song?>(null) }
    var creatingOfflinePlaylist by remember { mutableStateOf(false) }
    var showSpotifyImportDialog by remember { mutableStateOf(false) }
    // Which album or playlist the collection menu is open on, or null when it
    // is shut. One slot for every surface that can open it — the shelves on
    // three tabs, the search rows, the artist page's carousels, the release
    // page's own overflow — because only one of them can be held at a time.
    var browseActions by remember { mutableStateOf<BrowseTarget?>(null) }
    /** The card held to open [browseActions] — see [songMenuOrigin]. */
    var browseMenuOrigin by remember { mutableStateOf<HeldItem?>(null) }
    /** The ⋯ [browseActions] was opened from — see [songMenuAnchor]. */
    var browseMenuAnchor by remember { mutableStateOf<Rect?>(null) }
    /** Rename asked for from the menu, which hands it on to a sheet holding only the form. */
    var browseRenameInSheet by remember { mutableStateOf(false) }
    /** The playlist being rearranged, or null when the reorder page is shut. */
    var reorderTarget by remember { mutableStateOf<UserPlaylist?>(null) }
    /** Its entries as YouTube has them now — fetched fresh when the sheet opens. */
    var reorderEntries by remember { mutableStateOf<UiState<List<Song>>>(UiState.Loading) }
    var reorderSaving by remember { mutableStateOf(false) }
    /** An album or playlist: lifted when it was a hold, else hung off the ⋯ that was tapped. */
    val openBrowseMenu: (BrowseTarget) -> Unit = { target ->
        browseMenuOrigin = LongPressOrigin.consume()
        browseMenuAnchor = MenuAnchor.consume()
        browseRenameInSheet = false
        browseActions = target
    }
    val autoplay by AppSettings.autoplay.collectAsStateWithLifecycle()
    val partyState by ListenTogether.state.collectAsStateWithLifecycle()
    // The same answer the playback service acts on, rather than a second one
    // derived here — see [autoplayEnabledFor]. Drawing the local preference in
    // a party made the toggle lie in both directions: a listener whose own
    // switch was on sat under "AutoPlay on" in a party that had it off, got no
    // suggestions, and pressing the button appeared to do nothing, because it
    // turned the party's setting on while the label already said so.
    val autoplayEnabled = autoplayEnabledFor(partyState, autoplay)
    val partyServerStatus by ListenTogether.serverConnectionState.collectAsStateWithLifecycle()
    val listenBrainzToken by AppSettings.listenBrainzToken.collectAsStateWithLifecycle()
    // Set each time the search tab is tapped, which SearchScreen uses as a
    // signal to focus the input field.
    var searchFocusRequested by remember { mutableStateOf(false) }
    val searchFieldFocus = remember { FocusRequester() }
    // Invalidates an in-flight radio lookup when a later play request wins.
    var playRequestGeneration by remember { mutableIntStateOf(0) }
    // Starting radio from the item already playing must not replace that media
    // item just to add UI metadata. This temporary label covers that seed; all
    // following radio items carry radioName in their MediaItem extras.
    var activeRadioSeed by remember { mutableStateOf<Pair<String, String>?>(null) }

    // The modal player owns light status glyphs and its own contrast scrim.
    // Every other surface follows the theme; Replay's page and stories remain
    // dark artwork either way. So is the welcome, drawn over all of it.
    val onboardingComplete by AppSettings.onboardingComplete.collectAsStateWithLifecycle()
    // Held through the slide away as well, so the glyphs don't turn dark over
    // the mesh while it is still on its way off the screen.
    val onboardingShown = remember { MutableTransitionState(!onboardingComplete) }
    onboardingShown.targetState = !onboardingComplete
    val onboardingVisible = onboardingShown.currentState || onboardingShown.targetState

    // ---- Navigation ----
    // One back stack for every page: the routes pushed over the tabs, and the
    // album / artist / playlist pages interleaved with them — see
    // [AppNavigator]. Whatever was opened last is what is on screen, and back
    // (the gesture or the bar's button alike) undoes exactly that.
    val navigator = viewModel.navigator
    val detailStack by viewModel.detailStack.collectAsStateWithLifecycle()
    val topEntry = navigator.visibleEntry(detailStack.size)
    // An artist's open shelf is drawn by the artist page itself, so it is not a
    // route the page area switches to — only the shelf the page is showing.
    val artistShelfRoute = topEntry?.route as? AppRoute.ArtistShelf
    val visibleEntry = topEntry?.takeIf { artistShelfRoute == null }
    val detailActiveShelf = artistShelfRoute?.shelf
    /** The route on screen, or null when a release page or a tab is. */
    val route = visibleEntry?.route
    /**
     * The release page at the top of its stack, whether or not a route
     * covers it. Most things want [detail] — the one actually on screen.
     */
    val topDetail = detailStack.lastOrNull()
    /** The release page on screen, or null when a route or a tab is. */
    val detail = topDetail?.takeIf { visibleEntry == null }
    // The reorder list lives on its playlist's page; leaving that page by any
    // route — Back, a tab, another page over it — puts the list away with it.
    LaunchedEffect(detail?.browseId) {
        if (reorderTarget?.browseId != detail?.browseId) reorderTarget = null
    }

    SystemBarIcons(
        dark = !darkTheme && !showNowPlaying && route != AppRoute.Replay && replayStory == null && !onboardingVisible,
    )

    val homeState by viewModel.home.collectAsStateWithLifecycle()
    val homeLoadingMore by viewModel.homeLoadingMore.collectAsStateWithLifecycle()
    val homeRecentlyPlayedLoading by viewModel.homeRecentlyPlayedLoading.collectAsStateWithLifecycle()
    val topPicks by viewModel.topPicks.collectAsStateWithLifecycle()
    val topPicksLoading by viewModel.topPicksLoading.collectAsStateWithLifecycle()

    // The top bar's icon is the quiet, always-there nudge; this is the
    // once-per-launch popup version of the same news. `updateDialogShown`
    // rides out configuration changes on rememberSaveable so a rotation
    // doesn't bring it back — only a fresh launch does.
    var updateDialogShown by rememberSaveable { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    val updateAvailable by viewModel.updateAvailable.collectAsStateWithLifecycle()

    /**
     * The single gate both surfaces read, so the icon can't announce the update
     * a beat before the popup does — they're one piece of news, and staggering
     * them made the top bar look like it had caught something the app hadn't.
     */
    val updateNotice = updateAvailable

    LaunchedEffect(updateNotice) {
        if (updateNotice != null && !updateDialogShown) {
            updateDialogShown = true
            showUpdateDialog = true
        }
    }
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val exploreState by viewModel.explore.collectAsStateWithLifecycle()
    val selectedMoodGenre by viewModel.selectedMoodGenre.collectAsStateWithLifecycle()
    val moodGenreShelves by viewModel.moodGenreShelves.collectAsStateWithLifecycle()
    val libraryState by viewModel.library.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val searchSource by viewModel.searchSource.collectAsStateWithLifecycle()
    val libraryResults by viewModel.libraryResults.collectAsStateWithLifecycle()
    val addonResults by viewModel.addonResults.collectAsStateWithLifecycle()
    // Collected rather than read once, so addon rows in search re-dim the
    // moment the quality setting, the connection or the watch's cap moves.
    val addonCeiling = AppSettings.ceilingFor(
        wifi = AppSettings.audioQualityWifi.collectAsStateWithLifecycle().value,
        cellular = AppSettings.audioQualityCellular.collectAsStateWithLifecycle().value,
        metered = AppSettings.meteredConnection.collectAsStateWithLifecycle().value,
        override = AppSettings.streamCeilingOverride.collectAsStateWithLifecycle().value,
    )
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val incomingJamInvite by JamInviteLink.pending.collectAsStateWithLifecycle()
    var activeJamInviteCode by rememberSaveable { mutableStateOf<String?>(null) }
    var activeJamInviteServer by rememberSaveable { mutableStateOf<String?>(null) }

    // An invite is navigation and an action: reveal the Jam settings page now,
    // then let that page join once an account is available. Keeping the code
    // here lets a sign-in round trip return to the invite it started from.
    LaunchedEffect(incomingJamInvite) {
        val invite = incomingJamInvite ?: return@LaunchedEffect
        activeJamInviteCode = invite.code
        activeJamInviteServer = invite.serverUrl
        dismissPlayer()
        replayStory = null
        showReplayShare = false
        webSession = null
        navigator.reset(AppRoute.Settings, AppRoute.ListenTogether)
        JamInviteLink.handled()
    }
    LaunchedEffect(signedIn, activeJamInviteCode) {
        if (signedIn && activeJamInviteCode != null) {
            navigator.pushAll(AppRoute.Settings, AppRoute.ListenTogether)
        }
    }
    val account by viewModel.account.collectAsStateWithLifecycle()
    val selectedChannelName by viewModel.selectedChannelName.collectAsStateWithLifecycle()
    val googleAccounts by viewModel.googleAccounts.collectAsStateWithLifecycle()
    val activeAccountId by viewModel.activeAccountId.collectAsStateWithLifecycle()
    val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
    val historyState by viewModel.history.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val lyricsSource by viewModel.lyricsSource.collectAsStateWithLifecycle()
    val lyricsChecked by viewModel.lyricsChecked.collectAsStateWithLifecycle()
    val lyricsProviderStates by viewModel.lyricsProviderStates.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val searchSuggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val searchLoadingMore by viewModel.searchLoadingMore.collectAsStateWithLifecycle()
    val searchScrollReset by viewModel.searchScrollReset.collectAsStateWithLifecycle()
    val releaseLibrary by viewModel.releaseLibrary.collectAsStateWithLifecycle()
    // Local Music, Downloads and the other device folders draw as release
    // pages, but they have no artwork to wash the top inset in, so they keep
    // the plain bar; their top bar trades the overflow for a sort-and-view
    // menu, and keeps the account photo.
    //
    // A downloaded playlist's page is under `local:` too and is none of that: it
    // has a cover and a track list, so it takes the bar every other release page
    // takes — as does an album or artist opened out of a folder. Hence the
    // folder question rather than the prefix.
    val isLocalDetail = detail?.browseId.isDeviceFolder()
    // An album or playlist page: the one whose top bar carries its search,
    // sort and overflow controls.
    val isReleaseDetail = detail != null && !isLocalDetail && detail.type != BrowseType.ARTIST
    // Not keyed on the browse id and not remembered here: each page's choice
    // lives in AppSettings keyed by that page — Spotify-style, one playlist's
    // order never imposes itself on another, and every page keeps its own
    // across visits.
    val detailSongSorts by AppSettings.detailSongSorts.collectAsStateWithLifecycle()
    val songSort = topDetail?.browseId?.let { detailSongSorts[it] } ?: SongSort.DEFAULT
    var songSortMenuOpen by remember { mutableStateOf(false) }
    // A device folder's equivalents — its sort and whether it is a grid —
    // kept apart for Downloads and shared by every other folder, as they
    // always were. Up here because both the page and the top bar's menu read
    // them.
    val downloadedMusicSort by AppSettings.downloadedMusicSort.collectAsStateWithLifecycle()
    val localMusicSort by AppSettings.localMusicSort.collectAsStateWithLifecycle()
    val downloadedMusicViewType by AppSettings.downloadedMusicViewType.collectAsStateWithLifecycle()
    val localMusicViewType by AppSettings.localMusicViewType.collectAsStateWithLifecycle()
    val folderSortOf: (String) -> LocalMusicSort = { id ->
        if (id == "local:downloads") downloadedMusicSort else localMusicSort
    }
    val folderViewTypeOf: (String) -> LibraryViewType = { id ->
        if (id == "local:downloads") downloadedMusicViewType else localMusicViewType
    }
    var folderMenuOpen by remember { mutableStateOf(false) }
    // Whether the open release page's filter box is up. Held out here with the
    // sort control because the button that opens it sits in the top bar beside
    // it, and reset with the page so a filter never follows you to the next one.
    var detailSearching by rememberSaveable(topDetail?.browseId) { mutableStateOf(false) }
    val likeStatuses by viewModel.likeStatuses.collectAsStateWithLifecycle()
    // A device file's heart is the device's Liked playlist, not a YouTube
    // rating: the file has no identity there to rate. One rule for every
    // surface that draws a heart — the player and the track menu alike.
    val offlineLiked by OfflinePlaylists.liked.collectAsStateWithLifecycle()
    val offlinePlaylists by OfflinePlaylists.playlists.collectAsStateWithLifecycle()
    val offlinePlaylistGroups = remember(offlinePlaylists) { offlinePlaylists.map { it.toGroup() } }
    val likeStatusOf: (Song) -> LikeStatus = { song ->
        val uri = song.localUri
        when {
            uri != null -> if (uri in offlineLiked) LikeStatus.LIKE else LikeStatus.INDIFFERENT
            else -> likeStatuses[song.videoId] ?: LikeStatus.INDIFFERENT
        }
    }
    val toggleLikeOf: (Song) -> Unit = { song ->
        if (song.localUri != null) OfflinePlaylists.toggleLike(song) else viewModel.toggleLike(song.videoId)
    }
    // Which tracks are being held on YouTube's own upload, so the player's menu
    // offers the way back out of a revert rather than the revert again.
    val pinnedToOriginal by OriginalVersion.pinned.collectAsStateWithLifecycle()
    val qualityUpgradesInFlight by NerdStats.racingLossless.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlistsLoading by viewModel.playlistsLoading.collectAsStateWithLifecycle()

    // The Downloads page is a snapshot of the folder, taken when it was opened.
    // Saving a track or deleting one while it is on screen changes what belongs
    // on it — and now that the page groups by artist and album, a stale list is
    // stale counts and a missing row in three places rather than one. So it is
    // taken again whenever the record of what's on disk changes.
    val savedDownloads by Downloads.saved.collectAsStateWithLifecycle()
    val localMusicFolderUri by AppSettings.localMusicFolderUri.collectAsStateWithLifecycle()
    val filterNonMusicAudio by AppSettings.filterNonMusicAudio.collectAsStateWithLifecycle()
    val webdavUrl by AppSettings.webdavUrl.collectAsStateWithLifecycle()
    val smbHost by AppSettings.smbHost.collectAsStateWithLifecycle()
    val librarySort by AppSettings.librarySort.collectAsStateWithLifecycle()
    // The releases those files were asked for as — read here rather than in the
    // page so the Downloads folder recomposes when one is added, the same way it
    // does when a file is.
    val savedCollections by Downloads.collections.collectAsStateWithLifecycle()
    // The playlists and albums among them, for the Library page's On Device
    // shelf. Read off both records: the collection record is what says a
    // release was downloaded whole, and what is on disk is what says it still
    // has anything left to open.
    val downloadedReleases = remember(savedCollections, savedDownloads) {
        Downloads.savedReleases()
    }
    // Playlists imported without (or instead of) a YouTube Music account live
    // in the app's own store, and sit on the same shelf as the downloaded ones.
    val localPlaylists by com.music.bitchord.data.spotify.LocalPlaylistStore.playlists.collectAsStateWithLifecycle()
    val localPlaylistItems = localPlaylists.map { playlist ->
        ShelfItem(
            title = playlist.title,
            subtitle = stringResource(R.string.local_playlist_subtitle, playlist.songs.size),
            thumbnailUrl = playlist.songs.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl,
            videoId = null,
            browseId = playlist.browseId,
        )
    }
    // What a browse id is recorded under in Downloads.collections, when it names
    // a release downloaded whole — see BrowseTarget.downloadId. A downloaded
    // playlist's own page and its card both carry the id under the
    // `local:playlist:` prefix; a release still reachable by its real id (an
    // album's own page, a search hit) is looked up directly under that instead.
    val downloadIdFor: (String?) -> String? = { id ->
        id?.let { Downloads.recordIdOf(it) ?: it }?.takeIf { it in savedCollections }
    }
    LaunchedEffect(savedDownloads, savedCollections, topDetail?.browseId) {
        val openPage = topDetail ?: return@LaunchedEffect
        val open = openPage.browseId
        // A downloaded playlist's page is a snapshot of the same folder and goes
        // stale for the same reasons — and it is the one page a delete can empty
        // out entirely, which is worth saying rather than leaving rows behind
        // that play nothing.
        // An album or artist out of one of those folders goes stale with it.
        val folder = LocalGroups.folderOf(open) ?: open
        if (openPage.songs !is UiState.Loading &&
            (folder == "local:downloads" || folder == CACHE_FOLDER_BROWSE_ID || Downloads.recordIdOf(open) != null ||
                com.music.bitchord.data.spotify.LocalPlaylistStore.getPlaylist(open) != null)
        ) {
            viewModel.reloadLocalDetail(open)
        }
    }
    // An offline playlist's page is the store's, and follows it: a track
    // removed from its menu, or a like from the player, leaves no stale row.
    // Apart from the effect above so a like never re-lists the Downloads folder.
    LaunchedEffect(topDetail?.browseId, offlinePlaylists) {
        val openPage = topDetail ?: return@LaunchedEffect
        if (openPage.songs !is UiState.Loading && OfflinePlaylists.isOfflinePlaylist(openPage.browseId)) {
            viewModel.reloadLocalDetail(openPage.browseId)
        }
    }
    LaunchedEffect(localMusicFolderUri, filterNonMusicAudio) {
        val open = topDetail?.browseId
        if (open == "local:all" || LocalGroups.folderOf(open) == "local:all") {
            viewModel.reloadLocalDetail(open!!)
        }
    }
    LaunchedEffect(webdavUrl) {
        if (topDetail?.browseId == com.music.bitchord.data.webdav.WebDavConfig.BROWSE_ID) {
            viewModel.reloadLocalDetail(com.music.bitchord.data.webdav.WebDavConfig.BROWSE_ID)
        }
    }
    LaunchedEffect(smbHost) {
        if (topDetail?.browseId == com.music.bitchord.data.smb.SmbConfig.BROWSE_ID) {
            viewModel.reloadLocalDetail(com.music.bitchord.data.smb.SmbConfig.BROWSE_ID)
        }
    }
    val controller = rememberMediaController()
    val player = rememberPlayerState(controller)
    // A music video's picture follows this controller's clock — see [MusicVideoEngine].
    DisposableEffect(controller) {
        MusicVideoEngine.attach(controller)
        onDispose { MusicVideoEngine.attach(null) }
    }
    // A resume in a party is performed on the instant the server schedules, not
    // when it was pressed, and nothing about the player moves in between — so
    // the transport spends that round trip drawn as though the tap never landed.
    // Folded into the buffering flag every play button already answers to, since
    // to a listener the two are the same fact: it is coming, wait.
    val awaitingPartyStart by ListenTogether.awaitingStart.collectAsStateWithLifecycle()
    val playPauseBusy = player.isLoading || awaitingPartyStart
    // Listening in a party whose host has taken the controls. Read once here
    // and handed to every surface, so the player, the mini player and the glass
    // bar can never disagree about whether this device may drive the music.
    val controlsLocked = partyState.controlsLocked
    var queueNotice by remember { mutableStateOf<QueueActionNotice?>(null) }
    var queueNoticeId by remember { mutableIntStateOf(0) }
    val showQueueNotice: (String) -> Unit = { message ->
        queueNoticeId += 1
        queueNotice = QueueActionNotice(queueNoticeId, message)
    }
    LaunchedEffect(queueNotice?.id) {
        val shown = queueNotice ?: return@LaunchedEffect
        delay(3_000)
        if (queueNotice?.id == shown.id) queueNotice = null
    }
    // Why a control did nothing, on the same strip above the mini player that
    // already answers "added to queue". Reached from every surface that had a
    // control taken away — see [ListenTogether.State.controlsLocked].
    val hostOnlyMessage = stringResource(R.string.listen_together_host_only_notice)
    val showHostOnlyNotice: () -> Unit = { showQueueNotice(hostOnlyMessage) }

    /**
     * Whether the host has taken the music, and say so if they have.
     *
     * Every way the app starts or reorders playback funnels through one of the
     * lambdas below, and each asks this first. Checked here rather than left to
     * the player: the service refuses these actions anyway, but by then the tap
     * has already been half-applied — a queue swapped with nothing to play it,
     * or a resume of whatever the party was on — which is what a listener saw
     * as the music flickering on and off.
     */
    val refusedByHost: () -> Boolean = {
        val locked = ListenTogether.state.value.controlsLocked
        if (locked) showHostOnlyNotice()
        locked
    }

    /**
     * The one play/pause every surface presses.
     *
     * In a locked party this still works — it stops and starts *this* device
     * without touching the party, which is the whole of what a listener is
     * left with. The exception is a party that is itself paused: there is
     * nothing to join and nothing to hold out of, so the tap says why instead
     * of starting a second of audio that [PartySync] then has to stop.
     */
    val togglePlayPause: () -> Unit = {
        controller?.let { c ->
            val party = ListenTogether.state.value
            if (party.controlsLocked && !party.playback.isPlaying && !c.isPlaying) {
                showHostOnlyNotice()
            } else if (c.isPlaying) {
                c.pause()
            } else {
                c.play()
            }
        }
    }
    val shuffleEnabled by QueueShuffle.enabled.collectAsStateWithLifecycle()
    // For the version switch's "there is no other version" notice.
    val versionContext = LocalContext.current
    // Tracks whose other cut a switch looked for and didn't find: their menu
    // row reads "not found" from then on instead of offering it again.
    val missingVersions = remember { mutableStateListOf<String>() }
    // A conversion is deliberately scoped to the current listening session.
    // Keeping the complete original row here lets Revert restore the exact
    // video upload, including its title and playlist identity, rather than
    // trying to reconstruct it from the catalogue match.
    var convertedFromVideo by remember { mutableStateOf<Song?>(null) }
    var convertedAudioId by remember { mutableStateOf<String?>(null) }
    var switchingAudioVersion by remember { mutableStateOf(false) }
    // Revert is an explicit choice for this occurrence of the track. Without
    // remembering it, the automatic preference would see the restored video
    // as a fresh item and immediately convert it again.
    var keepVideoId by remember { mutableStateOf<String?>(null) }
    // Track conversion from audio to video (inverse of above)
    var convertedFromAudio by remember { mutableStateOf<Song?>(null) }
    var convertedVideoId by remember { mutableStateOf<String?>(null) }
    // Optimistically updated track for instant UI updates when switching versions
    var optimisticVersionSong by remember { mutableStateOf<Song?>(null) }

    // Lyrics follow whatever is playing; duration lands a beat after the track.
    // Keyed on the lyric settings too, so turning a source on or off, reordering
    // them or changing the syllable preference applies to the track already
    // playing rather than only the next one.
    val syncedLyricsEnabled by AppSettings.syncedLyrics.collectAsStateWithLifecycle()
    val lyricsSources by AppSettings.lyricsSources.collectAsStateWithLifecycle()
    val lyricsSourceOrder by AppSettings.lyricsSourceOrder.collectAsStateWithLifecycle()
    val prioritizeSyllableSync by AppSettings.prioritizeSyllableSync.collectAsStateWithLifecycle()
    LaunchedEffect(
        player.song?.videoId, player.durationMs, syncedLyricsEnabled, lyricsSources,
        lyricsSourceOrder, prioritizeSyllableSync,
    ) {
        player.song?.let {
            viewModel.loadLyrics(
                it.videoId,
                it.title,
                it.artist,
                // The player's own length, and the catalogue's where it has
                // none yet. Paused, ExoPlayer never finishes preparing the
                // track it was skipped to, so it reports no duration at all —
                // and a lookup that waits for one waits for ever, which left
                // the lyrics of a paused track loading until it was played.
                player.durationMs.takeIf { ms -> ms > 0L } ?: it.durationMillis(),
                it.albumName,
                it.localUri,
            )
        }
    }

    val homeListState = rememberLazyListState()
    val exploreListState = rememberLazyListState()
    val moodGenreListState = rememberLazyListState()
    val libraryListState = rememberLazyListState()
    val historyListState = rememberLazyListState()
    val libraryShowAllGridState = rememberLazyGridState()
    val moodsGridState = rememberLazyGridState()
    val searchListState = rememberLazyListState()
    val currentListState = when (selectedTab) {
        TAB_HOME -> homeListState
        TAB_EXPLORE -> if (selectedMoodGenre == null) exploreListState else moodGenreListState
        TAB_LIBRARY -> libraryListState
        else -> searchListState
    }

    // Pull-to-refresh: the drag lives with the feed, but the indicator is the
    // line under the top bar, so the state has to be visible to both.
    val homePull = rememberPullToRefreshState()
    val explorePull = rememberPullToRefreshState()
    val libraryPull = rememberPullToRefreshState()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val currentFeed = when {
        route != null || detail != null -> null
        selectedTab == TAB_HOME -> MainViewModel.Feed.HOME
        selectedTab == TAB_EXPLORE -> MainViewModel.Feed.EXPLORE
        selectedTab == TAB_LIBRARY -> MainViewModel.Feed.LIBRARY
        else -> null
    }
    // The lead shelf is listening history, so opening Home after playing
    // something is exactly when it needs re-fetching.
    LaunchedEffect(currentFeed) {
        if (currentFeed == MainViewModel.Feed.HOME) viewModel.onHomeShown()
        // Likewise for Library: a playlist created or a song liked since it
        // was last fetched is a change to exactly this page.
        if (currentFeed == MainViewModel.Feed.LIBRARY) viewModel.onLibraryShown()
    }

    val currentPull = when (currentFeed) {
        MainViewModel.Feed.HOME -> homePull
        MainViewModel.Feed.EXPLORE -> explorePull
        MainViewModel.Feed.LIBRARY -> libraryPull
        null -> null
    }
    val scrolled by remember(currentListState) {
        derivedStateOf {
            currentListState.firstVisibleItemIndex > 0 ||
                currentListState.firstVisibleItemScrollOffset > 24
        }
    }

    // A pushed album/artist/playlist page has a large header of its own — the
    // sleeve, or an artist's photo running edge to edge — which owns the title
    // until it is scrolled away, exactly as a tab's big heading does. The state
    // is hoisted because the bar lives beside that page rather than inside it,
    // and is rebuilt per page: pushing a second one must not inherit the
    // first's scroll offset.
    // As [detailListState], for Replay: its own large heading owns the title
    // until it is scrolled away, and the bar lives out here rather than on the
    // page. Rebuilt per opening so reopening starts at the top, but kept while
    // that opening is on the stack, so back from a page opened off it returns
    // to where it was.
    val replayEntryId = navigator.entries.lastOrNull { it.route == AppRoute.Replay }?.id
    val replayListState = remember(replayEntryId, replayLandingPage) { LazyListState() }
    val replayScrolled by remember(replayListState) {
        derivedStateOf {
            replayListState.firstVisibleItemIndex > 0 ||
                replayListState.firstVisibleItemScrollOffset > 24
        }
    }

    // AnimatedContent keeps the outgoing page composed during its fade. A
    // single state remembered from only the *current* detail id is therefore
    // handed to both the outgoing and incoming LazyColumns for that interval.
    // Compose lazy state is one-layout state: sharing it between those lists
    // can leave the incoming album attached to the disappearing artist/grid
    // layout and unable to consume scroll gestures. Keep one state per page
    // while it is on the navigation stack instead.
    val detailListStates = remember { mutableMapOf<String, LazyListState>() }
    val detailListState = topDetail?.browseId?.let { browseId ->
        detailListStates.getOrPut(browseId) { LazyListState() }
    } ?: remember { LazyListState() }
    // A device folder's tab and selection, kept the same way and for the same
    // reason: an album opened from the folder replaces its page until Back.
    val localFolderStates = remember { mutableMapOf<String, LocalFolderState>() }
    LaunchedEffect(detailStack.map { it.browseId }) {
        val open = detailStack.mapTo(HashSet()) { it.browseId }
        detailListStates.keys.retainAll(open)
        localFolderStates.keys.retainAll(open)
    }
    // Each pushed page's saved state (see the page switcher below), held while
    // the page is on the stack and dropped as it leaves it, so the next visit
    // to the same route starts fresh.
    val pageStates = rememberSaveableStateHolder()
    val pageKeysSeen = remember { mutableSetOf<String>() }
    val livePageKeys = navigator.entries.map { AppDestination.Route(it).key } +
        detailStack.map { AppDestination.Release(it.browseId).key }
    LaunchedEffect(livePageKeys) {
        val gone = pageKeysSeen.filter { it !in livePageKeys }
        gone.forEach(pageStates::removeState)
        pageKeysSeen.removeAll(gone.toSet())
    }
    val detailTitleDrop = with(LocalDensity.current) { DETAIL_TITLE_DROP.toPx() }
    val detailScrolled by remember(detailListState, detailTitleDrop) {
        derivedStateOf {
            detailListState.firstVisibleItemIndex > 0 ||
                detailListState.firstVisibleItemScrollOffset > detailTitleDrop
        }
    }

    // Held, not rebuilt. `listOf` hands back a new instance on every pass, and a
    // List is not a type the compiler can call stable, so under strong skipping
    // the bar this is handed to compares it by identity, never matches, and so
    // can never skip. This composable re-runs on every frame of a scroll — it
    // reads [scrolled] — which made the whole floating bar, both of its states
    // and every glass surface on them recompose once per frame for the length of
    // a fold. Keyed on the labels so a locale change still rebuilds it.
    val homeLabel = stringResource(R.string.home)
    val playLabel = stringResource(R.string.play)
    val exploreLabel = stringResource(R.string.explore)
    val libraryLabel = stringResource(R.string.library)
    val searchLabel = stringResource(R.string.search)
    val historyLabel = stringResource(R.string.history)
    val replayLabel = stringResource(R.string.replay)
    val queueLabel = stringResource(R.string.queue)
    val sharedLinkLabel = stringResource(R.string.shared_link)
    val openedFileLabel = stringResource(R.string.opened_file)
    val tabs = remember(homeLabel, exploreLabel, libraryLabel, searchLabel) {
        listOf(
            BottomTab(homeLabel, BitChordIcons.Home),
            BottomTab(exploreLabel, BitChordIcons.TabExplore),
            BottomTab(libraryLabel, BitChordIcons.TabLibrary),
            BottomTab(searchLabel, BitChordIcons.TabSearch),
        )
    }

    val scope = rememberCoroutineScope()

    // Copies tracks to the WebDAV server, leaving the local files alone.
    // A clash suspends the batch on WebDavUploads.conflict until the dialog
    // above answers it, so this needs nothing more than the summary.
    fun uploadToWebDav(songs: List<Song>) {
        scope.launch {
            val summary = com.music.bitchord.data.webdav.WebDavUploads.upload(context, songs)
            if (summary.total > 0) {
                showQueueNotice(
                    context.getString(
                        R.string.webdav_upload_summary,
                        summary.uploaded,
                        summary.skipped,
                        summary.failed,
                    ),
                )
            }
            if (summary.uploaded > 0 &&
                topDetail?.browseId == com.music.bitchord.data.webdav.WebDavConfig.BROWSE_ID
            ) {
                viewModel.reloadLocalDetail(com.music.bitchord.data.webdav.WebDavConfig.BROWSE_ID)
            }
        }
    }

    /**
     * Resolve and apply the catalogue release without replacing the video row
     * up front. That makes the video's title/artwork visible immediately and
     * leaves it in place as the fallback if matching fails.
     *
     * Automatic requests temporarily hold playback because the music-only
     * preference is the version the listener asked to hear. Manual requests
     * preserve the old behaviour and let the video keep playing meanwhile.
     */
    suspend fun switchToMusicOnly(song: Song, pauseWhileResolving: Boolean) {
        val c = controller ?: return
        val index = c.currentMediaItemIndex
        if (index !in 0 until c.mediaItemCount ||
            c.currentMediaItem?.mediaId != song.videoId ||
            switchingAudioVersion
        ) return

        val resumeAfterResolution = pauseWhileResolving && c.playWhenReady
        switchingAudioVersion = true
        keepVideoId = null
        val holdUntilAligned = AppSettings.smartVersionAlignment.value
        if (holdUntilAligned) AppSettings.versionAlignmentInProgress.value = true
        if (pauseWhileResolving) c.pause()
        try {
            TrackLog.d("Player", "audio switch requested for '${song.title}'", song.videoId)
            val audio = runCatching { YtMusicRepository.resolveAudio(song) }.getOrNull()
            val stillCurrent = c.currentMediaItemIndex == index &&
                c.currentMediaItem?.mediaId == song.videoId

            // The original MediaItem was never removed, so failure only needs
            // to release the loading state and resume it immediately.
            if (audio == null || audio.videoId == song.videoId) {
                TrackLog.w("Player", "audio switch found no distinct official song", song.videoId)
                // The switch is always offered now, so say when there was nothing to switch to.
                if (song.videoId !in missingVersions) missingVersions += song.videoId
                if (stillCurrent) showQueueNotice(versionContext.getString(R.string.no_audio_version))
                if (stillCurrent && resumeAfterResolution) c.play()
                return
            }
            if (!stillCurrent) {
                TrackLog.d("Player", "audio switch discarded; listener changed track", song.videoId)
                return
            }

            convertedFromVideo = song
            convertedAudioId = audio.videoId
            TrackLog.d("Player", "audio switch applying '${audio.title}' (${audio.videoId})", song.videoId)
            val target = audio.copy(
                isVideoOrigin = true,
                queueTier = song.queueTier,
                queueEntryId = song.queueEntryId,
                radioName = song.radioName,
                playbackSource = song.playbackSource,
                playbackSourceType = song.playbackSourceType,
                playbackSourceId = song.playbackSourceId,
            )
            if (!holdUntilAligned ||
                VersionAudioAligner.getCachedOffsetMs(song.videoId, target.videoId) != null ||
                YtMusicRepository.versionTimeMap(song.videoId, target.videoId) != null
            ) {
                optimisticVersionSong = target
            }
            // Let go of the bar *before* the command goes out: the service
            // raises it again the moment its own job starts, and releasing
            // first is what makes the handover correct whichever way that
            // command dispatches — inline or on the next turn of the loop.
            // The same line in the finally is the catch-all for every path
            // that never got this far, where nothing else would release it.
            if (holdUntilAligned) AppSettings.versionAlignmentInProgress.value = false
            c.swapToVersion(target)
        } finally {
            if (holdUntilAligned) AppSettings.versionAlignmentInProgress.value = false
            switchingAudioVersion = false
        }
    }

    /**
     * Resolve and apply the video version without replacing the audio row
     * up front. This is the inverse of switchToMusicOnly.
     */
    suspend fun switchToVideo(song: Song, pauseWhileResolving: Boolean) {
        val c = controller ?: return
        val index = c.currentMediaItemIndex
        if (index !in 0 until c.mediaItemCount ||
            c.currentMediaItem?.mediaId != song.videoId ||
            switchingAudioVersion
        ) return

        val resumeAfterResolution = pauseWhileResolving && c.playWhenReady
        switchingAudioVersion = true
        // Mirrors the music-only path: the bar lights up for the resolve and
        // the service keeps it lit through the measure-and-cut, and the row
        // stays on the audio version until that swap actually commits — see
        // [alignmentPending] at the toggle.
        val holdUntilAligned = AppSettings.smartVersionAlignment.value
        if (holdUntilAligned) AppSettings.versionAlignmentInProgress.value = true
        if (pauseWhileResolving) c.pause()
        try {
            TrackLog.d("Player", "video switch requested for '${song.title}'", song.videoId)
            val video = runCatching { YtMusicRepository.resolveVideo(song) }.getOrNull()
            val stillCurrent = c.currentMediaItemIndex == index &&
                c.currentMediaItem?.mediaId == song.videoId

            if (video == null || video.videoId == song.videoId) {
                TrackLog.w("Player", "video switch found no distinct video", song.videoId)
                if (song.videoId !in missingVersions) missingVersions += song.videoId
                if (stillCurrent) showQueueNotice(versionContext.getString(R.string.no_video_version))
                if (stillCurrent && resumeAfterResolution) c.play()
                return
            }
            if (!stillCurrent) {
                TrackLog.d("Player", "video switch discarded; listener changed track", song.videoId)
                return
            }

            convertedFromAudio = song
            convertedVideoId = video.videoId
            TrackLog.d("Player", "video switch applying '${video.title}' (${video.videoId})", song.videoId)
            val target = video.copy(
                queueTier = song.queueTier,
                queueEntryId = song.queueEntryId,
                radioName = song.radioName,
                playbackSource = song.playbackSource,
                playbackSourceType = song.playbackSourceType,
                playbackSourceId = song.playbackSourceId,
            )
            // Not while an alignment is still owed: claiming the video here
            // would show a version the player is not playing yet, and would
            // make the switch look finished before it had begun.
            if (!holdUntilAligned ||
                VersionAudioAligner.getCachedOffsetMs(song.videoId, target.videoId) != null ||
                YtMusicRepository.versionTimeMap(song.videoId, target.videoId) != null
            ) {
                optimisticVersionSong = target
            }
            // Released before the handover for the same reason as the
            // music-only path above: the service owns the flag from here, and
            // the finally only exists for the paths that never send.
            if (holdUntilAligned) AppSettings.versionAlignmentInProgress.value = false
            c.swapToVersion(target)
        } finally {
            if (holdUntilAligned) AppSettings.versionAlignmentInProgress.value = false
            switchingAudioVersion = false
        }
    }

    fun playFrom(
        allSongs: List<Song>,
        allIndex: Int,
        source: QueueSource,
        boundedContext: Boolean = false,
        continuationToken: String? = null,
    ) {
        // A Spotify page lists songs it has not found on YouTube Music yet (or
        // never will); those can't be queued, so play the rest in their order.
        if (allSongs.getOrNull(allIndex)?.isUnresolvedSpotify == true) return
        val songs = allSongs.filterNot { it.isUnresolvedSpotify }
        val index = songs.indexOf(allSongs.getOrNull(allIndex)).coerceAtLeast(0)
        playRequestGeneration++
        activeRadioSeed = null
        scope.launch {
            if (refusedByHost()) return@launch
            val c = controller ?: return@launch
            val currentTimeline = player.queue.takeIf { it.size == c.mediaItemCount }
                ?: (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toSong() }
            val currentIndex = c.currentMediaItemIndex

            if (ListenTogether.state.value.inParty) {
                val selectedSong = songs.getOrNull(index) ?: return@launch
                val party = ListenTogether.state.value
                val partyQueue = party.queue.items
                val partyIndex = partyQueueIndexOf(party.queue, party.playback, party.playback.track?.videoId)
                val upcomingPartyTracks = if (partyIndex >= 0) {
                    partyQueue.drop(partyIndex + 1)
                } else {
                    emptyList()
                }
                val timeline = QueueCoordinator.buildPartyPlaybackQueue(
                    tappedSong = selectedSong,
                    source = source,
                    upcomingPartyTracks = upcomingPartyTracks,
                )
                c.playSongs(timeline, 0)
            } else {
                // A Cast session still owns the full queue on the phone so the
                // receiver can follow its existing queue semantics. The bounded
                // service path is local-player-only; the service repeats this
                // guard for commands that race a newly connected receiver.
                //
                // Any list too long to fit the window goes this way too, whatever
                // page it came from: a whole library in the player is a whole
                // library re-sent to every media controller on each queue edit.
                val bounded = boundedContext || songs.size > BoundedQueuePlanner.MAX_PHYSICAL_CONTEXT
                if (bounded && !CastController.state.value.casting) {
                    c.playBoundedContext(songs, index, source, continuationToken)
                } else {
                    val result = QueueCoordinator.buildContextQueue(
                        currentTimeline = currentTimeline,
                        currentIndex = currentIndex,
                        newContextSongs = songs,
                        selectedIndex = index,
                        contextSource = source,
                    )
                    c.playSongs(result.timeline, result.startIndex)
                }
            }
            // Start playback in the mini-player; the user opens the full view by tapping it.
        }
    }
    // Kept for entry points whose rows already carry their origin (notably a
    // collection action fetched before this callback). The explicit wrappers
    // below are preferred because a track's album is not necessarily where it
    // was played from.
    val play: (List<Song>, Int) -> Unit = { songs, index ->
        val first = songs.getOrNull(index)
        val source = QueueSource(
            title = first?.playbackSource ?: first?.albumName ?: queueLabel,
            type = first?.playbackSourceType ?: PlaybackSourceType.QUEUE,
            id = first?.playbackSourceId,
        )
        playFrom(songs, index, source)
    }
    LaunchedEffect(player.song?.videoId) {
        if (optimisticVersionSong?.videoId == player.song?.videoId ||
            (optimisticVersionSong != null && player.song?.videoId != convertedAudioId && player.song?.videoId != convertedVideoId && player.song?.videoId != keepVideoId)
        ) {
            optimisticVersionSong = null
        }
        if (activeRadioSeed?.first != player.song?.videoId) activeRadioSeed = null
        if (keepVideoId != player.song?.videoId) keepVideoId = null
        if (player.song?.videoId != convertedAudioId) {
            convertedFromVideo = null
            convertedAudioId = null
            switchingAudioVersion = false
        }
        if (player.song?.videoId != convertedVideoId) {
            convertedFromAudio = null
            convertedVideoId = null
            switchingAudioVersion = false
        }
    }

    /**
     * A song picked on its own — off a home card or a search hit — starts a
     * station rather than queueing the list it was shown in. Searching
     * "Perfect" and tapping the top hit otherwise queues twenty covers and
     * remixes of the same song. Album, artist and playlist pages keep [play],
     * where the surrounding list *is* the thing the user asked for.
     */
    val playRadio: (Song, QueueSource) -> Unit = { song, source ->
        playRequestGeneration++
        activeRadioSeed = null
        scope.launch {
            if (refusedByHost()) return@launch
            val c = controller ?: return@launch
            val currentTimeline = player.queue.takeIf { it.size == c.mediaItemCount }
                ?: (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toSong() }
            val currentIndex = c.currentMediaItemIndex
            val oneOffQueue = QueueCoordinator.buildOneOffQueue(
                currentTimeline = currentTimeline,
                currentIndex = currentIndex,
                tappedSong = song,
                source = source,
            )
            c.playSongs(oneOffQueue, 0)
            // Start radio in the mini-player; the user opens the full view by tapping it.
        }
    }

    /**
     * Starts the explicit station offered by every song overflow menu.
     *
     * The related tracks come from YouTube Music's own RDAMVM watch queue.
     * Loading happens before the player is touched so a failed request cannot
     * destroy the queue already playing. Once ready, the whole old queue is
     * replaced in one Media3 operation.
     */
    val startRadio: (Song) -> Unit = { song ->
        val originalController = controller
        if (originalController != null && !refusedByHost()) {
            val request = ++playRequestGeneration
            // Ignore AutoPlay's tail: it may legitimately grow while the
            // request is in flight and does not mean the listener chose a
            // different queue. A new album/song queue does.
            val originalManualQueue = (0 until originalController.mediaItemCount)
                .map { originalController.getMediaItemAt(it) }
                .filterNot { it.fromAutoplay }
                .map { it.mediaId }
            scope.launch {
                val seed = song.copy(
                    radioName = song.title,
                    playbackSource = song.title,
                    playbackSourceType = PlaybackSourceType.SHARED_LINK,
                    playbackSourceId = song.videoId,
                )
                val related = loadAutoplayTracks(
                    existing = listOf(seed),
                    seedSong = seed,
                    limit = INITIAL_RADIO_TRACKS,
                ).getOrElse {
                    if (request == playRequestGeneration) {
                        Toast.makeText(context, R.string.couldnt_load_tracks, Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                if (related.isEmpty()) {
                    if (request == playRequestGeneration) {
                        Toast.makeText(context, R.string.couldnt_load_tracks, Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                val activeController = controller
                val activeManualQueue = (0 until activeController.mediaItemCount)
                    .map { activeController.getMediaItemAt(it) }
                    .filterNot { it.fromAutoplay }
                    .map { it.mediaId }
                if (request != playRequestGeneration || activeManualQueue != originalManualQueue) {
                    return@launch
                }
                // Cancel any armed crossfade/AutoPlay work and erase the old
                // cold-start snapshot before the visible queue is replaced.
                activeController.beginRadioQueue()
                val currentIndex = activeController.currentMediaItemIndex
                val currentItem = activeController.currentMediaItem
                if (currentIndex >= 0 && currentItem?.mediaId == song.videoId) {
                    // Keep the current MediaItem itself untouched. Replacing it,
                    // even with the same song, reparses the source at position
                    // zero and audibly stops/restarts the track.
                    // Station first, old tail after: removing first leaves the
                    // current track briefly last, and AutoPlay answers that by
                    // appending this same radio a second time.
                    activeController.addMediaItems(currentIndex + 1, related.map { it.toMediaItem() })
                    val oldTail = currentIndex + 1 + related.size
                    if (oldTail < activeController.mediaItemCount) {
                        activeController.removeMediaItems(oldTail, activeController.mediaItemCount)
                    }
                    if (currentIndex > 0) activeController.removeMediaItems(0, currentIndex)
                    activeRadioSeed = song.videoId to song.title
                } else {
                    activeRadioSeed = null
                    activeController.playSongs(listOf(seed) + related, 0)
                }
                // Make this station — never the queue from before it — what a
                // fresh process restores, even if it is killed immediately.
                activeController.commitRadioQueue()
                Toast.makeText(
                    context,
                    context.getString(R.string.radio_started, song.title),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    /**
     * A Top Picks card: opens the release, or starts the station behind it.
     *
     * A station is YouTube Music's own queue for it — the Supermix, the
     * artist's Mix, a mood playlist's radio — fetched only now, on the tap,
     * and loaded before the player is touched so a failed request leaves the
     * current queue playing. It then replaces the queue the way [startRadio]
     * does, and AutoPlay carries it on past its last track.
     */
    val openTopPick: (TopPick, String) -> Unit = { pick, label ->
        val browseId = pick.browseId
        if (pick.kind == TopPickKind.LATEST_RELEASE && browseId != null) {
            viewModel.openDetail(
                browseId = browseId,
                title = pick.title,
                subtitle = pick.artistName.orEmpty(),
                thumbnailUrl = pick.images.firstOrNull(),
            )
        } else if (controller != null && !refusedByHost()) {
            val request = ++playRequestGeneration
            scope.launch {
                val songs = TopPicks.queue(pick).getOrNull().orEmpty()
                if (request != playRequestGeneration) return@launch
                val activeController = controller ?: return@launch
                if (songs.isEmpty()) {
                    Toast.makeText(context, R.string.couldnt_load_tracks, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val sourceId = pick.watch?.playlistId ?: pick.mood?.name
                val station = songs.map {
                    it.copy(
                        radioName = label,
                        playbackSource = label,
                        playbackSourceType = PlaybackSourceType.HOME,
                        playbackSourceId = sourceId,
                    )
                }
                activeRadioSeed = null
                activeController.beginRadioQueue()
                activeController.playSongs(station, 0)
                activeController.commitRadioQueue()
            }
        }
    }
    /**
     * A Find Your Mood tile, on Home or on its Show all grid: that mood's
     * station, steered by the same scene words the Mood for You card was
     * built with.
     */
    val playMood: (TopPickMood, String) -> Unit = { mood, title ->
        val hints = topPicks?.firstOrNull { it.kind == TopPickKind.MOOD }?.hints
        openTopPick(
            if (hints != null) TopPicks.moodStation(mood, hints) else TopPicks.moodStation(mood),
            title,
        )
    }
    val addToQueue: (Song) -> Unit = { song ->
        scope.launch {
            if (refusedByHost()) return@launch
            // The end of what the user queued, not the end of the queue: a song
            // asked for by name outranks whatever AutoPlay lined up behind it.
            controller?.let {
                if (ListenTogether.state.value.inParty) {
                    val upcoming = (it.mediaItemCount - (it.currentMediaItemIndex + 1)).coerceAtLeast(0)
                    if (upcoming >= 25) {
                        showQueueNotice(context.getString(R.string.party_queue_full, 25))
                        return@launch
                    }
                }
                val current = it.currentMediaItem?.toSong()
                val timeline = player.queue.takeIf { q -> q.size == it.mediaItemCount }
                    ?: (0 until it.mediaItemCount).map { idx -> it.getMediaItemAt(idx).toSong() }
                val at = QueueCoordinator.findUserQueueInsertionIndex(
                    timeline = timeline,
                    currentIndex = it.currentMediaItemIndex,
                    isNext = false,
                )
                val queued = song.copy(
                    radioName = current?.radioName,
                    playbackSource = current?.playbackSource ?: queueLabel,
                    playbackSourceType = current?.playbackSourceType ?: PlaybackSourceType.QUEUE,
                    playbackSourceId = current?.playbackSourceId,
                ).asQueueEntry(QueueTier.USER_QUEUE)
                it.addMediaItem(at, queued.toMediaItem())
                showQueueNotice(context.getString(R.string.song_added_to_queue))
            }
        }
    }
    val playNext: (Song) -> Unit = { song ->
        scope.launch {
            if (refusedByHost()) return@launch
            controller?.let {
                if (ListenTogether.state.value.inParty) {
                    val upcoming = (it.mediaItemCount - (it.currentMediaItemIndex + 1)).coerceAtLeast(0)
                    if (upcoming >= 25) {
                        showQueueNotice(context.getString(R.string.party_queue_full, 25))
                        return@launch
                    }
                }
                val current = it.currentMediaItem?.toSong()
                val timeline = player.queue.takeIf { q -> q.size == it.mediaItemCount }
                    ?: (0 until it.mediaItemCount).map { idx -> it.getMediaItemAt(idx).toSong() }
                val at = QueueCoordinator.findUserQueueInsertionIndex(
                    timeline = timeline,
                    currentIndex = it.currentMediaItemIndex,
                    isNext = true,
                )
                val queued = song.copy(
                    radioName = current?.radioName,
                    playbackSource = current?.playbackSource ?: queueLabel,
                    playbackSourceType = current?.playbackSourceType ?: PlaybackSourceType.QUEUE,
                    playbackSourceId = current?.playbackSourceId,
                ).asQueueEntry(QueueTier.USER_QUEUE)
                it.addMediaItem(at, queued.toMediaItem())
                showQueueNotice(context.getString(R.string.song_will_play_next))
            }
        }
    }
    val onSongSwipe: (Song) -> Unit = { song ->
        if (AppSettings.swipeToPlayNext.value) playNext(song) else addToQueue(song)
    }

    /**
     * Opens an artist or release page given its browse id — or, failing that,
     * its name.
     *
     * Replay's charts are the reason this exists. An artist there is counted by
     * *name*, because a name is the only thing every track carries: a browse id
     * rides along only when the row that queued the track happened to have one,
     * which for a home-feed card or an AutoPlay suggestion it does not. So half
     * the rows on a chart would have nothing to open, and a row that does
     * nothing when tapped is worse than a row that isn't tappable — it reads as
     * the app having failed rather than as the app not offering.
     *
     * Searching for the name is what the user would do next anyway, and it is
     * what the app already does to find a video's catalogue release (see
     * [YtMusicRepository.resolveAudio]). A search that finds nothing says so,
     * which is at least an answer.
     */
    fun openByName(
        browseId: String?,
        name: String,
        subtitle: String?,
        type: BrowseType,
        artwork: String? = null,
    ) {
        if (browseId != null) {
            val credit = subtitle ?: context.getString(
                if (type == BrowseType.ARTIST) R.string.artist else R.string.album,
            )
            viewModel.openDetail(browseId, name, credit, artwork, type)
            return
        }
        scope.launch {
            val filter = if (type == BrowseType.ARTIST) {
                SearchFilter.ARTISTS
            } else {
                SearchFilter.ALBUMS
            }
            val query = listOfNotNull(name, subtitle).joinToString(" ")
            val hit = YtMusicRepository.search(query, filter).getOrNull()
                ?.filterIsInstance<SearchResult.Browse>()
                ?.firstOrNull()
                ?.item
            if (hit == null) {
                Toast.makeText(
                    context,
                    context.getString(R.string.couldnt_find, name),
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                viewModel.openDetail(
                    hit.browseId,
                    hit.title,
                    hit.subtitle,
                    hit.thumbnailUrl ?: artwork,
                    hit.type,
                )
            }
        }
    }

    /**
     * A whole album, playlist or library list onto the queue in one go.
     *
     * [next] picks which of the two positions the single-track menu already
     * offers it lands in — straight after the current track, or behind
     * everything else the user queued but still ahead of AutoPlay (see
     * [addToQueue]). The list keeps its own running order either way: this
     * *adds* a release, it doesn't start one, so [QueueShuffle] has no say here.
     *
     * There is usually nothing on screen to show for it — the queue panel is
     * shut, the current track carries on — so the count is said out loud, the
     * same way a batch download is.
     */
    val queueSongs: (List<Song>, Boolean) -> Unit = { songs, next ->
        if (songs.isNotEmpty()) {
            scope.launch {
                if (refusedByHost()) return@launch
                val c = controller
                if (c == null || c.mediaItemCount == 0) {
                    // Nothing to queue behind. "Add to queue" on a silent
                    // player can only mean start here — and adding without
                    // preparing would leave the list sitting in a player that
                    // never gets round to it.
                    play(songs, 0)
                } else {
                    val toAdd = if (ListenTogether.state.value.inParty) {
                        val upcoming = (c.mediaItemCount - (c.currentMediaItemIndex + 1)).coerceAtLeast(0)
                        val slotsLeft = (25 - upcoming).coerceAtLeast(0)
                        if (slotsLeft <= 0) {
                            showQueueNotice(context.getString(R.string.party_queue_full, 25))
                            return@launch
                        }
                        songs.take(slotsLeft)
                    } else {
                        songs
                    }
                    val timeline = player.queue.takeIf { q -> q.size == c.mediaItemCount }
                        ?: (0 until c.mediaItemCount).map { idx -> c.getMediaItemAt(idx).toSong() }
                    val at = QueueCoordinator.findUserQueueInsertionIndex(
                        timeline = timeline,
                        currentIndex = c.currentMediaItemIndex,
                        isNext = next,
                    )
                    val current = c.currentMediaItem?.toSong()
                    c.addMediaItems(
                        at,
                        toAdd.map {
                            it.copy(
                                radioName = current?.radioName,
                                playbackSource = current?.playbackSource ?: queueLabel,
                                playbackSourceType = current?.playbackSourceType
                                    ?: PlaybackSourceType.QUEUE,
                                playbackSourceId = current?.playbackSourceId,
                            ).asQueueEntry(QueueTier.USER_QUEUE).toMediaItem()
                        },
                    )
                    val message = context.resources.getQuantityString(
                        if (next) R.plurals.songs_will_play_next else R.plurals.songs_added_to_queue,
                        toAdd.size,
                        toAdd.size,
                    )
                    showQueueNotice(message)
                }
            }
        }
    }
    val addSongsToQueue: (List<Song>) -> Unit = { songs -> queueSongs(songs, false) }
    val playSongsNext: (List<Song>) -> Unit = { songs -> queueSongs(songs, true) }

    // ---- Links from outside the app ----

    /**
     * A YouTube Music link tapped elsewhere on the device, a link shared into
     * BitChord, or "play something" said to the assistant — see [MusicLink].
     *
     * Keyed on the controller as well as the request, because a link is as
     * often as not what cold-starts the app: the session it has to play into is
     * still connecting the first time this runs, and returning empty-handed
     * without spending the request is what lets the second run serve it.
     */
    val linkRequest by MusicLink.pending.collectAsStateWithLifecycle()
    LaunchedEffect(linkRequest, controller) {
        val request = linkRequest ?: return@LaunchedEffect
        // Nothing here can be served without somewhere to play it — even the
        // branches that only push a page are a beat away from a tap on one of
        // its rows, and half-serving a request would spend it.
        val session = controller ?: return@LaunchedEffect
        when (request) {
            is LinkRequest.Track -> {
                val song = YtMusicRepository.trackLinks(request.videoId).getOrNull()
                if (song == null) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.couldnt_open_link),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    // A link is one song named on purpose, which is exactly the
                    // case [playRadio] exists for: play it and let AutoPlay
                    // carry on, rather than queueing something around it.
                    playRadio(
                        song,
                        QueueSource(sharedLinkLabel, PlaybackSourceType.SHARED_LINK, song.videoId),
                    )
                }
            }
            is LinkRequest.Page -> {
                dismissPlayer()
                // Titled by the page itself once it lands — a link carries a
                // browse id and nothing else. See MainViewModel.openDetail.
                viewModel.openDetail(request.browseId, title = "")
            }
            is LinkRequest.Search -> {
                val songs = if (!request.play) null else {
                    YtMusicRepository.search(request.query, SearchFilter.SONGS).getOrNull()
                        ?.filterIsInstance<SearchResult.Track>()
                }
                val top = songs?.firstOrNull()?.song
                if (top != null) {
                    playRadio(top, QueueSource(searchLabel, PlaybackSourceType.SEARCH))
                } else {
                    // Either the link was a search to look at, or "play X"
                    // found nothing to start — and the results are a better
                    // answer to a spoken request than silence is.
                    dismissPlayer()
                    navigator.clear()
                    selectedTab = TAB_SEARCH
                    viewModel.searchFor(request.query)
                }
            }
            // "Play music", nothing named. The playback service restores its
            // bounded queue before the controller connects, so this resumes
            // both a live session and one recovered after process death.
            LinkRequest.Resume -> if (session.mediaItemCount > 0) session.play()
            // A file from a file manager. The read grant that came with the
            // intent lasts as long as this task; a persistable one, when the
            // sender offered it, keeps the track playable after a restart.
            is LinkRequest.LocalFile -> {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        request.uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                val song = LocalMediaRepository.songFromUri(context, request.uri)
                playFrom(
                    listOf(song),
                    0,
                    QueueSource(openedFileLabel, PlaybackSourceType.SHARED_LINK, song.localUri),
                )
                showNowPlaying = true
            }
        }
        MusicLink.handled()
    }

    // ---- Album / playlist menu ----

    /**
     * Holding an album or playlist card, wherever one is drawn.
     *
     * Artists are left out. An artist page is a selection of their work rather
     * than a running order, and "add Radiohead to the queue" has no answer that
     * isn't a guess — so holding an artist card does nothing, as it did before.
     */
    val onBrowseLongPress: (ShelfItem) -> Unit = { item ->
        val id = item.browseId
        val type = id?.let { viewModel.browseTypeOf(it) }
        if (id != null && type != BrowseType.ARTIST) {
            openBrowseMenu(
                BrowseTarget(
                    browseId = id,
                    title = item.title,
                    subtitle = item.subtitle,
                    thumbnailUrl = item.thumbnailUrl,
                    type = type ?: BrowseType.OTHER,
                    downloadId = downloadIdFor(id),
                ),
            )
        }
    }

    /**
     * The track a song card stands for, or null if the card is a collection.
     *
     * The card's own subtitle is billed as "Song • Chelsea Wolfe"; only the
     * credit belongs in the field the player, mini player and everything
     * downstream read.
     */
    val shelfSong: (ShelfItem) -> Song? = { item ->
        item.videoId?.let { videoId ->
            Song(
                videoId = videoId,
                title = item.title,
                artist = InnertubeParser.artistFromSubtitle(item.subtitle),
                thumbnailUrl = item.thumbnailUrl,
                isVideo = item.isVideo,
            )
        }
    }

    /**
     * Holding a card on a feed whose shelves mix tracks with collections —
     * Quick picks and Recently played are songs, Listen again is either.
     *
     * [onBrowseLongPress] alone answered only half of them: a track card
     * carries a videoId and no browse id, so holding one fell through its
     * null check and nothing opened. Dispatched on the same test as the tap
     * below, so a card that plays a song offers the track menu and a card that
     * opens a page offers the album / playlist one.
     */
    val onShelfLongPress: (ShelfItem) -> Unit = { item ->
        val song = shelfSong(item)
        if (song != null) openSongMenu(song) else onBrowseLongPress(item)
    }

    /**
     * Hands [action] the target's whole track list.
     *
     * A card has no tracks behind it — its page was never opened — so the
     * listing is fetched first, all of it, and the menu that asked has already
     * closed by the time it lands. A release page's overflow passes the rows it
     * is already showing and this is immediate.
     *
     * Album rows arrive with no album name of their own, the same way they do on
     * the page (see `withAlbum` below), so the title is stamped on here too —
     * otherwise a track queued from an album card reaches the player and the
     * download folder with nothing to file it under.
     */
    val withBrowseSongs: (BrowseTarget, (List<Song>) -> Unit) -> Unit = { target, action ->
        val stamp: (List<Song>) -> Unit = { songs ->
            action(
                songs.map { song ->
                    song.copy(
                        albumName = if (target.type == BrowseType.ALBUM) {
                            song.albumName ?: target.title
                        } else {
                            song.albumName
                        },
                        playbackSource = target.title,
                        playbackSourceType = PlaybackSourceType.BROWSE,
                        playbackSourceId = target.browseId,
                    )
                },
            )
        }
        when {
            target.songs.isNotEmpty() -> stamp(target.songs)
            target.browseId == null ->
                Toast.makeText(context, context.getString(R.string.no_tracks_here), Toast.LENGTH_SHORT).show()
            else -> viewModel.collectSongs(target.browseId, target.thumbnailUrl) { result ->
                result.fold(
                    onSuccess = stamp,
                    onFailure = {
                        val message = it.message ?: context.getString(R.string.couldnt_load_tracks)
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }

    // ---- Downloads ----
    // Two permissions, and never both on one device: writing to the shared
    // Music folder needs storage access below API 29 and none at all from
    // 29 on, where MediaStore grants an app its own rows; notifications are
    // only asked for from API 33. So the branches below are mutually exclusive
    // by SDK level, and nothing here can stack two dialogs on each other.
    var downloadPending by remember { mutableStateOf<List<Song>>(emptyList()) }
    /**
     * What the pending batch was asked for as, held alongside it for the same
     * reason: the storage-permission dialog is a round trip through another
     * process, and the release has to survive it or a whole album granted
     * permission arrives as forty loose tracks.
     */
    var downloadPendingFrom by remember { mutableStateOf<DownloadTarget?>(null) }
    val notifyPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Refusing costs the progress notification, not the download. */ }
    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val songs = downloadPending
        val from = downloadPendingFrom
        downloadPending = emptyList()
        downloadPendingFrom = null
        when {
            songs.isEmpty() -> Unit
            granted -> {
                songs.forEach { Downloads.enqueue(context, it, from?.title) }
                if (from != null) Downloads.markRequested(from.id, songs.map { it.videoId })
            }
            // The one case where refusing is fatal: below API 29 there is no
            // other way to reach the Music folder.
            else -> Toast
                .makeText(context, context.getString(R.string.storage_required_save), Toast.LENGTH_SHORT)
                .show()
        }
    }
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.reloadLocalDetail("local:all")
            viewModel.reloadLocalDetail("local:downloads")
            viewModel.loadLibrarySongs()
        } else {
            Toast.makeText(context, context.getString(R.string.storage_required_read), Toast.LENGTH_SHORT).show()
        }
    }
    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    // The Search tab's Library source reads the Local Music folder, so it asks
    // for the same permission opening that folder does.
    val onSearchSourceChange: (SearchSource) -> Unit = { source ->
        if (source == SearchSource.LIBRARY && !LocalMediaRepository.hasStoragePermission(context)) {
            mediaPermissionLauncher.launch(mediaPermission)
        }
        viewModel.setSearchSource(source)
    }
    // Shared by the Library tab itself and by a shelf's "Show all" page, so a
    // card opens the same way from either.
    val onLibraryItemClick: (ShelfItem) -> Unit = { item ->
        item.browseId?.let { id ->
            if (id == SPOTIFY_BROWSE_ID) {
                navigator.push(AppRoute.Spotify)
                return@let
            }
            if ((id == "local:all" || id == "local:downloads") && !LocalMediaRepository.hasStoragePermission(context)) {
                mediaPermissionLauncher.launch(mediaPermission)
            }
            // Opened from a shelf's "Show all" page, it stacks over the grid
            // exactly as one opened from the Library tab stacks over that, so
            // back from the release lands on the grid rather than skipping
            // past it.
            viewModel.openDetail(
                browseId = id,
                title = item.title,
                subtitle = item.subtitle,
                thumbnailUrl = item.thumbnailUrl,
            )
        }
    }
    // Takes a list so a single tap on an album/playlist header can queue the
    // whole thing — the permission dance only needs to happen once for the
    // batch, not once per track.
    //
    // [from] is what the list *is*, when it is a release rather than a
    // selection: an album or a playlist. It is recorded whole, so the Downloads
    // page can offer the thing that was tapped back rather than the forty rows
    // it decomposed into — see [Downloads.rememberCollection]. Null for a single
    // track, which is not a release however many of them are asked for one at a
    // time.
    val startDownload: (List<Song>, DownloadTarget?) -> Unit = { requested, from ->
        val saved = Downloads.saved.value
        // Already on disk, and already queued or running: neither needs asking
        // again. What's left is what a tap on "Download" actually means.
        //
        // The release's cover is stamped onto any row that hasn't got one, as a
        // last check before the tap becomes a file.
        //
        // An album page bills its artwork once, in the header — its track rows
        // carry no thumbnail at all, see [InnertubeParser.parseResponsiveListItem]
        // — and a row that reaches [MediaTagger.artworkFor] with a null url is a
        // track saved with no cover in the file and none in [SavedSongMetadata]
        // either, so nothing downstream can draw one afterwards.
        // `MainViewModel.withArtwork` normally fills those in as a page loads and
        // covers the usual route here; this is the backstop for a list that
        // reached this function some other way, and it is worth having precisely
        // because the failure is silent and permanent — the file is written
        // without a cover, and re-downloading adopts the untagged copy rather
        // than replacing it.
        val songs = requested
            .filter { it.videoId !in saved }
            .map { song ->
                val cover = from?.thumbnailUrl
                if (song.thumbnailUrl.isNullOrBlank() && !cover.isNullOrBlank()) {
                    song.copy(thumbnailUrl = cover)
                } else {
                    song
                }
            }
        // Asked here as well as inside [Downloads.enqueue] — not instead of it.
        // Enqueue is the invariant and has to refuse whoever calls it, including
        // the storage-permission continuation below, which resumes long enough
        // after this check for the connection to have changed under it. This is
        // the one place that knows the tap was for forty tracks and can say so
        // once, rather than leaving forty identical failed rows to be read.
        // A copy out of the song cache spends no data, so Wi-Fi only does not
        // refuse a batch that has one — [Downloads.enqueue] still refuses the
        // tracks in it that would need the network.
        val blocked = songs.isNotEmpty() && !AppSettings.downloadsAllowedNow &&
            songs.none { Downloads.servableFromCache(it.videoId) }
        if (songs.isNotEmpty() && !blocked) {
            val needsStorage = AppSettings.exportDownloads.value && DownloadStore.needsLegacyPermission() &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) != PackageManager.PERMISSION_GRANTED

            // Asked for here rather than at launch because here is where it means
            // something: a download is the first thing this app does that the user
            // is expected to walk away from.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            if (needsStorage) {
                downloadPending = songs
                downloadPendingFrom = from
                storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                songs.forEach { Downloads.enqueue(context, it, from?.title) }
                if (from != null) Downloads.markRequested(from.id, songs.map { it.videoId })
            }
            // Said once per addon and reason per session, and only for a batch
            // that will reach the sources at all — a copy out of the song cache
            // never asks them. Without it the addon was passed over in silence
            // and the file simply arrived from JioSaavn or YouTube.
            if (!songs.all { Downloads.servableFromCache(it.videoId) }) {
                SourceRegistry.addonsLeftOutOfDownloads()
                    .firstOrNull { (config, why) -> explainedDownloadExclusions.add("${config.id}:$why") }
                    ?.let { (config, why) -> addonLeftOutOfDownload = config.displayName to why }
            }
        }
        // Recorded for everything that was asked for, not just what still has to
        // be fetched: a release whose tracks are already on the device is still
        // that release, and the point of the record is to group them. Skipped
        // only when the whole batch was refused, since then there will be
        // nothing on disk for it to group.
        if (from != null && !blocked) {
            Downloads.rememberCollection(from, requested)
            // A collection cover is not part of any audio file. Cache the
            // header image separately so its Downloads card still has artwork
            // with no connection, then atomically replace the remote URL in
            // the persisted collection record.
            if (!from.thumbnailUrl.isNullOrBlank()) {
                scope.launch(Dispatchers.IO) {
                    MediaTagger.cacheArtwork(context.applicationContext, from.thumbnailUrl)
                        ?.let { Downloads.rememberCollectionArtwork(from.id, it) }
                }
            }
        }
        when {
            // The row's own icon reports a queued download, so a single tap
            // normally needs no toast — but a refused one leaves the row exactly
            // as it was, and a button that visibly does nothing is worse than a
            // long message. So this one is said whatever the count.
            blocked -> Toast.makeText(
                context,
                context.getString(R.string.wifi_only_download_refusal),
                Toast.LENGTH_LONG,
            ).show()
            requested.size > 1 -> {
                val message = if (songs.isEmpty()) {
                    context.getString(R.string.already_downloaded)
                } else {
                    context.resources.getQuantityString(
                        R.plurals.downloading_song_count,
                        songs.size,
                        songs.size,
                    )
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    // The Cached songs folder's download buttons. The song cache already holds
    // these tracks, so they are saved from its copy — no data spent, no
    // connection needed — rather than fetched afresh at the Download quality
    // setting. See [Downloads.preferCachedCopy]. The folder's own second line
    // ("YouTube · 4.2 MB") is the cache's, not the file's, and stays behind.
    val downloadFromCache: (List<Song>) -> Unit = { songs ->
        val cleaned = songs.map { it.copy(downloadFormat = null, localDateAddedSeconds = null) }
        Downloads.preferCachedCopy(cleaned.map { it.videoId })
        startDownload(cleaned, null)
    }
    val removedFromCacheMessage = stringResource(R.string.removed_from_cache)
    // Takes tracks out of the song cache — the folder's selection, or one
    // song's menu. The open folder re-reads itself off [AudioCache.contentsChanged].
    val removeFromCache: (List<Song>) -> Unit = { songs ->
        scope.launch {
            val removed = songs.sumOf { AudioCache.remove(it.videoId) }
            if (removed > 0) showQueueNotice(removedFromCacheMessage)
        }
    }
    /**
     * One track on its own, which is never a release.
     *
     * A track reached through AutoPlay or a radio queue often has no duration
     * string at all — YouTube's watch-queue rows don't always send
     * `lengthText` — while the player itself knows exactly how long the same
     * track runs once it has loaded. Backfilled from there when it's the song
     * on screen, so downloading it isn't handed a duration of zero and
     * silently skipped for lyrics — see [LyricsTag.forTrack].
     */
    val downloadSong: (Song) -> Unit = { song ->
        val withDuration = if (song.durationMillis() <= 0L &&
            player.song?.videoId == song.videoId && player.durationMs > 0L
        ) {
            song.copy(durationText = formatDurationText(player.durationMs))
        } else {
            song
        }
        startDownload(listOf(withDuration), null)
    }

    // Content padding leaves room for the frosted bar above and the tab bar
    // (plus mini player) below, so nothing is ever trapped under the glass.
    //
    // The top is measured off the bar rather than guessed at: the bar is pinned
    // under the status bar inset, and that inset varies by device and by window,
    // so a fixed number only ever lines up on the one device it was picked on.
    // See [topBarContentPadding].
    val listPadding = PaddingValues(
        top = topBarContentPadding(),
        bottom = if (player.song != null) 210.dp else 140.dp,
    )

    // What colour the page currently under the bars is. The fades either end
    // of the screen are flat colour wherever their blur has least to say, so
    // handing them the theme's background puts a black band on a page that is
    // washed in an artwork's colour instead. Off a detail page this resolves
    // to the theme's background anyway, which is exactly right there.
    //
    // An artist page is coloured from Apple's art when it has some, so this has
    // to read the same source the page does or the bars stay the YouTube photo's
    // colour while the page beneath them has moved on.
    val appleArtVersion by AppleArtistArtRepository.updates.collectAsStateWithLifecycle()
    val detailApple = remember(topDetail?.browseId, topDetail?.type, appleArtVersion) {
        topDetail?.takeIf { it.type == BrowseType.ARTIST }
            ?.let { AppleArtistArtRepository.cached(it.browseId) }
    }
    val detailPalette = rememberArtworkPalette(
        imageUrl = detailApple?.heroUrl ?: topDetail?.thumbnailUrl,
        keyColors = detailApple?.keyColors(),
    )

    // One set of numbers for the cards, the page, the stories and the shared
    // picture, so they cannot disagree. Read while any of them is on screen —
    // which includes the Library tab, since the cards live at the top of it.
    // See [rememberReplayState].
    val replayOpen = route == AppRoute.Replay || replayStory != null || showReplayShare ||
        (selectedTab == TAB_LIBRARY && detail == null && route == null)
    val (replay, setReplayPeriod) = rememberReplayState(replayOpen)
    val replayCards = remember(replay.summary) {
        replay.summary?.takeUnless { it.isEmpty }?.cards(context).orEmpty()
    }

    // ---- The track in the player ----
    // Whatever started this track knew its title and its artwork, but rarely
    // which album or artist page it belongs to. Fill that in while the player
    // is actually up — when the sheet is raised, so playing an album from the
    // mini player still costs nothing.
    val playerShowing = showNowPlaying
    var links by remember { mutableStateOf<Song?>(null) }
    LaunchedEffect(player.song?.videoId, playerShowing) {
        links = null
        linksLoading = false
        if (!playerShowing) return@LaunchedEffect
        val current = player.song ?: return@LaunchedEffect
        if (current.albumId != null && current.artistId != null) return@LaunchedEffect
        linksLoading = true
        links = YtMusicRepository.trackLinks(current.videoId).getOrNull()
        linksLoading = false
    }
    val playerSong = player.song?.let { current ->
        val extra = links?.takeIf { it.videoId == current.videoId } ?: return@let current
        current.copy(
            artistId = current.artistId ?: extra.artistId,
            albumId = current.albumId ?: extra.albumId,
            albumName = current.albumName ?: extra.albumName,
        )
    }
    // The three-dot menu snapshots the track into songActions when it's opened,
    // so a menu opened before the lookup above resolves would otherwise be
    // stuck without album/artist rows even after the ids come in. Keep it in
    // sync while it's showing this track.
    LaunchedEffect(playerSong) {
        if (playerSong != null && songActions?.videoId == playerSong.videoId) {
            songActions = playerSong
        }
    }

    // The player's whole parameter list, kept apart from the sheet that
    // mounts it so the sheet's own setup reads on its own.
    val nowPlaying: @Composable (Song) -> Unit = { song ->
        val effectiveSong = optimisticVersionSong?.takeIf {
            it.videoId == convertedAudioId || it.videoId == convertedVideoId || it.videoId == keepVideoId ||
            it.videoId == YtMusicRepository.cachedAudioVersion(song.videoId)?.videoId ||
            it.videoId == YtMusicRepository.cachedVideoVersion(song.videoId)?.videoId
        } ?: song
        val displayedSong = activeRadioSeed
            ?.takeIf { (videoId, _) -> effectiveSong.radioName == null && videoId == effectiveSong.videoId }
            ?.let { (videoId, name) ->
                effectiveSong.copy(
                    radioName = name,
                    playbackSource = name,
                    playbackSourceType = PlaybackSourceType.SHARED_LINK,
                    playbackSourceId = videoId,
                )
            }
            ?: effectiveSong
        val playedBy = partyState
            .takeIf {
                it.inParty && it.playback.track?.videoId == displayedSong.videoId
            }
            ?.playback
            ?.let { playback ->
                playback.startedByName?.takeIf(String::isNotBlank)
                    ?: partyState.members.firstOrNull {
                        it.memberId == playback.startedBy
                    }?.displayName?.takeIf(String::isNotBlank)
            }
        NowPlayingScreen(
            song = displayedSong,
            playedBy = playedBy,
            accountName = account?.name,
            windowWidth = windowWidth,
            windowHeight = windowHeight,
            // A canvas under the ⋯ menu holds still. Asked, not passed — see
            // the parameter.
            menuOpen = { songActions != null && menuFromPlayer && songMenuOrigin == null },
            onLiveCanvasChanged = { playerLiveCanvas = it },
            isPlaying = player.isPlaying,
            isLoading = playPauseBusy,
            position = player.position,
            durationMs = player.durationMs,
            audioVersionSwitching = switchingAudioVersion,
            qualityUpgraded = player.isQualityUpgraded,
            onPlayPause = {
                togglePlayPause()
            },
            onNext = { controller?.seekToNextMediaItem() },
            onPrevious = { controller?.seekToPrevious() },
            onBlockedControl = showHostOnlyNotice,
            onSeekFraction = { fraction ->
                controller?.let { player ->
                    // Read at the moment of the seek, not from the
                    // polled snapshot the screen draws with: a track
                    // change updates the current item before it updates
                    // the duration, so a fraction dropped seconds after
                    // a transition would otherwise be scaled by the
                    // previous song's length.
                    val duration = player.duration
                    if (duration > 0) {
                        player.seekTo(
                            (fraction * duration).toLong()
                                .coerceIn(0L, (duration - SEEK_END_GUARD_MS).coerceAtLeast(0L)),
                        )
                    }
                }
            },
            onSeek = { target ->
                controller?.let { player ->
                    // Clamped here rather than at each caller because
                    // not every caller can clamp. The scrubber's target
                    // is a fraction of the duration and cannot overrun,
                    // but a tapped lyric line seeks to a timestamp from
                    // whichever transcription matched on title, artist
                    // and duration — and a match against a slightly
                    // longer master puts every line late, so a tap near
                    // the end asks for a position past the end of this
                    // stream. Media3 answers that by clamping to the
                    // final millisecond, which ends the track and starts
                    // the next one: tapping the last line of a song
                    // skipped it.
                    val duration = player.duration
                    player.seekTo(
                        if (duration > 0) {
                            target.coerceIn(0L, (duration - SEEK_END_GUARD_MS).coerceAtLeast(0L))
                        } else {
                            target.coerceAtLeast(0L)
                        },
                    )
                }
            },
            queue = player.queue,
            queueIndex = player.queueIndex,
            hasPrevious = player.hasPrevious,
            hasNext = player.hasNext,
            repeatMode = player.repeatMode,
            shuffleEnabled = shuffleEnabled,
            autoplayEnabled = autoplayEnabled,
            signedIn = signedIn,
            likeStatus = likeStatusOf(song),
            onToggleLike = { toggleLikeOf(song) },
            // The service owns both the queue and the Shuffle state. Keeping
            // the toggle on that side prevents the UI from changing the icon
            // before its asynchronous reorder command has actually landed.
            onToggleShuffle = { controller?.toggleShuffle() },
            onCycleRepeat = {
                controller?.let {
                    val next = when (it.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                    // Persist the new repeat mode so it survives app restarts.
                    AppSettings.setRepeatMode(next)
                    // Nothing else to do here: PlaybackService watches the
                    // repeat mode itself and takes AutoPlay's tracks out of
                    // the queue for the duration of repeat-all — and, unlike
                    // this screen, is still around to put them back when the
                    // loop ends.
                    it.repeatMode = next
                }
            },
            onToggleAutoplay = {
                // PlaybackService owns the setting and queue extension so
                // this path and the notification use exactly one loader.
                controller?.toggleAutoplay()
            },
            onJumpTo = { index ->
                controller?.let { c ->
                    // The session applies the jump plan to its own player (see
                    // its seekTo override); editing the queue from here would
                    // round-trip every item through the controller and lose
                    // their bounded-window metadata.
                    c.seekTo(index, 0L)
                    c.play()
                }
            },
            onRemoveFromQueue = { controller?.removeMediaItem(it) },
            onMoveInQueue = { from, to -> controller?.moveMediaItem(from, to) },
            onQueueDragActiveChange = { active -> controller?.setQueueDragActive(active) },
            // The enriched copy, not player.song — otherwise the menu
            // hides the album and artist rows even once their browse
            // ids have been resolved.
            onOpenMenu = {
                menuFromPlayer = true
                songMenuOrigin = null
                songMenuAnchor = MenuAnchor.consume()
                songActions = song
                // Taken on the frame the menu opens on, which is long anyway,
                // rather than on one of its spring's.
                if (!AppSettings.reduceDynamicBlur.value && !playerLiveCanvas) playerBackdrop.capture()
            },
            // Pushed over whatever page is under the player, so back from it
            // returns there.
            onOpenAlbum = { id ->
                dismissPlayer()
                viewModel.openDetail(
                    id,
                    song.albumName ?: song.title,
                    song.artist,
                    song.thumbnailUrl,
                    BrowseType.ALBUM,
                )
            },
            // A credit with a channel id opens that channel straight away.
            // YouTube does not give one to every credited artist - on a two-artist
            // track it linked only the first, and neither the byline nor the
            // album header had the second - so the rest go by name, through a
            // lookup that opens a page only when the answer is that name
            // exactly. Otherwise nothing opens, which beats opening a stranger.
            //
            // No artwork: this track's cover isn't the artist's
            // picture, and the page fills its own in once loaded.
            onOpenArtist = { id, name ->
                dismissPlayer()
                if (id != null) {
                    viewModel.openDetail(
                        id,
                        name,
                        context.getString(R.string.artist),
                        null,
                        BrowseType.ARTIST,
                    )
                } else {
                    scope.launch {
                        val found = YtMusicRepository.findArtistPageId(name).getOrNull()
                        if (found != null) {
                            viewModel.openDetail(
                                found,
                                name,
                                context.getString(R.string.artist),
                                null,
                                BrowseType.ARTIST,
                            )
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.couldnt_find, name),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            },
            onOpenPlaybackSource = openSource@{
                val sourceType = displayedSong.playbackSourceType ?: PlaybackSourceType.QUEUE
                val sourceTitle = displayedSong.playbackSource
                    ?: displayedSong.albumName
                    ?: queueLabel
                val sourceId = displayedSong.playbackSourceId

                // A context link is navigation, not another page stacked over
                // Now Playing. Clear the current route before restoring it.
                dismissPlayer()
                navigator.clear()

                when (sourceType) {
                    PlaybackSourceType.BROWSE -> {
                        val id = sourceId ?: return@openSource
                        viewModel.closeMoodGenre()
                        viewModel.openDetail(id, sourceTitle)
                    }
                    PlaybackSourceType.HOME -> {
                        viewModel.closeMoodGenre()
                        selectedTab = TAB_HOME
                    }
                    PlaybackSourceType.SEARCH -> {
                        viewModel.closeMoodGenre()
                        selectedTab = TAB_SEARCH
                    }
                    PlaybackSourceType.HISTORY -> {
                        navigator.push(AppRoute.History)
                        viewModel.loadHistory()
                    }
                    PlaybackSourceType.REPLAY -> {
                        replayLandingPage = ReplayStoryPage.INTRO
                        navigator.push(AppRoute.Replay)
                    }
                    PlaybackSourceType.EXPLORE -> selectedTab = TAB_EXPLORE
                    PlaybackSourceType.SHARED_LINK -> {
                        val id = sourceId ?: return@openSource
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://music.youtube.com/watch?v=$id"),
                            ),
                        )
                    }
                    // Handled inside NowPlayingScreen by opening its queue.
                    PlaybackSourceType.QUEUE -> Unit
                }
            },
            lyrics = lyrics,
            lyricsSource = lyricsSource,
            lyricsProviderStates = lyricsProviderStates,
            onSelectLyricsProvider = viewModel::selectLyricsProvider,
            lyricsUnavailable = lyricsChecked && lyrics.isNullOrEmpty(),
            lyricsOffsetOpen = showLyricsOffset,
            onDismissLyricsOffset = { showLyricsOffset = false },
            onListenTogether = {
                // The player is a sheet over the page, so it has to come down
                // for the page to be read at all.
                dismissPlayer()
                navigator.pushAll(AppRoute.Settings, AppRoute.ListenTogether)
            },
            onClearQueue = {
                // Keep what's playing and context/autoplay; drop user-queued tracks.
                controller?.let { c ->
                    QueueCoordinator.clearUserQueue(c)
                }
            },
        )
    }

    // One step back, from anywhere: the page on top of the stack, then a mood
    // or genre opened inside Explore, then any tab but Home back to Home. Only
    // Home itself hands back to the system, which is what actually closes or
    // minimizes the app. The gesture and the bar's back button both come here,
    // so they can never disagree about what is on top.
    val canGoBack = navigator.entries.isNotEmpty() || detailStack.isNotEmpty() ||
        selectedMoodGenre != null || selectedTab != TAB_HOME
    val goBack: () -> Unit = {
        if (!navigator.pop() && !viewModel.closeMoodGenre()) selectedTab = TAB_HOME
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Nothing of the page under a portrait player that is fully up:
            // that player is its own window, opaque from edge to edge, and
            // this one drawing on behind it — the page, the glass bar and the
            // blurs that sample it, a now-playing row's bars — is GPU time
            // taken from the player on every frame for nothing anyone can see.
            // The same test the player's own dim skips itself on. Composition
            // and layout carry on, so the page is current the moment the
            // player starts to come down and this draws again.
            .drawWithContent {
                if (!coveredByPlayer) drawContent()
            },
    ) {
        // A pushed page replaces the tab content but leaves the tab bar and
        // mini player in place.
        //
        // Back handlers answer newest-registered first, so the order below is
        // the order of precedence, lowest first: the page stack, then what is
        // drawn over a page, then the alerts drawn over everything. Each sheet
        // and dialog further down registers its own handler as it opens, and
        // so outranks all of these while it is up.
        BackHandler(enabled = canGoBack, onBack = goBack)
        // Replay's stories and its share sheet are drawn over everything, and
        // unwind in the order they were opened. Only while there is a summary
        // to draw them from — otherwise back would spend a step closing
        // something that never appeared.
        val replayDrawable = replay.summary?.isEmpty == false
        BackHandler(enabled = replayDrawable && replayStory != null && !showReplayShare) { replayStory = null }
        BackHandler(enabled = replayDrawable && showReplayShare) { showReplayShare = false }
        BackHandler(enabled = showUpdateDialog) { showUpdateDialog = false }
        BackHandler(enabled = showListenBrainzLogin) { showListenBrainzLogin = false }
        BackHandler(enabled = showLastfmLogin) { showLastfmLogin = false }
        BackHandler(enabled = discordDialog != null) { discordDialog = null }
        BackHandler(enabled = editingSource != null) { editingSource = null }
        BackHandler(enabled = editingPartyServer) { editingPartyServer = false }
        BackHandler(enabled = editingSignatureApi) { editingSignatureApi = false }

        // On a tablet the page and the player stand side by side rather than
        // one over the other: everything a phone stacks in a single column —
        // the feed, the frosted bars, the tab row — becomes the left half of
        // a row, and the player is the right. Off a tablet the row has the
        // one child it always had and changes nothing.
        //
        // Everything a page's menu floats over, and so what [pageBackdrop]
        // takes its still of; the menus themselves are drawn outside it.
        Row(Modifier.fillMaxSize().snapshotBackdropSource(pageBackdrop)) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                AnimatedContent(
                    // Whatever is on top of the back stack — see [AppNavigator].
                    targetState = when {
                        visibleEntry != null -> AppDestination.Route(visibleEntry)
                        detail != null -> AppDestination.Release(detail.browseId)
                        else -> AppDestination.Tab(selectedTab)
                    },
                    // A route's entry is copied when the release pages under it
                    // change, which is not a different page.
                    contentKey = { it.key },
                    // Tabs swap outright; everything else crossfades.
                    //
                    // A tab is not a place you travel to — the bar is the whole
                    // navigation and it carries its own movement — so a fade
                    // between two of them only ever reads as a stutter. And it
                    // cannot read as anything else: neither page paints a
                    // background, so a crossfade dissolves both through to the
                    // window and the switch dips through a dimmer frame in the
                    // middle. Pushing a page or raising Settings is a real
                    // change of context and keeps the fade.
                    //
                    // "Show all" swapping with the Library tab underneath it is
                    // the same case as a tab swap, not a pushed page: it's still
                    // that tab, just laid out as a grid instead of a row, sharing
                    // its background rather than painting its own — so this one
                    // pair gets the tab's no-fade swap too, in both directions, or
                    // the dip through a dim frame shows up on every hold of a
                    // card there. A card opened *from* the grid is a real page
                    // and keeps the fade, same as one opened from the row.
                    transitionSpec = {
                        val initial = initialState
                        val target = targetState
                        val tabSwap = initial is AppDestination.Tab && target is AppDestination.Tab
                        val libraryTab = AppDestination.Tab(TAB_LIBRARY)
                        fun AppDestination.isShowAll() =
                            (this as? AppDestination.Route)?.entry?.route is AppRoute.LibraryShowAll
                        val libraryShowAllSwap = (initial.isShowAll() && target == libraryTab) ||
                            (target.isShowAll() && initial == libraryTab)
                        if (tabSwap || libraryShowAllSwap) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            fadeIn(tween(180)) togetherWith fadeOut(tween(180))
                        }
                    },
                    modifier = Modifier
                        .hazeSource(hazeState)
                        .then(
                            if (glassActive) {
                                Modifier
                                    // Not under "reduce dynamic blur": nothing
                                    // samples the layer then, and recording a
                                    // whole page into one for no reader is the
                                    // cost that setting exists to remove.
                                    .then(
                                        if (glassSamplesBackdrop) {
                                            Modifier.layerBackdrop(appBackdrop)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    // Every page's scroll passes through here, so
                                    // the glass bar collapses on all of them
                                    // without each one having to know about it.
                                    .nestedScroll(navBarScroll)
                            } else {
                                Modifier
                            },
                        ),
                    label = "content",
                ) { destination ->
                    // Every branch below reads `destination` rather than the
                    // stack that produced it. The two are the same thing only
                    // for the page being entered: the one on its way out is
                    // still composed, and asking it what is on top *now* has it
                    // redraw itself as its own replacement — which then fades
                    // out from under the identical copy fading in behind it.
                    val key = destination.key
                    val pageRoute = (destination as? AppDestination.Route)?.entry?.route
                    val live = (destination as? AppDestination.Release)?.let { release ->
                        detailStack.lastOrNull()?.takeIf { it.browseId == release.browseId }
                    }
                    // Held for the same reason, one step further on: a popped
                    // page is off the stack before it has finished animating
                    // out, so `live` goes null under it and it would spend its
                    // exit drawing whatever is underneath instead of itself.
                    // Per slot, since each is remembered against its own key.
                    val held = remember(key) { mutableStateOf(live) }
                    if (live != null) held.value = live
                    val page = held.value
                    // This state belongs to this AnimatedContent slot, not to
                    // whichever detail happens to be at the top of the stack
                    // while the slot is fading out.
                    val pageDetailListState = remember(key) {
                        detailListStates.getOrPut(key) { LazyListState() }
                    }
                    val pageContent: @Composable () -> Unit = {
                        if (pageRoute == AppRoute.History) {
                            HistoryScreen(
                                state = historyState,
                                listState = historyListState,
                                currentSong = player.song,
                                isPlaying = player.isPlaying,
                                onSongClick = { songs, index ->
                                    playFrom(songs, index, QueueSource(historyLabel, PlaybackSourceType.HISTORY))
                                },
                                onSongLongPress = { song ->
                                    menuFromHistory = true
                                    openSongMenu(song)
                                },
                                onSongSwipe = onSongSwipe,
                                onRetry = viewModel::loadHistory,
                                contentPadding = listPadding,
                            )
                        } else if (pageRoute is AppRoute.LibraryShowAll) {
                            pageRoute.shelf.let { shelf ->
                                LibraryGridPage(
                                    shelf = shelf,
                                    gridState = libraryShowAllGridState,
                                    onItemClick = onLibraryItemClick,
                                    onItemLongPress = onBrowseLongPress,
                                    // Only the Playlists shelf can grow one — see
                                    // [PlaylistShelf].
                                    onNewPlaylist = if (shelf.title == YtMusicRepository.PLAYLISTS_SHELF) {
                                        { creatingPlaylist = true }
                                    } else {
                                        null
                                    },
                                    contentPadding = listPadding,
                                )
                            }
                        } else if (pageRoute == AppRoute.Moods) {
                            MoodGridPage(
                                gridState = moodsGridState,
                                onClick = playMood,
                                contentPadding = listPadding,
                            )
                        } else if (pageRoute == AppRoute.Replay) {
                            ReplayScreen(
                                state = replay,
                                holder = account?.name.orEmpty(),
                                onPeriodChange = setReplayPeriod,
                                onOpenStory = { replayStory = it },
                                // A track tapped on a chart is one the user already
                                // knows they like, so it starts a station off itself
                                // rather than queueing the chart it was on — the
                                // same reading [playRadio] makes of a search hit.
                                onPlaySong = { song ->
                                    playRadio(song, QueueSource(replayLabel, PlaybackSourceType.REPLAY))
                                },
                                // Pushed over Replay, so back returns to it.
                                onOpenArtist = { id, name ->
                                    openByName(id, name, null, BrowseType.ARTIST)
                                },
                                onOpenAlbum = { id, title, artist, art ->
                                    openByName(id, title, artist, BrowseType.ALBUM, art)
                                },
                                onShare = {
                                    replaySharePage = null
                                    showReplayShare = true
                                },
                                contentPadding = listPadding,
                                listState = replayListState,
                                landingPage = replayLandingPage,
                            )
                        } else if (pageRoute == AppRoute.Spotify) {
                            SpotifyLibraryScreen(
                                onOpenPlaylist = { playlist ->
                                    viewModel.openDetail(
                                        browseId = SPOTIFY_PAGE_PREFIX + playlist.id,
                                        title = playlist.name,
                                        subtitle = playlist.owner ?: context.getString(R.string.spotify),
                                        thumbnailUrl = playlist.imageUrl,
                                        type = BrowseType.PLAYLIST,
                                    )
                                },
                                onOpenLogin = { showSpotifyLogin = true },
                                onOpenTokenSetup = { showSpotifyCanvasAuth = true },
                                contentPadding = listPadding,
                            )
                        } else if (pageRoute == AppRoute.Discord) {
                            DiscordScreen(
                                song = player.song,
                                positionMs = player.position.positionMs,
                                durationMs = player.durationMs,
                                onOpenLogin = { showDiscordLogin = true },
                                onOpenDialog = { discordDialog = it },
                                contentPadding = listPadding,
                            )
                        } else if (pageRoute == AppRoute.AccountScrobbling) {
                            AccountAndScrobblingScreen(
                                signedIn = signedIn,
                                account = account,
                                channelName = selectedChannelName,
                                onSignIn = {
                                    navigator.clear()
                                    webSession = WebSessionMode.SIGN_IN
                                },
                                onSwitchChannel = {
                                    viewModel.loadChannels()
                                    showAccountSelector = true
                                },
                                onSignOut = { viewModel.signOut() },
                                onOpenListenBrainzLogin = { showListenBrainzLogin = true },
                                onOpenLastfmLogin = { showLastfmLogin = true },
                                onOpenDiscord = { navigator.push(AppRoute.Discord) },
                                onOpenSpotify = { navigator.push(AppRoute.Spotify) },
                                contentPadding = listPadding,
                            )
                        } else if (pageRoute == AppRoute.Sources) {
                            SourcesScreen(
                                contentPadding = listPadding,
                                onEditSource = { editingSource = it },
                                onEditWebDav = { showWebDavEditor = true },
                                onEditSmb = { showSmbEditor = true },
                                onConfirmJioSaavn = { confirmJioSaavn = true },
                            )
                        } else if (pageRoute == AppRoute.Downloads) {
                            DownloadManagerPage(
                                onCleared = { navigator.pop() },
                                contentPadding = listPadding,
                            )
                        } else if (pageRoute == AppRoute.WearOs) {
                            WearOsScreen(contentPadding = listPadding)
                        } else if (pageRoute == AppRoute.ListenTogether) {
                            ListenTogetherScreen(
                                signedIn = signedIn,
                                inviteCode = activeJamInviteCode,
                                inviteServer = activeJamInviteServer,
                                onInviteHandled = {
                                    activeJamInviteCode = null
                                    activeJamInviteServer = null
                                },
                                onSignIn = {
                                    navigator.clear()
                                    webSession = WebSessionMode.SIGN_IN
                                },
                                contentPadding = listPadding,
                                onEditServer = { editingPartyServer = true },
                            )
                        } else if (pageRoute == AppRoute.Equalizer) {
                            EqualizerScreen(
                                contentPadding = listPadding,
                                onOpenPresets = { navigator.push(AppRoute.EqualizerPresets) },
                            )
                        } else if (pageRoute == AppRoute.EqualizerPresets) {
                            EqualizerPresetPage(
                                onChosen = { navigator.pop() },
                                contentPadding = listPadding,
                            )
                        } else if (pageRoute is AppRoute.SettingsChoice) {
                            SettingsChoicePage(
                                choice = pageRoute.choice,
                                onChosen = { navigator.pop() },
                                onConfirmBitPerfect = { confirmBitPerfect = it },
                                contentPadding = listPadding,
                            )
                        } else if (pageRoute == AppRoute.Settings || pageRoute is AppRoute.SettingsSection) {
                            // The main page and every category's page are the
                            // one screen, each showing its own share of the rows.
                            SettingsScreen(
                                section = (pageRoute as? AppRoute.SettingsSection)?.category,
                                onOpenSection = { navigator.push(AppRoute.SettingsSection(it)) },
                                onOpenChoice = { navigator.push(AppRoute.SettingsChoice(it)) },
                                windowWidth = windowWidth,
                                signedIn = signedIn,
                                account = account,
                                onSignIn = {
                                    navigator.clear()
                                    webSession = WebSessionMode.SIGN_IN
                                },
                                onSignOut = { viewModel.signOut() },
                                onAccountScrobbling = { navigator.push(AppRoute.AccountScrobbling) },
                                onEqualizer = { navigator.push(AppRoute.Equalizer) },
                                onOpenReplay = {
                                    replayLandingPage = ReplayStoryPage.INTRO
                                    navigator.push(AppRoute.Replay)
                                },
                                onLyricsSources = { showLyricsSources = true },
                                onTranslationLanguage = { showTranslationLanguage = true },
                                onSources = { navigator.push(AppRoute.Sources) },
                                onListenTogether = { navigator.push(AppRoute.ListenTogether) },
                                onWearOs = { navigator.push(AppRoute.WearOs) },
                                onSpotifyCanvasAuth = { showSpotifyCanvasAuth = true },
                                onAppLanguage = { showAppLanguage = true },
                                onEditSignatureApi = { editingSignatureApi = true },
                                onConfirmBitPerfect = { confirmBitPerfect = it },
                                onConfirmFixedVolume = { confirmFixedVolume = true },
                                contentPadding = listPadding,
                            )
                        } else if (page != null) {
                            // Local Music, Downloads and the other device folders are
                            // this same release page: the header any playlist has, then
                            // Songs / Artists / Albums under it. An album or artist
                            // picked there opens as a page of its own — see LocalGroups.
                            val folder = if (page.browseId.isDeviceFolder()) {
                                val isDownloads = page.browseId == "local:downloads"
                                val isCache = page.browseId == CACHE_FOLDER_BROWSE_ID
                                // The whole device: the one folder that is an offline
                                // player in its own right, with playlists of its own
                                // and the directories the files are kept in.
                                val isDevice = page.browseId == "local:all"
                                val folderSongs = (page.songs as? UiState.Success)?.data.orEmpty()
                                // Only the Downloads folder has releases behind it: Local
                                // Music is files this app never asked for, so there is
                                // nothing on record about how they were grouped. Keyed on
                                // the record as well as the list, so downloading an album
                                // while its folder is open adds the folder rather than
                                // waiting for the page to be reopened.
                                val downloadCollections = remember(folderSongs, savedCollections, isDownloads) {
                                    if (isDownloads) Downloads.collectionsAmong(folderSongs) else emptyList()
                                }
                                LocalFolder(
                                    state = localFolderStates.getOrPut(page.browseId) { LocalFolderState() },
                                    sort = folderSortOf(page.browseId),
                                    viewType = folderViewTypeOf(page.browseId),
                                    collections = downloadCollections,
                                    selectable = isDownloads || isCache,
                                    onOpenGroup = viewModel::openLocalGroup,
                                    onGroupLongPress = { group ->
                                        openBrowseMenu(
                                            BrowseTarget(
                                                browseId = group.browseId,
                                                title = group.title,
                                                subtitle = group.subtitle,
                                                thumbnailUrl = group.thumbnailUrl,
                                                type = group.type,
                                                songs = group.songs,
                                                downloadId = group.downloadId,
                                            ),
                                        )
                                    },
                                    onDelete = if (isDownloads) {
                                        { selected ->
                                            scope.launch {
                                                selected.forEach { song -> Downloads.delete(context, song.videoId) }
                                            }
                                        }
                                    } else {
                                        null
                                    },
                                    onUpload = if (isDownloads &&
                                        com.music.bitchord.data.webdav.WebDavConfig.isConfigured(webdavUrl)
                                    ) {
                                        { selected -> uploadToWebDav(selected) }
                                    } else {
                                        null
                                    },
                                    onDownload = if (isCache) downloadFromCache else null,
                                    onRemoveFromCache = if (isCache) removeFromCache else null,
                                    tabs = if (isDevice) LocalFolderTab.DEVICE else LocalFolderTab.BASIC,
                                    playlists = if (isDevice) offlinePlaylistGroups else emptyList(),
                                    onCreatePlaylist = if (isDevice) ({ creatingOfflinePlaylist = true }) else null,
                                )
                            } else {
                                null
                            }
                            // An album page's rows carry no album name of their own — the
                            // release is billed once, in the header the rows hang under — so
                            // the page title is stamped on as they leave for the download
                            // queue or the track menu. Without it every track saved from an
                            // album arrives in the Downloads folder with nothing to group it
                            // under, and its Albums tab stays empty however much is in it.
                            val withAlbum: (Song) -> Song = { song ->
                                if (page.type == BrowseType.ALBUM) {
                                    song.copy(albumName = song.albumName ?: page.title)
                                } else {
                                    song
                                }
                            }
                            // The visible list can be one render behind the page
                            // while fill-in is completing. For a local release
                            // handoff, cancel that fill and use the newest complete
                            // page snapshot; Cast deliberately keeps the old full
                            // queue path.
                            fun playDetailSongs(songs: List<Song>, index: Int) {
                                // Do not cancel a page's fill-in merely because a
                                // listener tapped while the host owns playback.
                                if (ListenTogether.state.value.controlsLocked) {
                                    refusedByHost()
                                    return
                                }
                                val source = QueueSource(
                                    page.title,
                                    PlaybackSourceType.BROWSE,
                                    page.browseId,
                                )
                                val selected = songs.getOrNull(index)
                                val pageSongKeys =
                                    (page.songs as? UiState.Success)?.data.orEmpty().mapTo(HashSet()) { it.contextEntryKey() }
                                val useBounded =
                                    (page.type == BrowseType.ALBUM || page.type == BrowseType.PLAYLIST) &&
                                        selected?.contextEntryKey()?.let(pageSongKeys::contains) == true &&
                                        !CastController.state.value.casting
                                val latestPage = if (useBounded) {
                                    viewModel.takePlaybackContext(page.browseId)
                                } else {
                                    null
                                }
                                val latestSongs = latestPage
                                    ?.let { (it.songs as? UiState.Success)?.data.orEmpty() }
                                    ?.map(withAlbum)
                                    ?.takeIf { it.isNotEmpty() }
                                val queue = latestSongs ?: songs.map(withAlbum)
                                if (queue.isEmpty()) return
                                val queueIndex = selected?.let { tapped ->
                                    queue.indexOfFirst { it.contextEntryKey() == tapped.contextEntryKey() }
                                }?.takeIf { it >= 0 } ?: index
                                playFrom(
                                    queue,
                                    queueIndex.coerceIn(queue.indices),
                                    source,
                                    boundedContext = useBounded,
                                    continuationToken = latestPage?.continuation ?: page.continuation,
                                )
                            }
                            // This page's songs being rearranged, drawn in place of its list.
                            val reordering = reorderTarget?.takeIf { it.browseId == page.browseId }
                            DetailScreen(
                                page = page,
                                currentSong = player.song,
                                isPlaying = player.isPlaying,
                                listState = pageDetailListState,
                                activeShelf = detailActiveShelf,
                                onActiveShelfChange = { shelf ->
                                    if (shelf != null) navigator.push(AppRoute.ArtistShelf(page.browseId, shelf))
                                    else navigator.pop()
                                },
                                // Album/playlist playback hands the page snapshot to
                                // the service, which then owns continuation loading.
                                onSongClick = ::playDetailSongs,
                                onSongLongPress = { openSongMenu(withAlbum(it)) },
                                onSongSwipe = { onSongSwipe(withAlbum(it)) },
                                onShuffle = { songs ->
                                    if (songs.isNotEmpty()) {
                                        // Shuffle goes on first so the queue is built shuffled
                                        // as it is set — the random pick here only decides
                                        // which track leads it.
                                        QueueShuffle.enableForNextQueue()
                                        playDetailSongs(songs, songs.indices.random())
                                    }
                                },
                                onSectionItemClick = { item ->
                                    val video = shelfSong(item)?.takeIf { item.browseId == null }
                                    if (video != null) {
                                        // An artist's Videos shelf plays, from the page it is on.
                                        playRadio(video, QueueSource(page.title, PlaybackSourceType.BROWSE, page.browseId))
                                    } else {
                                        item.browseId?.let { id ->
                                            viewModel.openDetail(
                                                browseId = id,
                                                title = item.title,
                                                subtitle = item.subtitle,
                                                thumbnailUrl = item.thumbnailUrl,
                                                type = BrowseType.ALBUM,
                                            )
                                        }
                                    }
                                },
                                onSectionItemLongPress = onShelfLongPress,
                                // Only the page on its way in answers to the bar's
                                // search button; one fading out keeps its list whole.
                                searching = detailSearching && page.browseId == detail?.browseId,
                                onSearchingChange = { detailSearching = it },
                                onArtistClick = { id, name ->
                                    viewModel.openDetail(id, name, "Artist", null, BrowseType.ARTIST)
                                },
                                onAddSuggested = { song -> viewModel.addSuggestedSong(page.browseId, song) },
                                // Saving is an account action, so it isn't offered to a
                                // guest at all — same as the like and add-to-playlist rows
                                // in the track menu.
                                onToggleLibrary = if (signedIn) {
                                    { viewModel.toggleLibrary(page.browseId) }
                                } else {
                                    null
                                },
                                // Same rule for the artist page's subscribe circle:
                                // a channel subscription is the account's, so a
                                // guest is never shown the button.
                                onToggleSubscription = if (signedIn) {
                                    { viewModel.toggleSubscription(page.browseId) }
                                } else {
                                    null
                                },
                                releaseLibrary = releaseLibrary,
                                onLoadReleaseLibrary = viewModel::loadReleaseLibrary,
                                onToggleReleaseLibrary = if (signedIn) {
                                    viewModel::toggleReleaseLibrary
                                } else {
                                    null
                                },
                                songSort = songSort,
                                folder = folder,
                                contentPadding = listPadding,
                                reorder = if (reordering != null) {
                                    { palette ->
                                        ReorderPlaylistPage(
                                            playlist = reordering,
                                            entries = reorderEntries,
                                            saving = reorderSaving,
                                            palette = palette,
                                            contentPadding = PaddingValues(
                                                bottom = listPadding.calculateBottomPadding(),
                                            ),
                                            onClose = { reorderTarget = null },
                                            onSave = { reordered ->
                                                val original = (reorderEntries as? UiState.Success)?.data.orEmpty()
                                                reorderSaving = true
                                                viewModel.reorderPlaylist(reordering, original, reordered) { saved ->
                                                    reorderSaving = false
                                                    showQueueNotice(
                                                        context.getString(
                                                            if (saved) R.string.playlist_reordered else R.string.reorder_failed,
                                                        ),
                                                    )
                                                    if (saved && reorderTarget == reordering) reorderTarget = null
                                                }
                                            },
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                        } else when ((destination as? AppDestination.Tab)?.index ?: selectedTab) {
                            TAB_HOME -> HomeScreen(
                                state = homeState,
                                listState = homeListState,
                                currentSong = player.song,
                                isPlaying = player.isPlaying,
                                title = stringResource(R.string.listen_now),
                                signedIn = signedIn,
                                onSignIn = { webSession = WebSessionMode.SIGN_IN },
                                onItemClick = { item, shelfTitle ->
                                    val song = shelfSong(item)
                                    // Hoisted because ShelfItem lives in :shared, and
                                    // Kotlin will not smart-cast a public nullable
                                    // property declared in another module.
                                    val browseId = item.browseId
                                    when {
                                        song != null -> playRadio(
                                            song,
                                            QueueSource(shelfTitle, PlaybackSourceType.HOME),
                                        )
                                        browseId != null -> viewModel.openDetail(
                                            browseId = browseId,
                                            title = item.title,
                                            subtitle = item.subtitle,
                                            thumbnailUrl = item.thumbnailUrl,
                                        )
                                    }
                                },
                                onItemLongPress = onShelfLongPress,
                                onRetry = viewModel::loadHome,
                                refreshing = MainViewModel.Feed.HOME in refreshing,
                                onRefresh = { viewModel.refresh(MainViewModel.Feed.HOME) },
                                pullState = homePull,
                                contentPadding = listPadding,
                                onLoadMore = viewModel::loadMoreHome,
                                loadingMore = homeLoadingMore,
                                recentlyPlayedLoading = homeRecentlyPlayedLoading,
                                topPicks = topPicks,
                                topPicksLoading = topPicksLoading,
                                listenerName = account?.name,
                                onTopPickClick = openTopPick,
                                onMoodClick = playMood,
                                onMoodsShowAll = { navigator.push(AppRoute.Moods) },
                                artworkMotion = artworkMotion,
                                onTopPickLongPress = { pick ->
                                    pick.browseId?.let { browseId ->
                                        onShelfLongPress(
                                            ShelfItem(
                                                title = pick.title,
                                                subtitle = pick.artistName.orEmpty(),
                                                thumbnailUrl = pick.images.firstOrNull(),
                                                videoId = null,
                                                browseId = browseId,
                                            ),
                                        )
                                    }
                                },
                            )
                            TAB_EXPLORE -> selectedMoodGenre?.let { category ->
                                MoodGenrePlaylistsScreen(
                                    title = category.title,
                                    state = moodGenreShelves,
                                    listState = moodGenreListState,
                                    onItemClick = { item ->
                                        item.videoId?.let { videoId ->
                                            playRadio(
                                                Song(
                                                    videoId = videoId,
                                                    title = item.title,
                                                    artist = InnertubeParser.artistFromSubtitle(item.subtitle),
                                                    thumbnailUrl = item.thumbnailUrl,
                                                    isVideo = item.isVideo,
                                                ),
                                                QueueSource(
                                                    category.title,
                                                    PlaybackSourceType.EXPLORE,
                                                    category.browseId,
                                                ),
                                            )
                                        } ?: item.browseId?.let { browseId ->
                                            viewModel.openDetail(
                                                browseId = browseId,
                                                title = item.title,
                                                subtitle = item.subtitle,
                                                thumbnailUrl = item.thumbnailUrl,
                                            )
                                        }
                                    },
                                    onRetry = { viewModel.openMoodGenre(category) },
                                    contentPadding = listPadding,
                                )
                            } ?: ExploreScreen(
                                state = exploreState,
                                listState = exploreListState,
                                onCategoryClick = viewModel::openMoodGenre,
                                onRetry = viewModel::loadExplore,
                                refreshing = MainViewModel.Feed.EXPLORE in refreshing,
                                onRefresh = { viewModel.refresh(MainViewModel.Feed.EXPLORE) },
                                pullState = explorePull,
                                contentPadding = listPadding,
                            )
                            TAB_SEARCH -> SearchScreen(
                                query = query,
                                onQueryChange = viewModel::onQueryChange,
                                filter = filter,
                                currentSong = player.song,
                                isPlaying = player.isPlaying,
                                onFilterChange = viewModel::onFilterChange,
                                results = results,
                                loadingMore = searchLoadingMore,
                                onLoadMore = viewModel::loadMoreSearchResults,
                                listState = searchListState,
                                scrollResetTrigger = searchScrollReset,
                                focusRequested = searchFocusRequested,
                                onFocusHandled = { searchFocusRequested = false },
                                // Search hits are alternatives to each other, not a running
                                // order — play the one tapped and build a station from it.
                                onSongClick = { songs, index ->
                                    songs.getOrNull(index)?.let { song ->
                                        viewModel.recordEntity(SearchHistoryEntity(
                                            id = song.videoId,
                                            title = song.title,
                                            subtitle = song.artist.ifEmpty { "" },
                                            artworkUrl = song.thumbnailUrl,
                                            entityType = EntityType.TRACK,
                                        ))
                                        playRadio(song, QueueSource(searchLabel, PlaybackSourceType.SEARCH))
                                    }
                                },
                                onSongLongPress = openSongMenu,
                                onSongSwipe = onSongSwipe,
                                onTopResultPlay = { song ->
                                    viewModel.recordEntity(SearchHistoryEntity(
                                        id = song.videoId,
                                        title = song.title,
                                        subtitle = song.artist.ifEmpty { "" },
                                        artworkUrl = song.thumbnailUrl,
                                        entityType = EntityType.TRACK,
                                    ))
                                    playRadio(song, QueueSource(searchLabel, PlaybackSourceType.SEARCH))
                                },
                                onTopResultPlaylist = { song ->
                                    viewModel.recordEntity(SearchHistoryEntity(
                                        id = song.videoId,
                                        title = song.title,
                                        subtitle = song.artist.ifEmpty { "" },
                                        artworkUrl = song.thumbnailUrl,
                                        entityType = EntityType.TRACK,
                                    ))
                                    viewModel.loadPlaylists()
                                    playlistTarget = song
                                },
                                onBrowseClick = { item ->
                                    viewModel.recordEntity(SearchHistoryEntity(
                                        id = item.browseId ?: "",
                                        title = item.title,
                                        subtitle = item.subtitle.ifBlank { "" },
                                        artworkUrl = item.thumbnailUrl,
                                        entityType = when (item.type) {
                                            BrowseType.ALBUM -> EntityType.ALBUM
                                            BrowseType.ARTIST -> EntityType.ARTIST
                                            BrowseType.PLAYLIST -> EntityType.PLAYLIST
                                            BrowseType.OTHER -> EntityType.OTHER
                                        },
                                    ))
                                    viewModel.openDetail(
                                        browseId = item.browseId,
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        thumbnailUrl = item.thumbnailUrl,
                                        type = item.type,
                                    )
                                },
                                onBrowseLongPress = { item ->
                                    // A search row does say what it is, so its own type is
                                    // better than what the browse id can be read to mean.
                                    if (item.type != BrowseType.ARTIST) {
                                        openBrowseMenu(
                                            BrowseTarget(
                                                browseId = item.browseId,
                                                title = item.title,
                                                subtitle = item.subtitle,
                                                thumbnailUrl = item.thumbnailUrl,
                                                type = item.type,
                                                downloadId = downloadIdFor(item.browseId),
                                            ),
                                        )
                                    }
                                },
                                history = searchHistory,
                                suggestions = searchSuggestions,
                                typeaheadResults = viewModel.typeaheadResults.collectAsStateWithLifecycle().value,
                                onSubmit = viewModel::submitSearch,
                                // Suggestions land in search history via searchFor → recordSearch.
                                // History items (onHistoryClick) navigate/play without re-logging.
                                onSuggestionClick = viewModel::searchFor,
                                onHistoryClick = { entity ->
                                    // Tap a history entity: navigate to it or play it directly.
                                    // Do NOT recordEntity here — tapping an existing history item
                                    // must not update its timestamp and push it to the top.
                                    when (entity.entityType) {
                                        EntityType.TRACK -> {
                                            // Play the track by its video id
                                            playRadio(
                                                com.music.bitchord.data.model.Song(
                                                    videoId = entity.id,
                                                    title = entity.title,
                                                    artist = entity.subtitle,
                                                    thumbnailUrl = entity.artworkUrl,
                                                ),
                                                QueueSource(entity.title, PlaybackSourceType.SEARCH),
                                            )
                                        }
                                        EntityType.ALBUM, EntityType.ARTIST, EntityType.PLAYLIST, EntityType.OTHER -> {
                                            viewModel.openDetail(
                                                browseId = entity.id,
                                                title = entity.title,
                                                subtitle = entity.subtitle,
                                                thumbnailUrl = entity.artworkUrl,
                                            )
                                        }
                                    }
                                },
                                onHistoryRemove = viewModel::removeSearch,
                                onHistoryClear = viewModel::clearSearchHistory,
                                onTypeaheadLongPress = openSongMenu,
                                contentPadding = listPadding,
                                // Snug under the bar rather than a page's breathing
                                // gap below it: the field is in the bar (see the bar's
                                // accessory), so what comes next is the field's own
                                // suggestions and filters, and a gap reads as a break.
                                topPadding = topBarHeight() + 4.dp,
                                showField = false,
                                // The settings page's own two-state selector, so the
                                // app has one look for "pick one of these".
                                sourceSwitcher = {
                                    SegmentedControl(
                                        options = listOf(
                                            stringResource(R.string.search_source_youtube),
                                            stringResource(R.string.library),
                                            stringResource(R.string.search_source_addons),
                                        ),
                                        selectedIndex = searchSource.ordinal,
                                        onSelect = { index -> onSearchSourceChange(SearchSource.entries[index]) },
                                    )
                                },
                                searchingLibrary = searchSource == SearchSource.LIBRARY,
                                libraryResults = libraryResults,
                                searchingAddons = searchSource == SearchSource.ADDONS,
                                addonResults = addonResults,
                                isBlocked = { song ->
                                    val configId = SourceRegistry.parseTrackKey(song.videoId)?.first
                                    !SourceRegistry.permitsForPlayback(configId, addonCeiling)
                                },
                                onBlockedSongClick = { song -> blockedAddonSong = song },
                                addonHistory =viewModel.addonSearchHistory.collectAsStateWithLifecycle().value,
                                onAddonHistoryClick = { term ->
                                    viewModel.onQueryChange(term)
                                    viewModel.submitSearch()
                                },
                                onAddonHistoryRemove = viewModel::removeAddonSearch,
                                onAddonHistoryClear = viewModel::clearAddonSearchHistory,
                                // The same queue opening the Local Music folder and
                                // tapping the row there would give.
                                onLibrarySongClick = { songs, index ->
                                    playFrom(
                                        songs,
                                        index,
                                        if (searchSource == SearchSource.ADDONS) {
                                            QueueSource(
                                                context.getString(R.string.search_source_addons),
                                                PlaybackSourceType.BROWSE,
                                                "addons:search",
                                            )
                                        } else {
                                            QueueSource(
                                                context.getString(R.string.local_music),
                                                PlaybackSourceType.BROWSE,
                                                "local:all",
                                            )
                                        },
                                    )
                                },
                            )
                            else -> LibraryScreen(
                                signedIn = signedIn,
                                state = libraryState,
                                listState = libraryListState,
                                onShelfItemClick = onLibraryItemClick,
                                // Every shelf here has a menu behind it now — the account's
                                // own playlists get rename and delete on top of what a saved
                                // album or a Liked Music card gets. Holding an artist still
                                // does nothing; see [onBrowseLongPress].
                                onShelfItemLongPress = onBrowseLongPress,
                                onNewPlaylist = { creatingPlaylist = true },
                                onImportSpotifyPlaylist = { showSpotifyImportDialog = true },
                                onShowAll = { shelf -> navigator.push(AppRoute.LibraryShowAll(shelf)) },
                                replay = {
                                    LibraryReplayEntry(
                                        cards = replayCards,
                                        loading = replay.loading,
                                        holder = account?.name.orEmpty(),
                                        memberSince = replay.memberSince,
                                        onOpenReplay = { page ->
                                            replayLandingPage = page
                                            navigator.push(AppRoute.Replay)
                                        },
                                    )
                                },
                                onSignIn = { webSession = WebSessionMode.SIGN_IN },
                                onRetry = viewModel::loadLibrary,
                                refreshing = MainViewModel.Feed.LIBRARY in refreshing,
                                onRefresh = { viewModel.refresh(MainViewModel.Feed.LIBRARY) },
                                pullState = libraryPull,
                                contentPadding = listPadding,
                                links = libraryLinks(),
                                deviceItems = libraryDeviceItems(downloadedReleases) + localPlaylistItems,
                            )
                        }
                    }
                    // A pushed page keeps its saved state — a scroll position,
                    // a search typed into Settings — for as long as it is on
                    // the stack, so coming back to it finds it as it was left.
                    // Tabs keep theirs hoisted above, as they always have.
                    //
                    // The playing bars hold still with the player up, the app in
                    // the background, or this page on its way out: each frame of
                    // them redraws the page under the glass, for nobody.
                    val barsMotion = artworkMotion && transition.targetState == EnterExitState.Visible
                    CompositionLocalProvider(LocalPlayingBarsMotion provides barsMotion) {
                        if (destination is AppDestination.Tab) {
                            pageContent()
                        } else {
                            pageKeysSeen += key
                            pageStates.SaveableStateProvider(key, pageContent)
                        }
                    }
                }

                // The top-bar footprint is transparent on every page so the
                // shared app-level gradient is continuous; its controls float
                // as separate circles in both the glass and blur materials.
                val isDetailVisible = detail != null &&
                    (detail.type == BrowseType.ALBUM ||
                        detail.type == BrowseType.PLAYLIST ||
                        detail.type == BrowseType.ARTIST) &&
                    !isLocalDetail
                val chromePageColor = if (isDetailVisible) {
                    detailPalette.background
                } else {
                    MaterialTheme.colorScheme.background
                }
                // Apple's scroll edge: what passes up under the status bar and the
                // controls is blurred — hardest at the top, none just below the
                // controls — rather than faded out to the page colour. It is a
                // Liquid Glass option: with glass off, the Progressive Blur setting
                // off, or under reduce dynamic blur, the bottom floor's own fade
                // stands in, turned upside down, so both edges share its curve
                // and colour.
                // On an artist page the photograph and logo run up under the status
                // bar, and either across them would only muddy them. It comes in as
                // the page scrolls past that header, when content starts passing
                // under the bar and needs the cover.
                //
                // Home's wordmark sits up there too, lifted under the bar by
                // HOME_TITLE_LIFT, and at rest the fade washed it out. It is held
                // off the same way, from the first pixel of scroll rather than
                // partway through, since the wordmark is the top of its heading.
                val artistListState = detail
                    ?.takeIf { isDetailVisible && it.type == BrowseType.ARTIST }
                    ?.let { detailListStates.getOrPut(it.browseId) { LazyListState() } }
                val homeHeadingVisible = route == null && detail == null && selectedTab == TAB_HOME
                val headerListState = artistListState ?: homeListState.takeIf { homeHeadingVisible }
                val fadeStart = if (artistListState != null) TOP_FADE_START else 0f
                val fadeRamp = if (artistListState != null) TOP_FADE_RAMP else 1f
                val topFadeAlpha by remember(headerListState, fadeStart, fadeRamp) {
                    derivedStateOf {
                        val list = headerListState ?: return@derivedStateOf 1f
                        if (list.firstVisibleItemIndex > 0) return@derivedStateOf 1f
                        val header = list.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.index == 0 }?.size?.toFloat()
                            ?: return@derivedStateOf 0f
                        ((list.firstVisibleItemScrollOffset - header * fadeStart) /
                            (header * fadeRamp)).coerceIn(0f, 1f)
                    }
                }
                if (reduceDynamicBlur || !glassActive || !progressiveBlur) {
                    BottomFadeScrim(
                        pageColor = chromePageColor,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .rotate(180f)
                            .graphicsLayer { alpha = topFadeAlpha },
                    )
                } else {
                    TopFadeBlur(
                        hazeState = hazeState,
                        pageColor = chromePageColor,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .graphicsLayer { alpha = topFadeAlpha },
                    )
                }

                FrostedTopBar(
                    title = when (route) {
                        AppRoute.Spotify -> stringResource(R.string.spotify)
                        AppRoute.Downloads -> stringResource(R.string.downloads)
                        AppRoute.Discord -> "Discord"
                        AppRoute.History -> stringResource(R.string.history)
                        is AppRoute.LibraryShowAll -> route.shelf.title
                        is AppRoute.ArtistShelf -> route.shelf.title
                        AppRoute.Moods -> stringResource(R.string.find_your_mood)
                        AppRoute.AccountScrobbling -> stringResource(R.string.account_scrobbling)
                        AppRoute.Sources -> stringResource(R.string.sources)
                        AppRoute.ListenTogether -> stringResource(R.string.listen_together)
                        AppRoute.WearOs -> stringResource(R.string.wear_os_page_title)
                        AppRoute.Equalizer -> stringResource(R.string.equalizer)
                        AppRoute.Settings -> stringResource(R.string.settings)
                        is AppRoute.SettingsSection -> stringResource(route.category.title)
                        is AppRoute.SettingsChoice -> stringResource(route.choice.title)
                        AppRoute.EqualizerPresets -> stringResource(R.string.equalizer_preset)
                        AppRoute.Replay -> stringResource(R.string.replay)
                        null -> when {
                            detail != null && detailActiveShelf != null -> detailActiveShelf?.title.orEmpty()
                            detail != null -> detail.title
                            selectedMoodGenre != null -> selectedMoodGenre?.title.orEmpty()
                            else -> tabs[selectedTab].let {
                                if (it.label == "Play") stringResource(R.string.listen_now) else it.label
                            }
                        }
                    },
                    // Every page, in either material, uses separated floating
                    // circles over the shared gradient — no full-width pane.
                    transparentBackdrop = true,
                    artworkPageChrome = true,
                    backButtonHazeState = hazeState,
                    trailingTitle = if (detail != null && detailActiveShelf != null) detail.title else null,
                    // Search has no large in-list header to hand the title back to —
                    // the field takes that space — so its bar title is always up.
                    scrolled = {
                        when {
                            // The page leads with its own large "Replay", so the bar
                            // stays out of the way until that has been scrolled off.
                            route == AppRoute.Replay -> replayScrolled
                            route != null -> true
                            detail != null && detailActiveShelf != null -> true
                            detail != null -> detailScrolled
                            selectedMoodGenre != null -> true
                            else -> scrolled || selectedTab == TAB_SEARCH
                        }
                    },
                    refreshing = currentFeed != null && currentFeed in refreshing,
                    pullFraction = { currentPull?.distanceFraction ?: 0f },
                    // The same step the back gesture takes — see [goBack] —
                    // offered on anything pushed over a tab.
                    onBack = when {
                        route != null || detail != null || selectedMoodGenre != null -> goBack
                        else -> null
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                    // Only on the Search tab itself, not on anything pushed over it.
                    accessory = if (
                        selectedTab == TAB_SEARCH && route == null && detail == null && selectedMoodGenre == null
                    ) {
                        {
                            // The search field lives up here in the bar, beside the
                            // account photo, so it answers the nav bar's Search tap
                            // itself: focus and keyboard, the way the page used to.
                            val keyboard = LocalSoftwareKeyboardController.current
                            LaunchedEffect(searchFocusRequested) {
                                if (searchFocusRequested) {
                                    searchFieldFocus.requestFocus()
                                    keyboard?.show()
                                    searchFocusRequested = false
                                }
                            }
                            SearchField(
                                query = query,
                                onQueryChange = viewModel::onQueryChange,
                                onSubmit = viewModel::submitSearch,
                                focusRequester = searchFieldFocus,
                                placeholder = org.jetbrains.compose.resources.stringResource(
                                    if (searchSource == SearchSource.LIBRARY) {
                                        SharedRes.string.search_library_hint
                                    } else if (searchSource == SearchSource.ADDONS) {
                                        SharedRes.string.search_addons_hint
                                    } else {
                                        SharedRes.string.search_hint
                                    },
                                ),
                            )
                        }
                    } else null,
                    actions = {
                        // This is intentionally scoped to Listen together: the
                        // round-trip time is meaningful while coordinating a
                        // party, but would be noise in the rest of the app.
                        if (route == AppRoute.ListenTogether) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            ) {
                                when (val state = partyServerStatus) {
                                    is ServerConnectionState.CustomFallback -> {
                                        Icon(
                                            Icons.Rounded.CloudOff,
                                            contentDescription = stringResource(R.string.listen_together_top_bar_fallback, stringResource(R.string.listen_together_ping, state.latencyMs)),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.listen_together_ping, state.latencyMs),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.error,
                                            maxLines = 1,
                                        )
                                    }
                                    is ServerConnectionState.CustomOnline -> {
                                        Text(
                                            text = stringResource(R.string.listen_together_ping, state.latencyMs),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                        )
                                    }
                                    is ServerConnectionState.DefaultOnline -> {
                                        Text(
                                            text = stringResource(R.string.listen_together_ping, state.latencyMs),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                        )
                                    }
                                    ServerConnectionState.Checking -> {
                                        Text(
                                            text = stringResource(R.string.listen_together_server_checking),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                    ServerConnectionState.Offline -> {
                                        Text(
                                            text = stringResource(R.string.listen_together_server_offline_dash),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.error,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                        // Only worth surfacing where there's room for it and it won't
                        // be mistaken for a per-page action — Home, at rest.
                        if (route == null && detail == null && selectedTab == TAB_HOME) {
                            updateNotice?.let { update ->
                                IconButton(onClick = { showUpdateDialog = true }) {
                                    Icon(
                                        // An arrow rising out of a bar, not the
                                        // little phone-with-an-arrow: at 24dp the
                                        // handset outline is mush, and the glyph
                                        // has to read as "newer version" rather
                                        // than as "something about your device".
                                        Icons.Rounded.Upgrade,
                                        contentDescription = stringResource(R.string.update_available, update.version),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        // None on Settings or the pages opened from it: those are
                        // about the account, and the photo would only reopen them.
                        if (!(route != null && AppRoute.Settings in navigator)) {
                            // Left of the account photo, and only on Library itself:
                            // a history is a record of what was played, which reads
                            // as that tab's business rather than every tab's.
                            if (route == null && detail == null && selectedTab == TAB_LIBRARY) {
                                IconButton(
                                    onClick = {
                                        navigator.push(AppRoute.History)
                                        viewModel.loadHistory()
                                    },
                                ) {
                                    Icon(
                                        Icons.Rounded.History,
                                        contentDescription = stringResource(R.string.listening_history),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            // Left of the account photo, and only on a Library
                            // "Show all" grid — the same control the Downloads
                            // folder offers (see `LocalSearchField`), adapted to
                            // the one thing a playlist or album card carries: a
                            // title.
                            if (route is AppRoute.LibraryShowAll) {
                                Box {
                                    IconButton(onClick = { librarySortMenuOpen = true }) {
                                        Icon(
                                            Icons.Rounded.Sort,
                                            contentDescription = stringResource(R.string.sort_library),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = librarySortMenuOpen,
                                        onDismissRequest = { librarySortMenuOpen = false },
                                    ) {
                                        LibrarySort.entries.forEach { option ->
                                            DropdownMenuItem(
                                                text = { Text(option.localizedLabel()) },
                                                trailingIcon = if (option == librarySort) {
                                                    { Icon(Icons.Rounded.Check, contentDescription = null) }
                                                } else null,
                                                onClick = {
                                                    AppSettings.setLibrarySort(option)
                                                    librarySortMenuOpen = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            // Sort, only on an album or playlist page or a device
                            // folder — an artist page has no single track list to
                            // reorder. A folder's opens its own menu: its sort
                            // orders, and list or grid for its tabs.
                            //
                            // Search and the overflow flank it, as the share and
                            // "…" pair do on Apple Music's album page: the header
                            // below is left with just the sleeve, the title and
                            // Shuffle / Play / Add.
                            if ((isReleaseDetail || isLocalDetail) && detail != null) {
                                val releaseSongs = (detail.songs as? UiState.Success)?.data.orEmpty()
                                if (releaseSongs.isNotEmpty()) {
                                    IconButton(onClick = { detailSearching = !detailSearching }) {
                                        Icon(
                                            if (detailSearching) Icons.Rounded.Close else BitChordIcons.Search,
                                            contentDescription = stringResource(
                                                if (detailSearching) R.string.close_search else R.string.search_this_list,
                                            ),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                                // The menu itself is [FrostedSortMenu], composed
                                // with the app's other frosted overlays further
                                // down — in the main hierarchy, where the haze
                                // can see the content it blurs.
                                IconButton(
                                    onClick = { if (isLocalDetail) folderMenuOpen = true else songSortMenuOpen = true },
                                ) {
                                    Icon(
                                        Icons.Rounded.Sort,
                                        contentDescription = stringResource(
                                            if (isLocalDetail) R.string.sort_music else R.string.sort_songs,
                                        ),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                if (isReleaseDetail && releaseSongs.isNotEmpty()) {
                                    // The page's own tracks, in the order the page
                                    // shows them, so the sheet has them already and
                                    // Play, Shuffle and Open are the buttons beside
                                    // the one that opened it rather than rows on it.
                                    // Download lives in the sheet too — and the
                                    // tracks arrive stamped with the album they came
                                    // off, which is what the download record groups
                                    // them under.
                                    IconButton(
                                        modifier = Modifier.menuAnchor(),
                                        onClick = {
                                            openBrowseMenu(BrowseTarget(
                                                browseId = detail.browseId,
                                                title = detail.title,
                                                subtitle = detail.subtitle,
                                                thumbnailUrl = detail.thumbnailUrl,
                                                type = detail.type,
                                                songs = releaseSongs.sortedForDetail(songSort).map { song ->
                                                    if (detail.type == BrowseType.ALBUM) {
                                                        song.copy(albumName = song.albumName ?: detail.title)
                                                    } else {
                                                        song
                                                    }
                                                },
                                                fromCard = false,
                                                downloadId = downloadIdFor(detail.browseId),
                                            ))
                                        },
                                    ) {
                                        Icon(
                                            Icons.Rounded.MoreHoriz,
                                            contentDescription = stringResource(R.string.more),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                            // Left of the account photo, on an artist page: iOS's
                            // share glyph, sending the artist's channel link — the
                            // one YouTube Music itself shares for an artist.
                            if (detail != null && !isLocalDetail && detail.type == BrowseType.ARTIST &&
                                detailActiveShelf == null && detail.browseId.startsWith("UC")
                            ) {
                                IconButton(
                                    onClick = {
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "https://music.youtube.com/channel/${detail.browseId}",
                                            )
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, detail.title))
                                    },
                                ) {
                                    Icon(
                                        Icons.Rounded.IosShare,
                                        contentDescription = stringResource(R.string.share),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            // Left of the account photo, and only there while
                            // there is a batch to report on — see
                            // [TopBarDownloadButton], which decides that for
                            // itself rather than being told. Not on the page it
                            // opens, where it would only lead back to itself.
                            if (route != AppRoute.Downloads) {
                                TopBarDownloadButton(onClick = { navigator.push(AppRoute.Downloads) })
                            }
                            // Not on a release page, whose pill already carries
                            // three of its own controls — Apple Music leaves the
                            // profile off its album pages too.
                            if (!isReleaseDetail) TopBarAccountButton(
                                account = account,
                                onClick = {
                                    if (signedIn) {
                                        viewModel.loadChannels()
                                        showAccountSelector = true
                                    } else navigator.push(AppRoute.Settings)
                                },
                                onSwipeProfile = { forward -> viewModel.stepProfile(forward) },
                            )
                        }
                    },
                )

                // Drawn before the bars so their own glass reads on top of it.
                BottomFadeScrim(
                    withMiniPlayer = player.song != null,
                    // Not the wash: by the foot of the screen the page has finished
                    // easing out of it and into this, so this is what is actually
                    // under the tab bar.
                    pageColor = chromePageColor,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )

                // Whichever bar is drawing reports its height here, for the
                // guard to read at layout — never in composition, see the guard.
                val floatingBarsHeight = remember { mutableIntStateOf(0) }
                FloatingBarsTapGuard(
                    barsHeight = { floatingBarsHeight.intValue },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )

                // One tab handler, whichever bar is drawing it.
                // A tab is a fresh start: everything pushed over the old one —
                // routes and release pages alike — goes with it.
                val onTabSelected: (Int) -> Unit = { index ->
                    navigator.clear()
                    viewModel.closeMoodGenre()
                    selectedTab = index

                    // Every search tab tap resets the field, focuses it, and opens
                    // the keyboard through SearchScreen's focus request.
                    if (index == TAB_SEARCH) {
                        viewModel.onQueryChange("")
                        searchFocusRequested = true
                    }
                }

                if (glassActive) Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = FLOATING_BAR_MAX_WIDTH)
                        .fillMaxWidth()
                        .onSizeChanged { floatingBarsHeight.intValue = it.height },
                ) {
                    QueueActionNoticeHost(queueNotice)
                    // Liquid glass replaces the two stacked bars with the single
                    // component they are stacked to imitate: the now playing
                    // controls dock into the tab bar rather than riding above it,
                    // and the pair folds together on scroll. See [GlassNavBar].
                    GlassNavBar(
                        tabs = tabs,
                        selectedIndex = selectedTab,
                        onTabSelected = onTabSelected,
                        scrollConnection = navBarScroll,
                        song = player.song,
                        isPlaying = player.isPlaying,
                        isLoading = playPauseBusy,
                        onPlayPause = {
                            togglePlayPause()
                        },
                        onNext = { controller?.seekToNextMediaItem() },
                        onPrevious = { controller?.seekToPrevious() },
                        onExpand = if (reducePlayerMotion) {
                            { showNowPlaying = true }
                        } else {
                            playerSheetMotion::open
                        },
                        controlsLocked = controlsLocked,
                        onBlockedControl = showHostOnlyNotice,
                        dock = activeDock,
                        pull = playerSheetMotion.takeIf { !reducePlayerMotion },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // Capped and centred rather than run to the page's edges
                        // — see [FLOATING_BAR_MAX_WIDTH]. It sits on the Column
                        // rather than on each bar so the two are held to the same
                        // width and keep the shared left and right edge they have
                        // on a phone. Before fillMaxWidth, so the fill has
                        // already been bounded by the time it is applied.
                        .widthIn(max = FLOATING_BAR_MAX_WIDTH)
                        .fillMaxWidth()
                        .onSizeChanged { floatingBarsHeight.intValue = it.height },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    QueueActionNoticeHost(queueNotice)
                    player.song?.let { song ->
                        MiniPlayer(
                            song = song,
                            isPlaying = player.isPlaying,
                            isLoading = playPauseBusy,
                            hazeState = hazeState,
                            onPlayPause = {
                                togglePlayPause()
                            },
                            onNext = { controller?.seekToNextMediaItem() },
                            onPrevious = { controller?.seekToPrevious() },
                            onExpand = if (reducePlayerMotion) {
                                { showNowPlaying = true }
                            } else {
                                playerSheetMotion::open
                            },
                            controlsLocked = controlsLocked,
                            onBlockedControl = showHostOnlyNotice,
                            dock = activeDock,
                            pull = playerSheetMotion.takeIf { !reducePlayerMotion },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    FloatingBottomBar(
                        tabs = tabs,
                        selectedIndex = selectedTab,
                        hazeState = hazeState,
                        onTabSelected = onTabSelected,
                    )
                }
            }

        }

        val playerRaised = showNowPlaying && playerSong != null

        // ---- The page's still, for a menu hung over it ----
        // Taken as a ⋮ or ⋯ menu opens over a page, let go once it has played
        // its way out — see [pageBackdrop]. Read in a scope of its own, as
        // the menus are below, so a menu opening recomposes only this.
        Isolated {
            val pageMenuUp =
                (songActions != null && songMenuOrigin == null && !(menuFromPlayer && playerRaised)) ||
                    (browseActions != null && browseMenuOrigin == null && !browseRenameInSheet)
            LaunchedEffect(pageMenuUp, reduceDynamicBlur) {
                if (pageMenuUp && !reduceDynamicBlur && isGlassSupported()) {
                    pageBackdrop.capture()
                } else {
                    delay(SNAPSHOT_RELEASE_DELAY_MS)
                    pageBackdrop.release()
                }
            }
        }

        // ---- Long-press track actions ----
        // One body for every frame the track menu comes in: the panel hung off
        // a ⋮ or the player's ⋯, and the one a held row lifts into. Every
        // callback below is the same in each; only the frame differs.
        val songMenuBody: @Composable (Song) -> Unit = { heldSong ->
            // Set by whoever opened it — see [menuFromPlayer]. It cannot be
            // read off the player's own visibility any more, because on a
            // tablet the player is visible whatever the menu was opened from.
            val fromPlayer = menuFromPlayer
            // A row from search results, recents or a feed rarely knows which
            // album or artist page it belongs to, which left the menu with no
            // way to get there. Ask as the menu opens, the way the player
            // backfills its own track — the player's copy is already kept in
            // sync above, so it is left alone here.
            val needsLinks = !fromPlayer && heldSong.localUri == null &&
                heldSong.videoId.isNotBlank() &&
                (heldSong.albumId == null || heldSong.artistId == null)
            var menuLinks by remember(heldSong.videoId) { mutableStateOf<Song?>(null) }
            var menuLinksLoading by remember(heldSong.videoId) { mutableStateOf(needsLinks) }
            LaunchedEffect(heldSong.videoId, needsLinks) {
                if (!needsLinks) return@LaunchedEffect
                menuLinksLoading = true
                menuLinks = YtMusicRepository.trackLinks(heldSong.videoId).getOrNull()
                menuLinksLoading = false
            }
            val song = menuLinks?.takeIf { needsLinks && it.videoId == heldSong.videoId }?.let { extra ->
                heldSong.copy(
                    artistId = heldSong.artistId ?: extra.artistId,
                    albumId = heldSong.albumId ?: extra.albumId,
                    albumName = heldSong.albumName ?: extra.albumName,
                )
            } ?: heldSong
            val share: () -> Unit = {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${song.videoId}")
                }
                context.startActivity(Intent.createChooser(sendIntent, song.title))
                songActions = null
            }
            // Navigating has to take the player down with the sheet, or the
            // page it opens lands behind a still-covering player.
            // The track's cover stands in for an album's, but never for an
            // artist's picture — that page loads its own.
            val openPage: (String, String, String, BrowseType) -> Unit = { id, title, sub, type ->
                songActions = null
                dismissPlayer()
                val art = song.thumbnailUrl.takeUnless { type == BrowseType.ARTIST }
                viewModel.openDetail(id, title, sub, art, type)
            }
            // Video vs audio, moved here from the player's own controls: it is
            // the same kind of choice as Revert to original / Upgrade
            // quality just above it — which recording is playing — so it now
            // sits in the same list rather than as a control of its own.
            //
            // Mirrors what the pill used to compute, keyed to this sheet's
            // own [song] rather than a `nowPlaying` lambda parameter: an
            // in-flight optimistic swap is still read off [optimisticVersionSong]
            // so a menu opened mid-switch describes the version actually
            // becoming current, not the one about to be left.
            val versionEffectiveSong = optimisticVersionSong?.takeIf {
                it.videoId == convertedAudioId || it.videoId == convertedVideoId || it.videoId == keepVideoId ||
                    it.videoId == YtMusicRepository.cachedAudioVersion(song.videoId)?.videoId ||
                    it.videoId == YtMusicRepository.cachedVideoVersion(song.videoId)?.videoId
            } ?: song
            fun versionAlignmentPending(targetId: String): Boolean =
                AppSettings.smartVersionAlignment.value &&
                    VersionAudioAligner.getCachedOffsetMs(song.videoId, targetId) == null &&
                    YtMusicRepository.versionTimeMap(song.videoId, targetId) == null
            val menuIsAudioVersion = if (optimisticVersionSong != null) {
                !optimisticVersionSong!!.isVideo
            } else {
                !song.isVideo && convertedVideoId != song.videoId
            }
            // Offered for the playing track wherever its menu is opened,
            // whether or not the other version has been found yet: the
            // switch looks it up, and says so when there isn't one.
            val onToggleVersion: (() -> Unit)? = if (
                controller?.currentMediaItem?.mediaId == song.videoId
            ) {
                toggle@{
                    songActions = null
                    // A party plays one cut for everyone in it — see
                    // [PlaybackService.smoothSwapCurrentTrackVersion].
                    if (partyState.inParty) {
                        showQueueNotice(versionContext.getString(R.string.version_switch_in_party))
                        return@toggle
                    }
                    if (switchingAudioVersion) return@toggle
                    val c = controller
                    val original = convertedFromVideo
                    val originalAudio = convertedFromAudio
                    when {
                        c == null -> Unit
                        original != null && (
                            convertedAudioId == song.videoId ||
                                convertedAudioId == versionEffectiveSong.videoId ||
                                convertedAudioId == optimisticVersionSong?.videoId
                            ) -> {
                            keepVideoId = original.videoId
                            convertedFromVideo = null
                            convertedAudioId = null
                            if (!versionAlignmentPending(original.videoId)) optimisticVersionSong = original
                            c.swapToVersion(original)
                        }
                        originalAudio != null && (
                            convertedVideoId == song.videoId ||
                                convertedVideoId == versionEffectiveSong.videoId ||
                                convertedVideoId == optimisticVersionSong?.videoId
                            ) -> {
                            convertedFromAudio = null
                            convertedVideoId = null
                            if (!versionAlignmentPending(originalAudio.videoId)) optimisticVersionSong = originalAudio
                            c.swapToVersion(originalAudio)
                        }
                        versionEffectiveSong.isVideo || song.isVideo -> {
                            val cached = YtMusicRepository.cachedAudioVersion(song.videoId)
                                ?: YtMusicRepository.cachedAudioVersion(versionEffectiveSong.videoId)
                            if (cached != null && cached.videoId != song.videoId &&
                                !versionAlignmentPending(cached.videoId)
                            ) {
                                optimisticVersionSong = cached.copy(
                                    isVideoOrigin = true,
                                    queueTier = song.queueTier,
                                    queueEntryId = song.queueEntryId,
                                    radioName = song.radioName,
                                    playbackSource = song.playbackSource,
                                    playbackSourceType = song.playbackSourceType,
                                    playbackSourceId = song.playbackSourceId,
                                )
                            }
                            scope.launch { switchToMusicOnly(song, pauseWhileResolving = false) }
                        }
                        else -> {
                            val cached = YtMusicRepository.cachedVideoVersion(song.videoId)
                                ?: YtMusicRepository.cachedVideoVersion(versionEffectiveSong.videoId)
                            if (cached != null && cached.videoId != song.videoId &&
                                !versionAlignmentPending(cached.videoId)
                            ) {
                                optimisticVersionSong = cached.copy(
                                    queueTier = song.queueTier,
                                    queueEntryId = song.queueEntryId,
                                    radioName = song.radioName,
                                    playbackSource = song.playbackSource,
                                    playbackSourceType = song.playbackSourceType,
                                    playbackSourceId = song.playbackSourceId,
                                )
                            }
                            scope.launch { switchToVideo(song, pauseWhileResolving = false) }
                        }
                    }
                }
            } else {
                null
            }
            // The library toggle needs tokens only YouTube can mint, and the
            // rating it comes back with is more authoritative than anything
            // the library feed knew — so the menu asks as it opens.
            LaunchedEffect(song.videoId) { viewModel.loadSongMenu(song.videoId) }
            // "Remove from this playlist" is only a sentence on a playlist
            // page the account can actually edit, and only for a row that
            // carries the per-entry id a removal is expressed in.
            val editable = viewModel.editablePlaylist(detail?.browseId)
                ?.takeIf { !fromPlayer && song.setVideoId != null }
            // Asked of the cache's index off the main thread as the menu opens,
            // so "Remove from cache" is only offered for a track it holds.
            // Re-asked when the cache changes, which is what drops the row
            // once the track has gone.
            val cacheVersion by AudioCache.contentsChanged.collectAsStateWithLifecycle()
            val inCache by produceState(false, song.videoId, cacheVersion) {
                value = song.localUri == null &&
                    withContext(Dispatchers.IO) { AudioCache.holds(song.videoId) }
            }
            SongActionsMenu(
                song = song,
                signedIn = signedIn,
                likeStatus = likeStatusOf(song),
                onPlayNext = { playNext(song); songActions = null },
                onAddToQueue = { addToQueue(song); songActions = null },
                onStartRadio = { startRadio(song); songActions = null },
                // Stays open: the row it replaces itself with is the
                // progress, and closing the sheet would hide the only
                // answer to "did that work?".
                // From the Cached songs folder, the cache's own copy is the
                // download — the same as the row's download button there.
                onDownload = {
                    if (!fromPlayer && detail?.browseId == CACHE_FOLDER_BROWSE_ID) {
                        downloadFromCache(listOf(song))
                    } else {
                        downloadSong(song)
                    }
                },
                // The other direction: a device file going up to the
                // server. Closed first, unlike a download — progress and
                // the summary notice live outside the sheet.
                onUploadToWebDav =
                    if (com.music.bitchord.data.webdav.WebDavConfig.isConfigured(webdavUrl) &&
                        com.music.bitchord.data.webdav.WebDavUploads.isUploadable(song)
                    ) {
                        {
                            songActions = null
                            uploadToWebDav(listOf(song))
                        }
                    } else {
                        null
                    },
                // The sheet stays up for a rating: it shows the new state
                // in place, and people often thumb a song and then queue it.
                onToggleLike = { toggleLikeOf(song) },
                onToggleDislike = {
                    val previousStatus = viewModel.toggleDislike(song.videoId)
                    if (
                        previousStatus != null &&
                        shouldSkipAfterDislike(
                            previousStatus = previousStatus,
                            targetVideoId = song.videoId,
                            currentVideoId = player.song?.videoId,
                        )
                    ) {
                        controller?.seekToNextMediaItem()
                    }
                },
                onAddToPlaylist = {
                    songActions = null
                    if (song.localUri != null && (song.videoId.startsWith("content://") || song.videoId.startsWith("file://"))) {
                        offlinePlaylistTarget = song
                    } else {
                        viewModel.loadPlaylists()
                        playlistTarget = song
                    }
                },
                onRemoveFromPlaylist = editable?.let {
                    {
                        songActions = null
                        viewModel.removeFromPlaylist(it.browseId, song)
                    }
                } ?: detail?.browseId
                    ?.takeIf { !fromPlayer && song.localUri != null && OfflinePlaylists.isOfflinePlaylist(it) }
                    ?.let { id ->
                        {
                            songActions = null
                            OfflinePlaylists.remove(id, song)
                        }
                    },
                onRemoveFromCache = if (inCache) {
                    {
                        songActions = null
                        removeFromCache(listOf(song))
                    }
                } else {
                    null
                },
                onRemoveFromHistory = song.feedbackToken?.takeIf { menuFromHistory }?.let { token ->
                    {
                        songActions = null
                        viewModel.removeFromHistory(token, onFailure = {
                            Toast.makeText(context, R.string.remove_from_history_failed, Toast.LENGTH_SHORT).show()
                        })
                    }
                },
                onOpenAlbum = { id ->
                    openPage(
                        id,
                        song.albumName ?: song.title,
                        song.artist,
                        BrowseType.ALBUM,
                    )
                },
                onOpenArtist = { id ->
                    openPage(id, song.artist, context.getString(R.string.artist), BrowseType.ARTIST)
                },
                // The player backfills its own track; any other row is
                // looked up as the menu opens — see `needsLinks` above.
                resolvingLinks = if (fromPlayer) linksLoading else needsLinks && menuLinksLoading,
                showSleepTimer = fromPlayer,
                // Offered for every playing track with a YouTube upload
                // behind it, not only for one an upgrade visibly swapped:
                // a source ranked above YouTube can be playing its own
                // idea of the song from the first second, and a wrong
                // match sounds like a wrong match whether or not anything
                // announced itself. See [Song.hasYouTubeOriginal].
                onRollbackToOriginal = if (fromPlayer &&
                    song.hasYouTubeOriginal() &&
                    // Nothing to revert *from*: the listener is hearing a
                    // file they saved, not a stream anything chose.
                    song.localUri == null &&
                    // Already there, and the menu says so with the row
                    // below instead.
                    song.videoId !in pinnedToOriginal &&
                    // And the same for a track that got back here without
                    // the listener asking: an upgrade that failed to prove
                    // itself is reverted automatically and pins nothing, so
                    // this row was being offered for a track already on
                    // YouTube's own stream, where it does nothing.
                    !playingYouTubesOwn(song.videoId, controller) &&
                    controller?.currentMediaItem?.mediaId == song.videoId
                ) {
                    {
                        controller?.revertToOriginal()
                        songActions = null
                    }
                } else {
                    null
                },
                // The way back, and for a pinned track the only one: it is
                // held off the automatic search on purpose, so nothing but
                // this will ever offer it a better copy again. Also shown
                // for a track whose upgrade failed and was reverted, which
                // is likewise sitting on YouTube's own stream with nothing
                // due to look at it again — [QualityUpgrade.refuseUpgrades]
                // takes a broken track off the automatic path for the rest
                // of the session, and `askByHand` is what clears that.
                onUpgradeQuality = if (fromPlayer &&
                    (
                        song.videoId in pinnedToOriginal ||
                            playingYouTubesOwn(song.videoId, controller)
                        ) &&
                    // A track playing off a file the listener saved is not
                    // playing a stream anything could upgrade — the pin on
                    // it is only waiting for the day it is streamed again.
                    song.localUri == null &&
                    controller?.currentMediaItem?.mediaId == song.videoId
                ) {
                    {
                        controller.upgradeQuality()
                        songActions = null
                    }
                } else {
                    null
                },
                upgradeQualityInProgress = fromPlayer && song.videoId in qualityUpgradesInFlight,
                onToggleAudioVersion = onToggleVersion,
                isAudioVersion = menuIsAudioVersion,
                audioVersionMissing = song.videoId in missingVersions,
                // Hidden outright when there's no real YouTube id behind
                // this row to build a link from — SongActionsMenu already
                // drops it for a local file via `isOffline`, this catches
                // the rest.
                onShare = share.takeIf { song.videoId.isNotBlank() },
                onCopyLog = if (fromPlayer) {
                    {
                        songActions = null
                        scope.launch {
                            val text = TrackLog.forTrack(song, NerdStats.current.value)
                            clipboard.setText(AnnotatedString(text))
                            // The line count, not just "copied": it is the
                            // one thing the system's own paste confirmation
                            // doesn't say, and an empty log is a real
                            // outcome worth seeing rather than a silent one.
                            Toast.makeText(
                                context,
                                context.resources.getQuantityString(
                                    R.plurals.log_copied_line_count,
                                    text.lineSequence().count(),
                                    text.lineSequence().count(),
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                } else {
                    null
                },
                onLyricsOffset = if (fromPlayer) {
                    {
                        songActions = null
                        showLyricsOffset = true
                    }
                } else {
                    null
                },
            )
        }
        // ---- Now Playing ----
        if (playerRaised) {
            // What rememberModalBottomSheetState builds, but able to start open,
            // and with every hide routed through [playerSheetMotion] — a drag
            // released low, a back press, a tap on the scrim. Refused here and
            // carried out there, on a curve slow enough to watch the artwork
            // fly home on, rather than on the sheet's own.
            val sheetDensity = LocalDensity.current
            val confirmSheetValue: (SheetValue) -> Boolean = { value ->
                // Read when asked, not when the sheet was made: the setting
                // can change while the player is up.
                if (value == SheetValue.Hidden && !reducePlayerMotion) {
                    playerSheetMotion.close()
                    false
                } else {
                    true
                }
            }
            val nowPlayingSheetState = rememberSaveable(
                saver = SheetState.Saver(
                    skipPartiallyExpanded = true,
                    confirmValueChange = confirmSheetValue,
                    density = sheetDensity,
                    skipHiddenState = false,
                ),
            ) {
                SheetState(
                    skipPartiallyExpanded = true,
                    density = sheetDensity,
                    // Already open when the app is the one raising it: its
                    // content is held down, and slid up, by the motion.
                    initialValue = if (playerSheetMotion.holding) SheetValue.Expanded else SheetValue.Hidden,
                    confirmValueChange = confirmSheetValue,
                )
            }
            DisposableEffect(nowPlayingSheetState) {
                playerSheetMotion.sheet = nowPlayingSheetState
                playerDock.sheetOffset = playerSheetMotion::position
                onDispose {
                    playerDock.sheetOffset = null
                    playerSheetMotion.onSheetGone()
                }
            }
            ModalBottomSheet(
                onDismissRequest = { showNowPlaying = false },
                sheetState = nowPlayingSheetState,
                // None of M3's: it follows the sheet's own animation, which is
                // no longer what moves the player. The player draws its own,
                // off where the player actually is — see the content below.
                scrimColor = if (reducePlayerMotion) BottomSheetDefaults.ScrimColor else Color.Transparent,
                // The player fills the screen and paints its own background to
                // the very top, so the sheet's default 28.dp top corners would
                // only cut two notches out of the artwork behind the status bar.
                // Square, and not clipped along its top — see [PlayerSheetShape].
                shape = PlayerSheetShape,
                containerColor = Color.Transparent,
                dragHandle = null,
                contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
                // M3 caps a bottom sheet at [BottomSheetDefaults.SheetMaxWidth]
                // (640.dp) and centres it once the window is wider than that —
                // built for a sheet that's meant to look like a sheet next to
                // visible content either side. This one is the whole player;
                // capped at 640dp on a tablet it renders as a narrow card with
                // the page it's supposed to be covering visible down both
                // sides. Unspecified opts out of the cap entirely, so the
                // sheet always spans the full window this app draws it for.
                sheetMaxWidth = Dp.Unspecified,
                // Back is the app's to carry out, like every other close — see
                // the handler below. Left to M3, a back press is the one close
                // that never asks `confirmValueChange`: the sheet hides itself
                // straight away on its own curve, then the window leaves on its
                // exit animation with the artwork still drawn in it.
                properties = ModalBottomSheetProperties(shouldDismissOnBackPress = reducePlayerMotion),
            ) {
                // With M3's own back off, the dialog's dispatcher ends here.
                // Composed ahead of the player, so the player's own handlers —
                // the lyrics and the queue putting themselves away first — are
                // newer and are asked before this one.
                BackHandler(enabled = !reducePlayerMotion) { playerSheetMotion.close() }
                // Keeps a sheet still "settling" after a lyrics or queue
                // scroll from taking the next touch meant for that list.
                // See [PlayerSheetMotion.attachWindow].
                val sheetWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
                // The player's window holds focus while it is up, so the volume
                // keys reach it rather than the activity.
                DacVolumeKeys()
                DisposableEffect(sheetWindow, reducePlayerMotion) {
                    if (!reducePlayerMotion) playerSheetMotion.attachWindow(sheetWindow)
                    onDispose { }
                }
                Box(
                    Modifier
                        // Where the app is holding the player, if it is — see
                        // [PlayerSheetMotion.contentOffsetPx]. Placement only.
                        .offset { IntOffset(0, playerSheetMotion.contentOffsetPx()) }
                        // The dim behind the player, from the window's top to
                        // its bottom whatever the player's offset, and as deep
                        // as the player is open.
                        .drawBehind {
                            val open = playerDock.openFraction()
                            // Not at all under a portrait player that is fully up:
                            // its own background is opaque, and a full-screen
                            // blend behind it is paid on every frame the window
                            // draws for nothing anyone can see.
                            if (!reducePlayerMotion && open > 0f && !(open >= 1f && playerDock.attached)) {
                                val top = playerSheetMotion.position().takeUnless { it.isNaN() } ?: 0f
                                drawRect(
                                    color = Color.Black.copy(alpha = PLAYER_SCRIM_ALPHA * open),
                                    topLeft = Offset(0f, -top.toInt().toFloat()),
                                    size = Size(size.width, size.height),
                                )
                            }
                        }
                        .guardSheetFromContentTouches(nowPlayingSheetState)
                        // The sheet fills the window, so its height is the
                        // whole of its travel: hidden sits that far down.
                        .onSizeChanged {
                            playerDock.sheetTravel = it.height.toFloat()
                            playerSheetMotion.laidOut = true
                        },
                ) {
                    // The still the ⋯ menu below blurs under its panel — taken
                    // by the tap that opens it (see onOpenMenu), not the player
                    // live, which re-rendered all of it under the panel on every
                    // frame anything in it moved (see [SnapshotBackdrop]). Let
                    // go once the menu has played its way out. Drawn in this
                    // sheet's own window, as the menu has to be to sit above
                    // the player. Not under "reduce dynamic blur": the panel
                    // is opaque then.
                    val playerMenuUp = songActions != null && songMenuOrigin == null && menuFromPlayer
                    LaunchedEffect(playerMenuUp, reduceDynamicBlur) {
                        if (!playerMenuUp || reduceDynamicBlur) {
                            delay(SNAPSHOT_RELEASE_DELAY_MS)
                            playerBackdrop.release()
                        }
                    }
                    Box(
                        when {
                            reduceDynamicBlur -> Modifier
                            playerLiveCanvas -> Modifier.layerBackdrop(livePlayerBackdrop)
                            else -> Modifier.snapshotBackdropSource(playerBackdrop)
                        },
                    ) {
                        CompositionLocalProvider(LocalPlayerDock provides activeDock) {
                            nowPlaying(playerSong)
                        }
                    }
                    IosMenuPopover(
                        item = songActions?.takeIf { songMenuOrigin == null && menuFromPlayer },
                        anchor = songMenuAnchor,
                        onDismiss = { songActions = null },
                        backdrop = if (playerLiveCanvas) livePlayerBackdrop else playerBackdrop,
                    ) { song ->
                        songMenuBody(song)
                    }
                }
            }
        }

        // ---- Replay stories ----
        // Mounted out here rather than inside the pane above, because a story
        // covers the window: on a tablet the page is only the left half of the
        // row, and a story laid out inside it would run alongside the player
        // instead of over it.
        replayStory?.let { start ->
            replay.summary?.takeUnless { it.isEmpty }?.let { summary ->
                ReplayStories(
                    summary = summary,
                    start = start,
                    onClose = { replayStory = null },
                    onShare = { card ->
                        replaySharePage = card
                        showReplayShare = true
                    },
                    paused = showReplayShare,
                )
            }
        }

        // ---- Share the Replay ----
        if (showReplayShare) {
            replay.summary?.takeUnless { it.isEmpty }?.let { summary ->
                ModalBottomSheet(
                    onDismissRequest = { showReplayShare = false },
                    // Straight to full height. The sheet is a picture and two
                    // buttons, and half-open it showed the picture with both
                    // buttons below the fold — a sheet whose only two controls
                    // need a drag to reach.
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = MaterialTheme.colorScheme.background,
                ) {
                    ReplayShareSheet(
                        summary = summary,
                        holder = account?.name.orEmpty(),
                        memberSince = replay.memberSince,
                        page = replaySharePage,
                        onDismiss = { showReplayShare = false },
                    )
                }
            }
        }

        // ---- Track menu ----
        // In a recompose scope of its own: whether a menu is up is read in
        // here, and read in the app's own body it recomposed all of the app
        // on the frame the menu starts to grow — the stutter at the start of
        // its spring, blur or no blur.
        Isolated {
            // Drawn over everything, tab bar and mini player included, and kept
            // composed through its own exit — so it is not gated on songActions.
            HeldContextMenu(
                item = songActions,
                held = songMenuOrigin,
                hazeState = hazeState,
                onDismiss = { songActions = null },
                // Tapping the lifted track opens its album, as Apple Music's does.
                onPreviewClick = { song ->
                    song.albumId?.let { id ->
                        songActions = null
                        viewModel.openDetail(id, song.albumName ?: song.title, song.artist, song.thumbnailUrl, BrowseType.ALBUM)
                    }
                },
            ) { song ->
                songMenuBody(song)
            }
            // A tap on a ⋮ — or on the player's ⋯ while the player is part of the
            // page, as on a tablet — hangs the same menu off that button. While the
            // player is a sheet the sheet draws its own (above), so it can sit
            // over the player and blur it.
            // A player menu goes with the player: left up, it would carry on here,
            // over the page.
            LaunchedEffect(playerRaised) {
                if (!playerRaised && menuFromPlayer) songActions = null
            }
            IosMenuPopover(
                item = songActions?.takeIf { songMenuOrigin == null && !(menuFromPlayer && playerRaised) },
                anchor = songMenuAnchor,
                onDismiss = { songActions = null },
                backdrop = pageBackdrop,
                hazeState = hazeState,
            ) { song ->
                songMenuBody(song)
            }
            // A closed menu forgets where it was held and what it hung off, so
            // whatever opens it next starts from nothing.
            LaunchedEffect(songActions == null) {
                if (songActions == null) {
                    songMenuOrigin = null
                    songMenuAnchor = null
                    menuFromHistory = false
                }
            }
        }

        // ---- Add to playlist / new playlist ----
        // One sheet for both, because they are one decision: the list of
        // playlists with a way to make another. `creatingPlaylist` opens it
        // straight onto the form, which is what the Library tile means.
        if (playlistTarget != null || creatingPlaylist) {
            val target = playlistTarget
            val dismiss = {
                playlistTarget = null
                creatingPlaylist = false
            }
            PlaylistPickerSheet(
                playlists = playlists,
                loading = playlistsLoading,
                song = target,
                startCreating = target == null,
                onDismiss = dismiss,
                onAdd = { picked ->
                    target?.let { song ->
                        viewModel.addToPlaylists(picked, song) { added, alreadyThere, failed ->
                            // One line for the whole batch, saying the outcome
                            // that matters most: what went in, else that it was
                            // all there already, else that it didn't work.
                            showQueueNotice(
                                when {
                                    added > 1 -> context.resources.getQuantityString(
                                        R.plurals.added_to_playlists_notice, added, added,
                                    )
                                    added == 1 -> context.getString(R.string.song_added_to_playlist)
                                    alreadyThere > 0 && failed == 0 ->
                                        context.getString(R.string.song_already_in_playlist)
                                    else -> context.getString(R.string.failed)
                                },
                            )
                        }
                    }
                },
                onCreate = { title, privacy ->
                    viewModel.createPlaylist(title, privacy, target)
                },
            )
        }

        // ---- Add to a device playlist / new device playlist ----
        // The sheet above over the offline player's own playlists, for a track
        // that is a file on this device. Nothing here waits on a network, so
        // there is no loading state and the outcome is known on the tap.
        if (offlinePlaylistTarget != null || creatingOfflinePlaylist) {
            val target = offlinePlaylistTarget
            val dismiss = {
                offlinePlaylistTarget = null
                creatingOfflinePlaylist = false
            }
            val choices = remember(offlinePlaylists) {
                offlinePlaylists.map { playlist ->
                    UserPlaylist(
                        playlistId = playlist.id,
                        title = playlist.title,
                        subtitle = context.resources.getQuantityString(
                            R.plurals.song_count_plural,
                            playlist.songs.size,
                            playlist.songs.size,
                        ),
                        thumbnailUrl = playlist.songs.firstNotNullOfOrNull { it.thumbnailUrl }
                            .takeUnless { playlist.isLiked },
                    )
                }
            }
            PlaylistPickerSheet(
                playlists = choices,
                loading = false,
                song = target,
                startCreating = target == null,
                offline = true,
                onDismiss = dismiss,
                onAdd = { picked ->
                    target?.let { song ->
                        val result = OfflinePlaylists.add(picked.mapTo(HashSet()) { it.playlistId }, listOf(song))
                        showQueueNotice(
                            when {
                                result.added > 1 -> context.resources.getQuantityString(
                                    R.plurals.added_to_playlists_notice, result.added, result.added,
                                )
                                result.added == 1 -> context.getString(R.string.song_added_to_playlist)
                                result.alreadyThere > 0 -> context.getString(R.string.song_already_in_playlist)
                                else -> context.getString(R.string.failed)
                            },
                        )
                    }
                },
                onCreate = { title, _ ->
                    OfflinePlaylists.create(title, listOfNotNull(target))
                    if (target != null) showQueueNotice(context.getString(R.string.song_added_to_playlist))
                },
            )
        }

        if (showSpotifyImportDialog) {
            BackHandler { showSpotifyImportDialog = false }
            SpotifyImportAlert(
                hazeState = hazeState,
                signedIn = signedIn,
                onImported = { title, privacy, songs ->
                    viewModel.createPlaylistWithVideoIds(
                        title,
                        privacy,
                        songs.map { it.videoId },
                        songs,
                    ) { browseId, pTitle, savedLocally ->
                        showQueueNotice(
                            context.getString(
                                if (savedLocally && signedIn) R.string.spotify_import_local_fallback
                                else R.string.spotify_import_done,
                                pTitle,
                            ),
                        )
                        browseId?.let { id ->
                            viewModel.openDetail(id, pTitle, "${songs.size} songs", songs.firstOrNull()?.thumbnailUrl)
                        }
                    }
                },
                onDismiss = { showSpotifyImportDialog = false },
            )
        }

        // ---- Album / playlist actions ----
        // Opened by holding a card on any tab, or from the release page's own
        // overflow. What a track's long-press menu is to one song, this is to
        // the whole release — the queue rows above all.
        // One body for every frame alike — see songMenuBody.
        val browseMenuBody: @Composable (BrowseTarget) -> Unit = { target ->
            // Every row here closes the menu first: the tracks may still have to
            // be fetched, and leaving the sheet up over a request nothing on it
            // reports on reads as a tap that didn't land.
            val act: ((List<Song>) -> Unit) -> () -> Unit = { action ->
                {
                    browseActions = null
                    withBrowseSongs(target, action)
                }
            }
            // Whose playlist this is, asked here rather than carried in by
            // whatever opened the sheet.
            //
            // Only the playlist's own page states it (see
            // InnertubeParser.parsePlaylistOwned), so a card has to send for the
            // answer and the sheet is already up by the time it lands — hence
            // read as state rather than settled once when the target was built.
            // Rename and Delete are absent until the answer says they apply, so
            // the sheet's worst moment is a beat without them on the user's own
            // playlist, rather than offering to delete a stranger's.
            LaunchedEffect(target.browseId) {
                viewModel.resolvePlaylistOwnership(target.browseId)
            }
            // Spelt out here rather than left to MainViewModel.editablePlaylist,
            // which is the same rule over the same two lists: that reads them as
            // plain values, which is right for a click handler and invisible to
            // Compose. Both are read from collected state so this sheet actually
            // recomposes when the answer arrives.
            val ownedPlaylists by viewModel.playlistOwned.collectAsStateWithLifecycle()
            val playlist = target.browseId
                ?.takeIf { signedIn && ownedPlaylists[it] == true }
                ?.let { id -> playlists.firstOrNull { it.browseId == id } }
            val remote = target.browseId?.startsWith("local:") == false
            // Spotify playlists saved on this device ("local:playlist:sp_local_…")
            // carry real YouTube video ids, so they download like a remote
            // playlist — but they have no YouTube Music page, so Share stays
            // on [remote] alone. The device-folder ids ("local:downloads",
            // "local:all", …) are neither.
            val downloadable = remote ||
                target.browseId?.startsWith("local:playlist:sp_local_") == true
            val pinnedPlaylists by AppSettings.pinnedPlaylists.collectAsStateWithLifecycle()
            // Device playlists and directories have nowhere on the Library
            // page to be pinned to that would still mean anything off-device.
            val pinnableId = target.browseId?.takeIf {
                target.type == BrowseType.PLAYLIST &&
                    !it.startsWith(OfflinePlaylists.PREFIX) && !it.startsWith(LocalGroups.PREFIX)
            }
            // One of the offline player's own playlists, which are the user's
            // to rename and delete — all but Liked, which is the app's.
            val offlineEditable = OfflinePlaylists.get(target.browseId)?.takeUnless { it.isLiked }
            BrowseActionsMenu(
                // The live answer, not the one the target was built with.
                target = target.copy(
                    playlist = playlist ?: offlineEditable?.let {
                        UserPlaylist(playlistId = it.id, title = it.title, subtitle = "", thumbnailUrl = null)
                    },
                ),
                onRenameInSheet = {
                    browseRenameInSheet = true
                    browseMenuOrigin = null
                },
                startRenaming = browseRenameInSheet,
                onPlayNext = act(playSongsNext),
                onAddToQueue = act(addSongsToQueue),
                onPlay = act { songs -> play(songs, 0) }.takeIf { target.fromCard },
                onShuffle = act { songs ->
                    // As on a release page: shuffle goes on before the queue
                    // is built, so it is built shuffled rather than played
                    // out of order.
                    QueueShuffle.enableForNextQueue()
                    play(songs, songs.indices.random())
                }.takeIf { target.fromCard },
                onOpen = target.browseId
                    ?.takeIf { target.fromCard }
                    ?.let { id ->
                        {
                            browseActions = null
                            viewModel.openDetail(
                                browseId = id,
                                title = target.title,
                                subtitle = target.subtitle,
                                thumbnailUrl = target.thumbnailUrl,
                                type = target.type,
                            )
                        }
                    },
                // The one place a whole release is asked for, from a card and
                // from the release's own page alike — its header spends that
                // spot on the search now. Nothing on this device needs
                // fetching to be on it, so a local page is the exception.
                // What the target carries that the tracks don't is the
                // release's own name and cover, which is exactly what the
                // record wants.
                onDownloadAll = act { songs ->
                    startDownload(
                        songs,
                        target.browseId
                            ?.takeIf { target.type != BrowseType.ARTIST }
                            ?.let { id ->
                                DownloadTarget(
                                    id = id,
                                    title = target.title,
                                    subtitle = target.subtitle,
                                    thumbnailUrl = target.thumbnailUrl,
                                    playlist = target.type == BrowseType.PLAYLIST,
                                )
                            },
                    )
                }.takeIf { downloadable },
                // The same link a share off YouTube Music's own overflow
                // gives — built from the browse id rather than fetched,
                // since nothing about it depends on the tracks or the
                // account. Left off an artist card (Share there is a
                // channel link, not a release, and nobody asked for it)
                // and off anything with no real browse id behind it.
                onShare = target.browseId
                    ?.takeIf { remote && (target.type == BrowseType.ALBUM || target.type == BrowseType.PLAYLIST) }
                    ?.let { id ->
                        {
                            val url = if (target.type == BrowseType.PLAYLIST) {
                                "https://music.youtube.com/playlist?list=${id.removePrefix("VL")}"
                            } else {
                                "https://music.youtube.com/browse/$id"
                            }
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, url)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, target.title))
                            browseActions = null
                        }
                    },
                isPinned = pinnableId != null && pinnableId in pinnedPlaylists,
                onTogglePin = pinnableId?.let { id ->
                    {
                        val nowPinned = AppSettings.togglePinnedPlaylist(id)
                        if (!nowPinned && id !in pinnedPlaylists) {
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.pinned_playlist_limit,
                                    AppSettings.MAX_PINNED_PLAYLISTS,
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        browseActions = null
                    }
                },
                onRename = playlist?.let { p ->
                    { name: String ->
                        browseActions = null
                        viewModel.renamePlaylist(p, name)
                    }
                } ?: offlineEditable?.let { p ->
                    { name: String ->
                        browseActions = null
                        OfflinePlaylists.rename(p.browseId, name)
                        viewModel.retitleDetail(p.browseId, name.trim())
                    }
                },
                onReorder = playlist?.let { p ->
                    {
                        browseActions = null
                        // Rearranged on the playlist's own page: open it first
                        // when the menu was raised from anywhere else.
                        if (detail?.browseId != p.browseId) {
                            viewModel.openDetail(
                                browseId = p.browseId,
                                title = p.title,
                                subtitle = p.subtitle,
                                thumbnailUrl = p.thumbnailUrl,
                                type = BrowseType.PLAYLIST,
                            )
                        }
                        detailSearching = false
                        reorderTarget = p
                        reorderEntries = UiState.Loading
                        reorderSaving = false
                        viewModel.loadPlaylistEntries(p) { result ->
                            if (reorderTarget != p) return@loadPlaylistEntries
                            reorderEntries = result.fold(
                                onSuccess = { UiState.Success(it) },
                                onFailure = { UiState.Error(context.getString(R.string.failed)) },
                            )
                        }
                    }
                },
                onDelete = playlist?.let { p ->
                    {
                        browseActions = null
                        viewModel.deletePlaylist(p)
                    }
                } ?: offlineEditable?.let { p ->
                    {
                        browseActions = null
                        // Its page has nothing left to show; take it away too.
                        if (detail?.browseId == p.browseId) viewModel.closeDetail()
                        OfflinePlaylists.delete(p.browseId)
                    }
                },
                onDeleteDownload = target.downloadId?.let { id ->
                    {
                        browseActions = null
                        scope.launch { Downloads.deleteCollection(context, id) }
                    }
                },
            )
        }
        // In a recompose scope of its own, as the track menu's is.
        Isolated {
            // Rename is a text field and a keyboard, which have no place on a menu
            // floating over the page: the menu hands it on, and the form comes up
            // here on a sheet of its own.
            browseActions?.takeIf { browseRenameInSheet }?.let { target ->
                ModalBottomSheet(
                    onDismissRequest = { browseActions = null },
                    containerColor = MaterialTheme.colorScheme.background,
                ) {
                    browseMenuBody(target)
                }
            }
            HeldContextMenu(
                item = browseActions,
                held = browseMenuOrigin,
                hazeState = hazeState,
                onDismiss = { browseActions = null },
                // Tapping the lifted card opens it, as a tap on it in the list
                // would have. Not for a Local Music grouping, which has no page.
                onPreviewClick = { target ->
                    target.browseId?.let { id ->
                        browseActions = null
                        viewModel.openDetail(
                            browseId = id,
                            title = target.title,
                            subtitle = target.subtitle,
                            thumbnailUrl = target.thumbnailUrl,
                            type = target.type,
                        )
                    }
                },
            ) { target ->
                browseMenuBody(target)
            }
            // A tap on a ⋯ — the release page's own — hangs the menu off it.
            IosMenuPopover(
                item = browseActions?.takeIf { browseMenuOrigin == null && !browseRenameInSheet },
                anchor = browseMenuAnchor,
                onDismiss = { browseActions = null },
                backdrop = pageBackdrop,
                hazeState = hazeState,
            ) { target ->
                browseMenuBody(target)
            }
            LaunchedEffect(browseActions == null) {
                if (browseActions == null) {
                    browseMenuOrigin = null
                    browseMenuAnchor = null
                    browseRenameInSheet = false
                }
            }
        }

        // ---- Welcome (once; under the sign-in WebView it can open) ----
        // There from the first frame, so no way in; on the way out it drops
        // like a dismissed sheet onto the app it was standing in front of.
        AnimatedVisibility(
            visibleState = onboardingShown,
            enter = EnterTransition.None,
            exit = slideOutVertically(
                tween<IntOffset>(durationMillis = 560, easing = OnboardingDismissEasing),
            ) { it },
        ) {
            OnboardingScreen(
                signedIn = signedIn,
                initialName = remember {
                    ListenTogether.nickname().ifBlank { account?.name.orEmpty() }
                },
                onNameChosen = ListenTogether::setNickname,
                onSignIn = { webSession = WebSessionMode.SIGN_IN },
                onFinish = AppSettings::setOnboardingComplete,
            )
        }

        // ---- Google sign-in (full screen WebView) ----
        webSession?.let { mode ->
            BackHandler { webSession = null }
            // Raised by "Use this channel", read by the browser as "take the
            // session from the page as it now stands". A counter rather than a
            // flag so a second tap, after a failed first one, is still a new
            // request rather than a value that was already true.
            var captureRequest by remember(mode) { mutableIntStateOf(0) }
            var captureFailed by remember(mode) { mutableStateOf(false) }
            var pageReady by remember(mode) { mutableStateOf(false) }
            var confirmingProfile by remember(mode) { mutableStateOf(false) }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { webSession = null }) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = when (mode) {
                                    WebSessionMode.SIGN_IN -> stringResource(R.string.sign_in_youtube_music)
                                    WebSessionMode.SWITCH_CHANNEL -> stringResource(R.string.choose_profile)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            if (pageReady) {
                                Text(
                                    text = if (captureFailed) {
                                         stringResource(R.string.profile_unavailable)
                                     } else {
                                         stringResource(R.string.switch_profile_hint)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                )
                            }
                        }
                        if (pageReady) {
                            TextButton(onClick = {
                                captureFailed = false
                                confirmingProfile = true
                                captureRequest++
                            }, enabled = !confirmingProfile) {
                                Text(
                                    if (confirmingProfile) stringResource(R.string.checking)
                                    else stringResource(R.string.use_this_profile),
                                )
                            }
                        }
                    }
                    YtMusicLoginScreen(
                        mode = mode,
                        initialCookie = if (mode == WebSessionMode.SWITCH_CHANNEL) {
                            googleAccounts.firstOrNull { it.accountId == activeAccountId }?.cookie
                        } else null,
                        captureRequest = captureRequest,
                        onPageReady = { pageReady = it },
                        onCaptureUnavailable = {
                            confirmingProfile = false
                            captureFailed = true
                        },
                        onCaptured = { session ->
                            viewModel.onWebSession(session, mode) { accepted ->
                                confirmingProfile = false
                                if (accepted) {
                                    webSession = null
                                    if (mode == WebSessionMode.SIGN_IN) {
                                        navigator.clear()
                                        selectedTab = TAB_LIBRARY
                                    }
                                } else {
                                    captureFailed = true
                                }
                            }
                        },
                    )
                }
            }
        }

        // ---- Update available (once per launch; held until the welcome is done) ----
        if (showUpdateDialog && onboardingComplete) {
            updateNotice?.let { update ->
                UpdateAvailableDialog(
                    version = update.version,
                    notes = update.notes,
                    hazeState = hazeState,
                    // A download in progress keeps running behind the closed
                    // sheet — only the sheet itself goes away. The top bar's
                    // update icon reopens it onto whatever state it reached.
                    onDismiss = { showUpdateDialog = false },
                    onDownload = {
                        if (update.apkUrl != null) {
                            scope.launch { AppUpdateChecker.downloadApk(context) }
                        } else {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl)))
                            showUpdateDialog = false
                        }
                    },
                    onCancelDownload = {
                        AppUpdateChecker.cancelDownload()
                    },
                    onInstall = {
                        val ready = AppUpdateChecker.download.value as? AppUpdateChecker.DownloadState.Ready
                        ready?.let { AppUpdateChecker.installApk(context, it.file) }
                    },
                    onOpenReleasePage = {
                        AppUpdateChecker.resetDownload()
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl)))
                        showUpdateDialog = false
                    },
                )
            }
        }

        if (showLyricsSources) {
            BackHandler { showLyricsSources = false }
            LyricsSourcesDialog(
                hazeState = hazeState,
                onDismiss = { showLyricsSources = false },
            )
        }

        if (songSortMenuOpen) {
            BackHandler { songSortMenuOpen = false }
            FrostedSortMenu(
                hazeState = hazeState,
                selected = songSort,
                onSelect = { option ->
                    detail?.browseId?.let { AppSettings.setDetailSongSort(it, option) }
                    songSortMenuOpen = false
                },
                // Flipping the date direction deliberately leaves the menu up:
                // closing it here would cut the arrow's rotation animation off
                // before it played, and the open menu lets the direction flip
                // read against the list reordering behind the frost.
                onFlipDateDirection = {
                    detail?.browseId?.let { browseId ->
                        val next = when (songSort) {
                            SongSort.DATE_ADDED_DESC -> SongSort.DATE_ADDED_ASC
                            SongSort.DATE_ADDED_ASC -> SongSort.DATE_ADDED_DESC
                            else -> SongSort.DATE_ADDED_DESC
                        }
                        AppSettings.setDetailSongSort(browseId, next)
                    }
                },
                onDismiss = { songSortMenuOpen = false },
            )
        }

        val menuFolder = detail?.browseId?.takeIf { it.isDeviceFolder() }
        if (folderMenuOpen && menuFolder != null) {
            BackHandler { folderMenuOpen = false }
            val downloadsFolder = menuFolder == "local:downloads"
            val viewType = folderViewTypeOf(menuFolder)
            FrostedFolderMenu(
                hazeState = hazeState,
                sort = folderSortOf(menuFolder),
                viewType = viewType,
                onSort = { option ->
                    if (downloadsFolder) AppSettings.setDownloadedMusicSort(option)
                    else AppSettings.setLocalMusicSort(option)
                    folderMenuOpen = false
                },
                onToggleViewType = {
                    val next = if (viewType == LibraryViewType.GRID) LibraryViewType.LIST else LibraryViewType.GRID
                    if (downloadsFolder) AppSettings.setDownloadedMusicViewType(next)
                    else AppSettings.setLocalMusicViewType(next)
                    folderMenuOpen = false
                },
                onDismiss = { folderMenuOpen = false },
            )
        }

        if (showAccountSelector) {
            BackHandler { showAccountSelector = false }
            AccountProfileSelector(
                accounts = googleAccounts,
                activeAccountId = activeAccountId,
                activeProfileId = activeProfileId,
                hazeState = hazeState,
                onSelect = { selected, profile -> viewModel.selectProfile(selected.accountId, profile.profileId) },
                onAddAccount = {
                    showAccountSelector = false
                    webSession = WebSessionMode.SIGN_IN
                },
                onRemoveAccount = { selected ->
                    viewModel.removeAccount(selected.accountId)
                    showAccountSelector = false
                },
                onOpenSettings = {
                    showAccountSelector = false
                    navigator.push(AppRoute.Settings)
                },
                onDismiss = { showAccountSelector = false },
            )
        }

        if (showAppLanguage) {
            BackHandler { showAppLanguage = false }
            AppLanguageDialog(
                hazeState = hazeState,
                onDismiss = { showAppLanguage = false },
            )
        }

        if (showTranslationLanguage) {
            BackHandler { showTranslationLanguage = false }
            TranslationLanguageDialog(
                hazeState = hazeState,
                onDismiss = { showTranslationLanguage = false },
            )
        }

        if (showListenBrainzLogin) {
            var tokenInput by remember { mutableStateOf(listenBrainzToken) }
            ListenBrainzTokenAlert(
                hazeState = hazeState,
                tokenInput = tokenInput,
                onTokenInputChange = { tokenInput = it },
                onSave = {
                    AppSettings.setListenBrainzToken(tokenInput.trim())
                    showListenBrainzLogin = false
                },
                onDismiss = { showListenBrainzLogin = false },
            )
        }

        if (showLastfmLogin) {
            var usernameInput by remember { mutableStateOf("") }
            var passwordInput by remember { mutableStateOf("") }
            var lastfmError by remember { mutableStateOf<String?>(null) }
            var lastfmLoading by remember { mutableStateOf(false) }
            LastfmLoginAlert(
                hazeState = hazeState,
                usernameInput = usernameInput,
                onUsernameInputChange = { usernameInput = it },
                passwordInput = passwordInput,
                onPasswordInputChange = { passwordInput = it },
                error = lastfmError,
                loading = lastfmLoading,
                onSignIn = {
                    lastfmLoading = true
                    lastfmError = null
                    scope.launch {
                        try {
                            LastFM.initialize(
                                apiKey = AppSettings.lastfmApiKey.value,
                                secret = AppSettings.lastfmSecret.value,
                            )
                            LastFM.getMobileSession(usernameInput.trim(), passwordInput)
                                .onSuccess { auth ->
                                    AppSettings.setLastfmSessionKey(auth.session.key)
                                    AppSettings.setLastfmUsername(auth.session.name)
                                    AppSettings.setLastfmEnabled(true)
                                    showLastfmLogin = false
                                }
                                .onFailure { e ->
                                    lastfmError = e.message ?: context.getString(R.string.login_failed)
                                }
                        } catch (e: Exception) {
                            lastfmError = e.message ?: context.getString(R.string.login_failed)
                        } finally {
                            lastfmLoading = false
                        }
                    }
                },
                onDismiss = { if (!lastfmLoading) showLastfmLogin = false },
            )
        }

        if (showWebDavEditor) {
            BackHandler { showWebDavEditor = false }
            ServerEditorHost(
                hazeState = hazeState,
                title = stringResource(R.string.webdav),
                description = stringResource(R.string.webdav_description),
                fields = listOf(
                    FieldConfig(
                        initial = AppSettings.webdavUrl.value,
                        placeholder = stringResource(R.string.webdav_server_url_hint),
                        keyboardType = KeyboardType.Uri,
                    ),
                    FieldConfig(
                        initial = AppSettings.webdavUsername.value,
                        placeholder = stringResource(R.string.username),
                    ),
                    FieldConfig(
                        initial = AppSettings.webdavPassword.value,
                        placeholder = stringResource(R.string.password),
                        keyboardType = KeyboardType.Password,
                        isPassword = true,
                    ),
                ),
                canSubmit = { it[0].isNotBlank() },
                testFailedRes = R.string.webdav_test_failed,
                onTest = { (url, username, password) ->
                    com.music.bitchord.data.webdav.WebDavRepository.testConnection(
                        url.trim(),
                        username.trim(),
                        password,
                    )
                },
                onSave = { (url, username, password) ->
                    AppSettings.setWebDavUrl(url.trim())
                    AppSettings.setWebDavUsername(username.trim())
                    AppSettings.setWebDavPassword(password)
                    showWebDavEditor = false
                },
                onDismiss = { showWebDavEditor = false },
            )
        }

        if (showSmbEditor) {
            BackHandler { showSmbEditor = false }
            ServerEditorHost(
                hazeState = hazeState,
                title = stringResource(R.string.smb),
                description = stringResource(R.string.smb_description),
                fields = listOf(
                    FieldConfig(
                        initial = AppSettings.smbHost.value,
                        placeholder = stringResource(R.string.smb_server_hint),
                        keyboardType = KeyboardType.Uri,
                    ),
                    FieldConfig(
                        initial = AppSettings.smbShare.value,
                        placeholder = stringResource(R.string.smb_share_hint),
                    ),
                    FieldConfig(
                        initial = AppSettings.smbBasePath.value,
                        placeholder = stringResource(R.string.smb_folder_hint),
                    ),
                    FieldConfig(
                        initial = AppSettings.smbUsername.value,
                        placeholder = stringResource(R.string.username),
                    ),
                    FieldConfig(
                        initial = AppSettings.smbPassword.value,
                        placeholder = stringResource(R.string.password),
                        keyboardType = KeyboardType.Password,
                        isPassword = true,
                    ),
                ),
                canSubmit = { it[0].isNotBlank() && it[1].isNotBlank() },
                testFailedRes = R.string.smb_test_failed,
                onTest = { (host, share, folder, username, password) ->
                    com.music.bitchord.data.smb.SmbRepository.testConnection(
                        host.trim(),
                        share.trim(),
                        folder.trim(),
                        username.trim(),
                        password,
                    )
                },
                onSave = { (host, share, folder, username, password) ->
                    AppSettings.setSmbHost(host.trim())
                    AppSettings.setSmbShare(share.trim())
                    AppSettings.setSmbBasePath(folder.trim())
                    AppSettings.setSmbUsername(username.trim())
                    AppSettings.setSmbPassword(password)
                    showSmbEditor = false
                },
                onDismiss = { showSmbEditor = false },
            )
        }

        // A clash mid-upload, answered here so the scrim covers the tab bar
        // and mini player like every other alert. Backing out is a skip —
        // leaving the batch suspended on a dismissed dialog would hang the
        // upload with no way to reach the question again.
        val uploadConflict by com.music.bitchord.data.webdav.WebDavUploads.conflict.collectAsStateWithLifecycle()
        uploadConflict?.let { req ->
            var applyToAll by remember(req) { mutableStateOf(false) }
            val answer: (com.music.bitchord.data.webdav.WebDavUploads.Choice) -> Unit = { choice ->
                req.answer.complete(
                    com.music.bitchord.data.webdav.WebDavUploads.Resolution(choice, applyToAll),
                )
            }
            BackHandler { answer(com.music.bitchord.data.webdav.WebDavUploads.Choice.SKIP) }
            WebDavConflictAlert(
                hazeState = hazeState,
                fileName = req.fileName,
                showApplyToAll = req.remaining > 0,
                applyToAll = applyToAll,
                onApplyToAllChange = { applyToAll = it },
                onOverwrite = { answer(com.music.bitchord.data.webdav.WebDavUploads.Choice.OVERWRITE) },
                onKeepBoth = { answer(com.music.bitchord.data.webdav.WebDavUploads.Choice.KEEP_BOTH) },
                onSkip = { answer(com.music.bitchord.data.webdav.WebDavUploads.Choice.SKIP) },
                onDismiss = { answer(com.music.bitchord.data.webdav.WebDavUploads.Choice.SKIP) },
            )
        }

        // ---- Discord sign-in (full screen WebView) ----
        if (showDiscordLogin) {
            BackHandler { showDiscordLogin = false }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { showDiscordLogin = false }) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        Text(
                            "Sign in to Discord",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    DiscordLoginScreen(
                        onTokenCaptured = { token ->
                            AppSettings.setDiscordToken(token)
                            showDiscordLogin = false
                        },
                    )
                }
            }
        }

        // ---- Spotify sign-in (full screen WebView) ----
        // Out here with Discord's rather than on the Spotify page itself: see
        // SpotifyLoginScreen for what the blurred content tree does to a WebView.
        if (showSpotifyLogin) {
            BackHandler { showSpotifyLogin = false }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { showSpotifyLogin = false }) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        Text(
                            stringResource(R.string.spotify_sign_in),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    SpotifyLoginScreen(
                        modifier = Modifier.navigationBarsPadding(),
                        onConnected = { token ->
                            AppSettings.setSpotifySpdcToken(token)
                            showSpotifyLogin = false
                        },
                    )
                }
            }
        }

        if (showSpotifyCanvasAuth) {
            BackHandler { showSpotifyCanvasAuth = false }
            SpotifyCanvasAuthScreen(
                onNavigateUp = { showSpotifyCanvasAuth = false }
            )
        }

        discordDialog?.let { which ->
            DiscordDialogHost(
                which = which,
                hazeState = hazeState,
                onDismiss = { discordDialog = null },
            )
        }

        // At the root with the source editor, and for the same two reasons: it
        // is a haze card that has to sample the backdrop it is *not* inside,
        // and a full-window scrim that has to be full-window.
        if (editingPartyServer) {
            PartyServerEditor(
                hazeState = hazeState,
                onDismiss = { editingPartyServer = false },
            )
        }

        if (editingSignatureApi) {
            SignatureApiEditor(
                hazeState = hazeState,
                onDismiss = { editingSignatureApi = false },
            )
        }

        editingSource?.let { config ->
            SourceEditorAlert(
                hazeState = hazeState,
                config = config,
                onDismiss = { editingSource = null },
                onSaved = { editingSource = null },
                onDelete = {
                    SourceRegistry.remove(config.id)
                    editingSource = null
                },
                scope = scope,
            )
        }

        if (confirmJioSaavn) {
            ConfirmationAlert(
                hazeState = hazeState,
                title = stringResource(R.string.enable_jiosaavn),
                description = stringResource(R.string.jiosaavn_mismatch_warning),
                confirmLabel = stringResource(R.string.enable_anyway),
                onConfirm = {
                    SourceRegistry.configs.value
                        .firstOrNull { it.kind == SourceKind.JIOSAAVN }
                        ?.let { SourceRegistry.setEnabled(it.id, true) }
                    confirmJioSaavn = false
                },
                onDismiss = { confirmJioSaavn = false },
            )
        }

        confirmBitPerfect?.let { mode ->
            ConfirmationAlert(
                hazeState = hazeState,
                title = stringResource(R.string.bit_perfect_confirm_title),
                description = stringResource(R.string.bit_perfect_confirm_message),
                confirmLabel = stringResource(R.string.enable),
                onConfirm = {
                    AppSettings.setBitPerfectMode(mode)
                    confirmBitPerfect = null
                },
                onDismiss = { confirmBitPerfect = null },
            )
        }

        if (confirmFixedVolume) {
            ConfirmationAlert(
                hazeState = hazeState,
                title = stringResource(R.string.usb_allow_fixed_volume_confirm_title),
                description = stringResource(R.string.usb_allow_fixed_volume_confirm_message),
                confirmLabel = stringResource(R.string.enable),
                onConfirm = {
                    AppSettings.setUsbAllowFixedVolume(true)
                    confirmFixedVolume = false
                },
                onDismiss = { confirmFixedVolume = false },
            )
        }

        // Over the page and its frosted bars, under nothing but the alerts.
        DacVolumeHud(hazeState)

        blockedAddonSong?.let { song ->
            AddonBlockedAlert(
                hazeState = hazeState,
                song = song,
                onDismiss = { blockedAddonSong = null },
            )
        }

        addonLeftOutOfDownload?.let { (addon, why) ->
            InfoAlert(
                hazeState = hazeState,
                title = stringResource(R.string.download_addon_left_out_title, addon),
                description = when (why) {
                    SourceRegistry.DownloadExclusion.DOWNLOADS_NOT_ALLOWED ->
                        stringResource(R.string.download_addon_not_allowed, addon)
                    SourceRegistry.DownloadExclusion.NEEDS_LOSSLESS_OUTPUT ->
                        stringResource(R.string.download_addon_needs_lossless_output, addon)
                },
                onDismiss = { addonLeftOutOfDownload = null },
                // A download can be asked for from the player or from either
                // menu, and each is a window of its own over this one. Drawn
                // here, the alert opened behind whichever it was.
                inOwnWindow = playerRaised || songActions != null || browseActions != null,
            )
        }

    }
}

/**
 * The addon exclusions already explained this session, as `configId:reason` —
 * see [SourceRegistry.addonsLeftOutOfDownloads]. Process-wide rather than
 * remembered by the composition, so turning the phone doesn't say it again.
 */
private val explainedDownloadExclusions = java.util.Collections.synchronizedSet(mutableSetOf<String>())

/** UIKit's sheet curve: leaves at once, settles long — a sheet let go of, not pushed. */
private val OnboardingDismissEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

private fun tween(durationMillis: Int) =
    androidx.compose.animation.core.tween<Float>(durationMillis)

@Composable
private fun LibrarySort.localizedLabel(): String = when (this) {
    LibrarySort.DEFAULT -> stringResource(R.string.sort_default)
    LibrarySort.TITLE_ASC -> stringResource(R.string.sort_title_ascending)
    LibrarySort.TITLE_DESC -> stringResource(R.string.sort_title_descending)
}

/**
 * The track-list sort menu, styled after the account switcher: a full-screen
 * scrim to catch the dismissal tap, and the options on a frosted panel that
 * blurs the page behind it. Composed here in the main hierarchy rather than
 * as a popup window — which is exactly what lets the haze see the content it
 * is blurring.
 */
@Composable
private fun FrostedSortMenu(
    hazeState: HazeState,
    selected: SongSort,
    onSelect: (SongSort) -> Unit,
    onFlipDateDirection: () -> Unit,
    onDismiss: () -> Unit,
) {
    FrostedMenuPanel(hazeState = hazeState, onDismiss = onDismiss) {
        // Date added is one row, Spotify-style: the arrow on it shows
        // the direction — up for newest first, down for oldest — and
        // tapping flips it, the rotation animating the flip. Up is
        // also where a fresh activation lands, newest first being the
        // point of the feature.
        val dateActive = selected == SongSort.DATE_ADDED_ASC ||
            selected == SongSort.DATE_ADDED_DESC
        val arrowRotation by animateFloatAsState(
            targetValue = if (selected == SongSort.DATE_ADDED_ASC) 180f else 0f,
            animationSpec = tween(durationMillis = 200),
            label = "dateAddedArrow",
        )
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { onFlipDateDirection() }
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.sort_date_added_toggle),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (dateActive) {
                Icon(
                    Icons.Rounded.ArrowUpward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.rotate(arrowRotation),
                )
            }
        }
        SongSort.entries
            .filter { it != SongSort.DATE_ADDED_ASC && it != SongSort.DATE_ADDED_DESC }
            .forEach { option ->
                FrostedMenuRow(
                    label = option.localizedLabel(),
                    checked = option == selected,
                    onClick = { onSelect(option) },
                )
            }
    }
}

/**
 * A device folder's menu, off the same button: list or grid for its tabs, then
 * its sort orders — the Files app's arrangement, view first and order under it.
 */
@Composable
private fun FrostedFolderMenu(
    hazeState: HazeState,
    sort: LocalMusicSort,
    viewType: LibraryViewType,
    onSort: (LocalMusicSort) -> Unit,
    onToggleViewType: () -> Unit,
    onDismiss: () -> Unit,
) {
    FrostedMenuPanel(hazeState = hazeState, onDismiss = onDismiss) {
        val grid = viewType == LibraryViewType.GRID
        FrostedMenuRow(
            label = stringResource(if (grid) R.string.switch_to_list_view else R.string.switch_to_grid_view),
            checked = false,
            onClick = onToggleViewType,
            icon = if (grid) BitChordIcons.ListView else BitChordIcons.GridView,
        )
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        LocalMusicSort.entries.forEach { option ->
            FrostedMenuRow(
                label = option.localizedLabel(),
                checked = option == sort,
                onClick = { onSort(option) },
            )
        }
    }
}

/** The scrim and frosted panel both top-bar menus are drawn on. */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun FrostedMenuPanel(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val shape = MaterialTheme.shapes.extraLarge
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = .48f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.TopEnd,
    ) {
        Surface(
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = shape,
            modifier = Modifier
                .padding(top = 56.dp, end = 20.dp)
                .width(IntrinsicSize.Max)
                .clip(shape)
                .then(
                    if (reduceDynamicBlur) {
                        Modifier.background(MaterialTheme.colorScheme.surface)
                    } else {
                        Modifier.optimizedHazeEffect(
                            state = hazeState,
                            style = HazeMaterials.thin(MaterialTheme.colorScheme.surface),
                        )
                    },
                )
                .clickable(onClick = {}),
        ) {
            Column(Modifier.padding(vertical = 8.dp), content = content)
        }
    }
}

/** One option on a frosted menu: its label, and a tick or [icon] at the end. */
@Composable
private fun FrostedMenuRow(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (checked) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        } else if (icon != null) {
            Spacer(Modifier.width(16.dp))
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SongSort.localizedLabel(): String = when (this) {
    SongSort.DEFAULT -> stringResource(R.string.sort_default)
    SongSort.TITLE_ASC -> stringResource(R.string.sort_title_ascending)
    SongSort.TITLE_DESC -> stringResource(R.string.sort_title_descending)
    SongSort.DATE_ADDED_ASC -> stringResource(R.string.sort_date_added_oldest)
    SongSort.DATE_ADDED_DESC -> stringResource(R.string.sort_date_added)
}

/**
 * Whether this page id is one of the device folders — `local:downloads`,
 * `local:all` and the rest, the pages with Songs / Artists / Albums tabs.
 *
 * Asked rather than `startsWith("local:")` because that prefix covers unlike
 * pages: a folder, one downloaded playlist, and an album or artist grouped out
 * of a folder — the last two plain track listings that want the same chrome
 * every other release page gets. See [Downloads.PLAYLIST_PREFIX] and
 * [LocalGroups.PREFIX] for why they share a namespace at all.
 */
private fun String?.isDeviceFolder(): Boolean =
    this != null && startsWith("local:") &&
        !startsWith(Downloads.PLAYLIST_PREFIX) && !startsWith(LocalGroups.PREFIX) &&
        !startsWith(OfflinePlaylists.PREFIX)

/**
 * Whether [videoId] is the track playing, and is known to be playing YouTube's
 * own copy — which decides whether the player menu leads with "Revert to
 * original" or with "Upgrade quality".
 *
 * See [QualityUpgrade.isKnownToBePlayingYouTubesOwn] for why "known" is doing
 * real work here: an unresolved track playing off the disk cache is judged by
 * the origin its cache entry was tagged with, and keeps the revert on offer
 * only when the entry has none.
 */
private fun playingYouTubesOwn(videoId: String, controller: MediaController?): Boolean {
    val item = controller?.currentMediaItem?.takeIf { it.mediaId == videoId } ?: return false
    val uri = item.localConfiguration?.uri
    return QualityUpgrade.isKnownToBePlayingYouTubesOwn(
        videoId,
        uri?.let(QualityUpgrade::cacheTag),
        uri?.let(AudioCache::originOf),
    )
}

/** `M:SS`/`H:MM:SS`, the same shape [String?.durationMillis] parses back. */
private fun formatDurationText(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(Locale.ROOT, minutes, seconds)
}

/**
 * How far short of the end a seek is allowed to land.
 *
 * Seeking to the final millisecond is indistinguishable from the track running
 * out, so it starts the next song — which is not what anyone dragging to the end
 * of the bar, or tapping the last line of a lyric, is asking for. A second back
 * from the end plays the outro instead.
 */
private const val SEEK_END_GUARD_MS = 1_000L

/**
 * How far a detail page scrolls before its title moves up into the bar.
 *
 * Roughly the height of the sleeve and the credit stacked above the Play pair,
 * so the two titles hand over as the header one leaves rather than sitting on
 * screen together. The bar cross-fades over 220ms, which absorbs the difference
 * between that estimate and a particular page's real header.
 */
private val DETAIL_TITLE_DROP = 320.dp

/**
 * How fast, a second, a mini-player pull has to be moving when the finger
 * lifts to decide open or closed on its own, wherever it got to — see
 * [MiniPlayerPull]. A flick is a whole gesture; only a slow pull is judged by
 * the distance it covered.
 */
private val PLAYER_PULL_FLING_VELOCITY = 400.dp

/** M3's own scrim strength, which the player's dim stands in for. */
private const val PLAYER_SCRIM_ALPHA = 0.32f

/**
 * [content] in a recompose scope of its own, so state read inside it
 * recomposes it alone rather than everything it is written in.
 */
@Composable
private fun Isolated(content: @Composable () -> Unit) = content()

/** Longer than the ⋯ menu's exit, so its panel keeps its frost on the way out. */
private const val SNAPSHOT_RELEASE_DELAY_MS = 400L

/**
 * The player sheet's shape: square, as [RectangleShape] was, but reaching a
 * whole sheet's height above the sheet's top edge.
 *
 * The sheet's Surface clips its content to this shape, and on the way down to
 * the mini player the artwork travels ahead of the sheet carrying it — up
 * above the sheet's top edge, where a plain rectangle cut it off mid-air,
 * leaving only its lower part to arrive in the bar. Nothing else is drawn
 * outside the sheet (its background is transparent and it casts no shadow),
 * so at rest this is the rectangle it replaces.
 */
private object PlayerSheetShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(Rect(0f, -size.height, size.width, size.height))
}

private const val TAB_HOME = 0
private const val TAB_EXPLORE = 1
private const val TAB_LIBRARY = 2
private const val TAB_SEARCH = 3
