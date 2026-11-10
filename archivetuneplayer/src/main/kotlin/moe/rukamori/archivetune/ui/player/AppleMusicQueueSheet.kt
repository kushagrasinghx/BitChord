/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * ViviMusic-style in-place queue sheet for the Apple Music player design.
 * Ported from vivi-music Queue_v2.kt (commits 31797caf + e556187d) —
 * https://github.com/vivizzz007/vivi-music — and adapted to ArchiveTune's
 * package structure, PlayerConnection API, and shared component library.
 *
 * The sheet renders three fixed "pill" controls (Shuffle / Repeat / Sleep
 * Timer), a "Queue" header row with an edit-lock toggle, and a reorderable
 * LazyColumn of the current + upcoming queue items. It is designed to be
 * hosted inside the Apple Music player's morphing content area — it does
 * NOT manage its own BottomSheet (the player morphs to reveal this content
 * in place, ViviMusic-style).
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package moe.rukamori.archivetune.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.mutableLongStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.QueueEditLockKey
import moe.rukamori.archivetune.extensions.move
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.toggleRepeatMode
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.MediaMetadataListItem
import moe.rukamori.archivetune.ui.menu.PlayerMenu
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberPreference
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private val QueuePillHeight = 48.dp
private val QueuePillCornerRadius = 16.dp

@Composable
fun AppleMusicQueueSheet(
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current

    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle()
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()

    val localPlayer = playerConnection.localPlayer

    var locked by rememberPreference(QueueEditLockKey, defaultValue = true)

    var showSleepTimerDialog by remember { mutableStateOf(false) }
    val sleepTimerEnabled =
        remember(
            playerConnection.service.sleepTimer.triggerTime,
            playerConnection.service.sleepTimer.pauseWhenSongEnd,
        ) {
            playerConnection.service.sleepTimer.isActive
        }
    var sleepTimerTimeLeft by remember { mutableLongStateOf(0L) }

    LaunchedEffect(sleepTimerEnabled) {
        if (sleepTimerEnabled) {
            while (isActive) {
                sleepTimerTimeLeft =
                    if (playerConnection.service.sleepTimer.pauseWhenSongEnd) {
                        playerConnection.player.duration - playerConnection.player.currentPosition
                    } else {
                        playerConnection.service.sleepTimer.triggerTime - System.currentTimeMillis()
                    }
                delay(1000L)
            }
        }
    }

    val adaptivePrimary = Color.White
    val adaptiveSecondary = Color.White.copy(alpha = 0.7f)
    val adaptiveSurface = Color.White.copy(alpha = 0.2f)

    val lazyListState = rememberLazyListState()
    val mutableQueueWindows = remember { mutableStateListOf<Timeline.Window>() }

    val currentPlayingUid =
        remember(currentWindowIndex, queueWindows) {
            if (currentWindowIndex in queueWindows.indices) {
                queueWindows[currentWindowIndex].uid
            } else {
                null
            }
        }

    var dragInfo by remember { mutableStateOf<AMQueueDragInfo?>(null) }
    var justCommittedDragUid by remember { mutableStateOf<Any?>(null) }

    var hasScrolledToCurrent by remember { mutableStateOf(false) }
    LaunchedEffect(mutableQueueWindows.size, currentPlayingUid) {
        if (!hasScrolledToCurrent && currentPlayingUid != null) {
            val idx = mutableQueueWindows.indexOfFirst { it.uid == currentPlayingUid }
            if (idx != -1) {
                lazyListState.scrollToItem(idx)
                hasScrolledToCurrent = true
            }
        }
    }

    val reorderableState =
        rememberReorderableLazyListState(
            lazyListState = lazyListState,
        ) onMove@{ from, to ->
            val fromQueueIndex = from.index
            val toQueueIndex = to.index
            if (fromQueueIndex !in mutableQueueWindows.indices ||
                toQueueIndex !in mutableQueueWindows.indices
            ) {
                return@onMove
            }

            val draggedItemUid =
                dragInfo?.draggedItemUid ?: mutableQueueWindows[fromQueueIndex].uid
            val actualFromQueueIndex =
                mutableQueueWindows.indexOfFirst { it.uid == draggedItemUid }
            if (actualFromQueueIndex == -1) return@onMove

            mutableQueueWindows.move(actualFromQueueIndex, toQueueIndex)

            val destinationUid: Any? = mutableQueueWindows.getOrNull(toQueueIndex - 1)?.uid
            dragInfo = AMQueueDragInfo(draggedItemUid, destinationUid)
        }

    LaunchedEffect(queueWindows, currentWindowIndex, reorderableState.isAnyItemDragging) {
        if (reorderableState.isAnyItemDragging) return@LaunchedEffect

        val completedDrag = dragInfo
        if (completedDrag != null) {
            val sourceIndex =
                queueWindows.indexOfFirst { it.uid == completedDrag.draggedItemUid }
            val destinationAnchorIndex =
                queueWindows.indexOfFirst { it.uid == completedDrag.destinationUid }
            dragInfo = null

            if (sourceIndex != -1) {
                val destinationIndex =
                    if (destinationAnchorIndex == -1) {
                        0
                    } else if (sourceIndex < destinationAnchorIndex) {
                        destinationAnchorIndex
                    } else {
                        (destinationAnchorIndex + 1).coerceAtMost(queueWindows.lastIndex)
                    }

                if (sourceIndex != destinationIndex) {
                    justCommittedDragUid = completedDrag.draggedItemUid
                    if (!shuffleModeEnabled) {
                        playerConnection.player.moveMediaItem(sourceIndex, destinationIndex)
                    } else {
                        localPlayer.setShuffleOrder(
                            DefaultShuffleOrder(
                                queueWindows
                                    .map { it.firstPeriodIndex }
                                    .toMutableList()
                                    .move(sourceIndex, destinationIndex)
                                    .toIntArray(),
                                System.currentTimeMillis(),
                            ),
                        )
                    }
                    return@LaunchedEffect
                }
            }
        }

        if (justCommittedDragUid != null) {
            justCommittedDragUid = null
            return@LaunchedEffect
        }

        Snapshot.withMutableSnapshot {
            mutableQueueWindows.clear()

            mutableQueueWindows.addAll(queueWindows)
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Transparent),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val pillShape = RoundedCornerShape(QueuePillCornerRadius)
            val activeColor = adaptivePrimary.copy(alpha = 0.25f)
            val inactiveColor = adaptivePrimary.copy(alpha = 0.1f)

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(QueuePillHeight)
                        .background(if (shuffleModeEnabled) activeColor else inactiveColor, pillShape)
                        .clip(pillShape)
                        .clickable {
                            playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.shuffle),
                    contentDescription = "Shuffle",
                    tint = adaptivePrimary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(QueuePillHeight)
                        .background(if (repeatMode != Player.REPEAT_MODE_OFF) activeColor else inactiveColor, pillShape)
                        .clip(pillShape)
                        .clickable { playerConnection.player.toggleRepeatMode() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter =
                        painterResource(
                            when (repeatMode) {
                                Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                                else -> R.drawable.repeat
                            },
                        ),
                    contentDescription = "Repeat",
                    tint = adaptivePrimary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(QueuePillHeight)
                        .background(if (sleepTimerEnabled) activeColor else inactiveColor, pillShape)
                        .clip(pillShape)
                        .clickable {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.bedtime),
                        contentDescription = "Sleep Timer",
                        tint = adaptivePrimary,
                        modifier = Modifier.size(24.dp),
                    )
                    if (sleepTimerEnabled) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = makeTimeString(sleepTimerTimeLeft.coerceAtLeast(0L)),
                            style = MaterialTheme.typography.labelSmall,
                            color = adaptivePrimary,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.queue),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = adaptivePrimary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(
                            painter = painterResource(R.drawable.player_close),
                            contentDescription = stringResource(R.string.close_dialog),
                            tint = adaptiveSecondary,
                        )
                    }
                }
                IconButton(onClick = { locked = !locked }) {
                    Icon(
                        painter = painterResource(if (locked) R.drawable.lock else R.drawable.lock_open),
                        contentDescription = if (locked) "Unlock Queue" else "Lock Queue",
                        tint = adaptiveSecondary,
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(bottom = 16.dp, top = 4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(
                items = mutableQueueWindows,
                key = { _, item -> item.uid.hashCode() },
            ) { index, window ->
                ReorderableItem(
                    state = reorderableState,
                    key = window.uid.hashCode(),
                ) {
                    val isActive = window.uid == currentPlayingUid
                    val metadata = window.mediaItem.metadata ?: return@ReorderableItem
                    val isPlayed =
                        currentWindowIndex >= 0 && index < currentWindowIndex && !isActive

                    val dismissBoxState =
                        rememberSwipeToDismissBoxState(
                            positionalThreshold = { totalDistance -> totalDistance },
                        )
                    var processedDismiss by remember { mutableStateOf(false) }

                    LaunchedEffect(dismissBoxState.currentValue) {
                        val dv = dismissBoxState.currentValue
                        if (!processedDismiss &&
                            (dv == SwipeToDismissBoxValue.StartToEnd || dv == SwipeToDismissBoxValue.EndToStart)
                        ) {
                            processedDismiss = true
                            playerConnection.player.removeMediaItem(window.firstPeriodIndex)
                        }
                        if (dv == SwipeToDismissBoxValue.Settled) {
                            processedDismiss = false
                        }
                    }

                    val content: @Composable () -> Unit = {
                        val rowBg =
                            if (isActive) adaptiveSurface.copy(alpha = 0.22f) else adaptiveSurface.copy(alpha = 0.10f)
                        val rowShape = RoundedCornerShape(12.dp)

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clip(rowShape)
                                    .background(rowBg),
                        ) {
                            MediaMetadataListItem(
                                mediaMetadata = metadata,
                                isSelected = false,
                                isActive = isActive,
                                isPlaying = isPlaying && isActive,
                                shouldLoadImage = true,

                                showActiveContainer = false,

                                textColorOverride = if (isPlayed) Color.White.copy(alpha = 0.55f) else Color.White,
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                menuState.show {
                                                    PlayerMenu(
                                                        mediaMetadata = metadata,
                                                        navController = navController,
                                                        playerBottomSheetState = playerBottomSheetState,
                                                        isQueueTrigger = true,
                                                        onShowDetailsDialog = {
                                                            window.mediaItem.mediaId.let {
                                                                bottomSheetPageState.show {
                                                                    ShowMediaInfo(it)
                                                                }
                                                            }
                                                        },
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.more_vert),
                                                contentDescription = "Options",
                                                tint = adaptivePrimary,
                                            )
                                        }

                                        if (!locked) {
                                            IconButton(
                                                onClick = { },
                                                modifier = Modifier.draggableHandle(),
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.drag_handle),
                                                    contentDescription = "Drag to reorder",
                                                    tint = adaptivePrimary,
                                                )
                                            }
                                        }
                                    }
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
                                            playerConnection.player.playWhenReady = true
                                        },
                            )
                        }
                    }

                    if (locked) {
                        content()
                    } else {
                        SwipeToDismissBox(
                            state = dismissBoxState,
                            backgroundContent = {
                                val color by animateColorAsState(
                                    targetValue =
                                        when (dismissBoxState.targetValue) {
                                            SwipeToDismissBoxValue.Settled -> Color.Transparent
                                            else -> MaterialTheme.colorScheme.error
                                        },
                                    label = "swipeDismissBg",
                                )
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .padding(vertical = 4.dp, horizontal = 16.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(color),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    val iconAlpha by animateFloatAsState(
                                        targetValue = if (dismissBoxState.targetValue != SwipeToDismissBoxValue.Settled) 1f else 0f,
                                        label = "iconAlpha",
                                    )
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove",
                                        modifier =
                                            Modifier
                                                .padding(end = 16.dp)
                                                .alpha(iconAlpha),
                                        tint = MaterialTheme.colorScheme.onError,
                                    )
                                }
                            },
                            content = { content() },
                            enableDismissFromStartToEnd = false,
                        )
                    }
                }
            }
        }
        }

    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            onDismiss = { showSleepTimerDialog = false },
            onConfirm = { minutes ->
                showSleepTimerDialog = false
                playerConnection.service.sleepTimer.start(minutes)
            },
            onEndOfSong = {
                showSleepTimerDialog = false
                playerConnection.service.sleepTimer.start(-1)
            },
            initialValue = 30f,
        )
    }
}

@Immutable
private data class AMQueueDragInfo(
    val draggedItemUid: Any,
    val destinationUid: Any?,
)
