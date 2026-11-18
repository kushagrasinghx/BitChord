package com.music.bitchord.playback.smart

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.music.bitchord.data.Http
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest

/**
 * Automix's two ONNX models, fetched once from a GitHub release instead of
 * riding in every APK.
 *
 * They are 31 MB between them, and most installs never turn Automix on, so
 * shipping them made every download and every update a third larger for a
 * feature few use. They are fetched the first time Automix is switched on (or
 * at launch, if it already is) into the same `filesDir` paths the APK used to
 * unpack them to — so anyone who had Automix running before keeps their copies
 * and downloads nothing.
 *
 * Until both are on disk the trackers simply find no model, and Automix falls
 * back to plain crossfades, exactly as it does for any other analysis failure.
 */
object AutomixModels {

    class Model(val fileName: String, val bytes: Long, val sha256: String)

    /**
     * The weights come from the latest app release, attached as
     * `model_<file>` so they list below the APKs. Every release must attach
     * both files under those names, or Automix cannot download them until one
     * does (the update checker only picks up `.apk` assets, so they never
     * confuse it). A copy is only kept if it matches the size and SHA-256
     * below, so new weights need a new app version, not just new assets. On
     * disk they keep the plain names.
     */
    private const val RELEASE_URL =
        "https://github.com/kushagrasinghx/BitChord/releases/latest/download/model_"

    val BEAT = Model(
        BeatTracker.MODEL_ASSET,
        23_138_816,
        "7801f9c1adfd5ff193f37ececa3320bf1fda1ddee2bf7de341cea4c25ffd0acd",
    )
    val VOCALS = Model(
        VocalTracker.MODEL_ASSET,
        9_054_206,
        "a2be987b55a29bc149d3a6ae99b08175d81f85ee292a8ea21f96c3a473bc94cb",
    )
    private val ALL = listOf(BEAT, VOCALS)

    /** Both models together, for the settings line that says what is being fetched. */
    val totalBytes: Long = ALL.sumOf { it.bytes }

    sealed interface State {
        data object Ready : State
        /** Not on disk, and nothing fetching them: Automix is off, or has not asked yet. */
        data object Missing : State
        data class Downloading(val fraction: Float) : State
        data object Failed : State
    }

    private val _state = MutableStateFlow<State>(State.Missing)
    val state: StateFlow<State> = _state.asStateFlow()

    private const val TAG = "AutomixModels"

    /** After a failed fetch, how long an analysis pass waits before trying again on its own. */
    private const val RETRY_AFTER_MS = 2 * 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    @Volatile private var filesDir: File? = null
    private var job: Job? = null
    private var failedAt = 0L

    /**
     * Called once at launch. Settles whether the models are already here and,
     * whenever Automix is (or becomes) on without them, starts fetching them.
     */
    fun install(context: Context) {
        synchronized(lock) {
            if (filesDir != null) return
            filesDir = context.applicationContext.filesDir
        }
        if (ALL.all(::isPresent)) _state.value = State.Ready
        scope.launch {
            AppSettings.smartFadeEnabled.collect { on ->
                // Switching Automix on is the user asking for it now, so a
                // recent failure does not hold this attempt back.
                if (on) request(force = true)
            }
        }
    }

    /**
     * The path of [model] on disk, for a tracker about to open it.
     *
     * Throws [FileNotFoundException] while it is not there — the trackers treat
     * that like any unreadable model and produce nothing — and makes sure a
     * fetch is under way so a later pass finds it.
     */
    fun path(model: Model): String {
        val file = fileFor(model) ?: throw FileNotFoundException("AutomixModels.install was never called")
        if (isPresent(model)) return file.absolutePath
        request(force = false)
        throw FileNotFoundException("${model.fileName} has not been downloaded yet")
    }

    /** Settings' Download / Retry: fetch whatever is missing now, whether or not Automix is on. */
    fun download() = request(force = true)

    /**
     * Settings' Delete: removes both models (and any half-finished download)
     * to free the space. Refused while a download is running. The caller turns
     * Automix off first, or the next analysis pass would fetch them straight back.
     */
    fun delete(): Boolean {
        synchronized(lock) {
            if (job?.isActive == true) return false
            for (model in ALL) {
                fileFor(model)?.let { file ->
                    file.delete()
                    File(file.parentFile, "${model.fileName}.part").delete()
                }
            }
            failedAt = 0L
            _state.value = State.Missing
        }
        Log.i(TAG, "Automix models deleted")
        return true
    }

    private fun fileFor(model: Model): File? = filesDir?.let { File(it, model.fileName) }

    /**
     * A file of exactly the published size is taken as the model. Every copy
     * this class writes is checked against its SHA-256 before it is renamed
     * into place, and the copies older versions unpacked from the APK are the
     * same bytes; a copy cut short by a crash mid-unpack is the wrong size and
     * gets fetched again.
     */
    private fun isPresent(model: Model): Boolean = fileFor(model)?.length() == model.bytes

    private fun request(force: Boolean) {
        synchronized(lock) {
            if (filesDir == null || job?.isActive == true) return
            if (ALL.all(::isPresent)) {
                _state.value = State.Ready
                return
            }
            if (!force && failedAt != 0L && SystemClock.elapsedRealtime() - failedAt < RETRY_AFTER_MS) return
            job = scope.launch { fetchMissing() }
        }
    }

    private fun fetchMissing() {
        val missing = ALL.filterNot(::isPresent)
        val total = missing.sumOf { it.bytes }.toFloat()
        var done = 0L
        _state.value = State.Downloading(0f)
        try {
            for (model in missing) {
                fetch(model) { read ->
                    _state.value = State.Downloading(((done + read) / total).coerceIn(0f, 1f))
                }
                done += model.bytes
            }
            synchronized(lock) { failedAt = 0L }
            _state.value = State.Ready
            Log.i(TAG, "Automix models ready")
        } catch (e: Exception) {
            Log.w(TAG, "Automix model download failed", e)
            synchronized(lock) { failedAt = SystemClock.elapsedRealtime() }
            _state.value = State.Failed
        }
    }

    private fun fetch(model: Model, progress: (Long) -> Unit) {
        val target = fileFor(model) ?: return
        val part = File(target.parentFile, "${model.fileName}.part")
        val url = RELEASE_URL + model.fileName
        val request = Request.Builder().url(url).build()
        Log.i(TAG, "Fetching $url")
        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for ${model.fileName}")
            val body = response.body ?: throw IOException("Empty body for ${model.fileName}")
            val digest = MessageDigest.getInstance("SHA-256")
            var read = 0L
            body.byteStream().use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var lastReport = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        read += n
                        if (read > model.bytes) throw IOException("${model.fileName} is larger than expected")
                        if (read - lastReport >= 256 * 1024) {
                            lastReport = read
                            progress(read)
                        }
                    }
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (read != model.bytes || hash != model.sha256) {
                part.delete()
                throw IOException("${model.fileName} failed verification ($read bytes, sha256 $hash)")
            }
        }
        if (!part.renameTo(target)) {
            part.delete()
            throw IOException("Could not move ${model.fileName} into place")
        }
        progress(model.bytes)
    }
}
