package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.AccountChannel
import com.music.bitchord.data.settings.AppSettings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * Which channel to listen as — the Google account's own, or one of its brand
 * channels.
 *
 * Same frosted alert as [AppLanguageDialog], and single-select for the same
 * reason: there is one active identity, so a tap applies it and closes rather
 * than waiting on a Done.
 *
 * The selected row is the app's own choice, not YouTube's. A channel YouTube's
 * switcher reports as active is labelled as such but never ticked, because the
 * tick answers "which one is this app acting as", and before a choice is made
 * that is decided by the shell rather than by the listener.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun AccountChannelDialog(
    channels: List<AccountChannel>,
    loading: Boolean,
    selectedKey: String?,
    hazeState: HazeState,
    onSelect: (AccountChannel) -> Unit,
    onChooseInYouTube: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(ALERT_CORNER)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SCRIM_COLOR)
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
                        Modifier.hazeEffect(
                            state = hazeState,
                            style = HazeMaterials.regular(MaterialTheme.colorScheme.surface),
                        )
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
                    text = "Listen as",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.W600,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = when {
                        loading && channels.isEmpty() -> "Looking up your channels…"
                        channels.isEmpty() ->
                            "Couldn't list this account's channels. You can still pick " +
                                "one in YouTube Music itself."
                        else ->
                            "Your library, likes and history come from the channel " +
                                "you pick here."
                    },
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                if (loading && channels.isEmpty()) {
                    Spacer(Modifier.padding(top = 12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            channels.forEach { channel ->
                AlertRule()
                ChannelRow(
                    channel = channel,
                    selected = channel.key == selectedKey,
                    onClick = {
                        onSelect(channel)
                        onDismiss()
                    },
                )
            }

            AlertRule()
            // The route that cannot be wrong about what exists. YouTube's own
            // Accounts list is the authority on which channels this login has,
            // and picking there means the session is read from a page already
            // showing the chosen channel rather than assembled from ids.
            AlertAction(
                label = "Choose in YouTube Music",
                emphasised = channels.isEmpty(),
                onClick = onChooseInYouTube,
            )
            AlertRule()
            AlertAction(label = "Close", emphasised = false, onClick = onDismiss)
        }
    }
}

@Composable
private fun ChannelRow(channel: AccountChannel, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ACTION_HEIGHT)
            .background(
                if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f) else Color.Transparent,
            )
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (channel.thumbnailUrl != null) {
            AsyncImage(
                model = channel.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .thumbnailBorder(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The handle when there is one; otherwise what kind of channel it
            // is, which is the part a listener with two of them is choosing by.
            val subtitle = channel.subtitle.ifBlank {
                if (channel.pageId != null) "Brand channel" else "Your channel"
            }
            Text(
                text = if (channel.activeOnWeb) "$subtitle · default" else subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}
