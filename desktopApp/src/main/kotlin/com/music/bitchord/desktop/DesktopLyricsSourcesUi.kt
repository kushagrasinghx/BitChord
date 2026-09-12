package com.music.bitchord.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs

/** Which lyric databases the player may ask, and in what order. */
@Composable
internal fun DesktopLyricsSourcesDialog(
    order: List<String>,
    enabled: Set<String>,
    prioritizeSyllables: Boolean,
    onReorder: (List<String>) -> Unit,
    onToggle: (String) -> Unit,
    onPrioritizeSyllables: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 420) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                DesktopStrings["lyrics_sources", "Lyrics sources"],
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Text(
                DesktopStrings[
                    "d_sources_are_tried_in_this_order",
                    "Sources are tried in this order. Drag to reorder them. The first source " +
                        "with lyrics wins unless syllable lyrics are prioritized.",
                ],
                style = MaterialTheme.typography.bodySmall,
                color = DesktopSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        // Scrolls: enough providers have been added that the card runs off both ends of a short
        // window, taking Reset and Done with it. A plain Column inside, not a lazy list — the
        // drag-reorder measures itself against the rows it has, and a lazy list would recycle one
        // out from under the pointer.
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 340.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            ReorderableSourceList(order, enabled, onReorder, onToggle)
        }
        DesktopCardRule()
        // Not a source to ask or not: a rule about what to do once one has answered.
        CheckableRow(
            title = DesktopStrings["prioritize_syllable_lyrics", "Prioritize syllable lyrics"],
            subtitle = DesktopStrings["prioritize_syllable_lyrics_subtitle", "Keep searching past a line-synced match for word-by-word lyrics"],
            checked = prioritizeSyllables,
            onClick = { onPrioritizeSyllables(!prioritizeSyllables) },
        )
        DesktopCardRule()
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            TextButton(onClick = onReset) { Text(DesktopStrings["reset_to_default", "Reset to default"], color = DesktopSecondary) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(DesktopStrings["done", "Done"], color = DesktopAccent) }
        }
    }
}

/** The checkable, drag-reorderable list. */
@Composable
private fun ReorderableSourceList(
    order: List<String>,
    enabled: Set<String>,
    onReorder: (List<String>) -> Unit,
    onToggle: (String) -> Unit,
) {
    var liveOrder by remember(order) { mutableStateOf(order) }
    var dragged by remember { mutableStateOf<String?>(null) }
    var totalDrag by remember { mutableStateOf(0f) }
    var startIndex by remember { mutableStateOf(0) }
    // Each row's own height, by source.
    val heights = remember { mutableStateMapOf<String, Float>() }
    // The list as it stood when the drag began.
    var startOrder by remember { mutableStateOf<List<String>>(emptyList()) }

    fun topOf(list: List<String>, index: Int): Float {
        var top = 0f
        for (slot in 0 until index.coerceAtMost(list.size)) top += heights[list[slot]] ?: 0f
        return top
    }

    Column {
        liveOrder.forEachIndexed { slot, name ->
            // Keyed on the source rather than the slot, and keyed *here*, as the column's own
            // child.
            key(name) {
                val source = DesktopLyricsClient.sources.firstOrNull { it.name == name }
                if (source != null) {
                    val checked = name in enabled
                    val dragging = name == dragged
                    Column(
                        Modifier
                            .fillMaxWidth()
                            // The rule above the row is measured with it and travels with it.
                            .onSizeChanged { heights[name] = it.height.toFloat() }
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer {
                                // Read in the draw phase, so a drag moves the row without
                                // recomposing the list at all.
                                translationY = if (dragging) {
                                    totalDrag - (
                                        topOf(liveOrder, liveOrder.indexOf(name)) -
                                            topOf(startOrder, startIndex)
                                        )
                                } else {
                                    0f
                                }
                            }
                            .background(
                                if (dragging) Color.White.copy(alpha = 0.08f) else Color.Transparent,
                            ),
                    ) {
                        if (slot > 0) DesktopCardRule()
                        CheckableRow(
                            title = source.name,
                            subtitle = source.detail,
                            checked = checked,
                            // The last one ticked cannot be unticked: an empty list is
                            // indistinguishable from switching lyrics off, and there is already a
                            // switch for that.
                            enabled = !checked || enabled.size > 1,
                            onClick = { onToggle(name) },
                            leading = {
                                Icon(
                                    Icons.Rounded.DragHandle,
                                    DesktopStrings["drag_to_reorder", "Drag to reorder"],
                                    tint = DesktopSecondary,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .pointerInput(name) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    dragged = name
                                                    totalDrag = 0f
                                                    startOrder = liveOrder
                                                    startIndex = liveOrder.indexOf(name)
                                                },
                                                onDragEnd = {
                                                    dragged = null
                                                    onReorder(liveOrder)
                                                },
                                                onDragCancel = {
                                                    dragged = null
                                                    liveOrder = order
                                                },
                                            ) { change, amount ->
                                                change.consume()
                                                totalDrag += amount.y
                                                val layout = startOrder.map { heights[it] ?: 0f }
                                                if (layout.all { it > 0f }) {
                                                    val target = reorderTargetSlot(layout, startIndex, totalDrag)
                                                    val at = liveOrder.indexOf(name)
                                                    if (target != at) {
                                                        liveOrder = liveOrder.toMutableList().apply {
                                                            removeAt(at)
                                                            add(target, name)
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Which slot a row dragged [dragPx] from slot [from] now belongs in, given the height of every row
 * in the layout it was grabbed out of.
 */
internal fun reorderTargetSlot(heights: List<Float>, from: Int, dragPx: Float): Int {
    if (heights.isEmpty()) return 0
    val start = from.coerceIn(0, heights.lastIndex)
    var target = start
    var nearest = abs(dragPx)
    var resting = 0f
    for (slot in start + 1..heights.lastIndex) {
        resting += heights[slot]
        val distance = abs(dragPx - resting)
        if (distance <= nearest) {
            nearest = distance
            target = slot
        }
    }
    resting = 0f
    for (slot in start - 1 downTo 0) {
        resting -= heights[slot]
        val distance = abs(dragPx - resting)
        if (distance < nearest) {
            nearest = distance
            target = slot
        }
    }
    return target
}

@Composable
private fun CheckableRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(if (hovered && enabled) DesktopRowHover else Color.Transparent)
            .hoverable(interaction, enabled = enabled)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let {
            it()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) Color.White else DesktopSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = DesktopSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        // A checkmark rather than a checkbox.
        if (checked) {
            Icon(Icons.Rounded.Check, DesktopStrings["enabled", "Enabled"], tint = DesktopAccent, modifier = Modifier.size(18.dp))
        } else {
            Spacer(Modifier.size(18.dp))
        }
    }
}

