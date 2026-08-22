package com.music.bitchord

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.music.bitchord.auth.YtMusicLoginScreen
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.data.sources.TrackMatcher
import com.music.bitchord.ui.screens.AccountAndScrobblingScreen
import com.music.bitchord.ui.screens.SettingsScreen
import com.music.bitchord.playback.QueueBuilder
import com.music.bitchord.playback.QueueShuffle
import com.music.bitchord.playback.autoplaySectionStart
import com.music.bitchord.playback.dropAutoplayTracks
import com.music.bitchord.playback.playSongs
import com.music.bitchord.playback.toMediaItem
import com.music.bitchord.download.DownloadStore
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.components.PlaylistActionsSheet
import com.music.bitchord.ui.components.PlaylistPickerSheet
import com.music.bitchord.ui.components.SongActionsSheet
import com.music.bitchord.playback.rememberMediaController
import com.music.bitchord.playback.rememberPlayerState
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.components.BottomFadeBlur
import com.music.bitchord.ui.components.BottomTab
import com.music.bitchord.ui.components.FloatingBottomBar
import com.music.bitchord.ui.components.FrostedTopBar
import com.music.bitchord.ui.components.LastfmLoginAlert
import com.music.bitchord.ui.components.ListenBrainzTokenAlert
import com.music.bitchord.ui.components.MiniPlayer
import com.music.bitchord.ui.components.TopFadeBlur
import com.music.bitchord.ui.components.LyricsSourcesDialog
import com.music.bitchord.ui.components.UpdateAvailableDialog
import com.music.bitchord.ui.icons.BitChordIcons
import androidx.media3.common.Player
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.ui.player.NowPlayingScreen
import com.music.bitchord.ui.screens.DetailScreen
import com.music.bitchord.ui.screens.LocalMusicScreen
import com.music.bitchord.ui.screens.HomeScreen
import com.music.bitchord.ui.screens.LibraryScreen
import com.music.bitchord.ui.screens.SearchScreen
import com.music.bitchord.ui.theme.BitChordTheme
import com.music.bitchord.ui.theme.rememberArtworkPalette
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
    val clipboard = LocalClipboardManager.current
    val hazeState = remember { HazeState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var showLogin by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAccountScrobbling by remember { mutableStateOf(false) }
    var showLyricsSources by remember { mutableStateOf(false) }
    var showListenBrainzLogin by remember { mutableStateOf(false) }
    var showLastfmLogin by remember { mutableStateOf(false) }
    var songActions by remember { mutableStateOf<Song?>(null) }
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
    var playlistActions by remember { mutableStateOf<UserPlaylist?>(null) }
    val autoplay by AppSettings.autoplay.collectAsStateWithLifecycle()
    val listenBrainzToken by AppSettings.listenBrainzToken.collectAsStateWithLifecycle()
    // Incremented each time the search tab is re-tapped while already selected,
    // which SearchScreen uses as a signal to focus the input field.
    var searchFocusTrigger by remember { mutableIntStateOf(0) }

    // The player fills the screen with dark artwork whichever theme is on, so
    // it keeps light glyphs; every other surface follows the theme.
    SystemBarIcons(dark = !darkTheme && !showNowPlaying)

    val homeState by viewModel.home.collectAsStateWithLifecycle()
    val homeLoadingMore by viewModel.homeLoadingMore.collectAsStateWithLifecycle()

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
    val libraryState by viewModel.library.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val lyricsSource by viewModel.lyricsSource.collectAsStateWithLifecycle()
    val lyricsChecked by viewModel.lyricsChecked.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val detailStack by viewModel.detailStack.collectAsStateWithLifecycle()
    val detail = detailStack.lastOrNull()
    // Local Music has no artwork to wash the bar in, so it renders with a
    // plain status bar rather than the artwork-driven blur other detail
    // pages (album/artist/playlist) get.
    val isLocalDetail = detail?.browseId == "local:all"
    val likeStatuses by viewModel.likeStatuses.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlistsLoading by viewModel.playlistsLoading.collectAsStateWithLifecycle()

    // Settings has no tab of its own — it sits on top of whatever tab was
    // selected. A pushed album/artist page (from the player, search, etc.)
    // should surface above it rather than being hidden behind it.
    LaunchedEffect(detail) { if (detail != null) showSettings = false }
    LaunchedEffect(showSettings) {
        if (!showSettings) {
            showAccountScrobbling = false
        }
    }

    val controller = rememberMediaController()
    val player = rememberPlayerState(controller)
    val shuffleEnabled by QueueShuffle.enabled.collectAsStateWithLifecycle()

    // AutoPlay: once the queue reaches its last track, extend it with YouTube
    // Music's radio mix for that song so playback carries on by itself.
    //
    // Repeat-all is left out of the trigger: its whole point is to loop the
    // queue as it stands, which AutoPlay extending it forever would defeat —
    // the queue would never actually reach the end repeat-all is meant to
    // wrap from. See onCycleRepeat, which drops whatever AutoPlay has already
    // added the moment repeat-all is turned on.
    var autoplaySeed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(autoplay, player.queueIndex, player.queue.size, player.song?.videoId, player.repeatMode) {
        val song = player.song
        val current = song?.videoId
        if (!autoplay || song == null || current == null) return@LaunchedEffect
        if (player.repeatMode == Player.REPEAT_MODE_ALL) return@LaunchedEffect
        if (player.queueIndex < player.queue.lastIndex) return@LaunchedEffect
        if (autoplaySeed == current) return@LaunchedEffect
        autoplaySeed = current
        // Radio is YouTube's, and only YouTube's — a module track's id means
        // nothing to it. See [youtubeSeedFor].
        val seed = youtubeSeedFor(song) ?: return@LaunchedEffect
        YtMusicRepository.radio(seed).onSuccess { related ->
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
    // Keyed on the lyric settings too, so turning a source on or off applies to
    // the track already playing rather than only the next one.
    val syncedLyricsEnabled by AppSettings.syncedLyrics.collectAsStateWithLifecycle()
    val lyricsSources by AppSettings.lyricsSources.collectAsStateWithLifecycle()
    LaunchedEffect(player.song?.videoId, player.durationMs, syncedLyricsEnabled, lyricsSources) {
        player.song?.let {
            viewModel.loadLyrics(it.videoId, it.title, it.artist, player.durationMs, it.albumName)
        }
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
        showSettings || showAccountScrobbling || detail != null -> null
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
    val detailListState = remember(detail?.browseId) { LazyListState() }
    val detailTitleDrop = with(LocalDensity.current) { DETAIL_TITLE_DROP.toPx() }
    val detailScrolled by remember(detailListState, detailTitleDrop) {
        derivedStateOf {
            detailListState.firstVisibleItemIndex > 0 ||
                detailListState.firstVisibleItemScrollOffset > detailTitleDrop
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
            // Radio is YouTube's, and only YouTube's — see [youtubeSeedFor].
            val seed = youtubeSeedFor(resolved) ?: return@launch
            YtMusicRepository.radio(seed).onSuccess { related ->
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
            controller?.let { it.addMediaItem(it.autoplaySectionStart(), resolved.toMediaItem()) }
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
        }
    }
    val onSongSwipe: (Song) -> Unit = { song ->
        if (AppSettings.swipeToPlayNext.value) playNext(song) else addToQueue(song)
    }

    // ---- Downloads ----
    // Two permissions, and never both on one device: writing to the shared
    // Music folder needs storage access below API 29 and none at all from
    // 29 on, where MediaStore grants an app its own rows; notifications are
    // only asked for from API 33. So the branches below are mutually exclusive
    // by SDK level, and nothing here can stack two dialogs on each other.
    var downloadPending by remember { mutableStateOf<List<Song>>(emptyList()) }
    val notifyPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Refusing costs the progress notification, not the download. */ }
    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val songs = downloadPending
        downloadPending = emptyList()
        when {
            songs.isEmpty() -> Unit
            granted -> songs.forEach { Downloads.enqueue(context, it) }
            // The one case where refusing is fatal: below API 29 there is no
            // other way to reach the Music folder.
            else -> Toast
                .makeText(context, "Storage access is needed to save songs", Toast.LENGTH_SHORT)
                .show()
        }
    }
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.reloadLocalDetail("local:all")
        } else {
            Toast.makeText(context, "Storage permission is required to read local audio files", Toast.LENGTH_SHORT).show()
        }
    }
    // Takes a list so a single tap on an album/playlist header can queue the
    // whole thing — the permission dance only needs to happen once for the
    // batch, not once per track.
    val startDownload: (List<Song>) -> Unit = { requested ->
        val saved = Downloads.saved.value
        // Already on disk, and already queued or running: neither needs asking
        // again. What's left is what a tap on "Download" actually means.
        val songs = requested.filter { it.videoId !in saved }
        if (songs.isNotEmpty()) {
            val needsStorage = DownloadStore.needsLegacyPermission() &&
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
                storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                songs.forEach { Downloads.enqueue(context, it) }
            }
        }
        if (requested.size > 1) {
            val message = if (songs.isEmpty()) {
                "Already downloaded"
            } else {
                "Downloading ${songs.size} song" + if (songs.size == 1) "" else "s"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Content padding leaves room for the frosted bar above and the tab bar
    // (plus mini player) below, so nothing is ever trapped under the glass.
    val listPadding = PaddingValues(
        top = 96.dp,
        bottom = if (player.song != null) 210.dp else 140.dp,
    )

    // What colour the page currently under the bars is. The fades either end
    // of the screen are flat colour wherever their blur has least to say, so
    // handing them the theme's background puts a black band on a page that is
    // washed in an artwork's colour instead. Off a detail page this resolves
    // to the theme's background anyway, which is exactly right there.
    val detailPalette = rememberArtworkPalette(detail?.thumbnailUrl)

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // A pushed album/artist/playlist page replaces the tab content but
        // leaves the tab bar and mini player in place.
        BackHandler(enabled = detail != null && !showSettings && !showAccountScrobbling) { viewModel.closeDetail() }
        BackHandler(enabled = showAccountScrobbling) {
            showAccountScrobbling = false
        }
        // One back step out of Settings, or out of any tab but Home, lands on
        // Home rather than exiting — only Home itself hands back to the system,
        // which is what actually closes/minimizes the app.
        BackHandler(enabled = showSettings && !showAccountScrobbling) {
            showSettings = false
            if (detail == null) selectedTab = TAB_HOME
        }
        BackHandler(enabled = detail == null && !showSettings && !showAccountScrobbling && selectedTab != TAB_HOME) {
            selectedTab = TAB_HOME
        }
        BackHandler(enabled = showUpdateDialog) { showUpdateDialog = false }
        BackHandler(enabled = showListenBrainzLogin) { showListenBrainzLogin = false }
        BackHandler(enabled = showLastfmLogin) { showLastfmLogin = false }

        AnimatedContent(
            targetState = when {
                showAccountScrobbling -> "account_scrobbling"
                showSettings -> "settings"
                detail != null -> detail.browseId
                else -> "tab:$selectedTab"
            },
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
            modifier = Modifier.hazeSource(hazeState),
            label = "content",
        ) { key ->
            val page = detailStack.lastOrNull()?.takeIf { it.browseId == key && key != "settings" && key != "account_scrobbling" }
            if (key == "account_scrobbling") {
                AccountAndScrobblingScreen(
                    signedIn = signedIn,
                    account = account,
                    onSignIn = {
                        showAccountScrobbling = false
                        showSettings = false
                        showLogin = true
                    },
                    onSignOut = { viewModel.signOut() },
                    onOpenListenBrainzLogin = { showListenBrainzLogin = true },
                    onOpenLastfmLogin = { showLastfmLogin = true },
                    contentPadding = listPadding,
                )
            } else if (key == "settings") {
                SettingsScreen(
                    signedIn = signedIn,
                    account = account,
                    onSignIn = {
                        showSettings = false
                        showLogin = true
                    },
                    onSignOut = { viewModel.signOut() },
                    onAccountScrobbling = { showAccountScrobbling = true },
                    onLyricsSources = { showLyricsSources = true },
                    contentPadding = listPadding,
                )
            } else if (page != null && page.browseId == "local:all") {
                // Local Music folder — show the tabbed Songs / Artists / Albums view.
                val localSongs = (page.songs as? com.music.bitchord.data.model.UiState.Success)?.data.orEmpty()
                LocalMusicScreen(
                    songs = localSongs,
                    onSongClick = play,
                    onSongLongPress = { songActions = it },
                    onSongSwipe = onSongSwipe,
                    onShuffle = { songs ->
                        QueueShuffle.enableForNextQueue()
                        play(songs, songs.indices.random())
                    },
                    contentPadding = listPadding,
                )
            } else if (page != null) {
                DetailScreen(
                    page = page,
                    listState = detailListState,
                    onSongClick = play,
                    onSongLongPress = { songActions = it },
                    onSongSwipe = onSongSwipe,
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
                    onDownloadAll = startDownload,
                    onArtistClick = { id, name ->
                        viewModel.openDetail(id, name, "Artist", null, BrowseType.ARTIST)
                    },
                    onAddSuggested = { song -> viewModel.addSuggestedSong(page.browseId, song) },
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
                    focusTrigger = searchFocusTrigger,
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
                    onSongSwipe = onSongSwipe,
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
                    onSubmit = viewModel::submitSearch,
                    onHistoryClick = viewModel::searchFor,
                    onHistoryRemove = viewModel::removeSearch,
                    onHistoryClear = viewModel::clearSearchHistory,
                    contentPadding = listPadding,
                )
                else -> LibraryScreen(
                    signedIn = signedIn,
                    state = libraryState,
                    listState = libraryListState,
                    onShelfItemClick = { item ->
                        item.browseId?.let { id ->
                            if (id == "local:all" && !LocalMediaRepository.hasStoragePermission(context)) {
                                val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    Manifest.permission.READ_MEDIA_AUDIO
                                } else {
                                    Manifest.permission.READ_EXTERNAL_STORAGE
                                }
                                mediaPermissionLauncher.launch(perm)
                            }
                            viewModel.openDetail(
                                browseId = id,
                                title = item.title,
                                subtitle = item.subtitle,
                                thumbnailUrl = item.thumbnailUrl,
                            )
                        }
                    },
                    // Only the account's own playlists have a menu behind
                    // them; holding a saved album or an artist does nothing.
                    onShelfItemLongPress = { item ->
                        playlistActions = viewModel.editablePlaylist(item.browseId)
                    },
                    onNewPlaylist = { creatingPlaylist = true },
                    onSignIn = { showLogin = true },
                    onRetry = viewModel::loadLibrary,
                    refreshing = MainViewModel.Feed.LIBRARY in refreshing,
                    onRefresh = { viewModel.refresh(MainViewModel.Feed.LIBRARY) },
                    pullState = libraryPull,
                    contentPadding = listPadding,
                )
            }
        }

        // A detail page's artwork runs up under the status bar, so the bar
        // there is a fade rather than a pane — see [TopFadeBlur]. Drawn before
        // the bar so the bar's own content sits on top of it.
        val isDetailVisible = detail != null && !isLocalDetail && !showSettings && !showAccountScrobbling
        if (isDetailVisible) {
            TopFadeBlur(
                hazeState = hazeState,
                pageColor = detailPalette.wash,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        FrostedTopBar(
            title = when {
                showAccountScrobbling -> "Account & scrobbling"
                showSettings -> "Settings"
                detail != null -> detail.title
                else -> tabs[selectedTab].let {
                    if (it.label == "Play") "Listen Now" else it.label
                }
            },
            hazeState = hazeState,
            ownBackdrop = detail == null || isLocalDetail,
            // Search has no large in-list header to hand the title back to —
            // the field takes that space — so its bar title is always up.
            scrolled = when {
                showSettings || showAccountScrobbling -> true
                detail != null -> detailScrolled
                else -> scrolled || selectedTab == TAB_SEARCH
            },
            refreshing = currentFeed != null && currentFeed in refreshing,
            pullFraction = { currentPull?.distanceFraction ?: 0f },
            onBack = when {
                showAccountScrobbling -> ({ showAccountScrobbling = false })
                showSettings -> ({ showSettings = false })
                detail != null -> ({ viewModel.closeDetail(); Unit })
                else -> null
            },
            modifier = Modifier.align(Alignment.TopCenter),
            actions = {
                // Only worth surfacing where there's room for it and it won't
                // be mistaken for a per-page action — Home, at rest.
                if (!showSettings && !showAccountScrobbling && detail == null && selectedTab == TAB_HOME) {
                    updateNotice?.let { update ->
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
                if (!showSettings && !showAccountScrobbling) IconButton(onClick = { showSettings = true }) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            },
        )

        // Drawn before the bars so their own glass reads on top of it; both
        // sample the same source content, so nothing is blurred twice.
        BottomFadeBlur(
            hazeState = hazeState,
            withMiniPlayer = player.song != null,
            // Not the wash: by the foot of the screen the page has finished
            // easing out of it and into this, so this is what is actually
            // under the tab bar.
            pageColor = if (isDetailVisible) detailPalette.background else MaterialTheme.colorScheme.background,
            modifier = Modifier.align(Alignment.BottomCenter),
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
                onTabSelected = { index ->
                    // Re-tapping the search tab while already on it focuses the
                    // input field and opens the keyboard rather than resetting.
                    if (index == TAB_SEARCH && selectedTab == TAB_SEARCH) {
                        searchFocusTrigger++
                        return@FloatingBottomBar
                    }
                    if (index != TAB_SEARCH) {
                        searchFocusTrigger = 0
                    }
                    viewModel.clearDetail()
                    showSettings = false
                    showAccountScrobbling = false
                    selectedTab = index
                },
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
                linksLoading = false
                val current = player.song ?: return@LaunchedEffect
                if (current.albumId != null && current.artistId != null) return@LaunchedEffect
                linksLoading = true
                links = YtMusicRepository.trackLinks(current.videoId).getOrNull()
                linksLoading = false
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
                    autoplayEnabled = autoplay,
                    signedIn = signedIn,
                    likeStatus = likeStatuses[song.videoId] ?: LikeStatus.INDIFFERENT,
                    onToggleLike = { viewModel.toggleLike(song.videoId) },
                    onToggleShuffle = { controller?.let(QueueShuffle::toggle) },
                    onCycleRepeat = {
                        controller?.let {
                            val next = when (it.repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                            // Repeat-all loops the queue as it stands; AutoPlay's
                            // tracks are the opposite of that — an endless supply
                            // of new ones — so they come back out first. Native
                            // REPEAT_MODE_ALL then wraps a plain queue exactly as
                            // it should, and the LaunchedEffect above leaves it be
                            // for as long as repeat-all stays on.
                            if (next == Player.REPEAT_MODE_ALL) it.dropAutoplayTracks()
                            it.repeatMode = next
                        }
                    },
                    onToggleAutoplay = {
                        val on = !autoplay
                        AppSettings.setAutoplay(on)
                        if (on) {
                            // Let the effect above seed a mix for the track
                            // playing now, instead of passing over it as one it
                            // has already extended.
                            autoplaySeed = null
                        } else {
                            // Switching it off takes the mix back out of the
                            // queue — what's left is what was actually asked for.
                            controller?.dropAutoplayTracks()
                        }
                    },
                    onJumpTo = { controller?.seekToDefaultPosition(it) },
                    onRemoveFromQueue = { controller?.removeMediaItem(it) },
                    onMoveInQueue = { from, to -> controller?.moveMediaItem(from, to) },
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
                        // No artwork: this track's cover isn't the artist's
                        // picture, and the page fills its own in once loaded.
                        viewModel.openDetail(id, song.artist, "Artist", null, BrowseType.ARTIST)
                    },
                    lyrics = lyrics,
                    lyricsSource = lyricsSource,
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
            // The track's cover stands in for an album's, but never for an
            // artist's picture — that page loads its own.
            val openPage: (String, String, String, BrowseType) -> Unit = { id, title, sub, type ->
                songActions = null
                showNowPlaying = false
                val art = song.thumbnailUrl.takeUnless { type == BrowseType.ARTIST }
                viewModel.openDetail(id, title, sub, art, type)
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
            ModalBottomSheet(
                onDismissRequest = { songActions = null },
                // The sheet paints itself in the track's own colours, corners
                // and drag handle included — see SongActionsSheet.
                containerColor = Color.Transparent,
                dragHandle = null,
            ) {
                SongActionsSheet(
                    song = song,
                    signedIn = signedIn,
                    likeStatus = likeStatuses[song.videoId] ?: LikeStatus.INDIFFERENT,
                    onPlayNext = { playNext(song); songActions = null },
                    onAddToQueue = { addToQueue(song); songActions = null },
                    // Stays open: the row it replaces itself with is the
                    // progress, and closing the sheet would hide the only
                    // answer to "did that work?".
                    onDownload = { startDownload(listOf(song)) },
                    // The sheet stays up for a rating: it shows the new state
                    // in place, and people often thumb a song and then queue it.
                    onToggleLike = { viewModel.toggleLike(song.videoId) },
                    onToggleDislike = { viewModel.toggleDislike(song.videoId) },
                    onAddToPlaylist = {
                        songActions = null
                        viewModel.loadPlaylists()
                        playlistTarget = song
                    },
                    onRemoveFromPlaylist = editable?.let {
                        {
                            songActions = null
                            viewModel.removeFromPlaylist(it.browseId, song)
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
                        openPage(id, song.artist, "Artist", BrowseType.ARTIST)
                    },
                    // Only the player's copy of a track is ever missing these
                    // and backfilling — a row opened from a list already has
                    // whatever ids it's ever going to have.
                    resolvingLinks = fromPlayer && linksLoading,
                    showSleepTimer = fromPlayer,
                    onShare = share.takeIf { fromPlayer },
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
                                    "Log copied · ${text.lineSequence().count()} lines",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    } else {
                        null
                    },
                )
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
            ModalBottomSheet(
                onDismissRequest = dismiss,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                PlaylistPickerSheet(
                    playlists = playlists,
                    loading = playlistsLoading,
                    song = target,
                    startCreating = target == null,
                    onPick = { playlist ->
                        target?.let { viewModel.addToPlaylist(playlist, it) }
                        dismiss()
                    },
                    onCreate = { title, privacy ->
                        viewModel.createPlaylist(title, privacy, target)
                        dismiss()
                    },
                )
            }
        }

        // ---- Playlist rename / delete (long-press on the Library tab) ----
        playlistActions?.let { playlist ->
            ModalBottomSheet(
                onDismissRequest = { playlistActions = null },
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                PlaylistActionsSheet(
                    playlist = playlist,
                    onOpen = {
                        playlistActions = null
                        viewModel.openDetail(
                            browseId = playlist.browseId,
                            title = playlist.title,
                            subtitle = playlist.subtitle,
                            thumbnailUrl = playlist.thumbnailUrl,
                            type = BrowseType.PLAYLIST,
                        )
                    },
                    onRename = { name ->
                        playlistActions = null
                        viewModel.renamePlaylist(playlist, name)
                    },
                    onDelete = {
                        playlistActions = null
                        viewModel.deletePlaylist(playlist)
                    },
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

        // ---- Update available (once per launch) ----
        if (showUpdateDialog) {
            updateNotice?.let { update ->
                UpdateAvailableDialog(
                    version = update.version,
                    hazeState = hazeState,
                    onDismiss = { showUpdateDialog = false },
                    onUpdate = {
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
                                apiKey = LastFM.FALLBACK_COMPAT_API_KEY,
                                secret = LastFM.FALLBACK_COMPAT_SECRET,
                            )
                            LastFM.getMobileSession(usernameInput.trim(), passwordInput)
                                .onSuccess { auth ->
                                    AppSettings.setLastfmSessionKey(auth.session.key)
                                    AppSettings.setLastfmUsername(auth.session.name)
                                    AppSettings.setLastfmEnabled(true)
                                    showLastfmLogin = false
                                }
                                .onFailure { e ->
                                    lastfmError = e.message ?: "Login failed"
                                }
                        } catch (e: Exception) {
                            lastfmError = e.message ?: "Login failed"
                        } finally {
                            lastfmLoading = false
                        }
                    }
                },
                onDismiss = { if (!lastfmLoading) showLastfmLogin = false },
            )
        }
    }
}

private fun tween(durationMillis: Int) =
    androidx.compose.animation.core.tween<Float>(durationMillis)

/** How many tracks a station pulls in at a time. */
private const val RADIO_BATCH = 20

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
 * A YouTube video id to seed a radio station from, for a track that may not
 * have one of its own.
 *
 * Radio, related tracks and the home feed are YouTube's alone — see
 * [SourceKind.YOUTUBE][com.music.bitchord.data.sources.SourceKind.YOUTUBE].
 * A track played from module *search* carries a
 * [SourceRegistry.trackKey] as its media id, which means nothing to
 * Innertube: handing one to [YtMusicRepository.radio] gets an empty mix
 * back, which is why AutoPlay quietly stopped extending the queue after a
 * module search result. Looking the recording up on YouTube by name gives
 * the station something it can actually seed from, and the mix that comes
 * back is YouTube's — those tracks then take the ordinary YouTube path and
 * get substituted individually if a module happens to hold them.
 *
 * Null when the track isn't on YouTube at all, which is a real answer: no
 * station rather than a station for the wrong song.
 */
private suspend fun youtubeSeedFor(song: Song): String? {
    if (SourceRegistry.parseTrackKey(song.videoId) == null) return song.videoId
    val target = TrackMatcher.targetOf(song)
    val query = TrackMatcher.queries(target).firstOrNull() ?: return null
    return YtMusicRepository.search(query, SearchFilter.SONGS)
        .getOrNull()
        ?.filterIsInstance<SearchResult.Track>()
        ?.map { it.song }
        ?.let { TrackMatcher.best(it, target) }
        ?.videoId
}

/**
 * How far a detail page scrolls before its title moves up into the bar.
 *
 * Roughly the height of the sleeve and the credit stacked above the Play pair,
 * so the two titles hand over as the header one leaves rather than sitting on
 * screen together. The bar cross-fades over 220ms, which absorbs the difference
 * between that estimate and a particular page's real header.
 */
private val DETAIL_TITLE_DROP = 320.dp

private const val TAB_HOME = 0
private const val TAB_EXPLORE = 1
private const val TAB_LIBRARY = 2
private const val TAB_SEARCH = 3
