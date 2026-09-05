package com.music.bitchord.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.R
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.auth.GoogleAccountSession
import com.music.bitchord.auth.YouTubeProfile
import com.music.bitchord.auth.profileId
import com.music.bitchord.auth.sessionId
import com.music.bitchord.auth.adjacentProfile
import com.music.bitchord.data.AppUpdateChecker
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.lyrics.EmbeddedLyrics
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsArtifactFormat
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.PlaybackTracker
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.auth.CapturedSession
import com.music.bitchord.auth.WebSessionMode
import com.music.bitchord.data.model.Account
import com.music.bitchord.data.model.AccountChannel
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.LibraryState
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.SongMenu
import com.music.bitchord.data.model.SubscriptionState
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.data.settings.SearchHistory
import com.music.bitchord.download.Downloads
import com.music.bitchord.download.LyricsSidecarStore
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.music.bitchord.data.sources.SourceKind
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.data.sources.SourceResolver
import com.music.bitchord.data.sources.TrackMatcher
import com.music.bitchord.playback.StreamChoice
import java.util.concurrent.atomic.AtomicLong
import java.util.Locale

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

    /**
     * Requests still filling out the first Play page. The core feed and each
     * supplement browse run in parallel and publish as they land, so the list
     * goes Success while several shelves are still on the wire — counted here
     * so the feed can say more is coming instead of just ending mid-load.
     */
    private val _homePendingShelves = MutableStateFlow(0)

    /** True whenever shelves are still due at the end of the feed, from either source. */
    val homeLoadingMore: StateFlow<Boolean> =
        combine(_homeLoadingMore, _homePendingShelves) { paging, pending -> paging || pending > 0 }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _homeRecentlyPlayedLoading = MutableStateFlow(false)
    val homeRecentlyPlayedLoading: StateFlow<Boolean> = _homeRecentlyPlayedLoading.asStateFlow()
    private val homeLoadGeneration = AtomicLong(0L)

    private val _explore = MutableStateFlow<UiState<List<MoodGenreSection>>>(UiState.Loading)
    val explore: StateFlow<UiState<List<MoodGenreSection>>> = _explore.asStateFlow()

    private val _selectedMoodGenre = MutableStateFlow<MoodGenre?>(null)
    val selectedMoodGenre: StateFlow<MoodGenre?> = _selectedMoodGenre.asStateFlow()

    private val _moodGenreShelves = MutableStateFlow<UiState<List<HomeShelf>>>(UiState.Loading)
    val moodGenreShelves: StateFlow<UiState<List<HomeShelf>>> = _moodGenreShelves.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<UiState<List<SearchResult>>?>(null)
    val results: StateFlow<UiState<List<SearchResult>>?> = _results.asStateFlow()

    /** The mixed YouTube Music result page is the fast, useful default. */
    private val _filter = MutableStateFlow(SearchFilter.ALL)
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    private val _searchLoadingMore = MutableStateFlow(false)
    val searchLoadingMore: StateFlow<Boolean> = _searchLoadingMore.asStateFlow()

    /** Increments once per first-page request so the UI can reset its list. */
    private val _searchScrollReset = MutableStateFlow(0)
    val searchScrollReset: StateFlow<Int> = _searchScrollReset.asStateFlow()

    /**
     * What the search page offers while a query is being typed, led by the
     * query itself.
     *
     * Non-empty *is* the signal that the field is mid-edit, so the screen
     * needs no second flag: these rows are shown in place of the results
     * whenever there are any, and cleared the moment a search is actually run
     * — see [submitSearch], [searchFor].
     *
     * Element 0 is always the raw text as typed. It's put there by the
     * keystroke itself rather than taken from the response, so the row the
     * thumb is already heading for is correct before the network answers, and
     * stays correct if it never does — YouTube's list never contains the
     * half-typed text, only completions of it.
     */
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    // The search pipeline's own state. Declared here, above [init], because
    // that is where the collector is started from and a property declared
    // below it would still be null when it runs. See [startSearchPipeline].

    /**
     * Buffered so an emission is never lost to a collector that happens to be
     * mid-search, and [BufferOverflow.DROP_OLDEST] because when two arrive
     * together the later one is the one meant.
     */
    private val searchRequests = MutableSharedFlow<SearchRequest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * The same arrangement as [searchRequests], for the typeahead — where the
     * drop policy earns its keep rather than just being safe: this one really
     * does take a keystroke each, and a fast typist's backlog should collapse
     * to the prefix they ended on instead of being worked through a letter at
     * a time.
     */
    private val suggestRequests = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val newestRequestId = AtomicLong(0L)

    /**
     * Results of recent searches, so a query searched before is answered
     * without asking again. That covers the two ways a query is repeated most:
     * a filter tab, which re-runs the same text against a different tab and
     * then usually goes back, and a term tapped out of the recent searches.
     *
     * Its other half is [prefixMatch], which is what the typeahead makes worth
     * keeping: picking "coldplay yellow" off a list is normally preceded by
     * having searched "coldplay", and those results are close enough to leave
     * up for the moment the narrower one takes rather than blanking the page
     * to a spinner.
     */
    private data class SearchCacheEntry(
        val rows: List<SearchResult>,
        val continuation: String?,
    )

    private data class SearchSession(
        val key: String,
        val requestId: Long,
        val filter: SearchFilter,
        val continuation: String?,
    )

    private val searchCache = LruCache<String, SearchCacheEntry>(SEARCH_CACHE_ENTRIES)
    private var searchSession: SearchSession? = null

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
    private var lyricsFor: Triple<String, Set<LyricsSource>, String?>? = null

    /**
     * Called as the playing track changes; cheap no-op when already loaded.
     *
     * [localUri] is the file this track plays from when it is on the device,
     * and it is tried before the network: a downloaded track had its lyrics
     * fetched once already and written into its own file (see `LyricsTag`), so
     * asking the same servers again is a round trip to arrive at a string that
     * is on disk — and one that fails outright with the connection off, which
     * is what made a downloaded song show nothing offline.
     */
    fun loadLyrics(
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
        localUri: String? = null,
        localLyricsUri: String? = null,
        localLyricsSource: String? = null,
        localLyricsFormat: String? = null,
    ) {
        val sources = if (AppSettings.syncedLyrics.value) {
            AppSettings.lyricsSources.value
        } else {
            emptySet()
        }
        val key = Triple(videoId, sources, localLyricsUri ?: localUri)
        if (lyricsFor == key) return
        lyricsFor = key
        _lyrics.value = null
        _lyricsSource.value = null
        lyricsJob?.cancel()
        if (sources.isEmpty() && localLyricsUri.isNullOrBlank() && localUri.isNullOrBlank()) {
            // Switched off, or every source unticked. Nothing to look up, and
            // nothing to say about it — the player drops the lyric strip
            // rather than reporting a track with no lyrics.
            _lyricsChecked.value = true
            return
        }
        _lyricsChecked.value = false
        lyricsJob = viewModelScope.launch {
            // 1. Check for local sidecar lyrics (.lrc / .ttml)
            if (!localLyricsUri.isNullOrBlank() && !localLyricsFormat.isNullOrBlank()) {
                val localSidecar = withContext(Dispatchers.IO) {
                    val content = LyricsSidecarStore.read(
                        getApplication(),
                        Uri.parse(localLyricsUri),
                    )
                    val format = runCatching {
                        LyricsArtifactFormat.valueOf(localLyricsFormat)
                    }.getOrNull()
                    val source = runCatching {
                        localLyricsSource?.let(LyricsSource::valueOf)
                    }.getOrNull()
                    if (content != null && format != null) {
                        LyricsRepository.offline(content, format, source ?: LyricsSource.LRCLIB)
                    } else {
                        null
                    }
                }
                if (localSidecar != null) {
                    _lyrics.value = localSidecar.lines
                    _lyricsSource.value = localSidecar.source
                    _lyricsChecked.value = true
                    return@launch
                }
            }

            // 2. The file first, and without the duration gate below: a length is
            // only needed to *match* a track against a stranger's database, and
            // nothing is being matched here — these lyrics were written into
            // this exact file, for this exact recording.
            if (localUri != null) {
                EmbeddedLyrics.forUri(getApplication(), localUri)?.let { embedded ->
                    _lyrics.value = embedded
                    // No source to name: what the file records is the lyrics,
                    // not which of the eight services they came from months ago.
                    _lyricsSource.value = null
                    _lyricsChecked.value = true
                    return@launch
                }
            }

            if (durationMs <= 0L) {
                // Duration arrives a beat after the track does; wait for it.
                lyricsFor = null
                return@launch
            }
            val found = LyricsRepository.lyrics(
                videoId = videoId,
                title = title,
                artist = artist,
                durationMs = durationMs,
                album = album,
                sources = sources,
                order = AppSettings.lyricsSourceOrder.value,
                prioritizeSyllableSync = AppSettings.prioritizeSyllableSync.value,
            )
            _lyrics.value = found?.lines
            _lyricsSource.value = found?.source
            _lyricsChecked.value = true
        }
    }

    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account.asStateFlow()

    /**
     * The channels this login can act as. Empty until the picker asks for
     * them — it is one more request per sign-in and nothing else on the
     * settings page needs the answer.
     */
    private val _channels = MutableStateFlow<List<AccountChannel>>(emptyList())
    val channels: StateFlow<List<AccountChannel>> = _channels.asStateFlow()

    private val _channelsLoading = MutableStateFlow(false)
    val channelsLoading: StateFlow<Boolean> = _channelsLoading.asStateFlow()

    /**
     * The chosen channel's [AccountChannel.key], or null while the app is
     * acting as whichever channel YouTube Music serves by default.
     */
    private val _selectedChannelKey = MutableStateFlow(
        authStore.activeSession?.activeProfileId ?: authStore.channelPageId ?: authStore.channelDataSyncId,
    )
    val selectedChannelKey: StateFlow<String?> = _selectedChannelKey.asStateFlow()

    private val _selectedChannelName = MutableStateFlow(
        authStore.activeSession?.profiles?.firstOrNull { it.profileId == authStore.activeProfileId }?.name
            ?: authStore.channelName,
    )
    val selectedChannelName: StateFlow<String?> = _selectedChannelName.asStateFlow()

    /** Source of truth for Google sessions and their YouTube identities. */
    private val _googleAccounts = MutableStateFlow(authStore.sessions)
    val googleAccounts: StateFlow<List<GoogleAccountSession>> = _googleAccounts.asStateFlow()
    private val _activeAccountId = MutableStateFlow(authStore.activeSession?.accountId)
    val activeAccountId: StateFlow<String?> = _activeAccountId.asStateFlow()
    private val _activeProfileId = MutableStateFlow(authStore.activeProfileId)
    val activeProfileId: StateFlow<String?> = _activeProfileId.asStateFlow()

    private val _history = MutableStateFlow<UiState<List<Song>>>(UiState.Loading)
    val history: StateFlow<UiState<List<Song>>> = _history.asStateFlow()

    private val _library = MutableStateFlow<UiState<LibraryPage>>(UiState.Loading)
    val library: StateFlow<UiState<LibraryPage>> = _library.asStateFlow()

    /** In-memory cache is partitioned by account and profile; it is never shared. */
    private data class ListenerSnapshot(
        val account: Account?, val library: UiState<LibraryPage>,
        val history: UiState<List<Song>>, val playlists: List<UserPlaylist>,
        val owned: Map<String, Boolean>,
    )
    private val listenerCache = mutableMapOf<String, ListenerSnapshot>()

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
    /** Every rating known for this account: the library's, then this session's. */
    val likeStatuses: StateFlow<Map<String, LikeStatus>> =
        combine(_library, LikeState.overrides) { library, overrides ->
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
        LikeState.set(videoId, status)
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
                    LikeState.set(videoId, previous)
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
     * Saves the album or playlist [browseId] to the library, or takes it out.
     *
     * Written to the screen first and rolled back if YouTube refuses, for the
     * same reason [setLike] is: it is one tap on a page the user is looking at,
     * and a control that waits on a round trip before it changes reads as a tap
     * that missed.
     *
     * A page with no [DetailPage.library] is one YouTube never offered to save
     * — a local page, an auto-playlist, a generated mix — and the UI has no
     * control on it to have been tapped, so this is a no-op rather than a guess.
     */
    fun toggleLibrary(browseId: String) {
        if (!requireSignIn()) return
        val current = _detailStack.value.firstOrNull { it.browseId == browseId }?.library ?: return
        val target = !current.saved
        setSavedOnPage(browseId, target)
        viewModelScope.launch {
            if (YtMusicRepository.setSaved(current.playlistId, target).isSuccess) {
                // The Library tab's Albums/Playlists shelf is now out of date.
                libraryStale = true
            } else {
                setSavedOnPage(browseId, current.saved)
            }
        }
    }

    /**
     * Subscribes to the artist page [browseId]'s channel, or unsubscribes.
     *
     * The release equivalent is [toggleLibrary], and this behaves the same way:
     * optimistic, rolled back on refusal, and a no-op on a page whose header
     * never offered a subscribe button — a signed-out response among them.
     */
    fun toggleSubscription(browseId: String) {
        if (!requireSignIn()) return
        val current = _detailStack.value
            .firstOrNull { it.browseId == browseId }?.subscription ?: return
        val target = !current.subscribed
        setSubscribedOnPage(browseId, target)
        viewModelScope.launch {
            if (YtMusicRepository.setSubscribed(current.channelId, target).isSuccess) {
                // The Library tab's Subscriptions shelf is now out of date.
                libraryStale = true
            } else {
                setSubscribedOnPage(browseId, current.subscribed)
            }
        }
    }

    /** As [setSavedOnPage], for the artist header's subscribe button. */
    private fun setSubscribedOnPage(browseId: String, subscribed: Boolean) {
        _detailStack.value = _detailStack.value.map { page ->
            val subscription = page.subscription
            if (page.browseId != browseId || subscription == null) {
                page
            } else {
                page.copy(subscription = subscription.copy(subscribed = subscribed))
            }
        }
    }

    /**
     * Restates whether a page is saved. By id rather than by index: the user may
     * have pushed or popped pages while the write was in flight.
     */
    private fun setSavedOnPage(browseId: String, saved: Boolean) {
        _detailStack.value = _detailStack.value.map { page ->
            val library = page.library
            if (page.browseId != browseId || library == null) {
                page
            } else {
                page.copy(library = library.copy(saved = saved))
            }
        }
    }

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
                videoId !in LikeState.overrides.value
            ) {
                LikeState.set(videoId, stated)
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
        val identity = listenerKey()
        _playlistsLoading.value = true
        viewModelScope.launch {
            YtMusicRepository.userPlaylists().onSuccess { if (identity == listenerKey()) _playlists.value = it }
            if (identity == listenerKey()) _playlistsLoading.value = false
        }
    }

    /**
     * The library feed's Playlists shelf, rewritten by [edit].
     *
     * Every playlist edit has to do this by hand, because the library tab reads
     * `_library` and nothing else — [playlists] is the picker's list, not the
     * tab's — so a rename that only updated that list left the card on screen
     * still bearing the old name.
     *
     * Re-fetching instead is what this replaces, and it did not work: YouTube's
     * `FEmusic_liked_playlists` is eventually consistent, and a fetch fired the
     * moment an edit returns reliably answers with the state from *before* it.
     * So the edit was applied, the feed denied it, and the denial is what
     * reached the screen — the bug this exists to fix. The re-fetch still
     * happens, via [libraryStale], once the tab is next opened and the feed has
     * caught up.
     *
     * A shelf that isn't there yet is created by [edit] returning rows for it
     * (a first playlist has no shelf to add to), and one left empty is dropped —
     * see [LibraryScreen], which draws the create tile with or without a shelf.
     */
    private fun editPlaylistShelf(edit: (List<ShelfItem>) -> List<ShelfItem>) {
        val page = (_library.value as? UiState.Success)?.data ?: return
        val existing = page.shelves.firstOrNull { it.title == YtMusicRepository.PLAYLISTS_SHELF }
        val items = edit(existing?.items.orEmpty())
        if (items == existing?.items) return
        val shelves = when {
            existing == null && items.isEmpty() -> return
            // No shelf yet: this is the account's first playlist, so the feed
            // has never had one to send. Leads the page, as the feed orders it.
            existing == null ->
                listOf(HomeShelf(YtMusicRepository.PLAYLISTS_SHELF, items)) + page.shelves
            // Emptied by deleting the last playlist. Dropped rather than left as
            // a heading over nothing; the create tile is drawn either way.
            items.isEmpty() -> page.shelves.filterNot { it === existing }
            else -> page.shelves.map { if (it === existing) existing.copy(items = items) else it }
        }
        _library.value = UiState.Success(page.copy(shelves = shelves))
    }

    /**
     * Restates a playlist's name everywhere it is currently drawn: its card in
     * the library, the picker's list, and its own open page — header and top
     * bar both, which read [DetailPage.title].
     */
    private fun setPlaylistTitle(playlist: UserPlaylist, title: String) {
        _playlists.value = _playlists.value.map {
            if (it.playlistId == playlist.playlistId) it.copy(title = title) else it
        }
        editPlaylistShelf { items ->
            items.map { if (it.browseId == playlist.browseId) it.copy(title = title) else it }
        }
        _detailStack.value = _detailStack.value.map {
            if (it.browseId == playlist.browseId) it.copy(title = title) else it
        }
    }

    /**
     * Adds [song] to a playlist, from the picker.
     *
     * Not optimistic, unlike a rating: the picker closes on the tap and there is
     * nothing left of it to update, and a playlist that shows a track it turned
     * out not to have taken is worse than one that shows it a moment late.
     *
     * The playlist's own page is the exception, because it can be the thing
     * behind the picker — a row's menu on a playlist offers "Add to playlist" —
     * and a page that doesn't show what was just added to it is the bug this is
     * part of fixing. Still after the answer, not ahead of it.
     */
    fun addToPlaylist(playlist: UserPlaylist, song: Song) {
        if (!requireSignIn()) return
        viewModelScope.launch {
            YtMusicRepository.addToPlaylist(playlist.playlistId, listOf(song.videoId)).fold(
                onSuccess = { added ->
                    libraryStale = true
                    // The playlist's page may be open behind the picker — it is
                    // reachable from a row's own menu on it — so the track goes
                    // into it for the same reason [addSuggestedSong] does.
                    appendToOpenPlaylist(playlist.browseId, song, added[song.videoId])
                },
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
        val name = title.trim().ifBlank { text(R.string.new_playlist) }
        viewModelScope.launch {
            YtMusicRepository.createPlaylist(
                title = name,
                privacy = privacy,
                videoIds = listOfNotNull(song?.videoId),
            ).fold(
                onSuccess = { playlistId ->
                    // Nothing to look up for a playlist this account has just
                    // made: it is the owner by construction, so its card is
                    // editable the moment it appears rather than one request
                    // after someone holds it.
                    setPlaylistOwned("VL$playlistId", true)
                    libraryStale = true
                    val created = UserPlaylist(
                        playlistId = playlistId,
                        title = name,
                        // Only what this request itself establishes. Both
                        // surfaces that draw it leave a blank one out, so an
                        // unseeded playlist gets a card of just its name rather
                        // than a guess at what the feed will call it.
                        subtitle = if (song != null) "1 song" else "",
                        thumbnailUrl = song?.thumbnailUrl,
                    )
                    // Drawn from what was just sent rather than waited for: the
                    // library feed does not have this playlist yet, and the
                    // fetch that used to run here answered without it — see
                    // [editPlaylistShelf]. Leads the shelf because it is the
                    // newest, which is the order the feed itself comes in.
                    _playlists.value = listOf(created) +
                        _playlists.value.filterNot { it.playlistId == created.playlistId }
                    editPlaylistShelf { items ->
                        listOf(
                            ShelfItem(
                                title = created.title,
                                subtitle = created.subtitle,
                                thumbnailUrl = created.thumbnailUrl,
                                videoId = null,
                                browseId = created.browseId,
                            ),
                        ) + items.filterNot { it.browseId == created.browseId }
                    }
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
     * Adds one of [DetailPage.suggestedSongs] to the playlist it was suggested
     * for: out of that section, and into the track list above it.
     *
     * Both halves, because either alone is a worse answer than doing nothing.
     * Only removing it — which is what this used to do — reads as the track
     * having been discarded rather than added: it leaves the Suggested list and
     * turns up nowhere, and the playlist it was added to looks unchanged until
     * the page is closed and reopened. Only adding it would leave YouTube still
     * suggesting a track that is now in the playlist.
     *
     * The row goes in complete, per-entry id included, because
     * [YtMusicRepository.addToPlaylist] reports the one it was just filed
     * under — so "Remove from this playlist" works on it immediately rather
     * than after a re-fetch. A response that named no id still adds the row;
     * it just can't offer to take it back out yet.
     */
    fun addSuggestedSong(browseId: String, song: Song) {
        if (!requireSignIn()) return
        val playlistId = browseId.removePrefix("VL")
        viewModelScope.launch {
            YtMusicRepository.addToPlaylist(playlistId, listOf(song.videoId)).fold(
                onSuccess = { added ->
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
                    appendToOpenPlaylist(browseId, song, added[song.videoId])
                },
                onFailure = {},
            )
        }
    }

    /**
     * Puts [song] at the end of the playlist page at [browseId], if that page
     * is open — where YouTube itself puts it, so the order survives the next
     * fetch.
     *
     * An empty playlist counts as open: it renders as an empty-state message, and the
     * first track added to one has to replace that message rather than be
     * dropped for want of a list to join. Only that message, though — any other
     * error is a page that failed to load, whose real contents are unknown, and
     * answering it with a one-track listing would be a playlist invented out of
     * a network failure. A page still loading is left alone too: the fetch in
     * flight is newer than this and will land with the addition already in it.
     */
    private fun appendToOpenPlaylist(browseId: String, song: Song, setVideoId: String?) {
        val added = song.copy(setVideoId = setVideoId)
        _detailStack.value = _detailStack.value.map { page ->
            if (page.browseId != browseId) return@map page
            val songs = when (val state = page.songs) {
                is UiState.Success -> state.data
                is UiState.Error -> if (state.message == text(R.string.no_tracks_here)) emptyList() else return@map page
                UiState.Loading -> return@map page
            }
            // Already there — a track added twice is two real entries on
            // YouTube's side, but a duplicate row from a double tap is not
            // something the user asked for.
            if (songs.any { it.videoId == song.videoId }) return@map page
            page.copy(
                songs = UiState.Success(
                    songs + added.copy(
                        thumbnailUrl = added.thumbnailUrl ?: page.thumbnailUrl,
                    ),
                ),
            )
        }
    }

    /**
     * Renames a playlist, and says so everywhere it is named — see
     * [setPlaylistTitle]. Renaming is nearly always done from the playlist's
     * own page or its card, so there is always something on screen still
     * showing the old name.
     */
    fun renamePlaylist(playlist: UserPlaylist, title: String) {
        if (!requireSignIn()) return
        val name = title.trim()
        if (name.isBlank() || name == playlist.title) return
        viewModelScope.launch {
            YtMusicRepository.renamePlaylist(playlist.playlistId, name).fold(
                onSuccess = {
                    setPlaylistTitle(playlist, name)
                    libraryStale = true
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
                    // The card in the library tab, which is the surface the
                    // deletion was almost certainly ordered from — and which the
                    // re-fetch that used to stand in for this left in place; see
                    // [editPlaylistShelf].
                    editPlaylistShelf { items ->
                        items.filterNot { it.browseId == playlist.browseId }
                    }
                    // Its page may be the one open; a deleted playlist has
                    // nothing left to show.
                    _detailStack.value = _detailStack.value
                        .filterNot { it.browseId == playlist.browseId }
                    libraryStale = true
                },
                onFailure = {},
            )
        }
    }

    /**
     * Whether [browseId] is a playlist this account can be asked to edit.
     *
     * Only ever yes for a playlist [playlistOwned] has confirmed the account
     * made. "In this account's library" is not the same thing and cannot stand
     * in for it: `FEmusic_liked_playlists` lists a playlist saved from someone
     * else in exactly the shape it lists one this account created, so a lookup
     * in [playlists] alone called a stranger's playlist editable and the menus
     * offered Rename and Delete on it — neither of which YouTube would have
     * honoured.
     *
     * Strict rather than permissive-until-proven, so that every surface gives
     * the same answer for the same playlist. The permissive version was right on
     * a playlist's own page — where the page load supplies the answer — and
     * wrong on a card until that page had been opened once, which is a menu that
     * changes its mind about what a playlist is depending on where it is held.
     */
    fun editablePlaylist(browseId: String?): UserPlaylist? {
        if (browseId == null || _playlistOwned.value[browseId] != true) return null
        return _playlists.value.firstOrNull { it.browseId == browseId }
    }

    /**
     * Which playlists in this account's library the account actually made, by
     * browse id — see
     * [com.music.bitchord.data.innertube.InnertubeParser.parsePlaylistOwned].
     * An id absent from the map is one nothing has asked about yet, which is not
     * the same as a no.
     *
     * Observable, because the answer routinely arrives after whatever wanted it
     * is already on screen: a card's menu opens with nothing fetched, and the
     * rows that depend on this appear as [resolvePlaylistOwnership] answers.
     */
    private val _playlistOwned = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val playlistOwned: StateFlow<Map<String, Boolean>> = _playlistOwned.asStateFlow()

    /**
     * Finds out who made the playlist at [browseId], if it isn't already known.
     *
     * Only the playlist's own page states this, so a surface that has no page —
     * a card in the library, a search result — has to ask for one. Which is why
     * this is on demand rather than swept up front: the alternative is a request
     * per playlist every time the library loads, for a question most of them
     * will never be asked.
     *
     * Silent about anything that isn't a playlist in this account's library.
     * Nothing else can be renamed or deleted whatever the answer, so asking
     * would be a request spent to rule out what was never on offer.
     */
    fun resolvePlaylistOwnership(browseId: String?) {
        if (!_signedIn.value || browseId == null) return
        if (browseId in _playlistOwned.value || browseId in ownershipInFlight) return
        if (_playlists.value.none { it.browseId == browseId }) return
        ownershipInFlight += browseId
        viewModelScope.launch {
            YtMusicRepository.playlistOwned(browseId).onSuccess { owned ->
                if (owned != null) setPlaylistOwned(browseId, owned)
            }
            // Released either way. A failed lookup that stayed marked would
            // never be retried, leaving Rename off the user's own playlist for
            // the rest of the session over one dropped request.
            ownershipInFlight -= browseId
        }
    }

    /** Guards against a second lookup while the first is still out. */
    private val ownershipInFlight = mutableSetOf<String>()

    private fun setPlaylistOwned(browseId: String, owned: Boolean) {
        _playlistOwned.value = _playlistOwned.value + (browseId to owned)
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
        startSuggestPipeline()
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
        viewModelScope.launch {
            AppSettings.filterNonMusicAudio.drop(1).collect {
                if (_detailStack.value.any { page -> page.browseId == "local:all" }) {
                    reloadLocalDetail("local:all")
                }
            }
        }
        viewModelScope.launch {
            // A leftover APK only means "Install Now" for the session that
            // downloaded it — see AppUpdateChecker.clearCache.
            AppUpdateChecker.clearCache(getApplication())
            AppUpdateChecker.check()
        }
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
        val identity = listenerKey()
        viewModelScope.launch {
            val account = YtMusicRepository.account().getOrNull()
            if (identity != listenerKey()) return@launch
            _account.value = account
            // A channel picked in the in-app browser arrives as ids and nothing
            // else — the page's `ytcfg` never says what the channel is called.
            // The account menu, asked *as* that channel, answers with its name,
            // which is what the settings row needs to show.
            if (account != null && _selectedChannelKey.value != null) {
                _selectedChannelName.value = account.name
                val accountId = _activeAccountId.value
                val profileId = _activeProfileId.value
                val session = accountId?.let { id -> authStore.sessions.firstOrNull { it.accountId == id } }
                val updated = session?.let { saved -> saved.copy(
                    name = account.name, email = account.email,
                    profiles = saved.profiles.map { profile ->
                        if (profile.profileId == profileId) profile.copy(
                            name = account.name, handle = account.email, avatar = account.thumbnailUrl,
                        ) else profile
                    },
                ) }
                if (updated != null) {
                    authStore.upsertSession(updated, activate = false)
                    _googleAccounts.value = authStore.sessions
                }
            }
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
        val identity = listenerKey()
        _refreshing.value = _refreshing.value + feed
        viewModelScope.launch {
            when (feed) {
                Feed.HOME -> refreshHome(identity)
                Feed.EXPLORE -> fetchExplore()
                Feed.LIBRARY -> fetchLibrary(identity)
            }
            _refreshing.value = _refreshing.value - feed
        }
    }

    fun loadExplore() {
        _explore.value = UiState.Loading
        viewModelScope.launch { fetchExplore() }
    }

    private suspend fun fetchExplore() {
        val state = YtMusicRepository.moodAndGenres().fold(
            onSuccess = { sections ->
                if (sections.isEmpty()) {
                    UiState.Error(text(R.string.nothing_to_explore))
                } else {
                    UiState.Success(sections)
                }
            },
            onFailure = { UiState.Error(it.friendly()) },
        )
        _explore.value = state
        (state as? UiState.Success)?.data?.let(::loadMoodGenreArtwork)
    }

    /**
     * Category buttons do not carry thumbnails themselves. Resolve a small
     * number at a time from the shelves they open, so the grid gains real art
     * without saturating the browse client or delaying the category list.
     */
    private fun loadMoodGenreArtwork(sections: List<MoodGenreSection>) {
        viewModelScope.launch {
            val limiter = Semaphore(4)
            coroutineScope {
                sections.flatMap(MoodGenreSection::items).forEach { item ->
                    launch {
                        val artwork = limiter.withPermit {
                            YtMusicRepository.moodGenreArtwork(item.browseId, item.params).getOrNull()
                        } ?: return@launch
                        val current = (_explore.value as? UiState.Success)?.data ?: return@launch
                        _explore.value = UiState.Success(current.map { section ->
                            section.copy(items = section.items.map { currentItem ->
                                if (currentItem.browseId == item.browseId && currentItem.params == item.params) {
                                    currentItem.copy(thumbnailUrl = artwork)
                                } else {
                                    currentItem
                                }
                            })
                        })
                    }
                }
            }
        }
    }

    fun openMoodGenre(item: MoodGenre) {
        _selectedMoodGenre.value = item
        _moodGenreShelves.value = UiState.Loading
        viewModelScope.launch {
            _moodGenreShelves.value = YtMusicRepository.moodGenreShelves(item.browseId, item.params).fold(
                onSuccess = { shelves ->
                    if (shelves.isEmpty()) UiState.Error(text(R.string.nothing_to_explore))
                    else UiState.Success(shelves)
                },
                onFailure = { UiState.Error(it.friendly()) },
            )
        }
    }

    fun closeMoodGenre(): Boolean {
        if (_selectedMoodGenre.value == null) return false
        _selectedMoodGenre.value = null
        return true
    }

    /** Tapping a tab should leave any pushed page behind. */
    fun clearDetail() {
        if (_detailStack.value.isNotEmpty()) _detailStack.value = emptyList()
    }

    fun loadHome() {
        val identity = listenerKey()
        val generation = homeLoadGeneration.incrementAndGet()
        _home.value = UiState.Loading
        homeContinuation = null
        homeSeenTitles.clear()
        _homeLoadingMore.value = false
        _homeRecentlyPlayedLoading.value = _signedIn.value
        // The core feed plus one browse per supplement. Recently played is left
        // out: it has its own skeleton at the head of the page rather than the
        // one at the tail.
        _homePendingShelves.value = 1 + YtMusicRepository.HOME_SUPPLEMENT_BROWSE_IDS.size
        viewModelScope.launch {
            launch {
                try {
                    YtMusicRepository.home()
                        .onSuccess { feed ->
                            if (!isCurrentHomeLoad(identity, generation)) return@onSuccess
                            homeContinuation = feed.continuation
                            publishHomeShelves(feed.shelves)
                        }
                        .onFailure { failure ->
                            if (isCurrentHomeLoad(identity, generation) && _home.value !is UiState.Success) {
                                _home.value = UiState.Error(failure.friendly())
                            }
                        }
                } finally {
                    homeShelfRequestSettled(identity, generation)
                }
            }
            if (_signedIn.value) {
                launch {
                    YtMusicRepository.homeRecentlyPlayed()
                        .onSuccess { shelf ->
                            if (isCurrentHomeLoad(identity, generation)) {
                                _homeRecentlyPlayedLoading.value = false
                                shelf?.let { publishHomeShelves(listOf(it), prepend = true) }
                            }
                        }
                        .onFailure {
                            if (isCurrentHomeLoad(identity, generation)) _homeRecentlyPlayedLoading.value = false
                        }
                }
            }
            YtMusicRepository.HOME_SUPPLEMENT_BROWSE_IDS.forEach { browseId ->
                launch {
                    try {
                        YtMusicRepository.homeSupplement(browseId).onSuccess { shelves ->
                            if (isCurrentHomeLoad(identity, generation)) publishHomeShelves(shelves)
                        }
                    } finally {
                        homeShelfRequestSettled(identity, generation)
                    }
                }
            }
        }
    }

    /**
     * One of the parallel first-page requests has finished. Ignored once a newer
     * load has taken over, which has already reset the count for its own fan-out.
     */
    private fun homeShelfRequestSettled(identity: String?, generation: Long) {
        if (!isCurrentHomeLoad(identity, generation)) return
        _homePendingShelves.value = (_homePendingShelves.value - 1).coerceAtLeast(0)
    }

    private fun isCurrentHomeLoad(identity: String?, generation: Long) =
        identity == listenerKey() && generation == homeLoadGeneration.get()

    private fun publishHomeShelves(shelves: List<HomeShelf>, prepend: Boolean = false) {
        val existing = (_home.value as? UiState.Success)?.data.orEmpty()
        if (prepend) {
            // YouTube's core home can also contain a stale "Recently played"
            // shelf. The dedicated history endpoint wins and replaces it.
            val replacing = shelves.map { it.title.lowercase(Locale.ROOT) }.toSet()
            homeSeenTitles.addAll(replacing)
            _home.value = UiState.Success(shelves + existing.filterNot {
                it.title.lowercase(Locale.ROOT) in replacing
            })
            return
        }
        val added = shelves.filter { homeSeenTitles.add(it.title.lowercase(Locale.ROOT)) }
        if (added.isNotEmpty()) _home.value = UiState.Success(existing + added)
    }

    /** Refreshes the core feed without blanking the current Play page first. */
    private suspend fun refreshHome(identity: String?) = coroutineScope {
        val recent = if (_signedIn.value) async { YtMusicRepository.homeRecentlyPlayed() } else null
        YtMusicRepository.home().onSuccess { feed ->
            if (identity != listenerKey()) return@onSuccess
            homeContinuation = feed.continuation
            homeSeenTitles.clear()
            val shelves = feed.shelves.filter { homeSeenTitles.add(it.title.lowercase(Locale.ROOT)) }
            if (shelves.isNotEmpty()) _home.value = UiState.Success(shelves)
        }
        recent?.await()?.onSuccess { shelf ->
            if (identity == listenerKey()) shelf?.let { publishHomeShelves(listOf(it), prepend = true) }
        }
    }

    /**
     * Called as the Home list nears its end. A no-op while a page is already
     * in flight, once the feed is exhausted, or before the first page has
     * loaded — [homeContinuation] covers all three by construction.
     */
    fun loadMoreHome() {
        val token = homeContinuation ?: return
        if (_homeLoadingMore.value) return
        val identity = listenerKey()
        _homeLoadingMore.value = true
        viewModelScope.launch {
            YtMusicRepository.moreHome(token).onSuccess { feed ->
                if (identity == listenerKey()) {
                    val added = feed.shelves.filter { homeSeenTitles.add(it.title.lowercase(Locale.ROOT)) }
                    // A page with nothing new signals the feed has looped back on
                    // itself rather than run dry with a token still attached —
                    // treat it the same as exhausted so scrolling can't spin here.
                    homeContinuation = feed.continuation.takeIf { added.isNotEmpty() }
                    if (added.isNotEmpty()) {
                        val existing = (_home.value as? UiState.Success)?.data ?: emptyList()
                        _home.value = UiState.Success(existing + added)
                    }
                }
            }
            if (identity == listenerKey()) _homeLoadingMore.value = false
        }
    }

    fun loadLibrary() {
        if (!_signedIn.value) return
        val identity = listenerKey()
        _library.value = UiState.Loading
        viewModelScope.launch { fetchLibrary(identity) }
    }

    private suspend fun fetchLibrary(identity: String?) {
        val next = YtMusicRepository.library().fold(
            onSuccess = { page ->
                if (page.isEmpty) UiState.Error(text(R.string.library_empty))
                else UiState.Success(page)
            },
            onFailure = { UiState.Error(it.friendly()) },
        )
        if (identity == listenerKey()) _library.value = next
    }

    /**
     * The account's listening history.
     *
     * Loaded on each visit rather than cached: it is a page whose whole subject
     * is what happened most recently, and one that opened showing the state it
     * was in last time would be answering a different question. Guests get the
     * signed-out message straight away, since there is no account to have a
     * history on.
     */
    fun loadHistory() {
        if (!_signedIn.value) {
            _history.value = UiState.Error(text(R.string.history_sign_in_required))
            return
        }
        val identity = listenerKey()
        _history.value = UiState.Loading
        viewModelScope.launch {
            val next = YtMusicRepository.history().fold(
                onSuccess = { songs ->
                    if (songs.isEmpty()) UiState.Error(text(R.string.history_empty))
                    else UiState.Success(songs)
                },
                onFailure = { UiState.Error(it.friendly()) },
            )
            if (identity == listenerKey()) _history.value = next
        }
    }

    /** Recent searches, kept on device. */
    val searchHistory: StateFlow<List<String>> = SearchHistory.recent

    fun onQueryChange(value: String) {
        val previous = _query.value
        _query.value = value
        if (value.isBlank()) {
            // Emptying the field is how the recent searches are got back to,
            // so it takes down the suggestions and the results together.
            // Nothing in flight can still be waiting to overwrite the latter:
            // the id it would be checked against has already moved past it.
            newestRequestId.incrementAndGet()
            searchSession = null
            _searchLoadingMore.value = false
            _results.value = null
            _suggestions.value = emptyList()
            return
        }
        // The previous keystroke's completions are left up beneath the new
        // lead row while the fresh ones are fetched — the same reasoning as
        // [prefixMatch]: they were right a letter ago, and a list that
        // collapses to one row on every letter is what makes a typeahead feel
        // broken. Text that isn't a continuation of what they were for (the
        // whole field replaced at once, say) drops them instead of showing
        // completions of a query that's gone.
        val stale = if (value.startsWith(previous, true) || previous.startsWith(value, true)) {
            _suggestions.value.drop(1)
        } else {
            emptyList()
        }
        _suggestions.value = listOf(value) + stale.filterNot { it.equals(value, true) }
        suggestRequests.tryEmit(value)
    }

    /**
     * Commits the current query to the history. Called when the user acts on
     * what they found — submitting from the keyboard, or opening a result —
     * rather than on every keystroke, which would fill the list with the
     * prefixes typed on the way to the real query.
     */
    fun recordSearch() = SearchHistory.record(_query.value)

    /**
     * The search button — the keyboard's search action, or the magnifier in
     * the field. The only thing that runs a search for text the user typed:
     * keystrokes themselves ask for suggestions and nothing more, so a query
     * is fetched once, when they say it's finished, instead of once per
     * prefix on the way to it.
     */
    fun submitSearch() {
        recordSearch()
        _suggestions.value = emptyList()
        runSearch()
    }

    /**
     * Runs a term the user picked out of a list rather than typed — a recent
     * search, or one of [suggestions] — and floats it to the top of the
     * history. Picking is as deliberate as submitting, so it searches on the
     * spot.
     */
    fun searchFor(term: String) {
        _query.value = term
        _suggestions.value = emptyList()
        SearchHistory.record(term)
        runSearch()
    }

    fun removeSearch(term: String) = SearchHistory.remove(term)

    fun clearSearchHistory() = SearchHistory.clear()

    fun onFilterChange(value: SearchFilter) {
        if (_filter.value == value) return
        _filter.value = value
        runSearch()
    }

    /**
     * A search asked for, as a request the pipeline below decides what to do
     * with.
     *
     * [requestId] is what makes a late answer harmless: a response is only
     * written to the screen if its id is still the newest one asked for.
     */
    private data class SearchRequest(
        val query: String,
        val filter: SearchFilter,
        val requestId: Long,
    )

    private fun cacheKey(query: String, filter: SearchFilter) = "${filter.name}:$query"

    /**
     * The results of the longest earlier query this one starts with — near
     * enough to leave up while the narrower search runs.
     */
    private fun prefixMatch(query: String, filter: SearchFilter): List<SearchResult>? {
        val prefix = "${filter.name}:"
        return searchCache.snapshot()
            .filterKeys { it.startsWith(prefix) && query.startsWith(it.removePrefix(prefix), true) }
            .maxByOrNull { it.key.length }
            ?.value
            ?.rows
    }

    private fun runSearch() {
        val query = _query.value
        if (query.isBlank()) {
            // Nothing in flight can still be waiting to overwrite this: the
            // id it would be checked against has already moved past it.
            newestRequestId.incrementAndGet()
            searchSession = null
            _searchLoadingMore.value = false
            _results.value = null
            return
        }
        val id = newestRequestId.incrementAndGet()
        _searchScrollReset.value += 1
        searchSession = null
        _searchLoadingMore.value = false
        searchRequests.tryEmit(SearchRequest(query, _filter.value, id))
    }

    /**
     * The search pipeline, started once and left running for the lifetime of
     * the view model.
     *
     * The point of it being one long-lived collector is that a new search no
     * longer cancels the request before it out of a fresh coroutine.
     * Cancelling a call mid-flight tears down its socket, and on a pooled HTTP
     * client that is felt by whatever picks that connection up next — which is
     * how one search could end in "Software caused connection abort" for a
     * request that was never itself in any trouble.
     *
     * There is no debounce here any more, and nothing to absorb: a search is
     * only ever asked for by a deliberate act — the search button, a
     * suggestion or history row, a filter tab — so the request that arrives is
     * already the one the user meant, and making them wait out a timer for it
     * would be a delay with nothing behind it. Typing asks
     * [startSuggestPipeline] for completions instead and leaves the results
     * alone.
     */
    private fun startSearchPipeline() = viewModelScope.launch {
        searchRequests
            .collectLatest { request ->
                val key = cacheKey(request.query, request.filter)
                // Something to look at immediately: the exact answer if this
                // query has been run before, otherwise the closest earlier
                // one. Only fall back to a spinner with neither.
                val exact = searchCache.get(key)
                val cached = exact?.rows ?: prefixMatch(request.query, request.filter)
                _results.value = cached?.let { UiState.Success(it) } ?: UiState.Loading
                if (exact != null) {
                    searchSession = SearchSession(key, request.requestId, request.filter, exact.continuation)
                    return@collectLatest
                }

                // Search is YouTube's alone. A module is a *substitution*
                // layer, not a catalogue to browse: it never has cover art,
                // radio, related tracks or an album page, so its rows arrived
                // in the results list looking like YouTube's and then behaved
                // nothing like them. Every track found here takes the ordinary
                // YouTube path and is handed to the module at playback time —
                // see [SourceResolver.substituteForYouTube] — which upgrades
                // the ones it holds without any of them having to be a
                // separate row to pick between.
                val result = YtMusicRepository.searchPage(request.query, request.filter)
                // A search that has been superseded shouldn't land on screen,
                // whether it succeeded or failed.
                if (request.requestId != newestRequestId.get()) return@collectLatest
                _results.value = result.fold(
                    onSuccess = { page -> published(page, key, request.requestId) },
                    onFailure = { failure -> UiState.Error(failure.friendly()) },
                )
            }
    }

    /**
     * Continues the visible search only when the list reaches its end. This is
     * deliberately separate from the first-page request: waiting for every
     * continuation was the reason a search sat on a spinner for seconds.
     */
    fun loadMoreSearchResults() {
        val session = searchSession ?: return
        val token = session.continuation ?: return
        if (_searchLoadingMore.value) return
        _searchLoadingMore.value = true
        viewModelScope.launch {
            val next = YtMusicRepository.searchContinuation(token, session.filter)
            val stillCurrent = searchSession == session && session.requestId == newestRequestId.get()
            if (stillCurrent) {
                next.onSuccess { page ->
                    val current = (_results.value as? UiState.Success)?.data.orEmpty()
                    val merged = (current + page.rows).distinctBy(::searchResultKey)
                    searchCache.put(session.key, SearchCacheEntry(merged, page.continuation))
                    searchSession = session.copy(continuation = page.continuation)
                    _results.value = UiState.Success(merged)
                }
                _searchLoadingMore.value = false
            }
        }
    }

    /**
     * The typeahead pipeline, alongside [startSearchPipeline] and for the same
     * structural reason — one long-lived collector rather than a coroutine per
     * keystroke, so a lookup the user has typed past doesn't take a pooled
     * socket down with it.
     *
     * This one *does* debounce, and that isn't the timer that was taken off the
     * search. It's two orders of magnitude shorter, and it's paid for by the
     * request behind it being a few hundred bytes rather than a full page of
     * results — a burst of keystrokes shouldn't each cost a round trip, but the
     * gap has to be short enough that the list is up before the next letter is
     * typed. Nothing is waiting on it either way: the row the user typed is
     * already on screen from the keystroke itself.
     *
     * A failure is left on the floor. There is no worthwhile way to report
     * "couldn't suggest anything" in a list of suggestions, and the typed text
     * is standing there as a working first row regardless.
     */
    @OptIn(FlowPreview::class)
    private fun startSuggestPipeline() = viewModelScope.launch {
        // Whether a list for [input] is still wanted. False once the field has
        // moved on: typed further, or searched — which empties [_suggestions],
        // and a late answer writing to it would reopen the suggestions over
        // the results the user is by then reading.
        fun stillWanted(input: String) =
            _query.value == input && _suggestions.value.isNotEmpty()

        suggestRequests
            .debounce(SUGGEST_DEBOUNCE_MS)
            .collectLatest { input ->
                if (!stillWanted(input)) return@collectLatest
                val fetched = YtMusicRepository.searchSuggestions(input).getOrNull()
                    ?: return@collectLatest
                // Asked again on the way back; the field is live throughout.
                if (!stillWanted(input)) return@collectLatest
                _suggestions.value = listOf(input) +
                    fetched.filterNot { it.equals(input, ignoreCase = true) }
            }
    }

    /** Caches and publishes the initial result page without waiting for later pages. */
    private fun published(
        page: YtMusicRepository.SearchPage,
        key: String,
        requestId: Long,
    ): UiState<List<SearchResult>> {
        val rows = page.rows
        if (rows.isEmpty()) return UiState.Error(text(R.string.no_results))
        searchCache.put(key, SearchCacheEntry(rows, page.continuation))
        searchSession = SearchSession(key, requestId, _filter.value, page.continuation)
        prefetchTopResult(rows)
        return UiState.Success(rows)
    }

    private fun searchResultKey(row: SearchResult): String = when (row) {
        is SearchResult.TopTrack -> "v:${row.song.videoId}"
        is SearchResult.Track -> "v:${row.song.videoId}"
        is SearchResult.Browse -> "b:${row.item.browseId}"
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
     *
     * Asked of the playback list rather than every enabled source, so a result
     * offered here is one this connection's ceiling would actually let play —
     * a row that can only be tapped into a YouTube stream is a lie about where
     * the track is coming from.
     */
    private suspend fun sourceResults(
        query: String,
        filter: SearchFilter,
    ): Pair<List<SearchResult>, List<SearchResult>> = coroutineScope {
        if (filter != SearchFilter.SONGS) return@coroutineScope emptyList<SearchResult>() to emptyList()
        val active = SourceRegistry.activeForPlayback()
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
     * The exact search result is warmed because video tracks now play their
     * own audio by default; catalogue matching is only requested manually.
     */
    private fun prefetchTopResult(rows: List<SearchResult>) {
        val song = rows.firstNotNullOfOrNull {
            when (it) {
                is SearchResult.TopTrack -> it.song
                is SearchResult.Track -> it.song
                is SearchResult.Browse -> null
            }
        } ?: return
        viewModelScope.launch {
            runCatching {
                val audio = song
                // A source-backed row resolves through its own source already
                // and never takes the YouTube path — warming either half of
                // this for one would be work nothing asks for.
                if (SourceRegistry.parseTrackKey(audio.videoId) != null) return@runCatching
                // JioSaavn first, on the same reasoning as the queue's
                // read-ahead: it is the copy that will actually be played if it
                // has the track, so warming YouTube's URL instead warms the one
                // that loses. Pinned through [StreamChoice] so playback opens
                // this very stream rather than racing for it again — see
                // [SourceResolver.prefetchSubstitute], which requires it.
                val warmed = SourceResolver.prefetchSubstitute(
                    TrackMatcher.Target(
                        title = audio.title,
                        artist = audio.artist,
                        durationSec = TrackMatcher.secondsOf(audio.durationText),
                        album = audio.albumName,
                        isExplicit = audio.isExplicit,
                        isVideo = audio.isVideo,
                    ),
                )
                if (warmed != null) {
                    StreamChoice.remember(audio.videoId, warmed, substituted = true)
                    return@runCatching
                }
                // Disabled, or hasn't got it: the tap path falls back to
                // YouTube, so that is what is worth having ready.
                StreamResolver.resolve(audio.videoId)
            }
        }
    }

    private companion object {
        /**
         * How long a keystroke waits before the typeahead is asked about it.
         *
         * Not the search's timer — searches aren't on a timer any more. This
         * one only stops a fast typist spending a round trip per letter, so it
         * wants to be as short as it can be while still collapsing a burst:
         * long enough that "cold" isn't four lookups, short enough that the
         * list is up by the time the thumb has left the key.
         */
        const val SUGGEST_DEBOUNCE_MS = 180L

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

        /**
         * What a page with an empty listing says.
         *
         * Named because it is a state one can be got *out* of, not just a
         * message: an own playlist with nothing in it lands here, and adding the
         * first track to it has to be able to tell "this page is empty" apart
         * from "this page failed to load" — see [appendToOpenPlaylist].
         */

        /**
         * What a downloaded playlist's page says once the files under it are
         * gone.
         *
         * A record here outlives the folder it names — the user is expected to
         * manage Downloads with a file manager — so this is a state its page has
         * to be able to reach, not an error. Named because three places say it:
         * the page, its refresh, and the long-press menu that queues it without
         * opening it.
         */
    }

    fun openDetail(
        browseId: String,
        title: String,
        subtitle: String = "",
        thumbnailUrl: String? = null,
        type: BrowseType = BrowseType.OTHER,
    ) {
        val resolved = browseTypeOf(browseId, type)
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
            /**
             * The credit line, when the page had to supply its own.
             *
             * Only a link tapped outside the app arrives with neither — see
             * [com.music.bitchord.playback.MusicLink]. Every other caller was
             * looking at a card that already said this.
             */
            var credit: String? = null
            /** Set when the track list carries on past its first response. */
            var more: String? = null
            /** Tracks YouTube offers to round the playlist out — see [DetailPage.suggestedSongs]. */
            var suggested: List<Song> = emptyList()
            /** Whether this release is already saved — see [DetailPage.library]. */
            var library: LibraryState? = null
            /** YouTube's own "About" blurb — see [DetailPage.description]. */
            var description: String? = null
            /** Artist header stats — see [DetailPage.subscriberCountText]. */
            var subscriberCountText: String? = null
            var monthlyListenerCount: String? = null
            /** Whether this artist is subscribed to — see [DetailPage.subscription]. */
            var subscription: SubscriptionState? = null
            val state = when {
                Downloads.recordIdOf(browseId) != null -> {
                    val songs = downloadedPlaylist(browseId)
                    if (songs.isEmpty()) UiState.Error(text(R.string.downloaded_playlist_empty))
                    else UiState.Success(songs)
                }
                browseId == "local:downloads" -> {
                    val context = getApplication<Application>()
                    val songs = Downloads.getDownloadedSongs(context)
                    if (songs.isEmpty()) UiState.Error("No downloaded tracks")
                    else UiState.Success(songs)
                }
                browseId == "local:all" -> {
                    val context = getApplication<Application>()
                    if (!LocalMediaRepository.hasStoragePermission(context)) {
                        UiState.Error(text(R.string.storage_required_read))
                    } else {
                        val songs = LocalMediaRepository.getLocalMusic(context)
                        if (songs.isEmpty()) UiState.Error(text(R.string.no_local_audio_found))
                        else UiState.Success(songs)
                    }
                }
                resolved == BrowseType.ARTIST -> {
                    YtMusicRepository.artistPage(browseId).fold(
                        onSuccess = { page ->
                            sections = page.sections
                            artwork = page.thumbnailUrl
                            name = page.name
                            description = page.description
                            subscriberCountText = page.subscriberCountText
                            monthlyListenerCount = page.monthlyListenerCount
                            subscription = page.subscription
                            if (page.songs.isEmpty()) {
                                UiState.Error(text(R.string.no_tracks_here))
                            } else {
                                UiState.Success(page.songs.withArtwork(thumbnailUrl ?: artwork))
                            }
                        },
                        onFailure = { UiState.Error(it.friendly()) },
                    )
                }
                else -> {
                    YtMusicRepository.browseSongs(browseId).fold(
                        onSuccess = { page ->
                            // Free here — the page that returned these rows is
                            // the one thing that states who made the playlist,
                            // so its own menu never has to go and ask. Recorded
                            // even when the listing came back empty.
                            page.owned?.let { setPlaylistOwned(browseId, it) }
                            // Only for the caller that had nothing: a card's own
                            // title is what the user just tapped, and must not
                            // be swapped for the header's wording underneath them.
                            page.header?.let { header ->
                                if (title.isBlank()) name = header.title
                                // Album cards often only carry the artist, while
                                // the page header also carries the release year.
                                // Prefer that richer line so the year appears
                                // directly below the artist on the album page.
                                if (resolved == BrowseType.ALBUM && header.subtitle.isNotBlank()) {
                                    credit = header.subtitle
                                } else if (subtitle.isBlank()) {
                                    credit = header.subtitle
                                }
                                if (thumbnailUrl == null) artwork = header.thumbnailUrl
                            }
                            description = page.description
                            if (page.songs.isEmpty()) {
                                UiState.Error(text(R.string.no_tracks_here))
                            } else {
                                more = page.continuation
                                suggested = page.suggested.withArtwork(thumbnailUrl ?: artwork)
                                library = page.library
                                UiState.Success(page.songs.withArtwork(thumbnailUrl ?: artwork))
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
                        subtitle = credit ?: it.subtitle,
                        suggestedSongs = suggested,
                        library = library,
                        description = description,
                        subscriberCountText = subscriberCountText,
                        monthlyListenerCount = monthlyListenerCount,
                        subscription = subscription,
                    )
                } else {
                    it
                }
            }
            // Only once the first page is on screen: [fillIn] appends to it,
            // and has nothing to append to before this.
            more?.let { fillIn(browseId, it, thumbnailUrl ?: artwork) }
        }
    }

    fun reloadLocalDetail(browseId: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val state: UiState<List<Song>> = when {
                Downloads.recordIdOf(browseId) != null -> {
                    val songs = downloadedPlaylist(browseId)
                    if (songs.isEmpty()) UiState.Error(text(R.string.downloaded_playlist_empty))
                    else UiState.Success(songs)
                }
                browseId == "local:downloads" -> {
                    val songs = Downloads.getDownloadedSongs(context)
                    if (songs.isEmpty()) UiState.Error("No downloaded tracks")
                    else UiState.Success(songs)
                }
                browseId == "local:all" -> {
                    if (!LocalMediaRepository.hasStoragePermission(context)) {
                        UiState.Error(text(R.string.storage_required_read))
                    } else {
                        val songs = LocalMediaRepository.getLocalMusic(context)
                        if (songs.isEmpty()) UiState.Error(text(R.string.no_local_audio_found))
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
     * The tracks of the downloaded playlist [browseId] names that are still on
     * disk, in the order the playlist had.
     *
     * Reads the whole Downloads folder rather than the record's own uris,
     * because that read is what fills in an album tag the record never carried
     * and what collapses a music video's two ids down to the one file it saved —
     * see [Downloads.collectionsAmong], of which this is a single-playlist view.
     *
     * Empty is the honest answer for a record whose files have all been deleted
     * from under it, and callers turn that into an empty-state message rather than
     * into a blank page.
     */
    private suspend fun downloadedPlaylist(browseId: String): List<Song> {
        val id = Downloads.recordIdOf(browseId) ?: return emptyList()
        val folder = LocalMediaRepository.getDownloadedSongs(getApplication())
        return Downloads.collectionsAmong(folder).firstOrNull { it.id == id }?.songs.orEmpty()
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
     *
     * Public because the long-press menus ask the same question of a card
     * before offering to queue what is behind it — an artist is not a running
     * order, so it gets no queue actions.
     */
    fun browseTypeOf(browseId: String, fallback: BrowseType = BrowseType.OTHER): BrowseType = when {
        // Not one of YouTube's, and the only one of these that says outright what
        // it is rather than being read off a prefix convention.
        browseId.startsWith(Downloads.PLAYLIST_PREFIX) -> BrowseType.PLAYLIST
        browseId.startsWith("UC") -> BrowseType.ARTIST
        browseId.startsWith("MPREb") -> BrowseType.ALBUM
        browseId.startsWith("VL") || browseId.startsWith("PL") -> BrowseType.PLAYLIST
        else -> fallback
    }

    /**
     * Every track behind an album or playlist, handed to [onResult] once it is
     * all in.
     *
     * What a long-press on a card is acting on. A card has nothing but a browse
     * id — its page was never opened, so there is no track list anywhere to
     * read — and "add this album to the queue" means the whole album, so this
     * follows continuations to the end rather than taking the first page.
     *
     * Runs in [viewModelScope], not the caller's: the sheet the tap came from
     * closes immediately, and a three-hundred-track playlist must not be
     * abandoned halfway because of it.
     */
    fun collectSongs(
        browseId: String,
        artworkFallback: String? = null,
        onResult: (Result<List<Song>>) -> Unit,
    ) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val result = when {
                Downloads.recordIdOf(browseId) != null -> runCatching {
                    downloadedPlaylist(browseId).ifEmpty {
                        error(text(R.string.downloaded_playlist_empty))
                    }
                }
                browseId == "local:downloads" -> runCatching {
                    Downloads.getDownloadedSongs(context)
                        .ifEmpty { error("No downloaded tracks") }
                }
                browseId == "local:all" -> runCatching {
                    if (!LocalMediaRepository.hasStoragePermission(context)) {
                        error(text(R.string.storage_required_read))
                    }
                    LocalMediaRepository.getLocalMusic(context)
                        .ifEmpty { error(text(R.string.no_local_audio_found)) }
                }
                else -> YtMusicRepository.allSongs(browseId)
            }
            onResult(result.map { it.withArtwork(artworkFallback) })
        }
    }

    /** Pops one page; returns false when there was nothing to pop. */
    fun closeDetail(): Boolean {
        val stack = _detailStack.value
        if (stack.isEmpty()) return false
        _detailStack.value = stack.dropLast(1)
        return true
    }

    /**
     * Loads the channel list, unless it is already in hand.
     *
     * @param force refetch even if it is — after a switch, since the list
     *   itself reports which channel is active.
     */
    fun loadChannels(force: Boolean = false) {
        if (!_signedIn.value) return
        if (_channelsLoading.value) return
        if (!force && _channels.value.isNotEmpty()) return
        viewModelScope.launch {
            _channelsLoading.value = true
            YtMusicRepository.accountChannels()
                .onSuccess {
                    _channels.value = it
                    persistDetectedProfiles(it)
                }
            _channelsLoading.value = false
        }
    }

    /**
     * Acts as [channel] from here on.
     *
     * Everything already on screen belongs to the channel being left — its
     * library, its hearts, its playlists — so the switch clears them and
     * refetches rather than letting the new identity's pages arrive one at a
     * time on top of the old one's.
     */
    fun selectChannel(channel: AccountChannel) {
        val accountId = _activeAccountId.value ?: return
        val profile = YouTubeProfile(
            profileId = profileId(channel.pageId, channel.dataSyncId, channel.name),
            name = channel.name, handle = channel.subtitle, avatar = channel.thumbnailUrl,
            pageId = channel.pageId, dataSyncId = channel.dataSyncId,
            isBrandAccount = channel.pageId != null,
        )
        selectProfile(accountId, profile.profileId, profile)
    }

    /**
     * A session lifted out of the in-app browser — a fresh sign-in, or the same
     * login now pointed at a different channel.
     *
     * One path for both because they differ in exactly one thing: whether the
     * cookie is new. What follows — adopt the identity the captured page
     * reported, remember it, and refetch everything that belongs to a listener
     * — is the same work either way.
     */
    fun onWebSession(session: CapturedSession, mode: WebSessionMode) {
        val accountId = sessionId(session.cookie, session.dataSyncId)
        val previous = authStore.sessions.firstOrNull { it.accountId == accountId }
        val profile = YouTubeProfile(
            profileId = profileId(session.pageId, session.dataSyncId, previous?.name ?: "Personal"),
            name = previous?.profiles?.firstOrNull { it.pageId == session.pageId }?.name ?: "Personal",
            pageId = session.pageId, dataSyncId = session.dataSyncId, authUser = session.authUser,
            isBrandAccount = session.pageId != null,
        )
        // A profile captured before its channel existed carries a provisional,
        // name-hash id (see profileId()) since Google reports no pageId/dataSyncId
        // for it yet. Once this same login reports real ids, that placeholder is
        // the same identity under a new id — drop it rather than keep both.
        val hasRealIdentity = profile.pageId != null || profile.dataSyncId != null
        val profiles = (previous?.profiles.orEmpty().filterNot {
            it.profileId == profile.profileId || (hasRealIdentity && it.profileId.startsWith("profile:"))
        } + profile)
        val stored = GoogleAccountSession(
            accountId = accountId, cookie = session.cookie, name = previous?.name.orEmpty(),
            email = previous?.email.orEmpty(), profiles = profiles, activeProfileId = profile.profileId,
        )
        authStore.upsertSession(stored)
        _googleAccounts.value = authStore.sessions
        _activeAccountId.value = accountId
        _activeProfileId.value = profile.profileId
        _channels.value = emptyList()
        authStore.cookie = session.cookie // legacy compatibility only
        // Assigned before the scope is adopted, never after: setting a cookie
        // that differs from the last one clears the scope and the channel with
        // it, which would throw away the identity just captured.
        Innertube.cookie = session.cookie
        Innertube.adoptSessionScope(
            pageId = session.pageId,
            dataSyncId = session.dataSyncId,
            authUser = session.authUser,
            visitorData = session.visitorData,
            clientVersion = session.clientVersion,
            loggedIn = session.loggedIn,
        )

        if (session.loggedIn && (session.pageId != null || session.dataSyncId != null)) {
            // Persisted so the choice survives a restart: the shell fetched on
            // the next launch reports the default channel, and without this the
            // app would quietly drift back to it.
            Innertube.selectChannel(session.pageId, session.dataSyncId, session.authUser)
            _selectedChannelKey.value = session.pageId ?: session.dataSyncId
            _selectedChannelName.value = profile.name
        }

        // Every "this track can't be played" the resolver recorded under the
        // previous session was reached under different rules. An age-gated
        // track is the whole point of signing in, and it is the one verdict a
        // session overturns — so a listener who signs in to play a track must
        // not spend the next ten minutes being told it still cannot be played.
        StreamResolver.onSessionChanged()
        val wasSignedIn = _signedIn.value
        _signedIn.value = true
        if (wasSignedIn) clearListenerState()
        reloadForAccount()
        loadChannels(force = true)
    }

    /** Selects an identity without ever allowing a response to replace it. */
    fun selectProfile(accountId: String, selectedProfileId: String, supplied: YouTubeProfile? = null) {
        val source = _googleAccounts.value.firstOrNull { it.accountId == accountId } ?: return
        val profile = supplied ?: source.profiles.firstOrNull { it.profileId == selectedProfileId } ?: return
        if (accountId == _activeAccountId.value && profile.profileId == _activeProfileId.value) return
        cacheCurrentListener()
        val updated = source.copy(
            profiles = (source.profiles.filterNot { it.profileId == profile.profileId } + profile),
            activeProfileId = profile.profileId,
        )
        authStore.upsertSession(updated)
        authStore.select(accountId, profile.profileId)
        _googleAccounts.value = authStore.sessions
        _activeAccountId.value = accountId
        _activeProfileId.value = profile.profileId
        authStore.cookie = updated.cookie
        Innertube.cookie = updated.cookie
        Innertube.selectChannel(profile.pageId, profile.dataSyncId, profile.authUser)
        _selectedChannelKey.value = profile.profileId
        _selectedChannelName.value = profile.name
        StreamResolver.onSessionChanged()
        clearListenerState(restoreCached = true)
        reloadForAccount()
    }

    /** Returns false at an edge, allowing the avatar to play its elastic cue. */
    fun stepProfile(forward: Boolean): Boolean {
        val target = adjacentProfile(_googleAccounts.value, _activeAccountId.value, _activeProfileId.value, forward)
            ?: return false
        selectProfile(target.first, target.second)
        return true
    }

    fun removeAccount(accountId: String) {
        val fallback = authStore.removeAccount(accountId)
        _googleAccounts.value = authStore.sessions
        if (fallback == null) { signOut(); return }
        selectProfile(fallback.accountId, fallback.activeProfileId ?: fallback.profiles.firstOrNull()?.profileId ?: return)
    }

    private fun persistDetectedProfiles(channels: List<AccountChannel>) {
        val accountId = _activeAccountId.value ?: return
        val current = authStore.sessions.firstOrNull { it.accountId == accountId } ?: return
        val detected = channels.map { channel -> YouTubeProfile(
            profileId(channel.pageId, channel.dataSyncId, channel.name), channel.name, channel.subtitle,
            channel.thumbnailUrl, channel.pageId, channel.dataSyncId,
            isBrandAccount = channel.pageId != null,
        ) }
        // A profile captured before its channel existed carries a provisional,
        // name-hash id (see profileId()) rather than the pageId/dataSyncId Google
        // reports here once the channel is live. Once Google actually reports a
        // channel, that placeholder is stale — it would have been listed here
        // too if it still lacked real ids — and it's dropped rather than kept
        // alongside the identity it was standing in for. An empty response
        // (channel not created yet, or a transient miss) must never drop it.
        val stalePlaceholder = if (detected.isEmpty()) emptySet() else current.profiles
            .filter { it.profileId.startsWith("profile:") }
            .map { it.profileId }
            .toSet()
        // Keep the captured active identity if Google's endpoint temporarily
        // omits it; an empty/partial response must never erase a selection.
        val profiles = (current.profiles.filter { known ->
            known.profileId !in stalePlaceholder && detected.none { it.profileId == known.profileId }
        } + detected)
        val selected = when {
            current.activeProfileId != null && current.activeProfileId !in stalePlaceholder -> current.activeProfileId
            current.activeProfileId in stalePlaceholder -> detected.singleOrNull()?.profileId
            else -> null
        } ?: profiles.firstOrNull()?.profileId
        authStore.upsertSession(current.copy(profiles = profiles, activeProfileId = selected), activate = false)
        _googleAccounts.value = authStore.sessions
    }

    /**
     * Drops everything on screen that belongs to the identity being left.
     *
     * The hearts, the playlists and the library are all answers to "who is
     * asking", so keeping them across a switch shows the new channel the old
     * one's music until each page happens to be refetched.
     */
    private fun cacheCurrentListener() {
        val key = listenerKey() ?: return
        listenerCache[key] = ListenerSnapshot(
            _account.value, _library.value, _history.value, _playlists.value, _playlistOwned.value,
        )
    }

    private fun listenerKey(): String? = _activeAccountId.value?.let { accountId ->
        _activeProfileId.value?.let { profileId -> "$accountId:$profileId" }
    }

    private fun clearListenerState(restoreCached: Boolean = false) {
        _account.value = null
        LikeState.clear()
        _playlistsLoading.value = false
        _playlists.value = emptyList()
        _playlistOwned.value = emptyMap()
        ownershipInFlight.clear()
        _songMenu.value = null
        _library.value = UiState.Loading
        _history.value = UiState.Loading
        if (restoreCached) listenerCache[listenerKey()]?.let { cached ->
            _account.value = cached.account
            _library.value = cached.library
            _history.value = cached.history
            _playlists.value = cached.playlists
            _playlistOwned.value = cached.owned
        }
    }

    /**
     * Everything that is "the signed-in listener's", refetched.
     *
     * The scope comes first, and inside one coroutine rather than beside them:
     * which channel the session acts as decides what "the library" and "the
     * history" even refer to, so loading them first and scoping second shows
     * the listener the wrong account's music and then silently disagrees with
     * itself.
     */
    private fun reloadForAccount() {
        viewModelScope.launch {
            Innertube.ensureSessionScope()
            loadHome()
            loadLibrary()
            loadAccount()
            loadPlaylists()
        }
    }

    fun signOut() {
        // In a multi-account install, sign out removes only the current Google
        // session and restores another one. A legacy single-account install
        // retains the familiar full sign-out behaviour.
        val current = _activeAccountId.value
        if (current != null && authStore.sessions.size > 1) {
            removeAccount(current)
            return
        }
        authStore.signOut()
        Innertube.cookie = null
        // The mirror image: verdicts reached with a session in hand say nothing
        // about what an anonymous walk will be told, and the clients stood down
        // for refusing the session deserve a fresh hearing without it.
        StreamResolver.onSessionChanged()
        _signedIn.value = false
        _account.value = null
        Innertube.selectChannel(null, null)
        _channels.value = emptyList()
        _selectedChannelKey.value = null
        _selectedChannelName.value = null
        _googleAccounts.value = emptyList()
        _activeAccountId.value = null
        _activeProfileId.value = null
        _library.value = UiState.Loading
        // Ratings and playlists belong to the account that just left; keeping
        // them would show the next signed-in user someone else's hearts.
        LikeState.clear()
        _playlists.value = emptyList()
        _playlistOwned.value = emptyMap()
        ownershipInFlight.clear()
        _songMenu.value = null
        loadHome()
    }

    private fun Throwable.friendly(): String = when {
        message?.contains("resolve host", true) == true ||
            message?.contains("Unable to resolve", true) == true -> text(R.string.no_internet_connection)
        message?.contains("401") == true || message?.contains("403") == true ->
            text(R.string.youtube_request_rejected)
        else -> message ?: text(R.string.something_went_wrong)
    }

    private fun text(id: Int): String = getApplication<Application>().getString(id)
}
