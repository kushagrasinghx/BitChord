package com.music.bitchord.ui.components

import com.music.bitchord.R

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.settings.AppSettings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * Which lyric databases the player is allowed to ask.
 *
 * Same frosted iOS alert as [UpdateAvailableDialog], down to the shared
 * [ALERT_WIDTH]/[ALERT_CORNER] metrics and hairline [AlertRule]s, with the
 * action rows swapped for checkable ones. Checkmarks rather than Material
 * checkboxes: that is what a multiple-selection list looks like in this
 * lineage, and a column of square boxes would be the one Material thing left
 * on an otherwise Apple-shaped alert.
 *
 * The order shown is the order they are tried, and it is the user's to set:
 * drag a row by its handle to move it, which reorders independently of
 * whether the row is ticked — priority and participation are different
 * questions, and this is the one dialog for both.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun LyricsSourcesDialog(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val selected by AppSettings.lyricsSources.collectAsStateWithLifecycle()
    val savedOrder by AppSettings.lyricsSourceOrder.collectAsStateWithLifecycle()
    val prioritizeSyllableSync by AppSettings.prioritizeSyllableSync.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(ALERT_CORNER)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SCRIM_COLOR)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(ALERT_WIDTH)
                .clip(shape)
                .then(
                    if (reduceDynamicBlur) {
                        Modifier.background(MaterialTheme.colorScheme.surface)
                    } else {
                        Modifier.optimizedHazeEffect(
                            state = hazeState,
                            style = HazeMaterials.regular(MaterialTheme.colorScheme.surface),
                        )
                    },
                )
                // Swallows the tap before it reaches the scrim behind, so
                // touching the card itself never dismisses it.
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 19.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.lyrics_sources),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.W600,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.lyrics_sources_order),
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }

            ReorderableSourceList(
                order = savedOrder,
                selected = selected,
                onReorder = AppSettings::setLyricsSourceOrder,
                onToggle = { source ->
                    val checked = source in selected
                    // The last one standing can't be unchecked — an empty list
                    // is indistinguishable from switching lyrics off, and there
                    // is already a switch for that a row above this dialog.
                    if (checked && selected.size <= 1) return@ReorderableSourceList
                    AppSettings.setLyricsSources(
                        if (checked) selected - source else selected + source,
                    )
                },
            )

            AlertRule()
            SyllableSyncToggle(
                checked = prioritizeSyllableSync,
                onToggle = { AppSettings.setPrioritizeSyllableSync(!prioritizeSyllableSync) },
            )

            AlertRule()
            AlertAction(
                label = stringResource(R.string.reset_to_default),
                emphasised = false,
                onClick = AppSettings::resetLyricsSourceSettings,
            )
            AlertRule()
            AlertAction(label = stringResource(R.string.done), emphasised = true, onClick = onDismiss)
        }
    }
}

/**
 * Whether a merely line-synced answer is good enough on its own, or worth
 * holding out on for a word-synced one further down the priority order —
 * see the note on [AppSettings.prioritizeSyllableSync]. A single row rather
 * than one more entry in the checkable list above: this isn't a source to
 * ask or not, it's a rule about what to do once one has answered.
 */
@Composable
private fun SyllableSyncToggle(checked: Boolean, onToggle: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ACTION_HEIGHT)
            .background(
                if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f) else Color.Transparent,
            )
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = onToggle,
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.prioritize_syllable_lyrics),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.prioritize_syllable_lyrics_subtitle),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 15.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
        Spacer(Modifier.width(10.dp))
        if (checked) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.enabled),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/**
 * The checkable, drag-reorderable list of sources.
 *
 * Reordering is entirely local until a drag ends — [liveOrder] tracks the
 * list as rows are dragged past each other, and only the finished order is
 * written back through [onReorder]. Writing on every intermediate swap would
 * mean [AppSettings] round-tripping the list back down through
 * [savedOrder][AppSettings.lyricsSourceOrder] on every frame of a drag, fighting
 * the gesture that produced it.
 *
 * The drag keeps exactly two numbers: how far the finger has come since it
 * went down ([totalDrag]), and which slot it went down on ([startIndex]).
 * Where to draw the row and which slot it belongs in are both *derived* from
 * those, so neither can drift from the other however many swaps happen on the
 * way. See [SWAP_THRESHOLD] for why the crossing point is past the halfway
 * mark rather than on it.
 */
@Composable
private fun ReorderableSourceList(
    order: List<LyricsSource>,
    selected: Set<LyricsSource>,
    onReorder: (List<LyricsSource>) -> Unit,
    onToggle: (LyricsSource) -> Unit,
) {
    var liveOrder by remember(order) { mutableStateOf(order) }
    var draggedSource by remember { mutableStateOf<LyricsSource?>(null) }

    /** Distance the finger has covered since this gesture began, in pixels. */
    var totalDrag by remember { mutableStateOf(0f) }

    /** Which slot of [liveOrder] it began on. */
    var startIndex by remember { mutableStateOf(0) }

    // The distance from one row's top to the next one's — which is the row
    // *plus* the hairline above it, not the row alone. Measured off a wrapper
    // holding both, because measuring the row by itself left every swap
    // short by the width of a rule and the error compounded down the list.
    //
    // All the rows are the same height by construction (one line of label,
    // one of detail, both capped), so whichever reports last is as good as
    // any other; [lockedPitchPx] then freezes it for the duration of a
    // gesture, so a relayout mid-drag can't move the boundaries the drag is
    // being measured against underneath it.
    var pitchPx by remember { mutableStateOf(0f) }
    var lockedPitchPx by remember { mutableStateOf(0f) }

    Column {
        liveOrder.forEach { source ->
            // Without this, Compose matches each row to its slot by position
            // rather than by which source it is — so the instant a swap moved
            // a different [LyricsSource] into the slot the finger was on,
            // that slot's `pointerInput` saw its key change and restarted the
            // coroutine mid-gesture, which is indistinguishable from letting
            // go: the touch kept moving but nothing was listening anymore,
            // and the drag stalled one swap after it started. Keying the
            // whole row on the value it represents is what keeps *this
            // composable*, gesture and all, following that value from slot to
            // slot instead of being torn down and rebuilt in place.
            key(source) {
                val checked = source in selected
                // The last one enabled can't be unticked — see the guard in
                // [onToggle] — so it reads the same disabled way the toggle
                // itself already treats it, rather than looking clickable and
                // silently doing nothing.
                val toggleable = !checked || selected.size > 1
                val dragging = source == draggedSource
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (dragging) 1f else 0f)
                        .onSizeChanged { pitchPx = it.height.toFloat() }
                        .graphicsLayer {
                            // Read here rather than in composition: this runs
                            // once a frame in the draw phase, so a drag moves
                            // the row without recomposing the list at all.
                            //
                            // The row sits wherever the finger has carried it
                            // from where it was picked up, less whatever the
                            // swaps have already moved its slot — so a swap
                            // relocates the slot and shortens this offset by
                            // exactly as much, and the row does not budge.
                            translationY = if (dragging) {
                                totalDrag - (liveOrder.indexOf(source) - startIndex) * lockedPitchPx
                            } else {
                                0f
                            }
                        },
                ) {
                    AlertRule()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = ACTION_HEIGHT)
                            .clickable(
                                enabled = toggleable,
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = { onToggle(source) },
                            )
                            .padding(start = 4.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DragHandle,
                            contentDescription = stringResource(R.string.drag_to_reorder),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .size(18.dp)
                                // A constant key on purpose — see the note above.
                                // The row this coroutine belongs to is now pinned
                                // by [key], so nothing about a reorder should ever
                                // restart it; only the handle's own identity
                                // (there is exactly one, for its whole lifetime)
                                // needs to.
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggedSource = source
                                            totalDrag = 0f
                                            startIndex = liveOrder.indexOf(source)
                                            lockedPitchPx = pitchPx
                                        },
                                        onDrag = { change, delta ->
                                            change.consume()
                                            val pitch = lockedPitchPx
                                            if (pitch <= 0f) return@detectDragGestures
                                            var index = liveOrder.indexOf(source)
                                            if (index < 0) return@detectDragGestures

                                            // Held past either end the row stops
                                            // there under the finger, rather than
                                            // running off the list and having to
                                            // be dragged all the way back before
                                            // it answers again.
                                            totalDrag = (totalDrag + delta.y).coerceIn(
                                                -startIndex * pitch,
                                                (liveOrder.lastIndex - startIndex) * pitch,
                                            )

                                            // A loop, not an `if`: one pointer
                                            // event can cover several rows when
                                            // the finger is quick, and settling
                                            // one row per event would leave the
                                            // list trailing the drag.
                                            while (true) {
                                                val travelled = totalDrag / pitch
                                                val moved = (index - startIndex).toFloat()
                                                if (travelled > moved + SWAP_THRESHOLD && index < liveOrder.lastIndex) {
                                                    liveOrder = liveOrder.toMutableList().apply {
                                                        add(index + 1, removeAt(index))
                                                    }
                                                    index++
                                                } else if (travelled < moved - SWAP_THRESHOLD && index > 0) {
                                                    liveOrder = liveOrder.toMutableList().apply {
                                                        add(index - 1, removeAt(index))
                                                    }
                                                    index--
                                                } else {
                                                    break
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            draggedSource = null
                                            totalDrag = 0f
                                            onReorder(liveOrder)
                                        },
                                        onDragCancel = {
                                            draggedSource = null
                                            totalDrag = 0f
                                            liveOrder = order
                                        },
                                    )
                                },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = source.label,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                color = MaterialTheme.colorScheme.onSurface
                                    .copy(alpha = if (toggleable) 1f else 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = source.detail,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        if (checked) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.enabled),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * How far past a neighbour the finger has to carry a row before the two trade
 * places, as a share of one row's pitch.
 *
 * Deliberately more than half. At exactly half, a row that has just swapped
 * lands with its offset sitting precisely on the boundary of swapping *back* —
 * so a single pixel of the shake any real finger has flipped it, and the
 * compensating shift put it straight back on the forward boundary again. The
 * row juddered between two slots for as long as it was held near a crossing,
 * which is the "loops up and down in the same position" this fixes. Anything
 * over half opens a gap between the two boundaries; a tenth of a row is enough
 * to swallow the shake without the swap feeling reluctant.
 */
private const val SWAP_THRESHOLD = 0.6f
