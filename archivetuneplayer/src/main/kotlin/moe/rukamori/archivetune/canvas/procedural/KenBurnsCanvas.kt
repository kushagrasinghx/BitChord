/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Ported from Sekai Tune (github.com/rgsekai/sekai-tune) canvas module — GPLv3.
 */

package moe.rukamori.archivetune.canvas.procedural

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale

@Composable
fun KenBurnsCanvas(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "KenBurnsCanvasTransition")

    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "KenBurnsScale",
    )

    val translationFractionX by infiniteTransition.animateFloat(
        initialValue = -0.03f,
        targetValue = 0.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "KenBurnsTranslateX",
    )

    val translationFractionY by infiniteTransition.animateFloat(
        initialValue = 0.02f,
        targetValue = -0.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "KenBurnsTranslateY",
    )

    Box(
        modifier = modifier
            .clipToBounds()
            .graphicsLayer {
                val currentScale = if (isPlaying) scale else 1.05f
                scaleX = currentScale
                scaleY = currentScale
                translationX = if (isPlaying) size.width * translationFractionX else 0f
                translationY = if (isPlaying) size.height * translationFractionY else 0f
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
