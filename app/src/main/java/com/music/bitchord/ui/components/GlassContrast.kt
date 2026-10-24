package com.music.bitchord.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.music.bitchord.ui.components.backdrop.backdrops.LayerBackdrop
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ceil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * How bright the content behind the glass over [backdrop] is, region by region.
 *
 * Glass shows whatever it sits over, so a glyph in one fixed colour on it loses
 * its contrast the moment something of that colour scrolls behind it: white
 * glyphs over a white sleeve vanish. Apple's glass reads what it is over and
 * turns its glyphs dark over light content; this is the reading.
 *
 * A few times a second, while anything is reading it, the backdrop's own
 * recording is drawn at [SAMPLE_SCALE] of its size and read back: one small
 * readback shared by every control over that backdrop. Working it out on the
 * GPU instead would take a layer and a shader pass over every glass surface on
 * every frame, which is what made scrolling drop frames before. An eighth is
 * finer than any glyph, and the glass blurs what is under it anyway.
 */
@Stable
class BackdropLuminance internal constructor(
    private val backdrop: LayerBackdrop,
    private val probe: GraphicsLayer,
) {
    /** How many glass controls are reading; nothing is sampled while there are none. */
    internal var readers by mutableIntStateOf(0)

    /** Bumped with every fresh sample, so a reader looks at its region again. */
    internal var samples by mutableIntStateOf(0)
        private set

    private var pixels = IntArray(0)
    private var width = 0
    private var height = 0
    private var origin = Offset.Zero

    internal suspend fun sample(density: Density, layoutDirection: LayoutDirection) {
        // Null while the backdrop is not being recorded, as under reduce dynamic blur.
        val coordinates = backdrop.layerCoordinates?.takeIf { it.isAttached } ?: return
        val w = ceil(coordinates.size.width * SAMPLE_SCALE).toInt()
        val h = ceil(coordinates.size.height * SAMPLE_SCALE).toInt()
        if (w <= 0 || h <= 0) return
        probe.record(density, layoutDirection, IntSize(w, h)) {
            scale(SAMPLE_SCALE, SAMPLE_SCALE, pivot = Offset.Zero) { drawLayer(backdrop.graphicsLayer) }
        }
        val image = probe.toImageBitmap().asAndroidBitmap()
        // A hardware bitmap lives on the GPU and cannot hand its pixels over.
        val readable = if (image.config == Bitmap.Config.HARDWARE) {
            image.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            image
        }
        val read = IntArray(w * h)
        readable.getPixels(read, 0, w, 0, 0, w, h)
        pixels = read
        width = w
        height = h
        origin = coordinates.positionInWindow()
        samples++
    }

    /**
     * How bright it is under [bounds], in window coordinates, at its brighter
     * end: the luminance [BRIGHT_SHARE] of the region is at or above. Null
     * before the first sample.
     *
     * Not the mean. A glyph over a dark sleeve with a white patch under part
     * of it stayed white, because the patch averaged away into the dark round
     * it — and the part of the glyph over the patch vanished. Read at the
     * brighter end, a patch a quarter of the region wide is enough to turn it.
     */
    internal fun luminanceIn(bounds: Rect): Float? {
        if (width == 0) return null
        val left = ((bounds.left - origin.x) * SAMPLE_SCALE).toInt().coerceIn(0, width - 1)
        val right = ceil((bounds.right - origin.x) * SAMPLE_SCALE).toInt().coerceIn(left + 1, width)
        val top = ((bounds.top - origin.y) * SAMPLE_SCALE).toInt().coerceIn(0, height - 1)
        val bottom = ceil((bounds.bottom - origin.y) * SAMPLE_SCALE).toInt().coerceIn(top + 1, height)
        val values = FloatArray((right - left) * (bottom - top))
        var i = 0
        for (y in top until bottom) {
            for (x in left until right) values[i++] = Color(pixels[y * width + x]).luminance()
        }
        values.sort()
        return values[((values.size - 1) * (1f - BRIGHT_SHARE)).toInt()]
    }
}

/** The [BackdropLuminance] of the backdrop the glass here samples, where it is read. */
val LocalBackdropLuminance = staticCompositionLocalOf<BackdropLuminance?> { null }

/**
 * Samples [backdrop] for the glass over it, while the app is in front and any
 * glass control is reading it.
 */
@Composable
fun rememberBackdropLuminance(backdrop: LayerBackdrop): BackdropLuminance {
    val probe = rememberGraphicsLayer()
    val luminance = remember(backdrop, probe) { BackdropLuminance(backdrop, probe) }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(luminance, density, layoutDirection, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow { luminance.readers > 0 }.collectLatest { read ->
                while (read) {
                    try {
                        luminance.sample(density, layoutDirection)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // A frame the renderer would not hand back, as when the window is
                        // going away; the next one will do.
                    }
                    delay(SAMPLE_INTERVAL_MS)
                }
            }
        }
    }
    return luminance
}

/** Where a glass control is on screen, so [glassContentColor] reads what is under it. */
@Stable
class GlassArea {
    internal var bounds by mutableStateOf<Rect?>(null)
}

/** Marks this as the region [area] stands for. */
fun Modifier.glassArea(area: GlassArea): Modifier = onGloballyPositioned { area.bounds = it.boundsInWindow() }

/**
 * Glyph and label colour for the glass control at [area]: white, or black where
 * the glass shows something light enough that white would sink into it, the way
 * Apple's glass turns its glyphs dark — crossfaded when it changes.
 *
 * Glass shows whatever is behind it rather than the theme's surface colour, so
 * the usual onSurface greys have nothing dependable to sit against; pure black
 * or white are the only tints that hold, and which of the two is read off what
 * the glass is showing. Goes dark above one brightness and light again only
 * below a lower one, so content that hovers around the line does not set it
 * flickering. Light glass veils what is behind it in white, so on the light
 * theme it stays dark. Until there is a sample — or with nothing sampling,
 * under reduce dynamic blur — it is the theme's: black on light glass, white
 * on dark.
 */
@Composable
fun glassContentColor(area: GlassArea): Color {
    val lightGlass = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    var dark by remember(lightGlass) { mutableStateOf(lightGlass) }
    val luminance = LocalBackdropLuminance.current
    if (luminance != null) {
        DisposableEffect(luminance) {
            luminance.readers++
            onDispose { luminance.readers-- }
        }
        LaunchedEffect(luminance, area, lightGlass) {
            snapshotFlow { luminance.samples to area.bounds }.collect { (_, bounds) ->
                val under = bounds?.let(luminance::luminanceIn) ?: return@collect
                val glass = glassLuminanceOver(under, lightGlass)
                dark = when {
                    glass > DARK_CONTENT_ABOVE -> true
                    glass < LIGHT_CONTENT_BELOW -> false
                    else -> dark
                }
            }
        }
    }
    val color by animateColorAsState(
        targetValue = if (dark) Color.Black else Color.White,
        animationSpec = tween(CONTENT_CROSSFADE_MS),
        label = "glassContent",
    )
    return color
}

/**
 * The fraction of its size the backdrop is sampled at: a sample every 8px, 3dp
 * at 3x, fine enough to find a patch under a single glyph's stroke.
 */
private const val SAMPLE_SCALE = 1f / 8f

/** How much of a region has to be bright for its content to turn dark. */
private const val BRIGHT_SHARE = 0.25f

/** How often it is sampled — well inside the crossfade, so the glyphs keep up with a scroll. */
private const val SAMPLE_INTERVAL_MS = 150L

/** How bright the glass has to show for its glyphs to go dark, and how dim for them to go light again. */
private const val DARK_CONTENT_ABOVE = 0.5f
private const val LIGHT_CONTENT_BELOW = 0.35f

private const val CONTENT_CROSSFADE_MS = 300
