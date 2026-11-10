/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.menu

import moe.rukamori.archivetune.ui.component.LocalLiquidGlassTuning
import androidx.activity.compose.BackHandler
import android.app.SearchManager
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.drawBackdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ai.AiLyricsDocumentParser
import moe.rukamori.archivetune.ai.AiLyricsSegment
import moe.rukamori.archivetune.constants.AiApiKeyKey
import moe.rukamori.archivetune.constants.AiApiValidationStatus
import moe.rukamori.archivetune.constants.AiApiValidationStatusKey
import moe.rukamori.archivetune.constants.AiCustomEndpointKey
import moe.rukamori.archivetune.constants.AiProvider
import moe.rukamori.archivetune.constants.AiProviderKey
import moe.rukamori.archivetune.constants.TranslatorTargetLangKey
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.lyrics.AiLyricsRomanization
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewAction
import moe.rukamori.archivetune.ui.component.NewActionGrid
import moe.rukamori.archivetune.ui.component.PlatformBackdrop
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LyricsMenuViewModel
import moe.rukamori.archivetune.viewmodels.LyricsSearchResultUiModel
import moe.rukamori.archivetune.viewmodels.LyricsSearchScreenState
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import moe.rukamori.archivetune.ui.component.KeepStatusBarHiddenInDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsMenu(
    lyricsProvider: () -> LyricsEntity?,
    mediaMetadataProvider: () -> MediaMetadata,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    viewModel: LyricsMenuViewModel = hiltViewModel(),

    transparentSurface: Boolean = false,
    showTranslationActions: Boolean = true,
) {
    val context = LocalContext.current

    var showEditDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showLyricsSyncOffsetDialog by rememberSaveable { mutableStateOf(false) }
    val isRefetching by viewModel.isRefetching.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.refetchCompletionEvents.collect {
            onDismiss()
        }
    }

    if (showEditDialog) {
        TextFieldDialog(
            onDismiss = { showEditDialog = false },
            icon = { Icon(painter = painterResource(R.drawable.solar_pen_linear), contentDescription = null) },
            title = { Text(text = mediaMetadataProvider().title) },
            initialTextFieldValue = TextFieldValue(lyricsProvider()?.lyrics.orEmpty()),
            singleLine = false,
            onDone = {
                viewModel.updateLyrics(mediaMetadataProvider(), it)
            },
        )
    }

    var showSearchDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showSearchResultDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showExportDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var searchOriginatedFromExport by remember { mutableStateOf(false) }

    val searchMediaMetadata =
        remember(showSearchDialog) {
            mediaMetadataProvider()
        }
    val (titleField, onTitleFieldChange) =
        rememberSaveable(showSearchDialog, stateSaver = TextFieldValue.Saver) {
            mutableStateOf(
                TextFieldValue(
                    text = mediaMetadataProvider().title,
                ),
            )
        }
    val (artistField, onArtistFieldChange) =
        rememberSaveable(showSearchDialog, stateSaver = TextFieldValue.Saver) {
            mutableStateOf(
                TextFieldValue(
                    text = mediaMetadataProvider().artists.joinToString { it.name },
                ),
            )
        }

    val isNetworkAvailable by viewModel.isNetworkAvailable.collectAsStateWithLifecycle()
    val lyricsSearchState by viewModel.lyricsSearchState.collectAsStateWithLifecycle()
    val isAiTranslating by viewModel.isAiTranslating.collectAsStateWithLifecycle()
    val translationUndo by viewModel.translationUndo.collectAsStateWithLifecycle()
    val (aiProvider) = rememberEnumPreference(AiProviderKey, AiProvider.NONE)
    val (aiApiKey) = rememberPreference(AiApiKeyKey, "")
    val (aiCustomEndpoint) = rememberPreference(AiCustomEndpointKey, "")
    val (aiValidationStatus) = rememberEnumPreference(AiApiValidationStatusKey, AiApiValidationStatus.UNKNOWN)
    var expandedSearchResultId by rememberSaveable { mutableStateOf<String?>(null) }
    val currentLyrics = lyricsProvider()?.lyrics.orEmpty()
    val isTranslateEnabled =
        currentLyrics.isNotBlank() &&
            currentLyrics != LyricsEntity.LYRICS_NOT_FOUND
    val canUndoTranslation = translationUndo?.mediaId == mediaMetadataProvider().id
    val isAiProviderConfigured = aiProvider != AiProvider.NONE
    val isAiTranslationEnabled =
        currentLyrics.isNotBlank() &&
            currentLyrics != LyricsEntity.LYRICS_NOT_FOUND &&
            isAiProviderConfigured &&
            aiApiKey.isNotBlank() &&
            (aiProvider != AiProvider.CUSTOM || aiCustomEndpoint.isNotBlank()) &&
            aiValidationStatus != AiApiValidationStatus.FAILED

    val aiRomanizationSettings = AiLyricsRomanization.rememberSettings()
    val isAiRomanizing by AiLyricsRomanization.running.collectAsStateWithLifecycle()
    val isAiRomanizationEnabled =
        aiRomanizationSettings.active &&
            currentLyrics.isNotBlank() &&
            currentLyrics != LyricsEntity.LYRICS_NOT_FOUND &&
            !isAiRomanizing

    LaunchedEffect(Unit) {
        viewModel.aiTranslationEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        AiLyricsRomanization.requestOutcomes.collect { status ->
            if (status == AiLyricsRomanization.RequestStatus.EMPTY_RESULT) {
                Toast
                    .makeText(context, context.getString(R.string.ai_romanize_empty_result), Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    if (isRefetching) {
        DefaultDialog(onDismiss = {}) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(12.dp),
            ) {
                LoadingIndicator(modifier = Modifier.size(40.dp))
            }
        }
    }

    if (showSearchDialog) {
        SearchLyricsInputDialog(
            titleField = titleField,
            onTitleFieldChange = onTitleFieldChange,
            artistField = artistField,
            onArtistFieldChange = onArtistFieldChange,
            onDismiss = { showSearchDialog = false },
            onSearchOnline = {
                showSearchDialog = false
                onDismiss()
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_WEB_SEARCH).apply {
                            putExtra(
                                SearchManager.QUERY,
                                "${artistField.text} ${titleField.text} lyrics",
                            )
                        },
                    )
                } catch (_: Exception) {
                }
            },
            onSearch = {
                viewModel.search(
                    searchMediaMetadata.id,
                    titleField.text,
                    artistField.text,
                    searchMediaMetadata.album?.title,
                    searchMediaMetadata.duration,
                )
                showSearchResultDialog = true

                if (!isNetworkAvailable) {
                    Toast.makeText(context, context.getString(R.string.error_no_internet), Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    if (showSearchResultDialog) {
        LyricsSearchResultDialog(
            state = lyricsSearchState,
            expandedResultId = expandedSearchResultId,
            onExpandedResultChange = { resultId ->
                expandedSearchResultId = if (expandedSearchResultId == resultId) null else resultId
            },
            onRefetch = {
                expandedSearchResultId = null
                viewModel.search(
                    searchMediaMetadata.id,
                    titleField.text,
                    artistField.text,
                    searchMediaMetadata.album?.title,
                    searchMediaMetadata.duration,
                )
            },
            onResultSelected = { result ->
                if (!searchOriginatedFromExport) {
                    onDismiss()
                } else {
                    showSearchDialog = false
                    showSearchResultDialog = false
                    showExportDialog = true
                }
                searchOriginatedFromExport = false
                viewModel.cancelSearch()
                viewModel.updateLyrics(
                    mediaMetadata = searchMediaMetadata,
                    lyrics = result.lyrics,
                    source = LyricsEntity.Source.USER_SELECTION,
                    providerName = result.providerName,
                )
            },
            onDismiss = {
                expandedSearchResultId = null
                showSearchResultDialog = false
                viewModel.resetSearchState()
            },
        )
    }

    if (showExportDialog) {
        ExportLyricsDialog(
            lyricsEntity = lyricsProvider(),
            mediaMetadata = mediaMetadataProvider(),
            onOpenSearch = {
                searchOriginatedFromExport = true
                showSearchDialog = true
            },
            onDismiss = { showExportDialog = false },
        )
    }

    if (showLyricsSyncOffsetDialog) {
        var tempLyricsSyncOffset by remember { mutableFloatStateOf(lyricsSyncOffset.toFloat()) }

        DefaultDialog(
            onDismiss = {
                tempLyricsSyncOffset = lyricsSyncOffset.toFloat()
                showLyricsSyncOffsetDialog = false
            },
            icon = {
                Icon(painter = painterResource(R.drawable.solar_speed), contentDescription = null)
            },
            title = { Text(stringResource(R.string.lyrics_sync_offset)) },
            buttons = {
                TextButton(
                    onClick = { tempLyricsSyncOffset = 0f },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.reset))
                }
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = {
                        tempLyricsSyncOffset = lyricsSyncOffset.toFloat()
                        showLyricsSyncOffsetDialog = false
                    },
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                FilledTonalButton(
                    onClick = {
                        onLyricsSyncOffsetChange(tempLyricsSyncOffset.roundToInt())
                        showLyricsSyncOffsetDialog = false
                        onDismiss()
                    },
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = formatLyricsSyncOffset(tempLyricsSyncOffset.roundToInt()),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                Slider(
                    value = tempLyricsSyncOffset,
                    onValueChange = { tempLyricsSyncOffset = it },
                    valueRange = -1000f..1000f,
                    steps = 79,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    val configuration = LocalConfiguration.current
    val defaultLanguageCode =
        remember(configuration) {
            configuration.locales
                .get(0)
                .getDisplayLanguage(Locale.ENGLISH)
                .uppercase(Locale.US)
                .replace(' ', '_')
        }
    val (targetLanguage, _) = rememberPreference(TranslatorTargetLangKey, defaultLanguageCode)
    val isTranslationInProgress = isAiTranslating

    LazyColumn(
        userScrollEnabled = true,
        contentPadding =
            PaddingValues(
                start = 0.dp,
                top = 0.dp,
                end = 0.dp,
                bottom = 12.dp,
            ),
    ) {
        item {
            val lyricsText = lyricsProvider()?.lyrics.orEmpty()
            val menuItems: List<AppleMusicLyricsMenuItem> =
                listOfNotNull(
                    AppleMusicLyricsMenuItem(
                        label = stringResource(R.string.edit),
                        iconRes = R.drawable.edit,
                        isDestructive = false,
                        enabled = true,
                        onClick = { showEditDialog = true },
                    ),
                    AppleMusicLyricsMenuItem(
                        label = stringResource(R.string.refetch),
                        iconRes = R.drawable.cached,
                        isDestructive = false,
                        enabled = true,
                        onClick = {
                            viewModel.refetchLyrics(mediaMetadataProvider())
                        },
                    ),
                    if (showTranslationActions) {
                        AppleMusicLyricsMenuItem(
                            label = stringResource(R.string.translate),
                            iconRes = R.drawable.translate,
                            isDestructive = false,
                            enabled = isTranslateEnabled && isAiTranslationEnabled && !isTranslationInProgress,
                            onClick = {
                                viewModel.translateLyricsWithAi(
                                    mediaMetadata = mediaMetadataProvider(),
                                    lyrics = lyricsText,
                                    targetLanguage = targetLanguage,
                                )
                            },
                        )
                    } else {
                        null
                    },
                    AppleMusicLyricsMenuItem(
                        label = stringResource(R.string.lyrics_sync_offset),
                        iconRes = R.drawable.speed,
                        isDestructive = false,
                        enabled = true,
                        onClick = { showLyricsSyncOffsetDialog = true },
                    ),
                    if (showTranslationActions) {
                        AppleMusicLyricsMenuItem(
                            label = stringResource(R.string.ai_romanize_now),
                            iconRes = R.drawable.language,
                            isDestructive = false,
                            enabled = isAiRomanizationEnabled,
                            onClick = {
                                val status = AiLyricsRomanization.request(
                                    sessionKey =
                                        AiLyricsRomanization.sessionKey(
                                            mediaId = mediaMetadataProvider().id,
                                            lyrics = lyricsText,
                                        ),
                                    lines = AiLyricsRomanization.linesOf(lyricsText, mediaMetadataProvider().duration),
                                    settings = aiRomanizationSettings,

                                    force = true,
                                )
                                val toastResId = when (status) {
                                    AiLyricsRomanization.RequestStatus.STARTED -> R.string.ai_romanize_started
                                    AiLyricsRomanization.RequestStatus.ALREADY_CACHED -> R.string.ai_romanize_already_cached
                                    AiLyricsRomanization.RequestStatus.IN_FLIGHT -> R.string.ai_romanize_in_flight
                                    AiLyricsRomanization.RequestStatus.SETTINGS_DISABLED -> R.string.ai_romanize_settings_disabled
                                    AiLyricsRomanization.RequestStatus.NO_LYRICS -> R.string.ai_romanize_no_lyrics
                                    AiLyricsRomanization.RequestStatus.EXCLUDED_LANGUAGE -> R.string.ai_romanize_excluded_language
                                    AiLyricsRomanization.RequestStatus.NO_ROMANIZABLE_SCRIPT -> R.string.ai_romanize_no_romanizable_script
                                    AiLyricsRomanization.RequestStatus.EMPTY_RESULT -> R.string.ai_romanize_empty_result
                                }
                                Toast
                                    .makeText(context, context.getString(toastResId), Toast.LENGTH_SHORT)
                                    .show()
                            },
                        )
                    } else {
                        null
                    },
                    if (showTranslationActions) {
                        AppleMusicLyricsMenuItem(
                            label = stringResource(R.string.undo_translation),
                            iconRes = R.drawable.restore,

                            isDestructive = true,
                            enabled = canUndoTranslation,
                            onClick = { viewModel.undoTranslation(mediaMetadataProvider().id) },
                        )
                    } else {
                        null
                    },

                    AppleMusicLyricsMenuItem(
                        label = stringResource(R.string.search),
                        iconRes = R.drawable.search,
                        isDestructive = false,
                        enabled = true,
                        onClick = { showSearchDialog = true },
                    ),

                    AppleMusicLyricsMenuItem(
                        label = stringResource(R.string.export_lyrics),
                        iconRes = R.drawable.download,
                        isDestructive = false,
                        enabled = lyricsText.isNotBlank(),
                        onClick = { showExportDialog = true },
                    ),
                )

            if (transparentSurface) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = Color.Transparent,
                    modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(vertical = 0.dp)) {
                        menuItems.forEachIndexed { index, item ->
                            AppleMusicLyricsMenuRow(
                                item = item,
                            )

                            if (index < menuItems.size - 1) {
                                HorizontalDivider(
                                    color = Color.White.copy(alpha = 0.12f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }

                    }
                }
            } else {
                MenuSurfaceSection {
                    NewActionGrid(
                        actions =
                            menuItems.map { item ->
                                NewAction(
                                    icon = {
                                        Icon(
                                            painter = painterResource(item.iconRes),
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    text = item.label,
                                    onClick = item.onClick,
                                    enabled = item.enabled,
                                )
                            },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsSearchResultDialog(
    state: LyricsSearchScreenState,
    expandedResultId: String?,
    onExpandedResultChange: (String) -> Unit,
    onRefetch: () -> Unit,
    onResultSelected: (LyricsSearchResultUiModel) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        KeepStatusBarHiddenInDialog()
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .imePadding()
                    .navigationBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            val listContentPadding =
                remember {
                    PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 20.dp)
                }
            val listArrangement = remember { Arrangement.spacedBy(10.dp) }

            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                        .heightIn(max = maxHeight),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LyricsSearchResultHeader(
                        state = state,
                        onRefetch = onRefetch,
                        onDismiss = onDismiss,
                    )
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                        contentPadding = listContentPadding,
                        verticalArrangement = listArrangement,
                    ) {
                        when (state) {
                            LyricsSearchScreenState.Loading -> {
                                item(contentType = "lyrics_search_loading") {
                                    LyricsSearchLoadingContent()
                                }
                            }

                            LyricsSearchScreenState.Empty -> {
                                item(contentType = "lyrics_search_empty") {
                                    LyricsSearchEmptyContent()
                                }
                            }

                            is LyricsSearchScreenState.Error -> {
                                item(contentType = "lyrics_search_error") {
                                    LyricsSearchErrorContent(messageResId = state.messageResId)
                                }
                            }

                            is LyricsSearchScreenState.Success -> {
                                itemsIndexed(
                                    items = state.results,
                                    key = { _, result -> result.id },
                                    contentType = { _, _ -> "lyrics_search_result" },
                                ) { _, result ->
                                    LyricsSearchResultItem(
                                        result = result,
                                        isExpanded = result.id == expandedResultId,
                                        onExpandedChange = { onExpandedResultChange(result.id) },
                                        onResultSelected = { onResultSelected(result) },
                                    )
                                }

                                if (state.isSearching) {
                                    item(contentType = "lyrics_search_footer_loading") {
                                        LyricsSearchFooterLoading()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsSearchResultHeader(
    state: LyricsSearchScreenState,
    onRefetch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val subtitle =
        when (state) {
            LyricsSearchScreenState.Loading -> {
                stringResource(R.string.lyrics_searching_providers)
            }

            LyricsSearchScreenState.Empty -> {
                stringResource(R.string.lyrics_not_found)
            }

            is LyricsSearchScreenState.Error -> {
                stringResource(state.messageResId)
            }

            is LyricsSearchScreenState.Success -> {
                stringResource(
                    R.string.lyrics_search_results_count,
                    state.results.size,
                )
            }
        }
    val isSearching =
        state == LyricsSearchScreenState.Loading ||
            state is LyricsSearchScreenState.Success && state.isSearching
    val isSearchComplete =
        when (state) {
            LyricsSearchScreenState.Loading -> false

            is LyricsSearchScreenState.Success -> !state.isSearching

            LyricsSearchScreenState.Empty,
            is LyricsSearchScreenState.Error,
            -> true
        }
    val rowArrangement = remember { Arrangement.spacedBy(16.dp) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 10.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = rowArrangement,
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        painter = painterResource(R.drawable.solar_magnifer_linear),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.search_lyrics),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isSearching) {
                LoadingIndicator(
                    modifier = Modifier.size(28.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (isSearchComplete) {
                IconButton(
                    onClick = onRefetch,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.solar_replay_linear),
                        contentDescription = stringResource(R.string.refetch),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.solar_close_circle_linear),
                    contentDescription = stringResource(R.string.close),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsSearchResultItem(
    result: LyricsSearchResultUiModel,
    isExpanded: Boolean,
    onExpandedChange: () -> Unit,
    onResultSelected: () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    val lyricsType =
        when {
            result.isWordSynced -> stringResource(R.string.lyrics_word_sync)
            result.isLineSynced -> stringResource(R.string.lyrics_synced_badge)
            else -> stringResource(R.string.lyrics_search_plain_badge)
        }
    val stats =
        stringResource(
            R.string.lyrics_search_result_stats,
            result.lineCount,
            result.characterCount,
        )
    val metadataArrangement = remember { Arrangement.spacedBy(8.dp) }
    val containerColor =
        if (isExpanded) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        }
    val contentColor =
        if (isExpanded) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    val outlineColor =
        if (isExpanded) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
    val itemArrangement = remember { Arrangement.spacedBy(14.dp) }

    Surface(
        onClick = onResultSelected,
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = motionScheme.defaultSpatialSpec()),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, outlineColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = itemArrangement,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = metadataArrangement,
            ) {
                LyricsSearchTypeIcon(
                    result = result,
                    isExpanded = isExpanded,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.providerName,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = lyricsType,
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = onExpandedChange,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (isExpanded) R.drawable.expand_less else R.drawable.expand_more,
                            ),
                        contentDescription = stringResource(R.string.details),
                        tint = contentColor,
                    )
                }
            }
            LyricsSearchResultSupportingContent(
                preview = result.preview,
                isExpanded = isExpanded,
                contentColor = contentColor,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = metadataArrangement,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LyricsSearchMetadataPill(
                    icon = R.drawable.info,
                    text = lyricsType,
                    isExpanded = isExpanded,
                    modifier = Modifier.weight(1f),
                )
                LyricsSearchMetadataPill(
                    icon = R.drawable.text_fields,
                    text = stats,
                    isExpanded = isExpanded,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LyricsSearchTypeIcon(
    result: LyricsSearchResultUiModel,
    isExpanded: Boolean,
) {
    val icon =
        when {
            result.isWordSynced -> R.drawable.lyrics
            result.isLineSynced -> R.drawable.sync
            else -> R.drawable.format_align_left
        }
    val containerColor =
        if (isExpanded) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        }
    val contentColor =
        if (isExpanded) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onTertiaryContainer
        }

    Surface(
        shape = CircleShape,
        color = containerColor,
        modifier = Modifier.size(36.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun LyricsSearchResultSupportingContent(
    preview: String,
    isExpanded: Boolean,
    contentColor: Color,
) {
    Text(
        text = preview,
        modifier = Modifier.fillMaxWidth(),
        maxLines = if (isExpanded) 8 else 2,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium,
        color = contentColor,
    )
}

@Composable
private fun LyricsSearchMetadataPill(
    icon: Int,
    text: String,
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        if (isExpanded) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.52f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val contentColor =
        if (isExpanded) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    val pillArrangement = remember { Arrangement.spacedBy(6.dp) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = pillArrangement,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LyricsSearchLoadingContent() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LoadingIndicator(modifier = Modifier.size(40.dp))
        Text(
            text = stringResource(R.string.lyrics_searching_providers),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LyricsSearchFooterLoading() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        LoadingIndicator(modifier = Modifier.size(24.dp))
        Text(
            text = stringResource(R.string.lyrics_search_still_searching),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LyricsSearchEmptyContent() {
    LyricsSearchMessageContent(
        icon = R.drawable.search_off,
        text = stringResource(R.string.lyrics_not_found),
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    )
}

@Composable
private fun LyricsSearchErrorContent(messageResId: Int) {
    LyricsSearchMessageContent(
        icon = R.drawable.error,
        text = stringResource(messageResId),
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    )
}

@Composable
private fun LyricsSearchMessageContent(
    icon: Int,
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = containerColor,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatLyricsSyncOffset(offsetMs: Int): String = if (offsetMs > 0) "+$offsetMs ms" else "$offsetMs ms"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchLyricsInputDialog(
    titleField: TextFieldValue,
    onTitleFieldChange: (TextFieldValue) -> Unit,
    artistField: TextFieldValue,
    onArtistFieldChange: (TextFieldValue) -> Unit,
    onDismiss: () -> Unit,
    onSearchOnline: () -> Unit,
    onSearch: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val maximumDialogHeight = (configuration.screenHeightDp.dp - 48.dp).coerceAtLeast(280.dp)
    val scrollState = rememberScrollState()
    val contentArrangement = remember { Arrangement.spacedBy(24.dp) }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier =
            Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maximumDialogHeight),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(24.dp),
                verticalArrangement = contentArrangement,
            ) {
                LyricsSearchInputHeader(onDismiss = onDismiss)

                LyricsSearchInputFields(
                    titleField = titleField,
                    onTitleFieldChange = onTitleFieldChange,
                    artistField = artistField,
                    onArtistFieldChange = onArtistFieldChange,
                    onSearch = onSearch,
                )

                LyricsSearchInputActions(
                    onSearchOnline = onSearchOnline,
                    onSearch = onSearch,
                )
            }
        }
    }
}

@Composable
private fun LyricsSearchInputHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            modifier = Modifier.size(34.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.solar_magnifer_linear),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.search_lyrics),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = AlertDialogDefaults.titleContentColor,
            modifier = Modifier.weight(1f),
        )

        IconButton(
            onClick = onDismiss,
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(
                painter = painterResource(R.drawable.solar_close_circle_linear),
                contentDescription = stringResource(R.string.close),
            )
        }
    }
}

@Composable
private fun LyricsSearchInputFields(
    titleField: TextFieldValue,
    onTitleFieldChange: (TextFieldValue) -> Unit,
    artistField: TextFieldValue,
    onArtistFieldChange: (TextFieldValue) -> Unit,
    onSearch: () -> Unit,
) {
    val fieldArrangement = remember { Arrangement.spacedBy(16.dp) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = fieldArrangement,
    ) {
        LyricsSearchTextField(
            value = titleField,
            onValueChange = onTitleFieldChange,
            label = stringResource(R.string.song_title),
            iconResId = R.drawable.music_note,
            imeAction = ImeAction.Next,
            onSearch = onSearch,
        )
        LyricsSearchTextField(
            value = artistField,
            onValueChange = onArtistFieldChange,
            label = stringResource(R.string.song_artists),
            iconResId = R.drawable.artist,
            imeAction = ImeAction.Search,
            onSearch = onSearch,
        )
    }
}

@Composable
private fun LyricsSearchTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    iconResId: Int,
    imeAction: ImeAction,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardOptions = remember(imeAction) { KeyboardOptions(imeAction = imeAction) }
    val currentOnSearch by rememberUpdatedState(onSearch)
    val keyboardActions =
        remember(imeAction) {
            if (imeAction == ImeAction.Search) {
                KeyboardActions(onSearch = { currentOnSearch() })
            } else {
                KeyboardActions.Default
            }
        }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                painter = painterResource(iconResId),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
        trailingIcon =
            if (value.text.isNotEmpty()) {
                {
                    IconButton(onClick = { onValueChange(TextFieldValue()) }) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.clear),
                        )
                    }
                }
            } else {
                null
            },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
    )
}

@Composable
private fun LyricsSearchInputActions(
    onSearchOnline: () -> Unit,
    onSearch: () -> Unit,
) {
    val horizontalArrangement = remember { Arrangement.spacedBy(8.dp, Alignment.End) }
    val verticalArrangement = remember { Arrangement.spacedBy(8.dp) }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
    ) {
        OutlinedButton(
            onClick = onSearchOnline,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.solar_magnifer_linear),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.search_online))
        }

        Button(
            onClick = onSearch,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.solar_magnifer_linear),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.search))
        }
    }
}

@Immutable
private data class AppleMusicLyricsMenuItem(
    val label: String,
    val iconRes: Int,
    val isDestructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
private fun AppleMusicLyricsMenuRow(
    item: AppleMusicLyricsMenuItem,
    modifier: Modifier = Modifier,
) {
    val headlineColor =
        if (item.isDestructive) {
            Color(0xFFFF453A)
        } else {
            Color.White
        }
    val iconColor =
        if (item.isDestructive) {
            Color(0xFFFF453A)
        } else {
            Color.White
        }
    val headlineWeight = if (item.isDestructive) FontWeight.SemiBold else FontWeight.Medium

    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    enabled = item.enabled,
                    onClick = item.onClick,
                )
                .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.label,
            color = headlineColor,
            fontWeight = headlineWeight,
            fontSize = 16.sp,
        )
        Icon(
            painter = painterResource(item.iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = iconColor,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnchoredLyricsOverflowMenu(
    iconBoundsInRoot: Rect,
    lyricsProvider: () -> LyricsEntity?,
    mediaMetadataProvider: () -> MediaMetadata,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    viewModel: LyricsMenuViewModel = hiltViewModel(),
    backdrop: com.kyant.backdrop.Backdrop? = null,

    scrimColor: Color = Color.Black.copy(alpha = 0.45f),
    showTranslationActions: Boolean = true,
) {
    var dismissed by remember { mutableStateOf(false) }

    BackHandler(enabled = !dismissed) {
        dismissed = true
    }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val scaleAnim = remember { Animatable(0.3f) }
    val alphaAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (dismissed) return@LaunchedEffect

        val scaleJob = scope.launch {
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
            )
        }
        val alphaJob = scope.launch {
            alphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(180),
            )
        }
        scaleJob.join()
        alphaJob.join()
    }

    LaunchedEffect(dismissed) {
        if (!dismissed) return@LaunchedEffect
        val scaleJob = scope.launch {
            scaleAnim.animateTo(
                targetValue = 0.3f,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
            )
        }
        val alphaJob = scope.launch {
            alphaAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(180),
            )
        }
        scaleJob.join()
        alphaJob.join()

        onDismiss()
    }

    val scale = scaleAnim.value
    val alpha = alphaAnim.value

    var anchorSpaceHeightPx by remember { mutableIntStateOf(0) }
    var popupHeightPx by remember { mutableIntStateOf(0) }
    val verticalOffsetPx = with(density) { 4.dp.toPx() }.toInt()

    fun opensAboveAnchor(): Boolean {
        val neededHeightPx =
            if (popupHeightPx > 0) popupHeightPx else with(density) { 360.dp.toPx() }.toInt()
        return anchorSpaceHeightPx > 0 &&
            iconBoundsInRoot.bottom + verticalOffsetPx + neededHeightPx > anchorSpaceHeightPx
    }

    val glassTuning = LocalLiquidGlassTuning.current
    val frostedBlurModifier = remember(backdrop, glassTuning) {
        if (backdrop != null) {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                effects = {
                    colorControls(saturation = glassTuning.saturation)

                    blur((20f * glassTuning.blurFactor).dp.toPx())

                    lens(
                        refractionHeight = minOf(
                            (16f * glassTuning.refractionHeightFactor).dp.toPx(),
                            size.minDimension / 2f,
                        ),
                        refractionAmount = (40f * glassTuning.refractionAmountFactor).dp.toPx(),
                        depthEffect = glassTuning.depth3D,
                        chromaticAberration = glassTuning.chromaticAberration,
                    )
                },
                onDrawBackdrop = { drawBackdrop ->
                    drawBackdrop()
                },
                shape = { RoundedCornerShape(16.dp) },
            )
        } else {
            null
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged { anchorSpaceHeightPx = it.height }
                .background(scrimColor.copy(alpha = scrimColor.alpha * alpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (!dismissed) dismissed = true
                },
    ) {
        Box(
            modifier =
                Modifier
                    .offset {
                        val popupWidthPx = with(density) { 220.dp.toPx() }.toInt()
                        val horizontalMarginPx = with(density) { 16.dp.toPx() }.toInt()
                        val iconRight = iconBoundsInRoot.right.toInt()
                        val iconBottom = iconBoundsInRoot.bottom.toInt()
                        val x =
                            (iconRight - popupWidthPx)
                                .coerceAtLeast(horizontalMarginPx)
                        val y =
                            if (opensAboveAnchor()) {
                                (iconBoundsInRoot.top - verticalOffsetPx -
                                    (if (popupHeightPx > 0) popupHeightPx else with(density) { 360.dp.toPx() }.toInt()))
                                    .coerceAtLeast(0f)
                                    .toInt()
                            } else {
                                iconBottom + verticalOffsetPx
                            }
                        IntOffset(x = x, y = y)
                    }
                    .widthIn(max = 220.dp)
                    .heightIn(max = 520.dp)
                    .onSizeChanged { popupHeightPx = it.height }
                    .graphicsLayer {
                        this.alpha = alpha
                        this.scaleX = scale
                        this.scaleY = scale

                        val popupWidthPx = 220.dp.toPx()
                        val horizontalMarginPx = 16.dp.toPx()
                        val popupLeftPx =
                            (iconBoundsInRoot.right - popupWidthPx)
                                .coerceAtLeast(horizontalMarginPx)
                        val iconCenterX = (iconBoundsInRoot.left + iconBoundsInRoot.right) / 2f
                        val pivotX =
                            ((iconCenterX - popupLeftPx) / popupWidthPx.coerceAtLeast(1f))
                                .coerceIn(0.02f, 0.98f)
                        this.transformOrigin =
                            TransformOrigin(pivotX, if (opensAboveAnchor()) 1f else 0f)

                        this.shadowElevation = 16.dp.toPx()
                        this.shape = RoundedCornerShape(16.dp)
                        this.clip = false
                    }

                    .then(
                        frostedBlurModifier
                            ?: Modifier.background(Color.Black.copy(alpha = 0.65f * alpha)),
                    )

                    .background(Color.Black.copy(alpha = 0.55f))
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                    },
        ) {
            LyricsMenu(
                lyricsProvider = lyricsProvider,
                mediaMetadataProvider = mediaMetadataProvider,
                lyricsSyncOffset = lyricsSyncOffset,
                onLyricsSyncOffsetChange = onLyricsSyncOffsetChange,
                onDismiss = {
                    if (!dismissed) dismissed = true
                },
                viewModel = viewModel,
                transparentSurface = true,
                showTranslationActions = showTranslationActions,
            )
        }
    }
}

private enum class LyricsExportFormat(val extension: String) {
    TTML("ttml"),
    LRC("lrc"),
    PLAIN("txt"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportLyricsDialog(
    lyricsEntity: LyricsEntity?,
    mediaMetadata: MediaMetadata,
    onOpenSearch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val rawLyrics = lyricsEntity?.lyrics.orEmpty()
    val format = remember(rawLyrics) { detectLyricsExportFormat(rawLyrics) }
    val isSynced = format != LyricsExportFormat.PLAIN
    var prettyPrint by rememberSaveable { mutableStateOf(false) }
    var showPreview by rememberSaveable { mutableStateOf(false) }

    val exportedText =
        remember(rawLyrics, prettyPrint) {
            if (prettyPrint && isSynced) prettyPrintLyricsForExport(rawLyrics) else rawLyrics
        }
    val fileBase =
        remember(mediaMetadata.id) {
            sanitizeExportFileBase("${mediaMetadata.artists.joinToString(", ") { it.name }} - ${mediaMetadata.title}")
        }
    val extension = if (prettyPrint && isSynced) "txt" else format.extension
    val fileName = "$fileBase.$extension"
    val mime =
        when {
            prettyPrint && isSynced -> moe.rukamori.archivetune.utils.LyricsExportCoordinator.MIME_TEXT
            format == LyricsExportFormat.TTML -> moe.rukamori.archivetune.utils.LyricsExportCoordinator.MIME_XML
            else -> moe.rukamori.archivetune.utils.LyricsExportCoordinator.MIME_TEXT
        }
    val sourceLabel =
        lyricsEntity?.providerName?.takeIf { it.isNotBlank() }
            ?: lyricsEntity?.source?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() }

    DefaultDialog(
        onDismiss = onDismiss,
        icon = { Icon(painterResource(R.drawable.download), contentDescription = null) },
        title = { Text(stringResource(R.string.export_lyrics)) },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
            Spacer(modifier = Modifier.weight(1f))
            FilledTonalButton(
                shape = RoundedCornerShape(18.dp),
                enabled = rawLyrics.isNotBlank(),
                onClick = {
                    if (exportedText.isBlank()) {
                        Toast.makeText(context, R.string.export_lyrics_failed, Toast.LENGTH_SHORT).show()
                        return@FilledTonalButton
                    }
                    moe.rukamori.archivetune.utils.LyricsExportCoordinator.request(
                        context,
                        exportedText,
                        fileName,
                        mime,
                    )
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.export))
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.solar_server_linear),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.export_lyrics_source),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = sourceLabel ?: stringResource(R.string.export_lyrics_source_none),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onOpenSearch) {
                    Icon(
                        painter = painterResource(R.drawable.search),
                        contentDescription = stringResource(R.string.search),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.export_lyrics_pretty_print),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text =
                            if (isSynced) {
                                stringResource(R.string.export_lyrics_pretty_print_desc)
                            } else {
                                stringResource(R.string.export_lyrics_pretty_print_unavailable)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = prettyPrint && isSynced,
                    enabled = isSynced && rawLyrics.isNotBlank(),
                    onCheckedChange = { prettyPrint = it },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showPreview = !showPreview }
                        .padding(vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.export_lyrics_preview),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter =
                        painterResource(
                            if (showPreview) R.drawable.expand_less else R.drawable.expand_more,
                        ),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showPreview) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                ) {
                    Text(
                        text = exportedText.ifBlank { stringResource(R.string.export_lyrics_source_none) },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Text(
                text = "${stringResource(R.string.export_lyrics_file)}: $fileName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

private val GenericVoiceAgentRegex = Regex("""(?i)^v\d+$""")

private val LrcTokenRegex =
    Regex("""\[[0-9]{1,3}:[0-9]{2}(?:[.:][0-9]{1,3})?\]|\[[a-zA-Z]+:[^]]*]""")

private val TtmlTagRegex = Regex("""<[^>]+>""")

internal fun prettyPrintLyricsForExport(raw: String): String {
    if (raw.isBlank()) return raw

    val entries =
        runCatching {
            if (moe.rukamori.archivetune.lyrics.LyricsUtils.isTtml(raw)) {
                moe.rukamori.archivetune.lyrics.LyricsUtils.parseTtml(raw)
            } else {
                moe.rukamori.archivetune.lyrics.LyricsUtils.parseLyrics(raw)
            }
        }.getOrElse { emptyList() }
    if (entries.isEmpty()) {
        return raw.lineSequence()
            .map { line -> LrcTokenRegex.replace(line, "").trim() }
            .map { line -> TtmlTagRegex.replace(line, "").trim() }
            .filterIndexed { index, line -> line.isNotEmpty() || (index > 0) }
            .joinToString("\n")
            .trim()
    }
    return entries.joinToString("\n") { entry ->
        val singer =
            entry.agent
                ?.trim()
                ?.takeIf { it.isNotEmpty() && !GenericVoiceAgentRegex.matches(it) }
        if (singer == null) entry.text else "${entry.text} ($singer)"
    }.trim()
}

private fun detectLyricsExportFormat(lyrics: String): LyricsExportFormat {
    if (lyrics.isBlank()) return LyricsExportFormat.PLAIN
    if (moe.rukamori.archivetune.lyrics.LyricsUtils.isTtml(lyrics)) return LyricsExportFormat.TTML

    val timedLine = Regex("""^\[.*?].*""")
    if (lyrics.lineSequence().any { timedLine.matches(it.trim()) }) return LyricsExportFormat.LRC
    return LyricsExportFormat.PLAIN
}

private fun sanitizeExportFileBase(raw: String): String =
    raw
        .map { ch -> if (ch.isLetterOrDigit() || ch in " -_()&.,'’" && !ch.isWhitespace()) ch else if (ch.isWhitespace()) ' ' else '_' }
        .joinToString("")
        .trim()
        .take(120)
        .ifBlank { "lyrics" }

