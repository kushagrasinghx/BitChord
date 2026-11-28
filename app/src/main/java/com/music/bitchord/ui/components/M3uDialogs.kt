package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.data.playlist.M3uFormat
import com.music.bitchord.data.playlist.M3uImportIssueKind
import com.music.bitchord.data.playlist.M3uImportPreview
import com.music.bitchord.ui.theme.AccentRed
import dev.chrisbanes.haze.HazeState

@Composable
fun M3uExportDialog(
    hazeState: HazeState,
    playlistTitle: String,
    isRemotePlaylist: Boolean,
    onExport: (M3uFormat) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedFormat by remember { mutableStateOf(M3uFormat.M3U8) }

    AlertScaffold(hazeState = hazeState, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 19.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.m3u_export_title),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = playlistTitle,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.m3u_export_subtitle),
                modifier = Modifier.padding(top = 8.dp, bottom = 14.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FormatChoiceChip(
                    label = stringResource(R.string.m3u_format_m3u8),
                    selected = selectedFormat == M3uFormat.M3U8,
                    onClick = { selectedFormat = M3uFormat.M3U8 },
                )
                FormatChoiceChip(
                    label = stringResource(R.string.m3u_format_m3u),
                    selected = selectedFormat == M3uFormat.M3U,
                    onClick = { selectedFormat = M3uFormat.M3U },
                )
            }

            if (isRemotePlaylist) {
                Text(
                    text = stringResource(R.string.m3u_export_yt_note),
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 15.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        AlertRule()
        AlertAction(
            label = stringResource(R.string.m3u_choose_location),
            emphasised = true,
            onClick = { onExport(selectedFormat) },
        )
        AlertRule()
        AlertAction(
            label = stringResource(R.string.cancel),
            emphasised = false,
            onClick = onDismiss,
        )
    }
}

@Composable
private fun FormatChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) AccentRed.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            color = if (selected) AccentRed else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun M3uImportDialog(
    hazeState: HazeState,
    preview: M3uImportPreview,
    onConfirmSave: (title: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var playlistTitle by remember(preview) { mutableStateOf(preview.suggestedTitle) }
    val canSave = preview.matchedCount > 0 && playlistTitle.isNotBlank()
    val allIssues = remember(preview) { preview.missingEntries + preview.unsupportedEntries }

    AlertScaffold(hazeState = hazeState, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 19.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.m3u_import_title),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            PillTextField(
                value = playlistTitle,
                onValueChange = { playlistTitle = it },
                placeholder = stringResource(R.string.m3u_import_name_hint),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canSave) onConfirmSave(playlistTitle) }),
            )
            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SummaryLine(stringResource(R.string.m3u_import_summary_total, preview.totalEntries))
                SummaryLine(
                    text = stringResource(R.string.m3u_import_summary_matched, preview.matchedCount),
                    highlight = preview.matchedCount > 0,
                )
                if (preview.missingCount > 0) {
                    SummaryLine(
                        text = stringResource(R.string.m3u_import_summary_missing, preview.missingCount),
                        error = true,
                    )
                }
                if (preview.unsupportedCount > 0) {
                    SummaryLine(
                        text = stringResource(R.string.m3u_import_summary_unsupported, preview.unsupportedCount),
                        error = true,
                    )
                }
                if (preview.duplicateCount > 0) {
                    SummaryLine(stringResource(R.string.m3u_import_summary_duplicates, preview.duplicateCount))
                }
            }

            if (preview.matchedCount == 0) {
                Text(
                    text = stringResource(R.string.m3u_import_none_matched),
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            if (allIssues.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.m3u_import_issues_header),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 170.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    allIssues.take(50).forEach { issue ->
                        val kindLabel = when (issue.kind) {
                            M3uImportIssueKind.MISSING -> stringResource(R.string.m3u_import_issue_missing)
                            M3uImportIssueKind.REMOTE_UNSUPPORTED -> stringResource(R.string.m3u_import_issue_remote)
                            M3uImportIssueKind.UNSUPPORTED -> stringResource(R.string.m3u_import_issue_unsupported)
                            M3uImportIssueKind.DUPLICATE -> ""
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = issue.label,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                            )
                            Text(
                                text = kindLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                color = AccentRed,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        AlertRule()
        AlertAction(
            label = stringResource(R.string.m3u_import_save),
            emphasised = true,
            enabled = canSave,
            onClick = { onConfirmSave(playlistTitle) },
        )
        AlertRule()
        AlertAction(
            label = stringResource(R.string.cancel),
            emphasised = false,
            onClick = onDismiss,
        )
    }
}

@Composable
private fun SummaryLine(
    text: String,
    highlight: Boolean = false,
    error: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 12.5.sp,
            fontWeight = if (highlight) FontWeight.W600 else FontWeight.W400,
        ),
        color = when {
            error -> AccentRed
            highlight -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}
