package com.music.bitchord.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.data.sources.SourceConfig
import com.music.bitchord.data.sources.SourceHealth
import com.music.bitchord.data.sources.SourceKind
import com.music.bitchord.data.sources.SourceRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Where the app is allowed to get audio from.
 *
 * The order is fixed rather than something to argue with: a module source,
 * when one is configured, is tried first — it's the one the user pointed at
 * on purpose — and YouTube Music is tried second, since it needs no setup and
 * has the full catalogue behind it. Nothing on this screen downloads code,
 * and nothing on it can teach the app a new way to behave after it has
 * shipped — a module supplies audio, not instructions.
 */
@Composable
fun SourcesScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val configs by SourceRegistry.configs.collectAsStateWithLifecycle()
    val lossless by AppSettings.losslessAudio.collectAsStateWithLifecycle()
    val wifiQuality by AppSettings.audioQualityWifi.collectAsStateWithLifecycle()
    val cellularQuality by AppSettings.audioQualityCellular.collectAsStateWithLifecycle()
    val metered by AppSettings.meteredConnection.collectAsStateWithLifecycle()

    /** Last known reachability per source, filled in as the probes come back. */
    val health = remember { mutableStateMapOf<String, SourceHealth>() }
    var editing by remember { mutableStateOf<SourceConfig?>(null) }
    val scope = rememberCoroutineScope()

    val module = configs.firstOrNull { it.kind == SourceKind.MODULE }
    val youtube = configs.first { it.kind == SourceKind.YOUTUBE }

    // Re-probed whenever the module config changes — which is when its
    // answer could have changed and when the user is most likely to be
    // looking.
    LaunchedEffect(module?.id, module?.baseUrl) {
        val config = module ?: return@LaunchedEffect
        val source = SourceRegistry.instance(config.id) ?: return@LaunchedEffect
        if (!config.isComplete) return@LaunchedEffect
        health[config.id] = withContext(Dispatchers.IO) {
            runCatching { source.health() }
                .getOrElse { SourceHealth.Unreachable(it.message ?: "Failed") }
        }
    }

    /** Whether the ceiling in force right now would cap a lossless stream anyway. */
    val cappedByQuality = (if (metered == true) cellularQuality else wifiQuality) != AudioQuality.HIGH
    val anyLosslessSource = module?.enabled == true && module.isComplete

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        Text(
            text = "Sources",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
        )

        SettingsGroup(
            header = "Lossless audio",
            // Said plainly because the alternative is a switch that appears to
            // do something and doesn't. YouTube publishes no lossless
            // rendition of anything, so on a stock install this toggle is
            // inert until a module source that can serve lossless is added
            // below.
            footer = when {
                !anyLosslessSource ->
                    "Nothing enabled below can serve lossless yet. Add a module source below, " +
                        "and tracks it holds a lossless rendition of will play as the file itself " +
                        "rather than as a transcode."
                cappedByQuality ->
                    "Currently overridden: the quality ceiling for this connection is set below " +
                        "High, and that budget wins. Tracks are being transcoded."
                else ->
                    "Asks the module source for the file it holds instead of a transcode of it. " +
                        "Costs considerably more data than High, and does nothing when it has no " +
                        "lossless rendition to give."
            },
        ) {
            SettingsRow(
                icon = Icons.Rounded.GraphicEq,
                title = "Prefer lossless",
                subtitle = "FLAC and ALAC straight from the source",
                badge = "Overridden".takeIf { lossless && cappedByQuality },
                trailing = {
                    Switch(
                        checked = lossless,
                        onCheckedChange = AppSettings::setLosslessAudio,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                },
                onClick = { AppSettings.setLosslessAudio(!lossless) },
            )
        }

        SettingsGroup(
            header = "Sources — tried in this order",
            footer = "A module source that doesn't have the track, or can't be reached, is " +
                "stepped over rather than failing playback — YouTube Music plays it instead. " +
                "With lossless on, the module is offered a YouTube track's recording first if " +
                "it can serve it bit-exact.",
        ) {
            if (module != null) {
                SourceRow(
                    position = 1,
                    config = module,
                    health = health[module.id],
                    onClick = { editing = module },
                    onToggle = { SourceRegistry.setEnabled(module.id, it) },
                )
            } else {
                SettingsRow(
                    icon = Icons.Rounded.Add,
                    title = "Add a module source",
                    subtitle = SourceKind.MODULE.detail,
                    onClick = { editing = SourceConfig(kind = SourceKind.MODULE) },
                )
            }
            RowDivider()
            SourceRow(
                position = if (module != null) 2 else 1,
                config = youtube,
                health = null,
                onClick = null,
                onToggle = { SourceRegistry.setEnabled(youtube.id, it) },
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    editing?.let { config ->
        ServerEditorDialog(
            config = config,
            onDismiss = { editing = null },
            onSave = { saved ->
                if (SourceRegistry.config(saved.id) == null) {
                    SourceRegistry.add(saved)
                } else {
                    SourceRegistry.update(saved)
                }
                editing = null
            },
            onDelete = {
                SourceRegistry.remove(config.id)
                health.remove(config.id)
                editing = null
            },
            probe = { candidate ->
                // Probed through a throwaway instance rather than the stored
                // one: the point of Test is to check what has been *typed*,
                // which is not yet what is saved, and testing the saved copy
                // would cheerfully report success for the old URL.
                withContext(Dispatchers.IO) {
                    runCatching { SourceRegistry.probeCandidate(candidate) }
                        .getOrElse { SourceHealth.Unreachable(it.message ?: "Failed") }
                }
            },
            scope = scope,
        )
    }
}

@Composable
private fun SourceRow(
    position: Int,
    config: SourceConfig,
    health: SourceHealth?,
    onClick: (() -> Unit)?,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .heightIn(min = 60.dp)
            .padding(horizontal = ROW_INSET, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$position",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(18.dp)
                .alpha(if (config.enabled) 1f else 0.4f),
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = when (config.kind) {
                SourceKind.MODULE -> Icons.Rounded.Extension
                SourceKind.YOUTUBE -> Icons.Rounded.PlayCircle
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .size(ICON_SIZE)
                .alpha(if (config.enabled) 1f else 0.4f),
        )
        Spacer(Modifier.width(ICON_GAP))
        Column(
            Modifier
                .weight(1f)
                .alpha(if (config.enabled) 1f else 0.4f),
        ) {
            Text(
                text = config.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = config.statusLine(health),
                style = MaterialTheme.typography.bodyMedium,
                color = when (health) {
                    // Only a rejection is coloured. A server that is merely
                    // down will be up again without anyone doing anything,
                    // and painting that red trains people to ignore the
                    // colour by the time it means something.
                    is SourceHealth.Rejected -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = config.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

/** The second line of a row: what this source is, or what is wrong with it. */
private fun SourceConfig.statusLine(health: SourceHealth?): String = when {
    !isComplete -> "Tap to finish setting up"
    health is SourceHealth.Ok -> listOfNotNull(
        health.detail,
        kind.labels.take(3).joinToString(" · "),
    ).joinToString(" — ")
    health is SourceHealth.Rejected -> health.reason
    health is SourceHealth.Unreachable -> "Can't reach it right now — ${health.reason}"
    kind.needsServer -> "Checking…"
    else -> kind.labels.take(3).joinToString(" · ")
}

/**
 * Add or edit the module source.
 *
 * Test is offered rather than required: an index that happens to be asleep is
 * still worth saving, and refusing to store it until it answers would make
 * setting one up from a coffee shop impossible.
 */
@Composable
private fun ServerEditorDialog(
    config: SourceConfig,
    onDismiss: () -> Unit,
    onSave: (SourceConfig) -> Unit,
    onDelete: () -> Unit,
    probe: suspend (SourceConfig) -> SourceHealth,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val isNew = SourceRegistry.config(config.id) == null
    var label by remember { mutableStateOf(config.label) }
    var baseUrl by remember { mutableStateOf(config.baseUrl) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SourceHealth?>(null) }

    val candidate = config.copy(label = label.trim(), baseUrl = baseUrl.trim())

    AlertDialog(
        onDismissRequest = { if (!testing) onDismiss() },
        title = { Text(if (isNew) "Add ${config.kind.label.lowercase()}" else config.displayName) },
        text = {
            Column {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it; result = null },
                    label = { Text("Link") },
                    placeholder = { Text("https://example.com/modules/index.json") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Paste the URL of a Convx-compatible module index JSON. The index " +
                        "lists JS plugins that can search and stream from services like Tidal, " +
                        "Qobuz, Apple Music and more.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                result?.let { health ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = when (health) {
                            is SourceHealth.Ok ->
                                listOfNotNull("Connected", health.detail).joinToString(" — ")
                            is SourceHealth.Rejected -> health.reason
                            is SourceHealth.Unreachable -> health.reason
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (health.isOk) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }

                if (!isNew) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.Start)) {
                        Text("Remove this source", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        testing = true
                        result = null
                        scope.launch {
                            result = probe(candidate)
                            testing = false
                        }
                    },
                    enabled = !testing && candidate.isComplete,
                ) {
                    Text(if (testing) "Testing…" else "Test")
                }
                TextButton(
                    onClick = { onSave(candidate) },
                    enabled = !testing && candidate.isComplete,
                ) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !testing) { Text("Cancel") }
        },
    )
}
