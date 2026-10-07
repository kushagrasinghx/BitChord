package com.music.bitchord.data.remote

import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.Song

/**
 * What every remote file library names a track: `Artist - Title`, or just
 * the title when there is no artist worth naming — plus the Song row itself,
 * which only differs per library in identity, address and credit.
 */
object RemoteSong {

    fun splitArtistTitle(base: String): Pair<String?, String?> =
        if (" - " in base) {
            val parts = base.split(" - ", limit = 2)
            parts[0].trim().takeIf { it.isNotBlank() } to parts[1].trim().takeIf { it.isNotBlank() }
        } else {
            null to base
        }

    fun build(
        videoId: String,
        streamUrl: String,
        fileName: String,
        albumName: String?,
        source: String,
        browseId: String,
    ): Song {
        val base = fileName.substringBeforeLast('.').takeIf { it.isNotBlank() } ?: fileName
        val (artist, title) = splitArtistTitle(base)
        return Song(
            videoId = videoId,
            title = title ?: base,
            artist = artist ?: "Unknown Artist",
            thumbnailUrl = null,
            durationText = null,
            albumName = albumName?.takeIf { it.isNotBlank() },
            localUri = streamUrl,
            playbackSource = source,
            playbackSourceType = PlaybackSourceType.BROWSE,
            playbackSourceId = browseId,
        )
    }
}
