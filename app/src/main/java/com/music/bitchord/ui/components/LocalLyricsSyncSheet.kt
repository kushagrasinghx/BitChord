package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.download.LocalLyricsSync
import com.music.bitchord.ui.icons.SfIcons
import java.util.Locale

/**
 * The batch side of local lyrics: one sheet with the three passes the store
 * supports, the run's progress, and what happened to each track that failed.
 *
 * It reads [LocalLyricsSync] directly rather than taking a snapshot, because a
 * run is a background job: the sheet may be opened after it started, closed
 * while it continues, and opened again to the same answer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalLyricsSyncSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val progress by LocalLyricsSync.progress.collectAsStateWithLifecycle()
    val statuses by LocalLyricsSync.statuses.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val synced = statuses.values.count { it.state == LocalLyricsSync.State.SYNCED }
    val present = statuses.values.count { it.state == LocalLyricsSync.State.ALREADY_PRESENT }
    val missing = statuses.values.count { it.state == LocalLyricsSync.State.NO_LYRICS }
    val failed = statuses.values.filter { it.state == LocalLyricsSync.State.FAILED }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sync_local_lyrics),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.sync_local_lyrics_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)

            if (progress.running) {
                RunningPanel(progress, onCancel = { LocalLyricsSync.cancel(context) })
            } else {
                Column(Modifier.padding(vertical = 8.dp)) {
                    ActionRow(
                        icon = Icons.Rounded.Sync,
                        label = stringResource(R.string.sync_all),
                        description = stringResource(R.string.sync_all_subtitle),
                        accent = MaterialTheme.colorScheme.primary,
                    ) {
                        LocalLyricsSync.start(context, LocalLyricsSync.Mode.ALL)
                    }
                    ActionRow(
                        icon = SfIcons.Notes,
                        label = stringResource(R.string.sync_missing),
                        description = stringResource(R.string.sync_missing_subtitle),
                        accent = MaterialTheme.colorScheme.primary,
                    ) {
                        LocalLyricsSync.start(context, LocalLyricsSync.Mode.MISSING)
                    }
                    ActionRow(
                        icon = Icons.Rounded.Refresh,
                        label = stringResource(R.string.retry_failed),
                        description = stringResource(R.string.retry_failed_subtitle),
                        accent = if (failed.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        enabled = failed.isNotEmpty(),
                    ) {
                        LocalLyricsSync.start(context, LocalLyricsSync.Mode.RETRY_FAILED)
                    }
                }

                progress.error?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                    )
                }
                if (progress.finished && progress.error == null && progress.total > 0) {
                    Text(
                        text = stringResource(R.string.sync_done),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                    )
                }

                if (synced + present + missing + failed.size > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    SummaryRow(synced, present, missing, failed.size)
                }

                if (failed.isNotEmpty()) {
                    SheetHeading(stringResource(R.string.sync_failed_tracks).uppercase(Locale.getDefault()))
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(failed, key = { it.uri }) { status ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 22.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = status.title.ifBlank { status.uri },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    status.message?.let { message ->
                                        Text(
                                            text = message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RunningPanel(progress: LocalLyricsSync.Progress, onCancel: () -> Unit) {
    Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = progress.currentTitle ?: stringResource(R.string.sync_preparing),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (progress.total > 0) {
                        stringResource(R.string.sync_progress, progress.done, progress.total)
                    } else {
                        stringResource(R.string.sync_preparing)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.sync_runs_in_background),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
private fun SummaryRow(synced: Int, present: Int, missing: Int, failed: Int) {
    Column(Modifier.padding(horizontal = 22.dp, vertical = 4.dp)) {
        SummaryLine(stringResource(R.string.sync_status_synced), synced, MaterialTheme.colorScheme.primary)
        SummaryLine(stringResource(R.string.sync_status_already_saved), present, MaterialTheme.colorScheme.onSurfaceVariant)
        SummaryLine(stringResource(R.string.sync_status_no_lyrics), missing, MaterialTheme.colorScheme.onSurfaceVariant)
        SummaryLine(stringResource(R.string.sync_status_failed), failed, MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun SummaryLine(label: String, count: Int, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = color,
        )
    }
}
