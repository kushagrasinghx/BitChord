/*
 * Vendored from Kyant0/backdrop v2.0.0 (io.github.kyant0:backdrop)
 * https://github.com/Kyant0/backdrop — Copyright 2025 Kyant0, Apache License 2.0
 *
 * Vendored so the library ships as source with this app (binary AARs compiled
 * against older Compose broke at runtime) and to add a backdrop resolution
 * scale for cheaper effect rendering. KMP expect/actual declarations were
 * merged into this single Android source set. Package renamed accordingly.
 */
package com.music.bitchord.ui.components.backdrop.internal

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

@Immutable
internal class ShapeProvider(val shapeBlock: () -> Shape) {

    // Vendored addition: equal when built around the same block. Every
    // drawBackdrop call wraps its block in a new provider, and with identity
    // equality the backdrop, shadow and highlight elements all compared
    // unequal on every recomposition of their caller — each one an update,
    // a dropped outline and a redraw. A caller that remembers its block now
    // gets the same elements back and its nodes are left alone.
    override fun equals(other: Any?): Boolean =
        other is ShapeProvider && other.shapeBlock === shapeBlock

    override fun hashCode(): Int = shapeBlock.hashCode()

    private var _shape: Shape? = null
    private var _outline: Outline? = null
    private var _size: Size = Size.Unspecified
    private var _layoutDirection: LayoutDirection? = null
    private var _density: Float? = null
    private var _fontScale: Float? = null

    val innerShape
        get() = shapeBlock()

    val shape = object : Shape {

        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val shape = shapeBlock()
            if (_shape != shape) {
                _shape = shape
                _outline = null
            }
            if (_outline == null || _size != size || _layoutDirection != layoutDirection ||
                _density != density.density || _fontScale != density.fontScale
            ) {
                _size = size
                _layoutDirection = layoutDirection
                _density = density.density
                _fontScale = density.fontScale
                _outline = shape.createOutline(size, layoutDirection, density)
            }

            return _outline!!
        }
    }
}
