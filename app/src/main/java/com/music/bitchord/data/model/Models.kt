package com.music.bitchord.data.model

/** A playable YouTube Music track. */
data class Song(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val durationText: String? = null,
    /** Browse ids lifted from the row, used by the long-press actions. */
    val artistId: String? = null,
    val albumId: String? = null,
    /** Names the album page header, which [albumId] alone can't. */
    val albumName: String? = null,
    /** A music-video upload rather than the catalogue track. */
    val isVideo: Boolean = false,
    /**
     * Queued by AutoPlay or by a station's own mix rather than asked for — the
     * player groups these under the AutoPlay heading and keeps them at the
     * bottom of the queue, below anything the user picked.
     */
    val fromAutoplay: Boolean = false,
)

/**
 * Artwork at a given pixel size.
 *
 * YouTube serves every size from one URL via a `w<n>-h<n>` hint, so the size
 * an image is fetched at is the caller's to choose, and worth choosing in both
 * directions. Up: the size YouTube advertises is far short of what a
 * full-screen player draws, and the source images run to about 1400px, so
 * asking for more is free and sharper. Down: a row thumbnail left at the
 * advertised size costs an order of magnitude more bytes than the square it
 * fills — 84kB against 7.8kB, measured on the same cover.
 *
 * Video thumbnails carry no hint and are returned unchanged.
 */
fun Song.artworkAt(px: Int): String? = thumbnailUrl.artworkAt(px)

/** As [Song.artworkAt], for artwork that isn't a track's. */
fun String?.artworkAt(px: Int): String? = this?.replace(SIZE_HINT, "w$px-h$px")

private val SIZE_HINT = Regex("""w\d+-h\d+""")

/**
 * Artwork for a list row — 52dp at most, so about 140px on a 3x screen.
 * Rounded up, and one value for every row in the app rather than one per
 * row height, so they share a cache entry instead of each fetching its own.
 */
const val ROW_ART_PX = 160

/** Artwork for a shelf card: 166dp wide, so a little under 450px at 3x. */
const val CARD_ART_PX = 480

/** Artwork for a page header, drawn near enough full width. */
const val HEADER_ART_PX = 720

/**
 * Artwork handed to the media session — the lock screen, the notification,
 * Android Auto. Generous because those surfaces draw it large and take one
 * copy: unlike a list row, nothing goes back for a better one later.
 */
const val NOTIFICATION_ART_PX = 544

enum class BrowseType { ALBUM, ARTIST, PLAYLIST, OTHER }

/** A non-track search result: album, artist or playlist. */
data class BrowseItem(
    val browseId: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val type: BrowseType,
)

/** Search rows are heterogeneous once filters other than "Songs" are used. */
sealed interface SearchResult {
    data class Track(val song: Song) : SearchResult
    data class Browse(val item: BrowseItem) : SearchResult
}

enum class SearchFilter(val label: String, val params: String?) {
    SONGS("Songs", "EgWKAQIIAWoKEAkQChAFEAMQBA=="),
    ALBUMS("Albums", "EgWKAQIYAWoKEAkQChAFEAMQBA=="),
    ARTISTS("Artists", "EgWKAQIgAWoKEAkQChAFEAMQBA=="),
    PLAYLISTS("Playlists", "EgWKAQIoAWoKEAkQChAFEAMQBA=="),
}

/** A card in a home-feed carousel: either a track (videoId) or an album/playlist (browseId). */
data class ShelfItem(
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val videoId: String?,
    val browseId: String?,
)

/** The signed-in Google account, as YouTube Music reports it. */
data class Account(
    val name: String,
    val email: String,
    val thumbnailUrl: String?,
)

data class HomeShelf(
    val title: String,
    val items: List<ShelfItem>,
    /** YouTube's "strapline" — the grey line Apple Music runs under a heading. */
    val subtitle: String = "",
)

/** A page of the Home feed, plus the token for the next one — null once exhausted. */
data class HomeFeed(
    val shelves: List<HomeShelf>,
    val continuation: String?,
)

/**
 * The signed-in library, as YouTube Music splits it: the auto-generated Liked
 * Music playlist, the tracks explicitly added to the library, and a shelf per
 * saved collection (playlists, albums, artists, subscriptions, podcasts).
 */
data class LibraryPage(
    val likedSongs: List<Song>,
    val librarySongs: List<Song>,
    val shelves: List<HomeShelf>,
) {
    val isEmpty: Boolean
        get() = likedSongs.isEmpty() && librarySongs.isEmpty() && shelves.isEmpty()
}

/** A browsed album / artist / playlist page. */
data class DetailPage(
    val browseId: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val songs: UiState<List<Song>>,
    val type: BrowseType = BrowseType.OTHER,
    /** Albums / singles carousels, populated for artist pages. */
    val sections: List<HomeShelf> = emptyList(),
)

/** Parsed artist landing page. */
data class ArtistPage(
    val songs: List<Song>,
    /** Playlist holding the artist's full song list, when the page links one. */
    val moreSongsBrowseId: String?,
    val sections: List<HomeShelf>,
    /** The artist's own picture, off the page header. */
    val thumbnailUrl: String? = null,
    /** The single artist this page is for, as the header bills them. */
    val name: String? = null,
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
