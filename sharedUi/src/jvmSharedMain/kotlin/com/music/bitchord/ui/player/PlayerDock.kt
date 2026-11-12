package com.music.bitchord.ui.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize

/**
 * The mini player the expanded player docks into, for a host that has one.
 *
 * Opening and closing the player is one movement on screen: the sheet's
 * artwork travels between its own place and the mini player's cover while the
 * rest of the player fades — see the sleeve in [NowPlayingScreen]. The two are
 * in different windows on a phone (the player is a modal sheet over the page),
 * so nothing here is shared layout; it is the two ends of the movement, each
 * reported by the side that owns it, and met in screen coordinates.
 *
 * Read in layout and draw only. Nothing in it is worth a recomposition: a
 * frame of the sheet's travel moves the artwork and re-tints a layer, and that
 * is all it does.
 */
@Stable
class PlayerDock {
    /**
     * The mini player's cover, as last laid out. Held as coordinates rather than
     * as a rect, and only turned into one when the player asks: the bar moves
     * on every scroll that folds it, and working out a screen position for each
     * of those frames would be paid for by a player that isn't open.
     */
    private var miniArt: LayoutCoordinates? = null
    private var miniArtCorner: Dp = 8.dp

    /**
     * How far the player sheet sits below fully open, in pixels. Set by the
     * host while the sheet is up, null while it is not. `NaN` before the sheet
     * has been laid out.
     */
    var sheetOffset: (() -> Float)? by mutableStateOf(null)

    /** How far the sheet travels from fully open to gone, in pixels. */
    var sheetTravel by mutableFloatStateOf(0f)

    /**
     * Whether the player on screen takes part. Only its portrait shape does —
     * a landscape player has no sleeve that could become a cover, and keeps
     * the plain slide.
     */
    var attached by mutableStateOf(false)
        internal set

    /** Reported by the mini player's cover whenever it is placed. */
    fun reportMiniArt(coordinates: LayoutCoordinates, corner: Dp) {
        miniArt = coordinates
        miniArtCorner = corner
    }

    /** From the cover leaving composition, so a stale rect is never flown to. */
    fun releaseMiniArt(coordinates: LayoutCoordinates) {
        if (miniArt === coordinates) miniArt = null
    }

    internal fun hasMiniArt(): Boolean = miniArt?.isAttached == true

    /**
     * Both corners mapped through every transform above the cover, not its
     * position plus its laid-out size: the glass bar draws its player scaled,
     * and the artwork landed on a cover a fifth bigger than the one on screen.
     */
    internal fun miniArtOnScreen(): Rect? {
        val coordinates = miniArt?.takeIf { it.isAttached } ?: return null
        val size = coordinates.size.toSize()
        return Rect(
            coordinates.localToScreen(Offset.Zero),
            coordinates.localToScreen(Offset(size.width, size.height)),
        )
    }

    internal fun miniArtCorner(): Dp = miniArtCorner

    /** The sheet's offset this frame, or null with no sheet laid out. */
    internal fun offset(): Float? = sheetOffset?.invoke()?.takeUnless { it.isNaN() }

    /**
     * 1 with the player fully open, 0 down on the mini player. 0 too while
     * there is no sheet yet, which is where an opening player starts.
     */
    fun openFraction(): Float {
        val travel = sheetTravel
        val offset = offset() ?: return 0f
        if (travel <= 0f) return 0f
        return (1f - offset / travel).coerceIn(0f, 1f)
    }

    /**
     * Whether the mini player's own cover should stand aside, because the
     * player's is on its way to or from it. Not before the sheet has moved at
     * all: the sheet's window can land a frame after the page's, and a cover
     * hidden in that frame is a hole in the bar.
     */
    val coversMiniArt: Boolean
        get() = coversMiniArtState.value

    // Derived, so a reader hears about it twice a transition — when it flips
    // — rather than on every frame of the sheet's travel. Read straight off
    // the fraction, the cover's layer re-ran every frame of an open or a close,
    // and every one of those invalidated the page's whole window behind the
    // player: a full redraw, liquid glass and all, for a cover that hadn't
    // changed.
    private val coversMiniArtState = derivedStateOf { attached && openFraction() > 0f }
}

/** The [PlayerDock] around the player, if its host has a mini player to dock into. */
val LocalPlayerDock = staticCompositionLocalOf<PlayerDock?> { null }
