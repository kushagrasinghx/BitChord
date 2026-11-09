package com.music.bitchord.ui.components

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
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
 * How far below the top bar's controls the blur runs out, ported from
 * ArchiveTune's HomeTopFadeRun: the haze belongs to the whole top of the
 * screen rather than only the bar's own strip, and it thins as it goes.
 */
private val FADE_RUN = 88.dp

/**
 * The haze's strength where it is at its peak, along the very top edge —
 * ArchiveTune's HomeTopFadePeak: strong enough to frost, short of enough to
 * erase.
 */
private const val FADE_PEAK = 0.75f

/** The legibility scrim's own peak, laid over the haze — ArchiveTune's 0.42. */
private const val SCRIM_PEAK = 0.42f

/** Enough stops that the eased scrim does not band across a near-flat colour. */
private const val SCRIM_STOPS = 12

/**
 * The top-of-screen haze, ported from ArchiveTune's HomeTopFadeBlur — the
 * effect the "Progressive blur" switch now draws. What scrolls up under the
 * status bar and the floating controls is hazed through the ultra-thin
 * material, at three quarters strength along the top edge and easing out to
 * none over the 88dp run below the bar, with no noise. Over it, a 12-stop
 * scrim of the page's own colour peaking at 0.42 and easing out on the same
 * curve keeps the ink readable without washing the content out — the pair
 * ArchiveTune lays over every scrolled screen's head.
 *
 * [pageColor] is what shows where the haze has nothing to sample: past the top
 * of the screen, and wherever the content has not yet reached.
 */
@OptIn(ExperimentalHazeApi::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun TopFadeBlur(
    hazeState: HazeState,
    pageColor: Color,
    modifier: Modifier = Modifier,
) {
    val height: Dp = topBarHeight() + FADE_RUN
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .hazeEffect(
                state = hazeState,
                style = HazeMaterials.ultraThin(pageColor),
            ) {
                progressive = HazeProgressive.verticalGradient(
                    easing = EaseOutCubic,
                    startIntensity = FADE_PEAK,
                    endIntensity = 0f,
                )
                noiseFactor = 0f
                // Sampled at a third and scaled back up, as this strip always
                // has been: the haze is low-frequency by definition, and at
                // full resolution this was the most expensive thing on a feed.
                inputScale = HazeInputScale.Fixed(0.33f)
            },
    )
    val scrim = remember(pageColor) {
        Brush.verticalGradient(
            colorStops = Array(SCRIM_STOPS) { i ->
                val t = i / (SCRIM_STOPS - 1f)
                t to pageColor.copy(alpha = SCRIM_PEAK * (1f - EaseOutCubic.transform(t)))
            },
        )
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(scrim),
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
