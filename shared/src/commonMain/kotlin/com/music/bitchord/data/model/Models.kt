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
    /** Whether this row originated as a video, including converted audio rows. */
    val isVideoOrigin: Boolean = isVideo,
    /** Identity within one playlist, when the row came from a playlist page. */
    val setVideoId: String? = null,
    /** Queued by AutoPlay rather than explicitly selected by the listener. */
    val fromAutoplay: Boolean = false,
    /** Seed title of an explicitly started radio queue. */
    val radioName: String? = null,
    /** Explicit content or file URI for local device tracks or downloaded audio. */
    val localUri: String? = null,
    /** Premium rendition marker recorded for a downloaded track. */
    val downloadFormat: String? = null,
    /** Real filesystem path backing [localUri], when one is available. */
    val localPath: String? = null,
    /** MediaStore timestamps used only to sort device and downloaded libraries. */
    val localDateAddedSeconds: Long? = null,
    val localDateModifiedSeconds: Long? = null,
    /** Quality advertised by a non-YouTube source. */
    val sourceQuality: String? = null,
    /** Explicit-content state from the catalogue; null when unknown. */
    val isExplicit: Boolean? = null,
)

/** Artwork at a given pixel size. */
fun Song.artworkAt(px: Int): String? = thumbnailUrl.artworkAt(px)

/**
 * Whether a row is the track the player is on, for the now-playing highlight.
 *
 * Title and credit only: every id a row could be matched on instead is scoped
 * to where the row came from, so a set-video-id names a slot in one playlist
 * and a video id differs between a local file, a download and a module source.
 * The cost is that an album track and its compilation appearance both light
 * up. The player's own queue must not use this — it matches on position.
 */
fun Song.isSameTrackAs(other: Song?): Boolean {
    other ?: return false
    return title == other.title && artist == other.artist
}

/** [Song.durationText] in milliseconds, or 0 when the row did not state one. */
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

const val ROW_ART_PX = 160
const val CARD_ART_PX = 480
const val HEADER_ART_PX = 720
const val NOTIFICATION_ART_PX = 544

/**
 * Artwork for the full player — sleeve and full-bleed banner both, and the
 * largest rung on the ladder. Named here rather than kept private to the
 * player because other surfaces pick their size off the same ladder and a size
 * only one of them asks for is a cache entry only that one fills.
 */
const val PLAYER_ART_PX = 1200

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
    data class TopTrack(val song: Song) : SearchResult
    data class Track(val song: Song) : SearchResult
    data class Browse(val item: BrowseItem) : SearchResult
}

enum class SearchFilter(val label: String, val params: String?) {
    ALL("All", null),
    SONGS("Songs", "EgWKAQIIAWoKEAkQChAFEAMQBA=="),
    VIDEOS("Videos", "EgWKAQIQAWoKEAkQChAFEAMQBA=="),
    ALBUMS("Albums", "EgWKAQIYAWoKEAkQChAFEAMQBA=="),
    ARTISTS("Artists", "EgWKAQIgAWoKEAkQChAFEAMQBA=="),
    PLAYLISTS("Playlists", "EgWKAQIoAWoKEAkQChAFEAMQBA=="),
}

/** A card in a home-feed carousel. */
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

/** One identity the signed-in session can act as. */
data class AccountChannel(
    val name: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val pageId: String?,
    val dataSyncId: String?,
    val activeOnWeb: Boolean,
) {
    val key: String get() = pageId ?: dataSyncId ?: name
}

data class HomeShelf(
    val title: String,
    val items: List<ShelfItem>,
    val subtitle: String = "",
)

data class HomeFeed(
    val shelves: List<HomeShelf>,
    val continuation: String?,
)

data class MoodGenreSection(
    val title: String,
    val items: List<MoodGenre>,
)

data class MoodGenre(
    val title: String,
    val browseId: String,
    val params: String?,
    val thumbnailUrl: String? = null,
)

data class LibraryPage(
    val likedSongs: List<Song>,
    val librarySongs: List<Song>,
    val shelves: List<HomeShelf>,
) {
    val isEmpty: Boolean
        get() = likedSongs.isEmpty() && librarySongs.isEmpty() && shelves.isEmpty()
}

data class DetailPage(
    val browseId: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val songs: UiState<List<Song>>,
    val type: BrowseType = BrowseType.OTHER,
    val sections: List<HomeShelf> = emptyList(),
    val suggestedSongs: List<Song> = emptyList(),
    val library: LibraryState? = null,
    val description: String? = null,
    val subscriberCountText: String? = null,
    val monthlyListenerCount: String? = null,
    /** Whether this artist's channel can be subscribed to, and whether it is. */
    val subscription: SubscriptionState? = null,
)

data class LibraryState(
    val playlistId: String,
    val saved: Boolean,
)

/**
 * Whether an artist's channel is subscribed to, and the channel that changes.
 *
 * Subscribing is the YouTube verb rather than a Music one: it takes the `UC…`
 * channel id, read off the header's subscribe button, which is also what says
 * whether the action is offered on this page at all.
 */
data class SubscriptionState(
    val channelId: String,
    val subscribed: Boolean,
)

data class ArtistPage(
    val songs: List<Song>,
    val moreSongsBrowseId: String?,
    val sections: List<HomeShelf>,
    val thumbnailUrl: String? = null,
    val name: String? = null,
    val description: String? = null,
    val subscriberCountText: String? = null,
    val monthlyListenerCount: String? = null,
    /** The header's subscribe button, when the page carries one. */
    val subscription: SubscriptionState? = null,
)

enum class LikeStatus { LIKE, DISLIKE, INDIFFERENT }

enum class PlaylistPrivacy(val label: String, val apiValue: String) {
    PRIVATE("Private", "PRIVATE"),
    UNLISTED("Unlisted", "UNLISTED"),
    PUBLIC("Public", "PUBLIC"),
}

data class UserPlaylist(
    val playlistId: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
) {
    val browseId: String get() = "VL$playlistId"
}

data class SongMenu(
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
