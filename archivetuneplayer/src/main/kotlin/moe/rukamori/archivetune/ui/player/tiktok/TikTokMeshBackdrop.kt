/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player.tiktok

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.CachePolicy
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private val MeshFallbackColors = listOf(
    Color(0xFF3A1C71),
    Color(0xFFD76D77),
    Color(0xFF2B5876),
    Color(0xFFFFAF7B),
)

@Immutable
internal data class TikTokMeshPalette(val colors: List<Color>)

private const val MESH_RENDER_SCALE = 0.25f

@Composable
internal fun TikTokMeshBackdrop(
    palette: TikTokMeshPalette,
    trackKey: Any? = null,
    reduceAnimation: Boolean = false,
    blurRadius: Dp = 64.dp,
    modifier: Modifier = Modifier,
) {
    val tuned = (palette.colors.ifEmpty { MeshFallbackColors } + MeshFallbackColors)
        .take(4)
        .map { it.tuned() }

    val colorSpec: AnimationSpec<Color> = if (reduceAnimation) snap() else tween(1400)
    val animatedColors = tuned.mapIndexed { index, color ->
        animateColorAsState(color, colorSpec, label = "tiktokMeshColor$index").value
    }
    val baseColor by animateColorAsState(tuned.first().dimmed(), colorSpec, label = "tiktokMeshBase")

    val phase = remember { Animatable(0f) }
    LaunchedEffect(trackKey, reduceAnimation) {
        when {
            reduceAnimation -> phase.snapTo(0f)

            else -> phase.animateTo(
                targetValue = phase.value + MESH_DRIFT_RADIANS,
                animationSpec = tween(8_000, easing = FastOutSlowInEasing),
            )
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize(MESH_RENDER_SCALE)
                    .graphicsLayer {
                        scaleX = 1.3f / MESH_RENDER_SCALE
                        scaleY = 1.3f / MESH_RENDER_SCALE
                    }
                    .background(baseColor)
                    .blur(blurRadius * MESH_RENDER_SCALE),
        ) {
            val anchors = listOf(
                Offset(0.20f, 0.25f),
                Offset(0.80f, 0.20f),
                Offset(0.75f, 0.80f),
                Offset(0.25f, 0.75f),
            )
            val speeds = listOf(1f, -0.7f, 0.85f, -1.15f)
            val drift = phase.value

            animatedColors.forEachIndexed { index, color ->
                val anchor = anchors[index]
                val center = Offset(
                    x = (anchor.x + 0.16f * cos(drift * speeds[index] + index * 1.7f)) * size.width,
                    y = (anchor.y + 0.16f * sin(drift * speeds[index] * 0.9f + index * 2.3f)) * size.height,
                )
                val radius = size.maxDimension * 0.62f
                drawCircle(
                    brush = Brush.radialGradient(

                        colors = listOf(color.copy(alpha = 0.92f), color.copy(alpha = 0f)),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.05f),
                        Color.Black.copy(alpha = 0.20f),
                    ),
                ),
            )
        }
    }
}

@Composable
internal fun rememberTikTokArtworkColors(imageUrl: String?): TikTokMeshPalette {
    val context = LocalContext.current
    var palette by remember(imageUrl) { mutableStateOf(TikTokMeshPalette(MeshFallbackColors)) }

    LaunchedEffect(imageUrl) {
        if (imageUrl == null) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(128)
            .allowHardware(false)
            .memoryCacheKey(imageUrl)
            .diskCacheKey(imageUrl)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()
        val result = context.imageLoader.execute(request)
        val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return@LaunchedEffect

        palette = TikTokMeshPalette(withContext(Dispatchers.Default) { paletteOf(bitmap) })
    }
    return palette
}

private const val MESH_DRIFT_RADIANS = (PI * 0.45f).toFloat()

private fun paletteOf(bitmap: Bitmap): List<Color> {
    fun swatchesOf(builder: Palette.Builder): List<Color> =
        builder.maximumColorCount(24).generate().swatches
            .sortedByDescending { it.population }
            .map { Color(it.rgb) }

    val found = swatchesOf(Palette.from(bitmap)).ifEmpty {
        swatchesOf(Palette.from(bitmap).clearFilters())
    }

    val distinct = found.distinctEnough()
    return when {
        distinct.isEmpty() -> MeshFallbackColors
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

private fun Color.hsl(): FloatArray =
    FloatArray(3).also { ColorUtils.colorToHSL(toArgb(), it) }

private fun Color.tuned(): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = (hsl[1] * 1.4f).coerceAtMost(1f)
    hsl[2] = hsl[2].coerceIn(0.22f, 0.66f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.dimmed(): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[2] = 0.12f
    return Color(ColorUtils.HSLToColor(hsl))
}
