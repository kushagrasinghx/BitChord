/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.theme

import androidx.compose.ui.graphics.Color
import moe.rukamori.archivetune.playback.artwork.PlayerPaletteCacheKey

object PlayerPaletteCache {
    private const val MAX_ENTRIES = 48

    private val cache =
        object : LinkedHashMap<PlayerPaletteCacheKey, List<Color>>(16, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<PlayerPaletteCacheKey, List<Color>>?,
            ): Boolean = size > MAX_ENTRIES
        }

    @Synchronized
    fun get(key: PlayerPaletteCacheKey): List<Color>? = cache[key]

    @Synchronized
    fun put(
        key: PlayerPaletteCacheKey,
        colors: List<Color>,
    ) {
        cache[key] = colors
    }

    @Synchronized
    fun clear() = cache.clear()
}
