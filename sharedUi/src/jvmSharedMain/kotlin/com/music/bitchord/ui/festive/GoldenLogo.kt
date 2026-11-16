package com.music.bitchord.ui.festive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.components.MOTION_FRAME_GAP_MS
import com.music.bitchord.ui.screens.LocalArtworkMotion
import com.music.bitchord.ui.screens.LocalArtworkScrolling
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.sin

private val GoldGlow = Color(0xFFFFC23D)

/** Metallic gold, dark at the edges and bright through the middle like a polished bevel. */
private val GoldFace = listOf(
    Color(0xFFA8740E),
    Color(0xFFE9B23A),
    Color(0xFFFFE9A3),
    Color(0xFFF6C24B),
    Color(0xFFB9801A),
    Color(0xFFFFD873),
)

/** How long one glint — the shine crossing the mark, the halo swelling with it — takes. */
private const val GLINT_MS = 1_600L

/** The rest between glints, during which nothing is drawn at all. */
private const val REST_MS = 3_400L

/** No glint in progress: the mark sits still and asks for no frames. */
private const val AT_REST = -1f

/**
 * The wordmark, cast in gold for a festival: a soft halo, a metallic gradient
 * face, and every few seconds a glint — the shine sweeping across while the
 * halo swells and settles.
 *
 * Built to be cheap under the liquid glass. Every frame of anything on the page
 * is a frame of the whole page (the glass bars sample it live and re-run their
 * blur), so the mark follows the same rules as the rest of the page's loops:
 * - only the glint moves, and between glints no frame is asked for at all;
 * - the glint runs at [MOTION_FRAME_GAP_MS] pacing (30fps), not every vsync;
 * - it holds still while [LocalArtworkScrolling] says the page is scrolling,
 *   and while [LocalArtworkMotion] is off (app in the background, Now Playing
 *   up, or the Diwali greeting covering the page);
 * - the blurred glow is never animated, so its blur renders once and is kept.
 *
 * Progress is read only inside draw lambdas, so a glint frame redraws these
 * canvases and never recomposes anything.
 */
@Composable
fun GoldenLogo(
    painter: Painter,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    val glint = remember { mutableFloatStateOf(AT_REST) }
    val running = LocalArtworkMotion.current
    val scrolling = LocalArtworkScrolling.current
    LaunchedEffect(running, scrolling) {
        glint.floatValue = AT_REST
        if (!running) return@LaunchedEffect
        while (true) {
            delay(REST_MS)
            if (scrolling()) snapshotFlow { scrolling() }.first { !it }
            var start = Long.MIN_VALUE
            while (true) {
                val elapsedMs = withFrameNanos { now ->
                    if (start == Long.MIN_VALUE) start = now
                    (now - start) / 1_000_000L
                }
                if (elapsedMs >= GLINT_MS) break
                glint.floatValue = elapsedMs / GLINT_MS.toFloat()
                delay(MOTION_FRAME_GAP_MS)
            }
            glint.floatValue = AT_REST
        }
    }

    Box(
        modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = onClickLabel,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .drawBehind {
                // The halo is a plain gradient — redrawing it costs nothing and
                // needs no layer. It rests at a steady glow and swells with
                // each glint.
                val p = glint.floatValue
                val swell = if (p == AT_REST) 0f else sin(p * PI.toFloat())
                val strength = 0.65f + 0.35f * swell
                val r = size.maxDimension * (0.78f + 0.12f * swell)
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to GoldGlow.copy(alpha = 0.38f * strength),
                        0.5f to GoldGlow.copy(alpha = 0.14f * strength),
                        1f to Color.Transparent,
                        center = center,
                        radius = r,
                    ),
                    radius = r,
                    center = center,
                )
            },
    ) {
        // Held perfectly still. Animating it either way went wrong on device:
        // a layer alpha rendered it offscreen at the mark's own bounds (a box
        // fading in and out), and re-recording it each frame re-ran the blur
        // every frame, which flickered.
        Box(
            Modifier
                .matchParentSize()
                .blur(10.dp, BlurredEdgeTreatment.Unbounded)
                .drawBehind {
                    with(painter) {
                        draw(size, alpha = 0.7f, colorFilter = ColorFilter.tint(GoldGlow))
                    }
                },
        )
        Icon(
            painter = painter,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.linearGradient(
                            GoldFace,
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        ),
                        blendMode = BlendMode.SrcIn,
                    )
                    val p = glint.floatValue
                    if (p != AT_REST) {
                        // Enters past the left edge and leaves past the right.
                        val band = size.width * 0.22f
                        val x = -band + p * (size.width + 2 * band)
                        drawRect(
                            brush = Brush.linearGradient(
                                0f to Color.Transparent,
                                0.5f to Color.White.copy(alpha = 0.9f),
                                1f to Color.Transparent,
                                start = Offset(x - band, 0f),
                                end = Offset(x + band, size.height * 0.6f),
                            ),
                            blendMode = BlendMode.SrcAtop,
                        )
                    }
                },
        )
    }
}
