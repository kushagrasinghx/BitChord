package com.music.bitchord.mcp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.music.bitchord.R
import com.music.bitchord.ui.screens.SettingsGroup
import com.music.bitchord.ui.screens.SettingsRow
import com.music.bitchord.ui.screens.RowDivider
import androidx.compose.ui.platform.LocalContext
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
    var warnBlocklist by remember { mutableStateOf(false) }
    fun copy(value: String, sensitive: Boolean = false) {
        val clip = ClipData.newPlainText(if (sensitive) "MCP token" else "MCP address", value)
        if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
        Toast.makeText(context, context.getString(R.string.mcp_copied), Toast.LENGTH_SHORT).show()
    }
    // Revision invalidates preference-backed reads after an explicit edit.
    key(revision) {
        Column(Modifier.fillMaxWidth()) {
            SettingsGroup(footer = stringResource(R.string.mcp_intro)) {
                McpToggle(stringResource(R.string.mcp_enable), McpSettings.enabled) { McpSettings.enabled = it; McpForegroundService.syncFromForeground(context); revision++ }
                RowDivider()
                SettingsRow(title = stringResource(R.string.mcp_server), subtitle = status.toString())
                RowDivider()
                SettingsRow(
                    title = stringResource(R.string.mcp_address),
                    subtitleContent = {
                        Column {
                            Text("http://localhost:${McpSettings.port}/mcp", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.mcp_tap_copy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = { copy("http://localhost:${McpSettings.port}/mcp") },
                )
                RowDivider()
                McpToggle(stringResource(R.string.mcp_lan), McpSettings.lanEnabled) {
                    if (it) warnLan = true else { McpSettings.lanEnabled = false; revision++ }
                }
            }
            SettingsGroup(header = stringResource(R.string.mcp_token), footer = if (McpSettings.lanEnabled) stringResource(R.string.mcp_lan_warning) else null) {
                SettingsRow(
                    title = stringResource(R.string.mcp_token),
                    subtitleContent = {
                        Column {
                            Text(if (showToken) McpSettings.token else "••••••••••••••••", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.mcp_tap_copy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = { copy(McpSettings.token, sensitive = true) },
                )
                RowDivider()
                SettingsRow(title = stringResource(if (showToken) R.string.mcp_hide else R.string.mcp_reveal), onClick = { showToken = !showToken })
                RowDivider()
                SettingsRow(title = stringResource(R.string.mcp_rotate_token), onClick = { rotate = true })
            }
            SettingsGroup(header = stringResource(R.string.mcp_permissions)) {
                McpSettings.permissions.forEachIndexed { index, permission ->
                    if (index > 0) RowDivider()
                    val label = when (permission) {
                        "now_playing" -> R.string.mcp_now_playing
                        "history" -> R.string.mcp_history
                        "queue" -> R.string.mcp_queue
                        "playback" -> R.string.mcp_playback
                        "seek" -> R.string.mcp_seek
                        "search" -> R.string.mcp_search
                        "playlist_read" -> R.string.mcp_playlist_read
                        "playlist_create" -> R.string.mcp_playlist_create
                        else -> R.string.mcp_playlist_write
                    }
                    McpToggle(stringResource(label), McpSettings.allowed(permission)) {
                        McpSettings.setAllowed(permission, it); revision++
                    }
                }
            }
            val blocklist = McpSettings.playlistMode == "blocklist"
            SettingsGroup(header = stringResource(R.string.mcp_playlist_mode)) {
                SettingsRow(
                    title = stringResource(R.string.mcp_allowlist),
                    value = if (!blocklist) "✓" else null,
                    onClick = { McpSettings.playlistMode = "allowlist"; revision++ },
                )
                RowDivider()
                SettingsRow(
                    title = stringResource(R.string.mcp_blocklist),
                    value = if (blocklist) "✓" else null,
                    onClick = { if (!blocklist) warnBlocklist = true },
                )
            }
            SettingsGroup(
                header = stringResource(if (blocklist) R.string.mcp_blocked_playlists else R.string.mcp_playlists),
                footer = stringResource(if (blocklist) R.string.mcp_blocklist_hint else R.string.mcp_playlist_hint),
            ) {
                if (playlists.isEmpty()) SettingsRow(title = stringResource(R.string.mcp_no_playlists))
                playlists.forEachIndexed { index, playlist ->
                    if (index > 0) RowDivider()
                    McpToggle(playlist.title, McpSettings.playlistSelected(playlist.id)) {
                        McpSettings.setPlaylistSelected(playlist.id, it); revision++
                    }
                }
            }
        }
    }
    if (warnBlocklist) AlertDialog(
        onDismissRequest = { warnBlocklist = false },
        title = { Text(stringResource(R.string.mcp_blocklist_title), color = MaterialTheme.colorScheme.onSurface) },
        text = { Text(stringResource(R.string.mcp_blocklist_warning), color = MaterialTheme.colorScheme.onSurface) },
        confirmButton = {
            TextButton(onClick = { McpSettings.playlistMode = "blocklist"; warnBlocklist = false; revision++ }) {
                Text(stringResource(R.string.mcp_enable_blocklist), color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = { warnBlocklist = false }) {
                Text(stringResource(R.string.mcp_cancel), color = MaterialTheme.colorScheme.primary)
            }
        },
    )
    if (warnLan) AlertDialog(
        onDismissRequest = { warnLan = false },
        title = { Text(stringResource(R.string.mcp_lan_title), color = MaterialTheme.colorScheme.onSurface) },
        text = { Text(stringResource(R.string.mcp_lan_body), color = MaterialTheme.colorScheme.onSurface) },
        confirmButton = { TextButton(onClick = { McpSettings.lanEnabled = true; warnLan = false; revision++ }) { Text(stringResource(R.string.mcp_allow_lan), color = MaterialTheme.colorScheme.primary) } },
        dismissButton = { TextButton(onClick = { warnLan = false }) { Text(stringResource(R.string.mcp_cancel), color = MaterialTheme.colorScheme.primary) } },
    )
    if (rotate) AlertDialog(
        onDismissRequest = { rotate = false },
        title = { Text(stringResource(R.string.mcp_rotate_title), color = MaterialTheme.colorScheme.onSurface) },
        text = { Text(stringResource(R.string.mcp_rotate_body), color = MaterialTheme.colorScheme.onSurface) },
        confirmButton = { TextButton(onClick = { McpSettings.rotateToken(); rotate = false; showToken = false; revision++ }) { Text(stringResource(R.string.mcp_rotate), color = MaterialTheme.colorScheme.primary) } },
        dismissButton = { TextButton(onClick = { rotate = false }) { Text(stringResource(R.string.mcp_cancel), color = MaterialTheme.colorScheme.primary) } },
    )
}

@Composable
private fun McpToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingsRow(
        title = label,
        onClick = { onChange(!checked) },
        trailing = { IosSwitch(checked = checked, onCheckedChange = onChange) },
    )
}
