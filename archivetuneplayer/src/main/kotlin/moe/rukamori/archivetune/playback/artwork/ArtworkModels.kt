/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.artwork

import moe.rukamori.archivetune.constants.PreferredArtworkProvider

enum class ArtworkProvider {
    LOCAL_EMBEDDED,

    ORIGINAL_METADATA,

    TIDAL,

    ARCHIVETUNE_CANVAS,
}

data class ResolvedArtwork(
    val mediaId: String,
    val url: String?,
    val provider: ArtworkProvider,
    val artworkIdentity: String,
    val matchConfidence: Float? = null,
)

data class ArtworkCacheKey(
    val mediaId: String,
    val provider: ArtworkProvider,
    val artworkIdentity: String,
    val requestedSize: Int,
)

data class PlayerPaletteCacheKey(
    val mediaId: String,
    val provider: ArtworkProvider,
    val artworkIdentity: String,
    val backgroundMode: String,
    val darkTheme: Boolean,
)

data class ArtworkRequest(
    val mediaId: String,
    val title: String,
    val artists: List<String>,
    val album: String?,
    val isrc: String?,
    val durationMs: Long?,

    val originalArtworkUrl: String?,

    val isLocal: Boolean = false,
)

data class ArtworkSettings(

    val tidalArtworkEnabled: Boolean,

    val tidalAvailable: Boolean,

    val providerOrder: List<PreferredArtworkProvider> = emptyList(),
)

data class TidalArtworkMatch(
    val trackId: String,
    val releaseId: String?,
    val artworkUrl: String,

    val matchMethod: String,

    val confidence: Float,
)

fun interface TidalArtworkFetcher {
    fun fetchArtwork(request: ArtworkRequest): TidalArtworkMatch?
}

fun String?.isLocalArtworkUri(): Boolean {
    val scheme = this?.substringBefore(':')?.lowercase() ?: return false
    return scheme == "content" || scheme == "file" || scheme == "android.resource"
}

fun guessArtworkProvider(url: String?): ArtworkProvider =
    when {
        url.isNullOrBlank() -> ArtworkProvider.ORIGINAL_METADATA
        url.isLocalArtworkUri() -> ArtworkProvider.LOCAL_EMBEDDED
        url.contains("resources.tidal.com") -> ArtworkProvider.TIDAL
        else -> ArtworkProvider.ORIGINAL_METADATA
    }
