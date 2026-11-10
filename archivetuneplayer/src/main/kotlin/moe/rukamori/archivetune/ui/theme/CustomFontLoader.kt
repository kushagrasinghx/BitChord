/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.theme

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
