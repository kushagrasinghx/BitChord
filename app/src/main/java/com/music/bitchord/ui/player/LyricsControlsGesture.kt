package com.music.bitchord.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput

/**
 * How far a finger may wander inside a lower-half tap and still be a tap, as a
 * multiple of the platform's own slop.
 *
 * The list underneath starts scrolling at exactly one slop, and it consumes the
 * gesture when it does — so a tap that shifted by a hair more than that was not
 * merely ignored, it was taken by the list and became a browse. Every shortfall
 * here is a tap somebody meant and did not get.
 */
private const val TAP_SLOP_FACTOR = 2.5f

/**
 * Intercept a lower-half tap before a lyric row can seek; leave real drags to
 * the list.
 *
 * Runs on [PointerEventPass.Initial], which is what makes the disambiguation
 * possible at all: this sees each event before the list does, and while the
 * finger is still inside [TAP_SLOP_FACTOR] it *consumes* the movement, so the
 * list never reaches its own slop and never takes the gesture away. Past that
 * distance the consuming stops, the deltas flow through, and what is left is an
 * ordinary scroll that began a few pixels late.
 *
 * The dead zone that costs only exists while [enabled] — that is, only while
 * the controls are away and a tap has something to do. With them on screen this
 * detector is absent entirely and the list scrolls off its own slop as usual.
 */
@Composable
internal fun Modifier.revealLyricsControlsOnTap(
    enabled: Boolean,
    onReveal: () -> Unit,
): Modifier {
    val currentOnReveal = rememberUpdatedState(onReveal)
    return pointerInput(enabled) {
        if (!enabled) return@pointerInput
        val tapSlop = viewConfiguration.touchSlop * TAP_SLOP_FACTOR
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.position.y < size.height / 2f) return@awaitEachGesture
            var dragged = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if ((change.position - down.position).getDistance() > tapSlop ||
                    event.changes.size > 1
                ) {
                    dragged = true
                } else if (change.positionChange() != Offset.Zero) {
                    // Still a tap as far as this is concerned, so hold the list
                    // still rather than let it read the wobble as the start of a
                    // scroll it would then own.
                    change.consume()
                }
                if (!change.pressed) {
                    if (!dragged) {
                        change.consume()
                        currentOnReveal.value()
                    }
                    break
                }
            } while (true)
        }
    }
}
