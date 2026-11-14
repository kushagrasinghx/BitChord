package com.music.bitchord.ui.components.backdrop.shadow

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Recording decisions use the production cache without requiring Android's renderer. */
class ShadowRecordingTest {
    private val shape = object : Shape {
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
            Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
    }
    private val size = Size(300f, 60f)
    private val density = Density(2f, 1f)

    @Test
    fun alphaAndBlendChangesReuseTheRecordingButDrawingInputChangesReplaceIt() {
        val cache = ShadowRecording()
        var records = 0
        fun draw(shadow: Shadow) {
            cache.recordIfNeeded(shape, size, density, LayoutDirection.Ltr, shadow) { records++ }
        }
        val original = Shadow.Default
        draw(original)
        repeat(50) { draw(original.copy(alpha = it / 50f, blendMode = BlendMode.Plus)) }
        assertEquals(1, records)

        draw(original.copy(color = Color.Red))
        draw(original.copy(radius = 30.dp))
        draw(original.copy(offset = DpOffset(3.dp, 4.dp)))
        draw(original)
        assertEquals(5, records)
        draw(original)
        assertEquals(5, records)
    }

    @Test
    fun geometryEnvironmentAndLifetimeChangesRequireFreshRecordings() {
        val cache = ShadowRecording()
        var records = 0
        fun draw(
            newShape: Shape = shape,
            newSize: Size = size,
            newDensity: Density = density,
            direction: LayoutDirection = LayoutDirection.Ltr,
        ) {
            cache.recordIfNeeded(newShape, newSize, newDensity, direction, Shadow.Default) { records++ }
        }
        draw()
        draw()
        assertEquals(1, records)
        draw(newSize = Size(400f, 60f))
        draw(newDensity = Density(3f, 1f))
        draw(newDensity = Density(3f, 1.5f))
        draw(direction = LayoutDirection.Rtl)
        draw(newShape = object : Shape by shape {})
        assertEquals(6, records)
        cache.invalidate() // A stateful outline changed without replacing its shape.
        draw()
        cache.reset() // Reusable node or newly attached graphics layer.
        draw()
        draw()
        assertEquals(8, records)
    }

    @Test
    fun observedShapeChangesStayTrackedAcrossCacheHits() {
        val inset = mutableFloatStateOf(2f)
        val mutableShape = object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
                Outline.Rectangle(Rect(inset.floatValue, 0f, size.width, size.height))
        }
        val cache = ShadowRecording()
        val observer = SnapshotStateObserver { it() }
        var outline: Outline? = null
        var records = 0
        fun draw() {
            cache.recordIfNeeded(mutableShape, size, density, LayoutDirection.Ltr, Shadow.Default) { inputs ->
                observer.observeReads(cache, { it.invalidate() }) {
                    outline = inputs.shape.createOutline(inputs.size, inputs.layoutDirection, density)
                    records++
                }
            }
        }
        observer.start()
        try {
            draw()
            val first = outline
            repeat(20) { draw() }
            assertEquals(1, records)
            inset.floatValue = 8f
            Snapshot.sendApplyNotifications()
            draw()
            assertEquals(2, records)
            assertNotEquals(first, outline)
            assertEquals(mutableShape.createOutline(size, LayoutDirection.Ltr, density), outline)

            repeat(20) { draw() }
            inset.floatValue = 12f
            Snapshot.sendApplyNotifications()
            draw()
            assertEquals(3, records)
            assertEquals(mutableShape.createOutline(size, LayoutDirection.Ltr, density), outline)
        } finally {
            observer.stop()
            observer.clear()
        }
    }

    @Test
    fun equalReplacementShapesEstablishTheirOwnRecording() {
        class EqualShape : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
                Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
            override fun equals(other: Any?) = other is EqualShape
            override fun hashCode() = 0
        }
        val cache = ShadowRecording()
        var records = 0
        fun draw(source: Shape) {
            cache.recordIfNeeded(source, size, density, LayoutDirection.Ltr, Shadow.Default) { records++ }
        }
        val first = EqualShape()
        val replacement = EqualShape()
        assertEquals(first, replacement)
        draw(first)
        draw(replacement)
        draw(replacement)
        assertEquals(2, records)
    }
}
