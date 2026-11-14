package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * An icon in [tint], read as it is drawn rather than as it is composed: a tint
 * that animates — a glass glyph turning dark — redraws the glyph and
 * recomposes nothing. Otherwise Material's Icon: 24dp unless sized, the
 * picture fitted and centred in its box.
 */
@Composable
fun TintedIcon(
    painter: Painter,
    contentDescription: String?,
    tint: ColorProducer,
    modifier: Modifier = Modifier,
) {
    val semantics = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    } else {
        Modifier
    }
    Box(
        modifier
            .defaultMinSize(DefaultIconSize, DefaultIconSize)
            .then(semantics)
            .drawBehind {
                val intrinsic = painter.intrinsicSize
                val drawn = if (intrinsic.isSpecified && intrinsic.width > 0f && intrinsic.height > 0f) {
                    val scale = min(size.width / intrinsic.width, size.height / intrinsic.height)
                    Size(intrinsic.width * scale, intrinsic.height * scale)
                } else {
                    size
                }
                translate((size.width - drawn.width) / 2f, (size.height - drawn.height) / 2f) {
                    with(painter) { draw(drawn, colorFilter = ColorFilter.tint(tint())) }
                }
            },
    )
}

/** [TintedIcon] of [imageVector]. */
@Composable
fun TintedIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    tint: ColorProducer,
    modifier: Modifier = Modifier,
) = TintedIcon(rememberVectorPainter(imageVector), contentDescription, tint, modifier)

private val DefaultIconSize = 24.dp
