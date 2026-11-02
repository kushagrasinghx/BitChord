package com.music.bitchord.ui.player

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
    tint: Color,
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
    CompositionLocalProvider(LocalPlayerTint provides tint) {
        Box(modifier.fillMaxSize()) {
            val source = if (frost != null) with(frost) { Modifier.frostSource() } else Modifier
            Box(Modifier.fillMaxSize().then(source)) {
                content()
            }
            CompositionLocalProvider(LocalPlayerFrost provides frost?.takeIf { !live }) {
                overlays()
            }
        }
    }
}

/** Longer than any panel's exit, so none loses its frost on the way out. */
private const val FROST_RELEASE_DELAY_MS = 400L
