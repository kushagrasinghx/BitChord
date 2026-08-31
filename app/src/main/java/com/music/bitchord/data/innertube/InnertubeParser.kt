package com.music.bitchord.data.innertube

import com.music.bitchord.data.model.Account
import com.music.bitchord.data.model.ArtistPage
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryState
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.SongMenu
import com.music.bitchord.data.model.UserPlaylist
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Locale

/**
 * Innertube responses are deeply nested and their shape drifts between
 * layouts (single-column vs two-column browse, shelf vs carousel). Rather
 * than hard-coding every path, structured parsing is used where the layout
 * is stable (search, home) and a recursive scan where it is not (playlists,
 * library) — see [collectSongsDeep].
 */
object InnertubeParser {

    // ---- Search -------------------------------------------------------------

    fun parseSearchSongs(response: JsonObject): List<Song> =
        parseSearch(response).filterIsInstance<SearchResult.Track>().map { it.song }

    /**
     * Search results are heterogeneous: songs carry a videoId, while albums,
     * artists and playlists carry a browseId plus a page type. Both arrive as
     * `musicResponsiveListItemRenderer`, so each row is classified on the way out.
     */
    fun parseSearch(response: JsonObject): List<SearchResult> {
        // The "All" tab spreads results across several shelf types (card shelf
        // for the top result, then one shelf per category), and the shapes
        // differ per filter. Walking for the row renderer itself is far more
        // robust than chasing each container path.
        val rows = collectRenderers(response, "musicResponsiveListItemRenderer")

        val seen = HashSet<String>()
        return rows.mapNotNull { renderer ->
            // Browse rows are tested first: an album row also carries a
            // "play album" videoId in its overlay, so checking for a track
            // first would misread every album as a single song.
            parseBrowseItem(renderer)?.let { item ->
                return@mapNotNull if (seen.add("b:${item.browseId}")) {
                    SearchResult.Browse(item)
                } else {
                    null
                }
            }
            parseResponsiveListItem(renderer)?.let { song ->
                if (song.isVideo) return@mapNotNull null
                if (seen.add("v:${song.videoId}")) SearchResult.Track(song) else null
            }
        }
    }

    /**
     * The typeahead queries out of a `music/get_search_suggestions` response.
     *
     * Two sections come back. The first is what this reads: query strings, as
     * `searchSuggestionRenderer`. The second — present signed in, and for
     * some terms signed out — is entity rows for songs and artists, as the
     * same `musicResponsiveListItemRenderer` a search result uses. Those are
     * deliberately ignored: what the field is being filled in with is a
     * query, and a row that navigates straight to a track instead is a
     * different feature with a different tap target.
     *
     * `searchEndpoint.query` is preferred over the display text because the
     * display text arrives split into runs purely so the typed prefix can be
     * bold-faced, with no separator of its own to rejoin on.
     */
    fun parseSearchSuggestions(response: JsonObject): List<String> =
        collectRenderers(response, "searchSuggestionRenderer")
            .mapNotNull { renderer ->
                val query = renderer.o("navigationEndpoint").o("searchEndpoint").s("query")
                    ?: renderer.o("suggestion").runs()
                query.takeIf { it.isNotBlank() }
            }
            .distinct()

    /** Depth-first collection of a named renderer, preserving document order. */
    private fun collectRenderers(root: JsonElement, name: String): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        fun walk(node: JsonElement) {
            when (node) {
                is JsonObject -> {
                    (node[name] as? JsonObject)?.let(out::add)
                    node.values.forEach(::walk)
                }
                is JsonArray -> node.forEach(::walk)
                else -> Unit
            }
        }
        walk(root)
        return out
    }

    private fun parseBrowseItem(renderer: JsonObject): BrowseItem? {
        val endpoint = renderer.o("navigationEndpoint").o("browseEndpoint") ?: return null
        val browseId = endpoint.s("browseId") ?: return null
        val pageType = endpoint.o("browseEndpointContextSupportedConfigs")
            .o("browseEndpointContextMusicConfig").s("pageType").orEmpty()

        val columns = renderer.a("flexColumns").orEmpty()
        val title = columns.getOrNull(0)
            .o("musicResponsiveListItemFlexColumnRenderer").o("text").runs()
        if (title.isBlank()) return null

        val subtitle = columns.getOrNull(1)
            .o("musicResponsiveListItemFlexColumnRenderer").o("text").runs()
        // A playlist/album billed as a video chart/compilation — "N videos"
        // in the subtitle, or "video" right in the title, e.g. "Daily Top
        // Music Videos" — would have every row dropped by
        // parseResponsiveListItem anyway, so skip the dead-end card rather
        // than link to an empty page.
        if (VIDEO_WORD.containsMatchIn(title) || VIDEO_WORD.containsMatchIn(subtitle)) return null

        return BrowseItem(
            browseId = browseId,
            title = title,
            subtitle = subtitle,
            thumbnailUrl = renderer.o("thumbnail").o("musicThumbnailRenderer")
                .o("thumbnail").a("thumbnails").best(),
            type = when {
                "ALBUM" in pageType -> BrowseType.ALBUM
                "ARTIST" in pageType -> BrowseType.ARTIST
                "PLAYLIST" in pageType -> BrowseType.PLAYLIST
                else -> BrowseType.OTHER
            },
        )
    }

    // ---- Home feed ----------------------------------------------------------

    fun parseHome(response: JsonObject): List<HomeShelf> {
        val sections = response.o("contents")
            .o("singleColumnBrowseResultsRenderer").a("tabs")?.firstOrNull()
            .o("tabRenderer").o("content").o("sectionListRenderer").a("contents")
            .orEmpty()

        return sections.mapNotNull { section ->
            section.o("musicCarouselShelfRenderer")?.let(::carouselShelf)
                ?: section.o("musicShelfRenderer")?.let(::plainShelf)
        }
    }

    /**
     * More Home shelves off a continuation response.
     *
     * Unlike the first page, a continuation envelope doesn't repeat the
     * tabs/section-list wrapper [parseHome] reads off a fixed path — so the
     * shelves are walked out wherever they land instead, the same tradeoff
     * [collectSongsDeep] makes for song rows. Preserves the order they were
     * found in, since a carousel and a plain shelf never share a parent node.
     */
    fun parseHomeContinuation(root: JsonElement): List<HomeShelf> {
        val out = mutableListOf<HomeShelf>()
        fun walk(node: JsonElement) {
            when (node) {
                is JsonObject -> {
                    (node["musicCarouselShelfRenderer"] as? JsonObject)
                        ?.let(::carouselShelf)?.let(out::add)
                    (node["musicShelfRenderer"] as? JsonObject)
                        ?.let(::plainShelf)?.let(out::add)
                    node.values.forEach(::walk)
                }
                is JsonArray -> node.forEach(::walk)
                else -> Unit
            }
        }
        walk(root)
        return out
    }

    private fun carouselShelf(carousel: JsonObject): HomeShelf? {
        val header = carousel.o("header").o("musicCarouselShelfBasicHeaderRenderer")
        val title = header.o("title").runs()
        val strapline = header.o("strapline").runs()
        // Whole shelves like "Video charts" carry nothing but video
        // compilations — each card would fail its own video check on the
        // way to a dead-end page, so the shelf is dropped outright.
        if (VIDEO_WORD.containsMatchIn(title)) return null
        val items = carousel.a("contents").orEmpty().mapNotNull { item ->
            parseTwoRowItem(item.o("musicTwoRowItemRenderer"))
                ?: parseResponsiveListItem(item.o("musicResponsiveListItemRenderer"))
                    ?.takeUnless { it.isVideo }
                    ?.let { song ->
                        ShelfItem(song.title, song.artist, song.thumbnailUrl, song.videoId, null)
                    }
                // A chart row with nothing to play — "Top artists" lists the
                // artist alone, no track — falls through parseResponsiveListItem
                // (it demands a videoId) and used to drop the whole shelf.
                ?: parseArtistRow(item.o("musicResponsiveListItemRenderer"))
        }
        return if (items.isEmpty()) null else HomeShelf(title.ifBlank { "For you" }, items, strapline)
    }

    private fun plainShelf(shelf: JsonObject): HomeShelf? {
        val title = shelf.o("title").runs()
        if (VIDEO_WORD.containsMatchIn(title)) return null
        val items = shelf.a("contents").orEmpty().mapNotNull {
            parseResponsiveListItem(it.o("musicResponsiveListItemRenderer"))
        }.filterNot { it.isVideo }
            .map { ShelfItem(it.title, it.artist, it.thumbnailUrl, it.videoId, null) }
        return if (items.isEmpty()) null else HomeShelf(title.ifBlank { "For you" }, items)
    }

    /**
     * Artist landing page: a "Top songs" shelf (only ~5 rows, but its header
     * links to a playlist with the full list) plus carousels for Albums,
     * Singles & EPs and friends.
     */
    fun parseArtistPage(response: JsonObject): ArtistPage {
        val sections = response.o("contents")
            .o("singleColumnBrowseResultsRenderer").a("tabs")?.firstOrNull()
            .o("tabRenderer").o("content").o("sectionListRenderer").a("contents")
            .orEmpty()

        val songs = mutableListOf<Song>()
        var moreSongs: String? = null
        val shelves = mutableListOf<HomeShelf>()
        val header = response["header"]
        // "Top songs" rows are billed by the page they sit on: the subtitle
        // beside them counts plays where a search row names the artist.
        val credit = Credits(artistName = artistName(header))

        sections.forEach { section ->
            section.o("musicShelfRenderer")?.let { shelf ->
                shelf.a("contents").orEmpty().forEach { row ->
                    parseResponsiveListItem(row.o("musicResponsiveListItemRenderer"), credit)
                        ?.let(songs::add)
                }
                if (moreSongs == null) {
                    moreSongs = shelf.o("title").a("runs")?.firstOrNull()
                        .o("navigationEndpoint").o("browseEndpoint").s("browseId")
                }
            }
            section.o("musicCarouselShelfRenderer")?.let { carousel ->
                val header = carousel.o("header").o("musicCarouselShelfBasicHeaderRenderer")
                val title = header.o("title").runs()
                if (VIDEO_WORD.containsMatchIn(title)) return@let
                val items = carousel.a("contents").orEmpty().mapNotNull {
                    parseTwoRowItem(it.o("musicTwoRowItemRenderer"))
                }.filter { it.browseId != null }
                if (title.isNotBlank() && items.isNotEmpty()) {
                    shelves += HomeShelf(title, items)
                }
            }
        }
        return ArtistPage(
            songs, moreSongs, shelves,
            thumbnailUrl = artistThumbnail(header),
            name = credit.artistName,
            description = parseDescription(response),
            subscriberCountText = subscriberCount(header),
            monthlyListenerCount = monthlyListeners(header),
        )
    }

    /**
     * "1.2M subscribers" off the artist header's subscribe button — YouTube
     * ships two shapes of it depending on how the page was served, and the
     * button itself carries the count under one of three different keys
     * across those shapes.
     */
    private fun subscriberCount(header: JsonElement?): String? {
        val immersive = header.o("musicImmersiveHeaderRenderer") ?: return null
        val button2 = immersive.o("subscriptionButton2").o("subscribeButtonRenderer")
        val button1 = immersive.o("subscriptionButton").o("subscribeButtonRenderer")
        return button2.o("subscriberCountWithSubscribeText").firstRunText()
            ?: button1.o("longSubscriberCountText").firstRunText()
            ?: button1.o("shortSubscriberCountText").firstRunText()
    }

    /** "3.4M monthly listeners", off the same header as [subscriberCount]. */
    private fun monthlyListeners(header: JsonElement?): String? =
        header.o("musicImmersiveHeaderRenderer").o("monthlyListenerCount").firstRunText()

    /**
     * The name the page bills itself under. A track credited to a trio hands
     * its callers all three names at once, so the page's own header is what
     * says which of them is actually open.
     */
    private fun artistName(header: JsonElement?): String? {
        val renderer = header.o("musicImmersiveHeaderRenderer")
            ?: header.o("musicVisualHeaderRenderer")
            ?: return null
        return renderer.o("title").runs().takeIf { it.isNotBlank() }
    }

    /**
     * The artist's own picture, off whichever header shape came back — the
     * immersive header serves it as `thumbnail`, the visual header as
     * `foregroundThumbnail` over a banner. Callers that arrive from a track
     * only know that track's cover art, so this is what a page is meant to
     * show instead.
     */
    private fun artistThumbnail(header: JsonElement?): String? {
        if (header == null) return null
        val immersive = header.o("musicImmersiveHeaderRenderer")
        val visual = header.o("musicVisualHeaderRenderer")
        val renderer = (
            immersive.o("thumbnail")
                ?: visual.o("foregroundThumbnail")
                ?: visual.o("thumbnail")
            ).o("musicThumbnailRenderer")
            // Header shapes drift; fall back to the first image anywhere under
            // the header rather than to the caller's album art.
            ?: collectRenderers(header, "musicThumbnailRenderer").firstOrNull()
        return renderer.o("thumbnail").a("thumbnails").best()
    }

    // ---- Generic / robust ---------------------------------------------------

    /**
     * Walks the whole response collecting any `musicResponsiveListItemRenderer`
     * that carries a videoId. Layout-agnostic, so it survives the differences
     * between playlist, album, library and history pages.
     */
    fun collectSongsDeep(root: JsonElement): List<Song> {
        val out = LinkedHashMap<String, Song>()
        // A release's own rows are credited by its header, not one by one.
        val pageCredit = pageCredit(root)
        fun walk(node: JsonElement) {
            when (node) {
                is JsonObject -> {
                    node["musicResponsiveListItemRenderer"]?.let { renderer ->
                        parseResponsiveListItem(renderer as? JsonObject, pageCredit)
                            ?.let { out[it.videoId] = it }
                    }
                    node.values.forEach(::walk)
                }
                is JsonArray -> node.forEach(::walk)
                else -> Unit
            }
        }
        walk(root)
        return out.values.toList()
    }

    /** A playlist page's own tracks, the ones YouTube suggests adding, and the token for the rest. */
    data class PlaylistShelfPage(val songs: List<Song>, val suggested: List<Song>, val continuation: String?)

    /**
     * A playlist page's own track list, scoped rather than walked — plus
     * whatever YouTube offers alongside it to round the playlist out.
     *
     * A playlist the account owns can carry a "Suggestions" shelf below the
     * list actually built by hand — even a two-song playlist's own shelf
     * comes back with a continuation token that, followed, serves it rather
     * than running dry. That continuation is not another
     * `musicPlaylistShelfContinuation` page, though: it lands as a plain
     * `sectionListContinuation` carrying a `musicShelfRenderer` titled
     * "Suggestions", structurally unrelated to the shelf the real tracks
     * came from. Scoping only to the playlist shelf itself — as an earlier
     * version of this function did — reads that continuation as belonging
     * to nothing and drops it, suggestions included. So the scope here is
     * the whole secondary column (or, for a continuation response, the
     * whole `continuationContents`), and what tells a suggested row from a
     * real one is `playlistItemData` — present on every row either way, but
     * only a row actually in the playlist carries a `playlistSetVideoId`
     * inside it, the id "remove from playlist" needs. [collectSongsDeep]
     * has no notion of any of this, so a plain walk reads suggestions as
     * songs the user added. Returns null off a page with nothing
     * playlist-shaped in scope (an album, say), so callers fall back to the
     * generic walk.
     */
    fun parsePlaylistShelf(root: JsonElement): PlaylistShelfPage? {
        val scope: JsonElement = root.o("continuationContents")
            ?: root.o("contents").o("twoColumnBrowseResultsRenderer").o("secondaryContents")
            ?: return null
        val looksLikePlaylist = collectRenderers(scope, "musicPlaylistShelfRenderer").isNotEmpty() ||
            scope.o("musicPlaylistShelfContinuation") != null ||
            collectRenderers(scope, "musicShelfRenderer").any { it.o("title").runs() == "Suggestions" }
        if (!looksLikePlaylist) return null

        val pageCredit = pageCredit(root)
        val (songs, suggested) = collectRenderers(scope, "musicResponsiveListItemRenderer")
            .mapNotNull { parseResponsiveListItem(it, pageCredit) }
            .distinctBy { it.videoId }
            .partition { it.setVideoId != null }
        // The "Suggestions" shelf's own continuation reloads it with a fresh
        // batch rather than paging it (see its "Refresh" button, wired to a
        // `reloadContinuationData` token) — only the real `nextContinuationData`
        // / `continuationItemRenderer` found in scope means more to fetch.
        val token = collectRenderers(scope, "continuationItemRenderer").firstOrNull()
            .o("continuationEndpoint").o("continuationCommand").s("token")
            ?: collectRenderers(scope, "nextContinuationData").firstOrNull().s("continuation")
        return PlaylistShelfPage(songs, suggested, token)
    }

    /**
     * The cards on a library feed — saved playlists, albums, artists, podcasts.
     *
     * Library pages remember whether the account last used the grid or the list
     * view, and serve `musicTwoRowItemRenderer` cards for one and
     * `musicResponsiveListItemRenderer` rows for the other, so both are read.
     */
    fun parseLibraryItems(root: JsonElement): List<ShelfItem> {
        val out = LinkedHashMap<String, ShelfItem>()
        collectRenderers(root, "musicTwoRowItemRenderer").forEach { renderer ->
            val item = parseTwoRowItem(renderer) ?: return@forEach
            item.browseId?.let { out.putIfAbsent(it, item) }
        }
        collectRenderers(root, "musicResponsiveListItemRenderer").forEach { renderer ->
            val item = parseBrowseItem(renderer) ?: return@forEach
            out.putIfAbsent(
                item.browseId,
                ShelfItem(item.title, item.subtitle, item.thumbnailUrl, null, item.browseId),
            )
        }
        return out.values.toList()
    }

    /**
     * Token for the next page of a paged response, or null once it has run out.
     * Both the modern `continuationItemRenderer` and the older `continuations`
     * array are in circulation, sometimes within the same account.
     */
    fun continuationToken(root: JsonElement): String? {
        collectRenderers(root, "continuationItemRenderer").firstOrNull()
            .o("continuationEndpoint").o("continuationCommand").s("token")
            ?.let { return it }
        return collectRenderers(root, "nextContinuationData").firstOrNull().s("continuation")
    }

    // ---- Renderers ----------------------------------------------------------

    /**
     * One track row. [fallback] is what the page it came from is billed to —
     * see [pageCredit] — and is used only where the row itself says nothing.
     */
    private fun parseResponsiveListItem(
        renderer: JsonObject?,
        fallback: Credits = Credits(),
    ): Song? {
        if (renderer == null) return null
        val videoId = renderer.o("playlistItemData").s("videoId")
            ?: renderer.o("overlay")
                .o("musicItemThumbnailOverlayRenderer").o("content")
                .o("musicPlayButtonRenderer").o("playNavigationEndpoint")
                .o("watchEndpoint").s("videoId")
            ?: return null

        val columns = renderer.a("flexColumns").orEmpty()
        val title = columns.getOrNull(0)
            .o("musicResponsiveListItemFlexColumnRenderer").o("text").runs()
        if (title.isBlank()) return null

        val subtitle = columns.getOrNull(1)
            .o("musicResponsiveListItemFlexColumnRenderer").o("text").runs()
        val parts = subtitle.split(" • ").filter { it.isNotBlank() }
        val duration = parts.lastOrNull()?.takeIf { it.matches(DURATION) }
        // On the "All" tab the first segment is the row type ("Song", "Video"),
        // not the artist — skip those so the subtitle reads like a credit.
        val rowType = parts.firstOrNull { it.lowercase(Locale.ROOT) in TYPE_WORDS }?.lowercase(Locale.ROOT)
        // A track row on an album lists its play count where a search row
        // lists the artist, so a segment that reads as a tally is no credit.
        val artist = parts.firstOrNull {
            !it.matches(DURATION) && it.lowercase(Locale.ROOT) !in TYPE_WORDS && !it.matches(TALLY)
        }

        // The artist/album names in the subtitle carry browse endpoints; pull
        // them out so the long-press menu can open those pages.
        val credits = creditsOf(
            columns.flatMap {
                it.o("musicResponsiveListItemFlexColumnRenderer").o("text").a("runs").orEmpty()
            },
        )

        val thumbnails = renderer.o("thumbnail").o("musicThumbnailRenderer")
            .o("thumbnail").a("thumbnails")

        return Song(
            videoId = videoId,
            title = title,
            // The run that links to an artist page is the authoritative
            // credit; the "All" tab often lists only "Song • 4:30" otherwise,
            // and an album's own rows carry no credit at all — the release is
            // billed once, in the header the row hangs under.
            artist = credits.artistName?.takeIf { it.isNotBlank() }
                ?: artist
                ?: fallback.artistName
                ?: "Unknown artist",
            thumbnailUrl = thumbnails.best(),
            durationText = duration,
            artistId = credits.artistId ?: fallback.artistId,
            albumId = credits.albumId ?: fallback.albumId,
            albumName = credits.albumName ?: fallback.albumName,
            // Only playlist rows carry one; on an album or a search hit this
            // is simply absent, which is what makes "remove from playlist"
            // offer itself exactly where it means something.
            setVideoId = renderer.o("playlistItemData").s("playlistSetVideoId"),
            // The row type word is the clean signal when present ("All" tab);
            // otherwise a music-video upload gives itself away with widescreen
            // art where a catalogue track has square album cover art.
            isVideo = rowType == "video" || thumbnails.isNotSquare(),
        )
    }

    /**
     * A chart row that names an artist rather than a track — "Top artists"
     * on the Charts page lists 40 of them with no song attached, so there is
     * no `videoId` for [parseResponsiveListItem] to key off and it returns
     * null for every one. Read here off the row's own `navigationEndpoint`
     * instead (the flex columns carry only the name and a subscriber count)
     * and pointed at the artist page rather than dropped.
     */
    private fun parseArtistRow(renderer: JsonObject?): ShelfItem? {
        if (renderer == null) return null
        val endpoint = renderer.o("navigationEndpoint").o("browseEndpoint")
        val pageType = endpoint.o("browseEndpointContextSupportedConfigs")
            .o("browseEndpointContextMusicConfig").s("pageType").orEmpty()
        if ("ARTIST" !in pageType) return null
        val browseId = endpoint.s("browseId") ?: return null

        val columns = renderer.a("flexColumns").orEmpty()
        val title = columns.getOrNull(0)
            .o("musicResponsiveListItemFlexColumnRenderer").o("text").runs()
        if (title.isBlank()) return null
        val subtitle = columns.getOrNull(1)
            .o("musicResponsiveListItemFlexColumnRenderer").o("text").runs()

        val thumbnails = renderer.o("thumbnail").o("musicThumbnailRenderer")
            .o("thumbnail").a("thumbnails")
        return ShelfItem(
            title = title,
            subtitle = subtitle,
            thumbnailUrl = thumbnails.best(),
            videoId = null,
            browseId = browseId,
        )
    }

    /** The artist / album pages a run list links out to, and their names. */
    private data class Credits(
        val artistId: String? = null,
        val artistName: String? = null,
        val albumId: String? = null,
        val albumName: String? = null,
    )

    private fun creditsOf(runs: List<JsonElement>): Credits {
        var credits = Credits()
        runs.forEach { run ->
            val browse = run.o("navigationEndpoint").o("browseEndpoint")
            val id = browse.s("browseId") ?: return@forEach
            val pageType = browse.o("browseEndpointContextSupportedConfigs")
                .o("browseEndpointContextMusicConfig").s("pageType").orEmpty()
            credits = when {
                "ARTIST" in pageType && credits.artistId == null ->
                    credits.copy(artistId = id, artistName = run.s("text"))
                "ALBUM" in pageType && credits.albumId == null ->
                    credits.copy(albumId = id, albumName = run.s("text"))
                else -> credits
            }
        }
        return credits
    }

    /**
     * Who a release page is billed to, off its own header.
     *
     * An album or single doesn't repeat the credit on every track — it says
     * "Single • Dhanda Nyoliwala" once at the top and then lists bare titles,
     * so every row read on its own comes back as "Unknown artist". The header
     * is that missing credit, and carries the artist's browse id with it, so
     * the long-press menu can still open the artist page from those rows.
     *
     * Only releases, never playlists: a playlist's header names whoever put
     * it together, which is not what its tracks are by. Playlist rows carry
     * their own credits anyway.
     */
    private fun pageCredit(root: JsonElement): Credits {
        val header = HEADER_RENDERERS.firstNotNullOfOrNull {
            collectRenderers(root, it).firstOrNull()
        } ?: return Credits()
        // The current header hangs the artist off a strapline above the title;
        // the older one packs it into the subtitle, "Album • Artist • 2024".
        val lines = HEADER_CREDIT_LINES.map { header.o(it).a("runs").orEmpty() }
        // Split per line, not across them: the strapline and the subtitle are
        // separate sentences, and running them together would weld the artist
        // onto the word that says this is a release at all.
        val parts = lines.flatMap { line ->
            line.joinToString("") { it.s("text").orEmpty() }.split(" • ").map(String::trim)
        }
        if (parts.none { it.lowercase(Locale.ROOT) in RELEASE_WORDS }) return Credits()

        val credits = creditsOf(lines.flatten())
        if (credits.artistName?.isNotBlank() == true) return credits
        // An artist YouTube has no page for is named in the same line without
        // a link to follow, leaving the name as the only thing to go on.
        val name = parts.firstOrNull {
            it.isNotBlank() && it.lowercase(Locale.ROOT) !in TYPE_WORDS && !it.matches(TALLY) &&
                !it.matches(YEAR) && !it.matches(DURATION)
        }
        return credits.copy(artistName = name)
    }

    /** How an album or playlist page bills itself, off its own header. */
    data class BrowseHeader(
        val title: String,
        /** The line under it — "Album • Artist • 2024", or a playlist's blurb. */
        val subtitle: String,
        val thumbnailUrl: String?,
    )

    /**
     * What a release or playlist page calls itself.
     *
     * Every other way into a detail page comes from a card that already carried
     * the name and the cover, so nothing used to have to ask. A YouTube Music
     * link tapped outside the app carries a browse id and nothing else — see
     * [com.music.bitchord.playback.MusicLink] — and a page with a blank title
     * over a track list reads as the app having half-loaded.
     */
    fun parseBrowseHeader(root: JsonElement): BrowseHeader? {
        val header = HEADER_RENDERERS.firstNotNullOfOrNull {
            collectRenderers(root, it).firstOrNull()
        } ?: return null
        val title = header.o("title").runs()
        if (title.isBlank()) return null
        // Same two header shapes as pageCredit: the current one straplines the
        // kind of release above the title, the older one packs it into the
        // subtitle. Either is a fair second line, so take whichever is there.
        val subtitle = header.o("straplineTextOne").runs()
            .ifBlank { header.o("subtitle").runs() }
        return BrowseHeader(
            title = title,
            subtitle = subtitle,
            // Header shapes drift — a cropped square here, a plain thumbnail
            // there — so the first image under the header is the cover.
            thumbnailUrl = collectRenderers(header, "musicThumbnailRenderer").firstOrNull()
                .o("thumbnail").a("thumbnails").best(),
        )
    }

    /**
     * The editorial blurb YouTube Music writes for a release or an artist —
     * "About the album" / "About the artist" on the web player.
     *
     * It arrives as its own shelf (`musicDescriptionShelfRenderer`) on a
     * current-layout page, but the field also turns up directly on the
     * header itself on some responses, so both are tried. Absent from a
     * playlist page — YouTube writes these for its own catalogue, not for
     * something a user put together — which is why callers only surface it
     * for [com.music.bitchord.data.model.BrowseType.ALBUM] and
     * [com.music.bitchord.data.model.BrowseType.ARTIST].
     */
    fun parseDescription(root: JsonElement): String? {
        val shelf = collectRenderers(root, "musicDescriptionShelfRenderer")
            .firstOrNull()?.o("description").runs()
        if (shelf.isNotBlank()) return shelf
        val onHeader = (HEADER_RENDERERS + "musicImmersiveHeaderRenderer")
            .firstNotNullOfOrNull { name ->
                collectRenderers(root, name).firstOrNull()
                    ?.o("description").runs().takeIf { it.isNotBlank() }
            }
        return onHeader
    }

    /**
     * The account header buried in the `account_menu` popup. Not every client
     * gets an `email` back — some return only the @handle — so whichever is
     * present is used as the secondary line.
     */
    fun parseAccount(response: JsonElement): Account? {
        val header = collectRenderers(response, "activeAccountHeaderRenderer").firstOrNull()
            ?: return null
        val name = header.o("accountName").runs()
        if (name.isBlank()) return null
        val email = header.o("email").runs()
            .ifBlank { header.o("email").s("simpleText").orEmpty() }
            .ifBlank { header.o("channelHandle").runs() }
        return Account(
            name = name,
            email = email,
            thumbnailUrl = header.o("accountPhoto").a("thumbnails").best(),
        )
    }

    /** Tracks of a watch queue (`next` response) — the AutoPlay radio mix. */
    fun parseWatchQueue(root: JsonElement): List<Song> {
        val out = LinkedHashMap<String, Song>()
        collectRenderers(root, "playlistPanelVideoRenderer").forEach { renderer ->
            val videoId = renderer.s("videoId") ?: return@forEach
            val title = renderer.o("title").runs()
            if (title.isBlank()) return@forEach
            // The byline packs artist, views and likes into one run list; only
            // the leading runs before the first bullet are the credit.
            val bylineRuns = renderer.o("longBylineText").a("runs").orEmpty()
            val byline = bylineRuns.map { it.s("text").orEmpty() }
            val artist = byline.takeWhile { !it.contains("•") }.joinToString("").trim()
            // Those same runs link out to the artist and album pages, which is
            // how a track started from the queue knows where it came from.
            val credits = creditsOf(bylineRuns)
            out[videoId] = Song(
                videoId = videoId,
                title = title,
                artist = artist,
                thumbnailUrl = renderer.o("thumbnail").a("thumbnails").best(),
                durationText = renderer.o("lengthText").runs().takeIf { it.isNotBlank() },
                artistId = credits.artistId,
                albumId = credits.albumId,
                albumName = credits.albumName,
                // A catalogue track is credited "Artist • Album • Year"; the
                // matching music video is "Artist • 417M views • 2.4M likes".
                isVideo = byline.any { it.contains("views", ignoreCase = true) },
            )
        }
        return out.values.toList()
    }

    /**
     * The account's own state for one track, read off the watch queue's row
     * menu: the thumbs rating, and the tokens that toggle library membership.
     *
     * Read from `next` rather than from anywhere cheaper because there is
     * nowhere cheaper — no endpoint answers "is this liked" on its own, and
     * library membership is only ever expressed as a pair of opaque tokens
     * attached to a rendered row. The queue's own entry for the track carries
     * both, so one call answers the whole menu.
     *
     * Scoped to [videoId]'s row: a watch queue is a list, and reading the
     * first `likeButtonRenderer` in the response would answer for whichever
     * track happened to be rendered first.
     */
    fun parseSongMenu(root: JsonElement, videoId: String): SongMenu? {
        val row = collectRenderers(root, "playlistPanelVideoRenderer")
            .firstOrNull { it.s("videoId") == videoId }
            ?: return null

        // Null, not INDIFFERENT, for anything this row doesn't actually say —
        // see [SongMenu.likeStatus]. A missing like button and a stated
        // "no rating" are different answers and must not collapse into one.
        val likeStatus = when (
            collectRenderers(row, "likeButtonRenderer").firstOrNull().s("likeStatus")
        ) {
            "LIKE" -> LikeStatus.LIKE
            "DISLIKE" -> LikeStatus.DISLIKE
            "INDIFFERENT" -> LikeStatus.INDIFFERENT
            else -> null
        }

        // A row's menu carries several toggles that all hang a feedback token
        // off the same endpoint — "Don't recommend this", "Remove from
        // history". Only the one wearing a library icon is this one, and
        // taking the first token that turned up meant reading a stranger's
        // state: its default icon isn't LIBRARY_ADD, so every song it matched
        // claimed to already be in the library.
        val toggle = collectRenderers(row, "toggleMenuServiceItemRenderer")
            .firstOrNull { it.feedbackToken("defaultServiceEndpoint") != null && it.isLibraryToggle }
        // A toggle states the action available *now* as its default and the
        // way back as its toggled half, so which icon leads also says whether
        // the track is in the library already.
        val defaultAdds = toggle.o("defaultIcon").s("iconType") == "LIBRARY_ADD"
        val defaultToken = toggle.feedbackToken("defaultServiceEndpoint")
        val toggledToken = toggle.feedbackToken("toggledServiceEndpoint")

        return SongMenu(
            likeStatus = likeStatus,
            inLibrary = toggle != null && !defaultAdds,
            addToLibraryToken = if (defaultAdds) defaultToken else toggledToken,
            removeFromLibraryToken = if (defaultAdds) toggledToken else defaultToken,
        )
    }

    private fun JsonElement?.feedbackToken(endpoint: String): String? =
        this.o(endpoint).o("feedbackEndpoint").s("feedbackToken")

    /**
     * Whether a toggle menu item is the library one, told by its icons rather
     * than by its label — the label is localised, the icon type never is.
     */
    private val JsonElement?.isLibraryToggle: Boolean
        get() = LIBRARY_ICONS.any {
            o("defaultIcon").s("iconType") == it || o("toggledIcon").s("iconType") == it
        }

    private val LIBRARY_ICONS = setOf("LIBRARY_ADD", "LIBRARY_REMOVE", "LIBRARY_SAVED")

    /**
     * Whether the album or playlist a browse response describes is in the
     * library, and the id that would change that — see [LibraryState].
     *
     * Both come off the page header, and both have to. A release's save control
     * is a [toggleButtonRenderer][isSaveToggle] wearing YouTube's bookmark
     * icons, *not* a like button: every track row on the page carries a
     * `likeButtonRenderer` aimed at its own `videoId` and the release carries
     * none at all, so reading a like button here answers for a track. Which is
     * how the first cut of this came back empty on every page — thirteen like
     * buttons on an album, every one of them a row's.
     *
     * The id is read from the header's *play* button, because it isn't the
     * browse id the page was fetched with: an `MPREb…` album is backed by an
     * `OLAK5uy_…` playlist, which the button names as a `watchPlaylistEndpoint`,
     * while a playlist page names its own raw id as a `watchEndpoint`. One of
     * the two answers for either kind of page.
     *
     * Scoped to the header rather than walked for, which matters more here than
     * it looks: an album page's "more from this artist" carousel is full of
     * *other* releases' playlist ids — a dozen of them, ahead of the header in
     * document order — so a page-wide walk would quietly save the wrong record.
     *
     * Null when the header has no save button to read: a continuation, a local
     * page, an auto-playlist, or a release YouTube marks unsaveable.
     */
    fun parseLibraryState(root: JsonElement): LibraryState? {
        val buttons = collectRenderers(root, "musicResponsiveHeaderRenderer")
            .firstOrNull()
            .a("buttons")
            .orEmpty()
        val save = buttons.firstNotNullOfOrNull { it.o("toggleButtonRenderer")?.takeIf { b -> b.isSaveToggle } }
            ?: return null
        if (save.s("isDisabled") == "true") return null
        // A play button states the release as a playlist, which is the one
        // thing on the page that names what saving would act on.
        val play = buttons.firstNotNullOfOrNull { it.o("musicPlayButtonRenderer").o("playNavigationEndpoint") }
        return LibraryState(
            playlistId = play.o("watchPlaylistEndpoint").s("playlistId")
                ?: play.o("watchEndpoint").s("playlistId")
                ?: return null,
            // The toggle carries the answer directly, rather than the
            // which-icon-leads reading a menu toggle needs: a button that is
            // *shown* toggled is one whose release is already saved.
            saved = save.s("isToggled") == "true",
        )
    }

    /**
     * Whether a header toggle is the save-to-library one rather than the
     * description's expander, told by its icons for the same reason
     * [isLibraryToggle] is: the label is localised, the icon type never is.
     *
     * Bookmarks, not the `LIBRARY_*` icons a track's menu uses — YouTube draws
     * the two actions differently even though they land in the same library.
     */
    private val JsonElement?.isSaveToggle: Boolean
        get() = o("defaultIcon").s("iconType") == "BOOKMARK_BORDER" ||
            o("toggledIcon").s("iconType") == "BOOKMARK"

    /**
     * Whether a playlist page is one the account *made*, rather than one it
     * merely saved — null when the response doesn't say either way.
     *
     * The library feed can't answer this. `FEmusic_liked_playlists` lists a
     * stranger's playlist this account saved in exactly the same shape as one
     * this account created (see [parseUserPlaylists]), which is how Rename and
     * Delete came to be offered on someone else's playlist. The page can
     * answer, and does so three ways over:
     *
     *  - An own playlist's header comes wrapped in
     *    `musicEditablePlaylistDetailHeaderRenderer` — YouTube's own words for
     *    "this belongs to whoever is asking".
     *  - Its header menu carries the Edit and Delete rows, read by icon rather
     *    than by label for the same reason [isSaveToggle] is.
     *  - Its header carries no save bookmark, and a saved playlist's always
     *    does: there is nothing to save a playlist already yours *into*. So a
     *    playlist header without one is this account's.
     *
     * Any one of the three is enough, because the two mistakes cost different
     * amounts. Reading "saved" as "own" puts a Delete button on a playlist the
     * account cannot delete; reading "own" as "saved" takes Rename and Delete
     * off the user's own playlist, which is a feature quietly vanishing. Three
     * readings rather than one so a layout change can only ever cause the
     * first, which the edit endpoint would refuse anyway.
     *
     * Only meaningful for a `VL…` playlist page — an album has a save bookmark
     * and no owner in this sense at all, and a continuation has no header.
     */
    fun parsePlaylistOwned(root: JsonElement): Boolean? {
        if (collectRenderers(root, "musicEditablePlaylistDetailHeaderRenderer").isNotEmpty()) {
            return true
        }
        // Scoped to the header, not walked for: every track row on the page
        // carries its own menu, and a row's "Remove from playlist" would
        // answer for the row rather than for the playlist.
        val header = collectRenderers(root, "musicResponsiveHeaderRenderer").firstOrNull()
            ?: return null
        if (collectRenderers(header, "menuNavigationItemRenderer")
                .any { it.o("icon").s("iconType") in OWNER_ICONS }
        ) {
            return true
        }
        return header.a("buttons").orEmpty().none { it.o("toggleButtonRenderer").isSaveToggle }
    }

    /** Header menu icons only the playlist's owner is offered. */
    private val OWNER_ICONS = setOf("DELETE", "EDIT")

    /**
     * The playlists the account can be asked to add a track to.
     *
     * `FEmusic_liked_playlists` also carries the "New playlist" tile (no
     * browse id, so it never survives [parseLibraryItems]), the Liked Music
     * auto-playlist and YouTube's own generated mixes — none of which take an
     * edit.
     *
     * Filtered by exclusion rather than by requiring a `PL` prefix. Playlist
     * ids are not as regular as they look, and a list that quietly drops the
     * user's own playlist is worse than one that offers a playlist the edit
     * endpoint then refuses — which it reports, and which the picker surfaces.
     *
     * Whether a playlist is *owned* rather than merely saved isn't stated on
     * this feed at all, so it isn't decided here: this stays the permissive
     * list the picker wants, and [parsePlaylistOwned] is what rules a saved
     * playlist out of being renamed or deleted.
     */
    fun parseUserPlaylists(root: JsonElement): List<UserPlaylist> =
        parseLibraryItems(root).mapNotNull { item ->
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

    private fun parseTwoRowItem(renderer: JsonObject?): ShelfItem? {
        if (renderer == null) return null
        val title = renderer.o("title").runs()
        if (title.isBlank()) return null
        val endpoint = renderer.o("navigationEndpoint")
        val browseId = endpoint.o("browseEndpoint").s("browseId")
        // History/"Listen again" cards for tracks YouTube never catalogued
        // as a proper Song carry no watchEndpoint at all — just a browseId
        // to a "non-music audio track page" prefixed MPED<videoId>. That's
        // the actual video id, not a real browsable page.
        val videoId = endpoint.o("watchEndpoint").s("videoId")
            ?: browseId?.takeIf { it.startsWith("MPED") }?.removePrefix("MPED")
        val resolvedBrowseId = browseId?.takeUnless { it.startsWith("MPED") }
        val thumbnails = renderer.o("thumbnailRenderer").o("musicThumbnailRenderer")
            .o("thumbnail").a("thumbnails")
        val subtitle = renderer.o("subtitle").runs()
        // A card with no browse target is a playable track, not an album,
        // playlist or artist; widescreen art on one of those means it's a
        // music-video upload rather than the catalogue track — drop it, same
        // as the equivalent check in parseResponsiveListItem.
        if (resolvedBrowseId == null && videoId != null && thumbnails.isNotSquare()) return null
        // An album/playlist billed as a video chart/compilation — "N videos"
        // in the subtitle, or "video" in the card's own title (e.g. "Daily
        // Top Music Videos") — is the same dead-end as in parseBrowseItem.
        // A plain track card is exempt: a song can legitimately be titled
        // "Video Games" without being a music-video upload.
        if (resolvedBrowseId != null &&
            (VIDEO_WORD.containsMatchIn(title) || VIDEO_WORD.containsMatchIn(subtitle))
        ) {
            return null
        }
        return ShelfItem(
            title = title,
            subtitle = subtitle,
            thumbnailUrl = thumbnails.best(),
            videoId = videoId,
            browseId = resolvedBrowseId,
        )
    }

    /**
     * The credit out of a shelf card's subtitle, which reads "Song • Chelsea
     * Wolfe" rather than just the artist — [parseTwoRowItem] keeps the whole
     * line because the card shows it as billed, but starting a radio off the
     * card and carrying that line into [Song.artist] would print the label
     * everywhere the field is read afterwards: the player, the mini player,
     * a shared link, a scrobble. Same split as [parseResponsiveListItem]'s
     * subtitle, so a "Song" or "Single" heading drops out the same way.
     */
    fun artistFromSubtitle(subtitle: String): String {
        val parts = subtitle.split(" • ").map(String::trim).filter { it.isNotBlank() }
        return parts.firstOrNull {
            it.lowercase() !in TYPE_WORDS && !it.matches(TALLY) && !it.matches(DURATION)
        } ?: subtitle
    }

    /**
     * Playlist-id prefixes nothing can be added to: `LM` is Liked Music (a
     * song joins it by being liked), `SE` is Episodes for Later, `RD` is a
     * generated radio mix, and `OLAK`/`MPRE` are albums wearing a playlist id.
     */
    private val NOT_EDITABLE = listOf("LM", "SE", "RD", "OLAK", "MPRE")

    private val DURATION = Regex("""\d+:\d{2}""")
    private val YEAR = Regex("""\d{4}""")
    /**
     * A counted quantity rather than a name — "12.4M plays", "13 songs",
     * "1 hour, 4 minutes". Deliberately narrow: it has to leave "21 Savage"
     * and "50 Cent" alone, so a number only disqualifies a segment when it is
     * counting one of the words YouTube counts with.
     */
    private val TALLY = Regex(
        """[\d.,]+\s*[KMB]?\s+(plays|views|likes|songs|tracks|subscribers|""" +
            """hours?|minutes?|seconds?)\b.*""",
        RegexOption.IGNORE_CASE,
    )
    /** Header words that mark a page as a release, whose rows share its credit. */
    private val RELEASE_WORDS = setOf("album", "single", "ep")
    private val HEADER_RENDERERS = listOf(
        "musicResponsiveHeaderRenderer",
        "musicDetailHeaderRenderer",
    )
    /** Header lines that name the artist, in either header shape. */
    private val HEADER_CREDIT_LINES = listOf("straplineTextOne", "subtitle")
    private val TYPE_WORDS = setOf(
        "song", "video", "album", "single", "ep", "artist",
        "playlist", "podcast", "episode",
    )
    /**
     * Flags a browse card as video content: "50 videos" in a subtitle
     * (instead of "50 songs"), or the word right in a title like
     * "Daily Top Music Videos".
     */
    private val VIDEO_WORD = Regex("""\bvideos?\b""", RegexOption.IGNORE_CASE)
}

// ---- Tiny JSON navigation helpers (null-safe, never throw) ------------------

private fun JsonElement?.o(key: String): JsonObject? =
    (this as? JsonObject)?.get(key) as? JsonObject

private fun JsonElement?.a(key: String): JsonArray? =
    (this as? JsonObject)?.get(key) as? JsonArray

private fun JsonElement?.s(key: String): String? =
    ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull

private fun JsonElement?.runs(): String =
    this.a("runs")?.joinToString("") { it.s("text").orEmpty() }.orEmpty()

/** The first run's text alone — for a field that is a count or a label, never a sentence. */
private fun JsonElement?.firstRunText(): String? =
    this.a("runs")?.firstOrNull().s("text")

/**
 * Last thumbnail is the largest, taken exactly as offered.
 *
 * This used to rewrite the size hint up to 544px on the way past, on the
 * reasoning that YouTube will serve any size asked for and bigger is better.
 * It is, for the one image drawn full-screen — and it is wasteful for every
 * other, which is most of them: YouTube offers a search row's cover at 120px
 * for 7.8kB and will happily serve the same cover at 544px for 84kB, to fill
 * a square the size of a fingertip.
 *
 * So the size is now decided where the image is drawn rather than here, by
 * [com.music.bitchord.data.model.artworkAt] — which trades up just as freely
 * as it trades down, and is what the player calls.
 */
private fun JsonArray?.best(): String? = this?.lastOrNull().s("url")

/**
 * Catalogue art is always square; a music-video upload's thumbnail is
 * widescreen. Missing dimensions default to "square" so a row is never
 * dropped just because the field wasn't present.
 */
private fun JsonArray?.isNotSquare(): Boolean {
    val last = this?.lastOrNull()
    val width = last.s("width")?.toDoubleOrNull() ?: return false
    val height = last.s("height")?.toDoubleOrNull() ?: return false
    if (width <= 0 || height <= 0) return false
    return width / height !in 0.85..1.15
}
