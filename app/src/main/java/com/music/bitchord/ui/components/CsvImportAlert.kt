package com.music.bitchord.ui.components

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.playlist.CsvPlaylistParser
import com.music.bitchord.ui.icons.SfIcons
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.launch

/**
 * Imports a playlist from a CSV file or pasted CSV content (Title, Artist, Album, YouTube Video ID).
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun CsvImportAlert(
    hazeState: HazeState,
    signedIn: Boolean,
    onImported: (title: String, privacy: PlaylistPrivacy, songs: List<Song>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var csvText by remember { mutableStateOf("") }
    var playlistTitle by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf<String?>(null) }
    var privacy by remember { mutableStateOf(PlaylistPrivacy.PRIVATE) }
    var working by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    val parsedPlaylist by remember(csvText, playlistTitle) {
        derivedStateOf {
            if (csvText.isBlank()) null
            else CsvPlaylistParser.parse(csvText, playlistTitle.ifBlank { fileName?.removeSuffix(".csv") })
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                var retrievedName: String? = null
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        retrievedName = cursor.getString(nameIndex)
                    }
                }
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!content.isNullOrBlank()) {
                    csvText = content
                    fileName = retrievedName
                    if (playlistTitle.isBlank() && retrievedName != null) {
                        playlistTitle = retrievedName!!.removeSuffix(".csv").removeSuffix(".CSV")
                    }
                    failed = false
                    statusMessage = null
                }
            } catch (e: Exception) {
                failed = true
                statusMessage = e.message ?: context.getString(R.string.csv_import_invalid)
            }
        }
    }

    fun startImport() {
        val parsed = parsedPlaylist
        if (parsed == null || parsed.songs.isEmpty()) {
            failed = true
            statusMessage = context.getString(R.string.csv_import_invalid)
            return
        }

        val effectiveTitle = playlistTitle.trim().ifBlank {
            parsed.title ?: fileName?.removeSuffix(".csv") ?: context.getString(R.string.imported_csv_playlist_default)
        }

        working = true
        onImported(effectiveTitle, privacy, parsed.songs)
        onDismiss()
    }

    AlertScaffold(hazeState = hazeState, onDismiss = { if (!working) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 540.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 19.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.csv_import_title),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = statusMessage ?: stringResource(R.string.csv_import_subtitle),
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = when {
                    failed -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
            )

            // File selection button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = !working) { launcher.launch("*/*") }
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = SfIcons.FileUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = fileName ?: stringResource(R.string.csv_import_choose_file),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Playlist Name field
            PillTextField(
                value = playlistTitle,
                onValueChange = { playlistTitle = it },
                placeholder = stringResource(R.string.csv_import_playlist_title),
                enabled = !working,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            )

            Spacer(Modifier.height(10.dp))

            // Paste text field
            PillTextField(
                value = csvText,
                onValueChange = {
                    csvText = it
                    if (failed) {
                        failed = false
                        statusMessage = null
                    }
                },
                placeholder = stringResource(R.string.csv_import_paste_hint),
                enabled = !working,
                singleLine = false,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (!working) startImport() }),
            )

            // Detected songs summary
            parsedPlaylist?.let { parsed ->
                if (parsed.songs.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.csv_import_songs_found, parsed.songs.size),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (signedIn) {
                Text(
                    text = stringResource(R.string.who_can_see_it),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PlaylistPrivacy.entries.forEach { option ->
                        val selected = option == privacy
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .clickable(enabled = !working) { privacy = option }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        AlertRule()
        AlertAction(
            label = stringResource(R.string.csv_import_button),
            emphasised = true,
            onClick = ::startImport,
            enabled = (parsedPlaylist?.songs?.isNotEmpty() == true) && !working,
        )
        AlertRule()
        AlertAction(
            label = stringResource(R.string.cancel),
            emphasised = false,
            onClick = onDismiss,
            enabled = !working,
        )
    }
}
