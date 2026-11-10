package com.music.bitchord.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class QueueSwipeAction { REMOVE, QUEUE }

/**
 * Finger motion is immediate; only release motion uses an animation. The identity
 * captured at touch-slop is carried through both animations, never a cached index.
 * A caller must resolve that identity again immediately before mutating the queue.
 */
@Composable
internal fun QueueSwipeRow(
    track: QueueTrack,
    enabled: Boolean,
    reduceAnimation: Boolean,
    canCommit: (QueueTrack, QueueSwipeAction) -> Boolean,
    onCommit: suspend (QueueTrack, QueueSwipeAction) -> Boolean,
    modifier: Modifier = Modifier,
    allowQueueGesture: Boolean = true,
    content: @Composable (interactive: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val motion = remember(track.key) { Animatable(0f) }
    val height = remember(track.key) { Animatable(1f) }
    val arrival = remember(track.key) { Animatable(1f) }
    var offset by remember(track.key) { mutableFloatStateOf(0f) }
    var width by remember(track.key) { mutableFloatStateOf(0f) }
    var dragging by remember(track.key) { mutableStateOf(false) }
    var committing by remember(track.key) { mutableStateOf(false) }
    var settling by remember(track.key) { mutableStateOf(false) }
    var releaseId by remember(track.key) { mutableIntStateOf(0) }
    var armed by remember(track.key) { mutableStateOf(false) }
    var gestureTrack by remember(track.key) { mutableStateOf(track) }
    var releaseJob by remember(track.key) { mutableStateOf<Job?>(null) }
    val currentTrack by rememberUpdatedState(track)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentReducedMotion by rememberUpdatedState(reduceAnimation)
    val currentCanCommit by rememberUpdatedState(canCommit)
    val currentCommit by rememberUpdatedState(onCommit)
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr

    fun actionAt(displacement: Float): QueueSwipeAction =
        if ((displacement < 0f) == ltr) QueueSwipeAction.REMOVE else QueueSwipeAction.QUEUE

    fun release(cancelled: Boolean) {
        if (!dragging) return
        dragging = false
        armed = false
        val releasedTrack = gestureTrack
        val action = actionAt(offset)
        val commit = !cancelled && currentEnabled && width > 0f &&
            abs(offset) >= width * 0.45f && currentCanCommit(releasedTrack, action)
        val destination = if (offset < 0f) -width else width
        committing = commit
        settling = true
        val id = ++releaseId
        releaseJob?.cancel()
        releaseJob = scope.launch {
            try {
                motion.snapTo(offset)
                if (commit) {
                    height.snapTo(1f)
                    arrival.snapTo(1f)
                    motion.animateTo(destination, tween(if (currentReducedMotion) 0 else 180, easing = FastOutSlowInEasing)) {
                        offset = value
                    }
                    height.animateTo(0f, tween(if (currentReducedMotion) 0 else 180, easing = FastOutSlowInEasing))
                    // Lock/visibility/identity/target may all have changed mid-flight.
                    // Do not suspend between final validation and entering the callback.
                    arrival.snapTo(0f)
                    val accepted = if (currentEnabled && currentCanCommit(releasedTrack, action)) {
                        try {
                            currentCommit(releasedTrack, action)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            false
                        }
                    } else false
                    if (accepted && action == QueueSwipeAction.QUEUE) {
                        // A move inside the manual section retains its lazy key. Reset
                        // that surviving row invisibly, then fade it in at its new slot.
                        // Cross-section moves dispose this scope instead.
                        withFrameNanos { }
                        height.snapTo(1f)
                        motion.snapTo(0f)
                        offset = 0f
                        arrival.animateTo(1f, tween(if (currentReducedMotion) 0 else 160))
                        return@launch
                    }
                    if (accepted) {
                        // Removal retains the existing fire-and-forget callback.
                        // Keep its old slot hidden until the snapshot disposes it;
                        // recover without retrying if no removal ever arrives.
                        delay(2_000)
                    }
                    arrival.snapTo(1f)
                    height.animateTo(1f, tween(if (currentReducedMotion) 0 else 180))
                }
                motion.animateTo(0f, tween(if (currentReducedMotion) 0 else 220, easing = FastOutSlowInEasing)) {
                    offset = value
                }
            } finally {
                // An interrupted snap-back must not unlock a newer release.
                if (releaseId == id) {
                    // Also restore a still-composed row if an awaited command
                    // propagates cancellation. Never swallow cancellation or retry.
                    offset = 0f
                    committing = false
                    settling = false
                }
            }
        }
    }

    DisposableEffect(track.key) {
        onDispose { releaseJob?.cancel() }
    }
    LaunchedEffect(enabled) {
        if (!enabled) {
            ++releaseId
            releaseJob?.cancel()
            dragging = false
            committing = false
            settling = false
            armed = false
            offset = 0f
            motion.snapTo(0f)
            height.snapTo(1f)
            arrival.snapTo(1f)
        }
    }

    val action = actionAt(offset)
    val tint = if (action == QueueSwipeAction.REMOVE) Color(0xFFE35B67) else MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Clip outside the measuring modifier: its reported (collapsing)
            // height, not the child's unchanged natural height, is the viewport.
            .clip(RoundedCornerShape(8.dp))
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                // One invisible pixel keeps a collapsed first-visible lazy item
                // alive while its asynchronous command/arrival is outstanding.
                val visibleFraction = if (committing) height.value else 1f
                val collapsedHeight = (placeable.height * visibleFraction).roundToInt()
                layout(placeable.width, collapsedHeight.coerceAtLeast(if (committing) 1 else 0)) {
                    placeable.place(0, 0)
                }
            }
            .onSizeChanged { width = it.width.toFloat() }
            .pointerInput(track.key, enabled, committing, ltr, allowQueueGesture) {
                if (!enabled || committing) return@pointerInput
                // Main-pass horizontal slop yields to the existing child handle's
                // consumed drag, vertical list scrolling and long-press consumption.
                detectHorizontalDragGestures(
                    onDragStart = {
                        ++releaseId
                        releaseJob?.cancel()
                        gestureTrack = currentTrack
                        dragging = true
                        settling = false
                        armed = false
                    },
                    onDragEnd = { release(cancelled = false) },
                    onDragCancel = { release(cancelled = true) },
                    onHorizontalDrag = { _, amount ->
                        val proposed = (offset + amount).coerceIn(-width, width)
                        // Now Playing permits removal, but must not reveal an add
                        // affordance for an action that can never commit.
                        offset = if (!allowQueueGesture && actionAt(proposed) == QueueSwipeAction.QUEUE) 0f else proposed
                        val eligible = currentCanCommit(gestureTrack, actionAt(offset))
                        val pastThreshold = width > 0f && abs(offset) >= width * 0.45f && eligible
                        if (pastThreshold && !armed) {
                            haptics.play(Haptic.Tick)
                            armed = true
                        } else if (!eligible || abs(offset) < width * 0.36f) {
                            armed = false
                        }
                    },
                )
            },
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    if (offset > 0f) {
                        clipRect(right = offset) { this@drawWithContent.drawContent() }
                    } else if (offset < 0f) {
                        clipRect(left = size.width + offset) { this@drawWithContent.drawContent() }
                    }
                }
                .background(tint.copy(alpha = 0.22f))
                .padding(horizontal = 20.dp),
            contentAlignment = if (action == QueueSwipeAction.REMOVE) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Icon(
                imageVector = if (action == QueueSwipeAction.REMOVE) Icons.Rounded.Delete else Icons.AutoMirrored.Rounded.PlaylistAdd,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
        Box(Modifier.graphicsLayer {
            translationX = offset
            alpha = if (committing) arrival.value else 1f
        }) {
            // Swipe availability is not row availability: disabled/prewarmed
            // panes and another handle drag must not suppress baseline taps or
            // menus. The original row still owns its locked-control semantics.
            content(!dragging && !committing && !settling)
        }
    }
}
