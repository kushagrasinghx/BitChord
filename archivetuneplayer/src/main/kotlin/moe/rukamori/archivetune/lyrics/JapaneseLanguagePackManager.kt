/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import android.content.Context
import com.atilika.kuromoji.util.ResourceResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile

sealed interface JapaneseLanguagePackState {
    data object NotInstalled : JapaneseLanguagePackState

    data class Downloading(
        val progressPercent: Int,
    ) : JapaneseLanguagePackState

    data class Installed(
        val sizeBytes: Long,
    ) : JapaneseLanguagePackState

    data class Failed(
        val message: String,
    ) : JapaneseLanguagePackState
}

object JapaneseLanguagePackManager {
    private const val PACK_DIRECTORY = "language_packs"
    private const val PACK_FILE = "kuromoji-ipadic-0.9.0.jar"
    private const val RESOURCE_PREFIX = "com/atilika/kuromoji/ipadic/"
    private const val DOWNLOAD_URL =
        "https://repo.maven.apache.org/maven2/com/atilika/kuromoji/kuromoji-ipadic/0.9.0/kuromoji-ipadic-0.9.0.jar"
    private const val EXPECTED_SHA256 =
        "24909fd751c0b439f7af5131b080eb65bc85062f0c4977ca4a71c76abe74e0b6"

    private val operationMutex = Mutex()
    private val tokenizerLock = Any()
    private val mutableState = MutableStateFlow<JapaneseLanguagePackState>(JapaneseLanguagePackState.NotInstalled)

    val state: StateFlow<JapaneseLanguagePackState> = mutableState.asStateFlow()

    @Volatile
    private var applicationContext: Context? = null

    @Volatile
    private var cachedTokenizer: DownloadedJapaneseTokenizer? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        val pack = packFile(context)
        mutableState.value =
            if (pack.isFile) {
                JapaneseLanguagePackState.Installed(pack.length())
            } else {
                JapaneseLanguagePackState.NotInstalled
            }
    }

    suspend fun install(): Result<Unit> =
        operationMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    val context = requireNotNull(applicationContext) { "Language pack manager is not initialized" }
                    val target = packFile(context)
                    target.parentFile?.mkdirs()
                    val partial = File(target.parentFile, "$PACK_FILE.partial")
                    partial.delete()

                    try {
                        downloadPack(partial)
                        check(sha256(partial).equals(EXPECTED_SHA256, ignoreCase = true)) {
                            "Downloaded language pack failed integrity verification"
                        }
                        check(partial.renameTo(target)) { "Could not install the downloaded language pack" }
                        synchronized(tokenizerLock) { cachedTokenizer = null }
                        mutableState.value = JapaneseLanguagePackState.Installed(target.length())
                    } catch (error: Throwable) {
                        partial.delete()
                        mutableState.value =
                            JapaneseLanguagePackState.Failed(
                                error.message ?: "Language pack download failed",
                            )
                        throw error
                    }
                }
            }
        }

    suspend fun remove(): Boolean =
        operationMutex.withLock {
            withContext(Dispatchers.IO) {
                val context = applicationContext ?: return@withContext false
                synchronized(tokenizerLock) { cachedTokenizer = null }
                val pack = packFile(context)
                val removed = !pack.exists() || pack.delete()
                if (removed) mutableState.value = JapaneseLanguagePackState.NotInstalled
                removed
            }
        }

    fun tokenizerOrNull(): DownloadedJapaneseTokenizer? {
        cachedTokenizer?.let { return it }
        val context = applicationContext ?: return null
        val pack = packFile(context).takeIf(File::isFile) ?: return null

        return synchronized(tokenizerLock) {
            cachedTokenizer
                ?: runCatching {
                    ZipFile(pack).use { archive ->
                        val resolver =
                            ResourceResolver { resourceName ->
                                val entry =
                                    archive.getEntry(RESOURCE_PREFIX + resourceName)
                                        ?: throw IOException("Missing Japanese dictionary resource: $resourceName")
                                archive.getInputStream(entry)
                            }
                        DownloadedJapaneseTokenizer.create(resolver)
                    }
                }.onFailure {
                    pack.delete()
                    mutableState.value = JapaneseLanguagePackState.Failed("Installed language pack is invalid")
                }.getOrNull()
                ?.also { cachedTokenizer = it }
        }
    }

    private fun packFile(context: Context): File = File(File(context.filesDir, PACK_DIRECTORY), PACK_FILE)

    private fun downloadPack(destination: File) {
        val connection =
            (URL(DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
        try {
            check(connection.responseCode in 200..299) {
                "Language pack server returned HTTP ${connection.responseCode}"
            }
            val total = connection.contentLengthLong
            var downloaded = 0L
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(destination).buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        if (total > 0) {
                            mutableState.value =
                                JapaneseLanguagePackState.Downloading(
                                    ((downloaded * 100L) / total).toInt().coerceIn(0, 100),
                                )
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }
}
