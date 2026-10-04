package com.music.pexpo.ui.components

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.pexpo.data.model.CARD_ART_PX
import com.music.pexpo.data.model.artworkAt
import com.music.pexpo.data.settings.AppSettings
import com.music.pexpo.ui.theme.ArtworkPalette

/**
 * The colour a surface takes from the artwork it is about: a flat tint
 * everywhere, with the artwork itself blurred across the top and dissolved
 * down into that tint.
 *
 * For surfaces that show no artwork of their own — a sheet, whose whole top is
 * this wash. A page that has the real sleeve above it wants [ArtworkWash]
 * instead: a second, blurrier copy of a picture already on screen only reads as
 * the picture again.
 *
 * Sized entirely by [modifier] — inside a wrap-content parent, pass
 * `Modifier.matchParentSize()` so the wash follows the content rather than
 * stretching it to the full screen.
 *
 * The blur is a one-off: nothing animates it, so it is rasterised once and
 * then only composited. It still needs API 31 for `RenderEffect`; below that,
 * and when the user has asked for less dynamic blur, the flat tint carries the
 * surface on its own.
 */
@Composable
fun ArtworkBackdrop(
    palette: ArtworkPalette,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    /** How far down the surface the blurred artwork reaches. */
    washFraction: Float = 0.72f,
    /**
     * The artwork size to fetch, which should be whichever one the surface
     * *already* has on screen.
     *
     * Nothing here survives a 72dp blur, so resolution is worth nothing and a
     * cache hit is worth everything: ask for a size the caller isn't already
     * showing and the wash sits on the theme colour until a fresh copy comes
     * over the wire. A sheet opened from a list row passes `ROW_ART_PX` and is
     * tinted on the frame it opens.
     */
    artPx: Int = CARD_ART_PX,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val canBlur = !reduceDynamicBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(modifier.background(palette.background)) {
        if (canBlur && imageUrl != null) {
            AsyncImage(
                model = imageUrl.artworkAt(artPx),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(washFraction)
                    // Overscaled so the blur's clamped edges never reach a
                    // visible one, exactly as the player's mesh does.
                    .graphicsLayer {
                        scaleX = 1.4f
                        scaleY = 1.4f
                    }
                    .blur(72.dp, BlurredEdgeTreatment.Unbounded),
            )
        }
        val isDark = androidx.compose.foundation.isSystemInDarkTheme()
        val alpha0 = if (isDark) 0.40f else 0.08f
        val alpha1 = if (isDark) 0.55f else 0.25f
        val alpha2 = if (isDark) 0.85f else 0.72f

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to palette.background.copy(alpha = alpha0),
                        0.20f to palette.background.copy(alpha = alpha1),
                        0.55f to palette.background.copy(alpha = alpha2),
                        washFraction to palette.background,
                    ),
                ),
        )
    }
}

/**
 * The colour a detail page is made of below its artwork.
 *
 * Holds [ArtworkPalette.wash] — the colour the sleeve's own blur ends on —
 * across the height the artwork occupies and a little past it, so the page
 * reads as that blur carrying on rather than as a second surface starting, and
 * then settles into the flat page tint on the way down.
 *
 * Two soft blobs of the artwork's other colours keep that from being a dead
 * vertical ramp. They are radial gradients rather than a blurred copy of the
 * sleeve, which is the whole point: a full-screen blur of a picture that is
 * *also on screen* still reads as the picture, and the faces in it show through
 * the song list. Nothing here is an image, so there is nothing to recognise —
 * and no full-screen `RenderEffect` behind a scrolling list either, so it costs
 * the same on every API level and under "reduce dynamic blur".
 */
@Composable
fun ArtworkWash(
    palette: ArtworkPalette,
    modifier: Modifier = Modifier,
    /**
     * Where the artwork above stops, as a fraction of the surface's height —
     * the point past which the wash is allowed to start letting go.
     *
     * Defaulted past where a detail header lands on a phone: both headers are
     * very nearly square and run the full width, so the artwork's own aspect
     * ratio and the band overhanging it reach a little under 60% of the way
     * down. The wash has to still be at full strength where that band hands
     * over to it, or the join it was drawn to hide reappears as a step in
     * brightness.
     */
    washFraction: Float = 0.62f,
) {
    Canvas(modifier.fillMaxSize()) {
        // Eased rather than run straight from [washFraction] to the bottom: a
        // gradient that changes slope at a stop has a visible line at that
        // stop, however close the two colours are either side of it. The eye
        // finds the kink, not the colours. These stops round it off.
        drawRect(
            Brush.verticalGradient(
                0f to palette.wash,
                washFraction to palette.wash,
                lerp(washFraction, 1f, 0.35f) to lerp(palette.wash, palette.background, 0.12f),
                lerp(washFraction, 1f, 0.70f) to lerp(palette.wash, palette.background, 0.55f),
                1f to palette.background,
            ),
        )
        // Placed just under where the sleeve ends and off opposite edges, so
        // the first thing below the artwork is the widest part of the mesh
        // rather than a horizon line across it.
        blob(palette.accent.copy(alpha = 0.13f), Offset(0.12f, washFraction + 0.08f), 0.80f)
        blob(palette.elevated.copy(alpha = 0.30f), Offset(0.96f, washFraction + 0.30f), 0.95f)
    }
}

/** One mesh lobe: a colour at its centre, gone by [radiusFraction] of the width. */
private fun DrawScope.blob(color: Color, at: Offset, radiusFraction: Float) {
    val center = Offset(at.x * size.width, at.y * size.height)
    val radius = size.width * radiusFraction
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0f)),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
