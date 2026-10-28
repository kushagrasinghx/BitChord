package com.music.bitchord.ui.components

import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.performance.FrameDiagnostics
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.ceil
import kotlin.math.pow

/**
 * Stable foreground tint for glass labels and icons: black in the light theme,
 * white in the dark theme. What a [GlassTone] starts from, and the tint
 * wherever nothing reads what the glass shows.
 */
@Composable
fun glassContentColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.Black else Color.White

/**
 * Glyph and label colour on glass read by [tone], for content that can only
 * take its colour as it is composed — a theme handed to a screen's own
 * controls: white, or black over light content, cut over when it turns,
 * which recomposes that content once. Content drawn here takes
 * [glassContentProducer] instead and crossfades. The theme's
 * [glassContentColor] without a tone.
 */
@Composable
fun glassContentColor(tone: GlassTone?): Color =
    when {
        tone == null -> glassContentColor()
        tone.dark -> Color.Black
        else -> Color.White
    }

/**
 * [tone]'s glyph colour at [alpha], read where the glyphs are drawn: white,
 * or black while the glass under them shows something light enough that
 * white would sink into it, the way Apple's glass turns its glyphs —
 * crossfaded when it turns, a redraw of the glyphs and no recomposition.
 * The theme's [glassContentColor] without a tone.
 */
@Composable
fun glassContentProducer(tone: GlassTone?, alpha: Float = 1f): ColorProducer {
    val theme = glassContentColor()
    return remember(tone, alpha, theme) {
        if (tone == null) {
            val color = theme.copy(alpha = theme.alpha * alpha)
            ColorProducer { color }
        } else {
            ColorProducer { tone.color(alpha) }
        }
    }
}

/**
 * [vibrancy] for glyphs [tone] colours: added onto the glass while they are
 * light and laid over it once they are dark — plus-lighter adds next to
 * nothing of black — switched in the glyphs' own layer halfway through a
 * turn, rather than by recomposing them. [vibrancy] of [color] without a tone.
 */
fun Modifier.glassVibrancy(tone: GlassTone?, color: Color): Modifier =
    if (tone == null) {
        vibrancy(color)
    } else {
        graphicsLayer { blendMode = if (tone.darkness < 0.5f) BlendMode.Plus else BlendMode.SrcOver }
    }

/**
 * Whether the glyphs on a piece of glass are dark, from what the glass shows
 * under them: its group's [GlassStrip] — the very sample the glass bends —
 * read a few times a second by the window's [GlassToneSampler], at its
 * brighter end. Goes dark above one brightness and light again only below a
 * lower one, so content hovering around the line does not set it flickering.
 *
 * Read where [glassToneArea] marks: what is under the glyphs, not the whole
 * control round them, which averaged a bright patch under a glyph away into
 * the dark round it.
 */
@Stable
class GlassTone internal constructor(internal val strip: GlassStrip, private val light: Boolean) {
    /** Whether the glyphs are dark, or turning so. Light glass starts there; dark glass starts white. */
    var dark by mutableStateOf(light)
        private set

    private val progress = Animatable(if (light) 1f else 0f)

    /**
     * How far the glyphs have turned dark, 0 to 1, crossfading as they turn.
     * Read where they are drawn: there, a turn redraws them and recomposes
     * nothing — read as they were composed, every frame of every crossfade
     * recomposed whatever read it, the whole mini player among them.
     */
    val darkness: Float get() = progress.value

    /** The glyphs' colour at this moment, white turning black, at [alpha]. Read where they are drawn. */
    fun color(alpha: Float = 1f): Color = lerp(Color.White, Color.Black, darkness).copy(alpha = alpha)

    /** Where the glyphs are. Plain rather than state: read only when sampled. */
    internal var area: LayoutCoordinates? = null

    private var turnedAt = 0L

    /**
     * What the strip shows under the glyphs: relative luminance, at its
     * brighter end. A tone that turned holds for [TURN_HOLD_MS] before it
     * turns back, so a fling past light and dark covers does not keep
     * restarting the crossfade.
     */
    internal fun offer(under: Float) {
        val glass = glassLuminanceOver(under, light)
        val next = when {
            glass > DARK_CONTENT_ABOVE -> true
            glass < LIGHT_CONTENT_BELOW -> false
            else -> dark
        }
        if (next == dark) return
        val now = SystemClock.uptimeMillis()
        if (now - turnedAt < TURN_HOLD_MS) return
        turnedAt = now
        dark = next
        FrameDiagnostics.mark("BitChord.glassToneTurn")
    }

    /** Crossfades [darkness] after [dark] for as long as it is called. */
    internal suspend fun follow() {
        snapshotFlow { dark }.collectLatest { target ->
            progress.animateTo(if (target) 1f else 0f, tween(CONTENT_CROSSFADE_MS))
        }
    }
}

/**
 * A tone for glyphs on glass sampling [strip] — light glass if [light] — or
 * null where nothing reads it: no strip, or no sampler (glass off, dynamic
 * blur reduced). Mark the glyphs with [glassToneArea].
 */
@Composable
fun rememberGlassTone(
    strip: GlassStrip? = LocalGlassStrip.current,
    light: Boolean = MaterialTheme.colorScheme.surface.luminance() > 0.5f,
): GlassTone? {
    val sampler = LocalGlassToneSampler.current
    if (sampler == null || strip == null) return null
    val tone = remember(strip, light) { GlassTone(strip, light) }
    // Light glass veils what is under it in white, never dim enough for its
    // dark glyphs to turn light: nothing to read.
    if (!light) {
        DisposableEffect(sampler, tone) {
            sampler.add(tone)
            onDispose { sampler.remove(tone) }
        }
        LaunchedEffect(tone) { tone.follow() }
    }
    return tone
}

/** Marks this as the glyphs [tone] reads under. */
fun Modifier.glassToneArea(tone: GlassTone?): Modifier =
    if (tone == null) this else this then GlassToneAreaElement(tone)

private data class GlassToneAreaElement(val tone: GlassTone) : ModifierNodeElement<GlassToneAreaNode>() {
    override fun create() = GlassToneAreaNode(tone)

    override fun update(node: GlassToneAreaNode) {
        node.tone = tone
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "glassToneArea"
    }
}

/** Hands [tone] its glyphs' layout once placed — a reference, not a write each frame. */
private class GlassToneAreaNode(tone: GlassTone) : LayoutAwareModifierNode, Modifier.Node() {
    private var coordinates: LayoutCoordinates? = null

    var tone: GlassTone = tone
        set(value) {
            if (field === value) return
            if (field.area === coordinates) field.area = null
            field = value
            coordinates?.let { value.area = it }
        }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        tone.area = coordinates
    }

    override fun onDetach() {
        if (tone.area === coordinates) tone.area = null
        coordinates = null
    }
}

/** The window's [GlassToneSampler], where glyphs on glass are read. */
internal val LocalGlassToneSampler = staticCompositionLocalOf<GlassToneSampler?> { null }

/**
 * The window's sampler, while liquid glass is on and sampling — null otherwise,
 * and while a frame recording prices the window without it.
 */
@Composable
internal fun rememberGlassToneSampler(liquidGlassEnabled: Boolean): GlassToneSampler? {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    if (!liquidGlassEnabled || !isGlassSupported() || reduceDynamicBlur || !glassToneForDiagnostics.value) return null
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val sampler = remember(view) { GlassToneSampler(view) }
    DisposableEffect(sampler, lifecycle) {
        sampler.attach()
        val observer = LifecycleEventObserver { _, _ ->
            sampler.setResumed(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            sampler.release()
        }
    }
    return sampler
}

/**
 * Set by a frame recording that prices the window without the sampler. State,
 * so the window drops it and every tone falls back to the theme's colour.
 */
internal val glassToneForDiagnostics = mutableStateOf(true)

/**
 * Reads what the glass shows under every [GlassTone] in a window, a few times
 * a second.
 *
 * The way Android's own gesture-nav handle tells light content from dark, and
 * none of the ways the sampler this replaces did. That one replayed the whole
 * page into a probe every 150ms, rendered it with the main thread waiting on
 * the render thread, copied the hardware bitmap back to the CPU on the main
 * thread — a wait on the GPU — and sorted every control's region there, while
 * each control wrote its bounds to state on every frame it moved.
 *
 * Here each group's [GlassStrip] — already sampled, saturated and blurred
 * this frame, a third of the screen's resolution, and exactly what its glass
 * shows — is drawn at [SAMPLE_SCALE] into one small atlas by a renderer of the
 * sampler's own, into an [ImageReader]. Only drawing the strips' finished
 * layers, at the start of a frame, when the render thread is usually idle:
 * the main thread pays for a sync of a handful of draw calls. The pixels are
 * read, and every tone's region worked out, on a thread of its own, which is
 * where the wait on the GPU lands; the main thread hears only the answers, and
 * a tone recomposes anything only when it turns. Nothing is sampled while the
 * window has drawn nothing since the last sample.
 */
internal class GlassToneSampler(private val view: View) {
    private val main = Handler(Looper.getMainLooper())
    private val tones = ArrayList<GlassTone>() // Main-thread confined.
    private val root = RenderNode("glassTone")
    private val drawScope = CanvasDrawScope()

    private var worker: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var renderer: HardwareRenderer? = null
    private var reader: ImageReader? = null
    private var readerWidth = 0
    private var readerHeight = 0

    private var attached = false
    private var resumed = false
    private var scheduled = false
    /** When the request still being read was made, or 0 with none. */
    private var inFlightSince = 0L
    /**
     * Whether the window has drawn since the last sample: nothing under the
     * glass can have changed otherwise. A window at rest is not sampled at all,
     * and its first frame of moving arms the next sample.
     */
    private var drawn = true
    private val onDraw = ViewTreeObserver.OnDrawListener {
        drawn = true
        schedule(SAMPLE_INTERVAL_MS)
    }

    /** The atlas the request in flight was drawn into, read on the worker. */
    @Volatile private var pending: Pending? = null

    fun add(tone: GlassTone) {
        tones += tone
        // A first reading straight away rather than at the next interval.
        drawn = true
        schedule(0L)
    }

    fun remove(tone: GlassTone) {
        tones -= tone
    }

    fun attach() {
        if (attached) return
        attached = true
        worker = HandlerThread("glass-tone", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
        workerHandler = Handler(worker!!.looper)
        view.viewTreeObserver.addOnDrawListener(onDraw)
        schedule(0L)
    }

    fun setResumed(resumed: Boolean) {
        this.resumed = resumed
        if (resumed) schedule(0L)
    }

    fun release() {
        if (!attached) return
        attached = false
        main.removeCallbacks(tick)
        Choreographer.getInstance().removeFrameCallback(frame)
        scheduled = false
        view.viewTreeObserver.takeIf { it.isAlive }?.removeOnDrawListener(onDraw)
        renderer?.destroy()
        renderer = null
        val reader = reader
        this.reader = null
        readerWidth = 0
        readerHeight = 0
        pending = null
        inFlightSince = 0L
        // On the worker, after anything it is still reading out of the reader.
        workerHandler?.post { reader?.close() }
        worker?.quitSafely()
        worker = null
        workerHandler = null
    }

    private fun schedule(delayMs: Long) {
        if (scheduled || !attached || !resumed || tones.isEmpty()) return
        scheduled = true
        main.postDelayed(tick, delayMs)
    }

    // At the start of a frame: the render thread has usually finished the one
    // before by then, so the sync below does not queue behind it.
    private val tick = Runnable { Choreographer.getInstance().postFrameCallback(frame) }

    private val frame = Choreographer.FrameCallback {
        scheduled = false
        if (!attached || !resumed || tones.isEmpty() || !drawn) return@FrameCallback
        FrameDiagnostics.measure("BitChord.glassTone") { sample() }
        // Held back by a request still in flight: again shortly.
        if (drawn) schedule(SAMPLE_INTERVAL_MS)
    }

    private fun sample() {
        val now = SystemClock.uptimeMillis()
        if (inFlightSince != 0L && now - inFlightSince < STUCK_MS) return
        drawn = false

        val layers = ArrayList<Pair<GraphicsLayer, Int>>()
        val areas = ArrayList<Area>()
        var width = 0
        var height = 0
        for ((strip, group) in tones.groupBy { it.strip }) {
            val layer = strip.layer?.takeUnless { it.isReleased } ?: continue
            val stripCoordinates = strip.coordinates?.takeIf { it.isAttached } ?: continue
            val w = ceil(layer.size.width * SAMPLE_SCALE).toInt()
            val h = ceil(layer.size.height * SAMPLE_SCALE).toInt()
            if (w <= 0 || h <= 0) continue
            val top = height
            // Strip pixels to the layer's — see [GlassStrip] — then to the atlas's.
            val k = strip.scale * SAMPLE_SCALE
            val pad = strip.padding * SAMPLE_SCALE
            var any = false
            for (tone in group) {
                val coordinates = tone.area?.takeIf { it.isAttached } ?: continue
                val box = try {
                    stripCoordinates.localBoundingBoxOf(coordinates, clipBounds = false)
                } catch (_: Exception) {
                    continue
                }
                val left = (box.left * k + pad).toInt().coerceIn(0, w - 1)
                val right = ceil(box.right * k + pad).toInt().coerceIn(left + 1, w)
                val t = (box.top * k + pad).toInt().coerceIn(0, h - 1)
                val bottom = ceil(box.bottom * k + pad).toInt().coerceIn(t + 1, h)
                areas += Area(tone, left, top + t, right, top + bottom)
                any = true
            }
            if (!any) continue
            layers += layer to top
            width = maxOf(width, w)
            height += h
        }
        if (layers.isEmpty()) return

        val renderer = target(width, height) ?: return
        root.setPosition(0, 0, width, height)
        val canvas = root.beginRecording(width, height)
        try {
            drawScope.draw(UnitDensity, LayoutDirection.Ltr, Canvas(canvas), Size(width.toFloat(), height.toFloat())) {
                for ((layer, top) in layers) {
                    translate(0f, top.toFloat()) {
                        scale(SAMPLE_SCALE, SAMPLE_SCALE, pivot = Offset.Zero) { drawLayer(layer) }
                    }
                }
            }
        } finally {
            root.endRecording()
        }
        pending = Pending(reader!!, areas)
        inFlightSince = now
        val result = renderer.createRenderRequest().setVsyncTime(System.nanoTime()).syncAndDraw()
        val drewNothing = HardwareRenderer.SYNC_LOST_SURFACE_REWARD_IF_FOUND or
            HardwareRenderer.SYNC_CONTEXT_IS_STOPPED or HardwareRenderer.SYNC_FRAME_DROPPED
        if (result and drewNothing != 0) {
            pending = null
            inFlightSince = 0L
        }
    }

    /** The renderer, drawing into a reader at least [width] × [height]. */
    private fun target(width: Int, height: Int): HardwareRenderer? {
        val handler = workerHandler ?: return null
        if (reader == null || width > readerWidth || height > readerHeight) {
            val old = reader
            // Rounded up, so a bar whose size follows a scroll keeps its reader.
            val w = roundUp(maxOf(width, readerWidth))
            val h = roundUp(maxOf(height, readerHeight))
            val fresh = ImageReader.newInstance(
                w, h, PixelFormat.RGBA_8888, 2,
                HardwareBuffer.USAGE_CPU_READ_OFTEN or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
            )
            fresh.setOnImageAvailableListener(::read, handler)
            val renderer = renderer ?: HardwareRenderer().also {
                it.setContentRoot(root)
                it.isOpaque = false
                renderer = it
            }
            renderer.setSurface(fresh.surface)
            reader = fresh
            readerWidth = w
            readerHeight = h
            handler.post { old?.close() }
        }
        return renderer
    }

    /** On the worker: the atlas back, each tone's region read off it. */
    private fun read(source: ImageReader) {
        val image = try {
            source.acquireLatestImage()
        } catch (_: Exception) {
            null
        } ?: return
        val job = pending?.takeIf { it.reader === source }
        val results = try {
            job?.let { luminances(image, it) }
        } catch (_: Exception) {
            null
        } finally {
            image.close()
        }
        main.post { finish(job, results) }
    }

    private fun finish(job: Pending?, results: FloatArray?) {
        if (job == null || job !== pending) return
        pending = null
        inFlightSince = 0L
        if (results == null) return
        job.areas.forEachIndexed { index, area ->
            if (area.tone in tones) area.tone.offer(results[index])
        }
    }

    private var scratch = FloatArray(256)

    /**
     * Each area's relative luminance at its brighter end: the value
     * [BRIGHT_SHARE] of it is at or above. Not the mean — a patch a quarter of
     * the region wide is enough to turn the glyphs over it.
     */
    private fun luminances(image: Image, job: Pending): FloatArray {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        return FloatArray(job.areas.size) { index ->
            val area = job.areas[index]
            val count = (area.right - area.left) * (area.bottom - area.top)
            if (scratch.size < count) scratch = FloatArray(count)
            var n = 0
            for (y in area.top until minOf(area.bottom, image.height)) {
                var offset = y * rowStride + area.left * pixelStride
                for (x in area.left until minOf(area.right, image.width)) {
                    val r = buffer.get(offset).toInt() and 0xFF
                    val g = buffer.get(offset + 1).toInt() and 0xFF
                    val b = buffer.get(offset + 2).toInt() and 0xFF
                    scratch[n++] = 0.2126f * SRGB_TO_LINEAR[r] + 0.7152f * SRGB_TO_LINEAR[g] + 0.0722f * SRGB_TO_LINEAR[b]
                    offset += pixelStride
                }
            }
            if (n == 0) return@FloatArray 0f
            java.util.Arrays.sort(scratch, 0, n)
            scratch[((n - 1) * (1f - BRIGHT_SHARE)).toInt()]
        }
    }

    private class Area(val tone: GlassTone, val left: Int, val top: Int, val right: Int, val bottom: Int)

    private class Pending(val reader: ImageReader, val areas: List<Area>)
}

private val UnitDensity = Density(1f)

private fun roundUp(value: Int): Int = (value + 31) / 32 * 32

/** sRGB-encoded channel to linear light, for relative luminance. */
private val SRGB_TO_LINEAR = FloatArray(256) { channel ->
    val c = channel / 255f
    if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
}

/**
 * The fraction of the strip's own resolution the atlas samples at: a sixth of
 * the screen's, about 2dp a sample at 3x — finer than a glyph's stroke, and
 * the strip is blurred anyway.
 */
private const val SAMPLE_SCALE = 0.5f

/** How often the glass is read: well inside the crossfade, so glyphs keep up with a scroll. */
private const val SAMPLE_INTERVAL_MS = 200L

/** A request that has not come back by then never will; the next one goes ahead. */
private const val STUCK_MS = 1_000L

/** How much of a region has to be bright for its glyphs to turn dark. */
private const val BRIGHT_SHARE = 0.25f

/** How bright the glass has to show for its glyphs to go dark, and how dim for them to go light again. */
private const val DARK_CONTENT_ABOVE = 0.5f
private const val LIGHT_CONTENT_BELOW = 0.35f

private const val CONTENT_CROSSFADE_MS = 300

/** How long a tone that turned holds before it may turn back. */
private const val TURN_HOLD_MS = 600L
