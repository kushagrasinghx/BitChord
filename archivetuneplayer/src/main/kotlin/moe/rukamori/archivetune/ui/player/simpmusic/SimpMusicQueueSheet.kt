/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player.simpmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.utils.highRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private val SheetSurface = Color(0xFF202020)

private val SheetSubtitle = Color(0xFFA8A8A8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SimpMusicQueueSheet(
    playerConnection: PlayerConnection,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle()
    val queueTitle by playerConnection.queueTitle.collectAsStateWithLifecycle()

    val entries =
        remember(queueWindows) {
            queueWindows.mapNotNull { window ->
                (window.mediaItem?.metadata as? MediaMetadata)?.let { it to window.firstPeriodIndex }
            }
        }
    val currentEntry = entries.getOrNull(currentWindowIndex)?.first

    var menuIndex by remember { mutableStateOf<Int?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    LaunchedEffect(currentWindowIndex, entries.size) {
        if (currentWindowIndex in 0 until entries.size) {
            runCatching { listState.scrollToItem(currentWindowIndex) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetSurface,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = null,
        shape = RectangleShape,
        modifier = Modifier.fillMaxHeight(),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            shape = RectangleShape,
            colors = CardDefaults.cardColors().copy(containerColor = SheetSurface),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(R.drawable.simpmusic_keyboard_arrow_down),
                            contentDescription = stringResource(R.string.collapse),
                            tint = Color.White,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.now_playing).uppercase(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            maxLines = 1,
                        )
                        Text(
                            text = queueTitle ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .basicMarquee(iterations = Int.MAX_VALUE),
                        )
                    }
                    Spacer(Modifier.size(48.dp))
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.now_playing),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(6.dp))
                currentEntry?.let { SimpMusicQueueRow(it, isPlaying = true, onClick = null, onMore = null) }

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.queue),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 20.dp).weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.Start,
                ) {
                    itemsIndexed(entries, key = { i, entry -> "$i:${entry.first.id}" }) { index, entry ->
                        val (meta, periodIndex) = entry
                        SimpMusicQueueRow(
                            meta,
                            isPlaying = index == currentWindowIndex,
                            onClick = {
                                playerConnection.player.seekToDefaultPosition(periodIndex)
                                playerConnection.player.playWhenReady = true
                            },
                            onMore = { menuIndex = index },
                        )
                    }

                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }

    menuIndex?.let { index ->
        SimpMusicQueueItemMenu(
            index = index,
            canMoveUp = index > 0,
            canMoveDown = index < entries.size - 1,
            onMoveUp = { playerConnection.player.moveMediaItem(index, index - 1) },
            onMoveDown = { playerConnection.player.moveMediaItem(index, index + 1) },
            onRemove = { playerConnection.player.removeMediaItem(index) },
            onDismiss = { menuIndex = null },
        )
    }
}

@Composable
private fun SimpMusicQueueRow(
    metadata: MediaMetadata,
    isPlaying: Boolean,
    onClick: (() -> Unit)?,
    onMore: (() -> Unit)?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 15.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (isPlaying) {
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.White),
                )
            } else {
                AsyncImage(
                    model = metadata.thumbnailUrl?.highRes(),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(4.dp)),
                )
            }
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 10.dp)
                    .align(Alignment.CenterVertically),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text(
                text = metadata.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .basicMarquee(iterations = Int.MAX_VALUE),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = metadata.artists.joinToString { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = SheetSubtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .weight(1f)
                            .basicMarquee(iterations = Int.MAX_VALUE),
                )
            }
        }
        if (onMore != null) {
            IconButton(onClick = onMore, modifier = Modifier.size(32.dp)) {
                Icon(
                    painter = painterResource(R.drawable.simpmusic_more_vert),
                    contentDescription = stringResource(R.string.more_options),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpMusicQueueItemMenu(
    index: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetSurface,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(5.dp))
            Box(
                modifier =
                    Modifier
                        .width(60.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.4f)),
            )
            Spacer(Modifier.height(5.dp))
        }
        Column {
            if (canMoveUp) {
                SimpMusicQueueMenuRow(
                    icon = R.drawable.simpmusic_keyboard_double_arrow_up,
                    label = stringResource(R.string.simpmusic_move_up),
                    onClick = {
                        onMoveUp()
                        onDismiss()
                    },
                )
            }
            if (canMoveDown) {
                SimpMusicQueueMenuRow(
                    icon = R.drawable.simpmusic_keyboard_double_arrow_down,
                    label = stringResource(R.string.simpmusic_move_down),
                    onClick = {
                        onMoveDown()
                        onDismiss()
                    },
                )
            }
            SimpMusicQueueMenuRow(
                icon = R.drawable.simpmusic_delete,
                label = stringResource(R.string.remove_from_queue),
                onClick = {
                    onRemove()
                    onDismiss()
                },
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SimpMusicQueueMenuRow(
    icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = Color.White,
        )
        Spacer(Modifier.width(10.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}
