package com.music.bitchord.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The phone's player sheet, for a window: the shared player rises over the page
 * the way Android's full-screen bottom sheet brings it up, and goes back down
 * the same ways — a downward drag anywhere the player does not claim for
 * itself, a click on the handle it draws at the top, or Escape (which the
 * window routes through [com.music.bitchord.ui.player.PlayerBack] first, so the
 * lyrics or the queue close before the sheet does).
 *
 * Drawn inside the window rather than as Material's modal sheet: that one is a
 * focusable popup on the desktop, and would take the media keys and the
 * window's shortcuts away for as long as the player was up.
 */
@Composable
internal fun DesktopPlayerSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable BoxScope.(windowWidth: Dp, windowHeight: Dp) -> Unit,
) {
    // Mounted while it animates out, so the slide down is seen.
    var mounted by remember { mutableStateOf(visible) }
    if (visible) mounted = true
    if (!mounted) return

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val windowWidth = maxWidth
        val windowHeight = maxHeight
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val offset = remember { Animatable(heightPx) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(visible) {
            if (visible) {
                offset.animateTo(0f, tween(SHEET_OPEN_MS, easing = FastOutSlowInEasing))
            } else {
                offset.animateTo(heightPx, tween(SHEET_CLOSE_MS, easing = FastOutSlowInEasing))
                mounted = false
            }
        }

        val dragState = rememberDraggableState { delta ->
            scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, heightPx)) }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = offset.value }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        if (offset.value > heightPx * DISMISS_FRACTION || velocity > DISMISS_VELOCITY) {
                            onDismiss()
                        } else {
                            offset.animateTo(0f, tween(SHEET_SETTLE_MS, easing = FastOutSlowInEasing))
                        }
                    },
                ),
        ) {
            content(windowWidth, windowHeight)
            // The handle the landscape player draws at its top edge, made to do
            // on a click what it promises for a drag.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(HANDLE_HIT_WIDTH_FRACTION)
                    .height(HANDLE_HIT_HEIGHT)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
    }
}

private const val SHEET_OPEN_MS = 360
private const val SHEET_CLOSE_MS = 280
private const val SHEET_SETTLE_MS = 220
private const val DISMISS_FRACTION = 0.25f
private const val DISMISS_VELOCITY = 1_600f
private const val HANDLE_HIT_WIDTH_FRACTION = 0.2f
private val HANDLE_HIT_HEIGHT = 24.dp
