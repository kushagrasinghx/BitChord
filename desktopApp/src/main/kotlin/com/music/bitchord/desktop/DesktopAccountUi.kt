package com.music.bitchord.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The account button in the toolbar: the listener's photo, or a person glyph when there is nobody
 * signed in.
 */
@Composable
internal fun DesktopAccountButton(
    avatar: String?,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (avatar != null) {
            DesktopArtwork(
                avatar,
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                contentScale = ContentScale.Crop,
                px = 96,
            )
        } else {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Person,
                    DesktopStrings["accounts", "Accounts"],
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * The account switcher: every login this app holds, the channels under each, and the actions that
 * change the set.
 */
@Composable
internal fun DesktopAccountSelector(
    accounts: List<DesktopGoogleAccount>,
    activeAccountId: String?,
    activeProfileId: String?,
    busy: Boolean,
    onSelect: (DesktopGoogleAccount, DesktopYouTubeProfile) -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (DesktopGoogleAccount) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    var managing by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        // The scrim is its own layer rather than a modifier on the container.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 64.dp, end = 24.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .clip(shape)
                // The same frosted pane the floating bars are made of, sampling the page underneath
                // rather than sitting on a flat fill.
                .desktopBarGlass(shape),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (accounts.isEmpty()) "Accounts" else "Switch account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Spacer(Modifier.weight(1f))
                if (busy) CircularProgressIndicator(color = DesktopAccent, modifier = Modifier.size(16.dp))
            }
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                if (accounts.isEmpty()) {
                    item {
                        Text(
                            DesktopStrings[
                                "d_nobody_is_signed_in",
                                "Nobody is signed in. Your library, playlists and history need a " +
                                    "YouTube Music account.",
                            ],
                            style = MaterialTheme.typography.bodySmall,
                            color = DesktopSecondary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        )
                    }
                }
                accounts.forEach { account ->
                    item(key = "header-${account.accountId}") {
                        Text(
                            account.email.ifBlank { account.name.ifBlank { "Account" } },
                            style = MaterialTheme.typography.labelMedium,
                            color = DesktopSecondary,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(account.profiles.size, key = { "p-${account.accountId}-$it" }) { index ->
                        val profile = account.profiles[index]
                        DesktopProfileRow(
                            profile = profile,
                            selected = account.accountId == activeAccountId &&
                                profile.profileId == activeProfileId,
                            managing = managing,
                            onClick = { onSelect(account, profile); onDismiss() },
                            onRemove = { onRemoveAccount(account) },
                        )
                    }
                }
                item { DesktopSelectorAction(Icons.Rounded.Add, "Add account", onAddAccount) }
                if (accounts.isNotEmpty()) {
                    item {
                        DesktopSelectorAction(Icons.Rounded.ManageAccounts, "Manage accounts") {
                            managing = !managing
                        }
                    }
                }
                item { DesktopSelectorAction(Icons.Rounded.Settings, "Settings", onOpenSettings) }
                item { Spacer(Modifier.height(10.dp)) }
            }
        }
    }
}

@Composable
private fun DesktopProfileRow(
    profile: DesktopYouTubeProfile,
    selected: Boolean,
    managing: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = if (managing) onRemove else onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (profile.avatar != null) {
            DesktopArtwork(
                profile.avatar,
                Modifier.size(38.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
                px = 128,
            )
        } else {
            Icon(
                Icons.Rounded.Person,
                null,
                tint = DesktopSecondary,
                modifier = Modifier.size(38.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(profile.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                profile.handle.ifBlank { if (profile.isBrandAccount) "Brand account" else "Personal" },
                style = MaterialTheme.typography.bodySmall,
                color = DesktopSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            managing -> Text(DesktopStrings["sign_out", "Sign out"], color = DesktopAccent, style = MaterialTheme.typography.labelMedium)
            selected -> Icon(Icons.Rounded.Check, DesktopStrings["selected", "Selected"], tint = DesktopAccent, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun DesktopSelectorAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = Color.White)
    }
}

/** Signing in: a browser to take the session from, or the cookie by hand. */
@Composable
internal fun DesktopSignInDialog(
    onImport: (DesktopBrowserCookies.Profile) -> Unit,
    onPaste: (String) -> Unit,
    busy: String?,
    error: String?,
    onDismiss: () -> Unit,
) {
    var profiles by remember { mutableStateOf<List<DesktopBrowserCookies.Profile>?>(null) }
    var pasting by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        profiles = withContext(Dispatchers.IO) { DesktopBrowserCookies.profiles() }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(shape)
                .desktopBarGlass(shape)
                .padding(bottom = 8.dp),
        ) {
            Text(
                DesktopStrings["sign_in_youtube_music", "Sign in to YouTube Music"],
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp),
            )
            Text(
                DesktopStrings[
                    "d_google_signs_in_inside_a_browser",
                    "Google signs in inside a browser and hands back a session. Pick a browser " +
                        "here that is already signed in and BitChord will use its session.",
                ],
                style = MaterialTheme.typography.bodySmall,
                color = DesktopSecondary,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 10.dp),
            )
            error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = DesktopAccent,
                    modifier = Modifier.padding(horizontal = 22.dp).padding(bottom = 8.dp),
                )
            }
            when {
                pasting -> {
                    Text(
                        DesktopStrings["d_paste_the_cookie_header_for_music_youtube_com", "Paste the Cookie header for music.youtube.com."],
                        style = MaterialTheme.typography.bodySmall,
                        color = DesktopSecondary,
                        modifier = Modifier.padding(horizontal = 22.dp),
                    )
                    BasicTextField(
                        value = pasted,
                        onValueChange = { pasted = it },
                        singleLine = false,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                        cursorBrush = SolidColor(DesktopAccent),
                        modifier = Modifier
                            .padding(horizontal = 22.dp, vertical = 10.dp)
                            .fillMaxWidth()
                            .heightIn(min = 84.dp, max = 160.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(12.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { pasting = false }) { Text(DesktopStrings["back", "Back"], color = DesktopSecondary) }
                        TextButton(
                            onClick = { onPaste(pasted.trim()) },
                            enabled = pasted.isNotBlank() && busy == null,
                        ) {
                            Text(DesktopStrings["sign_in", "Sign in"], color = DesktopAccent)
                        }
                    }
                }
                profiles == null -> Row(
                    Modifier.fillMaxWidth().padding(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(color = DesktopAccent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(DesktopStrings["d_looking_for_browsers", "Looking for browsers…"], color = DesktopSecondary)
                }
                else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    val found = profiles.orEmpty()
                    if (found.isEmpty()) {
                        item {
                            Text(
                                DesktopStrings["d_no_browser_profile_was_found_on_this_machine", "No browser profile was found on this machine."],
                                style = MaterialTheme.typography.bodySmall,
                                color = DesktopSecondary,
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                            )
                        }
                    }
                    items(found.size, key = { found[it].database.toString() }) { index ->
                        val profile = found[index]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clickable(enabled = busy == null) { onImport(profile) }
                                .padding(horizontal = 22.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(profile.label, color = Color.White, modifier = Modifier.weight(1f))
                            if (busy == profile.label) {
                                CircularProgressIndicator(color = DesktopAccent, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clickable { pasting = true }
                                .padding(horizontal = 22.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.ContentPaste,
                                null,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(DesktopStrings["d_paste_a_cookie_instead", "Paste a cookie instead"], color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
