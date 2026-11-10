/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Ported from SimpMusic (https://github.com/maxrave-dev/SimpMusic),
 * core/common Config.kt ITAG object — GPL-3.0, © maxrave-dev.
 * Logic kept byte-for-byte; only the package name changed.
 */

package moe.rukamori.archivetune.simpstream

/**
 * The YouTube stream format ids (`itag`) SimpMusic asks for, in one place.
 *
 * YouTube publishes dozens; these are the handful the player, the downloader and the extractor
 * health-check all have to agree on. Every itag reference belongs here — a bare number inside a
 * `find { it.itag == ... }` reads as an arbitrary constant, and the next person cannot tell that it
 * is the same stream the quality setting three modules away is supposed to produce.
 */
object ITAG {
    /** Opus, adaptive audio — what the "Low" audio-quality setting selects. */
    const val AUDIO_OPUS_LOW: Int = 250

    /** Opus, adaptive audio — what the "Medium" audio-quality setting selects. */
    const val AUDIO_OPUS_MEDIUM: Int = 251

    /** Opus 256 kbps, adaptive audio — the "High" setting. YouTube serves it to Premium accounts only. */
    const val AUDIO_OPUS_HIGH: Int = 774

    /**
     * AAC 256 kbps, adaptive audio — the AAC twin of [AUDIO_OPUS_HIGH]. An account entitled to
     * high-quality audio is given one family or the other, so this is the fallback when 774 is absent.
     */
    const val AUDIO_AAC_HIGH: Int = 141

    /** H.264 360p, adaptive video — what the "360p" video-quality setting selects. */
    const val VIDEO_360P: Int = 134

    /** H.264 720p, adaptive video — what the "720p" video-quality setting selects. */
    const val VIDEO_720P: Int = 136

    /** H.264 1080p, adaptive video — what the "1080p" video-quality setting selects. */
    const val VIDEO_1080P: Int = 137

    /** H.264 360p and AAC muxed into ONE progressive stream, for the single-URL (muxed) path. */
    const val MUXED_360P: Int = 18

    /** Every adaptive audio itag the app knows how to play. */
    val AUDIO: Set<Int> = setOf(AUDIO_OPUS_LOW, AUDIO_OPUS_MEDIUM, AUDIO_OPUS_HIGH, AUDIO_AAC_HIGH)

    /** Every adaptive video itag the app knows how to play. */
    val VIDEO: Set<Int> = setOf(VIDEO_1080P, VIDEO_720P, VIDEO_360P)

    /**
     * The other 256 kbps rendition of the same audio, or null for an itag that has no twin.
     *
     * YouTube hands a high-quality account ONE of the two families, so the one the user picked can
     * simply be absent from a response that still carries its counterpart. Asking for the twin is
     * therefore a better fallback than dropping to "any audio stream", which would silently serve
     * a 70 kbps one instead.
     */
    fun highQualityTwinOf(itag: Int?): Int? =
        when (itag) {
            AUDIO_OPUS_HIGH -> AUDIO_AAC_HIGH
            AUDIO_AAC_HIGH -> AUDIO_OPUS_HIGH
            else -> null
        }
}
