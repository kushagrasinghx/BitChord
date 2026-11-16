package com.music.bitchord.download

import android.content.Context
import com.music.bitchord.BuildConfig
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileInputStream
import java.util.Properties

/**
 * Debug-only deterministic transfer used by the process-death integration test.
 *
 * The fixture is a properties file in the app's own files directory, so it
 * survives the process kill along with the persisted download journal. Release
 * builds never take this path; a missing or malformed fixture is a normal
 * download.
 */
internal object DownloadTestRoute {

    private const val FIXTURE = "download-test-fixture.properties"
    private const val CHUNK_BYTES = 8 * 1024

    fun routeFor(context: Context, song: com.music.bitchord.data.model.Song): Downloads.Route? {
        if (!BuildConfig.DEBUG) return null
        val configFile = File(context.filesDir, FIXTURE)
        if (!configFile.isFile) return null
        val config = runCatching {
            Properties().apply {
                configFile.inputStream().use { load(it) }
            }
        }.getOrNull() ?: return null
        if (config.getProperty("videoId") != song.videoId) return null
        val source = config.getProperty("path")?.let(::File)?.canonicalFile ?: return null
        val filesRoot = context.filesDir.canonicalFile
        if (!source.path.startsWith(filesRoot.path + File.separator) || !source.isFile) return null
        val extension = config.getProperty("extension") ?: return null
        val mimeType = config.getProperty("mimeType") ?: return null
        val chunkDelayMs = config.getProperty("chunkDelayMs")?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val total = source.length()
        if (total <= 0L) return null
        return Downloads.Route(
            extension = extension,
            mimeType = mimeType,
            describe = "deterministic fixture, $total bytes",
            taggable = false,
            write = { sink, onProgress ->
                FileInputStream(source).use { input ->
                    val buffer = ByteArray(CHUNK_BYTES)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        written += read
                        onProgress(written, total)
                        if (chunkDelayMs > 0L) delay(chunkDelayMs)
                    }
                }
                sink.flush()
            },
        )
    }
}
