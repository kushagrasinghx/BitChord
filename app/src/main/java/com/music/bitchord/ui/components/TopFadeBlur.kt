package com.music.bitchord.ui.components

import androidx.compose.animation.core.EaseInOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * How far below the top bar's controls the blur runs out — enough that it
 * reaches nothing with no edge the eye can find, and no further. The blur
 * belongs to the top of the screen, not to the page under it.
 */
private val FADE_RUN = 16.dp

/**
 * The blur along the very top edge, where it is at its strongest: enough that
 * whatever scrolls under the status bar reads as frosted rather than legible.
 */
private val BLUR_MAX = 24.dp

/**
 * Where down the strip it starts handing over to the page beneath, as a
 * fraction of its height. By here the blur is under half strength.
 */
private const val FOOT_FADE_START = 0.6f

/** Enough stops that the eased fade does not band across a near-flat colour. */
private const val FOOT_FADE_STOPS = 8

/** The strip's opacity: whole down to [FOOT_FADE_START], then eased out to none at its foot. */
private val FootFade = Brush.verticalGradient(
    colorStops = Array(FOOT_FADE_STOPS + 1) { i ->
        val t = i / FOOT_FADE_STOPS.toFloat()
        val at = FOOT_FADE_START + (1f - FOOT_FADE_START) * t
        at to Color.Black.copy(alpha = 1f - EaseInOut.transform(t))
    },
)

/**
 * Apple's scroll edge at the top of the screen: whatever scrolls up under the
 * status bar and the floating controls is blurred, hardest along the top edge
 * and easing out to none just below the controls.
 *
 * Blur only — no tint, no scrim. What it replaces was a fade to the page's own
 * colour, which hides the content rather than frosting it, and a tint over the
 * blur would bring that wash back. Eased at both ends of the ramp, so neither
 * where it starts to give out nor where it stops shows as a line.
 *
 * [pageColor] is what shows where the blur has nothing to sample: past the top
 * of the screen. Haze lays it under the blurred content, so along the top edge
 * the frosting settles into the page's own colour rather than into black.
 *
 * Sampled at a third and scaled back up. At full resolution this strip was the
 * most expensive thing drawn on a feed — around 5ms of GPU time a frame against
 * an 8.3ms budget at 120Hz — and a blur is low-frequency by definition: a third
 * of the pixels carries the same picture.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun TopFadeBlur(
    hazeState: HazeState,
    pageColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topBarHeight() + FADE_RUN)
            // The strip is a third-resolution copy of the page, softer than the
            // page itself even where the blur has run out, so ending it outright
            // left a line across whatever text it ended on. Its last stretch
            // fades out instead, handing over to the page underneath.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(FootFade, blendMode = BlendMode.DstIn)
            }
            .hazeEffect(state = hazeState) {
                backgroundColor = pageColor
                tints = emptyList()
                blurRadius = BLUR_MAX
                noiseFactor = 0f
                inputScale = HazeInputScale.Fixed(0.33f)
                progressive = HazeProgressive.verticalGradient(
                    easing = EaseInOut,
                    startIntensity = 1f,
                    endIntensity = 0f,
                )
            },
    )
}

/**
 * The uniform glass used by ordinary top bars.
 *
 * Unlike [TopFadeBlur], this occupies only the status-bar inset and the 52dp
 * bar itself. Its material is deliberately identical to [FloatingBottomBar]'s
 * non-liquid-glass surface: the same regular Haze material, the same surface
 * colour and the same reduced-resolution sampling supplied by
 * [optimizedHazeEffect]. [FrostedTopBar] draws the hairline at its lower edge.
 *
 * Replay and artwork detail pages draw neither component and let their own
 * full-bleed background show through unchanged.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun TopBarBlur(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    // FrostedTopBar supplies the solid surface when dynamic blur is reduced.
    if (reduceDynamicBlur) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topBarHeight())
            .optimizedHazeEffect(
                state = hazeState,
                style = HazeMaterials.regular(MaterialTheme.colorScheme.surface),
            ),
    )
}
