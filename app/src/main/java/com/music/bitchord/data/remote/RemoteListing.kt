package com.music.bitchord.data.remote

import com.music.bitchord.data.model.UiState

/**
 * What a remote library page shows for a listing. A failed listing names
 * its cause: a share that answers with an error is not an empty share, and
 * "no audio files" would send the user hunting through folder paths for a
 * login or network problem.
 */
object RemoteListing {

    fun <T> state(listing: Result<List<T>>, emptyMessage: String): UiState<List<T>> =
        listing.fold(
            onSuccess = { items -> if (items.isEmpty()) UiState.Error(emptyMessage) else UiState.Success(items) },
            onFailure = { UiState.Error(it.message?.takeIf(String::isNotBlank) ?: it.javaClass.simpleName) },
        )
}
