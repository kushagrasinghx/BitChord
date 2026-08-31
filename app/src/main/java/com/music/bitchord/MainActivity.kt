package com.music.bitchord

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.music.bitchord.auth.DiscordLoginScreen
import com.music.bitchord.auth.YtMusicLoginScreen
import com.music.bitchord.data.AppUpdateChecker
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.screens.AccountAndScrobblingScreen
import com.music.bitchord.ui.screens.DiscordDialog
import com.music.bitchord.ui.screens.DiscordDialogHost
import com.music.bitchord.ui.screens.DiscordScreen
import com.music.bitchord.ui.screens.HistoryScreen
import com.music.bitchord.ui.screens.SettingsScreen
import com.music.bitchord.ui.screens.SourcesScreen
import com.music.bitchord.ui.screens.SpotifyCanvasAuthScreen
import com.music.bitchord.playback.LinkRequest
import com.music.bitchord.playback.MusicLink
import com.music.bitchord.playback.PlayerDeepLink
import com.music.bitchord.playback.QueueBuilder
import com.music.bitchord.playback.QueueShuffle
import com.music.bitchord.playback.autoplaySectionStart
import com.music.bitchord.playback.playSongs
import com.music.bitchord.playback.toMediaItem
import com.music.bitchord.playback.toggleAutoplay
import com.music.bitchord.download.DownloadSession
import com.music.bitchord.download.DownloadStore
import com.music.bitchord.download.DownloadTarget
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.components.BrowseActionsSheet
import com.music.bitchord.ui.components.BrowseTarget
import com.music.bitchord.ui.components.DownloadManagerSheet
import com.music.bitchord.ui.components.PlaylistPickerSheet
import com.music.bitchord.ui.components.SongActionsSheet
import com.music.bitchord.playback.rememberMediaController
import com.music.bitchord.playback.rememberPlayerState
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.components.BottomFadeScrim
import com.music.bitchord.ui.components.BottomTab
import com.music.bitchord.ui.components.FLOATING_BAR_MAX_WIDTH
import com.music.bitchord.ui.components.FloatingBottomBar
import com.music.bitchord.ui.components.FrostedTopBar
import com.music.bitchord.ui.components.LastfmLoginAlert
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.ui.components.ListenBrainzTokenAlert
import com.music.bitchord.ui.components.TextValueAlert
import com.music.bitchord.ui.components.MiniPlayer
import com.music.bitchord.ui.components.TopBarAccountButton
import com.music.bitchord.ui.components.TopBarDownloadButton
import com.music.bitchord.ui.components.TopFadeBlur
import com.music.bitchord.ui.components.topBarContentPadding
import com.music.bitchord.ui.components.AppLanguageDialog
import com.music.bitchord.ui.components.LyricsSourcesDialog
import com.music.bitchord.ui.components.UpdateAvailableDialog
import com.music.bitchord.ui.icons.BitChordIcons
import androidx.media3.common.Player
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.ui.player.NowPlayingScreen
import com.music.bitchord.ui.player.dockedPlayerAvailable
import com.music.bitchord.ui.player.dockedPlayerWidth
import com.music.bitchord.ui.screens.DetailScreen
import com.music.bitchord.ui.screens.LocalMusicScreen
import com.music.bitchord.ui.screens.HomeScreen
import com.music.bitchord.ui.screens.LibraryGridPage
import com.music.bitchord.ui.screens.LibraryScreen
import com.music.bitchord.ui.screens.SearchScreen
import com.music.bitchord.ui.replay.ReplayScreen
import com.music.bitchord.ui.replay.cards
import com.music.bitchord.ui.replay.ReplayShareSheet
import com.music.bitchord.ui.replay.ReplayStories
import com.music.bitchord.ui.replay.ReplayStoryPage
import com.music.bitchord.ui.replay.rememberReplayState
import com.music.bitchord.ui.theme.BitChordTheme
import com.music.bitchord.ui.theme.rememberArtworkPalette
import com.music.bitchord.ui.theme.SystemBarIcons
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Before the composition, so a cold launch from a widget's artwork has
        // the request already standing by the time BitChordApp first reads it.
        PlayerDeepLink.consume(intent)
        // Likewise for a link tapped or shared from another app — see [MusicLink].
        MusicLink.consume(intent)
        setContent {
            val theme by AppSettings.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            BitChordTheme(darkTheme = darkTheme) {
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
                    BitChordApp(darkTheme = darkTheme, windowWidth = maxWidth)
                }
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
        MusicLink.consume(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BitChordApp(
    darkTheme: Boolean,
    /** The width of the window this is laid out in — see the call site. */
    windowWidth: Dp,
    viewModel: MainViewModel = viewModel(),
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val hazeState = remember { HazeState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    // Whether there is room to keep the player open beside the page rather than
    // raising it over one. Read all over what follows, because most of what the
    // page does about the player is really about which of the two it is: no mini
    // player standing in for one that is already there, no sheet to raise, and
    // the bottom inset the mini player was holding handed back to the page.
    val playerDocked = dockedPlayerAvailable(windowWidth)
    /**
     * Whether the player's *sheet* is up.
     *
     * Only ever set where there is a sheet to set it for. Docked, the player is
     * open whatever this says, and the things that read it — the light status
     * bar glyphs the artwork needs, the sheet itself — are all asking the one
     * question this used to answer on its own: is the player covering the page?
     */
    var showNowPlaying by remember { mutableStateOf(false) }
    // The far end of the relay from a widget's artwork. Cleared here rather than
    // where it was set, so the request is spent by being served — see
    // [PlayerDeepLink.handled]. The sheet itself is gated on there being a track,
    // so on a cold launch this simply arms it and it opens as the controller
    // connects. Docked there is nothing to raise: the player is already up, and
    // the tap has been honoured by the time it arrives.
    val openPlayerRequested by PlayerDeepLink.pending.collectAsStateWithLifecycle()
    LaunchedEffect(openPlayerRequested) {
        if (openPlayerRequested) {
            if (!playerDocked) showNowPlaying = true
            PlayerDeepLink.handled()
        }
    }
    var showLogin by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    // Replay: the page, the stories over it, and the share sheet over those.
    // Three states rather than one enum because they stack — the stories are
    // opened from the page and the share sheet from either, and closing one
    // has to reveal what it was opened from.
    var showReplay by remember { mutableStateOf(false) }
    var replayStory by remember { mutableStateOf<ReplayStoryPage?>(null) }
    var showReplayShare by remember { mutableStateOf(false) }
    /** Which story card the share sheet is for, or null for the whole Replay. */
    var replaySharePage by remember { mutableStateOf<ReplayStoryPage?>(null) }
    var showAccountScrobbling by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    var showSpotifyCanvasAuth by remember { mutableStateOf(false) }
    
    // Hosted here rather than inside SourcesScreen so its scrim covers the tab
    // bar and mini player, like every other alert in the app.
    var customModuleAlert by remember { mutableStateOf(false) }
    var customModuleInput by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    // A Library shelf's "Show all" — the shelf it was opened from, so its own
    // cards can be laid out again as a full-screen grid. See [LibraryGridPage].
    var libraryShowAll by remember { mutableStateOf<HomeShelf?>(null) }
    var showLyricsSources by remember { mutableStateOf(false) }
    var showAppLanguage by remember { mutableStateOf(false) }
    var showListenBrainzLogin by remember { mutableStateOf(false) }
    var showLastfmLogin by remember { mutableStateOf(false) }
    /**
     * Whether the download manager is open.
     *
     * Not [rememberSaveable]: the list behind it does not survive the process
     * either (see [com.music.bitchord.download.DownloadSession]), and a sheet
     * restored over an empty one would be a manager with nothing to manage.
     */
    var showDownloadManager by remember { mutableStateOf(false) }
    // Discord Rich Presence: its own page under Account & integrations, its own
    // full-screen sign-in, and one slot for whichever of its alerts is open.
    // The alerts live out here rather than on the page because their scrim has
    // to cover the tab bar and mini player, which are drawn after it.
    var showDiscord by remember { mutableStateOf(false) }
    var showDiscordLogin by remember { mutableStateOf(false) }
    var discordDialog by remember { mutableStateOf<DiscordDialog?>(null) }
    var songActions by remember { mutableStateOf<Song?>(null) }
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
    /** Holding a row anywhere but the player — the menu without the player's rows. */
    val openSongMenu: (Song) -> Unit = { song ->
        menuFromPlayer = false
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
    // Which album or playlist the collection menu is open on, or null when it
    // is shut. One slot for every surface that can open it — the shelves on
    // three tabs, the search rows, the artist page's carousels, the release
    // page's own overflow — because only one of them can be held at a time.
    var browseActions by remember { mutableStateOf<BrowseTarget?>(null) }
    val autoplay by AppSettings.autoplay.collectAsStateWithLifecycle()
    val listenBrainzToken by AppSettings.listenBrainzToken.collectAsStateWithLifecycle()
    // Incremented each time the search tab is re-tapped while already selected,
    // which SearchScreen uses as a signal to focus the input field.
    var searchFocusTrigger by remember { mutableIntStateOf(0) }

    // The player fills the screen with dark artwork whichever theme is on, so
    // it keeps light glyphs; every other surface follows the theme. Replay's
    // page and stories are the same case — dark artwork either way.
    SystemBarIcons(dark = !darkTheme && !showNowPlaying && !showReplay && replayStory == null)

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
    val historyState by viewModel.history.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val lyricsSource by viewModel.lyricsSource.collectAsStateWithLifecycle()
    val lyricsChecked by viewModel.lyricsChecked.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val searchSuggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val detailStack by viewModel.detailStack.collectAsStateWithLifecycle()
    val detail = detailStack.lastOrNull()
    // Local Music has no artwork to wash the bar in, so it renders with a
    // plain status bar rather than the artwork-driven blur other detail
    // pages (album/artist/playlist) get. Downloads is the same page, and the
    // tab row it now carries sits directly under the bar, so it needs the same
    // treatment — an artwork blur over it would tint the tabs.
    //
    // A downloaded playlist's page is under `local:` too and is none of that: it
    // has a cover and a track list, so it takes the bar every other release page
    // takes. Hence the folder question rather than the prefix.
    val isLocalDetail = detail?.browseId.isDeviceFolder()
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

    // The Downloads page is a snapshot of the folder, taken when it was opened.
    // Saving a track or deleting one while it is on screen changes what belongs
    // on it — and now that the page groups by artist and album, a stale list is
    // stale counts and a missing row in three places rather than one. So it is
    // taken again whenever the record of what's on disk changes.
    val savedDownloads by Downloads.saved.collectAsStateWithLifecycle()
    // The releases those files were asked for as — read here rather than in the
    // page so the Downloads folder recomposes when one is added, the same way it
    // does when a file is.
    val savedCollections by Downloads.collections.collectAsStateWithLifecycle()
    // The playlists among them, for the Library page's On Device shelf. Read off
    // both records: the collection record is what says a playlist was downloaded
    // as a playlist, and what is on disk is what says it still has anything left
    // to open.
    val downloadedPlaylists = remember(savedCollections, savedDownloads) {
        Downloads.savedPlaylists()
    }
    // What a browse id is recorded under in Downloads.collections, when it names
    // a release downloaded whole — see BrowseTarget.downloadId. A downloaded
    // playlist's own page and its card both carry the id under the
    // `local:playlist:` prefix; a release still reachable by its real id (an
    // album's own page, a search hit) is looked up directly under that instead.
    val downloadIdFor: (String?) -> String? = { id ->
        id?.let { Downloads.recordIdOf(it) ?: it }?.takeIf { it in savedCollections }
    }
    LaunchedEffect(savedDownloads, savedCollections, detail?.browseId) {
        val open = detail?.browseId ?: return@LaunchedEffect
        // A downloaded playlist's page is a snapshot of the same folder and goes
        // stale for the same reasons — and it is the one page a delete can empty
        // out entirely, which is worth saying rather than leaving rows behind
        // that play nothing.
        if (open == "local:downloads" || Downloads.recordIdOf(open) != null) {
            viewModel.reloadLocalDetail(open)
        }
    }

    val controller = rememberMediaController()
    val player = rememberPlayerState(controller)
    val shuffleEnabled by QueueShuffle.enabled.collectAsStateWithLifecycle()

    // Lyrics follow whatever is playing; duration lands a beat after the track.
    // Keyed on the lyric settings too, so turning a source on or off applies to
    // the track already playing rather than only the next one.
    val syncedLyricsEnabled by AppSettings.syncedLyrics.collectAsStateWithLifecycle()
    val lyricsSources by AppSettings.lyricsSources.collectAsStateWithLifecycle()
    LaunchedEffect(player.song?.videoId, player.durationMs, syncedLyricsEnabled, lyricsSources) {
        player.song?.let {
            viewModel.loadLyrics(
                it.videoId,
                it.title,
                it.artist,
                player.durationMs,
                it.albumName,
                it.localUri,
            )
        }
    }

    val homeListState = rememberLazyListState()
    val exploreListState = rememberLazyListState()
    val libraryListState = rememberLazyListState()
    val historyListState = rememberLazyListState()
    val libraryShowAllGridState = rememberLazyGridState()
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
    // As [detailListState], for Replay: its own large heading owns the title
    // until it is scrolled away, and the bar lives out here rather than on the
    // page. Rebuilt per opening so reopening starts at the top.
    val replayListState = rememberLazyListState()
    val replayScrolled by remember(replayListState) {
        derivedStateOf {
            replayListState.firstVisibleItemIndex > 0 ||
                replayListState.firstVisibleItemScrollOffset > 24
        }
    }

    val detailListState = remember(detail?.browseId) { LazyListState() }
    val detailTitleDrop = with(LocalDensity.current) { DETAIL_TITLE_DROP.toPx() }
    val detailScrolled by remember(detailListState, detailTitleDrop) {
        derivedStateOf {
            detailListState.firstVisibleItemIndex > 0 ||
                detailListState.firstVisibleItemScrollOffset > detailTitleDrop
        }
    }

    val tabs = listOf(
        BottomTab(stringResource(R.string.play), BitChordIcons.Play),
        BottomTab(stringResource(R.string.explore), BitChordIcons.Explore),
        BottomTab(stringResource(R.string.library), BitChordIcons.Library),
        BottomTab(stringResource(R.string.search), BitChordIcons.Search),
    )

    val scope = rememberCoroutineScope()

    val play: (List<Song>, Int) -> Unit = { songs, index ->
        scope.launch {
            val starting = YtMusicRepository.resolveAudio(songs[index])
            val queued = songs.toMutableList().also { it[index] = starting }
            controller?.playSongs(queued, index)
            // Nothing to raise where the player is already open beside the page.
            if (!playerDocked) showNowPlaying = true
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
        scope.launch {
            val resolved = YtMusicRepository.resolveAudio(song)
            controller?.playSongs(listOf(resolved), 0)
            if (!playerDocked) showNowPlaying = true
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
            val credit = subtitle ?: if (type == BrowseType.ARTIST) "Artist" else "Album"
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
                Toast.makeText(context, "Couldn't find $name", Toast.LENGTH_SHORT).show()
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
                val c = controller
                if (c == null || c.mediaItemCount == 0) {
                    // Nothing to queue behind. "Add to queue" on a silent
                    // player can only mean start here — and adding without
                    // preparing would leave the list sitting in a player that
                    // never gets round to it.
                    play(songs, 0)
                } else {
                    val at = if (next) {
                        (c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount)
                    } else {
                        c.autoplaySectionStart()
                    }
                    c.addMediaItems(at, songs.map { it.toMediaItem() })
                    val count = "${songs.size} song" + if (songs.size == 1) "" else "s"
                    val message = if (next) "$count will play next" else "Added $count to queue"
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    // Video uploads are swapped for their catalogue audio
                    // release behind the queue rather than in front of it, for
                    // the same reason [play] does it: a hundred rows' worth of
                    // lookups is a wait, and none of them is the track playing
                    // now. Found by id rather than by index — the queue can be
                    // edited while these are in flight.
                    songs.forEach { song ->
                        if (!song.isVideo) return@forEach
                        launch {
                            val resolved = YtMusicRepository.resolveAudio(song)
                            if (resolved.videoId == song.videoId) return@launch
                            // The session can go away while a lookup is out —
                            // the activity is recreated, the service is stopped —
                            // and a released controller has no queue to patch.
                            if (!c.isConnected) return@launch
                            val index = (0 until c.mediaItemCount)
                                .firstOrNull { c.getMediaItemAt(it).mediaId == song.videoId }
                                ?: return@launch
                            c.replaceMediaItem(index, resolved.toMediaItem())
                        }
                    }
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
                    Toast.makeText(context, "Couldn't open that link", Toast.LENGTH_SHORT).show()
                } else {
                    // A link is one song named on purpose, which is exactly the
                    // case [playRadio] exists for: play it and let AutoPlay
                    // carry on, rather than queueing something around it.
                    playRadio(song)
                }
            }
            is LinkRequest.Page -> {
                showNowPlaying = false
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
                    playRadio(top)
                } else {
                    // Either the link was a search to look at, or "play X"
                    // found nothing to start — and the results are a better
                    // answer to a spoken request than silence is.
                    showNowPlaying = false
                    selectedTab = TAB_SEARCH
                    viewModel.searchFor(request.query)
                }
            }
            // "Play music", nothing named. The queue from last time is already
            // restored by the time the controller connects (see LastPlayed), so
            // this is the resume it sounds like. On a fresh install there is
            // nothing to resume and the app has just opened on Home, which is
            // as much as the request can honestly be given.
            LinkRequest.Resume -> if (session.mediaItemCount > 0) session.play()
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
            browseActions = BrowseTarget(
                browseId = id,
                title = item.title,
                subtitle = item.subtitle,
                thumbnailUrl = item.thumbnailUrl,
                type = type ?: BrowseType.OTHER,
                downloadId = downloadIdFor(id),
            )
        }
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
                if (target.type == BrowseType.ALBUM) {
                    songs.map { it.copy(albumName = it.albumName ?: target.title) }
                } else {
                    songs
                },
            )
        }
        when {
            target.songs.isNotEmpty() -> stamp(target.songs)
            target.browseId == null ->
                Toast.makeText(context, "No tracks here", Toast.LENGTH_SHORT).show()
            else -> viewModel.collectSongs(target.browseId, target.thumbnailUrl) { result ->
                result.fold(
                    onSuccess = stamp,
                    onFailure = {
                        val message = it.message ?: "Couldn't load these tracks"
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
    // Shared by the Library tab itself and by a shelf's "Show all" page, so a
    // card opens the same way from either.
    val onLibraryItemClick: (ShelfItem) -> Unit = { item ->
        item.browseId?.let { id ->
            if (id == "local:all" && !LocalMediaRepository.hasStoragePermission(context)) {
                val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_AUDIO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
                mediaPermissionLauncher.launch(perm)
            }
            // Left set rather than cleared: a card opened from a shelf's
            // "Show all" page stacks a detail page over it exactly as one
            // opened from the Library tab stacks over that, so back from the
            // release lands on the grid rather than skipping past it. Every
            // place that reads `libraryShowAll` alongside `detail` favours
            // `detail` while both are set — see the AnimatedContent below.
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
        val songs = requested.filter { it.videoId !in saved }
        // Asked here as well as inside [Downloads.enqueue] — not instead of it.
        // Enqueue is the invariant and has to refuse whoever calls it, including
        // the storage-permission continuation below, which resumes long enough
        // after this check for the connection to have changed under it. This is
        // the one place that knows the tap was for forty tracks and can say so
        // once, rather than leaving forty identical failed rows to be read.
        val blocked = songs.isNotEmpty() && !AppSettings.downloadsAllowedNow
        if (songs.isNotEmpty() && !blocked) {
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
                downloadPendingFrom = from
                storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                songs.forEach { Downloads.enqueue(context, it, from?.title) }
                if (from != null) Downloads.markRequested(from.id, songs.map { it.videoId })
            }
        }
        // Recorded for everything that was asked for, not just what still has to
        // be fetched: a release whose tracks are already on the device is still
        // that release, and the point of the record is to group them. Skipped
        // only when the whole batch was refused, since then there will be
        // nothing on disk for it to group.
        if (from != null && !blocked) {
            Downloads.rememberCollection(from, requested)
        }
        when {
            // The row's own icon reports a queued download, so a single tap
            // normally needs no toast — but a refused one leaves the row exactly
            // as it was, and a button that visibly does nothing is worse than a
            // long message. So this one is said whatever the count.
            blocked -> Toast.makeText(
                context,
                "${Downloads.WIFI_ONLY_REFUSAL} — turn that off in Settings to use mobile data",
                Toast.LENGTH_LONG,
            ).show()
            requested.size > 1 -> {
                val message = if (songs.isEmpty()) {
                    "Already downloaded"
                } else {
                    "Downloading ${songs.size} song" + if (songs.size == 1) "" else "s"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
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
        bottom = if (player.song != null && !playerDocked) 210.dp else 140.dp,
    )

    // What colour the page currently under the bars is. The fades either end
    // of the screen are flat colour wherever their blur has least to say, so
    // handing them the theme's background puts a black band on a page that is
    // washed in an artwork's colour instead. Off a detail page this resolves
    // to the theme's background anyway, which is exactly right there.
    val detailPalette = rememberArtworkPalette(detail?.thumbnailUrl)

    // One set of numbers for the cards, the page, the stories and the shared
    // picture, so they cannot disagree. Read while any of them is on screen —
    // which includes the Library tab, since the cards live at the top of it.
    // See [rememberReplayState].
    val replayOpen = showReplay || replayStory != null || showReplayShare ||
        (selectedTab == TAB_LIBRARY && detail == null && !showSettings)
    val (replay, setReplayPeriod) = rememberReplayState(replayOpen)
    val replayCards = remember(replay.summary) {
        replay.summary?.takeUnless { it.isEmpty }?.cards().orEmpty()
    }

    // ---- The track in the player ----
    // Whatever started this track knew its title and its artwork, but rarely
    // which album or artist page it belongs to. Fill that in while the player
    // is actually up: on a tablet that is from the moment the track starts,
    // since the pane never goes down; on a phone it is when the sheet is
    // raised, so playing an album from the mini player still costs nothing.
    val playerShowing = playerDocked || showNowPlaying
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

    // The player's whole parameter list, in one place because there are two
    // places it can be mounted: the sheet a phone raises over the page, and
    // the pane a tablet keeps beside it. [docked] is the only difference
    // between the two, and only ever one of them is in the tree.
    val nowPlaying: @Composable (Song, Boolean) -> Unit = { song, docked ->
        NowPlayingScreen(
            song = song,
            windowWidth = windowWidth,
            isPlaying = player.isPlaying,
            isLoading = player.isLoading,
            positionMs = player.position.positionMs,
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
            onJumpTo = { controller?.seekToDefaultPosition(it) },
            onRemoveFromQueue = { controller?.removeMediaItem(it) },
            onMoveInQueue = { from, to -> controller?.moveMediaItem(from, to) },
            // The enriched copy, not player.song — otherwise the menu
            // hides the album and artist rows even once their browse
            // ids have been resolved.
            onOpenMenu = {
                menuFromPlayer = true
                songActions = song
            },
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
            docked = docked,
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

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // A pushed album/artist/playlist page replaces the tab content but
        // leaves the tab bar and mini player in place.
        // Replay's three layers unwind in the order they were opened. Ahead of
        // every other handler because they are drawn over everything else.
        BackHandler(enabled = showReplayShare) { showReplayShare = false }
        BackHandler(enabled = replayStory != null && !showReplayShare) { replayStory = null }
        BackHandler(
            enabled = showReplay && !showSettings && replayStory == null && !showReplayShare,
        ) {
            showReplay = false
        }
        BackHandler(
            enabled = detail != null && !showSettings && !showAccountScrobbling && !showSources &&
                !showReplay,
        ) { viewModel.closeDetail() }
        BackHandler(enabled = showDiscord) {
            showDiscord = false
        }
        BackHandler(enabled = showAccountScrobbling && !showDiscord) {
            showAccountScrobbling = false
        }
        BackHandler(enabled = showSources) {
            showSources = false
        }
        // One back step out of Settings, or out of any tab but Home, lands on
        // Home rather than exiting — only Home itself hands back to the system,
        // which is what actually closes/minimizes the app.
        BackHandler(enabled = showSettings && !showAccountScrobbling && !showSources) {
            showSettings = false
            // Only when Settings was the whole of what was on screen. Opened
            // over Replay or over a release page, closing it reveals that again
            // rather than throwing both away.
            if (detail == null && !showReplay) selectedTab = TAB_HOME
        }
        BackHandler(
            enabled = detail == null && !showSettings && !showAccountScrobbling &&
                !showSources && !showReplay && selectedTab != TAB_HOME,
        ) {
            selectedTab = TAB_HOME
        }
        BackHandler(enabled = showUpdateDialog) { showUpdateDialog = false }
        BackHandler(enabled = showListenBrainzLogin) { showListenBrainzLogin = false }
        BackHandler(enabled = showLastfmLogin) { showLastfmLogin = false }
        BackHandler(enabled = discordDialog != null) { discordDialog = null }
        BackHandler(enabled = customModuleAlert) { customModuleAlert = false }
        BackHandler(enabled = showHistory) { showHistory = false }
        // Disabled while a detail page is open over the grid: that one's own
        // BackHandler below has to close first, or back would skip past it
        // straight to Library. See [onLibraryItemClick].
        BackHandler(enabled = libraryShowAll != null && detail == null) { libraryShowAll = null }

        // On a tablet the page and the player stand side by side rather than
        // one over the other: everything a phone stacks in a single column —
        // the feed, the frosted bars, the tab row — becomes the left half of
        // a row, and the player is the right. Off a tablet the row has the
        // one child it always had and changes nothing.
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                AnimatedContent(
                    targetState = when {
                        showDiscord -> "discord"
                        showHistory -> "history"
                        // `&& detail == null`: a card opened from the grid
                        // stacks a detail page over it exactly as one opened
                        // from the Library tab does — see
                        // [onLibraryItemClick] — so with both set this must
                        // give way to the `detail != null` branch below it
                        // rather than keep showing the grid underneath.
                        libraryShowAll != null && detail == null -> "library_show_all"
                        showAccountScrobbling -> "account_scrobbling"
                        showSources -> "sources"
                        // Above Replay, not below it. The top bar's account
                        // button sets `showSettings` from every page including
                        // this one, so with Replay winning the tie the button
                        // was live, hit, and changed nothing on screen.
                        showSettings -> "settings"
                        showReplay -> "replay"
                        detail != null -> detail.browseId
                        else -> "$TAB_KEY$selectedTab"
                    },
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
                        val tabSwap = initialState.startsWith(TAB_KEY) && targetState.startsWith(TAB_KEY)
                        val libraryTabKey = "$TAB_KEY$TAB_LIBRARY"
                        val libraryShowAllSwap = (initialState == "library_show_all" && targetState == libraryTabKey) ||
                            (targetState == "library_show_all" && initialState == libraryTabKey)
                        if (tabSwap || libraryShowAllSwap) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            fadeIn(tween(180)) togetherWith fadeOut(tween(180))
                        }
                    },
                    modifier = Modifier.hazeSource(hazeState),
                    label = "content",
                ) { key ->
                    // Every branch below reads `key` rather than the state that
                    // produced it. The two are the same thing only for the page
                    // being entered: the one on its way out is still composed,
                    // and asking it what is selected *now* has it redraw itself
                    // as its own replacement — which then fades out from under
                    // the identical copy fading in behind it.
                    val live = detailStack.lastOrNull()?.takeIf {
                        it.browseId == key && key != "settings" && key != "account_scrobbling" &&
                            key != "discord" && key != "replay" && key != "history" &&
                            key != "library_show_all"
                    }
                    // Held for the same reason, one step further on: a popped
                    // page is off the stack before it has finished animating
                    // out, so `live` goes null under it and it would spend its
                    // exit drawing whatever is underneath instead of itself.
                    // Per slot, since each is remembered against its own key.
                    val held = remember(key) { mutableStateOf(live) }
                    if (live != null) held.value = live
                    val page = held.value
                    if (key == "history") {
                        HistoryScreen(
                            state = historyState,
                            listState = historyListState,
                            onSongClick = play,
                            onSongLongPress = { songActions = it },
                            onSongSwipe = onSongSwipe,
                            onRetry = viewModel::loadHistory,
                            contentPadding = listPadding,
                        )
                    } else if (key == "library_show_all") {
                        libraryShowAll?.let { shelf ->
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
                    } else if (key == "replay") {
                        ReplayScreen(
                            state = replay,
                            holder = account?.name.orEmpty(),
                            onPeriodChange = setReplayPeriod,
                            onOpenStory = { replayStory = it },
                            // A track tapped on a chart is one the user already
                            // knows they like, so it starts a station off itself
                            // rather than queueing the chart it was on — the
                            // same reading [playRadio] makes of a search hit.
                            onPlaySong = playRadio,
                            onOpenArtist = { id, name ->
                                showReplay = false
                                openByName(id, name, null, BrowseType.ARTIST)
                            },
                            onOpenAlbum = { id, title, artist, art ->
                                showReplay = false
                                openByName(id, title, artist, BrowseType.ALBUM, art)
                            },
                            onShare = {
                                replaySharePage = null
                                showReplayShare = true
                            },
                            contentPadding = listPadding,
                            listState = replayListState,
                        )
                    } else if (key == "discord") {
                        DiscordScreen(
                            song = player.song,
                            positionMs = player.position.positionMs,
                            durationMs = player.durationMs,
                            onOpenLogin = { showDiscordLogin = true },
                            onOpenDialog = { discordDialog = it },
                            contentPadding = listPadding,
                        )
                    } else if (key == "account_scrobbling") {
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
                            onOpenDiscord = { showDiscord = true },
                            contentPadding = listPadding,
                        )
                    } else if (key == "sources") {
                        SourcesScreen(
                            contentPadding = listPadding,
                            onEditCustomModule = {
                                customModuleInput = SourceRegistry.customModule()?.baseUrl.orEmpty()
                                customModuleAlert = true
                            },
                        )
                    } else if (key == "settings") {
                        SettingsScreen(
                            windowWidth = windowWidth,
                            signedIn = signedIn,
                            account = account,
                            onSignIn = {
                                showSettings = false
                                showLogin = true
                            },
                            onSignOut = { viewModel.signOut() },
                            onAccountScrobbling = { showAccountScrobbling = true },
                            onOpenReplay = {
                                showSettings = false
                                showReplay = true
                            },
                            onLyricsSources = { showLyricsSources = true },
                            onSources = { showSources = true },
                            onSpotifyCanvasAuth = { showSpotifyCanvasAuth = true },
                            onAppLanguage = { showAppLanguage = true },
                            contentPadding = listPadding,
                        )
                    } else if (page != null && page.browseId.isDeviceFolder()) {
                        // Local Music and Downloads — both the tabbed Songs / Artists /
                        // Albums view. Two folders of tracks already on the device, so
                        // there is nothing to tell them apart on screen beyond what is
                        // in them and what to say when that is nothing.
                        //
                        // A single downloaded playlist is not one of these: it has one
                        // running order and nothing to tab through, so it falls to the
                        // release page below.
                        val localState = page.songs
                        val localSongs = (localState as? com.music.bitchord.data.model.UiState.Success)
                            ?.data.orEmpty()
                        // Only the Downloads folder has releases behind it: Local
                        // Music is files this app never asked for, so there is
                        // nothing on record about how they were grouped. Keyed on
                        // the record as well as the list, so downloading an album
                        // while its folder is open adds the folder rather than
                        // waiting for the page to be reopened.
                        val downloadCollections = remember(localSongs, savedCollections) {
                            if (page.browseId == "local:downloads") {
                                Downloads.collectionsAmong(localSongs)
                            } else {
                                emptyList()
                            }
                        }
                        LocalMusicScreen(
                            songs = localSongs,
                            collections = downloadCollections,
                            onSongClick = play,
                            onSongLongPress = openSongMenu,
                            onSongSwipe = onSongSwipe,
                            onShuffle = { songs ->
                                QueueShuffle.enableForNextQueue()
                                play(songs, songs.indices.random())
                            },
                            emptyMessage = (localState as? com.music.bitchord.data.model.UiState.Error)
                                ?.message,
                            // An album or artist here is a grouping of rows rather than
                            // a page, so the menu is handed the rows themselves — there
                            // is no id anything could be fetched with.
                            onCollectionLongPress = { label, grouped ->
                                // An artist grouping is never one of these — only a
                                // release downloaded whole has a record to match,
                                // which is exactly the distinction `asked` draws in
                                // `albumEntries`.
                                val downloadId = downloadCollections.firstOrNull {
                                    it.title == label && it.songs == grouped
                                }?.id
                                browseActions = BrowseTarget(
                                    browseId = null,
                                    title = label,
                                    subtitle = grouped.firstOrNull()?.artist.orEmpty()
                                        .takeUnless { it == label }
                                        .orEmpty(),
                                    thumbnailUrl = grouped.firstOrNull()?.thumbnailUrl,
                                    songs = grouped,
                                    downloadId = downloadId,
                                )
                            },
                            contentPadding = listPadding,
                        )
                    } else if (page != null) {
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
                        DetailScreen(
                            page = page,
                            listState = detailListState,
                            onSongClick = play,
                            onSongLongPress = { openSongMenu(withAlbum(it)) },
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
                            onSectionItemLongPress = onBrowseLongPress,
                            // The page's own tracks, so the sheet has them already and
                            // Play, Shuffle and Open are the buttons beside the one that
                            // opened it rather than rows on it. Download is the other
                            // way round: the header no longer carries it, so the sheet
                            // is where a whole release is asked for — and the tracks
                            // arrive stamped with the album they came off, which is what
                            // the download record groups them under.
                            onMore = { songs ->
                                browseActions = BrowseTarget(
                                    browseId = page.browseId,
                                    title = page.title,
                                    subtitle = page.subtitle,
                                    thumbnailUrl = page.thumbnailUrl,
                                    type = page.type,
                                    songs = songs.map(withAlbum),
                                    fromCard = false,
                                    downloadId = downloadIdFor(page.browseId),
                                )
                            },
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
                            contentPadding = listPadding,
                        )
                    } else when (key.removePrefix(TAB_KEY).toIntOrNull() ?: selectedTab) {
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
                                            // The card's own subtitle is billed
                                            // as "Song • Chelsea Wolfe"; only
                                            // the credit belongs in the field
                                            // the player, mini player and
                                            // everything downstream read.
                                            artist = InnertubeParser.artistFromSubtitle(item.subtitle),
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
                            onItemLongPress = onBrowseLongPress,
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
                                            // The card's own subtitle is billed
                                            // as "Song • Chelsea Wolfe"; only
                                            // the credit belongs in the field
                                            // the player, mini player and
                                            // everything downstream read.
                                            artist = InnertubeParser.artistFromSubtitle(item.subtitle),
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
                            onItemLongPress = onBrowseLongPress,
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
                            onSongLongPress = openSongMenu,
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
                            onBrowseLongPress = { item ->
                                // A search row does say what it is, so its own type is
                                // better than what the browse id can be read to mean.
                                if (item.type != BrowseType.ARTIST) {
                                    browseActions = BrowseTarget(
                                        browseId = item.browseId,
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        thumbnailUrl = item.thumbnailUrl,
                                        type = item.type,
                                        downloadId = downloadIdFor(item.browseId),
                                    )
                                }
                            },
                            history = searchHistory,
                            suggestions = searchSuggestions,
                            onSubmit = viewModel::submitSearch,
                            // A suggestion and a recent search are the same act — a
                            // term picked out of a list rather than typed — so they run
                            // through the same path and both land in the history.
                            onSuggestionClick = viewModel::searchFor,
                            onHistoryClick = viewModel::searchFor,
                            onHistoryRemove = viewModel::removeSearch,
                            onHistoryClear = viewModel::clearSearchHistory,
                            contentPadding = listPadding,
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
                            onShowAll = { shelf -> libraryShowAll = shelf },
                            replayCard = replayCards.firstOrNull(),
                            onOpenReplay = { showReplay = true },
                            onSignIn = { showLogin = true },
                            onRetry = viewModel::loadLibrary,
                            refreshing = MainViewModel.Feed.LIBRARY in refreshing,
                            onRefresh = { viewModel.refresh(MainViewModel.Feed.LIBRARY) },
                            pullState = libraryPull,
                            contentPadding = listPadding,
                            downloadedPlaylists = downloadedPlaylists,
                        )
                    }
                }

                // Every top bar is a fade rather than a pane — see [TopFadeBlur].
                // Drawn before the bar so the bar's own content sits on top of it.
                val isDetailVisible = detail != null && !isLocalDetail && !showSettings &&
                    !showAccountScrobbling && !showSources && !showReplay
                TopFadeBlur(
                    hazeState = hazeState,
                    // Replay paints its own full-bleed black backdrop up under the
                    // status bar, exactly as a release page's artwork does.
                    pageColor = when {
                        showReplay -> Color.Black
                        isDetailVisible -> detailPalette.wash
                        else -> MaterialTheme.colorScheme.background
                    },
                    scrimColor = when {
                        showReplay -> Color.Black
                        isDetailVisible -> detailPalette.background
                        else -> MaterialTheme.colorScheme.background
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                FrostedTopBar(
                    title = when {
                        showDiscord -> "Discord"
                        showHistory -> "History"
                        libraryShowAll != null && detail == null -> libraryShowAll?.title.orEmpty()
                        showAccountScrobbling -> "Account & scrobbling"
                        showSources -> "Sources"
                        showSettings -> "Settings"
                        showReplay -> "Replay"
                        detail != null -> detail.title
                        else -> tabs[selectedTab].let {
                            if (it.label == "Play") "Listen Now" else it.label
                        }
                    },
                    // Search has no large in-list header to hand the title back to —
                    // the field takes that space — so its bar title is always up.
                    scrolled = when {
                        showSettings || showAccountScrobbling || showSources || showDiscord || showHistory ||
                            (libraryShowAll != null && detail == null) -> true
                        // The page leads with its own large "Replay", so the bar
                        // stays out of the way until that has been scrolled off.
                        showReplay -> replayScrolled
                        detail != null -> detailScrolled
                        else -> scrolled || selectedTab == TAB_SEARCH
                    },
                    refreshing = currentFeed != null && currentFeed in refreshing,
                    pullFraction = { currentPull?.distanceFraction ?: 0f },
                    onBack = when {
                        showDiscord -> ({ showDiscord = false })
                        showHistory -> ({ showHistory = false })
                        libraryShowAll != null && detail == null -> ({ libraryShowAll = null })
                        showAccountScrobbling -> ({ showAccountScrobbling = false })
                        showSources -> ({ showSources = false })
                        showSettings -> ({ showSettings = false })
                        showReplay -> ({ showReplay = false })
                        detail != null -> ({ viewModel.closeDetail(); Unit })
                        else -> null
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                    actions = {
                        // Only worth surfacing where there's room for it and it won't
                        // be mistaken for a per-page action — Home, at rest.
                        if (!showSettings && !showAccountScrobbling && !showSources && detail == null && selectedTab == TAB_HOME) {
                            updateNotice?.let { update ->
                                IconButton(onClick = { showUpdateDialog = true }) {
                                    Icon(
                                        Icons.Rounded.SystemUpdate,
                                        contentDescription = "Update available: v${update.version}",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        if (!showSettings && !showAccountScrobbling) {
                            // Left of the account photo, and only on Library itself:
                            // a history is a record of what was played, which reads
                            // as that tab's business rather than every tab's.
                            if (!showHistory && !showReplay && !showDiscord && libraryShowAll == null &&
                                detail == null && selectedTab == TAB_LIBRARY
                            ) {
                                IconButton(
                                    onClick = {
                                        showHistory = true
                                        viewModel.loadHistory()
                                    },
                                ) {
                                    Icon(
                                        Icons.Rounded.History,
                                        contentDescription = "Listening history",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            // Left of the account photo, and only there while
                            // there is a batch to report on — see
                            // [TopBarDownloadButton], which decides that for
                            // itself rather than being told.
                            TopBarDownloadButton(onClick = { showDownloadManager = true })
                            TopBarAccountButton(
                                account = account,
                                onClick = { showSettings = true },
                            )
                        }
                    },
                )

                // Drawn before the bars so their own glass reads on top of it.
                BottomFadeScrim(
                    withMiniPlayer = player.song != null && !playerDocked,
                    // Not the wash: by the foot of the screen the page has finished
                    // easing out of it and into this, so this is what is actually
                    // under the tab bar.
                    pageColor = if (isDetailVisible) detailPalette.background else MaterialTheme.colorScheme.background,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // Capped and centred rather than run to the page's edges
                        // — see [FLOATING_BAR_MAX_WIDTH]. It sits on the Column
                        // rather than on each bar so the two are held to the same
                        // width and keep the shared left and right edge they have
                        // on a phone. Before fillMaxWidth, so the fill has
                        // already been bounded by the time it is applied.
                        .widthIn(max = FLOATING_BAR_MAX_WIDTH)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Only where the player isn't already open beside the page:
                    // a bar whose whole job is to stand in for the player, next
                    // to the player, is a second copy of what is already there.
                    player.song?.takeUnless { playerDocked }?.let { song ->
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
                        hazeState = hazeState,
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
                            showReplay = false
                            showHistory = false
                            libraryShowAll = null
                            selectedTab = index
                        },
                    )
                }
            }

            // The player, open for as long as the app is. There is no way to
            // put it away and nothing to put it away for — the pane is its
            // own space rather than something borrowed from the page.
            if (playerDocked) {
                DockedPlayer(
                    song = playerSong,
                    width = dockedPlayerWidth(windowWidth),
                    content = { current -> nowPlaying(current, true) },
                )
            }
        }

        // ---- Now Playing ----
        // Only raised where it isn't already open beside the page.
        if (!playerDocked && showNowPlaying && playerSong != null) {
            ModalBottomSheet(
                onDismissRequest = { showNowPlaying = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                // The player fills the screen and paints its own background to
                // the very top, so the sheet's default 28.dp top corners would
                // only cut two notches out of the artwork behind the status bar.
                shape = RectangleShape,
                containerColor = Color.Transparent,
                dragHandle = null,
                contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            ) {
                nowPlaying(playerSong, false)
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

        // ---- Album / playlist detail ----
        // ---- Long-press track actions ----
        songActions?.let { song ->
            // Set by whoever opened it — see [menuFromPlayer]. It cannot be
            // read off the player's own visibility any more, because on a
            // tablet the player is visible whatever the menu was opened from.
            val fromPlayer = menuFromPlayer
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
                    onDownload = { downloadSong(song) },
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
                    // Hidden outright when there's no real YouTube id behind
                    // this row to build a link from — SongActionsSheet already
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

        // ---- Download manager ----
        // The batch view of what the top-bar indicator is counting. Dismissing
        // it is what marks the batch seen, and marking it on the way *out*
        // rather than on the way in is deliberate: it is the outcome the user is
        // signing off on, and while the sheet is up there may not be one yet.
        if (showDownloadManager) {
            val closeDownloadManager = {
                showDownloadManager = false
                DownloadSession.markSeen()
            }
            BackHandler(onBack = closeDownloadManager)
            ModalBottomSheet(
                onDismissRequest = closeDownloadManager,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                DownloadManagerSheet(onDismiss = closeDownloadManager)
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

        // ---- Album / playlist actions ----
        // Opened by holding a card on any tab, or from the release page's own
        // overflow. What a track's long-press menu is to one song, this is to
        // the whole release — the queue rows above all.
        browseActions?.let { target ->
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
            val pinnedPlaylists by AppSettings.pinnedPlaylists.collectAsStateWithLifecycle()
            val pinnableId = target.browseId?.takeIf { target.type == BrowseType.PLAYLIST }
            ModalBottomSheet(
                onDismissRequest = { browseActions = null },
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                BrowseActionsSheet(
                    // The live answer, not the one the target was built with.
                    target = target.copy(playlist = playlist),
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
                    }.takeIf { remote },
                    isPinned = pinnableId != null && pinnableId in pinnedPlaylists,
                    onTogglePin = pinnableId?.let { id ->
                        {
                            val nowPinned = AppSettings.togglePinnedPlaylist(id)
                            if (!nowPinned && id !in pinnedPlaylists) {
                                Toast.makeText(
                                    context,
                                    "Only ${AppSettings.MAX_PINNED_PLAYLISTS} playlists can be pinned",
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
                    },
                    onDelete = playlist?.let { p ->
                        {
                            browseActions = null
                            viewModel.deletePlaylist(p)
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

        if (showAppLanguage) {
            BackHandler { showAppLanguage = false }
            AppLanguageDialog(
                hazeState = hazeState,
                onDismiss = { showAppLanguage = false },
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
                                contentDescription = "Close",
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

        if (customModuleAlert) {
            val existing = SourceRegistry.customModule()
            TextValueAlert(
                hazeState = hazeState,
                title = "Custom module",
                message = "A compatible module index, tried ahead of the built-in one. " +
                    "Only one at a time — saving replaces the current one.",
                placeholder = "Module index URL",
                value = customModuleInput,
                onValueChange = { customModuleInput = it },
                saveEnabled = customModuleInput.isNotBlank(),
                onSave = {
                    SourceRegistry.setCustomModule(customModuleInput)
                    customModuleAlert = false
                },
                onRemove = if (existing != null) {
                    {
                        SourceRegistry.setCustomModule("")
                        customModuleAlert = false
                    }
                } else {
                    null
                },
                onDismiss = { customModuleAlert = false },
            )
        }
    }
}

private fun tween(durationMillis: Int) =
    androidx.compose.animation.core.tween<Float>(durationMillis)

/**
 * Whether this page id is one of the two device folders — `local:downloads` and
 * `local:all`, the tabbed Songs / Artists / Albums view.
 *
 * Asked rather than `startsWith("local:")` because that prefix now covers two
 * unlike pages: a folder, and one downloaded playlist, which is a plain track
 * listing under its own cover and wants the same chrome every other release page
 * gets. See [Downloads.PLAYLIST_PREFIX] for why they share a namespace at all.
 */
private fun String?.isDeviceFolder(): Boolean =
    this != null && startsWith("local:") && !startsWith(Downloads.PLAYLIST_PREFIX)

/** `M:SS`/`H:MM:SS`, the same shape [String?.durationMillis] parses back. */
private fun formatDurationText(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(Locale.ROOT, minutes, seconds)
}

/**
 * The pane a wide window keeps the player in, down the right-hand edge.
 *
 * It is a fixed [width] rather than a share of the row because the player has a
 * width it wants and a page does not: past a point the sleeve and the transport
 * stop being improved by more room and the feed beside them still is, so the
 * pane takes what it needs and the page has the rest — see [dockedPlayerWidth].
 *
 * The pane is there whether or not anything is playing. A player that appears
 * and disappears would take a third of the page's width with it every time
 * something started or stopped, which is the layout jumping under the finger
 * rather than the app reacting to it; so with nothing to show it says so.
 */
@Composable
private fun DockedPlayer(
    song: Song?,
    width: Dp,
    content: @Composable (Song) -> Unit,
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (song != null) {
            content(song)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = BitChordIcons.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(44.dp),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Nothing playing",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Pick something and it turns up here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        // The status bar runs across both panes and its glyphs can only be one
        // colour, and that colour follows the page: in a light theme they are
        // dark ink, which over a plain surface is a clock nobody can read. Only
        // painted for the empty state, where the pane really is flat
        // [colorScheme.surface] behind the placeholder copy.
        //
        // A song mounts [NowPlayingScreen] instead, and that already runs its
        // own backdrop — the mesh gradient, and the hero banner's artwork —
        // up behind the inset, with its own scrim once the banner settles (see
        // its [heroT] scrim). Painting flat over that here was covering the
        // player's own backdrop with a solid rectangle every frame, which is
        // the black bar across the top of a playing dock: the artwork stopped
        // at this box instead of running to the edge like it does on a phone.
        if (song == null) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(MaterialTheme.colorScheme.background),
            )
        }
    }
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

private const val TAB_HOME = 0
private const val TAB_EXPLORE = 1
private const val TAB_LIBRARY = 2
private const val TAB_SEARCH = 3

/**
 * What a tab's key is prefixed with in the content switcher above.
 *
 * The index is read back off it there rather than off `selectedTab`, so the
 * prefix has to be the one thing both the writing and the reading agree on.
 */
private const val TAB_KEY = "tab:"
