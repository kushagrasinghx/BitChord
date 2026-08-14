package com.music.bitchord.ui.components

import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * The strip the tab bar alone needs, above the gesture inset. Generous on
 * purpose: the ramp below spends most of its run at a blur too small to see,
 * and that long invisible lead-in is what hides where the layer begins.
 */
private val FADE_HEIGHT = 180.dp

/** Taller once the mini player is stacked on top of the tab bar. */
private val FADE_HEIGHT_WITH_MINI_PLAYER = 248.dp

/**
 * The frosted floor the floating bars sit on.
 *
 * A full-width pane of glass pinned to the bottom of the screen whose blur
 * ramps in from nothing at the top to full at the very bottom, so content
 * scrolling under the tab bar dissolves rather than sliding under a hard-edged
 * panel. The gradient is the feathering: the top edge has no blur and no tint
 * at all, which is what keeps the strip from reading as a rectangle stuck over
 * the feed. The side edges run to the screen edges, so they have no seam of
 * their own to soften.
 *
 * A step lighter than the bars that sit on it ([HazeMaterials.ultraThin]
 * against their `thin`), so the mini player and the tab pill still separate
 * from it rather than dissolving into one frosted mass.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun BottomFadeBlur(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    withMiniPlayer: Boolean = false,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    // The floating bars fill themselves solid instead when blur is reduced,
    // so this frosted floor underneath them has nothing left to do.
    if (reduceDynamicBlur) return

    // The gesture bar sits below the tab pill and wants blurring too, so it is
    // added on rather than being part of the fade's own run.
    val inset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val height by animateDpAsState(
        targetValue = inset + if (withMiniPlayer) FADE_HEIGHT_WITH_MINI_PLAYER else FADE_HEIGHT,
        // Matches the beat the mini player takes to appear, so the glass grows
        // with it instead of snapping ahead of it.
        animationSpec = tween(220),
        label = "bottomFadeHeight",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .hazeEffect(
                state = hazeState,
                // Keyed to `background`, not `surface` like the bars are.
                // HazeStyle.backgroundColor is painted as an opaque rect under
                // the sampled content, and the progressive gradient does not
                // reach it — it only ramps the blur radius and the tint's
                // alpha. Handing it `surface` therefore lays a solid #0D0D0F
                // sheet over a pure-black page across the strip's whole height,
                // which is a visible block of colour with a hard top edge.
                // Matching the page's own colour makes the untouched top of the
                // ramp an exact copy of what it covers.
                style = HazeMaterials.ultraThin(MaterialTheme.colorScheme.background),
            ) {
                // A cubic ease-in rather than haze's default quadratic one: the
                // ramp then holds under a few percent for the first half of the
                // strip, which is what stops the eye from finding the line
                // where the layer starts. Its whole run is spent arriving.
                progressive = HazeProgressive.verticalGradient(
                    easing = EaseInCubic,
                    startIntensity = 0f,
                    endIntensity = 1f,
                )
                // Haze's film grain is uniform across the layer, so it shows up
                // at the top as texture over content that is otherwise
                // untouched — exactly the edge the gradient is hiding.
                noiseFactor = 0f
            },
    )
}
