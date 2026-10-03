package com.music.bitchord.desktop

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.ui.icons.BitChordIcons

/** Where a track goes: one of the account's playlists, a local one, or a new one. */
@Composable
internal fun DesktopPlaylistDialog(
    song: Song?,
    accountPlaylists: List<UserPlaylist>,
    localPlaylists: List<DesktopPlaylist>,
    signedIn: Boolean,
    /** Whether [song] is something YouTube can be asked to hold. */
    canUseAccount: Boolean,
    busy: Boolean,
    error: String?,
    onPickAccount: (UserPlaylist) -> Unit,
    onPickLocal: (DesktopPlaylist) -> Unit,
    onCreate: (String, PlaylistPrivacy) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(song == null) }

    DesktopDialogFrame(onDismiss = onDismiss) {
        if (creating) {
            NewPlaylistForm(
                // Nowhere to go back to when the dialog opened onto the form; its own dismiss is
                // the way out.
                onBack = if (song == null) null else ({ creating = false }),
                signedIn = signedIn && canUseAccount,
                busy = busy,
                error = error,
                onCreate = onCreate,
            )
            return@DesktopDialogFrame
        }

        DialogHeading("Add to playlist", song?.let { "${it.title} — ${it.artist}" }.orEmpty())
        error?.let { DialogError(it) }
        DialogAction(BitChordIcons.Plus, "New playlist", enabled = !busy) { creating = true }

        if (signedIn && !canUseAccount) {
            Text(
                DesktopStrings["d_this_track_is_not_from_youtube_music_so_it_can_only_go_i", "This track is not from YouTube Music, so it can only go in a playlist kept here."],
                style = MaterialTheme.typography.bodySmall,
                color = DesktopSecondary,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
            )
        }
        val account = if (canUseAccount) accountPlaylists else emptyList()
        when {
            busy && account.isEmpty() && localPlaylists.isEmpty() -> Box(
                Modifier.fillMaxWidth().height(96.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = DesktopAccent, modifier = Modifier.size(22.dp))
            }
            account.isEmpty() && localPlaylists.isEmpty() -> Text(
                when {
                    !signedIn -> "No playlists on this computer yet. Sign in to see the ones on your account."
                    // Signed in, but this track cannot go on the account — so offering to sign in
                    // would be answering a question nobody asked.
                    !canUseAccount -> "No playlists on this computer yet. Make one and this track goes straight into it."
                    else -> "No playlists yet. Make one and this song goes straight into it."
                },
                style = MaterialTheme.typography.bodySmall,
                color = DesktopSecondary,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
            )
            else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                if (account.isNotEmpty()) {
                    item(key = "heading:account") { DialogSectionHeading("On your account") }
                    items(account.size, key = { account[it].playlistId }) { index ->
                        val playlist = account[index]
                        DialogAction(
                            BitChordIcons.Queue,
                            playlist.title,
                            subtitle = playlist.subtitle,
                            enabled = !busy,
                        ) { onPickAccount(playlist) }
                    }
                }
                if (localPlaylists.isNotEmpty()) {
                    item(key = "heading:local") { DialogSectionHeading("On this computer") }
                    items(localPlaylists.size, key = { localPlaylists[it].id }) { index ->
                        val playlist = localPlaylists[index]
                        DialogAction(
                            BitChordIcons.Queue,
                            playlist.title,
                            subtitle = "${playlist.songs.size} songs",
                            enabled = !busy,
                        ) { onPickLocal(playlist) }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** Name and visibility, and nothing else. */
@Composable
private fun NewPlaylistForm(
    onBack: (() -> Unit)?,
    signedIn: Boolean,
    busy: Boolean,
    error: String?,
    onCreate: (String, PlaylistPrivacy) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf(PlaylistPrivacy.PRIVATE) }
    val focusRequester = remember { FocusRequester() }
    // The form exists to be typed into; opening it already focused saves the click that would
    // otherwise always follow.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val submit = { if (name.isNotBlank() && !busy) onCreate(name.trim(), privacy) }

    Row(
        Modifier.fillMaxWidth().padding(start = if (onBack != null) 8.dp else 22.dp, end = 22.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onBack?.let {
            IconButton(onClick = it) {
                Icon(Icons.Rounded.ArrowBack, DesktopStrings["back", "Back"], tint = Color.White)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                DesktopStrings["new_playlist", "New playlist"],
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Text(
                if (signedIn) "Saved to your YouTube Music account" else "Saved on this computer",
                style = MaterialTheme.typography.bodySmall,
                color = DesktopSecondary,
            )
        }
    }
    error?.let { DialogError(it) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 16.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (name.isEmpty()) {
                Text(DesktopStrings["playlist_name", "Playlist name"], style = MaterialTheme.typography.bodyLarge, color = DesktopSecondary)
            }
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                cursorBrush = SolidColor(DesktopAccent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        if (name.isNotEmpty()) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).clickable { name = "" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, DesktopStrings["clear_name", "Clear name"], tint = DesktopSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }

    if (signedIn) {
        DialogSectionHeading("Who can see it")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlaylistPrivacy.entries.forEach { option ->
                PrivacyPill(option.icon, option.label, option == privacy) { privacy = option }
            }
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(color = DesktopAccent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(12.dp))
        }
        TextButton(onClick = submit, enabled = name.isNotBlank() && !busy) {
            Text(DesktopStrings["create_playlist", "Create playlist"], color = if (name.isBlank() || busy) DesktopSecondary else DesktopAccent)
        }
    }
}

/** A name for something that already exists — renaming a playlist. */
@Composable
internal fun DesktopRenamePlaylistDialog(
    current: String,
    busy: Boolean,
    error: String?,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(current) { mutableStateOf(current) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val submit = { if (name.isNotBlank() && name.trim() != current && !busy) onRename(name.trim()) }

    DesktopDialogFrame(onDismiss = onDismiss) {
        DialogHeading("Rename playlist", current)
        error?.let { DialogError(it) }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 14.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                cursorBrush = SolidColor(DesktopAccent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        DialogButtons(
            busy = busy,
            confirmLabel = DesktopStrings["rename", "Rename"],
            confirmEnabled = name.isNotBlank() && name.trim() != current,
            onConfirm = submit,
            onDismiss = onDismiss,
        )
    }
}

/** The one destructive playlist action, so the one that asks first. */
@Composable
internal fun DesktopDeletePlaylistDialog(
    title: String,
    busy: Boolean,
    error: String?,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    DesktopDialogFrame(onDismiss = onDismiss) {
        DialogHeading(
            "Delete playlist",
            "“$title” will be removed from your YouTube Music account, on every " +
                "device. The songs in it are not deleted.",
        )
        error?.let { DialogError(it) }
        DialogButtons(
            busy = busy,
            confirmLabel = DesktopStrings["delete", "Delete"],
            confirmEnabled = true,
            onConfirm = onDelete,
            onDismiss = onDismiss,
        )
    }
}

/**
 * The panel every dialog on this page sits in: the app's own glass over a scrim, rather than
 * Material's surface.
 */
@Composable
private fun DesktopDialogFrame(onDismiss: () -> Unit, content: @Composable () -> Unit) {
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
        Box(
            Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .clip(shape)
                .desktopBarGlass(shape),
        ) {
            // Swallows clicks that land on the panel's own background so they do not reach the
            // scrim behind it and dismiss what was just clicked.
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            )
            Column(Modifier.padding(bottom = 8.dp)) { content() }
        }
    }
}

@Composable
private fun DialogHeading(title: String, subtitle: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp),
    )
    if (subtitle.isNotBlank()) {
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = DesktopSecondary,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 6.dp),
        )
    }
}

@Composable
private fun DialogSectionHeading(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = DesktopSecondary,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun DialogError(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = DesktopAccent,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 10.dp),
    )
}

@Composable
private fun DialogAction(
    icon: ImageVector,
    label: String,
    subtitle: String = "",
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = DesktopSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DialogButtons(
    busy: Boolean,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(color = DesktopAccent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(12.dp))
        }
        TextButton(onClick = onDismiss, enabled = !busy) { Text(DesktopStrings["cancel", "Cancel"], color = DesktopSecondary) }
        TextButton(onClick = onConfirm, enabled = confirmEnabled && !busy) {
            Text(confirmLabel, color = if (confirmEnabled && !busy) DesktopAccent else DesktopSecondary)
        }
    }
}

@Composable
private fun PrivacyPill(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) DesktopAccent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            null,
            tint = if (selected) DesktopAccent else DesktopSecondary,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) Color.White else DesktopSecondary,
        )
    }
}

private val PlaylistPrivacy.icon: ImageVector
    get() = when (this) {
        PlaylistPrivacy.PRIVATE -> Icons.Rounded.Lock
        PlaylistPrivacy.UNLISTED -> Icons.Rounded.Link
        PlaylistPrivacy.PUBLIC -> Icons.Rounded.Public
    }
