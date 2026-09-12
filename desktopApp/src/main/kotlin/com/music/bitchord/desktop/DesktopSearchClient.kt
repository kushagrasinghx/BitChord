package com.music.bitchord.desktop

import com.music.bitchord.data.model.ArtistPage
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.HomeFeed
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.SubscriptionState
import com.music.bitchord.data.model.UserPlaylist
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.putJsonArray
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** The desktop-facing, anonymous part of BitChord's YouTube Music client. */
object DesktopSearchClient {
    private const val BASE = "https://music.youtube.com/youtubei/v1"
    internal const val CLIENT_VERSION = "1.20250101.01.00"
    private const val CLIENT_NAME = "WEB_REMIX"

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val moodGenreShelfCache = ConcurrentHashMap<String, List<HomeShelf>>()

    suspend fun search(
        query: String,
        filter: SearchFilter = SearchFilter.SONGS,
    ): Result<List<SearchResult>> = runCatching {
        require(query.isNotBlank()) { "Search query cannot be empty" }
        val response = post("search") {
            put("query", query.trim())
            filter.params?.let { put("params", it) }
        }
        parseSearch(response, filter)
    }

    /** The first page of the home feed, with its own continuation token. */
    suspend fun home(): Result<HomeFeed> = runCatching {
        val response = post("browse") { put("browseId", "FEmusic_home") }
        HomeFeed(parseShelves(response), continuationToken(response))
    }

    /** More home shelves past the first page, following the feed's own token. */
    suspend fun moreHome(token: String): Result<HomeFeed> = runCatching {
        val response = post("browse", continuation = token) { put("continuation", token) }
        HomeFeed(parseShelves(response), continuationToken(response))
    }

    /** Shelves from a feed that supplements home — new releases and the editorial explore page. */
    suspend fun homeSupplement(browseId: String): Result<List<HomeShelf>> = runCatching {
        parseShelves(post("browse") { put("browseId", browseId) })
    }

    /** The browse feeds whose shelves are shown under the home feed's own. */
    val HOME_SUPPLEMENT_BROWSE_IDS = listOf("FEmusic_new_releases", "FEmusic_explore")

    /** The token for the next page, wherever this response chose to put it. */
    internal fun continuationToken(root: JsonObject): String? {
        collectRenderers(root, "continuationItemRenderer").firstOrNull()
            ?.let { it["continuationEndpoint"] as? JsonObject }
            ?.let { it["continuationCommand"] as? JsonObject }
            ?.let { (it["token"] as? JsonPrimitive)?.contentOrNull }
            ?.let { return it }
        return collectRenderers(root, "nextContinuationData").firstOrNull()
            ?.let { (it["continuation"] as? JsonPrimitive)?.contentOrNull }
    }

    /** The server-defined mood and genre categories behind Explore. */
    suspend fun moodAndGenres(): Result<List<MoodGenreSection>> = runCatching {
        val response = post("browse") { put("browseId", "FEmusic_moods_and_genres") }
        parseMoodAndGenres(response)
    }

    /** The playlist shelves behind one category. */
    suspend fun moodGenreShelves(browseId: String, params: String?): Result<List<HomeShelf>> {
        val key = "$browseId:${params.orEmpty()}"
        moodGenreShelfCache[key]?.let { return Result.success(it) }
        return runCatching {
            val response = post("browse") {
                put("browseId", browseId)
                params?.let { put("params", it) }
            }
            parseShelves(response)
        }.onSuccess { moodGenreShelfCache.putIfAbsent(key, it) }
    }

    /** A category card borrows the first real cover from the playlists it opens. */
    suspend fun moodGenreArtwork(browseId: String, params: String?): Result<String?> =
        moodGenreShelves(browseId, params).map { shelves ->
            shelves.asSequence()
                .flatMap { it.items.asSequence() }
                .mapNotNull(ShelfItem::thumbnailUrl)
                .firstOrNull()
        }

    suspend fun browse(browseId: String, fallback: BrowseItem? = null): Result<DesktopCollection> = runCatching {
        val response = post("browse") { put("browseId", browseId) }
        val header = response.browseHeader()
        val subtitle = header?.subtitle?.takeIf(String::isNotBlank)
            ?: fallback?.subtitle.orEmpty()
        val artistFallback = listOfNotNull(
            header?.subtitle,
            fallback?.subtitle,
        ).firstNotNullOfOrNull(::artistFromCollectionSubtitle)
        DesktopCollection(
            browseId = browseId,
            title = header?.title?.takeIf(String::isNotBlank)
                ?: fallback?.title?.takeIf(String::isNotBlank)
                ?: "Collection",
            subtitle = subtitle,
            thumbnailUrl = header?.thumbnailUrl ?: fallback?.thumbnailUrl,
            type = fallback?.type?.takeIf { it != BrowseType.OTHER }
                ?: browseTypeOf(browseId),
            songs = parseBrowseSongs(response, artistFallback),
            continuation = collectionContinuation(response),
            // Only asked of a playlist: an album or an artist page has no owner in the sense that
            // Rename and Delete mean.
            owned = if (browseId.startsWith("VL")) parsePlaylistOwned(response) else null,
        )
    }

    /** The rows after a collection's first page, and the token after those. */
    suspend fun moreCollectionSongs(
        token: String,
        artistFallback: String? = null,
    ): Result<Pair<List<Song>, String?>> = runCatching {
        val response = post("browse", continuation = token) { put("continuation", token) }
        parseBrowseSongs(response, artistFallback) to collectionContinuation(response)
    }

    /** The watch queue for a track: what plays after it, and what it belongs to. */
    private suspend fun next(videoId: String): JsonObject = post("next") {
        put("videoId", videoId)
        put("playlistId", "RDAMVM$videoId")
        put("isAudioOnly", true)
    }

    /**
     * The account's own listening history, from YouTube Music.
     *
     * What Android's History screen is, in full: the feed the account sees on
     * every device, so a track played on a phone shows up here and one played
     * here shows up there. The desktop's local list is a fallback for a signed
     * out session, not the source.
     */
    suspend fun history(): Result<List<Song>> = runCatching {
        parseBrowseSongs(post("browse") { put("browseId", HISTORY_FEED) }, fallbackArtist = null)
    }

    /** The station that plays on after [videoId] — YouTube Music's own radio. */
    suspend fun radio(videoId: String): Result<List<Song>> = runCatching {
        parseWatchQueue(next(videoId))
    }

    /**
     * The album and artist a track belongs to.
     *
     * Whatever started a track knew its title and its artwork, but rarely which
     * pages it belongs to: a tile in the home feed carries a flattened subtitle
     * and no ids at all, so a song played from there reaches the player with
     * nothing to open. The watch queue does carry them, which is why this is a
     * lookup rather than better parsing upstream.
     */
    suspend fun trackLinks(videoId: String): Result<Song> = runCatching {
        parseWatchQueue(next(videoId))
            .firstOrNull { it.videoId == videoId }
            ?: error("no watch entry for $videoId")
    }

    /** The tracks in a watch queue response. */
    internal fun parseWatchQueue(root: JsonObject): List<Song> {
        val out = LinkedHashMap<String, Song>()
        collectRenderers(root, "playlistPanelVideoRenderer").forEach { renderer ->
            val videoId = renderer["videoId"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val title = renderer["title"].runs()
                .mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                .joinToString("")
                .trim()
            if (title.isBlank()) return@forEach

            val bylineRuns = renderer["longBylineText"].runs()
            val byline = bylineRuns.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            val artist = byline.takeWhile { !it.contains("•") }.joinToString("").trim()
            val credits = creditsOf(bylineRuns)

            out[videoId] = Song(
                videoId = videoId,
                title = title,
                artist = artist.ifBlank { credits.artistName.orEmpty() },
                thumbnailUrl = renderer["thumbnail"]?.let(::findThumbnailUrl),
                durationText = renderer["lengthText"].runs()
                    .mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                    .joinToString("")
                    .takeIf { it.isNotBlank() },
                artistId = credits.artistId,
                albumId = credits.albumId,
                albumName = credits.albumName,
                // A catalogue track is credited "Artist • Album • Year"; the matching music video
                // is "Artist • 417M views • 2.4M likes".
                isVideo = byline.any { it.contains("views", ignoreCase = true) },
            )
        }
        return out.values.toList()
    }

    private suspend fun post(
        path: String,
        continuation: String? = null,
        body: JsonObjectBuilder.() -> Unit,
    ): JsonObject {
        val visitorData = DesktopYouTubeSession.ensureVisitorData()
        val response = client.post("$BASE/$path") {
            parameter("prettyPrint", "false")
            // A continuation is asked for in the query string as well as the body; the endpoint
            // answers an envelope of the same row renderers.
            continuation?.let {
                parameter("ctoken", it)
                parameter("continuation", it)
                parameter("type", "next")
            }
            contentType(ContentType.Application.Json)
            header("Origin", "https://music.youtube.com")
            header("Referer", "https://music.youtube.com/")
            header("X-YouTube-Client-Name", "67")
            header("X-YouTube-Client-Version", CLIENT_VERSION)
            visitorData?.let { header("X-Goog-Visitor-Id", it) }
            // Signed and addressed to one account when there is a session, and nothing at all when
            // there is not.
            val signed = DesktopYouTubeAuth.headers(DesktopYouTubeAuth.MUSIC_ORIGIN)
            if (signed.isNotEmpty()) {
                signed.forEach { (name, value) -> header(name, value) }
            } else {
                DesktopYouTubeAuth.environmentCookie()?.let { header("Cookie", it) }
            }
            setBody(
                buildJsonObject {
                    putJsonObject("context") {
                        putJsonObject("client") {
                            put("clientName", CLIENT_NAME)
                            put("clientVersion", CLIENT_VERSION)
                                put("hl", "en")
                                put("gl", "US")
                                visitorData?.let { put("visitorData", it) }
                        }
                        putJsonObject("user") {
                            put("lockedSafetyMode", false)
                            // Which account in the jar this request is about.
                            DesktopYouTubeAuth.onBehalfOfUser()?.let { put("onBehalfOfUser", it) }
                        }
                        // Both of these are sent on every request Android makes.
                        putJsonObject("request") { put("useSsl", true) }
                    }
                    body()
                },
            )
        }
        // A session Google will not accept must not be allowed to take the whole app with it.
        if (response.status.value == 401 && DesktopYouTubeAuth.isSignedIn) {
            DesktopTrackLog.log("youtube: the session was refused; continuing as a guest")
            DesktopYouTubeAuth.adopt(null)
            return post(path, continuation, body)
        }
        check(response.status.value in 200..299) {
            "YouTube Music returned HTTP ${response.status.value}"
        }
        return response.body<JsonObject>().also(DesktopYouTubeSession::capture)
    }

    internal fun parseSearch(root: JsonObject, filter: SearchFilter): List<SearchResult> {
        val seen = HashSet<String>()
        // The rows tucked inside an artist's promoted card, paired with the credit that card bills
        // them to. Matched by identity below, because these are the same renderer objects the walk
        // further down already finds; a card row is just a row that also sits here.
        val cardCredits: List<Pair<JsonObject, CardCredit>> = if (filter == SearchFilter.VIDEOS) {
            emptyList()
        } else {
            collectRenderers(root, "musicCardShelfRenderer").flatMap { card ->
                val credit = cardShelfCredit(card) ?: return@flatMap emptyList()
                collectRenderers(card, "musicResponsiveListItemRenderer").map { it to credit }
            }
        }
        return buildList {
            collectRenderers(root, "musicCardShelfRenderer")
                .mapNotNull(::parseCardShelfSong)
                .filter { !it.isVideo || filter == SearchFilter.VIDEOS }
                .forEach { song ->
                    if (seen.add("v:${song.videoId}")) add(SearchResult.TopTrack(song))
            }

            collectRenderers(root, "musicResponsiveListItemRenderer").forEach { renderer ->
                // A browse row has a direct navigationEndpoint.
                parseBrowseItem(renderer)?.let { item ->
                    val shouldInclude = when (filter) {
                        SearchFilter.ALL -> true
                        SearchFilter.ALBUMS -> item.type == BrowseType.ALBUM
                        SearchFilter.ARTISTS -> item.type == BrowseType.ARTIST
                        SearchFilter.PLAYLISTS -> item.type == BrowseType.PLAYLIST
                        else -> false
                    }
                    if (shouldInclude && seen.add("b:${item.browseId}")) {
                        add(SearchResult.Browse(item))
                    }
                    return@forEach
                }
                val card = cardCredits.firstOrNull { it.first === renderer }?.second
                parseSong(renderer, card?.name, card?.artistId)?.let { song ->
                    val shouldInclude = when (filter) {
                        SearchFilter.VIDEOS -> song.isVideo
                        SearchFilter.ALBUMS, SearchFilter.ARTISTS, SearchFilter.PLAYLISTS -> false
                        else -> !song.isVideo
                    }
                    if (shouldInclude && seen.add("v:${song.videoId}")) {
                        add(SearchResult.Track(song))
                    }
                }
            }
        }.take(100)
    }

    internal fun parseMoodAndGenres(root: JsonObject): List<MoodGenreSection> =
        collectRenderers(root, "gridRenderer").mapNotNull { grid ->
            val title = grid["header"].textValue()
            val items = (grid["items"] as? JsonArray).orEmpty().mapNotNull { entry ->
                val button = (entry as? JsonObject)
                    ?.get("musicNavigationButtonRenderer") as? JsonObject
                    ?: return@mapNotNull null
                // A category button carries its destination on either key depending on where in the
                // page it was rendered.
                val endpoint = ((button["clickCommand"] as? JsonObject)?.get("browseEndpoint")
                    ?: (button["navigationEndpoint"] as? JsonObject)?.get("browseEndpoint"))
                    as? JsonObject ?: return@mapNotNull null
                val browseId = (endpoint["browseId"] as? JsonPrimitive)?.contentOrNull
                    ?: return@mapNotNull null
                val label = button["buttonText"].textValue().takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                MoodGenre(label, browseId, (endpoint["params"] as? JsonPrimitive)?.contentOrNull)
            }
            if (title.isBlank() || items.isEmpty()) null else MoodGenreSection(title, items)
        }

    internal fun parseShelves(root: JsonObject): List<HomeShelf> = buildList {
        val renderers = collectRenderers(root, "musicShelfRenderer") +
            collectRenderers(root, "musicCarouselShelfRenderer")
        val seenTitles = HashSet<String>()
        renderers.forEach { shelf ->
            val title = shelf.headerText()
            // A whole shelf of video compilations.
            if (VIDEO_WORD.containsMatchIn(title)) return@forEach
            val items = buildList {
                val contents = (shelf["contents"] as? JsonArray).orEmpty()
                contents.forEach { content ->
                    val renderer = content.jsonObject
                    parseSong(renderer)?.let { song ->
                        // A video row inside an otherwise musical shelf goes the same way: its
                        // title is a video title.
                        if (!song.isVideo) {
                            add(ShelfItem(song.title, song.artist, song.thumbnailUrl, song.videoId, null))
                        }
                    } ?: parseTwoRowItem(renderer)?.let(::add)
                }
            }.distinctBy { it.videoId ?: it.browseId ?: it.title }
            if (title.isNotBlank() && items.isNotEmpty() && seenTitles.add(title)) {
                add(HomeShelf(title = title, subtitle = shelf.headerSubtitle(), items = items))
            }
        }
    }

    /** Shelves and rows whose subject is video rather than music. */
    private val VIDEO_WORD = Regex("""\bvideos?\b""", RegexOption.IGNORE_CASE)

    internal fun parseBrowseSongs(root: JsonObject, fallbackArtist: String?): List<Song> {
        val playlistScope = playlistShelf(root)
            // Not a playlist-shaped page at all — an album, an artist, the history feed — so the
            // layout-agnostic walk is the right read.
            ?: return collectRenderers(root, "musicResponsiveListItemRenderer")
                .mapNotNull { parseSong(it, fallbackArtist) }
                .distinctBy(Song::videoId)
        return collectRenderers(playlistScope, "musicResponsiveListItemRenderer")
            .mapNotNull { parseSong(it, fallbackArtist) }
            .distinctBy(Song::videoId)
    }

    /** A playlist page's own shelf of rows, or null when the page has none. */
    private fun playlistShelf(root: JsonObject): JsonElement? {
        val scope = root["continuationContents"]
            ?: root["contents"]?.jsonObject
                ?.get("twoColumnBrowseResultsRenderer")?.jsonObject
                ?.get("secondaryContents")
            ?: return null
        (scope as? JsonObject)?.get("musicPlaylistShelfContinuation")?.let { return it }
        collectRenderers(scope, "musicPlaylistShelfRenderer").firstOrNull()?.let { return it }
        val emptiedPlaylist = collectRenderers(scope, "musicShelfRenderer")
            .any { it["title"].textValue().trim() == "Suggestions" }
        return if (emptiedPlaylist) JsonObject(emptyMap()) else null
    }

    /** The token for a collection's next page of *rows*. */
    internal fun collectionContinuation(root: JsonObject): String? {
        val shelf = playlistShelf(root) ?: return continuationToken(root)
        return (shelf as? JsonObject)?.let(::continuationToken)
    }

    private fun artistFromCollectionSubtitle(subtitle: String): String? = subtitle
        .split(" • ", " · ", " | ")
        .firstOrNull {
            it.isNotBlank() &&
                it.lowercase(Locale.ROOT) !in TYPE_WORDS &&
                !it.matches(TALLY) &&
                !it.matches(DURATION) &&
                !it.matches(YEAR)
        }

    private fun parseSongs(root: JsonObject): List<Song> =
        collectRenderers(root, "musicResponsiveListItemRenderer")
            .mapNotNull(::parseSong)
            .distinctBy(Song::videoId)

    /** Who the rows inside a promoted card are by — see [cardShelfCredit]. */
    private data class CardCredit(val name: String, val artistId: String?)

    /**
     * Who the rows inside a promoted card are by, or null if the card isn't one that bills them.
     *
     * An artist card is a header with a track list under it: searching "mc stan" promotes the artist
     * and hangs three of their songs off the card, and those rows say only "Song • 3:16", so a row
     * read on its own came back as "Unknown Artist".
     *
     * Only artist cards, which is why this reads `onTap` rather than the subtitle. A song or video
     * card's rows are *related* uploads rather than its own, so lending them the card's credit would
     * put the wrong name on rows that were not missing one.
     */
    private fun cardShelfCredit(card: JsonObject): CardCredit? {
        val renderer = card["musicCardShelfRenderer"]?.jsonObject ?: card
        val endpoint = renderer["onTap"]?.jsonObject?.get("browseEndpoint")?.jsonObject ?: return null
        val pageType = endpoint["browseEndpointContextSupportedConfigs"]?.jsonObject
            ?.get("browseEndpointContextMusicConfig")?.jsonObject
            ?.get("pageType")?.jsonPrimitive?.contentOrNull.orEmpty()
        if ("ARTIST" !in pageType) return null
        val name = renderer["title"].textValue().trim().takeIf { it.isNotBlank() } ?: return null
        // Deliberately no album: the card says who the song is by and nothing about which release it
        // came off, and a guess there would show up as a wrong "Open album" in the row's own menu.
        return CardCredit(name, endpoint["browseId"]?.jsonPrimitive?.contentOrNull)
    }

    private fun parseSong(
        root: JsonElement,
        fallbackArtist: String? = null,
        fallbackArtistId: String? = null,
    ): Song? {
        val renderer = when (root) {
            is JsonObject -> root["musicResponsiveListItemRenderer"]?.jsonObject ?: root
            else -> return null
        }
        val videoId = renderer["playlistItemData"]?.jsonObject?.get("videoId")
            ?.jsonPrimitive?.contentOrNull
            ?: renderer["overlay"]?.jsonObject
                ?.get("musicItemThumbnailOverlayRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("musicPlayButtonRenderer")?.jsonObject
                ?.get("playNavigationEndpoint")?.jsonObject
                ?.get("watchEndpoint")?.jsonObject
                ?.get("videoId")?.jsonPrimitive?.contentOrNull
            ?: findString(renderer, "videoId")
            ?: return null
        val columns = renderer["flexColumns"] as? JsonArray ?: return null
        val title = columns.getOrNull(0).textValue().trim()
        if (title.isBlank()) return null
        val subtitle = columns.getOrNull(1).textValue().trim()
        val parts = subtitle.split(" • ", " · ", " | ").filter { it.isNotBlank() }
        val rowType = parts.firstOrNull()?.lowercase()
        val duration = parts.firstOrNull { it.matches(DURATION) }
            ?: findStrings(renderer, "text").firstOrNull { it.matches(DURATION) }
        val credits = creditsOf(columns.flatMap { it.responsiveRuns() })
        val artist = credits.artistName?.takeIf(String::isNotBlank)
            ?: parts.firstOrNull {
                !it.matches(DURATION) && it.lowercase(Locale.ROOT) !in TYPE_WORDS && !it.matches(TALLY)
            }
            ?: fallbackArtist
            ?: "Unknown Artist"
        // What the row *is*, decided the way Android decides it: the subtitle says so, or the
        // artwork is not square.
        val isVideo = rowType == "video" || findThumbnails(renderer).isNotSquare()
        return Song(
            videoId = videoId,
            title = title,
            artist = artist,
            thumbnailUrl = findThumbnailUrl(renderer),
            durationText = duration,
            artistId = credits.artistId ?: fallbackArtistId,
            albumId = credits.albumId,
            albumName = credits.albumName,
            setVideoId = renderer["playlistItemData"]?.jsonObject
                ?.get("playlistSetVideoId")?.jsonPrimitive?.contentOrNull,
            isVideo = isVideo,
            isVideoOrigin = isVideo,
        )
    }

    private fun parseCardShelfSong(root: JsonObject): Song? {
        val renderer = root["musicCardShelfRenderer"]?.jsonObject ?: root
        val videoId = renderer["onTap"]?.jsonObject
            ?.get("watchEndpoint")?.jsonObject
            ?.get("videoId")?.jsonPrimitive?.contentOrNull
            ?: return null
        val title = renderer["title"].textValue().trim()
        if (title.isBlank()) return null
        val parts = renderer["subtitle"].textValue()
            .split(" • ", " · ", " | ").filter { it.isNotBlank() }
        val duration = parts.firstOrNull { it.matches(DURATION) }
        val credits = creditsOf(renderer["subtitle"].runs())
        val artist = credits.artistName?.takeIf(String::isNotBlank)
            ?: parts.firstOrNull {
                !it.matches(DURATION) && it.lowercase(Locale.ROOT) !in TYPE_WORDS && !it.matches(TALLY)
            }
            ?: "Unknown Artist"
        return Song(
            videoId = videoId,
            title = title,
            artist = artist,
            thumbnailUrl = findThumbnailUrl(renderer),
            durationText = duration,
            artistId = credits.artistId,
            albumId = credits.albumId,
            albumName = credits.albumName,
        )
    }

    /** A card, from the wrapper the row it sits in names it by. */
    private fun parseTwoRowItem(root: JsonObject): ShelfItem? =
        twoRowItem(root["musicTwoRowItemRenderer"]?.jsonObject ?: return null)

    /**
     * A card, from the renderer itself — which is what [collectRenderers] hands back, having
     * already unwrapped it.
     */
    private fun twoRowItem(renderer: JsonObject): ShelfItem? {
        val title = renderer["title"].textValue().trim()
        if (title.isBlank()) return null
        val subtitle = renderer["subtitle"].textValue().trim()
        val navigation = renderer["navigationEndpoint"]?.jsonObject
        val videoId = findString(navigation ?: renderer, "videoId")
        val browseId = findString(navigation ?: renderer, "browseId")
        return ShelfItem(title, subtitle, findThumbnailUrl(renderer), videoId, browseId)
    }

    private fun parseBrowseItem(root: JsonObject): BrowseItem? {
        val renderer = root["musicResponsiveListItemRenderer"]?.jsonObject ?: root
        val endpoint = renderer["navigationEndpoint"]?.jsonObject
            ?.get("browseEndpoint")?.jsonObject ?: return null
        val browseId = endpoint["browseId"]?.jsonPrimitive?.contentOrNull ?: return null
        val columns = renderer["flexColumns"] as? JsonArray ?: return null
        val title = columns.getOrNull(0).textValue().trim()
        if (title.isBlank()) return null
        val subtitle = columns.getOrNull(1).textValue().trim()
        val pageType = endpoint["browseEndpointContextSupportedConfigs"]?.jsonObject
            ?.get("browseEndpointContextMusicConfig")?.jsonObject
            ?.get("pageType")?.jsonPrimitive?.contentOrNull.orEmpty()
        val type = when {
            "ALBUM" in pageType || browseId.startsWith("MPREb_") -> BrowseType.ALBUM
            "PLAYLIST" in pageType || browseId.startsWith("VL") -> BrowseType.PLAYLIST
            "ARTIST" in pageType || browseId.startsWith("UC") -> BrowseType.ARTIST
            else -> BrowseType.OTHER
        }
        return BrowseItem(browseId, title, subtitle, findThumbnailUrl(renderer), type)
    }

    private fun JsonObject.browseHeader(): DesktopBrowseHeader? {
        val header = DESKTOP_HEADER_RENDERERS.firstNotNullOfOrNull { name ->
            collectRenderers(this, name).firstOrNull()
        } ?: return null
        val title = header["title"].textValue().trim()
        if (title.isBlank()) return null
        val subtitle = DESKTOP_HEADER_CREDIT_LINES
            .map { header[it].textValue().trim() }
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(" • ")
        return DesktopBrowseHeader(
            title = title,
            subtitle = subtitle,
            thumbnailUrl = findThumbnailUrl(header),
        )
    }

    /** A shelf's heading. */
    private fun JsonObject.headerText(): String =
        this["header"].textValue().trim().ifBlank { this["title"].textValue().trim() }

    private fun JsonObject.headerSubtitle(): String =
        findStrings(this["header"] ?: return "", "text")
            .firstOrNull { it != headerText() }
            .orEmpty()

    private fun JsonElement?.textValue(): String {
        when (this) {
            null -> return ""
            is JsonPrimitive -> return contentOrNull.orEmpty()
            is JsonObject -> {
                // Most row columns are wrapped one level below the renderer itself.
                val nested = this["musicResponsiveListItemFlexColumnRenderer"]
                    ?: this["musicTwoRowItemRenderer"]
                    ?: this["musicCarouselShelfBasicHeaderRenderer"]
                    ?: this["gridHeaderRenderer"]
                    ?: this["text"]
                    ?: this["title"]
                    ?: this["subtitle"]
                if (nested != null) {
                    textValueOf(nested)?.let { return it }
                }
                val runs = this["runs"] as? JsonArray
                return runs?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                    ?.joinToString("")
                    ?: this["simpleText"]?.jsonPrimitive?.contentOrNull.orEmpty()
            }
            else -> return ""
        }
    }

    private fun textValueOf(element: JsonElement): String? {
        val value = element.textValue().takeIf(String::isNotBlank)
        return value
    }

    private fun JsonElement?.responsiveRuns(): List<JsonElement> {
        val renderer = (this as? JsonObject)
            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
            ?: return emptyList()
        return (renderer["text"] as? JsonObject)?.get("runs") as? JsonArray ?: emptyList()
    }

    private fun JsonElement?.runs(): List<JsonElement> =
        (this as? JsonObject)?.get("runs") as? JsonArray ?: emptyList()

    private data class Credits(
        val artistId: String? = null,
        val artistName: String? = null,
        val albumId: String? = null,
        val albumName: String? = null,
    )

    private fun creditsOf(runs: List<JsonElement>): Credits {
        var credits = Credits()
        runs.forEach { run ->
            val browse = run.jsonObject["navigationEndpoint"]?.jsonObject
                ?.get("browseEndpoint")?.jsonObject ?: return@forEach
            val id = browse["browseId"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val pageType = browse["browseEndpointContextSupportedConfigs"]?.jsonObject
                ?.get("browseEndpointContextMusicConfig")?.jsonObject
                ?.get("pageType")?.jsonPrimitive?.contentOrNull.orEmpty()
            credits = when {
                "ARTIST" in pageType && credits.artistId == null ->
                    credits.copy(
                        artistId = id,
                        artistName = run.jsonObject["text"]?.jsonPrimitive?.contentOrNull,
                    )
                "ALBUM" in pageType && credits.albumId == null ->
                    credits.copy(
                        albumId = id,
                        albumName = run.jsonObject["text"]?.jsonPrimitive?.contentOrNull,
                    )
                else -> credits
            }
        }
        return credits
    }

    private fun findString(root: JsonElement, key: String): String? = when (root) {
        is JsonObject -> (root[key] as? JsonPrimitive)?.contentOrNull
            ?: root.values.firstNotNullOfOrNull { findString(it, key) }
        is JsonArray -> root.firstNotNullOfOrNull { findString(it, key) }
        else -> null
    }

    /** The thumbnails array a renderer carries, for shape rather than for a URL. */
    private fun findThumbnails(root: JsonElement): JsonArray? = when (root) {
        is JsonObject -> (root["thumbnails"] as? JsonArray)
            ?: root.values.firstNotNullOfOrNull(::findThumbnails)
        is JsonArray -> root.firstNotNullOfOrNull(::findThumbnails)
        else -> null
    }

    /** Whether artwork is the wrong shape for a cover. */
    private fun JsonArray?.isNotSquare(): Boolean {
        val last = this?.lastOrNull() as? JsonObject ?: return false
        val width = (last["width"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: return false
        val height = (last["height"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: return false
        if (width <= 0 || height <= 0) return false
        return width / height !in 0.85..1.15
    }

    private fun findThumbnailUrl(root: JsonElement): String? = when (root) {
        is JsonObject -> {
            val thumbnails = root["thumbnails"] as? JsonArray
            thumbnails?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                ?: root.values.firstNotNullOfOrNull(::findThumbnailUrl)
        }
        is JsonArray -> root.firstNotNullOfOrNull(::findThumbnailUrl)
        else -> null
    }

    private fun findStrings(root: JsonElement, key: String): List<String> = buildList {
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    element[key]?.textValue()?.takeIf { it.isNotBlank() }?.let(::add)
                    element.values.forEach(::walk)
                }
                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(root)
    }

    /**
     * A player response fetched *with* the session, so it carries the `playbackTracking` block a
     * play has to be registered through.
     */
    suspend fun playerForTracking(videoId: String, signatureTimestamp: Int): Result<JsonObject> = runCatching {
        post("player") {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            // Real clients always describe where playback is happening; the response's tracking
            // block is scoped to it.
            putJsonObject("playbackContext") {
                putJsonObject("contentPlaybackContext") {
                    put("html5Preference", "HTML5_PREF_WANTS")
                    put("referer", "https://music.youtube.com/watch?v=$videoId")
                    put("signatureTimestamp", signatureTimestamp)
                }
            }
        }
    }

    /** The account's own library, in one shot. */
    suspend fun library(): Result<LibraryPage> = runCatching {
        if (!DesktopYouTubeAuth.isSignedIn) return@runCatching LibraryPage(emptyList(), emptyList(), emptyList())
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
            LibraryPage(
                likedSongs = likedSongs,
                // Thumbs-up'd tracks are in the library feed too; only what Liked Music does not
                // already cover earns a second section.
                librarySongs = added.await().filterNot { it.videoId in likedIds },
                shelves = shelves,
            )
        }
    }

    /** Every track behind a playlist-shaped browse id, following continuations. */
    private suspend fun songsPaged(browseId: String): List<Song> {
        val out = LinkedHashMap<String, Song>()
        var response = post("browse") { put("browseId", browseId) }
        var page = 1
        while (true) {
            parseBrowseSongs(response, null).forEach { out[it.videoId] = it }
            val token = continuationToken(response)
            if (token == null || page++ >= MAX_LIBRARY_PAGES) break
            response = runCatching { post("browse", continuation = token) { put("continuation", token) } }
                .getOrNull() ?: break
        }
        return out.values.toList()
    }

    /** Every saved card behind a library feed, following continuations. */
    private suspend fun libraryItemsPaged(browseId: String): List<ShelfItem> {
        val out = LinkedHashMap<String, ShelfItem>()
        var response = post("browse") { put("browseId", browseId) }
        var page = 1
        while (true) {
            parseLibraryItems(response).forEach { item ->
                out.putIfAbsent(item.browseId ?: item.videoId ?: "${item.title}\n${item.subtitle}", item)
            }
            val token = continuationToken(response)
            if (token == null || page++ >= MAX_LIBRARY_PAGES) break
            response = runCatching { post("browse", continuation = token) { put("continuation", token) } }
                .getOrNull() ?: break
        }
        return out.values.toList()
    }

    /** One page of a library feed's saved cards. */
    internal fun parseLibraryItems(root: JsonObject): List<ShelfItem> {
        val out = LinkedHashMap<String, ShelfItem>()
        renderers(root, "musicTwoRowItemRenderer").mapNotNull(::twoRowItem).forEach { item ->
            item.browseId?.let { out.putIfAbsent(it, item) }
        }
        renderers(root, "musicResponsiveListItemRenderer").mapNotNull(::parseBrowseItem).forEach { item ->
            out.putIfAbsent(
                item.browseId,
                ShelfItem(item.title, item.subtitle, item.thumbnailUrl, null, item.browseId),
            )
        }
        return out.values.toList()
    }

    private const val MAX_LIBRARY_PAGES = 10

    /** Liked Music: the `LM` auto-playlist, addressed as a playlist browse id. */
    private const val LIKED_MUSIC = "VLLM"

    /** Songs explicitly added to the library — distinct from Liked Music. */
    private const val HISTORY_FEED = "FEmusic_history"
    private const val LIBRARY_SONGS = "FEmusic_liked_videos"

    private val LIBRARY_FEEDS = listOf(
        "Playlists" to "FEmusic_liked_playlists",
        "Albums" to "FEmusic_liked_albums",
        "Artists" to "FEmusic_library_corpus_track_artists",
        "Subscriptions" to "FEmusic_library_corpus_artists",
        "Podcasts" to "FEmusic_library_non_music_audio_list",
    )

    /** Thumbs up, thumbs down, or neither, for one track on the account. */
    suspend fun rate(videoId: String, status: LikeStatus): Result<Unit> = runCatching {
        // A track from another source has no rating on YouTube to change.
        if (!DesktopYouTubeAuth.isSignedIn || !isVideoId(videoId)) return@runCatching
        val endpoint = when (status) {
            LikeStatus.LIKE -> "like/like"
            LikeStatus.DISLIKE -> "like/dislike"
            LikeStatus.INDIFFERENT -> "like/removelike"
        }
        val response = post(endpoint) { putJsonObject("target") { put("videoId", videoId) } }
        response["error"]?.let { error ->
            val message = (error as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
            error("YouTube Music refused the rating: ${message ?: error}")
        }
        DesktopTrackLog.log("$endpoint $videoId -> ${findString(response, "text") ?: "no confirmation"}")
    }

    /**
     * An artist's page: their picture, the numbers under it, their top songs and the carousels of
     * what they have released.
     */
    suspend fun artistPage(browseId: String): Result<ArtistPage> = runCatching {
        val response = post("browse") { put("browseId", browseId) }
        val page = parseArtistPage(response)
        val everything = page.moreSongsBrowseId
            ?.let { runCatching { songsPaged(it) }.getOrNull() }
            .orEmpty()
        if (everything.isEmpty()) page else page.copy(songs = everything)
    }

    internal fun parseArtistPage(response: JsonObject): ArtistPage {
        val sections = response["contents"]?.jsonObject
            ?.get("singleColumnBrowseResultsRenderer")?.jsonObject
            ?.get("tabs")?.let { it as? JsonArray }?.firstOrNull()?.jsonObject
            ?.get("tabRenderer")?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("sectionListRenderer")?.jsonObject
            ?.get("contents") as? JsonArray
        val header = response["header"]
        val name = artistName(header)

        val songs = mutableListOf<Song>()
        var moreSongs: String? = null
        val shelves = mutableListOf<HomeShelf>()
        sections.orEmpty().forEach { section ->
            (section as? JsonObject)?.get("musicShelfRenderer")?.jsonObject?.let { shelf ->
                (shelf["contents"] as? JsonArray).orEmpty().forEach { row ->
                    // Billed by the page they sit on: the line beside a top-songs row counts plays
                    // where a search row names the artist.
                    parseSong(row, name)?.let(songs::add)
                }
                if (moreSongs == null) {
                    moreSongs = ((shelf["title"]?.jsonObject?.get("runs")) as? JsonArray)
                        ?.firstOrNull()?.jsonObject
                        ?.get("navigationEndpoint")?.jsonObject
                        ?.get("browseEndpoint")?.jsonObject
                        ?.get("browseId")?.jsonPrimitive?.contentOrNull
                }
            }
            (section as? JsonObject)?.get("musicCarouselShelfRenderer")?.jsonObject?.let { carousel ->
                val title = carousel["header"].textValue().trim()
                // A shelf of music videos is a dead end here as it is on the home feed — the rows
                // lead to uploads rather than to releases.
                if (title.isBlank() || VIDEO_WORD.containsMatchIn(title)) return@let
                val items = renderers(carousel, "musicTwoRowItemRenderer")
                    .mapNotNull(::twoRowItem)
                    .filter { it.browseId != null }
                if (items.isNotEmpty()) shelves += HomeShelf(title, items)
            }
        }
        return ArtistPage(
            songs = songs.distinctBy(Song::videoId),
            moreSongsBrowseId = moreSongs,
            sections = shelves,
            thumbnailUrl = artistThumbnail(header),
            name = name,
            description = parseDescription(response),
            subscriberCountText = subscriberCount(header),
            monthlyListenerCount = monthlyListeners(header),
            subscription = subscription(header),
        )
    }

    /** The name the page bills itself under. */
    private fun artistName(header: JsonElement?): String? {
        val renderer = header?.jsonObjectOrNull()?.get("musicImmersiveHeaderRenderer")?.jsonObject
            ?: header?.jsonObjectOrNull()?.get("musicVisualHeaderRenderer")?.jsonObject
            ?: return null
        return renderer["title"].textValue().trim().takeIf(String::isNotBlank)
    }

    /** The artist's own picture, off whichever header shape came back. */
    private fun artistThumbnail(header: JsonElement?): String? {
        val root = header ?: return null
        val immersive = root.jsonObjectOrNull()?.get("musicImmersiveHeaderRenderer")?.jsonObject
        val visual = root.jsonObjectOrNull()?.get("musicVisualHeaderRenderer")?.jsonObject
        val thumbnail = immersive?.get("thumbnail")
            ?: visual?.get("foregroundThumbnail")
            ?: visual?.get("thumbnail")
        // Header shapes drift; fall back to the first image anywhere under the header rather than
        // to the caller's album art.
        return thumbnail?.let(::findThumbnailUrl) ?: findThumbnailUrl(root)
    }

    private fun subscriberCount(header: JsonElement?): String? {
        val immersive = header?.jsonObjectOrNull()?.get("musicImmersiveHeaderRenderer")?.jsonObject
            ?: return null
        val newer = immersive["subscriptionButton2"]?.jsonObject?.get("subscribeButtonRenderer")?.jsonObject
        val older = immersive["subscriptionButton"]?.jsonObject?.get("subscribeButtonRenderer")?.jsonObject
        return newer?.get("subscriberCountWithSubscribeText").firstRun()
            ?: older?.get("longSubscriberCountText").firstRun()
            ?: older?.get("shortSubscriberCountText").firstRun()
    }

    /** "3.4M monthly listeners", off the same header as [subscriberCount]. */
    private fun monthlyListeners(header: JsonElement?): String? =
        header?.jsonObjectOrNull()?.get("musicImmersiveHeaderRenderer")?.jsonObject
            ?.get("monthlyListenerCount").firstRun()

    /** The header's subscribe button, as state rather than as a label. */
    private fun subscription(header: JsonElement?): SubscriptionState? {
        val immersive = header?.jsonObjectOrNull()?.get("musicImmersiveHeaderRenderer")?.jsonObject
            ?: return null
        return listOfNotNull(
            immersive["subscriptionButton2"]?.jsonObject?.get("subscribeButtonRenderer")?.jsonObject,
            immersive["subscriptionButton"]?.jsonObject?.get("subscribeButtonRenderer")?.jsonObject,
        ).firstNotNullOfOrNull { button ->
            val subscribed = (button["subscribed"] as? JsonPrimitive)?.booleanOrNull
                ?: return@firstNotNullOfOrNull null
            val channelId = button["channelId"]?.jsonPrimitive?.contentOrNull
                ?: (button["serviceEndpoints"] as? JsonArray)?.firstNotNullOfOrNull { endpoint ->
                    ((endpoint as? JsonObject)?.get("subscribeEndpoint")?.jsonObject
                        ?.get("channelIds") as? JsonArray)?.firstOrNull()?.jsonPrimitive?.contentOrNull
                }
                ?: return@firstNotNullOfOrNull null
            SubscriptionState(channelId, subscribed)
        }
    }

    /** YouTube's own editorial blurb about an artist or a release. */
    internal fun parseDescription(root: JsonObject): String? {
        collectRenderers(root, "musicDescriptionShelfRenderer").firstOrNull()
            ?.get("description").textValue().trim()
            .takeIf(String::isNotBlank)
            ?.let { return it }
        return (DESKTOP_HEADER_RENDERERS + "musicImmersiveHeaderRenderer").firstNotNullOfOrNull { name ->
            collectRenderers(root, name).firstOrNull()
                ?.get("description").textValue().trim().takeIf(String::isNotBlank)
        }
    }

    /** Subscribes to an artist's channel, or unsubscribes from it. */
    suspend fun setSubscribed(channelId: String, subscribed: Boolean): Result<Unit> = runCatching {
        requireSession()
        val endpoint = if (subscribed) "subscription/subscribe" else "subscription/unsubscribe"
        val response = post(endpoint) { putJsonArray("channelIds") { add(channelId) } }
        response["error"]?.let { error ->
            val message = (error as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
            error("YouTube Music refused the change: ${message ?: error}")
        }
        DesktopTrackLog.log("$endpoint $channelId -> ${findString(response, "text") ?: "no confirmation"}")
    }

    /** The first run of a text node, rather than all of them joined. */
    private fun JsonElement?.firstRun(): String? =
        ((this as? JsonObject)?.get("runs") as? JsonArray)
            ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
            ?.trim()?.takeIf(String::isNotBlank)

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

    // ---- Playlist writes ---------------------------------------------------- Everything here
    // changes something on the account.

    /** Creates a playlist and answers its id. */
    suspend fun createPlaylist(
        title: String,
        privacy: PlaylistPrivacy,
        videoIds: List<String> = emptyList(),
    ): Result<String> = runCatching {
        requireSession()
        val response = post("playlist/create") {
            put("title", title)
            put("description", "")
            put("privacyStatus", privacy.apiValue)
            val seed = videoIds.filter(::isVideoId)
            if (seed.isNotEmpty()) putJsonArray("videoIds") { seed.forEach { add(it) } }
        }
        // Normally a bare top-level id, occasionally only inside the command that would navigate
        // the web client to the new page.
        val created = response["playlistId"]?.jsonPrimitive?.contentOrNull
            ?: findString(response, "playlistId")
            ?: error("the playlist was created but no id came back")
        DesktopTrackLog.log("playlist/create '$title' -> $created")
        created
    }

    suspend fun deletePlaylist(playlistId: String): Result<Unit> = runCatching {
        requireSession()
        post("playlist/delete") { put("playlistId", playlistId.removePrefix("VL")) }
        Unit
    }

    suspend fun renamePlaylist(playlistId: String, title: String): Result<Unit> = runCatching {
        editPlaylist(playlistId) {
            addJsonObject {
                put("action", "ACTION_SET_PLAYLIST_NAME")
                put("playlistName", title)
            }
        }
        Unit
    }

    /**
     * Adds tracks to a playlist, answering the per-entry id each one landed under — video id to
     * set-video-id, for the entries YouTube named.
     */
    suspend fun addToPlaylist(playlistId: String, videoIds: List<String>): Result<Map<String, String>> =
        runCatching {
            val addable = videoIds.filter(::isVideoId)
            check(addable.isNotEmpty()) { "That track is not on YouTube Music, so it cannot go in this playlist" }
            val response = editPlaylist(playlistId) {
                addable.forEach { videoId ->
                    addJsonObject {
                        put("action", "ACTION_ADD_VIDEO")
                        put("addedVideoId", videoId)
                    }
                }
            }
            (response["playlistEditResults"] as? JsonArray)
                .orEmpty()
                .mapNotNull { result ->
                    val added = (result as? JsonObject)
                        ?.get("playlistEditVideoAddedResultData") as? JsonObject
                        ?: return@mapNotNull null
                    val videoId = (added["videoId"] as? JsonPrimitive)?.contentOrNull
                        ?: return@mapNotNull null
                    val setVideoId = (added["setVideoId"] as? JsonPrimitive)?.contentOrNull
                        ?: return@mapNotNull null
                    videoId to setVideoId
                }
                .toMap()
        }

    /** Removes entries from a playlist. */
    suspend fun removeFromPlaylist(
        playlistId: String,
        entries: List<Pair<String, String>>,
    ): Result<Unit> = runCatching {
        editPlaylist(playlistId) {
            entries.forEach { (setVideoId, videoId) ->
                addJsonObject {
                    put("action", "ACTION_REMOVE_VIDEO")
                    put("setVideoId", setVideoId)
                    put("removedVideoId", videoId)
                }
            }
        }
        Unit
    }

    /** One or more edits to a playlist, applied together. */
    private suspend fun editPlaylist(
        playlistId: String,
        actions: JsonArrayBuilder.() -> Unit,
    ): JsonObject {
        requireSession()
        // The edit endpoint takes the raw id; `VL` is the browse prefix.
        val response = post("browse/edit_playlist") {
            put("playlistId", playlistId.removePrefix("VL"))
            putJsonArray("actions", actions)
        }
        val status = response["status"]?.jsonPrimitive?.contentOrNull
        DesktopTrackLog.log("edit_playlist $playlistId -> ${status ?: "no status"}")
        check(status == null || status == "STATUS_SUCCEEDED") {
            "YouTube Music refused the edit ($status)"
        }
        return response
    }

    /** A write attempted with no session; the caller has a sign-in prompt to show. */
    private fun requireSession() {
        check(DesktopYouTubeAuth.isSignedIn) { "Sign in to YouTube Music to do that" }
    }

    /**
     * Whether [id] is a YouTube video id, as opposed to anything else that can identify a track
     * here.
     */
    internal fun isVideoId(id: String): Boolean = VIDEO_ID.matches(id)

    private val VIDEO_ID = Regex("""[A-Za-z0-9_-]{11}""")

    /** The playlists the account can be asked to add a track to. */
    internal fun userPlaylists(items: List<ShelfItem>): List<UserPlaylist> = items.mapNotNull { item ->
        val browseId = item.browseId ?: return@mapNotNull null
        if (!browseId.startsWith("VL")) return@mapNotNull null
        if (NOT_EDITABLE.any { browseId.startsWith("VL$it") }) return@mapNotNull null
        UserPlaylist(
            playlistId = browseId.removePrefix("VL"),
            title = item.title,
            subtitle = item.subtitle,
            thumbnailUrl = item.thumbnailUrl,
        )
    }

    /** Playlist ids no edit can be aimed at. */
    private val NOT_EDITABLE = listOf("LM", "SE", "RD", "OLAK", "MPRE")

    /**
     * Whether a playlist page is one the account *made* rather than one it merely saved — null when
     * the response does not say either way.
     */
    internal fun parsePlaylistOwned(root: JsonObject): Boolean? {
        if (collectRenderers(root, "musicEditablePlaylistDetailHeaderRenderer").isNotEmpty()) return true
        // Scoped to the header rather than walked for.
        val header = collectRenderers(root, "musicResponsiveHeaderRenderer").firstOrNull() ?: return null
        if (collectRenderers(header, "menuNavigationItemRenderer").any {
                (it["icon"] as? JsonObject)?.get("iconType")?.jsonPrimitive?.contentOrNull in OWNER_ICONS
            }
        ) {
            return true
        }
        // Last resort: a playlist the account only saved offers to un-save it.
        return (header["buttons"] as? JsonArray).orEmpty().none { button ->
            val toggle = (button as? JsonObject)?.get("toggleButtonRenderer") as? JsonObject
            val default = (toggle?.get("defaultIcon") as? JsonObject)?.get("iconType")?.jsonPrimitive?.contentOrNull
            val toggled = (toggle?.get("toggledIcon") as? JsonObject)?.get("iconType")?.jsonPrimitive?.contentOrNull
            default == "BOOKMARK_BORDER" || toggled == "BOOKMARK"
        }
    }

    /** Header menu icons only a playlist's owner is offered. */
    private val OWNER_ICONS = setOf("DELETE", "EDIT")

    /**
     * The typeahead list YouTube Music's own search box shows for a half-typed query — query
     * strings, not results.
     */
    suspend fun searchSuggestions(input: String): Result<List<String>> = runCatching {
        parseSearchSuggestions(post("music/get_search_suggestions") { put("input", input) })
    }

    internal fun parseSearchSuggestions(response: JsonObject): List<String> =
        collectRenderers(response, "searchSuggestionRenderer")
            .mapNotNull { renderer ->
                val endpoint = (renderer["navigationEndpoint"] as? JsonObject)
                    ?.get("searchEndpoint") as? JsonObject
                // Preferred over the display text, which arrives split into runs purely so the
                // typed prefix can be bold-faced and has no separator of its own to rejoin on.
                val query = endpoint?.get("query")?.jsonPrimitive?.contentOrNull
                    ?: renderer["suggestion"].textValue()
                query.takeIf { it.isNotBlank() }
            }
            .distinct()

    /** The signed-in listener's own details, for the accounts screen. */
    suspend fun accountMenu(): Result<JsonObject> = runCatching { post("account/account_menu") {} }

    /** The channels this login can act as — its own, plus any brand channels. */
    suspend fun accountsList(): Result<JsonObject> = runCatching { post("account/accounts_list") {} }

    /** Exposed so the accounts layer can scan a response for its own renderers. */
    internal fun renderers(root: JsonElement, name: String): List<JsonObject> = collectRenderers(root, name)

    private fun collectRenderers(root: JsonElement, name: String): List<JsonObject> = buildList {
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    (element[name] as? JsonObject)?.let(::add)
                    element.values.forEach(::walk)
                }
                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(root)
    }

    private val DURATION = Regex("""\d{1,2}:\d{2}(?::\d{2})?""")
    private val YEAR = Regex("""\d{4}""")
    private val TYPE_WORDS = setOf("song", "video", "album", "ep", "single", "playlist")
    private val TALLY = Regex("[0-9,.]+\\s*(?:views?|likes?)", RegexOption.IGNORE_CASE)
    private val DESKTOP_HEADER_RENDERERS = listOf(
        "musicResponsiveHeaderRenderer",
        "musicDetailHeaderRenderer",
        "musicImmersiveHeaderRenderer",
        "musicVisualHeaderRenderer",
    )
    private val DESKTOP_HEADER_CREDIT_LINES = listOf("straplineTextOne", "subtitle")
}

data class DesktopCollection(
    val browseId: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val type: BrowseType,
    val songs: List<Song>,
    /** The token for the rows after these, or null once there are none. */
    val continuation: String? = null,
    /**
     * Whether this is a playlist the account made, rather than one it saved — null when the page
     * does not say, or when it is not a playlist at all.
     */
    val owned: Boolean? = null,
) {
    /** The raw id an edit is addressed to; the browse id carries a `VL` prefix. */
    val playlistId: String get() = browseId.removePrefix("VL")
}

private data class DesktopBrowseHeader(
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
)

internal fun browseTypeOf(browseId: String): BrowseType = when {
    browseId.startsWith("MPREb_") || browseId.startsWith("OLAK5uy_") -> BrowseType.ALBUM
    browseId.startsWith("VL") -> BrowseType.PLAYLIST
    browseId.startsWith("UC") -> BrowseType.ARTIST
    else -> BrowseType.OTHER
}
