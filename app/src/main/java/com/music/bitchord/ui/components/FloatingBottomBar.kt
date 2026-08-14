package com.music.bitchord.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings

data class BottomTab(
    val label: String,
    val icon: ImageVector,
)

/**
 * Floating translucent pill navigation, Telegram-flavoured:
 * thick-stroke icons and a springy (slightly overshooting) scale +
 * tinted-capsule selection animation.
 *
 * No blur of its own: the bottom fade blur it floats on is at full strength by
 * the time it reaches this far down, so the pill only needs the tint half of
 * that recipe. The alphas below are the ones [HazeMaterials.thin] applies, so
 * the body still matches the mini player sitting above it.
 */
@Composable
fun FloatingBottomBar(
    tabs: List<BottomTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Fully rounded ends, whatever the bar's height works out to.
    val pillShape = RoundedCornerShape(percent = 50)
    val container = MaterialTheme.colorScheme.surface
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    // With the frosted floor beneath it gone, the pill needs a solid fill of
    // its own instead of a tint meant to sit on top of that blur.
    val glass = if (reduceDynamicBlur) {
        container
    } else {
        container.copy(alpha = if (container.luminance() >= 0.5f) 0.6f else 0.65f)
    }
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 10.dp)
            // Sits close to the gesture bar, Apple-style.
            .padding(bottom = 2.dp)
            .fillMaxWidth()
            .clip(pillShape)
            .background(glass)
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), pillShape)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Equal weights: the gaps stay even however wide the screen is.
        tabs.forEachIndexed { index, tab ->
            BottomBarItem(
                tab = tab,
                selected = index == selectedIndex,
                onClick = { onTabSelected(index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BottomBarItem(
    tab: BottomTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "tabScale",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(200),
        label = "tabTint",
    )
    val capsuleAlpha by animateFloatAsState(
        targetValue = if (selected) 0.14f else 0f,
        animationSpec = tween(200),
        label = "tabCapsule",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            // Matches the bar's own ends rather than a softened rectangle.
            .clip(RoundedCornerShape(percent = 50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .background(MaterialTheme.colorScheme.primary.copy(alpha = capsuleAlpha))
            .padding(vertical = 7.dp),
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = tint,
            // Scale the glyph, not the capsule — a growing capsule would
            // crowd its neighbours now that the slots are fixed widths.
            modifier = Modifier
                .size(25.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
