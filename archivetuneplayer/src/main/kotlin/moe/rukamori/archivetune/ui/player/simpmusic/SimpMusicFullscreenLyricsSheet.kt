/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player.simpmusic

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalStableSystemBarsTopPadding
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import moe.rukamori.archivetune.constants.AutoTranslateExcludedLanguagesKey
import moe.rukamori.archivetune.constants.AutoTranslateLyricsKey
import moe.rukamori.archivetune.constants.LyricsBackgroundStyle
import moe.rukamori.archivetune.constants.LyricsBackgroundStyleKey
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerBackgroundStyleKey
import moe.rukamori.archivetune.constants.TranslatorTargetLangKey
import moe.rukamori.archivetune.lyrics.LyricsUtils
import moe.rukamori.archivetune.ui.component.LyricsEnhanced
import moe.rukamori.archivetune.ui.player.StyledLyricsBackground
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.viewmodels.LyricsMenuViewModel
import moe.rukamori.archivetune.utils.rememberPreference
import androidx.hilt.navigation.compose.hiltViewModel
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.ui.utils.highRes
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.PlatformBackdrop
import moe.rukamori.archivetune.ui.component.rememberLiquidGlassEnabled
import moe.rukamori.archivetune.ui.component.layerBackdrop
import moe.rukamori.archivetune.ui.component.rememberBackdrop
import moe.rukamori.archivetune.ui.menu.AnchoredLyricsOverflowMenu
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import android.os.Build
import androidx.media3.common.Player
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private val LyricsGutter = 50.dp

private const val CONTROLS_AUTO_HIDE_MS = 4_000L

@Composable
internal fun SimpMusicFullscreenLyricsSheet(
    mediaMetadata: MediaMetadata,
    playerConnection: PlayerConnection,
    navController: NavController,
    bottomSheetPageState: BottomSheetPageState,
    color: Color,
    onDismiss: () -> Unit,
    paletteColors: List<Color> = emptyList(),
) {
    val context = LocalContext.current
    val view = LocalView.current
    val menuState = LocalMenuState.current

    var showAnchoredLyricsMenu by remember { mutableStateOf(false) }
    var moreIconBounds by remember {
        mutableStateOf(androidx.compose.ui.geometry.Rect.Zero)
    }

    val popupBackdrop: PlatformBackdrop? =
        if (rememberLiquidGlassEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rememberBackdrop(Color.Transparent)
        } else {
            null
        }

    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val liked = currentSong?.song?.liked == true
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsStateWithLifecycle()
    val canSkipNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()
    val shuffleEnabled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()
    val currentLyricsEntity by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)

    val (autoTranslateLyrics) = rememberPreference(AutoTranslateLyricsKey, defaultValue = false)
    val (translatorTargetLang) = rememberPreference(TranslatorTargetLangKey, defaultValue = "")

    val (autoTranslateExcludedLanguages) =
        rememberPreference(AutoTranslateExcludedLanguagesKey, defaultValue = emptySet())
    val lyricsMenuViewModel: LyricsMenuViewModel = hiltViewModel()
    val translationDismissedMediaIds by lyricsMenuViewModel.translationDismissedMediaIds
        .collectAsStateWithLifecycle()
    LaunchedEffect(
        mediaMetadata.id,
        currentLyricsEntity?.lyrics,
        currentLyricsEntity?.source,
        autoTranslateLyrics,
        translatorTargetLang,
        autoTranslateExcludedLanguages,
        translationDismissedMediaIds,
    ) {
        if (!autoTranslateLyrics) return@LaunchedEffect
        val snapshot = currentLyricsEntity ?: return@LaunchedEffect
        val text = snapshot.lyrics ?: return@LaunchedEffect
        if (text.isBlank() || text == LYRICS_NOT_FOUND) return@LaunchedEffect

        if (snapshot.source == LyricsEntity.Source.AI_TRANSLATION.value &&
            LyricsUtils.hasTranslation(text)
        ) return@LaunchedEffect

        if (mediaMetadata.id in translationDismissedMediaIds) return@LaunchedEffect
        if (!LyricsUtils.shouldAutoTranslate(
                lyrics = text,
                targetLanguage = translatorTargetLang,
                excludedLanguageCodes = autoTranslateExcludedLanguages,
            )
        ) {
            return@LaunchedEffect
        }
        lyricsMenuViewModel.translateLyricsWithAi(
            mediaMetadata = mediaMetadata,
            lyrics = text,
            targetLanguage = translatorTargetLang,
        )
    }

    val hasLyrics = currentLyricsEntity?.lyrics
        ?.let { it.isNotBlank() && it != LYRICS_NOT_FOUND } == true

    DisposableEffect(view, hasLyrics) {
        if (hasLyrics) view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var showControlButtons by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(showControlButtons) {
        if (showControlButtons) {
            delay(CONTROLS_AUTO_HIDE_MS)
            showControlButtons = false
        }
    }

    var sliderPosition by remember { mutableLongStateOf(-1L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(mediaMetadata.id, isPlaying) {
        while (isActive) {
            val d = playerConnection.player.duration
            if (d > 0) duration = d
            if (!isScrubbing) {
                sliderPosition = playerConnection.player.currentPosition.coerceAtLeast(0L)
            }
            delay(200L)
        }
    }

    val startColor by animateColorAsState(color, tween(1200, easing = FastOutSlowInEasing))
    val midColor1 by animateColorAsState(color.copy(alpha = 0.95f), tween(1200, easing = FastOutSlowInEasing))
    val midColor2 by animateColorAsState(color.copy(alpha = 0.85f), tween(1200, easing = FastOutSlowInEasing))
    val endColor by animateColorAsState(Color.Black, tween(1200, easing = FastOutSlowInEasing))

    val lyricsBackgroundStylePref by rememberEnumPreference(LyricsBackgroundStyleKey, LyricsBackgroundStyle.DEFAULT)
    val playerBackgroundStylePref by rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT)
    val resolvedLyricsBackground = lyricsBackgroundStylePref.resolveFor(playerBackgroundStylePref)

    var queueOpen by rememberSaveable { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val shareLyrics: () -> Unit = {
        val text = currentLyricsEntity?.lyrics
        if (!text.isNullOrBlank()) {
            val body = text.lineSequence().joinToString("\n") { it.substringAfter("]") }
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, body)
                    },
                    null,
                ),
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            menuState.dismiss()
            bottomSheetPageState.dismiss()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = Color.Black,
        contentColor = Color.Transparent,
        dragHandle = null,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        shape = RectangleShape,
        modifier =
            Modifier
                .fillMaxHeight()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    showControlButtons = true
                },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier.fillMaxSize().let { base ->
                        if (popupBackdrop != null && showAnchoredLyricsMenu) {
                            base.layerBackdrop(popupBackdrop)
                        } else {
                            base
                        }
                    },
            ) {
            if (resolvedLyricsBackground != LyricsBackgroundStyle.DEFAULT) {
                StyledLyricsBackground(
                    style = resolvedLyricsBackground,
                    mediaMetadata = mediaMetadata,
                    gradientColors = paletteColors,
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors =
                                        listOf(
                                            startColor,
                                            midColor1,
                                            midColor2,
                                            endColor.copy(alpha = 0.9f),
                                            endColor,
                                        ),
                                    start =
                                        Offset(
                                            x = 0f,
                                            y = 0f,
                                        ),
                                    end =
                                        Offset(
                                            x = 2500f,
                                            y = 2500f,
                                        ),
                                ),
                            ),
                )
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()

                        .padding(
                            top = LocalStableSystemBarsTopPadding.current,
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                        ),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 36.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = mediaMetadata.thumbnailUrl?.highRes(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier

                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp)),
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = mediaMetadata.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            modifier =
                                Modifier.basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    animationMode = MarqueeAnimationMode.Immediately,
                                ),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier.clickable {
                                    mediaMetadata.artists
                                        .firstOrNull { !it.id.isNullOrBlank() }
                                        ?.id?.let { id -> navController.navigate("artist/$id") }
                                },
                        ) {
                            Text(
                                text = mediaMetadata.artists.joinToString { it.name },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier =
                                    Modifier.basicMarquee(
                                        iterations = Int.MAX_VALUE,
                                        animationMode = MarqueeAnimationMode.Immediately,
                                    ),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    IconButton(onClick = playerConnection::toggleLike, modifier = Modifier.size(28.dp)) {
                        Icon(
                            painter =
                                painterResource(
                                    if (liked) R.drawable.simpmusic_favorite else R.drawable.simpmusic_favorite_border,
                                ),
                            contentDescription = stringResource(R.string.action_like),
                            tint = if (liked) MaterialTheme.colorScheme.error else Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    if (hasLyrics) {
                        IconButton(onClick = shareLyrics) {
                            Icon(
                                painter = painterResource(R.drawable.simpmusic_share),
                                contentDescription = stringResource(R.string.share),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    IconButton(
                        onClick = { showAnchoredLyricsMenu = true },
                        modifier =
                            Modifier
                                .onGloballyPositioned { coords ->

                                    val pos = coords.positionInRoot()
                                    val sz = coords.size
                                    moreIconBounds =
                                        androidx.compose.ui.geometry.Rect(
                                            offset = pos,
                                            size =
                                                androidx.compose.ui.geometry.Size(
                                                    width = sz.width.toFloat(),
                                                    height = sz.height.toFloat(),
                                                ),
                                        )
                                },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.simpmusic_more_vert),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = LyricsGutter),
                ) {
                    if (hasLyrics) {
                        LyricsEnhanced(
                            sliderPositionProvider = { if (isScrubbing) sliderPosition else null },
                            lyricsSyncOffset = 0,
                            modifier = Modifier.fillMaxSize(),
                            textColorOverride = Color.White,
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.save_canvas_variant_unavailable),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp)) {
                    val safeDuration = if (duration > 0) duration else 1L
                    val shown = sliderPosition.coerceIn(0L, safeDuration)
                    Slider(
                        value = shown.toFloat() / safeDuration.toFloat(),
                        onValueChange = {
                            isScrubbing = true
                            sliderPosition = (it * safeDuration).toLong()
                        },
                        onValueChangeFinished = {
                            playerConnection.player.seekTo(sliderPosition)
                            isScrubbing = false
                        },
                        track = { sliderState ->
                            SliderDefaults.Track(
                                modifier = Modifier.height(5.dp),
                                enabled = true,
                                sliderState = sliderState,
                                colors =
                                    SliderDefaults.colors().copy(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.White,
                                        inactiveTrackColor = Color.Transparent,
                                    ),
                                thumbTrackGapSize = 0.dp,
                                drawTick = { _, _ -> },
                                drawStopIndicator = null,
                            )
                        },
                        thumb = {
                            SliderDefaults.Thumb(
                                modifier = Modifier.height(18.dp).width(8.dp).padding(vertical = 4.dp),
                                thumbSize = DpSize(8.dp, 8.dp),
                                interactionSource = remember { MutableInteractionSource() },
                                colors =
                                    SliderDefaults.colors().copy(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.White,
                                    ),
                                enabled = true,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = clockTime(shown),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Left,
                        )
                        Text(
                            text = if (duration > 0) clockTime(duration) else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Right,
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                }

                AnimatedVisibility(
                    visible = showControlButtons,
                    enter = expandVertically(tween(300)),
                    exit = shrinkVertically(tween(300)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(96.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LyricsTransportIcon(
                            painter = painterResource(R.drawable.simpmusic_shuffle),
                            contentDescription = stringResource(R.string.shuffle),
                            tint = if (shuffleEnabled) MaterialTheme.colorScheme.error else Color.White,
                            iconSize = 32.dp,
                            onClick = { playerConnection.player.shuffleModeEnabled = !shuffleEnabled },
                        )
                        LyricsTransportIcon(
                            painter = painterResource(R.drawable.simpmusic_skip_previous),
                            contentDescription = stringResource(R.string.widget_previous),
                            tint = Color.White.copy(alpha = if (canSkipPrevious) 1f else 0.4f),
                            enabled = canSkipPrevious,
                            iconSize = 42.dp,
                            onClick = playerConnection::seekToPrevious,
                        )
                        LyricsTransportIcon(
                            painter =
                                painterResource(
                                    if (isPlaying) R.drawable.simpmusic_pause_circle else R.drawable.simpmusic_play_circle,
                                ),
                            contentDescription = stringResource(if (isPlaying) R.string.widget_pause else R.string.play),
                            tint = Color.White,
                            iconSize = 72.dp,
                            onClick = { playerConnection.player.togglePlayPause() },
                        )
                        LyricsTransportIcon(
                            painter = painterResource(R.drawable.simpmusic_skip_next),
                            contentDescription = stringResource(R.string.next),
                            tint = Color.White.copy(alpha = if (canSkipNext) 1f else 0.4f),
                            enabled = canSkipNext,
                            iconSize = 42.dp,
                            onClick = playerConnection::seekToNext,
                        )
                        LyricsTransportIcon(
                            painter =
                                painterResource(
                                    if (repeatMode == Player.REPEAT_MODE_ONE) {
                                        R.drawable.simpmusic_repeat_one
                                    } else {
                                        R.drawable.simpmusic_repeat
                                    },
                                ),
                            contentDescription =
                                stringResource(
                                    when (repeatMode) {
                                        Player.REPEAT_MODE_ONE -> R.string.repeat_mode_one
                                        Player.REPEAT_MODE_ALL -> R.string.repeat_mode_all
                                        else -> R.string.repeat_mode_off
                                    },
                                ),
                            tint = if (repeatMode == Player.REPEAT_MODE_OFF) Color.White else MaterialTheme.colorScheme.error,
                            iconSize = 32.dp,
                            onClick = {
                                playerConnection.player.repeatMode =
                                    when (repeatMode) {
                                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                        else -> Player.REPEAT_MODE_OFF
                                    }
                            },
                        )
                    }
                }
                AnimatedVisibility(
                    visible = showControlButtons,
                    enter = expandVertically(tween(300)),
                    exit = shrinkVertically(tween(300)),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .height(32.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 40.dp),
                    ) {
                        IconButton(
                            modifier = Modifier.size(24.dp).align(Alignment.CenterStart),
                            onClick = { bottomSheetPageState.show { ShowMediaInfo(mediaMetadata.id) } },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.simpmusic_info),
                                tint = Color.White,
                                contentDescription = stringResource(R.string.details),
                            )
                        }
                        IconButton(
                            modifier = Modifier.size(24.dp).align(Alignment.CenterEnd),
                            onClick = { queueOpen = true },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.simpmusic_queue_music),
                                tint = Color.White,
                                contentDescription = stringResource(R.string.queue),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
                if (!showControlButtons) {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            }

            if (showAnchoredLyricsMenu) {
                AnchoredLyricsOverflowMenu(
                    iconBoundsInRoot = moreIconBounds,
                    lyricsProvider = { currentLyricsEntity },
                    mediaMetadataProvider = { mediaMetadata },
                    lyricsSyncOffset = 0,
                    onLyricsSyncOffsetChange = {},
                    onDismiss = { showAnchoredLyricsMenu = false },
                    backdrop = popupBackdrop,
                )
            }
        }
    }

    if (queueOpen) {
        SimpMusicQueueSheet(
            playerConnection = playerConnection,
            navController = navController,
            onDismiss = { queueOpen = false },
        )
    }
}

@Composable
private fun RowScope.LyricsTransportIcon(
    painter: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String,
    tint: Color,
    iconSize: androidx.compose.ui.unit.Dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

private fun clockTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0L)
    return String.format(Locale.getDefault(), "%02d:%02d", total / 60, total % 60)
}
