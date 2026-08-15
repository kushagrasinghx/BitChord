package com.music.bitchord.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.AppUpdateChecker
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.lyrics.LrcLib
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.PlaybackTracker
import com.music.bitchord.data.model.Account
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.settings.SearchHistory
import android.util.LruCache
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
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

    /**
     * Whether the lookup for the current track has finished. [lyrics] alone
     * can't tell "still looking" apart from "looked, found nothing" — both
     * are null — and the player needs that distinction to show "Lyrics not
     * available" only once it actually means that.
     */
    private val _lyricsChecked = MutableStateFlow(false)
    val lyricsChecked: StateFlow<Boolean> = _lyricsChecked.asStateFlow()

    private var lyricsJob: Job? = null
    private var lyricsFor: String? = null

    /** Called as the playing track changes; cheap no-op when already loaded. */
    fun loadLyrics(videoId: String, title: String, artist: String, durationMs: Long) {
        if (lyricsFor == videoId) return
        lyricsFor = videoId
        _lyrics.value = null
        _lyricsChecked.value = false
        lyricsJob?.cancel()
        if (durationMs <= 0L) {
            // Duration arrives a beat after the track does; wait for it.
            lyricsFor = null
            return
        }
        lyricsJob = viewModelScope.launch {
            _lyrics.value = LrcLib.lyrics(title, artist, durationMs)
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

    init {
        startSearchPipeline()
        loadHome()
        loadExplore()
        if (_signedIn.value) {
            loadLibrary()
            loadAccount()
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

    /** Re-runs a term picked out of the history, and floats it back to the top. */
    fun searchFor(term: String) {
        _query.value = term
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
     * Every keystroke, as a request the pipeline below decides what to do with.
     *
     * [requestId] is what makes a late answer harmless: a response is only
     * written to the screen if its id is still the newest one asked for.
     */
    private data class SearchRequest(val query: String, val filter: SearchFilter, val requestId: Long)

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

    private fun runSearch() {
        val query = _query.value
        if (query.isBlank()) {
            // Nothing in flight can still be waiting to overwrite this: the
            // id it would be checked against has already moved past it.
            newestRequestId.incrementAndGet()
            _results.value = null
            return
        }
        val id = newestRequestId.incrementAndGet()
        searchRequests.tryEmit(SearchRequest(query, _filter.value, id))
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
     */
    @OptIn(FlowPreview::class)
    private fun startSearchPipeline() = viewModelScope.launch {
        searchRequests
            .debounce(SEARCH_DEBOUNCE_MS)
            .collectLatest { request ->
                val key = cacheKey(request.query, request.filter)
                // Something to look at immediately: the exact answer if this
                // query has been run before, otherwise the closest earlier
                // one. Only fall back to a spinner with neither.
                val exact = searchCache.get(key)
                val cached = exact ?: prefixMatch(request.query, request.filter)
                _results.value = cached?.let { UiState.Success(it) } ?: UiState.Loading
                if (exact != null) return@collectLatest

                val result = YtMusicRepository.search(request.query, request.filter)
                // A search the user has already typed past shouldn't land on
                // screen, whether it succeeded or failed.
                if (request.requestId != newestRequestId.get()) return@collectLatest
                _results.value = result.fold(
                    onSuccess = { rows ->
                        if (rows.isEmpty()) {
                            UiState.Error("No results")
                        } else {
                            searchCache.put(key, rows)
                            UiState.Success(rows)
                        }
                    },
                    onFailure = { UiState.Error(it.friendly()) },
                )
            }
    }

    private companion object {
        /**
         * Short enough that it reads as instant, long enough that a word typed
         * at speed is one request rather than one per letter.
         */
        const val SEARCH_DEBOUNCE_MS = 80L

        const val SEARCH_CACHE_ENTRIES = 100
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
            val state = if (resolved == BrowseType.ARTIST) {
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
            } else {
                YtMusicRepository.browseSongs(browseId).fold(
                    onSuccess = { page ->
                        if (page.songs.isEmpty()) {
                            UiState.Error("No tracks here")
                        } else {
                            more = page.continuation
                            UiState.Success(page.songs.withArtwork(thumbnailUrl))
                        }
                    },
                    onFailure = { UiState.Error(it.friendly()) },
                )
            }
            // Update by id — the user may have pushed another page meanwhile.
            _detailStack.value = _detailStack.value.map {
                if (it.browseId == browseId && it.songs is UiState.Loading) {
                    it.copy(
                        songs = state,
                        sections = sections,
                        thumbnailUrl = artwork ?: it.thumbnailUrl,
                        title = name ?: it.title,
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
                // A page with nothing new on it means the feed has looped back
                // rather than run dry with a token still attached.
                if (added.isEmpty()) return@launch
                _detailStack.value = stack.toMutableList().also {
                    it[index] = current.copy(songs = UiState.Success(existing + added))
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
    }

    fun signOut() {
        authStore.signOut()
        Innertube.cookie = null
        _signedIn.value = false
        _account.value = null
        _library.value = UiState.Loading
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
