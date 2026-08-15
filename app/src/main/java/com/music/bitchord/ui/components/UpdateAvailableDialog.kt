package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/** UIAlertController's own metrics: fixed narrow width, 14pt corner, 44pt rows. */
private val ALERT_WIDTH = 270.dp
private val ALERT_CORNER = 14.dp
private val ACTION_HEIGHT = 44.dp

/**
 * The dim behind the alert. Flat on purpose — the glass is the card, and
 * blurring the wallpaper *behind* it too leaves nothing for the card to be
 * frosted against, which is what made this read as a grey box before.
 */
private val SCRIM_COLOR = Color.Black.copy(alpha = 0.28f)

/**
 * Once-per-launch nudge that a newer build is on GitHub Releases — the top
 * bar's [Icons.Rounded.SystemUpdate][androidx.compose.material.icons.rounded.SystemUpdate]
 * icon is the quiet, always-there version of this; this is the one-time,
 * hard-to-miss version shown the moment the check comes back.
 *
 * Shaped like an iOS system alert, which is the same lineage as the rest of the
 * app's Apple Music styling: frosted card, hairline rules, full-width actions
 * stacked under the message rather than a Material button pair in the corner.
 *
 * Sits over the whole app as an overlay rather than an Android [Dialog][androidx.compose.ui.window.Dialog]
 * so its glass can sample the same [HazeState] the rest of the app's frosted
 * surfaces use, the way [FrostedTopBar] and [MiniPlayer] already do.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun UpdateAvailableDialog(
    version: String,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(ALERT_CORNER)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SCRIM_COLOR)
            // Tapping the scrim reads the same as Remind Me Later — nothing
            // about this update is mandatory, so backing out of it should be as
            // easy as getting into it.
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(ALERT_WIDTH)
                .clip(shape)
                .then(
                    if (reduceDynamicBlur) {
                        Modifier.background(MaterialTheme.colorScheme.surface)
                    } else {
                        Modifier.hazeEffect(state = hazeState, style = HazeMaterials.regular(MaterialTheme.colorScheme.surface))
                    },
                )
                // Swallows the tap before it reaches the scrim behind, so
                // touching the card itself never dismisses it.
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 19.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Software Update",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.W600,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "BitChord $version is available to download.",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }

            AlertRule()
            AlertAction(label = "Download Now", emphasised = true, onClick = onUpdate)
            AlertRule()
            AlertAction(label = "Remind Me Later", emphasised = false, onClick = onDismiss)
        }
    }
}

/**
 * Full-bleed action row. Tinted rather than filled, so the two read as equals
 * in weight and only the font differentiates the default action — the alert's
 * whole point is that neither choice is a trap.
 */
@Composable
private fun AlertAction(
    label: String,
    emphasised: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ACTION_HEIGHT)
            // iOS washes the whole row instead of drawing a ripple inside it.
            .background(
                if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f) else Color.Transparent,
            )
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 17.sp,
                fontWeight = if (emphasised) FontWeight.W600 else FontWeight.W400,
            ),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Hairline separator — [HorizontalDivider][androidx.compose.material3.HorizontalDivider]'s 1dp reads as a bar at this scale. */
@Composable
private fun AlertRule() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
    )
}
