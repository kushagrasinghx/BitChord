package com.music.bitchord.ui.components

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.YtMusicRepository.MAX_PLAYLIST_COVER_BYTES
import dev.chrisbanes.haze.HazeState
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val PREVIEW_SIZE = 512

private data class CoverSelection(val bytes: ByteArray, val preview: ImageBitmap)
private class CoverSelectionException(@StringRes val messageRes: Int) : Exception()

/** The URI is saveable; image bytes and decoded pixels deliberately never enter saved state. */
@Composable
fun PlaylistCoverArtDialog(
    playlistId: String,
    playlistTitle: String,
    hazeState: HazeState,
    uploading: Boolean,
    uploadFailed: Boolean,
    onSubmit: (ByteArray) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var selectedUri by rememberSaveable(playlistId) { mutableStateOf<String?>(null) }
    var pickerOpened by rememberSaveable(playlistId) { mutableStateOf(false) }
    var selectionVersion by rememberSaveable(playlistId) { mutableStateOf(0) }
    var selection by remember(playlistId) { mutableStateOf<CoverSelection?>(null) }
    var reading by remember(playlistId) { mutableStateOf(false) }
    var errorRes by remember(playlistId) { mutableStateOf<Int?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        // Cancelling the picker leaves the previous preview intact and never submits anything.
        if (uri != null) {
            selection = null
            errorRes = null
            selectedUri = uri.toString()
            selectionVersion++
        }
    }
    LaunchedEffect(playlistId) {
        if (!pickerOpened && selectedUri == null && !uploading) {
            pickerOpened = true
            try {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } catch (_: Exception) {
                errorRes = R.string.playlist_cover_picker_failed
            }
        }
    }
    LaunchedEffect(selectedUri, selectionVersion) {
        selection = null
        val uri = selectedUri ?: return@LaunchedEffect
        errorRes = null
        reading = true
        try {
            selection = readCoverSelection(context.contentResolver, Uri.parse(uri))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (invalid: CoverSelectionException) {
            errorRes = invalid.messageRes
        } catch (_: Exception) {
            // Provider errors can contain signed URLs or headers. Never display or log them.
            errorRes = R.string.playlist_cover_read_failed
        } finally {
            reading = false
        }
    }
    BackHandler { if (!uploading) onDismiss() }
    AlertScaffold(hazeState = hazeState, onDismiss = { if (!uploading) onDismiss() }) {
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.playlist_cover_change),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                playlistTitle,
                modifier = Modifier.padding(top = 4.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            selection?.preview?.let { preview ->
                Image(
                    bitmap = preview,
                    contentDescription = stringResource(R.string.playlist_cover_preview),
                    modifier = Modifier.padding(top = 12.dp).size(180.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Text(
                stringResource(when {
                    uploading -> R.string.playlist_cover_uploading
                    reading -> R.string.playlist_cover_reading
                    else -> R.string.playlist_cover_help
                }),
                modifier = Modifier.padding(top = 12.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            val message = errorRes ?: if (uploadFailed) R.string.playlist_cover_upload_failed else null
            message?.let {
                Text(
                    stringResource(it),
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
        AlertRule()
        AlertAction(
            label = stringResource(R.string.playlist_cover_choose),
            emphasised = false,
            enabled = !uploading && !reading,
            onClick = {
                try {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } catch (_: Exception) {
                    errorRes = R.string.playlist_cover_picker_failed
                }
            },
        )
        AlertRule()
        AlertAction(
            label = stringResource(R.string.save),
            emphasised = true,
            enabled = selection != null && !reading && !uploading,
            onClick = { selection?.let { if (!uploading && !reading) onSubmit(it.bytes) } },
        )
        AlertRule()
        AlertAction(
            label = stringResource(R.string.cancel),
            emphasised = false,
            enabled = !uploading,
            onClick = onDismiss,
        )
    }
}

private suspend fun readCoverSelection(resolver: ContentResolver, uri: Uri): CoverSelection =
    withContext(Dispatchers.IO) {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            throw CoverSelectionException(R.string.playlist_cover_read_failed)
        }
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                // Read at most the app limit plus one byte, even with an untrusted provider.
                val count = input.read(buffer, 0, minOf(buffer.size, MAX_PLAYLIST_COVER_BYTES - total + 1))
                if (count == -1) break
                total += count
                if (total > MAX_PLAYLIST_COVER_BYTES) {
                    throw CoverSelectionException(R.string.playlist_cover_too_large)
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw CoverSelectionException(R.string.playlist_cover_read_failed)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outMimeType !in setOf("image/jpeg", "image/png")) {
            throw CoverSelectionException(R.string.playlist_cover_format)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw CoverSelectionException(R.string.playlist_cover_invalid)
        }
        var sample = 1
        while (bounds.outWidth / sample > PREVIEW_SIZE || bounds.outHeight / sample > PREVIEW_SIZE) {
            sample *= 2
        }
        currentCoroutineContext().ensureActive()
        val preview = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: throw CoverSelectionException(R.string.playlist_cover_invalid)
        CoverSelection(bytes, preview.asImageBitmap())
    }
