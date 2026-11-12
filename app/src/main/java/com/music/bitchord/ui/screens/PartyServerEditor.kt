package com.music.bitchord.ui.screens

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.listentogether.ServerConnectionState
import com.music.bitchord.data.listentogether.ServerUrlValidationResult
import com.music.bitchord.ui.components.AddonEditorAlert
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

/**
 * The party server's address, on the card an addon is added with.
 *
 * **Mounted from [MainActivity][com.music.bitchord.MainActivity], not from
 * [ListenTogetherScreen], and it has to stay that way.** The card underneath is
 * a haze effect reading the app's own backdrop layer, and it covers the window
 * with a scrim — neither of which a composable inside the settings sheet can do.
 * Put here, it sampled a layer it was itself inside, so the frosting had nothing
 * to frost and the card came out fully transparent, and its `fillMaxSize` scrim
 * was bounded by a scrolling column instead of the screen, so the actions were
 * laid out somewhere no finger reached. The addon editor is mounted at the root
 * for exactly these reasons; this is the same card and wants the same place.
 *
 * Self-contained on purpose: everything it edits lives in [ListenTogether], so
 * hoisting it cost one callback rather than half the screen's state.
 */
@Composable
fun PartyServerEditor(hazeState: HazeState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val customServer by ListenTogether.customServerUrl.collectAsStateWithLifecycle()
    val party by ListenTogether.state.collectAsStateWithLifecycle()

    // Seeded once, at the moment the card opens: this is a draft of the stored
    // address, and re-seeding it from the store while it is being typed into
    // would overwrite the typing the first time anything else touched it.
    var input by remember { mutableStateOf(customServer) }
    // What the last Test or Save said, and whether it was good news. Null until
    // one of them has run — the card shows its description then.
    var status by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var busy by remember { mutableStateOf(false) }

    val save: () -> Unit = {
        when (val validation = ListenTogether.parseAndNormalizeServerUrl(input)) {
            // In the card, where the address being complained about still is. A
            // toast over a dialog is a sentence about a field it is covering.
            is ServerUrlValidationResult.Invalid ->
                status = context.getString(validation.error.toMessageRes()) to false

            is ServerUrlValidationResult.Valid -> {
                busy = true
                status = null
                scope.launch {
                    val resolved = ListenTogether.setCustomServerUrl(validation.normalizedUrl)
                    busy = false
                    val messageRes = when (resolved) {
                        is ServerConnectionState.CustomOnline ->
                            R.string.listen_together_custom_server_connected
                        is ServerConnectionState.CustomFallback ->
                            R.string.listen_together_custom_server_unreachable_fallback
                        is ServerConnectionState.DefaultOnline ->
                            R.string.listen_together_switched_to_default
                        is ServerConnectionState.Offline ->
                            if (ListenTogether.customServerUrl.value.isNotBlank()) {
                                R.string.listen_together_status_all_offline
                            } else {
                                R.string.listen_together_server_offline
                            }
                        ServerConnectionState.Checking -> null
                    }
                    if (messageRes != null) {
                        Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                }
            }
        }
    }

    val remove: () -> Unit = {
        busy = true
        status = null
        scope.launch {
            ListenTogether.setCustomServerUrl("")
            busy = false
            Toast.makeText(
                context,
                context.getString(R.string.listen_together_switched_to_default),
                Toast.LENGTH_SHORT,
            ).show()
            onDismiss()
        }
    }

    AddonEditorAlert(
        hazeState = hazeState,
        title = stringResource(R.string.listen_together_custom_server),
        description = stringResource(R.string.listen_together_custom_server_footer),
        urlValue = input,
        // A result describes the address it was run against, so the moment that
        // address is edited it stops being true and is cleared. Left up, it
        // would report "Connected" over a URL nobody has tried.
        onUrlChange = {
            input = it
            status = null
        },
        // Never the default address, even as a placeholder: this box exists to
        // take somebody else's server, and the one this build uses is not shown
        // anywhere.
        urlPlaceholder = "https://party.example.com",
        status = status?.first,
        statusIsGood = status?.second == true,
        testing = busy,
        canSubmit = input.isNotBlank(),
        onTest = {
            when (val validation = ListenTogether.parseAndNormalizeServerUrl(input)) {
                is ServerUrlValidationResult.Invalid ->
                    status = context.getString(validation.error.toMessageRes()) to false

                is ServerUrlValidationResult.Valid -> {
                    busy = true
                    status = null
                    scope.launch {
                        val probe = ListenTogether.probeHealthWithLatency(
                            validation.normalizedUrl,
                            SERVER_PROBE_TIMEOUT_MS,
                        )
                        status = if (probe.isOnline) {
                            context.getString(R.string.listen_together_server_online, probe.latencyMs) to true
                        } else {
                            context.getString(R.string.listen_together_server_offline) to false
                        }
                        busy = false
                    }
                }
            }
        },
        onSave = save,
        // Nothing to remove until there is one stored, and never while a party
        // is live on it: changing the address under a live membership would
        // leave this device holding a token for a server it no longer talks to.
        onRemove = remove.takeIf { customServer.isNotBlank() && !party.inParty },
        removeLabel = stringResource(R.string.listen_together_remove_server),
        onDismiss = onDismiss,
    )
}

/**
 * How long the server editor waits on an address before calling it unreachable.
 *
 * Longer than the background health check, because this one was *asked* for: a
 * person who has just pressed Test is watching a spinner and will wait, where
 * the periodic check has to stay off the radio.
 */
private const val SERVER_PROBE_TIMEOUT_MS = 8_000L
