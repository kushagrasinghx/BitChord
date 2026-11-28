package com.music.bitchord.data.offlinebackup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite-backed store for [OfflineBackupTrack] records.
 *
 * Tracks played songs (`song_id`, `title`, `artist`, `remote_url`, `local_cache_path`,
 * `last_played_timestamp`, `play_count`, `file_size_bytes`) so the rolling LRU cache
 * and dynamic "Offline Backup" playlist survive process restarts.
 */
class OfflineBackupDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION),
    OfflineBackupDao {

    private val dbLock = Any()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_TRACKS (
                $COL_SONG_ID TEXT PRIMARY KEY,
                $COL_TITLE TEXT NOT NULL,
                $COL_ARTIST TEXT NOT NULL,
                $COL_REMOTE_URL TEXT,
                $COL_LOCAL_CACHE_PATH TEXT,
                $COL_LAST_PLAYED_TIMESTAMP INTEGER NOT NULL,
                $COL_PLAY_COUNT INTEGER NOT NULL DEFAULT 0,
                $COL_FILE_SIZE_BYTES INTEGER NOT NULL DEFAULT 0,
                $COL_THUMBNAIL_URL TEXT,
                $COL_DURATION_TEXT TEXT,
                $COL_ALBUM_NAME TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_offline_backup_lru ON $TABLE_TRACKS ($COL_LAST_PLAYED_TIMESTAMP, $COL_PLAY_COUNT)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_offline_backup_path ON $TABLE_TRACKS ($COL_LOCAL_CACHE_PATH)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Initial schema version 1.
    }

    override fun recordPlay(
        songId: String,
        title: String,
        artist: String,
        remoteUrl: String?,
        timestampMs: Long,
        thumbnailUrl: String?,
        durationText: String?,
        albumName: String?,
    ) {
        if (songId.isBlank()) return
        synchronized(dbLock) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                val existing = getTrackLocked(db, songId)
                if (existing == null) {
                    val values = ContentValues().apply {
                        put(COL_SONG_ID, songId)
                        put(COL_TITLE, title)
                        put(COL_ARTIST, artist)
                        put(COL_REMOTE_URL, remoteUrl)
                        putNull(COL_LOCAL_CACHE_PATH)
                        put(COL_LAST_PLAYED_TIMESTAMP, timestampMs)
                        put(COL_PLAY_COUNT, 1)
                        put(COL_FILE_SIZE_BYTES, 0L)
                        put(COL_THUMBNAIL_URL, thumbnailUrl)
                        put(COL_DURATION_TEXT, durationText)
                        put(COL_ALBUM_NAME, albumName)
                    }
                    db.insertWithOnConflict(TABLE_TRACKS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                } else {
                    val values = ContentValues().apply {
                        put(COL_TITLE, title.ifBlank { existing.title })
                        put(COL_ARTIST, artist.ifBlank { existing.artist })
                        if (!remoteUrl.isNullOrBlank()) put(COL_REMOTE_URL, remoteUrl)
                        put(COL_LAST_PLAYED_TIMESTAMP, maxOf(existing.lastPlayedTimestamp, timestampMs))
                        put(COL_PLAY_COUNT, existing.playCount + 1)
                        if (!thumbnailUrl.isNullOrBlank()) put(COL_THUMBNAIL_URL, thumbnailUrl)
                        if (!durationText.isNullOrBlank()) put(COL_DURATION_TEXT, durationText)
                        if (!albumName.isNullOrBlank()) put(COL_ALBUM_NAME, albumName)
                    }
                    db.update(TABLE_TRACKS, values, "$COL_SONG_ID = ?", arrayOf(songId))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    override fun markCached(songId: String, localCachePath: String, fileSizeBytes: Long) {
        if (songId.isBlank() || localCachePath.isBlank()) return
        synchronized(dbLock) {
            val values = ContentValues().apply {
                put(COL_LOCAL_CACHE_PATH, localCachePath)
                put(COL_FILE_SIZE_BYTES, fileSizeBytes.coerceAtLeast(0L))
            }
            writableDatabase.update(TABLE_TRACKS, values, "$COL_SONG_ID = ?", arrayOf(songId))
        }
    }

    override fun clearCachePaths(songIds: Collection<String>) {
        if (songIds.isEmpty()) return
        synchronized(dbLock) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                val values = ContentValues().apply {
                    putNull(COL_LOCAL_CACHE_PATH)
                    put(COL_FILE_SIZE_BYTES, 0L)
                }
                for (songId in songIds) {
                    db.update(TABLE_TRACKS, values, "$COL_SONG_ID = ?", arrayOf(songId))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    override fun getOfflineBackupTracks(): List<OfflineBackupTrack> = synchronized(dbLock) {
        queryTracks(
            selection = "$COL_LOCAL_CACHE_PATH IS NOT NULL AND $COL_LOCAL_CACHE_PATH != ''",
            orderBy = "$COL_LAST_PLAYED_TIMESTAMP DESC, $COL_PLAY_COUNT DESC",
        )
    }

    override fun getCachedTracksLeastRecentlyUsed(): List<OfflineBackupTrack> = synchronized(dbLock) {
        queryTracks(
            selection = "$COL_LOCAL_CACHE_PATH IS NOT NULL AND $COL_LOCAL_CACHE_PATH != ''",
            orderBy = "$COL_LAST_PLAYED_TIMESTAMP ASC, $COL_PLAY_COUNT ASC, $COL_SONG_ID ASC",
        )
    }

    override fun getTrack(songId: String): OfflineBackupTrack? = synchronized(dbLock) {
        getTrackLocked(readableDatabase, songId)
    }

    private fun getTrackLocked(db: SQLiteDatabase, songId: String): OfflineBackupTrack? {
        db.query(
            TABLE_TRACKS,
            ALL_COLUMNS,
            "$COL_SONG_ID = ?",
            arrayOf(songId),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toTrack() else null
        }
    }

    private fun queryTracks(selection: String?, orderBy: String?): List<OfflineBackupTrack> {
        val results = mutableListOf<OfflineBackupTrack>()
        readableDatabase.query(
            TABLE_TRACKS,
            ALL_COLUMNS,
            selection,
            null,
            null,
            null,
            orderBy,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results += cursor.toTrack()
            }
        }
        return results
    }

    private fun Cursor.toTrack(): OfflineBackupTrack = OfflineBackupTrack(
        songId = getString(getColumnIndexOrThrow(COL_SONG_ID)),
        title = getString(getColumnIndexOrThrow(COL_TITLE)),
        artist = getString(getColumnIndexOrThrow(COL_ARTIST)),
        remoteUrl = getStringOrNull(COL_REMOTE_URL),
        localCachePath = getStringOrNull(COL_LOCAL_CACHE_PATH),
        lastPlayedTimestamp = getLong(getColumnIndexOrThrow(COL_LAST_PLAYED_TIMESTAMP)),
        playCount = getInt(getColumnIndexOrThrow(COL_PLAY_COUNT)),
        fileSizeBytes = getLong(getColumnIndexOrThrow(COL_FILE_SIZE_BYTES)),
        thumbnailUrl = getStringOrNull(COL_THUMBNAIL_URL),
        durationText = getStringOrNull(COL_DURATION_TEXT),
        albumName = getStringOrNull(COL_ALBUM_NAME),
    )

    private fun Cursor.getStringOrNull(columnName: String): String? {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) null else getString(index)
    }

    companion object {
        const val DATABASE_NAME = "offline_backup.db"
        const val DATABASE_VERSION = 1
        const val TABLE_TRACKS = "offline_backup_tracks"

        const val COL_SONG_ID = "song_id"
        const val COL_TITLE = "title"
        const val COL_ARTIST = "artist"
        const val COL_REMOTE_URL = "remote_url"
        const val COL_LOCAL_CACHE_PATH = "local_cache_path"
        const val COL_LAST_PLAYED_TIMESTAMP = "last_played_timestamp"
        const val COL_PLAY_COUNT = "play_count"
        const val COL_FILE_SIZE_BYTES = "file_size_bytes"
        const val COL_THUMBNAIL_URL = "thumbnail_url"
        const val COL_DURATION_TEXT = "duration_text"
        const val COL_ALBUM_NAME = "album_name"

        private val ALL_COLUMNS = arrayOf(
            COL_SONG_ID,
            COL_TITLE,
            COL_ARTIST,
            COL_REMOTE_URL,
            COL_LOCAL_CACHE_PATH,
            COL_LAST_PLAYED_TIMESTAMP,
            COL_PLAY_COUNT,
            COL_FILE_SIZE_BYTES,
            COL_THUMBNAIL_URL,
            COL_DURATION_TEXT,
            COL_ALBUM_NAME,
        )
    }
}
