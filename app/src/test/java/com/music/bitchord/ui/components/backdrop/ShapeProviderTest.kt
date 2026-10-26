package com.music.bitchord.ui.components.backdrop

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.music.bitchord.ui.components.backdrop.internal.ShapeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ShapeProviderTest {
    @Test
    fun fontScaleChangeProducesTheFreshOutline() {
        val source = environmentShape()
        val cached = ShapeProvider { source }.shape
        val size = Size(100f, 100f)
        val normal = cached.createOutline(size, LayoutDirection.Ltr, Density(2f, 1f))
        assertSame(normal, cached.createOutline(size, LayoutDirection.Ltr, Density(2f, 1f)))

        val larger = Density(2f, 1.5f)
        assertEquals(
            source.createOutline(size, LayoutDirection.Ltr, larger),
            cached.createOutline(size, LayoutDirection.Ltr, larger),
        )
    }

    @Test
    fun resizingChangingDirectionAndReplacingShapeMatchFreshGeometry() {
        var source = environmentShape()
        val cached = ShapeProvider { source }.shape
        for (replacement in listOf(1f, 2f, 1f)) {
            source = environmentShape(replacement)
            for (size in listOf(Size(100f, 80f), Size(140f, 100f))) {
                for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                    for (density in listOf(Density(1f, 1f), Density(3f, 1.2f))) {
                        val expected = source.createOutline(size, direction, density)
                        val actual = cached.createOutline(size, direction, density)
                        assertEquals(expected, actual)
                        assertSame(actual, cached.createOutline(size, direction, density))
                    }
                }
            }
        }
    }

    private fun environmentShape(scale: Float = 1f) = object : Shape {
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
            val inset = 4f * density.density * scale
            val top = 8f * density.fontScale
            val left = if (layoutDirection == LayoutDirection.Ltr) inset else inset * 2f
            return Outline.Rectangle(Rect(left, top, size.width - inset, size.height - top))
        }
    }
}
