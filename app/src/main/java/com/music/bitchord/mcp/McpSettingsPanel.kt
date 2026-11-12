package com.music.bitchord.mcp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.spotify.LocalPlaylistStore
import com.music.bitchord.ui.components.IosSwitch

@Composable
fun McpSettingsPanel() {
    val context = LocalContext.current
    remember(context) { McpSettings.init(context); true }
    val status by McpServer.status.collectAsStateWithLifecycle()
    val playlists by LocalPlaylistStore.playlists.collectAsStateWithLifecycle()
    var revision by remember { mutableIntStateOf(0) }
    var warnLan by remember { mutableStateOf(false) }
    var rotate by remember { mutableStateOf(false) }
    var showToken by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    // Revision invalidates preference-backed reads after an explicit edit.
    key(revision) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("MCP · AI access", style = MaterialTheme.typography.titleMedium)
            Text("Phone-local AI is supported over loopback by default. All permissions start denied.", style = MaterialTheme.typography.bodySmall)
            McpToggle("Enable MCP", McpSettings.enabled) { McpSettings.enabled = it; revision++ }
            Text("Server: $status", style = MaterialTheme.typography.bodySmall)
            SelectionContainer { Text("http://localhost:${McpSettings.port}/mcp") }
            McpToggle("Allow LAN connections", McpSettings.lanEnabled) {
                if (it) warnLan = true else { McpSettings.lanEnabled = false; revision++ }
            }
            if (McpSettings.lanEnabled) Text("LAN enabled: HTTP sends the bearer token in cleartext. Never expose this port to the internet or an untrusted network.", color = MaterialTheme.colorScheme.error)
            Text("Bearer token", style = MaterialTheme.typography.titleSmall)
            if (showToken) SelectionContainer { Text(McpSettings.token, style = MaterialTheme.typography.bodySmall) }
            Row {
                TextButton(onClick = { showToken = !showToken }) { Text(if (showToken) "Hide" else "Reveal") }
                TextButton(onClick = { clipboard.setText(AnnotatedString(McpSettings.token)) }) { Text("Copy") }
                TextButton(onClick = { rotate = true }) { Text("Rotate token") }
            }
            Text("Permissions", style = MaterialTheme.typography.titleSmall)
            McpSettings.permissions.forEach { permission ->
                McpToggle(permission.replace('_', ' '), McpSettings.allowed(permission)) {
                    McpSettings.setAllowed(permission, it); revision++
                }
            }
            Text("Local playlist allowlist", style = MaterialTheme.typography.titleSmall)
            Text("Playlist read/write also requires the matching permission above. New playlists are never automatically allowed.", style = MaterialTheme.typography.bodySmall)
            if (playlists.isEmpty()) Text("No local playlists available.")
            playlists.forEach { playlist ->
                McpToggle(playlist.title, McpSettings.playlistAllowed(playlist.id)) {
                    McpSettings.setPlaylistAllowed(playlist.id, it); revision++
                }
            }
        }
    }
    if (warnLan) AlertDialog(
        onDismissRequest = { warnLan = false },
        title = { Text("Allow cleartext LAN access?") },
        text = { Text("The bearer token and music data travel over unencrypted HTTP. Use only a trusted private network. Never forward port 8765 to the internet. Phone-local AI needs no LAN access.") },
        confirmButton = { TextButton(onClick = { McpSettings.lanEnabled = true; warnLan = false; revision++ }) { Text("Allow LAN") } },
        dismissButton = { TextButton(onClick = { warnLan = false }) { Text("Cancel") } },
    )
    if (rotate) AlertDialog(
        onDismissRequest = { rotate = false },
        title = { Text("Rotate bearer token?") },
        text = { Text("Existing clients lose access until configured with the new token.") },
        confirmButton = { TextButton(onClick = { McpSettings.rotateToken(); rotate = false; showToken = false; revision++ }) { Text("Rotate") } },
        dismissButton = { TextButton(onClick = { rotate = false }) { Text("Cancel") } },
    )
}

@Composable
private fun McpToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        IosSwitch(checked = checked, onCheckedChange = onChange)
    }
}
