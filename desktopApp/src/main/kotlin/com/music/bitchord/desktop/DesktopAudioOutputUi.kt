package com.music.bitchord.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.icons.BitChordIcons

/**
 * Where playback is sent. Android routes through `AudioManager`; here the choice is a mixer.
 *
 * Enumerated when the dialog opens rather than held: devices come and go with what is plugged in.
 */
@Composable
internal fun DesktopAudioOutputDialog(onDismiss: () -> Unit) {
    val selected by DesktopAudioDevices.selected.collectAsState()
    val devices = remember { DesktopAudioDevices.available() }
    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 440) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                DesktopStrings["audio_output", "Audio output"],
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                DesktopStrings["d_audio_output_subtitle", "A change takes effect on the next track."],
                color = DesktopSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            OutputRow(
                name = DesktopStrings["d_system_default", "System default"],
                description = "",
                chosen = selected == DesktopAudioDevices.SYSTEM_DEFAULT,
            ) {
                DesktopAudioDevices.select(DesktopAudioDevices.SYSTEM_DEFAULT)
                onDismiss()
            }
            devices.forEach { device ->
                OutputRow(device.name, device.description, chosen = selected == device.id) {
                    DesktopAudioDevices.select(device.id)
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun OutputRow(name: String, description: String, chosen: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (description.isNotBlank()) {
                Text(
                    description,
                    color = DesktopSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (chosen) {
            Spacer(Modifier.width(10.dp))
            Icon(BitChordIcons.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}
