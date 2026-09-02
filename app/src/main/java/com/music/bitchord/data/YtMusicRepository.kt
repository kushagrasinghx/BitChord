package com.music.bitchord.data

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.Account
import com.music.bitchord.data.model.AccountChannel
import com.music.bitchord.data.model.ArtistPage
import com.music.bitchord.data.model.HomeFeed
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.LibraryState
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.SongMenu
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.sources.TrackMatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Suspend API over Innertube. Every call returns a Result so the UI can show a real error. */
object YtMusicRepository {

    private const val TAG = "BitChord"
    private val moodGenreShelfCache = ConcurrentHashMap<String, List<HomeShelf>>()

    /**
     * The core personalised feed. It stays deliberately independent from the
     * optional shelves below: callers can paint this response immediately,
     * rather than making the Play page wait for every supplementary endpoint.
     *
     * FEmusic_home's own continuation token comes back, for [moreHome] —
     * signed in, it keeps paging into mood mixes and more personalised
     * shelves the same way the official app does as you scroll; signed out
     * it's empty and there's nothing more to fetch.
     */
    suspend fun home(): Result<HomeFeed> = call("home") {
        val home = Innertube.browse("FEmusic_home")
        HomeFeed(InnertubeParser.parseHome(home), InnertubeParser.continuationToken(home))
    }

    /** Recently played is rendered independently, at the top of the Play page. */
    suspend fun homeRecentlyPlayed(): Result<HomeShelf?> = call("home:recent") { recentlyPlayed() }

    /** Extra Play shelves are independently fetchable so each can appear as soon as it arrives. */
    suspend fun homeSupplement(browseId: String): Result<List<HomeShelf>> = call("home:$browseId") {
        shelvesOf(browseId)
    }

    val HOME_SUPPLEMENT_BROWSE_IDS = listOf(
        "FEmusic_new_releases",
        "FEmusic_explore",
    )

    /**
     * More Home shelves past [home]'s first page, following FEmusic_home's
     * own continuation — the lever the official app pulls as you scroll
     * rather than a fixed one-shot page. "Recently played" and
     * FEmusic_new_releases are one-shot and don't participate.
     */
    suspend fun moreHome(token: String): Result<HomeFeed> = call("home:more") {
        val response = Innertube.browseContinuation(token)
        HomeFeed(
            shelves = InnertubeParser.parseHomeContinuation(response),
            continuation = InnertubeParser.continuationToken(response),
        )
    }

    /**
     * The lead shelf: the account's listening history, newest first.
     *
     * YouTube's home already carries a "Listen again", but it ranks by how
     * *often* something has been played rather than how recently — so it keeps
     * leading with last month's favourites for days after a change of mood,
     * which reads as the feed being broken. The history feed reflects a play
     * the moment it's registered, so it's what the top of the page is built
     * from. YouTube's own shelf stays below, where its ranking is a feature.
     *
     * Signed-in only; there is no history to read as a guest.
     */
    private suspend fun recentlyPlayed(): HomeShelf? {
        val ytSongs = if (Innertube.cookie != null) {
            runCatching { fetchHistory() }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val allSongs = ytSongs.distinctBy { it.videoId }.take(RECENT_LIMIT)
        if (allSongs.isEmpty()) return null
        return HomeShelf(
            title = RECENT_TITLE,
            items = allSongs.map {
                ShelfItem(
                    title = it.title,
                    subtitle = it.artist,
                    thumbnailUrl = it.thumbnailUrl,
                    videoId = it.videoId,
                    browseId = null,
                )
            },
        )
    }

    /**
     * The raw fetch behind both [recentlyPlayed] and [history]: the account's
     * listening history, newest first, one row per play collapsed to one row
     * per track.
     *
     * A track played three times today is three rows in the feed — what that
     * dedupe costs is the times, which is fine for "what you have been
     * listening to" but would matter for a log. YouTube's own page groups them
     * under Today and Yesterday headings that the shelf parser doesn't carry
     * through either.
     */
    private suspend fun fetchHistory(): List<Song> =
        InnertubeParser.collectSongsDeep(Innertube.browse(HISTORY)).distinctBy { it.videoId }

    /**
     * The account's listening history, in the order YouTube Music keeps it.
     *
     * The same feed [recentlyPlayed] reads, without the truncation: that one is
     * a shelf on the home page and stops at [RECENT_LIMIT] so it stays a shelf,
     * whereas this is the page you open when twenty is not enough.
     */
    suspend fun history(): Result<List<Song>> = call("history") { fetchHistory() }

    /** Account listening history for the Recents feed. */
    suspend fun recents(): Result<List<Song>> = call("recents") {
        if (Innertube.cookie != null) {
            val history = runCatching { fetchHistory() }.getOrDefault(emptyList())
            if (history.isNotEmpty()) return@call history
        }
        emptyList()
    }

    /**
     * Recommended discovery tracks for Quick Picks, strictly excluding listening history and recents.
     */
    suspend fun quickPicks(excludeSongIds: Set<String> = emptySet()): Result<List<Song>> = call("quickPicks") {
        val homeRaw = runCatching { Innertube.browse("FEmusic_home") }.getOrNull()
        val shelves = if (homeRaw != null) InnertubeParser.parseHome(homeRaw) else emptyList()

        // 1. Direct "Quick picks" or "Mix" or "Recommendations" shelf
        val qpShelf = shelves.firstOrNull {
            it.title.contains("quick", ignoreCase = true) ||
                it.title.contains("pick", ignoreCase = true) ||
                it.title.contains("mix", ignoreCase = true) ||
                it.title.contains("recommend", ignoreCase = true)
        }
        val qpSongs = qpShelf?.items?.mapNotNull { item ->
            item.videoId?.takeUnless { it in excludeSongIds }?.let { vid ->
                Song(videoId = vid, title = item.title, artist = item.subtitle, thumbnailUrl = item.thumbnailUrl)
            }
        }.orEmpty()
        if (qpSongs.isNotEmpty()) return@call qpSongs

        // 2. All shelves on Home (excluding shelves mentioning recent/history, and skipping top shelf if multiple exist)
        val candidateShelves = if (shelves.size > 1) {
            shelves.drop(1).filterNot {
                it.title.contains("recent", ignoreCase = true) ||
                    it.title.contains("history", ignoreCase = true) ||
                    it.title.contains("listen again", ignoreCase = true)
            }
        } else shelves

        val homeShelfSongs = candidateShelves.flatMap { shelf ->
            shelf.items.mapNotNull { item ->
                item.videoId?.takeUnless { it in excludeSongIds }?.let { vid ->
                    Song(videoId = vid, title = item.title, artist = item.subtitle, thumbnailUrl = item.thumbnailUrl)
                }
            }
        }.distinctBy { it.videoId }

        if (homeShelfSongs.isNotEmpty()) return@call homeShelfSongs

        // 3. Any shelf items on Home that have videoId
        val allHomeTrackSongs = shelves.flatMap { shelf ->
            shelf.items.mapNotNull { item ->
                item.videoId?.let { vid ->
                    Song(videoId = vid, title = item.title, artist = item.subtitle, thumbnailUrl = item.thumbnailUrl)
                }
            }
        }.distinctBy { it.videoId }

        val uniqueAllHome = allHomeTrackSongs.filterNot { it.videoId in excludeSongIds }
        if (uniqueAllHome.isNotEmpty()) return@call uniqueAllHome

        // 4. Explore / New releases
        val explore = runCatching { shelvesOf("FEmusic_new_releases") }.getOrDefault(emptyList())
        val exploreSongs = explore.flatMap { shelf ->
            shelf.items.mapNotNull { item ->
                item.videoId?.takeUnless { it in excludeSongIds }?.let { vid ->
                    Song(videoId = vid, title = item.title, artist = item.subtitle, thumbnailUrl = item.thumbnailUrl)
                }
            }
        }.distinctBy { it.videoId }

        if (exploreSongs.isNotEmpty()) return@call exploreSongs

        // 5. Fallback if everything was filtered out
        allHomeTrackSongs.ifEmpty { exploreSongs }
    }

    private const val HISTORY = "FEmusic_history"
    private const val RECENT_TITLE = "Recents"

    /** Enough to scroll through, short of turning the shelf into the history page. */
    private const val RECENT_LIMIT = 20

    private suspend fun shelvesOf(browseId: String): List<HomeShelf> =
        InnertubeParser.parseHome(Innertube.browse(browseId))

    /** The server-defined mood and genre categories used by Explore. */
    suspend fun moodAndGenres(): Result<List<MoodGenreSection>> = call("moods-and-genres") {
        InnertubeParser.parseMoodAndGenres(Innertube.browse("FEmusic_moods_and_genres"))
    }

    /**
     * The playlist shelves behind one mood/genre category. This result also
     * supplies its tile artwork, so caching it avoids a second request when a
     * user taps a category whose cover has already appeared.
     */
    suspend fun moodGenreShelves(browseId: String, params: String?): Result<List<HomeShelf>> {
        val key = "$browseId:${params.orEmpty()}"
        moodGenreShelfCache[key]?.let { return Result.success(it) }
        return call("mood-genre:$browseId") {
            InnertubeParser.parseHome(Innertube.browse(browseId, params))
        }.also { result -> result.getOrNull()?.let { moodGenreShelfCache.putIfAbsent(key, it) } }
    }

    /** A category card borrows the first real cover from the playlists it opens. */
    suspend fun moodGenreArtwork(browseId: String, params: String?): Result<String?> =
        moodGenreShelves(browseId, params).map { shelves ->
            shelves.asSequence().flatMap { it.items.asSequence() }
                .mapNotNull(ShelfItem::thumbnailUrl)
                .firstOrNull()
        }

    /** One page of a search response, with the token for its next page if present. */
    data class SearchPage(
        val rows: List<SearchResult>,
        val continuation: String?,
    )

    /**
     * Fetches only the first search page. Publishing it immediately keeps the
     * search responsive; the UI asks [searchContinuation] for later pages as
     * the listener reaches the end of the list.
     */
    suspend fun searchPage(query: String, filter: SearchFilter): Result<SearchPage> =
        call("search:${filter.name}") {
            InnertubeParser.parseSearchPage(
                Innertube.search(query, filter.params),
                includeVideos = filter == SearchFilter.VIDEOS,
            ).let { page ->
                SearchPage(page.rows.distinctBy { it.identityKey() }, page.continuation)
            }
        }

    /** Fetches the next page of a search only when the result list needs it. */
    suspend fun searchContinuation(token: String, filter: SearchFilter): Result<SearchPage> = call("search:continuation") {
        InnertubeParser.parseSearchPage(
            Innertube.searchContinuation(token),
            includeVideos = filter == SearchFilter.VIDEOS,
        ).let { page ->
            SearchPage(page.rows.distinctBy { it.identityKey() }, page.continuation)
        }
    }

    /**
     * Compatibility helper for callers that need candidates but not a scrolling
     * result screen. Those callers need the first, most relevant page only.
     */
    suspend fun search(query: String, filter: SearchFilter): Result<List<SearchResult>> =
        searchPage(query, filter).map { it.rows }

    private fun SearchResult.identityKey(): String = when (this) {
        is SearchResult.TopTrack -> "v:${song.videoId}"
        is SearchResult.Track -> "v:${song.videoId}"
        is SearchResult.Browse -> "b:${item.browseId}"
    }

    /**
     * What YouTube Music would suggest completing [input] to, for the search
     * field's typeahead. Unfiltered on purpose: a suggestion is a query, and
     * which tab it is then run against is the user's to pick afterwards.
     */
    suspend fun searchSuggestions(input: String): Result<List<String>> =
        call("suggest") {
            InnertubeParser.parseSearchSuggestions(Innertube.searchSuggestions(input))
        }

    /**
     * The catalogue (audio-only) release of a music-video upload, found the
     * same way the "Switch to audio" toggle in the real app would land on
     * it: searching the title and artist and taking the closest song match.
     * Called before a video-tagged [Song] ever reaches the queue, so
     * playback, the mini player/notification, and YouTube's own history all
     * see the audio track — never the video upload's title, art or id.
     *
     * Matched through [TrackMatcher] rather than a bare title compare, for
     * the same reason [SourceResolver][com.music.bitchord.data.sources.SourceResolver]
     * does: a query for a niche title can come back with nothing that is
     * really the recording, and taking the first row regardless was landing
     * on a same-language, wrong-song hit — a Telugu folk video resolving to
     * an unrelated devotional track was reported from exactly this path.
     * [TrackMatcher.best] returning null is a normal answer, not a failure to
     * work around.
     *
     * Returns [song] unchanged when it isn't a video, or when nothing better
     * turns up — playing the video's own audio track beats guessing at a
     * substitute. This is deliberately an explicit action from the player,
     * never part of normal queueing or playback.
     *
     * [search] already drops video rows from its results (see
     * [InnertubeParser.parseSearch]), so every candidate here is audio-only
     * without a second check.
     */
    suspend fun resolveAudio(song: Song): Song {
        if (!song.isVideo) return song
        val target = TrackMatcher.targetOf(song)
        for (query in TrackMatcher.queries(target)) {
            val candidates = search(query, SearchFilter.SONGS)
                .getOrNull()
                ?.filterIsInstance<SearchResult.Track>()
                ?.map { it.song }
                .orEmpty()
            TrackMatcher.best(candidates, target)?.let { match ->
                Log.d(TAG, "audio switch: '${song.title}' -> '${match.title}' ($query)")
                return match
            }
            // Music-video timing is visual timing, not the audio release's
            // timing. The manual switch may therefore use the exact official
            // song/artist match even when the video has a long intro or outro.
            TrackMatcher.bestOfficialAudioForVideo(candidates, target)?.let { match ->
                Log.d(TAG, "audio switch: accepted video/runtime drift '${song.title}' -> '${match.title}' ($query)")
                return match
            }
        }
        Log.w(TAG, "audio switch: no official song match for '${song.title}' by '${song.artist}'")
        return song
    }

    /** Signed-in profile for the settings header. Null when signed out. */
    suspend fun account(): Result<Account> = call("account") {
        InnertubeParser.parseAccount(Innertube.accountMenu())
            ?: error("No account details")
    }

    /**
     * The channels this login can act as — its own, plus any brand channels.
     *
     * Two endpoints are asked in turn because either can come back with an
     * envelope holding no `accountItem` at all, and the two do not fail
     * together: `accounts_list` is the first-party route and the switcher is
     * what youtube.com's own avatar menu uses. An empty list from the first is
     * not an answer, it is a shape this parser didn't recognise, so it is
     * treated the same as a failure and the other route is tried.
     */
    suspend fun accountChannels(): Result<List<AccountChannel>> = call("channels") {
        val viaInnertube = runCatching {
            InnertubeParser.parseAccountChannels(Innertube.accountsList())
        }.onFailure { Log.w(TAG, "accounts_list unavailable: ${it.message}") }
            .getOrNull()
            .orEmpty()
        if (viaInnertube.isNotEmpty()) return@call viaInnertube
        InnertubeParser.parseAccountChannels(Innertube.accountSwitcher())
    }

    /**
     * The whole library in one shot — requires a signed-in session.
     *
     * YouTube Music has no single "my library" feed: Liked Music is the `LM`
     * auto-playlist, the songs added to the library are a separate feed, and
     * every saved collection has its own browse id. They're fetched in
     * parallel and a feed that fails or is simply empty (a fresh account has
     * no saved albums) is dropped rather than failing the whole page.
     */
    suspend fun library(): Result<LibraryPage> = call("library") {
        coroutineScope {
            val liked = async { runCatching { songsPaged(LIKED_MUSIC) }.getOrDefault(emptyList()) }
            val added = async { runCatching { songsPaged(LIBRARY_SONGS) }.getOrDefault(emptyList()) }
            val shelves = LIBRARY_FEEDS
                .map { (title, browseId) ->
                    async {
                        HomeShelf(title, runCatching { libraryItemsPaged(browseId) }.getOrDefault(emptyList()))
                    }
                }
                .awaitAll()
                .filter { it.items.isNotEmpty() }

            val likedSongs = liked.await()
            val likedIds = likedSongs.mapTo(HashSet()) { it.videoId }
            LikeState.seedLiked(likedIds)
            LibraryPage(
                likedSongs = likedSongs,
                // Thumbs-up'd tracks are also in the library feed; only what
                // the "Liked Music" list doesn't already cover is worth a
                // second section.
                librarySongs = added.await().filterNot { it.videoId in likedIds },
                shelves = shelves,
            )
        }
    }

    /**
     * What YouTube Music would play on after [videoId]. Feeds AutoPlay; the
     * seed track itself comes back first, so callers filter what they have.
     */
    suspend fun radio(videoId: String): Result<List<Song>> = call("radio:$videoId") {
        InnertubeParser.parseWatchQueue(Innertube.next(videoId))
    }

    /**
     * The artist and album pages a track links out to.
     *
     * Search rows carry them, but home cards and anything already sitting in a
     * queue often don't — and the credits in the player have to lead somewhere
     * either way. A track's own watch queue entry always names both.
     */
    suspend fun trackLinks(videoId: String): Result<Song> = call("links:$videoId") {
        InnertubeParser.parseWatchQueue(Innertube.next(videoId))
            .firstOrNull { it.videoId == videoId }
            ?: error("no watch entry for $videoId")
    }

    /**
     * One page of a browse feed's tracks, and the token for the page after
     * it — null once there is nothing more. [suggested] is only ever
     * non-empty for a playlist page — see [InnertubeParser.parsePlaylistShelf].
     */
    data class SongPage(
        val songs: List<Song>,
        val continuation: String?,
        val suggested: List<Song> = emptyList(),
        /**
         * Whether the release this page describes is in the library. Only the
         * first page can answer — a continuation carries rows and nothing else
         * — so it is null from [moreSongs] and must not overwrite what
         * [browseSongs] already established.
         */
        val library: LibraryState? = null,
        /**
         * Whether this page's playlist is one the account made rather than one
         * it saved — see [InnertubeParser.parsePlaylistOwned]. Null for an
         * album, a continuation, or a page that doesn't say.
         */
        val owned: Boolean? = null,
        /**
         * What the page calls itself — only needed by callers that opened it
         * with nothing but a browse id, i.e. a tapped link. Null on a
         * continuation, which carries rows and no header.
         */
        val header: InnertubeParser.BrowseHeader? = null,
        /**
         * The editorial blurb YouTube Music writes for the release, when it
         * has one — see [InnertubeParser.parseDescription]. Null from a
         * continuation, same as [header].
         */
        val description: String? = null,
    )

    /**
     * The first page of an album/playlist's tracks, and nothing more.
     *
     * Deliberately not the whole list. Following every continuation before
     * returning meant a long playlist spent up to ten round trips showing a
     * spinner, when every row needed to fill the first screenful was in the
     * first response. The rest arrives behind a page that is by then already
     * being read — see [moreSongs].
     */
    suspend fun browseSongs(browseId: String): Result<SongPage> = call("browse:$browseId") {
        val response = Innertube.browse(browseId)
        val page = pageOf(response)
        // Only a playlist has an owner in the sense that matters — see
        // parsePlaylistOwned — and only its own first response can be asked.
        if (!browseId.startsWith("VL")) page
        else page.copy(owned = InnertubeParser.parsePlaylistOwned(response))
    }

    /** The page [SongPage.continuation] points at. */
    suspend fun moreSongs(token: String): Result<SongPage> = call("browse:more") {
        pageOf(Innertube.browseContinuation(token))
    }

    /**
     * Whether [browseId] is a playlist the account made — see
     * [InnertubeParser.parsePlaylistOwned].
     *
     * The same question [browseSongs] answers on the way past, asked on its own
     * by whatever needs it without a page open: holding a playlist card offers
     * Rename and Delete, and the card itself cannot say whether either applies.
     * The rows it fetches are thrown away, which is the price of one request for
     * a menu that would otherwise have to guess.
     */
    suspend fun playlistOwned(browseId: String): Result<Boolean?> = call("owner:$browseId") {
        InnertubeParser.parsePlaylistOwned(Innertube.browse(browseId))
    }

    private fun pageOf(response: JsonObject): SongPage {
        val library = InnertubeParser.parseLibraryState(response)
        val header = InnertubeParser.parseBrowseHeader(response)
        // A playlist page is scoped to its own shelf so its "Suggested
        // tracks" never read as songs the user added — see
        // parsePlaylistShelf. Anything else (album, library, history) has no
        // such shelf, and falls back to the layout-agnostic walk.
        InnertubeParser.parsePlaylistShelf(response)?.let { shelf ->
            return SongPage(shelf.songs, shelf.continuation, shelf.suggested, library, header = header)
        }
        return SongPage(
            // One response can name the same track twice — an album page that
            // also carries a "you might also like" shelf, say. Collecting into a
            // map used to take care of that; paging by hand means saying so.
            songs = InnertubeParser.collectSongsDeep(response).distinctBy { it.videoId },
            continuation = InnertubeParser.continuationToken(response),
            library = library,
            header = header,
            description = InnertubeParser.parseDescription(response),
        )
    }

    /**
     * The complete track listing behind an album or playlist browse id.
     *
     * The whole list rather than [browseSongs]' first page, because the callers
     * are the ones that act on all of it at once — "Add to queue" on a card
     * whose page was never opened. Queueing the first hundred rows of a
     * three-hundred-track playlist and calling it the playlist would be a
     * quieter kind of wrong than failing outright.
     *
     * Takes as long as the list is long — see [songsPaged].
     */
    suspend fun allSongs(browseId: String): Result<List<Song>> = call("all:$browseId") {
        songsPaged(browseId).ifEmpty { error("No tracks here") }
    }

    /**
     * Every track behind a browse id, following continuations.
     *
     * A playlist page returns its first ~100 rows and a token for the rest, so
     * a long list otherwise arrives silently truncated. Capped at
     * [MAX_PAGES] so a runaway feed can't hold the UI open forever, and a
     * failed page keeps whatever was already collected.
     *
     * Holds its caller until the last page lands, so it belongs behind things
     * nobody is watching — the library sync, an artist's back catalogue. For
     * anything a screen is waiting on, use [browseSongs] and [moreSongs].
     */
    private suspend fun songsPaged(browseId: String): List<Song> {
        val out = LinkedHashMap<String, Song>()
        var response = Innertube.browse(browseId)
        var page = 1
        while (true) {
            // Same shelf-scoping as pageOf: a playlist (Liked Music and the
            // Library Songs auto-playlist included) is read from its own
            // shelf so a trailing "Suggested tracks" shelf never joins in.
            val shelf = InnertubeParser.parsePlaylistShelf(response)
            (shelf?.songs ?: InnertubeParser.collectSongsDeep(response)).forEach { out[it.videoId] = it }
            val token = shelf?.continuation ?: InnertubeParser.continuationToken(response)
            if (token == null || page++ >= MAX_PAGES) break
            response = runCatching { Innertube.browseContinuation(token) }.getOrNull() ?: break
        }
        return out.values.toList()
    }

    /**
     * Every saved library card behind a feed browse id, following continuations.
     *
     * The library shelves are capped by YouTube's first page just like search:
     * playlists, albums and artists often stop at about twenty-five rows unless
     * their feed continuation is followed. These are background library loads,
     * so collecting the full bounded set before publishing is preferable to a
     * shelf that looks complete and silently is not.
     */
    private suspend fun libraryItemsPaged(browseId: String): List<ShelfItem> {
        val out = LinkedHashMap<String, ShelfItem>()
        var response = Innertube.browse(browseId)
        var page = 1
        while (true) {
            val parsed = InnertubeParser.parseLibraryItemPage(response)
            parsed.items.forEach { item ->
                val key = item.browseId ?: item.videoId ?: "${item.title}\n${item.subtitle}"
                out.putIfAbsent(key, item)
            }
            val token = parsed.continuation ?: break
            if (page++ >= MAX_PAGES) break
            response = runCatching { Innertube.browseContinuation(token) }.getOrNull() ?: break
        }
        return out.values.toList()
    }

    const val MAX_PAGES = 10

    /**
     * Liked Music: the `LM` auto-playlist, addressed as a playlist browse id.
     * Public because it is also the page a track has to disappear from the
     * moment it stops being liked — see MainViewModel's `dropFromLikedLists`.
     */
    const val LIKED_MUSIC = "VLLM"

    /** Songs explicitly added to the library — distinct from Liked Music. */
    private const val LIBRARY_SONGS = "FEmusic_liked_videos"

    /** Saved and own playlists; also what the "add to playlist" picker lists. */
    private const val LIBRARY_PLAYLISTS = "FEmusic_liked_playlists"

    /**
     * What the playlists shelf is called in a [LibraryPage].
     *
     * Coined here, and named here rather than spelt out at each use, because it
     * is the only shelf in the library anything else looks for by name: it is
     * the one the create tile leads (see LibraryScreen) and the one a rename or
     * a delete has to reach into (see MainViewModel's `editPlaylistShelf`).
     * Three copies of a bare "Playlists" is three places a retitling silently
     * turns those features off.
     */
    const val PLAYLISTS_SHELF = "Playlists"

    private val LIBRARY_FEEDS = listOf(
        PLAYLISTS_SHELF to LIBRARY_PLAYLISTS,
        "Albums" to "FEmusic_liked_albums",
        "Artists" to "FEmusic_library_corpus_track_artists",
        "Subscriptions" to "FEmusic_library_corpus_artists",
        "Podcasts" to "FEmusic_library_non_music_audio_list",
    )

    // ---- Writes -------------------------------------------------------------

    /**
     * The account's own state for one track — rating and library membership.
     *
     * Deliberately a lookup rather than something cached with the [Song]: a
     * track reaching the player through the queue has been round-tripped
     * through a MediaItem, which carries an id and little else, and the
     * feedback tokens are per-row anyway. Fetched when a menu is opened, which
     * is the only moment the answer is looked at.
     */
    suspend fun songMenu(videoId: String): Result<SongMenu> = call("menu:$videoId") {
        InnertubeParser.parseSongMenu(Innertube.next(videoId), videoId)
            ?: error("no menu for $videoId")
    }

    suspend fun rate(videoId: String, status: LikeStatus): Result<Unit> =
        call("rate:$videoId") { Innertube.rate(videoId, status) }

    /** Adds or removes a track from the library; [token] says which. */
    suspend fun setLibraryStatus(token: String): Result<Unit> =
        call("library:feedback") { Innertube.sendFeedback(token) }

    /**
     * Saves an album or playlist to the library, or removes it. [playlistId] is
     * the one the page named — see [LibraryState].
     */
    suspend fun setSaved(playlistId: String, saved: Boolean): Result<Unit> =
        call("library:$playlistId") { Innertube.ratePlaylist(playlistId, saved) }

    /**
     * The playlists a track can be added to. Paged because accounts with long
     * playlist collections otherwise lose everything past YouTube's first
     * library-feed response.
     */
    suspend fun userPlaylists(): Result<List<UserPlaylist>> = call("playlists") {
        InnertubeParser.parseUserPlaylists(libraryItemsPaged(LIBRARY_PLAYLISTS))
    }

    /**
     * All playlists in user library (including Liked Music and saved playlists).
     */
    suspend fun libraryPlaylists(): Result<List<ShelfItem>> = call("libraryPlaylists") {
        InnertubeParser.parseLibraryItems(Innertube.browse(LIBRARY_PLAYLISTS))
    }

    /** Creates a playlist, optionally seeded with [videoIds]; returns its id. */
    suspend fun createPlaylist(
        title: String,
        privacy: PlaylistPrivacy,
        videoIds: List<String> = emptyList(),
    ): Result<String> = call("playlist:create") {
        Innertube.createPlaylist(title, privacy, videoIds = videoIds)
    }

    /**
     * Adds tracks to a playlist. Succeeds with the per-entry ids YouTube minted
     * for them — see [Innertube.addToPlaylist]. A caller with nothing on screen
     * to update can ignore the map; one splicing the row into a playlist it is
     * looking at needs it for the row's "remove".
     */
    suspend fun addToPlaylist(
        playlistId: String,
        videoIds: List<String>,
    ): Result<Map<String, String>> =
        call("playlist:add") { Innertube.addToPlaylist(playlistId, videoIds) }

    /** [entries] are (setVideoId, videoId) pairs — see [Song.setVideoId]. */
    suspend fun removeFromPlaylist(
        playlistId: String,
        entries: List<Pair<String, String>>,
    ): Result<Unit> = call("playlist:remove") {
        Innertube.removeFromPlaylist(playlistId, entries)
    }

    suspend fun renamePlaylist(playlistId: String, title: String): Result<Unit> =
        call("playlist:rename") { Innertube.renamePlaylist(playlistId, title) }

    suspend fun deletePlaylist(playlistId: String): Result<Unit> =
        call("playlist:delete") { Innertube.deletePlaylist(playlistId) }

    /**
     * Artist page. The landing page only lists ~5 songs, so the linked
     * "Top songs" playlist is fetched to fill the list out.
     */
    suspend fun artistPage(browseId: String): Result<ArtistPage> = call("artist:$browseId") {
        val page = InnertubeParser.parseArtistPage(Innertube.browse(browseId))
        val fullSongs = page.moreSongsBrowseId?.let { playlistId ->
            runCatching { songsPaged(playlistId) }.getOrNull()
        }
        if (!fullSongs.isNullOrEmpty()) page.copy(songs = fullSongs) else page
    }

    private suspend fun <T> call(label: String, block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching { block() }.recoverCatching { failure ->
                // Context cookies can rotate while a process is alive. Refresh
                // once and retry; never loop or silently sign the listener out.
                val rejected = failure.message?.contains("401") == true ||
                    failure.message?.contains("403") == true ||
                    failure.message?.contains("rejected", true) == true
                if (!rejected || Innertube.cookie == null) throw failure
                Log.w(TAG, "$label rejected; refreshing active session context once")
                Innertube.refreshSessionScope()
                block()
            }
                // runCatching catches Throwable, cancellation included, which
                // would turn "the user typed another letter" into a failed
                // Result and put the abandoned request's error on screen.
                // Cancellation isn't this call's to answer for.
                .onFailure { if (it is CancellationException) throw it }
                .onSuccess { Log.d(TAG, "$label ok") }
                .onFailure { Log.w(TAG, "$label failed: ${it.message}") }
        }
}
