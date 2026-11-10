/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.MediaInfo

private val YouTubeId = Regex("^[A-Za-z0-9_-]{11}$")

@Composable
fun rememberMediaInfo(videoId: String): MediaInfo? {
    var info by remember(videoId) { mutableStateOf<MediaInfo?>(null) }
    LaunchedEffect(videoId) {
        info =
            if (!YouTubeId.matches(videoId)) {
                null
            } else {
                runCatching { YouTube.getMediaInfo(videoId).getOrNull() }.getOrNull()
            }
    }
    return info
}
