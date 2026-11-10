/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ai.AiLyricsTranslator
import moe.rukamori.archivetune.ai.AiServiceConfig
import moe.rukamori.archivetune.constants.AiApiKeyKey
import moe.rukamori.archivetune.constants.AiCustomEndpointKey
import moe.rukamori.archivetune.constants.AiCustomModelKey
import moe.rukamori.archivetune.constants.AiProvider
import moe.rukamori.archivetune.constants.AiProviderKey
import moe.rukamori.archivetune.constants.AiSelectedModelKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.lyrics.LyricsHelper
import moe.rukamori.archivetune.lyrics.LyricsResult
import moe.rukamori.archivetune.lyrics.LyricsUtils
import moe.rukamori.archivetune.lyrics.LyricsUtils.displayLyricsText
import moe.rukamori.archivetune.lyrics.LyricsUtils.isLineSyncedLrc
import moe.rukamori.archivetune.lyrics.LyricsUtils.isTtml
import moe.rukamori.archivetune.constants.AutoTranslateExcludedLanguagesKey
import moe.rukamori.archivetune.constants.AutoTranslateLyricsKey
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.NetworkConnectivityObserver
import moe.rukamori.archivetune.utils.dataStore
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

sealed interface LyricsSearchScreenState {
    data object Loading : LyricsSearchScreenState

    @Immutable
    data class Success(
        val results: ImmutableList<LyricsSearchResultUiModel>,
        val isSearching: Boolean,
    ) : LyricsSearchScreenState

    data object Empty : LyricsSearchScreenState

    @Immutable
    data class Error(
        @StringRes val messageResId: Int,
    ) : LyricsSearchScreenState
}

@Immutable
data class LyricsSearchResultUiModel(
    val id: String,
    val providerName: String,
    val lyrics: String,
    val preview: String,
    val lineCount: Int,
    val characterCount: Int,
    val isLineSynced: Boolean,
    val isWordSynced: Boolean,
)

data class LyricsTranslationUndoSnapshot(
    val mediaId: String,
    val lyrics: String,
    val source: String,
    val providerName: String,
)

@HiltViewModel
class LyricsMenuViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val lyricsHelper: LyricsHelper,
        val database: MusicDatabase,
        private val networkConnectivity: NetworkConnectivityObserver,
    ) : ViewModel() {
        private var job: Job? = null
        private var aiTranslationJob: Job? = null
        private val searchGeneration = AtomicLong(0L)
        private val _lyricsSearchState = MutableStateFlow<LyricsSearchScreenState>(LyricsSearchScreenState.Empty)
        val lyricsSearchState: StateFlow<LyricsSearchScreenState> = _lyricsSearchState.asStateFlow()
        private val _isRefetching = MutableStateFlow(false)
        val isRefetching: StateFlow<Boolean> = _isRefetching.asStateFlow()
        private val _refetchCompletionEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val refetchCompletionEvents: SharedFlow<Unit> = _refetchCompletionEvents.asSharedFlow()
        val isAiTranslating = MutableStateFlow(false)
        private val _translationUndo = MutableStateFlow<LyricsTranslationUndoSnapshot?>(null)
        val translationUndo: StateFlow<LyricsTranslationUndoSnapshot?> = _translationUndo.asStateFlow()

        private val _translationDismissedMediaIds = MutableStateFlow<Set<String>>(emptySet())
        val translationDismissedMediaIds: StateFlow<Set<String>> =
            _translationDismissedMediaIds.asStateFlow()

        private val _aiTranslationEvents = MutableSharedFlow<String>()
        val aiTranslationEvents: SharedFlow<String> = _aiTranslationEvents.asSharedFlow()

        private val _isNetworkAvailable = MutableStateFlow(false)
        val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

        init {
            viewModelScope.launch {
                networkConnectivity.networkStatus.collect { isConnected ->
                    _isNetworkAvailable.value = isConnected
                }
            }

            _isNetworkAvailable.value =
                try {
                    networkConnectivity.isCurrentlyConnected()
                } catch (e: Exception) {
                    true
                }
        }

        fun search(
            mediaId: String,
            title: String,
            artist: String,
            album: String?,
            duration: Int,
        ) {
            val generation = searchGeneration.incrementAndGet()
            job?.cancel()
            _lyricsSearchState.value = LyricsSearchScreenState.Loading
            job =
                viewModelScope.launch(Dispatchers.IO) {
                    val resultModels = mutableListOf<LyricsSearchResultUiModel>()
                    try {
                        lyricsHelper.getAllLyrics(
                            mediaId = mediaId,
                            songTitle = title,
                            songArtists = artist,
                            songAlbum = album,
                            duration = duration,
                            forceRefresh = true,
                        ) { result ->
                            if (generation != searchGeneration.get()) return@getAllLyrics
                            val model = result.toUiModel(resultModels.size)
                            if (model.preview.isBlank()) return@getAllLyrics

                            resultModels += model
                            _lyricsSearchState.value =
                                LyricsSearchScreenState.Success(
                                    results = ImmutableList.copyOf(resultModels),
                                    isSearching = true,
                                )
                        }
                        if (generation != searchGeneration.get()) return@launch
                        _lyricsSearchState.value =
                            if (resultModels.isEmpty()) {
                                LyricsSearchScreenState.Empty
                            } else {
                                LyricsSearchScreenState.Success(
                                    results = ImmutableList.copyOf(resultModels),
                                    isSearching = false,
                                )
                            }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        if (generation == searchGeneration.get()) {
                            _lyricsSearchState.value = LyricsSearchScreenState.Error(R.string.error_unknown)
                        }
                    }
                }
        }

        fun cancelSearch() {
            searchGeneration.incrementAndGet()
            job?.cancel()
            job = null
        }

        fun resetSearchState() {
            cancelSearch()
            _lyricsSearchState.value = LyricsSearchScreenState.Empty
        }

        fun refetchLyrics(mediaMetadata: MediaMetadata) {
            if (!_isRefetching.compareAndSet(expect = false, update = true)) return

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val result = lyricsHelper.getLyricsWithProvider(mediaMetadata, forceRefresh = true)
                    database.withTransaction {
                        replaceLyrics(
                            id = mediaMetadata.id,
                            lyrics = result.lyrics,
                            source = LyricsEntity.Source.REMOTE.value,
                            providerName = result.providerName,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                } finally {
                    _isRefetching.value = false
                    _refetchCompletionEvents.tryEmit(Unit)
                }
            }
        }

        fun updateLyrics(
            mediaMetadata: MediaMetadata,
            lyrics: String,
            source: LyricsEntity.Source = LyricsEntity.Source.USER_EDIT,
            providerName: String = "",
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                val effectiveProviderName =
                    if (source == LyricsEntity.Source.AI_TRANSLATION && providerName.isBlank()) {
                        captureLyricsBeforeTranslation(mediaMetadata.id)
                        _translationDismissedMediaIds.value =
                            _translationDismissedMediaIds.value - mediaMetadata.id
                        val fromSnapshot =
                            _translationUndo.value
                                ?.takeIf { it.mediaId == mediaMetadata.id }
                                ?.providerName
                                .orEmpty()
                        if (fromSnapshot.isNotBlank()) {
                            fromSnapshot
                        } else {
                            database
                                .withTransaction { getLyricsById(mediaMetadata.id) }
                                ?.providerName
                                .orEmpty()
                        }
                    } else {
                        providerName
                    }
                val lyricsToSave =
                    when (source) {
                        LyricsEntity.Source.REMOTE,
                        LyricsEntity.Source.EMBEDDED,
                        -> LyricsUtils.lyricsOrNotFound(lyrics)

                        LyricsEntity.Source.USER_EDIT,
                        -> lyrics

                        LyricsEntity.Source.USER_SELECTION,
                        -> lyrics.trim().ifBlank { LyricsEntity.LYRICS_NOT_FOUND }

                        LyricsEntity.Source.AI_TRANSLATION ->
                            usableTranslatedLyrics(lyrics) ?: return@launch
                    }
                database.query {
                    replaceLyrics(
                        id = mediaMetadata.id,
                        lyrics = lyricsToSave,
                        source = source.value,
                        providerName = effectiveProviderName,
                    )
                }
            }
        }

        fun translateLyricsWithAi(
            mediaMetadata: MediaMetadata,
            lyrics: String,
            targetLanguage: String,
        ) {
            if (isAiTranslating.value || lyrics.isBlank()) return

            _translationDismissedMediaIds.value =
                _translationDismissedMediaIds.value - mediaMetadata.id
            aiTranslationJob =
                viewModelScope.launch(Dispatchers.IO) {
                    isAiTranslating.value = true
                    var isAutomatic = false
                    try {
                        val prefs = context.dataStore.data.first()
                        isAutomatic = prefs[AutoTranslateLyricsKey] ?: false

                        if (isAutomatic) {
                            val excluded = prefs[AutoTranslateExcludedLanguagesKey] ?: emptySet()
                            val dominant = LyricsUtils.detectDominantLanguageCode(lyrics)
                            if (dominant != null && LyricsUtils.matchesExcludedLanguage(dominant, excluded)) {
                                Log.d(
                                    TAG,
                                    "AI translate skipped: song=${mediaMetadata.title} " +
                                        "language=$dominant is excluded from auto-translation",
                                )
                                return@launch
                            }
                        }

                        captureLyricsBeforeTranslation(mediaMetadata.id)
                        Log.d(
                            TAG,
                            "AI translate start: song=${mediaMetadata.title} automatic=$isAutomatic " +
                                "provider=${prefs[AiProviderKey]} model=${prefs[AiSelectedModelKey]}",
                        )
                        val aiProvider = prefs[AiProviderKey].toEnum(AiProvider.NONE)
                        var aiApiKey = prefs[AiApiKeyKey].orEmpty()
                        var aiCustomEndpoint = prefs[AiCustomEndpointKey].orEmpty()
                        var aiModel =
                            if (aiProvider == AiProvider.CUSTOM) {
                                prefs[AiCustomModelKey].orEmpty()
                            } else {
                                prefs[AiSelectedModelKey].orEmpty()
                            }
                        var aiDeepLFormality = "default"
                        when (aiProvider) {
                            AiProvider.DEEPL -> {
                                aiApiKey = aiApiKey.ifBlank { prefs[moe.rukamori.archivetune.constants.DeeplApiKeyKey].orEmpty() }
                                aiDeepLFormality = prefs[moe.rukamori.archivetune.constants.DeeplFormalityKey].orEmpty().ifBlank { "default" }
                            }
                            AiProvider.OPENROUTER -> {
                                aiApiKey = aiApiKey.ifBlank { prefs[moe.rukamori.archivetune.constants.OpenRouterApiKeyKey].orEmpty() }
                                aiCustomEndpoint = aiCustomEndpoint.ifBlank { prefs[moe.rukamori.archivetune.constants.OpenRouterBaseUrlKey].orEmpty() }
                                aiModel = aiModel.ifBlank { prefs[moe.rukamori.archivetune.constants.OpenRouterModelKey].orEmpty() }
                            }
                            else -> {}
                        }
                        val effectiveTargetLanguage =
                            prefs[moe.rukamori.archivetune.constants.TranslateLanguageKey]
                                ?.takeIf { it.isNotBlank() }
                                ?.let { code ->
                                    java.util.Locale.forLanguageTag(code)
                                        .getDisplayLanguage(java.util.Locale.ENGLISH)
                                        .uppercase()
                                }
                                ?.takeIf { it.isNotBlank() }
                                ?: targetLanguage.ifBlank { "ENGLISH" }
                        val translatedLyrics =
                            AiLyricsTranslator().translate(
                                config =
                                    AiServiceConfig(
                                        provider = aiProvider,
                                        apiKey = aiApiKey,
                                        customEndpoint = aiCustomEndpoint,
                                        model = aiModel,
                                        deepLFormality = aiDeepLFormality,
                                    ),
                                lyrics = lyrics,
                                targetLanguage = effectiveTargetLanguage,
                            )
                        val usableLyrics = usableTranslatedLyrics(translatedLyrics)
                        if (usableLyrics == null) {
                            _aiTranslationEvents.emit(context.getString(R.string.translation_failed))
                            return@launch
                        }
                        saveTranslatedLyrics(
                            mediaId = mediaMetadata.id,
                            lyrics = usableLyrics,
                            submittedLyrics = lyrics,
                        )
                        Log.d(TAG, "AI translate success: song=${mediaMetadata.title} automatic=$isAutomatic")
                        if (!isAutomatic) {
                            val msg = context.getString(R.string.translation_success)
                            _aiTranslationEvents.emit(msg)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(
                            TAG,
                            "AI translate failed: song=${mediaMetadata.title} automatic=$isAutomatic " +
                                "error=${e.javaClass.simpleName}: ${e.message}",
                        )
                        if (!isAutomatic) {
                            val msg = context.getString(R.string.translation_failed) + ": " + (e.localizedMessage ?: e.toString())
                            _aiTranslationEvents.emit(msg)
                        }
                    } finally {
                        isAiTranslating.value = false
                        aiTranslationJob = null
                    }
                }
        }

        private fun usableTranslatedLyrics(lyrics: String): String? =
            LyricsUtils
                .normalizeLyricsText(lyrics)
                .takeIf(LyricsUtils::hasMeaningfulLyricsContent)

        fun cancelAiTranslation() {
            aiTranslationJob?.cancel()
            aiTranslationJob = null
            isAiTranslating.value = false
        }

        fun undoTranslation(mediaId: String) {
            viewModelScope.launch(Dispatchers.IO) {
                val snapshot = _translationUndo.value?.takeIf { it.mediaId == mediaId } ?: return@launch
                database.withTransaction {
                    replaceLyrics(
                        id = snapshot.mediaId,
                        lyrics = snapshot.lyrics,
                        source = snapshot.source,
                        providerName = snapshot.providerName,
                    )
                }
                _translationUndo.value = null

                _translationDismissedMediaIds.value =
                    _translationDismissedMediaIds.value + mediaId
            }
        }

        private suspend fun captureLyricsBeforeTranslation(mediaId: String) {
            if (_translationUndo.value?.mediaId == mediaId) return
            val existing = database.withTransaction { getLyricsById(mediaId) } ?: return
            if (existing.source == LyricsEntity.Source.AI_TRANSLATION.value) return
            _translationUndo.value =
                LyricsTranslationUndoSnapshot(
                    mediaId = existing.id,
                    lyrics = existing.lyrics,
                    source = existing.source,
                    providerName = existing.providerName,
                )
        }

        private suspend fun saveTranslatedLyrics(
            mediaId: String,
            lyrics: String,
            submittedLyrics: String,
        ) {
            val current = database.withTransaction { getLyricsById(mediaId) }
            if (current?.lyrics != submittedLyrics) {
                Log.d(
                    TAG,
                    "AI translate result discarded: lyrics row changed while translating " +
                        "(song id=$mediaId)",
                )
                _aiTranslationEvents.tryEmit(context.getString(R.string.translation_discarded))
                return
            }

            val snapshotMatch = _translationUndo.value?.takeIf { it.mediaId == mediaId }
            val preservedProviderName =
                snapshotMatch?.providerName?.takeIf { it.isNotBlank() }
                    ?: current?.providerName
                    .orEmpty()
            database.query {
                replaceLyrics(
                    id = mediaId,
                    lyrics = lyrics,
                    source = LyricsEntity.Source.AI_TRANSLATION.value,
                    providerName = preservedProviderName,
                )
            }
        }

        private fun LyricsResult.toUiModel(index: Int): LyricsSearchResultUiModel {
            val preview = displayLyricsText(lyrics)
            val lineCount = preview.lineSequence().count { it.isNotBlank() }
            val isTtmlLyrics = isTtml(lyrics)
            val ttmlEntries =
                if (isTtmlLyrics) {
                    runCatching { LyricsUtils.parseTtml(lyrics) }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }

            val isWordSynced = LyricsUtils.hasWordSyncedLyrics(lyrics)

            return LyricsSearchResultUiModel(
                id = "${providerName}_${lyrics.hashCode()}_$index",
                providerName = providerName,
                lyrics = lyrics,
                preview = preview,
                lineCount = lineCount,
                characterCount = preview.length,
                isLineSynced =
                    if (isTtmlLyrics) {
                        ttmlEntries.isNotEmpty() && !isWordSynced
                    } else {
                        isLineSyncedLrc(lyrics) && !isWordSynced
                    },
                isWordSynced = isWordSynced,
            )
        }

        companion object {
            private const val TAG = "LyricsMenuViewModel"
        }
    }
