package com.music.bitchord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.LibraryViewType
import com.music.bitchord.download.Downloads
import com.music.bitchord.ui.components.ExplicitPrompt
import kotlinx.coroutines.flow.MutableStateFlow

/** The shared pages' settings and downloads, answered from the phone's own stores. */
object AndroidAppUiHost : AppUiHost {
    override val swipeToPlayNext = AppSettings.swipeToPlayNext
    override val homeRecentsViewType = AppSettings.homeRecentsViewType
    override fun setHomeRecentsViewType(value: LibraryViewType) = AppSettings.setHomeRecentsViewType(value)
    override val pinnedPlaylists = AppSettings.pinnedPlaylists
    override val librarySort = AppSettings.librarySort
    override val blockExplicit = AppSettings.blockExplicit
    override val explicitAllowed = AppSettings.explicitAllowed

    /** The tapped blocked explicit song MainActivity's alert is asking about; null when none is. */
    val explicitPrompt = MutableStateFlow<ExplicitPrompt?>(null)
    override fun askExplicitBlocked(prompt: ExplicitPrompt) {
        explicitPrompt.value = prompt
    }

    @Composable
    override fun downloadedIds(): Set<String> {
        val saved by Downloads.saved.collectAsStateWithLifecycle()
        return saved.keys
    }
}
