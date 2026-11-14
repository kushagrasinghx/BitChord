package com.music.bitchord.ui.player

import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth

/** Keeps retained, deactivated player nodes out of every hidden measure pass. */
internal class PlayerContentMeasurePolicy(private val active: Boolean) : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        // ReusableContentHost keeps these children in the tree when inactive.
        // They must not be measured, even when the scaffold updates its anchors
        // or an ancestor requests layout after dismissal.
        if (!active) return layout(constraints.minWidth, constraints.minHeight) {}

        // Match the former Box's TopStart placement and loose child minimums.
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val children = measurables.map { it.measure(childConstraints) }
        val width = constraints.constrainWidth(children.maxOfOrNull { it.width } ?: 0)
        val height = constraints.constrainHeight(children.maxOfOrNull { it.height } ?: 0)
        return layout(width, height) {
            children.forEach { it.placeRelative(0, 0) }
        }
    }
}
