/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.material3.Text

@Composable
fun VinylImageCard(
    songTitle: String,
    artistName: String,
    coverArtUrl: String?,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coverPainter =
        rememberAsyncImagePainter(
            ImageRequest
                .Builder(context)
                .data(coverArtUrl)
                .crossfade(true)
                .build(),
        )

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.large,
        color = Color(0xFF051418),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF0A1F24), Color(0xFF051418)),
                    ),
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val canvasSizeDp = minOf(maxWidth, maxHeight)
                val canvasSizePx = with(density) { canvasSizeDp.toPx() }
                val coverSize = canvasSizeDp * 0.46f
                val discSize = canvasSizeDp * 0.46f
                val coverLeft = (canvasSizeDp - coverSize) * 0.5f - coverSize * 0.10f
                val coverTop = canvasSizeDp * 0.18f
                val discLeft = coverLeft + coverSize * 0.62f
                val discTop = coverTop
                val labelRadius = discSize * 0.19f
                val strokePx = with(density) { 1.2.dp.toPx() }

                Box(
                    modifier = Modifier
                        .offset(x = discLeft, y = discTop)
                        .size(discSize),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFF050505)),
                    )
                    Canvas(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        val grooveCount = 28
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val maxRadius = size.minDimension / 2f
                        repeat(grooveCount) { i ->
                            val radius = maxRadius * ((i + 1f) / (grooveCount + 1f))
                            drawCircle(
                                color = Color.White.copy(alpha = 0.08f),
                                radius = radius,
                                center = center,
                                style = Stroke(width = strokePx),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(labelRadius * 2)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFF7DD3D8), Color(0xFF4FB6BC)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .width(labelRadius * 1.7f)
                                .padding(horizontal = 2.dp),
                        ) {
                            Text(
                                text = songTitle,
                                color = Color(0xFF0A1F24),
                                fontSize = (canvasSizePx * 0.038f / density.fontScale).sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = artistName,
                                color = Color(0xFF0A1F24),
                                fontSize = (canvasSizePx * 0.030f / density.fontScale).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(discSize * 0.024f)
                            .clip(CircleShape)
                            .background(Color.Black),
                    )
                }

                Box(
                    modifier = Modifier
                        .offset(x = coverLeft, y = coverTop)
                        .size(coverSize)
                        .clip(MaterialTheme.shapes.medium)
                        .border(1.dp, Color.White, MaterialTheme.shapes.medium),
                ) {
                    Image(
                        painter = coverPainter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = canvasSizeDp * 0.06f)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = songTitle,
                        color = Color.White,
                        fontSize = (canvasSizePx * 0.038f / density.fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = artistName,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = (canvasSizePx * 0.028f / density.fontScale).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
