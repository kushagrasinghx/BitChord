/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player.spatialflow

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.component.BottomSheetState

@Composable
fun BoxScope.SpatialFlowFloatingArtwork(
    state: BottomSheetState,
    mediaMetadata: MediaMetadata,
    queueWindows: List<androidx.media3.common.Timeline.Window>,
    currentWindowIndex: Int,
    artUrl: String?,
    isPlaying: Boolean,
    fullArtworkRect: Rect?,
    miniArtworkRect: Rect?,
    lyricsOpen: Boolean,
    queueOpen: Boolean = false,
    artworkActive: Boolean = true,
    onPlaySongAtWindow: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (fullArtworkRect == null || fullArtworkRect.width <= 0f) {
        return
    }
    val full = fullArtworkRect

    val mini = miniArtworkRect ?: full
    val miniRectMissing = miniArtworkRect == null

    val queueFade by animateFloatAsState(
        targetValue = if (queueOpen) 0f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = 300f,
            ),
        label = "SfFloatingArtworkQueueFade",
    )
    val lyricsFade by animateFloatAsState(
        targetValue = if (lyricsOpen) 0f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = 420f,
            ),
        label = "SfFloatingArtworkLyricsFade",
    )

    val pagerVisible by remember(artworkActive, miniRectMissing) {
        derivedStateOf {
            val rawP = state.progress.coerceIn(0f, 1f)
            val p = if (miniRectMissing) 1f else rawP
            val lyricsSuppress = lerp(1f, lyricsFade, p)
            val queueSuppress = lerp(1f, queueFade, p)
            val fallbackFade = if (miniRectMissing) (2f * rawP).coerceIn(0f, 1f) else 1f
            val layerAlpha =
                (if (artworkActive) 1f else 0f) * lyricsSuppress * queueSuppress * fallbackFade
            layerAlpha > 0.01f
        }
    }

    Box(
        modifier =
            modifier
                .align(Alignment.TopStart)

                .offset { IntOffset(full.left.roundToInt(), full.top.roundToInt()) }
                .size(with(androidx.compose.ui.platform.LocalDensity.current) { full.width.toDp() })
                .graphicsLayer {
                    val rawP = state.progress.coerceIn(0f, 1f)

                    val p = if (miniRectMissing) 1f else rawP

                    val lyricsSuppress = lerp(1f, lyricsFade, p)
                    val queueSuppress = lerp(1f, queueFade, p)
                    val fallbackFade = if (miniRectMissing) (2f * rawP).coerceIn(0f, 1f) else 1f
                    alpha = (if (artworkActive) 1f else 0f) * lyricsSuppress * queueSuppress * fallbackFade
                    if (alpha <= 0.01f) return@graphicsLayer

                    val scale = lerp(mini.width / full.width, 1f, p)
                    scaleX = scale
                    scaleY = scale

                    val targetCentreX = lerp(mini.center.x, full.center.x, p)
                    val targetCentreY = lerp(mini.center.y, full.center.y, p)
                    translationX = targetCentreX - full.center.x
                    translationY = targetCentreY - full.center.y

                    val cornerPx = lerp(mini.width / 2f, 16.dp.toPx(), p)
                    shape = RoundedCornerShape(cornerPx)
                    clip = true

                    shadowElevation = lerp(0f, 16.dp.toPx(), p)
                },
    ) {
        if (pagerVisible) {
            SpatialFlowArtworkPager(
                mediaMetadata = mediaMetadata,
                queueWindows = queueWindows,
                currentWindowIndex = currentWindowIndex,
                userScrollEnabled = state.progress > 0.95f && !lyricsOpen && !queueOpen,
                artUrl = artUrl,
                isPlaying = isPlaying,
                cornerRadius = 16.dp,
                shadowElevation = 0.dp,
                onPlaySongAtWindow = onPlaySongAtWindow,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction
