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
     * This track's identity *within one playlist*, which is not its [videoId]:
     * the same song added twice is two entries with two set-video-ids, and
     * removing one of them is only expressible in those terms. Present only on
     * rows parsed from a playlist page, which is the only place a removal can
     * be asked for from.
     */
    val setVideoId: String? = null,
    /**
     * Queued by AutoPlay or by a station's own mix rather than asked for — the
     * player groups these under the AutoPlay heading and keeps them at the
     * bottom of the queue, below anything the user picked.
     */
    val fromAutoplay: Boolean = false,
    /**
     * Explicit content or file URI for local device tracks or downloaded audio.
     */
    val localUri: String? = null,
    /**
     * Real filesystem path backing [localUri], when MediaStore exposes one.
     * Lets playback swap a content:// row for a raw file:// path on formats
     * that need it — see [com.music.bitchord.playback.toMediaItem].
     */
    val localPath: String? = null,
    /**
     * What a non-YouTube source says it can serve this recording at, as one of
     * `LOSSLESS`, `HIGH` or `LOW` — null for every row that didn't come from
     * one.
     *
     * Carried on the row rather than discovered at stream time because it is
     * the only thing that distinguishes two catalogues holding the same track,
     * and the choice between them has to be made *before* either is asked for
     * a URL. Without it the picker was blind: a Deezer row and a 16-bit FLAC
     * row looked identical, the FLAC lost a tie-break on artist spelling, and
     * the track played as a 128kbps MP3.
     */
    val sourceQuality: String? = null,
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

/**
 * [Song.durationText] in milliseconds, or 0 when the row didn't state one.
 *
 * A row's duration is a display string — YouTube sends `"3:45"`, not a
 * number — and anything that has to *reason* about the length rather than draw
 * it needs it back as a quantity. Lyrics matching is the case that forced this
 * out into the open: LRCLIB keys its exact lookup on the track's length, and
 * falls back to whichever fuzzy hit is closest to it, so a duration of zero
 * doesn't miss — it silently matches the shortest edit of the song in the
 * database and hands back timings for a different recording.
 *
 * Zero is the answer for anything that isn't a duration, including null, so a
 * caller has one thing to check rather than a nullable *and* a range.
 */
fun Song.durationMillis(): Long = durationText.durationMillis()

/** As [Song.durationMillis], for a `M:SS` or `H:MM:SS` string on its own. */
fun String?.durationMillis(): Long {
    val parts = this?.trim()?.takeIf { it.isNotEmpty() }?.split(":") ?: return 0L
    val numbers = parts.map { it.trim().toLongOrNull() ?: return 0L }
    val seconds = when (numbers.size) {
        2 -> numbers[0] * 60 + numbers[1]
        3 -> numbers[0] * 3_600 + numbers[1] * 60 + numbers[2]
        else -> return 0L
    }
    return (seconds * 1_000).coerceAtLeast(0L)
}

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
    /**
     * Tracks YouTube offers to round out a playlist but that were never
     * added — see [com.music.bitchord.data.innertube.InnertubeParser.parsePlaylistShelf].
     * Shown as their own section with a button to actually add them, rather
     * than folded into [songs] where they'd read as the user's own picks.
     */
    val suggestedSongs: List<Song> = emptyList(),
    /**
     * Whether this release can be saved to the library and whether it already
     * is — null when the page doesn't offer it at all. Only ever set for an
     * album or playlist fetched with a session; see [LibraryState].
     */
    val library: LibraryState? = null,
    /**
     * The editorial blurb YouTube Music writes for a release or an artist —
     * absent for most playlists, which is also why the "About" section only
     * ever shows for an album or an artist page.
     */
    val description: String? = null,
    /** "1.2M subscribers" off an artist page's header — see [ArtistPage.subscriberCountText]. */
    val subscriberCountText: String? = null,
    /** "3.4M monthly listeners" off an artist page's header. */
    val monthlyListenerCount: String? = null,
)

/**
 * Whether an album or playlist is in the library, and the id that changes that.
 *
 * YouTube has no "save" verb for a release: a saved album is a *liked* one, and
 * what gets liked is the playlist behind the page rather than the browse id the
 * page was fetched with — an `MPREb…` album is backed by an `OLAK5uy_…`
 * playlist, and liking the browse id does nothing at all. So the id has to be
 * read off the page rather than derived from what was asked for.
 */
data class LibraryState(
    val playlistId: String,
    val saved: Boolean,
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
    /** The artist bio YouTube Music writes for the page, when it has one. */
    val description: String? = null,
    /** "1.2M subscribers" — the artist's YouTube channel, when subscribed counts are shown. */
    val subscriberCountText: String? = null,
    /** "3.4M monthly listeners", off the same header. */
    val monthlyListenerCount: String? = null,
)

/**
 * A track's thumbs rating on the signed-in account.
 *
 * [INDIFFERENT] is YouTube's own word for "neither", and is a real state
 * rather than the absence of one — clearing a like is a request in its own
 * right (`like/removelike`), not the omission of one.
 */
enum class LikeStatus { LIKE, DISLIKE, INDIFFERENT }

/** Who can see a playlist. YouTube's own three values, sent verbatim. */
enum class PlaylistPrivacy(val label: String, val apiValue: String) {
    PRIVATE("Private", "PRIVATE"),
    UNLISTED("Unlisted", "UNLISTED"),
    PUBLIC("Public", "PUBLIC"),
}

/**
 * One of the account's own playlists, as the picker lists them.
 *
 * [playlistId] is the raw id (no `VL`), because that is what the edit endpoint
 * takes; [browseId] is the same playlist addressed as a page. Keeping both
 * spares every caller from remembering which prefix each side wants.
 */
data class UserPlaylist(
    val playlistId: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
) {
    val browseId: String get() = "VL$playlistId"
}

/**
 * The per-track state that only YouTube can answer: its rating, and whether it
 * is in the library.
 *
 * Library membership is not addressable by video id — it is toggled with an
 * opaque feedback token that YouTube mints per row and per direction, so the
 * tokens have to be fetched before the action can be offered at all. Both
 * arrive together on the watch queue's own menu, which is why this is one
 * lookup rather than two.
 */
data class SongMenu(
    /**
     * The rating YouTube states on this row, or null when the row states
     * none — which is common, and is *not* the same as INDIFFERENT. A watch
     * queue frequently renders without a like button at all, and reading that
     * silence as "not liked" is how a liked song ends up claiming it isn't.
     */
    val likeStatus: LikeStatus?,
    val inLibrary: Boolean,
    val addToLibraryToken: String?,
    val removeFromLibraryToken: String?,
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
