/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.player.LocalLyricsScrollListener
import moe.rukamori.archivetune.ui.player.LocalPlayerSheetVisible
import moe.rukamori.archivetune.constants.LyricsClickKey
import moe.rukamori.archivetune.constants.LyricsLineBlurKey
import moe.rukamori.archivetune.constants.LyricsLineSpacingKey
import moe.rukamori.archivetune.constants.LyricsRomanizeChineseKey
import moe.rukamori.archivetune.constants.LyricsRomanizeHindiKey
import moe.rukamori.archivetune.constants.LyricsRomanizeJapaneseKey
import moe.rukamori.archivetune.constants.LyricsRomanizeKoreanKey
import moe.rukamori.archivetune.constants.LyricsRomanizeOtherLanguagesKey
import moe.rukamori.archivetune.constants.LyricsScrollKey
import moe.rukamori.archivetune.constants.LyricsTextSizeKey
import moe.rukamori.archivetune.constants.LyricsV2BounceFactorKey
import moe.rukamori.archivetune.constants.LyricsV2FillTransitionWidthKey
import moe.rukamori.archivetune.constants.LyricsV2GlowFactorKey
import moe.rukamori.archivetune.constants.LyricsV2LrcBounceEnabledKey
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerBackgroundStyleKey
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import moe.rukamori.archivetune.lyrics.AiLyricsRomanization
import moe.rukamori.archivetune.lyrics.LyricsEntry
import moe.rukamori.archivetune.lyrics.LyricsRomanizationPreferences
import moe.rukamori.archivetune.lyrics.LyricsUtils.findCurrentLineIndex
import moe.rukamori.archivetune.lyrics.LyricsUtils.hasTrueWordSync
import moe.rukamori.archivetune.lyrics.LyricsUtils.insertInstrumentalBreaks
import moe.rukamori.archivetune.lyrics.LyricsUtils.isLineSyncedLrc
import moe.rukamori.archivetune.lyrics.LyricsUtils.isTtml
import moe.rukamori.archivetune.lyrics.LyricsUtils.parseLyrics
import moe.rukamori.archivetune.lyrics.LyricsUtils.parseTtml
import moe.rukamori.archivetune.lyrics.LyricsUtils.providedRomanizedTextForEntry
import moe.rukamori.archivetune.lyrics.LyricsUtils.providedTranslationTextForEntry
import moe.rukamori.archivetune.lyrics.LyricsUtils.romanizeLyricsLine
import moe.rukamori.archivetune.lyrics.LyricsUtils.shouldRomanizeLyricsLine
import moe.rukamori.archivetune.lyrics.WordTimestamp
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.component.shimmer.TextPlaceholder
import moe.rukamori.archivetune.ui.theme.rememberArchiveTuneLyricsFontFamily
import moe.rukamori.archivetune.ui.utils.smoothFadingEdge
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.reportException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private const val LRC_LEAD_MS = 300L

private const val TTML_LEAD_MS = 0L

private const val LYRIC_VISUAL_TUNING_OFFSET_MS = 150L

private const val MIN_SWEEP_MS = 180L

private const val V2_POSITION_RESET_BACKWARD_THRESHOLD_MS = 1_000L

private const val MANUAL_SCROLL_TIMEOUT_MS = 3000L
private const val V2_FIRST_FOCUS_FADE_MS = 200
private const val V2_FIRST_FOCUS_TIMEOUT_MS = 400L

private val HEAD_LYRICS_ENTRY = LyricsEntry(time = 0L, text = "")

private class WordSyncCache {
    private val cache = HashMap<LyricsEntry, Boolean>()

    fun getOrCompute(entry: LyricsEntry): Boolean =
        cache.getOrPut(entry) { hasTrueWordSync(entry) }
}

@Composable
private fun rememberWordSyncCache(): WordSyncCache = remember { WordSyncCache() }

private fun isRtlText(text: String): Boolean {
    for (ch in text) {
        when (Character.getDirectionality(ch)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE,
            -> return true

            Character.DIRECTIONALITY_LEFT_TO_RIGHT,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE,
            -> return false
        }
    }
    return false
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LyricsV2(
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    modifier: Modifier = Modifier,
    textColorOverride: Color? = null,
    lyricsLineBlurOverride: Boolean? = null,
    spotifyStyle: Boolean = false,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val (lyricsClick) = rememberPreference(LyricsClickKey, defaultValue = true)
    val (lyricsScroll) = rememberPreference(LyricsScrollKey, defaultValue = true)
    val (lyricsTextSize) = rememberPreference(LyricsTextSizeKey, defaultValue = 26f)
    val (lyricsLineSpacing) = rememberPreference(LyricsLineSpacingKey, defaultValue = 1.3f)
    val (lyricsLineBlurPreference) = rememberPreference(LyricsLineBlurKey, defaultValue = false)
    val (bounceFactorPreference) = rememberPreference(LyricsV2BounceFactorKey, defaultValue = 1f)
    val (glowFactorPreference) = rememberPreference(LyricsV2GlowFactorKey, defaultValue = 1f)
    val (fillTransitionWidth) = rememberPreference(LyricsV2FillTransitionWidthKey, defaultValue = 8f)
    val (lrcBounceEnabledPreference) = rememberPreference(LyricsV2LrcBounceEnabledKey, defaultValue = true)
    val v2AnimationsDisabled = LocalAnimationsDisabled.current
    val bounceFactor = if (v2AnimationsDisabled) 0f else bounceFactorPreference
    val glowFactor = if (v2AnimationsDisabled) 0f else glowFactorPreference
    val lrcBounceEnabled = lrcBounceEnabledPreference && !v2AnimationsDisabled
    val (romanizeChinese) = rememberPreference(LyricsRomanizeChineseKey, defaultValue = true)
    val (romanizeHindi) = rememberPreference(LyricsRomanizeHindiKey, defaultValue = true)
    val (romanizeJapanese) = rememberPreference(LyricsRomanizeJapaneseKey, defaultValue = true)
    val (romanizeKorean) = rememberPreference(LyricsRomanizeKoreanKey, defaultValue = true)
    val (romanizeOtherLanguages) = rememberPreference(LyricsRomanizeOtherLanguagesKey, defaultValue = true)
    val aiRomanizationSettings = AiLyricsRomanization.rememberSettings()
    val romanizationPreferences =
        remember(
            romanizeJapanese,
            romanizeKorean,
            romanizeChinese,
            romanizeHindi,
            romanizeOtherLanguages,
            aiRomanizationSettings.active,
        ) {
            LyricsRomanizationPreferences(
                romanizeJapanese = romanizeJapanese,
                romanizeKorean = romanizeKorean,
                romanizeChinese = romanizeChinese,
                romanizeHindi = romanizeHindi,
                romanizeOther = romanizeOtherLanguages,
                aiHandled = aiRomanizationSettings.active,
            )
        }
    val lyricsFontFamily = rememberArchiveTuneLyricsFontFamily()
    val playerBackground by rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT)

    val textColor = textColorOverride ?: Color.White
    val lyricsLineBlur = (lyricsLineBlurOverride ?: lyricsLineBlurPreference) && !v2AnimationsDisabled

    val inactiveAlpha = 0.35f

    var isSelectionModeActive by rememberSaveable { mutableStateOf(false) }
    val selectedIndices = remember { mutableStateListOf<Int>() }
    var showMaxSelectionToast by remember { mutableStateOf(false) }
    val maxSelectionLimit = 7
    var showShareDialog by remember { mutableStateOf(false) }
    var shareDialogData by remember { mutableStateOf<LyricsSharePayload?>(null) }
    var showShareImageDialog by remember { mutableStateOf(false) }

    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val lyrics = currentLyrics?.lyrics
    val showTranslations =
        remember(currentLyrics?.source) {
            currentLyrics?.source == LyricsEntity.Source.AI_TRANSLATION.value
        }

    val isSynced = remember(lyrics) { lyrics != null && (isLineSyncedLrc(lyrics!!) || isTtml(lyrics!!)) }
    val isTtmlFormat = remember(lyrics) { lyrics != null && isTtml(lyrics!!) }

    var parsedEntries by remember(lyrics) { mutableStateOf<List<LyricsEntry>?>(null) }
    LaunchedEffect(lyrics) {
        val text = lyrics
        if (text == null || text == LYRICS_NOT_FOUND) {
            parsedEntries = emptyList()
            return@LaunchedEffect
        }
        val dur = player.duration.takeIf { it > 0L } ?: 0L
        parsedEntries =
            withContext(Dispatchers.Default) {
                val parsed =
                    when {
                        isTtml(text) -> parseTtml(text)
                        isLineSyncedLrc(text) -> insertInstrumentalBreaks(parseLyrics(text), dur)
                        else ->
                            text
                                .lines()
                                .filter { it.isNotBlank() }
                                .map { line -> LyricsEntry(time = -1L, text = line.trim()) }
                    }
                if (parsed.isNotEmpty() && parsed.first().time >= 0) {
                    listOf(HEAD_LYRICS_ENTRY) + parsed
                } else {
                    parsed
                }
            }
    }
    val lyricsEntries: List<LyricsEntry> = parsedEntries.orEmpty()

    val entriesWithWords: List<LyricsEntry> = lyricsEntries

    val wordSyncCache = rememberWordSyncCache()

    val aiRomanizationSessionKey =
        remember(mediaMetadata?.id, lyrics) { AiLyricsRomanization.sessionKey(mediaMetadata?.id, lyrics) }
    val aiRomanizationResult by AiLyricsRomanization.results.collectAsStateWithLifecycle()
    val aiRomanizedLines: List<String?> =
        remember(aiRomanizationResult, aiRomanizationSessionKey, aiRomanizationSettings.active, aiRomanizationSettings.configKey, entriesWithWords) {
            if (!aiRomanizationSettings.active) {
                emptyList()
            } else {
                AiLyricsRomanization.linesFor(
                    aiRomanizationSessionKey,
                    entriesWithWords.map { it.text },
                    aiRomanizationSettings,
                )
            }
        }
    LaunchedEffect(aiRomanizationSessionKey, entriesWithWords, aiRomanizationSettings) {
        if (!aiRomanizationSettings.active || !aiRomanizationSettings.auto) return@LaunchedEffect
        if (entriesWithWords.isEmpty()) return@LaunchedEffect
        AiLyricsRomanization.request(
            sessionKey = aiRomanizationSessionKey,
            lines = entriesWithWords.map { it.text },
            settings = aiRomanizationSettings,
        )
    }
    LaunchedEffect(entriesWithWords, aiRomanizedLines, aiRomanizationSettings.active) {
        if (!aiRomanizationSettings.active) return@LaunchedEffect
        entriesWithWords.forEachIndexed { index, entry ->
            val romanized = aiRomanizedLines.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() }
            if (entry.romanizedTextFlow.value != romanized) {
                entry.romanizedTextFlow.value = romanized
            }
        }
    }

    LaunchedEffect(entriesWithWords, romanizationPreferences) {
        if (!romanizationPreferences.isEnabled) {
            if (aiRomanizationSettings.active) return@LaunchedEffect
            entriesWithWords.forEach { entry ->
                if (entry.romanizedTextFlow.value != null) {
                    entry.romanizedTextFlow.value = null
                }
            }
            return@LaunchedEffect
        }

        entriesWithWords.forEach { entry ->
            val providerRomanized = providedRomanizedTextForEntry(entry, romanizationPreferences)
            if (providerRomanized != null) {
                if (entry.romanizedTextFlow.value != providerRomanized) {
                    entry.romanizedTextFlow.value = providerRomanized
                }
                return@forEach
            }

            if (!shouldRomanizeLyricsLine(entry.text, romanizationPreferences)) {
                if (entry.romanizedTextFlow.value != null) {
                    entry.romanizedTextFlow.value = null
                }
                return@forEach
            }

            launch(Dispatchers.Default) {
                val romanized =
                    try {
                        romanizeLyricsLine(entry.text, romanizationPreferences)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportException(e)
                        null
                    }
                entry.romanizedTextFlow.value = romanized
            }
        }
    }

    val leadMs = if (isTtmlFormat) TTML_LEAD_MS else LRC_LEAD_MS

    val visualTuningMs = if (isTtmlFormat) 0L else LYRIC_VISUAL_TUNING_OFFSET_MS
    val currentPositionMsState = remember(lyrics) { mutableLongStateOf(0L) }
    var currentPositionMs by currentPositionMsState
    var playbackPositionMs by remember { mutableLongStateOf(0L) }
    var currentLineIndex by remember { mutableIntStateOf(0) }

    val currentPositionProvider: () -> Long =
        remember(currentPositionMsState) { { currentPositionMsState.longValue } }

    val latestSliderPositionProvider = rememberUpdatedState(sliderPositionProvider)

    val playerSheetVisibleState = rememberUpdatedState(LocalPlayerSheetVisible.current)

    var lastRawPositionMs by remember(lyrics) { mutableLongStateOf(0L) }
    var playbackResetTick by remember(lyrics) { mutableIntStateOf(0) }

    LaunchedEffect(entriesWithWords, isSynced, leadMs, visualTuningMs, lyricsSyncOffset) {
        if (!isSynced || entriesWithWords.isEmpty()) return@LaunchedEffect
        val pollIntervalMs =
            when {
                v2AnimationsDisabled -> 100L
                isTtmlFormat -> 16L
                else -> 50L
            }
        val useFrameClock = !v2AnimationsDisabled && isTtmlFormat
        while (isActive) {
            if (useFrameClock && !playerSheetVisibleState.value) {
                delay(250L)
                continue
            }
            val sliderPos = latestSliderPositionProvider.value()
            val pos = sliderPos ?: player.currentPosition

            val rawPlayerPositionMs = player.currentPosition.coerceAtLeast(0L)
            if (lastRawPositionMs - rawPlayerPositionMs > V2_POSITION_RESET_BACKWARD_THRESHOLD_MS) {
                playbackResetTick++
            }
            lastRawPositionMs = rawPlayerPositionMs

            playbackPositionMs = (pos + lyricsSyncOffset.toLong()).coerceAtLeast(0L)
            currentPositionMs = (playbackPositionMs + leadMs + visualTuningMs).coerceAtLeast(0L)

            currentLineIndex = findCurrentLineIndex(entriesWithWords, currentPositionMs, 0L)
            if (useFrameClock) {
                withFrameNanos { }
            } else {
                delay(pollIntervalMs)
            }
        }
    }

    val listState = key(playbackResetTick) { rememberLazyListState() }
    var isManualScrolling by remember { mutableStateOf(false) }
    var lastManualScrollTime by remember { mutableLongStateOf(0L) }

    var awaitingFirstFocus by
        remember(playbackResetTick) {
            mutableStateOf(isSynced)
        }
    LaunchedEffect(awaitingFirstFocus) {
        if (!awaitingFirstFocus) return@LaunchedEffect
        delay(V2_FIRST_FOCUS_TIMEOUT_MS)
        awaitingFirstFocus = false
    }
    val firstFocusAlpha =
        androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (awaitingFirstFocus) 0f else 1f,
            animationSpec =
                tween(
                    durationMillis = if (awaitingFirstFocus) 0 else V2_FIRST_FOCUS_FADE_MS,
                    easing = LinearEasing,
                ),
            label = "lyrics-v2-first-focus-alpha",
        )

    val nestedScrollConnection =
        remember {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (!isSelectionModeActive && source == NestedScrollSource.UserInput) {
                        isManualScrolling = true
                        lastManualScrollTime = System.currentTimeMillis()
                    }
                    return Offset.Zero
                }
            }
        }

    LaunchedEffect(isManualScrolling, lastManualScrollTime) {
        if (isManualScrolling) {
            delay(MANUAL_SCROLL_TIMEOUT_MS)
            isManualScrolling = false
        }
    }

    val onLyricsScroll = LocalLyricsScrollListener.current
    LaunchedEffect(isManualScrolling) {
        onLyricsScroll(isManualScrolling)
    }

    LaunchedEffect(currentLineIndex, isManualScrolling, lyricsScroll, entriesWithWords.size) {
        if (!lyricsScroll || isManualScrolling || !isSynced) {
            awaitingFirstFocus = false
            return@LaunchedEffect
        }
        if (currentLineIndex < 0 || currentLineIndex >= entriesWithWords.size) {
            if (entriesWithWords.isNotEmpty()) awaitingFirstFocus = false
            return@LaunchedEffect
        }

        if (awaitingFirstFocus) {
            val viewportHeight =
                listState.layoutInfo.viewportSize.height.takeIf { it > 0 }
                    ?: snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
            listState.scrollToItem(
                index = currentLineIndex,
                scrollOffset = -(viewportHeight * 0.35f).toInt(),
            )
            awaitingFirstFocus = false
            return@LaunchedEffect
        }

        val visibleInfo = listState.layoutInfo
        val viewportHeight = visibleInfo.viewportSize.height
        val targetOffset = (viewportHeight * 0.35f).toInt()

        val distance = abs(currentLineIndex - (listState.firstVisibleItemIndex))
        if (distance > 15) {
            listState.scrollToItem(
                (currentLineIndex - 2).coerceAtLeast(0),
                0,
            )
        }
        listState.animateScrollToItem(
            index = currentLineIndex,
            scrollOffset = -targetOffset,
        )
    }

    BackHandler(enabled = isSelectionModeActive) {
        isSelectionModeActive = false
        selectedIndices.clear()
    }

    LaunchedEffect(showMaxSelectionToast) {
        if (showMaxSelectionToast) {
            Toast
                .makeText(
                    context,
                    context.getString(R.string.max_selection_limit, maxSelectionLimit),
                    Toast.LENGTH_SHORT,
                ).show()
            showMaxSelectionToast = false
        }
    }

    val activity = context as? android.app.Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    BoxWithConstraints(
        contentAlignment = Alignment.TopCenter,
        modifier =
            modifier
                .fillMaxSize()
                .padding(bottom = 12.dp)
                .graphicsLayer { alpha = firstFocusAlpha.value },
    ) {
        if (lyrics == LYRICS_NOT_FOUND) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.lyrics_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
            return@BoxWithConstraints
        }

        if (lyrics == null || parsedEntries == null) {
            ShimmerHost {
                repeat(6) {
                    TextPlaceholder()
                }
            }
            return@BoxWithConstraints
        }

        if (entriesWithWords.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.lyrics_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
            return@BoxWithConstraints
        }

        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection)
                    .smoothFadingEdge(vertical = 80.dp)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(
                items = entriesWithWords,
                key = { index, entry -> "${playbackResetTick}_${index}_${entry.time}" },
                contentType = { _, entry ->
                    when {
                        entry == HEAD_LYRICS_ENTRY -> "head"
                        entry.isInstrumental -> "instrumental"
                        entry.words != null && isSynced -> "wordSynced"
                        else -> "lineSynced"
                    }
                },
            ) { index, item ->
                if (item == HEAD_LYRICS_ENTRY) {
                    Spacer(modifier = Modifier.height(120.dp))
                    return@itemsIndexed
                }

                if (item.isInstrumental && isSynced) {
                    InstrumentalBreakRow(
                        item = item,
                        index = index,
                        hasHeadEntry = entriesWithWords.isNotEmpty() && entriesWithWords[0] === HEAD_LYRICS_ENTRY,
                        currentLineIndex = currentLineIndex,
                        isManualScrolling = isManualScrolling,
                        lyricsLineSpacing = lyricsLineSpacing,
                        lyricsLineBlur = lyricsLineBlur,
                        lyricsClick = lyricsClick,
                        textColor = textColor,
                        inactiveAlpha = inactiveAlpha,
                        playbackPositionProvider = { playbackPositionMs },
                        onSeek = { time -> player.seekTo(time) },
                    )
                    return@itemsIndexed
                }

                val textAlign =
                    when (item.agent?.lowercase()) {
                        "v1", null -> TextAlign.Start
                        "v2" -> TextAlign.End
                        else -> TextAlign.Center
                    }
                val horizontalAlignment =
                    when (item.agent?.lowercase()) {
                        "v1", null -> Alignment.Start
                        "v2" -> Alignment.End
                        else -> Alignment.CenterHorizontally
                    }

                val isActive = isSynced && index == currentLineIndex
                val isPast = isSynced && index < currentLineIndex
                val isFuture = isSynced && index > currentLineIndex
                val isSelected = selectedIndices.contains(index)

                val distanceFromActive = if (isSynced) abs(index - currentLineIndex) else 0
                val lineAlpha =
                    when {
                        !isSynced -> {
                            0.92f
                        }

                        isActive -> {
                            1f
                        }

                        isManualScrolling -> {
                            when {
                                distanceFromActive == 1 -> 0.72f
                                distanceFromActive == 2 -> 0.56f
                                distanceFromActive == 3 -> 0.40f
                                else -> 0.28f
                            }
                        }

                        distanceFromActive == 1 -> {
                            0.52f
                        }

                        distanceFromActive == 2 -> {
                            0.30f
                        }

                        distanceFromActive == 3 -> {
                            0.18f
                        }

                        else -> {
                            0.10f
                        }
                    }
                val targetBlur =
                    when {
                        !isSynced || isActive || (isSelectionModeActive && isSelected) || isManualScrolling -> 0f
                        distanceFromActive == 1 -> 2f
                        distanceFromActive == 2 -> 5f
                        else -> 12f
                    }
                val animatedBlur by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = targetBlur,
                    animationSpec =
                        androidx.compose.animation.core.tween(
                            durationMillis = 300,
                            easing = androidx.compose.animation.core.FastOutSlowInEasing,
                        ),
                    label = "v2LyricBlur",
                )
                val animatedLineScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isActive) 1f else 0.95f,
                    animationSpec =
                        androidx.compose.animation.core.tween(
                            durationMillis = 166,
                            easing = androidx.compose.animation.core.FastOutSlowInEasing,
                        ),
                    label = "v2LineScale",
                )
                val animatedLineAlpha by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = lineAlpha,
                    animationSpec =
                        androidx.compose.animation.core.tween(
                            durationMillis = if (isActive) 330 else 500,
                            easing = androidx.compose.animation.core.FastOutSlowInEasing,
                        ),
                    label = "v2LineAlpha",
                )
                val spotifyRise = remember { Animatable(0f) }
                LaunchedEffect(isActive, spotifyStyle) {
                    if (!spotifyStyle) return@LaunchedEffect
                    if (isActive) {
                        spotifyRise.snapTo(1f)
                        spotifyRise.animateTo(
                            0f,
                            tween(durationMillis = 380, easing = FastOutSlowInEasing),
                        )
                    } else {
                        spotifyRise.snapTo(0f)
                    }
                }
                val lineTransformOrigin =
                    remember(item.agent) {
                        when (item.agent?.lowercase()) {
                            "v2" -> {
                                androidx.compose.ui.graphics
                                    .TransformOrigin(1f, 0.5f)
                            }

                            "v1", null -> {
                                androidx.compose.ui.graphics
                                    .TransformOrigin(0f, 0.5f)
                            }

                            else -> {
                                androidx.compose.ui.graphics
                                    .TransformOrigin(0.5f, 0.5f)
                            }
                        }
                    }

                val hasBackgroundWords = item.words?.any { it.isBackground } == true
                val isAllBackground = item.words?.all { it.isBackground || it.text.isBlank() } == true
                val baseLayoutDirection = LocalLayoutDirection.current
                val lineText =
                    remember(item.text, item.words) {
                        item.words
                            ?.joinToString(separator = "") { it.text }
                            ?.takeIf { it.isNotBlank() }
                            ?: item.text
                    }
                val lineIsRtl = remember(lineText) { isRtlText(lineText) }
                val lineLayoutDirection =
                    remember(lineIsRtl, baseLayoutDirection) {
                        if (lineIsRtl) LayoutDirection.Rtl else baseLayoutDirection
                    }

                CompositionLocalProvider(LocalLayoutDirection provides lineLayoutDirection) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    color =
                                        if (isSelected && isSelectionModeActive) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                        } else {
                                            Color.Transparent
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                ).padding(
                                    start = if (isAllBackground) 24.dp else 12.dp,
                                    end = 12.dp,
                                    top =
                                        if (index == 0 ||
                                            (index == 1 && entriesWithWords[0] == HEAD_LYRICS_ENTRY)
                                        ) {
                                            0.dp
                                        } else {
                                            (lyricsLineSpacing * 8).dp
                                        },
                                    bottom = (lyricsLineSpacing * 8).dp,
                                ).then(
                                    if (lyricsLineBlur) {
                                        Modifier.blur(
                                            radiusX = animatedBlur.dp,
                                            radiusY = animatedBlur.dp,
                                            edgeTreatment = BlurredEdgeTreatment.Unbounded,
                                        )
                                    } else {
                                        Modifier
                                    },
                                ).graphicsLayer {
                                    scaleX = animatedLineScale
                                    scaleY = animatedLineScale
                                    alpha = animatedLineAlpha
                                    transformOrigin = lineTransformOrigin
                                    if (spotifyStyle) {
                                        translationY = spotifyRise.value * 14.dp.toPx()
                                    }
                                }.combinedClickable(
                                    enabled = true,
                                    onClick = {
                                        if (isSelectionModeActive) {
                                            if (isSelected) {
                                                selectedIndices.remove(index)
                                                if (selectedIndices.isEmpty()) {
                                                    isSelectionModeActive = false
                                                }
                                            } else {
                                                if (selectedIndices.size < maxSelectionLimit) {
                                                    selectedIndices.add(index)
                                                } else {
                                                    showMaxSelectionToast = true
                                                }
                                            }
                                        } else if (lyricsClick && isSynced && item.time > 0) {
                                            player.seekTo(item.time)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionModeActive) {
                                            isSelectionModeActive = true
                                            selectedIndices.add(index)
                                        } else if (!isSelected && selectedIndices.size < maxSelectionLimit) {
                                            selectedIndices.add(index)
                                        } else if (!isSelected) {
                                            showMaxSelectionToast = true
                                        }
                                    },
                                ),
                        horizontalAlignment = horizontalAlignment,
                    ) {
                        val romanizedText =
                            if (romanizationPreferences.showsRomanization) {
                                val value by item.romanizedTextFlow.collectAsStateWithLifecycle()
                                value
                            } else {
                                null
                            }
                        val translationText =
                            remember(showTranslations, item.providerTranslationText, item.text) {
                                if (showTranslations) providedTranslationTextForEntry(item) else null
                            }
                        val supplementaryBaseTextStyle = MaterialTheme.typography.bodyMedium
                        val supplementaryTextStyle =
                            remember(supplementaryBaseTextStyle, lyricsTextSize, lyricsFontFamily, isAllBackground) {
                                supplementaryBaseTextStyle.copy(
                                    fontSize = (lyricsTextSize * 0.55f).sp,
                                    lineHeight = (lyricsTextSize * 0.75f).sp,
                                    fontWeight = FontWeight.Normal,
                                    fontStyle = if (isAllBackground) FontStyle.Italic else FontStyle.Normal,
                                    fontFamily = lyricsFontFamily ?: supplementaryBaseTextStyle.fontFamily,
                                )
                            }

                        if (romanizedText != null) {
                            Text(
                                text = romanizedText,
                                style = supplementaryTextStyle,
                                color = textColor.copy(alpha = if (isActive) 0.76f else 0.42f),
                                textAlign = textAlign,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = (lyricsTextSize * 0.18f).dp),
                            )
                        }

                        if (item.words != null && isSynced && wordSyncCache.getOrCompute(item)) {
                            if (spotifyStyle) {
                                LyricsLineSpotify(
                                    words = item.words!!,
                                    isActive = isActive,
                                    isPast = isPast,
                                    distanceFromActive = distanceFromActive,
                                    currentPositionProvider = currentPositionProvider,
                                    textColor = textColor,
                                    inactiveAlpha = inactiveAlpha,
                                    baseFontSize = lyricsTextSize,
                                    isLineAllBackground = isAllBackground,
                                    textAlign = textAlign,
                                    lyricsFontFamily = lyricsFontFamily,
                                    isRtl = lineIsRtl,
                                )
                            } else {
                                LyricsLineV2(
                                    words = item.words!!,
                                    isActive = isActive,
                                    isPast = isPast,
                                    distanceFromActive = distanceFromActive,
                                    currentPositionProvider = currentPositionProvider,
                                    textColor = textColor,
                                    inactiveAlpha = inactiveAlpha,
                                    baseFontSize = lyricsTextSize,
                                    isLineAllBackground = isAllBackground,
                                    textAlign = textAlign,
                                    lyricsFontFamily = lyricsFontFamily,
                                    isRtl = lineIsRtl,
                                    bounceFactor = bounceFactor,
                                    glowFactor = glowFactor,
                                    fillTransitionWidth = fillTransitionWidth,
                                )
                            }
                        } else if (isSynced) {
                            LyricsLineLrcBounce(
                                text = item.text,
                                isActive = isActive,
                                textColor = textColor.copy(alpha = if (isActive) 1f else 0.52f),
                                fontSize = lyricsTextSize,
                                lineSpacing = lyricsLineSpacing,
                                isAllBackground = isAllBackground,
                                lyricsFontFamily = lyricsFontFamily,
                                textAlign = textAlign,
                                bounceFactor = if (lrcBounceEnabled) bounceFactor else 0f,
                            )
                        } else {
                            Text(
                                text = item.text,
                                style =
                                    MaterialTheme.typography.headlineMedium.copy(
                                        fontSize = if (isAllBackground) (lyricsTextSize * 0.82f).sp else lyricsTextSize.sp,
                                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        fontStyle = if (isAllBackground) FontStyle.Italic else FontStyle.Normal,
                                        lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
                                        fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
                                    ),
                                color = textColor.copy(alpha = if (isActive) 1f else 0.52f),
                                textAlign = textAlign,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        if (translationText != null) {
                            Text(
                                text = translationText,
                                style = supplementaryTextStyle,
                                color = textColor.copy(alpha = if (isActive) 0.76f else 0.42f),
                                textAlign = textAlign,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = (lyricsTextSize * 0.3f).dp),
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(300.dp))
            }
        }

        if (isManualScrolling && isSynced) {
            androidx.compose.material3.FilledTonalButton(
                onClick = {
                    isManualScrolling = false
                    scope.launch {
                        val viewportHeight = listState.layoutInfo.viewportSize.height
                        listState.animateScrollToItem(
                            index = currentLineIndex,
                            scrollOffset = -(viewportHeight * 0.35f).toInt(),
                        )
                    }
                },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    text = "Resume",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        if (isSelectionModeActive) {
            mediaMetadata?.let { metadata ->
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(48.dp)
                                    .background(
                                        color = Color.Black.copy(alpha = 0.3f),
                                        shape = CircleShape,
                                    ).clickable {
                                        isSelectionModeActive = false
                                        selectedIndices.clear()
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.close),
                                contentDescription = stringResource(R.string.cancel),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        Row(
                            modifier =
                                Modifier
                                    .background(
                                        color =
                                            if (selectedIndices.isNotEmpty()) {
                                                Color.White.copy(alpha = 0.9f)
                                            } else {
                                                Color.White.copy(alpha = 0.5f)
                                            },
                                        shape = RoundedCornerShape(24.dp),
                                    ).clickable(enabled = selectedIndices.isNotEmpty()) {
                                        if (selectedIndices.isNotEmpty()) {
                                            val sortedIndices = selectedIndices.sorted()
                                            val selectedEntries =
                                                sortedIndices.mapNotNull { entriesWithWords.getOrNull(it) }
                                            val selectedLyricsText =
                                                selectedEntries.joinToString("\n") { it.text }

                                            if (selectedLyricsText.isNotBlank()) {
                                                shareDialogData =
                                                    LyricsSharePayload(
                                                        lyricsText = selectedLyricsText,
                                                        songTitle = metadata.title ?: "",
                                                        artists = metadata.artists.joinToString { it.name },
                                                        lines = selectedEntries.map { it.toLyricsShareLine() },
                                                    )
                                                showShareDialog = true
                                            }
                                            isSelectionModeActive = false
                                            selectedIndices.clear()
                                        }
                                    }.padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.share),
                                contentDescription = stringResource(R.string.share_selected),
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = stringResource(R.string.share),
                                color = Color.Black,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showShareDialog && shareDialogData != null) {
        val sharePayload = shareDialogData!!
        BasicAlertDialog(onDismissRequest = { showShareDialog = false }) {
            Card(
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                modifier =
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth(0.85f),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.share_lyrics),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    shareLyricsAsText(
                                        context = context,
                                        payload = sharePayload,
                                        songId = mediaMetadata?.id,
                                    )
                                    showShareDialog = false
                                }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.share),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.share_as_text),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showShareImageDialog = true
                                    showShareDialog = false
                                }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.share),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.share_as_image),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            modifier =
                                Modifier
                                    .clickable { showShareDialog = false }
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                        )
                    }
                }
            }
        }
    }

    if (showShareImageDialog && shareDialogData != null) {
        val sharePayload = shareDialogData!!
        LyricsShareImageDialog(
            mediaMetadata = mediaMetadata,
            payload = sharePayload,
            onDismissRequest = { showShareImageDialog = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricsLineV2(
    words: List<WordTimestamp>,
    isActive: Boolean,
    isPast: Boolean,
    distanceFromActive: Int,
    currentPositionProvider: () -> Long,
    textColor: Color,
    inactiveAlpha: Float,
    baseFontSize: Float,
    isLineAllBackground: Boolean,
    textAlign: TextAlign,
    lyricsFontFamily: FontFamily?,
    isRtl: Boolean,
    bounceFactor: Float,
    glowFactor: Float,
    fillTransitionWidth: Float,
) {
    val arrangement =
        when (textAlign) {
            TextAlign.Center -> Arrangement.Center
            TextAlign.End -> Arrangement.End
            else -> Arrangement.Start
        }

    val mainWords = words.filter { !it.isBackground }
    val bgWords = words.filter { it.isBackground }

    val isNearActive = isActive || distanceFromActive <= 1
    val effectivePositionMs =
        if (isNearActive) {
            currentPositionProvider()
        } else if (isPast) {
            Long.MAX_VALUE
        } else {
            0L
        }

    if (mainWords.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = arrangement,
        ) {
            mainWords.forEachIndexed { wordIndex, word ->
                if (word.text == " ") {
                    Text(
                        text = " ",
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontSize = if (isLineAllBackground) (baseFontSize * 0.82f).sp else baseFontSize.sp,
                                fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
                            ),
                        color = Color.Transparent,
                    )
                    return@forEachIndexed
                }
                if (word.text == "\n") {
                    Spacer(modifier = Modifier.fillMaxWidth())
                    return@forEachIndexed
                }

                AnimatedWordV2(
                    word = word,
                    wordIndex = wordIndex,
                    isLineActive = isActive,
                    isLinePast = isPast,
                    currentPositionMs = effectivePositionMs,
                    textColor = textColor,
                    inactiveAlpha = inactiveAlpha,
                    fontSize = if (isLineAllBackground) baseFontSize * 0.82f else baseFontSize,
                    isBackground = isLineAllBackground,
                    lyricsFontFamily = lyricsFontFamily,
                    isRtl = isRtl,
                    bounceFactor = bounceFactor,
                    glowFactor = glowFactor,
                    fillTransitionWidth = fillTransitionWidth,
                )
            }
        }
    }

    if (bgWords.isNotEmpty()) {
        val spacerHeight = if (mainWords.isNotEmpty()) 4.dp else 0.dp
        if (mainWords.isNotEmpty()) Spacer(modifier = Modifier.height(spacerHeight))

        FlowRow(
            modifier = Modifier.fillMaxWidth().alpha(0.85f),
            horizontalArrangement = arrangement,
        ) {
            bgWords.forEachIndexed { wordIndex, word ->
                if (word.text == " ") {
                    Text(
                        text = " ",
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontSize = (baseFontSize * 0.65f).sp,
                                fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
                            ),
                        color = Color.Transparent,
                    )
                    return@forEachIndexed
                }

                AnimatedWordV2(
                    word = word,
                    wordIndex = wordIndex + mainWords.size,
                    isLineActive = isActive,
                    isLinePast = isPast,
                    currentPositionMs = effectivePositionMs,
                    textColor = textColor,
                    inactiveAlpha = inactiveAlpha,
                    fontSize = baseFontSize * 0.65f,
                    isBackground = true,
                    lyricsFontFamily = lyricsFontFamily,
                    isRtl = isRtl,
                    bounceFactor = bounceFactor,
                    glowFactor = glowFactor,
                    fillTransitionWidth = fillTransitionWidth,
                )
            }
        }
    }
}

@Composable
private fun AnimatedWordV2(
    word: WordTimestamp,
    wordIndex: Int,
    isLineActive: Boolean,
    isLinePast: Boolean,
    currentPositionMs: Long,
    textColor: Color,
    inactiveAlpha: Float,
    fontSize: Float,
    isBackground: Boolean,
    lyricsFontFamily: FontFamily?,
    isRtl: Boolean,
    bounceFactor: Float,
    glowFactor: Float,
    fillTransitionWidth: Float,
) {
    val wordStartMs = (word.startTime * 1000).toLong()
    val rawWordEndMs = (word.endTime * 1000).toLong()

    val wordEndMs = if (rawWordEndMs > wordStartMs) rawWordEndMs else wordStartMs + MIN_SWEEP_MS
    val wordDuration = (wordEndMs - wordStartMs).coerceAtLeast(1L)

    val isWordComplete = currentPositionMs >= wordEndMs
    val isWordActive = currentPositionMs in wordStartMs until wordEndMs

    val sweepAnimatable = remember(word) { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(isWordActive, isWordComplete, wordStartMs, wordEndMs) {
        when {
            isWordComplete && sweepAnimatable.value < 1f -> {
                sweepAnimatable.animateTo(
                    1f,
                    androidx.compose.animation.core.tween(
                        durationMillis = 80,
                        easing = androidx.compose.animation.core.LinearEasing,
                    ),
                )
            }
            isWordActive -> {
                val remainingMs = (wordEndMs - currentPositionMs).coerceAtLeast(1L)
                val animDurationMs = maxOf(remainingMs, MIN_SWEEP_MS)
                sweepAnimatable.animateTo(
                    1f,
                    androidx.compose.animation.core.tween(
                        durationMillis = animDurationMs.toInt().coerceAtLeast(1),
                        easing = androidx.compose.animation.core.LinearEasing,
                    ),
                )
            }
            else -> {
                sweepAnimatable.snapTo(0f)
            }
        }
    }
    val progress = if (isWordComplete) 1f else sweepAnimatable.value

    val sinProgress = kotlin.math.sin(progress * kotlin.math.PI).toFloat()
    val wordScale = 1f + (0.015f * bounceFactor * sinProgress)

    val targetFloat = if (isWordActive) -4f * bounceFactor * sinProgress else 0f
    val floatOffset by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetFloat,
        animationSpec =
            androidx.compose.animation.core.tween(
                durationMillis = if (isWordActive) 50 else 350,
                easing = androidx.compose.animation.core.FastOutSlowInEasing,
            ),
        label = "v2FloatOffset",
    )

    val glowProgress = (progress * 2f).coerceAtMost(1f)
    val glowAlpha = if (isWordActive) glowProgress * 0.45f * glowFactor else 0f
    val glowRadius = if (isWordActive) glowProgress * 12f * glowFactor else 0f

    val actualFontSize = if (isBackground) fontSize * 0.85f else fontSize
    val fontWeight = if (isLineActive || isLinePast) FontWeight.ExtraBold else FontWeight.SemiBold
    val glowPadding = 10.dp

    val baseHeadlineStyle = MaterialTheme.typography.headlineMedium
    val baseTextStyle =
        remember(baseHeadlineStyle, actualFontSize, fontWeight, lyricsFontFamily) {
            baseHeadlineStyle.copy(
                fontSize = actualFontSize.sp,
                fontWeight = fontWeight,
                fontStyle = FontStyle.Normal,
                lineHeight = (actualFontSize * 1.35f).sp,
                fontFamily = lyricsFontFamily ?: baseHeadlineStyle.fontFamily,
            )
        }

    val sweepColors =
        if (isRtl) {
            remember { listOf(Color.Transparent, Color.Black) }
        } else {
            remember { listOf(Color.Black, Color.Transparent) }
        }

    Box(
        modifier =
            Modifier
                .layout { measurable, constraints ->
                    val glowPaddingPx = glowPadding.roundToPx()
                    val looseConstraints =
                        constraints.copy(
                            minWidth = 0,
                            maxWidth = constraints.maxWidth,
                            minHeight = 0,
                            maxHeight = Constraints.Infinity,
                        )
                    val placeable = measurable.measure(looseConstraints)

                    val coreWidth = (placeable.width - glowPaddingPx * 2).coerceAtLeast(0)
                    val coreHeight = (placeable.height - glowPaddingPx * 2).coerceAtLeast(0)

                    layout(coreWidth, coreHeight) {
                        placeable.place(-glowPaddingPx, -glowPaddingPx)
                    }
                }.graphicsLayer {
                    clip = false
                    translationY = floatOffset * density
                    scaleX = wordScale
                    scaleY = wordScale
                },
    ) {
        Text(
            text = word.text,
            style = baseTextStyle,
            color = textColor.copy(alpha = if (isBackground) inactiveAlpha * 0.7f else inactiveAlpha),
            modifier = Modifier.padding(glowPadding),
        )

        if (isWordComplete || isWordActive || isLinePast) {
            val overlayTextStyle =
                if (glowAlpha > 0f) {
                    remember(baseTextStyle, glowAlpha, glowRadius, textColor) {
                        baseTextStyle.copy(
                            shadow =
                                Shadow(
                                    color = textColor.copy(alpha = glowAlpha),
                                    offset = Offset.Zero,
                                    blurRadius = glowRadius.coerceAtLeast(1f),
                                ),
                        )
                    }
                } else {
                    baseTextStyle
                }
            Text(
                text = word.text,
                style = overlayTextStyle,
                color =
                    textColor.copy(
                        alpha = if (isBackground) 0.75f else 1f,
                    ),
                modifier =
                    if (isWordActive && !isWordComplete) {
                        Modifier
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                val edgeWidth = fillTransitionWidth.dp.toPx()
                                val center =
                                    if (isRtl) {
                                        size.width - ((size.width + edgeWidth * 2) * progress - edgeWidth)
                                    } else {
                                        (size.width + edgeWidth * 2) * progress - edgeWidth
                                    }
                                drawRect(
                                    brush =
                                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            colors = sweepColors,
                                            startX = center - edgeWidth,
                                            endX = center + edgeWidth,
                                        ),
                                    blendMode = BlendMode.DstIn,
                                )
                            }.padding(glowPadding)
                    } else {
                        Modifier.padding(glowPadding)
                    },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricsLineSpotify(
    words: List<WordTimestamp>,
    isActive: Boolean,
    isPast: Boolean,
    distanceFromActive: Int,
    currentPositionProvider: () -> Long,
    textColor: Color,
    inactiveAlpha: Float,
    baseFontSize: Float,
    isLineAllBackground: Boolean,
    textAlign: TextAlign,
    lyricsFontFamily: FontFamily?,
    isRtl: Boolean,
) {
    val arrangement =
        when (textAlign) {
            TextAlign.Center -> Arrangement.Center
            TextAlign.End -> Arrangement.End
            else -> Arrangement.Start
        }

    val mainWords = words.filter { !it.isBackground }
    val bgWords = words.filter { it.isBackground }

    val isNearActive = isActive || distanceFromActive <= 1
    val effectivePositionMs =
        if (isNearActive) {
            currentPositionProvider()
        } else if (isPast) {
            Long.MAX_VALUE
        } else {
            0L
        }

    val pillVisible = isActive && !isPast

    if (mainWords.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = arrangement,
        ) {
            mainWords.forEachIndexed { _, word ->
                if (word.text == " ") {
                    Text(
                        text = " ",
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontSize = if (isLineAllBackground) (baseFontSize * 0.82f).sp else baseFontSize.sp,
                                fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
                            ),
                        color = Color.Transparent,
                    )
                    return@forEachIndexed
                }
                if (word.text == "\n") {
                    Spacer(modifier = Modifier.fillMaxWidth())
                    return@forEachIndexed
                }
                SpotifyWord(
                    word = word,
                    isLineActive = isActive,
                    pillVisible = pillVisible,
                    currentPositionMs = effectivePositionMs,
                    textColor = textColor,
                    inactiveAlpha = inactiveAlpha,
                    fontSize = if (isLineAllBackground) baseFontSize * 0.82f else baseFontSize,
                    isBackground = isLineAllBackground,
                    lyricsFontFamily = lyricsFontFamily,
                    isRtl = isRtl,
                )
            }
        }
    }

    if (bgWords.isNotEmpty()) {
        if (mainWords.isNotEmpty()) Spacer(modifier = Modifier.height(4.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth().alpha(0.85f),
            horizontalArrangement = arrangement,
        ) {
            bgWords.forEachIndexed { _, word ->
                if (word.text == " ") {
                    Text(
                        text = " ",
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontSize = (baseFontSize * 0.65f).sp,
                                fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
                            ),
                        color = Color.Transparent,
                    )
                    return@forEachIndexed
                }
                if (word.text == "\n") {
                    Spacer(modifier = Modifier.fillMaxWidth())
                    return@forEachIndexed
                }
                SpotifyWord(
                    word = word,
                    isLineActive = isActive,
                    pillVisible = pillVisible,
                    currentPositionMs = effectivePositionMs,
                    textColor = textColor,
                    inactiveAlpha = inactiveAlpha,
                    fontSize = baseFontSize * 0.65f,
                    isBackground = true,
                    lyricsFontFamily = lyricsFontFamily,
                    isRtl = isRtl,
                )
            }
        }
    }
}

@Composable
internal fun SpotifyWord(
    word: WordTimestamp,
    isLineActive: Boolean,
    pillVisible: Boolean,
    currentPositionMs: Long,
    textColor: Color,
    inactiveAlpha: Float,
    fontSize: Float,
    isBackground: Boolean,
    lyricsFontFamily: FontFamily?,
    isRtl: Boolean,
) {
    val wordStartMs = (word.startTime * 1000).toLong()
    val rawWordEndMs = (word.endTime * 1000).toLong()

    val wordEndMs = if (rawWordEndMs > wordStartMs) rawWordEndMs else wordStartMs + MIN_SWEEP_MS
    val isWordComplete = currentPositionMs >= wordEndMs
    val isWordActive = currentPositionMs in wordStartMs until wordEndMs

    val sweepAnimatable = remember(word) { Animatable(0f) }
    LaunchedEffect(isWordActive, isWordComplete, wordStartMs, wordEndMs) {
        when {
            isWordComplete && sweepAnimatable.value < 1f -> {
                sweepAnimatable.animateTo(
                    1f,
                    tween(durationMillis = 80, easing = LinearEasing),
                )
            }

            isWordActive -> {
                val remainingMs = (wordEndMs - currentPositionMs).coerceAtLeast(1L)
                sweepAnimatable.animateTo(
                    1f,
                    tween(
                        durationMillis = maxOf(remainingMs, MIN_SWEEP_MS).toInt().coerceAtLeast(1),
                        easing = LinearEasing,
                    ),
                )
            }

            else -> {
                sweepAnimatable.snapTo(0f)
            }
        }
    }
    val progress = if (isWordComplete) 1f else sweepAnimatable.value

    val pillRadius = 6.dp
    val pillPaddingHorizontal = 7.dp
    val pillPaddingVertical = 1.dp
    val interWordGap = 2.dp

    val baseHeadlineStyle = MaterialTheme.typography.headlineMedium
    val textStyle =
        remember(baseHeadlineStyle, fontSize, lyricsFontFamily) {
            baseHeadlineStyle.copy(
                fontSize = fontSize.sp,
                fontWeight = FontWeight.SemiBold,
                fontStyle = FontStyle.Normal,
                lineHeight = (fontSize * 1.35f).sp,
                fontFamily = lyricsFontFamily ?: baseHeadlineStyle.fontFamily,
            )
        }

    Box(
        modifier =
            Modifier
                .padding(horizontal = interWordGap)
                .drawBehind {
                    if (!pillVisible) return@drawBehind
                    val r = pillRadius.toPx()
                    drawRoundRect(
                        color = textColor.copy(alpha = 0.15f),
                        cornerRadius = CornerRadius(r),
                    )
                    val pillWidth = size.width
                    val fillPx = pillWidth * progress
                    if (fillPx > 0f) {
                        val left = if (isRtl) pillWidth - fillPx else 0f
                        clipRect(left, 0f, left + fillPx, size.height) {
                            drawRoundRect(
                                color = textColor.copy(alpha = 0.32f),
                                cornerRadius = CornerRadius(r),
                            )
                        }
                    }
                }
                .padding(horizontal = pillPaddingHorizontal, vertical = pillPaddingVertical),
    ) {
        Text(
            text = word.text,
            style = textStyle,
            color = textColor.copy(alpha = if (isBackground) inactiveAlpha * 0.75f else (inactiveAlpha + 0.1f).coerceAtMost(1f)),
        )

        if (pillVisible && (isWordComplete || isWordActive) && isLineActive) {
            Text(
                text = word.text,
                style = textStyle,
                color = textColor.copy(alpha = if (isBackground) 0.75f else 1f),
                modifier =
                    if (isWordActive && !isWordComplete) {
                        Modifier
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                val padHpx = pillPaddingHorizontal.toPx()
                                val pillWidth = size.width + padHpx * 2f
                                val fillPx = pillWidth * progress
                                val pillLeft = if (isRtl) pillWidth - fillPx else 0f
                                val rawLeft = pillLeft - padHpx
                                val solidFraction = (rawLeft / size.width).coerceIn(0f, 1f)
                                drawContent()
                                drawRect(
                                    brush =
                                        if (isRtl) {
                                            Brush.horizontalGradient(
                                                0f to Color.Transparent,
                                                solidFraction.coerceAtMost(1f) to Color.Transparent,
                                                solidFraction.coerceAtLeast(0f) to Color.Black,
                                                1f to Color.Black,
                                            )
                                        } else {
                                            Brush.horizontalGradient(
                                                0f to Color.Black,
                                                solidFraction.coerceIn(0f, 1f) to Color.Black,
                                                solidFraction.coerceAtLeast(0f) to Color.Transparent,
                                                1f to Color.Transparent,
                                            )
                                        },
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                    } else {
                        Modifier
                    },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricsLineLrcBounce(
    text: String,
    isActive: Boolean,
    textColor: Color,
    fontSize: Float,
    lineSpacing: Float,
    isAllBackground: Boolean,
    lyricsFontFamily: FontFamily?,
    textAlign: TextAlign,
    bounceFactor: Float,
) {
    val words = remember(text) { text.toLyricsWrappingUnits() }
    val effectiveFontSize = if (isAllBackground) fontSize * 0.82f else fontSize
    val fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold
    val fontStyle = if (isAllBackground) FontStyle.Italic else FontStyle.Normal
    val scaleAnimatables = remember(words.size) { List(words.size) { Animatable(1f) } }
    val floatAnimatables = remember(words.size) { List(words.size) { Animatable(0f) } }

    LaunchedEffect(isActive) {
        if (!isActive || bounceFactor == 0f) return@LaunchedEffect
        words.indices.forEach { i ->
            launch {
                delay(i * 40L)
                try {
                    scaleAnimatables[i].animateTo(
                        targetValue = 1f + 0.045f * bounceFactor,
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessHigh,
                            ),
                    )
                    scaleAnimatables[i].animateTo(
                        targetValue = 1f,
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                    )
                } finally {
                    withContext(NonCancellable) { scaleAnimatables[i].snapTo(1f) }
                }
            }
            launch {
                delay(i * 40L)
                try {
                    floatAnimatables[i].animateTo(
                        targetValue = -5f * bounceFactor,
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessHigh,
                            ),
                    )
                    floatAnimatables[i].animateTo(
                        targetValue = 0f,
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                    )
                } finally {
                    withContext(NonCancellable) { floatAnimatables[i].snapTo(0f) }
                }
            }
        }
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            when (textAlign) {
                TextAlign.Center -> Arrangement.Center
                TextAlign.End -> Arrangement.End
                else -> Arrangement.Start
            },
    ) {
        words.forEachIndexed { i, word ->
            LrcBouncingWord(
                text = word,
                scaleAnim = scaleAnimatables[i],
                floatAnim = floatAnimatables[i],
                color = textColor,
                fontSize = effectiveFontSize,
                lineSpacing = lineSpacing,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                lyricsFontFamily = lyricsFontFamily,
            )
        }
    }
}

@Composable
private fun LrcBouncingWord(
    text: String,
    scaleAnim: Animatable<Float, AnimationVector1D>,
    floatAnim: Animatable<Float, AnimationVector1D>,
    color: Color,
    fontSize: Float,
    lineSpacing: Float,
    fontWeight: FontWeight,
    fontStyle: FontStyle,
    lyricsFontFamily: FontFamily?,
) {
    Text(
        text = text,
        style =
            MaterialTheme.typography.headlineMedium.copy(
                fontSize = fontSize.sp,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                lineHeight = (fontSize * lineSpacing).sp,
                fontFamily = lyricsFontFamily ?: MaterialTheme.typography.headlineMedium.fontFamily,
            ),
        color = color,
        modifier =
            Modifier.graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
                translationY = floatAnim.value
            },
    )
}

@Composable
private fun InstrumentalBreakRow(
    item: LyricsEntry,
    index: Int,
    hasHeadEntry: Boolean,
    currentLineIndex: Int,
    isManualScrolling: Boolean,
    lyricsLineSpacing: Float,
    lyricsLineBlur: Boolean,
    lyricsClick: Boolean,
    textColor: Color,
    inactiveAlpha: Float,
    playbackPositionProvider: () -> Long,
    onSeek: (Long) -> Unit,
) {
    val startTimeMs = item.time
    val endTimeMs = item.time + item.durationMs
    val playbackPositionMs = playbackPositionProvider()
    val isActive = playbackPositionMs in startTimeMs until endTimeMs
    val distanceFromActive = abs(index - currentLineIndex)
    val instrAlpha =
        when {
            isActive -> 1f
            isManualScrolling -> {
                when {
                    distanceFromActive == 1 -> 0.72f
                    distanceFromActive == 2 -> 0.56f
                    distanceFromActive == 3 -> 0.40f
                    else -> 0.28f
                }
            }
            distanceFromActive == 1 -> 0.52f
            distanceFromActive == 2 -> 0.30f
            distanceFromActive == 3 -> 0.18f
            else -> inactiveAlpha
        }
    val animatedInstrAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = instrAlpha,
        animationSpec =
            androidx.compose.animation.core.tween(
                durationMillis = if (isActive) 330 else 500,
                easing = androidx.compose.animation.core.FastOutSlowInEasing,
            ),
        label = "v2InstrumentalAlpha",
    )
    val animatedInstrScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isActive) 1f else 0.95f,
        animationSpec =
            androidx.compose.animation.core.tween(
                durationMillis = 166,
                easing = androidx.compose.animation.core.FastOutSlowInEasing,
            ),
        label = "v2InstrumentalScale",
    )
    val targetInstrBlur =
        when {
            isActive || isManualScrolling -> 0f
            distanceFromActive == 1 -> 2f
            distanceFromActive == 2 -> 5f
            else -> 12f
        }
    val animatedInstrBlur by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetInstrBlur,
        animationSpec =
            androidx.compose.animation.core.tween(
                durationMillis = 300,
                easing = androidx.compose.animation.core.FastOutSlowInEasing,
            ),
        label = "v2InstrumentalBlur",
    )
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top =
                        if (index == 0 || (index == 1 && hasHeadEntry)) {
                            0.dp
                        } else {
                            (lyricsLineSpacing * 8).dp
                        },
                    bottom = (lyricsLineSpacing * 8).dp,
                ).then(
                    if (lyricsLineBlur) {
                        Modifier.blur(
                            radiusX = animatedInstrBlur.dp,
                            radiusY = animatedInstrBlur.dp,
                            edgeTreatment = BlurredEdgeTreatment.Unbounded,
                        )
                    } else {
                        Modifier
                    },
                ).graphicsLayer {
                    scaleX = animatedInstrScale
                    scaleY = animatedInstrScale
                    alpha = animatedInstrAlpha
                }.then(
                    if (lyricsClick && item.time > 0) {
                        Modifier.clickable { onSeek(item.time) }
                    } else {
                        Modifier
                    },
                ),
    ) {
        InstrumentalBreakItem(
            durationMs = item.durationMs,
            currentPositionMs = playbackPositionMs,
            startTimeMs = startTimeMs,
            textColor = textColor,
            inactiveAlpha = inactiveAlpha,
        )
    }
}

@Composable
private fun InstrumentalBreakItem(
    durationMs: Long,
    currentPositionMs: Long,
    startTimeMs: Long,
    textColor: Color,
    inactiveAlpha: Float,
) {
    val musicNotePath =
        remember {
            androidx.compose.ui.graphics.vector
                .PathParser()
                .parsePathString(
                    "M10 21q-1.65 0-2.825-1.175T6 17t1.175-2.825T10 13q.575 0 1.063.138t.937.412V4" +
                        "q0-.425.288-.712T13 3h4q.425 0 .713.288T18 4v2q0 .425-.288.713T17 7h-3v10" +
                        "q0 1.65-1.175 2.825T10 21",
                ).toPath()
        }

    val targetFillFraction =
        when {
            durationMs <= 0L -> {
                0f
            }

            currentPositionMs <= startTimeMs -> {
                0f
            }

            currentPositionMs >= startTimeMs + durationMs -> {
                1f
            }

            else -> {
                ((currentPositionMs - startTimeMs).toDouble() / durationMs.toDouble())
                    .toFloat()
                    .coerceIn(0f, 1f)
            }
        }
    val fillFraction by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetFillFraction,
        animationSpec =
            spring(
                stiffness = Spring.StiffnessHigh,
                dampingRatio = Spring.DampingRatioNoBouncy,
            ),
        label = "instrumentalFill",
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.size(48.dp)) {
        val scaleX = size.width / 24f
        val scaleY = size.height / 24f
        val pivot = androidx.compose.ui.geometry.Offset.Zero

        withTransform(
            transformBlock = { scale(scaleX, scaleY, pivot) },
        ) {
            drawPath(path = musicNotePath, color = textColor.copy(alpha = inactiveAlpha))
        }

        if (fillFraction > 0f) {
            val clipTop = size.height * (1f - fillFraction)
            clipRect(
                left = 0f,
                top = clipTop,
                right = size.width,
                bottom = size.height,
            ) {
                withTransform(
                    transformBlock = { scale(scaleX, scaleY, pivot) },
                ) {
                    drawPath(path = musicNotePath, color = textColor)
                }
            }
        }
    }
}
