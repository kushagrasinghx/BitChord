package com.music.bitchord.desktop

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.model.ArtistPage
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.HomeFeed
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The desktop's door onto YouTube Music.
 *
 * Nothing here talks to YouTube itself any more: every call is the phone's own
 * [YtMusicRepository] / [Innertube], shared from `:shared`, so a parsing fix
 * lands on both at once. What stays is the desktop's calling convention — the
 * names its pages already use, and [DesktopCollection] for a browsed page.
 */
object DesktopSearchClient {

    suspend fun search(
        query: String,
        filter: SearchFilter = SearchFilter.SONGS,
    ): Result<List<SearchResult>> {
        if (query.isBlank()) return Result.failure(IllegalArgumentException("Search query cannot be empty"))
        return YtMusicRepository.search(query.trim(), filter)
    }

    suspend fun home(): Result<HomeFeed> = YtMusicRepository.home()

    suspend fun moreHome(token: String): Result<HomeFeed> = YtMusicRepository.moreHome(token)

    suspend fun homeSupplement(browseId: String): Result<List<HomeShelf>> =
        YtMusicRepository.homeSupplement(browseId)

    val HOME_SUPPLEMENT_BROWSE_IDS: List<String> get() = YtMusicRepository.HOME_SUPPLEMENT_BROWSE_IDS

    suspend fun moodAndGenres(): Result<List<MoodGenreSection>> = YtMusicRepository.moodAndGenres()

    suspend fun moodGenreShelves(browseId: String, params: String?): Result<List<HomeShelf>> =
        YtMusicRepository.moodGenreShelves(browseId, params)

    suspend fun moodGenreArtwork(browseId: String, params: String?): Result<String?> =
        YtMusicRepository.moodGenreArtwork(browseId, params)

    /** An album, playlist or artist song list, in the shape the desktop's collection page draws. */
    suspend fun browse(browseId: String, fallback: BrowseItem? = null): Result<DesktopCollection> =
        YtMusicRepository.browseSongs(browseId).map { page ->
            val header = page.header
            DesktopCollection(
                browseId = browseId,
                title = header?.title?.takeIf(String::isNotBlank)
                    ?: fallback?.title?.takeIf(String::isNotBlank)
                    ?: "Collection",
                subtitle = header?.subtitle?.takeIf(String::isNotBlank) ?: fallback?.subtitle.orEmpty(),
                thumbnailUrl = header?.thumbnailUrl ?: fallback?.thumbnailUrl,
                type = fallback?.type?.takeIf { it != BrowseType.OTHER } ?: browseTypeOf(browseId),
                songs = page.songs,
                continuation = page.continuation,
                owned = page.owned,
            )
        }

    /** The rows after a collection's first page, and the token after those. */
    suspend fun moreCollectionSongs(
        token: String,
        @Suppress("UNUSED_PARAMETER") artistFallback: String? = null,
    ): Result<Pair<List<Song>, String?>> =
        YtMusicRepository.moreSongs(token).map { it.songs to it.continuation }

    suspend fun history(): Result<List<Song>> = YtMusicRepository.history()

    suspend fun radio(videoId: String): Result<List<Song>> = YtMusicRepository.radio(videoId)

    suspend fun trackLinks(videoId: String): Result<Song> = YtMusicRepository.trackLinks(videoId)

    suspend fun library(): Result<LibraryPage> = YtMusicRepository.library()

    /** Follows Liked Music's pages past the first, handing each page's ids over as it lands. */
    suspend fun syncLikedIds(firstToken: String, onIds: suspend (Set<String>) -> Unit) {
        val seen = HashSet<String>()
        var next: String? = firstToken
        while (next != null && seen.add(next)) {
            val page = YtMusicRepository.moreSongs(next).getOrNull() ?: return
            onIds(page.songs.mapTo(HashSet()) { it.videoId })
            next = page.continuation
        }
    }

    suspend fun rate(videoId: String, status: LikeStatus): Result<Unit> = YtMusicRepository.rate(videoId, status)

    suspend fun artistPage(browseId: String): Result<ArtistPage> = YtMusicRepository.artistPage(browseId)

    suspend fun setSubscribed(channelId: String, subscribed: Boolean): Result<Unit> =
        YtMusicRepository.setSubscribed(channelId, subscribed)

    suspend fun createPlaylist(
        title: String,
        privacy: PlaylistPrivacy,
        videoIds: List<String> = emptyList(),
    ): Result<String> = YtMusicRepository.createPlaylist(title, privacy, videoIds)

    suspend fun deletePlaylist(playlistId: String): Result<Unit> = YtMusicRepository.deletePlaylist(playlistId)

    suspend fun renamePlaylist(playlistId: String, title: String): Result<Unit> =
        YtMusicRepository.renamePlaylist(playlistId, title)

    suspend fun addToPlaylist(playlistId: String, videoIds: List<String>): Result<Map<String, String>> =
        YtMusicRepository.addToPlaylist(playlistId, videoIds)

    suspend fun removeFromPlaylist(playlistId: String, entries: List<Pair<String, String>>): Result<Unit> =
        YtMusicRepository.removeFromPlaylist(playlistId, entries)

    suspend fun searchTypeahead(input: String): Result<List<SearchResult>> =
        YtMusicRepository.searchTypeahead(input).map { it.rows }

    suspend fun searchSuggestions(input: String): Result<List<String>> = YtMusicRepository.searchSuggestions(input)

    suspend fun accountMenu(): Result<JsonObject> = runCatching { Innertube.accountMenu() }

    suspend fun accountsList(): Result<JsonObject> = runCatching { Innertube.accountsList() }

    /** The account's own playlists among a library shelf's items — the ones an edit can target. */
    internal fun userPlaylists(items: List<com.music.bitchord.data.model.ShelfItem>) =
        com.music.bitchord.data.innertube.InnertubeParser.parseUserPlaylists(items)

    internal fun isVideoId(id: String): Boolean = VIDEO_ID.matches(id)

    private val VIDEO_ID = Regex("""[A-Za-z0-9_-]{11}""")

    /** Every object named [name] anywhere under [root], depth first. */
    internal fun renderers(root: JsonElement, name: String): List<JsonObject> = buildList {
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> element.forEach { (key, value) ->
                    if (key == name && value is JsonObject) add(value)
                    walk(value)
                }
                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(root)
    }
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

internal fun browseTypeOf(browseId: String): BrowseType = when {
    browseId.startsWith("MPREb_") || browseId.startsWith("OLAK5uy_") -> BrowseType.ALBUM
    browseId.startsWith("VL") -> BrowseType.PLAYLIST
    browseId.startsWith("UC") -> BrowseType.ARTIST
    else -> BrowseType.OTHER
}
