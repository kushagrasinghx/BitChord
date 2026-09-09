package com.music.bitchord.offline

import android.content.Context
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single local source of truth for the offline edition.
 *
 * It intentionally has no URL, account, catalogue or remote-service inputs.
 */
class OfflineLocalLibrary(private val context: Context) {
    private val _songs = MutableStateFlow<UiState<List<Song>>>(UiState.Loading)
    val songs: StateFlow<UiState<List<Song>>> = _songs.asStateFlow()

    suspend fun refresh() {
        if (!LocalMediaRepository.hasStoragePermission(context)) {
            _songs.value = UiState.Success(emptyList())
            return
        }
        _songs.value = runCatching {
            UiState.Success(LocalMediaRepository.getLocalMusic(context))
        }.getOrElse { UiState.Error(it.message ?: "Could not read local music") }
    }
}
