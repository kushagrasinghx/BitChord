/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player.spatialflow

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import moe.rukamori.archivetune.LocalStableSystemBarsTopPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clipToBounds
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.graphics.Bitmap
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size as CoilSize
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.utils.ImageBlurUtils
import moe.rukamori.archivetune.constants.AutoTranslateExcludedLanguagesKey
import moe.rukamori.archivetune.constants.AutoTranslateLyricsKey
import moe.rukamori.archivetune.constants.LyricsMode
import moe.rukamori.archivetune.constants.LyricsModeKey
import moe.rukamori.archivetune.constants.TranslatorTargetLangKey
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import moe.rukamori.archivetune.lyrics.AiLyricsRomanization
import moe.rukamori.archivetune.lyrics.LyricsEntry
import moe.rukamori.archivetune.lyrics.LyricsUtils
import moe.rukamori.archivetune.lyrics.WordTimestamp
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.component.PlatformBackdrop
import moe.rukamori.archivetune.ui.component.LyricsEnhanced
import moe.rukamori.archivetune.ui.component.rememberLiquidGlassEnabled
import moe.rukamori.archivetune.ui.component.layerBackdrop
import moe.rukamori.archivetune.ui.component.rememberBackdrop
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.ui.menu.AnchoredLyricsOverflowMenu
import moe.rukamori.archivetune.ui.player.blurBackdropFootprint
import moe.rukamori.archivetune.ui.player.movingBlurWanderMaxDriftDp
import moe.rukamori.archivetune.ui.player.rememberBlurWanderDrift
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LyricsMenuViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private fun Modifier.circularRevealFrom(
    progressProvider: () -> Float,
    centerProvider: () -> Offset?,
): Modifier =
    this.drawWithCachePathClip(progressProvider, centerProvider)

private fun Modifier.drawWithCachePathClip(
    progressProvider: () -> Float,
    centerProvider: () -> Offset?,
): Modifier =
    this.drawWithCache {
            val revealPath = Path()
            var lastCenter: Offset? = null
            var lastRadius = -1f
            onDrawWithContent {
                val progress = progressProvider()
                if (progress >= 1f) {
                    drawContent()
                    return@onDrawWithContent
                }
                if (progress <= 0f) {
                    return@onDrawWithContent
                }
                val revealCenter = centerProvider() ?: Offset(size.width / 2f, size.height / 3f)
                if (revealCenter != lastCenter || lastRadius == -1f) {
                    lastCenter = revealCenter
                    lastRadius =
                        maxOf(
                            kotlin.math.hypot(revealCenter.x.toDouble(), revealCenter.y.toDouble()).toFloat(),
                            kotlin.math.hypot((size.width - revealCenter.x).toDouble(), revealCenter.y.toDouble()).toFloat(),
                            kotlin.math.hypot(revealCenter.x.toDouble(), (size.height - revealCenter.y).toDouble()).toFloat(),
                            kotlin.math.hypot((size.width - revealCenter.x).toDouble(), (size.height - revealCenter.y).toDouble()).toFloat(),
                        )
                }
                val radius = lastRadius * progress
                revealPath.reset()
                revealPath.addOval(
                    androidx.compose.ui.geometry.Rect(
                        left = revealCenter.x - radius,
                        top = revealCenter.y - radius,
                        right = revealCenter.x + radius,
                        bottom = revealCenter.y + radius,
                    ),
                )

                clipPath(revealPath, ClipOp.Intersect) {
                    this@onDrawWithContent.drawContent()
                }
            }
        }

@Composable
internal fun SpatialFlowLyricsOverlay(
    currentSong: MediaMetadata,
    syncedLyrics: List<LyricsEntry>?,
    plainLyrics: String?,
    lyricsProvider: String?,
    currentPositionProvider: () -> Long,
    contentReady: Boolean,
    backgroundBrush: Brush,
    artUrl: String? = null,
    revealProgressProvider: () -> Float,
    revealCenterProvider: () -> Offset?,
    contentColor: Color,
    contentSecondary: Color,
    onSeekTo: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val currentLyricsEntity by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)

    val lyricsMode by rememberEnumPreference(LyricsModeKey, defaultValue = LyricsMode.ENHANCED)

    var showLyricsMenu by remember { mutableStateOf(false) }
    var moreIconBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }

    val popupBackdrop: PlatformBackdrop? =
        if (rememberLiquidGlassEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rememberBackdrop(Color.Transparent)
        } else {
            null
        }

    val (autoTranslateLyrics) = rememberPreference(AutoTranslateLyricsKey, defaultValue = false)
    val (translatorTargetLang) = rememberPreference(TranslatorTargetLangKey, defaultValue = "")
    val (autoTranslateExcludedLanguages) =
        rememberPreference(AutoTranslateExcludedLanguagesKey, defaultValue = emptySet())
    val lyricsMenuViewModel: LyricsMenuViewModel = hiltViewModel()
    val translationDismissedMediaIds by lyricsMenuViewModel.translationDismissedMediaIds
        .collectAsStateWithLifecycle()
    LaunchedEffect(
        currentSong.id,
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
        if (currentSong.id in translationDismissedMediaIds) return@LaunchedEffect
        if (!LyricsUtils.shouldAutoTranslate(
                lyrics = text,
                targetLanguage = translatorTargetLang,
                excludedLanguageCodes = autoTranslateExcludedLanguages,
            )
        ) {
            return@LaunchedEffect
        }
        lyricsMenuViewModel.translateLyricsWithAi(
            mediaMetadata = currentSong,
            lyrics = text,
            targetLanguage = translatorTargetLang,
        )
    }

    val aiRomanizationSettings = AiLyricsRomanization.rememberSettings()
    val aiRomanizationSessionKey =
        remember(currentLyricsEntity?.lyrics) {
            AiLyricsRomanization.sessionKey(currentSong.id, currentLyricsEntity?.lyrics)
        }
    val aiRomanizationResult by AiLyricsRomanization.results.collectAsStateWithLifecycle()
    val romanizedLines: List<String?> =
        remember(
            aiRomanizationResult,
            aiRomanizationSessionKey,
            aiRomanizationSettings.active,
            aiRomanizationSettings.configKey,
            syncedLyrics,
        ) {
            if (!aiRomanizationSettings.active || syncedLyrics == null) {
                emptyList()
            } else {
                AiLyricsRomanization.linesFor(
                    aiRomanizationSessionKey,
                    syncedLyrics.map { it.text },
                    aiRomanizationSettings,
                )
            }
        }
    LaunchedEffect(aiRomanizationSessionKey, syncedLyrics, aiRomanizationSettings) {
        if (!aiRomanizationSettings.active || !aiRomanizationSettings.auto) return@LaunchedEffect
        if (syncedLyrics.isNullOrEmpty()) return@LaunchedEffect
        AiLyricsRomanization.request(
            sessionKey = aiRomanizationSessionKey,
            lines = syncedLyrics.map { it.text },
            settings = aiRomanizationSettings,
        )
    }

    val consumeClicks = remember { MutableInteractionSource() }

    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
        }
    }

    Box(
        modifier =
            modifier
                .circularRevealFrom(
                    progressProvider = revealProgressProvider,
                    centerProvider = revealCenterProvider,
                ).background(backgroundBrush)
                .clickable(
                    interactionSource = consumeClicks,
                    indication = null,
                    onClick = {},
                ),
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize().let { base ->
                    if (popupBackdrop != null && showLyricsMenu) {
                        base.layerBackdrop(popupBackdrop)
                    } else {
                        base
                    }
                },
        ) {
        SpatialFlowLyricsMovingBlur(
            artUrl = artUrl,
            modifier = Modifier.matchParentSize(),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = LocalStableSystemBarsTopPadding.current)
                    .navigationBarsPadding()
                    .padding(vertical = 12.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { showLyricsMenu = true },
                    modifier =
                        Modifier.onGloballyPositioned { coords ->
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
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = "Lyrics menu",
                        tint = contentColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp),
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.basicMarqueeWithFadedEdges(edgeWidth = 8.dp),
                    ) {
                        Text(
                            text = "LYRICS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = contentColor.copy(alpha = 0.5f),
                            letterSpacing = 1.sp,
                        )
                        AnimatedVisibility(
                            visible = !syncedLyrics.isNullOrEmpty(),
                            enter =
                                fadeIn(
                                    animationSpec =
                                        androidx.compose.animation.core.spring(
                                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
                                        ),
                                ) + slideInHorizontally(initialOffsetX = { it / 2 }),
                            exit = fadeOut() + slideOutHorizontally(),
                        ) {
                            Text(
                                text = " • Synced Lyrics",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = contentColor.copy(alpha = 0.4f),
                                letterSpacing = 1.sp,
                            )
                        }
                    }
                    Text(
                        text = currentSong.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 1,
                        modifier = Modifier.basicMarqueeWithFadedEdges(edgeWidth = 8.dp),
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = "Close Lyrics",
                        tint = contentColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    !contentReady -> Unit

                    lyricsMode == LyricsMode.ENHANCED && !syncedLyrics.isNullOrEmpty() ->
                        LyricsEnhanced(
                            sliderPositionProvider = { null },
                            lyricsSyncOffset = 0,
                            modifier = Modifier.fillMaxSize(),
                            textColorOverride = contentColor,
                        )

                    !syncedLyrics.isNullOrEmpty() ->
                        SpatialFlowSyncedLyrics(
                            lyrics = syncedLyrics,
                            romanizedLines = romanizedLines,
                            currentPositionProvider = currentPositionProvider,
                            contentColor = contentColor,
                            onSeekTo = onSeekTo,
                            modifier = Modifier.fillMaxSize(),
                        )

                    !plainLyrics.isNullOrBlank() ->
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 28.dp),
                        ) {
                            Text(
                                text = plainLyrics,
                                style = MaterialTheme.typography.titleLarge,
                                color = contentColor.copy(alpha = 0.9f),
                            )
                            LyricsMetadataFooter(
                                currentSong = currentSong,
                                selectedProvider = lyricsProvider,
                                contentColor = contentColor,
                            )
                        }

                    else ->
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 32.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "No lyrics found",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = contentColor.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 64.dp, bottom = 12.dp),
                            )
                            Text(
                                text = "Lyrics for this song are not available yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                            )
                            LyricsMetadataFooter(
                                currentSong = currentSong,
                                selectedProvider = lyricsProvider,
                                contentColor = contentColor,
                            )
                        }
                }
            }
        }
        }

        if (showLyricsMenu) {
            AnchoredLyricsOverflowMenu(
                iconBoundsInRoot = moreIconBounds,
                lyricsProvider = { currentLyricsEntity },
                mediaMetadataProvider = { currentSong },
                lyricsSyncOffset = 0,
                onLyricsSyncOffsetChange = {},
                onDismiss = { showLyricsMenu = false },
                backdrop = popupBackdrop,
            )
        }
    }
}

@Composable
private fun SpatialFlowSyncedLyrics(
    lyrics: List<LyricsEntry>,
    romanizedLines: List<String?>,
    currentPositionProvider: () -> Long,
    contentColor: Color,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val dimColor = contentColor.copy(alpha = 0.35f)

    val isKaraokeMode =
        remember(lyrics) {
            lyrics.any { !it.isInstrumental && LyricsUtils.hasTrueWordSync(it) }
        }

    val displayItems =
        remember(lyrics, isKaraokeMode) {
            lyrics.mapIndexedNotNull { index, line ->
                if (isKaraokeMode && line.isInstrumental) null else index
            }
        }

    val activeIndex by remember(displayItems) {
        derivedStateOf {
            val position = currentPositionProvider()
            var index = -1
            for (i in displayItems.indices) {
                if (lyrics[displayItems[i]].time <= position) index = i else break
            }
            index
        }
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && !listState.isScrollInProgress) {
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = -200,
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding =
            androidx.compose.foundation.layout.PaddingValues(
                start = 32.dp,
                end = 32.dp,
                top = 48.dp,
                bottom = 96.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(
            items = displayItems,
            key = { displayIndex, lyricsIndex -> "$lyricsIndex-${lyrics[lyricsIndex].time}-$displayIndex" },
        ) { displayIndex, lyricsIndex ->
            val line = lyrics[lyricsIndex]
            val isActive = displayIndex == activeIndex
            if (line.isInstrumental) {
                SpatialFlowInterludeItem(
                    isActive = isActive,
                    currentPositionProvider = currentPositionProvider,
                    line = line,
                    nextLineStartMs = lyrics.getOrNull(lyricsIndex + 1)?.time ?: (line.time + 5000L),
                    accentColor = contentColor,
                )
            } else {
                SpatialFlowLyricLineItem(
                    line = line,
                    isActive = isActive,
                    currentPositionProvider = currentPositionProvider,
                    contentColor = contentColor,
                    romanizedText = romanizedLines.getOrNull(lyricsIndex)?.takeIf { it.isNotBlank() },
                    onClick = { onSeekTo(line.time) },
                )
            }
        }
    }
}

private data class WordCharSpan(
    val start: Int,
    val endExclusive: Int,
    val word: WordTimestamp,
)

private fun wordSpansFor(
    text: String,
    words: List<WordTimestamp>,
): List<WordCharSpan> {
    val spans = mutableListOf<WordCharSpan>()
    var cursor = 0
    for (word in words) {
        val core = word.text.trim()
        if (core.isEmpty()) continue
        val idx = text.indexOf(core, cursor)
        if (idx >= 0) {
            spans += WordCharSpan(idx, idx + core.length, word)
            cursor = idx + core.length
        }
    }
    return spans
}

@Composable
private fun SpatialFlowLyricLineItem(
    line: LyricsEntry,
    isActive: Boolean,
    currentPositionProvider: () -> Long,
    contentColor: Color,
    romanizedText: String? = null,
    onClick: () -> Unit,
) {
    val rawWords = line.words.orEmpty().filter { it.text.isNotBlank() }
    val spans = remember(line.text, rawWords) { wordSpansFor(line.text, rawWords) }
    val isKaraoke = LyricsUtils.hasTrueWordSync(line) && spans.isNotEmpty()

    val dimColor = contentColor.copy(alpha = 0.35f)
    val litColor = contentColor

    val rawPos = if (isKaraoke && isActive) currentPositionProvider() else line.time
    val smoothedPos by animateFloatAsState(
        targetValue = rawPos.toFloat(),
        animationSpec = tween(durationMillis = 200, easing = LinearEasing),
        label = "SmoothKaraokePos",
    )

    val mainTextStyle =
        MaterialTheme.typography.headlineMedium.copy(
            fontFamily = SpatialFlowGoogleSansFlexNonRounded,
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 50.sp,
        )
    val baseTextLayout = remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }

    Box(
        modifier =
            Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = line.text,
                    style = mainTextStyle,
                    color = if (isKaraoke || !isActive) dimColor else litColor,
                    textAlign = TextAlign.Center,
                    onTextLayout = { baseTextLayout.value = it },
                    maxLines = Int.MAX_VALUE,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (isKaraoke && isActive) {
                    Text(
                        text = line.text,
                        style = mainTextStyle,
                        color = litColor,
                        textAlign = TextAlign.Center,
                        maxLines = Int.MAX_VALUE,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithCache {
                                    val layout = baseTextLayout.value
                                    val textLength = layout?.layoutInput?.text?.length ?: 0
                                    val charPaths =
                                        if (layout != null && textLength > 0) {
                                            List(textLength) { charIndex ->
                                                layout.getPathForRange(charIndex, charIndex + 1)
                                            }
                                        } else {
                                            null
                                        }
                                    onDrawWithContent {
                                        drawContent()
                                        if (layout != null && charPaths != null) {
                                            eraseFutureText(layout, charPaths, spans, smoothedPos.toLong())
                                        }
                                    }
                                },
                    )
                }
            }

            romanizedText
                ?.takeIf { it.isNotBlank() && it != line.text }
                ?.let { romanized ->
                    Text(
                        text = romanized,
                        style =
                            MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = SpatialFlowGoogleSansFlexNonRounded,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                            ),
                        color = if (isActive) litColor.copy(alpha = 0.65f) else dimColor.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
        }
    }
}

private fun DrawScope.eraseFutureText(
    layout: androidx.compose.ui.text.TextLayoutResult,
    charPaths: List<androidx.compose.ui.graphics.Path>,
    spans: List<WordCharSpan>,
    pos: Long,
) {
    val textLength = charPaths.size
    for (charIndex in 0 until textLength) {
        val controllingSpan = findControllingSpan(charIndex, spans)
        val charProgress =
            if (controllingSpan != null) {
                calculateCharProgress(charIndex, controllingSpan, pos)
            } else {
                0f
            }

        if (charProgress >= 0.99f) {
        } else if (charProgress < 0.01f) {
            val path = charPaths[charIndex]
            drawPath(path, color = Color.Black, blendMode = BlendMode.DstOut)
        } else {
            val path = charPaths[charIndex]
            val box = layout.getBoundingBox(charIndex)

            val gradientWidth = box.width * 1.5f
            val sweepCenter = box.left + (box.width * charProgress)

            val brush =
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    0.0f to Color.Transparent,
                    1.0f to Color.Black,
                    startX = sweepCenter - (gradientWidth / 2f),
                    endX = sweepCenter + (gradientWidth / 2f),
                )

            drawPath(path, brush = brush, blendMode = BlendMode.DstOut)
        }
    }
}

private fun findControllingSpan(
    charIndex: Int,
    spans: List<WordCharSpan>,
): WordCharSpan? {
    if (spans.isEmpty()) return null

    val exactSpan = spans.find { charIndex >= it.start && charIndex < it.endExclusive }
    if (exactSpan != null) return exactSpan

    if (charIndex < spans.first().start) return spans.first()
    if (charIndex >= spans.last().endExclusive) return spans.last()

    return spans.lastOrNull { it.endExclusive <= charIndex } ?: spans.first()
}

private fun calculateCharProgress(
    charIndex: Int,
    span: WordCharSpan,
    pos: Long,
): Float {
    val wordStartMs = (span.word.startTime * 1000.0).toLong()
    val wordEndMs = (span.word.endTime * 1000.0).toLong().coerceAtLeast(wordStartMs + 120L)

    val wordProgress =
        when {
            pos < wordStartMs -> 0f
            pos >= wordEndMs -> 1f
            else -> {
                val duration = (wordEndMs - wordStartMs).toFloat().coerceAtLeast(1f)
                ((pos - wordStartMs).toFloat() / duration).coerceIn(0f, 1f)
            }
        }

    val easedWordProgress = easeOutCubic(wordProgress)

    val wStart = span.start
    val wEnd = span.endExclusive
    val wordLength = (wEnd - wStart).toFloat().coerceAtLeast(1f)

    val sweepWidth = 0.35f
    val sweepPosition = easedWordProgress * (1f + sweepWidth)

    val charOffsetInWord = (charIndex - wStart).toFloat()
    val charRelativePosition = charOffsetInWord / wordLength

    return when {
        sweepPosition < charRelativePosition -> 0f
        sweepPosition >= (charRelativePosition + sweepWidth) -> 1f
        else -> (sweepPosition - charRelativePosition) / sweepWidth
    }
}

private fun easeOutCubic(x: Float): Float = 1f - (1f - x) * (1f - x) * (1f - x)

@Composable
private fun SpatialFlowInterludeItem(
    isActive: Boolean,
    currentPositionProvider: () -> Long,
    line: LyricsEntry,
    nextLineStartMs: Long,
    accentColor: Color,
) {
    val duration = (nextLineStartMs - line.time).coerceAtLeast(1)
    val rawProgress =
        if (isActive) {
            ((currentPositionProvider() - line.time).toFloat() / duration).coerceIn(0f, 1f)
        } else {
            0f
        }
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "InterludeProgress",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "InterludeBreathing")
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.18f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "BreathScale",
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.85f else 0.25f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "InterludeAlpha",
    )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            painter = painterResource(id = R.drawable.spatialflow_ic_music_note),
            contentDescription = "Interlude",
            tint = accentColor.copy(alpha = iconAlpha),
            modifier =
                Modifier
                    .size(26.dp)
                    .graphicsLayer {
                        scaleX = if (isActive) breatheScale else 1f
                        scaleY = if (isActive) breatheScale else 1f
                    },
        )

        LinearWavyProgressIndicator(
            progress = { animatedProgress },
            modifier =
                Modifier
                    .weight(1f)
                    .height(11.dp),
            color = accentColor.copy(alpha = if (isActive) 0.75f else 0.18f),
            trackColor = accentColor.copy(alpha = 0.06f),
            amplitude = { p -> (0.6f + p) },
        )
    }
}

private const val SfLyricsBlurRestScale = 1.2f
private const val SfLyricsBlurDriftScale = 2.4f
private val SfLyricsBlurRadius = 64.dp

internal object SfLyricsBlurBitmapCache {
    private const val MAX_ENTRIES = 4

    private val cache = object : LinkedHashMap<String, Bitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean = size > MAX_ENTRIES
    }

    fun get(url: String): Bitmap? = synchronized(cache) { cache[url] }

    fun put(url: String, bitmap: Bitmap) {
        synchronized(cache) { cache[url] = bitmap }
    }
}

internal suspend fun loadSfLyricsBlurredBitmap(
    context: android.content.Context,
    artUrl: String,
): Bitmap? {
    SfLyricsBlurBitmapCache.get(artUrl)?.let { return it }
    val bitmap =
        withContext(Dispatchers.IO) {
            runCatching {
                val request =
                    ImageRequest
                        .Builder(context)
                        .data(artUrl)
                        .allowHardware(false)
                        .memoryCacheKey("$artUrl#sflyricsblur")
                        .diskCacheKey("$artUrl#sflyricsblur")
                        .size(CoilSize(720, 720))
                        .build()
                val result = context.imageLoader.execute(request)
                if (result is SuccessResult) {
                    val raw =
                        result.image
                            .toBitmap()
                            .copy(Bitmap.Config.ARGB_8888, true)
                    val density = context.resources.displayMetrics.density
                    ImageBlurUtils.blur(raw, SfLyricsBlurRadius.value * density)
                } else {
                    null
                }
            }.getOrNull()
        }
    if (bitmap != null) {
        SfLyricsBlurBitmapCache.put(artUrl, bitmap)
    }
    return bitmap
}

@Composable
private fun SpatialFlowLyricsMovingBlur(
    artUrl: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isPlaying by (LocalPlayerConnection.current?.isPlaying
        ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) })
        .collectAsStateWithLifecycle()
    val driftDpToPx = with(LocalDensity.current) { 1.dp.toPx() }

    val morph = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        morph.animateTo(1f, animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing))
    }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .clipToBounds(),
    ) {
        val wanderMaxDrift = movingBlurWanderMaxDriftDp(maxWidth, maxHeight)
        val blurWander = rememberBlurWanderDrift(active = isPlaying, maxDriftDp = wanderMaxDrift)
        val driftFootprint =
            remember(maxWidth, maxHeight) {
                blurBackdropFootprint(
                    width = maxWidth,
                    height = maxHeight,
                    restScale = SfLyricsBlurRestScale,
                    driftScale = SfLyricsBlurDriftScale,
                    maxDriftDp = wanderMaxDrift,
                )
            }

        if (artUrl != null) {
            var preBlurredBitmap by remember(artUrl) {
                mutableStateOf(SfLyricsBlurBitmapCache.get(artUrl))
            }
            LaunchedEffect(artUrl) {
                if (preBlurredBitmap == null) {
                    preBlurredBitmap = loadSfLyricsBlurredBitmap(context, artUrl)
                }
            }
            preBlurredBitmap?.let { bmp ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .requiredSize(driftFootprint)
                                .graphicsLayer {
                                    val scale =
                                        SfLyricsBlurRestScale +
                                            (SfLyricsBlurDriftScale - SfLyricsBlurRestScale) * morph.value
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = blurWander.xDp.floatValue * driftDpToPx * morph.value
                                    translationY = blurWander.yDp.floatValue * driftDpToPx * morph.value
                                },
                    )
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(SfCanvasScrimBrush),
        )
    }
}
