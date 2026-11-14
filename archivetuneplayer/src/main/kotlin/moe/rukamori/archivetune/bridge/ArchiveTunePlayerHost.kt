/*
 * BRIDGE — the hosting entry point.
 *
 * What the app mounts when a ported player style is active. It installs the
 * host bridge, builds the PlayerConnection over the app's controller,
 * provides every composition local ArchiveTune's stack reads, and lays out
 * ArchiveTune's own BottomSheetPlayer — the mini player and the full player
 * as one sheet, exactly as ArchiveTune's activity hosted it. When the
 * style is BITCHORD none of this is mounted and the app's own player runs
 * untouched.
 */

package moe.rukamori.archivetune.bridge

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.createGraph
import androidx.navigation.compose.composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.LocalStableSystemBarsTopPadding
import moe.rukamori.archivetune.LocalSyncUtils
import moe.rukamori.archivetune.constants.MiniPlayerBottomSpacing
import moe.rukamori.archivetune.constants.MiniPlayerHeight
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.di.BridgeEntryPoints
import moe.rukamori.archivetune.playback.DownloadUtil
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.rememberBottomSheetState
import moe.rukamori.archivetune.ui.player.BottomSheetPlayer
import moe.rukamori.archivetune.ui.player.ProvideVideoFullscreenState
import moe.rukamori.archivetune.utils.PreferenceStore
import moe.rukamori.archivetune.utils.SyncUtils
import timber.log.Timber

/**
 * Mounts ArchiveTune's whole player stack over the app.
 *
 * @param hostBridge the app's answers (controller, likes, lyrics, playlists)
 * @param onOpenArtist the app's artist page for a browse id
 * @param onOpenAlbum the app's album page for a browse id
 * @param onOpenSearch the app's search
 * @param pureBlack the theme's pure-black dark mode, for the player's
 *   blackest background
 * @param bottomBarHeight how tall the app's own floating bottom bar is; the
 *   mini player rests on top of it
 */
@Composable
fun ArchiveTunePlayerHost(
    hostBridge: ArchiveTuneHostBridge,
    onOpenArtist: (String) -> Unit = {},
    onOpenAlbum: (String) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    pureBlack: Boolean = false,
    bottomBarHeight: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Everything the stack below reads during its very first composition has
    // to be in place before that composition starts — so this is a remember,
    // which runs inline, ahead of the PlayerConnection and BottomSheetPlayer
    // below, and not a DisposableEffect, which only runs after the first
    // composition has been applied. With it as an effect the first frame of
    // a ported style composed against an empty bridge: PlayerConnection bound
    // to the placeholder player, every hiltViewModel() hit an empty view-model
    // registry ("No bridge view-model registered for …") and the cache entry
    // points had no context ("no context registered with BridgeEntryPoints").
    // That was the crash on switching to a ported style. All four calls are
    // idempotent, so a re-mount (or an abandoned composition) is harmless.
    remember(hostBridge) {
        ArchiveTuneBridge.install(hostBridge)
        BridgeEntryPoints.context = context.applicationContext
        BridgeViewModels.init(context)
        PreferenceStore.start(context.applicationContext)
        hostBridge
    }
    DisposableEffect(hostBridge) {
        onDispose {
            // Only clear what is still ours: a newer host mounted under a new
            // controller key installs before this older one is disposed.
            if (ArchiveTuneBridge.get() === hostBridge) BridgeEntryPoints.context = null
            ArchiveTuneBridge.uninstall(hostBridge)
        }
    }
    // SimpleCache opens and scans its folder on construction — disk I/O that
    // must not run on the main thread mid-composition. Built once per process
    // (ArchiveTuneCaches guards that; a folder can only ever have one), never
    // torn down on dispose, and the entry points still build it lazily if a
    // component asks before this has finished.
    val appContext = context.applicationContext
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching { ArchiveTuneCaches.ensure(appContext) }
                .onFailure { Timber.tag("ArchiveTuneBridge").w(it, "media caches unavailable") }
        }
    }

    val playerConnection = remember(scope, hostBridge) { PlayerConnection(scope) }
    DisposableEffect(playerConnection) {
        onDispose { playerConnection.dispose() }
    }

    val database = remember { MusicDatabase() }
    val downloadUtil = remember { DownloadUtil(context) }
    val syncUtils = remember { SyncUtils() }
    val menuState = remember { MenuState() }
    val bottomSheetPageState = remember { BottomSheetPageState() }

    val navController =
        remember(onOpenArtist, onOpenAlbum, onOpenSearch) {
            BridgeNavController(context, onOpenArtist, onOpenAlbum, onOpenSearch)
        }

    BoxWithConstraints(modifier = modifier) {
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val collapsedBound = bottomInset + bottomBarHeight + MiniPlayerBottomSpacing + MiniPlayerHeight
        val state: BottomSheetState =
            rememberBottomSheetState(
                dismissedBound = 0.dp,
                collapsedBound = collapsedBound,
                expandedBound = maxHeight,
            )

        CompositionLocalProvider(
            LocalPlayerConnection provides playerConnection,
            LocalDatabase provides database,
            LocalDownloadUtil provides downloadUtil,
            LocalSyncUtils provides syncUtils,
            LocalStableSystemBarsTopPadding provides 0.dp,
            LocalMenuState provides menuState,
            LocalBottomSheetPageState provides bottomSheetPageState,
        ) {
            ProvideVideoFullscreenState {
                BottomSheetPlayer(
                    state = state,
                    navController = navController,
                    pureBlack = pureBlack,
                    isMiniPlayerPairedWithNavigation = false,
                    navbarHiddenOffset = { 0f },
                )
            }
        }
    }
}

/**
 * A NavController whose graph carries the three routes the player stack
 * navigates to — artist/{id}, album/{id} and search — each a composable
 * that immediately hands the id to the host app's own page. Routes beyond
 * those three (ArchiveTune's settings sub-pages, its login flows) have no
 * graph entry and are logged and dropped by the listener below, so nothing
 * the ported player can do takes the app anywhere unexpected.
 */
private class BridgeNavController(
    context: android.content.Context,
    private val onOpenArtist: (String) -> Unit,
    private val onOpenAlbum: (String) -> Unit,
    private val onOpenSearch: () -> Unit,
) : NavController(context) {

    init {
        addOnDestinationChangedListener { _, destination, _ ->
            when (destination.route) {
                "artist/{id}" -> onOpenArtist(currentBackStackEntry?.arguments?.getString("id").orEmpty())
                "album/{id}" -> onOpenAlbum(currentBackStackEntry?.arguments?.getString("id").orEmpty())
                "search" -> onOpenSearch()
                else -> Timber.tag("ArchiveTuneBridge").d("route %s has no bridge — dropped", destination.route)
            }
        }
        graph =
            createGraph(startDestination = "home", route = "home") {
                composable("home") {}
                composable("artist/{id}") {}
                composable("album/{id}") {}
                composable("search") {}
            }
    }
}
