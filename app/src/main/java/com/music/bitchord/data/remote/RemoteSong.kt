package com.music.bitchord.data.remote

import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.Song

/**
 * What every remote file library names a track, read from where the file
 * sits rather than from tags a listing never touches. `Artist/Album
 * (Year)/NN - Title.ext` is the layout nearly every ripped or bought
 * library keeps: the folder above the album names the artist, the album
 * folder drops its year, the file drops its track number, and a file
 * still named `Artist - Title` names both itself — a featured credit on
 * one track beats the folder.
 */
object RemoteSong {

    data class Credit(val title: String, val artist: String?, val album: String?)

    fun splitArtistTitle(base: String): Pair<String?, String?> =
        if (" - " in base) {
            val parts = base.split(" - ", limit = 2)
            parts[0].trim().takeIf { it.isNotBlank() } to parts[1].trim().takeIf { it.isNotBlank() }
        } else {
            null to base
        }

    /** [relativePath] runs from the library root: forward slashes, decoded. */
    fun credit(relativePath: String): Credit {
        val segments = relativePath.split('/').filter { it.isNotBlank() }
        val fileName = segments.lastOrNull().orEmpty()
        val folders = segments.dropLast(1).dropLastWhile { discFolder.matches(it.trim()) }
        val album = folders.lastOrNull()
            ?.let { yearSuffix.replace(it, "").trim() }
            ?.takeIf { it.isNotBlank() }
        val folderArtist = folders.dropLast(1).lastOrNull()?.trim()?.takeIf { it.isNotBlank() }
        val base = fileName.substringBeforeLast('.').takeIf { it.isNotBlank() } ?: fileName
        val unnumbered = stripTrackNumber(base, inAlbum = album != null)
        val (fileArtist, split) = splitArtistTitle(unnumbered)
        val credited = fileArtist != null &&
            (folderArtist == null || fileArtist.startsWith(folderArtist, ignoreCase = true))
        return Credit(
            title = (if (credited) split else null) ?: unnumbered.ifBlank { base },
            artist = if (credited) fileArtist else folderArtist,
            album = album,
        )
    }

    private val discFolder = Regex("""(?i)^(disc|disk|cd)\s*\d+$""")
    private val yearSuffix = Regex("""\s*[(\[](19|20)\d{2}[)\]]\s*$""")
    private val trackPrefix = Regex("""^(\d{1,2}[-.])?\d{1,3}\s*[-._]\s*(?=\S)""")

    // "14 Strawberry's Wake" only reads as numbered inside an album folder;
    // loose, "21 Guns" is a title.
    private val looseTrackPrefix = Regex("""^\d{2}\s+(?=\S)""")

    private fun stripTrackNumber(base: String, inAlbum: Boolean): String {
        val strict = trackPrefix.replaceFirst(base, "")
        if (strict != base) return strict
        return if (inAlbum) looseTrackPrefix.replaceFirst(base, "") else base
    }

    fun build(
        videoId: String,
        streamUrl: String,
        credit: Credit,
        source: String,
        browseId: String,
    ): Song = Song(
        videoId = videoId,
        title = credit.title,
        artist = credit.artist ?: "Unknown Artist",
        thumbnailUrl = null,
        durationText = null,
        albumName = credit.album,
        localUri = streamUrl,
        playbackSource = source,
        playbackSourceType = PlaybackSourceType.BROWSE,
        playbackSourceId = browseId,
    )
}
