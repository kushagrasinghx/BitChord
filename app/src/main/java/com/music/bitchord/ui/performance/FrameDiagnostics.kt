package com.music.bitchord.ui.performance

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.os.Trace
import android.view.FrameMetrics
import android.view.View
import android.view.Window
import androidx.profileinstaller.ProfileVerifier
import com.music.bitchord.BuildConfig
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.music.bitchord.data.settings.AppSettings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.Executor
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

internal enum class DiagnosticMode(
    val title: String,
    val description: String,
    val noBlur: Boolean = false,
    val noArtwork: Boolean = false,
    val highRefresh: Boolean = false,
    val noAnimation: Boolean = false,
    val noLiveBackground: Boolean = false,
    /** Attribution only: each drops one stage of the blurred surfaces, to price it. */
    val noGlassRim: Boolean = false,
    val noGlassLens: Boolean = false,
    val noTopFade: Boolean = false,
    val legacyGlassRim: Boolean = false,
    val fullPageSample: Boolean = false,
    val noGlassClip: Boolean = false,
    val noGlassShadow: Boolean = false,
    val drawnGlass: Boolean = false,
    val noGlassTone: Boolean = false,
) {
    BASELINE("Baseline", "Current rendering and refresh-rate settings."),
    LIGHT("Light rendering + high refresh", "Disable dynamic blur and motion artwork; use the static artwork background and request the highest rate. Keep UI transitions.", noBlur = true, noArtwork = true, highRefresh = true, noLiveBackground = true),
    HIGH_REFRESH("Highest refresh rate only", "Keep rendering unchanged; request the highest supported rate on the main and player windows.", highRefresh = true),
    NO_BLUR("No dynamic blur", "Keep navigation and transitions; temporarily fill blurred surfaces solid.", noBlur = true),
    NO_ARTWORK("No motion artwork", "Use still covers instead of looping Canvas / motion artwork.", noArtwork = true),
    NO_ANIMATION("Reduce UI animation", "Temporarily enable Reduce animation without changing the refresh-rate preference.", noAnimation = true),
    NO_GLASS_RIM("No glass rim", "Keep the glass; skip its rim, glow and outline.", noGlassRim = true),
    NO_GLASS_LENS("No glass lens", "Keep the glass; skip its refraction pass.", noGlassLens = true),
    NO_TOP_FADE("No top fade", "Keep everything else; skip the blur under the status bar.", noTopFade = true),
    LEGACY_GLASS_RIM("Legacy glass rim", "Draw the rim from its paths every size change, as before it was baked.", legacyGlassRim = true),
    FULL_PAGE_SAMPLE("Full page sample", "Sample the whole page for the blurred surfaces, not just what they read.", fullPageSample = true),
    NO_GLASS_CLIP("No glass clip", "Attribution only: skip the glass's clips to its shape (corners will show).", noGlassClip = true),
    NO_GLASS_SHADOW("No glass shadow", "Attribution only: skip the glass's drop shadows.", noGlassShadow = true),
    DRAWN_GLASS("Drawn glass", "Draw the glass's rim, fill and edge after its lens instead of in its shader.", drawnGlass = true),
    NO_GLASS_TONE("No glass tone", "Keep the glass; don't read what it shows under its glyphs, which stay the theme's colour.", noGlassTone = true),
}

internal data class DiagnosticStatus(
    val recording: Boolean = false,
    val writing: Boolean = false,
    val stopAtUptimeMs: Long = 0,
    val message: String = "No recording in this process yet.",
)

/**
 * The recording's mode, for a surface a mode switches off. Always [DiagnosticMode.BASELINE]
 * in a build without diagnostics, which never subscribes to anything.
 */
@Composable
internal fun diagnosticMode(): DiagnosticMode =
    if (BuildConfig.PERFORMANCE_DIAGNOSTICS) {
        FrameDiagnostics.mode.collectAsState().value
    } else {
        DiagnosticMode.BASELINE
    }

/** Opt-in release instrumentation. No listeners or overrides exist in ordinary builds. */
internal object FrameDiagnostics {
    private val main = Handler(Looper.getMainLooper())
    private val worker by lazy {
        Handler(HandlerThread("frame-diagnostics", android.os.Process.THREAD_PRIORITY_BACKGROUND).apply { start() }.looper)
    }
    private val _mode = MutableStateFlow(DiagnosticMode.BASELINE)
    val mode = _mode.asStateFlow()
    private val _status = MutableStateFlow(DiagnosticStatus())
    val status = _status.asStateFlow()
    private val windows = LinkedHashMap<Window, Registration>() // Main-thread confined.
    private var restoreSettings: (() -> Unit)? = null
    private var automaticStop: Runnable? = null
    private var session: Session? = null // Worker-thread confined.
    @Volatile private var measuring = false
    private val cookies = AtomicInteger()

    val forceHighRefresh: Boolean get() = BuildConfig.PERFORMANCE_DIAGNOSTICS && mode.value.highRefresh

    fun registerWindow(window: Window, view: View, name: String, resetRefresh: (() -> Unit)?): () -> Unit {
        if (!BuildConfig.PERFORMANCE_DIAGNOSTICS) return {}
        check(Looper.myLooper() == Looper.getMainLooper())
        val registration = Registration(window, view, name, resetRefresh)
        windows[window] = registration
        if (status.value.recording) {
            registration.applyRefresh()
            registration.attach()
        }
        return {
            registration.detach()
            registration.restoreRefresh()
            if (windows[window] === registration) windows.remove(window)
        }
    }

    fun start(context: Context, mode: DiagnosticMode, label: String, recordMs: Long = RECORD_MS) {
        check(BuildConfig.PERFORMANCE_DIAGNOSTICS)
        check(!status.value.recording && !status.value.writing)
        val app = context.applicationContext
        val original = settings()
        val blur = AppSettings.reduceDynamicBlur.value
        val artwork = AppSettings.animatedCanvas.value
        val animation = AppSettings.reduceAnimation.value
        val legacyMesh = AppSettings.legacyMeshGradient.value
        // In-memory only: do not invoke setters that persist or couple these switches.
        restoreSettings = {
            if (mode.noBlur) AppSettings.reduceDynamicBlur.value = blur
            if (mode.noArtwork) AppSettings.animatedCanvas.value = artwork
            if (mode.noAnimation) AppSettings.reduceAnimation.value = animation
            if (mode.noLiveBackground) AppSettings.legacyMeshGradient.value = legacyMesh
            com.music.bitchord.ui.components.legacyRimForDiagnostics.value = false
            com.music.bitchord.ui.components.fullPageSampleForDiagnostics.value = false
            com.music.bitchord.ui.components.backdrop.backdropClipForDiagnostics.value = true
            com.music.bitchord.ui.components.shaderGlassForDiagnostics.value = com.music.bitchord.ui.components.SHADER_GLASS_DEFAULT
            com.music.bitchord.ui.components.glassToneForDiagnostics.value = true
        }
        _mode.value = mode
        if (mode.noBlur) AppSettings.reduceDynamicBlur.value = true
        if (mode.noArtwork) AppSettings.animatedCanvas.value = false
        if (mode.noAnimation) AppSettings.reduceAnimation.value = true
        if (mode.noLiveBackground) AppSettings.legacyMeshGradient.value = false
        com.music.bitchord.ui.components.legacyRimForDiagnostics.value = mode.legacyGlassRim
        com.music.bitchord.ui.components.fullPageSampleForDiagnostics.value = mode.fullPageSample
        com.music.bitchord.ui.components.backdrop.backdropClipForDiagnostics.value = !mode.noGlassClip
        if (mode.drawnGlass) com.music.bitchord.ui.components.shaderGlassForDiagnostics.value = false
        com.music.bitchord.ui.components.glassToneForDiagnostics.value = !mode.noGlassTone
        val start = System.nanoTime() + WARMUP_MS * 1_000_000
        val id = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(Date())
        val metadata = JSONObject()
            .put("schema", 1).put("id", id).put("mode", mode.name).put("label", label.take(120))
            .put("version", BuildConfig.VERSION_NAME).put("buildType", BuildConfig.BUILD_TYPE)
            .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
            .put("sdk", Build.VERSION.SDK_INT).put("android", Build.VERSION.RELEASE)
            .put("settingsBefore", original).put("settingsDuring", settings())
            .put("powerBefore", power(app))
            .put("notes", "App-window metrics only; launcher/system animations need a system trace. Lost reports are not dropped frames. First draws are separate from steady-frame summaries. Phase timings overlap; GPU duration must not be added to total. Raw times are nanoseconds on the monotonic clock.")
        worker.post {
            session = Session(id, metadata, FrameMeasurements(start, start + recordMs * 1_000_000))
            metadata.put("profileVerification", "pending")
            val profile = ProfileVerifier.getCompilationStatusAsync()
            profile.addListener({
                val verified = runCatching {
                    val status = profile.get()
                    JSONObject().put("code", status.profileInstallResultCode)
                        .put("compiledWithProfile", status.isCompiledWithProfile)
                        .put("profileEnqueued", status.hasProfileEnqueuedForCompilation())
                        .put("embeddedProfile", status.appApkHasEmbeddedProfile())
                }.getOrElse { JSONObject().put("error", it.javaClass.simpleName) }
                session?.takeIf { it.id == id }?.metadata?.put("profileVerification", verified)
            }, Executor { worker.post(it) })
        }
        measuring = true
        _status.value = DiagnosticStatus(true, stopAtUptimeMs = android.os.SystemClock.uptimeMillis() + WARMUP_MS + recordMs)
        windows.values.forEach { it.applyRefresh(); it.attach() }
        automaticStop = Runnable { stop(app) }.also { main.postDelayed(it, WARMUP_MS + recordMs) }
    }

    fun stop(context: Context) {
        if (!status.value.recording) return
        automaticStop?.let(main::removeCallbacks)
        automaticStop = null
        val stoppedAt = System.nanoTime()
        measuring = false
        windows.values.forEach { it.detach() }
        _mode.value = DiagnosticMode.BASELINE
        restoreSettings?.invoke()
        restoreSettings = null
        windows.values.forEach { it.restoreRefresh() }
        _status.value = DiagnosticStatus(writing = true, message = "Writing recording…")
        val app = context.applicationContext
        val powerAfter = power(app)
        worker.post {
            val completed = session
            session = null
            runCatching {
                requireNotNull(completed)
                completed.metadata.put("stoppedAtNs", stoppedAt).put("powerAfter", powerAfter)
                val summary = completed.frames.summary()
                val report = completed.report()
                val directory = reportDirectory(app).apply { mkdirs() }
                File(directory, "frame-${completed.id}.json").writeText(report.toString())
                // Also where `adb pull` can reach it without the share sheet — see [runSuite].
                app.getExternalFilesDir(SUITE_DIRECTORY)?.let { external ->
                    File(external, "frame-${completed.id}.json").writeText(report.toString())
                }
                val deadlines = if (summary.completeDeadlines) {
                    String.format(Locale.ROOT, "%.1f%% missed app deadlines.", 100.0 * summary.lateFrames / summary.knownDeadlines)
                } else "Deadline percentage unavailable: incomplete or inconsistent platform data."
                "${completed.metadata.getString("mode")}: ${summary.frames} unique frame samples; $deadlines ${summary.firstDraws} first draws tracked separately."
            }.fold(
                onSuccess = { _status.value = DiagnosticStatus(message = it) },
                onFailure = { _status.value = DiagnosticStatus(message = "Recording failed: ${it.javaClass.simpleName}") },
            )
        }
    }

    fun export(context: Context, callback: (Result<File>) -> Unit) {
        worker.post {
            val result = runCatching {
                val reports = reportDirectory(context).listFiles().orEmpty()
                    .filter { it.name.startsWith("frame-") && it.extension == "json" }
                    .sortedByDescending { it.lastModified() }.take(20)
                check(reports.isNotEmpty()) { "Record at least one run first." }
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
                val out = File(context.cacheDir, "shared/bitchord-frames-$stamp.zip")
                out.parentFile?.mkdirs()
                ZipOutputStream(out.outputStream().buffered()).use { zip ->
                    reports.forEach { file ->
                        zip.putNextEntry(ZipEntry(file.name))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                out
            }
            main.post { callback(result) }
        }
    }

    fun mark(name: String) {
        if (!BuildConfig.PERFORMANCE_DIAGNOSTICS) return
        if (Build.VERSION.SDK_INT >= 29 && Trace.isEnabled()) {
            Trace.beginSection(name)
            Trace.endSection()
        }
        if (!measuring) return
        val at = System.nanoTime()
        worker.post { session?.events?.put(JSONObject().put("name", name).put("atNs", at)) }
    }

    fun <T> measure(name: String, block: () -> T): T {
        if (!BuildConfig.PERFORMANCE_DIAGNOSTICS) return block()
        val collect = measuring
        if (!collect && !(Build.VERSION.SDK_INT >= 29 && Trace.isEnabled())) return block()
        val at = if (collect) System.nanoTime() else 0L
        Trace.beginSection(name)
        return try { block() } finally {
            Trace.endSection()
            if (collect) operation(name, at, System.nanoTime())
        }
    }

    suspend fun <T> measureSuspending(name: String, block: suspend () -> T): T {
        if (!BuildConfig.PERFORMANCE_DIAGNOSTICS) return block()
        val collect = measuring
        if (!collect && !(Build.VERSION.SDK_INT >= 29 && Trace.isEnabled())) return block()
        val at = if (collect) System.nanoTime() else 0L
        val cookie = cookies.incrementAndGet()
        if (Build.VERSION.SDK_INT >= 29) Trace.beginAsyncSection(name, cookie)
        return try { block() } finally {
            if (Build.VERSION.SDK_INT >= 29) Trace.endAsyncSection(name, cookie)
            if (collect) operation(name, at, System.nanoTime())
        }
    }

    private fun operation(name: String, start: Long, end: Long) {
        worker.post {
            session?.takeIf { start in it.frames.startNs until it.frames.endNs }?.operations
                ?.put(JSONObject().put("name", name).put("startNs", start).put("endNs", end))
        }
    }

    private class Registration(val window: Window, val view: View, val name: String, val resetRefresh: (() -> Unit)?) {
        private var listener: Window.OnFrameMetricsAvailableListener? = null
        private var originalRefresh: (() -> Unit)? = null
        private var waitingForAttach: View.OnAttachStateChangeListener? = null

        fun applyRefresh() {
            if (!forceHighRefresh || originalRefresh != null || !view.isAttachedToWindow) return
            val rate = window.attributes.preferredRefreshRate
            val vote = if (Build.VERSION.SDK_INT >= 35) view.requestedFrameRate else 0f
            val balanced = if (Build.VERSION.SDK_INT >= 35) window.isFrameRatePowerSavingsBalanced else true
            originalRefresh = {
                if (resetRefresh != null) resetRefresh.invoke() else {
                    window.attributes = window.attributes.apply { preferredRefreshRate = rate }
                    if (Build.VERSION.SDK_INT >= 35) {
                        window.setFrameRatePowerSavingsBalanced(balanced)
                        view.requestedFrameRate = vote
                    }
                }
            }
            val highest = view.display.supportedPerformanceRefreshRates().max().toFloat()
            window.attributes = window.attributes.apply { preferredRefreshRate = highest }
            if (Build.VERSION.SDK_INT >= 35) {
                window.setFrameRatePowerSavingsBalanced(false)
                view.requestedFrameRate = highest
            }
        }

        fun restoreRefresh() {
            originalRefresh?.invoke()
            originalRefresh = null
        }

        fun attach() {
            if (listener != null) return
            // A Compose dialog can compose before show() attaches its window.
            // Wait for a real display before choosing its rate or adding the observer.
            if (!view.isAttachedToWindow) {
                if (waitingForAttach == null) {
                    waitingForAttach = object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {
                            v.removeOnAttachStateChangeListener(this)
                            waitingForAttach = null
                            if (status.value.recording) attach()
                        }
                        override fun onViewDetachedFromWindow(v: View) {}
                    }.also(view::addOnAttachStateChangeListener)
                }
                return
            }
            applyRefresh()
            val info = JSONObject().put("name", name).put("atNs", System.nanoTime())
                .put("displayRefreshHz", view.display?.refreshRate)
                .put("supportedHz", JSONArray(view.display.supportedPerformanceRefreshRates()))
                .put("preferredRefreshHz", window.attributes.preferredRefreshRate)
                .put("width", view.width).put("height", view.height)
            if (Build.VERSION.SDK_INT >= 35) {
                // NaN is the platform's "no vote" (REQUESTED_FRAME_RATE_CATEGORY_DEFAULT),
                // and JSONObject refuses NaN outright — recording crashed on start.
                info.put("viewRequestedFrameRate", view.requestedFrameRate.takeUnless { it.isNaN() } ?: JSONObject.NULL)
                    .put("powerSavingsBalanced", window.isFrameRatePowerSavingsBalanced)
            }
            worker.post { session?.windows?.put(info) }
            listener = Window.OnFrameMetricsAvailableListener { _, metrics, lost ->
                val active = session ?: return@OnFrameMetricsAvailableListener
                val snapshot = FrameMetrics(metrics)
                fun metric(key: Int) = snapshot.getMetric(key)
                val unknown = metric(FrameMetrics.UNKNOWN_DELAY_DURATION)
                val layout = metric(FrameMetrics.LAYOUT_MEASURE_DURATION)
                val draw = metric(FrameMetrics.DRAW_DURATION)
                val input = metric(FrameMetrics.INPUT_HANDLING_DURATION)
                val animation = metric(FrameMetrics.ANIMATION_DURATION)
                active.frames.add(FrameSample(
                    window = name,
                    intendedNs = metric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP),
                    totalNs = metric(FrameMetrics.TOTAL_DURATION),
                    deadlineNs = if (Build.VERSION.SDK_INT >= 31) metric(FrameMetrics.DEADLINE) else -1,
                    uiNs = if (unknown >= 0 && layout >= 0 && draw >= 0 && input >= 0 && animation >= 0)
                        unknown + layout + draw + input + animation else -1,
                    gpuNs = if (Build.VERSION.SDK_INT >= 31) metric(FrameMetrics.GPU_DURATION) else -1,
                    unknownNs = unknown, layoutNs = layout, drawNs = draw,
                    syncNs = metric(FrameMetrics.SYNC_DURATION),
                    commandNs = metric(FrameMetrics.COMMAND_ISSUE_DURATION),
                    swapNs = metric(FrameMetrics.SWAP_BUFFERS_DURATION),
                    vsyncDelayNs = metric(FrameMetrics.VSYNC_TIMESTAMP) - metric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP),
                    firstDraw = metric(FrameMetrics.FIRST_DRAW_FRAME) == 1L,
                    reportsLost = lost,
                ))
            }.also { window.addOnFrameMetricsAvailableListener(it, worker) }
        }

        fun detach() {
            waitingForAttach?.let(view::removeOnAttachStateChangeListener)
            waitingForAttach = null
            listener?.let { window.removeOnFrameMetricsAvailableListener(it) }
            listener = null
        }
    }

    private class Session(val id: String, val metadata: JSONObject, val frames: FrameMeasurements) {
        val windows = JSONArray()
        val events = JSONArray()
        val operations = JSONArray()

        fun report(): JSONObject {
            val summaries = JSONObject().put("all", summaryJson(frames.summary()))
            frames.frames.map { it.window }.distinct().forEach { summaries.put(it, summaryJson(frames.summary(it))) }
            val rows = JSONArray()
            frames.frames.forEach { f ->
                rows.put(JSONArray(listOf(f.window, f.intendedNs, f.totalNs, f.deadlineNs, f.uiNs, f.gpuNs,
                    f.unknownNs, f.layoutNs, f.drawNs, f.syncNs, f.commandNs, f.swapNs, f.vsyncDelayNs, f.firstDraw, f.reportsLost)))
            }
            return metadata.put("captureStartNs", frames.startNs).put("captureEndNs", frames.endNs)
                .put("windows", windows).put("events", events).put("operations", operations)
                .put("overflowFrames", frames.overflow).put("summary", summaries)
                .put("frameColumns", JSONArray(listOf("window", "intendedNs", "totalNs", "deadlineNs", "uiNs", "gpuNs",
                    "unknownNs", "layoutNs", "drawNs", "syncNs", "commandNs", "swapNs", "vsyncDelayNs", "firstDraw", "reportsLost")))
                .put("frames", rows)
        }
    }

    private fun summaryJson(s: FrameSummary) = JSONObject()
        .put("frames", s.frames).put("firstDraws", s.firstDraws).put("knownDeadlines", s.knownDeadlines)
        .put("reportedFrames", s.reportedFrames).put("duplicateReports", s.duplicateReports)
        .put("conflictingDeadlineFrames", s.conflictingDeadlineFrames).put("invalidDeadlineFrames", s.invalidDeadlineFrames)
        .put("completeDeadlines", s.completeDeadlines)
        .put("lateFrames", s.lateFrames).put("lostReports", s.lostReports)
        .put("deadlineP50Ns", s.deadlineP50Ns ?: JSONObject.NULL)
        .put("totalP50Ns", s.totalP50Ns ?: JSONObject.NULL).put("totalP95Ns", s.totalP95Ns ?: JSONObject.NULL)
        .put("totalP99Ns", s.totalP99Ns ?: JSONObject.NULL).put("uiP95Ns", s.uiP95Ns ?: JSONObject.NULL)
        .put("gpuP95Ns", s.gpuP95Ns ?: JSONObject.NULL).put("vsyncIntervalP50Ns", s.vsyncIntervalP50Ns ?: JSONObject.NULL)

    private fun settings() = JSONObject()
        .put("liquidGlass", AppSettings.liquidGlass.value).put("reduceDynamicBlur", AppSettings.reduceDynamicBlur.value)
        .put("reduceAnimation", AppSettings.reduceAnimation.value).put("animatedCanvas", AppSettings.animatedCanvas.value)
        .put("legacyMesh", AppSettings.legacyMeshGradient.value).put("highPerformance", AppSettings.highPerformanceMode.value)
        .put("preferredRefreshHz", AppSettings.performanceRefreshRate.value)

    private fun power(context: Context): JSONObject {
        val power = context.getSystemService(PowerManager::class.java)
        return JSONObject().put("batterySaver", power.isPowerSaveMode).apply {
            if (Build.VERSION.SDK_INT >= 29) put("thermalStatus", power.currentThermalStatus)
        }
    }

    private fun reportDirectory(context: Context) = File(context.cacheDir, "frame-diagnostics")

    /**
     * Records [modes] one after another, [recordMs] each, with nobody at the
     * screen: one run prices every stage the modes switch off against the same
     * session, the same thermals and the same gestures, instead of a recording
     * per mode started by hand. Something outside drives the gestures — the
     * suite only switches modes and records — so `adb shell input swipe` in a
     * loop gives every mode identical flings. Started from an intent, see
     * [SUITE_EXTRA]; each report also lands in the app's external files under
     * [SUITE_DIRECTORY] for `adb pull`, and a line in logcat (tag
     * `BitChordFrames`) marks each mode's start and the end.
     */
    fun runSuite(context: Context, modes: List<DiagnosticMode>, recordMs: Long) {
        check(BuildConfig.PERFORMANCE_DIAGNOSTICS)
        if (status.value.recording || status.value.writing || suiteRunning) return
        suiteRunning = true
        val app = context.applicationContext
        app.getExternalFilesDir(SUITE_DIRECTORY)?.listFiles()?.forEach { it.delete() }
        fun next(index: Int) {
            if (index == modes.size) {
                suiteRunning = false
                android.util.Log.i(SUITE_TAG, "suite done")
                return
            }
            // Wait out the previous recording's write before starting the next.
            if (status.value.recording || status.value.writing) {
                main.postDelayed({ next(index) }, 200)
                return
            }
            val mode = modes[index]
            android.util.Log.i(SUITE_TAG, "suite mode ${index + 1}/${modes.size} ${mode.name}")
            start(app, mode, "suite", recordMs)
            main.postDelayed({ next(index + 1) }, WARMUP_MS + recordMs + 500)
        }
        next(0)
    }

    private var suiteRunning = false

    /** `am start` extra on MainActivity: comma-separated [DiagnosticMode] names, or `all`. */
    const val SUITE_EXTRA = "bitchord.frameSuite"

    /** `am start` extra on MainActivity: seconds recorded per mode. */
    const val SUITE_SECONDS_EXTRA = "bitchord.frameSuiteSeconds"

    private const val SUITE_DIRECTORY = "frame-suite"
    private const val SUITE_TAG = "BitChordFrames"

    /** The modes a suite of `all` runs: everything, then each stage dropped, then no blur at all. */
    val SUITE_ALL = listOf(
        DiagnosticMode.BASELINE,
        DiagnosticMode.NO_GLASS_TONE,
        DiagnosticMode.LEGACY_GLASS_RIM,
        DiagnosticMode.NO_GLASS_RIM,
        DiagnosticMode.NO_GLASS_LENS,
        DiagnosticMode.NO_TOP_FADE,
        DiagnosticMode.NO_BLUR,
    )

    /** Starts a suite from an intent carrying [SUITE_EXTRA], if it does. */
    fun handleSuiteIntent(context: Context, intent: android.content.Intent?) {
        if (!BuildConfig.PERFORMANCE_DIAGNOSTICS) return
        val spec = intent?.getStringExtra(SUITE_EXTRA) ?: return
        intent.removeExtra(SUITE_EXTRA)
        val modes = if (spec == "all") SUITE_ALL else spec.split(',').mapNotNull { name ->
            DiagnosticMode.entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
        }
        val seconds = intent.getIntExtra(SUITE_SECONDS_EXTRA, 20).coerceIn(5, 120)
        if (modes.isNotEmpty()) runSuite(context, modes, seconds * 1_000L)
    }
    private const val WARMUP_MS = 3_000L
    private const val RECORD_MS = 45_000L
}
