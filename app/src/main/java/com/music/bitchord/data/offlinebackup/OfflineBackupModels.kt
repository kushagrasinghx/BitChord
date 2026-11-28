package com.music.bitchord.data.offlinebackup

import com.music.bitchord.data.model.Song

/**
 * Database row representing a played track and its optional local offline-backup cache file.
 */
data class OfflineBackupTrack(
    val songId: String,
    val title: String,
    val artist: String,
    val remoteUrl: String?,
    val localCachePath: String?,
    val lastPlayedTimestamp: Long,
    val playCount: Int,
    val fileSizeBytes: Long,
    val thumbnailUrl: String? = null,
    val durationText: String? = null,
    val albumName: String? = null,
) {
    fun toSong(): Song = Song(
        videoId = songId,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        durationText = durationText,
        albumName = albumName,
        localDateAddedSeconds = lastPlayedTimestamp / 1000L,
    )
}

/**
 * Persistence contract for listening history and rolling offline-backup cache metadata.
 */
interface OfflineBackupDao {
    /**
     * Logs a play event for [songId], creating the row if absent or incrementing
     * `play_count` and updating `last_played_timestamp` and metadata if present.
     */
    fun recordPlay(
        songId: String,
        title: String,
        artist: String,
        remoteUrl: String?,
        timestampMs: Long,
        thumbnailUrl: String? = null,
        durationText: String? = null,
        albumName: String? = null,
    )

    /**
     * Associates a completed local cache file with [songId].
     */
    fun markCached(songId: String, localCachePath: String, fileSizeBytes: Long)

    /**
     * Clears `local_cache_path` (sets to null) and resets `file_size_bytes` to 0
     * for evicted or missing cache entries.
     */
    fun clearCachePaths(songIds: Collection<String>)

    /**
     * Returns all tracks that currently have a non-null `local_cache_path`,
     * ordered by `last_played_timestamp DESC, play_count DESC`.
     */
    fun getOfflineBackupTracks(): List<OfflineBackupTrack>

    /**
     * Returns all tracks that currently have a non-null `local_cache_path`,
     * ordered from least-recently-used to most-recently-used
     * (`last_played_timestamp ASC, play_count ASC, song_id ASC`).
     */
    fun getCachedTracksLeastRecentlyUsed(): List<OfflineBackupTrack>

    /**
     * Returns the record for [songId], or null if never played.
     */
    fun getTrack(songId: String): OfflineBackupTrack?
}
