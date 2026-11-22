package com.music.bitchord.data.telegram

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.Locale

internal data class TelegramTrack(
    val id: String,
    val chatId: Long,
    val messageId: Long,
    val fileId: Int,
    val fileUniqueId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val fileName: String,
    val mimeType: String,
    val durationSeconds: Int,
    val size: Long,
    val codec: String?,
    val date: Int,
)

class TelegramIndexDatabase(context: Context) : SQLiteOpenHelper(
    context,
    File(context.noBackupFilesDir, "telegram/library.db").also { it.parentFile?.mkdirs() }.absolutePath,
    null,
    1,
) {
    data class Cursor(val nextMessageId: Long, val built: Boolean, val newestMessageId: Long)
    data class PageResult(val messages: Int, val complete: Boolean)

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE tracks (
                id TEXT PRIMARY KEY NOT NULL,
                chat_id INTEGER NOT NULL,
                message_id INTEGER NOT NULL,
                file_id INTEGER NOT NULL,
                file_unique_id TEXT NOT NULL,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT,
                file_name TEXT NOT NULL,
                mime_type TEXT NOT NULL,
                duration INTEGER NOT NULL,
                size INTEGER NOT NULL,
                codec TEXT,
                message_date INTEGER NOT NULL,
                UNIQUE(chat_id, message_id)
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX tracks_chat_id ON tracks(chat_id)")
        db.execSQL("CREATE INDEX tracks_file_unique_id ON tracks(file_unique_id)")
        db.execSQL(
            "CREATE VIRTUAL TABLE tracks_fts USING fts4(id, title, artist, album, file_name, tokenize=unicode61)",
        )
        db.execSQL(
            "CREATE TABLE index_state (chat_id INTEGER PRIMARY KEY NOT NULL, cursor INTEGER NOT NULL DEFAULT 0, built INTEGER NOT NULL DEFAULT 0, newest_message_id INTEGER NOT NULL DEFAULT 0, last_indexed_at INTEGER NOT NULL DEFAULT 0)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    internal fun cursor(chatId: Long): Cursor = readableDatabase.rawQuery(
        "SELECT cursor,built,newest_message_id FROM index_state WHERE chat_id=?",
        arrayOf(chatId.toString()),
    ).use { c ->
        if (!c.moveToFirst()) Cursor(0L, false, 0L) else Cursor(c.getLong(0), c.getInt(1) != 0, c.getLong(2))
    }

    @Synchronized
    internal fun updateCursor(chatId: Long, cursor: Long, built: Boolean, newestMessageId: Long) {
        val values = ContentValues().apply {
            put("chat_id", chatId)
            put("cursor", cursor)
            put("built", if (built) 1 else 0)
            put("newest_message_id", newestMessageId)
            put("last_indexed_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("index_state", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    internal fun storeMessages(chatId: Long, messages: Array<out TdApi.Message>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            messages.forEach { message -> storeMessage(db, chatId, message) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    internal fun storeMessage(chatId: Long, message: TdApi.Message) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            storeMessage(db, chatId, message)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun storeMessage(db: SQLiteDatabase, chatId: Long, message: TdApi.Message) {
        val metadata = message.telegramAudio() ?: return
        val trackId = "$chatId:${message.id}"
        db.delete("tracks_fts", "id=?", arrayOf(trackId))
        val values = ContentValues().apply {
            put("id", trackId)
            put("chat_id", chatId)
            put("message_id", message.id)
            put("file_id", metadata.file.id)
            put("file_unique_id", metadata.file.remote?.uniqueId.orEmpty())
            put("title", metadata.title.ifBlank { metadata.fileName.substringBeforeLast('.', metadata.fileName) })
            put("artist", metadata.artist)
            put("album", metadata.album)
            put("file_name", metadata.fileName)
            put("mime_type", metadata.mimeType)
            put("duration", metadata.duration)
            put("size", metadata.file.size)
            put("codec", codecOf(metadata.fileName, metadata.mimeType))
            put("message_date", message.date)
        }
        db.insertWithOnConflict("tracks", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        val fts = ContentValues().apply {
            put("id", trackId)
            put("title", values.getAsString("title"))
            put("artist", metadata.artist)
            put("album", metadata.album.orEmpty())
            put("file_name", metadata.fileName)
        }
        db.insert("tracks_fts", null, fts)
        val state = cursor(chatId)
        updateCursor(chatId, state.nextMessageId, state.built, maxOf(state.newestMessageId, message.id))
    }

    @Synchronized
    internal fun search(query: String, limit: Int, chatIds: Set<Long>): List<TelegramTrack> {
        if (chatIds.isEmpty()) return emptyList()
        val terms = query.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 1 }
            .distinct()
            .take(8)
        if (terms.isEmpty()) return emptyList()
        val match = terms.joinToString(" AND ") { "\"${it.replace("\"", "")}" + "*\"" }
        val rows = mutableListOf<TelegramTrack>()
        readableDatabase.rawQuery(
            """SELECT t.id,t.chat_id,t.message_id,t.file_id,t.file_unique_id,t.title,t.artist,t.album,t.file_name,t.mime_type,t.duration,t.size,t.codec,t.message_date
                FROM tracks_fts JOIN tracks t ON t.id=tracks_fts.id WHERE tracks_fts MATCH ?
                ORDER BY t.message_date DESC LIMIT ?""".trimIndent(),
            arrayOf(match, (limit * 3).toString()),
        ).use { c -> while (c.moveToNext()) rows += c.track() }
        // Same upload in several accessible chats is one result. The most recent
        // Telegram post wins, while each message remains addressable in the DB.
        return rows.filter { it.chatId in chatIds }.distinctBy { track ->
            val unique = track.fileUniqueId.takeIf { it.isNotBlank() }
            unique ?: "${track.title.lowercase(Locale.ROOT)}|${track.artist.lowercase(Locale.ROOT)}|${track.size}"
        }.take(limit)
    }

    @Synchronized
    internal fun track(id: String): TelegramTrack? = readableDatabase.rawQuery(
        "SELECT id,chat_id,message_id,file_id,file_unique_id,title,artist,album,file_name,mime_type,duration,size,codec,message_date FROM tracks WHERE id=?",
        arrayOf(id),
    ).use { if (it.moveToFirst()) it.track() else null }

    @Synchronized
    internal fun progress(chatId: Long): Pair<Int, Boolean> {
        val count = readableDatabase.rawQuery("SELECT COUNT(*) FROM tracks WHERE chat_id=?", arrayOf(chatId.toString())).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        return count to cursor(chatId).built
    }

    @Synchronized
    internal fun deleteMessages(chatId: Long, messageIds: LongArray) {
        writableDatabase.beginTransaction()
        try {
            messageIds.forEach { messageId ->
                val id = "$chatId:$messageId"
                writableDatabase.delete("tracks", "id=?", arrayOf(id))
                writableDatabase.delete("tracks_fts", "id=?", arrayOf(id))
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    @Synchronized
    internal fun clear() {
        writableDatabase.delete("tracks", null, null)
        writableDatabase.delete("tracks_fts", null, null)
        writableDatabase.delete("index_state", null, null)
    }

    @Synchronized
    internal fun resetIndex(chatIds: Set<Long>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            chatIds.forEach { id ->
                db.delete("index_state", "chat_id=?", arrayOf(id.toString()))
                db.delete("tracks", "chat_id=?", arrayOf(id.toString()))
                db.delete("tracks_fts", "id NOT IN (SELECT id FROM tracks)", null)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    internal fun removeChats(chatIds: Set<Long>) {
        if (chatIds.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            chatIds.forEach { id ->
                db.delete("tracks", "chat_id=?", arrayOf(id.toString()))
                db.delete("index_state", "chat_id=?", arrayOf(id.toString()))
            }
            db.delete("tracks_fts", "id NOT IN (SELECT id FROM tracks)", null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    internal fun countTracks(chatIds: Set<Long>): Int {
        if (chatIds.isEmpty()) return 0
        val placeholders = chatIds.joinToString(",") { "?" }
        return readableDatabase.rawQuery("SELECT COUNT(*) FROM tracks WHERE chat_id IN ($placeholders)", chatIds.map(Long::toString).toTypedArray()).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    private fun android.database.Cursor.track() = TelegramTrack(
        id = getString(0),
        chatId = getLong(1),
        messageId = getLong(2),
        fileId = getInt(3),
        fileUniqueId = getString(4).orEmpty(),
        title = getString(5),
        artist = getString(6),
        album = getString(7),
        fileName = getString(8),
        mimeType = getString(9),
        durationSeconds = getInt(10),
        size = getLong(11),
        codec = getString(12),
        date = getInt(13),
    )

    private data class AudioMetadata(
        val file: TdApi.File,
        val title: String,
        val artist: String,
        val album: String?,
        val fileName: String,
        val mimeType: String,
        val duration: Int,
    )

    private fun TdApi.Message.telegramAudio(): AudioMetadata? {
        return (when (val content = content) {
            is TdApi.MessageAudio -> content.audio?.let { audio ->
                val fallback = parseName(audio.fileName)
                AudioMetadata(audio.audio, audio.title.ifBlank { fallback.second }, audio.performer.ifBlank { fallback.first }, null, audio.fileName, audio.mimeType, audio.duration)
            }
            is TdApi.MessageDocument -> content.document?.let { document ->
                val extension = document.fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                val looksAudio = document.mimeType.startsWith("audio/", ignoreCase = true) || extension in AUDIO_EXTENSIONS
                if (!looksAudio) return@let null
                val fallback = parseName(document.fileName)
                AudioMetadata(
                    document.document,
                    fallback.second,
                    fallback.first,
                    null,
                    document.fileName,
                    document.mimeType,
                    0,
                )
            }
            else -> null
        })?.takeIf { metadata -> metadata.file.id != 0 && metadata.fileName.isNotBlank() }
    }

    private fun parseName(fileName: String): Pair<String, String> {
        val stem = fileName.substringBeforeLast('.', fileName)
            .replace(Regex("^\\s*\\d{1,3}[ ._-]+"), "")
            .trim()
        val parts = stem.split(Regex("\\s+[-–—]\\s+"), limit = 2)
        return if (parts.size == 2) parts[0].trim() to parts[1].trim() else "Unknown Artist" to stem.ifBlank { "Unknown title" }
    }

    private fun codecOf(fileName: String, mime: String): String? {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when {
            ext in setOf("flac", "wav", "aiff", "aif", "alac", "m4a", "mp3", "aac", "opus", "ogg", "oga", "ec3") -> if (ext == "aif") "aiff" else ext
            mime.equals("audio/flac", true) -> "flac"
            mime.equals("audio/mpeg", true) -> "mp3"
            mime.equals("audio/ogg", true) -> "ogg"
            mime.equals("audio/aac", true) -> "aac"
            mime.equals("audio/mp4", true) -> "m4a"
            else -> null
        }
    }

    companion object {
        private val AUDIO_EXTENSIONS = setOf("flac", "wav", "aiff", "aif", "alac", "m4a", "mp3", "aac", "opus", "ogg", "oga", "ec3")
        private fun parseUnused() = Unit
    }
}
