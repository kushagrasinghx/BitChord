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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import io.github.fletchmckee.liquid.liquid

/**
 * The run the fade needs below the bar to get from full blur to none without
 * the eye finding where it got there.
 *
 * Shortened from 120: the page's first heading sits a fixed distance down the
 * screen, well inside this run, and over 120dp the ramp still had something
 * like a tenth of its blur left there — enough to leave a heavy 30sp title
 * looking soft before it had been scrolled anywhere. The tail is what hides the
 * layer's end, so it cannot simply be cut; 88 is as short as it goes before the
 * ramp starts to be findable. The rest of the clearance is bought by starting
 * the page's content lower — the two are tuned against each other, and neither
 * fixes it alone.
 */
private val FADE_RUN = 88.dp

/**
 * How much blur the fade reaches at its outer edge — short of all of it.
 *
 * The last quarter buys almost nothing visually and costs the most: a blur has
 * nothing to sample past the edge of its own layer, so the harder it is pushed
 * there the more of the layer is flat material colour rather than blurred
 * content, and the more that edge reads as a band of colour laid over the page.
 * Stopping at three quarters keeps the ramp and loses the band.
 */
private const val PEAK = 0.75f

/**
 * How dark the readability scrim starts, at the very top of the strip.
 *
 * Modest on purpose: it is there to give white glyphs a floor on a pale sleeve,
 * not to grey out the artwork. Anything heavier and the bar stops being a fade
 * over a picture and starts being a header with a picture behind it.
 */
private const val SCRIM_PEAK = 0.42f

/** Enough stops that the ramp does not band across a near-flat colour. */
private const val SCRIM_STOPS = 12

/**
 * The glass behind every top bar: full blur along the top edge, ramping to
 * nothing on the way down.
 *
 * A bar carrying a uniform pane is a rectangle sitting on the page, and its
 * bottom edge is a line drawn across whatever scrolls under it. That reads
 * worst on a detail page, whose artwork runs up under the status bar, but it
 * is the same hard edge on a feed — so the fade is what every page gets, and
 * [FrostedTopBar] paints no backdrop of its own anywhere.
 *
 * Fading out instead leaves the title and back arrow something to be legible
 * against and the page nothing to be interrupted by.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun TopFadeBlur(
    hazeState: HazeState,
    /**
     * The colour of the page behind this: the theme's background on a feed, a
     * detail page's artwork wash on one of those. See the effect below for why
     * it cannot just be the theme's in both cases.
     */
    pageColor: Color,
    modifier: Modifier = Modifier,
    /**
     * A wash laid over the blur to keep the bar's glyphs readable.
     *
     * Blur alone does not settle contrast — it makes a pale sleeve into a pale
     * blur, and a row of bright artwork scrolling under a feed's title into a
     * bright smear. The scrim gives the glyphs a floor to sit on whatever
     * happens to pass beneath them.
     *
     * Laid *over* the blur rather than under it, which is the only order that
     * works: haze samples the content tagged as its source, not whatever
     * sibling happens to sit between that content and itself, so a scrim
     * underneath would be painted over by the blurred content and do nothing.
     */
    scrimColor: Color,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val liquidState = LocalLiquidGlassState.current
    // The bar fills itself solid instead when blur is reduced, so this has
    // nothing left to do.
    if (reduceDynamicBlur) return

    val height = topBarHeight() + FADE_RUN
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(
                if (liquidState != null) {
                    Modifier.liquid(liquidState) {
                        frost = 10.dp
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
                        refraction = 0.08f
                        curve = 0.1f
                        edge = 0f
                        tint = pageColor.copy(alpha = 0.12f)
                        saturation = 1.05f
                    }
                } else Modifier.hazeEffect(
                    state = hazeState,
                // Keyed to the colour of the page underneath, not the theme's.
                //
                // Both halves of this material are flat colour: the style's
                // background is painted as an opaque rect under the sampled
                // content, and its tint is a film over that. The progressive
                // gradient reaches neither — it ramps only the blur radius and
                // the tint's alpha — so wherever the blur has least to say,
                // that flat colour is most of what is left. A blur has nothing
                // to sample past the top
                // of its own layer, so the first blur-radius of this strip is
                // barely covered by blurred content and shows mostly the flat
                // colour of the material instead. Given the theme's near-black
                // background, that is a black bar spreading unevenly down into
                // the artwork: the exact artefact this was added to remove.
                style = HazeMaterials.ultraThin(pageColor),
                ) {
                // This is intentionally the progressive Haze effect used by the
                // original top treatment. The mask-based optimization changes
                // the visual result on device and loses the soft blur shown in
                // the header reference. Reduce dynamic blur still exits before
                // this layer is composed.
                progressive = HazeProgressive.verticalGradient(
                    easing = EaseOutCubic,
                    startIntensity = PEAK,
                    endIntensity = 0f,
                )
                // Uniform across the layer, so it would show as texture over
                // the untouched foot of the ramp — the edge being hidden.
                noiseFactor = 0f
                }
            ),
    )

    val scrim = remember(scrimColor) {
        Brush.verticalGradient(
            // The same eased-out shape as the blur above it, so the two arrive
            // at nothing together. A scrim that outlasted the blur would leave
            // a tinted band hanging below a fade that had already finished —
            // the one artefact this bar exists to avoid.
            colorStops = Array(SCRIM_STOPS) { i ->
                val t = i / (SCRIM_STOPS - 1f)
                t to scrimColor.copy(alpha = SCRIM_PEAK * (1f - EaseOutCubic.transform(t)))
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
