package com.music.bitchord.ui.tv.dialogs

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.music.bitchord.BuildConfig
import com.music.bitchord.auth.WebSessionMode
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.TvSettings
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.configureTvSignInWebView
import com.music.bitchord.ui.onSignedIn
import com.music.bitchord.ui.tv.TvAppMark
import com.music.bitchord.ui.tv.auth.TvAuthServer
import com.music.bitchord.ui.tv.auth.TvQrCodeView
import com.music.bitchord.ui.tv.components.TvActivityIndicator
import com.music.bitchord.ui.tv.components.TvArtwork
import com.music.bitchord.ui.tv.components.TvButton
import com.music.bitchord.ui.tv.components.TvDialog
import com.music.bitchord.ui.tv.components.TvDialogButton
import com.music.bitchord.ui.tv.components.TvListRow
import com.music.bitchord.ui.tv.components.TvTextField
import com.music.bitchord.ui.tv.components.tvInitialFocus
import com.music.bitchord.ui.tv.display.TvRefreshRateController
import com.music.bitchord.ui.tv.display.TvRefreshRatePreference
import com.music.bitchord.ui.tv.personalization.AppThemeOption
import com.music.bitchord.ui.tv.theme.TvGlass
import com.music.bitchord.ui.tv.theme.TvType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private enum class SignInMode { Choose, OnTv, WithPhone }

/** Account: who is signed in, or the two ways to sign in from a TV. */
@Composable
fun TvAccountDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    /** Offer signing in with a phone (QR). Off goes straight to signing in on the TV. */
    allowPhoneSignIn: Boolean = true,
) {
    val signedIn by viewModel.signedIn.collectAsState()
    val account by viewModel.account.collectAsState()
    var mode by remember { mutableStateOf(if (allowPhoneSignIn) SignInMode.Choose else SignInMode.OnTv) }
    val backFromSignIn: () -> Unit = { if (allowPhoneSignIn) mode = SignInMode.Choose else onDismiss() }

    if (signedIn) {
        TvDialog(title = account?.name ?: "Signed In", message = account?.email, onDismissRequest = onDismiss) {
            TvArtwork(
                url = account?.thumbnailUrl,
                px = 240,
                shape = CircleShape,
                modifier = Modifier
                    .padding(bottom = 21.dp)
                    .size(77.dp),
            )
            TvDialogButton(text = "Done", onClick = onDismiss, initialFocus = true)
            Spacer(modifier = Modifier.height(6.dp))
            TvDialogButton(text = "Sign Out", destructive = true, onClick = { viewModel.signOut() })
        }
        return
    }

    when (mode) {
        SignInMode.Choose -> TvDialog(
            title = "Sign In to YouTube Music",
            message = "Your library, playlists and recommendations follow your account.",
            onDismissRequest = onDismiss,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                TvListRow(
                    title = "Sign In on This TV",
                    subtitle = "Use the remote to sign in to Google.",
                    leadingIcon = Icons.Rounded.Tv,
                    modifier = Modifier.tvInitialFocus(),
                    onClick = { mode = SignInMode.OnTv },
                )
                TvListRow(
                    title = "Sign In with Your Phone",
                    subtitle = "Scan a code with a phone on the same Wi-Fi.",
                    leadingIcon = Icons.Rounded.PhoneAndroid,
                    onClick = { mode = SignInMode.WithPhone },
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            TvDialogButton(text = "Not Now", onClick = onDismiss)
        }
        SignInMode.WithPhone -> TvPhoneSignIn(viewModel = viewModel, onBack = backFromSignIn)
        SignInMode.OnTv -> TvGoogleSignIn(
            viewModel = viewModel,
            onBack = backFromSignIn,
            onSignedIn = onDismiss,
        )
    }
}

/** A code for the phone to open; the TV serves a page there that takes the session. */
@Composable
private fun TvPhoneSignIn(viewModel: MainViewModel, onBack: () -> Unit) {
    var url by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        scope.launch {
            TvAuthServer.start { cookie ->
                scope.launch(Dispatchers.Main) { viewModel.onSignedIn(cookie) }
            }?.let { (port, ip) -> url = "http://$ip:$port/" }
        }
        onDispose { TvAuthServer.stop() }
    }

    TvDialog(title = "Sign In with Your Phone", width = 576.dp, onDismissRequest = onBack) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(29.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                val link = url
                if (link != null) TvQrCodeView(content = link, size = 147.dp) else TvActivityIndicator(color = Color.Black)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                TvStep(1, "Connect your phone to the same Wi-Fi as this TV.")
                TvStep(2, "Scan the code, or open ${url ?: "the address shown here"}.")
                TvStep(3, "Follow the steps on your phone. This TV signs in by itself.")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        TvDialogButton(text = "Back", onClick = onBack, initialFocus = true, modifier = Modifier.width(256.dp))
    }
}

@Composable
private fun TvStep(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(21.dp)
                .clip(CircleShape)
                .background(TvGlass.Fill),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = number.toString(), style = TvType.Caption, color = TvGlass.TextPrimary)
        }
        Text(text = text, style = TvType.Callout, color = TvGlass.TextSecondary, modifier = Modifier.weight(1f))
    }
}

/** Google's own sign-in page, full screen, with the finish and cancel controls above it. */
@Composable
private fun TvGoogleSignIn(viewModel: MainViewModel, onBack: () -> Unit, onSignedIn: () -> Unit) {
    var capture by remember { mutableIntStateOf(0) }
    // Google's page owns the D-pad while one of its fields has focus, so Up can't
    // be counted on to reach the buttons above it. Back does: the first press
    // comes up to Done / Cancel, a second leaves sign-in.
    val doneFocus = remember { FocusRequester() }
    var headerHasFocus by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = {
            if (headerHasFocus) onBack() else runCatching { doneFocus.requestFocus() }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF141416)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 14.dp)
                    .onFocusChanged { headerHasFocus = it.hasFocus },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sign In to YouTube Music",
                        style = TvType.Headline,
                        color = TvGlass.TextPrimary,
                    )
                    Text(
                        text = "Press Back for Done or Cancel",
                        style = TvType.Caption,
                        color = TvGlass.TextTertiary,
                    )
                }
                TvButton(text = "Cancel", onClick = onBack)
                Spacer(modifier = Modifier.width(10.dp))
                TvButton(text = "Done", isPrimary = true, onClick = { capture++ }, modifier = Modifier.focusRequester(doneFocus))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                com.music.bitchord.auth.YtMusicLoginScreen(
                    mode = WebSessionMode.SIGN_IN,
                    configureWebView = ::configureTvSignInWebView,
                    captureRequest = capture,
                    onPageReady = { ready -> if (ready) capture++ },
                    onCaptured = { session ->
                        viewModel.onWebSession(session, WebSessionMode.SIGN_IN) { success ->
                            if (success) onSignedIn()
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun TvDiscordDialog(onDismiss: () -> Unit) {
    val enabled by AppSettings.discordRpcEnabled.collectAsState()
    val savedToken by AppSettings.discordToken.collectAsState()
    var token by remember { mutableStateOf(savedToken) }

    TvDialog(
        title = "Discord",
        message = "Show what you're listening to in your Discord status.",
        onDismissRequest = onDismiss,
    ) {
        TvTextField(
            value = token,
            onValueChange = { token = it },
            placeholder = "Discord Token",
            modifier = Modifier.tvInitialFocus(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        TvDialogButton(
            text = if (enabled) "Turn Off" else "Turn On",
            enabled = enabled || token.isNotBlank(),
            onClick = {
                AppSettings.setDiscordToken(token)
                AppSettings.setDiscordRpcEnabled(!enabled)
                onDismiss()
            },
        )
        Spacer(modifier = Modifier.height(6.dp))
        TvDialogButton(text = "Cancel", onClick = onDismiss)
    }
}

@Composable
fun TvScrobbleDialog(onDismiss: () -> Unit) {
    val lastfmEnabled by AppSettings.lastfmEnabled.collectAsState()
    val lastfmUser by AppSettings.lastfmUsername.collectAsState()
    val listenBrainzEnabled by AppSettings.listenBrainzEnabled.collectAsState()
    val listenBrainzToken by AppSettings.listenBrainzToken.collectAsState()

    TvDialog(
        title = "Scrobbling",
        message = "Connect these on the BitChord phone app; the TV uses the same accounts.",
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            TvListRow(
                title = "Last.fm",
                value = if (lastfmEnabled && lastfmUser.isNotBlank()) lastfmUser else "Not Connected",
                enabled = false,
                onClick = {},
            )
            TvListRow(
                title = "ListenBrainz",
                value = if (listenBrainzEnabled && listenBrainzToken.isNotBlank()) "Connected" else "Not Connected",
                enabled = false,
                onClick = {},
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        TvDialogButton(text = "OK", onClick = onDismiss, initialFocus = true)
    }
}

@Composable
fun TvAboutDialog(onDismiss: () -> Unit) {
    TvDialog(title = "BitChord", message = "Version ${BuildConfig.VERSION_NAME}", onDismissRequest = onDismiss) {
        TvAppMark(size = 77.dp, modifier = Modifier.padding(bottom = 21.dp))
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            TvListRow(title = "Kushagra Singh", value = "Creator", enabled = false, onClick = {})
            TvListRow(title = "Nithyanantha", value = "TV App", enabled = false, onClick = {})
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Free software under the GNU GPL v3. Not affiliated with YouTube or Google.",
            style = TvType.Caption,
            color = TvGlass.TextTertiary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(18.dp))
        TvDialogButton(text = "OK", onClick = onDismiss, initialFocus = true)
    }
}

@Composable
fun TvRefreshRateDialog(onDismiss: () -> Unit) {
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    val capabilities by TvRefreshRateController.capabilities.collectAsState()
    val current by TvRefreshRateController.preference.collectAsState()

    TvDialog(
        title = "Refresh Rate",
        message = "Now ${capabilities.currentPhysicalWidth} × ${capabilities.currentPhysicalHeight} at " +
            String.format(java.util.Locale.US, "%.0f Hz", capabilities.actualRefreshRateHz),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            TvRefreshRatePreference.entries.forEach { preference ->
                val supported = preference != TvRefreshRatePreference.SMOOTH_60 || capabilities.is60HzSupported
                TvListRow(
                    title = preference.label,
                    subtitle = preference.description,
                    trailingIcon = if (preference == current) Icons.Rounded.Check else null,
                    enabled = supported,
                    modifier = if (preference == current) Modifier.tvInitialFocus() else Modifier,
                    onClick = {
                        activity?.let { TvRefreshRateController.setPreference(it, preference) }
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
fun TvThemeDialog(onDismiss: () -> Unit) {
    val currentId by TvSettings.tvTheme.collectAsState()
    val current = AppThemeOption.fromId(currentId)

    TvDialog(title = "Appearance", onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            AppThemeOption.entries.forEach { theme ->
                TvListRow(
                    title = theme.title,
                    subtitle = theme.description,
                    trailingIcon = if (theme == current) Icons.Rounded.Check else null,
                    modifier = if (theme == current) Modifier.tvInitialFocus() else Modifier,
                    onClick = {
                        TvSettings.setTvTheme(theme.id)
                        onDismiss()
                    },
                )
            }
        }
    }
}

/**
 * Which lyric databases the player may ask, and in what order. Select turns a
 * source on or off; Left and Right move the focused one up or down the list,
 * since a remote has no way to drag. The last source left on can't be turned off.
 */
@Composable
fun TvLyricsSourcesDialog(onDismiss: () -> Unit) {
    val selected by AppSettings.lyricsSources.collectAsState()
    val savedOrder by AppSettings.lyricsSourceOrder.collectAsState()
    val wordFirst by AppSettings.prioritizeSyllableSync.collectAsState()
    val order = remember(savedOrder) { LyricsSource.ordered(savedOrder) }

    TvDialog(
        title = "Lyrics Sources",
        message = "Select to turn a source on or off. Press Left or Right to move it up or down; the first is asked first.",
        width = 512.dp,
        onDismissRequest = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier.heightIn(max = 304.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            itemsIndexed(order, key = { _, source -> source.name }) { index, source ->
                val checked = source in selected
                TvListRow(
                    title = source.label,
                    subtitle = source.detail,
                    value = "${index + 1}",
                    leadingIcon = if (checked) Icons.Rounded.Check else null,
                    modifier = Modifier
                        .tvInitialFocus(enabled = index == 0)
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            val target = when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> index - 1
                                KeyEvent.KEYCODE_DPAD_RIGHT -> index + 1
                                else -> return@onKeyEvent false
                            }
                            if (target in order.indices) {
                                AppSettings.setLyricsSourceOrder(
                                    order.toMutableList().apply { add(target, removeAt(index)) },
                                )
                            }
                            true
                        },
                    onClick = {
                        if (checked && selected.size <= 1) return@TvListRow
                        AppSettings.setLyricsSources(if (checked) selected - source else selected + source)
                    },
                )
            }
            item(key = "wordFirst") {
                TvListRow(
                    title = "Prefer Word-Synced",
                    subtitle = "Hold out for word-by-word lyrics before settling for line-by-line.",
                    value = if (wordFirst) "On" else "Off",
                    onClick = { AppSettings.setPrioritizeSyllableSync(!wordFirst) },
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        TvDialogButton(text = "Reset to Default", onClick = AppSettings::resetLyricsSourceSettings)
        Spacer(modifier = Modifier.height(6.dp))
        TvDialogButton(text = "Done", onClick = onDismiss)
    }
}
