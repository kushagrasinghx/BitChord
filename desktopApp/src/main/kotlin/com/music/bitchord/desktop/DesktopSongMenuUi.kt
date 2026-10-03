package com.music.bitchord.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbDownOffAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.icons.BitChordIcons

/** The verbs the song menu needs that the player does not otherwise know about. */
internal data class DesktopSongActions(
    val signedIn: Boolean,
    val disliked: Boolean,
    val downloaded: Boolean,
    val downloadInProgress: Boolean,
    val sleepTimerMinutes: Int?,
    val sleepAfterTrack: Boolean,
    val onToggleDislike: (Song) -> Unit,
    val onAddToPlaylist: (Song) -> Unit,
    val onDownload: (Song) -> Unit,
    val onRemoveDownload: (Song) -> Unit,
    val onStartRadio: (Song) -> Unit,
    val onPlayNext: (Song) -> Unit,
    val onAddToQueue: (Song) -> Unit,
    val onOpenAlbum: (String) -> Unit,
    val onOpenArtist: (String) -> Unit,
    val onSleepTimer: (Int?) -> Unit,
    val onSleepAfterTrack: () -> Unit,
    val onShare: (Song) -> Unit,
)

/** The "…" beside the player's heart, and the menu it opens. */
@Composable
internal fun DesktopSongMenuAnchor(
    song: Song,
    liked: Boolean,
    actions: DesktopSongActions,
    onToggleLike: () -> Unit,
    onRevertToOriginal: (() -> Unit)?,
    onUpgradeQuality: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var open by remember(song.videoId) { mutableStateOf(false) }
    Box(modifier) {
        DesktopMoreButton(onClick = { open = true })
        if (open) {
            DesktopSongMenuFor(
                song = song,
                liked = liked,
                actions = actions,
                onToggleLike = onToggleLike,
                onRevertToOriginal = onRevertToOriginal,
                onUpgradeQuality = onUpgradeQuality,
                onDismiss = { open = false },
            )
        }
    }
}

/** The menu [DesktopSongMenuAnchor] opens, for a caller that has its own button. */
@Composable
internal fun DesktopSongMenuFor(
    song: Song,
    liked: Boolean,
    actions: DesktopSongActions,
    onToggleLike: () -> Unit,
    onRevertToOriginal: (() -> Unit)?,
    onUpgradeQuality: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val close = onDismiss
    DesktopSongMenu(
        song = song,
        signedIn = actions.signedIn,
        likeStatus = when {
            liked -> LikeStatus.LIKE
            actions.disliked -> LikeStatus.DISLIKE
            else -> LikeStatus.INDIFFERENT
        },
        downloaded = actions.downloaded,
        downloadInProgress = actions.downloadInProgress,
        sleepTimerMinutes = actions.sleepTimerMinutes,
        sleepAfterTrack = actions.sleepAfterTrack,
        onRevertToOriginal = onRevertToOriginal?.let { { it(); close() } },
        onUpgradeQuality = onUpgradeQuality?.let { { it(); close() } },
        // Rating stays open: the menu says what the rating now is, and closing it is the
        // one way to not see that it worked.
        onToggleLike = onToggleLike,
        onToggleDislike = { actions.onToggleDislike(song) },
        onAddToPlaylist = { actions.onAddToPlaylist(song); close() },
        onDownload = { actions.onDownload(song); close() },
        onRemoveDownload = { actions.onRemoveDownload(song); close() },
        onStartRadio = { actions.onStartRadio(song); close() },
        onPlayNext = { actions.onPlayNext(song); close() },
        onAddToQueue = { actions.onAddToQueue(song); close() },
        onOpenAlbum = { id -> actions.onOpenAlbum(id); close() },
        onOpenArtist = { id -> actions.onOpenArtist(id); close() },
        onSleepTimer = { actions.onSleepTimer(it); close() },
        onSleepAfterTrack = { actions.onSleepAfterTrack(); close() },
        onShare = { actions.onShare(song); close() },
        onDismiss = close,
    )
}

/** Everything the player can do to the track it is playing, in one menu. */
@Composable
internal fun DesktopSongMenu(
    song: Song,
    signedIn: Boolean,
    likeStatus: LikeStatus,
    downloaded: Boolean,
    downloadInProgress: Boolean,
    onRemoveDownload: () -> Unit,
    sleepTimerMinutes: Int?,
    sleepAfterTrack: Boolean,
    /** Sends the playing track back to YouTube's own upload and keeps it there. */
    onRevertToOriginal: (() -> Unit)?,
    /** The way back from a revert, for a track pinned to YouTube's own upload. */
    onUpgradeQuality: (() -> Unit)?,
    onToggleLike: () -> Unit,
    onToggleDislike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onStartRadio: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onOpenAlbum: ((String) -> Unit)?,
    onOpenArtist: ((String) -> Unit)?,
    onSleepTimer: (Int?) -> Unit,
    onSleepAfterTrack: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pickingSleepTimer by remember { mutableStateOf(false) }
    val liked = likeStatus == LikeStatus.LIKE
    val disliked = likeStatus == LikeStatus.DISLIKE
    // A local file or a finished download has no YouTube identity behind it to rate, queue into a
    // playlist, fetch again, or share a link for.
    val isOffline = song.localUri != null

    DesktopMenuSurface(onDismiss = onDismiss) {
        if (pickingSleepTimer) {
            DesktopMenuHeader("Sleep timer") { pickingSleepTimer = false }
            DesktopMenuDivider()
            DesktopSleepTimer.presets.forEach { minutes ->
                DesktopMenuRow(
                    icon = Icons.Rounded.Bedtime,
                    label = "$minutes minutes",
                    selected = sleepTimerMinutes == minutes && !sleepAfterTrack,
                ) { onSleepTimer(minutes) }
            }
            DesktopMenuRow(
                icon = Icons.Rounded.Bedtime,
                label = DesktopStrings["d_end_of_this_track", "End of this track"],
                selected = sleepAfterTrack,
                onClick = onSleepAfterTrack,
            )
            if (sleepTimerMinutes != null || sleepAfterTrack) {
                DesktopMenuDivider()
                DesktopMenuRow(Icons.Rounded.Bedtime, DesktopStrings["d_turn_off", "Turn off"]) { onSleepTimer(null) }
            }
            return@DesktopMenuSurface
        }

        // The two are never both offered.
        (onRevertToOriginal ?: onUpgradeQuality)?.let { action ->
            DesktopMenuRow(
                icon = if (onRevertToOriginal != null) Icons.AutoMirrored.Rounded.Undo else Icons.Rounded.HighQuality,
                label = if (onRevertToOriginal != null) "Revert to original" else "Upgrade quality",
                onClick = action,
            )
            DesktopMenuDivider()
        }

        if (signedIn && !isOffline) {
            DesktopMenuRow(
                icon = if (liked) BitChordIcons.HeartFilled else BitChordIcons.Heart,
                label = if (liked) "Remove from liked" else "Like",
                tint = if (liked) DesktopAccent else null,
                onClick = onToggleLike,
            )
            DesktopMenuRow(
                icon = if (disliked) Icons.Rounded.ThumbDown else Icons.Rounded.ThumbDownOffAlt,
                label = if (disliked) "Undo dislike" else "Dislike",
                tint = if (disliked) DesktopAccent else null,
                onClick = onToggleDislike,
            )
        }
        if (!isOffline) {
            DesktopMenuRow(Icons.AutoMirrored.Rounded.PlaylistAdd, DesktopStrings["add_to_playlist", "Add to playlist"], onClick = onAddToPlaylist)
        }
        if (signedIn || !isOffline) DesktopMenuDivider()

        // A saved copy is offered its own way out whatever the track is playing from — a download
        // being played *is* a local file, and hiding the row there left no way to remove it.
        // Only starting a new download needs a YouTube identity behind it.
        when {
            downloadInProgress -> DesktopMenuRow(
                icon = BitChordIcons.Download,
                label = DesktopStrings["cancel_download", "Cancel download"],
                busy = true,
                onClick = onDownload,
            )
            downloaded -> DesktopMenuRow(
                icon = Icons.Rounded.DownloadDone,
                label = DesktopStrings["saved_to_downloads", "Saved to Downloads"],
                tint = DesktopAccent,
                value = DesktopStrings["delete", "Delete"],
                onClick = onRemoveDownload,
            )
            !isOffline -> DesktopMenuRow(
                icon = BitChordIcons.Download,
                label = DesktopStrings["download", "Download"],
                onClick = onDownload,
            )
        }
        DesktopMenuRow(Icons.Rounded.Radio, DesktopStrings["start_radio", "Start radio"], onClick = onStartRadio)
        DesktopMenuRow(Icons.AutoMirrored.Rounded.PlaylistPlay, DesktopStrings["play_next", "Play next"], onClick = onPlayNext)
        DesktopMenuRow(Icons.AutoMirrored.Rounded.QueueMusic, DesktopStrings["add_to_queue", "Add to queue"], onClick = onAddToQueue)

        val albumId = song.albumId
        val artistId = song.artistId
        if ((albumId != null && onOpenAlbum != null) || (artistId != null && onOpenArtist != null)) {
            DesktopMenuDivider()
            if (albumId != null && onOpenAlbum != null) {
                DesktopMenuRow(Icons.Rounded.Album, DesktopStrings["open_album", "Open album"]) { onOpenAlbum(albumId) }
            }
            if (artistId != null && onOpenArtist != null) {
                DesktopMenuRow(Icons.Rounded.Person, DesktopStrings["open_artist", "Open artist"]) { onOpenArtist(artistId) }
            }
        }

        DesktopMenuDivider()
        DesktopMenuRow(
            icon = Icons.Rounded.Bedtime,
            label = DesktopStrings["sleep_timer", "Sleep timer"],
            value = sleepTimerLabel(sleepTimerMinutes, sleepAfterTrack),
        ) { pickingSleepTimer = true }
        if (!isOffline) {
            DesktopMenuRow(Icons.Rounded.Share, DesktopStrings["share", "Share"], onClick = onShare)
        }
    }
}

private fun sleepTimerLabel(minutes: Int?, afterTrack: Boolean): String = when {
    afterTrack -> "End of track"
    minutes != null -> "$minutes min"
    else -> ""
}

/** The panel a menu is drawn in: a floating card in the app's own glass. */
@Composable
private fun DesktopMenuSurface(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Popup(
        popupPositionProvider = remember { DesktopMenuPosition() },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        val shape = RoundedCornerShape(12.dp)
        Column(
            Modifier
                .widthIn(min = 232.dp, max = 300.dp)
                .clip(shape)
                .desktopCard(shape)
                .padding(vertical = 6.dp)
                // A menu with every optional row on it is taller than a small window; capped and
                // scrolled rather than run off the screen.
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            content()
        }
    }
}

/** Where a menu hangs relative to the button that opened it. */
private class DesktopMenuPosition : PopupPositionProvider {

    private var above: Boolean? = null

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val margin = MENU_MARGIN
        val below = anchorBounds.bottom + margin
        val above = anchorBounds.top - popupContentSize.height - margin
        val fitsBelow = below + popupContentSize.height + margin <= windowSize.height
        val fitsAbove = above >= margin
        val placeAbove = this.above ?: (!fitsBelow && fitsAbove)
        if (this.above == null) this.above = placeAbove
        val y = when {
            placeAbove && fitsAbove -> above
            fitsBelow -> below
            // Neither side has room, so it is as tall as the window allows and sits between the
            // margins.
            else -> margin
        }
        val x = anchorBounds.left.coerceIn(
            margin,
            (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin),
        )
        return IntOffset(x, y.coerceAtMost((windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)))
    }
}

/** Air kept between a menu and the window's edges. */
private const val MENU_MARGIN = 12

/** A submenu's title, and the way back out of it. */
@Composable
private fun DesktopMenuHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .clickable(onClick = onBack)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ArrowBack, DesktopStrings["back", "Back"], tint = DesktopSecondary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

@Composable
private fun DesktopMenuDivider() {
    HorizontalDivider(
        Modifier.padding(vertical = 5.dp),
        thickness = 0.5.dp,
        color = DesktopCardEdge,
    )
}

/** One menu row. */
@Composable
private fun DesktopMenuRow(
    icon: ImageVector,
    label: String,
    tint: Color? = null,
    value: String = "",
    selected: Boolean = false,
    enabled: Boolean = true,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 5.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (hovered && enabled) DesktopRowHover else Color.Transparent)
            .hoverable(interaction, enabled = enabled)
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 32.dp)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(15.dp), color = DesktopAccent, strokeWidth = 2.dp)
        } else {
            Icon(
                icon,
                null,
                tint = tint ?: if (enabled) Color.White.copy(alpha = 0.82f) else DesktopSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = tint ?: if (enabled) Color.White else DesktopSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (value.isNotBlank()) {
            Text(value, style = MaterialTheme.typography.bodySmall, color = DesktopSecondary)
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(BitChordIcons.Check, null, tint = DesktopAccent, modifier = Modifier.size(15.dp))
        }
    }
}

/** The "…" the menu hangs off, sized and tinted to sit beside the player's heart. */
@Composable
internal fun DesktopMoreButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier
            .size(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (hovered) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .hoverable(interaction)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Three dots drawn rather than taken from the icon set.
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) {
                Box(
                    Modifier
                        .size(3.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.75f)),
                )
            }
        }
    }
}
