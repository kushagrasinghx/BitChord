package com.music.bitchord.ui.components

import android.os.Build
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.theme.ArtworkPalette

/**
 * The colour a surface takes from the artwork it is about: a flat tint
 * everywhere, with the artwork itself blurred across the top and dissolved
 * down into that tint.
 *
 * For surfaces that show no artwork of their own — a sheet, whose whole top is
 * this wash. A page that has the real sleeve above it wants a flat
 * [ArtworkPalette.wash] instead: a second, blurrier copy of a picture already
 * on screen only reads as the picture again.
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
