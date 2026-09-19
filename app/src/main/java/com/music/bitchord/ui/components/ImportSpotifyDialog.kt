package com.music.bitchord.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.spotify.SpotifyImporter
import kotlinx.coroutines.launch

@Composable
fun ImportSpotifyDialog(
    onDismiss: () -> Unit,
    onImportComplete: (title: String, privacy: PlaylistPrivacy, videoIds: List<String>, songs: List<Song>) -> Unit,
) {
    var urlInput by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf(PlaylistPrivacy.PUBLIC) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }

    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = {
            Text(
                text = "Import Spotify Playlist",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = "Paste a public Spotify playlist link below. BitChord will match its songs and create a playlist in your library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = {
                        urlInput = it
                        errorMessage = null
                    },
                    label = { Text("Spotify Playlist URL") },
                    placeholder = { Text("https://open.spotify.com/playlist/...") },
                    enabled = !isProcessing,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Privacy",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlaylistPrivacy.entries.forEach { p ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !isProcessing) { privacy = p }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            RadioButton(
                                selected = (privacy == p),
                                onClick = null,
                                enabled = !isProcessing,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = p.label,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = isProcessing) {
                    Column(Modifier.padding(top = 16.dp)) {
                        if (progress > 0f) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.height(8.dp))
                        statusText?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                errorMessage?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = urlInput.isNotBlank() && !isProcessing,
                onClick = {
                    val playlistId = SpotifyImporter.extractPlaylistId(urlInput)
                    if (playlistId == null) {
                        errorMessage = "Invalid Spotify playlist link. Please enter a valid open.spotify.com/playlist/... URL."
                        return@Button
                    }

                    isProcessing = true
                    errorMessage = null
                    statusText = "Fetching Spotify playlist details..."
                    progress = 0f

                    scope.launch {
                        runCatching {
                            val (playlistTitle, tracks) = SpotifyImporter.fetchPlaylistTracks(playlistId)
                            statusText = "Matching ${tracks.size} tracks on YouTube Music..."

                            val (songs, unmatched) = com.music.bitchord.data.spotify.SpotifyImporter.resolveToSongs(tracks) { completed, total ->
                                progress = completed.toFloat() / total
                                statusText = "Matching tracks: $completed / $total"
                            }

                            if (songs.isEmpty()) {
                                error("Could not find any matching songs on YouTube Music.")
                            }

                            val videoIds = songs.map { it.videoId }
                            onImportComplete(playlistTitle, privacy, videoIds, songs)
                        }.onFailure { ex ->
                            isProcessing = false
                            errorMessage = ex.message ?: "An unexpected error occurred during import."
                        }
                    }
                },
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .height(16.dp)
                            .width(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isProcessing,
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
