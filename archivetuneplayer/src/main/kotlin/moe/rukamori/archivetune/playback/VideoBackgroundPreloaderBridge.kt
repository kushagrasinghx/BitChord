/*
 * BRIDGE (replaces ArchiveTune's playback/VideoBackgroundPreloader.kt).
 *
 * In ArchiveTune, the MusicService pauses the main audio player while a
 * background ExoPlayer silently preloads the next music video, so the UI
 * takeover starts instantly. That preloader lives inside the service this
 * port does not carry; with no hold ever taken, the copied player's
 * claimUiTakeover simply answers "nothing was playing" and proceeds to
 * resolve and start the video itself.
 */

package moe.rukamori.archivetune.playback

object VideoBackgroundPreloader {
    fun initialize(
        context: android.content.Context,
        isUiForeground: () -> Boolean,
    ) {
    }

    fun onMediaItemChanged(
        player: androidx.media3.common.Player,
        mediaId: String?,
        metadata: moe.rukamori.archivetune.models.MediaMetadata?,
        videoPlaybackEnabled: Boolean,
        reason: Int,
    ) {
    }

    fun claimUiTakeover(mediaId: String): Boolean = false

    fun onServiceDestroyed() {
    }
}
