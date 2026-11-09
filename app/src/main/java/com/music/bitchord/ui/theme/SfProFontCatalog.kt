package com.music.bitchord.ui.theme

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The SF Pro font index at sf-pro.kouzu.in, ported from ArchiveTune's
 * SfProFontCatalog verbatim: same index document, same entries, so a font
 * applied there applies here too.
 */
object SfProFontCatalog {
    private const val INDEX_URL = "https://sf-pro.kouzu.in/fonts.json"

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

    @Serializable
    data class FontIndex(
        val baseUrl: String? = null,
        @SerialName("total_fonts") val totalFonts: Int? = null,
        val families: List<String> = emptyList(),
        val fonts: List<FontEntry> = emptyList(),
    )

    @Serializable
    data class FontEntry(
        val name: String,
        val family: String? = null,
        val style: String? = null,
        val weight: String? = null,
        @SerialName("numeric_weight") val numericWeight: Int? = null,
        val format: String? = null,
        val type: String? = null,
        @SerialName("fileName") val fileName: String? = null,
        val url: String,
    )

    suspend fun fetchCatalog(): List<FontEntry>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request =
                    Request
                        .Builder()
                        .url(INDEX_URL)
                        .header("Accept", "application/json")
                        .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string() ?: return@use null
                    json.decodeFromString<FontIndex>(body).fonts
                }
            }.getOrNull()
        }

    suspend fun downloadFont(entry: FontEntry): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request =
                    Request
                        .Builder()
                        .url(entry.url)
                        .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val bytes = response.body?.bytes() ?: return@use null
                    if (bytes.size < 1024) null else bytes
                }
            }.getOrNull()
        }
}

/**
 * The cached preview copies of the catalog's fonts, ported from ArchiveTune's
 * SfProFontPreview: each row's "AaBbCcDd" line is drawn in the font itself,
 * fetched once into the cache directory and reused from then on.
 */
object SfProFontPreview {
    private const val PREVIEW_DIR = "sf_pro_previews"
    private const val MIN_FONT_BYTES = 1024

    private val inFlight = Mutex()

    private val loadedFamilies = HashMap<String, FontFamily>()

    private val client =
        OkHttpClient
            .Builder()
            .dispatcher(
                Dispatcher().apply {
                    maxRequests = 6
                    maxRequestsPerHost = 3
                },
            ).connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()

    @Immutable
    data class PreviewSpec(
        val fontWeight: FontWeight = FontWeight.Normal,
        val fontStyle: FontStyle = FontStyle.Normal,
    )

    fun previewSpec(entry: SfProFontCatalog.FontEntry): PreviewSpec =
        PreviewSpec(
            fontWeight = FontWeight(entry.numericWeight ?: 400),
            fontStyle = if (entry.style == "italic") FontStyle.Italic else FontStyle.Normal,
        )

    fun previewFile(
        context: Context,
        entry: SfProFontCatalog.FontEntry,
    ): File {
        val dir = File(context.cacheDir, PREVIEW_DIR)
        if (!dir.isDirectory) dir.mkdirs()
        val name = entry.fileName ?: entry.url.substringAfterLast('/')
        return File(dir, name)
    }

    fun isCached(
        context: Context,
        entry: SfProFontCatalog.FontEntry,
    ): Boolean = previewFile(context, entry).length() >= MIN_FONT_BYTES

    suspend fun ensureDownloaded(
        context: Context,
        entry: SfProFontCatalog.FontEntry,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val file = previewFile(context, entry)
            if (file.length() >= MIN_FONT_BYTES) return@withContext true
            inFlight.withLock {
                if (file.length() >= MIN_FONT_BYTES) return@withLock true
                runCatching {
                    val request = Request.Builder().url(entry.url).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use
                        val bytes = response.body?.bytes() ?: return@use
                        if (bytes.size < MIN_FONT_BYTES) return@use
                        val partial = File(file.parentFile, "${file.name}.part")
                        partial.writeBytes(bytes)
                        partial.renameTo(file)
                    }
                }
            }
            file.length() >= MIN_FONT_BYTES
        }

    fun fontFamilyFor(
        context: Context,
        entry: SfProFontCatalog.FontEntry,
    ): FontFamily? {
        val file = previewFile(context, entry)
        if (!file.isFile) return null
        return loadedFamilies.getOrPut(entry.url) {
            val spec = previewSpec(entry)
            FontFamily(
                Font(
                    file = file,
                    weight = spec.fontWeight,
                    style = spec.fontStyle,
                ),
            )
        }
    }
}
