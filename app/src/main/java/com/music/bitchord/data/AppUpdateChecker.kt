package com.music.bitchord.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.music.bitchord.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * BitChord ships as a sideloaded APK off GitHub Releases rather than through
 * a store, so there's nothing to push an update notice on its own — this
 * polls the repo's "latest release" once per launch and compares its tag
 * against the running build.
 *
 * The update itself is also handled here: the release's `.apk` asset is
 * downloaded into the app's cache and handed to the system package installer,
 * so the whole round trip stays inside the app instead of bouncing out to a
 * browser.
 */
object AppUpdateChecker {

    data class UpdateInfo(
        val version: String,
        val releaseUrl: String,
        val apkUrl: String?,
        /** The release's own Markdown body, shown as this update's "what's new". */
        val notes: String?,
    )

    private const val CACHE_SUBDIR = "updates"
    private const val PARTIAL_SUFFIX = ".part"
    private const val MAX_RETRIES = 5
    private const val RETRY_BASE_DELAY_MS = 1_000L

    /**
     * Owns the download so it isn't tied to the activity's composition: leaving
     * the app (or the window being recreated) no longer cancels it.
     */
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/kushagrasinghx/BitChord/releases/latest"

    private val json = Json { ignoreUnknownKeys = true }

    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available = _available.asStateFlow()

    /** Where this update's APK download currently stands, for the dialog's progress row. */
    sealed interface DownloadState {
        data object Idle : DownloadState
        data class Downloading(val fraction: Float) : DownloadState
        data class Ready(val file: File) : DownloadState
        data class Failed(val message: String) : DownloadState
    }

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download = _download.asStateFlow()

    /** Set from the UI thread when the user cancels; polled between network reads. */
    @Volatile
    private var downloadCancelled = false

    suspend fun check() = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(LATEST_RELEASE_URL).build()
            val body = Http.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            } ?: return@runCatching
            val release = json.parseToJsonElement(body) as? JsonObject ?: return@runCatching
            val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@runCatching
            val url = release["html_url"]?.jsonPrimitive?.contentOrNull ?: return@runCatching
            val apkUrl = apkAssetUrl(release)
            val notes = release["body"]?.jsonPrimitive?.contentOrNull
            val latest = tag.removePrefix("v")
            if (isNewer(latest, BuildConfig.VERSION_NAME)) {
                _available.value = UpdateInfo(latest, url, apkUrl, notes)
            }
        }
    }

    /**
     * Wipes any finished APK left over from a previous run. Called once at cold
     * start so a downloaded update is only ever "Install Now" for the session
     * that downloaded it. A half-finished `.part` file is kept on purpose: it
     * is what lets an interrupted download pick up where it stopped instead of
     * starting over (a leftover for an older version is removed by the next
     * [downloadApk]).
     */
    suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) {
        File(context.cacheDir, CACHE_SUBDIR).listFiles()?.forEach {
            if (!it.name.endsWith(PARTIAL_SUFFIX)) it.delete()
        }
    }

    /**
     * One release carries the phone app and the TV app, each as a universal
     * APK plus per-ABI splits:
     *
     *     BitChord-v1.8.apk     BitChord-v1.8_arm64-v8a.apk     ...
     *     BitChordTV-v1.8.apk   BitChordTV-v1.8_arm64-v8a.apk   ...
     *
     * so this app's APKs are the ones named with its own
     * [BuildConfig.UPDATE_APK_PREFIX] — "BitChord-" never matches a TV file,
     * since the prefix ends at the hyphen. The universal build (no `_ABI`
     * suffix) is taken first, as it installs anywhere; failing that, the split
     * for this device's ABI. A release with neither leaves
     * [UpdateInfo.apkUrl] null and the UI falls back to opening the releases
     * page.
     */
    private fun apkAssetUrl(release: JsonObject): String? = runCatching {
        val apks = release["assets"]?.jsonArray
            ?.mapNotNull { it as? JsonObject }
            ?.mapNotNull { asset ->
                val name = asset["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val url = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val ours = name.startsWith(BuildConfig.UPDATE_APK_PREFIX, ignoreCase = true) &&
                    name.endsWith(".apk", ignoreCase = true) &&
                    asset["state"]?.jsonPrimitive?.contentOrNull == "uploaded"
                if (ours) name.removeSuffix(".apk").removeSuffix(".APK") to url else null
            }
            .orEmpty()
        apks.firstOrNull { (name, _) -> '_' !in name }?.second
            ?: Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi ->
                apks.firstOrNull { (name, _) -> name.endsWith("_$abi") }?.second
            }
    }.getOrNull()

    /**
     * Starts the download on the checker's own scope, so it keeps going when
     * the activity is closed or recreated. Does nothing if one is already
     * running.
     */
    fun startDownload(context: Context) {
        if (downloadJob?.isActive == true) return
        val appContext = context.applicationContext
        downloadJob = downloadScope.launch { downloadApk(appContext) }
    }

    /**
     * Streams the current update's APK into the app cache, reporting progress
     * through [download]. The bytes land in a `.part` file that is kept if the
     * transfer breaks (dropped connection, app killed): the next attempt asks
     * the server for the rest with an HTTP Range request. Network errors are
     * retried a few times with a growing delay. A finished file survives a
     * cancelled dialog: until the state is reset, "Install Now" comes straight
     * back without a second download.
     */
    suspend fun downloadApk(context: Context): Unit = withContext(Dispatchers.IO) {
        val info = _available.value ?: return@withContext
        val url = info.apkUrl ?: return@withContext
        downloadCancelled = false
        _download.value = DownloadState.Downloading(0f)

        val dir = File(context.cacheDir, CACHE_SUBDIR).apply { mkdirs() }
        val target = File(dir, "bitchord-${info.version}.apk")
        val partial = File(dir, target.name + PARTIAL_SUFFIX)
        // Drop anything that isn't this version's partial download.
        dir.listFiles()?.forEach { if (it != partial) it.delete() }

        var attempt = 0
        while (true) {
            try {
                if (!transfer(url, partial)) {
                    _download.value = DownloadState.Idle
                    return@withContext
                }
                check(partial.renameTo(target)) { "Couldn't save the downloaded update" }
                _download.value = DownloadState.Ready(target)
                return@withContext
            } catch (e: IOException) {
                if (downloadCancelled) {
                    _download.value = DownloadState.Idle
                    return@withContext
                }
                if (++attempt > MAX_RETRIES) {
                    _download.value = DownloadState.Failed(e.message ?: "Download failed")
                    return@withContext
                }
                delay(RETRY_BASE_DELAY_MS shl (attempt - 1))
            } catch (e: Exception) {
                _download.value = if (downloadCancelled) {
                    DownloadState.Idle
                } else {
                    DownloadState.Failed(e.message ?: "Download failed")
                }
                return@withContext
            }
        }
    }

    /**
     * One pass at fetching [url] into [partial], continuing from whatever is
     * already there. Returns false if the user cancelled (the partial file is
     * left for a later resume), true once the file is complete, and throws
     * [IOException] for anything worth another attempt.
     */
    internal fun transfer(url: String, partial: File): Boolean {
        val have = partial.length()
        val request = Request.Builder().url(url)
            .apply { if (have > 0) header("Range", "bytes=$have-") }
            .build()
        Http.client.newCall(request).execute().use { response ->
            if (response.code == 416) {
                // The server has nothing past what we hold: either the partial
                // is already the whole file, or it doesn't belong to this file.
                val size = response.header("Content-Range")?.substringAfter("/")?.toLongOrNull()
                if (size == have) return true
                partial.delete()
                throw IOException("Download restarted")
            }
            if (response.code >= 500) throw IOException("Download failed: HTTP ${response.code}")
            check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
            val body = response.body ?: throw IOException("Empty download body")

            // 206 continues the partial file; a plain 200 means the server
            // ignored the Range header and is sending everything again.
            val resumed = response.code == 206 && have > 0
            val offset = if (resumed) have else 0L
            val remaining = body.contentLength().takeIf { it > 0 }
            val total = remaining?.let { it + offset }

            var written = offset
            body.byteStream().use { input ->
                java.io.FileOutputStream(partial, resumed).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (downloadCancelled) return false
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                        total?.let {
                            _download.value =
                                DownloadState.Downloading((written.toFloat() / it).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            if (total != null && written != total) throw IOException("Connection closed early")
        }
        return true
    }

    /** Stops an in-flight download; the next read loop sees this and bails. */
    fun cancelDownload() {
        downloadCancelled = true
    }

    /** Back to square one after a failure, so the dialog offers Download again. */
    fun resetDownload() {
        _download.value = DownloadState.Idle
    }

    /**
     * Hands a downloaded APK to the system installer.
     *
     * Sideloaded apps need the user's blessing per app ("install unknown apps");
     * without it the installer intent silently does nothing on most ROMs, so
     * the user is sent to that one switch first and taps Install again after.
     * Some TV builds have no such settings screen; there the installer is
     * launched anyway and shows its own "not allowed" prompt with a way in.
     */
    fun installApk(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val opened = runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.isSuccess
            if (opened) return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                .putExtra(Intent.EXTRA_RETURN_RESULT, true)
                .addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK,
                ),
        )
    }

    /** A version split into its numeric dotted parts and whether it carries a "-suffix" (e.g. "-beta2"). */
    private data class ParsedVersion(val parts: List<Int>, val isPreRelease: Boolean)

    private fun parseVersion(raw: String): ParsedVersion {
        val dash = raw.indexOf('-')
        val base = if (dash >= 0) raw.substring(0, dash) else raw
        return ParsedVersion(base.split(".").map { it.toIntOrNull() ?: 0 }, dash >= 0)
    }

    /**
     * Numeric, dot-separated comparison — "1.10" outranks "1.9" — with one
     * extra rule: a "-betaN" build (see the debug build type's
     * `versionNameSuffix` in app/build.gradle.kts) is treated as older than a
     * plain release of the same numbers, since the beta by definition predates
     * the tag it was testing toward. Without this, a beta and the release it
     * matches compare equal and testers never get nudged onto the real build.
     */
    private fun isNewer(latest: String, current: String): Boolean {
        val l = parseVersion(latest)
        val c = parseVersion(current)
        for (i in 0 until maxOf(l.parts.size, c.parts.size)) {
            val a = l.parts.getOrElse(i) { 0 }
            val b = c.parts.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return c.isPreRelease && !l.isPreRelease
    }
}
