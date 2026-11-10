/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val FallbackColors =
    listOf(
        Color(0xFF3A1C71),
        Color(0xFFD76D77),
        Color(0xFF2B5876),
        Color(0xFFFFAF7B),
    )

@Immutable
data class MeshPalette internal constructor(val colors: List<Color>) {
    internal val base: Color get() = colors.first().dimmed()

    companion object {
        val Fallback = MeshPalette(FallbackColors.map { it.tuned() })
    }
}

private val Anchors =
    floatArrayOf(
        0.20f, 0.25f,
        0.80f, 0.20f,
        0.75f, 0.80f,
        0.25f, 0.75f,
    )

private val Speeds = floatArrayOf(1f, -0.7f, 0.85f, -1.15f)

private const val DriftRadians = (PI * 0.45f).toFloat()

private const val CrossfadeMillis = 1_400
private const val DriftMillis = 8_000

@Composable
fun MeshBackdrop(
    palette: MeshPalette,
    modifier: Modifier = Modifier,
    trackKey: Any? = null,
    reduceAnimation: Boolean = false,
    blurRadius: Dp = 64.dp,
    blobAlpha: Float = 0.95f,
    scrim: Boolean = true,
) {
    val previous = remember { mutableStateOf(palette) }
    val target = remember { mutableStateOf(palette) }
    val fade = remember { Animatable(1f) }
    val phase = remember { Animatable(0f) }

    LaunchedEffect(palette, reduceAnimation) {
        if (palette == target.value) return@LaunchedEffect

        previous.value = if (fade.value >= 1f) target.value else blend(previous.value, target.value, fade.value)
        target.value = palette
        if (reduceAnimation) {
            fade.snapTo(1f)
        } else {
            fade.snapTo(0f)
            fade.animateTo(1f, tween(CrossfadeMillis))
        }
    }

    LaunchedEffect(trackKey, reduceAnimation) {
        if (reduceAnimation) {
            phase.snapTo(0f)
            return@LaunchedEffect
        }

        phase.animateTo(
            targetValue = phase.value + DriftRadians,
            animationSpec = tween(DriftMillis, easing = FastOutSlowInEasing),
        )
    }

    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer {
                    scaleX = 1.3f
                    scaleY = 1.3f
                }.blur(blurRadius),
    ) {
        val t = fade.value
        val from = previous.value
        val to = target.value
        val drift = phase.value

        drawRect(color = lerp(from.base, to.base, t))

        for (index in 0 until 4) {
            val color = lerp(from.colors[index], to.colors[index], t)
            val center = blobCenter(index, drift, size)
            val radius = size.maxDimension * 0.62f
            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors = listOf(color.copy(alpha = blobAlpha), color.copy(alpha = 0f)),
                        center = center,
                        radius = radius,
                    ),
                radius = radius,
                center = center,
            )
        }

        if (scrim) {
            drawRect(
                brush =
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                Color.Black.copy(alpha = 0.05f),
                                Color.Black.copy(alpha = 0.20f),
                            ),
                    ),
            )
        }
    }
}

private fun blobCenter(index: Int, drift: Float, size: Size): Offset {
    val anchorX = Anchors[index * 2]
    val anchorY = Anchors[index * 2 + 1]
    val speed = Speeds[index]
    return Offset(
        x = (anchorX + 0.16f * cos(drift * speed + index * 1.7f)) * size.width,
        y = (anchorY + 0.16f * sin(drift * speed * 0.9f + index * 2.3f)) * size.height,
    )
}

private fun blend(from: MeshPalette, to: MeshPalette, t: Float): MeshPalette =
    MeshPalette(List(4) { lerp(from.colors[it], to.colors[it], t) })

private val paletteCache = object : LinkedHashMap<String, MeshPalette>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MeshPalette>): Boolean = size > 64
}

@Composable
fun rememberMeshPalette(imageUrl: String?): MeshPalette {
    val context = LocalContext.current
    val cached = imageUrl?.let { synchronized(paletteCache) { paletteCache[it] } }
    val state = remember(imageUrl) { mutableStateOf(cached ?: MeshPalette.Fallback) }

    LaunchedEffect(imageUrl) {
        if (imageUrl == null || cached != null) return@LaunchedEffect
        val request =
            ImageRequest
                .Builder(context)
                .data(imageUrl)
                .size(128)
                .allowHardware(false)
                .build()
        val result = context.imageLoader.execute(request)
        val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return@LaunchedEffect

        val palette = withContext(Dispatchers.Default) { MeshPalette(paletteOf(bitmap).map { it.tuned() }) }
        synchronized(paletteCache) { paletteCache[imageUrl] = palette }
        state.value = palette
    }
    return state.value
}

private fun paletteOf(bitmap: Bitmap): List<Color> {
    fun swatchesOf(builder: Palette.Builder): List<Color> =
        builder
            .maximumColorCount(24)
            .generate()
            .swatches
            .sortedByDescending { it.population }
            .map { Color(it.rgb) }

    val found =
        swatchesOf(Palette.from(bitmap)).ifEmpty {
            swatchesOf(Palette.from(bitmap).clearFilters())
        }

    val distinct = found.distinctEnough()
    return when {
        distinct.isEmpty() -> FallbackColors
        distinct.size >= 4 -> distinct.take(4)
        else -> distinct.expandedToFour()
    }
}

private fun List<Color>.distinctEnough(): List<Color> {
    val kept = mutableListOf<Color>()
    forEach { color -> if (kept.none { it.isCloseTo(color) }) kept += color }
    return kept
}

private fun Color.isCloseTo(other: Color): Boolean {
    val a = hsl()
    val b = other.hsl()
    val hueGap = abs(a[0] - b[0]).let { min(it, 360f - it) }
    return hueGap < 15f && abs(a[2] - b[2]) < 0.12f
}

private fun List<Color>.expandedToFour(): List<Color> {
    val out = toMutableList()
    var step = 1
    while (out.size < 4) {
        out += this[(out.size - size) % size].shifted(24f * step, 0.12f * step)
        step++
    }
    return out
}

private fun Color.shifted(hue: Float, lightness: Float): Color {
    val hsl = hsl()
    hsl[0] = (hsl[0] + hue) % 360f
    hsl[2] = (hsl[2] + lightness).coerceIn(0.2f, 0.7f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.hsl(): FloatArray = FloatArray(3).also { ColorUtils.colorToHSL(toArgb(), it) }

private fun Color.tuned(): Color {
    val hsl = hsl()
    hsl[1] = (hsl[1] * 1.6f).coerceAtMost(1f)
    hsl[2] = hsl[2].coerceIn(0.22f, 0.68f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.dimmed(): Color {
    val hsl = hsl()
    hsl[2] = 0.12f
    return Color(ColorUtils.HSLToColor(hsl))
}
