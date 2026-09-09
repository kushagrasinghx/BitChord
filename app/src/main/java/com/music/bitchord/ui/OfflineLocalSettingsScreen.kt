package com.music.bitchord.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.bitchord.offline.OfflineLocalStore

@Composable
fun OfflineLocalSettingsScreen(store: OfflineLocalStore, onBack: () -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Text("Settings", style = MaterialTheme.typography.headlineLarge)
        }
        Text("Appearance", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        OfflineLocalStore.Theme.entries.forEach { option ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(store.theme() == option, onClick = { store.setTheme(option); onRefresh() })
                Text(option.name.lowercase().replaceFirstChar { it.uppercase() })
            }
        }
        Text("Library", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        Text("Minimum track length: ${store.minimumTrackSeconds()} seconds")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
            listOf(0, 10, 30, 60).forEach { s -> OutlinedButton(onClick = { store.setMinimumTrackSeconds(s); onRefresh() }) { Text("${s}s") } }
        }
        Text("Unknown artist tracks", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(store.showUnknownArtists(), onClick = { store.setShowUnknownArtists(true); onRefresh() }); Text("Show")
            RadioButton(!store.showUnknownArtists(), onClick = { store.setShowUnknownArtists(false); onRefresh() }); Text("Hide")
        }
        Text("History", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        Text("Up to 100 recently played tracks are stored locally.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = store::clearHistory, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Icon(Icons.Rounded.DeleteSweep, null); Text("Clear listening history", Modifier.padding(start = 8.dp)) }
    }
}
