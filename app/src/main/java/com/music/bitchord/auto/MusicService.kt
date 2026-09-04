package com.music.bitchord.auto

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import android.net.Uri
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.common.Player
import androidx.media3.session.SessionError
import com.music.bitchord.R
import com.music.bitchord.playback.PlaybackService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class MusicService : MediaLibraryService() {


private var librarySession: MediaLibrarySession? = null

    companion object {
        const val ACTION_TOGGLE_FAVORITE = "com.music.bitchord.action.TOGGLE_FAVORITE"
        const val ACTION_TOGGLE_SHUFFLE = "com.music.bitchord.action.TOGGLE_SHUFFLE"
        const val ACTION_SLEEP_TIMER = "com.music.bitchord.action.SLEEP_TIMER"
        const val ACTION_THUMBS_DOWN = "com.music.bitchord.action.THUMBS_DOWN"
        const val ACTION_THUMBS_UP = "com.music.bitchord.action.THUMBS_UP"
    }

    private val favoriteCommand = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)
    private val shuffleCommand = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
    private val sleepTimerCommand = SessionCommand(ACTION_SLEEP_TIMER, Bundle.EMPTY)
    private val thumbsDownCommand = SessionCommand(ACTION_THUMBS_DOWN, Bundle.EMPTY)
    private val thumbsUpCommand = SessionCommand(ACTION_THUMBS_UP, Bundle.EMPTY)

    override fun onCreate() {

        super.onCreate()
        val player = PlaybackService.globalPlayer
        if (player != null) {

            librarySession = MediaLibrarySession.Builder(this, player, CustomMediaLibrarySessionCallback())
                .build()
        }

    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return librarySession
    }

    override fun onDestroy() {

        librarySession?.release()
        librarySession = null
        super.onDestroy()
    }

private inner class CustomMediaLibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(favoriteCommand)
                .add(shuffleCommand)
                .add(sleepTimerCommand)
                .add(thumbsUpCommand)
                .add(thumbsDownCommand)
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(availableSessionCommands)
                .build()
        }


        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            val player = session.player
            when (customCommand.customAction) {

                ACTION_TOGGLE_SHUFFLE -> {
                    player.shuffleModeEnabled = !player.shuffleModeEnabled
                }
                ACTION_TOGGLE_FAVORITE, ACTION_THUMBS_UP -> {
                    // Logic to handle favorite in actual app
                }
                // Handle others as needed
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }


override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            // Check driver distraction guidelines / automotive properties if needed
            val rootItem = MediaItem.Builder()
                .setMediaId("bitchord_root")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }


override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>> {

            // Limit items per page to comply with driver safety (max 6 usually)
            val maxItems = 6
            val limit = if (pageSize > 0) Math.min(pageSize, maxItems) else maxItems

            if (parentId == "bitchord_root") {

                val tabs = listOf(
                    createTabItem("tab_now_playing", "Now Playing"),
                    createTabItem("tab_library", "Library"),
                    createTabItem("tab_recent", "Recent"),
                    createTabItem("tab_search", "Search")
                )
                return Futures.immediateFuture(
                    LibraryResult.ofItemList(tabs, params)
                )
            } else if (parentId == "tab_now_playing") {

                // Return current track
                val items = mutableListOf<MediaItem>()
                session.player.currentMediaItem?.let { items.add(it) }
                return Futures.immediateFuture(LibraryResult.ofItemList(items, params))
            } else if (parentId == "tab_recent") {

                // Fetch recent tracks (mocked for auto UI purposes)
                return Futures.immediateFuture(LibraryResult.ofItemList(emptyList(), params))
            } else if (parentId == "tab_library") {

                // Fetch library tracks (mocked for auto UI purposes)
                return Futures.immediateFuture(LibraryResult.ofItemList(emptyList(), params))
            }

            return Futures.immediateFuture(LibraryResult.ofItemList(emptyList(), params))
        }


        private fun createTabItem(id: String, title: String): MediaItem {
            return MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build()
        }

    }
}
