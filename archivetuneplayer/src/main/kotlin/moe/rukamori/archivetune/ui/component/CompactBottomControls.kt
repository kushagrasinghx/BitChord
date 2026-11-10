/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import com.kyant.backdrop.Backdrop

val NavigationBarGlassGlowKey = booleanPreferencesKey("navigationBarGlassGlow")

val NavigationBarGlassGlowIntensityKey = floatPreferencesKey("navigationBarGlassGlowIntensity")

const val NAVIGATION_BAR_GLASS_GLOW_INTENSITY_DEFAULT = 1f

val LocalBottomUiCompactFraction = compositionLocalOf<State<Float>> {
    mutableStateOf(0f)
}

val CompactControlSize = 56.dp

val CompactControlGap = 12.dp

private val GlowEdgeStrokeWidth = 0.75.dp

fun Modifier.glassGlowOverlay(
    strength: Float,
    shape: Shape,
): Modifier =
    if (strength <= 0.01f) {
        this
    } else {
        drawWithContent {
            drawContent()
            if (strength <= 0.01f) return@drawWithContent
            val outline = shape.createOutline(size, layoutDirection, this)
            val corner =
                when (val r = (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius) {
                    null -> CornerRadius(size.height / 2f)
                    else -> CornerRadius(r.x, r.y)
                }
            val stroke = GlowEdgeStrokeWidth.toPx()

            drawRoundRect(
                brush =
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.27f),
                            Color.White.copy(alpha = 0.02f),
                        ),
                    ),
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = corner,
                style = Stroke(stroke),
                alpha = strength,
            )

            drawRoundRect(
                brush =
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.10f),
                        0.45f to Color.Transparent,
                        1f to Color.White.copy(alpha = 0.05f),
                    ),
                cornerRadius = corner,
                alpha = strength,
            )

            drawRoundRect(
                brush =
                    Brush.verticalGradient(
                        0.72f to Color.Transparent,
                        1f to Color.White.copy(alpha = 0.14f),
                    ),
                cornerRadius = corner,
                alpha = strength * 0.7f,
            )
        }
    }

@Composable
fun CompactControlCircle(
    iconRes: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    backdrop: Backdrop?,
    glowStrength: Float,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    val useGlass = backdrop != null
    if (useGlass) {
        Box(
            modifier =
                modifier
                    .size(CompactControlSize)
                    .liquidGlass(
                        backdrop = backdrop,
                        shape = CircleShape,
                        interactive = false,
                        blurRadius = LiquidGlassPillBlurRadius,
                        baseColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    .glassGlowOverlay(glowStrength, CircleShape)
                    .clip(CircleShape)
                    .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = if (tint == Color.Unspecified) liquidGlassContentColor() else tint,
                modifier = Modifier.size(24.dp),
            )
        }
    } else {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
            modifier =
                modifier
                    .size(CompactControlSize)
                    .glassGlowOverlay(glowStrength, CircleShape)
                    .clip(CircleShape)
                    .clickable(onClick = onClick),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = contentDescription,
                    tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
