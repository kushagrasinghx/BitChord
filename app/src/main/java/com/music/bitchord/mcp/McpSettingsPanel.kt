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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.music.bitchord.R
import com.music.bitchord.ui.screens.SettingsGroup
import com.music.bitchord.ui.screens.SettingsRow
import com.music.bitchord.ui.screens.RowDivider
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CancellationException
import com.music.bitchord.data.spotify.LocalPlaylistStore
import com.music.bitchord.data.OfflinePlaylists
import com.music.bitchord.ui.components.IosSwitch

@Composable
fun McpSettingsPanel() {
    val context = LocalContext.current
    remember(context) { McpSettings.init(context); true }
    val status by McpServer.status.collectAsStateWithLifecycle()
    val localPlaylists by LocalPlaylistStore.playlists.collectAsStateWithLifecycle()
    val offlinePlaylists by OfflinePlaylists.playlists.collectAsStateWithLifecycle()
    val catalogWarnings by McpPlaylistCatalog.warnings.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var catalog by remember { mutableStateOf<List<McpPlaylistEntry>>(emptyList()) }
    var catalogLoading by remember { mutableStateOf(true) }
    var catalogFailure by remember { mutableStateOf(false) }
    var catalogDenied by remember { mutableStateOf(false) }
    var refreshCatalog by remember { mutableIntStateOf(0) }
    LaunchedEffect(lifecycleOwner, refreshCatalog) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Never display remote names from a previous account during refresh.
            catalog = emptyList()
            catalogLoading = true
            catalogFailure = false
            catalogDenied = false
            try {
                catalog = withContext(Dispatchers.IO) { McpPlaylistCatalog.load(context.applicationContext) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                catalogDenied = true
            } catch (_: Exception) {
                catalogFailure = true
            } finally {
                catalogLoading = false
            }
            // Relaunch on every resume, not continuously while the screen is visible.
            awaitCancellation()
        }
    }
    val playlists = remember(catalog, localPlaylists, offlinePlaylists) {
        val localEntries = localPlaylists.map { McpPlaylistEntry(it.id, it.title, "local", false) }
        val offlineEntries = offlinePlaylists.map { McpPlaylistEntry(it.browseId, it.title, "offline", true) }
        (localEntries + offlineEntries + catalog.filterNot { it.source in setOf("local", "offline") })
            .distinctBy { it.id }
    }
    var playlistQuery by rememberSaveable { mutableStateOf("") }
    val locale = LocalConfiguration.current.locales[0]
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
    // Invalidate only preference snapshots, never the UI subtree or text-field focus.
    val enabled = remember(revision) { McpSettings.enabled }
    val lanEnabled = remember(revision) { McpSettings.lanEnabled }
    val token = remember(revision) { McpSettings.token }
    val blocklist = remember(revision) { McpSettings.playlistMode == "blocklist" }
    val permissions = remember(revision) { McpSettings.permissions.associateWith { McpSettings.allowed(it) } }
    val selection = remember(revision, playlists) {
        PlaylistSelectionModel(playlists.map { it.id }, playlists.filter { McpSettings.playlistSelected(it.id) }.map { it.id }.toSet())
    }
    val filteredPlaylists = remember(playlists, playlistQuery, locale) {
        PlaylistSelectionModel.filterAndSort(playlists, playlistQuery, locale) { it.title }
    }
        Column(Modifier.fillMaxWidth()) {
            SettingsGroup(footer = stringResource(R.string.mcp_intro)) {
                val statusLabel = when {
                    status.startsWith("Listening on ") -> R.string.mcp_status_on
                    status == "Unable to start server" || status == "Invalid server settings" -> R.string.mcp_status_error
                    enabled -> R.string.mcp_status_starting
                    else -> R.string.mcp_status_off
                }
                McpToggle(
                    stringResource(R.string.mcp_enable), enabled,
                    subtitle = stringResource(statusLabel),
                ) { McpSettings.enabled = it; McpForegroundService.syncFromForeground(context); revision++ }
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
                McpToggle(stringResource(R.string.mcp_lan), lanEnabled) {
                    if (it) warnLan = true else { McpSettings.lanEnabled = false; revision++ }
                }
            }
            SettingsGroup(header = stringResource(R.string.mcp_token), footer = if (lanEnabled) stringResource(R.string.mcp_lan_warning) else null) {
                SettingsRow(
                    title = stringResource(R.string.mcp_token),
                    subtitleContent = {
                        Column {
                            Text(if (showToken) token else "••••••••••••••••", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
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
                    McpToggle(stringResource(label), permissions[permission] == true) {
                        McpSettings.setAllowed(permission, it); revision++
                    }
                }
            }
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
                OutlinedTextField(
                    value = playlistQuery,
                    onValueChange = { playlistQuery = it },
                    label = { Text(stringResource(R.string.mcp_playlist_search), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )
                val allSelected = selection.allSelected
                val selectionState = when {
                    allSelected -> ToggleableState.On
                    !selection.partiallySelected -> ToggleableState.Off
                    else -> ToggleableState.Indeterminate
                }
                val toggleAll = {
                    val (ids, selected) = selection.bulkSelection()
                    McpSettings.setPlaylistsSelected(ids, selected)
                    revision++
                }
                SettingsRow(
                    title = stringResource(if (allSelected) R.string.mcp_deselect_all else R.string.mcp_select_all),
                    subtitle = stringResource(R.string.mcp_select_all_hint),
                    enabled = playlists.isNotEmpty(),
                    onClick = { toggleAll() },
                    trailing = {
                        TriStateCheckbox(
                            state = selectionState,
                            enabled = playlists.isNotEmpty(),
                            onClick = { toggleAll() },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    },
                )
                if (catalogLoading) {
                    RowDivider()
                    SettingsRow(title = stringResource(R.string.mcp_catalog_loading), trailing = {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    })
                }
                if (catalogDenied || catalogFailure) {
                    RowDivider()
                    SettingsRow(
                        title = stringResource(if (catalogDenied) R.string.mcp_catalog_denied else R.string.mcp_catalog_error),
                        subtitle = stringResource(R.string.mcp_catalog_incomplete),
                    )
                }
                if (!catalogLoading) catalogWarnings.distinct().forEach { warning ->
                    RowDivider()
                    val warningLabel = when (warning) {
                        "youtube_catalog_unavailable" -> R.string.mcp_catalog_youtube_error
                        "spotify_catalog_unavailable" -> R.string.mcp_catalog_spotify_error
                        else -> R.string.mcp_catalog_error
                    }
                    SettingsRow(title = stringResource(warningLabel), subtitle = stringResource(R.string.mcp_catalog_incomplete))
                }
                RowDivider()
                SettingsRow(
                    title = stringResource(R.string.mcp_catalog_retry),
                    enabled = !catalogLoading,
                    onClick = { refreshCatalog++ },
                )
                if (playlists.isEmpty() && !catalogLoading && !catalogFailure && !catalogDenied && catalogWarnings.isEmpty()) {
                    RowDivider()
                    SettingsRow(title = stringResource(R.string.mcp_no_playlists))
                } else if (playlists.isNotEmpty() && filteredPlaylists.isEmpty()) {
                    RowDivider()
                    SettingsRow(title = stringResource(R.string.mcp_playlist_no_matches))
                }
                filteredPlaylists.forEach { playlist ->
                    RowDivider()
                    val sourceLabel = when (playlist.source.lowercase(java.util.Locale.ROOT)) {
                        "local" -> stringResource(R.string.mcp_source_local)
                        "offline" -> stringResource(R.string.mcp_source_offline)
                        "youtube" -> stringResource(R.string.mcp_source_youtube)
                        "spotify" -> stringResource(R.string.mcp_source_spotify)
                        else -> playlist.source
                    }
                    val playlistSubtitle = if (playlist.readOnly) {
                        "$sourceLabel · ${stringResource(R.string.mcp_read_only)}"
                    } else sourceLabel
                    McpToggle(playlist.title, playlist.id in selection.selectedIds, subtitle = playlistSubtitle) {
                        McpSettings.setPlaylistSelected(playlist.id, it); revision++
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
private fun McpToggle(label: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    SettingsRow(
        title = label,
        subtitle = subtitle,
        onClick = { onChange(!checked) },
        trailing = { IosSwitch(checked = checked, onCheckedChange = onChange) },
    )
}
