package com.music.pexpo.ui.components

import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The strip the tab bar alone needs, above the gesture inset. Generous on
 * purpose: the ramp below spends most of its run at an alpha too low to see,
 * and that long invisible lead-in is what hides where the layer begins.
 */
private val FADE_HEIGHT = 180.dp

/**
 * Taller once the mini player is stacked on top of the tab bar — by the 56dp
 * the pill-shaped bar now stands, plus the 8dp gap above it and the run the
 * ramp wants over both.
 */
private val FADE_HEIGHT_WITH_MINI_PLAYER = 254.dp

/**
 * How many colour stops the ramp is cut into.
 *
 * A two-stop gradient is interpolated linearly in the shader, which is both the
 * wrong curve and — across a strip this tall in a near-flat colour — enough of
 * a straight line through the low alphas to band visibly on an 8-bit display.
 * Sampling the curve at intervals hands the shader short spans to interpolate
 * across instead, and the banding goes with them.
 */
private const val STOPS = 16

/**
 * The floor the floating bars sit on: the page's own colour, faded in from
 * nothing at the top to solid at the very bottom.
 *
 * Flat colour rather than glass, deliberately. A blur here has to sample the
 * feed scrolling under it, and what it costs is paid on every frame of that
 * scroll; a gradient costs one shader over a fixed rect. The bars themselves
 * still carry glass, so the frosted look survives where it is actually read —
 * on the pill and the mini player, against a floor that is only ever a wash of
 * the colour already behind them.
 *
 * The ramp is the feathering. The top edge is fully transparent, which is what
 * keeps the strip from reading as a rectangle stuck over the feed, and the side
 * edges run to the screen edges, so they have no seam of their own to soften.
 *
 * Unlike the blur it replaced, this stays on under Reduce dynamic blur: it is
 * not a blur, it costs nothing to leave in, and with the bars filled solid it
 * is the only thing keeping content from running out from under them.
 */
@Composable
fun BottomFadeScrim(
    modifier: Modifier = Modifier,
    withMiniPlayer: Boolean = false,
    /**
     * The colour to fade in — whatever the page is painting at the foot of the
     * screen. The theme's background on a tab, and on a detail page the tint
     * its wash has settled into down here rather than the wash itself.
     */
    pageColor: Color = MaterialTheme.colorScheme.background,
) {
    // The gesture bar sits below the tab pill and wants covering too, so it is
    // added on rather than being part of the fade's own run.
    val inset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val height by animateDpAsState(
        targetValue = inset + if (withMiniPlayer) FADE_HEIGHT_WITH_MINI_PLAYER else FADE_HEIGHT,
        // Matches the beat the mini player takes to appear, so the floor grows
        // with it instead of snapping ahead of it.
        animationSpec = tween(220),
        label = "bottomScrimHeight",
    )

    val brush = remember(pageColor) {
        Brush.verticalGradient(
            // A cubic ease-in rather than a straight ramp: the alpha then holds
            // under a few percent for the first half of the strip, which is what
            // stops the eye from finding the line where the layer starts. Its
            // whole run is spent arriving — the same curve the blur it replaced
            // ramped its radius along, so the strip reads at the same weight.
            colorStops = Array(STOPS) { i ->
                val t = i / (STOPS - 1f)
                t to pageColor.copy(alpha = EaseInCubic.transform(t))
            },
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(brush),
    )
}
