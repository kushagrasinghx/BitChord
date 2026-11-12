package com.music.bitchord.wear.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/** BitChord's own colours, for when there is no cover to take them from. */
val BrandMesh = listOf(Color(0xFFFA2D48), Color(0xFF7B2FF7), Color(0xFFFF7A45), Color(0xFF2E1A8A))

/**
 * The phone player's backdrop, sized for a watch.
 *
 * Same four colours — the phone sends the ones its own player computed — tuned
 * the same way and laid out at the same anchors. What the watch leaves out is
 * the full-screen blur the phone draws them through: on a 450px round screen
 * the radial falloff alone already reads as a mesh, and a blur layer is the one
 * thing that would cost real GPU time here. Drawn from a cache, so it is
 * redrawn only while the colours crossfade on a track change.
 */
@Composable
fun MeshBackground(colors: List<Color>, modifier: Modifier = Modifier) {
    val tuned = remember(colors) {
        (colors.ifEmpty { BrandMesh } + BrandMesh).take(4).map { it.tuned() }
    }
    val c0 by animateColorAsState(tuned[0], tween(FADE_MS), label = "mesh0")
    val c1 by animateColorAsState(tuned[1], tween(FADE_MS), label = "mesh1")
    val c2 by animateColorAsState(tuned[2], tween(FADE_MS), label = "mesh2")
    val c3 by animateColorAsState(tuned[3], tween(FADE_MS), label = "mesh3")
    val base by animateColorAsState(tuned[0].dimmed(), tween(FADE_MS), label = "meshBase")

    Spacer(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val radius = size.maxDimension * 0.62f
                val blobs = listOf(c0, c1, c2, c3).mapIndexed { i, color ->
                    val center = Offset(ANCHORS[i].x * size.width, ANCHORS[i].y * size.height)
                    center to Brush.radialGradient(
                        colors = listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0f)),
                        center = center,
                        radius = radius,
                    )
                }
                // Darker than the phone's scrim: a watch face is small, often
                // outdoors, and white text has to hold at a glance.
                val scrim = Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.55f)),
                )
                onDrawBehind {
                    drawRect(base)
                    blobs.forEach { (center, brush) -> drawCircle(brush, radius, center) }
                    drawRect(scrim)
                }
            },
    )
}

private const val FADE_MS = 900

/** The phone's anchors, at the phone's settled drift. */
private val ANCHORS = listOf(
    Offset(0.20f, 0.25f),
    Offset(0.80f, 0.20f),
    Offset(0.75f, 0.80f),
    Offset(0.25f, 0.75f),
)

/** The phone's tuning: richer saturation, lightness kept out of the mud and the glare. */
private fun Color.tuned(): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = (hsl[1] * 1.35f).coerceAtMost(1f)
    hsl[2] = hsl[2].coerceIn(0.28f, 0.58f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.dimmed(): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[2] = 0.12f
    return Color(ColorUtils.HSLToColor(hsl))
}
