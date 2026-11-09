package com.music.bitchord.ui.theme

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.graphics.Typeface as AndroidTypeface

/**
 * Loads a font file the user picked, ported from ArchiveTune's CustomFontLoader.
 *
 * A picked document is copied into the app's own storage on first load — the
 * SAF grant dies with the picker's task, so the copy is the only thing that
 * survives — and the copy is what every later launch reads.
 */
object CustomFontLoader {
    val supportedMimeTypes =
        arrayOf(
            "font/ttf",
            "application/x-font-ttf",
            "application/x-font-truetype",
            "font/otf",
            "application/x-font-opentype",
            "application/vnd.ms-opentype",
            "application/octet-stream",
        )

    fun displayName(
        context: Context,
        uri: Uri,
    ): String {
        val resolvedName =
            runCatching {
                context.contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                    }
            }.getOrNull()

        return resolvedName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringBefore('?')
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            ?: uri.toString()
    }

    fun isSupportedTtf(
        context: Context,
        uri: Uri,
    ): Boolean {
        val name = displayName(context, uri)
        return name.endsWith(".ttf", ignoreCase = true) || name.endsWith(".otf", ignoreCase = true)
    }

    /**
     * Writes bytes fetched by the SF Pro browser into the app's storage and
     * hands back the uri they can be re-loaded from.
     */
    suspend fun applyDownloadedFont(
        context: Context,
        bytes: ByteArray,
        fileName: String,
    ): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val fontDirectory = File(context.filesDir, "custom_fonts")
                if (!fontDirectory.exists()) fontDirectory.mkdirs()
                val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val fontFile = File(fontDirectory, safeName)
                fontFile.writeBytes(bytes)
                Uri.fromFile(fontFile).toString()
            }.getOrNull()
        }

    /**
     * The picker's half: copies the picked document into the app's own storage
     * at once — the SAF grant dies with the picker's task — and hands back the
     * uri every later launch loads from, with the document's own name. Null
     * where the document isn't a font this device can read.
     */
    suspend fun persistPickedFont(
        context: Context,
        uri: Uri,
    ): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val name = displayName(context, uri)
                if (!isSupportedTtf(context, uri)) return@runCatching null
                val fontDirectory = File(context.filesDir, "custom_fonts")
                if (!fontDirectory.exists()) fontDirectory.mkdirs()
                val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val fontFile = File(fontDirectory, safeName)
                context.contentResolver.openInputStream(uri).use { inputStream ->
                    requireNotNull(inputStream)
                    fontFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                // Read once here, so a file that isn't a font is refused at the
                // pick rather than on the next launch, in place of the app's type.
                AndroidTypeface.createFromFile(fontFile)
                name to Uri.fromFile(fontFile).toString()
            }.getOrNull()
        }

    suspend fun loadFontFamily(
        context: Context,
        uriString: String,
    ): FontFamily? =
        withContext(Dispatchers.IO) {
            if (uriString.isBlank()) return@withContext null

            try {
                val uri = Uri.parse(uriString)
                if (!isSupportedTtf(context, uri)) return@withContext null

                val fontFile = copyToPrivateFontFile(context, uri, uriString)
                val androidTypeface = AndroidTypeface.createFromFile(fontFile)
                FontFamily(Typeface(androidTypeface))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }

    private fun copyToPrivateFontFile(
        context: Context,
        uri: Uri,
        uriString: String,
    ): File {
        val fontDirectory = File(context.filesDir, "custom_fonts")
        if (!fontDirectory.exists()) fontDirectory.mkdirs()

        val fontFile = File(fontDirectory, "${Integer.toHexString(uriString.hashCode())}.ttf")
        context.contentResolver.openInputStream(uri).use { inputStream ->
            requireNotNull(inputStream)
            fontFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return fontFile
    }
}
