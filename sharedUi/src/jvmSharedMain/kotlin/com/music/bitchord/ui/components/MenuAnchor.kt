package com.music.bitchord.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced

/**
 * Where the ⋮ or ⋯ that opened a menu sits, so the menu can hang off that
 * button rather than rise from the bottom of the screen.
 *
 * The counterpart to [LongPressOrigin] for a tap: a single slot, written when
 * a finger lands on the button and read by the menu's host in the click that
 * follows. Every ⋮ opens its menu through the same callback a hold does, so
 * the host asks [consume] rather than having bounds threaded through every
 * list. The timestamp keeps a press that never became a click — dragged off,
 * or a scroll that started on the button — from being claimed by a later,
 * unrelated opening.
 */
object MenuAnchor {
    private var bounds: Rect? = null
    private var recordedAt = 0L

    internal fun record(bounds: Rect) {
        this.bounds = bounds
        recordedAt = System.nanoTime()
    }

    /** The button this opening was a tap on, in window coordinates, or null. */
    fun consume(): Rect? {
        val anchor = bounds?.takeIf { System.nanoTime() - recordedAt < FRESH_NANOS }
        bounds = null
        return anchor
    }

    private const val FRESH_NANOS = 3_000_000_000L
}

/**
 * Marks this — a ⋮ or ⋯ button — as what a menu hangs off: its bounds go to
 * [MenuAnchor] the moment it is pressed, without taking the press from the
 * button's own click.
 */
@Composable
fun Modifier.menuAnchor(): Modifier {
    val anchor = remember { AnchorCoordinates() }
    return this
        .onPlaced { anchor.coordinates = it }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                anchor.coordinates?.takeIf { it.isAttached }?.let { MenuAnchor.record(it.boundsInWindow()) }
            }
        }
}

private class AnchorCoordinates {
    var coordinates: LayoutCoordinates? = null
}

/**
 * Leaves this — a row's ⋮ — out of the picture a hold takes of its row (see
 * [longPressMenuClickable]): the lifted row is the thing the menu is about,
 * and a ⋮ on it would offer the very menu already open under it.
 *
 * Not just hidden but given no width for as long as the picture is being
 * taken, so what sat beside it — a duration, a download mark — closes up to
 * the row's edge as it would on a row with no ⋮, instead of stopping short
 * of a blank space. It has to be the outermost of the button's modifiers to
 * do that: a fixed size outside it would hold the width open.
 */
@Composable
fun Modifier.leftOutOfHeldPicture(): Modifier {
    val position = remember { HeldPosition() }
    return this
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            // Only read while a hold is being captured, and only the held
            // row's own button is inside what it covers.
            val capturing = LongPressOrigin.capturing
            if (capturing != null && position.center?.let(capturing::contains) == true) {
                layout(0, placeable.height) {}
            } else {
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
        }
        // Kept from the last time it was placed: while collapsed it isn't.
        .onPlaced { position.center = it.boundsInWindow().center }
}

private class HeldPosition {
    var center: Offset? = null
}
