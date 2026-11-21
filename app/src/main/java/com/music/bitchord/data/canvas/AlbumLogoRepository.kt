package com.music.bitchord.data.canvas

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Manages official and user-imported title logos for albums and playlists.
 */
object AlbumLogoRepository {

    private const val LOGO_DIR = "album_logos"
    private val _updates = MutableStateFlow(0)
    val updates: StateFlow<Int> = _updates

    private fun logoDirectory(context: Context): File =
        File(context.filesDir, LOGO_DIR).apply { if (!exists()) mkdirs() }

    private fun sanitizeBrowseId(browseId: String): String =
        browseId.replace(Regex("[^a-zA-Z0-9._-]"), "_")

    fun customLogoFile(context: Context, browseId: String): File? {
        val safeId = sanitizeBrowseId(browseId)
        val file = File(logoDirectory(context), "$safeId.png")
        return file.takeIf { it.exists() && it.length() > 0 }
    }

    fun hasCustomLogo(context: Context, browseId: String): Boolean =
        customLogoFile(context, browseId) != null

    suspend fun saveCustomLogo(context: Context, browseId: String, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = logoDirectory(context)
                val targetFile = File(dir, "${sanitizeBrowseId(browseId)}.png")
                val tmpFile = File(dir, "${sanitizeBrowseId(browseId)}.tmp")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@runCatching false

                if (tmpFile.length() > 0 && tmpFile.renameTo(targetFile)) {
                    _updates.value += 1
                    true
                } else {
                    tmpFile.delete()
                    false
                }
            }.getOrDefault(false)
        }

    suspend fun removeCustomLogo(context: Context, browseId: String): Boolean =
        withContext(Dispatchers.IO) {
            val file = customLogoFile(context, browseId) ?: return@withContext false
            val deleted = file.delete()
            if (deleted) {
                _updates.value += 1
            }
            deleted
        }
}
