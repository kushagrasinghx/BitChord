package com.music.bitchord.ui.tv.performance

enum class TvPerformanceMode {
    AUTO, SMOOTH, STANDARD;

    companion object {
        fun fromStored(value: String?): TvPerformanceMode =
            entries.firstOrNull { it.name == value } ?: AUTO
    }
}

/** Reported total RAM excludes memory reserved by the firmware and GPU. Zero means unknown. */
data class TvDeviceResources(
    val lowRam: Boolean = false,
    val totalMemoryBytes: Long = 0,
    val memoryClassMb: Int = 0,
) {
    val constrained: Boolean
        get() = lowRam || totalMemoryBytes in 1..(2560L * 1024 * 1024) || memoryClassMb in 1..192
}

enum class TvPerformanceProfile(
    val motionArtworkAllowed: Boolean,
    val artworkMaxPx: Int,
    val artworkCrossfadeMs: Int,
    val artworkShadowDp: Int,
) {
    SMOOTH(false, 512, 0, 8),
    STANDARD(true, 720, 200, 29);

    fun allowsCanvas(requested: Boolean, lyricsVisible: Boolean, canvasInLyrics: Boolean): Boolean =
        motionArtworkAllowed && requested && (!lyricsVisible || canvasInLyrics)
}

fun resolveTvPerformanceProfile(
    mode: TvPerformanceMode,
    resources: TvDeviceResources,
): TvPerformanceProfile = when (mode) {
    TvPerformanceMode.AUTO -> if (resources.constrained) TvPerformanceProfile.SMOOTH else TvPerformanceProfile.STANDARD
    TvPerformanceMode.SMOOTH -> TvPerformanceProfile.SMOOTH
    TvPerformanceMode.STANDARD -> TvPerformanceProfile.STANDARD
}
