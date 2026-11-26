@file:OptIn(ExperimentalJsExport::class)

package com.music.bitchord.web

import com.music.bitchord.data.LocalGroup
import com.music.bitchord.data.LocalGroups
import com.music.bitchord.data.LocalMusicRules
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.useTrackTags

/**
 * One file of the web's Local Music, as much of a `Song` as LocalGroups.kt
 * reads: [path] is the file's place under the chosen folder, led by the
 * folder's own name (`/Music/Rock/song.mp3`), so the Folders tab and the
 * non-music path check read it as they read a phone path.
 */
@JsExport
class LocalRow(
    val title: String,
    val artist: String,
    val album: String?,
    val path: String?,
    val trackNumber: Int?,
    val thumbnailUrl: String?,
)

/**
 * LocalGroups.kt `LocalGroup`: one album, artist or directory, with its songs
 * as positions in the rows it was built from, in the page's own order.
 * [kind] is "album", "artist" or "folder".
 */
@JsExport
class LocalGroupRow(
    val browseId: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val kind: String,
    val songs: Array<Int>,
)

private fun songsOf(rows: Array<LocalRow>): List<Song> = rows.mapIndexed { i, row ->
    Song(
        videoId = i.toString(),
        title = row.title,
        artist = row.artist,
        thumbnailUrl = row.thumbnailUrl,
        albumName = row.album,
        localPath = row.path,
        trackNumber = row.trackNumber,
    )
}

private fun List<LocalGroup>.rows(): Array<LocalGroupRow> = map { it.row() }.toTypedArray()

private fun LocalGroup.row() = LocalGroupRow(
    browseId, title, subtitle, thumbnailUrl, localGroupKind(browseId) ?: "album", songs.map { it.videoId.toInt() }.toTypedArray(),
)

/** LocalGroups.kt `albumsOf` with no downloaded releases: the Albums tab, from the files' album tags. */
@JsExport
fun localAlbums(folderId: String, rows: Array<LocalRow>): Array<LocalGroupRow> =
    LocalGroups.albumsOf(folderId, songsOf(rows), emptyList()).rows()

/** LocalGroups.kt `artistsOf`: the Artists tab. */
@JsExport
fun localArtists(folderId: String, rows: Array<LocalRow>): Array<LocalGroupRow> =
    LocalGroups.artistsOf(folderId, songsOf(rows)).rows()

/** LocalGroups.kt `foldersOf`: the Folders tab. */
@JsExport
fun localFolders(folderId: String, rows: Array<LocalRow>): Array<LocalGroupRow> =
    LocalGroups.foldersOf(folderId, songsOf(rows)).rows()

/** LocalGroups.kt `songsOf` then `groupOf`: the page [browseId] names, rebuilt from the rows; null if it isn't a grouping. */
@JsExport
fun localGroup(browseId: String, rows: Array<LocalRow>): LocalGroupRow? =
    LocalGroups.groupOf(browseId, LocalGroups.songsOf(browseId, songsOf(rows)))?.row()

/** What kind of grouping [browseId] is ("album", "artist", "folder"), or null if it isn't one. */
@JsExport
fun localGroupKind(browseId: String): String? = when {
    LocalGroups.folderOf(browseId) == null -> null
    LocalGroups.isDirectory(browseId) -> "folder"
    LocalGroups.isArtist(browseId) -> "artist"
    else -> "album"
}

@JsExport
fun localAlbumPageId(folderId: String, name: String): String = LocalGroups.albumPageId(folderId, name)

@JsExport
fun localArtistPageId(folderId: String, name: String): String = LocalGroups.artistPageId(folderId, name)

/** LocalGroups.kt `useTrackTags`: whether the rows are one album's, numbered by their tags. */
@JsExport
fun localUseTrackTags(rows: Array<LocalRow>): Boolean = songsOf(rows).useTrackTags()

/** LocalMusicRules.isEligibleLocalMusic, the "Filter non-music audio" setting's rule. 0 is an unknown length. */
@JsExport
fun isEligibleLocalMusic(durationMs: Double, displayName: String, path: String?): Boolean =
    LocalMusicRules.isEligibleLocalMusic(durationMs.toLong(), displayName, path)

/** LocalMusicRules.trackNumberFromTag: "3" or "3/12" as 3; null when it isn't a track number. */
@JsExport
fun localTrackNumber(raw: String?): Int? = LocalMusicRules.trackNumberFromTag(raw)

/** LocalMusicRules.cleanTag: null for a blank tag. */
@JsExport
fun localCleanTag(raw: String?): String? = LocalMusicRules.cleanTag(raw)

/**
 * A track's title as LocalMediaRepository's AudioColumns words it: the title
 * tag without a stray audio extension, else the file name without its own.
 */
@JsExport
fun localTitle(tag: String?, fileName: String): String =
    LocalMusicRules.cleanTag(tag)?.let(LocalMusicRules::removeAudioExtension)
        ?: fileName.substringBeforeLast('.').takeIf { it.isNotBlank() }
        ?: fileName
