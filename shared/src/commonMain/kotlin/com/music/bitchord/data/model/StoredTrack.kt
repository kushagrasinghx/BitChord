package com.music.bitchord.data.model

import kotlinx.serialization.Serializable

/**
 * Lightweight, serializable snapshot of a [Song] kept on device.
 *
 * Shared by the queue snapshot in [com.music.bitchord.playback.LastPlayed] and
 * local listening history, so both persist the same fields and neither loses
 * queue metadata on a restore.
 */
@Serializable
data class StoredTrack(
    val id: String,
    val title: String,
    val artist: String,
    val artwork: String? = null,
    val auto: Boolean = false,
    val tier: String? = null,
    val entryId: String? = null,
    val local: String? = null,
    val path: String? = null,
    val duration: String? = null,
    val artistId: String? = null,
    val albumId: String? = null,
    val album: String? = null,
    val explicit: Boolean? = null,
    val video: Boolean = false,
    val radio: String? = null,
    val source: String? = null,
    val sourceType: String? = null,
    val sourceId: String? = null,
) {
    fun toSong(): Song {
        val resolvedTier = when (tier) {
            "USER_QUEUE" -> QueueTier.USER_QUEUE
            "CONTEXT" -> QueueTier.CONTEXT
            "AUTOPLAY" -> QueueTier.AUTOPLAY
            else -> if (auto) QueueTier.AUTOPLAY else QueueTier.CONTEXT
        }
        return Song(
            videoId = id,
            title = title,
            artist = artist,
            thumbnailUrl = artwork,
            durationText = duration,
            artistId = artistId,
            albumId = albumId,
            albumName = album,
            isExplicit = explicit,
            isVideo = video,
            queueTier = resolvedTier,
            queueEntryId = entryId,
            radioName = radio,
            playbackSource = source,
            playbackSourceType = sourceType?.let {
                runCatching { PlaybackSourceType.valueOf(it) }.getOrNull()
            },
            playbackSourceId = sourceId,
            localUri = local,
            localPath = path,
        )
    }

    companion object {
        fun from(song: Song) = StoredTrack(
            id = song.videoId,
            title = song.title,
            artist = song.artist,
            artwork = song.thumbnailUrl,
            auto = song.fromAutoplay,
            tier = song.queueTier.name,
            entryId = song.queueEntryId,
            local = song.localUri,
            path = song.localPath,
            duration = song.durationText,
            artistId = song.artistId,
            albumId = song.albumId,
            album = song.albumName,
            explicit = song.isExplicit,
            video = song.isVideo,
            radio = song.radioName,
            source = song.playbackSource,
            sourceType = song.playbackSourceType?.name,
            sourceId = song.playbackSourceId,
        )
    }
}
