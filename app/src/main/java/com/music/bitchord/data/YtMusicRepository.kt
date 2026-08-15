package com.music.bitchord.data

import android.util.Log
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.Account
import com.music.bitchord.data.model.ArtistPage
import com.music.bitchord.data.model.HomeFeed
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/** Suspend API over Innertube. Every call returns a Result so the UI can show a real error. */
object YtMusicRepository {

    private const val TAG = "BitChord"

    /**
     * The personalised feed, led by what was actually just played and padded
     * out with new releases.
     *
     * FEmusic_home alone is thin when signed out (three shelves), so extra
     * rows are pulled from FEmusic_new_releases, which carries genuinely
     * different content. Charts (Daily/Weekly, Trending) live under Explore
     * in the real app — see [explore] — not here. Titles are de-duped in
     * case the home feed already surfaced the same shelf.
     *
     * FEmusic_home's own continuation token comes back, for [moreHome] —
     * signed in, it keeps paging into mood mixes and more personalised
     * shelves the same way the official app does as you scroll; signed out
     * it's empty and there's nothing more to fetch.
     */
    suspend fun home(): Result<HomeFeed> = call("home") {
        coroutineScope {
            val recent = async { runCatching { recentlyPlayed() }.getOrNull() }
            val homeRaw = async { Innertube.browse("FEmusic_home") }
            val newReleases = async { runCatching { shelvesOf("FEmusic_new_releases") }.getOrDefault(emptyList()) }
            val home = homeRaw.await()
            val shelves = listOfNotNull(recent.await()) +
                InnertubeParser.parseHome(home) +
                newReleases.await()
            HomeFeed(shelves, InnertubeParser.continuationToken(home))
        }
    }

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
        if (Innertube.cookie == null) return null
        val songs = InnertubeParser.collectSongsDeep(Innertube.browse(HISTORY))
            // A track played three times today is three rows in the feed.
            .distinctBy { it.videoId }
            .take(RECENT_LIMIT)
        if (songs.isEmpty()) return null
        return HomeShelf(
            title = RECENT_TITLE,
            items = songs.map {
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

    private const val HISTORY = "FEmusic_history"
    private const val RECENT_TITLE = "Recently played"

    /** Enough to scroll through, short of turning the shelf into the history page. */
    private const val RECENT_LIMIT = 20

    private suspend fun shelvesOf(browseId: String): List<HomeShelf> =
        InnertubeParser.parseHome(Innertube.browse(browseId))

    /**
     * Explore: moods & genres from FEmusic_explore, plus the Daily/Weekly/
     * Trending charts, which YouTube Music serves from a separate browse id
     * and surfaces under Explore rather than Home.
     */
    suspend fun explore(): Result<List<HomeShelf>> = call("explore") {
        coroutineScope {
            val feeds = listOf("FEmusic_explore", "FEmusic_charts")
                .map { id -> async { runCatching { shelvesOf(id) }.getOrDefault(emptyList()) } }
                .awaitAll()
            val seen = mutableSetOf<String>()
            feeds.flatten().filter { seen.add(it.title.lowercase()) }
        }
    }

    suspend fun search(query: String, filter: SearchFilter): Result<List<SearchResult>> =
        call("search:${filter.name}") {
            InnertubeParser.parseSearch(Innertube.search(query, filter.params))
        }

    /**
     * The catalogue (audio-only) release of a music-video upload, found the
     * same way the "Switch to audio" toggle in the real app would land on
     * it: searching the title and artist and taking the closest song match.
     * Called before a video-tagged [Song] ever reaches the queue, so
     * playback, the mini player/notification, and YouTube's own history all
     * see the audio track — never the video upload's title, art or id.
     *
     * Returns [song] unchanged when it isn't a video, or when nothing better
     * turns up — playing the video's own audio track beats failing playback
     * outright, and [song] is what a queue restore or offline retry falls
     * back to as well.
     *
     * [search] already drops video rows from its results (see
     * [InnertubeParser.parseSearch]), so every candidate here is audio-only
     * without a second check.
     */
    suspend fun resolveAudio(song: Song): Song {
        if (!song.isVideo) return song
        val candidates = search("${song.title} ${song.artist}", SearchFilter.SONGS)
            .getOrNull()
            ?.filterIsInstance<SearchResult.Track>()
            ?.map { it.song }
            .orEmpty()
        val normalizedTitle = normalizeTitle(song.title)
        return candidates.firstOrNull { normalizeTitle(it.title) == normalizedTitle }
            ?: candidates.firstOrNull()
            ?: song
    }

    /** Strips the "(Official Video)" / "(Lyrical)" noise a title match would trip on. */
    private fun normalizeTitle(title: String): String =
        title.lowercase().replace(Regex("""[(\[][^)\]]*[)\]]"""), "").trim()

    /** Signed-in profile for the settings header. Null when signed out. */
    suspend fun account(): Result<Account> = call("account") {
        InnertubeParser.parseAccount(Innertube.accountMenu())
            ?: error("No account details")
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
                        val items = runCatching {
                            InnertubeParser.parseLibraryItems(Innertube.browse(browseId))
                        }.getOrDefault(emptyList())
                        HomeShelf(title, items)
                    }
                }
                .awaitAll()
                .filter { it.items.isNotEmpty() }

            val likedSongs = liked.await()
            val likedIds = likedSongs.mapTo(HashSet()) { it.videoId }
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
     * it — null once there is nothing more.
     */
    data class SongPage(val songs: List<Song>, val continuation: String?)

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
        pageOf(Innertube.browse(browseId))
    }

    /** The page [SongPage.continuation] points at. */
    suspend fun moreSongs(token: String): Result<SongPage> = call("browse:more") {
        pageOf(Innertube.browseContinuation(token))
    }

    private fun pageOf(response: JsonObject) = SongPage(
        // One response can name the same track twice — an album page that
        // also carries a "you might also like" shelf, say. Collecting into a
        // map used to take care of that; paging by hand means saying so.
        songs = InnertubeParser.collectSongsDeep(response).distinctBy { it.videoId },
        continuation = InnertubeParser.continuationToken(response),
    )

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
            InnertubeParser.collectSongsDeep(response).forEach { out[it.videoId] = it }
            val token = InnertubeParser.continuationToken(response)
            if (token == null || page++ >= MAX_PAGES) break
            response = runCatching { Innertube.browseContinuation(token) }.getOrNull() ?: break
        }
        return out.values.toList()
    }

    const val MAX_PAGES = 10

    /** Liked Music: the `LM` auto-playlist, addressed as a playlist browse id. */
    private const val LIKED_MUSIC = "VLLM"

    /** Songs explicitly added to the library — distinct from Liked Music. */
    private const val LIBRARY_SONGS = "FEmusic_liked_videos"

    private val LIBRARY_FEEDS = listOf(
        "Playlists" to "FEmusic_liked_playlists",
        "Albums" to "FEmusic_liked_albums",
        "Artists" to "FEmusic_library_corpus_track_artists",
        "Subscriptions" to "FEmusic_library_corpus_artists",
        "Podcasts" to "FEmusic_library_non_music_audio_list",
    )

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
            runCatching { block() }
                // runCatching catches Throwable, cancellation included, which
                // would turn "the user typed another letter" into a failed
                // Result and put the abandoned request's error on screen.
                // Cancellation isn't this call's to answer for.
                .onFailure { if (it is CancellationException) throw it }
                .onSuccess { Log.d(TAG, "$label ok") }
                .onFailure { Log.w(TAG, "$label failed: ${it.message}") }
        }
}
