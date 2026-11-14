package com.music.bitchord.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawModifierNode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil

/**
 * The edge of Apple's Liquid Glass, drawn over a surface of [shape].
 *
 * Three parts, outermost first:
 *  - a dark outline just outside the edge, a crescent round each end over a
 *    faint line along the flat run between, where the rim draws the edge;
 *  - a bright rim along the top and, mirrored, along the bottom, at its
 *    thickest where the edge runs flat and thinning to nothing round the
 *    corners, where the edge turns vertical;
 *  - a soft glow under that rim: the same rim, deeper, blurred well out and
 *    much fainter.
 *
 * Rim and glow are lit from what is beneath them. Each multiplies the colour
 * there up — color dodge against a grey, which is a gain on every channel —
 * and only then adds a little white. Added white alone, the obvious way,
 * raises every channel by the same amount: it keeps the hue but bleaches it,
 * so a red sleeve got a pale pink edge. A gain keeps the channels' ratios, so
 * red brightens as red, which is the edge Apple's glass has. The white is the
 * floor for what a gain cannot lift — over black there is nothing to multiply.
 *
 * The rim is cut the way Apple's is built — the shape less a copy of itself
 * shifted by the rim's depth, once down for the top and once up for the
 * bottom. Where the edge is horizontal that leaves the full depth; where it
 * turns, it leaves the depth times how far the edge still faces up or down,
 * which is the taper, from the shape's own geometry, at any size. Unlike the
 * backdrop library's [com.music.bitchord.ui.components.backdrop.highlight.Highlight],
 * which lights the edge from one diagonal, it is symmetrical top to bottom.
 *
 * The outline is drawn outside the shape, as Apple's design files draw it, and
 * the rim and glow inside it, from the edge in. Drawn inside, the outline had
 * to take the shape's outermost band and the rim to start beneath it, so where
 * the outline faded out the rim showed the full depth of both, as a solid
 * white band. So a glass surface has to be clipped to its shape after this,
 * never before: a clip ahead of it cuts the outline away.
 *
 * Neither Android nor Skia has plus-darker, which for black at alpha `a`
 * subtracts — `max(0, d - a)` — so the outline is black drawn normally, which
 * scales instead, `d * (1 - a)`: the same on a dark backdrop, where both come
 * out near black, and a shade lighter on a pale one. Working Apple's formula
 * per pixel takes a layer and a shader pass over every glass surface on every
 * frame it changes, and the bars change on every frame of a scroll.
 *
 * [alpha] scales all three, read at draw time, so a surface can fade its rim
 * in and out without recomposing.
 */
internal fun Modifier.glassRim(
    shape: Shape,
    /** Off for glass whose lights are drawn in its own shader — see [glassSurface]: the outline alone. */
    lights: Boolean = true,
    alpha: () -> Float = FullRim,
): Modifier = this.then(GlassRimElement(shape, alpha, lights))

private data class GlassRimElement(
    val shape: Shape,
    val alpha: () -> Float,
    val lights: Boolean,
) : ModifierNodeElement<GlassRimNode>() {
    override fun create() = GlassRimNode(shape, alpha, lights)
    override fun update(node: GlassRimNode) = node.update(shape, alpha, lights)
    override fun InspectorInfo.inspectableProperties() { name = "glassRim" }
}

private class GlassRimNode(
    private var shape: Shape,
    private var alpha: () -> Float,
    private var lights: Boolean,
) : DelegatingNode() {
    // Updating an opacity callback should redraw, not rebuild paths, blur
    // filters and gradients. Size, density and layout direction changes are
    // still handled by the delegated cache node.
    override val shouldAutoInvalidate = false

    // The bake to draw from is looked up on every draw rather than held: a
    // bar's size follows the scroll, so the bake that suits it changes with it,
    // and [GlassRimMasks.forSurface] keeps the one cache for every surface.
    private fun bakedFor(size: Size, direction: LayoutDirection, density: Density): GlassRimMasks? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return GlassRimMasks.forSurface(shape, size, direction, density)
    }

    private val drawing: CacheDrawModifierNode = delegate(CacheDrawModifierNode {
        // A frame recording can draw the rim the old way, to price the bake
        // against it in the same run.
        if (!legacyRimForDiagnostics.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val size = size
            val direction = layoutDirection
            val density: Density = this
            // Every shape from a bake: a pill stretched along its middle, and
            // one narrower than two caps — a circle, a pill of two buttons —
            // baked at its own width. The outline drawn from a layer of its own,
            // below, never showed on those; it stays for a recording to compare.
            run {
                return@CacheDrawModifierNode onDrawWithContent {
                    drawContent()
                    val strength = alpha().coerceIn(0f, 1f)
                    if (strength <= 0f) return@onDrawWithContent
                    val baked = bakedFor(size, direction, density)
                    if (baked != null) {
                        drawIntoCanvas { baked.draw(it.nativeCanvas, size, strength, lights) }
                    } else {
                        // No bake will fit yet: draw this frame exactly.
                        val geometry = GlassRimGeometry(shape, size, direction, density)
                        if (lights) {
                            clipPath(geometry.body) {
                                drawIntoCanvas { geometry.drawLights(it.nativeCanvas, strength) }
                            }
                        }
                        geometry.drawOutline(this, strength)
                    }
                }
            }
        }
        val geometry = GlassRimGeometry(shape, size, layoutDirection, this)
        // The outline, rasterised once per size into a layer of its own and
        // only composited after that. It is plain black over whatever is
        // outside the shape, so nothing about it changes from frame to frame
        // but its strength, which is the layer's alpha; drawn directly it was
        // an anti-aliased clip to the outside of the shape and a gradient
        // stroke on every frame the bar redrew. The lights stay direct: they
        // multiply what is under them, and that does change.
        val margin = ceil(GLASS_OUTLINE_WIDTH.toPx()) + OUTLINE_LAYER_SLACK_PX
        val outline = obtainGraphicsLayer().apply {
            compositingStrategy = CompositingStrategy.Offscreen
            record(size = IntSize(ceil(size.width + margin * 2).toInt(), ceil(size.height + margin * 2).toInt())) {
                translate(margin, margin) { geometry.drawOutline(this, 1f) }
            }
            topLeft = IntOffset(-margin.toInt(), -margin.toInt())
        }
        onDrawWithContent {
            drawContent()
            val strength = alpha().coerceIn(0f, 1f)
            if (strength <= 0f) return@onDrawWithContent
            if (lights) {
                clipPath(geometry.body) {
                    drawIntoCanvas {
                        geometry.drawLights(it.nativeCanvas, strength)
                    }
                }
            }
            outline.alpha = strength
            drawLayer(outline)
        }
    })

    fun update(shape: Shape, alpha: () -> Float, lights: Boolean) {
        if (this.shape != shape || this.lights != lights) {
            this.shape = shape
            this.lights = lights
            drawing.invalidateDrawCache()
        }
        if (this.alpha !== alpha) {
            this.alpha = alpha
            drawing.invalidateDraw()
        }
    }
}

/** Also used by offscreen render checks, without constructing an app screen. */
internal class GlassRimGeometry(shape: Shape, size: Size, direction: LayoutDirection, density: Density) {
    val body: Path
    private val rim: Path
    private val glow: Path
    private val rimGain: Paint
    private val rimLight: Paint
    private val glowGain: Paint
    private val glowLight: Paint
    private val outline: Stroke
    private val outlineBrush: Brush
    private var lastStrength = Float.NaN

    init {
        with(density) {
            body = Path().apply { addOutline(shape.createOutline(size, direction, density)) }
            rim = rims(body, GLASS_RIM_DEPTH.toPx())
            glow = rims(body, GLASS_GLOW_DEPTH.toPx())
            val rimBlur = GLASS_RIM_BLUR.toPx()
            val glowBlur = GLASS_GLOW_BLUR.toPx()
            // The rim's own colour comes from a top-to-bottom gradient rather than
            // the paint, so it can run out before it wraps too far down the ends.
            rimGain = blurredPaint(BlendMode.ColorDodge, rimBlur).apply {
                shader = rimReach(size.height, gainGrey(GLASS_RIM_GAIN))
            }
            rimLight = blurredPaint(BlendMode.Plus, rimBlur).apply {
                shader = rimReach(size.height, Color.White)
            }
            glowGain = blurredPaint(BlendMode.ColorDodge, glowBlur)
            glowLight = blurredPaint(BlendMode.Plus, glowBlur)
            // Twice the width, centred on the edge and clipped to the outside half.
            outline = Stroke(width = GLASS_OUTLINE_WIDTH.toPx() * 2f)
            outlineBrush = Brush.horizontalGradient(
                *outlineStops(size).map { (at, opacity) -> at to Color.Black.copy(alpha = opacity) }.toTypedArray(),
                startX = 0f,
                endX = size.width,
            )
        }
    }

    private fun updateStrength(strength: Float) {
        if (lastStrength == strength) return
        lastStrength = strength
        glowGain.color = gainGrey(GLASS_GLOW_GAIN).copy(alpha = strength)
        glowLight.color = Color.White.copy(alpha = GLASS_GLOW_ALPHA * strength)
        rimGain.alpha = strength
        rimLight.alpha = GLASS_RIM_ALPHA * strength
    }

    fun drawLights(canvas: android.graphics.Canvas, strength: Float) {
        updateStrength(strength)
        canvas.drawPath(glow.asAndroidPath(), glowGain.asFrameworkPaint())
        canvas.drawPath(glow.asAndroidPath(), glowLight.asFrameworkPaint())
        canvas.drawPath(rim.asAndroidPath(), rimGain.asFrameworkPaint())
        canvas.drawPath(rim.asAndroidPath(), rimLight.asFrameworkPaint())
    }

    fun drawOutline(scope: DrawScope, strength: Float) = with(scope) {
        clipPath(body, ClipOp.Difference) {
            drawPath(body, outlineBrush, alpha = strength, style = outline)
        }
    }

    /** The rim's coverage, white at full strength, for [GlassRimMasks] to tint. */
    fun drawRimMask(canvas: android.graphics.Canvas) {
        canvas.drawPath(rim.asAndroidPath(), rimMask.asFrameworkPaint())
    }

    /** The glow's coverage, white at full strength, for [GlassRimMasks] to tint. */
    fun drawGlowMask(canvas: android.graphics.Canvas) {
        canvas.drawPath(glow.asAndroidPath(), glowMask.asFrameworkPaint())
    }

    // The lights' own paints, plain white and drawn normally: what each one
    // covers, before its colour, its blend and its strength are put on it.
    private val rimMask = blurredPaint(BlendMode.SrcOver, with(density) { GLASS_RIM_BLUR.toPx() }).apply {
        shader = rimReach(size.height, Color.White)
    }
    private val glowMask = blurredPaint(BlendMode.SrcOver, with(density) { GLASS_GLOW_BLUR.toPx() }).apply {
        color = Color.White
    }
}

/**
 * A pill's rim, glow and outline, rasterised once for one [height] and then
 * drawn at any width.
 *
 * A pill is the same two end caps whatever its width, with a straight run
 * between that only gets longer; so is everything [GlassRimGeometry] draws
 * along it, the outline's darkening at the ends included, which is measured
 * from the ends rather than across the width. Each part is baked once at a
 * width of three caps and drawn as a three-column mesh — the end columns as
 * they are, the middle stretched to whatever is between them. Built per
 * width, a bar folding had its paths cut and its blurs redone on every frame
 * of the fold, on the main thread and again on the render thread; baked, a
 * frame of it is five textured draws in the blends the paths were drawn in.
 *
 * Off a height it was baked for — mid-fold, when the height animates too —
 * it is scaled evenly to the new one, caps and all, until the size settles
 * and it is baked again.
 */
@RequiresApi(Build.VERSION_CODES.P)
internal class GlassRimMasks(
    val shape: Shape,
    val height: Float,
    val direction: LayoutDirection,
    density: Density,
    /**
     * Baked at exactly this width rather than three caps across, for a shape
     * too narrow to have a straight run to stretch — a circle — which is then
     * only ever drawn at this size.
     */
    val exactWidth: Float? = null,
) {
    val density = density.density

    /** An end cap: the shape's own turn, a height across, and the glow's blur reaching past it. */
    val cap = if (exactWidth != null) exactWidth / 2f else ceil(capWidth(height, density))
    val margin = ceil(with(density) { GLASS_OUTLINE_WIDTH.toPx() }) + OUTLINE_LAYER_SLACK_PX
    private val bitmapWidth = if (exactWidth != null) ceil(exactWidth + margin * 2) else (cap + margin) * 3
    private val bitmapHeight = ceil(height + margin * 2)

    /** The width the parts are drawn at: three caps across — caps and a straight run of a cap — or [exactWidth]. */
    val bakedWidth = exactWidth ?: (bitmapWidth - margin * 2)

    private val bakedSize = Size(bakedWidth, height)
    private val geometry by lazy { GlassRimGeometry(shape, bakedSize, direction, density) }
    private val densityFull: Density = Density(density.density, density.fontScale)

    // Each part only once something draws it: the drawn rim wants the lights
    // and the outline, the shader the packed texture and the outline.
    private val rim by lazy { bake { canvas -> canvas.clipPath(geometry.body.asAndroidPath()); geometry.drawRimMask(canvas) } }
    private val glow by lazy { bake { canvas -> canvas.clipPath(geometry.body.asAndroidPath()); geometry.drawGlowMask(canvas) } }
    private val body by lazy {
        bake { canvas ->
            canvas.drawPath(geometry.body.asAndroidPath(), android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
        }
    }
    private val outline by lazy {
        bake { canvas ->
            CanvasDrawScope().draw(densityFull, direction, androidx.compose.ui.graphics.Canvas(canvas), bakedSize) {
                geometry.drawOutline(this, 1f)
            }
        }
    }

    /**
     * The rim, the glow and the shape's own coverage, one to a channel —
     * red, green, blue — of an opaque texture, for [glassSurface] to read.
     */
    val packed: Bitmap by lazy {
        val w = bitmapWidth.toInt()
        val h = bitmapHeight.toInt()
        val r = IntArray(w * h).also { rim.getPixels(it, 0, w, 0, 0, w, h) }
        val g = IntArray(w * h).also { glow.getPixels(it, 0, w, 0, 0, w, h) }
        val b = IntArray(w * h).also { body.getPixels(it, 0, w, 0, 0, w, h) }
        val out = IntArray(w * h) { i ->
            (0xFF shl 24) or ((r[i] ushr 24) shl 16) or ((g[i] ushr 24) shl 8) or (b[i] ushr 24)
        }
        Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Renders every part now, for a bake made off the main thread to hand over finished. */
    fun warm() {
        rim; glow; outline; packed
    }

    /** How [glassSurface] maps a surface of some size onto this bake. */
    data class Layout(
        val cap: Float,
        val capOnSurface: Float,
        val bakedWidth: Float,
        val margin: Float,
        val scale: Float,
        val squash: Boolean,
    )

    fun layoutFor(size: Size): Layout {
        val k = size.height / height
        val capOnSurface = cap * k
        val squash = if (exactWidth != null) {
            kotlin.math.abs(size.width - exactWidth * k) > 0.5f
        } else {
            size.width < capOnSurface * 2f
        }
        return Layout(cap, capOnSurface, bakedWidth, margin, k, squash)
    }

    private val verts = FloatArray(16)
    private val glowGain = maskPaint(android.graphics.BlendMode.COLOR_DODGE, gainGrey(GLASS_GLOW_GAIN))
    private val glowLight = maskPaint(android.graphics.BlendMode.PLUS, null)
    private val rimGain = maskPaint(android.graphics.BlendMode.COLOR_DODGE, gainGrey(GLASS_RIM_GAIN))
    private val rimLight = maskPaint(android.graphics.BlendMode.PLUS, null)
    private val outlinePaint = maskPaint(android.graphics.BlendMode.SRC_OVER, null)

    /** Whether these masks stretch to [size] — as wide as two of their caps at its height. */
    fun fits(size: Size): Boolean = exactWidth == null && size.width >= cap * 2f * (size.height / height)

    /** The rim at [size], in [GlassRimGeometry.drawLights]'s order, then the outline — or the outline alone. */
    fun draw(canvas: android.graphics.Canvas, size: Size, strength: Float, lights: Boolean = true) {
        val k = size.height / height
        val marginK = margin * k
        if (exactWidth != null) {
            // Nothing to stretch: the three columns evenly over the whole bake.
            val kx = size.width / exactWidth
            val span = (bitmapWidth / 3f) * kx
            val x0 = -margin * kx
            for (c in 0..3) xs[c] = x0 + span * c
        } else {
            // A stand-in a touch wider in the caps than this surface leaves no
            // middle: the caps meet in it rather than cross.
            val capK = minOf(cap * k, size.width / 2f)
            xs[0] = -marginK; xs[1] = capK; xs[2] = size.width - capK; xs[3] = size.width + marginK
        }
        val ys0 = -marginK
        val ys1 = size.height + marginK
        var i = 0
        for (y in floatArrayOf(ys0, ys1)) for (x in xs) { verts[i++] = x; verts[i++] = y }
        val alpha = (strength * 255f).toInt().coerceIn(0, 255)
        if (lights) {
            val lightAlpha = (strength * GLASS_RIM_ALPHA * 255f).toInt().coerceIn(0, 255)
            val glowAlpha = (strength * GLASS_GLOW_ALPHA * 255f).toInt().coerceIn(0, 255)
            mesh(canvas, glow, glowGain, alpha)
            mesh(canvas, glow, glowLight, glowAlpha)
            mesh(canvas, rim, rimGain, alpha)
            mesh(canvas, rim, rimLight, lightAlpha)
        }
        mesh(canvas, outline, outlinePaint, alpha)
    }

    private val xs = FloatArray(4)

    private fun mesh(canvas: android.graphics.Canvas, bitmap: Bitmap, paint: android.graphics.Paint, alpha: Int) {
        if (alpha <= 0) return
        paint.alpha = alpha
        canvas.drawBitmapMesh(bitmap, 3, 1, verts, 0, null, 0, paint)
    }

    /**
     * Draws [block] at the parts' size, offset by the margin, rasterised by the
     * GPU — so its blurs are the renderer's own and match the paths drawn live
     * — and copied out once into an ordinary bitmap. Drawn as the hardware
     * bitmap itself, every frame that changed the display list holding it
     * re-imported its buffer on the render thread: every frame of a fold.
     */
    private fun bake(block: (android.graphics.Canvas) -> Unit): Bitmap {
        val picture = android.graphics.Picture()
        val canvas = picture.beginRecording(bitmapWidth.toInt(), bitmapHeight.toInt())
        canvas.translate(margin, margin)
        block(canvas)
        picture.endRecording()
        val hardware = Bitmap.createBitmap(picture)
        return hardware.copy(Bitmap.Config.ARGB_8888, false).also { hardware.recycle() }
    }

    /** What a bake is for. Heights are exact: a settled bar sits at exactly its height. */
    data class Key(
        val shape: Shape,
        val height: Float,
        val direction: LayoutDirection,
        val density: Float,
        /** Set for a bake at exactly this width — see [exactWidth]. */
        val width: Float? = null,
    )

    companion object {
        // A handful of heights in all — the tab bar folded and unfolded, the
        // top bar's controls, a segmented control — so a few entries hold them
        // all; least recently used goes first past that.
        private val baked = object : LinkedHashMap<Key, GlassRimMasks>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, GlassRimMasks>) = size > MAX_BAKES
        }

        /**
         * A bake for [key], or one within [BAKE_TOLERANCE] of it. A bar's size
         * follows the scroll rather than snapping between two, so it rests at
         * a new size every time the scroll stops — and baked to the pixel, the
         * rim baked again on every pause. Within a few percent the bake is
         * drawn scaled, which on a rim under a point deep is nothing anyone sees.
         */
        fun cached(key: Key): GlassRimMasks? = baked[key] ?: baked.values.firstOrNull { it.matches(key) }

        private fun GlassRimMasks.matches(key: Key): Boolean =
            shape == key.shape && direction == key.direction && density == key.density &&
                close(height, key.height) &&
                when (val width = key.width) {
                    null -> exactWidth == null
                    else -> exactWidth != null && close(exactWidth, width)
                }

        private fun close(baked: Float, wanted: Float) =
            kotlin.math.abs(baked - wanted) <= wanted * BAKE_TOLERANCE

        fun bake(key: Key, density: Density): GlassRimMasks =
            com.music.bitchord.ui.performance.FrameDiagnostics.measure("BitChord.rimBake") {
                GlassRimMasks(key.shape, key.height, key.direction, density, key.width)
            }.also { baked[key] = it }

        /**
         * Bumped when a bake lands that a surface was waiting on, so whatever
         * read [forSurface] — a glass surface's effects — reads it again.
         */
        val version = androidx.compose.runtime.mutableIntStateOf(0)

        /**
         * The bake a glass surface of [size] draws from: its own if there is
         * one; otherwise, mid-animation, the nearest one, with its own baked
         * once the size holds still for [RIM_SETTLE_MS]; and with nothing to
         * stand in, baked now.
         */
        fun forSurface(shape: Shape, size: Size, direction: LayoutDirection, density: Density): GlassRimMasks {
            version.intValue
            val sliceable = fits(size, density)
            val key = Key(shape, size.height, direction, density.density, if (sliceable) null else size.width)
            cached(key)?.let { return it }
            // A stand-in until this size's own bake lands — never one of the other
            // kind. A stretched pill's caps squeezed into a narrow shape's halves
            // came out pointed at both ends, a rugby ball, for as long as a
            // scroll kept the narrow shape changing size; a narrow shape only
            // borrows a narrow bake near its own proportions, stretched evenly —
            // a little oval for the frames it stands in, never pointed.
            val aspect = size.width / size.height
            fun aspectOff(masks: GlassRimMasks) = kotlin.math.abs(masks.exactWidth!! / masks.height - aspect) / aspect
            val nearest = baked.values
                .filter { it.shape == shape && it.direction == direction && it.density == density.density }
                .filter { (it.exactWidth == null) == sliceable }
                .filter { sliceable || aspectOff(it) <= STAND_IN_ASPECT }
                .minByOrNull { if (sliceable) kotlin.math.abs(it.height - size.height) else aspectOff(it) }
                ?: return bake(key, density).also { it.packed }
            wanted[shape] = key to Density(density.density, density.fontScale)
            wantedAt[shape] = SystemClock.uptimeMillis()
            if (settling.add(shape)) main.postDelayed(Settle(shape), RIM_SETTLE_MS)
            return nearest
        }

        /**
         * Bakes off the main thread: a bake is several GPU renders read back,
         * and on this thread each one landed as a frame over budget. The bake
         * is swapped in when it is ready; until then the nearest stands in.
         */
        private val baker = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
            Thread(task, "glass-rim-bake").apply { priority = Thread.MIN_PRIORITY }
        }

        private fun bakeInBackground(key: Key, density: Density) {
            if (!inFlight.add(key)) return
            baker.execute {
                val masks = runCatching {
                    GlassRimMasks(key.shape, key.height, key.direction, density, key.width).also { it.warm() }
                }.getOrNull()
                main.post {
                    inFlight.remove(key)
                    if (masks != null && cached(key) == null) {
                        baked[key] = masks
                        version.intValue++
                    }
                }
            }
        }

        private val inFlight = HashSet<Key>()

        private val main = android.os.Handler(android.os.Looper.getMainLooper())
        private val wanted = HashMap<Shape, Pair<Key, Density>>()
        private val wantedAt = HashMap<Shape, Long>()
        private val settling = HashSet<Shape>()

        private class Settle(private val shape: Shape) : Runnable {
            override fun run() {
                val at = wantedAt[shape]
                if (at != null && SystemClock.uptimeMillis() - at < RIM_SETTLE_MS) {
                    main.postDelayed(this, RIM_SETTLE_MS)
                    return
                }
                settling.remove(shape)
                wantedAt.remove(shape)
                val (key, density) = wanted.remove(shape) ?: return
                if (cached(key) == null) bakeInBackground(key, density)
            }
        }

        /** Whether a pill of [size] is wide enough to have a middle to stretch. */
        fun fits(size: Size, density: Density): Boolean = size.width >= ceil(capWidth(size.height, density)) * 2f

        private fun capWidth(height: Float, density: Density): Float {
            // The glow's blur, as the sigma BlurMaskFilter turns its radius into, to three sigmas.
            val glowBlur = with(density) { GLASS_GLOW_BLUR.toPx() }
            return height + 3f * (glowBlur * 0.57735f + 0.5f)
        }

        private fun maskPaint(blend: android.graphics.BlendMode, tint: Color?) =
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
                blendMode = blend
                if (tint != null) {
                    colorFilter = android.graphics.PorterDuffColorFilter(tint.toArgb(), android.graphics.PorterDuff.Mode.SRC_IN)
                }
            }
    }
}

/** How long a size must hold before the rim is baked for it again. */
private const val RIM_SETTLE_MS = 120L

private const val MAX_BAKES = 24

/** How far a narrow stand-in's proportions may be from the shape it stands in for. */
private const val STAND_IN_ASPECT = 0.25f

/** How far off a bake's size may be, as a fraction, and still be drawn scaled for another. */
private const val BAKE_TOLERANCE = 0.06f

/**
 * Set by a frame recording that draws the rim the old way, for the cost of the
 * bake to be measured against it in the same run. State, so the rims redraw.
 */
internal val legacyRimForDiagnostics = androidx.compose.runtime.mutableStateOf(false)

private val FullRim = { 1f }

/** Whole pixels around the outline's layer past its stroke, for the anti-aliasing. */
private const val OUTLINE_LAYER_SLACK_PX = 2f

/**
 * The outline's opacity across the surface: [GLASS_OUTLINE_ALPHA] at each end,
 * falling away fast to [GLASS_OUTLINE_MIDDLE_ALPHA] [GLASS_OUTLINE_REACH] of
 * the way across an end cap and holding there between — a crescent tucked
 * round each end over a faint line along the rest.
 */
private fun outlineStops(size: Size): List<Pair<Float, Float>> {
    val reach = (size.minDimension / 2f / size.width).coerceIn(0f, 0.5f) * GLASS_OUTLINE_REACH
    val ends = squaredFade(reach).map { (at, fade) ->
        at to GLASS_OUTLINE_MIDDLE_ALPHA + (GLASS_OUTLINE_ALPHA - GLASS_OUTLINE_MIDDLE_ALPHA) * fade
    }
    return ends + ends.reversed().map { (at, opacity) -> 1f - at to opacity }
}

/**
 * A fade from whole to nothing over `0..[length]`, as stops: squared, so most
 * of what fades is spent at the start, and the tail runs out without a line.
 */
private fun squaredFade(length: Float): List<Pair<Float, Float>> =
    (0..FADE_STOPS).map { i ->
        val t = i / FADE_STOPS.toFloat()
        length * t to (1f - t) * (1f - t)
    }

private const val FADE_STOPS = 4

private fun blurredPaint(blendMode: BlendMode, blur: Float) = Paint().apply {
    this.blendMode = blendMode
    asFrameworkPaint().maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
}

/**
 * [color] down the height of the surface: whole at the top and bottom edges,
 * fading on the outline's own squared curve and gone [GLASS_RIM_REACH] of the
 * way in to the middle from either — so the rim, which thins as the edge turns
 * down an end, runs out smoothly well before it gets there.
 */
private fun rimReach(height: Float, color: Color): Shader {
    val top = squaredFade(GLASS_RIM_REACH / 2f)
    val stops = top + top.reversed().map { (at, fade) -> 1f - at to fade }
    return LinearGradientShader(
        from = Offset.Zero,
        to = Offset(0f, height),
        colors = stops.map { (_, fade) -> color.copy(alpha = fade) },
        colorStops = stops.map { (at, _) -> at },
    )
}

/** The grey that color-dodges whatever is beneath it up by [gain]: `d / (1 - g)`. */
private fun gainGrey(gain: Float): Color = (1f - 1f / gain).let { Color(it, it, it) }

/** The top and bottom rims of [body], each [depth] deep where the edge runs flat. */
private fun rims(body: Path, depth: Float): Path {
    fun shifted(dy: Float) = Path().apply { addPath(body, Offset(0f, dy)) }
    val top = Path().apply { op(body, shifted(depth), PathOperation.Difference) }
    val bottom = Path().apply { op(body, shifted(-depth), PathOperation.Difference) }
    return Path().apply { op(top, bottom, PathOperation.Union) }
}

/** The dark outline: a band just outside the shape (2.3px at 3x), at its darkest at the two ends. */
private val GLASS_OUTLINE_WIDTH = 0.77.dp
private const val GLASS_OUTLINE_ALPHA = 0.35f
private const val GLASS_OUTLINE_MIDDLE_ALPHA = 0.1f

/** How far across an end cap the outline takes to fall to its middle opacity. */
private const val GLASS_OUTLINE_REACH = 0.6f

/**
 * How deep the bright rim is, in from the edge at the middle of a flat run;
 * how far it multiplies the colour beneath; and the white added after.
 */
private val GLASS_RIM_DEPTH = 0.83.dp
private const val GLASS_RIM_GAIN = 2.4f
private const val GLASS_RIM_ALPHA = 0.07f

/**
 * How far the rim runs down an end before it is gone, as a fraction of the
 * way from the top (or bottom) to the middle.
 */
private const val GLASS_RIM_REACH = 0.55f

/** Just enough to take the hard edge off the rim — a pixel or two. */
private val GLASS_RIM_BLUR = 0.5.dp

/** The glow: a deeper copy of the rim, blurred, and a fraction as bright. */
private val GLASS_GLOW_DEPTH = 4.dp
private val GLASS_GLOW_BLUR = 10.dp
private const val GLASS_GLOW_GAIN = 1.6f
private const val GLASS_GLOW_ALPHA = 0.07f
