package com.music.bitchord

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.music.bitchord.auth.YtMusicLoginScreen
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.screens.SettingsScreen
import com.music.bitchord.playback.QueueBuilder
import com.music.bitchord.playback.QueueShuffle
import com.music.bitchord.playback.manualQueueEnd
import com.music.bitchord.playback.playSongs
import com.music.bitchord.playback.toMediaItem
import com.music.bitchord.ui.components.SongActionsSheet
import com.music.bitchord.playback.rememberMediaController
import com.music.bitchord.playback.rememberPlayerState
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.components.BottomTab
import com.music.bitchord.ui.components.FloatingBottomBar
import com.music.bitchord.ui.components.FrostedTopBar
import com.music.bitchord.ui.components.MiniPlayer
import com.music.bitchord.ui.icons.BitChordIcons
import androidx.media3.common.Player
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.ui.player.NowPlayingScreen
import com.music.bitchord.ui.screens.DetailScreen
import com.music.bitchord.ui.screens.HomeScreen
import com.music.bitchord.ui.screens.LibraryScreen
import com.music.bitchord.ui.screens.SearchScreen
import com.music.bitchord.ui.theme.BitChordTheme
import com.music.bitchord.ui.theme.SystemBarIcons
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by AppSettings.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            BitChordTheme(darkTheme = darkTheme) {
                BitChordApp(darkTheme = darkTheme)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BitChordApp(darkTheme: Boolean, viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val hazeState = remember { HazeState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var showLogin by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var songActions by remember { mutableStateOf<Song?>(null) }
    val autoplay by AppSettings.autoplay.collectAsStateWithLifecycle()

    // The player fills the screen with dark artwork whichever theme is on, so
    // it keeps light glyphs; every other surface follows the theme.
    SystemBarIcons(dark = !darkTheme && !showNowPlaying)

    val homeState by viewModel.home.collectAsStateWithLifecycle()
    val homeLoadingMore by viewModel.homeLoadingMore.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val exploreState by viewModel.explore.collectAsStateWithLifecycle()
    val libraryState by viewModel.library.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val lyricsChecked by viewModel.lyricsChecked.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val updateAvailable by viewModel.updateAvailable.collectAsStateWithLifecycle()
    val detailStack by viewModel.detailStack.collectAsStateWithLifecycle()
    val detail = detailStack.lastOrNull()
    // Settings has no tab of its own — it sits on top of whatever tab was
    // selected. A pushed album/artist page (from the player, search, etc.)
    // should surface above it rather than being hidden behind it.
    LaunchedEffect(detail) { if (detail != null) showSettings = false }

    val controller = rememberMediaController()
    val player = rememberPlayerState(controller)
    val shuffleEnabled by QueueShuffle.enabled.collectAsStateWithLifecycle()

    // AutoPlay: once the queue reaches its last track, extend it with YouTube
    // Music's radio mix for that song so playback carries on by itself.
    var autoplaySeed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(autoplay, player.queueIndex, player.queue.size, player.song?.videoId) {
        val current = player.song?.videoId
        if (!autoplay || current == null) return@LaunchedEffect
        if (player.queueIndex < player.queue.lastIndex) return@LaunchedEffect
        if (autoplaySeed == current) return@LaunchedEffect
        autoplaySeed = current
        YtMusicRepository.radio(current).onSuccess { related ->
            val extra = QueueBuilder.extend(player.queue, related, RADIO_BATCH)
            if (extra.isNotEmpty()) {
                // Swapped for the catalogue audio track before it ever
                // reaches the queue — see YtMusicRepository.resolveAudio.
                val resolved = coroutineScope {
                    extra.map { async { YtMusicRepository.resolveAudio(it) } }.awaitAll()
                }
                controller?.addMediaItems(
                    resolved.map { it.copy(fromAutoplay = true).toMediaItem() },
                )
            }
        }
    }

    // Lyrics follow whatever is playing; duration lands a beat after the track.
    LaunchedEffect(player.song?.videoId, player.durationMs) {
        player.song?.let { viewModel.loadLyrics(it.videoId, it.title, it.artist, player.durationMs) }
    }

    val homeListState = rememberLazyListState()
    val exploreListState = rememberLazyListState()
    val libraryListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val currentListState = when (selectedTab) {
        TAB_HOME -> homeListState
        TAB_EXPLORE -> exploreListState
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
        showSettings || detail != null -> null
        selectedTab == TAB_HOME -> MainViewModel.Feed.HOME
        selectedTab == TAB_EXPLORE -> MainViewModel.Feed.EXPLORE
        selectedTab == TAB_LIBRARY -> MainViewModel.Feed.LIBRARY
        else -> null
    }
    // The lead shelf is listening history, so opening Home after playing
    // something is exactly when it needs re-fetching.
    LaunchedEffect(currentFeed) {
        if (currentFeed == MainViewModel.Feed.HOME) viewModel.onHomeShown()
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

    val tabs = remember {
        listOf(
            BottomTab("Play", BitChordIcons.Play),
            BottomTab("Explore", BitChordIcons.Explore),
            BottomTab("Library", BitChordIcons.Library),
            BottomTab("Search", BitChordIcons.Search),
        )
    }

    val scope = rememberCoroutineScope()

    /**
     * A video-tagged [Song] is swapped for its catalogue audio release
     * before the queue, the notification or YouTube's own history ever see
     * it — see [YtMusicRepository.resolveAudio]. Plain songs pass through
     * this untouched and unawaited (`isVideo` is false, so the suspend call
     * returns immediately), so this costs nothing on the common path.
     */
    suspend fun List<Song>.resolvedForQueue(): List<Song> = coroutineScope {
        map { async { YtMusicRepository.resolveAudio(it) } }.awaitAll()
    }

    val play: (List<Song>, Int) -> Unit = { songs, index ->
        scope.launch {
            val starting = YtMusicRepository.resolveAudio(songs[index])
            val queued = songs.toMutableList().also { it[index] = starting }
            controller?.playSongs(queued, index)
            showNowPlaying = true
            // Starting playback only waits on the track about to play; the
            // rest of a long album/playlist resolves in the background and
            // is patched into the queue well before it's reached.
            queued.forEachIndexed { i, song ->
                if (i == index || !song.isVideo) return@forEachIndexed
                launch {
                    val resolved = YtMusicRepository.resolveAudio(song)
                    if (resolved.videoId == song.videoId) return@launch
                    // Found by id rather than by the index it went in at:
                    // shuffling and queue edits both move tracks around while
                    // this is in flight, and a song that has since been removed
                    // must not have something else overwritten in its place.
                    val c = controller ?: return@launch
                    val at = (0 until c.mediaItemCount)
                        .firstOrNull { c.getMediaItemAt(it).mediaId == song.videoId }
                        ?: return@launch
                    c.replaceMediaItem(at, resolved.toMediaItem())
                }
            }
        }
    }

    /**
     * A song picked on its own — off a home card or a search hit — starts a
     * station rather than queueing the list it was shown in. Searching
     * "Perfect" and tapping the top hit otherwise queues twenty covers and
     * remixes of the same song. Album, artist and playlist pages keep [play],
     * where the surrounding list *is* the thing the user asked for.
     */
    val playRadio: (Song) -> Unit = { song ->
        // Claim the AutoPlay seed up front: a one-track queue is already at its
        // end, so that effect would otherwise fetch the same radio in parallel.
        autoplaySeed = song.videoId
        scope.launch {
            val resolved = YtMusicRepository.resolveAudio(song)
            autoplaySeed = resolved.videoId
            controller?.playSongs(listOf(resolved), 0)
            showNowPlaying = true
            YtMusicRepository.radio(resolved.videoId).onSuccess { related ->
                // The user may have moved on while the mix was loading.
                if (controller?.currentMediaItem?.mediaId != resolved.videoId) return@onSuccess
                val extra = QueueBuilder.extend(listOf(resolved), related, RADIO_BATCH)
                if (extra.isNotEmpty()) {
                    // The station's own mix, which the queue files under
                    // AutoPlay just like the tracks it appends later — only the
                    // seed was actually asked for.
                    controller?.addMediaItems(
                        extra.resolvedForQueue().map {
                            it.copy(fromAutoplay = true).toMediaItem()
                        },
                    )
                }
            }
        }
    }
    val addToQueue: (Song) -> Unit = { song ->
        scope.launch {
            val resolved = YtMusicRepository.resolveAudio(song)
            // The end of what the user queued, not the end of the queue: a song
            // asked for by name outranks whatever AutoPlay lined up behind it.
            controller?.let { it.addMediaItem(it.manualQueueEnd(), resolved.toMediaItem()) }
            Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
        }
    }
    val playNext: (Song) -> Unit = { song ->
        scope.launch {
            val resolved = YtMusicRepository.resolveAudio(song)
            controller?.let {
                it.addMediaItem(
                    (it.currentMediaItemIndex + 1).coerceAtMost(it.mediaItemCount),
                    resolved.toMediaItem(),
                )
            }
            Toast.makeText(context, "Playing next", Toast.LENGTH_SHORT).show()
        }
    }

    // Content padding leaves room for the frosted bar above and the tab bar
    // (plus mini player) below, so nothing is ever trapped under the glass.
    val listPadding = PaddingValues(
        top = 96.dp,
        bottom = if (player.song != null) 210.dp else 140.dp,
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // A pushed album/artist/playlist page replaces the tab content but
        // leaves the tab bar and mini player in place.
        BackHandler(enabled = detail != null) { viewModel.closeDetail() }
        // One back step out of Settings, or out of any tab but Home, lands on
        // Home rather than exiting — only Home itself hands back to the system,
        // which is what actually closes/minimizes the app.
        BackHandler(enabled = detail == null && showSettings) {
            showSettings = false
            selectedTab = TAB_HOME
        }
        BackHandler(enabled = detail == null && !showSettings && selectedTab != TAB_HOME) {
            selectedTab = TAB_HOME
        }

        AnimatedContent(
            targetState = when {
                showSettings -> "settings"
                detail != null -> detail.browseId
                else -> "tab:$selectedTab"
            },
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
            modifier = Modifier.hazeSource(hazeState),
            label = "content",
        ) { key ->
            val page = detailStack.lastOrNull()?.takeIf { it.browseId == key && key != "settings" }
            if (key == "settings") {
                SettingsScreen(
                    signedIn = signedIn,
                    account = account,
                    onSignIn = {
                        showSettings = false
                        showLogin = true
                    },
                    onSignOut = { viewModel.signOut() },
                    contentPadding = listPadding,
                )
            } else if (page != null) {
                DetailScreen(
                    page = page,
                    onSongClick = play,
                    onSongLongPress = { songActions = it },
                    onSongSwipe = addToQueue,
                    onShuffle = { songs ->
                        // Shuffle goes on first so the queue is built shuffled
                        // as it is set — the random pick here only decides
                        // which track leads it.
                        QueueShuffle.enableForNextQueue()
                        play(songs, songs.indices.random())
                    },
                    onSectionItemClick = { item ->
                        item.browseId?.let { id ->
                            viewModel.openDetail(
                                browseId = id,
                                title = item.title,
                                subtitle = item.subtitle,
                                thumbnailUrl = item.thumbnailUrl,
                                type = BrowseType.ALBUM,
                            )
                        }
                    },
                    contentPadding = listPadding,
                )
            } else when (selectedTab) {
                TAB_HOME -> HomeScreen(
                    state = homeState,
                    listState = homeListState,
                    signedIn = signedIn,
                    onSignIn = { showLogin = true },
                    onItemClick = { item ->
                        when {
                            item.videoId != null -> playRadio(
                                Song(
                                    videoId = item.videoId,
                                    title = item.title,
                                    artist = item.subtitle,
                                    thumbnailUrl = item.thumbnailUrl,
                                ),
                            )
                            item.browseId != null -> viewModel.openDetail(
                                browseId = item.browseId,
                                title = item.title,
                                subtitle = item.subtitle,
                                thumbnailUrl = item.thumbnailUrl,
                            )
                        }
                    },
                    onRetry = viewModel::loadHome,
                    refreshing = MainViewModel.Feed.HOME in refreshing,
                    onRefresh = { viewModel.refresh(MainViewModel.Feed.HOME) },
                    pullState = homePull,
                    contentPadding = listPadding,
                    onLoadMore = viewModel::loadMoreHome,
                    loadingMore = homeLoadingMore,
                )
                TAB_EXPLORE -> HomeScreen(
                    state = exploreState,
                    listState = exploreListState,
                    title = "Explore",
                    onItemClick = { item ->
                        when {
                            item.videoId != null -> playRadio(
                                Song(
                                    videoId = item.videoId,
                                    title = item.title,
                                    artist = item.subtitle,
                                    thumbnailUrl = item.thumbnailUrl,
                                ),
                            )
                            item.browseId != null -> viewModel.openDetail(
                                browseId = item.browseId,
                                title = item.title,
                                subtitle = item.subtitle,
                                thumbnailUrl = item.thumbnailUrl,
                            )
                        }
                    },
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
                    onFilterChange = viewModel::onFilterChange,
                    results = results,
                    listState = searchListState,
                    // Search hits are alternatives to each other, not a running
                    // order — play the one tapped and build a station from it.
                    onSongClick = { songs, index ->
                        songs.getOrNull(index)?.let {
                            // Acting on a hit is what makes the query worth
                            // keeping — see MainViewModel.recordSearch.
                            viewModel.recordSearch()
                            playRadio(it)
                        }
                    },
                    onSongLongPress = { songActions = it },
                    onSongSwipe = addToQueue,
                    onBrowseClick = { item ->
                        viewModel.recordSearch()
                        viewModel.openDetail(
                            browseId = item.browseId,
                            title = item.title,
                            subtitle = item.subtitle,
                            thumbnailUrl = item.thumbnailUrl,
                            type = item.type,
                        )
                    },
                    history = searchHistory,
                    onSubmit = viewModel::recordSearch,
                    onHistoryClick = viewModel::searchFor,
                    onHistoryRemove = viewModel::removeSearch,
                    onHistoryClear = viewModel::clearSearchHistory,
                    contentPadding = listPadding,
                )
                else -> LibraryScreen(
                    signedIn = signedIn,
                    state = libraryState,
                    listState = libraryListState,
                    onSongClick = play,
                    onSongLongPress = { songActions = it },
                    onSongSwipe = addToQueue,
                    onShelfItemClick = { item ->
                        item.browseId?.let { id ->
                            viewModel.openDetail(
                                browseId = id,
                                title = item.title,
                                subtitle = item.subtitle,
                                thumbnailUrl = item.thumbnailUrl,
                            )
                        }
                    },
                    onSignIn = { showLogin = true },
                    onRetry = viewModel::loadLibrary,
                    refreshing = MainViewModel.Feed.LIBRARY in refreshing,
                    onRefresh = { viewModel.refresh(MainViewModel.Feed.LIBRARY) },
                    pullState = libraryPull,
                    contentPadding = listPadding,
                )
            }
        }

        FrostedTopBar(
            title = when {
                showSettings -> "Settings"
                detail != null -> detail.title
                else -> tabs[selectedTab].let {
                    if (it.label == "Play") "Listen Now" else it.label
                }
            },
            hazeState = hazeState,
            // Search has no large in-list header to hand the title back to —
            // the field takes that space — so its bar title is always up.
            scrolled = scrolled || detail != null || showSettings ||
                selectedTab == TAB_SEARCH,
            refreshing = currentFeed != null && currentFeed in refreshing,
            pullFraction = { currentPull?.distanceFraction ?: 0f },
            onBack = when {
                showSettings -> ({ showSettings = false })
                detail != null -> ({ viewModel.closeDetail(); Unit })
                else -> null
            },
            modifier = Modifier.align(Alignment.TopCenter),
            actions = {
                // Only worth surfacing where there's room for it and it won't
                // be mistaken for a per-page action — Home, at rest.
                if (!showSettings && detail == null && selectedTab == TAB_HOME) {
                    updateAvailable?.let { update ->
                        IconButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl)))
                        }) {
                            Icon(
                                Icons.Rounded.SystemUpdate,
                                contentDescription = "Update available: v${update.version}",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                if (!showSettings) IconButton(onClick = { showSettings = true }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            player.song?.let { song ->
                MiniPlayer(
                    song = song,
                    isPlaying = player.isPlaying,
                    isLoading = player.isLoading,
                    hazeState = hazeState,
                    onPlayPause = {
                        controller?.let { if (it.isPlaying) it.pause() else it.play() }
                    },
                    onNext = { controller?.seekToNextMediaItem() },
                    onExpand = { showNowPlaying = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FloatingBottomBar(
                tabs = tabs,
                selectedIndex = selectedTab,
                onTabSelected = {
                    // Leave any pushed album/artist page, or Settings, when
                    // switching tabs.
                    viewModel.clearDetail()
                    showSettings = false
                    selectedTab = it
                },
                hazeState = hazeState,
            )
        }

        // ---- Now Playing ----
        if (showNowPlaying && player.song != null) {
            // Whatever started this track knew its title and its artwork, but
            // rarely which album or artist page it belongs to. Fill that in
            // once the player is up, so the credits can be tapped through.
            var links by remember { mutableStateOf<Song?>(null) }
            LaunchedEffect(player.song?.videoId) {
                links = null
                val current = player.song ?: return@LaunchedEffect
                if (current.albumId != null && current.artistId != null) return@LaunchedEffect
                links = YtMusicRepository.trackLinks(current.videoId).getOrNull()
            }
            val song = player.song!!.let { current ->
                val extra = links?.takeIf { it.videoId == current.videoId }
                    ?: return@let current
                current.copy(
                    artistId = current.artistId ?: extra.artistId,
                    albumId = current.albumId ?: extra.albumId,
                    albumName = current.albumName ?: extra.albumName,
                )
            }
            // The three-dot menu snapshots `song` into songActions when it's
            // opened, so a menu opened before the lookup above resolves would
            // otherwise be stuck without album/artist rows even after the ids
            // come in. Keep it in sync while it's showing this track.
            LaunchedEffect(song) {
                if (songActions?.videoId == song.videoId) songActions = song
            }
            ModalBottomSheet(
                onDismissRequest = { showNowPlaying = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = Color.Transparent,
                dragHandle = null,
                contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            ) {
                NowPlayingScreen(
                    song = song,
                    isPlaying = player.isPlaying,
                    isLoading = player.isLoading,
                    positionMs = player.positionMs,
                    durationMs = player.durationMs,
                    onPlayPause = {
                        controller?.let { if (it.isPlaying) it.pause() else it.play() }
                    },
                    onNext = { controller?.seekToNextMediaItem() },
                    onPrevious = { controller?.seekToPrevious() },
                    onSeek = { controller?.seekTo(it) },
                    queue = player.queue,
                    queueIndex = player.queueIndex,
                    hasPrevious = player.hasPrevious,
                    hasNext = player.hasNext,
                    repeatMode = player.repeatMode,
                    shuffleEnabled = shuffleEnabled,
                    autoplayEnabled = autoplay,
                    onToggleShuffle = { controller?.let(QueueShuffle::toggle) },
                    onCycleRepeat = {
                        controller?.let {
                            it.repeatMode = when (it.repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                        }
                    },
                    onToggleAutoplay = { AppSettings.setAutoplay(!autoplay) },
                    onJumpTo = { controller?.seekToDefaultPosition(it) },
                    onRemoveFromQueue = { controller?.removeMediaItem(it) },
                    // The enriched copy, not player.song — otherwise the menu
                    // hides the album and artist rows even once their browse
                    // ids have been resolved.
                    onOpenMenu = { songActions = song },
                    onOpenAlbum = { id ->
                        showNowPlaying = false
                        viewModel.openDetail(
                            id,
                            song.albumName ?: song.title,
                            song.artist,
                            song.thumbnailUrl,
                            BrowseType.ALBUM,
                        )
                    },
                    onOpenArtist = { id ->
                        showNowPlaying = false
                        viewModel.openDetail(
                            id, song.artist, "Artist", song.thumbnailUrl, BrowseType.ARTIST,
                        )
                    },
                    lyrics = lyrics,
                    lyricsUnavailable = lyricsChecked && lyrics.isNullOrEmpty(),
                    onClearQueue = {
                        // Keep what's playing; drop everything queued after it.
                        controller?.let { c ->
                            if (c.mediaItemCount > c.currentMediaItemIndex + 1) {
                                c.removeMediaItems(c.currentMediaItemIndex + 1, c.mediaItemCount)
                            }
                        }
                    },
                )
            }
        }

        // ---- Album / playlist detail ----
        // ---- Long-press track actions ----
        songActions?.let { song ->
            // The player is the only thing that can be on screen while this
            // sheet is up, so it's also what "opened from the player" means.
            val fromPlayer = showNowPlaying
            val share: () -> Unit = {
                Toast.makeText(context, "Sharing is coming soon", Toast.LENGTH_SHORT).show()
                songActions = null
            }
            // Navigating has to take the player down with the sheet, or the
            // page it opens lands behind a still-covering player.
            val openPage: (String, String, String, BrowseType) -> Unit = { id, title, sub, type ->
                songActions = null
                showNowPlaying = false
                viewModel.openDetail(id, title, sub, song.thumbnailUrl, type)
            }
            ModalBottomSheet(
                onDismissRequest = { songActions = null },
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                SongActionsSheet(
                    song = song,
                    onPlayNext = { playNext(song); songActions = null },
                    onAddToQueue = { addToQueue(song); songActions = null },
                    onOpenAlbum = { id ->
                        openPage(
                            id,
                            song.albumName ?: song.title,
                            song.artist,
                            BrowseType.ALBUM,
                        )
                    },
                    onOpenArtist = { id ->
                        openPage(id, song.artist, "Artist", BrowseType.ARTIST)
                    },
                    showSleepTimer = fromPlayer,
                    onShare = share.takeIf { fromPlayer },
                )
            }
        }

        // ---- Google sign-in (full screen WebView) ----
        if (showLogin) {
            BackHandler { showLogin = false }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { showLogin = false }) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        Text(
                            "Sign in to YouTube Music",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    YtMusicLoginScreen(
                        onCookiesCaptured = { cookie ->
                            viewModel.onSignedIn(cookie)
                            showLogin = false
                            selectedTab = 2
                        },
                    )
                }
            }
        }
    }
}

private fun tween(durationMillis: Int) =
    androidx.compose.animation.core.tween<Float>(durationMillis)

/** How many tracks a station pulls in at a time. */
private const val RADIO_BATCH = 20

private const val TAB_HOME = 0
private const val TAB_EXPLORE = 1
private const val TAB_LIBRARY = 2
private const val TAB_SEARCH = 3
