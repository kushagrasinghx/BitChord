package com.music.bitchord.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.components.ReverbControls

/**
 * The reverb's settings page: the switch in the header, as the equaliser has
 * it, and the phone's controls ([ReverbControls]) below — the same presets and
 * parameters over the same [com.music.bitchord.playback.ReverbSettings].
 */
@Composable
internal fun DesktopReverbDialog(onDismiss: () -> Unit) {
    val enabled by DesktopReverbSettings.enabled.collectAsState()
    val settings by DesktopReverbSettings.settings.collectAsState()

    DesktopDialogPanel(onDismiss = onDismiss, maxWidth = 560) {
        Row(
            Modifier.fillMaxWidth().padding(start = panelInset(22.dp), end = panelInset(18.dp), top = 18.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    DesktopStrings["reverb", "Reverb"],
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                Text(
                    DesktopStrings["reverb_subtitle", "Place the music in a room, hall or plate"],
                    style = MaterialTheme.typography.bodySmall,
                    color = DesktopSecondary,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = DesktopReverbSettings::setEnabled,
                colors = desktopSwitchColors(),
            )
        }

        // Dimmed and deaf to the pointer while off, as the equaliser is, so
        // nobody drags a slider wondering why nothing changed.
        val alpha by animateFloatAsState(if (enabled) 1f else 0.38f, label = "reverbEnabled")
        Column(
            Modifier
                .graphicsLayer { this.alpha = alpha }
                .then(if (enabled) Modifier else Modifier.pointerInput(Unit) {})
                .then(desktopPanelBody(cardMax = 520.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = panelInset(22.dp), vertical = 14.dp),
        ) {
            ReverbControls(settings = settings, onChange = DesktopReverbSettings::setSettings)
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = panelInset(20.dp), vertical = 14.dp)) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(DesktopStrings["done", "Done"], color = Color.White) }
        }
    }
}
