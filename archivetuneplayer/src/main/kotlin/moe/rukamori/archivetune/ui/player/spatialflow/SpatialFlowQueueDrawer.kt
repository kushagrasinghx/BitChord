/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.player.spatialflow

import android.annotation.SuppressLint
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import moe.rukamori.archivetune.LocalStableSystemBarsTopPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.move
import moe.rukamori.archivetune.models.MediaMetadata
import androidx.media3.common.Timeline
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

enum class SpatialFlowSleepTimerMode {
    OFF,
    CUSTOM,
    END_OF_SONG,
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SlidingQueueDrawer(
    isQueueExpanded: Boolean,
    onQueueExpandedChange: (Boolean) -> Unit,
    queueWindows: List<Timeline.Window>,
    currentSongIndex: Int,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    sleepTimerMode: SpatialFlowSleepTimerMode,
    onReorderQueue: (Int, Int) -> Unit,
    onPlaySongAtIndex: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleLoopMode: () -> Unit,
    onShowSleepTimerDialog: () -> Unit,
    playerBackgroundColor: Color,
    dynamicAccentColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    val slidingOffset by animateDpAsState(
        targetValue = if (isQueueExpanded) 0.dp else screenHeight + 100.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
        label = "QueueSlidingOffset",
    )

    val queueCornerRadius by animateDpAsState(
        targetValue = if (isQueueExpanded) 0.dp else 32.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
        label = "QueueCornerRadius",
    )
    val safeCornerRadius = queueCornerRadius.coerceAtLeast(0.dp)

    val queueBgColor =
        deriveArtworkSurfaceColor(
            sourceColor = playerBackgroundColor,
            isDark = isDark,
            darkLightness = 0.155f,
            lightLightness = 0.835f,
            darkSaturationRange = 0.32f..0.54f,
            lightSaturationRange = 0.30f..0.48f,
        )
    val queueTrayBackgroundColor =
        remember(playerBackgroundColor, isDark) {
            deriveArtworkSurfaceColor(
                sourceColor = playerBackgroundColor,
                isDark = isDark,
                darkLightness = 0.24f,
                lightLightness = 0.73f,
                darkSaturationRange = 0.30f..0.60f,
                lightSaturationRange = 0.24f..0.50f,
            )
        }
    val queueTrayInactiveButtonColor =
        remember(queueTrayBackgroundColor, isDark) {
            val hsl = FloatArray(3)
            androidx.core.graphics.ColorUtils.colorToHSL(queueTrayBackgroundColor.toArgb(), hsl)
            if (hsl[1] < 0.08f) {
                hsl[1] = 0f
            } else {
                hsl[1] = hsl[1].coerceIn(0.24f, 0.55f)
            }
            if (isDark) {
                hsl[2] = 0.33f
            } else {
                hsl[2] = 0.64f
            }
            Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
        }
    val queueTrayActiveButtonColor =
        remember(dynamicAccentColor, isDark) {
            val hsl = FloatArray(3)
            androidx.core.graphics.ColorUtils.colorToHSL(dynamicAccentColor.toArgb(), hsl)
            if (hsl[1] < 0.08f) {
                if (isDark) Color(0xFFE8E8EA) else Color(0xFF1F1E23)
            } else {
                hsl[1] = hsl[1].coerceAtLeast(0.45f)
                hsl[2] = if (isDark) 0.62f else 0.42f
                Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
            }
        }
    val queueTrayInactiveContentColor =
        remember(queueTrayInactiveButtonColor) {
            if (androidx.core.graphics.ColorUtils.calculateLuminance(queueTrayInactiveButtonColor.toArgb()) > 0.5) {
                Color(0xFF1C1B1F)
            } else {
                Color.White
            }
        }
    val queueTrayActiveContentColor =
        remember(queueTrayActiveButtonColor) {
            if (androidx.core.graphics.ColorUtils.calculateLuminance(queueTrayActiveButtonColor.toArgb()) > 0.5) {
                Color(0xFF1C1B1F)
            } else {
                Color.White
            }
        }

    val haptic = LocalHapticFeedback.current

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .offset { IntOffset(0, slidingOffset.roundToPx()) },
        shape = RoundedCornerShape(topStart = safeCornerRadius, topEnd = safeCornerRadius),
        color = queueBgColor,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val boxScope = this
            val lazyListState = rememberLazyListState()

            val mutableQueueWindows = remember { mutableStateListOf<Timeline.Window>() }
            var dragInfo by remember { mutableStateOf<SfQueueDragInfo?>(null) }

            val currentPlayingUid =
                remember(currentSongIndex, queueWindows) {
                    queueWindows.getOrNull(currentSongIndex)?.uid
                }

            val reorderableState =
                rememberReorderableLazyListState(lazyListState = lazyListState) onMove@{ from, to ->
                    val fromQueueIndex = from.index
                    val toQueueIndex = to.index
                    if (
                        fromQueueIndex !in mutableQueueWindows.indices ||
                        toQueueIndex !in mutableQueueWindows.indices
                    ) {
                        return@onMove
                    }

                    val draggedItemUid = dragInfo?.draggedItemUid ?: mutableQueueWindows[fromQueueIndex].uid
                    val actualFromQueueIndex = mutableQueueWindows.indexOfFirst { it.uid == draggedItemUid }
                    if (actualFromQueueIndex == -1) return@onMove

                    mutableQueueWindows.move(actualFromQueueIndex, toQueueIndex)
                    dragInfo =
                        SfQueueDragInfo(
                            draggedItemUid = draggedItemUid,
                            destination =
                                if (toQueueIndex == 0) {
                                    SfQueueDragDestination.Start
                                } else {
                                    SfQueueDragDestination.After(
                                        itemUid = mutableQueueWindows[toQueueIndex - 1].uid,
                                    )
                                },
                        )
                }

            LaunchedEffect(queueWindows, reorderableState.isAnyItemDragging) {
                if (reorderableState.isAnyItemDragging) return@LaunchedEffect

                val completedDrag = dragInfo
                if (completedDrag != null) {
                    val sourceIndex = queueWindows.indexOfFirst { it.uid == completedDrag.draggedItemUid }
                    val destinationIndex = completedDrag.destination.resolveIndex(queueWindows, sourceIndex)
                    dragInfo = null

                    if (
                        sourceIndex != -1 &&
                        destinationIndex != null &&
                        sourceIndex != destinationIndex
                    ) {
                        onReorderQueue(sourceIndex, destinationIndex)
                        return@LaunchedEffect
                    }
                }

                Snapshot.withMutableSnapshot {
                    mutableQueueWindows.clear()
                    mutableQueueWindows.addAll(queueWindows)
                }
            }

            LaunchedEffect(isQueueExpanded) {
                if (isQueueExpanded && currentSongIndex in queueWindows.indices) {
                    val distance = abs(lazyListState.firstVisibleItemIndex - currentSongIndex)
                    if (distance > 24) {
                        lazyListState.scrollToItem(currentSongIndex)
                    } else {
                        lazyListState.animateScrollToItem(currentSongIndex)
                    }
                }
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = LocalStableSystemBarsTopPadding.current),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onQueueExpandedChange(false) }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.spatialflow_ic_keyboard_arrow_down),
                                    contentDescription = "Collapse Queue",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))

                            Column {
                                Text(
                                    text = "Playing From",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                    letterSpacing = 0.5.sp,
                                )
                                Text(
                                    text = "QUEUE",
                                    style = MaterialTheme.typography.titleSmallEmphasized,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                    letterSpacing = 0.5.sp,
                                )
                            }
                        }

                        Text(
                            text = "${queueWindows.size} tracks",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                LazyColumn(
                    state = lazyListState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    itemsIndexed(
                        items = mutableQueueWindows,
                        key = { _, window -> window.sfQueueItemKey },
                        contentType = { _, _ -> "queue-song" },
                    ) { index, window ->
                        val song = window.mediaItem?.metadata as? MediaMetadata ?: return@itemsIndexed
                        val isPlaying = window.uid == currentPlayingUid
                        val shapes =
                            ListItemDefaults.segmentedShapes(index = index, count = mutableQueueWindows.size)

                        ReorderableItem(
                            state = reorderableState,
                            key = window.sfQueueItemKey,
                        ) {
                            val dragHandleModifier =
                                if (isQueueExpanded) {
                                    Modifier.draggableHandle(
                                        onDragStarted = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                    )
                                } else {
                                    Modifier
                                }
                            SpatialFlowQueueListItem(
                                song = song,
                                isPlaying = isPlaying,
                                shapes = shapes,
                                showReorderControls = true,
                                dragHandleModifier = dragHandleModifier,
                                onMoveUp = {
                                    if (index > 0) {
                                        onReorderQueue(index, index - 1)
                                    }
                                },
                                onMoveDown = {
                                    if (index < mutableQueueWindows.size - 1) {
                                        onReorderQueue(index, index + 1)
                                    }
                                },
                                onClick = {
                                    onPlaySongAtIndex(index)
                                },
                            )
                        }
                    }
                }
            }

            Surface(
                modifier =
                    with(boxScope) {
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                    },
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = queueTrayBackgroundColor,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 20.dp, bottom = 16.dp)
                            .navigationBarsPadding(),
                    contentAlignment = Alignment.Center,
                ) {
                    ButtonGroup(
                        modifier = Modifier.fillMaxWidth(),
                        expandedRatio = 0.3f,
                        overflowIndicator = {},
                    ) {
                        val scope = this

                        customItem(
                            buttonGroupContent = {
                                val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val cornerRadius by animateDpAsState(
                                    targetValue = if (isPressed) 12.dp else 28.dp,
                                    animationSpec =
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium,
                                        ),
                                    label = "ShuffleCorner",
                                )
                                Button(
                                    onClick = {
                                        onToggleShuffle()
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    modifier =
                                        with(scope) {
                                            Modifier
                                                .animateWidth(interactionSource)
                                                .weight(1f)
                                                .height(56.dp)
                                        },
                                    interactionSource = interactionSource,
                                    shape = RoundedCornerShape(cornerRadius),
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor = if (isShuffleEnabled) queueTrayActiveButtonColor else queueTrayInactiveButtonColor,
                                            contentColor = if (isShuffleEnabled) queueTrayActiveContentColor else queueTrayInactiveContentColor,
                                        ),
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.spatialflow_ic_shuffle),
                                        contentDescription = "Shuffle",
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            },
                            menuContent = {},
                        )

                        customItem(
                            buttonGroupContent = {
                                val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val cornerRadius by animateDpAsState(
                                    targetValue = if (isPressed) 12.dp else 28.dp,
                                    animationSpec =
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium,
                                        ),
                                    label = "LoopCorner",
                                )
                                val loopIcon =
                                    if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) {
                                        R.drawable.spatialflow_ic_repeat_one
                                    } else {
                                        R.drawable.spatialflow_ic_repeat
                                    }
                                val loopActive = repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF
                                Button(
                                    onClick = {
                                        onToggleLoopMode()
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    modifier =
                                        with(scope) {
                                            Modifier
                                                .animateWidth(interactionSource)
                                                .weight(1f)
                                                .height(56.dp)
                                        },
                                    interactionSource = interactionSource,
                                    shape = RoundedCornerShape(cornerRadius),
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor = if (loopActive) queueTrayActiveButtonColor else queueTrayInactiveButtonColor,
                                            contentColor = if (loopActive) queueTrayActiveContentColor else queueTrayInactiveContentColor,
                                        ),
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(id = loopIcon),
                                        contentDescription = "Repeat Mode",
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            },
                            menuContent = {},
                        )

                        customItem(
                            buttonGroupContent = {
                                val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val cornerRadius by animateDpAsState(
                                    targetValue = if (isPressed) 12.dp else 28.dp,
                                    animationSpec =
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium,
                                        ),
                                    label = "TimerCorner",
                                )
                                val timerActive = sleepTimerMode != SpatialFlowSleepTimerMode.OFF
                                Button(
                                    onClick = {
                                        onShowSleepTimerDialog()
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    modifier =
                                        with(scope) {
                                            Modifier
                                                .animateWidth(interactionSource)
                                                .weight(1f)
                                                .height(56.dp)
                                        },
                                    interactionSource = interactionSource,
                                    shape = RoundedCornerShape(cornerRadius),
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor = if (timerActive) queueTrayActiveButtonColor else queueTrayInactiveButtonColor,
                                            contentColor = if (timerActive) queueTrayActiveContentColor else queueTrayInactiveContentColor,
                                        ),
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.spatialflow_ic_timer),
                                        contentDescription = "Sleep Timer",
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            },
                            menuContent = {},
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpatialFlowQueueListItem(
    song: MediaMetadata,
    isPlaying: Boolean,
    shapes: ListItemShapes,
    showReorderControls: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    @SuppressLint("ModifierParameter")
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        leadingContent = {
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = song.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                )
                if (isPlaying) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.spatialflow_ic_play),
                            contentDescription = "Playing",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        },

        content = {
            Text(
                text = song.title,
                fontWeight = if (isPlaying) FontWeight.ExtraBold else FontWeight.SemiBold,
                fontSize = 16.sp,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = song.artists.joinToString { it.name },
                fontSize = 13.sp,
                color = if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            if (showReorderControls) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = "Options",
                            tint = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Move Up") },
                            onClick = {
                                showMenu = false
                                onMoveUp()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Move Down") },
                            onClick = {
                                showMenu = false
                                onMoveDown()
                            },
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Icon(
                        painter = painterResource(R.drawable.drag_handle),
                        contentDescription = "Drag to reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier =
                            dragHandleModifier
                                .size(32.dp)
                                .padding(4.dp),
                    )
                }
            }
        },
        shapes = shapes,
        colors =
            ListItemDefaults.colors(
                containerColor =
                    if (isPlaying) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    } else {
                        Color.Transparent
                    },
            ),
    )
}

@Immutable
private data class SfQueueDragInfo(
    val draggedItemUid: Any,
    val destination: SfQueueDragDestination,
)

@Immutable
private sealed interface SfQueueDragDestination {
    data object Start : SfQueueDragDestination

    data class After(
        val itemUid: Any,
    ) : SfQueueDragDestination
}

private fun SfQueueDragDestination.resolveIndex(
    queueWindows: List<Timeline.Window>,
    sourceIndex: Int,
): Int? =
    when (this) {
        SfQueueDragDestination.Start -> if (queueWindows.isEmpty()) null else 0
        is SfQueueDragDestination.After -> {
            val anchorIndex = queueWindows.indexOfFirst { it.uid == itemUid }
            when {
                sourceIndex !in queueWindows.indices -> null
                anchorIndex == -1 -> null
                sourceIndex < anchorIndex -> anchorIndex
                else -> (anchorIndex + 1).coerceAtMost(queueWindows.lastIndex)
            }
        }
    }

private val Timeline.Window.sfQueueItemKey: Long
    get() =
        (uid.hashCode().toLong() shl Int.SIZE_BITS) xor
            (mediaItem.mediaId.hashCode().toLong() and UInt.MAX_VALUE.toLong())
