package com.music.bitchord.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.canvas.ArtistSignatureRepository
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.AddonEditorAlert
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

/**
 * The artist-signature API's address, on the card an addon is added with.
 *
 * **Mounted from [MainActivity][com.music.bitchord.MainActivity], not from the
 * settings sheet, and it has to stay that way** — see [PartyServerEditor] for why
 * a haze card cannot live inside a screen.
 *
 * Saves only once `GET /v1/health` answers 2xx; saving a blank address forgets
 * the provider.
 */
@Composable
fun SignatureApiEditor(hazeState: HazeState, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val stored by AppSettings.signatureApiUrl.collectAsStateWithLifecycle()

    // A draft of the stored address, seeded once: re-seeding it from the store
    // would overwrite typing the first time anything else touched it.
    var input by remember { mutableStateOf(stored) }
    var status by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var busy by remember { mutableStateOf(false) }

    val connected = stringResource(R.string.connected)
    val failed = stringResource(R.string.artist_signatures_test_failed)

    val test: (onGood: () -> Unit) -> Unit = { onGood ->
        val normalized = AppSettings.normalizeSignatureApiUrl(input)
        if (!ArtistSignatureRepository.isValidBaseUrl(normalized)) {
            status = failed to false
        } else {
            busy = true
            status = null
            scope.launch {
                val ok = ArtistSignatureRepository.checkHealth(normalized)
                busy = false
                status = (if (ok) connected else failed) to ok
                if (ok) onGood()
            }
        }
    }

    val remove: () -> Unit = {
        AppSettings.setSignatureApiUrl("")
        ArtistSignatureRepository.clear()
        onDismiss()
    }

    AddonEditorAlert(
        hazeState = hazeState,
        title = stringResource(R.string.artist_signatures_url),
        description = stringResource(R.string.artist_signatures_subtitle),
        urlValue = input,
        // A result describes the address it was run against, so editing the
        // address clears it.
        onUrlChange = {
            input = it
            status = null
        },
        urlPlaceholder = stringResource(R.string.artist_signatures_url_hint),
        status = status?.first,
        statusIsGood = status?.second == true,
        testing = busy,
        canSubmit = input.isNotBlank(),
        onTest = { test {} },
        onSave = {
            test {
                AppSettings.setSignatureApiUrl(AppSettings.normalizeSignatureApiUrl(input))
                ArtistSignatureRepository.clear()
                onDismiss()
            }
        },
        onRemove = remove.takeIf { stored.isNotBlank() },
        removeLabel = stringResource(R.string.remove),
        onDismiss = onDismiss,
    )
}
