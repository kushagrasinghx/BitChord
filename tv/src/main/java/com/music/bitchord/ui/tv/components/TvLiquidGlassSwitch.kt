package com.music.bitchord.ui.tv.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.tv.theme.AppleSpringPreset
import com.music.bitchord.ui.tv.theme.appleSpring

/**
 * 1:1 Apple iOS Liquid Glass Toggle Switch.
 *
 * Designed from reference asset media_1790736442117.png:
 * - Rounded translucent pill track: Vibrant iOS Emerald Green (#34C759) when ON, frosted smoke when OFF.
 * - Sliding Liquid Glass Knob: Translucent rounded glass capsule with glowing specular rim highlight around perimeter.
 * - Fluid Apple physics spring animation with TV D-Pad focus ring.
 */
@Composable
fun TvLiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Smooth fluid offset for thumb
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 2.dp,
        animationSpec = appleSpring(AppleSpringPreset.Snappy),
        label = "switchThumbOffset",
    )

    // Animated track color - Clean White when checked
    val trackColor by animateColorAsState(
        targetValue = when {
            checked -> Color.White // Clean white switch
            else -> Color.White.copy(alpha = 0.16f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "switchTrackColor",
    )

    // Focus scale animation
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.10f else 1.0f,
        animationSpec = appleSpring(AppleSpringPreset.Snappy),
        label = "switchScale",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .width(56.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(trackColor)
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.22f),
                shape = RoundedCornerShape(16.dp),
            )
            .then(
                if (enabled && onCheckedChange != null) {
                    Modifier
                        .focusable(interactionSource = interactionSource)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) {
                            onCheckedChange(!checked)
                        }
                } else Modifier
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Clean Modern Sliding Knob - Liquid Glass Specular Rim Effect Removed
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(28.dp)
                .shadow(
                    elevation = if (checked) 4.dp else 2.dp,
                    shape = CircleShape,
                    spotColor = Color.Black.copy(alpha = 0.35f),
                )
                .clip(CircleShape)
                .background(
                    if (checked) {
                        Color(0xFF141416) // High contrast clean dark knob on solid white track
                    } else {
                        Color.White
                    }
                ),
        )
    }
}
