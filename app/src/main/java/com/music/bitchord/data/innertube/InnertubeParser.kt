package com.music.bitchord.data.innertube

import com.music.bitchord.data.model.Account
import com.music.bitchord.data.model.ArtistPage
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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

        sections.forEach { section ->
            section.o("musicShelfRenderer")?.let { shelf ->
                shelf.a("contents").orEmpty().forEach { row ->
                    parseResponsiveListItem(row.o("musicResponsiveListItemRenderer"))
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
        val header = response["header"]
        return ArtistPage(
            songs, moreSongs, shelves,
            thumbnailUrl = artistThumbnail(header),
            name = artistName(header),
        )
    }

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
        fun walk(node: JsonElement) {
            when (node) {
                is JsonObject -> {
                    node["musicResponsiveListItemRenderer"]?.let { renderer ->
                        parseResponsiveListItem(renderer as? JsonObject)
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

    private fun parseResponsiveListItem(renderer: JsonObject?): Song? {
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
        val rowType = parts.firstOrNull { it.lowercase() in TYPE_WORDS }?.lowercase()
        val artist = parts.firstOrNull {
            !it.matches(DURATION) && it.lowercase() !in TYPE_WORDS
        } ?: "Unknown artist"

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
            // credit; the "All" tab often lists only "Song • 4:30" otherwise.
            artist = credits.artistName?.takeIf { it.isNotBlank() } ?: artist,
            thumbnailUrl = thumbnails.best(),
            durationText = duration,
            artistId = credits.artistId,
            albumId = credits.albumId,
            albumName = credits.albumName,
            // The row type word is the clean signal when present ("All" tab);
            // otherwise a music-video upload gives itself away with widescreen
            // art where a catalogue track has square album cover art.
            isVideo = rowType == "video" || thumbnails.isNotSquare(),
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

    private val DURATION = Regex("""\d+:\d{2}""")
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

/**
 * Last thumbnail is the largest. The size hint is normalised rather than
 * trusted — YouTube's largest offer is 544px, but it will serve any size
 * asked for, and [com.music.bitchord.data.model.artworkAt] trades up from
 * here for the full-screen player.
 */
private fun JsonArray?.best(): String? =
    this?.lastOrNull().s("url")?.replace(Regex("""w\d+-h\d+"""), "w544-h544")

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
