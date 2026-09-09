package com.music.bitchord.offline

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object DeviceMusicLibrary {
    private const val MIN_DURATION_MS = 30_000L
    private val extensions = setOf("mp3", "m4a", "flac", "wav", "ogg", "opus", "aac", "webm", "3gp")
    private val excludedPaths = listOf("/alarms/", "/notifications/", "/ringtones/", "/podcasts/", "/audiobooks/", "/recordings/", "/voice recorder/", "/sound_recorder/", "/call_rec/", "/whatsapp voice notes/")

    fun hasPermission(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun scan(context: Context): List<Song> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext emptyList()
        val result = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
        )
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val name = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val data = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val added = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val modified = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val artworkBase = Uri.parse("content://media/external/audio/albumart")
                while (cursor.moveToNext()) {
                    val durationMs = cursor.getLong(duration)
                    val displayName = cursor.getString(name).orEmpty()
                    val path = cursor.getString(data)
                    if (!isMusic(durationMs, displayName, path)) continue
                    val mediaId = cursor.getLong(id)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId)
                    val titleText = cursor.getString(title).cleanTag()?.removeExtension() ?: displayName.substringBeforeLast('.').ifBlank { "Track $mediaId" }
                    val artistText = cursor.getString(artist).cleanTag() ?: "Unknown Artist"
                    val albumText = cursor.getString(album).cleanTag()
                    val albumArtId = cursor.getLong(albumId)
                    result += Song(
                        videoId = uri.toString(),
                        title = titleText,
                        artist = artistText,
                        thumbnailUrl = if (albumArtId > 0L) ContentUris.withAppendedId(artworkBase, albumArtId).toString() else null,
                        durationText = durationMs.toDurationText(),
                        albumName = albumText,
                        localUri = uri.toString(),
                        localPath = path,
                        localDateAddedSeconds = cursor.getLong(added),
                        localDateModifiedSeconds = cursor.getLong(modified),
                    )
                }
            }
        }
        result.distinctBy { it.localUri }
    }

    private fun isMusic(durationMs: Long, displayName: String, path: String?): Boolean {
        if (durationMs < MIN_DURATION_MS) return false
        val filename = path?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: displayName
        if (filename.substringAfterLast('.', "").lowercase(Locale.ROOT) !in extensions) return false
        val normalized = path?.replace('\\', '/')?.lowercase(Locale.ROOT) ?: return true
        return excludedPaths.none(normalized::contains)
    }

    private fun String?.cleanTag(): String? = takeUnless { it.isNullOrBlank() || it == "<unknown>" }

    private fun String.removeExtension(): String {
        val extension = substringAfterLast('.', "").lowercase(Locale.ROOT)
        return if (extension in extensions) substringBeforeLast('.').ifBlank { this } else this
    }

    private fun Long.toDurationText(): String {
        val seconds = coerceAtLeast(0L) / 1000L
        return "%d:%02d".format(seconds / 60L, seconds % 60L)
    }
}
