package com.music.bitchord.ui.icons

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFHeart
import com.composables.sfsymbols.monochrome.SFHeartFill

/**
 * A broken heart, for Dislike: SF Symbols has a heart with a slash through it
 * but no broken one, so this is the SF heart split down the middle along a
 * zigzag crack, the two halves pulled a hair apart. Drawn a little lighter
 * than the menus' other symbols: two halves and a crack are more line than
 * one heart, and at Bold they read as a blot.
 *
 * The outline draws the crack's edges as well, so each half reads as a closed
 * piece rather than a ring with two nicks taken out of it.
 */
object BrokenHeart {
    val Outline: ImageVector by lazy { broken(SfSymbols.Monochrome.SFHeart, "bc_broken_heart", outline = true) }
    val Fill: ImageVector by lazy { broken(SfSymbols.Monochrome.SFHeartFill, "bc_broken_heart_fill", outline = false) }

    /** Added to Apple's Regular line — about half what [SfIcons] adds for Bold. */
    private const val WEIGHT = 0.35f

    /** Half the gap between the two halves, in the heart's own units. */
    private const val HALF_GAP = 0.55f

    /** The crack's line, as offsets from the heart's middle at each height. */
    private val crack = listOf(
        0f to -1f,
        0f to 4.4f,
        -1.6f to 9f,
        1.4f to 13.2f,
        -1f to 17.6f,
        0f to 26f,
    )

    /** [regular] is Apple's own Regular heart, outline or filled. */
    private fun broken(regular: ImageVector, name: String, outline: Boolean): ImageVector {
        // Padded the way [SfIcons] pads its symbols.
        val pad = SfIcons.BOLD_STROKE / 2f
        val width = regular.viewportWidth + pad * 2f
        val height = regular.viewportHeight + pad * 2f
        val middle = pad + regular.viewportWidth / 2f
        val right = width + 2f
        val bottom = height + 2f
        val heartShape = heartFillNodes()
        // An outline's edges are as thick as the outline itself.
        val edgeWidth = REGULAR_LINE + WEIGHT

        // On the same canvas as every other symbol — see [SfIcons.CANVAS].
        return SfIcons.canvas(name, width, height) {
            for (side in listOf(-1f, 1f)) {
                val shift = side * HALF_GAP
                addGroup(clipPathData = halfClip(middle + shift, if (side < 0) -2f else right, bottom))
                addGroup(translationX = pad, translationY = pad)
                SfIcons.copyInto(this, regular.root, extraStroke = WEIGHT)
                clearGroup()
                if (outline) {
                    // The crack's edge on this half, kept inside the heart.
                    addGroup(translationX = pad, translationY = pad, clipPathData = heartShape)
                    path(
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = edgeWidth,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Round,
                    ) {
                        val edge = middle - pad + side * (HALF_GAP + edgeWidth / 2f)
                        crack.forEachIndexed { i, (dx, y) ->
                            if (i == 0) moveTo(edge + dx, y - pad) else lineTo(edge + dx, y - pad)
                        }
                    }
                    clearGroup()
                }
                clearGroup()
            }
        }
    }

    /** Everything on one side of the crack, shifted to [crackX] — the side [edgeX] is on. */
    private fun halfClip(crackX: Float, edgeX: Float, bottom: Float): List<PathNode> =
        PathBuilder().apply {
            moveTo(edgeX, -2f)
            crack.forEach { (dx, y) -> lineTo(crackX + dx, y) }
            lineTo(edgeX, bottom)
            close()
        }.nodes

    /** The filled heart's silhouette, for keeping the crack's edges inside it. */
    private fun heartFillNodes(): List<PathNode> {
        val nodes = mutableListOf<PathNode>()
        fun collect(group: VectorGroup) {
            for (node in group) {
                when (node) {
                    is VectorGroup -> collect(node)
                    is VectorPath -> nodes += node.pathData
                }
            }
        }
        collect(SfSymbols.Monochrome.SFHeartFill.root)
        return nodes.ifEmpty { addPathNodes("") }
    }

    /** SF's Regular heart outline, in its own units. */
    private const val REGULAR_LINE = 1.73f
}
