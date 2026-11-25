package com.music.bitchord.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.ui.components.MENU_FROST_ALPHA
import com.music.bitchord.ui.components.MenuFrostDark
import com.music.bitchord.ui.components.liveMenuFrost
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * The ⋯ menu's frost, for the player's own drawers and popups: a still of the
 * player, drawn again under a panel blurred with its hue drawn up — the same
 * material the menu's panel is, rather than a haze that only sees the few
 * parts of the player that are haze sources.
 *
 * A still, taken as a panel opens ([capture]), rather than the player live:
 * blurring the live player re-renders all of it under the panel on every frame
 * anything in it moves — a progress tick, the mesh drifting — which is what
 * made the panels stutter. Behind a panel nobody is watching the player move.
 *
 * Supplied by the platform (see [PlayerHost.rememberPlayerFrost]), because the
 * backdrop is the platform's backdrop library's.
 */
@Stable
interface PlayerFrost {
    /** What [capture] takes its still of. Draws as if it weren't there. */
    @Composable
    fun Modifier.frostSource(): Modifier

    /** Takes a still of [frostSource] on its next frame, for the panels to blur. */
    fun capture()

    /** Lets the still go, once nothing is up to blur it. */
    fun release()

    /**
     * Paints this — a drawer, a popup card — in [shape], with what
     * [frostSource]'s still under it blurred, under [fill].
     */
    fun Modifier.frostPanel(shape: Shape, fill: Color): Modifier
}

/**
 * The player's frost, for whatever is drawn over it; null where there is none —
 * the platform can't draw it, or "reduce dynamic blur" is on.
 */
val LocalPlayerFrost = staticCompositionLocalOf<PlayerFrost?> { null }

/**
 * The player's outermost frame: the player itself, the source of [frost]'s
 * still, and [overlays] — its drawers and popups — drawn beside it rather than
 * inside it, so the still is of the player alone. Taken when [overlayOpen]
 * turns true, and let go when it turns false.
 *
 * None while [live]: the player is then a full-screen Spotify Canvas that
 * keeps playing under the panels, and a still of it would sit frozen under a
 * panel over a moving picture. The panels blur it live instead, off the haze
 * the clip is a source of — see [liveMenuFrost].
 */
@Composable
internal fun FrostedPlayerFrame(
    modifier: Modifier,
    /** [LocalPlayerChrome] for the player, not for what is drawn over it. */
    chrome: Boolean,
    /** [LocalChromeSoftened], alongside [chrome]. */
    softened: Boolean = false,
    frost: PlayerFrost?,
    live: Boolean,
    /** Read off composition, so a panel opening recomposes nothing here. */
    overlayOpen: () -> Boolean,
    overlays: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    if (frost != null && !live) {
        val open by rememberUpdatedState(overlayOpen)
        LaunchedEffect(frost) {
            snapshotFlow { open() }.collectLatest { up ->
                if (up) {
                    frost.capture()
                } else {
                    // Not until the last panel has played its way out.
                    delay(FROST_RELEASE_DELAY_MS)
                    frost.release()
                }
            }
        }
    }
    Box(modifier.fillMaxSize()) {
        val source = if (frost != null) with(frost) { Modifier.frostSource() } else Modifier
        Box(Modifier.fillMaxSize().then(source)) {
            CompositionLocalProvider(
                LocalPlayerChrome provides chrome,
                LocalChromeSoftened provides softened,
                content = content,
            )
        }
        CompositionLocalProvider(LocalPlayerFrost provides frost?.takeIf { !live }) {
            overlays()
        }
    }
}

/** Longer than any panel's exit, so none loses its frost on the way out. */
private const val FROST_RELEASE_DELAY_MS = 400L

/**
 * The ⋯ menu's and the drawers' panel, in [shape], for anything that should read
 * as the same material: flat with reduced blur on; over [LocalPlayerFrost]'s
 * still where this is one of the player's overlays; otherwise the same frost
 * blurred live off [hazeState] — which is what a bar inside the player gets,
 * since a still would freeze the list scrolling under it.
 */
@Composable
fun Modifier.playerMenuSurface(
    shape: Shape,
    hazeState: HazeState?,
    /** How much of the charcoal slab covers the frost. */
    slabAlpha: Float = MENU_FROST_ALPHA,
): Modifier {
    val reduceDynamicBlur by PlayerSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val frost = LocalPlayerFrost.current
    return clip(shape).then(
        when {
            reduceDynamicBlur || (frost == null && hazeState == null) -> Modifier.background(MenuFrostDark)
            frost != null -> with(frost) { Modifier.frostPanel(shape, MenuFrostDark.copy(alpha = slabAlpha)) }
            else -> Modifier.liveMenuFrost(hazeState!!, MenuFrostDark, slabAlpha)
        },
    )
}

/**
 * The ⋯ menu's own panel — its thinner slab and hairline rim — for the bars laid
 * over the player's lists: the queue's selection bar and Undo notice, and the
 * share-lyrics bar. Blurs every source under it, since each sits inside the
 * player's own (see [liveMenuFrost]); without that it drew nothing at all.
 */
@Composable
fun Modifier.playerBarSurface(shape: Shape, hazeState: HazeState?): Modifier {
    val plain by PlayerSettings.disableBlendEffects.collectAsStateWithLifecycle()
    // Thinner than the menu's 60%: the menu's frost also draws the colour up 1.8×,
    // which haze can't, so the same slab here reads as near-solid charcoal.
    return playerMenuSurface(shape, hazeState, slabAlpha = if (plain) 0.70f else 0.45f)
        .border(0.5.dp, Color.White.copy(alpha = 0.10f), shape)
}
