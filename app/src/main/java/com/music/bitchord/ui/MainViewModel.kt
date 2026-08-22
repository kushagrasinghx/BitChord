package com.music.bitchord.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.AppUpdateChecker
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.PlaybackTracker
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.data.model.Account
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.SongMenu
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.data.settings.SearchHistory
import android.util.LruCache
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.music.bitchord.data.sources.SourceKind
import com.music.bitchord.data.sources.SourceRegistry
import java.util.concurrent.atomic.AtomicLong

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val authStore = AuthStore(app)

    private val _signedIn = MutableStateFlow(authStore.isSignedIn)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val _home = MutableStateFlow<UiState<List<HomeShelf>>>(UiState.Loading)
    val home: StateFlow<UiState<List<HomeShelf>>> = _home.asStateFlow()

    /**
     * Token for the next page of Home shelves; null once there's nothing
     * more. Declared here rather than by [loadMoreHome] because [init] calls
     * [loadHome] synchronously up to its first suspension point — a property
     * declared after [init] would still be null when that runs.
     */
    private var homeContinuation: String? = null

    /** Titles already on screen, so a later page can't repeat a shelf. */
    private val homeSeenTitles = mutableSetOf<String>()

    private val _homeLoadingMore = MutableStateFlow(false)
    val homeLoadingMore: StateFlow<Boolean> = _homeLoadingMore.asStateFlow()

    private val _explore = MutableStateFlow<UiState<List<HomeShelf>>>(UiState.Loading)
    val explore: StateFlow<UiState<List<HomeShelf>>> = _explore.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<UiState<List<SearchResult>>?>(null)
    val results: StateFlow<UiState<List<SearchResult>>?> = _results.asStateFlow()

    /** Songs is the default tab; there is no "All" tab any more. */
    private val _filter = MutableStateFlow(SearchFilter.SONGS)
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    // The search pipeline's own state. Declared here, above [init], because
    // that is where the collector is started from and a property declared
    // below it would still be null when it runs. See [startSearchPipeline].

    /**
     * Buffered so a keystroke is never lost, and [BufferOverflow.DROP_OLDEST]
     * so a fast typist's backlog collapses to the query they ended on rather
     * than being worked through one at a time.
     */
    private val searchRequests = MutableSharedFlow<SearchRequest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val newestRequestId = AtomicLong(0L)

    /**
     * Results of recent searches, so a query typed before is answered without
     * asking again.
     *
     * Its real work is [prefixMatch]: typing "blinding" runs through five
     * queries on the way, and each one's results are a good enough answer for
     * the next keystroke to be worth showing while the real one is in flight.
     * That is the difference between a list that refines as you type and one
     * that blanks to a spinner on every letter.
     */
    private val searchCache = LruCache<String, List<SearchResult>>(SEARCH_CACHE_ENTRIES)

    /** Synced lyrics for whatever is playing; null while unknown or absent. */
    private val _lyrics = MutableStateFlow<List<LyricLine>?>(null)
    val lyrics: StateFlow<List<LyricLine>?> = _lyrics.asStateFlow()

    /** Which of the four databases [lyrics] came from, for the panel's credit. */
    private val _lyricsSource = MutableStateFlow<LyricsSource?>(null)
    val lyricsSource: StateFlow<LyricsSource?> = _lyricsSource.asStateFlow()

    /**
     * Whether the lookup for the current track has finished. [lyrics] alone
     * can't tell "still looking" apart from "looked, found nothing" — both
     * are null — and the player needs that distinction to show "Lyrics not
     * available" only once it actually means that.
     */
    private val _lyricsChecked = MutableStateFlow(false)
    val lyricsChecked: StateFlow<Boolean> = _lyricsChecked.asStateFlow()

    private var lyricsJob: Job? = null

    /**
     * What the loaded lyrics are for. Both the track *and* the settings that
     * chose them, so switching a source on or off re-runs the lookup rather
     * than leaving the last answer sitting on a player that would now find a
     * different one.
     */
    private var lyricsFor: Pair<String, Set<LyricsSource>>? = null

    /** Called as the playing track changes; cheap no-op when already loaded. */
    fun loadLyrics(
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
    ) {
        val sources = if (AppSettings.syncedLyrics.value) {
            AppSettings.lyricsSources.value
        } else {
            emptySet()
        }
        val key = videoId to sources
        if (lyricsFor == key) return
        lyricsFor = key
        _lyrics.value = null
        _lyricsSource.value = null
        lyricsJob?.cancel()
        if (sources.isEmpty()) {
            // Switched off, or every source unticked. Nothing to look up, and
            // nothing to say about it — the player drops the lyric strip
            // rather than reporting a track with no lyrics.
            _lyricsChecked.value = true
            return
        }
        _lyricsChecked.value = false
        if (durationMs <= 0L) {
            // Duration arrives a beat after the track does; wait for it.
            lyricsFor = null
            return
        }
        lyricsJob = viewModelScope.launch {
            val found =
                LyricsRepository.lyrics(videoId, title, artist, durationMs, album, sources)
            _lyrics.value = found?.lines
            _lyricsSource.value = found?.source
            _lyricsChecked.value = true
        }
    }

    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account.asStateFlow()

    private val _library = MutableStateFlow<UiState<LibraryPage>>(UiState.Loading)
    val library: StateFlow<UiState<LibraryPage>> = _library.asStateFlow()

    /**
     * Album / artist / playlist pages, as a stack — opening an artist from an
     * album page and pressing back returns to the album, not to search.
     */
    private val _detailStack = MutableStateFlow<List<DetailPage>>(emptyList())
    val detailStack: StateFlow<List<DetailPage>> = _detailStack.asStateFlow()


    /** Set once per launch if GitHub has a release newer than this build. */
    val updateAvailable: StateFlow<AppUpdateChecker.UpdateInfo?> = AppUpdateChecker.available

    // ---- Ratings, library and playlists -------------------------------------

    /**
     * Ratings this session has set, which win over whatever the library feed
     * last said.
     *
     * Kept apart from the library rather than folded into it because the two
     * answer different questions: Liked Music is what YouTube knew when the
     * page was fetched, and this is what the user has done since. Layering
     * them ([likeStatuses]) means a tap shows immediately without the library
     * having to be re-fetched, and a later refresh can't undo it.
     */
    private val _likeOverrides = MutableStateFlow<Map<String, LikeStatus>>(emptyMap())

    /** Every rating known for this account: the library's, then this session's. */
    val likeStatuses: StateFlow<Map<String, LikeStatus>> =
        combine(_library, _likeOverrides) { library, overrides ->
            val liked = (library as? UiState.Success)?.data?.likedSongs
                ?.associate { it.videoId to LikeStatus.LIKE }
                .orEmpty()
            liked + overrides
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun likeStatusOf(videoId: String): LikeStatus =
        likeStatuses.value[videoId] ?: LikeStatus.INDIFFERENT

    /**
     * Sets (or clears) the thumbs rating on [videoId].
     *
     * Written to the screen first and rolled back if YouTube refuses. A rating
     * is a one-tap, low-stakes action taken while a song is playing; waiting
     * on a round trip before the heart fills reads as the tap not having
     * registered, and people tap again.
     */
    fun setLike(videoId: String, status: LikeStatus) {
        if (!requireSignIn()) return
        val previous = likeStatusOf(videoId)
        if (previous == status) return
        _likeOverrides.value += (videoId to status)
        viewModelScope.launch {
            YtMusicRepository.rate(videoId, status).fold(
                onSuccess = {
                    // Liked Music is now out of date either way.
                    libraryStale = true
                    if (status != LikeStatus.LIKE) dropFromLikedLists(videoId)
                    // Clearing the heart means forgetting the song, not
                    // demoting it — see [forgetFromLibrary].
                    val unliked = previous == LikeStatus.LIKE &&
                        status == LikeStatus.INDIFFERENT
                    if (unliked) forgetFromLibrary(videoId)
                },
                onFailure = {
                    _likeOverrides.value += (videoId to previous)
                },
            )
        }
    }

    /**
     * Takes an un-liked track out of the library as well, and reports whether
     * it did.
     *
     * Liking and saving are two independent flags on YouTube's side, and
     * clearing only the first leaves the song saved — still feeding the
     * Library tab's Artists shelf, still in the library feeds, with nowhere
     * left in this app to reach it and finish the job. Clearing the heart
     * reads as "forget this song", so it clears both.
     *
     * The token is fetched here rather than taken from [songMenu] because the
     * heart in the player never opens a menu, so there is often nothing
     * cached to take. One extra request, on an action nobody performs in bulk.
     * A song that was never saved has no removal token and this is a no-op.
     */
    private suspend fun forgetFromLibrary(videoId: String): Boolean {
        val menu = YtMusicRepository.songMenu(videoId).getOrNull() ?: return false
        val token = menu.removeFromLibraryToken?.takeIf { menu.inLibrary } ?: return false
        if (YtMusicRepository.setLibraryStatus(token).isFailure) return false
        // The menu may be the one on screen; don't leave it offering a
        // removal that has already happened.
        _songMenu.value = _songMenu.value?.copy(inLibrary = false)
        return true
    }

    /**
     * Takes an un-liked track out of the lists that exist *because* it was
     * liked — the Library tab's Liked Music section, and the Liked Music page
     * itself if it happens to be open.
     *
     * Marking the library stale isn't enough on its own: that only acts when
     * the tab is next opened, and un-liking is nearly always done from inside
     * one of these two lists, looking straight at the row. Leaving it there
     * reads as the tap not having worked — the menu says "Like" again while
     * the song sits in Liked Music.
     *
     * Only ever removes. A track liked from somewhere else doesn't get spliced
     * into a list that YouTube orders for itself; the next fetch places it.
     */
    private fun dropFromLikedLists(videoId: String) {
        val library = (_library.value as? UiState.Success)?.data
        if (library != null && library.likedSongs.any { it.videoId == videoId }) {
            _library.value = UiState.Success(
                library.copy(likedSongs = library.likedSongs.filterNot { it.videoId == videoId }),
            )
        }
        _detailStack.value = _detailStack.value.map { page ->
            val songs = (page.songs as? UiState.Success)?.data
            if (page.browseId != YtMusicRepository.LIKED_MUSIC || songs == null) {
                page
            } else {
                page.copy(songs = UiState.Success(songs.filterNot { it.videoId == videoId }))
            }
        }
    }

    /** The heart: liked becomes neutral, anything else becomes liked. */
    fun toggleLike(videoId: String) = setLike(
        videoId,
        if (likeStatusOf(videoId) == LikeStatus.LIKE) LikeStatus.INDIFFERENT else LikeStatus.LIKE,
    )

    /** As [toggleLike], for the thumb-down. */
    fun toggleDislike(videoId: String) = setLike(
        videoId,
        if (likeStatusOf(videoId) == LikeStatus.DISLIKE) {
            LikeStatus.INDIFFERENT
        } else {
            LikeStatus.DISLIKE
        },
    )

    /**
     * The open track menu's account state, or null while it is still being
     * fetched. Only one menu can be open at a time, so one slot is enough.
     */
    private val _songMenu = MutableStateFlow<SongMenu?>(null)
    val songMenu: StateFlow<SongMenu?> = _songMenu.asStateFlow()

    private var songMenuJob: Job? = null

    /**
     * Loads the account state behind an opening track menu — the library
     * tokens, and any rating the response happens to state.
     *
     * The rating is only ever taken when it *adds* something: a LIKE or a
     * DISLIKE the library couldn't have told us, such as a disliked track or
     * one liked past the tenth page of Liked Music. An INDIFFERENT is
     * discarded.
     *
     * That asymmetry is not fussiness. This lookup reads a watch queue, and a
     * watch queue routinely renders a liked track with no rating on it at all;
     * believing that silence downgraded songs sitting in Liked Music to
     * "not liked" a beat after their menu opened — the label changing under
     * the user, with no request sent and nothing removed.
     */
    fun loadSongMenu(videoId: String?) {
        songMenuJob?.cancel()
        _songMenu.value = null
        if (videoId == null || !_signedIn.value) return
        songMenuJob = viewModelScope.launch {
            val menu = YtMusicRepository.songMenu(videoId).getOrNull() ?: return@launch
            _songMenu.value = menu
            val stated = menu.likeStatus
            if (stated != null && stated != LikeStatus.INDIFFERENT &&
                videoId !in _likeOverrides.value
            ) {
                _likeOverrides.value += (videoId to stated)
            }
        }
    }

    /** The account's own playlists, for the picker and the library tab. */
    private val _playlists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    val playlists: StateFlow<List<UserPlaylist>> = _playlists.asStateFlow()

    private val _playlistsLoading = MutableStateFlow(false)
    val playlistsLoading: StateFlow<Boolean> = _playlistsLoading.asStateFlow()

    /** Re-fetched rather than cached for the session: playlists are edited here. */
    fun loadPlaylists() {
        if (!_signedIn.value || _playlistsLoading.value) return
        _playlistsLoading.value = true
        viewModelScope.launch {
            YtMusicRepository.userPlaylists().onSuccess { _playlists.value = it }
            _playlistsLoading.value = false
        }
    }

    /**
     * Adds [song] to a playlist.
     *
     * Not optimistic, unlike a rating: there is nothing on screen to update
     * ahead of the answer, and a "Added to X" that turns out to be untrue is
     * worse than one that arrives a moment late.
     */
    fun addToPlaylist(playlist: UserPlaylist, song: Song) {
        if (!requireSignIn()) return
        viewModelScope.launch {
            YtMusicRepository.addToPlaylist(playlist.playlistId, listOf(song.videoId)).fold(
                onSuccess = { libraryStale = true },
                onFailure = {},
            )
        }
    }

    /**
     * Creates a playlist, seeded with [song] when the flow started from a
     * track's menu — one request, so it can't half-succeed into an empty
     * playlist the user has to add to again.
     */
    fun createPlaylist(title: String, privacy: PlaylistPrivacy, song: Song? = null) {
        if (!requireSignIn()) return
        val name = title.trim().ifBlank { "New playlist" }
        viewModelScope.launch {
            YtMusicRepository.createPlaylist(
                title = name,
                privacy = privacy,
                videoIds = listOfNotNull(song?.videoId),
            ).fold(
                onSuccess = {
                    libraryStale = true
                    loadPlaylists()
                    refresh(Feed.LIBRARY)
                },
                onFailure = {},
            )
        }
    }

    /**
     * Drops [song] from the playlist page it is being read on, and takes the
     * row out from under the reader rather than waiting for a re-fetch.
     */
    fun removeFromPlaylist(browseId: String, song: Song) {
        val setVideoId = song.setVideoId ?: return
        if (!requireSignIn()) return
        val playlistId = browseId.removePrefix("VL")
        viewModelScope.launch {
            YtMusicRepository.removeFromPlaylist(
                playlistId,
                listOf(setVideoId to song.videoId),
            ).fold(
                onSuccess = {
                    libraryStale = true
                    _detailStack.value = _detailStack.value.map { page ->
                        val songs = (page.songs as? UiState.Success)?.data
                        if (page.browseId != browseId || songs == null) {
                            page
                        } else {
                            page.copy(
                                songs = UiState.Success(
                                    songs.filterNot { it.setVideoId == setVideoId },
                                ),
                            )
                        }
                    }
                },
                onFailure = {},
            )
        }
    }

    /**
     * Adds one of [DetailPage.suggestedSongs] to the playlist it was
     * suggested for, and drops it from that section — the playlist's own
     * track list only shows it correctly (with a working "remove") once the
     * page is reopened, so there is nothing on screen for it to move to yet.
     */
    fun addSuggestedSong(browseId: String, song: Song) {
        if (!requireSignIn()) return
        val playlistId = browseId.removePrefix("VL")
        viewModelScope.launch {
            YtMusicRepository.addToPlaylist(playlistId, listOf(song.videoId)).fold(
                onSuccess = {
                    libraryStale = true
                    _detailStack.value = _detailStack.value.map { page ->
                        if (page.browseId != browseId) {
                            page
                        } else {
                            page.copy(
                                suggestedSongs = page.suggestedSongs
                                    .filterNot { it.videoId == song.videoId },
                            )
                        }
                    }
                },
                onFailure = {},
            )
        }
    }

    fun renamePlaylist(playlist: UserPlaylist, title: String) {
        if (!requireSignIn()) return
        val name = title.trim()
        if (name.isBlank() || name == playlist.title) return
        viewModelScope.launch {
            YtMusicRepository.renamePlaylist(playlist.playlistId, name).fold(
                onSuccess = {
                    _playlists.value = _playlists.value.map {
                        if (it.playlistId == playlist.playlistId) it.copy(title = name) else it
                    }
                    libraryStale = true
                    refresh(Feed.LIBRARY)
                },
                onFailure = {},
            )
        }
    }

    fun deletePlaylist(playlist: UserPlaylist) {
        if (!requireSignIn()) return
        viewModelScope.launch {
            YtMusicRepository.deletePlaylist(playlist.playlistId).fold(
                onSuccess = {
                    _playlists.value = _playlists.value
                        .filterNot { it.playlistId == playlist.playlistId }
                    // Its page may be the one open; a deleted playlist has
                    // nothing left to show.
                    _detailStack.value = _detailStack.value
                        .filterNot { it.browseId == playlist.browseId }
                    libraryStale = true
                    refresh(Feed.LIBRARY)
                },
                onFailure = {},
            )
        }
    }

    /** Whether [browseId] is a playlist this account can be asked to edit. */
    fun editablePlaylist(browseId: String?): UserPlaylist? {
        if (browseId == null) return null
        return _playlists.value.firstOrNull { it.browseId == browseId }
    }

    /**
     * Guards every account write. All of them are signed-in-only, and the UI
     * hides them for guests — this is the backstop for a session that expired
     * between the menu opening and the tap.
     */
    private fun requireSignIn(): Boolean = _signedIn.value

    /**
     * Whether the library needs re-fetching. Set by every write above and
     * acted on when the tab is next opened, for the same reason [homeStale]
     * exists: rearranging a page under whoever is reading it is worse than
     * showing it a moment out of date.
     */
    private var libraryStale = false

    /** Call when the library tab becomes visible. */
    fun onLibraryShown() {
        loadPlaylists()
        if (!libraryStale) return
        libraryStale = false
        if (_library.value is UiState.Success) refresh(Feed.LIBRARY)
    }

    init {
        startSearchPipeline()
        loadHome()
        loadExplore()
        if (_signedIn.value) {
            loadLibrary()
            loadAccount()
            loadPlaylists()
        }
        viewModelScope.launch {
            // drop(1): the current value is just the count so far, not a play.
            PlaybackTracker.registeredPlays.drop(1).collect { homeStale = true }
        }
        viewModelScope.launch { AppUpdateChecker.check() }
    }

    /**
     * Whether a play has been registered since the home feed was last fetched.
     *
     * The feed leads with listening history, so it's out of date the moment a
     * track starts — but re-fetching there would rearrange the page under
     * whoever is reading it, and the tab is usually in the background anyway.
     * It's re-fetched when the tab is next opened instead.
     */
    private var homeStale = false

    /** Call when the home tab becomes visible. */
    fun onHomeShown() {
        if (!homeStale) return
        homeStale = false
        // A first load already in flight will pick the new play up by itself.
        if (_home.value is UiState.Success) refresh(Feed.HOME)
    }

    private fun loadAccount() {
        viewModelScope.launch {
            _account.value = YtMusicRepository.account().getOrNull()
        }
    }

    /**
     * A feed that can be pulled down to refresh. Tracked per feed rather than
     * as one flag: a pull on Library while Home is still refreshing in the
     * background shouldn't leave the wrong tab showing a loader.
     */
    enum class Feed { HOME, EXPLORE, LIBRARY }

    private val _refreshing = MutableStateFlow(emptySet<Feed>())
    val refreshing: StateFlow<Set<Feed>> = _refreshing.asStateFlow()

    /**
     * Re-fetches [feed] in place. Unlike the `load*` entry points this leaves
     * the current content on screen rather than dropping back to the loading
     * state — a refresh that swapped the page for a spinner would be a worse
     * experience than the stale content it replaces.
     */
    fun refresh(feed: Feed) {
        if (feed in _refreshing.value) return
        if (feed == Feed.LIBRARY && !_signedIn.value) return
        _refreshing.value = _refreshing.value + feed
        viewModelScope.launch {
            when (feed) {
                Feed.HOME -> fetchHome()
                Feed.EXPLORE -> fetchExplore()
                Feed.LIBRARY -> fetchLibrary()
            }
            _refreshing.value = _refreshing.value - feed
        }
    }

    fun loadExplore() {
        _explore.value = UiState.Loading
        viewModelScope.launch { fetchExplore() }
    }

    private suspend fun fetchExplore() {
        _explore.value = YtMusicRepository.explore().fold(
            onSuccess = { shelves ->
                if (shelves.isEmpty()) UiState.Error("Nothing to explore right now")
                else UiState.Success(shelves)
            },
            onFailure = { UiState.Error(it.friendly()) },
        )
    }

    /** Tapping a tab should leave any pushed page behind. */
    fun clearDetail() {
        if (_detailStack.value.isNotEmpty()) _detailStack.value = emptyList()
    }

    fun loadHome() {
        _home.value = UiState.Loading
        viewModelScope.launch { fetchHome() }
    }

    private suspend fun fetchHome() {
        homeContinuation = null
        homeSeenTitles.clear()
        _home.value = YtMusicRepository.home().fold(
            onSuccess = { feed ->
                homeContinuation = feed.continuation
                val shelves = feed.shelves.filter { homeSeenTitles.add(it.title.lowercase()) }
                if (shelves.isEmpty()) UiState.Error("No results from YouTube Music")
                else UiState.Success(shelves)
            },
            onFailure = { UiState.Error(it.friendly()) },
        )
    }

    /**
     * Called as the Home list nears its end. A no-op while a page is already
     * in flight, once the feed is exhausted, or before the first page has
     * loaded — [homeContinuation] covers all three by construction.
     */
    fun loadMoreHome() {
        val token = homeContinuation ?: return
        if (_homeLoadingMore.value) return
        _homeLoadingMore.value = true
        viewModelScope.launch {
            YtMusicRepository.moreHome(token).onSuccess { feed ->
                val added = feed.shelves.filter { homeSeenTitles.add(it.title.lowercase()) }
                // A page with nothing new signals the feed has looped back on
                // itself rather than run dry with a token still attached —
                // treat it the same as exhausted so scrolling can't spin here.
                homeContinuation = feed.continuation.takeIf { added.isNotEmpty() }
                if (added.isNotEmpty()) {
                    val existing = (_home.value as? UiState.Success)?.data ?: emptyList()
                    _home.value = UiState.Success(existing + added)
                }
            }
            _homeLoadingMore.value = false
        }
    }

    fun loadLibrary() {
        if (!_signedIn.value) return
        _library.value = UiState.Loading
        viewModelScope.launch { fetchLibrary() }
    }

    private suspend fun fetchLibrary() {
        _library.value = YtMusicRepository.library().fold(
            onSuccess = { page ->
                if (page.isEmpty) UiState.Error("Nothing in your library yet")
                else UiState.Success(page)
            },
            onFailure = { UiState.Error(it.friendly()) },
        )
    }

    /** Recent searches, kept on device. */
    val searchHistory: StateFlow<List<String>> = SearchHistory.recent

    fun onQueryChange(value: String) {
        _query.value = value
        runSearch()
    }

    /**
     * Commits the current query to the history. Called when the user acts on
     * what they found — submitting from the keyboard, or opening a result —
     * rather than on every keystroke, which would fill the list with the
     * prefixes typed on the way to the real query.
     */
    fun recordSearch() = SearchHistory.record(_query.value)

    /**
     * The keyboard's search/enter action. A deliberate "I'm done typing", so
     * it jumps the debounce queue rather than waiting out [SEARCH_DEBOUNCE_MS]
     * behind it — that wait exists for keystrokes, not for a user who already
     * told us they're finished.
     */
    fun submitSearch() {
        recordSearch()
        runSearch(immediate = true)
    }

    /** Re-runs a term picked out of the history, and floats it back to the top. */
    fun searchFor(term: String) {
        _query.value = term
        SearchHistory.record(term)
        runSearch(immediate = true)
    }

    fun removeSearch(term: String) = SearchHistory.remove(term)

    fun clearSearchHistory() = SearchHistory.clear()

    fun onFilterChange(value: SearchFilter) {
        if (_filter.value == value) return
        _filter.value = value
        runSearch(immediate = true)
    }

    /**
     * Every keystroke, as a request the pipeline below decides what to do with.
     *
     * [requestId] is what makes a late answer harmless: a response is only
     * written to the screen if its id is still the newest one asked for.
     * [immediate] is set for a deliberate action — submitting, tapping a
     * filter, picking a history entry — so the pipeline can skip the
     * keystroke debounce for it.
     */
    private data class SearchRequest(
        val query: String,
        val filter: SearchFilter,
        val requestId: Long,
        val immediate: Boolean = false,
    )

    private fun cacheKey(query: String, filter: SearchFilter) = "${filter.name}:$query"

    /**
     * The results of the longest earlier query this one starts with — what was
     * on screen a keystroke ago, near enough to leave up meanwhile.
     */
    private fun prefixMatch(query: String, filter: SearchFilter): List<SearchResult>? {
        val prefix = "${filter.name}:"
        return searchCache.snapshot()
            .filterKeys { it.startsWith(prefix) && query.startsWith(it.removePrefix(prefix), true) }
            .maxByOrNull { it.key.length }
            ?.value
    }

    private fun runSearch(immediate: Boolean = false) {
        val query = _query.value
        if (query.isBlank()) {
            // Nothing in flight can still be waiting to overwrite this: the
            // id it would be checked against has already moved past it.
            newestRequestId.incrementAndGet()
            _results.value = null
            return
        }
        val id = newestRequestId.incrementAndGet()
        searchRequests.tryEmit(SearchRequest(query, _filter.value, id, immediate))
    }

    /**
     * The search pipeline, started once and left running for the lifetime of
     * the view model.
     *
     * The point of it being one long-lived collector is that a keystroke no
     * longer cancels the request before it. Cancelling a call mid-flight tears
     * down its socket, and on a pooled HTTP client that is felt by whatever
     * picks that connection up next — which is how typing a word could end in
     * "Software caused connection abort" for a request that was never itself
     * in any trouble. [debounce] collapses a burst of keystrokes into one
     * query before any request is made, and [collectLatest] only abandons a
     * search once a genuinely newer one has survived that window.
     *
     * The wait is skipped entirely for [SearchRequest.immediate] requests —
     * submitting from the keyboard is the user telling us they're done typing,
     * so it shouldn't sit behind the same delay that exists to absorb the
     * keystrokes leading up to it.
     */
    @OptIn(FlowPreview::class)
    private fun startSearchPipeline() = viewModelScope.launch {
        searchRequests
            .debounce { if (it.immediate) 0L else SEARCH_DEBOUNCE_MS }
            .collectLatest { request ->
                val key = cacheKey(request.query, request.filter)
                // Something to look at immediately: the exact answer if this
                // query has been run before, otherwise the closest earlier
                // one. Only fall back to a spinner with neither.
                val exact = searchCache.get(key)
                val cached = exact ?: prefixMatch(request.query, request.filter)
                _results.value = cached?.let { UiState.Success(it) } ?: UiState.Loading
                if (exact != null) return@collectLatest

                // Search is YouTube's alone. A module is a *substitution*
                // layer, not a catalogue to browse: it never has cover art,
                // radio, related tracks or an album page, so its rows arrived
                // in the results list looking like YouTube's and then behaved
                // nothing like them. Every track found here takes the ordinary
                // YouTube path and is handed to the module at playback time —
                // see [SourceResolver.substituteForYouTube] — which upgrades
                // the ones it holds without any of them having to be a
                // separate row to pick between.
                val result = YtMusicRepository.search(request.query, request.filter)
                // A search the user has already typed past shouldn't land on
                // screen, whether it succeeded or failed.
                if (request.requestId != newestRequestId.get()) return@collectLatest
                _results.value = result.fold(
                    onSuccess = { rows -> published(rows, key) },
                    onFailure = { failure -> UiState.Error(failure.friendly()) },
                )
            }
    }

    /** Caches and publishes one result list. */
    private fun published(rows: List<SearchResult>, key: String): UiState<List<SearchResult>> {
        if (rows.isEmpty()) return UiState.Error("No results")
        searchCache.put(key, rows)
        prefetchTopResult(rows)
        return UiState.Success(rows)
    }

    /**
     * The enabled non-YouTube sources, asked at the same time and returned
     * split at YouTube's own place in the order.
     *
     * The split is what makes the Sources screen's ordering visible where it
     * matters most. A library server ranked above YouTube puts its own copies
     * at the top of the results — which is the whole point of ranking it there —
     * and one ranked below appears under them instead.
     *
     * Only the Songs filter fans out: albums, artists and playlists are
     * browse-shaped, and [MusicSource] deliberately answers for tracks only.
     */
    private suspend fun sourceResults(
        query: String,
        filter: SearchFilter,
    ): Pair<List<SearchResult>, List<SearchResult>> = coroutineScope {
        if (filter != SearchFilter.SONGS) return@coroutineScope emptyList<SearchResult>() to emptyList()
        val active = SourceRegistry.active()
        val youtubeRank = active.indexOfFirst { it.kind == SourceKind.YOUTUBE }
            .let { if (it < 0) active.size else it }

        val answers = active
            .filter { it.kind != SourceKind.YOUTUBE }
            .map { source ->
                source to async {
                    // Per-source, so one slow or unreachable server delays the
                    // results by at most this much rather than for as long as
                    // its socket takes to give up.
                    runCatching {
                        withTimeout(SOURCE_SEARCH_TIMEOUT_MS) { source.search(query, SOURCE_SEARCH_LIMIT) }
                    }.getOrDefault(emptyList())
                }
            }

        val above = mutableListOf<SearchResult>()
        val below = mutableListOf<SearchResult>()
        answers.forEach { (source, job) ->
            val rows = job.await().map { SearchResult.Track(it) }
            val rank = active.indexOfFirst { it.configId == source.configId }
            if (rank in 0 until youtubeRank) above += rows else below += rows
        }
        above to below
    }

    /**
     * Warms the stream URL for the top song result the instant results land,
     * not when it's tapped. [AudioCache] gives a head start to whatever's
     * already queued; a fresh search has nothing queued yet, and the top
     * result is overwhelmingly what gets tapped — see [play][MainActivity.play].
     * [resolveAudio][YtMusicRepository.resolveAudio] first, same as the tap
     * path itself, so a video-tagged result warms the catalogue audio's id
     * rather than one nothing will ever ask for.
     */
    private fun prefetchTopResult(rows: List<SearchResult>) {
        val song = rows.filterIsInstance<SearchResult.Track>().firstOrNull()?.song ?: return
        viewModelScope.launch {
            runCatching {
                StreamResolver.resolve(YtMusicRepository.resolveAudio(song).videoId)
            }
        }
    }

    private companion object {
        /**
         * Deliberately generous: long enough that typing a full query rarely
         * outruns it, since [SearchRequest.immediate] is the fast path for a
         * user who wants results before that — pressing search/enter on the
         * keyboard — not this timer.
         */
        const val SEARCH_DEBOUNCE_MS = 2000L

        const val SEARCH_CACHE_ENTRIES = 100

        /**
         * How long any one source gets to answer a search.
         *
         * Short on purpose: these run alongside the YouTube search, and their
         * only job is to be *there* when it lands. A home server reached over
         * a VPN that takes eight seconds has effectively not answered, and
         * holding the whole result list for it would make search feel worse
         * for the sake of results the user can still get by searching again.
         */
        const val SOURCE_SEARCH_TIMEOUT_MS = 4000L

        /** Enough to be worth scrolling, short enough not to bury YouTube's own rows. */
        const val SOURCE_SEARCH_LIMIT = 12
    }

    fun openDetail(
        browseId: String,
        title: String,
        subtitle: String = "",
        thumbnailUrl: String? = null,
        type: BrowseType = BrowseType.OTHER,
    ) {
        val resolved = typeOf(browseId, type)
        _detailStack.value += DetailPage(
            browseId = browseId,
            title = title,
            subtitle = subtitle,
            thumbnailUrl = thumbnailUrl,
            songs = UiState.Loading,
            type = resolved,
        )
        viewModelScope.launch {
            var sections = emptyList<HomeShelf>()
            // Callers that open an artist from a track — the player, the
            // long-press menu — only have that track's cover art and its full
            // credit ("A, B & C") to hand, so the page swaps in the artist's
            // own picture and name once they arrive.
            var artwork: String? = null
            var name: String? = null
            /** Set when the track list carries on past its first response. */
            var more: String? = null
            /** Tracks YouTube offers to round the playlist out — see [DetailPage.suggestedSongs]. */
            var suggested: List<Song> = emptyList()
            val state = when {
                browseId == "local:downloads" -> {
                    val context = getApplication<Application>()
                    val songs = LocalMediaRepository.getDownloadedSongs(context)
                    if (songs.isEmpty()) UiState.Error("No downloaded tracks in Music/BitChord")
                    else UiState.Success(songs)
                }
                browseId == "local:all" -> {
                    val context = getApplication<Application>()
                    if (!LocalMediaRepository.hasStoragePermission(context)) {
                        UiState.Error("Storage permission required to view local audio files")
                    } else {
                        val songs = LocalMediaRepository.getLocalMusic(context)
                        if (songs.isEmpty()) UiState.Error("No audio files found on device")
                        else UiState.Success(songs)
                    }
                }
                resolved == BrowseType.ARTIST -> {
                    YtMusicRepository.artistPage(browseId).fold(
                        onSuccess = { page ->
                            sections = page.sections
                            artwork = page.thumbnailUrl
                            name = page.name
                            if (page.songs.isEmpty()) {
                                UiState.Error("No tracks here")
                            } else {
                                UiState.Success(page.songs.withArtwork(thumbnailUrl))
                            }
                        },
                        onFailure = { UiState.Error(it.friendly()) },
                    )
                }
                else -> {
                    YtMusicRepository.browseSongs(browseId).fold(
                        onSuccess = { page ->
                            if (page.songs.isEmpty()) {
                                UiState.Error("No tracks here")
                            } else {
                                more = page.continuation
                                suggested = page.suggested.withArtwork(thumbnailUrl)
                                UiState.Success(page.songs.withArtwork(thumbnailUrl))
                            }
                        },
                        onFailure = { UiState.Error(it.friendly()) },
                    )
                }
            }
            // Update by id — the user may have pushed another page meanwhile.
            _detailStack.value = _detailStack.value.map {
                if (it.browseId == browseId && it.songs is UiState.Loading) {
                    it.copy(
                        songs = state,
                        sections = sections,
                        thumbnailUrl = artwork ?: it.thumbnailUrl,
                        title = name ?: it.title,
                        suggestedSongs = suggested,
                    )
                } else {
                    it
                }
            }
            // Only once the first page is on screen: [fillIn] appends to it,
            // and has nothing to append to before this.
            more?.let { fillIn(browseId, it, thumbnailUrl) }
        }
    }

    fun reloadLocalDetail(browseId: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val state: UiState<List<Song>> = when (browseId) {
                "local:downloads" -> {
                    val songs = LocalMediaRepository.getDownloadedSongs(context)
                    if (songs.isEmpty()) UiState.Error("No downloaded tracks in Music/BitChord")
                    else UiState.Success(songs)
                }
                "local:all" -> {
                    if (!LocalMediaRepository.hasStoragePermission(context)) {
                        UiState.Error("Storage permission required to view local audio files")
                    } else {
                        val songs = LocalMediaRepository.getLocalMusic(context)
                        if (songs.isEmpty()) UiState.Error("No audio files found on device")
                        else UiState.Success(songs)
                    }
                }
                else -> return@launch
            }
            _detailStack.value = _detailStack.value.map {
                if (it.browseId == browseId) {
                    it.copy(songs = state)
                } else it
            }
        }
    }

    /**
     * Follows a detail page's continuations in the background, appending each
     * page to what is already being read.
     *
     * A playlist of a few hundred tracks is several round trips, and taking
     * them before showing anything meant a spinner for all of them. Growing
     * the list underneath the reader is also what makes it safe to keep
     * following continuations [YtMusicRepository.MAX_PAGES] deep — nobody is
     * waiting on the last one.
     *
     * Stops the moment the page leaves the stack: there is no one to append
     * for.
     */
    private fun fillIn(browseId: String, token: String, artworkFallback: String?) {
        viewModelScope.launch {
            var next: String? = token
            var page = 1
            while (next != null && page++ < YtMusicRepository.MAX_PAGES) {
                val fetched = YtMusicRepository.moreSongs(next).getOrNull() ?: return@launch
                val stack = _detailStack.value
                val index = stack.indexOfFirst { it.browseId == browseId }
                if (index < 0) return@launch
                val current = stack[index]
                val existing = (current.songs as? UiState.Success)?.data ?: return@launch
                val known = existing.mapTo(HashSet()) { it.videoId }
                val added = fetched.songs
                    .filter { known.add(it.videoId) }
                    .withArtwork(artworkFallback)
                // Suggestions can arrive on a later page than the real
                // tracks, once the playlist's own continuation runs dry —
                // see parsePlaylistShelf — so they're tracked separately
                // rather than folded into [known].
                val knownSuggested = current.suggestedSongs.mapTo(HashSet()) { it.videoId }
                val addedSuggested = fetched.suggested
                    .filter { it.videoId !in known && knownSuggested.add(it.videoId) }
                    .withArtwork(artworkFallback)
                // A page with nothing new on it means the feed has looped back
                // rather than run dry with a token still attached.
                if (added.isEmpty() && addedSuggested.isEmpty()) return@launch
                _detailStack.value = stack.toMutableList().also {
                    it[index] = current.copy(
                        songs = UiState.Success(existing + added),
                        suggestedSongs = current.suggestedSongs + addedSuggested,
                    )
                }
                next = fetched.continuation
            }
        }
    }

    /**
     * An album's track listing doesn't repeat the cover on every row — the
     * page carries it once — so rows arrive with no artwork and stay blank
     * through to the queue and the notification. Fall back to the page's.
     */
    private fun List<Song>.withArtwork(fallback: String?): List<Song> {
        if (fallback == null) return this
        return map { if (it.thumbnailUrl == null) it.copy(thumbnailUrl = fallback) else it }
    }

    /**
     * Home and Explore cards don't say what they point at, and an artist
     * fetched as an album only yields the five songs on its landing page.
     * YouTube's browse ids are prefixed by kind, so use that.
     */
    private fun typeOf(browseId: String, fallback: BrowseType): BrowseType = when {
        browseId.startsWith("UC") -> BrowseType.ARTIST
        browseId.startsWith("MPREb") -> BrowseType.ALBUM
        browseId.startsWith("VL") || browseId.startsWith("PL") -> BrowseType.PLAYLIST
        else -> fallback
    }

    /** Pops one page; returns false when there was nothing to pop. */
    fun closeDetail(): Boolean {
        val stack = _detailStack.value
        if (stack.isEmpty()) return false
        _detailStack.value = stack.dropLast(1)
        return true
    }

    fun onSignedIn(cookie: String) {
        authStore.cookie = cookie
        Innertube.cookie = cookie
        _signedIn.value = true
        loadHome()
        loadLibrary()
        loadAccount()
        loadPlaylists()
    }

    fun signOut() {
        authStore.signOut()
        Innertube.cookie = null
        _signedIn.value = false
        _account.value = null
        _library.value = UiState.Loading
        // Ratings and playlists belong to the account that just left; keeping
        // them would show the next signed-in user someone else's hearts.
        _likeOverrides.value = emptyMap()
        _playlists.value = emptyList()
        _songMenu.value = null
        loadHome()
    }

    private fun Throwable.friendly(): String = when {
        message?.contains("resolve host", true) == true ||
            message?.contains("Unable to resolve", true) == true -> "No internet connection"
        message?.contains("401") == true || message?.contains("403") == true ->
            "YouTube Music rejected the request — try signing in again"
        else -> message ?: "Something went wrong"
    }
}
