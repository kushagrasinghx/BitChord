/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.innertube

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import moe.rukamori.archivetune.innertube.models.AccountChannel
import moe.rukamori.archivetune.innertube.models.AccountInfo
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.GridRenderer
import moe.rukamori.archivetune.innertube.models.MediaInfo
import moe.rukamori.archivetune.innertube.models.MusicCarouselShelfRenderer
import moe.rukamori.archivetune.innertube.models.MusicPlaylistShelfRenderer
import moe.rukamori.archivetune.innertube.models.MusicResponsiveListItemRenderer
import moe.rukamori.archivetune.innertube.models.MusicShelfRenderer
import moe.rukamori.archivetune.innertube.models.MusicTwoRowItemRenderer
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SearchSuggestions
import moe.rukamori.archivetune.innertube.models.SectionListRenderer
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_REMIX
import moe.rukamori.archivetune.innertube.models.YouTubeLocale
import moe.rukamori.archivetune.innertube.models.distinctByPlaylistEntry
import moe.rukamori.archivetune.innertube.models.getContinuation
import moe.rukamori.archivetune.innertube.models.getItems
import moe.rukamori.archivetune.innertube.models.oddElements
import moe.rukamori.archivetune.innertube.models.response.AccountMenuResponse
import moe.rukamori.archivetune.innertube.models.response.AddItemYouTubePlaylistResponse
import moe.rukamori.archivetune.innertube.models.response.BrowseResponse
import moe.rukamori.archivetune.innertube.models.response.BrowseResponse.ContinuationContents.SectionListContinuation
import moe.rukamori.archivetune.innertube.models.response.CreatePlaylistResponse
import moe.rukamori.archivetune.innertube.models.response.EditPlaylistResponse
import moe.rukamori.archivetune.innertube.models.response.GetQueueResponse
import moe.rukamori.archivetune.innertube.models.response.GetSearchSuggestionsResponse
import moe.rukamori.archivetune.innertube.models.response.GetTranscriptResponse
import moe.rukamori.archivetune.innertube.models.response.ImageUploadResponse
import moe.rukamori.archivetune.innertube.models.response.NextResponse
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.innertube.models.response.SearchResponse
import moe.rukamori.archivetune.innertube.pages.AlbumPage
import moe.rukamori.archivetune.innertube.pages.ArtistItemsContinuationPage
import moe.rukamori.archivetune.innertube.pages.ArtistItemsPage
import moe.rukamori.archivetune.innertube.pages.ArtistItemsPageLayout
import moe.rukamori.archivetune.innertube.pages.ArtistPage
import moe.rukamori.archivetune.innertube.pages.BrowseResult
import moe.rukamori.archivetune.innertube.pages.ChartsPage
import moe.rukamori.archivetune.innertube.pages.ExplorePage
import moe.rukamori.archivetune.innertube.pages.HistoryPage
import moe.rukamori.archivetune.innertube.models.EpisodeItem
import moe.rukamori.archivetune.innertube.models.PODCAST_SHOW_BROWSE_PREFIX
import moe.rukamori.archivetune.innertube.models.PodcastItem
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.innertube.pages.PodcastPage
import moe.rukamori.archivetune.innertube.pages.LibraryContinuationPage
import moe.rukamori.archivetune.innertube.pages.LibraryPage
import moe.rukamori.archivetune.innertube.pages.MoodAndGenres
import moe.rukamori.archivetune.innertube.pages.NewReleaseAlbumPage
import moe.rukamori.archivetune.innertube.pages.NextPage
import moe.rukamori.archivetune.innertube.pages.NextResult
import moe.rukamori.archivetune.innertube.pages.PlaylistContinuationPage
import moe.rukamori.archivetune.innertube.pages.PlaylistPage
import moe.rukamori.archivetune.innertube.pages.toSongItem
import moe.rukamori.archivetune.innertube.pages.RelatedPage
import moe.rukamori.archivetune.innertube.pages.SearchPage
import moe.rukamori.archivetune.innertube.pages.SearchResult
import moe.rukamori.archivetune.innertube.pages.SearchSuggestionPage
import moe.rukamori.archivetune.innertube.pages.SearchSummary
import moe.rukamori.archivetune.innertube.pages.SearchSummaryPage
import moe.rukamori.archivetune.innertube.proxy.RotatingProxyClient
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.Proxy
import kotlin.random.Random

/**
 * Parse useful data with [InnerTube] sending requests.
 * Modified from [ViMusic](https://github.com/vfsfitvnm/ViMusic)
 */
object YouTube {
    private const val BROWSE_ID_EXPLORE = "FEmusic_explore"
    private const val BROWSE_ID_NEW_RELEASE_ALBUMS = "FEmusic_new_releases_albums"
    private const val BROWSE_ID_MOODS_AND_GENRES = "FEmusic_moods_and_genres"
    private const val PLAYLIST_EDIT_STATUS_SUCCEEDED = "STATUS_SUCCEEDED"

    /**
     * Safety guard for the new-releases continuation chain (see
     * newReleaseAlbumsFromBrowsePage). Not a display cap: the chain ends on
     * its own when YouTube stops returning tokens; this only stops a
     * pathological loop.
     */
    private const val MAX_NEW_RELEASE_PAGES = 25
    private val playlistCoverResponseJson = Json { ignoreUnknownKeys = true }

    private val innerTube = InnerTube()
    private val accountSwitcherClient =
        WEB.copy(
            loginSupported = true,
            supportsCookieAuthentication = true,
        )
    private val mutableAuthState = MutableStateFlow(PlaybackAuthState.EMPTY)

    val authStateFlow: StateFlow<PlaybackAuthState> = mutableAuthState.asStateFlow()

    private val _historySyncEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val historySyncEvent: SharedFlow<Unit> = _historySyncEvent.asSharedFlow()

    fun notifyHistorySynced() {
        _historySyncEvent.tryEmit(Unit)
    }

    var authState: PlaybackAuthState
        get() = mutableAuthState.value
        set(value) {
            val normalized = value.normalized()
            mutableAuthState.value = normalized
            innerTube.applyAuthState(normalized)
        }

    var locale: YouTubeLocale
        get() = innerTube.locale
        set(value) {
            innerTube.locale = value
        }
    var visitorData: String?
        get() = authState.visitorData
        set(value) {
            authState = authState.copy(visitorData = value)
        }
    var dataSyncId: String?
        get() = authState.dataSyncId
        set(value) {
            authState = authState.copy(dataSyncId = value)
        }
    var cookie: String?
        get() = authState.cookie
        set(value) {
            authState = authState.copy(cookie = value)
        }
    var poToken: String?
        get() = authState.poToken
        set(value) {
            authState = authState.copy(poToken = value)
        }
    var webClientPoTokenEnabled: Boolean
        get() = authState.webClientPoTokenEnabled
        set(value) {
            authState = authState.copy(webClientPoTokenEnabled = value)
        }
    var poTokenGvs: String?
        get() = authState.poTokenGvs
        set(value) {
            authState = authState.copy(poTokenGvs = value)
        }
    var poTokenPlayer: String?
        get() = authState.poTokenPlayer
        set(value) {
            authState = authState.copy(poTokenPlayer = value)
        }
    var proxy: Proxy?
        get() = innerTube.proxy
        set(value) {
            innerTube.proxy = value
        }
    var proxyUsername: String?
        get() = innerTube.proxyUsername
        set(value) {
            innerTube.proxyUsername = value
        }
    var proxyPassword: String?
        get() = innerTube.proxyPassword
        set(value) {
            innerTube.proxyPassword = value
        }
    var dns: Dns
        get() = innerTube.dns
        set(value) {
            innerTube.dns = value
        }
    var streamBypassProxy: Boolean = false
    val streamProxy: Proxy?
        get() = if (streamBypassProxy) null else proxy
    val streamOkHttpProxy: Proxy
        get() = streamProxy ?: Proxy.NO_PROXY
    var useLoginForBrowse: Boolean
        get() = innerTube.useLoginForBrowse
        set(value) {
            innerTube.useLoginForBrowse = value
        }

    /**
     * True while the user has pinned an explicit YouTube Music region (Internet Settings →
     * YouTube Music region). Set it together with a [locale] `gl` override.
     *
     * Why it exists: `context.client.gl` alone does NOT move the personalised, region-sensitive
     * endpoints (`FEmusic_home`, search, charts, trending, new releases, moods & genres, explore).
     * For a signed-in user YouTube derives the effective country from the *account* and ignores
     * `gl`, so the home feed keeps coming back in the account's home country no matter what the
     * picker says. While this flag is set, those endpoints are sent fully anonymously — no cookie,
     * no `dataSyncId`, and no `visitorData` in the body or the `X-Goog-Visitor-Id` header — which
     * makes `gl` authoritative.
     *
     * Login-required endpoints (library, playlists, history, likes) are unaffected and keep using
     * the session; only discovery surfaces go anonymous. The trade-off is deliberate: a pinned
     * region means "show me that country's catalogue", which cannot coexist with account-level
     * personalisation.
     *
     * Callers must persist the corresponding preference and restore this flag on process start,
     * otherwise spoofing silently stops working after the first restart.
     */
    @Volatile
    var regionSpooferActive: Boolean = false

    /**
     * Account context to use for region-sensitive browse/search requests: false while a region is
     * pinned (see [regionSpooferActive]), true otherwise. Combined with the caller's own
     * `useAccountContext` so this can only ever *remove* account context, never add it.
     */
    private val regionSensitiveAccountContext: Boolean
        get() = !regionSpooferActive

    /**
     * Rotating free-proxy pool used by Internet Settings → "IP rotation". Fetches a public HTTP
     * proxy list, validates each candidate against `music.youtube.com/generate_204`, and keeps the
     * ones that answer; [InnerTube] then routes every InnerTube request through the pool, advancing
     * to the next live proxy on each transient failure.
     */
    val rotatingProxyClient = RotatingProxyClient()

    private val _ipRotationActiveCount = MutableStateFlow(0)

    /** Number of proxies currently live in the pool. 0 means rotation is off or nothing validated. */
    val ipRotationActiveCount: StateFlow<Int> = _ipRotationActiveCount.asStateFlow()

    /**
     * Fetches + validates the proxy pool and installs it. Network-bound (it probes every candidate),
     * so callers should show progress. Leaves rotation disabled if nothing validated, in which case
     * [ipRotationActiveCount] stays 0 and requests keep going out directly.
     */
    suspend fun enableIpRotation() {
        withContext(Dispatchers.IO) {
            rotatingProxyClient.fetchAndLoad()
            innerTube.proxySelector = rotatingProxyClient.selector()
            _ipRotationActiveCount.value = rotatingProxyClient.activeCount()
        }
    }

    /**
     * Advances to the next proxy, re-fetching the whole pool first if it has been whittled down to
     * one (or zero) live entries.
     */
    suspend fun refreshIpRotation() {
        withContext(Dispatchers.IO) {
            if (rotatingProxyClient.activeCount() <= 1) {
                rotatingProxyClient.fetchAndLoad()
            } else {
                rotatingProxyClient.rotate()
            }
            innerTube.proxySelector = rotatingProxyClient.selector()
            _ipRotationActiveCount.value = rotatingProxyClient.activeCount()
        }
    }

    /** Uninstalls the pool so requests go out directly (or via the static proxy, if configured). */
    fun disableIpRotation() {
        innerTube.proxySelector = null
        _ipRotationActiveCount.value = 0
    }

    fun currentPlaybackAuthState(): PlaybackAuthState = authState

    /**
     * Echo-Music's NewPipe stream-URL harvest (https://github.com/EchoMusicApp/Echo-Music,
     * GPL-3.0): `(itag, url)` pairs for every stream NewPipe's watch-page extraction can see.
     */
    fun getNewPipeStreamUrls(videoId: String): List<Pair<Int, String>> = NewPipeUtils.newPipeStreamUrls(videoId)

    /**
     * Echo-Music's stream substitution (`YouTube.newPipePlayer`). For an already-fetched player
     * response whose playability is OK, replaces every format's URL with the NewPipe-harvested
     * URL of the same itag (formats NewPipe could not resolve keep their own URL). Returns null
     * when the response is not playable or the harvest came back empty — Echo's callers then
     * keep the original response and resolve the URL through the cipher path instead.
     */
    suspend fun newPipePlayer(
        videoId: String,
        tempRes: PlayerResponse,
    ): PlayerResponse? {
        if (tempRes.playabilityStatus.status != "OK") {
            return null
        }

        val streamsList = getNewPipeStreamUrls(videoId)
        if (streamsList.isEmpty()) return null

        val decodedSigResponse =
            tempRes.copy(
                streamingData =
                    tempRes.streamingData?.copy(
                        formats =
                            tempRes.streamingData.formats?.map { format ->
                                format.copy(
                                    url = streamsList.find { it.first == format.itag }?.second ?: format.url,
                                )
                            },
                        adaptiveFormats =
                            tempRes.streamingData.adaptiveFormats.map { adaptiveFormat ->
                                adaptiveFormat.copy(
                                    url = streamsList.find { it.first == adaptiveFormat.itag }?.second ?: adaptiveFormat.url,
                                )
                            },
                    ),
            )

        val urlList =
            (
                decodedSigResponse.streamingData?.adaptiveFormats?.mapNotNull { it.url }?.toMutableList()
                    ?: mutableListOf()
            ).apply {
                decodedSigResponse.streamingData?.formats?.mapNotNull { it.url }?.let { addAll(it) }
            }

        return if (urlList.isNotEmpty()) {
            decodedSigResponse
        } else {
            null
        }
    }

    fun createDnsOverHttps(url: String): Dns {
        val bootstrapClient = OkHttpClient.Builder().build()
        return DnsOverHttps
            .Builder()
            .client(bootstrapClient)
            .url(url.toHttpUrl())
            .build()
    }

    private fun resolvePlayerPoToken(
        client: YouTubeClient,
        explicitPoToken: String?,
        videoId: String,
        authState: PlaybackAuthState,
    ): String? =
        authState.resolvePlayerPoToken(
            client = client,
            explicitPoToken = explicitPoToken,
            videoId = videoId,
        )

    fun hasLoginCookie(): Boolean = authState.hasLoginCookie

    fun hasPlaybackLoginContext(): Boolean = authState.hasPlaybackLoginContext

    internal fun resolveGvsPoToken(
        videoId: String? = null,
        authState: PlaybackAuthState = currentPlaybackAuthState(),
    ): String? = authState.resolveGvsPoToken(videoId = videoId)

    internal fun appendGvsPoToken(
        url: String,
        client: YouTubeClient? = null,
        videoId: String? = null,
        authState: PlaybackAuthState = currentPlaybackAuthState(),
    ): String {
        val token = authState.resolveGvsPoToken(client = client, videoId = videoId) ?: return url
        if (url.contains("pot=")) return url

        val separator = if (url.contains("?")) "&" else "?"
        return "$url${separator}pot=$token"
    }

    suspend fun searchSuggestions(query: String): Result<SearchSuggestions> =
        runCatching {
            val response = innerTube.getSearchSuggestions(WEB_REMIX, query).body<GetSearchSuggestionsResponse>()
            SearchSuggestions(
                queries =
                    response.contents
                        ?.getOrNull(0)
                        ?.searchSuggestionsSectionRenderer
                        ?.contents
                        ?.mapNotNull { content ->
                            content.searchSuggestionRenderer
                                ?.suggestion
                                ?.runs
                                ?.joinToString(separator = "") { it.text }
                        }.orEmpty(),
                recommendedItems =
                    response.contents
                        ?.getOrNull(1)
                        ?.searchSuggestionsSectionRenderer
                        ?.contents
                        ?.mapNotNull {
                            it.musicResponsiveListItemRenderer?.let { renderer ->
                                SearchSuggestionPage.fromMusicResponsiveListItemRenderer(renderer)
                            }
                        }.orEmpty(),
            )
        }

    suspend fun searchSummary(query: String): Result<SearchSummaryPage> =
        runCatching {
            val response =
                innerTube
                    .search(
                        WEB_REMIX,
                        query,
                        // Region-sensitive: the summary shelves are ranked per-country.
                        useAccountContext = regionSensitiveAccountContext,
                    ).body<SearchResponse>()
            val contents =
                response.contents
                    ?.tabbedSearchResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    .orEmpty()
            val topItems = mutableListOf<YTItem>()
            val summaries = mutableListOf<SearchSummary>()

            contents.forEach { content ->
                content.musicCardShelfRenderer?.let { renderer ->
                    topItems +=
                        listOfNotNull(SearchSummaryPage.fromMusicCardShelfRenderer(renderer))
                            .plus(
                                renderer.contents
                                    ?.mapNotNull { it.musicResponsiveListItemRenderer }
                                    ?.mapNotNull { SearchSummaryPage.fromMusicResponsiveListItemRenderer(it) }
                                    .orEmpty(),
                            )
                    return@forEach
                }

                content.itemSectionRenderer?.contents?.let { sectionContents ->
                    topItems +=
                        sectionContents.mapNotNull {
                            it.musicResponsiveListItemRenderer?.let { renderer ->
                                SearchSummaryPage.fromMusicResponsiveListItemRenderer(renderer)
                            }
                        }
                    summaries +=
                        sectionContents.mapNotNull { it.musicShelfRenderer?.toSearchSummary() }
                    return@forEach
                }

                content.musicShelfRenderer?.toSearchSummary()?.let(summaries::add)
            }

            SearchSummaryPage(
                summaries =
                    buildList {
                        topItems
                            .distinctBy { it.id }
                            .takeIf { it.isNotEmpty() }
                            ?.let { add(SearchSummary(title = "Top results", items = it)) }
                        addAll(summaries)
                    },
            )
        }

    suspend fun search(
        query: String,
        filter: SearchFilter,
        useAccountContext: Boolean = true,
    ): Result<SearchResult> =
        runCatching {
            val response =
                innerTube
                    .search(
                        client = WEB_REMIX,
                        query = query,
                        params = filter.value,
                        useAccountContext = useAccountContext && regionSensitiveAccountContext,
                    ).body<SearchResponse>()
            val contents =
                response.contents
                    ?.tabbedSearchResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    .orEmpty()
            val shelves =
                contents.flatMap { content ->
                    buildList {
                        content.musicShelfRenderer?.let { add(it) }
                        content.itemSectionRenderer
                            ?.contents
                            ?.mapNotNull { it.musicShelfRenderer }
                            ?.let { addAll(it) }
                    }
                }
            val inlineItems =
                contents.flatMap { content ->
                    content.itemSectionRenderer
                        ?.contents
                        ?.mapNotNull { it.musicResponsiveListItemRenderer }
                        .orEmpty()
                }
            SearchResult(
                items =
                    shelves
                        .flatMap { it.contents?.getItems().orEmpty() }
                        .plus(inlineItems)
                        .mapNotNull { SearchPage.toYTItem(it) }
                        .distinctBy { it.id },
                continuation =
                    shelves
                        .asSequence()
                        .mapNotNull { it.continuations?.getContinuation() ?: it.contents?.getContinuation() }
                        .firstOrNull(),
            )
        }

    suspend fun searchContinuation(
        continuation: String,
        useAccountContext: Boolean = true,
    ): Result<SearchResult> =
        runCatching {
            val response =
                innerTube
                    .search(
                        client = WEB_REMIX,
                        continuation = continuation,
                        useAccountContext = useAccountContext && regionSensitiveAccountContext,
                    ).body<SearchResponse>()
            val continuationPage = response.continuationContents?.musicShelfContinuation
            val items =
                continuationPage
                    ?.contents
                    ?.mapNotNull {
                        it.musicResponsiveListItemRenderer?.let { renderer -> SearchPage.toYTItem(renderer) }
                    }
                    ?: emptyList()
            SearchResult(
                items = items,
                continuation =
                    if (items.isEmpty()) {
                        null
                    } else {
                        continuationPage?.continuations?.getContinuation()
                            ?: continuationPage
                                ?.contents
                                ?.firstOrNull { it.continuationItemRenderer != null }
                                ?.continuationItemRenderer
                                ?.continuationEndpoint
                                ?.continuationCommand
                                ?.token
                    },
            )
        }

    private fun MusicShelfRenderer.toSearchSummary(): SearchSummary? {
        val items =
            contents
                ?.getItems()
                ?.mapNotNull { SearchSummaryPage.fromMusicResponsiveListItemRenderer(it) }
                ?.distinctBy { it.id }
                .orEmpty()
        if (items.isEmpty()) return null

        val title =
            title
                ?.runs
                ?.joinToString(separator = "") { it.text }
                ?.takeIf { it.isNotBlank() }
                ?: "Other"

        return SearchSummary(title = title, items = items)
    }

    suspend fun album(
        browseId: String,
        withSongs: Boolean = true,
    ): Result<AlbumPage> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, browseId).body<BrowseResponse>()
            val playlistId =
                AlbumPage.getPlaylistId(response)
                    ?: throw IllegalStateException("Missing album playlist id for $browseId")
            val albumTitle =
                AlbumPage.getTitle(response)
                    ?: throw IllegalStateException("Missing album title for $browseId")
            val albumArtists = AlbumPage.getArtists(response).takeIf { it.isNotEmpty() }
            val albumYear = AlbumPage.getYear(response)
            val albumThumbnail =
                AlbumPage.getThumbnailInfo(response)
                    ?: throw IllegalStateException("Missing album thumbnail url for $browseId")
            val albumItem =
                AlbumItem(
                    browseId = browseId,
                    playlistId = playlistId,
                    title = albumTitle,
                    artists = albumArtists,
                    year = albumYear,
                    thumbnail = albumThumbnail.normalizedUrl,
                    thumbnailWidth = albumThumbnail.width,
                    thumbnailHeight = albumThumbnail.height,
                    explicit = false, // TODO: Extract explicit badge for albums from YouTube response
                )
            val inlineSongs = if (withSongs) AlbumPage.getSongs(response, albumItem) else emptyList()
            val songs =
                if (withSongs) {
                    val fetchedSongs =
                        runCatching {
                            albumSongs(playlistId, albumItem).getOrThrow()
                        }.getOrElse { error ->
                            if (inlineSongs.isNotEmpty()) {
                                inlineSongs
                            } else {
                                throw error
                            }
                        }

                    if (fetchedSongs.isEmpty() && inlineSongs.isNotEmpty()) {
                        inlineSongs
                    } else {
                        fetchedSongs
                    }
                } else {
                    emptyList()
                }

            AlbumPage(
                album = albumItem,
                songs = songs,
                otherVersions =
                    response.contents
                        ?.twoColumnBrowseResultsRenderer
                        ?.secondaryContents
                        ?.sectionListRenderer
                        ?.contents
                        ?.mapNotNull { it.musicCarouselShelfRenderer }
                        ?.flatMap { it.contents }
                        ?.mapNotNull { it.musicTwoRowItemRenderer }
                        ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
                        ?.distinctBy { it.id }
                        .orEmpty(),
            )
        }

    suspend fun albumSongs(
        playlistId: String,
        album: AlbumItem? = null,
    ): Result<List<SongItem>> =
        runCatching {
            // Same "VL" double-prefix guard as #playlist — community/radio-style IDs can
            // already carry the prefix.
            val normalizedPlaylistId = playlistId.removePrefix("VL").removePrefix("VL")
            var response = innerTube.browse(WEB_REMIX, "VL$normalizedPlaylistId").body<BrowseResponse>()
            val songs = linkedMapOf<String, SongItem>()

            fun appendSongs(
                candidates: List<MusicResponsiveListItemRenderer>,
                parsedSongs: List<SongItem>,
                source: String,
            ): Boolean {
                if (candidates.isNotEmpty() && parsedSongs.isEmpty()) {
                    throw IllegalStateException("Unable to parse album songs from $source for playlist $playlistId")
                }

                val previousSize = songs.size
                parsedSongs.forEach { songs.putIfAbsent(it.id, it) }
                return songs.size > previousSize
            }

            appendSongs(
                candidates = AlbumPage.getSongRenderers(response),
                parsedSongs = AlbumPage.getSongs(response, album),
                source = "initial response",
            )

            var continuation = AlbumPage.getSongContinuation(response)
            val seenContinuations = mutableSetOf<String>()
            var requestCount = 0
            val maxRequests = 50 // Prevent excessive API calls

            var consecutiveEmptyResponses = 0
            while (continuation != null && requestCount < maxRequests) {
                if (continuation in seenContinuations) {
                    break
                }
                seenContinuations.add(continuation)
                requestCount++

                response =
                    innerTube
                        .browse(
                            client = WEB_REMIX,
                            continuation = continuation,
                        ).body<BrowseResponse>()

                val newSongCandidates = AlbumPage.getContinuationSongRenderers(response)
                val newSongs = AlbumPage.getContinuationSongs(response, album)
                val hasNewSongs =
                    if (newSongCandidates.isNotEmpty() || newSongs.isNotEmpty()) {
                        appendSongs(
                            candidates = newSongCandidates,
                            parsedSongs = newSongs,
                            source = "continuation response",
                        )
                    } else {
                        false
                    }

                if (!hasNewSongs) {
                    consecutiveEmptyResponses++
                    if (consecutiveEmptyResponses >= 2) break
                } else {
                    consecutiveEmptyResponses = 0
                }

                continuation = AlbumPage.getNextSongContinuation(response)
            }
            songs.values.toList()
        }

    suspend fun artist(browseId: String): Result<ArtistPage> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, browseId).body<BrowseResponse>()
            val immersiveHeader = response.header?.musicImmersiveHeaderRenderer
            val subscribeButtonRenderer = immersiveHeader?.subscriptionButton?.subscribeButtonRenderer
            val artistThumbnail =
                immersiveHeader?.thumbnail?.musicThumbnailRenderer?.getBestThumbnail()
                    ?: response.header
                        ?.musicVisualHeaderRenderer
                        ?.foregroundThumbnail
                        ?.musicThumbnailRenderer
                        ?.getBestThumbnail()
                    ?: response.header
                        ?.musicDetailHeaderRenderer
                        ?.thumbnail
                        ?.musicThumbnailRenderer
                        ?.getBestThumbnail()

            ArtistPage(
                artist =
                    ArtistItem(
                        id = browseId,
                        title =
                            immersiveHeader
                                ?.title
                                ?.runs
                                ?.firstOrNull()
                                ?.text
                                ?: response.header
                                    ?.musicVisualHeaderRenderer
                                    ?.title
                                    ?.runs
                                    ?.firstOrNull()
                                    ?.text
                                ?: response.header
                                    ?.musicHeaderRenderer
                                    ?.title
                                    ?.runs
                                    ?.firstOrNull()
                                    ?.text!!,
                        thumbnail = artistThumbnail?.normalizedUrl,
                        thumbnailWidth = artistThumbnail?.width,
                        thumbnailHeight = artistThumbnail?.height,
                        channelId = subscribeButtonRenderer?.channelId,
                        playEndpoint =
                            response.contents
                                ?.singleColumnBrowseResultsRenderer
                                ?.tabs
                                ?.firstOrNull()
                                ?.tabRenderer
                                ?.content
                                ?.sectionListRenderer
                                ?.contents
                                ?.firstOrNull()
                                ?.musicShelfRenderer
                                ?.contents
                                ?.firstOrNull()
                                ?.musicResponsiveListItemRenderer
                                ?.overlay
                                ?.musicItemThumbnailOverlayRenderer
                                ?.content
                                ?.musicPlayButtonRenderer
                                ?.playNavigationEndpoint
                                ?.watchEndpoint,
                        shuffleEndpoint =
                            immersiveHeader
                                ?.playButton
                                ?.buttonRenderer
                                ?.navigationEndpoint
                                ?.watchEndpoint
                                ?: response.contents
                                    ?.singleColumnBrowseResultsRenderer
                                    ?.tabs
                                    ?.firstOrNull()
                                    ?.tabRenderer
                                    ?.content
                                    ?.sectionListRenderer
                                    ?.contents
                                    ?.firstOrNull()
                                    ?.musicShelfRenderer
                                    ?.contents
                                    ?.firstOrNull()
                                    ?.musicResponsiveListItemRenderer
                                    ?.navigationEndpoint
                                    ?.watchPlaylistEndpoint,
                        radioEndpoint =
                            immersiveHeader
                                ?.startRadioButton
                                ?.buttonRenderer
                                ?.navigationEndpoint
                                ?.watchEndpoint,
                        subscriberCountText =
                            subscribeButtonRenderer
                                ?.subscriberCountText
                                ?.runs
                                ?.firstOrNull()
                                ?.text
                                ?: subscribeButtonRenderer
                                    ?.subscriberCountWithSubscribeText
                                    ?.runs
                                    ?.firstOrNull()
                                    ?.text,
                        monthlyListenerCountText =
                            immersiveHeader
                                ?.monthlyListenerCount
                                ?.runs
                                ?.firstOrNull()
                                ?.text,
                    ),
                sections =
                    response.contents
                        ?.singleColumnBrowseResultsRenderer
                        ?.tabs
                        ?.firstOrNull()
                        ?.tabRenderer
                        ?.content
                        ?.sectionListRenderer
                        ?.contents
                        ?.mapNotNull(ArtistPage::fromSectionListRendererContent)!!,
                description =
                    immersiveHeader
                        ?.description
                        ?.runs
                        ?.joinToString(separator = "") { run -> run.text }
                        ?.takeIf { description -> description.isNotBlank() },
            )
        }

    suspend fun artistItems(endpoint: BrowseEndpoint): Result<ArtistItemsPage> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, endpoint.browseId, endpoint.params).body<BrowseResponse>()
            val sectionContents = response.artistItemsSectionContents()
            val gridRenderer = sectionContents.firstNotNullOfOrNull { it.findGridRenderer() }
            if (gridRenderer != null) {
                ArtistItemsPage(
                    title =
                        gridRenderer.header
                            ?.gridHeaderRenderer
                            ?.title
                            ?.runs
                            ?.firstOrNull()
                            ?.text
                            .orEmpty(),
                    items =
                        gridRenderer.items.mapNotNull {
                            it.musicTwoRowItemRenderer?.let { renderer ->
                                ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer)
                            }
                        },
                    continuation = gridRenderer.continuations?.getContinuation(),
                    layout = ArtistItemsPageLayout.GRID,
                )
            } else {
                val musicPlaylistShelfRenderer = sectionContents.firstNotNullOfOrNull { it.findMusicPlaylistShelfRenderer() }
                val musicShelfRenderer = sectionContents.firstNotNullOfOrNull { it.findMusicShelfRenderer() }
                val shelfContents = musicPlaylistShelfRenderer?.contents ?: musicShelfRenderer?.contents.orEmpty()
                ArtistItemsPage(
                    title =
                        response.header
                            ?.musicHeaderRenderer
                            ?.title
                            ?.runs
                            ?.firstOrNull()
                            ?.text
                            ?: musicShelfRenderer
                                ?.title
                                ?.runs
                                ?.firstOrNull()
                                ?.text
                            ?: "",
                    items =
                        shelfContents.getItems().mapNotNull {
                            ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                        },
                    continuation =
                        shelfContents.getContinuation()
                            ?: musicPlaylistShelfRenderer?.continuations?.getContinuation()
                            ?: musicShelfRenderer?.continuations?.getContinuation(),
                    layout = ArtistItemsPageLayout.LIST,
                )
            }
        }

    private fun BrowseResponse.artistItemsSectionContents(): List<SectionListRenderer.Content> =
        contents
            ?.singleColumnBrowseResultsRenderer
            ?.tabs
            ?.firstOrNull()
            ?.tabRenderer
            ?.content
            ?.sectionListRenderer
            ?.contents
            .orEmpty()

    private fun SectionListRenderer.Content.findGridRenderer(): GridRenderer? =
        gridRenderer ?: itemSectionRenderer?.contents?.firstNotNullOfOrNull { it.gridRenderer }

    private fun SectionListRenderer.Content.findMusicPlaylistShelfRenderer(): MusicPlaylistShelfRenderer? = musicPlaylistShelfRenderer

    private fun SectionListRenderer.Content.findMusicShelfRenderer(): MusicShelfRenderer? =
        musicShelfRenderer ?: itemSectionRenderer?.contents?.firstNotNullOfOrNull { it.musicShelfRenderer }

    suspend fun artistItemsContinuation(continuation: String): Result<ArtistItemsContinuationPage> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, continuation = continuation).body<BrowseResponse>()

            when {
                response.continuationContents?.gridContinuation != null -> {
                    val gridContinuation = response.continuationContents.gridContinuation
                    val items =
                        gridContinuation.items.mapNotNull {
                            it.musicTwoRowItemRenderer?.let { renderer ->
                                ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer)
                            }
                        }
                    ArtistItemsContinuationPage(
                        items = items,
                        continuation = if (items.isEmpty()) null else gridContinuation.continuations?.getContinuation(),
                    )
                }

                response.continuationContents?.musicPlaylistShelfContinuation != null -> {
                    val musicPlaylistShelfContinuation = response.continuationContents.musicPlaylistShelfContinuation
                    val items =
                        musicPlaylistShelfContinuation.contents.getItems().mapNotNull {
                            ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                        }
                    ArtistItemsContinuationPage(
                        items = items,
                        continuation = if (items.isEmpty()) null else musicPlaylistShelfContinuation.continuations?.getContinuation(),
                    )
                }

                else -> {
                    val continuationItems =
                        response.onResponseReceivedActions
                            ?.firstOrNull()
                            ?.appendContinuationItemsAction
                            ?.continuationItems
                    val items =
                        continuationItems?.getItems()?.mapNotNull {
                            ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                        } ?: emptyList()
                    ArtistItemsContinuationPage(
                        items = items,
                        continuation = if (items.isEmpty()) null else continuationItems?.getContinuation(),
                    )
                }
            }
        }

    suspend fun playlist(playlistId: String): Result<PlaylistPage> =
        runCatching {
            // InnerTube's browse endpoint expects the playlist browse ID to be prefixed with
            // "VL" — but community-playlist / radio-style IDs surfaced by YouTube Music search
            // results can already start with "VL" (or "RDCL", "OLAK5uy", etc.). Unconditionally
            // prepending "VL" produced "VLVL..." browse IDs that returned a private-playlist
            // response, which we surfaced as a thrown IllegalStateException("PLAYLIST_PRIVATE")
            // — and that exception, propagated through reportException(), was the most common
            // cause of the "community playlists crash on open" bug. Strip any existing "VL"
            // prefix first, then add exactly one.
            val normalizedPlaylistId = playlistId.removePrefix("VL").removePrefix("VL")
            val response =
                innerTube
                    .browse(
                        client = WEB_REMIX,
                        browseId = "VL$normalizedPlaylistId",
                        setLogin = true,
                    ).body<BrowseResponse>()
            val primarySection =
                response.contents
                    ?.twoColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
            val allFirstColumnContents = primarySection?.contents.orEmpty()
            val base =
                allFirstColumnContents.firstOrNull {
                    it.musicResponsiveHeaderRenderer != null || it.musicEditablePlaylistDetailHeaderRenderer != null
                }
            val header =
                base?.musicResponsiveHeaderRenderer
                    ?: base?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicResponsiveHeaderRenderer
            if (header == null) throw IllegalStateException("PLAYLIST_PRIVATE")

            val title =
                header.title.runs
                    ?.firstOrNull()
                    ?.text ?: throw IllegalStateException("PLAYLIST_PRIVATE")
            val thumbnail =
                header.thumbnail
                    ?.musicThumbnailRenderer
                    ?.getBestThumbnail()
                    ?: throw IllegalStateException("PLAYLIST_PRIVATE")

            val editable = base?.musicEditablePlaylistDetailHeaderRenderer != null

            val headerMenuItems =
                header.buttons
                    .firstOrNull { it.menuRenderer != null }
                    ?.menuRenderer
                    ?.items
                    .orEmpty()

            val description =
                base
                    ?.musicEditablePlaylistDetailHeaderRenderer
                    ?.header
                    ?.musicDetailHeaderRenderer
                    ?.description
                    ?.runs
                    ?.joinToString("") { it.text }
                    ?: allFirstColumnContents.firstNotNullOfOrNull {
                        it.musicDescriptionShelfRenderer
                            ?.description
                            ?.runs
                            ?.joinToString("") { run -> run.text }
                    }
            val secondarySection =
                response.contents
                    ?.twoColumnBrowseResultsRenderer
                    ?.secondaryContents
                    ?.sectionListRenderer
            val secondaryContents = secondarySection?.contents.orEmpty()
            val songContents =
                buildList {
                    secondaryContents.forEach { content ->
                        addAll(content.playlistSongContents())
                    }
                    allFirstColumnContents.forEach { content ->
                        addAll(content.playlistSongContents())
                    }
                }
            val songsContinuation =
                secondaryContents.firstNotNullOfOrNull { content ->
                    content.playlistSongContinuation()
                } ?: allFirstColumnContents.firstNotNullOfOrNull { content ->
                    content.playlistSongContinuation()
                }

            PlaylistPage(
                playlist =
                    PlaylistItem(
                        id = playlistId,
                        title = title,
                        author =
                            header.straplineTextOne?.runs?.firstOrNull()?.let {
                                Artist(
                                    name = it.text,
                                    id = it.navigationEndpoint?.browseEndpoint?.browseId,
                                )
                            },
                        songCountText =
                            header.secondSubtitle
                                ?.runs
                                ?.firstOrNull()
                                ?.text,
                        thumbnail = thumbnail.normalizedUrl,
                        thumbnailWidth = thumbnail.width,
                        thumbnailHeight = thumbnail.height,
                        description = description,
                        playEndpoint =
                            header.buttons
                                .firstOrNull()
                                ?.musicPlayButtonRenderer
                                ?.playNavigationEndpoint
                                ?.anyWatchEndpoint,
                        shuffleEndpoint =
                            headerMenuItems
                                .firstOrNull()
                                ?.menuNavigationItemRenderer
                                ?.navigationEndpoint
                                ?.watchPlaylistEndpoint,
                        radioEndpoint =
                            headerMenuItems
                                .find {
                                    it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                                }?.menuNavigationItemRenderer
                                ?.navigationEndpoint
                                ?.watchPlaylistEndpoint,
                        isEditable = editable,
                    ),
                songs =
                    songContents.getItems().mapNotNull {
                        PlaylistPage.fromMusicResponsiveListItemRenderer(it, playlistId)
                    }.distinctByPlaylistEntry(),
                songsContinuation = songsContinuation,
                continuation =
                    secondarySection?.continuations?.getContinuation()
                        ?: primarySection?.continuations?.getContinuation(),
            )
        }

    suspend fun playlistContinuation(
        continuation: String,
        playlistId: String? = null,
    ): Result<PlaylistContinuationPage> =
        runCatching {
            val response =
                innerTube
                    .browse(
                        client = WEB_REMIX,
                        continuation = continuation,
                        browseId = "",
                        setLogin = true,
                    ).body<BrowseResponse>()

            playlistContinuationPageFromResponse(response, playlistId)
        }

    suspend fun home(
        continuation: String? = null,
        params: String? = null,
    ): Result<HomePage> =
        runCatching {
            if (continuation != null) {
                return@runCatching homeContinuation(continuation).getOrThrow()
            }

            val response =
                innerTube
                    .browse(
                        WEB_REMIX,
                        browseId = "FEmusic_home",
                        params = params,
                        setLogin = true,
                        // A pinned region has to win over the account's own country here, or the
                        // home feed keeps coming back from the account's home country.
                        useAccountContext = regionSensitiveAccountContext,
                    ).body<BrowseResponse>()
            val continuation =
                response.contents
                    ?.singleColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.continuations
                    ?.getContinuation()
            val sectionListRender =
                response.contents
                    ?.singleColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
            val sections =
                sectionListRender
                    ?.contents!!
                    .mapNotNull { it.musicCarouselShelfRenderer }
                    .mapNotNull {
                        HomePage.Section.fromMusicCarouselShelfRenderer(it)
                    }.toMutableList()
            val chips =
                sectionListRender.header
                    ?.chipCloudRenderer
                    ?.chips
                    ?.mapNotNull { HomePage.Chip.fromChipCloudChipRenderer(it) }
            HomePage(chips, sections, continuation)
        }

    suspend fun podcast(browseId: String): Result<PodcastPage> =
        runCatching {
            val normalizedBrowseId =
                browseId.takeIf { it.startsWith(PODCAST_SHOW_BROWSE_PREFIX) }
                    ?: "$PODCAST_SHOW_BROWSE_PREFIX$browseId"
            val response =
                innerTube
                    .browse(
                        client = WEB_REMIX,
                        browseId = normalizedBrowseId,
                        setLogin = true,
                    ).body<BrowseResponse>()
            PodcastPage.fromResponse(response, normalizedBrowseId)
        }

    suspend fun podcastContinuation(continuation: String): Result<PodcastPage.Continuation> =
        runCatching {
            val response =
                innerTube
                    .browse(
                        client = WEB_REMIX,
                        continuation = continuation,
                        setLogin = true,
                    ).body<BrowseResponse>()
            PodcastPage.continuationFromResponse(response)
        }

    private suspend fun homeContinuation(continuation: String): Result<HomePage> =
        runCatching {
            val response =
                innerTube.browse(WEB_REMIX, continuation = continuation).body<BrowseResponse>()
            val sections =
                response.continuationContents
                    ?.sectionListContinuation
                    ?.contents
                    ?.mapNotNull { it.musicCarouselShelfRenderer }
                    ?.mapNotNull {
                        HomePage.Section.fromMusicCarouselShelfRenderer(it)
                    }.orEmpty()
            val nextContinuation =
                if (sections.isEmpty()) {
                    null
                } else {
                    response.continuationContents
                        ?.sectionListContinuation
                        ?.continuations
                        ?.getContinuation()
                }
            HomePage(
                chips = null,
                sections = sections,
                continuation = nextContinuation,
            )
        }

    suspend fun explore(): Result<ExplorePage> =
        runCatching {
            val response =
                innerTube
                    .browse(
                        client = WEB_REMIX,
                        browseId = BROWSE_ID_EXPLORE,
                        useAccountContext = regionSensitiveAccountContext,
                    ).body<BrowseResponse>()
            ExplorePage(
                newReleaseAlbums =
                    response.contents
                        ?.singleColumnBrowseResultsRenderer
                        ?.tabs
                        ?.firstOrNull()
                        ?.tabRenderer
                        ?.content
                        ?.sectionListRenderer
                        ?.contents
                        ?.find {
                            it.musicCarouselShelfRenderer
                                ?.header
                                ?.musicCarouselShelfBasicHeaderRenderer
                                ?.moreContentButton
                                ?.buttonRenderer
                                ?.navigationEndpoint
                                ?.browseEndpoint
                                ?.browseId ==
                                BROWSE_ID_NEW_RELEASE_ALBUMS
                        }?.musicCarouselShelfRenderer
                        ?.contents
                        ?.mapNotNull { it.musicTwoRowItemRenderer }
                        ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
                        .orEmpty(),
                moodAndGenres =
                    response.contents
                        ?.singleColumnBrowseResultsRenderer
                        ?.tabs
                        ?.firstOrNull()
                        ?.tabRenderer
                        ?.content
                        ?.sectionListRenderer
                        ?.contents
                        ?.find {
                            it.musicCarouselShelfRenderer
                                ?.header
                                ?.musicCarouselShelfBasicHeaderRenderer
                                ?.moreContentButton
                                ?.buttonRenderer
                                ?.navigationEndpoint
                                ?.browseEndpoint
                                ?.browseId ==
                                BROWSE_ID_MOODS_AND_GENRES
                        }?.musicCarouselShelfRenderer
                        ?.contents
                        ?.mapNotNull { it.musicNavigationButtonRenderer }
                        ?.mapNotNull(MoodAndGenres.Companion::fromMusicNavigationButtonRenderer)
                        .orEmpty(),
            )
        }

    suspend fun newReleaseAlbums(): Result<List<AlbumItem>> =
        runCatching {
            try {
                val directAlbums = newReleaseAlbumsFromBrowsePage()
                if (directAlbums.isNotEmpty()) return@runCatching directAlbums
            } catch (throwable: Throwable) {
                if (!throwable.isBrowsePageUnavailable()) throw throwable
            }
            explore().getOrThrow().newReleaseAlbums
        }

    private suspend fun newReleaseAlbumsFromBrowsePage(): List<AlbumItem> {
        // Follow the browse page's continuation chain until it is exhausted.
        // The "New releases" browse endpoint serves only its first page
        // (~200 albums) per request and paginates the rest behind
        // continuation tokens; a single request silently stopped at that
        // first page — the "cap of total 200" the user reported
        // (2026-09-03: "Remove the cap of total 200 notifications from new
        // releases section"). The page-count guard only protects against a
        // pathological token loop; every real page yields 20+ albums.
        val albums = mutableListOf<AlbumItem>()
        val response =
            innerTube
                .browse(WEB_REMIX, browseId = BROWSE_ID_NEW_RELEASE_ALBUMS)
                .body<BrowseResponse>()
        val sectionList =
            response.contents
                ?.singleColumnBrowseResultsRenderer
                ?.tabs
                ?.firstOrNull()
                ?.tabRenderer
                ?.content
                ?.sectionListRenderer
                ?: response.contents?.sectionListRenderer
        albums += sectionList?.contents.orEmpty().toNewReleaseAlbumItems()
        var continuation = sectionList.nextNewReleaseContinuation()
        var pages = 1
        while (continuation != null && pages < MAX_NEW_RELEASE_PAGES) {
            val continuationResponse =
                innerTube
                    .browse(WEB_REMIX, continuation = continuation)
                    .body<BrowseResponse>()
            val sectionListContinuation =
                continuationResponse.continuationContents?.sectionListContinuation
            if (sectionListContinuation != null) {
                albums += sectionListContinuation.contents.toNewReleaseAlbumItems()
                continuation = sectionListContinuation.nextNewReleaseContinuation()
            } else {
                val gridContinuation = continuationResponse.continuationContents?.gridContinuation
                if (gridContinuation == null) break
                albums +=
                    gridContinuation.items
                        .asSequence()
                        .mapNotNull { it.musicTwoRowItemRenderer }
                        .mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
                        .toList()
                continuation = gridContinuation.continuations?.getContinuation()
            }
            pages++
        }
        return albums.distinctBy { it.id }
    }

    /**
     * The next continuation token of a section-list page, wherever YouTube
     * chose to hang it: the sectionListRenderer's own "continuations" array,
     * a trailing continuationItemRenderer content, or (rarely) a grid
     * section carrying its own continuation.
     */
    private fun SectionListRenderer?.nextNewReleaseContinuation(): String? {
        this ?: return null
        continuations?.getContinuation()?.let { return it }
        contents
            ?.lastOrNull()
            ?.continuationItemRenderer
            ?.continuationEndpoint
            ?.continuationCommand
            ?.token
            ?.let { return it }
        return contents
            ?.lastOrNull()
            ?.gridRenderer
            ?.continuations
            ?.getContinuation()
    }

    private fun SectionListContinuation.nextNewReleaseContinuation(): String? {
        continuations?.getContinuation()?.let { return it }
        return contents
            ?.lastOrNull()
            ?.continuationItemRenderer
            ?.continuationEndpoint
            ?.continuationCommand
            ?.token
    }

    private fun List<SectionListRenderer.Content>.toNewReleaseAlbumItems(): List<AlbumItem> =
        asSequence()
            .flatMap { content ->
                sequence {
                    content.gridRenderer
                        ?.items
                        ?.asSequence()
                        ?.mapNotNull { it.musicTwoRowItemRenderer }
                        ?.forEach { yield(it) }
                    content.musicCarouselShelfRenderer
                        ?.contents
                        ?.asSequence()
                        ?.mapNotNull { it.musicTwoRowItemRenderer }
                        ?.forEach { yield(it) }
                    content.itemSectionRenderer
                        ?.contents
                        ?.asSequence()
                        ?.mapNotNull { it.gridRenderer }
                        ?.flatMap { it.items.asSequence() }
                        ?.mapNotNull { it.musicTwoRowItemRenderer }
                        ?.forEach { yield(it) }
                }
            }.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
            .toList()

    private fun Throwable.isBrowsePageUnavailable(): Boolean {
        val exception = this as? ClientRequestException ?: return false
        return exception.response.status == HttpStatusCode.NotFound ||
            exception.response.status == HttpStatusCode.BadRequest
    }

    private suspend fun <T> withPlaylistMutationAuthRecovery(block: suspend () -> T): T {
        val initialAuthState = authState
        return try {
            block()
        } catch (e: Throwable) {
            if (!shouldRetryPlaylistMutationWithoutDelegatedContext(initialAuthState, e)) throw e
            val fallbackAuthState = authState.copy(dataSyncId = null)
            authState = fallbackAuthState
            try {
                block()
            } finally {
                val currentAuthState = authState
                if (currentAuthState.fingerprint == fallbackAuthState.fingerprint) {
                    authState = currentAuthState.copy(dataSyncId = initialAuthState.dataSyncId)
                }
            }
        }
    }

    private fun shouldRetryPlaylistMutationWithoutDelegatedContext(
        initialAuthState: PlaybackAuthState,
        failure: Throwable,
    ): Boolean {
        val exception = failure as? ClientRequestException ?: return false
        if (exception.response.status != HttpStatusCode.Forbidden) return false

        val currentAuthState = authState
        return initialAuthState.hasPlaybackLoginContext &&
            currentAuthState.hasPlaybackLoginContext &&
            currentAuthState.cookie == initialAuthState.cookie &&
            currentAuthState.dataSyncId == initialAuthState.dataSyncId
    }

    suspend fun moodAndGenres(): Result<List<MoodAndGenres>> =
        runCatching {
            val response =
                innerTube
                    .browse(
                        WEB_REMIX,
                        browseId = BROWSE_ID_MOODS_AND_GENRES,
                        useAccountContext = regionSensitiveAccountContext,
                    ).body<BrowseResponse>()
            response.contents
                ?.singleColumnBrowseResultsRenderer
                ?.tabs
                ?.firstOrNull()
                ?.tabRenderer
                ?.content
                ?.sectionListRenderer
                ?.contents!!
                .mapNotNull(MoodAndGenres.Companion::fromSectionListRendererContent)
        }

    suspend fun browse(
        browseId: String,
        params: String?,
    ): Result<BrowseResult> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, browseId = browseId, params = params).body<BrowseResponse>()
            val browseItems =
                response.contents
                    ?.singleColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    ?.mapNotNull { content ->
                        when {
                            content.gridRenderer != null -> {
                                BrowseResult.Item(
                                    title =
                                        content.gridRenderer.header
                                            ?.gridHeaderRenderer
                                            ?.title
                                            ?.runs
                                            ?.firstOrNull()
                                            ?.text,
                                    items =
                                        content.gridRenderer.items
                                            .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                                            .mapNotNull(RelatedPage.Companion::fromMusicTwoRowItemRenderer),
                                )
                            }

                            content.musicCarouselShelfRenderer != null -> {
                                BrowseResult.Item(
                                    title =
                                        content.musicCarouselShelfRenderer.header
                                            ?.musicCarouselShelfBasicHeaderRenderer
                                            ?.title
                                            ?.runs
                                            ?.firstOrNull()
                                            ?.text,
                                    items =
                                        content.musicCarouselShelfRenderer.contents
                                            .mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
                                            .mapNotNull(RelatedPage.Companion::fromMusicTwoRowItemRenderer),
                                )
                            }

                            else -> {
                                null
                            }
                        }
                    }.orEmpty()
            BrowseResult(
                title =
                    response.header
                        ?.musicHeaderRenderer
                        ?.title
                        ?.runs
                        ?.firstOrNull()
                        ?.text,
                thumbnail =
                    response.header
                        ?.musicImmersiveHeaderRenderer
                        ?.thumbnail
                        ?.musicThumbnailRenderer
                        ?.getThumbnailUrl()
                        ?: response.header
                            ?.musicVisualHeaderRenderer
                            ?.foregroundThumbnail
                            ?.musicThumbnailRenderer
                            ?.getThumbnailUrl()
                        ?: response.header
                            ?.musicDetailHeaderRenderer
                            ?.thumbnail
                            ?.musicThumbnailRenderer
                            ?.getThumbnailUrl()
                        ?: response.header
                            ?.musicEditablePlaylistDetailHeaderRenderer
                            ?.header
                            ?.musicDetailHeaderRenderer
                            ?.thumbnail
                            ?.musicThumbnailRenderer
                            ?.getThumbnailUrl()
                        ?: response.header
                            ?.musicEditablePlaylistDetailHeaderRenderer
                            ?.header
                            ?.musicResponsiveHeaderRenderer
                            ?.thumbnail
                            ?.musicThumbnailRenderer
                            ?.getThumbnailUrl()
                        ?: response.header
                            ?.musicHeaderRenderer
                            ?.thumbnail
                            ?.thumbnails
                            ?.lastOrNull()
                            ?.normalizedUrl
                        ?: response.header
                            ?.musicHeaderRenderer
                            ?.straplineThumbnail
                            ?.thumbnails
                            ?.lastOrNull()
                            ?.normalizedUrl
                        ?: browseItems
                            .asSequence()
                            .flatMap { it.items.asSequence() }
                            .mapNotNull { it.thumbnail }
                            .firstOrNull(),
                items = browseItems,
            )
        }

    suspend fun library(
        browseId: String,
        tabIndex: Int = 0,
    ) = runCatching {
        val response =
            innerTube
                .browse(
                    client = WEB_REMIX,
                    browseId = browseId,
                    setLogin = true,
                ).body<BrowseResponse>()

        val tabs = response.contents?.singleColumnBrowseResultsRenderer?.tabs

        val contents =
            if (tabs != null && tabIndex >= 0 && tabIndex < tabs.size) {
                tabs[tabIndex]
                    .tabRenderer.content
                    ?.sectionListRenderer
                    ?.contents
                    .orEmpty()
            } else {
                emptyList()
            }
        LibraryPage(
            items = contents.flatMap { it.libraryItems() },
            continuation = contents.firstNotNullOfOrNull { it.libraryContinuation() },
        )
    }

    suspend fun libraryContinuation(continuation: String) =
        runCatching {
            val response =
                innerTube
                    .browse(
                        client = WEB_REMIX,
                        continuation = continuation,
                        setLogin = true,
                    ).body<BrowseResponse>()

            val contents = response.continuationContents
            val sectionContents = contents?.sectionListContinuation?.contents.orEmpty()
            val sectionItems = sectionContents.flatMap { it.libraryItems() }
            val gridItems =
                contents
                    ?.gridContinuation
                    ?.items
                    .orEmpty()
                    .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                    .mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) }
            val shelfItems =
                contents
                    ?.musicShelfContinuation
                    ?.contents
                    .orEmpty()
                    .mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                    .mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) }
            val playlistShelfItems =
                contents
                    ?.musicPlaylistShelfContinuation
                    ?.contents
                    .orEmpty()
                    .mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                    .mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) }
            val items = sectionItems + gridItems + shelfItems + playlistShelfItems
            LibraryContinuationPage(
                items = items,
                continuation =
                    if (items.isEmpty()) {
                        null
                    } else {
                        sectionContents.firstNotNullOfOrNull { it.libraryContinuation() }
                            ?: contents?.sectionListContinuation?.continuations?.getContinuation()
                            ?: contents?.gridContinuation?.continuations?.getContinuation()
                            ?: contents
                                ?.musicShelfContinuation
                                ?.contents
                                .orEmpty()
                                .getContinuation()
                            ?: contents?.musicShelfContinuation?.continuations?.getContinuation()
                            ?: contents
                                ?.musicPlaylistShelfContinuation
                                ?.contents
                                .orEmpty()
                                .getContinuation()
                            ?: contents?.musicPlaylistShelfContinuation?.continuations?.getContinuation()
                    },
            )
        }

    suspend fun libraryRecentActivity(): Result<LibraryPage> =
        runCatching {
            val continuation = LibraryFilter.FILTER_RECENT_ACTIVITY.value

            val response =
                innerTube
                    .browse(
                        client = WEB_REMIX,
                        continuation = continuation,
                        setLogin = true,
                    ).body<BrowseResponse>()

            val gridItems =
                response.continuationContents
                    ?.sectionListContinuation
                    ?.contents
                    ?.firstOrNull()
                    ?.gridRenderer
                    ?.items

            if (gridItems == null) {
                return@runCatching LibraryPage(
                    items = emptyList(),
                    continuation = null,
                )
            }

            val items =
                gridItems
                    .mapNotNull {
                        it.musicTwoRowItemRenderer?.let { renderer ->
                            LibraryPage.fromMusicTwoRowItemRenderer(renderer)
                        }
                    }.toMutableList()

        /*
         * We need to fetch the artist page when accessing the library because it allows to have
         * a proper playEndpoint, which is needed to correctly report the playing indicator in
         * the home page.
         *
         * Despite this, we need to use the old thumbnail because it's the proper format for a
         * square picture, which is what we need.
         */
            items.forEachIndexed { index, item ->
                if (item is ArtistItem) {
                    artist(item.id).getOrNull()?.artist?.let { fetchedArtist ->
                        items[index] =
                            fetchedArtist.copy(
                                thumbnail = item.thumbnail,
                                thumbnailWidth = item.thumbnailWidth,
                                thumbnailHeight = item.thumbnailHeight,
                            )
                    }
                }
            }

            LibraryPage(
                items = items,
                continuation = null,
            )
        }

    suspend fun getChartsPage(continuation: String? = null): Result<ChartsPage> =
        runCatching {
            val response =
                innerTube
                    .browse(
                        client = WEB_REMIX,
                        browseId = "FEmusic_charts",
                        params = "ggMGCgQIgAQ%3D",
                        continuation = continuation,
                        useAccountContext = regionSensitiveAccountContext,
                    ).body<BrowseResponse>()

            val sections = mutableListOf<ChartsPage.ChartSection>()

            response.contents
                ?.singleColumnBrowseResultsRenderer
                ?.tabs
                ?.firstOrNull()
                ?.tabRenderer
                ?.content
                ?.sectionListRenderer
                ?.contents
                ?.forEach { content ->

                    content.musicCarouselShelfRenderer?.let { renderer ->
                        val title =
                            renderer.header
                                ?.musicCarouselShelfBasicHeaderRenderer
                                ?.title
                                ?.runs
                                ?.firstOrNull()
                                ?.text
                                ?: return@forEach

                        val items =
                            renderer.contents
                                .mapNotNull { item ->
                                    when {
                                        item.musicResponsiveListItemRenderer != null -> {
                                            convertToChartItem(item.musicResponsiveListItemRenderer)
                                        }

                                        item.musicTwoRowItemRenderer != null -> {
                                            convertMusicTwoRowItem(item.musicTwoRowItemRenderer)
                                        }

                                        else -> {
                                            null
                                        }
                                    }
                                }.filterNotNull()

                        if (items.isNotEmpty()) {
                            sections.add(
                                ChartsPage.ChartSection(
                                    title = title,
                                    items = items,
                                    chartType = determineChartType(title),
                                ),
                            )
                        }
                    }

                    content.gridRenderer?.let { renderer ->
                        val title =
                            renderer.header
                                ?.gridHeaderRenderer
                                ?.title
                                ?.runs
                                ?.firstOrNull()
                                ?.text
                                ?: return@let

                        val items =
                            renderer.items
                                .mapNotNull { item ->
                                    item.musicTwoRowItemRenderer?.let { renderer ->
                                        convertMusicTwoRowItem(renderer)
                                    }
                                }.filterNotNull()

                        if (items.isNotEmpty()) {
                            sections.add(
                                ChartsPage.ChartSection(
                                    title = title,
                                    items = items,
                                    chartType = ChartsPage.ChartType.NEW_RELEASES,
                                ),
                            )
                        }
                    }
                }

            ChartsPage(
                sections = sections,
                continuation =
                    response.continuationContents
                        ?.sectionListContinuation
                        ?.continuations
                        ?.getContinuation(),
            )
        }

    private fun determineChartType(title: String): ChartsPage.ChartType =
        when {
            title.contains("Trending", ignoreCase = true) -> ChartsPage.ChartType.TRENDING
            title.contains("Top", ignoreCase = true) -> ChartsPage.ChartType.TOP
            else -> ChartsPage.ChartType.GENRE
        }

    private fun convertToChartItem(renderer: MusicResponsiveListItemRenderer): YTItem? {
        return try {
            when {
                renderer.flexColumns.size >= 3 && renderer.playlistItemData?.videoId != null -> {
                    val firstColumn =
                        renderer.flexColumns
                            .getOrNull(0)
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text ?: return null

                    val secondColumn =
                        renderer.flexColumns
                            .getOrNull(1)
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text ?: return null

                    val titleRun = firstColumn.runs?.firstOrNull() ?: return null
                    val title = titleRun.text.takeIf { it.isNotBlank() } ?: return null

                    val artists =
                        secondColumn.runs?.mapNotNull { run ->
                            run.text.takeIf { it.isNotBlank() }?.let { name ->
                                Artist(
                                    name = name,
                                    id = run.navigationEndpoint?.browseEndpoint?.browseId,
                                )
                            }
                        } ?: emptyList()

                    val thirdColumn =
                        renderer.flexColumns
                            .getOrNull(2)
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text

                    val thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getBestThumbnail() ?: return null
                    SongItem(
                        id = renderer.playlistItemData.videoId,
                        title = title,
                        artists = artists,
                        thumbnail = thumbnail.normalizedUrl,
                        thumbnailWidth = thumbnail.width,
                        thumbnailHeight = thumbnail.height,
                        explicit =
                            renderer.badges?.any {
                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                            } == true,
                        chartPosition =
                            thirdColumn
                                ?.runs
                                ?.firstOrNull()
                                ?.text
                                ?.toIntOrNull(),
                        chartChange = thirdColumn?.runs?.getOrNull(1)?.text,
                    )
                }

                else -> {
                    null
                }
            }
        } catch (e: Exception) {
            println("Error converting chart item: ${e.message}\n${Json.encodeToString(renderer)}")
            null
        }
    }

    private fun convertMusicTwoRowItem(renderer: MusicTwoRowItemRenderer): YTItem? {
        return try {
            when {
                renderer.isSong -> {
                    val subtitle = renderer.subtitle?.runs ?: return null
                    val thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail() ?: return null
                    SongItem(
                        id = renderer.navigationEndpoint.watchEndpoint?.videoId ?: return null,
                        title =
                            renderer.title.runs
                                ?.firstOrNull()
                                ?.text ?: return null,
                        artists =
                            subtitle.mapNotNull {
                                it.navigationEndpoint?.browseEndpoint?.browseId?.let { id ->
                                    Artist(name = it.text, id = id)
                                }
                            },
                        thumbnail = thumbnail.normalizedUrl,
                        thumbnailWidth = thumbnail.width,
                        thumbnailHeight = thumbnail.height,
                        explicit =
                            renderer.subtitleBadges?.any {
                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                            } == true,
                    )
                }

                renderer.isAlbum -> {
                    val thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getBestThumbnail() ?: return null
                    AlbumItem(
                        browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        playlistId =
                            renderer.thumbnailOverlay
                                ?.musicItemThumbnailOverlayRenderer
                                ?.content
                                ?.musicPlayButtonRenderer
                                ?.playNavigationEndpoint
                                ?.watchPlaylistEndpoint
                                ?.playlistId ?: return null,
                        title =
                            renderer.title.runs
                                ?.firstOrNull()
                                ?.text ?: return null,
                        artists =
                            renderer.subtitle?.runs?.oddElements()?.drop(1)?.mapNotNull {
                                it.navigationEndpoint?.browseEndpoint?.browseId?.let { id ->
                                    Artist(name = it.text, id = id)
                                }
                            },
                        year =
                            renderer.subtitle
                                ?.runs
                                ?.lastOrNull()
                                ?.text
                                ?.toIntOrNull(),
                        thumbnail = thumbnail.normalizedUrl,
                        thumbnailWidth = thumbnail.width,
                        thumbnailHeight = thumbnail.height,
                        explicit =
                            renderer.subtitleBadges?.any {
                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                            } == true,
                    )
                }

                else -> {
                    null
                }
            }
        } catch (e: Exception) {
            println("Error converting two row item: ${e.message}\n${Json.encodeToString(renderer)}")
            null
        }
    }

    suspend fun musicHistory() =
        runCatching {
            val response =
                innerTube
                    .browse(
                        client = WEB_REMIX,
                        browseId = "FEmusic_history",
                        setLogin = true,
                    ).body<BrowseResponse>()

            // Initial sections from the first browse response. Each
            // section may carry a `continuation` token — the YouTube
            // Music history endpoint paginates per-section (Today,
            // Yesterday, This Week, etc.) and the first page caps each
            // section at ~200 songs. Per user request (2026-08-28):
            // "The local song history got its 200 limit removed but the
            // remote history is still capped to 200. Fix it." We follow
            // each section's continuation chain until it's exhausted,
            // accumulating songs into the section's bucket. This mirrors
            // the album-page continuation sweep already used by
            // `YouTube.album` (commit history shows the same pattern).
            val initialSections =
                response.contents
                    ?.singleColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    ?.flatMap(HistoryPage::fromSectionListContent)
                    .orEmpty()

            // Mutable buckets keyed by section title, accumulating
            // songs from the initial response + every continuation
            // response. Keyed by title rather than by index because
            // continuation responses don't echo back the section's
            // original index — only the title is stable across pages.
            val buckets = LinkedHashMap<String, HistoryPage.HistorySection>()
            initialSections.forEach { section ->
                buckets[section.title] =
                    HistoryPage.HistorySection(
                        title = section.title,
                        songs = section.songs,
                        continuation = section.continuation,
                    )
            }

            // Pending continuations to follow. Each entry holds the
            // section title (so results land in the right bucket) and
            // the continuation token.
            val pending = ArrayDeque<Pair<String, String>>()
            buckets.values.forEach { section ->
                section.continuation?.let { token ->
                    pending.addLast(section.title to token)
                }
            }

            val seenContinuations = mutableSetOf<String>()
            var requestCount = 0
            val maxRequests = 200 // Guard against runaway loops; each section rarely exceeds ~5 pages.

            while (pending.isNotEmpty() && requestCount < maxRequests) {
                val (sectionTitle, token) = pending.removeFirst()
                if (token in seenContinuations) continue
                seenContinuations.add(token)
                requestCount++

                val continuationResponse =
                    innerTube
                        .browse(
                            client = WEB_REMIX,
                            continuation = token,
                            // 2026-09-05 fix: the continuation requests for a
                            // LOGIN-REQUIRED endpoint went out ANONYMOUS (the
                            // initial browse sets setLogin = true, these did
                            // not), so YouTube returned an empty/invalid
                            // continuation and the sweep silently stopped after
                            // the first page — the "remote history is still
                            // capped to 200" report. The per-section pages are
                            // exactly as login-gated as the page they continue.
                            setLogin = true,
                        ).body<BrowseResponse>()

                // The continuation response carries the next batch of
                // songs in `continuationContents.musicShelfContinuation`,
                // which has the same shape as a regular MusicShelfRenderer
                // (contents + continuations). We re-use the same
                // `toHistorySection` extension to extract songs + the
                // next continuation token.
                val musicShelfContinuation =
                    continuationResponse.continuationContents?.musicShelfContinuation
                if (musicShelfContinuation != null) {
                    val moreSongs =
                        musicShelfContinuation.contents.orEmpty().getItems().mapNotNull {
                            it.toSongItem(albumColumnIndex = 3)
                        }
                    val nextToken =
                        musicShelfContinuation.continuations?.getContinuation()
                            ?: musicShelfContinuation.contents?.getContinuation()
                    val existing = buckets[sectionTitle]
                    if (existing != null) {
                        buckets[sectionTitle] =
                            HistoryPage.HistorySection(
                                title = existing.title,
                                songs = existing.songs + moreSongs,
                                continuation = nextToken,
                            )
                    }
                    nextToken?.let { pending.addLast(sectionTitle to it) }
                }
            }

            HistoryPage(sections = buckets.values.toList())
        }

    suspend fun likeVideo(
        videoId: String,
        like: Boolean,
    ) = runCatching {
        if (like) {
            innerTube.likeVideo(WEB_REMIX, videoId)
        } else {
            innerTube.unlikeVideo(WEB_REMIX, videoId)
        }
    }

    suspend fun likePlaylist(
        playlistId: String,
        like: Boolean,
    ) = runCatching {
        if (like) {
            innerTube.likePlaylist(WEB_REMIX, playlistId)
        } else {
            innerTube.unlikePlaylist(WEB_REMIX, playlistId)
        }
    }

    suspend fun subscribeChannel(
        channelId: String,
        subscribe: Boolean,
    ) = runCatching {
        if (subscribe) {
            innerTube.subscribeChannel(WEB_REMIX, channelId)
        } else {
            innerTube.unsubscribeChannel(WEB_REMIX, channelId)
        }
    }

    suspend fun getChannelId(browseId: String): String {
        artist(browseId).onSuccess {
            return it.artist.channelId ?: ""
        }
        return ""
    }

    public suspend fun addToPlaylist(
        playlistId: String,
        videoId: String,
    ) = runCatchingCancellable {
        val response =
            withPlaylistMutationAuthRecovery {
                innerTube
                    .addToPlaylist(WEB_REMIX, playlistId, videoId)
                    .body<AddItemYouTubePlaylistResponse>()
            }.requireSuccessfulPlaylistEdit()
        val responseSetVideoId =
            response.playlistEditResults
                .asSequence()
                .mapNotNull(AddItemYouTubePlaylistResponse.PlaylistEditResult::playlistEditVideoAddedResultData)
                .firstOrNull { result -> result.videoId == videoId }
                ?.setVideoId
                ?.takeIf(String::isNotBlank)
        val setVideoId =
            responseSetVideoId
                ?: playlistEntrySetVideoIds(playlistId, videoId)
                    .getOrThrow()
                    .lastOrNull()
                    ?.takeIf(String::isNotBlank)
        requireNotNull(setVideoId) {
            "Playlist edit did not confirm added video $videoId"
        }
        setVideoId
    }

    public suspend fun addSongsToPlaylist(
        playlistId: String,
        videoIds: List<String>,
        batchSize: Int = DEFAULT_PLAYLIST_EDIT_BATCH_SIZE,
        onProgress: (completedSongs: Int, totalSongs: Int) -> Unit = { _, _ -> },
    ): Result<List<String?>> =
        runCatchingCancellable {
            require(batchSize > 0) { "batchSize must be positive" }
            if (videoIds.isEmpty()) return@runCatchingCancellable emptyList<String?>()

            val setVideoIds = ArrayList<String?>(videoIds.size)
            val totalSongs = videoIds.size
            var completedSongs = 0
            onProgress(completedSongs, totalSongs)

            videoIds.chunked(batchSize).forEach { batch ->
                val batchResponse =
                    runCatchingCancellable {
                        withPlaylistMutationAuthRecovery {
                            innerTube
                                .addSongsToPlaylist(WEB_REMIX, playlistId, batch)
                                .body<AddItemYouTubePlaylistResponse>()
                        }.requireSuccessfulPlaylistEdit()
                    }

                if (batchResponse.isSuccess) {
                    val resultByVideoId =
                        batchResponse
                            .getOrThrow()
                            .playlistEditResults
                            .asSequence()
                            .mapNotNull(AddItemYouTubePlaylistResponse.PlaylistEditResult::playlistEditVideoAddedResultData)
                            .mapNotNull { result ->
                                val resultVideoId = result.videoId?.takeIf(String::isNotBlank)
                                val resultSetVideoId = result.setVideoId?.takeIf(String::isNotBlank)
                                if (resultVideoId == null || resultSetVideoId == null) {
                                    null
                                } else {
                                    resultVideoId to resultSetVideoId
                                }
                            }.toMap()

                    batch.forEach { videoId ->
                        setVideoIds += resultByVideoId[videoId]
                        completedSongs += 1
                    }
                } else if (batch.size == 1) {
                    throw batchResponse.exceptionOrNull() ?: IllegalStateException("Playlist edit failed")
                } else {
                    batch.forEach { videoId ->
                        val setVideoId = addToPlaylist(playlistId, videoId).getOrThrow()
                        setVideoIds += setVideoId
                        completedSongs += 1
                    }
                }
                onProgress(completedSongs, totalSongs)
            }

            setVideoIds
        }

    private fun AddItemYouTubePlaylistResponse.requireSuccessfulPlaylistEdit(): AddItemYouTubePlaylistResponse {
        check(status == PLAYLIST_EDIT_STATUS_SUCCEEDED) {
            "Playlist edit failed with status $status"
        }
        return this
    }

    private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            Result.failure(failure)
        }

    suspend fun addPlaylistToPlaylist(
        playlistId: String,
        addPlaylistId: String,
    ) = runCatching {
        withPlaylistMutationAuthRecovery {
            innerTube.addPlaylistToPlaylist(WEB_REMIX, playlistId, addPlaylistId)
        }
    }

    suspend fun playlistEntrySetVideoIds(
        playlistId: String,
        videoId: String,
    ) = runCatching {
        val setVideoIds = mutableListOf<String>()

        fun collectSetVideoIds(songs: List<SongItem>) {
            setVideoIds +=
                songs
                    .asSequence()
                    .filter { song -> song.id == videoId }
                    .mapNotNull(SongItem::setVideoId)
                    .toList()
        }

        val playlistPage = playlist(playlistId).getOrThrow()
        collectSetVideoIds(playlistPage.songs)

        var continuation =
            playlistPage.songsContinuation?.takeUnless(String::isBlank)
                ?: playlistPage.continuation?.takeUnless(String::isBlank)
        while (continuation != null) {
            val continuationPage = playlistContinuation(continuation, playlistId).getOrThrow()
            collectSetVideoIds(continuationPage.songs)
            continuation = continuationPage.continuation?.takeUnless(String::isBlank)
        }

        setVideoIds.distinct()
    }

    suspend fun removeFromPlaylist(
        playlistId: String,
        videoId: String,
        setVideoId: String,
    ) = runCatching {
        withPlaylistMutationAuthRecovery {
            innerTube.removeFromPlaylist(WEB_REMIX, playlistId, videoId, setVideoId)
        }
    }

    suspend fun moveSongPlaylist(
        playlistId: String,
        setVideoId: String,
        successorSetVideoId: String?,
    ) = runCatching {
        withPlaylistMutationAuthRecovery {
            innerTube.moveSongPlaylist(WEB_REMIX, playlistId, setVideoId, successorSetVideoId)
        }
    }

    suspend fun createPlaylist(
        title: String,
        videoIds: List<String> = emptyList(),
    ) = runCatching {
        withPlaylistMutationAuthRecovery {
            innerTube.createPlaylist(WEB_REMIX, title, videoIds).body<CreatePlaylistResponse>()
        }.playlistId
    }

    suspend fun renamePlaylist(
        playlistId: String,
        name: String,
    ) = runCatching {
        withPlaylistMutationAuthRecovery {
            innerTube.renamePlaylist(WEB_REMIX, playlistId, name)
        }
    }

    suspend fun uploadCustomPlaylistCover(
        playlistId: String,
        image: ByteArray,
    ): Result<String> =
        runCatching {
            require(playlistId.isNotBlank())
            require(image.isNotEmpty())

            withPlaylistMutationAuthRecovery {
                val uploadId =
                    innerTube
                        .startPlaylistCoverUpload(WEB_REMIX, image.size)
                        .headers["x-guploader-uploadid"]
                        ?.takeIf(String::isNotBlank)
                        ?: throw IllegalStateException("Playlist cover upload session was not created")
                val uploadResponse = innerTube.uploadPlaylistCover(WEB_REMIX, uploadId, image)
                val encryptedBlobId =
                    playlistCoverResponseJson
                        .decodeFromString<ImageUploadResponse>(uploadResponse.bodyAsText())
                        .encryptedBlobId
                        .takeIf(String::isNotBlank)
                        ?: throw IllegalStateException("Playlist cover upload returned an empty blob id")
                val editResponse =
                    innerTube
                        .setPlaylistCustomCover(WEB_REMIX, playlistId, encryptedBlobId)
                        .body<EditPlaylistResponse>()

                editResponse.playlistCoverUrl()
                    ?: throw IllegalStateException("Playlist cover update returned no thumbnail")
            }
        }

    suspend fun removeCustomPlaylistCover(playlistId: String): Result<String?> =
        runCatching {
            require(playlistId.isNotBlank())
            withPlaylistMutationAuthRecovery {
                innerTube
                    .removePlaylistCustomCover(WEB_REMIX, playlistId)
                    .body<EditPlaylistResponse>()
                    .playlistCoverUrl()
            }
        }

    suspend fun deletePlaylist(playlistId: String) =
        runCatching {
            withPlaylistMutationAuthRecovery {
                innerTube.deletePlaylist(WEB_REMIX, playlistId)
            }
        }

    private fun EditPlaylistResponse.playlistCoverUrl(): String? =
        newHeader
            ?.musicEditablePlaylistDetailHeaderRenderer
            ?.header
            ?.musicResponsiveHeaderRenderer
            ?.thumbnail
            ?.musicThumbnailRenderer
            ?.getThumbnailUrl()

    suspend fun player(
        videoId: String,
        playlistId: String? = null,
        client: YouTubeClient,
        signatureTimestamp: Int? = null,
        poToken: String? = null,
        setLogin: Boolean = true,
        authState: PlaybackAuthState = currentPlaybackAuthState(),
        cpn: String? = null,
    ): Result<PlayerResponse> =
        runCatching {
            val resolvedPoToken = resolvePlayerPoToken(client, poToken, videoId, authState)
            innerTube
                .player(
                    client = client,
                    videoId = videoId,
                    playlistId = playlistId,
                    signatureTimestamp = signatureTimestamp,
                    poToken = resolvedPoToken,
                    setLogin = setLogin,
                    authState = authState,
                    cpn = cpn,
                ).body<PlayerResponse>()
        }

    suspend fun registerPlayback(
        playlistId: String? = null,
        playbackTracking: String,
        authState: PlaybackAuthState = currentPlaybackAuthState(),
    ) = runCatching {
        val cpn =
            (1..16)
                .map {
                    "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"[
                        Random.Default.nextInt(
                            0,
                            64,
                        ),
                    ]
                }.joinToString("")

        innerTube.registerPlayback(
            url = playbackTracking,
            playlistId = playlistId,
            cpn = cpn,
            authState = authState,
        )
    }

    suspend fun next(
        endpoint: WatchEndpoint,
        continuation: String? = null,
        followAutomixPreview: Boolean = true,
    ): Result<NextResult> =
        runCatching {
            val response =
                innerTube
                    .next(
                        WEB_REMIX,
                        endpoint.videoId,
                        endpoint.playlistId,
                        endpoint.playlistSetVideoId,
                        endpoint.index,
                        endpoint.params,
                        continuation,
                    ).body<NextResponse>()
            val playlistPanelRenderer =
                response.continuationContents?.playlistPanelContinuation
                    ?: response.contents.singleColumnMusicWatchNextResultsRenderer
                        ?.tabbedRenderer
                        ?.watchNextTabbedResultsRenderer
                        ?.tabs
                        ?.get(0)
                        ?.tabRenderer
                        ?.content
                        ?.musicQueueRenderer
                        ?.content
                        ?.playlistPanelRenderer!!
            val title =
                response.contents.singleColumnMusicWatchNextResultsRenderer
                    ?.tabbedRenderer
                    ?.watchNextTabbedResultsRenderer
                    ?.tabs
                    ?.get(0)
                    ?.tabRenderer
                    ?.content
                    ?.musicQueueRenderer
                    ?.header
                    ?.musicQueueHeaderRenderer
                    ?.subtitle
                    ?.runs
                    ?.firstOrNull()
                    ?.text
            val items =
                playlistPanelRenderer.contents.mapNotNull { content ->
                    content.playlistPanelVideoRenderer
                        ?.let(NextPage::fromPlaylistPanelVideoRenderer)
                        ?.let { it to content.playlistPanelVideoRenderer.selected }
                }
            val songs = items.map { it.first }
            val currentIndex = items.indexOfFirst { it.second }.takeIf { it != -1 }

            if (followAutomixPreview) {
                // Keep automix opt-in so ordered playlist queues can page through their own continuation first.
                playlistPanelRenderer.contents
                    .lastOrNull()
                    ?.automixPreviewVideoRenderer
                    ?.content
                    ?.automixPlaylistVideoRenderer
                    ?.navigationEndpoint
                    ?.watchPlaylistEndpoint
                    ?.let { watchPlaylistEndpoint ->
                        return@runCatching next(watchPlaylistEndpoint).getOrThrow().let { result ->
                            result.copy(
                                title = title,
                                items = songs + result.items,
                                lyricsEndpoint =
                                    response.contents.singleColumnMusicWatchNextResultsRenderer
                                        ?.tabbedRenderer
                                        ?.watchNextTabbedResultsRenderer
                                        ?.tabs
                                        ?.getOrNull(
                                            1,
                                        )?.tabRenderer
                                        ?.endpoint
                                        ?.browseEndpoint,
                                relatedEndpoint =
                                    response.contents.singleColumnMusicWatchNextResultsRenderer
                                        ?.tabbedRenderer
                                        ?.watchNextTabbedResultsRenderer
                                        ?.tabs
                                        ?.getOrNull(
                                            2,
                                        )?.tabRenderer
                                        ?.endpoint
                                        ?.browseEndpoint,
                                currentIndex = currentIndex,
                                endpoint = watchPlaylistEndpoint,
                            )
                        }
                    }
            }
            NextResult(
                title = title,
                items = songs,
                currentIndex = currentIndex,
                lyricsEndpoint =
                    response.contents.singleColumnMusicWatchNextResultsRenderer
                        ?.tabbedRenderer
                        ?.watchNextTabbedResultsRenderer
                        ?.tabs
                        ?.getOrNull(
                            1,
                        )?.tabRenderer
                        ?.endpoint
                        ?.browseEndpoint,
                relatedEndpoint =
                    response.contents.singleColumnMusicWatchNextResultsRenderer
                        ?.tabbedRenderer
                        ?.watchNextTabbedResultsRenderer
                        ?.tabs
                        ?.getOrNull(
                            2,
                        )?.tabRenderer
                        ?.endpoint
                        ?.browseEndpoint,
                continuation = playlistPanelRenderer.continuations?.getContinuation(),
                endpoint = endpoint,
            )
        }

    suspend fun lyrics(endpoint: BrowseEndpoint): Result<String?> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, endpoint.browseId, endpoint.params).body<BrowseResponse>()
            response.contents
                ?.sectionListRenderer
                ?.contents
                ?.firstOrNull()
                ?.musicDescriptionShelfRenderer
                ?.description
                ?.runs
                ?.firstOrNull()
                ?.text
        }

    suspend fun related(endpoint: BrowseEndpoint): Result<RelatedPage> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, endpoint.browseId).body<BrowseResponse>()
            val songs = mutableListOf<SongItem>()
            val albums = mutableListOf<AlbumItem>()
            val artists = mutableListOf<ArtistItem>()
            val playlists = mutableListOf<PlaylistItem>()
            response.contents?.sectionListRenderer?.contents?.forEach { sectionContent ->
                sectionContent.musicCarouselShelfRenderer?.contents?.forEach { content ->
                    when (
                        val item =
                            content.musicResponsiveListItemRenderer?.let(RelatedPage.Companion::fromMusicResponsiveListItemRenderer)
                                ?: content.musicTwoRowItemRenderer?.let(RelatedPage.Companion::fromMusicTwoRowItemRenderer)
                    ) {
                        is SongItem -> {
                            if (content.musicResponsiveListItemRenderer
                                    ?.overlay
                                    ?.musicItemThumbnailOverlayRenderer
                                    ?.content
                                    ?.musicPlayButtonRenderer
                                    ?.playNavigationEndpoint
                                    ?.watchEndpoint
                                    ?.watchEndpointMusicSupportedConfigs
                                    ?.watchEndpointMusicConfig
                                    ?.musicVideoType == MUSIC_VIDEO_TYPE_ATV
                            ) {
                                songs.add(item)
                            }
                        }

                        is AlbumItem -> {
                            albums.add(item)
                        }

                        is ArtistItem -> {
                            artists.add(item)
                        }

                        is PlaylistItem -> {
                            playlists.add(item)
                        }

                        is EpisodeItem, is PodcastItem -> Unit

                        null -> {}
                    }
                }
            }
            RelatedPage(songs, albums, artists, playlists)
        }

    suspend fun queue(
        videoIds: List<String>? = null,
        playlistId: String? = null,
    ): Result<List<SongItem>> =
        runCatching {
            if (videoIds != null) {
                assert(videoIds.size <= MAX_GET_QUEUE_SIZE) // Max video limit
            }
            innerTube
                .getQueue(WEB_REMIX, videoIds, playlistId)
                .body<GetQueueResponse>()
                .queueDatas
                .mapNotNull {
                    it.content.playlistPanelVideoRenderer?.let { renderer ->
                        NextPage.fromPlaylistPanelVideoRenderer(renderer)
                    }
                }
        }

    suspend fun transcript(videoId: String): Result<String> =
        runCatching {
            val response = innerTube.getTranscript(WEB, videoId).body<GetTranscriptResponse>()
            response.actions
                ?.firstOrNull()
                ?.updateEngagementPanelAction
                ?.content
                ?.transcriptRenderer
                ?.body
                ?.transcriptBodyRenderer
                ?.cueGroups
                ?.joinToString(
                    separator = "\n",
                ) { group ->
                    val time =
                        group.transcriptCueGroupRenderer.cues[0]
                            .transcriptCueRenderer.startOffsetMs
                    val text =
                        group.transcriptCueGroupRenderer.cues[0]
                            .transcriptCueRenderer.cue.simpleText
                            .trim('♪')
                            .trim(' ')
                    "[%02d:%02d.%03d]$text".format(time / 60000, (time / 1000) % 60, time % 1000)
                }!!
        }

    suspend fun visitorData(): Result<String> =
        runCatching {
            Json
                .parseToJsonElement(innerTube.getSwJsData().bodyAsText().substring(5))
                .jsonArray[0]
                .jsonArray[2]
                .jsonArray
                .first {
                    (it as? JsonPrimitive)?.contentOrNull?.let { candidate ->
                        VISITOR_DATA_REGEX.containsMatchIn(candidate)
                    } ?: false
                }.jsonPrimitive.content
        }

    suspend fun accountInfo(): Result<AccountInfo> =
        runCatching {
            val response = innerTube.accountMenu(WEB_REMIX).body<AccountMenuResponse>()
            val accountInfo =
                response.actions
                    .firstOrNull()
                    ?.openPopupAction
                    ?.popup
                    ?.multiPageMenuRenderer
                    ?.header
                    ?.activeAccountHeaderRenderer
                    ?.toAccountInfo()
            accountInfo ?: throw IllegalStateException("Failed to get account info - user may not be logged in")
        }

    suspend fun accountChannels(): Result<List<AccountChannel>> =
        runCatching {
            val response =
                Json.parseToJsonElement(
                    innerTube.accountChannels(accountSwitcherClient).bodyAsText(),
                )

            response
                .objectsNamed("accountItemRenderer")
                .mapNotNull(::parseAccountChannel)
                .sortedByDescending(AccountChannel::isSelected)
                .distinctBy(AccountChannel::dataSyncId)
                .toList()
        }

    suspend fun accountDataSyncId(): Result<String> =
        runCatching {
            val response =
                Json.parseToJsonElement(
                    innerTube.accountChannels(accountSwitcherClient).bodyAsText(),
                )

            response.findMainAppWebDataSyncId()
                ?: response
                    .objectsNamed("accountItemRenderer")
                    .mapNotNull { renderer ->
                        val isDisabled = renderer.booleanValue("isDisabled") ?: false
                        val hasChannel = renderer.booleanValue("hasChannel") ?: true
                        if (isDisabled || !hasChannel) return@mapNotNull null

                        renderer.parseAccountChannelDataSyncId()?.let { dataSyncId ->
                            dataSyncId to (renderer.booleanValue("isSelected") ?: false)
                        }
                    }.sortedByDescending { (_, isSelected) -> isSelected }
                    .firstOrNull()
                    ?.first
                ?: throw IllegalStateException("Failed to get YouTube DataSyncId")
        }

    private fun parseAccountChannel(renderer: JsonObject): AccountChannel? {
        val isDisabled = renderer.booleanValue("isDisabled") ?: false
        val hasChannel = renderer.booleanValue("hasChannel") ?: true
        if (isDisabled || !hasChannel) return null

        val dataSyncId = renderer.parseAccountChannelDataSyncId() ?: return null

        val name = renderer["accountName"].textValue() ?: return null
        val byline = renderer["accountByline"].textValue()
        val channelHandle = renderer["channelHandle"].textValue()
        val thumbnailUrl = renderer["accountPhoto"].thumbnailUrl()

        return AccountChannel(
            name = name,
            byline = byline,
            channelHandle = channelHandle,
            thumbnailUrl = thumbnailUrl,
            dataSyncId = dataSyncId,
            isSelected = renderer.booleanValue("isSelected") ?: false,
        )
    }

    private fun JsonObject.parseAccountChannelDataSyncId(): String? =
        this["serviceEndpoint"]
            ?.findDelegationValue()
            ?.normalizeAccountChannelDataSyncId()

    private fun JsonElement.findMainAppWebDataSyncId(): String? =
        (this as? JsonObject)
            ?.get("responseContext")
            ?.jsonObjectOrNull()
            ?.get("mainAppWebResponseContext")
            ?.jsonObjectOrNull()
            ?.get("dataSyncId")
            ?.jsonPrimitiveOrNull()
            ?.contentOrNull
            ?.normalizeAccountChannelDataSyncId()

    private fun JsonElement.objectsNamed(name: String): Sequence<JsonObject> =
        sequence {
            when (val element = this@objectsNamed) {
                is JsonObject -> {
                    element[name]?.jsonObjectOrNull()?.let { yield(it) }
                    element.values.forEach { yieldAll(it.objectsNamed(name)) }
                }

                is kotlinx.serialization.json.JsonArray -> {
                    element.forEach { yieldAll(it.objectsNamed(name)) }
                }

                else -> {}
            }
        }

    private fun JsonElement?.textValue(): String? {
        val obj = this as? JsonObject ?: return null
        obj["simpleText"]?.jsonPrimitiveOrNull()?.contentOrNull?.let { return it.trim().takeIf(String::isNotBlank) }
        return obj["runs"]
            ?.jsonArrayOrNull()
            ?.joinToString(separator = "") { run ->
                run
                    .jsonObjectOrNull()
                    ?.get("text")
                    ?.jsonPrimitiveOrNull()
                    ?.contentOrNull
                    .orEmpty()
            }?.trim()
            ?.takeIf(String::isNotBlank)
    }

    private fun JsonElement?.thumbnailUrl(): String? {
        val thumbnails =
            (this as? JsonObject)
                ?.get("thumbnails")
                ?.jsonArrayOrNull()
                ?: return null
        return thumbnails
            .asSequence()
            .mapNotNull { thumbnail ->
                thumbnail
                    .jsonObjectOrNull()
                    ?.get("url")
                    ?.jsonPrimitiveOrNull()
                    ?.contentOrNull
            }.lastOrNull()
            ?.let { url -> if (url.startsWith("//")) "https:$url" else url }
    }

    private fun JsonElement.findDelegationValue(): String? {
        val directKeys =
            setOf(
                "onBehalfOfUser",
                "obfuscatedSelectedGaiaId",
                "obfuscatedGaiaId",
                "accountId",
                "delegatedSessionId",
            )
        val fallbackKeys =
            setOf(
                "selectedSerializedDelegationContext",
                "serializedDelegationContext",
            )
        return findStringValue(directKeys) ?: findStringValue(fallbackKeys)
    }

    private fun JsonElement.findStringValue(keys: Set<String>): String? =
        when (this) {
            is JsonObject -> {
                keys.firstNotNullOfOrNull { key ->
                    this[key]
                        ?.jsonPrimitiveOrNull()
                        ?.contentOrNull
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
                } ?: values.firstNotNullOfOrNull { it.findStringValue(keys) }
            }

            is kotlinx.serialization.json.JsonArray -> {
                firstNotNullOfOrNull { it.findStringValue(keys) }
            }

            else -> {
                null
            }
        }

    private fun JsonObject.booleanValue(key: String): Boolean? = this[key]?.jsonPrimitiveOrNull()?.contentOrNull?.toBooleanStrictOrNull()

    private fun JsonElement?.jsonObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement?.jsonArrayOrNull(): kotlinx.serialization.json.JsonArray? = this as? kotlinx.serialization.json.JsonArray

    private fun JsonElement?.jsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

    private fun String.normalizeAccountChannelDataSyncId(): String? {
        val normalized =
            trim()
                .takeIf(String::isNotBlank)
                ?.let { value ->
                    value
                        .takeIf { !it.contains("||") }
                        ?: value.takeIf { it.endsWith("||") }?.substringBefore("||")
                        ?: value.substringAfter("||")
                }?.trim()
                ?.takeIf(String::isNotBlank)
        return normalized
    }

    suspend fun getMediaInfo(videoId: String): Result<MediaInfo> =
        runCatching {
            return innerTube.getMediaInfo(videoId)
        }

    /**
     * Lightweight like-count lookup for the player's TikTok-style like label
     * (user request 2026-09-03). See [InnerTube.getLikeCount].
     */
    suspend fun getLikeCount(videoId: String): Result<Int?> = innerTube.getLikeCount(videoId)

    @JvmInline
    value class SearchFilter(
        val value: String,
    ) {
        companion object {
            val FILTER_SONG = SearchFilter("EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D")
            val FILTER_VIDEO = SearchFilter("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_ALBUM = SearchFilter("EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_ARTIST = SearchFilter("EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_FEATURED_PLAYLIST = SearchFilter("EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ%3D")
            val FILTER_COMMUNITY_PLAYLIST = SearchFilter("EgeKAQQoAEABagoQAxAEEAoQCRAF")
        }
    }

    @JvmInline
    value class LibraryFilter(
        val value: String,
    ) {
        companion object {
            val FILTER_RECENT_ACTIVITY = LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpYnJhcnlfbGFuZGluZxoQZ2dNR0tnUUlCaEFCb0FZQg%3D%3D")
            val FILTER_RECENTLY_PLAYED = LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpYnJhcnlfbGFuZGluZxoQZ2dNR0tnUUlCUkFCb0FZQg%3D%3D")
            val FILTER_PLAYLISTS_ALPHABETICAL = LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpa2VkX3BsYXlsaXN0cxoQZ2dNR0tnUUlBUkFBb0FZQg%3D%3D")
            val FILTER_PLAYLISTS_RECENTLY_SAVED = LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpa2VkX3BsYXlsaXN0cxoQZ2dNR0tnUUlBQkFCb0FZQg%3D%3D")
        }
    }

    const val MAX_GET_QUEUE_SIZE = 1000
    private const val DEFAULT_PLAYLIST_EDIT_BATCH_SIZE = 50

    private val VISITOR_DATA_REGEX = Regex("^Cg[t|s]")
}

private fun SectionListRenderer.Content.libraryItems(): List<YTItem> =
    buildList {
        addAll(
            gridRenderer
                ?.items
                .orEmpty()
                .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                .mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) },
        )
        addAll(
            musicCarouselShelfRenderer
                ?.contents
                .orEmpty()
                .mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
                .mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) },
        )
        addAll(
            musicShelfRenderer
                ?.contents
                .orEmpty()
                .mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                .mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) },
        )
        addAll(
            musicPlaylistShelfRenderer
                ?.contents
                .orEmpty()
                .mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                .mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) },
        )
        itemSectionRenderer?.contents.orEmpty().forEach { content ->
            content.musicResponsiveListItemRenderer
                ?.let { LibraryPage.fromMusicResponsiveListItemRenderer(it) }
                ?.let(::add)
            addAll(
                content.musicShelfRenderer
                    ?.contents
                    .orEmpty()
                    .mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                    .mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) },
            )
            addAll(
                content.gridRenderer
                    ?.items
                    .orEmpty()
                    .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                    .mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) },
            )
        }
    }

private fun SectionListRenderer.Content.libraryContinuation(): String? =
    gridRenderer?.continuations?.getContinuation()
        ?: musicShelfRenderer?.contents.orEmpty().getContinuation()
        ?: musicShelfRenderer?.continuations?.getContinuation()
        ?: musicPlaylistShelfRenderer?.contents.orEmpty().getContinuation()
        ?: musicPlaylistShelfRenderer?.continuations?.getContinuation()
        ?: itemSectionRenderer?.contents.orEmpty().firstNotNullOfOrNull { content ->
            content.musicShelfRenderer
                ?.contents
                .orEmpty()
                .getContinuation()
                ?: content.musicShelfRenderer?.continuations?.getContinuation()
                ?: content.gridRenderer?.continuations?.getContinuation()
        }

private fun SectionListRenderer.Content.playlistSongContents(): List<MusicShelfRenderer.Content> =
    buildList {
        addAll(musicPlaylistShelfRenderer?.contents.orEmpty())
        addAll(musicShelfRenderer?.contents.orEmpty())
        itemSectionRenderer?.contents.orEmpty().forEach { content ->
            content.musicResponsiveListItemRenderer?.let { renderer ->
                add(
                    MusicShelfRenderer.Content(
                        musicResponsiveListItemRenderer = renderer,
                        continuationItemRenderer = null,
                    ),
                )
            }
            addAll(content.musicShelfRenderer?.contents.orEmpty())
        }
    }

private fun SectionListRenderer.Content.playlistSongContinuation(): String? =
    musicPlaylistShelfRenderer?.let { shelf ->
        shelf.contents.getContinuation() ?: shelf.continuations?.getContinuation()
    } ?: musicShelfRenderer?.let { shelf ->
        shelf.contents.orEmpty().getContinuation() ?: shelf.continuations?.getContinuation()
    } ?: itemSectionRenderer?.contents.orEmpty().firstNotNullOfOrNull { content ->
        content.musicShelfRenderer?.let { shelf ->
            shelf.contents.orEmpty().getContinuation() ?: shelf.continuations?.getContinuation()
        }
    }

internal fun playlistContinuationPageFromResponse(
    response: BrowseResponse,
    playlistId: String? = null,
): PlaylistContinuationPage {
    val appendedContents =
        response.onResponseReceivedActions
            ?.firstOrNull()
            ?.appendContinuationItemsAction
            ?.continuationItems
            .orEmpty()

    val candidates =
        listOf(
            PlaylistContinuationCandidate(
                contents =
                    buildList {
                        response.continuationContents
                            ?.sectionListContinuation
                            ?.contents
                            .orEmpty()
                            .forEach { sectionContent ->
                                addAll(sectionContent.playlistSongContents())
                            }
                        addAll(appendedContents)
                    },
                continuation =
                    response.continuationContents
                        ?.sectionListContinuation
                        ?.continuations
                        ?.getContinuation()
                        ?: appendedContents.getContinuation(),
            ),
            PlaylistContinuationCandidate(
                contents =
                    response.continuationContents
                        ?.musicPlaylistShelfContinuation
                        ?.contents
                        .orEmpty(),
                continuation =
                    response.continuationContents
                        ?.musicPlaylistShelfContinuation
                        ?.continuations
                        ?.getContinuation(),
            ),
            PlaylistContinuationCandidate(
                contents =
                    response.continuationContents
                        ?.musicShelfContinuation
                        ?.contents
                        .orEmpty(),
                continuation =
                    response.continuationContents
                        ?.musicShelfContinuation
                        ?.continuations
                        ?.getContinuation(),
            ),
            PlaylistContinuationCandidate(
                contents = appendedContents,
                continuation = appendedContents.getContinuation(),
            ),
        ).map { candidate ->
            candidate.copy(
                songs =
                    candidate.contents
                        .mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                        .mapNotNull { renderer ->
                            PlaylistPage.fromMusicResponsiveListItemRenderer(renderer, playlistId)
                        }.distinctByPlaylistEntry(),
            )
        }

    val selectedCandidate =
        candidates.firstOrNull { it.songs.isNotEmpty() }
            ?: candidates.firstOrNull { it.contents.isNotEmpty() }

    return PlaylistContinuationPage(
        songs = selectedCandidate?.songs.orEmpty(),
        continuation = selectedCandidate?.continuation?.takeUnless(String::isBlank),
    )
}

private data class PlaylistContinuationCandidate(
    val contents: List<MusicShelfRenderer.Content>,
    val continuation: String?,
    val songs: List<SongItem> = emptyList(),
)
