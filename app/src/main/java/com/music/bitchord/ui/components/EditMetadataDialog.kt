package com.music.bitchord.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.music.bitchord.R
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.launch

/**
 * Dedicated in-app metadata editor for local music files and downloaded tracks.
 * Allows editing title, artist, and album name, persisting changes to MediaStore,
 * download stores, and file tags.
 */
@Composable
fun EditMetadataDialog(
    song: Song,
    onDismiss: () -> Unit,
    onSaved: ((Song) -> Unit)? = null,
) {
    var title by remember(song) { mutableStateOf(song.title) }
    var artist by remember(song) { mutableStateOf(song.artist) }
    var album by remember(song) { mutableStateOf(song.albumName ?: "") }
    var saving by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(ALERT_CORNER)

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SCRIM_COLOR)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { if (!saving) onDismiss() },
                )
                .padding(horizontal = 24.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = ALERT_WIDTH, max = 340.dp)
                    .fillMaxWidth(0.9f)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {},
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.edit_metadata),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.W600,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.track_title)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !saving,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = artist,
                        onValueChange = { artist = it },
                        label = { Text(stringResource(R.string.artist)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !saving,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = album,
                        onValueChange = { album = it },
                        label = { Text(stringResource(R.string.album)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !saving,
                    )
                }

                AlertRule()
                AlertAction(
                    label = stringResource(R.string.save),
                    emphasised = true,
                    enabled = !saving && title.isNotBlank(),
                    onClick = {
                        saving = true
                        scope.launch {
                            val result = LocalMediaRepository.updateTrackMetadata(
                                context = context,
                                song = song,
                                title = title.trim(),
                                artist = artist.trim(),
                                albumName = album.trim().takeIf { it.isNotEmpty() },
                            )
                            saving = false
                            result.fold(
                                onSuccess = { updatedSong ->
                                    Toast.makeText(context, R.string.metadata_updated, Toast.LENGTH_SHORT).show()
                                    onSaved?.invoke(updatedSong)
                                    onDismiss()
                                },
                                onFailure = {
                                    Toast.makeText(context, R.string.metadata_update_failed, Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    },
                )
                AlertRule()
                AlertAction(
                    label = stringResource(R.string.cancel),
                    emphasised = false,
                    enabled = !saving,
                    onClick = onDismiss,
                )
            }
        }
    }
}
