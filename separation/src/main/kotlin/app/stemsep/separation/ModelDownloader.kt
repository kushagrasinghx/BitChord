package app.stemsep.separation

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class ModelDownloader(private val context: Context) {
    private val modelsDir = File(context.filesDir, "models")

    suspend fun ensureModel(
        spec: ModelSpec,
        onProgress: (Float) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        modelsDir.mkdirs()
        val target = File(modelsDir, "${spec.id}.onnx")
        if (target.isFile && isSatisfied(target, spec)) {
            onProgress(1f)
            return@withContext target
        }
        target.delete()

        val urls = listOfNotNull(spec.url, spec.fallbackUrl)
        var lastError: Exception? = null
        for (url in urls) {
            try {
                download(url, spec, target, onProgress)
                return@withContext target
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                lastError = error
                target.delete()
            }
        }
        throw IOException("Could not download ${spec.displayName} model", lastError)
    }

    fun isCached(spec: ModelSpec): Boolean {
        val target = File(modelsDir, "${spec.id}.onnx")
        return target.isFile && isSatisfied(target, spec)
    }

    /** The checksum on record for [spec]: pinned, sidecar-computed, or null. */
    fun recordedSha256(spec: ModelSpec): String? {
        val target = File(modelsDir, "${spec.id}.onnx")
        if (!target.isFile) return null
        return if (spec.sha256 != null) spec.sha256 else sidecar(target)?.readText()?.trim()
    }

    private fun isSatisfied(target: File, spec: ModelSpec): Boolean {
        if (spec.sizeBytes > 0 && target.length() != spec.sizeBytes) return false
        return if (spec.sha256 != null) {
            sha256(target) == spec.sha256
        } else {
            // Unverified custom model: accept what our own post-download hash
            // recorded, so the file is not fetched again per track.
            val recorded = sidecar(target)
            recorded?.isFile == true && recorded.readText().trim() == sha256(target)
        }
    }

    private fun sidecar(target: File) = File(target.parentFile, "${target.name}.sha256")

    private suspend fun download(
        url: String,
        spec: ModelSpec,
        target: File,
        onProgress: (Float) -> Unit,
    ) {
        val temp = File(target.parentFile, "${target.name}.part")
        temp.delete()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "StemSep/0.1")
        }

        try {
            val response = connection.responseCode
            if (response !in 200..299) {
                throw IOException("HTTP $response while downloading ${spec.displayName}")
            }
            val total = connection.contentLength.toLong().takeIf { it > 0 } ?: spec.sizeBytes
            val digest = MessageDigest.getInstance("SHA-256")
            var written = 0L
            var lastReported = -1
            val buffer = ByteArray(128 * 1024)
            connection.inputStream.use { input ->
                FileOutputStream(temp).use { output ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        written += read
                        val percent = ((written * 100) / total).toInt().coerceIn(0, 100)
                        if (percent != lastReported) {
                            lastReported = percent
                            onProgress(percent / 100f)
                        }
                    }
                    output.fd.sync()
                }
            }

            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (spec.sha256 != null && !actualHash.equals(spec.sha256, ignoreCase = true)) {
                throw IOException("Model checksum mismatch for ${spec.displayName}")
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            if (spec.sha256 == null) {
                sidecar(target).writeText(actualHash)
            }
            onProgress(1f)
        } finally {
            connection.disconnect()
            temp.delete()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
