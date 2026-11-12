package com.music.bitchord.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Keeps Haze's visual style while allowing it to reduce its sampling resolution.
 * Haze 1.x otherwise processes every effect at full resolution, even when a
 * large blur makes those extra source pixels invisible.
 *
 * A third rather than [HazeInputScale.Auto]. Auto only steps the resolution down
 * once the blur radius is large enough for it to be confident, which left the
 * mini player and the nav bar sampling at full resolution on every frame of
 * every scroll. A third is what the liquid glass surfaces already sample at, and
 * what [com.music.bitchord.ui.components.TopFadeBlur] was moved to for the same
 * reason: the blur is what hides the upscale, so the pixels that were being paid
 * for could not be seen either way. [block] can still override it per call site.
 */
@OptIn(ExperimentalHazeApi::class)
fun Modifier.optimizedHazeEffect(
    state: HazeState,
    style: HazeStyle = HazeStyle.Unspecified,
    block: (HazeEffectScope.() -> Unit)? = null,
): Modifier = hazeEffect(state, style) {
    inputScale = HazeInputScale.Fixed(0.33f)
    block?.invoke(this)
}

/**
 * The frost of the iOS menu panel: what is behind blurred well past legibility,
 * under [fill] at the alpha the panel's slab takes — so a page or the player
 * shows through as colour, not as a page seen through tinted glass. One style
 * for the menu and the player's drawers and popups alike, so they read as
 * the same material.
 *
 * [fill] alone stands in where the blur can't be drawn.
 */
fun menuFrost(fill: Color, alpha: Float = MENU_FROST_ALPHA): HazeStyle = HazeStyle(
    backgroundColor = fill,
    tints = listOf(HazeTint(fill.copy(alpha = alpha))),
    blurRadius = 24.dp,
    noiseFactor = 0f,
    fallbackTint = HazeTint(fill),
)

/**
 * [menuFrost] blurred live off [state] rather than off a still — for a panel
 * over something that keeps moving under it, the player's full-screen Spotify
 * Canvas. Sampled at a tenth: it is re-blurred on every frame of the video, and
 * under a blur this wide and a slab this opaque, more pixels than that are not
 * there to be seen.
 */
fun Modifier.liveMenuFrost(state: HazeState, fill: Color): Modifier =
    optimizedHazeEffect(state = state, style = menuFrost(fill)) {
        inputScale = HazeInputScale.Fixed(LIVE_FROST_INPUT_SCALE)
    }

private const val LIVE_FROST_INPUT_SCALE = 0.10f

/** How much of the menu panel's slab covers its frost, dark. */
const val MENU_FROST_ALPHA = 0.80f

/** The menu panel's charcoal, and the player's — which is dark whatever the theme. */
val MenuFrostDark = Color(0xFF1C1C1E)
