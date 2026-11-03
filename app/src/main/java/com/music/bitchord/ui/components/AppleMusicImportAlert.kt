package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.data.applemusic.AppleMusicImportController
import com.music.bitchord.data.applemusic.AppleMusicImportError
import com.music.bitchord.data.applemusic.AppleMusicImportSource
import com.music.bitchord.data.applemusic.AppleMusicLibrarySession
import com.music.bitchord.data.applemusic.AppleMusicPlaylist
import com.music.bitchord.data.applemusic.AppleMusicTrackMapper
import com.music.bitchord.data.applemusic.AppleMusicImportResult
import com.music.bitchord.playback.applemusic.AppleMusicPlayback
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keep an Apple Music playlist on this device.
 *
 * Given a library [playlist] the alert imports that; otherwise it offers the
 * whole library's songs and a pasted `music.apple.com/…/playlist/pl.…` link.
 * Rows that only exist on Apple Music are kept as playable `am-miss:` songs
 * rather than dropped — see [com.music.bitchord.ui.screens.AppleMusicLibraryScreen].
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun AppleMusicImportAlert(
    hazeState: HazeState,
    playlist: AppleMusicPlaylist?,
    youtubeConnected: Boolean,
    onExportToYouTube: (AppleMusicImportResult) -> Unit,
    onImported: (playlist: String, matched: Int, total: Int, appleOnly: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { AppleMusicLibrarySession(scope) }
    var controller by remember { mutableStateOf<AppleMusicImportController?>(null) }
    var link by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            controller?.close()
            session.close()
        }
    }
    LaunchedEffect(Unit) {
        val matcher = withContext(Dispatchers.IO) { AppleMusicPlayback.matcher(context) }
        controller = AppleMusicImportController(session, AppleMusicTrackMapper(matcher))
    }

    fun start(exportToYouTube: Boolean = false) {
        val current = controller ?: return
        working = true
        failed = false
        status = context.getString(R.string.apple_music_import_matching, 0)
        scope.launch {
            val selection = when {
                playlist != null -> AppleMusicImportSource.LibraryPlaylist(playlist)
                link.isNotBlank() -> AppleMusicImportSource.PlaylistUrl(link)
                else -> AppleMusicImportSource.LibrarySongs
            }
            val result = current.import(selection)
            if (result == null) {
                failed = true
                status = context.getString(current.state.value.error.messageRes())
            } else if (result.matchedCount + result.appleOnlyCount == 0) {
                failed = true
                status = context.getString(R.string.apple_music_import_none)
            } else {
                if (exportToYouTube && result.matchedCount > 0) onExportToYouTube(result)
                onImported(
                    playlist?.name ?: context.getString(R.string.apple_music),
                    result.matchedCount,
                    result.totalTracks,
                    result.appleOnlyCount,
                )
                onDismiss()
            }
            working = false
        }
    }

    AlertScaffold(hazeState = hazeState, onDismiss = { if (!working) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 19.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.apple_music_import_title),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = status ?: if (playlist != null) {
                    stringResource(R.string.apple_music_import_playlist_description, playlist.name)
                } else {
                    stringResource(R.string.apple_music_import_description)
                },
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = when {
                    status == null -> MaterialTheme.colorScheme.onSurface
                    failed -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
                textAlign = TextAlign.Center,
            )
            if (playlist == null) {
                PillTextField(
                    value = link,
                    onValueChange = {
                        link = it
                        if (failed) {
                            status = null
                            failed = false
                        }
                    },
                    placeholder = stringResource(R.string.apple_music_import_hint),
                    enabled = !working,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!working) start() }),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.apple_music_import_library_hint),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (youtubeConnected) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.apple_music_import_export_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (youtubeConnected) {
            AlertRule()
            AlertAction(
                label = stringResource(R.string.apple_music_import_to_youtube),
                emphasised = true,
                onClick = { start(exportToYouTube = true) },
                enabled = !working && controller != null,
            )
        }
        AlertRule()
        AlertAction(
            label = stringResource(R.string.apple_music_import_to_device),
            emphasised = !youtubeConnected,
            onClick = { start() },
            enabled = !working && controller != null,
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

private fun AppleMusicImportError?.messageRes(): Int = when (this) {
    AppleMusicImportError.NOT_CONNECTED -> R.string.apple_music_not_connected
    AppleMusicImportError.INVALID_LINK -> R.string.apple_music_import_invalid
    AppleMusicImportError.PLAYLIST_NOT_FOUND -> R.string.apple_music_import_missing
    AppleMusicImportError.SAVE_FAILED -> R.string.apple_music_import_save_failed
    AppleMusicImportError.LOAD_FAILED, null -> R.string.apple_music_import_failed
}
