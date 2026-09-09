package com.music.bitchord.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.bitchord.offline.OfflineLocalStore

@Composable
fun OfflineSettingsScreen(store: OfflineLocalStore, onBack: () -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Text("Settings", style = MaterialTheme.typography.headlineLarge)
        }
        Text("Appearance", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        OfflineLocalStore.Theme.entries.forEach { option ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = store.theme() == option, onClick = { store.setTheme(option); onRefresh() })
                Text(option.name.lowercase().replaceFirstChar { it.uppercase() })
            }
        }
        Text("Library", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        Text("Minimum track length", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            listOf(0, 10, 30, 60).forEach { seconds ->
                OutlinedButton(onClick = { store.setMinimumTrackSeconds(seconds); onRefresh() }) { Text("${seconds}s") }
            }
        }
        Text("Unknown artist tracks", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = store.showUnknownArtists(), onClick = { store.setShowUnknownArtists(true); onRefresh() })
            Text("Show")
            RadioButton(selected = !store.showUnknownArtists(), onClick = { store.setShowUnknownArtists(false); onRefresh() })
            Text("Hide")
        }
        Text("History", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        Text("Up to 100 recently played tracks are kept locally.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { store.clearHistory() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Icon(Icons.Rounded.DeleteSweep, null)
            Text("Clear listening history", modifier = Modifier.padding(start = 8.dp))
        }
    }
}
