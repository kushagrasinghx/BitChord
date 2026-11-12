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
    private const val FOLDER = "folder"

    fun albumPageId(folderId: String, name: String): String = pageId(ALBUM, folderId, name)

    fun artistPageId(folderId: String, name: String): String = pageId(ARTIST, folderId, name)

    /** One directory on the device, by its full path — see [foldersOf]. */
    fun directoryPageId(folderId: String, path: String): String = pageId(FOLDER, folderId, path)

    // `local:group:album:downloads:Some: Name` — the folder id is always
    // `local:<word>`, so only its word is stored and the name, which may hold
    // colons of its own, is everything after it.
    private fun pageId(kind: String, folderId: String, name: String) =
        "$PREFIX$kind:${folderId.removePrefix("local:")}:$name"

    private val KINDS = setOf(ALBUM, ARTIST, FOLDER)

    private class Parsed(val kind: String, val folderId: String, val name: String)

    private fun parse(browseId: String?): Parsed? {
        if (browseId == null || !browseId.startsWith(PREFIX)) return null
        val parts = browseId.removePrefix(PREFIX).split(':', limit = 3)
        if (parts.size != 3 || parts[0] !in KINDS || parts[1].isEmpty()) return null
        return Parsed(parts[0], "local:" + parts[1], parts[2])
    }

    /** The device folder [browseId] groups part of, or null if it isn't a grouping. */
    fun folderOf(browseId: String?): String? = parse(browseId)?.folderId

    /** What kind of page [browseId] is, or null if it isn't a grouping. */
    fun typeOf(browseId: String): BrowseType? =
        parse(browseId)?.let {
            when (it.kind) {
                ALBUM -> BrowseType.ALBUM
                // A directory is a list somebody put together by hand, which is
                // what a playlist is too — and it plays like one.
                FOLDER -> BrowseType.PLAYLIST
                else -> BrowseType.OTHER
            }
        }

    fun isArtist(browseId: String?): Boolean = parse(browseId)?.kind == ARTIST

    fun isDirectory(browseId: String?): Boolean = parse(browseId)?.kind == FOLDER

    /** The rows of [folderSongs] the grouping [browseId] names, in the order its page lists them. */
    fun songsOf(browseId: String, folderSongs: List<Song>): List<Song> {
        val group = parse(browseId) ?: return emptyList()
        return when (group.kind) {
            ALBUM -> folderSongs.filter { it.albumName == group.name }.sortedForAlbum()
            FOLDER -> folderSongs.filter { it.directory() == group.name }.sortedByTitle()
            else -> folderSongs.filter { it.artist == group.name }.sortedByTitle()
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
                album(folderId, name, group)
            }
        return (asked + derived).sortedWith(
            compareByDescending<LocalGroup> { it.downloadId != null }
                .thenBy { it.title.lowercase(Locale.ROOT) },
        )
    }

    /**
     * The Artists tab, by name. No cover of their own: a track's sleeve is not
     * a picture of whoever made it, so the page opens on a placeholder.
     */
    fun artistsOf(folderId: String, songs: List<Song>): List<LocalGroup> =
        songs.groupBy { it.artist }
            .map { (name, group) -> artist(folderId, name, group) }
            .sortedBy { it.title.lowercase(Locale.ROOT) }

    /**
     * The Folders tab: every directory on the device holding any of [songs],
     * the way a file manager would name it, with where it sits under it.
     *
     * A track with no path — one off a file server, or a scanner row that never
     * said — has no directory to be in, and is the Songs tab's alone. Two
     * directories of the same name in different places stay two rows; the
     * subtitle is what tells them apart.
     */
    fun foldersOf(folderId: String, songs: List<Song>): List<LocalGroup> =
        songs.groupBy { it.directory() }
            .mapNotNull { (path, group) ->
                if (path == null) return@mapNotNull null
                folder(folderId, path, group)
            }
            .sortedWith(compareBy<LocalGroup> { it.title.lowercase(Locale.ROOT) }.thenBy { it.subtitle })

    /**
     * The grouping [browseId] names, rebuilt from [songs] — its own rows, as
     * [songsOf] picks them — or null if it isn't a grouping.
     *
     * For a page opened by its id alone, such as from the player's "Playing
     * from", so it gets the same cover and credit as when its tab row opens it.
     */
    fun groupOf(browseId: String, songs: List<Song>): LocalGroup? {
        val group = parse(browseId) ?: return null
        return when (group.kind) {
            ALBUM -> album(group.folderId, group.name, songs)
            FOLDER -> folder(group.folderId, group.name, songs)
            else -> artist(group.folderId, group.name, songs)
        }
    }

    private fun album(folderId: String, name: String, songs: List<Song>) = LocalGroup(
        browseId = albumPageId(folderId, name),
        title = name,
        subtitle = songs.firstOrNull()?.artist.orEmpty(),
        thumbnailUrl = songs.firstNotNullOfOrNull { it.thumbnailUrl },
        type = BrowseType.ALBUM,
        songs = songs.sortedForAlbum(),
    )

    private fun artist(folderId: String, name: String, songs: List<Song>) = LocalGroup(
        browseId = artistPageId(folderId, name),
        title = name,
        subtitle = "",
        thumbnailUrl = null,
        type = BrowseType.OTHER,
        songs = songs.sortedByTitle(),
    )

    private fun folder(folderId: String, path: String, songs: List<Song>) = LocalGroup(
        browseId = directoryPageId(folderId, path),
        title = path.substringAfterLast('/').ifBlank { path },
        subtitle = path.substringBeforeLast('/', "").readablePath(),
        thumbnailUrl = songs.firstNotNullOfOrNull { it.thumbnailUrl },
        type = BrowseType.PLAYLIST,
        songs = songs.sortedByTitle(),
    )

    /** The directory a device file sits in, or null when its path isn't known. */
    private fun Song.directory(): String? =
        localPath?.replace('\\', '/')?.substringBeforeLast('/', "")?.takeIf { it.isNotEmpty() }

    /**
     * `/storage/emulated/0/Music/Rock` as `Music/Rock`: the volume is the same
     * for nearly everything on a phone and reads as noise. A memory card keeps
     * its name, since that is exactly what tells its folders apart.
     */
    private fun String.readablePath(): String = when {
        startsWith("/storage/emulated/0") -> removePrefix("/storage/emulated/0").trimStart('/')
        startsWith("/storage/") -> removePrefix("/storage/")
        else -> trimStart('/')
    }
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
    LocalMusicSort.TITLE_DESC -> sortedWith(compareByDescending<Song> { it.title.lowercase(Locale.ROOT) })
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
