package com.music.bitchord.ui.tv.dialogs

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.music.bitchord.BuildConfig
import com.music.bitchord.auth.GoogleAccountSession
import com.music.bitchord.auth.WebSessionMode
import com.music.bitchord.auth.YouTubeProfile
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.TvPinThrottle
import com.music.bitchord.data.settings.TvSettings
import kotlin.math.ceil
import kotlinx.coroutines.delay
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
import com.music.bitchord.ui.tv.components.tvClick
import com.music.bitchord.ui.tv.components.tvInitialFocus
import com.music.bitchord.ui.tv.components.tvLift
import com.music.bitchord.ui.tv.components.tvPlatter
import com.music.bitchord.ui.tv.display.TvRefreshRateController
import com.music.bitchord.ui.tv.display.TvRefreshRatePreference
import com.music.bitchord.ui.tv.personalization.AppThemeOption
import com.music.bitchord.ui.tv.theme.TvGlass
import com.music.bitchord.ui.tv.theme.TvType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private enum class AccountDialogMode { Profiles, SignInChoose, SignInPhone, SignInTv }

/** Account: profile switcher, who is signed in, or ways to sign in from a TV. */
@Composable
fun TvAccountDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSignOut: () -> Unit = {},
    /** Offer signing in with a phone (QR). Off goes straight to signing in on the TV. */
    allowPhoneSignIn: Boolean = false,
) {
    val googleAccounts by viewModel.googleAccounts.collectAsState()
    val activeAccountId by viewModel.activeAccountId.collectAsState()
    val activeProfileId by viewModel.activeProfileId.collectAsState()
    val signedIn by viewModel.signedIn.collectAsState()

    var mode by remember {
        mutableStateOf(
            if (googleAccounts.isNotEmpty() || signedIn) {
                AccountDialogMode.Profiles
            } else if (allowPhoneSignIn) {
                AccountDialogMode.SignInChoose
            } else {
                AccountDialogMode.SignInTv
            }
        )
    }

    val backFromSignIn: () -> Unit = {
        if (googleAccounts.isNotEmpty() || signedIn) {
            mode = AccountDialogMode.Profiles
        } else if (allowPhoneSignIn) {
            mode = AccountDialogMode.SignInChoose
        } else {
            onDismiss()
        }
    }

    when (mode) {
        AccountDialogMode.Profiles -> {
            TvProfileSwitcher(
                googleAccounts = googleAccounts,
                activeAccountId = activeAccountId,
                activeProfileId = activeProfileId,
                onSelectProfile = { account, profile ->
                    viewModel.selectProfile(account.accountId, profile.profileId, profile)
                    onDismiss()
                },
                onRemoveAccount = { account ->
                    TvSettings.wipeAllPins()
                    viewModel.removeAccount(account.accountId)
                    onSignOut()
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }
        AccountDialogMode.SignInChoose -> TvDialog(
            title = "Sign In to YouTube Music",
            message = "Your library, playlists and recommendations follow your account.",
            onDismissRequest = backFromSignIn,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                TvListRow(
                    title = "Sign In on This TV",
                    subtitle = "Use the remote to sign in to Google.",
                    leadingIcon = Icons.Rounded.Tv,
                    modifier = Modifier.tvInitialFocus(),
                    onClick = { mode = AccountDialogMode.SignInTv },
                )
                TvListRow(
                    title = "Sign In with Your Phone",
                    subtitle = "Scan a code with a phone on the same Wi-Fi.",
                    leadingIcon = Icons.Rounded.PhoneAndroid,
                    onClick = { mode = AccountDialogMode.SignInPhone },
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            TvDialogButton(
                text = if (googleAccounts.isNotEmpty() || signedIn) "Back" else "Not Now",
                onClick = backFromSignIn,
            )
        }
        AccountDialogMode.SignInPhone -> TvPhoneSignIn(viewModel = viewModel, onBack = backFromSignIn)
        AccountDialogMode.SignInTv -> TvGoogleSignIn(
            viewModel = viewModel,
            onBack = backFromSignIn,
            onSignedIn = onDismiss,
        )
    }
}

/** Full-screen Apple-style profile switcher for Android TV. */
@Composable
private fun TvProfileSwitcher(
    googleAccounts: List<GoogleAccountSession>,
    activeAccountId: String?,
    activeProfileId: String?,
    onSelectProfile: (GoogleAccountSession, YouTubeProfile) -> Unit,
    onRemoveAccount: (GoogleAccountSession) -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingUnlockProfile by remember { mutableStateOf<Pair<GoogleAccountSession, YouTubeProfile>?>(null) }
    var pendingUnlockSettingsProfile by remember { mutableStateOf<Pair<GoogleAccountSession, YouTubeProfile>?>(null) }
    var selectedAccountForSettings by remember { mutableStateOf<Pair<GoogleAccountSession, YouTubeProfile>?>(null) }
    var unlockError by remember { mutableStateOf<String?>(null) }
    var unlockSettingsError by remember { mutableStateOf<String?>(null) }

    val allProfiles = remember(googleAccounts) {
        googleAccounts.flatMap { account ->
            if (account.profiles.isNotEmpty()) {
                val personal = account.profiles.filterNot { it.isBrandAccount }
                val brand = account.profiles.filter { it.isBrandAccount }
                (personal + brand).map { profile -> account to profile }
            } else {
                listOf(
                    account to YouTubeProfile(
                        profileId = "default:${account.accountId}",
                        name = account.name.ifBlank { "Personal" },
                        handle = account.email,
                    )
                )
            }
        }
    }

    val currentSelectedProfile = remember(allProfiles, activeAccountId, activeProfileId) {
        if (activeProfileId != null) {
            allProfiles.firstOrNull { it.second.profileId == activeProfileId }?.second
        } else if (activeAccountId != null) {
            allProfiles.firstOrNull { it.first.accountId == activeAccountId }?.second
        } else {
            allProfiles.firstOrNull()?.second
        }
    }
    val isCurrentProfileLocked = currentSelectedProfile?.let { TvSettings.isProfileLocked(it.profileId) } == true
    val context = LocalContext.current

    val settingsPair = selectedAccountForSettings
    if (settingsPair != null) {
        TvAccountSettingsDialog(
            account = settingsPair.first,
            profile = settingsPair.second,
            onSignOut = {
                onRemoveAccount(settingsPair.first)
                selectedAccountForSettings = null
            },
            onDismiss = { selectedAccountForSettings = null },
        )
    }

    val unlockPair = pendingUnlockProfile
    if (unlockPair != null) {
        TvPinDialog(
            title = "Enter 4-Digit PIN",
            message = "Profile is locked. Enter PIN for ${unlockPair.second.name}",
            errorMessage = unlockError,
            throttleKey = unlockPair.second.profileId,
            onPinEntered = { entered ->
                val correct = TvSettings.verifyProfilePin(unlockPair.second.profileId, entered)
                if (correct) {
                    unlockError = null
                    val (account, profile) = unlockPair
                    pendingUnlockProfile = null
                    onSelectProfile(account, profile)
                } else {
                    unlockError = "Incorrect PIN. Try again."
                }
                correct
            },
            onDismiss = {
                pendingUnlockProfile = null
                unlockError = null
            },
        )
    }

    val unlockSettingsPair = pendingUnlockSettingsProfile
    if (unlockSettingsPair != null) {
        TvPinDialog(
            title = "Enter 4-Digit PIN",
            message = "Profile is locked. Enter PIN for ${unlockSettingsPair.second.name}",
            errorMessage = unlockSettingsError,
            throttleKey = unlockSettingsPair.second.profileId,
            onPinEntered = { entered ->
                val correct = TvSettings.verifyProfilePin(unlockSettingsPair.second.profileId, entered)
                if (correct) {
                    unlockSettingsError = null
                    val (account, profile) = unlockSettingsPair
                    pendingUnlockSettingsProfile = null
                    selectedAccountForSettings = account to profile
                } else {
                    unlockSettingsError = "Incorrect PIN. Try again."
                }
                correct
            },
            onDismiss = {
                pendingUnlockSettingsProfile = null
                unlockSettingsError = null
            },
        )
    }

    Dialog(
        onDismissRequest = {
            if (!isCurrentProfileLocked) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isCurrentProfileLocked,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BackHandler {
            if (isCurrentProfileLocked) {
                context.findActivity()?.finish()
            } else {
                onDismiss()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF2111113)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 36.dp, horizontal = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                TvAppMark(size = 64.dp)

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = if (allProfiles.isEmpty()) "Sign In" else "Who's watching?",
                    style = TvType.Title.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold),
                    color = TvGlass.TextPrimary,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (allProfiles.isEmpty()) {
                        "Nobody is signed in. Add a YouTube Music account to access your music."
                    } else {
                        "Choose a profile to switch your library, playlists and recommendations."
                    },
                    style = TvType.Callout,
                    color = TvGlass.TextSecondary,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(32.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    modifier = Modifier.focusGroup(),
                ) {
                    itemsIndexed(
                        items = allProfiles,
                        key = { _, pair -> "${pair.first.accountId}:${pair.second.profileId}" },
                    ) { index, (account, profile) ->
                        var itemFocused by remember { mutableStateOf(false) }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.onFocusChanged { itemFocused = it.hasFocus },
                        ) {
                            TvProfileCard(
                                account = account,
                                profile = profile,
                                modifier = Modifier
                                    .tvInitialFocus(enabled = index == 0)
                                    .focusProperties {
                                        if (index == 0) left = FocusRequester.Cancel
                                        if (index == allProfiles.lastIndex) right = FocusRequester.Cancel
                                    },
                                onClick = {
                                    if (TvSettings.isProfileLocked(profile.profileId)) {
                                        unlockError = null
                                        pendingUnlockProfile = account to profile
                                    } else {
                                        onSelectProfile(account, profile)
                                    }
                                },
                            )

                            TvProfileSettingsButton(
                                visible = itemFocused,
                                modifier = Modifier.focusProperties {
                                    if (index == 0) left = FocusRequester.Cancel
                                    if (index == allProfiles.lastIndex) right = FocusRequester.Cancel
                                },
                                onClick = {
                                    if (TvSettings.isProfileLocked(profile.profileId)) {
                                        unlockSettingsError = null
                                        pendingUnlockSettingsProfile = account to profile
                                    } else {
                                        selectedAccountForSettings = account to profile
                                    }
                                },
                            )
                        }
                    }
                }

                if (!isCurrentProfileLocked) {
                    Spacer(modifier = Modifier.height(28.dp))

                    TvButton(
                        text = "Cancel",
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun TvProfileCard(
    account: GoogleAccountSession,
    profile: YouTubeProfile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = modifier
            .width(170.dp)
            .height(210.dp)
            .tvLift(interaction, shape, focusedScale = 1.08f, elevation = 20.dp)
            .tvPlatter(
                interaction,
                resting = TvGlass.Fill,
                focused = TvGlass.Platter,
            )
            .tvClick(interaction, onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(TvGlass.Placeholder),
            contentAlignment = Alignment.Center,
        ) {
            if (!profile.avatar.isNullOrBlank()) {
                TvArtwork(
                    url = profile.avatar,
                    px = 512,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = if (focused) TvGlass.OnPlatterSecondary else TvGlass.TextSecondary,
                    modifier = Modifier.size(44.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = profile.name.ifBlank { account.name.ifBlank { "Account" } },
            style = TvType.CardTitle.copy(fontWeight = FontWeight.Bold),
            color = if (focused) TvGlass.OnPlatter else TvGlass.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(4.dp))

        val handleOrEmail = profile.handle.ifBlank {
            account.email.ifBlank {
                if (profile.isBrandAccount) "Brand account" else ""
            }
        }
        if (handleOrEmail.isNotBlank()) {
            Text(
                text = handleOrEmail,
                style = TvType.Caption,
                color = if (focused) TvGlass.OnPlatterSecondary else TvGlass.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun TvProfileSettingsButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(180),
        label = "settingsButtonAlpha",
    )

    Box(
        modifier = modifier
            .size(38.dp)
            .graphicsLayer { this.alpha = alpha }
            .focusProperties { canFocus = visible }
            .tvLift(interaction, CircleShape, focusedScale = 1.15f, elevation = 8.dp)
            .tvPlatter(interaction, resting = Color.Transparent, focused = TvGlass.Platter)
            .tvClick(interaction, enabled = visible, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Settings,
            contentDescription = "Account Settings",
            tint = if (focused) TvGlass.OnPlatter else TvGlass.TextPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

private enum class PinMode { Set, Confirm, Remove, SignOut }

@Composable
private fun TvAccountSettingsDialog(
    account: GoogleAccountSession,
    profile: YouTubeProfile,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    var isLocked by remember { mutableStateOf(TvSettings.isProfileLocked(profile.profileId)) }
    var pinMode by remember { mutableStateOf<PinMode?>(null) }
    var tempPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    when (pinMode) {
        PinMode.Set -> {
            TvPinDialog(
                title = "Set 4-Digit PIN",
                message = "Create a 4-digit PIN for ${profile.name}",
                errorMessage = pinError,
                onPinEntered = { entered ->
                    tempPin = entered
                    pinError = null
                    pinMode = PinMode.Confirm
                    true
                },
                onDismiss = { pinMode = null; pinError = null },
            )
        }
        PinMode.Confirm -> {
            TvPinDialog(
                title = "Confirm 4-Digit PIN",
                message = "Re-enter your 4-digit PIN",
                errorMessage = pinError,
                onPinEntered = { entered ->
                    if (entered == tempPin) {
                        TvSettings.setProfilePin(profile.profileId, entered)
                        isLocked = true
                        pinMode = null
                        pinError = null
                        true
                    } else {
                        pinError = "PINs did not match. Try again."
                        pinMode = PinMode.Set
                        false
                    }
                },
                onDismiss = { pinMode = null; pinError = null },
            )
        }
        PinMode.Remove -> {
            TvPinDialog(
                title = "Turn Off App Lock",
                message = "Enter current 4-digit PIN to remove lock",
                errorMessage = pinError,
                throttleKey = profile.profileId,
                onPinEntered = { entered ->
                    val correct = TvSettings.verifyProfilePin(profile.profileId, entered)
                    if (correct) {
                        TvSettings.setProfilePin(profile.profileId, null)
                        isLocked = false
                        pinMode = null
                        pinError = null
                    } else {
                        pinError = "Incorrect PIN"
                    }
                    correct
                },
                onDismiss = { pinMode = null; pinError = null },
            )
        }
        PinMode.SignOut -> {
            TvPinDialog(
                title = "Sign Out",
                message = "Enter 4-digit PIN to sign out ${profile.name}",
                errorMessage = pinError,
                throttleKey = profile.profileId,
                onPinEntered = { entered ->
                    val correct = TvSettings.verifyProfilePin(profile.profileId, entered)
                    if (correct) {
                        TvSettings.wipeAllPins()
                        pinMode = null
                        pinError = null
                        onSignOut()
                        onDismiss()
                    } else {
                        pinError = "Incorrect PIN"
                    }
                    correct
                },
                onDismiss = { pinMode = null; pinError = null },
            )
        }
        null -> {
            val isFirstProfile = !profile.isBrandAccount ||
                account.profiles.firstOrNull { !it.isBrandAccount }?.profileId == profile.profileId ||
                account.profiles.firstOrNull()?.profileId == profile.profileId ||
                account.profiles.isEmpty()

            TvDialog(
                title = profile.name.ifBlank { account.name.ifBlank { "Account Settings" } },
                message = profile.handle.ifBlank { account.email.ifBlank { null } },
                onDismissRequest = onDismiss,
            ) {
                TvArtwork(
                    url = profile.avatar ?: account.profiles.firstOrNull()?.avatar,
                    px = 240,
                    shape = CircleShape,
                    modifier = Modifier
                        .padding(bottom = 21.dp)
                        .size(77.dp),
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TvListRow(
                        title = "App Lock (4-Digit PIN)",
                        subtitle = if (isLocked) "Profile lock is On" else "Profile lock is Off",
                        value = if (isLocked) "On" else "Off",
                        leadingIcon = if (isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        onClick = {
                            pinError = null
                            pinMode = if (isLocked) PinMode.Remove else PinMode.Set
                        },
                    )

                    if (isFirstProfile) {
                        TvListRow(
                            title = "Sign Out",
                            subtitle = "Remove this account from TV",
                            destructive = true,
                            leadingIcon = Icons.AutoMirrored.Rounded.Logout,
                            onClick = {
                                if (isLocked) {
                                    pinError = null
                                    pinMode = PinMode.SignOut
                                } else {
                                    TvSettings.wipeAllPins()
                                    onSignOut()
                                    onDismiss()
                                }
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TvDialogButton(text = "Done", onClick = onDismiss, initialFocus = true)
            }
        }
    }
}

@Composable
private fun TvPinDialog(
    title: String,
    message: String,
    errorMessage: String? = null,
    throttleKey: String? = null,
    onPinEntered: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf(errorMessage) }
    var throttleText by remember { mutableStateOf<String?>(null) }
    var cooldownTrigger by remember { mutableLongStateOf(0L) }
    var isInputBlocked by remember { mutableStateOf(false) }

    val cancelFocusRequester = remember { FocusRequester() }
    val firstKeyFocusRequester = remember { FocusRequester() }

    val keypadAlpha by animateFloatAsState(
        targetValue = if (isInputBlocked) 0.35f else 1f,
        animationSpec = tween(250),
        label = "keypadAlpha",
    )

    LaunchedEffect(isInputBlocked) {
        if (isInputBlocked) {
            runCatching { cancelFocusRequester.requestFocus() }
        } else {
            runCatching { firstKeyFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(errorMessage) {
        if (!errorMessage.isNullOrBlank()) {
            localError = errorMessage
        }
    }

    LaunchedEffect(throttleKey, cooldownTrigger) {
        val remainingMs = throttleKey?.let { TvPinThrottle.getDelayRemainingMs(it) } ?: 0L

        if (remainingMs > 0L) {
            isInputBlocked = true
            val sec = ceil(remainingMs / 1000.0).toInt()
            val timeStr = TvPinThrottle.formatDuration(sec)
            throttleText = "Try again in $timeStr"
            delay(remainingMs)
            isInputBlocked = false
            throttleText = null
            localError = null
        } else {
            isInputBlocked = false
            throttleText = null
        }
    }

    val submitPin: (String) -> Unit = { candidate ->
        if (!isInputBlocked && candidate.length == 4) {
            val success = onPinEntered(candidate)
            if (!success) {
                pin = ""
                val delayMs = throttleKey?.let { TvPinThrottle.recordFailure(it) } ?: 0L
                localError = errorMessage ?: "Incorrect PIN"
                if (delayMs > 0L) {
                    val sec = ceil(delayMs / 1000.0).toInt()
                    val timeStr = TvPinThrottle.formatDuration(sec)
                    throttleText = "Try again in $timeStr"
                    isInputBlocked = true
                    cooldownTrigger = System.currentTimeMillis()
                } else {
                    throttleText = null
                    isInputBlocked = false
                }
            } else {
                throttleKey?.let { TvPinThrottle.recordSuccess(it) }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BackHandler { onDismiss() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF2111113))
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                    val digit = when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> '0'
                        KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> '1'
                        KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> '2'
                        KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> '3'
                        KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> '4'
                        KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> '5'
                        KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> '6'
                        KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> '7'
                        KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> '8'
                        KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> '9'
                        else -> null
                    }
                    if (digit != null) {
                        if (isInputBlocked) return@onPreviewKeyEvent true
                        if (pin.length < 4) {
                            localError = null
                            val next = pin + digit
                            pin = next
                            if (next.length == 4) {
                                submitPin(next)
                            }
                            return@onPreviewKeyEvent true
                        }
                        return@onPreviewKeyEvent true
                    } else if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DEL) {
                        if (isInputBlocked) return@onPreviewKeyEvent true
                        if (pin.isNotEmpty()) {
                            pin = pin.dropLast(1)
                            localError = null
                            return@onPreviewKeyEvent true
                        }
                        return@onPreviewKeyEvent false
                    }
                    false
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = TvType.Title.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                    color = TvGlass.TextPrimary,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = message,
                    style = TvType.Callout,
                    color = TvGlass.TextSecondary,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(4) { index ->
                        val filled = index < pin.length
                        Box(
                            modifier = Modifier
                                .size(width = 54.dp, height = 62.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (filled) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f)
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (filled) "●" else "",
                                style = TvType.Title.copy(fontSize = 22.sp),
                                color = TvGlass.TextPrimary,
                            )
                        }
                    }
                }

                val displayError = when {
                    throttleText != null -> {
                        val base = localError
                            ?.removeSuffix(".")
                            ?.replace(Regex("\\.?\\s*Try again\\.?$", RegexOption.IGNORE_CASE), "")
                            ?.trim()
                        if (!base.isNullOrBlank()) "$base. $throttleText" else throttleText
                    }
                    !localError.isNullOrBlank() -> localError
                    else -> null
                }

                if (!displayError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = displayError,
                        style = TvType.Caption.copy(fontWeight = FontWeight.SemiBold),
                        color = TvGlass.Destructive,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "⌫"),
                )

                Column(
                    modifier = Modifier.graphicsLayer { alpha = keypadAlpha },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    keys.forEachIndexed { rowIndex, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { label ->
                                val interaction = remember { MutableInteractionSource() }
                                val focused by interaction.collectIsFocusedAsState()

                                Box(
                                    modifier = Modifier
                                        .size(width = 68.dp, height = 44.dp)
                                        .tvLift(
                                            interaction,
                                            RoundedCornerShape(10.dp),
                                            focusedScale = if (isInputBlocked) 1f else 1.1f,
                                            elevation = if (isInputBlocked) 0.dp else 8.dp,
                                        )
                                        .tvPlatter(
                                            interaction,
                                            resting = TvGlass.Fill,
                                            focused = if (isInputBlocked) TvGlass.Fill else TvGlass.Platter,
                                        )
                                        .tvClick(interaction, enabled = !isInputBlocked) {
                                            if (isInputBlocked) return@tvClick
                                            when (label) {
                                                "C" -> {
                                                    pin = ""
                                                    localError = null
                                                }
                                                "⌫" -> {
                                                    if (pin.isNotEmpty()) {
                                                        pin = pin.dropLast(1)
                                                        localError = null
                                                    }
                                                }
                                                else -> {
                                                    if (pin.length < 4) {
                                                        localError = null
                                                        val next = pin + label
                                                        pin = next
                                                        if (next.length == 4) {
                                                            submitPin(next)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        .then(
                                            if (rowIndex == 0 && label == "1") {
                                                Modifier
                                                    .focusRequester(firstKeyFocusRequester)
                                                    .tvInitialFocus()
                                            } else Modifier
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = label,
                                        style = TvType.Body.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                                        color = if (isInputBlocked) TvGlass.TextSecondary.copy(alpha = 0.5f)
                                                else if (focused) TvGlass.OnPlatter else TvGlass.TextPrimary,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                TvButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(cancelFocusRequester),
                )
            }
        }
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
