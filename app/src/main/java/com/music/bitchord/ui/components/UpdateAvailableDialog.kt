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
import com.music.bitchord.data.AppUpdateChecker
import com.music.bitchord.data.settings.AppSettings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/** UIAlertController's own metrics: fixed narrow width, 14pt corner, 44pt rows. */
internal val ALERT_WIDTH = 270.dp
internal val ALERT_CORNER = 14.dp
internal val ACTION_HEIGHT = 44.dp

/**
 * The dim behind the alert. Flat on purpose — the glass is the card, and
 * blurring the wallpaper *behind* it too leaves nothing for the card to be
 * frosted against, which is what made this read as a grey box before.
 */
internal val SCRIM_COLOR = Color.Black.copy(alpha = 0.28f)

private val DOWNLOAD_ROW_HEIGHT = 4.dp

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
 * The update round trip happens here rather than in a browser: Download pulls
 * the release's APK into the app cache (progress fills the hairline under the
 * message), then Install hands it to the system installer. Where the release
 * carries no APK at all, the actions fall back to opening the releases page.
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
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenReleasePage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val state by AppUpdateChecker.download.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(ALERT_CORNER)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SCRIM_COLOR)
            // Tapping the scrim reads the same as Remind Me Later — nothing
            // about this update is mandatory, so backing out of it should be as
            // easy as getting into it. Mid-download it only closes the sheet;
            // the download keeps going and the top-bar icon reopens this.
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
                    text = when (state) {
                        is AppUpdateChecker.DownloadState.Downloading ->
                            "Downloading bitchord $version…"
                        is AppUpdateChecker.DownloadState.Ready ->
                            "bitchord $version is ready to install."
                        is AppUpdateChecker.DownloadState.Failed ->
                            "Couldn't download bitchord $version."
                        else ->
                            "bitchord $version is available to download."
                    },
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                // The download's progress, drawn as a thin fill across a
                // hairline track — same weight as [AlertRule], so it reads as
                // part of the card rather than a widget bolted onto it.
                val downloading = state as? AppUpdateChecker.DownloadState.Downloading
                if (downloading != null || state is AppUpdateChecker.DownloadState.Failed) {
                    Box(
                        Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .height(DOWNLOAD_ROW_HEIGHT)
                            .clip(RoundedCornerShape(DOWNLOAD_ROW_HEIGHT / 2))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
                    ) {
                        if (downloading != null && downloading.fraction > 0f) {
                            Box(
                                Modifier
                                    .fillMaxWidth(downloading.fraction)
                                    .height(DOWNLOAD_ROW_HEIGHT)
                                    .clip(RoundedCornerShape(DOWNLOAD_ROW_HEIGHT / 2))
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
                if (state is AppUpdateChecker.DownloadState.Failed) {
                    Text(
                        text = (state as AppUpdateChecker.DownloadState.Failed).message,
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            AlertRule()
            when (state) {
                is AppUpdateChecker.DownloadState.Downloading -> {
                    AlertAction(label = "Cancel", emphasised = false, onClick = onCancelDownload)
                }
                is AppUpdateChecker.DownloadState.Ready -> {
                    AlertAction(label = "Install Now", emphasised = true, onClick = onInstall)
                    AlertRule()
                    AlertAction(label = "Later", emphasised = false, onClick = onDismiss)
                }
                is AppUpdateChecker.DownloadState.Failed -> {
                    AlertAction(label = "Try Again", emphasised = true, onClick = onDownload)
                    AlertRule()
                    AlertAction(label = "Open Releases Page", emphasised = false, onClick = onOpenReleasePage)
                }
                else -> {
                    AlertAction(label = "Download Now", emphasised = true, onClick = onDownload)
                    AlertRule()
                    AlertAction(label = "Remind Me Later", emphasised = false, onClick = onDismiss)
                }
            }
        }
    }
}

/**
 * Full-bleed action row. Tinted rather than filled, so the two read as equals
 * in weight and only the font differentiates the default action — the alert's
 * whole point is that neither choice is a trap.
 */
@Composable
internal fun AlertAction(
    label: String,
    emphasised: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
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
                enabled = enabled,
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
            color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f),
        )
    }
}

/** Hairline separator — [HorizontalDivider][androidx.compose.material3.HorizontalDivider]'s 1dp reads as a bar at this scale. */
@Composable
internal fun AlertRule() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
    )
}
