package com.music.bitchord.ui.tv.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.BuildConfig
import com.music.bitchord.R
import com.music.bitchord.auth.WebSessionMode
import androidx.compose.ui.res.painterResource
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.tv.components.TvButton
import com.music.bitchord.ui.tv.components.TvDialog
import com.music.bitchord.ui.tv.focus.tvButtonFocus
import com.music.bitchord.ui.tv.theme.TvColors
import com.music.bitchord.ui.tv.theme.TvSFProDisplay
import com.music.bitchord.ui.tv.personalization.getPalette

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Icon
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.music.bitchord.ui.tv.auth.TvAuthServer
import com.music.bitchord.ui.tv.auth.TvQrCodeView
import kotlinx.coroutines.launch

@Composable
fun TvAccountDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
) {
    val signedIn by viewModel.signedIn.collectAsState()
    val account by viewModel.account.collectAsState()

    var loginMode by remember { mutableStateOf("menu") } // "menu", "google", "qr", "manual"
    var cookieText by remember { mutableStateOf("") }
    var pairingUrl by remember { mutableStateOf<String?>(null) }
    var isJustPaired by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Start local pairing HTTP server when in QR mode
    DisposableEffect(signedIn, loginMode) {
        if (!signedIn && loginMode == "qr") {
            scope.launch {
                val serverInfo = TvAuthServer.start { receivedCookie ->
                    scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        isJustPaired = true
                        viewModel.onSignedIn(receivedCookie) { success ->
                            if (success) {
                                isJustPaired = true
                            }
                        }
                    }
                }
                if (serverInfo != null) {
                    val (port, ip) = serverInfo
                    pairingUrl = "http://$ip:$port/"
                }
            }
        }
        onDispose {
            TvAuthServer.stop()
        }
    }

    if (loginMode == "google") {
        var captureTrigger by remember { androidx.compose.runtime.mutableIntStateOf(0) }
        var isPageReadyToConfirm by remember { mutableStateOf(false) }

        // Direct Fullscreen Google Web Login Dialog
        androidx.compose.ui.window.Dialog(onDismissRequest = { loginMode = "menu" }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF141418)),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Google Sign In • YouTube Music",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = TvSFProDisplay,
                            color = Color.White,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TvButton(
                                text = "Confirm & Finish",
                                isPrimary = true,
                                onClick = { captureTrigger++ },
                            )
                            TvButton(
                                text = "Cancel",
                                isPrimary = false,
                                onClick = { loginMode = "menu" },
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)),
                    ) {
                        com.music.bitchord.auth.YtMusicLoginScreen(
                            mode = WebSessionMode.SIGN_IN,
                            captureRequest = captureTrigger,
                            onPageReady = { ready ->
                                isPageReadyToConfirm = ready
                                if (ready) captureTrigger++
                            },
                            onCaptured = { session ->
                                viewModel.onWebSession(session, WebSessionMode.SIGN_IN) { success ->
                                    if (success) {
                                        isJustPaired = true
                                        loginMode = "menu"
                                        onDismiss()
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
        return
    }

    TvDialog(
        title = if (signedIn) "Account Details" else "YouTube Music Sign In",
        onDismissRequest = onDismiss,
    ) {
        if (signedIn && (account != null || isJustPaired)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF30D158),
                    )
                    Text(
                        text = "Connected Successfully",
                        color = Color(0xFF30D158),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TvSFProDisplay,
                    )
                }

                Text(
                    text = "Name: ${account?.name ?: "Personal Account"}",
                    color = TvColors.TextPrimary,
                    fontSize = 15.sp,
                    fontFamily = TvSFProDisplay,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!account?.email.isNullOrBlank()) {
                    Text(
                        text = "Email: ${account?.email}",
                        color = TvColors.TextSecondary,
                        fontSize = 13.sp,
                        fontFamily = TvSFProDisplay,
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TvButton(
                        text = "Sign Out",
                        isPrimary = false,
                        onClick = {
                            viewModel.signOut()
                            isJustPaired = false
                        },
                    )
                    TvButton(
                        text = "Done",
                        isPrimary = true,
                        onClick = onDismiss,
                    )
                }
            }
        } else if (loginMode == "menu") {
            // Main Choice Menu
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Choose how you would like to sign into your YouTube Music account:",
                    fontSize = 15.sp,
                    color = TvColors.TextSecondary,
                    fontFamily = TvSFProDisplay,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TvButton(
                        text = "Sign In with Google",
                        isPrimary = true,
                        onClick = { loginMode = "google" },
                        modifier = Modifier.weight(1f),
                    )
                    TvButton(
                        text = "Scan Phone QR",
                        isPrimary = false,
                        onClick = { loginMode = "qr" },
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TvButton(
                        text = "Cancel",
                        onClick = onDismiss,
                    )
                }
            }
        } else if (loginMode == "qr") {
            // QR Code Phone-to-TV Sign In Mode
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // QR Code View
                    if (pairingUrl != null) {
                        TvQrCodeView(
                            content = pairingUrl!!,
                            size = 180.dp,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(TvColors.SurfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Connecting to local Wi-Fi...",
                                color = TvColors.TextSecondary,
                                fontSize = 12.sp,
                                fontFamily = TvSFProDisplay,
                            )
                        }
                    }

                    // Steps & Instructions
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Scan QR Code with Phone",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = TvSFProDisplay,
                            color = TvColors.TextPrimary,
                        )

                        Text(
                            text = "1. Connect phone to same Wi-Fi\n2. Scan QR or visit:\n   ${pairingUrl ?: "Waiting for network..."}\n3. Paste cookie on your phone",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = TvColors.TextSecondary,
                            fontFamily = TvSFProDisplay,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TvButton(
                        text = "Back",
                        onClick = { loginMode = "menu" },
                    )
                    TvButton(
                        text = "Cancel",
                        onClick = onDismiss,
                    )
                }
            }
        } else {
            // Manual Remote Typing Mode
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter your YouTube Music Cookie or Session Token below for personalized library access:",
                    color = TvColors.TextSecondary,
                    fontSize = 14.sp,
                    fontFamily = TvSFProDisplay,
                )

                BasicTextField(
                    value = cookieText,
                    onValueChange = { cookieText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .tvButtonFocus(shape = RoundedCornerShape(10.dp))
                        .background(TvColors.SurfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = TvSFProDisplay),
                    cursorBrush = SolidColor(TvColors.AccentRed),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TvButton(
                        text = "Use QR Code",
                        icon = Icons.Default.QrCode,
                        onClick = { loginMode = "qr" },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvButton(
                            text = "Cancel",
                            onClick = onDismiss,
                        )
                        TvButton(
                            text = "Save & Connect",
                            isPrimary = true,
                            enabled = cookieText.isNotBlank(),
                            onClick = {
                                viewModel.onSignedIn(cookieText)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TvDiscordDialog(
    onDismiss: () -> Unit,
) {
    val discordRpc by AppSettings.discordRpcEnabled.collectAsState()
    val discordToken by AppSettings.discordToken.collectAsState()
    var tokenInput by remember { mutableStateOf(discordToken) }

    TvDialog(
        title = "Discord Rich Presence",
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Show what you're listening to on BitChord in your Discord status.",
                color = TvColors.TextSecondary,
                fontSize = 14.sp,
                fontFamily = TvSFProDisplay,
            )

            BasicTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .tvButtonFocus(shape = RoundedCornerShape(10.dp))
                    .background(TvColors.SurfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = TvSFProDisplay),
                cursorBrush = SolidColor(TvColors.AccentRed),
                singleLine = true,
                decorationBox = { inner ->
                    if (tokenInput.isEmpty()) {
                        Text("Discord User Token", color = TvColors.TextMuted, fontSize = 14.sp, fontFamily = TvSFProDisplay)
                    }
                    inner()
                },
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                TvButton(
                    text = "Close",
                    onClick = onDismiss,
                )
                TvButton(
                    text = if (discordRpc) "Disable RPC" else "Enable RPC",
                    isPrimary = true,
                    onClick = {
                        AppSettings.setDiscordToken(tokenInput)
                        AppSettings.setDiscordRpcEnabled(!discordRpc)
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
fun TvScrobbleDialog(
    onDismiss: () -> Unit,
) {
    val lastfmEnabled by AppSettings.lastfmEnabled.collectAsState()
    val lastfmUser by AppSettings.lastfmUsername.collectAsState()
    val listenBrainzEnabled by AppSettings.listenBrainzEnabled.collectAsState()
    val listenBrainzToken by AppSettings.listenBrainzToken.collectAsState()

    TvDialog(
        title = "Scrobbling Services",
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "Last.fm Account: ${if (lastfmEnabled && lastfmUser.isNotBlank()) lastfmUser else "Not connected"}",
                color = TvColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = TvSFProDisplay,
            )

            Text(
                text = "ListenBrainz: ${if (listenBrainzEnabled && listenBrainzToken.isNotBlank()) "Connected" else "Not connected"}",
                color = TvColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = TvSFProDisplay,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TvButton(
                    text = "Done",
                    isPrimary = true,
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
fun TvSourcesDialog(
    onDismiss: () -> Unit,
) {
    val moduleUrl = "Default bundled modules"

    TvDialog(
        title = "Hi-Res Stream Module Sources",
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "BitChord uses pluggable JavaScript module sources (QuickJS runtime) for resolving lossless FLAC/ALAC tracks.",
                color = TvColors.TextSecondary,
                fontSize = 14.sp,
                fontFamily = TvSFProDisplay,
            )

            Text(
                text = "Current Index: $moduleUrl",
                color = TvColors.TextPrimary,
                fontSize = 14.sp,
                fontFamily = TvSFProDisplay,
                fontWeight = FontWeight.Medium,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TvButton(
                    text = "Close",
                    isPrimary = true,
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
fun TvAboutDialog(
    onDismiss: () -> Unit,
) {
    TvDialog(
        title = "About BitChord TV",
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header Banner
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_logo),
                        contentDescription = "BitChord Logo",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Column {
                    Text(
                        text = "BitChord TV Edition",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.W800,
                        fontFamily = TvSFProDisplay,
                        color = Color.White,
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} TV • Apple Music Living Room Client",
                        fontSize = 13.sp,
                        fontFamily = TvSFProDisplay,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Developer Credit Cards
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Main Developer: Kushagra Singh (@kushagrasinghx)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Kushagra Singh",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = TvSFProDisplay,
                            color = Color.White,
                        )
                        Text(
                            text = "@kushagrasinghx",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = TvSFProDisplay,
                            color = TvColors.AccentRed,
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Main Developer & Creator • Core Android Client, InnerTube Engine & Automix",
                        fontSize = 12.sp,
                        fontFamily = TvSFProDisplay,
                        color = Color.White.copy(alpha = 0.70f),
                    )
                }

                // TV Lead & Architect: Nithyanantha / Nimalanrao (@nimalanrao)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Nithyanantha (Nimalanrao)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = TvSFProDisplay,
                            color = Color.White,
                        )
                        Text(
                            text = "@nimalanrao",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = TvSFProDisplay,
                            color = Color(0xFF64D2FF),
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "TV Platform Lead & Architect • 10-foot UI/UX, 120Hz Engine, Apple Music Layout & Flowing Lyrics",
                        fontSize = 12.sp,
                        fontFamily = TvSFProDisplay,
                        color = Color.White.copy(alpha = 0.70f),
                    )
                }
            }

            Text(
                text = "Free software licensed under GNU General Public License v3.0 (GPLv3). Independent third-party client not affiliated with YouTube or Google LLC.",
                fontSize = 11.sp,
                fontFamily = TvSFProDisplay,
                color = Color.White.copy(alpha = 0.50f),
                lineHeight = 16.sp,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TvButton(
                    text = "Close",
                    isPrimary = true,
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
fun TvRefreshRateDialog(
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity

    val capabilities by com.music.bitchord.ui.tv.display.TvRefreshRateController.capabilities.collectAsState()
    val currentPref by com.music.bitchord.ui.tv.display.TvRefreshRateController.preference.collectAsState()

    TvDialog(
        title = "Interface Refresh Rate",
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "Active Display: ${capabilities.currentPhysicalWidth} × ${capabilities.currentPhysicalHeight} @ ${String.format("%.1f", capabilities.actualRefreshRateHz)} Hz",
                color = TvColors.AccentRed,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = TvSFProDisplay,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Auto Option
                TvRefreshRateOptionRow(
                    preference = com.music.bitchord.ui.tv.display.TvRefreshRatePreference.SYSTEM_AUTO,
                    isSelected = currentPref == com.music.bitchord.ui.tv.display.TvRefreshRatePreference.SYSTEM_AUTO,
                    isEnabled = true,
                    onClick = {
                        activity?.let { com.music.bitchord.ui.tv.display.TvRefreshRateController.setPreference(it, com.music.bitchord.ui.tv.display.TvRefreshRatePreference.SYSTEM_AUTO) }
                        onDismiss()
                    },
                )

                // Smooth 60 Hz Option
                TvRefreshRateOptionRow(
                    preference = com.music.bitchord.ui.tv.display.TvRefreshRatePreference.SMOOTH_60,
                    isSelected = currentPref == com.music.bitchord.ui.tv.display.TvRefreshRatePreference.SMOOTH_60,
                    isEnabled = capabilities.is60HzSupported,
                    onClick = {
                        activity?.let { com.music.bitchord.ui.tv.display.TvRefreshRateController.setPreference(it, com.music.bitchord.ui.tv.display.TvRefreshRatePreference.SMOOTH_60) }
                        onDismiss()
                    },
                )

                // Ultra 120 Hz Option
                TvRefreshRateOptionRow(
                    preference = com.music.bitchord.ui.tv.display.TvRefreshRatePreference.ULTRA_120,
                    isSelected = currentPref == com.music.bitchord.ui.tv.display.TvRefreshRatePreference.ULTRA_120,
                    isEnabled = true,
                    disabledReason = null,
                    onClick = {
                        activity?.let { com.music.bitchord.ui.tv.display.TvRefreshRateController.setPreference(it, com.music.bitchord.ui.tv.display.TvRefreshRatePreference.ULTRA_120) }
                        onDismiss()
                    },
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TvButton(
                    text = "Close",
                    isPrimary = true,
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun TvRefreshRateOptionRow(
    preference: com.music.bitchord.ui.tv.display.TvRefreshRatePreference,
    isSelected: Boolean,
    isEnabled: Boolean,
    disabledReason: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvButtonFocus(
                shape = RoundedCornerShape(10.dp),
                focusedBorderColor = TvColors.BorderFocused,
                onClick = if (isEnabled) onClick else null,
            )
            .background(if (isSelected) TvColors.SurfaceSelected else TvColors.SurfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = preference.label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TvSFProDisplay,
                    color = if (isEnabled) TvColors.TextPrimary else TvColors.TextMuted,
                )
                if (isSelected) {
                    Text(
                        text = "ACTIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TvColors.AccentRed,
                        fontFamily = TvSFProDisplay,
                    )
                }
            }
            Text(
                text = disabledReason ?: preference.description,
                fontSize = 12.sp,
                fontFamily = TvSFProDisplay,
                color = if (isEnabled) TvColors.TextSecondary else TvColors.TextMuted,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
fun TvThemeDialog(
    onDismiss: () -> Unit,
) {
    val currentThemeId by AppSettings.tvTheme.collectAsState()
    val currentTheme = com.music.bitchord.ui.tv.personalization.AppThemeOption.fromId(currentThemeId)

    TvDialog(
        title = "Choose Visual Theme",
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            com.music.bitchord.ui.tv.personalization.AppThemeOption.entries.forEach { theme ->
                val isSelected = theme == currentTheme
                val palette = theme.getPalette()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvButtonFocus(
                            shape = RoundedCornerShape(10.dp),
                            focusedBorderColor = TvColors.BorderFocused,
                            onClick = {
                                AppSettings.setTvTheme(theme.id)
                                onDismiss()
                            },
                        )
                        .background(if (isSelected) TvColors.SurfaceSelected else TvColors.SurfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = theme.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = TvSFProDisplay,
                                color = TvColors.TextPrimary,
                            )
                            if (isSelected) {
                                Text(
                                    text = "SELECTED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.accent,
                                    fontFamily = TvSFProDisplay,
                                )
                            }
                        }
                        Text(
                            text = theme.description,
                            fontSize = 12.sp,
                            fontFamily = TvSFProDisplay,
                            color = TvColors.TextSecondary,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TvButton(
                    text = "Close",
                    isPrimary = true,
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
fun TvNicknameDialog(
    onDismiss: () -> Unit,
) {
    val currentSavedNickname by AppSettings.tvNickname.collectAsState()
    var draftNickname by remember { mutableStateOf(currentSavedNickname) }
    var cursorIndex by remember { mutableIntStateOf(draftNickname.length) }

    val validation = com.music.bitchord.ui.tv.personalization.NicknamePolicy.validate(draftNickname)
    val isValid = validation is com.music.bitchord.ui.tv.personalization.NicknameValidationResult.Valid
    val graphemeCount = com.music.bitchord.ui.tv.personalization.NicknamePolicy.getGraphemeCount(draftNickname)

    TvDialog(
        title = "Edit TV Nickname",
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            var isInputFocused by remember { mutableStateOf(false) }
            val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

            // Input box using default Android TV IME keyboard
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TvColors.SurfaceVariant)
                    .border(
                        width = if (isInputFocused) 3.dp else 1.dp,
                        color = if (isInputFocused) Color.White else Color.White.copy(alpha = 0.20f),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (draftNickname.isEmpty()) {
                    Text(
                        text = "Enter nickname...",
                        color = TvColors.TextMuted,
                        fontSize = 16.sp,
                        fontFamily = TvSFProDisplay,
                    )
                }

                androidx.compose.foundation.text.BasicTextField(
                    value = draftNickname,
                    onValueChange = { newText ->
                        if (com.music.bitchord.ui.tv.personalization.NicknamePolicy.getGraphemeCount(newText) <= com.music.bitchord.ui.tv.personalization.NicknamePolicy.MAX_GRAPHEMES) {
                            draftNickname = newText
                        }
                    },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TvSFProDisplay,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
                        autoCorrectEnabled = false,
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            if (isValid) {
                                AppSettings.setTvNickname(draftNickname)
                                onDismiss()
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isInputFocused = it.isFocused },
                )
            }

            if (validation is com.music.bitchord.ui.tv.personalization.NicknameValidationResult.Invalid) {
                Text(
                    text = validation.reason,
                    color = TvColors.AccentRed,
                    fontSize = 12.sp,
                    fontFamily = TvSFProDisplay,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TvButton(
                    text = "Save",
                    isPrimary = true,
                    enabled = isValid,
                    onClick = {
                        AppSettings.setTvNickname(draftNickname)
                        onDismiss()
                    },
                )
            }
        }
    }
}

/**
 * Mobile Web Remote Pairing Dialog.
 * Shows a QR code for http://[tv-ip]:[port]/remote so users can control the TV from their phone.
 */
@Composable
fun TvRemoteDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var remoteUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        scope.launch {
            val serverInfo = TvAuthServer.start()
            if (serverInfo != null) {
                val (port, ip) = serverInfo
                remoteUrl = "http://$ip:$port/remote"
            }
        }
        onDispose {}
    }

    TvDialog(
        title = "Mobile Web Remote",
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (remoteUrl != null) {
                    TvQrCodeView(
                        content = remoteUrl!!,
                        size = 180.dp,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(TvColors.SurfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Starting Remote Server...",
                            color = TvColors.TextSecondary,
                            fontSize = 12.sp,
                            fontFamily = TvSFProDisplay,
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Control BitChord TV from Phone",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TvSFProDisplay,
                        color = TvColors.TextPrimary,
                    )

                    Text(
                        text = "1. Connect phone to same Wi-Fi\n2. Scan the QR code or visit:\n   ${remoteUrl ?: "Waiting for network..."}\n3. Play/pause, seek, search YouTube Music, and use the D-pad remote directly on your phone!",
                        fontSize = 13.sp,
                        fontFamily = TvSFProDisplay,
                        color = TvColors.TextSecondary,
                        lineHeight = 18.sp,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TvButton(
                    text = "Close",
                    isPrimary = true,
                    onClick = onDismiss,
                )
            }
        }
    }
}

