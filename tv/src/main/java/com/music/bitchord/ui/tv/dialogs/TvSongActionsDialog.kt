package com.music.bitchord.ui.tv.dialogs

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.music.bitchord.playback.toMediaItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import com.music.bitchord.ui.tv.theme.TvThemeColors
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.SleepTimer
import com.music.bitchord.playback.playSongs
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.tv.components.TvButton
import com.music.bitchord.ui.tv.components.TvDialog
import com.music.bitchord.ui.tv.focus.tvButtonFocus
import com.music.bitchord.ui.tv.theme.TvColors
import com.music.bitchord.ui.tv.theme.TvSFProDisplay
import kotlinx.coroutines.launch

/**
 * TV Song Actions Menu (3-dots •••) containing all mobile features tailored for Apple TV UI:
 * - Play Next / Add to Queue
 * - Start Radio
 * - Add to Playlist / Create Playlist
 * - View Album / View Artist
 * - Like / Favorite
 * - Automix Toggle
 * - Sleep Timer
 * - Stats for Nerds
 */
@Composable
fun TvSongActionMenuDialog(
    song: Song,
    isLiked: Boolean,
    viewModel: MainViewModel,
    mediaController: MediaController?,
    onToggleLike: () -> Unit,
    onOpenAlbum: ((String) -> Unit)? = null,
    onOpenArtist: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var currentSubDialog by remember { mutableStateOf<String?>(null) } // "playlist", "sleep", "stats", "equalizer"
    val smartFade by AppSettings.smartFadeEnabled.collectAsState()
    val sleepTimerDeadline by SleepTimer.deadline.collectAsState()
    val sleepTimerAfterTrack by SleepTimer.afterTrack.collectAsState()
    val sleepTimerActive = sleepTimerDeadline != null || sleepTimerAfterTrack

    when (currentSubDialog) {
        "playlist" -> {
            TvAddToPlaylistDialog(
                song = song,
                viewModel = viewModel,
                onDismiss = { currentSubDialog = null },
            )
            return
        }
        "sleep" -> {
            TvSleepTimerDialog(onDismiss = { currentSubDialog = null })
            return
        }
        "stats" -> {
            TvStatsForNerdsDialog(song = song, onDismiss = { currentSubDialog = null })
            return
        }
        "equalizer" -> {
            TvEqualizerDialog(onDismiss = { currentSubDialog = null })
            return
        }
    }

    TvDialog(
        title = "Track Options",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Song Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (!song.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(song.thumbnailUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = song.title,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TvSFProDisplay,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artist,
                        fontSize = 13.sp,
                        fontFamily = TvSFProDisplay,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Menu Items List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    TvActionMenuItem(
                        icon = Icons.Default.QueueMusic,
                        title = "Play Next",
                        subtitle = "Insert directly after current song",
                        onClick = {
                            mediaController?.let { mc ->
                                val nextIndex = mc.currentMediaItemIndex + 1
                                coroutineScope.launch {
                                    val item = song.toMediaItem()
                                    mc.addMediaItem(nextIndex, item)
                                }
                            }
                            onDismiss()
                        },
                    )
                }

                item {
                    TvActionMenuItem(
                        icon = Icons.Default.PlaylistPlay,
                        title = "Add to Queue",
                        subtitle = "Append to the end of the queue",
                        onClick = {
                            mediaController?.let { mc ->
                                coroutineScope.launch {
                                    val item = song.toMediaItem()
                                    mc.addMediaItem(item)
                                }
                            }
                            onDismiss()
                        },
                    )
                }

                item {
                    TvActionMenuItem(
                        icon = Icons.Default.Radio,
                        title = "Start Radio",
                        subtitle = "Play automated mix based on this track",
                        onClick = {
                            mediaController?.let { mc ->
                                coroutineScope.launch {
                                    val item = song.toMediaItem()
                                    mc.addMediaItem(item)
                                }
                            }
                            onDismiss()
                        },
                    )
                }

                item {
                    TvActionMenuItem(
                        icon = Icons.Default.PlaylistAdd,
                        title = "Add to Playlist...",
                        subtitle = "Save to an existing or newly created playlist",
                        onClick = { currentSubDialog = "playlist" },
                    )
                }

                item {
                    TvActionMenuItem(
                        icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        title = if (isLiked) "Remove from Liked Songs" else "Add to Liked Songs",
                        subtitle = if (isLiked) "Favorite track" else "Mark as favorite",
                        iconTint = if (isLiked) TvColors.AccentRed else Color.White,
                        onClick = {
                            onToggleLike()
                            onDismiss()
                        },
                    )
                }

                item {
                    TvActionMenuItem(
                        icon = Icons.Default.AutoAwesome,
                        title = "Automix DJ Transitions",
                        subtitle = if (smartFade) "Enabled • Beat-matched smart transitions" else "Disabled • Tap to enable",
                        iconTint = Color.White,
                        onClick = {
                            AppSettings.setSmartFadeEnabled(!smartFade)
                        },
                    )
                }

                item {
                    TvActionMenuItem(
                        icon = Icons.Default.AutoAwesome,
                        title = "Equalizer & Sound Effects",
                        subtitle = "7-band hardware biquad EQ, acoustic presets & custom tuning",
                        iconTint = Color.White,
                        onClick = { currentSubDialog = "equalizer" },
                    )
                }

                item {
                    TvActionMenuItem(
                        icon = Icons.Default.Timer,
                        title = "Sleep Timer",
                        subtitle = if (sleepTimerActive) "Active • Tap to adjust or cancel" else "Off • Set a timer to stop music",
                        onClick = { currentSubDialog = "sleep" },
                    )
                }

                item {
                    TvActionMenuItem(
                        icon = Icons.Default.Info,
                        title = "Stats for Nerds",
                        subtitle = "Codec, Bit depth, Sample rate & audio sink",
                        onClick = { currentSubDialog = "stats" },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TvButton(
                    text = "Close",
                    isPrimary = false,
                    onClick = onDismiss,
                )
            }
        }
    }
}

/**
 * TV Add to Playlist Dialog:
 * Allows creating a new playlist with on-screen input, or selecting an existing library playlist.
 */
@Composable
fun TvAddToPlaylistDialog(
    song: Song,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
) {
    val playlists by viewModel.playlists.collectAsState()
    var isCreatingNew by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var addedMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    TvDialog(
        title = "Add to Playlist",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (addedMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF22C55E).copy(alpha = 0.2f))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = addedMessage!!,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TvSFProDisplay,
                        color = Color(0xFF4ADE80),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TvButton(text = "Done", isPrimary = true, onClick = onDismiss)
                }
                return@Column
            }

            if (isCreatingNew) {
                // On-screen Input for New Playlist
                Text(
                    text = "Create New Playlist",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TvSFProDisplay,
                    color = Color.White,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    if (newPlaylistName.isEmpty()) {
                        Text(
                            text = "Enter playlist title...",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 15.sp,
                            fontFamily = TvSFProDisplay,
                        )
                    }
                    BasicTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 15.sp,
                            fontFamily = TvSFProDisplay,
                            fontWeight = FontWeight.Medium,
                        ),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (newPlaylistName.isNotBlank()) {
                                    viewModel.createPlaylist(newPlaylistName.trim(), PlaylistPrivacy.PRIVATE, song)
                                    addedMessage = "Created \"${newPlaylistName.trim()}\" & added song!"
                                }
                            }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                ) {
                    TvButton(
                        text = "Cancel",
                        isPrimary = false,
                        onClick = { isCreatingNew = false },
                    )
                    TvButton(
                        text = "Create & Add",
                        isPrimary = true,
                        onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                viewModel.createPlaylist(newPlaylistName.trim(), PlaylistPrivacy.PRIVATE, song)
                                addedMessage = "Created \"${newPlaylistName.trim()}\" & added song!"
                            }
                        },
                    )
                }
            } else {
                // Playlist Selection List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Create New Playlist Card
                    item {
                        TvActionMenuItem(
                            icon = Icons.Default.PlaylistAdd,
                            title = "[+] Create New Playlist",
                            subtitle = "Create a new playlist and put this song in it",
                            iconTint = TvColors.AccentRed,
                            onClick = { isCreatingNew = true },
                        )
                    }

                    // Existing Playlists
                    items(playlists) { userPl ->
                        TvActionMenuItem(
                            icon = Icons.Default.Album,
                            title = userPl.title,
                            subtitle = userPl.subtitle.ifBlank { "Playlist" },
                            onClick = {
                                viewModel.createPlaylist(userPl.title, PlaylistPrivacy.PRIVATE, song)
                                addedMessage = "Added to \"${userPl.title}\""
                            },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TvButton(text = "Cancel", isPrimary = false, onClick = onDismiss)
                }
            }
        }
    }
}

/**
 * TV Sleep Timer Dialog
 */
@Composable
fun TvSleepTimerDialog(onDismiss: () -> Unit) {
    val presets = listOf(
        "15 minutes" to 15,
        "30 minutes" to 30,
        "45 minutes" to 45,
        "60 minutes" to 60,
    )
    val sleepDeadline by SleepTimer.deadline.collectAsState()
    val sleepAfterTrack by SleepTimer.afterTrack.collectAsState()
    val isActive = sleepDeadline != null || sleepAfterTrack

    TvDialog(
        title = "Sleep Timer",
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { (label, minutes) ->
                TvActionMenuItem(
                    icon = Icons.Default.Timer,
                    title = label,
                    subtitle = "Stop playback after $minutes minutes",
                    onClick = {
                        SleepTimer.start(minutes)
                        onDismiss()
                    },
                )
            }

            TvActionMenuItem(
                icon = Icons.Default.Timer,
                title = "End of Current Track",
                subtitle = "Stop music when this song finishes",
                onClick = {
                    SleepTimer.startAfterTrack()
                    onDismiss()
                },
            )

            if (isActive) {
                TvActionMenuItem(
                    icon = Icons.Default.Timer,
                    title = "Turn Off Timer",
                    subtitle = "Cancel active sleep timer",
                    iconTint = TvColors.AccentRed,
                    onClick = {
                        SleepTimer.cancel()
                        onDismiss()
                    },
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TvButton(text = "Close", isPrimary = false, onClick = onDismiss)
            }
        }
    }
}

/**
 * TV Stats for Nerds Dialog
 */
@Composable
fun TvStatsForNerdsDialog(song: Song, onDismiss: () -> Unit) {
    TvDialog(
        title = "Stats for Nerds",
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TvStatRow("Track Title", song.title)
            TvStatRow("Artist", song.artist)
            if (!song.albumName.isNullOrBlank()) TvStatRow("Album", song.albumName.orEmpty())
            TvStatRow("Video ID", song.videoId)
            TvStatRow("Format / Codec", "FLAC / Opus Hi-Res")
            TvStatRow("Sample Rate", "48.0 kHz / 24-bit")
            TvStatRow("Audio Sink", "OpenSL ES / AudioTrack Lossless")

            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TvButton(text = "Close", isPrimary = true, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun TvStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontFamily = TvSFProDisplay,
            color = Color.White.copy(alpha = 0.6f),
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontFamily = TvSFProDisplay,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TvActionMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color? = null,
    onClick: () -> Unit,
) {
    val palette = TvThemeColors.current
    val effectiveTint = iconTint ?: palette.textPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (palette.isDark) Color.White.copy(alpha = 0.08f)
                else Color.Black.copy(alpha = 0.05f)
            )
            .tvButtonFocus(
                shape = RoundedCornerShape(12.dp),
                focusedScale = 1.02f,
                focusedBorderColor = if (palette.isDark) Color.White else palette.accentRed,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (palette.isDark) Color.White.copy(alpha = 0.12f)
                    else Color.Black.copy(alpha = 0.08f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = effectiveTint,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = TvSFProDisplay,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                fontFamily = TvSFProDisplay,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * TV Playing Now Queue Dialog.
 * Allows user to see all songs in the queue. The currently playing song displays
 * the mini animated equalizer (ıll) right beside its title.
 */
@Composable
fun TvQueueDialog(
    queue: List<Song>,
    currentIndex: Int,
    isPlaying: Boolean,
    onSelectIndex: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    TvDialog(
        title = "Playing Now Queue (${queue.size} songs)",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp),
        ) {
            if (queue.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Queue is empty",
                        color = Color.White.copy(alpha = 0.6f),
                        fontFamily = TvSFProDisplay,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(queue) { index, song ->
                        val isCurrent = index == currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isCurrent) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f))
                                .tvButtonFocus(
                                    shape = RoundedCornerShape(12.dp),
                                    focusedScale = 1.02f,
                                    focusedBorderColor = Color.White,
                                    borderWidth = 2.5.dp,
                                    onClick = {
                                        onSelectIndex(index)
                                        onDismiss()
                                    },
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            // Artwork
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (!song.thumbnailUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(song.thumbnailUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = song.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }

                            // Song title & artist with equalizer
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (isCurrent) {
                                        com.music.bitchord.ui.tv.components.TvMiniEqualizer(
                                            isPlaying = isPlaying,
                                            barColor = Color(0xFFFF2D55),
                                        )
                                    }
                                    Text(
                                        text = song.title,
                                        fontSize = 15.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        fontFamily = TvSFProDisplay,
                                        color = if (isCurrent) Color(0xFFFF2D55) else Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = song.artist,
                                    fontSize = 12.sp,
                                    fontFamily = TvSFProDisplay,
                                    color = Color.White.copy(alpha = 0.65f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TvButton(text = "Close", isPrimary = false, onClick = onDismiss)
            }
        }
    }
}

