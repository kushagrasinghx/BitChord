package com.music.bitchord.ui.player

import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerContentMeasurePolicyTest {
    private val scope = object : MeasureScope {
        override val density = 1f
        override val fontScale = 1f
        override val layoutDirection = LayoutDirection.Ltr
    }

    private class PlayerContent : Measurable {
        var active = true
        var measures = 0
        override val parentData: Any? = null
        override fun measure(constraints: Constraints): Placeable {
            check(active) { "measure is called on a deactivated node" }
            measures++
            return object : Placeable() {
                init { measuredSize = IntSize(constraints.maxWidth, constraints.maxHeight) }
                override fun get(alignmentLine: AlignmentLine) = AlignmentLine.Unspecified
                override fun placeAt(
                    position: IntOffset,
                    zIndex: Float,
                    layerBlock: (GraphicsLayerScope.() -> Unit)?,
                ) = Unit
            }
        }
        override fun minIntrinsicWidth(height: Int) = error("Unexpected intrinsic measure")
        override fun maxIntrinsicWidth(height: Int) = error("Unexpected intrinsic measure")
        override fun minIntrinsicHeight(width: Int) = error("Unexpected intrinsic measure")
        override fun maxIntrinsicHeight(width: Int) = error("Unexpected intrinsic measure")
    }

    @Test fun dismissalAndHiddenRemeasureNeverMeasureDeactivatedContent() {
        val content = PlayerContent()
        val children = listOf(content)
        val active = PlayerContentMeasurePolicy(true)
        val hidden = PlayerContentMeasurePolicy(false)
        with(active) { scope.measure(children, Constraints.fixed(1080, 2400)) }
        content.active = false
        // Include a constraint change after closing, such as an inset update.
        for (height in listOf(2400, 2300, 2400)) {
            val result = with(hidden) { scope.measure(children, Constraints.fixed(1080, height)) }
            assertEquals(1080, result.width)
            assertEquals(height, result.height)
            result.placeChildren()
        }
        assertEquals(1, content.measures)
        content.active = true
        val reopened = with(active) { scope.measure(children, Constraints.fixed(1080, 2400)) }
        assertEquals(2400, reopened.height)
        assertEquals(2, content.measures)
    }

    @Test fun hostCanCloseBeforeItHasAnyChildren() {
        val result = with(PlayerContentMeasurePolicy(false)) {
            scope.measure(emptyList(), Constraints.fixed(1080, 2400))
        }
        assertEquals(1080, result.width)
        assertEquals(2400, result.height)
        result.placeChildren()
    }
}
