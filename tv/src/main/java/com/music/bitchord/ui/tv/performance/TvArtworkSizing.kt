package com.music.bitchord.ui.tv.performance

/** Preserve the measured aspect ratio and never enlarge small avatars/cards to the profile cap. */
internal fun capTvArtworkDimensions(width: Int, height: Int, maxPx: Int): Pair<Int, Int> {
    require(width > 0 && height > 0 && maxPx > 0)
    val scale = minOf(1.0, maxPx.toDouble() / maxOf(width, height))
    return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
}
