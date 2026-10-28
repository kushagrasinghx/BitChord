package com.music.bitchord.data

import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.LocalMusicSort
import com.music.bitchord.download.DownloadedCollection
import com.music.bitchord.download.Downloads
import java.util.Locale

/**
 * One album, playlist or artist inside a device folder, opened from the
 * folder's Albums or Artists tab as a page of its own — the same release page
 * an album off YouTube gets, not a list drawn inline in the folder.
 *
 * [browseId] is what makes it a page. A release downloaded whole is its own
 * download record's page ([Downloads.pageIdFor]); anything grouped off tags is
 * a [LocalGroups] id naming the folder and the name, so the page can be read
 * again after a delete or a rescan, or reopened from the player's "Playing
 * from", without anything having to hold on to the rows.
 */
data class LocalGroup(
    val browseId: String,
    val title: String,
    /** The credit under the title: an album's artist, or blank. */
    val subtitle: String,
    val thumbnailUrl: String?,
    /** [BrowseType.ALBUM], [BrowseType.PLAYLIST] for a playlist downloaded whole, [BrowseType.OTHER] for an artist. */
    val type: BrowseType,
    /** In the release's own order — see [sortedForAlbum]. */
    val songs: List<Song>,
    /** The record this was downloaded whole under; null for a tag grouping. */
    val downloadId: String? = null,
)

/**
 * The albums and artists a device folder's tracks group into, and the page ids
 * those groupings are addressed by.
 *
 * Kept out of the UI because both sides need it: the folder draws the
 * groupings, and the view model has to rebuild one from a fresh listing when
 * its page is reloaded or reopened by id.
 */
object LocalGroups {
    /**
     * Under `local:` like every other page whose tracks are already on the
     * device — which is what keeps the download button off it — but in a
     * segment of its own, so it is never mistaken for one of the folders.
     */
    const val PREFIX = "local:group:"
    private const val ALBUM = "album"
    private const val ARTIST = "artist"

    fun albumPageId(folderId: String, name: String): String = pageId(ALBUM, folderId, name)

    fun artistPageId(folderId: String, name: String): String = pageId(ARTIST, folderId, name)

    // `local:group:album:downloads:Some: Name` — the folder id is always
    // `local:<word>`, so only its word is stored and the name, which may hold
    // colons of its own, is everything after it.
    private fun pageId(kind: String, folderId: String, name: String) =
        "$PREFIX$kind:${folderId.removePrefix("local:")}:$name"

    private class Parsed(val kind: String, val folderId: String, val name: String)

    private fun parse(browseId: String?): Parsed? {
        if (browseId == null || !browseId.startsWith(PREFIX)) return null
        val parts = browseId.removePrefix(PREFIX).split(':', limit = 3)
        if (parts.size != 3 || (parts[0] != ALBUM && parts[0] != ARTIST) || parts[1].isEmpty()) return null
        return Parsed(parts[0], "local:" + parts[1], parts[2])
    }

    /** The device folder [browseId] groups part of, or null if it isn't a grouping. */
    fun folderOf(browseId: String?): String? = parse(browseId)?.folderId

    /** What kind of page [browseId] is, or null if it isn't a grouping. */
    fun typeOf(browseId: String): BrowseType? =
        parse(browseId)?.let { if (it.kind == ALBUM) BrowseType.ALBUM else BrowseType.OTHER }

    fun isArtist(browseId: String?): Boolean = parse(browseId)?.kind == ARTIST

    /** The rows of [folderSongs] the grouping [browseId] names, in the order its page lists them. */
    fun songsOf(browseId: String, folderSongs: List<Song>): List<Song> {
        val group = parse(browseId) ?: return emptyList()
        return if (group.kind == ALBUM) {
            folderSongs.filter { it.albumName == group.name }.sortedForAlbum()
        } else {
            folderSongs.filter { it.artist == group.name }.sortedByTitle()
        }
    }

    /**
     * The Albums tab: the releases downloaded whole, then whatever else the
     * files' own album tags group up.
     *
     * The two are merged rather than shown as separate sections because they are
     * the same kind of thing to whoever is looking for one — a folder of songs
     * with a name they remember. What matters is only that the *named* ones win
     * a collision: an album downloaded whole also stamps its name onto each of
     * its tracks (see `withAlbum` in MainActivity), so without this every one of
     * them would appear twice, once with its cover and once without.
     *
     * A playlist cannot be derived from tags at all — its tracks are off forty
     * different releases and none of them says which playlist it was pulled
     * from — so [collections] is the only way one appears here. Empty for
     * Local Music, where nothing was asked for through this app.
     *
     * Releases lead within their own alphabetical run rather than being sorted
     * together, because a tag grouping is a guess and a recorded release is not.
     */
    fun albumsOf(
        folderId: String,
        songs: List<Song>,
        collections: List<DownloadedCollection>,
    ): List<LocalGroup> {
        val asked = collections.map { collection ->
            LocalGroup(
                browseId = Downloads.pageIdFor(collection.id),
                title = collection.title,
                // A playlist's tracks are off forty releases, so the first
                // one's artist is no credit for it.
                subtitle = collection.subtitle.ifBlank {
                    if (collection.playlist) "" else collection.songs.firstOrNull()?.artist.orEmpty()
                },
                thumbnailUrl = collection.thumbnailUrl,
                type = if (collection.playlist) BrowseType.PLAYLIST else BrowseType.ALBUM,
                songs = collection.songs,
                downloadId = collection.id,
            )
        }
        val claimed = asked.mapTo(HashSet()) { it.title.lowercase(Locale.ROOT) }
        val derived = songs
            .groupBy { it.albumName }
            .mapNotNull { (name, group) ->
                // Null is every track that never said what release it was off,
                // and there is no row to draw for "no album" — those are the
                // Songs tab's and nothing else's.
                if (name == null || name.lowercase(Locale.ROOT) in claimed) return@mapNotNull null
                LocalGroup(
                    browseId = albumPageId(folderId, name),
                    title = name,
                    subtitle = group.firstOrNull()?.artist.orEmpty(),
                    thumbnailUrl = group.firstNotNullOfOrNull { it.thumbnailUrl },
                    type = BrowseType.ALBUM,
                    songs = group.sortedForAlbum(),
                )
            }
        return (asked + derived).sortedByLowercaseTitle(leading = { it.downloadId != null })
    }

    /**
     * The Artists tab, by name. No cover of their own: a track's sleeve is not
     * a picture of whoever made it, so the page opens on a placeholder.
     */
    fun artistsOf(folderId: String, songs: List<Song>): List<LocalGroup> =
        songs.groupBy { it.artist }
            .map { (name, group) ->
                LocalGroup(
                    browseId = artistPageId(folderId, name),
                    title = name,
                    subtitle = "",
                    thumbnailUrl = null,
                    type = BrowseType.OTHER,
                    songs = group.sortedByTitle(),
                )
            }
            .sortedByLowercaseTitle()

    /**
     * By title lowercased in the root locale, after any [leading] groups. Each
     * title is lowercased once up front rather than inside the comparator,
     * which would allocate two strings per comparison — tens of thousands of
     * them for a large library's Artists tab.
     */
    private fun List<LocalGroup>.sortedByLowercaseTitle(
        leading: (LocalGroup) -> Boolean = { false },
    ): List<LocalGroup> =
        map { it.title.lowercase(Locale.ROOT) to it }
            .sortedWith(compareByDescending<Pair<String, LocalGroup>> { leading(it.second) }.thenBy { it.first })
            .map { it.second }
}

/**
 * Whether this track is a hit for a query typed into a page's filter box — or
 * into the Search tab with its Library source picked, which has to find the
 * same tracks a folder would.
 */
fun Song.matchesSearch(query: String): Boolean =
    title.contains(query, ignoreCase = true) ||
        artist.contains(query, ignoreCase = true) ||
        albumName?.contains(query, ignoreCase = true) == true

/** A device folder's running order, per the sort its top bar menu picked. */
fun List<Song>.sortedForLibrary(order: LocalMusicSort): List<Song> = when (order) {
    LocalMusicSort.TITLE_ASC -> sortedByTitle()
    // Lowercased once per row, not once per comparison; see sortedByLowercaseTitle.
    LocalMusicSort.TITLE_DESC -> map { it.title.lowercase(Locale.ROOT) to it }
        .sortedByDescending { it.first }
        .map { it.second }
    LocalMusicSort.DATE_ADDED -> sortedWith(
        compareByDescending<Song> { it.localDateAddedSeconds ?: Long.MIN_VALUE }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
    )
    LocalMusicSort.DATE_MODIFIED -> sortedWith(
        compareByDescending<Song> { it.localDateModifiedSeconds ?: Long.MIN_VALUE }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
    )
}

private fun List<Song>.sortedByTitle(): List<Song> =
    sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

/**
 * Release order for one tag-derived album: by the file's own track number,
 * falling back to title only for rows whose tags say nothing. Untagged rows
 * sort after tagged ones rather than interleaving at the head.
 */
fun List<Song>.sortedForAlbum(): List<Song> {
    if (none { it.trackNumber != null }) return this
    return sortedWith(
        compareBy<Song> { it.trackNumber ?: Int.MAX_VALUE }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
    )
}

/**
 * Whether a track list is one album's own tracks rather than an artist's mixed
 * rows: every song names the same album and at least one carries a tag number.
 * Only then does a numbered row show the tag's number rather than its place.
 */
fun List<Song>.useTrackTags(): Boolean {
    if (isEmpty() || none { it.trackNumber != null }) return false
    val album = first().albumName ?: return false
    return all { it.albumName == album }
}
