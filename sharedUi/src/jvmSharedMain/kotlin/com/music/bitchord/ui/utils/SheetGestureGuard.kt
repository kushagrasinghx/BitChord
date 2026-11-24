package com.music.bitchord.ui.utils

import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Velocity

/**
 * Tracks when a resting [androidx.compose.material3.ModalBottomSheet] falsely
 * consumed the current gesture's down event in [PointerEventPass.Initial]
 * during Material3 1.3.1's 300 ms no-op post-fling settle (see
 * [guardSheetFromContentTouches]).
 *
 * [guardSheetFromContentTouches] stops that false grab from dragging the sheet,
 * but [Modifier.clickable] still drops taps when `down` arrives already
 * consumed. Recording the falsely-grabbed pointer id lets [sheetGuardedClickable]
 * rescue clean taps without ever double-firing when `down` was unconsumed.
 */
class SheetTouchGuard {
    internal var falseGrabPointerId: PointerId? = null
        private set

    internal fun markFalseGrab(id: PointerId) {
        falseGrabPointerId = id
    }

    internal fun clearFalseGrab(id: PointerId? = null) {
        if (id == null || falseGrabPointerId == id) {
            falseGrabPointerId = null
        }
    }

    internal fun isFalseGrab(id: PointerId): Boolean = falseGrabPointerId == id
}

val LocalSheetTouchGuard = staticCompositionLocalOf<SheetTouchGuard?> { null }

/**
 * Stops a resting [androidx.compose.material3.ModalBottomSheet] from stealing a
 * touch that belongs to the content inside it. Goes on the root of the sheet's
 * content.
 *
 * Material3 1.3.1's sheet starts its own drag on touch-down, skipping the touch
 * slop and taking the event in the Initial pass before any content sees it,
 * whenever it thinks it is animating. And it is "animating" for 300 ms after
 * every scroll that ends inside it: its nested-scroll connection calls settle()
 * on every post-fling, even one with zero velocity left, and settle runs the
 * sheet's fixed 300 ms tween from the anchor it is already on to that same
 * anchor. So a finger that lands within 300 ms of the last scroll ending drags
 * the sheet. The sheet carries the content along with it, so the list under the
 * finger sees no movement and never takes the gesture back. The player collapsing
 * mid-way through scrolling the lyrics or queue was exactly that.
 *
 * The fix: if the sheet grabbed the down while it was fully open (so there was
 * no real motion to catch), consume the first move in the Main pass. The sheet's
 * drag runs after its content in that pass, sees the move consumed, and cancels,
 * and the content's own drag carries on from its touch slop as usual. When
 * [guard] is provided, the pointer id is also recorded so [sheetGuardedClickable]
 * descendants can rescue taps whose `down` arrived already consumed. A sheet
 * that is really moving (opening, or springing back from a partial drag) is left
 * alone, so a finger can still catch it.
 */
@OptIn(ExperimentalMaterial3Api::class)
fun Modifier.guardSheetFromContentTouches(
    sheetState: SheetState,
    guard: SheetTouchGuard? = null,
): Modifier =
    pointerInput(sheetState, guard) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // Unconsumed means the sheet was not animating, so it waits for its
            // slop like any other ancestor and the content wins as normal.
            if (!down.isConsumed) {
                guard?.clearFalseGrab()
                return@awaitEachGesture
            }
            val offset = runCatching { sheetState.requireOffset() }.getOrNull()
            if (offset == null || offset > 1f) {
                guard?.clearFalseGrab()
                return@awaitEachGesture
            }
            guard?.markFalseGrab(down.id)
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if (change.positionChange() != Offset.Zero) {
                        change.consume()
                        break
                    }
                }
            } finally {
                guard?.clearFalseGrab(down.id)
            }
        }
    }

/**
 * Like [Modifier.clickable], but also rescues a clean tap when a resting
 * [androidx.compose.material3.ModalBottomSheet] falsely consumed `down` in
 * [PointerEventPass.Initial] (see [guardSheetFromContentTouches]).
 *
 * When `down` is unconsumed (the normal case) or was legitimately consumed by
 * another ancestor (e.g. a real fling or moving sheet), this adds no click
 * handling and delegates entirely to [Modifier.clickable].
 */
@Composable
fun Modifier.sheetGuardedClickable(
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val guard = LocalSheetTouchGuard.current
    val currentOnClick by rememberUpdatedState(onClick)
    val base = clickable(
        interactionSource = interactionSource,
        indication = indication,
        enabled = enabled,
        onClick = onClick,
    )
    if (guard == null || !enabled) return base
    return base.pointerInput(guard, interactionSource) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!down.isConsumed || !guard.isFalseGrab(down.id)) return@awaitEachGesture
            val press = PressInteraction.Press(down.position)
            interactionSource.tryEmit(press)
            var released = false
            try {
                var totalDrag = Offset.Zero
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        val inside = change.position.x in 0f..size.width.toFloat() &&
                            change.position.y in 0f..size.height.toFloat()
                        if (inside && !change.isConsumed) {
                            change.consume()
                            interactionSource.tryEmit(PressInteraction.Release(press))
                            released = true
                            currentOnClick()
                        }
                        break
                    }
                    if (change.isConsumed) break
                    totalDrag += change.positionChange()
                    if (totalDrag.getDistance() > slop) break
                }
            } finally {
                if (!released) {
                    interactionSource.tryEmit(PressInteraction.Cancel(press))
                }
            }
        }
    }
}

/**
 * Keeps every drag that starts inside an overlay — a drawer or dialog drawn
 * over the player — from reaching the sheet the player lives in. Goes on the
 * overlay's full-screen root.
 *
 * Two routes leak otherwise. A scrollable inside the overlay hands whatever it
 * can't use (a pull down while already at the top) up the nested-scroll chain,
 * where the sheet's own connection takes it and moves the player. And a drag
 * that nothing inside claims — on the scrim, or a row with no scroll of its
 * own — reaches the sheet's draggable as an unconsumed drag. The connection
 * swallows the first; consuming every move past touch slop stops the second,
 * while taps, which never cross the slop, still reach the scrim's dismiss.
 */
fun Modifier.containSheetGestures(): Modifier =
    nestedScroll(SwallowNestedScroll).pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val start = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                ?: return@awaitEachGesture
            drag(start.id) { it.consume() }
        }
    }

private object SwallowNestedScroll : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = available
    override suspend fun onPostFling(consumed: Velocity, available: Velocity) = available
}
