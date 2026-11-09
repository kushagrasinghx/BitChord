package com.music.bitchord.data.innertube

/**
 * Premium is a verified stream capability, not a boolean inferred from cookies.
 * Only a successfully probed, authenticated high-bitrate URL qualifies.
 * A signed-in account may still receive nothing beyond standard Opus.
 */
internal object PremiumAudioPolicy {
    const val MIN_PREMIUM_KBPS = 220

    fun shouldProbe(
        signedIn: Boolean,
        highQualitySelected: Boolean,
        maxKbps: Int,
        isUserUpload: Boolean,
    ): Boolean = signedIn && highQualitySelected && !isUserUpload && maxKbps >= MIN_PREMIUM_KBPS

    fun isPremiumTier(kbps: Int): Boolean = kbps >= MIN_PREMIUM_KBPS
}
