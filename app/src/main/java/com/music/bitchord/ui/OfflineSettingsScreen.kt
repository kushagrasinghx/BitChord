package com.music.bitchord.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.theme.LiquidGlassPreferences

@Composable
fun OfflineSettingsScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val enabled by LiquidGlassPreferences.enabled(context)
    val strength by LiquidGlassPreferences.strength(context)

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.displaySmall)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (enabled) Icons.Rounded.AutoAwesome else Icons.Rounded.VisibilityOff,
                contentDescription = null,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Liquid Glass", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (enabled) "Glass effects are enabled across the player"
                    else "Glass effects are disabled",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { LiquidGlassPreferences.setEnabled(context, it) },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Glass strength", style = MaterialTheme.typography.titleMedium)
                Text("${(strength * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Slider(
                value = strength,
                onValueChange = { LiquidGlassPreferences.setStrength(context, it) },
                valueRange = 0f..1f,
                enabled = enabled,
            )
            Text(
                "Higher values increase blur, refraction and edge highlights. Changes apply immediately.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
