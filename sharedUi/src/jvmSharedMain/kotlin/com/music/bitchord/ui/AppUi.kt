package com.music.bitchord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.settings.LibrarySort
import kotlinx.coroutines.flow.StateFlow

/**
 * What the shared pages — Home, Explore, Library, Search and the rows they are
 * built from — need from the app around them: a handful of the user's settings
 * and which tracks are on disk. The phone answers from `AppSettings` and
 * `Downloads`, the desktop from its own stores; the pages read the same values
 * either way. Installed once at start, beside [com.music.bitchord.ui.player.PlayerPlatform].
 */
interface AppUiHost {
    /** A swipe on a track row plays it next rather than adding it to the end of the queue. */
    val swipeToPlayNext: StateFlow<Boolean>

    /** Playlist browse ids pinned to the front of the Library's Playlists shelf, in pin order. */
    val pinnedPlaylists: StateFlow<List<String>>

    /** How a Library "Show all" grid is ordered. */
    val librarySort: StateFlow<LibrarySort>

    // ---- The Appearance page's home and library switches, ported from ArchiveTune ----

    /** Home keeps only its lead shelf — see ArchiveTune's minimal home mode. */
    val minimalHomeMode: StateFlow<Boolean>

    /** The sign-in banner at the top of Home, for the signed-out listener. */
    val hideSignInBanner: StateFlow<Boolean>

    /** The Replay card at the top of the Library tab. */
    val hideLibraryReplayCard: StateFlow<Boolean>

    /** The On-device shelf on the Library tab. */
    val hideLibraryOnDeviceShelf: StateFlow<Boolean>

    /** Video ids of the tracks already downloaded, for the mark on their rows. */
    @Composable
    fun downloadedIds(): Set<String>

    /**
     * A release's motion cover, for Home's Top Picks — the same lookup the
     * album page makes. Null is the normal answer, and the only one where the
     * user has animated artwork off or the platform has no lookup.
     */
    suspend fun releaseMotion(album: String, artist: String): CanvasArtwork? = null

    /**
     * Apple Music's looping clip of the YouTube artist [browseId], billed as
     * [artistName], for Home's Top Picks. Null as for [releaseMotion].
     */
    suspend fun artistMotion(browseId: String, artistName: String): CanvasArtwork? = null
}

object AppUi {
    @Volatile
    private var installed: AppUiHost? = null

    fun install(host: AppUiHost) {
        installed = host
    }

    val host: AppUiHost
        get() = checkNotNull(installed) { "AppUi.install was not called" }
}

/**
 * Whether the feeds answer a pull at the top with a refresh.
 *
 * On a phone that is a drag with a finger. A window has no such gesture — only
 * the mouse wheel, which scrolling up past the top of a page would turn into a
 * refresh nobody asked for — so the desktop switches it off.
 */
val LocalPullToRefreshEnabled = staticCompositionLocalOf { true }
