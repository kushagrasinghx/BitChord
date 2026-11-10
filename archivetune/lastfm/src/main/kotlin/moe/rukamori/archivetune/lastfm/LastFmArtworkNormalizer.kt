/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lastfm

import moe.rukamori.archivetune.lastfm.models.UserImage

object LastFmArtworkNormalizer {

    private const val LASTFM_NO_ART_HASH = "2a96cbd8b46e442fc41c2b86b821562f"

    fun isRealImage(url: String?): Boolean =
        !url.isNullOrBlank() && !url.contains(LASTFM_NO_ART_HASH)

    fun bestImageUrl(images: List<UserImage>?): String? {
        if (images.isNullOrEmpty()) return null
        val bySize = { size: String ->
            images.firstOrNull { it.size.equals(size, ignoreCase = true) && isRealImage(it.text) }?.text
        }
        return bySize("extralarge")
            ?: bySize("large")
            ?: bySize("medium")
            ?: bySize("small")
            ?: images.firstOrNull { isRealImage(it.text) }?.text
    }
}
