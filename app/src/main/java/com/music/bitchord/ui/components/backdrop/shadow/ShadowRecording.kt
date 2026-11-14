package com.music.bitchord.ui.components.backdrop.shadow

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.ceil

/** The pixels in the shadow layer, excluding its live alpha and blend mode. */
internal data class ShadowRecordingInputs(
    val shape: Shape,
    val size: Size,
    val density: Float,
    val fontScale: Float,
    val layoutDirection: LayoutDirection,
    val radius: Float,
    val offsetX: Float,
    val offsetY: Float,
    val color: Color,
) {
    val layerSize: IntSize
        get() = IntSize(
            ceil(size.width + radius * 4f + offsetX).toInt(),
            ceil(size.height + radius * 4f + offsetY).toInt(),
        )
}

/** A single display-list recording, replaced when its drawing inputs change. */
internal class ShadowRecording {
    private var recorded: ShadowRecordingInputs? = null
    private var dirty = true

    fun invalidate() { dirty = true }

    fun reset() {
        recorded = null
        dirty = true
    }

    fun recordIfNeeded(
        shape: Shape,
        size: Size,
        density: Density,
        layoutDirection: LayoutDirection,
        shadow: Shadow,
        record: (ShadowRecordingInputs) -> Unit,
    ) {
        val next = with(density) {
            ShadowRecordingInputs(
                shape, size, density.density, fontScale, layoutDirection,
                shadow.radius.toPx(), shadow.offset.x.toPx(), shadow.offset.y.toPx(), shadow.color,
            )
        }
        // Equal shapes may be different stateful instances. A replacement must
        // establish observation of its own createOutline() state reads.
        if (!dirty && recorded?.shape === shape && recorded == next) return
        record(next)
        recorded = next
        dirty = false
    }
}
