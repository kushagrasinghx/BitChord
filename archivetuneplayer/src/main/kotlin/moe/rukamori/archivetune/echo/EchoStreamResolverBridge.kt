/*
 * BRIDGE — ArchiveTune's echo resolver, the YouTube web-player pipeline it
 * used for stream recovery, kept as a named shape that always declines.
 * The one ported caller (YTPlayerUtils' video playback path) wraps every
 * echo attempt in runCatching and falls through to its other resolvers on
 * any failure — so a resolver that always throws is behaviourally the
 * "echo unavailable" branch of the original, and the host app's own stream
 * resolution is what actually serves playback.
 */

package moe.rukamori.archivetune.echo

import android.net.ConnectivityManager
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.utils.YTPlayerUtils

object EchoStreamResolver {
    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): YTPlayerUtils.PlaybackData =
        throw UnsupportedOperationException(
            "ArchiveTune's echo resolver is not part of this port — the host resolves streams.",
        )
}
